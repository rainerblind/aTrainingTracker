package com.atrainingtracker.trainingtracker.helpers;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.Sensor.SensorType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.ExtremumType;
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager;
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager.MyLocation;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.google.android.gms.maps.model.LatLng;

import java.util.Arrays;
import java.util.List;
import java.util.Set;


public class CalcExtremumValuesTask extends AsyncTask<Long, String, Boolean> {
    public static final String FINISHED_CALCULATING_EXTREMUM_VALUES = "com.atrainingtracker.helpers.CalcExtremumValuesTask.FINISHED_CALCULATING_EXTREMUM_VALUES";
    public static final String FINISHED_CALCULATING_EXTREMUM_VALUE = "com.atrainingtracker.helpers.CalcExtremumValuesTask.FINISHED_CALCULATING_EXTREMUM_VALUE";
    public static final String FINISHED_GUESSING_COMMUTE_AND_TRAINER = "com.atrainingtracker.helpers.CalcExtremumValuesTask.FINISHED_GUESSING_COMMUTE_AND_TRAINER";
    public static final String FINISHED_CALCULATING_FANCY_NAME = "com.atrainingtracker.helpers.CalcExtremumValuesTask.FINISHED_CALCULATING_FANCY_NAME";
    public static final String SENSOR_TYPE = "SENSOR_TYPE";
    public static final String FANCY_NAME = "FANCY_NAME";
    private static final String TAG = "CalcExtremumValuesTask";
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;
    private static final List<SensorType> IMPORTANT_SENSOR_TYPES = Arrays.asList(SensorType.ALTITUDE, SensorType.CADENCE, SensorType.HR, SensorType.PACE_spm, SensorType.PEDAL_POWER_BALANCE, SensorType.PEDAL_SMOOTHNESS_L, SensorType.PEDAL_SMOOTHNESS_R, SensorType.POWER, SensorType.SPEED_mps, SensorType.TEMPERATURE, SensorType.TORQUE, SensorType.TORQUE_EFFECTIVENESS_L, SensorType.TORQUE_EFFECTIVENESS_R);

    private Context mContext;
    private TextView mMessageTextView;

    public CalcExtremumValuesTask(Context context, TextView messageTextView) {
        mContext = context;
        mMessageTextView = messageTextView;

        if (DEBUG) Log.i(TAG, "CalcExtremumValuesTask()");
    }

