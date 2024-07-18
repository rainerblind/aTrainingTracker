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

package com.atrainingtracker.banalservice.database;

import static android.provider.MediaStore.Images.Media.getBitmap;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.core.content.res.ResourcesCompat;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.trainingtracker.TrainingApplication;

import java.util.LinkedList;
import java.util.List;

public class SportTypeDatabaseManager {

    private static final String TAG = SportTypeDatabaseManager.class.getName();
    private static final boolean DEBUG = BANALService.DEBUG && false;
    private static SportTypeDatabaseManager cInstance;
    private static SportTypeDbHelper cSportTypeDbHelper;
    private int mOpenCounter;
    private SQLiteDatabase mDatabase;

    public static synchronized void initializeInstance(SportTypeDbHelper sportTypeDbHelper) {
        if (cInstance == null) {
            cInstance = new SportTypeDatabaseManager();
            cSportTypeDbHelper = sportTypeDbHelper;
        }
    }

    public static synchronized SportTypeDatabaseManager getInstance() {
        if (cInstance == null) {
            throw new IllegalStateException(SportTypeDatabaseManager.class.getSimpleName() +
                    " is not initialized, call initializeInstance(..) method first.");
        }

        return cInstance;
    }

    public static final long getDefaultSportTypeId() {
        return 1;
    }

    public static List<Long> getSportTypesIdList() {
        if (DEBUG) Log.i(TAG, "getSportTypesIdList");

        LinkedList<Long> result = new LinkedList<>();

        SQLiteDatabase db = getInstance().getOpenDatabase();
        Cursor cursor = db.query(SportType.TABLE,
                null,
                null, null,
                null, null, null);
        while (cursor.moveToNext()) {
            result.add(cursor.getLong(cursor.getColumnIndex(SportType.C_ID)));
        }
        cursor.close();
        getInstance().closeDatabase();

        return result;
    }

    public static int getBSportTypeIconId(long id) {
        if (DEBUG) Log.i(TAG, "getBsportTypeIconId, id=" + id);

        BSportType bSportType = getBSportType(id);

        switch (bSportType) {
            case RUN:
                return R.drawable.bsport_run;

            case BIKE:
                return R.drawable.bsport_bike;

            default:
                return R.drawable.bsport_other;
        }
    }

    public static Drawable getBSportTypeIcon(Context context, long id, double scale) {
        if (DEBUG) Log.i(TAG, "getBsportTypeIcon, id=" + id);

        int drawableId = getBSportTypeIconId(id);

        Bitmap icon = ((BitmapDrawable) ResourcesCompat.getDrawable(context.getResources(), drawableId, null)).getBitmap();
        Bitmap iconScaled = Bitmap.createScaledBitmap(icon, (int) (icon.getWidth() * scale), (int) (icon.getHeight() * scale), false);

        return new BitmapDrawable(context.getResources(), iconScaled);
    }

    public static List<String> getSportTypesUiNameList() {
        if (DEBUG) Log.i(TAG, "getSportTypesUiNameList");

        LinkedList<String> result = new LinkedList<>();

        SQLiteDatabase db = getInstance().getOpenDatabase();
        Cursor cursor = db.query(SportType.TABLE,
                null,
                null, null,
                null, null, null);
        while (cursor.moveToNext()) {
            result.add(cursor.getString(cursor.getColumnIndex(SportType.UI_NAME)));
        }
        cursor.close();
        getInstance().closeDatabase();

        return result;
    }

    public static List<Long> getSportTypesIdList(BSportType bSportType, double avgSpd) {
        if (DEBUG)
            Log.i(TAG, "getSportTypesIdList, bSportType=" + bSportType.name() + ", avgSpd=" + avgSpd);

        List<Long> result = new LinkedList<>();

        SQLiteDatabase db = getInstance().getOpenDatabase();
        Cursor cursor = db.query(SportType.TABLE,
                null,
                SportType.BASE_SPORT_TYPE + "=? AND " + SportType.MIN_AVG_SPEED + "<=? AND " + SportType.MAX_AVG_SPEED + ">?",
                new String[]{bSportType.name(), Double.toString(avgSpd), Double.toString(avgSpd)},
                null, null, null);
        if (cursor.getCount() == 0   // nothing was found for the UNKNOWN sport type
                && bSportType == BSportType.UNKNOWN) {
            cursor = db.query(SportType.TABLE,   // => query again, ignoring the basic sport type
                    null,
                    SportType.MIN_AVG_SPEED + "<=? AND " + SportType.MAX_AVG_SPEED + ">?",
                    new String[]{Double.toString(avgSpd), Double.toString(avgSpd)},
                    null, null, null);
        }

        if (cursor.getCount() != 0) {
            while (cursor.moveToNext()) {
                if (DEBUG)
                    Log.i(TAG, "adding " + cursor.getString(cursor.getColumnIndex(SportType.UI_NAME)));
                result.add(cursor.getLong(cursor.getColumnIndex(SportType.C_ID)));
            }
        } else {
            result = getSportTypesIdList(bSportType);
        }

        cursor.close();
        getInstance().closeDatabase();

        return result;
    }

