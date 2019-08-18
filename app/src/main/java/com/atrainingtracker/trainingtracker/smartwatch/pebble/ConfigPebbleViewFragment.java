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

package com.atrainingtracker.trainingtracker.smartwatch.pebble;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.ActivityType;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.activities.ConfigViewsActivity;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.views.SensorArrayAdapter;
import com.atrainingtracker.trainingtracker.fragments.ConfigViewFragment;

import java.util.LinkedList;
import java.util.List;

public class ConfigPebbleViewFragment extends ConfigViewFragment {
    public static final String PEBBLE_VIEW_CHANGED_INTENT = "PEBBLE_VIEW_CHANGED_INTENT";
    private static final String TAG = ConfigPebbleViewFragment.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG && true;
    protected static ArrayAdapter<Integer> NUMBER_OF_FIELDS_ARRAY_ADAPTER;
    protected static SensorArrayAdapter SENSOR_ARRAY_ADAPTER;

    protected SensorType[] mSensorTypes = ActivityType.getSensorTypeArray(ActivityType.GENERIC, null);
    protected List<Spinner> mSensorTypeSpinners = new LinkedList<Spinner>();

    // protected ActivityType mActivityType;
    // protected long mViewId;

    protected SensorType mSensorType4 = SensorType.DISTANCE_m;
    protected SensorType mSensorType5 = SensorType.LAP_NR;

    public static ConfigPebbleViewFragment newInstance(long viewId) {
        ConfigPebbleViewFragment fragment = new ConfigPebbleViewFragment();

        Bundle args = new Bundle();
        args.putLong(ConfigViewsActivity.VIEW_ID, viewId);
        fragment.setArguments(args);

        return fragment;
    }


    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NUMBER_OF_FIELDS_ARRAY_ADAPTER = new ArrayAdapter<Integer>(getContext(), android.R.layout.simple_spinner_item, new Integer[]{3, 5});

        mViewId = getArguments().getLong(ConfigViewsActivity.VIEW_ID);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreateView");

        mActivityType = PebbleDatabaseManager.getActivityType(mViewId);

        mSensorTypes = ActivityType.getSensorTypeArray(mActivityType, getContext());
        SENSOR_ARRAY_ADAPTER = new SensorArrayAdapter(getContext(), android.R.layout.simple_spinner_item, mSensorTypes);


        View view = inflater.inflate(R.layout.pebble_config_list_5, container, false);


        final EditText etName = view.findViewById(R.id.editTextName);
        etName.setText(PebbleDatabaseManager.getName(mViewId));
        if (DEBUG) Log.i(TAG, "set name to " + PebbleDatabaseManager.getName(mViewId));

        etName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (DEBUG) Log.i(TAG, "afterTextChanged");

                notifyFinishedTyping(etName.getText().toString());
            }
        });

        LinkedList<SensorType> sensorTypeList = PebbleDatabaseManager.getSensorTypeList(mViewId);
        final int nrOfFields = sensorTypeList.size();
        if (DEBUG) Log.i(TAG, "nrOfFields=" + nrOfFields);

        final LinearLayout field4and5LinearLayout = view.findViewById(R.id.llField4And5);
        if (nrOfFields == 3) {
            if (DEBUG) Log.i(TAG, "only 3 sensorTypes in the list");
            field4and5LinearLayout.setVisibility(View.GONE);

            // add two "dummy" sensorTypes to the list
            sensorTypeList.add(mSensorType4);
            sensorTypeList.add(mSensorType5);
        }

        Spinner numberOfFieldsSpinner = view.findViewById(R.id.spinnerNumberOfFields);
        numberOfFieldsSpinner.setAdapter(NUMBER_OF_FIELDS_ARRAY_ADAPTER);
        numberOfFieldsSpinner.setSelection(nrOfFields == 3 ? 0 : 1);
        numberOfFieldsSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int newNumberOfFields = position == 0 ? 3 : 5;
                if (DEBUG)
                    Log.i(TAG, "number of fields selected: position=" + position + ", newNumberOfFields=" + newNumberOfFields);

                switch (newNumberOfFields) {

                    case 3:  // here, we have to delete row 4 and 5
                        PebbleDatabaseManager.deleteRow(mViewId, 4);
                        PebbleDatabaseManager.deleteRow(mViewId, 5);
                        field4and5LinearLayout.setVisibility(View.GONE);
                        break;

                    case 5:  // now, we have to insert values for row 4 and 5
                        PebbleDatabaseManager.addRow(mViewId, 4, mSensorType4);
                        PebbleDatabaseManager.addRow(mViewId, 5, mSensorType5);
                        field4and5LinearLayout.setVisibility(View.VISIBLE);
                        break;

                    default:
                        if (DEBUG) Log.d(TAG, "WTF: this should never ever happen");
                }
            }

            @Override // TODO Auto-generated method stub
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        // sensor fields
        mSensorTypeSpinners.clear();
        mSensorTypeSpinners.add((Spinner) view.findViewById(R.id.spinnerField1));
        mSensorTypeSpinners.add((Spinner) view.findViewById(R.id.spinnerField2));
        mSensorTypeSpinners.add((Spinner) view.findViewById(R.id.spinnerField3));
        mSensorTypeSpinners.add((Spinner) view.findViewById(R.id.spinnerField4));
        mSensorTypeSpinners.add((Spinner) view.findViewById(R.id.spinnerField5));

        int counter = 0;
        for (SensorType sensorType : sensorTypeList) {
            if (DEBUG)
                Log.i(TAG, "configuring spinner at position " + counter + ", sensorType=" + sensorType);

            final int finalCounter = counter + 1;
            Spinner spinner = mSensorTypeSpinners.get(counter);
            spinner.setAdapter(SENSOR_ARRAY_ADAPTER);
            spinner.setSelection(SENSOR_ARRAY_ADAPTER.getPosition(sensorType));
            spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (finalCounter < 4 || field4and5LinearLayout.getVisibility() == View.VISIBLE) {
                        if (DEBUG) Log.i(TAG, "update sensorType finalCounter=" + finalCounter);
                        PebbleDatabaseManager.updateSensorType(mViewId, finalCounter, mSensorTypes[position]);
                    }
                    if (finalCounter == 4) {
                        mSensorType4 = mSensorTypes[position];
                    } else if (finalCounter == 5) {
                        mSensorType5 = mSensorTypes[position];
                    }
                }

                @Override // TODO Auto-generated method stub
                public void onNothingSelected(AdapterView<?> arg0) {
                }
            });

            counter++;
        }


        if (DEBUG) Log.i(TAG, "finished onCreateView");

        return view;
    }

//    @Override
//    public void onResume()
//    {
//        super.onResume();
//        if (DEBUG) Log.i(TAG, "onResume");
//
//    }


    @Override
    protected void updateNameOfView(String name) {
        PebbleDatabaseManager.updateNameOfView(mViewId, name);
    }

    public void onPause() {
        super.onPause();
        if (DEBUG) Log.i(TAG, "onPause()");

        getActivity().sendBroadcast(new Intent(PEBBLE_VIEW_CHANGED_INTENT));
    }

}
