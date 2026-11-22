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

package com.atrainingtracker.trainingtracker.segments

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaSyncScheduler
import java.text.DateFormat
import java.util.Date

/**
 * Background [CoroutineWorker] that periodically synchronizes starred Strava segments
 * across both [BSportType.BIKE] and [BSportType.RUN] disciplines via [SegmentsRepository] (REQ-EXP-011, TST-EXP-008).
 *
 * Scheduling is managed via [schedule], respecting athlete configuration in [TrainingApplication]
 * and enforcing network connectivity and battery constraints.
 */
class StravaSegmentsSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting periodic automated Strava segments sync...")

        // Always enforce 7-day TTL cache retention for Strava segments (Section 6.2 compliance)
        SegmentsRepository.getInstance(applicationContext).pruneExpiredSegments()

        val stravaConnected = TrainingApplication.getStravaAccessToken() != null
        val enabled = TrainingApplication.isAutomatedStravaSegmentsSyncEnabled()
        if (!stravaConnected || !enabled) {
            Log.d(TAG, "Strava disconnected or automated sync disabled. Skipping periodic sync.")
            return Result.success()
        }

        val refreshedToken = com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper.getRefreshedAccessToken()
        if (refreshedToken.isNullOrBlank()) {
            Log.w(TAG, "Strava access token refresh returned null or empty. Skipping periodic segments sync.")
            return Result.success()
        }

        return try {
            val repository = SegmentsRepository.getInstance(applicationContext)
            repository.syncStarredSegments(BSportType.UNKNOWN)

            val timestamp = DateFormat.getDateTimeInstance().format(Date())
            TrainingApplication.setLastUpdateTimeOfStravaSegments(timestamp)

            Log.i(TAG, "Periodic automated Strava segments sync completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during periodic Strava segments sync", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "StravaSegmentsSyncWork"
        const val WORK_NAME = "automated_strava_segments_sync_work"

        /**
         * Schedules or cancels the periodic Strava segments synchronization worker.
         * Runs periodically according to athlete preference whenever authenticated with Strava.
         *
         * @param context Application or activity context.
         */
        fun schedule(context: Context) {
            StravaSyncScheduler.schedulePeriodicSync(
                context = context,
                workerClass = StravaSegmentsSyncWorker::class.java,
                tag = TAG,
                workName = WORK_NAME,
                entityLabel = "segments",
                isEnabled = { TrainingApplication.isAutomatedStravaSegmentsSyncEnabled() },
                getIntervalDays = { TrainingApplication.getStravaSegmentsSyncIntervalDays() }
            )
        }
    }
}
