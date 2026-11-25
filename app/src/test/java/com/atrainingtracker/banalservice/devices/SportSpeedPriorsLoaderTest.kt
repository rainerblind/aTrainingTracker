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

import android.content.ContentValues
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.odometry.SportSpeedPriors
import com.atrainingtracker.banalservice.sensor.MySensorManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Robolectric tests verifying off-main-thread `SportTypes` speed-prior loading, placeholder
 * filtering, `SpeedAndLocationDevice_ATTFused` propagation + shutdown safety, and `SDK_INT == 0`
 * plain-JVM isolation (`REQ-FIL-047.1`, `TST-FIL-047.1`, `ATT-9694`).
 */
// Linked test specs: TST-FIL-047
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SportSpeedPriorsLoaderTest {

    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        SportSpeedPriorsLoader.resetForTesting()
        SportTypeDatabaseManager.resetForTesting(null)
        appContext.deleteDatabase(SportTypeDatabaseManager.SportTypeDbHelper.DB_NAME)
    }

    @After
    fun tearDown() {
        SportSpeedPriorsLoader.resetForTesting()
        SportTypeDatabaseManager.resetForTesting(null)
        appContext.deleteDatabase(SportTypeDatabaseManager.SportTypeDbHelper.DB_NAME)
    }

    @Test
    fun loadAsync_executesOffMainThread_excludesPlaceholders_andPropagatesIntoAttFused_tstFil047_1() {
        val dbMgr = SportTypeDatabaseManager.getInstance(appContext)
        // Add custom user BIKE sport "Einkaufen" [2.78, 5.56] and custom walking sport "Wandern" [0.1, 2.2]
        dbMgr.updateSportType(-1L, "Einkaufen", BSportType.BIKE, 2.78, 5.56, "Ride", "Biking", "bike")
        dbMgr.updateSportType(-1L, "Wandern", BSportType.RUN, 0.1, 2.2, "Hike", "Walking", "walk")
        // Insert a restored placeholder row that MUST be ignored (IS_PLACEHOLDER = 1)
        val placeholderValues = ContentValues().apply {
            put(SportTypeDatabaseManager.SportType.UI_NAME, "PlaceholderBike")
            put(SportTypeDatabaseManager.SportType.BASE_SPORT_TYPE, BSportType.BIKE.name)
            put(SportTypeDatabaseManager.SportType.MIN_AVG_SPEED, 1.85)
            put(SportTypeDatabaseManager.SportType.MAX_AVG_SPEED, 3.0)
            put(SportTypeDatabaseManager.SportType.IS_PLACEHOLDER, 1)
        }
        SportTypeDatabaseManager.SportTypeDbHelper(appContext).writableDatabase.use { db ->
            db.insert(SportTypeDatabaseManager.SportType.TABLE, null, placeholderValues)
        }

        // 1. Verify real daemon background thread execution (strictly off main thread)
        val latch = CountDownLatch(1)
        val loadedRef = AtomicReference<SportSpeedPriors>()
        val workerThreadRef = AtomicReference<Thread>()
        SportSpeedPriorsLoader.loadAsync(appContext) { priors ->
            workerThreadRef.set(Thread.currentThread())
            loadedRef.set(priors)
            latch.countDown()
        }
        assertTrue("Expected async SportSpeedPriors callback within 5 s", latch.await(5, TimeUnit.SECONDS))
        assertNotEquals("Loader must execute off main thread", Looper.getMainLooper().thread, workerThreadRef.get())
        assertEquals(2.2, loadedRef.get().maxWalkSpeedMps, 1e-6)
        assertEquals(2.78, loadedRef.get().minBikeSpeedMps, 1e-6)
        assertEquals(4.0, loadedRef.get().maxRunSpeedMps, 1e-6)

        // 2. Verify deterministic propagation into SpeedAndLocationDevice_ATTFused on init & onAccumulatorsReset()
        SportSpeedPriorsLoader.executorOverride = Executor { it.run() }
        val device = object : SpeedAndLocationDevice_ATTFused(appContext, MySensorManager(appContext)) {
            fun triggerResetForTest() = onAccumulatorsReset()
        }
        try {
            assertEquals(2.2, device.carriageClassifier.currentSportSpeedPriors.maxWalkSpeedMps, 1e-6)
            assertEquals(2.78, device.carriageClassifier.currentSportSpeedPriors.minBikeSpeedMps, 1e-6)
            assertEquals(2.78, device.sportDiscriminator.currentSportSpeedPriors.minBikeSpeedMps, 1e-6)
            assertEquals(2.78, device.multisportDetector.currentSportSpeedPriors.minBikeSpeedMps, 1e-6)

            // Mutate SportTypes DB and verify onAccumulatorsReset() refreshes priors
            dbMgr.updateSportType(-1L, "CityBike", BSportType.BIKE, 2.30, 5.0, "Ride", "Biking", "bike")
            device.triggerResetForTest()
            assertEquals(2.30, device.carriageClassifier.currentSportSpeedPriors.minBikeSpeedMps, 1e-6)
            assertEquals(2.30, device.sportDiscriminator.currentSportSpeedPriors.minBikeSpeedMps, 1e-6)
            assertEquals(2.30, device.multisportDetector.currentSportSpeedPriors.minBikeSpeedMps, 1e-6)
        } finally {
            device.shutDown()
        }
    }

    @Test
    fun loadAsync_skipsBackgroundDispatchWhenSdkIntIsZero_andIgnoresLateCallbackAfterShutdown_tstFil047_1() {
        val executed = AtomicBoolean(false)
        SportSpeedPriorsLoader.sdkIntProvider = { 0 }
        SportSpeedPriorsLoader.executorOverride = Executor {
            executed.set(true)
            it.run()
        }

        SportSpeedPriorsLoader.loadAsync(appContext) {
            executed.set(true)
        }
        assertFalse("SDK_INT == 0 plain-JVM guard must synchronously skip executor dispatch", executed.get())

        // Verify volatile mIsShuttingDown prevents late callback from mutating priors after shutDown()
        SportSpeedPriorsLoader.resetForTesting()
        var pendingTask: Runnable? = null
        SportSpeedPriorsLoader.executorOverride = Executor { pendingTask = it }
        val dbMgr = SportTypeDatabaseManager.getInstance(appContext)
        dbMgr.updateSportType(-1L, "Einkaufen", BSportType.BIKE, 2.78, 5.56, "Ride", "Biking", "bike")

        val device = SpeedAndLocationDevice_ATTFused(appContext, MySensorManager(appContext))
        assertEquals(SportSpeedPriors.DEFAULT, device.multisportDetector.currentSportSpeedPriors)
        device.shutDown()
        pendingTask?.run()
        assertEquals(
            "Late loader callback after shutDown() must not overwrite priors",
            SportSpeedPriors.DEFAULT,
            device.multisportDetector.currentSportSpeedPriors
        )
    }
}
