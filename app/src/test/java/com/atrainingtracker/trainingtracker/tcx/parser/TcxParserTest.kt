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

package com.atrainingtracker.trainingtracker.tcx.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [TcxParser] XML deserialization, extension parsing,
 * bracket fallback heuristics, and leading whitespace resilience (REQ-DAT-040.3, TST-DAT-036.2).
 */
class TcxParserTest {

    @Test
    fun testParseStandardTcxWithExtensions() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="Running">
                  <Id>2026-11-10T07:30:00Z</Id>
                  <Lap StartTime="2026-11-10T07:30:00Z">
                    <TotalTimeSeconds>1200.0</TotalTimeSeconds>
                    <DistanceMeters>4000.0</DistanceMeters>
                    <MaximumSpeed>4.5</MaximumSpeed>
                    <Calories>280</Calories>
                    <AverageHeartRateBpm><Value>152</Value></AverageHeartRateBpm>
                    <MaximumHeartRateBpm><Value>168</Value></MaximumHeartRateBpm>
                    <Intensity>Active</Intensity>
                    <Cadence>85</Cadence>
                    <TriggerMethod>Manual</TriggerMethod>
                    <Track>
                      <Trackpoint>
                        <Time>2026-11-10T07:30:00Z</Time>
                        <Position>
                          <LatitudeDegrees>48.137</LatitudeDegrees>
                          <LongitudeDegrees>11.576</LongitudeDegrees>
                        </Position>
                        <AltitudeMeters>515.2</AltitudeMeters>
                        <DistanceMeters>0.0</DistanceMeters>
                        <HeartRateBpm><Value>140</Value></HeartRateBpm>
                        <Cadence>84</Cadence>
                        <Extensions>
                          <TPX xmlns="http://www.garmin.com/xmlschemas/ActivityExtension/v2">
                            <Speed>3.33</Speed>
                            <Watts>220</Watts>
                            <RunCadence>168</RunCadence>
                          </TPX>
                        </Extensions>
                      </Trackpoint>
                    </Track>
                    <Notes>[Warmup] 4km easy pace</Notes>
                    <Extensions>
                      <att:LapExtension xmlns:att="http://atrainingtracker.com/xmlschemas/TrainingCenterDatabaseExtensions/v1">
                        <att:Name>Warmup</att:Name>
                        <att:Description>4km easy pace</att:Description>
                      </att:LapExtension>
                    </Extensions>
                  </Lap>
                  <Notes>[Morning 4k] Crisp autumn morning</Notes>
                  <Creator xsi:type="Device_t">
                    <Name>Pixel 10 Pro</Name>
                    <UnitId>123456</UnitId>
                    <ProductID>1</ProductID>
                    <Version>
                      <VersionMajor>5</VersionMajor>
                      <VersionMinor>0</VersionMinor>
                      <BuildMajor>501</BuildMajor>
                      <BuildMinor>0</BuildMinor>
                    </Version>
                  </Creator>
                  <Extensions>
                    <att:ActivityExtension xmlns:att="http://atrainingtracker.com/xmlschemas/TrainingCenterDatabaseExtensions/v1">
                      <att:Name>Morning 4k</att:Name>
                      <att:Description>Crisp autumn morning</att:Description>
                    </att:ActivityExtension>
                  </Extensions>
                </Activity>
              </Activities>
              <Author xsi:type="Application_t">
                <Name>aTrainingTracker</Name>
                <Build>
                  <Version>
                    <VersionMajor>5</VersionMajor>
                    <VersionMinor>0</VersionMinor>
                    <BuildMajor>501</BuildMajor>
                    <BuildMinor>0</BuildMinor>
                  </Version>
                  <Type>Release</Type>
                </Build>
                <LangID>en</LangID>
                <PartNumber>000-00000-00</PartNumber>
              </Author>
            </TrainingCenterDatabase>
        """.trimIndent()

        val result = TcxParser.parse(xml)
        assertTrue(result.isSuccess)
        val doc = result.getOrThrow()

        assertEquals(1, doc.activities.size)
        val activity = doc.activities.first()
        assertEquals("Running", activity.sport)
        assertEquals("2026-11-10T07:30:00Z", activity.id)
        assertEquals("[Morning 4k] Crisp autumn morning", activity.notes)
        assertEquals("Morning 4k", activity.extensions?.customName)
        assertEquals("Crisp autumn morning", activity.extensions?.customDescription)

        // Creator
        assertNotNull(activity.creator)
        assertEquals("Pixel 10 Pro", activity.creator?.name)
        assertEquals(5, activity.creator?.versionMajor)

        // Lap
        assertEquals(1, activity.laps.size)
        val lap = activity.laps.first()
        assertEquals("2026-11-10T07:30:00Z", lap.startTime)
        assertEquals(1200.0, lap.totalTimeSeconds, 0.001)
        assertEquals(4000.0, lap.distanceMeters, 0.001)
        assertEquals(4.5, lap.maximumSpeed ?: 0.0, 0.001)
        assertEquals(280, lap.calories)
        assertEquals(152, lap.averageHeartRateBpm)
        assertEquals("Warmup", lap.extensions?.name)
        assertEquals("4km easy pace", lap.extensions?.description)

        // Trackpoint
        assertEquals(1, lap.trackpoints.size)
        val tp = lap.trackpoints.first()
        assertEquals("2026-11-10T07:30:00Z", tp.time)
        assertEquals(48.137, tp.position?.latitudeDegrees ?: 0.0, 0.0001)
        assertEquals(11.576, tp.position?.longitudeDegrees ?: 0.0, 0.0001)
        assertEquals(515.2, tp.altitudeMeters ?: 0.0, 0.001)
        assertEquals(140, tp.heartRateBpm)
        assertEquals(84, tp.cadence)
        assertEquals(3.33, tp.extensions?.speed ?: 0.0, 0.001)
        assertEquals(220, tp.extensions?.watts)
        assertEquals(168, tp.extensions?.runCadence)

        // Author
        assertNotNull(doc.author)
        assertEquals("aTrainingTracker", doc.author?.name)
    }

    @Test
    fun testParseBracketFallbackAndLeadingWhitespace() {
        val xmlWithWhitespace = "   \n\t  " + """
            <TrainingCenterDatabase>
              <Activities>
                <Activity Sport="Biking">
                  <Id>2026-11-10T09:00:00Z</Id>
                  <Lap StartTime="2026-11-10T09:00:00Z">
                    <TotalTimeSeconds>1800.0</TotalTimeSeconds>
                    <DistanceMeters>12000.0</DistanceMeters>
                    <Track>
                      <Trackpoint>
                        <Time>2026-11-10T09:00:00Z</Time>
                        <DistanceMeters>100.0</DistanceMeters>
                      </Trackpoint>
                    </Track>
                    <Notes>[Interval 1] Fast sprint</Notes>
                  </Lap>
                  <Notes>[Sunday Tempo] 30km flat course</Notes>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        val result = TcxParser.parse(xmlWithWhitespace)
        assertTrue(result.isSuccess)
        val doc = result.getOrThrow()

