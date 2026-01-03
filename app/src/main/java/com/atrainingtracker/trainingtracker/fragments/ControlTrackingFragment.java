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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Protocol;
import com.atrainingtracker.banalservice.fragments.ControlSportTypeFragment;
import com.atrainingtracker.banalservice.fragments.TrackingModeFragment;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.interfaces.RemoteDevicesSettingsInterface;
import com.atrainingtracker.trainingtracker.interfaces.StartOrResumeInterface;

public class ControlTrackingFragment extends BaseTrackingFragment {
    public static final String TAG = ControlTrackingFragment.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);
    private final IntentFilter mStartTrackingFilter = new IntentFilter();
    protected RemoteDevicesSettingsInterface mRemoteDevicesSettingsInterface;
    protected StartOrResumeInterface mStartOrResumeInterface;
    protected final BroadcastReceiver mUpdateResearchReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateResearchButton();
        }
    };
    private ImageButton mANTPairingButton;
    private ImageButton mBluetoothPairingButton;
    private ImageButton mStartButton, mPauseButton, mResumeFromPauseButton, mStopButton, mResearchButton;
    private LinearLayout mStartLayout, mPauseLayout, mResumeAndStopLayout, mResearchLayout;


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // BroadcastReceivers
    ////////////////////////////////////////////////////////////////////////////////////////////////
    protected final BroadcastReceiver mStartTrackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // showStartLayout();
            showPauseLayout();
            // showResumeAndStopLayout();

            disableStartLayout();
            // disablePauseLayout();
            disableResumeAndStopLayout();
        }
    };
    protected final BroadcastReceiver mPauseTrackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // showStartLayout();
            // showPauseLayout();
            showResumeAndStopLayout();

            disableStartLayout();
            disablePauseLayout();
            // disableResumeAndStopLayout();
        }
    };
    protected final BroadcastReceiver mStopTrackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showStartLayout();
            // showPauseLayout();
            // showResumeAndStopLayout();

            // disableStartLayout();
            disablePauseLayout();
            disableResumeAndStopLayout();
        }
    };
    private final IntentFilter mUpdateResearchFilter = new IntentFilter();  // Intents will be added in onResume


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle methods
    ////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (DEBUG) Log.d(TAG, "onAttach");

        try {
            mRemoteDevicesSettingsInterface = (RemoteDevicesSettingsInterface) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement StartPairingListener");
        }

        try {
            mStartOrResumeInterface = (StartOrResumeInterface) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement StartOrResumeInterface");
        }

        mGetBanalServiceIf.registerConnectionStatusListener(new BANALService.GetBanalServiceInterface.ConnectionStatusListener() {
            @Override
            public void connectedToBanalService() {
                updatePairing();
            }

            @Override
            public void disconnectedFromBanalService() {
                updatePairing();
            }
        });


        mUpdateResearchFilter.addAction(BANALService.SEARCHING_STARTED_FOR_ALL_INTENT);
        mUpdateResearchFilter.addAction(BANALService.SEARCHING_FINISHED_FOR_ALL_INTENT);

        mStartTrackingFilter.addAction(TrainingApplication.REQUEST_START_TRACKING);
        mStartTrackingFilter.addAction(TrainingApplication.REQUEST_RESUME_FROM_PAUSED);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreate");
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreateView");

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.control_tracking_layout, container, false);


        // add the fragments for showing the tracking mode as well as the fragment to change the sport type

        FragmentManager fragmentManager = getFragmentManager();

        TrackingModeFragment trackingModeFragment = new TrackingModeFragment();
        FragmentTransaction ft = fragmentManager.beginTransaction();
        ft.replace(R.id.tracking_mode_container, trackingModeFragment);
        ft.commitAllowingStateLoss();

        ControlSportTypeFragment controlSportTypeFragment = new ControlSportTypeFragment();
        ft = fragmentManager.beginTransaction();
        ft.replace(R.id.ctl_control_sport_type_container, controlSportTypeFragment);
        ft.commitAllowingStateLoss();


        // set the handlers for the main tracking control buttons (start, pause, resume, stop)

        mStartLayout = view.findViewById(R.id.llStart);
        mPauseLayout = view.findViewById(R.id.llPause);
        mResumeAndStopLayout = view.findViewById(R.id.llResumeAndStop);

        mResearchLayout = view.findViewById(R.id.llResearch);
        mResearchButton = view.findViewById(R.id.ibResearch);
        mResearchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getContext().sendBroadcast(new Intent(TrainingApplication.REQUEST_START_SEARCH_FOR_PAIRED_DEVICES)
                        .setPackage(getContext().getPackageName()));
            }
        });

        mStartButton = view.findViewById(R.id.imageButtonStart);
        mStartButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (DEBUG) Log.i(TAG, "start button clicked");
                // mControlTrackingListener.startTracking();
                getContext().sendBroadcast(new Intent(TrainingApplication.REQUEST_START_TRACKING)
                        .setPackage(getContext().getPackageName()));
            }
        });

        mPauseButton = view.findViewById(R.id.imageButtonPause);
        mPauseButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (DEBUG) Log.i(TAG, "start button clicked");
                // mControlTrackingListener.pauseTracking();
                getContext().sendBroadcast(new Intent(TrainingApplication.REQUEST_PAUSE_TRACKING)
                        .setPackage(getContext().getPackageName()));
            }
        });

        mResumeFromPauseButton = view.findViewById(R.id.imageButtonResume);
        mResumeFromPauseButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (DEBUG) Log.i(TAG, "resume button clicked");
                // mControlTrackingListener.resumeTracking();
                getContext().sendBroadcast(new Intent(TrainingApplication.REQUEST_RESUME_FROM_PAUSED)
                        .setPackage(getContext().getPackageName()));
            }
        });

        mStopButton = view.findViewById(R.id.imageButtonStop);
        mStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DEBUG) Log.i(TAG, "stop button clicked");
                // mControlTrackingListener.stopTracking();
                getContext().sendBroadcast(new Intent(TrainingApplication.REQUEST_STOP_TRACKING)
                        .setPackage(getContext().getPackageName()));

            }
        });


        // now, the ANT+ and Bluetooth buttons

        mANTPairingButton = view.findViewById(R.id.imageButtonANTPairing);
        if (mANTPairingButton != null) {
            if (BANALService.isANTProperlyInstalled(getContext())) {
                mANTPairingButton.setVisibility(View.GONE);
                View text = view.findViewById(R.id.textButtonANTPairing);
                text.setVisibility(View.GONE);
            } else {
                mANTPairingButton.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        mRemoteDevicesSettingsInterface.startPairing(Protocol.ANT_PLUS);
                    }
                });
            }
        } else {
            if (DEBUG) Log.d(TAG, "WTF, could not find the ANT+ pairing button");
        }

        mBluetoothPairingButton = view.findViewById(R.id.imageButtonBluetoothPairing);
        // this button will be configured within onActivityCreated()

        // finally, return the view
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onActivityCreated");

        if (mBluetoothPairingButton != null) {
            if (BANALService.isProtocolSupported(getActivity(), Protocol.BLUETOOTH_LE)) {
                mBluetoothPairingButton.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        BluetoothManager bluetoothManager = (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
                        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

                        if (bluetoothAdapter.isEnabled()) {
                            mRemoteDevicesSettingsInterface.startPairing(Protocol.BLUETOOTH_LE);
                        } else {
                            mRemoteDevicesSettingsInterface.enableBluetoothRequest();
                            // Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                            // // enableBtIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            // // getContext().startActivity(enableBtIntent);
                            // getActivity().startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_INTENT); TODO: do this via the mainActivity
                        }
                    }
                });
            } else {
                View view = getActivity().findViewById(R.id.llBluetoothPairing);
                if (view != null) {
                    view.setVisibility(View.INVISIBLE);
                }
            }
        } else {
            if (DEBUG) Log.d(TAG, "WTF, could not find the Bluetooth pairing button");
        }

        if (!prevTrackingFinishedProperly()) {
            mStartOrResumeInterface.showStartOrResumeDialog();
        }

        ContextCompat.registerReceiver(getActivity(), mUpdateResearchReceiver, mUpdateResearchFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(getActivity(), mStartTrackingReceiver, mStartTrackingFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(getActivity(), mPauseTrackingReceiver, new IntentFilter(TrainingApplication.REQUEST_PAUSE_TRACKING), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(getActivity(), mStopTrackingReceiver, new IntentFilter(TrainingApplication.REQUEST_STOP_TRACKING), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onStart () {
        super.onStart();
        if (DEBUG) Log.i(TAG, "onStart");

        updateResearchButton();
        updatePairing();
        showTrackingState();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(TAG, "onResume");

        showSystemUI();
        followSystem();
    }

    // Fragment is active

    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.i(TAG, "onPause");

    }

    @Override
    public void onStop () {
        super.onStop();
        if (DEBUG) Log.i(TAG, "onStop");
    }

    @Override
    public void onDestroyView () {
        super.onDestroyView();
        if (DEBUG) Log.i(TAG, "onDestroyView");

        getActivity().unregisterReceiver(mStartTrackingReceiver);
        getActivity().unregisterReceiver(mPauseTrackingReceiver);
        getActivity().unregisterReceiver(mStopTrackingReceiver);
        getActivity().unregisterReceiver(mUpdateResearchReceiver);
    }

    @Override
    public void onDestroy () {
        super.onDestroy();
        if (DEBUG) Log.i(TAG, "onDestroy");
    }

    @Override
    public void onDetach () {
        super.onDetach();
        if (DEBUG) Log.i(TAG, "onDetach");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // helper methods
    ////////////////////////////////////////////////////////////////////////////////////////////////
    protected void updateResearchButton() {
        if (BANALService.isSearching()) {
            mResearchLayout.setVisibility(View.GONE);
            mResearchButton.setEnabled(false);
        } else {
            mResearchLayout.setVisibility(View.VISIBLE);
            mResearchButton.setEnabled(true);
        }
    }

    protected void showTrackingState() {
        switch (TrainingApplication.getTrackingMode()) {
            case READY:
                showStartLayout();
                // showPauseLayout();
                // showResumeAndStopLayout();

                // disableStartLayout();
                disablePauseLayout();
                disableResumeAndStopLayout();
                break;

            case TRACKING:
                // showStartLayout();
                showPauseLayout();
                // showResumeAndStopLayout();

                disableStartLayout();
                // disablePauseLayout();
                disableResumeAndStopLayout();
                break;

            case PAUSED:
                // showStartLayout();
                // showPauseLayout();
                showResumeAndStopLayout();

                disableStartLayout();
                disablePauseLayout();
                // disableResumeAndStopLayout();
                break;

        }
    }


    // show and enable Start Button
    protected void showStartLayout() {
        if (DEBUG) Log.i(TAG, "showStartLayout");

        mStartLayout.setVisibility(View.VISIBLE);
        mStartButton.setClickable(true);
        mStartButton.setEnabled(true);

    }

    // remove and disable Start Buttons
    protected void disableStartLayout() {
        mStartLayout.setVisibility(View.INVISIBLE);
        mStartButton.setClickable(false);
        mStartButton.setEnabled(false);
    }


    // show and enable Pause Buttons
    protected void showPauseLayout() {
        if (DEBUG) Log.i(TAG, "showPauseLayout");

        mPauseLayout.setVisibility(View.VISIBLE);
        mPauseButton.setClickable(true);
        mPauseButton.setEnabled(true);

    }

    // remove and disable Pause Buttons
    protected void disablePauseLayout() {
        mPauseLayout.setVisibility(View.INVISIBLE);
        mPauseButton.setClickable(false);
        mPauseButton.setEnabled(false);
    }


    // show and enable the Resume and Stop
    protected void showResumeAndStopLayout() {
        if (DEBUG) Log.i(TAG, "showResumeAndStopLayout");

        mResumeAndStopLayout.setVisibility(View.VISIBLE);
        mResumeFromPauseButton.setClickable(true);
        mResumeFromPauseButton.setEnabled(true);
        mStopButton.setClickable(true);
        mStopButton.setEnabled(true);
    }

    // remove and disable the Resume and Stop
    protected void disableResumeAndStopLayout() {
        mResumeAndStopLayout.setVisibility(View.INVISIBLE);
        mResumeFromPauseButton.setClickable(false);
        mResumeFromPauseButton.setEnabled(false);
        mStopButton.setClickable(false);
        mStopButton.setEnabled(false);
    }


    protected void updatePairing() {
        if (mGetBanalServiceIf == null || mGetBanalServiceIf.getBanalServiceComm() == null) {
            disablePairing();
        } else {
            enablePairing();
        }
    }

    protected void enablePairing() {
        if (mANTPairingButton != null) {
            mANTPairingButton.setClickable(true);
            mANTPairingButton.setEnabled(true);
        }

        if (mBluetoothPairingButton != null) {
            mBluetoothPairingButton.setClickable(true);
            mBluetoothPairingButton.setEnabled(true);
        }
    }

    protected void disablePairing() {
        if (mANTPairingButton != null) {
            mANTPairingButton.setClickable(false);
            mANTPairingButton.setEnabled(false);
        }

        if (mBluetoothPairingButton != null) {
            mBluetoothPairingButton.setClickable(false);
            mBluetoothPairingButton.setEnabled(false);
        }
    }


    protected boolean prevTrackingFinishedProperly() {
        if (TrainingApplication.isTracking()) {
            return true;
        }

        boolean finishedProperly = true;

        WorkoutSummariesDatabaseManager databaseManager = WorkoutSummariesDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = db.query(WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE, null, null, null, null, null, null);
        if (cursor.getCount() > 0) {
            cursor.moveToLast();
            int index = cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.FINISHED);
            finishedProperly = cursor.getInt(index) >= 1;
        }
        cursor.close();
        databaseManager.closeDatabase(); // db.close();

        return finishedProperly;

    }
}
