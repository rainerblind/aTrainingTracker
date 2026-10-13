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

package com.atrainingtracker.trainingtracker.ui.equipment

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract and behavior verification for compact equipment card layout
 * and harmonized inter-card spacing (REQ-UI-257, TST-UI-216, ATT-2059).
 */
class EquipmentCardSpacingContractTest {

    private fun findFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("File not found in candidates: $relativePath")
    }

    @Test
    fun testLayoutConstants_conformsToCompactSpecifications() {
        assertEquals("CARD_SPACING must be 6.dp", 6.dp, EquipmentLayoutConstants.CARD_SPACING)
        assertEquals("CARD_HORIZONTAL_MARGIN must be 4.dp", 4.dp, EquipmentLayoutConstants.CARD_HORIZONTAL_MARGIN)
        assertEquals("CARD_VERTICAL_MARGIN must be 0.dp", 0.dp, EquipmentLayoutConstants.CARD_VERTICAL_MARGIN)
        assertEquals("COMPACT_INNER_VERTICAL_PADDING must be 8.dp", 8.dp, EquipmentLayoutConstants.COMPACT_INNER_VERTICAL_PADDING)
        assertEquals("STANDARD_INNER_VERTICAL_PADDING must be 12.dp", 12.dp, EquipmentLayoutConstants.STANDARD_INNER_VERTICAL_PADDING)
        assertEquals("SUBTITLE_SPACING must be 4.dp", 4.dp, EquipmentLayoutConstants.SUBTITLE_SPACING)
        assertEquals("RETIRED_ALPHA must be 0.75f", 0.75f, EquipmentLayoutConstants.RETIRED_ALPHA, 0.001f)
        assertEquals("MIN_TOUCH_TARGET_HEIGHT must be 48.dp", 48.dp, EquipmentLayoutConstants.MIN_TOUCH_TARGET_HEIGHT)
    }

    @Test
    fun testAdaptiveLayoutLogic_inactiveItem_resolvesCompactParameters() {
        val workouts = 0
        val isCompact = workouts == 0
        assertTrue("Item with 0 workouts must be compact", isCompact)

        val innerPadding = if (isCompact) {
            EquipmentLayoutConstants.COMPACT_INNER_VERTICAL_PADDING
        } else {
            EquipmentLayoutConstants.STANDARD_INNER_VERTICAL_PADDING
        }
        assertEquals(8.dp, innerPadding)
    }

    @Test
    fun testAdaptiveLayoutLogic_activeItem_resolvesStandardParameters() {
        val workouts = 42
        val isCompact = workouts == 0
        assertTrue("Item with >0 workouts must not be compact", !isCompact)

        val innerPadding = if (isCompact) {
            EquipmentLayoutConstants.COMPACT_INNER_VERTICAL_PADDING
        } else {
            EquipmentLayoutConstants.STANDARD_INNER_VERTICAL_PADDING
        }
        assertEquals(12.dp, innerPadding)
    }

    @Test
    fun testRetiredItem_appliesSubduedAlpha() {
        val isRetired = true
        val alpha = if (isRetired) EquipmentLayoutConstants.RETIRED_ALPHA else 1.0f
        assertEquals(0.75f, alpha, 0.001f)

        val isNotRetired = false
        val activeAlpha = if (isNotRetired) EquipmentLayoutConstants.RETIRED_ALPHA else 1.0f
        assertEquals(1.0f, activeAlpha, 0.001f)
    }

    @Test
    fun testTouchTarget_satisfiesMinimumAccessibilityHeight() {
        assertTrue(
            "Minimum touch target must meet or exceed WCAG 48.dp requirement",
            EquipmentLayoutConstants.MIN_TOUCH_TARGET_HEIGHT.value >= 48f
        )
    }

    @Test
    fun testEquipmentTabsScreen_contractIntegrity() {
        val screenFile = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentTabsScreen.kt")
        val content = screenFile.readText()

        // 1. SpacedBy in EquipmentList must use CARD_SPACING
        assertTrue(
            "EquipmentList must use EquipmentLayoutConstants.CARD_SPACING",
            content.contains("Arrangement.spacedBy(EquipmentLayoutConstants.CARD_SPACING)")
        )

        // 2. Card container must eliminate vertical margin and enforce defaultMinSize
        assertTrue(
            "EquipmentItem container must use CARD_HORIZONTAL_MARGIN and CARD_VERTICAL_MARGIN",
            content.contains("horizontal = EquipmentLayoutConstants.CARD_HORIZONTAL_MARGIN") &&
                    content.contains("vertical = EquipmentLayoutConstants.CARD_VERTICAL_MARGIN")
        )
        assertTrue(
            "EquipmentItem must enforce MIN_TOUCH_TARGET_HEIGHT",
            content.contains("defaultMinSize(minHeight = EquipmentLayoutConstants.MIN_TOUCH_TARGET_HEIGHT)")
        )

        // 3. Alpha must be applied to MappableListItem
        assertTrue(
            "MappableListItem must receive cardAlpha",
            content.contains("alpha = cardAlpha")
        )

        // 4. Adaptive inner vertical padding
        assertTrue(
            "EquipmentItem must adapt inner padding between compact and standard",
            content.contains("if (isCompact) EquipmentLayoutConstants.COMPACT_INNER_VERTICAL_PADDING else EquipmentLayoutConstants.STANDARD_INNER_VERTICAL_PADDING")
        )

        // 5. Adaptive typography
        assertTrue(
            "EquipmentItem must adapt title typography between titleMedium and titleLarge",
            content.contains("if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge")
        )

        // 6. Preview coverage for retired equipment
        assertTrue(
            "PreviewEquipmentCardRetired preview must be defined",
            content.contains("fun PreviewEquipmentCardRetired()")
        )
    }
}
