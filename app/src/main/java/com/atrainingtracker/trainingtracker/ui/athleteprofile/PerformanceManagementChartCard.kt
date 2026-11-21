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

package com.atrainingtracker.trainingtracker.ui.athleteprofile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.physiology.AcwrZone
import com.atrainingtracker.trainingtracker.physiology.PmcDailyPoint
import com.atrainingtracker.trainingtracker.physiology.PmcSummaryResult
import com.atrainingtracker.trainingtracker.physiology.TsbReadinessZone
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Performance Management Chart (PMC) card displaying current Fitness (CTL), Fatigue (ATL),
 * Form / Race-Readiness (TSB), Workload Ratio (ACWR), 7d Ramp Rate, a responsive 3-curve
 * legend, and the interactive multi-curve time-series Canvas chart (`REQ-UI-401.3`, `REQ-UI-2791`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerformanceManagementChartCard(
    pmcSummary: PmcSummaryResult,
    pmcPlotPoints: List<PmcDailyPoint>,
    selectedPmcTimeRange: MetricsTimeRange = MetricsTimeRange.SIX_MONTHS,
    onSelectPmcTimeRange: (MetricsTimeRange) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentLocale = LocalConfiguration.current.locales[0]
    val neutralBorderColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.athlete_profile_pmc_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Row 1: Plotted curves — borders and curve swatches strictly anchored to curve colors (REQ-UI-2791.1)
            val tsbZoneText = when (pmcSummary.currentTsbZone) {
                TsbReadinessZone.FRESH_RACE_READY -> stringResource(R.string.athlete_profile_pmc_zone_fresh)
                TsbReadinessZone.NEUTRAL -> stringResource(R.string.athlete_profile_pmc_zone_neutral)
                TsbReadinessZone.OPTIMAL_OVERLOAD -> stringResource(R.string.athlete_profile_pmc_zone_optimal)
                TsbReadinessZone.HIGH_FATIGUE_RISK -> stringResource(R.string.athlete_profile_pmc_zone_fatigue)
            }
            val tsbZoneColor = when (pmcSummary.currentTsbZone) {
                TsbReadinessZone.FRESH_RACE_READY -> TTColor.PmcFreshGreen
                TsbReadinessZone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
                TsbReadinessZone.OPTIMAL_OVERLOAD -> TTColor.PmcOptimalBlue
                TsbReadinessZone.HIGH_FATIGUE_RISK -> MaterialTheme.colorScheme.error
            }

            val ctlLabel = stringResource(R.string.athlete_profile_pmc_ctl_label)
            val atlLabel = stringResource(R.string.athlete_profile_pmc_atl_label)
            val tsbLabel = stringResource(R.string.athlete_profile_pmc_tsb_label)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .testTag("pmc_row_plotted"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PmcStatBadge(
                    label = ctlLabel,
                    valueText = String.format(currentLocale, "%.1f", pmcSummary.currentCtl),
                    badgeText = null,
                    borderColor = TTColor.PmcCtlBlue,
                    statusColor = TTColor.PmcCtlBlue,
                    curveSwatchColor = TTColor.PmcCtlBlue,
                    swatchTestTag = "pmc_swatch_ctl",
                    cardTestTag = "pmc_card_ctl",
                    labelTestTag = "pmc_label_ctl",
                    modifier = Modifier.weight(1f)
                )
                PmcStatBadge(
                    label = atlLabel,
                    valueText = String.format(currentLocale, "%.1f", pmcSummary.currentAtl),
                    badgeText = null,
                    borderColor = TTColor.PmcAtlMagenta,
                    statusColor = TTColor.PmcAtlMagenta,
                    curveSwatchColor = TTColor.PmcAtlMagenta,
                    swatchTestTag = "pmc_swatch_atl",
                    cardTestTag = "pmc_card_atl",
                    labelTestTag = "pmc_label_atl",
                    modifier = Modifier.weight(1f)
                )
                PmcStatBadge(
                    label = tsbLabel,
                    valueText = String.format(currentLocale, "%+.1f", pmcSummary.currentTsb),
                    badgeText = tsbZoneText,
                    borderColor = TTColor.PmcTsbAmber,
                    statusColor = tsbZoneColor,
                    curveSwatchColor = TTColor.PmcTsbAmber,
                    swatchTestTag = "pmc_swatch_tsb",
                    cardTestTag = "pmc_card_tsb",
                    labelTestTag = "pmc_label_tsb",
                    badgeTextTestTag = "pmc_tsb_readiness_text",
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2: Derived non-plotted KPIs — neutral borders avoid colliding with curve colors (REQ-UI-2791.1, .5; REQ-UI-2792.1)
            val isAcwrBuildingBase = pmcSummary.currentAcwr == null &&
                (pmcSummary.dailyPoints.isNotEmpty() || pmcPlotPoints.isNotEmpty())
            val acwrZoneText = when (pmcSummary.currentAcwrZone) {
                AcwrZone.UNDER_TRAINING -> stringResource(R.string.athlete_profile_pmc_acwr_under)
                AcwrZone.SWEET_SPOT -> stringResource(R.string.athlete_profile_pmc_acwr_sweet_spot)
                AcwrZone.HIGH_LOAD_CAUTION -> stringResource(R.string.athlete_profile_pmc_acwr_caution)
                AcwrZone.DANGER_ZONE -> stringResource(R.string.athlete_profile_pmc_acwr_danger)
                null -> if (isAcwrBuildingBase) {
                    stringResource(R.string.athlete_profile_pmc_acwr_building_base)
                } else {
                    null
                }
            }
            val acwrStatusColor = when (pmcSummary.currentAcwrZone) {
                AcwrZone.SWEET_SPOT -> TTColor.PmcFreshGreen
                AcwrZone.HIGH_LOAD_CAUTION -> TTColor.PmcCautionOrange
                AcwrZone.DANGER_ZONE -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .testTag("pmc_row_derived"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PmcStatBadge(
                    label = stringResource(R.string.athlete_profile_pmc_acwr_label),
                    valueText = pmcSummary.currentAcwr?.let { String.format(currentLocale, "%.2f", it) } ?: "—",
                    badgeText = acwrZoneText,
                    borderColor = neutralBorderColor,
                    statusColor = acwrStatusColor,
                    curveSwatchColor = null,
                    cardTestTag = "pmc_card_acwr",
                    valueTextTestTag = "pmc_acwr_value_text",
                    badgeTextTestTag = "pmc_acwr_zone_text",
                    modifier = Modifier.weight(1f)
                )
                PmcStatBadge(
                    label = stringResource(R.string.athlete_profile_pmc_ramp_label),
                    valueText = stringResource(
                        R.string.athlete_profile_pmc_ramp_format,
                        pmcSummary.currentRampRate7d
                    ),
                    badgeText = if (pmcSummary.isAggressiveRampUp) {
                        stringResource(R.string.athlete_profile_pmc_ramp_warning)
                    } else {
                        null
                    },
                    borderColor = neutralBorderColor,
                    statusColor = if (pmcSummary.isAggressiveRampUp) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    curveSwatchColor = null,
                    cardTestTag = "pmc_card_ramp",
                    modifier = Modifier.weight(1f)
                )
            }

            // Time Range Selector Pills (Rule 38: compact non-wrapping single-line pills)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val rangeOptions = listOf(
                    MetricsTimeRange.ONE_MONTH to stringResource(R.string.athlete_profile_range_1m),
                    MetricsTimeRange.THREE_MONTHS to stringResource(R.string.athlete_profile_range_3m),
                    MetricsTimeRange.SIX_MONTHS to stringResource(R.string.athlete_profile_range_6m),
                    MetricsTimeRange.ONE_YEAR to stringResource(R.string.athlete_profile_range_1y),
                    MetricsTimeRange.ALL to stringResource(R.string.athlete_profile_range_all)
                )
                rangeOptions.forEach { (range, label) ->
                    CompactSelectorPill(
                        label = label,
                        selected = selectedPmcTimeRange == range,
                        onClick = { onSelectPmcTimeRange(range) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Compact responsive 3-curve legend using existing ATT-5678 trademark-free 9-locale labels (REQ-UI-2791.1)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pmc_chart_legend"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PmcLegendItem(color = TTColor.PmcCtlBlue, label = ctlLabel, testTag = "pmc_legend_ctl")
                PmcLegendItem(color = TTColor.PmcAtlMagenta, label = atlLabel, testTag = "pmc_legend_atl")
                PmcLegendItem(color = TTColor.PmcTsbAmber, label = tsbLabel, testTag = "pmc_legend_tsb")
            }

            PerformanceManagementChartCanvas(
                points = pmcPlotPoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .testTag("pmc_chart_canvas")
            )
        }
    }
}

@Composable
private fun PmcLegendItem(
    color: Color,
    label: String,
    testTag: String
) {
    Row(
        modifier = Modifier.testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 12.dp, height = 3.dp)
                .background(color = color, shape = RoundedCornerShape(2.dp))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PmcStatBadge(
    label: String,
    valueText: String,
    badgeText: String?,
    borderColor: Color,
    statusColor: Color,
    curveSwatchColor: Color?,
    modifier: Modifier = Modifier,
    swatchTestTag: String? = null,
    cardTestTag: String? = null,
    labelTestTag: String? = null,
    valueTextTestTag: String? = null,
    badgeTextTestTag: String? = null
) {
    val cardModifier = if (cardTestTag != null) {
        modifier.fillMaxHeight().testTag(cardTestTag)
    } else {
        modifier.fillMaxHeight()
    }
    Surface(
        modifier = cardModifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, borderColor.copy(alpha = if (curveSwatchColor != null) 0.65f else 0.55f))
    ) {
        Column {
            if (curveSwatchColor != null) {
                val swatchModifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(color = curveSwatchColor)
                    .let { if (swatchTestTag != null) it.testTag(swatchTestTag) else it }
                Box(modifier = swatchModifier)
            }
            Column(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (labelTestTag != null) Modifier.testTag(labelTestTag) else Modifier
                )
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (valueTextTestTag != null) Modifier.testTag(valueTextTestTag) else Modifier
                )
                if (!badgeText.isNullOrEmpty()) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (badgeTextTestTag != null) Modifier.testTag(badgeTextTestTag) else Modifier
                    )
                }
            }
        }
    }
}

