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

package com.atrainingtracker.trainingtracker.ui.map

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode
import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain
import com.atrainingtracker.trainingtracker.ui.aftermath.ActivityCarriageModeSummary
import com.atrainingtracker.trainingtracker.ui.aftermath.ActivityCarriageSegment
import com.atrainingtracker.trainingtracker.ui.aftermath.ActivityCarriageTimeline
import com.atrainingtracker.trainingtracker.ui.aftermath.carriageModeIcon
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.model.LatLng
import kotlin.math.abs

/**
 * Pure-JVM projection and active-mode lookup helpers for [ActivityCarriageSyncBar]
 * (`REQ-UI-2796` / `ATT-9172`).
 */
object ActivityCarriageBarMath {

    /**
     * A single coalesced carriage segment projected into the active chart X-axis domain
     * (meters for [ProfileXAxisDomain.DISTANCE], seconds for [ProfileXAxisDomain.TIME]).
     */
    data class ProjectedCarriageSegment(
        val mode: PhoneCarriageMode,
        val startDomain: Double,
        val endDomain: Double,
        val durationSeconds: Long,
        val distanceMeters: Double
    )

    /**
     * Projects [timeline] segments into the active horizontal domain `[0.0 .. totalSpan]` so
     * transitions align pixel-for-pixel with [ElevationProfile] and [TelemetryMetricGraph].
     */
    fun projectSegmentsToDomain(
        timeline: ActivityCarriageTimeline,
        isTimeDomain: Boolean,
        totalSpan: Double
    ): List<ProjectedCarriageSegment> {
        if (timeline.segments.isEmpty()) return emptyList()

        if (isTimeDomain) {
            val timelineDuration = timeline.totalDurationSeconds.toDouble().coerceAtLeast(1.0)
            val useDirectTimestamps = totalSpan <= 0.0 ||
                (timeline.startSec == 0L && abs(timeline.endSec.toDouble() - totalSpan) <= 5.0)
            val scale = if (useDirectTimestamps) 1.0 else totalSpan / timelineDuration
            val origin = if (useDirectTimestamps) 0.0 else timeline.startSec.toDouble()
            return timeline.segments.map { seg ->
                val start = ((seg.startSec.toDouble() - origin) * scale).coerceAtLeast(0.0)
                val end = ((seg.endSec.toDouble() - origin) * scale).coerceAtLeast(start)
                ProjectedCarriageSegment(
                    mode = seg.mode,
                    startDomain = start,
                    endDomain = end,
                    durationSeconds = seg.durationSeconds,
                    distanceMeters = seg.distanceMeters
                )
            }
        } else {
            val timelineDist = timeline.totalDistanceMeters
            if (timelineDist <= 0.0) {
                // Fallback for stationary/zero-distance timelines: distribute proportionally by duration across totalSpan.
                val effectiveSpan = totalSpan.coerceAtLeast(1.0)
                val totalDur = timeline.totalDurationSeconds.toDouble().coerceAtLeast(1.0)
                var cursor = 0.0
                return timeline.segments.map { seg ->
                    val width = (seg.durationSeconds.toDouble() / totalDur) * effectiveSpan
                    val start = cursor
                    val end = cursor + width
                    cursor = end
                    ProjectedCarriageSegment(
                        mode = seg.mode,
                        startDomain = start,
                        endDomain = end,
                        durationSeconds = seg.durationSeconds,
                        distanceMeters = seg.distanceMeters
                    )
                }
            }
            val scale = if (totalSpan > 0.0) totalSpan / timelineDist else 1.0
            var cursorDist = 0.0
            return timeline.segments.map { seg ->
                val start = cursorDist * scale
                cursorDist += seg.distanceMeters.coerceAtLeast(0.0)
                val end = (cursorDist * scale).coerceAtLeast(start)
                ProjectedCarriageSegment(
                    mode = seg.mode,
                    startDomain = start,
                    endDomain = end,
                    durationSeconds = seg.durationSeconds,
                    distanceMeters = seg.distanceMeters
                )
            }
        }
    }

    /**
     * Resolves the active horizontal domain coordinate of the scrubbing cursor, mirroring
     * [TelemetryMetricGraph] across GPS and trackless workouts.
     */
    fun resolveCursorDomainPosition(
        pathPoints: List<PathPoint>,
        currentDistance: Double?,
        isTimeDomain: Boolean,
        isTrackless: Boolean
    ): Double? {
        if (currentDistance == null || pathPoints.isEmpty()) return null
        return if (isTimeDomain && !isTrackless) {
            TelemetryMetricUtils.findNearestPoint(
                points = pathPoints,
                targetValue = currentDistance,
                isTimeDomain = false
            )?.timeSec?.toDouble()
        } else {
            currentDistance
        }
    }

