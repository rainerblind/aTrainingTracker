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

package com.atrainingtracker.trainingtracker.ui.tracking.typography

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.atrainingtracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Cockpit Typography domain resolution, fallback mechanisms,
 * weight mappings, and display resource bindings (REQ-UI-212 / TST-UI-166).
 */
class CockpitTypographyResolutionTest {

    @Test
    fun allFontFamilies_resolveNonNullFontFamily() {
        assertEquals(13, CockpitFontFamily.values().size)
        for (family in CockpitFontFamily.values()) {
            val resolved = CockpitTypography.resolveFontFamily(family)
            assertNotNull("Resolved font family must not be null for $family", resolved)
        }
    }

    @Test
    fun fallbackFontFamily_mapsExpectedFamilies() {
        assertEquals(FontFamily.Default, CockpitTypography.fallbackFontFamily(CockpitFontFamily.SYSTEM_DEFAULT))
        assertEquals(FontFamily.Default, CockpitTypography.fallbackFontFamily(CockpitFontFamily.SEVEN_SEGMENT))
        assertEquals(FontFamily.SansSerif, CockpitTypography.fallbackFontFamily(CockpitFontFamily.MODERN_ATHLETIC))
        assertEquals(FontFamily.Monospace, CockpitTypography.fallbackFontFamily(CockpitFontFamily.MONOSPACE))
        assertEquals(FontFamily.Cursive, CockpitTypography.fallbackFontFamily(CockpitFontFamily.PLAYFUL))
        assertEquals(FontFamily.SansSerif, CockpitTypography.fallbackFontFamily(CockpitFontFamily.BEBAS_NEUE))
        assertEquals(FontFamily.SansSerif, CockpitTypography.fallbackFontFamily(CockpitFontFamily.TEKO))
        assertEquals(FontFamily.SansSerif, CockpitTypography.fallbackFontFamily(CockpitFontFamily.BARLOW_CONDENSED))
        assertEquals(FontFamily.SansSerif, CockpitTypography.fallbackFontFamily(CockpitFontFamily.OSWALD))
        assertEquals(FontFamily.SansSerif, CockpitTypography.fallbackFontFamily(CockpitFontFamily.CHAKRA_PETCH))
        assertEquals(FontFamily.SansSerif, CockpitTypography.fallbackFontFamily(CockpitFontFamily.OXANIUM))
        assertEquals(FontFamily.SansSerif, CockpitTypography.fallbackFontFamily(CockpitFontFamily.RAJDHANI))
        assertEquals(FontFamily.SansSerif, CockpitTypography.fallbackFontFamily(CockpitFontFamily.MONTSERRAT))
    }

    @Test
    fun cockpitFontWeight_mapsCorrectlyToComposeFontWeight() {
        assertEquals(FontWeight.Normal, CockpitFontWeight.NORMAL.asFontWeight())
        assertEquals(FontWeight.SemiBold, CockpitFontWeight.SEMI_BOLD.asFontWeight())
        assertEquals(FontWeight.Bold, CockpitFontWeight.BOLD.asFontWeight())
    }

    @Test
    fun cockpitFontFamily_getDisplayNameRes_returnsValidResourceIds() {
        assertEquals(R.string.tuning_font_system_default, CockpitFontFamily.SYSTEM_DEFAULT.getDisplayNameRes())
        assertEquals(R.string.tuning_font_seven_segment, CockpitFontFamily.SEVEN_SEGMENT.getDisplayNameRes())
        assertEquals(R.string.tuning_font_modern_athletic, CockpitFontFamily.MODERN_ATHLETIC.getDisplayNameRes())
        assertEquals(R.string.tuning_font_monospace, CockpitFontFamily.MONOSPACE.getDisplayNameRes())
        assertEquals(R.string.tuning_font_playful, CockpitFontFamily.PLAYFUL.getDisplayNameRes())
        assertEquals(R.string.tuning_font_bebas_neue, CockpitFontFamily.BEBAS_NEUE.getDisplayNameRes())
        assertEquals(R.string.tuning_font_teko, CockpitFontFamily.TEKO.getDisplayNameRes())
        assertEquals(R.string.tuning_font_barlow_condensed, CockpitFontFamily.BARLOW_CONDENSED.getDisplayNameRes())
        assertEquals(R.string.tuning_font_oswald, CockpitFontFamily.OSWALD.getDisplayNameRes())
        assertEquals(R.string.tuning_font_chakra_petch, CockpitFontFamily.CHAKRA_PETCH.getDisplayNameRes())
        assertEquals(R.string.tuning_font_oxanium, CockpitFontFamily.OXANIUM.getDisplayNameRes())
        assertEquals(R.string.tuning_font_rajdhani, CockpitFontFamily.RAJDHANI.getDisplayNameRes())
        assertEquals(R.string.tuning_font_montserrat, CockpitFontFamily.MONTSERRAT.getDisplayNameRes())

        for (family in CockpitFontFamily.values()) {
            assertTrue("Resource ID must be positive for $family", family.getDisplayNameRes() > 0)
        }
    }

    @Test
    fun cockpitFontWeight_getDisplayNameRes_returnsValidResourceIds() {
        assertEquals(R.string.tuning_weight_normal, CockpitFontWeight.NORMAL.getDisplayNameRes())
        assertEquals(R.string.tuning_weight_semi_bold, CockpitFontWeight.SEMI_BOLD.getDisplayNameRes())
        assertEquals(R.string.tuning_weight_bold, CockpitFontWeight.BOLD.getDisplayNameRes())

        for (weight in CockpitFontWeight.values()) {
            assertTrue("Resource ID must be positive for $weight", weight.getDisplayNameRes() > 0)
        }
    }

    @Test
    fun resolveConfig_createsCompleteConfig() {
        val config = CockpitTypography.resolveConfig(
            family = CockpitFontFamily.MONOSPACE,
            weight = CockpitFontWeight.BOLD
        )
        assertEquals(CockpitFontFamily.MONOSPACE, config.family)
        assertEquals(CockpitFontWeight.BOLD, config.weight)
        assertNotNull(config.resolvedFontFamily)
    }
}
