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

package com.atrainingtracker.trainingtracker.tracker

import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.sensor.SensorValueType
import com.atrainingtracker.testutil.MockCursorFactory
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.IdentityHashMap
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.regex.Pattern

/**
 * Unit and SQLite schema-simulation test suite verifying safe SQLite column identifier escaping
 * for custom device names containing apostrophes (`'`) and double quotes (`"`), and rate-limited
 * sample insert failure telemetry in [TrackerService] (REQ-TRK-029, TST-TRK-021, ATT-3985).
 */
class TrackerServiceDeviceNameQuotingTest {

    private lateinit var service: TrackerService
    private lateinit var mockSamplesManager: WorkoutSamplesDatabaseManager
    private lateinit var mockSummariesManager: WorkoutSummariesDatabaseManager
    private lateinit var mockSummariesDb: SQLiteDatabase
    private lateinit var mockLocalBroadcastManager: LocalBroadcastManager

    /**
     * Backing map for mocked [ContentValues] instances representing the current tick's
     * `samplingValues` in JVM unit tests. Summary-only keys from [WorkoutSummariesDatabaseManager.WorkoutSummaries]
     * are filtered out so `samplingValues.keySet()` contains only sample table columns.
     */
    private val currentTickSamplingMap = Collections.synchronizedMap(LinkedHashMap<String, Any?>())

    private val summaryColumnKeys = setOf(
        WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_TOTAL_s,
        WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_ACTIVE_s,
        WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m,
        WorkoutSummariesDatabaseManager.WorkoutSummaries.CALORIES,
        WorkoutSummariesDatabaseManager.WorkoutSummaries.LAPS,
        WorkoutSummariesDatabaseManager.WorkoutSummaries.ASCENDING,
        WorkoutSummariesDatabaseManager.WorkoutSummaries.DESCENDING,
        WorkoutSummariesDatabaseManager.WorkoutSummaries.SPEED_AVERAGE_mps
    )

    private fun recordSamplingEntry(key: String, value: Any?) {
        if (key !in summaryColumnKeys) {
            currentTickSamplingMap[key] = value
        }
    }

    /**
     * Strict SQLite identifier & schema simulator that enforces SQLite's exact lexical rules for
     * column identifiers in `INSERT INTO <table> (<col>, ...)` and
     * `ALTER TABLE <table> ADD COLUMN <col> <type> null;`:
     * - Single-quoted identifiers (`'...'`) with unescaped internal `'` throw `SQLiteException("near ...: syntax error")`.
     * - Double-quoted identifiers (`"..."`) must have internal `"` escaped as `""`, and unquote to the raw column name.
     * - Unquoted identifiers must match `[A-Za-z_][A-Za-z0-9_]*`.
     * - Inserting a column not yet in `columns` throws `SQLiteException("table ... has no column named ...")`.
     * - Executing `ALTER TABLE ... ADD COLUMN` for an already-existing column throws
     *   `SQLiteException("duplicate column name: ...")`.
     * - `query(...)` returns a cursor whose `getColumnIndex(...)` mimics Android's
     *   `AbstractWindowedCursor` dot-stripping quirk when a column name contains `.`, while
     *   `getColumnNames()` returns the exact raw column names stored in `sqlite_master`.
     */
    private class StrictSqliteSamplesSimulator(initialColumns: List<String>) {
        val columns = mutableListOf<String>().apply { addAll(initialColumns) }
        val rows = mutableListOf<Map<String, Any?>>()
        val alterTableStatements = mutableListOf<String>()

