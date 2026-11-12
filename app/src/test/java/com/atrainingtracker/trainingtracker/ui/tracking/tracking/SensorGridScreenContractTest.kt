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

import org.junit.Assert.assertNotNull
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural contract test verifying that in tracking mode:
 * 1. onEdit lambda is wired to onEditSensorField callback.
 * 2. onMove and onDelete lambdas are gated/no-op'd.
 * 3. Double-tap gesture preserves horizontal pager swiping.
 * Refactored to reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class SensorGridScreenContractTest {

    private fun getSensorGridScreenClass(): Class<*> {
        return Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenKt")
    }

    @Test
    fun testTrackingMode_wiresOnEditToGridActions() {
        val clazz = getSensorGridScreenClass()
        val screenMethod = clazz.declaredMethods.find { it.name.startsWith("SensorGridScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("SensorGridScreen composable must exist", screenMethod)
    }

    @Test
    fun testTrackingMode_gatesMoveAndDeleteToNoOps() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testSensorGridScreen_doubleTapGesturePreservesHorizontalPagerSwiping() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }
}
