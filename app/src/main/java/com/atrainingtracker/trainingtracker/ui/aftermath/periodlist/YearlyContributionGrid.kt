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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Calculates positive volume quartiles (Q1, Q2, Q3) for a list of positive values (REQ-PER-025.5).
 */
fun calculateQuartiles(positiveValues: List<Double>): Triple<Double, Double, Double> {
    if (positiveValues.isEmpty()) return Triple(0.0, 0.0, 0.0)
    val sorted = positiveValues.sorted()
    val n = sorted.size
    val q1 = sorted[(n * 0.25).toInt().coerceIn(0, n - 1)]
    val q2 = sorted[(n * 0.50).toInt().coerceIn(0, n - 1)]
    val q3 = sorted[(n * 0.75).toInt().coerceIn(0, n - 1)]
    return Triple(q1, q2, q3)
}

/**
 * Assigns an activity volume to a contribution intensity level from 0 to 4 (REQ-PER-025.5).
 * Level 0 = zero / rest day
 * Level 1 = (0, Q1]
 * Level 2 = (Q1, Q2]
 * Level 3 = (Q2, Q3]
 * Level 4 = > Q3
 */
fun calculateQuartileLevel(value: Double, q1: Double, q2: Double, q3: Double): Int {
    if (value <= 0.0) return 0
    return when {
        value <= q1 -> 1
        value <= q2 -> 2
        value <= q3 -> 3
        else -> 4
    }
}

/**
 * Resolves the semantic cell color for a given sport and quartile level (REQ-PER-025.5).
 */
@Composable
fun getSportCellColor(sport: BSportType, level: Int): Color {
    val baseSportColor = when (sport) {
        BSportType.BIKE -> TTColor.BikeSportBarColor
        BSportType.RUN -> TTColor.RunSportBarColor
        else -> TTColor.OtherSportBarColor
    }
    val emptyCellColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    return when (level) {
        0 -> emptyCellColor
        1 -> baseSportColor.copy(alpha = 0.30f)
        2 -> baseSportColor.copy(alpha = 0.55f)
        3 -> baseSportColor.copy(alpha = 0.80f)
        else -> baseSportColor
    }
}

/**
 * Calculates the target column index to align at the right edge of the viewport (REQ-PER-025.9).
 * - For past calendar years (year < today.year): returns the last column (totalCols - 1)
 * - For future calendar years (year > today.year): returns 0
 * - For the current calendar year (year == today.year): returns the column containing today
 */
fun calculateTargetColumn(year: Int, today: LocalDate, startOffset: Int, totalCols: Int): Int {
    if (year < today.year) return totalCols - 1
    if (year > today.year) return 0
    val dayIndex = today.dayOfYear - 1
    return ((dayIndex + startOffset) / 7).coerceIn(0, totalCols - 1)
}

/**
 * Calculates the initial horizontal scroll offset required to align targetCol at the right edge
 * of the visible viewport (REQ-PER-025.9).
 *
 * S_target = maxValue - (totalCols - 1 - targetCol) * colPitchPx, clamped to [0, maxValue].
 */
fun calculateInitialScrollOffset(
    maxValue: Int,
    totalCols: Int,
    targetCol: Int,
    colPitchPx: Float
): Int {
    if (maxValue <= 0 || totalCols <= 0) return 0
    val remainingCols = (totalCols - 1 - targetCol).coerceAtLeast(0)
    val futureWeeksWidth = remainingCols * colPitchPx
    return (maxValue - futureWeeksWidth.roundToInt()).coerceIn(0, maxValue)
}

/**
 * Evaluates whether a day-of-week row index (0..6, Monday to Sunday) should display a label (REQ-PER-025.3).
 * Shows labels for Monday (0), Wednesday (2), Friday (4), and Sunday (6), establishing an even 2-day cadence.
 */
fun shouldShowDayOfWeekLabel(rowIdx: Int): Boolean = rowIdx in listOf(0, 2, 4, 6)

/**
 * Renders a GitHub-style 52/53-week x 7-day Activity Contribution Grid for a single sport type (REQ-PER-025).
 *
 * @param contribution Yearly sport contribution dataset with daily activity buckets.
 * @param activeMetric The master metric selector (DURATION, DISTANCE, ASCENT) slaved from Periods.
 * @param onDayClick Callback triggered when the athlete taps a day cell or its drill-down action.
 */
