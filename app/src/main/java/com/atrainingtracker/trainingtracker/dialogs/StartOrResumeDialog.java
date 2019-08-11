package com.atrainingtracker.trainingtracker.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

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
