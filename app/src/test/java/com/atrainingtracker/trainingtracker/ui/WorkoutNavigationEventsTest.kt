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

package com.atrainingtracker.trainingtracker.ui

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

/**
 * Unit tests verifying [WorkoutNavigationEvents] decoupled event bus behavior (REQ-SET-058, TST-SET-057, ATT-503).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutNavigationEventsTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })
        WorkoutNavigationEvents.reset()
        WorkoutNavigationEvents.resetCluster()
    }

    @After
    fun tearDown() {
        WorkoutNavigationEvents.reset()
        WorkoutNavigationEvents.resetCluster()
        testDispatcher.scheduler.advanceUntilIdle()
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
    }

    @Test
    fun triggerCluster_emitsClusterIdToFlowAndLiveData() = runTest(testDispatcher) {
        val observer = androidx.lifecycle.Observer<Long?> {}
        WorkoutNavigationEvents.navigateToClusterLiveData.observeForever(observer)
        try {
            WorkoutNavigationEvents.triggerCluster(42L)
            testScheduler.advanceUntilIdle()

            val flowValue = WorkoutNavigationEvents.navigateToCluster.first()
            assertEquals(42L, flowValue)

            val liveDataValue = WorkoutNavigationEvents.navigateToClusterLiveData.value
            assertEquals(42L, liveDataValue)
        } finally {
            WorkoutNavigationEvents.navigateToClusterLiveData.removeObserver(observer)
            testScheduler.advanceTimeBy(6000)
            testScheduler.runCurrent()
        }
    }

    @Test
    fun resetCluster_clearsActiveClusterId() = runTest(testDispatcher) {
        val observer = androidx.lifecycle.Observer<Long?> {}
        WorkoutNavigationEvents.navigateToClusterLiveData.observeForever(observer)
        try {
            WorkoutNavigationEvents.triggerCluster(100L)
            testScheduler.advanceUntilIdle()
            assertEquals(100L, WorkoutNavigationEvents.navigateToCluster.first())
            assertEquals(100L, WorkoutNavigationEvents.navigateToClusterLiveData.value)

            WorkoutNavigationEvents.resetCluster()
            testScheduler.advanceUntilIdle()
            val flowValue = WorkoutNavigationEvents.navigateToCluster.first()
            assertNull(flowValue)
            assertNull(WorkoutNavigationEvents.navigateToClusterLiveData.value)
        } finally {
            WorkoutNavigationEvents.navigateToClusterLiveData.removeObserver(observer)
            testScheduler.advanceTimeBy(6000)
            testScheduler.runCurrent()
        }
    }

    @Test
    fun triggerEdit_remainsIndependentFromClusterNavigation() = runTest(testDispatcher) {
        WorkoutNavigationEvents.triggerEdit(77L)
        WorkoutNavigationEvents.triggerCluster(88L)

        assertEquals(77L, WorkoutNavigationEvents.navigateToEdit.first())
        assertEquals(88L, WorkoutNavigationEvents.navigateToCluster.first())

        WorkoutNavigationEvents.resetCluster()
        assertNull(WorkoutNavigationEvents.navigateToCluster.first())
        assertEquals(77L, WorkoutNavigationEvents.navigateToEdit.first())
    }

    @Test
    fun triggerLowBatteryAlert_emitsAlertAndClearsOnConsume() = runTest(testDispatcher) {
        val alert = WorkoutNavigationEvents.LowBatteryAlert(
            message = "⚠️ HRM-Pro: Akku schwach (15%)",
            deviceNames = listOf("HRM-Pro")
        )

        WorkoutNavigationEvents.triggerLowBatteryAlert(alert)
        testScheduler.advanceUntilIdle()

        val emitted = WorkoutNavigationEvents.lowBatteryAlert.first()
        assertEquals("⚠️ HRM-Pro: Akku schwach (15%)", emitted?.message)
        assertEquals(listOf("HRM-Pro"), emitted?.deviceNames)

        WorkoutNavigationEvents.consumeLowBatteryAlert()
        testScheduler.advanceUntilIdle()

        assertNull(WorkoutNavigationEvents.lowBatteryAlert.first())
    }
}
