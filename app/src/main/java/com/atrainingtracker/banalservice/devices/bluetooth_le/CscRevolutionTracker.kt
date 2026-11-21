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

package com.atrainingtracker.banalservice.devices.bluetooth_le

/**
 * Pure-JVM stateful tracker for BLE Cycling Speed and Cadence (0x2A5B) and Cycling Power (0x2A63)
 * wheel and crank revolution notifications.
 *
 * Encapsulates unsigned counter deltas (`UINT32` wheel revolutions, `UINT16` crank revolutions),
 * unsigned `UINT16` event-time rollover arithmetic `(eventTime - lastEventTime) and 0xFFFFL`,
 * identical-timestamp coasting zeroing after [maxIdenticalTicks] packets, and crank cadence
 * plausibility filtering (`0.0..250.0` rpm).
 */
class CscRevolutionTracker @JvmOverloads constructor(
    private val wheelTimeResolution: Int,
    private val crankTimeResolution: Int = DEFAULT_CRANK_TIME_RESOLUTION,
    private val maxIdenticalTicks: Int = DEFAULT_MAX_IDENTICAL_TICKS
) {

    init {
        require(wheelTimeResolution > 0) { "wheelTimeResolution must be positive: $wheelTimeResolution" }
        require(crankTimeResolution > 0) { "crankTimeResolution must be positive: $crankTimeResolution" }
        require(maxIdenticalTicks > 0) { "maxIdenticalTicks must be positive: $maxIdenticalTicks" }
    }

    enum class WheelStatus {
        INITIAL_BASELINE,
        COMPUTED,
        IDENTICAL_TICK_ZEROED,
        NO_UPDATE
    }

    data class WheelUpdate(
        val status: WheelStatus,
        val speedMps: Double = 0.0,
        val paceSpm: Double? = null,
        val distanceMeters: Double = 0.0,
        val revDiff: Long = 0L
    )

    enum class CrankStatus {
        INITIAL_BASELINE,
        COMPUTED,
        IDENTICAL_TICK_ZEROED,
        IGNORED_OUT_OF_RANGE,
        NO_UPDATE
    }

    data class CrankUpdate(
        val status: CrankStatus,
        val cadenceRpm: Double = 0.0,
        val revDiff: Long = 0L,
        val timeDiff: Long = 0L
    )

    private var lastWheelRevolutionsValid = false
    private var lastCrankRevolutionsValid = false
    private var initWheelRevolutions = 0L
    private var lastWheelRevolutions = 0L
    private var lastWheelEventTime = 0L
    private var lastCrankRevolutions = 0L
    private var lastCrankEventTime = 0L
    private var identicalWheelTime = 0
    private var identicalCrankTime = 0

    /**
     * Evaluates a wheel revolution notification sample and updates baseline state unconditionally.
     */
    fun processWheel(
        cumulativeWheelRevolutions: Long,
        wheelEventTime: Long,
        calibrationFactor: Double
    ): WheelUpdate {
        val maskedRevs = cumulativeWheelRevolutions and UINT32_MASK
        val maskedTime = wheelEventTime and UINT16_MASK

        val result = if (!lastWheelRevolutionsValid) {
            initWheelRevolutions = maskedRevs
            identicalWheelTime = 0
            WheelUpdate(status = WheelStatus.INITIAL_BASELINE)
        } else {
            // UINT16 modular subtraction preserves the sample across 32s (2048 Hz) / 64s (1024 Hz) timer rollovers.
            val timeDiff = (maskedTime - lastWheelEventTime) and UINT16_MASK
            if (timeDiff > 0L) {
                identicalWheelTime = 0
                val revDiff = (maskedRevs - lastWheelRevolutions) and UINT32_MASK
                val speedMps = calibrationFactor * revDiff * wheelTimeResolution / timeDiff
                val paceSpm = if (revDiff != 0L && speedMps > 0.0) 1.0 / speedMps else null
                val totalRevs = (maskedRevs - initWheelRevolutions) and UINT32_MASK
                val distanceMeters = calibrationFactor * totalRevs
                WheelUpdate(
                    status = WheelStatus.COMPUTED,
                    speedMps = speedMps,
                    paceSpm = paceSpm,
                    distanceMeters = distanceMeters,
                    revDiff = revDiff
                )
            } else {
                identicalWheelTime++
                if (identicalWheelTime >= maxIdenticalTicks) {
                    WheelUpdate(
                        status = WheelStatus.IDENTICAL_TICK_ZEROED,
                        speedMps = 0.0,
                        paceSpm = null
                    )
                } else {
                    WheelUpdate(status = WheelStatus.NO_UPDATE)
                }
            }
        }

        lastWheelRevolutions = maskedRevs
        lastWheelEventTime = maskedTime
        lastWheelRevolutionsValid = true
        return result
    }

    /**
     * Evaluates a crank revolution notification sample and updates baseline state unconditionally.
     */
    fun processCrank(
        cumulativeCrankRevolutions: Long,
        crankEventTime: Long
    ): CrankUpdate {
        val maskedRevs = cumulativeCrankRevolutions and UINT16_MASK
        val maskedTime = crankEventTime and UINT16_MASK

        val result = if (!lastCrankRevolutionsValid) {
            identicalCrankTime = 0
            CrankUpdate(status = CrankStatus.INITIAL_BASELINE)
        } else {
            val timeDiff = (maskedTime - lastCrankEventTime) and UINT16_MASK
            if (timeDiff > 0L) {
                identicalCrankTime = 0
                val revDiff = (maskedRevs - lastCrankRevolutions) and UINT16_MASK
                val cadenceRpm = (60.0 * revDiff * crankTimeResolution) / timeDiff
                if (cadenceRpm in 0.0..MAX_PLAUSIBLE_CADENCE_RPM) {
                    CrankUpdate(
                        status = CrankStatus.COMPUTED,
                        cadenceRpm = cadenceRpm,
                        revDiff = revDiff,
                        timeDiff = timeDiff
                    )
                } else {
                    CrankUpdate(
                        status = CrankStatus.IGNORED_OUT_OF_RANGE,
                        cadenceRpm = cadenceRpm,
                        revDiff = revDiff,
                        timeDiff = timeDiff
                    )
                }
            } else {
                identicalCrankTime++
                if (identicalCrankTime >= maxIdenticalTicks) {
                    CrankUpdate(
                        status = CrankStatus.IDENTICAL_TICK_ZEROED,
                        cadenceRpm = 0.0
                    )
                } else {
                    CrankUpdate(status = CrankStatus.NO_UPDATE)
                }
            }
        }

        lastCrankRevolutions = maskedRevs
        lastCrankEventTime = maskedTime
        lastCrankRevolutionsValid = true
        return result
    }

    /**
     * Clears all wheel and crank baseline timestamps, cumulative initial offsets, and identical counters.
     */
    fun reset() {
        lastWheelRevolutionsValid = false
        lastCrankRevolutionsValid = false
        initWheelRevolutions = 0L
        lastWheelRevolutions = 0L
        lastWheelEventTime = 0L
        lastCrankRevolutions = 0L
        lastCrankEventTime = 0L
        identicalWheelTime = 0
        identicalCrankTime = 0
    }

    companion object {
        const val DEFAULT_CRANK_TIME_RESOLUTION = 1024
        const val DEFAULT_MAX_IDENTICAL_TICKS = 4
        const val MAX_PLAUSIBLE_CADENCE_RPM = 250.0
        private const val UINT16_MASK = 0xFFFFL
        private const val UINT32_MASK = 0xFFFFFFFFL
    }
}