    public static List<String> getSportTypesUiNameList(BSportType bSportType, double avgSpd) {
        if (DEBUG)
            Log.i(TAG, "getSportTypesUiNameList, bSportType=" + bSportType.name() + ", avgSpd=" + avgSpd);

        List<String> result = new LinkedList<>();

        SQLiteDatabase db = getInstance().getOpenDatabase();
        Cursor cursor = db.query(SportType.TABLE,
                null,
                SportType.BASE_SPORT_TYPE + "=? AND " + SportType.MIN_AVG_SPEED + "<=? AND " + SportType.MAX_AVG_SPEED + ">?",
                new String[]{bSportType.name(), Double.toString(avgSpd), Double.toString(avgSpd)},
                null, null, null);
        if (cursor.getCount() == 0
                && bSportType == BSportType.UNKNOWN) {  // nothing was found for the UNKNOWN sport type
            // => query again, ignoring the basic sport type
            cursor = db.query(SportType.TABLE,
                    null,
                    SportType.MIN_AVG_SPEED + "<=? AND " + SportType.MAX_AVG_SPEED + ">?",
                    new String[]{Double.toString(avgSpd), Double.toString(avgSpd)},
                    null, null, null);
        }

        if (cursor.getCount() != 0) {
            while (cursor.moveToNext()) {
                result.add(cursor.getString(cursor.getColumnIndex(SportType.UI_NAME)));
            }
        } else {
            result = getSportTypesUiNameList(bSportType);
        }

        cursor.close();
        getInstance().closeDatabase();

        return result;
    }

    public static List<Long> getSportTypesIdList(BSportType bSportType) {
        if (DEBUG) Log.i(TAG, "getSportTypesIdList, bSportType=" + bSportType.name());

        List<Long> result = new LinkedList<>();

        SQLiteDatabase db = getInstance().getOpenDatabase();
        Cursor cursor = db.query(SportType.TABLE,
                null,
                SportType.BASE_SPORT_TYPE + "=?",
                new String[]{bSportType.name()},
                null, null, null);

        if (cursor.getCount() != 0) {
            while (cursor.moveToNext()) {
                if (DEBUG)
                    Log.i(TAG, "adding " + cursor.getString(cursor.getColumnIndex(SportType.UI_NAME)));
                result.add(cursor.getLong(cursor.getColumnIndex(SportType.C_ID)));
            }
        } else {
            result.add(getSportTypeId(bSportType));
        }

        cursor.close();
        getInstance().closeDatabase();

        return result;
    }

    public static List<String> getSportTypesUiNameList(BSportType bSportType) {
        if (DEBUG) Log.i(TAG, "getSportTypesUiNameList, bSportType=" + bSportType.name());

        LinkedList<String> result = new LinkedList<>();

        SQLiteDatabase db = getInstance().getOpenDatabase();
        Cursor cursor = db.query(SportType.TABLE,
                null,
                SportType.BASE_SPORT_TYPE + "=?",
                new String[]{bSportType.name()},
                null, null, null);

        if (cursor.getCount() != 0) {
            while (cursor.moveToNext()) {
                result.add(cursor.getString(cursor.getColumnIndex(SportType.UI_NAME)));
            }
        } else {
            result.add(getUIName(getSportTypeId(bSportType)));
        }

        cursor.close();
        getInstance().closeDatabase();

        return result;
    }

    public static BSportType getBSportType(long id) {
        if (DEBUG) Log.i(TAG, "getBsportType: id=" + id);

        BSportType result = BSportType.UNKNOWN;
        String bSportType = getString(id, SportType.BASE_SPORT_TYPE);
        try {
            result = BSportType.valueOf(bSportType);
        } catch (Exception e) {
        }

        return result;
    }

