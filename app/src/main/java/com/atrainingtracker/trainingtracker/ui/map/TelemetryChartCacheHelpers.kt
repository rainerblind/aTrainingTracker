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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import kotlin.math.abs

internal data class TelemetryPlotPoint(
    val offset: Offset,
    val value: Double
)

internal data class TelemetryGapInterval(
    val startX: Float,
    val endX: Float
)

internal data class CachedTelemetrySingleDot(
    val center: Offset,
    val color: Color
)

internal data class CachedOverlayCurvePath(
    val path: Path,
    val color: Color,
    val stroke: Stroke
)

/**
 * Precomputed, viewport-scoped curve paths, brushes, strokes, and overlays built inside
 * `Modifier.drawWithCache` for [TelemetryMetricGraph] (`REQ-UI-2803.1`).
 *
 * Scrubbing `currentDistance` at 60 Hz replays these cached [Path]/[Brush]/[PathEffect] objects
 * without iterating raw track points or allocating draw objects.
 */
internal data class CachedTelemetryCurveGeometry(
    val strokePaths: List<Path>,
    val fillPaths: List<Path>,
    val singleDots: List<CachedTelemetrySingleDot>,
    val gapIntervals: List<TelemetryGapInterval>,
    val fillBrush: Brush,
    val strokeBrush: Brush?,
    val curveStroke: Stroke,
    val thresholdDashEffect: PathEffect,
    val cursorDashEffect: PathEffect,
    val averageDotEffect: PathEffect,
    val calibWindowBounds: Pair<Float, Float>?,
    val driftPolygons: List<Path>,
    val freshModelPath: Path?,
    val freshModelStroke: Stroke?,
    val overlayCurves: List<CachedOverlayCurvePath>,
    val plottedPointCount: Int
)

internal data class CachedElevationColorRun(
    val color: Color,
    val fillPath: Path,
    val strokePath: Path
)

/**
 * Precomputed coalesced fill and stroke paths per contiguous slope-grade color run built inside
 * `Modifier.drawWithCache` for [ElevationProfile] (`REQ-UI-2803.1`).
 */
internal data class CachedElevationCurveGeometry(
    val runs: List<CachedElevationColorRun>,
    val stroke: Stroke,
    val overlayCurves: List<CachedOverlayCurvePath>
)

/**
 * Pure viewport clipping and min-max envelope downsampling for contiguous telemetry curve segments
 * (`REQ-UI-2803.1`).
 *
 * Preserves:
 * 1. Exact 1-point left/right boundary anchors outside `[plotStartX, plotEndX]` so zoomed curves
 *    connect seamlessly to both clip edges.
 * 2. Exact segment start/end points and per-bucket minimum + maximum values (in chronological X
 *    order) whenever a segment contains more points than `maxBuckets * 2`, guaranteeing spikes and
 *    valleys remain visually faithful while capping per-frame path vertices.
 */
