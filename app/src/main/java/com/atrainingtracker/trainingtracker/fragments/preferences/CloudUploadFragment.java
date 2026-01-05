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

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import android.util.Log;

import com.atrainingtracker.BuildConfig;
import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.android.Auth;
import com.dropbox.core.oauth.DbxCredential;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by rainer on 01.02.16.
 */

public class CloudUploadFragment extends androidx.preference.PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final boolean DEBUG = TrainingApplication.getDebug(false);
    private static final String TAG = CloudUploadFragment.class.getName();

    @Nullable
    private PreferenceScreen mPSStrava, /* mPSRunkeeper, mPSTrainingPeaks, */ mPSEmailUpload;

    private SharedPreferences mSharedPreferences;

    private Boolean mAwaitDropboxResult = false;

    // private static DropboxAPI<AndroidAuthSession> mDBApi;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        if (DEBUG) Log.i(TAG, "onCreatePreferences(savedInstanceState, rootKey=" + rootKey + ")");

        setPreferencesFromResource(R.xml.prefs, rootKey);

        mPSStrava = this.getPreferenceScreen().findPreference(TrainingApplication.PREFERENCE_SCREEN_STRAVA);
        // mPSRunkeeper = this.getPreferenceScreen().findPreference(TrainingApplication.PREFERENCE_SCREEN_RUNKEEPER);
        // mPSTrainingPeaks = this.getPreferenceScreen().findPreference(TrainingApplication.PREFERENCE_SCREEN_TRAINING_PEAKS);
        mPSEmailUpload = this.getPreferenceScreen().findPreference(TrainingApplication.PREFERENCE_SCREEN_EMAIL_UPLOAD);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(TAG, "onResume()");

        mPSStrava.setSummary(getPSStravaSummary());
        // mPSRunkeeper.setSummary(getPSRunkeeperSummary());
        // mPSTrainingPeaks.setSummary(getPSTrainingPeaksSummary());
        mPSEmailUpload.setSummary(getPSEmailUploadSummary());

        if (mAwaitDropboxResult) {
            DbxCredential dbxCredential = Auth.getDbxCredential();
            TrainingApplication.storeDropboxCredential(dbxCredential);
            mAwaitDropboxResult = false;
        }

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

        if (TrainingApplication.SP_UPLOAD_TO_DROPBOX.equals(key)) {
            if (!TrainingApplication.uploadToDropbox()) { // -> Upload to Dropbox has been changed to false by the user
                TrainingApplication.deleteDropboxCredential();
            } else {                                      // -> Upload to Dropbox has been changed to true by the user
                Auth.startOAuth2PKCE(getActivity(), BuildConfig.DROPBOX_APP_KEY, new DbxRequestConfig(BuildConfig.DROPBOX_APP_KEY));
                mAwaitDropboxResult = true;
            }
        }
    }

    @NonNull
    private String getPSStravaSummary() {
        if (!TrainingApplication.uploadToStrava()) {
            return getString(R.string.prefsUploadToStravaSummary);
        } else {
            List<String> list = new LinkedList<>();
            if (TrainingApplication.uploadStravaGPS()) {
                list.add(getString(R.string.GPS));
            }
            if (TrainingApplication.uploadStravaAltitude()) {
                list.add(getString(R.string.altitude));
            }
            if (TrainingApplication.uploadStravaHR()) {
                list.add(getString(R.string.heart_rate));
            }
            if (TrainingApplication.uploadStravaPower()) {
                list.add(getString(R.string.power));
            }
            if (TrainingApplication.uploadStravaCadence()) {
                list.add(getString(R.string.cadence));
            }

            return getString(R.string.upload_to_strava_format, listToString(list, 5));
        }
    }

    @NonNull
    private String getPSRunkeeperSummary() {
        if (!TrainingApplication.uploadToRunKeeper()) {
            return getString(R.string.prefsUploadToRunkeeperSummary);
        } else {
            List<String> list = new LinkedList<>();
            if (TrainingApplication.uploadRunkeeperGPS()) {
                list.add(getString(R.string.GPS));
            }
            if (TrainingApplication.uploadRunkeeperHR()) {
                list.add(getString(R.string.heart_rate));
            }

            return getString(R.string.upload_to_runkeeper_format, listToString(list, 3));
        }
    }

    @NonNull
    private String getPSTrainingPeaksSummary() {
        if (!TrainingApplication.uploadToTrainingPeaks()) {
            return getString(R.string.prefsUploadToTrainingPeaksSummary);
        } else {
            List<String> list = new LinkedList<>();
            if (TrainingApplication.uploadTrainingPeaksGPS()) {
                list.add(getString(R.string.GPS));
            }
            if (TrainingApplication.uploadTrainingPeaksAltitude()) {
                list.add(getString(R.string.altitude));
            }
            if (TrainingApplication.uploadTrainingPeaksHR()) {
                list.add(getString(R.string.heart_rate));
            }
            if (TrainingApplication.uploadTrainingPeaksPower()) {
                list.add(getString(R.string.power));
            }
            if (TrainingApplication.uploadTrainingPeaksCadence()) {
                list.add(getString(R.string.cadence));
            }

            return getString(R.string.upload_to_training_peaks_format, listToString(list, 5));
        }
    }

    @NonNull
    private String getPSEmailUploadSummary() {
        if (!TrainingApplication.sendEmail()) {
            return getString(R.string.upload_via_email);
        } else {
            List<String> list = new LinkedList<>();
            if (TrainingApplication.sendTCXEmail()) {
                list.add(getString(R.string.TCX));
            }
            if (TrainingApplication.sendGPXEmail()) {
                list.add(getString(R.string.GPX));
            }
            if (TrainingApplication.sendCSVEmail()) {
                list.add(getString(R.string.CSV));
            }
            if (TrainingApplication.sendGCEmail()) {
                list.add(getString(R.string.GC));
            }

            if (list.isEmpty()) {
                return getString(R.string.send_email_only_summary_format, TrainingApplication.getSpEmailAddress());
            } else if (list.size() == 1) {
                return getString(R.string.send_email_format, list.get(0), TrainingApplication.getSpEmailAddress());
            } else {
                return getString(R.string.send_email_format_plural, listToString(list, 5), TrainingApplication.getSpEmailAddress());
            }
        }
    }

    private String listToString(@NonNull List<String> listOfString, int max) {
        int size = listOfString.size();
        if (size == max) {
            return getString(R.string.everything);
        } else if (size == 0) {
            return getString(R.string.summary);
        } else if (size == 1) {
            return listOfString.get(0);
        } else if (size == 2) {
            return getString(R.string.concatenate_2_format, listOfString.get(0), listOfString.get(1));
        } else {
            String lastOne = listOfString.get(size - 1);
            listOfString.remove(size - 1);

            StringBuilder result = new StringBuilder();
            for (String string : listOfString) {
                result.append(string);
                result.append(", ");
            }
            String firstPart = result.substring(0, result.length() - 2);

            return getString(R.string.concatenate_last_format, firstPart, lastOne);
        }
    }

}
