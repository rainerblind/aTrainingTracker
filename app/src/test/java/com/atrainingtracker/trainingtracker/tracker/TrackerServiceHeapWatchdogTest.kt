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

import com.atrainingtracker.banalservice.BANALService.BANALServiceComm
import com.atrainingtracker.banalservice.devices.MyDevice
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executor

/**
 * Integration/wiring tests for [TrackerService] <-> [DebugHeapWatchdog] hooks
 * (REQ-STB-022.1, REQ-STB-022.2, TST-STB-022.1, TST-STB-022.2).
 */
// Linked test specs: TST-STB-022.1, TST-STB-022.2
class TrackerServiceHeapWatchdogTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // Linked test specs: TST-STB-022.2
    @Test
    fun countActiveDevicesSafely_handlesNullThrowingAndValidBanalService() {
        assertEquals(0, DebugHeapWatchdog.countActiveDevicesSafely(null))

        val throwingComm = mockk<BANALServiceComm>()
        every { throwingComm.activeDevicesIncludingSpeedAndLocationDevices } throws IllegalStateException("unmocked")
        assertEquals(0, DebugHeapWatchdog.countActiveDevicesSafely(throwingComm))

        val validComm = mockk<BANALServiceComm>()
        every { validComm.activeDevicesIncludingSpeedAndLocationDevices } returns listOf(
            mockk<MyDevice>(relaxed = true),
            mockk<MyDevice>(relaxed = true),
            mockk<MyDevice>(relaxed = true)
        )
        assertEquals(3, DebugHeapWatchdog.countActiveDevicesSafely(validComm))
    }

    // Linked test specs: TST-STB-022.1, TST-STB-022.2
    @Test
    fun trackerServiceWatchdogHook_logsCsvViaBanalServiceOverloadAndHonorsReleaseNoOp() {
        val rootDir = tempFolder.newFolder("service_hook")
        val mockBanal = mockk<BANALServiceComm>()
        every { mockBanal.activeDevicesIncludingSpeedAndLocationDevices } returns listOf(
            mockk<MyDevice>(relaxed = true),
            mockk<MyDevice>(relaxed = true)
        )

        val debugWatchdog = DebugHeapWatchdog(
            filesDirProvider = { rootDir },
            isDebug = true,
            clock = { 1_763_683_200_000L },
            memorySnapshotProvider = { DebugHeapWatchdog.HeapSnapshot(100L * 1024L * 1024L, 512L * 1024L * 1024L, 16L * 1024L * 1024L) },
            executorOverride = Executor { it.run() }
        )

        debugWatchdog.startSession("2026-11-21_040000")
        debugWatchdog.onTick("2026-11-21_040000", mockBanal)
        debugWatchdog.stopSession()
        debugWatchdog.shutdown()

        val csvFile = File(File(rootDir, DebugHeapWatchdog.DEBUG_HEAP_DIR), "2026-11-21_040000.heap.csv")
        assertTrue(csvFile.exists())
        val lines = csvFile.readLines()
        assertEquals(2, lines.size)
        assertTrue(lines[1].endsWith(",2"))

        // Release-mode hook on TrackerService accessor must produce zero files
        val releaseRoot = tempFolder.newFolder("service_release")
        val releaseWatchdog = DebugHeapWatchdog(
            filesDirProvider = { releaseRoot },
            isDebug = false,
            executorOverride = Executor { it.run() }
        )
        releaseWatchdog.startSession("2026-11-21_040100")
        releaseWatchdog.onTick("2026-11-21_040100", mockBanal)
        releaseWatchdog.stopSession()
        releaseWatchdog.shutdown()
        assertFalse(File(releaseRoot, DebugHeapWatchdog.DEBUG_HEAP_DIR).exists())

        // Verify TrackerService lazy accessor does not throw on unattached JVM instance
        val service = TrackerService()
        assertNotNull(service.debugHeapWatchdog)
        service.debugHeapWatchdog.shutdown()
    }
}
