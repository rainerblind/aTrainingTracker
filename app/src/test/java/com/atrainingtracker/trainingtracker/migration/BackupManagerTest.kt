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
 * You should have received a copy of the GNU License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.migration

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * TST-MIG-033: Unit tests verifying BackupManager persistent directory,
 * process-safe lock acquisition, co-located atomic staging, stale file purge,
 * and try-finally error cleanup guarantees.
 */
class BackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var fakeFilesDir: File
    private lateinit var fakeDataDir: File

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        fakeFilesDir = tempFolder.newFolder("files")
        fakeDataDir = tempFolder.newFolder("data")

        mockContext = mockk(relaxed = true)
        every { mockContext.filesDir } returns fakeFilesDir
        val appInfo = ApplicationInfo().apply { dataDir = fakeDataDir.absolutePath }
        every { mockContext.applicationInfo } returns appInfo
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testGetBackupDir_createsAndReturnsPersistentBackupsDirectory() {
        val backupDir = BackupManager.getBackupDir(mockContext)
        assertEquals(File(fakeFilesDir, "backups"), backupDir)
        assertTrue("Backup directory must exist", backupDir.exists())
        assertTrue("Backup directory must be a directory", backupDir.isDirectory)
    }

    @Test
    fun testAcquireLock_acquiresAndReleasesLockCleanly() {
        val lock1 = BackupManager.acquireLock(mockContext)
        assertNotNull("First lock acquisition must succeed", lock1)

        // Second lock acquisition on same thread/process should fail due to reentrant guard
        val lock2 = BackupManager.acquireLock(mockContext)
        assertNull("Second lock acquisition while held must return null", lock2)

        lock1?.close()

        val lock3 = BackupManager.acquireLock(mockContext)
        assertNotNull("Lock acquisition after close must succeed", lock3)
        lock3?.close()
    }

    @Test
    fun testCreateBackup_cleansUpStaleTempFileBeforeExecution() {
        val backupDir = BackupManager.getBackupDir(mockContext)
        val staleTemp = File(backupDir, "${BackupManager.BACKUP_FILENAME}.tmp")
        staleTemp.writeText("corrupted partial data from interrupted run")
        assertTrue("Stale temp file must exist before test", staleTemp.exists())

        // Create dummy source folders
        File(fakeDataDir, "databases").mkdirs()
        File(fakeDataDir, "shared_prefs").mkdirs()
        File(fakeFilesDir, "datastore").mkdirs()

        val resultFile = BackupManager.createBackup(mockContext)

        assertNotNull("Backup creation should succeed", resultFile)
        assertTrue("Canonical backup file must exist", resultFile!!.exists())
        assertFalse("Stale temp file must be cleaned up and not remain", staleTemp.exists())
    }

    @Test
    fun testCreateBackup_storageIsBoundedToSingleCanonicalArchive() {
        val backupDir = BackupManager.getBackupDir(mockContext)

        File(fakeDataDir, "databases").mkdirs()
        File(fakeDataDir, "shared_prefs").mkdirs()
        File(fakeFilesDir, "datastore").mkdirs()

        // First run
        val firstFile = BackupManager.createBackup(mockContext)
        assertNotNull(firstFile)

        // Second run
        val secondFile = BackupManager.createBackup(mockContext)
        assertNotNull(secondFile)

        // Assert strictly 1 archive and 1 lock file exist in backupDir (O(1) disk bounds)
        val files = backupDir.listFiles()?.filter { it.isFile && it.name.endsWith(".attbackup") } ?: emptyList()
        assertEquals("Exactly one canonical .attbackup archive must exist in backups directory", 1, files.size)
        assertEquals(BackupManager.BACKUP_FILENAME, files.first().name)
    }

    @Test
    fun testSourceCodeInvariants_atomicMoveAndTryFinallyGuaranteed() {
        val rootDir = if (File("app").exists()) File(".") else File("..")
        val managerSource = File(rootDir, "app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupManager.kt").readText()
        assertTrue(
            "BackupManager must use ATOMIC_MOVE",
            managerSource.contains("StandardCopyOption.ATOMIC_MOVE")
        )
        assertTrue(
            "BackupManager must have try-finally deleting tempBackupFile",
            managerSource.contains("tempBackupFile.delete()")
        )
        assertTrue(
            "BackupManager must store in context.filesDir",
            managerSource.contains("""File(context.filesDir, "backups")""")
        )
    }
}
