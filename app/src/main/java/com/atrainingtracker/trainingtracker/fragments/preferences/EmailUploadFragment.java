package com.atrainingtracker.trainingtracker.fragments.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;

/**
 * Created by rainer on 01.02.16.
 */
public class EmailUploadFragment extends android.support.v7.preference.PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final boolean DEBUG = TrainingApplication.DEBUG;
    private static final String TAG = EmailUploadFragment.class.getName();

    EditTextPreference etpAddress, etpSubject;

    private SharedPreferences mSharedPreferences;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        if (DEBUG) Log.i(TAG, "onCreatePreferences(savedInstanceState, rootKey=" + rootKey + ")");

        setPreferencesFromResource(R.xml.prefs, rootKey);

        etpAddress = (EditTextPreference) this.getPreferenceScreen().findPreference(TrainingApplication.SP_EMAIL_ADDRESS);
        etpSubject = (EditTextPreference) this.getPreferenceScreen().findPreference(TrainingApplication.SP_EMAIL_SUBJECT);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(TAG, "onResume()");

        etpAddress.setSummary(TrainingApplication.getSpEmailAddress());
        etpSubject.setSummary(TrainingApplication.getSpEmailSubject());

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

        if (TrainingApplication.SP_EMAIL_ADDRESS.equals(key)) {
            etpAddress.setSummary(TrainingApplication.getSpEmailAddress());
        } else if (TrainingApplication.SP_EMAIL_SUBJECT.equals(key)) {
            etpSubject.setSummary(TrainingApplication.getSpEmailSubject());
        }
    }
}
