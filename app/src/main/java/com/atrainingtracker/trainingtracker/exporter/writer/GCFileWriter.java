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

package com.atrainingtracker.trainingtracker.exporter.writer;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.annotation.NonNull;

import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager;
import com.atrainingtracker.trainingtracker.exporter.ExportInfo;
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Exporter responsible for generating GoldenCheetah-compatible JSON ride files (.json).
 * Conforms to GoldenCheetah Ride JSON specification (JsonRideFile.y, REQ-DAT-027).
 */
public class GCFileWriter extends BaseFileWriter {
    protected static final boolean WRITE_ONLY_ON_NEW_GEO_DATA = true;
    private static final String TAG = "GCFileExporter";
    private static final boolean DEBUG = false;

    public GCFileWriter(@NonNull Context context) {
        super(context);
    }

    /**
     * Converts SQLite UTC timestamp (yyyy-MM-dd HH:mm:ss) into GoldenCheetah ride timestamp (yyyy/MM/dd HH:mm:ss).
     * Thread-safe and locale-independent (REQ-DAT-027).
     *
     * @param dbTime Start time in SQLite UTC format.
     * @return Formatted GoldenCheetah UTC timestamp string.
     * @throws ParseException If the database timestamp cannot be parsed.
     */
    @NonNull
    protected static String dbTime2GCTime(@NonNull String dbTime) throws ParseException {
        SimpleDateFormat msdfFromDb = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        msdfFromDb.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat msdfToGC = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);
        msdfToGC.setTimeZone(TimeZone.getTimeZone("UTC"));
        return msdfToGC.format(msdfFromDb.parse(dbTime));
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    @NonNull
    @Override
    protected ExportResult doExport(@NonNull ExportInfo exportInfo)
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

