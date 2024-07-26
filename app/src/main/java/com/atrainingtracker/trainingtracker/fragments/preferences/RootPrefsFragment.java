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
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.exporter.FileFormat;
import com.atrainingtracker.trainingtracker.TrainingApplication;


public class RootPrefsFragment extends PreferenceFragmentCompat
        implements OnSharedPreferenceChangeListener {
    public static final String TAG = RootPrefsFragment.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);


    private EditTextPreference mAthleteNamePref, mSamplingTimePref, mSearchRoundsPref;
    private ListPreference mUnitPref;
    private Preference mExport, mPebble, mLocationSources, mCloudUpload;

    private SharedPreferences mSharedPreferences;


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        if (DEBUG) Log.i(TAG, "onCreatePreferences(savedInstanceState, rootKey=" + rootKey + ")");

        // addPreferencesFromResource(R.xml.prefs);
        setPreferencesFromResource(R.xml.prefs, rootKey);
        if (DEBUG) Log.i(TAG, "inflated xml resource file");

        mUnitPref = getPreferenceScreen().findPreference(TrainingApplication.SP_UNITS);

        mAthleteNamePref = getPreferenceScreen().findPreference(TrainingApplication.SP_ATHLETE_NAME);
        mSamplingTimePref = getPreferenceScreen().findPreference(TrainingApplication.SP_SAMPLING_TIME);
        mSearchRoundsPref = getPreferenceScreen().findPreference(TrainingApplication.SP_NUMBER_OF_SEARCH_TRIES);

        mExport = this.getPreferenceScreen().findPreference(TrainingApplication.FILE_EXPORT);
        mCloudUpload = this.getPreferenceScreen().findPreference(TrainingApplication.CLOUD_UPLOAD);
        mPebble = this.getPreferenceScreen().findPreference(TrainingApplication.PEBBLE_SCREEN);
        mLocationSources = this.getPreferenceScreen().findPreference(TrainingApplication.LOCATION_SOURCES);

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (DEBUG) Log.i(TAG, "onCreate()");
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(TAG, "onResume()");


        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        mUnitPref.setSummary(TrainingApplication.getUnit().getNameId());
        mPebble.setSummary(pebbleSummary());
        mLocationSources.setSummary(locationSourcesSummary());


        mAthleteNamePref.setSummary(TrainingApplication.getAthleteName());
        if (DEBUG) Log.d(TAG, "sampling time: " + TrainingApplication.getSamplingTime());
        setSamplingTimeSummary();

        mSearchRoundsPref.setSummary(TrainingApplication.getNumberOfSearchTries() + "");


        mExport.setSummary(exportSummary());
        mCloudUpload.setSummary(cloudUploadSummary());


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
        if (TrainingApplication.SP_ATHLETE_NAME.equals(key)) {
            mAthleteNamePref.setSummary(TrainingApplication.getAthleteName());
        }

        if (TrainingApplication.SP_UNITS.equals(key)) {
            mUnitPref.setSummary(TrainingApplication.getUnit().toString());
        }

        if (TrainingApplication.SP_PEBBLE_WATCHAPP.equals(key)) {
            mPebble.setSummary(pebbleSummary());
            getActivity().onContentChanged();
        }

        if (TrainingApplication.SP_PEBBLE_SUPPORT.equals(key)) {
            mPebble.setSummary(pebbleSummary());
            getActivity().onContentChanged();
        }


        if (TrainingApplication.SP_EXPORT_TO_CSV.equals(key)
                | TrainingApplication.SP_EXPORT_TO_TCX.equals(key)
                | TrainingApplication.SP_EXPORT_TO_GPX.equals(key)
                | TrainingApplication.SP_EXPORT_TO_GC_JSON.equals(key)) {
            String exportSummary = exportSummary();
            Log.i(TAG, "updating exportSummary to " + exportSummary);
            mExport.setSummary(exportSummary);
            getActivity().onContentChanged();
        }

        if (TrainingApplication.SP_UPLOAD_TO_DROPBOX.equals(key)
                | TrainingApplication.SP_UPLOAD_TO_STRAVA.equals(key)
                | TrainingApplication.SP_UPLOAD_TO_RUNKEEPER.equals(key)
                | TrainingApplication.SP_UPLOAD_TO_TRAINING_PEAKS.equals(key)) {
            String cloudUploadSummary = cloudUploadSummary();
            Log.i(TAG, "updating cloudUploadSummary to " + cloudUploadSummary);
            mCloudUpload.setSummary(cloudUploadSummary);
            getActivity().onContentChanged();
        }

        if (TrainingApplication.SP_SAMPLING_TIME.equals(key)) {
            setSamplingTimeSummary();
        }

        if (TrainingApplication.SP_NUMBER_OF_SEARCH_TRIES.equals(key)) {
            mSearchRoundsPref.setSummary(TrainingApplication.getNumberOfSearchTries() + "");
        }


        if (TrainingApplication.SP_LOCATION_SOURCE_GPS.equals(key)
                | TrainingApplication.SP_LOCATION_SOURCE_GOOGLE_FUSED.equals(key)
                | TrainingApplication.SP_LOCATION_SOURCE_NETWORK.equals(key)) {
            mLocationSources.setSummary(locationSourcesSummary());
            getActivity().onContentChanged();
        }
    }

    protected String exportSummary() {
        if (DEBUG) Log.i(TAG, "exportSummary()");

        String exportTo = null;

        if (TrainingApplication.exportToTCX()) {
            exportTo = getString(R.string.TCX);
        }
        if (TrainingApplication.exportToGPX()) {
            exportTo = incString(exportTo);
            exportTo += getString(R.string.GPX);
        }
        if (TrainingApplication.exportToGCJson()) {
            exportTo = incString(exportTo);
            exportTo += getString(R.string.GC);
        }
        if (TrainingApplication.exportToCSV()) {
            exportTo = incString(exportTo);
            exportTo += getString(R.string.CSV);
        }

        if (exportTo == null) {
            exportTo = getString(R.string.prefsExportSummary);
        }

        return exportTo;
    }

    protected String cloudUploadSummary() {
        if (DEBUG) Log.i(TAG, "cloudUploadSummary()");

        String cloudUpload = null;

        if (TrainingApplication.uploadToDropbox()) {
            cloudUpload = getString(R.string.Dropbox);
        }
        for (FileFormat fileFormat : FileFormat.ONLINE_COMMUNITIES) {
            if (TrainingApplication.uploadToCommunity(fileFormat)) {
                cloudUpload = incString(cloudUpload);
                cloudUpload += getString(fileFormat.getUiNameId());
            }
        }

        if (cloudUpload == null) {
            cloudUpload = getString(R.string.prefsUploadSummary);
        }

        return cloudUpload;
    }

    protected String incString(String string) {
        if (string != null) {
            string += ", ";
        } else {
            string = "";
        }
        return string;
    }

    protected String pebbleSummary() {
        if (TrainingApplication.pebbleSupport()) {
            return getString(TrainingApplication.getPebbleWatchapp().getUiId());
        } else {
            return getString(R.string.prefsDoNotUsePebble);
        }
    }

    protected String locationSourcesSummary() {
        String locationSources = null;

        if (TrainingApplication.useLocationSourceGPS()) {
            locationSources = getString(R.string.prefsLocationSourceGPS);
        }
        if (TrainingApplication.useLocationSourceGoogleFused()) {
            locationSources = incString(locationSources);
            locationSources += getString(R.string.prefsLocationSourceGoogleFused);
        }
        if (TrainingApplication.useLocationSourceNetwork()) {
            locationSources = incString(locationSources);
            locationSources += getString(R.string.prefsLocationSourceNetwork);
        }

        if (locationSources == null) {
            locationSources = getString(R.string.prefsLocationSourceSummary);
        }

        return locationSources;
    }


    protected void setSamplingTimeSummary() {
        int samplingTime = TrainingApplication.getSamplingTime();
        String units = getString(R.string.units_time_basic);
        if (DEBUG)
            Log.i(TAG, "setSamplingTimeSummary: samplingTime=" + samplingTime + ", units = " + units);
        mSamplingTimePref.setSummary(getString(R.string.value_unit_int_string, samplingTime, units));
    }

}
