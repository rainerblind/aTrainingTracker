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

@file:Suppress("PackageNaming")

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.devices.RrBeatListener
import com.atrainingtracker.trainingtracker.settings.RawSensorRetentionPolicy
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarGyroData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

/**
 * Non-blocking, buffered streaming GZIP sidecar writer for high-frequency Polar PMD
 * and offline sync telemetry (REQ-SET-117.1, REQ-SET-117.2, ATT-8019).
 *
 * Emits raw sensor frames to `files/raw_sensor/<fileBaseName>.polar_raw.csv.gz` through a
 * bounded [Channel] of capacity 8192 drained on [Dispatchers.IO]. BLE GATT event loops
 * are strictly never blocked. Discards empty or aborted recording sessions leaving zero orphan files.
 */
class PolarRawTelemetryRecorder(
    private val context: Context,
    val fileBaseName: String,
    val sessionStartTimeMs: Long,
    var metadata: PolarRawMetadata = PolarRawMetadata()
) : PolarOnlineStreamingBridge.ImuFrameListener, RrBeatListener {

    private val isRunning = AtomicBoolean(false)
    private val isClosed = AtomicBoolean(false)
    private val rowCount = AtomicLong(0)

    private val firstAccNs = AtomicLong(-1L)
    private val accBaseElapsedMs = AtomicLong(0L)

    private val firstGyroNs = AtomicLong(-1L)
    private val gyroBaseElapsedMs = AtomicLong(0L)

    private val firstHrNs = AtomicLong(-1L)
    private val hrBaseElapsedMs = AtomicLong(0L)

    private val firstRrNs = AtomicLong(-1L)
    private val rrBaseElapsedMs = AtomicLong(0L)

    private val channel = Channel<RawTelemetryRow>(capacity = CHANNEL_CAPACITY)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var writeJob: Job? = null

    val tempFile: File by lazy {
        File(getRawSensorDir(context), "$fileBaseName$SIDECAR_EXTENSION$TMP_EXTENSION")
    }

    val targetFile: File by lazy {
        File(getRawSensorDir(context), "$fileBaseName$SIDECAR_EXTENSION")
    }

    /**
     * Starts background writer coroutine and prepares output stream.
     */
    @Synchronized
    fun start() {
        if (isRunning.getAndSet(true)) return

        writeJob = scope.launch {
            var writer: BufferedWriter? = null
            try {
                val dir = getRawSensorDir(context)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                if (tempFile.exists()) {
                    tempFile.delete()
                }

                val fos = FileOutputStream(tempFile)
                val gzipOut = GZIPOutputStream(fos)
                writer = BufferedWriter(OutputStreamWriter(gzipOut, StandardCharsets.UTF_8))

                // Write self-describing metadata header
                writer.write("# metadata: ${metadata.toJsonString()}")
                writer.newLine()
                // Write CSV column header
                writer.write(RawTelemetryRow.CSV_HEADER)
                writer.newLine()

                // Drain channel loop
                for (row in channel) {
                    writer.write(row.toCsvLine())
                    writer.newLine()
                    rowCount.incrementAndGet()
                }
            } catch (e: IOException) {
                Log.w(TAG, "IOException during raw telemetry streaming for $fileBaseName: ${e.message}")
            } catch (e: CancellationException) {
                Log.d(TAG, "Streaming coroutine cancelled for $fileBaseName")
            } finally {
                try {
                    writer?.flush()
                    writer?.close()
                } catch (e: IOException) {
                    Log.w(TAG, "Error closing GZIP writer for $fileBaseName: ${e.message}")
                }
                finalizeFile()
            }
        }
    }

    private fun finalizeFile() {
        val count = rowCount.get()
        if (count == 0L) {
            if (tempFile.exists()) {
                val deleted = tempFile.delete()
                Log.d(TAG, "Discarded empty raw telemetry session file $tempFile (deleted=$deleted)")
            }
        } else {
            if (tempFile.exists()) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                val renamed = tempFile.renameTo(targetFile)
                Log.i(TAG, "Finalized raw telemetry sidecar $targetFile with $count frames (renamed=$renamed)")
            }
        }
    }

    /**
     * Non-blocking record call. Drops frame if channel is saturated rather than blocking GATT callbacks.
     */
    fun recordRow(row: RawTelemetryRow) {
        if (!isRunning.get() || isClosed.get()) return
        channel.trySend(row)
    }

    fun recordAcc(sensorTimestampNs: Long, axMg: Int, ayMg: Int, azMg: Int) {
        val elapsed = resolveHwElapsedMs(sensorTimestampNs, firstAccNs, accBaseElapsedMs)
        recordRow(
            RawTelemetryRow(
                sensorTimestampNs = sensorTimestampNs,
                elapsedMs = elapsed,
                channel = RawTelemetryChannel.ACC,
                axMg = axMg,
                ayMg = ayMg,
                azMg = azMg
            )
        )
    }

    fun recordGyro(sensorTimestampNs: Long, gxMdps: Float, gyMdps: Float, gzMdps: Float) {
        val elapsed = resolveHwElapsedMs(sensorTimestampNs, firstGyroNs, gyroBaseElapsedMs)
        recordRow(
            RawTelemetryRow(
                sensorTimestampNs = sensorTimestampNs,
                elapsedMs = elapsed,
                channel = RawTelemetryChannel.GYRO,
                gxMdps = gxMdps,
                gyMdps = gyMdps,
                gzMdps = gzMdps
            )
        )
    }

    fun recordHr(sensorTimestampNs: Long, hrBpm: Int) {
        val elapsed = resolveWallClockOrMonotonicElapsedMs(sensorTimestampNs, firstHrNs, hrBaseElapsedMs)
        recordRow(
            RawTelemetryRow(
                sensorTimestampNs = sensorTimestampNs,
                elapsedMs = elapsed,
                channel = RawTelemetryChannel.HR,
                hrBpm = hrBpm
            )
        )
    }

    fun recordRr(sensorTimestampNs: Long, rrMs: Double) {
        val elapsed = resolveWallClockOrMonotonicElapsedMs(sensorTimestampNs, firstRrNs, rrBaseElapsedMs)
        recordRow(
            RawTelemetryRow(
                sensorTimestampNs = sensorTimestampNs,
                elapsedMs = elapsed,
                channel = RawTelemetryChannel.RR,
                rrMs = rrMs
            )
        )
    }

    private fun resolveWallClockOrMonotonicElapsedMs(
        timestampNs: Long,
        firstNsRef: AtomicLong,
        baseElapsedRef: AtomicLong
    ): Long {
        return if (timestampNs >= EPOCH_THRESHOLD_NS) {
            val eventMs = timestampNs / NS_PER_MS
            maxOf(0L, eventMs - sessionStartTimeMs)
        } else {
            resolveHwElapsedMs(timestampNs, firstNsRef, baseElapsedRef)
        }
    }

    private fun resolveHwElapsedMs(
        hwTimestampNs: Long,
        firstNsRef: AtomicLong,
        baseElapsedRef: AtomicLong
    ): Long {
        if (hwTimestampNs <= 0L) {
            return maxOf(0L, System.currentTimeMillis() - sessionStartTimeMs)
        }
        if (firstNsRef.compareAndSet(-1L, hwTimestampNs)) {
            val initialElapsed = maxOf(0L, System.currentTimeMillis() - sessionStartTimeMs)
            baseElapsedRef.set(initialElapsed)
            return initialElapsed
        }
        val firstNs = firstNsRef.get()
        val baseElapsed = baseElapsedRef.get()
        return if (firstNs > 0L && hwTimestampNs >= firstNs) {
            baseElapsed + ((hwTimestampNs - firstNs) / NS_PER_MS)
        } else {
            maxOf(0L, System.currentTimeMillis() - sessionStartTimeMs)
        }
    }

    // ImuFrameListener
    override fun onAccFrame(polarId: String, data: PolarAccelerometerData) {
        if (metadata.sensorModel.isEmpty()) {
            metadata = metadata.copy(sensorModel = polarId)
        }
        for (sample in data.samples) {
            recordAcc(sample.timeStamp, sample.x, sample.y, sample.z)
        }
    }

    override fun onGyroFrame(polarId: String, data: PolarGyroData) {
        for (sample in data.samples) {
            recordGyro(sample.timeStamp, sample.x, sample.y, sample.z)
        }
    }

    override fun onHrSample(polarId: String, hrBpm: Int, timestampNs: Long) {
        recordHr(timestampNs, hrBpm)
    }

    // RrBeatListener
    override fun onRrBeat(event: RrBeatEvent) {
        val tsNs = event.beatTimestampMs * NS_PER_MS
        recordRr(tsNs, event.rrMs)
    }

    /**
     * Closes the recorder, drains the channel buffer, and flushes output to disk.
     */
    @Synchronized
    fun stop(timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        if (isClosed.getAndSet(true)) return
        channel.close()
        runBlocking {
            withTimeoutOrNull(timeoutMs) {
                writeJob?.join()
            }
        }
        isRunning.set(false)
    }

    fun getRecordedRowCount(): Long = rowCount.get()

    companion object {
        private const val TAG = "PolarRawTelemetryRec"
        const val CHANNEL_CAPACITY = 8192
        const val NS_PER_MS = 1_000_000L
        const val EPOCH_THRESHOLD_NS = 1_000_000_000_000_000_000L
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val MIN_HEART_RATE_BPM = 30
        const val MAX_HEART_RATE_BPM = 250
        const val MS_PER_MINUTE = 60_000.0
        const val RAW_SENSOR_DIR = "raw_sensor"
        const val SIDECAR_EXTENSION = ".polar_raw.csv.gz"
        const val TMP_EXTENSION = ".tmp"

        @Volatile
        private var activeRecorder: PolarRawTelemetryRecorder? = null

        @JvmStatic
        fun getRawSensorDir(context: Context): File {
            val dir = File(context.filesDir, RAW_SENSOR_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

        @JvmStatic
        fun getSidecarFile(context: Context, fileBaseName: String): File {
            return File(getRawSensorDir(context), "$fileBaseName$SIDECAR_EXTENSION")
        }

        @JvmStatic
        fun getActiveRecorder(): PolarRawTelemetryRecorder? = activeRecorder

        @JvmStatic
        fun isSessionActive(): Boolean = activeRecorder != null

        @JvmStatic
        @Synchronized
        fun startActiveSession(
            context: Context,
            fileBaseName: String,
            startTimeMs: Long,
            sportId: Int = 0
        ): PolarRawTelemetryRecorder? {
            val policy = SettingsDataStoreJavaHelper.getRawSensorRetentionPolicy(context)
            if (policy == RawSensorRetentionPolicy.OFF) {
                Log.d(TAG, "Raw telemetry retention is OFF; skipping recorder start")
                return null
            }
            stopActiveSession()

            val metadata = PolarRawMetadata(
                streamMode = "LIVE",
                sportId = sportId
            )
            val recorder = PolarRawTelemetryRecorder(context, fileBaseName, startTimeMs, metadata)
            recorder.start()
            activeRecorder = recorder
            PolarOnlineStreamingBridge.attachActiveRecorder(recorder)
            Log.i(TAG, "Started active PolarRawTelemetryRecorder session for $fileBaseName")
            return recorder
        }

        @JvmStatic
        @Synchronized
        fun stopActiveSession() {
            val recorder = activeRecorder ?: return
            activeRecorder = null
            PolarOnlineStreamingBridge.detachActiveRecorder(recorder)
            try {
                recorder.stop()
                Log.i(TAG, "Stopped active PolarRawTelemetryRecorder session for ${recorder.fileBaseName}")
            } catch (e: IOException) {
                Log.w(TAG, "IOException stopping active recorder: ${e.message}")
            }
        }

        /**
         * Persists an offline exercise sync session directly into a streaming sidecar (REQ-SET-117.1).
         */
        @JvmStatic
        fun recordOfflineExercise(
            context: Context,
            fileBaseName: String,
            sensorModel: String,
            startTimeMs: Long,
            hrSamples: List<Int>
        ): Boolean {
            val policy = SettingsDataStoreJavaHelper.getRawSensorRetentionPolicy(context)
            if (hrSamples.isEmpty() || policy == RawSensorRetentionPolicy.OFF) {
                return false
            }
            return writeOfflineExerciseFile(context, fileBaseName, sensorModel, startTimeMs, hrSamples)
        }

        private fun writeOfflineExerciseFile(
            context: Context,
            fileBaseName: String,
            sensorModel: String,
            startTimeMs: Long,
            hrSamples: List<Int>
        ): Boolean {
            val metadata = PolarRawMetadata(sensorModel = sensorModel, streamMode = "OFFLINE")
            val targetFile = getSidecarFile(context, fileBaseName)
            val tempFile = File(getRawSensorDir(context), "$fileBaseName$SIDECAR_EXTENSION$TMP_EXTENSION")

            return try {
                val fos = FileOutputStream(tempFile)
                val gzipOut = GZIPOutputStream(fos)
                val writer = BufferedWriter(OutputStreamWriter(gzipOut, StandardCharsets.UTF_8))

                writer.write("# metadata: ${metadata.toJsonString()}")
                writer.newLine()
                writer.write(RawTelemetryRow.CSV_HEADER)
                writer.newLine()

                var cumulativeMs = 0L
                for (rr in hrSamples) {
                    cumulativeMs += rr
                    val beatTsMs = startTimeMs + cumulativeMs
                    val bpm = (MS_PER_MINUTE / rr).toInt().coerceIn(MIN_HEART_RATE_BPM, MAX_HEART_RATE_BPM)
                    val row = RawTelemetryRow(
                        sensorTimestampNs = beatTsMs * NS_PER_MS,
                        elapsedMs = cumulativeMs,
                        channel = RawTelemetryChannel.RR,
                        hrBpm = bpm,
                        rrMs = rr.toDouble()
                    )
                    writer.write(row.toCsvLine())
                    writer.newLine()
                }

                writer.flush()
                writer.close()
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            } catch (e: IOException) {
                Log.w(TAG, "Failed to record offline exercise for $fileBaseName: ${e.message}")
                if (tempFile.exists()) tempFile.delete()
                false
            }
        }
    }
}
