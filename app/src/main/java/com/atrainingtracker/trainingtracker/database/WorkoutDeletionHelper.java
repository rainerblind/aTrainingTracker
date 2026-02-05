package com.atrainingtracker.trainingtracker.database;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusRepository;

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
    private final ExportStatusRepository mExportStatusRepo;

    /**
     * Constructor for the deletion helper.
     *
     * @param context The application context.
     */
    public WorkoutDeletionHelper(@NonNull Context context) {
        this.mContext = context.getApplicationContext();

        // Get instances of all required managers
        this.mSummariesManager = WorkoutSummariesDatabaseManager.getInstance(mContext);
        this.mLapsManager = LapsDatabaseManager.getInstance(mContext);
        this.mSamplesManager = WorkoutSamplesDatabaseManager.getInstance(mContext);
        this.mExportStatusRepo = ExportStatusRepository.getInstance(mContext);
    }

    /**
     * Deletes a workout and all its related data across all databases.
     * This method orchestrates the entire deletion process.
     *
     * @param workoutId The ID of the workout to delete.
     * @return {@code true} if the workout was found and deletion was attempted, {@code false} otherwise.
     */
    public boolean deleteWorkout(long workoutId) {

        mSummariesManager.deleteWorkout(workoutId);
        mLapsManager.deleteWorkout(workoutId);

        String fileBaseName = mSummariesManager.getBaseFileName(workoutId);
        mSamplesManager.deleteWorkout(fileBaseName);
        mExportStatusRepo.deleteWorkout(fileBaseName);

        return true;
    }

    public boolean deleteOldWorkouts(int daysToKeep, Function1<Long, Unit> progressCallback) {
        Log.i(TAG, "deleteOldWorkouts(" + daysToKeep + ")");
        List<Long> oldWorkoutIds = WorkoutSummariesDatabaseManager.getInstance(mContext).getOldWorkouts(daysToKeep);
        for (long workoutId : oldWorkoutIds) {
            Log.d(TAG, "Deleting workout with ID: " + workoutId);

            progressCallback.invoke(workoutId);

            deleteWorkout(workoutId);
        }

        return true;
    }
}

