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

package com.atrainingtracker.trainingtracker.ui.map.offline

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Defensive, idempotent bootstrap for Mapsforge's [AndroidGraphicFactory] (`REQ-MAP-071.1`).
 *
 * Guards plain-JVM unit tests (`Build.VERSION.SDK_INT == 0`) and mocked contexts so
 * `TrainingApplication.onCreate()` and offline map helpers never fail with unmocked Android
 * framework errors (`Coding Standards §5.5`).
 */
object OfflineMapGraphicInitializer {
    private const val TAG = "OfflineMapGraphicInit"

    private val initialized = AtomicBoolean(false)

    /**
     * Initializes [AndroidGraphicFactory] at most once on real Android / Robolectric runtimes.
     *
     * @param application Host Android [Application] context (nullable for defensive callers).
     * @param sdkInt Injectable Android SDK level (defaults to [Build.VERSION.SDK_INT]).
     * @param initializer Factory invocation hook for deterministic testing.
     * @return `true` when [AndroidGraphicFactory] is initialized and ready, `false` otherwise.
     */
    @JvmStatic
    @JvmOverloads
    fun initialize(
        application: Application?,
        sdkInt: Int = Build.VERSION.SDK_INT,
        initializer: (Application) -> Unit = { app -> AndroidGraphicFactory.createInstance(app) }
    ): Boolean {
        if (application == null || sdkInt <= 0) {
            return false
        }
        if (initialized.get()) {
            return true
        }
        return synchronized(this) {
            if (initialized.get()) {
                return@synchronized true
            }
            try {
                initializer(application)
                initialized.set(true)
                true
            } catch (t: Throwable) {
                safeLogWarn("Failed to initialize Mapsforge AndroidGraphicFactory", t)
                false
            }
        }
    }

    /** Returns `true` if [AndroidGraphicFactory] has been successfully initialized. */
    @JvmStatic
    fun isInitialized(): Boolean = initialized.get()

    @VisibleForTesting
    internal fun resetForTesting() {
        synchronized(this) {
            initialized.set(false)
        }
    }

    private fun safeLogWarn(message: String, t: Throwable) {
        try {
            Log.w(TAG, message, t)
        } catch (_: Throwable) {
            // Unmocked android.util.Log in plain JVM tests
        }
    }
}
