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

package com.atrainingtracker.trainingtracker.ui.segments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.segments.LiveSegment
import com.atrainingtracker.trainingtracker.segments.LiveSegmentStatus
import com.atrainingtracker.trainingtracker.ui.components.core.LiveTrackingEntitySheet
import com.atrainingtracker.trainingtracker.ui.map.MapDetailLayout
import com.atrainingtracker.trainingtracker.ui.map.MapZoomFocus
import com.atrainingtracker.banalservice.BSportType


@Composable
fun LiveSegmentSheet(
    liveSegment: LiveSegment,
    modifier: Modifier = Modifier
) {
    val externalScrubDistance = when (liveSegment.liveData.segmentStatus) {
        LiveSegmentStatus.ON_SEGMENT,
        LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH -> liveSegment.liveData.distanceOnSegment_raw
        LiveSegmentStatus.FINISHED -> liveSegment.staticData.summary.distance_raw.coerceAtLeast(liveSegment.liveData.distanceOnSegment_raw)
        LiveSegmentStatus.APPROACHING,
        LiveSegmentStatus.FAR_FAR_AWAY -> null
    }

    MapDetailLayout(
        modifier = modifier.fillMaxWidth().wrapContentHeight(),
        bSportType = liveSegment.staticData.summary.bSportType,
        zoomFocus = MapZoomFocus.FIT_PRIMARY,
        activeScrubPath = liveSegment.staticData.path,
        useStatusBarsPadding = false,
        showMap = false,
        showZoomControls = false,
        externalScrubDistance = externalScrubDistance,
        header = {
            LiveTrackingEntitySheet(
                headerContent = { headerModifier ->
                    // --- Header with live data (REQ-UI-196: harmonized padding with drag handle) ---
                    SegmentHeader(
                        summary = liveSegment.staticData.summary,
                        liveSegmentStatus = liveSegment.liveData.segmentStatus,
                        modifier = headerModifier
                    )
                },
                telemetryHudContent = { hudModifier ->
                    SegmentLiveDetails(
                        liveSegmentData = liveSegment.liveData,
                        modifier = hudModifier
                    )
                },
                elevationContent = {}
            )
        }
    )
}
