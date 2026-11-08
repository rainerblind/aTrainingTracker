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

package com.atrainingtracker.trainingtracker.ui.equipment

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Structural contract tests for Equipment & Sensors UI polish (REQ-UI-492 / TST-UI-492 / ATT-5847).
 */
class EquipmentSensorsUiPolishContractTest {

    private fun findFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("File not found in candidates: $relativePath")
    }

    @Test
    fun testStravaGearNameDeduplicationContract() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentTabsScreen.kt")
        val content = file.readText()

        // 1. Checks equality between stravaName and local equipment name
        assertTrue(
            "EquipmentTabsScreen must check equality between stravaName and name",
            content.contains("isStravaIdentical") && content.contains("stravaName.trim().equals(item.name.trim()")
        )

        // 2. Inline Strava logo in title row when identical
        assertTrue(
            "EquipmentTabsScreen must render inline StravaOriginalLogo in title row when identical",
            content.contains("if (isStravaIdentical)") && content.contains("StravaOriginalLogo(modifier = Modifier.height(14.dp))")
        )

        // 3. Subtitle row suppressed when identical
        assertTrue(
            "EquipmentTabsScreen must suppress subtitle row when stravaName is identical to name",
            content.contains("!isStravaIdentical")
        )
    }

    @Test
    fun testUsageDateFormattingAndFrameTypeLocalizationContract() {
        val tabsFile = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentTabsScreen.kt")
        val tabsContent = tabsFile.readText()

        // 1. UsageItem calls DateTimeUtils.formatIsoDate
        assertTrue(
            "UsageItem must format date using DateTimeUtils.formatIsoDate",
            tabsContent.contains("DateTimeUtils.formatIsoDate(date)")
        )

        // 2. EquipmentTabsScreen uses localized string resources for frame types
        assertTrue(
            "EquipmentTabsScreen must resolve frame types via stringResource",
            tabsContent.contains("R.string.equipment_bike_type_mtb") &&
                    tabsContent.contains("R.string.equipment_bike_type_cross") &&
                    tabsContent.contains("R.string.equipment_bike_type_road") &&
                    tabsContent.contains("R.string.equipment_bike_type_tt")
        )

        // 3. BikeTypeSelector uses localized string resources
        val selectorFile = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/BikeTypeSelector.kt")
        val selectorContent = selectorFile.readText()
        assertTrue(
            "BikeTypeSelector must resolve bike types via stringResource",
            selectorContent.contains("R.string.equipment_bike_type_mtb") &&
                    selectorContent.contains("R.string.equipment_bike_type_cross") &&
                    selectorContent.contains("R.string.equipment_bike_type_road") &&
                    selectorContent.contains("R.string.equipment_bike_type_tt")
        )
    }

    @Test
    fun testGermanSpellingAndPlaceholderHarmonizationContract() {
        val deStringsFile = findFile("src/main/res/values-de/strings.xml")
        val deStrings = deStringsFile.readText()

        // 1. Radtyp spelling
        assertTrue(
            "values-de/strings.xml must define equipment_bike_type as 'Radtyp'",
            deStrings.contains("<string name=\"equipment_bike_type\">Radtyp</string>")
        )
        assertTrue(
            "values-de/strings.xml must define equipment_select_bike_type as 'Radtyp auswählen'",
            deStrings.contains("<string name=\"equipment_select_bike_type\">Radtyp auswählen</string>")
        )

        // 2. EditEquipmentDialog binds harmonized empty placeholder
        val dialogFile = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EditEquipmentDialog.kt")
        val dialogContent = dialogFile.readText()
        assertTrue(
            "EditEquipmentDialog must bind equipment_no_sports_linked for both dropdowns",
            dialogContent.contains("R.string.equipment_no_sports_linked")
        )
    }

    @Test
    fun testSensorDetailStatusLedAndManufacturerCapitalizationContract() {
        val editDeviceFile = findFile("src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceDialog.kt")
        val editDeviceContent = editDeviceFile.readText()

        // 1. Status LED synchronized with DeviceStatusRow
        assertTrue(
            "EditDeviceDialog must pass isConnected to DeviceStatusRow and use it for LED dot color",
            editDeviceContent.contains("isConnected = isConnected") &&
                    editDeviceContent.contains("TTColor.ConnectionStatusGreen else Color.LightGray")
        )

        // 2. EquipmentSection styled with OutlinedTextField
        assertTrue(
            "EquipmentSection must render OutlinedTextField with dropdown affordance",
            editDeviceContent.contains("OutlinedTextField(") &&
                    editDeviceContent.contains("Icons.Default.ArrowDropDown")
        )

        // 3. DeviceData manufacturer capitalization
        val deviceDataFile = findFile("src/main/java/com/atrainingtracker/banalservice/ui/devices/devicedata/DeviceData.kt")
        val deviceDataContent = deviceDataFile.readText()
        assertTrue(
            "DeviceData must capitalize manufacturer via replaceFirstChar titlecase",
            deviceDataContent.contains("replaceFirstChar") && deviceDataContent.contains("titlecase")
        )

        // 4. Pure unit verification of titlecasing
        val lowercaseSamsung = "samsung".replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        org.junit.Assert.assertEquals("Samsung", lowercaseSamsung)
        val lowercaseGoogle = "google".replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        org.junit.Assert.assertEquals("Google", lowercaseGoogle)
    }
}
