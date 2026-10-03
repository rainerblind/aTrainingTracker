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

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import androidx.preference.PreferenceManager
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
 * Unit tests verifying forensic process exit classification, battery optimization state detection,
 * and escalation counter handling in [ProcessExitReasonHelper] (REQ-STB-012, TST-STB-012, ATT-2079).
 */
class ProcessExitReasonHelperTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var powerManager: PowerManager
    private lateinit var activityManager: ActivityManager
    private val prefStorage = mutableMapOf<String, Any>()

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        powerManager = mockk(relaxed = true)
        activityManager = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        sharedPreferencesEditor = mockk(relaxed = true)

        prefStorage.clear()

        every { context.packageName } returns "com.atrainingtracker"
        every { context.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager

        every { powerManager.isIgnoringBatteryOptimizations("com.atrainingtracker") } returns true

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns sharedPreferences

        every { sharedPreferences.getInt(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<Int>()
            (prefStorage[key] as? Int) ?: default
        }

        every { sharedPreferences.edit() } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putInt(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<Int>()
            prefStorage[key] = value
            sharedPreferencesEditor
        }
        every { sharedPreferencesEditor.apply() } answers { }

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.uploadToStrava() } returns false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testClassify_excessiveResourceUsage_returnsBatteryKill() {
        val exitInfo = mockk<ApplicationExitInfo>(relaxed = true)
        every { exitInfo.reason } returns ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE
        every { activityManager.getHistoricalProcessExitReasons("com.atrainingtracker", 0, 1) } returns listOf(exitInfo)

        val diagnosis = ProcessExitReasonHelper.resolveKillReason(context, sdkInt = Build.VERSION_CODES.R)

        assertEquals(KillReason.BATTERY_KILL, diagnosis.reason)
        assertEquals(1, diagnosis.escalationLevel)
    }

    @Test
    fun testClassify_notIgnoringBatteryOptimizations_returnsBatteryKill() {
        every { activityManager.getHistoricalProcessExitReasons("com.atrainingtracker", 0, 1) } returns emptyList()
        every { powerManager.isIgnoringBatteryOptimizations("com.atrainingtracker") } returns false

        val diagnosis = ProcessExitReasonHelper.resolveKillReason(context, sdkInt = Build.VERSION_CODES.R)

        assertEquals(KillReason.BATTERY_KILL, diagnosis.reason)
        assertTrue(diagnosis.shouldShowBatteryButton)
    }

    @Test
    fun testClassify_lowMemory_returnsLowMemory() {
        val exitInfo = mockk<ApplicationExitInfo>(relaxed = true)
        every { exitInfo.reason } returns ApplicationExitInfo.REASON_LOW_MEMORY
        every { activityManager.getHistoricalProcessExitReasons("com.atrainingtracker", 0, 1) } returns listOf(exitInfo)

        val diagnosis = ProcessExitReasonHelper.resolveKillReason(context, sdkInt = Build.VERSION_CODES.R)

        assertEquals(KillReason.LOW_MEMORY, diagnosis.reason)
        assertFalse(diagnosis.shouldShowBatteryButton)
    }

    @Test
    fun testClassify_permissionChange_returnsPermissionRevoked() {
        val exitInfo = mockk<ApplicationExitInfo>(relaxed = true)
        every { exitInfo.reason } returns ApplicationExitInfo.REASON_PERMISSION_CHANGE
        every { activityManager.getHistoricalProcessExitReasons("com.atrainingtracker", 0, 1) } returns listOf(exitInfo)

        val diagnosis = ProcessExitReasonHelper.resolveKillReason(context, sdkInt = Build.VERSION_CODES.R)

        assertEquals(KillReason.PERMISSION_REVOKED, diagnosis.reason)
        assertFalse(diagnosis.shouldShowBatteryButton)
    }

    @Test
    fun testClassify_normalGeneric_returnsGenericUnfinished() {
        every { activityManager.getHistoricalProcessExitReasons("com.atrainingtracker", 0, 1) } returns emptyList()
        every { powerManager.isIgnoringBatteryOptimizations("com.atrainingtracker") } returns true

        val diagnosis = ProcessExitReasonHelper.resolveKillReason(context, sdkInt = Build.VERSION_CODES.R)

        assertEquals(KillReason.GENERIC_UNFINISHED, diagnosis.reason)
        assertEquals(0, diagnosis.escalationLevel)
        assertFalse(diagnosis.shouldShowBatteryButton)
    }

    @Test
    fun testBatteryKillCount_incrementsAndResets() {
        assertEquals(0, ProcessExitReasonHelper.getBatteryKillCount(context))

        val count1 = ProcessExitReasonHelper.incrementBatteryKillCount(context)
        assertEquals(1, count1)
        assertEquals(1, ProcessExitReasonHelper.getBatteryKillCount(context))

        val count2 = ProcessExitReasonHelper.incrementBatteryKillCount(context)
        assertEquals(2, count2)
        assertEquals(2, ProcessExitReasonHelper.getBatteryKillCount(context))

        ProcessExitReasonHelper.resetBatteryKillCount(context)
        assertEquals(0, ProcessExitReasonHelper.getBatteryKillCount(context))
    }
}
