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
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * State precedence and wiring verification tests for WorkoutSummary and TrackOnMapScreen (REQ-UI-372.8, TST-UI-335.9).
 * Verifies Tier 1 (interactive user override) vs. Tier 2 (global preference default).
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
    fun workoutSummary_sourceContractEnforcesReactivePreferenceAndOmitsModeSelector() {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("WorkoutSummary.kt not found in candidates: $candidates")

        val content = file.readText()
        assertTrue(
            "WorkoutSummary must declare nullable hrZoneDisplayMode defaulting to null",
            content.contains("mutableStateOf<ZoneCardDisplayMode?>(null)")
        )
        assertTrue(
            "WorkoutSummary must compute effectiveHrZoneDisplayMode using preferences.zoneCardDisplayMode",
            content.contains("hrZoneDisplayMode ?: preferences.zoneCardDisplayMode")
        )
        assertTrue(
            "WorkoutSummary must compute effectivePowerZoneDisplayMode using preferences.zoneCardDisplayMode",
            content.contains("powerZoneDisplayMode ?: preferences.zoneCardDisplayMode")
        )
        assertTrue(
            "WorkoutSummary must pass showModeSelector = false to zone cards (REQ-UI-388.3)",
            content.contains("showModeSelector = false")
        )
    }

    @Test
    fun zoneCards_sourceContractSupportsOptionalModeSelectorGating() {
        val cardFiles = listOf(
            "ui/aftermath/zones/HeartRateZoneDistributionCard.kt",
            "ui/aftermath/zones/PowerZoneDistributionCard.kt"
        )
        for (rel in cardFiles) {
            val candidates = listOf(
                File("app/src/main/java/com/atrainingtracker/trainingtracker/$rel"),
                File("src/main/java/com/atrainingtracker/trainingtracker/$rel"),
                File("../app/src/main/java/com/atrainingtracker/trainingtracker/$rel")
            )
            val file = candidates.firstOrNull { it.exists() }
                ?: error("Card file $rel not found")
            val content = file.readText()
            assertTrue(
                "$rel must declare showModeSelector: Boolean = true default parameter (REQ-UI-388.3)",
                content.contains("showModeSelector: Boolean = true")
            )
            assertTrue(
                "$rel must gate the mode switcher row on showModeSelector && distribution.entries.isNotEmpty()",
                content.contains("if (showModeSelector && distribution.entries.isNotEmpty())")
            )
        }
    }

    @Test
    fun trackOnMapScreen_sourceContractEnforcesReactiveDetailPreferenceAndLocalOverride() {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("TrackOnMapScreen.kt not found in candidates: $candidates")

        val content = file.readText()
        assertTrue(
            "TrackOnMapScreen must collect workoutDetailPreferencesFlow",
            content.contains("workoutDetailPreferencesFlow.collectAsState")
        )
        assertTrue(
            "TrackOnMapScreen must compute effectiveHrZoneDisplayMode using activeDetailPrefs.zoneCardDisplayMode (REQ-UI-388)",
            content.contains("hrZoneDisplayMode ?: activeDetailPrefs.zoneCardDisplayMode")
        )
        assertTrue(
            "TrackOnMapScreen must compute effectivePowerZoneDisplayMode using activeDetailPrefs.zoneCardDisplayMode (REQ-UI-388)",
            content.contains("powerZoneDisplayMode ?: activeDetailPrefs.zoneCardDisplayMode")
        )
    }

    @Test
    fun workoutMasksAndCardsSection_sourceContractExposesIndependentSummaryAndDetailZoneModeSelectors() {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/WorkoutMasksAndCardsSection.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/WorkoutMasksAndCardsSection.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/WorkoutMasksAndCardsSection.kt")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("WorkoutMasksAndCardsSection.kt not found in candidates: $candidates")

        val content = file.readText()
        assertTrue(
            "WorkoutMasksAndCardsSection must reference settings_zone_card_display_mode_title",
            content.contains("R.string.settings_zone_card_display_mode_title")
        )
        assertTrue(
            "WorkoutMasksAndCardsSection must support ZoneCardDisplayMode.STACKED_BAR, COLUMNS, and HISTOGRAM",
            content.contains("ZoneCardDisplayMode.STACKED_BAR") &&
                content.contains("ZoneCardDisplayMode.COLUMNS") &&
                content.contains("ZoneCardDisplayMode.HISTOGRAM")
        )
        assertTrue(
            "WorkoutMasksAndCardsSection must mutate workoutCardPrefs.zoneCardDisplayMode on Summary selection",
            content.contains("workoutCardPrefs.copy(zoneCardDisplayMode = mode)")
        )
        assertTrue(
            "WorkoutMasksAndCardsSection must mutate workoutDetailPrefs.zoneCardDisplayMode on Details selection",
            content.contains("workoutDetailPrefs.copy(zoneCardDisplayMode = mode)")
        )
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
            val detailPrefs = com.atrainingtracker.trainingtracker.WorkoutDetailPreferences(zoneCardDisplayMode = targetMode)
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