/**
 * Multi-curve Canvas chart plotting Fitness (CTL — blue area/line), Fatigue (ATL — magenta line),
 * and Form / Race-Readiness (TSB — amber line) over time with clean rounded Y-axis ticks and
 * a zero-baseline-clamped CTL fill (`REQ-UI-401.3`, `REQ-UI-2791.3`, `REQ-UI-2791.4`).
 */
@Composable
fun PerformanceManagementChartCanvas(
    points: List<PmcDailyPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.athlete_profile_pmc_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val currentLocale = LocalConfiguration.current.locales[0]
    val ctlColor = TTColor.PmcCtlBlue
    val atlColor = TTColor.PmcAtlMagenta
    val tsbColor = TTColor.PmcTsbAmber
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val zeroLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        val leftPad = 40.dp.toPx()
        val rightPad = 14.dp.toPx()
        val topPad = 12.dp.toPx()
        val bottomPad = 24.dp.toPx()

        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        val yAxisScale = AthleteMetricsPlotMath.computePmcYAxisScale(points)
        val yMin = yAxisScale.yMin.toDouble()
        val yMax = yAxisScale.yMax.toDouble()
        val yRange = (yMax - yMin).coerceAtLeast(1.0)

        val minTs = points.first().dayStartTimestampMs
        val maxTs = points.last().dayStartTimestampMs
        val tsSpan = (maxTs - minTs).coerceAtLeast(1L)

        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = labelColor.toArgb()
            textSize = 10.sp.toPx()
        }

        // Draw clean rounded horizontal grid lines + Y-axis labels (0 emphasized once, no duplicate zero line)
        for (tick in yAxisScale.ticks) {
            val frac = ((tick.toDouble() - yMin) / yRange).toFloat().coerceIn(0f, 1f)
            val y = topPad + plotH * (1f - frac)
            val isZero = abs(tick) < 1e-4f
            drawLine(
                color = if (isZero) zeroLineColor else gridColor,
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = if (isZero) 1.4.dp.toPx() else 1.dp.toPx()
            )
            drawContext.canvas.nativeCanvas.drawText(
                tick.roundToInt().toString(),
                4.dp.toPx(),
                y + 3.5.dp.toPx(),
                textPaint
            )
        }

        fun mapSeries(selector: (PmcDailyPoint) -> Double): List<Offset> =
            points.map { pt ->
                val x = if (points.size == 1 || maxTs == minTs) {
                    leftPad + plotW * 0.5f
                } else {
                    leftPad + ((pt.dayStartTimestampMs - minTs).toDouble() / tsSpan.toDouble()).toFloat() * plotW
                }
                val normY = ((selector(pt) - yMin) / yRange).toFloat().coerceIn(0f, 1f)
                val y = topPad + plotH * (1f - normY)
                Offset(x, y)
            }

        val ctlOffsets = mapSeries { it.ctl }
        val atlOffsets = mapSeries { it.atl }
        val tsbOffsets = mapSeries { it.tsb }

        if (ctlOffsets.size >= 2) {
            val zeroY = AthleteMetricsPlotMath.computePmcZeroBaselineY(
                yMin = yAxisScale.yMin,
                yMax = yAxisScale.yMax,
                topPad = topPad,
                plotH = plotH
            )
            // Anchor CTL area fill strictly to clamped zeroY (REQ-UI-2791.3) and guard LinearGradient endY > startY
            if (zeroY > topPad + 1f && points.any { it.ctl > 0.0 }) {
                val fillPath = Path().apply {
                    moveTo(ctlOffsets.first().x, zeroY)
                    ctlOffsets.forEach { lineTo(it.x, it.y.coerceAtMost(zeroY)) }
                    lineTo(ctlOffsets.last().x, zeroY)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(ctlColor.copy(alpha = 0.22f), Color.Transparent),
                        startY = topPad,
                        endY = zeroY
                    )
                )
            }

            fun drawCurve(offsets: List<Offset>, color: Color, strokeDp: Float) {
                val path = Path().apply {
                    moveTo(offsets.first().x, offsets.first().y)
                    for (i in 1 until offsets.size) {
                        lineTo(offsets[i].x, offsets[i].y)
                    }
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = strokeDp.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            drawCurve(tsbOffsets, tsbColor, 1.8f)
            drawCurve(atlOffsets, atlColor, 2.0f)
            drawCurve(ctlOffsets, ctlColor, 2.5f)
        } else {
            drawCircle(color = tsbColor, radius = 3.5.dp.toPx(), center = tsbOffsets.first())
            drawCircle(color = atlColor, radius = 3.5.dp.toPx(), center = atlOffsets.first())
            drawCircle(color = ctlColor, radius = 4.dp.toPx(), center = ctlOffsets.first())
        }

        // X-axis date ticks (start and end)
        val startStr = AthleteMetricsPlotMath.formatShortDate(minTs, currentLocale)
        drawContext.canvas.nativeCanvas.drawText(
            startStr,
            leftPad,
            size.height - 4.dp.toPx(),
            textPaint
        )
        if (points.size > 1 && maxTs > minTs) {
            val endStr = AthleteMetricsPlotMath.formatShortDate(maxTs, currentLocale)
            val endW = textPaint.measureText(endStr)
            drawContext.canvas.nativeCanvas.drawText(
                endStr,
                (leftPad + plotW - endW).coerceAtLeast(leftPad),
                size.height - 4.dp.toPx(),
                textPaint
            )
        }
    }
}

