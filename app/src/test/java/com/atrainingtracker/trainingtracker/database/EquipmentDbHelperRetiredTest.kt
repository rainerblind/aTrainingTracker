package com.atrainingtracker.trainingtracker.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.testutil.MockCursorFactory
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying EquipmentDbHelper retirement handling, SQLite queries,
 * updateEquipment, setEquipmentRetired, and isEquipmentRetired (REQ-UI-158, TST-UI-111, ATT-1118).
 * Demonstrates MockCursorFactory integration (REQ-PRO-023, TST-PRO-016).
 */
class EquipmentDbHelperRetiredTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase

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

        contentValueStores.clear()
        mockkConstructor(ContentValues::class)
        every { constructedWith<ContentValues>().put(any<String>(), any<String>()) } answers {
            val cv = getRealInstance()
            contentValueStores.computeIfAbsent(cv) { mutableMapOf() }[firstArg<String>()] = secondArg<String>()
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Int>()) } answers {
            val cv = getRealInstance()
            contentValueStores.computeIfAbsent(cv) { mutableMapOf() }[firstArg<String>()] = secondArg<Int>()
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Long>()) } answers {
            val cv = getRealInstance()
            contentValueStores.computeIfAbsent(cv) { mutableMapOf() }[firstArg<String>()] = secondArg<Long>()
        }
        every { constructedWith<ContentValues>().getAsString(any<String>()) } answers {
            val cv = getRealInstance()
            contentValueStores[cv]?.get(firstArg<String>())?.toString()
        }
        every { constructedWith<ContentValues>().getAsInteger(any<String>()) } answers {
            val cv = getRealInstance()
            (contentValueStores[cv]?.get(firstArg<String>()) as? Number)?.toInt()
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testEquipmentData_isRetiredDefaultAndExplicit() {
        val defaultData = EquipmentDbHelper.EquipmentData(
            1L, "Bike", BSportType.BIKE, 1, "StravaBike", "b1"
        )
        assertFalse("Default constructor should set isRetired to false", defaultData.isRetired)

        val retiredData = EquipmentDbHelper.EquipmentData(
            2L, "Old Bike", BSportType.BIKE, 1, "OldStrava", "b2", true
        )
        assertTrue("Explicit constructor should store isRetired as true", retiredData.isRetired)

        val activeData = EquipmentDbHelper.EquipmentData(
            3L, "New Bike", BSportType.BIKE, 1, "NewStrava", "b3", false
        )
        assertFalse("Explicit constructor should store isRetired as false", activeData.isRetired)
    }

    @Test
    fun testUpdateEquipment_withIsRetiredTrue_persistsRetiredOne() {
        val testHelper = object : EquipmentDbHelper(mockContext) {
            override fun getWritableDatabase(): SQLiteDatabase = mockDb
        }

        val capturedValues = slot<ContentValues>()
        every {
            mockDb.update(
                EquipmentDbHelper.EQUIPMENT,
                capture(capturedValues),
                "${EquipmentDbHelper.C_ID}=?",
                arrayOf("42")
            )
        } returns 1

        testHelper.updateEquipment(42L, "Retired Road Bike", 3, listOf(101L, 102L), true)

        verify { mockDb.beginTransaction() }
        verify { mockDb.setTransactionSuccessful() }
        verify { mockDb.endTransaction() }

        val cv = capturedValues.captured
        val storedRetired = contentValueStores[cv]?.get(EquipmentDbHelper.RETIRED) as? Int
        assertEquals(1, storedRetired)
    }

    @Test
    fun testSetEquipmentRetired_persistsValue() {
        val testHelper = object : EquipmentDbHelper(mockContext) {
            override fun getWritableDatabase(): SQLiteDatabase = mockDb
        }

        val capturedValues = slot<ContentValues>()
        every {
            mockDb.update(
                EquipmentDbHelper.EQUIPMENT,
                capture(capturedValues),
                "${EquipmentDbHelper.C_ID}=?",
                arrayOf("10")
            )
        } returns 1

        testHelper.setEquipmentRetired(10L, true)

        val cv = capturedValues.captured
        val storedRetired = contentValueStores[cv]?.get(EquipmentDbHelper.RETIRED) as? Int
        assertEquals(1, storedRetired)
    }

    @Test
    fun testIsEquipmentRetired_returnsTrueWhenColumnIsOne() {
        val mockCursor = MockCursorFactory.create(
            columns = listOf(EquipmentDbHelper.RETIRED),
            rows = listOf(listOf(1))
        )

        val testHelper = object : EquipmentDbHelper(mockContext) {
            override fun getReadableDatabase(): SQLiteDatabase = mockDb
        }

        every {
            mockDb.query(
                EquipmentDbHelper.EQUIPMENT,
                arrayOf(EquipmentDbHelper.RETIRED),
                "${EquipmentDbHelper.C_ID}=?",
                arrayOf("15"),
                null, null, null
            )
        } returns mockCursor

        assertTrue(testHelper.isEquipmentRetired(15L))
        assertTrue(mockCursor.isClosed)
    }

    @Test
    fun testIsEquipmentRetired_returnsFalseWhenColumnIsZero() {
        val mockCursor = MockCursorFactory.create(
            columns = listOf(EquipmentDbHelper.RETIRED),
            rows = listOf(listOf(0))
        )

        val testHelper = object : EquipmentDbHelper(mockContext) {
            override fun getReadableDatabase(): SQLiteDatabase = mockDb
        }

        every {
            mockDb.query(
                EquipmentDbHelper.EQUIPMENT,
                arrayOf(EquipmentDbHelper.RETIRED),
                "${EquipmentDbHelper.C_ID}=?",
                arrayOf("16"),
                null, null, null
            )
        } returns mockCursor

        assertFalse(testHelper.isEquipmentRetired(16L))
        assertTrue(mockCursor.isClosed)
    }

    @Test
    fun testGetEquipmentItems_activeOnly_appendsRetiredClause() {
        val mockCursor = MockCursorFactory.create(
            columns = listOf("any_col"),
            rows = emptyList()
        )

        val testHelper = object : EquipmentDbHelper(mockContext) {
            override fun getReadableDatabase(): SQLiteDatabase = mockDb
        }

        val capturedSelection = slot<String>()
        every {
            mockDb.query(
                EquipmentDbHelper.EQUIPMENT,
                any(),
                capture(capturedSelection),
                arrayOf("BIKE"),
                null, null, null
            )
        } returns mockCursor

        testHelper.getEquipmentItems(BSportType.BIKE, true)

        assertTrue(capturedSelection.captured.contains("Retired IS NULL OR Retired=0"))
        assertTrue(mockCursor.isClosed)
    }
}
