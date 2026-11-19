package com.atrainingtracker.trainingtracker.ui.aftermath

import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode

/**
 * Pure-JVM 1-Hz coalescer turning pre-decimation `ACTIVITY_AND_CARRIAGE_MODE` cursor rows
 * into an [ActivityCarriageTimeline] (`REQ-PER-045.1`, `REQ-PER-045.2`).
 *
 * Design invariants:
 * - Sample delta time is clamped to `1..5` seconds so workout pauses do not inflate phase durations.
 * - Forward distance deltas are non-negative (`(d_{i+1} - d_i).coerceAtLeast(0.0)` when both finite).
 * - Short transient/warmup `UNKNOWN(-1)` spans `<= 5` seconds are absorbed into the preceding active
 *   span (or following active span when leading) and resulting equal adjacent active spans merge.
 * - Genuine `1..5` second active transitions (`0..7`) are preserved verbatim without debouncing.
 * - `UNKNOWN(-1)` spans strictly longer than `5` seconds are retained as explicit unknown gaps and
 *   included in percentage denominators.
 * - Returns `null` when zero active (`0..7`) samples exist.
 */
object ActivityCarriageTimelineCalculator {

    const val MIN_SAMPLE_DT_SECONDS: Long = 1L
    const val MAX_SAMPLE_DT_SECONDS: Long = 5L
    const val UNKNOWN_SLIVER_MAX_SECONDS: Long = 5L

    private data class MutableSpan(
        val mode: PhoneCarriageMode,
        var durationSeconds: Long,
        var distanceMeters: Double
    )

    /**
     * Batch helper coalescing ordered [ActivityCarriageSample] instances via [Accumulator].
     */
    fun coalesce(samples: List<ActivityCarriageSample>): ActivityCarriageTimeline? {
        if (samples.isEmpty()) return null
        val accumulator = Accumulator()
        for (sample in samples) {
            accumulator.record(
                timeSec = sample.timeSec,
                distanceMeters = sample.distanceMeters,
                rawCode = sample.rawCode
            )
        }
        return accumulator.build()
    }

    /**
     * Streaming O(segments) cursor accumulator suitable for direct row-by-row iteration inside
     * [WorkoutRepository.loadWorkoutDetailTelemetry] before track decimation.
     */
    class Accumulator {
        private var hasPrevRow = false
        private var firstTimeSec = 0L
        private var prevTimeSec = 0L
        private var prevDistanceMeters = 0.0
        private var prevMode = PhoneCarriageMode.UNKNOWN
        private var hasActiveMode = false

        private val rawSpans = ArrayList<MutableSpan>()

        /**
         * Appends one chronological 1-Hz telemetry sample.
         */
        fun record(timeSec: Long, distanceMeters: Double, rawCode: Int?) {
            val mode = PhoneCarriageMode.fromCode(rawCode)
            if (mode != PhoneCarriageMode.UNKNOWN) {
                hasActiveMode = true
            }

            if (!hasPrevRow) {
                hasPrevRow = true
                firstTimeSec = timeSec.coerceAtLeast(0L)
                prevTimeSec = timeSec
                prevDistanceMeters = distanceMeters
                prevMode = mode
                return
            }

            val dt = (timeSec - prevTimeSec).coerceIn(MIN_SAMPLE_DT_SECONDS, MAX_SAMPLE_DT_SECONDS)
            val deltaDist = if (prevDistanceMeters.isFinite() && distanceMeters.isFinite()) {
                (distanceMeters - prevDistanceMeters).coerceAtLeast(0.0)
            } else {
                0.0
            }

            appendRawSlice(prevMode, dt, deltaDist)

            prevTimeSec = timeSec
            prevDistanceMeters = distanceMeters
            prevMode = mode
        }

