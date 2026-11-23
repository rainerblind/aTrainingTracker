package com.atrainingtracker.trainingtracker.routes.parsers

import com.atrainingtracker.trainingtracker.database.WorkoutSource
import com.atrainingtracker.trainingtracker.exporter.writer.TCXFileWriter
import com.atrainingtracker.trainingtracker.migration.parsers.FitWorkoutParser
import com.atrainingtracker.trainingtracker.migration.parsers.GpxWorkoutParser
import com.atrainingtracker.trainingtracker.migration.parsers.TcxWorkoutParser
import com.atrainingtracker.trainingtracker.migration.parsers.WorkoutParser
import com.atrainingtracker.trainingtracker.tcx.model.TcxActivityExtensions
import com.atrainingtracker.trainingtracker.tcx.model.TcxCoursePoint
import com.atrainingtracker.trainingtracker.tcx.model.TcxCreator
import com.atrainingtracker.trainingtracker.tcx.model.TcxLapExtensions
import com.atrainingtracker.trainingtracker.tcx.parser.TcxParser
import com.atrainingtracker.trainingtracker.tcx.parser.TcxStreamListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Unit tests for [TcxParser] streaming & DTO parsing, [GpxTrackpointParser] streaming & DTO parsing,
 * [WorkoutParser] registry, and excision of obsolete `TcxTrackpointParser` and `TCXFileWriter.buildLap`
 * verifying `REQ-MIG-037`, `TST-MIG-034`, `REQ-MIG-061.1`, `TST-MIG-061.1`.
 */
class TrackpointXmlParserTest {

    @Test
    fun testObsoleteTcxTrackpointParserAndDeadBuildLapRemoved_TST_MIG_061_1() {
        var foundRemovedClass = false
        try {
            Class.forName("com.atrainingtracker.trainingtracker.routes.parsers.TcxTrackpointParser")
            foundRemovedClass = true
        } catch (_: ClassNotFoundException) {
            foundRemovedClass = false
        }
        assertFalse("TcxTrackpointParser must be deleted (REQ-MIG-061.1)", foundRemovedClass)

        val hasBuildLap = TCXFileWriter::class.java.declaredMethods.any { it.name == "buildLap" }
        assertFalse("Dead TCXFileWriter.buildLap method must be removed (REQ-MIG-061.1)", hasBuildLap)
    }

    @Test
    fun testWorkoutParserRegistryResolvesFormats_TST_MIG_061_1() {
        assertSame(FitWorkoutParser, WorkoutParser.forFormat("fit"))
        assertSame(FitWorkoutParser, WorkoutParser.forFormat(".FIT"))
        assertSame(FitWorkoutParser, WorkoutParser.forFormat("activities/123.fit.gz"))
        assertSame(TcxWorkoutParser, WorkoutParser.forFormat("tcx"))
        assertSame(TcxWorkoutParser, WorkoutParser.forFormat("ride.tcx.gz"))
        assertSame(GpxWorkoutParser, WorkoutParser.forFormat("gpx"))
        assertSame(GpxWorkoutParser, WorkoutParser.forFormat("track.GPX.gz"))
        assertNull(WorkoutParser.forFormat("csv"))
        assertNull(WorkoutParser.forFormat(""))
        assertNull(WorkoutParser.forFormat(null))

        assertEquals(WorkoutSource.FIT, FitWorkoutParser.workoutSource)
        assertEquals(WorkoutSource.TCX, TcxWorkoutParser.workoutSource)
        assertEquals(WorkoutSource.GPX, GpxWorkoutParser.workoutSource)
    }

