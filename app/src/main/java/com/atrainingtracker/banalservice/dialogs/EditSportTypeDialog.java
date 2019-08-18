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

package com.atrainingtracker.banalservice.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.SportType;
import com.atrainingtracker.trainingtracker.MyHelper;

import java.util.Arrays;
import java.util.List;

/**
 * Created by rainer on 05.01.17.
 */

public class EditSportTypeDialog extends DialogFragment {
    public static final String TAG = EditSportTypeDialog.class.getName();
    public static final String SPORT_TYPE_CHANGED_INTENT = "SPORT_TYPE_CHANGED_INTENT";
    private static final boolean DEBUG = BANALService.DEBUG && false;
    private static final String SPORT_TYPE_ID = "SPORT_TYPE_ID";
    protected EditText mEtName, mEtMinAvgSpeed, mEtMaxAvgSpeed;
    protected Spinner mSpBSportType, mSpStrava, mSpRunkeeper, mSpTrainingPeaks, mSpTCX, mSpGC;
    private long mSportTypeId;

    public static EditSportTypeDialog newInstance(long id) {
        if (DEBUG) Log.i(TAG, "newInstance");

        EditSportTypeDialog fragment = new EditSportTypeDialog();

        Bundle args = new Bundle();
        args.putLong(SPORT_TYPE_ID, id);
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

        mSportTypeId = getArguments().getLong(SPORT_TYPE_ID);
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        // get the values
        BSportType bSportType = SportType.DEFAULT_BASE_SPORT_TYPE;
        String uiName = SportType.getDefaultUiName(getContext());
        String gcName = SportType.DEFAULT_GOLDEN_CHEETAH_NAME;
        String tcxName = SportType.DEFAULT_TCX_NAME;
        String stravaName = SportType.DEFAULT_STRAVA_NAME;
        String runkeeperName = SportType.DEFAULT_RUNKEEPER_NAME;
        String tpName = SportType.DEFAULT_TRAINING_PEAKS_NAME;
        double minAvgSpeed = SportType.DEFAULT_MIN_AVG_SPEED;
        double maxAvgSpeed = SportType.DEFAULT_MAX_AVG_SPEED;

        SQLiteDatabase db = SportTypeDatabaseManager.getInstance().getOpenDatabase();

        Cursor cursor = db.query(SportType.TABLE,  // String table,
                null,                              // String[] columns,
                SportType.C_ID + " =? ",           // String selection,
                new String[]{Long.toString(mSportTypeId)},  // String[] selectionArgs,
                null, null, null);                 // String groupBy, String having, String orderBy

        if (cursor.moveToFirst()) {
            bSportType = BSportType.valueOf(cursor.getString(cursor.getColumnIndex(SportType.BASE_SPORT_TYPE)));
            uiName = cursor.getString(cursor.getColumnIndex(SportType.UI_NAME));
            gcName = cursor.getString(cursor.getColumnIndex(SportType.GOLDEN_CHEETAH_NAME));
            tcxName = cursor.getString(cursor.getColumnIndex(SportType.TCX_NAME));
            stravaName = cursor.getString(cursor.getColumnIndex(SportType.STRAVA_NAME));
            runkeeperName = cursor.getString(cursor.getColumnIndex(SportType.RUNKEEPER_NAME));
            tpName = cursor.getString(cursor.getColumnIndex(SportType.TRAINING_PEAKS_NAME));
            minAvgSpeed = cursor.getDouble(cursor.getColumnIndex(SportType.MIN_AVG_SPEED));
            maxAvgSpeed = cursor.getDouble(cursor.getColumnIndex(SportType.MAX_AVG_SPEED));
        }
        cursor.close();
        SportTypeDatabaseManager.getInstance().closeDatabase();

        // now, create the view
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.edit_sport_type);

