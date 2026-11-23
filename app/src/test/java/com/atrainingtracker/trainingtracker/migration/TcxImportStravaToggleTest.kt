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

package com.atrainingtracker.trainingtracker.migration

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager.LapsDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.dropbox.core.DbxDownloader
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.DbxUserFilesRequests
import com.dropbox.core.v2.files.DbxUserListFolderBuilder
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.ListFolderResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.OutputStream

/**
 * Robolectric & Compose tests verifying optional Strava upload control during TCX import:
 * - `importFromTcx(uploadToStrava = false/true)` persisting `UPLOAD_TO_STRAVA` (`0`/`1`) and gating `ExportManager`
 * - `bulkRecoverFromDropbox(uploadToStrava = false)` forwarding flag
 * - `PreImportTuningBottomSheet` toggle visibility when Strava is connected vs disconnected
 * (REQ-MIG-028, TST-MIG-025, REQ-MIG-054, TST-MIG-052).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TcxImportStravaToggleTest {

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
        TrainingApplication.setStravaAccessToken(null)
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        WorkoutRepository.resetForTesting(null)
        WorkoutClusterEngine.resetForTesting(null)
        com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository.resetForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusRepository.resetForTesting(null)
        com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager.resetForTesting(null)
        com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepository.resetInstanceForTesting()
        com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager.resetForTesting(null)
        listOf(
            WorkoutSummariesDbHelper.DB_NAME,
            WorkoutSamplesDbHelper.DB_NAME,
            LapsDbHelper.DB_NAME,
            "WorkoutCluster.db",
            "RouteClusters.db",
            "ExportStatus.db",
            "PeriodSummaries.db",
            "Routes.db"
        ).forEach { context.deleteDatabase(it) }
    }

    private fun sampleTcx(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
          <Activities>
            <Activity Sport="Biking">
              <Id>2026-08-06T08:00:00Z</Id>
              <Lap StartTime="2026-08-06T08:00:00Z">
                <TotalTimeSeconds>60.0</TotalTimeSeconds>
                <DistanceMeters>400.0</DistanceMeters>
                <Track>
                  <Trackpoint>
                    <Time>2026-08-06T08:00:00Z</Time>
                    <Position><LatitudeDegrees>48.2</LatitudeDegrees><LongitudeDegrees>11.2</LongitudeDegrees></Position>
                    <DistanceMeters>0.0</DistanceMeters>
                  </Trackpoint>
                  <Trackpoint>
                    <Time>2026-08-06T08:01:00Z</Time>
                    <Position><LatitudeDegrees>48.203</LatitudeDegrees><LongitudeDegrees>11.203</LongitudeDegrees></Position>
                    <DistanceMeters>400.0</DistanceMeters>
                  </Trackpoint>
                </Track>
              </Lap>
            </Activity>
          </Activities>
        </TrainingCenterDatabase>
    """.trimIndent()

    @Test
    fun testImportFromTcx_uploadToStravaFalseVsTrue_persistsFlagAndSchedulesExport_TST_MIG_025() = runBlocking {
        TrainingApplication.injectMockStravaAccount()
        TrainingApplication.setUploadImportedWorkoutsToStrava(false)

        // REQ-MIG-054, TST-MIG-052: Leave RouteClusters.db empty so refreshClusters() runs migrateHistory() ->
        // assignClusterToWorkout() -> applyInferredIdentity() and verify explicit UPLOAD_TO_STRAVA = 0 stays 0.
        assertTrue(WorkoutClusterDatabaseManager.getInstance(context).getAllClusters().isEmpty())

        mockkConstructor(ExportManager::class)
        every { anyConstructed<ExportManager>().exportWorkoutTo(any<Long>(), any<FileFormat>()) } returns Unit
        every { anyConstructed<ExportManager>().newWorkout(any<String>()) } returns Unit

        val tempDir = java.nio.file.Files.createTempDirectory("tcx_strava_opt").toFile()
        try {
            // 1. uploadToStrava = false -> UPLOAD_TO_STRAVA = 0, no Strava export scheduled
            val fileOff = File(tempDir, "2026-08-06_08-00-00.tcx").apply { writeText(sampleTcx()) }
            val resOff = LegacyImportEngine.importFromTcxResult(context, fileOff, null, uploadToStrava = false)
            assertEquals(LegacyImportEngine.ImportStatus.SUCCESS, resOff.status)
            val idOff = resOff.workoutId!!

            val db = WorkoutSummariesDatabaseManager.getInstance(context).database
            db.query(
                WorkoutSummaries.TABLE,
                arrayOf(
                    WorkoutSummaries.UPLOAD_TO_STRAVA,
                    WorkoutSummaries.CLUSTER_ID,
                    WorkoutSummaries.SPORT_ID,
                    WorkoutSummaries.FILE_BASE_NAME,
                    WorkoutSummaries.SOURCE
                ),
                "${WorkoutSummaries.C_ID} = ?",
                arrayOf(idOff.toString()),
                null, null, null
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                val uploadVal = cursor.getInt(0)
                val clusterVal = cursor.getLong(1)
                val sportVal = cursor.getLong(2)
                val fbn = cursor.getString(3)
                val src = cursor.getString(4)
                assertEquals(
                    "Expected UPLOAD_TO_STRAVA=0 for idOff=$idOff (cluster=$clusterVal, sport=$sportVal, file=$fbn, src=$src)",
                    0,
                    uploadVal
                )
            }
            verify(exactly = 0) { anyConstructed<ExportManager>().exportWorkoutTo(idOff, FileFormat.STRAVA) }

            // 2. uploadToStrava = true -> UPLOAD_TO_STRAVA = 1, Strava export scheduled
            val fileOn = File(tempDir, "2026-08-07_08-00-00.tcx").apply {
                writeText(sampleTcx().replace("2026-08-06", "2026-08-07"))
            }
            val resOn = LegacyImportEngine.importFromTcxResult(context, fileOn, null, uploadToStrava = true)
            assertEquals(LegacyImportEngine.ImportStatus.SUCCESS, resOn.status)
            val idOn = resOn.workoutId!!

            db.query(
                WorkoutSummaries.TABLE,
                arrayOf(WorkoutSummaries.UPLOAD_TO_STRAVA),
                "${WorkoutSummaries.C_ID} = ?",
                arrayOf(idOn.toString()),
                null, null, null
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            verify(exactly = 1) { anyConstructed<ExportManager>().exportWorkoutTo(idOn, FileFormat.STRAVA) }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testBulkRecoverFromDropbox_forwardsUploadToStravaFlag_TST_MIG_025() = runBlocking {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.readDropboxCredential() } returns mockk<DbxCredential>(relaxed = true)
        every { TrainingApplication.uploadImportedWorkoutsToStrava() } returns true

        val entry = mockk<FileMetadata>(relaxed = true) {
            every { name } returns "2026-08-08_09-00-00.tcx"
            every { pathLower } returns "/tcx/2026-08-08_09-00-00.tcx"
        }
        val page = mockk<ListFolderResult>(relaxed = true) {
            every { entries } returns listOf(entry)
            every { hasMore } returns false
        }
        val emptyPage = mockk<ListFolderResult>(relaxed = true) {
            every { entries } returns emptyList()
            every { hasMore } returns false
        }
        val builderTcx = mockk<DbxUserListFolderBuilder>(relaxed = true)
        every { builderTcx.withRecursive(true) } returns builderTcx
        every { builderTcx.start() } returns page
        val builderApps = mockk<DbxUserListFolderBuilder>(relaxed = true)
        every { builderApps.withRecursive(true) } returns builderApps
        every { builderApps.start() } returns emptyPage

        val downloader = mockk<DbxDownloader<FileMetadata>>(relaxed = true)
        every { downloader.download(any<OutputStream>()) } returns mockk<FileMetadata>(relaxed = true)

        val filesReq = mockk<DbxUserFilesRequests>(relaxed = true)
        every { filesReq.listFolderBuilder("/TCX") } returns builderTcx
        every { filesReq.listFolderBuilder("/apps/Workouts/TCX") } returns builderApps
        every { filesReq.download(any()) } returns downloader
        mockkConstructor(DbxClientV2::class)
        every { anyConstructed<DbxClientV2>().files() } returns filesReq

        mockkObject(LegacyImportEngine)
        coEvery {
            LegacyImportEngine.importFromTcxInternal(any(), any(), any(), false)
        } returns LegacyImportEngine.ImportStatus.SUCCESS

        val result = LegacyImportEngine.bulkRecoverFromDropbox(context, "tcx", null, uploadToStrava = false)
        assertEquals(1, result.importedCount)
        coVerify(exactly = 1) {
            LegacyImportEngine.importFromTcxInternal(any(), any(), null, false)
        }
    }

    @Test
    fun testPreImportTuningBottomSheet_showsSwitchWhenStravaConnectedAndPersistsToggle_TST_MIG_025() {
        TrainingApplication.injectMockStravaAccount()
        TrainingApplication.setUploadImportedWorkoutsToStrava(false)

        val vm = BackupRestoreViewModel(app)
        assertFalse(vm.uploadToStravaOnImport)

        composeRule.setContent {
            ATrainingTrackerTheme {
                PreImportTuningBottomSheet(
                    viewModel = vm,
                    showStravaUploadOption = true,
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        val label = context.getString(R.string.import_upload_to_strava_label)
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(label).performScrollTo().assertExists()
        val toggles = composeRule.onAllNodes(isToggleable())
        val stravaSwitch = toggles[toggles.fetchSemanticsNodes().lastIndex]
        stravaSwitch.assertIsOff()
        stravaSwitch.performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        stravaSwitch.assertIsOn()
        assertTrue("ViewModel uploadToStravaOnImport must update to true", vm.uploadToStravaOnImport)
        assertTrue("Persistent preference must update to true", TrainingApplication.uploadImportedWorkoutsToStrava())
    }

    @Test
    fun testPreImportTuningBottomSheet_hidesSwitchWhenStravaDisconnected_TST_MIG_025() {
        TrainingApplication.setStravaAccessToken(null)
        TrainingApplication.setUploadImportedWorkoutsToStrava(false)

        val vm = BackupRestoreViewModel(app)
        composeRule.setContent {
            ATrainingTrackerTheme {
                PreImportTuningBottomSheet(
                    viewModel = vm,
                    showStravaUploadOption = true,
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.import_upload_to_strava_label)).assertDoesNotExist()
    }
}