internal fun downsampleSegmentMinMax(
    segment: List<TelemetryPlotPoint>,
    plotStartX: Float,
    plotEndX: Float,
    maxBuckets: Int
): List<TelemetryPlotPoint> {
    if (segment.size <= 2 || maxBuckets <= 0) return segment

    // Narrow to visible X range with 1 anchor point on each side to preserve boundary line slopes.
    var firstVisible = -1
    var lastVisible = -1
    for (i in segment.indices) {
        val x = segment[i].offset.x
        if (x in plotStartX..plotEndX) {
            if (firstVisible == -1) firstVisible = i
            lastVisible = i
        }
    }

    val sliceStart: Int
    val sliceEnd: Int
    if (firstVisible == -1) {
        // Segment may span completely across [plotStartX, plotEndX] (e.g. sparse points zoomed in).
        val crossesViewport = segment.first().offset.x < plotStartX && segment.last().offset.x > plotEndX
        if (!crossesViewport) return emptyList()
        var leftAnchor = 0
        var rightAnchor = segment.lastIndex
        for (i in segment.indices) {
            if (segment[i].offset.x <= plotStartX) leftAnchor = i
            if (segment[i].offset.x >= plotEndX) {
                rightAnchor = i
                break
            }
        }
        sliceStart = leftAnchor
        sliceEnd = rightAnchor
    } else {
        sliceStart = (firstVisible - 1).coerceAtLeast(0)
        sliceEnd = (lastVisible + 1).coerceAtMost(segment.lastIndex)
    }

    val visibleCount = sliceEnd - sliceStart + 1
    if (visibleCount <= maxBuckets * 2 + 2) {
        return if (sliceStart == 0 && sliceEnd == segment.lastIndex) {
            segment
        } else {
            segment.subList(sliceStart, sliceEnd + 1)
        }
    }

    val result = ArrayList<TelemetryPlotPoint>(maxBuckets * 2 + 2)
    result.add(segment[sliceStart])

    val interiorStart = sliceStart + 1
    val interiorEnd = sliceEnd - 1
    val interiorCount = interiorEnd - interiorStart + 1
    val bucketSize = interiorCount.toDouble() / maxBuckets.toDouble()

    for (b in 0 until maxBuckets) {
        val bStart = interiorStart + (b * bucketSize).toInt()
        val bEnd = (interiorStart + ((b + 1) * bucketSize).toInt() - 1).coerceIn(bStart, interiorEnd)
        var minIdx = bStart
        var maxIdx = bStart
        var minY = segment[bStart].offset.y
        var maxY = minY
        for (i in (bStart + 1)..bEnd) {
            val y = segment[i].offset.y
            if (y < minY) {
                minY = y
                minIdx = i
            }
            if (y > maxY) {
                maxY = y
                maxIdx = i
            }
        }
        if (minIdx == maxIdx) {
            result.add(segment[minIdx])
        } else if (minIdx < maxIdx) {
            result.add(segment[minIdx])
            result.add(segment[maxIdx])
        } else {
            result.add(segment[maxIdx])
            result.add(segment[minIdx])
        }
    }

    result.add(segment[sliceEnd])
    return result
}

/**
 * Builds [CachedTelemetryCurveGeometry] for [TelemetryMetricGraph] inside `Modifier.drawWithCache`.
 */
