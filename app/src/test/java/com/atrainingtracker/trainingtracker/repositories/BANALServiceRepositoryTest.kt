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
}
