/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.migration

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager.LapsDbHelper
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager
import com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepository
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusRepository
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.dropbox.core.oauth.DbxCredential
import com.google.android.gms.maps.model.LatLng
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Robolectric SQLite & Compose UI tests for silent default route cluster creation during workout
 * imports and opt-in interactive prompting (`ATT-9013`, `REQ-MIG-060`, `TST-MIG-060`).
 */
// Linked test specs: TST-MIG-060
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class SilentImportClusterCreationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var context: Context

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        context = app
        setStaticField(TrainingApplication::class.java, "cAppContext", context)
        setStaticField(
            TrainingApplication::class.java,
            "cSharedPreferences",
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        )
        cleanupState()
    }

    @After
    fun tearDown() {
        AutoImportManager.resetForTesting()
        unmockkAll()
        cleanupState()
        setStaticField(TrainingApplication::class.java, "cAppContext", null)
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", null)
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        } catch (_: Exception) {
        }
    }

    private fun cleanupState() {
        DatabaseConnectionManager.quiesceAllDatabases(context, closeConnections = true)
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        WorkoutRepository.resetForTesting(null)
        WorkoutClusterEngine.resetForTesting(null)
        WorkoutClusterRepository.resetForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        ExportStatusRepository.resetForTesting(null)
        ExportStatusDatabaseManager.resetForTesting(null)
        PeriodsRepository.resetInstanceForTesting()
        RoutesDatabaseManager.resetForTesting(null)
        KnownLocationsRepository.resetForTesting(null)
        listOf(
            WorkoutSummariesDbHelper.DB_NAME,
            WorkoutSamplesDbHelper.DB_NAME,
            LapsDbHelper.DB_NAME,
            "WorkoutCluster.db",
            "RouteClusters.db",
            "ExportStatus.db",
            "PeriodSummaries.db",
            "Routes.db",
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.DB_NAME
        ).forEach { context.deleteDatabase(it) }
    }

    private fun buildTcx(
        startIso: String,
        endIso: String,
        startLat: Double,
        startLng: Double,
        midLat: Double,
        midLng: Double,
        endLat: Double,
        endLng: Double,
        distanceMeters: Double,
        workoutName: String? = null,
        sport: String = "Biking"
    ): String {
        val extBlock = if (workoutName != null) {
            """
            <Extensions>
              <att:ActivityExtension>
                <att:Name>$workoutName</att:Name>
              </att:ActivityExtension>
            </Extensions>
            """.trimIndent()
        } else {
            ""
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"
              xmlns:att="http://atrainingtracker.com/xmlschemas/TrainingCenterDatabaseExtensions/v1">
              <Activities>
                <Activity Sport="$sport">
                  <Id>$startIso</Id>
                  <Lap StartTime="$startIso">
                    <TotalTimeSeconds>1800.0</TotalTimeSeconds>
                    <DistanceMeters>$distanceMeters</DistanceMeters>
                    <Track>
                      <Trackpoint>
                        <Time>$startIso</Time>
                        <Position><LatitudeDegrees>$startLat</LatitudeDegrees><LongitudeDegrees>$startLng</LongitudeDegrees></Position>
                        <DistanceMeters>0.0</DistanceMeters>
                      </Trackpoint>
                      <Trackpoint>
                        <Time>$startIso</Time>
                        <Position><LatitudeDegrees>$midLat</LatitudeDegrees><LongitudeDegrees>$midLng</LongitudeDegrees></Position>
                        <DistanceMeters>${distanceMeters / 2.0}</DistanceMeters>
                      </Trackpoint>
                      <Trackpoint>
                        <Time>$endIso</Time>
                        <Position><LatitudeDegrees>$endLat</LatitudeDegrees><LongitudeDegrees>$endLng</LongitudeDegrees></Position>
                        <DistanceMeters>$distanceMeters</DistanceMeters>
                      </Trackpoint>
                    </Track>
                  </Lap>
                  $extBlock
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()
    }

    private fun noPromptListener(promptCalls: AtomicInteger) = object : LegacyImportEngine.ProgressListener {
        override fun onProgress(current: Int, total: Int, name: String) {}
        override fun onStatus(message: String) {}
        override suspend fun onNewClusterCandidate(
            date: String,
            start: LatLng,
            end: LatLng,
            apex: LatLng,
            distance: Double,
            bSportType: com.atrainingtracker.banalservice.BSportType,
            polyline: String,
            workoutName: String?,
            candidateSportTypes: Set<com.atrainingtracker.banalservice.BSportType>,
            minAltPos: LatLng?,
            maxAltPos: LatLng?
        ): LegacyImportEngine.ClusterDecision {
            promptCalls.incrementAndGet()
            return LegacyImportEngine.ClusterDecision(null, null, null)
        }
    }

    @Test
    fun defaultSilentImport_createsClusterFromFirstWorkout_preservesCustomTitles_andAutoNamesUntitledMatches_TST_MIG_060_2() = runBlocking {
        assertFalse("askOnNewClusterDuringImport must default to false", TrainingApplication.askOnNewClusterDuringImport())

        val promptCalls = AtomicInteger(0)
        val listener = noPromptListener(promptCalls)
        val tempDir = java.nio.file.Files.createTempDirectory("silent_cluster_title").toFile()

        try {
            // 1. First workout with #4 hit-count suffix seeds cluster "Feierabendrunde"
            val file1 = File(tempDir, "w1.tcx").apply {
                writeText(
                    buildTcx(
                        startIso = "2026-08-01T17:00:00Z",
                        endIso = "2026-08-01T17:30:00Z",
                        startLat = 48.1000, startLng = 11.5000,
                        midLat = 48.1200, midLng = 11.5200,
                        endLat = 48.1001, endLng = 11.5001,
                        distanceMeters = 10000.0,
                        workoutName = "Feierabendrunde #4"
                    )
                )
            }
            val res1 = LegacyImportEngine.importFromTcxResult(context, file1, listener, uploadToStrava = false)
            assertEquals(LegacyImportEngine.ImportStatus.SUCCESS, res1.status)
            assertEquals("Default silent import must not prompt", 0, promptCalls.get())

            val clustersAfter1 = WorkoutClusterDatabaseManager.getInstance(context).getAllClusters()
            assertEquals(1, clustersAfter1.size)
            val createdCluster = clustersAfter1.first()
            assertEquals("Feierabendrunde", createdCluster.name)
            assertEquals(1, createdCluster.hitCount)

            val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context)
            val w1Id = res1.workoutId!!
            assertEquals(createdCluster.id, summariesDb.getLong(w1Id, WorkoutSummaries.CLUSTER_ID))
            assertEquals("Feierabendrunde #4", summariesDb.getString(w1Id, WorkoutSummaries.WORKOUT_NAME))

            // REQ-UI-186: starting location anchor seeded for novel start coordinate
            assertNotNull(
                "ensureStartingLocationAnchor must seed KnownLocations entry",
                KnownLocationsRepository.getInstance(context).getMyLocation(LatLng(48.1000, 11.5000))
            )

            // 2. Second workout on same route with distinct custom title preserves its own title
            val file2 = File(tempDir, "w2.tcx").apply {
                writeText(
                    buildTcx(
                        startIso = "2026-08-02T17:00:00Z",
                        endIso = "2026-08-02T17:30:00Z",
                        startLat = 48.1001, startLng = 11.5001,
                        midLat = 48.1202, midLng = 11.5202,
                        endLat = 48.1002, endLng = 11.5002,
                        distanceMeters = 10050.0,
                        workoutName = "Intervall-Training mit Peter"
                    )
                )
            }
            val res2 = LegacyImportEngine.importFromTcxResult(context, file2, listener, uploadToStrava = false)
            assertEquals(LegacyImportEngine.ImportStatus.SUCCESS, res2.status)
            assertEquals(0, promptCalls.get())
            val w2Id = res2.workoutId!!
            assertEquals(createdCluster.id, summariesDb.getLong(w2Id, WorkoutSummaries.CLUSTER_ID))
            assertEquals("Intervall-Training mit Peter", summariesDb.getString(w2Id, WorkoutSummaries.WORKOUT_NAME))
            assertEquals(2, WorkoutClusterDatabaseManager.getInstance(context).getClusterById(createdCluster.id)?.hitCount)

            // 3. Third untitled workout on same route gets auto-named "Feierabendrunde #3"
            val file3 = File(tempDir, "2026-08-03_17-00-00.tcx").apply {
                writeText(
                    buildTcx(
                        startIso = "2026-08-03T17:00:00Z",
                        endIso = "2026-08-03T17:30:00Z",
                        startLat = 48.1000, startLng = 11.5000,
                        midLat = 48.1201, midLng = 11.5201,
                        endLat = 48.1001, endLng = 11.5001,
                        distanceMeters = 9980.0,
                        workoutName = null
                    )
                )
            }
            val res3 = LegacyImportEngine.importFromTcxResult(context, file3, listener, uploadToStrava = false)
            assertEquals(LegacyImportEngine.ImportStatus.SUCCESS, res3.status)
            val w3Id = res3.workoutId!!
            assertEquals(createdCluster.id, summariesDb.getLong(w3Id, WorkoutSummaries.CLUSTER_ID))
            val expectedName3 = context.getString(R.string.cluster_autoname_format, "Feierabendrunde", 3)
            assertEquals(expectedName3, summariesDb.getString(w3Id, WorkoutSummaries.WORKOUT_NAME))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun silentImport_distantSameNamedWorkoutsCreateUniqueClustersWithoutCentroidCorruption_andFallbackForUntitled_TST_MIG_060_2() = runBlocking {
        val promptCalls = AtomicInteger(0)
        val listener = noPromptListener(promptCalls)
        val tempDir = java.nio.file.Files.createTempDirectory("silent_cluster_collision").toFile()

        try {
            // Seed a known location for the untitled Lieblingsort fallback test
            KnownLocationsRepository.getInstance(context).addLocation(
                name = "Zuhause",
                altitude = 600.0,
                radius = 300,
                latLng = LatLng(47.5000, 10.5000)
            )

            // 1. Distant Workout A titled "Morning Ride" at (48.10, 11.50)
            val fileA = File(tempDir, "ride_a.tcx").apply {
                writeText(
                    buildTcx(
                        startIso = "2026-08-04T07:00:00Z",
                        endIso = "2026-08-04T07:45:00Z",
                        startLat = 48.1000, startLng = 11.5000,
                        midLat = 48.1200, midLng = 11.5200,
                        endLat = 48.1000, endLng = 11.5000,
                        distanceMeters = 12000.0,
                        workoutName = "Morning Ride"
                    )
                )
            }
            val resA = LegacyImportEngine.importFromTcxResult(context, fileA, listener, uploadToStrava = false)
            assertEquals(LegacyImportEngine.ImportStatus.SUCCESS, resA.status)

            // 2. Spatially distant Workout B (~200km away) ALSO titled "Morning Ride"
            val fileB = File(tempDir, "ride_b.tcx").apply {
                writeText(
                    buildTcx(
                        startIso = "2026-08-05T07:00:00Z",
                        endIso = "2026-08-05T07:45:00Z",
                        startLat = 49.5000, startLng = 12.5000,
                        midLat = 49.5300, midLng = 12.5300,
                        endLat = 49.5000, endLng = 12.5000,
                        distanceMeters = 15000.0,
                        workoutName = "Morning Ride"
                    )
                )
            }
            val resB = LegacyImportEngine.importFromTcxResult(context, fileB, listener, uploadToStrava = false)
            assertEquals(LegacyImportEngine.ImportStatus.SUCCESS, resB.status)

            val clusters = WorkoutClusterDatabaseManager.getInstance(context).getAllClusters()
            val clusterA = clusters.first { it.name == "Morning Ride" }
            val clusterB = clusters.first { it.name == "Morning Ride var 2" }
            assertEquals("Cluster A centroid must stay uncorrupted at 48.10", 48.1000, clusterA.startLat, 1e-4)
            assertEquals("Cluster A hitCount must remain 1", 1, clusterA.hitCount)
            assertEquals("Cluster B centroid must be at 49.50", 49.5000, clusterB.startLat, 1e-4)
            assertEquals("Morning Ride", WorkoutSummariesDatabaseManager.getInstance(context).getString(resB.workoutId!!, WorkoutSummaries.WORKOUT_NAME))

            // 3. Untitled workout starting at Lieblingsort "Zuhause"
            val fileLoc = File(tempDir, "2026-08-06_09-00-00.tcx").apply {
                writeText(
                    buildTcx(
                        startIso = "2026-08-06T09:00:00Z",
                        endIso = "2026-08-06T09:30:00Z",
                        startLat = 47.5000, startLng = 10.5000,
                        midLat = 47.5200, midLng = 10.5200,
                        endLat = 47.5000, endLng = 10.5000,
                        distanceMeters = 8000.0,
                        workoutName = null
                    )
                )
            }
            val resLoc = LegacyImportEngine.importFromTcxResult(context, fileLoc, listener, uploadToStrava = false)
            assertEquals(LegacyImportEngine.ImportStatus.SUCCESS, resLoc.status)
            val expectedSeedName = context.getString(R.string.cluster_seed_name_loop_format, "Zuhause")
            assertTrue(
                "Expected cluster named '$expectedSeedName'",
                WorkoutClusterDatabaseManager.getInstance(context).getAllClusters().any { it.name == expectedSeedName }
            )

            // 4. Untitled workout with NO matching Lieblingsort -> date fallback
            val fileDate = File(tempDir, "2026-08-07_10-00-00.tcx").apply {
                writeText(
                    buildTcx(
                        startIso = "2026-08-07T10:00:00Z",
                        endIso = "2026-08-07T10:30:00Z",
                        startLat = 46.1000, startLng = 9.1000,
                        midLat = 46.1200, midLng = 9.1200,
                        endLat = 46.1000, endLng = 9.1000,
                        distanceMeters = 7000.0,
                        workoutName = null
                    )
                )
            }
            val resDate = LegacyImportEngine.importFromTcxResult(context, fileDate, listener, uploadToStrava = false)
            assertEquals(LegacyImportEngine.ImportStatus.SUCCESS, resDate.status)
            val expectedDateName = context.getString(R.string.cluster_default_name_format, "2026-08-07")
            assertTrue(
                "Expected date fallback cluster '$expectedDateName'",
                WorkoutClusterDatabaseManager.getInstance(context).getAllClusters().any { it.name == expectedDateName }
            )
            assertEquals(0, promptCalls.get())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun bulkImportBatch_seesSqlClustersWithoutPerWorkoutRefresh_and_optInToggleRestoresPromptWhileAutoImportStaysNonBlocking_TST_MIG_060_2() = runBlocking {
        val tempDir = java.nio.file.Files.createTempDirectory("silent_cluster_batch").toFile()
        val mockClusterRepo = spyk(WorkoutClusterRepository.getInstance(context))
        coEvery { mockClusterRepo.refreshClusters(any(), any()) } returns Unit
        WorkoutClusterRepository.resetForTesting(mockClusterRepo)

        try {
            val file1 = File(tempDir, "batch_1.tcx").apply {
                writeText(
                    buildTcx(
                        startIso = "2026-08-10T08:00:00Z",
                        endIso = "2026-08-10T08:30:00Z",
                        startLat = 48.2000, startLng = 11.6000,
                        midLat = 48.2200, midLng = 11.6200,
                        endLat = 48.2000, endLng = 11.6000,
                        distanceMeters = 9000.0,
                        workoutName = "Batch Trail"
                    )
                )
            }
            val file2 = File(tempDir, "batch_2.tcx").apply {
                writeText(
                    buildTcx(
                        startIso = "2026-08-11T08:00:00Z",
                        endIso = "2026-08-11T08:30:00Z",
                        startLat = 48.2001, startLng = 11.6001,
                        midLat = 48.2201, midLng = 11.6201,
                        endLat = 48.2001, endLng = 11.6001,
                        distanceMeters = 9050.0,
                        workoutName = "Second Batch Ride"
                    )
                )
            }

            withBulkImportBatch { batch ->
                LegacyImportEngine.importFromTcxResult(context, file1, null, uploadToStrava = false)
                LegacyImportEngine.importFromTcxResult(context, file2, null, uploadToStrava = false)
                reconcileBulkImportBatch(context, refreshClimbs = batch?.hasPersistedClimbs == true)
            }

            // Per-workout refreshClusters() was deferred; only 1 post-batch reconcile ran
            coVerify(exactly = 1) { mockClusterRepo.refreshClusters() }
            val clusters = WorkoutClusterDatabaseManager.getInstance(context).getAllClusters()
            assertEquals(1, clusters.size)
            assertEquals("Batch Trail", clusters.first().name)
            assertEquals(2, clusters.first().hitCount)

            // Opt-in interactive mode: manual listener IS prompted, AutoImportManager (listener=null) stays non-blocking
            mockkStatic(TrainingApplication::class)
            every { TrainingApplication.askOnNewClusterDuringImport() } returns true

            val promptCalls = AtomicInteger(0)
            val file3 = File(tempDir, "opt_in.tcx").apply {
                writeText(
                    buildTcx(
                        startIso = "2026-08-12T08:00:00Z",
                        endIso = "2026-08-12T08:30:00Z",
                        startLat = 50.0000, startLng = 8.0000,
                        midLat = 50.0200, midLng = 8.0200,
                        endLat = 50.0000, endLng = 8.0000,
                        distanceMeters = 11000.0,
                        workoutName = "Opt-In Route"
                    )
                )
            }
            LegacyImportEngine.importFromTcxResult(context, file3, noPromptListener(promptCalls), uploadToStrava = false)
            assertEquals("Interactive prompt must fire when askOnNewClusterDuringImport == true", 1, promptCalls.get())

            // AutoImportManager with askOnNewClusterDuringImport == true stays non-blocking and leaves novel route unclustered
            every { TrainingApplication.isAutoImportEnabled() } returns true
            every { TrainingApplication.isAutoImportDropboxEnabled() } returns true
            every { TrainingApplication.isAutoImportGoogleDriveEnabled() } returns false
            every { TrainingApplication.uploadToDropbox() } returns true
            every { TrainingApplication.readDropboxCredential() } returns mockk<DbxCredential>(relaxed = true)
            every { TrainingApplication.getAutoImportFormat() } returns "tcx"
            every { TrainingApplication.isSyncImportedWorkoutsToHealthConnect() } returns false
            every { TrainingApplication.uploadImportedWorkoutsToStrava() } returns false
            every { TrainingApplication.setAutoImportLastSyncTime(any()) } returns Unit
            every { TrainingApplication.setAutoImportLastSyncResult(any()) } returns Unit

            AutoImportManager.snapshotProvider = { ExistingWorkoutsSnapshot(emptySet()) }
            AutoImportManager.dropboxLister = { _, _, _ ->
                listOf(AutoImportManager.CloudFileItem("id_auto", "auto_novel.tcx", "/TCX/auto_novel.tcx"))
            }
            AutoImportManager.dropboxDownloader = { _, _, targetFile ->
                targetFile.writeText(
                    buildTcx(
                        startIso = "2026-08-13T08:00:00Z",
                        endIso = "2026-08-13T08:30:00Z",
                        startLat = 51.0000, startLng = 7.0000,
                        midLat = 51.0200, midLng = 7.0200,
                        endLat = 51.0000, endLng = 7.0000,
                        distanceMeters = 11000.0,
                        workoutName = "Auto Novel"
                    )
                )
                true
            }
            val syncRes = AutoImportManager.syncIncremental(context)
            assertEquals(1, syncRes.importedCount)
            assertEquals("AutoImport must stay non-blocking without creating cluster when interactive mode is on", 1, WorkoutClusterDatabaseManager.getInstance(context).getAllClusters().size)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun preImportTuningBottomSheet_defaultsAskToggleOff_andTogglingUpdatesViewModel_TST_MIG_060_3() {
        val vm = spyk(BackupRestoreViewModel(app))
        every { vm.saveClusteringTolerances() } returns Unit

        composeRule.setContent {
            ATrainingTrackerTheme {
                PreImportTuningBottomSheet(
                    viewModel = vm,
                    showStravaUploadOption = false,
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()

        assertFalse(vm.askOnNewClusterDuringImport)
        composeRule.onNodeWithText(context.getString(R.string.cluster_tuning_ask_on_new_import_label))
            .performScrollTo()
            .assertExists()
        val askSwitch = composeRule.onNode(
            isToggleable() and hasAnyAncestor(hasTestTag("cluster_tuning_ask_on_new_import_toggle"))
        )
        askSwitch.assertIsOff()
        askSwitch.performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        askSwitch.assertIsOn()
        assertTrue(vm.askOnNewClusterDuringImport)
        verify(atLeast = 1) { vm.saveClusteringTolerances() }
    }
}
