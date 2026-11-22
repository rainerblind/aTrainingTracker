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
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * Unit tests for [BaseDatabaseManager] verifying lazy open, volatile double-checked locking,
 * ATT-289 closed-DB helper close/reopen, lifecycle close/isOpen, and testing seam injection.
 *
 * // Verifies REQ-DAT-047 / TST-DAT-043
 */
class BaseDatabaseManagerTest {

    private class TestDatabaseManager(helper: SQLiteOpenHelper?) : BaseDatabaseManager<SQLiteOpenHelper>(helper)

    // Verifies REQ-DAT-047 / TST-DAT-043
    @Test
    fun getDatabase_opensLazilyAndCachesOpenHandle() {
        val helper = mockk<SQLiteOpenHelper>(relaxed = true)
        val db = mockk<SQLiteDatabase>(relaxed = true)
        every { helper.writableDatabase } returns db
        every { db.isOpen } returns true

        val manager = TestDatabaseManager(helper)
        assertFalse(manager.isDatabaseOpen())

        val first = manager.database
        val second = manager.database

        assertSame(db, first)
        assertSame(db, second)
        assertTrue(manager.isDatabaseOpen())
        verify(exactly = 1) { helper.writableDatabase }
    }

    // Verifies REQ-DAT-047 / TST-DAT-043
    @Test
    fun getDatabase_reopensCleanlyAfterExternalClose_ATT289() {
        val helper = mockk<SQLiteOpenHelper>(relaxed = true)
        val firstDb = mockk<SQLiteDatabase>(relaxed = true)
        val reopenedDb = mockk<SQLiteDatabase>(relaxed = true)
        var firstOpen = true
        every { firstDb.isOpen } answers { firstOpen }
        every { reopenedDb.isOpen } returns true
        every { helper.writableDatabase } returnsMany listOf(firstDb, reopenedDb)

        val manager = TestDatabaseManager(helper)
        assertSame(firstDb, manager.database)

        // Simulate backup/restore quiescence closing the underlying handle
        firstOpen = false
        assertFalse(manager.isDatabaseOpen())

        val recovered = manager.database
        assertSame(reopenedDb, recovered)
        assertTrue(manager.isDatabaseOpen())
        verify(exactly = 1) { helper.close() }
        verify(exactly = 2) { helper.writableDatabase }
    }

    // Verifies REQ-DAT-047 / TST-DAT-043
    @Test
    fun getDatabase_isThreadSafeUnderConcurrentDcl() {
        val helper = mockk<SQLiteOpenHelper>(relaxed = true)
        val db = mockk<SQLiteDatabase>(relaxed = true)
        every { db.isOpen } returns true
        every { helper.writableDatabase } answers {
            Thread.sleep(15)
            db
        }

        val manager = TestDatabaseManager(helper)
        val threads = 8
        val startLatch = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val futures = (1..threads).map {
                pool.submit(Callable {
                    startLatch.await()
                    manager.database
                })
            }
            startLatch.countDown()
            val results = futures.map { it.get() }
            results.forEach { assertSame(db, it) }
            verify(exactly = 1) { helper.writableDatabase }
        } finally {
            pool.shutdownNow()
        }
    }

    // Verifies REQ-DAT-047 / TST-DAT-043
    @Test
    fun closeDatabaseAndSetDatabaseForTesting_manageStateCleanly() {
        val helper = mockk<SQLiteOpenHelper>(relaxed = true)
        val injectedDb = mockk<SQLiteDatabase>(relaxed = true)
        every { injectedDb.isOpen } returns true

        val manager = TestDatabaseManager(helper)
        manager.setDatabaseForTesting(injectedDb)
        assertTrue(manager.isDatabaseOpen())
        assertSame(injectedDb, manager.database)

        manager.closeDatabase()
        assertFalse(manager.isDatabaseOpen())
        verify(exactly = 1) { injectedDb.close() }
        verify(exactly = 1) { helper.close() }
    }

    // Verifies REQ-DAT-047 / TST-DAT-043
    @Test
    fun nullHelper_allowsInjectedTestDatabaseAndThrowsIfUninjected() {
        val manager = TestDatabaseManager(null)
        val thrown = runCatching { manager.database }.exceptionOrNull()
        assertEquals(IllegalStateException::class.java, thrown?.javaClass)

        val injectedDb = mockk<SQLiteDatabase>(relaxed = true)
        every { injectedDb.isOpen } returns true
        manager.setDatabaseForTesting(injectedDb)
        assertSame(injectedDb, manager.database)
        manager.closeDatabase()
        assertFalse(manager.isDatabaseOpen())
    }
}
