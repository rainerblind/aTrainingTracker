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
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.header

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Visual and architectural contract test verifying Material 3 Race badge enforcement in WorkoutHeader (REQ-UI-236, TST-UI-195.3).
 * Enforces:
 * 1. Conditional display based on `data.race`.
 * 2. Athletic-grade Material 3 vector styling using Icons.Filled.EmojiEvents.
 * 3. Strict zero-tolerance for raw unicode emojis.
 * 4. Tonal container color tokens (tertiaryContainer, onTertiaryContainer).
 * 5. String localization via R.string.race_badge.
 */
class WorkoutHeaderRaceBadgeVisualContractTest {

    private val headerSourceFile: File by lazy {
        listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt")
        ).firstOrNull { it.exists() } ?: File("src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt")
    }

    @Test
    fun testWorkoutHeaderSourceExists() {
        assertTrue("WorkoutHeader.kt source file must exist", headerSourceFile.exists())
    }

    @Test
    fun testRaceBadge_isGatedByDataRace() {
        val content = headerSourceFile.readText()
        assertTrue("Race badge must be conditionally rendered when data.race is true",
            content.contains("if (data.race)") || content.contains("if (headerData.race)"))
    }

    @Test
    fun testRaceBadge_usesVectorTrophyIcon() {
        val content = headerSourceFile.readText()
        assertTrue("Race badge must use Icons.Filled.EmojiEvents",
            content.contains("EmojiEvents"))
    }

    @Test
    fun testRaceBadge_strictlyZeroRawEmojis() {
        val content = headerSourceFile.readText()
        // Check for raw trophy or race flag unicode emojis
        val rawEmojiRegex = Regex("\uD83C\uDFC6|\uD83C\uDFC1|🏆|🏁")
        assertFalse("WorkoutHeader.kt must contain zero raw emojis in badge presentation",
            rawEmojiRegex.containsMatchIn(content))
    }

    @Test
    fun testRaceBadge_usesM3TonalTokens() {
        val content = headerSourceFile.readText()
        assertTrue("Race badge must use tertiaryContainer color",
            content.contains("tertiaryContainer"))
        assertTrue("Race badge must use onTertiaryContainer content color",
            content.contains("onTertiaryContainer"))
    }

    @Test
    fun testRaceBadge_usesLocalizedResource() {
        val content = headerSourceFile.readText()
        assertTrue("Race badge must use R.string.race_badge",
            content.contains("R.string.race_badge"))
    }
}
