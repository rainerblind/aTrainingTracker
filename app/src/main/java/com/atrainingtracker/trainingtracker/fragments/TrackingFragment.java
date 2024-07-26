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

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.ActivityType;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;
import com.atrainingtracker.banalservice.filters.FilterData;
import com.atrainingtracker.banalservice.filters.FilterType;
import com.atrainingtracker.banalservice.filters.FilteredSensorData;
import com.atrainingtracker.trainingtracker.activities.ConfigTrackingViewsActivity;
import com.atrainingtracker.trainingtracker.activities.ConfigViewsActivity;
import com.atrainingtracker.trainingtracker.MyHelper;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager;
import com.atrainingtracker.trainingtracker.dialogs.EditFieldDialog;
import com.atrainingtracker.trainingtracker.fragments.mapFragments.TrackOnMapTrackingAndFollowingFragment;
import com.atrainingtracker.trainingtracker.fragments.mapFragments.TrackOnMapTrackingFragment;

import java.util.HashMap;
import java.util.TreeMap;

import static com.atrainingtracker.trainingtracker.dialogs.EditFieldDialog.TRACKING_VIEW_CHANGED_INTENT;

public class TrackingFragment extends BaseTrackingFragment {
    public static final String TAG = "TrackingFragment";
    public static final String VIEW_ID = "VIEW_ID";
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;
    private static final String MODE = "MODE";
    private static final String ACTIVITY_TYPE = "ACTIVITY_TYPE";
    private static final int TEXT_SIZE_TITLE = 15;
    private final IntentFilter mNewTimeEventFilter = new IntentFilter(BANALService.NEW_TIME_EVENT_INTENT);
    private final IntentFilter mTrackingViewChangedFilter = new IntentFilter(TRACKING_VIEW_CHANGED_INTENT);
    protected Mode mMode = Mode.TRACKING;

    // protected static final String BEST = "BEST_1353485234512395476534439475247";
    // protected EnumMap<SensorType, HashMap<String, String>> mSensorValueMap = new EnumMap<SensorType, HashMap<String, String>>(SensorType.class); // maps (SensorType, DeviceName) -> value

    // protected List<TvSensorType> mLTvSensorType;  // contains all the TvSensorTypes
    protected HashMap<String, TvSensorType> mHashMapTextViews = new HashMap<>(); // HashMap<String, TvSensorType>   for the TextViews and SensorType
    protected HashMap<String, String> mHashMapValues = new HashMap<>();     // HashMap<String, String>     for the values
    protected LinearLayout mLLSensors;
    protected FrameLayout mMapContainer;
    protected Button mButtonLap;
    protected long mViewId;
    protected ActivityType mActivityType;
    protected LayoutInflater mLayoutInflater;

