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

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import android.content.Context
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.EditWorkoutFieldPreferences
import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontFamily
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontWeight
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Unit tests verifying TuningSubtitleFormatter logic across all 5 semantic accordion subsections
 * in accordance with REQ-UI-222 and TST-UI-176.1.
 */
class AdvancedTuningAccordionTest {

    @Test
    fun testCockpitTypographySubtitle_reflectsFontAndWeight() {
        val mockContext = mockk<Context>()

        // Mock getDisplayNameRes() string resolution for fonts
        every { mockContext.getString(R.string.tuning_font_system_default) } returns "System Default"
        every { mockContext.getString(R.string.tuning_font_seven_segment) } returns "7-Segment LCD"
        every { mockContext.getString(R.string.tuning_font_modern_athletic) } returns "Modern Athletic"
        every { mockContext.getString(R.string.tuning_font_monospace) } returns "Monospace Precision"
        every { mockContext.getString(R.string.tuning_font_playful) } returns "Playful Flow"

        // Mock getDisplayNameRes() string resolution for weights
        every { mockContext.getString(R.string.tuning_weight_normal) } returns "Normal"
        every { mockContext.getString(R.string.tuning_weight_semi_bold) } returns "Semi-Bold"
        every { mockContext.getString(R.string.tuning_weight_bold) } returns "Bold"

        val subtitle1 = TuningSubtitleFormatter.formatCockpitSubtitle(
            CockpitFontFamily.SYSTEM_DEFAULT,
            CockpitFontWeight.NORMAL,
            mockContext
        )
        assertEquals("System Default, Normal", subtitle1)

        val subtitle2 = TuningSubtitleFormatter.formatCockpitSubtitle(
            CockpitFontFamily.SEVEN_SEGMENT,
            CockpitFontWeight.BOLD,
            mockContext
        )
        assertEquals("7-Segment LCD, Bold", subtitle2)

        val subtitle3 = TuningSubtitleFormatter.formatCockpitSubtitle(
            CockpitFontFamily.MODERN_ATHLETIC,
            CockpitFontWeight.SEMI_BOLD,
            mockContext
        )
        assertEquals("Modern Athletic, Semi-Bold", subtitle3)
    }

    @Test
    fun testAmoledBatterySaverSubtitle_reflectsDimFactorsAndSlopes() {
        val defaultLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val subtitle = TuningSubtitleFormatter.formatBatterySaverSubtitle(
                fullDim = 0.25f,
                mediumDim = 0.50f,
                flatSlope = 2.0f,
                steepSlope = 5.0f
            )
            assertEquals("25% / 50%, Flat: 2.0%, Steep: 5.0%", subtitle)

            val subtitleEdge = TuningSubtitleFormatter.formatBatterySaverSubtitle(
                fullDim = 0.10f,
                mediumDim = 0.80f,
                flatSlope = 1.5f,
                steepSlope = 6.2f
            )
            assertEquals("10% / 80%, Flat: 1.5%, Steep: 6.2%", subtitleEdge)
        } finally {
            Locale.setDefault(defaultLocale)
        }
    }

    @Test
    fun testSensorsGpsFilterSubtitle_reflectsGpsAltSpeed() {
        val defaultLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val subtitle = TuningSubtitleFormatter.formatSensorsGpsSubtitle(
                gpsAccuracy = 200f,
                altitudeWindowSec = 21,
                slopeMinSpeed = 0.5f
            )
            assertEquals("GPS: 200m, Alt: 21s, Speed: 0.5 m/s", subtitle)

            val subtitleCustom = TuningSubtitleFormatter.formatSensorsGpsSubtitle(
                gpsAccuracy = 50f,
                altitudeWindowSec = 10,
                slopeMinSpeed = 1.2f
            )
            assertEquals("GPS: 50m, Alt: 10s, Speed: 1.2 m/s", subtitleCustom)
        } finally {
            Locale.setDefault(defaultLocale)
        }
    }

    @Test
    fun testAftermathAnalysisSubtitle_reflectsDomain() {
        val mockContext = mockk<Context>()
        every { mockContext.getString(R.string.tuning_profile_x_axis_title) } returns "X-Axis"
        every { mockContext.getString(R.string.tuning_profile_x_axis_distance) } returns "Distance"
        every { mockContext.getString(R.string.tuning_profile_x_axis_time) } returns "Time"

        val subtitleDistance = TuningSubtitleFormatter.formatAftermathSubtitle(
            ProfileXAxisDomain.DISTANCE,
            mockContext
        )
        assertEquals("X-Axis: Distance", subtitleDistance)

        val subtitleTime = TuningSubtitleFormatter.formatAftermathSubtitle(
            ProfileXAxisDomain.TIME,
            mockContext
        )
        assertEquals("X-Axis: Time", subtitleTime)
    }

    @Test
    fun testWorkoutMasksAndCardsSubtitle_reflectsActiveCounts() {
        val mockContext = mockk<Context>()
        every {
            mockContext.getString(
                R.string.tuning_summary_masks_cards_format,
                any(),
                any()
            )
        } answers {
            val formatArgs = args[1] as Array<*>
            "${formatArgs[0]}/8 Cards, ${formatArgs[1]}/6 Fields"
        }

        // All 8 cards true, all 6 fields true
        val fullCardPrefs = WorkoutCardSectionPreferences(
            showDescription = true,
            showExtrema = true,
            showLaps = true,
            showStrava = true,
            showMapPreview = true,
            showElevationProfile = true,
            showTelemetryCharts = true,
            showZoneAnalysis = true
        )
        val fullEditPrefs = EditWorkoutFieldPreferences(
            showDescription = true,
            showCluster = true,
            showCommuteTrainer = true,
            showStravaUpload = true,
            showGoal = true,
            showMethod = true
        )

        val fullSubtitle = TuningSubtitleFormatter.formatWorkoutMasksSubtitle(
            fullCardPrefs,
            fullEditPrefs,
            mockContext
        )
        assertEquals("8/8 Cards, 6/6 Fields", fullSubtitle)

        // Custom subset: 3 cards, 2 fields
        val subsetCardPrefs = WorkoutCardSectionPreferences(
            showDescription = true,
            showExtrema = false,
            showLaps = true,
            showStrava = false,
            showMapPreview = true,
            showElevationProfile = false,
            showTelemetryCharts = false,
            showZoneAnalysis = false
        )
        val subsetEditPrefs = EditWorkoutFieldPreferences(
            showDescription = true,
            showCluster = false,
            showCommuteTrainer = true,
            showStravaUpload = false,
            showGoal = false,
            showMethod = false
        )

        val subsetSubtitle = TuningSubtitleFormatter.formatWorkoutMasksSubtitle(
            subsetCardPrefs,
            subsetEditPrefs,
            mockContext
        )
        assertEquals("3/8 Cards, 2/6 Fields", subsetSubtitle)
    }
}
