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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.smartwatch.pebble

import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.getpebble.android.kit.PebbleKit
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * Robolectric lifecycle tests for [PebbleService] verifying immediate exit when
 * Pebble appMessages are unsupported and clean teardown / PebbleView.db closure
 * (REQ-STB-024, TST-STB-024, TST-STB-024.1, TST-STB-024.2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PebbleServiceLifecycleTest {

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TrainingApplication.setAppContextForTesting(context)
        TrainingApplication.setSharedPreferencesForTesting(
            PreferenceManager.getDefaultSharedPreferences(context)
        )
        PebbleDatabaseManager.resetInstanceForTesting()
        context.deleteDatabase(PebbleDatabaseManager.PebbleDbHelper.DB_NAME)
    }

    @After
    fun tearDown() {
        PebbleDatabaseManager.resetInstanceForTesting()
        context.deleteDatabase(PebbleDatabaseManager.PebbleDbHelper.DB_NAME)
        TrainingApplication.setAppContextForTesting(null)
        TrainingApplication.setSharedPreferencesForTesting(null)
        unmockkAll()
    }

    /**
     * TST-STB-024.1: Real PebbleService on a host without com.getpebble.android.provider
     * stops itself immediately without binding BANALService, registering receivers,
     * opening PebbleView.db, or crashing in onDestroy().
     */
    @Test
    fun unsupportedHost_stopsImmediately_skipsBindReceiversAndDb_andDestroysCleanly() {
        val shadowApp = Shadows.shadowOf(context)
        val initialReceiverCount = shadowApp.registeredReceivers.size
        val initialBoundCount = shadowApp.boundServiceConnections.size

        val controller = Robolectric.buildService(PebbleService::class.java)
        val service = controller.create().get()
        val shadowService = Shadows.shadowOf(service)

        assertTrue("onCreate must call stopSelf() when appMessages unsupported", shadowService.isStoppedBySelf)
        assertFalse("BANALService must not be bound when unsupported", service.mIsBanalBound)
        assertFalse("Base receivers must not be registered when unsupported", service.mBaseReceiversRegistered)
        assertEquals(initialBoundCount, shadowApp.boundServiceConnections.size)
        assertEquals(initialReceiverCount, shadowApp.registeredReceivers.size)

        val startResult = service.onStartCommand(Intent(context, PebbleService::class.java), 0, 1)

        assertEquals(
            "onStartCommand must return START_NOT_STICKY when appMessages unsupported",
            Service.START_NOT_STICKY,
            startResult
        )
        assertFalse(
            "PebbleDatabaseManager must not initialize when appMessages unsupported",
            PebbleDatabaseManager.isInitialized()
        )
        assertFalse(
            "PebbleView.db file must not be created when appMessages unsupported",
            context.getDatabasePath(PebbleDatabaseManager.PebbleDbHelper.DB_NAME).exists()
        )
        assertFalse("PebbleKit receivers must remain unregistered", service.mReceiversRegistered)

        // Must not throw IllegalArgumentException from unguarded unbindService or unregisterReceiver
        controller.destroy()
    }

    /**
     * TST-STB-024.1: Mid-session loss of appMessages support inside startPebbleWatchApp()
     * invokes stopSelf() before opening PebbleView.db or registering PebbleKit handlers.
     */
    @Test
    fun midSessionUnsupported_startPebbleWatchApp_stopsSelfBeforeDbOrPebbleKit() {
        var supported = true
        val service = object : PebbleService() {
            override fun isAppMessagesSupported(): Boolean = supported
        }
        val controller = ServiceController.of(service, Intent(context, PebbleService::class.java))
        controller.create()
        assertTrue(service.mIsBanalBound)
        assertTrue(service.mBaseReceiversRegistered)

        supported = false
        service.startPebbleWatchApp()

        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)
        assertFalse(service.mReceiversRegistered)
        assertFalse(PebbleDatabaseManager.isInitialized())

        controller.destroy()
        assertFalse(service.mIsBanalBound)
        assertFalse(service.mBaseReceiversRegistered)
    }

    /**
     * TST-STB-024.2: When appMessages ARE supported, PebbleService remains START_STICKY,
     * populates mSensorTypeList & mRows via singleton PebbleDatabaseManager, resets
     * mReceiversRegistered in stopPebbleWatchApp(), and closes PebbleView.db cleanly on destroy.
     */
    @Test
    fun supportedHost_populatesRowsViaSingleton_resetsFlags_andClosesDbOnDestroy() {
        mockkStatic(PebbleKit::class)
        every { PebbleKit.startAppOnPebble(any<Context>(), any()) } just runs
        every { PebbleKit.closeAppOnPebble(any<Context>(), any()) } just runs
        every { PebbleKit.registerReceivedAckHandler(any(), any()) } returns null
        every { PebbleKit.registerReceivedNackHandler(any(), any()) } returns null
        every { PebbleKit.registerReceivedDataHandler(any(), any()) } returns null

        val service = object : PebbleService() {
            override fun isAppMessagesSupported(): Boolean = true
        }
        val controller = ServiceController.of(service, Intent(context, PebbleService::class.java))
        controller.create()

        assertTrue(service.mIsBanalBound)
        assertTrue(service.mBaseReceiversRegistered)

        val startResult = service.onStartCommand(Intent(context, PebbleService::class.java), 0, 1)
        assertEquals(Service.START_STICKY, startResult)
        assertTrue(service.mReceiversRegistered)

        val expectedRows = PebbleDatabaseManager.getInstance(context)
            .getSensorTypeList(PebbleDatabaseManager.getInstance(context).getFirstViewId(ActivityType.GENERIC_HR))
        assertEquals(3, expectedRows.size)
        assertEquals(expectedRows.size, service.mRows)
        assertTrue(PebbleDatabaseManager.getInstance(context).isDatabaseOpen())

        // Second startPebbleWatchApp() must not double-register PebbleKit receivers
        service.startPebbleWatchApp()
        verify(exactly = 1) { PebbleKit.registerReceivedDataHandler(any(), any()) }

        // stopPebbleWatchApp() resets mReceiversRegistered and guards repeated calls
        service.stopPebbleWatchApp()
        assertFalse(service.mReceiversRegistered)
        service.stopPebbleWatchApp()
        verify(exactly = 1) { PebbleKit.closeAppOnPebble(any(), any()) }

        // onDestroy() unbinds, unregisters base receivers, and closes PebbleView.db cleanly
        controller.destroy()
        assertFalse(service.mIsBanalBound)
        assertFalse(service.mBaseReceiversRegistered)
        assertFalse(
            "PebbleDatabaseManager must close PebbleView.db cleanly on PebbleService.onDestroy()",
            PebbleDatabaseManager.getInstance(context).isDatabaseOpen()
        )
        // Verify mUpdatePebbleFilter action wasn't left registered
        val shadowApp = Shadows.shadowOf(context)
        val lingeringUpdateReceivers = shadowApp.registeredReceivers.count {
            it.intentFilter.hasAction(BANALService.NEW_TIME_EVENT_INTENT)
        }
        assertEquals(0, lingeringUpdateReceivers)
    }
}