    public static void calcAndSaveMaxLineDistancePosition(long workoutId) {
        if (DEBUG) Log.i(TAG, "calcAndSaveMaxLineDistancePosition: workoutId=" + workoutId);

        WorkoutSamplesDatabaseManager.LatLngValue latLngValue = WorkoutSamplesDatabaseManager.getExtremumPosition(workoutId, SensorType.LINE_DISTANCE_m, ExtremumType.MAX);
        if (latLngValue == null) {
            return;
        }  // TODO: when does this happen and what follows when we have no maxLineDistancePosition???

        // save the location in the database
        SQLiteDatabase summariesDb = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();
        ContentValues values = new ContentValues();

        // first, the latitude
        values.put(WorkoutSummaries.WORKOUT_ID, workoutId);
        values.put(WorkoutSummaries.EXTREMUM_TYPE, ExtremumType.MAX_LINE_DISTANCE.name());
        values.put(WorkoutSummaries.SENSOR_TYPE, SensorType.LATITUDE.name());
        values.put(WorkoutSummaries.VALUE, latLngValue.latLng.latitude);

        String whereClause = WorkoutSummaries.WORKOUT_ID + "=? AND " + WorkoutSummaries.EXTREMUM_TYPE + "=? AND " + WorkoutSummaries.SENSOR_TYPE + "=?"; // selection,
        String[] whereArgs = new String[]{Long.toString(workoutId), ExtremumType.MAX_LINE_DISTANCE.name(), SensorType.LATITUDE.name()}; // selectionArgs,

        Cursor cursor = summariesDb.query(WorkoutSummaries.TABLE_EXTREMUM_VALUES,
                null, // columns
                whereClause,
                whereArgs,
                null, null, null); // groupBy, having, orderBy)
        if (cursor.getCount() == 0) { // no value yet
            summariesDb.insert(WorkoutSummaries.TABLE_EXTREMUM_VALUES, null, values);
        } else {
            summariesDb.update(WorkoutSummaries.TABLE_EXTREMUM_VALUES, values, whereClause, whereArgs);
        }

        // now, the longitude
        // values.put(WorkoutSummaries.WORKOUT_ID,    workoutId);
        // values.put(WorkoutSummaries.EXTREMUM_TYPE, ExtremumType.MAX_LINE_DISTANCE.name());
        values.put(WorkoutSummaries.SENSOR_TYPE, SensorType.LONGITUDE.name());
        values.put(WorkoutSummaries.VALUE, latLngValue.latLng.longitude);

        // whereClause = WorkoutSummaries.WORKOUT_ID + "=? AND " + WorkoutSummaries.EXTREMUM_TYPE + "=? AND " + WorkoutSummaries.SENSOR_TYPE + "=?"; // selection,
        whereArgs = new String[]{Long.toString(workoutId), ExtremumType.MAX_LINE_DISTANCE.name(), SensorType.LONGITUDE.name()}; // selectionArgs,

        cursor = summariesDb.query(WorkoutSummaries.TABLE_EXTREMUM_VALUES,
                null, // columns
                whereClause,
                whereArgs,
                null, null, null); // groupBy, having, orderBy)
        if (cursor.getCount() == 0) { // no value yet
            summariesDb.insert(WorkoutSummaries.TABLE_EXTREMUM_VALUES, null, values);
        } else {
            summariesDb.update(WorkoutSummaries.TABLE_EXTREMUM_VALUES, values, whereClause, whereArgs);
        }
    }

    protected void onPreExecute() {
        mMessageTextView.setText(R.string.initializing);
    }

    @Override
    protected void onPostExecute(final Boolean success) {
        if (DEBUG) Log.d(TAG, "onPostExecute");

        mContext.sendBroadcast(new Intent(FINISHED_CALCULATING_EXTREMUM_VALUES));
    }

    @Override
    public void onProgressUpdate(String... args) {
        mMessageTextView.setText(args[0]);
    }

    @Override
    protected Boolean doInBackground(Long... args) {
        long workoutId = args[0];

        if (DEBUG) Log.d(TAG, "calculating extremum values for workout " + workoutId);

        String baseFileName = WorkoutSummariesDatabaseManager.getInstance().getBaseFileName(workoutId);

        // find max line distance
        calcAndSaveExtremumValues(workoutId,
                baseFileName,
                Arrays.asList(SensorType.LINE_DISTANCE_m),
                Arrays.asList(ExtremumType.MAX, ExtremumType.END));

        // start and end location
        calcAndSaveExtremumValues(workoutId,
                baseFileName,
                Arrays.asList(SensorType.LATITUDE, SensorType.LONGITUDE),
                Arrays.asList(ExtremumType.START, ExtremumType.END));

        calcAndSaveMaxLineDistancePosition(workoutId);

        calcFancyName(workoutId);

        guessCommuteAndTrainer(workoutId);

        ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // calc min, mean, and max values
        ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        // first, we need the accumulated sensors of this workout
        Set<SensorType> accumulatedSensorTypes = WorkoutSummariesDatabaseManager.getInstance().getAccumulatedSensorTypes(workoutId);

        // if there are no sensors stored (due to upgrading from DB version 3 to 4, we use all important sensors
        if (accumulatedSensorTypes.isEmpty()) {
            accumulatedSensorTypes.addAll(IMPORTANT_SENSOR_TYPES);
        } else {
            // otherwise, we use the intersection of the available ones and the important ones
            accumulatedSensorTypes.retainAll(IMPORTANT_SENSOR_TYPES);
        }

        // all interesting available sensors
        calcAndSaveExtremumValues(workoutId,
                baseFileName,
                accumulatedSensorTypes,  // the intersection of the available sensors and the ones we want to have the values
                Arrays.asList(ExtremumType.MIN, ExtremumType.AVG, ExtremumType.MAX));


        // finally, store that the extremum values are calculated
        ContentValues values = new ContentValues();
        values.put(WorkoutSummaries.EXTREMUM_VALUES_CALCULATED, 1);

        if (DEBUG) Log.i(TAG, "updating WorkoutSummaries for workoutId=" + workoutId);
        SQLiteDatabase db = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();
        db.update(WorkoutSummaries.TABLE,
                values,
                WorkoutSummaries.C_ID + "=?",
                new String[]{Long.toString(workoutId)});
        WorkoutSummariesDatabaseManager.getInstance().closeDatabase(); // db.close();

        return true;
    }

