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
import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.atrainingtracker.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.PaceFormatter
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.LinearHistogramBin
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramCalculator
import com.atrainingtracker.trainingtracker.ui.climbs.getClimbCategoryColors
import com.atrainingtracker.trainingtracker.ui.climbs.getClimbGradeColor
import com.atrainingtracker.trainingtracker.ui.theme.*
import com.atrainingtracker.trainingtracker.ui.util.NumericalEncodingUtils
import com.google.android.gms.maps.model.LatLng
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow

// Data class to cache the pre-calculated geometry and metadata
private data class CachedProfileData(
    val segments: List<ElevationSegment>,
    val minAlt: Double,
    val maxAlt: Double,
    val totalDist: Double,
    val totalTimeSec: Long,
    val altRange: Double,
    val distStep: Float,
    val altStep: Float,
    val adaptiveHeight: androidx.compose.ui.unit.Dp
)

/**
 * ATT-508 / REQ-UI-126: Encapsulates sanitized vertical elevation bounds and calculated range.
 *
 * @property min The sanitized effective minimum altitude (in meters).
 * @property max The sanitized effective maximum altitude (in meters).
 * @property range The effective vertical range (max - min), guaranteed to be >= 1.0.
 */
data class ElevationBounds(
    val min: Double,
    val max: Double,
    val range: Double
)

/**
 * ATT-508 / REQ-UI-126: Pure helper to calculate sanitized vertical elevation bounds for the profile chart.
 *
 * Scrutinizes [minAltitudeOverride] and [maxAltitudeOverride] against the actual [pathPoints]
 * envelope. If an override deviates from stream bounds by more than [outlierToleranceMeters] (e.g. from
 * historical GPS cold-start spikes), it is clamped to the stream envelope. Furthermore, enforces an aesthetic
 * minimum vertical span ([minSpanMeters]) centered around the route elevation to prevent flat routes
 * (such as coastal rides) from compressing into a hairline or magnifying millibar noise.
 *
 * @param pathPoints The list of route points containing altitude values.
 * @param minAltitudeOverride Optional persisted minimum altitude override from database summary extrema.
 * @param maxAltitudeOverride Optional persisted maximum altitude override from database summary extrema.
 * @param outlierToleranceMeters Tolerance threshold in meters beyond which an override is treated as corrupt.
 * @param minSpanMeters Aesthetic minimum vertical span in meters centered around route midpoint.
 * @return [ElevationBounds] containing sanitized min, max, and range.
 */
fun calculateElevationBounds(
    pathPoints: List<PathPoint>,
    minAltitudeOverride: Double? = null,
    maxAltitudeOverride: Double? = null,
    outlierToleranceMeters: Double = 15.0,
    minSpanMeters: Double = 20.0
): ElevationBounds {
    val nonNullPoints = pathPoints.filter { it.altitude != null && !it.altitude.isNaN() }
    if (nonNullPoints.isEmpty()) {
        val fallbackMin = minAltitudeOverride ?: 0.0
        val fallbackMax = maxAltitudeOverride ?: (fallbackMin + minSpanMeters)
        val range = (fallbackMax - fallbackMin).coerceAtLeast(1.0)
        return ElevationBounds(fallbackMin, fallbackMax, range)
    }

    // REQ-CON-017 / ATT-5312: Filter extreme non-physical altitude outliers and isolated 0.0m dropouts on inland terrain
    val validPoints = if (nonNullPoints.size >= 5) {
        val sortedAlts = nonNullPoints.map { it.altitude!! }.sorted()
        val medianAlt = sortedAlts[sortedAlts.size / 2]
        var filtered = nonNullPoints
        if (medianAlt < 1500.0 && nonNullPoints.any { it.altitude!! > 3000.0 }) {
            filtered = filtered.filter { it.altitude!! in -500.0..3000.0 }.ifEmpty { filtered }
        }
        if (medianAlt > 50.0 && filtered.any { Math.abs(it.altitude!!) < 1e-4 }) {
            filtered = filtered.filter { Math.abs(it.altitude!!) >= 1e-4 }.ifEmpty { filtered }
        }
        filtered
    } else {
        nonNullPoints
    }

    val streamMin = validPoints.minOf { it.altitude!! }
    val streamMax = validPoints.maxOf { it.altitude!! }

    // Sanitize min override:
    // If override is an outlier (< streamMin - 15) OR higher than stream points (> streamMin),
    // clamp to streamMin to guarantee that rendered points never drop below the chart baseline.
    val sanitizedMin = if (minAltitudeOverride != null) {
        if (minAltitudeOverride < streamMin - outlierToleranceMeters || minAltitudeOverride > streamMin) {
            streamMin
        } else {
            minAltitudeOverride
        }
    } else {
        streamMin
    }

    // Sanitize max override:
    // If override is an outlier (> streamMax + 15) OR lower than stream points (< streamMax),
    // clamp to streamMax to guarantee that rendered points never clip above the chart ceiling.
    val sanitizedMax = if (maxAltitudeOverride != null) {
        if (maxAltitudeOverride > streamMax + outlierToleranceMeters || maxAltitudeOverride < streamMax) {
            streamMax
        } else {
            maxAltitudeOverride
        }
    } else {
        streamMax
    }

    // Ensure valid order and envelope containment
    val effectiveMinRaw = minOf(sanitizedMin, streamMin)
    val effectiveMaxRaw = maxOf(sanitizedMax, streamMax)
    val currentSpan = effectiveMaxRaw - effectiveMinRaw

    // Enforce aesthetic minimum vertical span centered on the route
    return if (currentSpan < minSpanMeters) {
        val mid = (effectiveMinRaw + effectiveMaxRaw) / 2.0
        val expandedMin = minOf(mid - (minSpanMeters / 2.0), streamMin)
        val expandedMax = maxOf(mid + (minSpanMeters / 2.0), streamMax)
        val span = expandedMax - expandedMin
        ElevationBounds(expandedMin, expandedMax, span.coerceAtLeast(1.0))
    } else {
        ElevationBounds(effectiveMinRaw, effectiveMaxRaw, currentSpan.coerceAtLeast(1.0))
    }
}

