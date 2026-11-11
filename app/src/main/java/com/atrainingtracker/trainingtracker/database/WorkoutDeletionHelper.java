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
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository;

import java.util.List;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/**
 * Encapsulated deletion helper delegating cascade deletion to {@link WorkoutRepository} (REQ-DAT-042.5).
 */
public class WorkoutDeletionHelper {

    private static final String TAG = WorkoutDeletionHelper.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);

    private final Context mContext;
    private final WorkoutRepository mWorkoutRepository;

    /**
     * Constructor for the deletion helper.
     *
     * @param context The application context.
     */
    public WorkoutDeletionHelper(@NonNull Context context) {
        this(context, WorkoutRepository.getInstance(context));
    }

    /**
     * Testing constructor allowing injection of mock WorkoutRepository.
     */
    @androidx.annotation.VisibleForTesting
    public WorkoutDeletionHelper(@NonNull Context context, @NonNull WorkoutRepository workoutRepository) {
        this.mContext = context.getApplicationContext();
        this.mWorkoutRepository = workoutRepository;
    }

    /**
     * Deletes a workout and all its related data across all databases via {@link WorkoutRepository}.
     *
     * @param workoutId The ID of the workout to delete.
     * @return {@code true} if deletion was attempted and succeeded.
     */
    public boolean deleteWorkout(long workoutId) {
        return mWorkoutRepository.deleteWorkoutCascade(workoutId);
    }

    public interface DeletionProgressCallback {
        void onProgress(int current, int total, long workoutId);
    }

    /**
     * Bulk deletes all workouts older than the specified retention period with detailed progress tracking.
     *
     * @param daysToKeep Number of days of workout history to preserve.
     * @param progressCallback Callback invoked with current index, total count, and deleted workout ID.
     * @return {@code true} if the operation completed successfully.
     */
    public boolean deleteOldWorkouts(int daysToKeep, DeletionProgressCallback progressCallback) {
        Log.i(TAG, "deleteOldWorkouts(" + daysToKeep + ")");
        return mWorkoutRepository.deleteOldWorkoutsCascade(daysToKeep, progressCallback);
    }

    /**
     * Bulk deletes all workouts older than the specified retention period.
     * Backwards-compatible overload for single-parameter callback callers.
     *
     * @param daysToKeep Number of days of workout history to preserve.
     * @param progressCallback Callback invoked with each deleted workout ID for UI progress tracking.
     * @return {@code true} if the operation completed successfully.
     */
    public boolean deleteOldWorkouts(int daysToKeep, Function1<Long, Unit> progressCallback) {
        return deleteOldWorkouts(daysToKeep, progressCallback != null ? (current, total, workoutId) -> progressCallback.invoke(workoutId) : null);
    }
}
