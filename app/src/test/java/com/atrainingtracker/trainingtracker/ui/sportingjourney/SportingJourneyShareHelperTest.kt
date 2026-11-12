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

package com.atrainingtracker.trainingtracker.ui.sportingjourney

import android.content.Context
import com.atrainingtracker.trainingtracker.monetization.SupporterTier
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit verification suite for [SportingJourneyShareHelper] (REQ-SPT-004, TST-SPT-004).
 */
class SportingJourneyShareHelperTest {

    @Test
    fun testBuildShareText_containsPassportData() {
        val mockContext: Context = mockk(relaxed = true)

        val totals = SportingHeroTotals(
            totalDistanceKm = 20529.0,
            equatorPercentage = 51.2,
            totalWorkouts = 1777,
            totalActiveHours = 1322.0,
            nonstopDays = 55.1,
            totalElevationM = 88450,
            everestMultiplier = 10.0,
            favoriteRoutesCount = 78
        )

        val gems = SportingHiddenGems(
            pedalRevolutions = 1408926L,
            cadenceTrackedRidesCount = 679,
            estimatedHeartbeats = 9980000L,
            hrTrackedWorkoutsCount = 1200,
            mechanicalKwh = 67.6,
            mechanicalKj = 243207.0,
            powerTrackedWorkoutsCount = 652,
            ebikeKmEquivalent = 6760.0
        )

        val achievements = listOf(
            SportingAchievement("c", "🌲", "Century", "", true, "", ""),
            SportingAchievement("e", "🏔️", "Everest", "", true, "", ""),
            SportingAchievement("w", "🔒", "World", "", false, "", "")
        )

        val data = SportingJourneyData(
            horizon = SportingHorizon.AllTime,
            sportFilter = SportingSportFilter.ALL,
            heroTotals = totals,
            hiddenGems = gems,
            achievements = achievements,
            athleteName = "Rainer"
        )

        val shareText = SportingJourneyShareHelper.buildShareText(mockContext, data, SupporterTier.GOLD)

        assertTrue(shareText.contains("Official Athlete Passport"))
        assertTrue(shareText.contains("GOLD SPONSOR"))
        assertTrue(shareText.contains("20,529.0 km"))
        assertTrue(shareText.contains("51.2% of Equator"))
        assertTrue(shareText.contains("88,450 m"))
        assertTrue(shareText.contains("10.00× Mt. Everest"))
        assertTrue(shareText.contains("1,408,926"))
        assertTrue(shareText.contains("67.6 kWh"))
        assertTrue(shareText.contains("2 / 3 Unlocked"))
        assertTrue(shareText.contains("Recorded 100% offline with aTrainingTracker"))
    }
}
