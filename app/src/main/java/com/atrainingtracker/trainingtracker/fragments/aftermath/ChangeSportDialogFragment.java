package com.atrainingtracker.trainingtracker.fragments.aftermath;

import android.app.Dialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;

import java.util.List;

public class ChangeSportDialogFragment extends DialogFragment {

    private static final String TAG = ChangeSportDialogFragment.class.getName();
    private static final boolean DEBUG = true;

    private static final String ARG_WORKOUT_ID = "workoutId";
    private static final String ARG_CURRENT_SPORT_ID = "currentSportId";

    private Spinner spinnerSport;
    private long mWorkoutId;
    private long mCurrentSportId;

    // We can show all sport types in this dialog
    private List<String> mSportTypeUiNameList;
    private List<Long> mSportTypeIdList;

    public interface OnSportChangedListener {
        void onSportChanged(long workoutId);
    }
    public void setOnSportChangedListener(OnSportChangedListener listener) {
        mListener = listener;
    }

    private OnSportChangedListener mListener;

    public static ChangeSportDialogFragment newInstance(long workoutId, long currentSportId) {
        ChangeSportDialogFragment fragment = new ChangeSportDialogFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_WORKOUT_ID, workoutId);
        args.putLong(ARG_CURRENT_SPORT_ID, currentSportId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mWorkoutId = getArguments().getLong(ARG_WORKOUT_ID);
            mCurrentSportId = getArguments().getLong(ARG_CURRENT_SPORT_ID);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_change_sport, null);

        spinnerSport = view.findViewById(R.id.spinner_change_sport);
        setSpinnerSport(); // Reuse the logic here

        builder.setView(view)
                .setTitle(R.string.change_sport)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        saveNewSport();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        ChangeSportDialogFragment.this.getDialog().cancel();
                    }
                });
        return builder.create();
    }

    /**
     * This is adapted directly from EditWorkoutFragment.
     * We simplify it to always show all sport types.
     */
    private void setSpinnerSport() {
        if (DEBUG) Log.i(TAG, "setSpinnerSport");

        mSportTypeUiNameList = SportTypeDatabaseManager.getSportTypesUiNameList();
        mSportTypeIdList = SportTypeDatabaseManager.getSportTypesIdList();

        spinnerSport.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, mSportTypeUiNameList.toArray(new String[0])));

        int selectionIndex = mSportTypeIdList.indexOf(mCurrentSportId);
        if (selectionIndex != -1) {
            spinnerSport.setSelection(selectionIndex);
        }
    }

    /**
     * Saves the newly selected sport ID to the database for the given workout.
     */
    private void saveNewSport() {
        int selectedPosition = spinnerSport.getSelectedItemPosition();
        if (selectedPosition < 0 || selectedPosition >= mSportTypeIdList.size()) {
            Log.e(TAG, "Invalid sport selection");
            return;
        }

        long newSportTypeId = mSportTypeIdList.get(selectedPosition);
        if (DEBUG) Log.d(TAG, "Saving new sportId: " + newSportTypeId + " for workout: " + mWorkoutId);

        ContentValues values = new ContentValues();
        values.put(WorkoutSummaries.SPORT_ID, newSportTypeId);
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

        // After saving, notify the listener that a change was made.
        if (mListener != null) {
            mListener.onSportChanged(mWorkoutId);
        }
    }
}
