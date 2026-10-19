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

package com.atrainingtracker.trainingtracker.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract test verifying that GenericFileProvider and overly broad FileProvider
 * root directory exposures remain completely excised from the codebase (REQ-DAT-032.4, TST-DAT-027).
 */
class FileProviderHardeningContractTest {

    @Test
    fun testGenericFileProviderClassDeleted() {
        val classFile = File("app/src/main/java/com/atrainingtracker/trainingtracker/GenericFileProvider.java").let {
            if (it.exists()) it else File("src/main/java/com/atrainingtracker/trainingtracker/GenericFileProvider.java")
        }
        assertFalse("GenericFileProvider.java must be deleted", classFile.exists())
    }

    @Test
    fun testProviderPathsXmlDeleted() {
        val xmlFile = File("app/src/main/res/xml/provider_paths.xml").let {
            if (it.exists()) it else File("src/main/res/xml/provider_paths.xml")
        }
        assertFalse("provider_paths.xml must be deleted", xmlFile.exists())
    }

    @Test
    fun testAndroidManifestDoesNotDeclareGenericFileProvider() {
        val manifest = File("app/src/main/AndroidManifest.xml").let {
            if (it.exists()) it else File("src/main/AndroidManifest.xml")
        }
        assertTrue("AndroidManifest.xml must exist", manifest.exists())
        val text = manifest.readText()
        assertFalse("Manifest must not declare GenericFileProvider", text.contains("GenericFileProvider"))
        assertFalse("Manifest must not reference provider_paths", text.contains("provider_paths"))
    }

    @Test
    fun testFilePathsXmlDoesNotExposeRootDirectories() {
        val filePathsXml = File("app/src/main/res/xml/file_paths.xml").let {
            if (it.exists()) it else File("src/main/res/xml/file_paths.xml")
        }
        assertTrue("file_paths.xml must exist", filePathsXml.exists())
        val text = filePathsXml.readText()

        // Root path="." must not be used on cache-path or files-path or external-path
        assertFalse("file_paths.xml must not expose cache root path=\".\"", text.contains("path=\".\""))
        assertFalse("file_paths.xml must not expose cache root path=\"\"", text.contains("path=\"\""))
    }
}
