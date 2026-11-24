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

package com.atrainingtracker.trainingtracker.settings

import android.content.Context
import androidx.preference.PreferenceManager
import kotlin.math.abs

/**
 * SharedPreferences accessor for periodic long-term sensor re-search and steady-state backoff
 * configuration (ATT-8965, ATT-9178, REQ-CON-052.3).
 *
 * Stored in default SharedPreferences alongside legacy search preferences while keeping
 * [com.atrainingtracker.trainingtracker.TrainingApplication] within its ratcheted LoC ceiling.
 */
object SensorSearchSettings {
    const val SP_PERIODIC_RESEARCH_ENABLED = "periodicSensorResearchEnabled"
    const val DEFAULT_PERIODIC_RESEARCH_ENABLED = true

    const val SP_PERIODIC_RESEARCH_FINAL_PERIOD_SEC = "periodicSensorResearchFinalPeriodSec"
    const val MIN_FINAL_PERIOD_SEC = 15
    const val MAX_FINAL_PERIOD_SEC = 120
    const val DEFAULT_FINAL_PERIOD_SEC = 60

    @JvmField
    val FINAL_PERIOD_OPTIONS_SEC: List<Int> = listOf(15, 30, 60, 120)

    /**
     * Clamps [valueSec] to `[MIN_FINAL_PERIOD_SEC, MAX_FINAL_PERIOD_SEC]` (mapping legacy `ATT-8965`
     * `300`/`600` values to `120`) and snaps to the nearest canonical preset in
     * [FINAL_PERIOD_OPTIONS_SEC] so segmented selectors always highlight a valid option.
     */
    @JvmStatic
    fun normalizeFinalPeriodSec(valueSec: Int): Int {
        val clamped = valueSec.coerceIn(MIN_FINAL_PERIOD_SEC, MAX_FINAL_PERIOD_SEC)
        return FINAL_PERIOD_OPTIONS_SEC.minByOrNull { abs(it - clamped) } ?: DEFAULT_FINAL_PERIOD_SEC
    }

    @JvmStatic
    fun isPeriodicResearchEnabled(context: Context?): Boolean {
        if (context == null) return DEFAULT_PERIODIC_RESEARCH_ENABLED
        return try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            if (!prefs.contains(SP_PERIODIC_RESEARCH_ENABLED)) return DEFAULT_PERIODIC_RESEARCH_ENABLED
            prefs.getBoolean(SP_PERIODIC_RESEARCH_ENABLED, DEFAULT_PERIODIC_RESEARCH_ENABLED)
        } catch (_: Throwable) {
            DEFAULT_PERIODIC_RESEARCH_ENABLED
        }
    }

    @JvmStatic
    fun setPeriodicResearchEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(SP_PERIODIC_RESEARCH_ENABLED, enabled)
            .apply()
    }

    @JvmStatic
    fun getPeriodicResearchFinalPeriodSec(context: Context?): Int {
        if (context == null) return DEFAULT_FINAL_PERIOD_SEC
        return try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            if (!prefs.contains(SP_PERIODIC_RESEARCH_FINAL_PERIOD_SEC)) return DEFAULT_FINAL_PERIOD_SEC
            val raw = try {
                prefs.getInt(SP_PERIODIC_RESEARCH_FINAL_PERIOD_SEC, DEFAULT_FINAL_PERIOD_SEC)
            } catch (_: ClassCastException) {
                prefs.getString(SP_PERIODIC_RESEARCH_FINAL_PERIOD_SEC, null)?.toIntOrNull()
                    ?: DEFAULT_FINAL_PERIOD_SEC
            }
            normalizeFinalPeriodSec(raw)
        } catch (_: Throwable) {
            DEFAULT_FINAL_PERIOD_SEC
        }
    }

    @JvmStatic
    fun setPeriodicResearchFinalPeriodSec(context: Context, finalPeriodSec: Int) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putInt(SP_PERIODIC_RESEARCH_FINAL_PERIOD_SEC, normalizeFinalPeriodSec(finalPeriodSec))
            .apply()
    }

    @JvmStatic
    fun getPeriodicResearchFinalPeriodMs(context: Context?): Long =
        getPeriodicResearchFinalPeriodSec(context) * 1_000L
}
