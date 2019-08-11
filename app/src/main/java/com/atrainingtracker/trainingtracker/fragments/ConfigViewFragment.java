package com.atrainingtracker.trainingtracker.fragments;

import android.content.Intent;
import android.support.v4.app.Fragment;
import android.util.Log;

import com.atrainingtracker.banalservice.ActivityType;
import com.atrainingtracker.trainingtracker.Activities.ConfigViewsActivity;

public abstract class ConfigViewFragment extends Fragment {
    private static final String TAG = ConfigViewFragment.class.getName();
    private static final boolean DEBUG = true;

    // public static final String TRACKING_VIEW_CHANGED_INTENT = "TRACKING_VIEW_CHANGED_INTENT";


    protected ActivityType mActivityType;
    protected long mViewId;

    protected void notifyFinishedTyping(String name) {
        if (DEBUG) Log.i(TAG, "notifyFinishedTyping, mViewId=" + mViewId);

        updateNameOfView(name);

        Intent intent = new Intent(ConfigViewsActivity.NAME_CHANGED_INTENT);
        intent.putExtra(ConfigViewsActivity.VIEW_ID, mViewId);
        intent.putExtra(ConfigViewsActivity.NAME, name);
        getActivity().sendBroadcast(intent);
    }

    protected abstract void updateNameOfView(String name);

    public long getViewId() {
        return mViewId;
    }

}
