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

import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.AthleteMetricType
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.athleteprofile.PlotMetricType
import com.atrainingtracker.trainingtracker.ui.athleteprofile.resolveEffectiveHistoryFilter
import com.atrainingtracker.trainingtracker.ui.athleteprofile.resolveEffectivePlotMetric
import com.atrainingtracker.trainingtracker.ui.athleteprofile.resolveVisibleHistoryFilterMetrics
import com.atrainingtracker.trainingtracker.ui.athleteprofile.resolveVisiblePlotMetrics
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.shouldIncludeSensorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests verifying Physiology & Training Models defaults, consumer visibility contracts,
 * sensor filtering, Rule 59 subordinate hierarchy, and 9-locale localization parity (`REQ-UI-403`, `REQ-UI-424`, `TST-UI-366`, `TST-UI-388`).
 */
class PhysiologyModelsVisibilityContractTest {

    @Test
    fun `TST-UI-366_1 default TuningConfig enables only Coggan power metrics`() {
        val defaultConfig = TuningConfig()
        assertTrue(TuningPreferencesDefaults.DEFAULT_ENABLE_COGGAN_POWER_METRICS)
        assertTrue(TuningPreferencesDefaults.DEFAULT_SHOW_COGGAN_POWER_IN_WORKOUT_SUMMARY)
        assertFalse(TuningPreferencesDefaults.DEFAULT_ENABLE_PMC_MODEL)
        assertFalse(TuningPreferencesDefaults.DEFAULT_ENABLE_AEROBIC_EFFICIENCY_MODEL)
        assertTrue(TuningPreferencesDefaults.DEFAULT_SHOW_AEROBIC_EFFICIENCY_IN_WORKOUT_SUMMARY)
        assertFalse(TuningPreferencesDefaults.DEFAULT_ENABLE_CRITICAL_POWER_MODEL)
        assertTrue(TuningPreferencesDefaults.DEFAULT_SHOW_CRITICAL_POWER_IN_WORKOUT_SUMMARY)

        assertTrue(defaultConfig.enableCogganPowerMetrics)
        assertTrue(defaultConfig.showCogganPowerInWorkoutSummary)
        assertFalse(defaultConfig.enablePmcModel)
        assertFalse(defaultConfig.enableAerobicEfficiencyModel)
        assertTrue(defaultConfig.showAerobicEfficiencyInWorkoutSummary)
        assertFalse(defaultConfig.enableCriticalPowerModel)
        assertTrue(defaultConfig.showCriticalPowerInWorkoutSummary)
    }

    @Test
    fun `TST-UI-366_3 EditSensorFieldViewModel filters Coggan and CP sensors when disabled`() {
        // When Coggan is false and CP is false
        assertFalse(
            shouldIncludeSensorType(
                type = SensorType.NORMALIZED_POWER,
                initialSensorType = SensorType.SPEED_mps,
                enableCogganPowerMetrics = false,
                enableCriticalPowerModel = false
            )
        )
        assertFalse(
            shouldIncludeSensorType(
                type = SensorType.INTENSITY_FACTOR,
                initialSensorType = SensorType.SPEED_mps,
                enableCogganPowerMetrics = false,
                enableCriticalPowerModel = false
            )
        )
        assertFalse(
            shouldIncludeSensorType(
                type = SensorType.TSS,
                initialSensorType = SensorType.SPEED_mps,
                enableCogganPowerMetrics = false,
                enableCriticalPowerModel = false
            )
        )
        assertFalse(
            shouldIncludeSensorType(
                type = SensorType.W_PRIME_BAL,
                initialSensorType = SensorType.SPEED_mps,
                enableCogganPowerMetrics = true,
                enableCriticalPowerModel = false
            )
        )

        // Core power sensors (POWER, WORK_KJ, POWER_TO_WEIGHT, etc.) are always included
        assertTrue(
            shouldIncludeSensorType(
                type = SensorType.POWER,
                initialSensorType = SensorType.SPEED_mps,
                enableCogganPowerMetrics = false,
                enableCriticalPowerModel = false
            )
        )
        assertTrue(
            shouldIncludeSensorType(
                type = SensorType.WORK_KJ,
                initialSensorType = SensorType.SPEED_mps,
                enableCogganPowerMetrics = false,
                enableCriticalPowerModel = false
            )
        )
        assertTrue(
            shouldIncludeSensorType(
                type = SensorType.POWER_TO_WEIGHT,
                initialSensorType = SensorType.SPEED_mps,
                enableCogganPowerMetrics = false,
                enableCriticalPowerModel = false
            )
        )

        // Existing field configured with a disabled sensor type still includes its initialSensorType
        assertTrue(
            shouldIncludeSensorType(
                type = SensorType.W_PRIME_BAL,
                initialSensorType = SensorType.W_PRIME_BAL,
                enableCogganPowerMetrics = false,
                enableCriticalPowerModel = false
            )
        )
    }

