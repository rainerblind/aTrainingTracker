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

package com.atrainingtracker.banalservice.devices

import android.content.Context
import com.atrainingtracker.trainingtracker.settings.SensorSearchSettings

/**
 * Shared exponential backoff schedule for BLE / Polar / DeviceManager reconnect & periodic re-search
 * of lost/MIA sensors (ATT-8965, ATT-9178, REQ-CON-052.1).
 *
 * Sequence when periodic re-search is enabled (presets `15s / 30s / 1m / 2m`, default `1m = 60,000 ms`):
 * `1s -> 2s -> 4s -> 8s -> 16s -> 32s -> 64s -> 120s` capped at `<finalPeriodMs>`.
 *
 * When periodic re-search is disabled, delays cap cleanly at [LEGACY_FAST_MAX_DELAY_MS] (16,000 ms)
 * matching `MyBTLEDevice.MAX_RECONNECT_DELAY_MS` (`SYSREQ-TRK-002`).
 */
object SensorReconnectBackoffPolicy {
    const val INITIAL_DELAY_MS = 1_000L
    const val LEGACY_FAST_MAX_DELAY_MS = 16_000L
    const val INITIAL_PERIODIC_RESEARCH_DELAY_MS = 32_000L
    const val EXTENDED_DOUBLING_CEILING_MS = 128_000L
    const val DEFAULT_FINAL_PERIOD_MS = 60_000L

    /**
     * Clamps [currentDelayMs] immediately against the live settings ceiling (`16,000 ms` when
     * disabled, [finalPeriodMs] when enabled) so turning off periodic re-search or lowering the
     * steady-state period takes effect on the very next scheduled retry (ATT-8965 Bounce 2).
     */
    @JvmStatic
    fun effectiveDelayMs(
        currentDelayMs: Long,
        periodicResearchEnabled: Boolean,
        finalPeriodMs: Long = DEFAULT_FINAL_PERIOD_MS
    ): Long {
        val safeCurrent = if (currentDelayMs <= 0L) INITIAL_DELAY_MS else currentDelayMs
        val ceiling = if (periodicResearchEnabled) {
            finalPeriodMs.coerceAtLeast(INITIAL_DELAY_MS)
        } else {
            LEGACY_FAST_MAX_DELAY_MS
        }
        return safeCurrent.coerceAtMost(ceiling)
    }

    /**
     * Clamps [currentDelayMs] immediately against live [SensorSearchSettings] read from [context].
     */
    @JvmStatic
    fun effectiveDelayMs(currentDelayMs: Long, context: Context?): Long {
        val enabled = SensorSearchSettings.isPeriodicResearchEnabled(context)
        val finalPeriodMs = SensorSearchSettings.getPeriodicResearchFinalPeriodMs(context)
        return effectiveDelayMs(
            currentDelayMs = currentDelayMs,
            periodicResearchEnabled = enabled,
            finalPeriodMs = finalPeriodMs
        )
    }

    /**
     * Computes the next retry delay in milliseconds given [currentDelayMs].
     */
    @JvmStatic
    fun nextDelayMs(
        currentDelayMs: Long,
        periodicResearchEnabled: Boolean,
        finalPeriodMs: Long = DEFAULT_FINAL_PERIOD_MS
    ): Long {
        val safeCurrent = if (currentDelayMs <= 0L) INITIAL_DELAY_MS else currentDelayMs
        if (!periodicResearchEnabled) {
            val doubled = if (safeCurrent > Long.MAX_VALUE / 2L) LEGACY_FAST_MAX_DELAY_MS else safeCurrent * 2L
            return doubled.coerceAtMost(LEGACY_FAST_MAX_DELAY_MS)
        }

        val capMs = finalPeriodMs.coerceAtLeast(INITIAL_DELAY_MS)
        if (safeCurrent >= EXTENDED_DOUBLING_CEILING_MS) {
            return capMs
        }
        val doubled = if (safeCurrent > Long.MAX_VALUE / 2L) capMs else safeCurrent * 2L
        return doubled.coerceAtMost(capMs)
    }

    /**
     * Computes the next retry delay using live [SensorSearchSettings] read from [context].
     */
    @JvmStatic
    fun nextDelayMs(currentDelayMs: Long, context: Context?): Long {
        val enabled = SensorSearchSettings.isPeriodicResearchEnabled(context)
        val finalPeriodMs = SensorSearchSettings.getPeriodicResearchFinalPeriodMs(context)
        return nextDelayMs(
            currentDelayMs = currentDelayMs,
            periodicResearchEnabled = enabled,
            finalPeriodMs = finalPeriodMs
        )
    }

    /**
     * Computes the first or subsequent delay after sequential queue tries (`tries <= 1`) expire:
     * starts at `min(32s, finalPeriodMs)` and progresses `32s -> 64s -> 128s -> <finalPeriodMs>`.
     */
    @JvmStatic
    fun nextPeriodicResearchDelayMs(
        previousDelayMs: Long?,
        finalPeriodMs: Long = DEFAULT_FINAL_PERIOD_MS
    ): Long {
        val capMs = finalPeriodMs.coerceAtLeast(INITIAL_DELAY_MS)
        if (previousDelayMs == null || previousDelayMs <= 0L) {
            return INITIAL_PERIODIC_RESEARCH_DELAY_MS.coerceAtMost(capMs)
        }
        return nextDelayMs(
            currentDelayMs = previousDelayMs,
            periodicResearchEnabled = true,
            finalPeriodMs = capMs
        )
    }
}
