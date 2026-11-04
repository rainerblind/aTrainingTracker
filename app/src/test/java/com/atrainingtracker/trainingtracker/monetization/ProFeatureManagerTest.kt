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

package com.atrainingtracker.trainingtracker.monetization

import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying ProFeatureManager tier resolution hierarchy, Pioneer grandfathering,
 * Free tier device quota checks, and debug override invariants (REQ-SET-085, REQ-SET-086, REQ-SET-087).
 */
class ProFeatureManagerTest {

    @Test
    fun testFreeTierDefaultResolution() {
        val tier = ProFeatureManager.resolveEffectiveTier(
            override = SupporterTierOverride.AUTO,
            isPioneer = false,
            subscriptionTier = SupporterTier.FREE,
            isDebug = false
        )
        assertEquals(SupporterTier.FREE, tier)
        assertFalse(tier.isPro)
    }

    @Test
    fun testPioneerGrandfatheringGivesPioneerTier() {
        val tier = ProFeatureManager.resolveEffectiveTier(
            override = SupporterTierOverride.AUTO,
            isPioneer = true,
            subscriptionTier = SupporterTier.FREE,
            isDebug = false
        )
        assertEquals(SupporterTier.PIONEER, tier)
        assertTrue(tier.isPro)
    }

    @Test
    fun testPioneerWithHigherSubscriptionUpgradesToHigherTier() {
        val tier = ProFeatureManager.resolveEffectiveTier(
            override = SupporterTierOverride.AUTO,
            isPioneer = true,
            subscriptionTier = SupporterTier.GOLD,
            isDebug = false
        )
        assertEquals(SupporterTier.GOLD, tier)
        assertTrue(tier.isPro)
    }

    @Test
    fun testDebugOverrideTakesPrecedenceInDebugMode() {
        val tier = ProFeatureManager.resolveEffectiveTier(
            override = SupporterTierOverride.FORCE_DIAMOND,
            isPioneer = false,
            subscriptionTier = SupporterTier.FREE,
            isDebug = true
        )
        assertEquals(SupporterTier.DIAMOND, tier)
        assertTrue(tier.isPro)

        val forcedFree = ProFeatureManager.resolveEffectiveTier(
            override = SupporterTierOverride.FORCE_FREE,
            isPioneer = true,
            subscriptionTier = SupporterTier.GOLD,
            isDebug = true
        )
        assertEquals(SupporterTier.FREE, forcedFree)
        assertFalse(forcedFree.isPro)
    }

    @Test
    fun testDebugOverrideIgnoredInReleaseBuilds() {
        val tier = ProFeatureManager.resolveEffectiveTier(
            override = SupporterTierOverride.FORCE_DIAMOND,
            isPioneer = false,
            subscriptionTier = SupporterTier.FREE,
            isDebug = false // Release build
        )
        assertEquals(SupporterTier.FREE, tier)
        assertFalse(tier.isPro)
    }

    @Test
    fun testDevicePairingAllowedQuotaRules() {
        // Free tier
        assertFalse(SupporterTier.FREE.isPro)
        val freeQuotaLimit = DevicesDatabaseManager.MAX_FREE_PAIRED_DEVICES // 2

        // When 0 or 1 device paired, Free user can pair
        assertTrue(0 < freeQuotaLimit)
        assertTrue(1 < freeQuotaLimit)

        // When 2 devices already paired, Free user cannot pair 3rd device
        assertFalse(2 < freeQuotaLimit)
        assertFalse(3 < freeQuotaLimit)

        // Pro tiers (Pioneer, Bronze, Silver, Gold, Diamond)
        listOf(
            SupporterTier.PIONEER,
            SupporterTier.BRONZE,
            SupporterTier.SILVER,
            SupporterTier.GOLD,
            SupporterTier.DIAMOND
        ).forEach { proTier ->
            assertTrue(proTier.isPro)
        }
    }
}
