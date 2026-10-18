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

package com.atrainingtracker.trainingtracker.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.ActivityType;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;
import com.atrainingtracker.banalservice.filters.DefaultFilterConfig;
import com.atrainingtracker.banalservice.filters.FilterData;
import com.atrainingtracker.banalservice.filters.FilterType;
import com.atrainingtracker.banalservice.filters.SensorFilterDefaults;
import com.atrainingtracker.banalservice.helpers.HavePressureSensor;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize;
import com.atrainingtracker.util.CursorUtils;
import com.atrainingtracker.util.EnumUtils;

import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;


public class TrackingViewsDatabaseManager {
    private static final String TAG = "TrackingViewsDatabaseManager";
    private static final boolean DEBUG = TrainingApplication.getDebug(false);

    // singleton pattern
    private static volatile TrackingViewsDatabaseManager cInstance;
    private final TrackingViewsDbHelper cDbHelper;
    private SQLiteDatabase mDatabase = null;

    private TrackingViewsDatabaseManager(@NonNull Context context) {
        this.cDbHelper = new TrackingViewsDbHelper(context.getApplicationContext());
    }

    @NonNull
    public static TrackingViewsDatabaseManager getInstance(@NonNull Context context) {
        if (cInstance == null) {
            synchronized (TrackingViewsDatabaseManager.class) {
                if (cInstance == null) {
                    cInstance = new TrackingViewsDatabaseManager(context);
                }
            }
        }
        return cInstance;
    }

    /**
     * Returns a writable database instance and ensures it remains open.
     * Re-opens if closed (e.g., by a backup process) to prevent IllegalStateException (ATT-289).
     */
    public SQLiteDatabase getDatabase() {
        if (mDatabase != null && mDatabase.isOpen()) {
            return mDatabase;
        }
        synchronized (this) {
            if (mDatabase != null && mDatabase.isOpen()) {
                return mDatabase;
            }
            // If the database is closed, ensure the helper clears its reference
            if (mDatabase != null) {
                cDbHelper.close();
            }
            mDatabase = cDbHelper.getWritableDatabase();
            return mDatabase;
        }
    }



    public void updateNameOfTabView(long viewId, String name) {
        ContentValues values = new ContentValues();
        values.put(TrackingViewsDbHelper.NAME, name);

        getDatabase().update(TrackingViewsDbHelper.VIEWS_TABLE,
                values,
                TrackingViewsDbHelper.C_ID + "=?",
                new String[]{viewId + ""});

    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // some high level methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void updateSensorView(
            long sensorFieldId,
            SensorType sensorType,
            ViewSize viewSize,
            @Nullable Long sourceDeviceId,
            FilterType filterType,
            double filterConstant
    ) {
        ContentValues values = new ContentValues();
        values.put(TrackingViewsDbHelper.SENSOR_TYPE, sensorType.name());
        values.put(TrackingViewsDbHelper.VIEW_SIZE, viewSize.name());
        if (sourceDeviceId == null || sourceDeviceId < 0) {
            values.putNull(TrackingViewsDbHelper.SOURCE_DEVICE_ID);
        } else {
            values.put(TrackingViewsDbHelper.SOURCE_DEVICE_ID, sourceDeviceId);
        }
        values.put(TrackingViewsDbHelper.FILTER_TYPE, filterType.name());
        values.put(TrackingViewsDbHelper.FILTER_CONSTANT, filterConstant);

        getDatabase().update(TrackingViewsDbHelper.ROWS_TABLE,
                values,
                TrackingViewsDbHelper.ROW_ID + "=?",
                new String[]{sensorFieldId + ""});
    }

    public void insertSensorFieldAt(
            long tabViewId,
            int rowNr,
            int colNr,                        // -1 means new row
            SensorType sensorType,
            ViewSize viewSize,
            @Nullable Long sourceDeviceId,    // null means none = best
            FilterType filterType,
            double filterConstant) {

        Log.i(TAG, "insertSensorFiledAt(" + tabViewId + ", " + rowNr + ", " + colNr + ", " + sensorType.name());

        String sql;
        if (colNr == -1) {  // new row -> add 1 to all rows with a larger rowNr
            sql = "UPDATE " + TrackingViewsDbHelper.ROWS_TABLE +
                    " SET " + TrackingViewsDbHelper.ROW_NR + "=" + TrackingViewsDbHelper.ROW_NR + "+1" +
                    " WHERE " + TrackingViewsDbHelper.VIEW_ID + "=" + tabViewId +
                    " AND " + TrackingViewsDbHelper.ROW_NR + " >= " + rowNr;
            getDatabase().execSQL(sql);

            // Shift section dividers at or below rowNr down by 1 (REQ-UI-411.2)
            String shiftDividersSql = "UPDATE " + TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE +
                    " SET " + TrackingViewsDbHelper.ROW_NR + "=" + TrackingViewsDbHelper.ROW_NR + "+1" +
                    " WHERE " + TrackingViewsDbHelper.VIEW_ID + "=" + tabViewId +
                    " AND " + TrackingViewsDbHelper.ROW_NR + " >= " + rowNr;
            getDatabase().execSQL(shiftDividersSql);

            colNr = 1;  // now, that this row is freed, we can give the colNr the correct value.
        }
        else {  // new col -> add 1 to all cols with a larger colNr and the same rowNr
            sql = "UPDATE " + TrackingViewsDbHelper.ROWS_TABLE +
                    " SET " + TrackingViewsDbHelper.COL_NR + "=" + TrackingViewsDbHelper.COL_NR + "+1" +
                    " WHERE " + TrackingViewsDbHelper.VIEW_ID + "=" + tabViewId +
                    " AND " + TrackingViewsDbHelper.COL_NR + " >= " + colNr + " AND " + TrackingViewsDbHelper.ROW_NR + "=" + rowNr;
            getDatabase().execSQL(sql);
        }

        ContentValues values = new ContentValues();
        values.put(TrackingViewsDbHelper.VIEW_ID, tabViewId);
        values.put(TrackingViewsDbHelper.ROW_NR, rowNr);
        values.put(TrackingViewsDbHelper.COL_NR, colNr);
        values.put(TrackingViewsDbHelper.SENSOR_TYPE, sensorType.name());
        values.put(TrackingViewsDbHelper.VIEW_SIZE, viewSize.name());
        if (sourceDeviceId != null && sourceDeviceId >= 0) {
            values.put(TrackingViewsDbHelper.SOURCE_DEVICE_ID, sourceDeviceId);
        }
        values.put(TrackingViewsDbHelper.FILTER_TYPE, filterType.name());
        values.put(TrackingViewsDbHelper.FILTER_CONSTANT, filterConstant);

        getDatabase().insert(TrackingViewsDbHelper.ROWS_TABLE, null, values);
    }


    protected void updateBoolean(long viewId, String ID, boolean value) {
        ContentValues values = new ContentValues();
        values.put(ID, value ? 1 : 0);

        getDatabase().update(TrackingViewsDbHelper.VIEWS_TABLE,
                values,
                TrackingViewsDbHelper.C_ID + "=?",
                new String[]{viewId + ""});
    }

    public void updateShowMap(long viewId, boolean showMap) {
        updateBoolean(viewId, TrackingViewsDbHelper.SHOW_MAP, showMap);
    }

    public void updateShowLapButton(long viewId, boolean showLapButton) {
        updateBoolean(viewId, TrackingViewsDbHelper.SHOW_LAP_BUTTON, showLapButton);
    }

    public void updateShowLiveSegments(long viewId, boolean showLiveSegments) {
        updateBoolean(viewId, TrackingViewsDbHelper.SHOW_LIVE_SEGMENTS, showLiveSegments);
    }

    public void updateShowElevationProfile(long viewId, boolean showElevationProfile) {
        updateBoolean(viewId, TrackingViewsDbHelper.SHOW_ELEVATION_PROFILE, showElevationProfile);
    }

    public void updateShowLiveClimbs(long viewId, boolean showLiveClimbs) {
        updateBoolean(viewId, TrackingViewsDbHelper.SHOW_LIVE_CLIMBS, showLiveClimbs);
    }

    public void updateShowNavigationHints(long viewId, boolean showNavigationHints) {
        updateBoolean(viewId, TrackingViewsDbHelper.SHOW_NAVIGATION_HINTS, showNavigationHints);
    }

