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
            "athlete_profile_tab_profile",
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
            "athlete_profile_delete_message",
            "w_prime_bal",
            "w_prime_bal_short",
            "athlete_profile_metric_cp",
            "athlete_profile_metric_w_prime",
            "athlete_profile_cp_estimator_title",
            "athlete_profile_cp_estimator_short_power",
            "athlete_profile_cp_estimator_short_sec",
            "athlete_profile_cp_estimator_long_power",
            "athlete_profile_cp_estimator_long_sec",
            "athlete_profile_cp_estimator_apply",
            "workout_w_prime_bal_min_format",
            "workout_w_prime_empty_tank",
            "efficiency_factor_short",
            "aerobic_decoupling_short",
            "workout_ef_pwhr_format",
            "workout_ef_pahr_format",
            "workout_ef_only_pw_format",
            "workout_ef_only_pa_format",
            "workout_decoupling_coupled",
            "workout_decoupling_drift",
            "athlete_profile_pmc_title",
            "athlete_profile_pmc_ctl_label",
            "athlete_profile_pmc_atl_label",
            "athlete_profile_pmc_tsb_label",
            "athlete_profile_pmc_acwr_label",
            "athlete_profile_pmc_ramp_label",
            "athlete_profile_pmc_ramp_format",
            "athlete_profile_pmc_ramp_warning",
            "athlete_profile_pmc_zone_fresh",
            "athlete_profile_pmc_zone_neutral",
            "athlete_profile_pmc_zone_optimal",
            "athlete_profile_pmc_zone_fatigue",
            "athlete_profile_pmc_acwr_under",
            "athlete_profile_pmc_acwr_sweet_spot",
            "athlete_profile_pmc_acwr_caution",
            "athlete_profile_pmc_acwr_danger",
            "athlete_profile_pmc_empty"
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

            // Verify positional specifiers in workout_w_prime_bal_min_format
            val wPrimeFmtRegex = Regex("""<string name="workout_w_prime_bal_min_format">([^<]+)</string>""")
            val wPrimeMatch = wPrimeFmtRegex.find(content)
            assertTrue("workout_w_prime_bal_min_format not matched in $locale", wPrimeMatch != null)
            val wPrimeText = wPrimeMatch!!.groupValues[1]
            assertTrue("$locale workout_w_prime_bal_min_format must contain %1\$.1f", wPrimeText.contains("%1\$.1f"))
            assertTrue("$locale workout_w_prime_bal_min_format must contain %2\$.0f", wPrimeText.contains("%2\$.0f"))

            // Verify positional specifiers in workout_ef_pwhr_format and workout_ef_pahr_format
            for (efKey in listOf("workout_ef_pwhr_format", "workout_ef_pahr_format")) {
                val efRegex = Regex("""<string name="$efKey">([^<]+)</string>""")
                val efMatch = efRegex.find(content)
                assertTrue("$efKey not matched in $locale", efMatch != null)
                val efText = efMatch!!.groupValues[1]
                assertTrue("$locale $efKey must contain %1\$.2f", efText.contains("%1\$.2f"))
                assertTrue("$locale $efKey must contain %2\$.1f", efText.contains("%2\$.1f"))
            }

            // Verify positional specifier in athlete_profile_pmc_ramp_format
            val rampRegex = Regex("""<string name="athlete_profile_pmc_ramp_format">([^<]+)</string>""")
            val rampMatch = rampRegex.find(content)
            assertTrue("athlete_profile_pmc_ramp_format not matched in $locale", rampMatch != null)
            val rampText = rampMatch!!.groupValues[1]
            assertTrue("$locale athlete_profile_pmc_ramp_format must contain %1\$+.1f", rampText.contains("%1\$+.1f"))
        }
    }

    @Test
    fun `AthleteProfileScreen applies WindowInsets navigationBars to LazyColumn bottom contentPadding and hosts 4 Profile and Zones tabs (Rule 41, REQ-UI-397)`() {
        val screenFile = File(
            resolveModuleRoot(),
            "src/main/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileScreen.kt"
        )
        assertTrue("AthleteProfileScreen.kt must exist", screenFile.exists())
        val source = screenFile.readText()

        assertTrue(
            "AthleteProfileScreen.kt must import WindowInsets",
            source.contains("import androidx.compose.foundation.layout.WindowInsets")
        )
        assertTrue(
            "AthleteProfileScreen.kt must import asPaddingValues",
            source.contains("import androidx.compose.foundation.layout.asPaddingValues")
        )
        assertTrue(
            "AthleteProfileScreen.kt must import navigationBars",
            source.contains("import androidx.compose.foundation.layout.navigationBars")
        )
        assertTrue(
            "AthleteProfileContent must compute navBarBottomPadding from WindowInsets.navigationBars",
            source.contains("val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()")
        )
        assertTrue(
            "AthleteProfileContent LazyColumn must apply bottom = 16.dp + navBarBottomPadding in contentPadding (Rule 41)",
            source.contains("bottom = 16.dp + navBarBottomPadding")
        )
        assertTrue(
            "AthleteProfileContent header row must retain .statusBarsPadding()",
            source.contains(".statusBarsPadding()")
        )
        assertTrue(
            "AthleteProfileContent must host PrimaryScrollableTabRow and HorizontalPager for Profile & Zones tabs",
            source.contains("PrimaryScrollableTabRow(") && source.contains("HorizontalPager(")
        )
        assertTrue(
            "AthleteProfileContent must include all 4 tab titles (Profile, HR Run, HR Bike, Power Bike)",
            source.contains("R.string.athlete_profile_tab_profile") &&
                source.contains("R.string.tab_hr_run") &&
                source.contains("R.string.tab_hr_bike") &&
                source.contains("R.string.tab_pwr_bike")
        )
        assertTrue(
            "AthleteProfileContent must embed ZoneProfileTabContent for HR_RUN, HR_BIKE, and PWR_BIKE",
            source.contains("ZoneProfileTabContent(") &&
                source.contains("ZoneType.HR_RUN") &&
                source.contains("ZoneType.HR_BIKE") &&
                source.contains("ZoneType.PWR_BIKE")
        )
        assertTrue(
            "AthleteProfileScreen.kt must render PerformanceManagementChartCard and PerformanceManagementChartCanvas (REQ-UI-401.3)",
            source.contains("fun PerformanceManagementChartCard(") &&
                source.contains("fun PerformanceManagementChartCanvas(")
        )
    }
}


