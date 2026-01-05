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

package com.atrainingtracker.trainingtracker.onlinecommunities.strava;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.atrainingtracker.trainingtracker.TrainingApplication;

import static com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaSegmentsIntentService.REQUEST_TYPE;
import static com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaSegmentsIntentService.REQUEST_UPDATE_LEADERBOARD;
import static com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaSegmentsIntentService.REQUEST_UPDATE_STARRED_SEGMENTS;
import static com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaSegmentsIntentService.SEGMENT_ID;
import static com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaSegmentsIntentService.SPORT_TYPE_ID;


public class StravaSegmentsHelper {
    private static final String TAG = StravaSegmentsHelper.class.getSimpleName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);

    private final Context mContext;

    public StravaSegmentsHelper(Context context) {
        mContext = context;
    }

    public boolean isSegmentListUpdating(long sportTypeId) {
        if (DEBUG) Log.i(TAG, "isSegmentListUpdating(" + sportTypeId + ")");

        return ((TrainingApplication) mContext.getApplicationContext()).isSegmentListUpdating(sportTypeId);
    }

    public boolean isLeaderboardUpdating(long segmentId) {
        return ((TrainingApplication) mContext.getApplicationContext()).isLeaderboardUpdating(segmentId);
    }


    public void getStarredStravaSegments(long sportTypeId) {
        if (DEBUG) Log.i(TAG, "getStarredStravaSegments");

        if (((TrainingApplication) mContext.getApplicationContext()).isSegmentListUpdating(sportTypeId)) {
            // currently updating, so do nothing
        } else {
            Intent intent = new Intent(mContext, StravaSegmentsIntentService.class);
            intent.putExtra(REQUEST_TYPE, REQUEST_UPDATE_STARRED_SEGMENTS);
            intent.putExtra(SPORT_TYPE_ID, sportTypeId);

            mContext.startService(intent);
        }
    }

    public void getSegmentLeaderboard(long segmentId) {
        if (DEBUG) Log.i(TAG, "getSegmentLeaderboard");

        if (((TrainingApplication) mContext.getApplicationContext()).isLeaderboardUpdating(segmentId)) {

        } else {
            Intent intent = new Intent(mContext, StravaSegmentsIntentService.class);
            intent.putExtra(REQUEST_TYPE, REQUEST_UPDATE_LEADERBOARD);
            intent.putExtra(SEGMENT_ID, segmentId);

            mContext.startService(intent);
        }
    }

}
