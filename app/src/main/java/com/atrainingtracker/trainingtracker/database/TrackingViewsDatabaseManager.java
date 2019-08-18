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

package com.atrainingtracker.trainingtracker.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.ActivityType;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;
import com.atrainingtracker.banalservice.filters.FilterData;
import com.atrainingtracker.banalservice.filters.FilterType;
import com.atrainingtracker.banalservice.helpers.HavePressureSensor;
import com.atrainingtracker.trainingtracker.TrainingApplication;

import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;


public class TrackingViewsDatabaseManager {
    private static final String TAG = TrackingViewsDatabaseManager.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;
    private static TrackingViewsDatabaseManager cInstance;
    private static TrackingViewsDbHelper cTrackingViewsDbHelper;
    private int mOpenCounter;
    private SQLiteDatabase mDatabase;

    public static synchronized void initializeInstance(TrackingViewsDbHelper trackingViewsDbHelper) {
        if (cInstance == null) {
            cInstance = new TrackingViewsDatabaseManager();
            cTrackingViewsDbHelper = trackingViewsDbHelper;
        }
    }

    public static synchronized TrackingViewsDatabaseManager getInstance() {
        if (cInstance == null) {
            throw new IllegalStateException(TrackingViewsDatabaseManager.class.getSimpleName() +
                    " is not initialized, call initializeInstance(..) method first.");
        }

        return cInstance;
    }

    public static void updateNameOfView(long viewId, String name) {
        ContentValues values = new ContentValues();
        values.put(TrackingViewsDbHelper.NAME, name);

        TrackingViewsDatabaseManager databaseManager = TrackingViewsDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        db.update(TrackingViewsDbHelper.VIEWS_TABLE,
                values,
                TrackingViewsDbHelper.C_ID + "=?",
                new String[]{viewId + ""});

        databaseManager.closeDatabase();
    }

    public static void updateSensorTypeOfRow(long rowId, SensorType sensorType) {
        if (DEBUG) Log.i(TAG, "updateSensorTypeOfRow(" + rowId + ", " + sensorType.name() + ")");

        ContentValues values = new ContentValues();
        values.put(TrackingViewsDbHelper.SENSOR_TYPE, sensorType.name());

        TrackingViewsDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        db.update(TrackingViewsDbHelper.ROWS_TABLE,
                values,
                TrackingViewsDbHelper.ROW_ID + "=?",
                new String[]{rowId + ""});
        databaseManager.closeDatabase();
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // some high level methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static void updateTextSizeOfRow(long rowId, int textSize) {
        ContentValues values = new ContentValues();
        values.put(TrackingViewsDbHelper.TEXT_SIZE, textSize);


        TrackingViewsDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        db.update(TrackingViewsDbHelper.ROWS_TABLE,
                values,
                TrackingViewsDbHelper.ROW_ID + "=?",
                new String[]{rowId + ""});
        databaseManager.closeDatabase();
    }

    public static void updateSourceDeviceIdOfRow(long rowId, long deviceId) {
        ContentValues values = new ContentValues();
        values.put(TrackingViewsDbHelper.SOURCE_DEVICE_ID, deviceId);


        TrackingViewsDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        db.update(TrackingViewsDbHelper.ROWS_TABLE,
                values,
                TrackingViewsDbHelper.ROW_ID + "=?",
                new String[]{rowId + ""});
        databaseManager.closeDatabase();
    }

    public static void updateShowMap(long viewId, boolean showMap) {
        ContentValues values = new ContentValues();
        values.put(TrackingViewsDbHelper.SHOW_MAP, showMap ? 1 : 0);

        TrackingViewsDatabaseManager databaseManager = TrackingViewsDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        db.update(TrackingViewsDbHelper.VIEWS_TABLE,
                values,
                TrackingViewsDbHelper.C_ID + "=?",
                new String[]{viewId + ""});

        databaseManager.closeDatabase();
    }

    public static void updateShowLapButton(long viewId, boolean showLapButton) {
        ContentValues values = new ContentValues();
        values.put(TrackingViewsDbHelper.SHOW_LAP_BUTTON, showLapButton ? 1 : 0);

        TrackingViewsDatabaseManager databaseManager = TrackingViewsDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        db.update(TrackingViewsDbHelper.VIEWS_TABLE,
                values,
                TrackingViewsDbHelper.C_ID + "=?",
                new String[]{viewId + ""});

        databaseManager.closeDatabase();
    }

    public static void deleteRow(long rowId) {
        if (DEBUG) Log.i(TAG, "deleteRow(" + rowId + ")");

        TrackingViewsDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        db.delete(TrackingViewsDbHelper.ROWS_TABLE,
                TrackingViewsDbHelper.ROW_ID + "=?",
                new String[]{rowId + ""});
        databaseManager.closeDatabase();
    }

    public static String getName(long viewId) {

        String name = null;

        TrackingViewsDatabaseManager databaseManager = TrackingViewsDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        Cursor cursor = db.query(TrackingViewsDbHelper.VIEWS_TABLE,
                null,
                TrackingViewsDbHelper.C_ID + "=?",
                new String[]{viewId + ""},
                null,
                null,
                null);

        if (cursor.moveToFirst()) {
            name = cursor.getString(cursor.getColumnIndex(TrackingViewsDbHelper.NAME));
        }

        cursor.close();
        databaseManager.closeDatabase();

        return name;
    }

