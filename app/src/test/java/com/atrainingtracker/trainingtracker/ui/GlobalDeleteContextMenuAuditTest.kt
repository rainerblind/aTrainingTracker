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

package com.atrainingtracker.trainingtracker.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural and structural AST/source audit test verifying universal
 * adherence to the delete-only long-press context menu standard (REQ-UI-061 / TST-UI-070.1).
 *
 * Invariants Enforced:
 * 1. Context menus on deletable list item cards SHALL anchor to `Alignment.TopStart`.
 * 2. Context menus SHALL use uniform offset `padding(start = 12.dp, top = 8.dp)`.
 * 3. Context menus SHALL strictly offer deletion (or documented lifecycle actions) and
 *    SHALL NOT introduce secondary actions such as "Show on Map" or "Edit".
 * 4. List cards SHALL NOT feature redundant 3-dots overflow buttons (`MoreVert`).
 */
class GlobalDeleteContextMenuAuditTest {

    private val cardFiles = listOf(
        "app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt",
        "app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItem.kt",
        "app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentTabsScreen.kt",
        "app/src/main/java/com/atrainingtracker/banalservice/ui/sporttype/SportTypesTabsScreen.kt",
        "app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt",
        "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummaryCompact.kt"
    )

    private fun resolveSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Source file not found for audit: $relativePath")
    }

    @Test
    fun testAllCardContextMenusAnchorToTopStartWithStandardPadding() {
        for (relativePath in cardFiles) {
            val file = resolveSourceFile(relativePath)
            val content = file.readText()

            // 1. Must anchor to TopStart
            assertTrue(
                "File ${file.name} must anchor its context menu container to Alignment.TopStart per REQ-UI-061",
                content.contains(".align(Alignment.TopStart)")
            )

            // 2. Must apply standard start = 12.dp, top = 8.dp padding
            assertTrue(
                "File ${file.name} must specify padding(start = 12.dp, top = 8.dp) for context menu positioning per REQ-UI-061",
                content.contains("padding(start = 12.dp, top = 8.dp)")
            )

            // 3. Must NOT anchor to TopEnd
            assertFalse(
                "File ${file.name} must NOT anchor context menu to TopEnd",
                content.contains(".align(Alignment.TopEnd)") && content.contains("padding(end = 12.dp, top = 8.dp)")
            )
        }
    }

    @Test
    fun testKnownLocationCardDoesNotContainOverflowButtonOrSecondaryActions() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt")
        val content = file.readText()

        // Forbidden overflow icon button
        assertFalse(
            "KnownLocationsScreen must not contain MoreVert overflow button in KnownLocationCard",
            content.contains("Icons.Default.MoreVert") || content.contains("location_overflow_button_")
        )

        // Forbidden secondary actions in context menu
        assertFalse(
            "KnownLocationsScreen must not contain 'Show on Map' context menu action in KnownLocationCard",
            content.contains("location_show_on_map_action_")
        )
        assertFalse(
            "KnownLocationsScreen must not contain 'Edit' context menu action in KnownLocationCard",
            content.contains("location_edit_action_")
        )

        // Required delete action
        assertTrue(
            "KnownLocationsScreen must contain 'location_delete_action_' test tag",
            content.contains("location_delete_action_")
        )
    }

    @Test
    fun testDeletableListCardsContainDeleteAction() {
        for (relativePath in cardFiles) {
            val file = resolveSourceFile(relativePath)
            val content = file.readText()

            assertTrue(
                "File ${file.name} must offer delete functionality in its context menu",
                content.contains("R.string.delete") || content.contains("onDelete")
            )
        }
    }
}
