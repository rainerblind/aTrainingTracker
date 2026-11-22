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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter
import com.atrainingtracker.banalservice.sensor.formater.PaceFormatter
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.theme.TelemetryGraphColor
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Shared multi-sensor overlay helpers for [TelemetryMetricGraph] and [ElevationProfile]
 * (`REQ-PER-042.3`, `REQ-PER-044.3`, `REQ-UI-2798.2`). Handles chart-boundary unit conversion
 * (`SPEED` m/s -> user speed, `PACE` m/s -> user pace, `ALTITUDE` m -> m/ft), progressive-disclosure
 * `Layers` menus, `Ø` mean legends, secondary curve/dot rendering, and scrub header readouts.
 *
 * When [avoidZoneColors] is `true` (zone-colored `HEART_RATE` / `POWER` primary curves), resolves
 * zone-orthogonal theme-adapted contrast colors (`CYAN`, `THEME_DEFAULT`, `MAGENTA`, `TEAL`) so
 * secondary sensor overlays remain clearly distinguishable across all training zones.
 */
internal fun overlayStrokeColor(
    index: Int,
    isDarkSurface: Boolean = false,
    avoidZoneColors: Boolean = false
): Color {
    if (avoidZoneColors) {
        val token = when (index % 4) {
            0 -> TelemetryGraphColor.CYAN
            1 -> TelemetryGraphColor.THEME_DEFAULT
            2 -> TelemetryGraphColor.MAGENTA
            else -> TelemetryGraphColor.TEAL
        }
        return if (isDarkSurface) token.darkColor else token.lightColor
    }
    return when (index % 4) {
        0 -> TTColor.Zone2
        1 -> TTColor.Zone4
        2 -> TTColor.Zone5
        else -> TTColor.Zone3
    }
}

internal data class OverlayXViewport(
    val isTimeDomain: Boolean,
    val startDist: Double,
    val visibleSpan: Double,
    val startPaddingPx: Float,
    val chartWidthPx: Float
)

/**
 * Converts a raw repository [TelemetrySourceSeries] sample value (`SPEED_mps` in m/s, `ALTITUDE`
 * in meters, `SLOPE` in %) into the active chart's Y-axis domain value.
 */
internal fun resolveOverlayValueForChart(
    rawValue: Double,
    metricType: TelemetryMetricType,
    unit: MyUnits,
    paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM
): Double? {
    if (!rawValue.isFinite()) return null
    return when (metricType) {
        TelemetryMetricType.SPEED -> MyHelper.mps2userUnit(rawValue, unit)
        TelemetryMetricType.PACE -> TelemetryMetricUtils.convertSpeedMpsToPaceMinutes(
            speedMps = rawValue,
            unit = unit,
            paceCeilingMinKm = paceCeilingMinKm
        )
        else -> rawValue
    }
}

internal fun DrawScope.drawMultiSensorOverlayCurves(
    overlaySeries: List<TelemetrySourceSeries>,
    allOverlaySeries: List<TelemetrySourceSeries>,
    viewport: OverlayXViewport,
    metricType: TelemetryMetricType,
    unit: MyUnits,
    paceCeilingMinKm: Float,
    valueToY: (Double) -> Float,
    isDarkSurface: Boolean = false,
    avoidZoneColors: Boolean = false
) {
    for (series in overlaySeries) {
        val colorIdx = allOverlaySeries.indexOfFirst { it.columnName == series.columnName }.coerceAtLeast(0)
        val color = overlayStrokeColor(colorIdx, isDarkSurface = isDarkSurface, avoidZoneColors = avoidZoneColors)
        val dashLength = if (colorIdx % 2 == 0) 6.dp.toPx() else 3.dp.toPx()
        val gapLength = 3.dp.toPx()
        val path = Path()
        var started = false
        for (pt in series.points) {
            val chartValue = resolveOverlayValueForChart(pt.value, metricType, unit, paceCeilingMinKm)
            if (chartValue == null) {
                if (metricType == TelemetryMetricType.PACE) {
                    started = false
                }
                continue
            }
            val xSpan = if (viewport.isTimeDomain) pt.timeSec.toDouble() else pt.distance
            val x = viewport.startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(
                xSpan,
                viewport.startDist,
                viewport.visibleSpan,
                viewport.chartWidthPx
            )
            val y = valueToY(chartValue)
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }
        if (started) {
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = 1.8.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength, gapLength), 0f),
                    cap = StrokeCap.Round
                )
            )
        }
    }
}