    public static String getUIName(long id) {
        return getString(id, SportType.UI_NAME);
    }

    public static String getGcName(long id) {
        return getString(id, SportType.GOLDEN_CHEETAH_NAME);
    }

    public static String getStravaName(long id) {
        return getString(id, SportType.STRAVA_NAME);
    }

    // public String toString() { TODO  }
    // public int    getUIId()              { return UIId;              }

    public static String getTcxName(long id) {
        return getString(id, SportType.TCX_NAME);
    }

    public static String getRunkeeperName(long id) {
        return getString(id, SportType.RUNKEEPER_NAME);
    }

    public static String getTrainingPeaksName(long id) {
        return getString(id, SportType.TRAINING_PEAKS_NAME);
    }

    public static double getMinSpeed(long id) {
        return getDouble(id, SportType.MIN_AVG_SPEED);
    }

    public static double getMaxSpeed(long id) {
        return getDouble(id, SportType.MAX_AVG_SPEED);
    }

    public static long getSportTypeIdFromUIName(String UIName) {
        long result = -1;
        SQLiteDatabase db = getInstance().getOpenDatabase();
        Cursor cursor = db.query(SportType.TABLE,
                null,
                SportType.UI_NAME + "=?", new String[]{UIName},
                null, null, null);
        if (cursor.moveToNext()) {
            result = cursor.getLong(cursor.getColumnIndex(SportType.C_ID));
        }

        getInstance().closeDatabase();

        return result;
    }

    private static String getString(long id, String col) {
        String result = null;
        SQLiteDatabase db = getInstance().getOpenDatabase();
        Cursor cursor = db.query(SportType.TABLE,
                null,
                SportType.C_ID + "=?",
                new String[]{Long.toString(id)},
                null, null, null);
        if (cursor.moveToNext()) {
            result = cursor.getString(cursor.getColumnIndex(col));
        } else {  // try to find the corresponding row of the 'other' sport type
            cursor = db.query(SportType.TABLE,
                    null,
                    SportType.C_ID + "=?",
                    new String[]{Long.toString(1)},
                    null, null, null);
            if (cursor.moveToNext()) {
                result = cursor.getString(cursor.getColumnIndex(col));
            }
        }
        getInstance().closeDatabase();

        return result;
    }

    private static double getDouble(long id, String col) {
        double result = 0.0;
        SQLiteDatabase db = getInstance().getOpenDatabase();
        Cursor cursor = db.query(SportType.TABLE,
                null,
                SportType.C_ID + "=?",
                new String[]{Long.toString(id)},
                null, null, null);
        if (cursor.moveToNext()) {
            result = cursor.getDouble(cursor.getColumnIndex(col));
        } else {  // try to find the corresponding row of the 'other' sport type
            cursor = db.query(SportType.TABLE,
                    null,
                    SportType.C_ID + "=?",
                    new String[]{Long.toString(1)},
                    null, null, null);
            if (cursor.moveToNext()) {
                result = cursor.getDouble(cursor.getColumnIndex(col));
            }
        }
        getInstance().closeDatabase();

        return result;
    }

    public static List<String> getSportTypesList() {
        List<String> result = new LinkedList<>();

        SQLiteDatabase db = getInstance().getOpenDatabase();
        Cursor cursor = db.query(SportType.TABLE,
                null,
                null, null,
                null, null, null);
        while (cursor.moveToNext()) {
            result.add(cursor.getString(cursor.getColumnIndex(SportType.UI_NAME)));
        }

        getInstance().closeDatabase();

        return result;
    }

    public static boolean canDelete(long id) {
        return id < 0 || id > 3;
    }

    public static void delete(long id) {
        if (!canDelete(id)) {
            return;
        }

        SQLiteDatabase db = getInstance().getOpenDatabase();
        db.delete(SportType.TABLE,
                SportType.C_ID + "=?",
                new String[]{Long.toString(id)});
        getInstance().closeDatabase();
    }

    public static BSportType getBSportType(String ttSportTypeName) {
        TTSportType ttSportType = TTSportType.valueOf(ttSportTypeName);
        return ttSportType.getBSportType();
    }

    public static long getSportTypeId(BSportType bSportType) {
        switch (bSportType) {
            case UNKNOWN:
                return 1;
            case RUN:
                return 2;
            case BIKE:
                return 3;
            default:
                return 1;
        }
    }

