/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import android.app.Application
import androidx.compose.ui.graphics.Color
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.sensor.SensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.sensor.formater.DefaultStringFormatter
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.LiveSegmentsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Objects

/**
 * Unit test verifying non-null string formatter fallback and defensive null safety
 * in TrackingViewModel.applySensorData() (REQ-TRK-049.5, TST-TRK-049.5b, ATT-5080).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackingViewModelSensorNullSafetyTest {

    private lateinit var viewModel: TrackingViewModel

    @Before
    fun setUp() {
        val app = mockk<Application>(relaxed = true)
        val trackingViewsRepo = mockk<TrackingViewsRepository>(relaxed = true)
        val banalServiceRepo = mockk<BANALServiceRepository>(relaxed = true)
        val liveSegmentsRepo = mockk<LiveSegmentsRepository>(relaxed = true)
        val routesRepo = mockk<RoutesRepository>(relaxed = true)
        val clusterRepo = mockk<WorkoutClusterRepository>(relaxed = true)

        viewModel = TrackingViewModel(
            application = app,
            trackingViewsRepository = trackingViewsRepo,
            banalServiceRepository = banalServiceRepo,
            liveSegmentsRepository = liveSegmentsRepo,
            routesRepository = routesRepo,
            viewId = 1L,
            workoutClusterRepository = clusterRepo
        )
    }

    @Test
    fun defaultStringFormatter_formatNull_returnsDashFallback() {
        val formatter = DefaultStringFormatter()
        assertEquals("--", formatter.format(null))
        assertEquals("test_val", formatter.format("test_val"))
    }

    @Test
    fun sensorData_getStringValue_nullString_returnsDashFallback() {
        val sensorData = SensorData<String>(SensorType.TIME_OF_DAY, null, null, "clock_device")
        assertEquals("--", sensorData.stringValue)

        val filteredData = FilteredSensorData<String>(
            SensorType.TIME_OF_DAY,
            null,
            null,
            "clock_device",
            FilterType.INSTANTANEOUS,
            1.0
        )
        assertEquals("--", filteredData.stringValue)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun applySensorData_nullStringValue_defensivelyDefaultsToDashWithoutCrashing() {
        val configHash = Objects.hash(SensorType.TIME_OF_DAY, FilterType.INSTANTANEOUS, 1.0, "clock_device")
        val baseField = SensorFieldState(
            configHash = configHash,
            sensorFieldId = 1L,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Time of Day",
            filterDescription = "None",
            value = "10:15:00",
            units = "",
            zoneColor = Color.Transparent
        )

        // Raw filtered sensor data simulating uninitialized / null stringValue
        val nullFilteredData = FilteredSensorData<String>(
            SensorType.TIME_OF_DAY,
            null,
            null,
            "clock_device",
            FilterType.INSTANTANEOUS,
            1.0
        )

        val applyMethod = TrackingViewModel::class.java.getDeclaredMethod(
            "applySensorData",
            List::class.java,
            List::class.java,
            ActivityType::class.java
        )
        applyMethod.isAccessible = true

        val result = applyMethod.invoke(
            viewModel,
            listOf(baseField),
            listOf(nullFilteredData),
            ActivityType.RUN_SPEED_AND_CADENCE
        ) as List<SensorFieldState>

        assertNotNull(result)
        assertEquals(1, result.size)
        // Value must update to non-null fallback "--" without NPE
        assertEquals("--", result[0].value)
    }
}
