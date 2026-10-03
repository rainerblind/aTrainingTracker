package com.atrainingtracker.testutil

import android.database.Cursor
import io.mockk.every
import io.mockk.mockk

/**
 * Reusable test fixture factory to construct high-fidelity mock [Cursor] instances
 * from tabular column and row definitions (REQ-PRO-023, TST-PRO-016).
 *
 * Eliminates repetitive MockK answer boilerplate across JVM unit tests.
 */
object MockCursorFactory {

    /**
     * Creates a mock [Cursor] populated with [columns] and optional [rows].
     *
     * Supports:
     * - Column index lookup via [Cursor.getColumnIndex] and [Cursor.getColumnIndexOrThrow] (case-insensitive fallback).
     * - Navigation via [Cursor.moveToFirst], [Cursor.moveToNext], [Cursor.moveToPrevious], [Cursor.moveToPosition].
     * - Positional flags: [Cursor.getPosition], [Cursor.isBeforeFirst], [Cursor.isAfterLast], [Cursor.isFirst], [Cursor.isLast].
     * - Counts: [Cursor.getCount], [Cursor.getColumnCount], [Cursor.getColumnNames], [Cursor.getColumnName].
     * - Typed getters: [Cursor.getInt], [Cursor.getLong], [Cursor.getDouble], [Cursor.getFloat], [Cursor.getShort], [Cursor.getString], [Cursor.getBlob].
     * - Nullability checks: [Cursor.isNull].
     * - Safe no-op [Cursor.close].
     */
    fun create(
        columns: List<String>,
        rows: List<List<Any?>> = emptyList()
    ): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)

        // Internal mutable position pointer (-1 is before first row)
        var position = -1
        var isClosed = false

        // Column indexing helpers (case-insensitive lookup with exact priority)
        fun findColumnIndex(name: String): Int {
            val exact = columns.indexOf(name)
            if (exact != -1) return exact
            return columns.indexOfFirst { it.equals(name, ignoreCase = true) }
        }

        fun currentCell(colIdx: Int): Any? {
            if (position < 0 || position >= rows.size) {
                throw IllegalStateException("Cursor position $position is out of bounds [0, ${rows.size})")
            }
            val row = rows[position]
            if (colIdx < 0 || colIdx >= row.size) {
                throw IndexOutOfBoundsException("Column index $colIdx is out of bounds [0, ${row.size})")
            }
            return row[colIdx]
        }

        // Row count & column counts
        every { cursor.count } answers { rows.size }
        every { cursor.columnCount } answers { columns.size }
        every { cursor.columnNames } answers { columns.toTypedArray() }
        every { cursor.getColumnNames() } answers { columns.toTypedArray() }
        every { cursor.getColumnName(any()) } answers {
            val idx = firstArg<Int>()
            if (idx < 0 || idx >= columns.size) {
                throw IndexOutOfBoundsException("Column index $idx is out of bounds [0, ${columns.size})")
            }
            columns[idx]
        }

        // Column index lookups
        every { cursor.getColumnIndex(any()) } answers {
            findColumnIndex(firstArg<String>())
        }
        every { cursor.getColumnIndexOrThrow(any()) } answers {
            val colName = firstArg<String>()
            val idx = findColumnIndex(colName)
            if (idx == -1) {
                throw IllegalArgumentException("Column '$colName' does not exist in mock cursor")
            }
            idx
        }

        // Navigation & Position Tracking
        every { cursor.position } answers { position }
        every { cursor.moveToPosition(any()) } answers {
            val newPos = firstArg<Int>()
            if (newPos in rows.indices) {
                position = newPos
                true
            } else {
                position = if (newPos < 0) -1 else rows.size
                false
            }
        }
        every { cursor.moveToFirst() } answers {
            if (rows.isNotEmpty()) {
                position = 0
                true
            } else {
                position = 0
                false
            }
        }
        every { cursor.moveToNext() } answers {
            if (position < rows.size - 1) {
                position++
                true
            } else {
                position = rows.size
                false
            }
        }
        every { cursor.moveToPrevious() } answers {
            if (position > 0) {
                position--
                true
            } else {
                position = -1
                false
            }
        }
        every { cursor.moveToLast() } answers {
            if (rows.isNotEmpty()) {
                position = rows.size - 1
                true
            } else {
                position = 0
                false
            }
        }

        // Positional Queries
        every { cursor.isBeforeFirst } answers { position < 0 || rows.isEmpty() }
        every { cursor.isAfterLast } answers { position >= rows.size || rows.isEmpty() }
        every { cursor.isFirst } answers { rows.isNotEmpty() && position == 0 }
        every { cursor.isLast } answers { rows.isNotEmpty() && position == rows.size - 1 }

        // Nullability Check
        every { cursor.isNull(any()) } answers {
            val col = firstArg<Int>()
            currentCell(col) == null
        }

        // Typed Value Extraction
        every { cursor.getString(any()) } answers {
            val col = firstArg<Int>()
            currentCell(col)?.toString()
        }
        every { cursor.getInt(any()) } answers {
            val cell = currentCell(firstArg())
            when (cell) {
                null -> 0
                is Number -> cell.toInt()
                is Boolean -> if (cell) 1 else 0
                else -> cell.toString().toInt()
            }
        }
        every { cursor.getLong(any()) } answers {
            val cell = currentCell(firstArg())
            when (cell) {
                null -> 0L
                is Number -> cell.toLong()
                is Boolean -> if (cell) 1L else 0L
                else -> cell.toString().toLong()
            }
        }
        every { cursor.getDouble(any()) } answers {
            val cell = currentCell(firstArg())
            when (cell) {
                null -> 0.0
                is Number -> cell.toDouble()
                else -> cell.toString().toDouble()
            }
        }
        every { cursor.getFloat(any()) } answers {
            val cell = currentCell(firstArg())
            when (cell) {
                null -> 0.0f
                is Number -> cell.toFloat()
                else -> cell.toString().toFloat()
            }
        }
        every { cursor.getShort(any()) } answers {
            val cell = currentCell(firstArg())
            when (cell) {
                null -> 0.toShort()
                is Number -> cell.toShort()
                else -> cell.toString().toShort()
            }
        }
        every { cursor.getBlob(any()) } answers {
            val cell = currentCell(firstArg())
            when (cell) {
                null -> null
                is ByteArray -> cell
                else -> cell.toString().toByteArray(Charsets.UTF_8)
            }
        }

        // Lifecycle & State
        every { cursor.close() } answers { isClosed = true }
        every { cursor.isClosed } answers { isClosed }

        return cursor
    }
}
