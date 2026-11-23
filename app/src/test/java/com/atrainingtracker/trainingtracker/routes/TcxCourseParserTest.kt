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

    /**
     * TST-MAP-070.3: Verifies that TcxCourseParser filters out position-less trackpoints
     * to protect route polyline projection.
     */
    @Test
    fun testParseTcxCourse_filtersPositionlessTrackpoints() {
        val tcxXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
                <Courses>
                    <Course>
                        <Name>Route With Mixed Points</Name>
                        <Track>
                            <Trackpoint>
                                <Position>
                                    <LatitudeDegrees>47.0</LatitudeDegrees>
                                    <LongitudeDegrees>10.0</LongitudeDegrees>
                                </Position>
                                <AltitudeMeters>500.0</AltitudeMeters>
                                <DistanceMeters>0.0</DistanceMeters>
                            </Trackpoint>
                            <!-- Position-less indoor / sensor point -->
                            <Trackpoint>
                                <Time>2026-10-04T08:05:00Z</Time>
                                <AltitudeMeters>502.0</AltitudeMeters>
                                <DistanceMeters>500.0</DistanceMeters>
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
        // Position-less point was filtered out, leaving exactly 2 valid route points
        assertEquals(2, importData.pathPoints.size)
        assertEquals(47.0, importData.pathPoints[0].latLng.latitude, 0.0001)
        assertEquals(47.01, importData.pathPoints[1].latLng.latitude, 0.0001)
    }

    /**
     * TST-MIG-061.1: Standard Garmin/Komoot TCX v2 <Course> files include a summary-only <Lap>
     * (zero nested trackpoints) followed by a sibling <Track><Trackpoint>...</Trackpoint></Track>.
     */
    @Test
    fun testParseTcxCourse_withSummaryOnlyLapAndSiblingTrack_extractsSiblingTrackpoints_TST_MIG_061_1() {
        val tcxXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
                <Courses>
                    <Course>
                        <Name>Garmin Komoot Loop</Name>
                        <Lap>
                            <TotalTimeSeconds>3600.0</TotalTimeSeconds>
                            <DistanceMeters>12000.0</DistanceMeters>
                            <BeginPosition>
                                <LatitudeDegrees>48.10</LatitudeDegrees>
                                <LongitudeDegrees>11.50</LongitudeDegrees>
                            </BeginPosition>
                            <EndPosition>
                                <LatitudeDegrees>48.12</LatitudeDegrees>
                                <LongitudeDegrees>11.52</LongitudeDegrees>
                            </EndPosition>
                            <Intensity>Active</Intensity>
                        </Lap>
                        <Track>
                            <Trackpoint>
                                <Time>2026-11-23T08:00:00Z</Time>
                                <Position>
                                    <LatitudeDegrees>48.10</LatitudeDegrees>
                                    <LongitudeDegrees>11.50</LongitudeDegrees>
                                </Position>
                                <AltitudeMeters>520.0</AltitudeMeters>
                                <DistanceMeters>0.0</DistanceMeters>
                            </Trackpoint>
                            <Trackpoint>
                                <Time>2026-11-23T08:30:00Z</Time>
                                <Position>
                                    <LatitudeDegrees>48.11</LatitudeDegrees>
                                    <LongitudeDegrees>11.51</LongitudeDegrees>
                                </Position>
                                <AltitudeMeters>560.0</AltitudeMeters>
                                <DistanceMeters>6000.0</DistanceMeters>
                            </Trackpoint>
                            <Trackpoint>
                                <Time>2026-11-23T09:00:00Z</Time>
                                <Position>
                                    <LatitudeDegrees>48.12</LatitudeDegrees>
                                    <LongitudeDegrees>11.52</LongitudeDegrees>
                                </Position>
                                <AltitudeMeters>540.0</AltitudeMeters>
                                <DistanceMeters>12000.0</DistanceMeters>
                            </Trackpoint>
                        </Track>
                        <CoursePoint>
                            <Name>Summit View</Name>
                            <PointType>Summit</PointType>
                            <Position>
                                <LatitudeDegrees>48.11</LatitudeDegrees>
                                <LongitudeDegrees>11.51</LongitudeDegrees>
                            </Position>
                        </CoursePoint>
                    </Course>
                </Courses>
            </TrainingCenterDatabase>
        """.trimIndent()

        val result = TcxCourseParser.parse(tcxXml)

        assertTrue("Standard Garmin/Komoot TCX Course with summary-only <Lap> must parse cleanly", result.isSuccess)
        val importData = result.getOrThrow()
        assertEquals("Garmin Komoot Loop", importData.summary.name)
        assertEquals(3, importData.pathPoints.size)
        assertEquals(48.10, importData.pathPoints[0].latLng.latitude, 0.0001)
        assertEquals(48.12, importData.pathPoints[2].latLng.latitude, 0.0001)
        assertEquals(12000.0, importData.summary.distance, 0.001)
        assertEquals(1, importData.waypoints.size)
        assertEquals("Summit View", importData.waypoints[0].name)
    }
}
