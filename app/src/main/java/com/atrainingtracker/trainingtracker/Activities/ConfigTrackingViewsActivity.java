package com.atrainingtracker.trainingtracker.Activities;

import com.atrainingtracker.banalservice.ActivityType;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager;
import com.atrainingtracker.trainingtracker.fragments.ConfigTrackingViewsFragment;
import com.atrainingtracker.trainingtracker.fragments.ConfigViewsFragment;

public class ConfigTrackingViewsActivity
        extends ConfigViewsActivity
        implements BANALService.GetBanalServiceInterface {

    private static final String TAG = ConfigTrackingViewsActivity.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG & true;


    @Override
    public ActivityType getActivityType(long viewId) {
        return TrackingViewsDatabaseManager.getActivityType(viewId);
    }

    @Override
    public ConfigViewsFragment getNewConfigViewsFragment(ActivityType activityType, long viewId) {
        return ConfigTrackingViewsFragment.newInstance(activityType, viewId);
    }


    // just dummy methods to implement the interfaces but without any functionality
    @Override
    public BANALService.BANALServiceComm getBanalServiceComm() {
        return null;
    }

    @Override
    public void registerConnectionStatusListener(ConnectionStatusListener connectionStatusListener) {
    }

}
