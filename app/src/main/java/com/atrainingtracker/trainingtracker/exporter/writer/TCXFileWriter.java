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
import android.util.Log;

import androidx.annotation.NonNull;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.helpers.HavePressureSensor;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.exporter.ExportInfo;
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository;
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository;
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSampleRecord;
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
            List<LapData> lapsList = WorkoutRepository.getInstance(mContext).getLaps(workoutID);
            Map<Long, LapData> lapDataMap = new HashMap<>();
            for (LapData lapData : lapsList) {
                lapDataMap.put(lapData.getLapNr(), lapData);
            }

            List<TcxLap> tcxLaps = new ArrayList<>();
            final List<TcxTrackpoint>[] currentTrackpointsHolder = new List[]{new ArrayList<>()};
            final String[] currentLapStartTime = new String[]{null};
            final String[] totalTime = new String[]{"0"};
            final String[] totalDistance = new String[]{"0"};
            final long[] prevLineLap = new long[]{BANALService.INIT_LAP_NR - 1};
            final double[] latitude = new double[]{0.0};
            final double[] longitude = new double[]{0.0};

            WorkoutRepository.getInstance(mContext).streamSamples(exportInfo.getFileBaseName(), sample -> {
                if (isCancelled()) {
                    throw new CancellationException("TCX export cancelled at start of loop iteration");
                }

                int lap = sample.getLapNr();
                if (prevLineLap[0] != lap) { // new lap
                    if (prevLineLap[0] != BANALService.INIT_LAP_NR - 1) { // finish previous lap
                        tcxLaps.add(buildLap(prevLineLap[0], currentLapStartTime[0], totalTime[0], totalDistance[0], currentTrackpointsHolder[0], lapDataMap));
                        currentTrackpointsHolder[0] = new ArrayList<>();
                    }

                    // get the lap data
                    LapData lapData = lapDataMap.get((long) lap);
                    if (lapData != null) {
                        totalTime[0] = String.valueOf(lapData.getTimeTotalS());
                        totalDistance[0] = String.valueOf(lapData.getDistanceTotalM());
                    } else {
                        LapData fetchedLap = WorkoutRepository.getInstance(mContext).getLap(workoutID, (long) lap);
                        if (fetchedLap != null) {
                            totalTime[0] = String.valueOf(fetchedLap.getTimeTotalS());
                            totalDistance[0] = String.valueOf(fetchedLap.getDistanceTotalM());
                        } else {
                            totalTime[0] = "0";
                            totalDistance[0] = "0";
                        }
                    }

                    if (sample.getTime() != null) {
                        try {
                            currentLapStartTime[0] = dbTime2XMLTime(sample.getTime());
                        } catch (ParseException e) {
                            currentLapStartTime[0] = "";
                        }
                    }
                }
                prevLineLap[0] = lap;

                String tpTime = "";
                if (sample.getTime() != null) {
                    try {
                        tpTime = dbTime2XMLTime(sample.getTime());
                    } catch (ParseException e) {
                        tpTime = "";
                    }
                }

                TcxPosition position = null;
                // we do not write location data when it was a (indoor) trainer session
                if (!indoorTrainerSession && haveGeo && sample.getLatitude() != null && sample.getLongitude() != null) {
                    double latitudeOld = latitude[0];
                    double longitudeOld = longitude[0];
                    latitude[0] = sample.getLatitude();
                    longitude[0] = sample.getLongitude();

                    if (!(WRITE_ONLY_ON_NEW_GEO_DATA && latitude[0] == latitudeOld && longitude[0] == longitudeOld)) {
                        position = new TcxPosition(latitude[0], longitude[0]);
                    }
                }

                Double altitude = null;
                if (haveAltitude && sample.getAltitude() != null) {
                    double alt = sample.getAltitude();
                    if (alt >= -500.0 && alt <= 9000.0) {
                        altitude = alt;
                    }
                }

                Double distance = null;
                if (haveDistance && sample.getDistanceM() != null) {
                    distance = sample.getDistanceM();
                }

                Integer hrVal = null;
                if (haveHR && sample.getHr() != null) {
                    hrVal = sample.getHr();
                }

                Integer cadenceVal = null;
                if (haveBikeCadence && sample.getCadence() != null) {
                    cadenceVal = (int) Math.round(sample.getCadence());
                }

                Double speedVal = null;
                Integer powerVal = null;
                Integer runCadenceVal = null;

                if (haveSpeed && sample.getSpeedMps() != null) {
                    speedVal = sample.getSpeedMps();
                }

                if (havePower && sample.getPower() != null) {
                    powerVal = (int) Math.round(sample.getPower());
                }

                if (haveRunCadence && sample.getCadence() != null) {
                    runCadenceVal = (int) Math.round(sample.getCadence());
                }

                TcxTrackpointExtensions tpExt = (speedVal != null || powerVal != null || runCadenceVal != null)
                        ? new TcxTrackpointExtensions(speedVal, powerVal, runCadenceVal)
                        : null;

                currentTrackpointsHolder[0].add(new TcxTrackpoint(
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
            });

            if (prevLineLap[0] != BANALService.INIT_LAP_NR - 1) {
                tcxLaps.add(buildLap(prevLineLap[0], currentLapStartTime[0], totalTime[0], totalDistance[0], currentTrackpointsHolder[0], lapDataMap));
            }

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
                    SportTypesRepository.getInstance(mContext).getTcxName(sportTypeId),
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
