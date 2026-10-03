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

package com.atrainingtracker.trainingtracker.helpers

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [WorkoutSnapshotLayoutCalculator].
 * (TST-UI-159 / REQ-UI-205 / ATT-1393)
 */
class WorkoutSnapshotLayoutCalculatorTest {

    @Test
    fun testCalculateTotalHeight_allSectionsPresent() {
        val total = WorkoutSnapshotLayoutCalculator.calculateTotalHeight(
            headerHeight = 200,
            mapHeight = 800,
            elevationHeight = 400,
            analyticsHeight = 500,
            footerHeight = 125
        )
        // 200 + 800 + 400 + 500 + 125 = 2025
        assertEquals(2025, total)
    }

    @Test
    fun testCalculateTotalHeight_analyticsNullOrZero() {
        val total = WorkoutSnapshotLayoutCalculator.calculateTotalHeight(
            headerHeight = 200,
            mapHeight = 800,
            elevationHeight = 400,
            analyticsHeight = 0,
            footerHeight = 125
        )
        // 200 + 800 + 400 + 0 + 125 = 1525
        assertEquals(1525, total)
    }

    @Test
    fun testCalculateTotalHeight_negativeInputsSanitized() {
        val total = WorkoutSnapshotLayoutCalculator.calculateTotalHeight(
            headerHeight = -50,
            mapHeight = 500,
            elevationHeight = -10,
            analyticsHeight = -100,
            footerHeight = 125
        )
        // 0 + 500 + 0 + 0 + 125 = 625
        assertEquals(625, total)
    }

    @Test
    fun testCalculateScaleFactor() {
        val scale1 = WorkoutSnapshotLayoutCalculator.calculateScaleFactor(targetWidth = 1080, componentWidth = 1000)
        assertEquals(1.08f, scale1, 0.0001f)

        val scaleEqual = WorkoutSnapshotLayoutCalculator.calculateScaleFactor(targetWidth = 1080, componentWidth = 1080)
        assertEquals(1.0f, scaleEqual, 0.0001f)

        // Defensive handling for non-positive dimensions
        val scaleZeroWidth = WorkoutSnapshotLayoutCalculator.calculateScaleFactor(targetWidth = 1080, componentWidth = 0)
        assertEquals(1.0f, scaleZeroWidth, 0.0001f)

        val scaleNegativeTarget = WorkoutSnapshotLayoutCalculator.calculateScaleFactor(targetWidth = -100, componentWidth = 500)
        assertEquals(1.0f, scaleNegativeTarget, 0.0001f)
    }

    @Test
    fun testCalculateSectionOffsets_sequentialStacking() {
        val offsets = WorkoutSnapshotLayoutCalculator.calculateSectionOffsets(
            headerHeight = 200,
            mapHeight = 800,
            elevationHeight = 400,
            analyticsHeight = 500,
            footerHeight = 125
        )

        assertEquals(0f, offsets.headerTop, 0.001f)
        assertEquals(200f, offsets.mapTop, 0.001f)
        assertEquals(1000f, offsets.elevationTop, 0.001f)
        assertEquals(1400f, offsets.analyticsTop, 0.001f)
        assertEquals(1900f, offsets.footerTop, 0.001f)
        assertEquals(2025, offsets.totalHeight)
    }

    @Test
    fun testCalculateSectionOffsets_omittedAnalyticsCollapsesCleanly() {
        val offsets = WorkoutSnapshotLayoutCalculator.calculateSectionOffsets(
            headerHeight = 200,
            mapHeight = 800,
            elevationHeight = 400,
            analyticsHeight = 0,
            footerHeight = 125
        )

        assertEquals(0f, offsets.headerTop, 0.001f)
        assertEquals(200f, offsets.mapTop, 0.001f)
        assertEquals(1000f, offsets.elevationTop, 0.001f)
        assertEquals(1400f, offsets.analyticsTop, 0.001f)
        assertEquals(1400f, offsets.footerTop, 0.001f)
        assertEquals(1525, offsets.totalHeight)
    }
}
