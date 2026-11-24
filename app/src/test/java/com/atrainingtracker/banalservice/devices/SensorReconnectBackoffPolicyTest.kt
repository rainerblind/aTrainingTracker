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
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.settings.SensorSearchSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [SensorReconnectBackoffPolicy] and [SensorSearchSettings]
 * (`ATT-8965`, `ATT-9178`, `REQ-CON-052.1`, `REQ-CON-052.3`, `TST-CON-052.1`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SensorReconnectBackoffPolicyTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun testBackoffSequence_defaultOneMinute_progressesAndCapsAtSixtySeconds() {
        val observed = mutableListOf(SensorReconnectBackoffPolicy.INITIAL_DELAY_MS)
        var delay = SensorReconnectBackoffPolicy.INITIAL_DELAY_MS
        repeat(7) {
            delay = SensorReconnectBackoffPolicy.nextDelayMs(
                currentDelayMs = delay,
                periodicResearchEnabled = true,
                finalPeriodMs = SensorReconnectBackoffPolicy.DEFAULT_FINAL_PERIOD_MS
            )
            observed.add(delay)
        }

        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L),
            observed
        )
    }

    @Test
    fun testBackoffSequence_allFourPresets_15s_30s_1m_2m_clampCleanly() {
        var d15 = 1_000L
        val seq15 = mutableListOf(d15)
        repeat(5) {
            d15 = SensorReconnectBackoffPolicy.nextDelayMs(d15, true, 15_000L)
            seq15.add(d15)
        }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 15_000L), seq15)

        var d30 = 1_000L
        val seq30 = mutableListOf(d30)
        repeat(6) {
            d30 = SensorReconnectBackoffPolicy.nextDelayMs(d30, true, 30_000L)
            seq30.add(d30)
        }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), seq30)

        var d120 = 1_000L
        val seq120 = mutableListOf(d120)
        repeat(8) {
            d120 = SensorReconnectBackoffPolicy.nextDelayMs(d120, true, 120_000L)
            seq120.add(d120)
        }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 64_000L, 120_000L, 120_000L), seq120)
    }

    @Test
    fun testBackoffSequence_whenPeriodicResearchDisabled_capsAtLegacy16Seconds() {
        var delay = SensorReconnectBackoffPolicy.INITIAL_DELAY_MS
        val observed = mutableListOf(delay)
        repeat(6) {
            delay = SensorReconnectBackoffPolicy.nextDelayMs(delay, false, 120_000L)
            observed.add(delay)
        }
        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 16_000L, 16_000L),
            observed
        )
    }

    @Test
    fun testNextPeriodicResearchDelay_honorsAllFourPresets() {
        // 15s & 30s start immediately capped at finalPeriodMs
        assertEquals(15_000L, SensorReconnectBackoffPolicy.nextPeriodicResearchDelayMs(null, 15_000L))
        assertEquals(15_000L, SensorReconnectBackoffPolicy.nextPeriodicResearchDelayMs(15_000L, 15_000L))
        assertEquals(30_000L, SensorReconnectBackoffPolicy.nextPeriodicResearchDelayMs(null, 30_000L))
        assertEquals(30_000L, SensorReconnectBackoffPolicy.nextPeriodicResearchDelayMs(30_000L, 30_000L))

        // 1m (60s default): 32s -> 60s -> 60s
        val m1a = SensorReconnectBackoffPolicy.nextPeriodicResearchDelayMs(null, 60_000L)
        val m1b = SensorReconnectBackoffPolicy.nextPeriodicResearchDelayMs(m1a, 60_000L)
        val m1c = SensorReconnectBackoffPolicy.nextPeriodicResearchDelayMs(m1b, 60_000L)
        assertEquals(listOf(32_000L, 60_000L, 60_000L), listOf(m1a, m1b, m1c))

        // 2m (120s): 32s -> 64s -> 120s -> 120s
        val m2a = SensorReconnectBackoffPolicy.nextPeriodicResearchDelayMs(null, 120_000L)
        val m2b = SensorReconnectBackoffPolicy.nextPeriodicResearchDelayMs(m2a, 120_000L)
        val m2c = SensorReconnectBackoffPolicy.nextPeriodicResearchDelayMs(m2b, 120_000L)
        val m2d = SensorReconnectBackoffPolicy.nextPeriodicResearchDelayMs(m2c, 120_000L)
        assertEquals(listOf(32_000L, 64_000L, 120_000L, 120_000L), listOf(m2a, m2b, m2c, m2d))
    }

    @Test
    fun testSensorSearchSettings_defaultsRoundTripAndLegacyClamping() {
        assertEquals(listOf(15, 30, 60, 120), SensorSearchSettings.FINAL_PERIOD_OPTIONS_SEC)
        assertTrue(SensorSearchSettings.isPeriodicResearchEnabled(context))
        assertEquals(60, SensorSearchSettings.getPeriodicResearchFinalPeriodSec(context))
        assertEquals(60_000L, SensorSearchSettings.getPeriodicResearchFinalPeriodMs(context))

        SensorSearchSettings.setPeriodicResearchEnabled(context, false)
        SensorSearchSettings.setPeriodicResearchFinalPeriodSec(context, 30)
        assertFalse(SensorSearchSettings.isPeriodicResearchEnabled(context))
        assertEquals(30, SensorSearchSettings.getPeriodicResearchFinalPeriodSec(context))
        assertEquals(30_000L, SensorSearchSettings.getPeriodicResearchFinalPeriodMs(context))

        // Legacy ATT-8965 persisted 300s & 600s values clamp cleanly to 120s (2m)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putInt(SensorSearchSettings.SP_PERIODIC_RESEARCH_FINAL_PERIOD_SEC, 300).commit()
        assertEquals(120, SensorSearchSettings.getPeriodicResearchFinalPeriodSec(context))
        prefs.edit().putInt(SensorSearchSettings.SP_PERIODIC_RESEARCH_FINAL_PERIOD_SEC, 600).commit()
        assertEquals(120, SensorSearchSettings.getPeriodicResearchFinalPeriodSec(context))

        // Lower bound, upper bound, and intermediate non-preset values snap cleanly to valid presets
        assertEquals(15, SensorSearchSettings.normalizeFinalPeriodSec(5))
        assertEquals(15, SensorSearchSettings.normalizeFinalPeriodSec(15))
        assertEquals(30, SensorSearchSettings.normalizeFinalPeriodSec(40))
        assertEquals(60, SensorSearchSettings.normalizeFinalPeriodSec(50))
        assertEquals(120, SensorSearchSettings.normalizeFinalPeriodSec(100))
        assertEquals(120, SensorSearchSettings.normalizeFinalPeriodSec(9999))
    }

    @Test
    fun testEffectiveDelayMs_clampsStaleBackoffImmediatelyOnLiveSettingsChange() {
        assertEquals(15_000L, SensorReconnectBackoffPolicy.effectiveDelayMs(120_000L, true, 15_000L))
        assertEquals(30_000L, SensorReconnectBackoffPolicy.effectiveDelayMs(120_000L, true, 30_000L))
        assertEquals(16_000L, SensorReconnectBackoffPolicy.effectiveDelayMs(120_000L, false, 120_000L))
        assertEquals(60_000L, SensorReconnectBackoffPolicy.effectiveDelayMs(60_000L, true, 120_000L))
    }
}
