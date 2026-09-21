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

package com.atrainingtracker.trainingtracker.repositories

import android.app.Application
import android.content.Context
import android.util.Log
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaEquipmentSynchronizeThread
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit tests verifying reactive Strava equipment sync progress, in-memory StateFlow lifecycle,
 * persistent storage decoupling (no "now" written to SharedPreferences), defensive cleanup,
 * two-tier debounce guard, and 9-language resource parity (REQ-EXP-016, TST-EXP-013, ATT-1231).
 */
class EquipmentRepositorySyncTest {

    private lateinit var mockContext: Context
    private lateinit var mockApplication: Application

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockApplication = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockApplication
        every { mockContext.packageName } returns "com.atrainingtracker"

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.getLastUpdateTimeOfStravaEquipment() } returns "01.01.2026, 12:00"
        every { TrainingApplication.setLastUpdateTimeOfStravaEquipment(any()) } just Runs

        mockkStatic(StravaHelper::class)
        every { StravaHelper.getRefreshedAccessToken() } returns null

        EquipmentRepository.resetSyncingForTesting()
    }

    @After
    fun tearDown() {
        EquipmentRepository.resetSyncingForTesting()
        EquipmentRepository.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testIsSyncingInitialStateIsFalse() {
        assertFalse("EquipmentRepository.isSyncing must initially be false", EquipmentRepository.isSyncing.value)
    }

    @Test
    fun testSetSyncingUpdatesStateFlow() {
        assertFalse(EquipmentRepository.isSyncing.value)
        EquipmentRepository.setSyncing(true)
        assertTrue("isSyncing must be true after setSyncing(true)", EquipmentRepository.isSyncing.value)
        EquipmentRepository.setSyncing(false)
        assertFalse("isSyncing must be false after setSyncing(false)", EquipmentRepository.isSyncing.value)
    }

    @Test
    fun testThreadLifecycleTransitionsAndReset() {
        assertFalse(EquipmentRepository.isSyncing.value)

        var observedSyncingDuringExecution = false
        every { StravaHelper.getRefreshedAccessToken() } answers {
            observedSyncingDuringExecution = EquipmentRepository.isSyncing.value
            null
        }

        val thread = StravaEquipmentSynchronizeThread(mockContext)
        thread.run()

        assertTrue("EquipmentRepository.isSyncing must be true during thread execution", observedSyncingDuringExecution)
        assertFalse("EquipmentRepository.isSyncing must reset to false in finally", EquipmentRepository.isSyncing.value)
    }

    @Test
    fun testExceptionInRunGuaranteesResetToFalse() {
        assertFalse(EquipmentRepository.isSyncing.value)

        every { StravaHelper.getRefreshedAccessToken() } throws RuntimeException("Simulated catastrophic network failure")

        val thread = StravaEquipmentSynchronizeThread(mockContext)
        thread.run()

        assertFalse("EquipmentRepository.isSyncing MUST reset to false even when an unhandled exception occurs", EquipmentRepository.isSyncing.value)
    }

    @Test
    fun testThrowableInRunGuaranteesResetToFalse() {
        assertFalse(EquipmentRepository.isSyncing.value)

        every { StravaHelper.getRefreshedAccessToken() } throws OutOfMemoryError("Simulated OOM Error")

        val thread = StravaEquipmentSynchronizeThread(mockContext)
        thread.run()

        assertFalse("EquipmentRepository.isSyncing MUST reset to false even when an unhandled Throwable occurs", EquipmentRepository.isSyncing.value)
    }

    @Test
    fun testWorkerDebounceSuppressesDuplicateRuns() {
        EquipmentRepository.setSyncing(true)

        var helperCalled = false
        every { StravaHelper.getRefreshedAccessToken() } answers {
            helperCalled = true
            null
        }

        val thread = StravaEquipmentSynchronizeThread(mockContext)
        thread.run()

        assertFalse("Worker thread must abort immediately when isSyncing is already true (debounce guard)", helperCalled)
        assertTrue("isSyncing should remain true if already set before debounced call", EquipmentRepository.isSyncing.value)
    }

    @Test
    fun testSharedPreferencesNeverMutatedWithTransientNow() {
        val capturedTimestamps = mutableListOf<String>()
        every { TrainingApplication.setLastUpdateTimeOfStravaEquipment(capture(capturedTimestamps)) } just Runs

        val thread = StravaEquipmentSynchronizeThread(mockContext)
        thread.run()

        for (ts in capturedTimestamps) {
            assertNotEquals("SharedPreferences must NEVER be populated with transient string 'now'", "now", ts.lowercase())
            assertNotEquals("SharedPreferences must NEVER be populated with transient string 'jetzt'", "jetzt", ts.lowercase())
            assertNotEquals("SharedPreferences must NEVER be populated with transient string 'ahora'", "ahora", ts.lowercase())
            assertNotEquals("SharedPreferences must NEVER be populated with transient string 'maintenant'", "maintenant", ts.lowercase())
            assertNotEquals("SharedPreferences must NEVER be populated with transient string 'adesso'", "adesso", ts.lowercase())
            assertNotEquals("SharedPreferences must NEVER be populated with transient string '今'", "今", ts)
            assertNotEquals("SharedPreferences must NEVER be populated with transient string 'nu'", "nu", ts.lowercase())
            assertNotEquals("SharedPreferences must NEVER be populated with transient string 'teraz'", "teraz", ts.lowercase())
            assertNotEquals("SharedPreferences must NEVER be populated with transient string 'agora'", "agora", ts.lowercase())
        }
    }

    @Test
    fun testNineLanguageParityForLastUpdateOfEquipmentNow() {
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
            val actual = strings["lastUpdateOfEquipmentNow"]
            assertNotNull("Key 'lastUpdateOfEquipmentNow' must exist in $valuesDir/strings.xml", actual)
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
