package com.atrainingtracker.testutil

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite verifying [MockCursorFactory] fidelity (REQ-PRO-023, TST-PRO-016).
 */
class MockCursorFactoryTest {

    @Test
    fun testEmptyCursor_initialStateAndNavigation() {
        val cursor = MockCursorFactory.create(listOf("id", "name"), emptyList())

        assertEquals(0, cursor.count)
        assertEquals(2, cursor.columnCount)
        assertArrayEquals(arrayOf("id", "name"), cursor.columnNames)
        assertArrayEquals(arrayOf("id", "name"), cursor.getColumnNames())
        assertEquals("id", cursor.getColumnName(0))
        assertEquals("name", cursor.getColumnName(1))

        assertFalse("moveToFirst should return false for empty cursor", cursor.moveToFirst())
        assertFalse("moveToNext should return false for empty cursor", cursor.moveToNext())
        assertTrue("isAfterLast should be true for empty cursor", cursor.isAfterLast)
        assertFalse("isClosed should initially be false", cursor.isClosed)

        cursor.close()
        assertTrue("isClosed should be true after close()", cursor.isClosed)
    }

    @Test
    fun testColumnResolution_caseSensitivityAndMissingColumn() {
        val columns = listOf("C_ID", "UserName", "IS_ACTIVE")
        val cursor = MockCursorFactory.create(columns, listOf(listOf(1, "Alice", true)))

        // Exact match
        assertEquals(0, cursor.getColumnIndex("C_ID"))
        assertEquals(1, cursor.getColumnIndex("UserName"))
        assertEquals(2, cursor.getColumnIndex("IS_ACTIVE"))

        // Case-insensitive fallback
        assertEquals(0, cursor.getColumnIndex("c_id"))
        assertEquals(1, cursor.getColumnIndex("username"))
        assertEquals(2, cursor.getColumnIndex("is_active"))

        // Missing column returns -1
        assertEquals(-1, cursor.getColumnIndex("non_existent"))

        // getColumnIndexOrThrow throws on missing column
        try {
            cursor.getColumnIndexOrThrow("non_existent")
            fail("Expected IllegalArgumentException for non-existent column")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("non_existent") == true)
        }
    }

    @Test
    fun testRowIteration_andPositionalFlags() {
        val columns = listOf("id", "val")
        val rows = listOf(
            listOf(10, "Row 1"),
            listOf(20, "Row 2"),
            listOf(30, "Row 3")
        )
        val cursor = MockCursorFactory.create(columns, rows)

        assertEquals(3, cursor.count)
        assertEquals(-1, cursor.position)
        assertTrue(cursor.isBeforeFirst)
        assertFalse(cursor.isFirst)

        // Move to first
        assertTrue(cursor.moveToFirst())
        assertEquals(0, cursor.position)
        assertTrue(cursor.isFirst)
        assertFalse(cursor.isLast)
        assertEquals(10, cursor.getInt(0))
        assertEquals("Row 1", cursor.getString(1))

        // Move to next (row 1)
        assertTrue(cursor.moveToNext())
        assertEquals(1, cursor.position)
        assertFalse(cursor.isFirst)
        assertFalse(cursor.isLast)
        assertEquals(20, cursor.getInt(0))

        // Move to next (row 2)
        assertTrue(cursor.moveToNext())
        assertEquals(2, cursor.position)
        assertFalse(cursor.isFirst)
        assertTrue(cursor.isLast)
        assertFalse(cursor.isAfterLast)
        assertEquals(30, cursor.getInt(0))

        // Move past last
        assertFalse(cursor.moveToNext())
        assertTrue(cursor.isAfterLast)

        // Move to previous
        assertTrue(cursor.moveToPrevious())
        assertEquals(2, cursor.position)

        // Move to position
        assertTrue(cursor.moveToPosition(1))
        assertEquals(1, cursor.position)
        assertEquals("Row 2", cursor.getString(1))

        assertFalse(cursor.moveToPosition(99))
    }

    @Test
    fun testDataTypeExtraction_andNullHandling() {
        val columns = listOf("col_int", "col_long", "col_double", "col_float", "col_str", "col_null", "col_blob")
        val blobData = "test-bytes".toByteArray(Charsets.UTF_8)
        val rows = listOf(
            listOf(42, 1234567890123L, 3.14159, 2.718f, "Hello World", null, blobData)
        )

        val cursor = MockCursorFactory.create(columns, rows)
        assertTrue(cursor.moveToFirst())

        assertEquals(42, cursor.getInt(0))
        assertEquals(1234567890123L, cursor.getLong(1))
        assertEquals(3.14159, cursor.getDouble(2), 0.0001)
        assertEquals(2.718f, cursor.getFloat(3), 0.0001f)
        assertEquals("Hello World", cursor.getString(4))

        // Null checks
        assertFalse("Column 0 should not be null", cursor.isNull(0))
        assertTrue("Column 5 should be null", cursor.isNull(5))
        assertNull(cursor.getString(5))
        assertEquals(0, cursor.getInt(5))
        assertEquals(0L, cursor.getLong(5))

        // Blob check
        assertArrayEquals(blobData, cursor.getBlob(6))
    }
}
