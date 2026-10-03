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
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.preference.PreferenceManager
import com.atrainingtracker.trainingtracker.TrainingApplication

/**
 * Diagnostic helper that inspects historical process exit reasons via [ApplicationExitInfo] (Android 11+)
 * and battery optimization states via [PowerManager] to explain unexpected process termination
 * to the athlete with progressive escalation (REQ-STB-012, TST-STB-012, ATT-2079).
 */
object ProcessExitReasonHelper {

    private const val TAG = "ProcessExitReasonHelper"
    const val PREF_BATTERY_KILL_COUNT = "pref_battery_kill_count"

    enum class KillReason {
        BATTERY_KILL,
        LOW_MEMORY,
        PERMISSION_REVOKED,
        GENERIC_UNFINISHED
    }

    data class KillDiagnosis(
        val reason: KillReason,
        val escalationLevel: Int,
        val isStravaActive: Boolean,
        val shouldShowBatteryButton: Boolean
    )

    /**
     * Resolves the root cause of the previous process termination when an unfinished workout exists.
     * Evaluates Android 11+ [ApplicationExitInfo] and [PowerManager.isIgnoringBatteryOptimizations].
     */
    fun resolveKillReason(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): KillDiagnosis {
        var detectedReason = KillReason.GENERIC_UNFINISHED

        if (sdkInt >= Build.VERSION_CODES.R) {
            try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val exitInfos = activityManager?.getHistoricalProcessExitReasons(context.packageName, 0, 1)
                val exitInfo = exitInfos?.firstOrNull()
                if (exitInfo != null) {
                    detectedReason = when (exitInfo.reason) {
                        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> KillReason.BATTERY_KILL
                        ApplicationExitInfo.REASON_LOW_MEMORY -> KillReason.LOW_MEMORY
                        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> KillReason.PERMISSION_REVOKED
                        else -> KillReason.GENERIC_UNFINISHED
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to query historical process exit reasons", e)
            }
        }

        val isIgnoringBattery = isIgnoringBatteryOptimizations(context, sdkInt)
        if (detectedReason == KillReason.GENERIC_UNFINISHED && !isIgnoringBattery) {
            detectedReason = KillReason.BATTERY_KILL
        }

        val escalationLevel: Int
        val shouldShowBatteryButton: Boolean
        if (detectedReason == KillReason.BATTERY_KILL) {
            escalationLevel = incrementBatteryKillCount(context)
            shouldShowBatteryButton = !isIgnoringBattery
        } else {
            escalationLevel = 0
            shouldShowBatteryButton = false
        }

        val isStravaActive = try {
            TrainingApplication.uploadToStrava()
        } catch (e: Throwable) {
            false
        }

        return KillDiagnosis(
            reason = detectedReason,
            escalationLevel = escalationLevel,
            isStravaActive = isStravaActive,
            shouldShowBatteryButton = shouldShowBatteryButton
        )
    }

    /**
     * Checks if the app is exempted from battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        return if (sdkInt >= Build.VERSION_CODES.M) {
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to query isIgnoringBatteryOptimizations", e)
                true
            }
        } else {
            true
        }
    }

    /**
     * Retrieves the current progressive battery kill count.
     */
    fun getBatteryKillCount(context: Context): Int {
        return try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.getInt(PREF_BATTERY_KILL_COUNT, 0)
        } catch (e: Throwable) {
            0
        }
    }

    /**
     * Increments and returns the progressive battery kill count.
     */
    fun incrementBatteryKillCount(context: Context): Int {
        return try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val current = prefs.getInt(PREF_BATTERY_KILL_COUNT, 0)
            val updated = current + 1
            prefs.edit().putInt(PREF_BATTERY_KILL_COUNT, updated).apply()
            updated
        } catch (e: Throwable) {
            1
        }
    }

    /**
     * Resets the progressive battery kill count back to 0.
     */
    fun resetBatteryKillCount(context: Context) {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit().putInt(PREF_BATTERY_KILL_COUNT, 0).apply()
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to reset battery kill count", e)
        }
    }

    /**
     * Opens system battery optimization settings so the athlete can whitelist the app.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Throwable) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (e2: Throwable) {
                Log.e(TAG, "Failed to launch battery settings or application details", e2)
            }
        }
    }
}
