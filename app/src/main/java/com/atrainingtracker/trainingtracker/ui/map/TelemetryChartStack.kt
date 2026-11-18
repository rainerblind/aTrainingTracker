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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.PaceFormatter
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.CardioPowerDynamicsRecord
import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionData
import java.util.Locale

/**
 * Computes all [TelemetryChartKind]s that have recorded data in [path], preserving canonical
 * vertical ordering inside Workout Details `CHARTS` (`REQ-PER-044.3`, `REQ-PER-044.4`).
 */
fun availableTelemetryChartKinds(
    path: List<PathPoint>?,
    enableCriticalPowerModel: Boolean
): List<TelemetryChartKind> {
    if (path.isNullOrEmpty()) return emptyList()
    return buildList {
        if (TelemetryMetricUtils.hasSpeedData(path)) add(TelemetryChartKind.SPEED_PACE)
        if (TelemetryMetricUtils.hasSlopeData(path)) add(TelemetryChartKind.SLOPE)
        if (TelemetryMetricUtils.hasHeartRateData(path)) add(TelemetryChartKind.HEART_RATE)
        if (TelemetryMetricUtils.hasPowerData(path)) add(TelemetryChartKind.POWER)
        if (TelemetryMetricUtils.hasCadenceData(path)) add(TelemetryChartKind.CADENCE)
        if (enableCriticalPowerModel && TelemetryMetricUtils.hasWPrimeBalData(path)) add(TelemetryChartKind.W_PRIME_BAL)
        if (TelemetryMetricUtils.hasHrvDfaA1Data(path)) add(TelemetryChartKind.HRV_DFA_A1)
        if (TelemetryMetricUtils.hasHrvRmssdData(path)) add(TelemetryChartKind.HRV_RMSSD)
    }
}

/**
 * Canonical availability check unifying [TrackOnMapScreen] `CHARTS` tab presence and
 * [MapDetailLayout] zoom/scroll gating (`REQ-PER-044.3`, `REQ-PER-044.4`).
 */
fun hasAnyTelemetryChartSeries(
    path: List<PathPoint>?,
    enableCriticalPowerModel: Boolean
): Boolean = availableTelemetryChartKinds(path, enableCriticalPowerModel).isNotEmpty()

/**
 * Shared vertical stack of telemetry charts (`Speed/Pace`, `Slope`, `HR`, `Power`, `Cadence`,
 * `W'bal`, `DFA a1`, `rMSSD`) with a persisted Material 3 per-chart visibility menu (`REQ-PER-044.4`)
 * and `Rule 64` plot-alignment preservation. Used by both [MapDetailLayout] and [TrackOnMapScreen].
 */
