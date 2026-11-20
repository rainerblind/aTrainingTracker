/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.database

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric SQLite verification for [WorkoutClusterDatabaseManager.getDatabase]
 * persistent connection lifecycle (`REQ-SET-036`, `TST-SET-027`, `REQ-PRO-133`).
 */
// Linked test specs: TST-SET-027
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkoutClusterDatabaseConnectionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkoutClusterDatabaseManager.resetForTesting(null)
        context.deleteDatabase("RouteClusters.db")
    }

    @After
    fun tearDown() {
        WorkoutClusterDatabaseManager.resetForTesting(null)
        context.deleteDatabase("RouteClusters.db")
    }

    @Test
    fun getDatabase_remainsOpenAcrossSequentialOperationsAndReopensIfClosed_TST_SET_027() {
        // TST-SET-027
        val manager = WorkoutClusterDatabaseManager.getInstance(context)
        val initialDb = manager.getDatabase()
        assertTrue("Initial DB connection must be open", initialDb.isOpen)

        val insertedId = manager.insertCluster(
            WorkoutCluster(
                name = "Persistent Connection Loop",
                probableSportId = 1L,
                startLat = 48.1,
                startLng = 11.5,
                endLat = 48.1,
                endLng = 11.5,
                maxDispLat = 48.2,
                maxDispLng = 11.6,
                refDistance = 10000.0,
                hitCount = 1,
                bSportType = BSportType.BIKE
            )
        )
        assertTrue(insertedId > 0L)
        assertEquals(1, manager.getAllClusters().size)
        assertTrue("DB connection must remain open after CRUD queries", initialDb.isOpen)
        assertSame("Subsequent getDatabase() calls must reuse open connection", initialDb, manager.getDatabase())

        // Simulate external close (e.g. backup/restore), verify automatic reopen
        initialDb.close()
        val reopenedDb = manager.getDatabase()
        assertTrue("getDatabase() must automatically reopen a closed DB", reopenedDb.isOpen)
        assertEquals(1, manager.getAllClusters().size)
    }
}
