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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.filters.FilterType;
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager;
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager.TrackingViewsDbHelper;

public class ConfigureFilterDialogFragment
        extends DialogFragment {
    public static final String TAG = ConfigureFilterDialogFragment.class.getName();
    public static final String FILTERS_CHANGED_INTENT = "FILTERS_CHANGED_INTENT";
    private static final boolean DEBUG = BANALService.DEBUG & false;
    long mRowId;
    FilterType mFilterType;
    double mFilterConstant;

    Spinner mSpinnerFilterType;
    EditText mEditTextFilterConstant;
    Spinner mSpinnerUnit;
    LinearLayout mLLUnit, mLLConstant;
    View mDivider;
    TextView mTvFilterDetails;

    public static ConfigureFilterDialogFragment newInstance(long rowId, FilterType filterType, double filterConstant) {
        ConfigureFilterDialogFragment configureFilterDialogFragment = new ConfigureFilterDialogFragment();

        Bundle args = new Bundle();
        args.putLong(TrackingViewsDbHelper.ROW_ID, rowId);
        args.putString(TrackingViewsDbHelper.FILTER_TYPE, filterType.name());
        args.putDouble(TrackingViewsDbHelper.FILTER_CONSTANT, filterConstant);
        configureFilterDialogFragment.setArguments(args);

        return configureFilterDialogFragment;
    }

    public static String getFilterSummary(Context context, FilterType filterType, double filterConstant) {
        switch (filterType) {
            case INSTANTANEOUS: // instantaneous
                return context.getString(R.string.filter_instantaneous);

            case AVERAGE: // average (entire workout)
                return context.getString(R.string.filter_average);

            case MOVING_AVERAGE_TIME:
                if (filterConstant % 60 == 0) { // 5 min moving average
                    return (int) (filterConstant / 60) + " " + context.getString(R.string.units_minutes) + " " + context.getString(R.string.filter_moving_average);
                } else { // 5 sec moving average
                    return (int) filterConstant + " " + context.getString(R.string.units_seconds) + " " + context.getString(R.string.filter_moving_average);
                }

            case MOVING_AVERAGE_NUMBER: // 5 samples moving average
                return filterConstant + " " + context.getString(R.string.units_samples) + " " + context.getString(R.string.filter_moving_average);

            case EXPONENTIAL_SMOOTHING:  // exponential smoothing with \alpha = 0.9
                return context.getString(R.string.filter_exponential_smoothing_format, filterConstant);

            case MAX_VALUE:
                return context.getString(R.string.max);

            default:
                return null;
        }

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreate()");

        mRowId = getArguments().getLong(TrackingViewsDbHelper.ROW_ID);
        mFilterType = FilterType.valueOf(getArguments().getString(TrackingViewsDbHelper.FILTER_TYPE));
        mFilterConstant = getArguments().getDouble(TrackingViewsDbHelper.FILTER_CONSTANT);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (DEBUG) Log.i(TAG, "onCreateDialog");

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        TextView title = new TextView(getActivity());
        // You Can Customise your Title here
        title.setText(R.string.configure_filter);
//        title.setBackgroundColor(getResources().getColor(R.color.my_blue));
        title.setPadding(10, 10, 10, 10);
        title.setGravity(Gravity.CENTER);
//        title.setTextColor(getResources().getColor(R.color.my_white));
        title.setTextSize(20);

        builder.setCustomTitle(title);
        // builder.setTitle(getString(R.string.Lap_NR, lapNr));

        // builder.setTitle(R.string.configure_filter);
        // builder.setIcon(UIHelper.getIconId(mDeviceType, protocol));

        // create the view
        final View mainDialog = getActivity().getLayoutInflater().inflate(R.layout.config_filter, null);
        builder.setView(mainDialog);

        // find views
        mSpinnerFilterType = mainDialog.findViewById(R.id.spinnerFilter);
        mEditTextFilterConstant = mainDialog.findViewById(R.id.editTextFilterConstant);
        mSpinnerUnit = mainDialog.findViewById(R.id.spinnerFilterUnit);
        mLLUnit = mainDialog.findViewById(R.id.llFilterUnit);
        mLLConstant = mainDialog.findViewById(R.id.llFilterConstant);
        mDivider = mainDialog.findViewById(R.id.divider);
        mTvFilterDetails = mainDialog.findViewById(R.id.tvFilterDetails);


        // configure the views
        configureViews();

        // finally, add the action buttons
        builder.setPositiveButton(getString(R.string.OK), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                getActivity().sendBroadcast(new Intent(FILTERS_CHANGED_INTENT));
                saveEverything();
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                ConfigureFilterDialogFragment.this.getDialog().cancel();
            }
        });
        return builder.create();
    }

    protected void configureViews() {
        if (DEBUG) Log.i(TAG, "configureViews()");
        boolean selectMinutes = false;

        // set the filter constant
        if (mFilterType == FilterType.MOVING_AVERAGE_TIME) {
            if ((mFilterConstant % 60) == 0) {                                                    // use minutes
                mEditTextFilterConstant.setText((int) mFilterConstant / 60 + "");  // TODO: better formatting?
                selectMinutes = true;
            } else {
                mEditTextFilterConstant.setText((int) mFilterConstant + "");  // TODO: better formatting?
            }
        } else {
            mEditTextFilterConstant.setText(mFilterConstant + "");  // TODO: better formatting?
        }
        mEditTextFilterConstant.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                configureFilterConstantAndUnit();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        // configure the Filter Type spinner
        ArrayAdapter<CharSequence> adapterFilterType = ArrayAdapter.createFromResource(getContext(), R.array.Filter_Types_UI, android.R.layout.simple_list_item_1);
        mSpinnerFilterType.setAdapter(adapterFilterType);

        int selected_index = 0;
        switch (mFilterType) {
            case INSTANTANEOUS:
                selected_index = 0;
                break;
            case AVERAGE:
                selected_index = 1;
                break;
            case MOVING_AVERAGE_TIME:
                selected_index = 2;
                break;  // on the ui, we only have moving average and
            case MOVING_AVERAGE_NUMBER:
                selected_index = 2;
                break;  // use the unit to distinguish between them
            case EXPONENTIAL_SMOOTHING:
                selected_index = 3;
                break;
            case MAX_VALUE:
                selected_index = 4;
                break;
        }

        mSpinnerFilterType.setSelection(selected_index, false);
        mSpinnerFilterType.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                configureFilterConstantAndUnit();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        // fill the units spinner
        ArrayAdapter<CharSequence> adapterTimeUnit = ArrayAdapter.createFromResource(getContext(), R.array.Time_Units_UI, android.R.layout.simple_list_item_1);
        mSpinnerUnit.setAdapter(adapterTimeUnit);
        if (selectMinutes) {
            mSpinnerUnit.setSelection(1);
        }
        mSpinnerUnit.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                configureFilterConstantAndUnit();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        // we have to reconfigure the constant and units when the filter type changes.  Thus, we use an extra method for this:
        configureFilterConstantAndUnit();
    }

    protected void configureFilterConstantAndUnit() {
        if (DEBUG) Log.i(TAG, "configureFilterConstantAndUnit");

        getValuesFromUI();

        switch (mFilterType) {
            case INSTANTANEOUS:  // no constant, no units
                mLLConstant.setVisibility(View.GONE);
                mLLUnit.setVisibility(View.GONE);
                mTvFilterDetails.setText(R.string.filter_details__instantaneous);
                break;

            case AVERAGE:        // no constant, no units
                mLLConstant.setVisibility(View.GONE);
                mLLUnit.setVisibility(View.GONE);
                mTvFilterDetails.setText(R.string.filter_details__average);
                break;

            case MAX_VALUE:      // no constant, no units
                mLLConstant.setVisibility(View.GONE);
                mLLUnit.setVisibility(View.GONE);
                mTvFilterDetails.setText(R.string.filter_details__max);
                break;

            case EXPONENTIAL_SMOOTHING:  // no units
                mLLConstant.setVisibility(View.VISIBLE);
                mLLUnit.setVisibility(View.GONE);
                mEditTextFilterConstant.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);
                mTvFilterDetails.setText(R.string.filter_details__exponential_smoothing);
                break;

            case MOVING_AVERAGE_TIME:    // units either sec or min
                mLLConstant.setVisibility(View.VISIBLE);
                mLLUnit.setVisibility(View.VISIBLE);
                mEditTextFilterConstant.setInputType(InputType.TYPE_CLASS_NUMBER);   // only Integer for Moving Average
                if ((mFilterConstant % 60) != 0) {                                                  // use seconds
                    mSpinnerUnit.setSelection(0);
                    mTvFilterDetails.setText(getString(R.string.filter_details__moving_average_time, (int) mFilterConstant, getString(R.string.units_seconds_long)));
                } else {
                    mSpinnerUnit.setSelection(1);                                                   // use minutes
                    mTvFilterDetails.setText(getString(R.string.filter_details__moving_average_time, (int) mFilterConstant / 60, getString(R.string.units_minutes_long)));
                }

                break;

            case MOVING_AVERAGE_NUMBER:  // units will be samples
                mLLConstant.setVisibility(View.VISIBLE);
                mLLUnit.setVisibility(View.VISIBLE);
                mEditTextFilterConstant.setInputType(InputType.TYPE_CLASS_NUMBER);   // only Integer for Moving Average
                mSpinnerUnit.setSelection(2);                                                       // samples
                mTvFilterDetails.setText(getString(R.string.filter_details__moving_average_number, (int) mFilterConstant));
                break;
        }
    }

    protected void getValuesFromUI() {
        switch (mSpinnerFilterType.getSelectedItemPosition()) {
            case 0:
                mFilterType = FilterType.INSTANTANEOUS;
                break;

            case 1:
                mFilterType = FilterType.AVERAGE;
                break;

            case 2:
                if (mSpinnerUnit.getSelectedItemPosition() == 2) {
                    mFilterType = FilterType.MOVING_AVERAGE_NUMBER;
                } else {
                    mFilterType = FilterType.MOVING_AVERAGE_TIME;
                }
                break;

            case 3:
                mFilterType = FilterType.EXPONENTIAL_SMOOTHING;
                break;

            case 4:
                mFilterType = FilterType.MAX_VALUE;
                break;
        }

        String filterConstant_String = mEditTextFilterConstant.getText().toString();
        try {
            mFilterConstant = Double.parseDouble(filterConstant_String);
        } catch (Exception e) {
            // TODO: do something useful here...
        }

        if (mFilterType == FilterType.MAX_VALUE
                || mFilterType == FilterType.AVERAGE
                || mFilterType == FilterType.INSTANTANEOUS) {
            mFilterConstant = 1;
        } else if (mFilterType == FilterType.EXPONENTIAL_SMOOTHING) {
            // guarantee that 0 <= mFilterConstant <= 1...
            if (mFilterConstant > 1) {
                mFilterConstant = 1;
            } else if (mFilterConstant < 0) {
                mFilterConstant = 0.1;
            }
        } else {
            // minimal value is 1
            if (mFilterConstant < 1) {
                mFilterConstant = 1;
            }

            if (mFilterType == FilterType.MOVING_AVERAGE_TIME
                    && mSpinnerUnit.getSelectedItemPosition() == 1) { // minutes selected
                mFilterConstant *= 60;                           // minutes -> seconds
                if (DEBUG) Log.i(TAG, "multiplied by 60...");
            }
        }
    }

    protected void saveEverything() {
        if (DEBUG) Log.d(TAG, "saveEverything");

        getValuesFromUI();

        ContentValues values = new ContentValues();
        values.put(TrackingViewsDbHelper.FILTER_TYPE, mFilterType.name());
        values.put(TrackingViewsDbHelper.FILTER_CONSTANT, mFilterConstant);

        TrackingViewsDatabaseManager databaseManager = TrackingViewsDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        try {
            db.update(TrackingViewsDbHelper.ROWS_TABLE,
                    values,
                    TrackingViewsDbHelper.ROW_ID + "=" + mRowId,
                    null);
        } catch (SQLException e) {
            // TODO: use Toast?
            Log.e(TAG, "Error while writing" + e.toString());
        }
        databaseManager.closeDatabase(); // db.close();

        // TODO: inform everyone that the Filters have changed???
    }

}