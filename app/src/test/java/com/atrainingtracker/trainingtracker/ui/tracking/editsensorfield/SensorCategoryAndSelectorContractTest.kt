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

package com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield

import com.atrainingtracker.banalservice.sensor.SensorCategory
import com.atrainingtracker.banalservice.sensor.SensorSubCategory
import com.atrainingtracker.banalservice.sensor.SensorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit, architectural contract, and 9-language localization verification suite for ATT-3852 & ATT-6726
 * (`REQ-UI-395`, `REQ-TRK-070`, `TST-TRK-070.1`..`TST-TRK-070.4`):
 * - Exhaustive 63-metric 2-level `SensorCategory` (6 groups) and `SensorSubCategory` (9 groups) partition
 * - Direct core metrics returning null subCategory
 * - Unified 5 HRV metrics under HEART_RATE_AND_ENERGY
 * - Cadence and Strides unified under SPEED_PACE_AND_DISTANCE
 * - Elevation renamed from ELEVATION_AND_NAVIGATION
 * - `SensorTypeSearchFilter` case-insensitive query filtering across full name, short name, unit, and enum name
 * - `EditSensorFieldDialog.kt` source contract and `AppModalBottomSheet` scroll safety (zero nested `verticalScroll` or `LazyColumn`)
 * - 9-locale localization parity for all category, sub-category, and capability badge string keys
 */
class SensorCategoryAndSelectorContractTest {