    /**
     * Resolves the [PhoneCarriageMode] at [domainPosition] along [projected].
     */
    fun resolveModeAtDomainPosition(
        projected: List<ProjectedCarriageSegment>,
        domainPosition: Double?
    ): PhoneCarriageMode? {
        if (projected.isEmpty() || domainPosition == null || !domainPosition.isFinite()) return null
        if (domainPosition <= projected.first().startDomain) return projected.first().mode
        if (domainPosition >= projected.last().endDomain) return projected.last().mode
        val exactMatch = projected.firstOrNull { seg ->
            seg.endDomain > seg.startDomain &&
                domainPosition >= seg.startDomain &&
                domainPosition <= seg.endDomain
        }
        if (exactMatch != null) return exactMatch.mode
        return projected.minByOrNull { seg ->
            val mid = (seg.startDomain + seg.endDomain) * 0.5
            abs(mid - domainPosition)
        }?.mode
    }
}

/**
 * Synchronized, color-coded horizontal Activity & Carriage Mode segmented bar coupled 1:1 with
 * `MapDetailLayout`'s scrubbing cursor (`currentDistance`), zoom/pan viewport (`zoomScale`,
 * `startDist`), X-axis domain (`DISTANCE` / `TIME`), and `Rule 64` plot bounds (`startPaddingPx = 50.dp`)
 * (`REQ-UI-2796` / `ATT-9172`).
 */
