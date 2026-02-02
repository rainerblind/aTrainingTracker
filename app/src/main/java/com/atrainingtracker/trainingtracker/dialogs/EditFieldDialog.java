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

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.ActivityType;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;
import com.atrainingtracker.banalservice.fragments.ConfigureFilterDialogFragment;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.views.DeviceIdAndNameArrayAdapter;
import com.atrainingtracker.trainingtracker.views.SensorArrayAdapter;
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager;

import java.util.LinkedList;


/**
 * Created by rainer on 05.01.17.
 */


public class EditFieldDialog extends DialogFragment {
    public static final String TAG = EditFieldDialog.class.getName();
    public static final String TRACKING_VIEW_CHANGED_INTENT = "TRACKING_VIEW_CHANGED_INTENT";
    protected static final Integer[] TEXT_SIZES = {20, 25, 30, 35, 40, 45, 50, 60, 70, 80};
    private static final boolean DEBUG = TrainingApplication.getDebug(false);
    private static final String ACTIVITY_TYPE = "ACTIVITY_TYPE";
    private static final String ROW_ID = "ROW_ID";
    private static final String SENSOR_TYPE = "SENSOR_TYPE";
    private static final String DEVICE_ID = "DEVICE_ID";
    private static final String TEXT_SIZE = "TEXT_SIZE";
    protected final IntentFilter mFilterChangedFilter = new IntentFilter(ConfigureFilterDialogFragment.FILTERS_CHANGED_INTENT);
    private ActivityType mActivityType;
    private View mMainView;
    private long mRowId;
    private SensorType mSensorType;
    final BroadcastReceiver mFilterChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            configureConfigureFilterButton(mMainView);
        }
    };
    private long mDeviceId;
    private int mTextSize;

    protected static int textSize2Pos(int textSize) {
        if (textSize == 20) {
            return 0;
        } else if (textSize == 25) {
            return 1;
        } else if (textSize == 30) {
            return 2;
        } else if (textSize == 35) {
            return 3;
        } else if (textSize == 40) {
            return 4;
        } else if (textSize == 45) {
            return 5;
        } else if (textSize == 50) {
            return 6;
        } else if (textSize == 60) {
            return 7;
        } else if (textSize == 70) {
            return 8;
        } else if (textSize == 80) {
            return 9;
        } else {
            return 2;
        }

    }

    @NonNull
    public static EditFieldDialog newInstance(@NonNull ActivityType activityType, @NonNull TrackingViewsDatabaseManager.ViewInfo viewInfo) {
        if (DEBUG) Log.i(TAG, "newInstance");

        EditFieldDialog fragment = new EditFieldDialog();

        Bundle args = new Bundle();
        args.putString(ACTIVITY_TYPE, activityType.name());
        args.putString(SENSOR_TYPE, viewInfo.sensorType().name());
        args.putLong(ROW_ID, viewInfo.rowId());
        args.putInt(TEXT_SIZE, viewInfo.textSize());
        args.putLong(DEVICE_ID, viewInfo.sourceDeviceId());
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

        mActivityType = ActivityType.valueOf(getArguments().getString(ACTIVITY_TYPE));
        mSensorType = SensorType.valueOf(getArguments().getString(SENSOR_TYPE));
        mRowId = getArguments().getLong(ROW_ID);
        mDeviceId = getArguments().getLong(DEVICE_ID);
        mTextSize = getArguments().getInt(TEXT_SIZE);
    }

    @Override
    public void onResume() {
        super.onResume();
        ContextCompat.registerReceiver(getContext(), mFilterChangedReceiver, mFilterChangedFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    public void onPause() {
        super.onPause();
        if (DEBUG) Log.i(TAG, "onPause()");

        getContext().unregisterReceiver(mFilterChangedReceiver);
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        TextView title = new TextView(getActivity());
        // You Can Customise your Title here
        title.setText(R.string.edit_field);
//         title.setBackgroundColor(getResources().getColor(R.color.my_blue));
        title.setPadding(10, 10, 10, 10);
        title.setGravity(Gravity.CENTER);
//        title.setTextColor(getResources().getColor(R.color.my_white));
        title.setTextSize(20);

        builder.setCustomTitle(title);
        // builder.setTitle(getString(R.string.Lap_NR, lapNr));

        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();

        // Inflate and set the layout for the dialog
        mMainView = inflater.inflate(R.layout.config_tracking_view_entry_configurable, null);
        builder.setView(mMainView);
//        mMainView.setBackgroundColor(getResources().getColor(R.color.my_white));


        final SensorType[] sensorTypes = ActivityType.getSensorTypeArray(mActivityType, getContext());
        SensorArrayAdapter SENSOR_ARRAY_ADAPTER = new SensorArrayAdapter(getContext(), android.R.layout.simple_spinner_item, sensorTypes);

        ArrayAdapter<Integer> TEXT_SIZES_ARRAY_ADAPTER = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, TEXT_SIZES);

        Spinner sensorSpinner = mMainView.findViewById(R.id.spinnerSensor);
        sensorSpinner.setAdapter(SENSOR_ARRAY_ADAPTER);

        // sensorSpinner.setSelection(sensorType.ordinal());  // TODO: assuming that all SensorTypes are shown
        sensorSpinner.setSelection(SENSOR_ARRAY_ADAPTER.getPosition(mSensorType));

        sensorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mSensorType = sensorTypes[position];
                configureSourceSpinner(mMainView); // update corresponding sourceSpinner
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        configureSourceSpinner(mMainView);
        configureConfigureFilterButton(mMainView);

        Spinner textSizeSpinner = mMainView.findViewById(R.id.spinnerTextSize);
        textSizeSpinner.setAdapter(TEXT_SIZES_ARRAY_ADAPTER);
        textSizeSpinner.setSelection(textSize2Pos(mTextSize));

        textSizeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mTextSize = TEXT_SIZES[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        // remove the delete button
        Button deleteButton = mMainView.findViewById(R.id.buttonDelete);
        deleteButton.setVisibility(View.GONE);

        // configure the positive/OK and negative/cancel button
        builder.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                TrackingViewsDatabaseManager trackingViewsDatabaseManager = TrackingViewsDatabaseManager.getInstance(getContext());
                trackingViewsDatabaseManager.updateTextSizeOfRow(mRowId, mTextSize);
                trackingViewsDatabaseManager.updateSensorTypeOfRow(mRowId, mSensorType);
                trackingViewsDatabaseManager.updateSourceDeviceIdOfRow(mRowId, mDeviceId);
                getActivity().sendBroadcast(new Intent(TRACKING_VIEW_CHANGED_INTENT)
                        .setPackage(getActivity().getPackageName()));
            }
        });
        builder.setNegativeButton(R.string.Cancel, null);


        return builder.create();
    }

    protected void configureSourceSpinner(@NonNull View parentView) {
        Spinner sourceSpinner = parentView.findViewById(R.id.spinnerSource);
        TextView textViewSource = parentView.findViewById(R.id.textViewSource);
        DevicesDatabaseManager.DeviceIdAndNameLists deviceIdAndNameLists = DevicesDatabaseManager.getDeviceIdAndNameLists(mSensorType);
        if (DEBUG)
            Log.i(TAG, "configuring view for sensorType " + mSensorType + " deviceId=" + mDeviceId);
        if (deviceIdAndNameLists == null) {                        // only build in sensors like TIME, ...
            if (DEBUG) Log.i(TAG, "list is null");
            sourceSpinner.setVisibility(View.GONE);
            textViewSource.setVisibility(View.VISIBLE);
            textViewSource.setText(R.string.smartphone);
        } else if (deviceIdAndNameLists.deviceIds.isEmpty()) {     // no sensors available
            if (DEBUG) Log.i(TAG, "size of list is zero");
            sourceSpinner.setVisibility(View.GONE);
            textViewSource.setVisibility(View.VISIBLE);
            textViewSource.setText(R.string.no_sensor_available);
        } else {
            if (DEBUG) Log.i(TAG, "there seem to be some devices...");
            sourceSpinner.setVisibility(View.VISIBLE);
            textViewSource.setVisibility(View.GONE);

            LinkedList<Long> deviceIds = deviceIdAndNameLists.deviceIds;
            LinkedList<String> names = deviceIdAndNameLists.names;

            deviceIds.addFirst(0L);
            names.addFirst(getContext().getString(R.string.bestSensor));

            if (mDeviceId < 0) {
                mDeviceId = 0;
            }

            final Long[] a_deviceIds = deviceIdAndNameLists.deviceIds.toArray(new Long[0]);
            DeviceIdAndNameArrayAdapter deviceIdAndNameArrayAdapter = new DeviceIdAndNameArrayAdapter(getContext(),
                    android.R.layout.simple_spinner_item,
                    a_deviceIds,
                    names.toArray(new String[0]));

            sourceSpinner.setAdapter(deviceIdAndNameArrayAdapter);
            sourceSpinner.setSelection(deviceIdAndNameArrayAdapter.getPosition(mDeviceId));  // TODO: when first showing this view, the spinner is 'empty'
            sourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (DEBUG)
                        Log.i(TAG, "sourceSpinner selected: position=" + position + ", id=" + id);

                    mDeviceId = a_deviceIds[position];
                }

                @Override // TODO Auto-generated method stub
                public void onNothingSelected(AdapterView<?> arg0) {
                }
            });
        }

    }

    protected void configureConfigureFilterButton(@NonNull View parentView) {
        Button button = parentView.findViewById(R.id.buttonConfigureFilter);
        TextView tvConfigure = parentView.findViewById(R.id.tvConfigureFilter);

        if (mSensorType.filteringPossible) {
            button.setVisibility(View.VISIBLE);
            tvConfigure.setVisibility(View.VISIBLE);

            final TrackingViewsDatabaseManager.FilterInfo filterInfo = TrackingViewsDatabaseManager.getInstance(getContext()).getFilterInfo(mRowId);

            button.setText(ConfigureFilterDialogFragment.getFilterSummary(getContext(), filterInfo.filterType, filterInfo.filterConstant));

            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ConfigureFilterDialogFragment configureFilterDialogFragment = ConfigureFilterDialogFragment.newInstance(mRowId, filterInfo.filterType, filterInfo.filterConstant);
                    configureFilterDialogFragment.show(getFragmentManager(), ConfigureFilterDialogFragment.TAG);
                }
            });
        } else {
            button.setVisibility(View.GONE);
            tvConfigure.setVisibility(View.GONE);
        }
    }
}