    protected void calcFancyName(long workoutId) {
        if (DEBUG) Log.i(TAG, "calcFancyName");
        publishProgress(mContext.getString(R.string.calc_workout_name));


        MyLocation startLocation = null;
        Double startLat = WorkoutSummariesDatabaseManager.getExtremumValue(workoutId, SensorType.LATITUDE, ExtremumType.START);
        Double startLon = WorkoutSummariesDatabaseManager.getExtremumValue(workoutId, SensorType.LONGITUDE, ExtremumType.START);
        if (startLat != null && startLon != null) {
            startLocation = KnownLocationsDatabaseManager.getMyLocation(new LatLng(startLat, startLon));
        }

        MyLocation maxLineLocation = null;
        Double maxLineLat = WorkoutSummariesDatabaseManager.getExtremumValue(workoutId, SensorType.LATITUDE, ExtremumType.MAX_LINE_DISTANCE);
        Double maxLineLon = WorkoutSummariesDatabaseManager.getExtremumValue(workoutId, SensorType.LONGITUDE, ExtremumType.MAX_LINE_DISTANCE);
        if (maxLineLat != null && maxLineLon != null) {
            maxLineLocation = KnownLocationsDatabaseManager.getMyLocation(new LatLng(maxLineLat, maxLineLon));
        }

        MyLocation endLocation = null;
        Double endLat = WorkoutSummariesDatabaseManager.getExtremumValue(workoutId, SensorType.LATITUDE, ExtremumType.END);
        Double endLon = WorkoutSummariesDatabaseManager.getExtremumValue(workoutId, SensorType.LONGITUDE, ExtremumType.END);
        if (endLat != null && endLon != null) {
            endLocation = KnownLocationsDatabaseManager.getMyLocation(new LatLng(endLat, endLon));
        }

        Long sportTypeId = WorkoutSummariesDatabaseManager.getLong(workoutId, WorkoutSummaries.SPORT_ID);
        if (sportTypeId == null) {
            sportTypeId = SportTypeDatabaseManager.getDefaultSportTypeId();
        }
        String fancyName = WorkoutSummariesDatabaseManager.getFancyName(sportTypeId, startLocation, maxLineLocation, endLocation);

        if (fancyName != null) {
            Intent intent = new Intent(FINISHED_CALCULATING_FANCY_NAME);
            intent.putExtra(FANCY_NAME, fancyName);
            mContext.sendBroadcast(intent);

            ContentValues contentValues = new ContentValues();
            contentValues.put(WorkoutSummaries.WORKOUT_NAME, fancyName);

            SQLiteDatabase db = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();
            db.update(WorkoutSummaries.TABLE, contentValues, WorkoutSummaries.C_ID + " = ?", new String[]{Long.toString(workoutId)});
        }
    }

