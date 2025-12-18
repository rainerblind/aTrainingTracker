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

import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.sensor.SensorValueType;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import au.com.bytecode.opencsv.CSVWriter;

public class CSVFileExporter extends BaseExporter {
    private static final String TAG = "CSVFileExporter";
    private static final boolean DEBUG = TrainingApplication.getDebug(false);

    public CSVFileExporter(Context context) {
        super(context);
    }

    @Override
    public ExportResult doExport(ExportInfo exportInfo)
            throws IOException {
        if (DEBUG) Log.d(TAG, "exportToFile: " + exportInfo.getFileBaseName());

        WorkoutSamplesDatabaseManager databaseManager = WorkoutSamplesDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = db.query(WorkoutSamplesDatabaseManager.getTableName(exportInfo.getFileBaseName()),
                null,
                null,
                null,
                null,
                null,
                null);  // sorting


        // get column names, and sort them.  But only the ones with the source
        String[] columnNames = cursor.getColumnNames();

        List<String> sortedNames = new LinkedList<>();    // list for the sensors without the source.  Here, we want to keep the order
        List<String> unsortedNames = new LinkedList<>();    // list for the sensors with the source.  Since they are added whenever they appear, they are unsorted

        Pattern pattern = Pattern.compile("(.*)( \\(.*\\)|_gps|_network|_google_fused)");
        Map<String, SensorValueType> columnName2Type = new HashMap<>();

        for (int i = 0; i < columnNames.length; i++) {
            String columnName = columnNames[i];
            // reconstruct the original SensorType.
            String sensorTypeName = "";
            Matcher matcher = pattern.matcher(columnName);
            if (matcher.find()) {  // one with the source
                unsortedNames.add(columnName);
                sensorTypeName = matcher.group(1);
                if (DEBUG) Log.i(TAG, columnName + " is a " + sensorTypeName);
            } else {     // one without source
                if (DEBUG) Log.i(TAG, columnName + " is already a SensorType");
                sortedNames.add(columnName);
                sensorTypeName = columnName;
            }

            try {
                columnName2Type.put(columnName, SensorType.valueOf(sensorTypeName).getSensorValueType());
            } catch (Exception e) {
                columnName2Type.put(columnName, SensorValueType.STRING);
            }
        }

        // now, sort the ones with the source
        Collections.sort(unsortedNames);

        // and finally, append them to the sorted list
        sortedNames.addAll(unsortedNames);


        // now, we are ready to write to CSV

        CSVWriter csvWrite = new CSVWriter(getWriter(mContext, exportInfo.getShortPath(), "application/gpx+xml"));

        // first of all: header with column names
        csvWrite.writeNext(sortedNames.toArray(new String[sortedNames.size()]));

        String[] columnString = new String[cursor.getColumnCount()];

        int lines = cursor.getCount();
        int count = 0;
        int csvIndex = 0;

        while (cursor.moveToNext()) {
            csvIndex = 0;

            for (String columnName : sortedNames) {
                int columnIndex = cursor.getColumnIndex(columnName);

                // previously, we simply had
                // columnString[columnIndex] = cursor.getString(columnIndex);
                // but this leads to problems because double values might be written with only 3 or 4 digits after the point
                if (cursor.isNull(columnIndex)) {
                    columnString[csvIndex] = "";
                } else {
                    switch (columnName2Type.get(columnName)) {
                        case DOUBLE:
                            columnString[csvIndex] = Double.toString(cursor.getDouble(columnIndex));
                            break;
                        case INTEGER:
                            columnString[csvIndex] = Integer.toString(cursor.getInt(columnIndex));
                            break;
                        case STRING:
                        default:
                            columnString[csvIndex] = cursor.getString(columnIndex);
                            break;
                    }
                }

                csvIndex++;
            }
            csvWrite.writeNext(columnString);

            notifyProgress(lines, count++);
        }
        csvWrite.close();
        cursor.close();
        databaseManager.closeDatabase(); // db.close();


        return new ExportResult(true, getPositiveAnswer(exportInfo));
    }

    @Override
    protected Action getAction() {
        return Action.EXPORT;
    }

}
