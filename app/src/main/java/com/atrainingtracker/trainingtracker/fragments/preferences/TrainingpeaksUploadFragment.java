package com.atrainingtracker.trainingtracker.fragments.preferences;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.onlinecommunities.BaseGetAccessTokenActivity;
import com.atrainingtracker.trainingtracker.onlinecommunities.trainingpeaks.TrainingpeaksGetAccessTokenActivity;

/**
 * Created by rainer on 01.02.16.
 */
public class TrainingpeaksUploadFragment extends android.support.v7.preference.PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final int GET_TRAINING_PEAKS_ACCESS_TOKEN = 4;
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;
    private static final String TAG = TrainingpeaksUploadFragment.class.getName();
    private CheckBoxPreference mTrainingPeaksUpload;

    private SharedPreferences mSharedPreferences;


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        if (DEBUG) Log.i(TAG, "onCreatePreferences(savedInstanceState, rootKey=" + rootKey + ")");

        setPreferencesFromResource(R.xml.prefs, rootKey);

        mTrainingPeaksUpload = (CheckBoxPreference) this.getPreferenceScreen().findPreference(TrainingApplication.SP_UPLOAD_TO_TRAINING_PEAKS);
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

        if (TrainingApplication.SP_UPLOAD_TO_TRAINING_PEAKS.equals(key)) {
            if (!TrainingApplication.uploadToTrainingPeaks()) {
                if (DEBUG) Log.d(TAG, "deleting TrainingPeaks token");
                TrainingApplication.deleteTrainingPeaksToken();
            } else {
                startActivityForResult(new Intent(getActivity(), TrainingpeaksGetAccessTokenActivity.class), GET_TRAINING_PEAKS_ACCESS_TOKEN);
            }
        }

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (DEBUG) Log.i(TAG, "onActivityResult: requestCode=" + requestCode);

        switch (requestCode) {
            case (GET_TRAINING_PEAKS_ACCESS_TOKEN):
                if (DEBUG) Log.i(TAG, "result from TrainingPeaks");
                if (resultCode == Activity.RESULT_OK) {
                    if (DEBUG) Log.i(TAG, "result_ok");
                    String accessToken = data.getStringExtra(BaseGetAccessTokenActivity.ACCESS_TOKEN);
                    TrainingApplication.setTrainingPeaksAccessToken(accessToken);
                    // Log.d(TAG, "we got the access token: " + accessToken);
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    if (DEBUG) Log.i(TAG, "result_canceled");
                    // Log.d(TAG, "WTF, something went wrong");
                    TrainingApplication.deleteTrainingPeaksToken();
                    mTrainingPeaksUpload.setChecked(false);
                }

                break;
        }
    }
}
