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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract test for BatteryOptimizationWarningBanner composable (REQ-PRI-005, TST-PRI-004, ATT-2621).
 */
class BatteryOptimizationWarningBannerTest {

    @Test
    fun testBannerSourceContract_verifiesDesignTokensAndTags() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/BatteryOptimizationWarningBanner.kt")
        assertTrue("BatteryOptimizationWarningBanner.kt must exist", file.exists())
        val content = file.readText()

        // Test tags
        assertTrue(content.contains("testTag(\"BatteryOptimizationWarningBanner\")"))
        assertTrue(content.contains("testTag(\"BatteryOptimizationWarningBannerDismiss\")"))
        assertTrue(content.contains("testTag(\"BatteryOptimizationWarningBannerAction\")"))

        // Material 3 & Design Guidelines compliance
        assertTrue(content.contains("RoundedCornerShape(12.dp)"))
        assertTrue(content.contains("MaterialTheme.colorScheme.errorContainer"))
        assertTrue(content.contains("MaterialTheme.colorScheme.onErrorContainer"))
        assertTrue(content.contains("R.drawable.ic_battery_full"))
        assertTrue(content.contains("R.string.battery_optimization_warning_banner_title"))
        assertTrue(content.contains("R.string.battery_optimization_warning_banner_desc"))
        assertTrue(content.contains("R.string.battery_optimization_action_fix"))
        assertTrue(content.contains("R.string.battery_optimization_dismiss_banner"))
    }
}
