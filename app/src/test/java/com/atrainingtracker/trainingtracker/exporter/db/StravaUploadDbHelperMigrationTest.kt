package com.atrainingtracker.trainingtracker.exporter.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.testutil.MockCursorFactory
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying StravaUploadDbHelper database version 6 migration,
 * deterministic minimization in onUpgrade, onDowngrade safety, and CQS invariance (REQ-EXP-013, TST-EXP-010, ATT-1190).
 */
class StravaUploadDbHelperMigrationTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase
    private val cvStores = java.util.Collections.synchronizedMap(java.util.IdentityHashMap<ContentValues, MutableMap<String, Any?>>())

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
        every { constructedWith<ContentValues>().getAsString(any<String>()) } answers {
            val cv = getRealInstance()
            cvStores[cv]?.get(firstArg<String>()) as? String
        }
        every { constructedWith<ContentValues>().get(any<String>()) } answers {
            val cv = getRealInstance()
            cvStores[cv]?.get(firstArg<String>())
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testDbVersionIsSix() {
        assertEquals("DB_VERSION must be bumped to 6 for deterministic minimization migration", 6, StravaUploadDbHelper.DB_VERSION)
    }

    @Test
    fun testOnUpgradeFromVersion5To6_migratesLegacyRecords() {
        val helper = StravaUploadDbHelper(mockContext)

        val legacyJson1 = """
            {
              "id": 1001,
              "athlete": { "id": 123 },
              "kudos_count": 5,
              "segment_efforts": [
                { "name": "Sprint", "elapsed_time": 60, "pr_rank": 1 }
              ]
            }
        """.trimIndent()

        val legacyJson2 = """
            {
              "id": 1002,
              "kudos_count": 0,
              "segment_efforts": []
            }
        """.trimIndent()

        val mockCursor = MockCursorFactory.create(
            columns = listOf(StravaUploadDbHelper.C_ID, StravaUploadDbHelper.STRAVA_ACTIVITY_DATA),
            rows = listOf(
                listOf(1L, legacyJson1),
                listOf(2L, legacyJson2)
            )
        )

        every { mockDb.rawQuery(any(), null) } returns mockCursor

        val capturedValuesByRowId = mutableMapOf<Long, String?>()
        every { mockDb.update(StravaUploadDbHelper.TABLE, any(), any(), any()) } answers {
            val cv = secondArg<ContentValues>()
            val whereArgs = lastArg<Array<String>>()
            val rowId = whereArgs[0].toLong()
            capturedValuesByRowId[rowId] = cvStores[cv]?.get(StravaUploadDbHelper.STRAVA_ACTIVITY_DATA) as? String
            1
        }

        helper.onUpgrade(mockDb, 5, 6)

        assertEquals(2, capturedValuesByRowId.size)

        // Verify row 1 migrated to minimized v:2
        val json1 = capturedValuesByRowId[1L]
        assertNotNull(json1)
        val obj1 = org.json.JSONObject(json1!!)
        assertEquals(2, obj1.getInt("v"))
        assertEquals(1001L, obj1.getLong("id"))
        assertFalse(obj1.has("athlete"))
        assertFalse(obj1.has("kudos_count"))
        assertEquals(1, obj1.getJSONArray("segment_efforts").length())

        // Verify row 2 migrated
        val json2 = capturedValuesByRowId[2L]
        assertNotNull(json2)
        val obj2 = org.json.JSONObject(json2!!)
        assertEquals(2, obj2.getInt("v"))
        assertEquals(1002L, obj2.getLong("id"))
    }

    @Test
    fun testOnUpgradeFromVersion5To6_handlesCorruptRecordsSafely() {
        val helper = StravaUploadDbHelper(mockContext)

        val corruptJson = "{ truncated_invalid_json..."

        val mockCursor = MockCursorFactory.create(
            columns = listOf(StravaUploadDbHelper.C_ID, StravaUploadDbHelper.STRAVA_ACTIVITY_DATA),
            rows = listOf(
                listOf(3L, corruptJson)
            )
        )

        every { mockDb.rawQuery(any(), null) } returns mockCursor

        val capturedValuesByRowId = mutableMapOf<Long, String?>()
        every { mockDb.update(StravaUploadDbHelper.TABLE, any(), any(), any()) } answers {
            val cv = secondArg<ContentValues>()
            val whereArgs = lastArg<Array<String>>()
            val rowId = whereArgs[0].toLong()
            capturedValuesByRowId[rowId] = cvStores[cv]?.get(StravaUploadDbHelper.STRAVA_ACTIVITY_DATA) as? String
            1
        }

        // Must not throw exception
        helper.onUpgrade(mockDb, 5, 6)

        assertEquals(1, capturedValuesByRowId.size)
        assertNull("Corrupt JSON row must be cleared to null without crashing migration", capturedValuesByRowId[3L])
    }

    @Test
    fun testOnDowngrade_doesNotThrow() {
        val helper = StravaUploadDbHelper(mockContext)
        // Should execute smoothly and log downgrade warning without crashing
        helper.onDowngrade(mockDb, 6, 5)
    }

    @Test
    fun testCheckpointWal_executesPragma() {
        val helper = spyk(StravaUploadDbHelper(mockContext))
        every { helper.writableDatabase } returns mockDb

        val pragmaCursor = MockCursorFactory.create(
            columns = listOf("busy", "log", "checkpointed"),
            rows = listOf(listOf(0, 0, 0))
        )
        every { mockDb.rawQuery("PRAGMA wal_checkpoint(FULL)", null) } returns pragmaCursor

        helper.checkpointWal()

        verify { mockDb.rawQuery("PRAGMA wal_checkpoint(FULL)", null) }
    }
}
