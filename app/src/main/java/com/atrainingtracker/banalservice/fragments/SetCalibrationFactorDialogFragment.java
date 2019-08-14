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
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.trainingtracker.MyHelper;

/**
 * Created by rainer on 06.03.16.
 */
public class SetCalibrationFactorDialogFragment extends DialogFragment {
    public static final String TAG = "SetCalibrationFactorDialogFragment";
    private static final boolean DEBUG = BANALService.DEBUG & false;
    private static final String CALIBRATION_FACTOR = "CALIBRATION_FACTOR";
    private static final String TITLE_NAME = "TITLE_NAME";
    private static final String FIELD_NAME = "FIELD_NAME";
    private String mTitleName;
    private String mFieldName;
    private String mCalibrationFactor = 1.0 + "";
    private double mOldCalibrationFactor = 1;
    private EditText etCalibrationFactor;
    private EditText etMeassured;
    private EditText etCorrect;
    private NewCalibrationFactorListener mNewCalibrationFactorListener = null;
    private TextWatcher calibrationDistancesChangedWatcher = new TextWatcher() {

        @Override
        public void afterTextChanged(Editable s) {
            if (DEBUG) Log.d(TAG, "afterTextChanged");
            double meassuredDistance = MyHelper.string2Double(etMeassured.getText().toString());
            double correctDistance = MyHelper.string2Double(etCorrect.getText().toString());

            etCalibrationFactor.setText(mOldCalibrationFactor / meassuredDistance * correctDistance + "");
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            if (DEBUG) Log.d(TAG, "beforeTextChanged");
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (DEBUG) Log.d(TAG, "onTextChanged");
        }
    };

    public static SetCalibrationFactorDialogFragment newInstance(String calibrationFactor, String titleName, String fieldName) {
        SetCalibrationFactorDialogFragment fragment = new SetCalibrationFactorDialogFragment();

        Bundle args = new Bundle();
        args.putString(CALIBRATION_FACTOR, calibrationFactor);
        args.putString(TITLE_NAME, titleName);
        args.putString(FIELD_NAME, fieldName);
        fragment.setArguments(args);

        return fragment;
    }

    public void setNewCalibrationFactorListener(NewCalibrationFactorListener listener) {
        mNewCalibrationFactorListener = listener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreate()");

        mCalibrationFactor = getArguments().getString(CALIBRATION_FACTOR);
        mTitleName = getArguments().getString(TITLE_NAME);
        mFieldName = getArguments().getString(FIELD_NAME);
        mOldCalibrationFactor = MyHelper.string2Double(mCalibrationFactor);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setTitle(getString(R.string.Set_foo, mTitleName));

        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout

        final View mainDialog = inflater.inflate(R.layout.dialog_set_calibration_factor, null);

        etMeassured = mainDialog.findViewById(R.id.etMeassured);
        etMeassured.addTextChangedListener(calibrationDistancesChangedWatcher);

        etCorrect = mainDialog.findViewById(R.id.etCorrect);
        etCorrect.addTextChangedListener(calibrationDistancesChangedWatcher);

        etCalibrationFactor = mainDialog.findViewById(R.id.etCalibrationFactor);
        etCalibrationFactor.setText(mCalibrationFactor);

        ((TextView) mainDialog.findViewById(R.id.tvCalibrationFactor)).setText(mFieldName + ":");

        builder.setView(mainDialog);

        // Add action buttons
        builder.setPositiveButton(getString(R.string.OK), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                mNewCalibrationFactorListener.newCalibrationFactor(etCalibrationFactor.getText().toString());
            }
        });
        builder.setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                SetCalibrationFactorDialogFragment.this.getDialog().cancel();
            }
        });
        return builder.create();
    }

    public interface NewCalibrationFactorListener {
        void newCalibrationFactor(String calibrationFactor);
    }

}