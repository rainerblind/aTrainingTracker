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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * TST-DAT-026.4: Concurrency integration test verifying that SQLite VACUUM INTO
 * snapshots execute safely under active concurrent background write transactions
 * without locking errors, torn reads, or data corruption (REQ-DAT-031.2, REQ-DAT-031.5).
 */
@RunWith(RobolectricTestRunner::class)
class BackupManagerConcurrencyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private val dbName = "concurrent_workout.db"
    private var db: SQLiteDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        db?.enableWriteAheadLogging()
        db?.execSQL("CREATE TABLE Telemetry (id INTEGER PRIMARY KEY AUTOINCREMENT, hr INT, power REAL);")
    }

    @After
    fun tearDown() {
        try { db?.close() } catch (_: Exception) {}
        context.deleteDatabase(dbName)
    }

    @Test
    fun testVacuumInto_underConcurrentWrites_preservesIntegrity() {
        val activeDb = checkNotNull(db)
        val stagingDir = tempFolder.newFolder("concurrency_staging")
        val snapshotFile = File(stagingDir, "snapshot_concurrent.db")

        val isWriting = AtomicBoolean(true)
        val writeCount = AtomicInteger(0)
        val executor = Executors.newSingleThreadExecutor()

        try {
            // Background writer simulating TrackerService
            val writerFuture = executor.submit {
                var counter = 0
                while (isWriting.get() && counter < 100) {
                    val values = ContentValues().apply {
                        put("hr", 120 + (counter % 50))
                        put("power", 200.0 + counter)
                    }
                    activeDb.insert("Telemetry", null, values)
                    writeCount.incrementAndGet()
                    counter++
                    Thread.sleep(2)
                }
            }

            // Let background writer start
            Thread.sleep(10)

            // Execute VACUUM INTO concurrently
            activeDb.execSQL("VACUUM INTO '${snapshotFile.absolutePath}';")

            // Stop writer and await completion
            isWriting.set(false)
            writerFuture.get()

            assertTrue("Snapshot file must exist", snapshotFile.exists())
            assertTrue("Active writes must have occurred", writeCount.get() > 0)

            // Verify snapshot file passes PRAGMA integrity_check
            SQLiteDatabase.openDatabase(snapshotFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { snapDb ->
                snapDb.rawQuery("PRAGMA integrity_check;", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("ok", cursor.getString(0))
                }

                // Verify rows exist in snapshot
                snapDb.rawQuery("SELECT COUNT(*) FROM Telemetry;", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    val count = cursor.getInt(0)
                    assertTrue("Snapshot must contain committed rows", count > 0)
                }
            }
        } finally {
            isWriting.set(false)
            executor.shutdown()
        }
    }
}
