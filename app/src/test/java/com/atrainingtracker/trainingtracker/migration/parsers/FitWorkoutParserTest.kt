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

package com.atrainingtracker.trainingtracker.migration.parsers

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.garmin.fit.DateTime
import com.garmin.fit.FileEncoder
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.LapMesg
import com.garmin.fit.Manufacturer
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Date

/**
 * Targeted unit tests for [FitWorkoutParser] (REQ-MIG-038, TST-MIG-035).
 */
class FitWorkoutParserTest {

    private lateinit var mockContext: Context
    private lateinit var mockSportTypeDb: SportTypeDatabaseManager
    private val contentValueStores = java.util.Collections.synchronizedMap(java.util.IdentityHashMap<ContentValues, MutableMap<String, Any?>>())

    private fun io.mockk.MockKAnswerScope<*, *>.getRealInstance(): ContentValues {
        try {
            var obj: Any? = call.invocation.originalCall
            while (obj != null) {
                for (f in obj.javaClass.declaredFields) {
                    if (f.name == "self" || f.name == "\$self" || f.name.endsWith("\$self")) {
                        f.isAccessible = true
                        val s = f.get(obj)
                        if (s is ContentValues && s !== this.self) {
                            return s
                        }
                    }
                }
                val nextField = obj.javaClass.declaredFields.firstOrNull { 
                    it.name.contains("originalCall") || it.name.contains("callable") 
                }
                obj = nextField?.apply { isAccessible = true }?.get(obj)
            }
        } catch (_: Exception) { }
        return self as ContentValues
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getSportTypeId(BSportType.BIKE) } returns 2L

        contentValueStores.clear()
        mockkConstructor(ContentValues::class)
        every { constructedWith<ContentValues>().put(any<String>(), any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<String>()
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Long>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Long>()
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Int>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Int>()
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Double>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Double>()
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testParseValidFit() {
        val tempFile = File.createTempFile("test_workout", ".fit")
        try {
            val encoder = FileEncoder(tempFile, Fit.ProtocolVersion.V2_0)

            val fileIdMesg = FileIdMesg().apply {
                type = com.garmin.fit.File.ACTIVITY
                manufacturer = Manufacturer.GARMIN
                product = 1
                serialNumber = 12345L
                timeCreated = DateTime(Date(1700000000000L))
            }
            encoder.write(fileIdMesg)

            val sessionMesg = SessionMesg().apply {
                sport = Sport.CYCLING
                subSport = SubSport.ROAD
                startTime = DateTime(Date(1700000000000L))
                totalElapsedTime = 600f
                totalTimerTime = 600f
                totalDistance = 3000f
            }
            encoder.write(sessionMesg)

            val lapMesg = LapMesg().apply {
                startTime = DateTime(Date(1700000000000L))
                totalElapsedTime = 600f
                totalTimerTime = 600f
                totalDistance = 3000f
                enhancedMaxSpeed = 8.5f
                totalCalories = 120
                avgHeartRate = 140.toShort()
            }
            encoder.write(lapMesg)

            val latSemi1 = (48.137154 * (2147483648.0 / 180.0)).toInt()
            val lngSemi1 = (11.576124 * (2147483648.0 / 180.0)).toInt()
            val record1 = RecordMesg().apply {
                timestamp = DateTime(Date(1700000000000L))
                positionLat = latSemi1
                positionLong = lngSemi1
                altitude = 520f
                distance = 0f
                speed = 5.0f
                heartRate = 135.toShort()
                cadence = 80.toShort()
                power = 200
            }
            encoder.write(record1)

            val latSemi2 = (48.140000 * (2147483648.0 / 180.0)).toInt()
            val lngSemi2 = (11.580000 * (2147483648.0 / 180.0)).toInt()
            val record2 = RecordMesg().apply {
                timestamp = DateTime(Date(1700000600000L))
                positionLat = latSemi2
                positionLong = lngSemi2
                altitude = 530f
                distance = 3000f
                speed = 6.0f
                heartRate = 145.toShort()
                cadence = 85.toShort()
                power = 220
            }
            encoder.write(record2)

            encoder.close()

            val result = FitWorkoutParser.parse(tempFile, mockContext)
            assertTrue("FitWorkoutParser should succeed", result.isSuccess)
            val workout = result.getOrThrow()

            assertEquals(BSportType.BIKE, workout.bSportType)
            assertEquals(2L, workout.resolvedSportId)
            assertEquals(2, workout.points.size)
            assertEquals(2, workout.altitudes.size)
            assertEquals(520.0, workout.minAltVal, 0.001)
            assertEquals(530.0, workout.maxAltVal, 0.001)
            assertEquals(1, workout.parsedLaps.size)
            assertEquals(3000.0, workout.parsedLaps[0].distanceMeters, 0.001)
            assertEquals(120, workout.parsedLaps[0].calories)
            assertEquals(2, workout.bufferedSamples.size)
        } finally {
            tempFile.delete()
        }
    }
}
