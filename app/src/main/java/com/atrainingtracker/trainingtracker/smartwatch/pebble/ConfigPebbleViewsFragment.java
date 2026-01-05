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

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.atrainingtracker.banalservice.ActivityType;
import com.atrainingtracker.trainingtracker.activities.ConfigViewsActivity;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.fragments.ConfigViewsFragment;

import java.util.LinkedList;
import java.util.Objects;

/**
 * Created by rainer on 20.01.16.
 */

public class ConfigPebbleViewsFragment extends ConfigViewsFragment {

    public static final String TAG = ConfigPebbleViewsFragment.class.getSimpleName();
    private static final boolean DEBUG = TrainingApplication.getDebug(true);

    @NonNull
    public static ConfigPebbleViewsFragment newInstance(ActivityType activityType, long viewId) {

        ConfigPebbleViewsFragment fragment = new ConfigPebbleViewsFragment();

        Bundle args = new Bundle();
        args.putString(ConfigViewsActivity.ACTIVITY_TYPE, Objects.requireNonNullElseGet(activityType, ActivityType::getDefaultActivityType).name());
        args.putLong(ConfigViewsActivity.VIEW_ID, viewId);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    protected void ensureEntryForActivityTypeExists() {
        PebbleDatabaseManager.ensureEntryForActivityTypeExists(getContext(), mActivityType);
    }

    @NonNull
    @Override
    protected LinkedList<Long> getViewIdList() {
        return PebbleDatabaseManager.getViewIdList(mActivityType);
    }

    @NonNull
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

    @NonNull
    @Override
    protected Fragment getNewChildFragment(long viewId) {
        return ConfigPebbleViewFragment.newInstance(viewId);
    }
}
