package com.atrainingtracker.trainingtracker.ui.aftermath

import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM verification suite for [ActivityCarriageTimelineCalculator] (`TST-PER-053.1`).
 */
class ActivityCarriageTimelineCalculatorTest {

    @Test
    fun coalesce_emptyOrOnlyUnknownSamples_returnsNull() {
        assertNull(ActivityCarriageTimelineCalculator.coalesce(emptyList()))
        assertNull(
            ActivityCarriageTimelineCalculator.coalesce(
                listOf(ActivityCarriageSample(0L, 0.0, null))
            )
        )
        val longUnknown = (0L..19L).map { sec ->
            ActivityCarriageSample(timeSec = sec, distanceMeters = sec * 2.0, rawCode = -1)
        }
        assertNull(ActivityCarriageTimelineCalculator.coalesce(longUnknown))
    }

    @Test
    fun coalesce_bakeryTourMultiPhase_preservesExactChronologicalOrderAndPercentages() {
        val samples = buildList {
            // 0..9s BIKE_HANDLEBAR (10s, ~50m)
            for (t in 0L..9L) {
                add(ActivityCarriageSample(t, t * 5.0, PhoneCarriageMode.BIKE_HANDLEBAR.code))
            }
            // 10..13s WALKING_TRANSITION (4s, ~5m)
            for (t in 10L..13L) {
                add(ActivityCarriageSample(t, 50.0 + (t - 10L) * 1.25, PhoneCarriageMode.WALKING_TRANSITION.code))
            }
            // 14..17s STATIONARY (4s, 0m)
            for (t in 14L..17L) {
                add(ActivityCarriageSample(t, 55.0, PhoneCarriageMode.STATIONARY.code))
            }
            // 18..29s BIKE_JERSEY_BACK (12s, ~60m)
            for (t in 18L..29L) {
                add(ActivityCarriageSample(t, 55.0 + (t - 18L) * 5.0, PhoneCarriageMode.BIKE_JERSEY_BACK.code))
            }
        }

        val timeline = ActivityCarriageTimelineCalculator.coalesce(samples)
        assertNotNull(timeline)
        timeline!!

        assertEquals(30L, timeline.totalDurationSeconds)
        assertEquals(0L, timeline.startSec)
        assertEquals(30L, timeline.endSec)
        assertEquals(110.0, timeline.totalDistanceMeters, 1e-6)
        assertEquals(4, timeline.segments.size)

        assertEquals(PhoneCarriageMode.BIKE_HANDLEBAR, timeline.segments[0].mode)
        assertEquals(10L, timeline.segments[0].durationSeconds)
        assertEquals(0L, timeline.segments[0].startSec)
        assertEquals(10L, timeline.segments[0].endSec)

        assertEquals(PhoneCarriageMode.WALKING_TRANSITION, timeline.segments[1].mode)
        assertEquals(4L, timeline.segments[1].durationSeconds)
        assertEquals(10L, timeline.segments[1].startSec)
        assertEquals(14L, timeline.segments[1].endSec)

        assertEquals(PhoneCarriageMode.STATIONARY, timeline.segments[2].mode)
        assertEquals(4L, timeline.segments[2].durationSeconds)
        assertEquals(0.0, timeline.segments[2].distanceMeters, 1e-6)

        assertEquals(PhoneCarriageMode.BIKE_JERSEY_BACK, timeline.segments[3].mode)
        assertEquals(12L, timeline.segments[3].durationSeconds)

        // Percentages sum cleanly to 100%
        assertEquals(100.0, timeline.segments.sumOf { it.durationPercentage.toDouble() }, 0.05)
        assertEquals(100.0, timeline.segments.sumOf { it.distancePercentage.toDouble() }, 0.05)

        // Mode summaries sorted descending by duration
        assertEquals(PhoneCarriageMode.BIKE_JERSEY_BACK, timeline.modeSummaries.first().mode)
        assertEquals(PhoneCarriageMode.BIKE_HANDLEBAR, timeline.modeSummaries[1].mode)
    }

