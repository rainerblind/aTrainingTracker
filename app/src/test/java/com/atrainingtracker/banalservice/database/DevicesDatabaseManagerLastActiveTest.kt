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
import com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceSortOrder
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Unit tests verifying [LastActiveDateFormatter] ThreadLocal UTC ISO-8601 formatting,
 * cross-locale parse immunity, legacy `DateFormat.getDateTimeInstance()` backward compatibility,
 * locale/timezone cache invalidation, concurrency safety, and [DevicesDatabaseManager.setLastActive]
 * persistence (REQ-DAT-048.3 / TST-DAT-044.3, refining REQ-STB-021.4 / TST-STB-021.4).
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

        val utcFirst = LastActiveDateFormatter.currentUtcFormat()
        val utcSecond = LastActiveDateFormatter.currentUtcFormat()
        assertSame("Same thread must reuse cached UTC ISO SimpleDateFormat", utcFirst, utcSecond)

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
    fun formatEpochMillis_persistsUtcIso8601ParseableAcrossLocalesAndSupportsLegacyFormats_TST_DAT_044_3() {
        val sampleMillis = 1_763_528_400_000L // whole second timestamp (2025-11-19T05:00:00Z)
        val expectedUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(sampleMillis))

        // Write under German locale / Berlin TZ
        Locale.setDefault(Locale.GERMANY)
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        val formattedDe = LastActiveDateFormatter.formatEpochMillis(sampleMillis)
        assertEquals("UTC ISO-8601 output must be locale-independent", expectedUtc, formattedDe)

        // Switch to US locale / New York TZ — canonical UTC string must still parse identically
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        assertEquals(sampleMillis, LastActiveDateFormatter.parseToEpochMillis(formattedDe))
        assertEquals(sampleMillis, DeviceSortOrder.parseLastSeenTimestamp(formattedDe))

        // Legacy locale DateFormat rows remain backward-compatible under current locale
        for (locale in listOf(Locale.US, Locale.GERMANY, Locale.FRANCE)) {
            Locale.setDefault(locale)
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
            val legacyFormatted = DateFormat.getDateTimeInstance().format(Date(sampleMillis))
            assertEquals(
                "Legacy DateFormat string must remain parseable for $locale",
                sampleMillis,
                LastActiveDateFormatter.parseToEpochMillis(legacyFormatted)
            )
        }

        // Legacy DB ISO formats remain supported and garbage returns null
        assertNotNull(LastActiveDateFormatter.parseToEpochMillis("2026-11-14 10:30:00"))
        assertNotNull(LastActiveDateFormatter.parseToEpochMillis("2026-11-14T10:30:00"))
        assertNotNull(LastActiveDateFormatter.parseToEpochMillis("2026-11-14"))
        assertNull(LastActiveDateFormatter.parseToEpochMillis(null))
        assertNull(LastActiveDateFormatter.parseToEpochMillis("   "))
        assertNull(LastActiveDateFormatter.parseToEpochMillis("not-a-date"))
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
            java.util.Collections.newSetFromMap(java.util.IdentityHashMap<SimpleDateFormat, Boolean>())
        )

        val threads = (0 until threadCount).map { threadIdx ->
            Thread {
                try {
                    val threadFormat = LastActiveDateFormatter.currentUtcFormat()
                    seenFormats.add(threadFormat)
                    for (i in 0 until iterationsPerThread) {
                        val epochSec = (1_700_000_000L + threadIdx * 1000L + i) * 1000L
                        val formatted = LastActiveDateFormatter.formatEpochMillis(epochSec)
                        val parsed = LastActiveDateFormatter.parseToEpochMillis(formatted)
                        assertNotNull(parsed)
                        assertEquals(epochSec, parsed)
                    }
                    assertSame(threadFormat, LastActiveDateFormatter.currentUtcFormat())
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
        assertEquals("Each worker thread must receive an isolated UTC DateFormat instance", threadCount, seenFormats.size)
    }

    @Test
    fun devicesDatabaseManager_setLastActivePersistsUtcIsoTimestamp_TST_DAT_044_3() {
        Locale.setDefault(Locale.GERMANY)
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))

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
        assertTrue(
            "Persisted LAST_ACTIVE must be canonical UTC ISO-8601, was '$persisted'",
            Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$""").matches(persisted!!)
        )

        // Must parse cleanly even after switching locale to en_US
        Locale.setDefault(Locale.US)
        assertNotNull(LastActiveDateFormatter.parseToEpochMillis(persisted))
        assertTrue(DeviceSortOrder.parseLastSeenTimestamp(persisted) > 0L)
    }
}