    private fun resolveProjectRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return if (File(cwd, "app/src/main").exists()) cwd else cwd.parentFile ?: cwd
    }

    @Test
    fun testSensorCategory_exhaustive63MetricPartition() {
        val categories = SensorCategory.values()
        assertEquals("SensorCategory must define exactly 6 categories", 6, categories.size)
        categories.forEach { category ->
            assertTrue("titleResId must be non-zero for $category", category.titleResId != 0)
            assertTrue("iconResId must be non-zero for $category", category.iconResId != 0)
        }

        val subCategories = SensorSubCategory.values()
        assertEquals("SensorSubCategory must define exactly 9 sub-categories", 9, subCategories.size)
        subCategories.forEach { subCategory ->
            assertTrue("titleResId must be non-zero for $subCategory", subCategory.titleResId != 0)
        }

        val allSensors = SensorType.values().toList()
        assertEquals("SensorType must contain 63 constants", 63, allSensors.size)
        allSensors.forEach { sensorType ->
            assertNotNull("SensorType.${sensorType.name} must have a non-null category", sensorType.category)
        }

        val expectedPartitions: Map<SensorCategory, Set<SensorType>> = mapOf(
            SensorCategory.POWER_AND_PEDALING to setOf(
                SensorType.POWER,
                SensorType.NORMALIZED_POWER,
                SensorType.POWER_TO_WEIGHT,
                SensorType.INTENSITY_FACTOR,
                SensorType.TSS,
                SensorType.WORK_KJ,
                SensorType.W_PRIME_BAL,
                SensorType.POWER_5S,
                SensorType.POWER_1M,
                SensorType.POWER_5M,
                SensorType.POWER_20M,
                SensorType.POWER_PEAK_5S,
                SensorType.POWER_PEAK_1M,
                SensorType.POWER_PEAK_5M,
                SensorType.POWER_PEAK_20M,
                SensorType.TORQUE,
                SensorType.PEDAL_POWER_BALANCE,
                SensorType.PEDAL_SMOOTHNESS,
                SensorType.PEDAL_SMOOTHNESS_L,
                SensorType.PEDAL_SMOOTHNESS_R,
                SensorType.TORQUE_EFFECTIVENESS_L,
                SensorType.TORQUE_EFFECTIVENESS_R
            ),
            SensorCategory.HEART_RATE_AND_ENERGY to setOf(
                SensorType.HR,
                SensorType.CALORIES,
                SensorType.HRV_RMSSD,
                SensorType.HRV_DFA_A1,
                SensorType.RR_INTERVAL,
                SensorType.HRV_ARTIFACT_RATE,
                SensorType.LIVE_HR_DRIFT
            ),
            SensorCategory.SPEED_PACE_AND_DISTANCE to setOf(
                SensorType.SPEED_mps,
                SensorType.PACE_spm,
                SensorType.DISTANCE_m,
                SensorType.CADENCE,
                SensorType.STRIDES
            ),
            SensorCategory.TIME_AND_LAPS to setOf(
                SensorType.TIME_ACTIVE,
                SensorType.TIME_TOTAL,
                SensorType.TIME_LAP,
                SensorType.TIME_OF_DAY,
                SensorType.DISTANCE_m_LAP,
                SensorType.LAP_NR
            ),
            SensorCategory.ELEVATION to setOf(
                SensorType.ALTITUDE,
                SensorType.ASCENT,
                SensorType.DESCENT,
                SensorType.SLOPE,
                SensorType.VERTICAL_SPEED,
                SensorType.BEARING,
                SensorType.LINE_DISTANCE_m,
                SensorType.LATITUDE,
                SensorType.LONGITUDE
            ),
            SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS to setOf(
                SensorType.PHONE_BATTERY,
                SensorType.BATTERY_REMAINING_TIME,
                SensorType.SENSORS,
                SensorType.ACCUMULATED_SENSORS,
                SensorType.ACCURACY,
                SensorType.TEMPERATURE,
                SensorType.TEMPERATURE_MIN,
                SensorType.TEMPERATURE_MAX,
                SensorType.RADAR_THREAT_COUNT,
                SensorType.RADAR_CLOSEST_DISTANCE_m,
                SensorType.RADAR_APPROACH_SPEED_mps,
                SensorType.RADAR_VEHICLE_COUNT,
                SensorType.RADAR_PASSING_DISTANCE_m,
                SensorType.RADAR_LARGE_VEHICLE_COUNT
            )
        )

        expectedPartitions.forEach { (category, expectedSet) ->
            val actualSet = allSensors.filter { it.category == category }.toSet()
            assertEquals("Mismatch in SensorCategory.$category partition", expectedSet, actualSet)
        }

        // Sub-category partitions
        val expectedSubPartitions: Map<SensorSubCategory, Set<SensorType>> = mapOf(
            SensorSubCategory.POWER_LOAD to setOf(
                SensorType.NORMALIZED_POWER,
                SensorType.POWER_TO_WEIGHT,
                SensorType.INTENSITY_FACTOR,
                SensorType.TSS,
                SensorType.WORK_KJ
            ),
            SensorSubCategory.POWER_ROLLING to setOf(
                SensorType.POWER_5S,
                SensorType.POWER_1M,
                SensorType.POWER_5M,
                SensorType.POWER_20M
            ),
            SensorSubCategory.POWER_PEAK to setOf(
                SensorType.POWER_PEAK_5S,
                SensorType.POWER_PEAK_1M,
                SensorType.POWER_PEAK_5M,
                SensorType.POWER_PEAK_20M
            ),
            SensorSubCategory.PEDALING_DYNAMICS to setOf(
                SensorType.TORQUE,
                SensorType.PEDAL_POWER_BALANCE,
                SensorType.PEDAL_SMOOTHNESS,
                SensorType.PEDAL_SMOOTHNESS_L,
                SensorType.PEDAL_SMOOTHNESS_R,
                SensorType.TORQUE_EFFECTIVENESS_L,
                SensorType.TORQUE_EFFECTIVENESS_R
            ),
            SensorSubCategory.HRV_AND_DRIFT to setOf(
                SensorType.HRV_DFA_A1,
                SensorType.HRV_RMSSD,
                SensorType.RR_INTERVAL,
                SensorType.HRV_ARTIFACT_RATE,
                SensorType.LIVE_HR_DRIFT
            ),
            SensorSubCategory.LAP_METRICS to setOf(
                SensorType.TIME_LAP,
                SensorType.DISTANCE_m_LAP,
                SensorType.LAP_NR
            ),
            SensorSubCategory.RADAR_SAFETY to setOf(
                SensorType.RADAR_THREAT_COUNT,
                SensorType.RADAR_CLOSEST_DISTANCE_m,
                SensorType.RADAR_APPROACH_SPEED_mps,
                SensorType.RADAR_VEHICLE_COUNT,
                SensorType.RADAR_PASSING_DISTANCE_m,
                SensorType.RADAR_LARGE_VEHICLE_COUNT
            ),
            SensorSubCategory.AMBIENT_TEMPERATURE to setOf(
                SensorType.TEMPERATURE,
                SensorType.TEMPERATURE_MIN,
                SensorType.TEMPERATURE_MAX
            ),
            SensorSubCategory.PHONE_BATTERY_GROUP to setOf(
                SensorType.PHONE_BATTERY,
                SensorType.BATTERY_REMAINING_TIME
            )
        )

        expectedSubPartitions.forEach { (subCat, expectedSet) ->
            val actualSet = allSensors.filter { it.subCategory == subCat }.toSet()
            assertEquals("Mismatch in SensorSubCategory.$subCat partition", expectedSet, actualSet)
        }

        // Direct core metrics must return null subCategory
        val directMetrics = setOf(
            SensorType.POWER,
            SensorType.W_PRIME_BAL,
            SensorType.HR,
            SensorType.CALORIES,
            SensorType.SPEED_mps,
            SensorType.PACE_spm,
            SensorType.DISTANCE_m,
            SensorType.CADENCE,
            SensorType.STRIDES,
            SensorType.TIME_ACTIVE,
            SensorType.TIME_TOTAL,
            SensorType.TIME_OF_DAY,
            SensorType.ALTITUDE,
            SensorType.ASCENT,
            SensorType.DESCENT,
            SensorType.SLOPE,
            SensorType.VERTICAL_SPEED,
            SensorType.BEARING,
            SensorType.LINE_DISTANCE_m,
            SensorType.LATITUDE,
            SensorType.LONGITUDE,
            SensorType.SENSORS,
            SensorType.ACCUMULATED_SENSORS,
            SensorType.ACCURACY
        )

        directMetrics.forEach { sensorType ->
            assertNull("Direct metric ${sensorType.name} must have null subCategory", sensorType.subCategory)
        }
    }

    @Test
    fun testSensorTypeSearchFilter_matchesFullNameShortNameUnitAndEnumName() {
        val sample = listOf(
            SensorType.POWER_TO_WEIGHT,
            SensorType.NORMALIZED_POWER,
            SensorType.HR,
            SensorType.CADENCE,
            SensorType.ALTITUDE
        )
        val fullNames = mapOf(
            SensorType.POWER_TO_WEIGHT to "Power-to-Weight",
            SensorType.NORMALIZED_POWER to "Normalized Power",
            SensorType.HR to "Heart Rate",
            SensorType.CADENCE to "Cadence",
            SensorType.ALTITUDE to "Altitude"
        )
        val shortNames = mapOf(
            SensorType.POWER_TO_WEIGHT to "W/kg",
            SensorType.NORMALIZED_POWER to "WP",
            SensorType.HR to "HR",
            SensorType.CADENCE to "CAD",
            SensorType.ALTITUDE to "ALT"
        )
        val units = mapOf(
            SensorType.POWER_TO_WEIGHT to "W/kg",
            SensorType.NORMALIZED_POWER to "W",
            SensorType.HR to "bpm",
            SensorType.CADENCE to "rpm",
            SensorType.ALTITUDE to "m"
        )

        // Blank query returns all items
        assertEquals(
            sample,
            SensorTypeSearchFilter.filterByQuery(
                items = sample,
                query = "   ",
                fullNameResolver = { fullNames[it].orEmpty() },
                shortNameResolver = { shortNames[it].orEmpty() },
                unitResolver = { units[it].orEmpty() }
            )
        )

        // Match by full name (case-insensitive)
        assertEquals(
            listOf(SensorType.POWER_TO_WEIGHT, SensorType.NORMALIZED_POWER),
            SensorTypeSearchFilter.filterByQuery(
                items = sample,
                query = "power",
                fullNameResolver = { fullNames[it].orEmpty() },
                shortNameResolver = { shortNames[it].orEmpty() },
                unitResolver = { units[it].orEmpty() }
            )
        )

        // Match by short name ("WP")
        assertEquals(
            listOf(SensorType.NORMALIZED_POWER),
            SensorTypeSearchFilter.filterByQuery(
                items = sample,
                query = "wp",
                fullNameResolver = { fullNames[it].orEmpty() },
                shortNameResolver = { shortNames[it].orEmpty() },
                unitResolver = { units[it].orEmpty() }
            )
        )

        // Match by unit ("bpm")
        assertEquals(
            listOf(SensorType.HR),
            SensorTypeSearchFilter.filterByQuery(
                items = sample,
                query = "BPM",
                fullNameResolver = { fullNames[it].orEmpty() },
                shortNameResolver = { shortNames[it].orEmpty() },
                unitResolver = { units[it].orEmpty() }
            )
        )

        // No match returns empty list
        assertTrue(
            SensorTypeSearchFilter.filterByQuery(
                items = sample,
                query = "nonexistent_metric_xyz",
                fullNameResolver = { fullNames[it].orEmpty() },
                shortNameResolver = { shortNames[it].orEmpty() },
                unitResolver = { units[it].orEmpty() }
            ).isEmpty()
        )
    }

    @Test
    fun testEditSensorFieldDialog_sourceContractAndBottomSheetScrollSafety() {
        val root = resolveProjectRoot()
        val dialogFile = File(
            root,
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt"
        )
        assertTrue("EditSensorFieldDialog.kt must exist", dialogFile.exists())
        val source = dialogFile.readText()

        assertTrue("Must define SensorTypeSelectorSection", source.contains("fun SensorTypeSelectorSection("))
        assertTrue("Must invoke SensorTypeSelectorSection", source.contains("SensorTypeSelectorSection("))
        assertTrue("Must reference SensorCategory", source.contains("SensorCategory"))
        assertTrue("Must reference SensorSubCategory", source.contains("SensorSubCategory"))
        assertTrue("Must reference isBarSupported", source.contains("isBarSupported"))
        assertTrue("Must reference isGraphSupported", source.contains("isGraphSupported"))
        assertTrue("Must reference R.string.sensor_type_search_hint", source.contains("R.string.sensor_type_search_hint"))
        assertTrue("Must reference R.string.sensor_type_no_matches", source.contains("R.string.sensor_type_no_matches"))
        assertFalse(
            "Must NOT nest verticalScroll inside AppModalBottomSheet (docs/design_guidelines.md Section 5.8)",
            source.contains("verticalScroll(")
        )
        assertFalse(
            "Must NOT nest unbounded LazyColumn inside AppModalBottomSheet (docs/design_guidelines.md Section 5.8)",
            source.contains("LazyColumn(")
        )
    }

    @Test
    fun testNineLocaleLocalizationParity_allSensorCategoryAndSearchStringsPresent() {
        val root = resolveProjectRoot()
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
            "sensor_category_power_and_pedaling",
            "sensor_category_heart_rate_and_energy",
            "sensor_category_speed_pace_and_distance",
            "sensor_category_time_and_laps",
            "sensor_category_elevation",
            "sensor_category_system_environment_and_diagnostics",
            "sensor_sub_category_power_load",
            "sensor_sub_category_power_rolling",
            "sensor_sub_category_power_peak",
            "sensor_sub_category_pedaling_dynamics",
            "sensor_sub_category_hrv_and_drift",
            "sensor_sub_category_lap_metrics",
            "sensor_sub_category_radar_safety",
            "sensor_sub_category_ambient_temperature",
            "sensor_sub_category_phone_battery",
            "sensor_capability_bar",
            "sensor_capability_graph",
            "sensor_type_search_hint",
            "sensor_type_no_matches"
        )

        val dbf = DocumentBuilderFactory.newInstance()
        locales.forEach { localeDir ->
            val dir = File(root, "app/src/main/res/$localeDir")
            assertTrue("Missing directory $localeDir", dir.exists())

            val entries = mutableMapOf<String, String>()
            val xmlFiles = dir.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") } ?: emptyArray()
            assertTrue("No strings files found in $localeDir", xmlFiles.isNotEmpty())

            for (file in xmlFiles) {
                val doc = dbf.newDocumentBuilder().parse(file)
                val nodes = doc.getElementsByTagName("string")
                for (i in 0 until nodes.length) {
                    val elem = nodes.item(i)
                    val name = elem.attributes?.getNamedItem("name")?.nodeValue
                    if (name != null) {
                        entries[name] = elem.textContent ?: ""
                    }
                }
            }

            requiredKeys.forEach { key ->
                val value = entries[key]
                assertNotNull("Missing string '$key' in $localeDir", value)
                assertTrue("Blank string '$key' in $localeDir", value!!.isNotBlank())
            }
        }
    }
}
