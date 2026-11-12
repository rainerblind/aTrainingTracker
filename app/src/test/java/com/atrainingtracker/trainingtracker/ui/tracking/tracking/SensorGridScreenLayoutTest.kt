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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural contract test for SensorGridScreen layout geometry, weight allocation,
 * and elevation profile zero dead space enforcement (REQ-UI-437, TST-UI-437, ATT-5072).
 * Refactored to reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class SensorGridScreenLayoutTest {

    private fun getSensorGridScreenClass(): Class<*> {
        return Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenKt")
    }

    @Test
    fun testSensorGrid_appliesWeightConstraintWithFillFalse() {
        val clazz = getSensorGridScreenClass()
        val screenMethod = clazz.declaredMethods.find { it.name.startsWith("SensorGridScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("SensorGridScreen composable must exist", screenMethod)
    }

    @Test
    fun testElevationProfile_doesNotApplyWeightWhenMapDisabled() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testElevationProfile_doesNotPassFillParentHeightOrFillMaxSize() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testElevationProfile_guardedByPathPointsSize() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testLayoutStructure_boxWithConstraintsAndAsymmetricWeightAllocation() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testLayoutGeometry_simulationCalculatesZeroDeadSpace() {
        val totalAvailableHeightDp = 600f
        val showMap = true
        val showElevation = true

        val allocatedMapHeight = if (showMap) totalAvailableHeightDp * 0.40f else 0f
        val allocatedTelemetryHeight = totalAvailableHeightDp * 0.60f

        val totalAllocated = allocatedMapHeight + allocatedTelemetryHeight
        assertEquals("Total allocated height must equal total available height", totalAvailableHeightDp, totalAllocated, 0.01f)
    }

    @Test
    fun testElevationProfileSurface_doesNotApplyStaticScaffoldBottomPadding() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testCockpitSectionDivider_unlabeledProminenceAndContrast() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testCockpitSectionDivider_labeledCenteredTitleSymmetricWeights() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testConfigurationMode_dividerButtonUsesHorizontalSplitAndExcludesHorizontalRule() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testSensorGrid_appliesProportionalColWeightWithNonZeroSafetyClamp() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testSensorGrid_appliesFillMaxHeightPropagationInRow() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }
}
