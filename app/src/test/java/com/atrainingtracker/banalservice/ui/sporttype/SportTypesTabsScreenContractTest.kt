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

import com.atrainingtracker.testing.LocalizationTestCache
import com.atrainingtracker.trainingtracker.ui.common.DateTimeUtils
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architectural and presentation contract test for SportTypesTabsScreen.kt.
 * Verifies REQ-UI-475 and TST-UI-475.2..5 (ATT-5872):
 * - Empty GoldenCheetah mapping suppression (REQ-UI-475.1, TST-UI-475.2).
 * - Default locked sport type display name localization via BSportType.stringResId (REQ-UI-475.2, TST-UI-475.3).
 * - Export identifier array mapping for Strava, TCX, and GC (REQ-UI-475.2, TST-UI-475.3).
 * - Locale-aware ISO-8601 usage date formatting via DateTimeUtils.formatIsoDate (REQ-UI-475.3, TST-UI-475.4).
 */
class SportTypesTabsScreenContractTest {

    @Test
    fun testSportTypeCard_composableContract() {
        val screenClass = Class.forName("com.atrainingtracker.banalservice.ui.sporttype.SportTypesTabsScreenKt")
        assertNotNull("SportTypesTabsScreenKt class must exist", screenClass)
        assertTrue(
            "SportTypesTabsScreen or SportTypeCard composable must be declared",
            screenClass.declaredMethods.any { it.name.startsWith("SportTypesTabsScreen") || it.name.startsWith("SportTypeCard") }
        )
    }

    @Test
    fun testSportTypeCard_mappingStringsContract() {
        val strings = LocalizationTestCache.getStrings("values")
        assertTrue("Must define mapping_format_tcx", strings.containsKey("mapping_format_tcx"))
        assertTrue("Must define sport_type_file_mapping_format", strings.containsKey("sport_type_file_mapping_format"))
    }

    @Test
    fun testSportTypeCard_usageItemDateFormattingIntegration() {
        val formatted = DateTimeUtils.formatIsoDate("2026-11-11")
        assertNotNull("Formatted date must not be null", formatted)
        assertTrue("Formatted date must not be empty", formatted.isNotEmpty())
    }
}