@Composable
fun ActivityCarriageSyncBar(
    timeline: ActivityCarriageTimeline,
    pathPoints: List<PathPoint>,
    currentDistance: Double?,
    onDistanceSelected: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    xAxisDomain: ProfileXAxisDomain = ProfileXAxisDomain.DISTANCE,
    zoomScale: Float = 1.0f,
    startDist: Double = 0.0,
    onZoomChanged: ((Float, Double) -> Unit)? = null,
    isPanMode: Boolean = false,
    alignWithTelemetryCharts: Boolean = true,
    enableGestures: Boolean = true
) {
    if (timeline.segments.isEmpty() || timeline.totalDurationSeconds <= 0L || pathPoints.isEmpty()) return

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTrackless = remember(pathPoints) { ProfileDomainMath.isTracklessWorkout(pathPoints) }
    val isTimeDomain = remember(xAxisDomain, pathPoints) {
        ProfileDomainMath.isEffectiveTimeDomain(xAxisDomain, pathPoints)
    }
    val totalSpan = remember(xAxisDomain, pathPoints) {
        ProfileDomainMath.calculateTotalSpan(xAxisDomain, pathPoints).coerceAtLeast(1.0)
    }
    val projectedSegments = remember(timeline, isTimeDomain, totalSpan) {
        ActivityCarriageBarMath.projectSegmentsToDomain(
            timeline = timeline,
            isTimeDomain = isTimeDomain,
            totalSpan = totalSpan
        )
    }
    val cursorDomainPosition = remember(pathPoints, currentDistance, isTimeDomain, isTrackless) {
        ActivityCarriageBarMath.resolveCursorDomainPosition(
            pathPoints = pathPoints,
            currentDistance = currentDistance,
            isTimeDomain = isTimeDomain,
            isTrackless = isTrackless
        )
    }
    val activeScrubMode = remember(projectedSegments, cursorDomainPosition) {
        ActivityCarriageBarMath.resolveModeAtDomainPosition(projectedSegments, cursorDomainPosition)
    }

    val density = LocalDensity.current
    val startPaddingPx = with(density) { 50.dp.toPx() }
    val endPaddingPx = with(density) {
        if (isLandscape) 0.dp.toPx() else (if (alignWithTelemetryCharts) 52.dp else 25.dp).toPx()
    }

    // Lifecycle key stability (docs/design_guidelines.md §2.2): capture mutable state via rememberUpdatedState
    val currentZoomScaleState by rememberUpdatedState(zoomScale)
    val currentStartDistState by rememberUpdatedState(startDist)
    val currentIsPanModeState by rememberUpdatedState(isPanMode)
    val currentOnDistanceSelectedState by rememberUpdatedState(onDistanceSelected)
    val currentOnZoomChangedState by rememberUpdatedState(onZoomChanged)
    val currentPathPointsState by rememberUpdatedState(pathPoints)
    val currentIsTimeDomainState by rememberUpdatedState(isTimeDomain)
    val currentIsTracklessState by rememberUpdatedState(isTrackless)
    val currentTotalSpanState by rememberUpdatedState(totalSpan)

    val surfaceColor = MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val cursorColor = MaterialTheme.colorScheme.onSurface
    val cursorHaloColor = MaterialTheme.colorScheme.surface

    val barGestureModifier = if (enableGestures) {
        Modifier.pointerInput(startPaddingPx, endPaddingPx) {
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val chartWidthPx = (size.width - startPaddingPx - endPaddingPx).coerceAtLeast(1f)
                var isDragging = false
                var isVerticalScrolling = false
                var prevX = down.position.x
                var localStartDist = currentStartDistState

                fun dispatchScrubAtX(posX: Float) {
                    val activeSpan = currentTotalSpanState
                    val activeVisibleSpan = ElevationProfileZoomMath.calculateVisibleDistance(activeSpan, currentZoomScaleState)
                    val localX = (posX - startPaddingPx).coerceIn(0f, chartWidthPx)
                    val selectedVal = ElevationProfileZoomMath.canvasXToDistance(
                        canvasX = localX,
                        startDist = currentStartDistState,
                        visibleDist = activeVisibleSpan,
                        canvasWidth = chartWidthPx,
                        totalDist = activeSpan
                    )
                    if (currentIsTimeDomainState) {
                        val nearest = TelemetryMetricUtils.findNearestPoint(
                            points = currentPathPointsState,
                            targetValue = selectedVal,
                            isTimeDomain = true
                        )
                        if (currentIsTracklessState) {
                            currentOnDistanceSelectedState(nearest?.timeSec?.toDouble())
                        } else {
                            currentOnDistanceSelectedState(nearest?.distance)
                        }
                    } else {
                        currentOnDistanceSelectedState(selectedVal)
                    }
                }

                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break

                    if (pressed.size == 1) {
                        val pointer = pressed[0]
                        val diffX = pointer.position.x - down.position.x
                        val diffY = pointer.position.y - down.position.y

                        if (!isDragging && !isVerticalScrolling) {
                            if (ChartGestureDisambiguator.isDominantVertical(diffX, diffY, touchSlop)) {
                                isVerticalScrolling = true
                            } else if (ChartGestureDisambiguator.isDominantHorizontal(diffX, diffY, touchSlop)) {
                                isDragging = true
                            }
                        }

                        if (isDragging) {
                            val dragDeltaX = pointer.position.x - prevX
                            pointer.consume()
                            val activeZoom = currentZoomScaleState
                            val activeSpan = currentTotalSpanState
                            val activeVisibleSpan = ElevationProfileZoomMath.calculateVisibleDistance(activeSpan, activeZoom)
                            val onZoomChangedFn = currentOnZoomChangedState
                            if (currentIsPanModeState && activeSpan > 10.0 && onZoomChangedFn != null) {
                                val panStart = ElevationProfileZoomMath.applyPan(
                                    currentStartDist = localStartDist,
                                    visibleDist = activeVisibleSpan,
                                    panDeltaX = dragDeltaX,
                                    canvasWidth = chartWidthPx,
                                    totalDist = activeSpan
                                )
                                localStartDist = panStart
                                onZoomChangedFn(activeZoom, panStart)
                            } else {
                                dispatchScrubAtX(pointer.position.x)
                            }
                            prevX = pointer.position.x
                        } else if (isVerticalScrolling && pointer.isConsumed) {
                            break
                        }
                    }
                }

                if (isDragging) {
                    if (!currentIsPanModeState) {
                        currentOnDistanceSelectedState(null)
                    }
                } else if (!isVerticalScrolling && !currentIsPanModeState) {
                    dispatchScrubAtX(down.position.x)
                }
            }
        }
    } else {
        Modifier
    }

    val barCanvas: @Composable (Modifier) -> Unit = { canvasHostModifier ->
        Box(
            modifier = canvasHostModifier
                .height(34.dp)
                .then(barGestureModifier)
                .testTag("activity_carriage_sync_bar_canvas")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val plotWidth = size.width - startPaddingPx - endPaddingPx
                if (plotWidth <= 0f) return@Canvas
                val barTop = 7.dp.toPx()
                val barHeight = (size.height - 14.dp.toPx()).coerceAtLeast(12.dp.toPx())
                val cornerRadiusPx = 6.dp.toPx()
                val visibleSpan = ElevationProfileZoomMath.calculateVisibleDistance(totalSpan, zoomScale)
                val separatorWidthPx = 1.dp.toPx()
                val stopMarkerWidthPx = 2.5.dp.toPx()

                val plotRect = Rect(
                    left = startPaddingPx,
                    top = barTop,
                    right = startPaddingPx + plotWidth,
                    bottom = barTop + barHeight
                )
                val clipRoundRect = Path().apply {
                    addRoundRect(RoundRect(plotRect, CornerRadius(cornerRadiusPx, cornerRadiusPx)))
                }

                clipPath(clipRoundRect) {
                    // Base subtle background inside plot bounds
                    drawRect(
                        color = borderColor.copy(alpha = 0.25f),
                        topLeft = Offset(plotRect.left, plotRect.top),
                        size = Size(plotWidth, barHeight)
                    )

                    // 1. Render non-zero width segments clipped to [startDist .. startDist + visibleSpan]
                    projectedSegments.forEachIndexed { idx, seg ->
                        if (seg.endDomain <= seg.startDomain) return@forEachIndexed
                        val rawLeft = startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(
                            dist = seg.startDomain,
                            startDist = startDist,
                            visibleDist = visibleSpan,
                            canvasWidth = plotWidth
                        )
                        val rawRight = startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(
                            dist = seg.endDomain,
                            startDist = startDist,
                            visibleDist = visibleSpan,
                            canvasWidth = plotWidth
                        )
                        val clampedLeft = rawLeft.coerceIn(plotRect.left, plotRect.right)
                        val clampedRight = rawRight.coerceIn(plotRect.left, plotRect.right)
                        if (clampedRight > clampedLeft) {
                            drawRect(
                                color = TTColor.getCarriageModeColor(seg.mode),
                                topLeft = Offset(clampedLeft, barTop),
                                size = Size(clampedRight - clampedLeft, barHeight)
                            )
                            if (idx > 0 && rawLeft > plotRect.left && rawLeft < plotRect.right) {
                                drawLine(
                                    color = surfaceColor,
                                    start = Offset(rawLeft, barTop),
                                    end = Offset(rawLeft, barTop + barHeight),
                                    strokeWidth = separatorWidthPx
                                )
                            }
                        }
                    }

                    // 2. Render zero-distance stops (e.g. STATIONARY in DISTANCE domain) on top as crisp markers
                    val effectiveMarkerWidth = stopMarkerWidthPx.coerceAtMost(plotRect.width)
                    val maxMarkerLeft = (plotRect.right - effectiveMarkerWidth).coerceAtLeast(plotRect.left)
                    projectedSegments.forEach { seg ->
                        if (seg.endDomain > seg.startDomain) return@forEach
                        val x = startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(
                            dist = seg.startDomain,
                            startDist = startDist,
                            visibleDist = visibleSpan,
                            canvasWidth = plotWidth
                        )
                        if (x in plotRect.left..plotRect.right) {
                            drawRect(
                                color = TTColor.getCarriageModeColor(seg.mode),
                                topLeft = Offset((x - effectiveMarkerWidth / 2f).coerceIn(plotRect.left, maxMarkerLeft), barTop),
                                size = Size(effectiveMarkerWidth, barHeight)
                            )
                        }
                    }
                }

                // Crisp border around the segmented bar
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(plotRect.left, plotRect.top),
                    size = Size(plotWidth, barHeight),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    style = Stroke(width = 0.75.dp.toPx())
                )

                // Synchronized vertical scrubbing indicator matching ElevationProfile & TelemetryMetricGraph
                if (cursorDomainPosition != null &&
                    cursorDomainPosition >= startDist &&
                    cursorDomainPosition <= startDist + visibleSpan
                ) {
                    val cursorX = startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(
                        dist = cursorDomainPosition,
                        startDist = startDist,
                        visibleDist = visibleSpan,
                        canvasWidth = plotWidth
                    )
                    if (cursorX in plotRect.left..plotRect.right) {
                        val indicatorTop = 2.dp.toPx()
                        val indicatorBottom = size.height - 2.dp.toPx()
                        drawLine(
                            color = cursorHaloColor,
                            start = Offset(cursorX, indicatorTop),
                            end = Offset(cursorX, indicatorBottom),
                            strokeWidth = 4.dp.toPx()
                        )
                        drawLine(
                            color = cursorColor,
                            start = Offset(cursorX, indicatorTop),
                            end = Offset(cursorX, indicatorBottom),
                            strokeWidth = 2.dp.toPx()
                        )
                        drawCircle(
                            color = cursorColor,
                            radius = 3.dp.toPx(),
                            center = Offset(cursorX, indicatorTop + 1.5.dp.toPx())
                        )
                    }
                }
            }
        }
    }

    val headerRow: @Composable (Modifier) -> Unit = { headerModifier ->
        // Compact header with title + active scrubbed PhoneCarriageMode badge
        Row(
            modifier = headerModifier
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.carriage_timeline_card_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (activeScrubMode != null) {
                val modeColor = TTColor.getCarriageModeColor(activeScrubMode)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = modeColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, modeColor.copy(alpha = 0.55f)),
                    modifier = Modifier.testTag("activity_carriage_scrub_readout")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(modeColor)
                        )
                        Icon(
                            imageVector = carriageModeIcon(activeScrubMode),
                            contentDescription = null,
                            tint = modeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(activeScrubMode.labelResId),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp)
            .testTag("activity_carriage_sync_bar")
    ) {
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(0.72f)) {
                    headerRow(Modifier.fillMaxWidth())
                    barCanvas(Modifier.fillMaxWidth())
                }
                Spacer(modifier = Modifier.weight(0.28f))
            }
        } else {
            headerRow(Modifier.fillMaxWidth())
            barCanvas(Modifier.fillMaxWidth())
        }
    }
}

