package com.atrainingtracker.trainingtracker.segments

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager.Segments
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying SegmentsDatabaseManager.updateSegmentPrTime (REQ-EXP-010, TST-EXP-007, ATT-912).
 */
class SegmentsDatabaseManagerPrTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase
    private lateinit var manager: SegmentsDatabaseManager

    private val contentValueStores = java.util.Collections.synchronizedMap(java.util.IdentityHashMap<ContentValues, MutableMap<String, Any?>>())

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
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)
        every { mockDb.isOpen } returns true

        contentValueStores.clear()
        mockkConstructor(ContentValues::class)
        every { constructedWith<ContentValues>().put(any<String>(), any<Int>()) } answers {
            val cv = getRealInstance()
            contentValueStores.computeIfAbsent(cv) { mutableMapOf() }[firstArg<String>()] = secondArg<Int>()
        }
        every { constructedWith<ContentValues>().getAsInteger(any<String>()) } answers {
            val cv = getRealInstance()
            (contentValueStores[cv]?.get(firstArg<String>()) as? Number)?.toInt()
        }

        // Instantiate SegmentsDatabaseManager via private constructor and inject mockDb
        val constructor = SegmentsDatabaseManager::class.java.getDeclaredConstructor(Context::class.java)
        constructor.isAccessible = true
        manager = constructor.newInstance(mockContext)

        val dbField = SegmentsDatabaseManager::class.java.getDeclaredField("mDatabase")
        dbField.isAccessible = true
        dbField.set(manager, mockDb)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testUpdateSegmentPrTime_invalidSeconds_returnsFalseImmediately() {
        assertFalse(manager.updateSegmentPrTime(12345L, 0))
        assertFalse(manager.updateSegmentPrTime(12345L, -10))

        verify(exactly = 0) { mockDb.query(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { mockDb.update(any(), any(), any(), any()) }
    }

    @Test
    fun testUpdateSegmentPrTime_segmentNotInStarredSegments_returnsFalse() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToFirst() } returns false
        every { mockCursor.close() } returns Unit

        every {
            mockDb.query(
                Segments.TABLE_STARRED_SEGMENTS,
                arrayOf(Segments.PR_TIME),
                "${Segments.STRAVA_SEGMENT_ID}=?",
                arrayOf("12345"),
                null, null, null
            )
        } returns mockCursor

        val result = manager.updateSegmentPrTime(12345L, 60)

        assertFalse(result)
        verify { mockCursor.close() }
        verify(exactly = 0) { mockDb.update(any(), any(), any(), any()) }
    }

    @Test
    fun testUpdateSegmentPrTime_whenNewTimeIsSlowerOrEqual_doesNotUpdateAndReturnsFalse() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getColumnIndexOrThrow(Segments.PR_TIME) } returns 0
        every { mockCursor.getInt(0) } returns 100 // existing PR is 100 seconds
        every { mockCursor.close() } returns Unit

        every {
            mockDb.query(
                Segments.TABLE_STARRED_SEGMENTS,
                arrayOf(Segments.PR_TIME),
                "${Segments.STRAVA_SEGMENT_ID}=?",
                arrayOf("12345"),
                null, null, null
            )
        } returns mockCursor

        // Equal time (100s)
        val equalResult = manager.updateSegmentPrTime(12345L, 100)
        assertFalse(equalResult)

        // Slower time (110s)
        val slowerResult = manager.updateSegmentPrTime(12345L, 110)
        assertFalse(slowerResult)

        verify(exactly = 0) { mockDb.update(any(), any(), any(), any()) }
    }

    @Test
    fun testUpdateSegmentPrTime_whenNewTimeIsFaster_updatesDatabaseAndReturnsTrue() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getColumnIndexOrThrow(Segments.PR_TIME) } returns 0
        every { mockCursor.getInt(0) } returns 100 // existing PR is 100 seconds
        every { mockCursor.close() } returns Unit

        every {
            mockDb.query(
                Segments.TABLE_STARRED_SEGMENTS,
                arrayOf(Segments.PR_TIME),
                "${Segments.STRAVA_SEGMENT_ID}=?",
                arrayOf("12345"),
                null, null, null
            )
        } returns mockCursor

        val capturedCv = mutableListOf<ContentValues>()
        every {
            mockDb.update(
                eq(Segments.TABLE_STARRED_SEGMENTS),
                capture(capturedCv),
                eq("${Segments.STRAVA_SEGMENT_ID}=?"),
                eq(arrayOf("12345"))
            )
        } returns 1

        val result = manager.updateSegmentPrTime(12345L, 85)

        assertTrue(result)
        verify { mockCursor.close() }
        assertEquals(1, capturedCv.size)
        assertEquals(85, capturedCv[0].getAsInteger(Segments.PR_TIME))
    }

    @Test
    fun testUpdateSegmentPrTime_whenPreviousPrZero_updatesDatabaseAndReturnsTrue() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getColumnIndexOrThrow(Segments.PR_TIME) } returns 0
        every { mockCursor.getInt(0) } returns 0 // previously no PR (0)
        every { mockCursor.close() } returns Unit

        every {
            mockDb.query(
                Segments.TABLE_STARRED_SEGMENTS,
                arrayOf(Segments.PR_TIME),
                "${Segments.STRAVA_SEGMENT_ID}=?",
                arrayOf("99999"),
                null, null, null
            )
        } returns mockCursor

        val capturedCv = mutableListOf<ContentValues>()
        every {
            mockDb.update(
                eq(Segments.TABLE_STARRED_SEGMENTS),
                capture(capturedCv),
                eq("${Segments.STRAVA_SEGMENT_ID}=?"),
                eq(arrayOf("99999"))
            )
        } returns 1

        val result = manager.updateSegmentPrTime(99999L, 142)

        assertTrue(result)
        verify { mockCursor.close() }
        assertEquals(1, capturedCv.size)
        assertEquals(142, capturedCv[0].getAsInteger(Segments.PR_TIME))
    }
}
