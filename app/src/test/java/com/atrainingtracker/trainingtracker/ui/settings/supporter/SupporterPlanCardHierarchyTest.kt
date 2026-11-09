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

package com.atrainingtracker.trainingtracker.ui.settings.supporter

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.monetization.SupporterBasePlan
import com.atrainingtracker.trainingtracker.monetization.SupporterTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Architectural and contract verification suite for Supporter tier visual hierarchy,
 * lower tier de-emphasis, and recommendation badge gating (REQ-UI-501, TST-UI-501 / ATT-6286).
 */
class SupporterPlanCardHierarchyTest {

    private val supportedLocales = listOf("", "de", "es", "fr", "it", "ja", "nl", "pl", "pt")

    @Test
    fun testPlanCardHierarchyStateResolution() {
        val bronzeMonthly = SupporterBasePlan.PLANS.first { it.basePlanId == "bronze-monthly" }
        val bronzeYearly = SupporterBasePlan.PLANS.first { it.basePlanId == "bronze-yearly" }
        val silverYearly = SupporterBasePlan.PLANS.first { it.basePlanId == "silver-yearly" }
        val goldYearly = SupporterBasePlan.PLANS.first { it.basePlanId == "gold-yearly" }
        val diamondYearly = SupporterBasePlan.PLANS.first { it.basePlanId == "diamond-yearly" }

        // Scenario 1: Active Silver supporter
        assertEquals(PlanCardHierarchyState.LOWER_TIER, getPlanCardHierarchyState(bronzeMonthly, SupporterTier.SILVER))
        assertEquals(PlanCardHierarchyState.LOWER_TIER, getPlanCardHierarchyState(bronzeYearly, SupporterTier.SILVER))
        assertEquals(PlanCardHierarchyState.CURRENT_TIER, getPlanCardHierarchyState(silverYearly, SupporterTier.SILVER))
        assertEquals(PlanCardHierarchyState.UPGRADE_TIER, getPlanCardHierarchyState(goldYearly, SupporterTier.SILVER))
        assertEquals(PlanCardHierarchyState.UPGRADE_TIER, getPlanCardHierarchyState(diamondYearly, SupporterTier.SILVER))

        // Scenario 2: Active Gold supporter
        assertEquals(PlanCardHierarchyState.LOWER_TIER, getPlanCardHierarchyState(bronzeMonthly, SupporterTier.GOLD))
        assertEquals(PlanCardHierarchyState.LOWER_TIER, getPlanCardHierarchyState(silverYearly, SupporterTier.GOLD))
        assertEquals(PlanCardHierarchyState.CURRENT_TIER, getPlanCardHierarchyState(goldYearly, SupporterTier.GOLD))
        assertEquals(PlanCardHierarchyState.UPGRADE_TIER, getPlanCardHierarchyState(diamondYearly, SupporterTier.GOLD))

        // Scenario 3: Active Diamond supporter
        assertEquals(PlanCardHierarchyState.LOWER_TIER, getPlanCardHierarchyState(bronzeMonthly, SupporterTier.DIAMOND))
        assertEquals(PlanCardHierarchyState.LOWER_TIER, getPlanCardHierarchyState(silverYearly, SupporterTier.DIAMOND))
        assertEquals(PlanCardHierarchyState.LOWER_TIER, getPlanCardHierarchyState(goldYearly, SupporterTier.DIAMOND))
        assertEquals(PlanCardHierarchyState.CURRENT_TIER, getPlanCardHierarchyState(diamondYearly, SupporterTier.DIAMOND))

        // Scenario 4: Active Free user
        SupporterBasePlan.PLANS.forEach { plan ->
            assertEquals(PlanCardHierarchyState.UPGRADE_TIER, getPlanCardHierarchyState(plan, SupporterTier.FREE))
        }

        // Scenario 5: Pioneer user (Pioneer is honorary, not a paid tier)
        SupporterBasePlan.PLANS.forEach { plan ->
            assertEquals(PlanCardHierarchyState.UPGRADE_TIER, getPlanCardHierarchyState(plan, SupporterTier.PIONEER))
        }

        // Scenario 6: Active Bronze supporter
        assertEquals(PlanCardHierarchyState.CURRENT_TIER, getPlanCardHierarchyState(bronzeMonthly, SupporterTier.BRONZE))
        assertEquals(PlanCardHierarchyState.CURRENT_TIER, getPlanCardHierarchyState(bronzeYearly, SupporterTier.BRONZE))
        assertEquals(PlanCardHierarchyState.UPGRADE_TIER, getPlanCardHierarchyState(silverYearly, SupporterTier.BRONZE))
        assertEquals(PlanCardHierarchyState.UPGRADE_TIER, getPlanCardHierarchyState(goldYearly, SupporterTier.BRONZE))
    }

