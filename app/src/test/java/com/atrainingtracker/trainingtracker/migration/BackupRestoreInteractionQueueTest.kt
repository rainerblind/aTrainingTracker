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
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.dropbox.core.oauth.DbxCredential
import com.google.android.gms.maps.model.LatLng
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests verifying [BackupRestoreViewModel] interaction queue backpressure (`Semaphore(3)`)
 * and non-blocking concurrent background progress while user resolution dialogs remain open
 * (REQ-MIG-017, TST-MIG-013; REQ-MIG-014, REQ-MIG-017, TST-MIG-017, TST-PRO-004).
 */
// Linked test specs: TST-PRO-004
@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreInteractionQueueTest {

    @get:org.junit.Rule
    val tempFolder = org.junit.rules.TemporaryFolder()

    private lateinit var mockApp: Application
    private lateinit var mockContext: Context
    private val testDispatcher = StandardTestDispatcher()

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

        val filesDir = tempFolder.newFolder("files")
        mockApp = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        every { mockApp.filesDir } returns filesDir
        every { mockContext.filesDir } returns filesDir
        every { mockApp.applicationContext } returns mockApp
        every { mockContext.applicationContext } returns mockApp

        com.atrainingtracker.trainingtracker.settings.SettingsDataStore.setInstanceForTesting(
            com.atrainingtracker.trainingtracker.settings.SettingsDataStore.createForTesting(
                mockApp,
                com.atrainingtracker.trainingtracker.settings.InMemoryPreferencesDataStore()
            )
        )
        com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore.setInstanceForTesting(
            com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore.createForTesting(
                mockApp,
                dataStoreOverride = com.atrainingtracker.trainingtracker.settings.InMemoryPreferencesDataStore()
            )
        )
        val mockPrefs = mockk<SharedPreferences>(relaxed = true)
        val mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { mockPrefs.edit() } returns mockEditor
        every { mockPrefs.getString("backup_interval_days", any()) } returns "1"

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.uploadToDropbox() } returns true
        every { TrainingApplication.uploadImportedWorkoutsToStrava() } returns false
        every { TrainingApplication.readDropboxCredential() } returns mockk<DbxCredential>(relaxed = true)
        every { TrainingApplication.getClusterTolEndpoints() } returns 100f
        every { TrainingApplication.getClusterTolApex() } returns 100f
        every { TrainingApplication.getClusterTolDistance() } returns 100f
        every { TrainingApplication.getClusterTolAltitudePos() } returns 100f
        every { TrainingApplication.useSportTypeForClustering() } returns false
        every { TrainingApplication.useAltitudePosForClustering() } returns false

        every { mockApp.getString(R.string.legacy_import__finished_all_new, any(), any()) } returns "Done"
    }

    @After
    fun tearDown() {
        com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore.setInstanceForTesting(null)
        com.atrainingtracker.trainingtracker.settings.SettingsDataStore.setInstanceForTesting(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testInteractionSemaphore_limitsQueueToThreeAndResumesConcurrently_TST_MIG_013_TST_MIG_017() = runTest(testDispatcher) {
        val startedCandidates = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val backgroundCompletedBeforeFirstResolve = CompletableDeferred<Unit>()

        mockkObject(LegacyImportEngine)
        coEvery {
            LegacyImportEngine.bulkRecoverFromDropbox(any(), any(), any(), any())
        } coAnswers {
            val listener = arg<LegacyImportEngine.ProgressListener>(2)
            coroutineScope {
                val jobs = (1..5).map { idx ->
                    async {
                        // Simulate background download/parse progress reporting while item 1 is still pending
                        listener.onProgress(idx, 5, "workout_$idx.tcx")
                        if (idx == 4) {
                            backgroundCompletedBeforeFirstResolve.complete(Unit)
                        }
                        val decision = listener.onNewClusterCandidate(
                            date = "2026-08-0$idx 10:00:00",
                            start = LatLng(48.0, 11.0),
                            end = LatLng(48.1, 11.1),
                            apex = LatLng(48.05, 11.05),
                            distance = 5000.0,
                            bSportType = BSportType.BIKE,
                            polyline = "abc",
                            workoutName = "Ride $idx",
                            candidateSportTypes = setOf(BSportType.BIKE),
                            minAltPos = null,
                            maxAltPos = null
                        )
                        startedCandidates.incrementAndGet()
                        decision
                    }
                }
                jobs.awaitAll()
            }
            LegacyImportEngine.RecoveryResult(5, 0, 0, 5)
        }

        val vm = BackupRestoreViewModel(mockApp)
        val countCollector = backgroundScope.launch { vm.pendingInteractionsCount.collect {} }
        val activeCollector = backgroundScope.launch { vm.activeInteraction.collect {} }
        val recoveryJob = vm.bulkRecoverLegacyData(mockContext, "tcx", testDispatcher)

        // Advance until background workers saturate the 3-permit interaction semaphore
        advanceUntilIdle()
        backgroundCompletedBeforeFirstResolve.await()

        val initialQueued = vm.pendingInteractionsCount.value
        maxConcurrent.set(maxOf(maxConcurrent.get(), initialQueued))
        assertEquals("Semaphore(3) must cap pending interaction queue at 3", 3, initialQueued)
        assertTrue("Background progress must continue while first dialog is unresolved", vm.activeInteraction.value != null)

        // Resolve queued interactions one by one and confirm remaining 2 admit smoothly
        var resolved = 0
        while (vm.activeInteraction.value != null) {
            assertTrue("Queue size must never exceed 3", vm.pendingInteractionsCount.value <= 3)
            vm.provideClusterDecision(LegacyImportEngine.ClusterDecision(null, null, "Resolved"))
            resolved++
            advanceUntilIdle()
        }

        recoveryJob.join()
        countCollector.cancel()
        activeCollector.cancel()
        assertEquals(5, resolved)
        assertEquals(5, startedCandidates.get())
        assertEquals(0, vm.pendingInteractionsCount.value)
    }
}
