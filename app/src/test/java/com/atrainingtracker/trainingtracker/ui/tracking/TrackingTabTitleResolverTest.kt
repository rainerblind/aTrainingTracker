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

import android.content.Context
import android.content.res.Resources
import com.atrainingtracker.R
import com.atrainingtracker.testing.LocalizationTestCache
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [TrackingTabTitleResolver] verifying 100% 9-language parity against
 * `values-[locale]/strings.xml` (`text_default`, `tab_power`, `tab_map`, `tab_laps`), cross-locale
 * re-localization across all 9x9 locale pairs, and strict preservation of custom tab names
 * (`REQ-TRK-097`, `TST-TRK-097.1`).
 */
// Linked test specs: TST-TRK-097
class TrackingTabTitleResolverTest {

    private val locales = listOf(
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

    private val keyMapping = mapOf(
        "text_default" to DefaultTrackingTabKey.DEFAULT,
        "tab_power" to DefaultTrackingTabKey.POWER,
        "tab_map" to DefaultTrackingTabKey.MAP,
        "tab_laps" to DefaultTrackingTabKey.LAPS
    )

    @Test
    fun all9LocaleDefaultTabStrings_mapDeterministicallyToExpectedKey_tstTrk097_1() {
        for (locale in locales) {
            val strings = LocalizationTestCache.getStrings(locale)
            for ((xmlKey, expectedEnum) in keyMapping) {
                val localized = strings[xmlKey]
                requireNotNull(localized) { "Missing $xmlKey in $locale/strings.xml" }
                assertEquals(
                    "Persisted '$localized' from $locale/$xmlKey must resolve to $expectedEnum",
                    expectedEnum,
                    TrackingTabTitleResolver.resolveDefaultTabKey(localized)
                )
            }
        }
    }

    @Test
    fun crossLocaleMatrix_all9SourceLocalesRelocalizeToAll9TargetLocales_tstTrk097_1() {
        for (sourceLocale in locales) {
            val sourceStrings = LocalizationTestCache.getStrings(sourceLocale)
            for (targetLocale in locales) {
                val targetStrings = LocalizationTestCache.getStrings(targetLocale)
                val targetContext = mockk<Context>()
                every { targetContext.getString(R.string.text_default) } returns targetStrings.getValue("text_default")
                every { targetContext.getString(R.string.tab_power) } returns targetStrings.getValue("tab_power")
                every { targetContext.getString(R.string.tab_map) } returns targetStrings.getValue("tab_map")
                every { targetContext.getString(R.string.tab_laps) } returns targetStrings.getValue("tab_laps")

                for (xmlKey in keyMapping.keys) {
                    val persisted = sourceStrings.getValue(xmlKey)
                    val expectedTarget = targetStrings.getValue(xmlKey)
                    assertEquals(
                        "Failed $sourceLocale ($persisted) -> $targetLocale ($expectedTarget) for $xmlKey",
                        expectedTarget,
                        TrackingTabTitleResolver.resolveTitle(targetContext, persisted)
                    )
                }
            }
        }
    }

    @Test
    fun userRenamedAndNumericTitles_neverMatchDefaultKeysAndStayPreserved_tstTrk097_1() {
        val targetContext = mockk<Context>()
        every { targetContext.getString(any()) } returns "ShouldNotBeUsed"

        val customNames = listOf(
            "Intervalle",
            "Race Cockpit",
            "My Map",
            "4",
            "Standard 2",
            "Endurance",
            ""
        )
        for (customName in customNames) {
            assertNull("Custom name '$customName' must not match a default key", TrackingTabTitleResolver.resolveDefaultTabKey(customName))
            assertEquals(customName, TrackingTabTitleResolver.resolveTitle(targetContext, customName))
        }
        assertNull(TrackingTabTitleResolver.resolveDefaultTabKey(null))
    }

    @Test
    fun emptyOrThrowingContextGetString_fallsBackSafelyToPersistedName() {
        val emptyContext = mockk<Context>()
        every { emptyContext.getString(any()) } returns ""
        assertEquals("Standard", TrackingTabTitleResolver.resolveTitle(emptyContext, "Standard"))

        val throwingContext = mockk<Context>()
        every { throwingContext.getString(any()) } throws Resources.NotFoundException("test")
        assertEquals("Karte", TrackingTabTitleResolver.resolveTitle(throwingContext, "Karte"))
    }
}
