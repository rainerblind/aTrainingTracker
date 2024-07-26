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

package com.atrainingtracker.banalservice.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.devices.MyRemoteDevice;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.banalservice.helpers.UIHelper;
import com.atrainingtracker.trainingtracker.TrackingMode;
import com.atrainingtracker.trainingtracker.TrainingApplication;


public class TrackingModeFragment extends Fragment {
    private static final String TAG = TrackingModeFragment.class.getName();
    private static final boolean DEBUG = BANALService.getDebug(false);

    private LinearLayout mLLSensors;
    private ProgressBar mPBSearching;
    private TextView mTVTitle, mTVSubTitle, mTVSensors;


    private boolean mViewCreated = false;

    private BANALService.GetBanalServiceInterface mGetBanalServiceInterface;
    BroadcastReceiver mUpdateViewReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateView(context);
        }
    };
    private final IntentFilter mUpdateViewFilter = new IntentFilter();  // Intents will be added in onResume

    // onCreate()

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        if (DEBUG) Log.i(TAG, "onAttach");

        try {
            mGetBanalServiceInterface = (BANALService.GetBanalServiceInterface) context;
            mGetBanalServiceInterface.registerConnectionStatusListener(new BANALService.GetBanalServiceInterface.ConnectionStatusListener() {
                @Override
                public void connectedToBanalService() {
                    updateView(context);
                }

                @Override
                public void disconnectedFromBanalService() {
                    updateView(context);
                }
            });
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement GetBanalServiceInterface");
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreateView");

        View view = inflater.inflate(R.layout.tracking_mode, container, false);

        mLLSensors = view.findViewById(R.id.llSensors);
        mPBSearching = view.findViewById(R.id.pbSearching);
        mTVTitle = view.findViewById(R.id.tvTitle);
        mTVSubTitle = view.findViewById(R.id.tvSubTitle);
        mTVSensors = view.findViewById(R.id.tvSensors);


        mViewCreated = true;

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(TAG, "onResume");

        updateView(getContext());

        mUpdateViewFilter.addAction(BANALService.SENSORS_CHANGED);
        mUpdateViewFilter.addAction(BANALService.SEARCHING_FINISHED_FOR_ALL_INTENT);
        mUpdateViewFilter.addAction(BANALService.SEARCHING_STARTED_FOR_ONE_INTENT);
        mUpdateViewFilter.addAction(BANALService.SPORT_TYPE_CHANGED_BY_USER_INTENT);
        mUpdateViewFilter.addAction(TrainingApplication.TRACKING_STATE_CHANGED);
        ContextCompat.registerReceiver(getActivity(), mUpdateViewReceiver, mUpdateViewFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.i(TAG, "onPause");

        getActivity().unregisterReceiver(mUpdateViewReceiver);
    }

    public void updateView(Context context) {
        if (DEBUG) Log.d(TAG, "updateView");

        if (!mViewCreated | !isAdded()) {
            return;
        }

        TrackingMode trackingMode = TrainingApplication.getTrackingMode();
        BANALServiceComm banalServiceComm = mGetBanalServiceInterface.getBanalServiceComm();

        // get a meaningful name for the sensorString and sportId
        String sensorString;
        long sportTypeId;
        if (banalServiceComm == null) {
            trackingMode = TrackingMode.WAITING_FOR_BANAL_SERVICE;
            sensorString = "---------";
            sportTypeId = TrackingMode.WAITING_FOR_BANAL_SERVICE.getSportId(BSportType.UNKNOWN);
        } else {
            sensorString = banalServiceComm.getGCDataString();
            sportTypeId = banalServiceComm.getSportTypeId();
        }

        if (BANALService.isSearching()) {
            trackingMode = TrackingMode.SEARCHING;
        }

        // now, set title, subtitle and sensorString
        mTVTitle.setText(trackingMode.getTitleId());
        mTVSubTitle.setText(SportTypeDatabaseManager.getUIName(sportTypeId));
        mTVSensors.setText(sensorString);

        // update the list of available sensors
        mLLSensors.removeAllViews();
        if (banalServiceComm != null) {
            float scale = getResources().getDisplayMetrics().density;
            int padding = (int) (2 * scale + 0.5f);
            for (MyRemoteDevice myRemoteDevice : banalServiceComm.getActiveRemoteDevices()) {
                ImageView imageView = new ImageView(context);
                imageView.setPadding(padding, padding, padding, padding);
                imageView.setImageResource(UIHelper.getIconId(myRemoteDevice.getDeviceType(), myRemoteDevice.getProtocol()));
                mLLSensors.addView(imageView);
            }
        }

        // show the Searching View depending on the TrackingMode
        switch (trackingMode) {
            case WAITING_FOR_BANAL_SERVICE:
            case SEARCHING:
                mPBSearching.setVisibility(View.VISIBLE);
                break;

            case READY:
            case TRACKING:
            case PAUSED:
                mPBSearching.setVisibility(View.GONE);
                break;
        }

        // when searching, we display the name of the device we are currently searching for
        if (banalServiceComm != null & BANALService.isSearching()) {
            mTVSubTitle.setText(banalServiceComm.getNameOfSearchingDevice());
        }
    }

}
