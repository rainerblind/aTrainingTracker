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

import android.content.ContentValues
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import com.atrainingtracker.testutil.RealSqliteTestHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [mapRows] and [inTransaction] verifying deterministic cursor closure
 * (on success, empty set, and mapper exception) and SQLite transaction commit/rollback semantics.
 *
 * // Verifies REQ-DAT-047 / TST-DAT-043
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseExtensionsTest {

    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        db = RealSqliteTestHelper.createInMemoryDatabase()
        db.execSQL("CREATE TABLE Kv (k TEXT PRIMARY KEY, v INTEGER)")
    }

    @After
    fun tearDown() {
        RealSqliteTestHelper.cleanupDatabase(db)
    }

    // Verifies REQ-DAT-047 / TST-DAT-043
    @Test
    fun mapRows_mapsAllRowsAndClosesCursor() {
        val cursor = MatrixCursor(arrayOf("k", "v")).apply {
            addRow(arrayOf<Any>("a", 1))
            addRow(arrayOf<Any>("b", 2))
            addRow(arrayOf<Any>("c", 3))
        }
        val mapped = cursor.mapRows { it.getString(0) to it.getInt(1) }
        assertEquals(listOf("a" to 1, "b" to 2, "c" to 3), mapped)
        assertTrue("Cursor must be closed after mapRows", cursor.isClosed)
    }

    // Verifies REQ-DAT-047 / TST-DAT-043
    @Test
    fun mapRows_returnsEmptyListOnEmptyCursorAndClosesOnException() {
        val empty = MatrixCursor(arrayOf("k"))
        assertEquals(emptyList<String>(), empty.mapRows { it.getString(0) })
        assertTrue(empty.isClosed)

        val failing = MatrixCursor(arrayOf("k")).apply { addRow(arrayOf("boom")) }
        val error = runCatching {
            failing.mapRows { throw IllegalStateException("mapper failure") }
        }.exceptionOrNull()
        assertEquals("mapper failure", error?.message)
        assertTrue("Cursor must still be closed when mapper throws", failing.isClosed)
    }

    // Verifies REQ-DAT-047 / TST-DAT-043
    @Test
    fun inTransaction_commitsAndReturnsValueOnSuccess() {
        val insertedId = db.inTransaction {
            val cv = ContentValues().apply {
                put("k", "committed")
                put("v", 42)
            }
            it.insert("Kv", null, cv)
        }
        assertTrue(insertedId > 0L)

        val rows = db.query("Kv", arrayOf("k", "v"), null, null, null, null, null)
            .mapRows { it.getString(0) to it.getInt(1) }
        assertEquals(listOf("committed" to 42), rows)
    }

    // Verifies REQ-DAT-047 / TST-DAT-043
    @Test
    fun inTransaction_rollsBackAndPropagatesOnException() {
        val thrown = runCatching {
            db.inTransaction {
                val cv = ContentValues().apply {
                    put("k", "rolled_back")
                    put("v", 99)
                }
                it.insert("Kv", null, cv)
                throw IllegalArgumentException("abort tx")
            }
        }.exceptionOrNull()

        assertEquals("abort tx", thrown?.message)
        val rows = db.query("Kv", arrayOf("k"), null, null, null, null, null)
            .mapRows { it.getString(0) }
        assertTrue("Transaction must roll back inserted row on exception", rows.isEmpty())
    }
}
