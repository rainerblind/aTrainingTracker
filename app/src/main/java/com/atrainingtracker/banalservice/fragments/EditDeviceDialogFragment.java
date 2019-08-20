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
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.BikePowerSensorsHelper;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.Protocol;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager.DevicesDbHelper;
import com.atrainingtracker.banalservice.helpers.BatteryStatusHelper;
import com.atrainingtracker.banalservice.helpers.UIHelper;
import com.atrainingtracker.trainingtracker.views.MultiSelectionSpinner;
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper;

import java.util.List;

public class EditDeviceDialogFragment
        extends DialogFragment {
    public static final String TAG = "EditDeviceDialogFragment";
    private static final boolean DEBUG = BANALService.DEBUG & false;

    // public interface OnEditDeviceFinishedListener
    // {
    //	void onEditDeviceFinished();
    //	// double getLastLapDistance();
    // }
    // OnEditDeviceFinishedListener mOnEditDeviceFinishedListener;
    // TODO: localize wheel circumference!
    private static final String[] CIRCUMFERENCE_TEXT = {"wheel_circumference", "700 X 28", "700 X 25", "700 X 23", "700 X 20", "26 X 2.125", "26 X 1.9", "26 X 1.5", "26 X 1.25", "26 X 1.0"};
    private static final double[] CIRCUMFERENCE_VALUE = {2.1, 2.136, 2.105, 2.097, 2.086, 2.070, 2.055, 1.985, 1.953, 1.913};
    protected boolean mHaveEquipmentList = false;
    protected DeviceType mDeviceType;
    protected int mBikePowerSensorFlags;
    String mOriginalCalibrationFactor;
    boolean mCalibrationFactorChanged;
    long mDeviceID;
    // all the views
    EditText etDeviceName, etCalibrationFactor;
    CheckBox cbPaired, cbDoublePowerBalanceValues, cbInvertPowerBalanceValues;
    TextView tvManufacturer;
    TextView tvLastSeen;
    ImageView ivBatteryStatus;
    Spinner spinnerWheelCircumference;
    MultiSelectionSpinner spinnerEquipment;
    Button bEditCalibrationFactor;
    LinearLayout llWheelCircumference, llPowerSensors;
    private ArrayAdapter<String> CIRCUMFERENCE_ARRAY_ADAPTER;
    private String mCalibrationFactorTitle;
    private String mCalibrationFactorName;

    public static EditDeviceDialogFragment newInstance(long deviceId) {
        EditDeviceDialogFragment editDeviceDialogFragment = new EditDeviceDialogFragment();

        Bundle args = new Bundle();
        args.putLong(DevicesDbHelper.C_ID, deviceId);
        editDeviceDialogFragment.setArguments(args);

        return editDeviceDialogFragment;
    }

    // @Override
    // public void onAttach(Activity activity) {
    //    super.onAttach(activity);
    //    // Verify that the host activity implements the callback interface
    //    try {
    //        mOnEditDeviceFinishedListener = (OnEditDeviceFinishedListener) activity;
    //    } catch (ClassCastException e) {
    //        // The activity doesn't implement the interface, throw exception
    //        throw new ClassCastException(activity.toString()
    //                + " must implement OnEditDeviceFinishedListener");
    //    }
    //}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreate()");

        mDeviceID = getArguments().getLong(DevicesDbHelper.C_ID);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());


        CIRCUMFERENCE_ARRAY_ADAPTER = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_item, CIRCUMFERENCE_TEXT);

        // get the data
        DevicesDatabaseManager databaseManager = DevicesDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = db.query(DevicesDbHelper.DEVICES,
                null,
                DevicesDbHelper.C_ID + "=?",
                new String[]{Long.toString(mDeviceID)},
                null,
                null,
                null);
        cursor.moveToFirst();
        mDeviceType = DeviceType.valueOf(cursor.getString(cursor.getColumnIndex(DevicesDbHelper.DEVICE_TYPE)));
        Protocol protocol = Protocol.valueOf(cursor.getString(cursor.getColumnIndex(DevicesDbHelper.PROTOCOL)));

        builder.setTitle(R.string.edit_device);
        builder.setIcon(UIHelper.getIconId(mDeviceType, protocol));

        // create the view
        final View mainDialog = getActivity().getLayoutInflater().inflate(getLayout(protocol, mDeviceType), null);
        builder.setView(mainDialog);

        // find views
        etDeviceName = mainDialog.findViewById(R.id.etDeviceName);
        cbPaired = mainDialog.findViewById(R.id.cbPaired);
        cbDoublePowerBalanceValues = mainDialog.findViewById(R.id.cbDoublePowerBalanceValues);
        cbInvertPowerBalanceValues = mainDialog.findViewById(R.id.cbInvertPowerBalanceValues);


        tvManufacturer = mainDialog.findViewById(R.id.tvManufacturer);
        tvLastSeen = mainDialog.findViewById(R.id.tvLastSeen);
        ivBatteryStatus = mainDialog.findViewById(R.id.ivBatteryStatus);

        etCalibrationFactor = mainDialog.findViewById(R.id.etCalibrationFactor);
        spinnerWheelCircumference = mainDialog.findViewById(R.id.spinnerWheelCircumference);
        spinnerEquipment = mainDialog.findViewById(R.id.spinnerEquipment);

        bEditCalibrationFactor = mainDialog.findViewById(R.id.bEditCalibrationFactor);

        llWheelCircumference = mainDialog.findViewById(R.id.llWheelCircumference);
        llPowerSensors = mainDialog.findViewById(R.id.llBTPowerFeatures);

        // optionally, configure the bike power view
        if (mDeviceType == DeviceType.BIKE_POWER) {
            mBikePowerSensorFlags = DevicesDatabaseManager.getBikePowerSensorFlags(mDeviceID);

            if (!BikePowerSensorsHelper.isWheelRevolutionDataSupported(mBikePowerSensorFlags)
                    && !BikePowerSensorsHelper.isWheelDistanceDataSupported(mBikePowerSensorFlags)
                    && !BikePowerSensorsHelper.isWheelSpeedDataSupported(mBikePowerSensorFlags)) {
                // there is no wheel speed or distance sensor, so we do not need the calibration factor stuff
                llWheelCircumference.setVisibility(View.GONE);
                etCalibrationFactor = null;
                spinnerWheelCircumference = null;
                bEditCalibrationFactor = null;
            }

            // torque (almost power)
            if (BikePowerSensorsHelper.isTorqueDataSupported(mBikePowerSensorFlags)) {
                TextView tv = new TextView(getContext());
                tv.setText(R.string.bike_power__torque_data);
                tv.setTextAppearance(getContext(), android.R.style.TextAppearance_Medium);
                llPowerSensors.addView(tv);
            }

            // speed and distance
            if (BikePowerSensorsHelper.isWheelRevolutionDataSupported(mBikePowerSensorFlags)) {
                TextView tv = new TextView(getContext());
                tv.setText(R.string.bike_power__wheel_revolution_data);
                tv.setTextAppearance(getContext(), android.R.style.TextAppearance_Medium);
                llPowerSensors.addView(tv);
            }

            if (BikePowerSensorsHelper.isWheelSpeedDataSupported(mBikePowerSensorFlags) && BikePowerSensorsHelper.isWheelDistanceDataSupported(mBikePowerSensorFlags)) {
                TextView tv = new TextView(getContext());
                tv.setText(R.string.bike_power__wheel_speed_and_distance_data);
                tv.setTextAppearance(getContext(), android.R.style.TextAppearance_Medium);
                llPowerSensors.addView(tv);
            } else if (BikePowerSensorsHelper.isWheelSpeedDataSupported(mBikePowerSensorFlags)) {
                TextView tv = new TextView(getContext());
                tv.setText(R.string.bike_power__wheel_speed_data);
                tv.setTextAppearance(getContext(), android.R.style.TextAppearance_Medium);
                llPowerSensors.addView(tv);
            } else if (BikePowerSensorsHelper.isWheelDistanceDataSupported(mBikePowerSensorFlags)) {
                TextView tv = new TextView(getContext());
                tv.setText(R.string.bike_power__wheel_distance_data);
                tv.setTextAppearance(getContext(), android.R.style.TextAppearance_Medium);
                llPowerSensors.addView(tv);
            }

            // cadence
            if (BikePowerSensorsHelper.isCrankRevolutionDataSupported(mBikePowerSensorFlags)) {
                TextView tv = new TextView(getContext());
                tv.setText(R.string.bike_power__crank_revolution_data);
                tv.setTextAppearance(getContext(), android.R.style.TextAppearance_Medium);
                llPowerSensors.addView(tv);
            }

            // balance (related to effectiveness)
            if (BikePowerSensorsHelper.isPowerBalanceSupported(mBikePowerSensorFlags)) {
                TextView tv = new TextView(getContext());
                tv.setText(R.string.bike_power__pedal_power_balance);
                tv.setTextAppearance(getContext(), android.R.style.TextAppearance_Medium);
                llPowerSensors.addView(tv);

                if (protocol == Protocol.BLUETOOTH_LE) {
                    cbDoublePowerBalanceValues.setChecked(BikePowerSensorsHelper.doublePowerBalanceValues(mBikePowerSensorFlags));
                } else {
                    cbDoublePowerBalanceValues.setVisibility(View.GONE);
                }
                cbInvertPowerBalanceValues.setChecked(BikePowerSensorsHelper.invertPowerBalanceValues(mBikePowerSensorFlags));

            } else {
                mainDialog.findViewById(R.id.llPedalPowerBalanceCorrection).setVisibility(View.GONE);
            }

            // effectiveness values
            if (BikePowerSensorsHelper.isExtremeMagnitudesSupported(mBikePowerSensorFlags)) {
                TextView tv = new TextView(getContext());
                tv.setTextColor(getResources().getColor(R.color.bright_grey));
                tv.setText(R.string.bike_power__extreme_magnitudes);
                tv.setTextAppearance(getContext(), android.R.style.TextAppearance_Medium);
                llPowerSensors.addView(tv);
            }

            if (BikePowerSensorsHelper.isExtremeAnglesSupported(mBikePowerSensorFlags)) {
                TextView tv = new TextView(getContext());
                tv.setTextColor(getResources().getColor(R.color.bright_grey));
                tv.setText(R.string.bike_power__extreme_angles);
                tv.setTextAppearance(getContext(), android.R.style.TextAppearance_Medium);
                llPowerSensors.addView(tv);
            }

            if (BikePowerSensorsHelper.isDeadSpotAnglesSupported(mBikePowerSensorFlags)) {
                TextView tv = new TextView(getContext());
                tv.setTextColor(getResources().getColor(R.color.bright_grey));
                tv.setText(R.string.bike_power__top_and_bottom_dead_sport_angles);
                tv.setTextAppearance(getContext(), android.R.style.TextAppearance_Medium);
                llPowerSensors.addView(tv);
            }

            if (BikePowerSensorsHelper.isPedalSmoothnessSupported(mBikePowerSensorFlags)) {
                TextView tv = new TextView(getContext());
                tv.setText(R.string.bike_power__pedal_smoothness);
                tv.setTextAppearance(getContext(), android.R.style.TextAppearance_Medium);
                llPowerSensors.addView(tv);
            }

            if (BikePowerSensorsHelper.isTorqueEffectivenessSupported(mBikePowerSensorFlags)) {
                TextView tv = new TextView(getContext());
                tv.setText(R.string.bike_power__torque_effectiveness);
                tv.setTextAppearance(getContext(), android.R.style.TextAppearance_Medium);
                llPowerSensors.addView(tv);
            }

            // accumulated values
            if (BikePowerSensorsHelper.isAccumulatedTorqueSupported(mBikePowerSensorFlags)) {
                TextView tv = new TextView(getContext());
                tv.setTextColor(getResources().getColor(R.color.bright_grey));
                tv.setText(R.string.bike_power__accumulated_torque);
                tv.setTextAppearance(getContext(), android.R.style.TextAppearance_Medium);
                llPowerSensors.addView(tv);
            }

            if (BikePowerSensorsHelper.isAccumulatedEnergySupported(mBikePowerSensorFlags)) {
                TextView tv = new TextView(getContext());
                tv.setTextColor(getResources().getColor(R.color.bright_grey));
                tv.setText(R.string.bike_power__accumulated_energy);
                tv.setTextAppearance(getContext(), android.R.style.TextAppearance_Medium);
                llPowerSensors.addView(tv);
            }

        }

        // set values of the views
        etDeviceName.setText(cursor.getString(cursor.getColumnIndexOrThrow(DevicesDbHelper.NAME)));
        cbPaired.setChecked(cursor.getInt(cursor.getColumnIndexOrThrow(DevicesDbHelper.PAIRED)) > 0);

        String manufacturer = cursor.getString(cursor.getColumnIndexOrThrow(DevicesDbHelper.MANUFACTURER_NAME));
        tvManufacturer.setText(manufacturer == null ? getActivity().getString(R.string.unknown_manufacturer) : manufacturer);

        String lastSeen = cursor.getString(cursor.getColumnIndexOrThrow(DevicesDbHelper.LAST_ACTIVE));
        tvLastSeen.setText(lastSeen == null ? getActivity().getString(R.string.unknown_last_seen_date) : lastSeen);

        int batteryPercentage = cursor.getInt(cursor.getColumnIndex(DevicesDbHelper.LAST_BATTERY_PERCENTAGE));
        if (DEBUG) Log.i(TAG, "batteryPercentage=" + batteryPercentage);
        ivBatteryStatus.setImageResource(BatteryStatusHelper.getBatteryStatusImageId(batteryPercentage));

        mOriginalCalibrationFactor = cursor.getDouble(cursor.getColumnIndexOrThrow(DevicesDbHelper.CALIBRATION_FACTOR)) + "";
        setCalibrationFactor(mOriginalCalibrationFactor);

        cursor.close();
        databaseManager.closeDatabase(); // db.close();

