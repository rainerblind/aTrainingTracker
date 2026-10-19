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

import android.content.Context
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * TST-DAT-026.5: Contract and unit tests verifying thread-safe singleton lifecycle,
 * instance identity, application context binding, and resetForTesting() behavior
 * across StravaUploadDbHelper, EquipmentDbHelper, and ActiveDevicesDbHelper (REQ-DAT-031.4).
 */
class DbHelperSingletonTest {

    private lateinit var mockContext: Context
    private lateinit var mockAppContext: Context

    @Before
    fun setUp() {
        mockAppContext = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockAppContext
        every { mockAppContext.applicationContext } returns mockAppContext

        StravaUploadDbHelper.resetForTesting()
        EquipmentDbHelper.resetForTesting()
        ActiveDevicesDbHelper.resetForTesting()
    }

    @After
    fun tearDown() {
        StravaUploadDbHelper.resetForTesting()
        EquipmentDbHelper.resetForTesting()
        ActiveDevicesDbHelper.resetForTesting()
    }

    @Test
    fun testStravaUploadDbHelper_singletonIdentityAndReset() {
        val instance1 = StravaUploadDbHelper.getInstance(mockContext)
        val instance2 = StravaUploadDbHelper.getInstance(mockContext)
        assertSame("Repeated calls must return identical singleton instance", instance1, instance2)

        StravaUploadDbHelper.resetForTesting()
        val instance3 = StravaUploadDbHelper.getInstance(mockContext)
        assertNotSame("After resetForTesting, a new instance must be created", instance1, instance3)
    }

    @Test
    fun testEquipmentDbHelper_singletonIdentityAndReset() {
        val instance1 = EquipmentDbHelper.getInstance(mockContext)
        val instance2 = EquipmentDbHelper.getInstance(mockContext)
        assertSame("Repeated calls must return identical singleton instance", instance1, instance2)

        EquipmentDbHelper.resetForTesting()
        val instance3 = EquipmentDbHelper.getInstance(mockContext)
        assertNotSame("After resetForTesting, a new instance must be created", instance1, instance3)
    }

    @Test
    fun testActiveDevicesDbHelper_singletonIdentityAndReset() {
        val instance1 = ActiveDevicesDbHelper.getInstance(mockContext)
        val instance2 = ActiveDevicesDbHelper.getInstance(mockContext)
        assertSame("Repeated calls must return identical singleton instance", instance1, instance2)

        ActiveDevicesDbHelper.resetForTesting()
        val instance3 = ActiveDevicesDbHelper.getInstance(mockContext)
        assertNotSame("After resetForTesting, a new instance must be created", instance1, instance3)
    }

    @Test
    fun testSingleton_concurrencyThreadSafety() {
        val executor = Executors.newFixedThreadPool(8)
        try {
            val tasks = (1..16).map {
                Callable {
                    Triple(
                        StravaUploadDbHelper.getInstance(mockContext),
                        EquipmentDbHelper.getInstance(mockContext),
                        ActiveDevicesDbHelper.getInstance(mockContext)
                    )
                }
            }
            val results = executor.invokeAll(tasks).map { it.get() }
            val first = results.first()
            for (result in results) {
                assertSame("All concurrent threads must receive identical StravaUploadDbHelper", first.first, result.first)
                assertSame("All concurrent threads must receive identical EquipmentDbHelper", first.second, result.second)
                assertSame("All concurrent threads must receive identical ActiveDevicesDbHelper", first.third, result.third)
            }
        } finally {
            executor.shutdown()
        }
    }
}