    public void updateElevationProfileScope(long viewId, int scope) {
        ContentValues values = new ContentValues();
        values.put(TrackingViewsDbHelper.ELEVATION_PROFILE_SCOPE, scope);
        SQLiteDatabase db = getDatabase();
        db.update(TrackingViewsDbHelper.VIEWS_TABLE, values, TrackingViewsDbHelper.C_ID + "=?", new String[]{Long.toString(viewId)});
    }

    public void updateElevationProfileLookahead(long viewId, int lookaheadMeters) {
        ContentValues values = new ContentValues();
        values.put(TrackingViewsDbHelper.ELEVATION_PROFILE_LOOKAHEAD, lookaheadMeters);
        SQLiteDatabase db = getDatabase();
        db.update(TrackingViewsDbHelper.VIEWS_TABLE, values, TrackingViewsDbHelper.C_ID + "=?", new String[]{Long.toString(viewId)});
    }

    public void deleteSensorField(long sensorFieldId) {
        if (DEBUG) Log.i(TAG, "deleteSensorField(" + sensorFieldId + ")");

        SQLiteDatabase db = getDatabase();
        Long tabViewId = null;
        try (Cursor cursor = db.query(TrackingViewsDbHelper.ROWS_TABLE,
                new String[]{TrackingViewsDbHelper.VIEW_ID},
                TrackingViewsDbHelper.ROW_ID + "=?",
                new String[]{String.valueOf(sensorFieldId)}, null, null, null)) {
            tabViewId = CursorUtils.mapFirst(cursor, c -> c.getLong(0));
        }

        db.delete(TrackingViewsDbHelper.ROWS_TABLE,
                TrackingViewsDbHelper.ROW_ID + "=?", // note that due to historic reasons, whe ID of a sensor field is called ROW_ID :(
                new String[]{sensorFieldId + ""});

        if (tabViewId != null) {
            normalizeGrid(tabViewId);
        }
    }

