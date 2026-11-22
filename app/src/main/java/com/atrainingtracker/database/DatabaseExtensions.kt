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

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/**
 * Functional Kotlin combinators for safe SQLite cursor mapping and transactional execution
 * ({@code REQ-DAT-047.3}, {@code ATT-6036}), complementing Java {@code CursorUtils} ({@code REQ-DAT-043}).
 */

/**
 * Iterates over all rows of this [Cursor], applies [mapper] to each row, and deterministically
 * closes the cursor via [use] even if [mapper] throws.
 */
inline fun <T> Cursor.mapRows(mapper: (Cursor) -> T): List<T> {
    return use { cursor ->
        val results = ArrayList<T>()
        while (cursor.moveToNext()) {
            results.add(mapper(cursor))
        }
        results
    }
}

/**
 * Executes [block] inside a SQLite transaction, marking the transaction successful on normal
 * completion and guaranteeing [SQLiteDatabase.endTransaction] (rolling back on exception).
 */
inline fun <T> SQLiteDatabase.inTransaction(block: (SQLiteDatabase) -> T): T {
    beginTransaction()
    try {
        val result = block(this)
        setTransactionSuccessful()
        return result
    } finally {
        endTransaction()
    }
}