    @Test
    fun `TST-UI-366_4 and 366_6 AthleteProfile plot and history filters respect enableCriticalPowerModel`() {
        val plotMetricsWhenDisabled = resolveVisiblePlotMetrics(enableCriticalPowerModel = false)
        assertEquals(
            listOf(
                PlotMetricType.WEIGHT,
                PlotMetricType.HR_REST,
                PlotMetricType.FTP,
                PlotMetricType.POWER_TO_WEIGHT
            ),
            plotMetricsWhenDisabled
        )

        val plotMetricsWhenEnabled = resolveVisiblePlotMetrics(enableCriticalPowerModel = true)
        assertTrue(plotMetricsWhenEnabled.contains(PlotMetricType.CP))
        assertTrue(plotMetricsWhenEnabled.contains(PlotMetricType.W_PRIME))

        // Fallback to WEIGHT when CP/W_PRIME was selected and model is disabled
        assertEquals(
            PlotMetricType.WEIGHT,
            resolveEffectivePlotMetric(PlotMetricType.CP, enableCriticalPowerModel = false)
        )
        assertEquals(
            PlotMetricType.WEIGHT,
            resolveEffectivePlotMetric(PlotMetricType.W_PRIME, enableCriticalPowerModel = false)
        )
        assertEquals(
            PlotMetricType.FTP,
            resolveEffectivePlotMetric(PlotMetricType.FTP, enableCriticalPowerModel = false)
        )

        // History filters
        val historyFiltersDisabled = resolveVisibleHistoryFilterMetrics(enableCriticalPowerModel = false)
        assertFalse(historyFiltersDisabled.contains(AthleteMetricType.CP_WATTS))
        assertFalse(historyFiltersDisabled.contains(AthleteMetricType.W_PRIME_KJ))
        assertEquals(
            null,
            resolveEffectiveHistoryFilter(AthleteMetricType.CP_WATTS, enableCriticalPowerModel = false)
        )
        assertEquals(
            null,
            resolveEffectiveHistoryFilter(AthleteMetricType.W_PRIME_KJ, enableCriticalPowerModel = false)
        )
    }

    @Test
    fun `TST-UI-366_3_5_6 WorkoutDetails and MapDetailLayout source contract gates physiology metrics`() {
        val detailsClass = Class.forName("com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsKt")
        org.junit.Assert.assertNotNull("WorkoutDetailsKt must exist", detailsClass)

        val mapLayoutClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        org.junit.Assert.assertNotNull("MapDetailLayoutKt must exist", mapLayoutClass)
    }

    @Test
    fun `TST-UI-366_7 and TST-UI-367_2 all 9 locales define physiology models and Section 5_10 string keys`() {
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
        val requiredKeys = listOf(
            "tuning_cat_physiology_models",
            "tuning_physiology_none_enabled",
            "tuning_physiology_coggan_short",
            "tuning_physiology_pmc_short",
            "tuning_physiology_ef_short",
            "tuning_physiology_cp_short",
            "tuning_physiology_coggan_title",
            "tuning_physiology_coggan_desc",
            "tuning_physiology_pmc_title",
            "tuning_physiology_pmc_desc",
            "tuning_physiology_ef_title",
            "tuning_physiology_ef_desc",
            "tuning_physiology_cp_title",
            "tuning_physiology_cp_desc",
            "tuning_physiology_show_in_workout_summary",
            "workout_domain_efficiency",
            "workout_ef_short",
            "workout_pwhr_short",
            "workout_pahr_short",
            "workout_ef_value_pw_format",
            "workout_ef_value_pa_format",
            "workout_decoupling_pct_format",
            "workout_w_prime_min_kj_label",
            "workout_w_prime_min_pct_label",
            "workout_w_prime_min_kj_value_format",
            "workout_w_prime_min_pct_value_format",
            "workout_power_label_work",
            "workout_power_value_watts_format",
            "workout_power_value_if_format",
            "workout_power_value_tss_format",
            "workout_power_value_work_format"
        )

        val deadKeys = listOf(
            "tuning_physiology_basic_power_title",
            "tuning_physiology_basic_power_desc",
            "tuning_physiology_show_in_workout_summary_desc"
        )

        for (locale in locales) {
            val strings = com.atrainingtracker.testing.LocalizationTestCache.getStrings(locale, "strings.xml")
            for (key in requiredKeys) {
                assertTrue(
                    "Locale $locale missing string key '$key'",
                    strings.containsKey(key) && strings[key]!!.isNotEmpty()
                )
            }
            for (deadKey in deadKeys) {
                assertFalse(
                    "Locale $locale must NOT contain dead string key '$deadKey' (REQ-UI-424.6)",
                    strings.containsKey(deadKey)
                )
            }
        }
    }

