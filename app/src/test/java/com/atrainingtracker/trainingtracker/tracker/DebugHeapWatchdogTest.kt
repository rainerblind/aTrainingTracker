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
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm
import com.atrainingtracker.banalservice.devices.MyDevice
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for [DebugHeapWatchdog] (REQ-STB-022, TST-STB-022, ATT-9352).
 */
// Linked test specs: TST-STB-022, TST-STB-022.1, TST-STB-022.2, TST-STB-022.3
class DebugHeapWatchdogTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val directExecutor = Executor { runnable -> runnable.run() }

    private fun mb(megabytes: Long): Long = megabytes * 1024L * 1024L

    // Linked test specs: TST-STB-022.1
    @Test
    fun releaseMode_noOpsCompletelyWithZeroFilesThreadsOrProviderCalls() {
        val rootDir = tempFolder.newFolder("release_noop")
        var snapshotCalls = 0
        var dumpCalls = 0
        var executorCalls = 0

        val watchdog = DebugHeapWatchdog(
            filesDirProvider = { rootDir },
            isDebug = false,
            memorySnapshotProvider = {
                snapshotCalls++
                DebugHeapWatchdog.HeapSnapshot(mb(400), mb(512), mb(20))
            },
            hprofDumper = { dumpCalls++ },
            executorOverride = Executor { r ->
                executorCalls++
                r.run()
            }
        )

        watchdog.startSession("2026-11-21_000100")
        watchdog.onTick("2026-11-21_000100", 3)
        watchdog.onTick("2026-11-21_000100", 3)
        watchdog.stopSession()
        watchdog.shutdown()

        assertEquals(0, executorCalls)
        assertEquals(0, snapshotCalls)
        assertEquals(0, dumpCalls)
        assertFalse(File(rootDir, DebugHeapWatchdog.DEBUG_HEAP_DIR).exists())
        assertFalse(File(rootDir, DebugHeapWatchdog.DEBUG_HPROF_DIR).exists())
    }

    // Linked test specs: TST-STB-022.1
    @Test
    fun emptyFilesDirFromRelaxedMockContext_isSafelyIgnoredWithoutCreatingRelativeDirs() {
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.filesDir } returns File("")

        val watchdog = DebugHeapWatchdog(
            filesDirProvider = {
                val dir = mockContext.filesDir
                if (dir.path.isEmpty()) null else dir
            },
            isDebug = true,
            executorOverride = directExecutor
        )

        watchdog.startSession("2026-11-21_000101")
        watchdog.onTick("2026-11-21_000101", 2)
        watchdog.shutdown()

        assertFalse(File(DebugHeapWatchdog.DEBUG_HEAP_DIR).exists())
        assertFalse(File(DebugHeapWatchdog.DEBUG_HPROF_DIR).exists())
    }

    // Linked test specs: TST-STB-022.2
    @Test
    fun periodicSampling_writes30sCsvRowsAndIgnoresIntermediateTicks() {
        val rootDir = tempFolder.newFolder("csv_30s")
        var nowMs = 1_763_683_200_000L // 2025-11-21T00:00:00Z
        var usedMb = 100L
        var nativeMb = 24L

        val watchdog = DebugHeapWatchdog(
            filesDirProvider = { rootDir },
            isDebug = true,
            clock = { nowMs },
            memorySnapshotProvider = {
                DebugHeapWatchdog.HeapSnapshot(mb(usedMb), mb(512), mb(nativeMb))
            },
            executorOverride = directExecutor
        )

        watchdog.startSession("2026-11-21_000000")
        watchdog.onTick("2026-11-21_000000", 4)

        // 15s later: should be skipped (< 30s)
        nowMs += 15_000L
        usedMb = 120L
        watchdog.onTick("2026-11-21_000000", 4)

        // 30s from first sample: should log
        nowMs += 15_000L
        usedMb = 150L
        nativeMb = 28L
        watchdog.onTick("2026-11-21_000000", 5)

        // 60s from first sample: should log
        nowMs += 30_000L
        usedMb = 200L
        watchdog.onTick("2026-11-21_000000", 6)

        val csvFile = File(File(rootDir, DebugHeapWatchdog.DEBUG_HEAP_DIR), "2026-11-21_000000.heap.csv")
        assertTrue(csvFile.exists())
        val lines = csvFile.readLines()
        assertEquals(4, lines.size) // header + 3 samples (0s, 30s, 60s)
        assertEquals(DebugHeapWatchdog.CSV_HEADER, lines[0])
        assertTrue(lines[1].endsWith(",0,100.00,512.00,19.5,24.00,4"))
        assertTrue(lines[2].endsWith(",30,150.00,512.00,29.3,28.00,5"))
        assertTrue(lines[3].endsWith(",60,200.00,512.00,39.1,28.00,6"))
    }

    // Linked test specs: TST-STB-022.2
    @Test
    fun deferredBaseFileNameOnResume_autoBindsAndPrunesOldCsvLogsBeyond20() {
        val rootDir = tempFolder.newFolder("csv_prune")
        val heapDir = File(rootDir, DebugHeapWatchdog.DEBUG_HEAP_DIR).apply { mkdirs() }
        for (i in 1..22) {
            val oldFile = File(heapDir, String.format("2026-11-01_%06d.heap.csv", i))
            oldFile.writeText("header\n")
            oldFile.setLastModified(1_000_000L + i * 1_000L)
        }

        var nowMs = 1_763_683_200_000L
        val watchdog = DebugHeapWatchdog(
            filesDirProvider = { rootDir },
            isDebug = true,
            clock = { nowMs },
            memorySnapshotProvider = { DebugHeapWatchdog.HeapSnapshot(mb(64), mb(512), mb(12)) },
            executorOverride = directExecutor
        )

        // Null baseFileName during async resume rehydration is ignored safely.
        watchdog.onTick(null, 2)
        // Once mBaseFileName is restored, watchdog auto-binds without needing startSession().
        nowMs += 1_000L
        watchdog.onTick("2026-11-21_000500", 3)

        val remaining = heapDir.listFiles { f -> f.name.endsWith(".heap.csv") }?.map { it.name }?.toSet() ?: emptySet()
        assertEquals(20, remaining.size)
        assertTrue(remaining.contains("2026-11-21_000500.heap.csv"))
        assertFalse(remaining.contains("2026-11-01_000001.heap.csv"))
        assertFalse(remaining.contains("2026-11-01_000002.heap.csv"))
        assertFalse(remaining.contains("2026-11-01_000003.heap.csv"))
        assertTrue(remaining.contains("2026-11-01_000004.heap.csv"))
    }

    // Linked test specs: TST-STB-022.3
    @Test
    fun hprofCapture_requiresConsecutiveHighWater_capsOnePerSession_andPrunesToLatest2() {
        val rootDir = tempFolder.newFolder("hprof_capture")
        val hprofDir = File(rootDir, DebugHeapWatchdog.DEBUG_HPROF_DIR).apply { mkdirs() }
        val oldDump1 = File(hprofDir, "2026-11-10_100000_370MB.hprof").apply {
            writeText("old1")
            setLastModified(1_000L)
        }
        val oldDump2 = File(hprofDir, "2026-11-11_100000_380MB.hprof").apply {
            writeText("old2")
            setLastModified(2_000L)
        }

        var nowMs = 1_763_683_200_000L
        var usedMb = 360L // 70.3% of 512 MB
        val dumpedPaths = mutableListOf<String>()

        val watchdog = DebugHeapWatchdog(
            filesDirProvider = { rootDir },
            isDebug = true,
            clock = { nowMs },
            memorySnapshotProvider = { DebugHeapWatchdog.HeapSnapshot(mb(usedMb), mb(512), mb(30)) },
            diskSpaceProvider = { 2L * 1024L * 1024L * 1024L }, // 2 GB free
            hprofDumper = { path ->
                File(path).writeText("hprof-payload")
                dumpedPaths.add(path)
            },
            executorOverride = directExecutor
        )

        watchdog.startSession("2026-11-21_010000")
        // Sample 1: >= 70% transient spike
        watchdog.onTick("2026-11-21_010000", 2)
        assertEquals(0, dumpedPaths.size)

        // Sample 2: drops below 70% -> resets consecutive high-water counter
        nowMs += 30_000L
        usedMb = 250L
        watchdog.onTick("2026-11-21_010000", 2)
        assertEquals(0, dumpedPaths.size)

        // Sample 3: first >= 70% again -> no dump yet
        nowMs += 30_000L
        usedMb = 360L
        watchdog.onTick("2026-11-21_010000", 2)
        assertEquals(0, dumpedPaths.size)

        // Sample 4: second consecutive >= 70% -> dumps exactly once
        nowMs += 30_000L
        usedMb = 372L
        watchdog.onTick("2026-11-21_010000", 2)
        assertEquals(1, dumpedPaths.size)
        assertTrue(dumpedPaths[0].endsWith("2026-11-21_010000_372MB.hprof"))

        // Sample 5: third consecutive >= 70% in same session -> must NOT dump again
        nowMs += 30_000L
        usedMb = 410L
        watchdog.onTick("2026-11-21_010000", 2)
        assertEquals(1, dumpedPaths.size)

        // Pruned to at most 2 newest .hprof files
        val retainedNames = hprofDir.listFiles { f -> f.name.endsWith(".hprof") }?.map { it.name }?.toSet() ?: emptySet()
        assertEquals(2, retainedNames.size)
        assertFalse(retainedNames.contains(oldDump1.name))
        assertTrue(retainedNames.contains(oldDump2.name))
        assertTrue(retainedNames.contains("2026-11-21_010000_372MB.hprof"))

        // Simulated process restart resuming the SAME fileBaseName must also honor 1-dump-per-workout cap
        val restartedWatchdog = DebugHeapWatchdog(
            filesDirProvider = { rootDir },
            isDebug = true,
            clock = { nowMs },
            memorySnapshotProvider = { DebugHeapWatchdog.HeapSnapshot(mb(420), mb(512), mb(30)) },
            diskSpaceProvider = { 2L * 1024L * 1024L * 1024L },
            hprofDumper = { path -> dumpedPaths.add(path) },
            executorOverride = directExecutor
        )
        nowMs += 30_000L
        restartedWatchdog.onTick("2026-11-21_010000", 2)
        nowMs += 30_000L
        restartedWatchdog.onTick("2026-11-21_010000", 2)
        assertEquals(1, dumpedPaths.size)
    }

    // Linked test specs: TST-STB-022.3
    @Test
    fun hprofCapture_skipsWhenFreeSpaceBelow1Gb_andShieldsExceptions() {
        val rootDir = tempFolder.newFolder("hprof_guards")
        var nowMs = 1_763_683_200_000L
        val dumpAttempts = AtomicInteger(0)

        val lowDiskWatchdog = DebugHeapWatchdog(
            filesDirProvider = { rootDir },
            isDebug = true,
            clock = { nowMs },
            memorySnapshotProvider = { DebugHeapWatchdog.HeapSnapshot(mb(400), mb(512), mb(10)) },
            diskSpaceProvider = { 500L * 1024L * 1024L }, // 500 MB < 1 GB
            hprofDumper = { dumpAttempts.incrementAndGet() },
            executorOverride = directExecutor
        )

        lowDiskWatchdog.startSession("2026-11-21_020000")
        lowDiskWatchdog.onTick("2026-11-21_020000", 1)
        nowMs += 30_000L
        lowDiskWatchdog.onTick("2026-11-21_020000", 1)
        assertEquals(0, dumpAttempts.get())

        val throwingWatchdog = DebugHeapWatchdog(
            filesDirProvider = { rootDir },
            isDebug = true,
            clock = { nowMs },
            memorySnapshotProvider = { DebugHeapWatchdog.HeapSnapshot(mb(400), mb(512), mb(10)) },
            diskSpaceProvider = { 2L * 1024L * 1024L * 1024L },
            hprofDumper = {
                dumpAttempts.incrementAndGet()
                throw OutOfMemoryError("Simulated dump failure")
            },
            executorOverride = directExecutor
        )

        throwingWatchdog.startSession("2026-11-21_020500")
        throwingWatchdog.onTick("2026-11-21_020500", 1)
        nowMs += 30_000L
        throwingWatchdog.onTick("2026-11-21_020500", 1)
        // Third high-water sample must not retry after one-shot attempt failed
        nowMs += 30_000L
        throwingWatchdog.onTick("2026-11-21_020500", 1)
        assertEquals(1, dumpAttempts.get())
    }

    // Linked test specs: TST-STB-022.2
    @Test
    fun backgroundExecutor_runsOffCallingThreadWithDaemonThreadName() {
        val rootDir = tempFolder.newFolder("async_bg")
        val callingThreadName = Thread.currentThread().name
        val latch = CountDownLatch(1)
        var workerThreadName: String? = null

        val watchdog = DebugHeapWatchdog(
            filesDirProvider = { rootDir },
            isDebug = true,
            memorySnapshotProvider = {
                workerThreadName = Thread.currentThread().name
                latch.countDown()
                DebugHeapWatchdog.HeapSnapshot(mb(64), mb(512), mb(8))
            }
        )

        watchdog.startSession("2026-11-21_030000")
        watchdog.onTick("2026-11-21_030000", 1)
        assertTrue("Background watchdog sample must run asynchronously", latch.await(5, TimeUnit.SECONDS))
        assertEquals("DebugHeapWatchdog", workerThreadName)
        assertFalse(callingThreadName == workerThreadName)
        watchdog.shutdown()
    }

    // Linked test specs: TST-STB-022.2
    @Test
    fun onTickBanalService_queriesActiveDevicesOnlyOncePer30Seconds() {
        val rootDir = tempFolder.newFolder("banal_30s_gate")
        var nowMs = 1_763_683_200_000L
        val mockBanal = mockk<BANALServiceComm>()
        every { mockBanal.activeDevicesIncludingSpeedAndLocationDevices } returns listOf(
            mockk<MyDevice>(relaxed = true),
            mockk<MyDevice>(relaxed = true)
        )

        val watchdog = DebugHeapWatchdog(
            filesDirProvider = { rootDir },
            isDebug = true,
            clock = { nowMs },
            memorySnapshotProvider = { DebugHeapWatchdog.HeapSnapshot(mb(64), mb(512), mb(8)) },
            executorOverride = directExecutor
        )

        watchdog.startSession("2026-11-21_033000")
        for (sec in 0..60) {
            watchdog.onTick("2026-11-21_033000", mockBanal)
            nowMs += 1_000L
        }

        verify(exactly = 3) { mockBanal.activeDevicesIncludingSpeedAndLocationDevices }
        val csvFile = File(File(rootDir, DebugHeapWatchdog.DEBUG_HEAP_DIR), "2026-11-21_033000.heap.csv")
        assertEquals(4, csvFile.readLines().size) // header + t=0, 30, 60
    }
}
