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

package com.atrainingtracker.trainingtracker.ui.branding

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Manages dynamic homescreen launcher icon switches via Android <activity-alias> (REQ-UI-445 / ATT-1406).
 *
 * System Invariant (Non-Disruptive Deferred Activation):
 * Component switches via [PackageManager.setComponentEnabledSetting] with [PackageManager.DONT_KILL_APP]
 * are deferred until the application moves to background ([ProcessLifecycleOwner] [onStop]) AND no active
 * workout recording is running (![TrainingApplication.isTracking]). Active sessions and workouts are NEVER interrupted.
 */
class LauncherIconManager @VisibleForTesting constructor(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore = SettingsDataStore.getInstance(context),
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val isTrackingProvider: () -> Boolean = { TrainingApplication.isTracking() }
) : DefaultLifecycleObserver {

    private var isObserverRegistered = false

    @Synchronized
    fun init() {
        if (!isObserverRegistered) {
            try {
                ProcessLifecycleOwner.get().lifecycle.addObserver(this)
                isObserverRegistered = true
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to register ProcessLifecycleOwner observer", t)
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!isTrackingProvider.invoke()) {
            coroutineScope.launch(ioDispatcher) {
                applyDesiredLauncherIcon()
            }
        }
    }

    /**
     * Resolves the persisted icon preference from [SettingsDataStore] and applies it to the PackageManager.
     */
    suspend fun applyDesiredLauncherIcon(): Boolean {
        val selectedId = settingsDataStore.selectedLauncherIcon.first()
        val targetVariant = LauncherIconVariant.fromId(selectedId)
        return applyLauncherIcon(targetVariant)
    }

    /**
     * Applies the target launcher icon alias and disables all other aliases with [PackageManager.DONT_KILL_APP].
     */
    fun applyLauncherIcon(targetVariant: LauncherIconVariant): Boolean {
        return try {
            val packageManager = context.packageManager
            val packageName = context.packageName

            var stateChanged = false
            for (variant in LauncherIconVariant.entries) {
                val component = ComponentName(packageName, variant.aliasName)
                val isTarget = variant == targetVariant
                val desiredState = if (isTarget) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }

                val currentState = packageManager.getComponentEnabledSetting(component)
                if (currentState != desiredState) {
                    packageManager.setComponentEnabledSetting(
                        component,
                        desiredState,
                        PackageManager.DONT_KILL_APP
                    )
                    stateChanged = true
                }
            }
            stateChanged
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to update launcher icon component state", t)
            false
        }
    }

    companion object {
        private const val TAG = "LauncherIconManager"

        @Volatile
        private var instance: LauncherIconManager? = null

        fun getInstance(context: Context): LauncherIconManager =
            instance ?: synchronized(this) {
                instance ?: LauncherIconManager(context.applicationContext).also {
                    instance = it
                }
            }
    }
}
