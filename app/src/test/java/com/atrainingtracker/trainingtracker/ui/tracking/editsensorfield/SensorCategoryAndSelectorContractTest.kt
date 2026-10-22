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
import com.atrainingtracker.banalservice.sensor.SensorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit, architectural contract, and 9-language localization verification suite for ATT-3852
 * (`REQ-UI-395`, `TST-UI-358`):
 * - Exhaustive 43-metric `SensorCategory` partition (`5 + 4 + 2 + 2 + 13 + 9 + 8 = 43`)
 * - `SensorTypeSearchFilter` case-insensitive query filtering across full name, short name, unit, and enum name
 * - `EditSensorFieldDialog.kt` source contract and `AppModalBottomSheet` scroll safety (zero nested `verticalScroll` or `LazyColumn`)
 * - 9-locale localization parity for all 9 new string keys
 */
class SensorCategoryAndSelectorContractTest {

    private fun resolveProjectRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return if (File(cwd, "app/src/main").exists()) cwd else cwd.parentFile ?: cwd
    }

    @Test
    fun testSensorCategory_exhaustive43MetricPartition() {
        val categories = SensorCategory.values()
        assertEquals("SensorCategory must define exactly 7 categories", 7, categories.size)
        categories.forEach { category ->
            assertTrue("titleResId must be non-zero for $category", category.titleResId != 0)
            assertTrue("iconResId must be non-zero for $category", category.iconResId != 0)
        }

        val allSensors = SensorType.values().toList()
        assertEquals("SensorType must contain 44 constants", 44, allSensors.size)
        allSensors.forEach { sensorType ->
            assertNotNull("SensorType.${sensorType.name} must have a non-null category", sensorType.category)
        }

        val expectedPartitions: Map<SensorCategory, Set<SensorType>> = mapOf(
            SensorCategory.TIME_AND_LAPS to setOf(
                SensorType.TIME_ACTIVE,
                SensorType.TIME_TOTAL,
                SensorType.TIME_LAP,
                SensorType.TIME_OF_DAY,
                SensorType.LAP_NR
            ),
            SensorCategory.SPEED_PACE_AND_DISTANCE to setOf(
                SensorType.SPEED_mps,
                SensorType.PACE_spm,
                SensorType.DISTANCE_m,
                SensorType.DISTANCE_m_LAP
            ),
            SensorCategory.CADENCE_AND_RUNNING to setOf(
                SensorType.CADENCE,
                SensorType.STRIDES
            ),
            SensorCategory.HEART_RATE_AND_ENERGY to setOf(
                SensorType.HR,
                SensorType.CALORIES
            ),
            SensorCategory.POWER_AND_PEDALING to setOf(
                SensorType.POWER,
                SensorType.NORMALIZED_POWER,
                SensorType.POWER_TO_WEIGHT,
                SensorType.INTENSITY_FACTOR,
                SensorType.TSS,
                SensorType.WORK_KJ,
                SensorType.W_PRIME_BAL,
                SensorType.TORQUE,
                SensorType.PEDAL_POWER_BALANCE,
                SensorType.PEDAL_SMOOTHNESS,
                SensorType.PEDAL_SMOOTHNESS_L,
                SensorType.PEDAL_SMOOTHNESS_R,
                SensorType.TORQUE_EFFECTIVENESS_L,
                SensorType.TORQUE_EFFECTIVENESS_R
            ),
            SensorCategory.ELEVATION_AND_NAVIGATION to setOf(
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
                SensorType.TEMPERATURE_MAX
            )
        )

        expectedPartitions.forEach { (category, expectedSet) ->
            val actualSet = allSensors.filter { it.category == category }.toSet()
            assertEquals("Mismatch in SensorCategory.$category partition", expectedSet, actualSet)
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
            SensorType.NORMALIZED_POWER to "NP",
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

        // Match by short name ("NP")
        assertEquals(
            listOf(SensorType.NORMALIZED_POWER),
            SensorTypeSearchFilter.filterByQuery(
                items = sample,
                query = "np",
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
            "sensor_category_time_and_laps",
            "sensor_category_speed_pace_and_distance",
            "sensor_category_cadence_and_running",
            "sensor_category_heart_rate_and_energy",
            "sensor_category_power_and_pedaling",
            "sensor_category_elevation_and_navigation",
            "sensor_category_system_environment_and_diagnostics",
            "sensor_type_search_hint",
            "sensor_type_no_matches"
        )

        val dbf = DocumentBuilderFactory.newInstance()
        locales.forEach { localeDir ->
            val stringsFile = File(root, "app/src/main/res/$localeDir/strings.xml")
            assertTrue("Missing $localeDir/strings.xml", stringsFile.exists())
            val doc = dbf.newDocumentBuilder().parse(stringsFile)
            val nodes = doc.getElementsByTagName("string")
            val entries = mutableMapOf<String, String>()
            for (i in 0 until nodes.length) {
                val elem = nodes.item(i)
                val name = elem.attributes?.getNamedItem("name")?.nodeValue
                if (name != null) {
                    entries[name] = elem.textContent ?: ""
                }
            }

            requiredKeys.forEach { key ->
                val value = entries[key]
                assertNotNull("Missing string '$key' in $localeDir/strings.xml", value)
                assertTrue("Blank string '$key' in $localeDir/strings.xml", value!!.isNotBlank())
            }
        }
    }
}
