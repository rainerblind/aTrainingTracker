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

package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural UI layout contract test verifying `EditWorkoutScreen.kt`
 * classification toggle tiles (Variant B) and grouped Cloud Sync card
 * pursuant to REQ-REV-013.1, REQ-REV-013.3 and TST-REV-014.2 (ATT-7517).
 */
class EditWorkoutClassificationLayoutContractTest {

    private fun loadEditWorkoutScreenSource(): String {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt")
        )
        val file = candidates.find { it.exists() }
            ?: throw IllegalStateException("EditWorkoutScreen.kt source file not found")
        return file.readText()
    }

    @Test
    fun testClassificationTagTiles_useThreeEqualWidthTilesWithToggleableSemantics() {
        val source = loadEditWorkoutScreenSource()

        // 1. Must use equal-width weight(1f) tiles
        assertTrue(
            "Source must contain weight(1f) for classification tiles",
            source.contains(".weight(1f)")
        )

        // 2. Must enforce minHeight 42.dp
        assertTrue(
            "Source must specify minHeight 42.dp for thumb targets",
            source.contains("defaultMinSize(minHeight = 42.dp)")
        )

        // 3. Must use toggleable with Role.Checkbox
        assertTrue(
            "Source must apply toggleable modifier with Role.Checkbox",
            source.contains("role = Role.Checkbox")
        )

        // 4. Must reference Icons.Default.Commute, Icons.Default.Home, and Icons.Filled.EmojiEvents
        assertTrue("Source must use Icons.Default.Commute", source.contains("Icons.Default.Commute"))
        assertTrue("Source must use Icons.Default.Home", source.contains("Icons.Default.Home"))
        assertTrue("Source must use Icons.Filled.EmojiEvents", source.contains("Icons.Filled.EmojiEvents"))

        // 5. Must use concise indoor_short string resource
        assertTrue(
            "Source must reference R.string.indoor_short instead of multi-word trainer string",
            source.contains("R.string.indoor_short")
        )

        // 6. Must style Race tile with tertiaryContainer / tertiary border
        assertTrue(
            "Source must style active Race tile with tertiaryContainer",
            source.contains("MaterialTheme.colorScheme.tertiaryContainer")
        )
        assertTrue(
            "Source must style active Race tile with tertiary border",
            source.contains("MaterialTheme.colorScheme.tertiary")
        )
    }

    @Test
    fun testGroupedCloudSyncCard_groupsStravaAndHealthConnectWithBrandAssets() {
        val source = loadEditWorkoutScreenSource()

        // 1. Grouped in a 10.dp rounded container
        assertTrue(
            "Cloud sync container must use RoundedCornerShape(10.dp)",
            source.contains("RoundedCornerShape(10.dp)")
        )

        // 2. Official brand assets used
        assertTrue(
            "Source must use logo_square_strava for Strava sync",
            source.contains("R.drawable.logo_square_strava")
        )
        assertTrue(
            "Source must use ic_health_connect for Health Connect sync",
            source.contains("R.drawable.ic_health_connect")
        )

        // 3. Disabled Strava alpha 0.38f preserved
        assertTrue(
            "Source must preserve 0.38f alpha when Strava mapping is unavailable",
            source.contains("0.38f")
        )

        // 4. Checkboxes in sync card have onCheckedChange = null to prevent double toggle
        assertTrue(
            "Trailing checkboxes in full-row toggleable cards must set onCheckedChange = null",
            source.contains("onCheckedChange = null")
        )
    }
}
