/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.clusters

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodMarkerType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric DataStore persistence verification for selectable cluster markers and isolation
 * from Periods marker preferences (`REQ-SET-060`, `TST-SET-045`, `REQ-PRO-133`).
 */
// Linked test specs: TST-SET-045
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkoutClustersSelectableMarkersTest {

    private lateinit var context: Context
    private lateinit var prefManager: MyPreferenceManager

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        prefManager = MyPreferenceManager.createForTesting(context)
        ClusterMarkerType.entries.forEach { prefManager.setClusterMarkerTypeEnabled(it.name, true) }
        PeriodMarkerType.entries.forEach { prefManager.setPeriodMarkerTypeEnabled(it.name, true) }
    }

    @Test
    fun setClusterMarkerTypeEnabled_persistsAcrossInstancesAndIsolatesPeriodPrefs_TST_SET_045() = runBlocking {
        // TST-SET-045
        val initialCluster = prefManager.enabledClusterMarkerTypesFlow.first()
        assertTrue(initialCluster.contains(ClusterMarkerType.START.name))

        // Uncheck START in cluster preferences
        prefManager.setClusterMarkerTypeEnabled(ClusterMarkerType.START.name, false)

        val updatedCluster = prefManager.enabledClusterMarkerTypesFlow.first()
        assertFalse("START must be unchecked in cluster marker preferences", updatedCluster.contains(ClusterMarkerType.START.name))
        assertTrue("END must remain checked", updatedCluster.contains(ClusterMarkerType.END.name))

        // Verify independent persistence in a fresh MyPreferenceManager instance (simulated restart)
        val reloadedManager = MyPreferenceManager.createForTesting(context)
        val reloadedCluster = reloadedManager.enabledClusterMarkerTypesFlow.first()
        assertFalse("Unchecked START must persist across restarts", reloadedCluster.contains(ClusterMarkerType.START.name))

        // Verify Periods marker preferences remain unaffected
        val periodMarkers = reloadedManager.enabledPeriodMarkerTypesFlow.first()
        assertTrue("Period START marker preference must remain isolated", periodMarkers.contains(PeriodMarkerType.START.name))

        // Re-check START
        reloadedManager.setClusterMarkerTypeEnabled(ClusterMarkerType.START.name, true)
        assertTrue(reloadedManager.enabledClusterMarkerTypesFlow.first().contains(ClusterMarkerType.START.name))
    }
}
