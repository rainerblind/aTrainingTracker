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

package com.atrainingtracker.trainingtracker.dialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.MyHelper;
import com.atrainingtracker.trainingtracker.TrainingApplication;

/**
 * Created by rainer on 05.01.17.
 */

public class LapSummaryDialog extends DialogFragment {
    public static final String TAG = LapSummaryDialog.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);


    private static final int SHOW_LAP_SUMMARY_TIME = 3000;

    private static final String LAP_NR = "LAP_NR";
    private static final String LAP_TIME = "LAP_TIME";
    private static final String LAP_DISTANCE = "LAP_DISTANCE";
    private static final String LAP_SPEED = "LAP_SPEED";

    private int mLapNr;
    private String mLapTime;
    private String mLapDistance;
    private String mLapSpeed;

    public static LapSummaryDialog newInstance(int lapNr, String lapTime, String lapDistance, String lapSpeed) {
        if (DEBUG) Log.i(TAG, "newInstance");

        LapSummaryDialog fragment = new LapSummaryDialog();

        Bundle args = new Bundle();
        args.putInt(LAP_NR, lapNr);
        args.putString(LAP_TIME, lapTime);
        args.putString(LAP_DISTANCE, lapDistance);
        args.putString(LAP_SPEED, lapSpeed);
        fragment.setArguments(args);

        return fragment;
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate");

        mLapNr = getArguments().getInt(LAP_NR);
        mLapTime = getArguments().getString(LAP_TIME);
        mLapDistance = getArguments().getString(LAP_DISTANCE);
        mLapSpeed = getArguments().getString(LAP_SPEED);
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        TextView title = new TextView(getActivity());
        // You Can Customise your Title here
        title.setText(getString(R.string.Lap_NR, mLapNr));
//        title.setBackgroundColor(getResources().getColor(R.color.my_blue));
        title.setPadding(10, 10, 10, 10);
        title.setGravity(Gravity.CENTER);
//        title.setTextColor(getResources().getColor(R.color.my_white));
        title.setTextSize(20);

        builder.setCustomTitle(title);
        // builder.setTitle(getString(R.string.Lap_NR, lapNr));

        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();

        // Inflate and set the layout for the dialog
        final View mainView = inflater.inflate(R.layout.lap_summary_dialog, null);

        TextView tvLapTime = mainView.findViewById(R.id.textViewLapTime);
        tvLapTime.setText(getString(R.string.value_unit_string_string, mLapTime, getString(MyHelper.getUnitsId(SensorType.TIME_LAP))));

        TextView tvLapDistance = mainView.findViewById(R.id.textViewLapDistance);
        tvLapDistance.setText(getString(R.string.value_unit_string_string, mLapDistance, getString(MyHelper.getUnitsId(SensorType.DISTANCE_m_LAP))));

        TextView tvLapSpeed = mainView.findViewById(R.id.textViewLapSpeed);
        tvLapSpeed.setText(getString(R.string.value_unit_string_string, mLapSpeed, getString(MyHelper.getUnitsId(SensorType.SPEED_mps))));

        builder.setView(mainView);

        final AlertDialog alert = builder.create();

        // Hide after some seconds
        final Handler handler = new Handler();
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (alert.isShowing()) {
                    alert.dismiss();
                }
            }
        };

        // todo ondismiss()
        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                handler.removeCallbacks(runnable);
            }
        });

        handler.postDelayed(runnable, SHOW_LAP_SUMMARY_TIME);


        return alert;
    }
}
