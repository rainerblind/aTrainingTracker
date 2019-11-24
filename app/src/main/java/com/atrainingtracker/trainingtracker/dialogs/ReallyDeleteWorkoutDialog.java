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

package com.atrainingtracker.trainingtracker.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.interfaces.ReallyDeleteDialogInterface;

public class ReallyDeleteWorkoutDialog extends DialogFragment {
    public static final String TAG = "ReallyDeleteWorkoutDialog";
    private static final boolean DEBUG = false;
    ReallyDeleteDialogInterface mReallyDeleteDialogListener;
    private long mWorkoutId;

    public static ReallyDeleteWorkoutDialog newInstance(long workoutId) {
        if (DEBUG) Log.i(TAG, "newInstance");

        ReallyDeleteWorkoutDialog fragment = new ReallyDeleteWorkoutDialog();

        Bundle args = new Bundle();
        args.putLong(WorkoutSummaries.WORKOUT_ID, workoutId);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            mReallyDeleteDialogListener = (ReallyDeleteDialogInterface) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString() + " must implement ReallyDeleteDialogListener");
        }
    }


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate");

        mWorkoutId = getArguments().getLong(WorkoutSummaries.WORKOUT_ID);
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.delete_workout)
                .setMessage(R.string.really_delete_workout)
                .setIcon(android.R.drawable.ic_menu_delete)
                .setPositiveButton(R.string.delete_workout, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        mReallyDeleteDialogListener.reallyDeleteWorkout(mWorkoutId);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        // Create the AlertDialog object and return it
        return builder.create();
    }

}