    /**
     * Atomically swaps the grid coordinates (RowNr, ColNr) of two sensor fields (REQ-UI-200).
     */
    public void swapSensorFields(long fieldIdA, long fieldIdB) {
        if (fieldIdA == fieldIdB) {
            return;
        }
        SQLiteDatabase db = getDatabase();
        db.beginTransaction();
        try {
            int rowA;
            int colA;
            int rowB;
            int colB;
            try (Cursor cursorA = db.query(TrackingViewsDbHelper.ROWS_TABLE,
                    new String[]{TrackingViewsDbHelper.ROW_NR, TrackingViewsDbHelper.COL_NR},
                    TrackingViewsDbHelper.ROW_ID + "=?",
                    new String[]{String.valueOf(fieldIdA)}, null, null, null);
                 Cursor cursorB = db.query(TrackingViewsDbHelper.ROWS_TABLE,
                    new String[]{TrackingViewsDbHelper.ROW_NR, TrackingViewsDbHelper.COL_NR},
                    TrackingViewsDbHelper.ROW_ID + "=?",
                    new String[]{String.valueOf(fieldIdB)}, null, null, null)) {

                if (cursorA != null && cursorB != null && cursorA.moveToFirst() && cursorB.moveToFirst()) {
                    rowA = cursorA.getInt(0);
                    colA = cursorA.getInt(1);
                    rowB = cursorB.getInt(0);
                    colB = cursorB.getInt(1);
                } else {
                    return;
                }
            }

            ContentValues valA = new ContentValues();
            valA.put(TrackingViewsDbHelper.ROW_NR, rowB);
            valA.put(TrackingViewsDbHelper.COL_NR, colB);
            db.update(TrackingViewsDbHelper.ROWS_TABLE, valA,
                    TrackingViewsDbHelper.ROW_ID + "=?",
                    new String[]{String.valueOf(fieldIdA)});

            ContentValues valB = new ContentValues();
            valB.put(TrackingViewsDbHelper.ROW_NR, rowA);
            valB.put(TrackingViewsDbHelper.COL_NR, colA);
            db.update(TrackingViewsDbHelper.ROWS_TABLE, valB,
                    TrackingViewsDbHelper.ROW_ID + "=?",
                    new String[]{String.valueOf(fieldIdB)});

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Repositions a sensor field to a designated target row and column slot,
     * compacting the origin row, adjusting row/column indices, and normalizing the grid (REQ-UI-200).
     *
     * @param sensorFieldId The ID of the sensor field to move.
     * @param targetRow The target row number.
     * @param targetCol The target column number, or -1 to create a new row at targetRow.
     */
    public void moveSensorField(long sensorFieldId, int targetRow, int targetCol) {
        SQLiteDatabase db = getDatabase();
        db.beginTransaction();
        try {
            long tabViewId;
            int oldRow;
            int oldCol;
            try (Cursor cursor = db.query(TrackingViewsDbHelper.ROWS_TABLE,
                    new String[]{TrackingViewsDbHelper.VIEW_ID, TrackingViewsDbHelper.ROW_NR, TrackingViewsDbHelper.COL_NR},
                    TrackingViewsDbHelper.ROW_ID + "=?",
                    new String[]{String.valueOf(sensorFieldId)}, null, null, null)) {
                if (cursor == null || !cursor.moveToFirst()) {
                    return;
                }
                tabViewId = cursor.getLong(0);
                oldRow = cursor.getInt(1);
                oldCol = cursor.getInt(2);
            }

            // 1. Temporarily park the field out of the layout
            ContentValues parkVal = new ContentValues();
            parkVal.put(TrackingViewsDbHelper.ROW_NR, -999);
            parkVal.put(TrackingViewsDbHelper.COL_NR, -999);
            db.update(TrackingViewsDbHelper.ROWS_TABLE, parkVal,
                    TrackingViewsDbHelper.ROW_ID + "=?",
                    new String[]{String.valueOf(sensorFieldId)});

            // 2. Compact the remaining fields in the old row (shift columns > oldCol left)
            db.execSQL("UPDATE " + TrackingViewsDbHelper.ROWS_TABLE +
                    " SET " + TrackingViewsDbHelper.COL_NR + "=" + TrackingViewsDbHelper.COL_NR + "-1" +
                    " WHERE " + TrackingViewsDbHelper.VIEW_ID + "=" + tabViewId +
                    " AND " + TrackingViewsDbHelper.ROW_NR + "=" + oldRow +
                    " AND " + TrackingViewsDbHelper.COL_NR + " > " + oldCol);

            // 3. Check if oldRow has 0 remaining fields
            int remainingInOldRow = 0;
            try (Cursor countCursor = db.query(TrackingViewsDbHelper.ROWS_TABLE,
                    new String[]{TrackingViewsDbHelper.ROW_ID},
                    TrackingViewsDbHelper.VIEW_ID + "=? AND " + TrackingViewsDbHelper.ROW_NR + "=?",
                    new String[]{String.valueOf(tabViewId), String.valueOf(oldRow)},
                    null, null, null)) {
                remainingInOldRow = countCursor != null ? countCursor.getCount() : 0;
            }

            if (remainingInOldRow == 0) {
                // Shift all rows > oldRow down by 1
                db.execSQL("UPDATE " + TrackingViewsDbHelper.ROWS_TABLE +
                        " SET " + TrackingViewsDbHelper.ROW_NR + "=" + TrackingViewsDbHelper.ROW_NR + "-1" +
                        " WHERE " + TrackingViewsDbHelper.VIEW_ID + "=" + tabViewId +
                        " AND " + TrackingViewsDbHelper.ROW_NR + " > " + oldRow);
                if (targetRow > oldRow) {
                    targetRow--;
                }
            }

            // 4. Open space at target location
            if (targetCol == -1) {
                // New row requested: shift rows >= targetRow up by 1
                db.execSQL("UPDATE " + TrackingViewsDbHelper.ROWS_TABLE +
                        " SET " + TrackingViewsDbHelper.ROW_NR + "=" + TrackingViewsDbHelper.ROW_NR + "+1" +
                        " WHERE " + TrackingViewsDbHelper.VIEW_ID + "=" + tabViewId +
                        " AND " + TrackingViewsDbHelper.ROW_NR + " >= " + targetRow);
                targetCol = 1;
            } else {
                // Column insertion into existing row: shift cols >= targetCol right by 1
                db.execSQL("UPDATE " + TrackingViewsDbHelper.ROWS_TABLE +
                        " SET " + TrackingViewsDbHelper.COL_NR + "=" + TrackingViewsDbHelper.COL_NR + "+1" +
                        " WHERE " + TrackingViewsDbHelper.VIEW_ID + "=" + tabViewId +
                        " AND " + TrackingViewsDbHelper.ROW_NR + "=" + targetRow +
                        " AND " + TrackingViewsDbHelper.COL_NR + " >= " + targetCol);
            }

            // 5. Assign final coordinates to the moved field
            ContentValues targetVal = new ContentValues();
            targetVal.put(TrackingViewsDbHelper.ROW_NR, targetRow);
            targetVal.put(TrackingViewsDbHelper.COL_NR, targetCol);
            db.update(TrackingViewsDbHelper.ROWS_TABLE, targetVal,
                    TrackingViewsDbHelper.ROW_ID + "=?",
                    new String[]{String.valueOf(sensorFieldId)});

            // 6. Normalize grid coordinates
            normalizeGrid(db, tabViewId);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Normalizes the rows and columns for a given tabViewId to ensure contiguous 1..N indices with zero gaps.
     */
    public void normalizeGrid(long tabViewId) {
        SQLiteDatabase db = getDatabase();
        db.beginTransaction();
        try {
            normalizeGrid(db, tabViewId);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void normalizeGrid(SQLiteDatabase db, long tabViewId) {
        // Query distinct row numbers ordered ascending
        java.util.List<Integer> distinctRows = new java.util.ArrayList<>();
        try (Cursor rowCursor = db.query(true, TrackingViewsDbHelper.ROWS_TABLE,
                new String[]{TrackingViewsDbHelper.ROW_NR},
                TrackingViewsDbHelper.VIEW_ID + "=? AND " + TrackingViewsDbHelper.ROW_NR + " > 0",
                new String[]{String.valueOf(tabViewId)},
                null, null, TrackingViewsDbHelper.ROW_NR + " ASC", null)) {
            if (rowCursor != null) {
                while (rowCursor.moveToNext()) {
                    distinctRows.add(rowCursor.getInt(0));
                }
            }
        }

        // 1. Assign temporary negative row indices to avoid collisions
        for (int r = 0; r < distinctRows.size(); r++) {
            int originalRow = distinctRows.get(r);
            int tempRow = -(r + 1);
            ContentValues val = new ContentValues();
            val.put(TrackingViewsDbHelper.ROW_NR, tempRow);
            db.update(TrackingViewsDbHelper.ROWS_TABLE, val,
                    TrackingViewsDbHelper.VIEW_ID + "=? AND " + TrackingViewsDbHelper.ROW_NR + "=?",
                    new String[]{String.valueOf(tabViewId), String.valueOf(originalRow)});
        }

        // Invert back to positive 1..R
        db.execSQL("UPDATE " + TrackingViewsDbHelper.ROWS_TABLE +
                " SET " + TrackingViewsDbHelper.ROW_NR + "=-" + TrackingViewsDbHelper.ROW_NR +
                " WHERE " + TrackingViewsDbHelper.VIEW_ID + "=" + tabViewId +
                " AND " + TrackingViewsDbHelper.ROW_NR + " < 0");

        // 2. Re-index columns in each row to 1..C
        for (int r = 1; r <= distinctRows.size(); r++) {
            try (Cursor colCursor = db.query(TrackingViewsDbHelper.ROWS_TABLE,
                    new String[]{TrackingViewsDbHelper.ROW_ID},
                    TrackingViewsDbHelper.VIEW_ID + "=? AND " + TrackingViewsDbHelper.ROW_NR + "=?",
                    new String[]{String.valueOf(tabViewId), String.valueOf(r)},
                    null, null, TrackingViewsDbHelper.COL_NR + " ASC, " + TrackingViewsDbHelper.ROW_ID + " ASC")) {

                if (colCursor != null) {
                    int colIndex = 1;
                    while (colCursor.moveToNext()) {
                        long fieldId = colCursor.getLong(0);
                        ContentValues colVal = new ContentValues();
                        colVal.put(TrackingViewsDbHelper.COL_NR, colIndex++);
                        db.update(TrackingViewsDbHelper.ROWS_TABLE, colVal,
                                TrackingViewsDbHelper.ROW_ID + "=?",
                                new String[]{String.valueOf(fieldId)});
                    }
                }
            }
        }

        // 3. Re-map section dividers (REQ-UI-411.2)
        // Existing dividers for tabViewId mapped to matching new row index (1..N).
        // If an original row no longer exists or newRow <= 1, delete it.
        try (Cursor divCursor = db.query(TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE,
                new String[]{TrackingViewsDbHelper.C_ID, TrackingViewsDbHelper.ROW_NR, TrackingViewsDbHelper.TITLE},
                TrackingViewsDbHelper.VIEW_ID + "=?",
                new String[]{String.valueOf(tabViewId)},
                null, null, null)) {
            if (divCursor != null) {
                while (divCursor.moveToNext()) {
                    long divId = divCursor.getLong(0);
                    int origRow = divCursor.getInt(1);
                    int newRowIndex = distinctRows.indexOf(origRow) + 1; // 1-based new row index
                    if (newRowIndex <= 1) {
                        // Orphaned or now at row 1 (section dividers are between rows, row >= 2)
                        db.delete(TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE,
                                TrackingViewsDbHelper.C_ID + "=?",
                                new String[]{String.valueOf(divId)});
                    } else if (newRowIndex != origRow) {
                        ContentValues divVal = new ContentValues();
                        divVal.put(TrackingViewsDbHelper.ROW_NR, newRowIndex);
                        db.update(TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE,
                                divVal,
                                TrackingViewsDbHelper.C_ID + "=?",
                                new String[]{String.valueOf(divId)});
                    }
                }
            }
        }
    }

    @NonNull
    public ActivityType getActivityTypeForTab(long tabViewId) {
        try (Cursor cursor = getDatabase().query(TrackingViewsDbHelper.VIEWS_TABLE,
                null,
                TrackingViewsDbHelper.C_ID + "=?",
                new String[]{tabViewId + ""},
                null,
                null,
                null)) {
            ActivityType result = CursorUtils.mapFirst(cursor, c -> {
                int colIdx = c.getColumnIndex(TrackingViewsDbHelper.ACTIVITY_TYPE);
                return colIdx != -1 ? EnumUtils.safeValueOf(ActivityType.class, c.getString(colIdx), ActivityType.getDefaultActivityType(), TAG) : ActivityType.getDefaultActivityType();
            });
            return result != null ? result : ActivityType.getDefaultActivityType();
        }
    }

    public int getLayoutNr(long viewId) {
        try (Cursor cursor = getDatabase().query(TrackingViewsDbHelper.VIEWS_TABLE,
                null,
                TrackingViewsDbHelper.C_ID + "=?",
                new String[]{viewId + ""},
                null,
                null,
                null)) {
            Integer layoutNr = CursorUtils.mapFirst(cursor, c -> {
                int colIdx = c.getColumnIndex(TrackingViewsDbHelper.LAYOUT_NR);
                return colIdx != -1 ? c.getInt(colIdx) : -1;
            });
            return layoutNr != null ? layoutNr : -1;
        }
    }

    public void deleteTabView(long viewId) {

        SQLiteDatabase db = getDatabase();
        int layoutNr = getLayoutNr(viewId);

        // delete from views, rows, and section dividers tables (REQ-UI-411.2)
        db.delete(TrackingViewsDbHelper.VIEWS_TABLE, TrackingViewsDbHelper.C_ID + "=?", new String[]{viewId + ""});
        db.delete(TrackingViewsDbHelper.ROWS_TABLE, TrackingViewsDbHelper.VIEW_ID + "=?", new String[]{viewId + ""});
        db.delete(TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE, TrackingViewsDbHelper.VIEW_ID + "=?", new String[]{viewId + ""});

        // reduce layoutNr of all views with a larger layoutNr
        String execsql = "UPDATE " + TrackingViewsDbHelper.VIEWS_TABLE
                + " set " + TrackingViewsDbHelper.LAYOUT_NR
                + " = " + TrackingViewsDbHelper.LAYOUT_NR + " - 1 "
                + " where " + TrackingViewsDbHelper.LAYOUT_NR + " > " + layoutNr;
        if (DEBUG) Log.i(TAG, "DeleteView viewId=" + viewId + "code: " + execsql);
        db.execSQL(execsql);
    }

    public void addEmptyTabView(long viewId, boolean addAfterLayout) {
        int layoutNr = getLayoutNr(viewId);
        ActivityType activityType = getActivityTypeForTab(viewId);
        int newLayoutNr = layoutNr;
        if (addAfterLayout) {
            newLayoutNr = layoutNr + 1;
        }
        if (DEBUG)
            Log.i(TAG, "addEmptyView viewId=" + viewId + ", layoutNr=" + layoutNr + ", addAfterLayout=" + addAfterLayout + ", newLayoutNr=" + newLayoutNr);

        SQLiteDatabase db = getDatabase();

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
        values.put(TrackingViewsDbHelper.SHOW_ELEVATION_PROFILE, 0);
        values.put(TrackingViewsDbHelper.ELEVATION_PROFILE_SCOPE, 0);
        values.put(TrackingViewsDbHelper.ELEVATION_PROFILE_LOOKAHEAD, 2000);

        db.insert(TrackingViewsDbHelper.VIEWS_TABLE, null, values);
    }



    // TODO: create own helper class instad of passing a database manager.
    @NonNull
    public List<FilterData> getAllFilterData(DevicesDatabaseManager devicesDatabaseManager) {
        SQLiteDatabase db = getDatabase();
        try (Cursor cursor = db.query(TrackingViewsDbHelper.ROWS_TABLE,  // get cursor with all rows.  This is necessary, because the view and sport type and ... might change
                null,
                null,
                null,
                null, null, null)) {
            return CursorUtils.mapList(cursor, c -> {
                int sensorTypeIdx = c.getColumnIndex(TrackingViewsDbHelper.SENSOR_TYPE);
                String sensorTypeStr = sensorTypeIdx != -1 ? c.getString(sensorTypeIdx) : null;
                SensorType sensorType = EnumUtils.safeValueOf(SensorType.class, sensorTypeStr, SensorType.TIME_ACTIVE, TAG);

                int sourceDeviceIdx = c.getColumnIndex(TrackingViewsDbHelper.SOURCE_DEVICE_ID);
                int sourceDeviceId = sourceDeviceIdx != -1 ? c.getInt(sourceDeviceIdx) : -1;
                String deviceName = devicesDatabaseManager.getDeviceName(sourceDeviceId);

                int filterTypeIdx = c.getColumnIndex(TrackingViewsDbHelper.FILTER_TYPE);
                String filterTypeStr = filterTypeIdx != -1 ? c.getString(filterTypeIdx) : null;
                FilterType filterType = EnumUtils.safeValueOf(FilterType.class, filterTypeStr, FilterType.INSTANTANEOUS, TAG);

                int filterConstIdx = c.getColumnIndex(TrackingViewsDbHelper.FILTER_CONSTANT);
                double filterConstant = filterConstIdx != -1 ? c.getDouble(filterConstIdx) : 1.0;

                return new FilterData(deviceName, sensorType, filterType, filterConstant);
            });
        }
    }


    /**
     * Non-destructively ensures the dedicated Power tab exists for {@link ActivityType#BIKE_POWER}
     * without overwriting or deleting existing user-customized tracking tabs (REQ-UI-409.2).
     */
    public void ensurePowerTabExistsForBikePower() {
        cDbHelper.ensurePowerTabExistsForBikePower(getDatabase());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Section Dividers (REQ-UI-411)
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public record SectionDividerData(long id, long viewId, int rowNr, @Nullable String title) {
    }

    @NonNull
    public List<SectionDividerData> getSectionDividers(long tabViewId) {
        SQLiteDatabase db = getDatabase();
        try (Cursor cursor = db.query(
                TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE,
                new String[]{TrackingViewsDbHelper.C_ID, TrackingViewsDbHelper.VIEW_ID, TrackingViewsDbHelper.ROW_NR, TrackingViewsDbHelper.TITLE},
                TrackingViewsDbHelper.VIEW_ID + "=?",
                new String[]{String.valueOf(tabViewId)},
                null, null, TrackingViewsDbHelper.ROW_NR + " ASC")) {
            return CursorUtils.mapList(cursor, c -> {
                long id = c.getLong(0);
                long viewId = c.getLong(1);
                int rowNr = c.getInt(2);
                String title = c.isNull(3) ? null : c.getString(3);
                return new SectionDividerData(id, viewId, rowNr, title);
            });
        }
    }

    public void setSectionDivider(long viewId, int rowNr, @Nullable String title) {
        ContentValues values = new ContentValues();
        values.put(TrackingViewsDbHelper.VIEW_ID, viewId);
        values.put(TrackingViewsDbHelper.ROW_NR, rowNr);
        if (title != null && !title.trim().isEmpty()) {
            values.put(TrackingViewsDbHelper.TITLE, title.trim());
        } else {
            values.putNull(TrackingViewsDbHelper.TITLE);
        }
        getDatabase().insertWithOnConflict(
                TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    public void deleteSectionDivider(long viewId, int rowNr) {
        getDatabase().delete(
                TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE,
                TrackingViewsDbHelper.VIEW_ID + "=? AND " + TrackingViewsDbHelper.ROW_NR + "=?",
                new String[]{String.valueOf(viewId), String.valueOf(rowNr)}
        );
    }

    public void deleteSectionDividersForView(long viewId) {
        getDatabase().delete(
                TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE,
                TrackingViewsDbHelper.VIEW_ID + "=?",
                new String[]{String.valueOf(viewId)}
        );
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class TrackingViewsDbHelper extends SQLiteOpenHelper {
        public static final String DB_NAME = "FlexibleGenericView.db";
        // private static final boolean DEBUG = true;
        // public static final int DB_VERSION = 2;    // upgraded to version 2 at 16.2.2016
        // public static final int DB_VERSION = 3;       // upgraded to version 3 at 2.3.2016
        // public static final int DB_VERSION = 4;       // upgraded to version 4 at 3.1.2017
        // public static final int DB_VERSION = 5;       // upgraded to version 5 at 14.03.2018
        // public static final int DB_VERSION = 6;       // upgraded to version 6 at 17.04.2018
        // public static final int DB_VERSION = 7;       // upgraded to version 7 at 15.10.2019
        // public version 8 at 25.02.2026
        // public static final int DB_VERSION = 9;  // upgraded to version 9 at 12.04.2026: Adding Live Segments
        // public static final int DB_VERSION = 10; // upgraded to version 10 at 08.06.2026: Adding Elevation Profile
        // public static final int DB_VERSION = 11; // upgraded to version 11 at 03.10.2026: Repair missing ViewSize defaults in rows
        // public static final int DB_VERSION = 12; // upgraded to version 12 at 06.10.2026: Adding Live Climbs & Navigation Hints (ATT-2360)
        // public static final int DB_VERSION = 13; // upgraded to version 13 at 13.10.2026: Adding Elevation Profile Scope & Lookahead (ATT-3317)
        public static final int DB_VERSION = 14; // upgraded to version 14 at 18.10.2026: Adding Section Dividers Table (ATT-4055)
        public static final String VIEWS_TABLE = "ViewsTable";                // the table for the different 'tabs'
        public static final String ROWS_TABLE = "LayoutRowsTable";            // the table for the sensor fields within each tab
        public static final String SECTION_DIVIDERS_TABLE = "SectionDividersTable"; // new in version 14 (ATT-4055)
        public static final String C_ID = BaseColumns._ID;
        public static final String VIEW_ID = "ViewId";                        // for each sensor field, the id of the tab where it is embedded
        public static final String ROW_ID = "RowId";                          // the ID of each sensor field.  Due to historic reasons, the name is misleading.
        public static final String TITLE = "Title";                           // new in version 14: optional title for section divider
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
        public static final String TEXT_SIZE = "TextSize";        // no longer needed in version 8
        public static final String VIEW_SIZE = "ViewSize";        // new in version 8
        public static final String SOURCE_DEVICE_ID = "SourceDeviceId";  // new in version 5
        public static final String FILTER_TYPE = "FilterType";      // new in version 6
        public static final String FILTER_CONSTANT = "FilterConstant";  // new in version 6  // TODO: add and use these
        // protected static final int SMALL = 20;                 removed in version 8
        // protected static final int MEDIUM = 25;                removed in version 8
        // protected static final int LARGE = 30;                 removed in version 8
        // protected static final int HUGE = 40;                  removed in version 8

        // new in V7 -> fullscreen and day/night
        protected static final String FULL_SCREEN = "FullScreen";        // currently, not used.
        protected static final String SYSTEM_SETTING = "SystemSetting";  // no longer supported
        protected static final String DAY = "Day";                       // no longer supported
        protected static final String NIGHT = "Night";                   // no longer supported

        // new in V9 -> Live Segments
        public static final String SHOW_LIVE_SEGMENTS = "ShowLiveSegments";
        // new in V10 -> Elevation Profile
        public static final String SHOW_ELEVATION_PROFILE = "ShowElevationProfile";
        // new in V12 -> Live Climbs & Navigation Hints (ATT-2360)
        public static final String SHOW_LIVE_CLIMBS = "ShowLiveClimbs";
        public static final String SHOW_NAVIGATION_HINTS = "ShowNavigationHints";
        // new in V13 -> Elevation Profile Scope & Lookahead (ATT-3317)
        public static final String ELEVATION_PROFILE_SCOPE = "ElevationProfileScope";
        public static final String ELEVATION_PROFILE_LOOKAHEAD = "ElevationProfileLookahead";

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
        @Deprecated
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
        protected static final String CREATE_VIEWS_TABLE_V7 = "create table " + VIEWS_TABLE + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                // + VIEW_ID       + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + ACTIVITY_TYPE + " text, "
                + NAME + " text, "
                + LAYOUT_NR + " int, "
                + NEXT_POSITION + " int, "
                + SHOW_LAP_BUTTON + " int, "
                + SHOW_MAP + " int, "
                + FULL_SCREEN + " int, "
                + SYSTEM_SETTING + " int, "
                + DAY + " int, "
                + NIGHT + " int)";
        protected static final String CREATE_VIEWS_TABLE_V9 = "create table " + VIEWS_TABLE + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                // + VIEW_ID       + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + ACTIVITY_TYPE + " text, "
                + NAME + " text, "
                + LAYOUT_NR + " int, "
                + NEXT_POSITION + " int, "
                + SHOW_LAP_BUTTON + " int, "
                + SHOW_MAP + " int, "
                + FULL_SCREEN + " int, "
                + SYSTEM_SETTING + " int, "
                + DAY + " int, "
                + NIGHT + " int, "
                + SHOW_LIVE_SEGMENTS + " int)";

        protected static final String CREATE_VIEWS_TABLE_V10 = "create table " + VIEWS_TABLE + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + ACTIVITY_TYPE + " text, "
                + NAME + " text, "
                + LAYOUT_NR + " int, "
                + NEXT_POSITION + " int, "
                + SHOW_LAP_BUTTON + " int, "
                + SHOW_MAP + " int, "
                + FULL_SCREEN + " int, "
                + SYSTEM_SETTING + " int, "
                + DAY + " int, "
                + NIGHT + " int, "
                + SHOW_LIVE_SEGMENTS + " int, "
                + SHOW_ELEVATION_PROFILE + " int)";

        protected static final String CREATE_VIEWS_TABLE_V12 = "create table " + VIEWS_TABLE + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + ACTIVITY_TYPE + " text, "
                + NAME + " text, "
                + LAYOUT_NR + " int, "
                + NEXT_POSITION + " int, "
                + SHOW_LAP_BUTTON + " int, "
                + SHOW_MAP + " int, "
                + FULL_SCREEN + " int, "
                + SYSTEM_SETTING + " int, "
                + DAY + " int, "
                + NIGHT + " int, "
                + SHOW_LIVE_SEGMENTS + " int, "
                + SHOW_ELEVATION_PROFILE + " int, "
                + SHOW_LIVE_CLIMBS + " int, "
                + SHOW_NAVIGATION_HINTS + " int)";

        protected static final String CREATE_VIEWS_TABLE_V13 = "create table " + VIEWS_TABLE + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + ACTIVITY_TYPE + " text, "
                + NAME + " text, "
                + LAYOUT_NR + " int, "
                + NEXT_POSITION + " int, "
                + SHOW_LAP_BUTTON + " int, "
                + SHOW_MAP + " int, "
                + FULL_SCREEN + " int, "
                + SYSTEM_SETTING + " int, "
                + DAY + " int, "
                + NIGHT + " int, "
                + SHOW_LIVE_SEGMENTS + " int, "
                + SHOW_ELEVATION_PROFILE + " int, "
                + SHOW_LIVE_CLIMBS + " int, "
                + SHOW_NAVIGATION_HINTS + " int, "
                + ELEVATION_PROFILE_SCOPE + " int, "
                + ELEVATION_PROFILE_LOOKAHEAD + " int)";

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
        @Deprecated
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

        protected static final String CREATE_LAYOUTS_TABLE_V8 = "create table " + ROWS_TABLE + " ("
                + ROW_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + VIEW_ID + " int, "
                + ROW_NR + " int, "
                + COL_NR + " int, "
                + SENSOR_TYPE + " text, "
                + TEXT_SIZE + " int, "
                + VIEW_SIZE + " text, "
                + SOURCE_DEVICE_ID + " int, "
                + FILTER_TYPE + " text, "
                + FILTER_CONSTANT + " real)";

        protected static final String CREATE_SECTION_DIVIDERS_TABLE_V14 = "CREATE TABLE IF NOT EXISTS "
                + SECTION_DIVIDERS_TABLE + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + VIEW_ID + " INTEGER NOT NULL, "
                + ROW_NR + " INTEGER NOT NULL, "
                + TITLE + " TEXT, "
                + "UNIQUE(" + VIEW_ID + ", " + ROW_NR + ") ON CONFLICT REPLACE)";

        private final String TAG = TrackingViewsDbHelper.class.getName();
        private final Context mContext;
        private boolean mHavePressureSensor = false;

        // Constructor
        public TrackingViewsDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);

            mContext = context;
            mHavePressureSensor = HavePressureSensor.havePressureSensor(mContext);
        }

        private static void addColumn(@NonNull SQLiteDatabase db, String table, String column, String type) {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type + ";");
        }

        @Override
        public void onCreate(@NonNull SQLiteDatabase db) {
            db.execSQL(CREATE_VIEWS_TABLE_V13);
            db.execSQL(CREATE_LAYOUTS_TABLE_V8);
            db.execSQL(CREATE_SECTION_DIVIDERS_TABLE_V14);

            if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_VIEWS_TABLE_V13);
            if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_LAYOUTS_TABLE_V8);
            if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_SECTION_DIVIDERS_TABLE_V14);


            if (DEBUG) Log.d(TAG, "filling db");

            EnumMap<ActivityType, List<RowData>> viewMap = createViewMap();
            for (ActivityType activityType : viewMap.keySet()) {
                // Standard Tab: layoutNr 1, live segments, live climbs, nav hints
                addDefaultTab(db, activityType, mContext.getString(R.string.text_default), 1, false, false, true, true, true, viewMap.get(activityType));
                if (activityType == ActivityType.BIKE_POWER) {
                    // Dedicated Power Tab: layoutNr 2, nav hints only (REQ-UI-409.2)
                    addDefaultTab(db, activityType, mContext.getString(R.string.tab_power), 2, false, false, false, false, true, getDefaultPowerRowDataList());
                    // Map Tab: layoutNr 3, map, elevation profile, nav hints
                    addDefaultTab(db, activityType, mContext.getString(R.string.tab_map), 3, false, true, false, false, true, getDefaultMapRowDataList(activityType));
                    // Laps Tab: layoutNr 4, lap button only
                    addDefaultTab(db, activityType, mContext.getString(R.string.tab_laps), 4, true, false, false, false, false, getDefaultLapRowDataList(activityType));
                } else {
                    // Map Tab: layoutNr 2, map, elevation profile, nav hints
                    addDefaultTab(db, activityType, mContext.getString(R.string.tab_map), 2, false, true, false, false, true, getDefaultMapRowDataList(activityType));
                    // Laps Tab: layoutNr 3, lap button only
                    addDefaultTab(db, activityType, mContext.getString(R.string.tab_laps), 3, true, false, false, false, false, getDefaultLapRowDataList(activityType));
                }
            }
            if (DEBUG) Log.d(TAG, "filled db");
        }

        /**
         * Non-destructively ensures the dedicated Power tab exists for {@link ActivityType#BIKE_POWER}
         * by querying {@link #VIEWS_TABLE} first and inserting the Power tab at layoutNr = 2 (shifting
         * subsequent BIKE_POWER layouts up by 1) only if absent (REQ-UI-409.2).
         *
         * @param db Writable SQLite database instance.
         */
        public void ensurePowerTabExistsForBikePower(@NonNull SQLiteDatabase db) {
            String powerTabName = mContext != null ? mContext.getString(R.string.tab_power) : null;
            if (powerTabName == null || powerTabName.isEmpty()) {
                powerTabName = "Power";
            }
            db.beginTransaction();
            try {
                boolean exists = false;
                try (Cursor c = db.query(
                        VIEWS_TABLE,
                        new String[]{C_ID},
                        ACTIVITY_TYPE + "=? AND " + NAME + "=?",
                        new String[]{ActivityType.BIKE_POWER.name(), powerTabName},
                        null,
                        null,
                        null
                )) {
                    if (c != null && c.moveToFirst()) {
                        exists = true;
                    }
                }
                if (!exists) {
                    db.execSQL("UPDATE " + VIEWS_TABLE
                            + " SET " + LAYOUT_NR + " = " + LAYOUT_NR + " + 1"
                            + " WHERE " + ACTIVITY_TYPE + " = '" + ActivityType.BIKE_POWER.name() + "'"
                            + " AND " + LAYOUT_NR + " >= 2");
                    addDefaultTab(
                            db,
                            ActivityType.BIKE_POWER,
                            powerTabName,
                            2,
                            false,
                            false,
                            false,
                            false,
                            true,
                            getDefaultPowerRowDataList()
                    );
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        /**
         * Backward-compatible overload for seeding default tracking views with live climbs and navigation hints enabled.
         */
        public long addDefaultTab(@NonNull SQLiteDatabase db, @NonNull ActivityType activityType, String name, int layoutNr, boolean showLapButton, boolean showMap, boolean showLiveSegments, List<RowData> rowDataList) {
            return addDefaultTab(db, activityType, name, layoutNr, showLapButton, showMap, showLiveSegments, true, true, rowDataList);
        }

        /**
         * Seeds a default tracking view tab in SQLite with explicit toggles for live telemetry, segments, climbs, and navigation cues.
         *
         * @param db SQLite database instance.
         * @param activityType Activity type profile.
         * @param name Localized tab title.
         * @param layoutNr Sequential layout index (1 = Standard, 2 = Map/Power, 3 = Laps/Map, 4 = Laps).
         * @param showLapButton Whether the lap split button is visible.
         * @param showMap Whether the map canvas and elevation profile are enabled.
         * @param showLiveSegments Whether Strava live segment bottom sheets are enabled.
         * @param showLiveClimbs Whether Live ClimbPro bottom sheets are enabled.
         * @param showNavigationHints Whether turn prompts and navigation cues are enabled.
         * @param rowDataList Initial sensor grid field layout.
         * @return New view ID in {@link #VIEWS_TABLE}.
         */
        public long addDefaultTab(
                @NonNull SQLiteDatabase db,
                @NonNull ActivityType activityType,
                String name,
                int layoutNr,
                boolean showLapButton,
                boolean showMap,
                boolean showLiveSegments,
                boolean showLiveClimbs,
                boolean showNavigationHints,
                List<RowData> rowDataList
        ) {
            if (DEBUG)
                Log.i(TAG, "addDefaultActivity: activityType=" + activityType + ", layoutNr=" + layoutNr);
            long newViewId = -1;

            ContentValues values = new ContentValues();

            values.put(ACTIVITY_TYPE, activityType.name());
            values.put(NAME, name);
            values.put(LAYOUT_NR, layoutNr);
            values.put(NEXT_POSITION, -1);  // insert an invalid value to indicate that this field is invalid
            values.put(SHOW_LAP_BUTTON, showLapButton ? 1 : 0);
            values.put(SHOW_MAP, showMap ? 1 : 0);
            values.put(SHOW_ELEVATION_PROFILE, showMap ? 1 : 0);
            values.put(FULL_SCREEN, 0);     // default will be to have no fullscreen mode
            values.put(SYSTEM_SETTING, 1);  // default will be to follow the systems settings
            values.put(DAY, 0);
            values.put(NIGHT, 0);
            values.put(SHOW_LIVE_SEGMENTS, showLiveSegments ? 1 : 0);
            values.put(SHOW_LIVE_CLIMBS, showLiveClimbs ? 1 : 0);
            values.put(SHOW_NAVIGATION_HINTS, showNavigationHints ? 1 : 0);
            values.put(ELEVATION_PROFILE_SCOPE, 0);
            values.put(ELEVATION_PROFILE_LOOKAHEAD, 2000);
            newViewId = db.insert(VIEWS_TABLE, null, values);

            for (RowData rowData : rowDataList) {
                if (DEBUG)
                    Log.i(TAG, "adding new row: newViewId=" + newViewId + ", sensorType=" + rowData.sensorType.name());

                values.clear();
                values.put(VIEW_ID, newViewId);
                values.put(ROW_NR, rowData.row);
                values.put(COL_NR, rowData.col);
                values.put(SENSOR_TYPE, rowData.sensorType.name());
                values.put(TEXT_SIZE, 0);                              // no longer needed in version 8
                values.put(VIEW_SIZE, rowData.viewSize.name());
                DefaultFilterConfig filterConfig = SensorFilterDefaults.getDefaultFilterConfig(rowData.sensorType);
                values.put(FILTER_TYPE, filterConfig.getFilterType().name());
                values.put(FILTER_CONSTANT, filterConfig.getFilterConstant());
                db.insert(ROWS_TABLE, null, values);
            }

            return newViewId;
        }

        //Called whenever newVersion != oldVersion
        @Override
        public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {

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
                try (Cursor cursor = db.query(ROWS_TABLE, null, null, null, null, null, null)) {
                    int rowNr = 1;
                    if (cursor != null) {
                        int rowIdCol = cursor.getColumnIndex(ROW_ID);
                        while (cursor.moveToNext()) {
                            long rowId = rowIdCol != -1 ? cursor.getLong(rowIdCol) : -1;
                            contentValues.clear();
                            contentValues.put(ROW_NR, rowNr++);
                            contentValues.put(COL_NR, 1);
                            db.update(ROWS_TABLE, contentValues, ROW_ID + "=?", new String[]{Long.toString(rowId)});
                            db.insert(ROWS_TABLE, null, contentValues);
                        }
                    }
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

            if (oldVersion < 7) { // add fullscreen and day/night
                addColumn(db, VIEWS_TABLE, FULL_SCREEN, "int");
                addColumn(db, VIEWS_TABLE, SYSTEM_SETTING, "int");
                addColumn(db, VIEWS_TABLE, DAY, "int");
                addColumn(db, VIEWS_TABLE, NIGHT, "int");

                ContentValues contentValues = new ContentValues();
                contentValues.put(FULL_SCREEN, 0);
                contentValues.put(SYSTEM_SETTING, 1);
                contentValues.put(DAY, 0);
                contentValues.put(NIGHT, 0);
                db.update(VIEWS_TABLE, contentValues, null, null);
            }

            if (oldVersion < 8) {
                Log.i(TAG, "Upgrading database from version 7 to 8");
                // 1. Add the new VIEW_SIZE column to the ROWS_TABLE
                addColumn(db, ROWS_TABLE, VIEW_SIZE, "text");

                // 2. Use a CASE statement to populate the new column based on the old TEXT_SIZE value
                // This is efficient as it's a single SQL command.
                db.execSQL("UPDATE " + ROWS_TABLE + " SET " + VIEW_SIZE + " = " +
                        "CASE " + TEXT_SIZE + " " +
                        "WHEN 20 THEN 'XSMALL' " +
                        "WHEN 25 THEN 'XSMALL' " +
                        "WHEN 30 THEN 'SMALL' " +
                        "WHEN 35 THEN 'SMALL' " +
                        "WHEN 40 THEN 'NORMAL' " +
                        "WHEN 45 THEN 'NORMAL' " +
                        "WHEN 50 THEN 'LARGE' " +
                        "WHEN 60 THEN 'LARGE' " +
                        "WHEN 70 THEN 'XLARGE' " +
                        "WHEN 80 THEN 'XLARGE' " +
                        "ELSE 'NORMAL' END;"); // Default to 'NORMAL' for any unexpected values.
                Log.i(TAG, "Successfully migrated TEXT_SIZE (int) to VIEW_SIZE (text).");
            }

            if (oldVersion < 9) {
                Log.i(TAG, "Upgrading database from version 8 to 9");

                // add the new SHOW_LIVE_SEGMENTS column
                addColumn(db, VIEWS_TABLE, SHOW_LIVE_SEGMENTS, "int");

                ContentValues contentValues = new ContentValues();
                contentValues.put(SHOW_LIVE_SEGMENTS, 1);
                db.update(VIEWS_TABLE, contentValues, null, null);
            }

            if (oldVersion < 10) {
                Log.i(TAG, "Upgrading database from version 9 to 10");
                try {
                    addColumn(db, VIEWS_TABLE, SHOW_ELEVATION_PROFILE, "int");
                } catch (Exception e) {
                    Log.w(TAG, "Column " + SHOW_ELEVATION_PROFILE + " might already exist.");
                }
                // Synchronize elevation profile default with map visibility
                db.execSQL("UPDATE " + VIEWS_TABLE + " SET " + SHOW_ELEVATION_PROFILE + " = " + SHOW_MAP);
            }

            if (oldVersion < 11) {
                Log.i(TAG, "Upgrading database to version 11: repairing null VIEW_SIZE values");
                try {
                    db.execSQL("UPDATE " + ROWS_TABLE + " SET " + VIEW_SIZE + " = 'NORMAL' WHERE " + VIEW_SIZE + " IS NULL;");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to repair null VIEW_SIZE", e);
                }
            }

            if (oldVersion < 12) {
                Log.i(TAG, "Upgrading database to version 12: Adding Live Climbs and Navigation Hints");
                try {
                    addColumn(db, VIEWS_TABLE, SHOW_LIVE_CLIMBS, "int DEFAULT 1");
                } catch (Exception e) {
                    Log.w(TAG, "Column " + SHOW_LIVE_CLIMBS + " might already exist.", e);
                }
                try {
                    addColumn(db, VIEWS_TABLE, SHOW_NAVIGATION_HINTS, "int DEFAULT 1");
                } catch (Exception e) {
                    Log.w(TAG, "Column " + SHOW_NAVIGATION_HINTS + " might already exist.", e);
                }
                db.execSQL("UPDATE " + VIEWS_TABLE + " SET " + SHOW_LIVE_CLIMBS + " = 1 WHERE " + SHOW_LIVE_CLIMBS + " IS NULL;");
                db.execSQL("UPDATE " + VIEWS_TABLE + " SET " + SHOW_NAVIGATION_HINTS + " = 1 WHERE " + SHOW_NAVIGATION_HINTS + " IS NULL;");
            }

            if (oldVersion < 13) {
                Log.i(TAG, "Upgrading database to version 13: Adding Elevation Profile Scope and Lookahead");
                try {
                    addColumn(db, VIEWS_TABLE, ELEVATION_PROFILE_SCOPE, "int DEFAULT 0");
                } catch (Exception e) {
                    Log.w(TAG, "Column " + ELEVATION_PROFILE_SCOPE + " might already exist.", e);
                }
                try {
                    addColumn(db, VIEWS_TABLE, ELEVATION_PROFILE_LOOKAHEAD, "int DEFAULT 2000");
                } catch (Exception e) {
                    Log.w(TAG, "Column " + ELEVATION_PROFILE_LOOKAHEAD + " might already exist.", e);
                }
                db.execSQL("UPDATE " + VIEWS_TABLE + " SET " + ELEVATION_PROFILE_SCOPE + " = 0 WHERE " + ELEVATION_PROFILE_SCOPE + " IS NULL;");
                db.execSQL("UPDATE " + VIEWS_TABLE + " SET " + ELEVATION_PROFILE_LOOKAHEAD + " = 2000 WHERE " + ELEVATION_PROFILE_LOOKAHEAD + " IS NULL;");
            }

            if (oldVersion < 14) {
                Log.i(TAG, "Upgrading database to version 14: Creating SectionDividersTable");
                try {
                    db.execSQL(CREATE_SECTION_DIVIDERS_TABLE_V14);
                } catch (Exception e) {
                    Log.w(TAG, "SectionDividersTable might already exist.", e);
                }
            }
        }

        /*******************************************************************************************
         * Note that we start to count from 1!
         ******************************************************************************************/

        @NonNull
        public List<RowData> getDefaultStartRowDataList(ActivityType activityType) {
            List<RowData> rowDataList = new LinkedList<>();
            rowDataList.add(new RowData(SensorType.TIME_ACTIVE, ViewSize.SMALL, 1, 1));
            rowDataList.add(new RowData(SensorType.TIME_OF_DAY, ViewSize.SMALL, 1, 2));
            if (activityType == ActivityType.RUN_SPEED_AND_CADENCE) {
                rowDataList.add(new RowData(SensorType.PACE_spm, ViewSize.NORMAL, 2, 1));
            }
            else {
                rowDataList.add(new RowData(SensorType.SPEED_mps, ViewSize.NORMAL, 2, 1));
            }
            rowDataList.add(new RowData(SensorType.DISTANCE_m, ViewSize.NORMAL, 2, 2));
            rowDataList.add(new RowData(SensorType.HR, ViewSize.LARGE, 3, 1));

            return rowDataList;
        }

        /**
         * Builds the dedicated Power tab sensor grid layout for {@link ActivityType#BIKE_POWER} (REQ-UI-409.2).
         *
         * @return Ordered list of {@link RowData} entries for the Power tab.
         */
        @NonNull
        public List<RowData> getDefaultPowerRowDataList() {
            List<RowData> rowDataList = new LinkedList<>();
            // Row 1: TIME_ACTIVE (SMALL, col 1), HR (SMALL, col 2), CADENCE (SMALL, col 3)
            rowDataList.add(new RowData(SensorType.TIME_ACTIVE, ViewSize.SMALL, 1, 1));
            rowDataList.add(new RowData(SensorType.HR, ViewSize.SMALL, 1, 2));
            rowDataList.add(new RowData(SensorType.CADENCE, ViewSize.SMALL, 1, 3));
            // Row 2: POWER (HUGE, col 1)
            rowDataList.add(new RowData(SensorType.POWER, ViewSize.HUGE, 2, 1));
            // Row 3: POWER_TO_WEIGHT (LARGE, col 1), NORMALIZED_POWER (LARGE, col 2)
            rowDataList.add(new RowData(SensorType.POWER_TO_WEIGHT, ViewSize.LARGE, 3, 1));
            rowDataList.add(new RowData(SensorType.NORMALIZED_POWER, ViewSize.LARGE, 3, 2));
            // Row 4: INTENSITY_FACTOR (NORMAL, col 1), TSS (NORMAL, col 2), WORK_KJ (NORMAL, col 3)
            rowDataList.add(new RowData(SensorType.INTENSITY_FACTOR, ViewSize.NORMAL, 4, 1));
            rowDataList.add(new RowData(SensorType.TSS, ViewSize.NORMAL, 4, 2));
            rowDataList.add(new RowData(SensorType.WORK_KJ, ViewSize.NORMAL, 4, 3));
            // Row 5: W_PRIME_BAL (LARGE, col 1)
            rowDataList.add(new RowData(SensorType.W_PRIME_BAL, ViewSize.LARGE, 5, 1));
            // Row 6: PEDAL_POWER_BALANCE (SMALL, col 1), TORQUE (SMALL, col 2)
            rowDataList.add(new RowData(SensorType.PEDAL_POWER_BALANCE, ViewSize.SMALL, 6, 1));
            rowDataList.add(new RowData(SensorType.TORQUE, ViewSize.SMALL, 6, 2));
            return rowDataList;
        }

        /**
         * Builds the default Map tab sensor grid header for the given {@link ActivityType} (REQ-UI-409.2).
         *
         * @param activityType Activity type profile.
         * @return Ordered list of {@link RowData} entries for the Map tab.
         */
        @NonNull
        public List<RowData> getDefaultMapRowDataList(ActivityType activityType) {
            if (activityType == ActivityType.BIKE_POWER) {
                List<RowData> rowDataList = new LinkedList<>();
                // Row 1: TIME_ACTIVE (NORMAL, col 1), DISTANCE_m (NORMAL, col 2)
                rowDataList.add(new RowData(SensorType.TIME_ACTIVE, ViewSize.NORMAL, 1, 1));
                rowDataList.add(new RowData(SensorType.DISTANCE_m, ViewSize.NORMAL, 1, 2));
                // Row 2: SPEED_mps (NORMAL, col 1), HR (NORMAL, col 2)
                rowDataList.add(new RowData(SensorType.SPEED_mps, ViewSize.NORMAL, 2, 1));
                rowDataList.add(new RowData(SensorType.HR, ViewSize.NORMAL, 2, 2));
                // Row 3: POWER (NORMAL, col 1), POWER_TO_WEIGHT (NORMAL, col 2)
                rowDataList.add(new RowData(SensorType.POWER, ViewSize.NORMAL, 3, 1));
                rowDataList.add(new RowData(SensorType.POWER_TO_WEIGHT, ViewSize.NORMAL, 3, 2));
                return rowDataList;
            }
            return getDefaultStartRowDataList(activityType);
        }

        @NonNull
        public List<RowData> getDefaultLapRowDataList(ActivityType activityType) {
            if (activityType == ActivityType.BIKE_POWER) {
                List<RowData> rowDataList = new LinkedList<>();
                // Row 1: TIME_LAP (LARGE, col 1), LAP_NR (SMALL, col 2)
                rowDataList.add(new RowData(SensorType.TIME_LAP, ViewSize.LARGE, 1, 1));
                rowDataList.add(new RowData(SensorType.LAP_NR, ViewSize.SMALL, 1, 2));
                // Row 2: SPEED_mps (NORMAL, col 1), DISTANCE_m_LAP (NORMAL, col 2)
                rowDataList.add(new RowData(SensorType.SPEED_mps, ViewSize.NORMAL, 2, 1));
                rowDataList.add(new RowData(SensorType.DISTANCE_m_LAP, ViewSize.NORMAL, 2, 2));
                // Row 3: HR (NORMAL, col 1), CADENCE (NORMAL, col 2)
                rowDataList.add(new RowData(SensorType.HR, ViewSize.NORMAL, 3, 1));
                rowDataList.add(new RowData(SensorType.CADENCE, ViewSize.NORMAL, 3, 2));
                // Row 4: POWER (LARGE, col 1), POWER_TO_WEIGHT (NORMAL, col 2)
                rowDataList.add(new RowData(SensorType.POWER, ViewSize.LARGE, 4, 1));
                rowDataList.add(new RowData(SensorType.POWER_TO_WEIGHT, ViewSize.NORMAL, 4, 2));
                // Row 5: ALTITUDE (NORMAL, col 1), ASCENT (NORMAL, col 2)
                rowDataList.add(new RowData(SensorType.ALTITUDE, ViewSize.NORMAL, 5, 1));
                rowDataList.add(new RowData(SensorType.ASCENT, ViewSize.NORMAL, 5, 2));
                return rowDataList;
            }

            List<RowData> rowDataList = new LinkedList<>();
            rowDataList.add(new RowData(SensorType.TIME_LAP, ViewSize.SMALL, 1, 1));
            rowDataList.add(new RowData(SensorType.LAP_NR, ViewSize.SMALL, 1, 2));
            if (activityType == ActivityType.RUN_SPEED_AND_CADENCE) {
                rowDataList.add(new RowData(SensorType.PACE_spm, ViewSize.NORMAL, 2, 1));
            }
            else {
                rowDataList.add(new RowData(SensorType.SPEED_mps, ViewSize.NORMAL, 2, 1));
            }
            rowDataList.add(new RowData(SensorType.DISTANCE_m_LAP, ViewSize.NORMAL, 2, 2));
            rowDataList.add(new RowData(SensorType.HR, ViewSize.LARGE, 3, 1));

            switch (activityType) {
                case GENERIC_HR -> {
                    rowDataList.add(new RowData(SensorType.ALTITUDE, ViewSize.NORMAL, 4, 1));
                    rowDataList.add(new RowData(SensorType.ASCENT, ViewSize.NORMAL, 4, 2));
                }
                case RUN_SPEED_AND_CADENCE -> {
                    rowDataList.add(new RowData(SensorType.CADENCE, ViewSize.LARGE, 4, 1));
                    rowDataList.add(new RowData(SensorType.ALTITUDE, ViewSize.NORMAL, 5, 1));
                    rowDataList.add(new RowData(SensorType.ASCENT, ViewSize.NORMAL, 5, 2));
                }
                case BIKE_SPEED_AND_CADENCE -> {
                    rowDataList.add(new RowData(SensorType.CADENCE, ViewSize.LARGE, 4, 1));
                    rowDataList.add(new RowData(SensorType.ALTITUDE, ViewSize.NORMAL, 5, 1));
                    rowDataList.add(new RowData(SensorType.ASCENT, ViewSize.NORMAL, 5, 2));
                }
                case BIKE_POWER -> {
                    // Handled above
                }
            }

            return rowDataList;
        }

        @NonNull
        public EnumMap<ActivityType, List<RowData>> createViewMap() {
            if (DEBUG) Log.d(TAG, "createViewMap");
            EnumMap<ActivityType, List<RowData>> viewMap = new EnumMap<>(ActivityType.class);
            List<RowData> rowDataList;

            // GENERIC_HR
            rowDataList = getDefaultStartRowDataList(ActivityType.GENERIC_HR);
            rowDataList.add(new RowData(SensorType.ALTITUDE, ViewSize.NORMAL, 4, 1));
            rowDataList.add(new RowData(SensorType.ASCENT, ViewSize.NORMAL, 4, 2));
            if (mHavePressureSensor) {
                // TODO: add slope and VAM when this is working properly
            }
            viewMap.put(ActivityType.GENERIC_HR, rowDataList);

            // RUN_SPEED_AND_CADENCE
            rowDataList = getDefaultStartRowDataList(ActivityType.RUN_SPEED_AND_CADENCE);
            rowDataList.add(new RowData(SensorType.CADENCE, ViewSize.LARGE, 4, 1));
            rowDataList.add(new RowData(SensorType.ALTITUDE, ViewSize.NORMAL, 5, 1));
            rowDataList.add(new RowData(SensorType.ASCENT, ViewSize.NORMAL, 5, 2));
            if (mHavePressureSensor) {
                // TODO: add slope and VAM when this is working properly
            }
            viewMap.put(ActivityType.RUN_SPEED_AND_CADENCE, rowDataList);

            // BIKE_SPEED_AND_CADENCE
            rowDataList = getDefaultStartRowDataList(ActivityType.BIKE_SPEED_AND_CADENCE);
            rowDataList.add(new RowData(SensorType.CADENCE, ViewSize.LARGE, 4, 1));
            rowDataList.add(new RowData(SensorType.ALTITUDE, ViewSize.NORMAL, 5, 1));
            rowDataList.add(new RowData(SensorType.ASCENT, ViewSize.NORMAL, 5, 2));
            if (mHavePressureSensor) {
                // TODO: add slope and VAM when this is working properly
            }
            viewMap.put(ActivityType.BIKE_SPEED_AND_CADENCE, rowDataList);

            // BIKE_POWER (REQ-UI-409.2 — 6-row Standard layout)
            rowDataList = new LinkedList<>();
            // Row 1: TIME_ACTIVE (SMALL, col 1), TIME_OF_DAY (SMALL, col 2)
            rowDataList.add(new RowData(SensorType.TIME_ACTIVE, ViewSize.SMALL, 1, 1));
            rowDataList.add(new RowData(SensorType.TIME_OF_DAY, ViewSize.SMALL, 1, 2));
            // Row 2: SPEED_mps (NORMAL, col 1), DISTANCE_m (NORMAL, col 2)
            rowDataList.add(new RowData(SensorType.SPEED_mps, ViewSize.NORMAL, 2, 1));
            rowDataList.add(new RowData(SensorType.DISTANCE_m, ViewSize.NORMAL, 2, 2));
            // Row 3: HR (LARGE, col 1), CADENCE (LARGE, col 2)
            rowDataList.add(new RowData(SensorType.HR, ViewSize.LARGE, 3, 1));
            rowDataList.add(new RowData(SensorType.CADENCE, ViewSize.LARGE, 3, 2));
            // Row 4: POWER (HUGE, col 1)
            rowDataList.add(new RowData(SensorType.POWER, ViewSize.HUGE, 4, 1));
            // Row 5: POWER_TO_WEIGHT (NORMAL, col 1), NORMALIZED_POWER (NORMAL, col 2)
            rowDataList.add(new RowData(SensorType.POWER_TO_WEIGHT, ViewSize.NORMAL, 5, 1));
            rowDataList.add(new RowData(SensorType.NORMALIZED_POWER, ViewSize.NORMAL, 5, 2));
            // Row 6: ALTITUDE (NORMAL, col 1), ASCENT (NORMAL, col 2)
            rowDataList.add(new RowData(SensorType.ALTITUDE, ViewSize.NORMAL, 6, 1));
            rowDataList.add(new RowData(SensorType.ASCENT, ViewSize.NORMAL, 6, 2));
            if (mHavePressureSensor) {
                // TODO: add slope and VAM when this is working properly
            }
            viewMap.put(ActivityType.BIKE_POWER, rowDataList);

            return viewMap;
        }

        public record RowData(SensorType sensorType, ViewSize viewSize, int row, int col) {

        }

    }
}
