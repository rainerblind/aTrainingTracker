/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying double-quote escaping, length guard validation, and table name
 * formatting for workout sample tables (REQ-MIG-042 / TST-MIG-039).
 */
class WorkoutSamplesDatabaseManagerEscapingTest {

    @Test
    fun testGetTableName_escapesEmbeddedDoubleQuotes() {
        val rawName = "Morning \"Fast\" Run"
        val tableName = WorkoutSamplesDatabaseManager.getTableName(rawName)
        assertEquals("\"Morning \"\"Fast\"\" Run\"", tableName)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetTableName_rejectsNullOrBlank() {
        WorkoutSamplesDatabaseManager.getTableName("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetTableName_rejectsLiteralNull() {
        WorkoutSamplesDatabaseManager.getTableName("null")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetTableName_rejectsLengthExceedingLimit() {
        // String of 65 characters
        val tooLong = "A".repeat(65)
        WorkoutSamplesDatabaseManager.getTableName(tooLong)
    }

    @Test
    fun testGetTableName_allowsMaxLengthAt64() {
        val exactly64 = "B".repeat(64)
        val tableName = WorkoutSamplesDatabaseManager.getTableName(exactly64)
        assertEquals("\"$exactly64\"", tableName)
    }

    @Test
    fun testSqlFormatting_withEscapedTableName() {
        val trickyName = "Run \"2026\" - Morning"
        val tableName = WorkoutSamplesDatabaseManager.getTableName(trickyName)
        assertEquals("\"Run \"\"2026\"\" - Morning\"", tableName)
        
        val createSql = "CREATE TABLE IF NOT EXISTS $tableName (_id INTEGER PRIMARY KEY, value REAL)"
        assertEquals("CREATE TABLE IF NOT EXISTS \"Run \"\"2026\"\" - Morning\" (_id INTEGER PRIMARY KEY, value REAL)", createSql)
    }
}
