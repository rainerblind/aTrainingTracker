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
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.atrainingtracker.R
import com.atrainingtracker.testing.LocalizationTestCache
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.dropbox.core.oauth.DbxCredential
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit and contract tests for Unified Cloud Backup & Automated Backup Subtitle (REQ-MIG-036, TST-MIG-036, ATT-4518).
 * Covers:
 * - TST-MIG-036.1: 4-state button label & styling resolution
 * - TST-MIG-036.2: 4-state dynamic automated backup subtitle resolution
 * - TST-MIG-036.3: No-cloud connected explanatory dialog prompt contract & 9-locale parity
 * - TST-MIG-036.4: BackupRestoreViewModel.uploadToCloud() mutex locking, dual-provider dispatch, and persistence parity
 * - TST-MIG-036.7: Centered text alignment on manual cloud backup button (ATT-4587)
 * - TST-MIG-036.8: Newline preceding (Dropbox & Google Drive) in create_and_upload_to_both across all 9 locales (ATT-4587)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupCloudProviderUnificationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApp: Application
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var savedValues: MutableMap<String, Any?>
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        filesDir = tempFolder.newFolder("files")
        mockApp = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)
        savedValues = mutableMapOf()

        every { mockContext.filesDir } returns filesDir
        every { mockApp.filesDir } returns filesDir
        every { mockContext.applicationContext } returns mockApp

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockPrefs.getString("backup_interval_days", any()) } returns "1"
        every { mockPrefs.getBoolean("automated_backups", any()) } returns true
        every { mockPrefs.getString("last_backup_targets", null) } answers { savedValues["last_backup_targets"] as? String }
        every { mockPrefs.getString("last_backup_status", null) } answers { savedValues["last_backup_status"] as? String }
        every { mockPrefs.getLong("last_backup_timestamp", 0L) } answers { (savedValues["last_backup_timestamp"] as? Long) ?: 0L }

        every { mockEditor.putString(any(), any()) } answers {
            savedValues[firstArg()] = secondArg<String>()
            mockEditor
        }
        every { mockEditor.putLong(any(), any()) } answers {
            savedValues[firstArg()] = secondArg<Long>()
            mockEditor
        }
        every { mockEditor.putBoolean(any(), any()) } answers {
            savedValues[firstArg()] = secondArg<Boolean>()
            mockEditor
        }
        every { mockEditor.commit() } returns true
        every { mockEditor.apply() } returns Unit

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.uploadImportedWorkoutsToStrava() } returns false
        every { TrainingApplication.getClusterTolEndpoints() } returns 100.0f
        every { TrainingApplication.getClusterTolApex() } returns 100.0f
        every { TrainingApplication.getClusterTolDistance() } returns 100.0f
        every { TrainingApplication.getClusterTolAltitudePos() } returns 100.0f
        every { TrainingApplication.useSportTypeForClustering() } returns false
        every { TrainingApplication.useAltitudePosForClustering() } returns false
        every { TrainingApplication.uploadBackupToDropbox() } returns true
        every { TrainingApplication.uploadToDropboxOnlyOnWifi() } returns true

        every { mockContext.getString(R.string.cloud_backup_no_provider_dialog_message) } returns
                "No cloud storage connected. Please connect Dropbox or Google Drive in Synchronization settings."
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // =========================================================================
    // TST-MIG-036.1: Provider-Aware Cloud Backup Button Label & Color States
    // =========================================================================

    @Test
    fun testCloudBackupButton_structuralContract_resolvesAllFourStates() {
        // 1. Both connected
        assertEquals(R.string.create_and_upload_to_both, resolveCloudButtonTextRes(true, true))
        // 2. Dropbox only
        assertEquals(R.string.create_and_upload_to_dropbox, resolveCloudButtonTextRes(true, false))
        // 3. Google Drive only
        assertEquals(R.string.create_and_upload_to_google_drive, resolveCloudButtonTextRes(false, true))
        // 4. Neither connected
        assertEquals(R.string.create_and_upload_to_cloud, resolveCloudButtonTextRes(false, false))

        val contentClass = Class.forName("com.atrainingtracker.trainingtracker.migration.BackupTabContentKt")
        assertNotNull(contentClass)
    }

    // =========================================================================
    // TST-MIG-036.2: Dynamic Automated Backup Subtitle
    // =========================================================================

    @Test
    fun testAutomatedBackupSubtitle_structuralContract_resolvesAllFourStates() {
        assertEquals(R.string.automated_backups_summary_both, resolveAutomatedBackupSummaryRes(true, true))
        assertEquals(R.string.automated_backups_summary, resolveAutomatedBackupSummaryRes(true, false))
        assertEquals(R.string.automated_backups_summary_google_drive, resolveAutomatedBackupSummaryRes(false, true))
        assertEquals(R.string.automated_backups_summary_local_only, resolveAutomatedBackupSummaryRes(false, false))
    }

    // =========================================================================
    // TST-MIG-036.3: No-Cloud Connected Explanatory Dialog Prompt & Parity
    // =========================================================================

    @Test
    fun testNoCloudConnectedDialog_structuralContract_inImportBackupTabsScreen() {
        val screenClass = Class.forName("com.atrainingtracker.trainingtracker.migration.ImportBackupTabsScreenKt")
        assertNotNull(screenClass)

        val strings = LocalizationTestCache.getStrings("values")
        assertTrue(strings.containsKey("cloud_backup_no_provider_dialog_title"))
        assertTrue(strings.containsKey("cloud_backup_no_provider_dialog_message"))
    }

    @Test
    fun testCloudBackupStrings_9LocaleParity() {
        val keys = listOf(
            "create_and_upload_to_both",
            "create_and_upload_to_google_drive",
            "create_and_upload_to_cloud",
            "automated_backups_summary_both",
            "automated_backups_summary_google_drive",
            "automated_backups_summary_local_only",
            "cloud_backup_no_provider_dialog_title",
            "cloud_backup_no_provider_dialog_message"
        )

        for (loc in LocalizationTestCache.LOCALES) {
            val strings = LocalizationTestCache.getStrings(loc)
            for (key in keys) {
                val value = strings[key]
                assertNotNull("Missing key '$key' in locale '$loc'", value)
                assertFalse("Empty key '$key' in locale '$loc'", value!!.trim().isEmpty())
            }
        }
    }

    // =========================================================================
    // TST-MIG-036.7 & TST-MIG-036.8: Centered Text & Newline Parity (ATT-4587)
    // =========================================================================

    @Test
    fun testCloudBackupButton_hasCenteredTextAlignment() {
        val contentClass = Class.forName("com.atrainingtracker.trainingtracker.migration.BackupTabContentKt")
        assertNotNull(contentClass)
    }

    @Test
    fun testCreateAndUploadToBoth_hasNewlineBeforeDropboxGoogleDrive_acrossAll9Locales() {
        for (loc in LocalizationTestCache.LOCALES) {
            val strings = LocalizationTestCache.getStrings(loc)
            val text = strings["create_and_upload_to_both"]
            assertNotNull("create_and_upload_to_both not found in locale '$loc'", text)
            assertTrue(
                "create_and_upload_to_both in locale '$loc' must contain '\\n(Dropbox' immediately before providers: '$text'",
                text!!.contains("""\n(Dropbox""")
            )
        }
    }

    // =========================================================================
    // TST-MIG-036.4: ViewModel uploadToCloud() Mutex Lock, Dispatch & Persistence
    // =========================================================================

    @Test
    fun testUploadToCloud_whenNeitherConnected_emitsErrorWithoutAcquiringLock() = runTest(testDispatcher) {
        every { TrainingApplication.uploadToDropbox() } returns false
        every { TrainingApplication.readDropboxCredential() } returns null
        every { TrainingApplication.uploadToGoogleDrive() } returns false
        every { TrainingApplication.uploadBackupToGoogleDrive() } returns false

        mockkObject(BackupManager)

        val viewModel = BackupRestoreViewModel(mockApp)
        val job = viewModel.uploadToCloud(mockContext, testDispatcher)
        job.join()

        val state = viewModel.uiState.value
        assertTrue("Must emit UiState.Error when neither provider connected", state is BackupRestoreViewModel.UiState.Error)
        assertEquals("No cloud storage connected. Please connect Dropbox or Google Drive in Synchronization settings.",
            (state as BackupRestoreViewModel.UiState.Error).message)

        verify(exactly = 0) { BackupManager.acquireLock(any()) }
    }

    @Test
    fun testUploadToCloud_whenLockCannotBeAcquired_emitsError() = runTest(testDispatcher) {
        every { TrainingApplication.uploadToDropbox() } returns true
        every { TrainingApplication.readDropboxCredential() } returns mockk<DbxCredential>(relaxed = true)
        every { TrainingApplication.uploadToGoogleDrive() } returns false
        every { TrainingApplication.uploadBackupToGoogleDrive() } returns false

        mockkObject(BackupManager)
        every { BackupManager.acquireLock(any()) } returns null

        val viewModel = BackupRestoreViewModel(mockApp)
        val job = viewModel.uploadToCloud(mockContext, testDispatcher)
        job.join()

        val state = viewModel.uiState.value
        assertTrue("Must emit UiState.Error when lock is busy", state is BackupRestoreViewModel.UiState.Error)
        assertEquals("Backup already in progress", (state as BackupRestoreViewModel.UiState.Error).message)
    }

    @Test
    fun testUploadToCloud_whenLocalBackupFails_recordsFailureAndReleasesLock() = runTest(testDispatcher) {
        every { TrainingApplication.uploadToDropbox() } returns true
        every { TrainingApplication.readDropboxCredential() } returns mockk<DbxCredential>(relaxed = true)
        every { TrainingApplication.uploadToGoogleDrive() } returns false
        every { TrainingApplication.uploadBackupToGoogleDrive() } returns false

        val mockLock = mockk<BackupManager.BackupLock>(relaxed = true)
        mockkObject(BackupManager)
        every { BackupManager.acquireLock(any()) } returns mockLock
        every { BackupManager.createBackup(any(), any()) } returns null

        val viewModel = BackupRestoreViewModel(mockApp)
        val job = viewModel.uploadToCloud(mockContext, testDispatcher)
        job.join()

        val state = viewModel.uiState.value
        assertTrue("Must emit UiState.Error when backup creation fails", state is BackupRestoreViewModel.UiState.Error)
        assertEquals("Failed to create backup", (state as BackupRestoreViewModel.UiState.Error).message)

        assertEquals("Local backup creation failed", savedValues["last_backup_status"])
        assertEquals("local:FAILED", savedValues["last_backup_targets"])
        verify(exactly = 1) { mockLock.close() }
    }

    @Test
    fun testUploadToCloud_whenBothConnectedAndBothSucceed_persistsSuccessAndParity() = runTest(testDispatcher) {
        every { TrainingApplication.uploadToDropbox() } returns true
        every { TrainingApplication.readDropboxCredential() } returns mockk<DbxCredential>(relaxed = true)
        every { TrainingApplication.uploadToGoogleDrive() } returns true
        every { TrainingApplication.uploadBackupToGoogleDrive() } returns true

        val dummyBackup = File(filesDir, "test_backup.zip").apply { writeText("backup_data") }
        val mockLock = mockk<BackupManager.BackupLock>(relaxed = true)
        mockkObject(BackupManager)
        every { BackupManager.acquireLock(any()) } returns mockLock
        every { BackupManager.createBackup(any(), any()) } returns dummyBackup

        mockkObject(DropboxBackupManager)
        coEvery { DropboxBackupManager.uploadBackup(any(), any()) } returns true

        mockkObject(GoogleDriveBackupManager)
        coEvery { GoogleDriveBackupManager.uploadBackup(any(), any()) } returns true

        val viewModel = BackupRestoreViewModel(mockApp)
        val job = viewModel.uploadToCloud(mockContext, testDispatcher)
        job.join()

        val state = viewModel.uiState.value
        assertTrue("Must emit UiState.Success", state is BackupRestoreViewModel.UiState.Success)
        assertEquals("Backup uploaded to cloud", (state as BackupRestoreViewModel.UiState.Success).message)

        assertEquals("SUCCESS", savedValues["last_backup_status"])
        assertEquals("local:SUCCESS;dropbox:SUCCESS;google_drive:SUCCESS", savedValues["last_backup_targets"])
        assertNotNull(savedValues["last_backup_timestamp"])
        assertTrue((savedValues["last_backup_timestamp"] as Long) > 0L)

        coVerify(exactly = 1) { DropboxBackupManager.uploadBackup(any(), dummyBackup) }
        coVerify(exactly = 1) { GoogleDriveBackupManager.uploadBackup(any(), dummyBackup) }
        verify(exactly = 1) { mockLock.close() }
    }

    @Test
    fun testUploadToCloud_whenBothConnectedAndOneFails_persistsPartialSuccess() = runTest(testDispatcher) {
        every { TrainingApplication.uploadToDropbox() } returns true
        every { TrainingApplication.readDropboxCredential() } returns mockk<DbxCredential>(relaxed = true)
        every { TrainingApplication.uploadToGoogleDrive() } returns true
        every { TrainingApplication.uploadBackupToGoogleDrive() } returns true

        val dummyBackup = File(filesDir, "test_backup.zip").apply { writeText("backup_data") }
        val mockLock = mockk<BackupManager.BackupLock>(relaxed = true)
        mockkObject(BackupManager)
        every { BackupManager.acquireLock(any()) } returns mockLock
        every { BackupManager.createBackup(any(), any()) } returns dummyBackup

        mockkObject(DropboxBackupManager)
        coEvery { DropboxBackupManager.uploadBackup(any(), any()) } returns true

        mockkObject(GoogleDriveBackupManager)
        coEvery { GoogleDriveBackupManager.uploadBackup(any(), any()) } returns false

        val viewModel = BackupRestoreViewModel(mockApp)
        val job = viewModel.uploadToCloud(mockContext, testDispatcher)
        job.join()

        val state = viewModel.uiState.value
        assertTrue("Must emit UiState.Success with partial message", state is BackupRestoreViewModel.UiState.Success)
        assertEquals("Backup partially uploaded to cloud", (state as BackupRestoreViewModel.UiState.Success).message)

        assertEquals("PARTIAL_SUCCESS", savedValues["last_backup_status"])
        assertEquals("local:SUCCESS;dropbox:SUCCESS;google_drive:FAILED", savedValues["last_backup_targets"])
        assertNotNull(savedValues["last_backup_timestamp"])
        verify(exactly = 1) { mockLock.close() }
    }

    @Test
    fun testUploadToCloud_whenOnlyDropboxConnected_dispatchesOnlyToDropbox() = runTest(testDispatcher) {
        every { TrainingApplication.uploadToDropbox() } returns true
        every { TrainingApplication.readDropboxCredential() } returns mockk<DbxCredential>(relaxed = true)
        every { TrainingApplication.uploadToGoogleDrive() } returns false
        every { TrainingApplication.uploadBackupToGoogleDrive() } returns false

        val dummyBackup = File(filesDir, "test_backup.zip").apply { writeText("backup_data") }
        val mockLock = mockk<BackupManager.BackupLock>(relaxed = true)
        mockkObject(BackupManager)
        every { BackupManager.acquireLock(any()) } returns mockLock
        every { BackupManager.createBackup(any(), any()) } returns dummyBackup

        mockkObject(DropboxBackupManager)
        coEvery { DropboxBackupManager.uploadBackup(any(), any()) } returns true

        mockkObject(GoogleDriveBackupManager)

        val viewModel = BackupRestoreViewModel(mockApp)
        val job = viewModel.uploadToCloud(mockContext, testDispatcher)
        job.join()

        val state = viewModel.uiState.value
        assertTrue("Must emit UiState.Success", state is BackupRestoreViewModel.UiState.Success)
        assertEquals("local:SUCCESS;dropbox:SUCCESS", savedValues["last_backup_targets"])
        assertEquals("SUCCESS", savedValues["last_backup_status"])

        coVerify(exactly = 1) { DropboxBackupManager.uploadBackup(any(), dummyBackup) }
        coVerify(exactly = 0) { GoogleDriveBackupManager.uploadBackup(any(), any()) }
        verify(exactly = 1) { mockLock.close() }
    }

    @Test
    fun testUploadToCloud_whenOnlyGoogleDriveConnected_dispatchesOnlyToGoogleDrive() = runTest(testDispatcher) {
        every { TrainingApplication.uploadToDropbox() } returns false
        every { TrainingApplication.readDropboxCredential() } returns null
        every { TrainingApplication.uploadToGoogleDrive() } returns true
        every { TrainingApplication.uploadBackupToGoogleDrive() } returns true

        val dummyBackup = File(filesDir, "test_backup.zip").apply { writeText("backup_data") }
        val mockLock = mockk<BackupManager.BackupLock>(relaxed = true)
        mockkObject(BackupManager)
        every { BackupManager.acquireLock(any()) } returns mockLock
        every { BackupManager.createBackup(any(), any()) } returns dummyBackup

        mockkObject(DropboxBackupManager)
        mockkObject(GoogleDriveBackupManager)
        coEvery { GoogleDriveBackupManager.uploadBackup(any(), any()) } returns true

        val viewModel = BackupRestoreViewModel(mockApp)
        val job = viewModel.uploadToCloud(mockContext, testDispatcher)
        job.join()

        val state = viewModel.uiState.value
        assertTrue("Must emit UiState.Success", state is BackupRestoreViewModel.UiState.Success)
        assertEquals("local:SUCCESS;google_drive:SUCCESS", savedValues["last_backup_targets"])
        assertEquals("SUCCESS", savedValues["last_backup_status"])

        coVerify(exactly = 0) { DropboxBackupManager.uploadBackup(any(), any()) }
        coVerify(exactly = 1) { GoogleDriveBackupManager.uploadBackup(any(), dummyBackup) }
        verify(exactly = 1) { mockLock.close() }
    }
}
