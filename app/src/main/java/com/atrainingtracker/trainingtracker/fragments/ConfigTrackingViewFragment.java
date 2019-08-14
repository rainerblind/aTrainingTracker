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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.Sensor.SensorType;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;
import com.atrainingtracker.banalservice.fragments.ConfigureFilterDialogFragment;
import com.atrainingtracker.trainingtracker.Activities.ConfigViewsActivity;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager;
import com.atrainingtracker.trainingtracker.dialogs.EditFieldDialog;

import java.util.TreeMap;

import static com.atrainingtracker.banalservice.fragments.ConfigureFilterDialogFragment.FILTERS_CHANGED_INTENT;
import static com.atrainingtracker.trainingtracker.dialogs.EditFieldDialog.TRACKING_VIEW_CHANGED_INTENT;


public class ConfigTrackingViewFragment extends ConfigViewFragment {
    protected static final SensorType SENSOR_TYPE_DEFAULT = SensorType.SPEED_mps;
    protected static final int TEXT_SIZE_DEFAULT = 30;


    // public static final String NAME_CHANGED_INTENT   = "NAME_CHANGED_INTENT";
    // public static final String VIEW_CHANGED_INTENT = "VIEW_CHANGED_INTENT";

    // public static final String VIEW_ID = "VIEW_ID";
    // public static final String NAME    = "NAME";
    private static final String TAG = ConfigTrackingViewFragment.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;
    // protected ActivityType mActivityType;
    // protected long mViewId;
    protected LinearLayout mLLSensors;
    protected LayoutInflater mLayoutInflater;
    protected String mName = null;
    protected IntentFilter mViewChangedFilter = new IntentFilter();  // actions will be added later on
    TreeMap<Integer, TreeMap<Integer, TrackingViewsDatabaseManager.ViewInfo>> mViewInfoMap = TrackingViewsDatabaseManager.getViewInfoMap(mViewId);
    BroadcastReceiver mFilterChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            getViewInfoMapAndAddSensorFields();
        }
    };

    public static ConfigTrackingViewFragment newInstance(long viewId) {
        if (DEBUG) Log.i(TAG, "newInstance(" + viewId + ")");

        ConfigTrackingViewFragment fragment = new ConfigTrackingViewFragment();

        Bundle args = new Bundle();
        args.putLong(ConfigViewsActivity.VIEW_ID, viewId);
        fragment.setArguments(args);

        return fragment;
    }


    // @Override
    // public void onAttach(Context context) {
    //     super.onAttach(context);

    //    // check whether activity implements some interfaces
    // }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreate");

        mViewId = getArguments().getLong(ConfigViewsActivity.VIEW_ID);
        if (DEBUG) Log.i(TAG, "mViewId=" + mViewId);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreateView, mViewId=" + mViewId);

        mLayoutInflater = inflater;
        mActivityType = TrackingViewsDatabaseManager.getActivityType(mViewId);

        View view = inflater.inflate(R.layout.config_tracking_view, container, false);
        mLLSensors = view.findViewById(R.id.linearLayoutSensors);

        mName = TrackingViewsDatabaseManager.getName(mViewId);
        final EditText etName = view.findViewById(R.id.editTextName);
        etName.setText(mName);

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

                mName = etName.getText().toString();
                notifyFinishedTyping(mName);
            }
        });


        CheckBox checkBox = view.findViewById(R.id.cbShowMap);
        checkBox.setChecked(TrackingViewsDatabaseManager.showMap(mViewId));
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                TrackingViewsDatabaseManager.updateShowMap(mViewId, isChecked);
            }
        });

        checkBox = view.findViewById(R.id.cbShowLapButton);
        checkBox.setChecked(TrackingViewsDatabaseManager.showLapButton(mViewId));
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                TrackingViewsDatabaseManager.updateShowLapButton(mViewId, isChecked);
            }
        });


        if (DEBUG) Log.i(TAG, "finished onCreateView");

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(TAG, "onResume, mViewId=" + mViewId);

        getActivity().setTitle(R.string.application_name);
        setHasOptionsMenu(true);

        getViewInfoMapAndAddSensorFields();

        mViewChangedFilter.addAction(FILTERS_CHANGED_INTENT);
        mViewChangedFilter.addAction(TRACKING_VIEW_CHANGED_INTENT);
        getContext().registerReceiver(mFilterChangedReceiver, mViewChangedFilter);
    }

    protected void getViewInfoMapAndAddSensorFields() {
        mViewInfoMap = TrackingViewsDatabaseManager.getViewInfoMap(mViewId);
        addSensorFields();
    }

    protected void addSensorFields() {
        if (DEBUG) Log.i(TAG, "addSensorFields, mViewId=" + mViewId);

        // first, remove everything
        mLLSensors.removeAllViews();


        // finally, update the views
        for (int rowNr : mViewInfoMap.keySet()) {
            if (DEBUG) Log.i(TAG, "adding rowNr=" + rowNr);
            addRow(mViewInfoMap.get(rowNr));
        }
    }

    @Override
    protected void updateNameOfView(String name) {
        TrackingViewsDatabaseManager.updateNameOfView(mViewId, name);
    }


    protected void addRow(final TreeMap<Integer, TrackingViewsDatabaseManager.ViewInfo> rowMap) {
        if (rowMap.size() == 0) {
            if (DEBUG) Log.i(TAG, "row contains no entries => returning");
            return;
        }


        final LinearLayout linearLayout = (LinearLayout) mLayoutInflater.inflate(R.layout.config_tracking_view_row, null);
        mLLSensors.addView(linearLayout);

        LinearLayout llFields = linearLayout.findViewById(R.id.llFields);

        for (final int colNr : rowMap.keySet()) {
            final TrackingViewsDatabaseManager.ViewInfo viewInfo = rowMap.get(colNr);
            if (DEBUG)
                Log.i(TAG, "add view: rowNr=" + viewInfo.rowNr + ", colNr=" + viewInfo.colNr + ", SensorType=" + viewInfo.sensorType + ", TextSize=" + viewInfo.textSize);

            final LinearLayout llView = (LinearLayout) mLayoutInflater.inflate(R.layout.config_tracking_view_entry_static, llFields, false);
            llFields.addView(llView);

            TextView tvSensor = llView.findViewById(R.id.tvSensor);
            tvSensor.setText(viewInfo.sensorType.toString());

            TextView tvSource = llView.findViewById(R.id.tvSource);
            if (viewInfo.sourceDeviceId <= 0) {
                tvSource.setText(R.string.bestSensor);
            } else {
                tvSource.setText(DevicesDatabaseManager.getDeviceName(viewInfo.sourceDeviceId));
            }

            TextView tvFilterTitle = llView.findViewById(R.id.tvConfigureFilterTitle);
            TextView tvFilterValue = llView.findViewById(R.id.tvConfigureFilterValue);
            if (viewInfo.sensorType.filteringPossible) {
                tvFilterTitle.setVisibility(View.VISIBLE);
                tvFilterValue.setVisibility(View.VISIBLE);

                tvFilterValue.setText(ConfigureFilterDialogFragment.getFilterSummary(getContext(), viewInfo.filterType, viewInfo.filterConstant));
            } else {
                tvFilterTitle.setVisibility(View.GONE);
                tvFilterValue.setVisibility(View.GONE);
            }

            TextView tvSize = llView.findViewById(R.id.tvTextSize);
            tvSize.setText(viewInfo.textSize + "");

            LinearLayout llSummary = llView.findViewById(R.id.llConfigSummary);
            llSummary.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showEditFieldDialog(viewInfo);
                }
            });

            Button deleteButton = llView.findViewById(R.id.buttonDelete);
            deleteButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (DEBUG) Log.i(TAG, "delete button pressed");
                    TrackingViewsDatabaseManager.deleteRow(viewInfo.rowId);

                    mViewInfoMap.get(viewInfo.rowNr).remove(viewInfo.colNr);
                    addSensorFields();
                }
            });
        }

        Button addView = linearLayout.findViewById(R.id.buttonAddSensorToRow);
        addView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (DEBUG) Log.i(TAG, "add view button clicked");

                TrackingViewsDatabaseManager.ViewInfo viewInfo = rowMap.get(rowMap.lastKey());
                viewInfo = TrackingViewsDatabaseManager.addSensorToRow(viewInfo.viewId, viewInfo.rowNr, SENSOR_TYPE_DEFAULT, TEXT_SIZE_DEFAULT);

                mViewInfoMap.get(viewInfo.rowNr).put(viewInfo.colNr, viewInfo);
                addSensorFields();
            }
        });

        Button addRow = linearLayout.findViewById(R.id.buttonAddNewRow);
        addRow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.i(TAG, "buttonAddNewRow clicked");
                TrackingViewsDatabaseManager.ViewInfo viewInfo = rowMap.get(rowMap.lastKey());
                viewInfo = TrackingViewsDatabaseManager.addRowAfter(viewInfo.viewId, viewInfo.rowNr, SENSOR_TYPE_DEFAULT, TEXT_SIZE_DEFAULT);

                getViewInfoMapAndAddSensorFields();

                showEditFieldDialog(viewInfo);
            }
        });

    }


    public void onPause() {
        super.onPause();
        if (DEBUG) Log.i(TAG, "onPause()");

        getActivity().sendBroadcast(new Intent(TRACKING_VIEW_CHANGED_INTENT));

        getContext().unregisterReceiver(mFilterChangedReceiver);
    }


    /**
     * Called first time user clicks on the menu button
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (DEBUG) Log.d(TAG, "onCreateOptionsMenu");

        inflater.inflate(R.menu.preview, menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (DEBUG) Log.i(TAG, "onOptionsItemSelected");

        switch (item.getItemId()) {
            case R.id.itemPreview:
                showPreview();

                return true;
        }

        return false;
    }

    private void showEditFieldDialog(TrackingViewsDatabaseManager.ViewInfo viewInfo) {
        EditFieldDialog editFieldDialog = EditFieldDialog.newInstance(mActivityType, viewInfo);
        editFieldDialog.show(getFragmentManager(), EditFieldDialog.TAG);
    }

    protected void showPreview() {
        setHasOptionsMenu(false);
        getActivity().setTitle(getString(R.string.preview_name_format, mName));

        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.content, TrackingFragment.newInstance(mViewId, TrackingFragment.Mode.PREVIEW, mActivityType));
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();
    }

}
