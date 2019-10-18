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
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;

import java.util.List;

/**
 * Created by rainer on 05.01.17.
 */

public class EditFancyWorkoutNameDialog extends DialogFragment {
    public static final String TAG = EditFancyWorkoutNameDialog.class.getName();
    public static final String FANCY_WORKOUT_NAME_CHANGED_INTENT = "FANCY_WORKOUT_NAME_CHANGED_INTENT";
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;
    private static final String FANCY_NAME_ID = "FANCY_NAME_ID";

    private long mFancyNameId = -1;

    public static EditFancyWorkoutNameDialog newInstance(long fancyNameId) {
        if (DEBUG) Log.i(TAG, "newInstance");

        EditFancyWorkoutNameDialog fragment = new EditFancyWorkoutNameDialog();

        Bundle args = new Bundle();
        args.putLong(FANCY_NAME_ID, fancyNameId);
        fragment.setArguments(args);

        return fragment;
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate");

        mFancyNameId = getArguments().getLong(FANCY_NAME_ID);
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        long sportTypeId;
        String sportTypeName = null;
        String startLocationName = null;
        String endLocationName = null;
        String fancyName = null;
        boolean addCounter = true;
        int counter = 0;
        boolean addVia = true;

        // get all the data
        List<String> myLocationNameList = KnownLocationsDatabaseManager.getMyLocationNameList();
        List<String> sportTypesList = SportTypeDatabaseManager.getSportTypesList();

        final SQLiteDatabase db = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();

        Cursor cursor = db.query(WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS,  // String table,
                null,                                           // String[] columns,
                WorkoutSummaries.C_ID + " =? ",                 // String selection,
                new String[]{Long.toString(mFancyNameId)},               // String[] selectionArgs,
                null, null, null);                              // String groupBy, String having, String orderBy

        if (cursor.moveToFirst()) {
            sportTypeId = cursor.getLong(cursor.getColumnIndex(WorkoutSummaries.SPORT_ID));
            sportTypeName = SportTypeDatabaseManager.getUIName(sportTypeId);
            startLocationName = cursor.getString(cursor.getColumnIndex(WorkoutSummaries.START_LOCATION_NAME));
            endLocationName = cursor.getString(cursor.getColumnIndex(WorkoutSummaries.END_LOCATION_NAME));
            fancyName = cursor.getString(cursor.getColumnIndex(WorkoutSummaries.FANCY_NAME));
            addCounter = cursor.getInt(cursor.getColumnIndex(WorkoutSummaries.ADD_COUNTER)) >= 1;
            counter = cursor.getInt(cursor.getColumnIndex(WorkoutSummaries.COUNTER));
            addVia = cursor.getInt(cursor.getColumnIndex(WorkoutSummaries.ADD_VIA)) >= 1;
        }
        cursor.close();


        // create the main view
        LayoutInflater inflater = getActivity().getLayoutInflater();
        final View view = inflater.inflate(R.layout.edit_fancy_commute_name, null);

        final Spinner spinnerSport = view.findViewById(R.id.spinnerSport);
        final Spinner spinnerStartName = view.findViewById(R.id.spinnerStartLocation);
        final Spinner spinnerEndName = view.findViewById(R.id.spinnerEndLocation);
        final EditText etName = view.findViewById(R.id.editTextFancyBaseName);
        final CheckBox cbAddCounter = view.findViewById(R.id.checkBoxAddCounter);
        final EditText etCounter = view.findViewById(R.id.editTextCounter);
        final Button bDecrementCounter = view.findViewById(R.id.buttonDecrementCounter);
        final Button bIncrementCounter = view.findViewById(R.id.buttonIncrementCounter);
        final CheckBox cbAddVia = view.findViewById(R.id.checkBoxAddVia);

        // configure the main view
        spinnerSport.setAdapter(new ArrayAdapter(getContext(), android.R.layout.simple_list_item_1, sportTypesList));
        if (sportTypeName != null) {
            spinnerSport.setSelection(sportTypesList.indexOf(sportTypeName));
        }

        spinnerStartName.setAdapter(new ArrayAdapter(getContext(), android.R.layout.simple_list_item_1, myLocationNameList));
        if (startLocationName != null) {
            spinnerStartName.setSelection(myLocationNameList.indexOf(startLocationName));
        }

        spinnerEndName.setAdapter(new ArrayAdapter(getContext(), android.R.layout.simple_list_item_1, myLocationNameList));
        if (endLocationName != null) {
            spinnerEndName.setSelection(myLocationNameList.indexOf(endLocationName));
        }

        etName.setText(fancyName);

        cbAddCounter.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    etCounter.setVisibility(View.VISIBLE);
                    bDecrementCounter.setVisibility(View.VISIBLE);
                    bIncrementCounter.setVisibility(View.VISIBLE);
                } else {
                    etCounter.setVisibility(View.INVISIBLE);
                    bDecrementCounter.setVisibility(View.INVISIBLE);
                    bIncrementCounter.setVisibility(View.INVISIBLE);
                }
            }
        });
        cbAddCounter.setChecked(addCounter);

        etCounter.setText(counter + "");

        bDecrementCounter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    int counter = Integer.parseInt(etCounter.getText().toString());
                    if (counter >= 0) {
                        etCounter.setText(counter - 1 + "");
                    }
                } catch (Exception e) {
                }
            }
        });

        bIncrementCounter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    int counter = Integer.parseInt(etCounter.getText().toString());
                    etCounter.setText(counter + 1 + "");
                } catch (Exception e) {
                }
            }
        });

        cbAddVia.setChecked(addVia);


        // finally, create the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.workout_name_scheme);
        builder.setView(view);
        builder.setPositiveButton(R.string.OK,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Log.i(TAG, "OK clicked");

                        // save everything
                        ContentValues contentValues = new ContentValues();
                        contentValues.put(WorkoutSummaries.SPORT_ID, SportTypeDatabaseManager.getSportTypesIdList().get(spinnerSport.getSelectedItemPosition()));
                        contentValues.put(WorkoutSummaries.START_LOCATION_NAME, (String) spinnerStartName.getSelectedItem());
                        contentValues.put(WorkoutSummaries.END_LOCATION_NAME, (String) spinnerEndName.getSelectedItem());
                        contentValues.put(WorkoutSummaries.FANCY_NAME, etName.getText().toString());
                        contentValues.put(WorkoutSummaries.ADD_COUNTER, (cbAddCounter.isChecked()) ? 1 : 0);
                        try {
                            contentValues.put(WorkoutSummaries.COUNTER, Integer.parseInt(etCounter.getText().toString()));
                        } catch (Exception e) {
                        }
                        contentValues.put(WorkoutSummaries.ADD_VIA, (cbAddVia.isChecked()) ? 1 : 0);

                        if (mFancyNameId < 0) {  // create an entry
                            db.insert(WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS, null, contentValues);
                        } else {
                            db.update(WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS, contentValues,
                                    WorkoutSummaries.C_ID + "=?", new String[]{Long.toString(mFancyNameId)});
                        }

                        getContext().sendBroadcast(new Intent(FANCY_WORKOUT_NAME_CHANGED_INTENT));

                        WorkoutSummariesDatabaseManager.getInstance().closeDatabase();

                        dialog.dismiss();
                    }
                });
        builder.setNegativeButton(R.string.Cancel,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Log.i(TAG, "Cancel clicked");

                        WorkoutSummariesDatabaseManager.getInstance().closeDatabase();

                        dialog.dismiss();
                    }
                });

        return builder.create();
    }


}
