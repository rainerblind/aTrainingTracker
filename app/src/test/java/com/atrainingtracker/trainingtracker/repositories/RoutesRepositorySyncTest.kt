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

import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.ClimbsDatabaseManager
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
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
 * Unit tests verifying reactive Strava route sync progress, in-memory StateFlow lifecycle,
 * persistent storage decoupling (no "now" written to SharedPreferences), defensive cleanup,
 * and 9-language resource parity (REQ-EXP-015, TST-EXP-012, ATT-1230).
 */
class RoutesRepositorySyncTest {

    private lateinit var mockContext: Context
    private lateinit var mockRoutesDb: RoutesDatabaseManager

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

        mockRoutesDb = mockk(relaxed = true)
        every { mockRoutesDb.pruneExpiredStravaRoutes(any()) } returns 0
        every { mockRoutesDb.getAllRoutes() } returns emptyList()

        RoutesDatabaseManager.resetForTesting(mockRoutesDb)

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getStravaAccessToken() } returns null // Avoid auto-sync on init
        every { TrainingApplication.getLastUpdateTimeOfStravaRoutes() } returns "01.01.2026, 12:00"
        every { TrainingApplication.setLastUpdateTimeOfStravaRoutes(any()) } just Runs

        mockkStatic(StravaHelper::class)
        every { StravaHelper.getRefreshedAccessToken() } returns null

        RoutesRepository.resetForTesting(null)
    }

    @After
    fun tearDown() {
        RoutesRepository.resetForTesting(null)
        RoutesDatabaseManager.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testIsSyncingInitialStateIsFalse() {
        val repository = RoutesRepository.getInstance(mockContext)
        assertFalse("isSyncing must initially be false", repository.isSyncing.value)
    }

    @Test
    fun testIsSyncingTransitionsDuringSyncAndResetsOnCompletion() = runBlocking {
        val repository = RoutesRepository.getInstance(mockContext)
        assertFalse(repository.isSyncing.value)

        val syncJob = launch {
            repository.syncRoutesFromStrava()
        }
        syncJob.join()

        assertFalse("isSyncing must reset to false after completion", repository.isSyncing.value)
    }

    @Test
    fun testIsSyncingEmitsTrueWhileSyncingIsActive() = runBlocking {
        every { StravaHelper.getRefreshedAccessToken() } coAnswers {
            delay(100)
            null
        }

        val repository = RoutesRepository.getInstance(mockContext)
        assertFalse(repository.isSyncing.value)

        val syncJob = launch {
            repository.syncRoutesFromStrava()
        }

        // Allow coroutine to start and enter mutex
        delay(30)
        assertTrue("isSyncing must be true while sync is actively in progress", repository.isSyncing.value)

        syncJob.join()
        assertFalse("isSyncing must reset to false after completion", repository.isSyncing.value)
    }

    @Test
    fun testIsSyncingResetsToFalseOnExceptionInFinally() = runBlocking {
        val repository = RoutesRepository.getInstance(mockContext)
        assertFalse(repository.isSyncing.value)

        // Force an exception during getRefreshedAccessToken
        every { StravaHelper.getRefreshedAccessToken() } throws IOException("Simulated network/DB error")

        try {
            repository.syncRoutesFromStrava()
            fail("Expected IOException was not thrown")
        } catch (e: IOException) {
            assertEquals("Simulated network/DB error", e.message)
        }

        assertFalse("isSyncing MUST reset to false in finally block even after exception", repository.isSyncing.value)
    }

    @Test
    fun testSharedPreferencesNeverMutatedWithTransientNowString() = runBlocking {
        val capturedTimestamps = mutableListOf<String>()
        every { TrainingApplication.setLastUpdateTimeOfStravaRoutes(capture(capturedTimestamps)) } just Runs

        val repository = RoutesRepository.getInstance(mockContext)
        repository.syncRoutesFromStrava()

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
    fun testNineLanguageParityForLastUpdateOfRoutesNow() {
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
            val actual = strings["lastUpdateOfRoutesNow"]
            assertNotNull("Key 'lastUpdateOfRoutesNow' must exist in $valuesDir/strings.xml", actual)
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

    /**
     * TST-PER-048.2: Cluster-to-Route rename sync updates SQLite and _allRoutes without calling
     * WorkoutClusterEngine.syncRouteNameChange (loop guard, REQ-PER-040.2).
     */
    @Test
    fun testSyncClusterNameChange_updatesRouteWithoutCallingClusterEngine_TST_PER_048_2() = runBlocking {
        val mockEngine = mockk<WorkoutClusterEngine>(relaxed = true)
        val mockClimbsDb = mockk<ClimbsDatabaseManager>(relaxed = true)
        coEvery { mockClimbsDb.getClimbsForRoute(any()) } returns emptyList()
        WorkoutClusterEngine.resetForTesting(mockEngine)
        ClimbsDatabaseManager.resetForTesting(mockClimbsDb)
        val repository = RoutesRepository.getInstance(mockContext)

        try {
            val summary = RouteSummary(
                id = 7L,
                externalId = "ext-7",
                name = "Old Route",
                description = "",
                isSelected = false,
                distance = 12000.0,
                elevationGain = 150.0,
                bSportType = BSportType.BIKE,
                source = RouteSource.LOCAL_GPX,
                clusterId = 10L
            )
            val routeWithPath = RouteWithPath(summary = summary, path = emptyList())
            every { mockRoutesDb.getRouteByClusterId(10L) } returns routeWithPath
            every { mockRoutesDb.getAllRoutes() } returns listOf(
                routeWithPath.copy(summary = summary.copy(name = "New Cluster Name"))
            )

            repository.syncClusterNameChange(10L, "New Cluster Name")
            withTimeout(2000) {
                while (repository.allRoutes.value.firstOrNull()?.summary?.name != "New Cluster Name") {
                    delay(10)
                }
            }

            verify(exactly = 1) {
                mockRoutesDb.updateRouteSummary(match { it.id == 7L && it.name == "New Cluster Name" })
            }
            verify(exactly = 0) { mockEngine.syncRouteNameChange(any(), any()) }
            assertEquals("New Cluster Name", repository.allRoutes.value.firstOrNull()?.summary?.name)
        } finally {
            repository.cancelScope()
            ClimbsDatabaseManager.resetForTesting(null)
            WorkoutClusterEngine.resetForTesting(null)
        }
    }

    /**
     * TST-PER-048.2: Route-to-Cluster rename notifies WorkoutClusterRepository.onRouteSyncedClusterRename
     * once without infinite recursion back into RoutesDatabaseManager.updateRouteSummary (REQ-PER-040.2).
     */
    @Test
    fun testUpdateRouteSummary_propagatesToClusterRepositoryWithoutRecursion_TST_PER_048_2() = runBlocking {
        val mockEngine = mockk<WorkoutClusterEngine>(relaxed = true)
        val mockClusterRepo = mockk<WorkoutClusterRepository>(relaxed = true)
        val mockClimbsDb = mockk<ClimbsDatabaseManager>(relaxed = true)
        coEvery { mockClimbsDb.getClimbsForRoute(any()) } returns emptyList()
        WorkoutClusterEngine.resetForTesting(mockEngine)
        WorkoutClusterRepository.resetForTesting(mockClusterRepo)
        ClimbsDatabaseManager.resetForTesting(mockClimbsDb)
        val repository = RoutesRepository.getInstance(mockContext)

        try {
            val summary = RouteSummary(
                id = 7L,
                externalId = "ext-7",
                name = "Route Renamed",
                description = "",
                isSelected = false,
                distance = 12000.0,
                elevationGain = 150.0,
                bSportType = BSportType.BIKE,
                source = RouteSource.LOCAL_GPX,
                clusterId = 10L
            )
            repository.updateRouteSummary(summary)
            delay(50)

            verify(exactly = 1) { mockRoutesDb.updateRouteSummary(summary) }
            verify(exactly = 1) { mockEngine.syncRouteNameChange(10L, "Route Renamed") }
            coVerify(exactly = 1) { mockClusterRepo.onRouteSyncedClusterRename(10L) }
        } finally {
            repository.cancelScope()
            ClimbsDatabaseManager.resetForTesting(null)
            WorkoutClusterRepository.resetForTesting(null)
            WorkoutClusterEngine.resetForTesting(null)
        }
    }
}
