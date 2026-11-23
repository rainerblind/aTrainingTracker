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
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.sensor.SensorValueType;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper;
import com.atrainingtracker.trainingtracker.exporter.ExportInfo;
import com.atrainingtracker.util.EnumUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

public class CSVFileWriter extends BaseFileWriter {
    private static final String TAG = "CSVFileExporter";
    private static final boolean DEBUG = TrainingApplication.getDebug(false);
    private static final String DEVICE_DELIMITER = " (";
    private static final String[] LOCATION_SOURCE_SUFFIXES = {
            "_gps", "_network", "_google_fused", "_att_fused"
    };

    public CSVFileWriter(@NonNull Context context) {
        super(context);
    }


    @Override
    protected void onFinished(@NonNull ExportInfo exportInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            copyFileToDownloads(exportInfo);
        }
    }

    @NonNull
    @Override
    public ExportResult doExport(@NonNull ExportInfo exportInfo)
            throws IOException {
        if (DEBUG) Log.d(TAG, "exportToFile: " + exportInfo.getFileBaseName());

        WorkoutSamplesDatabaseManager databaseManager = WorkoutSamplesDatabaseManager.getInstance(mContext);
        SQLiteDatabase db = databaseManager.getDatabase();
        try (Cursor cursor = db.query(WorkoutSamplesDatabaseManager.getTableName(exportInfo.getFileBaseName()),
                null,
                null,
                null,
                null,
                null,
                null)) {  // sorting

            // get column names, and sort them.  But only the ones with the source
            String[] columnNames = cursor.getColumnNames();

            List<String> sortedNames = new LinkedList<>();    // list for the sensors without the source.  Here, we want to keep the order
            List<String> unsortedNames = new LinkedList<>();    // list for the sensors with the source.  Since they are added whenever they appear, they are unsorted

            Map<String, SensorValueType> columnName2Type = new HashMap<>();

            for (String columnName : columnNames) {
                if (isMetadataColumn(columnName)) {
                    sortedNames.add(columnName);
                    columnName2Type.put(columnName, SensorValueType.STRING);
                    continue;
                }

                // Reconstruct the original SensorType before any device or location source suffix.
                String sensorTypeName = extractSourcedSensorTypeName(columnName);
                if (sensorTypeName != null) {  // one with the source
                    unsortedNames.add(columnName);
                    if (DEBUG) Log.i(TAG, columnName + " is a " + sensorTypeName);
                } else {     // one without source
                    if (DEBUG) Log.i(TAG, columnName + " is already a SensorType");
                    sortedNames.add(columnName);
                    sensorTypeName = columnName;
                }

                SensorType sensorType = EnumUtils.safeValueOfOrNull(SensorType.class, sensorTypeName, TAG);
                columnName2Type.put(columnName, sensorType != null ? sensorType.getSensorValueType() : SensorValueType.STRING);
            }

            // now, sort the ones with the source
            Collections.sort(unsortedNames);

            // and finally, append them to the sorted list
            sortedNames.addAll(unsortedNames);

            // now, we are ready to write to CSV
            try (Rfc4180CsvWriter csvWrite = new Rfc4180CsvWriter(getBufferedWriter(exportInfo.getShortPath()))) {
                // first of all: header with column names
                csvWrite.writeNext(sortedNames.toArray(new String[sortedNames.size()]));

                String[] columnString = new String[cursor.getColumnCount()];

                int lines = cursor.getCount();
                int count = 0;
                int csvIndex = 0;

                while (cursor.moveToNext()) {
                    if (isCancelled()) {
                        throw new CancellationException("Export cancelled by WorkManager");
                    }
                    csvIndex = 0;

                    for (String columnName : sortedNames) {
                        int columnIndex = cursor.getColumnIndex(columnName);

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
                    if (isCancelled()) {
                        throw new CancellationException("Export cancelled by WorkManager");
                    }
                }
            } catch (CancellationException e) {
                deleteOutputFileSafely(exportInfo);
                throw e;
            }
        }

        return new ExportResult(true, false, "Successfully exported to CSV");
    }

    /**
     * Returns {@code true} for non-sensor {@code WorkoutSamples.db} metadata columns
     * ({@code _id}, {@code time}) so they are exported verbatim without enum lookup.
     */
    private static boolean isMetadataColumn(@Nullable String columnName) {
        return WorkoutSamplesDbHelper.C_ID.equals(columnName)
                || WorkoutSamplesDbHelper.TIME.equals(columnName);
    }

    /**
     * Extracts the canonical {@link SensorType} prefix from a source-qualified sample column
     * (splitting on the first {@code " ("} before checking location suffixes so nested parentheses
     * like {@code ALTITUDE (Topografische Höhe (DEM))} resolve cleanly), or {@code null} if unsourced.
     */
    @Nullable
    static String extractSourcedSensorTypeName(@NonNull String columnName) {
        int parenIndex = columnName.indexOf(DEVICE_DELIMITER);
        if (parenIndex > 0) {
            return columnName.substring(0, parenIndex);
        }
        for (String suffix : LOCATION_SOURCE_SUFFIXES) {
            if (columnName.endsWith(suffix) && columnName.length() > suffix.length()) {
                return columnName.substring(0, columnName.length() - suffix.length());
            }
        }
        return null;
    }

}
