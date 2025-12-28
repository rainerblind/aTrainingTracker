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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.onlinecommunities.BaseGetAccessTokenActivity;
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaDeauthorizationThread;
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaEquipmentSynchronizeThread;
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaGetAccessTokenActivity;
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaOAuthCallbackActivity;
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaSegmentsHelper;

/**
 * Created by rainer on 01.02.16.
 */
public class StravaUploadFragment extends androidx.preference.PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final int GET_STRAVA_ACCESS_TOKEN = 3;
    private static final boolean DEBUG = TrainingApplication.getDebug(true);
    private static final String TAG = StravaUploadFragment.class.getName();
    private CheckBoxPreference mStravaUpload;
    private Preference mUpdateStravaEquipment;

    private SharedPreferences mSharedPreferences;

    private enum RequestTokenState {
        REQUESTING,
        GOT
    }
    private RequestTokenState requestTokenState = null;

    private final BroadcastReceiver tokenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String token = intent.getStringExtra("access_token");
            handleToken(token);
        }
    };


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        if (DEBUG) Log.i(TAG, "onCreatePreferences(savedInstanceState, rootKey=" + rootKey + ")");

        setPreferencesFromResource(R.xml.prefs, rootKey);

        mStravaUpload = this.getPreferenceScreen().findPreference(TrainingApplication.SP_UPLOAD_TO_STRAVA);
        mUpdateStravaEquipment = this.getPreferenceScreen().findPreference(TrainingApplication.UPDATE_STRAVA_EQUIPMENT);

        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(tokenReceiver, new IntentFilter(StravaOAuthCallbackActivity.StravaOAuthSuccess));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(TAG, "onResume()");

        mUpdateStravaEquipment.setSummary(TrainingApplication.getLastUpdateTimeOfStravaEquipment());
        mUpdateStravaEquipment.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (DEBUG) Log.d(TAG, "updateStravaEquipment has been clicked");
                new StravaEquipmentSynchronizeThread(getActivity()).start();
                return false;
            }
        });

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
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(tokenReceiver);
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (DEBUG) Log.i(TAG, "onSharedPreferenceChanged: key=" + key);

        if (TrainingApplication.SP_LAST_UPDATE_TIME_OF_STRAVA_EQUIPMENT.equals(key)) {
            mUpdateStravaEquipment.setSummary(TrainingApplication.getLastUpdateTimeOfStravaEquipment());
        }

        if (TrainingApplication.SP_UPLOAD_TO_STRAVA.equals(key)) {
            if (!TrainingApplication.uploadToStrava()) {
                if (DEBUG) Log.d(TAG, "deleting Strava token");
                TrainingApplication.deleteStravaToken();
                new StravaDeauthorizationThread(getActivity()).start();
            } else {
                requestTokenState = RequestTokenState.REQUESTING;
                startActivity(new Intent(getActivity(), StravaGetAccessTokenActivity.class));
            }
        }

    }


    protected void handleToken(String token) {
        if (token != null) {
            requestTokenState = RequestTokenState.GOT;

            TrainingApplication.setStravaAccessToken(token);

            // synchronize equipment
            new StravaEquipmentSynchronizeThread(getActivity()).start();

            // update Segments
            StravaSegmentsHelper stravaSegmentsHelper = new StravaSegmentsHelper(getContext());
            stravaSegmentsHelper.getStarredStravaSegments(SportTypeDatabaseManager.getSportTypeId(BSportType.BIKE));
            stravaSegmentsHelper.getStarredStravaSegments(SportTypeDatabaseManager.getSportTypeId(BSportType.RUN));
        }
    }
}
