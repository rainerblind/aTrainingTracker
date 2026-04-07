/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
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

package com.atrainingtracker.trainingtracker.fragments;

import android.content.Intent;
import androidx.fragment.app.Fragment;
import android.util.Log;

import com.atrainingtracker.banalservice.ActivityType;
import com.atrainingtracker.trainingtracker.activities.ConfigViewsActivityClassic;

public abstract class ConfigViewFragment extends Fragment {
    private static final String TAG = ConfigViewFragment.class.getName();
    private static final boolean DEBUG = true;

    // public static final String TRACKING_VIEW_CHANGED_INTENT = "TRACKING_VIEW_CHANGED_INTENT";


    protected ActivityType mActivityType;
    protected long mViewId;

    protected void notifyFinishedTyping(String name) {
        if (DEBUG) Log.i(TAG, "notifyFinishedTyping, mViewId=" + mViewId);

        updateNameOfView(name);

        Intent intent = new Intent(ConfigViewsActivityClassic.NAME_CHANGED_INTENT)
                .putExtra(ConfigViewsActivityClassic.VIEW_ID, mViewId)
                .putExtra(ConfigViewsActivityClassic.NAME, name)
                .setPackage(getActivity().getPackageName());
        getActivity().sendBroadcast(intent);
    }

    protected abstract void updateNameOfView(String name);

    public long getViewId() {
        return mViewId;
    }

}
