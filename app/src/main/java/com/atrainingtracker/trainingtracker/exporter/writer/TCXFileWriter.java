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

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;


public class TCXFileWriter extends BaseFileWriter {
    protected static final boolean WRITE_ONLY_ON_NEW_GEO_DATA = true;
    private static final String TAG = "TCXFileExporter";
    private static final boolean DEBUG = false;
    // DateFormats to convert from Db style dates to XML style dates
    protected static final SimpleDateFormat msdfFromDb = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    protected static final SimpleDateFormat msdfToXML = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);

    public TCXFileWriter(@NonNull Context context) {
        super(context);
    }

    // convert from "2012-03-29 16:23:05" to "2012-03-29T16:23:05Z"
    @NonNull
    protected static String dbTime2XMLTime(@NonNull String dbTime) throws ParseException {
        return msdfToXML.format(msdfFromDb.parse(dbTime)) + "Z";
    }

    @NonNull
    @Override
    protected ExportResult doExport(@NonNull ExportInfo exportInfo)
            throws IOException, ParseException {
        if (DEBUG) Log.d(TAG, "exportToFile");

        getHeaderData(exportInfo);

        BufferedWriter bufferedWriter = getBufferedWriter(exportInfo.getShortPath());

        // write the header data to the file
        bufferedWriter.write("<?xml version=\"1.0\"?>\n");
        bufferedWriter.write("<TrainingCenterDatabase xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.garmin.com/xmlschemas/ActivityExtension/v2 http://www.garmin.com/xmlschemas/ActivityExtensionv2.xsd http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2 http://www.garmin.com/xmlschemas/TrainingCenterDatabasev2.xsd\">\n");
        bufferedWriter.write("  <Activities>\n");
        bufferedWriter.write("    <Activity Sport=\"" + SportTypeDatabaseManager.getTcxName(sportTypeId) + "\">\n");
        bufferedWriter.write("      <Id>" + dbTime2XMLTime(startTime) + "</Id>\n");

        WorkoutSamplesDatabaseManager databaseManager = WorkoutSamplesDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = db.query(WorkoutSamplesDatabaseManager.getTableName(exportInfo.getFileBaseName()),
                null,
                null,
                null,
                null,
                null,
                null);

        int hr;
        double distance, speed, power, cadence, altitude;
        double latitude = 0.0;
        double longitude = 0.0;
        double latitudeOld;
        double longitudeOld;

        long prevLineLap = BANALService.INIT_LAP_NR - 1;

        int lines = cursor.getCount();

        int count = 0;
        while (cursor.moveToNext()) {

            // TODO: avoid writing laps with zero distance and time and ...

            int lap = cursor.getInt(cursor.getColumnIndexOrThrow(SensorType.LAP_NR.name()));
            if (prevLineLap != lap) { // new lap

                if (lap != BANALService.INIT_LAP_NR) { // finish previous lap
                    bufferedWriter.write("        </Track>\n");
                    bufferedWriter.write("      </Lap>\n");
                }

                // get the lap data
                SQLiteDatabase lapDb = LapsDatabaseManager.getInstance(mContext).getDatabase();

                Cursor lapCursor = lapDb.query(LapsDatabaseManager.Laps.TABLE,
                        null,
                        LapsDatabaseManager.Laps.WORKOUT_ID + "=? AND " + LapsDatabaseManager.Laps.LAP_NR + "=?",
                        new String[]{workoutID + "", lap + ""},
                        null,
                        null,
                        null);
                if (DEBUG)
                    Log.d(TAG, "getting lap data: workoutID: " + workoutID + ", lapNr: " + lap + " found: "
                            + lapCursor.getCount() + ", " + lapCursor.getColumnCount());

                lapCursor.moveToFirst();

                // get the data for the lap
                totalTime = myGet(lapCursor, LapsDatabaseManager.Laps.TIME_TOTAL_s, "0");
                totalDistance = myGet(lapCursor, LapsDatabaseManager.Laps.DISTANCE_TOTAL_m, "0");

                lapCursor.close();

                // write the lap data
                bufferedWriter.write("      <Lap StartTime=\"" + dbTime2XMLTime(cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSamplesDbHelper.TIME))) + "\">\n");
                bufferedWriter.write("        <TotalTimeSeconds>" + totalTime + "</TotalTimeSeconds>\n");
                bufferedWriter.write("        <DistanceMeters>" + totalDistance + "</DistanceMeters>\n");
                // bufferedWriter.write("        <Calories>" + 0 + "</Calories>\n");    // TODO
                // bufferedWriter.write("        <Intensity>Active</Intensity>\n"); // TODO: meaning???
                // bufferedWriter.write("        <TriggerMethod>Manual</TriggerMethod>\n"); // TODO: meaning?
                bufferedWriter.write("        <Track>\n");

            }
            prevLineLap = lap;

            bufferedWriter.write("          <Trackpoint>\n");
            bufferedWriter.write("            <Time>"
                    + dbTime2XMLTime(cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSamplesDbHelper.TIME)))
                    + "</Time>\n");

            // we do not write location data when it was a (indoor) trainer session
            if (!indoorTrainerSession && haveGeo && dataValid(cursor, SensorType.LATITUDE.name()) && dataValid(cursor, SensorType.LONGITUDE.name())) {
                latitudeOld = latitude;
                longitudeOld = longitude;
                latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.LATITUDE.name()));
                longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.LONGITUDE.name()));

                if (WRITE_ONLY_ON_NEW_GEO_DATA && latitude == latitudeOld && longitude == longitudeOld) {
                    // do nothing
                } else {
                    bufferedWriter.write("            <Position>\n");
                    bufferedWriter.write("              <LatitudeDegrees>" +
                            latitude + "</LatitudeDegrees>\n");
                    bufferedWriter.write("              <LongitudeDegrees>" +
                            longitude + "</LongitudeDegrees>\n");
                    bufferedWriter.write("            </Position>\n");
                }
            }

            if (haveAltitude && dataValid(cursor, SensorType.ALTITUDE.name())) {
                altitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.ALTITUDE.name()));
                bufferedWriter.write("            <AltitudeMeters>" +
                        altitude + "</AltitudeMeters>\n");
            }

            if (haveDistance && dataValid(cursor, SensorType.DISTANCE_m.name())) {
                distance = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.DISTANCE_m.name()));
                bufferedWriter.write("            <DistanceMeters>" +
                        distance + "</DistanceMeters>\n");
            }

            if (haveHR && dataValid(cursor, SensorType.HR.name())) {
                bufferedWriter.write("            <HeartRateBpm xsi:type=\"HeartRateInBeatsPerMinute_t\">\n");
                hr = cursor.getInt(cursor.getColumnIndexOrThrow(SensorType.HR.name()));
                bufferedWriter.write("              <Value>" +
                        hr + "</Value>\n");
                bufferedWriter.write("            </HeartRateBpm>\n");
            }

            if (haveBikeCadence && dataValid(cursor, SensorType.CADENCE.name())) {
                cadence = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.CADENCE.name()));
                bufferedWriter.write("            <Cadence>" +
                        cadence + "</Cadence>\n");
            }

            if ((haveSpeed || havePower || haveRunCadence)
                    && (dataValid(cursor, SensorType.SPEED_mps.name())
                    || dataValid(cursor, SensorType.POWER.name())
                    || dataValid(cursor, SensorType.CADENCE.name()))) {
                bufferedWriter.write("            <Extensions>\n");
                bufferedWriter.write("              <TPX xmlns=\"http://www.garmin.com/xmlschemas/ActivityExtension/v2\">\n");
                if (haveSpeed && dataValid(cursor, SensorType.SPEED_mps.name())) {
                    speed = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.SPEED_mps.name()));
                    bufferedWriter.write("            <Speed>" +
                            speed + "</Speed>\n");
                }

                if (havePower && dataValid(cursor, SensorType.POWER.name())) {
                    power = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.POWER.name()));
                    bufferedWriter.write("            <Watts>" +
                            power + "</Watts>\n");
                }

                if (haveRunCadence && dataValid(cursor, SensorType.CADENCE.name())) {
                    cadence = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.CADENCE.name()));
                    bufferedWriter.write("            <RunCadence>" +
                            cadence + "</RunCadence>\n");
                }


                bufferedWriter.write("              </TPX>\n");
                bufferedWriter.write("            </Extensions>\n");
            }
            bufferedWriter.write("          </Trackpoint>\n");
        }

        // now the tail
        bufferedWriter.write("        </Track>\n");
        bufferedWriter.write("      </Lap>\n");
        bufferedWriter.write("    </Activity>\n");
        bufferedWriter.write("  </Activities>\n");
        bufferedWriter.write("  <Creator xsi:type=\"Device_t\">\n");
        bufferedWriter.write("    <Name>" + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + "</Name>\n");