        /**
         * Finalizes the trailing row and materializes the coalesced [ActivityCarriageTimeline],
         * or returns `null` when no active (`0..7`) carriage modes were observed.
         */
        fun build(): ActivityCarriageTimeline? {
            if (!hasPrevRow || !hasActiveMode) return null

            // Flush final sample with unit 1s slice and 0m lookahead distance delta.
            appendRawSlice(prevMode, MIN_SAMPLE_DT_SECONDS, 0.0)

            val coalesced = coalesceUnknownSlivers(rawSpans)
            if (coalesced.isEmpty()) return null

            val totalDurationSeconds = coalesced.sumOf { it.durationSeconds }
            if (totalDurationSeconds <= 0L) return null

            val totalDistanceMeters = coalesced.sumOf { it.distanceMeters }.coerceAtLeast(0.0)

            var cursorSec = firstTimeSec
            val timelineStartSec = cursorSec
            val segments = ArrayList<ActivityCarriageSegment>(coalesced.size)
            for (span in coalesced) {
                val startSec = cursorSec
                val endSec = startSec + span.durationSeconds
                cursorSec = endSec
                segments.add(
                    ActivityCarriageSegment(
                        mode = span.mode,
                        startSec = startSec,
                        endSec = endSec,
                        durationSeconds = span.durationSeconds,
                        distanceMeters = span.distanceMeters,
                        durationPercentage = toPercentage(span.durationSeconds.toDouble(), totalDurationSeconds.toDouble()),
                        distancePercentage = toPercentage(span.distanceMeters, totalDistanceMeters)
                    )
                )
            }

            val modeSummaries = buildModeSummaries(coalesced, totalDurationSeconds, totalDistanceMeters)

            return ActivityCarriageTimeline(
                segments = segments,
                modeSummaries = modeSummaries,
                totalDurationSeconds = totalDurationSeconds,
                totalDistanceMeters = totalDistanceMeters,
                startSec = timelineStartSec,
                endSec = cursorSec
            )
        }

        private fun appendRawSlice(mode: PhoneCarriageMode, durationSeconds: Long, distanceMeters: Double) {
            val last = rawSpans.lastOrNull()
            if (last != null && last.mode == mode) {
                last.durationSeconds += durationSeconds
                last.distanceMeters += distanceMeters
            } else {
                rawSpans.add(MutableSpan(mode, durationSeconds, distanceMeters))
            }
        }
    }

    private fun coalesceUnknownSlivers(rawSpans: List<MutableSpan>): List<MutableSpan> {
        val result = ArrayList<MutableSpan>(rawSpans.size)
        var pendingLeadingUnknown: MutableSpan? = null

        for (span in rawSpans) {
            val isUnknownSliver = span.mode == PhoneCarriageMode.UNKNOWN &&
                span.durationSeconds <= UNKNOWN_SLIVER_MAX_SECONDS

            if (isUnknownSliver) {
                val prev = result.lastOrNull()
                if (prev != null) {
                    prev.durationSeconds += span.durationSeconds
                    prev.distanceMeters += span.distanceMeters
                } else {
                    pendingLeadingUnknown = span.copy()
                }
                continue
            }

            var current = span.copy()
            val leading = pendingLeadingUnknown
            if (leading != null) {
                current.durationSeconds += leading.durationSeconds
                current.distanceMeters += leading.distanceMeters
                pendingLeadingUnknown = null
            }

            val prev = result.lastOrNull()
            if (prev != null && prev.mode == current.mode) {
                prev.durationSeconds += current.durationSeconds
                prev.distanceMeters += current.distanceMeters
            } else {
                result.add(current)
            }
        }

        return result
    }

    private fun buildModeSummaries(
        coalesced: List<MutableSpan>,
        totalDurationSeconds: Long,
        totalDistanceMeters: Double
    ): List<ActivityCarriageModeSummary> {
        val grouped = LinkedHashMap<PhoneCarriageMode, ArrayList<MutableSpan>>()
        for (span in coalesced) {
            grouped.getOrPut(span.mode) { ArrayList() }.add(span)
        }

        return grouped.entries.map { (mode, spans) ->
            val duration = spans.sumOf { it.durationSeconds }
            val distance = spans.sumOf { it.distanceMeters }
            ActivityCarriageModeSummary(
                mode = mode,
                durationSeconds = duration,
                distanceMeters = distance,
                durationPercentage = toPercentage(duration.toDouble(), totalDurationSeconds.toDouble()),
                distancePercentage = toPercentage(distance, totalDistanceMeters),
                segmentCount = spans.size
            )
        }.sortedWith(
            compareByDescending<ActivityCarriageModeSummary> { it.durationSeconds }
                .thenByDescending { it.distanceMeters }
                .thenBy { it.mode.code }
        )
    }

    private fun toPercentage(part: Double, total: Double): Float =
        if (total > 0.0 && part.isFinite()) {
            ((part / total) * 100.0).toFloat()
        } else {
            0f
        }
}
