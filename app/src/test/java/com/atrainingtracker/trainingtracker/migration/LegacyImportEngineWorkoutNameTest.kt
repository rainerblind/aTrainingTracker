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

package com.atrainingtracker.trainingtracker.migration

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.util.Xml
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests verifying TCX workout name and description extraction, structured extension parsing,
 * bracket notation heuristics, Garmin training plan fallback, character unescaping,
 * and cluster overwrite immunity (REQ-DAT-013, TST-DAT-007, ATT-922, ATT-924).
 */
class LegacyImportEngineWorkoutNameTest {

    private lateinit var mockContext: Context
    private lateinit var mockSummariesDb: WorkoutSummariesDatabaseManager
    private lateinit var mockSamplesDb: WorkoutSamplesDatabaseManager
    private lateinit var mockLapsDb: LapsDatabaseManager
    private lateinit var mockSportTypeDb: SportTypeDatabaseManager
    private lateinit var mockSqlDb: SQLiteDatabase

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
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.uploadToCommunity(any()) } returns false

        mockkStatic(Xml::class)
        every { Xml.newPullParser() } answers {
            org.kxml2.io.KXmlParser()
        }

        mockContext = mockk(relaxed = true)
        mockSqlDb = mockk(relaxed = true)

        mockSummariesDb = mockk(relaxed = true)
        every { mockSummariesDb.database } returns mockSqlDb
        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb

        mockSamplesDb = mockk(relaxed = true)
        every { mockSamplesDb.database } returns mockSqlDb
        mockkStatic(WorkoutSamplesDatabaseManager::class)
        every { WorkoutSamplesDatabaseManager.getInstance(any()) } returns mockSamplesDb

        mockLapsDb = mockk(relaxed = true)
        every { mockLapsDb.database } returns mockSqlDb
        mockkStatic(LapsDatabaseManager::class)
        every { LapsDatabaseManager.getInstance(any()) } returns mockLapsDb

        mockSportTypeDb = mockk(relaxed = true)
        every { mockSportTypeDb.getSportTypeIdFromTcxName(any()) } returns 1L
        every { mockSportTypeDb.getBSportType(1L) } returns BSportType.RUN
        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeDb

        contentValueStores.clear()
        mockkConstructor(ContentValues::class)
        every { constructedWith<ContentValues>().put(any<String>(), any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<String>()
            Unit
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Long>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Long>()
            Unit
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Int>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Int>()
            Unit
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Double>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Double>()
            Unit
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Float>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Float>()
            Unit
        }
        every { constructedWith<ContentValues>().containsKey(any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            map?.containsKey(firstArg<String>()) ?: false
        }
        every { constructedWith<ContentValues>().getAsString(any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            map?.get(firstArg<String>())?.toString()
        }
        every { constructedWith<ContentValues>().getAsLong(any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            (map?.get(firstArg<String>()) as? Number)?.toLong()
        }
        every { constructedWith<ContentValues>().getAsInteger(any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            (map?.get(firstArg<String>()) as? Number)?.toInt()
        }
        every { constructedWith<ContentValues>().getAsDouble(any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            (map?.get(firstArg<String>()) as? Number)?.toDouble()
        }
        every { constructedWith<ContentValues>().size() } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            map?.size ?: 0
        }

        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.count } returns 0
        every { mockCursor.moveToFirst() } returns false
        every { mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any()) } returns mockCursor
        every { mockSqlDb.insert(WorkoutSummaries.TABLE, any(), any()) } returns 101L
        every { mockSqlDb.insert(match { it != WorkoutSummaries.TABLE }, any(), any()) } returns 1L
        every { mockSqlDb.update(WorkoutSummaries.TABLE, any(), any(), any()) } returns 1
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun generateTcx(
        activityNotes: String? = null,
        activityExtensionName: String? = null,
        activityExtensionDesc: String? = null,
        trainingPlanName: String? = null
    ): String {
        val notesElement = if (activityNotes != null) "<Notes>$activityNotes</Notes>" else ""
        val trainingElement = if (trainingPlanName != null) {
            "<Training><Plan><Name>$trainingPlanName</Name></Plan></Training>"
        } else ""
        val extensionsElement = if (activityExtensionName != null || activityExtensionDesc != null) {
            val nameTag = if (activityExtensionName != null) "<att:Name>$activityExtensionName</att:Name>" else ""
            val descTag = if (activityExtensionDesc != null) "<att:Description>$activityExtensionDesc</att:Description>" else ""
            "<Extensions><att:ActivityExtension xmlns:att=\"http://atrainingtracker.com/xmlschemas/TrainingCenterDatabaseExtensions/v1\">$nameTag$descTag</att:ActivityExtension></Extensions>"
        } else ""

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            """<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns:att="http://atrainingtracker.com/xmlschemas/TrainingCenterDatabaseExtensions/v1">
  <Activities>
    <Activity Sport="Running">
      <Id>2026-09-12T10:00:00Z</Id>
      <Lap StartTime="2026-09-12T10:00:00Z">
        <TotalTimeSeconds>300.0</TotalTimeSeconds>
        <DistanceMeters>1000.0</DistanceMeters>
        <Track>
          <Trackpoint>
            <Time>2026-09-12T10:00:00Z</Time>
            <Position><LatitudeDegrees>48.0</LatitudeDegrees><LongitudeDegrees>11.0</LongitudeDegrees></Position>
            <DistanceMeters>0.0</DistanceMeters>
          </Trackpoint>
          <Trackpoint>
            <Time>2026-09-12T10:05:00Z</Time>
            <Position><LatitudeDegrees>48.005</LatitudeDegrees><LongitudeDegrees>11.005</LongitudeDegrees></Position>
            <DistanceMeters>1000.0</DistanceMeters>
          </Trackpoint>
        </Track>
      </Lap>
      $notesElement
      $trainingElement
      <Creator xsi:type="Device_t">
        <Name>Pixel 10</Name>
      </Creator>
      $extensionsElement
    </Activity>
  </Activities>
  <Author xsi:type="Application_t">
    <Name>aTrainingTracker</Name>
  </Author>
</TrainingCenterDatabase>""".trimIndent()
    }

    @Test
    fun testImportExtractsStructuredActivityExtension() = runBlocking {
        val tcx = generateTcx(
            activityNotes = "[Morning Intervals] Felt strong today",
            activityExtensionName = "Morning Intervals",
            activityExtensionDesc = "Felt strong today"
        )

        val tempFile = File.createTempFile("test_activity_ext_", ".tcx")
        tempFile.writeText(tcx)

        try {
            val result = LegacyImportEngine.importFromTcx(mockContext, tempFile)
            assertTrue("Import must succeed", result)

            val insertedValues = mutableListOf<ContentValues>()
            verify { mockSqlDb.insert(WorkoutSummaries.TABLE, null, capture(insertedValues)) }
            assertEquals(1, insertedValues.size)
            assertEquals("Morning Intervals", insertedValues[0].getAsString(WorkoutSummaries.WORKOUT_NAME))

            val updatedValues = mutableListOf<ContentValues>()
            verify { mockSqlDb.update(WorkoutSummaries.TABLE, capture(updatedValues), any(), any()) }
            assertTrue(updatedValues.any { it.getAsString(WorkoutSummaries.WORKOUT_NAME) == "Morning Intervals" })
            assertTrue(updatedValues.any { it.getAsString(WorkoutSummaries.DESCRIPTION) == "Felt strong today" })
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testImportExtractsBracketNotationFromNotes() = runBlocking {
        // Third-party format without structured extension: standard bracket notes
        val tcx = generateTcx(
            activityNotes = "[Tempo 5k] Solid negative split",
            activityExtensionName = null,
            activityExtensionDesc = null
        )

        val tempFile = File.createTempFile("test_bracket_notes_", ".tcx")
        tempFile.writeText(tcx)

        try {
            val result = LegacyImportEngine.importFromTcx(mockContext, tempFile)
            assertTrue("Import must succeed", result)

            val insertedValues = mutableListOf<ContentValues>()
            verify { mockSqlDb.insert(WorkoutSummaries.TABLE, null, capture(insertedValues)) }
            assertEquals("Tempo 5k", insertedValues[0].getAsString(WorkoutSummaries.WORKOUT_NAME))

            val updatedValues = mutableListOf<ContentValues>()
            verify { mockSqlDb.update(WorkoutSummaries.TABLE, capture(updatedValues), any(), any()) }
            assertTrue(updatedValues.any { it.getAsString(WorkoutSummaries.WORKOUT_NAME) == "Tempo 5k" })
            assertTrue(updatedValues.any { it.getAsString(WorkoutSummaries.DESCRIPTION) == "Solid negative split" })
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testImportExtractsNameOnlyFromBracketNotes() = runBlocking {
        val tcx = generateTcx(
            activityNotes = "[Speed Session]",
            activityExtensionName = null,
            activityExtensionDesc = null
        )

        val tempFile = File.createTempFile("test_name_only_", ".tcx")
        tempFile.writeText(tcx)

        try {
            val result = LegacyImportEngine.importFromTcx(mockContext, tempFile)
            assertTrue("Import must succeed", result)

            val insertedValues = mutableListOf<ContentValues>()
            verify { mockSqlDb.insert(WorkoutSummaries.TABLE, null, capture(insertedValues)) }
            assertEquals("Speed Session", insertedValues[0].getAsString(WorkoutSummaries.WORKOUT_NAME))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testImportExtractsGarminTrainingPlanName() = runBlocking {
        val tcx = generateTcx(
            activityNotes = "Endurance pace throughout",
            activityExtensionName = null,
            activityExtensionDesc = null,
            trainingPlanName = "Marathon Base Phase"
        )

        val tempFile = File.createTempFile("test_training_plan_", ".tcx")
        tempFile.writeText(tcx)

        try {
            val result = LegacyImportEngine.importFromTcx(mockContext, tempFile)
            assertTrue("Import must succeed", result)

            val insertedValues = mutableListOf<ContentValues>()
            verify { mockSqlDb.insert(WorkoutSummaries.TABLE, null, capture(insertedValues)) }
            assertEquals("Marathon Base Phase", insertedValues[0].getAsString(WorkoutSummaries.WORKOUT_NAME))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testImportDoesNotPolluteWorkoutNameFromCreatorOrAuthor() = runBlocking {
        // TCX with no activity notes or extensions, but has Creator Name ("Pixel 10") and Author Name ("aTrainingTracker")
        val tcx = generateTcx(
            activityNotes = null,
            activityExtensionName = null,
            activityExtensionDesc = null
        )

        val tempFile = File.createTempFile("test_creator_author_", ".tcx")
        tempFile.writeText(tcx)

        try {
            val result = LegacyImportEngine.importFromTcx(mockContext, tempFile)
            assertTrue("Import must succeed", result)

            val insertedValues = mutableListOf<ContentValues>()
            verify { mockSqlDb.insert(WorkoutSummaries.TABLE, null, capture(insertedValues)) }
            // Workout name must NOT be "Pixel 10" or "aTrainingTracker"; it should default to baseFileName (starts with "2026-09-12")
            val assignedName = insertedValues[0].getAsString(WorkoutSummaries.WORKOUT_NAME)
            assertTrue("Assigned name must not be device creator name", assignedName != "Pixel 10")
            assertTrue("Assigned name must not be author name", assignedName != "aTrainingTracker")
            assertEquals("Assigned name must match temp file nameWithoutExtension", tempFile.nameWithoutExtension, assignedName)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testImportUnescapesXmlEntities() = runBlocking {
        val tcx = generateTcx(
            activityNotes = "[Interval &amp; Hill] Zone 4 &lt;hard&gt; &quot;effort&quot; &apos;test&apos;",
            activityExtensionName = "Interval &amp; Hill",
            activityExtensionDesc = "Zone 4 &lt;hard&gt; &quot;effort&quot; &apos;test&apos;"
        )

        val tempFile = File.createTempFile("test_unescape_", ".tcx")
        tempFile.writeText(tcx)

        try {
            val result = LegacyImportEngine.importFromTcx(mockContext, tempFile)
            assertTrue("Import must succeed", result)

            val insertedValues = mutableListOf<ContentValues>()
            verify { mockSqlDb.insert(WorkoutSummaries.TABLE, null, capture(insertedValues)) }
            assertEquals("Interval & Hill", insertedValues[0].getAsString(WorkoutSummaries.WORKOUT_NAME))

            val updatedValues = mutableListOf<ContentValues>()
            verify { mockSqlDb.update(WorkoutSummaries.TABLE, capture(updatedValues), any(), any()) }
            assertTrue(updatedValues.any { it.getAsString(WorkoutSummaries.WORKOUT_NAME) == "Interval & Hill" })
            assertTrue(updatedValues.any { it.getAsString(WorkoutSummaries.DESCRIPTION) == "Zone 4 <hard> \"effort\" 'test'" })
        } finally {
            tempFile.delete()
        }
    }
}
