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

package com.atrainingtracker.trainingtracker.onlinecommunities.strava;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.annotation.NonNull;

import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper;
import com.atrainingtracker.trainingtracker.repositories.EquipmentRepository;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Headless background worker thread for fetching and synchronizing Strava equipment (bikes and shoes).
 *
 * Operates purely as a headless background task without displaying modal progress dialogs,
 * toasts, or window-blocking UI notifications (REQ-EXP-017, ATT-1242).
 *
 * Concurrency & Invariants:
 * - Emits in-memory sync state via {@link EquipmentRepository#setSyncing(boolean)} (REQ-EXP-016).
 * - Enforces two-tier concurrency debounce guard to suppress redundant runs.
 * - Broadcasts {@link #SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED} upon completion for UI receivers.
 * - Decoupled from Activity window contexts; accepts generic {@link Context}.
 */
public class StravaEquipmentSynchronizeThread extends Thread {

    public static final String SYNCHRONIZE_EQUIPMENT_STRAVA_START = "de.rainerblind.trainingtracker.equipment.StravaEquipmentHelper.SYNCHRONIZE_EQUIPMENT_STRAVA_START";
    public static final String SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED = "de.rainerblind.trainingtracker.equipment.StravaEquipmentHelper.SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED";

    private static final String STRAVA_URL_ATHLETE = "https://www.strava.com/api/v3/athlete";
    private static final String STRAVA_URL_GEAR = "https://www.strava.com/api/v3/gear";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer";
    private static final String MESSAGE = "message";
    private static final String BIKES = "bikes";
    private static final String SHOES = "shoes";
    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String FRAME_TYPE = "frame_type";

    private static final String TAG = "StravaEquipmentThread";
    private static final boolean DEBUG = TrainingApplication.getDebug(false);

    private final Context mContext;

    public StravaEquipmentSynchronizeThread(@NonNull Context context) {
        mContext = context;
    }

    @Override
    public void run() {
        if (EquipmentRepository.isSyncing().getValue()) {
            if (DEBUG) Log.w(TAG, "Equipment synchronization already in progress. Ignoring duplicate execution.");
            return;
        }

        EquipmentRepository.setSyncing(true);
        try {
            final String result = getStravaEquipment();
            if (DEBUG) Log.d(TAG, "updated Strava equipment: " + result);

            TrainingApplication.setLastUpdateTimeOfStravaEquipment(result);
            try {
                mContext.sendBroadcast(new Intent(SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED)
                        .setPackage(mContext.getPackageName()));
            } catch (Exception e) {
                if (DEBUG) Log.e(TAG, "Failed to send equipment sync finished broadcast", e);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected error in equipment synchronization thread", t);
        } finally {
            EquipmentRepository.setSyncing(false);
        }
    }

    @NonNull
    private String getStravaEquipment() {
        if (DEBUG) Log.d(TAG, "getStravaEquipment");

        String accessToken = StravaHelper.getRefreshedAccessToken();
        if (accessToken == null || accessToken.trim().isEmpty()) {
            Log.e(TAG, "Not connected to Strava (null or empty access token)");
            return "Not connected to Strava";
        }

        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(STRAVA_URL_ATHLETE);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setRequestProperty(AUTHORIZATION, BEARER + " " + accessToken);
            //urlConnection.addRequestProperty(AUTHORIZATION, BEARER + " " + StravaHelper.getRefreshedAccessToken());
            urlConnection.setConnectTimeout(15000);
            urlConnection.setReadTimeout(15000);

            Map<String, List<String>> headers = urlConnection.getHeaderFields();

            int responseCode = urlConnection.getResponseCode();
            String response = readStream(urlConnection, responseCode);

            if (DEBUG) Log.d(TAG, "getStravaEquipment response: " + response);

            JSONObject responseJson = new JSONObject(response);

            if (responseJson.has(MESSAGE) && responseJson.has("errors")) {
                String message = responseJson.getString(MESSAGE);
                Log.e(TAG, "Strava Error: " + message);
                return message;
            }

            return fillDbFromJsonObject(responseJson);

        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error fetching equipment", e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return "updating failed";
    }

    @NonNull
    String fillDbFromJsonObject(@NonNull JSONObject jsonObject) {
        if (!jsonObject.has(SHOES) && !jsonObject.has(BIKES)) {
            Log.w(TAG, "Strava athlete payload missing gear arrays (profile:read_all permission missing)");
            return "No gear returned (missing profile:read_all permission)";
        }

        try (SQLiteDatabase equipmentDb = new EquipmentDbHelper(mContext).getWritableDatabase()) {
            ContentValues values = new ContentValues();

            if (DEBUG) Log.d(TAG, "checking strava shoes");

            if (jsonObject.has(SHOES)) {
                JSONArray shoes = jsonObject.getJSONArray(SHOES);
                for (int i = 0; i < shoes.length(); i++) {
                    JSONObject shoe = shoes.getJSONObject(i);
                    String id = shoe.getString(ID);
                    String name = shoe.getString(NAME);

                    if (DEBUG) Log.d(TAG, "got shoe: " + name + " id: " + id);

                    values.clear();
                    values.put(EquipmentDbHelper.STRAVA_NAME, name);
                    values.put(EquipmentDbHelper.SPORT_TYPE, BSportType.RUN.name());
                    values.put(EquipmentDbHelper.RETIRED, 0);

                    int updates = equipmentDb.update(EquipmentDbHelper.EQUIPMENT,
                            values,
                            EquipmentDbHelper.STRAVA_ID + "=?",
                            new String[]{id});

                    if (updates < 1) {
                        // Check if an unlinked shoe with matching name already exists locally
                        values.put(EquipmentDbHelper.STRAVA_ID, id);
                        updates = equipmentDb.update(EquipmentDbHelper.EQUIPMENT,
                                values,
                                EquipmentDbHelper.NAME + "=? AND (" + EquipmentDbHelper.STRAVA_ID + " IS NULL OR " + EquipmentDbHelper.STRAVA_ID + "='')",
                                new String[]{name});
                    }

                    if (updates < 1) {
                        if (DEBUG) Log.d(TAG, "adding shoe: " + name + " id: " + id);
                        values.put(EquipmentDbHelper.NAME, name);
                        values.put(EquipmentDbHelper.STRAVA_ID, id);
                        equipmentDb.insert(EquipmentDbHelper.EQUIPMENT, null, values);
                    }
                }
            }

            if (DEBUG) Log.d(TAG, "checking strava bikes");
            if (jsonObject.has(BIKES)) {
                JSONArray bikes = jsonObject.getJSONArray(BIKES);
                for (int i = 0; i < bikes.length(); i++) {
                    JSONObject bike = bikes.getJSONObject(i);
                    String id = bike.getString(ID);
                    String name = bike.getString(NAME);

                    int frameType = getStravaFrameType(id);
                    if (DEBUG) Log.d(TAG, "got frameType for bike " + name + ": " + frameType);

                    values.clear();
                    values.put(EquipmentDbHelper.STRAVA_NAME, name);
                    values.put(EquipmentDbHelper.FRAME_TYPE, frameType);
                    values.put(EquipmentDbHelper.SPORT_TYPE, BSportType.BIKE.name());
                    values.put(EquipmentDbHelper.RETIRED, 0);

                    int updates = equipmentDb.update(EquipmentDbHelper.EQUIPMENT,
                            values,
                            EquipmentDbHelper.STRAVA_ID + "=?",
                            new String[]{id});

                    if (updates < 1) {
                        // Check if an unlinked bike with matching name already exists locally
                        values.put(EquipmentDbHelper.STRAVA_ID, id);
                        updates = equipmentDb.update(EquipmentDbHelper.EQUIPMENT,
                                values,
                                EquipmentDbHelper.NAME + "=? AND (" + EquipmentDbHelper.STRAVA_ID + " IS NULL OR " + EquipmentDbHelper.STRAVA_ID + "='')",
                                new String[]{name});
                    }

                    if (updates < 1) {
                        if (DEBUG) Log.d(TAG, "creating bike: " + name);
                        values.put(EquipmentDbHelper.NAME, name);
                        values.put(EquipmentDbHelper.STRAVA_ID, id);
                        equipmentDb.insert(EquipmentDbHelper.EQUIPMENT, null, values);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing equipment JSON", e);
        }

        return DateFormat.getDateTimeInstance().format(new Date());
    }

    private int getStravaFrameType(String bikeId) {
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(STRAVA_URL_GEAR + "/" + bikeId);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setRequestProperty(AUTHORIZATION, BEARER + " " + StravaHelper.getRefreshedAccessToken());

            int responseCode = urlConnection.getResponseCode();
            String response = readStream(urlConnection, responseCode);

            JSONObject responseJson = new JSONObject(response);
            if (responseJson.has(FRAME_TYPE)) {
                return responseJson.getInt(FRAME_TYPE);
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error fetching frame type for " + bikeId, e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return 0;
    }

    @NonNull
    private String readStream(@NonNull HttpURLConnection connection, int responseCode) throws IOException {
        InputStream inputStream;
        if (responseCode >= 200 && responseCode < 300) {
            inputStream = connection.getInputStream();
        } else {
            inputStream = connection.getErrorStream();
        }

        if (inputStream == null) return "";

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }
}
