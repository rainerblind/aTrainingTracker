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

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.Protocol;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager.DevicesDbHelper;
import com.atrainingtracker.trainingtracker.MyHelper;
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public abstract class RemoteDevicesFragment extends Fragment {
    private static final String TAG = "RemoteDevicesFragment";
    private static final boolean DEBUG = BANALService.getDebug(false);
    protected DeviceType mDeviceType;
    protected Protocol mProtocol;
    protected ListView lvDevices;


    // protected BANALServiceComm mBanalServiceComm;
    protected SQLiteDatabase mRemoteDevicesDb;
    protected Cursor mRemoteDevicesCursor;
    protected DeviceListCursorAdapter mRemoteDevicesAdapter;
    protected IntentFilter mPairingChangedFilter = new IntentFilter(BANALService.PAIRING_CHANGED);
    // callback interfaces
    OnRemoteDeviceSelectedListener mOnRemoteDeviceSelectedListener;
    BANALService.GetBanalServiceInterface mGetBanalServiceInterface;
    BroadcastReceiver mPairingChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.i(TAG, "received Pairing Changed Intent");
            updateView();
        }
    };
    private Timer timer = null;
    private ProgressDialog mProgressDialog;
    private Map<Long, String> mDeviceId2Units;

    abstract protected Cursor getCursor();

    abstract protected int getLayoutId();


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // lifecycle methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAttach(Context context) {
        if (DEBUG) Log.i(TAG, "onAttach");
        super.onAttach(context);

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception.
        try {
            mOnRemoteDeviceSelectedListener = (OnRemoteDeviceSelectedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context
                    + " must implement OnRemoteDeviceSelectedListener");
        }

        try {
            mGetBanalServiceInterface = (BANALService.GetBanalServiceInterface) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement GetBanalServiceInterface");
        }


        mDeviceId2Units = new HashMap<Long, String>();
    }

    /**
     * Called when the fragment is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreate");

        // mDeviceType = DeviceType.valueOf(savedInstanceState.getString(BANALService.DEVICE_TYPE));
        // mProtocol   = Protocol.valueOf(savedInstanceState.getString(BANALService.PROTOCOL));

        mDeviceType = DeviceType.valueOf(getArguments().getString(BANALService.DEVICE_TYPE));
        mProtocol = Protocol.valueOf(getArguments().getString(BANALService.PROTOCOL));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (DEBUG) Log.i(TAG, "onCreateView");

        // Inflate the layout for this fragment
        View view = inflater.inflate(getLayoutId(), container, false);

        // get the view
        lvDevices = view.findViewById(R.id.lvDevices);

        lvDevices.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (DEBUG) Log.d(TAG, "onItemClick");

                mOnRemoteDeviceSelectedListener.onRemoteDeviceSelected(id);
            }
        });

        lvDevices.setFocusable(true);
        lvDevices.setClickable(true);
        registerForContextMenu(lvDevices);

        mRemoteDevicesAdapter = new DeviceListCursorAdapter(getActivity(), mRemoteDevicesCursor, new DeviceListCursorAdapter.PairingChangedInterface() {
            @Override
            public void onPairingChanged(long deviceId, boolean paired) {
                if (DEBUG) Log.i(TAG, "onPairingChanged: " + deviceId + " paired: " + paired);
                // first, update the database
                ContentValues contentValues = new ContentValues();
                contentValues.put(DevicesDbHelper.PAIRED, paired);
                if (mRemoteDevicesDb != null) {
                    mRemoteDevicesDb.update(DevicesDbHelper.DEVICES,
                            contentValues,
                            DevicesDbHelper.C_ID + "=?",
                            new String[]{Long.toString(deviceId)});

                    // then inform all others
                    sendPairingChangedIntent(deviceId, paired);
                } else {
                    Log.i(TAG, "WTF, mRemoteDevicesDb == null");
                }
            }
        });
        lvDevices.setAdapter(mRemoteDevicesAdapter);

        // finally, return the main view
        return view;
    }


    // onActivityCreated

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.d(TAG, "onResume()");

        mRemoteDevicesDb = DevicesDatabaseManager.getInstance().getOpenDatabase();
        mRemoteDevicesCursor = getCursor();

        // really necessary???
        mRemoteDevicesAdapter.changeCursor(mRemoteDevicesCursor);
        mRemoteDevicesAdapter.notifyDataSetChanged();

        ContextCompat.registerReceiver(getActivity(), mPairingChangedReceiver, mPairingChangedFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        startTimer();
    }


    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.d(TAG, "onPause()");

        stopTimer();

        DevicesDatabaseManager.getInstance().closeDatabase();
        mRemoteDevicesDb = null;

        if (mRemoteDevicesCursor != null && !mRemoteDevicesCursor.isClosed()) {
            mRemoteDevicesCursor.close();
        }
        mRemoteDevicesCursor = null;

        try {
            getActivity().unregisterReceiver(mPairingChangedReceiver);
        } catch (Exception e) {
        }

    }


    // @Override
    // public void onSaveInstanceState(Bundle outState) {
    //    super.onSaveInstanceState(outState);
    //
    //    // Save the current article selection in case we need to recreate the fragment
    //    if (mDeviceType != null) {
    //    	outState.putString(BANALService.DEVICE_TYPE, mDeviceType.name());
    //    }
    //    if (mProtocol != null) {
    //    	outState.putString(BANALService.PROTOCOL, mProtocol.name());
    //    }
    //}


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if (DEBUG) Log.d(TAG, "onCreateContextMenu");
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.device_list_context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (DEBUG) Log.d(TAG, "onContextItemSelected");
        long id = ((AdapterContextMenuInfo) item.getMenuInfo()).id;
        switch (item.getItemId()) {
            case R.id.editDevice:
                mOnRemoteDeviceSelectedListener.onRemoteDeviceSelected(id);
                return true;
            case R.id.deleteDevice:
                deleteDevice(id);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    public void updateView() {
        if (DEBUG) Log.i(TAG, "updateView");

        if (getActivity() == null) {
            return;
        }

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mRemoteDevicesCursor = getCursor();
                if (mRemoteDevicesCursor != null) {
                    mRemoteDevicesAdapter.deleteLookupTable();  // might lead to race condition?
                    mRemoteDevicesAdapter.changeCursor(mRemoteDevicesCursor);
                    mRemoteDevicesAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    protected void deleteDevice(long deviceId) {
        if (DEBUG) Log.d(TAG, "deleteDevice: " + deviceId);
        // SQLiteDatabase db = (new DeviceDbHelper(this)).getWritableDatabase();

        showProgressDialog(getString(R.string.delete_device));

        mRemoteDevicesDb.delete(DevicesDbHelper.DEVICES,
                DevicesDbHelper.C_ID + "=?",
                new String[]{deviceId + ""});

        // also 'remove' the device from the TrackingViewsDatabase
        TrackingViewsDatabaseManager.removeSourceDevice(deviceId);

        updateView();

        cancelProgressDialog();

        // send broadcast to remove the device from the DeviceManager
        sendPairingChangedIntent(deviceId, false);
    }

    private synchronized void startTimer() {
        if (timer == null) {
            timer = new Timer();
            timer.scheduleAtFixedRate(new ClockTimeTask(), 0, 1000);
        }
    }

    private synchronized void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    protected void showProgressDialog(String message) {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(getActivity());
        }

        mProgressDialog.setMessage(message);
        mProgressDialog.show();
        mProgressDialog.setCancelable(false);
        mProgressDialog.setCanceledOnTouchOutside(false);

    }

    protected void cancelProgressDialog() {
        if (mProgressDialog.isShowing()) {
            try {
                mProgressDialog.dismiss();
                // sometimes this gives the following exception:
                // java.lang.IllegalArgumentException: View not attached to window manager
                // so we catch this exception
            } catch (IllegalArgumentException e) {
                // and nothing
                // http://stackoverflow.com/questions/2745061/java-lang-illegalargumentexception-view-not-attached-to-window-manager
            }
        }
    }

    private void sendPairingChangedIntent(long deviceId, boolean paired) {
        if (DEBUG) Log.i(TAG, "sendPairingChangedIntent");

        Intent intent = new Intent(BANALService.PAIRING_CHANGED)
                .putExtra(BANALService.PAIRED, paired)
                .putExtra(BANALService.DEVICE_ID, deviceId)
                .setPackage(getActivity().getPackageName());
        getActivity().sendBroadcast(intent);
    }

    // The container Activity must implement this interface so the frag can deliver messages
    public interface OnRemoteDeviceSelectedListener {
        /**
         * Called by DeviceTypeChoiceFragment when a list item is selected
         */
        void onRemoteDeviceSelected(long deviceId);
    }

    private class ClockTimeTask extends TimerTask {
        @Override
        public void run() {
            if (DEBUG) Log.d(TAG, "update main fields");

            if (getActivity() != null && isAdded()) {  // TODO: just hiding a bug?
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isAdded()) {               // still added?
                            for (Long deviceId : mRemoteDevicesAdapter.getDeviceIds()) {
                                if (DEBUG) Log.d(TAG, "updating " + deviceId);

                                if (mGetBanalServiceInterface != null
                                        && mGetBanalServiceInterface.getBanalServiceComm() != null) {
                                    if (!mDeviceId2Units.containsKey(deviceId)) {
                                        mDeviceId2Units.put(deviceId, getString(MyHelper.getUnitsId(DevicesDatabaseManager.getDeviceType(deviceId).getMainSensorType())));
                                    }
                                    mRemoteDevicesAdapter.updateMainField(deviceId, getString(R.string.ValueAndUnitFormat,
                                            mGetBanalServiceInterface.getBanalServiceComm().getMainSensorStringValue(deviceId),
                                            mDeviceId2Units.get(deviceId)));
                                } else {
                                    mRemoteDevicesAdapter.updateMainField(deviceId, getString(R.string.NoData));
                                }
                            }
                        }
                    }
                });
            }
        }
    }

}
