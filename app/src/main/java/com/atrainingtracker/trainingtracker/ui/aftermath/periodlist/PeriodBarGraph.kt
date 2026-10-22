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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Supported metrics for [PeriodBarGraph] visualization.
 */
enum class PeriodBarMetric {
    DURATION,
    DISTANCE,
    ASCENT
}

/**
 * Sport color palette for stacked bar segments.
 */
val BikeSportBarColor = Color(0xFF1E88E5)
val RunSportBarColor = Color(0xFFFF7043)
val OtherSportBarColor = Color(0xFF26A69A)

/**
 * Internal segment representing a sport type contribution to a period bar.
 */
data class SportSegment(
    val sportType: BSportType,
    val value: Double,
    val fraction: Float,
    val color: Color
)

/**
 * Item model representing either an active period or an interpolated rest bucket.
 */
data class PeriodBarItem(
    val summary: PeriodSummary?,
    val originalIndex: Int?,
    val startTimestampS: Long,
    val periodType: PeriodType,
    val isRest: Boolean,
    val durationSec: Long,
    val distanceMeters: Double,
    val ascentMeters: Long,
    val sportSegments: List<SportSegment> = emptyList()
)

/**
 * Interpolates timeline gaps between chronological period entries, inserting zero-volume rest buckets
 * to maintain temporal spacing without distorting the horizontal timeline axis (REQ-PER-018.1).
 */
fun interpolateTimelineGaps(
    periods: List<PeriodSummary>,
    maxGapBuckets: Int = 30,
    zoneId: ZoneId = ZoneId.systemDefault()
): List<PeriodBarItem> {
    if (periods.isEmpty()) return emptyList()

    val indexed = periods.mapIndexed { index, summary -> index to summary }
    val chronological = indexed.sortedBy { it.second.startTimestampS }
    val type = chronological.first().second.periodType
    val result = mutableListOf<PeriodBarItem>()

    for (i in chronological.indices) {
        val (origIdx, summary) = chronological[i]
        val totalAscent = summary.sportStats.values.sumOf { it.totalAscentMeters }

        result.add(
            PeriodBarItem(
                summary = summary,
                originalIndex = origIdx,
                startTimestampS = summary.startTimestampS,
                periodType = type,
                isRest = false,
                durationSec = summary.totalDurationSec,
                distanceMeters = summary.totalDistance,
                ascentMeters = totalAscent
            )
        )

        if (i < chronological.size - 1) {
            val nextStartS = chronological[i + 1].second.startTimestampS
            var cursorDate = ZonedDateTime.ofInstant(Instant.ofEpochSecond(summary.startTimestampS), zoneId)
            cursorDate = advanceCalendarUnit(cursorDate, type)

            var inserted = 0
            while (cursorDate.toEpochSecond() < nextStartS && inserted < maxGapBuckets) {
                result.add(
                    PeriodBarItem(
                        summary = null,
                        originalIndex = null,
                        startTimestampS = cursorDate.toEpochSecond(),
                        periodType = type,
                        isRest = true,
                        durationSec = 0L,
                        distanceMeters = 0.0,
                        ascentMeters = 0L
                    )
                )
                inserted++
                cursorDate = advanceCalendarUnit(cursorDate, type)
            }
        }
    }
    return result
}

private fun advanceCalendarUnit(date: ZonedDateTime, type: PeriodType): ZonedDateTime {
    return when (type) {
        PeriodType.DAY -> date.plusDays(1)
        PeriodType.WEEK -> date.plusWeeks(1)
        PeriodType.MONTH -> date.plusMonths(1)
        PeriodType.YEAR -> date.plusYears(1)
    }
}

/**
 * Calculates proportional sport segments for an active [PeriodSummary] under the given [PeriodBarMetric].
 */
fun calculateSportSegments(
    summary: PeriodSummary?,
    metric: PeriodBarMetric
): List<SportSegment> {
    if (summary == null) return emptyList()
    val total = when (metric) {
        PeriodBarMetric.DURATION -> summary.totalDurationSec.toDouble()
        PeriodBarMetric.DISTANCE -> summary.totalDistance
        PeriodBarMetric.ASCENT -> summary.sportStats.values.sumOf { it.totalAscentMeters }.toDouble()
    }
    if (total <= 0.0) return emptyList()

    val segments = mutableListOf<SportSegment>()
    val canonicalSports = listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN)
    val allSports = (canonicalSports + summary.sportStats.keys).distinct()

    for (sport in allSports) {
        val stats = summary.sportStats[sport] ?: continue
        val sportValue = when (metric) {
            PeriodBarMetric.DURATION -> stats.totalDurationSec.toDouble()
            PeriodBarMetric.DISTANCE -> stats.totalDistanceMeters
            PeriodBarMetric.ASCENT -> stats.totalAscentMeters.toDouble()
        }
        if (sportValue > 0.0) {
            val color = when (sport) {
                BSportType.BIKE -> BikeSportBarColor
                BSportType.RUN -> RunSportBarColor
                else -> OtherSportBarColor
            }
            segments.add(
                SportSegment(
                    sportType = sport,
                    value = sportValue,
                    fraction = (sportValue / total).toFloat(),
                    color = color
                )
            )
        }
    }

    if (segments.isEmpty() && total > 0.0) {
        segments.add(
            SportSegment(
                sportType = BSportType.UNKNOWN,
                value = total,
                fraction = 1.0f,
                color = BikeSportBarColor
            )
        )
    }

    return segments
}