internal fun DrawScope.drawMultiSensorOverlayScrubDots(
    overlaySeries: List<TelemetrySourceSeries>,
    allOverlaySeries: List<TelemetrySourceSeries>,
    targetValue: Double,
    isTimeDomain: Boolean,
    cursorX: Float,
    metricType: TelemetryMetricType,
    unit: MyUnits,
    paceCeilingMinKm: Float,
    valueToY: (Double) -> Float,
    isDarkSurface: Boolean = false,
    avoidZoneColors: Boolean = false
) {
    for (series in overlaySeries) {
        val rawSample = TelemetryMetricUtils.findNearestSourceValue(series.points, targetValue, isTimeDomain)
            ?: continue
        val chartValue = resolveOverlayValueForChart(rawSample, metricType, unit, paceCeilingMinKm)
            ?: continue
        val colorIdx = allOverlaySeries.indexOfFirst { it.columnName == series.columnName }.coerceAtLeast(0)
        drawCircle(
            color = overlayStrokeColor(colorIdx, isDarkSurface = isDarkSurface, avoidZoneColors = avoidZoneColors),
            radius = 3.dp.toPx(),
            center = Offset(cursorX, valueToY(chartValue))
        )
    }
}

private fun Offset(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x, y)

/**
 * Formats an overlay scalar value (raw repository units) into a localized user-facing string
 * using [unit] and [paceCeilingMinKm].
 */
internal fun formatOverlayScalarWithUnit(
    rawValue: Double,
    metricType: TelemetryMetricType,
    unit: MyUnits,
    paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM,
    locale: Locale = Locale.getDefault(),
    altitudeFormatter: AltitudeFormatter? = null,
    speedFormatter: SpeedFormatter? = null
): String {
    if (!rawValue.isFinite()) return "--"
    val usesGlobalDefaultLocaleAndUnit = locale == Locale.getDefault() &&
        runCatching { TrainingApplication.getUnit() == unit }.getOrDefault(false)
    return when (metricType) {
        TelemetryMetricType.HEART_RATE -> "${rawValue.roundToInt()} bpm"
        TelemetryMetricType.POWER -> "${rawValue.roundToInt()} W"
        TelemetryMetricType.CADENCE -> "${rawValue.roundToInt()} rpm"
        TelemetryMetricType.SPEED -> {
            if (speedFormatter != null && usesGlobalDefaultLocaleAndUnit) {
                runCatching { speedFormatter.format_with_units(rawValue) }.getOrNull()
            } else {
                null
            } ?: run {
                val speedUser = MyHelper.mps2userUnit(rawValue, unit)
                val suffix = if (unit == MyUnits.IMPERIAL) "mile/h" else "km/h"
                String.format(locale, "%.1f %s", speedUser, suffix)
            }
        }
        TelemetryMetricType.PACE -> {
            val paceMin = TelemetryMetricUtils.convertSpeedMpsToPaceMinutes(rawValue, unit, paceCeilingMinKm)
                ?: return "--"
            val suffix = if (unit == MyUnits.IMPERIAL) "min/mile" else "min/km"
            "${TelemetryMetricUtils.formatPaceMinutes(paceMin)} $suffix"
        }
        TelemetryMetricType.SLOPE -> String.format(locale, "%.1f%%", rawValue)
        TelemetryMetricType.ALTITUDE -> {
            val resolvedAltitudeFormatter = altitudeFormatter ?: runCatching { AltitudeFormatter() }.getOrNull()
            if (resolvedAltitudeFormatter != null && usesGlobalDefaultLocaleAndUnit) {
                runCatching { resolvedAltitudeFormatter.format_with_units(rawValue) }.getOrNull()
            } else {
                null
            } ?: run {
                val altUser = if (unit == MyUnits.IMPERIAL) rawValue / BANALService.METER_PER_FOOT else rawValue
                val suffix = if (unit == MyUnits.IMPERIAL) "ft" else "m"
                String.format(locale, "%,.0f %s", altUser, suffix)
            }
        }
        TelemetryMetricType.W_PRIME_BAL -> String.format(locale, "%.1f kJ", rawValue)
        TelemetryMetricType.HRV_DFA_A1 -> String.format(locale, "%.2f", rawValue)
        TelemetryMetricType.HRV_RMSSD -> String.format(locale, "%.1f ms", rawValue)
    }
}

@Composable
internal fun resolveOverlaySourceLabel(sourceName: String): String = when (sourceName) {
    "GPS" -> stringResource(R.string.chart_overlay_source_gps)
    "Google Fused" -> stringResource(R.string.chart_overlay_source_google_fused)
    "ATT Fused" -> stringResource(R.string.chart_overlay_source_att_fused)
    "ATT Fused (Alt/Slope)" -> stringResource(R.string.chart_overlay_source_att_fused_alt_slope)
    else -> sourceName
}

