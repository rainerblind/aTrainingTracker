package com.atrainingtracker.trainingtracker.smartwatch.pebble;

import android.os.Bundle;
import android.support.v4.app.Fragment;

import com.atrainingtracker.banalservice.ActivityType;
import com.atrainingtracker.trainingtracker.Activities.ConfigViewsActivity;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.fragments.ConfigViewsFragment;

import java.util.LinkedList;

/**
 * Created by rainer on 20.01.16.
 */

public class ConfigPebbleViewsFragment extends ConfigViewsFragment {

    public static final String TAG = ConfigPebbleViewsFragment.class.getSimpleName();
    private static final boolean DEBUG = TrainingApplication.DEBUG && true;

    public static ConfigPebbleViewsFragment newInstance(ActivityType activityType, long viewId) {

        ConfigPebbleViewsFragment fragment = new ConfigPebbleViewsFragment();

        Bundle args = new Bundle();
        if (activityType == null) {
            args.putString(ConfigViewsActivity.ACTIVITY_TYPE, ActivityType.getDefaultActivityType().name());
        } else {
            args.putString(ConfigViewsActivity.ACTIVITY_TYPE, activityType.name());
        }
        args.putLong(ConfigViewsActivity.VIEW_ID, viewId);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    protected void ensureEntryForActivityTypeExists() {
        PebbleDatabaseManager.ensureEntryForActivityTypeExists(getContext(), mActivityType);
    }

    @Override
    protected LinkedList<Long> getViewIdList() {
        return PebbleDatabaseManager.getViewIdList(mActivityType);
    }

    @Override
    protected LinkedList<String> getTitleList() {
        return PebbleDatabaseManager.getTitleList(mActivityType);
    }

    @Override
    protected void deleteView(long viewId) {
        PebbleDatabaseManager.deleteView(viewId);
    }

    @Override
    protected long addView(long viewId, boolean addAfterCurrentLayout) {
        // TODO: show dialog with several options
        return PebbleDatabaseManager.addDefaultView(getContext(), viewId, addAfterCurrentLayout);
    }

    @Override
    protected Fragment getNewChildFragment(long viewId) {
        return ConfigPebbleViewFragment.newInstance(viewId);
    }
}
