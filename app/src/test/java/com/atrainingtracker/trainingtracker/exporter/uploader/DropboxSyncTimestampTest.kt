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

package com.atrainingtracker.trainingtracker.exporter.uploader

import android.content.Context
import android.util.Log
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.migration.DropboxBackupManager
import com.dropbox.core.NetworkIOException
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.DbxUserFilesRequests
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.UploadBuilder
import com.dropbox.core.v2.files.WriteMode
import com.dropbox.core.v2.users.DbxUserUsersRequests
import com.dropbox.core.v2.users.FullAccount
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Unit tests verifying Dropbox sync timestamp updating and defensive account profile lookups (TST-DAT-031.4).
 */
class DropboxSyncTimestampTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.filesDir } returns tempFolder.root

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.setDropboxLastSyncTimestamp(any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testDropboxUploader_updatesSyncTimestampOnSuccess() {
        val uploader = DropboxUploader(mockContext)
        val exportInfo = ExportInfo("sync_test_workout", FileFormat.CSV, ExportType.DROPBOX)

        val localFile = File(tempFolder.root, exportInfo.shortPath).apply {
            parentFile?.mkdirs()
            writeText("csv,content")
        }

        val mockCredential = mockk<DbxCredential>(relaxed = true)
        every { TrainingApplication.readDropboxCredential() } returns mockCredential

        mockkConstructor(DbxClientV2::class)
        val mockFiles = mockk<DbxUserFilesRequests>(relaxed = true)
        val mockUploadBuilder = mockk<UploadBuilder>(relaxed = true)
        val mockMetadata = mockk<FileMetadata>(relaxed = true)

        every { anyConstructed<DbxClientV2>().files() } returns mockFiles
        every { mockFiles.uploadBuilder(any()) } returns mockUploadBuilder
        every { mockUploadBuilder.withMode(any()) } returns mockUploadBuilder
        every { mockUploadBuilder.uploadAndFinish(any<InputStream>()) } returns mockMetadata

        val result = uploader.doExport(exportInfo)
        assertTrue(result.success())

        verify(atLeast = 1) { TrainingApplication.setDropboxLastSyncTimestamp(any()) }
    }

    @Test
    fun testDropboxBackupManager_updatesSyncTimestampOnSuccess() = runBlocking {
        val backupFile = File(tempFolder.root, "backup.attbackup").apply { writeText("backup") }
        val mockCredential = mockk<DbxCredential>(relaxed = true)
        every { TrainingApplication.readDropboxCredential() } returns mockCredential

        mockkConstructor(DbxClientV2::class)
        val mockFiles = mockk<DbxUserFilesRequests>(relaxed = true)
        val mockUploadBuilder = mockk<UploadBuilder>(relaxed = true)
        val mockMetadata = mockk<FileMetadata>(relaxed = true)

        every { anyConstructed<DbxClientV2>().files() } returns mockFiles
        every { mockFiles.uploadBuilder(any()) } returns mockUploadBuilder
        every { mockUploadBuilder.withMode(any()) } returns mockUploadBuilder
        every { mockUploadBuilder.uploadAndFinish(any<InputStream>()) } returns mockMetadata

        val success = DropboxBackupManager.uploadBackup(mockContext, backupFile)
        assertTrue(success)

        verify(atLeast = 1) { TrainingApplication.setDropboxLastSyncTimestamp(any()) }
    }

    @Test
    fun testDropboxBackupManager_fetchAccountEmail_success() = runBlocking {
        val mockCredential = mockk<DbxCredential>(relaxed = true)
        every { TrainingApplication.readDropboxCredential() } returns mockCredential

        mockkConstructor(DbxClientV2::class)
        val mockUsers = mockk<DbxUserUsersRequests>(relaxed = true)
        val mockAccount = mockk<FullAccount>(relaxed = true)
        every { mockAccount.email } returns "user@dropbox.com"

        every { anyConstructed<DbxClientV2>().users() } returns mockUsers
        every { mockUsers.currentAccount } returns mockAccount

        val email = DropboxBackupManager.fetchAccountEmail()
        assertEquals("user@dropbox.com", email)
    }

    @Test
    fun testDropboxBackupManager_fetchAccountEmail_dbxException_returnsNullDefensively() = runBlocking {
        val mockCredential = mockk<DbxCredential>(relaxed = true)
        every { TrainingApplication.readDropboxCredential() } returns mockCredential

        mockkConstructor(DbxClientV2::class)
        val mockUsers = mockk<DbxUserUsersRequests>(relaxed = true)
        every { anyConstructed<DbxClientV2>().users() } returns mockUsers
        every { mockUsers.currentAccount } throws NetworkIOException(IOException("Network unreachable"))

        val email = DropboxBackupManager.fetchAccountEmail()
        assertNull(email)
    }
}