/**
 * Calculates height based on altitude range.
 * Min: 70dp, Max: 200dp (at 1000m range)
 */
fun calculateElevationProfileHeight(range: Double): androidx.compose.ui.unit.Dp {
    val minH = 70f
    val maxH = 200f
    val threshold = 1000.0
    val normalizedRange = (range.coerceIn(0.0, threshold) / threshold).toFloat()
    val curvedRange = normalizedRange.toDouble().pow(0.6).toFloat()
    val height = minH + curvedRange * (maxH - minH)
    return height.toInt().dp
}

/**
 * Resolves standard 7-zone slope gradient color bands (REQ-UI-371):
 * - < 2%: Zone 1 (Chartreuse) - flat, rolling, descents, non-finite values (NaN/Inf)
 * - 2% - 5%: Zone 2 (Green) - gentle incline [2.0, 5.0)
 * - 5% - 10%: Zone 3 (Orange) - moderate climb [5.0, 10.0)
 * - 10% - 15%: Zone 4 (Red) - steep climb [10.0, 15.0)
 * - 15% - 20%: Zone 5 (Dark Violet) - very steep climb [15.0, 20.0)
 * - 20% - 25%: Zone 6 (Dark Chestnut Brown) - extreme ramp [20.0, 25.0)
 * - >= 25%: Zone 7 (Black) - monster grade / wall [25.0, +inf)
 */
fun getElevationGradeColor(gradePercent: Double): Color {
    if (!gradePercent.isFinite()) return TTColor.Zone1
    return when {
        gradePercent < 2.0 -> TTColor.Zone1
        gradePercent < 5.0 -> TTColor.Zone2
        gradePercent < 10.0 -> TTColor.Zone3
        gradePercent < 15.0 -> TTColor.Zone4
        gradePercent < 20.0 -> TTColor.Zone5
        gradePercent < 25.0 -> TTColor.Zone6
        else -> TTColor.Zone7
    }
}



@Composable
fun ElevationProfile(
    encodedAltitudes: String,
    encodedDistances: String,
    currentDistance: Double? = null,
    minAltitudeOverride: Double? = null,
    maxAltitudeOverride: Double? = null,
    onDistanceSelected: (Double?) -> Unit = {},
    showZoomControls: Boolean = false,
    xAxisDomain: ProfileXAxisDomain = ProfileXAxisDomain.DISTANCE,
    bSportType: BSportType = BSportType.BIKE,
    onPointSelected: ((PathPoint?) -> Unit)? = null,
    zoomScale: Float = 1.0f,
    startDist: Double = 0.0,
    onZoomChanged: ((zoomScale: Float, startDist: Double) -> Unit)? = null,
    isPanMode: Boolean = false,
    showScrubbingBadge: Boolean = true,
    climbs: List<Climb> = emptyList(),
    smoothingSigma: Double? = null,
    fillParentHeight: Boolean = false,
    climbGradientShading: Boolean = false,
    segmentColorProvider: ((Double) -> Color)? = null,
    fixedHeight: androidx.compose.ui.unit.Dp? = null,
    showEmptyState: Boolean = false,
    alignWithTelemetryCharts: Boolean = false,
    overlaySeries: List<TelemetrySourceSeries> = emptyList(),
    enabledOverlayIds: Set<String> = emptySet(),
    onToggleOverlay: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val decodedData = remember(encodedAltitudes, encodedDistances) {
        val alts = NumericalEncodingUtils.decodeDoubles(encodedAltitudes)
        val dists = NumericalEncodingUtils.decodeDoubles(encodedDistances)
        dists.zip(alts) { dist, alt ->
            PathPoint(distance = dist, altitude = alt, latLng = LatLng(0.0, 0.0))
        }
    }

    ElevationProfile(
        pathPoints = decodedData,
        currentDistance = currentDistance,
        minAltitudeOverride = minAltitudeOverride,
        maxAltitudeOverride = maxAltitudeOverride,
        onDistanceSelected = onDistanceSelected,
        showZoomControls = showZoomControls,
        xAxisDomain = xAxisDomain,
        bSportType = bSportType,
        onPointSelected = onPointSelected,
        zoomScale = zoomScale,
        startDist = startDist,
        onZoomChanged = onZoomChanged,
        isPanMode = isPanMode,
        showScrubbingBadge = showScrubbingBadge,
        climbs = climbs,
        smoothingSigma = smoothingSigma,
        fillParentHeight = fillParentHeight,
        climbGradientShading = climbGradientShading,
        segmentColorProvider = segmentColorProvider,
        fixedHeight = fixedHeight,
        showEmptyState = showEmptyState,
        alignWithTelemetryCharts = alignWithTelemetryCharts,
        overlaySeries = overlaySeries,
        enabledOverlayIds = enabledOverlayIds,
        onToggleOverlay = onToggleOverlay,
        modifier = modifier
    )
}

