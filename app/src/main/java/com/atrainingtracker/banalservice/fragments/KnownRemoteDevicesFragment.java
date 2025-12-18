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

import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.Protocol;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager.DevicesDbHelper;

/**
 * Created by rainer on 30.01.16.
 */
public class KnownRemoteDevicesFragment extends RemoteDevicesFragment {
    public static final String TAG = "KnownRemoteDevicesFragment";
    private static final boolean DEBUG = BANALService.getDebug(true);

    public static KnownRemoteDevicesFragment newInstance(Protocol protocol, DeviceType deviceType) {
        if (DEBUG) Log.i(TAG, "newInstance");

        KnownRemoteDevicesFragment knownRemoteDevicesFragment = new KnownRemoteDevicesFragment();

        Bundle args = new Bundle();
        args.putString(BANALService.PROTOCOL, protocol.name());
        args.putString(BANALService.DEVICE_TYPE, deviceType.name());
        knownRemoteDevicesFragment.setArguments(args);

        return knownRemoteDevicesFragment;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.device_list;
    }

    @Override
    protected Cursor getCursor() {
        if (DEBUG) Log.i(TAG, "getCursor()");

        Cursor cursor = null;

        if (mDeviceType == null) {
            // get an empty cursor
            cursor = mRemoteDevicesDb.query(DevicesDbHelper.DEVICES,
                    DeviceListCursorAdapter.COLUMNS,
                    DevicesDbHelper.C_ID + "=?",
                    new String[]{Long.toString(-1)},  // there must be no device with this device type byte
                    null,
                    null,
                    null);
        } else if (mDeviceType == DeviceType.ALL) {
            if (mProtocol == Protocol.ALL) {
                cursor = mRemoteDevicesDb.query(DevicesDbHelper.DEVICES,
                        DeviceListCursorAdapter.COLUMNS,
                        null,
                        null,
                        null,
                        null,
                        null);
            } else {
                cursor = mRemoteDevicesDb.query(DevicesDbHelper.DEVICES,
                        DeviceListCursorAdapter.COLUMNS,
                        DevicesDbHelper.PROTOCOL + "=?",
                        new String[]{mProtocol.name()},
                        null,
                        null,
                        null);
            }
        } else {
            cursor = mRemoteDevicesDb.query(DevicesDbHelper.DEVICES,
                    DeviceListCursorAdapter.COLUMNS,
                    DevicesDbHelper.DEVICE_TYPE + "=? AND " + DevicesDbHelper.PROTOCOL + "=?",
                    new String[]{mDeviceType.name(), mProtocol.name()},
                    null,
                    null,
                    null);
        }

        return cursor;
    }

    // onAttach
    // onCreate
    // onCreateView
    // onActivityCreated
    // onStart
    // onResume

    // onPause

    // onDestroy


}