//        bufferedWriter.write("        <UnitId>0</UnitId>\n");
//        bufferedWriter.write("        <ProductID>0</ProductID>\n");
//        bufferedWriter.write("        <Version>\n");
//        bufferedWriter.write("            <VersionMajor>" + TrainingApplication.getVersionMajor() + "</VersionMajor>\n");
//        bufferedWriter.write("            <VersionMinor>" + TrainingApplication.getVersionMinor() + "</VersionMinor>\n");
//        bufferedWriter.write("            <BuildMajor>0</BuildMajor>\n");
//        bufferedWriter.write("            <BuildMinor>0</BuildMinor>\n");
//        bufferedWriter.write("        </Version>\n");
        bufferedWriter.write("  </Creator>\n");
        bufferedWriter.write("  <Author xsi:type=\"Application_t\">\n");
        String name = TrainingApplication.getAppName();
        if (HavePressureSensor.havePressureSensor(mContext)) {
            name += " with barometer";
        } // add "with barometer" when a barometer was/is available, see Strava API Documentations
        bufferedWriter.write("    <Name>" + name + "</Name>\n");
//        bufferedWriter.write("        <Build>\n");
//        bufferedWriter.write("            <Version>\n");
//        bufferedWriter.write("                <VersionMajor>" + TrainingApplication.getVersionMajor() + "</VersionMajor>\n");
//        bufferedWriter.write("                <VersionMinor>" + TrainingApplication.getVersionMinor() + "</VersionMinor>\n");
//        bufferedWriter.write("                <BuildMajor>0</BuildMajor>\n");
//        bufferedWriter.write("                <BuildMinor>0</BuildMinor>\n");
//        bufferedWriter.write("            </Version>\n");
//        bufferedWriter.write("            <Type>Beta</Type>\n");
//        bufferedWriter.write("        </Build>\n");
//        bufferedWriter.write("        <LangID>en</LangID>\n");  // TODO: change LangID when necessary!
//        bufferedWriter.write("        <PartNumber>0</PartNumber>\n");
        bufferedWriter.write("  </Author>\n");
        bufferedWriter.write("</TrainingCenterDatabase>\n");

        bufferedWriter.close();

        // TODO: which order ???
        cursor.close();
        databaseManager.closeDatabase(); // db.close();

        return new ExportResult(true, false, "Successfully exported to TCX File");
    }

}
