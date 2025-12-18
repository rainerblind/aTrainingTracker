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

import com.atrainingtracker.banalservice.BSportType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

// import com.google.common.collect.Sets;

public class EquipmentDbHelper extends SQLiteOpenHelper {
    // 1 -> mtb, 2 -> cross, 3 -> road, 4 -> time trial
    public static final int MTB = 1;
    public static final int CROSS = 2;
    public static final int ROAD = 3;
    public static final int TT = 4;
    // The different tables
    public static final String EQUIPMENT = "Equipment";
    public static final String LINKS = "Links";
    // columns
    public static final String C_ID = BaseColumns._ID;
    public static final String EQUIPMENT_ID = "EquipmentId";
    public static final String NAME = "Name";
    public static final String SPORT_TYPE = "SportType";
    public static final String STRAVA_NAME = "StravaName";
    public static final String STRAVA_ID = "StravaId";
    public static final String FRAME_TYPE = "FrameType";
    public static final String ANT_DEVICE_ID = "ANTDeviceId";
    static final String DB_NAME = "Equipment.db";
    static final int DB_VERSION = 1;
    private static final String TAG = "EquipmentDbHelper";
    private static final boolean DEBUG = false;
    private static final String CREATE_EQUIPMENT_TABLE = "create table " + EQUIPMENT + " ("
            + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + NAME + " text,"
            + SPORT_TYPE + " text,"
            + FRAME_TYPE + " int,"
            + STRAVA_NAME + " text,"
            + STRAVA_ID + " text)";
    private static final String CREATE_LINKS_TABLE = "create table " + LINKS + " ("
            // + C_ID          + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + EQUIPMENT_ID + " int,"
            + ANT_DEVICE_ID + " int)";
    private final Context mContext;