@Composable
internal fun formatMeanForMetric(
    metricType: TelemetryMetricType,
    meanValue: Double,
    unit: MyUnits = MyUnits.METRIC,
    paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM
): String {
    val configuration = LocalConfiguration.current
    val currentLocale = remember(configuration) {
        configuration.locales.let { if (it.isEmpty) Locale.getDefault() else it[0] }
    }
    val rounded = kotlin.math.round(meanValue).toInt()
    return when (metricType) {
        TelemetryMetricType.HEART_RATE -> stringResource(R.string.chart_overlay_mean_bpm, rounded)
        TelemetryMetricType.POWER -> stringResource(R.string.chart_overlay_mean_watt, rounded)
        TelemetryMetricType.CADENCE -> stringResource(R.string.chart_overlay_mean_rpm, rounded)
        else -> {
            val valueStr = formatOverlayScalarWithUnit(meanValue, metricType, unit, paceCeilingMinKm, locale = currentLocale)
            stringResource(R.string.chart_overlay_mean_value, valueStr)
        }
    }
}

@Composable
internal fun MultiSensorOverlayControls(
    metricType: TelemetryMetricType,
    overlaySeries: List<TelemetrySourceSeries>,
    enabledOverlayIds: Set<String>,
    activeOverlaySeries: List<TelemetrySourceSeries>,
    onToggleOverlay: (String) -> Unit,
    isLandscape: Boolean,
    unit: MyUnits = MyUnits.METRIC,
    paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM,
    isDarkSurface: Boolean = false,
    avoidZoneColors: Boolean = false
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = if (isLandscape) 4.dp else 16.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (activeOverlaySeries.isNotEmpty()) {
            MultiSensorMeanLegendRow(
                metricType = metricType,
                overlaySeries = overlaySeries,
                activeOverlaySeries = activeOverlaySeries,
                unit = unit,
                paceCeilingMinKm = paceCeilingMinKm,
                isDarkSurface = isDarkSurface,
                avoidZoneColors = avoidZoneColors,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier
                    .size(32.dp)
                    .testTag("multi_source_layers_button_${metricType.name}")
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = stringResource(R.string.chart_overlay_layers_cd),
                    tint = if (activeOverlaySeries.isNotEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
            MultiSensorOverlayDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                metricType = metricType,
                overlaySeries = overlaySeries,
                enabledOverlayIds = enabledOverlayIds,
                onToggleOverlay = onToggleOverlay,
                unit = unit,
                paceCeilingMinKm = paceCeilingMinKm,
                isDarkSurface = isDarkSurface,
                avoidZoneColors = avoidZoneColors
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiSensorMeanLegendRow(
    metricType: TelemetryMetricType,
    overlaySeries: List<TelemetrySourceSeries>,
    activeOverlaySeries: List<TelemetrySourceSeries>,
    unit: MyUnits,
    paceCeilingMinKm: Float,
    isDarkSurface: Boolean,
    avoidZoneColors: Boolean,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.testTag("multi_source_mean_legend_${metricType.name}"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (series in activeOverlaySeries) {
            val colorIdx = overlaySeries.indexOfFirst { it.columnName == series.columnName }.coerceAtLeast(0)
            val label = resolveOverlaySourceLabel(series.sourceName)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(overlayStrokeColor(colorIdx, isDarkSurface = isDarkSurface, avoidZoneColors = avoidZoneColors), CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$label: ${formatMeanForMetric(metricType, series.meanValue, unit, paceCeilingMinKm)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MultiSensorOverlayDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    metricType: TelemetryMetricType,
    overlaySeries: List<TelemetrySourceSeries>,
    enabledOverlayIds: Set<String>,
    onToggleOverlay: (String) -> Unit,
    unit: MyUnits,
    paceCeilingMinKm: Float,
    isDarkSurface: Boolean,
    avoidZoneColors: Boolean
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        overlaySeries.forEachIndexed { idx, series ->
            val checked = series.columnName in enabledOverlayIds
            val meanLabel = formatMeanForMetric(metricType, series.meanValue, unit, paceCeilingMinKm)
            val label = resolveOverlaySourceLabel(series.sourceName)
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(overlayStrokeColor(idx, isDarkSurface = isDarkSurface, avoidZoneColors = avoidZoneColors), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$label ($meanLabel)",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                onClick = { onToggleOverlay(series.columnName) }
            )
        }
    }
}

/**
 * Resolves the lookup X-axis coordinate for overlay scrub interpolation across both GPS
 * (`DISTANCE`/`TIME`) and trackless (`TIME`) workouts (REQ-PER-042.3).
 */
internal fun resolveOverlayScrubTarget(
    pathPoints: List<PathPoint>,
    selectedDistance: Double?,
    isTimeDomain: Boolean,
    isTrackless: Boolean
): Double? = when {
    selectedDistance == null -> null
    !isTimeDomain || isTrackless -> selectedDistance
    else -> TelemetryMetricUtils.findNearestPoint(
        points = pathPoints,
        targetValue = selectedDistance,
        isTimeDomain = false
    )?.timeSec?.toDouble()
}

internal fun formatTelemetryHeaderWithOverlays(
    baseHeading: String,
    activePrimaryText: String?,
    metricType: TelemetryMetricType,
    activeOverlays: List<TelemetrySourceSeries>,
    overlayScrubTarget: Double?,
    isTimeDomain: Boolean,
    unit: MyUnits = MyUnits.METRIC,
    paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM,
    locale: Locale = Locale.getDefault(),
    speedFormatter: SpeedFormatter? = null,
    altitudeFormatter: AltitudeFormatter? = null,
    sourceLabelResolver: (String) -> String = { it }
): String {
    if (activePrimaryText == null && (overlayScrubTarget == null || activeOverlays.isEmpty())) {
        return baseHeading
    }
    val parts = mutableListOf<String>()
    if (activePrimaryText != null) {
        parts.add("$baseHeading: $activePrimaryText")
    } else {
        parts.add(baseHeading)
    }
    if (overlayScrubTarget != null && activeOverlays.isNotEmpty()) {
        for (series in activeOverlays) {
            val rawSample = TelemetryMetricUtils.findNearestSourceValue(
                points = series.points,
                targetValue = overlayScrubTarget,
                isTimeDomain = isTimeDomain
            ) ?: continue
            val formatted = formatOverlayScalarWithUnit(
                rawValue = rawSample,
                metricType = metricType,
                unit = unit,
                paceCeilingMinKm = paceCeilingMinKm,
                locale = locale,
                altitudeFormatter = altitudeFormatter,
                speedFormatter = speedFormatter
            )
            parts.add("${sourceLabelResolver(series.sourceName)}: $formatted")
        }
    }
    return parts.joinToString(" • ")
}

internal fun formatPrimaryActiveValueText(
    activePoint: PathPoint?,
    metricType: TelemetryMetricType,
    hrZoneThresholds: HeartRateZoneThresholds?,
    powerZoneThresholds: PowerZoneThresholds?,
    unit: MyUnits = MyUnits.METRIC,
    paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM,
    speedFormatter: SpeedFormatter? = null,
    paceFormatter: PaceFormatter? = null,
    locale: Locale = Locale.getDefault(),
    altitudeFormatter: AltitudeFormatter? = null
): String? {
    if (activePoint == null) return null
    val extracted = TelemetryMetricUtils.extractMetricValue(
        point = activePoint,
        metricType = metricType,
        unit = unit,
        paceCeilingMinKm = paceCeilingMinKm
    ) ?: return null
    val usesGlobalDefaultLocaleAndUnit = locale == Locale.getDefault() &&
        runCatching { TrainingApplication.getUnit() == unit }.getOrDefault(false)
    return when (metricType) {
        TelemetryMetricType.HEART_RATE -> {
            val base = "${extracted.toInt()} bpm"
            val zone = hrZoneThresholds?.let {
                " • Z${TelemetryZoneMath.determineHeartRateZone(extracted, it)}"
            }.orEmpty()
            "$base$zone"
        }
        TelemetryMetricType.POWER -> {
            val base = "${extracted.toInt()} W"
            val zone = powerZoneThresholds?.let {
                " • Z${TelemetryZoneMath.determinePowerZone(extracted, it)}"
            }.orEmpty()
            "$base$zone"
        }
        TelemetryMetricType.CADENCE -> "${extracted.toInt()} rpm"
        TelemetryMetricType.SPEED -> {
            val rawMps = activePoint.speedMps ?: return null
            if (speedFormatter != null && usesGlobalDefaultLocaleAndUnit) {
                speedFormatter.format_with_units(rawMps)
            } else {
                formatOverlayScalarWithUnit(rawMps, TelemetryMetricType.SPEED, unit, paceCeilingMinKm, locale = locale)
            }
        }
        TelemetryMetricType.PACE -> {
            val rawMps = activePoint.speedMps ?: return null
            if (paceFormatter != null && usesGlobalDefaultLocaleAndUnit) {
                val spm = (extracted * 60.0) / if (unit == MyUnits.IMPERIAL) BANALService.METER_PER_MILE else 1000.0
                paceFormatter.format_with_units(spm)
            } else {
                formatOverlayScalarWithUnit(rawMps, TelemetryMetricType.PACE, unit, paceCeilingMinKm, locale = locale)
            }
        }
        TelemetryMetricType.SLOPE -> String.format(locale, "%.1f%%", extracted)
        TelemetryMetricType.ALTITUDE -> formatOverlayScalarWithUnit(
            rawValue = extracted,
            metricType = TelemetryMetricType.ALTITUDE,
            unit = unit,
            locale = locale,
            altitudeFormatter = altitudeFormatter
        )
        else -> null
    }
}