    @Test
    fun coalesce_unknownSliversUpTo5sAbsorbed_andEqualAdjacentModesMerged() {
        val samples = buildList {
            // 0..2: leading 3s UNKNOWN warmup -> absorbed into following HANDLEBAR
            for (t in 0L..2L) add(ActivityCarriageSample(t, t * 2.0, PhoneCarriageMode.UNKNOWN.code))
            // 3..12: 10s HANDLEBAR
            for (t in 3L..12L) add(ActivityCarriageSample(t, 6.0 + (t - 3L) * 4.0, PhoneCarriageMode.BIKE_HANDLEBAR.code))
            // 13..17: boundary 5s UNKNOWN sliver -> absorbed into preceding HANDLEBAR
            for (t in 13L..17L) add(ActivityCarriageSample(t, 46.0, null))
            // 18..25: 8s HANDLEBAR -> merges with preceding HANDLEBAR!
            for (t in 18L..25L) add(ActivityCarriageSample(t, 46.0 + (t - 18L) * 4.0, PhoneCarriageMode.BIKE_HANDLEBAR.code))
            // 26..29: 4s RUN_WAIST_HIP
            for (t in 26L..29L) add(ActivityCarriageSample(t, 78.0 + (t - 26L) * 3.0, PhoneCarriageMode.RUN_WAIST_HIP.code))
            // 30..31: trailing 2s UNKNOWN sliver -> absorbed into preceding RUN_WAIST_HIP
            for (t in 30L..31L) add(ActivityCarriageSample(t, 87.0, -1))
        }

        val timeline = ActivityCarriageTimelineCalculator.coalesce(samples)!!
        assertEquals(2, timeline.segments.size)
        assertEquals(PhoneCarriageMode.BIKE_HANDLEBAR, timeline.segments[0].mode)
        assertEquals(26L, timeline.segments[0].durationSeconds)
        assertEquals(PhoneCarriageMode.RUN_WAIST_HIP, timeline.segments[1].mode)
        assertEquals(6L, timeline.segments[1].durationSeconds)
        assertEquals(32L, timeline.totalDurationSeconds)
    }

    @Test
    fun coalesce_genuineShortActiveModes1To5s_neverDebounced() {
        val samples = listOf(
            ActivityCarriageSample(0L, 0.0, PhoneCarriageMode.BIKE_HANDLEBAR.code),
            ActivityCarriageSample(1L, 5.0, PhoneCarriageMode.WALKING_TRANSITION.code),
            ActivityCarriageSample(2L, 6.0, PhoneCarriageMode.STATIONARY.code),
            ActivityCarriageSample(3L, 6.0, PhoneCarriageMode.STATIONARY.code),
            ActivityCarriageSample(4L, 6.0, PhoneCarriageMode.RUN_HANDHELD.code),
            ActivityCarriageSample(5L, 9.0, PhoneCarriageMode.RUN_HANDHELD.code),
            ActivityCarriageSample(6L, 12.0, PhoneCarriageMode.RUN_HANDHELD.code)
        )

        val timeline = ActivityCarriageTimelineCalculator.coalesce(samples)!!
        assertEquals(4, timeline.segments.size)
        assertEquals(PhoneCarriageMode.BIKE_HANDLEBAR, timeline.segments[0].mode)
        assertEquals(1L, timeline.segments[0].durationSeconds)
        assertEquals(PhoneCarriageMode.WALKING_TRANSITION, timeline.segments[1].mode)
        assertEquals(1L, timeline.segments[1].durationSeconds)
        assertEquals(PhoneCarriageMode.STATIONARY, timeline.segments[2].mode)
        assertEquals(2L, timeline.segments[2].durationSeconds)
        assertEquals(PhoneCarriageMode.RUN_HANDHELD, timeline.segments[3].mode)
        assertEquals(3L, timeline.segments[3].durationSeconds)
    }