@Composable
fun ElevationProfile(
    pathPoints: List<PathPoint>,
    currentDistance: Double?,
    minAltitudeOverride: Double? = null,
    maxAltitudeOverride: Double? = null,
    onDistanceSelected: (Double?) -> Unit = {},
    showZoomControls: Boolean = false,
    xAxisDomain: ProfileXAxisDomain = ProfileXAxisDomain.DISTANCE,
    bSportType: BSportType = BSportType.BIKE,
    onPointSelected: ((PathPoint?) -> Unit)? = null,
    zoomScale: Float = 1.0f,
    startDist: Double = 0.0,
    onZoomChanged: ((zoomScale: Float, startDist: Double) -> Unit)? = null,
    isPanMode: Boolean = false,
    showScrubbingBadge: Boolean = true,
    modifier: Modifier = Modifier,
    hrZoneThresholds: HeartRateZoneThresholds? = null,
    powerZoneThresholds: PowerZoneThresholds? = null,
    climbs: List<Climb> = emptyList(),
    smoothingSigma: Double? = null,
    fillParentHeight: Boolean = false,
    climbGradientShading: Boolean = false,
    segmentColorProvider: ((Double) -> Color)? = null,
    fixedHeight: androidx.compose.ui.unit.Dp? = null,
    showEmptyState: Boolean = false,
    alignWithTelemetryCharts: Boolean = false,
    overlaySeries: List<TelemetrySourceSeries> = emptyList(),
    enabledOverlayIds: Set<String> = emptySet(),
    onToggleOverlay: ((String) -> Unit)? = null
) {
    if (pathPoints.size < 2 && showEmptyState) {
        ElevationProfileEmptyState(
            fixedHeight = fixedHeight,
            fillMaxContainerHeight = fillParentHeight,
            modifier = modifier
        )
        return
    }
    if (pathPoints.size < 2) return

    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val unit = TrainingApplication.getUnit()
    val tuningDataStore = remember(context) { TuningPreferencesDataStore.getInstance(context) }
    val tuningConfig by tuningDataStore.tuningConfigFlow.collectAsState(initial = TuningConfig())
    val effectiveSigma = smoothingSigma ?: tuningConfig.elevationSmoothingSigmaMeters.toDouble()
    var showLegend by remember { mutableStateOf(false) }
    var localEnabledOverlayIds by rememberSaveable(overlaySeries.map { it.columnName }) {
        mutableStateOf(enabledOverlayIds)
    }
    val effectiveEnabledOverlayIds = if (onToggleOverlay != null) enabledOverlayIds else localEnabledOverlayIds
    val handleToggleOverlay: (String) -> Unit = onToggleOverlay ?: { col ->
        localEnabledOverlayIds = if (col in localEnabledOverlayIds) localEnabledOverlayIds - col else localEnabledOverlayIds + col
    }
    val hasMultiSourceOverlays = overlaySeries.size >= 2
    val activeOverlaySeries = remember(overlaySeries, effectiveEnabledOverlayIds, hasMultiSourceOverlays) {
        if (hasMultiSourceOverlays) overlaySeries.filter { it.columnName in effectiveEnabledOverlayIds } else emptyList()
    }

    val pathStartDist = pathPoints.firstOrNull()?.distance ?: 0.0
    var internalZoomScale by remember(pathPoints) { mutableFloatStateOf(1.0f) }
    var internalStartDist by remember(pathPoints) { mutableDoubleStateOf(pathStartDist) }
    val currentZoomScale = if (onZoomChanged != null) zoomScale else internalZoomScale
    val currentStartDist = if (onZoomChanged != null) (if (startDist != 0.0 || pathStartDist == 0.0) startDist else pathStartDist) else internalStartDist

    val currentOnZoomChangedState by rememberUpdatedState(onZoomChanged)
    val updateZoom: (Float, Double) -> Unit = { newZoom, newStart ->
        val onZoom = currentOnZoomChangedState
        if (onZoom != null) {
            onZoom(newZoom, newStart)
        } else {
            internalZoomScale = newZoom
            internalStartDist = newStart
        }
    }
    val currentDistanceState = rememberUpdatedState(currentDistance)
    val curveCacheBuildListener = LocalTelemetryCurveCacheBuildListener.current

    val cachedData = remember(pathPoints, unit, minAltitudeOverride, maxAltitudeOverride, effectiveSigma, climbGradientShading, segmentColorProvider, activeOverlaySeries) {
        val maxPoints = 500
        val pathPointsDownsampled = if (pathPoints.size > maxPoints) {
            val step = pathPoints.size / maxPoints
            pathPoints.filterIndexed { index, _ -> index % step == 0 || index == pathPoints.size - 1 }
        } else {
            pathPoints
        }

        val baseStartDist = pathPointsDownsampled.first().distance
        val totalDist = (pathPointsDownsampled.last().distance - baseStartDist).coerceAtLeast(1.0)
        val totalTimeSec = (pathPointsDownsampled.last().timeSec - pathPointsDownsampled.first().timeSec).coerceAtLeast(1L)

        val smoothedAltitudes = ElevationSmoothingMath.smoothAltitudes(pathPointsDownsampled, sigma = effectiveSigma)

        val bounds = calculateElevationBoundsWithOverlays(
            pathPoints = pathPointsDownsampled,
            minAltitudeOverride = minAltitudeOverride,
            maxAltitudeOverride = maxAltitudeOverride,
            activeOverlaySeries = activeOverlaySeries
        )
        val min = bounds.min
        val max = bounds.max
        val range = bounds.range

        val distStep = ElevationProfileZoomMath.calculateAdaptiveDistanceStep(totalDist, unit)

        val altStep = if (unit == MyUnits.METRIC) {
            when {
                range > 2000 -> 1000f
                range > 1000 -> 500f
                range > 500 -> 200f
                range > 100 -> 100f
                else -> 50f
            }
        } else {
            val rangeFeet = range / BANALService.METER_PER_FOOT
            val feetStep = when {
                rangeFeet > 10000 -> 5000f
                rangeFeet > 5000 -> 2000f
                rangeFeet > 2000 -> 1000f
                rangeFeet > 1000 -> 500f
                rangeFeet > 500 -> 200f
                else -> 100f
            }
            (feetStep * BANALService.METER_PER_FOOT).toFloat()
        }

        val segments = mutableListOf<ElevationSegment>()
        for (i in 0 until pathPointsDownsampled.size - 1) {
            val p1 = pathPointsDownsampled[i]
            val p2 = pathPointsDownsampled[i + 1]
            val sAlt1 = smoothedAltitudes[i]
            val sAlt2 = smoothedAltitudes[i + 1]
            val a1 = ((sAlt1 - min) / range).toFloat()
            val a2 = ((sAlt2 - min) / range).toFloat()
            val grade = ElevationSmoothingMath.calculateGrade(p1.distance, sAlt1, p2.distance, sAlt2)

            val color = if (climbGradientShading) {
                segmentColorProvider?.invoke(grade) ?: getClimbGradeColor(grade)
            } else {
                segmentColorProvider?.invoke(grade) ?: getElevationGradeColor(grade)
            }
            segments.add(ElevationSegment(p1.distance, p1.timeSec, a1, p2.distance, p2.timeSec, a2, color))
        }

        val adaptiveHeight = calculateElevationProfileHeight(range)
        CachedProfileData(segments, min, max, totalDist, totalTimeSec, range, distStep, altStep, adaptiveHeight)
    }

    val isTimeDomain = ProfileDomainMath.isEffectiveTimeDomain(xAxisDomain, totalDistance = cachedData.totalDist, totalTimeSec = cachedData.totalTimeSec)
    val totalSpan = ProfileDomainMath.calculateTotalSpan(xAxisDomain, totalDistance = cachedData.totalDist, totalTimeSec = cachedData.totalTimeSec)
    val visibleSpan = ElevationProfileZoomMath.calculateVisibleDistance(totalSpan, currentZoomScale)

    val altitudeFormatter = remember(unit) { AltitudeFormatter() }
    val distanceFormatter = remember(unit) { DistanceFormatter() }

    val textPaint = remember(colorScheme) {
        Paint().apply {
            color = colorScheme.onSurfaceVariant.toArgb()
            textSize = 32f
            isAntiAlias = true
        }
    }
    val highlightPaint = remember(colorScheme) {
        Paint().apply {
            color = colorScheme.onSurface.toArgb()
            textSize = 32f
            isAntiAlias = true
            isFakeBoldText = true
        }
    }

    val topPadding = if (showZoomControls) 24.dp else 16.dp
    val bottomPadding = if (fixedHeight != null) 18.dp else 24.dp
    val totalCanvasHeight = fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 8.dp else cachedData.adaptiveHeight

    val density = LocalDensity.current
    val cachedClimbs = remember(climbs) { precomputeClimbDrawData(climbs) }
    val climbBadgePaints = remember(density, colorScheme) { buildClimbBadgePaintCache(density) }
    val markerDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) }
    val badgeTextSizeDp: Dp = with(density) { 10.sp.toDp() }
    val requiredClimbTopClearance: Dp = ElevationProfileZoomMath.calculateRequiredClimbTopClearance(
        badgeTextSizeDp = badgeTextSizeDp.value
    ).dp
    val climbBadgeTopPadding: Dp = if (!isTimeDomain && climbs.isNotEmpty()) {
        (requiredClimbTopClearance - topPadding).coerceAtLeast(0.dp)
    } else {
        0.dp
    }
    val effectiveTopPadding: Dp = ElevationProfileZoomMath.calculateEffectiveTopPadding(
        fixedHeightDp = fixedHeight?.value,
        baseTopPaddingDp = topPadding.value,
        bottomPaddingDp = bottomPadding.value,
        requiredClearanceDp = requiredClimbTopClearance.value,
        isTimeDomain = isTimeDomain,
        hasClimbs = climbs.isNotEmpty()
    ).dp

    val altitudeHistogram = remember(pathPoints, cachedData.minAlt, cachedData.maxAlt, alignWithTelemetryCharts) {
        if (!alignWithTelemetryCharts || pathPoints.isEmpty() || cachedData.maxAlt <= cachedData.minAlt) {
            emptyList()
        } else {
            val altColor = TTColor.Zone1
            val altValues = pathPoints.map { it.altitude }
            val timestamps = pathPoints.map { it.timeSec }
            TelemetryHistogramCalculator.computeLinearMarginalHistogram(
                values = altValues,
                timestamps = timestamps,
                dataMin = cachedData.minAlt,
                dataMax = cachedData.maxAlt,
                numBins = 20,
                color = altColor
            )
        }
    }

    val configuration = LocalConfiguration.current
    val currentLocale = configuration.locales[0]
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val climbCategoryLabels = remember {
        ClimbCategory.entries.associateWith { cat ->
            getClimbCategoryColors(cat).third
        }
    }.mapValues { (_, stringRes) -> stringResource(stringRes) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (hasMultiSourceOverlays) {
            MultiSensorOverlayControls(
                metricType = TelemetryMetricType.ALTITUDE,
                overlaySeries = overlaySeries,
                enabledOverlayIds = effectiveEnabledOverlayIds,
                activeOverlaySeries = activeOverlaySeries,
                onToggleOverlay = handleToggleOverlay,
                isLandscape = isLandscape,
                unit = unit
            )
        }
        Box(modifier = if (fillParentHeight) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth()) {
        val baseCanvasModifier = if (fillParentHeight) {
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = totalCanvasHeight)
                .fillMaxHeight()
        } else {
            Modifier
                .fillMaxWidth()
                .height(totalCanvasHeight)
        }

        val endCanvasPadding = if (isLandscape && showZoomControls) 0.dp else (if (alignWithTelemetryCharts) 52.dp else 25.dp)
        val canvasModifier = baseCanvasModifier.telemetryChartGestures(
            enabled = showZoomControls,
            totalSpan = totalSpan,
            isTimeDomain = isTimeDomain,
            isTrackless = false,
            isPanMode = isPanMode,
            startPadding = 50.dp,
            endPadding = endCanvasPadding,
            zoomScale = currentZoomScale,
            startDist = currentStartDist,
            pathPoints = pathPoints,
            indexSemantics = ChartScrubIndexSemantics.FLOOR,
            onZoomChanged = updateZoom,
            onDistanceSelected = onDistanceSelected,
            onPointSelected = onPointSelected
        )

        val elevationDrawCacheBuilder: CacheDrawScope.() -> DrawResult = remember(
            pathPoints,
            cachedData,
            isTimeDomain,
            currentStartDist,
            visibleSpan,
            currentZoomScale,
            unit,
            currentLocale,
            showZoomControls,
            effectiveTopPadding,
            cachedClimbs,
            climbBadgePaints,
            climbCategoryLabels,
            activeOverlaySeries,
            overlaySeries,
            colorScheme,
            textPaint,
            highlightPaint,
            altitudeFormatter,
            distanceFormatter,
            markerDashEffect,
            curveCacheBuildListener
        ) {
            {
                val width = size.width
                val height = size.height
                val cachedCurve = buildCachedElevationCurveGeometry(
                    segments = cachedData.segments,
                    isTimeDomain = isTimeDomain,
                    currentStartDist = currentStartDist,
                    visibleSpan = visibleSpan,
                    minAlt = cachedData.minAlt,
                    altRange = cachedData.altRange,
                    width = width,
                    height = height,
                    densityPxPerDp = density.density,
                    activeOverlaySeries = activeOverlaySeries,
                    allOverlaySeries = overlaySeries
                )
                curveCacheBuildListener?.invoke()

                onDrawBehind {
                    drawIntoCanvas { canvas ->
                        val minAltLabel = altitudeFormatter.format_with_units(cachedData.minAlt)
                        val maxAltLabel = altitudeFormatter.format_with_units(cachedData.maxAlt)
                        val minAltWidth = highlightPaint.measureText(minAltLabel)
                        val maxAltWidth = highlightPaint.measureText(maxAltLabel)
                        canvas.nativeCanvas.drawText(minAltLabel, -minAltWidth - 10f, height, highlightPaint)
                        canvas.nativeCanvas.drawText(maxAltLabel, -maxAltWidth - 10f, highlightPaint.textSize, highlightPaint)

                        val endLabel = if (isTimeDomain) {
                            ElevationProfileZoomMath.formatTimeTick((currentStartDist + visibleSpan).toLong())
                        } else {
                            distanceFormatter.format_with_units(currentStartDist + visibleSpan)
                        }
                        val endLabelWidth = highlightPaint.measureText(endLabel)
                        canvas.nativeCanvas.drawText(endLabel, width - endLabelWidth, height + 45f, highlightPaint)

                        val minSpacingPx = 24.dp.toPx()
                        val isZoomedOrSliced = currentZoomScale > 1.01f || currentStartDist > 1.0
                        val startLabelWidth = if (isZoomedOrSliced) {
                            val startLabel = if (isTimeDomain) {
                                ElevationProfileZoomMath.formatTimeTick(currentStartDist.toLong())
                            } else {
                                distanceFormatter.format_with_units(currentStartDist)
                            }
                            canvas.nativeCanvas.drawText(startLabel, 0f, height + 45f, highlightPaint)
                            highlightPaint.measureText(startLabel)
                        } else {
                            0f
                        }

                        val startClearanceThreshold = if (isZoomedOrSliced) startLabelWidth + minSpacingPx else minSpacingPx
                        val endClearanceThreshold = width - endLabelWidth - minSpacingPx
                        val initialLastDrawnRightX = if (isZoomedOrSliced) startLabelWidth else -1f
                        val xAxisStep = if (isTimeDomain) {
                            ElevationProfileZoomMath.calculateAdaptiveTimeStep(visibleSpan).toDouble()
                        } else {
                            ElevationProfileZoomMath.calculateAdaptiveDistanceStep(visibleSpan, unit).toDouble()
                        }
                        drawXAxisTicks(
                            startValue = currentStartDist,
                            visibleSpan = visibleSpan,
                            step = xAxisStep,
                            plotStartX = 0f,
                            plotWidth = width,
                            paint = textPaint,
                            labelY = height + 45f,
                            initialLastDrawnRightX = initialLastDrawnRightX,
                            formatLabel = { value ->
                                if (isTimeDomain) {
                                    ElevationProfileZoomMath.formatTimeTick(value.toLong())
                                } else {
                                    formatElevationDistanceTickLabel(value, visibleSpan, unit, currentLocale)
                                }
                            },
                            shouldRenderLabel = { labelLeft, labelRight, lastDrawnRightX ->
                                ElevationProfileZoomMath.shouldRenderTickLabel(
                                    labelLeft = labelLeft,
                                    labelRight = labelRight,
                                    lastDrawnRightX = lastDrawnRightX,
                                    startClearanceThreshold = startClearanceThreshold,
                                    endClearanceThreshold = endClearanceThreshold,
                                    minSpacing = minSpacingPx
                                )
                            },
                            onLabelRendered = { x, label, labelLeft, labelY ->
                                canvas.nativeCanvas.drawLine(x, height, x, height - 10f, textPaint)
                                canvas.nativeCanvas.drawText(label, labelLeft, labelY, textPaint)
                            }
                        )

                        var currentA = (ceil(cachedData.minAlt / cachedData.altStep) * cachedData.altStep).toFloat()
                        var lastY = -1000f
                        while (currentA < cachedData.maxAlt) {
                            val y = height - ((currentA - cachedData.minAlt) / cachedData.altRange).toFloat() * height
                            if (ElevationProfileZoomMath.shouldRenderYTickLabel(
                                    y = y,
                                    canvasHeight = height,
                                    maxLabelBaselineY = highlightPaint.textSize,
                                    lastY = lastY,
                                    textSize = textPaint.textSize
                                )
                            ) {
                                canvas.nativeCanvas.drawLine(-10f, y, 0f, y, textPaint)
                                drawLine(colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Ghost), Offset(0f, y), Offset(width, y), 1.dp.toPx())
                                val altText = altitudeFormatter.format(currentA.toDouble())
                                val altWidth = textPaint.measureText(altText)
                                canvas.nativeCanvas.drawText(altText, -altWidth - 10f, y + 10f, textPaint)
                                lastY = y
                            }
                            currentA += cachedData.altStep
                        }

                        // Render Summit Category Badges using hoisted Paint cache & precomputed summits (REQ-UI-274, REQ-UI-2793)
                        if (!isTimeDomain && cachedClimbs.isNotEmpty()) {
                            val pillPaddingH = 6.dp.toPx()
                            val pillPaddingV = 2.dp.toPx()
                            val pillCornerRadius = 4.dp.toPx()
                            val stemOffsetPx = 8.dp.toPx()
                            val effectiveTopPaddingPx = effectiveTopPadding.toPx()
                            val safetyMarginPx = 2.dp.toPx()
                            for (i in cachedClimbs.indices) {
                                val climbData = cachedClimbs[i]
                                val summitDist = climbData.summitDistance ?: continue
                                if (summitDist in currentStartDist..(currentStartDist + visibleSpan)) {
                                    val summitAlt = climbData.summitAltitude ?: cachedData.minAlt
                                    val summitX = ElevationProfileZoomMath.distanceToCanvasX(summitDist, currentStartDist, visibleSpan, width)
                                    val summitY = height - (((summitAlt - cachedData.minAlt) / cachedData.altRange).toFloat() * height)

                                    val paints = climbBadgePaints[climbData.category] ?: continue
                                    val badgePaint = paints.badgePaint
                                    val bgPaint = paints.bgPaint
                                    val stemPaint = paints.stemPaint
                                    val badgeText = climbCategoryLabels[climbData.category] ?: ""

                                    val textWidth = badgePaint.measureText(badgeText)
                                    val pillHeight = badgePaint.textSize + pillPaddingV * 2
                                    val pillWidth = textWidth + pillPaddingH * 2
                                    val unconstrainedPillBottom = summitY - stemOffsetPx
                                    val unconstrainedPillTop = unconstrainedPillBottom - pillHeight
                                    val pillTop = ElevationProfileZoomMath.calculateClampedPillTop(
                                        unconstrainedPillTop = unconstrainedPillTop,
                                        effectiveTopPaddingPx = effectiveTopPaddingPx,
                                        safetyMarginPx = safetyMarginPx
                                    )
                                    val pillBottom = pillTop + pillHeight
                                    val pillLeft = ElevationProfileZoomMath.calculateClampedPillLeft(
                                        summitXPx = summitX,
                                        pillWidthPx = pillWidth,
                                        canvasWidthPx = width,
                                        safetyMarginPx = safetyMarginPx
                                    )
                                    val pillRight = pillLeft + pillWidth

                                    canvas.nativeCanvas.drawLine(summitX, summitY, summitX, pillBottom, stemPaint)
                                    canvas.nativeCanvas.drawRoundRect(
                                        pillLeft,
                                        pillTop,
                                        pillRight,
                                        pillBottom,
                                        pillCornerRadius,
                                        pillCornerRadius,
                                        bgPaint
                                    )
                                    val textY = pillBottom - pillPaddingV - badgePaint.descent()
                                    canvas.nativeCanvas.drawText(badgeText, (pillLeft + pillRight) / 2f, textY, badgePaint)
                                }
                            }
                        }
                    }

                    clipRect(left = 0f, top = 0f, right = width, bottom = height) {
                        for (run in cachedCurve.runs) {
                            drawPath(run.fillPath, run.color.copy(alpha = TTAlpha.Disabled))
                        }
                        for (run in cachedCurve.runs) {
                            drawPath(run.strokePath, run.color, style = cachedCurve.stroke)
                        }

                        // Render horizontal climb span indicators along the X-axis baseline (REQ-UI-299, REQ-UI-307)
                        if (!isTimeDomain && cachedClimbs.isNotEmpty()) {
                            val baselineY = height - 2.dp.toPx()
                            val climbStrokePx = 4.dp.toPx()
                            for (i in cachedClimbs.indices) {
                                val climbData = cachedClimbs[i]
                                val cStart = climbData.startDistance ?: continue
                                val cEnd = climbData.endDistance ?: continue
                                if (cEnd < currentStartDist || cStart > currentStartDist + visibleSpan) continue

                                val rawX1 = ElevationProfileZoomMath.distanceToCanvasX(cStart, currentStartDist, visibleSpan, width)
                                val rawX2 = ElevationProfileZoomMath.distanceToCanvasX(cEnd, currentStartDist, visibleSpan, width)
                                val x1 = rawX1.coerceIn(0f, width)
                                val x2 = rawX2.coerceIn(0f, width)

                                if (x2 > x1) {
                                    drawLine(
                                        color = climbData.bgColor,
                                        start = Offset(x1, baselineY),
                                        end = Offset(x2, baselineY),
                                        strokeWidth = climbStrokePx,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                        for (overlayCurve in cachedCurve.overlayCurves) {
                            drawPath(
                                path = overlayCurve.path,
                                color = overlayCurve.color,
                                style = overlayCurve.stroke
                            )
                        }
                    }

                    currentDistanceState.value?.let { dist ->
                        val activeIndex = TelemetryChartSearch.findFloorIndex(pathPoints, dist, isTimeDomain = false).coerceAtLeast(0)
                        val activePoint = pathPoints.getOrNull(activeIndex) ?: pathPoints.firstOrNull()
                        val pLeft = pathPoints[activeIndex]
                        val pRight = pathPoints.getOrNull(activeIndex + 1)
                        val interAlt = if (pRight != null && pRight.distance > pLeft.distance && pLeft.altitude != null && pRight.altitude != null) {
                            pLeft.altitude + ((dist - pLeft.distance) / (pRight.distance - pLeft.distance)) * (pRight.altitude - pLeft.altitude)
                        } else {
                            pLeft.altitude ?: pRight?.altitude
                        }

                        val markerVal = if (isTimeDomain) (activePoint?.timeSec?.toDouble() ?: 0.0) else dist
                        if (markerVal in currentStartDist..(currentStartDist + visibleSpan)) {
                            val markerAlt = interAlt ?: cachedData.minAlt
                            val markerX = ElevationProfileZoomMath.distanceToCanvasX(markerVal, currentStartDist, visibleSpan, width)
                            val markerY = height - (((markerAlt - cachedData.minAlt) / cachedData.altRange).toFloat() * height)

                            drawLine(
                                color = colorScheme.primary,
                                start = Offset(markerX, 0f),
                                end = Offset(markerX, height),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = markerDashEffect
                            )
                            if (!showZoomControls) {
                                drawIntoCanvas { canvas ->
                                    val altStr = if (interAlt != null) altitudeFormatter.format_with_units(interAlt) else "--"
                                    val combinedLabel = "${distanceFormatter.format_with_units(dist)} | $altStr"
                                    val lWidth = highlightPaint.measureText(combinedLabel)
                                    canvas.nativeCanvas.drawText(
                                        combinedLabel,
                                        (markerX - lWidth / 2f).coerceIn(0f, (width - lWidth).coerceAtLeast(0f)),
                                        -4.dp.toPx(),
                                        highlightPaint
                                    )
                                }
                            }
                            drawCircle(colorScheme.onSurface, 5.dp.toPx(), Offset(markerX, markerY))
                            drawCircle(colorScheme.primary, 3.dp.toPx(), Offset(markerX, markerY))
                            if (activeOverlaySeries.isNotEmpty()) {
                                drawElevationOverlayScrubDots(
                                    activeOverlaySeries = activeOverlaySeries,
                                    allOverlaySeries = overlaySeries,
                                    targetValue = markerVal,
                                    isTimeDomain = isTimeDomain,
                                    markerX = markerX,
                                    minAlt = cachedData.minAlt,
                                    altRange = cachedData.altRange,
                                    height = height
                                )
                            }
                        }
                    }
                }
            }
        }

        val profileCanvas: @Composable () -> Unit = {
            Spacer(
                modifier = canvasModifier
                    .padding(
                        bottom = bottomPadding,
                        start = 50.dp,
                        end = endCanvasPadding,
                        top = effectiveTopPadding
                    )
                    .drawWithCache(elevationDrawCacheBuilder)
            )
        }

    if (isLandscape && showZoomControls) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(0.72f)) {
                profileCanvas()
            }
            if (alignWithTelemetryCharts && altitudeHistogram.isNotEmpty()) {
                AlignedMarginalLinearDistribution(
                    bins = altitudeHistogram,
                    dataMin = cachedData.minAlt,
                    dataMax = cachedData.maxAlt,
                    metricType = null,
                    topPadding = effectiveTopPadding,
                    bottomPadding = bottomPadding,
                    modifier = Modifier.weight(0.28f).height(totalCanvasHeight)
                )
            } else {
                Spacer(modifier = Modifier.weight(0.28f))
            }
        }
    } else {
        profileCanvas()
        if (!isLandscape && alignWithTelemetryCharts && altitudeHistogram.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(52.dp)
                    .height(totalCanvasHeight)
                    .padding(top = effectiveTopPadding, bottom = bottomPadding)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stripStartPx = 3.dp.toPx()
                    val stripWidthPx = (size.width - 8.dp.toPx()).coerceAtLeast(10.dp.toPx())
                    val chartHeightPx = size.height
                    val valRange = (cachedData.maxAlt - cachedData.minAlt).coerceAtLeast(1.0)
                    fun valueToY(alt: Double): Float {
                        val ratio = ((alt - cachedData.minAlt) / valRange).coerceIn(0.0, 1.0)
                        return ((1.0 - ratio) * chartHeightPx).toFloat()
                    }
                    drawMarginalHistogramBars(
                        bins = altitudeHistogram,
                        dataMin = cachedData.minAlt,
                        dataMax = cachedData.maxAlt,
                        stripStartPx = stripStartPx,
                        stripWidthPx = stripWidthPx,
                        valueToY = ::valueToY,
                        rangeMinOf = { it.rangeMin },
                        rangeMaxOf = { it.rangeMax },
                        durationSecOf = { it.durationSec },
                        percentageOf = { it.percentage },
                        colorOf = { it.color }
                    )
                }
            }
        }
    }

        // Multi-metric telemetry floating badge in detailed view (REQ-UI-201 / ATT-1391)
        if (showZoomControls && showScrubbingBadge && currentDistance != null) {
            val activeIndex = TelemetryChartSearch.findFloorIndex(pathPoints, currentDistance, isTimeDomain = false).coerceAtLeast(0)
            val activePoint = pathPoints.getOrNull(activeIndex) ?: pathPoints.firstOrNull()
            if (activePoint != null) {
                val pLeft = pathPoints[activeIndex]
                val pRight = pathPoints.getOrNull(activeIndex + 1)
                val interAlt = if (pRight != null && pRight.distance > pLeft.distance && pLeft.altitude != null && pRight.altitude != null) {
                    pLeft.altitude + ((currentDistance - pLeft.distance) / (pRight.distance - pLeft.distance)) * (pRight.altitude - pLeft.altitude)
                } else {
                    pLeft.altitude ?: pRight?.altitude
                }

                val overlayReadout = formatElevationOverlayScrubSummary(
                    activeOverlaySeries = activeOverlaySeries,
                    targetValue = if (isTimeDomain) activePoint.timeSec.toDouble() else currentDistance,
                    isTimeDomain = isTimeDomain,
                    unit = unit,
                    altitudeFormatter = altitudeFormatter,
                    locale = currentLocale
                )
                ScrubbingTelemetryBadge(
                    point = activePoint,
                    bSportType = bSportType,
                    altitude = interAlt,
                    unit = unit,
                    xAxisDomain = xAxisDomain,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp),
                    hrZoneThresholds = hrZoneThresholds,
                    powerZoneThresholds = powerZoneThresholds,
                    overlayReadoutText = overlayReadout
                )
            }
        }

        if (showZoomControls) {
            IconButton(
                onClick = { showLegend = !showLegend },
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp).size(24.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = stringResource(R.string.legend), modifier = Modifier.size(16.dp), tint = colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium))
            }

            if (showLegend) {
                GradeLegend(modifier = Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 4.dp))
            }
        }
        }
    }
}