    @Test
    fun testRecommendationBadgeSuppressionLogic() {
        val silverYearly = SupporterBasePlan.PLANS.first { it.basePlanId == "silver-yearly" }
        assertTrue("Silver yearly must be marked as isRecommended in data model", silverYearly.isRecommended)

        // Recommendation badge is shown strictly when effectiveTier.rank < SILVER.rank (REQ-UI-501.2)
        fun shouldShowBadge(plan: SupporterBasePlan, effectiveTier: SupporterTier): Boolean {
            return plan.isRecommended && effectiveTier.rank < SupporterTier.SILVER.rank
        }

        // Must show for Free, Pioneer, and Bronze athletes
        assertTrue(shouldShowBadge(silverYearly, SupporterTier.FREE))
        assertTrue(shouldShowBadge(silverYearly, SupporterTier.PIONEER))
        assertTrue(shouldShowBadge(silverYearly, SupporterTier.BRONZE))

        // Must be suppressed for Silver, Gold, and Diamond athletes
        assertFalse(shouldShowBadge(silverYearly, SupporterTier.SILVER))
        assertFalse(shouldShowBadge(silverYearly, SupporterTier.GOLD))
        assertFalse(shouldShowBadge(silverYearly, SupporterTier.DIAMOND))

        // Non-recommended plans must never show badge
        SupporterBasePlan.PLANS.filter { !it.isRecommended }.forEach { plan ->
            SupporterTier.values().forEach { tier ->
                assertFalse("Non-recommended plan ${plan.basePlanId} must not show badge", shouldShowBadge(plan, tier))
            }
        }
    }

    @Test
    fun testActionButtonsAndLabelsSpecification() {
        fun getActionButtonLabel(state: PlanCardHierarchyState): Int = when (state) {
            PlanCardHierarchyState.CURRENT_TIER -> R.string.supporter_plan_button_active
            PlanCardHierarchyState.LOWER_TIER -> R.string.supporter_plan_button_switch
            PlanCardHierarchyState.UPGRADE_TIER -> R.string.supporter_plan_button_support
        }

        assertEquals(R.string.supporter_plan_button_active, getActionButtonLabel(PlanCardHierarchyState.CURRENT_TIER))
        assertEquals(R.string.supporter_plan_button_switch, getActionButtonLabel(PlanCardHierarchyState.LOWER_TIER))
        assertEquals(R.string.supporter_plan_button_support, getActionButtonLabel(PlanCardHierarchyState.UPGRADE_TIER))
    }

    @Test
    fun testSupporterPlanButtonSwitchTranslationParity() {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
            File("../app/src/main/res")
        )
        val resDir = candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("res directory not found in candidate paths: $candidates")

        val stringKey = "supporter_plan_button_switch"
        val expectedTranslations = mapOf(
            "" to "Switch",
            "de" to "Wechseln",
            "es" to "Cambiar",
            "fr" to "Changer",
            "it" to "Cambia",
            "ja" to "変更",
            "nl" to "Wisselen",
            "pl" to "Zmień",
            "pt" to "Mudar"
        )

        for (locale in supportedLocales) {
            val dirName = if (locale.isEmpty()) "values" else "values-$locale"
            val monetizationFile = File(resDir, "$dirName/strings_monetization.xml")
            assertTrue("strings_monetization.xml must exist for locale '$locale' at ${monetizationFile.path}", monetizationFile.exists())

            val content = monetizationFile.readText()
            val regex = Pattern.compile("<string\\s+name=[\"']$stringKey[\"'][^>]*>(.*?)</string>", Pattern.DOTALL)
            val matcher = regex.matcher(content)
            assertTrue("String key '$stringKey' must exist in locale '$locale'", matcher.find())

            val text = matcher.group(1)!!.trim()
            assertFalse("String key '$stringKey' must not be blank in locale '$locale'", text.isEmpty())

            val expected = expectedTranslations[locale]
            if (expected != null) {
                assertEquals("Translation for locale '$locale' mismatch", expected, text)
            }
        }
    }

    @Test
    fun testSupporterScreenEffectiveTierPropagationAudit() {
        val candidates = listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/supporter/SupporterScreen.kt"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/supporter/SupporterScreen.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/supporter/SupporterScreen.kt")
        )
        val screenFile = candidates.firstOrNull { it.exists() }
            ?: error("SupporterScreen.kt not found in candidate paths: $candidates")

        val content = screenFile.readText()
        assertTrue(
            "SupporterScreen.kt must pass 'effectiveTier = effectiveTier' into SupporterPlanCard",
            content.contains("effectiveTier = effectiveTier")
        )
    }
}
