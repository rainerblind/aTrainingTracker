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
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.exporter.ExportInfo;
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData;
import com.garmin.fit.Activity;
import com.garmin.fit.ActivityMesg;
import com.garmin.fit.DateTime;
import com.garmin.fit.FileEncoder;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.Fit;
import com.garmin.fit.Intensity;
import com.garmin.fit.LapMesg;
import com.garmin.fit.Manufacturer;
import com.garmin.fit.RecordMesg;
import com.garmin.fit.SessionMesg;
import com.garmin.fit.Sport;
import com.garmin.fit.SubSport;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * High-fidelity binary workout exporter implementing the Garmin FIT protocol
 * utilizing the official Garmin FIT SDK (com.garmin:fit).
 * Encodes FileIdMesg, RecordMesg trackpoints, LapMesg splits, SessionMesg summaries,
 * and ActivityMesg termination conforming to FIT Protocol V2.0.
 */
public class FitFileWriter extends BaseFileWriter {
    private static final String TAG = "FitFileWriter";
    private static final boolean DEBUG = false;

    private static final double SEMICIRCLES_FACTOR = 2147483648.0 / 180.0;
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    public FitFileWriter(@NonNull Context context) {
        super(context);
    }

    @NonNull
    @Override
    protected ExportResult doExport(@NonNull ExportInfo exportInfo) throws IOException, ParseException {
        if (DEBUG) Log.d(TAG, "doExport: " + exportInfo.getFileBaseName());

        getHeaderData(exportInfo);

        File outputFile = new File(getBaseDirFile(mContext), exportInfo.getShortPath());
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory: " + parentDir.getAbsolutePath());
            }
        }

        FileEncoder encoder = new FileEncoder(outputFile, Fit.ProtocolVersion.V2_0);

        try {
            Date startDate;
            try {
                startDate = mDateFormat.parse(startTime);
            } catch (Exception e) {
                startDate = new Date();
            }

            double totalTimeSec = 0.0;
            try {
                totalTimeSec = Double.parseDouble(totalTime);
            } catch (Exception ignored) {}

            Date endDate = new Date(startDate.getTime() + (long) (totalTimeSec * 1000.0));

            // 1. FileIdMesg (mandatory first message)
            FileIdMesg fileIdMesg = new FileIdMesg();
            fileIdMesg.setType(com.garmin.fit.File.ACTIVITY);
            fileIdMesg.setManufacturer(Manufacturer.DEVELOPMENT);
            fileIdMesg.setProduct(1);
            fileIdMesg.setSerialNumber(1L);
            fileIdMesg.setTimeCreated(new DateTime(startDate));
            encoder.write(fileIdMesg);

            // Determine sport mapping
            Sport sport = Sport.GENERIC;
            SubSport subSport = SubSport.GENERIC;

            BSportType bSport = SportTypeDatabaseManager.getInstance(mContext).getBSportType(sportTypeId);
            String tcxName = SportTypeDatabaseManager.getInstance(mContext).getTcxName(sportTypeId);
            if (tcxName == null) tcxName = "";
            String tcxLower = tcxName.toLowerCase(Locale.US);

            if (bSport == BSportType.BIKE) {
                sport = Sport.CYCLING;
                if (indoorTrainerSession) {
                    subSport = SubSport.INDOOR_CYCLING;
                }
            } else if (bSport == BSportType.RUN) {
                if (tcxLower.contains("walk") || tcxLower.contains("hike")) {
                    sport = Sport.WALKING;
                } else {
                    sport = Sport.RUNNING;
                    if (indoorTrainerSession) {
                        subSport = SubSport.TREADMILL;
                    }
                }
            } else if (tcxLower.contains("swim")) {
                sport = Sport.SWIMMING;
            } else if (indoorTrainerSession) {
                sport = Sport.FITNESS_EQUIPMENT;
            }

            // 2. Iterate samples and write RecordMesg entries
            WorkoutSamplesDatabaseManager samplesDbManager = WorkoutSamplesDatabaseManager.getInstance(mContext);
            SQLiteDatabase samplesDb = samplesDbManager.getDatabase();
            String tableName = WorkoutSamplesDatabaseManager.getTableName(exportInfo.getFileBaseName());

            long sampleCount = 0;
            try (Cursor cursor = samplesDb.query(tableName, null, null, null, null, null, null)) {
                while (cursor.moveToNext()) {
                    RecordMesg record = new RecordMesg();

                    Date sampleDate;
                    if (dataValid(cursor, WorkoutSamplesDbHelper.TIME)) {
                        String timeStr = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSamplesDbHelper.TIME));
                        try {
                            sampleDate = mDateFormat.parse(timeStr);
                        } catch (Exception e) {
                            sampleDate = new Date(startDate.getTime() + sampleCount * 1000L);
                        }
                    } else {
                        sampleDate = new Date(startDate.getTime() + sampleCount * 1000L);
                    }
                    record.setTimestamp(new DateTime(sampleDate));

                    // Coordinates (only if not indoor session and coordinates are valid finite numbers)
                    if (!indoorTrainerSession && haveGeo
                            && dataValid(cursor, SensorType.LATITUDE.name())
                            && dataValid(cursor, SensorType.LONGITUDE.name())) {
                        double lat = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.LATITUDE.name()));
                        double lng = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.LONGITUDE.name()));
                        if (!Double.isNaN(lat) && !Double.isNaN(lng)
                                && lat >= -90.0 && lat <= 90.0 && lng >= -180.0 && lng <= 180.0
                                && (lat != 0.0 || lng != 0.0)) {
                            int latSemicircles = (int) Math.round(lat * SEMICIRCLES_FACTOR);
                            int lngSemicircles = (int) Math.round(lng * SEMICIRCLES_FACTOR);
                            record.setPositionLat(latSemicircles);
                            record.setPositionLong(lngSemicircles);
                        }
                    }

                    // Altitude
                    if (haveAltitude && dataValid(cursor, SensorType.ALTITUDE.name())) {
                        double alt = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.ALTITUDE.name()));
                        if (!Double.isNaN(alt) && alt >= -500.0 && alt <= 9000.0) {
                            record.setAltitude((float) alt);
                        }
                    }

                    // Distance
                    if (haveDistance && dataValid(cursor, SensorType.DISTANCE_m.name())) {
                        double dist = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.DISTANCE_m.name()));
                        if (!Double.isNaN(dist) && dist >= 0.0) {
                            record.setDistance((float) dist);
                        }
                    }

                    // Speed
                    if (haveSpeed && dataValid(cursor, SensorType.SPEED_mps.name())) {
                        double spd = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.SPEED_mps.name()));
                        if (!Double.isNaN(spd) && spd >= 0.0) {
                            record.setSpeed((float) spd);
                        }
                    }

                    // Heart Rate
                    if (haveHR && dataValid(cursor, SensorType.HR.name())) {
                        int hr = cursor.getInt(cursor.getColumnIndexOrThrow(SensorType.HR.name()));
                        if (hr > 0 && hr < 300) {
                            record.setHeartRate((short) hr);
                        }
                    }

                    // Cadence
                    if (haveCadence && dataValid(cursor, SensorType.CADENCE.name())) {
                        double cad = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.CADENCE.name()));
                        if (!Double.isNaN(cad) && cad >= 0.0) {
                            record.setCadence((short) Math.round(cad));
                        }
                    }

                    // Power
                    if (havePower && dataValid(cursor, SensorType.POWER.name())) {
                        double pwr = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.POWER.name()));
                        if (!Double.isNaN(pwr) && pwr >= 0.0) {
                            record.setPower((int) Math.round(pwr));
                        }
                    }

                    // Temperature
                    if (dataValid(cursor, SensorType.TEMPERATURE.name())) {
                        double temp = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.TEMPERATURE.name()));
                        if (!Double.isNaN(temp)) {
                            record.setTemperature((byte) Math.round(temp));
                        }
                    }

                    encoder.write(record);
                    sampleCount++;
                }
            } catch (Exception e) {
                Log.w(TAG, "Error querying samples for FIT export: " + e.getMessage());
            }

            // 3. LapMesg entries
            List<LapData> laps = LapsDatabaseManager.getInstance(mContext).getLaps(workoutID);
            if (laps != null && !laps.isEmpty()) {
                for (LapData lap : laps) {
                    LapMesg lapMesg = new LapMesg();
                    Date lapStart = startDate;
                    if (lap.getTimeStart() != null) {
                        try {
                            lapStart = mDateFormat.parse(lap.getTimeStart());
                        } catch (Exception ignored) {}
                    }
                    lapMesg.setStartTime(new DateTime(lapStart));
                    long lapEndMs = lapStart.getTime() + (long) (lap.getTimeTotalS() * 1000L);
                    lapMesg.setTimestamp(new DateTime(new Date(lapEndMs)));
                    lapMesg.setTotalElapsedTime((float) lap.getTimeTotalS());
                    lapMesg.setTotalTimerTime((float) lap.getTimeTotalS());
                    lapMesg.setTotalDistance((float) lap.getDistanceTotalM());
                    if (lap.getSpeedAverageMps() > 0.0) {
                        lapMesg.setAvgSpeed((float) lap.getSpeedAverageMps());
                    }
                    lapMesg.setIntensity(Intensity.ACTIVE);
                    lapMesg.setSport(sport);
                    lapMesg.setSubSport(subSport);
                    encoder.write(lapMesg);
                }
            }

            // 4. SessionMesg
            SessionMesg sessionMesg = new SessionMesg();
            sessionMesg.setStartTime(new DateTime(startDate));
            sessionMesg.setTimestamp(new DateTime(endDate));
            sessionMesg.setTotalElapsedTime((float) totalTimeSec);
            sessionMesg.setTotalTimerTime((float) totalTimeSec);
            double distTotal = 0.0;
            try {
                distTotal = Double.parseDouble(totalDistance);
            } catch (Exception ignored) {}
            sessionMesg.setTotalDistance((float) distTotal);
            sessionMesg.setSport(sport);
            sessionMesg.setSubSport(subSport);
            sessionMesg.setNumLaps(laps != null ? laps.size() : 1);

            // Populate summary metrics from WorkoutSummaries table
            WorkoutSummariesDatabaseManager summaryDbManager = WorkoutSummariesDatabaseManager.getInstance(mContext);
            SQLiteDatabase summaryDb = summaryDbManager.getDatabase();
            try (Cursor sumCursor = summaryDb.query(WorkoutSummaries.TABLE,
                    new String[]{WorkoutSummaries.CALORIES, WorkoutSummaries.ASCENDING, WorkoutSummaries.DESCENDING, WorkoutSummaries.SPEED_AVERAGE_mps},
                    WorkoutSummaries.FILE_BASE_NAME + "=?",
                    new String[]{exportInfo.getFileBaseName()},
                    null, null, null)) {
                if (sumCursor.moveToFirst()) {
                    int calIdx = sumCursor.getColumnIndex(WorkoutSummaries.CALORIES);
                    if (calIdx != -1 && !sumCursor.isNull(calIdx)) {
                        sessionMesg.setTotalCalories(sumCursor.getInt(calIdx));
                    }
                    int ascIdx = sumCursor.getColumnIndex(WorkoutSummaries.ASCENDING);
                    if (ascIdx != -1 && !sumCursor.isNull(ascIdx)) {
                        sessionMesg.setTotalAscent(sumCursor.getInt(ascIdx));
                    }
                    int descIdx = summaryCursorIndex(sumCursor, WorkoutSummaries.DESCENDING);
                    if (descIdx != -1 && !sumCursor.isNull(descIdx)) {
                        sessionMesg.setTotalDescent(sumCursor.getInt(descIdx));
                    }
                    int speedAvgIdx = sumCursor.getColumnIndex(WorkoutSummaries.SPEED_AVERAGE_mps);
                    if (speedAvgIdx != -1 && !sumCursor.isNull(speedAvgIdx)) {
                        sessionMesg.setAvgSpeed((float) sumCursor.getDouble(speedAvgIdx));
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error querying workout summary: " + e.getMessage());
            }

            // Populate extrema from TABLE_EXTREMA_VALUES
            try (Cursor extCursor = summaryDb.query(WorkoutSummaries.TABLE_EXTREMA_VALUES,
                    new String[]{WorkoutSummaries.SENSOR_TYPE, WorkoutSummaries.EXTREMA_TYPE, WorkoutSummaries.VALUE},
                    WorkoutSummaries.WORKOUT_ID + "=?",
                    new String[]{String.valueOf(workoutID)},
                    null, null, null)) {
                int sensorIdx = extCursor.getColumnIndexOrThrow(WorkoutSummaries.SENSOR_TYPE);
                int typeIdx = extCursor.getColumnIndexOrThrow(WorkoutSummaries.EXTREMA_TYPE);
                int valIdx = extCursor.getColumnIndexOrThrow(WorkoutSummaries.VALUE);
                while (extCursor.moveToNext()) {
                    String sType = extCursor.getString(sensorIdx);
                    String eType = extCursor.getString(typeIdx);
                    double val = extCursor.getDouble(valIdx);
                    if (SensorType.HR.name().equals(sType)) {
                        if ("MAX".equalsIgnoreCase(eType)) sessionMesg.setMaxHeartRate((short) Math.round(val));
                        else if ("MEAN".equalsIgnoreCase(eType) || "AVG".equalsIgnoreCase(eType)) sessionMesg.setAvgHeartRate((short) Math.round(val));
                    } else if (SensorType.POWER.name().equals(sType)) {
                        if ("MAX".equalsIgnoreCase(eType)) sessionMesg.setMaxPower((int) Math.round(val));
                        else if ("MEAN".equalsIgnoreCase(eType) || "AVG".equalsIgnoreCase(eType)) sessionMesg.setAvgPower((int) Math.round(val));
                    } else if (SensorType.CADENCE.name().equals(sType)) {
                        if ("MAX".equalsIgnoreCase(eType)) sessionMesg.setMaxCadence((short) Math.round(val));
                        else if ("MEAN".equalsIgnoreCase(eType) || "AVG".equalsIgnoreCase(eType)) sessionMesg.setAvgCadence((short) Math.round(val));
                    } else if (SensorType.SPEED_mps.name().equals(sType)) {
                        if ("MAX".equalsIgnoreCase(eType)) sessionMesg.setMaxSpeed((float) val);
                        else if ("MEAN".equalsIgnoreCase(eType) || "AVG".equalsIgnoreCase(eType)) sessionMesg.setAvgSpeed((float) val);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error querying workout extrema: " + e.getMessage());
            }

            encoder.write(sessionMesg);

            // 5. ActivityMesg
            ActivityMesg activityMesg = new ActivityMesg();
            activityMesg.setTimestamp(new DateTime(endDate));
            activityMesg.setTotalTimerTime((float) totalTimeSec);
            activityMesg.setNumSessions(1);
            activityMesg.setType(Activity.MANUAL);
            encoder.write(activityMesg);

        } finally {
            encoder.close();
        }

        return new ExportResult(true, false, null);
    }

    private int summaryCursorIndex(@NonNull Cursor cursor, @NonNull String columnName) {
        return cursor.getColumnIndex(columnName);
    }
}
