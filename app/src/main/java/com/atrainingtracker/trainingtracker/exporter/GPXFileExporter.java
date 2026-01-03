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

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.helpers.HavePressureSensor;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;


public class GPXFileExporter extends BaseFileExporter {
    protected static final boolean WRITE_ONLY_ON_NEW_GEO_DATA = true;
    private static final String TAG = "GPXFileExporter";
    private static final boolean DEBUG = false;
    // DateFormats to convert from Db style dates to XML style dates
    protected static final SimpleDateFormat msdfFromDb = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    protected static final SimpleDateFormat msdfToXML = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);

    public GPXFileExporter(Context context) {
        super(context);
    }

    // convert from "2012-03-29 16:23:05" to "2012-03-29T16:23:05Z"
    protected static String dbTime2XMLTime(String dbTime) throws ParseException {
        return msdfToXML.format(msdfFromDb.parse(dbTime)) + "Z";
    }

    @Override
    protected ExportResult doExport(ExportInfo exportInfo)
            throws IOException, ParseException {
        if (DEBUG) Log.d(TAG, "exportToFile");

        getHeaderData(exportInfo);

        BufferedWriter bufferedWriter = getBufferedWriter(exportInfo);


        // write the header data to the file
        bufferedWriter.write("<?xml version=\"1.0\"?>\n");
        String name = TrainingApplication.getAppName();
        if (HavePressureSensor.havePressureSensor(mContext)) {
            name += " with barometer";
        } // add "with barometer" when a barometer was/is available, see Strava API Documentations
        bufferedWriter.write("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" creator=\"" + name + "\" version=\"1.1\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\"> \n");

        bufferedWriter.write(" <metadata>\n");
        bufferedWriter.write("  <time>" + dbTime2XMLTime(startTime) + "</time>\n");
        bufferedWriter.write(" </metadata>\n");
        bufferedWriter.write(" <trk>\n");
        bufferedWriter.write("  <name>" + startTime + "</name>\n");

        WorkoutSamplesDatabaseManager databaseManager = WorkoutSamplesDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = db.query(WorkoutSamplesDatabaseManager.getTableName(exportInfo.getFileBaseName()),
                null,
                null,
                null,
                null,
                null,
                null);

        double altitude;
        double latitude = 0.0;
        double longitude = 0.0;
        double latitudeOld;
        double longitudeOld;

        long prevLineLap = BANALService.INIT_LAP_NR - 1;

        int lines = cursor.getCount();

        int count = 0;
        while (cursor.moveToNext()) {

            int lap = cursor.getInt(cursor.getColumnIndexOrThrow(SensorType.LAP_NR.name()));
            if (prevLineLap != lap) { // new lap

                if (lap != BANALService.INIT_LAP_NR) { // finish previous lap
                    bufferedWriter.write("  </trkseg>\n");
                }

                // get the lap data
                SQLiteDatabase lapDb = LapsDatabaseManager.getInstance().getOpenDatabase();

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
                LapsDatabaseManager.getInstance().closeDatabase(); // instead of lapDb.close();

                bufferedWriter.write("  <trkseg>\n");

            }
            prevLineLap = lap;


            // TODO: extensions with atemp, hr, cadence, distance, hr, temp as described on http://strava.github.io/api/v3/uploads/
            if (haveGeo && dataValid(cursor, SensorType.LATITUDE.name()) && dataValid(cursor, SensorType.LONGITUDE.name())) {
                latitudeOld = latitude;
                longitudeOld = longitude;
                latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.LATITUDE.name()));
                longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.LONGITUDE.name()));

                if (WRITE_ONLY_ON_NEW_GEO_DATA && latitude == latitudeOld && longitude == longitudeOld) {
                    // do nothing
                } else {
                    bufferedWriter.write("   <trkpt lat=\"" + latitude + "\" lon=\"" + longitude + "\">\n");

                    if (haveAltitude && dataValid(cursor, SensorType.ALTITUDE.name())) {
                        altitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.ALTITUDE.name()));
                        bufferedWriter.write("    <ele>" + altitude + "</ele>\n");
                    }

                    bufferedWriter.write("    <time>"
                            + dbTime2XMLTime(cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSamplesDbHelper.TIME)))
                            + "</time>\n");

                    bufferedWriter.write("   </trkpt>\n");
                }
            }

            notifyProgress(lines, count++);
        }

        // now the tail
        bufferedWriter.write("  </trkseg>\n");
        bufferedWriter.write(" </trk>\n");
        bufferedWriter.write("</gpx>\n");

        bufferedWriter.close();

        // TODO: which order ???
        cursor.close();
        databaseManager.closeDatabase(); // db.close();

        return new ExportResult(true, getPositiveAnswer(exportInfo));
    }

    @Override
    protected Action getAction() {
        return Action.EXPORT;
    }

}
