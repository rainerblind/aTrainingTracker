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

package com.atrainingtracker.trainingtracker.ui.tracking

/**
 * Lightweight, memory-bounded circular and decimation time-series buffer for cockpit live sparkline graphs
 * (REQ-TRK-059.4, ATT-1294).
 *
 * <p>For fixed horizons ([horizonSeconds] &gt; 0), maintains a sliding window of at most [horizonSeconds] samples at 1Hz.
 * For Full Workout mode ([horizonSeconds] &lt;= 0), dynamically applies pairwise decimation when sample count
 * exceeds [MAX_FULL_WORKOUT_SAMPLES], maintaining bounded memory consumption (&lt;= 1 KB per tile) across
 * multi-hour workouts with O(1) amortized sample insertions.</p>
 */
class LiveSparklineBuffer(val horizonSeconds: Int = 300) {

    companion object {
        const val MAX_FULL_WORKOUT_SAMPLES: Int = 300
    }

    private val lock = Any()
    private val fixedBuffer: ArrayDeque<Float> = if (horizonSeconds > 0) ArrayDeque(horizonSeconds) else ArrayDeque()
    private val fullWorkoutBuffer: ArrayList<Float> = ArrayList(if (horizonSeconds <= 0) MAX_FULL_WORKOUT_SAMPLES + 2 else 0)
    private var totalElapsedSeconds: Int = 0

    /**
     * Appends a new numeric telemetry sample to the buffer.
     *
     * @param value Telemetry reading at the current 1-Hz tick.
     */
    fun addSample(value: Float) {
        synchronized(lock) {
            totalElapsedSeconds++
            if (horizonSeconds > 0) {
                if (fixedBuffer.size >= horizonSeconds) {
                    fixedBuffer.removeFirst()
                }
                fixedBuffer.addLast(value)
            } else {
                fullWorkoutBuffer.add(value)
                if (fullWorkoutBuffer.size > MAX_FULL_WORKOUT_SAMPLES) {
                    decimateFullWorkout()
                }
            }
        }
    }

    /**
     * Pairwise averages adjacent samples to halve buffer size without losing the temporal trend profile.
     */
    private fun decimateFullWorkout() {
        val decimated = ArrayList<Float>((fullWorkoutBuffer.size + 1) / 2)
        var i = 0
        while (i < fullWorkoutBuffer.size - 1) {
            decimated.add((fullWorkoutBuffer[i] + fullWorkoutBuffer[i + 1]) / 2.0f)
            i += 2
        }
        if (i < fullWorkoutBuffer.size) {
            decimated.add(fullWorkoutBuffer[i])
        }
        fullWorkoutBuffer.clear()
        fullWorkoutBuffer.addAll(decimated)
    }

    /**
     * Returns an immutable snapshot list of samples in chronological order.
     */
    fun getSamples(): List<Float> {
        synchronized(lock) {
            return if (horizonSeconds > 0) {
                fixedBuffer.toList()
            } else {
                ArrayList(fullWorkoutBuffer)
            }
        }
    }

    /**
     * Current number of samples retained in the buffer.
     */
    val size: Int
        get() = synchronized(lock) {
            if (horizonSeconds > 0) fixedBuffer.size else fullWorkoutBuffer.size
        }

    /**
     * Elapsed temporal span represented by the buffer in seconds (REQ-TRK-059.9, ATT-6695).
     *
     * <p>For fixed horizons, caps at [horizonSeconds] once warmed. For Full Workout mode,
     * preserves cumulative elapsed duration across repeated pairwise decimations.</p>
     */
    val currentSpanSeconds: Int
        get() = synchronized(lock) {
            if (horizonSeconds > 0) {
                minOf(maxOf(0, totalElapsedSeconds - 1), horizonSeconds)
            } else {
                maxOf(0, totalElapsedSeconds - 1)
            }
        }

    /**
     * Clears all samples in the buffer.
     */
    fun clear() {
        synchronized(lock) {
            fixedBuffer.clear()
            fullWorkoutBuffer.clear()
            totalElapsedSeconds = 0
        }
    }
}
