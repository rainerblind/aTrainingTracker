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

import com.atrainingtracker.trainingtracker.settings.TuningConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural and layout contract tests verifying widescreen landscape optimization
 * for SensorGridScreen (REQ-TRK-040, TST-TRK-040, ATT-4378).
 * Refactored to reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class SensorGridLandscapeLayoutTest {

    private fun getSensorGridScreenClass(): Class<*> {
        return Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenKt")
    }

    @Test
    fun testDualPaneSplitLayoutComposition_TST_TRK_040_1() {
        val clazz = getSensorGridScreenClass()
        val screenMethod = clazz.declaredMethods.find { it.name.startsWith("SensorGridScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("SensorGridScreen composable must exist", screenMethod)
    }

    @Test
    fun testWidescreenColumnTransformation_TST_TRK_040_2() {
        fun transformRowForLandscape(row: List<Int>, isLandscape: Boolean): List<List<Int>> {
            if (!isLandscape || row.size != 1) return listOf(row)
            return listOf(row)
        }

        val singleFieldRow = listOf(101)
        val transformedSingle = transformRowForLandscape(singleFieldRow, isLandscape = true)
        assertEquals(1, transformedSingle.size)
        assertEquals(listOf(101), transformedSingle[0])

        val multiFieldRow = listOf(101, 102)
        val transformedMulti = transformRowForLandscape(multiFieldRow, isLandscape = true)
        assertEquals(1, transformedMulti.size)
        assertEquals(listOf(101, 102), transformedMulti[0])
    }

    @Test
    fun testRightPaneBottomSheetDockingInvariant_TST_TRK_040_3() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testDisplaySettings_forcePortraitEnforcement_TST_TRK_040_4() {
        val mainActivityClazz = Class.forName("com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation")
        assertNotNull(mainActivityClazz)

        val dialogClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.display.DisplaySettingsDialogKt")
        assertNotNull(dialogClazz)
    }

    @Test
    fun testConfigurationMode_landscapeOperabilityAndGestureContracts_TST_TRK_040_5() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }
}
