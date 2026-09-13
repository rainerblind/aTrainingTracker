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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.time.LocalDateTime
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Automated unit test suite verifying mark-as-finished capability,
 * active workout protection guard, and visual state transitions (REQ-UI-146, TST-UI-099, ATT-987).
 */
class WorkoutMarkFinishedTest {

    private val supportedLocales = listOf("", "de", "es", "fr", "it", "ja", "nl", "pl", "pt")

    @Before
    fun setUp() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setActiveWorkoutIdForTesting(-1L)
    }

    @After
    fun tearDown() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setActiveWorkoutIdForTesting(-1L)
    }

    private fun createWorkoutData(
        id: Long,
        finished: Boolean
    ): WorkoutData {
        return WorkoutData(
            id = id,
            finished = finished,
            fileBaseName = "workout_$id",
            workoutName = "Test Workout $id",
            sportId = 1L,
            sportName = "Running",
            bSportType = BSportType.RUN,
            startTimeS = 1718000000L,
            formattedDate = "2026-09-13",
            formattedTime = "10:00",
            localDateTime = LocalDateTime.of(2026, 9, 13, 10, 0),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 5000.0,
            maxDisplacement = null,
            activeTimeSec = 1800L,
            totalTimeSec = 1800L,
            avgSpeedMps = 2.78,
            ascentMeters = 50L,
            descentMeters = 50L,
            minAltitude = 200.0,
            maxAltitude = 250.0,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null
        )
    }

    private fun findResDirectory(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("res directory not found in candidate paths: $candidates")
    }

    private fun parseStringsFile(file: File): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (!file.exists()) return map

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")

        for (i in 0 until stringNodes.length) {
            val element = stringNodes.item(i) as Element
            val name = element.getAttribute("name")
            val text = element.textContent
            map[name] = text
        }
        return map
    }

    @Test
    fun testUnfinishedWorkout_whenIdle_canBeMarkedFinished_andHasLowAlphaAndDisabledExportMenu() {
        val workout = createWorkoutData(id = 100L, finished = false)
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setActiveWorkoutIdForTesting(-1L)

        // Visual Alpha evaluation: unfinished workouts have 0.5f alpha
        val contentAlpha = if (workout.headerData.finished) TTAlpha.High else 0.5f
        assertEquals("Unfinished workout must render with 0.5f alpha", 0.5f, contentAlpha, 0.001f)

        // Export menu permission evaluation: unfinished workouts cannot be exported
        val menuEnabled = workout.headerData.finished
        assertFalse("Export menu must be disabled for unfinished workout", menuEnabled)

        // Mark finished eligibility evaluation
        val canMarkFinished = !workout.finished && !TrainingApplication.isActivelyTracked(workout.id)
        assertTrue("Old unfinished workout when idle must be markable as finished", canMarkFinished)

        // Context menu triggering evaluation
        val canDelete = !TrainingApplication.isActivelyTracked(workout.id)
        val hasLongClick = canDelete || canMarkFinished
        assertTrue("Context menu must be available via long click", hasLongClick)
    }

    @Test
    fun testActivelyTrackedWorkout_cannotBeMarkedFinishedViaUI() {
        val workout = createWorkoutData(id = 200L, finished = false)
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        TrainingApplication.setActiveWorkoutIdForTesting(200L)

        // Safety Guard: Actively tracked workout MUST NEVER be markable as finished via aftermath UI
        val canMarkFinished = !workout.finished && !TrainingApplication.isActivelyTracked(workout.id)
        assertFalse("Actively tracked workout MUST NOT be markable as finished", canMarkFinished)

        val canDelete = !TrainingApplication.isActivelyTracked(workout.id)
        assertFalse("Actively tracked workout must not be deletable", canDelete)

        val hasLongClick = canDelete || canMarkFinished
        assertFalse("Actively tracked workout must not trigger context menu if neither delete nor mark finished is allowed", hasLongClick)
    }

    @Test
    fun testUnfinishedWorkout_whenAnotherWorkoutIsActivelyTracked_canBeMarkedFinished() {
        val oldWorkout = createWorkoutData(id = 101L, finished = false)
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        TrainingApplication.setActiveWorkoutIdForTesting(200L) // different workout is active

        val canMarkFinished = !oldWorkout.finished && !TrainingApplication.isActivelyTracked(oldWorkout.id)
        assertTrue("Old unfinished workout must be markable as finished even if another workout is actively tracked", canMarkFinished)
    }

    @Test
    fun testFinishedWorkout_hasHighAlpha_enabledExportMenu_andCannotBeMarkedFinishedAgain() {
        val workout = createWorkoutData(id = 300L, finished = true)
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setActiveWorkoutIdForTesting(-1L)

        val contentAlpha = if (workout.headerData.finished) TTAlpha.High else 0.5f
        assertEquals("Finished workout must render with TTAlpha.High", TTAlpha.High, contentAlpha, 0.001f)

        val menuEnabled = workout.headerData.finished
        assertTrue("Export menu must be enabled for finished workout", menuEnabled)

        val canMarkFinished = !workout.finished && !TrainingApplication.isActivelyTracked(workout.id)
        assertFalse("Finished workout must NOT offer mark as finished option", canMarkFinished)
    }

    @Test
    fun testStateTransitionFromUnfinishedToFinished() {
        // Step 1: Initial unfinished state
        val initialWorkout = createWorkoutData(id = 500L, finished = false)
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setActiveWorkoutIdForTesting(-1L)

        assertFalse(initialWorkout.finished)
        assertFalse(initialWorkout.headerData.finished)
        assertEquals(0.5f, if (initialWorkout.headerData.finished) TTAlpha.High else 0.5f, 0.001f)
        assertFalse(initialWorkout.headerData.finished)
        assertTrue(!initialWorkout.finished && !TrainingApplication.isActivelyTracked(initialWorkout.id))

        // Step 2: Transitioned state (as performed by WorkoutRepository.markWorkoutFinished)
        val updatedWorkout = initialWorkout.copy(finished = true)

        assertTrue(updatedWorkout.finished)
        assertTrue(updatedWorkout.headerData.finished)
        assertEquals(TTAlpha.High, if (updatedWorkout.headerData.finished) TTAlpha.High else 0.5f, 0.001f)
        assertTrue(updatedWorkout.headerData.finished)
        assertFalse(!updatedWorkout.finished && !TrainingApplication.isActivelyTracked(updatedWorkout.id))
    }

    @Test
    fun testMarkAsFinishedStringExistsAcrossAll9Locales() {
        val resDir = findResDirectory()

        for (locale in supportedLocales) {
            val valuesDir = if (locale.isEmpty()) "values" else "values-$locale"
            val stringsFile = File(resDir, "$valuesDir/strings.xml")
            assertTrue("File $stringsFile must exist", stringsFile.exists())

            val strings = parseStringsFile(stringsFile)
            assertTrue(
                "Key 'mark_as_finished' must exist in $valuesDir/strings.xml",
                strings.containsKey("mark_as_finished")
            )
            val content = strings["mark_as_finished"]
            assertNotNull("Content for 'mark_as_finished' in $valuesDir must not be null", content)
            assertFalse("Content for 'mark_as_finished' in $valuesDir must not be blank", content!!.isBlank())
        }
    }

    @Test
    fun testMarkAsFinishedTranslationsAreDistinctAndLocalized() {
        val resDir = findResDirectory()
        val enStrings = parseStringsFile(File(resDir, "values/strings.xml"))
        val deStrings = parseStringsFile(File(resDir, "values-de/strings.xml"))
        val esStrings = parseStringsFile(File(resDir, "values-es/strings.xml"))
        val frStrings = parseStringsFile(File(resDir, "values-fr/strings.xml"))
        val itStrings = parseStringsFile(File(resDir, "values-it/strings.xml"))
        val jaStrings = parseStringsFile(File(resDir, "values-ja/strings.xml"))
        val nlStrings = parseStringsFile(File(resDir, "values-nl/strings.xml"))
        val plStrings = parseStringsFile(File(resDir, "values-pl/strings.xml"))
        val ptStrings = parseStringsFile(File(resDir, "values-pt/strings.xml"))

        assertEquals("Mark as finished", enStrings["mark_as_finished"])
        assertEquals("Als abgeschlossen markieren", deStrings["mark_as_finished"])
        assertEquals("Marcar como finalizado", esStrings["mark_as_finished"])
        assertEquals("Marquer comme terminé", frStrings["mark_as_finished"])
        assertEquals("Contrassegna come completato", itStrings["mark_as_finished"])
        assertEquals("完了としてマーク", jaStrings["mark_as_finished"])
        assertEquals("Markeren als voltooid", nlStrings["mark_as_finished"])
        assertEquals("Oznacz jako zakończony", plStrings["mark_as_finished"])
        assertEquals("Marcar como concluído", ptStrings["mark_as_finished"])
    }
}