    public static long getSportTypeIdFromTTSportTypeName(String ttSportTypeName) {
        TTSportType ttSportType = TTSportType.valueOf(ttSportTypeName);
        switch (ttSportType) {
            case OTHER:
                return 1;
            case RUN:
                return 2;
            case BIKE:
                return 3;
            case WALK:
                return 4;
            case MTB:
                return 5;
            default:
                return 1;
        }
    }

    public synchronized SQLiteDatabase getOpenDatabase() {
        mOpenCounter++;
        if (mOpenCounter == 1) {
            // Opening new database
            mDatabase = cSportTypeDbHelper.getWritableDatabase();
        }
        return mDatabase;
    }

    public synchronized void closeDatabase() {
        mOpenCounter--;
        if (mOpenCounter == 0) {
            // Closing database
            mDatabase.close();
        }
    }

    private enum TTSportType {
        // only for the UI we use the R.string, others are hard coded because they should never ever be translated
        //                         UI Id    gc       strava   tcx         Runkeeper           TrainingPeaks
        WALK(BSportType.UNKNOWN, R.string.sport_type_walk, "walk", "Walk", "Walking", "Walking", "Walk"),
        RUN(BSportType.RUN, R.string.sport_type_run, "run", "Run", "Running", "Running", "Run"),
        MTB(BSportType.BIKE, R.string.sport_type_MTB, "mtb", "Ride", "Biking", "Mountain Biking", "MTB"),
        BIKE(BSportType.BIKE, R.string.sport_type_bike, "bike", "Ride", "Biking", "Cycling", "Bike"),
        OTHER(BSportType.UNKNOWN, R.string.sport_type_other, "", "", "Other", "Other", "Other");

        // TODO: Runkeeper: Running, Cycling, Mountain Biking, Walking, Hiking, Downhill Skiing, Cross-Country Skiing, Snowboarding, Skating, Swimming, Wheelchair, Rowing, Elliptical, Other
        // TODO: TrainingPeaks: Bike, Run, Walk, Swim, Brick, Cross train, Race, Day Off, Mountain Bike, Strength, XC Ski, Rowing, Other

        private final BSportType bSportType;
        private final int UIId;
        private final String gcName;
        private final String stravaName;
        private final String tcxName;
        private final String runkeeperName;
        private final String trainingPeaksName;

        TTSportType(BSportType bSportType, int UIId, String gcName, String stravaName, String tcxName, String runkeeperName, String trainingPeaksName) {
            this.bSportType = bSportType;
            this.UIId = UIId;
            this.gcName = gcName;
            this.stravaName = stravaName;
            this.tcxName = tcxName;
            this.runkeeperName = runkeeperName;
            this.trainingPeaksName = trainingPeaksName;
        }

        @Override
        public String toString() {
            return TrainingApplication.getAppContext().getString(UIId);
        }

        public BSportType getBSportType() {
            return bSportType;
        }

        // public int    getUIId()              { return UIId;              }
        public String getGcName() {
            return gcName;
        }

        public String getStravaName() {
            return stravaName;
        }

        public String getTcxName() {
            return tcxName;
        }

        public String getRunkeeperName() {
            return runkeeperName;
        }

        public String getTrainingPeaksName() {
            return trainingPeaksName;
        }

    }

    // the columns of the table
    public static final class SportType {
        public static final String TABLE = "SportTypes";

        public static final String C_ID = BaseColumns._ID;
        public static final String UI_NAME = "UIName";
        public static final String BASE_SPORT_TYPE = "baseSportType";
        public static final String GOLDEN_CHEETAH_NAME = "gcName";
        public static final String TCX_NAME = "tcxName";
        public static final String STRAVA_NAME = "stravaName";
        public static final String RUNKEEPER_NAME = "runkeeperName";
        public static final String TRAINING_PEAKS_NAME = "trainingPeaksName";
        public static final String MIN_AVG_SPEED = "minSpeed";
        public static final String MAX_AVG_SPEED = "maxSpeed";

        public static final BSportType DEFAULT_BASE_SPORT_TYPE = BSportType.UNKNOWN;
        public static final String DEFAULT_GOLDEN_CHEETAH_NAME = "";
        public static final String DEFAULT_TCX_NAME = "Other";
        public static final String DEFAULT_STRAVA_NAME = "";
        public static final String DEFAULT_RUNKEEPER_NAME = "Other";
        public static final String DEFAULT_TRAINING_PEAKS_NAME = "Other";
        public static final double DEFAULT_MIN_AVG_SPEED = 0.0;
        public static final double DEFAULT_MAX_AVG_SPEED = 0.5;

