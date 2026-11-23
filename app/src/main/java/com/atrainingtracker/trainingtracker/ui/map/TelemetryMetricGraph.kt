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

import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.CardioPowerDynamicsRecord
import com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculator
import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain
import com.atrainingtracker.trainingtracker.settings.TelemetryGraphColorPreferences
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.LinearHistogramBin
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramCalculator
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionData
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneTimeEntry
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import java.util.Locale
import kotlin.math.abs

/**
 * Optional test/diagnostic observer invoked whenever [TelemetryMetricGraph] or [ElevationProfile]
 * rebuilds its `Modifier.drawWithCache` curve geometry cache (`REQ-UI-2803.1`).
 */
internal val LocalTelemetryCurveCacheBuildListener = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Metric types supported by [TelemetryMetricGraph].
 * (REQ-UI-206 / ATT-1740)
 */
enum class TelemetryMetricType {
    HEART_RATE,
    SPEED,
    PACE,
    POWER,
    CADENCE,
    SLOPE,
    ALTITUDE,
    W_PRIME_BAL,
    HRV_DFA_A1,
    HRV_RMSSD
}


/**
 * High-performance, aesthetic continuous telemetry graph composable for Heart Rate,
 * Speed/Pace, and Cycling Power curves in Aftermath detailed inspection.
 * (REQ-UI-206 / ATT-1740)
 *
 * Invariant: Horizontal paddings strictly match [ElevationProfile] (start = 50.dp, end = 25.dp, bottom = 24.dp),
 * guaranteeing pixel-perfect vertical alignment of the horizontal axes and synchronized scrubbing markers across all charts.
 */
