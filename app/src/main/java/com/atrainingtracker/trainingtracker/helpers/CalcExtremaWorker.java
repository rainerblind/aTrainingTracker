package com.atrainingtracker.trainingtracker.helpers;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.ExtremaType;
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager;
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager.MyLocation;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.google.android.gms.maps.model.LatLng;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class CalcExtremaWorker extends Worker {
    private static final String TAG = CalcExtremaWorker.class.getSimpleName();
    private static final boolean DEBUG = TrainingApplication.getDebug(true);

    // --- KEYS for Input/Output/Progress Data ---
    public static final String KEY_WORKOUT_ID = "WORKOUT_ID";


    private int progressCounter = 0;
    public static final String KEY_PROGRESS_SEQUENCE = "PROGRESS_SEQUENCE";

    public static final String KEY_STARTING_MESSAGE = "STARTING_MESSAGE";


    public static final String KEY_FINISHED_MESSAGE = "FINISHED_MESSAGE";
    public static final String FINISHED_EXTREMA_VALE = "EXTREMA_VALUE";
    public static final String FINISHED_COMMUTE_AND_TRAINER = "COMMUTE_AND_TRAINER";
    public static final String FINISHED_AUTO_NAME = "AUTO_NAME";



    // Same sensor types from the old thread
    private static final List<SensorType> IMPORTANT_SENSOR_TYPES = Arrays.asList(
            SensorType.ALTITUDE,
            SensorType.CADENCE,
            SensorType.HR,
            SensorType.PACE_spm,
            SensorType.PEDAL_POWER_BALANCE,
            SensorType.PEDAL_SMOOTHNESS_L,
            SensorType.PEDAL_SMOOTHNESS_R,
            SensorType.POWER,
            SensorType.SPEED_mps,
            SensorType.TEMPERATURE,
            SensorType.TORQUE,
            SensorType.TORQUE_EFFECTIVENESS_L,
            SensorType.TORQUE_EFFECTIVENESS_R
    );

    public CalcExtremaWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }


    private void publishStarting(String message) {
        progressCounter++;
        Data progressData = new Data.Builder()
                .putString(KEY_STARTING_MESSAGE, message)
                .putInt(KEY_PROGRESS_SEQUENCE, progressCounter)
                .build();

        setProgressAsync(progressData);
    }

    private void publishFinished (String message) {
        progressCounter++;
        Data progressData = new Data.Builder()
                .putString(KEY_FINISHED_MESSAGE, message)
                .putInt(KEY_PROGRESS_SEQUENCE, progressCounter)
                .build();

        setProgressAsync(progressData);
    }



    @NonNull
    @Override
    public Result doWork() {
        long workoutId = getInputData().getLong(KEY_WORKOUT_ID, -1);
        if (workoutId == -1) {
            Log.e(TAG, "Invalid workoutId provided. Cannot perform work.");
            return Result.failure();
        }

        Context context = getApplicationContext();

        try {
            if (DEBUG) Log.d(TAG, "Starting extrema calculation for workout " + workoutId);
            publishStarting(context.getString(R.string.initializing));

            String baseFileName = WorkoutSummariesDatabaseManager.getInstance(context).getBaseFileName(workoutId);

            calcAndSaveExtremaValues(context, workoutId, baseFileName, Collections.singletonList(SensorType.LINE_DISTANCE_m), Arrays.asList(ExtremaType.MAX, ExtremaType.END));
            calcAndSaveExtremaValues(context, workoutId, baseFileName, Arrays.asList(SensorType.LATITUDE, SensorType.LONGITUDE), Arrays.asList(ExtremaType.START, ExtremaType.END));
            calcAndSaveMaxLineDistancePosition(context, workoutId);

            calcFancyName(context, workoutId);

            guessCommuteAndTrainer(context, workoutId);

            // -- calc min, mean, and max values --
            // first, we need the accumulated sensors of this workout
            Set<SensorType> accumulatedSensorTypes = WorkoutSummariesDatabaseManager.getInstance(context).getAccumulatedSensorTypes(workoutId);

            // if there are no sensors stored (due to upgrading from DB version 3 to 4, we use all important sensors
            if (accumulatedSensorTypes.isEmpty()) {
                accumulatedSensorTypes = new HashSet<>(IMPORTANT_SENSOR_TYPES);
            } else {
                // otherwise, we use the intersection of the available ones and the important ones
                accumulatedSensorTypes.retainAll(IMPORTANT_SENSOR_TYPES);
            }
            // now, calc the values for all interesting available sensors
            calcAndSaveExtremaValues(context, workoutId, baseFileName, accumulatedSensorTypes, Arrays.asList(ExtremaType.MIN, ExtremaType.AVG, ExtremaType.MAX));

            // finally, store that the extrema values are calculated TODO: do not use the database, delegate to the database manager, instead.
            ContentValues values = new ContentValues();
            values.put(WorkoutSummaries.EXTREMA_VALUES_CALCULATED, 1);
            WorkoutSummariesDatabaseManager.getInstance(context).getDatabase().update(WorkoutSummaries.TABLE, values, WorkoutSummaries.C_ID + "=?", new String[]{Long.toString(workoutId)});


            if (DEBUG) Log.d(TAG, "Successfully finished extrema calculations for workout " + workoutId);

            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Error calculating extrema values for workout " + workoutId, e);
            return Result.failure();
        }
    }


    // --- Helper Methods (Copied from CalcExtremaValuesThread and made non-static) ---

    public void calcAndSaveMaxLineDistancePosition(Context context, final long workoutId) {
        if (DEBUG) Log.i(TAG, "calcAndSaveMaxLineDistancePosition: workoutId=" + workoutId);
        publishStarting(context.getString(R.string.calculating_extrema_value_for,
                ExtremaType.MAX.name(),
                context.getString(SensorType.LINE_DISTANCE_m.getShortNameId())));

        WorkoutSummariesDatabaseManager workoutSummariesDatabaseManager = WorkoutSummariesDatabaseManager.getInstance(context);
        WorkoutSamplesDatabaseManager.LatLngValue latLngValue = WorkoutSamplesDatabaseManager.getInstance(context).getExtremaPosition(workoutSummariesDatabaseManager, workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX);
        if (latLngValue == null) {
            return;
        }  // TODO: when does this happen and what follows when we have no maxLineDistancePosition???

        // save the location in the database
        SQLiteDatabase summariesDb = WorkoutSummariesDatabaseManager.getInstance(context).getDatabase();
        ContentValues values = new ContentValues();

        // first, the latitude
        values.put(WorkoutSummaries.WORKOUT_ID, workoutId);
        values.put(WorkoutSummaries.EXTREMA_TYPE, ExtremaType.MAX_LINE_DISTANCE.name());
        values.put(WorkoutSummaries.SENSOR_TYPE, SensorType.LATITUDE.name());
        values.put(WorkoutSummaries.VALUE, latLngValue.latLng.latitude);

        String whereClause = WorkoutSummaries.WORKOUT_ID + "=? AND " + WorkoutSummaries.EXTREMA_TYPE + "=? AND " + WorkoutSummaries.SENSOR_TYPE + "=?"; // selection,
        String[] whereArgs = new String[]{Long.toString(workoutId), ExtremaType.MAX_LINE_DISTANCE.name(), SensorType.LATITUDE.name()}; // selectionArgs,

        Cursor cursor = summariesDb.query(WorkoutSummaries.TABLE_EXTREMA_VALUES,
                null, // columns
                whereClause,
                whereArgs,
                null, null, null); // groupBy, having, orderBy)
        if (cursor.getCount() == 0) { // no value yet
            summariesDb.insert(WorkoutSummaries.TABLE_EXTREMA_VALUES, null, values);
        } else {
            summariesDb.update(WorkoutSummaries.TABLE_EXTREMA_VALUES, values, whereClause, whereArgs);
        }

        // now, the longitude
        // values.put(WorkoutSummaries.WORKOUT_ID,    workoutId);
        // values.put(WorkoutSummaries.EXTREMA_TYPE, ExtremaType.MAX_LINE_DISTANCE.name());
        values.put(WorkoutSummaries.SENSOR_TYPE, SensorType.LONGITUDE.name());
        values.put(WorkoutSummaries.VALUE, latLngValue.latLng.longitude);

        // whereClause = WorkoutSummaries.WORKOUT_ID + "=? AND " + WorkoutSummaries.EXTREMA_TYPE + "=? AND " + WorkoutSummaries.SENSOR_TYPE + "=?"; // selection,
        whereArgs = new String[]{Long.toString(workoutId), ExtremaType.MAX_LINE_DISTANCE.name(), SensorType.LONGITUDE.name()}; // selectionArgs,

        cursor = summariesDb.query(WorkoutSummaries.TABLE_EXTREMA_VALUES,
                null, // columns
                whereClause,
                whereArgs,
                null, null, null); // groupBy, having, orderBy)
        if (cursor.getCount() == 0) { // no value yet
            summariesDb.insert(WorkoutSummaries.TABLE_EXTREMA_VALUES, null, values);
        } else {
            summariesDb.update(WorkoutSummaries.TABLE_EXTREMA_VALUES, values, whereClause, whereArgs);
        }
    }

    private void calcFancyName(Context context, long workoutId) {
        if (DEBUG) Log.i(TAG, "calcFancyName");
        publishStarting(context.getString(R.string.calc_workout_name));

        WorkoutSummariesDatabaseManager workoutSummariesDatabaseManager = WorkoutSummariesDatabaseManager.getInstance(context);
        KnownLocationsDatabaseManager knownLocationsDatabaseManager = KnownLocationsDatabaseManager.getInstance(context);
        SportTypeDatabaseManager sportTypeDatabaseManager = SportTypeDatabaseManager.getInstance(context);
        MyLocation startLocation = null;

        Double startLat = workoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.LATITUDE, ExtremaType.START);
        Double startLon = workoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.LONGITUDE, ExtremaType.START);
        if (startLat != null && startLon != null) {
            startLocation = knownLocationsDatabaseManager.getMyLocation(new LatLng(startLat, startLon));
        }

        MyLocation maxLineLocation = null;
        Double maxLineLat = workoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.LATITUDE, ExtremaType.MAX_LINE_DISTANCE);
        Double maxLineLon = workoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.LONGITUDE, ExtremaType.MAX_LINE_DISTANCE);
        if (maxLineLat != null && maxLineLon != null) {
            maxLineLocation = knownLocationsDatabaseManager.getMyLocation(new LatLng(maxLineLat, maxLineLon));
        }

        MyLocation endLocation = null;
        Double endLat = workoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.LATITUDE, ExtremaType.END);
        Double endLon = workoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.LONGITUDE, ExtremaType.END);
        if (endLat != null && endLon != null) {
            endLocation = knownLocationsDatabaseManager.getMyLocation(new LatLng(endLat, endLon));
        }

        Long sportTypeId = workoutSummariesDatabaseManager.getLong(workoutId, WorkoutSummaries.SPORT_ID);
        if (sportTypeId == null) {
            sportTypeId = SportTypeDatabaseManager.getDefaultSportTypeId();
        }
        String fancyName = workoutSummariesDatabaseManager.getFancyName(sportTypeDatabaseManager, sportTypeId, startLocation, maxLineLocation, endLocation);

        if (fancyName != null) {

            // TODO: use method of workoutSummariesDatabaseManager instead
            ContentValues contentValues = new ContentValues();
            contentValues.put(WorkoutSummaries.WORKOUT_NAME, fancyName);

            SQLiteDatabase db = workoutSummariesDatabaseManager.getDatabase();
            db.update(WorkoutSummaries.TABLE, contentValues, WorkoutSummaries.C_ID + " = ?", new String[]{Long.toString(workoutId)});

            // inform others that we are done
            publishFinished(FINISHED_AUTO_NAME);
        }
    }

    private void guessCommuteAndTrainer(Context context, long workoutId) {
        if (DEBUG) Log.i(TAG, "guessCommuteAndTrainer");
        publishStarting(context.getString(R.string.guess_commute_and_trainer));

        WorkoutSummariesDatabaseManager workoutSummariesDatabaseManager = WorkoutSummariesDatabaseManager.getInstance(context);


        // get max away points and guess commute and trainer
        boolean commute = false, trainer = false;
        Double distance = workoutSummariesDatabaseManager.getDouble(workoutId, WorkoutSummaries.DISTANCE_TOTAL_m);
        Double maxLineDistance = workoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX);
        Double endLineDistance = workoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.END);
        if (DEBUG)
            Log.i(TAG, "distance=" + distance + ", max line distance=" + maxLineDistance + ", end line distance=" + endLineDistance);

        if (maxLineDistance != null) {

            // guess trainer
            if (distance != null) {
                if (maxLineDistance < TrainingApplication.DISTANCE_TO_MAX_THRESHOLD_FOR_TRAINER) {
                    trainer = true;
                }
            }
            if (DEBUG)
                Log.i(TAG, "This seems to be " + (trainer ? "" : "NOT ") + "a trainer session");


            // guess commute
            if (endLineDistance != null) {
                if (maxLineDistance < endLineDistance * TrainingApplication.DISTANCE_TO_MAX_RATIO_FOR_COMMUTE) {
                    commute = true;
                }
            }
        } else { // ok, it looks like there was never a valid GPS location.  Most likely this was an indoor activity (or a very short one)
            trainer = true;
        }
        if (DEBUG) Log.i(TAG, "trainer=" + trainer + ", commute=" + commute);


        // when commute and trainer are not contradicting, add them to the database
        if (commute ^ trainer) { // xor
            ContentValues values = new ContentValues();
            values.put(WorkoutSummaries.COMMUTE, commute);
            values.put(WorkoutSummaries.TRAINER, trainer);

            if (DEBUG) Log.i(TAG, "updating WorkoutSummaries for workoutId=" + workoutId);
            // TODO: use method of workoutSummariesDatabaseManager instead
            SQLiteDatabase db = WorkoutSummariesDatabaseManager.getInstance(context).getDatabase();
            db.update(WorkoutSummaries.TABLE,
                    values,
                    WorkoutSummaries.C_ID + "=?",
                    new String[]{Long.toString(workoutId)});
        }

        // inform others that this is done
        publishFinished(FINISHED_COMMUTE_AND_TRAINER);
    }

    private void calcAndSaveExtremaValues(Context context, long workoutId, String baseFileName, @NonNull Iterable<SensorType> sensorTypeList, @NonNull Iterable<ExtremaType> extremaTypeList) {
        if (DEBUG) Log.i(TAG, "calcAndSaveExtremaValues(" + workoutId + "...)");

        WorkoutSamplesDatabaseManager workoutSamplesDatabaseManager = WorkoutSamplesDatabaseManager.getInstance(context);
        WorkoutSummariesDatabaseManager workoutSummariesDatabaseManager = WorkoutSummariesDatabaseManager.getInstance(context);

        // WorkoutSamplesDbHelper workoutSamplesDbHelper = new WorkoutSamplesDbHelper(mContext);
        SQLiteDatabase summariesDb = workoutSummariesDatabaseManager.getDatabase();

        ContentValues values = new ContentValues();

        for (SensorType sensorType : sensorTypeList) {
            for (ExtremaType extremaType : extremaTypeList) {
                publishStarting(context.getString(R.string.calculating_extrema_value_for, extremaType.name(), context.getString(sensorType.getShortNameId())));

                WorkoutSamplesDatabaseManager.getInstance(context);
                Double value = workoutSamplesDatabaseManager.calcExtremaValue(workoutSummariesDatabaseManager, baseFileName, extremaType, sensorType);
                if (value != null) {
                    if (DEBUG)
                        Log.i(TAG, "saving " + extremaType.name() + " of " + sensorType.name() + ": " + value);
                    // TODO: create and use method of workoutSummariesDatabaseManager instead
                    // save the value in the corresponding database
                    values.put(WorkoutSummaries.WORKOUT_ID, workoutId);
                    values.put(WorkoutSummaries.EXTREMA_TYPE, extremaType.name());
                    values.put(WorkoutSummaries.SENSOR_TYPE, sensorType.name());
                    values.put(WorkoutSummaries.VALUE, value);

                    String whereClause = WorkoutSummaries.WORKOUT_ID + "=? AND " + WorkoutSummaries.EXTREMA_TYPE + "=? AND " + WorkoutSummaries.SENSOR_TYPE + "=?"; // selection,
                    String[] whereArgs = new String[]{Long.toString(workoutId), extremaType.name(), sensorType.name()}; // selectionArgs,

                    Cursor cursor = summariesDb.query(WorkoutSummaries.TABLE_EXTREMA_VALUES,
                            null, // columns
                            whereClause,
                            whereArgs,
                            null, null, null); // groupBy, having, orderBy)
                    if (cursor.getCount() == 0) { // no value yet
                        summariesDb.insert(WorkoutSummaries.TABLE_EXTREMA_VALUES, null, values);
                    } else {
                        summariesDb.update(WorkoutSummaries.TABLE_EXTREMA_VALUES, values, whereClause, whereArgs);
                    }
                    cursor.close();

                    if (!summariesDb.isOpen()) {
                        Log.d(TAG, "WTF: database is not open! -----------------------");
                    }
                } else {
                    if (DEBUG)
                        Log.i(TAG, "did not save " + extremaType.name() + " of " + sensorType.name() + " because its value is null");

                }
            }

            publishFinished(FINISHED_EXTREMA_VALE);
        }
    }

}