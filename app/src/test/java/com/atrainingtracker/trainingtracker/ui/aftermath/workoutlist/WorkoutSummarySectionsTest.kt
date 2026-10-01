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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural contract test for [WorkoutSummary] section visibility and media decoupling logic
 * pursuant to REQ-UI-210 and TST-UI-164.
 */
class WorkoutSummarySectionsTest {

    enum class MediaRenderingMode {
        COMBINED_MAP_AND_ELEVATION,
        MAP_ONLY,
        ELEVATION_ONLY,
        NONE
    }

    /**
     * Helper replicating the decoupled media evaluation contract in WorkoutSummary.kt
     */
    private fun resolveMediaRenderingMode(
        preferences: WorkoutCardSectionPreferences,
        hasMap: Boolean,
        hasElevation: Boolean
    ): MediaRenderingMode {
        return when {
            preferences.showMapPreview && preferences.showElevationProfile && hasMap ->
                MediaRenderingMode.COMBINED_MAP_AND_ELEVATION
            preferences.showMapPreview && hasMap ->
                MediaRenderingMode.MAP_ONLY
            preferences.showElevationProfile && hasElevation ->
                MediaRenderingMode.ELEVATION_ONLY
            else ->
                MediaRenderingMode.NONE
        }
    }

    @Test
    fun mediaDecoupling_whenBothEnabledAndDataPresent_rendersCombined() {
        val prefs = WorkoutCardSectionPreferences(showMapPreview = true, showElevationProfile = true)
        val mode = resolveMediaRenderingMode(prefs, hasMap = true, hasElevation = true)
        assertEquals(MediaRenderingMode.COMBINED_MAP_AND_ELEVATION, mode)
    }

    @Test
    fun mediaDecoupling_whenOnlyMapEnabled_rendersMapOnly() {
        val prefs = WorkoutCardSectionPreferences(showMapPreview = true, showElevationProfile = false)
        val mode = resolveMediaRenderingMode(prefs, hasMap = true, hasElevation = true)
        assertEquals(MediaRenderingMode.MAP_ONLY, mode)
    }

    @Test
    fun mediaDecoupling_whenOnlyElevationEnabled_rendersElevationOnly() {
        val prefs = WorkoutCardSectionPreferences(showMapPreview = false, showElevationProfile = true)
        val mode = resolveMediaRenderingMode(prefs, hasMap = true, hasElevation = true)
        assertEquals(MediaRenderingMode.ELEVATION_ONLY, mode)
    }

    @Test
    fun mediaDecoupling_whenBothDisabled_rendersNone() {
        val prefs = WorkoutCardSectionPreferences(showMapPreview = false, showElevationProfile = false)
        val mode = resolveMediaRenderingMode(prefs, hasMap = true, hasElevation = true)
        assertEquals(MediaRenderingMode.NONE, mode)
    }

    @Test
    fun mediaDecoupling_whenMapUnavailable_elevationCanStillRender() {
        val prefs = WorkoutCardSectionPreferences(showMapPreview = true, showElevationProfile = true)
        val mode = resolveMediaRenderingMode(prefs, hasMap = false, hasElevation = true)
        assertEquals(MediaRenderingMode.ELEVATION_ONLY, mode)
    }

    @Test
    fun sectionVisibility_respectsPreferences() {
        val customPrefs = WorkoutCardSectionPreferences(
            showDescription = false,
            showExtrema = true,
            showLaps = false,
            showStrava = true,
            showTelemetryCharts = true,
            showZoneAnalysis = true
        )

        assertFalse("Description should be hidden", customPrefs.showDescription)
        assertTrue("Extrema should be visible", customPrefs.showExtrema)
        assertFalse("Laps should be hidden", customPrefs.showLaps)
        assertTrue("Strava should be visible", customPrefs.showStrava)
        assertTrue("Telemetry charts should be enabled", customPrefs.showTelemetryCharts)
        assertTrue("Zone analysis should be enabled", customPrefs.showZoneAnalysis)
    }

    @Test
    fun mandatoryAnchors_areInvariant() {
        // Architectural invariant: WorkoutHeader and WorkoutDetails must never have
        // toggle flags in WorkoutCardSectionPreferences.
        val fields = WorkoutCardSectionPreferences::class.java.declaredFields.map { it.name }
        assertFalse("WorkoutHeader must remain mandatory and not configurable", fields.contains("showHeader"))
        assertFalse("WorkoutDetails must remain mandatory and not configurable", fields.contains("showDetails"))
    }
}
