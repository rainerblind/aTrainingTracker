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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract test verifying database manager decoupling across Phase B engines,
 * exporters, and migration layer (REQ-DAT-041, TST-DAT-037.1).
 *
 * Ensures that background processing engines and exporters (WorkoutClusterEngine, LegacyImportEngine,
 * StravaUploader, FitFileWriter, TCXFileWriter, WorkoutDeletionHelper, BackupManager) eliminate direct
 * `*DatabaseManager.getInstance(...)` and `*DbHelper.getInstance(...)` calls, routing database access
 * cleanly through domain repositories.
 */
class RepositoryEngineCleanlinessContractTest {

    private fun resolveFile(relativePath: String): File {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(projectDir, relativePath),
            File(projectDir, "app/$relativePath"),
            File(projectDir, "../app/$relativePath")
        )
        return candidates.firstOrNull { it.exists() } ?: File(projectDir, "app/$relativePath")
    }

    private val directDbInstantiationPattern = Regex("""\b\w*(DatabaseManager|DbHelper)\s*\.\s*getInstance\s*\(""")

    @Test
    fun testWorkoutClusterEngine_zeroDatabaseManagerInstantiation() {
        val file = resolveFile("src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt")
        assertTrue("WorkoutClusterEngine.kt must exist", file.exists())
        val content = file.readText()

        val matches = directDbInstantiationPattern.findAll(content).map { it.value }.toList()
        assertTrue(
            "WorkoutClusterEngine.kt must contain zero DatabaseManager/DbHelper getInstance references (REQ-DAT-041.1), found: $matches",
            matches.isEmpty()
        )
        assertTrue(
            "WorkoutClusterEngine.kt must route through WorkoutClusterRepository",
            content.contains("WorkoutClusterRepository.getInstance")
        )
        assertTrue(
            "WorkoutClusterEngine.kt must route through SportTypesRepository",
            content.contains("SportTypesRepository.getInstance")
        )
    }

    @Test
    fun testLegacyImportEngine_zeroDatabaseManagerInstantiation() {
        val file = resolveFile("src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt")
        assertTrue("LegacyImportEngine.kt must exist", file.exists())
        val content = file.readText()

        val matches = directDbInstantiationPattern.findAll(content).map { it.value }.toList()
        assertTrue(
            "LegacyImportEngine.kt must contain zero DatabaseManager/DbHelper getInstance references (REQ-DAT-041.2), found: $matches",
            matches.isEmpty()
        )
        assertTrue(
            "LegacyImportEngine.kt must route through WorkoutRepository",
            content.contains("WorkoutRepository.getInstance")
        )
        assertTrue(
            "LegacyImportEngine.kt must route through ExportStatusRepository",
            content.contains("ExportStatusRepository.getInstance")
        )
    }

    @Test
    fun testStravaUploader_zeroDatabaseManagerInstantiation() {
        val file = resolveFile("src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/StravaUploader.kt")
        assertTrue("StravaUploader.kt must exist", file.exists())
        val content = file.readText()

        val matches = directDbInstantiationPattern.findAll(content).map { it.value }.toList()
        assertTrue(
            "StravaUploader.kt must contain zero DatabaseManager/DbHelper getInstance references (REQ-DAT-041.3), found: $matches",
            matches.isEmpty()
        )
        assertTrue(
            "StravaUploader.kt must route through WorkoutRepository",
            content.contains("WorkoutRepository.getInstance")
        )
        assertTrue(
            "StravaUploader.kt must route through ExportStatusRepository",
            content.contains("ExportStatusRepository.getInstance")
        )
    }

    @Test
    fun testFitFileWriter_zeroDatabaseManagerInstantiation() {
        val file = resolveFile("src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/FitFileWriter.java")
        assertTrue("FitFileWriter.java must exist", file.exists())
        val content = file.readText()

        val matches = directDbInstantiationPattern.findAll(content).map { it.value }.toList()
        assertTrue(
            "FitFileWriter.java must contain zero DatabaseManager/DbHelper getInstance references (REQ-DAT-041.4), found: $matches",
            matches.isEmpty()
        )
        assertTrue(
            "FitFileWriter.java must route through WorkoutRepository",
            content.contains("WorkoutRepository.getInstance")
        )
        assertTrue(
            "FitFileWriter.java must route through SportTypesRepository",
            content.contains("SportTypesRepository.getInstance")
        )
    }

    @Test
    fun testTCXFileWriter_zeroDatabaseManagerInstantiation() {
        val file = resolveFile("src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/TCXFileWriter.java")
        assertTrue("TCXFileWriter.java must exist", file.exists())
        val content = file.readText()

        val matches = directDbInstantiationPattern.findAll(content).map { it.value }.toList()
        assertTrue(
            "TCXFileWriter.java must contain zero DatabaseManager/DbHelper getInstance references (REQ-DAT-041.4), found: $matches",
            matches.isEmpty()
        )
        assertTrue(
            "TCXFileWriter.java must route through WorkoutRepository",
            content.contains("WorkoutRepository.getInstance")
        )
        assertTrue(
            "TCXFileWriter.java must route through SportTypesRepository",
            content.contains("SportTypesRepository.getInstance")
        )
    }

    @Test
    fun testWorkoutDeletionHelper_zeroDatabaseManagerInstantiation() {
        val file = resolveFile("src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutDeletionHelper.java")
        assertTrue("WorkoutDeletionHelper.java must exist", file.exists())
        val content = file.readText()

        val matches = directDbInstantiationPattern.findAll(content).map { it.value }.toList()
        assertTrue(
            "WorkoutDeletionHelper.java must contain zero DatabaseManager/DbHelper getInstance references (REQ-DAT-041.5), found: $matches",
            matches.isEmpty()
        )
        assertTrue(
            "WorkoutDeletionHelper.java must route through WorkoutRepository",
            content.contains("WorkoutRepository.getInstance")
        )
        assertTrue(
            "WorkoutDeletionHelper.java must route through ExportStatusRepository",
            content.contains("ExportStatusRepository.getInstance")
        )
    }

    @Test
    fun testBackupManager_zeroDatabaseManagerInstantiation() {
        val file = resolveFile("src/main/java/com/atrainingtracker/trainingtracker/migration/BackupManager.kt")
        assertTrue("BackupManager.kt must exist", file.exists())
        val content = file.readText()

        val matches = directDbInstantiationPattern.findAll(content).map { it.value }.toList()
        assertTrue(
            "BackupManager.kt must contain zero DatabaseManager/DbHelper getInstance references, found: $matches",
            matches.isEmpty()
        )
    }
}