@Composable
fun TelemetryMetricGraph(
    pathPoints: List<PathPoint>,
    metricType: TelemetryMetricType,
    currentDistance: Double?,
    onDistanceSelected: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    onPointSelected: (PathPoint?) -> Unit = {},
    xAxisDomain: ProfileXAxisDomain = ProfileXAxisDomain.DISTANCE,
    bSportType: BSportType = BSportType.UNKNOWN,
    zoomScale: Float = 1.0f,
    startDist: Double = 0.0,
    isPanMode: Boolean = false,
    onZoomChanged: ((Float, Double) -> Unit)? = null,
    hrZoneThresholds: HeartRateZoneThresholds? = null,
    powerZoneThresholds: PowerZoneThresholds? = null,
    paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM,
    enableGestures: Boolean = true,
    zoneDistribution: ZoneDistributionData? = null,
    zoneDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.FIVE_ZONES,
    cardioPowerDynamics: CardioPowerDynamicsRecord? = null,
    enableCardioPowerDynamics: Boolean = true,
    showFreshModelOverlay: Boolean? = null,
    onShowFreshModelOverlayChange: ((Boolean) -> Unit)? = null,
    dfaA1ThresholdT1: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1,
    dfaA1ThresholdT2: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2,
    overlaySeries: List<TelemetrySourceSeries> = emptyList(),
    enabledOverlayIds: Set<String> = emptySet(),
    onToggleOverlay: ((String) -> Unit)? = null
) {
    if (pathPoints.isEmpty()) return

    var localShowFreshModel by rememberSaveable { mutableStateOf(false) }
    val effectiveShowFreshModel = showFreshModelOverlay ?: localShowFreshModel

    var localEnabledOverlayIds by rememberSaveable(metricType, overlaySeries.map { it.columnName }, enabledOverlayIds) {
        mutableStateOf(enabledOverlayIds)
    }
    val effectiveEnabledOverlayIds = if (onToggleOverlay != null) enabledOverlayIds else localEnabledOverlayIds
    val handleToggleOverlay: (String) -> Unit = onToggleOverlay ?: { colName ->
        localEnabledOverlayIds = if (colName in localEnabledOverlayIds) {
            localEnabledOverlayIds - colName
        } else {
            localEnabledOverlayIds + colName
        }
    }
    val hasMultiSourceOverlays = overlaySeries.size >= 2
    val activeOverlaySeries = remember(overlaySeries, effectiveEnabledOverlayIds, hasMultiSourceOverlays) {
        if (hasMultiSourceOverlays) {
            overlaySeries.filter { it.columnName in effectiveEnabledOverlayIds }
        } else {
            emptyList()
        }
    }

    val hasPowerData = remember(pathPoints) {
        pathPoints.any { it.power != null && it.power > 0 }
    }
    val canRenderFreshModelOverlay = metricType == TelemetryMetricType.HEART_RATE &&
        cardioPowerDynamics != null &&
        enableCardioPowerDynamics &&
        hasPowerData

    val freshHrTrajectory = remember(pathPoints, cardioPowerDynamics, canRenderFreshModelOverlay) {
        if (canRenderFreshModelOverlay) {
            val powerSamples = pathPoints.map { it.power?.toDouble() }
            val timeSecSamples = pathPoints.map { it.timeSec }
            PowerHrDynamicsCalculator.simulateTrajectory(
                powerSamples = powerSamples,
                timestampsSec = timeSecSamples,
                tau = cardioPowerDynamics.tauStart,
                hr0 = cardioPowerDynamics.hr0Start,
                k = cardioPowerDynamics.kStart
            )
        } else emptyList()
    }

    val context = LocalContext.current
    val effectiveHrThresholds = remember(hrZoneThresholds, bSportType, context) {
        val resolved = hrZoneThresholds ?: TelemetryZoneMath.loadHeartRateThresholds(context, bSportType)
        resolved?.takeIf { TelemetryZoneMath.isValidHeartRateThresholds(it) }
    }

    val effectivePowerThresholds = remember(powerZoneThresholds, context) {
        val resolved = powerZoneThresholds ?: TelemetryZoneMath.loadPowerThresholds(context)
        resolved?.takeIf { TelemetryZoneMath.isValidPowerThresholds(it) }
    }

    val unit = remember {
        try {
            TrainingApplication.getUnit()
        } catch (_: Exception) {
            MyUnits.METRIC
        }
    }

    val distanceFormatter = remember(unit) { DistanceFormatter() }

    val colorScheme = MaterialTheme.colorScheme
    val graphColorPrefs = remember(context) {
        TelemetryGraphColorPreferences.resolveSharedPreferences(context)
    }
    var graphColorRevision by remember { mutableIntStateOf(0) }
    val metricColorPrefKey = remember(metricType) {
        TelemetryGraphColorPreferences.preferenceKey(metricType)
    }
    DisposableEffect(graphColorPrefs, metricColorPrefKey) {
        if (graphColorPrefs == null) {
            onDispose {}
        } else {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == null ||
                    changedKey == metricColorPrefKey ||
                    changedKey.startsWith(TelemetryGraphColorPreferences.KEY_PREFIX)
                ) {
                    graphColorRevision++
                }
            }
            graphColorPrefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose {
                graphColorPrefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }

    val accentColor = remember(metricType, colorScheme, graphColorPrefs, graphColorRevision) {
        TelemetryGraphColorPreferences.resolveAccentColor(
            prefs = graphColorPrefs,
            metricType = metricType,
            colorScheme = colorScheme
        )
    }

    val isTrackless = remember(pathPoints) {
        ProfileDomainMath.isTracklessWorkout(pathPoints)
    }
    val isTimeDomain = remember(xAxisDomain, pathPoints) {
        ProfileDomainMath.isEffectiveTimeDomain(xAxisDomain, pathPoints)
    }
    val totalSpan = remember(xAxisDomain, pathPoints) {
        ProfileDomainMath.calculateTotalSpan(xAxisDomain, pathPoints)
    }
    val visibleSpan = remember(totalSpan, zoomScale) {
        ElevationProfileZoomMath.calculateVisibleDistance(totalSpan, zoomScale)
    }

    // Extract scalar values
    val rawValues = remember(pathPoints, metricType, unit, paceCeilingMinKm) {
        pathPoints.map { TelemetryMetricUtils.extractMetricValue(it, metricType, unit, paceCeilingMinKm) }
    }

    val validValues = remember(rawValues) { rawValues.filterNotNull() }
    if (validValues.isEmpty()) return

    // Entire-workout overall average anchored independently of zoom/pan viewport (REQ-UI-2797.1 / ATT-9170)
    val workoutAverage = remember(pathPoints, metricType, unit) {
        TelemetryMetricUtils.computeWorkoutAverage(pathPoints, metricType, unit)
    }

    val (dataMin, dataMax) = remember(
        pathPoints,
        rawValues,
        validValues,
        metricType,
        paceCeilingMinKm,
        unit,
        canRenderFreshModelOverlay,
        effectiveShowFreshModel,
        freshHrTrajectory,
        activeOverlaySeries
    ) {
        val baseValues = if (metricType == TelemetryMetricType.HEART_RATE &&
            canRenderFreshModelOverlay &&
            effectiveShowFreshModel &&
            freshHrTrajectory.isNotEmpty()
        ) {
            validValues + freshHrTrajectory.filterNotNull()
        } else {
            validValues
        }
        val effectiveValues = if (activeOverlaySeries.isNotEmpty()) {
            baseValues + activeOverlaySeries.flatMap { series ->
                series.points.mapNotNull { pt ->
                    resolveOverlayValueForChart(pt.value, metricType, unit, paceCeilingMinKm)
                }
            }
        } else {
            baseValues
        }
        val min = effectiveValues.minOrNull() ?: 0.0
        val max = effectiveValues.maxOrNull() ?: 1.0
        when (metricType) {
            TelemetryMetricType.POWER -> {
                0.0 to (max * 1.1).coerceAtLeast(50.0)
            }
            TelemetryMetricType.CADENCE -> {
                0.0 to (max * 1.15).coerceAtLeast(100.0)
            }
            TelemetryMetricType.SLOPE -> {
                val span = (max - min).coerceAtLeast(2.0)
                val pad = span * 0.1
                val boundMin = if (min >= 0.0) 0.0 else (min - pad)
                val boundMax = if (max <= 0.0) 0.0 else (max + pad)
                if (boundMax - boundMin < 2.0) {
                    (boundMin - 1.0) to (boundMax + 1.0)
                } else {
                    boundMin to boundMax
                }
            }
            TelemetryMetricType.ALTITUDE -> {
                val span = (max - min).coerceAtLeast(10.0)
                val pad = span * 0.05
                (min - pad) to (max + pad)
            }
            TelemetryMetricType.W_PRIME_BAL -> {
                0.0 to (max * 1.1).coerceAtLeast(10.0)
            }
            TelemetryMetricType.HEART_RATE -> {
                (min - 5.0).coerceAtLeast(30.0) to (max + 5.0).coerceAtLeast(100.0)
            }
            TelemetryMetricType.SPEED -> {
                0.0 to (max * 1.1).coerceAtLeast(5.0)
            }
            TelemetryMetricType.PACE -> {
                val effectiveCeiling = if (unit == MyUnits.METRIC) {
                    paceCeilingMinKm.toDouble()
                } else {
                    paceCeilingMinKm.toDouble() * (BANALService.METER_PER_MILE / 1000.0)
                }
                // For Pace: min is fastest, max is slowest
                val paceMin = (min * 0.95).coerceAtLeast(effectiveCeiling)
                val paceMax = (max * 1.05).coerceAtMost(20.0)
                paceMin to paceMax.coerceAtLeast(paceMin + 1.0)
            }
            TelemetryMetricType.HRV_DFA_A1 -> {
                // Clamped to [0.2, 1.4] with outlier resilience per REQ-PER-035.3 & Rule 48
                0.2 to 1.4
            }
            TelemetryMetricType.HRV_RMSSD -> {
                // Exclude high-artifact primary samples (> 5.0%) with fallback, preserving overlays (REQ-HRV-047.2)
                val cleanPrimary = pathPoints.indices.mapNotNull { idx ->
                    val v = rawValues.getOrNull(idx)
                    val rate = pathPoints[idx].hrvArtifactRate
                    if (v != null && (rate == null || rate <= 5.0)) v else null
                }
                val overlayValues = activeOverlaySeries.flatMap { series ->
                    series.points.mapNotNull { pt ->
                        resolveOverlayValueForChart(pt.value, metricType, unit, paceCeilingMinKm)
                    }
                }
                TelemetryZoneMath.calculateHrvRmssdYAxisBounds(
                    cleanPrimary.ifEmpty { validValues } + overlayValues
                )
            }
        }
    }

    val visibleAverageValue = remember(workoutAverage, dataMin, dataMax) {
        resolveVisibleAverageValue(workoutAverage, dataMin, dataMax)
    }

    val axisTextPaint = remember(colorScheme) {
        Paint().apply {
            color = colorScheme.onSurfaceVariant.toArgb()
            textSize = 28f
            isAntiAlias = true
        }
    }

    val averageLabelPaint = remember(colorScheme) {
        Paint().apply {
            color = colorScheme.onSurfaceVariant.toArgb()
            textSize = 28f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    val zoneBands = remember(metricType, effectiveHrThresholds, effectivePowerThresholds, dfaA1ThresholdT1, dfaA1ThresholdT2, dataMin, dataMax) {
        when (metricType) {
            TelemetryMetricType.HEART_RATE -> effectiveHrThresholds?.let {
                TelemetryZoneMath.calculateHeartRateZoneBands(it, dataMin, dataMax)
            } ?: emptyList()
            TelemetryMetricType.POWER -> effectivePowerThresholds?.let {
                TelemetryZoneMath.calculatePowerZoneBands(it, dataMin, dataMax)
            } ?: emptyList()
            TelemetryMetricType.HRV_DFA_A1 -> TelemetryZoneMath.calculateHrvDfaA1ZoneBands(
                t1 = dfaA1ThresholdT1,
                t2 = dfaA1ThresholdT2,
                dataMin = dataMin,
                dataMax = dataMax
            )
            else -> emptyList()
        }
    }

    val isDarkSurface = remember(colorScheme) {
        TTColor.relativeLuminance(colorScheme.surface) < 0.5
    }

    val zoneStrokeStops = remember(
        metricType, zoneBands, effectiveHrThresholds, effectivePowerThresholds,
        dfaA1ThresholdT1, dfaA1ThresholdT2, dataMin, dataMax, isDarkSurface
    ) {
        TelemetryZoneMath.buildMetricZoneGradientStops(
            metricType, zoneBands, effectiveHrThresholds, effectivePowerThresholds,
            dfaA1ThresholdT1, dfaA1ThresholdT2, dataMin, dataMax, isDarkSurface,
            topAlpha = 1f, bottomAlpha = 1f
        )
    }

    val zoneFillStops = remember(
        metricType, zoneBands, effectiveHrThresholds, effectivePowerThresholds,
        dfaA1ThresholdT1, dfaA1ThresholdT2, dataMin, dataMax, isDarkSurface
    ) {
        TelemetryZoneMath.buildMetricZoneGradientStops(
            metricType, zoneBands, effectiveHrThresholds, effectivePowerThresholds,
            dfaA1ThresholdT1, dfaA1ThresholdT2, dataMin, dataMax, isDarkSurface,
            topAlpha = 0.22f, bottomAlpha = 0.04f
        )
    }

    val useZoneColoredCurve = zoneStrokeStops.size >= 2 && zoneFillStops.size >= 2

    val thresholdDashes = remember(metricType, effectiveHrThresholds, effectivePowerThresholds, dfaA1ThresholdT1, dfaA1ThresholdT2, dataMin, dataMax) {
        when (metricType) {
            TelemetryMetricType.HEART_RATE -> effectiveHrThresholds?.let {
                TelemetryZoneMath.calculateThresholdDashes(it.z1Max, it.z2Max, it.z3Max, it.z4Max, dataMin, dataMax)
            } ?: emptyList()
            TelemetryMetricType.POWER -> effectivePowerThresholds?.let {
                TelemetryZoneMath.calculatePowerThresholdDashes(it, dataMin, dataMax)
            } ?: emptyList()
            TelemetryMetricType.HRV_DFA_A1 -> TelemetryZoneMath.calculateHrvDfaA1ThresholdDashes(
                t1 = dfaA1ThresholdT1,
                t2 = dfaA1ThresholdT2,
                dataMin = dataMin,
                dataMax = dataMax
            )
            else -> emptyList()
        }
    }

    val zoneLabelPaint = remember(colorScheme) {
        Paint().apply {
            color = colorScheme.onSurfaceVariant.toArgb()
            textSize = 24f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val currentLocale = remember(configuration) {
        configuration.locales.let { if (it.isEmpty) Locale.getDefault() else it[0] }
    }
    val curveCacheBuildListener = LocalTelemetryCurveCacheBuildListener.current
    val currentDistanceState = rememberUpdatedState(currentDistance)

    val canvasModifier = Modifier
        .fillMaxWidth()
        .height(110.dp)
        .telemetryChartGestures(
            enabled = enableGestures,
            totalSpan = totalSpan,
            isTimeDomain = isTimeDomain,
            isTrackless = isTrackless,
            isPanMode = isPanMode,
            startPadding = 50.dp,
            endPadding = if (isLandscape) 0.dp else 52.dp,
            zoomScale = zoomScale,
            startDist = startDist,
            pathPoints = pathPoints,
            indexSemantics = ChartScrubIndexSemantics.NEAREST,
            onZoomChanged = onZoomChanged,
            onDistanceSelected = onDistanceSelected,
            onPointSelected = onPointSelected
        )

    val nonZonedHistogram = remember(rawValues, pathPoints, dataMin, dataMax, accentColor, zoneDistribution, metricType, dfaA1ThresholdT1, dfaA1ThresholdT2) {
        if (zoneDistribution != null || validValues.isEmpty() || dataMax <= dataMin) {
            emptyList()
        } else {
            val timestamps = pathPoints.map { it.timeSec }
            val rawBins = TelemetryHistogramCalculator.computeLinearMarginalHistogram(
                values = rawValues,
                timestamps = timestamps,
                dataMin = dataMin,
                dataMax = dataMax,
                numBins = 20,
                color = accentColor
            )
            if (metricType == TelemetryMetricType.HRV_DFA_A1) {
                rawBins.map { bin ->
                    val midAlpha1 = (bin.rangeMin + bin.rangeMax) / 2.0
                    bin.copy(
                        color = TelemetryZoneMath.resolveHrvDfaA1ZoneColor(
                            alpha1 = midAlpha1,
                            t1 = dfaA1ThresholdT1,
                            t2 = dfaA1ThresholdT2
                        )
                    )
                }
            } else {
                rawBins
            }
        }
    }

    val drawCacheBuilder: CacheDrawScope.() -> DrawResult = remember(
        pathPoints,
        rawValues,
        metricType,
        isTimeDomain,
        isTrackless,
        totalSpan,
        zoomScale,
        startDist,
        visibleSpan,
        dataMin,
        dataMax,
        isLandscape,
        currentLocale,
        accentColor,
        useZoneColoredCurve,
        zoneBands,
        zoneStrokeStops,
        zoneFillStops,
        thresholdDashes,
        effectiveHrThresholds,
        effectivePowerThresholds,
        dfaA1ThresholdT1,
        dfaA1ThresholdT2,
        isDarkSurface,
        zoneDistribution,
        zoneDisplayMode,
        nonZonedHistogram,
        visibleAverageValue,
        canRenderFreshModelOverlay,
        effectiveShowFreshModel,
        freshHrTrajectory,
        activeOverlaySeries,
        overlaySeries,
        unit,
        paceCeilingMinKm,
        colorScheme,
        axisTextPaint,
        averageLabelPaint,
        zoneLabelPaint,
        distanceFormatter,
        curveCacheBuildListener
    ) {
        {
            val startPaddingPx = 50.dp.toPx()
            val endPaddingPx = if (isLandscape) 0.dp.toPx() else 52.dp.toPx()
            val bottomPaddingPx = 24.dp.toPx()
            val topPaddingPx = 10.dp.toPx()

            val rawChartWidthPx = size.width - startPaddingPx - endPaddingPx
            val chartWidthPx = rawChartWidthPx.coerceAtLeast(1f)
            val chartHeightPx = (size.height - topPaddingPx - bottomPaddingPx).coerceAtLeast(1f)
            val isInvertedYAxis = TelemetryZoneMath.isInvertedTelemetryYAxis(metricType)
            val isHrvMetric = metricType == TelemetryMetricType.HRV_DFA_A1 || metricType == TelemetryMetricType.HRV_RMSSD
            val baselineY = topPaddingPx + chartHeightPx

            fun valueToY(v: Double): Float = TelemetryZoneMath.mapTelemetryValueToY(
                value = v,
                dataMin = dataMin,
                dataMax = dataMax,
                topPaddingPx = topPaddingPx,
                chartHeightPx = chartHeightPx,
                isInverted = isInvertedYAxis
            )

            // Build viewport-downsampled curve paths, brushes, PathEffects, and overlays ONCE per
            // geometry/viewport change so 60-Hz scrubbing performs zero curve path allocations (REQ-UI-2803.1).
            val cachedCurves = buildCachedTelemetryCurveGeometry(
                pathPoints = pathPoints,
                rawValues = rawValues,
                metricType = metricType,
                isTimeDomain = isTimeDomain,
                startDist = startDist,
                visibleSpan = visibleSpan,
                dataMin = dataMin,
                dataMax = dataMax,
                startPaddingPx = startPaddingPx,
                topPaddingPx = topPaddingPx,
                chartWidthPx = chartWidthPx,
                chartHeightPx = chartHeightPx,
                densityPxPerDp = density,
                accentColor = accentColor,
                useZoneColoredCurve = useZoneColoredCurve,
                zoneStrokeStops = zoneStrokeStops,
                zoneFillStops = zoneFillStops,
                effectiveHrThresholds = effectiveHrThresholds,
                effectivePowerThresholds = effectivePowerThresholds,
                dfaA1ThresholdT1 = dfaA1ThresholdT1,
                dfaA1ThresholdT2 = dfaA1ThresholdT2,
                isDarkSurface = isDarkSurface,
                canRenderFreshModelOverlay = canRenderFreshModelOverlay,
                effectiveShowFreshModel = effectiveShowFreshModel,
                freshHrTrajectory = freshHrTrajectory,
                activeOverlaySeries = activeOverlaySeries,
                allOverlaySeries = overlaySeries,
                unit = unit,
                paceCeilingMinKm = paceCeilingMinKm
            )
            curveCacheBuildListener?.invoke()

            onDrawBehind {
                val nativeCanvas = drawContext.canvas.nativeCanvas

                // 1. Draw horizontal background training zone bands (REQ-UI-230 / ATT-1839)
                if (zoneBands.isNotEmpty()) {
                    for (band in zoneBands) {
                        val yTop = valueToY(band.maxVal)
                        val yBottom = valueToY(band.minVal)
                        val rectTop = minOf(yTop, yBottom)
                        val rectHeight = abs(yTop - yBottom)

                        drawRect(
                            color = band.color.copy(alpha = TelemetryZoneMath.ZONE_BAND_ALPHA),
                            topLeft = Offset(startPaddingPx, rectTop),
                            size = Size(chartWidthPx, rectHeight)
                        )

                        // Draw right-hand secondary zone axis label (Z1-Z5) only if marginal bars/histograms are not rendered
                        if (zoneDistribution == null && nonZonedHistogram.isEmpty() && !isLandscape) {
                            if (TelemetryZoneMath.shouldRenderZoneLabel(rectHeight, 12.dp.toPx())) {
                                val yCenter = rectTop + (rectHeight / 2f)
                                val textBaseline = yCenter - ((zoneLabelPaint.descent() + zoneLabelPaint.ascent()) / 2f)
                                val rightAxisCenterX = startPaddingPx + chartWidthPx + (endPaddingPx / 2f)
                                nativeCanvas.drawText(band.label, rightAxisCenterX, textBaseline, zoneLabelPaint)
                            }
                        }
                    }
                }

                // 1b. Draw minimalist marginal zone bars / histogram (REQ-UI-251 / ATT-2147 / REQ-UI-446 / ATT-5370)
                if (zoneDistribution != null && !isLandscape) {
                    val stripStartPx = startPaddingPx + chartWidthPx + 3.dp.toPx()
                    val stripWidthPx = (endPaddingPx - 8.dp.toPx()).coerceAtLeast(10.dp.toPx())

                    if (zoneDisplayMode == ZoneCardDisplayMode.FIVE_ZONES || zoneDisplayMode == ZoneCardDisplayMode.COLUMNS || zoneDisplayMode == ZoneCardDisplayMode.STACKED_BAR) {
                        for (entry in zoneDistribution.entries) {
                            val band = zoneBands.getOrNull(entry.zoneIndex - 1)
                            if (band != null) {
                                val yTop = valueToY(band.maxVal)
                                val yBottom = valueToY(band.minVal)
                                val rectTop = minOf(yTop, yBottom)
                                val rectHeight = abs(yTop - yBottom)

                                if (rectHeight > 0f) {
                                    drawRect(
                                        color = entry.color.copy(alpha = 0.15f),
                                        topLeft = Offset(stripStartPx, rectTop),
                                        size = Size(stripWidthPx, rectHeight)
                                    )
                                    val barWidthPx = (entry.percentage / 100f).coerceIn(0f, 1f) * stripWidthPx
                                    if (barWidthPx > 0f) {
                                        drawRect(
                                            color = entry.color,
                                            topLeft = Offset(stripStartPx, rectTop),
                                            size = Size(barWidthPx, rectHeight)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (zoneDisplayMode == ZoneCardDisplayMode.HISTOGRAM && zoneDistribution.histogram != null) {
                        drawMarginalHistogramBars(
                            bins = zoneDistribution.histogram.linearBins,
                            dataMin = dataMin,
                            dataMax = dataMax,
                            stripStartPx = stripStartPx,
                            stripWidthPx = stripWidthPx,
                            valueToY = ::valueToY,
                            rangeMinOf = { it.rangeMin.toDouble() },
                            rangeMaxOf = { it.rangeMax.toDouble() },
                            durationSecOf = { it.durationSec },
                            percentageOf = { it.percentage },
                            colorOf = { it.color },
                            minBarHeightPx = 1f
                        )
                    }
                } else if (nonZonedHistogram.isNotEmpty() && !isLandscape) {
                    val stripStartPx = startPaddingPx + chartWidthPx + 3.dp.toPx()
                    val stripWidthPx = (endPaddingPx - 8.dp.toPx()).coerceAtLeast(10.dp.toPx())
                    drawMarginalHistogramBars(
                        bins = nonZonedHistogram,
                        dataMin = dataMin,
                        dataMax = dataMax,
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

                // 2. Draw horizontal threshold / fallback nice tick / zero-reference boundary guidelines (REQ-UI-230, REQ-UI-2798.3)
                val glyphAscent = maxOf(axisTextPaint.ascent(), -axisTextPaint.textSize * 0.80f)
                val glyphDescent = minOf(axisTextPaint.descent(), axisTextPaint.textSize * 0.12f)
                val minLabelGapPx = 2.dp.toPx()
                val labelSlotPx = (glyphDescent - glyphAscent + minLabelGapPx).coerceAtLeast(1f)
                val isUnzonedHrOrPower = (metricType == TelemetryMetricType.HEART_RATE && effectiveHrThresholds == null) ||
                    (metricType == TelemetryMetricType.POWER && effectivePowerThresholds == null)
                val fallbackNiceTicks = if (isUnzonedHrOrPower) {
                    val maxTickCount = ((chartHeightPx / labelSlotPx).toInt() - 1).coerceIn(1, 4)
                    TelemetryZoneMath.calculateNiceIntermediateTicks(metricType, dataMin, dataMax, maxTickCount)
                } else {
                    emptyList()
                }

                if (thresholdDashes.isNotEmpty()) {
                    for (dashVal in thresholdDashes) {
                        val dashY = valueToY(dashVal)
                        drawLine(
                            color = colorScheme.outlineVariant.copy(alpha = 0.35f),
                            start = Offset(startPaddingPx, dashY),
                            end = Offset(startPaddingPx + chartWidthPx, dashY),
                            strokeWidth = 1f,
                            pathEffect = cachedCurves.thresholdDashEffect
                        )
                    }
                } else if (fallbackNiceTicks.isNotEmpty()) {
                    for (tickVal in fallbackNiceTicks) {
                        val tickY = valueToY(tickVal)
                        drawLine(
                            color = colorScheme.outlineVariant.copy(alpha = 0.25f),
                            start = Offset(startPaddingPx, tickY),
                            end = Offset(startPaddingPx + chartWidthPx, tickY),
                            strokeWidth = 1f,
                            pathEffect = cachedCurves.thresholdDashEffect
                        )
                    }
                } else if (metricType == TelemetryMetricType.SLOPE && dataMin < 0.0 && dataMax > 0.0) {
                    val zeroY = valueToY(0.0)
                    drawLine(
                        color = colorScheme.outlineVariant.copy(alpha = 0.5f),
                        start = Offset(startPaddingPx, zeroY),
                        end = Offset(startPaddingPx + chartWidthPx, zeroY),
                        strokeWidth = 1f,
                        pathEffect = cachedCurves.thresholdDashEffect
                    )
                }

                // Draw Y-axis labels with priority-based collision avoidance (Ø > zone/threshold/nice ticks > extrema; REQ-HRV-046.3, REQ-UI-2797.2, REQ-UI-2798.3)
                val topAxisValue = if (isInvertedYAxis) dataMin else dataMax
                val bottomAxisValue = if (isInvertedYAxis) dataMax else dataMin
                val maxLabel = TelemetryMetricUtils.formatYAxisValue(topAxisValue, metricType, currentLocale)
                val minLabel = TelemetryMetricUtils.formatYAxisValue(bottomAxisValue, metricType, currentLocale)
                val topBaselineY = topPaddingPx + 10f
                val bottomBaselineY = topPaddingPx + chartHeightPx
                val minInteriorBaselineY = maxOf(topBaselineY, -axisTextPaint.ascent() + 2f)

                val intermediateAxisValues = when {
                    metricType == TelemetryMetricType.HRV_DFA_A1 ||
                        metricType == TelemetryMetricType.HEART_RATE ||
                        metricType == TelemetryMetricType.POWER ->
                        if (thresholdDashes.isNotEmpty()) thresholdDashes else fallbackNiceTicks
                    else -> emptyList()
                }

                val yAxisCandidates = buildList {
                    if (visibleAverageValue != null) {
                        val avgLabel = TelemetryMetricUtils.formatAverageYAxisLabel(visibleAverageValue, metricType, currentLocale)
                        if (avgLabel.isNotEmpty()) {
                            val avgBaselineY = computeCenteredYAxisBaseline(
                                anchorY = valueToY(visibleAverageValue),
                                ascent = axisTextPaint.ascent(),
                                descent = axisTextPaint.descent(),
                                minBaselineY = minInteriorBaselineY,
                                maxBaselineY = bottomBaselineY
                            )
                            add(
                                TelemetryYAxisLabelCandidate(
                                    id = "avg",
                                    text = avgLabel,
                                    baselineY = avgBaselineY,
                                    priority = Y_AXIS_PRIORITY_AVERAGE,
                                    alignment = TelemetryYAxisLabelAlignment.RIGHT_GUTTER
                                )
                            )
                        }
                    }
                    intermediateAxisValues.forEachIndexed { idx, axisVal ->
                        val axisBaselineY = computeCenteredYAxisBaseline(
                            anchorY = valueToY(axisVal),
                            ascent = axisTextPaint.ascent(),
                            descent = axisTextPaint.descent(),
                            minBaselineY = minInteriorBaselineY,
                            maxBaselineY = bottomBaselineY
                        )
                        add(
                            TelemetryYAxisLabelCandidate(
                                id = "threshold_$idx",
                                text = TelemetryMetricUtils.formatYAxisValue(axisVal, metricType, currentLocale),
                                baselineY = axisBaselineY,
                                priority = Y_AXIS_PRIORITY_THRESHOLD,
                                alignment = TelemetryYAxisLabelAlignment.LEFT_GUTTER
                            )
                        )
                    }
                    add(
                        TelemetryYAxisLabelCandidate(
                            id = "top",
                            text = maxLabel,
                            baselineY = topBaselineY,
                            priority = Y_AXIS_PRIORITY_EXTREMA,
                            alignment = TelemetryYAxisLabelAlignment.LEFT_GUTTER
                        )
                    )
                    add(
                        TelemetryYAxisLabelCandidate(
                            id = "bottom",
                            text = minLabel,
                            baselineY = bottomBaselineY,
                            priority = Y_AXIS_PRIORITY_EXTREMA,
                            alignment = TelemetryYAxisLabelAlignment.LEFT_GUTTER
                        )
                    )
                }

                val placedYAxisLabels = resolveTelemetryYAxisLabels(
                    candidates = yAxisCandidates,
                    ascent = glyphAscent,
                    descent = glyphDescent,
                    minVerticalGapPx = minLabelGapPx
                )
                val gutterLeftPx = 2.dp.toPx()
                val gutterRightPx = (startPaddingPx - 4.dp.toPx()).coerceAtLeast(gutterLeftPx)
                val maxGutterWidthPx = (gutterRightPx - gutterLeftPx).coerceAtLeast(1f)
                for (placed in placedYAxisLabels) {
                    if (placed.alignment == TelemetryYAxisLabelAlignment.RIGHT_GUTTER) {
                        averageLabelPaint.textSize = axisTextPaint.textSize
                        val rawWidth = averageLabelPaint.measureText(placed.text)
                        averageLabelPaint.textSize = computeFittedGutterTextSize(
                            baseTextSizePx = axisTextPaint.textSize,
                            measuredTextWidthPx = rawWidth,
                            maxGutterWidthPx = maxGutterWidthPx
                        )
                        val fittedWidth = averageLabelPaint.measureText(placed.text)
                        val labelX = computeRightAlignedGutterX(
                            textWidthPx = fittedWidth,
                            gutterLeftPx = gutterLeftPx,
                            gutterRightPx = gutterRightPx
                        )
                        nativeCanvas.drawText(placed.text, labelX, placed.baselineY, averageLabelPaint)
                    } else {
                        nativeCanvas.drawText(placed.text, 6.dp.toPx(), placed.baselineY, axisTextPaint)
                    }
                }

                // Draw horizontal boundary grid lines
                drawLine(
                    color = colorScheme.outlineVariant.copy(alpha = 0.35f),
                    start = Offset(startPaddingPx, topPaddingPx),
                    end = Offset(startPaddingPx + chartWidthPx, topPaddingPx),
                    strokeWidth = 1f
                )
                drawLine(
                    color = colorScheme.outlineVariant.copy(alpha = 0.35f),
                    start = Offset(startPaddingPx, topPaddingPx + chartHeightPx),
                    end = Offset(startPaddingPx + chartWidthPx, topPaddingPx + chartHeightPx),
                    strokeWidth = 1f
                )

                // Draw X-axis boundary labels (start and end) (REQ-UI-220 / ATT-1819)
                val labelY = size.height - 4.dp.toPx()
                val endLabel = if (isTimeDomain) {
                    ElevationProfileZoomMath.formatTimeTick((startDist + visibleSpan).toLong())
                } else {
                    distanceFormatter.format_with_units(startDist + visibleSpan)
                }
                val endLabelWidth = axisTextPaint.measureText(endLabel)
                nativeCanvas.drawText(
                    endLabel,
                    startPaddingPx + chartWidthPx - endLabelWidth,
                    labelY,
                    axisTextPaint
                )

                val lastDrawnRightX = if (zoomScale > 1.01f) {
                    val startLabel = if (isTimeDomain) {
                        ElevationProfileZoomMath.formatTimeTick(startDist.toLong())
                    } else {
                        distanceFormatter.format_with_units(startDist)
                    }
                    nativeCanvas.drawText(startLabel, startPaddingPx, labelY, axisTextPaint)
                    startPaddingPx + axisTextPaint.measureText(startLabel)
                } else {
                    startPaddingPx
                }

                val startBoundaryThreshold = startPaddingPx + 40.dp.toPx()
                val endBoundaryThreshold = startPaddingPx + chartWidthPx - (endLabelWidth + 16.dp.toPx())
                val minMilestoneSpacing = 12.dp.toPx()

                // Draw X-axis ticks along bottom (REQ-UI-220, REQ-UI-2789.2)
                val xAxisStep = if (isTimeDomain) {
                    ElevationProfileZoomMath.calculateAdaptiveTimeStep(visibleSpan).toDouble()
                } else {
                    ElevationProfileZoomMath.calculateAdaptiveDistanceStep(visibleSpan, unit).toDouble()
                }
                drawXAxisTicks(
                    startValue = startDist,
                    visibleSpan = visibleSpan,
                    step = xAxisStep,
                    plotStartX = startPaddingPx,
                    plotWidth = chartWidthPx,
                    paint = axisTextPaint,
                    labelY = labelY,
                    initialLastDrawnRightX = lastDrawnRightX,
                    formatLabel = { value ->
                        if (isTimeDomain) {
                            ElevationProfileZoomMath.formatTimeTick(value.toLong())
                        } else {
                            TelemetryMetricUtils.formatMilestoneLabel(value, visibleSpan, unit)
                        }
                    },
                    shouldRenderLabel = { labelLeft, labelRight, prevRightX ->
                        TelemetryMetricUtils.shouldRenderMilestoneLabel(
                            labelLeft = labelLeft,
                            labelRight = labelRight,
                            lastDrawnRightX = prevRightX,
                            startBoundaryThreshold = startBoundaryThreshold,
                            endBoundaryThreshold = endBoundaryThreshold,
                            minSpacing = minMilestoneSpacing
                        )
                    },
                    onTickInBounds = { tickX ->
                        drawLine(
                            color = colorScheme.outlineVariant.copy(alpha = 0.3f),
                            start = Offset(tickX, topPaddingPx),
                            end = Offset(tickX, topPaddingPx + chartHeightPx),
                            strokeWidth = 1f
                        )
                    },
                    onLabelRendered = { _, label, labelLeft, y ->
                        nativeCanvas.drawText(label, labelLeft, y, axisTextPaint)
                    }
                )

                // Replay cached viewport curve paths, artifact gap hatching, and overlays (REQ-UI-2803.1)
                clipRect(
                    left = startPaddingPx,
                    top = topPaddingPx,
                    right = startPaddingPx + chartWidthPx,
                    bottom = topPaddingPx + chartHeightPx
                ) {
                    if (isHrvMetric && cachedCurves.gapIntervals.isNotEmpty()) {
                        for (gap in cachedCurves.gapIntervals) {
                            val gLeft = maxOf(startPaddingPx, minOf(gap.startX, gap.endX))
                            val gRight = minOf(startPaddingPx + chartWidthPx, maxOf(gap.startX, gap.endX))
                            if (gRight > gLeft) {
                                clipRect(left = gLeft, top = topPaddingPx, right = gRight, bottom = baselineY) {
                                    drawRect(
                                        color = colorScheme.outlineVariant.copy(alpha = 0.12f),
                                        topLeft = Offset(gLeft, topPaddingPx),
                                        size = Size(gRight - gLeft, chartHeightPx)
                                    )
                                    val hatchSpacing = 10.dp.toPx()
                                    var hatchX = gLeft - chartHeightPx
                                    while (hatchX < gRight + chartHeightPx) {
                                        drawLine(
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
                                            start = Offset(hatchX, baselineY),
                                            end = Offset(hatchX + chartHeightPx, topPaddingPx),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                        hatchX += hatchSpacing
                                    }
                                }
                            }
                        }
                    }

                    for (dot in cachedCurves.singleDots) {
                        drawCircle(
                            color = dot.color,
                            radius = 2.5.dp.toPx(),
                            center = dot.center
                        )
                    }

                    for (idx in cachedCurves.fillPaths.indices) {
                        drawPath(path = cachedCurves.fillPaths[idx], brush = cachedCurves.fillBrush)
                        val strokeBrush = cachedCurves.strokeBrush
                        if (strokeBrush != null) {
                            drawPath(
                                path = cachedCurves.strokePaths[idx],
                                brush = strokeBrush,
                                style = cachedCurves.curveStroke
                            )
                        } else {
                            drawPath(
                                path = cachedCurves.strokePaths[idx],
                                color = accentColor,
                                style = cachedCurves.curveStroke
                            )
                        }
                    }

                    cachedCurves.calibWindowBounds?.let { (leftX, rightX) ->
                        drawRect(
                            color = TTColor.Zone2.copy(alpha = 0.08f),
                            topLeft = Offset(leftX, topPaddingPx),
                            size = Size(rightX - leftX, chartHeightPx)
                        )
                    }
                    for (driftPoly in cachedCurves.driftPolygons) {
                        drawPath(driftPoly, color = TTColor.Zone4.copy(alpha = 0.22f))
                    }
                    val freshPath = cachedCurves.freshModelPath
                    val freshStroke = cachedCurves.freshModelStroke
                    if (freshPath != null && freshStroke != null) {
                        drawPath(path = freshPath, color = TTColor.Zone2, style = freshStroke)
                    }

                    for (overlayCurve in cachedCurves.overlayCurves) {
                        drawPath(
                            path = overlayCurve.path,
                            color = overlayCurve.color,
                            style = overlayCurve.stroke
                        )
                    }
                }

                // Dotted grey entire-workout average reference line across plot area (REQ-UI-2797.3 / ATT-9170)
                if (visibleAverageValue != null && rawChartWidthPx > 0f) {
                    drawAverageReferenceLine(
                        avgY = valueToY(visibleAverageValue),
                        plotStartX = startPaddingPx,
                        plotWidth = rawChartWidthPx,
                        color = colorScheme.onSurfaceVariant,
                        pathEffect = cachedCurves.averageDotEffect
                    )
                }

                // Synchronized Scrubbing Cursor & Marker Dot (reads currentDistanceState strictly in draw pass)
                val activeScrubDistance = currentDistanceState.value
                if (activeScrubDistance != null) {
                    val cursorDistSpan = if (isTimeDomain) {
                        if (isTrackless) {
                            activeScrubDistance
                        } else {
                            val nearestPt = TelemetryMetricUtils.findNearestPoint(
                                points = pathPoints,
                                targetValue = activeScrubDistance,
                                isTimeDomain = false
                            )
                            (nearestPt?.timeSec ?: 0L).toDouble()
                        }
                    } else {
                        activeScrubDistance
                    }

                    if (cursorDistSpan in 0.0..totalSpan && cursorDistSpan in startDist..(startDist + visibleSpan)) {
                        val cursorX = (startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(cursorDistSpan, startDist, visibleSpan, chartWidthPx))
                            .coerceIn(startPaddingPx, startPaddingPx + chartWidthPx)

                        drawLine(
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            start = Offset(cursorX, topPaddingPx),
                            end = Offset(cursorX, topPaddingPx + chartHeightPx),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = cachedCurves.cursorDashEffect
                        )

                        val nearestPoint = TelemetryMetricUtils.findNearestPoint(
                            points = pathPoints,
                            targetValue = activeScrubDistance,
                            isTimeDomain = isTrackless && isTimeDomain
                        )
                        if (nearestPoint != null) {
                            val metricVal = TelemetryMetricUtils.extractMetricValue(nearestPoint, metricType, unit, paceCeilingMinKm)
                            if (metricVal != null) {
                                val cursorY = valueToY(metricVal)
                                val cursorColor = TelemetryZoneMath.resolveMetricZoneStrokeColor(
                                    metricType = metricType,
                                    value = metricVal,
                                    hrThresholds = effectiveHrThresholds,
                                    powerThresholds = effectivePowerThresholds,
                                    dfaA1ThresholdT1 = dfaA1ThresholdT1,
                                    dfaA1ThresholdT2 = dfaA1ThresholdT2,
                                    isDarkSurface = isDarkSurface
                                ) ?: accentColor

                                drawCircle(
                                    color = cursorColor.copy(alpha = 0.35f),
                                    radius = 6.dp.toPx(),
                                    center = Offset(cursorX, cursorY)
                                )
                                drawCircle(
                                    color = cursorColor,
                                    radius = 3.5.dp.toPx(),
                                    center = Offset(cursorX, cursorY)
                                )
                            }
                        }

                        if (activeOverlaySeries.isNotEmpty()) {
                            drawMultiSensorOverlayScrubDots(
                                overlaySeries = activeOverlaySeries,
                                allOverlaySeries = overlaySeries,
                                targetValue = cursorDistSpan,
                                isTimeDomain = isTimeDomain,
                                cursorX = cursorX,
                                metricType = metricType,
                                unit = unit,
                                paceCeilingMinKm = paceCeilingMinKm,
                                valueToY = ::valueToY,
                                isDarkSurface = isDarkSurface,
                                avoidZoneColors = useZoneColoredCurve
                            )
                        }
                    }
                }
            }
        }
    }

    val chartCanvas: @Composable () -> Unit = {
        Spacer(modifier = canvasModifier.drawWithCache(drawCacheBuilder))
    }

    val freshModelToggleChip: @Composable () -> Unit = {
        if (canRenderFreshModelOverlay) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = if (isLandscape) 0.dp else 16.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                FilterChip(
                    selected = effectiveShowFreshModel,
                    onClick = {
                        val next = !effectiveShowFreshModel
                        localShowFreshModel = next
                        onShowFreshModelOverlayChange?.invoke(next)
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.cardio_power_fresh_model_chip),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("fresh_hr_model_toggle_chip")
                )
            }
        }
    }

    val overlayHeaderControls: @Composable () -> Unit = {
        if (hasMultiSourceOverlays) {
            MultiSensorOverlayControls(
                metricType = metricType,
                overlaySeries = overlaySeries,
                enabledOverlayIds = effectiveEnabledOverlayIds,
                activeOverlaySeries = activeOverlaySeries,
                onToggleOverlay = handleToggleOverlay,
                isLandscape = isLandscape,
                unit = unit,
                paceCeilingMinKm = paceCeilingMinKm,
                isDarkSurface = isDarkSurface,
                avoidZoneColors = useZoneColoredCurve
            )
        }
    }

    if (isLandscape) {
        Column(modifier = modifier.fillMaxWidth()) {
            freshModelToggleChip()
            overlayHeaderControls()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(0.72f)) {
                    chartCanvas()
                }
                if (zoneDistribution != null) {
                    AlignedMarginalZoneDistribution(
                        zoneDistribution = zoneDistribution,
                        zoneDisplayMode = zoneDisplayMode,
                        zoneBands = zoneBands,
                        dataMin = dataMin,
                        dataMax = dataMax,
                        metricType = metricType,
                        thresholdDashes = thresholdDashes,
                        modifier = Modifier.weight(0.28f).height(110.dp)
                    )
                } else if (nonZonedHistogram.isNotEmpty()) {
                    AlignedMarginalLinearDistribution(
                        bins = nonZonedHistogram,
                        dataMin = dataMin,
                        dataMax = dataMax,
                        metricType = metricType,
                        modifier = Modifier.weight(0.28f).height(110.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(0.28f))
                }
            }
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            freshModelToggleChip()
            overlayHeaderControls()
            Box(modifier = Modifier.fillMaxWidth()) {
                chartCanvas()
            }
        }
    }
}

/**
 * Aligned marginal linear histogram distribution pane for landscape mode telemetry and elevation graphs (REQ-UI-446).
 */
@Composable
fun AlignedMarginalLinearDistribution(
    bins: List<LinearHistogramBin>,
    dataMin: Double,
    dataMax: Double,
    metricType: TelemetryMetricType? = null,
    modifier: Modifier = Modifier,
    topPadding: Dp = 10.dp,
    bottomPadding: Dp = 24.dp
) {
    if (bins.isEmpty()) return
    val colorScheme = MaterialTheme.colorScheme

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        val topPaddingPx = topPadding.toPx()
        val bottomPaddingPx = bottomPadding.toPx()
        val chartHeightPx = (size.height - topPaddingPx - bottomPaddingPx).coerceAtLeast(1f)
        val availableWidthPx = size.width
        val isInvertedYAxis = TelemetryZoneMath.isInvertedTelemetryYAxis(metricType)

        fun valueToY(v: Double): Float {
            return TelemetryZoneMath.mapTelemetryValueToY(
                value = v,
                dataMin = dataMin,
                dataMax = dataMax,
                topPaddingPx = topPaddingPx,
                chartHeightPx = chartHeightPx,
                isInverted = isInvertedYAxis
            )
        }

        drawMarginalHistogramBars(
            bins = bins,
            dataMin = dataMin,
            dataMax = dataMax,
            stripStartPx = 0f,
            stripWidthPx = availableWidthPx,
            valueToY = ::valueToY,
            rangeMinOf = { it.rangeMin },
            rangeMaxOf = { it.rangeMax },
            durationSecOf = { it.durationSec },
            percentageOf = { it.percentage },
            colorOf = { it.color },
            cornerRadiusPx = 2.dp.toPx(),
            foregroundAlpha = 0.85f
        )

        // Draw horizontal boundary grid lines matching adjacent graph
        drawLine(
            color = colorScheme.outlineVariant.copy(alpha = 0.35f),
            start = Offset(0f, topPaddingPx),
            end = Offset(availableWidthPx, topPaddingPx),
            strokeWidth = 1f
        )
        drawLine(
            color = colorScheme.outlineVariant.copy(alpha = 0.35f),
            start = Offset(0f, topPaddingPx + chartHeightPx),
            end = Offset(availableWidthPx, topPaddingPx + chartHeightPx),
            strokeWidth = 1f
        )
    }
}

/**
 * Aligned marginal zone distribution pane for landscape mode telemetry graphs (REQ-UI-431 / ATT-2145).
 *
 * Renders horizontal zone bars (or fine histogram frequency bars) docked immediately to the right
 * of the telemetry graph curve in a 72/28 dual-pane split, maintaining 1:1 vertical Y-axis alignment
 * with the adjacent graph's zone thresholds, and displaying legible zone labels, percentage shares,
 * and formatted durations.
 */
@Composable
fun AlignedMarginalZoneDistribution(
    zoneDistribution: ZoneDistributionData,
    zoneDisplayMode: ZoneCardDisplayMode,
    zoneBands: List<TelemetryZoneBand>,
    dataMin: Double,
    dataMax: Double,
    metricType: TelemetryMetricType,
    modifier: Modifier = Modifier,
    thresholdDashes: List<Double> = emptyList(),
    topPadding: Dp = 10.dp,
    bottomPadding: Dp = 24.dp
) {
    val colorScheme = MaterialTheme.colorScheme
    val timeFormatter = remember { TimeFormatter() }

    val textPaint = remember(colorScheme) {
        Paint().apply {
            color = android.graphics.Color.WHITE
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f, 0f, 1f, android.graphics.Color.BLACK)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        val topPaddingPx = topPadding.toPx()
        val bottomPaddingPx = bottomPadding.toPx()
        val chartHeightPx = (size.height - topPaddingPx - bottomPaddingPx).coerceAtLeast(1f)
        val availableWidthPx = size.width
        val isInvertedYAxis = TelemetryZoneMath.isInvertedTelemetryYAxis(metricType)

        fun valueToY(v: Double): Float {
            return TelemetryZoneMath.mapTelemetryValueToY(
                value = v,
                dataMin = dataMin,
                dataMax = dataMax,
                topPaddingPx = topPaddingPx,
                chartHeightPx = chartHeightPx,
                isInverted = isInvertedYAxis
            )
        }

        val nativeCanvas = drawContext.canvas.nativeCanvas

        if (zoneDisplayMode == ZoneCardDisplayMode.FIVE_ZONES ||
            zoneDisplayMode == ZoneCardDisplayMode.COLUMNS ||
            zoneDisplayMode == ZoneCardDisplayMode.STACKED_BAR
        ) {
            for (entry in zoneDistribution.entries) {
                val band = zoneBands.getOrNull(entry.zoneIndex - 1)
                if (band != null) {
                    val yTop = valueToY(band.maxVal)
                    val yBottom = valueToY(band.minVal)
                    val rectTop = minOf(yTop, yBottom)
                    val rectHeight = abs(yTop - yBottom)

                    if (rectHeight > 0f) {
                        // 1. Background container track
                        drawRoundRect(
                            color = entry.color.copy(alpha = 0.18f),
                            topLeft = Offset(0f, rectTop),
                            size = Size(availableWidthPx, rectHeight),
                            cornerRadius = CornerRadius(3.dp.toPx())
                        )

                        // 2. Weighted horizontal bar
                        val barWidthPx = (entry.percentage / 100f).coerceIn(0f, 1f) * availableWidthPx
                        if (barWidthPx > 0f) {
                            drawRoundRect(
                                color = entry.color.copy(alpha = 0.85f),
                                topLeft = Offset(0f, rectTop),
                                size = Size(barWidthPx, rectHeight),
                                cornerRadius = CornerRadius(3.dp.toPx())
                            )
                        }

                        // 3. Granular metrics text overlay (REQ-UI-431.3)
                        if (rectHeight >= 12.dp.toPx()) {
                            val zoneLabel = "Z${entry.zoneIndex}"
                            val pctLabel = "${entry.percentage.toInt()}%"
                            val durLabel = timeFormatter.format_with_units(entry.durationSec)
                            val fullText = "$zoneLabel  $pctLabel  $durLabel"

                            textPaint.textSize = (rectHeight * 0.55f).coerceIn(16f, 24f)
                            val yCenter = rectTop + (rectHeight / 2f)
                            val textBaseline = yCenter - ((textPaint.descent() + textPaint.ascent()) / 2f)

                            val textToDraw = if (textPaint.measureText(fullText) <= availableWidthPx - 6.dp.toPx()) {
                                fullText
                            } else if (textPaint.measureText("$zoneLabel $pctLabel") <= availableWidthPx - 6.dp.toPx()) {
                                "$zoneLabel $pctLabel"
                            } else {
                                zoneLabel
                            }

                            nativeCanvas.drawText(textToDraw, 4.dp.toPx(), textBaseline, textPaint)
                        }
                    }
                }
            }
        } else if (zoneDisplayMode == ZoneCardDisplayMode.HISTOGRAM && zoneDistribution.histogram != null) {
            drawMarginalHistogramBars(
                bins = zoneDistribution.histogram.linearBins,
                dataMin = dataMin,
                dataMax = dataMax,
                stripStartPx = 0f,
                stripWidthPx = availableWidthPx,
                valueToY = ::valueToY,
                rangeMinOf = { it.rangeMin.toDouble() },
                rangeMaxOf = { it.rangeMax.toDouble() },
                durationSecOf = { it.durationSec },
                percentageOf = { it.percentage },
                colorOf = { it.color },
                cornerRadiusPx = 2.dp.toPx(),
                foregroundAlpha = 0.85f,
                minBarHeightPx = 1f,
                onBinDrawn = { bin, rectTop, rectHeight ->
                    if (rectHeight >= 12.dp.toPx() && bin.durationSec > 0L) {
                        textPaint.textSize = (rectHeight * 0.50f).coerceIn(14f, 20f)
                        val binText = "${bin.percentage.toInt()}%"
                        val yCenter = rectTop + (rectHeight / 2f)
                        val textBaseline = yCenter - ((textPaint.descent() + textPaint.ascent()) / 2f)
                        nativeCanvas.drawText(binText, 4.dp.toPx(), textBaseline, textPaint)
                    }
                }
            )
        }

        // Draw horizontal boundary grid lines matching adjacent graph
        drawLine(
            color = colorScheme.outlineVariant.copy(alpha = 0.35f),
            start = Offset(0f, topPaddingPx),
            end = Offset(availableWidthPx, topPaddingPx),
            strokeWidth = 1f
        )
        drawLine(
            color = colorScheme.outlineVariant.copy(alpha = 0.35f),
            start = Offset(0f, topPaddingPx + chartHeightPx),
            end = Offset(availableWidthPx, topPaddingPx + chartHeightPx),
            strokeWidth = 1f
        )

        // Draw threshold boundary dash guidelines matching adjacent graph
        if (thresholdDashes.isNotEmpty()) {
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()), 0f)
            for (dashVal in thresholdDashes) {
                val dashY = valueToY(dashVal)
                drawLine(
                    color = colorScheme.outlineVariant.copy(alpha = 0.35f),
                    start = Offset(0f, dashY),
                    end = Offset(availableWidthPx, dashY),
                    strokeWidth = 1f,
                    pathEffect = dashEffect
                )
            }
        }
    }
}

@Preview(name = "Aligned Marginal Zone Distribution - Light", widthDp = 220, heightDp = 120, showBackground = true)
@Composable
private fun AlignedMarginalZoneDistributionLightPreview() {
    MaterialTheme {
        val entries = listOf(
            ZoneTimeEntry(1, 0, 120L, 10f, TTColor.Zone1),
            ZoneTimeEntry(2, 0, 360L, 30f, TTColor.Zone2),
            ZoneTimeEntry(3, 0, 480L, 40f, TTColor.Zone3),
            ZoneTimeEntry(4, 0, 180L, 15f, TTColor.Zone4),
            ZoneTimeEntry(5, 0, 60L, 5f, TTColor.Zone5)
        )
        val zoneBands = listOf(
            TelemetryZoneBand(1, 100.0, 120.0, TTColor.Zone1, "Z1"),
            TelemetryZoneBand(2, 120.0, 140.0, TTColor.Zone2, "Z2"),
            TelemetryZoneBand(3, 140.0, 160.0, TTColor.Zone3, "Z3"),
            TelemetryZoneBand(4, 160.0, 180.0, TTColor.Zone4, "Z4"),
            TelemetryZoneBand(5, 180.0, 200.0, TTColor.Zone5, "Z5")
        )
        AlignedMarginalZoneDistribution(
            zoneDistribution = ZoneDistributionData(1200L, entries),
            zoneDisplayMode = ZoneCardDisplayMode.FIVE_ZONES,
            zoneBands = zoneBands,
            dataMin = 95.0,
            dataMax = 205.0,
            metricType = TelemetryMetricType.HEART_RATE
        )
    }
}

@Preview(name = "Aligned Marginal Zone Distribution - Dark", widthDp = 220, heightDp = 120, showBackground = true)
@Composable
private fun AlignedMarginalZoneDistributionDarkPreview() {
    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
        val entries = listOf(
            ZoneTimeEntry(1, 0, 120L, 10f, TTColor.Zone1),
            ZoneTimeEntry(2, 0, 360L, 30f, TTColor.Zone2),
            ZoneTimeEntry(3, 0, 480L, 40f, TTColor.Zone3),
            ZoneTimeEntry(4, 0, 180L, 15f, TTColor.Zone4),
            ZoneTimeEntry(5, 0, 60L, 5f, TTColor.Zone5)
        )
        val zoneBands = listOf(
            TelemetryZoneBand(1, 100.0, 120.0, TTColor.Zone1, "Z1"),
            TelemetryZoneBand(2, 120.0, 140.0, TTColor.Zone2, "Z2"),
            TelemetryZoneBand(3, 140.0, 160.0, TTColor.Zone3, "Z3"),
            TelemetryZoneBand(4, 160.0, 180.0, TTColor.Zone4, "Z4"),
            TelemetryZoneBand(5, 180.0, 200.0, TTColor.Zone5, "Z5")
        )
        AlignedMarginalZoneDistribution(
            zoneDistribution = ZoneDistributionData(1200L, entries),
            zoneDisplayMode = ZoneCardDisplayMode.FIVE_ZONES,
            zoneBands = zoneBands,
            dataMin = 95.0,
            dataMax = 205.0,
            metricType = TelemetryMetricType.HEART_RATE
        )
    }
}

@Preview(name = "DFA alpha 1 Inverted Y-Axis - Light", widthDp = 360, heightDp = 120, showBackground = true)
@Composable
private fun TelemetryMetricGraphDfaA1InvertedPreview() {
    MaterialTheme {
        val origin = com.google.android.gms.maps.model.LatLng(0.0, 0.0)
        TelemetryMetricGraph(
            pathPoints = listOf(
                PathPoint(distance = 0.0, latLng = origin, timeSec = 0L, hrvDfaA1 = 1.08),
                PathPoint(distance = 200.0, latLng = origin, timeSec = 60L, hrvDfaA1 = 0.68),
                PathPoint(distance = 400.0, latLng = origin, timeSec = 120L, hrvDfaA1 = 0.36)
            ),
            metricType = TelemetryMetricType.HRV_DFA_A1,
            currentDistance = 400.0,
            onDistanceSelected = {},
            enableGestures = false
        )
    }
}

@Preview(name = "rMSSD Dynamic Y-Axis - Light", widthDp = 360, heightDp = 120, showBackground = true)
@Composable
private fun TelemetryMetricGraphRmssdDynamicPreview() {
    MaterialTheme {
        val origin = com.google.android.gms.maps.model.LatLng(0.0, 0.0)
        TelemetryMetricGraph(
            pathPoints = listOf(
                PathPoint(distance = 0.0, latLng = origin, timeSec = 0L, hrvRmssd = 5.64),
                PathPoint(distance = 200.0, latLng = origin, timeSec = 60L, hrvRmssd = 2.85),
                PathPoint(distance = 400.0, latLng = origin, timeSec = 120L, hrvRmssd = 1.89),
                PathPoint(distance = 600.0, latLng = origin, timeSec = 180L, hrvRmssd = 4.20)
            ),
            metricType = TelemetryMetricType.HRV_RMSSD,
            currentDistance = 200.0,
            onDistanceSelected = {},
            enableGestures = false
        )
    }
}

@Preview(name = "Heart Rate Average Reference Line - Light", widthDp = 360, heightDp = 120, showBackground = true)
@Composable
private fun TelemetryMetricGraphAverageReferenceLightPreview() {
    com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme(darkTheme = false, setWindowColors = false) {
        val origin = com.google.android.gms.maps.model.LatLng(0.0, 0.0)
        TelemetryMetricGraph(
            pathPoints = listOf(
                PathPoint(distance = 0.0, latLng = origin, timeSec = 0L, hr = 132),
                PathPoint(distance = 500.0, latLng = origin, timeSec = 150L, hr = 154),
                PathPoint(distance = 1000.0, latLng = origin, timeSec = 300L, hr = 172),
                PathPoint(distance = 1500.0, latLng = origin, timeSec = 450L, hr = 162)
            ),
            metricType = TelemetryMetricType.HEART_RATE,
            currentDistance = null,
            onDistanceSelected = {},
            enableGestures = false
        )
    }
}

@Preview(name = "Pace Average Reference Line - Dark", widthDp = 360, heightDp = 120, showBackground = true)
@Composable
private fun TelemetryMetricGraphAverageReferenceDarkPreview() {
    com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme(darkTheme = true, setWindowColors = false) {
        val origin = com.google.android.gms.maps.model.LatLng(0.0, 0.0)
        TelemetryMetricGraph(
            pathPoints = listOf(
                PathPoint(distance = 0.0, latLng = origin, timeSec = 0L, speedMps = 3.2),
                PathPoint(distance = 500.0, latLng = origin, timeSec = 155L, speedMps = 3.5),
                PathPoint(distance = 1000.0, latLng = origin, timeSec = 305L, speedMps = 2.9),
                PathPoint(distance = 1500.0, latLng = origin, timeSec = 470L, speedMps = 3.4)
            ),
            metricType = TelemetryMetricType.PACE,
            currentDistance = null,
            onDistanceSelected = {},
            enableGestures = false
        )
    }
}
