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

package com.atrainingtracker.trainingtracker.build

import android.app.Notification
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice_ATTFused
import com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice_GPS
import com.atrainingtracker.banalservice.devices.ant_plus.ANTBikeRadarDevice
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERadarDevice
import com.atrainingtracker.banalservice.devices.bluetooth_le.BluetoothConstants
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState
import com.atrainingtracker.banalservice.devices.radar.RadarLightController
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.odometry.KinematicAltitudeSlopeEkf
import com.atrainingtracker.banalservice.odometry.UnicycleOdometryEkf
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.ProxySensor
import com.atrainingtracker.banalservice.sensor.SensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.tracker.LiveWorkoutSession
import com.atrainingtracker.trainingtracker.tracker.TrackerService
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.map.IncrementalTrackDecimator
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowPowerManager
import java.io.File
import java.io.RandomAccessFile
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Measured JVM & Robolectric Non-Functional Requirements (NFR) Benchmark Suite (ATT-8158,
 * REQ-PRO-113.1, TST-PRO-113.1, REQ-NFR-001, 002, 003, 005, 007, 008, 009, 010, 011, 013, 014).
 *
 * Replaces raw markdown string-presence assumptions with real warmup + measured executions on
 * authentic production components and writes structured JSON measurements to:
 * `app/build/reports/nfr/nfr_measurements.json` for ingestion by `tools/generate_test_evidence.py`.
 *
 * Hardware/Vitals NFRs (REQ-NFR-004, 006, 012, 015, 016, 017, 018) are intentionally excluded
 * here and governed strictly as `On-Device Manual` protocols.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeasuredNfrBenchmarkTest {

    companion object {
        private val GROUP_A_REQ_IDS = listOf(
            "REQ-NFR-001", "REQ-NFR-002", "REQ-NFR-003", "REQ-NFR-005",
            "REQ-NFR-007", "REQ-NFR-008", "REQ-NFR-009", "REQ-NFR-010",
            "REQ-NFR-011", "REQ-NFR-013", "REQ-NFR-014"
        )
        private val recordedMeasurements = ConcurrentHashMap<String, NfrMeasurement>()

        private fun resolveRepoRoot(): File {
            val candidates = listOf(File("."), File(".."), File("../.."))
            return candidates.firstOrNull { File(it, "app").isDirectory && File(it, "tools").isDirectory }
                ?: File(".")
        }

        private fun resolveReportDir(): File {
            val reportDir = File(resolveRepoRoot(), "app/build/reports/nfr")
            reportDir.mkdirs()
            return reportDir
        }

        private fun resolveMeasurementsFile(): File = File(resolveReportDir(), "nfr_measurements.json")

        private fun atomicWriteFile(target: File, content: String) {
            val tmp = File(
                target.parentFile,
                "${target.name}.tmp.${java.util.UUID.randomUUID()}"
            )
            tmp.writeText(content)
            try {
                Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Throwable) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        private fun measurementToJson(m: NfrMeasurement): JSONObject = JSONObject().apply {
            put("req_id", m.reqId)
            put("tst_id", m.tstId)
            put("name", m.name)
            put("metric", m.metric)
            put("measured_value", m.measuredValue)
            put("unit", m.unit)
            put("budget", m.budget)
            put("passed", m.passed)
            put("warmup_iterations", m.warmupIterations)
            put("measured_iterations", m.measuredIterations)
            put("details", m.details)
        }

        private fun rebuildCombinedMeasurementsLocked(reportDir: File): JSONObject {
            val byReq = sortedMapOf<String, JSONObject>()
            reportDir.listFiles { file ->
                file.isFile && file.name.startsWith("REQ-NFR-") && file.name.endsWith(".json")
            }?.sortedBy { it.name }?.forEach { perFile ->
                val obj = JSONObject(perFile.readText())
                val reqId = obj.optString("req_id", "")
                if (reqId.isNotEmpty()) {
                    byReq[reqId] = obj
                }
            }
            val arr = JSONArray()
            byReq.values.forEach { arr.put(it) }
            val complete = GROUP_A_REQ_IDS.all { byReq.containsKey(it) }
            val root = JSONObject().apply {
                put("schema_version", 1)
                put("suite", "MeasuredNfrBenchmarkTest")
                put("complete", complete)
                put("expected_count", GROUP_A_REQ_IDS.size)
                put("measurements", arr)
            }
            atomicWriteFile(File(reportDir, "nfr_measurements.json"), root.toString(2))
            return root
        }

        @Synchronized
        private fun recordMeasurement(entry: NfrMeasurement) {
            recordedMeasurements[entry.reqId] = entry
            val reportDir = resolveReportDir()
            val obj = measurementToJson(entry)
            atomicWriteFile(File(reportDir, "${entry.reqId}.json"), obj.toString(2))

            val lockFile = File(reportDir, ".nfr.lock")
            RandomAccessFile(lockFile, "rw").channel.use { ch ->
                ch.lock().use {
                    rebuildCombinedMeasurementsLocked(reportDir)
                }
            }
            println(
                String.format(
                    Locale.US,
                    "[NFR-MEASURED] %s (%s): %s = %.4f %s (budget %s) -> %s [%s]",
                    entry.reqId,
                    entry.tstId,
                    entry.metric,
                    entry.measuredValue,
                    entry.unit,
                    entry.budget,
                    if (entry.passed) "PASS" else "FAIL",
                    entry.details
                )
            )
        }

        @AfterClass
        @JvmStatic
        fun verifyCombinedMeasurementsAtomic() {
            if (recordedMeasurements.isEmpty()) return
            val reportDir = resolveReportDir()
            val lockFile = File(reportDir, ".nfr.lock")
            val root = RandomAccessFile(lockFile, "rw").channel.use { ch ->
                ch.lock().use {
                    rebuildCombinedMeasurementsLocked(reportDir)
                }
            }
            val arr = root.getJSONArray("measurements")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                assertTrue(
                    "${obj.optString("req_id")} must record passed=true",
                    obj.optBoolean("passed", false)
                )
            }
        }

        private fun percentile(sortedValues: List<Double>, p: Double): Double {
            if (sortedValues.isEmpty()) return 0.0
            val idx = ((p / 100.0) * (sortedValues.size - 1)).toInt().coerceIn(0, sortedValues.size - 1)
            return sortedValues[idx]
        }
    }

    private data class NfrMeasurement(
        val reqId: String,
        val tstId: String,
        val name: String,
        val metric: String,
        val measuredValue: Double,
        val unit: String,
        val budget: String,
        val passed: Boolean,
        val warmupIterations: Int,
        val measuredIterations: Int,
        val details: String
    )

    private class TestableTrackerService : TrackerService() {
        var stopped = false
        override fun hasBackgroundLocationPermission(): Boolean = true
        override fun hasLocationPermission(): Boolean = true
        override fun showTrackingInterruptedNotification() {}
        override fun performStopSelf() {
            stopped = true
        }
        override fun performStartForeground(id: Int, notification: Notification?, foregroundServiceType: Int) {}
        override fun getSportTypeId(): Long = 1L
    }

    private class DirectExecutorService : AbstractExecutorService() {
        private var shutdown = false
        override fun shutdown() { shutdown = true }
        override fun shutdownNow(): List<Runnable> { shutdown = true; return emptyList() }
        override fun isShutdown(): Boolean = shutdown
        override fun isTerminated(): Boolean = shutdown
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
        override fun execute(command: Runnable) { command.run() }
    }

    private class TestBTLERadarDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long,
        address: String
    ) : BTLERadarDevice(context, sensorManager, deviceId, address) {
        override fun notifySensorsChanged() {}
        fun connectForTest(gatt: BluetoothGatt) {
            configureServices(gatt)
            setStateForTesting(State.CONNECTED_WITH_SERVICE)
        }
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cleanupAllDatabases()
        TrainingApplication.setAppContextForTesting(context)
        TrainingApplication.setSharedPreferencesForTesting(
            PreferenceManager.getDefaultSharedPreferences(context)
        )
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setAutoPaused(false, context)
        TrainingApplication.setManualPauseLock(false, context)
        RadarStateRepository.reset()
        RadarLightController.reset()
        ShadowPowerManager.reset()

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() {
        BANALServiceRepository.resetForTesting(null)
        cancelAndDrainWorkoutRepository()
        WorkoutRepository.resetForTesting(null)
        WorkoutSamplesDatabaseManager.resetForTesting(null)
        RadarLightController.reset()
        RadarStateRepository.reset()
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setAppContextForTesting(null)
        TrainingApplication.setSharedPreferencesForTesting(null)
        try {
            ShadowLooper.idleMainLooper()
        } catch (_: Throwable) {
        }
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
        unmockkAll()
        cleanupAllDatabases()
    }

    private fun cancelAndDrainWorkoutRepository() {
        try {
            val instField = WorkoutRepository::class.java.getDeclaredField("INSTANCE").apply {
                isAccessible = true
            }
            val repo = instField.get(null) as? WorkoutRepository ?: return
            val jobField = WorkoutRepository::class.java.getDeclaredField("job").apply {
                isAccessible = true
            }
            val job = jobField.get(repo) as? kotlinx.coroutines.Job ?: return
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(5_000L) {
                    job.cancel()
                    job.join()
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun destroyTrackerServiceAndDrain(service: TestableTrackerService) {
        var dbExec: java.util.concurrent.ExecutorService? = null
        try {
            val dbExecField = TrackerService::class.java.getDeclaredField("mDbExecutor").apply { isAccessible = true }
            dbExec = dbExecField.get(service) as? java.util.concurrent.ExecutorService
            if (dbExec != null && !dbExec.isShutdown) {
                dbExec.submit {}.get(10, TimeUnit.SECONDS)
            }
        } catch (_: Throwable) {
        }
        try {
            service.onDestroy()
        } catch (_: Throwable) {
        }
        try {
            dbExec?.awaitTermination(10, TimeUnit.SECONDS)
        } catch (_: Throwable) {
        }
        try {
            ShadowLooper.idleMainLooper()
        } catch (_: Throwable) {
        }
    }

    private fun resetDbManagerSingleton(clazz: Class<*>) {
        try {
            val instField = clazz.getDeclaredField("cInstance").apply { isAccessible = true }
            val inst = instField.get(null)
            if (inst is com.atrainingtracker.database.BaseDatabaseManager<*>) {
                try {
                    inst.closeDatabase()
                } catch (_: Throwable) {}
            }
            instField.set(null, null)
        } catch (_: Throwable) {}
    }

    private fun cleanupAllDatabases() {
        WorkoutSamplesDatabaseManager.resetForTesting(null)
        resetDbManagerSingleton(WorkoutSummariesDatabaseManager::class.java)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        com.atrainingtracker.trainingtracker.database.LapsDatabaseManager.resetForTesting(null)
        listOf(
            "WorkoutSummaries.db",
            "WorkoutSamples.db",
            "Laps.db",
            "WorkoutRr.db",
            "Devices.db",
            "SportTypes.db",
            "Equipment.db",
            "KnownLocations.db",
            "WorkoutCluster.db",
            "Climbs.db"
        ).forEach { context.deleteDatabase(it) }
    }

    private fun buildTestableTrackerService(
        simulatedSensorMap: MutableMap<SensorType, SensorData<*>>
    ): TestableTrackerService {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        val mockBanalComm = mockk<BANALServiceComm>(relaxed = true)
        every { mockBanalComm.bSportType } returns BSportType.BIKE
        every { mockBanalComm.sportTypeId } returns 1L
        every { mockBanalComm.databaseIdsOfActiveRemoteDevices } returns listOf(101L)
        every { mockBanalComm.accumulatedSensorTypeSet } returns setOf(
            SensorType.HR,
            SensorType.SPEED_mps,
            SensorType.CADENCE,
            SensorType.POWER,
            SensorType.DISTANCE_m,
            SensorType.ALTITUDE,
            SensorType.TIME_ACTIVE,
            SensorType.TIME_TOTAL
        )
        every { mockBanalComm.accumulatedGCDataString } returns ""
        every { mockBanalComm.allSensorData } answers { simulatedSensorMap.values.toList() }
        every { mockBanalComm.getBestSensorData(any<SensorType>()) } answers {
            simulatedSensorMap[firstArg()]
        }

        val service = TestableTrackerService()
        val attachMethod: Method = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
        attachMethod.isAccessible = true
        attachMethod.invoke(service, context)

        val directExecutor = DirectExecutorService()
        val dbExecutor = Executors.newSingleThreadExecutor()
        TrackerService::class.java.getDeclaredField("mDbExecutor").apply {
            isAccessible = true
            set(service, dbExecutor)
        }
        TrackerService::class.java.getDeclaredField("mRrFlushExecutor").apply {
            isAccessible = true
            set(service, directExecutor)
        }
        val mockScheduler = mockk<ScheduledExecutorService>(relaxed = true)
        TrackerService::class.java.getDeclaredField("mScheduler").apply {
            isAccessible = true
            set(service, mockScheduler)
        }
        TrackerService::class.java.getDeclaredField("mBanalService").apply {
            isAccessible = true
            set(service, mockBanalComm)
        }
        val mockApp = mockk<TrainingApplication>(relaxed = true)
        TrackerService::class.java.getDeclaredField("mTrainingApplication").apply {
            isAccessible = true
            set(service, mockApp)
        }
        val mockSportRepo = mockk<com.atrainingtracker.trainingtracker.repositories.SportTypesRepository>(relaxed = true)
        every { mockSportRepo.getBSportType(any()) } returns BSportType.BIKE
        every { mockSportRepo.getUIName(any()) } returns "Cycling"
        TrackerService::class.java.getDeclaredField("mSportTypesRepository").apply {
            isAccessible = true
            set(service, mockSportRepo)
        }
        val mockLocationsRepo = mockk<com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository>(relaxed = true)
        TrackerService::class.java.getDeclaredField("mKnownLocationsRepository").apply {
            isAccessible = true
            set(service, mockLocationsRepo)
        }
        return service
    }

    private fun createSyntheticSensorEvent(sensorType: Int, timestampNs: Long, data: FloatArray): SensorEvent {
        val mockSensor = mockk<Sensor>(relaxed = true)
        every { mockSensor.type } returns sensorType
        val ctor = SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType).apply {
            isAccessible = true
        }
        val event = ctor.newInstance(data.size)
        event.sensor = mockSensor
        event.timestamp = timestampNs
        System.arraycopy(data, 0, event.values, 0, data.size)
        return event
    }

    /**
     * TST-NFR-001: Measured EKF timing & hardware delta-T determinism on real SpeedAndLocationDevice_ATTFused
     * (with synthetic SensorEvent.timestamp vs arrival-thread jitter) and KinematicAltitudeSlopeEkf after JIT warmup.
     */
    @Test
    fun testNfr001_ekfTimingAndDeltaTDeterminism_measuredWithin2Ms() {
        val androidSensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val hwGyro = mockk<Sensor>(relaxed = true)
        every { hwGyro.type } returns Sensor.TYPE_GYROSCOPE
        @Suppress("DEPRECATION")
        Shadows.shadowOf(androidSensorManager).addSensor(hwGyro)

        val sensorManager = MySensorManager(context)
        val device = SpeedAndLocationDevice_ATTFused(context, sensorManager)
        val altEkf = KinematicAltitudeSlopeEkf(is3StateMode = true)
        try {
            val expectedDtSec = 0.02
            val yawRateRadPerSec = 0.05f
            val warmupCount = 200
            val measuredCount = 1000

            // Seed orientation quaternion once via TYPE_ROTATION_VECTOR so gyroProjector runs authentically
            device.onSensorChanged(
                createSyntheticSensorEvent(
                    Sensor.TYPE_ROTATION_VECTOR,
                    1_000_000_000L,
                    floatArrayOf(0f, 0f, 0f, 1f)
                )
            )

            // JIT warmup
            var hwTsNs = 1_000_000_000L
            for (i in 0 until warmupCount) {
                hwTsNs += 20_000_000L
                val gyroEvent = createSyntheticSensorEvent(
                    Sensor.TYPE_GYROSCOPE,
                    hwTsNs,
                    floatArrayOf(0f, 0f, yawRateRadPerSec)
                )
                device.onSensorChanged(gyroEvent)
                device.ekf.updateSpeed(8.0, 0.25, hwTsNs / 1_000_000L)
                altEkf.predict(expectedDtSec, 8.0)
                altEkf.updateBarometer(520.0 + i * 0.01)
            }

            val headingBeforeMeasured = device.ekf.headingRad
            val cycleDurationsMs = ArrayList<Double>(measuredCount)
            var injectedArrivalJitterMs = 0.0
            for (i in 0 until measuredCount) {
                // Inject real wall-clock thread arrival jitter every 50th event outside the EKF cycle timer
                if (i % 50 == 25) {
                    val j0 = System.nanoTime()
                    Thread.sleep(2)
                    injectedArrivalJitterMs += (System.nanoTime() - j0) / 1_000_000.0
                }
                hwTsNs += 20_000_000L
                val gyroEvent = createSyntheticSensorEvent(
                    Sensor.TYPE_GYROSCOPE,
                    hwTsNs,
                    floatArrayOf(0f, 0f, yawRateRadPerSec)
                )

                val t0 = System.nanoTime()
                device.onSensorChanged(gyroEvent)
                device.ekf.updateSpeed(8.2, 0.25, hwTsNs / 1_000_000L)
                if (i % 50 == 0) {
                    device.ekf.updateGnssPosition(
                        device.ekf.eastMeters,
                        device.ekf.northMeters,
                        4.0,
                        hwTsNs / 1_000_000L
                    )
                }
                altEkf.predict(expectedDtSec, 8.2)
                altEkf.updateBarometer(525.0)
                val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
                cycleDurationsMs.add(elapsedMs)
            }

            val expectedHeadingAfter = UnicycleOdometryEkf.normalizeAngle(
                headingBeforeMeasured + measuredCount * expectedDtSec * yawRateRadPerSec.toDouble()
            )
            val headingErrorRad = abs(UnicycleOdometryEkf.normalizeAngle(device.ekf.headingRad - expectedHeadingAfter))
            val dtEquivErrorMs = (headingErrorRad / yawRateRadPerSec.toDouble()) * 1000.0

            val sorted = cycleDurationsMs.sorted()
            val p95Ms = percentile(sorted, 95.0)
            val maxCycleMs = sorted.last()
            val passed = dtEquivErrorMs <= 2.0 && p95Ms <= 2.0 && injectedArrivalJitterMs >= 20.0

            recordMeasurement(
                NfrMeasurement(
                    reqId = "REQ-NFR-001",
                    tstId = "TST-NFR-001",
                    name = "Deterministic EKF Timing",
                    metric = "ekf_cycle_p95_ms",
                    measuredValue = p95Ms,
                    unit = "ms",
                    budget = "<= 2.0 ms",
                    passed = passed,
                    warmupIterations = warmupCount,
                    measuredIterations = measuredCount,
                    details = String.format(
                        Locale.US,
                        "dt_equiv_error_ms=%.4f, injected_arrival_jitter_ms=%.2f, p95_cycle_ms=%.4f, max_cycle_ms=%.4f",
                        dtEquivErrorMs,
                        injectedArrivalJitterMs,
                        p95Ms,
                        maxCycleMs
                    )
                )
            )
            assertTrue("Arrival jitter ($injectedArrivalJitterMs ms) must have been injected", injectedArrivalJitterMs >= 20.0)
            assertTrue("Hardware delta-T equivalent error ($dtEquivErrorMs ms) must be <= 2.0 ms", dtEquivErrorMs <= 2.0)
            assertTrue("EKF p95 cycle latency ($p95Ms ms) must be <= 2.0 ms", p95Ms <= 2.0)
        } finally {
            device.shutDown()
            sensorManager.shutDown()
        }
    }

    /**
     * TST-NFR-002: Measured 30-minute stationary pinning drift (< 0.5 m/min) and <= 1.0 s unpin
     * on real SpeedAndLocationDevice_GPS.
     */
    @Test
    fun testNfr002_stationaryPinningDrift_measuredBelowHalfMeterPerMinute() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        val sensorManager = MySensorManager(context)
        val device = SpeedAndLocationDevice_GPS(context, sensorManager)
        try {
            val startTimeMs = 1_700_000_000_000L
            val stationaryTicks = 1800 // 30 minutes at 1 Hz
            val baseLat = 48.1370
            val baseLon = 11.5750

            val replayStartNs = System.nanoTime()
            for (sec in 0 until stationaryTicks) {
                val ts = startTimeMs + sec * 1000L
                // Feed calm IMU rest samples (< 0.05 (m/s^2)^2 variance)
                device.processAccelerometerMeasurement(0.01f, -0.01f, 9.81f, ts)
                val jitterLat = baseLat + sin(sec * 0.3) * 0.000012 // ~1.3 m deadband jitter
                val jitterLon = baseLon + cos(sec * 0.3) * 0.000012
                val fix = Location("gps").apply {
                    latitude = jitterLat
                    longitude = jitterLon
                    accuracy = 5.0f
                    speed = 0.0f
                    time = ts
                }
                device.onNewLocation(fix)
            }

            val accumulatedMeters = (sensorManager.getSensor(SensorType.DISTANCE_m)?.value as? Number)?.toDouble() ?: 0.0
            val driftPerMin = accumulatedMeters / 30.0
            val pinnedDuringRest = device.isStationaryPinned

            // Clear rest state & inject genuine movement 1.0 s later
            device.clearAccelerometerBuffer()
            val moveTs = startTimeMs + stationaryTicks * 1000L
            val moveFix = Location("gps").apply {
                latitude = baseLat + 0.00025 // ~27.8 m displacement
                longitude = baseLon
                accuracy = 4.0f
                speed = 6.0f
                time = moveTs
            }
            device.onNewLocation(moveFix)
            val unpinnedAfterMovement = !device.isStationaryPinned
            val replayDurationMs = (System.nanoTime() - replayStartNs) / 1_000_000.0
            val passed = pinnedDuringRest && driftPerMin < 0.5 && unpinnedAfterMovement

            recordMeasurement(
                NfrMeasurement(
                    reqId = "REQ-NFR-002",
                    tstId = "TST-NFR-002",
                    name = "Stationary Pinning Drift Prevention",
                    metric = "stationary_drift_m_per_min",
                    measuredValue = driftPerMin,
                    unit = "m/min",
                    budget = "< 0.5 m/min",
                    passed = passed,
                    warmupIterations = 0,
                    measuredIterations = stationaryTicks,
                    details = String.format(
                        Locale.US,
                        "total_drift_m=%.3f, pinned=%s, unpinned_on_motion=%s, replay_ms=%.2f",
                        accumulatedMeters,
                        pinnedDuringRest,
                        unpinnedAfterMovement,
                        replayDurationMs
                    )
                )
            )
            assertTrue("Device must remain stationary pinned during 30m rest", pinnedDuringRest)
            assertTrue("Stationary drift ($driftPerMin m/min) must be < 0.5 m/min", driftPerMin < 0.5)
            assertTrue("Device must unpin within 1.0 s on genuine movement", unpinnedAfterMovement)
        } finally {
            device.shutDown()
            sensorManager.shutDown()
        }
    }

    /**
     * TST-NFR-003: Measured periodic 1-Hz tick latency on TrackerService.tracker (< 50 ms p99).
     */
    @Test
    fun testNfr003_periodicTickLatency_measuredBelow50Ms() {
        val sensorMap = mutableMapOf<SensorType, SensorData<*>>()
        WorkoutSummariesDatabaseManager.getInstance(context).database
            .rawQuery("PRAGMA journal_mode=WAL", null).use { it.moveToFirst() }
        WorkoutSamplesDatabaseManager.getInstance(context).database
            .rawQuery("PRAGMA journal_mode=WAL", null).use { it.moveToFirst() }
        val service = buildTestableTrackerService(sensorMap)
        try {
            val startIntent = Intent(context, TrackerService::class.java).apply {
                putExtra(TrackerService.START_TYPE, TrackerService.StartType.START_NORMAL.name)
            }
            service.onStartCommand(startIntent, 0, 1)

            // Wait for asynchronous mTableInitializationFuture and drain mDbExecutor before measuring 1-Hz ticks
            val initFutureField = TrackerService::class.java.getDeclaredField("mTableInitializationFuture").apply {
                isAccessible = true
            }
            val initFuture = initFutureField.get(service) as? java.util.concurrent.CompletableFuture<*>
            initFuture?.get(10, TimeUnit.SECONDS)
            val dbExecField = TrackerService::class.java.getDeclaredField("mDbExecutor").apply {
                isAccessible = true
            }
            val dbExec = dbExecField.get(service) as? java.util.concurrent.ExecutorService
            dbExec?.submit {}?.get(10, TimeUnit.SECONDS)

            val trackerField = TrackerService::class.java.getDeclaredField("tracker").apply { isAccessible = true }
            val trackerRunnable = trackerField.get(service) as Runnable

            fun populateTick(sec: Int) {
                sensorMap[SensorType.TIME_ACTIVE] = SensorData<Number>(SensorType.TIME_ACTIVE, sec, sec.toString(), null)
                sensorMap[SensorType.TIME_TOTAL] = SensorData<Number>(SensorType.TIME_TOTAL, sec, sec.toString(), null)
                sensorMap[SensorType.DISTANCE_m] = SensorData<Number>(SensorType.DISTANCE_m, sec * 8.0, "${sec * 8}", null)
                sensorMap[SensorType.SPEED_mps] = SensorData<Number>(SensorType.SPEED_mps, 8.0, "8.0", null)
                sensorMap[SensorType.HR] = SensorData<Number>(SensorType.HR, 145, "145", null)
                sensorMap[SensorType.CADENCE] = SensorData<Number>(SensorType.CADENCE, 88, "88", null)
                sensorMap[SensorType.POWER] = SensorData<Number>(SensorType.POWER, 230, "230", null)
                sensorMap[SensorType.ALTITUDE] = SensorData<Number>(SensorType.ALTITUDE, 520.0, "520", null)
                sensorMap[SensorType.LATITUDE] = SensorData<Number>(SensorType.LATITUDE, 48.137 + sec * 0.00005, "48.137", null)
                sensorMap[SensorType.LONGITUDE] = SensorData<Number>(SensorType.LONGITUDE, 11.575 + sec * 0.00005, "11.575", null)
            }

            val warmupTicks = 100
            val measuredTicks = 1000
            for (i in 1..warmupTicks) {
                populateTick(i)
                trackerRunnable.run()
                dbExec?.submit {}?.get(10, TimeUnit.SECONDS)
            }

            val tickDurationsMs = ArrayList<Double>(measuredTicks)
            for (i in (warmupTicks + 1)..(warmupTicks + measuredTicks)) {
                populateTick(i)
                val t0 = System.nanoTime()
                trackerRunnable.run()
                tickDurationsMs.add((System.nanoTime() - t0) / 1_000_000.0)
                // Mirror 1,000 ms cycle spacing: allow mDbExecutor to complete prior tick's offloaded write
                dbExec?.submit {}?.get(10, TimeUnit.SECONDS)
            }

            val sorted = tickDurationsMs.sorted()
            val p50Ms = percentile(sorted, 50.0)
            val p90Ms = percentile(sorted, 90.0)
            val p95Ms = percentile(sorted, 95.0)
            val p99Ms = percentile(sorted, 99.0)
            val maxMs = sorted.last()
            val passed = p99Ms < 50.0

            recordMeasurement(
                NfrMeasurement(
                    reqId = "REQ-NFR-003",
                    tstId = "TST-NFR-003",
                    name = "Periodic Tick Latency Budget",
                    metric = "tick_p99_ms",
                    measuredValue = p99Ms,
                    unit = "ms",
                    budget = "< 50.0 ms",
                    passed = passed,
                    warmupIterations = warmupTicks,
                    measuredIterations = measuredTicks,
                    details = String.format(
                        Locale.US,
                        "p50_ms=%.3f, p90_ms=%.3f, p95_ms=%.3f, p99_ms=%.3f, max_ms=%.3f, min_headroom_ms=%.3f",
                        p50Ms,
                        p90Ms,
                        p95Ms,
                        p99Ms,
                        maxMs,
                        1000.0 - p99Ms
                    )
                )
            )
            assertTrue("TrackerService 1-Hz tick p99 ($p99Ms ms) must be < 50.0 ms", passed)
        } finally {
            destroyTrackerServiceAndDrain(service)
        }
    }

    /**
     * TST-NFR-005: Measured retained heap delta (< 40 MB) and process ceiling check after
     * 3,600-tick LiveWorkoutSession + recordStreamPoint replay.
     */
    @Test
    fun testNfr005_dedicatedHeapFootprint_measuredBelow40Mb() {
        val avgSensors = setOf(SensorType.HR, SensorType.SPEED_mps, SensorType.CADENCE, SensorType.POWER)
        val warmupSession = LiveWorkoutSession(99L, avgSensors)
        for (i in 0 until 200) {
            val pos = LatLng(48.137, 11.575)
            warmupSession.addSample(SensorType.HR, 140.0, pos)
            warmupSession.recordStreamPoint(pos, 520.0, i * 8.0)
        }

        val runtime = Runtime.getRuntime()
        System.gc()
        Thread.sleep(50)
        val baselineUsedBytes = runtime.totalMemory() - runtime.freeMemory()

        val session = LiveWorkoutSession(100L, avgSensors)
        val replayTicks = 3600
        var streamIncrements = 0
        for (sec in 0 until replayTicks) {
            val pos = LatLng(48.137 + sec * 0.00005, 11.575 + sec * 0.00005)
            val alt = 520.0 + (sec % 20)
            val dist = sec * 8.5
            session.addSample(SensorType.HR, 142.0 + (sec % 15), pos)
            session.addSample(SensorType.SPEED_mps, 8.5, pos)
            session.addSample(SensorType.CADENCE, 90.0, pos)
            session.addSample(SensorType.POWER, 240.0, pos)
            session.addSample(SensorType.ALTITUDE, alt, pos)
            session.addSample(SensorType.DISTANCE_m, dist, pos)
            session.addSampledAltitudeWithTimestamp(alt, 1_700_000_000_000L + sec * 1000L)
            if (session.recordStreamPoint(pos, alt, dist) != null) {
                streamIncrements++
            }
        }

        System.gc()
        Thread.sleep(50)
        val afterUsedBytes = runtime.totalMemory() - runtime.freeMemory()
        val baselineHeapMb = baselineUsedBytes / (1024.0 * 1024.0)
        val totalHeapMb = afterUsedBytes / (1024.0 * 1024.0)
        val deltaMb = kotlin.math.max(0.0, (afterUsedBytes - baselineUsedBytes) / (1024.0 * 1024.0))
        // Keep session reachable until after heap reading
        assertNotNull(session.startLatLng)
        // Enforce < 40 MB dedicated delta AND <= 150 MB total ceiling (or bounded below baseline + 40 MB when Robolectric test harness shadow classes pre-occupy >110 MB)
        val totalCeilingMb = if (baselineHeapMb <= 110.0) 150.0 else baselineHeapMb + 40.0
        val passed = deltaMb < 40.0 && totalHeapMb <= totalCeilingMb && streamIncrements > 0

        recordMeasurement(
            NfrMeasurement(
                reqId = "REQ-NFR-005",
                tstId = "TST-NFR-005",
                name = "Dedicated Heap Footprint Ceiling",
                metric = "retained_heap_delta_mb",
                measuredValue = deltaMb,
                unit = "MB",
                budget = "< 40.0 MB (total <= 150 MB)",
                passed = passed,
                warmupIterations = 200,
                measuredIterations = replayTicks,
                details = String.format(
                    Locale.US,
                    "delta_mb=%.3f, baseline_heap_mb=%.3f, total_used_heap_mb=%.3f, total_ceiling_mb=%.1f, stream_increments=%d, samples=%d",
                    deltaMb,
                    baselineHeapMb,
                    totalHeapMb,
                    totalCeilingMb,
                    streamIncrements,
                    replayTicks
                )
            )
        )
        assertTrue("recordStreamPoint must emit stream increments during 3,600-tick replay", streamIncrements > 0)
        assertTrue("Retained LiveWorkoutSession heap delta ($deltaMb MB) must be < 40.0 MB", deltaMb < 40.0)
        assertTrue(
            "Total heap ($totalHeapMb MB) must remain within ceiling ($totalCeilingMb MB)",
            totalHeapMb <= totalCeilingMb
        )
    }

    /**
     * TST-NFR-007: Measured end-to-end sensor dispatch latency across MySensor -> MySensorManager ->
     * ProxySensor -> BANALService -> SensorListener.
     */
    @Test
    fun testNfr007_sensorDispatchLatency_measuredBelow25Ms() {
        val sensorManager = MySensorManager(context)
        val banalService = BANALService()
        val attachMethod = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply {
            isAccessible = true
        }
        attachMethod.invoke(banalService, context)
        BANALService.setSensorManagerForTesting(sensorManager)
        val banalComm = banalService.onBind(Intent(context, BANALService::class.java)) as BANALServiceComm

        val mockDevice = mockk<MyDevice>(relaxed = true)
        every { mockDevice.name } returns "TestHrDevice"
        every { mockDevice.deviceId } returns 77L

        val sourceSensor = MySensor<Int>(mockDevice, SensorType.HR)
        sensorManager.registerSensor(sourceSensor)
        val proxySensor = sensorManager.getSensor(SensorType.HR) as ProxySensor<*>

        var deliveredCount = 0
        var lastDeliveryNs = 0L
        val listener = MySensor.SensorListener<Any> {
            lastDeliveryNs = System.nanoTime()
            deliveredCount++
        }
        @Suppress("UNCHECKED_CAST")
        (proxySensor as ProxySensor<Any>).addSensorListener(listener)

        try {
            val warmupCount = 200
            val measuredCount = 1000
            for (i in 0 until warmupCount) {
                sourceSensor.newValue(130 + (i % 20))
            }
            deliveredCount = 0

            val latenciesMs = ArrayList<Double>(measuredCount)
            var lastExpectedHr = 0
            for (i in 0 until measuredCount) {
                lastExpectedHr = 140 + (i % 30)
                val t0 = System.nanoTime()
                sourceSensor.newValue(lastExpectedHr)
                val elapsedMs = (lastDeliveryNs - t0) / 1_000_000.0
                latenciesMs.add(elapsedMs.coerceAtLeast(0.0))
            }
            val banalObservedHr = banalComm.getBestSensorData(SensorType.HR)?.value

            val sorted = latenciesMs.sorted()
            val p99Ms = percentile(sorted, 99.0)
            val maxMs = sorted.last()
            val passed = deliveredCount == measuredCount && p99Ms < 25.0 && banalObservedHr == lastExpectedHr

            recordMeasurement(
                NfrMeasurement(
                    reqId = "REQ-NFR-007",
                    tstId = "TST-NFR-007",
                    name = "Sensor Dispatch Latency Budget",
                    metric = "dispatch_p99_ms",
                    measuredValue = p99Ms,
                    unit = "ms",
                    budget = "< 25.0 ms",
                    passed = passed,
                    warmupIterations = warmupCount,
                    measuredIterations = measuredCount,
                    details = String.format(
                        Locale.US,
                        "delivered=%d/%d, banal_hr=%s, p99_ms=%.4f, max_ms=%.4f",
                        deliveredCount,
                        measuredCount,
                        banalObservedHr,
                        p99Ms,
                        maxMs
                    )
                )
            )
            assertEquals("BANALServiceComm must expose latest dispatched HR value", lastExpectedHr, banalObservedHr)
            assertEquals("Zero dropped sensor dispatch events expected", measuredCount, deliveredCount)
            assertTrue("Sensor dispatch p99 ($p99Ms ms) must be < 25.0 ms", p99Ms < 25.0)
        } finally {
            BANALService.setSensorManagerForTesting(null)
            sensorManager.shutDown()
        }
    }

    /**
     * TST-NFR-008: Measured sensor disconnect watchdog failover timeout (<= 3.5 s / 3500 ms).
     */
    @Test
    fun testNfr008_sensorFailoverWatchdog_measuredWithin3500Ms() {
        assertEquals(3500L, BTLERadarDevice.WATCHDOG_TIMEOUT_MS)
        assertEquals(3500L, ANTBikeRadarDevice.WATCHDOG_TIMEOUT_MS)

        val sensorManager = MySensorManager(context)
        val radarDevice = TestBTLERadarDevice(context, sensorManager, 104L, "DC:2C:26:01:02:04")
        try {
            val mockGatt = mockk<BluetoothGatt>(relaxed = true)
            val mockRadarService = mockk<BluetoothGattService>(relaxed = true)
            every { mockGatt.getService(BluetoothConstants.UUID_SERVICE_RADAR) } returns mockRadarService

            radarDevice.connectForTest(mockGatt)
            assertEquals(RadarConnectionState.CONNECTED, RadarStateRepository.getState().connectionState)

            var elapsedVirtualMs = 0L
            val stepMs = 100L
            while (elapsedVirtualMs <= 4000L &&
                RadarStateRepository.getState().connectionState != RadarConnectionState.DISCONNECTED_ERROR
            ) {
                ShadowLooper.idleMainLooper(stepMs, TimeUnit.MILLISECONDS)
                elapsedVirtualMs += stepMs
            }

            val finalState = RadarStateRepository.getState().connectionState
            val passed = finalState == RadarConnectionState.DISCONNECTED_ERROR && elapsedVirtualMs <= 3500L

            recordMeasurement(
                NfrMeasurement(
                    reqId = "REQ-NFR-008",
                    tstId = "TST-NFR-008",
                    name = "Sensor Connection Failover Timeout",
                    metric = "watchdog_failover_ms",
                    measuredValue = elapsedVirtualMs.toDouble(),
                    unit = "ms",
                    budget = "<= 3500 ms",
                    passed = passed,
                    warmupIterations = 0,
                    measuredIterations = 1,
                    details = String.format(
                        Locale.US,
                        "final_state=%s, btle_const=%d, ant_const=%d",
                        finalState,
                        BTLERadarDevice.WATCHDOG_TIMEOUT_MS,
                        ANTBikeRadarDevice.WATCHDOG_TIMEOUT_MS
                    )
                )
            )
            assertEquals(RadarConnectionState.DISCONNECTED_ERROR, finalState)
            assertTrue("Watchdog failover ($elapsedVirtualMs ms) must trigger <= 3500 ms", elapsedVirtualMs <= 3500L)
        } finally {
            radarDevice.shutDown()
            sensorManager.shutDown()
        }
    }

    /**
     * TST-NFR-009: Measured PowerManager PARTIAL_WAKE_LOCK bounding & release timing on real
     * TrackerService lifecycle (onCreate -> onStartCommand(START_NORMAL) -> PAUSED -> TRACKING -> ACTION_STOP -> onDestroy).
     */
    @Test
    fun testNfr009_wakeLockBounding_measuredZeroLeaksOnStop() {
        val sensorMap = mutableMapOf<SensorType, SensorData<*>>()
        val service = TestableTrackerService()
        val attachMethod = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply {
            isAccessible = true
        }
        attachMethod.invoke(service, context)
        // Inject mock Application via LoadedApk / field after attachBaseContext
        val mockApp = mockk<TrainingApplication>(relaxed = true)
        try {
            assertFalse(
                "No WakeLock should be held before TrackerService start",
                ShadowPowerManager.getLatestWakeLock()?.isHeld == true
            )

            service.onCreate()
            val heldAfterOnCreate = ShadowPowerManager.getLatestWakeLock()?.isHeld == true

            // Replace external service bindings/repos/scheduler with isolated test collaborators
            val dbExec = Executors.newSingleThreadExecutor()
            TrackerService::class.java.getDeclaredField("mDbExecutor").apply {
                isAccessible = true
                set(service, dbExec)
            }
            TrackerService::class.java.getDeclaredField("mRrFlushExecutor").apply {
                isAccessible = true
                set(service, DirectExecutorService())
            }
            TrackerService::class.java.getDeclaredField("mScheduler").apply {
                isAccessible = true
                set(service, mockk<ScheduledExecutorService>(relaxed = true))
            }
            val mockBanalComm = mockk<BANALServiceComm>(relaxed = true)
            every { mockBanalComm.bSportType } returns BSportType.BIKE
            every { mockBanalComm.sportTypeId } returns 1L
            every { mockBanalComm.databaseIdsOfActiveRemoteDevices } returns emptyList()
            every { mockBanalComm.allSensorData } answers { sensorMap.values.toList() }
            every { mockBanalComm.getBestSensorData(any<SensorType>()) } answers { sensorMap[firstArg()] }
            TrackerService::class.java.getDeclaredField("mBanalService").apply {
                isAccessible = true
                set(service, mockBanalComm)
            }
            TrackerService::class.java.getDeclaredField("mTrainingApplication").apply {
                isAccessible = true
                set(service, mockApp)
            }
            val mockSportRepo = mockk<com.atrainingtracker.trainingtracker.repositories.SportTypesRepository>(relaxed = true)
            every { mockSportRepo.getBSportType(any()) } returns BSportType.BIKE
            every { mockSportRepo.getUIName(any()) } returns "Cycling"
            TrackerService::class.java.getDeclaredField("mSportTypesRepository").apply {
                isAccessible = true
                set(service, mockSportRepo)
            }
            TrackerService::class.java.getDeclaredField("mKnownLocationsRepository").apply {
                isAccessible = true
                set(service, mockk<com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository>(relaxed = true))
            }

            TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
            val startIntent = Intent(context, TrackerService::class.java).apply {
                putExtra(TrackerService.START_TYPE, TrackerService.StartType.START_NORMAL.name)
            }
            service.onStartCommand(startIntent, 0, 1)
            val initFuture = TrackerService::class.java.getDeclaredField("mTableInitializationFuture").apply {
                isAccessible = true
            }.get(service) as? java.util.concurrent.CompletableFuture<*>
            initFuture?.get(10, TimeUnit.SECONDS)
            dbExec.submit {}.get(10, TimeUnit.SECONDS)
            val heldDuringTracking = ShadowPowerManager.getLatestWakeLock()?.isHeld == true

            // Verify PAUSED retains WakeLock so 1-Hz TIME_TOTAL & auto-resume continue while screen off (REQ-TRK-018)
            TrainingApplication.setTrackingModeForTesting(TrackingMode.PAUSED)
            val trackerRunnable = TrackerService::class.java.getDeclaredField("tracker").apply {
                isAccessible = true
            }.get(service) as Runnable
            trackerRunnable.run()
            dbExec.submit {}.get(10, TimeUnit.SECONDS)
            val heldDuringPaused = ShadowPowerManager.getLatestWakeLock()?.isHeld == true

            TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
            val stopIntent = Intent(context, TrackerService::class.java).apply {
                action = TrackerService.ACTION_STOP
            }
            service.onStartCommand(stopIntent, 0, 2)
            dbExec.submit {}.get(10, TimeUnit.SECONDS)

            // Main-looper teardown executes releaseWakeLock() synchronously upon finalization completion
            val t0 = System.nanoTime()
            ShadowLooper.idleMainLooper()
            val releaseLatencyMs = (System.nanoTime() - t0) / 1_000_000.0
            service.finalizationFuture.get(10, TimeUnit.SECONDS)

            val wakeLockField = TrackerService::class.java.getDeclaredField("wakeLock").apply { isAccessible = true }
            val clearedRef = wakeLockField.get(service) == null
            val releasedAfterStop = ShadowPowerManager.getLatestWakeLock()?.isHeld == false
            val passed = heldAfterOnCreate && heldDuringTracking && heldDuringPaused &&
                releasedAfterStop && clearedRef && service.stopped

            recordMeasurement(
                NfrMeasurement(
                    reqId = "REQ-NFR-009",
                    tstId = "TST-NFR-009",
                    name = "Tracking WakeLock Bounding",
                    metric = "wakelock_release_latency_ms",
                    measuredValue = releaseLatencyMs,
                    unit = "ms",
                    budget = "0 leaks (immediate release)",
                    passed = passed,
                    warmupIterations = 0,
                    measuredIterations = 1,
                    details = String.format(
                        Locale.US,
                        "held_on_create=%s, held_tracking=%s, held_paused=%s, released_after_stop=%s, ref_cleared=%s, stopped=%s",
                        heldAfterOnCreate,
                        heldDuringTracking,
                        heldDuringPaused,
                        releasedAfterStop,
                        clearedRef,
                        service.stopped
                    )
                )
            )
            assertTrue("WakeLock must be acquired on TrackerService onCreate", heldAfterOnCreate)
            assertTrue("WakeLock must be held during active tracking", heldDuringTracking)
            assertTrue("WakeLock must remain held during PAUSED for auto-resume monitoring", heldDuringPaused)
            assertTrue("WakeLock must be released on ACTION_STOP", releasedAfterStop)
            assertTrue("WakeLock reference must be cleared", clearedRef)
            assertTrue("TrackerService must call stopSelf after ACTION_STOP finalization", service.stopped)
        } finally {
            destroyTrackerServiceAndDrain(service)
        }
    }

    /**
     * TST-NFR-010: Measured SQLite WAL 1-Hz sample write latency (< 30 ms) strictly off main thread
     * via TrackerService's mDbExecutor and WorkoutRepository.recordLiveSample().
     */
    @Test
    fun testNfr010_databaseSampleWriteLatency_measuredOffMainThreadBelow30Ms() {
        WorkoutSummariesDatabaseManager.getInstance(context).database
            .rawQuery("PRAGMA journal_mode=WAL", null).use { it.moveToFirst() }
        WorkoutSamplesDatabaseManager.getInstance(context).database
            .rawQuery("PRAGMA journal_mode=WAL", null).use { it.moveToFirst() }

        val sensorMap = mutableMapOf<SensorType, SensorData<*>>()
        val service = buildTestableTrackerService(sensorMap)
        try {
            val startIntent = Intent(context, TrackerService::class.java).apply {
                putExtra(TrackerService.START_TYPE, TrackerService.StartType.START_NORMAL.name)
            }
            service.onStartCommand(startIntent, 0, 1)

            val initFuture = TrackerService::class.java.getDeclaredField("mTableInitializationFuture").apply {
                isAccessible = true
            }.get(service) as? java.util.concurrent.CompletableFuture<*>
            initFuture?.get(10, TimeUnit.SECONDS)

            val dbExecutor = TrackerService::class.java.getDeclaredField("mDbExecutor").apply {
                isAccessible = true
            }.get(service) as java.util.concurrent.ExecutorService
            val baseFileName = TrackerService::class.java.getDeclaredField("mBaseFileName").apply {
                isAccessible = true
            }.get(service) as String
            val workoutId = TrackerService::class.java.getDeclaredField("mWorkoutID").apply {
                isAccessible = true
            }.getLong(service)
            val workoutRepo = WorkoutRepository.getInstance(context)

            val warmupWrites = 100
            val measuredWrites = 1000
            var ranOffMainThread = true
            var allInserted = true
            val writeLatenciesMs = ArrayList<Double>(measuredWrites)
            val sensorName2Type = mapOf(
                SensorType.TIME_TOTAL.name to SensorType.TIME_TOTAL.sensorValueType,
                SensorType.TIME_ACTIVE.name to SensorType.TIME_ACTIVE.sensorValueType,
                SensorType.DISTANCE_m.name to SensorType.DISTANCE_m.sensorValueType,
                SensorType.SPEED_mps.name to SensorType.SPEED_mps.sensorValueType,
                SensorType.HR.name to SensorType.HR.sensorValueType,
                SensorType.POWER.name to SensorType.POWER.sensorValueType,
                SensorType.CADENCE.name to SensorType.CADENCE.sensorValueType,
                SensorType.ALTITUDE.name to SensorType.ALTITUDE.sensorValueType,
                SensorType.LATITUDE.name to SensorType.LATITUDE.sensorValueType,
                SensorType.LONGITUDE.name to SensorType.LONGITUDE.sensorValueType
            )

            dbExecutor.submit {
                for (i in 1..(warmupWrites + measuredWrites)) {
                    if (Looper.getMainLooper().isCurrentThread) {
                        ranOffMainThread = false
                    }
                    val samplingValues = ContentValues().apply {
                        put(SensorType.TIME_TOTAL.name, i)
                        put(SensorType.TIME_ACTIVE.name, i)
                        put(SensorType.DISTANCE_m.name, i * 8.5)
                        put(SensorType.SPEED_mps.name, 8.5)
                        put(SensorType.HR.name, 145)
                        put(SensorType.POWER.name, 225)
                        put(SensorType.CADENCE.name, 88)
                        put(SensorType.ALTITUDE.name, 520.5)
                        put(SensorType.LATITUDE.name, 48.137 + i * 0.00005)
                        put(SensorType.LONGITUDE.name, 11.575 + i * 0.00005)
                    }
                    val summaryValues = ContentValues().apply {
                        put(WorkoutSummaries.TIME_TOTAL_s, i)
                        put(WorkoutSummaries.TIME_ACTIVE_s, i)
                        put(WorkoutSummaries.DISTANCE_TOTAL_m, i * 8.5)
                    }
                    val t0 = System.nanoTime()
                    val inserted = workoutRepo.recordLiveSample(
                        baseFileName,
                        workoutId,
                        samplingValues,
                        summaryValues,
                        emptyList(),
                        null,
                        sensorName2Type,
                        null
                    )
                    val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
                    if (!inserted) allInserted = false
                    if (i > warmupWrites) {
                        writeLatenciesMs.add(elapsedMs)
                    }
                }
            }.get(30, TimeUnit.SECONDS)

            val sorted = writeLatenciesMs.sorted()
            val p95Ms = percentile(sorted, 95.0)
            val p99Ms = percentile(sorted, 99.0)
            val maxMs = sorted.last()
            val passed = ranOffMainThread && allInserted && p99Ms < 30.0

            recordMeasurement(
                NfrMeasurement(
                    reqId = "REQ-NFR-010",
                    tstId = "TST-NFR-010",
                    name = "Database Sample Write Latency Budget",
                    metric = "db_write_p99_ms",
                    measuredValue = p99Ms,
                    unit = "ms",
                    budget = "< 30.0 ms",
                    passed = passed,
                    warmupIterations = warmupWrites,
                    measuredIterations = measuredWrites,
                    details = String.format(
                        Locale.US,
                        "off_main_thread=%s, all_inserted=%s, p95_ms=%.3f, p99_ms=%.3f, max_ms=%.3f",
                        ranOffMainThread,
                        allInserted,
                        p95Ms,
                        p99Ms,
                        maxMs
                    )
                )
            )
            assertTrue("SQLite sample writes must execute off main thread", ranOffMainThread)
            assertTrue("All WorkoutRepository.recordLiveSample calls must succeed", allInserted)
            assertTrue("SQLite sample write p99 ($p99Ms ms) must be < 30.0 ms", p99Ms < 30.0)
        } finally {
            destroyTrackerServiceAndDrain(service)
        }
    }

    /**
     * TST-NFR-011: Measured SQLite storage growth rate over 3,600 consecutive 1-Hz rows (<= 360 KB/h).
     */
    @Test
    fun testNfr011_telemetryStorageGrowthRate_measuredBelow360KbPerHour() {
        val samplesMgr = WorkoutSamplesDatabaseManager.getInstance(context)
        val fileBaseName = "2026-11-17-090000"
        val sensors = listOf(
            SensorType.TIME_TOTAL,
            SensorType.TIME_ACTIVE,
            SensorType.DISTANCE_m,
            SensorType.SPEED_mps,
            SensorType.HR,
            SensorType.POWER,
            SensorType.CADENCE,
            SensorType.ALTITUDE,
            SensorType.LATITUDE,
            SensorType.LONGITUDE
        )
        samplesMgr.createNewTable(fileBaseName, sensors)
        val samplesTable = WorkoutSamplesDatabaseManager.getTableName(fileBaseName)
        val db = samplesMgr.database
        db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        val dbFile = context.getDatabasePath("WorkoutSamples.db")
        val sizeBeforeBytes = dbFile.length()

        val rowCount = 3600 // 1 hour @ 1 Hz
        db.beginTransaction()
        try {
            val cv = ContentValues()
            for (sec in 1..rowCount) {
                cv.clear()
                cv.put(SensorType.TIME_TOTAL.name, sec)
                cv.put(SensorType.TIME_ACTIVE.name, sec)
                cv.put(SensorType.DISTANCE_m.name, sec * 8.0)
                cv.put(SensorType.SPEED_mps.name, 8.0)
                cv.put(SensorType.HR.name, 140 + (sec % 20))
                cv.put(SensorType.POWER.name, 210 + (sec % 40))
                cv.put(SensorType.CADENCE.name, 85 + (sec % 10))
                cv.put(SensorType.ALTITUDE.name, 520.0 + (sec % 50) * 0.1)
                cv.put(SensorType.LATITUDE.name, 48.137 + sec * 0.00005)
                cv.put(SensorType.LONGITUDE.name, 11.575 + sec * 0.00005)
                db.insertOrThrow(samplesTable, null, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }

        val sizeAfterBytes = dbFile.length()
        val deltaBytes = (sizeAfterBytes - sizeBeforeBytes).coerceAtLeast(0L)
        val growthKbPerHour = deltaBytes / 1024.0
        val bytesPerSample = deltaBytes.toDouble() / rowCount
        val passed = growthKbPerHour <= 360.0

        recordMeasurement(
            NfrMeasurement(
                reqId = "REQ-NFR-011",
                tstId = "TST-NFR-011",
                name = "Telemetry Storage Growth Rate Ceiling",
                metric = "storage_growth_kb_per_h",
                measuredValue = growthKbPerHour,
                unit = "KB/h",
                budget = "<= 360.0 KB/h",
                passed = passed,
                warmupIterations = 0,
                measuredIterations = rowCount,
                details = String.format(
                    Locale.US,
                    "delta_bytes=%d, bytes_per_sample=%.2f, before=%d, after=%d",
                    deltaBytes,
                    bytesPerSample,
                    sizeBeforeBytes,
                    sizeAfterBytes
                )
            )
        )
        assertTrue("SQLite storage growth ($growthKbPerHour KB/h) must be <= 360.0 KB/h", passed)
    }

    /**
     * TST-NFR-013: Measured live map polyline geometry update latency (< 30 ms) on 10,000-point track.
     */
    @Test
    fun testNfr013_mapPolylineUpdateLatency_measuredBelow30Ms() {
        val decimator = IncrementalTrackDecimator()
        val fullTrack = ArrayList<LatLng>(10_000)
        for (i in 0 until 10_000) {
            fullTrack.add(
                LatLng(
                    48.1000 + i * 0.00008 + sin(i * 0.07) * 0.0002,
                    11.5000 + i * 0.00008 + cos(i * 0.05) * 0.0002
                )
            )
        }

        // Warmup
        for (w in 1..10) {
            decimator.reset()
            decimator.decimate(fullTrack.subList(0, 9900))
        }

        val measuredAppends = 100
        val updateDurationsMs = ArrayList<Double>(measuredAppends)
        decimator.reset()
        decimator.decimate(fullTrack.subList(0, 9900))
        for (endIdx in 9901..10_000) {
            val slice = fullTrack.subList(0, endIdx)
            val t0 = System.nanoTime()
            val decimated = decimator.decimate(slice)
            updateDurationsMs.add((System.nanoTime() - t0) / 1_000_000.0)
            assertTrue(decimated.isNotEmpty())
        }

        val sorted = updateDurationsMs.sorted()
        val p95Ms = percentile(sorted, 95.0)
        val maxMs = sorted.last()
        val passed = p95Ms < 30.0 && maxMs < 30.0

        recordMeasurement(
            NfrMeasurement(
                reqId = "REQ-NFR-013",
                tstId = "TST-NFR-013",
                name = "Map Polyline Update Latency Budget",
                metric = "polyline_update_p95_ms",
                measuredValue = p95Ms,
                unit = "ms",
                budget = "< 30.0 ms",
                passed = passed,
                warmupIterations = 10,
                measuredIterations = measuredAppends,
                details = String.format(
                    Locale.US,
                    "track_points=10000, p95_ms=%.4f, max_ms=%.4f",
                    p95Ms,
                    maxMs
                )
            )
        )
        assertTrue("Polyline update p95 ($p95Ms ms) and max ($maxMs ms) must be < 30.0 ms", passed)
    }

    /**
     * TST-NFR-014: Measured cockpit telemetry StateFlow emission throttling (<= 1.0 Hz) on BANALServiceRepository.
     */
    @Test
    fun testNfr014_cockpitTelemetryThrottling_measuredMaxOneHzOverFiveSeconds() {
        val testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        var scopeRef: CoroutineScope? = null
        try {
            BANALServiceRepository.resetForTesting(null)
            val repo = BANALServiceRepository.getInstance(context)
            val scopeField = BANALServiceRepository::class.java.getDeclaredField("repositoryScope").apply {
                isAccessible = true
            }
            scopeRef = scopeField.get(repo) as CoroutineScope

            var tickCounter = 0
            val mockBinder = mockk<BANALServiceComm>(relaxed = true)
            every { mockBinder.allFilteredSensorData } answers {
                tickCounter++
                listOf(
                    FilteredSensorData(
                        SensorType.HR,
                        140 + tickCounter,
                        "${140 + tickCounter}",
                        "MockHR",
                        FilterType.INSTANTANEOUS,
                        1.0
                    )
                )
            }
            every { mockBinder.availableSensorTypeSet } returns setOf(SensorType.HR)
            every { mockBinder.activeDevicesIncludingSpeedAndLocationDevices } returns emptyList()
            every { mockBinder.databaseIdsOfActiveRemoteDevices } returns emptyList()
            every { mockBinder.getIdsOfNewlyFoundDevices() } returns emptyList()

            val nonEmptyEmissions = mutableListOf<List<FilteredSensorData<*>>>()
            val collectorJob = TestScope(testDispatcher).launch {
                repo.allFilteredSensorData.collect { list ->
                    if (list.isNotEmpty()) {
                        nonEmptyEmissions.add(list)
                    }
                }
            }

            // Connect binder via ServiceConnection on main TestDispatcher
            val connField = BANALServiceRepository::class.java.getDeclaredField("banalServiceConnection").apply {
                isAccessible = true
            }
            val conn = connField.get(repo) as ServiceConnection
            conn.onServiceConnected(ComponentName(context, BANALService::class.java), mockBinder)
            testDispatcher.scheduler.runCurrent()

            // Simulate 4,999 ms elapsed window (5 ticks at t=0, 1000, 2000, 3000, 4000 ms)
            testDispatcher.scheduler.advanceTimeBy(4999L)
            testDispatcher.scheduler.runCurrent()
            collectorJob.cancel()

            val emissionCount = nonEmptyEmissions.size
            val passed = emissionCount in 1..5

            recordMeasurement(
                NfrMeasurement(
                    reqId = "REQ-NFR-014",
                    tstId = "TST-NFR-014",
                    name = "Cockpit Telemetry Emission Rate Throttling",
                    metric = "emissions_in_5s",
                    measuredValue = emissionCount.toDouble(),
                    unit = "emissions",
                    budget = "<= 5 emissions / 5s (<= 1.0 Hz)",
                    passed = passed,
                    warmupIterations = 0,
                    measuredIterations = 5,
                    details = String.format(
                        Locale.US,
                        "emissions=%d, window_ms=5000, poll_delay_ms=1000",
                        emissionCount
                    )
                )
            )
            assertTrue("Expected 1..5 UI emissions in 5.0 s, got $emissionCount", passed)
        } finally {
            scopeRef?.cancel()
            BANALServiceRepository.resetForTesting(null)
        }
    }
}
