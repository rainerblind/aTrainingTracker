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
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfile
import com.atrainingtracker.trainingtracker.ui.segments.SegmentHeader
import com.atrainingtracker.trainingtracker.ui.segments.SegmentLiveDetails


@Composable
fun LiveSegmentSheet(
    liveSegment: LiveSegment
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            //.fillMaxHeight(0.5f)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // --- Drag Handle (The horizontal line) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(width = 32.dp, height = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.extraLarge
            ) {}
        }

        // --- Header with live data ---
        SegmentHeader(
            summary = liveSegment.staticData.summary,
            liveSegmentStatus = liveSegment.liveData.segmentStatus,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        SegmentLiveDetails(
            liveSegmentData = liveSegment.liveData,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // --- Elevation Profile ---
        ElevationProfile(
            pathPoints = liveSegment.staticData.path,
            // Pass the raw distance double to the profile for the progress marker
            currentDistance = liveSegment.liveData.distanceOnSegment_raw,
            modifier = Modifier
                .height(150.dp)
                .fillMaxWidth()
        )
    }
}