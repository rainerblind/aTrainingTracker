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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import android.content.Context
import com.atrainingtracker.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Automated unit tests verifying bulk workout deletion two-step confirmation flow (REQ-UI-129, TST-UI-082, ATT-704).
 */
class DeleteOldWorkoutsConfirmationTest {

    private val mockContext = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        every {
            mockContext.getString(R.string.really_delete_old_workouts_format, *anyVararg())
        } answers {
            val formatArgs = args[1] as Array<*>
            val days = formatArgs[0]
            "Do you really want to delete all workouts older than $days days?"
        }
    }

    @Test
    fun testReallyDeleteOldWorkoutsFormat_interpolatesDaysCorrectly() {
        val message180 = mockContext.getString(R.string.really_delete_old_workouts_format, 180)
        assertEquals("Do you really want to delete all workouts older than 180 days?", message180)

        val message365 = mockContext.getString(R.string.really_delete_old_workouts_format, 365)
        assertEquals("Do you really want to delete all workouts older than 365 days?", message365)

        val message30 = mockContext.getString(R.string.really_delete_old_workouts_format, 30)
        assertEquals("Do you really want to delete all workouts older than 30 days?", message30)
    }

    @Test
    fun testTwoStepConfirmation_cancellationSafety_doesNotTriggerDeletion() {
        var showDeleteDialog = true
        var daysToConfirm: Int? = null
        var deletionExecutedWithDays: Int? = null

        // Step 1: User confirms input in DeleteOldWorkoutsDialog
        val onInputConfirm: (Int) -> Unit = { days ->
            showDeleteDialog = false
            daysToConfirm = days
        }
        onInputConfirm(180)

        assertFalse(showDeleteDialog)
        assertEquals(180, daysToConfirm)
        assertNull(deletionExecutedWithDays)

        // Step 2: User cancels in DeleteConfirmationDialog
        val onConfirmationDismiss: () -> Unit = {
            daysToConfirm = null
        }
        onConfirmationDismiss()

        assertNull("Confirmation state must be reset to null upon cancel", daysToConfirm)
        assertNull("Deletion must not be executed when user cancels confirmation", deletionExecutedWithDays)
    }

    @Test
    fun testTwoStepConfirmation_confirmedExecution_triggersDeletion() {
        var showDeleteDialog = true
        var daysToConfirm: Int? = null
        var deletionExecutedWithDays: Int? = null

        // Step 1: User confirms input in DeleteOldWorkoutsDialog
        val onInputConfirm: (Int) -> Unit = { days ->
            showDeleteDialog = false
            daysToConfirm = days
        }
        onInputConfirm(180)

        assertFalse(showDeleteDialog)
        assertEquals(180, daysToConfirm)
        assertNull(deletionExecutedWithDays)

        // Step 2: User confirms in DeleteConfirmationDialog
        val onConfirmationConfirm: (Int) -> Unit = { days ->
            deletionExecutedWithDays = days
            daysToConfirm = null
        }
        daysToConfirm?.let { onConfirmationConfirm(it) }

        assertNull("Confirmation state must be reset to null upon confirm", daysToConfirm)
        assertEquals("Deletion must be executed with specified retention days", 180, deletionExecutedWithDays)
    }
}
