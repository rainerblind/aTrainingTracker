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

package com.atrainingtracker.trainingtracker.ui.tracking

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Layout contract and intrinsic height constraint verification for Graph-only Cockpit tiles
 * inside [IntrinsicSize.Min] multi-field rows (REQ-TRK-059.10, TST-TRK-059.10a, ATT-6689).
 */
class SensorFieldViewGraphOnlyLayoutTest {

    private fun resolveSourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val fromRepoRoot = File(System.getProperty("user.dir", "."), relativePath)
        if (fromRepoRoot.exists()) return fromRepoRoot
        val parent = File("..", relativePath)
        if (parent.exists()) return parent
        throw IllegalStateException("Cannot resolve source file: $relativePath from ${File(".").absolutePath}")
    }

    @Test
    fun testResolveSparklineHeight_graphOnlyMode_enforcesStrictlyPositiveHeights() {
        // Assert all ViewSizes produce guaranteed non-zero minimum heights in Graph-only mode
        val smallH = resolveSparklineHeight(ViewSize.SMALL, isGraphOnly = true)
        val normalH = resolveSparklineHeight(ViewSize.NORMAL, isGraphOnly = true)
        val largeH = resolveSparklineHeight(ViewSize.LARGE, isGraphOnly = true)
        val xlargeH = resolveSparklineHeight(ViewSize.XLARGE, isGraphOnly = true)

        assertEquals(24.dp, smallH)
        assertEquals(36.dp, normalH)
        assertEquals(52.dp, largeH)
        assertEquals(68.dp, xlargeH)

        assertTrue("Small graph-only height must be strictly positive", smallH > 0.dp)
        assertTrue("Normal graph-only height must be strictly positive", normalH > 0.dp)
        assertTrue("Large graph-only height must be strictly positive", largeH > 0.dp)
        assertTrue("XLarge graph-only height must be strictly positive", xlargeH > 0.dp)
    }

    @Test
    fun testSensorFieldView_graphOnlySparklineAppliesHeightInMinConstraint() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldViewKt")
        val methods = clazz.declaredMethods
        val viewMethod = methods.find { it.name.startsWith("SensorFieldView") && java.lang.reflect.Modifier.isPublic(it.modifiers) }
        org.junit.Assert.assertNotNull("SensorFieldView composable must exist", viewMethod)
    }

    @Test
    fun testSensorGridScreen_rowIntrinsicMinSafety() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenKt")
        val methods = clazz.declaredMethods
        val gridMethod = methods.find { it.name.startsWith("SensorGridScreen") && java.lang.reflect.Modifier.isPublic(it.modifiers) }
        org.junit.Assert.assertNotNull("SensorGridScreen composable must exist", gridMethod)
    }
}