    protected void guessCommuteAndTrainer(long workoutId) {
        if (DEBUG) Log.i(TAG, "guessCommuteAndTrainer");
        publishProgress(mContext.getString(R.string.guess_commute_and_trainer));

        // get max away points and guess commute and trainer
        boolean commute = false, trainer = false;
        Double distance = WorkoutSummariesDatabaseManager.getInstance().getDouble(workoutId, WorkoutSummaries.DISTANCE_TOTAL_m);
        Double maxLineDistance = WorkoutSummariesDatabaseManager.getInstance().getExtremumValue(workoutId, SensorType.LINE_DISTANCE_m, ExtremumType.MAX);
        Double endLineDistance = WorkoutSummariesDatabaseManager.getInstance().getExtremumValue(workoutId, SensorType.LINE_DISTANCE_m, ExtremumType.END);
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
            SQLiteDatabase db = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();
            db.update(WorkoutSummaries.TABLE,
                    values,
                    WorkoutSummaries.C_ID + "=?",
                    new String[]{Long.toString(workoutId)});
            WorkoutSummariesDatabaseManager.getInstance().closeDatabase(); // db.close();
        }

        mContext.sendBroadcast(new Intent(FINISHED_GUESSING_COMMUTE_AND_TRAINER));
    }

    protected void calcAndSaveExtremumValues(long workoutId, String baseFileName, Iterable<SensorType> sensorTypeList, Iterable<ExtremumType> extremumTypeList) {
        if (DEBUG) Log.i(TAG, "calcAndSaveExtremumValues(" + workoutId + "...)");

        // WorkoutSamplesDbHelper workoutSamplesDbHelper = new WorkoutSamplesDbHelper(mContext);
        SQLiteDatabase summariesDb = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();

        ContentValues values = new ContentValues();

        for (SensorType sensorType : sensorTypeList) {
            for (ExtremumType extremumType : extremumTypeList) {
                publishProgress(mContext.getString(R.string.calculating_extremum_value_for, extremumType.name(), mContext.getString(sensorType.getShortNameId())));

                Double value = WorkoutSamplesDatabaseManager.getInstance().calcExtremumValue(baseFileName, extremumType, sensorType);
                if (value != null) {
                    if (DEBUG)
                        Log.i(TAG, "saving " + extremumType.name() + " of " + sensorType.name() + ": " + value);
                    // save the value in the corresponding database
                    values.put(WorkoutSummaries.WORKOUT_ID, workoutId);
                    values.put(WorkoutSummaries.EXTREMUM_TYPE, extremumType.name());
                    values.put(WorkoutSummaries.SENSOR_TYPE, sensorType.name());
                    values.put(WorkoutSummaries.VALUE, value);

                    String whereClause = WorkoutSummaries.WORKOUT_ID + "=? AND " + WorkoutSummaries.EXTREMUM_TYPE + "=? AND " + WorkoutSummaries.SENSOR_TYPE + "=?"; // selection,
                    String[] whereArgs = new String[]{Long.toString(workoutId), extremumType.name(), sensorType.name()}; // selectionArgs,

                    Cursor cursor = summariesDb.query(WorkoutSummaries.TABLE_EXTREMUM_VALUES,
                            null, // columns
                            whereClause,
                            whereArgs,
                            null, null, null); // groupBy, having, orderBy)
                    if (cursor.getCount() == 0) { // no value yet
                        summariesDb.insert(WorkoutSummaries.TABLE_EXTREMUM_VALUES, null, values);
                    } else {
                        summariesDb.update(WorkoutSummaries.TABLE_EXTREMUM_VALUES, values, whereClause, whereArgs);
                    }

                    if (!summariesDb.isOpen()) {
                        Log.d(TAG, "WTF: database is not open! -----------------------");
                    }
                } else {
                    if (DEBUG)
                        Log.i(TAG, "did not save " + extremumType.name() + " of " + sensorType.name() + " because its value is null");

                }
            }

            Intent intent = new Intent(FINISHED_CALCULATING_EXTREMUM_VALUE);
            intent.putExtra(SENSOR_TYPE, sensorType.name());
            mContext.sendBroadcast(intent);
        }

        WorkoutSummariesDatabaseManager.getInstance().closeDatabase(); // summariesDb.close();
    }
}
