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
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.odometry.KinematicSportTypeDiscriminator
import com.atrainingtracker.banalservice.odometry.MultisportTransitionDetector
import com.atrainingtracker.banalservice.odometry.PhoneCarriageClassifier
import com.atrainingtracker.banalservice.odometry.SportSpeedPriors
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Off-main-thread loader bridging [SportTypeDatabaseManager] and live odometry classifiers
 * (`REQ-FIL-047.1`, `ATT-9694`).
 *
 * Synchronously no-ops when `Build.VERSION.SDK_INT == 0` so plain-JVM unit tests with mocked
 * Android [Context] instances never spawn background SQLite work (`Coding Standards §5.5`).
 */
@Suppress("TooGenericExceptionCaught")
internal object SportSpeedPriorsLoader {

    private val defaultExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "att-sport-priors").apply { isDaemon = true }
    }

    @Volatile
    @VisibleForTesting
    internal var executorOverride: Executor? = null

    @Volatile
    @VisibleForTesting
    internal var sdkIntProvider: () -> Int = ::defaultSdkInt

    private fun defaultSdkInt(): Int = try {
        Build.VERSION.SDK_INT
    } catch (_: Throwable) {
        0
    }

    fun loadAsync(context: Context, onLoaded: (SportSpeedPriors) -> Unit) {
        if (sdkIntProvider() == 0) return
        val appContext = try {
            context.applicationContext ?: context
        } catch (_: Throwable) {
            context
        }
        val executor = executorOverride ?: defaultExecutor
        try {
            executor.execute {
                val priors = loadBlocking(appContext)
                try {
                    onLoaded(priors)
                } catch (_: Throwable) {
                    // Protect daemon thread from caller exceptions.
                }
            }
        } catch (_: Throwable) {
            // Keep canonical SportSpeedPriors.DEFAULT on executor rejection.
        }
    }

    fun loadAsyncInto(
        context: Context,
        carriageClassifier: PhoneCarriageClassifier,
        sportDiscriminator: KinematicSportTypeDiscriminator,
        multisportDetector: MultisportTransitionDetector,
        isShuttingDown: () -> Boolean
    ) {
        loadAsync(context) { priors ->
            if (!isShuttingDown()) {
                carriageClassifier.setSportSpeedPriors(priors)
                sportDiscriminator.setSportSpeedPriors(priors)
                multisportDetector.setSportSpeedPriors(priors)
            }
        }
    }

    internal fun loadBlocking(context: Context): SportSpeedPriors = try {
        val bands = SportTypeDatabaseManager.getInstance(context).activeSportTypeSpeedBands
        SportSpeedPriors.fromBands(bands)
    } catch (_: Throwable) {
        SportSpeedPriors.DEFAULT
    }

    @VisibleForTesting
    internal fun resetForTesting() {
        executorOverride = null
        sdkIntProvider = ::defaultSdkInt
    }
}