        fun parseSqlIdentifier(token: String): String {
            val trimmed = token.trim()
            if (trimmed.startsWith("'")) {
                if (trimmed.length < 2 || !trimmed.endsWith("'")) {
                    throw mockSqliteException("unrecognized token: $trimmed (code 1 SQLITE_ERROR)")
                }
                val inner = trimmed.substring(1, trimmed.length - 1)
                var i = 0
                val sb = StringBuilder()
                while (i < inner.length) {
                    val c = inner[i]
                    if (c == '\'') {
                        if (i + 1 < inner.length && inner[i + 1] == '\'') {
                            sb.append('\'')
                            i += 2
                        } else {
                            val tail = inner.substring(i + 1)
                            throw mockSqliteException("near \"$tail\": syntax error (code 1 SQLITE_ERROR)")
                        }
                    } else {
                        sb.append(c)
                        i++
                    }
                }
                return sb.toString()
            } else if (trimmed.startsWith("\"")) {
                if (trimmed.length < 2 || !trimmed.endsWith("\"")) {
                    throw mockSqliteException("unrecognized token: $trimmed (code 1 SQLITE_ERROR)")
                }
                val inner = trimmed.substring(1, trimmed.length - 1)
                var i = 0
                val sb = StringBuilder()
                while (i < inner.length) {
                    val c = inner[i]
                    if (c == '"') {
                        if (i + 1 < inner.length && inner[i + 1] == '"') {
                            sb.append('"')
                            i += 2
                        } else {
                            val tail = inner.substring(i + 1)
                            throw mockSqliteException("near \"$tail\": syntax error (code 1 SQLITE_ERROR)")
                        }
                    } else {
                        sb.append(c)
                        i++
                    }
                }
                return sb.toString()
            } else {
                if (!trimmed.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
                    throw mockSqliteException("near \"$trimmed\": syntax error (code 1 SQLITE_ERROR)")
                }
                return trimmed
            }
        }

        fun createDatabaseMock(
            contentValuesExtractor: (ContentValues) -> Map<String, Any?>
        ): SQLiteDatabase {
            val db = mockk<SQLiteDatabase>(relaxed = true)
            val schemaCursor = mockk<Cursor>(relaxed = true)

            every { schemaCursor.columnNames } answers { columns.toTypedArray() }
            every { schemaCursor.getColumnNames() } answers { columns.toTypedArray() }
            every { schemaCursor.getColumnIndex(any()) } answers {
                val colName = firstArg<String>()
                // Simulate Android AbstractWindowedCursor.getColumnIndex(String) stripping up to last '.'
                val sanitized = if (colName.contains('.')) {
                    colName.substring(colName.lastIndexOf('.') + 1)
                } else {
                    colName
                }
                val exact = columns.indexOf(sanitized)
                if (exact != -1) exact else columns.indexOfFirst { it.equals(sanitized, ignoreCase = true) }
            }

            fun doInsert(table: String, cv: ContentValues): Long {
                val entries = contentValuesExtractor(cv)
                val rowMap = LinkedHashMap<String, Any?>()

                // Phase 1: SQLite compiles `INSERT INTO <table> (<key1>, <key2>, ...) VALUES (?, ...)`
                // Syntax errors in any column identifier fail before column existence checks.
                val parsedEntries = entries.map { (rawKey, v) ->
                    val unquotedCol = parseSqlIdentifier(rawKey)
                    unquotedCol to v
                }

                // Phase 2: SQLite checks that every referenced column exists in the table schema.
                for ((unquotedCol, v) in parsedEntries) {
                    if (!columns.any { it.equals(unquotedCol, ignoreCase = true) }) {
                        throw mockSqliteException("table $table has no column named $unquotedCol (code 1 SQLITE_ERROR)")
                    }
                    rowMap[unquotedCol] = v
                }

                rows.add(rowMap)
                return rows.size.toLong()
            }

            every { db.insertOrThrow(any(), any(), any()) } answers { doInsert(firstArg(), thirdArg()) }
            every { db.insertOrThrow(any(), null, any()) } answers { doInsert(firstArg(), thirdArg()) }

            every { db.execSQL(any<String>()) } answers {
                val sql = firstArg<String>().trim()
                alterTableStatements.add(sql)
                // Expected pattern: ALTER TABLE <tableName> ADD COLUMN <quotedCol> <type> null;
                val prefixMatch = Regex("""^ALTER\s+TABLE\s+("[^"]+"|\S+)\s+ADD\s+COLUMN\s+(.+)\s+(int|double|text)\s+null\s*;?$""", RegexOption.IGNORE_CASE)
                    .matchEntire(sql)
                    ?: throw mockSqliteException("near \"$sql\": syntax error (code 1 SQLITE_ERROR)")
                val colToken = prefixMatch.groupValues[2].trim()
                val unquotedCol = parseSqlIdentifier(colToken)
                if (columns.any { it.equals(unquotedCol, ignoreCase = true) }) {
                    throw mockSqliteException("duplicate column name: $unquotedCol (code 1 SQLITE_ERROR)")
                }
                columns.add(unquotedCol)
            }

            every { db.query(any(), any(), any(), any(), any(), any(), any(), any()) } returns schemaCursor
            every { db.query(any(), null, null, null, null, null, null, any()) } returns schemaCursor

            return db
        }