        val activity = doc.activities.first()
        assertEquals("Biking", activity.sport)
        assertEquals("Sunday Tempo", activity.extensions?.customName)
        assertEquals("30km flat course", activity.extensions?.customDescription)

        val lap = activity.laps.first()
        assertEquals("Interval 1", lap.extensions?.name)
        assertEquals("Fast sprint", lap.extensions?.description)
        assertNull(lap.trackpoints.first().position)
    }

    @Test
    fun testParseCoursesAndCoursePoints() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Courses>
                <Course>
                  <Name>Alpine Ridge Route</Name>
                  <Track>
                    <Trackpoint>
                      <Time>2026-10-14T08:00:00Z</Time>
                      <Position>
                        <LatitudeDegrees>47.500</LatitudeDegrees>
                        <LongitudeDegrees>11.000</LongitudeDegrees>
                      </Position>
                      <AltitudeMeters>800.0</AltitudeMeters>
                      <DistanceMeters>0.0</DistanceMeters>
                    </Trackpoint>
                    <Trackpoint>
                      <Time>2026-10-14T08:05:00Z</Time>
                      <Position>
                        <LatitudeDegrees>47.510</LatitudeDegrees>
                        <LongitudeDegrees>11.010</LongitudeDegrees>
                      </Position>
                      <AltitudeMeters>850.0</AltitudeMeters>
                      <DistanceMeters>1200.0</DistanceMeters>
                    </Trackpoint>
                  </Track>
                  <CoursePoint>
                    <Name>Summit Cross</Name>
                    <Time>2026-10-14T08:05:00Z</Time>
                    <Position>
                      <LatitudeDegrees>47.510</LatitudeDegrees>
                      <LongitudeDegrees>11.010</LongitudeDegrees>
                    </Position>
                    <AltitudeMeters>850.0</AltitudeMeters>
                    <PointType>Summit</PointType>
                    <Notes>Windy ridge</Notes>
                  </CoursePoint>
                  <Notes>Demanding trail with scenic view</Notes>
                </Course>
              </Courses>
            </TrainingCenterDatabase>
        """.trimIndent()

        val result = TcxParser.parse(xml)
        assertTrue("Course parse should succeed", result.isSuccess)
        val doc = result.getOrThrow()

        assertEquals(1, doc.courses.size)
        val course = doc.courses.first()
        assertEquals("Alpine Ridge Route", course.name)
        assertEquals("Demanding trail with scenic view", course.notes)
        assertEquals(2, course.trackpoints.size)
        assertEquals(47.500, course.trackpoints[0].position?.latitudeDegrees ?: 0.0, 1e-4)

        assertEquals(1, course.coursePoints.size)
        val cp = course.coursePoints[0]
        assertEquals("Summit Cross", cp.name)
        assertEquals("Summit", cp.pointType)
        assertEquals("Windy ridge", cp.notes)
        assertEquals(47.510, cp.position.latitudeDegrees, 1e-4)
        assertEquals(11.010, cp.position.longitudeDegrees, 1e-4)
        assertEquals(850.0, cp.altitudeMeters ?: 0.0, 1e-2)
    }
}
