package com.atrainingtracker.trainingtracker.smartwatch.pebble;


import com.atrainingtracker.banalservice.ActivityType;
import com.atrainingtracker.trainingtracker.Activities.ConfigViewsActivity;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.fragments.ConfigViewsFragment;

public class ConfigPebbleViewsActivity extends ConfigViewsActivity {

    private static final String TAG = ConfigPebbleViewsActivity.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG & true;


    @Override
    public ActivityType getActivityType(long viewId) {
        return PebbleDatabaseManager.getActivityType(viewId);
    }

    @Override
    public ConfigViewsFragment getNewConfigViewsFragment(ActivityType activityType, long viewId) {
        return ConfigPebbleViewsFragment.newInstance(activityType, viewId);
    }
}
