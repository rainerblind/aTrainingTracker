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

package com.atrainingtracker.banalservice.devices.radar

import android.content.Context
import android.media.AudioManager
import android.os.Vibrator
import com.atrainingtracker.trainingtracker.audio.radar.RadarAlertManager
import com.atrainingtracker.trainingtracker.audio.radar.RadarAudioSynthesizer
import com.atrainingtracker.trainingtracker.settings.InMemoryPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.RadarLightControlMode
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.tracker.TrackerService
import com.atrainingtracker.trainingtracker.ui.tracking.radar.RadarOverlayMath
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Concurrency, zero-stale-cache, zero-1-Hz-wakeup, and AB-BA deadlock stress verification suite
 * (REQ-CON-056.2, REQ-CON-056.3, REQ-CON-056.4 / TST-CON-056.2, TST-CON-056.3, TST-CON-056.4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RadarBrakeLightConcurrencyStressTest {

    @Before
    fun setUp() {
        io.mockk.mockkStatic(android.util.Log::class)
        io.mockk.every { android.util.Log.d(any(), any()) } returns 0
        io.mockk.every { android.util.Log.i(any(), any()) } returns 0
        io.mockk.every { android.util.Log.w(any(), any<String>()) } returns 0
        io.mockk.every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        io.mockk.every { android.util.Log.e(any(), any()) } returns 0
        io.mockk.every { android.util.Log.e(any(), any(), any()) } returns 0
        TuningPreferencesDataStore.setInstanceForTesting(null)
        com.atrainingtracker.trainingtracker.settings.SettingsDataStore.setInstanceForTesting(null)
        RadarStateRepository.reset()
        RadarSnoozeController.reset()
        RadarLightController.reset()
        SmartBrakeLightDetector.reset()
        SmartRadarLightEngine.reset()
    }

    @After
    fun tearDown() {
        TuningPreferencesDataStore.setInstanceForTesting(null)
        com.atrainingtracker.trainingtracker.settings.SettingsDataStore.setInstanceForTesting(null)
        RadarStateRepository.reset()
        RadarSnoozeController.reset()
        RadarLightController.reset()
        SmartBrakeLightDetector.reset()
        SmartRadarLightEngine.reset()
        io.mockk.unmockkStatic(android.util.Log::class)
    }

    @Test
    fun deadCachesAndInvalidationHooks_areRemovedFromRadarLightControllerAndTrackerService_tstCon056_2() {
        val controllerFields = RadarLightController::class.java.declaredFields.map { it.name }.toSet()
        val controllerMethods = RadarLightController::class.java.declaredMethods.map { it.name }.toSet()
        assertFalse("cachedAutoMode must be deleted", "cachedAutoMode" in controllerFields)
        assertFalse("cachedAutoLightControl must be deleted", "cachedAutoLightControl" in controllerFields)
        assertFalse("cachedLightDefaultMode must be deleted", "cachedLightDefaultMode" in controllerFields)
        assertFalse("cachedControlMode must be deleted", "cachedControlMode" in controllerFields)
        assertFalse("hasLoadedInitialPreferences must be deleted", "hasLoadedInitialPreferences" in controllerFields)
        assertFalse("updateCachedPreferences must be deleted", "updateCachedPreferences" in controllerMethods)

        val trackerFields = TrackerService::class.java.declaredFields.map { it.name }.toSet()
        val trackerMethods = TrackerService::class.java.declaredMethods.map { it.name }.toSet()
        assertFalse("mCachedAutoPauseConfig must be deleted", "mCachedAutoPauseConfig" in trackerFields)
        assertFalse("invalidateAutoPauseCache must be deleted", "invalidateAutoPauseCache" in trackerMethods)
        assertFalse("invalidateAutoPauseConfigCache must be deleted", "invalidateAutoPauseConfigCache" in trackerMethods)
    }

    @Test
    fun tuningPreferencesUpdates_propagateImmediatelyToRadarLightControllerAndAutoPauseConfig_tstCon056_2() = runTest {
        val mockContext = mockk<Context>(relaxed = true)
        val tempFilesDir = java.nio.file.Files.createTempDirectory("radar-stress-").toFile()
        io.mockk.every { mockContext.applicationContext } returns mockContext
        io.mockk.every { mockContext.filesDir } returns tempFilesDir
        com.atrainingtracker.trainingtracker.settings.SettingsDataStore.setInstanceForTesting(mockk(relaxed = true))
        val inMemory = InMemoryPreferencesDataStore()
        val store = TuningPreferencesDataStore.createForTesting(
            context = mockContext,
            testScope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            dataStoreOverride = inMemory
        )
        TuningPreferencesDataStore.setInstanceForTesting(store)

        // Live radar light changes take immediate effect without any manual cache update call
        store.updateRadarLightSelection(isAuto = false, mode = RadarLightMode.PELOTON)
        assertFalse(RadarLightController.isAutoMode.value)
        assertEquals(RadarLightMode.PELOTON, RadarLightController.preferredMode.value)

        store.updateRadarLightControlMode(RadarLightControlMode.SMART_AUTOMATION)
        assertTrue(RadarLightController.isAutoMode.value)

        // Live Auto-Pause toggles & resetToDefaults take immediate effect without invalidateAutoPauseCache()
        store.updateAutoPauseBikeEnabled(false)
        store.updateAutoPauseRunEnabled(true)
        store.updateAutoPauseOtherEnabled(true)
        val updatedAutoPause = SettingsDataStoreJavaHelper.getAutoPauseConfig(mockContext)
        assertFalse(updatedAutoPause.bikeEnabled)
        assertTrue(updatedAutoPause.runEnabled)
        assertTrue(updatedAutoPause.otherEnabled)

        store.resetToDefaults()
        val resetAutoPause = SettingsDataStoreJavaHelper.getAutoPauseConfig(mockContext)
        assertTrue(resetAutoPause.bikeEnabled)
        assertFalse(resetAutoPause.runEnabled)
        assertFalse(resetAutoPause.otherEnabled)
    }

    @Test
    fun timedSnooze_producesZeroWakeupsInSmartRadarLightEngineAndRadarAlertManager_tstCon056_3() = runTest {
        var nowMs = System.currentTimeMillis()
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        RadarSnoozeController.setTimeProvider { nowMs }
        RadarSnoozeController.setCoroutineScope(testScope)

        val mockContext = mockk<Context>(relaxed = true)
        val tempFilesDir = java.nio.file.Files.createTempDirectory("radar-stress-").toFile()
        io.mockk.every { mockContext.applicationContext } returns mockContext
        io.mockk.every { mockContext.filesDir } returns tempFilesDir
        com.atrainingtracker.trainingtracker.settings.SettingsDataStore.setInstanceForTesting(mockk(relaxed = true))
        val mockSynth = mockk<RadarAudioSynthesizer>(relaxed = true)
        val mockAudio = mockk<AudioManager>(relaxed = true)
        val mockVibrator = mockk<Vibrator>(relaxed = true)
        val alertManager = RadarAlertManager(
            context = mockContext,
            tuningDataStore = null,
            audioSynthesizer = mockSynth,
            audioManager = mockAudio,
            vibrator = mockVibrator,
            handler = null,
            coroutineScope = testScope,
            timeProvider = { nowMs }
        )

        SmartRadarLightEngine.coroutineDispatcher = UnconfinedTestDispatcher(testScheduler)
        SmartRadarLightEngine.isAutomationEnabled = true
        val mockTuningStore = mockk<TuningPreferencesDataStore>(relaxed = true)
        io.mockk.every { mockTuningStore.tuningConfigFlow } returns kotlinx.coroutines.flow.MutableStateFlow(
            com.atrainingtracker.trainingtracker.settings.TuningConfig()
        )
        TuningPreferencesDataStore.setInstanceForTesting(mockTuningStore)
        SmartRadarLightEngine.start(mockContext)
        alertManager.start()

        // Publish one approaching vehicle at t=50s, then clear road at t=51s
        RadarStateRepository.updateThreatState(
            RadarThreatState(
                connectionState = RadarConnectionState.CONNECTED,
                overallThreatLevel = ThreatLevel.APPROACHING,
                targets = listOf(RadarTarget(1, 30f, 12f, ThreatLevel.APPROACHING)),
                lastUpdateRealtimeMs = nowMs,
                source = RadarSource.BLE
            )
        )
        assertEquals(1, SmartRadarLightEngine.currentThreatStage)

        // Activate 15m timed snooze and advance 120 seconds:
        // Neither SmartRadarLightEngine nor RadarAlertManager may be woken by snooze ticks!
        RadarSnoozeController.snooze(RadarAudioSnoozeMode.SNOOZE_15M)
        val snooze = RadarStateRepository.snoozeState.value
        assertEquals("15m", RadarOverlayMath.formatSnoozeBadgeText(snooze, nowMs))

        nowMs += 120_000L
        testScope.advanceTimeBy(120_000L)
        assertEquals("13m", RadarOverlayMath.formatSnoozeBadgeText(snooze, nowMs))

        // Only the single initial approach chime should have played (zero spurious wakeups during snooze)
        verify(exactly = 1) { mockSynth.playApproach(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { mockSynth.playAllClear(any(), any()) }

        // Clear road AFTER snooze progression: cooldown from t=50s (3.5s) was NOT extended by snooze ticks!
        RadarStateRepository.onThreatStateChangedCheck(nowMs)
        assertEquals(0, SmartRadarLightEngine.currentThreatStage)

        alertManager.release()
        SmartRadarLightEngine.stop()
    }

    private fun RadarStateRepository.onThreatStateChangedCheck(nowMs: Long) {
        SmartRadarLightEngine.onThreatStateChanged(
            RadarThreatState(
                connectionState = RadarConnectionState.CONNECTED,
                overallThreatLevel = ThreatLevel.NONE,
                targets = emptyList(),
                lastUpdateRealtimeMs = nowMs,
                source = RadarSource.BLE
            ),
            nowMs = nowMs
        )
    }

    @Test
    fun concurrentThreatAndSnoozeMutations_neverOverwriteTargetsOrFailoverControlState_tstCon056_3() {
        val executor = Executors.newFixedThreadPool(3)
        val stop = AtomicBoolean(false)
        val failure = AtomicReference<Throwable?>(null)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(3)

        val target = RadarTarget(1, 25f, 15f, ThreatLevel.FAST_APPROACHING)

        // Thread 1: High-rate 8-Hz threat frames on BLE
        executor.execute {
            try {
                startLatch.await()
                for (i in 1..2000) {
                    RadarStateRepository.updateThreatState(
                        RadarThreatState(
                            connectionState = RadarConnectionState.CONNECTED,
                            overallThreatLevel = ThreatLevel.FAST_APPROACHING,
                            targets = listOf(target.copy(rangeMeters = 100f - (i % 80))),
                            lastUpdateRealtimeMs = i.toLong(),
                            source = RadarSource.BLE
                        )
                    )
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                doneLatch.countDown()
            }
        }

        // Thread 2: Concurrent snooze toggles
        executor.execute {
            try {
                startLatch.await()
                for (i in 1..1000) {
                    RadarStateRepository.updateSnoozeState {
                        RadarAudioSnoozeState(
                            isSnoozed = (i % 2 == 0),
                            mode = if (i % 2 == 0) RadarAudioSnoozeMode.FAST_ONLY else RadarAudioSnoozeMode.NONE
                        )
                    }
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                doneLatch.countDown()
            }
        }

        // Thread 3: Concurrent light-mode toggles
        executor.execute {
            try {
                startLatch.await()
                for (i in 1..1000) {
                    RadarStateRepository.updateLightState {
                        it.copy(
                            lightMode = if (i % 2 == 0) RadarLightMode.SOLID else RadarLightMode.PELOTON,
                            isLightControlSupported = true,
                            isAutoMode = false
                        )
                    }
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                stop.set(true)
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue("Concurrent state stress test timed out", doneLatch.await(10, TimeUnit.SECONDS))
        executor.shutdownNow()
        assertNull("Unexpected exception during concurrent state updates", failure.get())

        val finalState = RadarStateRepository.getState()
        assertEquals(RadarSource.BLE, finalState.source)
        assertEquals(ThreatLevel.FAST_APPROACHING, finalState.overallThreatLevel)
        assertEquals(1, finalState.targets.size)
        assertFalse(finalState.isAutoMode)
        assertTrue(finalState.isLightControlSupported)
    }

    @Test
    fun parallelRadarLightControllerAndSmartBrakeLightDetector_haveZeroAbBaDeadlocks_tstCon056_4() {
        val mockDevice = object : RadarLightDeviceClient {
            override fun setLightMode(mode: RadarLightMode): Boolean = true
            override fun disconnectLight() {}
        }
        val mockContext = mockk<Context>(relaxed = true)
        val tempFilesDir = java.nio.file.Files.createTempDirectory("radar-stress-").toFile()
        io.mockk.every { mockContext.applicationContext } returns mockContext
        io.mockk.every { mockContext.filesDir } returns tempFilesDir
        com.atrainingtracker.trainingtracker.settings.SettingsDataStore.setInstanceForTesting(mockk(relaxed = true))
        SmartBrakeLightDetector.onSpeedChanged(8.0f, nowMs = 1000L)
        SmartBrakeLightDetector.setDvDtForTesting(-3.0f)

        val iterations = 5000
        val workerCount = 4
        val executor = Executors.newFixedThreadPool(workerCount + 1)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(workerCount)
        val failure = AtomicReference<Throwable?>(null)
        val deadlockDetected = AtomicBoolean(false)

        // Watchdog polling JVM ThreadMXBean.findDeadlockedThreads() reflectively (Android SDK jar omits java.lang.management)
        val watchdog = executor.submit {
            val mgmtClass = Class.forName("java.lang.management.ManagementFactory")
            val threadBean = mgmtClass.getMethod("getThreadMXBean").invoke(null)
            val findDeadlocked = threadBean.javaClass.getMethod("findDeadlockedThreads")
            while (doneLatch.count > 0) {
                val deadlocked = findDeadlocked.invoke(threadBean) as? LongArray
                if (deadlocked != null && deadlocked.isNotEmpty()) {
                    deadlockDetected.set(true)
                    break
                }
                try {
                    Thread.sleep(5L)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }

        // Thread 1 (Path A -> B): register/unregister radar devices toggling hasHardwareBrakeSensor
        executor.execute {
            try {
                startLatch.await()
                for (i in 0 until iterations) {
                    RadarLightController.registerRadarDevice(
                        device = mockDevice,
                        lightSupported = true,
                        isStvzo = (i % 5 == 0),
                        hasHardwareBrakeSensor = (i % 3 == 0)
                    )
                    if (i % 4 == 0) {
                        RadarLightController.unregisterRadarDevice(mockDevice)
                    }
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                doneLatch.countDown()
            }
        }

        // Thread 2 (Path B -> A): IMU deceleration bursts triggering/releasing brake light
        executor.execute {
            try {
                startLatch.await()
                var ts = 2000L
                for (i in 0 until iterations) {
                    SmartBrakeLightDetector.onSpeedChanged(8.0f, nowMs = ts)
                    SmartBrakeLightDetector.setDvDtForTesting(-3.0f)
                    SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -3.5f, 0f, nowMs = ts)
                    ts += 300L
                    SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -3.5f, 0f, nowMs = ts)
                    ts += 100L
                    SmartBrakeLightDetector.onLinearAccelerationChanged(0f, 0.0f, 0f, nowMs = ts)
                    ts += 100L
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                doneLatch.countDown()
            }
        }

        // Thread 3 (Path A): mode switches, pause/stop, and pre-brake hold restoration
        executor.execute {
            try {
                startLatch.await()
                for (i in 0 until iterations) {
                    if (i % 2 == 0) {
                        RadarLightController.setManualLightMode(RadarLightMode.SOLID)
                    } else {
                        RadarLightController.setAutoMode()
                    }
                    if (i % 10 == 0) {
                        RadarLightController.onWorkoutPaused()
                    } else if (i % 15 == 0) {
                        RadarLightController.onWorkoutStopped(mockContext)
                    }
                    RadarLightController.checkAndRestorePreBrakeMode(nowMs = 10_000_000L + i)
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                doneLatch.countDown()
            }
        }

        // Thread 4 (Path B): concurrent stopListening, resetMotionState, and AtomicBoolean toggles
        executor.execute {
            try {
                startLatch.await()
                for (i in 0 until iterations) {
                    SmartBrakeLightDetector.isEnabled = (i % 2 == 0)
                    SmartBrakeLightDetector.isAutoBypassed = (i % 3 == 0)
                    if (i % 5 == 0) {
                        SmartBrakeLightDetector.stopListening()
                    }
                    if (i % 20 == 0) {
                        SmartBrakeLightDetector.resetMotionState()
                    }
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(15, TimeUnit.SECONDS)
        watchdog.cancel(true)
        executor.shutdownNow()

        assertFalse("AB-BA deadlock detected by ThreadMXBean!", deadlockDetected.get())
        assertTrue("Concurrency stress test timed out (possible deadlock)", finished)
        assertNull("Unexpected exception during concurrency stress test: ${failure.get()}", failure.get())
    }
}
