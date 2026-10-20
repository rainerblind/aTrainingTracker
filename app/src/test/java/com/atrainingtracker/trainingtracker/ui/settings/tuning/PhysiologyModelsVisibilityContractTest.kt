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
 * sensor filtering, and 9-locale localization parity (`REQ-UI-403`, `TST-UI-366`).
 */
class PhysiologyModelsVisibilityContractTest {

    @Test
    fun `TST-UI-366_1 default TuningConfig enables only Coggan power metrics`() {
        val defaultConfig = TuningConfig()
        assertTrue(TuningPreferencesDefaults.DEFAULT_ENABLE_BASIC_POWER_METRICS)
        assertTrue(TuningPreferencesDefaults.DEFAULT_SHOW_BASIC_POWER_IN_WORKOUT_SUMMARY)
        assertTrue(TuningPreferencesDefaults.DEFAULT_ENABLE_COGGAN_POWER_METRICS)
        assertTrue(TuningPreferencesDefaults.DEFAULT_SHOW_COGGAN_POWER_IN_WORKOUT_SUMMARY)
        assertFalse(TuningPreferencesDefaults.DEFAULT_ENABLE_PMC_MODEL)
        assertFalse(TuningPreferencesDefaults.DEFAULT_ENABLE_AEROBIC_EFFICIENCY_MODEL)
        assertTrue(TuningPreferencesDefaults.DEFAULT_SHOW_AEROBIC_EFFICIENCY_IN_WORKOUT_SUMMARY)
        assertFalse(TuningPreferencesDefaults.DEFAULT_ENABLE_CRITICAL_POWER_MODEL)
        assertTrue(TuningPreferencesDefaults.DEFAULT_SHOW_CRITICAL_POWER_IN_WORKOUT_SUMMARY)

        assertTrue(defaultConfig.enableBasicPowerMetrics)
        assertTrue(defaultConfig.showBasicPowerInWorkoutSummary)
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
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetails.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetails.kt")
        )
        val workoutDetailsFile = candidates.first { it.exists() }
        val detailsContent = workoutDetailsFile.readText()

        assertTrue(detailsContent.contains("enableCogganPowerMetrics: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_COGGAN_POWER_METRICS"))
        assertTrue(detailsContent.contains("enableAerobicEfficiencyModel: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_AEROBIC_EFFICIENCY_MODEL"))
        assertTrue(detailsContent.contains("enableCriticalPowerModel: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_CRITICAL_POWER_MODEL"))
        assertFalse("WorkoutDetails must not use FlowRow pill badges (Section 5.10)", detailsContent.contains("FlowRow("))
        assertFalse("WorkoutDetails must not use surfaceContainerHighest pills (Section 5.10)", detailsContent.contains("surfaceContainerHighest"))
        assertTrue(detailsContent.contains("fun WorkoutDomainRow("))
        assertTrue(detailsContent.contains("R.drawable.ic_power"))
        assertTrue(detailsContent.contains("R.drawable.ic_cardio_load"))
        assertTrue(detailsContent.contains("R.drawable.ic_battery_full"))
        assertTrue(detailsContent.contains("Spacer(modifier = Modifier.width(20.dp))"))

        val mapLayoutCandidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
        )
        val mapLayoutFile = mapLayoutCandidates.first { it.exists() }
        val mapContent = mapLayoutFile.readText()
        assertTrue(mapContent.contains("tuningConfig.enableCriticalPowerModel && TelemetryMetricUtils.hasWPrimeBalData"))
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
            "tuning_physiology_basic_power_title",
            "tuning_physiology_basic_power_desc",
            "tuning_physiology_show_in_workout_summary",
            "tuning_physiology_show_in_workout_summary_desc",
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

        val resBase = listOf(
            File("app/src/main/res"),
            File("src/main/res")
        ).first { it.exists() && it.isDirectory }

        for (locale in locales) {
            val stringsXml = File(resBase, "$locale/strings.xml")
            assertTrue("Missing $locale/strings.xml", stringsXml.exists())
            val xml = stringsXml.readText()
            for (key in requiredKeys) {
                assertTrue(
                    "Locale $locale missing string key '$key'",
                    xml.contains("name=\"$key\"")
                )
            }
        }
    }

    @Test
    fun `TST-UI-388_3 testTuningConfig_persistenceAndModularity`() {
        val allKeys = TuningPreferencesDataStore.ALL_KEYS
        assertTrue(allKeys.contains(TuningPreferencesDataStore.KEY_ENABLE_BASIC_POWER_METRICS))
        assertTrue(allKeys.contains(TuningPreferencesDataStore.KEY_SHOW_BASIC_POWER_IN_WORKOUT_SUMMARY))
        assertTrue(allKeys.contains(TuningPreferencesDataStore.KEY_SHOW_COGGAN_POWER_IN_WORKOUT_SUMMARY))
        assertTrue(allKeys.contains(TuningPreferencesDataStore.KEY_SHOW_AEROBIC_EFFICIENCY_IN_WORKOUT_SUMMARY))
        assertTrue(allKeys.contains(TuningPreferencesDataStore.KEY_SHOW_CRITICAL_POWER_IN_WORKOUT_SUMMARY))

        fun resolveFile(relPath: String): File {
            val candidates = listOf(
                File(relPath),
                File(relPath.removePrefix("app/")),
                File("app/$relPath")
            )
            return candidates.firstOrNull { it.exists() }
                ?: error("File not found: $relPath in candidates: $candidates")
        }

        val sectionFile = resolveFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/PhysiologyModelsSection.kt")
        assertTrue(sectionFile.exists())
        val sectionContent = sectionFile.readText()
        assertTrue(sectionContent.contains("enableBasicPowerMetrics: Boolean"))
        assertTrue(sectionContent.contains("showBasicPowerInWorkoutSummary: Boolean"))
        assertTrue(sectionContent.contains("showCogganPowerInWorkoutSummary: Boolean"))
        assertTrue(sectionContent.contains("showAerobicEfficiencyInWorkoutSummary: Boolean"))
        assertTrue(sectionContent.contains("showCriticalPowerInWorkoutSummary: Boolean"))
        assertTrue(sectionContent.contains("Modifier.padding(start = 16.dp)"))

        val filesToCheck = listOf(
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/PhysiologyModelsSection.kt",
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreen.kt",
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt"
        )
        for (filePath in filesToCheck) {
            val f = resolveFile(filePath)
            assertTrue("File must exist: $filePath", f.exists())
            val lineCount = f.readLines().size
            assertTrue("File $filePath must be < 400 lines (was: $lineCount)", lineCount < 400)
        }
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