    public static ActivityType getActivityType(long viewId) {

        ActivityType activityType = ActivityType.getDefaultActivityType();

        TrackingViewsDatabaseManager databaseManager = TrackingViewsDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        Cursor cursor = db.query(TrackingViewsDbHelper.VIEWS_TABLE,
                null,
                TrackingViewsDbHelper.C_ID + "=?",
                new String[]{viewId + ""},
                null,
                null,
                null);

        if (cursor.moveToFirst()) {
            activityType = ActivityType.valueOf(cursor.getString(cursor.getColumnIndex(TrackingViewsDbHelper.ACTIVITY_TYPE)));
        }

        cursor.close();
        databaseManager.closeDatabase();

        return activityType;
    }

    public static int getLayoutNr(long viewId) {
        int layoutNr = -1;

        TrackingViewsDatabaseManager databaseManager = TrackingViewsDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        Cursor cursor = db.query(TrackingViewsDbHelper.VIEWS_TABLE,
                null,
                TrackingViewsDbHelper.C_ID + "=?",
                new String[]{viewId + ""},
                null,
                null,
                null);

        if (cursor.moveToFirst()) {
            layoutNr = cursor.getInt(cursor.getColumnIndex(TrackingViewsDbHelper.LAYOUT_NR));
        }

        cursor.close();
        databaseManager.closeDatabase();

        return layoutNr;
    }

    public static boolean showMap(long viewId) {
        boolean result = false;

        TrackingViewsDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        Cursor cursor = db.query(TrackingViewsDbHelper.VIEWS_TABLE,
                null,
                TrackingViewsDbHelper.C_ID + "=?",
                new String[]{viewId + ""},
                null,
                null,
                null);

        if (cursor.moveToFirst()) {
            result = (cursor.getInt(cursor.getColumnIndex(TrackingViewsDbHelper.SHOW_MAP)) > 0);
        }

        cursor.close();
        databaseManager.closeDatabase();

        return result;
    }

    public static boolean showLapButton(long viewId) {
        boolean result = false;

        TrackingViewsDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        Cursor cursor = db.query(TrackingViewsDbHelper.VIEWS_TABLE,
                null,
                TrackingViewsDbHelper.C_ID + "=?",
                new String[]{viewId + ""},
                null,
                null,
                null);

        if (cursor.moveToFirst()) {
            result = (cursor.getInt(cursor.getColumnIndex(TrackingViewsDbHelper.SHOW_LAP_BUTTON)) > 0);
        }

        cursor.close();
        databaseManager.closeDatabase();

        return result;
    }

    public static FilterInfo getFilterInfo(long rowId) {
        FilterInfo filterInfo = null;

        TrackingViewsDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        Cursor cursor = db.query(TrackingViewsDbHelper.ROWS_TABLE,
                null,
                TrackingViewsDbHelper.ROW_ID + "=?",
                new String[]{rowId + ""},
                null,
                null,
                null);

        if (cursor.moveToFirst()) {
            FilterType filterType = FilterType.valueOf(cursor.getString(cursor.getColumnIndex(TrackingViewsDbHelper.FILTER_TYPE)));
            double filterConstant = cursor.getDouble(cursor.getColumnIndex(TrackingViewsDbHelper.FILTER_CONSTANT));
            filterInfo = new FilterInfo(filterType, filterConstant);
        }

        cursor.close();
        databaseManager.closeDatabase();

        return filterInfo;
    }

    public static void ensureEntryForActivityTypeExists(Context context, ActivityType activityType) {
        TrackingViewsDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = db.query(TrackingViewsDbHelper.VIEWS_TABLE,
                null,
                TrackingViewsDbHelper.ACTIVITY_TYPE + "=?",
                new String[]{activityType.name()},
                null,
                null,
                null);
        if (cursor.getCount() == 0) {
            TrackingViewsDbHelper dbHelper = new TrackingViewsDbHelper(context);
            dbHelper.addDefaultActivity(db, activityType, 1);
        }

        cursor.close();
        databaseManager.closeDatabase();
    }

    public static LinkedList<String> getTitleList(ActivityType activityType) {
        LinkedList<String> titleList = new LinkedList<>();

        TrackingViewsDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = db.query(TrackingViewsDbHelper.VIEWS_TABLE,
                null,
                TrackingViewsDbHelper.ACTIVITY_TYPE + "=?",
                new String[]{activityType.name()},
                null,
                null,
                TrackingViewsDbHelper.LAYOUT_NR + " ASC");
        while (cursor.moveToNext()) {
            String name = cursor.getString(cursor.getColumnIndex(TrackingViewsDbHelper.NAME));
            if (DEBUG) Log.i(TAG, "adding " + name);
            titleList.add(name);
        }
        cursor.close();
        databaseManager.closeDatabase();

        return titleList;
    }

    @Deprecated // use new method ??? instead
    public static LinkedList<Long> getViewIdList(ActivityType activityType) {
        LinkedList<Long> viewIdList = new LinkedList<>();

        TrackingViewsDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = db.query(TrackingViewsDbHelper.VIEWS_TABLE,
                null,
                TrackingViewsDbHelper.ACTIVITY_TYPE + "=?",
                new String[]{activityType.name()},
                null,
                null,
                TrackingViewsDbHelper.LAYOUT_NR + " ASC");
        while (cursor.moveToNext()) {
            long viewId = cursor.getLong(cursor.getColumnIndex(TrackingViewsDbHelper.C_ID));
            viewIdList.add(viewId);
        }
        cursor.close();
        databaseManager.closeDatabase();

        return viewIdList;
    }