        public static String getDefaultUiName(Context context) {
            return context.getString(R.string.sport_type_other);
        }
    }

    public static class SportTypeDbHelper extends SQLiteOpenHelper {
        public static final String DB_NAME = "SportType.db";
        public static final int DB_VERSION = 1;
        protected static final String CREATE_TABLE = "create table " + SportType.TABLE + " ("
                + SportType.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + SportType.UI_NAME + " text, "
                + SportType.BASE_SPORT_TYPE + " text, "
                + SportType.GOLDEN_CHEETAH_NAME + " text, "
                + SportType.TCX_NAME + " text, "
                + SportType.STRAVA_NAME + " text, "
                + SportType.RUNKEEPER_NAME + " text, "
                + SportType.TRAINING_PEAKS_NAME + " text, "
                + SportType.MIN_AVG_SPEED + " real, "
                + SportType.MAX_AVG_SPEED + " real)";
        private static final String TAG = "SportTypeDbHelper";
        private static final boolean DEBUG = false;
        Context mContext;

        // Constructor
        public SportTypeDbHelper(Context context) {

            super(context, DB_NAME, null, DB_VERSION);
            mContext = context;
        }

        // TTSportType:
        //                            UI Id                      gc       strava   tcx         Runkeeper           TrainingPeaks
        // WALK( BSportType.UNKNOWN,  R.string.sport_type_walk,  "walk",  "Walk",  "Walking",  "Walking",          "Walk"),
        // RUN(  BSportType.RUN,      R.string.sport_type_run,   "run",   "Run",   "Running",  "Running",          "Run"),
        // MTB(  BSportType.BIKE,     R.string.sport_type_MTB,   "mtb",   "Ride",  "Biking",   "Mountain Biking",  "Mountain Bike"),
        // BIKE( BSportType.BIKE,     R.string.sport_type_bike,  "bike",  "Ride",  "Biking",   "Cycling",          "Bike"),
        // OTHER(BSportType.UNKNOWN,  R.string.sport_type_other, "",      "",      "Other",    "Other",            "Other");


