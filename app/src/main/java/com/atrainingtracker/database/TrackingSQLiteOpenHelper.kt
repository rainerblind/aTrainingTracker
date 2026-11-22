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

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.annotation.VisibleForTesting
import java.util.WeakHashMap

/**
 * Process-wide weak-referenced tracker of currently open [SQLiteDatabase] handles across all
 * [BaseDatabaseManager] and [TrackingSQLiteOpenHelper] instances (`REQ-MIG-057.3`, `ATT-8122`).
 *
 * Allows `DatabaseConnectionManager.getActiveDatabases()` and `quiesceAllDatabases()` to
 * checkpoint/close every open SQLite database without eagerly instantiating singletons or
 * opening/creating closed `.db` files on disk.
 */
object OpenDatabaseTracker {

    private val lock = Any()
    private val managers = WeakHashMap<BaseDatabaseManager<*>, Boolean>()
    private val helpers = WeakHashMap<TrackingSQLiteOpenHelper, Boolean>()

    internal fun isDatabaseOpenSafely(db: SQLiteDatabase?): Boolean {
        if (db == null) return false
        return try {
            db.isOpen
        } catch (_: Throwable) {
            false
        }
    }

    fun register(manager: BaseDatabaseManager<*>) {
        synchronized(lock) {
            managers[manager] = true
        }
    }

    fun register(helper: TrackingSQLiteOpenHelper) {
        synchronized(lock) {
            helpers[helper] = true
        }
    }

    /**
     * Returns all currently open [SQLiteDatabase] handles (deduplicated) without triggering any
     * lazy database open or singleton creation.
     */
    @JvmStatic
    fun getOpenDatabases(): List<SQLiteDatabase> {
        val managerSnapshot: List<BaseDatabaseManager<*>>
        val helperSnapshot: List<TrackingSQLiteOpenHelper>
        synchronized(lock) {
            managerSnapshot = managers.keys.toList()
            helperSnapshot = helpers.keys.toList()
        }
        val result = LinkedHashSet<SQLiteDatabase>()
        for (manager in managerSnapshot) {
            val db = try {
                manager.getOpenDatabaseOrNull()
            } catch (_: Throwable) {
                null
            }
            if (isDatabaseOpenSafely(db)) {
                result.add(db!!)
            }
        }
        for (helper in helperSnapshot) {
            val db = try {
                helper.getOpenDatabaseOrNull()
            } catch (_: Throwable) {
                null
            }
            if (isDatabaseOpenSafely(db)) {
                result.add(db!!)
            }
        }
        return result.toList()
    }

    @VisibleForTesting
    internal fun clearForTesting() {
        synchronized(lock) {
            managers.clear()
            helpers.clear()
        }
    }
}

/**
 * Abstract [SQLiteOpenHelper] base class that passively records its live [SQLiteDatabase] handle
 * when opened and registers itself in [OpenDatabaseTracker] (`REQ-MIG-057.3`, `ATT-8122`).
 */
abstract class TrackingSQLiteOpenHelper(
    context: Context?,
    name: String?,
    factory: SQLiteDatabase.CursorFactory?,
    version: Int
) : SQLiteOpenHelper(context, name, factory, version) {

    @Volatile
    private var cachedDb: SQLiteDatabase? = null

    init {
        OpenDatabaseTracker.register(this)
    }

    override fun getWritableDatabase(): SQLiteDatabase {
        return super.getWritableDatabase().also {
            cachedDb = it
            OpenDatabaseTracker.register(this)
        }
    }

    override fun getReadableDatabase(): SQLiteDatabase {
        return super.getReadableDatabase().also {
            cachedDb = it
            OpenDatabaseTracker.register(this)
        }
    }

    @Synchronized
    override fun close() {
        try {
            super.close()
        } finally {
            cachedDb = null
        }
    }

    /**
     * Returns the cached [SQLiteDatabase] handle iff already open, clearing the cache if closed
     * and never opening an unopened database.
     */
    fun getOpenDatabaseOrNull(): SQLiteDatabase? {
        val db = cachedDb ?: return null
        return if (OpenDatabaseTracker.isDatabaseOpenSafely(db)) {
            db
        } else {
            cachedDb = null
            null
        }
    }
}