    public static ViewInfo addSensorToLayout(long viewId, SensorType sensorType, int textSize) {
        if (DEBUG)
            Log.i(TAG, "addSensorToLayout(" + viewId + ", " + sensorType.name() + ", " + textSize + ")");

        TrackingViewsDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        Cursor cursor = db.query(TrackingViewsDbHelper.ROWS_TABLE,
                new String[]{"MAX(" + TrackingViewsDbHelper.ROW_NR + ")"},  // columns,
                TrackingViewsDbHelper.VIEW_ID + "=?", // selection,
                new String[]{Long.toString(viewId)}, // selectionArgs,
                null, null, null); // groupBy, having, orderBy)
        cursor.moveToFirst();
        int maxRowNr = cursor.getInt(0);

        ContentValues values = new ContentValues();
        values.put(TrackingViewsDbHelper.VIEW_ID, viewId);
        values.put(TrackingViewsDbHelper.ROW_NR, maxRowNr + 1);
        values.put(TrackingViewsDbHelper.COL_NR, 1);
        values.put(TrackingViewsDbHelper.SENSOR_TYPE, sensorType.name());
        values.put(TrackingViewsDbHelper.TEXT_SIZE, textSize);
        values.put(TrackingViewsDbHelper.FILTER_TYPE, FilterType.INSTANTANEOUS.name());
        values.put(TrackingViewsDbHelper.FILTER_CONSTANT, 1);

        long rowId = db.insert(TrackingViewsDbHelper.ROWS_TABLE, null, values);

        databaseManager.closeDatabase();

        return new ViewInfo(viewId, rowId, maxRowNr + 1, 1, sensorType, textSize, 0, FilterType.INSTANTANEOUS, 1);
    }

    public static ViewInfo addSensorToRow(long viewId, int rowNr, SensorType sensorType, int textSize) {
        if (DEBUG)
            Log.i(TAG, "addSensorToRow(" + viewId + ", " + ", rowNr=" + rowNr + sensorType.name() + ", " + textSize + ")");

        TrackingViewsDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();


        Cursor cursor = db.query(TrackingViewsDbHelper.ROWS_TABLE,
                new String[]{"MAX(" + TrackingViewsDbHelper.COL_NR + ")"},  // columns,
                TrackingViewsDbHelper.VIEW_ID + "=? AND " + TrackingViewsDbHelper.ROW_NR + "=?", // selection,
                new String[]{Long.toString(viewId), Long.toString(rowNr)}, // selectionArgs,
                null, null, null); // groupBy, having, orderBy)
        cursor.moveToFirst();
        int maxColNr = cursor.getInt(0);

        ContentValues values = new ContentValues();
        values.put(TrackingViewsDbHelper.VIEW_ID, viewId);
        values.put(TrackingViewsDbHelper.ROW_NR, rowNr);
        values.put(TrackingViewsDbHelper.COL_NR, maxColNr + 1);
        values.put(TrackingViewsDbHelper.SENSOR_TYPE, sensorType.name());
        values.put(TrackingViewsDbHelper.TEXT_SIZE, textSize);
        values.put(TrackingViewsDbHelper.FILTER_TYPE, FilterType.INSTANTANEOUS.name());
        values.put(TrackingViewsDbHelper.FILTER_CONSTANT, 1);

        long rowId = db.insert(TrackingViewsDbHelper.ROWS_TABLE, null, values);

        databaseManager.closeDatabase();

        return new ViewInfo(viewId, rowId, rowNr, maxColNr + 1, sensorType, textSize, 0, FilterType.INSTANTANEOUS, 1);
    }

    public static ViewInfo addRowAfter(long viewId, int rowNr, SensorType sensorType, int textSize) {
        if (DEBUG)
            Log.i(TAG, "addRowAfter(" + viewId + ", " + sensorType.name() + ", " + textSize + ")");

        TrackingViewsDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        String sql = "UPDATE " + TrackingViewsDbHelper.ROWS_TABLE +
                " SET " + TrackingViewsDbHelper.ROW_NR + "=" + TrackingViewsDbHelper.ROW_NR + "+1" +
                " WHERE " + TrackingViewsDbHelper.ROW_NR + " > " + rowNr;

        Log.i(TAG, sql);

        db.execSQL(sql);

        ContentValues values = new ContentValues();
        values.put(TrackingViewsDbHelper.VIEW_ID, viewId);
        values.put(TrackingViewsDbHelper.ROW_NR, rowNr + 1);
        values.put(TrackingViewsDbHelper.COL_NR, 1);
        values.put(TrackingViewsDbHelper.SENSOR_TYPE, sensorType.name());
        values.put(TrackingViewsDbHelper.TEXT_SIZE, textSize);
        values.put(TrackingViewsDbHelper.FILTER_TYPE, FilterType.INSTANTANEOUS.name());
        values.put(TrackingViewsDbHelper.FILTER_CONSTANT, 1);

        long rowId = db.insert(TrackingViewsDbHelper.ROWS_TABLE, null, values);

        databaseManager.closeDatabase();

        return new ViewInfo(viewId, rowId, rowNr, 1, sensorType, textSize, 0, FilterType.INSTANTANEOUS, 1);
    }

