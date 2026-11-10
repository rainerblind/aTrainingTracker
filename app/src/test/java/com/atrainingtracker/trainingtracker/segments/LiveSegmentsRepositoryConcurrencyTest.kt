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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Multi-threaded concurrency stress test for [LiveSegmentsRepository] verifying atomic StateFlow updates,
 * CAS retry idempotency, and elimination of lost updates between concurrent location updates and
 * segment database reconciliations (REQ-MAP-065.1, TST-MAP-067.1).
 */
class LiveSegmentsRepositoryConcurrencyTest {

    private lateinit var mockContext: Context
    private lateinit var mockSegmentsRepo: SegmentsRepository
    private lateinit var mockBanalRepo: BANALServiceRepository
    private lateinit var repository: LiveSegmentsRepository

    private fun createSampleSegment(id: Long, name: String, startLat: Double): SegmentWithPath {
        return SegmentWithPath(
            summary = SegmentSummary(
                stravaId = id,
                name = name,
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
                PathPoint(distance = 0.0, latLng = LatLng(startLat, 10.0), altitude = 500.0),
                PathPoint(distance = 150.0, latLng = LatLng(startLat + 0.001, 10.0), altitude = 508.0),
                PathPoint(distance = 300.0, latLng = LatLng(startLat + 0.002, 10.0), altitude = 515.0),
                PathPoint(distance = 500.0, latLng = LatLng(startLat + 0.004, 10.0), altitude = 525.0),
                PathPoint(distance = 700.0, latLng = LatLng(startLat + 0.0055, 10.0), altitude = 535.0),
                PathPoint(distance = 850.0, latLng = LatLng(startLat + 0.007, 10.0), altitude = 542.0),
                PathPoint(distance = 1000.0, latLng = LatLng(startLat + 0.008, 10.0), altitude = 550.0)
            )
        )
    }

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
     * TST-MAP-067.1: Stress test running 20 concurrent coroutines interleaving updateLiveSegments
     * and reconcileSegments. Asserts zero lost updates and no race conditions.
     */
    @Test
    fun testConcurrentReconcileAndUpdate_noLostUpdates() = runBlocking {
        val seg1 = createSampleSegment(101L, "Segment 1", 47.0)
        val seg2 = createSampleSegment(202L, "Segment 2", 48.0)
        val seg3 = createSampleSegment(303L, "Segment 3", 49.0)

        // Initial setup: seg1 only
        repository.reconcileSegments(listOf(seg1))
        assertEquals(1, repository.liveSegments.value.size)

        // Move seg1 to approaching and then ON_SEGMENT
        repository.updateLiveSegments(LatLng(46.9995, 10.0), currentBearing = 0.0, currentDistance = 100.0)
        repository.updateLiveSegments(LatLng(47.0001, 10.0), currentBearing = 0.0, currentDistance = 150.0)
        assertEquals(LiveSegmentStatus.ON_SEGMENT, repository.liveSegments.value.first().liveData.segmentStatus)

        val iterations = 50
        val coroutineCount = 10
        val exceptions = AtomicInteger(0)

        // Launch concurrent jobs
        val jobs = (1..coroutineCount).map { i ->
            launch(Dispatchers.Default) {
                try {
                    for (step in 1..iterations) {
                        if (i % 2 == 0) {
                            // Location update worker (advancing slightly along seg1)
                            repository.updateLiveSegments(
                                LatLng(47.001, 10.0),
                                currentBearing = 0.0,
                                currentDistance = 150.0 + (step * 2.0)
                            )
                        } else {
                            // Segment database reconciliation worker (adding seg2 or seg3 alongside seg1)
                            val segments = if (step % 2 == 0) {
                                listOf(seg1, seg2)
                            } else {
                                listOf(seg1, seg2, seg3)
                            }
                            repository.reconcileSegments(segments)
                        }
                    }
                } catch (t: Throwable) {
                    exceptions.incrementAndGet()
                }
            }
        }

        jobs.joinAll()

        assertEquals("No exceptions should occur during concurrent updates", 0, exceptions.get())

        val finalList = repository.liveSegments.value
        val seg1Final = finalList.firstOrNull { it.staticData.summary.stravaId == 101L }
        assertNotNull("Segment 1 must remain in the live segments list", seg1Final)
        assertEquals(
            "Segment 1 must retain its active ON_SEGMENT status and not be reset to FAR_FAR_AWAY",
            LiveSegmentStatus.ON_SEGMENT,
            seg1Final!!.liveData.segmentStatus
        )

        // Segment 2 must also be present
        val seg2Final = finalList.firstOrNull { it.staticData.summary.stravaId == 202L }
        assertNotNull("Segment 2 must be present in reconciled list", seg2Final)
    }
}