/**
 * Formats metric values into compact top-bar labels with sub-hour precision (REQ-PER-018.2).
 */
fun formatMetricValue(value: Double, metric: PeriodBarMetric, periodType: PeriodType): String {
    if (value <= 0.0) return ""
    return when (metric) {
        PeriodBarMetric.DURATION -> {
            val totalSec = value.toLong()
            when {
                totalSec < 60 -> "<1m"
                totalSec < 3600 -> "${totalSec / 60}m"
                totalSec < 36000 -> {
                    val hours = totalSec / 3600
                    val minutes = (totalSec % 3600) / 60
                    if (minutes < 6) {
                        "${hours}h"
                    } else {
                        val frac = (minutes / 60.0 * 10).roundToInt()
                        if (frac >= 10) "${hours + 1}h"
                        else if (frac == 0) "${hours}h"
                        else String.format(Locale.US, "%d.%dh", hours, frac)
                    }
                }
                else -> "${(totalSec / 3600.0).roundToInt()}h"
            }
        }
        PeriodBarMetric.DISTANCE -> {
            val meters = value
            when {
                meters < 1000.0 -> "${meters.roundToInt()}m"
                meters < 10000.0 -> String.format(Locale.US, "%.1fk", meters / 1000.0)
                else -> "${(meters / 1000.0).roundToInt()}k"
            }
        }
        PeriodBarMetric.ASCENT -> {
            "${value.roundToInt()}m"
        }
    }
}

/**
 * Formats period start timestamps into compact X-axis date/period identifiers (REQ-PER-018.2).
 */
fun formatPeriodXLabel(
    timestampS: Long,
    periodType: PeriodType,
    locale: Locale = Locale.getDefault(),
    zoneId: ZoneId = ZoneId.systemDefault()
): String {
    val dt = ZonedDateTime.ofInstant(Instant.ofEpochSecond(timestampS), zoneId)
    return when (periodType) {
        PeriodType.DAY -> dt.format(DateTimeFormatter.ofPattern("d.M", locale))
        PeriodType.WEEK -> "W${dt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
        PeriodType.MONTH -> dt.format(DateTimeFormatter.ofPattern("MMM", locale))
        PeriodType.YEAR -> dt.format(DateTimeFormatter.ofPattern("yyyy", locale))
    }
}

/**
 * Upgraded timeline bar graph header with rest-period gap spacing, sub-hour duration labels,
 * compact X-axis period labels, sport-stacked bars, and metric mode switching (REQ-PER-018).
 */