    public static void removeSourceDevice(long sourceDeviceId) {
        SQLiteDatabase db = getInstance().getOpenDatabase();

        ContentValues contentValues = new ContentValues();
        contentValues.put(TrackingViewsDbHelper.SOURCE_DEVICE_ID, 0);

        db.update(TrackingViewsDbHelper.ROWS_TABLE,
                contentValues,
                TrackingViewsDbHelper.SOURCE_DEVICE_ID + "=?",
                new String[]{sourceDeviceId + ""});

        getInstance().closeDatabase();
    }

    public static void deleteView(long viewId) {
        TrackingViewsDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        int layoutNr = getLayoutNr(viewId);

        // delete from both tables
        db.delete(TrackingViewsDbHelper.VIEWS_TABLE, TrackingViewsDbHelper.C_ID + "=?", new String[]{viewId + ""});
        db.delete(TrackingViewsDbHelper.ROWS_TABLE, TrackingViewsDbHelper.VIEW_ID + "=?", new String[]{viewId + ""});

        // reduce layoutNr of all views with a larger layoutNr
        String execsql = "UPDATE " + TrackingViewsDbHelper.VIEWS_TABLE
                + " set " + TrackingViewsDbHelper.LAYOUT_NR
                + " = " + TrackingViewsDbHelper.LAYOUT_NR + " - 1 "
                + " where " + TrackingViewsDbHelper.LAYOUT_NR + " > " + layoutNr;
        if (DEBUG) Log.i(TAG, "DeleteView viewId=" + viewId + "code: " + execsql);
        db.execSQL(execsql);

        databaseManager.closeDatabase();
    }

    public static void addEmptyView(long viewId, boolean addAfterLayout) {
        int layoutNr = getLayoutNr(viewId);
        ActivityType activityType = getActivityType(viewId);
        int newLayoutNr = layoutNr;
        if (addAfterLayout) {
            newLayoutNr = layoutNr + 1;
        }
        if (DEBUG)
            Log.i(TAG, "addEmptyView viewId=" + viewId + ", layoutNr=" + layoutNr + ", addAfterLayout=" + addAfterLayout + ", newLayoutNr=" + newLayoutNr);

        TrackingViewsDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        // first, increment the layoutNr of all views with a larger layoutNr
        String sqlCommand = "UPDATE " + TrackingViewsDbHelper.VIEWS_TABLE
                + " set " + TrackingViewsDbHelper.LAYOUT_NR
                + " = " + TrackingViewsDbHelper.LAYOUT_NR + " + 1 "
                + " where " + TrackingViewsDbHelper.LAYOUT_NR + " > " + (newLayoutNr - 1);
        if (DEBUG) Log.i(TAG, "execSQL=" + sqlCommand);
        db.execSQL(sqlCommand);

        ContentValues values = new ContentValues();
        values.put(TrackingViewsDbHelper.ACTIVITY_TYPE, activityType.name());
        values.put(TrackingViewsDbHelper.NAME, newLayoutNr);
        values.put(TrackingViewsDbHelper.LAYOUT_NR, newLayoutNr);
        values.put(TrackingViewsDbHelper.SHOW_LAP_BUTTON, 1);

        db.insert(TrackingViewsDbHelper.VIEWS_TABLE, null, values);

        databaseManager.closeDatabase();
    }

    public static long addDefaultView(Context context, long viewId, ActivityType activityType, boolean addAfterLayout) {
        long newViewId = -1;

        int layoutNr = getLayoutNr(viewId);
        int newLayoutNr = layoutNr;
        if (addAfterLayout) {
            newLayoutNr = layoutNr + 1;
        }
        if (DEBUG)
            Log.i(TAG, "addDefaultView viewId=" + viewId + ", layoutNr=" + layoutNr + ", addAfterLayout=" + addAfterLayout + ", newLayoutNr=" + newLayoutNr);

        TrackingViewsDatabaseManager databaseManager = getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();

        // first, increment the layoutNr of all views with a larger layoutNr
        String sqlCommand = "UPDATE " + TrackingViewsDbHelper.VIEWS_TABLE
                + " set " + TrackingViewsDbHelper.LAYOUT_NR
                + " = " + TrackingViewsDbHelper.LAYOUT_NR + " + 1 "
                + " where " + TrackingViewsDbHelper.LAYOUT_NR + " > " + (newLayoutNr - 1);
        if (DEBUG) Log.i(TAG, "execSQL=" + sqlCommand);
        db.execSQL(sqlCommand);
        if (DEBUG) Log.i(TAG, "executed SQL code");

        // TrackingViewsDbHelper dbHelper = new TrackingViewsDbHelper(context);
        TrackingViewsDbHelper dbHelper = new TrackingViewsDbHelper(context);  // why are the log messages herein not shown???
        newViewId = dbHelper.addDefaultActivity(db, activityType, newLayoutNr);
        databaseManager.closeDatabase();
        if (DEBUG) Log.i(TAG, "finished adding new view");

        return newViewId;
    }

