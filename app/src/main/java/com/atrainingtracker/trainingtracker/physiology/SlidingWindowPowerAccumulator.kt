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

package com.atrainingtracker.trainingtracker.physiology

import kotlin.math.max

/**
 * High-performance O(1) circular ring-buffer sliding window accumulator for real-time
 * Mean Maximal Power (MMP) duration metrics and session peak tracking (REQ-TRK-058.1, REQ-TRK-058.2).
 *
 * <p>Growing warm-up window (Rule 40 / REQ-TRK-023):
 * During the initial warm-up phase (1 <= k < windowSeconds), [getAverage] computes over the
 * currently available samples (runningSum / count) rather than returning null or delaying emission.
 * Session peak power monotonically tracks the maximum rolling average observed so far.</p>
 *
 * @property windowSeconds Window capacity in seconds (must be > 0).
 */
class SlidingWindowPowerAccumulator(val windowSeconds: Int) {

    init {
        require(windowSeconds > 0) { "windowSeconds must be > 0, was $windowSeconds" }
    }

    private val buffer = DoubleArray(windowSeconds)
    private var head = 0
    private var internalCount = 0
    private var runningSum = 0.0
    private var peakAverage: Double? = null

    /**
     * Number of samples currently held in the sliding window.
     */
    val count: Int
        @Synchronized get() = internalCount

    /**
     * Ingests a new 1-Hz power sample in O(1) time complexity.
     * Updates the circular buffer, running sum, and session peak average.
     *
     * @param power Instantaneous power in Watts.
     */
    @Synchronized
    fun addSample(power: Double) {
        if (internalCount < windowSeconds) {
            buffer[head] = power
            runningSum += power
            internalCount++
            head = (head + 1) % windowSeconds
        } else {
            val oldest = buffer[head]
            buffer[head] = power
            runningSum += (power - oldest)
            if (runningSum < 0.0) {
                runningSum = 0.0
            }
            head = (head + 1) % windowSeconds
        }

        val currentAvg = runningSum / internalCount
        peakAverage = if (peakAverage == null) {
            currentAvg
        } else {
            max(peakAverage!!, currentAvg)
        }
    }

    /**
     * Returns the current rolling mean power over the window, or over the growing window
     * during the warm-up phase (Rule 40 / REQ-TRK-058.1).
     *
     * @return Rolling mean power in Watts, or null if no samples have been added.
     */
    @Synchronized
    fun getAverage(): Double? {
        if (internalCount == 0) return null
        return runningSum / internalCount
    }

    /**
     * Returns the highest sustained average power achieved so far during the session
     * for this duration window (REQ-TRK-058.2).
     *
     * @return Session peak power in Watts, or null if no samples have been added.
     */
    @Synchronized
    fun getPeak(): Double? {
        return peakAverage
    }

    /**
     * Returns true when the buffer is completely filled (count == windowSeconds).
     */
    @Synchronized
    fun isFull(): Boolean {
        return internalCount == windowSeconds
    }

    /**
     * Clears all samples, resets running sum and session peak registers (Rule 51 / REQ-TRK-058.5).
     */
    @Synchronized
    fun reset() {
        buffer.fill(0.0)
        head = 0
        internalCount = 0
        runningSum = 0.0
        peakAverage = null
    }
}
