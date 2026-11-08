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

package com.atrainingtracker.trainingtracker.ui.dialogs

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.tracker.DiagnosticCategory
import com.atrainingtracker.trainingtracker.tracker.WorkoutRecordingErrorCollector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Contract and behavioral tests for [WorkoutDiagnosticBottomSheet] and diagnostic actions
 * (REQ-TRK-053.4, TST-TRK-053.4, ATT-5886).
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutDiagnosticBottomSheetTest {

    private fun resolveSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app", relativePath),
            File("../app", relativePath)
        )
        return candidates.firstOrNull { it.exists() }
            ?: File(relativePath)
    }

    @Test
    fun testWorkoutDiagnosticBottomSheet_structuralContractVerification() {
        val file = resolveSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/dialogs/WorkoutDiagnosticBottomSheet.kt")
        assertTrue("WorkoutDiagnosticBottomSheet.kt must exist at ${file.absolutePath}", file.exists())
        val content = file.readText()

        // 1. Must use AppModalBottomSheet
        assertTrue("Must use AppModalBottomSheet", content.contains("AppModalBottomSheet"))

        // 2. Must use WarningAmber icon
        assertTrue("Must use WarningAmber icon", content.contains("WarningAmber"))

        // 3. Must contain plain explanation card with testTag
        assertTrue("Must contain testTag EXPLANATION", content.contains("WorkoutDiagnosticTestTags.EXPLANATION"))

        // 4. Must contain technical details section with copy button
        assertTrue("Must contain testTag DETAILS_TOGGLE", content.contains("WorkoutDiagnosticTestTags.DETAILS_TOGGLE"))
        assertTrue("Must contain testTag COPY_BUTTON", content.contains("WorkoutDiagnosticTestTags.COPY_BUTTON"))

        // 5. Must contain primary email bug report button
        assertTrue("Must contain testTag EMAIL_BUTTON", content.contains("WorkoutDiagnosticTestTags.EMAIL_BUTTON"))
        assertTrue("Must contain mailto:rainer.blind@gmail.com", content.contains("mailto:rainer.blind@gmail.com"))

        // 6. Must contain clear warning button and confirmation dialog
        assertTrue("Must contain testTag CLEAR_BUTTON", content.contains("WorkoutDiagnosticTestTags.CLEAR_BUTTON"))
        assertTrue("Must contain testTag CONFIRM_CLEAR", content.contains("WorkoutDiagnosticTestTags.CONFIRM_CLEAR"))
        assertTrue("Must contain AlertDialog confirmation", content.contains("AlertDialog"))
    }

    @Test
    fun testDiagnosticStringResources_exist() {
        assertTrue(R.string.workout_diagnostic_banner_title > 0)
        assertTrue(R.string.workout_diagnostic_banner_action > 0)
        assertTrue(R.string.workout_diagnostic_dialog_title > 0)
        assertTrue(R.string.workout_diagnostic_explanation_storage_full > 0)
        assertTrue(R.string.workout_diagnostic_explanation_sample_write > 0)
        assertTrue(R.string.workout_diagnostic_explanation_finalize > 0)
        assertTrue(R.string.workout_diagnostic_explanation_generic > 0)
        assertTrue(R.string.workout_diagnostic_technical_details_title > 0)
        assertTrue(R.string.workout_diagnostic_action_copy > 0)
        assertTrue(R.string.workout_diagnostic_copied_toast > 0)
        assertTrue(R.string.workout_diagnostic_action_send_email > 0)
        assertTrue(R.string.workout_diagnostic_email_subject > 0)
        assertTrue(R.string.workout_diagnostic_action_clear_warning > 0)
        assertTrue(R.string.workout_diagnostic_clear_confirm_title > 0)
        assertTrue(R.string.workout_diagnostic_clear_confirm_message > 0)
    }

    @Test
    fun testCategoryExplanationMapping() {
        // Sample write category
        val sampleJson = """[{"stage":"SAMPLE_WRITE","exceptionClass":"SQLiteException","message":"disk error","count":1}]"""
        val sampleEntries = WorkoutRecordingErrorCollector.fromJson(sampleJson)
        val sampleCat = WorkoutRecordingErrorCollector.resolvePrimaryCauseCategory(sampleEntries)
        assertEquals(DiagnosticCategory.SAMPLE_WRITE, sampleCat)

        // Storage full category
        val storageJson = """[{"stage":"SAMPLE_WRITE","exceptionClass":"android.database.sqlite.SQLiteFullException","message":"database or disk is full","count":1}]"""
        val storageEntries = WorkoutRecordingErrorCollector.fromJson(storageJson)
        val storageCat = WorkoutRecordingErrorCollector.resolvePrimaryCauseCategory(storageEntries)
        assertEquals(DiagnosticCategory.STORAGE_FULL, storageCat)

        // Finalize category
        val finalizeJson = """[{"stage":"FINALIZE","exceptionClass":"IllegalStateException","message":"failed to auto name","count":1}]"""
        val finalizeEntries = WorkoutRecordingErrorCollector.fromJson(finalizeJson)
        val finalizeCat = WorkoutRecordingErrorCollector.resolvePrimaryCauseCategory(finalizeEntries)
        assertEquals(DiagnosticCategory.FINALIZE, finalizeCat)
    }

    @Test
    fun testEmailSubjectAndBodyGeneration() {
        assertTrue(R.string.workout_diagnostic_email_subject > 0)
        val json = """[{"stage":"SAMPLE_WRITE","exceptionClass":"SQLiteException","message":"no column cadence","count":50}]"""

        val body = WorkoutRecordingErrorCollector.formatEmailBody(
            workoutName = "Afternoon Ride",
            sportName = "Bike",
            recordingErrorsJson = json
        )
        assertNotNull(body)
        assertTrue(body.contains("Afternoon Ride"))
        assertTrue(body.contains("Bike"))
        assertTrue(body.contains("no column cadence"))
        assertTrue(body.contains("50x"))
    }
}