    // protected String mUnitSpeed, mUnitPace, mUnitDistance;
    protected TrackOnMapTrackingFragment mTrackOnMapTrackingFragment = null;
    BroadcastReceiver mNewTimeEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            doDisplayUpdate();
        }
    };
    BroadcastReceiver mTrackingViewChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateSensorFields();
        }
    };

    public static TrackingFragment newInstance(long viewId, ActivityType activityType) {
        TrackingFragment trackingFragment = new TrackingFragment();

        Bundle args = new Bundle();
        args.putLong(VIEW_ID, viewId);
        args.putString(ACTIVITY_TYPE, activityType.name());
        trackingFragment.setArguments(args);

        return trackingFragment;
    }

    // public void setViewId(int viewId) 
    // {
    // mViewId = viewId;
    // 	if (mViewCreated) {
    //		updateSensorFields();
    //	}
    //}

    public static TrackingFragment newInstance(long viewId, Mode mode, ActivityType activityType) {
        if (DEBUG) Log.i(TAG, "newInstance viewId=" + viewId + ", mode=" + mode);

        TrackingFragment trackingFragment = new TrackingFragment();

        Bundle args = new Bundle();
        args.putLong(VIEW_ID, viewId);
        args.putString(MODE, mode.name());
        args.putString(ACTIVITY_TYPE, activityType.name());
        trackingFragment.setArguments(args);

        return trackingFragment;
    }

    public static String getShortFilterSummary(Context context, FilterType filterType, double filterConstant) {
        switch (filterType) {
            case INSTANTANEOUS: // instantaneous
                return context.getString(R.string.filter_instantaneous_short);

            case AVERAGE: // average (entire workout)
                return context.getString(R.string.filter_average_short) + " ";

            case MOVING_AVERAGE_TIME:
                if (filterConstant % 60 == 0) { // 5 min moving average
                    return (int) (filterConstant / 60) + " " + context.getString(R.string.units_minutes) + " " + context.getString(R.string.filter_moving_average_short) + " ";
                } else { // 5 sec moving average
                    return (int) filterConstant + " " + context.getString(R.string.units_seconds) + " " + context.getString(R.string.filter_moving_average_short) + " ";
                }

            case MOVING_AVERAGE_NUMBER: // 5 samples moving average
                return filterConstant + " " + context.getString(R.string.units_samples) + " " + context.getString(R.string.filter_moving_average_short) + " ";

            case EXPONENTIAL_SMOOTHING:  // exponential smoothing with \alpha = 0.9
                return context.getString(R.string.filter_exponential_smoothing_short_format, filterConstant) + " ";

            case MAX_VALUE:
                return context.getString(R.string.max) + " ";

            default:
                return "";
        }

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreate " + getArguments().getLong(VIEW_ID));

        mViewId = getArguments().getLong(VIEW_ID);
        mActivityType = ActivityType.valueOf(getArguments().getString(ACTIVITY_TYPE));

        String mode = getArguments().getString(MODE);
        if (DEBUG) Log.i(TAG, "mode=" + mode);
        if (mode != null) {
            mMode = Mode.valueOf(mode);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreateView " + mViewId);

        if (DEBUG) Log.i(TAG, "mode=" + mMode.name());
        if (mMode == Mode.TRACKING) {
            setHasOptionsMenu(true);
        } else if (mMode == Mode.PREVIEW) {
            setHasOptionsMenu(false);
        }

        mLayoutInflater = inflater;

        View view = inflater.inflate(R.layout.tracking_fragment, container, false);

        mButtonLap = view.findViewById(R.id.buttonLap);

        if (mButtonLap != null) {
            // Log.d(TAG, "setting onClickListener to Lap Button");

            mButtonLap.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    getActivity().sendBroadcast(new Intent(TrainingApplication.REQUEST_NEW_LAP)
                            .setPackage(getActivity().getPackageName()));
                }
            });
        } else {
            if (DEBUG) {
                Log.d(TAG, "!!! could not find Lap Button !!!");
            }
        }

        mLLSensors = view.findViewById(R.id.llSensors);
        mLLSensors.removeAllViews();
        updateSensorFields();  // initialize with the default stuff

        mMapContainer = view.findViewById(R.id.map_container);
        // optionally show a map with the track
        if (mTrackOnMapTrackingFragment == null && TrackingViewsDatabaseManager.showMap(mViewId)) {
            mTrackOnMapTrackingFragment = TrackOnMapTrackingAndFollowingFragment.newInstance();
            getChildFragmentManager().beginTransaction().add(mMapContainer.getId(), mTrackOnMapTrackingFragment).commit();
        }

        return view;
    }

    @Override
    public void onActivityCreated(Bundle bundle) {
        super.onActivityCreated(bundle);
        if (DEBUG) Log.i(TAG, "onActivityCreated " + mViewId);

        ContextCompat.registerReceiver(getActivity(), mNewTimeEventReceiver, mNewTimeEventFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(getActivity(), mTrackingViewChangedReceiver, mTrackingViewChangedFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onStart () {
        super.onStart();
        if (DEBUG) Log.i(TAG, "onStart " + mViewId);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(TAG, "onResume " + mViewId);

        // optionally enable fullscreen mode
        if (TrackingViewsDatabaseManager.fullscreen(mViewId)) {
            hideSystemUI();
        }
        else {
            showSystemUI();
        }

        // optionally force day or night...
        if (TrackingViewsDatabaseManager.day(mViewId))   { forceDay();   }
        if (TrackingViewsDatabaseManager.night(mViewId)) { forceNight(); }
        if (TrackingViewsDatabaseManager.systemSettings(mViewId)) { followSystem(); }
    }


    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.i(TAG, "onPause " + mViewId);

        // try {
        //     getActivity().unregisterReceiver(mNewTimeEventReceiver);
        // } catch (Exception e) {
        // }
        // try {
        //     getActivity().unregisterReceiver(mTrackingViewChangedReceiver);
        // } catch (Exception e) {
        // }
    }

    @Override
    public void onStop () {
        super.onStop();
        if (DEBUG) Log.i(TAG, "onStop " + mViewId);
    }

    @Override
    public void onDestroyView () {
        super.onDestroyView();
        if (DEBUG) Log.i(TAG, "onDestroyView " + mViewId);

        getActivity().unregisterReceiver(mNewTimeEventReceiver);
        getActivity().unregisterReceiver(mTrackingViewChangedReceiver);
    }

    @Override
    public void onDestroy () {
        super.onDestroy();
        if (DEBUG) Log.i(TAG, "onDestroy " + mViewId);
    }

    @Override
    public void onDetach () {
        super.onDetach();
        if (DEBUG) Log.i(TAG, "onDetach " + mViewId);
    }

    /**
     * Called first time user clicks on the menu button
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (DEBUG) Log.d(TAG, "onCreateOptionsMenu");

        inflater.inflate(R.menu.tracking_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (DEBUG) Log.i(TAG, "onOptionsItemSelected");

        // Log.d(TAG, "onOptionsItemSelected");
        switch (item.getItemId()) {
            case R.id.itemEditTrackingView:
                if (DEBUG) Log.i(TAG, "edit trackingView for viewId=" + mViewId);

                Bundle bundle = new Bundle();
                bundle.putLong(ConfigViewsActivity.VIEW_ID, mViewId);
                Intent intent = new Intent(getActivity(), ConfigTrackingViewsActivity.class);
                intent.putExtras(bundle);
                startActivity(intent);

                return true;
        }

        return false;
    }

    public void updateSensorFields() {
        if (DEBUG) Log.d(TAG, "updateSensorFields for " + mViewId);

        TreeMap<Integer, TreeMap<Integer, TrackingViewsDatabaseManager.ViewInfo>> viewInfoMap = TrackingViewsDatabaseManager.getViewInfoMap(mViewId);

        mLLSensors.removeAllViews();
        mHashMapTextViews = new HashMap<>();

        for (int rowNr : viewInfoMap.keySet()) {
            if (DEBUG) Log.i(TAG, "adding rowNr=" + rowNr);
            addRow(viewInfoMap.get(rowNr));
        }


        if (TrackingViewsDatabaseManager.showLapButton(mViewId)) {
            mButtonLap.setVisibility(View.VISIBLE);
        } else {
            mButtonLap.setVisibility(View.GONE);
        }

        // finally, also fill with new values
        doDisplayUpdate();
    }

    protected void addRow(final TreeMap<Integer, TrackingViewsDatabaseManager.ViewInfo> rowMap) {
        if (rowMap.size() == 0) {
            if (DEBUG) Log.i(TAG, "row contains no entries => returning");
            return;
        }

        LinearLayout llRow = new LinearLayout(getContext());
        llRow.setOrientation(LinearLayout.HORIZONTAL);
        llRow.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        mLLSensors.addView(llRow);

        for (int colNr : rowMap.keySet()) {
            final TrackingViewsDatabaseManager.ViewInfo viewInfo = rowMap.get(colNr);
            if (DEBUG)
                Log.i(TAG, "add view: rowNr=" + viewInfo.rowNr + ", colNr=" + viewInfo.colNr + ", SensorType=" + viewInfo.sensorType + ", TextSize=" + viewInfo.textSize);

            LinearLayout llField = (LinearLayout) mLayoutInflater.inflate(R.layout.sensor_field, llRow, false);
            llRow.addView(llField);

            if (mMode != Mode.PREVIEW) {
                llField.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        showEditFieldDialog(viewInfo);
                        return true;
                    }
                });
            }

            SensorType sensorType = viewInfo.sensorType;
            long deviceId = viewInfo.sourceDeviceId;
            String deviceName = null;
            if (deviceId > 0) {
                deviceName = DevicesDatabaseManager.getDeviceName(deviceId);
            }
            if (DEBUG) {
                Log.d(TAG, "creating view for sensor: " + sensorType.name() + " (" + deviceName + ")" + " deviceId=" + deviceId);
            }

            // TextView for the title/description
            TextView tv = new TextView(getContext());
            // tv.setBackgroundColor(getResources().getColor(R.color.my_white));
            tv.setTextSize(TEXT_SIZE_TITLE);
            String filterSummary = getShortFilterSummary(getContext(), viewInfo.filterType, viewInfo.filterConstant);
            if (deviceName == null) {
                tv.setText(filterSummary + getString(sensorType.getFullNameId()) + ":");
            } else {
                tv.setText(filterSummary + getString(R.string.format_sensorType_and_DeviceName, getString(sensorType.getFullNameId()), deviceName));
            }
            // TODO: add filter description like 5 min avg...

            llField.addView(tv);

            // TextView for the content
            tv = new TextView(getContext());
            // tv.setBackgroundColor(getResources().getColor(R.color.my_white));
            tv.setTextSize(viewInfo.textSize);
            // tv.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
            tv.setGravity(Gravity.CENTER_HORIZONTAL);
            llField.addView(tv);

            // finally, add this TextView to the Map
            mHashMapTextViews.put((new FilterData(deviceName, sensorType, viewInfo.filterType, viewInfo.filterConstant)).getHashKey(),
                    new TvSensorType(tv, sensorType));
        }
    }

    private void showEditFieldDialog(TrackingViewsDatabaseManager.ViewInfo viewInfo) {
        EditFieldDialog editFieldDialog = EditFieldDialog.newInstance(mActivityType, viewInfo);
        editFieldDialog.show(getFragmentManager(), EditFieldDialog.TAG);
    }


    public void doDisplayUpdate() {
        if (DEBUG) Log.d(TAG, "doDisplayUpdate for " + mViewId);

        if (mMode == Mode.PREVIEW || getSensorData()) {

            for (String hashKey : mHashMapTextViews.keySet()) {

                TvSensorType tvSensorType = mHashMapTextViews.get(hashKey);

                // get the value
                String value = null;
                if (mHashMapValues.containsKey(hashKey)) {
                    value = mHashMapValues.get(hashKey);
                }

                // check if there was a valid value
                if (value == null) {
                    // if (DEBUG) Log.d(TAG, "value==null for " + getString(sensorType.getShortNameId()));
                    if (getActivity() != null) {
                        value = getString(R.string.NoData);
                        if (DEBUG) Log.i(TAG, ":-( no valid value for " + hashKey);
                    }
                    else {
                        value = "--";
                        Log.i(TAG, "WTF: no value for " + hashKey + " but no Activity");
                    }
                } else {
                    // remove/delete the current value (necessary when a sensor is removed)
                    mHashMapValues.put(hashKey, null);
                }
                if (DEBUG) Log.i(TAG, "displayUpdate for " + hashKey + ": " + value);

                // now, display it
                if (TrainingApplication.showUnits() && getActivity() != null) {
                    String units = getString(MyHelper.getUnitsId(tvSensorType.sensorType));
                    tvSensorType.textView.setText(getString(R.string.value_unit_string_string, value, units));
                } else {
                    tvSensorType.textView.setText(value);
                }
            }
        }
    }


    protected boolean getSensorData() {
        if (DEBUG) Log.d(TAG, "getSensorData for " + mViewId);

        if (mGetBanalServiceIf != null & mGetBanalServiceIf.getBanalServiceComm() != null) {
            // for (SensorData sensorData : mGetBanalServiceIf.getBanalServiceComm().getBestSensorData()) {
            for (FilteredSensorData filteredSensorData : mGetBanalServiceIf.getBanalServiceComm().getAllFilteredSensorData()) {
                if (DEBUG) {
                    Log.d(TAG, "getSensorData for " + filteredSensorData.getFilterData().getHashKey() + ": " + filteredSensorData.getStringValue());
                }

                mHashMapValues.put(filteredSensorData.getFilterData().getHashKey(), filteredSensorData.getStringValue());
            }
            return true;
        } else {
            if (DEBUG) Log.i(TAG, "no Connection to BANALService");
            return false;
        }
    }


    public enum Mode {TRACKING, PREVIEW}

    protected class TvSensorType {
        protected TextView textView;
        protected SensorType sensorType;

        public TvSensorType(TextView textView, SensorType sensorType) {
            this.textView = textView;
            this.sensorType = sensorType;
        }
    }
}