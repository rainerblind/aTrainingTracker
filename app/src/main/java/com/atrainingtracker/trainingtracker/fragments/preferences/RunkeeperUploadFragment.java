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

package com.atrainingtracker.trainingtracker.fragments.preferences;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.CheckBoxPreference;
import androidx.preference.PreferenceManager;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.onlinecommunities.BaseGetAccessTokenActivity;
import com.atrainingtracker.trainingtracker.onlinecommunities.runkeeper.RunkeeperGetAccessTokenActivity;

/**
 * Created by rainer on 01.02.16.
 */
public class RunkeeperUploadFragment extends androidx.preference.PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final int GET_RUNKEEPER_ACCESS_TOKEN = 4;
    private static final boolean DEBUG = TrainingApplication.getDebug(false);
    private static final String TAG = RunkeeperUploadFragment.class.getName();
    private CheckBoxPreference mRunkeeperUpload;

    private SharedPreferences mSharedPreferences;


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        if (DEBUG) Log.i(TAG, "onCreatePreferences(savedInstanceState, rootKey=" + rootKey + ")");

        setPreferencesFromResource(R.xml.prefs, rootKey);

        mRunkeeperUpload = this.getPreferenceScreen().findPreference(TrainingApplication.SP_UPLOAD_TO_RUNKEEPER);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(TAG, "onResume()");

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        // Unregister the listener whenever a key changes
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (DEBUG) Log.i(TAG, "onSharedPreferenceChanged: key=" + key);

        if (TrainingApplication.SP_UPLOAD_TO_RUNKEEPER.equals(key)) {
            if (!TrainingApplication.uploadToRunKeeper()) {
                if (DEBUG) Log.d(TAG, "deleting Runkeeper token");
                TrainingApplication.deleteRunkeeperToken();
            } else {
                startActivityForResult(new Intent(getActivity(), RunkeeperGetAccessTokenActivity.class), GET_RUNKEEPER_ACCESS_TOKEN);
            }
        }

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (DEBUG) Log.i(TAG, "onActivityResult: requestCode=" + requestCode);

        switch (requestCode) {
            case (GET_RUNKEEPER_ACCESS_TOKEN):
                if (DEBUG) Log.i(TAG, "result from runkeeper");
                if (resultCode == Activity.RESULT_OK) {
                    if (DEBUG) Log.i(TAG, "result_ok");
                    String accessToken = data.getStringExtra(BaseGetAccessTokenActivity.ACCESS_TOKEN);
                    TrainingApplication.setRunkeeperToken(accessToken);
                    // Log.d(TAG, "we got the access token: " + accessToken);
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    if (DEBUG) Log.i(TAG, "result_canceled");
                    // Log.d(TAG, "WTF, something went wrong");
                    TrainingApplication.deleteRunkeeperToken();
                    mRunkeeperUpload.setChecked(false);
                }

                break;
        }
    }
}
