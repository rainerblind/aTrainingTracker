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

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.banalservice.helpers.HavePressureSensor;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper;
import com.atrainingtracker.trainingtracker.exporter.ExportInfo;
import com.atrainingtracker.trainingtracker.tcx.model.TcxActivity;
import com.atrainingtracker.trainingtracker.tcx.model.TcxActivityExtensions;
import com.atrainingtracker.trainingtracker.tcx.model.TcxAuthor;
import com.atrainingtracker.trainingtracker.tcx.model.TcxCreator;
import com.atrainingtracker.trainingtracker.tcx.model.TcxDocument;
import com.atrainingtracker.trainingtracker.tcx.model.TcxLap;
import com.atrainingtracker.trainingtracker.tcx.model.TcxLapExtensions;
import com.atrainingtracker.trainingtracker.tcx.model.TcxPosition;
import com.atrainingtracker.trainingtracker.tcx.model.TcxTrackpoint;
import com.atrainingtracker.trainingtracker.tcx.model.TcxTrackpointExtensions;
import com.atrainingtracker.trainingtracker.tcx.serializer.TcxSerializer;
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData;

import com.atrainingtracker.BuildConfig;

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * TCX workout exporter refactored to use structured DTO serialization via {@link TcxSerializer}.
 *
 * Traceability:
 * - REQ-DAT-040: TCX Structured DTO Serialization, Parsing Decoupling & Round-Trip Fidelity.
 * - REQ-EXP-001: Export Interoperability & integer Watts/Cadence.
 * - REQ-EXP-022: Garmin TCX v2 sequence ordering, XML escaping, thread safety.
 */
public class TCXFileWriter extends BaseFileWriter {
    protected static final boolean WRITE_ONLY_ON_NEW_GEO_DATA = true;
    private static final String TAG = "TCXFileExporter";
    private static final boolean DEBUG = false;
    // DateFormats to convert from Db style dates to XML style dates (UTC)
    private static final DateTimeFormatter DB_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter XML_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private static final String GARMIN_AUTHOR_LANG_ID = "en";
    private static final String GARMIN_AUTHOR_PART_NUMBER = "000-00000-00";
    private static final int DEFAULT_VERSION_MAJOR = 4;
    private static final int DEFAULT_VERSION_MINOR = 9;

    public TCXFileWriter(@NonNull Context context) {
        super(context);
    }

