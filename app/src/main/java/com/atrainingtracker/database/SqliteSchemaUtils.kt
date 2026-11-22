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
import android.util.Log
import java.util.TreeSet

/**
 * Centralized SQLite schema introspection and idempotent DDL helpers backed by
 * {@code PRAGMA table_info} ({@code REQ-DAT-047.2}, {@code REQ-TRK-051.1}, {@code ATT-4968}, {@code ATT-6036}).
 *
 * <p>Bypasses Android {@code SQLiteConnection.mPreparedStatementCache} staleness on Android <= 14
 * (API <= 34) by issuing direct {@code PRAGMA table_info(...)} queries with safe table-identifier
 * quoting and deterministic {@code Cursor.use} closure ({@code REQ-DAT-043}).</p>
 */
object SqliteSchemaUtils {

    private const val TAG = "SqliteSchemaUtils"
    private val SIMPLE_IDENTIFIER_REGEX = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

    /**
     * Structured metadata for a single column returned by {@code PRAGMA table_info}.
     */
    data class ColumnInfo(
        val name: String,
        val type: String,
        val notNull: Boolean,
        val defaultValue: String?,
        val isPrimaryKey: Boolean
    )

    /**
     * Formats [tableName] safely for {@code PRAGMA table_info(...)}:
     * - Simple unquoted SQL identifiers (e.g. {@code SportType}, {@code Rows}) are preserved bare.
     * - Already-quoted names or names containing hyphens, spaces, digits-first, or quotes
     *   (e.g. {@code "2026-11-03_120000"}) are normalized and double-quoted with {@code ""} escaping
     *   ({@code ATT-4968} / {@code REQ-TRK-051.1}).
     */
    @JvmStatic
    fun formatTableIdentifierForPragma(tableName: String): String {
        val trimmed = tableName.trim()
        if (SIMPLE_IDENTIFIER_REGEX.matches(trimmed)) {
            return trimmed
        }
        val unquoted = if (trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed.substring(1, trimmed.length - 1).replace("\"\"", "\"")
        } else {
            trimmed
        }
        return "\"${unquoted.replace("\"", "\"\"")}\""
    }

    /**
     * Retrieves all column names currently present in [tableName] using {@code PRAGMA table_info},
     * returning a case-insensitive [Set]. Returns an empty set safely if the table does not exist
     * or the query fails.
     */
    @JvmStatic
    fun getTableColumnNames(db: SQLiteDatabase, tableName: String): Set<String> {
        val columnNames: MutableSet<String> = TreeSet(String.CASE_INSENSITIVE_ORDER)
        val formattedTable = formatTableIdentifierForPragma(tableName)
        val sql = "PRAGMA table_info($formattedTable)"
        try {
            db.rawQuery(sql, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex >= 0) {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIndex)
                        if (name != null) {
                            columnNames.add(name)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read PRAGMA table_info for $tableName", e)
        }
        return columnNames
    }

    /**
     * Returns {@code true} iff [tableName] contains a column matching [columnName]
     * (case-insensitively) according to {@code PRAGMA table_info}.
     */
    @JvmStatic
    fun hasColumn(db: SQLiteDatabase, tableName: String, columnName: String): Boolean {
        return getTableColumnNames(db, tableName).contains(columnName)
    }

    /**
     * Idempotently adds [columnName] to [tableName] if it does not already exist (fail-fast).
     *
     * <p>Any {@code ALTER TABLE} failure is logged and rethrown so SQLite {@code onUpgrade}
     * transactions roll back instead of bumping the database version with a missing column
     * ({@code RISK-006}). Callers that intentionally log and continue (e.g. {@code onOpen}
     * self-healing) must use {@link #addColumnIfNotExistsLenient}.</p>
     *
     * @param db writable database handle
     * @param tableName target table name
     * @param columnName column name to verify and add
     * @param columnType SQL column type/definition (e.g. {@code "INTEGER DEFAULT 0"} or {@code "int"})
     * @param defaultValue optional default value expression appended as {@code DEFAULT <defaultValue>}
     * @return {@code true} if {@code ALTER TABLE ... ADD COLUMN} was executed, {@code false} if already present
     */
    @JvmStatic
    @JvmOverloads
    fun addColumnIfNotExists(
        db: SQLiteDatabase,
        tableName: String,
        columnName: String,
        columnType: String,
        defaultValue: String? = null
    ): Boolean {
        if (hasColumn(db, tableName, columnName)) {
            return false
        }
        val sql = buildString {
            append("ALTER TABLE ")
            append(tableName)
            append(" ADD COLUMN ")
            append(columnName)
            append(' ')
            append(columnType)
            if (defaultValue != null) {
                append(" DEFAULT ")
                append(defaultValue)
            }
        }
        try {
            db.execSQL(sql)
            Log.i(TAG, "Added missing column [$columnName] to table [$tableName]")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add column [$columnName] to table [$tableName]: ${e.message}", e)
            throw e
        }
    }

    /**
     * Lenient variant of [addColumnIfNotExists] that catches [Exception] (never [Error]/[Throwable]),
     * logs a warning, and returns {@code false} on failure. Reserved for legacy best-effort callers
     * ({@code onOpen} self-healing or defensive legacy upgrades) that intentionally continue on error.
     */
    @JvmStatic
    @JvmOverloads
    fun addColumnIfNotExistsLenient(
        db: SQLiteDatabase,
        tableName: String,
        columnName: String,
        columnType: String,
        defaultValue: String? = null
    ): Boolean {
        return try {
            addColumnIfNotExists(db, tableName, columnName, columnType, defaultValue)
        } catch (e: Exception) {
            Log.w(TAG, "Lenient addColumnIfNotExists skipped column [$columnName] on [$tableName]: ${e.message}", e)
            false
        }
    }

    /**
     * Reads full {@code PRAGMA table_info} column metadata ({@code name, type, notnull, dflt_value, pk})
     * for [tableName] (used by {@code ImportEngine} schema cloning).
     */
    @JvmStatic
    fun getTableColumnInfos(db: SQLiteDatabase, tableName: String): List<ColumnInfo> {
        val formattedTable = formatTableIdentifierForPragma(tableName)
        val sql = "PRAGMA table_info($formattedTable)"
        val columns = mutableListOf<ColumnInfo>()
        db.rawQuery(sql, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            val typeIdx = cursor.getColumnIndexOrThrow("type")
            val notNullIdx = cursor.getColumnIndexOrThrow("notnull")
            val dfltIdx = cursor.getColumnIndexOrThrow("dflt_value")
            val pkIdx = cursor.getColumnIndexOrThrow("pk")
            while (cursor.moveToNext()) {
                columns.add(
                    ColumnInfo(
                        name = cursor.getString(nameIdx),
                        type = cursor.getString(typeIdx) ?: "",
                        notNull = cursor.getInt(notNullIdx) == 1,
                        defaultValue = cursor.getString(dfltIdx),
                        isPrimaryKey = cursor.getInt(pkIdx) > 0
                    )
                )
            }
        }
        return columns
    }
}