@Preview(showBackground = true, name = "PMC Card - Zero-CTL Negative-TSB Light")
@Composable
private fun PerformanceManagementChartCardLightPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        PerformanceManagementChartCard(
            pmcSummary = previewSamplePmcSummary(),
            pmcPlotPoints = previewSamplePmcPoints(),
            selectedPmcTimeRange = MetricsTimeRange.THREE_MONTHS
        )
    }
}

@Preview(showBackground = true, name = "PMC Card - Zero-CTL Negative-TSB Dark AMOLED")
@Composable
private fun PerformanceManagementChartCardDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true, amoled = true) {
        PerformanceManagementChartCard(
            pmcSummary = previewSamplePmcSummary(),
            pmcPlotPoints = previewSamplePmcPoints(),
            selectedPmcTimeRange = MetricsTimeRange.THREE_MONTHS
        )
    }
}

private fun previewSamplePmcSummary(): PmcSummaryResult = PmcSummaryResult(
    dailyPoints = emptyList(),
    currentCtl = 7.0,
    currentAtl = 22.7,
    currentTsb = -13.4,
    currentAcwr = null,
    currentRampRate7d = 4.2,
    currentTsbZone = TsbReadinessZone.OPTIMAL_OVERLOAD,
    currentAcwrZone = null,
    isAggressiveRampUp = false
)

