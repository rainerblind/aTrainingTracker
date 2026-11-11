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
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.database.DatabaseConstants;
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository;
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutExportHeader;
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository;
import com.atrainingtracker.trainingtracker.exporter.BaseExporter;
import com.atrainingtracker.trainingtracker.exporter.ExportInfo;
import com.atrainingtracker.trainingtracker.exporter.ExportType;
import com.atrainingtracker.trainingtracker.exporter.FileFormat;

import java.io.File;

public abstract class BaseFileWriter extends BaseExporter {
    protected static final int MIN_DATA_POINTS_FOR_UPLOAD = 10;
    private static final String TAG = "BaseFileExporter";
    private static final boolean DEBUG = false;
    String startTime, totalTime, data, goal, method, totalDistance, description, workoutName;
    boolean indoorTrainerSession, haveDistance, haveSpeed, havePower, haveHR, haveCadence, haveRunCadence, haveBikeCadence, haveTorque, haveAltitude, haveGeo;
    long workoutID, sportTypeId;

    public BaseFileWriter(@NonNull Context context) {
        super(context);
    }

    @NonNull
    public File getOutputFile(@NonNull ExportInfo exportInfo) {
        return new File(getBaseDirFile(mContext), exportInfo.getShortPath());
    }

    public void deleteOutputFileSafely(@NonNull ExportInfo exportInfo) {
        File file = getOutputFile(exportInfo);
        if (file.exists()) {
            boolean deleted = file.delete();
            Log.i(TAG, "Deleted output file on cancellation or failure: " + file.getAbsolutePath() + " (success=" + deleted + ")");
        }
    }

    /**
     * Determines whether copying exported files to the shared public Downloads directory
     * is supported on the active Android runtime (Android 10+ / API 29+ Scoped Storage).
     *
     * @return true if running on Android Q or higher, false otherwise
     */
    protected boolean isDownloadsCopySupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    /**
     * Post-export hook invoked upon successful export completion.
     * Preserves public Downloads copy for explicit user file exports (CSV, TCX, GPX, FIT, GC)
     * while strictly excluding internal uploads (STRAVA or COMMUNITY) to protect athlete privacy
     * from leaking GPS traces into public shared storage (REQ-EXP-022, ATT-4013).
     *
     * @param exportInfo the completed export metadata descriptor
     */
    @Override
    protected void onFinished(@NonNull ExportInfo exportInfo) {
        if (exportInfo.getFileFormat() != FileFormat.STRAVA && exportInfo.getExportType() != ExportType.COMMUNITY) {
            if (isDownloadsCopySupported()) {
                copyFileToDownloads(exportInfo);
            }
        }
    }


    protected WorkoutExportHeader exportHeader;

    protected void getHeaderData(@NonNull ExportInfo exportInfo) {
        exportHeader = WorkoutRepository.getInstance(mContext).getWorkoutExportHeader(exportInfo.getFileBaseName());
        if (exportHeader != null) {
            startTime = exportHeader.getStartTime();
            totalTime = exportHeader.getTotalTime();
            data = exportHeader.getData();
            goal = exportHeader.getGoal();
            method = exportHeader.getMethod();
            totalDistance = exportHeader.getTotalDistance();
            description = exportHeader.getDescription();
            workoutName = exportHeader.getWorkoutName();
            workoutID = exportHeader.getWorkoutId();
            sportTypeId = exportHeader.getSportTypeId();
            indoorTrainerSession = exportHeader.getIndoorTrainerSession();
        } else {
            startTime = "";
            totalTime = "0";
            data = "--------";
            goal = "";
            method = "";
            totalDistance = "0";
            description = "";
            workoutName = "";
            workoutID = DatabaseConstants.INVALID_ID;
            sportTypeId = DatabaseConstants.INVALID_ID;
            indoorTrainerSession = false;
        }

        haveDistance = data.indexOf('D') > 0;
        haveSpeed = data.indexOf('S') > 0;
        havePower = data.indexOf('P') > 0;
        haveHR = data.indexOf('H') > 0;
        haveCadence = data.indexOf('C') > 0;
        haveTorque = data.indexOf('N') > 0;
        haveAltitude = data.indexOf('A') > 0;
        haveGeo = data.indexOf('G') > 0;

        // selective upload
        if (exportInfo.getExportType() == ExportType.FILE) {
            if (exportInfo.getFileFormat() == FileFormat.STRAVA) {
                // Log.i(TAG, "selective Strava settings");
                haveGeo = haveGeo & TrainingApplication.uploadStravaGPS();
                haveAltitude = haveAltitude & TrainingApplication.uploadStravaAltitude();
                haveHR = haveHR & TrainingApplication.uploadStravaHR();
                havePower = havePower & TrainingApplication.uploadStravaPower();
                haveCadence = haveCadence & TrainingApplication.uploadStravaCadence();
            }
        }

        BSportType bSportType = SportTypesRepository.getInstance(mContext).getBSportType(sportTypeId);
        haveBikeCadence = haveCadence & bSportType == BSportType.BIKE;
        haveRunCadence = haveCadence & bSportType == BSportType.RUN;
    }
}
