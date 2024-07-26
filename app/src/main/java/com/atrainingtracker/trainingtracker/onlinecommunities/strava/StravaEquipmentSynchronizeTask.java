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

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper;

import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;

public class StravaEquipmentSynchronizeTask extends AsyncTask<String, String, String> {
    public static final String SYNCHRONIZE_EQUIPMENT_STRAVA_START = "de.rainerblind.trainingtracker.equipment.StravaEquipmentHelper.SYNCHRONIZE_EQUIPMENT_STRAVA_START";
    public static final String SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED = "de.rainerblind.trainingtracker.equipment.StravaEquipmentHelper.SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED";
    protected static final String STRAVA_URL_ATHLETE = "https://www.strava.com/api/v3/athlete";
    protected static final String STRAVA_URL_GEAR = "https://www.strava.com/api/v3/gear";
    protected static final String AUTHORIZATION = "Authorization";
    protected static final String BEARER = "Bearer";
    protected static final String MESSAGE = "message";
    protected static final String AUTHORIZATION_ERROR = "Authorization Error";
    protected static final String BIKES = "bikes";
    protected static final String SHOES = "shoes";
    protected static final String ID = "id";
    protected static final String NAME = "name";
    protected static final String FRAME_TYPE = "frame_type";
    private static final String TAG = "StravaEquipmentHelperTask";
    private static final boolean DEBUG = false;
    protected Context mContext;
    private final ProgressDialog mProgressDialog;

    public StravaEquipmentSynchronizeTask(Context context) {
        mContext = context;
        mProgressDialog = new ProgressDialog(context);
    }


    @Override
    protected void onPreExecute() {
        mProgressDialog.setMessage(mContext.getString(R.string.getting_equipment_from_strava));
        mProgressDialog.show();
        // mProgressDialog.setCancelable(false);
        // mProgressDialog.setCanceledOnTouchOutside(false);
    }

    @Override
    protected String doInBackground(String... params) {
        return getStravaEquipment();
    }

    protected void onProgressUpdate(String... progress) {
        // TODO: update some Progress Dialog
        mProgressDialog.setMessage(progress[0]);
    }


    @Override
    protected void onPostExecute(String result) {
        if (DEBUG) Log.d(TAG, "updated Strava equipment");

        if (mProgressDialog.isShowing()) {
            try {
                mProgressDialog.dismiss();
                // sometimes this gives the following exception:
                // java.lang.IllegalArgumentException: View not attached to window manager
                // so we catch this exception
            } catch (IllegalArgumentException e) {
                // and nothing
                // http://stackoverflow.com/questions/2745061/java-lang-illegalargumentexception-view-not-attached-to-window-manager
            }
        }

        TrainingApplication.setLastUpdateTimeOfStravaEquipment(result);// DateFormat.getDateTimeInstance().format(new Date()));

        mContext.sendBroadcast(new Intent(SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED));
    }