internal fun buildCachedTelemetryCurveGeometry(
    pathPoints: List<PathPoint>,
    rawValues: List<Double?>,
    metricType: TelemetryMetricType,
    isTimeDomain: Boolean,
    startDist: Double,
    visibleSpan: Double,
    dataMin: Double,
    dataMax: Double,
    startPaddingPx: Float,
    topPaddingPx: Float,
    chartWidthPx: Float,
    chartHeightPx: Float,
    densityPxPerDp: Float,
    accentColor: Color,
    useZoneColoredCurve: Boolean,
    zoneStrokeStops: List<Pair<Float, Color>>,
    zoneFillStops: List<Pair<Float, Color>>,
    effectiveHrThresholds: HeartRateZoneThresholds?,
    effectivePowerThresholds: PowerZoneThresholds?,
    dfaA1ThresholdT1: Float,
    dfaA1ThresholdT2: Float,
    isDarkSurface: Boolean,
    canRenderFreshModelOverlay: Boolean,
    effectiveShowFreshModel: Boolean,
    freshHrTrajectory: List<Double?>,
    activeOverlaySeries: List<TelemetrySourceSeries>,
    allOverlaySeries: List<TelemetrySourceSeries>,
    unit: MyUnits,
    paceCeilingMinKm: Float
): CachedTelemetryCurveGeometry {
    val isInvertedYAxis = TelemetryZoneMath.isInvertedTelemetryYAxis(metricType)
    val isHrvMetric = metricType == TelemetryMetricType.HRV_DFA_A1 || metricType == TelemetryMetricType.HRV_RMSSD
    val baselineY = topPaddingPx + chartHeightPx
    val plotEndX = startPaddingPx + chartWidthPx
    val maxBuckets = (chartWidthPx.toInt() / 2).coerceIn(64, 400)

    fun valueToY(v: Double): Float = TelemetryZoneMath.mapTelemetryValueToY(
        value = v,
        dataMin = dataMin,
        dataMax = dataMax,
        topPaddingPx = topPaddingPx,
        chartHeightPx = chartHeightPx,
        isInverted = isInvertedYAxis
    )

    val rawSegments = mutableListOf<MutableList<TelemetryPlotPoint>>()
    var currentSegment = mutableListOf<TelemetryPlotPoint>()
    val gapIntervals = mutableListOf<TelemetryGapInterval>()
    var inGap = false
    var gapStartX = startPaddingPx

    val visibleEnd = startDist + visibleSpan
    // Narrow raw scan to viewport + 1 anchor sample when not tracking HRV full-range gaps
    val scanStartIdx: Int
    val scanEndIdx: Int
    if (!isHrvMetric && pathPoints.size > 4) {
        val leftLower = TelemetryChartSearch.findLowerBoundIndex(pathPoints, startDist, isTimeDomain = isTimeDomain)
        val rightFloor = TelemetryChartSearch.findFloorIndex(pathPoints, visibleEnd, isTimeDomain = isTimeDomain)
        scanStartIdx = (leftLower - 1).coerceAtLeast(0)
        scanEndIdx = (rightFloor + 2).coerceAtMost(pathPoints.lastIndex)
    } else {
        scanStartIdx = 0
        scanEndIdx = pathPoints.lastIndex
    }

    for (i in scanStartIdx..scanEndIdx) {
        val pt = pathPoints[i]
        val v = rawValues[i]
        val isArtifact = isHrvMetric && (pt.hrvArtifactRate != null && pt.hrvArtifactRate > 5.0)
        val isValid = v != null && !isArtifact

        val xSpan = if (isTimeDomain) pt.timeSec.toDouble() else pt.distance
        val x = startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(xSpan, startDist, visibleSpan, chartWidthPx)

        if (isValid) {
            val y = valueToY(v)
            if (inGap) {
                gapIntervals.add(TelemetryGapInterval(gapStartX, x))
                inGap = false
            }
            currentSegment.add(TelemetryPlotPoint(Offset(x, y), v))
        } else {
            if (!inGap) {
                gapStartX = currentSegment.lastOrNull()?.offset?.x ?: x
                inGap = true
            }
            if (currentSegment.isNotEmpty()) {
                rawSegments.add(currentSegment)
                currentSegment = mutableListOf()
            }
        }
    }
    if (currentSegment.isNotEmpty()) {
        rawSegments.add(currentSegment)
    } else if (inGap && isHrvMetric) {
        gapIntervals.add(TelemetryGapInterval(gapStartX, plotEndX))
    }

    val fillBaselineY = if (metricType == TelemetryMetricType.SLOPE && 0.0 in dataMin..dataMax) {
        valueToY(0.0)
    } else {
        baselineY
    }

    val strokePaths = mutableListOf<Path>()
    val fillPaths = mutableListOf<Path>()
    val singleDots = mutableListOf<CachedTelemetrySingleDot>()
    var plottedCount = 0

    for (rawSeg in rawSegments) {
        if (rawSeg.isEmpty()) continue
        if (rawSeg.size == 1) {
            val pt = rawSeg[0]
            if (pt.offset.x in startPaddingPx..plotEndX) {
                val dotColor = TelemetryZoneMath.resolveMetricZoneStrokeColor(
                    metricType = metricType,
                    value = pt.value,
                    hrThresholds = effectiveHrThresholds,
                    powerThresholds = effectivePowerThresholds,
                    dfaA1ThresholdT1 = dfaA1ThresholdT1,
                    dfaA1ThresholdT2 = dfaA1ThresholdT2,
                    isDarkSurface = isDarkSurface
                ) ?: accentColor
                singleDots.add(CachedTelemetrySingleDot(pt.offset, dotColor))
                plottedCount++
            }
            continue
        }

        val seg = downsampleSegmentMinMax(
            segment = rawSeg,
            plotStartX = startPaddingPx,
            plotEndX = plotEndX,
            maxBuckets = maxBuckets
        )
        if (seg.size < 2) continue
        plottedCount += seg.size

        val strokePath = Path()
        val fillPath = Path()
        val firstPt = seg.first().offset
        strokePath.moveTo(firstPt.x, firstPt.y)
        fillPath.moveTo(firstPt.x, fillBaselineY)
        fillPath.lineTo(firstPt.x, firstPt.y)
        for (j in 1 until seg.size) {
            val pt = seg[j].offset
            strokePath.lineTo(pt.x, pt.y)
            fillPath.lineTo(pt.x, pt.y)
        }
        val lastPt = seg.last().offset
        fillPath.lineTo(lastPt.x, fillBaselineY)
        fillPath.close()

        strokePaths.add(strokePath)
        fillPaths.add(fillPath)
    }

    val fillBrush = if (useZoneColoredCurve) {
        Brush.verticalGradient(
            colorStops = zoneFillStops.toTypedArray(),
            startY = topPaddingPx,
            endY = baselineY
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.20f),
                accentColor.copy(alpha = 0.02f)
            ),
            startY = topPaddingPx,
            endY = baselineY
        )
    }

    val strokeBrush = if (useZoneColoredCurve) {
        Brush.verticalGradient(
            colorStops = zoneStrokeStops.toTypedArray(),
            startY = topPaddingPx,
            endY = baselineY
        )
    } else {
        null
    }

    val curveStroke = Stroke(width = 2f * densityPxPerDp, cap = StrokeCap.Round)
    val thresholdDashEffect = PathEffect.dashPathEffect(floatArrayOf(6f * densityPxPerDp, 6f * densityPxPerDp), 0f)
    val cursorDashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
    val averageDotEffect = PathEffect.dashPathEffect(floatArrayOf(0.5f * densityPxPerDp, 5f * densityPxPerDp), 0f)

    // Pre-build Fresh Model HR overlay & cardiac drift polygons (REQ-REV-010)
    var calibWindowBounds: Pair<Float, Float>? = null
    val driftPolygons = mutableListOf<Path>()
    var freshModelPath: Path? = null
    var freshModelStroke: Stroke? = null

    if (canRenderFreshModelOverlay && effectiveShowFreshModel && freshHrTrajectory.isNotEmpty()) {
        val calibStartX = if (isTimeDomain) {
            startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(480.0, startDist, visibleSpan, chartWidthPx)
        } else {
            val pt480 = pathPoints.firstOrNull { it.timeSec >= 480L } ?: pathPoints.firstOrNull()
            if (pt480 != null) {
                startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(pt480.distance, startDist, visibleSpan, chartWidthPx)
            } else startPaddingPx
        }
        val calibEndX = if (isTimeDomain) {
            startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(3300.0, startDist, visibleSpan, chartWidthPx)
        } else {
            val pt3300 = pathPoints.firstOrNull { it.timeSec >= 3300L } ?: pathPoints.lastOrNull()
            if (pt3300 != null) {
                startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(pt3300.distance, startDist, visibleSpan, chartWidthPx)
            } else plotEndX
        }
        val leftX = maxOf(startPaddingPx, minOf(calibStartX, calibEndX))
        val rightX = minOf(plotEndX, maxOf(calibStartX, calibEndX))
        if (rightX > leftX) {
            calibWindowBounds = leftX to rightX
        }

        val driftActualPts = mutableListOf<Offset>()
        val driftFreshPts = mutableListOf<Offset>()
        fun flushDrift() {
            if (driftActualPts.size >= 2) {
                val driftPoly = Path()
                driftPoly.moveTo(driftActualPts.first().x, driftActualPts.first().y)
                for (j in 1 until driftActualPts.size) {
                    driftPoly.lineTo(driftActualPts[j].x, driftActualPts[j].y)
                }
                for (j in driftFreshPts.indices.reversed()) {
                    driftPoly.lineTo(driftFreshPts[j].x, driftFreshPts[j].y)
                }
                driftPoly.close()
                driftPolygons.add(driftPoly)
            }
            driftActualPts.clear()
            driftFreshPts.clear()
        }

        val builtFreshPath = Path()
        var freshStarted = false
        for (i in scanStartIdx..scanEndIdx) {
            val pt = pathPoints[i]
            val act = rawValues[i]
            val fr = freshHrTrajectory.getOrNull(i)
            val xSpan = if (isTimeDomain) pt.timeSec.toDouble() else pt.distance
            val x = startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(xSpan, startDist, visibleSpan, chartWidthPx)

            if (fr != null) {
                val yFr = valueToY(fr)
                if (!freshStarted) {
                    builtFreshPath.moveTo(x, yFr)
                    freshStarted = true
                } else {
                    builtFreshPath.lineTo(x, yFr)
                }
                if (act != null) {
                    val yAct = valueToY(act)
                    if (act > fr) {
                        driftActualPts.add(Offset(x, yAct))
                        driftFreshPts.add(Offset(x, yFr))
                    } else if (driftActualPts.isNotEmpty()) {
                        driftActualPts.add(Offset(x, yAct))
                        driftFreshPts.add(Offset(x, yFr))
                        flushDrift()
                    }
                } else if (driftActualPts.isNotEmpty()) {
                    flushDrift()
                }
            } else if (driftActualPts.isNotEmpty()) {
                flushDrift()
            }
        }
        flushDrift()
        if (freshStarted) {
            freshModelPath = builtFreshPath
            freshModelStroke = Stroke(
                width = 2f * densityPxPerDp,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * densityPxPerDp, 4f * densityPxPerDp), 0f),
                cap = StrokeCap.Round
            )
        }
    }

    // Pre-build multi-sensor overlay paths
    val overlayCurves = if (activeOverlaySeries.isEmpty()) {
        emptyList()
    } else {
        buildList {
            for (series in activeOverlaySeries) {
                val colorIdx = allOverlaySeries.indexOfFirst { it.columnName == series.columnName }.coerceAtLeast(0)
                val color = overlayStrokeColor(colorIdx, isDarkSurface = isDarkSurface, avoidZoneColors = useZoneColoredCurve)
                val dashLength = (if (colorIdx % 2 == 0) 6f else 3f) * densityPxPerDp
                val gapLength = 3f * densityPxPerDp
                val path = Path()
                var started = false
                var hasAnyPoint = false
                for (pt in series.points) {
                    val chartValue = resolveOverlayValueForChart(pt.value, metricType, unit, paceCeilingMinKm)
                    if (chartValue == null) {
                        if (metricType == TelemetryMetricType.PACE) {
                            started = false
                        }
                        continue
                    }
                    val xSpan = if (isTimeDomain) pt.timeSec.toDouble() else pt.distance
                    val x = startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(
                        xSpan,
                        startDist,
                        visibleSpan,
                        chartWidthPx
                    )
                    val y = valueToY(chartValue)
                    if (!started) {
                        path.moveTo(x, y)
                        started = true
                    } else {
                        path.lineTo(x, y)
                    }
                    hasAnyPoint = true
                }
                if (hasAnyPoint) {
                    add(
                        CachedOverlayCurvePath(
                            path = path,
                            color = color,
                            stroke = Stroke(
                                width = 1.8f * densityPxPerDp,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength, gapLength), 0f),
                                cap = StrokeCap.Round
                            )
                        )
                    )
                }
            }
        }
    }

    return CachedTelemetryCurveGeometry(
        strokePaths = strokePaths,
        fillPaths = fillPaths,
        singleDots = singleDots,
        gapIntervals = gapIntervals,
        fillBrush = fillBrush,
        strokeBrush = strokeBrush,
        curveStroke = curveStroke,
        thresholdDashEffect = thresholdDashEffect,
        cursorDashEffect = cursorDashEffect,
        averageDotEffect = averageDotEffect,
        calibWindowBounds = calibWindowBounds,
        driftPolygons = driftPolygons,
        freshModelPath = freshModelPath,
        freshModelStroke = freshModelStroke,
        overlayCurves = overlayCurves,
        plottedPointCount = plottedCount
    )
}