private fun sampleSyncBarTimeline(): ActivityCarriageTimeline = ActivityCarriageTimeline(
    segments = listOf(
        ActivityCarriageSegment(PhoneCarriageMode.BIKE_HANDLEBAR, 0L, 480L, 480L, 3800.0, 40f, 44.7f),
        ActivityCarriageSegment(PhoneCarriageMode.WALKING_TRANSITION, 480L, 600L, 120L, 200.0, 10f, 2.4f),
        ActivityCarriageSegment(PhoneCarriageMode.STATIONARY, 600L, 720L, 120L, 0.0, 10f, 0f),
        ActivityCarriageSegment(PhoneCarriageMode.BIKE_JERSEY_BACK, 720L, 1200L, 480L, 4500.0, 40f, 52.9f)
    ),
    modeSummaries = listOf(
        ActivityCarriageModeSummary(PhoneCarriageMode.BIKE_HANDLEBAR, 480L, 3800.0, 40f, 44.7f, 1),
        ActivityCarriageModeSummary(PhoneCarriageMode.BIKE_JERSEY_BACK, 480L, 4500.0, 40f, 52.9f, 1),
        ActivityCarriageModeSummary(PhoneCarriageMode.WALKING_TRANSITION, 120L, 200.0, 10f, 2.4f, 1),
        ActivityCarriageModeSummary(PhoneCarriageMode.STATIONARY, 120L, 0.0, 10f, 0f, 1)
    ),
    totalDurationSeconds = 1200L,
    totalDistanceMeters = 8500.0,
    startSec = 0L,
    endSec = 1200L
)

