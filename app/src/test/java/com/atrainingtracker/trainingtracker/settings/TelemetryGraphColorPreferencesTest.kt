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
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricType
import com.atrainingtracker.trainingtracker.ui.theme.DarkColorScheme
import com.atrainingtracker.trainingtracker.ui.theme.LightColorScheme
import com.atrainingtracker.trainingtracker.ui.theme.TelemetryGraphColor
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [TelemetryGraphColorPreferences] covering canonical metric/sensor defaults,
 * SharedPreferences persistence, reset operations, and safe enum deserialization
 * (REQ-UI-530.2, REQ-UI-530.3, TST-UI-530.2, TST-UI-530.3, REQ-ARC-001, ATT-7759).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TelemetryGraphColorPreferencesTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().commit()
    }

    @Test
    fun canonicalDefaultColors_matchSpecificationForAllMetricsAndSensors() {
        val expectedMetrics = mapOf(
            TelemetryMetricType.HEART_RATE to TelemetryGraphColor.CRIMSON,
            TelemetryMetricType.POWER to TelemetryGraphColor.VIOLET,
            TelemetryMetricType.W_PRIME_BAL to TelemetryGraphColor.EMERALD,
            TelemetryMetricType.HRV_DFA_A1 to TelemetryGraphColor.AMBER,
            TelemetryMetricType.HRV_RMSSD to TelemetryGraphColor.TEAL,
            TelemetryMetricType.SPEED to TelemetryGraphColor.THEME_DEFAULT,
            TelemetryMetricType.PACE to TelemetryGraphColor.THEME_DEFAULT
        )
        expectedMetrics.forEach { (metric, expected) ->
            assertEquals(expected, TelemetryGraphColorPreferences.getDefaultColor(metric))
        }

        val expectedSensors = mapOf(
            SensorType.HR to TelemetryGraphColor.CRIMSON,
            SensorType.POWER to TelemetryGraphColor.VIOLET,
            SensorType.POWER_5S to TelemetryGraphColor.VIOLET,
            SensorType.NORMALIZED_POWER to TelemetryGraphColor.VIOLET,
            SensorType.W_PRIME_BAL to TelemetryGraphColor.EMERALD,
            SensorType.HRV_DFA_A1 to TelemetryGraphColor.AMBER,
            SensorType.HRV_RMSSD to TelemetryGraphColor.TEAL,
            SensorType.SPEED_mps to TelemetryGraphColor.THEME_DEFAULT,
            SensorType.PACE_spm to TelemetryGraphColor.THEME_DEFAULT,
            SensorType.CADENCE to TelemetryGraphColor.THEME_DEFAULT,
            SensorType.ALTITUDE to TelemetryGraphColor.THEME_DEFAULT,
            SensorType.SLOPE to TelemetryGraphColor.THEME_DEFAULT,
            SensorType.TEMPERATURE to TelemetryGraphColor.THEME_DEFAULT
        )
        expectedSensors.forEach { (sensor, expected) ->
            assertEquals(expected, TelemetryGraphColorPreferences.getDefaultColor(sensor))
        }
    }

    @Test
    fun setGetAndResetGraphColor_persistsAndSharesMetricFamilyKeys() {
        TelemetryGraphColorPreferences.setGraphColor(context, TelemetryMetricType.HEART_RATE, TelemetryGraphColor.CYAN)
        assertEquals(
            TelemetryGraphColor.CYAN,
            TelemetryGraphColorPreferences.getGraphColor(context, TelemetryMetricType.HEART_RATE)
        )
        // SensorType.HR shares the HEART_RATE family key
        assertEquals(
            TelemetryGraphColor.CYAN,
            TelemetryGraphColorPreferences.getGraphColor(context, SensorType.HR)
        )

        TelemetryGraphColorPreferences.setGraphColor(context, SensorType.CADENCE, TelemetryGraphColor.MAGENTA)
        assertEquals(
            TelemetryGraphColor.MAGENTA,
            TelemetryGraphColorPreferences.getGraphColor(context, SensorType.CADENCE)
        )

        // Resetting HEART_RATE restores CRIMSON without clobbering CADENCE
        TelemetryGraphColorPreferences.resetGraphColor(context, TelemetryMetricType.HEART_RATE)
        assertEquals(
            TelemetryGraphColor.CRIMSON,
            TelemetryGraphColorPreferences.getGraphColor(context, TelemetryMetricType.HEART_RATE)
        )
        assertEquals(
            TelemetryGraphColor.MAGENTA,
            TelemetryGraphColorPreferences.getGraphColor(context, SensorType.CADENCE)
        )
    }

    @Test
    fun resetAllGraphColors_clearsOnlyTelemetryColorKeys() {
        prefs.edit().putString("unrelated_app_pref", "keep_me").apply()
        TelemetryGraphColorPreferences.setGraphColor(context, TelemetryMetricType.HEART_RATE, TelemetryGraphColor.CYAN)
        TelemetryGraphColorPreferences.setGraphColor(context, TelemetryMetricType.POWER, TelemetryGraphColor.CORAL)
        TelemetryGraphColorPreferences.setGraphColor(context, SensorType.ALTITUDE, TelemetryGraphColor.TEAL)

        TelemetryGraphColorPreferences.resetAllGraphColors(context)

        assertEquals("keep_me", prefs.getString("unrelated_app_pref", null))
        assertEquals(
            TelemetryGraphColor.CRIMSON,
            TelemetryGraphColorPreferences.getGraphColor(context, TelemetryMetricType.HEART_RATE)
        )
        assertEquals(
            TelemetryGraphColor.VIOLET,
            TelemetryGraphColorPreferences.getGraphColor(context, TelemetryMetricType.POWER)
        )
        assertEquals(
            TelemetryGraphColor.THEME_DEFAULT,
            TelemetryGraphColorPreferences.getGraphColor(context, SensorType.ALTITUDE)
        )
    }

    @Test
    fun corruptOrUnknownTokens_safelyFallBackToCanonicalDefault() {
        val hrKey = TelemetryGraphColorPreferences.preferenceKey(TelemetryMetricType.HEART_RATE)
        val pwrKey = TelemetryGraphColorPreferences.preferenceKey(TelemetryMetricType.POWER)
        val spdKey = TelemetryGraphColorPreferences.preferenceKey(TelemetryMetricType.SPEED)

        prefs.edit()
            .putString(hrKey, "NON_EXISTENT_COLOR")
            .putString(pwrKey, "cyan") // lowercase invalid enum name
            .putString(spdKey, "   ") // blank
            .commit()

        assertEquals(
            TelemetryGraphColor.CRIMSON,
            TelemetryGraphColorPreferences.getGraphColor(context, TelemetryMetricType.HEART_RATE)
        )
        assertEquals(
            TelemetryGraphColor.VIOLET,
            TelemetryGraphColorPreferences.getGraphColor(context, TelemetryMetricType.POWER)
        )
        assertEquals(
            TelemetryGraphColor.THEME_DEFAULT,
            TelemetryGraphColorPreferences.getGraphColor(context, TelemetryMetricType.SPEED)
        )
    }

    @Test
    fun resolveAccentColor_handlesNullPrefsAndThemeAdaptation() {
        assertEquals(
            TelemetryGraphColor.CRIMSON.lightColor,
            TelemetryGraphColorPreferences.resolveAccentColor(null, TelemetryMetricType.HEART_RATE, LightColorScheme)
        )
        assertEquals(
            TelemetryGraphColor.CRIMSON.darkColor,
            TelemetryGraphColorPreferences.resolveAccentColor(null, TelemetryMetricType.HEART_RATE, DarkColorScheme)
        )
    }
}
