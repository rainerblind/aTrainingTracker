package com.atrainingtracker.trainingtracker.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
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
