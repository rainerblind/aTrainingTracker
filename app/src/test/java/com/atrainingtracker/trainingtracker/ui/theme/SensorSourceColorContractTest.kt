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
import kotlin.math.sqrt

/**
 * Contract and unit tests verifying the semantic green color tokens for SensorSourceDialog,
 * their WCAG 2.1 AA contrast compliance across Light, Dark Slate, and AMOLED surfaces,
 * and chromatic separation (REQ-UI-051, TST-UI-052, ATT-4374).
 */
class SensorSourceColorContractTest {

    private fun colorDistance(c1: Color, c2: Color): Float {
        val dr = c1.red - c2.red
        val dg = c1.green - c2.green
        val db = c1.blue - c2.blue
        return sqrt(dr * dr + dg * dg + db * db)
    }

    @Test
    fun testSensorSourceTokens_haveExactMaterialGreenValues() {
        assertEquals(
            "SensorSourceActive must be Material Green 800 (#2E7D32)",
            Color(0xFF2E7D32),
            TTColor.SensorSourceActive
        )
        assertEquals(
            "SensorBackupActive must be Material Green 600 (#43A047)",
            Color(0xFF43A047),
            TTColor.SensorBackupActive
        )
    }

    @Test
    fun testSensorSourceTokens_meetWcagContrastRequirements() {
        val lightBackground = Color(0xFFFFFFFF)
        val darkSurface = Color(0xFF16161A)
        val amoledBackground = Color(0xFF000000)

        // SensorSourceActive: Primary active source header
        val sourceContrastLight = TTColor.calculateContrastRatio(TTColor.SensorSourceActive, lightBackground)
        val sourceContrastDark = TTColor.calculateContrastRatio(TTColor.SensorSourceActive, darkSurface)
        val sourceContrastAmoled = TTColor.calculateContrastRatio(TTColor.SensorSourceActive, amoledBackground)

        assertTrue(
            "SensorSourceActive contrast on Light ($sourceContrastLight) must be >= 4.5:1",
            sourceContrastLight >= 4.5
        )
        assertTrue(
            "SensorSourceActive contrast on Dark ($sourceContrastDark) must be >= 3.0:1 (bold text)",
            sourceContrastDark >= 3.0
        )
        assertTrue(
            "SensorSourceActive contrast on AMOLED ($sourceContrastAmoled) must be >= 3.0:1 (bold text)",
            sourceContrastAmoled >= 3.0
        )

        // SensorBackupActive: Standby active backup header
        val backupContrastLight = TTColor.calculateContrastRatio(TTColor.SensorBackupActive, lightBackground)
        val backupContrastDark = TTColor.calculateContrastRatio(TTColor.SensorBackupActive, darkSurface)
        val backupContrastAmoled = TTColor.calculateContrastRatio(TTColor.SensorBackupActive, amoledBackground)

        assertTrue(
            "SensorBackupActive contrast on Light ($backupContrastLight) must be >= 3.0:1 (bold text)",
            backupContrastLight >= 3.0
        )
        assertTrue(
            "SensorBackupActive contrast on Dark ($backupContrastDark) must be >= 4.5:1",
            backupContrastDark >= 4.5
        )
        assertTrue(
            "SensorBackupActive contrast on AMOLED ($backupContrastAmoled) must be >= 4.5:1",
            backupContrastAmoled >= 4.5
        )
    }

    @Test
    fun testSensorSourceTokens_haveAdequateChromaticSeparation() {
        val distance = colorDistance(TTColor.SensorSourceActive, TTColor.SensorBackupActive)
        assertTrue(
            "Chromatic distance between SensorSourceActive and SensorBackupActive must be > 0.10, was $distance",
            distance > 0.10f
        )
    }
}