    // convert from "2012-03-29 16:23:05" to "2012-03-29T16:23:05Z"
    @NonNull
    protected static String dbTime2XMLTime(@NonNull String dbTime) throws ParseException {
        try {
            LocalDateTime ldt = LocalDateTime.parse(dbTime, DB_TIME_FORMATTER);
            return ldt.atOffset(ZoneOffset.UTC).format(XML_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new ParseException("Unparseable date: " + dbTime, 0);
        }
    }

    @NonNull
    public static String escapeXml(String text) {
        return TcxSerializer.INSTANCE.escapeXml(text);
    }

    /**
     * Extracts the major version number from the application version name.
     * Guaranteed never to throw, falling back to {@link #DEFAULT_VERSION_MAJOR} on failure.
     *
     * @return parsed major version number
     */
    protected static int parseVersionMajor() {
        try {
            String versionName = BuildConfig.VERSION_NAME;
            if (versionName != null && !versionName.isEmpty()) {
                String[] parts = versionName.split("[.-]");
                if (parts.length > 0 && !parts[0].isEmpty()) {
                    return Integer.parseInt(parts[0]);
                }
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_VERSION_MAJOR;
    }

    /**
     * Extracts the minor version number from the application version name.
     * Guaranteed never to throw, falling back to {@link #DEFAULT_VERSION_MINOR} on failure.
     *
     * @return parsed minor version number
     */
    protected static int parseVersionMinor() {
        try {
            String versionName = BuildConfig.VERSION_NAME;
            if (versionName != null && !versionName.isEmpty()) {
                String[] parts = versionName.split("[.-]");
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    return Integer.parseInt(parts[1]);
                }
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_VERSION_MINOR;
    }

    @NonNull
    @Override
    protected ExportResult doExport(@NonNull ExportInfo exportInfo)
            throws IOException, ParseException {
        if (DEBUG) Log.d(TAG, "exportToFile");

        getHeaderData(exportInfo);

        try {
            List<LapData> lapsList = LapsDatabaseManager.getInstance(mContext).getLaps(workoutID);
            Map<Long, LapData> lapDataMap = new HashMap<>();
            for (LapData lapData : lapsList) {
                lapDataMap.put(lapData.getLapNr(), lapData);
            }

            List<TcxLap> tcxLaps = new ArrayList<>();
            List<TcxTrackpoint> currentTrackpoints = new ArrayList<>();
            String currentLapStartTime = null;
            String totalTime = "0";
            String totalDistance = "0";

            SQLiteDatabase db = WorkoutSamplesDatabaseManager.getInstance(mContext).getDatabase();
            try (Cursor cursor = db.query(WorkoutSamplesDatabaseManager.getTableName(exportInfo.getFileBaseName()),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null)) {

                double latitude = 0.0;
                double longitude = 0.0;
                double latitudeOld;
                double longitudeOld;

                long prevLineLap = BANALService.INIT_LAP_NR - 1;

                while (cursor != null && cursor.moveToNext()) {
                    if (isCancelled()) {
                        throw new CancellationException("TCX export cancelled at start of loop iteration");
                    }

                    int lap = cursor.getInt(cursor.getColumnIndexOrThrow(SensorType.LAP_NR.name()));
                    if (prevLineLap != lap) { // new lap
                        if (prevLineLap != BANALService.INIT_LAP_NR - 1) { // finish previous lap
                            tcxLaps.add(buildLap(prevLineLap, currentLapStartTime, totalTime, totalDistance, currentTrackpoints, lapDataMap));
                            currentTrackpoints = new ArrayList<>();
                        }

                        // get the lap data
                        LapData lapData = lapDataMap.get((long) lap);
                        if (lapData != null) {
                            totalTime = String.valueOf(lapData.getTimeTotalS());
                            totalDistance = String.valueOf(lapData.getDistanceTotalM());
                        } else {
                            SQLiteDatabase lapDb = LapsDatabaseManager.getInstance(mContext).getDatabase();
                            try (Cursor lapCursor = lapDb.query(LapsDatabaseManager.Laps.TABLE,
                                    null,
                                    LapsDatabaseManager.Laps.WORKOUT_ID + "=? AND " + LapsDatabaseManager.Laps.LAP_NR + "=?",
                                    new String[]{workoutID + "", lap + ""},
                                    null,
                                    null,
                                    null)) {
                                if (lapCursor.moveToFirst()) {
                                    totalTime = myGet(lapCursor, LapsDatabaseManager.Laps.TIME_TOTAL_s, "0");
                                    totalDistance = myGet(lapCursor, LapsDatabaseManager.Laps.DISTANCE_TOTAL_m, "0");
                                } else {
                                    totalTime = "0";
                                    totalDistance = "0";
                                }
                            }
                        }

                        currentLapStartTime = dbTime2XMLTime(cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSamplesDbHelper.TIME)));
                    }
                    prevLineLap = lap;

                    String tpTime = dbTime2XMLTime(cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSamplesDbHelper.TIME)));

                    TcxPosition position = null;
                    // we do not write location data when it was a (indoor) trainer session
                    if (!indoorTrainerSession && haveGeo && dataValid(cursor, SensorType.LATITUDE.name()) && dataValid(cursor, SensorType.LONGITUDE.name())) {
                        latitudeOld = latitude;
                        longitudeOld = longitude;
                        latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.LATITUDE.name()));
                        longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.LONGITUDE.name()));

                        if (!(WRITE_ONLY_ON_NEW_GEO_DATA && latitude == latitudeOld && longitude == longitudeOld)) {
                            position = new TcxPosition(latitude, longitude);
                        }
                    }

