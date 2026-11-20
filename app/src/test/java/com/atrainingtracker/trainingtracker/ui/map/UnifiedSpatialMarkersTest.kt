/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.map

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric verification for [createSensorMarker] unified spatial marker drawables and
 * standardized [TTColor] palette (`REQ-SET-041`, `TST-SET-031`, `REQ-PRO-133`).
 */
// Linked test specs: TST-SET-031
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UnifiedSpatialMarkersTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(BitmapDescriptorFactory::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun createSensorMarker_rendersUnifiedStartEndAndApexMarkers_TST_SET_031() {
        // TST-SET-031
        val startDesc = mockk<BitmapDescriptor>(relaxed = true)
        val endDesc = mockk<BitmapDescriptor>(relaxed = true)
        val apexDesc = mockk<BitmapDescriptor>(relaxed = true)
        every { BitmapDescriptorFactory.fromBitmap(any()) } returnsMany listOf(startDesc, endDesc, apexDesc)

        val actualStart = createSensorMarker(context, R.drawable.control_start, TTColor.StartPoint)
        val actualEnd = createSensorMarker(context, R.drawable.control_stop, TTColor.EndPoint)
        val actualApex = createSensorMarker(context, R.drawable.ic_distance, TTColor.ApexPoint)

        assertNotNull("Start marker descriptor must be created", actualStart)
        assertNotNull("End marker descriptor must be created", actualEnd)
        assertNotNull("Apex marker descriptor must be created", actualApex)
        assertEquals(startDesc, actualStart)
        assertEquals(endDesc, actualEnd)
        assertEquals(apexDesc, actualApex)
        assertNotEquals(TTColor.StartPoint, TTColor.EndPoint)
        assertNotEquals(TTColor.StartPoint, TTColor.ApexPoint)
        assertNotEquals(TTColor.EndPoint, TTColor.ApexPoint)
    }
}
