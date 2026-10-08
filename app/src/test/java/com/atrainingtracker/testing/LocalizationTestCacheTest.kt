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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.atrainingtracker.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit verification tests for [LocalizationTestCache] (REQ-PRO-025, TST-PRO-018.3).
 *
 * Enforces:
 * - Complete 9-locale parity and validity.
 * - Thread-safety under concurrent multi-threaded worker access.
 * - Immutability of returned string resource maps.
 * - Cache idempotency and in-memory reuse.
 */
class LocalizationTestCacheTest {

    @Before
    fun setUp() {
        LocalizationTestCache.clear()
    }

    @Test
    fun testCache_returnsCompleteAndValidStringsMapForAll9Locales() {
        assertEquals("Must support exactly 9 locales", 9, LocalizationTestCache.LOCALES.size)

        for (locale in LocalizationTestCache.LOCALES) {
            val strings = LocalizationTestCache.getStrings(locale)
            assertNotNull("Strings map for locale $locale must not be null", strings)
            assertTrue("Locale $locale must contain parsed strings (got ${strings.size})", strings.isNotEmpty())

            // Universal invariant key present across all 9 locales
            assertTrue(
                "Locale $locale must contain battery_optimization_warning_banner_title",
                strings.containsKey("battery_optimization_warning_banner_title")
            )
        }
    }

    @Test
    fun testCache_returnsImmutableMap() {
        val strings = LocalizationTestCache.getStrings("values")
        assertNotNull(strings)

        try {
            (strings as MutableMap<String, String>)["new_key"] = "new_val"
            fail("Attempting to mutate returned map must throw UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            // Expected: immutable map
        }

        try {
            (strings as MutableMap<String, String>).remove("app_name")
            fail("Attempting to remove keys from returned map must throw UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            // Expected: immutable map
        }
    }

    @Test
    fun testCache_concurrentAccessIsThreadSafe() {
        val threadCount = 16
        val iterationsPerThread = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    val locale = LocalizationTestCache.LOCALES[i % LocalizationTestCache.LOCALES.size]
                    for (j in 0 until iterationsPerThread) {
                        val strings = LocalizationTestCache.getStrings(locale)
                        if (strings.isEmpty() || !strings.containsKey("battery_optimization_warning_banner_title")) {
                            errorCount.incrementAndGet()
                        }
                        val raw = LocalizationTestCache.getRawContent(locale)
                        if (raw.isEmpty()) {
                            errorCount.incrementAndGet()
                        }
                    }
                } catch (t: Throwable) {
                    errorCount.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val finishedInTime = doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("Concurrent executions must finish within 10 seconds", finishedInTime)
        assertEquals("Zero concurrency errors or race conditions allowed", 0, errorCount.get())
    }

    @Test
    fun testCache_idempotentSubsequentCallsReturnSameCachedInstance() {
        val firstCall = LocalizationTestCache.getStrings("values-de")
        val secondCall = LocalizationTestCache.getStrings("values-de")

        assertSame("Subsequent calls for the same locale must return the identical cached Map instance", firstCall, secondCall)

        val firstRaw = LocalizationTestCache.getRawContent("values-de")
        val secondRaw = LocalizationTestCache.getRawContent("values-de")

        assertSame("Subsequent calls for raw content must return the identical cached String reference", firstRaw, secondRaw)
    }

    @Test
    fun testCache_getRawContent_returnsNonEmptyXml() {
        for (locale in LocalizationTestCache.LOCALES) {
            val content = LocalizationTestCache.getRawContent(locale)
            assertTrue("Locale $locale raw content must start with XML or contain <resources>", content.contains("<resources>"))
        }
    }
}
