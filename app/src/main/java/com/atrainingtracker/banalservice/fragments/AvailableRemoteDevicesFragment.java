package com.atrainingtracker.banalservice.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Devices.DeviceType;
import com.atrainingtracker.banalservice.Protocol;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager.DevicesDbHelper;
import com.atrainingtracker.banalservice.helpers.UIHelper;

import java.util.List;

/**
 * Created by rainer on 30.01.16.
 */
public class AvailableRemoteDevicesFragment extends RemoteDevicesFragment {
    public static final String TAG = "AvailableRemoteDevicesFragment";
    private static final boolean DEBUG = BANALService.DEBUG && false;
    protected LinearLayout mllSearchLayout;
    // protected ProgressBar mpbSearching;
    protected TextView mtvSearchingForRemoteDevice;

    protected IntentFilter mNewDeviceFoundFilter = new IntentFilter(BANALService.NEW_DEVICE_FOUND_INTENT);
    BroadcastReceiver mNewDeviceFoundReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateView();
        }
    };

    public static AvailableRemoteDevicesFragment newInstance(Protocol protocol, DeviceType deviceType) {
        if (DEBUG) Log.i(TAG, "newInstance()");

        AvailableRemoteDevicesFragment availableRemoteDevicesFragment = new AvailableRemoteDevicesFragment();

        Bundle args = new Bundle();
        args.putString(BANALService.PROTOCOL, protocol.name());
        args.putString(BANALService.DEVICE_TYPE, deviceType.name());
        availableRemoteDevicesFragment.setArguments(args);

        return availableRemoteDevicesFragment;
    }

    //    @Override
//	public void onAttach(Context context)
//	{
//		super.onAttach(context);
//
//        try {
//            mGetBanalServiceInterface = (BANALService.GetBanalServiceInterface) context;
//        }
//        catch (ClassCastException e) {
//            throw new ClassCastException(context.toString() + " must implement GetBanalServiceInterface");
//        }
//	}
    @Override
    public void onAttach(Context context) {
        if (DEBUG) Log.i(TAG, "onAttach");
        super.onAttach(context);

        mGetBanalServiceInterface.registerConnectionStatusListener(new BANALService.GetBanalServiceInterface.ConnectionStatusListener() {
            @Override
            public void connectedToBanalService() {
                updateView();
            }

            @Override
            public void disconnectedFromBanalService() {

            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (DEBUG) Log.i(TAG, "onCreateView");

        View view = super.onCreateView(inflater, container, savedInstanceState);

        mllSearchLayout = view.findViewById(R.id.llSearchLayout);
        // mpbSearching = (ProgressBar) view.findViewById(R.id.pbSearching);
        mtvSearchingForRemoteDevice = view.findViewById(R.id.tvSearchingForRemoteDevice);
        mtvSearchingForRemoteDevice.setText(getString(R.string.searchingForDevice,
                getString(UIHelper.getNameId(mProtocol)),
                getString(UIHelper.getNameId(mDeviceType))));
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(TAG, "onResume()");

        getActivity().registerReceiver(mNewDeviceFoundReceiver, mNewDeviceFoundFilter);

        if (mDeviceType == DeviceType.ALL) {
            mllSearchLayout.setVisibility(View.GONE);
        }
        //else {
        //    startSearching();
        //}

        // anything else?
    }

    @Override
    public void onPause() {
        if (DEBUG) Log.i(TAG, "onPause");
        super.onPause();

//         stopSearching();

        try {
            getActivity().unregisterReceiver(mNewDeviceFoundReceiver);
        } catch (Exception e) {
        }


        // anything else?
    }

    @Override
    protected int getLayoutId() {
        return R.layout.device_list_with_search;
    }


    @Override
    protected Cursor getCursor() {
        if (DEBUG) Log.i(TAG, "getCursor()");

        if (mGetBanalServiceInterface.getBanalServiceComm() == null
                || mRemoteDevicesDb == null) {
            return null;
        }

        List<Long> availableDeviceIds = mGetBanalServiceInterface.getBanalServiceComm().getDatabaseIdsOfActiveDevices(mProtocol, mDeviceType);
        if (DEBUG) {
            Log.i(TAG, "availableDeviceIds: size=" + availableDeviceIds.size() + ": " + availableDeviceIds);
        }
        String queryString = "";
        String[] queryArgs = new String[availableDeviceIds.size()];

        if (availableDeviceIds.size() > 0) {
            StringBuilder queryBuilder = new StringBuilder();

            int pos = 0;
            for (long deviceId : availableDeviceIds) {
                if (pos > 0) {
                    queryBuilder.append(" OR ");
                }
                queryBuilder.append(DevicesDbHelper.C_ID + "=?");
                queryArgs[pos] = Long.toString(deviceId);

                pos++;
            }

            // queryBuilder.insert(0, "(");
            // queryBuilder.append(") AND " + DevicesDbHelper.DEVICE_TYPE + "=? AND " + DevicesDbHelper.PROTOCOL + "=?");

            queryString = queryBuilder.toString();

            // queryArgs[queryArgs.length-2] = mDeviceType.name();
            // queryArgs[queryArgs.length-1] = mProtocol.name();

        } else { // empty list: here we need a queryString and Args such that we get an empty cursor
            queryString = DevicesDbHelper.C_ID + "=?";
            queryArgs = new String[]{Long.toString(-1)};  // there must be no device with this device type byte
        }

        if (DEBUG) Log.i(TAG, "query = " + queryString);
        if (DEBUG) Log.i(TAG, "args = " + queryArgs);

        return mRemoteDevicesDb.query(DevicesDbHelper.DEVICES,
                DeviceListCursorAdapter.COLUMNS,
                queryString,
                queryArgs,
                null,
                null,
                null);
    }


    // onCreate
    // onCreateView
    // onActivityCreatec
    // onStart
    // onResume

    // onPause

    // onDestroy
}
