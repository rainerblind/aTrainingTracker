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

package com.atrainingtracker.trainingtracker.database;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager;

import java.util.List;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class WorkoutDeletionHelper {

    private static final String TAG = WorkoutDeletionHelper.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);

    private final Context mContext;
    private final WorkoutSummariesDatabaseManager mSummariesManager;
    private final LapsDatabaseManager mLapsManager;
    private final WorkoutSamplesDatabaseManager mSamplesManager;
    private final ExportStatusDatabaseManager mExportStatusRepo;

    /**
     * Constructor for the deletion helper.
     *
     * @param context The application context.
     */
    public WorkoutDeletionHelper(@NonNull Context context) {
        this(
                context,
                WorkoutSummariesDatabaseManager.getInstance(context),
                LapsDatabaseManager.getInstance(context),
                WorkoutSamplesDatabaseManager.getInstance(context),
                ExportStatusDatabaseManager.getInstance(context)
        );
    }

    /**
     * Testing constructor allowing injection of mock database managers.
     */
    @androidx.annotation.VisibleForTesting
    public WorkoutDeletionHelper(
            @NonNull Context context,
            @NonNull WorkoutSummariesDatabaseManager summariesManager,
            @NonNull LapsDatabaseManager lapsManager,
            @NonNull WorkoutSamplesDatabaseManager samplesManager,
            @NonNull ExportStatusDatabaseManager exportStatusRepo
    ) {
        this.mContext = context.getApplicationContext();
        this.mSummariesManager = summariesManager;
        this.mLapsManager = lapsManager;
        this.mSamplesManager = samplesManager;
        this.mExportStatusRepo = exportStatusRepo;
    }

    /**
     * Deletes a workout and all its related data across all databases.
     * This method orchestrates the entire deletion process.
     *
     * <p>Forensic sequencing fix (ATT-296): {@code getBaseFileName} must be queried
     * BEFORE deleting the summary record from SQLite, otherwise {@code fileBaseName}
     * resolves to null and high-frequency sample tables are permanently orphaned.
     *
     * @param workoutId The ID of the workout to delete.
     * @return {@code true} if the workout was found and deletion was attempted, {@code false} otherwise.
     */
    public boolean deleteWorkout(long workoutId) {
        String fileBaseName = mSummariesManager.getBaseFileName(workoutId);

        mSummariesManager.deleteWorkout(workoutId);
        mLapsManager.deleteWorkout(workoutId);

        if (fileBaseName != null) {
            mSamplesManager.deleteWorkout(fileBaseName);
            mExportStatusRepo.deleteWorkout(fileBaseName);
        }

        return true;
    }

    /**
     * Bulk deletes all workouts older than the specified retention period.
     *
     * @param daysToKeep Number of days of workout history to preserve.
     * @param progressCallback Callback invoked with each deleted workout ID for UI progress tracking.
     * @return {@code true} if the operation completed successfully.
     */
    public boolean deleteOldWorkouts(int daysToKeep, Function1<Long, Unit> progressCallback) {
        Log.i(TAG, "deleteOldWorkouts(" + daysToKeep + ")");
        List<Long> oldWorkoutIds = WorkoutSummariesDatabaseManager.getInstance(mContext).getOldWorkouts(daysToKeep);
        for (long workoutId : oldWorkoutIds) {
            Log.d(TAG, "Deleting workout with ID: " + workoutId);

            if (progressCallback != null) {
                progressCallback.invoke(workoutId);
            }

            deleteWorkout(workoutId);
        }

        return true;
    }
}

