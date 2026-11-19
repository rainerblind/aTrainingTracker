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
 * (`ATT-8965`, `REQ-CON-052.1`, `REQ-CON-052.3`, `TST-CON-052.1`).
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
    fun testBackoffSequence_defaultFiveMinutes_progressesThroughTwoMinutesAndCapsAtFiveMinutes() {
        val observed = mutableListOf(SensorReconnectBackoffPolicy.INITIAL_DELAY_MS)
        var delay = SensorReconnectBackoffPolicy.INITIAL_DELAY_MS
        repeat(9) {
            delay = SensorReconnectBackoffPolicy.nextDelayMs(
                currentDelayMs = delay,
                periodicResearchEnabled = true,
                finalPeriodMs = SensorReconnectBackoffPolicy.DEFAULT_FINAL_PERIOD_MS
            )
            observed.add(delay)
        }

        assertEquals(
            listOf(
                1_000L,
                2_000L,
                4_000L,
                8_000L,
                16_000L,
                32_000L,
                64_000L,
                128_000L,
                300_000L,
                300_000L
            ),
            observed
        )
    }

    @Test
    fun testBackoffSequence_customFinalPeriods_clampCleanlyFor60sAnd120sAndReach600s() {
        var d60 = 1_000L
        val seq60 = mutableListOf(d60)
        repeat(7) {
            d60 = SensorReconnectBackoffPolicy.nextDelayMs(d60, true, 60_000L)
            seq60.add(d60)
        }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L), seq60)

        var d120 = 1_000L
        val seq120 = mutableListOf(d120)
        repeat(8) {
            d120 = SensorReconnectBackoffPolicy.nextDelayMs(d120, true, 120_000L)
            seq120.add(d120)
        }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 64_000L, 120_000L, 120_000L), seq120)

        var d600 = 1_000L
        repeat(10) {
            d600 = SensorReconnectBackoffPolicy.nextDelayMs(d600, true, 600_000L)
        }
        assertEquals(600_000L, d600)
    }

    @Test
    fun testBackoffSequence_whenPeriodicResearchDisabled_capsAtLegacy16Seconds() {
        var delay = SensorReconnectBackoffPolicy.INITIAL_DELAY_MS
        val observed = mutableListOf(delay)
        repeat(6) {
            delay = SensorReconnectBackoffPolicy.nextDelayMs(delay, false, 300_000L)
            observed.add(delay)
        }
        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 16_000L, 16_000L),
            observed
        )
    }

    @Test
    fun testNextPeriodicResearchDelay_startsAt32sAndAdvancesToFinalPeriod() {
        val d1 = SensorReconnectBackoffPolicy.nextPeriodicResearchDelayMs(null, 300_000L)
        val d2 = SensorReconnectBackoffPolicy.nextPeriodicResearchDelayMs(d1, 300_000L)
        val d3 = SensorReconnectBackoffPolicy.nextPeriodicResearchDelayMs(d2, 300_000L)
        val d4 = SensorReconnectBackoffPolicy.nextPeriodicResearchDelayMs(d3, 300_000L)
        val d5 = SensorReconnectBackoffPolicy.nextPeriodicResearchDelayMs(d4, 300_000L)
        assertEquals(listOf(32_000L, 64_000L, 128_000L, 300_000L, 300_000L), listOf(d1, d2, d3, d4, d5))
    }

    @Test
    fun testSensorSearchSettings_defaultsRoundTripAndClamping() {
        assertTrue(SensorSearchSettings.isPeriodicResearchEnabled(context))
        assertEquals(300, SensorSearchSettings.getPeriodicResearchFinalPeriodSec(context))
        assertEquals(300_000L, SensorSearchSettings.getPeriodicResearchFinalPeriodMs(context))

        SensorSearchSettings.setPeriodicResearchEnabled(context, false)
        SensorSearchSettings.setPeriodicResearchFinalPeriodSec(context, 120)
        assertFalse(SensorSearchSettings.isPeriodicResearchEnabled(context))
        assertEquals(120, SensorSearchSettings.getPeriodicResearchFinalPeriodSec(context))
        assertEquals(120_000L, SensorSearchSettings.getPeriodicResearchFinalPeriodMs(context))

        SensorSearchSettings.setPeriodicResearchFinalPeriodSec(context, 10)
        assertEquals(60, SensorSearchSettings.getPeriodicResearchFinalPeriodSec(context))
        SensorSearchSettings.setPeriodicResearchFinalPeriodSec(context, 9999)
        assertEquals(600, SensorSearchSettings.getPeriodicResearchFinalPeriodSec(context))
    }

    @Test
    fun testEffectiveDelayMs_clampsStaleBackoffImmediatelyOnLiveSettingsChange() {
        assertEquals(60_000L, SensorReconnectBackoffPolicy.effectiveDelayMs(600_000L, true, 60_000L))
        assertEquals(16_000L, SensorReconnectBackoffPolicy.effectiveDelayMs(300_000L, false, 300_000L))
        assertEquals(120_000L, SensorReconnectBackoffPolicy.effectiveDelayMs(120_000L, true, 300_000L))
    }
}
