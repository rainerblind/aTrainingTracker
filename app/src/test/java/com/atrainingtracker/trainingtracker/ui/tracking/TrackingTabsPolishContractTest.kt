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

package com.atrainingtracker.trainingtracker.ui.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Verification suite for Cockpit Edit-Mode Typography, Localization Parity & FAB Layout Polish
 * (REQ-UI-471 / TST-UI-471 / ATT-5879).
 */
class TrackingTabsPolishContractTest {

    private fun findProjectRoot(): File {
        var dir: File = File(".").canonicalFile
        while (dir.parentFile != null) {
            if (File(dir, "gradlew").exists() && File(dir, "app").exists()) {
                return dir
            }
            dir = dir.parentFile!!
        }
        return File(".").canonicalFile
    }

    private fun resolveSourceFile(relativePath: String): File {
        val root = findProjectRoot()
        val target = File(root, relativePath)
        assertTrue("File must exist: $relativePath", target.exists())
        return target
    }

    @Test
    fun testSensorFieldView_enforcesSingleLineAndScalingContracts_TST_UI_471_1() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt")
        val content = file.readText()

        // Telemetry value single line and softWrap false
        assertTrue("SensorFieldView must enforce maxLines = 1 on value", content.contains("text = fieldState.value,\n                                style = valueStyle,\n                                maxLines = 1,\n                                softWrap = false"))
        // Telemetry value responsive font scaling in configuration mode
        assertTrue("SensorFieldView must scale font size in CONFIGURATION mode", content.contains("screenMode == ScreenMode.CONFIGURATION && fieldState.value.length >= 6"))
        // Filter badge single line and softWrap false
        assertTrue("SensorFieldView must enforce maxLines = 1 on filter badge", content.contains("text = fieldState.filterDescription,\n                            style = filterStyle,\n                            fontStyle = FontStyle.Italic,\n                            textAlign = TextAlign.End,\n                            maxLines = 1,\n                            softWrap = false"))
        // Localized content descriptions
        assertTrue("SensorFieldView must use cd_move_field", content.contains("stringResource(R.string.cd_move_field)"))
        assertTrue("SensorFieldView must use cd_delete_field", content.contains("stringResource(R.string.cd_delete_field)"))
    }

    @Test
    fun testLocalization_stringsAnd9LocaleParity_TST_UI_471_2() {
        val root = findProjectRoot()
        val deStrings = File(root, "app/src/main/res/values-de/strings.xml").readText()
        val deFilterStrings = File(root, "app/src/main/res/values-de/strings_filters.xml").readText()
        val defaultStrings = File(root, "app/src/main/res/values/strings.xml").readText()

        fun extractString(xml: String, key: String): String {
            val matcher = Pattern.compile("<string\\s+name=\"$key\"[^>]*>(.*?)</string>", Pattern.DOTALL).matcher(xml)
            assertTrue("String '$key' must exist", matcher.find())
            return matcher.group(1)?.trim().orEmpty()
        }

        // SI unit s
        assertEquals("s", extractString(defaultStrings, "units_seconds"))

        // German strings
        assertEquals("Aktiv", extractString(deStrings, "time_active_short"))
        assertEquals("Runde", extractString(deStrings, "time_lap_short"))
        assertEquals("Rundenzeit", extractString(deStrings, "time_lap"))
        assertEquals("Runde Nr.", extractString(deStrings, "lap_number_short"))
        assertEquals("Rundennummer", extractString(deStrings, "lap_number"))
        assertEquals("Dist. Runde", extractString(deStrings, "distance_lap_short"))
        assertEquals("Rundendistanz", extractString(deStrings, "distance_lap"))
        assertEquals("Live-Segmente", extractString(deStrings, "config_tracking__showLiveSegments"))
        assertEquals("Runden-Button", extractString(deStrings, "config_tracking__showLapButton"))
        assertEquals("Anzeige anpassen", extractString(deStrings, "edit_field"))
        assertEquals("Fertig", extractString(deStrings, "Done"))
        assertEquals("gleitender Durchschnitt", extractString(deFilterStrings, "filter_moving_average"))

        // 9-locale parity for action content descriptions
        val locales = listOf("values", "values-de", "values-es", "values-fr", "values-it", "values-ja", "values-nl", "values-pl", "values-pt")
        for (loc in locales) {
            val file = File(root, "app/src/main/res/$loc/strings.xml")
            assertTrue("Locale file $file must exist", file.exists())
            val xml = file.readText()
            assertTrue("cd_move_field must exist in $loc", extractString(xml, "cd_move_field").isNotEmpty())
            assertTrue("cd_delete_field must exist in $loc", extractString(xml, "cd_delete_field").isNotEmpty())
        }
    }

    @Test
    fun testEditSensorFieldDialog_polishContracts_TST_UI_471_3() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt")
        val content = file.readText()

        // Breadcrumb maxLines = 2
        assertTrue("Category breadcrumb must allow maxLines = 2", content.contains("text = if (categoryTitle != null)") && content.contains("maxLines = 2"))
        // Relative weight start alignment
        assertTrue("Relative weight header must fillMaxWidth", content.contains("text = stringResource(R.string.sensor_field_relative_weight)") && content.contains("modifier = Modifier.fillMaxWidth()"))
        // Smoothing button with Icons.Default.Tune and ArrowDropDown
        assertTrue("Smoothing button must render Tune icon", content.contains("imageVector = Icons.Default.Tune"))
        assertTrue("Smoothing button must render ArrowDropDown icon", content.contains("imageVector = Icons.Default.ArrowDropDown"))
    }

    @Test
    fun testSensorGridScreen_elevationProfileBottomClearance_TST_UI_471_4() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "SensorGridScreen must calculate fabBottomClearance (68dp) in portrait mode",
            content.contains("val fabBottomClearance = if (!isLandscape && (state.showBikeBell || state.showLapButton) && screenMode != ScreenMode.CONFIGURATION) 68.dp else 0.dp")
        )
        assertTrue(
            "SensorGridScreen ElevationProfile must apply bottom clearance",
            content.contains("padding(top = 8.dp, bottom = fabBottomClearance)")
        )
    }

    @Test
    fun testTrackingTabHeaders_renderSportContext_TST_UI_471_5() {
        val previewHeader = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabPreviewHeader.kt").readText()
        val configHeader = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabConfigHeader.kt").readText()
        val trackingTabsScreen = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt").readText()

        assertTrue("TrackingTabPreviewHeader must declare sportName parameter", previewHeader.contains("sportName: String = \"\""))
        assertTrue("TrackingTabPreviewHeader must render sportName", previewHeader.contains("text = sportName") && previewHeader.contains("style = MaterialTheme.typography.labelMedium"))

        assertTrue("TrackingTabConfigHeader must declare sportName parameter", configHeader.contains("sportName: String = \"\""))
        assertTrue("TrackingTabConfigHeader must render sportName", configHeader.contains("text = sportName") && configHeader.contains("style = MaterialTheme.typography.labelMedium"))

        assertTrue("TrackingTabsScreen must pass sportName to config header", trackingTabsScreen.contains("val sportName = stringResource(id = activityType.titleId)"))
    }
}
