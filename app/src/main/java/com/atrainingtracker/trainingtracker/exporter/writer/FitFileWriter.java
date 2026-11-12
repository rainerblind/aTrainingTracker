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
import androidx.annotation.Nullable;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.database.ExtremaType;
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrInterval;
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrRepository;
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository;
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData;
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutExportHeader;
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository;
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSampleRecord;
import com.atrainingtracker.trainingtracker.exporter.ExportInfo;
import com.garmin.fit.Activity;
import com.garmin.fit.ActivityMesg;
import com.garmin.fit.DateTime;
import com.garmin.fit.DeveloperDataIdMesg;
import com.garmin.fit.DeveloperField;
import com.garmin.fit.Event;
import com.garmin.fit.EventMesg;
import com.garmin.fit.EventType;
import com.garmin.fit.FieldDescriptionMesg;
import com.garmin.fit.FileEncoder;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.Fit;
import com.garmin.fit.HrvMesg;
import com.garmin.fit.Intensity;
import com.garmin.fit.LapMesg;
import com.garmin.fit.Manufacturer;
import com.garmin.fit.RadarThreatLevelType;
import com.garmin.fit.RecordMesg;
import com.garmin.fit.SessionMesg;
import com.garmin.fit.Sport;
import com.garmin.fit.SubSport;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CancellationException;

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
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US);

    public FitFileWriter(@NonNull Context context) {
        super(context.getApplicationContext() != null ? context.getApplicationContext() : context);
    }

    @Nullable
    private static Date parseUtcTimestamp(@Nullable String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return null;
        }
        LocalDateTime ldt = LocalDateTime.parse(timeStr.trim(), DATE_TIME_FORMATTER);
        return new Date(ldt.toInstant(ZoneOffset.UTC).toEpochMilli());
    }

    @NonNull
    @Override
    protected ExportResult doExport(@NonNull ExportInfo exportInfo) throws IOException, ParseException {
        if (DEBUG) Log.d(TAG, "doExport: " + exportInfo.getFileBaseName());

        getHeaderData(exportInfo);

        // Pre-flight header validation: fail-fast on invalid ground-truth parameters
        if (startTime == null || startTime.trim().isEmpty()) {
            return new ExportResult(false, false, "FIT export failed: missing or empty startTime");
        }
        Date startDate;
        try {
            startDate = parseUtcTimestamp(startTime);
            if (startDate == null) {
                return new ExportResult(false, false, "FIT export failed: empty startTime");
            }
        } catch (DateTimeParseException e) {
            return new ExportResult(false, false, "FIT export failed: unparseable startTime: " + startTime);
        }

        double totalTimeSec = 0.0;
        try {
            if (totalTime != null && !totalTime.trim().isEmpty()) {
                totalTimeSec = Double.parseDouble(totalTime);
            }
        } catch (NumberFormatException e) {
            return new ExportResult(false, false, "FIT export failed: invalid totalTime: " + totalTime);
        }
        if (totalTimeSec < 0.0) {
            return new ExportResult(false, false, "FIT export failed: negative totalTime: " + totalTimeSec);
        }

        double distTotal = 0.0;
        try {
            if (totalDistance != null && !totalDistance.trim().isEmpty()) {
                distTotal = Double.parseDouble(totalDistance);
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "Failed to parse totalDistance: " + totalDistance + ", falling back to 0.0", e);
        }
        if (distTotal < 0.0) distTotal = 0.0;

        File outputFile = new File(getBaseDirFile(mContext), exportInfo.getShortPath());
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory: " + parentDir.getAbsolutePath());
            }
        }

        FileEncoder encoder = null;
        boolean success = false;
        String errorMessage = null;

        try {
            encoder = new FileEncoder(outputFile, Fit.ProtocolVersion.V2_0);

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

            BSportType bSport = SportTypesRepository.getInstance(mContext).getBSportType(sportTypeId);
            String tcxName = SportTypesRepository.getInstance(mContext).getTcxName(sportTypeId);
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
            long workoutId = this.workoutID;
            if (workoutId <= 0) {
                workoutId = WorkoutRepository.getInstance(mContext).getWorkoutIdByBaseFileName(exportInfo.getFileBaseName());
            }

            List<WorkoutRrInterval> rrIntervals = null;
            if (workoutId > 0) {
                try {
                    rrIntervals = WorkoutRrRepository.getInstance(mContext).getIntervalsForWorkoutBlocking(workoutId);
                } catch (Exception e) {
                    Log.w(TAG, "Could not query RR intervals for workout " + workoutId + ": " + e.getMessage());
                }
            }
            final int[] rrIntervalIndex = new int[]{0};
            final int totalRr = (rrIntervals != null) ? rrIntervals.size() : 0;
            final List<WorkoutRrInterval> finalRrIntervals = rrIntervals;

            final Date[] firstSampleDate = new Date[]{null};
            final Date[] lastSampleDate = new Date[]{null};
            final long[] sampleCount = new long[]{0};
            final FileEncoder finalEncoder = encoder;

            // Determine if radar metrics are present in this workout (REQ-EXP-042 / ATT-5088)
            boolean haveRadar = workoutId > 0 && WorkoutRepository.getInstance(mContext).hasRadarData(workoutId, exportInfo.getFileBaseName());
            DeveloperDataIdMesg devDataIdMesg = null;
            FieldDescriptionMesg radarCurrentDesc = null;
            FieldDescriptionMesg radarTotalDesc = null;
            FieldDescriptionMesg passingSpeedDesc = null;
            FieldDescriptionMesg passingSpeedAbsDesc = null;
            FieldDescriptionMesg passingDistDesc = null;
            FieldDescriptionMesg largeVehCountDesc = null;

            if (haveRadar) {
                devDataIdMesg = new DeveloperDataIdMesg();
                devDataIdMesg.setDeveloperDataIndex((short) 0);
                devDataIdMesg.setManufacturerId(Manufacturer.DEVELOPMENT);
                devDataIdMesg.setApplicationVersion(1L);
                UUID appIdUuid = UUID.fromString("6a4e3200-667b-11e3-949a-0800200c9a66");
                ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
                bb.putLong(appIdUuid.getMostSignificantBits());
                bb.putLong(appIdUuid.getLeastSignificantBits());
                byte[] appIdBytes = bb.array();
                for (int i = 0; i < 16; i++) {
                    devDataIdMesg.setApplicationId(i, appIdBytes[i]);
                }
                encoder.write(devDataIdMesg);

                radarCurrentDesc = new FieldDescriptionMesg();
                radarCurrentDesc.setDeveloperDataIndex((short) 0);
                radarCurrentDesc.setFieldDefinitionNumber((short) 0);
                radarCurrentDesc.setFitBaseTypeId((short) Fit.BASE_TYPE_UINT8);
                radarCurrentDesc.setFieldName(0, "radar_current");
                radarCurrentDesc.setScale((short) 1);
                radarCurrentDesc.setUnits(0, "targets");
                encoder.write(radarCurrentDesc);

                radarTotalDesc = new FieldDescriptionMesg();
                radarTotalDesc.setDeveloperDataIndex((short) 0);
                radarTotalDesc.setFieldDefinitionNumber((short) 1);
                radarTotalDesc.setFitBaseTypeId((short) Fit.BASE_TYPE_UINT16);
                radarTotalDesc.setFieldName(0, "radar_total");
                radarTotalDesc.setScale((short) 1);
                radarTotalDesc.setUnits(0, "vehicles");
                encoder.write(radarTotalDesc);

                passingSpeedDesc = new FieldDescriptionMesg();
                passingSpeedDesc.setDeveloperDataIndex((short) 0);
                passingSpeedDesc.setFieldDefinitionNumber((short) 2);
                passingSpeedDesc.setFitBaseTypeId((short) Fit.BASE_TYPE_UINT16);
                passingSpeedDesc.setFieldName(0, "passing_speed");
                passingSpeedDesc.setScale((short) 100);
                passingSpeedDesc.setUnits(0, "m/s");
                encoder.write(passingSpeedDesc);

                passingSpeedAbsDesc = new FieldDescriptionMesg();
                passingSpeedAbsDesc.setDeveloperDataIndex((short) 0);
                passingSpeedAbsDesc.setFieldDefinitionNumber((short) 3);
                passingSpeedAbsDesc.setFitBaseTypeId((short) Fit.BASE_TYPE_UINT16);
                passingSpeedAbsDesc.setFieldName(0, "passing_speed_abs");
                passingSpeedAbsDesc.setScale((short) 100);
                passingSpeedAbsDesc.setUnits(0, "m/s");
                encoder.write(passingSpeedAbsDesc);

                passingDistDesc = new FieldDescriptionMesg();
                passingDistDesc.setDeveloperDataIndex((short) 0);
                passingDistDesc.setFieldDefinitionNumber((short) 4);
                passingDistDesc.setFitBaseTypeId((short) Fit.BASE_TYPE_UINT16);
                passingDistDesc.setFieldName(0, "passing_distance");
                passingDistDesc.setScale((short) 100);
                passingDistDesc.setUnits(0, "m");
                encoder.write(passingDistDesc);

                largeVehCountDesc = new FieldDescriptionMesg();
                largeVehCountDesc.setDeveloperDataIndex((short) 0);
                largeVehCountDesc.setFieldDefinitionNumber((short) 5);
                largeVehCountDesc.setFitBaseTypeId((short) Fit.BASE_TYPE_UINT16);
                largeVehCountDesc.setFieldName(0, "large_vehicle_count");
                largeVehCountDesc.setScale((short) 1);
                largeVehCountDesc.setUnits(0, "vehicles");
                encoder.write(largeVehCountDesc);
            }

            final boolean finalHaveRadar = haveRadar;
            final DeveloperDataIdMesg finalDevDataIdMesg = devDataIdMesg;
            final FieldDescriptionMesg finalRadarCurrentDesc = radarCurrentDesc;
            final FieldDescriptionMesg finalRadarTotalDesc = radarTotalDesc;
            final FieldDescriptionMesg finalPassingSpeedDesc = passingSpeedDesc;
            final FieldDescriptionMesg finalPassingSpeedAbsDesc = passingSpeedAbsDesc;
            final FieldDescriptionMesg finalPassingDistDesc = passingDistDesc;
            final FieldDescriptionMesg finalLargeVehCountDesc = largeVehCountDesc;
            final int[] prevVehCount = new int[]{0};

            try {
                WorkoutRepository.getInstance(mContext).streamSamples(exportInfo.getFileBaseName(), sample -> {
                    if (isCancelled()) {
                        throw new CancellationException("FIT export cancelled at start of loop iteration");
                    }
                    RecordMesg record = new RecordMesg();

                    Date sampleDate;
                    if (sample.getTime() != null && !sample.getTime().isEmpty()) {
                        try {
                            sampleDate = parseUtcTimestamp(sample.getTime());
                            if (sampleDate == null) {
                                sampleDate = new Date(startDate.getTime() + sampleCount[0] * 1000L);
                            }
                        } catch (Exception e) {
                            sampleDate = new Date(startDate.getTime() + sampleCount[0] * 1000L);
                        }
                    } else {
                        sampleDate = new Date(startDate.getTime() + sampleCount[0] * 1000L);
                    }

                    if (firstSampleDate[0] == null) firstSampleDate[0] = sampleDate;
                    lastSampleDate[0] = sampleDate;

                    record.setTimestamp(new DateTime(sampleDate));

                    // Coordinates (only if not indoor session and coordinates are valid finite numbers)
                    if (!indoorTrainerSession && haveGeo
                            && sample.getLatitude() != null
                            && sample.getLongitude() != null) {
                        double lat = sample.getLatitude();
                        double lng = sample.getLongitude();
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
                    if (haveAltitude && sample.getAltitude() != null) {
                        double alt = sample.getAltitude();
                        if (!Double.isNaN(alt) && alt >= -500.0 && alt <= 9000.0) {
                            record.setAltitude((float) alt);
                        }
                    }

                    // Distance
                    if (haveDistance && sample.getDistanceM() != null) {
                        double dist = sample.getDistanceM();
                        if (!Double.isNaN(dist) && dist >= 0.0) {
                            record.setDistance((float) dist);
                        }
                    }

                    // Speed
                    if (haveSpeed && sample.getSpeedMps() != null) {
                        double spd = sample.getSpeedMps();
                        if (!Double.isNaN(spd) && spd >= 0.0) {
                            record.setSpeed((float) spd);
                        }
                    }

                    // Heart Rate
                    if (haveHR && sample.getHr() != null) {
                        int hr = sample.getHr();
                        if (hr > 0 && hr < 300) {
                            record.setHeartRate((short) hr);
                        }
                    }

                    // Cadence
                    if (haveCadence && sample.getCadence() != null) {
                        double cad = sample.getCadence();
                        if (!Double.isNaN(cad) && cad >= 0.0) {
                            record.setCadence((short) Math.round(cad));
                        }
                    }

                    // Power
                    if (havePower && sample.getPower() != null) {
                        double pwr = sample.getPower();
                        if (!Double.isNaN(pwr) && pwr >= 0.0) {
                            record.setPower((int) Math.round(pwr));
                        }
                    }

                    // Temperature
                    if (sample.getTemperature() != null) {
                        double temp = sample.getTemperature();
                        if (!Double.isNaN(temp)) {
                            record.setTemperature((byte) Math.round(temp));
                        }
                    }

                    // Write pending HrvMesg intervals that occurred up to sampleDate (REQ-PER-034.4)
                    if (finalRrIntervals != null && rrIntervalIndex[0] < totalRr) {
                        long sampleTs = sampleDate.getTime();
                        List<Float> hrvBatch = new ArrayList<>(5);
                        while (rrIntervalIndex[0] < totalRr && finalRrIntervals.get(rrIntervalIndex[0]).getBeatTimestampMs() <= sampleTs) {
                            hrvBatch.add(finalRrIntervals.get(rrIntervalIndex[0]).getRrMs() / 1000.0f);
                            rrIntervalIndex[0]++;
                            if (hrvBatch.size() == 5) {
                                writeHrvMesg(finalEncoder, hrvBatch);
                                hrvBatch.clear();
                            }
                        }
                        if (!hrvBatch.isEmpty()) {
                            writeHrvMesg(finalEncoder, hrvBatch);
                            hrvBatch.clear();
                        }
                    }

                    // Vehicle pass event emission (REQ-EXP-042 / ATT-5088)
                    if (finalHaveRadar && sample.getRadarVehicleCount() != null) {
                        int currentVehCount = sample.getRadarVehicleCount();
                        if (currentVehCount > prevVehCount[0]) {
                            prevVehCount[0] = currentVehCount;
                            EventMesg eventMesg = new EventMesg();
                            eventMesg.setTimestamp(new DateTime(sampleDate));
                            eventMesg.setEvent(Event.RADAR_THREAT_ALERT);
                            eventMesg.setEventType(EventType.MARKER);
                            if (sample.getRadarThreatCount() != null) {
                                eventMesg.setRadarThreatCount((short) sample.getRadarThreatCount().intValue());
                            }
                            double appSpdKph = (sample.getRadarApproachSpeedMps() != null ? sample.getRadarApproachSpeedMps() : 0.0) * 3.6;
                            boolean isHighThreat = (sample.getRadarThreatCount() != null && sample.getRadarThreatCount() >= 2) || appSpdKph >= 25.0;
                            eventMesg.setRadarThreatLevelMax(isHighThreat ? RadarThreatLevelType.THREAT_APPROACHING_FAST : RadarThreatLevelType.THREAT_APPROACHING);
                            if (sample.getRadarApproachSpeedMps() != null) {
                                float spd = (float) Math.min(Math.max(sample.getRadarApproachSpeedMps(), 0.0), 25.4);
                                eventMesg.setRadarThreatMaxApproachSpeed(spd);
                                eventMesg.setRadarThreatAvgApproachSpeed(spd);
                            }
                            finalEncoder.write(eventMesg);
                        }
                    }

                    // Attach FIT Protocol V2.0 Developer Fields on RecordMesg (REQ-EXP-042 / ATT-5088)
                    if (finalHaveRadar) {
                        if (sample.getRadarThreatCount() != null && finalRadarCurrentDesc != null) {
                            DeveloperField f = new DeveloperField(finalRadarCurrentDesc, finalDevDataIdMesg);
                            f.setValue((short) sample.getRadarThreatCount().intValue());
                            record.addDeveloperField(f);
                        }
                        if (sample.getRadarVehicleCount() != null && finalRadarTotalDesc != null) {
                            DeveloperField f = new DeveloperField(finalRadarTotalDesc, finalDevDataIdMesg);
                            f.setValue(sample.getRadarVehicleCount());
                            record.addDeveloperField(f);
                        }
                        if (sample.getRadarApproachSpeedMps() != null) {
                            if (finalPassingSpeedDesc != null) {
                                DeveloperField f = new DeveloperField(finalPassingSpeedDesc, finalDevDataIdMesg);
                                f.setValue(sample.getRadarApproachSpeedMps());
                                record.addDeveloperField(f);
                            }
                            if (finalPassingSpeedAbsDesc != null && sample.getSpeedMps() != null) {
                                DeveloperField f = new DeveloperField(finalPassingSpeedAbsDesc, finalDevDataIdMesg);
                                f.setValue(sample.getSpeedMps() + sample.getRadarApproachSpeedMps());
                                record.addDeveloperField(f);
                            }
                        }
                        if (sample.getRadarPassingDistanceM() != null && finalPassingDistDesc != null) {
                            DeveloperField f = new DeveloperField(finalPassingDistDesc, finalDevDataIdMesg);
                            f.setValue(sample.getRadarPassingDistanceM());
                            record.addDeveloperField(f);
                        }
                        if (sample.getRadarLargeVehicleCount() != null && finalLargeVehCountDesc != null) {
                            DeveloperField f = new DeveloperField(finalLargeVehCountDesc, finalDevDataIdMesg);
                            f.setValue(sample.getRadarLargeVehicleCount());
                            record.addDeveloperField(f);
                        }
                    }

                    finalEncoder.write(record);
                    if (isCancelled()) {
                        throw new CancellationException("FIT export cancelled post-I/O");
                    }
                    sampleCount[0]++;
                });
            } catch (CancellationException e) {
                throw e;
            } catch (Exception e) {
                Log.e(TAG, "Error querying samples for FIT export: " + e.getMessage(), e);
                throw new IOException("FIT export failed during sample encoding: " + e.getMessage(), e);
            }

            // Write remaining trailing HrvMesg intervals (REQ-PER-034.4)
            if (finalRrIntervals != null && rrIntervalIndex[0] < totalRr) {
                List<Float> hrvBatch = new ArrayList<>(5);
                while (rrIntervalIndex[0] < totalRr) {
                    hrvBatch.add(finalRrIntervals.get(rrIntervalIndex[0]).getRrMs() / 1000.0f);
                    rrIntervalIndex[0]++;
                    if (hrvBatch.size() == 5) {
                        writeHrvMesg(encoder, hrvBatch);
                        hrvBatch.clear();
                    }
                }
                if (!hrvBatch.isEmpty()) {
                    writeHrvMesg(encoder, hrvBatch);
                }
            }

            // Calculate timing metrics supporting pauses
            double elapsedSampleSec = 0.0;
            if (sampleCount[0] > 0 && firstSampleDate[0] != null && lastSampleDate[0] != null) {
                long elapsedMs = lastSampleDate[0].getTime() - firstSampleDate[0].getTime();
                elapsedSampleSec = elapsedMs / 1000.0;
            }
            double totalTimerSec = totalTimeSec > 0.0 ? totalTimeSec : elapsedSampleSec;
            double totalElapsedTimeSec = Math.max(totalTimerSec, elapsedSampleSec);
            Date sessionEndDate = (lastSampleDate[0] != null)
                    ? lastSampleDate[0]
                    : new Date(startDate.getTime() + (long) (totalElapsedTimeSec * 1000.0));

            // 3. LapMesg entries
            // Query laps from Laps.db (strictly read-only access)
            List<LapData> laps = WorkoutRepository.getInstance(mContext).getLaps(workoutID);
            if (laps != null && !laps.isEmpty()) {
                for (LapData lap : laps) {
                    LapMesg lapMesg = new LapMesg();
                    Date lapStart = startDate;
                    if (lap.getTimeStart() != null) {
                        try {
                            Date parsed = parseUtcTimestamp(lap.getTimeStart());
                            if (parsed != null) lapStart = parsed;
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to parse lap start time: " + lap.getTimeStart() + ", falling back to workout startDate", e);
                        }
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
            } else {
                // Transient representation for FIT protocol compliance; zero mutations to Laps.db (Laps.db is purely read-only)
                LapMesg lapMesg = new LapMesg();
                lapMesg.setStartTime(new DateTime(startDate));
                lapMesg.setTimestamp(new DateTime(sessionEndDate));
                lapMesg.setTotalElapsedTime((float) totalElapsedTimeSec);
                lapMesg.setTotalTimerTime((float) totalTimerSec);
                lapMesg.setTotalDistance((float) distTotal);
                if (totalTimerSec > 0.0 && distTotal > 0.0) {
                    lapMesg.setAvgSpeed((float) (distTotal / totalTimerSec));
                }
                lapMesg.setIntensity(Intensity.ACTIVE);
                lapMesg.setSport(sport);
                lapMesg.setSubSport(subSport);
                encoder.write(lapMesg);
            }

            // 4. SessionMesg
            SessionMesg sessionMesg = new SessionMesg();
            sessionMesg.setStartTime(new DateTime(startDate));
            sessionMesg.setTimestamp(new DateTime(sessionEndDate));
            sessionMesg.setTotalElapsedTime((float) totalElapsedTimeSec);
            sessionMesg.setTotalTimerTime((float) totalTimerSec);
            sessionMesg.setTotalDistance((float) distTotal);
            sessionMesg.setSport(sport);
            sessionMesg.setSubSport(subSport);
            sessionMesg.setNumLaps(laps != null && !laps.isEmpty() ? laps.size() : 1);

            // Populate summary metrics from WorkoutExportHeader (REQ-DAT-042.3)
            if (exportHeader != null) {
                if (exportHeader.getCalories() != null) {
                    sessionMesg.setTotalCalories(exportHeader.getCalories());
                }
                if (exportHeader.getTotalAscent() != null) {
                    sessionMesg.setTotalAscent(exportHeader.getTotalAscent());
                }
                if (exportHeader.getTotalDescent() != null) {
                    sessionMesg.setTotalDescent(exportHeader.getTotalDescent());
                }
                if (exportHeader.getAvgSpeedMps() != null) {
                    sessionMesg.setAvgSpeed(exportHeader.getAvgSpeedMps().floatValue());
                }
                if (exportHeader.getRpe() != null) {
                    sessionMesg.setWorkoutRpe((short) (exportHeader.getRpe() * 10));
                }
                if (exportHeader.getFeel() != null) {
                    sessionMesg.setWorkoutFeel((short) ((exportHeader.getFeel() - 1) * 25));
                }
            }

            // Populate extrema from WorkoutRepository (REQ-DAT-042.1)
            WorkoutRepository repo = WorkoutRepository.getInstance(mContext);
            Double maxHr = repo.getExtremaValue(workoutId, SensorType.HR, ExtremaType.MAX);
            if (maxHr != null) sessionMesg.setMaxHeartRate((short) Math.round(maxHr));
            Double avgHr = repo.getExtremaValue(workoutId, SensorType.HR, ExtremaType.AVG);
            if (avgHr != null) sessionMesg.setAvgHeartRate((short) Math.round(avgHr));

            Double maxPower = repo.getExtremaValue(workoutId, SensorType.POWER, ExtremaType.MAX);
            if (maxPower != null) sessionMesg.setMaxPower((int) Math.round(maxPower));
            Double avgPower = repo.getExtremaValue(workoutId, SensorType.POWER, ExtremaType.AVG);
            if (avgPower != null) sessionMesg.setAvgPower((int) Math.round(avgPower));

            Double maxCad = repo.getExtremaValue(workoutId, SensorType.CADENCE, ExtremaType.MAX);
            if (maxCad != null) sessionMesg.setMaxCadence((short) Math.round(maxCad));
            Double avgCad = repo.getExtremaValue(workoutId, SensorType.CADENCE, ExtremaType.AVG);
            if (avgCad != null) sessionMesg.setAvgCadence((short) Math.round(avgCad));

            Double maxSpeed = repo.getExtremaValue(workoutId, SensorType.SPEED_mps, ExtremaType.MAX);
            if (maxSpeed != null) sessionMesg.setMaxSpeed(maxSpeed.floatValue());
            Double avgSpeed = repo.getExtremaValue(workoutId, SensorType.SPEED_mps, ExtremaType.AVG);
            if (avgSpeed != null) sessionMesg.setAvgSpeed(avgSpeed.floatValue());

            encoder.write(sessionMesg);

            // 5. ActivityMesg
            ActivityMesg activityMesg = new ActivityMesg();
            activityMesg.setTimestamp(new DateTime(sessionEndDate));
            activityMesg.setTotalTimerTime((float) totalTimerSec);
            activityMesg.setNumSessions(1);
            activityMesg.setType(Activity.MANUAL);
            encoder.write(activityMesg);

            success = true;
        } catch (CancellationException e) {
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Error during FIT export: " + e.getMessage(), e);
            errorMessage = e.getMessage() != null ? e.getMessage() : "FIT export failed: " + e.getClass().getSimpleName();
            success = false;
        } finally {
            if (encoder != null) {
                try {
                    encoder.close();
                } catch (Exception e) {
                    Log.w(TAG, "Error closing FileEncoder: " + e.getMessage(), e);
                }
            }
            if (!success && outputFile.exists()) {
                try {
                    boolean deleted = outputFile.delete();
                    if (!deleted) {
                        Log.w(TAG, "Cleanup failure: failed to delete partial FIT file: " + outputFile.getAbsolutePath());
                        errorMessage = (errorMessage != null ? errorMessage : "FIT export failed")
                                + " (Cleanup failure: could not delete partial file)";
                    } else {
                        Log.d(TAG, "Partial FIT file cleaned up: " + outputFile.getAbsolutePath());
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Exception during partial file deletion: " + e.getMessage(), e);
                }
            }
        }

        if (!success) {
            return new ExportResult(false, false, errorMessage != null ? errorMessage : "FIT export failed");
        }
        return new ExportResult(true, false, "FIT export successful: " + outputFile.getAbsolutePath());
    }

    private void writeHrvMesg(@NonNull FileEncoder encoder, @NonNull List<Float> intervalsSec) {
        if (intervalsSec.isEmpty()) return;
        HrvMesg hrvMesg = new HrvMesg();
        for (int i = 0; i < intervalsSec.size(); i++) {
            hrvMesg.setTime(i, intervalsSec.get(i));
        }
        encoder.write(hrvMesg);
    }
}
