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

package com.atrainingtracker.trainingtracker.tracker

import android.content.Context
import android.os.Debug
import android.util.Log
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Headless, [BuildConfig.DEBUG]-only heap watchdog hooked into [TrackerService]
 * (`REQ-STB-022`, `ATT-9352`).
 *
 * - In release builds (`isDebug == false`), all operations are immediate O(1) no-ops with
 *   zero thread allocation and zero file I/O.
 * - In debug builds, appends periodic 30-second Java/native heap telemetry to
 *   `files/debug_heap/<fileBaseName>.heap.csv` (retaining at most 20 sessions) and triggers
 *   at most one automatic `.hprof` dump per workout session under `files/debug_hprof/`
 *   (retaining at most 2 dumps) when `javaUsed / javaMax >= 0.70` across 2 consecutive checks
 *   and at least 1 GB internal storage is available.
 * - Executes all I/O, pruning, and `Debug.dumpHprofData()` off the UI, `mScheduler`, and
 *   `mDbExecutor` threads inside `try/catch (Throwable)` guards.
 */
class DebugHeapWatchdog(
    private val filesDirProvider: () -> File?,
    private val isDebug: Boolean = BuildConfig.DEBUG,
    private val samplingIntervalMs: Long = DEFAULT_SAMPLING_INTERVAL_MS,
    private val hprofHeapRatio: Double = DEFAULT_HPROF_HEAP_RATIO,
    private val consecutiveHighWaterRequired: Int = DEFAULT_CONSECUTIVE_HIGH_WATER_REQUIRED,
    private val minFreeSpaceBytes: Long = DEFAULT_MIN_FREE_SPACE_BYTES,
    private val maxHeapCsvFiles: Int = DEFAULT_MAX_HEAP_CSV_FILES,
    private val maxHprofFiles: Int = DEFAULT_MAX_HPROF_FILES,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val memorySnapshotProvider: () -> HeapSnapshot = { captureRuntimeHeapSnapshot() },
    private val diskSpaceProvider: (File) -> Long = { dir -> dir.usableSpace },
    private val hprofDumper: (String) -> Unit = { path -> Debug.dumpHprofData(path) },
    private val executorOverride: Executor? = null
) {

    /**
     * Convenience constructor for production [TrackerService] wiring (`REQ-STB-022.1`).
     */
    constructor(context: Context?) : this(
        filesDirProvider = { resolveSafeFilesDir(context) }
    )

    /**
     * Point-in-time Java & native heap measurements in bytes.
     */
    data class HeapSnapshot(
        val javaUsedBytes: Long,
        val javaMaxBytes: Long,
        val nativeAllocatedBytes: Long
    )

    private val lock = Any()
    private var ownedExecutor: ExecutorService? = null
    private var activeFileBaseName: String? = null
    private var sessionStartWallMs: Long = 0L
    private var lastSampleWallMs: Long = Long.MIN_VALUE
    private var consecutiveHighWaterCount: Int = 0
    private var hasDumpedHprofThisSession: Boolean = false
    private var sessionGeneration: Long = 0L

    /**
     * Binds or resets watchdog state for a newly started workout session (`START_NORMAL`).
     */
    fun startSession(fileBaseName: String?) {
        if (!isDebug) return
        try {
            val safeName = sanitizeFileBaseName(fileBaseName) ?: return
            val nowMs = clock()
            synchronized(lock) {
                sessionGeneration++
                activeFileBaseName = safeName
                sessionStartWallMs = nowMs
                lastSampleWallMs = Long.MIN_VALUE
                consecutiveHighWaterCount = 0
                hasDumpedHprofThisSession = false
            }
        } catch (t: Throwable) {
            safeLogWarn("Failed to start DebugHeapWatchdog session", t)
        }
    }

    /**
     * Convenience overload for [TrackerService] that resolves active device count safely only
     * when a 30-second sampling interval is due.
     */
    fun onTick(fileBaseName: String?, banalService: BANALServiceComm?) {
        onTickWithCountProvider(fileBaseName) { countActiveDevicesSafely(banalService) }
    }

    /**
     * Called on the 1-Hz tracking heartbeat; dispatches background sampling only once per
     * [samplingIntervalMs] (30 s default) and handles deferred `fileBaseName` hydration
     * after service crash-recreation (`RESUME_SERVICE_RECREATION`).
     */
    fun onTick(fileBaseName: String?, activeSensorCount: Int) {
        onTickWithCountProvider(fileBaseName) { activeSensorCount }
    }

    private inline fun onTickWithCountProvider(
        fileBaseName: String?,
        sensorCountProvider: () -> Int
    ) {
        if (!isDebug) return
        try {
            val safeName = sanitizeFileBaseName(fileBaseName) ?: return
            val nowMs = clock()
            val capturedGeneration: Long
            val elapsedSec: Long
            val executorToUse: Executor

            synchronized(lock) {
                if (activeFileBaseName != safeName) {
                    sessionGeneration++
                    activeFileBaseName = safeName
                    sessionStartWallMs = nowMs
                    lastSampleWallMs = Long.MIN_VALUE
                    consecutiveHighWaterCount = 0
                    hasDumpedHprofThisSession = false
                }
                if (lastSampleWallMs != Long.MIN_VALUE && (nowMs - lastSampleWallMs) < samplingIntervalMs) {
                    return
                }
                lastSampleWallMs = nowMs
                elapsedSec = ((nowMs - sessionStartWallMs) / 1000L).coerceAtLeast(0L)
                capturedGeneration = sessionGeneration
                executorToUse = getOrCreateExecutorLocked()
            }

            val sensorCount = sensorCountProvider().coerceAtLeast(0)
            executorToUse.execute {
                recordSampleSafely(capturedGeneration, safeName, nowMs, elapsedSec, sensorCount)
            }
        } catch (t: Throwable) {
            safeLogWarn("Failed in DebugHeapWatchdog.onTick", t)
        }
    }

    /**
     * Clears active workout session tracking when recording stops (`handleActionStop`).
     */
    fun stopSession() {
        if (!isDebug) return
        try {
            synchronized(lock) {
                sessionGeneration++
                activeFileBaseName = null
                lastSampleWallMs = Long.MIN_VALUE
                consecutiveHighWaterCount = 0
                hasDumpedHprofThisSession = false
            }
        } catch (t: Throwable) {
            safeLogWarn("Failed in DebugHeapWatchdog.stopSession", t)
        }
    }

    /**
     * Stops session tracking and shuts down any internally owned background executor (`onDestroy`).
     */
    fun shutdown() {
        if (!isDebug) return
        try {
            val toShutdown: ExecutorService?
            synchronized(lock) {
                sessionGeneration++
                activeFileBaseName = null
                lastSampleWallMs = Long.MIN_VALUE
                consecutiveHighWaterCount = 0
                hasDumpedHprofThisSession = false
                toShutdown = ownedExecutor
                ownedExecutor = null
            }
            toShutdown?.shutdown()
        } catch (t: Throwable) {
            safeLogWarn("Failed in DebugHeapWatchdog.shutdown", t)
        }
    }

    private fun getOrCreateExecutorLocked(): Executor {
        executorOverride?.let { return it }
        ownedExecutor?.let { existing ->
            if (!existing.isShutdown) return existing
        }
        return Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "DebugHeapWatchdog").apply { isDaemon = true }
        }.also { ownedExecutor = it }
    }

    private fun recordSampleSafely(
        capturedGeneration: Long,
        safeName: String,
        wallClockMs: Long,
        elapsedSec: Long,
        activeSensorCount: Int
    ) {
        try {
            val baseDir = filesDirProvider() ?: return
            if (baseDir.path.isEmpty()) return

            val snapshot = memorySnapshotProvider()
            val maxBytes = snapshot.javaMaxBytes.coerceAtLeast(1L)
            val usedBytes = snapshot.javaUsedBytes.coerceAtLeast(0L)
            val nativeBytes = snapshot.nativeAllocatedBytes.coerceAtLeast(0L)
            val ratio = usedBytes.toDouble() / maxBytes.toDouble()

            writeCsvRowAndPrune(baseDir, safeName, wallClockMs, elapsedSec, usedBytes, maxBytes, ratio, nativeBytes, activeSensorCount)
            maybeDumpHprof(capturedGeneration, baseDir, safeName, usedBytes, ratio)
        } catch (t: Throwable) {
            safeLogWarn("DebugHeapWatchdog background sample failed", t)
        }
    }

    private fun writeCsvRowAndPrune(
        baseDir: File,
        safeName: String,
        wallClockMs: Long,
        elapsedSec: Long,
        usedBytes: Long,
        maxBytes: Long,
        ratio: Double,
        nativeBytes: Long,
        activeSensorCount: Int
    ) {
        val heapDir = File(baseDir, DEBUG_HEAP_DIR)
        if (!heapDir.exists() && !heapDir.mkdirs()) return

        val csvFile = File(heapDir, "$safeName$HEAP_CSV_EXTENSION")
        val needsHeader = !csvFile.exists() || csvFile.length() == 0L

        val usedMb = usedBytes.toDouble() / BYTES_PER_MB
        val maxMb = maxBytes.toDouble() / BYTES_PER_MB
        val pct = ratio * 100.0
        val nativeMb = nativeBytes.toDouble() / BYTES_PER_MB
        val isoTimestamp = formatUtcIsoTimestamp(wallClockMs)

        FileWriter(csvFile, true).use { writer ->
            if (needsHeader) {
                writer.append(CSV_HEADER).append('\n')
            }
            writer.append(
                String.format(
                    Locale.US,
                    "%s,%d,%.2f,%.2f,%.1f,%.2f,%d%n",
                    isoTimestamp,
                    elapsedSec,
                    usedMb,
                    maxMb,
                    pct,
                    nativeMb,
                    activeSensorCount
                )
            )
            writer.flush()
        }

        pruneOldestFiles(heapDir, HEAP_CSV_EXTENSION, maxHeapCsvFiles, protectedFile = csvFile)
    }

    private fun maybeDumpHprof(
        capturedGeneration: Long,
        baseDir: File,
        safeName: String,
        usedBytes: Long,
        ratio: Double
    ) {
        val shouldAttemptDump: Boolean
        synchronized(lock) {
            if (capturedGeneration != sessionGeneration) return
            consecutiveHighWaterCount = if (ratio >= hprofHeapRatio) {
                consecutiveHighWaterCount + 1
            } else {
                0
            }
            shouldAttemptDump = !hasDumpedHprofThisSession &&
                consecutiveHighWaterCount >= consecutiveHighWaterRequired
        }
        if (!shouldAttemptDump) return

        val hprofDir = File(baseDir, DEBUG_HPROF_DIR)
        if (hasExistingSessionHprof(hprofDir, safeName)) {
            synchronized(lock) {
                if (capturedGeneration == sessionGeneration) {
                    hasDumpedHprofThisSession = true
                }
            }
            return
        }

        val usableBytes = diskSpaceProvider(baseDir)
        if (usableBytes < minFreeSpaceBytes) {
            safeLogWarn("Skipping HPROF dump: insufficient free space ($usableBytes bytes < $minFreeSpaceBytes)", null)
            return
        }

        if (!hprofDir.exists() && !hprofDir.mkdirs()) return

        synchronized(lock) {
            if (capturedGeneration != sessionGeneration || hasDumpedHprofThisSession) return
            hasDumpedHprofThisSession = true
        }

        val usedMbInt = (usedBytes.toDouble() / BYTES_PER_MB).roundToInt().coerceAtLeast(0)
        val destFile = File(hprofDir, "${safeName}_${usedMbInt}MB$HPROF_EXTENSION")
        hprofDumper(destFile.absolutePath)
        pruneOldestFiles(hprofDir, HPROF_EXTENSION, maxHprofFiles, protectedFile = destFile)
    }

    private fun hasExistingSessionHprof(hprofDir: File, safeName: String): Boolean {
        if (!hprofDir.exists() || !hprofDir.isDirectory) return false
        val prefix = "${safeName}_"
        val existing = hprofDir.listFiles { file ->
            file.isFile && file.name.startsWith(prefix) && file.name.endsWith(HPROF_EXTENSION)
        }
        return !existing.isNullOrEmpty()
    }

    private fun pruneOldestFiles(
        dir: File,
        extension: String,
        maxToKeep: Int,
        protectedFile: File
    ) {
        if (maxToKeep < 0 || !dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles { file ->
            file.isFile && file.name.endsWith(extension)
        }?.toList() ?: return

        if (files.size <= maxToKeep) return

        val protectedPath = protectedFile.absolutePath
        val sorted = files.sortedWith(
            compareByDescending<File> { it.absolutePath == protectedPath }
                .thenByDescending { it.lastModified() }
                .thenByDescending { it.name }
        )

        for (index in maxToKeep until sorted.size) {
            val candidate = sorted[index]
            if (candidate.absolutePath != protectedPath) {
                candidate.delete()
            }
        }
    }

    private fun formatUtcIsoTimestamp(epochMs: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date(epochMs))
    }

    private fun safeLogWarn(message: String, t: Throwable?) {
        try {
            if (t != null) {
                Log.w(TAG, message, t)
            } else {
                Log.w(TAG, message)
            }
        } catch (_: Throwable) {
            // Plain-JVM unit tests without mocked Log must never fail.
        }
    }

    companion object {
        private const val TAG = "DebugHeapWatchdog"
        const val DEBUG_HEAP_DIR = "debug_heap"
        const val DEBUG_HPROF_DIR = "debug_hprof"
        const val HEAP_CSV_EXTENSION = ".heap.csv"
        const val HPROF_EXTENSION = ".hprof"
        const val CSV_HEADER = "timestamp,elapsedSec,javaUsedMb,javaMaxMb,javaPct,nativeAllocatedMb,activeSensorCount"

        const val DEFAULT_SAMPLING_INTERVAL_MS = 30_000L
        const val DEFAULT_HPROF_HEAP_RATIO = 0.70
        const val DEFAULT_CONSECUTIVE_HIGH_WATER_REQUIRED = 2
        const val DEFAULT_MIN_FREE_SPACE_BYTES = 1_073_741_824L // 1 GB
        const val DEFAULT_MAX_HEAP_CSV_FILES = 20
        const val DEFAULT_MAX_HPROF_FILES = 2
        private const val BYTES_PER_MB = 1024.0 * 1024.0
        private val UNSAFE_NAME_CHARS = Regex("[^A-Za-z0-9._-]")

        internal fun sanitizeFileBaseName(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim().replace("..", "_")
            val sanitized = UNSAFE_NAME_CHARS.replace(trimmed, "_")
            return sanitized.takeIf { it.isNotBlank() }
        }

        internal fun countActiveDevicesSafely(banalService: BANALServiceComm?): Int {
            if (banalService == null) return 0
            return try {
                banalService.activeDevicesIncludingSpeedAndLocationDevices?.size ?: 0
            } catch (_: Throwable) {
                0
            }
        }

        private fun resolveSafeFilesDir(context: Context?): File? {
            if (context == null) return null
            return try {
                val dir = context.filesDir ?: return null
                if (dir.path.isEmpty()) null else dir
            } catch (_: Throwable) {
                null
            }
        }

        internal fun captureRuntimeHeapSnapshot(): HeapSnapshot {
            val runtime = Runtime.getRuntime()
            val maxBytes = runtime.maxMemory().coerceAtLeast(1L)
            val usedBytes = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
            val nativeBytes = try {
                Debug.getNativeHeapAllocatedSize().coerceAtLeast(0L)
            } catch (_: Throwable) {
                0L
            }
            return HeapSnapshot(
                javaUsedBytes = usedBytes,
                javaMaxBytes = maxBytes,
                nativeAllocatedBytes = nativeBytes
            )
        }
    }
}
