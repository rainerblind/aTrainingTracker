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

package com.atrainingtracker.trainingtracker.ui.athleteprofile

import com.atrainingtracker.testutil.MockCursorFactory
import com.atrainingtracker.trainingtracker.database.AthleteMetricEntry
import com.atrainingtracker.trainingtracker.database.AthleteMetricType
import com.atrainingtracker.trainingtracker.database.AthleteMetricsDbHelper
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Unit tests for [AthleteMetricEntry] DTO cursor symmetry (Rule 20), [AthleteMetricType] bounds,
 * and [AthleteMetricsPlotMath] derived `W/kg` time-series calculation (`REQ-UI-391.2`, `REQ-UI-391.4`, `TST-UI-354.1`).
 */
class AthleteMetricsDtoAndMathTest {

    @Test
    fun `fromCursor and fromCursorBatch map columns symmetrically without closing cursor`() {
        val columns = listOf(
            AthleteMetricsDbHelper.C_ID,
            AthleteMetricsDbHelper.TIMESTAMP_MS,
            AthleteMetricsDbHelper.METRIC_TYPE,
            AthleteMetricsDbHelper.VALUE
        )
        val rows = listOf(
            listOf<Any?>(11L, 1_700_000_000_000L, "WEIGHT_KG", 72.4),
            listOf<Any?>(12L, 1_700_086_400_000L, "HR_REST_BPM", 49.0),
            listOf<Any?>(13L, 1_700_172_800_000L, "FTP_WATTS", 290.0)
        )

        val cursorSingle = MockCursorFactory.create(columns, rows)
        assertTrue(cursorSingle.moveToFirst())
        val firstFromSingle = AthleteMetricEntry.fromCursor(cursorSingle)
        verify(exactly = 0) { cursorSingle.close() }

        val cursorBatch = MockCursorFactory.create(columns, rows)
        val batchList = AthleteMetricEntry.fromCursorBatch(cursorBatch)
        verify(exactly = 0) { cursorBatch.close() }

        assertEquals(3, batchList.size)
        assertEquals(firstFromSingle, batchList[0])
        assertEquals(AthleteMetricType.WEIGHT_KG, batchList[0].metricType)
        assertEquals(72.4, batchList[0].value, 1e-6)
        assertEquals(AthleteMetricType.HR_REST_BPM, batchList[1].metricType)
        assertEquals(49.0, batchList[1].value, 1e-6)
        assertEquals(AthleteMetricType.FTP_WATTS, batchList[2].metricType)
        assertEquals(290.0, batchList[2].value, 1e-6)
    }

    @Test
    fun `AthleteMetricType validates canonical bounds`() {
        assertTrue(AthleteMetricType.WEIGHT_KG.isValidMetricValue(30.0))
        assertTrue(AthleteMetricType.WEIGHT_KG.isValidMetricValue(72.5))
        assertTrue(AthleteMetricType.WEIGHT_KG.isValidMetricValue(300.0))
        assertFalse(AthleteMetricType.WEIGHT_KG.isValidMetricValue(29.9))
        assertFalse(AthleteMetricType.WEIGHT_KG.isValidMetricValue(300.1))

        assertTrue(AthleteMetricType.HR_REST_BPM.isValidMetricValue(30.0))
        assertTrue(AthleteMetricType.HR_REST_BPM.isValidMetricValue(50.0))
        assertTrue(AthleteMetricType.HR_REST_BPM.isValidMetricValue(120.0))
        assertFalse(AthleteMetricType.HR_REST_BPM.isValidMetricValue(29.0))
        assertFalse(AthleteMetricType.HR_REST_BPM.isValidMetricValue(121.0))

        assertTrue(AthleteMetricType.FTP_WATTS.isValidMetricValue(50.0))
        assertTrue(AthleteMetricType.FTP_WATTS.isValidMetricValue(280.0))
        assertTrue(AthleteMetricType.FTP_WATTS.isValidMetricValue(600.0))
        assertFalse(AthleteMetricType.FTP_WATTS.isValidMetricValue(49.0))
        assertFalse(AthleteMetricType.FTP_WATTS.isValidMetricValue(601.0))
    }

