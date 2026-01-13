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

package com.atrainingtracker.trainingtracker.fragments;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter;
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.database.ExtremaType;
import com.atrainingtracker.trainingtracker.exporter.ExportManager;
import com.atrainingtracker.trainingtracker.exporter.ExportWorkoutWorker;
import com.atrainingtracker.trainingtracker.MyHelper;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.dialogs.EditFancyWorkoutNameDialog;
import com.atrainingtracker.trainingtracker.helpers.CalcExtremaValuesThread;
import com.atrainingtracker.trainingtracker.interfaces.ReallyDeleteDialogInterface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EditWorkoutFragment extends Fragment {
    public static final String TAG = "EditWorkoutFragment";
    private static final boolean DEBUG = TrainingApplication.getDebug(false);

    // public static final String WORKOUT_ID = "de.rainerblind.trainingtracker.Activities.UpdateWorkoutActivity.WORKOUT_ID";
    // some definitions to store the values of the views
    private static final String BASE_FILE_NAME = "BASE_FILE_NAME";
    private static final String EXPORT_NAME = "EXPORT_NAME";
    private static final String SPORT_ID = "SPORT_ID";
    private static final String EQUIPMENT = "EQUIPMENT";
    private static final String COMMUTE = "COMMUTE";
    private static final String TRAINER = "TRAINER";
    private static final String DESCRIPTION = "DESCRIPTION";
    private static final String GOAL = "GOAL";
    private static final String METHOD = "METHOD";
    private static final String AVERAGE_SPEED = "AVERAGE_SPEED";
    private static final String START_TIME = "START_TIME";
    private static final String TOTAL_TIME = "TOTAL_TIME";
    private static final String ACTIVE_TIME = "ACTIVE_TIME";
    private static final String SENSOR_TYPES = "SENSOR_TYPES";
    private static final String DISTANCE = "DISTANCE";
    private static final String MAX_LINE_DISTANCE = "MAX_LINE_DISTANCE";
    private static final String CALORIES = "CALORIES";
    private static final String HR_MEAN = "HR_MEAN";
    private static final String HR_MAX = "HR_MAX";
    private static final String HR_MIN = "HR_MIN";
    private static final String SPEED_MEAN = "SPEED_MEAN";
    private static final String SPEED_MAX = "SPEED_MAX";
    private static final String SPEED_MIN = "SPEED_MIN";
    private static final String PACE_MEAN = "PACE_MEAN";
    private static final String PACE_MAX = "PACE_MAX";
    private static final String PACE_MIN = "PACE_MIN";
    private static final String CADENCE_MEAN = "CADENCE_MEAN";
    private static final String CADENCE_MAX = "CADENCE_MAX";
    private static final String CADENCE_MIN = "CADENCE_MIN";
    private static final String POWER_MEAN = "POWER_MEAN";
    private static final String POWER_MAX = "POWER_MAX";
    private static final String POWER_MIN = "POWER_MIN";
    private static final String TORQUE_MEAN = "TORQUE_MEAN";
    private static final String TORQUE_MAX = "TORQUE_MAX";
    private static final String TORQUE_MIN = "TORQUE_MIN";
    private static final String PEDAL_POWER_BALANCE_MEAN = "PEDAL_POWER_BALANCE_MEAN";
    private static final String PEDAL_POWER_BALANCE_MAX = "PEDAL_POWER_BALANCE_MAX";
    private static final String PEDAL_POWER_BALANCE_MIN = "PEDAL_POWER_BALANCE_MIN";
    private static final String PEDAL_SMOOTHNESS_LEFT_MEAN = "PEDAL_SMOOTHNESS_LEFT_MEAN";
    private static final String PEDAL_SMOOTHNESS_LEFT_MAX = "PEDAL_SMOOTHNESS_LEFT_MAX";
    private static final String PEDAL_SMOOTHNESS_LEFT_MIN = "PEDAL_SMOOTHNESS_LEFT_MIN";
    private static final String PEDAL_SMOOTHNESS_MEAN = "PEDAL_SMOOTHNESS_MEAN";
    private static final String PEDAL_SMOOTHNESS_MAX = "PEDAL_SMOOTHNESS_MAX";
    private static final String PEDAL_SMOOTHNESS_MIN = "PEDAL_SMOOTHNESS_MIN";
    private static final String PEDAL_SMOOTHNESS_RIGHT_MEAN = "PEDAL_SMOOTHNESS_RIGHT_MEAN";
    private static final String PEDAL_SMOOTHNESS_RIGHT_MAX = "PEDAL_SMOOTHNESS_RIGHT_MAX";
    private static final String PEDAL_SMOOTHNESS_RIGHT_MIN = "PEDAL_SMOOTHNESS_RIGHT_MIN";
    private static final String ALTITUDE_MEAN = "ALTITUDE_MEAN";
    private static final String ALTITUDE_MAX = "ALTITUDE_MAX";
    private static final String ALTITUDE_MIN = "ALTITUDE_MIN";
    private static final String TEMPERATURE_MEAN = "TEMPERATURE_MEAN";
    private static final String TEMPERATURE_MAX = "TEMPERATURE_MAX";
    private static final String TEMPERATURE_MIN = "TEMPERATURE_MIN";
    private static final String SHOW_DELETE_BUTTON = "SHOW_DELETE_BUTTON";
    private static final List<Integer> TR_IDS_EXTREMA_VALUES = Arrays.asList(R.id.trAltitude, R.id.trCadence, R.id.trHR, R.id.trPace, R.id.trPedalPowerBalance,
            R.id.trPedalSmoothnessLeft, R.id.trPedalSmoothnessRight, R.id.trPower, R.id.trSpeed, R.id.trTemperature, R.id.trTorque);
    private final IntentFilter mFinishedCalculatingExtremaValueFilter = new IntentFilter(CalcExtremaValuesThread.FINISHED_CALCULATING_EXTREMA_VALUE);
    private final IntentFilter mFinishedGuessingCommuteAndTrainerFilter = new IntentFilter(CalcExtremaValuesThread.FINISHED_GUESSING_COMMUTE_AND_TRAINER);
    private final IntentFilter mFinishedCalculatingFancyNameFilter = new IntentFilter(CalcExtremaValuesThread.FINISHED_CALCULATING_FANCY_NAME);
    @Nullable
    protected String mBaseFileName;
    // protected TTSportType mTTSportType;  // we want the sport type easily available since the equipment also depends on the sport type
    protected long mSportTypeId = SportTypeDatabaseManager.getDefaultSportTypeId();
    @Nullable
    protected String mEquipmentName;     // we want to save the equipment name since we get it from some DB and have to find it in the equipment spinner

    protected List<String> mSportTypeUiNameList;
    protected List<Long> mSportTypeIdList;
    protected double mAverageSpeed;
    protected boolean mShowAllSportTypes;
    protected BSportType mBSportType;
    protected ReallyDeleteDialogInterface mReallyDeleteDialogInterface;
    private long mWorkoutID;

    private Button buttonDeleteWorkout;
    private Spinner spinnerSport, spinnerEquipment;
    private EditText editExportName, editGoal, editMethod, editDescription;
    private TextView tvEquipment;
    private RadioGroup rgCommuteTrainer;
    private RadioButton rbCommute, rbTrainer;
    private boolean radioButtonAlreadyChecked = false;  // necessary to allow deselect of the radio buttons within the group for Commute and Trainer
    private static final double MAX_WORKOUT_TIME_TO_SHOW_DELETE_BUTTON = 10 * 60;  // 10 min
    @NonNull
    private String ALL = "all";
    private boolean mPaceExtremaValuesAvailable = false;

    // BroadcastReceivers to update the name, commute, and extrema values
    private final BroadcastReceiver mFinishedCalculatingFancyNameReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            String workoutName = intent.getExtras().getString(CalcExtremaValuesThread.FANCY_NAME);
            editExportName.setText(workoutName);
        }
    };
    private final BroadcastReceiver mFinishedGuessingCommuteAndTrainerReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            WorkoutSummariesDatabaseManager databaseManager = WorkoutSummariesDatabaseManager.getInstance();
            SQLiteDatabase db = databaseManager.getOpenDatabase();
            Cursor cursor = db.query(WorkoutSummaries.TABLE,
                    null,
                    WorkoutSummaries.C_ID + "=?",
                    new String[]{Long.toString(mWorkoutID)},
                    null,
                    null,
                    null);
            cursor.moveToFirst();
            rbCommute.setChecked(cursor.getInt(cursor.getColumnIndex(WorkoutSummaries.COMMUTE)) > 0);
            rbTrainer.setChecked(cursor.getInt(cursor.getColumnIndex(WorkoutSummaries.TRAINER)) > 0);
            cursor.close();
            databaseManager.closeDatabase(); // db.close();
        }
    };
    private final BroadcastReceiver mFinishedCalculatingExtremaValueReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, @NonNull Intent intent) {
            SensorType sensorType = SensorType.valueOf(intent.getExtras().getString(CalcExtremaValuesThread.SENSOR_TYPE));

            fillTrExtrema(sensorType);
        }
    };

    @NonNull
    public static EditWorkoutFragment newInstance(long workoutId) {
        EditWorkoutFragment editWorkoutFragment = new EditWorkoutFragment();

        Bundle args = new Bundle();
        args.putLong(WorkoutSummaries.WORKOUT_ID, workoutId);
        editWorkoutFragment.setArguments(args);

        return editWorkoutFragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (DEBUG) Log.d(TAG, "onAttach");

        try {
            mReallyDeleteDialogInterface = (ReallyDeleteDialogInterface) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement ReallyDeleteDialogInterface");
        }
    }

    /**
     * Called when the fragment is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreate");

        mWorkoutID = getArguments().getLong(WorkoutSummaries.WORKOUT_ID);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreateView");

        setHasOptionsMenu(true);

        View view = inflater.inflate(R.layout.edit_workout, container, false);

        // find interactive views
        // all the interactive views
        Button buttonSaveWorkout = view.findViewById(R.id.buttonSaveWorkout);
        buttonDeleteWorkout = view.findViewById(R.id.buttonDeleteWorkout);
        Button buttonFancyName = view.findViewById(R.id.buttonFancyName);

        spinnerSport = view.findViewById(R.id.spinnerSport);
        spinnerEquipment = view.findViewById(R.id.spinnerEquipment);

        editExportName = view.findViewById(R.id.editExportName);
        editGoal = view.findViewById(R.id.editGoal);
        editMethod = view.findViewById(R.id.editMethod);
        editDescription = view.findViewById(R.id.editDescription);

        tvEquipment = view.findViewById(R.id.tvEquipment);

        rgCommuteTrainer = view.findViewById(R.id.rgCommuteTrainer);
        rbCommute = view.findViewById(R.id.rbCommute);
        rbTrainer = view.findViewById(R.id.rbTrainer);


        // configure the views
        ALL = getString(R.string.equipment_all);

        // first, set the units of speed and pace to the user choice
        ((TextView) view.findViewById(R.id.tvSpeedUnit)).setText(getString(MyHelper.getSpeedUnitNameId()));
        ((TextView) view.findViewById(R.id.tvPaceUnit)).setText(getString(MyHelper.getPaceUnitNameId()));

        spinnerSport.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id) {
                if (DEBUG) Log.i(TAG, "spinnerSport.onItemSelected");
                if (!mShowAllSportTypes
                        && spinnerSport.getSelectedItemPosition() == mSportTypeIdList.size()) {
                    if (DEBUG) Log.i(TAG, "all sport types selected");
                    mShowAllSportTypes = true;
                    setSpinnerSport();
                    spinnerSport.performClick();
                } else {
                    if (DEBUG) Log.i(TAG, "id=" + id + " selected");
                    long sportTypeId = mSportTypeIdList.get(spinnerSport.getSelectedItemPosition());
                    if (sportTypeId != mSportTypeId) {
                        mSportTypeId = sportTypeId;
                        onSportTypeChanged();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub
            }
        });

        spinnerEquipment.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id) {
                if (DEBUG)
                    Log.d(TAG, "spinnerEquipment selected: " + spinnerEquipment.getSelectedItem());

                if (ALL.equals(spinnerEquipment.getSelectedItem())) {
                    if (DEBUG) Log.d(TAG, "now, we show all equipment!");

                    setSpinnerEquipment(true);
                }
                // TODO
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub
            }
        });

        // allow to uncheck the RadioButtons Commute and Trainer
        OnCheckedChangeListener radioCheckChangeListener = new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                radioButtonAlreadyChecked = false;
            }
        };

        OnClickListener radioClickListener = new OnClickListener() {
            public void onClick(@NonNull View v) {
                if (v.getId() == rgCommuteTrainer.getCheckedRadioButtonId() && radioButtonAlreadyChecked) {
                    rgCommuteTrainer.clearCheck();
                } else {
                    radioButtonAlreadyChecked = true;
                }
            }
        };

        rbCommute.setOnCheckedChangeListener(radioCheckChangeListener);
        rbTrainer.setOnCheckedChangeListener(radioCheckChangeListener);

        rbCommute.setOnClickListener(radioClickListener);
        rbTrainer.setOnClickListener(radioClickListener);


        final Context context = getActivity();
        buttonSaveWorkout.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (DEBUG) Log.i(TAG, "buttonSaveWorkout pressed");

                // TODO: do in new thread?
                saveWorkout();

                Intent resultIntent = new Intent();
                resultIntent.putExtra(WorkoutSummaries.WORKOUT_ID, mWorkoutID);

                if (mBaseFileName != null) {
                    ExportManager exportManager = new ExportManager(getActivity().getApplicationContext(), TAG);
                    exportManager.exportWorkout(mBaseFileName);
                    exportManager.onFinished(TAG);

                    OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ExportWorkoutWorker.class)
                            .build();
                    assert context != null;
                    WorkManager.getInstance(context).enqueue(workRequest);

                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                } else {
                    if (DEBUG) Log.d(TAG, "mBaseFileName is null!");
                    getActivity().setResult(Activity.RESULT_CANCELED, resultIntent);
                }

                getActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        });

        buttonDeleteWorkout.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                mReallyDeleteDialogInterface.confirmDeleteWorkout(mWorkoutID);
            }
        });

        buttonFancyName.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showFancyWorkoutNameDialog();
            }
        });

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (DEBUG) Log.d(TAG, "onViewCreated");

        // register receivers
        ContextCompat.registerReceiver(getActivity(), mFinishedCalculatingExtremaValueReceiver, mFinishedCalculatingExtremaValueFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(getActivity(), mFinishedGuessingCommuteAndTrainerReceiver, mFinishedGuessingCommuteAndTrainerFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(getActivity(), mFinishedCalculatingFancyNameReceiver, mFinishedCalculatingFancyNameFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ViewCompat.setOnApplyWindowInsetsListener(view, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets navBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
                Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
                int bottomPadding = navBarInsets.bottom + imeInsets.bottom;
                v.setPadding(
                        v.getPaddingLeft(),
                        v.getPaddingTop(),
                        v.getPaddingRight(),
                        bottomPadding
                );
                Insets systemGestureInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemGestures());
                return WindowInsetsCompat.CONSUMED;
            }
        });

        // Request initial insets apply in case the listener was set up after the first dispatch
        // (though with setOnApplyWindowInsetsListener, it usually gets called immediately if insets are available)
        ViewCompat.requestApplyInsets(view);

        // register receivers (moved from onActivityCreated for modern Fragment lifecycle)
        if (getActivity() != null) {
            ContextCompat.registerReceiver(getActivity(), mFinishedCalculatingExtremaValueReceiver, mFinishedCalculatingExtremaValueFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
            ContextCompat.registerReceiver(getActivity(), mFinishedGuessingCommuteAndTrainerReceiver, mFinishedGuessingCommuteAndTrainerFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
            ContextCompat.registerReceiver(getActivity(), mFinishedCalculatingFancyNameReceiver, mFinishedCalculatingFancyNameFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        }

        // fill the views
        // first, remove all  TODO: still necessary?
        removeExtremaValuesSeparator();
        removeAllExtremaValuesViews();

        // then fill them
        if (savedInstanceState == null) {
            fillViewsFromDb();
        } else {
            fillViewsFromSavedInstanceState(savedInstanceState);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.d(TAG, "onResume");
    }

    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.d(TAG, "onPause");
    }

    @Override
    public void onDestroyView () {
        super.onDestroyView();
        if (DEBUG) Log.i(TAG, "onDestroyView");

        getActivity().unregisterReceiver(mFinishedCalculatingExtremaValueReceiver);
        getActivity().unregisterReceiver(mFinishedGuessingCommuteAndTrainerReceiver);
        getActivity().unregisterReceiver(mFinishedCalculatingFancyNameReceiver);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState)//savedInstanceState)
    {
        if (DEBUG) Log.i(TAG, "onSaveInstanceState");

        savedInstanceState.putLong(WorkoutSummaries.WORKOUT_ID, mWorkoutID);
        savedInstanceState.putString(BASE_FILE_NAME, mBaseFileName);
        savedInstanceState.putString(EXPORT_NAME, editExportName.getText().toString());
        savedInstanceState.putLong(SPORT_ID, mSportTypeId);
        savedInstanceState.putString(EQUIPMENT, mEquipmentName);
        savedInstanceState.putBoolean(COMMUTE, rbCommute.isChecked());
        savedInstanceState.putBoolean(TRAINER, rbTrainer.isChecked());
        savedInstanceState.putString(DESCRIPTION, editDescription.getText().toString());
        savedInstanceState.putString(GOAL, editGoal.getText().toString());
        savedInstanceState.putString(METHOD, editMethod.getText().toString());
        savedInstanceState.putDouble(AVERAGE_SPEED, mAverageSpeed);

        addString(savedInstanceState, START_TIME, R.id.tvStartTime);
        addString(savedInstanceState, TOTAL_TIME, R.id.tvTotalTime);
        addString(savedInstanceState, ACTIVE_TIME, R.id.tvActiveTime);
        addString(savedInstanceState, SENSOR_TYPES, R.id.tvSensorTypes);
        addString(savedInstanceState, DISTANCE, R.id.tvDistance);
        addString(savedInstanceState, MAX_LINE_DISTANCE, R.id.tvMaxLineDistance);
        addString(savedInstanceState, CALORIES, R.id.tvCalories);
        addString(savedInstanceState, HR_MEAN, R.id.tvHRMean);
        addString(savedInstanceState, HR_MAX, R.id.tvHRMax);
        addString(savedInstanceState, HR_MIN, R.id.tvHRMin);
        addString(savedInstanceState, SPEED_MEAN, R.id.tvSpeedMean);
        addString(savedInstanceState, SPEED_MAX, R.id.tvSpeedMax);
        addString(savedInstanceState, SPEED_MIN, R.id.tvSpeedMin);
        addString(savedInstanceState, PACE_MEAN, R.id.tvPaceMean);
        addString(savedInstanceState, PACE_MAX, R.id.tvPaceMax);
        addString(savedInstanceState, PACE_MIN, R.id.tvPaceMin);
        addString(savedInstanceState, CADENCE_MEAN, R.id.tvCadenceMean);
        addString(savedInstanceState, CADENCE_MAX, R.id.tvCadenceMax);
        addString(savedInstanceState, CADENCE_MIN, R.id.tvCadenceMin);
        addString(savedInstanceState, POWER_MEAN, R.id.tvPowerMax);
        addString(savedInstanceState, POWER_MAX, R.id.tvPowerMax);
        addString(savedInstanceState, POWER_MIN, R.id.tvPaceMin);
        addString(savedInstanceState, TORQUE_MEAN, R.id.tvTorqueMean);
        addString(savedInstanceState, TORQUE_MAX, R.id.tvTorqueMax);
        addString(savedInstanceState, TORQUE_MIN, R.id.tvTorqueMin);
        addString(savedInstanceState, PEDAL_POWER_BALANCE_MEAN, R.id.tvPedalPowerBalanceMean);
        addString(savedInstanceState, PEDAL_POWER_BALANCE_MAX, R.id.tvPedalPowerBalanceMax);
        addString(savedInstanceState, PEDAL_POWER_BALANCE_MIN, R.id.tvPedalPowerBalanceMin);
        addString(savedInstanceState, PEDAL_SMOOTHNESS_LEFT_MEAN, R.id.tvPedalSmoothnessLeftMean);
        addString(savedInstanceState, PEDAL_SMOOTHNESS_LEFT_MAX, R.id.tvPedalSmoothnessLeftMax);
        addString(savedInstanceState, PEDAL_SMOOTHNESS_LEFT_MIN, R.id.tvPedalSmoothnessLeftMin);
        addString(savedInstanceState, PEDAL_SMOOTHNESS_MEAN, R.id.tvPedalSmoothnessMean);
        addString(savedInstanceState, PEDAL_SMOOTHNESS_MAX, R.id.tvPedalSmoothnessMax);
        addString(savedInstanceState, PEDAL_SMOOTHNESS_MIN, R.id.tvPedalSmoothnessMin);
        addString(savedInstanceState, PEDAL_SMOOTHNESS_RIGHT_MEAN, R.id.tvPedalSmoothnessRightMean);
        addString(savedInstanceState, PEDAL_SMOOTHNESS_RIGHT_MAX, R.id.tvPedalSmoothnessRightMax);
        addString(savedInstanceState, PEDAL_SMOOTHNESS_RIGHT_MIN, R.id.tvPedalSmoothnessRightMin);
        addString(savedInstanceState, ALTITUDE_MEAN, R.id.tvAltitudeMean);
        addString(savedInstanceState, ALTITUDE_MAX, R.id.tvAltitudeMax);
        addString(savedInstanceState, ALTITUDE_MIN, R.id.tvAltitudeMin);
        addString(savedInstanceState, TEMPERATURE_MEAN, R.id.tvTemperatureMean);
        addString(savedInstanceState, TEMPERATURE_MAX, R.id.tvTemperatureMax);
        addString(savedInstanceState, TEMPERATURE_MIN, R.id.tvTemperatureMin);

        savedInstanceState.putBoolean(SHOW_DELETE_BUTTON, getActivity().findViewById(R.id.buttonDeleteWorkout).getVisibility() == View.VISIBLE);

        super.onSaveInstanceState(savedInstanceState);
    }

    protected void addString(@NonNull Bundle savedInstanceState, String key, int id) {
        if (getActivity().findViewById(id).isShown()) {
            savedInstanceState.putString(key, ((TextView) getActivity().findViewById(id)).getText().toString());
        }
    }

    private void fillViewsFromDb() {
        if (DEBUG) Log.d(TAG, "fillViewsFromDb()");
        if (DEBUG) Log.i(TAG, "mWorkoutID=" + mWorkoutID);

        // first, open the database and get a valid cursor
        WorkoutSummariesDatabaseManager databaseManager = WorkoutSummariesDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = db.query(WorkoutSummaries.TABLE,
                null,
                WorkoutSummaries.C_ID + "=?",
                new String[]{Long.toString(mWorkoutID)},
                null,
                null,
                null);
        cursor.moveToFirst();
        if (DEBUG) Log.d(TAG, "after cursor.moveToFirst()");

        // set the member variables
        // first, the file name
        mBaseFileName = cursor.getString(cursor.getColumnIndex(WorkoutSummaries.FILE_BASE_NAME));

        mAverageSpeed = cursor.getDouble(cursor.getColumnIndex(WorkoutSummaries.SPEED_AVERAGE_mps));

        // next, the equipment
        if (DEBUG) {
            Log.d(TAG, "equipmentId form db: " + cursor.getInt(cursor.getColumnIndex(WorkoutSummaries.EQUIPMENT_ID)));
            Log.d(TAG, "equipmentName: " + new EquipmentDbHelper(getActivity()).getEquipmentFromId(cursor.getInt(cursor.getColumnIndex(WorkoutSummaries.EQUIPMENT_ID))));
        }
        if (!cursor.isNull(cursor.getColumnIndex(WorkoutSummaries.EQUIPMENT_ID))) {
            mEquipmentName = new EquipmentDbHelper(getActivity()).getEquipmentFromId(cursor.getInt(cursor.getColumnIndex(WorkoutSummaries.EQUIPMENT_ID)));
        }

        // now, the remaining interactive views
        editExportName.setText(cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_NAME)));
        editGoal.setText(cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.GOAL)));
        editMethod.setText(cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.METHOD)));
        editDescription.setText(cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.DESCRIPTION)));

        rbCommute.setChecked(cursor.getInt(cursor.getColumnIndex(WorkoutSummaries.COMMUTE)) > 0);
        rbTrainer.setChecked(cursor.getInt(cursor.getColumnIndex(WorkoutSummaries.TRAINER)) > 0);

        // finally, the remaining views
        // ((TextView) getActivity().findViewById(R.id.tvStartTime)).setText(cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_START)));
        ((TextView) getActivity().findViewById(R.id.tvStartTime)).setText(WorkoutSummariesDatabaseManager.getStartTime(mWorkoutID, "localtime"));

        int totalTime = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_TOTAL_s));
        ((TextView) getActivity().findViewById(R.id.tvTotalTime)).setText((new TimeFormatter()).format(totalTime));
        // do not show the delete button on "long" workouts or when tracking
        if (totalTime > MAX_WORKOUT_TIME_TO_SHOW_DELETE_BUTTON
                || TrainingApplication.isTracking()) {
            buttonDeleteWorkout.setVisibility(View.GONE);
        }
        int activeTime = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_ACTIVE_s));
        ((TextView) getActivity().findViewById(R.id.tvActiveTime)).setText((new TimeFormatter()).format(activeTime));

        ((TextView) getActivity().findViewById(R.id.tvSensorTypes)).setText(cursor.getString(cursor.getColumnIndex(WorkoutSummaries.GC_DATA)));  // wtf, confusing wording?

        // distance
        String distance = (new DistanceFormatter()).format(cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_TOTAL_m)));
        String unit = getString(MyHelper.getDistanceUnitNameId());
        ((TextView) getActivity().findViewById(R.id.tvDistance)).setText(getString(R.string.value_unit_string_string, distance, unit));

        // max line distance
        fillTvExtrema(SensorType.LINE_DISTANCE_m, ExtremaType.MAX, R.id.tvMaxLineDistance);
        TextView tvMaxLineDistance = getActivity().findViewById(R.id.tvMaxLineDistance);
        CharSequence maxLineDistance = tvMaxLineDistance.getText();
        tvMaxLineDistance.setText(getString(R.string.value_unit_string_string, maxLineDistance, unit));

        // calories when available and not zero
        TextView tvCalories = getActivity().findViewById(R.id.tvCalories);
        Integer calories = cursor.getInt(cursor.getColumnIndex(WorkoutSummaries.CALORIES));
        if (calories == 0) {
            getActivity().findViewById(R.id.trCalories).setVisibility(View.GONE);
        } else {
            getActivity().findViewById(R.id.trCalories).setVisibility(View.VISIBLE);
            tvCalories.setText(getString(R.string.value_unit_int_string, calories, getString(R.string.units_calories)));
        }

        // mean, max, and min values
        fillExtremaValuesFromDb();

        // finally, the sport type.  This must be done last in order to set the pace field depending on the sport type
        // This also triggers to update the equipment and tvEquipment
        mSportTypeId = cursor.getLong(cursor.getColumnIndex(WorkoutSummaries.SPORT_ID));
        onSportTypeChanged();

        cursor.close();
        databaseManager.closeDatabase(); // db.close();
    }

    protected void fillExtremaValuesFromDb() {
        // simply fill with all the values
        fillTrExtrema(SensorType.HR);
        fillTrExtrema(SensorType.SPEED_mps);
        fillTrExtrema(SensorType.PACE_spm);
        fillTrExtrema(SensorType.CADENCE);
        fillTrExtrema(SensorType.POWER);
        fillTrExtrema(SensorType.TORQUE);
        fillTrExtrema(SensorType.PEDAL_POWER_BALANCE);
        fillTrExtrema(SensorType.PEDAL_SMOOTHNESS_L);
        fillTrExtrema(SensorType.PEDAL_SMOOTHNESS);
        fillTrExtrema(SensorType.PEDAL_SMOOTHNESS_R);
        fillTrExtrema(SensorType.ALTITUDE);
        fillTrExtrema(SensorType.TEMPERATURE);

    }

    protected void removeAllExtremaValuesViews() {
        for (Integer trId : TR_IDS_EXTREMA_VALUES) {
            getActivity().findViewById(trId).setVisibility(View.GONE);
        }
    }

    protected void removeExtremaValuesSeparator() {
        getActivity().findViewById(R.id.separatorViewMeanMaxValues).setVisibility(View.GONE);
        getActivity().findViewById(R.id.tlMeanMaxValues).setVisibility(View.GONE);
    }

    protected void showExtremaValuesSeparator() {
        getActivity().findViewById(R.id.separatorViewMeanMaxValues).setVisibility(View.VISIBLE);
        getActivity().findViewById(R.id.tlMeanMaxValues).setVisibility(View.VISIBLE);
    }

    protected void showOrHideTrPace() {
        if (SportTypeDatabaseManager.getBSportType(mSportTypeId) == BSportType.RUN
                && mPaceExtremaValuesAvailable) {
            getActivity().findViewById(R.id.trPace).setVisibility(View.VISIBLE);
        } else {
            getActivity().findViewById(R.id.trPace).setVisibility(View.GONE);
        }
    }

    protected void fillTrExtrema(@NonNull SensorType sensorType) {
        switch (sensorType) {
            case HR:
                fillTrExtrema(SensorType.HR, R.id.trHR, R.id.tvHRMean, R.id.tvHRMax, R.id.tvHRMin);
                break;
            case SPEED_mps:
                fillTrExtrema(SensorType.SPEED_mps, R.id.trSpeed, R.id.tvSpeedMean, R.id.tvSpeedMax, R.id.tvSpeedMin);
                break;
            case PACE_spm:
                fillTrExtrema(SensorType.PACE_spm, R.id.trPace, R.id.tvPaceMean, R.id.tvPaceMax, R.id.tvPaceMin);
                showOrHideTrPace();
                break;
            case CADENCE:
                fillTrExtrema(SensorType.CADENCE, R.id.trCadence, R.id.tvCadenceMean, R.id.tvCadenceMax, R.id.tvCadenceMin);
                break;
            case POWER:
                fillTrExtrema(SensorType.POWER, R.id.trPower, R.id.tvPowerMean, R.id.tvPowerMax, R.id.tvPowerMin);
                break;
            case TORQUE:
                fillTrExtrema(SensorType.TORQUE, R.id.trTorque, R.id.tvTorqueMean, R.id.tvTorqueMax, R.id.tvTorqueMin);
                break;
            case PEDAL_POWER_BALANCE:
                fillTrExtrema(SensorType.PEDAL_POWER_BALANCE, R.id.trPedalPowerBalance, R.id.tvPedalPowerBalanceMean, R.id.tvPedalPowerBalanceMax, R.id.tvPedalPowerBalanceMin);
                break;
            case PEDAL_SMOOTHNESS_L:
                fillTrExtrema(SensorType.PEDAL_SMOOTHNESS_L, R.id.trPedalSmoothnessLeft, R.id.tvPedalSmoothnessLeftMean, R.id.tvPedalSmoothnessLeftMax, R.id.tvPedalSmoothnessLeftMin);
                break;
            case PEDAL_SMOOTHNESS:
                fillTrExtrema(SensorType.PEDAL_SMOOTHNESS, R.id.trPedalSmoothness, R.id.tvPedalSmoothnessMean, R.id.tvPedalSmoothnessMax, R.id.tvPedalSmoothnessMin);
                break;
            case PEDAL_SMOOTHNESS_R:
                fillTrExtrema(SensorType.PEDAL_SMOOTHNESS_R, R.id.trPedalSmoothnessRight, R.id.tvPedalSmoothnessRightMean, R.id.tvPedalSmoothnessRightMax, R.id.tvPedalSmoothnessRightMin);
                break;
            case ALTITUDE:
                fillTrExtrema(SensorType.ALTITUDE, R.id.trAltitude, R.id.tvAltitudeMean, R.id.tvAltitudeMax, R.id.tvAltitudeMin);
                break;
            case TEMPERATURE:
                fillTrExtrema(SensorType.TEMPERATURE, R.id.trTemperature, R.id.tvTemperatureMean, R.id.tvTemperatureMax, R.id.tvTemperatureMin);
                break;
        }
    }

    protected void fillTrExtrema(@NonNull SensorType sensorType, int trId, int tvMeanId, int tvMaxId, int tvMinId) {
        if (DEBUG) Log.i(TAG, "fillTrExtrema for sensor: " + sensorType.name());

        // if one of the extrema values contains valid data (not 0), we want to show the complete row
        boolean dataAvailable =     // NOTE THAT WE MUST NOT USE THE SHORT-CIRCUIT || HERE!  When using || instead of |, we only update the mean/avg.
                fillTvExtrema(sensorType, ExtremaType.AVG, tvMeanId)
                | fillTvExtrema(sensorType, ExtremaType.MAX, tvMaxId)
                | fillTvExtrema(sensorType, ExtremaType.MIN, tvMinId);

        if (dataAvailable) {  // there seems to be valid data, show it
            getActivity().findViewById(trId).setVisibility(View.VISIBLE);
            showExtremaValuesSeparator();
        } else {   // no valid data available, remove the complete row
            getActivity().findViewById(trId).setVisibility(View.GONE);
        }

        if (sensorType == SensorType.PACE_spm) {
            mPaceExtremaValuesAvailable = dataAvailable;
        }

    }

    protected boolean fillTvExtrema(@NonNull SensorType sensorType, @NonNull ExtremaType extremaType, int tvId) {

        Double extremaValue = WorkoutSummariesDatabaseManager.getExtremaValue(mWorkoutID, sensorType, extremaType);
        Log.d(TAG, sensorType.name() + " " + extremaType.name() + " extremaValue=" + extremaValue);
        if (extremaValue != null) {
            TextView tv = getActivity().findViewById(tvId) ;
            tv.setText(sensorType.getMyFormatter().format(extremaValue));
            return true;
        } else {
            return false;
        }
    }

    // TODO: somewhere, we have to store all these values!
    protected void fillViewsFromSavedInstanceState(@NonNull Bundle savedInstanceState) {
        if (DEBUG) Log.i(TAG, "fillViewsFromSavedInstanceState");

        // first, the member variables
        mWorkoutID = savedInstanceState.getLong(WorkoutSummaries.WORKOUT_ID);
        mBaseFileName = savedInstanceState.getString(BASE_FILE_NAME);
        mEquipmentName = savedInstanceState.getString(EQUIPMENT);
        mSportTypeId = savedInstanceState.getLong(SPORT_ID);
        mAverageSpeed = savedInstanceState.getDouble(AVERAGE_SPEED);
        onSportTypeChanged();   // also updates the equipment

        // update the views
        editExportName.setText(savedInstanceState.getString(EXPORT_NAME));

        ((RadioButton) getActivity().findViewById(R.id.rbCommute)).setChecked(savedInstanceState.getBoolean(COMMUTE));
        ((RadioButton) getActivity().findViewById(R.id.rbTrainer)).setChecked(savedInstanceState.getBoolean(TRAINER));
        ((EditText) getActivity().findViewById(R.id.editDescription)).setText(savedInstanceState.getString(DESCRIPTION));
        ((EditText) getActivity().findViewById(R.id.editGoal)).setText(savedInstanceState.getString(GOAL));
        ((EditText) getActivity().findViewById(R.id.editMethod)).setText(savedInstanceState.getString(METHOD));
        ((TextView) getActivity().findViewById(R.id.tvStartTime)).setText(savedInstanceState.getString(START_TIME));
        ((TextView) getActivity().findViewById(R.id.tvTotalTime)).setText(savedInstanceState.getString(TOTAL_TIME));
        ((TextView) getActivity().findViewById(R.id.tvActiveTime)).setText(savedInstanceState.getString(ACTIVE_TIME));
        ((TextView) getActivity().findViewById(R.id.tvSensorTypes)).setText(savedInstanceState.getString(SENSOR_TYPES));
        ((TextView) getActivity().findViewById(R.id.tvDistance)).setText(savedInstanceState.getString(DISTANCE));
        ((TextView) getActivity().findViewById(R.id.tvMaxLineDistance)).setText(savedInstanceState.getString(MAX_LINE_DISTANCE));

        if (savedInstanceState.containsKey(CALORIES)) {
            getActivity().findViewById(R.id.tvCalories).setVisibility(View.VISIBLE);
            ((TextView) getActivity().findViewById(R.id.tvCalories)).setText(savedInstanceState.getString(CALORIES));
        } else {
            getActivity().findViewById(R.id.tvCalories).setVisibility(View.GONE);
        }

        if (savedInstanceState.containsKey(HR_MEAN)) {
            showExtremaValuesSeparator();
            getActivity().findViewById(R.id.trHR).setVisibility(View.VISIBLE);
            ((TextView) getActivity().findViewById(R.id.tvHRMean)).setText(savedInstanceState.getString(HR_MEAN));
            ((TextView) getActivity().findViewById(R.id.tvHRMax)).setText(savedInstanceState.getString(HR_MAX));
            ((TextView) getActivity().findViewById(R.id.tvHRMin)).setText(savedInstanceState.getString(HR_MIN));
        } else {
            getActivity().findViewById(R.id.trHR).setVisibility(View.GONE);
        }

        if (savedInstanceState.containsKey(SPEED_MEAN)) {
            showExtremaValuesSeparator();
            getActivity().findViewById(R.id.trSpeed).setVisibility(View.VISIBLE);
            ((TextView) getActivity().findViewById(R.id.tvSpeedMean)).setText(savedInstanceState.getString(SPEED_MEAN));
            ((TextView) getActivity().findViewById(R.id.tvSpeedMax)).setText(savedInstanceState.getString(SPEED_MAX));
            ((TextView) getActivity().findViewById(R.id.tvSpeedMin)).setText(savedInstanceState.getString(SPEED_MIN));
        } else {
            getActivity().findViewById(R.id.trSpeed).setVisibility(View.GONE);
        }

        if (savedInstanceState.containsKey(PACE_MEAN)) {
            showExtremaValuesSeparator();
            ((TextView) getActivity().findViewById(R.id.tvPaceMean)).setText(savedInstanceState.getString(PACE_MEAN));
            ((TextView) getActivity().findViewById(R.id.tvPaceMax)).setText(savedInstanceState.getString(PACE_MAX));
            ((TextView) getActivity().findViewById(R.id.tvPaceMin)).setText(savedInstanceState.getString(PACE_MIN));
            showOrHideTrPace();
        } else {
            getActivity().findViewById(R.id.trPace).setVisibility(View.GONE);
        }

        if (savedInstanceState.containsKey(CADENCE_MEAN)) {
            showExtremaValuesSeparator();
            getActivity().findViewById(R.id.trCadence).setVisibility(View.VISIBLE);
            ((TextView) getActivity().findViewById(R.id.tvCadenceMean)).setText(savedInstanceState.getString(CADENCE_MEAN));
            ((TextView) getActivity().findViewById(R.id.tvCadenceMax)).setText(savedInstanceState.getString(CADENCE_MAX));
            ((TextView) getActivity().findViewById(R.id.tvCadenceMin)).setText(savedInstanceState.getString(CADENCE_MIN));
        } else {
            getActivity().findViewById(R.id.trCadence).setVisibility(View.GONE);
        }

        if (savedInstanceState.containsKey(POWER_MEAN)) {
            showExtremaValuesSeparator();
            getActivity().findViewById(R.id.trPower).setVisibility(View.VISIBLE);
            ((TextView) getActivity().findViewById(R.id.tvPowerMean)).setText(savedInstanceState.getString(POWER_MEAN));
            ((TextView) getActivity().findViewById(R.id.tvPowerMax)).setText(savedInstanceState.getString(POWER_MAX));
            ((TextView) getActivity().findViewById(R.id.tvPowerMin)).setText(savedInstanceState.getString(POWER_MIN));
        } else {
            getActivity().findViewById(R.id.trPower).setVisibility(View.GONE);
        }

        if (savedInstanceState.containsKey(TORQUE_MEAN)) {
            showExtremaValuesSeparator();
            getActivity().findViewById(R.id.trTorque).setVisibility(View.VISIBLE);
            ((TextView) getActivity().findViewById(R.id.tvTorqueMean)).setText(savedInstanceState.getString(TORQUE_MEAN));
            ((TextView) getActivity().findViewById(R.id.tvTorqueMax)).setText(savedInstanceState.getString(TORQUE_MAX));
            ((TextView) getActivity().findViewById(R.id.tvTorqueMin)).setText(savedInstanceState.getString(TORQUE_MIN));
        } else {
            getActivity().findViewById(R.id.trTorque).setVisibility(View.GONE);
        }

        if (savedInstanceState.containsKey(PEDAL_POWER_BALANCE_MEAN)) {
            showExtremaValuesSeparator();
            getActivity().findViewById(R.id.trPedalPowerBalance).setVisibility(View.VISIBLE);
            ((TextView) getActivity().findViewById(R.id.tvPedalPowerBalanceMean)).setText(savedInstanceState.getString(PEDAL_POWER_BALANCE_MEAN));
            ((TextView) getActivity().findViewById(R.id.tvPedalPowerBalanceMax)).setText(savedInstanceState.getString(PEDAL_POWER_BALANCE_MAX));
            ((TextView) getActivity().findViewById(R.id.tvPedalPowerBalanceMin)).setText(savedInstanceState.getString(PEDAL_POWER_BALANCE_MIN));
        } else {
            getActivity().findViewById(R.id.trPedalPowerBalance).setVisibility(View.GONE);
        }

        if (savedInstanceState.containsKey(PEDAL_SMOOTHNESS_LEFT_MEAN)) {
            showExtremaValuesSeparator();
            getActivity().findViewById(R.id.trPedalSmoothnessLeft).setVisibility(View.VISIBLE);
            ((TextView) getActivity().findViewById(R.id.tvPedalSmoothnessLeftMean)).setText(savedInstanceState.getString(PEDAL_SMOOTHNESS_LEFT_MEAN));
            ((TextView) getActivity().findViewById(R.id.tvPedalSmoothnessLeftMax)).setText(savedInstanceState.getString(PEDAL_SMOOTHNESS_LEFT_MAX));
            ((TextView) getActivity().findViewById(R.id.tvPedalSmoothnessLeftMin)).setText(savedInstanceState.getString(PEDAL_SMOOTHNESS_LEFT_MIN));
        } else {
            getActivity().findViewById(R.id.trPedalSmoothnessLeft).setVisibility(View.GONE);
        }

        if (savedInstanceState.containsKey(PEDAL_SMOOTHNESS_MEAN)) {
            showExtremaValuesSeparator();
            getActivity().findViewById(R.id.trPedalSmoothness).setVisibility(View.VISIBLE);
            ((TextView) getActivity().findViewById(R.id.tvPedalSmoothnessMean)).setText(savedInstanceState.getString(PEDAL_SMOOTHNESS_MEAN));
            ((TextView) getActivity().findViewById(R.id.tvPedalSmoothnessMax)).setText(savedInstanceState.getString(PEDAL_SMOOTHNESS_MAX));
            ((TextView) getActivity().findViewById(R.id.tvPedalSmoothnessMin)).setText(savedInstanceState.getString(PEDAL_SMOOTHNESS_MIN));
        } else {
            getActivity().findViewById(R.id.trPedalSmoothnessRight).setVisibility(View.GONE);
        }

        if (savedInstanceState.containsKey(PEDAL_SMOOTHNESS_RIGHT_MEAN)) {
            showExtremaValuesSeparator();
            getActivity().findViewById(R.id.trPedalSmoothnessRight).setVisibility(View.VISIBLE);
            ((TextView) getActivity().findViewById(R.id.tvPedalSmoothnessRightMean)).setText(savedInstanceState.getString(PEDAL_SMOOTHNESS_RIGHT_MEAN));
            ((TextView) getActivity().findViewById(R.id.tvPedalSmoothnessRightMax)).setText(savedInstanceState.getString(PEDAL_SMOOTHNESS_RIGHT_MAX));
            ((TextView) getActivity().findViewById(R.id.tvPedalSmoothnessRightMin)).setText(savedInstanceState.getString(PEDAL_SMOOTHNESS_RIGHT_MIN));
        } else {
            getActivity().findViewById(R.id.trPedalSmoothnessRight).setVisibility(View.GONE);
        }

        if (savedInstanceState.containsKey(ALTITUDE_MEAN)) {
            showExtremaValuesSeparator();
            getActivity().findViewById(R.id.trAltitude).setVisibility(View.VISIBLE);
            ((TextView) getActivity().findViewById(R.id.tvAltitudeMean)).setText(savedInstanceState.getString(ALTITUDE_MEAN));
            ((TextView) getActivity().findViewById(R.id.tvAltitudeMax)).setText(savedInstanceState.getString(ALTITUDE_MAX));
            ((TextView) getActivity().findViewById(R.id.tvAltitudeMin)).setText(savedInstanceState.getString(ALTITUDE_MIN));
        } else {
            getActivity().findViewById(R.id.trAltitude).setVisibility(View.GONE);
        }

        if (savedInstanceState.containsKey(TEMPERATURE_MEAN)) {
            showExtremaValuesSeparator();
            getActivity().findViewById(R.id.trTemperature).setVisibility(View.VISIBLE);
            ((TextView) getActivity().findViewById(R.id.tvTemperatureMean)).setText(savedInstanceState.getString(TEMPERATURE_MEAN));
            ((TextView) getActivity().findViewById(R.id.tvTemperatureMax)).setText(savedInstanceState.getString(TEMPERATURE_MAX));
            ((TextView) getActivity().findViewById(R.id.tvTemperatureMin)).setText(savedInstanceState.getString(TEMPERATURE_MIN));
        } else {
            getActivity().findViewById(R.id.trTemperature).setVisibility(View.GONE);
        }

        getActivity().findViewById(R.id.buttonDeleteWorkout).setVisibility(savedInstanceState.getBoolean(SHOW_DELETE_BUTTON) ? View.VISIBLE : View.GONE);
    }

    protected void onSportTypeChanged() {
        if (DEBUG) Log.d(TAG, "onSportTypeChanged to " + mSportTypeId);

        mBSportType = SportTypeDatabaseManager.getBSportType(mSportTypeId);

        // first, configure the sport spinner
        setSpinnerSport();

        // show pace field only for running
        showOrHideTrPace();

        // adapt the indoor trainer, equipment name, and cadence name
        TextView tvCadence = getActivity().findViewById(R.id.tvCadence);
        switch (mBSportType) {
            case BIKE:
                tvCadence.setText(R.string.cadence_bike_short);
                rbTrainer.setText(R.string.trainer_bike);
                tvEquipment.setText(R.string.equipment_type_bike);
                ALL = getString(R.string.equipment_all_bikes);
                break;

            case RUN:
                tvCadence.setText(R.string.cadence_run_short);
                rbTrainer.setText(R.string.trainer_run);
                tvEquipment.setText(R.string.equipment_type_shoe);
                ALL = getString(R.string.equipment_all_shoes);
                break;

            default:
                tvCadence.setText(R.string.cadence_short);
                rbTrainer.setText(R.string.trainer_general);
                tvEquipment.setVisibility(View.INVISIBLE);
                if (spinnerEquipment != null) {
                    // spinnerEquipment.setClickable(false);
                    spinnerEquipment.setVisibility(View.INVISIBLE);
                }
                // TODO: something else?
                return;
        }


        spinnerEquipment.setClickable(true);
        spinnerEquipment.setVisibility(View.VISIBLE);
        tvEquipment.setVisibility(View.VISIBLE);

        setSpinnerEquipment(false);
    }

    private void setSpinnerSport() {
        if (DEBUG) Log.i(TAG, "setSpinnerSport");

        if (mShowAllSportTypes) {
            if (DEBUG) Log.i(TAG, "show all sport types");
            mSportTypeUiNameList = SportTypeDatabaseManager.getSportTypesUiNameList();
            mSportTypeIdList = SportTypeDatabaseManager.getSportTypesIdList();
        } else {
            if (DEBUG) Log.i(TAG, "do NOT show all sport types");
            mSportTypeUiNameList = SportTypeDatabaseManager.getSportTypesUiNameList(mBSportType, mAverageSpeed);
            mSportTypeIdList = SportTypeDatabaseManager.getSportTypesIdList(mBSportType, mAverageSpeed);
            if (mSportTypeUiNameList.size() <= 1
                    || !mSportTypeIdList.contains(mSportTypeId)) {
                mShowAllSportTypes = true;
                setSpinnerSport();
                return;
            }
            mSportTypeUiNameList.add(getString(R.string.show_all_sport_types));
        }

        spinnerSport.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, mSportTypeUiNameList.toArray(new String[mSportTypeUiNameList.size()])));
        if (DEBUG) Log.i(TAG, "calling setSelection");
        spinnerSport.setSelection(mSportTypeIdList.indexOf(mSportTypeId));
        if (DEBUG) Log.i(TAG, "called setSelection");
    }

    private void setSpinnerEquipment(boolean allEquipment) {
        if (DEBUG) Log.d(TAG, "setSpinnerEquipment " + (allEquipment ? "ALL" : "selected"));

        EquipmentDbHelper equipmentDbHelper = new EquipmentDbHelper(getActivity());
        List<String> equipmentList = new ArrayList<>();

        if (!allEquipment) {
            equipmentList = equipmentDbHelper.getLinkedEquipment((int) mWorkoutID);
            if (DEBUG) Log.d(TAG, "got selected equipmentList, size: " + equipmentList.size());
        }

        // check the size of the found equipment.
        // when nothing is found, we have to create the full list
        // when only one is found, we also create the full list but set mEquipmentName
        if (equipmentList.size() >= 2) {
            if (DEBUG) Log.d(TAG, "equipmentList.size() >= 2");
            equipmentList.add(ALL);
        } else {
            if (equipmentList.size() == 1 && mEquipmentName == null) {
                if (DEBUG) Log.d(TAG, "equipmentList.size() == 1 && mEquipmentName == null");
                mEquipmentName = equipmentList.get(0);
            }

            equipmentList = equipmentDbHelper.getEquipment(SportTypeDatabaseManager.getBSportType(mSportTypeId));

        }
        if (DEBUG) {
            Log.d(TAG, "Equipment list:");
            for (String equipment : equipmentList) {
                Log.d(TAG, equipment);
            }
        }

        if (equipmentList.isEmpty()) {
            // there is no equipment, so the spinner is removed.
            spinnerEquipment.setVisibility(View.GONE);
            return;
        } else {
            spinnerEquipment.setVisibility(View.VISIBLE);
        }

        String[] equipment = equipmentList.toArray(new String[equipmentList.size()]);

        spinnerEquipment.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, equipment));

        if (mEquipmentName != null) {
            spinnerEquipment.setSelection(equipmentList.indexOf(mEquipmentName));
        } else if (DEBUG) {
            Log.d(TAG, "mEquipmentName == null, so the first entry is selected");
        }
    }

    protected void saveWorkout() {
        if (DEBUG) Log.d(TAG, "saveWorkout()");

        ContentValues values = new ContentValues();

        values.put(WorkoutSummaries.WORKOUT_NAME, editExportName.getText().toString());
        values.put(WorkoutSummaries.GOAL, editGoal.getText().toString());
        values.put(WorkoutSummaries.METHOD, editMethod.getText().toString());
        values.put(WorkoutSummaries.SPORT_ID, mSportTypeId);
        values.put(WorkoutSummaries.DESCRIPTION, editDescription.getText().toString());
        values.put(WorkoutSummaries.COMMUTE, rbCommute.isChecked());
        values.put(WorkoutSummaries.TRAINER, rbTrainer.isChecked());
        values.put(WorkoutSummaries.FINISHED, 1);  // when the user saves the changes, the workout is finished properly (by my definition ;-)

        if (spinnerEquipment.getSelectedItem() != null) {
            if (DEBUG) {
                Log.d(TAG, "selected equipment: " + spinnerEquipment.getSelectedItem());
                Log.d(TAG, "equipmentId: " + new EquipmentDbHelper(getActivity()).getEquipmentId((String) spinnerEquipment.getSelectedItem()));
            }
            values.put(WorkoutSummaries.EQUIPMENT_ID, new EquipmentDbHelper(getActivity()).getEquipmentId((String) spinnerEquipment.getSelectedItem()));
        }

        WorkoutSummariesDatabaseManager databaseManager = WorkoutSummariesDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        try {
            db.update(WorkoutSummaries.TABLE,
                    values,
                    WorkoutSummaries.C_ID + "=" + mWorkoutID,
                    null);
        } catch (SQLException e) {
            // TODO: use Toast?
            Log.e(TAG, "Error while writing" + e);
        }
        databaseManager.closeDatabase(); // db.close();

        if (DEBUG) Log.d(TAG, "end of saveWorkout()");
    }

    protected void showFancyWorkoutNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.choose_auto_name);
        ListView listView = new ListView(getContext());
        builder.setView(listView);
        final Dialog dialog = builder.create();

        final List<String> fancyNameList = WorkoutSummariesDatabaseManager.getFancyNameList();
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, fancyNameList.toArray(new String[fancyNameList.size()]));
        listView.setAdapter(arrayAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String fullFancyName = WorkoutSummariesDatabaseManager.getFancyNameAndIncrement(fancyNameList.get(position));
                editExportName.setText(fullFancyName);
                dialog.dismiss();
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                long fancyNameId = WorkoutSummariesDatabaseManager.getFancyNameId(fancyNameList.get(position));
                EditFancyWorkoutNameDialog dialog = EditFancyWorkoutNameDialog.newInstance(fancyNameId);
                dialog.show(getFragmentManager(), EditFancyWorkoutNameDialog.TAG);
                return true;
            }
        });

        dialog.show();
    }

}
