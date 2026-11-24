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

package com.atrainingtracker.banalservice.devices.raw

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.location.Location
import android.os.Build
import android.os.SystemClock
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Bounded, crash-safe streaming recorder for smartphone raw sensors (ACC, GYRO, ROTVEC, BARO,
 * STEP, GNSS, GNSS_FUSED, WHEEL, STATE) writing `files/raw_sensor/<fileBaseName>.phone_raw.csv.gz`
 * (REQ-EXP-045.1, REQ-EXP-045.2, ATT-9348; REQ-EXP-047.1, REQ-EXP-047.2, ATT-9604).
 *
 * Mirrors `PolarRawTelemetryRecorder` (`ATT-9343`, `ATT-9344`):
 * - Opt-in (`recordSmartphoneRawTelemetryEnabled == false` by default -> zero files/writers/allocations)
 * - Self-contained [SmartphoneRawSensorCapture] listener capturing IMU/GYRO/ROTVEC/BARO/STEP
 *   regardless of whether `SpeedAndLocationDevice_ATTFused` is paired or toggled post-startup
 * - Unified `CLOCK_BOOTTIME` (`SystemClock.elapsedRealtimeNanos()`) timestamps across all 9 channels
 * - Instance-scoped zero-allocation per-channel GNSS deduplication (`GNSS` vs `GNSS_FUSED`)
 * - Bounded `Channel(capacity = 8192, BufferOverflow.DROP_OLDEST)` with exact `onUndeliveredElement`
 *   dropped-frame accounting
 * - Zero `String.format` per row (single reusable `StringBuilder` in IO drain loop)
 * - Periodic flush (`<= 256` rows / `<= 5 s`) into `GZIPOutputStream(..., syncFlush = true)`
 * - Private `CoroutineScope` cancelled on [stop] / bounded [stopBlocking]
 */
