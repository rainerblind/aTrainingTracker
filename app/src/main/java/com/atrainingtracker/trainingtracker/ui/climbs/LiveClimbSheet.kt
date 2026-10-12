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

package com.atrainingtracker.trainingtracker.ui.climbs

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.climbs.LiveClimbData
import com.atrainingtracker.trainingtracker.climbs.LiveClimbStatus
import com.atrainingtracker.trainingtracker.ui.components.MetricBadge
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfile
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Real-time Climb Cockpit Bottom Sheet (ClimbPro) for active route climbs and free-riding ascents (REQ-MAP-027).
 *
 * Renders:
 * 1. Header with climb name, category badge, and route climb index counter.
 * 2. Elevation profile curve colored dynamically by slope gradient bands with live rider pin.
 * 3. Pacing HUD metrics (remaining distance, remaining elevation gain, current instantaneous gradient).
 */
@Composable
fun LiveClimbSheet(
    liveClimb: LiveClimbData,
    modifier: Modifier = Modifier
) {
    val climb = liveClimb.climb
    val distanceFormatter = remember { DistanceFormatter() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // --- 1. Top Header Row: 32dp Icon, Title, and Right-Aligned Category Chip ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Terrain,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = climb.name.ifBlank { stringResource(R.string.climb_title) },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            ClimbCategoryChip(category = climb.category)
        }

        // --- 2. Second Row: Status and Route Counter ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClimbStatusBadge(
                status = liveClimb.status,
                distanceToStart = liveClimb.distanceToStart,
                distanceFormatter = distanceFormatter
            )

            val counterText = if (liveClimb.routeIndex != null && liveClimb.totalRouteClimbs != null) {
                stringResource(R.string.climb_route_counter, liveClimb.routeIndex, liveClimb.totalRouteClimbs)
            } else {
                "${climb.elevationGainMeters.roundToInt()} m"
            }
            Text(
                text = counterText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // --- 3. Standardized Horizontal Divider ---
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // --- 4. Live Telemetry HUD (Harmonized with SegmentLiveDetails) ---
        val remDistStr = if (liveClimb.status == LiveClimbStatus.FINISHED) {
            "0 m"
        } else {
            distanceFormatter.format_with_units(liveClimb.distanceToSummit) ?: "${liveClimb.distanceToSummit.roundToInt()} m"
        }
        val remElevStr = if (liveClimb.status == LiveClimbStatus.FINISHED) {
            "0 m"
        } else {
            "${liveClimb.remainingElevationGain.roundToInt()} m"
        }
        val gradeStr = String.format(Locale.getDefault(), "%.1f%%", liveClimb.currentGradePercent)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Distance & Elevation Progress
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "$remDistStr ${stringResource(R.string.climb_remaining_dist)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$remElevStr ${stringResource(R.string.climb_remaining_elevation)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Right: Instantaneous Grade in Headline Monospace
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = gradeStr,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.climb_grade),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // --- 5. Colored Elevation Profile (Shared ElevationProfile with Gradient Shading) ---
        val normalizedPoints = climb.normalizedPathPoints.ifEmpty { climb.pathPoints }
        val currentDistance = if (liveClimb.status == LiveClimbStatus.ON_CLIMB || liveClimb.status == LiveClimbStatus.FINISHED) {
            (liveClimb.currentProgressFraction.coerceIn(0f, 1f) * climb.distanceMeters)
        } else null

        ElevationProfile(
            pathPoints = normalizedPoints,
            currentDistance = currentDistance,
            showZoomControls = false,
            climbGradientShading = true,
            fixedHeight = 68.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

fun getClimbCategoryColors(category: ClimbCategory): Triple<Color, Color, Int> {
    return when (category) {
        ClimbCategory.HC -> Triple(Color(0xFF880E4F), Color.White, R.string.climb_category_hc)
        ClimbCategory.CAT_1 -> Triple(Color(0xFFC62828), Color.White, R.string.climb_category_cat1)
        ClimbCategory.CAT_2 -> Triple(Color(0xFFEF6C00), Color.White, R.string.climb_category_cat2)
        ClimbCategory.CAT_3 -> Triple(Color(0xFFF9A825), Color.Black, R.string.climb_category_cat3)
        ClimbCategory.CAT_4 -> Triple(Color(0xFF2E7D32), Color.White, R.string.climb_category_cat4)
        ClimbCategory.UNCATEGORIZED -> Triple(Color(0xFF757575), Color.White, R.string.climb_category_uc)
    }
}

@Composable
fun ClimbCategoryChip(
    category: ClimbCategory,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, labelRes) = getClimbCategoryColors(category)
    MetricBadge(
        text = stringResource(labelRes),
        containerColor = bgColor,
        contentColor = textColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    )
}

