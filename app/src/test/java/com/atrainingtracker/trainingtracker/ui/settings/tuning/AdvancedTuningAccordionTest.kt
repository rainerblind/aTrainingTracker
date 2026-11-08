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
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontFamily
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontWeight
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Unit tests verifying TuningSubtitleFormatter logic across all 5 semantic accordion subsections
 * in accordance with REQ-UI-222 and TST-UI-176.1.
 */
class AdvancedTuningAccordionTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

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
    fun testCockpitTypographySubtitle_reflectsFontWeightAndVariant() {
        val mockContext = mockk<Context>()

        every { mockContext.getString(R.string.tuning_font_system_default) } returns "System Default"
        every { mockContext.getString(R.string.tuning_weight_semi_bold) } returns "Semi-Bold"
        every { mockContext.getString(R.string.sensor_field_variant_v0) } returns "Classic Seamless Grid"
        every { mockContext.getString(R.string.sensor_field_variant_v2) } returns "Elevated Sports Cards"

        val subtitleWithVariant0 = TuningSubtitleFormatter.formatCockpitSubtitle(
            CockpitFontFamily.SYSTEM_DEFAULT,
            CockpitFontWeight.SEMI_BOLD,
            com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldVariant.CLASSIC_SEAMLESS,
            mockContext
        )
        assertEquals("System Default, Semi-Bold · Classic Seamless Grid", subtitleWithVariant0)

        val subtitleWithVariant2 = TuningSubtitleFormatter.formatCockpitSubtitle(
            CockpitFontFamily.SYSTEM_DEFAULT,
            CockpitFontWeight.SEMI_BOLD,
            com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldVariant.ELEVATED_CARDS,
            mockContext
        )
        assertEquals("System Default, Semi-Bold · Elevated Sports Cards", subtitleWithVariant2)
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

            // Localized context test (German labels)
            val mockContextDe = mockk<Context>()
            every { mockContextDe.getString(R.string.tuning_battery_saver_flat_short) } returns "Flach"
            every { mockContextDe.getString(R.string.tuning_battery_saver_steep_short) } returns "Steil"
            val subtitleLocalized = TuningSubtitleFormatter.formatBatterySaverSubtitle(
                fullDim = 0.25f,
                mediumDim = 0.50f,
                flatSlope = 2.0f,
                steepSlope = 5.0f,
                context = mockContextDe
            )
            assertEquals("25% / 50%, Flach: 2.0%, Steil: 5.0%", subtitleLocalized)
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

            // Localized context test (German labels)
            val mockContextDe = mockk<Context>()
            every { mockContextDe.getString(R.string.tuning_sensors_alt_short) } returns "Höhe"
            every { mockContextDe.getString(R.string.tuning_sensors_speed_short) } returns "Geschw."
            val subtitleLocalized = TuningSubtitleFormatter.formatSensorsGpsSubtitle(
                gpsAccuracy = 200f,
                altitudeWindowSec = 21,
                slopeMinSpeed = 0.5f,
                context = mockContextDe
            )
            assertEquals("GPS: 200m, Höhe: 21s, Geschw.: 0.5 m/s", subtitleLocalized)
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
    fun testAftermathAnalysisSubtitle_reflectsIndependentDomains() {
        val mockContext = mockk<Context>()
        every { mockContext.getString(R.string.tuning_profile_x_axis_title) } returns "Elevation"
        every { mockContext.getString(R.string.tuning_telemetry_x_axis_title) } returns "Telemetry"
        every { mockContext.getString(R.string.tuning_profile_x_axis_distance) } returns "Distance"
        every { mockContext.getString(R.string.tuning_profile_x_axis_time) } returns "Time"

        val subtitle = TuningSubtitleFormatter.formatAftermathSubtitle(
            ProfileXAxisDomain.DISTANCE,
            ProfileXAxisDomain.TIME,
            mockContext
        )
        assertEquals("Elevation: Distance | Telemetry: Time | \u03c3: 21m", subtitle)

        val customSubtitle = TuningSubtitleFormatter.formatAftermathSubtitle(
            ProfileXAxisDomain.DISTANCE,
            ProfileXAxisDomain.TIME,
            mockContext,
            smoothingSigma = 35.0f
        )
        assertEquals("Elevation: Distance | Telemetry: Time | \u03c3: 35m", customSubtitle)
    }

    @Test
    fun testWorkoutMasksAndCardsSubtitle_reflectsActiveCounts() {
        val mockContext = mockk<Context>()
        every {
            mockContext.getString(
                R.string.tuning_summary_masks_cards_format,
                any()
            )
        } answers {
            val formatArgs = args[1] as Array<*>
            "${formatArgs[0]}/8 Cards"
        }

        // All 8 cards true
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

        val fullSubtitle = TuningSubtitleFormatter.formatWorkoutMasksSubtitle(
            fullCardPrefs,
            mockContext
        )
        assertEquals("8/8 Cards", fullSubtitle)

        // Custom subset: 3 cards
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

        val subsetSubtitle = TuningSubtitleFormatter.formatWorkoutMasksSubtitle(
            subsetCardPrefs,
            mockContext
        )
        assertEquals("3/8 Cards", subsetSubtitle)
    }

    @Test
    fun testWorkoutMatrixSubtitle_reflectsListAndDetailCounts() {
        val mockContext = mockk<Context>()
        every {
            mockContext.getString(
                R.string.tuning_summary_matrix_format,
                any(),
                any()
            )
        } answers {
            val formatArgs = args[1] as Array<*>
            "${formatArgs[0]}/8 List, ${formatArgs[1]}/8 Details"
        }

        val cardPrefs = WorkoutCardSectionPreferences(
            showDescription = true,
            showExtrema = false,
            showLaps = true,
            showStrava = false,
            showMapPreview = true,
            showElevationProfile = false,
            showTelemetryCharts = false,
            showZoneAnalysis = false
        ) // 3 active

        val detailPrefs = WorkoutDetailPreferences(
            showDescription = true,
            showExtrema = true,
            showLaps = true,
            showStrava = true,
            showMap = true,
            showElevationProfile = true,
            showTelemetryCharts = true,
            showZoneAnalysis = true
        ) // 8 active

        val subtitle = TuningSubtitleFormatter.formatWorkoutMatrixSubtitle(
            cardPrefs,
            detailPrefs,
            mockContext
        )
        assertEquals("3/8 List, 8/8 Details", subtitle)
    }

    @Test
    fun testNavigationSubtitle_reflectsTransparencyDismissDurationAndRadius() {
        val defaultLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val mockContext = mockk<Context>()

            val subtitle1 = TuningSubtitleFormatter.formatNavigationSubtitle(
                radiusKm = 1.0f,
                dismissSec = 4,
                context = mockContext
            )
            assertEquals("4s, 1.00 km", subtitle1)

            val subtitle2 = TuningSubtitleFormatter.formatNavigationSubtitle(
                radiusKm = 2.5f,
                dismissSec = 10,
                context = mockContext
            )
            assertEquals("10s, 2.50 km", subtitle2)

            val subtitle3 = TuningSubtitleFormatter.formatNavigationSubtitle(
                radiusKm = 2.5f,
                dismissSec = 0,
                context = mockContext
            )
            assertEquals("4s, 2.50 km", subtitle3)
        } finally {
            Locale.setDefault(defaultLocale)
        }
    }

    @Test
    fun testSensorSearchSubtitle_reflectsRoundsAndActiveStatus() {
        val mockContext = mockk<Context>()
        every { mockContext.getString(R.string.tuning_sensor_search_active) } returns "Active"
        every { mockContext.getString(R.string.tuning_sensor_search_disabled) } returns "Disabled"
        every {
            mockContext.getString(R.string.tuning_sensor_search_subtitle, 3, "Active")
        } returns "3 search rounds, Active auto-search"
        every {
            mockContext.getString(R.string.tuning_sensor_search_subtitle, 1, "Disabled")
        } returns "1 search rounds, Disabled auto-search"
        every {
            mockContext.getString(R.string.tuning_sensor_search_subtitle, 2, "Active")
        } returns "2 search rounds, Active auto-search"

        val subtitleActive = TuningSubtitleFormatter.formatSensorSearchSubtitle(
            numberOfSearchTries = 3,
            startSearchWhenAppStarts = true,
            startSearchWhenResumeFromPaused = false,
            startSearchWhenUserChangesSport = false,
            startSearchWhenTrackingStarts = false,
            context = mockContext
        )
        assertEquals("3 search rounds, Active auto-search", subtitleActive)

        val subtitleDisabled = TuningSubtitleFormatter.formatSensorSearchSubtitle(
            numberOfSearchTries = 1,
            startSearchWhenAppStarts = false,
            startSearchWhenResumeFromPaused = false,
            startSearchWhenUserChangesSport = false,
            startSearchWhenTrackingStarts = false,
            context = mockContext
        )
        assertEquals("1 search rounds, Disabled auto-search", subtitleDisabled)

        val subtitlePartial = TuningSubtitleFormatter.formatSensorSearchSubtitle(
            numberOfSearchTries = 2,
            startSearchWhenAppStarts = false,
            startSearchWhenResumeFromPaused = true,
            startSearchWhenUserChangesSport = false,
            startSearchWhenTrackingStarts = false,
            context = mockContext
        )
        assertEquals("2 search rounds, Active auto-search", subtitlePartial)
    }

    @Test
    fun testClusterTuningSubtitle_formatting() {
        val defaultLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val mockContext = mockk<Context>()
            every { mockContext.getString(R.string.units_distance_metric) } returns "km"
            every { mockContext.getString(R.string.units_distance_imperial) } returns "mi"
            every { mockContext.getString(R.string.units_distance_basic) } returns "m"
            every { mockContext.getString(R.string.tuning_cluster_end_short) } returns "End"
            every { mockContext.getString(R.string.tuning_cluster_apex_short) } returns "Apex"
            every { mockContext.getString(R.string.tuning_cluster_dist_short) } returns "Dist"
            every { mockContext.getString(R.string.tuning_cluster_alt_short) } returns "Alt"

            val metricSubtitle = TuningSubtitleFormatter.formatClusterTuningSubtitle(
                endpointTolerance = 200f,
                apexTolerance = 400f,
                distanceTolerance = 0.20f,
                altitudePositionTolerance = 400f,
                useSportTypeForClustering = true,
                useAltitudePosForClustering = true,
                isImperial = false,
                context = mockContext
            )
            assertEquals("End: 200 m · Apex: 400 m · Dist: 20% · Alt: 400 m", metricSubtitle)

            val metricSubtitleNoAlt = TuningSubtitleFormatter.formatClusterTuningSubtitle(
                endpointTolerance = 200f,
                apexTolerance = 400f,
                distanceTolerance = 0.20f,
                altitudePositionTolerance = 400f,
                useSportTypeForClustering = true,
                useAltitudePosForClustering = false,
                isImperial = false,
                context = mockContext
            )
            assertEquals("End: 200 m · Apex: 400 m · Dist: 20%", metricSubtitleNoAlt)

            // Localized German context test
            val mockContextDe = mockk<Context>()
            every { mockContextDe.getString(R.string.units_distance_basic) } returns "m"
            every { mockContextDe.getString(R.string.tuning_cluster_end_short) } returns "Ende"
            every { mockContextDe.getString(R.string.tuning_cluster_apex_short) } returns "Apex"
            every { mockContextDe.getString(R.string.tuning_cluster_dist_short) } returns "Dist."
            every { mockContextDe.getString(R.string.tuning_cluster_alt_short) } returns "Höhe"

            val deSubtitle = TuningSubtitleFormatter.formatClusterTuningSubtitle(
                endpointTolerance = 200f,
                apexTolerance = 400f,
                distanceTolerance = 0.20f,
                altitudePositionTolerance = 400f,
                useSportTypeForClustering = true,
                useAltitudePosForClustering = true,
                isImperial = false,
                context = mockContextDe
            )
            assertEquals("Ende: 200 m · Apex: 400 m · Dist.: 20% · Höhe: 400 m", deSubtitle)

            // Large tolerance (> 1000m) uses kilometer formatting
            val largeMetricSubtitle = TuningSubtitleFormatter.formatClusterTuningSubtitle(
                endpointTolerance = 1500f,
                apexTolerance = 2000f,
                distanceTolerance = 0.20f,
                altitudePositionTolerance = 1500f,
                useSportTypeForClustering = true,
                useAltitudePosForClustering = true,
                isImperial = false,
                context = mockContext
            )
            assertEquals("End: 1.500km · Apex: 2.000km · Dist: 20% · Alt: 1.500km", largeMetricSubtitle)

            // Imperial formatting
            val imperialSubtitle = TuningSubtitleFormatter.formatClusterTuningSubtitle(
                endpointTolerance = 1609.344f,
                apexTolerance = 1609.344f,
                distanceTolerance = 0.20f,
                altitudePositionTolerance = 1609.344f,
                useSportTypeForClustering = true,
                useAltitudePosForClustering = true,
                isImperial = true,
                context = mockContext
            )
            assertEquals("End: 1.000mi · Apex: 1.000mi · Dist: 20% · Alt: 1.000mi", imperialSubtitle)
        } finally {
            Locale.setDefault(defaultLocale)
        }
    }

    @Test
    fun testPhysiologyModelsSubtitle_reflectsEnabledSchools() {
        val mockContext = mockk<Context>()
        every { mockContext.getString(R.string.tuning_physiology_coggan_short) } returns "Coggan"
        every { mockContext.getString(R.string.tuning_physiology_pmc_short) } returns "Load / Form"
        every { mockContext.getString(R.string.tuning_physiology_ef_short) } returns "EF / Decoupling"
        every { mockContext.getString(R.string.tuning_physiology_cp_short) } returns "CP / W'"
        every { mockContext.getString(R.string.tuning_physiology_none_enabled) } returns "None enabled"

        val defaultSubtitle = TuningSubtitleFormatter.formatPhysiologyModelsSubtitle(
            enableCogganPowerMetrics = true,
            enablePmcModel = false,
            enableAerobicEfficiencyModel = false,
            enableCriticalPowerModel = false,
            context = mockContext
        )
        assertEquals("Coggan", defaultSubtitle)

        val allSubtitle = TuningSubtitleFormatter.formatPhysiologyModelsSubtitle(
            enableCogganPowerMetrics = true,
            enablePmcModel = true,
            enableAerobicEfficiencyModel = true,
            enableCriticalPowerModel = true,
            context = mockContext
        )
        assertEquals("Coggan, Load / Form, EF / Decoupling, CP / W'", allSubtitle)

        val noneSubtitle = TuningSubtitleFormatter.formatPhysiologyModelsSubtitle(
            enableCogganPowerMetrics = false,
            enablePmcModel = false,
            enableAerobicEfficiencyModel = false,
            enableCriticalPowerModel = false,
            context = mockContext
        )
        assertEquals("None enabled", noneSubtitle)
    }

    @Test
    fun testAutoPauseSubtitle_formatsSpeedAndDebounceDelays() {
        val mockContext = mockk<Context>()

        every {
            mockContext.getString(R.string.tuning_speed_value_format, 3, "km/h")
        } returns "3 km/h"
        every {
            mockContext.getString(R.string.tuning_seconds_value_format, 5)
        } returns "5 s"
        every {
            mockContext.getString(R.string.tuning_seconds_value_format, 2)
        } returns "2 s"
        every {
            mockContext.getString(
                R.string.tuning_auto_pause_subtitle_format,
                "3 km/h",
                "5 s",
                "2 s"
            )
        } returns "3 km/h · Pause: 5 s · Resume: 2 s"

        val subtitle = TuningSubtitleFormatter.formatAutoPauseSubtitle(
            speedThreshold = 3,
            speedUnit = "km/h",
            debounceSec = 5,
            resumeDebounceSec = 2,
            context = mockContext
        )
        assertEquals("3 km/h · Pause: 5 s · Resume: 2 s", subtitle)
    }

    @Test
    fun testTuningSectionEnum_canonicalLifecycleSequence() {
        val expected = listOf(
            TuningSection.NAVIGATION,
            TuningSection.COCKPIT_TYPOGRAPHY,
            TuningSection.BATTERY_SAVER,
            TuningSection.SENSORS_GPS,
            TuningSection.SENSOR_SEARCH,
            TuningSection.WORKOUT_MASKS_CARDS,
            TuningSection.PHYSIOLOGY_MODELS,
            TuningSection.ROUTE_CLUSTERING,
            TuningSection.AFTERMATH_ANALYSIS
        )
        assertEquals(expected, TuningSection.values().toList())
    }
}
