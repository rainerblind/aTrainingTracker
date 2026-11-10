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

package com.atrainingtracker.trainingtracker.segments

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Immutability and pure-functional transformation tests for [LiveSegmentMath] and [LiveSegmentsRepository]
 * verifying compliance with REQ-MAP-065.2 and TST-MAP-067.2.
 */
class LiveSegmentsRepositoryImmutabilityTest {

    private lateinit var mockContext: Context
    private lateinit var mockSegmentsRepo: SegmentsRepository
    private lateinit var mockBanalRepo: BANALServiceRepository
    private lateinit var repository: LiveSegmentsRepository

    private val sampleSegment = SegmentWithPath(
        summary = SegmentSummary(
            stravaId = 55555L,
            name = "Immutability Test Hill",
            bSportType = BSportType.BIKE,
            climbCategory_raw = 0,
            climbCategory = "NC",
            prTime_raw = 120,
            prTime = "2:00",
            city = "Munich",
            distance = "1.0 km",
            distance_raw = 1000.0,
            averageGrade_raw = 5.0,
            averageGrade = "5%",
            maxGrade = "8%",
            elevationGain_raw = 50.0,
            elevationGain = "50 m",
            elevationMin = "500 m",
            elevationMax = "550 m",
            map_polyline = ""
        ),
        path = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(47.0, 10.0), altitude = 500.0),
            PathPoint(distance = 150.0, latLng = LatLng(47.001, 10.0), altitude = 508.0),
            PathPoint(distance = 300.0, latLng = LatLng(47.002, 10.0), altitude = 515.0),
            PathPoint(distance = 500.0, latLng = LatLng(47.004, 10.0), altitude = 525.0),
            PathPoint(distance = 700.0, latLng = LatLng(47.0055, 10.0), altitude = 535.0),
            PathPoint(distance = 850.0, latLng = LatLng(47.007, 10.0), altitude = 542.0),
            PathPoint(distance = 1000.0, latLng = LatLng(47.008, 10.0), altitude = 550.0)
        )
    )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        val mockPrefs = mockk<SharedPreferences>(relaxed = true)
        every { mockPrefs.getString(any(), any()) } returns "METRIC"
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", mockPrefs)

        mockContext = mockk(relaxed = true)
        mockSegmentsRepo = mockk(relaxed = true)
        mockBanalRepo = mockk(relaxed = true)

        every { mockSegmentsRepo.allSegmentsWithPath } returns MutableStateFlow(emptyList())
        every { mockBanalRepo.currentLocation } returns MutableStateFlow(null)
        every { mockBanalRepo.currentDistance } returns MutableStateFlow(0.0)
        every { mockBanalRepo.currentBearing } returns MutableStateFlow(0.0)

        repository = LiveSegmentsRepository(
            context = mockContext,
            segmentsRepository = mockSegmentsRepo,
            banalRepository = mockBanalRepo
        )
    }

    @After
    fun tearDown() {
        repository.cancelScope()
        unmockkStatic(Log::class)
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", null)
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        val field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(null, value)
    }

    /**
     * TST-MAP-067.2: Reflection verification that LiveSegmentMath fields are 100% final (val properties)
     * and no setter methods exist.
     */
    @Test
    fun testLiveSegmentMath_allDeclaredFieldsAreFinal_noSettersExist() {
        val mathClass = LiveSegmentMath::class.java

        // 1. All fields must be final
        for (field in mathClass.declaredFields) {
            // Skip synthetic fields generated by compiler (e.g. $jacocoData) if any
            if (field.isSynthetic) continue
            assertTrue(
                "Property '${field.name}' in LiveSegmentMath must be final (val)",
                Modifier.isFinal(field.modifiers)
            )
        }

        // 2. No setter methods should be generated
        val setters = mathClass.declaredMethods.filter { it.name.startsWith("set") }
        assertTrue(
            "LiveSegmentMath must not expose any setter methods, but found: ${setters.map { it.name }}",
            setters.isEmpty()
        )
    }

    /**
     * TST-MAP-067.2: Functional transformation verification that processLiveSegmentUpdate returns
     * new instances and leaves the input LiveSegment and LiveSegmentMath instances untouched.
     */
    @Test
    fun testProcessLiveSegmentUpdate_preservesImmutability_doesNotInPlaceMutate() {
        val initialSegment = repository.calculateInitialLiveSegmentState(sampleSegment)
        assertNotNull(initialSegment)

        val originalMath = initialSegment!!.math
        assertEquals(0.0, originalMath.start_cross_loc, 0.0001)
        assertEquals(0.0, originalMath.end_cross_loc, 0.0001)
        assertEquals(-1L, originalMath.startTime_ms)
        assertEquals(0.0, originalMath.startDistance, 0.0001)
        assertNull(originalMath.lastSegmentIndex)

        // 1. First update: Approach
        val approachingPos = LatLng(46.9995, 10.0)
        val updatedSegment1 = repository.processLiveSegmentUpdate(
            initialSegment,
            currentLocation = approachingPos,
            currentBearing = 0.0,
            currentDistance = 100.0
        )

        // Verify reference isolation
        assertNotSame("Result must be a new LiveSegment instance", initialSegment, updatedSegment1)
        assertNotSame("Math must be a new LiveSegmentMath instance", originalMath, updatedSegment1.math)

        // Original math MUST NOT be mutated
        assertEquals("Original start_cross_loc must remain 0.0", 0.0, originalMath.start_cross_loc, 0.0001)
        assertEquals("Original startTime_ms must remain -1", -1L, originalMath.startTime_ms)
        assertNull("Original lastSegmentIndex must remain null", originalMath.lastSegmentIndex)

        // 2. Second update: Cross start gate
        val onTrackPos = LatLng(47.0001, 10.0)
        val updatedSegment2 = repository.processLiveSegmentUpdate(
            updatedSegment1,
            currentLocation = onTrackPos,
            currentBearing = 0.0,
            currentDistance = 150.0
        )

        assertNotSame(updatedSegment1, updatedSegment2)
        assertNotSame(updatedSegment1.math, updatedSegment2.math)
        assertEquals(LiveSegmentStatus.ON_SEGMENT, updatedSegment2.liveData.segmentStatus)
        assertEquals(0, updatedSegment2.math.lastSegmentIndex)
        assertTrue(updatedSegment2.math.startTime_ms > 0)
        assertEquals(150.0, updatedSegment2.math.startDistance, 0.0001)

        // updatedSegment1.math MUST NOT be mutated
        assertEquals(-1L, updatedSegment1.math.startTime_ms)
        assertNull(updatedSegment1.math.lastSegmentIndex)
    }
}