@Composable
private fun ClimbStatusBadge(
    status: LiveClimbStatus,
    distanceToStart: Double,
    distanceFormatter: DistanceFormatter,
    modifier: Modifier = Modifier
) {
    val (label, containerColor, contentColor) = when (status) {
        LiveClimbStatus.APPROACHING -> {
            val dist = distanceFormatter.format_with_units(distanceToStart) ?: "${distanceToStart.roundToInt()} m"
            Triple(
                "${stringResource(R.string.climb_approaching)} ($dist)",
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        LiveClimbStatus.ON_CLIMB -> Triple(
            stringResource(R.string.climb_on_climb),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        LiveClimbStatus.FINISHED -> Triple(
            stringResource(R.string.climb_summit),
            Color(0xFF2E7D32),
            Color.White
        )
        LiveClimbStatus.FAR_FAR_AWAY -> Triple(
            stringResource(R.string.climb_title),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    MetricBadge(
        text = label,
        containerColor = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    )
}

/**
 * Resolves standard slope gradient color bands (REQ-MAP-027, REQ-UI-352):
 * - < 3%: Green
 * - 3% - 6%: Yellow-Green
 * - 6% - 9%: Amber/Yellow
 * - 9% - 12%: Orange
 * - 12% - 20%: Red
 * - >= 20%: Dark Red / Black
 */
fun getClimbGradeColor(gradePercent: Double): Color {
    return when {
        gradePercent < 3.0 -> Color(0xFF4CAF50)
        gradePercent < 6.0 -> Color(0xFF8BC34A)
        gradePercent < 9.0 -> Color(0xFFFFC107)
        gradePercent < 12.0 -> Color(0xFFFF9800)
        gradePercent < 20.0 -> Color(0xFFE53935)
        else -> Color(0xFF212121)
    }
}

@Composable
private fun ClimbHudMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// =============================================================================
// Preview Fixtures & Multi-State Previews (REQ-UI-341, ATT-3103)
// =============================================================================

private fun createMockLiveClimbData(
    status: LiveClimbStatus = LiveClimbStatus.ON_CLIMB,
    distanceToStart: Double = 0.0,
    distanceToSummit: Double = 850.0,
    remainingElevationGain: Double = 62.0,
    currentGradePercent: Double = 7.4,
    currentProgressFraction: Float = 0.45f
): LiveClimbData {
    val samplePoints = listOf(
        PathPoint(distance = 0.0, latLng = LatLng(47.5, 11.5), altitude = 600.0),
        PathPoint(distance = 400.0, latLng = LatLng(47.502, 11.502), altitude = 630.0),
        PathPoint(distance = 800.0, latLng = LatLng(47.504, 11.504), altitude = 670.0),
        PathPoint(distance = 1200.0, latLng = LatLng(47.506, 11.506), altitude = 715.0),
        PathPoint(distance = 1550.0, latLng = LatLng(47.508, 11.508), altitude = 745.0)
    )
    val climb = Climb(
        id = 1L,
        name = "Alpe d'Huez Test Climb",
        startLat = 47.5,
        startLng = 11.5,
        endLat = 47.508,
        endLng = 11.508,
        distanceMeters = 1550.0,
        elevationGainMeters = 145.0,
        avgGradePercent = 9.3,
        maxGradePercent = 14.2,
        category = ClimbCategory.CAT_2,
        pathPoints = samplePoints
    )
    return LiveClimbData(
        climb = climb,
        status = status,
        distanceToStart = distanceToStart,
        distanceToSummit = distanceToSummit,
        remainingElevationGain = remainingElevationGain,
        currentGradePercent = currentGradePercent,
        currentProgressFraction = currentProgressFraction,
        routeIndex = 2,
        totalRouteClimbs = 5
    )
}

@Preview(name = "Light Mode - On Climb", showBackground = true)
@Composable
fun LiveClimbSheet_OnClimb_Preview() {
    ATrainingTrackerTheme(darkTheme = false) {
        LiveClimbSheet(
            liveClimb = createMockLiveClimbData(
                status = LiveClimbStatus.ON_CLIMB,
                currentProgressFraction = 0.45f
            )
        )
    }
}

@Preview(name = "Dark AMOLED Mode - On Climb", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LiveClimbSheet_OnClimb_Dark_Preview() {
    ATrainingTrackerTheme(darkTheme = true, amoled = true) {
        LiveClimbSheet(
            liveClimb = createMockLiveClimbData(
                status = LiveClimbStatus.ON_CLIMB,
                currentProgressFraction = 0.45f
            )
        )
    }
}

@Preview(name = "Approaching Climb", showBackground = true)
@Composable
fun LiveClimbSheet_Approaching_Preview() {
    ATrainingTrackerTheme(darkTheme = false) {
        LiveClimbSheet(
            liveClimb = createMockLiveClimbData(
                status = LiveClimbStatus.APPROACHING,
                distanceToStart = 350.0,
                distanceToSummit = 1550.0,
                remainingElevationGain = 145.0,
                currentProgressFraction = 0f
            )
        )
    }
}

@Preview(name = "Summit Finished", showBackground = true)
@Composable
fun LiveClimbSheet_Finished_Preview() {
    ATrainingTrackerTheme(darkTheme = false) {
        LiveClimbSheet(
            liveClimb = createMockLiveClimbData(
                status = LiveClimbStatus.FINISHED,
                distanceToSummit = 0.0,
                remainingElevationGain = 0.0,
                currentProgressFraction = 1f
            )
        )
    }
}