    @Test
    fun coalesce_unknownGapStrictlyOver5sRetained_andCountedInSummaryDenominators() {
        val samples = buildList {
            for (t in 0L..9L) add(ActivityCarriageSample(t, t * 5.0, PhoneCarriageMode.BIKE_HANDLEBAR.code))
            // 6s UNKNOWN gap (> 5s threshold -> must be kept!)
            for (t in 10L..15L) add(ActivityCarriageSample(t, 50.0, PhoneCarriageMode.UNKNOWN.code))
            for (t in 16L..19L) add(ActivityCarriageSample(t, 50.0 + (t - 16L) * 5.0, PhoneCarriageMode.BIKE_HANDLEBAR.code))
        }

        val timeline = ActivityCarriageTimelineCalculator.coalesce(samples)!!
        assertEquals(3, timeline.segments.size)
        assertEquals(PhoneCarriageMode.UNKNOWN, timeline.segments[1].mode)
        assertEquals(6L, timeline.segments[1].durationSeconds)
        assertEquals(20L, timeline.totalDurationSeconds)

        val bikeSummary = timeline.modeSummaries.first { it.mode == PhoneCarriageMode.BIKE_HANDLEBAR }
        val unknownSummary = timeline.modeSummaries.first { it.mode == PhoneCarriageMode.UNKNOWN }
        assertEquals(14L, bikeSummary.durationSeconds)
        assertEquals(2, bikeSummary.segmentCount)
        assertEquals(70.0f, bikeSummary.durationPercentage, 0.05f)
        assertEquals(6L, unknownSummary.durationSeconds)
        assertEquals(30.0f, unknownSummary.durationPercentage, 0.05f)
        assertEquals(100.0, timeline.modeSummaries.sumOf { it.durationPercentage.toDouble() }, 0.05)
    }

    @Test
    fun coalesce_clampsPauseDeltaT_andGuardsNegativeOrNaNDistance() {
        val samples = listOf(
            ActivityCarriageSample(0L, 100.0, PhoneCarriageMode.RUN_WAIST_HIP.code),
            // 300s pause gap -> dt clamped to MAX_SAMPLE_DT_SECONDS (5L); distance drop clamped to 0.0
            ActivityCarriageSample(300L, 20.0, PhoneCarriageMode.RUN_WAIST_HIP.code),
            ActivityCarriageSample(301L, Double.NaN, PhoneCarriageMode.RUN_BICEP_ARM.code),
            ActivityCarriageSample(302L, 35.0, PhoneCarriageMode.RUN_BICEP_ARM.code)
        )

        val timeline = ActivityCarriageTimelineCalculator.coalesce(samples)!!
        assertEquals(6L, timeline.segments[0].durationSeconds) // 5L + 1L
        assertEquals(0.0, timeline.segments[0].distanceMeters, 1e-6)
        assertEquals(2L, timeline.segments[1].durationSeconds) // 1L + 1L
        assertEquals(0.0, timeline.segments[1].distanceMeters, 1e-6)
    }

    @Test
    fun coalesce_zeroDistanceIndoorWorkout_yieldsZeroDistancePercentagesAndPositiveDurationWeights() {
        val samples = listOf(
            ActivityCarriageSample(0L, 0.0, PhoneCarriageMode.BIKE_HANDLEBAR.code),
            ActivityCarriageSample(1L, 0.0, PhoneCarriageMode.BIKE_HANDLEBAR.code),
            ActivityCarriageSample(2L, 0.0, PhoneCarriageMode.STATIONARY.code),
            ActivityCarriageSample(3L, 0.0, PhoneCarriageMode.WALKING_TRANSITION.code)
        )

        val timeline = ActivityCarriageTimelineCalculator.coalesce(samples)!!
        assertEquals(0.0, timeline.totalDistanceMeters, 1e-6)
        assertEquals(4L, timeline.totalDurationSeconds)
        timeline.segments.forEach { seg ->
            assertEquals(0f, seg.distancePercentage, 1e-6f)
            assertTrue(seg.durationPercentage > 0f)
        }
        timeline.modeSummaries.forEach { summary ->
            assertEquals(0f, summary.distancePercentage, 1e-6f)
            assertTrue(summary.durationPercentage > 0f)
        }
    }

    @Test
    fun accumulator_streamingSingleAndMultiSampleMatchesCoalesce() {
        val accSingle = ActivityCarriageTimelineCalculator.Accumulator()
        accSingle.record(5L, 0.0, PhoneCarriageMode.STATIONARY.code)
        val singleTimeline = accSingle.build()!!
        assertEquals(1, singleTimeline.segments.size)
        assertEquals(1L, singleTimeline.totalDurationSeconds)
        assertEquals(5L, singleTimeline.startSec)
        assertEquals(6L, singleTimeline.endSec)
    }
}
