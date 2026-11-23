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
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.dropbox.core.oauth.DbxCredential
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Job
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

/**
 * Behavioral Robolectric Compose UI tests for sealed dialog and pre-import tuning state models
 * in [ImportBackupTabsScreen] (`REQ-UI-2790.1`, `TST-UI-2790.1`, `ATT-6040`).
 *
 * Strictly performs zero `src/main` file reads (`REQ-PRO-049.1`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class ImportBackupTabsSealedStateComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var context: Context

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        context = app
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun stubTrainingApplication(
        dropboxConnected: Boolean,
        googleDriveConnected: Boolean,
        uploadToStrava: Boolean = false
    ) {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getAppContext() } returns context
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.uploadToDropbox() } returns dropboxConnected
        every { TrainingApplication.uploadBackupToDropbox() } returns dropboxConnected
        every { TrainingApplication.readDropboxCredential() } returns if (dropboxConnected) mockk<DbxCredential>(relaxed = true) else null
        every { TrainingApplication.uploadToGoogleDrive() } returns googleDriveConnected
        every { TrainingApplication.uploadBackupToGoogleDrive() } returns googleDriveConnected
        every { TrainingApplication.uploadToStrava() } returns uploadToStrava
        every { TrainingApplication.uploadImportedWorkoutsToStrava() } returns uploadToStrava
        every { TrainingApplication.getClusterTolEndpoints() } returns 100f
        every { TrainingApplication.getClusterTolApex() } returns 100f
        every { TrainingApplication.getClusterTolDistance() } returns 100f
        every { TrainingApplication.getClusterTolAltitudePos() } returns 100f
        every { TrainingApplication.useSportTypeForClustering() } returns false
        every { TrainingApplication.useAltitudePosForClustering() } returns false
        every { TrainingApplication.getClusterScoreThreshold() } returns 0.5f
        every { TrainingApplication.isAutoImportDropboxEnabled() } returns false
        every { TrainingApplication.isAutoImportGoogleDriveEnabled() } returns false
        every { TrainingApplication.getAutoImportLastSyncTime() } returns 0L
        every { TrainingApplication.getAutoImportLastSyncResult() } returns ""
    }

    private fun navigateToTab(tabTitleRes: Int) {
        composeRule.onNodeWithText(context.getString(tabTitleRes)).performClick()
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
    }

    private fun confirmLatestButton(labelRes: Int) {
        val nodes = composeRule.onAllNodesWithText(context.getString(labelRes))
        val count = nodes.fetchSemanticsNodes().size
        assertTrue("Expected at least 1 button for $labelRes", count >= 1)
        nodes[count - 1].performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun testPendingRestoreSource_autoDropboxAndGoogleDriveRestoreDialogs_cancelAndConfirmDispatch_tstUi2790_1() {
        stubTrainingApplication(dropboxConnected = true, googleDriveConnected = true)
        val backupDir = java.io.File(context.filesDir, "backups").apply { mkdirs() }
        val autoBackupFile = java.io.File(backupDir, "aTrainingTracker_backup.attbackup").apply {
            writeText("mock-backup-bytes")
        }
        try {
            val vm = spyk(BackupRestoreViewModel(app))
            every { vm.restoreFromLocalBackup(any()) } returns Unit
            every { vm.restoreFromDropbox(any()) } returns Unit
            every { vm.restoreFromGoogleDrive(any()) } returns Unit

            composeRule.setContent {
                ATrainingTrackerTheme {
                    ImportBackupTabsScreen(viewModel = vm)
                }
            }

            navigateToTab(R.string.tab_backup)
            navigateToTab(R.string.tab_restore)

            // 1. Automatic local backup restore -> Cancel blocks, Confirm dispatches once
            composeRule.onNodeWithText(context.getString(R.string.restore_automatic_local_backup))
                .performScrollTo()
                .performClick()
            composeRule.onNodeWithText(context.getString(R.string.restore_warning_title)).assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
            verify(exactly = 0) { vm.restoreFromLocalBackup(any()) }

            composeRule.onNodeWithText(context.getString(R.string.restore_automatic_local_backup))
                .performScrollTo()
                .performClick()
            confirmLatestButton(R.string.restore_backup)
            verify(exactly = 1) { vm.restoreFromLocalBackup(any()) }

            // 2. Dropbox restore -> Cancel blocks, Confirm dispatches once
            composeRule.onNodeWithText(context.getString(R.string.restore_from_dropbox))
                .performScrollTo()
                .performClick()
            composeRule.onNodeWithText(context.getString(R.string.restore_warning_title)).assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
            verify(exactly = 0) { vm.restoreFromDropbox(any()) }

            composeRule.onNodeWithText(context.getString(R.string.restore_from_dropbox))
                .performScrollTo()
                .performClick()
            confirmLatestButton(R.string.restore_backup)
            verify(exactly = 1) { vm.restoreFromDropbox(any()) }

            // 3. Google Drive restore -> Cancel blocks, Confirm dispatches once
            composeRule.onNodeWithText(context.getString(R.string.restore_from_google_drive))
                .performScrollTo()
                .performClick()
            composeRule.onNodeWithText(context.getString(R.string.restore_warning_title)).assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
            verify(exactly = 0) { vm.restoreFromGoogleDrive(any()) }

            composeRule.onNodeWithText(context.getString(R.string.restore_from_google_drive))
                .performScrollTo()
                .performClick()
            confirmLatestButton(R.string.restore_backup)
            verify(exactly = 1) { vm.restoreFromGoogleDrive(any()) }
        } finally {
            autoBackupFile.delete()
        }
    }

    @Test
    fun testCloudInfoDialog_disconnectedAndNoCloudPaths_showUnifiedAlertAndNeverOpenRestoreConfirm_tstUi2790_1() {
        stubTrainingApplication(dropboxConnected = false, googleDriveConnected = false)
        val vm = spyk(BackupRestoreViewModel(app))

        composeRule.setContent {
            ATrainingTrackerTheme {
                ImportBackupTabsScreen(viewModel = vm)
            }
        }

        // 1. Tab 1 (Import): Dropbox scan while disconnected -> DropboxDisconnected dialog
        navigateToTab(R.string.tab_import)
        composeRule.onNodeWithText(context.getString(R.string.import_fit_dropbox_button))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.Dropbox)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.dropbox_disconnected_status)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.OK)).performClick()

        // 2. Tab 2 (Backup): Upload to cloud while neither connected -> NoCloudConnected dialog
        navigateToTab(R.string.tab_backup)
        composeRule.onNodeWithText(context.getString(R.string.create_and_upload_to_cloud))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.cloud_backup_no_provider_dialog_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.cloud_backup_no_provider_dialog_message)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.OK)).performClick()

        // 3. Tab 3 (Restore): Disconnected Dropbox & Google Drive -> info dialogs without destructive prompt
        navigateToTab(R.string.tab_restore)
        composeRule.onNodeWithText(context.getString(R.string.restore_from_dropbox))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.dropbox_disconnected_status)).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText(context.getString(R.string.restore_warning_title)).fetchSemanticsNodes().size)
        composeRule.onNodeWithText(context.getString(R.string.OK)).performClick()

        composeRule.onNodeWithText(context.getString(R.string.restore_from_google_drive))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.google_drive_disconnected_status)).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText(context.getString(R.string.restore_warning_title)).fetchSemanticsNodes().size)
        composeRule.onNodeWithText(context.getString(R.string.OK)).performClick()
    }

    @Test
    fun testPendingImportSource_dropboxBulkConfirmAndStravaStravaOptionSuppression_tstUi2790_1() {
        stubTrainingApplication(dropboxConnected = true, googleDriveConnected = false, uploadToStrava = true)
        val vm = spyk(BackupRestoreViewModel(app))
        every { vm.bulkRecoverLegacyData(any(), any(), any()) } returns Job()

        composeRule.setContent {
            ATrainingTrackerTheme {
                ImportBackupTabsScreen(viewModel = vm)
            }
        }

        // Navigate to Tab 1 (Import) and trigger PendingImportSource.DropboxBulk("fit") via UI click -> opens PreImportTuningBottomSheet
        navigateToTab(R.string.tab_import)
        composeRule.onNodeWithText(context.getString(R.string.import_fit_dropbox_button))
            .performScrollTo()
            .performClick()
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.cluster_tuning_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.import_upload_to_strava_label)).assertIsDisplayed()
        val saveNodes = composeRule.onAllNodesWithText(context.getString(R.string.save))
        val saveCount = saveNodes.fetchSemanticsNodes().size
        assertTrue("Expected Save button in PreImportTuningBottomSheet", saveCount >= 1)
        saveNodes[saveCount - 1].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        verify(exactly = 1) { vm.bulkRecoverLegacyData(any(), "fit", any()) }
    }

    @Test
    fun testPendingImportSource_stravaArchiveHidesStravaUploadOptionWhileOtherVariantsShowIt_tstUi2790_1() {
        stubTrainingApplication(dropboxConnected = true, googleDriveConnected = false, uploadToStrava = true)
        val vm = spyk(BackupRestoreViewModel(app))
        val uri = Uri.parse("content://test/archive.zip")

        val stravaSource: PendingImportSource = PendingImportSource.StravaArchive(uri)
        val legacySource: PendingImportSource = PendingImportSource.LegacyFiles(listOf(uri), "gpx")
        val fitSource: PendingImportSource = PendingImportSource.FitFiles(listOf(uri))
        val directorySource: PendingImportSource = PendingImportSource.Directory(uri, "fit")
        val dropboxSource: PendingImportSource = PendingImportSource.DropboxBulk("tcx")

        assertFalse("StravaArchive must suppress Strava upload toggle", stravaSource.showStravaUploadOption)
        assertTrue(legacySource.showStravaUploadOption)
        assertTrue(fitSource.showStravaUploadOption)
        assertTrue(directorySource.showStravaUploadOption)
        assertTrue(dropboxSource.showStravaUploadOption)

        composeRule.setContent {
            ATrainingTrackerTheme {
                PreImportTuningBottomSheet(
                    viewModel = vm,
                    showStravaUploadOption = stravaSource.showStravaUploadOption,
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.cluster_tuning_title)).assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithText(context.getString(R.string.import_upload_to_strava_label)).fetchSemanticsNodes().size
        )
    }
}
