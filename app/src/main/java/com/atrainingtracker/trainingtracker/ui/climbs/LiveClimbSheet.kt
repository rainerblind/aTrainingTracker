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

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.climbs.LiveClimbData
import com.atrainingtracker.trainingtracker.climbs.LiveClimbStatus
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

        // --- 5. Colored Elevation Profile Canvas ---
        ClimbProfileCanvas(
            liveClimb = liveClimb,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
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

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = stringResource(labelRes),
            color = textColor,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
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

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ClimbProfileCanvas(
    liveClimb: LiveClimbData,
    modifier: Modifier = Modifier
) {
    val pathPoints = liveClimb.climb.pathPoints
    val progressFraction = liveClimb.currentProgressFraction.coerceIn(0f, 1f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        if (pathPoints.size < 2) {
            // Draw a basic gradient ramp placeholder if path is empty
            val rampPath = Path().apply {
                moveTo(0f, height)
                lineTo(width, 0f)
                lineTo(width, height)
                close()
            }
            drawPath(
                path = rampPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFEF6C00).copy(alpha = 0.5f), Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )
            return@Canvas
        }

        val startDist = pathPoints.first().distance
        val totalDist = (pathPoints.last().distance - startDist).coerceAtLeast(1.0)
        val minAlt = pathPoints.minOf { it.altitude }
        val maxAlt = pathPoints.maxOf { it.altitude }
        val altSpan = (maxAlt - minAlt).coerceAtLeast(10.0)

        val topPadding = 6f
        val bottomPadding = 4f
        val usableHeight = height - topPadding - bottomPadding

        // 1. Draw segment fills and lines colored by gradient
        var prevX = 0f
        var prevY = height - bottomPadding - (((pathPoints[0].altitude - minAlt) / altSpan) * usableHeight).toFloat()

        for (i in 1 until pathPoints.size) {
            val p = pathPoints[i]
            val x = (((p.distance - startDist) / totalDist) * width).toFloat().coerceIn(0f, width)
            val y = (height - bottomPadding - (((p.altitude - minAlt) / altSpan) * usableHeight)).toFloat().coerceIn(0f, height)

            val dDist = p.distance - pathPoints[i - 1].distance
            val dAlt = p.altitude - pathPoints[i - 1].altitude
            val grade = if (dDist > 0) (dAlt / dDist) * 100.0 else 0.0

            val segmentColor = getGradeColor(grade)

            // Fill under segment
            val fillPath = Path().apply {
                moveTo(prevX, height)
                lineTo(prevX, prevY)
                lineTo(x, y)
                lineTo(x, height)
                close()
            }
            drawPath(
                path = fillPath,
                color = segmentColor.copy(alpha = 0.35f)
            )

            // Stroke line along segment
            drawLine(
                color = segmentColor,
                start = Offset(prevX, prevY),
                end = Offset(x, y),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            prevX = x
            prevY = y
        }

        // 2. Dynamic Rider Pin
        if (liveClimb.status == LiveClimbStatus.ON_CLIMB || liveClimb.status == LiveClimbStatus.FINISHED) {
            val riderX = (progressFraction * width).coerceIn(0f, width)

            // Interpolate altitude at riderX
            val riderDist = startDist + (progressFraction * totalDist)
            val nextIdx = pathPoints.indexOfFirst { it.distance >= riderDist }.let { if (it <= 0) 1 else it }
            val prevP = pathPoints[nextIdx - 1]
            val nextP = pathPoints[nextIdx]
            val segmentFrac = if (nextP.distance > prevP.distance) {
                ((riderDist - prevP.distance) / (nextP.distance - prevP.distance)).coerceIn(0.0, 1.0)
            } else 0.0
            val riderAlt = prevP.altitude + (segmentFrac * (nextP.altitude - prevP.altitude))
            val riderY = (height - bottomPadding - (((riderAlt - minAlt) / altSpan) * usableHeight)).toFloat().coerceIn(0f, height)

            // Vertical indicator line
            drawLine(
                color = Color.White.copy(alpha = 0.8f),
                start = Offset(riderX, riderY),
                end = Offset(riderX, height),
                strokeWidth = 1.5.dp.toPx()
            )

            // Outer white ring
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = Offset(riderX, riderY)
            )
            // Inner cyan indicator dot
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = 4.dp.toPx(),
                center = Offset(riderX, riderY)
            )
        }
    }
}

/**
 * Resolves standard slope gradient color bands (REQ-MAP-027):
 * - < 3%: Green
 * - 3% - 6%: Yellow-Green
 * - 6% - 9%: Amber/Yellow
 * - 9% - 12%: Orange
 * - 12% - 20%: Red
 * - > 20%: Dark Red / Black
 */
private fun getGradeColor(gradePercent: Double): Color {
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
