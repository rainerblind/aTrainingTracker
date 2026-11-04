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

package com.atrainingtracker.trainingtracker.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit and contract tests for [MilestoneAutoNamingManager] verifying milestone threshold evaluation,
 * dynamic title augmentation, and single-prompt invariant enforcement (REQ-REV-001, REQ-REV-002, REQ-REV-004, TST-REV-001, TST-REV-002, TST-REV-004).
 */
class MilestoneAutoNamingManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockSummariesDb: WorkoutSummariesDatabaseManager
    private lateinit var mockSqliteDb: SQLiteDatabase
    private lateinit var mockSportDb: SportTypeDatabaseManager
    private lateinit var mockSettingsDataStore: SettingsDataStore
    private val contentValuesMap = mutableMapOf<String, Any?>()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        contentValuesMap.clear()
        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } answers {
            contentValuesMap[firstArg<String>()] = secondArg<String>()
        }
        every { anyConstructed<ContentValues>().getAsString(any<String>()) } answers {
            contentValuesMap[firstArg<String>()] as? String
        }

        mockContext = mockk(relaxed = true)
        mockSummariesDb = mockk(relaxed = true)
        mockSqliteDb = mockk(relaxed = true)
        mockSportDb = mockk(relaxed = true)
        mockSettingsDataStore = mockk(relaxed = true)

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb
        every { mockSummariesDb.database } returns mockSqliteDb

        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportDb

        // SettingsDataStore passed directly via dependency injection parameter

        every { mockContext.getString(eq(R.string.milestone_workout_name_format), *anyVararg()) } answers {
            val rawArgs = invocation.args[1]
            val varargs: Array<*> = when (rawArgs) {
                is Array<*> -> rawArgs
                else -> arrayOf(rawArgs)
            }
            val count = varargs[0]
            val sport = varargs[1]
            val base = varargs[2]
            "$count. $sport: $base"
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testIsMilestone_exactThresholdEvaluation() {
        // Milestone thresholds: 100, 250, 500, 1000, 1500, 2000...
        assertTrue("100 must be recognized as milestone", MilestoneAutoNamingManager.isMilestone(100))
        assertTrue("250 must be recognized as milestone", MilestoneAutoNamingManager.isMilestone(250))
        assertTrue("500 must be recognized as milestone", MilestoneAutoNamingManager.isMilestone(500))
        assertTrue("1000 must be recognized as milestone", MilestoneAutoNamingManager.isMilestone(1000))
        assertTrue("1500 must be recognized as milestone", MilestoneAutoNamingManager.isMilestone(1500))
        assertTrue("2000 must be recognized as milestone", MilestoneAutoNamingManager.isMilestone(2000))
        assertTrue("2500 must be recognized as milestone", MilestoneAutoNamingManager.isMilestone(2500))

        // Non-milestones
        assertFalse("0 is not milestone", MilestoneAutoNamingManager.isMilestone(0))
        assertFalse("-1 is not milestone", MilestoneAutoNamingManager.isMilestone(-1))
        assertFalse("1 is not milestone", MilestoneAutoNamingManager.isMilestone(1))
        assertFalse("99 is not milestone", MilestoneAutoNamingManager.isMilestone(99))
        assertFalse("101 is not milestone", MilestoneAutoNamingManager.isMilestone(101))
        assertFalse("249 is not milestone", MilestoneAutoNamingManager.isMilestone(249))
        assertFalse("251 is not milestone", MilestoneAutoNamingManager.isMilestone(251))
        assertFalse("499 is not milestone", MilestoneAutoNamingManager.isMilestone(499))
        assertFalse("750 is not milestone", MilestoneAutoNamingManager.isMilestone(750))
        assertFalse("999 is not milestone", MilestoneAutoNamingManager.isMilestone(999))
    }

    @Test
    fun testFormatMilestoneTitle_formatsCorrectly() {
        val title = MilestoneAutoNamingManager.formatMilestoneTitle(
            context = mockContext,
            count = 100,
            sportName = "Radfahrt",
            baseTitle = "Hausrunde"
        )
        assertEquals("100. Radfahrt: Hausrunde", title)
    }

    @Test
    fun testFormatMilestoneTitle_preventsDoublePrefixing() {
        val title = MilestoneAutoNamingManager.formatMilestoneTitle(
            context = mockContext,
            count = 100,
            sportName = "Radfahrt",
            baseTitle = "100. Radfahrt: Hausrunde"
        )
        assertEquals("100. Radfahrt: Hausrunde", title)
    }

    @Test
    fun testEvaluateAndApplyMilestone_nonMilestoneDoesNotUpdateNameOrPrompt() {
        every { mockSummariesDb.getFinishedWorkoutCountForSport(1L) } returns 42

        val result = MilestoneAutoNamingManager.evaluateAndApplyMilestone(
            context = mockContext,
            workoutId = 123L,
            sportId = 1L,
            settingsDataStore = mockSettingsDataStore
        )

        assertNull(result)
    }

    @Test
    fun testEvaluateAndApplyMilestone_firstJubileeUpdatesNameAndReturnsCelebrationData() {
        every { mockSummariesDb.getFinishedWorkoutCountForSport(1L) } returns 100
        every { mockSportDb.getUIName(1L) } returns "Radfahrt"
        every { mockSummariesDb.getString(123L, WorkoutSummaries.WORKOUT_NAME) } returns "Hausrunde"
        every { mockSettingsDataStore.hasShownFirstJubileePrompt } returns flowOf(false)

        val valuesSlot = slot<ContentValues>()
        every {
            mockSqliteDb.update(WorkoutSummaries.TABLE, capture(valuesSlot), any(), any())
        } returns 1

        val result = MilestoneAutoNamingManager.evaluateAndApplyMilestone(
            context = mockContext,
            workoutId = 123L,
            sportId = 1L,
            settingsDataStore = mockSettingsDataStore
        )

        assertNotNull(result)
        assertEquals(123L, result!!.workoutId)
        assertEquals(100, result.count)
        assertEquals("Radfahrt", result.sportName)

        val updatedName = valuesSlot.captured.getAsString(WorkoutSummaries.WORKOUT_NAME)
        assertEquals("100. Radfahrt: Hausrunde", updatedName)
    }

    @Test
    fun testEvaluateAndApplyMilestone_subsequentJubileeUpdatesNameWithoutPrompting() {
        every { mockSummariesDb.getFinishedWorkoutCountForSport(1L) } returns 250
        every { mockSportDb.getUIName(1L) } returns "Radfahrt"
        every { mockSummariesDb.getString(124L, WorkoutSummaries.WORKOUT_NAME) } returns "Hausrunde"
        // Athlete has already seen the first jubilee prompt
        every { mockSettingsDataStore.hasShownFirstJubileePrompt } returns flowOf(true)

        val valuesSlot = slot<ContentValues>()
        every {
            mockSqliteDb.update(WorkoutSummaries.TABLE, capture(valuesSlot), any(), any())
        } returns 1

        val result = MilestoneAutoNamingManager.evaluateAndApplyMilestone(
            context = mockContext,
            workoutId = 124L,
            sportId = 1L,
            settingsDataStore = mockSettingsDataStore
        )

        // AC-4 invariant: never show dialog again (result must be null)
        assertNull("Subsequent jubilee must not prompt review/celebration dialog", result)

        // But the title must still be augmented!
        val updatedName = valuesSlot.captured.getAsString(WorkoutSummaries.WORKOUT_NAME)
        assertEquals("250. Radfahrt: Hausrunde", updatedName)
    }
}
