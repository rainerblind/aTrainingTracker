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

package com.atrainingtracker.trainingtracker.repositories

import android.content.Context
import android.content.Intent
import android.util.Log
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.TrackingMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit and contract tests for BANALServiceRepository verifying breadcrumb clearing,
 * force unbind behavior, and tracking mode state handling (REQ-TRK-049 / TST-TRK-049).
 */
// Linked test specs: TST-MAP-047
class BANALServiceRepositoryTest {

    private fun resolveSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("File not found: $relativePath")
    }

    @Test
    fun testClearBreadcrumbs_sourceContract() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt")
        val content = file.readText()

        // REQ-TRK-049.1 / TST-TRK-049.1: clearBreadcrumbs clears currentTrack and currentPathPoints
        assertTrue("clearBreadcrumbs must exist", content.contains("fun clearBreadcrumbs()"))
        assertTrue("clearBreadcrumbs must empty _currentTrack", content.contains("_currentTrack.value = emptyList()"))
        assertTrue("clearBreadcrumbs must empty _currentPathPoints", content.contains("_currentPathPoints.value = emptyList()"))
    }

    @Test
    fun testTrackingModeReceiver_clearsBreadcrumbsOnReady() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt")
        val content = file.readText()

        // REQ-TRK-049.1 / TST-TRK-049.1: trackingModeReceiver calls clearBreadcrumbs on READY
        assertTrue(
            "trackingModeReceiver must invoke clearBreadcrumbs when mode is READY",
            content.contains("if (newTrackingMode == TrackingMode.READY)") &&
            content.contains("clearBreadcrumbs()")
        )
    }

    @Test
    fun testForceUnbindFromBANALService_contract() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt")
        val content = file.readText()

        // REQ-TRK-049.2 / TST-TRK-049.2: forceUnbindFromBANALService resets bindReferenceCount to 0
        assertTrue("forceUnbindFromBANALService must exist", content.contains("fun forceUnbindFromBANALService()"))
        assertTrue("forceUnbindFromBANALService must reset count", content.contains("bindReferenceCount = 0"))
        assertTrue("forceUnbindFromBANALService must null out binder", content.contains("_serviceBinder.value = null"))
        assertTrue("forceUnbindFromBANALService must unbindService", content.contains("appContext.unbindService(banalServiceConnection)"))
    }

    @Test
    fun testTrainingApplication_clearsBreadcrumbsOnStopAndAbort() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java")
        val content = file.readText()

        // REQ-TRK-049.1 / TST-TRK-049.1: TrainingApplication must call clearBreadcrumbs on stop and abort
        assertTrue(
            "stopTracking must clear breadcrumbs",
            content.contains("BANALServiceRepository.Companion.getInstance(this).clearBreadcrumbs()")
        )
    }

    @Test
    fun testMainActivity_disconnectForcesUnbind() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt")
        val content = file.readText()

        // REQ-TRK-049.2 / TST-TRK-049.2: disconnectFromBANALService forces unbind when not tracking
        assertTrue(
            "disconnectFromBANALService must invoke forceUnbindFromBANALService",
            content.contains("forceUnbindFromBANALService()")
        )
    }

    // --- TST-TRK-080: Telemetry Rehydration Tests ---

    @Test
    fun testRehydrationGatingAndStartTypeNeutrality_contract_TST_TRK_080_1() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt")
        val content = file.readText()

        // TST-TRK-080.1: checkAndTriggerRehydration checks tracking mode, empty track, and active workout
        assertTrue(
            "checkAndTriggerRehydration must exist",
            content.contains("fun checkAndTriggerRehydration()")
        )
        assertTrue(
            "checkAndTriggerRehydration must check TRACKING and PAUSED modes",
            content.contains("mode != TrackingMode.TRACKING && mode != TrackingMode.PAUSED")
        )
        assertTrue(
            "checkAndTriggerRehydration must guard against already populated track",
            content.contains("_currentTrack.value.isNotEmpty()")
        )
        assertTrue(
            "checkAndTriggerRehydration must offload to Dispatchers.IO",
            content.contains("repositoryScope.launch(Dispatchers.IO)")
        )
        assertTrue(
            "trackingModeReceiver must trigger rehydration on TRACKING or PAUSED",
            content.contains("newTrackingMode == TrackingMode.TRACKING || newTrackingMode == TrackingMode.PAUSED") &&
            content.contains("checkAndTriggerRehydration()")
        )
        assertTrue(
            "clearSparklineBuffers must reset cached rehydration samples",
            content.contains("cachedRehydrationSamples = null")
        )
    }

    @Test
    fun testRehydrationTelemetry_dataFlowContract_TST_TRK_080_2() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt")
        val content = file.readText()

        // TST-TRK-080.2: rehydrateActiveWorkoutTelemetry fetches samples and updates currentTrack / currentPathPoints
        assertTrue(
            "rehydrateActiveWorkoutTelemetry must exist with Dispatchers.IO",
            content.contains("suspend fun rehydrateActiveWorkoutTelemetry(baseFileName: String) = withContext(Dispatchers.IO)")
        )
        assertTrue(
            "rehydrateActiveWorkoutTelemetry must construct LatLng track",
            content.contains("LatLng(s.latitude, s.longitude)")
        )
        assertTrue(
            "rehydrateActiveWorkoutTelemetry must construct PathPoint with distance and altitude",
            content.contains("com.atrainingtracker.trainingtracker.ui.map.PathPoint(") &&
            content.contains("distance = s.distanceMeters ?: 0.0") &&
            content.contains("altitude = s.altitude ?: 0.0")
        )
        assertTrue(
            "rehydrateActiveWorkoutTelemetry must use SnapshotChunkedList.from()",
            content.contains("SnapshotChunkedList.from(historicalTrack + currentLiveTrack)")
        )
        assertTrue(
            "rehydrateActiveWorkoutTelemetry must update path points via SnapshotChunkedList",
            content.contains("SnapshotChunkedList.from(historicalPathPoints + currentLivePathPoints)")
        )
    }

    @Test
    fun testSparklineRehydrationAndConcurrentMerge_contract_TST_TRK_080_4() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt")
        val content = file.readText()

        // TST-TRK-080.4: backfills sparklineBuffers and synchronizes track updates
        assertTrue(
            "rehydrateActiveWorkoutTelemetry must backfill registered buffers",
            content.contains("reg.buffer.backfillHistoricalSamples(metricValues)")
        )
        assertTrue(
            "registerSparklineBuffer must backfill from cachedRehydrationSamples when newly registered",
            content.contains("cachedRehydrationSamples?.let") &&
            content.contains("buffer.backfillHistoricalSamples(metricValues)")
        )
        assertTrue(
            "track mutations must synchronize on trackLock",
            content.contains("synchronized(trackLock)")
        )
    }
}