    @Test
    fun `computeDerivedPowerToWeightSeries pairs active FTP and Weight chronologically and formats to 2 decimals`() {
        val t1 = 1_700_000_000_000L
        val t2 = t1 + 86_400_000L
        val t3 = t2 + 86_400_000L
        val t4 = t3 + 86_400_000L

        val entries = listOf(
            AthleteMetricEntry(1L, t1, AthleteMetricType.WEIGHT_KG, 75.0),
            AthleteMetricEntry(2L, t2, AthleteMetricType.FTP_WATTS, 300.0),
            AthleteMetricEntry(3L, t3, AthleteMetricType.WEIGHT_KG, 72.0),
            AthleteMetricEntry(4L, t4, AthleteMetricType.FTP_WATTS, 310.0)
        )

        val derived = AthleteMetricsPlotMath.computeDerivedPowerToWeightSeries(entries)
        // At t1 only weight exists -> no W/kg point yet. Points emitted at t2, t3, t4.
        assertEquals(3, derived.size)

        assertEquals(t2, derived[0].timestampMs)
        assertEquals(300.0 / 75.0, derived[0].value, 1e-6)
        assertEquals("4.00", AthleteMetricsPlotMath.formatPowerToWeightValue(derived[0].value, Locale.US))

        assertEquals(t3, derived[1].timestampMs)
        assertEquals(300.0 / 72.0, derived[1].value, 1e-6)
        assertEquals("4.17", AthleteMetricsPlotMath.formatPowerToWeightValue(derived[1].value, Locale.US))

        assertEquals(t4, derived[2].timestampMs)
        assertEquals(310.0 / 72.0, derived[2].value, 1e-6)
        assertEquals("4.31", AthleteMetricsPlotMath.formatPowerToWeightValue(derived[2].value, Locale.US))

        assertNull(AthleteMetricsPlotMath.computeCurrentPowerToWeight(null, 72.0))
        assertNull(AthleteMetricsPlotMath.computeCurrentPowerToWeight(300.0, null))
        assertNotNull(AthleteMetricsPlotMath.computeCurrentPowerToWeight(300.0, 72.0))
    }

    @Test
    fun `filterAndConvertSeries filters by MetricsTimeRange and converts weight for Imperial units`() {
        val dayMs = 86_400_000L
        val nowMs = 1_760_000_000_000L
        val entries = listOf(
            AthleteMetricEntry(1L, nowMs - 400 * dayMs, AthleteMetricType.WEIGHT_KG, 76.0),
            AthleteMetricEntry(2L, nowMs - 100 * dayMs, AthleteMetricType.WEIGHT_KG, 74.0),
            AthleteMetricEntry(3L, nowMs - 10 * dayMs, AthleteMetricType.WEIGHT_KG, 72.0)
        )

        val oneMonth = AthleteMetricsPlotMath.filterAndConvertSeries(
            entries = entries,
            metricType = PlotMetricType.WEIGHT,
            timeRange = MetricsTimeRange.ONE_MONTH,
            nowMs = nowMs,
            isImperial = false
        )
        assertEquals(1, oneMonth.size)
        assertEquals(72.0, oneMonth[0].value, 1e-6)

        val sixMonthsImperial = AthleteMetricsPlotMath.filterAndConvertSeries(
            entries = entries,
            metricType = PlotMetricType.WEIGHT,
            timeRange = MetricsTimeRange.SIX_MONTHS,
            nowMs = nowMs,
            isImperial = true
        )
        assertEquals(2, sixMonthsImperial.size)
        assertEquals(74.0 * SettingsDataStore.LBS_PER_KG, sixMonthsImperial[0].value, 1e-3)
        assertEquals(72.0 * SettingsDataStore.LBS_PER_KG, sixMonthsImperial[1].value, 1e-3)

        val allPoints = AthleteMetricsPlotMath.filterAndConvertSeries(
            entries = entries,
            metricType = PlotMetricType.WEIGHT,
            timeRange = MetricsTimeRange.ALL,
            nowMs = nowMs,
            isImperial = false
        )
        assertEquals(3, allPoints.size)
    }
}
