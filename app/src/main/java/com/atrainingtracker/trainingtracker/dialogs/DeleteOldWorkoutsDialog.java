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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.InputType;
import android.widget.EditText;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.helpers.DeleteWorkoutTask;

import java.util.List;

/**
 * Created by rainer on 05.01.17.
 */

public class DeleteOldWorkoutsDialog extends DialogFragment {
    public static final String TAG = DeleteOldWorkoutsDialog.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder alertDialogBuilder = new android.app.AlertDialog.Builder(getContext());
        alertDialogBuilder.setTitle(R.string.deleteOldWorkouts);
        alertDialogBuilder.setMessage(R.string.deleteWorkoutsThatAreOlderThanDays);
        final EditText input = new EditText(getContext());
        input.setText(R.string.defaultDaysToKeep);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);  // TODO: recheck!
        alertDialogBuilder.setView(input);
        alertDialogBuilder.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                try {
                    int daysToKeep = Integer.parseInt(input.getText().toString());
                    List<Long> oldWorkoutIds = WorkoutSummariesDatabaseManager.getOldWorkouts(daysToKeep);

                    (new DeleteWorkoutTask(getContext())).execute(oldWorkoutIds.toArray(new Long[oldWorkoutIds.size()]));
                } catch (Exception e) {

                }
            }
        });
        alertDialogBuilder.setNegativeButton(R.string.Cancel, null);
        return alertDialogBuilder.create();
    }
}
