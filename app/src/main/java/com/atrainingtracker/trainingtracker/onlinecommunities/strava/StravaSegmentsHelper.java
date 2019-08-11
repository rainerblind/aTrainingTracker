package com.atrainingtracker.trainingtracker.onlinecommunities.strava;

/**
 * Created by rainer on 30.08.16.
 */

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
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;

    private Context mContext;

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
        if (DEBUG) Log.i(TAG, "getSegmentSeaderboard");

        if (((TrainingApplication) mContext.getApplicationContext()).isLeaderboardUpdating(segmentId)) {

        } else {
            Intent intent = new Intent(mContext, StravaSegmentsIntentService.class);
            intent.putExtra(REQUEST_TYPE, REQUEST_UPDATE_LEADERBOARD);
            intent.putExtra(SEGMENT_ID, segmentId);

            mContext.startService(intent);
        }
    }

}
