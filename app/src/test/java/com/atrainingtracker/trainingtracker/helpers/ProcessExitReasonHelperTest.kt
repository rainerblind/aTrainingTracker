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
        every { sharedPreferences.getLong(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<Long>()
            (prefStorage[key] as? Long) ?: default
        }

        every { sharedPreferences.edit() } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putInt(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<Int>()
            prefStorage[key] = value
            sharedPreferencesEditor
        }
        every { sharedPreferencesEditor.putLong(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<Long>()
            prefStorage[key] = value
            sharedPreferencesEditor
        }
        every { sharedPreferencesEditor.apply() } answers { }

        ProcessExitReasonHelper.resetSessionForTesting()

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.uploadToStrava() } returns false

        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<Throwable>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any<Throwable>()) } returns 0
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
        ProcessExitReasonHelper.resetSessionForTesting()
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

    @Test
    fun testResolveKillReason_repeatedCalls_doesNotIncrementCounter() {
        val exitInfo = mockk<ApplicationExitInfo>(relaxed = true)
        every { exitInfo.reason } returns ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE
        every { exitInfo.timestamp } returns 1000L
        every { activityManager.getHistoricalProcessExitReasons("com.atrainingtracker", 0, 1) } returns listOf(exitInfo)
        every { powerManager.isIgnoringBatteryOptimizations("com.atrainingtracker") } returns false

        // First resolution
        val diag1 = ProcessExitReasonHelper.resolveKillReason(context, sdkInt = Build.VERSION_CODES.R)
        assertEquals(KillReason.BATTERY_KILL, diag1.reason)
        assertEquals(1, diag1.escalationLevel)

        // Second resolution (simulating screen rotation or fragment recreation with same timestamp)
        val diag2 = ProcessExitReasonHelper.resolveKillReason(context, sdkInt = Build.VERSION_CODES.R)
        assertEquals(KillReason.BATTERY_KILL, diag2.reason)
        assertEquals(1, diag2.escalationLevel)
        assertEquals(1, ProcessExitReasonHelper.getBatteryKillCount(context))
    }

    @Test
    fun testResolveKillReason_newExitTimestamp_incrementsCounter() {
        val exitInfo1 = mockk<ApplicationExitInfo>(relaxed = true)
        every { exitInfo1.reason } returns ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE
        every { exitInfo1.timestamp } returns 1000L
        every { activityManager.getHistoricalProcessExitReasons("com.atrainingtracker", 0, 1) } returns listOf(exitInfo1)
        every { powerManager.isIgnoringBatteryOptimizations("com.atrainingtracker") } returns false

        val diag1 = ProcessExitReasonHelper.resolveKillReason(context, sdkInt = Build.VERSION_CODES.R)
        assertEquals(1, diag1.escalationLevel)

        // Subsequent kill event with newer timestamp
        val exitInfo2 = mockk<ApplicationExitInfo>(relaxed = true)
        every { exitInfo2.reason } returns ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE
        every { exitInfo2.timestamp } returns 2000L
        every { activityManager.getHistoricalProcessExitReasons("com.atrainingtracker", 0, 1) } returns listOf(exitInfo2)

        val diag2 = ProcessExitReasonHelper.resolveKillReason(context, sdkInt = Build.VERSION_CODES.R)
        assertEquals(2, diag2.escalationLevel)
        assertEquals(2, ProcessExitReasonHelper.getBatteryKillCount(context))
    }

    @Test
    fun testOpenBatteryOptimizationSettings_launchesActionRequestIgnoreBatteryOptimizationsFirst() {
        mockkStatic(android.net.Uri::class)
        val mockUri = mockk<android.net.Uri>()
        every { android.net.Uri.parse("package:com.atrainingtracker") } returns mockUri

        io.mockk.mockkConstructor(android.content.Intent::class)
        val intentSlot = io.mockk.slot<android.content.Intent>()
        every { context.startActivity(capture(intentSlot)) } answers { }
        every { anyConstructed<android.content.Intent>().action } returns android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        every { anyConstructed<android.content.Intent>().data } returns mockUri
        every { anyConstructed<android.content.Intent>().addFlags(any()) } returns mockk(relaxed = true)
        every { anyConstructed<android.content.Intent>().data = any() } answers { }
        every { anyConstructed<android.content.Intent>().setData(any()) } returns mockk(relaxed = true)

        ProcessExitReasonHelper.openBatteryOptimizationSettings(context)

        assertTrue(intentSlot.isCaptured)
        val captured = intentSlot.captured
        assertEquals(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, captured.action)
        assertEquals(mockUri, captured.data)
    }
}
