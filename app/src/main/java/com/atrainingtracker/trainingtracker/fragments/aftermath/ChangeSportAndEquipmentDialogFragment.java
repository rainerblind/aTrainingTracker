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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;

import java.util.List;

public class ChangeSportAndEquipmentDialogFragment extends DialogFragment {

    private static final String TAG = "ChangeSportAndEquipmentDialogFragment";
    private static final boolean DEBUG = true;

    // ARGUMENTS
    private static final String ARG_WORKOUT_ID = "workoutId";
    private static final String ARG_CURRENT_SPORT_ID = "currentSportId";
    private static final String ARG_CURRENT_EQUIPMENT_NAME = "currentEquipmentName";

    // VIEWS
    private Spinner mSpinnerSport;
    private Spinner mSpinnerEquipment;
    private TextView mTvEquipmentLabel;


    // DATA
    private long mWorkoutId;
    private long mCurrentSportId;
    private List<Long> mSportTypeIdList;
    private String mCurrentEquipmentName;

    public interface OnSportChangedListener {
        void onSportChanged(long workoutId);
    }
    public void setOnSportChangedListener(OnSportChangedListener listener) {
        mListener = listener;
    }

    private OnSportChangedListener mListener;

    public static ChangeSportAndEquipmentDialogFragment newInstance(long workoutId, long currentSportId, String currentEquipmentName) {
        ChangeSportAndEquipmentDialogFragment fragment = new ChangeSportAndEquipmentDialogFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_WORKOUT_ID, workoutId);
        args.putLong(ARG_CURRENT_SPORT_ID, currentSportId);
        args.putString(ARG_CURRENT_EQUIPMENT_NAME, currentEquipmentName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mWorkoutId = getArguments().getLong(ARG_WORKOUT_ID);
            mCurrentSportId = getArguments().getLong(ARG_CURRENT_SPORT_ID);
            mCurrentEquipmentName = getArguments().getString(ARG_CURRENT_EQUIPMENT_NAME);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_change_sport, null);

        mSpinnerSport = view.findViewById(R.id.spinner_change_sport);
        mSpinnerEquipment = view.findViewById(R.id.spinner_change_equipment);
        mTvEquipmentLabel = view.findViewById(R.id.tv_equipment_label);

        setupSportSpinnerAdapter();
        setInitialSportSelection();
        setupEquipmentSpinner();

        builder.setView(view)
                .setTitle(R.string.change_sport)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        saveNewSportAndEquipment();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        ChangeSportAndEquipmentDialogFragment.this.getDialog().cancel();
                    }
                });
        return builder.create();
    }

    /**
     * This is adapted directly from EditWorkoutFragment.
     * We simplify it to always show all sport types.
     */
    private void setupSportSpinnerAdapter() {
        if (DEBUG) Log.i(TAG, "setSpinnerSport");

        List<String> sportTypeUiNameList = SportTypeDatabaseManager.getSportTypesUiNameList();
        mSportTypeIdList = SportTypeDatabaseManager.getSportTypesIdList();

        mSpinnerSport.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, sportTypeUiNameList.toArray(new String[0])));
    }

    private void setInitialSportSelection() {
        int selectionIndex = mSportTypeIdList.indexOf(mCurrentSportId);
        if (selectionIndex != -1) {
            // Set selection WITHOUT triggering the onItemSelected listener.
            mSpinnerSport.setSelection(selectionIndex, false);
        }
    }

    private void setupEquipmentSpinner() {
        if (DEBUG) Log.i(TAG, "setSpinnerEquipment");

        updateEquipmentSpinner(mCurrentSportId, mCurrentEquipmentName);

        mSpinnerSport.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                long selectedSportId = mSportTypeIdList.get(position);
                // Update equipment list for the newly selected sport, with no pre-selected equipment
                updateEquipmentSpinner(selectedSportId, null);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    /**
     * Updates the equipment spinner's content based on the selected sport.
     * @param currentSportId The ID of the currently selected sport.
     * @param equipmentToSelect The name of the equipment to pre-select, or null.
     */
    private void updateEquipmentSpinner(long currentSportId, @Nullable String equipmentToSelect) {

        // first, get the BSportType
        BSportType bSportType = SportTypeDatabaseManager.getInstance().getBSportType(currentSportId);

        int labelResId = switch (bSportType) {
            case BIKE -> R.string.equipment_type_bike;
            case RUN -> R.string.equipment_type_shoe;
            default -> R.string.Equipment;
        };
        mTvEquipmentLabel.setText(labelResId);

        EquipmentDbHelper equipmentDbHelper = new EquipmentDbHelper(getActivity());
        List<String> equipmentNameList = equipmentDbHelper.getEquipment(bSportType);

        if (equipmentNameList.isEmpty()) {
            mTvEquipmentLabel.setVisibility(View.GONE);
            mSpinnerEquipment.setVisibility(View.GONE);
        } else {
            mTvEquipmentLabel.setVisibility(View.VISIBLE);
            mSpinnerEquipment.setVisibility(View.VISIBLE);

            mSpinnerEquipment.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, equipmentNameList.toArray(new String[0])));

            // when we reselect the original sport, the original equipment should be selected
            if (currentSportId == mCurrentSportId) {
                equipmentToSelect = mCurrentEquipmentName;
            }

            if (equipmentToSelect != null) {
                int equipmentSelectionIndex = equipmentNameList.indexOf(equipmentToSelect);
                if (equipmentSelectionIndex != -1) {
                    mSpinnerEquipment.setSelection(equipmentSelectionIndex);
                }
            }
        }
    }

    /**
     * Saves the newly selected sport ID to the database for the given workout.
     */
    private void saveNewSportAndEquipment() {
        // get selected sport
        int selectedPosition = mSpinnerSport.getSelectedItemPosition();
        if (selectedPosition < 0 || selectedPosition >= mSportTypeIdList.size()) {
            Log.e(TAG, "Invalid sport selection");
            return;
        }

        long newSportTypeId = mSportTypeIdList.get(selectedPosition);
        if (DEBUG) Log.d(TAG, "Saving new sportId: " + newSportTypeId + " for workout: " + mWorkoutId);

        // get selected equipment
        String selectedEquipmentName = mSpinnerEquipment.getSelectedItem().toString();
        EquipmentDbHelper equipmentDbHelper = new EquipmentDbHelper(getActivity());
        long newEquipmentId = equipmentDbHelper.getEquipmentId(selectedEquipmentName);
        if (DEBUG) Log.d(TAG, "Saving new equipment: " + selectedEquipmentName + " (id: " + newEquipmentId + ")");

        // save
        ContentValues values = new ContentValues();
        values.put(WorkoutSummaries.SPORT_ID, newSportTypeId);
        values.put(WorkoutSummaries.EQUIPMENT_ID, newEquipmentId);

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
