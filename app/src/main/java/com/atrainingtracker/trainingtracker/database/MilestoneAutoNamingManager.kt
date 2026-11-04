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

package com.atrainingtracker.trainingtracker.database

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Manages milestone workout detection, automatic title augmentation,
 * and first-jubilee celebration prompting (REQ-REV-001, REQ-REV-002, REQ-REV-004 / ATT-2308).
 */
object MilestoneAutoNamingManager {

    private const val TAG = "MilestoneAutoNaming"

    const val EXTRA_IS_FIRST_JUBILEE = "is_first_jubilee"
    const val EXTRA_MILESTONE_COUNT = "milestone_count"
    const val EXTRA_MILESTONE_SPORT_NAME = "milestone_sport_name"

    data class MilestoneCelebrationData(
        val workoutId: Long,
        val count: Int,
        val sportName: String
    )

    /**
     * Determines whether the given workout count corresponds to an athletic milestone (REQ-REV-001).
     * Thresholds: 100, 250, 500, 1000, 1500, 2000, and subsequent increments of 500.
     */
    @JvmStatic
    fun isMilestone(count: Int): Boolean {
        if (count <= 0) return false
        return count == 100 || count == 250 || (count >= 500 && count % 500 == 0)
    }

    /**
     * Formats the milestone-augmented workout title (REQ-REV-002).
     * Pattern: "[Count]. [SportName]: [BaseTitle]" (e.g. "100. Radfahrt: Hausrunde").
     */
    @JvmStatic
    fun formatMilestoneTitle(
        context: Context,
        count: Int,
        sportName: String,
        baseTitle: String
    ): String {
        val cleanBase = baseTitle.trim()
        val cleanSport = sportName.trim().ifEmpty {
            context.getString(R.string.sport_types)
        }
        val prefix = "$count."
        // Avoid double-prefixing if already formatted
        if (cleanBase.startsWith(prefix)) {
            return cleanBase
        }
        val effectiveBase = cleanBase.ifEmpty { cleanSport }
        return context.getString(R.string.milestone_workout_name_format, count, cleanSport, effectiveBase)
    }

    /**
     * Inspects the newly finished workout in SQLite, augments its title if it achieves a sport milestone,
     * and evaluates whether to trigger the first-jubilee celebration dialog (REQ-REV-001, REQ-REV-002, REQ-REV-004).
     *
     * @return [MilestoneCelebrationData] if this workout is the athlete's very first milestone ever, or null otherwise.
     */
    @JvmStatic
    @JvmOverloads
    fun evaluateAndApplyMilestone(
        context: Context,
        workoutId: Long,
        sportId: Long,
        bSportType: BSportType? = null,
        settingsDataStore: SettingsDataStore = SettingsDataStore.getInstance(context)
    ): MilestoneCelebrationData? {
        if (workoutId <= 0) return null
        try {
            val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context)
            val count = if (sportId > 0) {
                summariesDb.getFinishedWorkoutCountForSport(sportId)
            } else if (bSportType != null && bSportType != BSportType.UNKNOWN) {
                summariesDb.getFinishedWorkoutCountForSportType(bSportType)
            } else {
                0
            }

            if (!isMilestone(count)) {
                return null
            }

            val sportTypeManager = SportTypeDatabaseManager.getInstance(context)
            val sportName = if (sportId > 0) {
                sportTypeManager.getUIName(sportId)
            } else if (bSportType != null) {
                bSportType.name
            } else {
                ""
            }

            val currentName = summariesDb.getString(workoutId, WorkoutSummaries.WORKOUT_NAME) ?: ""
            val milestoneTitle = formatMilestoneTitle(context, count, sportName, currentName)

            // Persist the milestone title into SQLite before cloud sync / TCX / FIT export (REQ-REV-002)
            val values = ContentValues().apply {
                put(WorkoutSummaries.WORKOUT_NAME, milestoneTitle)
            }
            summariesDb.database.update(
                WorkoutSummaries.TABLE,
                values,
                "${WorkoutSummaries.C_ID}=?",
                arrayOf(workoutId.toString())
            )

            // Check single-prompt invariant: show celebration dialog strictly once in lifetime (REQ-REV-004)
            val hasShown = runBlocking {
                try {
                    settingsDataStore.hasShownFirstJubileePrompt.first()
                } catch (e: Exception) {
                    false
                }
            }

            return if (!hasShown) {
                MilestoneCelebrationData(workoutId, count, sportName)
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error evaluating milestone for workout $workoutId", t)
            return null
        }
    }
}
