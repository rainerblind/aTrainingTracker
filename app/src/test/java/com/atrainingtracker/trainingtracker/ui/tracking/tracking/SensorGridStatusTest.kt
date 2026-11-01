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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for non-recording cockpit status resolution and sensor grid dimming alpha
 * (REQ-TRK-045, TST-TRK-045, ATT-4856).
 */
class SensorGridStatusTest {

    @Test
    fun `TST-TRK-045_1 resolveCockpitStatus contract across screen and tracking modes`() {
        // 1. In ScreenMode.TRACKING:
        // Ready -> NOT_STARTED
        val statusReady = SensorGridStatusResolver.resolveCockpitStatus(
            screenMode = ScreenMode.TRACKING,
            trackingMode = TrackingMode.READY,
            isAutoPaused = false
        )
        assertEquals(CockpitStatus.NOT_STARTED, statusReady)
        assertEquals(R.string.cockpit_status_not_started, statusReady?.titleRes)
        assertEquals(R.string.Start, statusReady?.actionRes)
        assertEquals(TrainingApplication.REQUEST_START_TRACKING, statusReady?.intentAction)

        // IDLE -> NOT_STARTED
        val statusIdle = SensorGridStatusResolver.resolveCockpitStatus(
            screenMode = ScreenMode.TRACKING,
            trackingMode = TrackingMode.IDLE,
            isAutoPaused = false
        )
        assertEquals(CockpitStatus.NOT_STARTED, statusIdle)

        // Manual Paused -> PAUSED
        val statusManualPaused = SensorGridStatusResolver.resolveCockpitStatus(
            screenMode = ScreenMode.TRACKING,
            trackingMode = TrackingMode.PAUSED,
            isAutoPaused = false
        )
        assertEquals(CockpitStatus.PAUSED, statusManualPaused)
        assertEquals(R.string.cockpit_status_paused, statusManualPaused?.titleRes)
        assertEquals(R.string.cockpit_auto_pause_resume, statusManualPaused?.actionRes)
        assertEquals(TrainingApplication.REQUEST_RESUME_FROM_PAUSED, statusManualPaused?.intentAction)

        // Auto-Paused -> AUTO_PAUSED
        val statusAutoPaused = SensorGridStatusResolver.resolveCockpitStatus(
            screenMode = ScreenMode.TRACKING,
            trackingMode = TrackingMode.PAUSED,
            isAutoPaused = true
        )
        assertEquals(CockpitStatus.AUTO_PAUSED, statusAutoPaused)
        assertEquals(R.string.cockpit_auto_pause_badge, statusAutoPaused?.titleRes)
        assertEquals(R.string.cockpit_auto_pause_resume, statusAutoPaused?.actionRes)
        assertEquals(TrainingApplication.REQUEST_RESUME_FROM_PAUSED, statusAutoPaused?.intentAction)

        // Tracking actively -> null (no banner)
        val statusTracking = SensorGridStatusResolver.resolveCockpitStatus(
            screenMode = ScreenMode.TRACKING,
            trackingMode = TrackingMode.TRACKING,
            isAutoPaused = false
        )
        assertNull(statusTracking)

        // 2. Outside ScreenMode.TRACKING (CONFIGURATION / PREVIEW): always null
        assertNull(
            SensorGridStatusResolver.resolveCockpitStatus(
                screenMode = ScreenMode.CONFIGURATION,
                trackingMode = TrackingMode.READY,
                isAutoPaused = false
            )
        )
        assertNull(
            SensorGridStatusResolver.resolveCockpitStatus(
                screenMode = ScreenMode.CONFIGURATION,
                trackingMode = TrackingMode.PAUSED,
                isAutoPaused = false
            )
        )
        assertNull(
            SensorGridStatusResolver.resolveCockpitStatus(
                screenMode = ScreenMode.PREVIEW,
                trackingMode = TrackingMode.PAUSED,
                isAutoPaused = true
            )
        )
    }

    @Test
    fun `TST-TRK-045_2 resolveGridAlpha contract across screen and tracking modes`() {
        // In ScreenMode.TRACKING, dimmed to 0.6f when not recording
        assertEquals(0.6f, SensorGridStatusResolver.resolveGridAlpha(ScreenMode.TRACKING, TrackingMode.READY), 0.001f)
        assertEquals(0.6f, SensorGridStatusResolver.resolveGridAlpha(ScreenMode.TRACKING, TrackingMode.PAUSED), 0.001f)

        // In ScreenMode.TRACKING, full 1.0f when recording
        assertEquals(1.0f, SensorGridStatusResolver.resolveGridAlpha(ScreenMode.TRACKING, TrackingMode.TRACKING), 0.001f)

        // In ScreenMode.CONFIGURATION or PREVIEW, always undimmed 1.0f
        assertEquals(1.0f, SensorGridStatusResolver.resolveGridAlpha(ScreenMode.CONFIGURATION, TrackingMode.READY), 0.001f)
        assertEquals(1.0f, SensorGridStatusResolver.resolveGridAlpha(ScreenMode.CONFIGURATION, TrackingMode.PAUSED), 0.001f)
        assertEquals(1.0f, SensorGridStatusResolver.resolveGridAlpha(ScreenMode.CONFIGURATION, TrackingMode.TRACKING), 0.001f)
        assertEquals(1.0f, SensorGridStatusResolver.resolveGridAlpha(ScreenMode.PREVIEW, TrackingMode.READY), 0.001f)
        assertEquals(1.0f, SensorGridStatusResolver.resolveGridAlpha(ScreenMode.PREVIEW, TrackingMode.PAUSED), 0.001f)
        assertEquals(1.0f, SensorGridStatusResolver.resolveGridAlpha(ScreenMode.PREVIEW, TrackingMode.TRACKING), 0.001f)
    }
}