        companion object {
            private class SimulatedSQLiteException(private val msg: String) : SQLiteException() {
                override val message: String
                    get() = msg
            }

            fun mockSqliteException(msg: String): SQLiteException = SimulatedSQLiteException(msg)
        }
    }

    @Before
    fun setUp() {
        currentTickSamplingMap.clear()

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } answers {
            recordSamplingEntry(firstArg(), secondArg<Int>())
        }
        every { anyConstructed<ContentValues>().put(any<String>(), any<Long>()) } answers {
            recordSamplingEntry(firstArg(), secondArg<Long>())
        }
        every { anyConstructed<ContentValues>().put(any<String>(), any<Double>()) } answers {
            recordSamplingEntry(firstArg(), secondArg<Double>())
        }
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } answers {
            recordSamplingEntry(firstArg(), secondArg<String>())
        }
        every { anyConstructed<ContentValues>().put(any<String>(), any<Boolean>()) } answers {
            recordSamplingEntry(firstArg(), secondArg<Boolean>())
        }
        every { anyConstructed<ContentValues>().keySet() } answers {
            LinkedHashSet(currentTickSamplingMap.keys)
        }

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.isPaused() } returns false
        every { TrainingApplication.isAutoPaused() } returns false
        every { TrainingApplication.isManualPauseLocked() } returns false
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC

        mockkStatic(SettingsDataStoreJavaHelper::class)
        val mockAutoPauseConfig = mockk<AutoPauseConfig>(relaxed = true)
        every { SettingsDataStoreJavaHelper.getAutoPauseConfig(any()) } returns mockAutoPauseConfig

        mockSamplesManager = mockk(relaxed = true)
        mockSummariesManager = mockk(relaxed = true)
        mockSummariesDb = mockk(relaxed = true)
        mockLocalBroadcastManager = mockk(relaxed = true)

        every { mockSummariesManager.database } returns mockSummariesDb

        mockkStatic(WorkoutSamplesDatabaseManager::class)
        every { WorkoutSamplesDatabaseManager.getInstance(any()) } returns mockSamplesManager
        every { WorkoutSamplesDatabaseManager.getTableName(any()) } answers { callOriginal() }

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesManager

        mockkStatic(LocalBroadcastManager::class)
        every { LocalBroadcastManager.getInstance(any()) } returns mockLocalBroadcastManager

        service = TrackerService()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * TST-TRK-021.1: Verifies that `quoteSqlColumnIdentifier` and `unquoteSqlColumnIdentifier`
     * form a lossless round-trip for device names containing apostrophes (`'`), double quotes (`"`),
     * parentheses, and periods, without stripping internal apostrophes or double quotes.
     */
    @Test
    fun testQuoteAndUnquoteSqlColumnIdentifier_preservesApostrophesAndEscapesDoubleQuotes() {
        val rawNames = listOf(
            "HR (Rainer's HRM)",
            "POWER (Stage's \"Pro\" L/R)",
            "CADENCE (O'Brien's \"Elite\" 2.0)",
            "SPEED_mps (Polar H10)",
            "ALTITUDE_gps"
        )

        for (raw in rawNames) {
            val quoted = TrackerService.quoteSqlColumnIdentifier(raw)
            assertTrue("Quoted identifier must start with '\"': $quoted", quoted.startsWith("\""))
            assertTrue("Quoted identifier must end with '\"': $quoted", quoted.endsWith("\""))

            val unquoted = TrackerService.unquoteSqlColumnIdentifier(quoted)
            assertEquals("Round-trip unquote(quote(raw)) must equal raw column name", raw, unquoted)
        }

        // Specific verification for embedded apostrophes and double quotes
        assertEquals(
            "\"HR (Rainer's HRM)\"",
            TrackerService.quoteSqlColumnIdentifier("HR (Rainer's HRM)")
        )
        assertEquals(
            "\"POWER (Stage's \"\"Pro\"\" L/R)\"",
            TrackerService.quoteSqlColumnIdentifier("POWER (Stage's \"Pro\" L/R)")
        )

        // Legacy single-quoted identifier compatibility
        assertEquals(
            "HR (Polar H10)",
            TrackerService.unquoteSqlColumnIdentifier("'HR (Polar H10)'")
        )
        assertEquals(
            "HR (Rainer's HRM)",
            TrackerService.unquoteSqlColumnIdentifier("'HR (Rainer''s HRM)'")
        )
        // Unquoted identifier pass-through
        assertEquals(
            "SPEED_mps_gps",
            TrackerService.unquoteSqlColumnIdentifier("SPEED_mps_gps")
        )
        assertEquals("", TrackerService.unquoteSqlColumnIdentifier(null))
    }

    /**
     * TST-TRK-021.2: Verifies end-to-end 1 Hz sample insertion and dynamic `ALTER TABLE` column
     * creation through `sampleAndWriteToDb()` and `handleMissingColumns()` when paired device names
     * contain apostrophes (`'`), double quotes (`"`), and periods (`.`), and confirms that the
     * resulting column names match `CSVFileWriter`'s `<SensorType> (<DeviceName>)` regex.
     */
    @Test
    fun testSampleAndWriteToDb_withApostropheAndDoubleQuoteDeviceNames_insertsAllSampleRowsInSqlite() {
        val simulator = StrictSqliteSamplesSimulator(
            initialColumns = listOf("TIME_TOTAL (clock)", "HR", "POWER", "CADENCE")
        )
        val mockSamplesDb = simulator.createDatabaseMock { LinkedHashMap(currentTickSamplingMap) }
        every { mockSamplesManager.database } returns mockSamplesDb

        // Configure TrackerService fields for synchronous DB execution on calling thread
        setPrivateField(service, "mWorkoutID", 42L)
        setPrivateField(service, "mBaseFileName", "2026-10-18_150000")
        setPrivateField(service, "mSamplesTableName", "\"2026-10-18_150000\"")
        setPrivateField(service, "mTableInitializationFuture", CompletableFuture.completedFuture<Void>(null))
        setPrivateField(service, "mDbExecutor", newDirectExecutorService())

        val mockBanalComm = mockk<BANALServiceComm>(relaxed = true)
        every { mockBanalComm.databaseIdsOfActiveRemoteDevices } returns emptyList()
        every { mockBanalComm.bSportType } returns BSportType.BIKE
        every { mockBanalComm.getBestSensorData(any()) } returns null
        setPrivateField(service, "mBanalService", mockBanalComm)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        // Tick 1: HR ("Rainer's HRM") and POWER ("Stage's \"Pro\" v2.1")
        currentTickSamplingMap.clear()
        val tick1Sensors = listOf<SensorData<Number>>(
            SensorData(SensorType.TIME_TOTAL, 1, dateFormat.format(Date(1000L)), "clock"),
            SensorData(SensorType.HR, 142, "142", "Rainer's HRM"),
            SensorData(SensorType.POWER, 255, "255", "Stage's \"Pro\" v2.1")
        )
        every { mockBanalComm.allSensorData } returns ArrayList(tick1Sensors)

        service.sampleAndWriteToDb()

        // Tick 2: Same sensors at second 2 (columns now already exist in table)
        currentTickSamplingMap.clear()
        val tick2Sensors = listOf<SensorData<Number>>(
            SensorData(SensorType.TIME_TOTAL, 2, dateFormat.format(Date(2000L)), "clock"),
            SensorData(SensorType.HR, 145, "145", "Rainer's HRM"),
            SensorData(SensorType.POWER, 260, "260", "Stage's \"Pro\" v2.1")
        )
        every { mockBanalComm.allSensorData } returns ArrayList(tick2Sensors)

        service.sampleAndWriteToDb()

        // Tick 3: A third sensor CADENCE ("O'Brien's Cadence") joins mid-workout at second 3.
        // handleMissingColumns must recognize that "HR (Rainer's HRM)" and "POWER (Stage's \"Pro\" v2.1)"
        // already exist (even with apostrophes, quotes, and periods) and only ALTER TABLE for CADENCE.
        currentTickSamplingMap.clear()
        val tick3Sensors = listOf<SensorData<Number>>(
            SensorData(SensorType.TIME_TOTAL, 3, dateFormat.format(Date(3000L)), "clock"),
            SensorData(SensorType.HR, 148, "148", "Rainer's HRM"),
            SensorData(SensorType.POWER, 270, "270", "Stage's \"Pro\" v2.1"),
            SensorData(SensorType.CADENCE, 92, "92", "O'Brien's Cadence")
        )
        every { mockBanalComm.allSensorData } returns ArrayList(tick3Sensors)

        service.sampleAndWriteToDb()

        // Verify all 3 sample rows were persisted without a single dropped sample
        assertEquals("All 3 sample rows must be persisted", 3, simulator.rows.size)
        assertEquals(0, service.sampleInsertFailureCount)

        // Verify exact unquoted column names in schema
        val expectedHrCol = "HR (Rainer's HRM)"
        val expectedPowerCol = "POWER (Stage's \"Pro\" v2.1)"
        val expectedCadenceCol = "CADENCE (O'Brien's Cadence)"
        assertTrue(simulator.columns.contains(expectedHrCol))
        assertTrue(simulator.columns.contains(expectedPowerCol))
        assertTrue(simulator.columns.contains(expectedCadenceCol))

        // Verify each column was added via ALTER TABLE exactly once
        assertEquals(3, simulator.alterTableStatements.size)

        // Verify stored row values
        assertEquals(142, simulator.rows[0][expectedHrCol])
        assertEquals(255.0, simulator.rows[0][expectedPowerCol])
        assertEquals(145, simulator.rows[1][expectedHrCol])
        assertEquals(260.0, simulator.rows[1][expectedPowerCol])
        assertEquals(148, simulator.rows[2][expectedHrCol])
        assertEquals(270.0, simulator.rows[2][expectedPowerCol])
        assertEquals(92.0, simulator.rows[2][expectedCadenceCol])

        // Verify CSVFileWriter regex compatibility for all device-qualified column names
        val csvHeaderPattern = Pattern.compile("(.*)( \\(.*\\)|_gps|_network|_google_fused)")
        for (col in listOf(expectedHrCol, expectedPowerCol, expectedCadenceCol)) {
            val matcher = csvHeaderPattern.matcher(col)
            assertTrue("Column '$col' must match CSVFileWriter pattern", matcher.matches())
            assertNotNull(matcher.group(1))
            assertNotNull(matcher.group(2))
        }
    }

    /**
     * TST-TRK-021.3: Verifies that repeated unrecoverable SQLite sample insert failures increment
     * `getSampleInsertFailureCount()` on every failure while rate-limiting `Log.e` emission to the
     * initial burst (`1..3`) and every 60th failure thereafter (`60`).
     */
    @Test
    fun testSampleInsertFailureCounter_rateLimitsRepeatedSqliteErrors() {
        val mockSamplesDb = mockk<SQLiteDatabase>(relaxed = true)
        val values = ContentValues()
        values.put("HR", 140)
        val name2Type = mapOf("HR" to SensorValueType.INTEGER)

        val diskFullEx = mockk<SQLiteException>(relaxed = true) {
            every { message } returns "database or disk is full (code 13 SQLITE_FULL)"
        }

        val totalFailures = 65
        for (i in 1..totalFailures) {
            val result = service.handleMissingColumns(
                mockSamplesDb,
                "\"2026-10-18_150000\"",
                values,
                name2Type,
                diskFullEx
            )
            assertFalse("handleMissingColumns must return false on unrecoverable error (iteration $i)", result)
        }

        assertEquals(
            "Failure counter must track all $totalFailures failures",
            totalFailures,
            service.sampleInsertFailureCount
        )

        // Rate-limiting policy: log on failures 1, 2, 3 (initial burst) and 60 (interval=60) -> 4 total calls
        verify(exactly = 4) {
            Log.e(
                eq("TrackerService"),
                match { it.contains("sampleInsertFailureCount=") },
                eq(diskFullEx)
            )
        }
    }

    private fun setPrivateField(target: Any, fieldName: String, value: Any?) {
        val field: Field = TrackerService::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun newDirectExecutorService(): ExecutorService {
        val executor = mockk<ExecutorService>(relaxed = true)
        every { executor.execute(any()) } answers {
            firstArg<Runnable>().run()
        }
        return executor
    }
}
