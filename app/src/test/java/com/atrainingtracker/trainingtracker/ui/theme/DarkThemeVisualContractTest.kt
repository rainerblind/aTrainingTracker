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

package com.atrainingtracker.trainingtracker.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Visual contract tests verifying card border boundaries and AMOLED theme preservation
 * (REQ-UI-190, TST-UI-144.2, ATT-1585).
 */
class DarkThemeVisualContractTest {

    @Test
    fun testMappableListItem_appliesOutlineVariantBorderStroke() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/components/MappableListItem.kt")
        assertTrue("MappableListItem.kt must exist", file.exists())
        val content = file.readText()

        assertTrue(
            "MappableListItem must apply BorderStroke using outlineVariant",
            content.contains("BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))")
        )
    }

    @Test
    fun testAmoledTheme_preservesPureBlackInvariants() {
        assertEquals(
            "AMOLED background must remain pitch black (#000000)",
            Color(0xFF000000),
            AmoledDarkColorScheme.background
        )
        assertEquals(
            "AMOLED surface must remain pitch black (#000000)",
            Color(0xFF000000),
            AmoledDarkColorScheme.surface
        )
        assertEquals(
            "AMOLED outlineVariant must remain #262626 (REQ-UI-171)",
            Color(0xFF262626),
            AmoledDarkColorScheme.outlineVariant
        )
    }
}
