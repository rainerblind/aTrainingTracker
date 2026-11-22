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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.database

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.atrainingtracker.testutil.RealSqliteTestHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric real-SQLite unit tests for [SqliteSchemaUtils] covering plain & quoted table names
 * (`ATT-4968` / `REQ-TRK-051.1`), case-insensitive `hasColumn`, idempotent `addColumnIfNotExists`,
 * missing-table safety, and structured [SqliteSchemaUtils.ColumnInfo] parsing.
 *
 * // Verifies REQ-DAT-047 / TST-DAT-043
 */
@RunWith(RobolectricTestRunner::class)
class SqliteSchemaUtilsTest {

    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        db = RealSqliteTestHelper.createInMemoryDatabase()
    }

    @After
    fun tearDown() {
        RealSqliteTestHelper.cleanupDatabase(db)
    }

    // Verifies REQ-DAT-047 / TST-DAT-043
    @Test
    fun formatTableIdentifierForPragma_preservesSimpleAndQuotesSpecialNames() {
        assertEquals("SportType", SqliteSchemaUtils.formatTableIdentifierForPragma("SportType"))
        assertEquals("\"test_workout\"", SqliteSchemaUtils.formatTableIdentifierForPragma("\"test_workout\""))
        assertEquals("\"2026-11-21_120000\"", SqliteSchemaUtils.formatTableIdentifierForPragma("2026-11-21_120000"))
        assertEquals("\"2026-11-21_120000\"", SqliteSchemaUtils.formatTableIdentifierForPragma("\"2026-11-21_120000\""))
        assertEquals("\"odd\"\"name\"", SqliteSchemaUtils.formatTableIdentifierForPragma("odd\"name"))
    }

    // Verifies REQ-DAT-047 / TST-DAT-043
    @Test
    fun getTableColumnNames_handlesPlainQuotedAndMissingTablesCaseInsensitively() {
        db.execSQL("CREATE TABLE SamplePlain (_id INTEGER PRIMARY KEY, Speed REAL, Name TEXT)")
        val plainCols = SqliteSchemaUtils.getTableColumnNames(db, "SamplePlain")
        assertEquals(3, plainCols.size)
        assertTrue(plainCols.contains("speed"))
        assertTrue(SqliteSchemaUtils.hasColumn(db, "SamplePlain", "NAME"))
        assertFalse(SqliteSchemaUtils.hasColumn(db, "SamplePlain", "NonExistent"))

        val dynamicTable = "\"2026-11-22_010000\""
        db.execSQL("CREATE TABLE $dynamicTable (_id INTEGER PRIMARY KEY, \"POWER (Stage's \"\"Pro\"\")\" REAL)")
        val dynamicCols = SqliteSchemaUtils.getTableColumnNames(db, dynamicTable)
        assertTrue(dynamicCols.contains("_id"))
        assertTrue(dynamicCols.contains("POWER (Stage's \"Pro\")"))

        // Missing table returns empty set without throwing
        assertTrue(SqliteSchemaUtils.getTableColumnNames(db, "DoesNotExist").isEmpty())
        assertFalse(SqliteSchemaUtils.hasColumn(db, "DoesNotExist", "_id"))
    }

    // Verifies REQ-DAT-047 / TST-DAT-043
    @Test
    fun addColumnIfNotExists_isIdempotentWithAndWithoutDefaultValue() {
        db.execSQL("CREATE TABLE Items (_id INTEGER PRIMARY KEY)")

        assertTrue(SqliteSchemaUtils.addColumnIfNotExists(db, "Items", "Count", "INTEGER", "0"))
        assertFalse(SqliteSchemaUtils.addColumnIfNotExists(db, "Items", "count", "INTEGER", "0"))
        assertTrue(SqliteSchemaUtils.addColumnIfNotExists(db, "Items", "Label", "TEXT DEFAULT 'none'"))
        assertFalse(SqliteSchemaUtils.addColumnIfNotExists(db, "Items", "LABEL", "TEXT DEFAULT 'none'"))

        assertTrue(SqliteSchemaUtils.hasColumn(db, "Items", "Count"))
        assertTrue(SqliteSchemaUtils.hasColumn(db, "Items", "Label"))
    }

    // Verifies REQ-DAT-047 / TST-DAT-043
    @Test
    fun addColumnIfNotExists_throwsFailFastOnAlterFailureForMissingTable() {
        assertThrows(SQLiteException::class.java) {
            SqliteSchemaUtils.addColumnIfNotExists(db, "MissingTable", "ColA", "INTEGER DEFAULT 0")
        }
    }

    // Verifies REQ-DAT-047 / TST-DAT-043
    @Test
    fun addColumnIfNotExistsLenient_returnsFalseOnAlterFailureAndAddsWhenPresent() {
        assertFalse(
            SqliteSchemaUtils.addColumnIfNotExistsLenient(db, "MissingTable", "ColA", "INTEGER DEFAULT 0")
        )

        db.execSQL("CREATE TABLE LenientItems (_id INTEGER PRIMARY KEY)")
        assertTrue(
            SqliteSchemaUtils.addColumnIfNotExistsLenient(db, "LenientItems", "Flag", "INTEGER", "1")
        )
        assertFalse(
            SqliteSchemaUtils.addColumnIfNotExistsLenient(db, "LenientItems", "flag", "INTEGER", "1")
        )
        assertTrue(SqliteSchemaUtils.hasColumn(db, "LenientItems", "Flag"))
    }

    // Verifies REQ-DAT-047 / TST-DAT-043
    @Test
    fun getTableColumnInfos_parsesFullMetadataForImportEngine() {
        db.execSQL(
            """
            CREATE TABLE "2026-11-22_sample" (
                _id INTEGER PRIMARY KEY,
                TIME TEXT NOT NULL DEFAULT '0',
                ALTITUDE REAL
            )
            """.trimIndent()
        )

        val infos = SqliteSchemaUtils.getTableColumnInfos(db, "\"2026-11-22_sample\"")
        assertEquals(3, infos.size)

        assertEquals("_id", infos[0].name)
        assertEquals("INTEGER", infos[0].type)
        assertTrue(infos[0].isPrimaryKey)

        assertEquals("TIME", infos[1].name)
        assertEquals("TEXT", infos[1].type)
        assertTrue(infos[1].notNull)
        assertEquals("'0'", infos[1].defaultValue)
        assertFalse(infos[1].isPrimaryKey)

        assertEquals("ALTITUDE", infos[2].name)
        assertEquals("REAL", infos[2].type)
        assertFalse(infos[2].notNull)
        assertEquals(null, infos[2].defaultValue)
    }
}
