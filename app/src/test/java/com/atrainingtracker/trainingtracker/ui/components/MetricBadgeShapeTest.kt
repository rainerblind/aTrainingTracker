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

package com.atrainingtracker.trainingtracker.ui.components

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit and contract tests for [MetricBadge] shape customization (REQ-UI-352, TST-UI-312.2).
 */
class MetricBadgeShapeTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val metricBadgeFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/MetricBadge.kt")
    }

    @Test
    fun testMetricBadge_composableSupportsCustomShape() {
        assertTrue("MetricBadge.kt must exist", metricBadgeFile.exists())
        val content = metricBadgeFile.readText()

        assertTrue(
            "MetricBadge must declare shape parameter with default RoundedCornerShape(4.dp)",
            content.contains("shape: Shape = RoundedCornerShape(4.dp)")
        )
        assertTrue(
            "MetricBadge must apply shape parameter to Surface",
            content.contains("shape = shape")
        )
    }

    @Test
    fun testMetricBadge_reflectionCheck() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.MetricBadgeKt")
        assertNotNull("MetricBadgeKt class must exist", clazz)
        val method = clazz.declaredMethods.find { it.name.startsWith("MetricBadge") }
        assertNotNull("MetricBadge composable function must exist", method)
    }
}
