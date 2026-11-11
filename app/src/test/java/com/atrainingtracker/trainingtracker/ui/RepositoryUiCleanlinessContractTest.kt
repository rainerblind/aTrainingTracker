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
 * Architectural contract test verifying database manager decoupling across presentation layers
 * (REQ-UI-507, TST-UI-507.1).
 *
 * Ensures that presentation layer files (MainActivityWithNavigation, ImportBackupTabsScreen,
 * ControlTrackingViewModel, EquipmentViewModel) route all database access through domain repositories
 * and eliminate direct SQLite database manager calls.
 */
class RepositoryUiCleanlinessContractTest {

    private fun resolveFile(relativePath: String): File {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(projectDir, relativePath),
            File(projectDir, "app/$relativePath"),
            File(projectDir, "../app/$relativePath")
        )
        return candidates.firstOrNull { it.exists() } ?: File(projectDir, "app/$relativePath")
    }

    private val dbManagerPattern = Regex("""\b(Devices|SportType|TrackingViews|WorkoutSummaries)DatabaseManager\b""")

    @Test
    fun testMainActivityWithNavigation_zeroDatabaseManagerReferences() {
        val file = resolveFile("src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt")
        assertTrue("MainActivityWithNavigation.kt must exist", file.exists())
        val content = file.readText()

        val matches = dbManagerPattern.findAll(content).map { it.value }.toList()
        assertTrue(
            "MainActivityWithNavigation.kt must contain zero DatabaseManager references (REQ-UI-507.1), found: $matches",
            matches.isEmpty()
        )
        assertTrue(
            "MainActivityWithNavigation.kt must route through DeviceDataRepository",
            content.contains("DeviceDataRepository.getInstance")
        )
        assertTrue(
            "MainActivityWithNavigation.kt must route through TrackingViewsRepository",
            content.contains("TrackingViewsRepository.getInstance")
        )
        assertTrue(
            "MainActivityWithNavigation.kt must route through WorkoutRepository",
            content.contains("WorkoutRepository.getInstance")
        )
    }

    @Test
    fun testImportBackupTabsScreen_zeroDatabaseManagerReferences() {
        val file = resolveFile("src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt")
        assertTrue("ImportBackupTabsScreen.kt must exist", file.exists())
        val content = file.readText()

        val matches = dbManagerPattern.findAll(content).map { it.value }.toList()
        assertTrue(
            "ImportBackupTabsScreen.kt must contain zero DatabaseManager references (REQ-UI-507.2), found: $matches",
            matches.isEmpty()
        )
        assertTrue(
            "ImportBackupTabsScreen.kt must route through SportTypesRepository",
            content.contains("SportTypesRepository")
        )
    }

    @Test
    fun testControlTrackingViewModel_zeroDatabaseManagerReferences() {
        val file = resolveFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingViewModel.kt")
        assertTrue("ControlTrackingViewModel.kt must exist", file.exists())
        val content = file.readText()

        val matches = dbManagerPattern.findAll(content).map { it.value }.toList()
        assertTrue(
            "ControlTrackingViewModel.kt must contain zero DatabaseManager references (REQ-UI-507.4), found: $matches",
            matches.isEmpty()
        )
        assertTrue(
            "ControlTrackingViewModel.kt must route sensor seeding through DeviceDataRepository",
            content.contains("devicesRepository.hasPairedRemoteDevices()")
        )
        assertTrue(
            "ControlTrackingViewModel.kt must route unfinished workout check through WorkoutRepository",
            content.contains("workoutRepository.hasUnfinishedWorkout()")
        )
    }

    @Test
    fun testEquipmentViewModel_zeroDatabaseManagerGetInstance() {
        val file = resolveFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentViewModel.kt")
        assertTrue("EquipmentViewModel.kt must exist", file.exists())
        val content = file.readText()

        assertFalse(
            "EquipmentViewModel.kt must not call DatabaseManager.getInstance (REQ-UI-507.3)",
            content.contains("DatabaseManager.getInstance")
        )
        assertTrue(
            "EquipmentViewModel.kt must support SportTypesRepository injection",
            content.contains("sportTypesRepository: SportTypesRepository")
        )
        assertTrue(
            "EquipmentViewModel.kt must support DeviceDataRepository injection",
            content.contains("deviceDataRepository: DeviceDataRepository")
        )
        assertTrue(
            "EquipmentViewModel.kt must support WorkoutRepository injection",
            content.contains("workoutRepository: WorkoutRepository")
        )
    }

    @Test
    fun testPackageWideUiAndActivities_zeroDatabaseManagerGetInstance() {
        val rootDirs = listOf(
            resolveFile("src/main/java/com/atrainingtracker/trainingtracker/ui"),
            resolveFile("src/main/java/com/atrainingtracker/trainingtracker/activities")
        )
        val directDbInstantiationPattern = Regex("""\b\w*DatabaseManager\s*\.\s*getInstance\s*\(""")
        val violations = mutableListOf<String>()

        for (dir in rootDirs) {
            assertTrue("Directory ${dir.path} must exist", dir.exists())
            dir.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .filterNot { it.name.endsWith("Repository.kt") || it.name == "MapFragmentWithTrackViewModel.kt" }
                .forEach { file ->
                    val content = file.readText()
                    val matches = directDbInstantiationPattern.findAll(content).map { it.value }.toList()
                    if (matches.isNotEmpty()) {
                        violations.add("${file.name}: $matches")
                    }
                }
        }

        assertTrue(
            "Package-wide scan across ui/ and activities/ must contain zero DatabaseManager.getInstance references (REQ-UI-510.3), found: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun testSweepTargetClasses_zeroDatabaseManagerGetInstance() {
        val targetFiles = listOf(
            "src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt",
            "src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModel.kt",
            "src/main/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileViewModel.kt",
            "src/main/java/com/atrainingtracker/trainingtracker/ui/components/strava/StravaActivitySection.kt",
            "src/main/java/com/atrainingtracker/trainingtracker/ui/components/export/ExportStatusDataProvider.kt",
            "src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt",
            "src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapInitialLocationResolver.kt",
            "src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt",
            "src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt",
            "src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt"
        )
        val directDbInstantiationPattern = Regex("""\b\w*(DatabaseManager|DbHelper)\s*\.\s*getInstance\s*\(""")

        for (relPath in targetFiles) {
            val file = resolveFile(relPath)
            assertTrue("Target file ${file.path} must exist", file.exists())
            val content = file.readText()
            val matches = directDbInstantiationPattern.findAll(content).map { it.value }.toList()
            assertTrue(
                "Target file ${file.name} must contain zero DatabaseManager/DbHelper.getInstance references (REQ-UI-510.1), found: $matches",
                matches.isEmpty()
            )
        }
    }
}
