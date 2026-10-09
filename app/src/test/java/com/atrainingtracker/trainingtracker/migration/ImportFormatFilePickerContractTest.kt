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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract tests asserting format-specific MIME arrays and document picker bindings (ATT-2622 / REQ-UI-294 / TST-UI-254).
 */
class ImportFormatFilePickerContractTest {

    @Test
    fun testFitMimeTypes_containsTargetedMimeSignatures() {
        val expected = arrayOf("application/vnd.ant.fit", "application/fit")
        assertArrayEquals(expected, FIT_MIME_TYPES)
        assertTrue(FIT_MIME_TYPES.none { it == "application/octet-stream" })
    }

    @Test
    fun testTcxMimeTypes_containsTargetedMimeSignatures() {
        val expected = arrayOf("application/vnd.garmin.tcx+xml", "application/xml", "text/xml")
        assertArrayEquals(expected, TCX_MIME_TYPES)
        assertTrue(TCX_MIME_TYPES.none { it == "application/octet-stream" })
    }

    @Test
    fun testGpxMimeTypes_containsTargetedMimeSignatures() {
        val expected = arrayOf("application/gpx+xml", "application/xml", "text/xml")
        assertArrayEquals(expected, GPX_MIME_TYPES)
        assertTrue(GPX_MIME_TYPES.none { it == "application/octet-stream" })
    }

    @Test
    fun testImportBackupTabsScreen_hasNoGenericWildcardLocalLaunches() {
        val screenFile = File("src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt")
        assertTrue("ImportBackupTabsScreen.kt must exist", screenFile.exists())
        val content = screenFile.readText()

        // Verify that local format triggers use format-specific MIME arrays
        assertTrue(content.contains("pickFitFilesLauncher.launch(FIT_MIME_TYPES)"))
        assertTrue(content.contains("pickLegacyFileLauncher.launch(TCX_MIME_TYPES)"))
        assertTrue(content.contains("pickLegacyFileLauncher.launch(GPX_MIME_TYPES)"))

        // Assert defensive validation integration
        assertTrue(content.contains("ImportFileValidator.isMatchingFormat"))
        assertTrue(content.contains("invalid_workout_file_format"))
    }
}