        // create the main view
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.edit_sport_type, null);
        builder.setView(view);

        mEtName = view.findViewById(R.id.est_etName);
        mEtMinAvgSpeed = view.findViewById(R.id.est_etMinSpeed);
        mEtMaxAvgSpeed = view.findViewById(R.id.est_etMaxSpeed);
        mSpBSportType = view.findViewById(R.id.est_sBSportType);
        mSpStrava = view.findViewById(R.id.est_sStrava);
        mSpRunkeeper = view.findViewById(R.id.est_sRunkeeper);
        mSpTrainingPeaks = view.findViewById(R.id.est_sTrainingPeaks);
        mSpTCX = view.findViewById(R.id.est_sTCX);
        mSpGC = view.findViewById(R.id.est_sGC);


        // set the units
        TextView tvSpeedUnit = view.findViewById(R.id.est_tvUnitSpeed);
        tvSpeedUnit.setText(MyHelper.getSpeedUnitNameId());
        tvSpeedUnit = view.findViewById(R.id.est_tvUnitSpeed2);
        tvSpeedUnit.setText(MyHelper.getSpeedUnitNameId());

        // configure the main view
        mEtName.setText(uiName);
        mEtMinAvgSpeed.setText(String.format("%.1f", MyHelper.mps2userUnit(minAvgSpeed)));
        mEtMaxAvgSpeed.setText(String.format("%.1f", MyHelper.mps2userUnit(maxAvgSpeed)));

        if (SportTypeDatabaseManager.canDelete(mSportTypeId)) {

            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(), R.array.Basic_Sport_Types, android.R.layout.simple_list_item_1);
            mSpBSportType.setAdapter(adapter);
            mSpBSportType.setSelection(bSportType.ordinal());

            List<String> foo = Arrays.asList(getResources().getStringArray(R.array.Strava_Sport_Types_Strava_Names));
            ArrayAdapter<CharSequence> stravaAdapter = ArrayAdapter.createFromResource(getContext(), R.array.Strava_Sport_Types_UI_Names, android.R.layout.simple_list_item_1);
            mSpStrava.setAdapter(stravaAdapter);
            mSpStrava.setSelection(foo.indexOf(stravaName));

            ArrayAdapter<CharSequence> runkeeperAdapter = ArrayAdapter.createFromResource(getContext(), R.array.Runkeeper_Sport_Types, android.R.layout.simple_list_item_1);
            mSpRunkeeper.setAdapter(runkeeperAdapter);
            mSpRunkeeper.setSelection(runkeeperAdapter.getPosition(runkeeperName));

            ArrayAdapter<CharSequence> tpAdapter = ArrayAdapter.createFromResource(getContext(), R.array.Training_Peaks_Sport_Types, android.R.layout.simple_list_item_1);
            mSpTrainingPeaks.setAdapter(tpAdapter);
            mSpTrainingPeaks.setSelection(tpAdapter.getPosition(tpName));

            ArrayAdapter<CharSequence> tcxAdapter = ArrayAdapter.createFromResource(getContext(), R.array.TCX_Sport_Types, android.R.layout.simple_list_item_1);
            mSpTCX.setAdapter(tcxAdapter);
            mSpTCX.setSelection(tcxAdapter.getPosition(tcxName));

            ArrayAdapter<CharSequence> gcAdapter = ArrayAdapter.createFromResource(getContext(), R.array.GC_Sport_Types, android.R.layout.simple_list_item_1);
            mSpGC.setAdapter(gcAdapter);
            mSpGC.setSelection(gcAdapter.getPosition(gcName));
        } else {
            view.findViewById(R.id.est_tvBSportType).setVisibility(View.GONE);
            mSpBSportType.setVisibility(View.GONE);
            view.findViewById(R.id.est_seperator1).setVisibility(View.GONE);
            view.findViewById(R.id.est_tvOnlineCommunities).setVisibility(View.GONE);
            view.findViewById(R.id.est_tvStrava).setVisibility(View.GONE);
            mSpStrava.setVisibility(View.GONE);
            view.findViewById(R.id.est_tvRunkeeper).setVisibility(View.GONE);
            mSpRunkeeper.setVisibility(View.GONE);
            view.findViewById(R.id.est_tvTrainingPeaks).setVisibility(View.GONE);
            mSpTrainingPeaks.setVisibility(View.GONE);
            view.findViewById(R.id.est_seperator2).setVisibility(View.GONE);
            view.findViewById(R.id.est_tvExportFiles).setVisibility(View.GONE);
            view.findViewById(R.id.est_tvTCX).setVisibility(View.GONE);
            mSpTCX.setVisibility(View.GONE);
            view.findViewById(R.id.est_tvGC).setVisibility(View.GONE);
            mSpGC.setVisibility(View.GONE);
        }


        builder.setPositiveButton(R.string.OK,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Log.i(TAG, "OK clicked");

                        saveSportTypes();

                        dialog.dismiss();
                    }
                });

        builder.setNegativeButton(R.string.Cancel,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Log.i(TAG, "Cancel clicked");
                        dialog.dismiss();
                    }
                });

        return builder.create();
    }

    private void saveSportTypes() {
        ContentValues contentValues = new ContentValues();
        contentValues.put(SportType.UI_NAME, mEtName.getText().toString());
        contentValues.put(SportType.MIN_AVG_SPEED, MyHelper.UserUnit2mps(MyHelper.string2Double(mEtMinAvgSpeed.getText().toString())));
        contentValues.put(SportType.MAX_AVG_SPEED, MyHelper.UserUnit2mps(MyHelper.string2Double(mEtMaxAvgSpeed.getText().toString())));
        if (SportTypeDatabaseManager.canDelete(mSportTypeId)) {
            contentValues.put(SportType.BASE_SPORT_TYPE, BSportType.values()[mSpBSportType.getSelectedItemPosition()].name());
            contentValues.put(SportType.GOLDEN_CHEETAH_NAME, (String) mSpGC.getSelectedItem());
            contentValues.put(SportType.TCX_NAME, (String) mSpTCX.getSelectedItem());
            String stravaName = Arrays.asList(getResources().getStringArray(R.array.Strava_Sport_Types_Strava_Names)).get(mSpStrava.getSelectedItemPosition());
            contentValues.put(SportType.STRAVA_NAME, stravaName);
            contentValues.put(SportType.RUNKEEPER_NAME, (String) mSpRunkeeper.getSelectedItem());
            contentValues.put(SportType.TRAINING_PEAKS_NAME, (String) mSpTrainingPeaks.getSelectedItem());
        }

        SQLiteDatabase db = SportTypeDatabaseManager.getInstance().getOpenDatabase();

        if (mSportTypeId < 0) {  // create an entry
            db.insert(SportType.TABLE, null, contentValues);
        } else {
            db.update(SportType.TABLE, contentValues,
                    SportType.C_ID + "=?", new String[]{Long.toString(mSportTypeId)});
        }

        SportTypeDatabaseManager.getInstance().closeDatabase();

        getContext().sendBroadcast(new Intent(SPORT_TYPE_CHANGED_INTENT));
    }

}
