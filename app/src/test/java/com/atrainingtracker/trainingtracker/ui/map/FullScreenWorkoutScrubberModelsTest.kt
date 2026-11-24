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

package com.atrainingtracker.trainingtracker.ui.map

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Pure-JVM unit tests for [FullScreenWorkoutScrubberModels] dynamic metric resolution, default
 * selection, linear altitude interpolation, and live scrub readout formatting
 * (`REQ-MAP-070`, `TST-MAP-073.1`, `ATT-9030`).
 */
// Linked test specs: TST-MAP-073, TST-MAP-073.1
class FullScreenWorkoutScrubberModelsTest {

    private val origin = LatLng(48.1371, 11.5754)

    @Test
    fun resolveAvailableScrubberMetrics_returnsEmptyForNullSinglePointOrBareTrack() {
        assertEquals(emptyList<FullScreenScrubberMetric>(), resolveAvailableScrubberMetrics(null))
        assertEquals(emptyList<FullScreenScrubberMetric>(), resolveAvailableScrubberMetrics(emptyList()))
        assertEquals(
            emptyList<FullScreenScrubberMetric>(),
            resolveAvailableScrubberMetrics(listOf(PathPoint(0.0, origin, altitude = 500.0, hr = 140)))
        )

        val bareTrack = listOf(
            PathPoint(0.0, origin, altitude = Double.NaN, hr = 0, power = 0, speedMps = 0.0, cadence = 0),
            PathPoint(500.0, LatLng(48.140, 11.580))
        )
        assertEquals(emptyList<FullScreenScrubberMetric>(), resolveAvailableScrubberMetrics(bareTrack))
    }

    @Test
    fun resolveAvailableScrubberMetrics_listsOnlyRecordedStreamsAndGatesWPrimeBal() {
        val path = listOf(
            PathPoint(
                distance = 0.0,
                latLng = origin,
                altitude = 520.0,
                timeSec = 0L,
                hr = 135,
                power = 210,
                wPrimeBalKj = 18.0
            ),
            PathPoint(
                distance = 1000.0,
                latLng = LatLng(48.145, 11.585),
                altitude = 550.0,
                timeSec = 120L,
                hr = 152,
                power = 280,
                wPrimeBalKj = 15.5
            )
        )

        assertEquals(
            listOf(
                FullScreenScrubberMetric.ELEVATION,
                FullScreenScrubberMetric.HEART_RATE,
                FullScreenScrubberMetric.POWER
            ),
            resolveAvailableScrubberMetrics(path, enableCriticalPowerModel = false)
        )

        assertEquals(
            listOf(
                FullScreenScrubberMetric.ELEVATION,
                FullScreenScrubberMetric.HEART_RATE,
                FullScreenScrubberMetric.POWER,
                FullScreenScrubberMetric.W_PRIME_BAL
            ),
            resolveAvailableScrubberMetrics(path, enableCriticalPowerModel = true)
        )
    }

    @Test
    fun resolveDefaultScrubberMetric_prefersElevationElseFirstAvailable() {
        assertNull(resolveDefaultScrubberMetric(emptyList()))
        assertEquals(
            FullScreenScrubberMetric.ELEVATION,
            resolveDefaultScrubberMetric(
                listOf(
                    FullScreenScrubberMetric.HEART_RATE,
                    FullScreenScrubberMetric.ELEVATION,
                    FullScreenScrubberMetric.POWER
                )
            )
        )
        assertEquals(
            FullScreenScrubberMetric.HEART_RATE,
            resolveDefaultScrubberMetric(
                listOf(FullScreenScrubberMetric.HEART_RATE, FullScreenScrubberMetric.POWER)
            )
        )
    }

    @Test
    fun toTelemetryMetricType_adaptsSpeedVsPaceBySportType() {
        assertNull(FullScreenScrubberMetric.ELEVATION.toTelemetryMetricType(BSportType.BIKE))
        assertEquals(
            TelemetryMetricType.SPEED,
            FullScreenScrubberMetric.SPEED_PACE.toTelemetryMetricType(BSportType.BIKE)
        )
        assertEquals(
            TelemetryMetricType.PACE,
            FullScreenScrubberMetric.SPEED_PACE.toTelemetryMetricType(BSportType.RUN)
        )
        assertEquals(
            TelemetryMetricType.POWER,
            FullScreenScrubberMetric.POWER.toTelemetryMetricType(BSportType.BIKE)
        )
    }

    @Test
    fun interpolateScrubAltitude_linearlyInterpolatesAndClampsBounds() {
        val path = listOf(
            PathPoint(0.0, origin, altitude = 500.0),
            PathPoint(1000.0, LatLng(48.145, 11.585), altitude = 600.0),
            PathPoint(2000.0, LatLng(48.150, 11.590), altitude = 560.0)
        )

        assertEquals(500.0, interpolateScrubAltitude(path, 0.0)!!, 0.01)
        assertEquals(525.0, interpolateScrubAltitude(path, 250.0)!!, 0.01)
        assertEquals(580.0, interpolateScrubAltitude(path, 1500.0)!!, 0.01)
        assertEquals(560.0, interpolateScrubAltitude(path, 2500.0)!!, 0.01)
    }

