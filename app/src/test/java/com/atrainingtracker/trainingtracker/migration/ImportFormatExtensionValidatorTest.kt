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

package com.atrainingtracker.trainingtracker.migration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying ImportFileValidator extension matching and fallback logic (ATT-2622 / REQ-UI-294 / TST-UI-254).
 */
class ImportFormatExtensionValidatorTest {

    @Test
    fun testIsMatchingFormat_withMatchingExtensions_returnsTrue() {
        assertTrue(ImportFileValidator.isMatchingFormat("activity.fit", "fit"))
        assertTrue(ImportFileValidator.isMatchingFormat("ACTIVITY.FIT", "fit"))
        assertTrue(ImportFileValidator.isMatchingFormat("workout.tcx", "tcx"))
        assertTrue(ImportFileValidator.isMatchingFormat("WORKOUT.TCX", "tcx"))
        assertTrue(ImportFileValidator.isMatchingFormat("track.gpx", "gpx"))
        assertTrue(ImportFileValidator.isMatchingFormat("TRACK.GPX", "gpx"))
    }

    @Test
    fun testIsMatchingFormat_withConflictingWorkoutExtensions_returnsFalse() {
        assertFalse(ImportFileValidator.isMatchingFormat("activity.fit", "tcx"))
        assertFalse(ImportFileValidator.isMatchingFormat("activity.fit", "gpx"))
        assertFalse(ImportFileValidator.isMatchingFormat("workout.tcx", "fit"))
        assertFalse(ImportFileValidator.isMatchingFormat("workout.tcx", "gpx"))
        assertFalse(ImportFileValidator.isMatchingFormat("track.gpx", "fit"))
        assertFalse(ImportFileValidator.isMatchingFormat("track.gpx", "tcx"))
    }

    @Test
    fun testIsMatchingFormat_withUnrelatedDocumentExtensions_returnsFalse() {
        assertFalse(ImportFileValidator.isMatchingFormat("document.pdf", "fit"))
        assertFalse(ImportFileValidator.isMatchingFormat("photo.jpg", "tcx"))
        assertFalse(ImportFileValidator.isMatchingFormat("archive.zip", "gpx"))
        assertFalse(ImportFileValidator.isMatchingFormat("notes.txt", "fit"))
    }

    @Test
    fun testIsMatchingFormat_whenFileNameNullOrBlankOrNoExtension_returnsTrueForStreamInspectionFallback() {
        assertTrue(ImportFileValidator.isMatchingFormat(null, "fit"))
        assertTrue(ImportFileValidator.isMatchingFormat("", "tcx"))
        assertTrue(ImportFileValidator.isMatchingFormat("   ", "gpx"))
        assertTrue(ImportFileValidator.isMatchingFormat("opaque_content_provider_id", "fit"))
        assertTrue(ImportFileValidator.isMatchingFormat("trailing_dot.", "tcx"))
    }
}
