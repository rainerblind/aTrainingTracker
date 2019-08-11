package com.atrainingtracker.trainingtracker.fragments.preferences;

import android.os.Bundle;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;

/**
 * Created by rainer on 01.02.16.
 */
public class FileExportFragment extends android.support.v7.preference.PreferenceFragmentCompat {
    private static final boolean DEBUG = TrainingApplication.DEBUG;
    private static final String TAG = FileExportFragment.class.getName();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        if (DEBUG) Log.i(TAG, "onCreatePreferences(savedInstanceState, rootKey=" + rootKey + ")");

        setPreferencesFromResource(R.xml.prefs, rootKey);
    }

    // thats all???
}