    @Test
    fun formatFullScreenScrubReadout_powerSelectedIncludesZoneAndNonDuplicatingContext() {
        val point = PathPoint(
            distance = 21_500.0,
            latLng = origin,
            altitude = 642.0,
            timeSec = 3370L,
            hr = 164,
            power = 285,
            speedMps = 8.75,
            slope = 4.2,
            cadence = 88
        )
        val powerThresholds = PowerZoneThresholds(
            z1Max = 150, z2Max = 210, z3Max = 250, z4Max = 295, z5Max = 340, z6Max = 420
        )

        val readout = formatFullScreenScrubReadout(
            point = point,
            selectedMetric = FullScreenScrubberMetric.POWER,
            bSportType = BSportType.BIKE,
            unit = MyUnits.METRIC,
            interpolatedAltitude = 642.0,
            powerZoneThresholds = powerThresholds,
            locale = Locale.US
        )

        assertEquals("21.50 km", readout.distanceText)
        assertEquals("56:10", readout.timeText)
        assertEquals("285 W", readout.selectedMetricText)
        assertEquals(4, readout.selectedZoneNumber)
        assertTrue("Secondary context must contain elevation + grade", readout.secondaryContextItems.any { it.contains("642 m") && it.contains("+4.2%") })
        assertTrue("Secondary context must contain HR", readout.secondaryContextItems.any { it == "164 bpm" })
        assertTrue("Secondary context must contain speed", readout.secondaryContextItems.any { it == "31.5 km/h" })
        assertFalse("Secondary context must not duplicate selected Power metric", readout.secondaryContextItems.any { it.contains("285 W") })
    }

    @Test
    fun formatFullScreenScrubReadout_runPaceAndImperialUnitsFormatAccurately() {
        val point = PathPoint(
            distance = 1609.344 * 5.0,
            latLng = origin,
            altitude = 304.8,
            timeSec = 1500L,
            hr = 158,
            speedMps = 1609.344 / 480.0 // 8:00 /mi
        )
        val hrThresholds = HeartRateZoneThresholds(z1Max = 120, z2Max = 140, z3Max = 155, z4Max = 170)

        val paceReadout = formatFullScreenScrubReadout(
            point = point,
            selectedMetric = FullScreenScrubberMetric.SPEED_PACE,
            bSportType = BSportType.RUN,
            unit = MyUnits.IMPERIAL,
            hrZoneThresholds = hrThresholds,
            locale = Locale.US
        )
        assertEquals("5.00 mi", paceReadout.distanceText)
        assertEquals("25:00", paceReadout.timeText)
        assertEquals("8:00 /mi", paceReadout.selectedMetricText)
        assertTrue(paceReadout.secondaryContextItems.any { it == "1000 ft" })
        assertTrue(paceReadout.secondaryContextItems.any { it == "158 bpm" })

        val hrReadout = formatFullScreenScrubReadout(
            point = point,
            selectedMetric = FullScreenScrubberMetric.HEART_RATE,
            bSportType = BSportType.RUN,
            unit = MyUnits.IMPERIAL,
            hrZoneThresholds = hrThresholds,
            locale = Locale.US
        )
        assertEquals("158 bpm", hrReadout.selectedMetricText)
        assertEquals(4, hrReadout.selectedZoneNumber)
    }

    @Test
    fun formatFullScreenScrubReadout_slopeSelectedFormatsGradeAndShowsAltitudeWithoutDuplicatingSlope() {
        val climbPoint = PathPoint(
            distance = 14_200.0,
            latLng = origin,
            altitude = 642.0,
            timeSec = 1820L,
            hr = 164,
            power = 285,
            speedMps = 6.5,
            slope = 4.2,
            cadence = 82
        )

        val climbReadout = formatFullScreenScrubReadout(
            point = climbPoint,
            selectedMetric = FullScreenScrubberMetric.SLOPE,
            bSportType = BSportType.BIKE,
            unit = MyUnits.METRIC,
            interpolatedAltitude = 642.0,
            locale = Locale.US
        )

        assertEquals("14.20 km", climbReadout.distanceText)
        assertEquals("30:20", climbReadout.timeText)
        assertEquals("+4.2%", climbReadout.selectedMetricText)
        assertNull("Slope metric must not emit a zone number", climbReadout.selectedZoneNumber)
        assertTrue("Secondary context must show altitude without duplicating slope", climbReadout.secondaryContextItems.any { it == "642 m" })
        assertFalse("Secondary context must not repeat +4.2%", climbReadout.secondaryContextItems.any { it.contains("4.2%") })
        assertTrue("Secondary context must include HR", climbReadout.secondaryContextItems.any { it == "164 bpm" })
        assertTrue("Secondary context must include Power", climbReadout.secondaryContextItems.any { it == "285 W" })

        val descentPoint = climbPoint.copy(slope = -6.5, altitude = 510.0)
        val descentReadout = formatFullScreenScrubReadout(
            point = descentPoint,
            selectedMetric = FullScreenScrubberMetric.SLOPE,
            bSportType = BSportType.BIKE,
            unit = MyUnits.METRIC,
            interpolatedAltitude = 510.0,
            locale = Locale.US
        )
        assertEquals("-6.5%", descentReadout.selectedMetricText)
        assertTrue(descentReadout.secondaryContextItems.any { it == "510 m" })
    }
}
