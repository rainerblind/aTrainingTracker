/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.banalservice.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Unit tests verifying [LastActiveDateFormatter] ThreadLocal caching, locale/timezone invalidation,
 * concurrent formatting safety, downstream `DateFormat.getDateTimeInstance().parse(...)` parity,
 * and [DevicesDatabaseManager.setLastActive] persistence (REQ-STB-021.4, TST-STB-021.4).
 */
class DevicesDatabaseManagerLastActiveTest {

    private lateinit var originalLocale: Locale
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        originalTimeZone = TimeZone.getDefault()
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalTimeZone)
        unmockkAll()
    }

    @Test
    fun currentFormat_reusesSameInstanceOnThreadAndInvalidatesOnLocaleOrTimeZoneChange() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        val first = LastActiveDateFormatter.currentFormat()
        val second = LastActiveDateFormatter.currentFormat()
        assertSame("Same thread/locale/timezone must reuse cached DateFormat instance", first, second)

        Locale.setDefault(Locale.GERMANY)
        val afterLocaleChange = LastActiveDateFormatter.currentFormat()
        assertNotSame("Locale change must invalidate ThreadLocal cached DateFormat", first, afterLocaleChange)
        assertSame(
            "Subsequent lookups under new locale must reuse updated DateFormat",
            afterLocaleChange,
            LastActiveDateFormatter.currentFormat()
        )

        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        val afterTzChange = LastActiveDateFormatter.currentFormat()
        assertNotSame("TimeZone change must invalidate ThreadLocal cached DateFormat", afterLocaleChange, afterTzChange)
        assertSame(
            "Subsequent lookups under new timezone must reuse updated DateFormat",
            afterTzChange,
            LastActiveDateFormatter.currentFormat()
        )
    }

    @Test
    fun formatEpochMillis_matchesDateFormatGetDateTimeInstanceParsingParity() {
        val sampleMillis = 1_763_528_400_000L // whole second timestamp
        for (locale in listOf(Locale.US, Locale.GERMANY, Locale.FRANCE)) {
            Locale.setDefault(locale)
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))

            val formatted = LastActiveDateFormatter.formatEpochMillis(sampleMillis)
            val expected = DateFormat.getDateTimeInstance().format(Date(sampleMillis))
            assertEquals("Formatted timestamp must match DateFormat.getDateTimeInstance() for $locale", expected, formatted)

            // Exact parser used by DeviceStatusRow & DeviceSortOrder
            val parsed = DateFormat.getDateTimeInstance().parse(formatted)
            assertNotNull("Downstream parser must accept formatted string for $locale", parsed)
            assertEquals(sampleMillis, parsed!!.time)
        }
    }

    @Test
    fun formatEpochMillis_isThreadSafeAndThreadIsolatedUnderConcurrency() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        val threadCount = 8
        val iterationsPerThread = 200
        val latch = CountDownLatch(threadCount)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val seenFormats = java.util.Collections.synchronizedSet(
            java.util.Collections.newSetFromMap(java.util.IdentityHashMap<DateFormat, Boolean>())
        )

        val threads = (0 until threadCount).map { threadIdx ->
            Thread {
                try {
                    val threadFormat = LastActiveDateFormatter.currentFormat()
                    seenFormats.add(threadFormat)
                    for (i in 0 until iterationsPerThread) {
                        val epochSec = (1_700_000_000L + threadIdx * 1000L + i) * 1000L
                        val formatted = LastActiveDateFormatter.formatEpochMillis(epochSec)
                        val parsed = LastActiveDateFormatter.currentFormat().parse(formatted)
                        assertNotNull(parsed)
                        assertEquals(epochSec, parsed!!.time)
                    }
                    assertSame(threadFormat, LastActiveDateFormatter.currentFormat())
                } catch (t: Throwable) {
                    failures.add(t)
                } finally {
                    latch.countDown()
                }
            }.apply { start() }
        }

        assertTrue("All worker threads must finish within timeout", latch.await(15, TimeUnit.SECONDS))
        threads.forEach { it.join() }
        assertTrue("Concurrent ThreadLocal formatting produced errors: $failures", failures.isEmpty())
        assertEquals("Each worker thread must receive an isolated DateFormat instance", threadCount, seenFormats.size)
    }

    @Test
    fun devicesDatabaseManager_setLastActivePersistsParseableTimestamp() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        val mockContext = mockk<Context>(relaxed = true)
        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        every { mockDb.isOpen } returns true
        every { mockDb.update(any(), any(), any(), any()) } returns 1

        mockkConstructor(ContentValues::class)
        val stored = mutableMapOf<String, String>()
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } answers {
            stored[firstArg()] = secondArg()
        }

        val manager = object : DevicesDatabaseManager(mockContext) {
            override val database: SQLiteDatabase get() = mockDb
        }

        manager.setLastActive(77L)

        verify(exactly = 1) {
            mockDb.update(
                DevicesDatabaseManager.DevicesDbHelper.DEVICES,
                any(),
                "${DevicesDatabaseManager.DevicesDbHelper.C_ID}=?",
                arrayOf("77")
            )
        }
        val persisted = stored[DevicesDatabaseManager.DevicesDbHelper.LAST_ACTIVE]
        assertNotNull("setLastActive must persist LAST_ACTIVE value", persisted)
        assertTrue(persisted!!.isNotBlank())
        assertNotNull(
            "Persisted LAST_ACTIVE must be parseable by DateFormat.getDateTimeInstance()",
            DateFormat.getDateTimeInstance().parse(persisted)
        )
    }
}
