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

package com.atrainingtracker.trainingtracker.smartwatch.pebble;


import androidx.annotation.NonNull;

import com.atrainingtracker.banalservice.ActivityType;
import com.atrainingtracker.trainingtracker.activities.ConfigViewsActivity;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.fragments.ConfigViewsFragment;

public class ConfigPebbleViewsActivity extends ConfigViewsActivity {

    private static final String TAG = ConfigPebbleViewsActivity.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(true);


    @NonNull
    @Override
    public ActivityType getActivityType(long viewId) {
        return PebbleDatabaseManager.getActivityType(viewId);
    }

    @NonNull
    @Override
    public ConfigViewsFragment getNewConfigViewsFragment(ActivityType activityType, long viewId) {
        return ConfigPebbleViewsFragment.newInstance(activityType, viewId);
    }
}
