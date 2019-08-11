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
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
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

public class ControlTrackingFragment extends Fragment {
    public static final String TAG = ControlTrackingFragment.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG & false;
    private final IntentFilter mStartTrackingFilter = new IntentFilter();
    protected RemoteDevicesSettingsInterface mRemoteDevicesSettingsInterface;
    protected StartOrResumeInterface mStartOrResumeInterface;
    protected BANALService.GetBanalServiceInterface mGetBanalServiceIf;
    protected BroadcastReceiver mUpdateResearchReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateResearchButton();
        }
    };
    private ImageButton mANTPairingButton;
    private ImageButton mBluetoothPairingButton;
    private ImageButton mStartButton, mPauseButton, mResumeFromPauseButton, mStopButton, mResearchButton;
    private LinearLayout mStartLayout, mPauseLayout, mResumeAndStopLayout, mResearchLayout;
    protected BroadcastReceiver mStartTrackingReceiver = new BroadcastReceiver() {
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
    protected BroadcastReceiver mPauseTrackingReceiver = new BroadcastReceiver() {
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
    protected BroadcastReceiver mStopTrackingReceiver = new BroadcastReceiver() {
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
    private IntentFilter mUpdateResearchFilter = new IntentFilter();  // Intents will be added in onResume

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (DEBUG) Log.d(TAG, "onAttach");

        try {
            mRemoteDevicesSettingsInterface = (RemoteDevicesSettingsInterface) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement StartPairingListener");
        }

        try {
            mStartOrResumeInterface = (StartOrResumeInterface) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement StartOrResumeInterface");
        }

        try {
            mGetBanalServiceIf = (BANALService.GetBanalServiceInterface) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement GetBanalServiceInterface");
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
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // BroadcastReceivers
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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
                getContext().sendBroadcast(new Intent(TrainingApplication.REQUEST_START_SEARCH_FOR_PAIRED_DEVICES));
            }
        });

        mStartButton = view.findViewById(R.id.imageButtonStart);
        mStartButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (DEBUG) Log.i(TAG, "start button clicked");
                // mControlTrackingListener.startTracking();
                getContext().sendBroadcast(new Intent(TrainingApplication.REQUEST_START_TRACKING));
            }
        });

        mPauseButton = view.findViewById(R.id.imageButtonPause);
        mPauseButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (DEBUG) Log.i(TAG, "start button clicked");
                // mControlTrackingListener.pauseTracking();
                getContext().sendBroadcast(new Intent(TrainingApplication.REQUEST_PAUSE_TRACKING));
            }
        });

        mResumeFromPauseButton = view.findViewById(R.id.imageButtonResume);
        mResumeFromPauseButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (DEBUG) Log.i(TAG, "resume button clicked");
                // mControlTrackingListener.resumeTracking();
                getContext().sendBroadcast(new Intent(TrainingApplication.REQUEST_RESUME_FROM_PAUSED));
            }
        });

        mStopButton = view.findViewById(R.id.imageButtonStop);
        mStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DEBUG) Log.i(TAG, "stop button clicked");
                // mControlTrackingListener.stopTracking();
                getContext().sendBroadcast(new Intent(TrainingApplication.REQUEST_STOP_TRACKING));

            }
        });


        // now, the ANT+ and Bluetooth buttons

        mANTPairingButton = view.findViewById(R.id.imageButtonANTPairing);
        if (mANTPairingButton != null) {
            mANTPairingButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mRemoteDevicesSettingsInterface.startPairing(Protocol.ANT_PLUS);
                }
            });
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
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(TAG, "onResume");


        mUpdateResearchFilter.addAction(BANALService.SEARCHING_STARTED_FOR_ALL_INTENT);
        mUpdateResearchFilter.addAction(BANALService.SEARCHING_FINISHED_FOR_ALL_INTENT);
        getActivity().registerReceiver(mUpdateResearchReceiver, mUpdateResearchFilter);

        mStartTrackingFilter.addAction(TrainingApplication.REQUEST_START_TRACKING);
        mStartTrackingFilter.addAction(TrainingApplication.REQUEST_RESUME_FROM_PAUSED);

        getActivity().registerReceiver(mStartTrackingReceiver, mStartTrackingFilter);
        getActivity().registerReceiver(mPauseTrackingReceiver, new IntentFilter(TrainingApplication.REQUEST_PAUSE_TRACKING));
        getActivity().registerReceiver(mStopTrackingReceiver, new IntentFilter(TrainingApplication.REQUEST_STOP_TRACKING));

        updateResearchButton();
        updatePairing();
        showTrackingState();
    }

    @Override
    public void onPause() {
        super.onPause();

        // try { getActivity().unregisterReceiver(mUpdatePairingReceiver);    } catch (Exception e) { }
        try {
            getActivity().unregisterReceiver(mStartTrackingReceiver);
        } catch (Exception e) {
        }
        try {
            getActivity().unregisterReceiver(mPauseTrackingReceiver);
        } catch (Exception e) {
        }
        try {
            getActivity().unregisterReceiver(mStopTrackingReceiver);
        } catch (Exception e) {
        }
        try {
            getActivity().unregisterReceiver(mUpdateResearchReceiver);
        } catch (Exception e) {
        }

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
        if (mGetBanalServiceIf == null | mGetBanalServiceIf.getBanalServiceComm() == null) {
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