        try (BufferedWriter bufferedWriter = getBufferedWriter(exportInfo.getShortPath())) {
            bufferedWriter.write("{\n");
            bufferedWriter.write("  \"RIDE\":{\n");
            if (startTime != null && !startTime.isEmpty()) {
                bufferedWriter.write(String.format(FORMAT_qq, STARTTIME, dbTime2GCTime(startTime) + " UTC"));
            }
            bufferedWriter.write(String.format(FORMAT_q, RECINTSECS, 1));
            bufferedWriter.write(String.format(FORMAT_qq, DEVICETYPE, TrainingApplication.getAppName()));
            bufferedWriter.write(String.format(FORMAT_qq, IDENTIFIER, ""));

            JSONObject tags = new JSONObject();
            tags.put(DATA, data);
            tags.put(SPORT, SportTypeDatabaseManager.getInstance(mContext).getGcName(sportTypeId));
            String workoutCode = (goal + " " + method).trim();
            tags.put(WORKOUT_CODE, workoutCode);
            if (workoutName != null && !workoutName.trim().isEmpty()) {
                tags.put("Workout Title", workoutName.trim());
            }
            if (description != null && !description.trim().isEmpty()) {
                tags.put("Notes", description.trim());
            }
            tags.put("Device", TrainingApplication.getAppName());
            bufferedWriter.write(String.format(FORMAT_q, TAGS, tags.toString()));

            // INTERVALS block (REQ-DAT-027)
            List<LapData> laps = LapsDatabaseManager.getInstance(mContext).getLaps(workoutID);
            if (laps != null && !laps.isEmpty()) {
                JSONArray intervalsArray = new JSONArray();
                int currentSec = 0;
                for (LapData lap : laps) {
                    JSONObject intervalObj = new JSONObject();
                    String lapName = lap.getName();
                    if (lapName == null || lapName.trim().isEmpty()) {
                        lapName = "Lap " + lap.getLapNr();
                    }
                    intervalObj.put("NAME", lapName);
                    intervalObj.put("START", currentSec);
                    currentSec += lap.getTimeTotalS();
                    intervalObj.put("STOP", currentSec);
                    intervalObj.put("COLOR", "#000000");
                    intervalsArray.put(intervalObj);
                }
                bufferedWriter.write("    \"INTERVALS\":" + intervalsArray.toString() + ",\n");
            }

            bufferedWriter.write("    \"SAMPLES\":[\n");

            WorkoutSamplesDatabaseManager databaseManager = WorkoutSamplesDatabaseManager.getInstance(mContext);
            SQLiteDatabase db = databaseManager.getDatabase();
            try (Cursor cursor = db.query(WorkoutSamplesDatabaseManager.getTableName(exportInfo.getFileBaseName()),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null)) {

                if (cursor != null) {
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

                            if (haveDistance && dataValid(cursor, SensorType.DISTANCE_m.name())) {
                                sample.put(KM, round3(cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.DISTANCE_m.name())) / 1000.0));
                            }

                            if (haveSpeed && dataValid(cursor, SensorType.SPEED_mps.name())) {
                                sample.put(KPH, round3(cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.SPEED_mps.name())) * 3.6));
                            }

                            if (havePower && dataValid(cursor, SensorType.POWER.name())) {
                                sample.put(WATTS, round1(cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.POWER.name()))));
                            }
                            if (havePower && dataValid(cursor, SensorType.PEDAL_POWER_BALANCE.name())) {
                                sample.put(LRBALANCE, round1(cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.PEDAL_POWER_BALANCE.name()))));
                            }
                            if (havePower && dataValid(cursor, SensorType.TORQUE_EFFECTIVENESS_L.name())) {
                                sample.put(LTE, round1(cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.TORQUE_EFFECTIVENESS_L.name()))));
                            }
                            if (havePower && dataValid(cursor, SensorType.TORQUE_EFFECTIVENESS_R.name())) {
                                sample.put(RTE, round1(cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.TORQUE_EFFECTIVENESS_R.name()))));
                            }
                            if (havePower && dataValid(cursor, SensorType.PEDAL_SMOOTHNESS_L.name())) {
                                sample.put(LPS, round1(cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.PEDAL_SMOOTHNESS_L.name()))));
                            }
                            if (havePower && dataValid(cursor, SensorType.PEDAL_SMOOTHNESS_R.name())) {
                                sample.put(RPS, round1(cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.PEDAL_SMOOTHNESS_R.name()))));
                            }

                            if (haveHR && dataValid(cursor, SensorType.HR.name())) {
                                sample.put(HR, cursor.getInt(cursor.getColumnIndexOrThrow(SensorType.HR.name())));
                            }

                            if (haveCadence && dataValid(cursor, SensorType.CADENCE.name())) {
                                sample.put(CAD, round1(cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.CADENCE.name()))));
                            }

                            if (haveTorque && dataValid(cursor, SensorType.TORQUE.name())) {
                                sample.put(NM, round3(cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.TORQUE.name()))));
                            }

                            if (haveAltitude && dataValid(cursor, SensorType.ALTITUDE.name())) {
                                sample.put(ALT, round1(cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.ALTITUDE.name()))));
                            }

                            // We do not write location data when it was an (indoor) trainer session.
                            // Both LATITUDE and LONGITUDE are validated (REQ-DAT-027).
                            if (!indoorTrainerSession && haveGeo
                                    && dataValid(cursor, SensorType.LATITUDE.name())
                                    && dataValid(cursor, SensorType.LONGITUDE.name())) {
                                latitudeOld = latitude;
                                longitudeOld = longitude;
                                latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.LATITUDE.name()));
                                longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.LONGITUDE.name()));
                                if (WRITE_ONLY_ON_NEW_GEO_DATA && latitude == latitudeOld && longitude == longitudeOld) {
                                    // do nothing
                                } else {
                                    sample.put(LAT, latitude);
                                    sample.put(LON, longitude);
                                }
                            }

                            bufferedWriter.write(getSamplePrefix(isFirst) + sample.toString());
                            isFirst = false;
                        }
                    }
                }
            }

            bufferedWriter.write("\n    ]\n");
            bufferedWriter.write("  }\n");
            bufferedWriter.write("}\n");
        }

        return new ExportResult(true, false, "Successfully exported to GC File");
    }
}
