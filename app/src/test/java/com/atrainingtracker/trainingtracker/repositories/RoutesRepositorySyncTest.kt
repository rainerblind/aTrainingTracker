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
     * TST-PER-048.2 / TST-GEO-003: Cluster-to-Route rename sync updates only routes.name via
     * updateRouteNameByClusterId and patches _allRoutes in-place without calling
     * WorkoutClusterEngine.syncRouteNameChange or reloading route_points from SQLite
     * (REQ-PER-040.4, REQ-GEO-003, ATT-8133).
     */
    @Test
    fun testSyncClusterNameChange_updatesRouteWithoutCallingClusterEngine_TST_PER_048_2() = runBlocking {
        val mockEngine = mockk<WorkoutClusterEngine>(relaxed = true)
        val mockClimbsDb = mockk<ClimbsDatabaseManager>(relaxed = true)
        coEvery { mockClimbsDb.getClimbsForRoute(any()) } returns emptyList()
        WorkoutClusterEngine.resetForTesting(mockEngine)
        ClimbsDatabaseManager.resetForTesting(mockClimbsDb)

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
        val routePath = listOf(
            com.atrainingtracker.trainingtracker.ui.map.PathPoint(0.0, com.google.android.gms.maps.model.LatLng(48.0, 9.0), 300.0),
            com.atrainingtracker.trainingtracker.ui.map.PathPoint(12000.0, com.google.android.gms.maps.model.LatLng(48.1, 9.1), 450.0)
        )
        val routeWithPath = RouteWithPath(summary = summary, path = routePath)
        every { mockRoutesDb.getAllRoutes() } returns listOf(routeWithPath)
        every { mockRoutesDb.updateRouteNameByClusterId(10L, "New Cluster Name") } returns 1

        val repository = RoutesRepository.getInstance(mockContext)

        try {
            withTimeout(2000) {
                while (repository.allRoutes.value.firstOrNull()?.summary?.name != "Old Route") {
                    delay(10)
                }
            }
            clearMocks(mockRoutesDb, answers = false)
            every { mockRoutesDb.updateRouteNameByClusterId(10L, "New Cluster Name") } returns 1

            repository.syncClusterNameChange(10L, "New Cluster Name")

            assertEquals("New Cluster Name", repository.allRoutes.value.firstOrNull()?.summary?.name)
            assertEquals(routePath, repository.allRoutes.value.firstOrNull()?.path)
            verify(exactly = 1) { mockRoutesDb.updateRouteNameByClusterId(10L, "New Cluster Name") }
            verify(exactly = 0) { mockRoutesDb.getRouteByClusterId(any()) }
            verify(exactly = 0) { mockRoutesDb.getAllRoutes() }
            verify(exactly = 0) { mockRoutesDb.getRoutePath(any()) }
            verify(exactly = 0) { mockEngine.syncRouteNameChange(any(), any()) }
        } finally {
            repository.cancelScope()
            ClimbsDatabaseManager.resetForTesting(null)
            WorkoutClusterEngine.resetForTesting(null)
        }
    }

    /**
     * TST-PER-048.2 / TST-GEO-003: Route-to-Cluster rename patches _allRoutes in-place without
     * reloading route_points and notifies WorkoutClusterRepository.onRouteSyncedClusterRename
     * once without infinite recursion back into RoutesDatabaseManager.updateRouteSummary.
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

        val initialSummary = RouteSummary(
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
        val routePath = listOf(
            com.atrainingtracker.trainingtracker.ui.map.PathPoint(0.0, com.google.android.gms.maps.model.LatLng(48.0, 9.0), 300.0)
        )
        every { mockRoutesDb.getAllRoutes() } returns listOf(RouteWithPath(summary = initialSummary, path = routePath))
        val repository = RoutesRepository.getInstance(mockContext)

        try {
            withTimeout(2000) {
                while (repository.allRoutes.value.isEmpty()) {
                    delay(10)
                }
            }
            clearMocks(mockRoutesDb, answers = false)

            val summary = initialSummary.copy(name = "Route Renamed")
            repository.updateRouteSummary(summary)
            delay(50)

            assertEquals("Route Renamed", repository.allRoutes.value.firstOrNull()?.summary?.name)
            assertEquals(routePath, repository.allRoutes.value.firstOrNull()?.path)
            verify(exactly = 1) { mockRoutesDb.updateRouteSummary(summary) }
            verify(exactly = 0) { mockRoutesDb.getAllRoutes() }
            verify(exactly = 0) { mockRoutesDb.getRoutePath(any()) }
            verify(exactly = 1) { mockEngine.syncRouteNameChange(10L, "Route Renamed") }
            coVerify(exactly = 1) { mockClusterRepo.onRouteSyncedClusterRename(10L) }
        } finally {
            repository.cancelScope()
            ClimbsDatabaseManager.resetForTesting(null)
            WorkoutClusterRepository.resetForTesting(null)
            WorkoutClusterEngine.resetForTesting(null)
        }
    }

    /**
     * TST-GEO-003 (ATT-8133): Verifies updateRouteSummary runs WorkoutClusterEngine.syncRouteNameChange
     * strictly on ioDispatcher (never Main UI thread) and reloads via refreshRoutes() when the updated
     * route ID is missing from a non-empty _allRoutes cache.
     */
    @Test
    fun testUpdateRouteSummary_runsClusterSyncOnIoDispatcherAndReloadsWhenMissing_TST_GEO_003() = runBlocking {
        val mockEngine = mockk<WorkoutClusterEngine>(relaxed = true)
        val mockClusterRepo = mockk<WorkoutClusterRepository>(relaxed = true)
        val mockClimbsDb = mockk<ClimbsDatabaseManager>(relaxed = true)
        coEvery { mockClimbsDb.getClimbsForRoute(any()) } returns emptyList()
        WorkoutClusterEngine.resetForTesting(mockEngine)
        WorkoutClusterRepository.resetForTesting(mockClusterRepo)
        ClimbsDatabaseManager.resetForTesting(mockClimbsDb)

        val ioThreadName = "att-8133-test-io"
        val ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, ioThreadName)
        }
        val testIoDispatcher = ioExecutor.asCoroutineDispatcher()

        val observedSyncThreads = mutableListOf<String>()
        every { mockEngine.syncRouteNameChange(any(), any()) } answers {
            observedSyncThreads.add(Thread.currentThread().name)
        }

        val existingSummary = RouteSummary(
            id = 1L, externalId = "ext-1", name = "Existing Route", description = "",
            isSelected = false, distance = 5000.0, elevationGain = 50.0,
            bSportType = BSportType.BIKE, source = RouteSource.LOCAL_GPX, clusterId = 1L
        )
        every { mockRoutesDb.getAllRoutes() } returns listOf(RouteWithPath(existingSummary, emptyList()))

        val repository = RoutesRepository(mockContext, mockRoutesDb, mockClimbsDb, testIoDispatcher)
        try {
            withTimeout(2000) {
                while (repository.allRoutes.value.isEmpty()) {
                    delay(10)
                }
            }
            clearMocks(mockRoutesDb, answers = false)
            every { mockRoutesDb.getAllRoutes() } returns listOf(RouteWithPath(existingSummary, emptyList()))

            // Update a route ID (99L) absent from non-empty _allRoutes -> must execute sync on ioDispatcher AND reload
            val missingSummary = existingSummary.copy(id = 99L, name = "Missing Route", clusterId = 99L)
            repository.updateRouteSummary(missingSummary)

            assertEquals(1, observedSyncThreads.size)
            assertTrue(
                "Expected cluster sync on $ioThreadName, got ${observedSyncThreads.first()}",
                observedSyncThreads.first().startsWith(ioThreadName)
            )
            verify(timeout = 1000, exactly = 1) { mockRoutesDb.getAllRoutes() }
        } finally {
            repository.cancelScope()
            ioExecutor.shutdownNow()
            ClimbsDatabaseManager.resetForTesting(null)
            WorkoutClusterRepository.resetForTesting(null)
            WorkoutClusterEngine.resetForTesting(null)
        }
    }
}
