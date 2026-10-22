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
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * TST-DAT-026.2 / TST-DAT-026.5: Real SQLite Robolectric integration tests verifying
 * VACUUM INTO standalone snapshotting, WAL data incorporation, and PRAGMA integrity_check
 * validation on authentic SQLite databases (REQ-DAT-031.2, REQ-DAT-031.5).
 */
@RunWith(RobolectricTestRunner::class)
class BackupManagerRealSqliteTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private val testDbName = "real_test_workout.db"
    private var db: SQLiteDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(testDbName)
        db = context.openOrCreateDatabase(testDbName, Context.MODE_PRIVATE, null)
        db?.enableWriteAheadLogging()
        db?.execSQL("CREATE TABLE TestSamples (_id INTEGER PRIMARY KEY, value REAL, label TEXT);")
    }

    @After
    fun tearDown() {
        try { db?.close() } catch (_: Exception) {}
        context.deleteDatabase(testDbName)
    }

    @Test
    fun testVacuumInto_createsConsistentStandaloneSnapshot() {
        val activeDb = checkNotNull(db)

        // Insert uncheckpointed rows
        for (i in 1..20) {
            val values = ContentValues().apply {
                put("_id", i)
                put("value", i * 10.5)
                put("label", "sample_$i")
            }
            activeDb.insert("TestSamples", null, values)
        }

        val stagingDir = tempFolder.newFolder("staging")
        val snapshotFile = File(stagingDir, "snapshot.db")

        // Execute VACUUM INTO directly
        activeDb.execSQL("VACUUM INTO '${snapshotFile.absolutePath}';")

        assertTrue("Snapshot file must exist", snapshotFile.exists())
        assertTrue("Snapshot file size must be non-zero", snapshotFile.length() > 0)

        // Verify snapshot integrity and content
        SQLiteDatabase.openDatabase(snapshotFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { snapDb ->
            snapDb.rawQuery("PRAGMA integrity_check;", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }

            snapDb.rawQuery("SELECT COUNT(*) FROM TestSamples;", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(20, cursor.getInt(0))
            }
        }
    }

    @Test
    fun testVerifySnapshotIntegrity_validatesCleanAndCorruptDatabases() {
        val stagingDir = tempFolder.newFolder("integrity_test")
        val cleanDbFile = File(stagingDir, "clean.db")

        // Create clean DB
        SQLiteDatabase.openOrCreateDatabase(cleanDbFile, null).use { cleanDb ->
            cleanDb.execSQL("CREATE TABLE Test (id INT);")
            cleanDb.execSQL("INSERT INTO Test VALUES (1);")
        }

        assertTrue("Clean snapshot must pass integrity check", BackupManager.verifySnapshotIntegrity(stagingDir))

        // Create corrupt file
        val corruptDbFile = File(stagingDir, "corrupt.db")
        corruptDbFile.writeBytes(ByteArray(4096) { 0xFF.toByte() })

        assertFalse("Corrupted snapshot must fail integrity check", BackupManager.verifySnapshotIntegrity(stagingDir))
    }
}