    //                    rowNr            colNr
    public static TreeMap<Integer, TreeMap<Integer, ViewInfo>> getViewInfoMap(long viewId) {
        TreeMap<Integer, TreeMap<Integer, ViewInfo>> result = new TreeMap<>();

        SQLiteDatabase db = getInstance().getOpenDatabase();
        Cursor cursor = db.query(TrackingViewsDbHelper.ROWS_TABLE,
                null,
                TrackingViewsDbHelper.VIEW_ID + "=?",
                new String[]{Long.toString(viewId)},
                null, null, null);
        while (cursor.moveToNext()) {
            int rowId = cursor.getInt(cursor.getColumnIndex(TrackingViewsDbHelper.ROW_ID));
            int rowNr = cursor.getInt(cursor.getColumnIndex(TrackingViewsDbHelper.ROW_NR));
            int colNr = cursor.getInt(cursor.getColumnIndex(TrackingViewsDbHelper.COL_NR));
            SensorType sensorType = SensorType.valueOf(cursor.getString(cursor.getColumnIndex(TrackingViewsDbHelper.SENSOR_TYPE)));
            int textSize = cursor.getInt(cursor.getColumnIndex(TrackingViewsDbHelper.TEXT_SIZE));
            int sourceDeviceId = cursor.getInt(cursor.getColumnIndex(TrackingViewsDbHelper.SOURCE_DEVICE_ID));
            FilterType filterType = FilterType.valueOf(cursor.getString(cursor.getColumnIndex(TrackingViewsDbHelper.FILTER_TYPE)));
            double filterConstant = cursor.getDouble(cursor.getColumnIndex(TrackingViewsDbHelper.FILTER_CONSTANT));

            if (!result.containsKey(rowNr)) {
                result.put(rowNr, new TreeMap<Integer, ViewInfo>());
            }

            result.get(rowNr).put(colNr, new ViewInfo(viewId, rowId, rowNr, colNr, sensorType, textSize, sourceDeviceId, filterType, filterConstant));
        }

        getInstance().closeDatabase();

        return result;
    }

    public static List<FilterData> getAllFilterData() {
        LinkedList<FilterData> result = new LinkedList<>();

        SQLiteDatabase db = getInstance().getOpenDatabase();
        Cursor cursor = db.query(TrackingViewsDbHelper.ROWS_TABLE,  // get cursor with all rows.  This is necessary, because the view and sport type and ... might change
                null,
                null,
                null,
                null, null, null);
        while (cursor.moveToNext()) {
            SensorType sensorType = SensorType.valueOf(cursor.getString(cursor.getColumnIndex(TrackingViewsDbHelper.SENSOR_TYPE)));
            int sourceDeviceId = cursor.getInt(cursor.getColumnIndex(TrackingViewsDbHelper.SOURCE_DEVICE_ID));
            String deviceName = DevicesDatabaseManager.getDeviceName(sourceDeviceId);
            FilterType filterType = FilterType.valueOf(cursor.getString(cursor.getColumnIndex(TrackingViewsDbHelper.FILTER_TYPE)));
            double filterConstant = cursor.getDouble(cursor.getColumnIndex(TrackingViewsDbHelper.FILTER_CONSTANT));

            result.add(new FilterData(deviceName, sensorType, filterType, filterConstant));
        }

        getInstance().closeDatabase();

        return result;
    }

