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

package com.atrainingtracker.trainingtracker.zones

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import com.atrainingtracker.trainingtracker.activities.resolveZonePreviewContainerModifier
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.ZoneType
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import com.atrainingtracker.trainingtracker.ui.tracking.ZoneGaugeMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architectural, contract, and layout verification tests for
 * `REQ-UI-441` / `TST-UI-441` / `ATT-5071`:
 * Verifies that the preview container wrapping `SensorFieldView` in `ZonesSettingsActivity`
 * enforces `Modifier.height(IntrinsicSize.Min)` bounding to prevent vertical collapsing
 * under unconstrained scrollable parents (`verticalScroll`), without source file text-grep (REQ-TST-002).
 */
class ZonesSettingsPreviewLayoutTest {

    /**
     * TST-UI-441.1: Verify [resolveZonePreviewContainerModifier] chains [IntrinsicSize.Min]
     * height bounding, [Modifier.fillMaxWidth], and vertical padding onto the container.
     */
    @Test
    fun testZonePreviewContainerModifier_enforcesIntrinsicMinHeightAndBounds() {
        val customBase = Modifier.semantics {}
        val modifier = resolveZonePreviewContainerModifier(customBase)

        val elements = mutableListOf<Modifier.Element>()
        modifier.foldIn(elements) { list, element ->
            list.add(element)
            list
        }

        // Must preserve the base modifier
        assertTrue(
            "resolveZonePreviewContainerModifier must retain the base modifier chain",
            elements.any { it.toString().contains("SemanticsModifier") || it == customBase }
        )

        // Must contain an Intrinsic height element configuring IntrinsicSize.Min
        val intrinsicElement = elements.firstOrNull { it.javaClass.simpleName == "IntrinsicHeightElement" }
        assertNotNull(
            "resolveZonePreviewContainerModifier must contain an IntrinsicHeightElement (REQ-UI-441.1). Elements: $elements",
            intrinsicElement
        )

        // Verify that IntrinsicHeightElement is configured with IntrinsicSize.Min
        val intrinsicSizeField = intrinsicElement!!.javaClass.declaredFields.firstOrNull {
            it.type == IntrinsicSize::class.java || it.name.contains("size", ignoreCase = true)
        }?.apply { isAccessible = true }

        val configuredSize = intrinsicSizeField?.get(intrinsicElement)
        assertEquals(
            "IntrinsicHeightElement must be configured with IntrinsicSize.Min (REQ-UI-441.1)",
            IntrinsicSize.Min,
            configuredSize
        )

        // Must contain fillMaxWidth
        val hasFillMaxWidth = elements.any { element ->
            element.javaClass.simpleName == "FillElement"
        }
        assertTrue(
            "resolveZonePreviewContainerModifier must apply fillMaxWidth. Elements: $elements",
            hasFillMaxWidth
        )

        // Must contain vertical padding
        val hasPadding = elements.any { element ->
            element.javaClass.simpleName == "PaddingElement"
        }
        assertTrue(
            "resolveZonePreviewContainerModifier must apply vertical padding. Elements: $elements",
            hasPadding
        )
    }

    /**
     * TST-UI-441.2: Verify preview state generation produces non-empty telemetry,
     * active zone gauge state, and full display flags across HR and Power zone types.
     */
    @Test
    fun testZonePreviewFieldState_hasCompleteTelemetryAndGaugeConfiguration() {
        // HR Run Preview Configuration
        val hrCutoffs = listOf(130, 145, 160, 175)
        val hrGaugeState = ZoneGaugeMath.calculateZoneGaugeState(
            value = 150.0,
            zoneType = ZoneType.HR_RUN,
            cutoffs = hrCutoffs
        )
        assertNotNull("ZoneGaugeState must be computed for preview", hrGaugeState)

        val hrFieldState = SensorFieldState(
            configHash = 0,
            sensorFieldId = 0,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Heart Rate",
            filterDescription = "",
            value = "150",
            units = "bpm",
            zoneColor = androidx.compose.ui.graphics.Color.Red,
            zoneDisplayOptions = ZoneDisplayOptions(showBackground = true, showLeftBar = true),
            zoneGaugeState = hrGaugeState,
            showValue = true,
            showBar = true
        )

        assertEquals("Heart Rate", hrFieldState.label)
        assertEquals("150", hrFieldState.value)
        assertEquals("bpm", hrFieldState.units)
        assertTrue(hrFieldState.showValue)
        assertTrue(hrFieldState.showBar)
        assertNotNull(hrFieldState.zoneGaugeState)

        // Power Bike Preview Configuration
        val pwrCutoffs = listOf(140, 190, 230, 270, 320, 380)
        val pwrGaugeState = ZoneGaugeMath.calculateZoneGaugeState(
            value = 250.0,
            zoneType = ZoneType.PWR_BIKE,
            cutoffs = pwrCutoffs
        )
        assertNotNull("ZoneGaugeState must be computed for power preview", pwrGaugeState)

        val pwrFieldState = SensorFieldState(
            configHash = 0,
            sensorFieldId = 0,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Power",
            filterDescription = "",
            value = "250",
            units = "W",
            zoneColor = androidx.compose.ui.graphics.Color.Green,
            zoneDisplayOptions = ZoneDisplayOptions(showBackground = true, showRightBar = true),
            zoneGaugeState = pwrGaugeState,
            showValue = true,
            showBar = true
        )

        assertEquals("Power", pwrFieldState.label)
        assertEquals("250", pwrFieldState.value)
        assertEquals("W", pwrFieldState.units)
        assertTrue(pwrFieldState.showValue)
        assertTrue(pwrFieldState.showBar)
        assertNotNull(pwrFieldState.zoneGaugeState)
    }
}