/**
 * Builds coalesced fill + stroke [Path] runs per contiguous slope-grade color for [ElevationProfile]
 * inside `Modifier.drawWithCache` (`REQ-UI-2803.1`, `REQ-UI-2793.1`).
 */
internal fun buildCachedElevationCurveGeometry(
    segments: List<ElevationSegment>,
    isTimeDomain: Boolean,
    currentStartDist: Double,
    visibleSpan: Double,
    minAlt: Double,
    altRange: Double,
    width: Float,
    height: Float,
    densityPxPerDp: Float,
    activeOverlaySeries: List<TelemetrySourceSeries>,
    allOverlaySeries: List<TelemetrySourceSeries>
): CachedElevationCurveGeometry {
    val endDist = currentStartDist + visibleSpan
    val runs = mutableListOf<CachedElevationColorRun>()

    var runColor: Color? = null
    var runFillPath: Path? = null
    var runStrokePath: Path? = null
    var runLastX = 0f
    var runLastY = 0f

    fun flushRun() {
        val color = runColor
        val fill = runFillPath
        val stroke = runStrokePath
        if (color != null && fill != null && stroke != null) {
            fill.lineTo(runLastX, height)
            fill.close()
            runs.add(CachedElevationColorRun(color = color, fillPath = fill, strokePath = stroke))
        }
        runColor = null
        runFillPath = null
        runStrokePath = null
    }

    for (i in segments.indices) {
        val seg = segments[i]
        val v1 = if (isTimeDomain) seg.timeSec1.toDouble() else seg.dist1
        val v2 = if (isTimeDomain) seg.timeSec2.toDouble() else seg.dist2
        if (v2 < currentStartDist || v1 > endDist) continue

        val x1 = ElevationProfileZoomMath.distanceToCanvasX(v1, currentStartDist, visibleSpan, width)
        val y1 = height - (seg.altNorm1 * height)
        val x2 = ElevationProfileZoomMath.distanceToCanvasX(v2, currentStartDist, visibleSpan, width)
        val y2 = height - (seg.altNorm2 * height)

        val isContiguous = runColor == seg.color &&
            runFillPath != null &&
            abs(x1 - runLastX) <= 0.01f &&
            abs(y1 - runLastY) <= 0.01f

        if (!isContiguous) {
            flushRun()
            val fill = Path().apply {
                moveTo(x1, height)
                lineTo(x1, y1)
                lineTo(x2, y2)
            }
            val stroke = Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
            }
            runColor = seg.color
            runFillPath = fill
            runStrokePath = stroke
            runLastX = x2
            runLastY = y2
        } else {
            runFillPath?.lineTo(x2, y2)
            runStrokePath?.lineTo(x2, y2)
            runLastX = x2
            runLastY = y2
        }
    }
    flushRun()

    val overlayCurves = if (activeOverlaySeries.isEmpty() || altRange <= 0.0) {
        emptyList()
    } else {
        buildList {
            for (series in activeOverlaySeries) {
                val colorIdx = allOverlaySeries.indexOfFirst { it.columnName == series.columnName }.coerceAtLeast(0)
                val color = overlayStrokeColor(colorIdx)
                val dashLen = (if (colorIdx % 2 == 0) 6f else 3f) * densityPxPerDp
                val gapLen = 3f * densityPxPerDp
                val path = Path()
                var started = false
                for (pt in series.points) {
                    if (!pt.value.isFinite()) continue
                    val xSpan = if (isTimeDomain) pt.timeSec.toDouble() else pt.distance
                    val x = ElevationProfileZoomMath.distanceToCanvasX(xSpan, currentStartDist, visibleSpan, width)
                    val y = height - (((pt.value - minAlt) / altRange).toFloat() * height)
                    if (!started) {
                        path.moveTo(x, y)
                        started = true
                    } else {
                        path.lineTo(x, y)
                    }
                }
                if (started) {
                    add(
                        CachedOverlayCurvePath(
                            path = path,
                            color = color,
                            stroke = Stroke(
                                width = 1.8f * densityPxPerDp,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLen, gapLen), 0f),
                                cap = StrokeCap.Round
                            )
                        )
                    )
                }
            }
        }
    }

    return CachedElevationCurveGeometry(
        runs = runs,
        stroke = Stroke(width = 2f * densityPxPerDp, cap = StrokeCap.Round),
        overlayCurves = overlayCurves
    )
}
