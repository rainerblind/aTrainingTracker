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

package com.atrainingtracker.trainingtracker.routes

import android.location.Location
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.*

/**
 * Unit tests verifying streaming Garmin TCX course (<Courses><Course>) parsing,
 * trackpoint extraction, and <CoursePoint> semantic mapping (REQ-MAP-026, TST-MAP-028 Group 2).
 */
class TcxCourseParserTest {

    @Before
    fun setUp() {
        mockkStatic(Location::class)
        every { Location.distanceBetween(any(), any(), any(), any(), any()) } answers {
            val startLat = arg<Double>(0)
            val startLng = arg<Double>(1)
            val endLat = arg<Double>(2)
            val endLng = arg<Double>(3)
            val results = arg<FloatArray>(4)
            val dLat = Math.toRadians(endLat - startLat)
            val dLng = Math.toRadians(endLng - startLng)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(startLat)) * cos(Math.toRadians(endLat)) *
                    sin(dLng / 2) * sin(dLng / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            val dist = (6371000 * c).toFloat()
            results[0] = dist
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testParseTcxCourse_extractsTrackpointsAndCoursePoints() {
        val tcxXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
                <Courses>
                    <Course>
                        <Name>Alpine Pass Loop</Name>
                        <Track>
                            <Trackpoint>
                                <Time>2026-10-04T08:00:00Z</Time>
                                <Position>
                                    <LatitudeDegrees>47.0</LatitudeDegrees>
                                    <LongitudeDegrees>10.0</LongitudeDegrees>
                                </Position>
                                <AltitudeMeters>1000.0</AltitudeMeters>
                                <DistanceMeters>0.0</DistanceMeters>
                            </Trackpoint>
                            <Trackpoint>
                                <Time>2026-10-04T08:10:00Z</Time>
                                <Position>
                                    <LatitudeDegrees>47.2</LatitudeDegrees>
                                    <LongitudeDegrees>10.2</LongitudeDegrees>
                                </Position>
                                <AltitudeMeters>1500.0</AltitudeMeters>
                                <DistanceMeters>5000.0</DistanceMeters>
                            </Trackpoint>
                            <Trackpoint>
                                <Time>2026-10-04T08:25:00Z</Time>
                                <Position>
                                    <LatitudeDegrees>47.4</LatitudeDegrees>
                                    <LongitudeDegrees>10.4</LongitudeDegrees>
                                </Position>
                                <AltitudeMeters>2200.0</AltitudeMeters>
                                <DistanceMeters>10000.0</DistanceMeters>
                            </Trackpoint>
                        </Track>
                        <CoursePoint>
                            <Name>Sharp Left</Name>
                            <PointType>Left</PointType>
                            <Notes>Sharp hairpin turn</Notes>
                            <Position>
                                <LatitudeDegrees>47.1</LatitudeDegrees>
                                <LongitudeDegrees>10.1</LongitudeDegrees>
                            </Position>
                        </CoursePoint>
                        <CoursePoint>
                            <Name>Spring Refill</Name>
                            <PointType>Water</PointType>
                            <Position>
                                <LatitudeDegrees>47.2</LatitudeDegrees>
                                <LongitudeDegrees>10.2</LongitudeDegrees>
                            </Position>
                        </CoursePoint>
                        <CoursePoint>
                            <Name>High Summit</Name>
                            <PointType>Summit</PointType>
                            <AltitudeMeters>2200.0</AltitudeMeters>
                            <Position>
                                <LatitudeDegrees>47.3</LatitudeDegrees>
                                <LongitudeDegrees>10.3</LongitudeDegrees>
                            </Position>
                        </CoursePoint>
                    </Course>
                </Courses>
            </TrainingCenterDatabase>
        """.trimIndent()

        val result = TcxCourseParser.parse(tcxXml)

        assertTrue(result.isSuccess)
        val importData = result.getOrThrow()

        assertEquals("Alpine Pass Loop", importData.summary.name)
        assertEquals(3, importData.pathPoints.size)
        assertEquals(10000.0, importData.summary.distance, 0.001)
        assertEquals(1200.0, importData.summary.elevationGain, 0.001)

        val waypoints = importData.waypoints
        assertEquals(3, waypoints.size)

        val turnLeft = waypoints.find { it.name == "Sharp Left" }
        assertNotNull(turnLeft)
        assertEquals(WaypointType.TURN_LEFT, turnLeft?.type)
        assertEquals("Sharp hairpin turn", turnLeft?.description)

        val water = waypoints.find { it.name == "Spring Refill" }
        assertNotNull(water)
        assertEquals(WaypointType.POI_WATER, water?.type)

        val summit = waypoints.find { it.name == "High Summit" }
        assertNotNull(summit)
        assertEquals(WaypointType.POI_SUMMIT, summit?.type)
        assertEquals(2200.0, summit?.altitude ?: 0.0, 0.001)
    }

    @Test
    fun testParseTcxCourse_whenNoCoursePointsPresent_returnsRouteWithZeroWaypoints() {
        val tcxXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
                <Courses>
                    <Course>
                        <Name>Flat Trail</Name>
                        <Track>
                            <Trackpoint>
                                <Position>
                                    <LatitudeDegrees>47.0</LatitudeDegrees>
                                    <LongitudeDegrees>10.0</LongitudeDegrees>
                                </Position>
                                <AltitudeMeters>500.0</AltitudeMeters>
                                <DistanceMeters>0.0</DistanceMeters>
                            </Trackpoint>
                            <Trackpoint>
                                <Position>
                                    <LatitudeDegrees>47.01</LatitudeDegrees>
                                    <LongitudeDegrees>10.01</LongitudeDegrees>
                                </Position>
                                <AltitudeMeters>505.0</AltitudeMeters>
                                <DistanceMeters>1000.0</DistanceMeters>
                            </Trackpoint>
                        </Track>
                    </Course>
                </Courses>
            </TrainingCenterDatabase>
        """.trimIndent()

        val result = TcxCourseParser.parse(tcxXml)

        assertTrue(result.isSuccess)
        val importData = result.getOrThrow()

        assertEquals("Flat Trail", importData.summary.name)
        assertEquals(2, importData.pathPoints.size)
        assertTrue(importData.waypoints.isEmpty())
    }

    @Test
    fun testParseTcxCourse_invalidXml_returnsFailure() {
        val invalidXml = "<InvalidXml></InvalidXml>"
        val result = TcxCourseParser.parse(invalidXml)
        assertTrue(result.isFailure)
    }
}