    public synchronized SQLiteDatabase getOpenDatabase() {
        mOpenCounter++;
        if (mOpenCounter == 1) {
            // Opening new database
            mDatabase = cTrackingViewsDbHelper.getWritableDatabase();
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

    public static class FilterInfo {
        public FilterType filterType;
        public double filterConstant;

        FilterInfo(FilterType filterType, double filterConstant) {
            this.filterType = filterType;
            this.filterConstant = filterConstant;
        }
    }

    public static class ViewInfo {
        public long viewId;
        public long rowId;
        public int rowNr;
        public int colNr;
        public SensorType sensorType;
        public int textSize;
        public long sourceDeviceId;
        public FilterType filterType;
        public double filterConstant;

        public ViewInfo(long viewId, long rowId, int rowNr, int colNr, SensorType sensorType, int textSize, long sourceDeviceId, FilterType filterType, double filterConstant) {
            this.viewId = viewId;
            this.rowId = rowId;
            this.rowNr = rowNr;
            this.colNr = colNr;
            this.sensorType = sensorType;
            this.textSize = textSize;
            this.sourceDeviceId = sourceDeviceId;
            this.filterType = filterType;
            this.filterConstant = filterConstant;
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class TrackingViewsDbHelper extends SQLiteOpenHelper {
        public static final String DB_NAME = "FlexibleGenericView.db";
        // private static final boolean DEBUG = true;
        // public static final int DB_VERSION = 2;    // upgraded to version 2 at 16.2.2016
        // public static final int DB_VERSION = 3;       // upgraded to version 3 at 2.3.2016
        // public static final int DB_VERSION = 4;       // upgraded to version 4 at 3.1.2017
        // public static final int DB_VERSION = 5;       // upgraded to version 5 at 14.03.2018
        public static final int DB_VERSION = 6;        // upgraded to version 6 at 17.04.2018
        public static final String VIEWS_TABLE = "ViewsTable";
        public static final String ROWS_TABLE = "LayoutRowsTable";
        public static final String C_ID = BaseColumns._ID;
        public static final String VIEW_ID = "ViewId";
        public static final String ROW_ID = "RowId";
        // new in version 4
        public static final String ROW_NR = "RowNr";
        public static final String COL_NR = "ColNr";
        // public static final String SPORT_TYPE    = "ActivityType";
        // public static final String SENSOR_BITS   = "SensorBits";
        public static final String ACTIVITY_TYPE = "ActivityType";
        public static final String NAME = "Name";
        public static final String LAYOUT_NR = "LayoutNr";       // for each ActivityType, we might have different views/activities/layouts
        @Deprecated
        public static final String NEXT_POSITION = "NextPosition";   // this value is needed when a new row is added, never ever used
        public static final String SHOW_LAP_BUTTON = "ShowLapButton";
        public static final String SHOW_MAP = "ShowMap";
        // public static final String VIEW_ID       = "ViewID";
        public static final String SENSOR_TYPE = "SensorType";
        public static final String TEXT_SIZE = "TextSize";
        public static final String SOURCE_DEVICE_ID = "SourceDeviceId";  // new in version 5
        public static final String FILTER_TYPE = "FilterType";      // new in version 6
        public static final String FILTER_CONSTANT = "FilterConstant";  // new in version 6  // TODO: add and use these
        protected static final int SMALL = 20;
        protected static final int MEDIUM = 25;
        protected static final int LARGE = 30;
        protected static final int HUGE = 40;
        @Deprecated
        protected static final String CREATE_VIEWS_TABLE_V1 = "create table " + VIEWS_TABLE + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                // + VIEW_ID       + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + ACTIVITY_TYPE + " text, "
                + NAME + " text, "
                + LAYOUT_NR + " int, "  // TODO: currently, we have only layout nr 1.
                + NEXT_POSITION + " int)";
        @Deprecated
        protected static final String CREATE_VIEWS_TABLE_V2 = "create table " + VIEWS_TABLE + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                // + VIEW_ID       + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + ACTIVITY_TYPE + " text, "
                + NAME + " text, "
                + LAYOUT_NR + " int, "  // TODO: currently, we have only layout nr 1.
                + NEXT_POSITION + " int, "
                + SHOW_LAP_BUTTON + " int)";
        protected static final String CREATE_VIEWS_TABLE_V3 = "create table " + VIEWS_TABLE + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                // + VIEW_ID       + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + ACTIVITY_TYPE + " text, "
                + NAME + " text, "
                + LAYOUT_NR + " int, "
                + NEXT_POSITION + " int, "
                + SHOW_LAP_BUTTON + " int, "
                + SHOW_MAP + " int)";
        @Deprecated
        protected static final String CREATE_LAYOUTS_TABLE_V3 = "create table " + ROWS_TABLE + " ("
                + ROW_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + VIEW_ID + " int, "
                + SENSOR_TYPE + " text, "
                + TEXT_SIZE + " int)";
        @Deprecated
        protected static final String CREATE_LAYOUTS_TABLE_V4 = "create table " + ROWS_TABLE + " ("
                + ROW_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + VIEW_ID + " int, "
                + ROW_NR + " int, "
                + COL_NR + " int, "
                + SENSOR_TYPE + " text, "
                + TEXT_SIZE + " int)";
        @Deprecated
        protected static final String CREATE_LAYOUTS_TABLE_V5 = "create table " + ROWS_TABLE + " ("
                + ROW_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + VIEW_ID + " int, "
                + ROW_NR + " int, "
                + COL_NR + " int, "
                + SENSOR_TYPE + " text, "
                + TEXT_SIZE + " int, "
                + SOURCE_DEVICE_ID + " int)";
        // TODO: same as for PebbleDbHelper: switch to next/previous id structure?
        // NO! when inserting a new view, we just have to add 1 to all following layout_nrs, similar for deleting.
        protected static final String CREATE_LAYOUTS_TABLE_V6 = "create table " + ROWS_TABLE + " ("
                + ROW_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + VIEW_ID + " int, "
                + ROW_NR + " int, "
                + COL_NR + " int, "
                + SENSOR_TYPE + " text, "
                + TEXT_SIZE + " int, "
                + SOURCE_DEVICE_ID + " int, "
                + FILTER_TYPE + " text, "
                + FILTER_CONSTANT + " real)";
        private final String TAG = TrackingViewsDbHelper.class.getName();
        private Context mContext;
        private boolean mHavePressureSensor = false;

        // Constructor
        public TrackingViewsDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);

            mContext = context;
            mHavePressureSensor = HavePressureSensor.havePressureSensor(mContext);
        }

        private static final void addColumn(SQLiteDatabase db, String table, String column, String type) {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type + ";");
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_VIEWS_TABLE_V3);
            db.execSQL(CREATE_LAYOUTS_TABLE_V6);

            if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_VIEWS_TABLE_V3);
            if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_LAYOUTS_TABLE_V6);


            if (DEBUG) Log.d(TAG, "filling db");