@Composable
fun YearlyContributionGrid(
    contribution: YearlySportContribution,
    activeMetric: PeriodBarMetric,
    onDayClick: (date: LocalDate, dayActivity: DailySportActivity?) -> Unit,
    showCard: Boolean = true,
    showSportHeader: Boolean = true,
    modifier: Modifier = Modifier
) {
    val year = contribution.year
    val jan1 = remember(year) { LocalDate.of(year, 1, 1) }
    val totalDays = remember(jan1) { jan1.lengthOfYear() }
    val startOffset = remember(jan1) { jan1.dayOfWeek.value - 1 } // Monday = 0
    val totalCols = remember(totalDays, startOffset) { ((totalDays - 1 + startOffset) / 7) + 1 }

    // Precompute quartiles for the active metric across positive days
    val quartiles = remember(contribution, activeMetric) {
        val positiveValues = contribution.dailyActivities.values.mapNotNull { activity ->
            val v = when (activeMetric) {
                PeriodBarMetric.DURATION -> activity.durationSec.toDouble()
                PeriodBarMetric.DISTANCE -> activity.distanceMeters
                PeriodBarMetric.ASCENT -> activity.ascentMeters.toDouble()
            }
            if (v > 0.0) v else null
        }
        calculateQuartiles(positiveValues)
    }

    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val selectedActivity = remember(selectedDate, contribution) {
        selectedDate?.let { contribution.dailyActivities[it] }
    }

    val tf = remember { TimeFormatter() }
    val df = remember { DistanceFormatter() }
    val af = remember { AltitudeFormatter() }

    val cellSize = 11.dp
    val cellSpacing = 2.dp
    val colPitch = 13.dp
    val density = LocalDensity.current
    val colPitchPx = with(density) { colPitch.toPx() }

    val currentLocale = LocalConfiguration.current.locales[0]

    // Month headers
    val monthOffsets = remember(year, startOffset, currentLocale) {
        (1..12).map { month ->
            val firstOfMonth = LocalDate.of(year, month, 1)
            val dayIndex = firstOfMonth.dayOfYear - 1
            val col = (dayIndex + startOffset) / 7
            val label = firstOfMonth.format(DateTimeFormatter.ofPattern("MMM", currentLocale))
            col to label
        }
    }

    val gridContent = @Composable {
        Column(modifier = if (showCard) Modifier.padding(12.dp) else Modifier) {
            // Header Row: Sport Identity + GitHub-Style Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (showSportHeader) Arrangement.SpaceBetween else Arrangement.End
            ) {
                if (showSportHeader) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = contribution.sportType.iconResId),
                            contentDescription = stringResource(id = contribution.sportType.stringResId),
                            tint = when (contribution.sportType) {
                                BSportType.BIKE -> TTColor.BikeSportBarColor
                                BSportType.RUN -> TTColor.RunSportBarColor
                                else -> TTColor.OtherSportBarColor
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(id = contribution.sportType.stringResId),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Legend: Less [0, 1, 2, 3, 4] More
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "–",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    (0..4).forEach { lvl ->
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(getSportCellColor(contribution.sportType, lvl))
                        )
                    }
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Grid Container: Day-of-week labels on the left, horizontally scrollable matrix on the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Day-of-Week Column (Mon, Wed, Fri)
                Column(
                    modifier = Modifier.padding(top = 18.dp, end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(cellSpacing)
                ) {
                    val days = listOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY,
                        DayOfWeek.SATURDAY,
                        DayOfWeek.SUNDAY
                    )
                    days.forEachIndexed { idx, day ->
                        val showLabel = shouldShowDayOfWeekLabel(idx)
                        Box(
                            modifier = Modifier
                                .height(cellSize)
                                .width(16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (showLabel) {
                                @Suppress("DEPRECATION")
                                Text(
                                    text = day.getDisplayName(TextStyle.NARROW, currentLocale),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        lineHeight = 9.sp,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically, unbounded = true)
                                )
                            }
                        }
                    }
                }

                // Scrollable 52/53-Week Grid
                val scrollState = rememberScrollState()
                var hasAutoScrolled by remember(year) { mutableStateOf(false) }

                LaunchedEffect(scrollState.maxValue, year) {
                    if (!hasAutoScrolled && scrollState.maxValue > 0 && scrollState.maxValue < Int.MAX_VALUE) {
                        val targetCol = calculateTargetColumn(
                            year = year,
                            today = LocalDate.now(),
                            startOffset = startOffset,
                            totalCols = totalCols
                        )
                        val targetOffset = calculateInitialScrollOffset(
                            maxValue = scrollState.maxValue,
                            totalCols = totalCols,
                            targetCol = targetCol,
                            colPitchPx = colPitchPx
                        )
                        scrollState.scrollTo(targetOffset)
                        hasAutoScrolled = true
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                ) {
                    Column {
                        // Month Header Row
                        Box(
                            modifier = Modifier
                                .height(16.dp)
                                .width(colPitch * totalCols)
                        ) {
                            monthOffsets.forEach { (col, label) ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.offset(x = colPitch * col)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // 7-Row Matrix
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(cellSpacing)
                        ) {
                            for (col in 0 until totalCols) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(cellSpacing)
                                ) {
                                    for (row in 0..6) {
                                        val dayOffset = col * 7 + row - startOffset
                                        if (dayOffset in 0 until totalDays) {
                                            val cellDate = jan1.plusDays(dayOffset.toLong())
                                            val activity = contribution.dailyActivities[cellDate]
                                            val metricValue = when (activeMetric) {
                                                PeriodBarMetric.DURATION -> activity?.durationSec?.toDouble() ?: 0.0
                                                PeriodBarMetric.DISTANCE -> activity?.distanceMeters ?: 0.0
                                                PeriodBarMetric.ASCENT -> activity?.ascentMeters?.toDouble() ?: 0.0
                                            }
                                            val level = calculateQuartileLevel(
                                                value = metricValue,
                                                q1 = quartiles.first,
                                                q2 = quartiles.second,
                                                q3 = quartiles.third
                                            )
                                            val isSelected = selectedDate == cellDate
                                            val cellColor = getSportCellColor(contribution.sportType, level)

                                            Box(
                                                modifier = Modifier
                                                    .size(cellSize)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(cellColor)
                                                    .then(
                                                        if (isSelected) {
                                                            Modifier.border(
                                                                width = 1.5.dp,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                shape = RoundedCornerShape(2.dp)
                                                            )
                                                        } else Modifier
                                                    )
                                                    .clickable {
                                                        selectedDate = if (selectedDate == cellDate) null else cellDate
                                                        onDayClick(cellDate, activity)
                                                    }
                                                    .semantics {
                                                        contentDescription = "${cellDate}: Level $level"
                                                    }
                                            )
                                        } else {
                                            // Empty padding cell outside the calendar year
                                            Box(
                                                modifier = Modifier
                                                    .size(cellSize)
                                                    .background(Color.Transparent)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Interactive Day Selection Detail Banner (REQ-PER-025.6)
            if (selectedDate != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDayClick(selectedDate!!, selectedActivity) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val formattedDate = remember(selectedDate, currentLocale) {
                            selectedDate!!.format(DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", currentLocale))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = formattedDate,
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

    if (showCard) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            gridContent()
        }
    } else {
        Box(modifier = modifier.fillMaxWidth()) {
            gridContent()
        }
    }
}

@Preview(showBackground = true, name = "Yearly Contribution Grid Preview - Light")
@Composable
private fun YearlyContributionGridPreviewLight() {
    ATrainingTrackerTheme(darkTheme = false) {
        val sampleActivities = (1..60).associate { day ->
            val date = LocalDate.of(2026, 1, 1).plusDays((day * 5).toLong().coerceAtMost(360))
            date to DailySportActivity(
                date = date,
                bSportType = BSportType.BIKE,
                count = 1,
                durationSec = 3600L + day * 100L,
                distanceMeters = 25000.0 + day * 500.0,
                ascentMeters = 300L + day * 10L,
                workoutIds = listOf(day.toLong()),
                workoutNames = listOf("Morning Ride $day")
            )
        }
        val contribution = YearlySportContribution(
            sportType = BSportType.BIKE,
            year = 2026,
            dailyActivities = sampleActivities,
            maxDailyDurationSec = 7200L,
            maxDailyDistanceMeters = 55000.0,
            maxDailyAscentMeters = 800L
        )

        YearlyContributionGrid(
            contribution = contribution,
            activeMetric = PeriodBarMetric.DISTANCE,
            onDayClick = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, name = "Yearly Contribution Grid Preview - Dark")
@Composable
private fun YearlyContributionGridPreviewDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        val sampleActivities = (1..40).associate { day ->
            val date = LocalDate.of(2026, 1, 1).plusDays((day * 7).toLong().coerceAtMost(360))
            date to DailySportActivity(
                date = date,
                bSportType = BSportType.RUN,
                count = 1,
                durationSec = 2400L,
                distanceMeters = 10000.0,
                ascentMeters = 100L,
                workoutIds = listOf(day.toLong()),
                workoutNames = listOf("Park Run $day")
            )
        }
        val contribution = YearlySportContribution(
            sportType = BSportType.RUN,
            year = 2026,
            dailyActivities = sampleActivities,
            maxDailyDurationSec = 3600L,
            maxDailyDistanceMeters = 15000.0,
            maxDailyAscentMeters = 150L
        )

        YearlyContributionGrid(
            contribution = contribution,
            activeMetric = PeriodBarMetric.DURATION,
            onDayClick = { _, _ -> }
        )
    }
}
