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

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;

// DB Helper to store which ANT Devices were active at a certain workout
public class ActiveDevicesDbHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "ActiveDevices.db";
    public static final int DB_VERSION = 1;
    private static final String TAG = "ActiveDevicesDbHelper";
    private static final boolean DEBUG = false;

    // Constructor
    public ActiveDevicesDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    // Called only once, first time the DB is created
    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL(ActiveDevices.CREATE_TABLE);

        if (DEBUG) Log.d(TAG, "onCreated sql: " + ActiveDevices.CREATE_TABLE);
    }

    //Called whenever newVersion != oldVersion
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO: alter table instead of deleting!

        db.execSQL("drop table if exists " + ActiveDevices.TABLE);

        if (DEBUG) Log.d(TAG, "onUpgraded");
        onCreate(db);  // run onCreate to get new database
    }

    public List<Integer> getDatabaseIdsOfActiveDevices(int workoutId) {
        if (DEBUG) Log.d(TAG, "getDatabaseIdsOfActiveDevices workoutId=" + workoutId);

        List<Integer> result = new LinkedList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(ActiveDevices.TABLE,
                null,
                ActiveDevices.WORKOUT_ID + "=?",
                new String[]{workoutId + ""},
                null,
                null,
                null);

        if (cursor.moveToFirst()) {
            int index = cursor.getColumnIndex(ActiveDevices.DEVICE_DB_ID);
            if (index >= 0) {
                do {
                    if (DEBUG)
                        Log.d(TAG, "adding " + cursor.getInt(index));
                    result.add(cursor.getInt(index));
                } while (cursor.moveToNext());
            }
        }

        cursor.close();
        db.close();

        return result;
    }


    public static final class ActiveDevices {
        public static final String TABLE = "ActiveDevices";

        public static final String C_ID = BaseColumns._ID;
        public static final String WORKOUT_ID = "workoutID";
        public static final String DEVICE_DB_ID = "deviceDbId";

        private static final String CREATE_TABLE = "create table " + TABLE + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + WORKOUT_ID + " int,"
                + DEVICE_DB_ID + " int)";

    }

}