    @Test
    fun testTcxActivityParsingWithTelemetryAndStreamingParity_TST_MIG_061_1() {
        val tcx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
                <Activities>
                    <Activity Sport="Biking">
                        <Id>2026-10-14T10:00:00Z</Id>
                        <Lap StartTime="2026-10-14T10:00:00Z">
                            <TotalTimeSeconds>1200.0</TotalTimeSeconds>
                            <DistanceMeters>10000.0</DistanceMeters>
                            <MaximumSpeed>12.5</MaximumSpeed>
                            <Calories>350</Calories>
                            <AverageHeartRateBpm>
                                <Value>145</Value>
                            </AverageHeartRateBpm>
                            <Track>
                                <Trackpoint>
                                    <Time>2026-10-14T10:00:00Z</Time>
                                    <Position>
                                        <LatitudeDegrees>48.137154</LatitudeDegrees>
                                        <LongitudeDegrees>11.576124</LongitudeDegrees>
                                    </Position>
                                    <AltitudeMeters>520.5</AltitudeMeters>
                                    <DistanceMeters>0.0</DistanceMeters>
                                    <HeartRateBpm>
                                        <Value>130</Value>
                                    </HeartRateBpm>
                                    <Cadence>85</Cadence>
                                    <Extensions>
                                        <TPX xmlns="http://www.garmin.com/xmlschemas/ActivityExtension/v2">
                                            <Speed>6.2</Speed>
                                            <Watts>210</Watts>
                                        </TPX>
                                    </Extensions>
                                </Trackpoint>
                                <Trackpoint>
                                    <Time>2026-10-14T10:00:10Z</Time>
                                    <Position>
                                        <LatitudeDegrees>48.137250</LatitudeDegrees>
                                        <LongitudeDegrees>11.576200</LongitudeDegrees>
                                    </Position>
                                    <AltitudeMeters>521.0</AltitudeMeters>
                                    <DistanceMeters>50.0</DistanceMeters>
                                    <HeartRateBpm>
                                        <Value>135</Value>
                                    </HeartRateBpm>
                                    <Cadence>88</Cadence>
                                    <Extensions>
                                        <TPX xmlns="http://www.garmin.com/xmlschemas/ActivityExtension/v2">
                                            <Speed>7.0</Speed>
                                            <Watts>230</Watts>
                                        </TPX>
                                    </Extensions>
                                </Trackpoint>
                            </Track>
                        </Lap>
                        <Notes>Test Ride After Lap</Notes>
                    </Activity>
                </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        // 1. DTO parsing
        val result = TcxParser.parse(ByteArrayInputStream(tcx.toByteArray()))
        assertTrue("Expected TCX parse success", result.isSuccess)
        val activity = result.getOrThrow().activities.single()
        assertEquals("Biking", activity.sport)
        assertEquals("Test Ride After Lap", activity.notes)
        assertEquals(1, activity.laps.size)
        val lap = activity.laps[0]
        assertEquals(1200.0, lap.totalTimeSeconds, 0.001)
        assertEquals(10000.0, lap.distanceMeters, 0.001)
        assertEquals(12.5, lap.maximumSpeed!!, 0.001)
        assertEquals(350, lap.calories)
        assertEquals(145, lap.averageHeartRateBpm)
        assertEquals(2, lap.trackpoints.size)

        // 2. Direct single-pass streaming callbacks (including post-Lap <Notes> onActivityEnd)
        var streamedTpCount = 0
        var streamedNotes: String? = null
        var streamedLapEndCount = 0
        val streamResult = TcxParser.parseStreaming(tcx, object : TcxStreamListener {
            override fun onTrackpoint(
                inCourse: Boolean, ownerIndex: Int, inLap: Boolean, lapIndex: Int,
                time: String, lat: Double?, lng: Double?, altitudeMeters: Double?,
                distanceMeters: Double?, heartRateBpm: Int?, cadence: Int?,
                speed: Double?, watts: Int?, runCadence: Int?
            ) {
                streamedTpCount++
            }

            override fun onLapEnd(
                inCourse: Boolean, ownerIndex: Int, lapIndex: Int, startTime: String,
                totalTimeSeconds: Double, distanceMeters: Double, maximumSpeed: Double?,
                calories: Int?, averageHeartRateBpm: Int?, maximumHeartRateBpm: Int?,
                intensity: String?, cadence: Int?, triggerMethod: String?,
                notes: String?, extensions: TcxLapExtensions?
            ) {
                streamedLapEndCount++
                assertEquals(1200.0, totalTimeSeconds, 0.001)
                assertEquals(145, averageHeartRateBpm)
            }

            override fun onActivityEnd(
                activityIndex: Int, sport: String, id: String, notes: String?,
                creator: TcxCreator?, extensions: TcxActivityExtensions?
            ) {
                streamedNotes = notes
            }
        })
        assertTrue(streamResult.isSuccess)
        assertEquals(2, streamedTpCount)
        assertEquals(1, streamedLapEndCount)
        assertEquals("Test Ride After Lap", streamedNotes)
    }