private fun previewSamplePmcPoints(): List<PmcDailyPoint> {
    val startMs = 1_787_443_200_000L
    val dayMs = 86_400_000L
    val startEpochDay = startMs / dayMs
    return List(90) { day ->
        val ts = startMs + day * dayMs
        val (tss, ctl, atl) = when {
            day < 50 -> Triple(0.0, 0.0, 0.0)
            day == 50 -> Triple(150.0, 3.6, 21.4)
            day < 80 -> {
                val k = day - 50
                Triple(0.0, 3.6 * kotlin.math.exp(-k / 42.0), 21.4 * kotlin.math.exp(-k / 7.0))
            }
            else -> {
                val k = day - 80
                Triple(40.0, 2.0 + k * 0.55, 3.0 + k * 2.1)
            }
        }
        val tsb = ctl - atl
        val acwr = if (ctl > 0.0) atl / ctl else null
        PmcDailyPoint(
            dayEpochIndex = startEpochDay + day,
            dayStartTimestampMs = ts,
            dailyTss = tss,
            ctl = ctl,
            atl = atl,
            tsb = tsb,
            acwr = acwr,
            rampRate7d = 0.0,
            tsbZone = TsbReadinessZone.OPTIMAL_OVERLOAD,
            acwrZone = if (acwr != null && acwr > 1.5) AcwrZone.DANGER_ZONE else null,
            isAggressiveRampUp = false
        )
    }
}