            EnumMap<ActivityType, List<RowData>> viewMap = createViewMap();
            for (ActivityType activityType : viewMap.keySet()) {
                addDefaultActivity(db, activityType, 1);
            }
            if (DEBUG) Log.d(TAG, "filled db");
        }

        public long addDefaultActivity(SQLiteDatabase db, ActivityType activityType, int layoutNr) {
            if (DEBUG)
                Log.i(TAG, "addDefaultActivity: activityType=" + activityType + ", layoutNr=" + layoutNr);
            long newViewId = -1;

            String name = mContext.getString(R.string.default_device_name_format,
                    mContext.getString(activityType.getTitleId()),
                    mContext.getString(R.string.text_default));
            Cursor cursor = db.query(VIEWS_TABLE,
                    null,
                    ACTIVITY_TYPE + "=?",
                    new String[]{activityType.name()},
                    null,
                    null,
                    null);
            int numberOfDefaults = cursor.getCount();
            if (numberOfDefaults > 0) {
                name = mContext.getString(R.string.string_and_number_format, name, numberOfDefaults + 1);
            }

            // OK, not the optimal way...
            EnumMap<ActivityType, List<RowData>> viewMap = createViewMap();

            ContentValues values = new ContentValues();

            values.put(ACTIVITY_TYPE, activityType.name());
            values.put(NAME, name);
            values.put(LAYOUT_NR, layoutNr);
            values.put(NEXT_POSITION, -1);  // insert an invalid value to indicate that this field is invalid
            values.put(SHOW_LAP_BUTTON, 1);
            values.put(SHOW_MAP, 0);
            newViewId = db.insert(VIEWS_TABLE, null, values);

            for (RowData rowData : viewMap.get(activityType)) {
                if (DEBUG)
                    Log.i(TAG, "adding new row: newViewId=" + newViewId + ", sensorType=" + rowData.sensorType.name());

                values.clear();
                values.put(VIEW_ID, newViewId);
                values.put(ROW_NR, rowData.row);
                values.put(COL_NR, rowData.col);
                values.put(SENSOR_TYPE, rowData.sensorType.name());
                values.put(TEXT_SIZE, rowData.textSize);
                values.put(FILTER_TYPE, FilterType.INSTANTANEOUS.name());
                values.put(FILTER_CONSTANT, 1);
                db.insert(ROWS_TABLE, null, values);
            }

            return newViewId;
        }

        //Called whenever newVersion != oldVersion
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

            if (oldVersion < 2) {  // add SHOW_LAP_BUTTON and set its field to 1
                addColumn(db, VIEWS_TABLE, SHOW_LAP_BUTTON, "int");

                ContentValues contentValues = new ContentValues();
                contentValues.put(SHOW_LAP_BUTTON, 1);
                db.update(VIEWS_TABLE, contentValues, null, null);
            }

            if (oldVersion < 3) {  // add SHOW_MAP and set its field to 0
                addColumn(db, VIEWS_TABLE, SHOW_MAP, "int");

                ContentValues contentValues = new ContentValues();
                contentValues.put(SHOW_MAP, 0);
                db.update(VIEWS_TABLE, contentValues, null, null);
            }

            if (oldVersion < 4) { // add ROW_NR and COL_NR
                addColumn(db, ROWS_TABLE, ROW_NR, "int");
                addColumn(db, ROWS_TABLE, COL_NR, "int");

                ContentValues contentValues = new ContentValues();
                Cursor cursor = db.query(ROWS_TABLE, null, null, null, null, null, null);
                int rowNr = 1;
                while (cursor.moveToNext()) {
                    long rowId = cursor.getLong(cursor.getColumnIndex(ROW_ID));
                    contentValues.clear();
                    contentValues.put(ROW_NR, rowNr++);
                    contentValues.put(COL_NR, 1);
                    db.update(ROWS_TABLE, contentValues, ROW_ID + "=?", new String[]{Long.toString(rowId)});
                    db.insert(ROWS_TABLE, null, contentValues);
                }

                Log.i(TAG, "upgraded to db version 4");
            }

            if (oldVersion < 5) {  // add SOURCE_DEVICE_ID
                addColumn(db, ROWS_TABLE, SOURCE_DEVICE_ID, "int"); // device_id = null => best sensor
            }

            if (oldVersion < 6) {  // add FILTER_TYPE and FILTER_CONSTANT and give them some meaningful data
                addColumn(db, ROWS_TABLE, FILTER_TYPE, "text");
                addColumn(db, ROWS_TABLE, FILTER_CONSTANT, "real");

                ContentValues contentValues = new ContentValues();
                contentValues.put(FILTER_TYPE, FilterType.INSTANTANEOUS.name());
                contentValues.put(FILTER_CONSTANT, 1);
                db.update(ROWS_TABLE, contentValues, null, null);
            }
        }

        protected EnumMap<ActivityType, List<RowData>> createViewMap() {
            if (DEBUG) Log.d(TAG, "createViewMap");
            EnumMap<ActivityType, List<RowData>> viewMap = new EnumMap<ActivityType, List<RowData>>(ActivityType.class);
            List<RowData> rowDataList;

            // GENERIC
            rowDataList = new LinkedList<RowData>();
            rowDataList.add(new RowData(SensorType.TIME_ACTIVE, SMALL, 1, 1));
            rowDataList.add(new RowData(SensorType.TIME_OF_DAY, SMALL, 1, 2));
            rowDataList.add(new RowData(SensorType.SPEED_mps, LARGE, 2, 1));
            rowDataList.add(new RowData(SensorType.DISTANCE_m, MEDIUM, 3, 1));
            if (mHavePressureSensor) {
                rowDataList.add(new RowData(SensorType.ALTITUDE, MEDIUM, 3, 2));
            }
            viewMap.put(ActivityType.GENERIC, rowDataList);

            // GENERIC_HR
            rowDataList = new LinkedList<RowData>();
            rowDataList.add(new RowData(SensorType.TIME_ACTIVE, SMALL, 1, 1));
            rowDataList.add(new RowData(SensorType.TIME_OF_DAY, SMALL, 1, 2));
            rowDataList.add(new RowData(SensorType.HR, LARGE, 2, 1));
            rowDataList.add(new RowData(SensorType.SPEED_mps, MEDIUM, 3, 1));
            rowDataList.add(new RowData(SensorType.DISTANCE_m, MEDIUM, 4, 1));
            if (mHavePressureSensor) {
                rowDataList.add(new RowData(SensorType.ALTITUDE, MEDIUM, 4, 2));
            }
            viewMap.put(ActivityType.GENERIC_HR, rowDataList);

            // RUN_SPEED_AND_CADENCE
            rowDataList = new LinkedList<RowData>();
            rowDataList.add(new RowData(SensorType.TIME_ACTIVE, SMALL, 1, 1));
            rowDataList.add(new RowData(SensorType.TIME_OF_DAY, SMALL, 1, 2));
            rowDataList.add(new RowData(SensorType.HR, LARGE, 2, 1));
            rowDataList.add(new RowData(SensorType.PACE_spm, LARGE, 3, 1));
            rowDataList.add(new RowData(SensorType.CADENCE, LARGE, 4, 1));
            rowDataList.add(new RowData(SensorType.DISTANCE_m, MEDIUM, 5, 1));
            if (mHavePressureSensor) {
                rowDataList.add(new RowData(SensorType.ALTITUDE, MEDIUM, 5, 2));
            }
            viewMap.put(ActivityType.RUN_SPEED_AND_CADENCE, rowDataList);

            // RUN_SPEED
            rowDataList = new LinkedList<RowData>();
            rowDataList.add(new RowData(SensorType.TIME_ACTIVE, SMALL, 1, 1));
            rowDataList.add(new RowData(SensorType.TIME_OF_DAY, SMALL, 1, 2));
            rowDataList.add(new RowData(SensorType.HR, LARGE, 2, 1));
            rowDataList.add(new RowData(SensorType.PACE_spm, LARGE, 3, 1));
            rowDataList.add(new RowData(SensorType.DISTANCE_m, MEDIUM, 5, 1));
            if (mHavePressureSensor) {
                rowDataList.add(new RowData(SensorType.ALTITUDE, MEDIUM, 5, 2));
            }
            viewMap.put(ActivityType.RUN_SPEED, rowDataList);

            // BIKE_SPEED
            rowDataList = new LinkedList<RowData>();
            rowDataList.add(new RowData(SensorType.TIME_ACTIVE, SMALL, 1, 1));
            rowDataList.add(new RowData(SensorType.TIME_OF_DAY, SMALL, 1, 2));
            rowDataList.add(new RowData(SensorType.HR, LARGE, 2, 1));
            rowDataList.add(new RowData(SensorType.SPEED_mps, LARGE, 3, 1));
            rowDataList.add(new RowData(SensorType.DISTANCE_m, MEDIUM, 5, 1));
            if (mHavePressureSensor) {
                rowDataList.add(new RowData(SensorType.ALTITUDE, MEDIUM, 5, 2));
            }
            viewMap.put(ActivityType.BIKE_SPEED, rowDataList);

            // BIKE_SPEED_AND_CADENCE
            rowDataList = new LinkedList<RowData>();
            rowDataList.add(new RowData(SensorType.TIME_ACTIVE, SMALL, 1, 1));
            rowDataList.add(new RowData(SensorType.TIME_OF_DAY, SMALL, 1, 2));
            rowDataList.add(new RowData(SensorType.HR, LARGE, 2, 1));
            rowDataList.add(new RowData(SensorType.SPEED_mps, LARGE, 3, 1));
            rowDataList.add(new RowData(SensorType.CADENCE, LARGE, 4, 1));
            rowDataList.add(new RowData(SensorType.DISTANCE_m, MEDIUM, 5, 1));
            if (mHavePressureSensor) {
                rowDataList.add(new RowData(SensorType.ALTITUDE, MEDIUM, 5, 2));
            }
            viewMap.put(ActivityType.BIKE_SPEED_AND_CADENCE, rowDataList);

            // BIKE_POWER
            rowDataList = new LinkedList<RowData>();
            rowDataList.add(new RowData(SensorType.TIME_ACTIVE, SMALL, 1, 1));
            rowDataList.add(new RowData(SensorType.TIME_OF_DAY, SMALL, 1, 2));
            rowDataList.add(new RowData(SensorType.POWER, HUGE, 2, 1));
            rowDataList.add(new RowData(SensorType.HR, LARGE, 3, 1));
            rowDataList.add(new RowData(SensorType.CADENCE, LARGE, 3, 2));
            rowDataList.add(new RowData(SensorType.DISTANCE_m, SMALL, 4, 1));
            rowDataList.add(new RowData(SensorType.SPEED_mps, MEDIUM, 4, 2));
            if (mHavePressureSensor) {
                rowDataList.add(new RowData(SensorType.ALTITUDE, SMALL, 4, 3));
            }
            viewMap.put(ActivityType.BIKE_POWER, rowDataList);

            return viewMap;
        }

        protected class RowData {
            public SensorType sensorType;
            public int textSize;
            public int row;
            public int col;

            public RowData(SensorType sensorType, int textSize, int row, int col) {
                this.sensorType = sensorType;
                this.textSize = textSize;
                this.row = row;
                this.col = col;
            }

        }

    }
}