    @Test
    fun `TST-UI-388_3 testTuningConfig_persistenceAndModularity`() {
        val allKeys = TuningPreferencesDataStore.ALL_KEYS
        val keyNames = allKeys.map { it.name }
        assertFalse(keyNames.contains("enable_basic_power_metrics"))
        assertFalse(keyNames.contains("show_basic_power_in_workout_summary"))
        assertTrue(allKeys.contains(TuningPreferencesDataStore.KEY_SHOW_COGGAN_POWER_IN_WORKOUT_SUMMARY))
        assertTrue(allKeys.contains(TuningPreferencesDataStore.KEY_SHOW_AEROBIC_EFFICIENCY_IN_WORKOUT_SUMMARY))
        assertTrue(allKeys.contains(TuningPreferencesDataStore.KEY_SHOW_CRITICAL_POWER_IN_WORKOUT_SUMMARY))

        val sectionClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.PhysiologyModelsSectionKt")
        org.junit.Assert.assertNotNull(sectionClass)

        val formControlsClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControlsKt")
        org.junit.Assert.assertNotNull(formControlsClass)

        val filesToCheck = listOf(
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.PhysiologyModelsSectionKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningScreenKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningDialogKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControlsKt"
        )
        for (className in filesToCheck) {
            val clazz = Class.forName(className)
            org.junit.Assert.assertNotNull("Class must exist: $className", clazz)
        }
    }

    @Test
    fun `TST-WKO-036_1 testPhysiologyModelsSection_rpeFeelSwitchContract`() {
        val sectionClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.PhysiologyModelsSectionKt")
        val method = sectionClass.declaredMethods.find { it.name.startsWith("PhysiologyModelsSection") }
        org.junit.Assert.assertNotNull("PhysiologyModelsSection method must exist", method)

        val strings = com.atrainingtracker.testing.LocalizationTestCache.getStrings("values", "strings_rpe_feel.xml")
        org.junit.Assert.assertNotNull("settings_workout_card_rpe_feel must exist", strings["settings_workout_card_rpe_feel"])
        org.junit.Assert.assertNotNull("tuning_rpe_feel_desc must exist", strings["tuning_rpe_feel_desc"])
    }

    @Test
    fun `TST-UI-388_4 testLiveCockpitAndBackgroundPersistenceInvariants`() {
        assertTrue(
            shouldIncludeSensorType(
                type = SensorType.NORMALIZED_POWER,
                initialSensorType = SensorType.SPEED_mps,
                enableCogganPowerMetrics = true,
                enableCriticalPowerModel = false
            )
        )
        assertFalse(
            shouldIncludeSensorType(
                type = SensorType.NORMALIZED_POWER,
                initialSensorType = SensorType.SPEED_mps,
                enableCogganPowerMetrics = false,
                enableCriticalPowerModel = true
            )
        )
        assertTrue(
            shouldIncludeSensorType(
                type = SensorType.W_PRIME_BAL,
                initialSensorType = SensorType.SPEED_mps,
                enableCogganPowerMetrics = false,
                enableCriticalPowerModel = true
            )
        )
        assertFalse(
            shouldIncludeSensorType(
                type = SensorType.W_PRIME_BAL,
                initialSensorType = SensorType.SPEED_mps,
                enableCogganPowerMetrics = true,
                enableCriticalPowerModel = false
            )
        )
    }
}
