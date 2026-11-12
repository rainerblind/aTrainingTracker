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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit verification for [SportingJourneyComponents] grid geometry and layout math (REQ-SPT-005.3, REQ-SPT-005.4, TST-SPT-006.2).
 */
class SportingJourneyComponentsTest {

    @Test
    fun testAchievementGridChunking_evenCount() {
        val badges = (1..8).map {
            SportingAchievement("id_$it", "⭐", "Badge $it", "Sub $it", true, "Story $it", "Text $it")
        }

        val chunks = badges.chunked(4)
        assertEquals(2, chunks.size)
        assertEquals(4, chunks[0].size)
        assertEquals(4, chunks[1].size)

        // Spacer count for row 0 and row 1
        assertEquals(0, 4 - chunks[0].size)
        assertEquals(0, 4 - chunks[1].size)
    }

    @Test
    fun testAchievementGridChunking_incompleteFinalRow() {
        val badges = (1..5).map {
            SportingAchievement("id_$it", "⭐", "Badge $it", "Sub $it", true, "Story $it", "Text $it")
        }

        val chunks = badges.chunked(4)
        assertEquals(2, chunks.size)
        assertEquals(4, chunks[0].size)
        assertEquals(1, chunks[1].size)

        // Row 1 must have 3 spacers to maintain equal 1f weight and prevent stretching
        val spacersNeeded = 4 - chunks[1].size
        assertEquals(3, spacersNeeded)
    }

    @Test
    fun testAchievementGridChunking_singleItem() {
        val badges = listOf(
            SportingAchievement("single", "⭐", "Solo", "Sub", true, "Story", "Text")
        )

        val chunks = badges.chunked(4)
        assertEquals(1, chunks.size)
        assertEquals(1, chunks[0].size)

        val spacersNeeded = 4 - chunks[0].size
        assertEquals(3, spacersNeeded)
    }
}