                    Double altitude = null;
                    if (haveAltitude && dataValid(cursor, SensorType.ALTITUDE.name())) {
                        double alt = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.ALTITUDE.name()));
                        if (alt >= -500.0 && alt <= 9000.0) {
                            altitude = alt;
                        }
                    }

                    Double distance = null;
                    if (haveDistance && dataValid(cursor, SensorType.DISTANCE_m.name())) {
                        distance = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.DISTANCE_m.name()));
                    }

                    Integer hrVal = null;
                    if (haveHR && dataValid(cursor, SensorType.HR.name())) {
                        hrVal = cursor.getInt(cursor.getColumnIndexOrThrow(SensorType.HR.name()));
                    }

                    Integer cadenceVal = null;
                    if (haveBikeCadence && dataValid(cursor, SensorType.CADENCE.name())) {
                        cadenceVal = (int) Math.round(cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.CADENCE.name())));
                    }

                    Double speedVal = null;
                    Integer powerVal = null;
                    Integer runCadenceVal = null;

                    if (haveSpeed && dataValid(cursor, SensorType.SPEED_mps.name())) {
                        speedVal = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.SPEED_mps.name()));
                    }

                    if (havePower && dataValid(cursor, SensorType.POWER.name())) {
                        powerVal = (int) Math.round(cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.POWER.name())));
                    }

                    if (haveRunCadence && dataValid(cursor, SensorType.CADENCE.name())) {
                        runCadenceVal = (int) Math.round(cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.CADENCE.name())));
                    }

                    TcxTrackpointExtensions tpExt = (speedVal != null || powerVal != null || runCadenceVal != null)
                            ? new TcxTrackpointExtensions(speedVal, powerVal, runCadenceVal)
                            : null;

                    currentTrackpoints.add(new TcxTrackpoint(
                            tpTime,
                            position,
                            altitude,
                            distance,
                            hrVal,
                            cadenceVal,
                            tpExt
                    ));

                    if (isCancelled()) {
                        throw new CancellationException("TCX export cancelled post-I/O");
                    }
                }

                if (prevLineLap != BANALService.INIT_LAP_NR - 1) {
                    tcxLaps.add(buildLap(prevLineLap, currentLapStartTime, totalTime, totalDistance, currentTrackpoints, lapDataMap));
                }
            } // close cursor try

            String activityWorkoutName = (workoutName != null && !workoutName.trim().isEmpty() && !workoutName.equals(exportInfo.getFileBaseName()))
                    ? workoutName.trim() : null;
            String activityDesc = (description != null && !description.trim().isEmpty())
                    ? description.trim() : null;

            String activityNotes = null;
            if (activityWorkoutName != null || activityDesc != null) {
                if (activityWorkoutName != null && activityDesc != null) {
                    activityNotes = "[" + activityWorkoutName + "] " + activityDesc;
                } else if (activityWorkoutName != null) {
                    activityNotes = "[" + activityWorkoutName + "]";
                } else {
                    activityNotes = activityDesc;
                }
            }

            TcxActivityExtensions actExt = (activityWorkoutName != null || activityDesc != null)
                    ? new TcxActivityExtensions(activityWorkoutName, activityDesc)
                    : null;

            TcxCreator creator = new TcxCreator(
                    android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL,
                    0L,
                    1,
                    parseVersionMajor(),
                    parseVersionMinor(),
                    BuildConfig.VERSION_CODE,
                    0
            );

            String authorName = TrainingApplication.getAppName();
            if (HavePressureSensor.havePressureSensor(mContext)) {
                authorName += " with barometer";
            }
            TcxAuthor author = new TcxAuthor(
                    authorName,
                    parseVersionMajor(),
                    parseVersionMinor(),
                    BuildConfig.VERSION_CODE,
                    0,
                    "Release",
                    GARMIN_AUTHOR_LANG_ID,
                    GARMIN_AUTHOR_PART_NUMBER
            );

            TcxActivity activity = new TcxActivity(
                    SportTypeDatabaseManager.getInstance(mContext).getTcxName(sportTypeId),
                    dbTime2XMLTime(startTime),
                    tcxLaps,
                    activityNotes,
                    creator,
                    actExt
            );

            TcxDocument document = new TcxDocument(
                    Collections.singletonList(activity),
                    author
            );

            try (BufferedWriter bufferedWriter = getBufferedWriter(exportInfo.getShortPath())) {
                TcxSerializer.INSTANCE.serialize(document, bufferedWriter);
            }

        } catch (CancellationException e) {
            deleteOutputFileSafely(exportInfo);
            throw e;
        }

        return new ExportResult(true, false, "Successfully exported to TCX File");
    }

    private TcxLap buildLap(long lapNr, String startTime, String totalTime, String totalDistance,
                            List<TcxTrackpoint> trackpoints, Map<Long, LapData> lapDataMap) {
        String lapNotes = null;
        TcxLapExtensions lapExt = null;

        LapData lapData = lapDataMap.get(lapNr);
        if (lapData != null) {
            String name = lapData.getName();
            String desc = lapData.getDescription();
            boolean hasName = name != null && !name.trim().isEmpty();
            boolean hasDesc = desc != null && !desc.trim().isEmpty();

            if (hasName || hasDesc) {
                if (hasName && hasDesc) {
                    lapNotes = "[" + name.trim() + "] " + desc.trim();
                } else if (hasName) {
                    lapNotes = "[" + name.trim() + "]";
                } else {
                    lapNotes = desc.trim();
                }
                lapExt = new TcxLapExtensions(hasName ? name.trim() : null, hasDesc ? desc.trim() : null);
            }
        }

        double totalTimeSec = 0.0;
        try {
            totalTimeSec = Double.parseDouble(totalTime);
        } catch (Exception ignored) {
        }

        double totalDistM = 0.0;
        try {
            totalDistM = Double.parseDouble(totalDistance);
        } catch (Exception ignored) {
        }

        return new TcxLap(
                startTime != null ? startTime : "",
                totalTimeSec,
                totalDistM,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                trackpoints,
                lapNotes,
                lapExt
        );
    }
}
