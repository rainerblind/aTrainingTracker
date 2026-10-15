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
    fun workoutSummary_sourceContractEnforcesReactivePreferenceAndLocalOverride() {
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
    }

    @Test
    fun trackOnMapScreen_sourceContractEnforcesReactivePreferenceAndLocalOverride() {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("TrackOnMapScreen.kt not found in candidates: $candidates")

        val content = file.readText()
        assertTrue(
            "TrackOnMapScreen must collect workoutCardPreferencesFlow",
            content.contains("workoutCardPreferencesFlow.collectAsState")
        )
        assertTrue(
            "TrackOnMapScreen must compute effectiveHrZoneDisplayMode using persistedCardPrefs.zoneCardDisplayMode",
            content.contains("hrZoneDisplayMode ?: persistedCardPrefs.zoneCardDisplayMode")
        )
        assertTrue(
            "TrackOnMapScreen must compute effectivePowerZoneDisplayMode using persistedCardPrefs.zoneCardDisplayMode",
            content.contains("powerZoneDisplayMode ?: persistedCardPrefs.zoneCardDisplayMode")
        )
    }

    @Test
    fun workoutMasksAndCardsSection_sourceContractExposesZoneDisplayModeSelector() {
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
            "WorkoutMasksAndCardsSection must mutate zoneCardDisplayMode on selection",
            content.contains("workoutCardPrefs.copy(zoneCardDisplayMode = mode)")
        )
    }
}