/**
 * Floating telemetry badge for multi-metric post-workout profile scrubbing.
 * Displays instantaneous Distance, Time, Altitude, Slope, HR, Power, and Speed/Pace.
 * (REQ-UI-201 / ATT-1391)
 */
@Composable
fun ScrubbingTelemetryBadge(
    point: PathPoint,
    bSportType: BSportType,
    altitude: Double? = null,
    unit: MyUnits,
    xAxisDomain: ProfileXAxisDomain,
    modifier: Modifier = Modifier,
    hrZoneThresholds: HeartRateZoneThresholds? = null,
    powerZoneThresholds: PowerZoneThresholds? = null,
    overlayReadoutText: String? = null
) {
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]
    val effectiveHrThresholds = remember(hrZoneThresholds, bSportType, context) {
        hrZoneThresholds ?: TelemetryZoneMath.loadHeartRateThresholds(context, bSportType)
    }

    val effectivePowerThresholds = remember(powerZoneThresholds, context) {
        powerZoneThresholds ?: TelemetryZoneMath.loadPowerThresholds(context)
    }

    val distanceFormatter = remember(unit) { DistanceFormatter() }
    val altitudeFormatter = remember(unit) { AltitudeFormatter() }
    val speedFormatter = remember(unit) { SpeedFormatter() }
    val paceFormatter = remember(unit) { PaceFormatter() }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = TTAlpha.Overlay),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = 3.dp,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier
            .padding(horizontal = 4.dp)
            .testTag("scrubbing_telemetry_badge")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Row 1: Primary Spatial & Temporal Tracking
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val primaryText = if (xAxisDomain == ProfileXAxisDomain.TIME) {
                    val distStr = if (point.distance > 0.0) " • ${distanceFormatter.format_with_units(point.distance)}" else ""
                    "${ElevationProfileZoomMath.formatTimeTick(point.timeSec)}$distStr"
                } else {
                    val timeStr = if (point.timeSec > 0) " • ${ElevationProfileZoomMath.formatTimeTick(point.timeSec)}" else ""
                    "${distanceFormatter.format_with_units(point.distance)}$timeStr"
                }
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val altStr = if (altitude != null) altitudeFormatter.format_with_units(altitude) else "--"
                val slopeVal = point.slope
                val slopeStr = if (slopeVal != null) {
                    val sign = if (slopeVal > 0) "+" else ""
                    " ($sign${String.format(currentLocale, "%.1f", slopeVal)}%)"
                } else ""
                Text(
                    text = "$altStr$slopeStr",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Row 2: Telemetry (HR, Power, Speed/Pace)
            val hasHr = point.hr != null && point.hr > 0
            val hasPower = point.power != null && point.power >= 0
            val hasSpeed = point.speedMps != null && point.speedMps > 0.0

            if (hasHr || hasPower || hasSpeed) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasHr) {
                        val hrZone = effectiveHrThresholds?.let {
                            TelemetryZoneMath.determineHeartRateZone(point.hr.toDouble(), it)
                        }
                        val hrAnnotated = buildAnnotatedString {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                                append("${point.hr} bpm")
                            }
                            if (hrZone != null && hrZone in 1..5) {
                                withStyle(
                                    SpanStyle(
                                        color = TelemetryZoneMath.ZONE_COLORS[hrZone - 1],
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append(" • Z$hrZone")
                                }
                            }
                        }
                        Text(
                            text = hrAnnotated,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (hasPower) {
                        val powerZone = effectivePowerThresholds?.let {
                            TelemetryZoneMath.determinePowerZone(point.power.toDouble(), it)
                        }
                        val powerAnnotated = buildAnnotatedString {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                                append("${point.power} W")
                            }
                            if (powerZone != null && powerZone in 1..TelemetryZoneMath.ZONE_COLORS.size) {
                                withStyle(
                                    SpanStyle(
                                        color = TelemetryZoneMath.ZONE_COLORS[powerZone - 1],
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append(" • Z$powerZone")
                                }
                            }
                        }
                        Text(
                            text = powerAnnotated,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (hasSpeed) {
                        val speedStr = if (bSportType == BSportType.RUN) {
                            val spd = point.speedMps
                            if (spd < 0.55) {
                                paceFormatter.format_with_units(null)
                            } else {
                                paceFormatter.format_with_units(1.0 / spd)
                            }
                        } else {
                            speedFormatter.format_with_units(point.speedMps)
                        }
                        Text(
                            text = speedStr,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (!overlayReadoutText.isNullOrEmpty()) {
                Text(
                    text = overlayReadoutText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * ATT-3779 / REQ-UI-394: Placeholder empty state rendered when the Elevation Profile is enabled
 * on a tracking tab (`showEmptyState = true`) but fewer than 2 elevation points exist
 * (e.g., before tracking starts and no route is selected).
 *
 * Uses the canonical minimum elevation profile height (`calculateElevationProfileHeight(0.0)` = 70.dp)
 * in strict compliance with Design Guidelines Section 2.3 ("No Hardcoded Fixed Heights").
 */
@Composable
internal fun ElevationProfileEmptyState(
    fixedHeight: Dp? = null,
    fillMaxContainerHeight: Boolean = false,
    modifier: Modifier = Modifier
) {
    val targetHeight = fixedHeight ?: calculateElevationProfileHeight(0.0)
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (fillMaxContainerHeight) Modifier.fillMaxHeight()
                else Modifier.height(targetHeight)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val gridColor = colorScheme.outlineVariant.copy(alpha = 0.25f)
            val topY = h * 0.2f
            val midY = h * 0.5f
            val bottomY = h * 0.8f
            drawLine(gridColor, Offset(0f, topY), Offset(w, topY), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, midY), Offset(w, midY), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, bottomY), Offset(w, bottomY), strokeWidth = 1f)
        }

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Terrain,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.elevation_profile_empty_state),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(name = "ElevationProfileEmptyState Light", showBackground = true)
@Composable
private fun ElevationProfileEmptyStateLightPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface {
            ElevationProfileEmptyState()
        }
    }
}

@Preview(
    name = "ElevationProfileEmptyState Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
private fun ElevationProfileEmptyStateDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface {
            ElevationProfileEmptyState()
        }
    }
}

