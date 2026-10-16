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

package com.atrainingtracker.trainingtracker.ui.athleteprofile

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.navigation.NavRoutes
import com.atrainingtracker.trainingtracker.ui.navigation.createDrawerGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract & 9-Language Localization Parity tests for `ATT-3704` (`REQ-UI-391.1`, `REQ-UI-391.5`, `TST-UI-354.3`).
 */
class AthleteProfileContractAndLocalizationTest {

    private fun resolveModuleRoot(): File {
        val userDir = File(System.getProperty("user.dir") ?: ".")
        return if (File(userDir, "src/main/res").exists()) {
            userDir
        } else {
            File(userDir, "app")
        }
    }

    @Test
    fun `NavRoutes and AppNavigationDrawer map drawer_athlete_profile bidirectionally`() {
        assertEquals(NavRoutes.ATHLETE_PROFILE, NavRoutes.fromDrawerItemId(R.id.drawer_athlete_profile))
        assertEquals(R.id.drawer_athlete_profile, NavRoutes.toDrawerItemId(NavRoutes.ATHLETE_PROFILE))

        val groups = createDrawerGroups(R.string.tab_start)
        val myStuffGroup = groups.first { it.titleRes == R.string.drawer__my_stuff }
        assertTrue(
            "drawer__my_stuff group must contain drawer_athlete_profile",
            myStuffGroup.items.any {
                it.id == R.id.drawer_athlete_profile &&
                    it.iconRes == R.drawable.ic_athlete_profile &&
                    it.titleRes == R.string.drawer_athlete_profile
            }
        )
    }

    @Test
    fun `ic_athlete_profile vector drawable exists and uses true curved geometric primitives (Rule 37)`() {
        val drawableFile = File(resolveModuleRoot(), "src/main/res/drawable/ic_athlete_profile.xml")
        assertTrue("ic_athlete_profile.xml must exist", drawableFile.exists())
        val xml = drawableFile.readText()
        assertTrue("ic_athlete_profile.xml must be a <vector>", xml.contains("<vector"))
        assertTrue(
            "ic_athlete_profile.xml must use curved SVG primitives (A or C) per Rule 37",
            Regex("""[ACac]\s*[\d.-]""").containsMatchIn(xml)
        )
    }

    @Test
    fun `all 9 locales contain Athlete Profile strings with matching positional format specifiers`() {
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
            "drawer_athlete_profile",
            "athlete_profile_title",
            "athlete_profile_add_entry",
            "athlete_profile_edit_entry",
            "athlete_profile_metric_weight",
            "athlete_profile_metric_hr_rest",
            "athlete_profile_metric_ftp",
            "athlete_profile_metric_w_per_kg",
            "athlete_profile_chart_title",
            "athlete_profile_range_1m",
            "athlete_profile_range_3m",
            "athlete_profile_range_6m",
            "athlete_profile_range_1y",
            "athlete_profile_range_all",
            "athlete_profile_history_title",
            "athlete_profile_filter_all",
            "athlete_profile_empty_chart",
            "athlete_profile_empty_history",
            "athlete_profile_date_label",
            "athlete_profile_value_label",
            "athlete_profile_delete_title",
            "athlete_profile_delete_message"
        )

        val root = resolveModuleRoot()
        for (locale in locales) {
            val file = File(root, "src/main/res/$locale/strings.xml")
            assertTrue("Missing strings.xml for $locale", file.exists())
            val content = file.readText()
            for (key in requiredKeys) {
                assertTrue(
                    "Missing string key '$key' in $locale/strings.xml",
                    content.contains("""name="$key"""")
                )
            }
            // Verify positional specifiers in athlete_profile_delete_message
            val deleteMsgRegex = Regex("""<string name="athlete_profile_delete_message">([^<]+)</string>""")
            val match = deleteMsgRegex.find(content)
            assertTrue("athlete_profile_delete_message not matched in $locale", match != null)
            val text = match!!.groupValues[1]
            assertTrue("$locale athlete_profile_delete_message must contain %1\$s", text.contains("%1\$s"))
            assertTrue("$locale athlete_profile_delete_message must contain %2\$s", text.contains("%2\$s"))
            assertTrue("$locale athlete_profile_delete_message must contain %3\$s", text.contains("%3\$s"))
        }
    }
}
