package com.atrainingtracker.trainingtracker.fragments;

import android.app.Dialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.google.android.material.textfield.TextInputEditText;

public class EditWorkoutNameDialogFragment extends DialogFragment {

    private static final String TAG = "EditWorkoutNameDialogFragment";
    private static final String ARG_WORKOUT_ID = "workoutId";
    private static final String ARG_CURRENT_NAME = "currentName";

    private long mWorkoutId;


    // Listener to notify the list to refresh
    public interface OnWorkoutNameChangedListener {
        void onWorkoutNameChanged();
    }
    private OnWorkoutNameChangedListener mListener;

    public static EditWorkoutNameDialogFragment newInstance(long workoutId, String currentName) {
        EditWorkoutNameDialogFragment fragment = new EditWorkoutNameDialogFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_WORKOUT_ID, workoutId);
        args.putString(ARG_CURRENT_NAME, currentName);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnWorkoutNameChangedListener(OnWorkoutNameChangedListener listener) {
        mListener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mWorkoutId = getArguments().getLong(ARG_WORKOUT_ID);
        String currentName = getArguments().getString(ARG_CURRENT_NAME);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_edit_name, null);
        final TextInputEditText editTextName = view.findViewById(R.id.edit_text_workout_name);
        editTextName.setText(currentName);
        editTextName.requestFocus(); // Focus and show keyboard

        builder.setView(view)
                .setTitle(R.string.edit_workout_name)
                .setPositiveButton(R.string.save, (dialog, id) -> {
                    String newName = editTextName.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        saveNewWorkoutName(newName);
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.cancel());

        return builder.create();
    }

    private void saveNewWorkoutName(String newName) {
        ContentValues values = new ContentValues();
        values.put(WorkoutSummaries.WORKOUT_NAME, newName);
        SQLiteDatabase db = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();

        try {
            db.update(WorkoutSummaries.TABLE,
                    values,
                    WorkoutSummaries.C_ID + "=" + mWorkoutId,
                    null);
        } catch (SQLException e) {
            // TODO: use Toast?
            Log.e(TAG, "Error while writing" + e);
        }


        if (mListener != null) {
            mListener.onWorkoutNameChanged();
        }
    }
}
