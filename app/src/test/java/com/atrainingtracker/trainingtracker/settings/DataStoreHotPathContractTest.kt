/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract tests enforcing that UI and sensor hot paths are free of blocking
 * DataStore reads and thread parking calls (REQ-SET-079 / TST-SET-079.3).
 */
class DataStoreHotPathContractTest {

    private fun getProjectRoot(): File {
        var dir: File? = File(".").canonicalFile
        while (dir != null) {
            if (File(dir, "gradlew").exists() && File(dir, "settings.gradle").exists()) {
                return dir
            }
            dir = dir.parentFile
        }
        throw IllegalStateException("Project root containing gradlew not found from ${File(".").canonicalPath}")
    }

    @Test
    fun testHotPathFiles_containZeroRunBlockingCalls() {
        val root = getProjectRoot()

        val hotPathRelativePaths = listOf(
            "app/src/main/java/com/atrainingtracker/trainingtracker/settings/SettingsDataStoreJavaHelper.kt",
            "app/src/main/java/com/atrainingtracker/trainingtracker/batterysaver/BatterySaverModel.kt",
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt",
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt",
            "app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice.java",
            "app/src/main/java/com/atrainingtracker/banalservice/devices/VerticalSpeedAndSlopeDevice.java",
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryZoneMath.kt"
        )

        for (relativePath in hotPathRelativePaths) {
            val file = File(root, relativePath)
            assertTrue("File must exist: $relativePath", file.exists())
            val content = file.readText()
            assertFalse(
                "File '$relativePath' must NOT contain any 'runBlocking' calls (REQ-SET-079)",
                content.contains("runBlocking")
            )
        }
    }

    @Test
    fun testSettingsDataStore_readsDoNotContainRunBlocking() {
        val root = getProjectRoot()
        val file = File(root, "app/src/main/java/com/atrainingtracker/trainingtracker/settings/SettingsDataStore.kt")
        assertTrue("SettingsDataStore.kt must exist", file.exists())

        val lines = file.readLines()
        for ((index, line) in lines.withIndex()) {
            if (line.contains("runBlocking")) {
                // The ONLY permissible runBlocking is within the synchronous write bridge setAthleteWeightKgSync
                val isPermittedSyncWrite = lines.subList(maxOf(0, index - 5), minOf(lines.size, index + 5))
                    .any { it.contains("setAthleteWeightKgSync") }
                assertTrue(
                    "Unexpected runBlocking in SettingsDataStore.kt at line ${index + 1}: '$line'. " +
                        "Only setAthleteWeightKgSync is permitted to perform synchronous blocking writes.",
                    isPermittedSyncWrite
                )
            }
        }
    }

    @Test
    fun testLocationNameResolver_resolveLocationNameBlocking_hasWorkerThreadAnnotation() {
        val root = getProjectRoot()
        val file = File(root, "app/src/main/java/com/atrainingtracker/trainingtracker/location/LocationNameResolver.kt")
        assertTrue("LocationNameResolver.kt must exist", file.exists())

        val content = file.readText()
        assertTrue(
            "LocationNameResolver.kt must import WorkerThread",
            content.contains("import androidx.annotation.WorkerThread")
        )

        val methodRegex = Regex("@WorkerThread[\\s\\S]*?fun resolveLocationNameBlocking")
        assertTrue(
            "resolveLocationNameBlocking must be annotated with @WorkerThread (REQ-SET-079)",
            methodRegex.containsMatchIn(content)
        )
    }
}
