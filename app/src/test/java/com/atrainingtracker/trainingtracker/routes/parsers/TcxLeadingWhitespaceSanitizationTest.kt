/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.routes.parsers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Unit tests verifying BOM and leading ASCII whitespace sanitization before <?xml (REQ-MIG-048.3 / TST-MIG-046.2).
 */
class TcxLeadingWhitespaceSanitizationTest {

    @Test
    fun testStripBomAndLeadingWhitespace_cleanStream() {
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><TrainingCenterDatabase/>"
        val stream = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))
        val sanitized = stripBomAndLeadingWhitespace(stream)
        val result = sanitized.bufferedReader().readText()
        assertEquals(xml, result)
    }

    @Test
    fun testStripBomAndLeadingWhitespace_withBom() {
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><TrainingCenterDatabase/>"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + xml.toByteArray(Charsets.UTF_8)
        val stream = ByteArrayInputStream(bytes)
        val sanitized = stripBomAndLeadingWhitespace(stream)
        val result = sanitized.bufferedReader().readText()
        assertEquals(xml, result)
    }

    @Test
    fun testStripBomAndLeadingWhitespace_withLeadingSpaces() {
        val spaces = "          " // 10 spaces as found in real Strava export 3131550008.tcx.gz
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><TrainingCenterDatabase/>"
        val stream = ByteArrayInputStream((spaces + xml).toByteArray(Charsets.UTF_8))
        val sanitized = stripBomAndLeadingWhitespace(stream)
        val result = sanitized.bufferedReader().readText()
        assertEquals(xml, result)
    }

    @Test
    fun testStripBomAndLeadingWhitespace_withBomAndMixedWhitespace() {
        val whitespace = " \t \r\n  "
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><TrainingCenterDatabase/>"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + (whitespace + xml).toByteArray(Charsets.UTF_8)
        val stream = ByteArrayInputStream(bytes)
        val sanitized = stripBomAndLeadingWhitespace(stream)
        val result = sanitized.bufferedReader().readText()
        assertEquals(xml, result)
    }

    @Test
    fun testTcxTrackpointParser_withLeadingWhitespace() {
        val leadingSpaces = "          "
        val tcxContent = """
            $leadingSpaces<?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="Biking">
                  <Id>2026-11-01T08:00:00Z</Id>
                  <Lap StartTime="2026-11-01T08:00:00Z">
                    <TotalTimeSeconds>120.0</TotalTimeSeconds>
                    <DistanceMeters>500.0</DistanceMeters>
                    <Track>
                      <Trackpoint>
                        <Time>2026-11-01T08:00:00Z</Time>
                        <Position>
                          <LatitudeDegrees>48.137154</LatitudeDegrees>
                          <LongitudeDegrees>11.576124</LongitudeDegrees>
                        </Position>
                        <AltitudeMeters>520.0</AltitudeMeters>
                      </Trackpoint>
                    </Track>
                  </Lap>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        val stream = ByteArrayInputStream(tcxContent.toByteArray(Charsets.UTF_8))
        val parseResult = TcxTrackpointParser.parse(stream)
        assertTrue("Parser should succeed despite leading whitespace", parseResult.isSuccess)
        val parsed = parseResult.getOrThrow()
        assertEquals("Biking", parsed.sport)
        assertEquals(1, parsed.laps.size)
        assertEquals(1, parsed.laps[0].trackpoints.size)
        assertEquals(48.137154, parsed.laps[0].trackpoints[0].lat!!, 1e-6)
    }
}
