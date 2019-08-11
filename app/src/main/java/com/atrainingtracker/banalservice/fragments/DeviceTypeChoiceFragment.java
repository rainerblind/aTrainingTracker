/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.atrainingtracker.banalservice.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Devices.DeviceType;
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