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

package com.atrainingtracker.testutil

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider

/**
 * Reusable test harness for real SQLite database testing backed by Robolectric.
 * Eliminates reliance on MockK execSQL mocks by running real DDL/DML migrations
 * against an authentic SQLite engine.
 */
object RealSqliteTestHelper {

    data class ColumnInfo(
        val cid: Int,
        val name: String,
        val type: String,
        val notNull: Boolean,
        val dfltValue: String?,
        val pk: Int
    )

    /**
     * Creates an in-memory SQLite database.
     */
    fun createInMemoryDatabase(): SQLiteDatabase {
        return SQLiteDatabase.create(null)
    }

    /**
     * Creates or opens a named database file backed by ApplicationProvider context.
     * Deletes any pre-existing database with that name first to ensure clean isolation.
     */
    fun createNamedDatabase(dbName: String): SQLiteDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(dbName)
        return context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
    }

    /**
     * Cleans up and deletes the database file for test isolation.
     */
    fun cleanupDatabase(db: SQLiteDatabase?, dbName: String? = null) {
        try {
            if (db != null && db.isOpen) {
                db.close()
            }
        } catch (_: Exception) {}
        if (dbName != null) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.deleteDatabase(dbName)
        }
    }

    /**
     * Inspects SQLite metadata for [tableName] via `PRAGMA table_info`.
     */
    fun getTableColumns(db: SQLiteDatabase, tableName: String): List<ColumnInfo> {
        val columns = mutableListOf<ColumnInfo>()
        db.rawQuery("PRAGMA table_info(\"$tableName\")", null).use { cursor ->
            val cidIdx = cursor.getColumnIndex("cid")
            val nameIdx = cursor.getColumnIndex("name")
            val typeIdx = cursor.getColumnIndex("type")
            val notnullIdx = cursor.getColumnIndex("notnull")
            val dfltIdx = cursor.getColumnIndex("dflt_value")
            val pkIdx = cursor.getColumnIndex("pk")

            while (cursor.moveToNext()) {
                columns.add(
                    ColumnInfo(
                        cid = cursor.getInt(cidIdx),
                        name = cursor.getString(nameIdx),
                        type = cursor.getString(typeIdx),
                        notNull = cursor.getInt(notnullIdx) == 1,
                        dfltValue = if (dfltIdx != -1 && !cursor.isNull(dfltIdx)) cursor.getString(dfltIdx) else null,
                        pk = cursor.getInt(pkIdx)
                    )
                )
            }
        }
        return columns
    }

    /**
     * Returns true if [tableName] has a column named [columnName] (case-insensitive).
     */
    fun hasColumn(db: SQLiteDatabase, tableName: String, columnName: String): Boolean {
        return getTableColumns(db, tableName).any { it.name.equals(columnName, ignoreCase = true) }
    }

    /**
     * Returns the ColumnInfo for [columnName] if it exists, or null.
     */
    fun getColumn(db: SQLiteDatabase, tableName: String, columnName: String): ColumnInfo? {
        return getTableColumns(db, tableName).firstOrNull { it.name.equals(columnName, ignoreCase = true) }
    }

    /**
     * Returns true if a table named [tableName] exists in sqlite_master.
     */
    fun hasTable(db: SQLiteDatabase, tableName: String): Boolean {
        db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    /**
     * Returns row count for [tableName].
     */
    fun getRowCount(db: SQLiteDatabase, tableName: String, whereClause: String? = null, whereArgs: Array<String>? = null): Long {
        return DatabaseUtils.queryNumEntries(db, tableName, whereClause, whereArgs)
    }
}