private fun sampleSyncBarPath(): List<PathPoint> = List(21) { idx ->
    PathPoint(
        distance = idx * 425.0,
        latLng = LatLng(48.10 + idx * 0.001, 11.50 + idx * 0.001),
        altitude = 520.0 + idx * 4.0,
        timeSec = idx * 60L
    )
}

@Preview(showBackground = true, name = "Light - Scrubbing Walking Phase")
@Composable
private fun ActivityCarriageSyncBarLightPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface {
            ActivityCarriageSyncBar(
                timeline = sampleSyncBarTimeline(),
                pathPoints = sampleSyncBarPath(),
                currentDistance = 3900.0,
                onDistanceSelected = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Dark - Scrubbing Bike Phase")
@Composable
private fun ActivityCarriageSyncBarDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface {
            ActivityCarriageSyncBar(
                timeline = sampleSyncBarTimeline(),
                pathPoints = sampleSyncBarPath(),
                currentDistance = 6000.0,
                onDistanceSelected = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "AMOLED - Idle")
@Composable
private fun ActivityCarriageSyncBarAmoledPreview() {
    ATrainingTrackerTheme(darkTheme = true, amoled = true) {
        Surface {
            ActivityCarriageSyncBar(
                timeline = sampleSyncBarTimeline(),
                pathPoints = sampleSyncBarPath(),
                currentDistance = null,
                onDistanceSelected = {}
            )
        }
    }
}