        // Called only once, first time the DB is created
        @Override
        public void onCreate(SQLiteDatabase db) {

            db.execSQL(CREATE_TABLE);

            ContentValues cv = new ContentValues();

            // OTHER(BSportType.UNKNOWN,  R.string.sport_type_other, "",      "",      "Other",    "Other",            "Other");
            cv.clear();
            cv.put(SportType.UI_NAME, TTSportType.OTHER.toString());
            cv.put(SportType.BASE_SPORT_TYPE, TTSportType.OTHER.getBSportType().name());
            cv.put(SportType.GOLDEN_CHEETAH_NAME, TTSportType.OTHER.getGcName());
            cv.put(SportType.TCX_NAME, TTSportType.OTHER.getTcxName());
            cv.put(SportType.STRAVA_NAME, TTSportType.OTHER.getStravaName());
            cv.put(SportType.RUNKEEPER_NAME, TTSportType.OTHER.getRunkeeperName());
            cv.put(SportType.TRAINING_PEAKS_NAME, TTSportType.OTHER.getTrainingPeaksName());
            cv.put(SportType.MIN_AVG_SPEED, 0.0);
            cv.put(SportType.MAX_AVG_SPEED, TrainingApplication.getMinWalkSpeed_mps());
            long id = db.insert(SportType.TABLE, null, cv);
            if (DEBUG) Log.i(TAG, "other gets id=" + id);

            // RUN(  BSportType.RUN,      R.string.sport_type_run,   "run",   "Run",   "Running",  "Running",          "Run"),
            cv.clear();
            cv.put(SportType.UI_NAME, TTSportType.RUN.toString());
            cv.put(SportType.BASE_SPORT_TYPE, TTSportType.RUN.getBSportType().name());
            cv.put(SportType.GOLDEN_CHEETAH_NAME, TTSportType.RUN.getGcName());
            cv.put(SportType.TCX_NAME, TTSportType.RUN.getTcxName());
            cv.put(SportType.STRAVA_NAME, TTSportType.RUN.getStravaName());
            cv.put(SportType.RUNKEEPER_NAME, TTSportType.RUN.getRunkeeperName());
            cv.put(SportType.TRAINING_PEAKS_NAME, TTSportType.RUN.getTrainingPeaksName());
            cv.put(SportType.MIN_AVG_SPEED, TrainingApplication.getMaxWalkSpeed_mps());
            cv.put(SportType.MAX_AVG_SPEED, TrainingApplication.getMaxRunSpeed_mps());
            id = db.insert(SportType.TABLE, null, cv);
            if (DEBUG) Log.i(TAG, "run gets id=" + id);

            // BIKE( BSportType.BIKE,     R.string.sport_type_bike,  "bike",  "Ride",  "Biking",   "Cycling",          "Bike"),
            cv.clear();
            cv.put(SportType.UI_NAME, TTSportType.BIKE.toString());
            cv.put(SportType.BASE_SPORT_TYPE, TTSportType.BIKE.getBSportType().name());
            cv.put(SportType.GOLDEN_CHEETAH_NAME, TTSportType.BIKE.getGcName());
            cv.put(SportType.TCX_NAME, TTSportType.BIKE.getTcxName());
            cv.put(SportType.STRAVA_NAME, TTSportType.BIKE.getStravaName());
            cv.put(SportType.RUNKEEPER_NAME, TTSportType.BIKE.getRunkeeperName());
            cv.put(SportType.TRAINING_PEAKS_NAME, TTSportType.BIKE.getTrainingPeaksName());
            cv.put(SportType.MIN_AVG_SPEED, TrainingApplication.getMaxMTBSpeed_mps());
            cv.put(SportType.MAX_AVG_SPEED, TrainingApplication.getMaxBikeSpeed_mps());
            id = db.insert(SportType.TABLE, null, cv);
            if (DEBUG) Log.i(TAG, "bike gets id=" + id);

            // WALK( BSportType.UNKNOWN,  R.string.sport_type_walk,  "walk",  "Walk",  "Walking",  "Walking",          "Walk"),
            cv.clear();
            cv.put(SportType.UI_NAME, TTSportType.WALK.toString());
            cv.put(SportType.BASE_SPORT_TYPE, TTSportType.WALK.getBSportType().name());
            cv.put(SportType.GOLDEN_CHEETAH_NAME, TTSportType.WALK.getGcName());
            cv.put(SportType.TCX_NAME, TTSportType.WALK.getTcxName());
            cv.put(SportType.STRAVA_NAME, TTSportType.WALK.getStravaName());
            cv.put(SportType.RUNKEEPER_NAME, TTSportType.WALK.getRunkeeperName());
            cv.put(SportType.TRAINING_PEAKS_NAME, TTSportType.WALK.getTrainingPeaksName());
            cv.put(SportType.MIN_AVG_SPEED, TrainingApplication.getMinWalkSpeed_mps());
            cv.put(SportType.MAX_AVG_SPEED, TrainingApplication.getMaxWalkSpeed_mps());
            id = db.insert(SportType.TABLE, null, cv);
            if (DEBUG) Log.i(TAG, "walk gets id=" + id);


            // MTB(  BSportType.BIKE,     R.string.sport_type_MTB,   "mtb",   "Ride",  "Biking",   "Mountain Biking",  "Mountain Bike"),
            cv.clear();
            cv.put(SportType.UI_NAME, TTSportType.MTB.toString());
            cv.put(SportType.BASE_SPORT_TYPE, TTSportType.MTB.getBSportType().name());
            cv.put(SportType.GOLDEN_CHEETAH_NAME, TTSportType.MTB.getGcName());
            cv.put(SportType.TCX_NAME, TTSportType.MTB.getTcxName());
            cv.put(SportType.STRAVA_NAME, TTSportType.MTB.getStravaName());
            cv.put(SportType.RUNKEEPER_NAME, TTSportType.MTB.getRunkeeperName());
            cv.put(SportType.TRAINING_PEAKS_NAME, TTSportType.MTB.getTrainingPeaksName());
            cv.put(SportType.MIN_AVG_SPEED, TrainingApplication.getMaxRunSpeed_mps());
            cv.put(SportType.MAX_AVG_SPEED, TrainingApplication.getMaxMTBSpeed_mps());
            id = db.insert(SportType.TABLE, null, cv);
            if (DEBUG) Log.i(TAG, "MTB gets id=" + id);


            if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_TABLE);
        }

        //Called whenever newVersion != oldVersion
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // TODO: alter table instead of deleting!

            db.execSQL("drop table if exists " + SportType.TABLE);

            if (DEBUG) Log.d(TAG, "onUpgraded");
            onCreate(db);  // run onCreate to get new database
        }


    }

}
