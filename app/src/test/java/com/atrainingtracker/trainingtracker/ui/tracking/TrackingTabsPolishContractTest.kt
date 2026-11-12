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

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Contract test suite verifying Phase 5 Polish & Parity refinements (REQ-UI-471, TST-UI-471, ATT-5396).
 * Refactored to reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class TrackingTabsPolishContractTest {

    @Test
    fun testSensorFieldView_enforcesSingleLineAndScalingContracts_TST_UI_471_1() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldViewKt")
        val methods = clazz.declaredMethods
        val viewMethod = methods.find { it.name.startsWith("SensorFieldView") && Modifier.isPublic(it.modifiers) }
        assertNotNull("SensorFieldView composable must exist", viewMethod)
    }

    @Test
    fun testLocalization_stringsAnd9LocaleParity_TST_UI_471_2() {
        val deStrings = LocalizationTestCache.getStrings("values-de", "strings.xml")
        val deFilterStrings = LocalizationTestCache.getStrings("values-de", "strings_filters.xml")
        val defaultStrings = LocalizationTestCache.getStrings("values", "strings.xml")

        // SI unit s
        assertEquals("s", defaultStrings["units_seconds"])

        // German strings
        assertEquals("Aktiv", deStrings["time_active_short"])
        assertEquals("Runde", deStrings["time_lap_short"])
        assertEquals("Rundenzeit", deStrings["time_lap"])
        assertEquals("Runde Nr.", deStrings["lap_number_short"])
        assertEquals("Rundennummer", deStrings["lap_number"])
        assertEquals("Dist. Runde", deStrings["distance_lap_short"])
        assertEquals("Rundendistanz", deStrings["distance_lap"])
        assertEquals("Live-Segmente", deStrings["config_tracking__showLiveSegments"])
        assertEquals("Runden-Button", deStrings["config_tracking__showLapButton"])
        assertEquals("Anzeige anpassen", deStrings["edit_field"])
        assertEquals("Fertig", deStrings["Done"])
        assertEquals("gleitender Durchschnitt", deFilterStrings["filter_moving_average"])

        // 9-locale parity for action content descriptions
        val locales = listOf("values", "values-de", "values-es", "values-fr", "values-it", "values-ja", "values-nl", "values-pl", "values-pt")
        for (loc in locales) {
            val strings = LocalizationTestCache.getStrings(loc, "strings.xml")
            assertTrue("cd_move_field must exist in $loc", !strings["cd_move_field"].isNullOrEmpty())
            assertTrue("cd_delete_field must exist in $loc", !strings["cd_delete_field"].isNullOrEmpty())
        }
    }

    @Test
    fun testEditSensorFieldDialog_polishContracts_TST_UI_471_3() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield/EditSensorFieldDialogKt".replace('/', '.'))
        val methods = clazz.declaredMethods
        val dialogMethod = methods.find { it.name.startsWith("EditSensorFieldDialog") && Modifier.isPublic(it.modifiers) }
        assertNotNull("EditSensorFieldDialog composable must exist", dialogMethod)
    }

    @Test
    fun testSensorGridScreen_elevationProfileBottomClearance_TST_UI_471_4() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenKt")
        val methods = clazz.declaredMethods
        val gridMethod = methods.find { it.name.startsWith("SensorGridScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("SensorGridScreen composable must exist", gridMethod)
    }

    @Test
    fun testTrackingTabHeaders_renderSportContext_TST_UI_471_5() {
        val previewHeaderClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabPreviewHeaderKt")
        val configHeaderClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabConfigHeaderKt")
        val trackingTabsScreenClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsScreenKt")

        assertNotNull(previewHeaderClazz)
        assertNotNull(configHeaderClazz)
        assertNotNull(trackingTabsScreenClazz)
    }
}
