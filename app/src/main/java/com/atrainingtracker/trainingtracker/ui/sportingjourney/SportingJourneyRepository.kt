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

package com.atrainingtracker.trainingtracker.ui.sportingjourney

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.banalservice.sensor.SensorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Offline SQLite repository aggregating athlete retrospective data for "The Trophy Vault" (REQ-SPT-002).
 *
 * Executes strictly on [Dispatchers.IO] against existing `WorkoutSummaries` and `ExtremumValues` tables
 * with zero schema modifications.
 */
class SportingJourneyRepository private constructor(private val context: Context) {

    private val dbManager: WorkoutSummariesDatabaseManager =
        WorkoutSummariesDatabaseManager.getInstance(context)

    companion object {
        @Volatile
        private var instance: SportingJourneyRepository? = null

        fun getInstance(context: Context): SportingJourneyRepository {
            return instance ?: synchronized(this) {
                instance ?: SportingJourneyRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Loads the distinct calendar years available in [WorkoutSummaries] on [Dispatchers.IO].
     */
    suspend fun getAvailableYears(): List<Int> = withContext(Dispatchers.IO) {
        val years = mutableListOf<Int>()
        val db = try { dbManager.database } catch (_: Throwable) { return@withContext emptyList() }
        if (!db.isOpen) return@withContext emptyList()

        val query = """
            SELECT DISTINCT CAST(strftime('%Y', ${WorkoutSummaries.TIME_START}) AS INTEGER) AS yr
            FROM ${WorkoutSummaries.TABLE}
            WHERE ${WorkoutSummaries.FINISHED} = 1 AND ${WorkoutSummaries.TIME_START} IS NOT NULL
            ORDER BY yr DESC
        """.trimIndent()

        try {
            db.rawQuery(query, null).use { cursor ->
                val yrIdx = cursor.getColumnIndex("yr")
                while (cursor.moveToNext()) {
                    if (yrIdx >= 0 && !cursor.isNull(yrIdx)) {
                        val year = cursor.getInt(yrIdx)
                        if (year > 1990) {
                            years.add(year)
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // Safe fallback
        }
        years
    }

    /**
     * Loads complete retrospective journey data for the given [horizon] and [sportFilter].
     */
    suspend fun loadJourneyData(
        horizon: SportingHorizon = SportingHorizon.AllTime,
        sportFilter: SportingSportFilter = SportingSportFilter.ALL
    ): SportingJourneyData = withContext(Dispatchers.IO) {
        val db = try { dbManager.database } catch (_: Throwable) {
            return@withContext SportingJourneyData(horizon = horizon, sportFilter = sportFilter)
        }
        if (!db.isOpen) {
            return@withContext SportingJourneyData(horizon = horizon, sportFilter = sportFilter)
        }

        val availableYears = getAvailableYears()

        // 1. Build SQL where filter fragments
        val whereClauses = mutableListOf("${WorkoutSummaries.FINISHED} = 1")
        val selectionArgs = mutableListOf<String>()

        when (sportFilter) {
            SportingSportFilter.ALL -> {
                whereClauses.add("${WorkoutSummaries.B_SPORT} IN ('BIKE', 'RUN')")
            }
            SportingSportFilter.CYCLING -> {
                whereClauses.add("${WorkoutSummaries.B_SPORT} = 'BIKE'")
            }
            SportingSportFilter.RUNNING -> {
                whereClauses.add("${WorkoutSummaries.B_SPORT} = 'RUN'")
            }
        }

        when (horizon) {
            is SportingHorizon.AllTime -> {
                // No year filtering
            }
            is SportingHorizon.Year -> {
                whereClauses.add("strftime('%Y', ${WorkoutSummaries.TIME_START}) = ?")
                selectionArgs.add(horizon.year.toString())
            }
        }

        val whereSql = whereClauses.joinToString(" AND ")
        val argsArray = if (selectionArgs.isEmpty()) null else selectionArgs.toTypedArray()

        // 2. Query Hero Totals and high-level milestones
        var totalWorkouts = 0
        var totalDistanceM = 0.0
        var totalActiveS = 0L
        var totalElevationM = 0
        var distinctClusters = 0
        var ritualCount = 0
        var explorerCount = 0
        var firstWorkoutDateStr: String? = null
        var maxDistanceM = 0.0
        var maxElevationM = 0

        val heroQuery = """
            SELECT
                COUNT(*) AS total_count,
                IFNULL(SUM(${WorkoutSummaries.DISTANCE_TOTAL_m}), 0.0) AS sum_dist,
                IFNULL(SUM(${WorkoutSummaries.TIME_ACTIVE_s}), 0) AS sum_active_s,
                IFNULL(SUM(${WorkoutSummaries.ASCENDING}), 0) AS sum_elevation,
                COUNT(DISTINCT CASE WHEN ${WorkoutSummaries.CLUSTER_ID} > 0 THEN ${WorkoutSummaries.CLUSTER_ID} END) AS num_clusters,
                COUNT(CASE WHEN ${WorkoutSummaries.CLUSTER_ID} > 0 THEN 1 END) AS ritual_cnt,
                COUNT(CASE WHEN ${WorkoutSummaries.CLUSTER_ID} <= 0 OR ${WorkoutSummaries.CLUSTER_ID} IS NULL THEN 1 END) AS explorer_cnt,
                MIN(${WorkoutSummaries.TIME_START}) AS first_time,
                MAX(${WorkoutSummaries.DISTANCE_TOTAL_m}) AS max_dist,
                MAX(${WorkoutSummaries.ASCENDING}) AS max_elev
            FROM ${WorkoutSummaries.TABLE}
            WHERE $whereSql
        """.trimIndent()

        try {
            db.rawQuery(heroQuery, argsArray).use { cursor ->
                if (cursor.moveToFirst()) {
                    totalWorkouts = cursor.getInt(cursor.getColumnIndexOrThrow("total_count"))
                    totalDistanceM = cursor.getDouble(cursor.getColumnIndexOrThrow("sum_dist"))
                    totalActiveS = cursor.getLong(cursor.getColumnIndexOrThrow("sum_active_s"))
                    totalElevationM = cursor.getInt(cursor.getColumnIndexOrThrow("sum_elevation"))
                    distinctClusters = cursor.getInt(cursor.getColumnIndexOrThrow("num_clusters"))
                    ritualCount = cursor.getInt(cursor.getColumnIndexOrThrow("ritual_cnt"))
                    explorerCount = cursor.getInt(cursor.getColumnIndexOrThrow("explorer_cnt"))
                    firstWorkoutDateStr = cursor.getString(cursor.getColumnIndexOrThrow("first_time"))
                    maxDistanceM = cursor.getDouble(cursor.getColumnIndexOrThrow("max_dist"))
                    maxElevationM = cursor.getInt(cursor.getColumnIndexOrThrow("max_elev"))
                }
            }
        } catch (_: Throwable) {}

        val totalDistanceKm = totalDistanceM / 1000.0
        val totalActiveHours = totalActiveS.toDouble() / 3600.0
        val equatorPct = SportingJourneyCalculator.calculateEquatorPercentage(totalDistanceKm)
        val everestMultiplier = SportingJourneyCalculator.calculateEverestMultiplier(totalElevationM.toDouble())
        val nonstopDays = SportingJourneyCalculator.calculateNonstopDays(totalActiveHours)

        val heroTotals = SportingHeroTotals(
            totalDistanceKm = totalDistanceKm,
            equatorPercentage = equatorPct,
            totalWorkouts = totalWorkouts,
            totalActiveHours = totalActiveHours,
            nonstopDays = nonstopDays,
            totalElevationM = totalElevationM,
            everestMultiplier = everestMultiplier,
            favoriteRoutesCount = distinctClusters
        )

        // 3. Hidden Gems: Cadence
        var cadenceRidesCount = 0
        var totalRevolutions = 0L
        val cadenceQuery = """
            SELECT
                COUNT(DISTINCT w.${WorkoutSummaries.C_ID}) AS cadence_cnt,
                IFNULL(SUM(e.${WorkoutSummaries.VALUE} * (w.${WorkoutSummaries.TIME_ACTIVE_s} / 60.0)), 0.0) AS total_revs
            FROM ${WorkoutSummaries.TABLE} w
            INNER JOIN ${WorkoutSummaries.TABLE_EXTREMA_VALUES} e ON w.${WorkoutSummaries.C_ID} = e.${WorkoutSummaries.WORKOUT_ID}
            WHERE ${whereClauses.joinToString(" AND ") { "w.$it" }}
              AND e.${WorkoutSummaries.SENSOR_TYPE} = '${SensorType.CADENCE.name}'
              AND e.${WorkoutSummaries.EXTREMA_TYPE} = '${ExtremaType.AVG.name}'
              AND e.${WorkoutSummaries.VALUE} > 0 AND e.${WorkoutSummaries.VALUE} <= 250
        """.trimIndent()

        try {
            db.rawQuery(cadenceQuery, argsArray).use { cursor ->
                if (cursor.moveToFirst()) {
                    cadenceRidesCount = cursor.getInt(cursor.getColumnIndexOrThrow("cadence_cnt"))
                    totalRevolutions = cursor.getDouble(cursor.getColumnIndexOrThrow("total_revs")).toLong()
                }
            }
        } catch (_: Throwable) {}

        // 4. Hidden Gems: Heart Rate
        var hrWorkoutsCount = 0
        var totalHeartbeats = 0L
        val hrQuery = """
            SELECT
                COUNT(DISTINCT w.${WorkoutSummaries.C_ID}) AS hr_cnt,
                IFNULL(SUM(e.${WorkoutSummaries.VALUE} * (w.${WorkoutSummaries.TIME_ACTIVE_s} / 60.0)), 0.0) AS total_beats
            FROM ${WorkoutSummaries.TABLE} w
            INNER JOIN ${WorkoutSummaries.TABLE_EXTREMA_VALUES} e ON w.${WorkoutSummaries.C_ID} = e.${WorkoutSummaries.WORKOUT_ID}
            WHERE ${whereClauses.joinToString(" AND ") { "w.$it" }}
              AND e.${WorkoutSummaries.SENSOR_TYPE} = '${SensorType.HR.name}'
              AND e.${WorkoutSummaries.EXTREMA_TYPE} = '${ExtremaType.AVG.name}'
              AND e.${WorkoutSummaries.VALUE} >= 30 AND e.${WorkoutSummaries.VALUE} <= 240
        """.trimIndent()

        try {
            db.rawQuery(hrQuery, argsArray).use { cursor ->
                if (cursor.moveToFirst()) {
                    hrWorkoutsCount = cursor.getInt(cursor.getColumnIndexOrThrow("hr_cnt"))
                    totalHeartbeats = cursor.getDouble(cursor.getColumnIndexOrThrow("total_beats")).toLong()
                }
            }
        } catch (_: Throwable) {}

        // 5. Hidden Gems: Mechanical Work / Power
        var powerWorkoutsCount = 0
        var totalMechanicalKj = 0.0
        val powerSubquery = """
            SELECT
                COUNT(*) AS pwr_cnt,
                IFNULL(SUM(workout_kj), 0.0) AS sum_kj
            FROM (
                SELECT
                    w.${WorkoutSummaries.C_ID},
                    COALESCE(
                        MAX(CASE WHEN e.${WorkoutSummaries.SENSOR_TYPE} = '${SensorType.WORK_KJ.name}' AND e.${WorkoutSummaries.EXTREMA_TYPE} IN ('END', 'AVG', 'MAX') THEN e.${WorkoutSummaries.VALUE} END),
                        MAX(CASE WHEN e.${WorkoutSummaries.SENSOR_TYPE} = '${SensorType.POWER.name}' AND e.${WorkoutSummaries.EXTREMA_TYPE} = '${ExtremaType.AVG.name}' AND e.${WorkoutSummaries.VALUE} > 0 AND e.${WorkoutSummaries.VALUE} <= 2000 THEN (e.${WorkoutSummaries.VALUE} * w.${WorkoutSummaries.TIME_ACTIVE_s} / 1000.0) END)
                    ) AS workout_kj
                FROM ${WorkoutSummaries.TABLE} w
                INNER JOIN ${WorkoutSummaries.TABLE_EXTREMA_VALUES} e ON w.${WorkoutSummaries.C_ID} = e.${WorkoutSummaries.WORKOUT_ID}
                WHERE ${whereClauses.joinToString(" AND ") { "w.$it" }}
                GROUP BY w.${WorkoutSummaries.C_ID}
                HAVING workout_kj IS NOT NULL AND workout_kj > 0
            )
        """.trimIndent()

        try {
            db.rawQuery(powerSubquery, argsArray).use { cursor ->
                if (cursor.moveToFirst()) {
                    powerWorkoutsCount = cursor.getInt(cursor.getColumnIndexOrThrow("pwr_cnt"))
                    totalMechanicalKj = cursor.getDouble(cursor.getColumnIndexOrThrow("sum_kj"))
                }
            }
        } catch (_: Throwable) {}

        val mechanicalKwh = SportingJourneyCalculator.calculateKwhFromKj(totalMechanicalKj)
        val ebikeKm = SportingJourneyCalculator.calculateEbikeKm(mechanicalKwh)
        val espressos = SportingJourneyCalculator.calculateEspressoCount(mechanicalKwh)
        val phoneCharges = SportingJourneyCalculator.calculateSmartphoneCharges(mechanicalKwh)

        // 6. Circadian Launch Window & Day of Week
        var mostActiveHour: Int? = null
        val hourQuery = """
            SELECT CAST(strftime('%H', ${WorkoutSummaries.TIME_START}) AS INTEGER) AS hr_start, COUNT(*) AS cnt
            FROM ${WorkoutSummaries.TABLE}
            WHERE $whereSql AND ${WorkoutSummaries.TIME_START} IS NOT NULL
            GROUP BY hr_start
            ORDER BY cnt DESC
            LIMIT 1
        """.trimIndent()

        try {
            db.rawQuery(hourQuery, argsArray).use { cursor ->
                if (cursor.moveToFirst()) {
                    mostActiveHour = cursor.getInt(cursor.getColumnIndexOrThrow("hr_start"))
                }
            }
        } catch (_: Throwable) {}

        var mostActiveDow: Int? = null
        val dowQuery = """
            SELECT CAST(strftime('%w', ${WorkoutSummaries.TIME_START}) AS INTEGER) AS dow, COUNT(*) AS cnt
            FROM ${WorkoutSummaries.TABLE}
            WHERE $whereSql AND ${WorkoutSummaries.TIME_START} IS NOT NULL
            GROUP BY dow
            ORDER BY cnt DESC
            LIMIT 1
        """.trimIndent()

        try {
            db.rawQuery(dowQuery, argsArray).use { cursor ->
                if (cursor.moveToFirst()) {
                    mostActiveDow = cursor.getInt(cursor.getColumnIndexOrThrow("dow"))
                }
            }
        } catch (_: Throwable) {}

        var earlyMorningStarts = 0
        val earlyQuery = """
            SELECT COUNT(*) AS early_cnt
            FROM ${WorkoutSummaries.TABLE}
            WHERE $whereSql AND ${WorkoutSummaries.TIME_START} IS NOT NULL
              AND CAST(strftime('%H', ${WorkoutSummaries.TIME_START}) AS INTEGER) < 7
        """.trimIndent()

        try {
            db.rawQuery(earlyQuery, argsArray).use { cursor ->
                if (cursor.moveToFirst()) {
                    earlyMorningStarts = cursor.getInt(cursor.getColumnIndexOrThrow("early_cnt"))
                }
            }
        } catch (_: Throwable) {}

        val ritualPct = SportingJourneyCalculator.calculateRitualPercentage(ritualCount, totalWorkouts)

        val hiddenGems = SportingHiddenGems(
            pedalRevolutions = totalRevolutions,
            cadenceTrackedRidesCount = cadenceRidesCount,
            estimatedHeartbeats = totalHeartbeats,
            hrTrackedWorkoutsCount = hrWorkoutsCount,
            mechanicalKwh = mechanicalKwh,
            mechanicalKj = totalMechanicalKj,
            powerTrackedWorkoutsCount = powerWorkoutsCount,
            ebikeKmEquivalent = ebikeKm,
            espressosEquivalent = espressos,
            phoneChargesEquivalent = phoneCharges,
            mostActiveHourStart = mostActiveHour,
            mostActiveDayOfWeek = mostActiveDow,
            ritualWorkoutsCount = ritualCount,
            explorerWorkoutsCount = explorerCount,
            ritualPercentage = ritualPct
        )

        // 7. Milestones
        val milestones = mutableListOf<SportingMilestone>()

        // Date parsing helper
        val dbDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val displayDateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

        var firstWorkoutDateFormatted: String? = null
        var daysSinceFirst = 0L

        if (!firstWorkoutDateStr.isNullOrBlank()) {
            try {
                val parsedDate = dbDateFormat.parse(firstWorkoutDateStr!!)
                if (parsedDate != null) {
                    firstWorkoutDateFormatted = displayDateFormat.format(parsedDate)
                    val diffMs = System.currentTimeMillis() - parsedDate.time
                    daysSinceFirst = TimeUnit.MILLISECONDS.toDays(diffMs).coerceAtLeast(1)
                }
            } catch (_: Throwable) {
                firstWorkoutDateFormatted = firstWorkoutDateStr
            }
        }

        // Milestone 1: First Recorded Workout
        val firstWorkoutQuery = """
            SELECT ${WorkoutSummaries.C_ID}, ${WorkoutSummaries.WORKOUT_NAME}, ${WorkoutSummaries.TIME_START}, ${WorkoutSummaries.DISTANCE_TOTAL_m}
            FROM ${WorkoutSummaries.TABLE}
            WHERE $whereSql AND ${WorkoutSummaries.TIME_START} IS NOT NULL
            ORDER BY ${WorkoutSummaries.TIME_START} ASC
            LIMIT 1
        """.trimIndent()

        try {
            db.rawQuery(firstWorkoutQuery, argsArray).use { cursor ->
                if (cursor.moveToFirst()) {
                    val wId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.C_ID))
                    val wName = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_NAME)) ?: context.getString(R.string.sporting_journey_milestone_first_title)
                    val distKm = cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_TOTAL_m)) / 1000.0
                    milestones.add(
                        SportingMilestone(
                            id = "first_workout",
                            emoji = "🌱",
                            title = context.getString(R.string.sporting_journey_milestone_first_title),
                            subtitle = firstWorkoutDateFormatted ?: "",
                            value = "${String.format(Locale.US, "%.1f", distKm)} km",
                            workoutId = wId
                        )
                    )
                }
            }
        } catch (_: Throwable) {}

        // Milestone 2: Longest Single Workout
        var longestWorkoutId: Long? = null
        var longestWorkoutKm = 0.0
        var longestWorkoutName: String? = null
        val longestQuery = """
            SELECT ${WorkoutSummaries.C_ID}, ${WorkoutSummaries.WORKOUT_NAME}, ${WorkoutSummaries.DISTANCE_TOTAL_m}, ${WorkoutSummaries.TIME_START}
            FROM ${WorkoutSummaries.TABLE}
            WHERE $whereSql
            ORDER BY ${WorkoutSummaries.DISTANCE_TOTAL_m} DESC
            LIMIT 1
        """.trimIndent()

        try {
            db.rawQuery(longestQuery, argsArray).use { cursor ->
                if (cursor.moveToFirst()) {
                    longestWorkoutId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.C_ID))
                    longestWorkoutKm = cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_TOTAL_m)) / 1000.0
                    longestWorkoutName = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_NAME))
                    val dateStr = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_START))
                    milestones.add(
                        SportingMilestone(
                            id = "longest_workout",
                            emoji = "🏆",
                            title = context.getString(R.string.sporting_journey_milestone_longest_title),
                            subtitle = longestWorkoutName ?: dateStr ?: "",
                            value = "${String.format(Locale.US, "%.1f", longestWorkoutKm)} km",
                            workoutId = longestWorkoutId
                        )
                    )
                }
            }
        } catch (_: Throwable) {}

        // Milestone 3: Highest Single Climb
        var highestClimbWorkoutId: Long? = null
        var highestClimbM = 0
        var highestClimbName: String? = null
        val climbQuery = """
            SELECT ${WorkoutSummaries.C_ID}, ${WorkoutSummaries.WORKOUT_NAME}, ${WorkoutSummaries.ASCENDING}, ${WorkoutSummaries.TIME_START}
            FROM ${WorkoutSummaries.TABLE}
            WHERE $whereSql
            ORDER BY ${WorkoutSummaries.ASCENDING} DESC
            LIMIT 1
        """.trimIndent()

        try {
            db.rawQuery(climbQuery, argsArray).use { cursor ->
                if (cursor.moveToFirst()) {
                    highestClimbWorkoutId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.C_ID))
                    highestClimbM = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.ASCENDING))
                    highestClimbName = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_NAME))
                    val dateStr = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_START))
                    milestones.add(
                        SportingMilestone(
                            id = "highest_climb",
                            emoji = "⛰️",
                            title = context.getString(R.string.sporting_journey_milestone_climb_title),
                            subtitle = highestClimbName ?: dateStr ?: "",
                            value = "$highestClimbM m",
                            workoutId = highestClimbWorkoutId
                        )
                    )
                }
            }
        } catch (_: Throwable) {}

        // Milestone 4: Busiest Month
        val monthQuery = """
            SELECT strftime('%Y-%m', ${WorkoutSummaries.TIME_START}) AS ym, COUNT(*) AS cnt, SUM(${WorkoutSummaries.DISTANCE_TOTAL_m}) AS dist_m
            FROM ${WorkoutSummaries.TABLE}
            WHERE $whereSql AND ${WorkoutSummaries.TIME_START} IS NOT NULL
            GROUP BY ym
            ORDER BY cnt DESC
            LIMIT 1
        """.trimIndent()

        try {
            db.rawQuery(monthQuery, argsArray).use { cursor ->
                if (cursor.moveToFirst()) {
                    val ym = cursor.getString(cursor.getColumnIndexOrThrow("ym"))
                    val cnt = cursor.getInt(cursor.getColumnIndexOrThrow("cnt"))
                    val distKm = cursor.getDouble(cursor.getColumnIndexOrThrow("dist_m")) / 1000.0
                    milestones.add(
                        SportingMilestone(
                            id = "busiest_month",
                            emoji = "🔥",
                            title = context.getString(R.string.sporting_journey_milestone_active_month_title),
                            subtitle = "$ym ($cnt ${context.getString(R.string.sporting_journey_hero_workouts_label).lowercase()})",
                            value = "${String.format(Locale.US, "%.0f", distKm)} km"
                        )
                    )
                }
            }
        } catch (_: Throwable) {}

        // 8. Achievement Trophies (Deterministic evaluation)
        val achievements = mutableListOf<SportingAchievement>()

        // Badge 1: Century Rider (>= 100 km)
        val centuryUnlocked = longestWorkoutKm >= 100.0
        achievements.add(
            SportingAchievement(
                id = "century_rider",
                emoji = "🌲",
                name = context.getString(R.string.sporting_journey_badge_century_title),
                subtitle = context.getString(R.string.sporting_journey_badge_century_sub),
                isUnlocked = centuryUnlocked,
                storyTitle = context.getString(R.string.sporting_journey_badge_century_story_title),
                storyText = if (centuryUnlocked) {
                    context.getString(
                        R.string.sporting_journey_badge_century_story_text,
                        longestWorkoutName ?: "",
                        longestWorkoutKm
                    )
                } else context.getString(R.string.sporting_journey_locked_hint),
                workoutId = if (centuryUnlocked) longestWorkoutId else null
            )
        )

        // Badge 2: Everest Club (>= 8,848 m)
        val everestUnlocked = totalElevationM >= 8848
        achievements.add(
            SportingAchievement(
                id = "everest_club",
                emoji = "🏔️",
                name = context.getString(R.string.sporting_journey_badge_everest_title),
                subtitle = context.getString(R.string.sporting_journey_badge_everest_sub),
                isUnlocked = everestUnlocked,
                storyTitle = context.getString(R.string.sporting_journey_badge_everest_story_title),
                storyText = if (everestUnlocked) {
                    context.getString(
                        R.string.sporting_journey_badge_everest_story_text,
                        totalElevationM.toString(),
                        everestMultiplier
                    )
                } else context.getString(R.string.sporting_journey_locked_hint)
            )
        )

        // Badge 3: Dawn Patrol (< 07:00 launches >= 1)
        val dawnUnlocked = earlyMorningStarts >= 1
        achievements.add(
            SportingAchievement(
                id = "dawn_patrol",
                emoji = "🌅",
                name = context.getString(R.string.sporting_journey_badge_dawn_title),
                subtitle = context.getString(R.string.sporting_journey_badge_dawn_sub),
                isUnlocked = dawnUnlocked,
                storyTitle = context.getString(R.string.sporting_journey_badge_dawn_story_title),
                storyText = if (dawnUnlocked) {
                    context.getString(
                        R.string.sporting_journey_badge_dawn_story_text,
                        earlyMorningStarts
                    )
                } else context.getString(R.string.sporting_journey_locked_hint)
            )
        )

        // Badge 4: Million Cranks (>= 1,000,000 pedal revolutions)
        val cranksUnlocked = totalRevolutions >= 1_000_000L
        achievements.add(
            SportingAchievement(
                id = "million_cranks",
                emoji = "⚙️",
                name = context.getString(R.string.sporting_journey_badge_cranks_title),
                subtitle = context.getString(R.string.sporting_journey_badge_cranks_sub),
                isUnlocked = cranksUnlocked,
                storyTitle = context.getString(R.string.sporting_journey_badge_cranks_story_title),
                storyText = if (cranksUnlocked) {
                    context.getString(R.string.sporting_journey_badge_cranks_story_text)
                } else context.getString(R.string.sporting_journey_locked_hint)
            )
        )

        // Badge 5: ANT+ & BLE Pioneer (>= 365 days of history)
        val pioneerUnlocked = daysSinceFirst >= 365
        achievements.add(
            SportingAchievement(
                id = "pioneer_athlete",
                emoji = "📡",
                name = context.getString(R.string.sporting_journey_badge_pioneer_title),
                subtitle = context.getString(R.string.sporting_journey_badge_pioneer_sub),
                isUnlocked = pioneerUnlocked,
                storyTitle = context.getString(R.string.sporting_journey_badge_pioneer_story_title),
                storyText = if (pioneerUnlocked) {
                    context.getString(R.string.sporting_journey_badge_pioneer_story_text, daysSinceFirst)
                } else context.getString(R.string.sporting_journey_locked_hint)
            )
        )

        // Badge 6: Local Legend (distinct clusters >= 5 or ritual count >= 10)
        val legendUnlocked = distinctClusters >= 5 || ritualCount >= 10
        achievements.add(
            SportingAchievement(
                id = "local_legend",
                emoji = "🔁",
                name = context.getString(R.string.sporting_journey_badge_legend_title),
                subtitle = context.getString(R.string.sporting_journey_badge_legend_sub),
                isUnlocked = legendUnlocked,
                storyTitle = context.getString(R.string.sporting_journey_badge_legend_story_title),
                storyText = if (legendUnlocked) {
                    context.getString(R.string.sporting_journey_badge_legend_story_text, ritualCount)
                } else context.getString(R.string.sporting_journey_locked_hint)
            )
        )

        // Badge 7: Human Power Plant (>= 60 kWh or >= 216,000 kJ)
        val powerPlantUnlocked = mechanicalKwh >= 60.0 || totalMechanicalKj >= 216_000.0
        achievements.add(
            SportingAchievement(
                id = "human_power_plant",
                emoji = "⚡",
                name = context.getString(R.string.sporting_journey_badge_powerplant_title),
                subtitle = context.getString(R.string.sporting_journey_badge_powerplant_sub),
                isUnlocked = powerPlantUnlocked,
                storyTitle = context.getString(R.string.sporting_journey_badge_powerplant_story_title),
                storyText = if (powerPlantUnlocked) {
                    context.getString(
                        R.string.sporting_journey_badge_powerplant_story_text,
                        mechanicalKwh,
                        String.format(Locale.US, "%,.0f", totalMechanicalKj)
                    )
                } else context.getString(R.string.sporting_journey_locked_hint)
            )
        )

        // Badge 8: Half-Way Around the World (>= 20,037.5 km)
        val worldUnlocked = totalDistanceKm >= 20_037.5
        achievements.add(
            SportingAchievement(
                id = "half_way_world",
                emoji = "🌍",
                name = context.getString(R.string.sporting_journey_badge_world_title),
                subtitle = context.getString(R.string.sporting_journey_badge_world_sub),
                isUnlocked = worldUnlocked,
                storyTitle = context.getString(R.string.sporting_journey_badge_world_story_title),
                storyText = if (worldUnlocked) {
                    context.getString(
                        R.string.sporting_journey_badge_world_story_text,
                        String.format(Locale.US, "%,.0f", totalDistanceKm)
                    )
                } else context.getString(R.string.sporting_journey_locked_hint)
            )
        )

        SportingJourneyData(
            horizon = horizon,
            sportFilter = sportFilter,
            availableYears = availableYears,
            heroTotals = heroTotals,
            hiddenGems = hiddenGems,
            milestones = milestones,
            achievements = achievements,
            firstWorkoutDateFormatted = firstWorkoutDateFormatted,
            daysSinceFirstWorkout = daysSinceFirst,
            athleteName = null,
            isLoading = false
        )
    }
}
