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
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Unit tests verifying default automated Dropbox backup configuration and WorkManager scheduling
 * on clean installations: daily interval (`"1"`) and automated backups enabled by default (`true`)
 * (REQ-MIG-003, TST-MIG-003; REQ-MIG-009, TST-MIG-006; REQ-MIG-010, TST-MIG-007).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DropboxBackupDefaultsTest {

    private lateinit var context: Context
    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        context = app
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        unmockkAll()
    }

    @Test
    fun testCleanInstallDefaults_automatedBackupsEnabledAndIntervalOneDay_TST_MIG_006_TST_MIG_007() {
        androidx.preference.PreferenceManager.setDefaultValues(context, R.xml.prefs_dropbox, true)
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)

        assertTrue("automated_backups must default to true", prefs.getBoolean("automated_backups", false))
        assertEquals("backup_interval_days must default to '1'", "1", prefs.getString("backup_interval_days", null))

        val vm = BackupRestoreViewModel(app)
        assertTrue("ViewModel automatedBackupsEnabled must default to true", vm.automatedBackupsEnabled)
        assertEquals("ViewModel backupIntervalDays must default to 1", 1, vm.backupIntervalDays)
    }

    @Test
    fun testBackupWorkerSchedule_enqueuesDailyPeriodicWorkByDefault_TST_MIG_003_TST_MIG_006_TST_MIG_007() {
        androidx.preference.PreferenceManager.setDefaultValues(context, R.xml.prefs_dropbox, true)

        val mockWorkManager = mockk<WorkManager>(relaxed = true)
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns mockWorkManager

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isWorkManagerAvailable() } returns true
        every { TrainingApplication.uploadToDropbox() } returns true
        every { TrainingApplication.uploadBackupToDropbox() } returns true
        every { TrainingApplication.uploadToDropboxOnlyOnWifi() } returns true
        every { TrainingApplication.uploadToGoogleDrive() } returns false
        every { TrainingApplication.readDropboxCredential() } returns null

        BackupWorker.schedule(context)

        val requestSlot = slot<PeriodicWorkRequest>()
        verify(exactly = 1) {
            mockWorkManager.enqueueUniquePeriodicWork(
                "automated_backup_work",
                ExistingPeriodicWorkPolicy.UPDATE,
                capture(requestSlot)
            )
        }
        assertEquals(
            TimeUnit.DAYS.toMillis(1),
            requestSlot.captured.workSpec.intervalDuration
        )
    }
}
