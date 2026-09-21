package com.atrainingtracker.trainingtracker.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying EquipmentDbHelper schema migration v2, reverse Strava ID lookup,
 * and addOrUpdateStravaGear (REQ-EXP-009, TST-EXP-006, ATT-1114).
 */
class EquipmentDbHelperStravaEnrichmentTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase
    private lateinit var helper: EquipmentDbHelper

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
        helper = EquipmentDbHelper(mockContext)

        contentValueStores.clear()
        io.mockk.mockkConstructor(ContentValues::class)
        every { constructedWith<ContentValues>().put(any<String>(), any<String>()) } answers {
            val cv = getRealInstance()
            contentValueStores.computeIfAbsent(cv) { mutableMapOf() }[firstArg<String>()] = secondArg<String>()
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Int>()) } answers {
            val cv = getRealInstance()
            contentValueStores.computeIfAbsent(cv) { mutableMapOf() }[firstArg<String>()] = secondArg<Int>()
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
    fun testGetIdFromStravaId_returnsIdWhenFound() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getLong(0) } returns 42L
        every { mockCursor.close() } returns Unit

        val testHelper = object : EquipmentDbHelper(mockContext) {
            override fun getReadableDatabase(): SQLiteDatabase = mockDb
        }

        every {
            mockDb.query(
                EquipmentDbHelper.EQUIPMENT,
                arrayOf(EquipmentDbHelper.C_ID),
                "${EquipmentDbHelper.STRAVA_ID}=?",
                arrayOf("b123456"),
                null, null, null, "1"
            )
        } returns mockCursor

        val id = testHelper.getIdFromStravaId("b123456")
        assertEquals(42L, id)
    }

    @Test
    fun testGetIdFromStravaId_returnsMinusOneWhenNotFound() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToFirst() } returns false
        every { mockCursor.close() } returns Unit

        val testHelper = object : EquipmentDbHelper(mockContext) {
            override fun getReadableDatabase(): SQLiteDatabase = mockDb
        }

        every {
            mockDb.query(
                EquipmentDbHelper.EQUIPMENT,
                arrayOf(EquipmentDbHelper.C_ID),
                "${EquipmentDbHelper.STRAVA_ID}=?",
                arrayOf("nonexistent"),
                null, null, null, "1"
            )
        } returns mockCursor

        val id = testHelper.getIdFromStravaId("nonexistent")
        assertEquals(-1L, id)
    }

    @Test
    fun testGetIdFromStravaId_returnsMinusOneForNullOrEmpty() {
        val testHelper = object : EquipmentDbHelper(mockContext) {
            override fun getReadableDatabase(): SQLiteDatabase = mockDb
        }

        assertEquals(-1L, testHelper.getIdFromStravaId(null))
        assertEquals(-1L, testHelper.getIdFromStravaId(""))
        assertEquals(-1L, testHelper.getIdFromStravaId("   "))
    }

    @Test
    fun testOnUpgrade_fromVersion1To2_addsRetiredColumnSafely() {
        helper.onUpgrade(mockDb, 1, 2)
        verify {
            mockDb.execSQL("ALTER TABLE ${EquipmentDbHelper.EQUIPMENT} ADD COLUMN ${EquipmentDbHelper.RETIRED} INTEGER DEFAULT 0")
        }
    }

    @Test
    fun testAddOrUpdateStravaGear_insertsNewRecordWhenNotPresent() {
        val capturedValues = mutableListOf<ContentValues>()
        val testHelper = object : EquipmentDbHelper(mockContext) {
            override fun getWritableDatabase(): SQLiteDatabase = mockDb
            override fun getReadableDatabase(): SQLiteDatabase = mockDb
            override fun getIdFromStravaId(stravaId: String?): Long = 100L
        }

        every {
            mockDb.update(EquipmentDbHelper.EQUIPMENT, any(), "${EquipmentDbHelper.STRAVA_ID}=?", arrayOf("b999"))
        } returns 0

        every {
            mockDb.update(EquipmentDbHelper.EQUIPMENT, any(), any(), arrayOf("My Old Bike"))
        } returns 0

        every {
            mockDb.insert(EquipmentDbHelper.EQUIPMENT, null, capture(capturedValues))
        } returns 100L

        val id = testHelper.addOrUpdateStravaGear("b999", "My Old Bike", 1, BSportType.BIKE.name, true)
        assertEquals(100L, id)
        assertTrue(capturedValues.isNotEmpty())
        val values = capturedValues.first()
        assertEquals("My Old Bike", values.getAsString(EquipmentDbHelper.NAME))
        assertEquals("My Old Bike", values.getAsString(EquipmentDbHelper.STRAVA_NAME))
        assertEquals("b999", values.getAsString(EquipmentDbHelper.STRAVA_ID))
        assertEquals(1, values.getAsInteger(EquipmentDbHelper.FRAME_TYPE))
        assertEquals(BSportType.BIKE.name, values.getAsString(EquipmentDbHelper.SPORT_TYPE))
        assertEquals(1, values.getAsInteger(EquipmentDbHelper.RETIRED))
    }
}
