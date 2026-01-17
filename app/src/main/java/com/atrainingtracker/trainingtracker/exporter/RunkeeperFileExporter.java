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

package com.atrainingtracker.trainingtracker.exporter;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;


public class RunkeeperFileExporter extends BaseFileExporter {
    protected static final boolean WRITE_ONLY_ON_NEW_GEO_DATA = true;
    private static final String TAG = "RunkeeperFileExporter";
    private static final boolean DEBUG = true;
    // DateFormats to convert from Db style dates to XML style dates
    protected static final SimpleDateFormat msdfFromDb = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    protected static final SimpleDateFormat msdfToRK = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US);
    // Sat, 1 Jan 2011 00:00:00

    public RunkeeperFileExporter(@NonNull Context context) {
        super(context);
    }

    @NonNull
    protected static String dbTime2RunkeeperTime(@NonNull String dbTime) throws ParseException {
        return msdfToRK.format(msdfFromDb.parse(dbTime));
    }

    @NonNull
    @Override
    protected ExportResult doExport(@NonNull ExportInfo exportInfo) throws IOException, IllegalArgumentException, ParseException, JSONException {
        if (DEBUG) Log.d(TAG, "exportToFile");

        final String FORMAT_qq = "  \"%s\":\"%s\",\n";
        final String FORMAT_q = "  \"%s\":%s,\n";

        final String TYPE = "type";
        final String START_TIME = "start_time";
        final String TOTAL_DISTANCE = "total_distance";
        final String DURATION = "duration";
        final String NOTES = "notes";
        final String HAS_PATH = "has_path";

        final String HEART_RATE = "heart_rate";
        final String PATH = "path";
        final String TIMESTAMP = "timestamp";
        final String LATITUDE = "latitude";
        final String LONGITUDE = "longitude";
        final String ALTITUDE = "altitude";

        final String START = "start";
        final String END = "end";
        final String GPS = "gps";
        final String PAUSE = "pause";
        final String RESUME = "resume";
        final String MANUAL = "manual";

        getHeaderData(exportInfo);

        BufferedWriter bufferedWriter = getBufferedWriter(exportInfo.getShortPath());

        // if (!haveGeo) {
        // return new ExportResult(false, "No GPS Data");
        // }

        bufferedWriter.write("{\n");
        bufferedWriter.write(String.format(FORMAT_qq, TYPE, SportTypeDatabaseManager.getRunkeeperName(sportTypeId)));
        bufferedWriter.write(String.format(FORMAT_q, HAS_PATH, haveGeo));

        // TODO: check before writing!
        startTime = WorkoutSummariesDatabaseManager.getStartTime(exportInfo.getFileBaseName(), "localtime");
        bufferedWriter.write(String.format(FORMAT_qq, START_TIME, dbTime2RunkeeperTime(startTime)));
        bufferedWriter.write(String.format(FORMAT_q, TOTAL_DISTANCE, totalDistance)); // double, in meters
        bufferedWriter.write(String.format(FORMAT_q, DURATION, totalTime)); // double, in seconds
        bufferedWriter.write(String.format(FORMAT_qq, NOTES, description));  // or something else?

        WorkoutSamplesDatabaseManager databaseManager = WorkoutSamplesDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = db.query(WorkoutSamplesDatabaseManager.getTableName(exportInfo.getFileBaseName()),
                null,
                null,
                null,
                null,
                null,
                null);

        int lines = cursor.getCount();
        int count = 0;
        boolean isFirst = true;

        JSONObject sample;

        int dataPointsHR = 0;

        // first, write the heart rate
        if (haveHR) {
            bufferedWriter.write("  \"heart_rate\": [");
            while (cursor.moveToNext()) {
                if (dataValid(cursor, SensorType.HR.name())) {
                    dataPointsHR++;

                    sample = new JSONObject();
                    sample.put(TIMESTAMP, cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.TIME_TOTAL.name())));
                    sample.put(HEART_RATE, Math.round(cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.HR.name()))));
                    bufferedWriter.write(getSamplePrefix(isFirst) + sample);

                    isFirst = false;
                }
            }
            bufferedWriter.write("\n  ],\n");
        }


        // now, write the path
        if (haveGeo) {
            bufferedWriter.write("  \"path\": [");
            String type;
            isFirst = true;

            double latitude = 0.0;
            double longitude = 0.0;
            double latitudeOld;
            double longitudeOld;

            int dataPointsPos = 0;

            cursor.moveToFirst();
            cursor.moveToPrevious();
            while (cursor.moveToNext()) {
                if (dataValid(cursor, SensorType.LATITUDE.name())
                        & dataValid(cursor, SensorType.LONGITUDE.name())
                        & dataValid(cursor, SensorType.ACCURACY.name())) {

                    latitudeOld = latitude;
                    longitudeOld = longitude;
                    latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.LATITUDE.name()));
                    longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.LONGITUDE.name()));

                    if (WRITE_ONLY_ON_NEW_GEO_DATA && latitude == latitudeOld && longitude == longitudeOld) {
                        // do nothing
                    } else {
                        dataPointsPos++;

                        sample = new JSONObject();

                        sample.put(TIMESTAMP, cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.TIME_TOTAL.name())));
                        sample.put(LATITUDE, latitude);
                        sample.put(LONGITUDE, longitude);
                        sample.put(ALTITUDE, cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.ALTITUDE.name())));
                        if (cursor.isFirst()) {
                            type = START;
                        } else if (cursor.isLast()) {
                            type = END;
                        } else {
                            type = GPS;
                        }
                        sample.put(TYPE, type);

                        bufferedWriter.write(getSamplePrefix(isFirst) + sample);
                        isFirst = false;
                    }
                }
            }

            bufferedWriter.write("\n  ]\n");
        }

        bufferedWriter.write("}\n");
        bufferedWriter.close();

        // TODO: which order ???
        cursor.close();
        databaseManager.closeDatabase(); // db.close();

        return new ExportResult(true, false, "successfully exported a Runkeeper file");

        // int dataPoints = (dataPointsHR > dataPointsPos) ? dataPointsHR : dataPointsPos;
        //
        // if (dataPoints > MIN_DATA_POINTS_FOR_UPLOAD) {
        //    return new ExportResult(true, getPositiveAnswer(exportInfo));
        // }
        // else {
        //    return new ExportResult(false, "only "+ dataPoints + "data points");
        // }
    }

}
