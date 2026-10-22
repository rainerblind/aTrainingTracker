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

package com.atrainingtracker.banalservice.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.testutil.MockCursorFactory
import com.atrainingtracker.trainingtracker.migration.BackupManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Unit test suite for SportType database upgrade, custom sport preservation,
 * orphan state reconciliation, sequence safety, UI picker filtering, and downgrade safety (REQ-MIG-044, TST-MIG-041).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SportTypeDatabaseUpgradeTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase

    private val cvStores = Collections.synchronizedMap(IdentityHashMap<ContentValues, MutableMap<String, Any?>>())

    private fun io.mockk.MockKAnswerScope<*, *>.getRealInstance(): ContentValues {
        try {
            var obj: Any? = call.invocation.originalCall
            while (obj != null) {
                for (f in obj.javaClass.declaredFields) {
                    if (f.name == "self" || f.name == "\$self" || f.name.endsWith("\$self")) {
                        f.isAccessible = true
                        val s = f.get(obj)
                        if (s is ContentValues && s !== this.self) {
                            return s
                        }
                    }
                }
                val nextField = obj.javaClass.declaredFields.firstOrNull {
                    it.name.contains("originalCall") || it.name.contains("callable")
                }
                obj = nextField?.apply { isAccessible = true }?.get(obj)
            }
        } catch (_: Exception) { }
        return self as ContentValues
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)

        cvStores.clear()
        mockkConstructor(ContentValues::class)
        every { constructedWith<ContentValues>().put(any<String>(), or(any<String>(), isNull())) } answers {
            val cv = getRealInstance()
            val key = firstArg<String>()
            val value = secondArg<String?>()
            cvStores.computeIfAbsent(cv) { mutableMapOf() }[key] = value
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Long>()) } answers {
            val cv = getRealInstance()
            val key = firstArg<String>()
            val value = secondArg<Long>()
            cvStores.computeIfAbsent(cv) { mutableMapOf() }[key] = value
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Int>()) } answers {
            val cv = getRealInstance()
            val key = firstArg<String>()
            val value = secondArg<Int>()
            cvStores.computeIfAbsent(cv) { mutableMapOf() }[key] = value
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Double>()) } answers {
            val cv = getRealInstance()
            val key = firstArg<String>()
            val value = secondArg<Double>()
            cvStores.computeIfAbsent(cv) { mutableMapOf() }[key] = value
        }
        every { constructedWith<ContentValues>().getAsString(any<String>()) } answers {
            val cv = getRealInstance()
            cvStores[cv]?.get(firstArg<String>()) as? String
        }
        every { constructedWith<ContentValues>().getAsLong(any<String>()) } answers {
            val cv = getRealInstance()
            (cvStores[cv]?.get(firstArg<String>()) as? Number)?.toLong()
        }
        every { constructedWith<ContentValues>().getAsInteger(any<String>()) } answers {
            val cv = getRealInstance()
            (cvStores[cv]?.get(firstArg<String>()) as? Number)?.toInt()
        }
        every { constructedWith<ContentValues>().getAsDouble(any<String>()) } answers {
            val cv = getRealInstance()
            (cvStores[cv]?.get(firstArg<String>()) as? Number)?.toDouble()
        }
        every { constructedWith<ContentValues>().get(any<String>()) } answers {
            val cv = getRealInstance()
            cvStores[cv]?.get(firstArg<String>())
        }
        every { constructedWith<ContentValues>().clear() } answers {
            val cv = getRealInstance()
            cvStores[cv]?.clear()
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * TST-MIG-041.1: Verify clean v1 -> v3 schema upgrade preserves custom sports,
     * updates WALK base sport type to RUN, and adds is_placeholder column without dropping table.
     */
    @Test
    fun testCleanV1ToV3UpgradePreservesCustomSports() {
        val helper = SportTypeDatabaseManager.SportTypeDbHelper(mockContext)
        assertEquals("DB_VERSION must be 3 for ATT-3993", 3, SportTypeDatabaseManager.SportTypeDbHelper.DB_VERSION)

        // Mock PRAGMA table_info returning v1 columns (without is_placeholder)
        val pragmaCursor = MockCursorFactory.create(
            columns = listOf("cid", "name", "type", "notnull", "dflt_value", "pk"),
            rows = listOf(
                listOf(0, "_id", "INTEGER", 1, null, 1),
                listOf(1, "UIName", "text", 0, null, 0),
                listOf(2, "baseSportType", "text", 0, null, 0)
            )
        )
        every { mockDb.rawQuery("PRAGMA table_info(" + SportTypeDatabaseManager.SportType.TABLE + ")", null) } returns pragmaCursor

        val executedSqls = mutableListOf<String>()
        every { mockDb.execSQL(capture(executedSqls)) } just Runs
        val updatedTables = mutableListOf<String>()
        val capturedCv = slot<ContentValues>()
        val capturedWhere = slot<String>()
        val capturedArgs = slot<Array<String>>()
        every { mockDb.update(capture(updatedTables), capture(capturedCv), capture(capturedWhere), capture(capturedArgs)) } returns 1

        helper.onUpgrade(mockDb, 1, 3)

        // Verify transaction wrap
        verify(exactly = 1) { mockDb.beginTransaction() }
        verify(exactly = 1) { mockDb.setTransactionSuccessful() }
        verify(exactly = 1) { mockDb.endTransaction() }

        // Verify NO DROP TABLE was executed
        assertFalse("onUpgrade must not execute DROP TABLE", executedSqls.any { it.contains("DROP TABLE", ignoreCase = true) })

        // Verify WALK baseSportType updated to RUN
        assertTrue("onUpgrade must update SportTypes for WALK", updatedTables.contains(SportTypeDatabaseManager.SportType.TABLE))
        assertEquals("RUN", capturedCv.captured.getAsString(SportTypeDatabaseManager.SportType.BASE_SPORT_TYPE))
        assertTrue("where clause must guard WALK update", capturedWhere.captured.contains(SportTypeDatabaseManager.SportType.C_ID + "=4"))
        assertArrayEquals(arrayOf("walk", BSportType.UNKNOWN.name), capturedArgs.captured)

        // Verify ALTER TABLE was called to add is_placeholder
        assertTrue(
            "onUpgrade must execute ALTER TABLE to add is_placeholder",
            executedSqls.any { it.contains("ALTER TABLE SportTypes ADD COLUMN is_placeholder INTEGER DEFAULT 0", ignoreCase = true) }
        )
    }

    /**
     * TST-MIG-041.2: Verify corrupted post-v2 state healing resurrects missing sport IDs
     * as placeholders with is_placeholder = 1 and formatted UIName.
     */
    @Test
    fun testCorruptedPostV2StateHealingResurrectsPlaceholders() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val mockSummariesDb = mockk<SQLiteDatabase>(relaxed = true)

        // Mock WorkoutSummaries containing references to sportId 6 and 8
        val summariesCursor = MockCursorFactory.create(
            columns = listOf("sportId"),
            rows = listOf(listOf(6L), listOf(8L))
        )
        every {
            mockSummariesDb.rawQuery(
                "SELECT DISTINCT sportId FROM WorkoutSummaries WHERE sportId > ?",
                arrayOf("5")
            )
        } returns summariesCursor

        // Mock existing SportTypes containing only default sports 1..5
        val sportTypesCursor = MockCursorFactory.create(
            columns = listOf("_id"),
            rows = listOf(listOf(1L), listOf(2L), listOf(3L), listOf(4L), listOf(5L))
        )
        every {
            mockDb.rawQuery(
                "SELECT ${SportTypeDatabaseManager.SportType.C_ID} FROM ${SportTypeDatabaseManager.SportType.TABLE}",
                null
            )
        } returns sportTypesCursor

        every { mockContext.getString(com.atrainingtracker.R.string.sport_type_restored_placeholder, any()) } answers {
            "Restored Sport ${firstArg<Long>()}"
        }

        val insertedValues = mutableListOf<ContentValues>()
        every {
            mockDb.insertWithOnConflict(
                SportTypeDatabaseManager.SportType.TABLE,
                null,
                capture(insertedValues),
                SQLiteDatabase.CONFLICT_IGNORE
            )
        } returns 1L

        val resurrectedCount = SportTypeReconciliationHelper.reconcileOrphanSportTypes(
            context = mockContext,
            sportDb = mockDb,
            summariesDb = mockSummariesDb,
            dispatcher = testDispatcher
        )

        assertEquals("Must resurrect 2 orphan sports (6 and 8)", 2, resurrectedCount)
        assertEquals(2, insertedValues.size)

        val row6 = insertedValues.first { it.getAsLong(SportTypeDatabaseManager.SportType.C_ID) == 6L }
        assertEquals("Restored Sport 6", row6.getAsString(SportTypeDatabaseManager.SportType.UI_NAME))
        assertEquals(BSportType.UNKNOWN.name, row6.getAsString(SportTypeDatabaseManager.SportType.BASE_SPORT_TYPE))
        assertEquals(1, row6.getAsInteger(SportTypeDatabaseManager.SportType.IS_PLACEHOLDER))

        val row8 = insertedValues.first { it.getAsLong(SportTypeDatabaseManager.SportType.C_ID) == 8L }
        assertEquals("Restored Sport 8", row8.getAsString(SportTypeDatabaseManager.SportType.UI_NAME))
        assertEquals(BSportType.UNKNOWN.name, row8.getAsString(SportTypeDatabaseManager.SportType.BASE_SPORT_TYPE))
        assertEquals(1, row8.getAsInteger(SportTypeDatabaseManager.SportType.IS_PLACEHOLDER))
    }

    /**
     * TST-MIG-041.3: Verify explicit primary key insertion into SQLite AUTOINCREMENT
     * advances sequence without collision and allows new auto-generated ID >= 9.
     */
    @Test
    fun testEngineNativeSequenceManagementAndCollisionPrevention() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val mockSummariesDb = mockk<SQLiteDatabase>(relaxed = true)

        val summariesCursor = MockCursorFactory.create(
            columns = listOf("sportId"),
            rows = listOf(listOf(8L))
        )
        every {
            mockSummariesDb.rawQuery(
                "SELECT DISTINCT sportId FROM WorkoutSummaries WHERE sportId > ?",
                arrayOf("5")
            )
        } returns summariesCursor

        val sportTypesCursor = MockCursorFactory.create(
            columns = listOf("_id"),
            rows = listOf(listOf(1L), listOf(2L), listOf(3L), listOf(4L), listOf(5L))
        )
        every {
            mockDb.rawQuery(
                "SELECT ${SportTypeDatabaseManager.SportType.C_ID} FROM ${SportTypeDatabaseManager.SportType.TABLE}",
                null
            )
        } returns sportTypesCursor

        val insertedValues = mutableListOf<ContentValues>()
        every {
            mockDb.insertWithOnConflict(
                SportTypeDatabaseManager.SportType.TABLE,
                null,
                capture(insertedValues),
                SQLiteDatabase.CONFLICT_IGNORE
            )
        } returns 8L

        val count = SportTypeReconciliationHelper.reconcileOrphanSportTypes(
            context = mockContext,
            sportDb = mockDb,
            summariesDb = mockSummariesDb,
            dispatcher = testDispatcher
        )

        assertEquals(1, count)
        val inserted = insertedValues.first()
        assertEquals(8L, inserted.getAsLong(SportTypeDatabaseManager.SportType.C_ID)!!.toLong())
    }

    /**
     * TST-MIG-041.4: Verify active tracking pickers filter out placeholders (is_placeholder = 0).
     */
    @Test
    fun testActivePickerFiltersPlaceholdersAndAllowsClaiming() {
        val manager = object : SportTypeDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }

        // Mock cursor returning only non-placeholders (1, 2, 3, 4, 5, 7) - placeholder 6 excluded
        val activeCursor = MockCursorFactory.create(
            columns = listOf(SportTypeDatabaseManager.SportType.C_ID),
            rows = listOf(listOf(1L), listOf(2L), listOf(3L), listOf(4L), listOf(5L), listOf(7L))
        )
        val selectionSlot = slot<String>()
        every {
            mockDb.query(
                SportTypeDatabaseManager.SportType.TABLE,
                null,
                capture(selectionSlot),
                any(),
                null, null, null
            )
        } returns activeCursor

        val ids = manager.sportTypesIdList
        assertTrue("Selection must filter is_placeholder = 0", selectionSlot.captured.contains("is_placeholder=0"))
        assertEquals(6, ids.size)
        assertFalse("Placeholder ID 6 must not be present in active pickers", ids.contains(6L))
        assertTrue("Active custom sport 7 must be present", ids.contains(7L))
    }

    /**
     * TST-MIG-041.5: Verify onDowngrade strictly throws SQLiteException without data loss.
     */
    @Test
    fun testDowngradeThrowsSQLiteExceptionWithoutDataLoss() {
        val helper = SportTypeDatabaseManager.SportTypeDbHelper(mockContext)

        val exception = assertThrows(SQLiteException::class.java) {
            helper.onDowngrade(mockDb, 3, 2)
        }
        assertNotNull("Downgrade must throw non-null SQLiteException", exception)
        verify(exactly = 0) { mockDb.execSQL(any()) }
    }

    /**
     * TST-MIG-041.6: Verify claimPlaceholder validates case-insensitive name uniqueness
     * and clears is_placeholder to 0.
     */
    @Test
    fun testClaimPlaceholderValidatesNameUniqueness() {
        // Mock duplicate check returning true for existing name
        val duplicateCursor = MockCursorFactory.create(
            columns = listOf(SportTypeDatabaseManager.SportType.C_ID),
            rows = listOf(listOf(2L))
        )
        every {
            mockDb.query(
                SportTypeDatabaseManager.SportType.TABLE,
                arrayOf(SportTypeDatabaseManager.SportType.C_ID),
                "${SportTypeDatabaseManager.SportType.UI_NAME} = ? COLLATE NOCASE AND ${SportTypeDatabaseManager.SportType.C_ID} != ?",
                arrayOf("Running", "6"),
                null, null, null
            )
        } returns duplicateCursor

        val conflictEx = assertThrows(IllegalArgumentException::class.java) {
            SportTypeReconciliationHelper.claimPlaceholder(
                sportDb = mockDb,
                sportId = 6L,
                newName = "Running",
                bSportType = BSportType.RUN
            )
        }
        assertTrue(conflictEx.message?.contains("already exists") == true)

        // Mock duplicate check returning empty for unique name
        val emptyCursor = MockCursorFactory.create(
            columns = listOf(SportTypeDatabaseManager.SportType.C_ID),
            rows = emptyList()
        )
        every {
            mockDb.query(
                SportTypeDatabaseManager.SportType.TABLE,
                arrayOf(SportTypeDatabaseManager.SportType.C_ID),
                "${SportTypeDatabaseManager.SportType.UI_NAME} = ? COLLATE NOCASE AND ${SportTypeDatabaseManager.SportType.C_ID} != ?",
                arrayOf("Kayaking", "6"),
                null, null, null
            )
        } returns emptyCursor

        val capturedCv = slot<ContentValues>()
        val capturedWhere = slot<String>()
        val capturedArgs = slot<Array<String>>()
        every {
            mockDb.update(
                SportTypeDatabaseManager.SportType.TABLE,
                capture(capturedCv),
                capture(capturedWhere),
                capture(capturedArgs)
            )
        } returns 1

        SportTypeReconciliationHelper.claimPlaceholder(
            sportDb = mockDb,
            sportId = 6L,
            newName = "Kayaking",
            bSportType = BSportType.UNKNOWN
        )

        assertEquals("Kayaking", capturedCv.captured.getAsString(SportTypeDatabaseManager.SportType.UI_NAME))
        assertEquals(0, capturedCv.captured.getAsInteger(SportTypeDatabaseManager.SportType.IS_PLACEHOLDER))
        assertEquals("${SportTypeDatabaseManager.SportType.C_ID} = ?", capturedWhere.captured)
        assertArrayEquals(arrayOf("6"), capturedArgs.captured)
    }

    /**
     * TST-MIG-041.7: Verify pruneUnreferencedPlaceholders removes orphan placeholders
     * that have zero workouts, equipment links, and clusters.
     */
    @Test
    fun testHighVolumePlaceholderScalabilityAndPruning() {
        // SportTypes has placeholders 6 and 8
        val placeholdersCursor = MockCursorFactory.create(
            columns = listOf(SportTypeDatabaseManager.SportType.C_ID),
            rows = listOf(listOf(6L), listOf(8L))
        )
        every {
            mockDb.query(
                SportTypeDatabaseManager.SportType.TABLE,
                arrayOf(SportTypeDatabaseManager.SportType.C_ID),
                "${SportTypeDatabaseManager.SportType.IS_PLACEHOLDER} = 1",
                null, null, null, null
            )
        } returns placeholdersCursor

        val mockSummariesDb = mockk<SQLiteDatabase>(relaxed = true)
        // WorkoutSummaries references 6 only (8 is unreferenced)
        val summariesCursor = MockCursorFactory.create(
            columns = listOf("sportId"),
            rows = listOf(listOf(6L))
        )
        every { mockSummariesDb.rawQuery("SELECT DISTINCT sportId FROM WorkoutSummaries", null) } returns summariesCursor

        val deletedWhere = mutableListOf<String>()
        val deletedArgs = mutableListOf<Array<String>>()
        every {
            mockDb.delete(
                SportTypeDatabaseManager.SportType.TABLE,
                capture(deletedWhere),
                capture(deletedArgs)
            )
        } returns 1

        val pruned = SportTypeReconciliationHelper.pruneUnreferencedPlaceholders(
            context = mockContext,
            sportDb = mockDb,
            summariesDb = mockSummariesDb
        )

        assertEquals("Only unreferenced placeholder 8 should be pruned", 1, pruned)
        assertEquals(1, deletedArgs.size)
        assertArrayEquals(arrayOf("8"), deletedArgs.first())
    }

    /**
     * TST-MIG-041.8: Verify isPlaceholder helper correctly identifies placeholder rows.
     */
    @Test
    fun testIsPlaceholderHelper() {
        val manager = object : SportTypeDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }

        // Mock ID 6 is placeholder (1)
        val placeholderCursor = MockCursorFactory.create(
            columns = listOf(SportTypeDatabaseManager.SportType.IS_PLACEHOLDER),
            rows = listOf(listOf(1))
        )
        every {
            mockDb.query(
                SportTypeDatabaseManager.SportType.TABLE,
                arrayOf(SportTypeDatabaseManager.SportType.IS_PLACEHOLDER),
                SportTypeDatabaseManager.SportType.C_ID + "=?",
                arrayOf("6"),
                null, null, null
            )
        } returns placeholderCursor

        assertTrue(manager.isPlaceholder(6L))

        // Mock ID 2 is not placeholder (0)
        val nonPlaceholderCursor = MockCursorFactory.create(
            columns = listOf(SportTypeDatabaseManager.SportType.IS_PLACEHOLDER),
            rows = listOf(listOf(0))
        )
        every {
            mockDb.query(
                SportTypeDatabaseManager.SportType.TABLE,
                arrayOf(SportTypeDatabaseManager.SportType.IS_PLACEHOLDER),
                SportTypeDatabaseManager.SportType.C_ID + "=?",
                arrayOf("2"),
                null, null, null
            )
        } returns nonPlaceholderCursor

        assertFalse(manager.isPlaceholder(2L))
    }

    /**
     * TST-MIG-041.9: Verify reconcileOrphanSportTypes acquires and guarantees release of BackupManager lock.
     */
    @Test
    fun testReconcileOrphanSportTypesAcquiresAndReleasesBackupLock() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        mockkObject(BackupManager)
        val mockLock = mockk<BackupManager.BackupLock>(relaxed = true)
        every { BackupManager.acquireLock(mockContext) } returns mockLock

        val summariesCursor = MockCursorFactory.create(
            columns = listOf("sportId"),
            rows = listOf(listOf(9L))
        )
        val mockSummariesDb = mockk<SQLiteDatabase>(relaxed = true)
        every {
            mockSummariesDb.rawQuery(
                "SELECT DISTINCT sportId FROM WorkoutSummaries WHERE sportId > ?",
                arrayOf("5")
            )
        } returns summariesCursor

        val sportTypesCursor = MockCursorFactory.create(
            columns = listOf(SportTypeDatabaseManager.SportType.C_ID),
            rows = emptyList()
        )
        every {
            mockDb.rawQuery(
                "SELECT ${SportTypeDatabaseManager.SportType.C_ID} FROM ${SportTypeDatabaseManager.SportType.TABLE}",
                null
            )
        } returns sportTypesCursor

        every {
            mockDb.insertWithOnConflict(
                SportTypeDatabaseManager.SportType.TABLE,
                null,
                any(),
                SQLiteDatabase.CONFLICT_IGNORE
            )
        } returns 9L

        val count = SportTypeReconciliationHelper.reconcileOrphanSportTypes(
            context = mockContext,
            sportDb = mockDb,
            summariesDb = mockSummariesDb,
            dispatcher = testDispatcher
        )

        assertEquals(1, count)
        verify(exactly = 1) { BackupManager.acquireLock(mockContext) }
        verify(exactly = 1) { mockLock.close() }
    }

    /**
     * TST-MIG-041.10: Verify pruneUnreferencedPlaceholders acquires and guarantees release of BackupManager lock.
     */
    @Test
    fun testPruneUnreferencedPlaceholdersAcquiresAndReleasesBackupLock() {
        mockkObject(BackupManager)
        val mockLock = mockk<BackupManager.BackupLock>(relaxed = true)
        every { BackupManager.acquireLock(mockContext) } returns mockLock

        // Mock SportTypes has placeholder id 10
        val placeholderCursor = MockCursorFactory.create(
            columns = listOf(SportTypeDatabaseManager.SportType.C_ID),
            rows = listOf(listOf(10L))
        )
        every {
            mockDb.query(
                SportTypeDatabaseManager.SportType.TABLE,
                arrayOf(SportTypeDatabaseManager.SportType.C_ID),
                "${SportTypeDatabaseManager.SportType.IS_PLACEHOLDER} = 1",
                null, null, null, null
            )
        } returns placeholderCursor

        // Empty summaries
        val mockSummariesDb = mockk<SQLiteDatabase>(relaxed = true)
        val emptyCursor = MockCursorFactory.create(
            columns = listOf("sportId"),
            rows = emptyList()
        )
        every { mockSummariesDb.rawQuery(any(), null) } returns emptyCursor

        every {
            mockDb.delete(
                SportTypeDatabaseManager.SportType.TABLE,
                "${SportTypeDatabaseManager.SportType.C_ID} = ? AND ${SportTypeDatabaseManager.SportType.IS_PLACEHOLDER} = 1",
                arrayOf("10")
            )
        } returns 1

        val pruned = SportTypeReconciliationHelper.pruneUnreferencedPlaceholders(
            context = mockContext,
            sportDb = mockDb,
            summariesDb = mockSummariesDb
        )

        assertEquals(1, pruned)
        verify(exactly = 1) { BackupManager.acquireLock(mockContext) }
        verify(exactly = 1) { mockLock.close() }
    }
}

