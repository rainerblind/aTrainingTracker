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
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class GCFileExporter extends BaseFileExporter {
    protected static final boolean WRITE_ONLY_ON_NEW_GEO_DATA = true;
    private static final String TAG = "GCFileExporter";
    private static final boolean DEBUG = false;
    // DateFormats to convert from Db style dates to Strava style dates
    protected static final SimpleDateFormat msdfFromDb = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    protected static final SimpleDateFormat msdfToGC = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);

    public GCFileExporter(@NonNull Context context) {
        super(context);
    }

    @NonNull
    protected static String dbTime2GCTime(@NonNull String dbTime) throws ParseException {
        return msdfToGC.format(msdfFromDb.parse(dbTime));
    }

    @NonNull
    @Override
    protected ExportResult doExport(@NonNull ExportInfo exportInfo, @NonNull IExportProgressListener progressListener)
            throws IOException, IllegalArgumentException, JSONException, ParseException {
        if (DEBUG) Log.d(TAG, "exportWorkoutToFile");

        final String FORMAT_qq = "    \"%s\":\"%s\",\n";
        final String FORMAT_q = "    \"%s\":%s,\n";

        final String RIDE = "RIDE";
        final String STARTTIME = "STARTTIME";
        final String RECINTSECS = "RECINTSECS";
        final String DEVICETYPE = "DEVICETYPE";
        final String IDENTIFIER = "IDENTIFIER";
        final String SAMPLES = "SAMPLES";
        final String TAGS = "TAGS";
        final String ATHLETE = "Athlete";
        final String DATA = "Data";
        final String SPORT = "Sport";
        final String WORKOUT_CODE = "Workout Code";
        final String SECS = "SECS";
        final String KM = "KM";
        final String KPH = "KPH";
        final String WATTS = "WATTS";
        final String HR = "HR";
        final String CAD = "CAD";
        final String NM = "NM";
        final String ALT = "ALT";
        final String LAT = "LAT";
        final String LON = "LON";
        final String LRBALANCE = "LRBALANCE";
        final String LTE = "LTE";
        final String RTE = "RTE";
        final String LPS = "LPS";
        final String RPS = "RPS";

        getHeaderData(exportInfo);

        BufferedWriter bufferedWriter = getBufferedWriter(exportInfo.getShortPath());

        //TODO: use constants and String.format()
        // write the header data to the file
        bufferedWriter.write("{\n");
        bufferedWriter.write("  \"RIDE\":{\n");
        bufferedWriter.write(String.format(FORMAT_qq, STARTTIME, dbTime2GCTime(startTime) + " UTC"));
        bufferedWriter.write(String.format(FORMAT_q, RECINTSECS, samplingTime));
        bufferedWriter.write(String.format(FORMAT_qq, DEVICETYPE, TrainingApplication.getAppName()));
        bufferedWriter.write(String.format(FORMAT_qq, IDENTIFIER, ""));
        bufferedWriter.write(String.format(FORMAT_q, TAGS, (new JSONObject())
                .put(ATHLETE, athleteName)
                .put(DATA, data)
                .put(SPORT, SportTypeDatabaseManager.getGcName(sportTypeId))
                .put(WORKOUT_CODE, goal + " " + method)));

        bufferedWriter.write("        \"SAMPLES\":[\n");

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

        JSONObject sample;
        boolean isFirst = true;

        double latitude = 0.0;
        double longitude = 0.0;
        double latitudeOld;
        double longitudeOld;

        while (cursor.moveToNext()) {
            if (dataValid(cursor, SensorType.TIME_TOTAL.name())) {

                sample = new JSONObject();

                sample.put(SECS, cursor.getInt(cursor.getColumnIndexOrThrow(SensorType.TIME_TOTAL.name())));

                // TODO: loop over ???
                // TODO Locale should be US if , is used as separator
                if (haveDistance && dataValid(cursor, SensorType.DISTANCE_m.name())) {
                    sample.put(KM, String.format(Locale.getDefault(), "%.3f", cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.DISTANCE_m.name())) / 1000));
                }

                if (haveSpeed && dataValid(cursor, SensorType.SPEED_mps.name())) {
                    sample.put(KPH, String.format(Locale.getDefault(), "%.3f", cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.SPEED_mps.name())) * 3.6));
                }

                if (havePower && dataValid(cursor, SensorType.POWER.name())) {
                    sample.put(WATTS, String.format(Locale.getDefault(), "%.3f", cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.POWER.name()))));
                }
                if (havePower && dataValid(cursor, SensorType.PEDAL_POWER_BALANCE.name())) {
                    sample.put(LRBALANCE, String.format(Locale.getDefault(), "%.3f", cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.PEDAL_POWER_BALANCE.name()))));
                }
                if (havePower && dataValid(cursor, SensorType.TORQUE_EFFECTIVENESS_L.name())) {
                    sample.put(LTE, String.format(Locale.getDefault(), "%.3f", cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.TORQUE_EFFECTIVENESS_L.name()))));
                }
                if (havePower && dataValid(cursor, SensorType.TORQUE_EFFECTIVENESS_R.name())) {
                    sample.put(RTE, String.format(Locale.getDefault(), "%.3f", cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.TORQUE_EFFECTIVENESS_R.name()))));
                }
                if (havePower && dataValid(cursor, SensorType.PEDAL_SMOOTHNESS_L.name())) {
                    sample.put(LPS, String.format(Locale.getDefault(), "%.3f", cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.PEDAL_SMOOTHNESS_L.name()))));
                }
                if (havePower && dataValid(cursor, SensorType.PEDAL_SMOOTHNESS_R.name())) {
                    sample.put(RPS, String.format(Locale.getDefault(), "%.3f", cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.PEDAL_SMOOTHNESS_R.name()))));
                }

                if (haveHR && dataValid(cursor, SensorType.HR.name())) {
                    sample.put(HR, cursor.getInt(cursor.getColumnIndexOrThrow(SensorType.HR.name())));
                }

                if (haveCadence && dataValid(cursor, SensorType.CADENCE.name())) {
                    sample.put(CAD, String.format(Locale.getDefault(), "%.3f", cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.CADENCE.name()))));
                }

                if (haveTorque & dataValid(cursor, SensorType.TORQUE.name())) {
                    sample.put(NM, String.format(Locale.getDefault(), "%.3f", cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.TORQUE.name()))));
                }

                if (haveAltitude && dataValid(cursor, SensorType.ALTITUDE.name())) {
                    sample.put(ALT, String.format(Locale.getDefault(), "%.3f", cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.ALTITUDE.name()))));
                }

                // we do not write location data when it was an (indoor) trainer session
                if (!indoorTrainerSession && haveGeo && dataValid(cursor, SensorType.LATITUDE.name()) && dataValid(cursor, SensorType.LATITUDE.name())) {
                    latitudeOld = latitude;
                    longitudeOld = longitude;
                    latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.LATITUDE.name()));
                    longitude = cursor.getFloat(cursor.getColumnIndexOrThrow(SensorType.LONGITUDE.name()));
                    if (WRITE_ONLY_ON_NEW_GEO_DATA && latitude == latitudeOld && longitude == longitudeOld) {
                        // do nothing
                    } else {
                        sample.put(LAT, latitude);
                        sample.put(LON, longitude);
                    }
                }

                bufferedWriter.write(getSamplePrefix(isFirst) + sample);
                isFirst = false;
            }

            progressListener.onProgress(lines, count++);
        }

        bufferedWriter.write("\n        ]\n");
        bufferedWriter.write("    }\n");
        bufferedWriter.write("}\n");
        bufferedWriter.close();

        // TODO: which order ???
        cursor.close();
        databaseManager.closeDatabase();

        return new ExportResult(true, false, "Successfully exported to GC File");

    }

    @NonNull
    @Override
    protected Action getAction() {
        return Action.EXPORT;
    }

}
