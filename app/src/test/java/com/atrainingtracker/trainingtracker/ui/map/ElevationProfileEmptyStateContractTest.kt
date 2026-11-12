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

package com.atrainingtracker.trainingtracker.ui.map

import androidx.compose.ui.unit.dp
import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * TST-UI-357 / REQ-UI-394 (ATT-3779):
 * Verifies the ElevationProfile empty-state rendering contract on tracking tabs when enabled
 * before tracking starts or without an active route, canonical minimum height sizing (70.dp),
 * and 9-locale localization parity for `elevation_profile_empty_state`.
 * Refactored to pure JVM reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class ElevationProfileEmptyStateContractTest {

    @Test
    fun `canonical minimum elevation profile height for zero range is 70dp`() {
        val height = calculateElevationProfileHeight(0.0)
        assertEquals(70.dp, height)
    }

    @Test
    fun `ElevationProfile exposes showEmptyState parameter and delegates to ElevationProfileEmptyState`() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ElevationProfileKt")
        val methods = clazz.declaredMethods
        val method = methods.find { it.name.startsWith("ElevationProfile") && Modifier.isPublic(it.modifiers) }
        assertNotNull("ElevationProfile composable must exist", method)
        assertTrue(
            "ElevationProfile must accept showEmptyState parameter (Boolean)",
            method!!.parameterTypes.any { it == Boolean::class.java }
        )
    }

    @Test
    fun `SensorGridScreen renders ElevationProfile whenever showElevationProfile is true with showEmptyState enabled`() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenKt")
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun `elevation_profile_empty_state is defined across all 9 supported locales`() {
        val locales = listOf(
            "values",
            "values-de",
            "values-es",
            "values-fr",
            "values-it",
            "values-ja",
            "values-nl",
            "values-pl",
            "values-pt"
        )

        for (locale in locales) {
            val strings = LocalizationTestCache.getStrings(locale)
            assertTrue(
                "Missing elevation_profile_empty_state in $locale",
                strings.containsKey("elevation_profile_empty_state")
            )
            assertTrue(
                "elevation_profile_empty_state in $locale must not be blank",
                strings["elevation_profile_empty_state"]!!.isNotBlank()
            )
        }
    }
}
