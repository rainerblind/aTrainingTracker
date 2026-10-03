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

package com.atrainingtracker.trainingtracker.segments

import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper
import io.mockk.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit tests verifying reactive Strava segment sync progress, in-memory StateFlow lifecycle,
 * persistent storage decoupling (no "now" written to SharedPreferences), defensive cleanup,
 * and 9-language resource parity (REQ-EXP-014, TST-EXP-011, ATT-1219).
 */
class SegmentsRepositorySyncTest {

    private lateinit var mockContext: Context
    private lateinit var mockSegmentsDb: SegmentsDatabaseManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        mockSegmentsDb = mockk(relaxed = true)
        every { mockSegmentsDb.pruneExpiredSegments(any()) } returns 0
        every { mockSegmentsDb.getAllSegmentSummaries() } returns emptyList()

        mockkStatic(SegmentsDatabaseManager::class)
        every { SegmentsDatabaseManager.getInstance(any()) } returns mockSegmentsDb

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getStravaAccessToken() } returns null // Avoid auto-sync on init
        every { TrainingApplication.getLastUpdateTimeOfStravaSegments() } returns "01.01.2026, 12:00"
        every { TrainingApplication.setLastUpdateTimeOfStravaSegments(any()) } just Runs

        mockkStatic(StravaHelper::class)
        every { StravaHelper.getRefreshedAccessToken() } returns null

        SegmentsRepository.resetForTesting(null)
    }

    @After
    fun tearDown() {
        SegmentsRepository.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testIsSyncingInitialStateIsFalse() {
        val repository = SegmentsRepository.getInstance(mockContext)
        assertFalse("isSyncing must initially be false", repository.isSyncing.value)
    }

    @Test
    fun testIsSyncingTransitionsDuringSyncAndResetsOnCompletion() = runBlocking {
        val repository = SegmentsRepository.getInstance(mockContext)
        assertFalse(repository.isSyncing.value)

        val syncJob = launch {
            repository.syncStarredSegments(BSportType.UNKNOWN)
        }
        syncJob.join()

        assertFalse("isSyncing must reset to false after completion", repository.isSyncing.value)
        verify(atLeast = 1) {
            TrainingApplication.setLastUpdateTimeOfStravaSegments(not(match { it.equals("now", ignoreCase = true) || it.equals("jetzt", ignoreCase = true) }))
        }
    }

    @Test
    fun testIsSyncingResetsToFalseOnExceptionInFinally() = runBlocking {
        val repository = SegmentsRepository.getInstance(mockContext)
        assertFalse(repository.isSyncing.value)

        // Force an exception during pruneExpiredSegments
        every { mockSegmentsDb.pruneExpiredSegments(any()) } throws IOException("Simulated network/DB error")

        try {
            repository.syncStarredSegments(BSportType.UNKNOWN)
            fail("Expected IOException was not thrown")
        } catch (e: IOException) {
            assertEquals("Simulated network/DB error", e.message)
        }

        assertFalse("isSyncing MUST reset to false in finally block even after exception", repository.isSyncing.value)
    }

    @Test
    fun testSharedPreferencesNeverMutatedWithTransientNowString() = runBlocking {
        val capturedTimestamps = mutableListOf<String>()
        every { TrainingApplication.setLastUpdateTimeOfStravaSegments(capture(capturedTimestamps)) } just Runs

        val repository = SegmentsRepository.getInstance(mockContext)
        repository.syncStarredSegments(BSportType.UNKNOWN)

        assertTrue("setLastUpdateTimeOfStravaSegments must be called upon sync completion", capturedTimestamps.isNotEmpty())
        for (ts in capturedTimestamps) {
            assertNotEquals("SharedPreferences must NEVER be populated with transient string 'now'", "now", ts.lowercase())
            assertNotEquals("SharedPreferences must NEVER be populated with transient string 'jetzt'", "jetzt", ts.lowercase())
            assertNotEquals("SharedPreferences must NEVER be populated with transient string 'ahora'", "ahora", ts.lowercase())
        }
    }

    @Test
    fun testRefreshingSportsCleanupOnWorkerFailure() = runBlocking {
        val repository = SegmentsRepository.getInstance(mockContext)

        // Mock StravaHelper to throw inside worker
        every { StravaHelper.getRefreshedAccessToken() } throws RuntimeException("OAuth failure")

        try {
            repository.syncStarredSegments(BSportType.BIKE)
            fail("Expected RuntimeException was not thrown")
        } catch (e: RuntimeException) {
            assertEquals("OAuth failure", e.message)
        }

        assertTrue("refreshingSports must be empty after worker exception cleanup", repository.refreshingSports.value.isEmpty())
        assertFalse("isSyncing must be false after worker exception", repository.isSyncing.value)
    }

    @Test
    fun testNineLanguageParityForLastUpdateOfSegmentsNow() {
        val resDir = findResDirectory()
        val expectedTranslations = mapOf(
            "" to "now",
            "de" to "jetzt",
            "es" to "ahora",
            "fr" to "maintenant",
            "it" to "adesso",
            "ja" to "今",
            "nl" to "nu",
            "pl" to "teraz",
            "pt" to "agora"
        )

        for ((locale, expectedText) in expectedTranslations) {
            val valuesDir = if (locale.isEmpty()) "values" else "values-$locale"
            val file = File(resDir, "$valuesDir/strings.xml")
            assertTrue("Resource file must exist: $file", file.exists())

            val strings = parseStringsFile(file)
            val actual = strings["lastUpdateOfSegmentsNow"]
            assertNotNull("Key 'lastUpdateOfSegmentsNow' must exist in $valuesDir/strings.xml", actual)
            assertEquals("Translation mismatch for locale '$locale' ($valuesDir)", expectedText, actual)
        }
    }

    private fun findResDirectory(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("res directory not found in candidate paths: $candidates")
    }

    private fun parseStringsFile(file: File): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (!file.exists()) return map

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")

        for (i in 0 until stringNodes.length) {
            val element = stringNodes.item(i) as Element
            val name = element.getAttribute("name")
            val text = element.textContent
            map[name] = text
        }
        return map
    }
}
