package com.atrainingtracker.util

import android.database.Cursor
import android.database.sqlite.SQLiteException
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite for [CursorUtils] enforcing REQ-DAT-001 / TST-DAT-001.
 *
 * Verifies bounds checking, null-safety, canonical fallback return values,
 * and deterministic exception propagation.
 */
class CursorUtilsTest {

    private val mockCursor = mockk<Cursor>(relaxed = true)

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun testMapFirst_withValidRow_returnsMappedValue() {
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getString(0) } returns "result_value"

        val result = CursorUtils.mapFirst(mockCursor, "default_value") { cursor ->
            cursor.getString(0)
        }

        assertEquals("result_value", result)
        verify(exactly = 1) { mockCursor.moveToFirst() }
    }

    @Test
    fun testMapFirst_withEmptyCursor_returnsDefaultValue() {
        every { mockCursor.moveToFirst() } returns false

        var mapperInvoked = false
        val result = CursorUtils.mapFirst(mockCursor, "default_value") { cursor ->
            mapperInvoked = true
            cursor.getString(0)
        }

        assertEquals("default_value", result)
        assertFalse(mapperInvoked)
        verify(exactly = 1) { mockCursor.moveToFirst() }
    }

    @Test
    fun testMapFirst_withNullCursor_returnsDefaultValue() {
        var mapperInvoked = false
        val result = CursorUtils.mapFirst<String>(null, "fallback") {
            mapperInvoked = true
            "unexpected"
        }

        assertEquals("fallback", result)
        assertFalse(mapperInvoked)
    }

    @Test
    fun testMapFirst_whenExceptionThrown_propagatesCleanly() {
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getString(0) } throws SQLiteException("Database corrupted")

        assertThrows(SQLiteException::class.java) {
            CursorUtils.mapFirst(mockCursor, "fallback") { cursor ->
                cursor.getString(0)
            }
        }
    }

    @Test
    fun testMapLast_withValidRow_returnsMappedValue() {
        every { mockCursor.moveToLast() } returns true
        every { mockCursor.getLong(0) } returns 42L

        val result = CursorUtils.mapLast(mockCursor, -1L) { cursor ->
            cursor.getLong(0)
        }

        assertEquals(42L, result)
        verify(exactly = 1) { mockCursor.moveToLast() }
    }

    @Test
    fun testMapLast_withEmptyCursor_returnsDefaultValue() {
        every { mockCursor.moveToLast() } returns false

        var mapperInvoked = false
        val result = CursorUtils.mapLast(mockCursor, -1L) { cursor ->
            mapperInvoked = true
            cursor.getLong(0)
        }

        assertEquals(-1L, result)
        assertFalse(mapperInvoked)
    }

    @Test
    fun testMapLast_withNullCursor_returnsDefaultValue() {
        var mapperInvoked = false
        val result = CursorUtils.mapLast<Long>(null, -1L) {
            mapperInvoked = true
            999L
        }

        assertEquals(-1L, result)
        assertFalse(mapperInvoked)
    }

    @Test
    fun testMapList_withMultipleRows_returnsMappedList() {
        every { mockCursor.moveToNext() } returnsMany listOf(true, true, true, false)
        every { mockCursor.getString(0) } returnsMany listOf("row1", "row2", "row3")

        val result = CursorUtils.mapList(mockCursor) { cursor ->
            cursor.getString(0)
        }

        assertEquals(listOf("row1", "row2", "row3"), result)
        verify(exactly = 4) { mockCursor.moveToNext() }
    }

    @Test
    fun testMapList_withEmptyCursor_returnsEmptyList() {
        every { mockCursor.moveToNext() } returns false

        val result = CursorUtils.mapList(mockCursor) { cursor ->
            cursor.getString(0)
        }

        assertTrue(result.isEmpty())
        verify(exactly = 1) { mockCursor.moveToNext() }
    }

    @Test
    fun testMapList_withNullCursor_returnsEmptyList() {
        val result = CursorUtils.mapList<String>(null) {
            "mapped"
        }
        assertTrue(result.isEmpty())
    }

    @Test
    fun testMapList_whenExceptionThrown_propagatesCleanly() {
        every { mockCursor.moveToNext() } returns true
        every { mockCursor.getString(0) } throws SQLiteException("Disk I/O error")

        assertThrows(SQLiteException::class.java) {
            CursorUtils.mapList(mockCursor) { cursor ->
                cursor.getString(0)
            }
        }
    }

    @Test
    fun testMoveToFirstOrNull_successAndFailure() {
        every { mockCursor.moveToFirst() } returns true
        val valid = CursorUtils.moveToFirstOrNull(mockCursor)
        assertSame(mockCursor, valid)

        every { mockCursor.moveToFirst() } returns false
        val empty = CursorUtils.moveToFirstOrNull(mockCursor)
        assertNull(empty)

        assertNull(CursorUtils.moveToFirstOrNull(null))
    }

    @Test
    fun testMoveToLastOrNull_successAndFailure() {
        every { mockCursor.moveToLast() } returns true
        val valid = CursorUtils.moveToLastOrNull(mockCursor)
        assertSame(mockCursor, valid)

        every { mockCursor.moveToLast() } returns false
        val empty = CursorUtils.moveToLastOrNull(mockCursor)
        assertNull(empty)

        assertNull(CursorUtils.moveToLastOrNull(null))
    }
}
