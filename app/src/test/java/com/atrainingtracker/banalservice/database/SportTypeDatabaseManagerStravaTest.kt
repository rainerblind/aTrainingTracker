package com.atrainingtracker.banalservice.database

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying SportTypeDatabaseManager.getSportTypeIdFromStravaName (REQ-EXP-009, TST-EXP-006, ATT-1114).
 */
class SportTypeDatabaseManagerStravaTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase
    private lateinit var manager: SportTypeDatabaseManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)
        manager = SportTypeDatabaseManager.getInstance(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testGetSportTypeIdFromStravaName_nullOrEmpty_returnsMinusOne() {
        assertEquals(-1L, manager.getSportTypeIdFromStravaName(null))
        assertEquals(-1L, manager.getSportTypeIdFromStravaName(""))
    }

    @Test
    fun testGetSportTypeIdFromStravaName_foundInDatabase() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getLong(0) } returns 5L
        every { mockCursor.close() } returns Unit

        val testManager = object : SportTypeDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }

        every {
            mockDb.query(
                SportTypeDatabaseManager.SportType.TABLE,
                arrayOf(SportTypeDatabaseManager.SportType.C_ID),
                "${SportTypeDatabaseManager.SportType.STRAVA_NAME}=? COLLATE NOCASE",
                arrayOf("MountainBikeRide"),
                null, null, null
            )
        } returns mockCursor

        val sportId = testManager.getSportTypeIdFromStravaName("MountainBikeRide")
        assertEquals(5L, sportId)
    }

    @Test
    fun testGetSportTypeIdFromStravaName_fallbackToEnumMapping() {
        val emptyCursor = mockk<Cursor>(relaxed = true)
        every { emptyCursor.moveToFirst() } returns false
        every { emptyCursor.close() } returns Unit

        val fallbackCursor = mockk<Cursor>(relaxed = true)
        every { fallbackCursor.moveToFirst() } returns true
        every { fallbackCursor.getLong(0) } returns 2L
        every { fallbackCursor.close() } returns Unit

        val testManager = object : SportTypeDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }

        // Direct lookup fails
        every {
            mockDb.query(
                SportTypeDatabaseManager.SportType.TABLE,
                arrayOf(SportTypeDatabaseManager.SportType.C_ID),
                "${SportTypeDatabaseManager.SportType.STRAVA_NAME}=? COLLATE NOCASE",
                arrayOf("Ride"),
                null, null, null
            )
        } returns emptyCursor

        // Fallback lookup by BASE_SPORT_TYPE matches
        every {
            mockDb.query(
                SportTypeDatabaseManager.SportType.TABLE,
                arrayOf(SportTypeDatabaseManager.SportType.C_ID),
                "${SportTypeDatabaseManager.SportType.BASE_SPORT_TYPE}=?",
                any(),
                null, null, null, "1"
            )
        } returns fallbackCursor

        val sportId = testManager.getSportTypeIdFromStravaName("Ride")
        assertEquals(2L, sportId)
    }
}
