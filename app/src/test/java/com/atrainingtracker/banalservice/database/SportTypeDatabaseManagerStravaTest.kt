package com.atrainingtracker.banalservice.database

import android.app.Application
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Field

/**
 * Unit tests verifying SportTypeDatabaseManager Strava mapping persistence and lookups
 * (REQ-EXP-006, TST-UNT-006, REQ-EXP-009, TST-EXP-006, ATT-1114).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
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

    /**
     * TST-UNT-006 (REQ-EXP-006): Verifies that SportTypeDatabaseManager persists an explicit
     * null Strava mapping ("No upload") across SQLite updates and fresh instance reloads.
     */
    @Test
    fun testUpdateSportType_persistsNullStravaMappingAsPersistentState_TST_UNT_006() {
        // TST-UNT-006
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val ctxField: Field = TrainingApplication::class.java.getDeclaredField("cAppContext")
        ctxField.isAccessible = true
        val prevContext = ctxField.get(null)
        ctxField.set(null, appContext)

        val prefsField: Field = TrainingApplication::class.java.getDeclaredField("cSharedPreferences")
        prefsField.isAccessible = true
        val prevPrefs = prefsField.get(null)
        prefsField.set(null, androidx.preference.PreferenceManager.getDefaultSharedPreferences(appContext))

        SportTypeDatabaseManager.resetForTesting(null)
        appContext.deleteDatabase(SportTypeDatabaseManager.SportTypeDbHelper.DB_NAME)

        try {
            val realMgr = SportTypeDatabaseManager.getInstance(appContext)
            val id = realMgr.updateSportType(
                -1L,
                "Commute Unsynced",
                BSportType.BIKE,
                1.0,
                12.0,
                "Ride",
                "biking",
                "bike"
            )
            assertTrue(id > 0L)
            assertEquals("Ride", realMgr.getStravaName(id))

            // Explicitly update Strava mapping to null ("No upload")
            realMgr.updateSportType(
                id,
                "Commute Unsynced",
                BSportType.BIKE,
                1.0,
                12.0,
                null,
                "biking",
                "bike"
            )
            assertNull(realMgr.getStravaName(id))

            // Force fresh manager instance / DB read
            SportTypeDatabaseManager.resetForTesting(null)
            val reloadedMgr = SportTypeDatabaseManager.getInstance(appContext)
            assertNull(reloadedMgr.getStravaName(id))
        } finally {
            SportTypeDatabaseManager.resetForTesting(null)
            appContext.deleteDatabase(SportTypeDatabaseManager.SportTypeDbHelper.DB_NAME)
            ctxField.set(null, prevContext)
            prefsField.set(null, prevPrefs)
        }
    }
}