    @Test
    fun testTcxCourseParsingWithCoursePoints() {
        val tcx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
                <Courses>
                    <Course>
                        <Name>Alpine Climb</Name>
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
                            <Name>Summit Summit</Name>
                            <Time>2026-10-14T08:05:00Z</Time>
                            <Position>
                                <LatitudeDegrees>47.510</LatitudeDegrees>
                                <LongitudeDegrees>11.010</LongitudeDegrees>
                            </Position>
                            <AltitudeMeters>850.0</AltitudeMeters>
                            <PointType>Summit</PointType>
                            <Notes>Watch for wind</Notes>
                        </CoursePoint>
                    </Course>
                </Courses>
            </TrainingCenterDatabase>
        """.trimIndent()

        val result = TcxParser.parse(ByteArrayInputStream(tcx.toByteArray()))
        assertTrue(result.isSuccess)
        val course = result.getOrThrow().courses.single()

        assertEquals("Alpine Climb", course.name)
        assertEquals(2, course.trackpoints.size)
        assertEquals(1, course.coursePoints.size)

        val cp = course.coursePoints[0]
        assertEquals("Summit Summit", cp.name)
        assertEquals("Summit", cp.pointType)
        assertEquals("Watch for wind", cp.notes)
        assertEquals(47.510, cp.position.latitudeDegrees, 0.0001)
        assertEquals(11.010, cp.position.longitudeDegrees, 0.0001)
        assertEquals(850.0, cp.altitudeMeters!!, 0.01)

        // Streaming callback emits course point directly
        val streamedCoursePoints = mutableListOf<TcxCoursePoint>()
        assertTrue(TcxParser.parseStreaming(tcx, object : TcxStreamListener {
            override fun onCoursePoint(courseIndex: Int, coursePoint: TcxCoursePoint) {
                streamedCoursePoints.add(coursePoint)
            }
        }).isSuccess)
        assertEquals(1, streamedCoursePoints.size)
        assertEquals("Summit Summit", streamedCoursePoints[0].name)
    }

    @Test
    fun testGpxTrackParsingWithGarminTpxExtensionsAndStreamingParity_TST_MIG_061_1() {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="Garmin Connect"
                 xmlns="http://www.topografix.com/GPX/1/1"
                 xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">
                <metadata>
                    <name>Morning Run</name>
                    <desc>Tempo run around the lake</desc>
                </metadata>
                <wpt lat="48.1400" lon="11.5800">
                    <ele>515.0</ele>
                    <name>Start/Finish Banner</name>
                    <desc>Official meeting point</desc>
                    <type>Generic</type>
                </wpt>
                <trk>
                    <name>Lake Loop</name>
                    <trkseg>
                        <trkpt lat="48.1401" lon="11.5801">
                            <ele>516.0</ele>
                            <time>2026-10-14T07:00:00Z</time>
                            <extensions>
                                <gpxtpx:TrackPointExtension>
                                    <gpxtpx:hr>142</gpxtpx:hr>
                                    <gpxtpx:cad>86</gpxtpx:cad>
                                    <gpxtpx:speed>3.8</gpxtpx:speed>
                                    <gpxtpx:atemp>14.5</gpxtpx:atemp>
                                </gpxtpx:TrackPointExtension>
                            </extensions>
                        </trkpt>
                        <trkpt lat="48.1405" lon="11.5806">
                            <ele>517.2</ele>
                            <time>2026-10-14T07:00:05Z</time>
                            <extensions>
                                <gpxtpx:TrackPointExtension>
                                    <gpxtpx:hr>148</gpxtpx:hr>
                                    <gpxtpx:cad>88</gpxtpx:cad>
                                    <gpxtpx:speed>4.1</gpxtpx:speed>
                                    <gpxtpx:atemp>14.6</gpxtpx:atemp>
                                </gpxtpx:TrackPointExtension>
                            </extensions>
                        </trkpt>
                    </trkseg>
                </trk>
            </gpx>
        """.trimIndent()

        val result = GpxTrackpointParser.parse(ByteArrayInputStream(gpx.toByteArray()))
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()

        assertEquals("Morning Run", data.name)
        assertEquals("Lake Loop", data.trackName)
        assertEquals("Tempo run around the lake", data.description)
        assertEquals(1, data.waypoints.size)
        assertEquals("Start/Finish Banner", data.waypoints[0].name)
        assertEquals("Official meeting point", data.waypoints[0].notes)
        assertEquals(48.1400, data.waypoints[0].lat, 0.0001)
        assertEquals(11.5800, data.waypoints[0].lng, 0.0001)

        assertEquals(1, data.laps.size)
        val lap = data.laps[0]
        assertEquals(2, lap.trackpoints.size)

        val pt1 = lap.trackpoints[0]
        assertEquals(48.1401, pt1.lat!!, 0.00001)
        assertEquals(11.5801, pt1.lng!!, 0.00001)
        assertEquals(516.0, pt1.altitude!!, 0.01)
        assertEquals("2026-10-14T07:00:00Z", pt1.time)
        assertEquals(142, pt1.hr)
        assertEquals(86, pt1.cadence)
        assertEquals(3.8, pt1.speedMps!!, 0.01)
        assertEquals(14.5, pt1.temperature!!, 0.01)

        val pt2 = lap.trackpoints[1]
        assertEquals(148, pt2.hr)
        assertEquals(88, pt2.cadence)
        assertEquals(4.1, pt2.speedMps!!, 0.01)
        assertEquals(14.6, pt2.temperature!!, 0.01)
    }