@Composable
fun TelemetryChartStack(
    path: List<PathPoint>,
    bSportType: BSportType,
    tuningConfig: TuningConfig,
    selectedDistance: Double?,
    onDistanceSelected: (Double?) -> Unit,
    activeTelemetryDomain: ProfileXAxisDomain,
    isTrackless: Boolean,
    zoomScale: Float,
    startDist: Double,
    isPanMode: Boolean,
    onZoomChanged: (Float, Double) -> Unit,
    chartModifier: Modifier,
    modifier: Modifier = Modifier,
    enableGestures: Boolean = true,
    showVisibilitySelector: Boolean = true,
    hiddenCharts: Set<TelemetryChartKind> = emptySet(),
    onToggleChartVisibility: ((TelemetryChartKind) -> Unit)? = null,
    hrThresholds: HeartRateZoneThresholds? = null,
    powerThresholds: PowerZoneThresholds? = null,
    hrZoneDistribution: ZoneDistributionData? = null,
    powerZoneDistribution: ZoneDistributionData? = null,
    hrZoneDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.COLUMNS,
    powerZoneDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.COLUMNS,
    cardioPowerDynamics: CardioPowerDynamicsRecord? = null,
    multiSensorOverlays: Map<TelemetryMetricType, List<TelemetrySourceSeries>> = emptyMap(),
    showChartHeader: Boolean = true
) {
    val availableKinds = remember(path, tuningConfig.enableCriticalPowerModel) {
        availableTelemetryChartKinds(path, tuningConfig.enableCriticalPowerModel)
    }
    if (availableKinds.isEmpty()) return

    var localHiddenCharts by rememberSaveable(hiddenCharts) { mutableStateOf(hiddenCharts) }
    val effectiveHiddenCharts = if (onToggleChartVisibility != null) hiddenCharts else localHiddenCharts
    val handleToggleChart: (TelemetryChartKind) -> Unit = onToggleChartVisibility ?: { kind ->
        localHiddenCharts = if (kind in localHiddenCharts) localHiddenCharts - kind else localHiddenCharts + kind
    }

    val isRunning = bSportType == BSportType.RUN
    val unit = remember {
        try {
            TrainingApplication.getUnit()
        } catch (_: Throwable) {
            MyUnits.METRIC
        }
    }
    val speedFormatter = remember(unit) { SpeedFormatter() }
    val paceFormatter = remember(unit) { PaceFormatter() }
    val configuration = LocalConfiguration.current
    val currentLocale = remember(configuration) {
        configuration.locales.let { if (it.isEmpty) Locale.getDefault() else it[0] }
    }
    val visibleKinds = remember(availableKinds, effectiveHiddenCharts) {
        availableKinds.filter { it !in effectiveHiddenCharts }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showVisibilitySelector) {
            ChartVisibilityHeaderBar(
                availableKinds = availableKinds,
                hiddenCharts = effectiveHiddenCharts,
                isRunning = isRunning,
                onToggleChartVisibility = handleToggleChart
            )
        }

        if (visibleKinds.isEmpty()) {
            Text(
                text = stringResource(R.string.charts_visibility_none_selected_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("charts_visibility_empty_hint")
            )
            return@Column
        }

        for (kind in visibleKinds) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("telemetry_chart_section_${kind.name}")
            ) {
                when (kind) {
                    TelemetryChartKind.SPEED_PACE -> {
                        val metric = if (isRunning) TelemetryMetricType.PACE else TelemetryMetricType.SPEED
                        val heading = if (isRunning) R.string.graph_heading_pace else R.string.graph_heading_speed
                        OverlayTelemetryChartSection(
                            path = path,
                            metricType = metric,
                            headingRes = heading,
                            selectedDistance = selectedDistance,
                            onDistanceSelected = onDistanceSelected,
                            activeTelemetryDomain = activeTelemetryDomain,
                            isTrackless = isTrackless,
                            bSportType = bSportType,
                            zoomScale = zoomScale,
                            startDist = startDist,
                            isPanMode = isPanMode,
                            onZoomChanged = onZoomChanged,
                            overlaySeries = multiSensorOverlays[TelemetryMetricType.SPEED].orEmpty(),
                            chartModifier = chartModifier,
                            enableGestures = enableGestures,
                            paceCeilingMinKm = tuningConfig.paceCeilingMinKm,
                            unit = unit,
                            speedFormatter = speedFormatter,
                            paceFormatter = paceFormatter,
                            showHeader = showChartHeader
                        )
                    }
                    TelemetryChartKind.SLOPE -> {
                        OverlayTelemetryChartSection(
                            path = path,
                            metricType = TelemetryMetricType.SLOPE,
                            headingRes = R.string.graph_heading_slope,
                            selectedDistance = selectedDistance,
                            onDistanceSelected = onDistanceSelected,
                            activeTelemetryDomain = activeTelemetryDomain,
                            isTrackless = isTrackless,
                            bSportType = bSportType,
                            zoomScale = zoomScale,
                            startDist = startDist,
                            isPanMode = isPanMode,
                            onZoomChanged = onZoomChanged,
                            overlaySeries = multiSensorOverlays[TelemetryMetricType.SLOPE].orEmpty(),
                            chartModifier = chartModifier,
                            enableGestures = enableGestures,
                            paceCeilingMinKm = tuningConfig.paceCeilingMinKm,
                            unit = unit,
                            showHeader = showChartHeader
                        )
                    }
                    TelemetryChartKind.HEART_RATE -> {
                        OverlayTelemetryChartSection(
                            path = path,
                            metricType = TelemetryMetricType.HEART_RATE,
                            headingRes = R.string.graph_heading_heart_rate,
                            selectedDistance = selectedDistance,
                            onDistanceSelected = onDistanceSelected,
                            activeTelemetryDomain = activeTelemetryDomain,
                            isTrackless = isTrackless,
                            bSportType = bSportType,
                            zoomScale = zoomScale,
                            startDist = startDist,
                            isPanMode = isPanMode,
                            onZoomChanged = onZoomChanged,
                            overlaySeries = multiSensorOverlays[TelemetryMetricType.HEART_RATE].orEmpty(),
                            chartModifier = chartModifier,
                            hrZoneThresholds = hrThresholds,
                            zoneDistribution = hrZoneDistribution,
                            zoneDisplayMode = hrZoneDisplayMode,
                            cardioPowerDynamics = cardioPowerDynamics,
                            enableCardioPowerDynamics = tuningConfig.enableCardioPowerDynamics,
                            enableGestures = enableGestures,
                            unit = unit,
                            showHeader = showChartHeader
                        )
                    }
                    TelemetryChartKind.POWER -> {
                        OverlayTelemetryChartSection(
                            path = path,
                            metricType = TelemetryMetricType.POWER,
                            headingRes = R.string.graph_heading_power,
                            selectedDistance = selectedDistance,
                            onDistanceSelected = onDistanceSelected,
                            activeTelemetryDomain = activeTelemetryDomain,
                            isTrackless = isTrackless,
                            bSportType = bSportType,
                            zoomScale = zoomScale,
                            startDist = startDist,
                            isPanMode = isPanMode,
                            onZoomChanged = onZoomChanged,
                            overlaySeries = multiSensorOverlays[TelemetryMetricType.POWER].orEmpty(),
                            chartModifier = chartModifier,
                            powerZoneThresholds = powerThresholds,
                            zoneDistribution = powerZoneDistribution,
                            zoneDisplayMode = powerZoneDisplayMode,
                            enableGestures = enableGestures,
                            unit = unit,
                            showHeader = showChartHeader
                        )
                    }
                    TelemetryChartKind.CADENCE -> {
                        OverlayTelemetryChartSection(
                            path = path,
                            metricType = TelemetryMetricType.CADENCE,
                            headingRes = R.string.graph_heading_cadence,
                            selectedDistance = selectedDistance,
                            onDistanceSelected = onDistanceSelected,
                            activeTelemetryDomain = activeTelemetryDomain,
                            isTrackless = isTrackless,
                            bSportType = bSportType,
                            zoomScale = zoomScale,
                            startDist = startDist,
                            isPanMode = isPanMode,
                            onZoomChanged = onZoomChanged,
                            overlaySeries = multiSensorOverlays[TelemetryMetricType.CADENCE].orEmpty(),
                            chartModifier = chartModifier,
                            enableGestures = enableGestures,
                            unit = unit,
                            showHeader = showChartHeader
                        )
                    }
                    TelemetryChartKind.W_PRIME_BAL -> {
                        StandardScalarChartSection(
                            path = path,
                            metricType = TelemetryMetricType.W_PRIME_BAL,
                            heading = stringResource(R.string.w_prime_bal),
                            valueFormatter = { pt -> pt.wPrimeBalKj?.let { String.format(currentLocale, "%.1f kJ", it) } },
                            selectedDistance = selectedDistance,
                            onDistanceSelected = onDistanceSelected,
                            activeTelemetryDomain = activeTelemetryDomain,
                            isTrackless = isTrackless,
                            bSportType = bSportType,
                            zoomScale = zoomScale,
                            startDist = startDist,
                            isPanMode = isPanMode,
                            onZoomChanged = onZoomChanged,
                            enableGestures = enableGestures,
                            chartModifier = chartModifier,
                            showHeader = showChartHeader
                        )
                    }
                    TelemetryChartKind.HRV_DFA_A1 -> {
                        StandardScalarChartSection(
                            path = path,
                            metricType = TelemetryMetricType.HRV_DFA_A1,
                            heading = stringResource(R.string.hrv_dfa_a1),
                            valueFormatter = { pt -> pt.hrvDfaA1?.let { String.format(currentLocale, "%.2f", it) } },
                            selectedDistance = selectedDistance,
                            onDistanceSelected = onDistanceSelected,
                            activeTelemetryDomain = activeTelemetryDomain,
                            isTrackless = isTrackless,
                            bSportType = bSportType,
                            zoomScale = zoomScale,
                            startDist = startDist,
                            isPanMode = isPanMode,
                            onZoomChanged = onZoomChanged,
                            dfaA1ThresholdT1 = tuningConfig.dfaA1ThresholdT1,
                            dfaA1ThresholdT2 = tuningConfig.dfaA1ThresholdT2,
                            enableGestures = enableGestures,
                            chartModifier = chartModifier,
                            showHeader = showChartHeader
                        )
                    }
                    TelemetryChartKind.HRV_RMSSD -> {
                        StandardScalarChartSection(
                            path = path,
                            metricType = TelemetryMetricType.HRV_RMSSD,
                            heading = stringResource(R.string.hrv_rmssd),
                            valueFormatter = { pt -> pt.hrvRmssd?.let { String.format(currentLocale, "%.1f ms", it) } },
                            selectedDistance = selectedDistance,
                            onDistanceSelected = onDistanceSelected,
                            activeTelemetryDomain = activeTelemetryDomain,
                            isTrackless = isTrackless,
                            bSportType = bSportType,
                            zoomScale = zoomScale,
                            startDist = startDist,
                            isPanMode = isPanMode,
                            onZoomChanged = onZoomChanged,
                            enableGestures = enableGestures,
                            chartModifier = chartModifier,
                            showHeader = showChartHeader
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartVisibilityHeaderBar(
    availableKinds: List<TelemetryChartKind>,
    hiddenCharts: Set<TelemetryChartKind>,
    isRunning: Boolean,
    onToggleChartVisibility: (TelemetryChartKind) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val visibleCount = availableKinds.count { it !in hiddenCharts }
    val hasHidden = visibleCount < availableKinds.size

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            TextButton(
                onClick = { expanded = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("charts_visibility_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = stringResource(R.string.charts_visibility_cd),
                    modifier = Modifier.size(16.dp),
                    tint = if (hasHidden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (hasHidden) {
                        stringResource(R.string.charts_visibility_summary, visibleCount, availableKinds.size)
                    } else {
                        stringResource(R.string.charts_visibility_button)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hasHidden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                for (kind in availableKinds) {
                    val isVisible = kind !in hiddenCharts
                    DropdownMenuItem(
                        modifier = Modifier.testTag("charts_visibility_menu_item_${kind.name}"),
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = isVisible, onCheckedChange = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = chartKindLabel(kind, isRunning),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        onClick = { onToggleChartVisibility(kind) }
                    )
                }
            }
        }
    }
}

@Composable
private fun chartKindLabel(kind: TelemetryChartKind, isRunning: Boolean): String = when (kind) {
    TelemetryChartKind.SPEED_PACE -> stringResource(if (isRunning) R.string.graph_heading_pace else R.string.graph_heading_speed)
    TelemetryChartKind.SLOPE -> stringResource(R.string.graph_heading_slope)
    TelemetryChartKind.HEART_RATE -> stringResource(R.string.graph_heading_heart_rate)
    TelemetryChartKind.POWER -> stringResource(R.string.graph_heading_power)
    TelemetryChartKind.CADENCE -> stringResource(R.string.graph_heading_cadence)
    TelemetryChartKind.W_PRIME_BAL -> stringResource(R.string.w_prime_bal)
    TelemetryChartKind.HRV_DFA_A1 -> stringResource(R.string.hrv_dfa_a1)
    TelemetryChartKind.HRV_RMSSD -> stringResource(R.string.hrv_rmssd)
}

@Composable
private fun OverlayTelemetryChartSection(
    path: List<PathPoint>,
    metricType: TelemetryMetricType,
    headingRes: Int,
    selectedDistance: Double?,
    onDistanceSelected: (Double?) -> Unit,
    activeTelemetryDomain: ProfileXAxisDomain,
    isTrackless: Boolean,
    bSportType: BSportType,
    zoomScale: Float,
    startDist: Double,
    isPanMode: Boolean,
    onZoomChanged: (Float, Double) -> Unit,
    overlaySeries: List<TelemetrySourceSeries>,
    chartModifier: Modifier,
    enableGestures: Boolean = true,
    paceCeilingMinKm: Float = com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM,
    unit: MyUnits = MyUnits.METRIC,
    speedFormatter: SpeedFormatter? = null,
    paceFormatter: PaceFormatter? = null,
    hrZoneThresholds: HeartRateZoneThresholds? = null,
    powerZoneThresholds: PowerZoneThresholds? = null,
    zoneDistribution: ZoneDistributionData? = null,
    zoneDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.COLUMNS,
    cardioPowerDynamics: CardioPowerDynamicsRecord? = null,
    enableCardioPowerDynamics: Boolean = true,
    showHeader: Boolean = true
) {
    Spacer(modifier = Modifier.height(8.dp))
    var enabledOverlayIds by rememberSaveable(metricType.name, overlaySeries.map { it.columnName }) {
        mutableStateOf(emptySet<String>())
    }
    val isTimeDomain = isTrackless || activeTelemetryDomain == ProfileXAxisDomain.TIME
    val activePoint = selectedDistance?.let {
        TelemetryMetricUtils.findNearestPoint(path, it, isTimeDomain = isTrackless)
    }
    val configuration = LocalConfiguration.current
    val currentLocale = remember(configuration) {
        configuration.locales.let { if (it.isEmpty) Locale.getDefault() else it[0] }
    }
    val activePrimaryText = formatPrimaryActiveValueText(
        activePoint = activePoint,
        metricType = metricType,
        hrZoneThresholds = hrZoneThresholds,
        powerZoneThresholds = powerZoneThresholds,
        unit = unit,
        paceCeilingMinKm = paceCeilingMinKm,
        speedFormatter = speedFormatter,
        paceFormatter = paceFormatter,
        locale = currentLocale
    )
    val activeOverlays = if (overlaySeries.size >= 2) {
        overlaySeries.filter { it.columnName in enabledOverlayIds }
    } else {
        emptyList()
    }
    val gpsSource = stringResource(R.string.chart_overlay_source_gps)
    val googleFusedSource = stringResource(R.string.chart_overlay_source_google_fused)
    val attFusedSource = stringResource(R.string.chart_overlay_source_att_fused)
    val attAltSlopeSource = stringResource(R.string.chart_overlay_source_att_fused_alt_slope)
    val headerText = formatTelemetryHeaderWithOverlays(
        baseHeading = stringResource(headingRes),
        activePrimaryText = activePrimaryText,
        metricType = metricType,
        activeOverlays = activeOverlays,
        overlayScrubTarget = resolveOverlayScrubTarget(path, selectedDistance, isTimeDomain, isTrackless),
        isTimeDomain = isTimeDomain,
        unit = unit,
        paceCeilingMinKm = paceCeilingMinKm,
        locale = currentLocale,
        speedFormatter = speedFormatter,
        sourceLabelResolver = { name ->
            when (name) {
                "GPS" -> gpsSource
                "Google Fused" -> googleFusedSource
                "ATT Fused" -> attFusedSource
                "ATT Fused (Alt/Slope)" -> attAltSlopeSource
                else -> name
            }
        }
    )
    if (showHeader) {
        Text(
            text = headerText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
        )
    }
    TelemetryMetricGraph(
        pathPoints = path,
        metricType = metricType,
        currentDistance = selectedDistance,
        onDistanceSelected = onDistanceSelected,
        xAxisDomain = activeTelemetryDomain,
        bSportType = bSportType,
        zoomScale = zoomScale,
        startDist = startDist,
        isPanMode = isPanMode,
        onZoomChanged = onZoomChanged,
        hrZoneThresholds = hrZoneThresholds,
        powerZoneThresholds = powerZoneThresholds,
        paceCeilingMinKm = paceCeilingMinKm,
        enableGestures = enableGestures,
        zoneDistribution = zoneDistribution,
        zoneDisplayMode = zoneDisplayMode,
        cardioPowerDynamics = cardioPowerDynamics,
        enableCardioPowerDynamics = enableCardioPowerDynamics,
        overlaySeries = overlaySeries,
        enabledOverlayIds = enabledOverlayIds,
        onToggleOverlay = { col ->
            enabledOverlayIds = if (col in enabledOverlayIds) enabledOverlayIds - col else enabledOverlayIds + col
        },
        modifier = chartModifier
    )
}

@Composable
private fun StandardScalarChartSection(
    path: List<PathPoint>,
    metricType: TelemetryMetricType,
    heading: String,
    valueFormatter: (PathPoint) -> String?,
    selectedDistance: Double?,
    onDistanceSelected: (Double?) -> Unit,
    activeTelemetryDomain: ProfileXAxisDomain,
    isTrackless: Boolean,
    bSportType: BSportType,
    zoomScale: Float,
    startDist: Double,
    isPanMode: Boolean,
    onZoomChanged: (Float, Double) -> Unit,
    enableGestures: Boolean,
    chartModifier: Modifier,
    dfaA1ThresholdT1: Float = 0.75f,
    dfaA1ThresholdT2: Float = 0.50f,
    showHeader: Boolean = true
) {
    Spacer(modifier = Modifier.height(8.dp))
    val activePoint = selectedDistance?.let {
        TelemetryMetricUtils.findNearestPoint(
            points = path,
            targetValue = it,
            isTimeDomain = isTrackless
        )
    }
    val formattedVal = activePoint?.let(valueFormatter)
    if (showHeader) {
        Text(
            text = if (formattedVal != null) "$heading: $formattedVal" else heading,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
        )
    }
    TelemetryMetricGraph(
        pathPoints = path,
        metricType = metricType,
        currentDistance = selectedDistance,
        onDistanceSelected = onDistanceSelected,
        xAxisDomain = activeTelemetryDomain,
        bSportType = bSportType,
        zoomScale = zoomScale,
        startDist = startDist,
        isPanMode = isPanMode,
        onZoomChanged = onZoomChanged,
        dfaA1ThresholdT1 = dfaA1ThresholdT1,
        dfaA1ThresholdT2 = dfaA1ThresholdT2,
        enableGestures = enableGestures,
        modifier = chartModifier
    )
}
