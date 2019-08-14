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
import android.os.AsyncTask;
import android.util.Log;

import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Created by rainer on 31.08.16.
 */

public class StravaHelper {
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER = "Bearer";
    protected static final String ID = "id";
    private static final String TAG = StravaHelper.class.getSimpleName();
    private static final boolean DEBUG = true; // TrainingApplication.DEBUG & true;

    public static String translateClimbCategory(int climbCategory) {
        switch (climbCategory) {
            case 1:
                return "cat. 4";

            case 2:
                return "cat. 3";

            case 3:
                return "cat. 2";

            case 4:
                return "cat. 1";

            case 5:
                return "HC";

            default:
                return "";
        }
    }

    /* returns the athleteId stored in the shared preferences.  If this is not available (== 0), get the id from strava in the background and also get the segments
     * But this method still returns 0. */
    public int getAthleteId(Context context) {
        int athleteId = TrainingApplication.getStravaAthleteId();

        if (athleteId == 0) {
            new GetAthleteIdFromStravaAsyncTask(context).execute();
        }

        return athleteId;
    }

    class GetAthleteIdFromStravaAsyncTask extends AsyncTask<Void, Void, Void> {
        Context mContext;

        GetAthleteIdFromStravaAsyncTask(Context context) {
            mContext = context;
        }

        protected Void doInBackground(Void... foo) {
            HttpClient httpClient = new DefaultHttpClient();
            HttpGet httpGet = new HttpGet("https://www.strava.com/api/v3/athlete");
            httpGet.addHeader(AUTHORIZATION, BEARER + " " + TrainingApplication.getStravaToken());

            HttpResponse httpResponse;
            try {
                httpResponse = httpClient.execute(httpGet);

                String response = EntityUtils.toString(httpResponse.getEntity());
                if (DEBUG) Log.d(TAG, "getAthleteId response: " + response);

                JSONObject jsonObject = new JSONObject(response);

                if (jsonObject.has(ID)) {
                    if (DEBUG) Log.i(TAG, "got own athleteId: " + jsonObject.getInt(ID));
                    TrainingApplication.setStravaAthleteId(jsonObject.getInt(ID));

                    // ok, we got a new strava id, so we update the segments database
                    if (!SegmentsDatabaseManager.doesDatabaseExist(mContext)) {  // but there is not yet a database for the segments
                        StravaSegmentsHelper stravaSegmentsHelper = new StravaSegmentsHelper(mContext);
                        stravaSegmentsHelper.getStarredStravaSegments(SportTypeDatabaseManager.getSportTypeId(BSportType.BIKE));
                        stravaSegmentsHelper.getStarredStravaSegments(SportTypeDatabaseManager.getSportTypeId(BSportType.RUN));
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }
    }

}
