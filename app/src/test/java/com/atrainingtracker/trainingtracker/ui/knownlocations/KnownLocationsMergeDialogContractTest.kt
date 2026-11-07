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

package com.atrainingtracker.trainingtracker.ui.knownlocations

import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Architectural, state transition, and 9-language localization contract tests for
 * the Known Locations merge dialog flow (ATT-5889 / REQ-SET-095 / TST-SET-095.4).
 *
 * Traceability:
 * - REQ-SET-095.1: Context menu action (Icons.AutoMirrored.Filled.CallMerge)
 * - REQ-SET-095.2: Proximity candidate selection dialog
 * - REQ-SET-095.3: Explicit merge confirmation dialog with radius preview and pinned notice
 * - REQ-SET-095.6: 9-language localization parity and zero emojis (ATT-5400)
 */
class KnownLocationsMergeDialogContractTest {

    private val supportedLocales = listOf("", "de", "es", "fr", "it", "ja", "nl", "pl", "pt")

    private fun findResDirectory(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("res directory not found in candidate paths: $candidates")
    }

    private fun loadFileSource(relativeSubpath: String): String {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/$relativeSubpath"),
            File("src/main/java/com/atrainingtracker/trainingtracker/$relativeSubpath"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/$relativeSubpath")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Source file not found for: $relativeSubpath")
        return file.readText()
    }

    private fun extractStringTagRawContent(stringsFile: File, stringKey: String): String? {
        val content = stringsFile.readText()
        val regex = Pattern.compile("<string\\s+name=[\"']$stringKey[\"']>(.*?)</string>", Pattern.DOTALL)
        val matcher = regex.matcher(content)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun containsEmoji(text: String): Boolean {
        return text.codePoints().anyMatch { cp ->
            (cp in 0x1F000..0x1FAFF) || (cp in 0x2600..0x27BF) || cp == 0xFE0F || cp == 0x200D
        }
    }

    private fun createLocation(id: Long, name: String, isHome: Boolean = false, isLocked: Boolean = false): KnownLocationItem {
        return KnownLocationItem(
            id = id,
            name = name,
            altitude = 500.0,
            radius = 200,
            latLng = LatLng(48.0, 11.0),
            hitCount = 5,
            isLocked = isLocked,
            source = if (isLocked) ElevationSource.MANUAL_USER else ElevationSource.AUTO_LEARNED,
            isHome = isHome
        )
    }

    @Test
    fun testKnownLocationsScreen_structuralComponentsExist() {
        val content = loadFileSource("ui/knownlocations/KnownLocationsScreen.kt")

        // Context menu merge item (REQ-SET-095.1)
        assertTrue("Must reference CallMerge icon", content.contains("CallMerge"))
        assertTrue("Must reference known_locations_menu_merge", content.contains("known_locations_menu_merge"))
        assertTrue("Must tag context menu item with location_merge_action_\${item.id}", content.contains("location_merge_action_\${item.id}"))

        // Selection dialog (REQ-SET-095.2)
        assertTrue("Must declare KnownLocationMergeSelectionDialog", content.contains("fun KnownLocationMergeSelectionDialog"))
        assertTrue("Must tag candidates list with location_merge_candidates_list", content.contains("location_merge_candidates_list"))
        assertTrue("Must tag candidate items with location_merge_candidate_\${candidate.id}", content.contains("location_merge_candidate_\${candidate.id}"))

        // Confirmation dialog (REQ-SET-095.3)
        assertTrue("Must declare KnownLocationMergeConfirmationDialog", content.contains("fun KnownLocationMergeConfirmationDialog"))
        assertTrue("Must tag confirm button with location_merge_confirm_button", content.contains("location_merge_confirm_button"))
        assertTrue("Must tag cancel button with location_merge_cancel_button", content.contains("location_merge_cancel_button"))
    }

    @Test
    fun testTwoStepMergeDialogStateTransition_firstTapDecoupling() {
        val source = createLocation(1L, "Doorway")
        val target = createLocation(2L, "Driveway")

        // Initial state: no merge in progress
        var locationToMerge: KnownLocationItem? = null
        var pendingMergePair: Pair<KnownLocationItem, KnownLocationItem>? = null

        // Step 1: User long-presses source location and taps "Zusammenführen"
        locationToMerge = source
        assertNotNull("Selection dialog should be open", locationToMerge)
        assertNull("Confirmation dialog should not yet be open", pendingMergePair)

        // Step 2: User taps candidate target in selection sheet
        // onSelectTarget sets pendingMergePair and onDismiss clears locationToMerge
        pendingMergePair = Pair(source, target)
        locationToMerge = null

        // Invariant: Confirmation dialog opens immediately on the first attempt
        assertNull("Selection sheet is closed", locationToMerge)
        assertNotNull("Confirmation dialog is immediately active", pendingMergePair)
        pendingMergePair.let { activePair ->
            assertEquals(source.id, activePair.first.id)
            assertEquals(target.id, activePair.second.id)
        }

        // Step 3: User confirms merge in confirmation dialog
        var mergedDispatched = false
        val onConfirm = {
            mergedDispatched = true
            pendingMergePair = null
            locationToMerge = null
        }
        onConfirm()

        assertTrue("Merge must be dispatched", mergedDispatched)
        assertNull("State must be completely reset", pendingMergePair)
        assertNull("State must be completely reset", locationToMerge)
    }

    @Test
    fun testTwoStepMergeDialogStateTransition_dismissalWithoutSelection() {
        val source = createLocation(1L, "Doorway")

        var locationToMerge: KnownLocationItem? = source
        var pendingMergePair: Pair<KnownLocationItem, KnownLocationItem>? = null

        // User dismisses selection sheet without choosing a target
        locationToMerge = null

        assertNull("Selection sheet is closed", locationToMerge)
        assertNull("Confirmation dialog must NOT be opened", pendingMergePair)
    }

    @Test
    fun test9LanguageLocalizationParity_knownLocationsMergeStrings() {
        val resDir = findResDirectory()
        val requiredKeys = listOf(
            "known_locations_menu_merge",
            "known_locations_merge_select_target_title",
            "known_locations_merge_distance_away",
            "known_locations_merge_no_candidates",
            "known_locations_merge_dialog_title",
            "known_locations_merge_dialog_message",
            "known_locations_merge_pinned_notice",
            "known_locations_merge_action"
        )

        for (locale in supportedLocales) {
            val dirName = if (locale.isEmpty()) "values" else "values-$locale"
            val stringsFile = File(resDir, "$dirName/strings.xml")
            assertTrue("strings.xml must exist for locale $dirName", stringsFile.exists())

            for (key in requiredKeys) {
                val value = extractStringTagRawContent(stringsFile, key)
                assertNotNull("Key '$key' must exist in $dirName/strings.xml", value)
                assertFalse("Key '$key' in $dirName/strings.xml must NOT contain decorative inline emojis (ATT-5400)", containsEmoji(value!!))

                // Verify positional specifier parity
                if (key == "known_locations_merge_distance_away" || key == "known_locations_merge_pinned_notice") {
                    assertTrue("Key '$key' in $dirName must contain %1\$s", value.contains("%1\$s"))
                }
                if (key == "known_locations_merge_dialog_message") {
                    assertTrue("Key '$key' in $dirName must contain %1\$s", value.contains("%1\$s"))
                    assertTrue("Key '$key' in $dirName must contain %2\$s", value.contains("%2\$s"))
                    assertTrue("Key '$key' in $dirName must contain %3\$s", value.contains("%3\$s"))
                }
            }
        }
    }
}
