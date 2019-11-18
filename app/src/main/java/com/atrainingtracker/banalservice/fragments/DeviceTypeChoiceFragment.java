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

import android.content.Context;
import android.os.Bundle;
import androidx.fragment.app.ListFragment;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.Protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DeviceTypeChoiceFragment extends ListFragment {
    public static final String TAG = "DeviceTypeChoiceFragment";
    private static final boolean DEBUG = BANALService.DEBUG & false;

    OnDeviceTypeSelectedListener mCallback;
    Protocol mProtocol;
    List<DeviceType> mDeviceTypes;

    public static DeviceTypeChoiceFragment newInstance(Protocol protocol) {
        DeviceTypeChoiceFragment deviceTypeChoiceFragment = new DeviceTypeChoiceFragment();

        Bundle args = new Bundle();
        args.putString(BANALService.PROTOCOL, protocol.name());
        deviceTypeChoiceFragment.setArguments(args);

        return deviceTypeChoiceFragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (DEBUG) Log.i(TAG, "onAttach()");

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception.
        try {
            mCallback = (OnDeviceTypeSelectedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement OnDeviceTypeSelectedListener");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreate()");

        mProtocol = Protocol.valueOf(getArguments().getString(BANALService.PROTOCOL));
        mDeviceTypes = new ArrayList<>(Arrays.asList(DeviceType.getRemoteDeviceTypes(mProtocol)));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.d(TAG, "onResume");

        setListAdapter(new DeviceChoiceArrayAdapter(getActivity(), R.layout.device_choice_row, mDeviceTypes, mProtocol));

    }

    // onCreateView
    // onActivityCreated
    // onStart

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        // Notify the hosting activity of selected item
        mCallback.onDeviceTypeSelected(mDeviceTypes.get(position), mProtocol);

        // // Set the item as checked to be highlighted when in two-pane layout
        // getListView().setItemChecked(position, true);  // unfortunately, this does not work with an custom adapter TODO
        //
        // for (int i = 0; i < getListView().getChildCount(); i++) {
        // 	getListView().getChildAt(i).setBackgroundColor(getResources().getColor(android.R.color.white));
        // }
        // view.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
    }


//    @Override
//    public void onSaveInstanceState(Bundle outState) {
//        super.onSaveInstanceState(outState);
//
//        // Save the current article selection in case we need to recreate the fragment
//        if (mProtocol != null) {
//        	outState.putString(BANALService.PROTOCOL, mProtocol.name());
//        }
//    }

    // The container Activity must implement this interface so the frag can deliver messages
    public interface OnDeviceTypeSelectedListener {
        /**
         * Called by DeviceTypeChoiceFragment when a list item is selected
         */
        void onDeviceTypeSelected(DeviceType deviceType, Protocol protocol);
    } // TODO: good way to implement interfaces, so it is always clear who need the interface!
}