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
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry
import com.atrainingtracker.trainingtracker.migration.MigrationEngine
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Comprehensive verification suite for ATT-8131 (`REQ-PER-056`, `TST-PER-056.1`–`.4`):
 * 1. `TST-PER-056.1`: `PeriodSummariesDatabaseManager` excised & `DatabaseSchemaRegistry` excludes `PeriodSummaries.db`.
 * 2. `TST-PER-056.2`: Reactive in-memory projection parity across multi-sport/multi-year/DST workouts,
 *    empty intermediate bar-graph periods, dynamic locale/zone formatting, indoor bounds, `getWorkoutsForRange`,
 *    and 5,000-workout performance.
 * 3. `TST-PER-056.3`: Immediate Day/Week/Month/Year consistency after `cropWorkout`/edit (unchanged workout count),
 *    sport-type change, deletion, and 0-rebuild suppression on non-period edits.
 * 4. `TST-PER-056.4`: One-time obsolete `PeriodSummaries*` disk cleanup (even when legacy pref is true)
 *    and legacy `.attbackup` restore compatibility with post-swap `PeriodSummaries*` purge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PeriodReactiveProjectionTest {

    private lateinit var context: Context
    private lateinit var app: Application
    private val berlinZone: ZoneId = ZoneId.of("Europe/Berlin")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        app = context as Application
        PeriodsRepository.resetInstanceForTesting()
        WorkoutRepository.resetForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        clearPeriodCardMapStateCache()
    }

    @After
    fun tearDown() {
        PeriodsRepository.resetInstanceForTesting()
        WorkoutRepository.resetForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        clearPeriodCardMapStateCache()
        unmockkAll()
    }

    private fun workout(
        id: Long,
        dateTime: LocalDateTime,
        sport: BSportType = BSportType.BIKE,
        sportName: String = if (sport == BSportType.BIKE) "Road" else "Trail",
        sportId: Long = if (sport == BSportType.BIKE) 2L else 1L,
        durationSec: Long = 3600L,
        distanceM: Double = 25_000.0,
        ascentM: Long = 300L,
        finished: Boolean = true,
        workoutName: String = "Workout $id",
        withGps: Boolean = true,
        latBase: Double = 48.10,
        lngBase: Double = 11.50
    ): WorkoutData {
        val startS = dateTime.atZone(berlinZone).toEpochSecond()
        val points = if (withGps) {
            listOf(LatLng(latBase, lngBase), LatLng(latBase + 0.02, lngBase + 0.02))
        } else {
            emptyList()
        }
        val poly = if (points.isNotEmpty()) PolyUtil.encode(points) else ""
        return WorkoutData(
            id = id,
            finished = finished,
            fileBaseName = "w_$id",
            workoutName = workoutName,
            sportId = sportId,
            sportName = sportName,
            bSportType = sport,
            startTimeS = startS,
            formattedDate = dateTime.toLocalDate().toString(),
            formattedTime = dateTime.toLocalTime().toString(),
            localDateTime = dateTime,
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = !withGps,
            mapPolyline = poly,
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = distanceM,
            maxDisplacement = if (withGps) 3000.0 else 0.0,
            activeTimeSec = durationSec,
            totalTimeSec = durationSec + 60L,
            avgSpeedMps = if (durationSec > 0) distanceM / durationSec else 0.0,
            ascentMeters = ascentM,
            descentMeters = ascentM,
            minAltitude = 500.0,
            maxAltitude = 500.0 + ascentM,
            minLat = if (withGps) latBase else null,
            minLng = if (withGps) lngBase else null,
            maxLat = if (withGps) latBase + 0.02 else null,
            maxLng = if (withGps) lngBase + 0.02 else null,
            startLatLng = points.firstOrNull(),
            endLatLng = points.lastOrNull(),
            maxDisplacementLatLng = points.lastOrNull(),
            maxAltitudeLatLng = points.lastOrNull(),
            description = null,
            goal = null,
            method = null,
            stravaSportName = null
        )
    }

    /**
     * Verifies PeriodSummariesDatabaseManager excision & registry parity (TST-PER-056.1;
     * supersedes PeriodSummaries.db sync tests TST-PERF-004, TST-PERF-005, TST-PER-020, TST-PER-026, TST-BUG-002).
     */
    @Test
    fun testPeriodSummariesDatabaseManagerExcised_andRegistryExcludesIt_TST_PER_056_1() {
        var found = true
        try {
            Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodSummariesDatabaseManager")
        } catch (_: ClassNotFoundException) {
            found = false
        }
        assertFalse("PeriodSummariesDatabaseManager must be permanently excised", found)

        assertFalse(
            "DatabaseSchemaRegistry must not list PeriodSummaries.db as an active DB",
            DatabaseSchemaRegistry.getSupportedDatabases().contains("PeriodSummaries.db")
        )
        assertNull(
            "getMaxSupportedVersion must return null for excised PeriodSummaries.db",
            DatabaseSchemaRegistry.getMaxSupportedVersion("PeriodSummaries.db")
        )
        assertTrue(
            "Unregistered legacy PeriodSummaries.db (v27) must be accepted for backward-compatible .attbackup restore",
            DatabaseSchemaRegistry.isVersionSupported("PeriodSummaries.db", 27)
        )

        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        every { mockWorkoutRepo.allWorkouts } returns MutableStateFlow(emptyList())
        val repo = PeriodsRepository(app, Dispatchers.Unconfined, mockWorkoutRepo)
        assertNull("migrationStatus must remain null with no SQLite rollup DB", repo.migrationStatus.value)
    }

    @Test
    fun testReactiveProjection_multiSportMultiYearDstLocaleEmptyGapsAndRange_TST_PER_056_2() {
        val workoutsFlow = MutableStateFlow(
            listOf(
                // 2025-12-29 (Mon, ISO 2026-W01) outdoor bike
                workout(1L, LocalDateTime.of(2025, 12, 29, 9, 0), BSportType.BIKE, "Road", 2L, 3600L, 30_000.0, 400L),
                // 2026-03-29 (DST spring-forward Sunday in Europe/Berlin) outdoor run
                workout(2L, LocalDateTime.of(2026, 3, 29, 23, 15), BSportType.RUN, "Trail", 1L, 1800L, 10_000.0, 150L),
                // 2026-03-29 same day indoor bike (no GPS)
                workout(3L, LocalDateTime.of(2026, 3, 29, 10, 0), BSportType.BIKE, "Indoor", 3L, 2400L, 18_000.0, 0L, withGps = false),
                // 2026-04-02 (4 days later -> leaves 3 empty intermediate days Mar 30, Mar 31, Apr 1)
                workout(4L, LocalDateTime.of(2026, 4, 2, 17, 0), BSportType.BIKE, "Gravel", 4L, 5400L, 45_000.0, 650L),
                // Unfinished workout -> MUST be excluded
                workout(5L, LocalDateTime.of(2026, 4, 2, 19, 0), BSportType.RUN, "Trail", 1L, 9999L, 99_999.0, 999L, finished = false),
                // Invalid epoch-zero workout -> MUST be excluded
                workout(6L, LocalDateTime.of(2026, 4, 2, 12, 0), BSportType.BIKE, "Road", 2L, 1200L, 5_000.0, 10L).copy(startTimeS = 0L)
            )
        )
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        every { mockWorkoutRepo.allWorkouts } returns workoutsFlow

        var activeLocale = Locale.GERMANY
        val repo = PeriodsRepository(
            application = app,
            defaultDispatcher = Dispatchers.Unconfined,
            workoutRepo = mockWorkoutRepo,
            zoneProvider = { berlinZone },
            localeProvider = { activeLocale }
        )

        val grouped = repo.groupedPeriods.value
        assertEquals(4, grouped.size)
        val (days, weeks, months, years) = grouped

        // Descending sortKey order across all 4 levels
        assertEquals(listOf("2026-04-02", "2026-03-29", "2025-12-29"), days.map { it.sortKey })
        assertEquals(listOf("2026-W14", "2026-W13", "2026-W01"), weeks.map { it.sortKey })
        assertEquals(listOf("2026-04", "2026-03", "2025-12"), months.map { it.sortKey })
        assertEquals(listOf("2026", "2025"), years.map { it.sortKey })

        // DST day check (2026-03-29 = 23h = 82,800s) + multi-sport aggregation
        val dstDay = days[1]
        assertEquals(2, dstDay.totalWorkouts)
        assertEquals(4200L, dstDay.totalDurationSec)
        assertEquals(28_000.0, dstDay.totalDistance, 0.001)
        assertEquals(82_800L, dstDay.endTimestampS - dstDay.startTimestampS + 1L)
        assertEquals(1, dstDay.sportStats[BSportType.BIKE]?.count)
        assertEquals(1, dstDay.sportStats[BSportType.RUN]?.count)
        assertEquals(18_000.0, dstDay.sportStats[BSportType.BIKE]?.detailedSportStats?.get("Indoor")?.totalDistanceMeters ?: 0.0, 0.001)
        assertEquals(3L, dstDay.longestId)
        assertTrue("workoutIdToPathMap must stay empty (REQ-PER-039.1)", dstDay.workoutIdToPathMap.isEmpty())
        assertTrue("workoutIdToAllPolylinesMap must stay empty (REQ-PER-039.2)", dstDay.workoutIdToAllPolylinesMap.isEmpty())
        assertTrue("polylines bounded <= 8", dstDay.polylines.size <= MAX_SUMMARY_THUMBNAIL_TRACKS)
        assertNotNull("Primary region must be resolved for GPS day", dstDay.primaryRegionSummary)

        // Yearly & daily contribution maps populated
        val year2026 = years.first { it.sortKey == "2026" }
        assertEquals(3, year2026.totalWorkouts)
        assertTrue(year2026.yearlySportContributions.containsKey(BSportType.BIKE))
        assertTrue(year2026.yearlySportContributions.containsKey(BSportType.RUN))
        assertEquals(2026, year2026.yearlySportContributions[BSportType.BIKE]?.year)
        val marchMonth = months.first { it.sortKey == "2026-03" }
        assertTrue(marchMonth.dailySportContributions.containsKey(BSportType.RUN))
        assertTrue(marchMonth.periodLabel.contains("März", ignoreCase = true))

        // Dynamic locale propagation check (switch to US English -> labels adapt without cached formatter)
        activeLocale = Locale.US
        val usGrouped = repo.buildGroupedPeriods(workoutsFlow.value, berlinZone, activeLocale)
        assertTrue(usGrouped[2].first { it.sortKey == "2026-03" }.periodLabel.contains("March", ignoreCase = true))

        // Empty intermediate periods in PeriodBarGraph interpolation (Mar 29 -> Apr 2 yields 3 rest days)
        val marchAprilDays = listOf(days[0], days[1])
        val barItems = interpolateTimelineGaps(marchAprilDays, zoneId = berlinZone)
        assertEquals(5, barItems.size)
        assertEquals(3, barItems.count { it.isRest })

        // Indoor-only period retains spatial sentinel bounds cleanly
        val indoorOnly = repo.buildGroupedPeriods(
            listOf(workout(99L, LocalDateTime.of(2026, 5, 1, 8, 0), BSportType.BIKE, "Indoor", 3L, 1800L, 12_000.0, 0L, withGps = false)),
            berlinZone,
            Locale.US
        )
        val indoorDay = indoorOnly[0].single()
        assertEquals(90.0, indoorDay.minLat, 0.0001)
        assertEquals(-90.0, indoorDay.maxLat, 0.0001)
        assertEquals(99L, indoorDay.northId)

        // getWorkoutsForRange serves finished workouts sorted ascending, excluding unfinished
        val inRange = repo.getWorkoutsForRange(dstDay.startTimestampS, dstDay.endTimestampS)
        assertEquals(listOf(3L, 2L), inRange.map { it.id })
    }

    @Test
    fun testReactiveProjection_cropEditSportChangeDeleteAndNonPeriodDedup_TST_PER_056_3() {
        val original = workout(
            id = 10L,
            dateTime = LocalDateTime.of(2026, 6, 15, 8, 0),
            sport = BSportType.BIKE,
            sportName = "Road",
            sportId = 2L,
            durationSec = 3600L,
            distanceM = 30_000.0,
            ascentM = 450L
        )
        val companion = workout(
            id = 11L,
            dateTime = LocalDateTime.of(2026, 6, 16, 8, 0),
            sport = BSportType.RUN,
            sportName = "Trail",
            sportId = 1L,
            durationSec = 1800L,
            distanceM = 10_000.0,
            ascentM = 120L
        )
        val workoutsFlow = MutableStateFlow(listOf(original, companion))
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        every { mockWorkoutRepo.allWorkouts } returns workoutsFlow

        val repo = PeriodsRepository(
            application = app,
            defaultDispatcher = Dispatchers.Unconfined,
            workoutRepo = mockWorkoutRepo,
            zoneProvider = { berlinZone },
            localeProvider = { Locale.US }
        )

        // Baseline: 2 workouts -> Week/Month/Year distance = 40,000 m, duration = 5,400 s
        assertEquals(40_000.0, repo.groupedPeriods.value[1].single().totalDistance, 0.001)
        assertEquals(40_000.0, repo.groupedPeriods.value[2].single().totalDistance, 0.001)
        assertEquals(40_000.0, repo.groupedPeriods.value[3].single().totalDistance, 0.001)

        // 1. Non-period edit (name/description/clusterId) -> ZERO recomputation (same reference)
        val beforeNonPeriod = repo.groupedPeriods.value
        workoutsFlow.value = listOf(
            original.copy(workoutName = "Renamed Ride", description = "Updated notes", clusterId = 77L),
            companion
        )
        assertSame("Non-period edit must not trigger recomputation", beforeNonPeriod, repo.groupedPeriods.value)

        // 2. Simulate cropWorkout (SAME workout count = 2, reduced distance/duration/ascent)
        val cropped = original.copy(
            totalDistance = 21_500.0,
            activeTimeSec = 2500L,
            totalTimeSec = 2560L,
            ascentMeters = 280L
        )
        workoutsFlow.value = listOf(cropped, companion)

        // Every period level updates IMMEDIATELY despite identical workout count!
        val croppedDay = repo.groupedPeriods.value[0].first { it.sortKey == "2026-06-15" }
        val croppedWeek = repo.groupedPeriods.value[1].single()
        val croppedMonth = repo.groupedPeriods.value[2].single()
        val croppedYear = repo.groupedPeriods.value[3].single()

        assertEquals(21_500.0, croppedDay.totalDistance, 0.001)
        assertEquals(2500L, croppedDay.totalDurationSec)
        assertEquals(280L, croppedDay.sportStats[BSportType.BIKE]?.totalAscentMeters)
        assertEquals(31_500.0, croppedWeek.totalDistance, 0.001)
        assertEquals(4300L, croppedWeek.totalDurationSec)
        assertEquals(31_500.0, croppedMonth.totalDistance, 0.001)
        assertEquals(31_500.0, croppedYear.totalDistance, 0.001)

        // 3. Sport-type change (BIKE -> RUN) immediately updates sportStats breakdown
        val convertedToRun = cropped.copy(bSportType = BSportType.RUN, sportId = 1L, sportName = "Trail")
        workoutsFlow.value = listOf(convertedToRun, companion)
        val afterSportChange = repo.groupedPeriods.value[1].single()
        assertFalse("BIKE must disappear after sport change", afterSportChange.sportStats.containsKey(BSportType.BIKE))
        assertEquals(2, afterSportChange.sportStats[BSportType.RUN]?.count)
        assertEquals(31_500.0, afterSportChange.sportStats[BSportType.RUN]?.totalDistanceMeters ?: 0.0, 0.001)

        // 4. Deleting workout 10 immediately removes 2026-06-15 day bucket & updates parent totals
        workoutsFlow.value = listOf(companion)
        assertEquals(listOf("2026-06-16"), repo.groupedPeriods.value[0].map { it.sortKey })
        assertEquals(10_000.0, repo.groupedPeriods.value[1].single().totalDistance, 0.001)
        assertEquals(10_000.0, repo.groupedPeriods.value[3].single().totalDistance, 0.001)
    }

    @Test
    fun testReactiveProjection_5000WorkoutsCompletesQuicklyInMemory_TST_PER_056_2() {
        val baseDate = LocalDate.of(2016, 1, 1)
        val workouts = (1L..5000L).map { i ->
            val dt = baseDate.plusDays(i % 3650L).atTime(8, (i % 60).toInt())
            workout(
                id = i,
                dateTime = dt,
                sport = if (i % 3L == 0L) BSportType.RUN else BSportType.BIKE,
                durationSec = 1800L + (i % 3600L),
                distanceM = 10_000.0 + (i % 20_000L),
                ascentM = 100L + (i % 500L),
                withGps = false
            )
        }
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        every { mockWorkoutRepo.allWorkouts } returns MutableStateFlow(emptyList())
        val repo = PeriodsRepository(app, Dispatchers.Unconfined, mockWorkoutRepo)

        val startNs = System.nanoTime()
        val grouped = repo.buildGroupedPeriods(workouts, berlinZone, Locale.US)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L

        assertEquals(5000, grouped[0].sumOf { it.totalWorkouts })
        assertEquals(5000, grouped[1].sumOf { it.totalWorkouts })
        assertEquals(5000, grouped[2].sumOf { it.totalWorkouts })
        assertEquals(5000, grouped[3].sumOf { it.totalWorkouts })
        assertTrue("Projecting 5,000 workouts in memory took ${elapsedMs}ms (expected < 2000ms on CI JVM)", elapsedMs < 2000L)
    }

    @Test
    fun testObsoletePeriodSummariesDiskCleanup_deletesAllVariantsOnceEvenWithLegacyPref_TST_PER_056_4() {
        val databasesDir = File(context.applicationInfo.dataDir, "databases").apply { mkdirs() }
        val obsoleteNames = listOf(
            "PeriodSummaries.db",
            "PeriodSummaries.db-wal",
            "PeriodSummaries.db-shm",
            "PeriodSummaries.db-journal",
            "PeriodSummaries_v25.db"
        )
        obsoleteNames.forEach { File(databasesDir, it).writeText("obsolete") }
        val unrelatedDb = File(databasesDir, "WorkoutSummaries.db").apply { writeText("keep-me") }

        // Simulate existing V4/V5 install where old 'legacy_period_dbs_cleaned' is ALREADY true
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean("legacy_period_dbs_cleaned", true).commit()

        val deletedFirst = PeriodsRepository.cleanupObsoletePeriodDatabases(context, force = false)
        assertEquals(obsoleteNames.size, deletedFirst)
        obsoleteNames.forEach { assertFalse("$it must be deleted", File(databasesDir, it).exists()) }
        assertTrue("Unrelated WorkoutSummaries.db must stay intact", unrelatedDb.exists())
        assertTrue(prefs.getBoolean(PeriodsRepository.PREF_PERIOD_SUMMARIES_DB_DECOMMISSIONED_CLEANED, false))

        // Second non-forced run is gated by preference and performs zero deletions
        val replanted = File(databasesDir, "PeriodSummaries.db").apply { writeText("replanted") }
        assertEquals(0, PeriodsRepository.cleanupObsoletePeriodDatabases(context, force = false))
        assertTrue("Non-forced cleanup must be skipped after pref flag is set", replanted.exists())

        // Forced run (used post-restore) cleans up replanted files regardless of pref flag
        assertEquals(1, PeriodsRepository.cleanupObsoletePeriodDatabases(context, force = true))
        assertFalse("Forced post-restore cleanup must delete PeriodSummaries.db", replanted.exists())
    }

    @Test
    fun testObsoletePeriodSummariesDiskCleanup_doesNotSetPrefFlagWhenDeleteFails_TST_PER_056_4() {
        val databasesDir = File(context.applicationInfo.dataDir, "databases").apply { mkdirs() }
        val lockedFile = File(databasesDir, "PeriodSummaries.db").apply { writeText("locked") }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        assertFalse(prefs.getBoolean(PeriodsRepository.PREF_PERIOD_SUMMARIES_DB_DECOMMISSIONED_CLEANED, false))

        // Make databasesDir non-writable so File.delete() returns false without root privileges
        val readOnlySet = databasesDir.setWritable(false)
        try {
            if (readOnlySet && !databasesDir.canWrite()) {
                val deletedWhileLocked = PeriodsRepository.cleanupObsoletePeriodDatabases(context, force = false)
                assertEquals(0, deletedWhileLocked)
                assertTrue("Obsolete file must remain when delete fails", lockedFile.exists())
                assertFalse(
                    "Cleanup flag must NOT be persisted when any PeriodSummaries* deletion fails",
                    prefs.getBoolean(PeriodsRepository.PREF_PERIOD_SUMMARIES_DB_DECOMMISSIONED_CLEANED, false)
                )
            }
        } finally {
            databasesDir.setWritable(true)
        }

        // Subsequent retry once writable must succeed and persist the one-time cleanup flag
        val deletedOnRetry = PeriodsRepository.cleanupObsoletePeriodDatabases(context, force = false)
        assertEquals(1, deletedOnRetry)
        assertFalse("Obsolete file must be deleted on retry", lockedFile.exists())
        assertTrue(
            "Cleanup flag must be persisted once all obsolete files are deleted cleanly",
            prefs.getBoolean(PeriodsRepository.PREF_PERIOD_SUMMARIES_DB_DECOMMISSIONED_CLEANED, false)
        )
    }

    @Test
    fun testLegacyAttBackupContainingPeriodSummariesDb_validatesRestoresAndPurges_TST_PER_056_4() {
        val stagingDir = File(context.cacheDir, "att8131_legacy_backup_staging").apply {
            deleteRecursively()
            mkdirs()
        }
        val dbDir = File(stagingDir, "databases").apply { mkdirs() }

        // Create authentic SQLite WorkoutSummaries.db + legacy PeriodSummaries.db (v27)
        val summariesFile = File(dbDir, "WorkoutSummaries.db")
        SQLiteDatabase.openOrCreateDatabase(summariesFile, null).use { db ->
            db.execSQL("CREATE TABLE test_marker (_id INTEGER PRIMARY KEY)")
            db.version = WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper.DB_VERSION
        }
        val legacyPeriodsFile = File(dbDir, "PeriodSummaries.db")
        SQLiteDatabase.openOrCreateDatabase(legacyPeriodsFile, null).use { db ->
            db.execSQL("CREATE TABLE PeriodSummaries (_id INTEGER PRIMARY KEY)")
            db.version = 27
        }

        val previousRestarter = MigrationEngine.appRestarter
        var restartCount = 0
        MigrationEngine.appRestarter = { restartCount++ }
        try {
            // 1. Validation must accept legacy .attbackup containing PeriodSummaries.db (v27)
            val validation = MigrationEngine.validateBackupArchive(stagingDir)
            assertTrue("Legacy backup containing PeriodSummaries.db must validate cleanly: $validation", validation is MigrationEngine.ValidationResult.Valid)

            // 2. Pack valid .attbackup ZIP and execute full atomic restore without terminating test JVM
            val backupZip = File(context.cacheDir, "att8131_legacy.attbackup")
            ZipOutputStream(FileOutputStream(backupZip)).use { zos ->
                listOf(summariesFile, legacyPeriodsFile).forEach { file ->
                    zos.putNextEntry(ZipEntry("databases/${file.name}"))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }

            try {
                val liveDbDir = File(context.applicationInfo.dataDir, "databases").apply { mkdirs() }
                File(liveDbDir, "existing_placeholder.db").writeText("old")

                val restored = MigrationEngine.performFullRestore(context, backupZip)
                assertTrue("performFullRestore must succeed on legacy .attbackup with PeriodSummaries.db", restored)
                assertEquals("appRestarter must be invoked exactly once post-restore", 1, restartCount)

                assertTrue("Restored WorkoutSummaries.db must exist", File(liveDbDir, "WorkoutSummaries.db").exists())
                assertFalse(
                    "Restored legacy PeriodSummaries.db must be purged post-swap (REQ-PER-056.4)",
                    File(liveDbDir, "PeriodSummaries.db").exists()
                )
            } finally {
                backupZip.delete()
            }
        } finally {
            MigrationEngine.appRestarter = previousRestarter
            stagingDir.deleteRecursively()
        }
    }
}
