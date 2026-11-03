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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.ui.graphics.vector.ImageVector
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode

/**
 * High-visibility cockpit status banner models for non-recording states (REQ-TRK-045, ATT-4856).
 */
enum class CockpitStatus(
    val titleRes: Int,
    val actionRes: Int,
    val icon: ImageVector,
    val intentAction: String
) {
    NOT_STARTED(
        titleRes = R.string.cockpit_status_not_started,
        actionRes = R.string.Start,
        icon = Icons.Default.PlayCircle,
        intentAction = TrainingApplication.REQUEST_START_TRACKING
    ),
    PAUSED(
        titleRes = R.string.cockpit_status_paused,
        actionRes = R.string.cockpit_auto_pause_resume,
        icon = Icons.Default.PauseCircle,
        intentAction = TrainingApplication.REQUEST_RESUME_FROM_PAUSED
    ),
    AUTO_PAUSED(
        titleRes = R.string.cockpit_auto_pause_badge,
        actionRes = R.string.cockpit_auto_pause_resume,
        icon = Icons.Default.PauseCircle,
        intentAction = TrainingApplication.REQUEST_RESUME_FROM_PAUSED
    )
}

/**
 * Pure state resolver mapping runtime tracking and screen modes to banner status and grid dimming alpha
 * (REQ-TRK-045, TST-TRK-045, ATT-4856).
 */
object SensorGridStatusResolver {

    /**
     * Resolves the non-recording cockpit status banner to display.
     * Returns null when recording is actively underway or when outside [ScreenMode.TRACKING].
     */
    @JvmStatic
    fun resolveCockpitStatus(
        screenMode: ScreenMode,
        trackingMode: TrackingMode,
        isAutoPaused: Boolean
    ): CockpitStatus? {
        if (screenMode != ScreenMode.TRACKING) return null
        return when (trackingMode) {
            TrackingMode.IDLE, TrackingMode.READY -> CockpitStatus.NOT_STARTED
            TrackingMode.PAUSED -> if (isAutoPaused) CockpitStatus.AUTO_PAUSED else CockpitStatus.PAUSED
            TrackingMode.TRACKING -> null
        }
    }

    /**
     * Resolves the compositing alpha for sensor grid content.
     * Dims to 0.6f when viewing sensor tabs in [ScreenMode.TRACKING] while recording is not active.
     */
    @JvmStatic
    fun resolveGridAlpha(
        screenMode: ScreenMode,
        trackingMode: TrackingMode
    ): Float {
        return if (screenMode == ScreenMode.TRACKING && trackingMode != TrackingMode.TRACKING) 0.6f else 1.0f
    }
}
