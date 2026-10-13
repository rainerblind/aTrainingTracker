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

package com.atrainingtracker.trainingtracker.notifications

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.SavedStateHandle
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicelist.BatteryFilter
import com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceFilterCriteria
import com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedViewModel
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying notification deep-linking and cold-start battery filter pre-selection
 * (ATT-3166 / REQ-UI-351 / TST-UI-311.4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationDeepLinkTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context = mockk<Context>(relaxed = true)
    private val notificationManager = mockk<NotificationManagerCompat>(relaxed = true)
    private val app = mockk<Application>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkObject(DeviceDataRepository.Companion)
        mockkObject(BANALServiceRepository.Companion)
        every { DeviceDataRepository.getInstance(any()) } returns mockk(relaxed = true)
        every { BANALServiceRepository.getInstance(any()) } returns mockk(relaxed = true)

        every { notificationManager.areNotificationsEnabled() } returns true
        every { context.packageName } returns "com.atrainingtracker"
        every { context.applicationContext } returns context
        every { app.applicationContext } returns app
        every { app.packageName } returns "com.atrainingtracker"
        every { app.registerReceiver(any(), any(), any()) } returns null
        every { app.registerReceiver(any(), any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test
    fun lowBatteryNotification_pendingIntent_containsDeepLinkAndFilterExtras() {
        val mockIntent = mockk<Intent>(relaxed = true)
        val passedExtras = mutableMapOf<String, Any?>()

        every { mockIntent.putExtra(any(), any<String>()) } answers {
            passedExtras[firstArg()] = secondArg<String>()
            mockIntent
        }
        every { mockIntent.putExtra(any(), any<Int>()) } answers {
            passedExtras[firstArg()] = secondArg<Int>()
            mockIntent
        }
        every { mockIntent.putExtra(any(), any<Boolean>()) } answers {
            passedExtras[firstArg()] = secondArg<Boolean>()
            mockIntent
        }

        val manager = SensorBatteryNotificationManager(
            context = context,
            notificationManager = notificationManager,
            pendingIntentFactory = { _ ->
                mockIntent.apply {
                    putExtra(MainActivityWithNavigation.SELECTED_FRAGMENT, MainActivityWithNavigation.SelectedFragment.SENSORS.name)
                    putExtra(MainActivityWithNavigation.EXTRA_DRAWER_ITEM_ID, R.id.drawer_my_sensors)
                    putExtra(MainActivityWithNavigation.EXTRA_FILTER_CRITICAL_BATTERY, true)
                }
                mockk<PendingIntent>(relaxed = true)
            }
        ).apply {
            notificationFactory = { mockk(relaxed = true) }
        }

        val devices = listOf(
            DevicesDatabaseManager.NameAndBatteryPercentage("Polar H10", 10, 101L)
        )
        manager.showLowBatteryNotification(devices)

        assertEquals(MainActivityWithNavigation.SelectedFragment.SENSORS.name, passedExtras[MainActivityWithNavigation.SELECTED_FRAGMENT])
        assertEquals(R.id.drawer_my_sensors, passedExtras[MainActivityWithNavigation.EXTRA_DRAWER_ITEM_ID])
        assertEquals(true, passedExtras[MainActivityWithNavigation.EXTRA_FILTER_CRITICAL_BATTERY])
    }

    @Test
    fun devicesTabbedViewModel_initializedWithExtraFilterCriticalBattery_preselectsCriticalBatteryFilter() {
        val savedStateHandle = SavedStateHandle(mapOf(
            DevicesTabbedViewModel.EXTRA_FILTER_CRITICAL_BATTERY to true
        ))

        val viewModel = DevicesTabbedViewModel(
            application = app,
            savedStateHandle = savedStateHandle
        )

        assertEquals(BatteryFilter.CRITICAL_ONLY, viewModel.filterCriteria.value.batteryFilter)
        assertEquals(1, viewModel.filterCriteria.value.activeFilterCount)
    }

    @Test
    fun devicesTabbedViewModel_setCriticalBatteryFilter_updatesCriteriaReactively() {
        val viewModel = DevicesTabbedViewModel(
            application = app,
            savedStateHandle = SavedStateHandle()
        )

        assertEquals(BatteryFilter.ALL, viewModel.filterCriteria.value.batteryFilter)

        viewModel.setCriticalBatteryFilter()
        assertEquals(BatteryFilter.CRITICAL_ONLY, viewModel.filterCriteria.value.batteryFilter)

        viewModel.clearFilterCriteria()
        assertEquals(BatteryFilter.ALL, viewModel.filterCriteria.value.batteryFilter)
    }
}
