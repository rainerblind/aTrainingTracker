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
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.interfaces.StartOrResumeInterface;

/**
 * Created by rainer on 05.01.17.
 */

public class StartOrResumeDialog extends DialogFragment {
    public static final String TAG = StartOrResumeDialog.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;

    private StartOrResumeInterface mStartOrResumeInterface;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            mStartOrResumeInterface = (StartOrResumeInterface) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString() + " must implement ChooseStartOrResumeInterface");
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getContext());
        alertDialogBuilder.setMessage(R.string.start_or_resume_dialog_message);
        // alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setPositiveButton(R.string.start_new_workout, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                mStartOrResumeInterface.chooseStart();
                dialog.cancel();
            }
        });

        alertDialogBuilder.setNegativeButton(R.string.resume_workout, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                mStartOrResumeInterface.chooseResume();
                dialog.cancel();

            }
        });

        return alertDialogBuilder.create();
    }
}