    private String getStravaEquipment() {
        if (DEBUG) Log.d(TAG, "getStravaEquipment");

        HttpClient httpClient = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(STRAVA_URL_ATHLETE);
        // httpPost.addHeader(AUTHORIZATION, "Bearer " + TrainingApplication.getRunkeeperToken());
        httpGet.addHeader(AUTHORIZATION, BEARER + " " + StravaHelper.getRefreshedAccessToken());

        HttpResponse httpResponse;
        try {
            httpResponse = httpClient.execute(httpGet);

            String response;
            response = EntityUtils.toString(httpResponse.getEntity());
            if (DEBUG) Log.d(TAG, "getStravaEquipment response: " + response);

            JSONObject responseJson = new JSONObject(response);

            if (responseJson.has(MESSAGE)) {
                String message = responseJson.getString(MESSAGE);
                if (DEBUG) Log.d(TAG, message);
                return message;
            }

            return fillDbFromJsonObject(responseJson);

        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            Log.e(TAG, e.toString());
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            Log.e(TAG, e.toString());
            e.printStackTrace();
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return ("updating failed");
    }

    public String fillDbFromJsonObject(JSONObject jsonObject) {
        SQLiteDatabase equipmentDb = new EquipmentDbHelper(mContext).getWritableDatabase();
        ContentValues values = new ContentValues();

        // equipmentDb.delete(EquipmentDbHelper.SHOES, null, null);
        // equipmentDb.delete(EquipmentDbHelper.BIKES, null, null);

        if (DEBUG) Log.d(TAG, "checking strava shoes");
        publishProgress("checking Strava shoes");
        JSONArray shoes;
        try {
            shoes = jsonObject.getJSONArray(SHOES);
            for (int i = 0; i < shoes.length(); i++) {

                JSONObject shoe = shoes.getJSONObject(i);
                String id = shoe.getString(ID);
                String name = shoe.getString(NAME);
                if (DEBUG) Log.d(TAG, "got shoe: " + name + "id: " + id);
                publishProgress(mContext.getString(R.string.got_shoe, name));

                values.clear();
                values.put(EquipmentDbHelper.STRAVA_NAME, name);
                values.put(EquipmentDbHelper.SPORT_TYPE, BSportType.RUN.name());

                int updates = 0;
                try {
                    updates = equipmentDb.update(EquipmentDbHelper.EQUIPMENT,
                            values,
                            EquipmentDbHelper.STRAVA_ID + "=?",
                            new String[]{id});
                    if (DEBUG) Log.d(TAG, "updated shoe " + name + " id: " + id);
                } catch (SQLException e) {
                    if (DEBUG) Log.d(TAG, e.getMessage());
                    if (DEBUG) Log.d(TAG, "Exception! for shoe " + name + " id: " + id);
                }
                if (updates < 1) {  // if nothing is updated, we create the entry
                    if (DEBUG) Log.d(TAG, "adding shoe: " + name + " id: " + id);
                    values.put(EquipmentDbHelper.NAME, name);
                    values.put(EquipmentDbHelper.STRAVA_ID, id);
                    equipmentDb.insert(EquipmentDbHelper.EQUIPMENT, null, values);
                }
            }
        } catch (JSONException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        if (DEBUG) Log.d(TAG, "checking strava bikes");
        JSONArray bikes;
        try {
            bikes = jsonObject.getJSONArray(BIKES);
            for (int i = 0; i < bikes.length(); i++) {
                JSONObject bike = bikes.getJSONObject(i);
                String id = bike.getString(ID);
                String name = bike.getString(NAME);
                publishProgress(mContext.getString(R.string.got_bike, name));

                int frameType = getStravaFrameType(id);
                if (DEBUG) Log.d(TAG, "got frameType for bike " + name + ": " + frameType);

                values.clear();
                values.put(EquipmentDbHelper.STRAVA_NAME, name);
                values.put(EquipmentDbHelper.FRAME_TYPE, frameType);
                values.put(EquipmentDbHelper.SPORT_TYPE, BSportType.BIKE.name());

                int updates = 0;
                try {
                    updates = equipmentDb.update(EquipmentDbHelper.EQUIPMENT,
                            values,
                            EquipmentDbHelper.STRAVA_ID + "=?",
                            new String[]{id});
                } catch (SQLException e) {
                    // do nothing?
                }
                if (updates < 1) {  // if nothing is updated, we create the entry
                    if (DEBUG) Log.d(TAG, "creating bike: " + name);
                    values.put(EquipmentDbHelper.NAME, name);
                    values.put(EquipmentDbHelper.STRAVA_ID, id);
                    equipmentDb.insert(EquipmentDbHelper.EQUIPMENT, null, values);
                }
            }
        } catch (JSONException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        equipmentDb.close();

        return DateFormat.getDateTimeInstance().format(new Date());
    }


    protected int getStravaFrameType(String bikeId) {

        HttpClient httpClient = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(STRAVA_URL_GEAR + "/" + bikeId);
        // httpPost.addHeader(AUTHORIZATION, "Bearer " + TrainingApplication.getRunkeeperToken());
        httpGet.addHeader(AUTHORIZATION, BEARER + " " + StravaHelper.getRefreshedAccessToken());

        try {
            HttpResponse httpResponse = httpClient.execute(httpGet);
            String response;
            response = EntityUtils.toString(httpResponse.getEntity());
            if (DEBUG) Log.d(TAG, "getStravaEquipment response: " + response);

            JSONObject responseJson = new JSONObject(response);
            return responseJson.getInt(FRAME_TYPE);

        } catch (JSONException e) {
            // TODO Auto-generated catch block
            Log.e(TAG, e.toString());
            e.printStackTrace();
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            Log.e(TAG, e.toString());
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return 0;
    }
}
