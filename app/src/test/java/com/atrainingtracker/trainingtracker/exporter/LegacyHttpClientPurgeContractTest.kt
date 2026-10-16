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

package com.atrainingtracker.trainingtracker.exporter

import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract tests verifying the complete excision of obsolete Runkeeper and TrainingPeaks classes,
 * removal of manifest entries, and elimination of Apache HttpClient dependencies (ATT-3551, REQ-EXP-020, TST-EXP-017).
 */
class LegacyHttpClientPurgeContractTest {

    private fun findProjectRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            if (File(dir, "app/build.gradle").exists()) {
                return dir
            }
            dir = dir.parentFile
        }
        return File(".")
    }

    @Test
    fun verifyNoApacheHttpImportsInMainSource() {
        val root = findProjectRoot()
        val mainJavaDir = File(root, "app/src/main/java")
        assertTrue("Main java directory must exist: ${mainJavaDir.absolutePath}", mainJavaDir.exists())

        val violatingFiles = mutableListOf<String>()
        mainJavaDir.walkTopDown()
            .filter { it.isFile && (it.extension == "java" || it.extension == "kt") }
            .forEach { file ->
                file.useLines { lines ->
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.startsWith("import org.apache.http")) {
                            violatingFiles.add("${file.relativeTo(root).path}: $trimmed")
                        }
                    }
                }
            }

        assertTrue(
            "Found forbidden org.apache.http imports in production source files:\n${violatingFiles.joinToString("\n")}",
            violatingFiles.isEmpty()
        )
    }

    @Test
    fun verifyNoApacheHttpDependenciesInBuildGradle() {
        val root = findProjectRoot()
        val buildGradle = File(root, "app/build.gradle")
        assertTrue("app/build.gradle must exist", buildGradle.exists())

        val content = buildGradle.readText()
        assertFalse(
            "app/build.gradle must not contain org.apache.httpcomponents dependencies",
            content.contains("org.apache.httpcomponents")
        )
    }

    @Test
    fun verifyNoApacheHttpInAndroidManifest() {
        val root = findProjectRoot()
        val manifest = File(root, "app/src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifest.exists())

        val content = manifest.readText()
        assertFalse(
            "AndroidManifest.xml must not declare org.apache.http.legacy",
            content.contains("org.apache.http.legacy")
        )
    }

    @Test
    fun verifyDeadActivitiesRemovedFromManifest() {
        val root = findProjectRoot()
        val manifest = File(root, "app/src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifest.exists())

        val content = manifest.readText()
        assertFalse(
            "AndroidManifest.xml must not declare RunkeeperGetAccessTokenActivity",
            content.contains("RunkeeperGetAccessTokenActivity")
        )
        assertFalse(
            "AndroidManifest.xml must not declare TrainingpeaksGetAccessTokenActivity",
            content.contains("TrainingpeaksGetAccessTokenActivity")
        )
    }

    @Test
    fun verifyDeadClassesRemovedFromDisk() {
        val root = findProjectRoot()
        val deadPaths = listOf(
            "app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/runkeeper/RunkeeperGetAccessTokenActivity.java",
            "app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/runkeeper/RunkeeperTestActivity.java",
            "app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/trainingpeaks/TrainingpeaksGetAccessTokenActivity.java",
            "app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/BaseGetAccessTokenActivity.java",
            "app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/RunkeeperUploader.java",
            "app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/TrainingPeaksUploader.java",
            "app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/RunkeeperFileWriter.java"
        )

        val existingFiles = deadPaths.filter { File(root, it).exists() }
        assertTrue(
            "The following obsolete files must be removed from disk:\n${existingFiles.joinToString("\n")}",
            existingFiles.isEmpty()
        )
    }

    @Test
    fun verifySportTypeRunkeeperDbColumnPreserved() {
        assertEquals("runkeeperName", SportTypeDatabaseManager.SportType.RUNKEEPER_NAME)
    }
}
