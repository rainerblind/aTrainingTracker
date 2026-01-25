package com.atrainingtracker.trainingtracker.fragments.aftermath;

import android.app.Dialog;
import android.content.ContentValues;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;

public class EditDescriptionDialogFragment extends DialogFragment {

    private static final String ARG_WORKOUT_ID = "workoutId";
    private static final String ARG_DESCRIPTION = "description";
    private static final String ARG_GOAL = "goal";
    private static final String ARG_METHOD = "method";

    private long workoutId;

    public interface OnDescriptionChangedListener {
        void onDescriptionChanged(long workoutId, String description, String goal, String method);
    }

    private OnDescriptionChangedListener mListener;

    public void setOnDescriptionChangedListener(OnDescriptionChangedListener listener) {
        mListener = listener;
    }

    public static EditDescriptionDialogFragment newInstance(long workoutId, String description, String goal, String method) {
        EditDescriptionDialogFragment fragment = new EditDescriptionDialogFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_WORKOUT_ID, workoutId);
        args.putString(ARG_DESCRIPTION, description);
        args.putString(ARG_GOAL, goal);
        args.putString(ARG_METHOD, method);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (getArguments() != null) {
            workoutId = getArguments().getLong(ARG_WORKOUT_ID);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_edit_description, null);

        final EditText etDescription = view.findViewById(R.id.edit_text_workout_description);
        final EditText etGoal = view.findViewById(R.id.edit_text_workout_goal);
        final EditText etMethod = view.findViewById(R.id.edit_text_workout_method);

        // Pre-fill the fields with existing data
        if (getArguments() != null) {
            etDescription.setText(getArguments().getString(ARG_DESCRIPTION));
            etGoal.setText(getArguments().getString(ARG_GOAL));
            etMethod.setText(getArguments().getString(ARG_METHOD));
        }

        builder.setView(view)
                .setTitle(R.string.title_edit_description) // Add this title string
                .setPositiveButton(R.string.save, (dialog, id) -> {
                    String newDescription = etDescription.getText().toString().trim();
                    String newGoal = etGoal.getText().toString().trim();
                    String newMethod = etMethod.getText().toString().trim();

                    if (mListener != null) {
                        mListener.onDescriptionChanged(workoutId, newDescription, newGoal, newMethod);
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, id) -> EditDescriptionDialogFragment.this.getDialog().cancel());

        return builder.create();
    }
}
