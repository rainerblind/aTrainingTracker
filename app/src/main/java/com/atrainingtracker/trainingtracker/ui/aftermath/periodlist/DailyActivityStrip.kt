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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure calculation helper: Computes the proportional height fraction of a daily metric bar (REQ-PER-027.4).
 * Returns 0f for rest/zero days, and clamps positive ratios into [0.15f, 1.0f] for guaranteed visibility.
 */
fun calculateDailyBarFraction(value: Double, maxValue: Double): Float {
    if (value <= 0.0 || maxValue <= 0.0) return 0f
    return (value / maxValue).toFloat().coerceIn(0.15f, 1.0f)
}

/**
 * Pure calculation helper: Extracts the scalar metric value from DailySportActivity for the active metric mode.
 */
fun extractDailyMetricValue(activity: DailySportActivity?, metric: PeriodBarMetric): Double {
    if (activity == null) return 0.0
    return when (metric) {
        PeriodBarMetric.DURATION -> activity.durationSec.toDouble()
        PeriodBarMetric.DISTANCE -> activity.distanceMeters
        PeriodBarMetric.ASCENT -> activity.ascentMeters.toDouble()
    }
}

/**
 * Pure calculation helper: Extracts the peak metric scalar from DailySportContribution for normalization.
 */
fun extractMaxDailyMetricValue(contribution: DailySportContribution?, metric: PeriodBarMetric): Double {
    if (contribution == null) return 0.0
    return when (metric) {
        PeriodBarMetric.DURATION -> contribution.maxDailyDurationSec.toDouble()
        PeriodBarMetric.DISTANCE -> contribution.maxDailyDistanceMeters
        PeriodBarMetric.ASCENT -> contribution.maxDailyAscentMeters.toDouble()
    }
}

/**
 * Pure calculation helper: Generates an inclusive sequence of LocalDates between start and end.
 */
fun generateDateSequence(startDate: LocalDate, endDate: LocalDate): List<LocalDate> {
    if (startDate.isAfter(endDate)) return emptyList()
    val dates = ArrayList<LocalDate>()
    var current = startDate
    while (!current.isAfter(endDate)) {
        dates.add(current)
        current = current.plusDays(1)
    }
    return dates
}

/**
 * Resolves the primary semantic color token for the given sport type.
 */
@Composable
fun getSportBarColor(sport: BSportType): Color {
    return when (sport) {
        BSportType.BIKE -> TTColor.BikeSportBarColor
        BSportType.RUN -> TTColor.RunSportBarColor
        else -> TTColor.OtherSportBarColor
    }
}

/**
 * Renders an embedded daily activity intensity / mini-bar strip for Months and Weeks inside SportStatsRow (REQ-PER-027).
 *
 * @param sportType The sport type of the containing row.
 * @param periodType Must be WEEK or MONTH.
 * @param startDate The first date of the period range.
 * @param endDate The last date of the period range.
 * @param dailyContribution Daily aggregation dataset containing daily activities and max metrics.
 * @param activeMetric The active metric selector (DURATION, DISTANCE, ASCENT).
 * @param onDayClick Callback triggered when an active day is tapped.
 */
