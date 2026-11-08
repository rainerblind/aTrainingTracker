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

package com.atrainingtracker.banalservice.ui.sporttype

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural and presentation contract test for SportTypesTabsScreen.kt.
 * Verifies REQ-UI-475 and TST-UI-475.2..5 (ATT-5872):
 * - Empty GoldenCheetah mapping suppression (REQ-UI-475.1, TST-UI-475.2).
 * - Default locked sport type display name localization via BSportType.stringResId (REQ-UI-475.2, TST-UI-475.3).
 * - Export identifier array mapping for Strava, TCX, and GC (REQ-UI-475.2, TST-UI-475.3).
 * - Locale-aware ISO-8601 usage date formatting via DateTimeUtils.formatIsoDate (REQ-UI-475.3, TST-UI-475.4).
 */
class SportTypesTabsScreenContractTest {

    private fun resolveSourceFile(relativePath: String): File {
        return sequenceOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath")
        ).firstOrNull { it.exists() } ?: File(relativePath)
    }

    @Test
    fun testSportTypeCard_conditionalGcMappingSuppression() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/banalservice/ui/sporttype/SportTypesTabsScreen.kt")
        assertTrue("SportTypesTabsScreen.kt must exist", file.exists())
        val content = file.readText()

        val cardBody = content.substringAfter("fun SportTypeCard(")

        // Asserts that gcName is checked for blank / null before displaying GC mapping
        assertTrue(
            "SportTypeCard must check item.gcName.isNullOrBlank() or equivalent to suppress empty GC label",
            cardBody.contains("item.gcName.isNullOrBlank()") || cardBody.contains("item.gcName.isBlank()")
        )

        // Asserts that when gcName is blank, only mapping_format_tcx is rendered
        assertTrue(
            "SportTypeCard must format only mapping_format_tcx when gcName is blank",
            cardBody.contains("stringResource(R.string.mapping_format_tcx, localizedTcx)")
        )

        // Asserts that combined format is used when gcName is present
        assertTrue(
            "SportTypeCard must format sport_type_file_mapping_format when gcName is non-blank",
            cardBody.contains("R.string.sport_type_file_mapping_format")
        )
    }

    @Test
    fun testSportTypeCard_defaultSportDisplayNameLocalization() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/banalservice/ui/sporttype/SportTypesTabsScreen.kt")
        assertTrue("SportTypesTabsScreen.kt must exist", file.exists())
        val content = file.readText()

        val cardBody = content.substringAfter("fun SportTypeCard(")

        // Asserts that locked default sport types resolve localized name via bSportType.stringResId
        assertTrue(
            "SportTypeCard must resolve displayName using item.bSportType.stringResId when !item.isEditable",
            cardBody.contains("item.bSportType.stringResId")
        )
        assertTrue(
            "SportTypeCard must bind displayName to Title Text",
            cardBody.contains("text = displayName")
        )
    }

    @Test
    fun testSportTypeCard_exportArrayMappings() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/banalservice/ui/sporttype/SportTypesTabsScreen.kt")
        assertTrue("SportTypesTabsScreen.kt must exist", file.exists())
        val content = file.readText()

        val cardBody = content.substringAfter("fun SportTypeCard(")

        // Strava array mappings
        assertTrue(
            "SportTypeCard must load Strava arrays for UI mapping",
            cardBody.contains("R.array.Strava_Sport_Types_UI_Names") &&
                cardBody.contains("R.array.Strava_Sport_Types_Strava_Names")
        )

        // TCX array mapping
        assertTrue(
            "SportTypeCard must load TCX_Sport_Types for localized TCX mapping",
            cardBody.contains("R.array.TCX_Sport_Types")
        )

        // GC array mapping
        assertTrue(
            "SportTypeCard must load GC_Sport_Types for localized GC mapping",
            cardBody.contains("R.array.GC_Sport_Types")
        )
    }

    @Test
    fun testSportTypeCard_usageItemDateFormattingIntegration() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/banalservice/ui/sporttype/SportTypesTabsScreen.kt")
        assertTrue("SportTypesTabsScreen.kt must exist", file.exists())
        val content = file.readText()

        val cardBody = content.substringAfter("fun SportTypeCard(")

        // Asserts UsageItem dates pass through DateTimeUtils.formatIsoDate
        assertTrue(
            "UsageItem dates must be formatted via DateTimeUtils.formatIsoDate",
            cardBody.contains("DateTimeUtils.formatIsoDate(it)")
        )
    }
}
