/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.localization

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Resource localization tests for:
 * - TST-UI-028 (REQ-UI-018): Relative elevation gain (Ascent/Höhenmeter) vs absolute altitude (Altitude/Höhe).
 * - TST-UI-075 (REQ-UI-115): Concise settings drawer titles without redundant Settings/Einstellungen qualifiers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UiDesignSystemTerminologyTest {

    private fun localizedContext(locale: Locale): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val cfg = Configuration(base.resources.configuration).apply {
            setLocale(locale)
        }
        return base.createConfigurationContext(cfg)
    }

    @Test
    fun elevationTerminology_distinguishesRelativeAscentFromAbsoluteAltitude_TST_UI_028() {
        // TST-UI-028
        val en = localizedContext(Locale.ENGLISH)
        val de = localizedContext(Locale.GERMAN)

        val enElevation = en.getString(R.string.elevation)
        val enMinAlt = en.getString(R.string.marker_min_altitude)
        assertEquals("elevation", enElevation)
        assertEquals("Min Altitude", enMinAlt)
        assertNotEquals(enElevation.lowercase(), enMinAlt.lowercase())

        val deElevation = de.getString(R.string.elevation)
        val deMinAlt = de.getString(R.string.marker_min_altitude)
        assertEquals("Höhenmeter", deElevation)
        assertEquals("Min. Höhe", deMinAlt)
    }

    @Test
    fun navigationDrawerSettingsTitles_useConciseEnAndDeTerminology_TST_UI_075() {
        // TST-UI-075
        val en = localizedContext(Locale.ENGLISH)
        val de = localizedContext(Locale.GERMAN)

        assertEquals("Display", en.getString(R.string.Display))
        assertEquals("Search", en.getString(R.string.Search_Settings))

        assertEquals("Display", de.getString(R.string.Display))
        assertEquals("Suche", de.getString(R.string.Search_Settings))

        assertFalse(en.getString(R.string.Display).contains("Settings", ignoreCase = true))
        assertFalse(en.getString(R.string.Search_Settings).contains("Settings", ignoreCase = true))
        assertFalse(de.getString(R.string.Search_Settings).contains("Einstellungen", ignoreCase = true))
    }
}
