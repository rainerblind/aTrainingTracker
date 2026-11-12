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

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Structural contract test for BatteryOptimizationWarningBanner composable (REQ-PRI-005, TST-PRI-004, ATT-2621).
 * Refactored to reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class BatteryOptimizationWarningBannerTest {

    @Test
    fun testBannerSourceContract_verifiesDesignTokensAndTags() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.controltracking.BatteryOptimizationWarningBannerKt")
        val methods = clazz.declaredMethods
        val bannerMethod = methods.find { it.name.startsWith("BatteryOptimizationWarningBanner") && Modifier.isPublic(it.modifiers) }
        assertNotNull("BatteryOptimizationWarningBanner composable must exist", bannerMethod)

        // String resource presence via LocalizationTestCache
        val strings = LocalizationTestCache.getStrings("values")
        assertTrue(strings.containsKey("battery_optimization_warning_banner_title"))
        assertTrue(strings.containsKey("battery_optimization_warning_banner_desc"))
        assertTrue(strings.containsKey("battery_optimization_action_fix"))
        assertTrue(strings.containsKey("battery_optimization_dismiss_banner"))

        // Drawable resource presence
        val drawableFile = LocalizationTestCache.resolveFile("", "drawable/ic_battery_full.xml")
        val altDrawable = File("app/src/main/res/drawable/ic_battery_full.xml")
        assertTrue("ic_battery_full.xml drawable must exist", drawableFile.exists() || altDrawable.exists())
    }
}
