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

package com.atrainingtracker.trainingtracker.helpers

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.preference.PreferenceManager
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.helpers.ProcessExitReasonHelper.KillReason
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying progressive escalation levels (Stage 1, Stage 2, Stage 3+),
 * conditional Strava bonus text, and battery exemption logic (REQ-STB-012, TST-STB-012, ATT-2079).
 */
class ProcessKillEscalationTest {

    private lateinit var context: Context
    private lateinit var powerManager: PowerManager
    private val prefStorage = mutableMapOf<String, Any>()

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        powerManager = mockk(relaxed = true)
        prefStorage.clear()

        every { context.packageName } returns "com.atrainingtracker"
        every { context.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { powerManager.isIgnoringBatteryOptimizations("com.atrainingtracker") } returns false

        mockkStatic(PreferenceManager::class)
        val editor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        every { editor.putInt(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<Int>()
            prefStorage[key] = value
            editor
        }
        val prefs = mockk<android.content.SharedPreferences>(relaxed = true)
        every { prefs.getInt(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<Int>()
            (prefStorage[key] as? Int) ?: default
        }
        every { prefs.edit() } returns editor
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns prefs

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.uploadToStrava() } returns false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testStage1_stravaActive_setsFlagTrue() {
        every { TrainingApplication.uploadToStrava() } returns true

        val diagnosis = ProcessExitReasonHelper.resolveKillReason(context, sdkInt = Build.VERSION_CODES.R)

        assertEquals(KillReason.BATTERY_KILL, diagnosis.reason)
        assertEquals(1, diagnosis.escalationLevel)
        assertTrue("Strava active flag should be true", diagnosis.isStravaActive)
    }

    @Test
    fun testStage1_stravaDisabled_setsFlagFalse() {
        every { TrainingApplication.uploadToStrava() } returns false

        val diagnosis = ProcessExitReasonHelper.resolveKillReason(context, sdkInt = Build.VERSION_CODES.R)

        assertEquals(KillReason.BATTERY_KILL, diagnosis.reason)
        assertEquals(1, diagnosis.escalationLevel)
        assertFalse("Strava active flag should be false", diagnosis.isStravaActive)
    }

    @Test
    fun testStage2_secondKill_escalationLevelTwo() {
        prefStorage[ProcessExitReasonHelper.PREF_BATTERY_KILL_COUNT] = 1

        val diagnosis = ProcessExitReasonHelper.resolveKillReason(context, sdkInt = Build.VERSION_CODES.R)

        assertEquals(KillReason.BATTERY_KILL, diagnosis.reason)
        assertEquals(2, diagnosis.escalationLevel)
    }

    @Test
    fun testStage3_thirdKill_escalationLevelThreeOrMore() {
        prefStorage[ProcessExitReasonHelper.PREF_BATTERY_KILL_COUNT] = 2

        val diagnosis = ProcessExitReasonHelper.resolveKillReason(context, sdkInt = Build.VERSION_CODES.R)

        assertEquals(KillReason.BATTERY_KILL, diagnosis.reason)
        assertEquals(3, diagnosis.escalationLevel)
    }

    @Test
    fun testBatteryOptimizationButton_trueWhenNotIgnoring() {
        every { powerManager.isIgnoringBatteryOptimizations("com.atrainingtracker") } returns false

        val diagnosis = ProcessExitReasonHelper.resolveKillReason(context, sdkInt = Build.VERSION_CODES.R)

        assertTrue(diagnosis.shouldShowBatteryButton)
    }

    @Test
    fun testRequiredStringResourcesExist() {
        val requiredIds = listOf(
            R.string.unfinished_workout_title,
            R.string.kill_reason_battery_title,
            R.string.kill_reason_battery_stage1,
            R.string.kill_reason_battery_stage1_strava,
            R.string.kill_reason_battery_stage2,
            R.string.kill_reason_battery_stage3,
            R.string.kill_reason_low_memory,
            R.string.kill_reason_permission_revoked,
            R.string.action_disable_battery_optimization,
            R.string.battery_optimization_exempt_celebration
        )
        for (resId in requiredIds) {
            assertTrue("Resource ID $resId must be valid non-zero integer", resId != 0)
        }
    }
}
