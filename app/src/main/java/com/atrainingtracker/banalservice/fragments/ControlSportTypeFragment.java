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
import android.widget.ImageButton;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.dialogs.SelectSportTypeDialog;

public class ControlSportTypeFragment extends Fragment {
    private static final String TAG = ControlSportTypeFragment.class.getName();
    private static final boolean DEBUG = BANALService.DEBUG && false;

    private ImageButton mIbRun, mIbBike, mIbOther;
    private TextView mTvRun, mTvBike, mTvOther;

    private boolean mViewCreated = false;

    private BANALService.GetBanalServiceInterface mGetBanalServiceInterface;
    BroadcastReceiver mUpdateViewReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateView();
        }
    };
    private IntentFilter mUpdateViewFilter = new IntentFilter(BANALService.NEW_DEVICE_FOUND_INTENT);

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
                    updateView();
                }

                @Override
                public void disconnectedFromBanalService() {
                    updateView();
                }
            });
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement GetBanalServiceInterface");
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreateView");

        View view = inflater.inflate(R.layout.control_sport_type_layout, container, false);

        mTvRun = view.findViewById(R.id.cst_tvRun);
        mTvBike = view.findViewById(R.id.cst_tvBike);
        mTvOther = view.findViewById(R.id.cst_tvOther);

        mIbRun = view.findViewById(R.id.cst_ibRun);
        mIbBike = view.findViewById(R.id.cst_ibBike);
        mIbOther = view.findViewById(R.id.cst_ibOther);

        mIbRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DEBUG) Log.i(TAG, "RUN selected by user");
                setUserSelectedSportType(BSportType.RUN);
            }
        });
        mIbRun.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showSelectSportTypeDialog(BSportType.RUN);
                return true;
            }
        });

        mIbBike.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DEBUG) Log.i(TAG, "BIKE selected by user");
                setUserSelectedSportType(BSportType.BIKE);
            }
        });
        mIbBike.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showSelectSportTypeDialog(BSportType.BIKE);
                return true;
            }
        });

        mIbOther.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DEBUG) Log.i(TAG, "OTHER (UNKNOWN) selected by user");
                setUserSelectedSportType(BSportType.UNKNOWN);
            }
        });
        mIbOther.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showSelectSportTypeDialog(BSportType.UNKNOWN);
                return true;
            }
        });

        mViewCreated = true;

        return view;
    }

    private void setUserSelectedSportType(BSportType bSportType) {
        BANALServiceComm banalServiceComm = mGetBanalServiceInterface.getBanalServiceComm();
        if (banalServiceComm != null) {
            banalServiceComm.setUserSelectedSportType(bSportType);
            updateView();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(TAG, "onResume");

        updateView();

        getActivity().registerReceiver(mUpdateViewReceiver, mUpdateViewFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.i(TAG, "onPause");

        getActivity().unregisterReceiver(mUpdateViewReceiver);
    }

    private void showSelectSportTypeDialog(BSportType bsportType) {
        SelectSportTypeDialog selectSportTypeDialog = SelectSportTypeDialog.newInstance(bsportType);
        selectSportTypeDialog.show(getFragmentManager(), SelectSportTypeDialog.TAG);
    }


    public void updateView() {
        if (DEBUG) Log.d(TAG, "updateView");

        if (!mViewCreated | !isAdded()) {
            return;
        }

        BANALServiceComm banalServiceComm = mGetBanalServiceInterface.getBanalServiceComm();
        if (banalServiceComm != null) {
            switch (banalServiceComm.getBSportType()) {
                case RUN:
                    mTvRun.setTextColor(ContextCompat.getColor(getActivity(), R.color.color_on_background));
                    mTvBike.setTextColor(ContextCompat.getColor(getActivity(), R.color.bright_grey));
                    mTvOther.setTextColor(ContextCompat.getColor(getActivity(), R.color.bright_grey));

                    mIbRun.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.bsport_run));
                    mIbBike.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.bsport_bike_gray));
                    mIbOther.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.bsport_other_gray));

                    break;

                case BIKE:
                    mTvRun.setTextColor(ContextCompat.getColor(getActivity(), R.color.bright_grey));
                    mTvBike.setTextColor(ContextCompat.getColor(getActivity(), R.color.color_on_background));
                    mTvOther.setTextColor(ContextCompat.getColor(getActivity(), R.color.bright_grey));

                    mIbRun.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.bsport_run_gray));
                    mIbBike.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.bsport_bike));
                    mIbOther.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.bsport_other_gray));

                    break;

                case UNKNOWN:
                    mTvRun.setTextColor(ContextCompat.getColor(getActivity(), R.color.bright_grey));
                    mTvBike.setTextColor(ContextCompat.getColor(getActivity(), R.color.bright_grey));
                    mTvOther.setTextColor(ContextCompat.getColor(getActivity(), R.color.color_on_background));

                    mIbRun.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.bsport_run_gray));
                    mIbBike.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.bsport_bike_gray));
                    mIbOther.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.bsport_other));

                    break;

                default:
                    mTvRun.setTextColor(ContextCompat.getColor(getActivity(), R.color.bright_grey));
                    mTvBike.setTextColor(ContextCompat.getColor(getActivity(), R.color.bright_grey));
                    mTvOther.setTextColor(ContextCompat.getColor(getActivity(), R.color.bright_grey));

                    mIbRun.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.bsport_run_gray));
                    mIbBike.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.bsport_bike_gray));
                    mIbOther.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.bsport_other_gray));

                    break;
            }
        } else {
            mTvRun.setTextColor(ContextCompat.getColor(getActivity(), R.color.bright_grey));
            mTvBike.setTextColor(ContextCompat.getColor(getActivity(), R.color.bright_grey));
            mTvOther.setTextColor(ContextCompat.getColor(getActivity(), R.color.bright_grey));

            mIbRun.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.bsport_run_gray));
            mIbBike.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.bsport_bike_gray));
            mIbOther.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.bsport_other_gray));
        }
    }

}
