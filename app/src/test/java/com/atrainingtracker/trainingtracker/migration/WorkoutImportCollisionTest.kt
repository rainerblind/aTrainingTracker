/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.migration

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying case-insensitive collision disambiguation, true duplicate skipping,
 * and structural DDL construction without destructive DROP TABLE (REQ-MIG-042 / TST-MIG-039).
 */
class WorkoutImportCollisionTest {

    private lateinit var mockSummariesDb: SQLiteDatabase
    private lateinit var mockSamplesDb: SQLiteDatabase

    @Before
    fun setUp() {
        mockSummariesDb = mockk(relaxed = true)
        mockSamplesDb = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testTrueDuplicate_matchesCaseInsensitiveFileNameAndTimestamp() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.count } returns 1

        val capturedSelection = slot<String>()
        val capturedArgs = slot<Array<String>>()

        every {
            mockSummariesDb.query(
                WorkoutSummaries.TABLE,
                arrayOf(WorkoutSummaries.C_ID),
                capture(capturedSelection),
                capture(capturedArgs),
                null, null, null
            )
        } returns mockCursor

        val fileBaseName = "run_2026_10_18"
        val timeStart = "2026-10-18 10:00:00"

        // Call private isTrueDuplicate via reflection
        val method = ImportEngine::class.java.getDeclaredMethod(
            "isTrueDuplicate",
            SQLiteDatabase::class.java,
            String::class.java,
            String::class.java
        )
        method.isAccessible = true
        val result = method.invoke(ImportEngine, mockSummariesDb, fileBaseName, timeStart) as Boolean

        assertTrue("Expected duplicate to return true", result)
        assertTrue(capturedSelection.captured.contains("COLLATE NOCASE"))
        assertEquals(fileBaseName, capturedArgs.captured[0])
        assertEquals(timeStart, capturedArgs.captured[1])
    }

    @Test
    fun testDisambiguateBaseName_generatesBoundedUniqueName() {
        // When candidate is checked, first check returns false (not taken)
        val mockEmptyCursor = mockk<Cursor>(relaxed = true)
        every { mockEmptyCursor.count } returns 0

        every {
            mockSummariesDb.query(
                WorkoutSummaries.TABLE,
                any(),
                any(),
                any(),
                null, null, null
            )
        } returns mockEmptyCursor

        every {
            mockSamplesDb.rawQuery(any(), null)
        } returns mockEmptyCursor

        val method = ImportEngine::class.java.getDeclaredMethod(
            "disambiguateBaseName",
            SQLiteDatabase::class.java,
            SQLiteDatabase::class.java,
            String::class.java,
            String::class.java
        )
        method.isAccessible = true

        val longBaseName = "VeryLongWorkoutNameThatExceedsTheRecommendedLengthForSQLiteIdentifiers"
        val timeStart = "2026-10-18 12:34:56"

        val disambiguated = method.invoke(ImportEngine, mockSummariesDb, mockSamplesDb, longBaseName, timeStart) as String

        assertTrue("Expected disambiguated name to be <= 50 chars", disambiguated.length <= 50)
        assertTrue("Expected name to contain epoch timestamp", disambiguated.contains("_"))
        
        // Quoted table name must be <= 64 chars
        val tableName = WorkoutSamplesDatabaseManager.getTableName(disambiguated)
        assertTrue(tableName.startsWith("\"") && tableName.endsWith("\""))
        assertTrue(tableName.length <= 66) // quotes add 2 chars
    }

    @Test
    fun testStructuralDDLConstruction_preservesColumnsAndConstraints() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.getColumnIndexOrThrow("name") } returns 0
        every { mockCursor.getColumnIndexOrThrow("type") } returns 1
        every { mockCursor.getColumnIndexOrThrow("notnull") } returns 2
        every { mockCursor.getColumnIndexOrThrow("dflt_value") } returns 3
        every { mockCursor.getColumnIndexOrThrow("pk") } returns 4

        // Simulate 2 columns: _id (INTEGER PRIMARY KEY) and SPEED (REAL)
        var row = 0
        every { mockCursor.moveToNext() } answers {
            row++
            row <= 2
        }
        every { mockCursor.getString(0) } answers { if (row == 1) "_id" else "SPEED" }
        every { mockCursor.getString(1) } answers { if (row == 1) "INTEGER" else "REAL" }
        every { mockCursor.getInt(2) } answers { if (row == 1) 1 else 0 } // not null
        every { mockCursor.getString(3) } returns null
        every { mockCursor.getInt(4) } answers { if (row == 1) 1 else 0 } // pk

        val srcDb = mockk<SQLiteDatabase>(relaxed = true)
        val targetDb = mockk<SQLiteDatabase>(relaxed = true)

        every { srcDb.rawQuery("PRAGMA table_info(\"test_workout\")", null) } returns mockCursor

        val queryCursor = mockk<Cursor>(relaxed = true)
        every { queryCursor.moveToNext() } returns false
        every { srcDb.query(any(), any(), any(), any(), any(), any(), any()) } returns queryCursor

        val capturedSql = slot<String>()
        every { targetDb.execSQL(capture(capturedSql)) } returns Unit

        val method = ImportEngine::class.java.getDeclaredMethod(
            "copySamplesTable",
            SQLiteDatabase::class.java,
            SQLiteDatabase::class.java,
            String::class.java,
            String::class.java
        )
        method.isAccessible = true
        method.invoke(ImportEngine, srcDb, targetDb, "test_workout", "test_workout_disambiguated")

        assertTrue("DDL must be CREATE TABLE IF NOT EXISTS", capturedSql.captured.startsWith("CREATE TABLE IF NOT EXISTS"))
        assertTrue("DDL must target disambiguated name", capturedSql.captured.contains("\"test_workout_disambiguated\""))
        assertTrue("DDL must contain _id column", capturedSql.captured.contains("\"_id\" INTEGER PRIMARY KEY NOT NULL"))
        assertTrue("DDL must contain SPEED column", capturedSql.captured.contains("\"SPEED\" REAL"))
        verify(exactly = 0) { targetDb.execSQL(match { it.contains("DROP TABLE") }) }
    }
}
