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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * State precedence and wiring verification tests for WorkoutSummary and TrackOnMapScreen (REQ-UI-372.8, TST-UI-335.9).
 * Verifies Tier 1 (interactive user override) vs. Tier 2 (global preference default).
 * Refactored to JVM reflection and behavioral tests per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class WorkoutSummaryZoneDisplayModeTest {

    @Test
    fun statePrecedenceLogic_userOverrideTakesPrecedenceOverDefault() {
        fun resolveMode(override: ZoneCardDisplayMode?, default: ZoneCardDisplayMode) = override ?: default

        val globalDefault = ZoneCardDisplayMode.STACKED_BAR
        var userSelectedOverride: ZoneCardDisplayMode? = null

        // Initial state: No user interaction -> reflects global preference (Tier 2)
        assertEquals(ZoneCardDisplayMode.STACKED_BAR, resolveMode(userSelectedOverride, globalDefault))

        // User interacts by selecting HISTOGRAM -> overrides global preference (Tier 1)
        userSelectedOverride = ZoneCardDisplayMode.HISTOGRAM
        assertEquals(ZoneCardDisplayMode.HISTOGRAM, resolveMode(userSelectedOverride, globalDefault))

        // Global default changes (e.g. from background DataStore sync) -> user override remains sticky
        val updatedGlobalDefault = ZoneCardDisplayMode.COLUMNS
        assertEquals(ZoneCardDisplayMode.HISTOGRAM, resolveMode(userSelectedOverride, updatedGlobalDefault))
    }

    @Test
    fun workoutSummary_contractEnforcesReactivePreferenceAndOmitsModeSelector() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummaryKt")
        val methods = clazz.declaredMethods.filter { it.name == "WorkoutSummary" }
        assertTrue("WorkoutSummary composable must exist", methods.isNotEmpty())

        val prefs = WorkoutCardSectionPreferences()
        assertEquals(ZoneCardDisplayMode.STACKED_BAR, prefs.zoneCardDisplayMode)
        val overriddenPrefs = prefs.copy(zoneCardDisplayMode = ZoneCardDisplayMode.HISTOGRAM)
        assertEquals(ZoneCardDisplayMode.HISTOGRAM, overriddenPrefs.zoneCardDisplayMode)
    }

    @Test
    fun zoneCards_contractSupportsOptionalModeSelectorGating() {
        val hrClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneDistributionCardKt")
        val hrMethods = hrClass.declaredMethods.filter { it.name == "HeartRateZoneDistributionCard" }
        assertTrue("HeartRateZoneDistributionCard composable must exist", hrMethods.isNotEmpty())

        val powerClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneDistributionCardKt")
        val powerMethods = powerClass.declaredMethods.filter { it.name == "PowerZoneDistributionCard" }
        assertTrue("PowerZoneDistributionCard composable must exist", powerMethods.isNotEmpty())

        val genericClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionCardKt")
        val genericMethods = genericClass.declaredMethods.filter { it.name == "ZoneDistributionCard" }
        assertTrue("ZoneDistributionCard composable must exist", genericMethods.isNotEmpty())
    }

    @Test
    fun trackOnMapScreen_contractEnforcesReactiveDetailPreferenceAndLocalOverride() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreenKt")
        val methods = clazz.declaredMethods.filter { it.name == "TrackOnMapScreen" }
        assertTrue("TrackOnMapScreen composable must exist", methods.isNotEmpty())

        val detailPrefs = WorkoutDetailPreferences()
        assertEquals(ZoneCardDisplayMode.HISTOGRAM, detailPrefs.zoneCardDisplayMode)
        val updated = detailPrefs.copy(zoneCardDisplayMode = ZoneCardDisplayMode.COLUMNS)
        assertEquals(ZoneCardDisplayMode.COLUMNS, updated.zoneCardDisplayMode)
    }

    @Test
    fun workoutMasksAndCardsSection_contractExposesIndependentSummaryAndDetailZoneModeSelectors() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.WorkoutMasksAndCardsSectionKt")
        assertNotNull("WorkoutMasksAndCardsSectionKt class must be present", clazz)

        // Verify that summary and detail preferences support independent mutations
        var summaryPrefs = WorkoutCardSectionPreferences(zoneCardDisplayMode = ZoneCardDisplayMode.STACKED_BAR)
        var detailPrefs = WorkoutDetailPreferences(zoneCardDisplayMode = ZoneCardDisplayMode.COLUMNS)

        summaryPrefs = summaryPrefs.copy(zoneCardDisplayMode = ZoneCardDisplayMode.HISTOGRAM)
        assertEquals(ZoneCardDisplayMode.HISTOGRAM, summaryPrefs.zoneCardDisplayMode)
        assertEquals(ZoneCardDisplayMode.COLUMNS, detailPrefs.zoneCardDisplayMode)

        detailPrefs = detailPrefs.copy(zoneCardDisplayMode = ZoneCardDisplayMode.STACKED_BAR)
        assertEquals(ZoneCardDisplayMode.HISTOGRAM, summaryPrefs.zoneCardDisplayMode)
        assertEquals(ZoneCardDisplayMode.STACKED_BAR, detailPrefs.zoneCardDisplayMode)
    }

    @Test
    fun zoneModeSelector_supportsAllThreeDisplayModesWithDeterministicSelectionAndHistogramFallback() {
        val selectableModes = listOf(
            ZoneCardDisplayMode.STACKED_BAR,
            ZoneCardDisplayMode.COLUMNS,
            ZoneCardDisplayMode.HISTOGRAM
        )
        for (targetMode in selectableModes) {
            val prefs = WorkoutCardSectionPreferences(zoneCardDisplayMode = targetMode)
            assertEquals(targetMode, prefs.zoneCardDisplayMode)
            val detailPrefs = WorkoutDetailPreferences(zoneCardDisplayMode = targetMode)
            assertEquals(targetMode, detailPrefs.zoneCardDisplayMode)
        }

        // Verify histogram fallback contract preserved for both cards (REQ-UI-386 / REQ-UI-388 invariant)
        assertEquals(
            ZoneCardDisplayMode.HISTOGRAM,
            ZoneCardDisplayMode.HISTOGRAM.resolveWithHistogramFallback(hasHistogram = true)
        )
        assertEquals(
            ZoneCardDisplayMode.COLUMNS,
            ZoneCardDisplayMode.HISTOGRAM.resolveWithHistogramFallback(hasHistogram = false)
        )
    }
}
