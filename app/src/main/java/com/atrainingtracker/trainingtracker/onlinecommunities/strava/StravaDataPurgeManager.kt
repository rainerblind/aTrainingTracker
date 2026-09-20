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

package com.atrainingtracker.trainingtracker.onlinecommunities.strava

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Coordinates comprehensive deletion of Strava data upon token invalidation,
 * user deauthorization, or unrecoverable OAuth revocation (REQ-EXT-009, TST-EXT-006, ATT-1078).
 *
 * Enforces strict compliance with Strava API Policy Section 7.4 ("Deletion Obligation")
 * while preserving native athlete recordings (summaries, samples, laps), local routes,
 * and hardware sensor pairings in Equipment.db.
 */
object StravaDataPurgeManager {

    private const val TAG = "StravaDataPurgeManager"
    private const val DEAUTHORIZE_URL = "https://www.strava.com/oauth/deauthorize"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Executes asynchronous purge of all Strava-originated data across SharedPreferences,
     * database tables, and scheduled background workers.
     *
     * @param context Application or UI context.
     * @param alsoRevokeRemote When true, attempts to call Strava's /oauth/deauthorize endpoint
     *                         prior to wiping local credentials.
     * @param onComplete Optional completion callback dispatched on Dispatchers.Main.
     */
    @JvmOverloads
    fun purgeAllStravaData(
        context: Context,
        alsoRevokeRemote: Boolean = false,
        onComplete: Runnable? = null
    ) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                if (alsoRevokeRemote) {
                    revokeRemoteAccessSafely(appContext)
                }

                executeLocalDataPurge(appContext)

                withContext(Dispatchers.Main) {
                    onComplete?.run()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during Strava data purge", e)
                withContext(Dispatchers.Main) {
                    onComplete?.run()
                }
            }
        }
    }

    /**
     * Synchronously executes the local data purge. Suitable for worker threads or tests.
     */
    fun executeLocalDataPurge(context: Context) {
        Log.i(TAG, "Initiating full Strava local data purge...")

        // 1. SharedPreferences: Clear tokens, athlete ID, and toggles
        TrainingApplication.deleteStravaToken()

        // 2. StravaUpload.db: Purge activity JSON, segment efforts, and Strava IDs
        try {
            val uploadDb = StravaUploadDbHelper(context)
            uploadDb.clearAllStravaData()
            Log.d(TAG, "Purged StravaUploadDbHelper")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to purge StravaUploadDbHelper", e)
        }

        // 3. Segments.db: Wipe starred segments and streams
        try {
            val segmentsDb = SegmentsDatabaseManager.getInstance(context)
            segmentsDb.deleteAllTables()
            Log.d(TAG, "Purged SegmentsDatabaseManager")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to purge SegmentsDatabaseManager", e)
        }

        // 4. Routes.db: Delete all Strava-origin routes (points cascade-delete)
        try {
            val routesDb = RoutesDatabaseManager.getInstance(context)
            routesDb.deleteRoutesBySource(RouteSource.STRAVA)
            Log.d(TAG, "Purged Strava routes from RoutesDatabaseManager")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to purge Strava routes", e)
        }

        // 5. Equipment.db: Unlink Strava IDs and names (preserves local equipment & sensor links)
        try {
            val equipmentDb = EquipmentDbHelper(context)
            equipmentDb.unlinkAllStravaEquipment()
            Log.d(TAG, "Unlinked Strava equipment in EquipmentDbHelper")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unlink Strava equipment", e)
        }

        // 6. WorkManager: Cancel periodic synchronization workers
        try {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork("automated_strava_segments_sync_work")
            workManager.cancelUniqueWork("automated_strava_routes_sync_work")
            Log.d(TAG, "Canceled Strava WorkManager tasks")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel WorkManager tasks", e)
        }

        // 7. Auth Repository: Reset in-memory state
        StravaAuthRepository.getInstance().resetState()

        Log.i(TAG, "Strava local data purge successfully completed.")
    }

    private fun revokeRemoteAccessSafely(context: Context) {
        val token = TrainingApplication.getStravaAccessToken()
        if (token.isNullOrBlank()) {
            Log.d(TAG, "No active access token to revoke remotely.")
            return
        }

        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(DEAUTHORIZE_URL)
                .header("Authorization", "Bearer $token")
                .post(FormBody.Builder().build())
                .build()

            client.newCall(request).execute().use { response ->
                Log.i(TAG, "Remote deauthorization response: ${response.code}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Remote deauthorization request failed (will proceed with local purge)", e)
        }
    }
}
