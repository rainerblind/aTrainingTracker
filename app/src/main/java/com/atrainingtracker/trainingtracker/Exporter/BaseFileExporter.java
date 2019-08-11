package com.atrainingtracker.trainingtracker.Exporter;

import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public abstract class BaseFileExporter extends BaseExporter {
    protected static final int MIN_DATA_POINTS_FOR_UPLOAD = 10;
    private static final String TAG = "BaseFileExporter";
    private static final boolean DEBUG = false;
    String startTime, totalTime, samplingTime, athleteName, data, goal, method, totalDistance, description;
    boolean indoorTrainerSession, haveDistance, haveSpeed, havePower, haveHR, haveCadence, haveRunCadence, haveBikeCadence, haveTorque, haveAltitude, haveGeo;
    long workoutID, sportTypeId;

    public BaseFileExporter(Context context) {
        super(context);
    }

    protected void getHeaderData(ExportInfo exportInfo) {
        WorkoutSummariesDatabaseManager databaseManager = WorkoutSummariesDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        Cursor cursor = db.query(WorkoutSummaries.TABLE,
                null,
                WorkoutSummaries.FILE_BASE_NAME + "=?",
                new String[]{exportInfo.getFileBaseName()},
                null,
                null,
                null);
        cursor.moveToFirst();

        // get the data for the header
        startTime = myGet(cursor, WorkoutSummaries.TIME_START, "");
        totalTime = myGet(cursor, WorkoutSummaries.TIME_TOTAL_s, "0");
        samplingTime = myGet(cursor, WorkoutSummaries.SAMPLING_TIME, null);
        athleteName = myGet(cursor, WorkoutSummaries.ATHLETE_NAME, TrainingApplication.getAthleteName()); // TODO: Preferences/myANTPLusApplication
        data = myGet(cursor, WorkoutSummaries.GC_DATA, "--------");
        goal = myGet(cursor, WorkoutSummaries.GOAL, "");
        method = myGet(cursor, WorkoutSummaries.METHOD, "");
        totalDistance = myGet(cursor, WorkoutSummaries.DISTANCE_TOTAL_m, "0");
        description = myGet(cursor, WorkoutSummaries.DESCRIPTION, "");
        workoutID = cursor.getLong(cursor.getColumnIndex(WorkoutSummaries.C_ID));
        sportTypeId = cursor.getLong(cursor.getColumnIndex(WorkoutSummaries.SPORT_ID));
        indoorTrainerSession = cursor.getInt(cursor.getColumnIndex(WorkoutSummaries.TRAINER)) > 0;

        // now, that we have all the data, we close the mCursor
        cursor.close();
        databaseManager.closeDatabase(); // db.close();

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
            } else if (exportInfo.getFileFormat() == FileFormat.RUNKEEPER) {
                haveGeo = haveGeo & TrainingApplication.uploadRunkeeperGPS();
                haveHR = haveHR & TrainingApplication.uploadRunkeeperHR();
            } else if (exportInfo.getFileFormat() == FileFormat.TRAINING_PEAKS) {
                // Log.i(TAG, "selective TrainingPeaks settings");
                haveGeo = haveGeo & TrainingApplication.uploadTrainingPeaksGPS();
                haveAltitude = haveAltitude & TrainingApplication.uploadTrainingPeaksAltitude();
                haveHR = haveHR & TrainingApplication.uploadTrainingPeaksHR();
                havePower = havePower & TrainingApplication.uploadTrainingPeaksPower();
                haveCadence = haveCadence & TrainingApplication.uploadTrainingPeaksCadence();
            }
        }

        BSportType bSportType = SportTypeDatabaseManager.getBSportType(sportTypeId);
        haveBikeCadence = haveCadence & bSportType == BSportType.BIKE;
        haveRunCadence = haveCadence & bSportType == BSportType.RUN;
    }

    protected BufferedWriter getBufferedWriter(ExportInfo exportInfo) throws IOException {
        File file = new File(getDir(exportInfo.getFileFormat().getDirName()), exportInfo.getFileBaseName() + exportInfo.getFileFormat().getFileEnding());
        file.createNewFile();
        return new BufferedWriter(new FileWriter(file));
    }

    protected boolean doesFileAlreadyExist(ExportInfo exportInfo) {
        return (new File(getDir(exportInfo.getFileFormat().getDirName()), exportInfo.getFileBaseName() + exportInfo.getFileFormat().getFileEnding())).exists();
    }

    protected String myGet(Cursor cursor, String name, String defaultValue) {
        String foo = defaultValue;
        try {
            foo = cursor.getString(cursor.getColumnIndexOrThrow(name));
        } catch (CursorIndexOutOfBoundsException e) {
            Log.e(TAG, e.toString());
        }
        if (foo == null || foo.equals("")) {
            foo = defaultValue;
        }
        return foo;
    }
}
