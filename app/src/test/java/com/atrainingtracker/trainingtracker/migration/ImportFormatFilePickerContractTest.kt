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

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests asserting format-specific MIME arrays and document picker bindings (ATT-2622 / REQ-UI-294 / TST-UI-254).
 */
class ImportFormatFilePickerContractTest {

    @Test
    fun testFitMimeTypes_containsTargetedMimeSignatures() {
        val expected = arrayOf("application/vnd.ant.fit", "application/fit", "application/octet-stream")
        assertArrayEquals(expected, FIT_MIME_TYPES)
        assertTrue(FIT_MIME_TYPES.contains("application/octet-stream"))
    }

    @Test
    fun testTcxMimeTypes_containsTargetedMimeSignatures() {
        val expected = arrayOf("application/vnd.garmin.tcx+xml", "application/xml", "text/xml", "application/octet-stream")
        assertArrayEquals(expected, TCX_MIME_TYPES)
        assertTrue(TCX_MIME_TYPES.contains("application/octet-stream"))
    }

    @Test
    fun testGpxMimeTypes_containsTargetedMimeSignatures() {
        val expected = arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream")
        assertArrayEquals(expected, GPX_MIME_TYPES)
        assertTrue(GPX_MIME_TYPES.contains("application/octet-stream"))
    }

    @Test
    fun testImportBackupTabsScreen_structureAndContract() {
        val screenClass = Class.forName("com.atrainingtracker.trainingtracker.migration.ImportBackupTabsScreenKt")
        assertNotNull("ImportBackupTabsScreenKt must exist", screenClass)

        val validatorClass = Class.forName("com.atrainingtracker.trainingtracker.migration.ImportFileValidator")
        assertNotNull("ImportFileValidator must exist", validatorClass)

        val strings = LocalizationTestCache.getStrings("values")
        assertTrue(strings.containsKey("invalid_workout_file_format"))
    }
}
