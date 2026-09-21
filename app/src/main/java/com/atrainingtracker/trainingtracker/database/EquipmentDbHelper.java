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

import com.atrainingtracker.banalservice.BSportType;

import java.util.ArrayList;
import java.util.Collections;
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
    public static final String RETIRED = "Retired";
    static final String DB_NAME = "Equipment.db";
    static final int DB_VERSION = 2;
    private static final String TAG = "EquipmentDbHelper";
    private static final boolean DEBUG = true;
    private static final String CREATE_EQUIPMENT_TABLE = "create table " + EQUIPMENT + " ("
            + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + NAME + " text,"
            + SPORT_TYPE + " text,"
            + FRAME_TYPE + " int,"
            + STRAVA_NAME + " text,"
            + STRAVA_ID + " text,"
            + RETIRED + " int default 0)";
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

    @NonNull
    public List<String> getLinkedEquipment(long workoutId) {
        if (DEBUG) Log.d(TAG, "getLinkedEquipment, workoutId=" + workoutId);

        return getLinkedEquipment(new ActiveDevicesDbHelper(mContext).getDatabaseIdsOfActiveDevices(workoutId));
    }

    @NonNull
    protected List<String> getLinkedEquipment(@Nullable List<Long> antDeviceIds) {
        if (DEBUG) Log.d(TAG, "getLinkedEquipment with antDeviceList");

        if (antDeviceIds == null || antDeviceIds.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> linkedEquipment = new HashSet<>();

        int i = 0;
        // find first device with linked equipment
        for (; i < antDeviceIds.size(); i++) {
            List<String> tmpEquipment = getLinkedEquipmentFromDeviceId(antDeviceIds.get(i));
            if (!tmpEquipment.isEmpty()) {
                if (DEBUG)
                    Log.d(TAG, "found device with linked equipment: " + i + ", " + antDeviceIds.get(i));
                linkedEquipment = new HashSet<>(tmpEquipment);
                break;
            } else {
                if (DEBUG) Log.d(TAG, "no linked equipment for " + i + ", " + antDeviceIds.get(i));
            }
        }

        // for the rest of the devices, we do a set intersectionL
        for (; i < antDeviceIds.size(); i++) {
            List<String> tmpEquipment = getLinkedEquipmentFromDeviceId(antDeviceIds.get(i));
            if (!tmpEquipment.isEmpty()) {
                linkedEquipment.retainAll(new HashSet<>(tmpEquipment));
                if (DEBUG) Log.d(TAG, "did set intersection for " + i + ", " + antDeviceIds.get(i));
            } else {
                if (DEBUG) Log.d(TAG, "no linked equipment for " + i + ", " + antDeviceIds.get(i));
            }
        }

        return new ArrayList<>(linkedEquipment);
    }

    @NonNull
    public List<String> getEquipment(@NonNull BSportType sportType) {
        return getEquipment(sportType, 0);
    }


    @NonNull
    public List<String> getEquipment(@NonNull BSportType sportType, int frameType) {
        if (DEBUG)
            Log.d(TAG, "getEquipment, sportType=" + sportType.name() + "frameType=" + frameType);

        List<String> equipmentList = new LinkedList<>();

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor;
        if (sportType == BSportType.UNKNOWN) {
            cursor = db.query(EQUIPMENT,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }
        else if (frameType == 0) {
            cursor = db.query(EQUIPMENT,
                    null,
                    SPORT_TYPE + "=?",
                    new String[]{sportType.name()},
                    null,
                    null,
                    null);
        }
        else {
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

        return equipmentList;
    }

    /**
     * Updates an existing equipment entry and synchronizes its linked sensors.
     *
     * @param id              The ID of the equipment to update.
     * @param name            The new name.
     * @param frameType       The new frame type (1-4 for bikes, 0 for others).
     * @param linkedDeviceIds The new list of sensor IDs to link to this equipment.
     */
    public void updateEquipment(long id, String name, int frameType, @NonNull List<Long> linkedDeviceIds, boolean isRetired) {
        if (id <= 0) {
            return;
        }
        SQLiteDatabase db = this.getWritableDatabase();

        // Start a transaction to ensure database integrity
        db.beginTransaction();
        try {
            // 1. Update the Equipment table
            ContentValues values = new ContentValues();
            values.put(NAME, name);
            values.put(FRAME_TYPE, frameType);
            values.put(RETIRED, isRetired ? 1 : 0);
            db.update(EQUIPMENT, values, C_ID + "=?", new String[]{String.valueOf(id)});

            // 2. Clear existing links for this equipment
            db.delete(LINKS, EQUIPMENT_ID + "=?", new String[]{String.valueOf(id)});

            // 3. Insert new links
            for (Long deviceId : linkedDeviceIds) {
                ContentValues linkValues = new ContentValues();
                linkValues.put(EQUIPMENT_ID, id);
                linkValues.put(ANT_DEVICE_ID, deviceId);
                db.insert(LINKS, null, linkValues);
            }

            // Mark transaction as successful
            db.setTransactionSuccessful();
            if (DEBUG) Log.d(TAG, "Successfully updated equipment " + id + " with " + linkedDeviceIds.size() + " sensors, isRetired=" + isRetired);
        } catch (Exception e) {
            Log.e(TAG, "Error updating equipment links: " + e.getMessage());
        } finally {
            db.endTransaction();
        }
    }

    public void updateEquipment(long id, String name, int frameType, @NonNull List<Long> linkedDeviceIds) {
        updateEquipment(id, name, frameType, linkedDeviceIds, isEquipmentRetired(id));
    }

    public void setEquipmentRetired(long id, boolean isRetired) {
        if (id <= 0) {
            return;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(RETIRED, isRetired ? 1 : 0);
        db.update(EQUIPMENT, values, C_ID + "=?", new String[]{String.valueOf(id)});
    }

    public boolean isEquipmentRetired(long id) {
        if (id <= 0) {
            return false;
        }
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.query(EQUIPMENT, new String[]{RETIRED}, C_ID + "=?", new String[]{String.valueOf(id)}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int colIdx = cursor.getColumnIndex(RETIRED);
                return colIdx != -1 && cursor.getInt(colIdx) == 1;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking isEquipmentRetired: " + id, e);
        }
        return false;
    }

    /**
     * Deletes equipment and its associated sensor links in one transaction.
     */
    public void deleteEquipment(long id) {
        if (id <= 0) {
            return;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(LINKS, EQUIPMENT_ID + "=?", new String[]{String.valueOf(id)});
            db.delete(EQUIPMENT, C_ID + "=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }


    /**
     * Data class for Equipment
     */
    public static class EquipmentData {
        public final long id;
        public final String name;
        public final BSportType sportType;
        public final int frameType;
        public final String stravaName;
        public final String stravaId;
        public final boolean isRetired;

        public EquipmentData(long id, String name, BSportType sportType, int frameType, String stravaName, String stravaId, boolean isRetired) {
            this.id = id;
            this.name = name;
            this.sportType = sportType;
            this.frameType = frameType;
            this.stravaName = stravaName;
            this.stravaId = stravaId;
            this.isRetired = isRetired;
        }

        public EquipmentData(long id, String name, BSportType sportType, int frameType, String stravaName, String stravaId) {
            this(id, name, sportType, frameType, stravaName, stravaId, false);
        }
    }

    /**
     * New method to get all Equipment IDs linked to a specific sport type
     */
    @NonNull
    public List<EquipmentData> getEquipmentItems(@NonNull BSportType sportType) {
        return getEquipmentItems(sportType, false);
    }

    @NonNull
    public List<EquipmentData> getEquipmentItems(@NonNull BSportType sportType, boolean activeOnly) {
        if (DEBUG) Log.d(TAG, "getEquipmentItems, sportType=" + sportType.name() + ", activeOnly=" + activeOnly);

        List<EquipmentData> itemList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String selection = SPORT_TYPE + "=?";
        if (activeOnly) {
            selection += " AND (" + RETIRED + " IS NULL OR " + RETIRED + "=0)";
        }

        Cursor cursor = db.query(EQUIPMENT,
                new String[]{C_ID, NAME, SPORT_TYPE, FRAME_TYPE, STRAVA_NAME, STRAVA_ID, RETIRED},
                selection,
                new String[]{sportType.name()},
                null, null, null);

        if (cursor.moveToFirst()) {
            int idIdx = cursor.getColumnIndex(C_ID);
            int nameIdx = cursor.getColumnIndex(NAME);
            int sportIdx = cursor.getColumnIndex(SPORT_TYPE);
            int frameIdx = cursor.getColumnIndex(FRAME_TYPE);
            int stravaNameIdx = cursor.getColumnIndex(STRAVA_NAME);
            int stravaIdIdx = cursor.getColumnIndex(STRAVA_ID);
            int retiredIdx = cursor.getColumnIndex(RETIRED);

            do {
                boolean isRetired = retiredIdx != -1 && cursor.getInt(retiredIdx) == 1;
                itemList.add(new EquipmentData(
                        cursor.getLong(idIdx),
                        cursor.getString(nameIdx),
                        BSportType.valueOf(cursor.getString(sportIdx)),
                        cursor.getInt(frameIdx),
                        cursor.getString(stravaNameIdx),
                        cursor.getString(stravaIdIdx),
                        isRetired
                ));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return itemList;
    }

    // Get list of all equipment
    @NonNull
    public List<EquipmentData> getEquipmentItems() {
        return getEquipmentItems(false);
    }

    @NonNull
    public List<EquipmentData> getEquipmentItems(boolean activeOnly) {
        if (DEBUG) Log.d(TAG, "getEquipmentItems, activeOnly=" + activeOnly);

        List<EquipmentData> itemList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String selection = activeOnly ? ("(" + RETIRED + " IS NULL OR " + RETIRED + "=0)") : null;

        Cursor cursor = db.query(EQUIPMENT,
                new String[]{C_ID, NAME, SPORT_TYPE, FRAME_TYPE, STRAVA_NAME, STRAVA_ID, RETIRED},
                selection,
                null,
                null, null, null);

        if (cursor.moveToFirst()) {
            int idIdx = cursor.getColumnIndex(C_ID);
            int nameIdx = cursor.getColumnIndex(NAME);
            int sportIdx = cursor.getColumnIndex(SPORT_TYPE);
            int frameIdx = cursor.getColumnIndex(FRAME_TYPE);
            int stravaNameIdx = cursor.getColumnIndex(STRAVA_NAME);
            int stravaIdIdx = cursor.getColumnIndex(STRAVA_ID);
            int retiredIdx = cursor.getColumnIndex(RETIRED);

            do {
                boolean isRetired = retiredIdx != -1 && cursor.getInt(retiredIdx) == 1;
                itemList.add(new EquipmentData(
                        cursor.getLong(idIdx),
                        cursor.getString(nameIdx),
                        BSportType.valueOf(cursor.getString(sportIdx)),
                        cursor.getInt(frameIdx),
                        cursor.getString(stravaNameIdx),
                        cursor.getString(stravaIdIdx),
                        isRetired
                ));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return itemList;
    }

    /**
     * New method to get all ANT device IDs linked to a specific Equipment ID
     */
    @NonNull
    public List<Long> getDeviceIdsForEquipment(long equipmentId) {
        if (equipmentId <= 0) {
            return Collections.emptyList();
        }
        List<Long> deviceIds = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(LINKS,
                new String[]{ANT_DEVICE_ID},
                EQUIPMENT_ID + "=?",
                new String[]{Long.toString(equipmentId)},
                null, null, null);

        if (cursor.moveToFirst()) {
            do {
                deviceIds.add(cursor.getLong(0));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return deviceIds;
    }

    @Nullable
    public String getLinkedEquipmentStringFromDeviceId(long deviceId) {
        String equipment = null;
        List<String> equipmentList = getLinkedEquipmentFromDeviceId(deviceId);
        if (!equipmentList.isEmpty()) {
            equipment = equipmentList.toString().replace("[", "").replace("]", "");
        }

        return equipment;
    }

    @NonNull
    public List<String> getLinkedEquipmentFromDeviceId(long deviceId) {
        if (DEBUG) Log.d(TAG, "getLinkedEquipmentFromDeviceId: " + deviceId);

        List<String> equipmentList = new LinkedList<>();

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

        return equipmentList;
    }

    public List<Long> getLinkedEquipmentIdsFromDeviceId(long deviceId) {
        if (DEBUG) Log.d(TAG, "getLinkedEquipmentIdsFromDeviceId: " + deviceId);

        List<Long> equipmentList = new LinkedList<>();

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
                equipmentList.add(linkCursor.getLong(linkCursor.getColumnIndex(EQUIPMENT_ID)));

            } while (linkCursor.moveToNext());
        }
        linkCursor.close();

        return equipmentList;
    }



    public void setEquipmentLinks(int antDeviceId, @NonNull List<String> equipmentList) {
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

        if (DEBUG) Log.d(TAG, "inserted");
    }

    private long getEquipmentId(@NonNull SQLiteDatabase db, @NonNull String equipmentName) {
        equipmentName.isEmpty();  // throw an exception when equipmentName is null

        long equipmentId = -1;

        Cursor cursor = db.query(EQUIPMENT, null, NAME + "=?", new String[]{equipmentName}, null, null, null);
        if (cursor.moveToFirst()) {
            equipmentId = cursor.getInt(cursor.getColumnIndex(C_ID));
        } else {
            Log.e(TAG, "ERROR: in getEquipmentId: no id to name: " + equipmentName);
        }
        cursor.close();

        return equipmentId;
    }

    public long getEquipmentId(@NonNull String equipmentName) {
        SQLiteDatabase db = this.getReadableDatabase();
        return getEquipmentId(db, equipmentName);
    }

    @Nullable
    public String getEquipmentNameFromId(long equipmentId) {
        if (equipmentId <= 0) {
            return null;
        }
        String equipmentName = null;

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(EQUIPMENT, null, C_ID + "=?", new String[]{Long.toString(equipmentId)}, null, null, null);

        if (cursor.moveToFirst()) {
            equipmentName = cursor.getString(cursor.getColumnIndex(NAME));
        } else {
            Log.e(TAG, "ERROR: in getEquipmentFromId: no name for id: " + equipmentId);
        }

        cursor.close();

        return equipmentName;
    }

    @Nullable
    public String getStravaIdFromId(int equipmentId) {
        return getStravaIdFromId((long) equipmentId);
    }

    @Nullable
    public String getStravaIdFromId(long equipmentId) {
        if (equipmentId <= 0) {
            return null;
        }
        String stravaId = null;

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(EQUIPMENT, null, C_ID + "=?", new String[]{Long.toString(equipmentId)}, null, null, null);

        if (cursor.moveToFirst()) {
            stravaId = cursor.getString(cursor.getColumnIndex(STRAVA_ID));
        } else {
            Log.e(TAG, "ERROR: in getStravaIdFromId: no stravaId for id: " + equipmentId);
        }

        cursor.close();

        return stravaId;
    }

    public long getIdFromStravaId(@Nullable String stravaId) {
        if (stravaId == null || stravaId.trim().isEmpty()) {
            return -1;
        }
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.query(EQUIPMENT, new String[]{C_ID},
                STRAVA_ID + "=?", new String[]{stravaId.trim()},
                null, null, null, "1")) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error looking up equipment by Strava ID: " + stravaId, e);
        }
        return -1;
    }

    public long addOrUpdateStravaGear(@NonNull String stravaId, @NonNull String name, int frameType, @NonNull String sportType, boolean retired) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(NAME, name);
        values.put(STRAVA_NAME, name);
        values.put(STRAVA_ID, stravaId);
        values.put(FRAME_TYPE, frameType);
        values.put(SPORT_TYPE, sportType);
        values.put(RETIRED, retired ? 1 : 0);

        int updated = db.update(EQUIPMENT, values, STRAVA_ID + "=?", new String[]{stravaId});
        if (updated > 0) {
            return getIdFromStravaId(stravaId);
        }

        // Try updating existing unlinked equipment with matching name
        int unlinkedUpdated = db.update(EQUIPMENT, values,
                NAME + "=? AND (" + STRAVA_ID + " IS NULL OR " + STRAVA_ID + "='')",
                new String[]{name});
        if (unlinkedUpdated > 0) {
            return getIdFromStravaId(stravaId);
        }

        long newId = db.insert(EQUIPMENT, null, values);
        if (DEBUG) Log.i(TAG, "addOrUpdateStravaGear inserted id: " + newId + " for stravaId: " + stravaId);
        return newId;
    }

    /**
     * Inserts new equipment and its linked sensors.
     * @return The ID of the newly created equipment.
     */
    public long addEquipment(String name, int frameType, List<Long> linkedDeviceIds) {
        if (DEBUG) Log.i(TAG, "addEquipment: " + name + ", " + frameType + ", " + linkedDeviceIds);

        SQLiteDatabase db = this.getWritableDatabase();
        long newId = -1;

        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put(NAME, name);
            values.put(FRAME_TYPE, frameType);
            if (frameType > 0 && frameType <= 4) {  // indeed a bike
                values.put(SPORT_TYPE, BSportType.BIKE.name());
            }
            if (frameType == 0) {  // not a bike
                values.put(SPORT_TYPE, BSportType.RUN.name());
            }
            // Strava IDs would be null/empty for new local items

            newId = db.insert(EQUIPMENT, null, values);

            if (newId != -1) {
                for (Long deviceId : linkedDeviceIds) {
                    ContentValues linkValues = new ContentValues();
                    linkValues.put(EQUIPMENT_ID, newId);
                    linkValues.put(ANT_DEVICE_ID, deviceId);
                    db.insert(LINKS, null, linkValues);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        if (DEBUG) Log.i(TAG, "added equipment with id: " + newId);
        return newId;
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        db.execSQL(CREATE_EQUIPMENT_TABLE);
        db.execSQL(CREATE_LINKS_TABLE);

        if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_EQUIPMENT_TABLE);
        if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_LINKS_TABLE);

    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + EQUIPMENT + " ADD COLUMN " + RETIRED + " INTEGER DEFAULT 0");
        }
        if (DEBUG) Log.d(TAG, "onUpgraded from " + oldVersion + " to " + newVersion);
    }

    /**
     * Unlinks all equipment from Strava by clearing StravaId and StravaName to NULL.
     * Preserves local equipment entities, hardware sensor pairings (LINKS), and sport type links (REQ-EXT-009).
     */
    public int unlinkAllStravaEquipment() {
        if (DEBUG) Log.d(TAG, "unlinkAllStravaEquipment");
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.putNull(STRAVA_ID);
        values.putNull(STRAVA_NAME);
        return db.update(EQUIPMENT, values, null, null);
    }
}
