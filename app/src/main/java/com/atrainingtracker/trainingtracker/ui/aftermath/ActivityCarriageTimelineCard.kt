package com.atrainingtracker.trainingtracker.ui.aftermath

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionChartMath
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter
import kotlin.math.roundToInt

/**
 * Post-workout card displaying a proportional segmented activity and phone carriage mode
 * timeline bar, per-mode cumulative summary, and expandable chronological phase breakdown (`REQ-PER-045`).
 */
@Composable
fun ActivityCarriageTimelineCard(
    timeline: ActivityCarriageTimeline,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    if (timeline.segments.isEmpty() || timeline.totalDurationSeconds <= 0L) return

    var isBreakdownExpanded by remember(timeline) { mutableStateOf(initiallyExpanded) }
    val distanceFormatter = LocalMetricFormatter.current.distance
    val hasDistance = timeline.totalDistanceMeters > 0.0

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CardHeaderRow(phaseCount = timeline.segments.size)

            SegmentedTimelineBar(segments = timeline.segments)

            TimelineAxisRow(
                startSec = timeline.startSec,
                endSec = timeline.endSec,
                totalDurationSeconds = timeline.totalDurationSeconds,
                formattedTotalDistance = if (hasDistance) {
                    distanceFormatter.format_with_units(timeline.totalDistanceMeters)
                } else {
                    null
                }
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                timeline.modeSummaries.forEach { modeSummary ->
                    ModeSummaryRow(
                        summary = modeSummary,
                        formattedDistance = if (hasDistance) {
                            distanceFormatter.format_with_units(modeSummary.distanceMeters)
                        } else {
                            null
                        }
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            BreakdownToggleHeader(
                isExpanded = isBreakdownExpanded,
                onClick = { isBreakdownExpanded = !isBreakdownExpanded }
            )

            if (isBreakdownExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    timeline.segments.forEachIndexed { index, segment ->
                        ChronologicalSegmentRow(
                            index = index,
                            segment = segment,
                            formattedDistance = if (hasDistance) {
                                distanceFormatter.format_with_units(segment.distanceMeters)
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardHeaderRow(phaseCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Timeline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = stringResource(R.string.carriage_timeline_card_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ) {
            Text(
                text = stringResource(R.string.carriage_timeline_phase_count, phaseCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SegmentedTimelineBar(segments: List<ActivityCarriageSegment>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .testTag("carriage_timeline_bar")
    ) {
        segments.forEachIndexed { index, segment ->
            val weight = segment.durationSeconds.coerceAtLeast(1L).toFloat()
            val color = TTColor.getCarriageModeColor(segment.mode)
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .background(color)
                    .testTag("carriage_timeline_segment_$index")
            )
            if (index < segments.lastIndex) {
                Spacer(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
        }
    }
}

@Composable
private fun TimelineAxisRow(
    startSec: Long,
    endSec: Long,
    totalDurationSeconds: Long,
    formattedTotalDistance: String?
) {
    val startText = ZoneDistributionChartMath.formatZoneDuration(startSec.coerceAtLeast(0L))
    val endText = ZoneDistributionChartMath.formatZoneDuration(endSec.coerceAtLeast(0L))
    val totalDurText = ZoneDistributionChartMath.formatZoneDuration(totalDurationSeconds.coerceAtLeast(0L))
    val centerText = if (formattedTotalDistance != null) {
        "$totalDurText · $formattedTotalDistance"
    } else {
        totalDurText
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = startText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = centerText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = endText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModeSummaryRow(
    summary: ActivityCarriageModeSummary,
    formattedDistance: String?
) {
    val modeColor = TTColor.getCarriageModeColor(summary.mode)
    val durationText = ZoneDistributionChartMath.formatZoneDuration(summary.durationSeconds.coerceAtLeast(0L))
    val durationWithPct = stringResource(
        R.string.carriage_timeline_duration_percent,
        durationText,
        summary.durationPercentage.roundToInt()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(modeColor)
        )
        Icon(
            imageVector = carriageModeIcon(summary.mode),
            contentDescription = null,
            tint = modeColor,
            modifier = Modifier.size(16.dp)
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(summary.mode.labelResId),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (summary.segmentCount > 1) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = modeColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = stringResource(R.string.carriage_timeline_segment_repeats, summary.segmentCount),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = durationWithPct,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (formattedDistance != null) {
                Text(
                    text = formattedDistance,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BreakdownToggleHeader(
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag("carriage_breakdown_toggle"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.carriage_timeline_breakdown_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(
                if (isExpanded) R.string.carriage_timeline_hide_phases
                else R.string.carriage_timeline_show_phases
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ChronologicalSegmentRow(
    index: Int,
    segment: ActivityCarriageSegment,
    formattedDistance: String?
) {
    val modeColor: Color = TTColor.getCarriageModeColor(segment.mode)
    val rangeText = stringResource(
        R.string.carriage_timeline_time_range,
        ZoneDistributionChartMath.formatZoneDuration(segment.startSec.coerceAtLeast(0L)),
        ZoneDistributionChartMath.formatZoneDuration(segment.endSec.coerceAtLeast(0L))
    )
    val durationWithPct = stringResource(
        R.string.carriage_timeline_duration_percent,
        ZoneDistributionChartMath.formatZoneDuration(segment.durationSeconds.coerceAtLeast(0L)),
        segment.durationPercentage.roundToInt()
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .testTag("carriage_phase_row_$index"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(modeColor)
        )
        Icon(
            imageVector = carriageModeIcon(segment.mode),
            contentDescription = null,
            tint = modeColor,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(segment.mode.labelResId),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = rangeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = durationWithPct,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (formattedDistance != null) {
                Text(
                    text = formattedDistance,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Canonical Material icon representing a [PhoneCarriageMode] across [ActivityCarriageTimelineCard]
 * and [com.atrainingtracker.trainingtracker.ui.map.ActivityCarriageSyncBar] (`REQ-PER-045`, `REQ-UI-2796`).
 */
fun carriageModeIcon(mode: PhoneCarriageMode): ImageVector = when (mode) {
    PhoneCarriageMode.BIKE_HANDLEBAR,
    PhoneCarriageMode.BIKE_JERSEY_BACK,
    PhoneCarriageMode.BIKE_FRONT_POCKET -> Icons.AutoMirrored.Filled.DirectionsBike
    PhoneCarriageMode.RUN_WAIST_HIP,
    PhoneCarriageMode.RUN_BICEP_ARM,
    PhoneCarriageMode.RUN_HANDHELD -> Icons.AutoMirrored.Filled.DirectionsRun
    PhoneCarriageMode.WALKING_TRANSITION -> Icons.AutoMirrored.Filled.DirectionsWalk
    PhoneCarriageMode.STATIONARY -> Icons.Default.PauseCircle
    PhoneCarriageMode.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
}

@Preview(showBackground = true, name = "Light - Expanded Bakery Tour")
@Composable
private fun ActivityCarriageTimelineCardLightPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ActivityCarriageTimelineCard(
                timeline = sampleBakeryTourTimeline(),
                initiallyExpanded = true,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(showBackground = true, name = "Dark - Compact Bakery Tour")
@Composable
private fun ActivityCarriageTimelineCardDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ActivityCarriageTimelineCard(
                timeline = sampleBakeryTourTimeline(),
                initiallyExpanded = false,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(showBackground = true, name = "AMOLED - Expanded Bakery Tour")
@Composable
private fun ActivityCarriageTimelineCardAmoledPreview() {
    ATrainingTrackerTheme(darkTheme = true, amoled = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ActivityCarriageTimelineCard(
                timeline = sampleBakeryTourTimeline(),
                initiallyExpanded = true,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

private fun sampleBakeryTourTimeline(): ActivityCarriageTimeline =
    ActivityCarriageTimeline(
        segments = listOf(
            ActivityCarriageSegment(
                mode = PhoneCarriageMode.BIKE_HANDLEBAR,
                startSec = 0L,
                endSec = 900L,
                durationSeconds = 900L,
                distanceMeters = 5200.0,
                durationPercentage = 60.0f,
                distancePercentage = 54.7f
            ),
            ActivityCarriageSegment(
                mode = PhoneCarriageMode.WALKING_TRANSITION,
                startSec = 900L,
                endSec = 1020L,
                durationSeconds = 120L,
                distanceMeters = 150.0,
                durationPercentage = 8.0f,
                distancePercentage = 1.6f
            ),
            ActivityCarriageSegment(
                mode = PhoneCarriageMode.STATIONARY,
                startSec = 1020L,
                endSec = 1140L,
                durationSeconds = 120L,
                distanceMeters = 0.0,
                durationPercentage = 8.0f,
                distancePercentage = 0.0f
            ),
            ActivityCarriageSegment(
                mode = PhoneCarriageMode.BIKE_JERSEY_BACK,
                startSec = 1140L,
                endSec = 1500L,
                durationSeconds = 360L,
                distanceMeters = 4150.0,
                durationPercentage = 24.0f,
                distancePercentage = 43.7f
            )
        ),
        modeSummaries = listOf(
            ActivityCarriageModeSummary(
                mode = PhoneCarriageMode.BIKE_HANDLEBAR,
                durationSeconds = 900L,
                distanceMeters = 5200.0,
                durationPercentage = 60.0f,
                distancePercentage = 54.7f,
                segmentCount = 1
            ),
            ActivityCarriageModeSummary(
                mode = PhoneCarriageMode.BIKE_JERSEY_BACK,
                durationSeconds = 360L,
                distanceMeters = 4150.0,
                durationPercentage = 24.0f,
                distancePercentage = 43.7f,
                segmentCount = 1
            ),
            ActivityCarriageModeSummary(
                mode = PhoneCarriageMode.WALKING_TRANSITION,
                durationSeconds = 120L,
                distanceMeters = 150.0,
                durationPercentage = 8.0f,
                distancePercentage = 1.6f,
                segmentCount = 1
            ),
            ActivityCarriageModeSummary(
                mode = PhoneCarriageMode.STATIONARY,
                durationSeconds = 120L,
                distanceMeters = 0.0,
                durationPercentage = 8.0f,
                distancePercentage = 0.0f,
                segmentCount = 1
            )
        ),
        totalDurationSeconds = 1500L,
        totalDistanceMeters = 9500.0,
        startSec = 0L,
        endSec = 1500L
    )
