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

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.activities.ZonesSettingsActivity;
import com.atrainingtracker.trainingtracker.exporter.FileFormat;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore;


public class RootPrefsFragment extends PreferenceFragmentCompat
        implements OnSharedPreferenceChangeListener {
    public static final String TAG = "RootPrefsFragment";
    private static final boolean DEBUG = TrainingApplication.getDebug(false);


    @Nullable
    private EditTextPreference mSearchRoundsPref;
    @Nullable
    private ListPreference mUnitPref;
    @Nullable
    private Preference mZonesRunHR, mZonesBikeHR, mZonesBikePower, mExport, mCloudUpload, mDisplayOptions;

    private SharedPreferences mSharedPreferences;
    private SettingsDataStore mSettingsDataStore;


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        if (DEBUG) Log.i(TAG, "onCreatePreferences(savedInstanceState, rootKey=" + rootKey + ")");

        // addPreferencesFromResource(R.xml.prefs);
        setPreferencesFromResource(R.xml.prefs, rootKey);
        if (DEBUG) Log.i(TAG, "inflated xml resource file");

        mUnitPref = getPreferenceScreen().findPreference(TrainingApplication.SP_UNITS);

        // HR Run Zones
        mZonesRunHR = findPreference("zones_hr_run");
        if (mZonesRunHR != null) {
            mZonesRunHR.setOnPreferenceClickListener(preference -> {
                startZonesActivity(0); // Index for Run
                return true;
            });
        }

        // HR Bike Zones
        mZonesBikeHR = findPreference("zones_hr_bike");
        if (mZonesBikeHR != null) {
            mZonesBikeHR.setOnPreferenceClickListener(preference -> {
                startZonesActivity(1); // Index for Bike
                return true;
            });
        }

        // Power Bike Zones
        mZonesBikePower = findPreference("zones_pwr_bike");
        if (mZonesBikePower != null) {
            mZonesBikePower.setOnPreferenceClickListener(preference -> {
                startZonesActivity(2); // Index for Power
                return true;
            });
        }

        mSearchRoundsPref = getPreferenceScreen().findPreference(TrainingApplication.SP_NUMBER_OF_SEARCH_TRIES);

        mExport = this.getPreferenceScreen().findPreference(TrainingApplication.SP_EXPORT_FORMATS);
        mCloudUpload = this.getPreferenceScreen().findPreference(TrainingApplication.CLOUD_UPLOAD);

        mDisplayOptions = this.getPreferenceScreen().findPreference(TrainingApplication.SP_DISPLAY_OPTIONS);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSettingsDataStore = new SettingsDataStore(requireContext());
        if (DEBUG) Log.i(TAG, "onCreate()");
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(TAG, "onResume()");


        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        mUnitPref.setSummary(TrainingApplication.getUnit().getNameId());

        updateZonesRunHRSummary();
        updateZonesBikeHRSummary();
        updateZonesBikePowerSummary();

        mSearchRoundsPref.setSummary(TrainingApplication.getNumberOfSearchTries() + "");


        mExport.setSummary(exportSummary());
        mCloudUpload.setSummary(cloudUploadSummary());

        mDisplayOptions.setSummary(displayOptionsSummary());


        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    private void startZonesActivity(int tabIndex) {
        Intent intent = new Intent(getActivity(), ZonesSettingsActivity.class);
        intent.putExtra("TARGET_ZONE_TAB", tabIndex);
        startActivity(intent);
    }

    private void updateZonesRunHRSummary() {
        if (mZonesRunHR != null && mSettingsDataStore != null) {
            try {
                // Fetch the summary string from the Kotlin helper
                mZonesRunHR.setSummary(mSettingsDataStore.getSummary(SettingsDataStore.ZoneType.HR_RUN));
            } catch (Exception e) {
                Log.e(TAG, "Failed to load HR Zones summary for run HR", e);
                mZonesRunHR.setSummary("Configure your run HR training zones");
            }
        }
    }

    private void updateZonesBikeHRSummary() {
        if (mZonesBikeHR != null && mSettingsDataStore != null) {
            try {
                // Fetch the summary string from the Kotlin helper
                mZonesBikeHR.setSummary(mSettingsDataStore.getSummary(SettingsDataStore.ZoneType.HR_BIKE));
            } catch (Exception e) {
                Log.e(TAG, "Failed to load HR Zones summary for bike HR", e);
                mZonesBikeHR.setSummary("Configure your bike HR training zones");
            }
        }
    }

    private void updateZonesBikePowerSummary() {
        if (mZonesBikePower != null && mSettingsDataStore != null) {
            try {
                // Fetch the summary string from the Kotlin helper
                mZonesBikePower.setSummary(mSettingsDataStore.getSummary(SettingsDataStore.ZoneType.PWR_BIKE));
            } catch (Exception e) {
                Log.e(TAG, "Failed to load HR Zones summary", e);
                mZonesBikePower.setSummary("Configure your bike power training zones");
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        // Unregister the listener whenever a key changes            
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (TrainingApplication.SP_UNITS.equals(key)) {
            mUnitPref.setSummary(TrainingApplication.getUnit().toString());
        }

        if (TrainingApplication.SP_EXPORT_FORMATS.equals(key)) {
            String exportSummary = exportSummary();
            Log.i(TAG, "updating exportSummary to " + exportSummary);
            mExport.setSummary(exportSummary);
            getActivity().onContentChanged();
        }

        if (TrainingApplication.SP_UPLOAD_TO_DROPBOX.equals(key)
                || TrainingApplication.SP_UPLOAD_TO_STRAVA.equals(key)
                || TrainingApplication.SP_UPLOAD_TO_RUNKEEPER.equals(key)
                || TrainingApplication.SP_UPLOAD_TO_TRAINING_PEAKS.equals(key)) {
            String cloudUploadSummary = cloudUploadSummary();
            Log.i(TAG, "updating cloudUploadSummary to " + cloudUploadSummary);
            mCloudUpload.setSummary(cloudUploadSummary);
            getActivity().onContentChanged();
        }

        if (TrainingApplication.SP_DISPLAY_OPTIONS.equals(key)) {
            String displaySummary = displayOptionsSummary();
            if (DEBUG) Log.i(TAG, "updating displayOptionsSummary to " + displaySummary);
            mDisplayOptions.setSummary(displaySummary);
            // This ensures the UI refreshes the text immediately
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> mDisplayOptions.setSummary(displaySummary));
            }
        }

        if (TrainingApplication.SP_NUMBER_OF_SEARCH_TRIES.equals(key)) {
            mSearchRoundsPref.setSummary(TrainingApplication.getNumberOfSearchTries() + "");
        }
    }

    @NonNull
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

    @NonNull
    protected String cloudUploadSummary() {
        if (DEBUG) Log.i(TAG, "cloudUploadSummary()");

        String cloudUpload = null;

        if (TrainingApplication.uploadToDropbox()) {
            cloudUpload = incString(cloudUpload);
            cloudUpload += getString(R.string.Dropbox);
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

    String displayOptionsSummary() {
        if (DEBUG) Log.i(TAG, "displayOptionsSummary()");

        String displayOptions = null;

        if (TrainingApplication.forcePortrait()) {
            displayOptions = incString(displayOptions);
            displayOptions += getString(R.string.forcePortrait);
        }

        if (TrainingApplication.keepScreenOn()) {
            displayOptions = incString(displayOptions);
            displayOptions += getString(R.string.prefsKeepScreenOnTitle);
        }

        if (TrainingApplication.NoUnlocking()) {
            displayOptions = incString(displayOptions);
            displayOptions += getString(R.string.prefsNoUnlockingTitle);
        }

        if (displayOptions == null) {
            // Default text if nothing is selected
            displayOptions = getString(R.string.prefsDisplaySummary);
        }

        return displayOptions;
    }

    @NonNull
    protected String incString(@Nullable String string) {
        if (string != null) {
            string += ", ";
        } else {
            string = "";
        }
        return string;
    }
}