class SmartphoneRawTelemetryRecorder(
    private val targetFile: File,
    private val metadata: PhoneRawMetadata,
    private val resumeExisting: Boolean = false,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val channelCapacity: Int = CHANNEL_CAPACITY,
    private val onBeforeDrainRow: (() -> Unit)? = null,
    val fileBaseName: String = targetFile.name.removeSuffix(SIDECAR_EXTENSION)
) {
    private val tempFile = File(targetFile.parentFile, "${targetFile.name}$TMP_EXTENSION")
    private val droppedFrameCount = AtomicLong(0L)
    private val writtenRowCount = AtomicLong(0L)
    private val isStopped = AtomicBoolean(false)

    // REQ-EXP-047.2 (ATT-9604): Instance-scoped, zero-allocation per-channel GNSS deduplication ring buffers.
    private class LocationDedupRingBuffer {
        private val elapsedNsSlots = LongArray(LOCATION_DEDUP_WINDOW_SIZE)
        private val epochMsSlots = LongArray(LOCATION_DEDUP_WINDOW_SIZE)
        private val hasLocNsSlots = BooleanArray(LOCATION_DEDUP_WINDOW_SIZE)
        private var filledCount = 0
        private var cursor = 0

        fun acceptIfNotDuplicate(elapsedNs: Long, epochMs: Long, hasLocationElapsedNs: Boolean): Boolean {
            for (i in 0 until filledCount) {
                if (hasLocationElapsedNs) {
                    if (hasLocNsSlots[i] && elapsedNsSlots[i] == elapsedNs) return false
                } else {
                    if (!hasLocNsSlots[i] && elapsedNsSlots[i] == elapsedNs && epochMsSlots[i] == epochMs) return false
                }
            }
            elapsedNsSlots[cursor] = elapsedNs
            epochMsSlots[cursor] = epochMs
            hasLocNsSlots[cursor] = hasLocationElapsedNs
            cursor = (cursor + 1) % LOCATION_DEDUP_WINDOW_SIZE
            if (filledCount < LOCATION_DEDUP_WINDOW_SIZE) filledCount++
            return true
        }
    }

    private val locationDedupLock = Any()
    private val gnssDedupBuffer = LocationDedupRingBuffer()
    private val gnssFusedDedupBuffer = LocationDedupRingBuffer()

    internal fun shouldAcceptLocation(
        channel: PhoneRawChannel,
        elapsedNs: Long,
        epochMs: Long,
        hasLocationElapsedNs: Boolean
    ): Boolean {
        val ring = when (channel) {
            PhoneRawChannel.GNSS -> gnssDedupBuffer
            PhoneRawChannel.GNSS_FUSED -> gnssFusedDedupBuffer
            else -> return true
        }
        return synchronized(locationDedupLock) {
            ring.acceptIfNotDuplicate(elapsedNs, epochMs, hasLocationElapsedNs)
        }
    }

    private val channel = Channel<PhoneRawTelemetryRow>(
        capacity = channelCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { droppedFrameCount.incrementAndGet() }
    )

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var writeJob: Job? = null

    /**
     * Starts the single IO drain coroutine. Does not open disk files until the first frame
     * arrives (or existing crash-salvage rows exist on resume).
     */
    fun start() {
        if (writeJob != null || isStopped.get()) return
        writeJob = scope.launch {
            drainLoop()
        }
    }

    private suspend fun drainLoop() {
        var gzipOut: GZIPOutputStream? = null
        var writer: BufferedWriter? = null
        var opened = false
        var rowsSinceFlush = 0
        var lastFlushMs = System.currentTimeMillis()
        val lineBuilder = StringBuilder(128)

        fun openWriterIfNeeded(): BufferedWriter? {
            if (opened) return writer
            opened = true
            return try {
                val parent = targetFile.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                val salvageFile = if (resumeExisting) {
                    PhoneRawSidecarRecovery.prepareSalvageSource(tempFile, targetFile)
                } else {
                    if (tempFile.exists()) tempFile.delete()
                    null
                }
                val gOut = GZIPOutputStream(FileOutputStream(tempFile), true)
                val bw = BufferedWriter(OutputStreamWriter(gOut, StandardCharsets.UTF_8))
                if (salvageFile != null) {
                    val result = PhoneRawSidecarRecovery.salvageIntoWriter(salvageFile, bw, metadata)
                    writtenRowCount.addAndGet(result.salvagedRowCount)
                } else {
                    PhoneRawSidecarRecovery.writeHeader(bw, metadata)
                }
                bw.flush()
                gzipOut = gOut
                writer = bw
                bw
            } catch (_: IOException) {
                null
            }
        }

        try {
            if (resumeExisting && ((tempFile.exists() && tempFile.length() > 0L) ||
                    (targetFile.exists() && targetFile.length() > 0L))
            ) {
                openWriterIfNeeded()
            }

            for (row in channel) {
                onBeforeDrainRow?.invoke()
                val bw = openWriterIfNeeded() ?: continue
                lineBuilder.setLength(0)
                row.appendCsvLine(lineBuilder)
                bw.write(lineBuilder.toString())
                bw.newLine()
                writtenRowCount.incrementAndGet()
                rowsSinceFlush++

                val nowMs = System.currentTimeMillis()
                if (rowsSinceFlush >= FLUSH_INTERVAL_ROWS || (nowMs - lastFlushMs) >= FLUSH_INTERVAL_MS) {
                    bw.flush()
                    rowsSinceFlush = 0
                    lastFlushMs = nowMs
                }
            }
        } catch (_: Throwable) {
            // Best-effort cleanup in finally block
        } finally {
            try {
                writer?.flush()
            } catch (_: IOException) {
            }
            try {
                writer?.close()
            } catch (_: IOException) {
            }
            try {
                gzipOut?.close()
            } catch (_: IOException) {
            }

            if (writtenRowCount.get() > 0L && tempFile.exists() && tempFile.length() > 0L) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            } else {
                if (tempFile.exists()) tempFile.delete()
            }
        }
    }

    /**
     * Non-blocking enqueue from sensor/location callbacks. Never blocks calling thread.
     */
    fun record(row: PhoneRawTelemetryRow) {
        if (isStopped.get()) return
        val result = channel.trySend(row)
        if (!result.isSuccess) {
            droppedFrameCount.incrementAndGet()
        }
    }

    /**
     * Closes the channel, waits for the IO drain coroutine to finish atomic `.tmp` finalization,
     * and cancels the private [CoroutineScope].
     */
    suspend fun stop() {
        if (!isStopped.compareAndSet(false, true)) return
        channel.close()
        try {
            writeJob?.join()
        } finally {
            scope.cancel()
        }
    }

    /**
     * Timeout-bounded synchronous stop helper for Java lifecycle callers (`TrackerService`).
     * Returns `true` if the writer finished cleanly within [timeoutMs], or `false` on timeout.
     */
    @JvmOverloads
    fun stopBlocking(timeoutMs: Long = STOP_TIMEOUT_MS): Boolean {
        val completed = runBlocking {
            withTimeoutOrNull(timeoutMs) {
                stop()
                true
            }
        }
        if (completed == null) {
            isStopped.set(true)
            channel.close()
            scope.cancel()
            return false
        }
        return true
    }

    fun isStopped(): Boolean = isStopped.get()

    fun getDroppedFrameCount(): Long = droppedFrameCount.get()

    fun getWrittenRowCount(): Long = writtenRowCount.get()

    fun getTempFile(): File = tempFile

    fun getTargetFile(): File = targetFile

    companion object {
        const val RAW_SENSOR_DIR = "raw_sensor"
        const val SIDECAR_EXTENSION = ".phone_raw.csv.gz"
        const val TMP_EXTENSION = ".tmp"
        const val CHANNEL_CAPACITY = 8192
        const val FLUSH_INTERVAL_ROWS = 256
        const val FLUSH_INTERVAL_MS = 5_000L
        const val STOP_TIMEOUT_MS = 5_000L
        const val LOCATION_DEDUP_WINDOW_SIZE = 4

        @Volatile
        private var activeRecorder: SmartphoneRawTelemetryRecorder? = null

        @Volatile
        private var activeSensorCapture: SmartphoneRawSensorCapture? = null

        @Volatile
        private var currentSportId: Long = -1L

        @Volatile
        private var currentBSportCode: Int = -1

        @Volatile
        private var currentCarriageCode: Int = PhoneCarriageMode.UNKNOWN.code

        @Volatile
        private var enabledOverrideForTests: Boolean? = null

        @Volatile
        internal var elapsedRealtimeNanosProvider: () -> Long = { defaultElapsedRealtimeNanos() }

        private val sessionLock = Any()

        /**
         * Returns the unified `CLOCK_BOOTTIME` timestamp in nanoseconds (`SystemClock.elapsedRealtimeNanos()`),
         * matching Android `SensorEvent.timestamp` and `Location.elapsedRealtimeNanos` for chronological
         * multi-channel replay (`ATT-9349`). Falls back to `System.nanoTime()` only on plain JVM when
         * `SystemClock` is stubbed/unavailable.
         */
        @JvmStatic
        fun currentElapsedRealtimeNanos(): Long = elapsedRealtimeNanosProvider()

        private fun defaultElapsedRealtimeNanos(): Long {
            return try {
                val ns = SystemClock.elapsedRealtimeNanos()
                if (ns > 0L) ns else System.nanoTime()
            } catch (_: Throwable) {
                System.nanoTime()
            }
        }

        /**
         * Returns the raw sensor storage directory (`files/raw_sensor`), or `null` if [context]
         * lacks a valid `filesDir` (e.g., plain-JVM MockK tests).
         */
        @JvmStatic
        fun getRawSensorDir(context: Context?): File? {
            if (context == null) return null
            return try {
                val filesDir = context.filesDir ?: return null
                File(filesDir, RAW_SENSOR_DIR)
            } catch (_: Throwable) {
                null
            }
        }

        @JvmStatic
        fun getSidecarFile(context: Context?, fileBaseName: String?): File? {
            if (fileBaseName.isNullOrBlank()) return null
            val dir = getRawSensorDir(context) ?: return null
            return File(dir, "$fileBaseName$SIDECAR_EXTENSION")
        }

        @JvmStatic
        fun getTempSidecarFile(context: Context?, fileBaseName: String?): File? {
            val sidecar = getSidecarFile(context, fileBaseName) ?: return null
            return File(sidecar.parentFile, "${sidecar.name}$TMP_EXTENSION")
        }

        /**
         * Non-blocking O(1) check whether smartphone raw telemetry recording is opted in
         * (REQ-EXP-045.1, REQ-SET-131.3). Delegates to [SettingsDataStoreJavaHelper] without
         * blocking the caller thread.
         */
        @JvmStatic
        fun isEnabled(context: Context?): Boolean {
            enabledOverrideForTests?.let { return it }
            if (context == null) return false
            return try {
                SettingsDataStoreJavaHelper.isRecordSmartphoneRawTelemetryEnabled(context)
            } catch (_: Throwable) {
                false
            }
        }

        /**
         * Fast lock-free check whether an active recording session is currently running.
         */
        @JvmStatic
        fun isRecordingActive(): Boolean = activeRecorder != null

        /**
         * Starts a fresh active session if smartphone raw recording is enabled, registers
         * [SmartphoneRawSensorCapture] directly on [android.hardware.SensorManager], and emits
         * the initial `STATE(sportId, bSportCode, carriageCode)` row. No-op when disabled.
         */
        @JvmStatic
        @JvmOverloads
        fun startActiveSession(
            context: Context?,
            fileBaseName: String?,
            sportId: Long,
            bSportCode: Int = -1
        ): SmartphoneRawTelemetryRecorder? {
            if (context == null || fileBaseName.isNullOrBlank() || !isEnabled(context)) return null
            val target = getSidecarFile(context, fileBaseName) ?: return null
            return synchronized(sessionLock) {
                stopActiveSessionLocked()
                val rec = SmartphoneRawTelemetryRecorder(
                    targetFile = target,
                    metadata = buildMetadata(sportId),
                    resumeExisting = false,
                    fileBaseName = fileBaseName
                )
                rec.start()
                activeRecorder = rec
                currentSportId = sportId
                currentBSportCode = bSportCode
                currentCarriageCode = PhoneCarriageMode.UNKNOWN.code
                rec.record(
                    PhoneRawTelemetryRow(
                        currentElapsedRealtimeNanos(),
                        System.currentTimeMillis(),
                        PhoneRawChannel.STATE,
                        sportId.toDouble(),
                        bSportCode.toDouble(),
                        currentCarriageCode.toDouble()
                    )
                )
                startSensorCaptureLocked(context)
                rec
            }
        }

        /**
         * Resumes an active session across service recreation or user resume. Reuses an
         * already-active in-memory recorder for the same [fileBaseName] cleanly without
         * stopping/re-salvaging (mirroring `PolarRawTelemetryRecorder`), or salvages any
         * crash-truncated `.tmp` archive on disk and emits an initial `STATE` row.
         */
        @JvmStatic
        @JvmOverloads
        fun resumeOrStartActiveSession(
            context: Context?,
            fileBaseName: String?,
            sportId: Long,
            bSportCode: Int = -1
        ): SmartphoneRawTelemetryRecorder? {
            if (context == null || fileBaseName.isNullOrBlank() || !isEnabled(context)) return null
            return synchronized(sessionLock) {
                val existing = activeRecorder
                if (existing != null && existing.fileBaseName == fileBaseName && !existing.isStopped()) {
                    updateSportState(sportId, bSportCode)
                    return@synchronized existing
                }
                val target = getSidecarFile(context, fileBaseName) ?: return@synchronized null
                stopActiveSessionLocked()
                val rec = SmartphoneRawTelemetryRecorder(
                    targetFile = target,
                    metadata = buildMetadata(sportId),
                    resumeExisting = true,
                    fileBaseName = fileBaseName
                )
                rec.start()
                activeRecorder = rec
                currentSportId = sportId
                currentBSportCode = bSportCode
                currentCarriageCode = PhoneCarriageMode.UNKNOWN.code
                rec.record(
                    PhoneRawTelemetryRow(
                        currentElapsedRealtimeNanos(),
                        System.currentTimeMillis(),
                        PhoneRawChannel.STATE,
                        sportId.toDouble(),
                        bSportCode.toDouble(),
                        currentCarriageCode.toDouble()
                    )
                )
                startSensorCaptureLocked(context)
                rec
            }
        }

        /**
         * Stops sensor capture and finalizes the active recording session (if any).
         */
        @JvmStatic
        fun stopActiveSession() {
            val toStop = synchronized(sessionLock) {
                stopCaptureOnlyLocked()
                val prev = activeRecorder
                activeRecorder = null
                currentSportId = -1L
                currentBSportCode = -1
                currentCarriageCode = PhoneCarriageMode.UNKNOWN.code
                prev
            }
            toStop?.stopBlocking()
        }

        private fun stopActiveSessionLocked() {
            stopCaptureOnlyLocked()
            val prev = activeRecorder
            activeRecorder = null
            prev?.stopBlocking()
        }

        private fun startSensorCaptureLocked(context: Context) {
            stopCaptureOnlyLocked()
            val appCtx = try {
                context.applicationContext ?: context
            } catch (_: Throwable) {
                context
            }
            val capture = SmartphoneRawSensorCapture(appCtx)
            if (capture.start()) {
                activeSensorCapture = capture
            }
        }

        private fun stopCaptureOnlyLocked() {
            val capture = activeSensorCapture
            activeSensorCapture = null
            capture?.stop()
        }

        @JvmStatic
        internal fun setActiveRecorderForTest(recorder: SmartphoneRawTelemetryRecorder?) {
            synchronized(sessionLock) {
                stopCaptureOnlyLocked()
                activeRecorder = recorder
                if (recorder == null) {
                    currentSportId = -1L
                    currentBSportCode = -1
                    currentCarriageCode = PhoneCarriageMode.UNKNOWN.code
                }
            }
        }

        @JvmStatic
        internal fun setEnabledOverrideForTest(enabled: Boolean?) {
            enabledOverrideForTests = enabled
        }

        // --- Fast non-blocking static tap helpers ---

        /**
         * Dispatches a raw Android [SensorEvent] (ACC, GYRO, ROTVEC/GAME_ROTVEC, BARO, STEP)
         * into the active recorder with zero allocations when recording is inactive.
         */
        @JvmStatic
        fun recordSensorEvent(event: SensorEvent, epochMs: Long) {
            val rec = activeRecorder ?: return
            val sensor = event.sensor ?: return
            val tsNs = if (event.timestamp > 0L) event.timestamp else currentElapsedRealtimeNanos()
            val values = event.values
            when (sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    if (values != null && values.size >= 3) {
                        val ax = values[0].toDouble()
                        val ay = values[1].toDouble()
                        val az = values[2].toDouble()
                        if (ax.isFinite() && ay.isFinite() && az.isFinite()) {
                            rec.record(PhoneRawTelemetryRow(tsNs, epochMs, PhoneRawChannel.ACC, ax, ay, az))
                        }
                    }
                }
                Sensor.TYPE_GYROSCOPE -> {
                    if (values != null && values.size >= 3) {
                        val gx = values[0].toDouble()
                        val gy = values[1].toDouble()
                        val gz = values[2].toDouble()
                        if (gx.isFinite() && gy.isFinite() && gz.isFinite()) {
                            rec.record(PhoneRawTelemetryRow(tsNs, epochMs, PhoneRawChannel.GYRO, gx, gy, gz))
                        }
                    }
                }
                Sensor.TYPE_ROTATION_VECTOR,
                Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                    if (values != null && values.size >= 3) {
                        recordRotationVector(tsNs, epochMs, values)
                    }
                }
                Sensor.TYPE_PRESSURE -> {
                    if (values != null && values.isNotEmpty()) {
                        val hpa = values[0].toDouble()
                        if (hpa.isFinite() && hpa > 0.0) {
                            val altM = 44330.0 * (1.0 - (hpa / 1013.25).pow(0.19029495718363465))
                            rec.record(PhoneRawTelemetryRow(tsNs, epochMs, PhoneRawChannel.BARO, hpa, altM))
                        }
                    }
                }
                Sensor.TYPE_STEP_DETECTOR -> {
                    rec.record(PhoneRawTelemetryRow(tsNs, epochMs, PhoneRawChannel.STEP))
                }
            }
        }

        /**
         * Resolves the canonical GNSS channel for an Android [Location] (`"fused"` -> [PhoneRawChannel.GNSS_FUSED],
         * raw GPS / null / unmocked providers -> [PhoneRawChannel.GNSS]) safely on plain JVM (REQ-EXP-047.1, ATT-9604).
         */
        @JvmStatic
        fun resolveLocationChannel(location: Location): PhoneRawChannel {
            val provider = try {
                location.provider
            } catch (_: Throwable) {
                null
            }
            return if (provider != null && provider.equals("fused", ignoreCase = true)) {
                PhoneRawChannel.GNSS_FUSED
            } else {
                PhoneRawChannel.GNSS
            }
        }

        /**
         * Dispatches a raw GNSS [Location] fix into the active recorder with per-channel deduplication
         * (`GNSS` vs `GNSS_FUSED`, REQ-EXP-047.1/REQ-EXP-047.2) and strict `.isFinite()` guards on
         * latitude, longitude, and all optional metrics. Zero allocations when inactive.
         */
        @JvmStatic
        @JvmOverloads
        fun recordLocation(
            location: Location,
            epochMs: Long,
            channel: PhoneRawChannel = resolveLocationChannel(location)
        ) {
            val rec = activeRecorder ?: return
            val lat = location.latitude
            val lon = location.longitude
            if (!lat.isFinite() || !lon.isFinite()) return

            val effectiveChannel = when (channel) {
                PhoneRawChannel.GNSS, PhoneRawChannel.GNSS_FUSED -> channel
                else -> resolveLocationChannel(location)
            }
            val locElapsedNs = try {
                location.elapsedRealtimeNanos
            } catch (_: Throwable) {
                0L
            }
            val hasLocNs = locElapsedNs > 0L
            val elapsedNs = if (hasLocNs) locElapsedNs else currentElapsedRealtimeNanos()
            if (!rec.shouldAcceptLocation(effectiveChannel, elapsedNs, epochMs, hasLocationElapsedNs = hasLocNs)) {
                return
            }

            val altM = if (location.hasAltitude() && location.altitude.isFinite()) location.altitude else 0.0
            val speedMps = if (location.hasSpeed() && location.speed.isFinite()) location.speed.toDouble() else 0.0
            val bearingDeg = if (location.hasBearing() && location.bearing.isFinite()) location.bearing.toDouble() else 0.0
            val accM = if (location.hasAccuracy() && location.accuracy.isFinite()) location.accuracy.toDouble() else 0.0
            rec.record(
                PhoneRawTelemetryRow(
                    elapsedNs, epochMs, effectiveChannel,
                    lat, lon, altM, speedMps, bearingDeg, accM
                )
            )
        }

        @JvmStatic
        fun recordAcc(elapsedNs: Long, epochMs: Long, ax: Double, ay: Double, az: Double) {
            val rec = activeRecorder ?: return
            if (!ax.isFinite() || !ay.isFinite() || !az.isFinite()) return
            rec.record(PhoneRawTelemetryRow(elapsedNs, epochMs, PhoneRawChannel.ACC, ax, ay, az))
        }

        @JvmStatic
        fun recordGyro(elapsedNs: Long, epochMs: Long, gx: Double, gy: Double, gz: Double) {
            val rec = activeRecorder ?: return
            if (!gx.isFinite() || !gy.isFinite() || !gz.isFinite()) return
            rec.record(PhoneRawTelemetryRow(elapsedNs, epochMs, PhoneRawChannel.GYRO, gx, gy, gz))
        }

        /**
         * Records a rotation vector (`values.size >= 3`). When `values.size == 3`, derives
         * `qw = sqrt(max(0.0, 1.0 - qx^2 - qy^2 - qz^2))` per Android `SensorEvent` contract
         * (REQ-EXP-045.1).
         */
        @JvmStatic
        fun recordRotationVector(elapsedNs: Long, epochMs: Long, values: FloatArray) {
            val rec = activeRecorder ?: return
            if (values.size < 3) return
            val qx = values[0].toDouble()
            val qy = values[1].toDouble()
            val qz = values[2].toDouble()
            if (!qx.isFinite() || !qy.isFinite() || !qz.isFinite()) return
            val qw = if (values.size >= 4) {
                val rawQw = values[3].toDouble()
                if (rawQw.isFinite()) rawQw else sqrt(max(0.0, 1.0 - qx * qx - qy * qy - qz * qz))
            } else {
                sqrt(max(0.0, 1.0 - qx * qx - qy * qy - qz * qz))
            }
            rec.record(PhoneRawTelemetryRow(elapsedNs, epochMs, PhoneRawChannel.ROTVEC, qx, qy, qz, qw))
        }

        @JvmStatic
        fun recordBarometer(elapsedNs: Long, epochMs: Long, pressureHpa: Double, isaAltM: Double) {
            val rec = activeRecorder ?: return
            if (!pressureHpa.isFinite() || !isaAltM.isFinite()) return
            rec.record(PhoneRawTelemetryRow(elapsedNs, epochMs, PhoneRawChannel.BARO, pressureHpa, isaAltM))
        }

        @JvmStatic
        fun recordStep(elapsedNs: Long, epochMs: Long) {
            val rec = activeRecorder ?: return
            rec.record(PhoneRawTelemetryRow(elapsedNs, epochMs, PhoneRawChannel.STEP))
        }

        @JvmStatic
        @JvmOverloads
        fun recordGnss(
            elapsedNs: Long,
            epochMs: Long,
            latDeg: Double,
            lonDeg: Double,
            altM: Double,
            speedMps: Double,
            bearingDeg: Double,
            accuracyM: Double,
            channel: PhoneRawChannel = PhoneRawChannel.GNSS
        ) {
            val rec = activeRecorder ?: return
            if (!latDeg.isFinite() || !lonDeg.isFinite()) return
            val effectiveChannel = if (channel == PhoneRawChannel.GNSS_FUSED) {
                PhoneRawChannel.GNSS_FUSED
            } else {
                PhoneRawChannel.GNSS
            }
            if (!rec.shouldAcceptLocation(effectiveChannel, elapsedNs, epochMs, hasLocationElapsedNs = elapsedNs > 0L)) {
                return
            }
            rec.record(
                PhoneRawTelemetryRow(
                    elapsedNs, epochMs, effectiveChannel,
                    latDeg,
                    lonDeg,
                    if (altM.isFinite()) altM else 0.0,
                    if (speedMps.isFinite()) speedMps else 0.0,
                    if (bearingDeg.isFinite()) bearingDeg else 0.0,
                    if (accuracyM.isFinite()) accuracyM else 0.0
                )
            )
        }

        @JvmStatic
        fun recordWheel(elapsedNs: Long, epochMs: Long, speedMps: Double, variance: Double) {
            val rec = activeRecorder ?: return
            if (!speedMps.isFinite() || !variance.isFinite()) return
            rec.record(PhoneRawTelemetryRow(elapsedNs, epochMs, PhoneRawChannel.WHEEL, speedMps, variance))
        }

        /**
         * Updates authoritative workout sport state (`sportId`, `bSportCode`) and emits a
         * canonical `STATE(sportId, bSportCode, carriageCode)` row whenever either changes.
         */
        @JvmStatic
        @JvmOverloads
        fun updateSportState(sportId: Long, bSportCode: Int, epochMs: Long = System.currentTimeMillis()) {
            val rec = activeRecorder ?: return
            if (sportId != currentSportId || bSportCode != currentBSportCode) {
                currentSportId = sportId
                currentBSportCode = bSportCode
                rec.record(
                    PhoneRawTelemetryRow(
                        currentElapsedRealtimeNanos(),
                        epochMs,
                        PhoneRawChannel.STATE,
                        sportId.toDouble(),
                        bSportCode.toDouble(),
                        currentCarriageCode.toDouble()
                    )
                )
            }
        }

        /**
         * Updates authoritative phone carriage mode (`PhoneCarriageMode.code`) and emits a
         * canonical `STATE(sportId, bSportCode, carriageCode)` row whenever it changes.
         */
        @JvmStatic
        @JvmOverloads
        fun updateCarriageMode(carriageCode: Int, epochMs: Long = System.currentTimeMillis()) {
            val rec = activeRecorder ?: return
            if (carriageCode != currentCarriageCode) {
                currentCarriageCode = carriageCode
                rec.record(
                    PhoneRawTelemetryRow(
                        currentElapsedRealtimeNanos(),
                        epochMs,
                        PhoneRawChannel.STATE,
                        currentSportId.toDouble(),
                        currentBSportCode.toDouble(),
                        carriageCode.toDouble()
                    )
                )
            }
        }

        @JvmStatic
        fun recordState(elapsedNs: Long, epochMs: Long, sportId: Long, bSportCode: Int, carriageCode: Int) {
            val rec = activeRecorder ?: return
            rec.record(
                PhoneRawTelemetryRow(
                    elapsedNs, epochMs, PhoneRawChannel.STATE,
                    sportId.toDouble(), bSportCode.toDouble(), carriageCode.toDouble()
                )
            )
        }

        private fun buildMetadata(sportId: Long): PhoneRawMetadata {
            val appVer = try {
                BuildConfig.VERSION_NAME
            } catch (_: Throwable) {
                "unknown"
            }
            val model = try {
                val m = Build.MODEL
                if (m.isNullOrBlank()) "unknown" else m
            } catch (_: Throwable) {
                "unknown"
            }
            return PhoneRawMetadata(
                schemaVersion = PhoneRawMetadata.CURRENT_SCHEMA_VERSION,
                appVersion = appVer,
                deviceModel = model,
                sportId = sportId,
                sessionStartTimeMs = System.currentTimeMillis()
            )
        }
    }
}