@Composable
fun PeriodBarGraph(
    periods: List<PeriodSummary>,
    currentScrollState: LazyListState,
    onBarClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (periods.isEmpty()) return

    var selectedMetric by rememberSaveable { mutableStateOf(PeriodBarMetric.DURATION) }
    val graphItems = remember(periods) { interpolateTimelineGaps(periods) }
    if (graphItems.isEmpty()) return

    val barWidth = remember(periods) {
        when (periods.firstOrNull()?.periodType) {
            PeriodType.DAY -> 18.dp
            PeriodType.WEEK -> 28.dp
            PeriodType.MONTH -> 48.dp
            PeriodType.YEAR -> 80.dp
            else -> 18.dp
        }
    }

    val maxMetricValue = remember(graphItems, selectedMetric) {
        graphItems.maxOfOrNull { item ->
            when (selectedMetric) {
                PeriodBarMetric.DURATION -> item.durationSec.toDouble()
                PeriodBarMetric.DISTANCE -> item.distanceMeters
                PeriodBarMetric.ASCENT -> item.ascentMeters.toDouble()
            }
        }?.coerceAtLeast(1.0) ?: 1.0
    }

    val graphScrollState = rememberLazyListState()
    val firstVisibleIndex by remember { derivedStateOf { currentScrollState.firstVisibleItemIndex } }

    LaunchedEffect(firstVisibleIndex, graphItems.size) {
        val targetGraphIndex = graphItems.indexOfFirst { it.originalIndex == firstVisibleIndex }
        if (targetGraphIndex in graphItems.indices) {
            if (currentScrollState.isScrollInProgress) {
                graphScrollState.animateScrollToItem(targetGraphIndex)
            } else {
                graphScrollState.scrollToItem(targetGraphIndex)
            }
        }
    }

    Column(modifier = modifier) {
        // Metric Switcher Row
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(horizontal = 16.dp)
        ) {
            PeriodBarMetric.values().forEachIndexed { index, metric ->
                val label = when (metric) {
                    PeriodBarMetric.DURATION -> stringResource(R.string.filter_section_duration)
                    PeriodBarMetric.DISTANCE -> stringResource(R.string.filter_section_distance)
                    PeriodBarMetric.ASCENT -> stringResource(R.string.stats_ascent)
                }
                SegmentedButton(
                    selected = selectedMetric == metric,
                    onClick = { selectedMetric = metric },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                    icon = {},
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selectedMetric == metric) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Bar Chart Timeline Row
        LazyRow(
            state = graphScrollState,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            itemsIndexed(graphItems) { _, item ->
                val isSelected = item.originalIndex != null && item.originalIndex == firstVisibleIndex
                val metricValue = when (selectedMetric) {
                    PeriodBarMetric.DURATION -> item.durationSec.toDouble()
                    PeriodBarMetric.DISTANCE -> item.distanceMeters
                    PeriodBarMetric.ASCENT -> item.ascentMeters.toDouble()
                }
                val heightFraction = (metricValue / maxMetricValue).toFloat().coerceIn(0.0f, 1.0f)
                val valueString = formatMetricValue(metricValue, selectedMetric, item.periodType)
                val sportSegments = remember(item, selectedMetric) {
                    if (!item.isRest) calculateSportSegments(item.summary, selectedMetric) else emptyList()
                }

                Column(
                    modifier = Modifier
                        .width(barWidth)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // Top Value Label
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (!item.isRest && valueString.isNotEmpty()) {
                            Text(
                                text = valueString,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.wrapContentWidth(unbounded = true),
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = TTAlpha.Medium)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Bar Body or Rest Baseline Tick
                    if (item.isRest) {
                        Box(
                            modifier = Modifier
                                .width(barWidth.coerceAtMost(10.dp))
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = TTAlpha.Disabled))
                        )
                    } else {
                        val barAlpha = if (isSelected) 1.0f else 0.55f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .fillMaxHeight(heightFraction.coerceAtLeast(0.08f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .clickable { item.originalIndex?.let { onBarClick(it) } }
                        ) {
                            if (sportSegments.size <= 1) {
                                val singleColor = sportSegments.firstOrNull()?.color ?: MaterialTheme.colorScheme.primary
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(singleColor.copy(alpha = barAlpha))
                                )
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    for (segment in sportSegments) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(segment.fraction.coerceAtLeast(0.01f))
                                                .background(segment.color.copy(alpha = barAlpha))
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Bottom X-Axis Label
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = formatPeriodXLabel(item.startTimestampS, item.periodType),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = TTAlpha.Medium),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.wrapContentWidth(unbounded = true)
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "PeriodBarGraph Preview Light", showBackground = true)
@Composable
private fun PeriodBarGraphPreview() {
    val samplePeriods = listOf(
        PeriodSummary(
            periodLabel = "Today",
            periodDateRange = "",
            periodType = PeriodType.DAY,
            startTimestampS = 1729641600L,
            endTimestampS = 1729728000L,
            totalWorkouts = 1,
            totalDurationSec = 2700L,
            totalDistance = 8500.0,
            sportStats = mapOf(
                BSportType.RUN to SportStats(1, 2700L, 8500.0, 120L, emptyMap(), null)
            ),
            sortKey = "2026-10-23"
        ),
        PeriodSummary(
            periodLabel = "Yesterday",
            periodDateRange = "",
            periodType = PeriodType.DAY,
            startTimestampS = 1729468800L, // 2 days prior (leaves 1 rest day)
            endTimestampS = 1729555200L,
            totalWorkouts = 2,
            totalDurationSec = 5400L,
            totalDistance = 35000.0,
            sportStats = mapOf(
                BSportType.BIKE to SportStats(1, 4000L, 30000.0, 350L, emptyMap(), null),
                BSportType.RUN to SportStats(1, 1400L, 5000.0, 50L, emptyMap(), null)
            ),
            sortKey = "2026-10-21"
        )
    )

    Surface {
        PeriodBarGraph(
            periods = samplePeriods,
            currentScrollState = rememberLazyListState(),
            onBarClick = {},
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
    }
}
