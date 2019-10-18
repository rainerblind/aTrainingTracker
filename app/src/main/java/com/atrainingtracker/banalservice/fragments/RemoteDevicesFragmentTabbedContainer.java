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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.Protocol;
import com.atrainingtracker.banalservice.dialogs.InstallANTShitDialog;

import java.util.ArrayList;
import java.util.Arrays;


/**
 * Created by rainer on 20.01.16.
 */
// TODO: show option to check ANT Installation
public class RemoteDevicesFragmentTabbedContainer extends Fragment {

    public static final String TAG = RemoteDevicesFragmentTabbedContainer.class.getSimpleName();
    private static final boolean DEBUG = BANALService.DEBUG && false;
    /**
     * The {@link ViewPager} that will host the section contents.
     */
    protected ViewPager mViewPager;
    protected boolean mShowingDialog = false;
    protected boolean mSearching = false;
    /**
     * The {@link PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which
     * will keep every loaded fragment in memory. If this becomes too memory
     * intensive, it may be best to switch to a
     * {@link FragmentStatePagerAdapter}.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;
    Protocol mProtocol = null;
    DeviceType mDeviceType = null;

    public static RemoteDevicesFragmentTabbedContainer newInstance(Protocol protocol, DeviceType deviceType) {

        RemoteDevicesFragmentTabbedContainer remoteDevicesFragmentTabbedContainer = new RemoteDevicesFragmentTabbedContainer();

        Bundle args = new Bundle();
        args.putString(BANALService.PROTOCOL, protocol.name());
        args.putString(BANALService.DEVICE_TYPE, deviceType.name());
        remoteDevicesFragmentTabbedContainer.setArguments(args);

        return remoteDevicesFragmentTabbedContainer;
    }

    public static RemoteDevicesFragmentTabbedContainer newInstance(Protocol protocol) {

        RemoteDevicesFragmentTabbedContainer remoteDevicesFragmentTabbedContainer = new RemoteDevicesFragmentTabbedContainer();

        Bundle args = new Bundle();
        args.putString(BANALService.PROTOCOL, protocol.name());
        remoteDevicesFragmentTabbedContainer.setArguments(args);

        return remoteDevicesFragmentTabbedContainer;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreate()");

        mProtocol = Protocol.valueOf(getArguments().getString(BANALService.PROTOCOL));
        if (getArguments().getString(BANALService.DEVICE_TYPE) != null) {
            mDeviceType = DeviceType.valueOf(getArguments().getString(BANALService.DEVICE_TYPE));
        } else if (savedInstanceState != null && savedInstanceState.containsKey(BANALService.DEVICE_TYPE)) {
            mDeviceType = DeviceType.valueOf(savedInstanceState.getString(BANALService.DEVICE_TYPE));
        }

        if (DEBUG) Log.i(TAG, "protocol=" + mProtocol.name() + ", DeviceType=" + mDeviceType);
    }

    // onAttach???

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreateView()");

        View view = inflater.inflate(R.layout.tabbed_remote_devices_container_fragment, container, false);

        // get the views
        mViewPager = view.findViewById(R.id.pager);

        return view;
    }


    @Override
    public void onResume() {
        super.onResume();

        if (mDeviceType != null) {
            startSearching();
            setSectionsPagerAdapter();
        } else if (!mShowingDialog) { // show dialog when mDeviceType is not yet known
            mShowingDialog = true;

            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getContext());
            dialogBuilder.setIcon(mProtocol.getIconId());
            dialogBuilder.setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    Log.i(TAG, "key pressed: keyCode=" + keyCode + ", keyEvent=" + event);
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        cancelDialog(dialog);
                        return true;
                    }
                    return false;
                }
            });
            dialogBuilder.setCancelable(false);
            dialogBuilder.setTitle(R.string.select_device_type);

            dialogBuilder.setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    cancelDialog(dialog);
                }
            });

            final ArrayList<DeviceType> deviceTypeList = new ArrayList<>(Arrays.asList(DeviceType.getRemoteDeviceTypes(mProtocol)));
            dialogBuilder.setAdapter(new DeviceChoiceArrayAdapter(getActivity(), R.layout.device_choice_row, deviceTypeList, mProtocol),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mDeviceType = deviceTypeList.get(which);
                            startSearching();
                            setSectionsPagerAdapter();
                        }
                    });
            dialogBuilder.show();
        }

        if (mProtocol == Protocol.ANT_PLUS) {
            setHasOptionsMenu(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.i(TAG, "onPause");

        stopSearching();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (DEBUG) Log.i(TAG, "onSaveInstanceState");

        if (mProtocol != null) {
            outState.putString(BANALService.PROTOCOL, mProtocol.name());
        }
        if (mDeviceType != null) {
            outState.putString(BANALService.DEVICE_TYPE, mDeviceType.name());
        }

        super.onSaveInstanceState(outState);
    }

    /**
     * Called first time user clicks on the menu button
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (DEBUG) Log.d(TAG, "onCreateOptionsMenu");

        inflater.inflate(R.menu.remote_devices, menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (DEBUG) Log.i(TAG, "onOptionsItemSelected");

        switch (item.getItemId()) {
            case R.id.itemCheckANTInstallation:
                InstallANTShitDialog installANTShitDialog = new InstallANTShitDialog();
                installANTShitDialog.show(getFragmentManager(), InstallANTShitDialog.TAG);

                return true;
        }

        return false;
    }


    private void cancelDialog(DialogInterface dialog) {
        dialog.dismiss();
        if (getActivity() != null) {
            getActivity().onBackPressed();
        }
        mShowingDialog = false;
    }

    protected void setSectionsPagerAdapter() {
        if (DEBUG) Log.i(TAG, "setSectionsPagerAdapter");

        mSectionsPagerAdapter = new SectionsPagerAdapter(getChildFragmentManager());
        mViewPager.setAdapter(mSectionsPagerAdapter);
    }


    protected void startSearching() {
        if (DEBUG) Log.i(TAG, "startSearching");

        // mllSearchLayout.setVisibility(View.VISIBLE);  // really necessary???
        // mtvSearchingForRemoteDevice.setText(getString(R.string.searchingForDevice,
        //        getString(UIHelper.getNameId(mProtocol)),
        //        getString(UIHelper.getNameId(mDeviceType))));

        Intent intent = new Intent(BANALService.START_SEARCHING_FOR_NEW_DEVICES_INTENT);
        intent.putExtra(BANALService.PROTOCOL, mProtocol.name());
        intent.putExtra(BANALService.DEVICE_TYPE, mDeviceType.name());
        getActivity().sendBroadcast(intent);

        mSearching = true;

        // anything else?
        // Toast.makeText(getActivity(), "end of startSearching", Toast.LENGTH_SHORT);
    }

    protected void stopSearching() {
        if (DEBUG) Log.i(TAG, "stopSearching");

        // mtvSearchingForRemoteDevice.setVisibility(View.GONE);

        getActivity().sendBroadcast(new Intent(BANALService.STOP_SEARCHING_FOR_NEW_DEVICES_INTENT));
        mSearching = false;

        // anything else?
    }


    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            if (DEBUG) Log.i(TAG, "SectionsPagerAdapter.getItem(" + position + ")");

            switch (position) {
                case 0:
                    return AvailableRemoteDevicesFragment.newInstance(mProtocol, mDeviceType);

                case 1:
                    return PairedRemoteDevicesFragment.newInstance(mProtocol, mDeviceType);

                case 2:
                    return KnownRemoteDevicesFragment.newInstance(mProtocol, mDeviceType);

                default:
                    return new Fragment();
            }

        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.available_devices);

                case 1:
                    return getString(R.string.paired_devices);

                case 2:
                    return getString(R.string.known_devices);
            }

            return null;
        }
    }
}