//        cbPaired.setOnClickListener(new OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                // inform everybody that pairing has changed
//                Intent intent = new Intent(BANALService.PAIRING_CHANGED);
//                intent.putExtra(BANALService.DEVICE_ID, mDeviceID);
//                intent.putExtra(BANALService.PAIRED, cbPaired.isChecked());
//                getActivity().sendBroadcast(intent);
//            }
//        });

        // configure spinners and buttons
        if (spinnerWheelCircumference != null) {

            spinnerWheelCircumference.setAdapter(CIRCUMFERENCE_ARRAY_ADAPTER);
            // spinner.setSelection(textSize2Pos(cursor.getInt(cursor.getColumnIndex(FlexibleGenericViewDbHelper.TEXT_SIZE))));

            spinnerWheelCircumference.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position != 0) {
                        setCalibrationFactor(CIRCUMFERENCE_VALUE[position] + "");
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {
                }
            });
        }


        EquipmentDbHelper equipmentDbHelper = new EquipmentDbHelper(getActivity());
        List<String> equipmentList = equipmentDbHelper.getEquipment(mDeviceType.getSportType());
        List<String> linkedEquipmentList = equipmentDbHelper.getLinkedEquipmentFromDeviceId((int) mDeviceID);

        if (spinnerEquipment != null) {
            if (equipmentList != null && equipmentList.size() >= 1) {
                mHaveEquipmentList = true;
                spinnerEquipment.setItems(equipmentList);
                spinnerEquipment.setSelection(linkedEquipmentList);
            } else {
                spinnerEquipment.setVisibility(View.INVISIBLE);
            }
        }

        if (bEditCalibrationFactor != null) {
            bEditCalibrationFactor.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Create an instance of the dialog fragment and show it
                    SetCalibrationFactorDialogFragment dialog = SetCalibrationFactorDialogFragment.newInstance(etCalibrationFactor.getText().toString(), mCalibrationFactorTitle, mCalibrationFactorName);
                    dialog.setNewCalibrationFactorListener(new SetCalibrationFactorDialogFragment.NewCalibrationFactorListener() {
                        @Override
                        public void newCalibrationFactor(String calibrationFactor) {
                            setCalibrationFactor(calibrationFactor);
                        }
                    });
                    dialog.show(getFragmentManager(), SetCalibrationFactorDialogFragment.TAG);
                }
            });
        }

        // finally, add the action buttons
        builder.setPositiveButton(getString(R.string.OK), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                saveEverything();
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                EditDeviceDialogFragment.this.getDialog().cancel();
            }
        });
        return builder.create();

    }

    protected void saveEverything() {
        if (DEBUG) Log.d(TAG, "saveEverything");


        ContentValues values = new ContentValues();

        values.put(DevicesDbHelper.PAIRED, cbPaired.isChecked());
        values.put(DevicesDbHelper.NAME, etDeviceName.getText().toString());

        double newCalibrationFactor = 1;
        if (etCalibrationFactor != null) {
            String calibrationFactor = etCalibrationFactor.getText().toString();
            values.put(DevicesDbHelper.CALIBRATION_FACTOR, calibrationFactor);
            if (!mOriginalCalibrationFactor.equals(calibrationFactor)) {
                mCalibrationFactorChanged = true;
                newCalibrationFactor = Double.parseDouble(calibrationFactor);
            }

        }

        DevicesDatabaseManager databaseManager = DevicesDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        try {
            db.update(DevicesDbHelper.DEVICES,
                    values,
                    DevicesDbHelper.C_ID + "=" + mDeviceID,
                    null);
        } catch (SQLException e) {
            // TODO: use Toast?
            Log.e(TAG, "Error while writing" + e.toString());
        }
        databaseManager.closeDatabase(); // db.close();

        if (spinnerEquipment != null && mHaveEquipmentList) {
            if (DEBUG) Log.d(TAG, "save equipment links");

            new EquipmentDbHelper(getActivity()).setEquipmentLinks((int) mDeviceID, spinnerEquipment.getSelectedStrings());
        }

        if (mDeviceType == DeviceType.BIKE_POWER) {
            mBikePowerSensorFlags = cbDoublePowerBalanceValues.isChecked() ? BikePowerSensorsHelper.addDoublePowerBalanceValues(mBikePowerSensorFlags) : BikePowerSensorsHelper.removeDoublePowerBalanceValues(mBikePowerSensorFlags);
            mBikePowerSensorFlags = cbInvertPowerBalanceValues.isChecked() ? BikePowerSensorsHelper.addInvertPowerBalanceValues(mBikePowerSensorFlags) : BikePowerSensorsHelper.removeInvertPowerBalanceValues(mBikePowerSensorFlags);
            DevicesDatabaseManager.putBikePowerSensorFlags(mDeviceID, mBikePowerSensorFlags);
        }

        // TODO: store original value and do this only when necessary???
        // inform everybody that pairing has changed
        Intent intent = new Intent(BANALService.PAIRING_CHANGED);
        intent.putExtra(BANALService.DEVICE_ID, mDeviceID);
        intent.putExtra(BANALService.PAIRED, cbPaired.isChecked());
        getActivity().sendBroadcast(intent);

        if (mCalibrationFactorChanged) {
            intent = new Intent(BANALService.CALIBRATION_FACTOR_CHANGED);
            intent.putExtra(BANALService.DEVICE_ID, mDeviceID);
            intent.putExtra(BANALService.CALIBRATION_FACTOR, newCalibrationFactor);
            getActivity().sendBroadcast(intent);
        }

        // mOnEditDeviceFinishedListener.onEditDeviceFinished();
    }

    public void setCalibrationFactor(String calibrationFactor) {
        if (etCalibrationFactor != null) {
            etCalibrationFactor.setText(calibrationFactor);
        }
    }


    int getLayout(Protocol protocol, DeviceType deviceType) {
        switch (deviceType) {
            case BIKE_POWER:
                mCalibrationFactorTitle = getString(R.string.Wheel_Circumference);
                mCalibrationFactorName = getString(R.string.wheel_circumference);
                CIRCUMFERENCE_TEXT[0] = getString(R.string.wheel_circumference);
                return R.layout.edit_device_bike_power;

            case BIKE_SPEED_AND_CADENCE:
            case BIKE_SPEED:
                mCalibrationFactorTitle = getString(R.string.Wheel_Circumference);
                mCalibrationFactorName = getString(R.string.wheel_circumference);
                CIRCUMFERENCE_TEXT[0] = getString(R.string.wheel_circumference);
                return R.layout.edit_device_bike;

            case BIKE_CADENCE:
                return R.layout.edit_device_bike_cadence;

            case RUN_SPEED:
                mCalibrationFactorTitle = getString(R.string.Calibration_Factor);
                mCalibrationFactorName = getString(R.string.calibration_factor);
                return R.layout.edit_device_run;

            default:
                return R.layout.edit_device_generic;
        }
    }

}