@Composable
fun DailyActivityStrip(
    sportType: BSportType,
    periodType: PeriodType,
    startDate: LocalDate,
    endDate: LocalDate,
    dailyContribution: DailySportContribution,
    modifier: Modifier = Modifier,
    activeMetric: PeriodBarMetric = PeriodBarMetric.DURATION,
    onDayClick: (LocalDate, DailySportActivity?) -> Unit = { _, _ -> }
) {
    val dates = remember(startDate, endDate) {
        generateDateSequence(startDate, endDate)
    }
    if (dates.isEmpty()) return

    val maxMetricValue = remember(dailyContribution, activeMetric) {
        extractMaxDailyMetricValue(dailyContribution, activeMetric)
    }
    val sportColor = getSportBarColor(sportType)
    val emptyTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val accessibilityLabel = stringResource(R.string.workout_periods__daily_activity_strip)

    val tf = remember { TimeFormatter() }
    val df = remember { DistanceFormatter() }
    val af = remember { AltitudeFormatter() }

    // Section 1.10 compliance: selectedDate updated exclusively inside user event handlers
    var selectedDate by remember(startDate, endDate) { mutableStateOf<LocalDate?>(null) }

    Column(
        modifier = modifier.semantics {
            contentDescription = accessibilityLabel
        }
    ) {
        if (periodType == PeriodType.WEEK) {
            // 7-day layout (Monday..Sunday)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                dates.forEach { date ->
                    val activity = dailyContribution.dailyActivities[date]
                    val metricValue = extractDailyMetricValue(activity, activeMetric)
                    val fraction = calculateDailyBarFraction(metricValue, maxMetricValue)
                    val isSelected = selectedDate == date

                    WeekDayColumn(
                        date = date,
                        fraction = fraction,
                        sportColor = sportColor,
                        emptyTrackColor = emptyTrackColor,
                        isSelected = isSelected,
                        onClick = {
                            selectedDate = if (selectedDate == date) null else date
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            // Month layout (28..31 compact non-scrolling columns)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    dates.forEach { date ->
                        val activity = dailyContribution.dailyActivities[date]
                        val metricValue = extractDailyMetricValue(activity, activeMetric)
                        val fraction = calculateDailyBarFraction(metricValue, maxMetricValue)
                        val isSelected = selectedDate == date

                        MonthDayBar(
                            date = date,
                            fraction = fraction,
                            sportColor = sportColor,
                            emptyTrackColor = emptyTrackColor,
                            isSelected = isSelected,
                            onClick = {
                                selectedDate = if (selectedDate == date) null else date
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Milestone Day Numbers: 1, 10, 20, end of month
                MonthMilestoneLabels(dates = dates)
            }
        }

        // Selected Day Detail Banner (REQ-PER-027.6)
        if (selectedDate != null) {
            val selectedActivity = dailyContribution.dailyActivities[selectedDate]
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = TTAlpha.Ghost),
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = TTAlpha.Subtle)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = selectedActivity != null && selectedActivity.count > 0) {
                        onDayClick(selectedDate!!, selectedActivity)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = selectedDate!!.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.getDefault())),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedActivity != null && selectedActivity.count > 0) {
                            val metricString = when (activeMetric) {
                                PeriodBarMetric.DURATION -> tf.format_with_units(selectedActivity.durationSec)
                                PeriodBarMetric.DISTANCE -> df.format_with_units(selectedActivity.distanceMeters)
                                PeriodBarMetric.ASCENT -> af.format_with_units(selectedActivity.ascentMeters.toFloat())
                            }
                            val workoutsCount = pluralStringResource(
                                R.plurals.workout_periods__workouts,
                                selectedActivity.count,
                                selectedActivity.count
                            )
                            Text(
                                text = "$metricString • $workoutsCount",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.workout_periods__no_activity),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (selectedActivity != null && selectedActivity.count > 0) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekDayColumn(
    date: LocalDate,
    fraction: Float,
    sportColor: Color,
    emptyTrackColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dayInitial = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2)
    val dayNumber = date.dayOfMonth.toString()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = dayInitial,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium),
            fontSize = 10.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(3.dp))
                .then(
                    if (isSelected) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                    } else Modifier
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (fraction > 0f) {
                val barHeight = (28.dp * fraction).coerceAtLeast(4.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(barHeight)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(sportColor)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(emptyTrackColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = dayNumber,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun MonthDayBar(
    date: LocalDate,
    fraction: Float,
    sportColor: Color,
    emptyTrackColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(26.dp)
            .clip(RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                } else Modifier
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (fraction > 0f) {
            val barHeight = (26.dp * fraction).coerceAtLeast(3.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp))
                    .background(sportColor)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(emptyTrackColor)
            )
        }
    }
}

@Composable
private fun MonthMilestoneLabels(dates: List<LocalDate>) {
    val totalDays = dates.size
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
        if (totalDays >= 10) {
            Text("10", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
        }
        if (totalDays >= 20) {
            Text("20", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
        }
        Text(totalDays.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
    }
}

@Preview(name = "DailyActivityStrip - Week Light", showBackground = true)
@Composable
private fun PreviewDailyActivityStripWeekLight() {
    val start = LocalDate.of(2026, 5, 11)
    val end = LocalDate.of(2026, 5, 17)
    val daily = mapOf(
        start.plusDays(1) to DailySportActivity(start.plusDays(1), BSportType.BIKE, 1, 3600L, 25000.0, 300L),
        start.plusDays(3) to DailySportActivity(start.plusDays(3), BSportType.BIKE, 2, 7200L, 50000.0, 600L),
        start.plusDays(5) to DailySportActivity(start.plusDays(5), BSportType.BIKE, 1, 5400L, 38000.0, 450L)
    )
    val contribution = DailySportContribution(
        sportType = BSportType.BIKE,
        dailyActivities = daily,
        maxDailyDurationSec = 7200L,
        maxDailyDistanceMeters = 50000.0,
        maxDailyAscentMeters = 600L
    )
    ATrainingTrackerTheme {
        Surface {
            DailyActivityStrip(
                sportType = BSportType.BIKE,
                periodType = PeriodType.WEEK,
                startDate = start,
                endDate = end,
                dailyContribution = contribution,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(name = "DailyActivityStrip - Month Light", showBackground = true)
@Composable
private fun PreviewDailyActivityStripMonthLight() {
    val start = LocalDate.of(2026, 5, 1)
    val end = LocalDate.of(2026, 5, 31)
    val daily = mapOf(
        start.plusDays(2) to DailySportActivity(start.plusDays(2), BSportType.RUN, 1, 3600L, 10000.0, 50L),
        start.plusDays(8) to DailySportActivity(start.plusDays(8), BSportType.RUN, 1, 4200L, 12000.0, 80L),
        start.plusDays(15) to DailySportActivity(start.plusDays(15), BSportType.RUN, 1, 5000L, 15000.0, 100L),
        start.plusDays(25) to DailySportActivity(start.plusDays(25), BSportType.RUN, 1, 6000L, 18000.0, 120L)
    )
    val contribution = DailySportContribution(
        sportType = BSportType.RUN,
        dailyActivities = daily,
        maxDailyDurationSec = 6000L,
        maxDailyDistanceMeters = 18000.0,
        maxDailyAscentMeters = 120L
    )
    ATrainingTrackerTheme {
        Surface {
            DailyActivityStrip(
                sportType = BSportType.RUN,
                periodType = PeriodType.MONTH,
                startDate = start,
                endDate = end,
                dailyContribution = contribution,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