    @Test
    fun testMalformedXmlGracefulHandling() {
        val badXml = "<TrainingCenterDatabase><Activities><Activity></TrainingCenterDatabase>"
        val result = TcxParser.parse(ByteArrayInputStream(badXml.toByteArray()))
        assertTrue("Malformed XML should return Failure Result", result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun testGpxWithUtf8BomIsHandledGracefully() {
        val gpxWithBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            """<?xml version="1.0" encoding="UTF-8"?><gpx version="1.1"><trk><trkseg><trkpt lat="48.0" lon="11.0"/></trkseg></trk></gpx>""".toByteArray(Charsets.UTF_8)
        val result = GpxTrackpointParser.parse(ByteArrayInputStream(gpxWithBom))
        assertTrue("GPX with UTF-8 BOM should parse successfully", result.isSuccess)
        val data = result.getOrThrow()
        assertEquals(1, data.laps.size)
        assertEquals(1, data.laps[0].trackpoints.size)
        assertEquals(48.0, data.laps[0].trackpoints[0].lat!!, 0.001)
    }

    @Test
    fun testTcxWithUtf8BomIsHandledGracefully() {
        val tcxWithBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            """<?xml version="1.0" encoding="UTF-8"?><TrainingCenterDatabase><Activities><Activity Sport="Running"><Lap><Track><Trackpoint><Position><LatitudeDegrees>48.0</LatitudeDegrees><LongitudeDegrees>11.0</LongitudeDegrees></Position></Trackpoint></Track></Lap></Activity></Activities></TrainingCenterDatabase>""".toByteArray(Charsets.UTF_8)
        val result = TcxParser.parse(ByteArrayInputStream(tcxWithBom))
        assertTrue("TCX with UTF-8 BOM should parse successfully", result.isSuccess)
        val activity = result.getOrThrow().activities.single()
        assertEquals(1, activity.laps.size)
        assertEquals(1, activity.laps[0].trackpoints.size)
        assertEquals(48.0, activity.laps[0].trackpoints[0].position!!.latitudeDegrees, 0.001)
    }

    @Test
    fun testEmptyXmlGracefulHandling() {
        val emptyGpx = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\"></gpx>"
        val result = GpxTrackpointParser.parse(ByteArrayInputStream(emptyGpx.toByteArray()))
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertTrue(data.laps.isEmpty())
        assertTrue(data.waypoints.isEmpty())
    }
}