    // Constructor
    public EquipmentDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);

        mContext = context;
    }

    public List<String> getLinkedEquipment(int workoutId) {
        if (DEBUG) Log.d(TAG, "getLinkedEquipment, workoutId=" + workoutId);

        return getLinkedEquipment(new ActiveDevicesDbHelper(mContext).getDatabaseIdsOfActiveDevices(workoutId));
    }

    protected List<String> getLinkedEquipment(List<Integer> antDeviceIds) {
        if (DEBUG) Log.d(TAG, "getLinkedEquipment with antDeviceList");

        if (antDeviceIds == null || antDeviceIds.size() == 0) {
            return new ArrayList<String>();
        }

        Set<String> linkedEquipment = new HashSet<String>();

        int i = 0;
        // find first device with linked equipment
        for (; i < antDeviceIds.size(); i++) {
            List<String> tmpEquipment = getLinkedEquipmentFromDeviceId(antDeviceIds.get(i));
            if (tmpEquipment.size() != 0) {
                if (DEBUG)
                    Log.d(TAG, "found device with linked equipment: " + i + ", " + antDeviceIds.get(i));
                linkedEquipment = new HashSet<String>(tmpEquipment);
                break;
            } else {
                if (DEBUG) Log.d(TAG, "no linked equipment for " + i + ", " + antDeviceIds.get(i));
            }
        }

        // for the rest of the devices, we do a set intersectionL
        for (; i < antDeviceIds.size(); i++) {
            List<String> tmpEquipment = getLinkedEquipmentFromDeviceId(antDeviceIds.get(i));
            if (tmpEquipment.size() != 0) {
                linkedEquipment.retainAll(new HashSet<String>(tmpEquipment));
                if (DEBUG) Log.d(TAG, "did set intersection for " + i + ", " + antDeviceIds.get(i));
            } else {
                if (DEBUG) Log.d(TAG, "no linked equipment for " + i + ", " + antDeviceIds.get(i));
            }
        }

        return new ArrayList<String>(linkedEquipment);
    }

    public List<String> getEquipment(BSportType sportType) {
        return getEquipment(sportType, 0);
    }


    public List<String> getEquipment(BSportType sportType, int frameType) {
        if (DEBUG)
            Log.d(TAG, "getEquipment, sportType=" + sportType.name() + "frameType=" + frameType);

        List<String> equipmentList = new LinkedList<String>();

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor;
        if (frameType == 0) {
            cursor = db.query(EQUIPMENT,
                    null,
                    SPORT_TYPE + "=?",
                    new String[]{sportType.name()},
                    null,
                    null,
                    null);
        } else {
            cursor = db.query(EQUIPMENT,
                    null,
                    FRAME_TYPE + "=? AND " + SPORT_TYPE + "=?",
                    new String[]{Long.toString(frameType), sportType.name()},
                    null,
                    null,
                    null);
        }

        if (cursor.moveToFirst()) {
            do {
                if (DEBUG)
                    Log.d(TAG, "adding " + cursor.getString(cursor.getColumnIndex(NAME)) + " to equipment list");

                equipmentList.add(cursor.getString(cursor.getColumnIndex(NAME)));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return equipmentList;
    }

    public String getLinkedEquipmentStringFromDeviceId(long deviceId) {
        String equipment = null;
        List<String> equipmentList = getLinkedEquipmentFromDeviceId(deviceId);
        if (equipmentList.size() > 0) {
            equipment = equipmentList.toString().replace("[", "").replace("]", "");
        }

        return equipment;
    }

    public List<String> getLinkedEquipmentFromDeviceId(long deviceId) {
        if (DEBUG) Log.d(TAG, "getLinkedEquipmentFromDeviceId: " + deviceId);

        List<String> equipmentList = new LinkedList<String>();

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor linkCursor = db.query(LINKS,
                null,
                ANT_DEVICE_ID + "=?",
                new String[]{Long.toString(deviceId)},
                null,
                null,
                null);

        if (linkCursor.moveToFirst()) {
            if (DEBUG) Log.d(TAG, "got some linked equipment");
            do {
                int equipmentId = linkCursor.getInt(linkCursor.getColumnIndex(EQUIPMENT_ID));

                Cursor equipmentCursor = db.query(EQUIPMENT,
                        null,
                        C_ID + "=?",
                        new String[]{Long.toString(equipmentId)},
                        null,
                        null,
                        null);
                if (equipmentCursor.moveToFirst()) {
                    if (DEBUG)
                        Log.d(TAG, "adding " + equipmentCursor.getString(equipmentCursor.getColumnIndex(NAME)));
                    equipmentList.add(equipmentCursor.getString(equipmentCursor.getColumnIndex(NAME)));
                } else {
                    Log.e(TAG, "ERROR: more than one name to one equipment id!");
                }
                equipmentCursor.close();

            } while (linkCursor.moveToNext());
        }

        linkCursor.close();
        db.close();

        return equipmentList;
    }


    public void setEquipmentLinks(int antDeviceId, List<String> equipmentList) {
        if (DEBUG) Log.d(TAG, "setEquipmentLinks: " + antDeviceId);

        // first of all, delete all existing links
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(LINKS, ANT_DEVICE_ID + "=?", new String[]{antDeviceId + ""});

        // insert new links
        ContentValues values = new ContentValues();
        values.put(ANT_DEVICE_ID, antDeviceId);

        for (String equipmentName : equipmentList) {

            if (DEBUG)
                Log.d(TAG, "save " + equipmentName + ", id=" + getEquipmentId(db, equipmentName));
            // ContentValues values = new ContentValues();
            values.put(EQUIPMENT_ID, getEquipmentId(db, equipmentName));
            db.insert(LINKS, null, values);
        }

        db.close();
        if (DEBUG) Log.d(TAG, "inserted");
    }

    private int getEquipmentId(SQLiteDatabase db, String equipmentName) {
        equipmentName.equals("");  // throw an exception when equipmentName is null

        int equipmentId = -1;

        Cursor cursor = db.query(EQUIPMENT, null, NAME + "=?", new String[]{equipmentName}, null, null, null);
        if (cursor.moveToFirst()) {
            equipmentId = cursor.getInt(cursor.getColumnIndex(C_ID));
        } else {
            Log.e(TAG, "ERROR: in getEquipmentId: no id to name: " + equipmentName);
        }
        cursor.close();

        return equipmentId;
    }

    public int getEquipmentId(String equipmentName) {
        SQLiteDatabase db = this.getReadableDatabase();
        int equipmentId = getEquipmentId(db, equipmentName);
        db.close();
        return equipmentId;
    }

    public String getEquipmentFromId(int equipmentId) {
        String equipmentName = null;

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(EQUIPMENT, null, C_ID + "=?", new String[]{Long.toString(equipmentId)}, null, null, null);

        if (cursor.moveToFirst()) {
            equipmentName = cursor.getString(cursor.getColumnIndex(NAME));
        } else {
            Log.e(TAG, "ERROR: in getEquipmentFromId: no name for id: " + equipmentId);
        }

        cursor.close();
        db.close();

        return equipmentName;
    }

    public String getStravaIdFromId(int equipmentId) {
        String stravaId = null;

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(EQUIPMENT, null, C_ID + "=?", new String[]{Long.toString(equipmentId)}, null, null, null);

        if (cursor.moveToFirst()) {
            stravaId = cursor.getString(cursor.getColumnIndex(STRAVA_ID));
        } else {
            Log.e(TAG, "ERROR: in getStravaIdFromId: no stravaId for id: " + equipmentId);
        }

        cursor.close();
        db.close();

        return stravaId;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_EQUIPMENT_TABLE);
        db.execSQL(CREATE_LINKS_TABLE);

        if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_EQUIPMENT_TABLE);
        if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_LINKS_TABLE);

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO: alter table instead of deleting!

        db.execSQL("drop table if exists " + EQUIPMENT);  // drops the old database
        db.execSQL("drop table if exists " + LINKS);  // drops the old database

        if (DEBUG) Log.d(TAG, "onUpgraded");
        onCreate(db);  // run onCreate to get new database

    }

}
