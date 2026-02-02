/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (C) 2011 - 2019 Rainer Blind <rainer.blind@gmail.com>
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

package com.atrainingtracker.trainingtracker.helpers;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusRepository;


public class DeleteWorkoutThread extends Thread {
    public static final String FINISHED_DELETING = "de.rainerblind.trainingtracker.helpers.DeleteWorkoutThread.FINISHED_DELETING";
    private static final String TAG = "DeleteWorkoutThread";
    private static final boolean DEBUG = false;
    @NonNull
    private final ProgressDialog progressDialog;
    private final Context context;
    private final Long[] oldWorkouts;

    public DeleteWorkoutThread(Context context, Long[] oldWorkouts) {
        this.context = context;
        this.oldWorkouts = oldWorkouts;
        progressDialog = new ProgressDialog(context);
    }

    @Override
    public void run() {
        new Handler(Looper.getMainLooper()).post(() -> {
            progressDialog.setMessage(context.getString(R.string.deleting_please_wait));
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.show();
        });

        SQLiteDatabase dbSummaries = WorkoutSummariesDatabaseManager.getInstance(context).getDatabase();
        LapsDatabaseManager lapsDBManager = LapsDatabaseManager.getInstance(context);

        Cursor cursor;

        for (long workoutId : oldWorkouts) {

            // TODO: use WorkoutSummariesDatabaseManger.deleteWorkout instead...

            if (DEBUG) Log.d(TAG, "delete workout " + workoutId);

            cursor = dbSummaries.query(WorkoutSummaries.TABLE, null, WorkoutSummaries.C_ID + "=?", new String[]{workoutId + ""}, null, null, null);
            if (!cursor.moveToFirst()) {
                break;
            }
            String name = cursor.getString(cursor.getColumnIndex(WorkoutSummaries.WORKOUT_NAME));
            String baseFileName = cursor.getString(cursor.getColumnIndex(WorkoutSummaries.FILE_BASE_NAME));
            cursor.close();

            new Handler(Looper.getMainLooper()).post(() ->
                    progressDialog.setMessage(String.format("deleting %s...\nplease wait", name))
            );

            // delete from WorkoutSummaries
            if (DEBUG) Log.d(TAG, "deleting from WorkoutSummaries");
            dbSummaries.delete(WorkoutSummaries.TABLE, WorkoutSummaries.C_ID + "=?", new String[]{workoutId + ""});
            dbSummaries.delete(WorkoutSummaries.TABLE_ACCUMULATED_SENSORS, WorkoutSummaries.WORKOUT_ID + "=?", new String[]{workoutId + ""});
            dbSummaries.delete(WorkoutSummaries.TABLE_EXTREMA_VALUES, WorkoutSummaries.WORKOUT_ID + "=?", new String[]{workoutId + ""});

            // delete from WorkoutSamples
            if (DEBUG) Log.d(TAG, "deleting from WorkoutSamples");
            WorkoutSamplesDatabaseManager.getInstance(context);
            WorkoutSamplesDatabaseManager.deleteWorkout(context, baseFileName);

            // delete from ExportManager
            if (DEBUG) Log.d(TAG, "deleting from ExportStatusRepository");
            ExportStatusRepository.getInstance(context).deleteWorkout(baseFileName);

            // delete from Laps
            if (DEBUG) Log.d(TAG, "deleting from Laps");
            lapsDBManager.deleteWorkout(workoutId);
        }

        new Handler(Looper.getMainLooper()).post(() -> {
            if (DEBUG) Log.d(TAG, "onPostExecute");
            if (progressDialog.isShowing()) {
                if (DEBUG) Log.d(TAG, "dialog still showing => dismiss");
                try {
                    progressDialog.dismiss();
                    // sometimes this gives the following exception:
                    // java.lang.IllegalArgumentException: View not attached to window manager
                    // so we catch this exception
                } catch (IllegalArgumentException e) {
                    // and nothing
                    // http://stackoverflow.com/questions/2745061/java-lang-illegalargumentexception-view-not-attached-to-window-manager
                }
            } else {
                if (DEBUG) Log.d(TAG, "dialog no longer showing, so do nothing?");
            }
            context.sendBroadcast(new Intent(FINISHED_DELETING)
                    .setPackage(context.getPackageName()));
        });
    }
}
