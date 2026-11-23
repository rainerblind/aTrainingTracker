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

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp

/**
 * Index lookup semantics preserved across sister post-workout telemetry charts (`REQ-UI-2803.1`):
 * - [FLOOR]: Greatest monotonic sample at or before the target coordinate (used by [ElevationProfile]).
 * - [NEAREST]: Nearest sample with earlier-point tie-break and trackless time-domain reporting
 *   (used by [TelemetryMetricGraph]).
 */
enum class ChartScrubIndexSemantics {
    FLOOR,
    NEAREST
}

/**
 * Result of resolving a horizontal chart gesture coordinate against a workout's [PathPoint] series.
 */
data class ResolvedChartScrubSelection(
    val point: PathPoint?,
    val reportedDistance: Double?
)

/**
 * Pure O(log N) binary-search helpers shared by [ElevationProfile], [TelemetryMetricGraph], and
 * [MapDetailLayout] (`REQ-UI-2803.1`, `REQ-UI-260`).
 */
object TelemetryChartSearch {

    /**
     * Binary-searches monotonically ordered [points] for the greatest index `i` whose domain
     * coordinate (`timeSec` when [isTimeDomain], otherwise `distance`) is `<= targetValue`,
     * clamped to `0` when [targetValue] precedes the first sample. Returns `-1` for an empty list.
     *
     * Guarantees exact numerical parity with `points.indexOfLast { coord <= targetValue }.coerceAtLeast(0)`
     * in O(log N) time.
     */
    fun findFloorIndex(
        points: List<PathPoint>,
        targetValue: Double,
        isTimeDomain: Boolean = false
    ): Int {
        val size = points.size
        if (size == 0) return -1
        if (size == 1) return 0

        fun coordAt(idx: Int): Double = if (isTimeDomain) points[idx].timeSec.toDouble() else points[idx].distance

        if (targetValue < coordAt(0)) return 0
        if (targetValue >= coordAt(size - 1)) return size - 1

        var low = 0
        var high = size - 1
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (coordAt(mid) <= targetValue) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    /**
     * Binary search returning the FIRST index `i` where `coord(i) >= targetValue`,
     * clamped to `0` when [targetValue] precedes or equals the first sample and to `lastIndex`
     * when [targetValue] exceeds all samples. Returns `-1` for an empty list.
     *
     * Used for viewport left-edge scan bounds so duplicate coordinates sharing `coord == startDist`
     * (e.g. stationary start at `0.0 m` or pauses at the left zoom edge) are never dropped.
     */
    fun findLowerBoundIndex(
        points: List<PathPoint>,
        targetValue: Double,
        isTimeDomain: Boolean = false
    ): Int {
        val size = points.size
        if (size == 0) return -1
        if (size == 1) return 0

        fun coordAt(idx: Int): Double = if (isTimeDomain) points[idx].timeSec.toDouble() else points[idx].distance

        if (targetValue <= coordAt(0)) return 0
        if (targetValue > coordAt(size - 1)) return size - 1

        var low = 0
        var high = size - 1
        var result = size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (coordAt(mid) >= targetValue) {
                result = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return result
    }

    /**
     * Resolves a scrub target coordinate [selectedDomainValue] to a [ResolvedChartScrubSelection],
     * preserving exact [ChartScrubIndexSemantics.FLOOR] (`ElevationProfile`) vs.
     * [ChartScrubIndexSemantics.NEAREST] + trackless (`TelemetryMetricGraph`) contracts.
     */
    fun resolveScrubSelection(
        points: List<PathPoint>,
        selectedDomainValue: Double,
        isTimeDomain: Boolean,
        isTrackless: Boolean,
        semantics: ChartScrubIndexSemantics
    ): ResolvedChartScrubSelection {
        return when (semantics) {
            ChartScrubIndexSemantics.FLOOR -> {
                val activeIndex = findFloorIndex(points, selectedDomainValue, isTimeDomain = isTimeDomain)
                val activePoint = if (activeIndex >= 0) points[activeIndex] else points.firstOrNull()
                ResolvedChartScrubSelection(
                    point = activePoint,
                    reportedDistance = activePoint?.distance ?: selectedDomainValue
                )
            }
            ChartScrubIndexSemantics.NEAREST -> {
                val nearest = TelemetryMetricUtils.findNearestPoint(
                    points = points,
                    targetValue = selectedDomainValue,
                    isTimeDomain = isTimeDomain
                )
                val reported = if (isTimeDomain) {
                    if (isTrackless) {
                        nearest?.timeSec?.toDouble()
                    } else {
                        nearest?.distance
                    }
                } else {
                    selectedDomainValue
                }
                ResolvedChartScrubSelection(
                    point = nearest,
                    reportedDistance = reported
                )
            }
        }
    }
}

private class ChartTapTimestampHolder(var lastTapMs: Long = 0L)

/**
 * Unified chart touch-gesture modifier shared across [ElevationProfile] and [TelemetryMetricGraph]
 * (`REQ-UI-2803.1`, `REQ-UI-192`, `REQ-UI-226`).
 *
 * Encapsulates:
 * - Two-finger centroid-anchored pinch-to-zoom + simultaneous two-finger pan.
 * - Single-finger directional slope disambiguation via [ChartGestureDisambiguator] so dominant
 *   vertical drags yield unconsumed to parent vertical scroll containers.
 * - Single-finger horizontal pan (when [isPanMode] is true) or O(log N) scrub (when false).
 * - Single-tap inspection and double-tap zoom reset (`1.0f`, `0.0`) within [doubleTapTimeoutMs].
 *
 * Mutable state is captured via [rememberUpdatedState] so `pointerInput` coroutines are never
 * cancelled mid-drag by zoom/pan state updates (`Design Guidelines §2.2`).
 */
@Composable
fun Modifier.telemetryChartGestures(
    enabled: Boolean,
    totalSpan: Double,
    isTimeDomain: Boolean,
    isTrackless: Boolean = false,
    isPanMode: Boolean,
    startPadding: Dp,
    endPadding: Dp,
    zoomScale: Float,
    startDist: Double,
    pathPoints: List<PathPoint>,
    indexSemantics: ChartScrubIndexSemantics,
    onZoomChanged: ((zoomScale: Float, startDist: Double) -> Unit)?,
    onDistanceSelected: (Double?) -> Unit,
    onPointSelected: ((PathPoint?) -> Unit)? = null,
    doubleTapTimeoutMs: Long = 350L,
    clockMs: () -> Long = System::currentTimeMillis
): Modifier {
    if (!enabled) return this

    val currentZoomScaleState by rememberUpdatedState(zoomScale)
    val currentStartDistState by rememberUpdatedState(startDist)
    val currentOnZoomChangedState by rememberUpdatedState(onZoomChanged)
    val currentOnDistanceSelectedState by rememberUpdatedState(onDistanceSelected)
    val currentOnPointSelectedState by rememberUpdatedState(onPointSelected)
    val currentPathPointsState by rememberUpdatedState(pathPoints)
    val currentIsTracklessState by rememberUpdatedState(isTrackless)
    val currentStartPaddingState by rememberUpdatedState(startPadding)
    val currentEndPaddingState by rememberUpdatedState(endPadding)
    val currentClockMsState by rememberUpdatedState(clockMs)
    val tapHolder = remember { ChartTapTimestampHolder() }

    return this.pointerInput(totalSpan, isTimeDomain, isPanMode, indexSemantics) {
        val touchSlop = viewConfiguration.touchSlop

        awaitEachGesture {
            val startPaddingPx = currentStartPaddingState.toPx()
            val endPaddingPx = currentEndPaddingState.toPx()
            val chartWidthPx = (size.width - startPaddingPx - endPaddingPx).coerceAtLeast(1f)

            val down = awaitFirstDown(requireUnconsumed = false)
            var prevCentroid = down.position
            var prevSpan = 0f
            var isTransforming = false
            var isDragging = false
            var isVerticalScrolling = false
            var localStartDist = currentStartDistState
            var localZoom = currentZoomScaleState

            fun dispatchScrubAt(rawX: Float) {
                val activeVisibleSpan = ElevationProfileZoomMath.calculateVisibleDistance(
                    totalDist = totalSpan,
                    zoomScale = currentZoomScaleState
                )
                val adjustedX = (rawX - startPaddingPx).coerceIn(0f, chartWidthPx)
                val selectedValue = ElevationProfileZoomMath.canvasXToDistance(
                    canvasX = adjustedX,
                    startDist = currentStartDistState,
                    visibleDist = activeVisibleSpan,
                    canvasWidth = chartWidthPx,
                    totalDist = totalSpan
                )
                val resolved = TelemetryChartSearch.resolveScrubSelection(
                    points = currentPathPointsState,
                    selectedDomainValue = selectedValue,
                    isTimeDomain = isTimeDomain,
                    isTrackless = currentIsTracklessState,
                    semantics = indexSemantics
                )
                currentOnPointSelectedState?.invoke(resolved.point)
                currentOnDistanceSelectedState(resolved.reportedDistance)
            }

            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break

                val zoomCallback = currentOnZoomChangedState
                if (pressed.size >= 2 &&
                    totalSpan > ElevationProfileZoomMath.MIN_TRACK_DIST_FOR_ZOOM &&
                    zoomCallback != null
                ) {
                    isTransforming = true
                    val p1 = pressed[0].position
                    val p2 = pressed[1].position
                    val centroid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                    val span = (p1 - p2).getDistance()

                    if (prevSpan > 0f && span > 0f) {
                        val zoomFactor = span / prevSpan
                        val panDeltaX = centroid.x - prevCentroid.x
                        val adjustedCentroidX = (centroid.x - startPaddingPx).coerceIn(0f, chartWidthPx)

                        val (newZoom, newStart) = ElevationProfileZoomMath.applyZoomAtCentroid(
                            totalDist = totalSpan,
                            currentZoom = localZoom,
                            targetZoom = localZoom * zoomFactor,
                            centroidX = adjustedCentroidX,
                            canvasWidth = chartWidthPx,
                            currentStartDist = localStartDist
                        )
                        val panStart = ElevationProfileZoomMath.applyPan(
                            currentStartDist = newStart,
                            visibleDist = ElevationProfileZoomMath.calculateVisibleDistance(totalSpan, newZoom),
                            panDeltaX = panDeltaX,
                            canvasWidth = chartWidthPx,
                            totalDist = totalSpan
                        )
                        localZoom = newZoom
                        localStartDist = panStart
                        zoomCallback(newZoom, panStart)
                    }
                    prevSpan = span
                    prevCentroid = centroid
                    pressed.forEach { it.consume() }
                } else if (pressed.size == 1 && !isTransforming) {
                    val pointer = pressed[0]
                    val diffX = pointer.position.x - down.position.x
                    val diffY = pointer.position.y - down.position.y

                    if (!isDragging && !isVerticalScrolling) {
                        if (ChartGestureDisambiguator.isDominantVertical(diffX, diffY, touchSlop)) {
                            isVerticalScrolling = true
                        } else if (ChartGestureDisambiguator.isDominantHorizontal(diffX, diffY, touchSlop)) {
                            isDragging = true
                            prevCentroid = pointer.position
                        }
                    }

                    if (isDragging) {
                        val dragDeltaX = pointer.position.x - prevCentroid.x
                        pointer.consume()
                        val activeZoom = currentZoomScaleState
                        val activeVisibleSpan = ElevationProfileZoomMath.calculateVisibleDistance(totalSpan, activeZoom)
                        if (isPanMode &&
                            totalSpan > ElevationProfileZoomMath.MIN_TRACK_DIST_FOR_ZOOM &&
                            zoomCallback != null
                        ) {
                            val panStart = ElevationProfileZoomMath.applyPan(
                                currentStartDist = localStartDist,
                                visibleDist = activeVisibleSpan,
                                panDeltaX = dragDeltaX,
                                canvasWidth = chartWidthPx,
                                totalDist = totalSpan
                            )
                            localStartDist = panStart
                            zoomCallback(activeZoom, panStart)
                        } else {
                            dispatchScrubAt(pointer.position.x)
                        }
                        prevCentroid = pointer.position
                    } else if (isVerticalScrolling && pointer.isConsumed) {
                        break
                    }
                }
            }

            if (!isTransforming) {
                if (isDragging) {
                    if (!isPanMode) {
                        currentOnDistanceSelectedState(null)
                        currentOnPointSelectedState?.invoke(null)
                    }
                } else if (!isVerticalScrolling) {
                    val now = currentClockMsState()
                    val zoomCallback = currentOnZoomChangedState
                    if (now - tapHolder.lastTapMs < doubleTapTimeoutMs &&
                        totalSpan > ElevationProfileZoomMath.MIN_TRACK_DIST_FOR_ZOOM &&
                        zoomCallback != null
                    ) {
                        zoomCallback(ElevationProfileZoomMath.MIN_ZOOM, 0.0)
                        tapHolder.lastTapMs = 0L
                        currentOnDistanceSelectedState(null)
                        currentOnPointSelectedState?.invoke(null)
                    } else {
                        tapHolder.lastTapMs = now
                        if (!isPanMode) {
                            dispatchScrubAt(down.position.x)
                        }
                    }
                }
            }
        }
    }
}
