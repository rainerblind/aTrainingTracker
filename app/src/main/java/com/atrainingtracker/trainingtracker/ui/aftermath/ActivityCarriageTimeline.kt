package com.atrainingtracker.trainingtracker.ui.aftermath

import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode

/**
 * Raw 1-Hz telemetry sample feeding [ActivityCarriageTimelineCalculator].
 *
 * @property timeSec Elapsed workout active/total timestamp in seconds.
 * @property distanceMeters Cumulative workout distance in meters at [timeSec].
 * @property rawCode Raw integer value read from `ACTIVITY_AND_CARRIAGE_MODE`, or `null` if DB cell was NULL.
 */
data class ActivityCarriageSample(
    val timeSec: Long,
    val distanceMeters: Double,
    val rawCode: Int?
)

/**
 * Chronological coalesced phase span within a workout's activity and carriage-mode timeline.
 *
 * @property mode Resolved [PhoneCarriageMode] for this span.
 * @property startSec Segment start offset in seconds along the active timeline.
 * @property endSec Segment end offset in seconds along the active timeline (`startSec + durationSeconds`).
 * @property durationSeconds Active duration spent in this phase (`>= 1L`).
 * @property distanceMeters Forward distance covered during this phase in meters (`>= 0.0`).
 * @property durationPercentage Share of [ActivityCarriageTimeline.totalDurationSeconds] in `0f..100f`.
 * @property distancePercentage Share of [ActivityCarriageTimeline.totalDistanceMeters] in `0f..100f` (`0f` when total distance is `0.0`).
 */
data class ActivityCarriageSegment(
    val mode: PhoneCarriageMode,
    val startSec: Long,
    val endSec: Long,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val durationPercentage: Float,
    val distancePercentage: Float
)

/**
 * Aggregated per-mode totals across all chronological segments of a workout.
 *
 * @property mode Resolved [PhoneCarriageMode].
 * @property durationSeconds Cumulative active seconds spent in [mode].
 * @property distanceMeters Cumulative forward distance in meters spent in [mode].
 * @property durationPercentage Share of [ActivityCarriageTimeline.totalDurationSeconds] in `0f..100f`.
 * @property distancePercentage Share of [ActivityCarriageTimeline.totalDistanceMeters] in `0f..100f` (`0f` when total distance is `0.0`).
 * @property segmentCount Number of discrete chronological visits to [mode].
 */
data class ActivityCarriageModeSummary(
    val mode: PhoneCarriageMode,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val durationPercentage: Float,
    val distancePercentage: Float,
    val segmentCount: Int
)

/**
 * Complete coalesced activity and phone-carriage timeline for a single workout (`REQ-PER-045`).
 *
 * @property segments Chronological non-overlapping [ActivityCarriageSegment] sequence.
 * @property modeSummaries Per-mode breakdowns sorted by descending duration.
 * @property totalDurationSeconds Sum of retained segment durations (`>= 1L`).
 * @property totalDistanceMeters Sum of retained segment distances (`>= 0.0`).
 * @property startSec Start timestamp of the timeline in seconds.
 * @property endSec End timestamp of the timeline in seconds (`startSec + totalDurationSeconds`).
 */
data class ActivityCarriageTimeline(
    val segments: List<ActivityCarriageSegment>,
    val modeSummaries: List<ActivityCarriageModeSummary>,
    val totalDurationSeconds: Long,
    val totalDistanceMeters: Double,
    val startSec: Long,
    val endSec: Long
)
