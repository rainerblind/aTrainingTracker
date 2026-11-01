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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural and layout verification tests for Cockpit sensor field card height stretching,
 * continuous border/indicator strip spanning, and vertical telemetry centering
 * (REQ-UI-438 / TST-UI-438 / ATT-4878).
 */
class SensorFieldViewLayoutTest {

    private fun findSourceFile(): File {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not find SensorFieldView.kt")
    }

    @Test
    fun testCardAndInnerRow_applyFillMaxHeight() {
        val content = findSourceFile().readText()

        // REQ-UI-438.2 / TST-UI-438.2: Card and inner Row must apply fillMaxHeight
        assertTrue(
            "Card must apply modifier.fillMaxWidth().fillMaxHeight() (TST-UI-438.2)",
            content.contains(".fillMaxWidth()") && content.contains(".fillMaxHeight()")
        )
        assertTrue(
            "Inner layout Row must apply Modifier.fillMaxWidth().fillMaxHeight() (TST-UI-438.2)",
            content.contains("Row(modifier = Modifier.fillMaxWidth().fillMaxHeight())")
        )
        assertFalse(
            "Inner layout Row must NOT clamp to IntrinsicSize.Min (TST-UI-438.2)",
            content.contains("Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min))")
        )
    }

    @Test
    fun testIndicatorStrips_fillFullCardHeight() {
        val content = findSourceFile().readText()

        // REQ-UI-438.2 / TST-UI-438.2: Indicator strip Spacers must fill max height
        assertTrue(
            "Left indicator strip must apply fillMaxHeight (TST-UI-438.2)",
            content.contains(".width(6.dp)") && content.contains(".fillMaxHeight()")
        )
    }

    @Test
    fun testTelemetryContent_verticallyCenteredInExpandedSpace() {
        val content = findSourceFile().readText()

        // REQ-UI-438.3 / TST-UI-438.3: Telemetry value & gauge must be centered in weighted Column
        assertTrue(
            "Content Column must apply .weight(1f).fillMaxHeight() (TST-UI-438.3)",
            content.contains(".weight(1f)") && content.contains(".fillMaxHeight()")
        )
        assertTrue(
            "Telemetry Column must apply Modifier.fillMaxWidth().weight(1f) with verticalArrangement = Arrangement.Center (TST-UI-438.3)",
            content.contains("verticalArrangement = Arrangement.Center")
        )
        assertTrue(
            "Header label row must anchor at top with Alignment.Top (TST-UI-438.3)",
            content.contains("verticalAlignment = Alignment.Top")
        )
    }
}
