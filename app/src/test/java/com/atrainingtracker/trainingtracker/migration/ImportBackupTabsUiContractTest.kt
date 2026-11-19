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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.testing.LocalizationTestCache
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.dropbox.core.oauth.DbxCredential
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavioral Compose UI and localization contract tests for [ImportBackupTabsScreen],
 * [BackupTabContentLayout], [RestoreTabContent], and Import tab terminology:
 * - Dropbox full-restore confirmation dialog gating (`REQ-MIG-007`, `TST-MIG-004`)
 * - Informative Backup & Restore dashboard labels (`REQ-MIG-008`, `TST-MIG-005`)
 * - 4-tab `HorizontalPager` navigation (`REQ-MIG-011`, `TST-MIG-008`)
 * - Positive Import UI alignment (`REQ-MIG-020`, `TST-MIG-016`)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ImportBackupTabsUiContractTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var context: Context

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        context = app
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testTabbedNavigation_rendersFourTabsAndSwitchesBetweenImportBackupRestore_TST_MIG_008() {
        val vm = BackupRestoreViewModel(app)
        composeRule.setContent {
            ATrainingTrackerTheme {
                ImportBackupTabsScreen(viewModel = vm)
            }
        }

        // Top app bar title: "Import & Backup"
        composeRule.onNodeWithText(context.getString(R.string.import_backup)).assertIsDisplayed()

        // All 4 tabs exist in HorizontalPager header
        val importTab = context.getString(R.string.tab_import)
        val autoImportTab = context.getString(R.string.tab_auto_import)
        val backupTab = context.getString(R.string.tab_backup)
        val restoreTab = context.getString(R.string.tab_restore)

        composeRule.onNodeWithText(importTab).assertExists()
        composeRule.onNodeWithText(autoImportTab).assertExists()
        composeRule.onNodeWithText(backupTab).assertExists()
        composeRule.onNodeWithText(restoreTab).assertExists()

        // Switch to Backup tab and verify Backup card content appears
        composeRule.onNodeWithText(backupTab).performClick()
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.backup_now)).assertIsDisplayed()

        // Switch to Restore tab and verify Restore card content appears
        composeRule.onNodeWithText(restoreTab).performClick()
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.restore_from_dropbox)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testDropboxRestoreConfirmation_showsAlertDialogAndBlocksRestoreUntilConfirmed_TST_MIG_004() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getAppContext() } returns context
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.uploadToDropbox() } returns true
        every { TrainingApplication.uploadBackupToDropbox() } returns true
        every { TrainingApplication.readDropboxCredential() } returns mockk<DbxCredential>(relaxed = true)
        every { TrainingApplication.uploadToGoogleDrive() } returns false
        every { TrainingApplication.uploadBackupToGoogleDrive() } returns false
        every { TrainingApplication.uploadImportedWorkoutsToStrava() } returns false
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

        val vm = io.mockk.spyk(BackupRestoreViewModel(app))
        every { vm.restoreFromDropbox(any()) } returns Unit

        composeRule.setContent {
            ATrainingTrackerTheme {
                ImportBackupTabsScreen(viewModel = vm)
            }
        }

        // Navigate sequentially across tabs so HorizontalPager composes page 3 cleanly
        composeRule.onNodeWithText(context.getString(R.string.tab_backup)).performClick()
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.tab_restore)).performClick()
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText(context.getString(R.string.restore_from_dropbox))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(context.getString(R.string.restore_from_dropbox)).performScrollTo().performClick()

        // Confirmation dialog must be shown and restoreFromDropbox must NOT have run yet
        composeRule.onNodeWithText(context.getString(R.string.restore_warning_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.restore_warning_message)).assertIsDisplayed()
        verify(exactly = 0) { vm.restoreFromDropbox(any()) }

        // Dismiss via Cancel first -> still 0 invocations
        composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        verify(exactly = 0) { vm.restoreFromDropbox(any()) }

        // Open again and confirm -> triggers restoreFromDropbox exactly once
        composeRule.onNodeWithText(context.getString(R.string.restore_from_dropbox)).performScrollTo().performClick()
        val confirmNodes = composeRule.onAllNodesWithText(context.getString(R.string.restore_backup))
        val count = confirmNodes.fetchSemanticsNodes().size
        confirmNodes[count - 1].performClick()
        verify(exactly = 1) { vm.restoreFromDropbox(any()) }
    }

    @Test
    fun testInformativeDashboardLabels_rendersBackupAndRestoreDescriptions_TST_MIG_005() {
        composeRule.setContent {
            ATrainingTrackerTheme {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                        BackupTabContentLayout(
                            isBusy = false,
                            isDropboxConnected = true,
                            isGoogleDriveConnected = false,
                            automatedBackupsEnabled = true,
                            backupIntervalDays = 1,
                            lastBackupInfo = null,
                            onCreateBackupClick = {},
                            onUploadToCloudClick = {},
                            onUpdateAutomatedBackupsEnabled = {},
                            onUpdateBackupIntervalDays = {}
                        )
                    }
                    androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                        RestoreTabContent(
                            isBusy = false,
                            isDropboxConnected = true,
                            isGoogleDriveConnected = false,
                            automaticBackupTimestamp = null,
                            onIncrementalImportClick = {},
                            onAutomaticBackupRestoreClick = {},
                            onLocalRestoreClick = {},
                            onDropboxRestoreClick = {},
                            onGoogleDriveRestoreClick = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.backup_now)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.create_and_upload_to_dropbox)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.incremental_import_description)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.full_restore_description)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.merge_from_backup_file)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.restore_from_dropbox)).assertExists()
    }

    @Test
    fun testPositiveImportUiAlignment_omitsLegacyOrRecoveryTerminology_TST_MIG_016() {
        val vm = BackupRestoreViewModel(app)
        composeRule.setContent {
            ATrainingTrackerTheme {
                ImportBackupTabsScreen(viewModel = vm)
            }
        }

        // Import tab cards render positive "Import" titles
        composeRule.onNodeWithText(context.getString(R.string.import_fit_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.import_tcx_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.import_gpx_title)).assertExists()

        val importKeys = listOf(
            "import_backup",
            "tab_import",
            "import_fit_title",
            "import_fit_description",
            "import_tcx_title",
            "import_tcx_description",
            "import_gpx_title",
            "import_gpx_description",
            "import_strava_archive_title"
        )
        for (locale in LocalizationTestCache.LOCALES) {
            val strings = LocalizationTestCache.getStrings(locale)
            for (key in importKeys) {
                val value = strings[key].orEmpty()
                assertTrue("Key '$key' must be non-blank in $locale", value.isNotBlank())
                assertFalse(
                    "Import key '$key' in $locale must avoid negative 'Legacy' wording: '$value'",
                    value.contains("legacy", ignoreCase = true)
                )
                assertFalse(
                    "Import key '$key' in $locale must avoid 'Recovery' wording: '$value'",
                    value.contains("recovery", ignoreCase = true)
                )
            }
        }
    }
}
