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
import android.database.sqlite.SQLiteOpenHelper
import androidx.annotation.VisibleForTesting

/**
 * Shared thread-safe base class for legacy Java `*DatabaseManager` singletons
 * (`REQ-DAT-047.1`, `ATT-6036`, Kotlin-first per `ADR-001` / `REQ-ARC-005`).
 *
 * Consolidates:
 * - `@Volatile` double-checked locking for [getDatabase] so concurrent readers observe fully
 *   initialized [SQLiteDatabase] handles.
 * - Transparent `ATT-289` reopen-if-closed recovery: when backup/restore quiescence
 *   (`DatabaseConnectionManager.quiesceAllDatabases`, `REQ-MIG-041.3`) closes an active handle,
 *   [mDbHelper] is closed and reopened cleanly instead of throwing [IllegalStateException].
 * - Public [getDatabase] preserved for `DatabaseConnectionManager.getActiveDatabases()`.
 * - Deterministic [closeDatabase], [isDatabaseOpen], and [setDatabaseForTesting] lifecycle/test seams.
 *
 * @param H concrete [SQLiteOpenHelper] type managed by the subclass
 */
abstract class BaseDatabaseManager<H : SQLiteOpenHelper> protected constructor(
    @JvmField
    protected val mDbHelper: H?
) {

    @JvmField
    @Volatile
    protected var mDatabase: SQLiteDatabase? = null

    /**
     * Returns a writable [SQLiteDatabase] handle, opening it lazily via `@Volatile`
     * double-checked locking and transparently reopening if previously closed (`ATT-289`).
     *
     * Exposed as `public SQLiteDatabase getDatabase()` to Java callers and `database` property
     * to Kotlin callers (`REQ-DAT-047.1`, `REQ-MIG-041.3`).
     */
    open val database: SQLiteDatabase
        get() {
            var db = mDatabase
            if (db != null && db.isOpen) {
                return db
            }
            synchronized(this) {
                db = mDatabase
                if (db != null && db.isOpen) {
                    return db
                }
                val helper = mDbHelper
                    ?: throw IllegalStateException("SQLiteOpenHelper is null on ${javaClass.simpleName}")
                // If the database was closed externally (e.g. backup quiescence), clear helper cache first.
                if (db != null) {
                    helper.close()
                }
                db = helper.writableDatabase
                mDatabase = db
                return db!!
            }
        }

    /**
     * Closes the cached [SQLiteDatabase] handle (if open) and its [SQLiteOpenHelper].
     */
    @Synchronized
    open fun closeDatabase() {
        val db = mDatabase
        if (db != null && db.isOpen) {
            db.close()
        }
        mDatabase = null
        mDbHelper?.close()
    }

    /**
     * Returns `true` iff a cached database handle is currently present and open.
     */
    @Synchronized
    open fun isDatabaseOpen(): Boolean {
        val db = mDatabase
        return db != null && db.isOpen
    }

    /**
     * Returns the underlying [SQLiteOpenHelper] instance for subclass-internal operations.
     */
    protected fun getDbHelper(): H? = mDbHelper

    /**
     * Injects (or clears) a test [SQLiteDatabase] instance without brittle field reflection
     * on subclass declarations (`REQ-DAT-047.1`).
     */
    @VisibleForTesting
    @Synchronized
    open fun setDatabaseForTesting(database: SQLiteDatabase?) {
        mDatabase = database
    }
}
