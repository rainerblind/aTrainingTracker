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

package com.atrainingtracker.trainingtracker.segments;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.annotation.NonNull;

import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter;
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter;
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper;
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaSegment;
import com.atrainingtracker.trainingtracker.ui.map.MapSegment;
import com.atrainingtracker.trainingtracker.ui.map.PathPoint;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SegmentsDatabaseManager {
    private static final String TAG = SegmentsDatabaseManager.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);

    // --- Modern Singleton Pattern ---
    private static volatile SegmentsDatabaseManager cInstance;
    private final SegmentsDbHelper cSegmentsDbHelper;
    private final Context mContext;
    private SQLiteDatabase mDatabase = null;

    private SegmentsDatabaseManager(@NonNull Context context) {
        this.mContext = context.getApplicationContext();
        this.cSegmentsDbHelper = new SegmentsDbHelper(this.mContext);
    }

    @NonNull
    public static SegmentsDatabaseManager getInstance(@NonNull Context context) {
        if (cInstance == null) {
            synchronized (SegmentsDatabaseManager.class) {
                if (cInstance == null) {
                    cInstance = new SegmentsDatabaseManager(context);
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
            // If the database was closed, ensure the helper clears its reference
            if (mDatabase != null) {
                cSegmentsDbHelper.close();
            }
            mDatabase = cSegmentsDbHelper.getWritableDatabase();
            return mDatabase;
        }
    }
    // --- End of Singleton Pattern ---

    public static boolean doesDatabaseExist(@NonNull Context context) {
        File dbFile = context.getDatabasePath(SegmentsDbHelper.DB_NAME);
        return dbFile.exists();
    }

    public List<PathPoint> getSegmentPath(long segmentId) {
        List<PathPoint> points = new ArrayList<>();
        SQLiteDatabase db = getDatabase();
        Cursor c = db.query(Segments.TABLE_SEGMENT_STREAMS,
                new String[]{Segments.DISTANCE, Segments.LATITUDE, Segments.LONGITUDE, Segments.ALTITUDE},
                Segments.STRAVA_SEGMENT_ID + "=?", new String[]{String.valueOf(segmentId)},
                null, null, Segments.C_ID + " ASC");

        int dist_index = c.getColumnIndexOrThrow(Segments.DISTANCE);
        int lat_index = c.getColumnIndexOrThrow(Segments.LATITUDE);
        int lon_index = c.getColumnIndexOrThrow(Segments.LONGITUDE);
        int alt_index = c.getColumnIndexOrThrow(Segments.ALTITUDE);

        while (c.moveToNext()) {
            points.add(
                    new PathPoint(
                            c.getFloat(dist_index),
                            new LatLng(
                                    c.getDouble(lat_index),
                                    c.getDouble(lon_index)),
                            c.getFloat(alt_index)
                    )
            );
        }
        c.close();
        return points;
    }


    /**
     * Fetches summary details for a specific segment and formats them into a SegmentSummary object.
     */
    public SegmentSummary getSegmentSummary(long segmentId) {
        SQLiteDatabase db = getDatabase();
        SegmentSummary summary = null;

        Cursor cursor = db.query(
                Segments.TABLE_STARRED_SEGMENTS,
                null, // Fetch all columns defined in your projection
                Segments.STRAVA_SEGMENT_ID + "=?",
                new String[]{String.valueOf(segmentId)},
                null, null, null
        );

        if (cursor.moveToFirst()) {
            // 1. Get Activity Type/Sport
            SportTypeDatabaseManager sportTypeMgr = SportTypeDatabaseManager.getInstance(mContext);
            String activityType = cursor.getString(cursor.getColumnIndexOrThrow(Segments.ACTIVITY_TYPE));
            BSportType sportType = sportTypeMgr.getBSportTypeFromStravaName(activityType);

            // 2. Extract raw values
            double distance = cursor.getDouble(cursor.getColumnIndexOrThrow(Segments.DISTANCE));
            double avgGrade = cursor.getDouble(cursor.getColumnIndexOrThrow(Segments.AVERAGE_GRADE));
            double maxGrade = cursor.getDouble(cursor.getColumnIndexOrThrow(Segments.MAXIMUM_GRADE));
            double elevationGain = cursor.getDouble(cursor.getColumnIndexOrThrow(Segments.TOTAL_ELEVATION_GAIN));
            double elevLow = cursor.getDouble(cursor.getColumnIndexOrThrow(Segments.ELEVATION_LOW));
            double elevHigh = cursor.getDouble(cursor.getColumnIndexOrThrow(Segments.ELEVATION_HIGH));
            int prTimeSeconds = cursor.getInt(cursor.getColumnIndexOrThrow(Segments.PR_TIME));
            int climbCategory = cursor.getInt(cursor.getColumnIndexOrThrow(Segments.CLIMB_CATEGORY));
            String city = cursor.getString(cursor.getColumnIndexOrThrow(Segments.CITY));
            String name = cursor.getString(cursor.getColumnIndexOrThrow(Segments.SEGMENT_NAME));
            String polyline = cursor.getString(cursor.getColumnIndexOrThrow(Segments.MAP_POLYLINE));
            
            Double minLat = cursor.isNull(cursor.getColumnIndexOrThrow(Segments.BOUND_MIN_LAT)) ? null : cursor.getDouble(cursor.getColumnIndexOrThrow(Segments.BOUND_MIN_LAT));
            Double minLng = cursor.isNull(cursor.getColumnIndexOrThrow(Segments.BOUND_MIN_LNG)) ? null : cursor.getDouble(cursor.getColumnIndexOrThrow(Segments.BOUND_MIN_LNG));
            Double maxLat = cursor.isNull(cursor.getColumnIndexOrThrow(Segments.BOUND_MAX_LAT)) ? null : cursor.getDouble(cursor.getColumnIndexOrThrow(Segments.BOUND_MAX_LAT));
            Double maxLng = cursor.isNull(cursor.getColumnIndexOrThrow(Segments.BOUND_MAX_LNG)) ? null : cursor.getDouble(cursor.getColumnIndexOrThrow(Segments.BOUND_MAX_LNG));

            // 3. Format strings
            DistanceFormatter df = new DistanceFormatter();
            TimeFormatter tf = new TimeFormatter();
            AltitudeFormatter af = new AltitudeFormatter();

            summary = new SegmentSummary(
                    segmentId,
                    name,
                    sportType,
                    climbCategory,
                    climbCategory > 0 ? StravaHelper.translateClimbCategory(climbCategory) : "",
                    prTimeSeconds,
                    prTimeSeconds > 0 ? tf.format(prTimeSeconds) : "",
                    (city != null && !city.isEmpty()) ? city : "",
                    df.format_with_units(distance),
                    distance,
                    avgGrade,
                    String.format(Locale.getDefault(), "Ø %.1f%%", avgGrade),
                    String.format(Locale.getDefault(), "%.1f%% Max", maxGrade),
                    elevationGain,
                    af.format_with_units(elevationGain),
                    af.format_with_units(elevLow),
                    af.format_with_units(elevHigh),
                    polyline,
                    minLat,
                    minLng,
                    maxLat,
                    maxLng
            );
        }
        cursor.close();

        return summary;
    }

    public List<SegmentSummary> getAllSegmentSummaries() {
        List<SegmentSummary> summaries = new ArrayList<>();
        SQLiteDatabase db = getDatabase();
        Cursor cursor = db.query(Segments.TABLE_STARRED_SEGMENTS, null, null, null, null, null, null);

        SportTypeDatabaseManager sportTypeMgr = SportTypeDatabaseManager.getInstance(mContext);
        DistanceFormatter df = new DistanceFormatter();
        TimeFormatter tf = new TimeFormatter();
        AltitudeFormatter af = new AltitudeFormatter();

        int strava_id_index = cursor.getColumnIndexOrThrow(Segments.STRAVA_SEGMENT_ID);
        int activity_type_index = cursor.getColumnIndexOrThrow(Segments.ACTIVITY_TYPE);
        int dist_index = cursor.getColumnIndexOrThrow(Segments.DISTANCE);
        int avg_grade_index = cursor.getColumnIndexOrThrow(Segments.AVERAGE_GRADE);
        int max_grade_index = cursor.getColumnIndexOrThrow(Segments.MAXIMUM_GRADE);
        int elev_gain_index = cursor.getColumnIndexOrThrow(Segments.TOTAL_ELEVATION_GAIN);
        int elev_low_index = cursor.getColumnIndexOrThrow(Segments.ELEVATION_LOW);
        int elev_high_index = cursor.getColumnIndexOrThrow(Segments.ELEVATION_HIGH);
        int pr_time_index = cursor.getColumnIndexOrThrow(Segments.PR_TIME);
        int climb_category_index = cursor.getColumnIndexOrThrow(Segments.CLIMB_CATEGORY);
        int city_index = cursor.getColumnIndexOrThrow(Segments.CITY);
        int name_index = cursor.getColumnIndexOrThrow(Segments.SEGMENT_NAME);
        int map_polyline_index = cursor.getColumnIndexOrThrow(Segments.MAP_POLYLINE);
        int min_lat_index = cursor.getColumnIndexOrThrow(Segments.BOUND_MIN_LAT);
        int min_lng_index = cursor.getColumnIndexOrThrow(Segments.BOUND_MIN_LNG);
        int max_lat_index = cursor.getColumnIndexOrThrow(Segments.BOUND_MAX_LAT);
        int max_lng_index = cursor.getColumnIndexOrThrow(Segments.BOUND_MAX_LNG);


        while (cursor.moveToNext()) {
            long segmentId = cursor.getLong(strava_id_index);
            String activityType = cursor.getString(activity_type_index);
            BSportType sportType = sportTypeMgr.getBSportTypeFromStravaName(activityType);
            double distance = cursor.getDouble(dist_index);
            double avgGrade = cursor.getDouble(avg_grade_index);
            double maxGrade = cursor.getDouble(max_grade_index);
            double elevation_gain = cursor.getDouble(elev_gain_index);
            double elevLow = cursor.getDouble(elev_low_index);
            double elevHigh = cursor.getDouble(elev_high_index);
            int prTimeSeconds = cursor.getInt(pr_time_index);
            int climbCategory = cursor.getInt(climb_category_index);
            String city = cursor.getString(city_index);
            String name = cursor.getString(name_index);
            String polyline = cursor.getString(map_polyline_index);
            if (polyline == null) {
                polyline = "";
            }

            Double minLat = cursor.isNull(min_lat_index) ? null : cursor.getDouble(min_lat_index);
            Double minLng = cursor.isNull(min_lng_index) ? null : cursor.getDouble(min_lng_index);
            Double maxLat = cursor.isNull(max_lat_index) ? null : cursor.getDouble(max_lat_index);
            Double maxLng = cursor.isNull(max_lng_index) ? null : cursor.getDouble(max_lng_index);

            summaries.add(new SegmentSummary(
                            segmentId,
                            name,
                            sportType,
                            climbCategory,
                            StravaHelper.translateClimbCategory(climbCategory),
                            prTimeSeconds,
                            prTimeSeconds > 0 ? tf.format(prTimeSeconds) : "",
                            (city != null && !city.isEmpty()) ? city : "",
                            df.format_with_units(distance),
                            distance,
                            avgGrade,
                            String.format(Locale.getDefault(), "Ø %.1f%%", avgGrade),
                            String.format(Locale.getDefault(), "%.1f%% Max", maxGrade),
                            elevation_gain,
                            af.format_with_units(elevation_gain),
                            af.format_with_units(elevLow),
                            af.format_with_units(elevHigh),
                            polyline,
                            minLat,
                            minLng,
                            maxLat,
                            maxLng
                    )
            );
        }
        cursor.close();

        return summaries;
    }

    /**
     * Adds or updates a Strava segment in the database using the modern StravaSegment data class.
     * This method maps the Kotlin object properties to the SQLite columns.
     *
     * @param segment The StravaSegment object parsed from JSON
     */
    public void addOrUpdateSegment(StravaSegment segment) {
        SQLiteDatabase db = getDatabase();
        ContentValues cv = new ContentValues();

        // Mapping Kotlin fields to Database Columns
        cv.put(Segments.STRAVA_SEGMENT_ID, segment.getId());
        cv.put(Segments.SEGMENT_NAME, segment.getName());
        cv.put(Segments.ACTIVITY_TYPE, segment.getActivityType());
        cv.put(Segments.DISTANCE, segment.getDistance());
        cv.put(Segments.AVERAGE_GRADE, segment.getAverageGrade());
        cv.put(Segments.MAXIMUM_GRADE, segment.getMaximumGrade());
        cv.put(Segments.ELEVATION_HIGH, segment.getElevationHigh());
        cv.put(Segments.ELEVATION_LOW, segment.getElevationLow());
        cv.put(Segments.TOTAL_ELEVATION_GAIN, segment.getTotalElevationGain());
        cv.put(Segments.CLIMB_CATEGORY, segment.getClimbCategory());
        cv.put(Segments.CITY, segment.getCity());
        cv.put(Segments.STATE, segment.getState());
        cv.put(Segments.COUNTRY, segment.getCountry());
        cv.put(Segments.PR_TIME, segment.getPrTime());

        // Extract the polyline from the nested Map object
        if (segment.getMap() != null) {
            String polyline = segment.getMap().getPolyline();
            cv.put(Segments.MAP_POLYLINE, polyline);
            
            if (polyline != null && !polyline.isEmpty()) {
                List<LatLng> decoded = PolyUtil.decode(polyline);
                if (!decoded.isEmpty()) {
                    double minLat = 90.0, maxLat = -90.0, minLng = 180.0, maxLng = -180.0;
                    for (LatLng p : decoded) {
                        if (p.latitude < minLat) minLat = p.latitude;
                        if (p.latitude > maxLat) maxLat = p.latitude;
                        if (p.longitude < minLng) minLng = p.longitude;
                        if (p.longitude > maxLng) maxLng = p.longitude;
                    }
                    cv.put(Segments.BOUND_MIN_LAT, minLat);
                    cv.put(Segments.BOUND_MIN_LNG, minLng);
                    cv.put(Segments.BOUND_MAX_LAT, maxLat);
                    cv.put(Segments.BOUND_MAX_LNG, maxLng);
                }
            }
        }

        // Handle LatLng arrays (Strava returns [lat, lng])
        if (segment.getStartLatLng().size() >= 2) {
            cv.put(Segments.START_LATITUDE, segment.getStartLatLng().get(0));
            cv.put(Segments.START_LONGITUDE, segment.getStartLatLng().get(1));
        }
        if (segment.getEndLatLng().size() >= 2) {
            cv.put(Segments.END_LATITUDE, segment.getEndLatLng().get(0));
            cv.put(Segments.END_LONGITUDE, segment.getEndLatLng().get(1));
        }


        // Insert or Replace logic
        db.beginTransaction();
        try {
            // Check if segment already exists to handle updates vs inserts
            int rowsAffected = db.update(Segments.TABLE_STARRED_SEGMENTS, cv,
                    Segments.STRAVA_SEGMENT_ID + "=?",
                    new String[]{String.valueOf(segment.getId())});

            if (rowsAffected == 0) {
                db.insert(Segments.TABLE_STARRED_SEGMENTS, null, cv);
            }

            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error inserting/updating segment: " + segment.getId(), e);
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Deletes a specific segment and its associated streams.
     * Useful for when a user un-stars a segment on Strava.
     */
    public void deleteSegment(long stravaSegmentId) {
        SQLiteDatabase db = getDatabase();
        db.beginTransaction();
        try {
            db.delete(Segments.TABLE_STARRED_SEGMENTS,
                    Segments.STRAVA_SEGMENT_ID + "=?",
                    new String[]{String.valueOf(stravaSegmentId)});

            db.delete(Segments.TABLE_SEGMENT_STREAMS,
                    Segments.STRAVA_SEGMENT_ID + "=?",
                    new String[]{String.valueOf(stravaSegmentId)});

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Inserts the segment stream data into the database.
     * Handles the 1-second interpolation logic if time data is present.
     *
     * @param segmentId The Strava ID of the segment
     * @param effortRows List of ContentValues prepared from the Strava Stream
     * @param haveTime Boolean indicating if time data is present for interpolation
     */
    public void insertSegmentStreams(long segmentId, List<ContentValues> effortRows, boolean haveTime) {
        if (effortRows == null || effortRows.isEmpty()) return;

        haveTime = false;

        SQLiteDatabase db = getDatabase();
        db.beginTransaction();
        try {
            if (haveTime) {
                // Strava time starts at 0, but we need the first prevTime to be -1
                // to ensure the first point is inserted correctly via the delta logic
                Integer firstTime = effortRows.get(0).getAsInteger("time");
                int prevTime = (firstTime != null ? firstTime : 0) - 1;

                for (ContentValues row : effortRows) {
                    Integer curTimeObj = row.getAsInteger("time");
                    int curTime = (curTimeObj != null ? curTimeObj : prevTime + 1);

                    // Clean up the row for insertion
                    row.remove("time");
                    row.put(Segments.STRAVA_SEGMENT_ID, segmentId);

                    // Fill gaps if time jumps (ensure 1 row per second in the DB)
                    int delta = Math.max(1, curTime - prevTime);
                    for (int i = 0; i < delta; i++) {
                        db.insert(Segments.TABLE_SEGMENT_STREAMS, null, row);
                    }
                    prevTime = curTime;
                }
            } else {
                // Simple insertion if no time interpolation is needed
                for (ContentValues row : effortRows) {
                    row.put(Segments.STRAVA_SEGMENT_ID, segmentId);
                    db.insert(Segments.TABLE_SEGMENT_STREAMS, null, row);
                }
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error inserting streams for segment: " + segmentId, e);
        } finally {
            db.endTransaction();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // some high level helper methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Deletes all tables and effectively resets the database.
     * Note: This is a destructive operation.
     */
    public void deleteAllTables() {
        try {
            SQLiteDatabase db = getDatabase();
            db.beginTransaction();
            try {
                db.execSQL("DROP TABLE IF EXISTS " + Segments.TABLE_STARRED_SEGMENTS);
                db.execSQL("DROP TABLE IF EXISTS " + Segments.TABLE_SEGMENT_STREAMS);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            // Recreate the tables
            cSegmentsDbHelper.onCreate(db);
            if (DEBUG) Log.d(TAG, "All segment tables deleted and recreated.");
        } catch (SQLException e) {
            Log.e(TAG, "Error deleting all tables in SegmentsDatabase", e);
        }
    }


    public static final class Segments {
        public static final String TABLE_STARRED_SEGMENTS = "StarredSegmentsTable";
        public static final String TABLE_SEGMENT_STREAMS = "SegmentStreams";


        // for TABLE_STARRED_SEGMENTS
        public static final String C_ID = BaseColumns._ID;
        public static final String STRAVA_SEGMENT_ID = "SegmentId";     // id: 	integer
        public static final String RESOURCE_STATE = "ResourceState"; // resource_state: 	integer // resource_state: 	integer
        public static final String SEGMENT_NAME = "SegmentName";   // name: 	string
        public static final String ACTIVITY_TYPE = "ActivityType";  // activity_type: 	string ‘Ride’ or ‘Run’
        public static final String DISTANCE = "Distance";      // distance: 	float meters
        public static final String AVERAGE_GRADE = "AverageGrade";  // average_grade: 	float percent
        public static final String MAXIMUM_GRADE = "MaximumGrade";  // maximum_grade: 	float percent
        public static final String ELEVATION_HIGH = "ElevationHigh"; // elevation_high: 	float meters
        public static final String ELEVATION_LOW = "ElevationLow";  // elevation_low: 	float meters
        public static final String TOTAL_ELEVATION_GAIN = "TotalElevationGain"; // total_elevation_gain: 	float meters
        public static final String START_LATITUDE = "StartLatitude";
        public static final String START_LONGITUDE = "StartLongitude";
        public static final String END_LATITUDE = "EndLatitude";
        public static final String END_LONGITUDE = "EndLongitude";
        public static final String CLIMB_CATEGORY = "ClimbCategory"; // climb_category: 	integer [0, 5], higher is harder ie. 5 is Hors catégorie, 0 is uncategorized
        public static final String CITY = "City";          // city: 	string
        public static final String STATE = "State";         // state: 	string
        public static final String COUNTRY = "Country";       // country: 	string
        public static final String PRIVATE = "Private";       // private: 	boolean
        public static final String STARRED = "Starred";       // starred: 	boolean
        public static final String HAZARDOUS = "Hazardous";     // hazardous: boolean
        public static final String PR_TIME = "pr_time";
        public static final String MAP_POLYLINE = "MapPolyline";
        public static final String BOUND_MIN_LAT = "BoundMinLat"; // added in Version 7
        public static final String BOUND_MIN_LNG = "BoundMinLng"; // added in Version 7
        public static final String BOUND_MAX_LAT = "BoundMaxLat"; // added in Version 7
        public static final String BOUND_MAX_LNG = "BoundMaxLng"; // added in Version 7


        // for TABLE_SEGMENT_STREAMS
        // SEGMENT_ID
        // DISTANCE
        public static final String ALTITUDE = "Altitude";
        public static final String LATITUDE = "Latitude";
        public static final String LONGITUDE = "Longitude";
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // The database itself
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static class SegmentsDbHelper extends SQLiteOpenHelper {
        public static final String DB_NAME = "Segments.db";
        // public static final int DB_VERSION = 1; // created  3.8.2016
        // public static final int DB_VERSION = 2; // updated 19.8.2016
        // public static final int DB_VERSION = 3; // updated 26.9.2016
        // public static final int DB_VERSION = 5; // updated 11.01.2026: add PR_TIME
        public static final int DB_VERSION = 7; // updated 25.07.2026: add spatial bounds (ATT-352)

        protected static final String CREATE_TABLE_STARRED_SEGMENTS_V7 = "create table " + Segments.TABLE_STARRED_SEGMENTS + " ("
                + Segments.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Segments.STRAVA_SEGMENT_ID + " int, "
                + Segments.RESOURCE_STATE + " int, "
                + Segments.SEGMENT_NAME + " text, "
                + Segments.ACTIVITY_TYPE + " text, "  // activity_type: 	string ‘Ride’ or ‘Run’
                + Segments.DISTANCE + " real, "
                + Segments.AVERAGE_GRADE + " real, "
                + Segments.MAXIMUM_GRADE + " real, "
                + Segments.ELEVATION_HIGH + " real, "
                + Segments.ELEVATION_LOW + " real, "
                + Segments.TOTAL_ELEVATION_GAIN + " real, " // introduced in Version 6
                + Segments.MAP_POLYLINE + " text, "         // introduced in Version 6
                + Segments.START_LATITUDE + " real, "
                + Segments.START_LONGITUDE + " real, "
                + Segments.END_LATITUDE + " real, "
                + Segments.END_LONGITUDE + " real, "
                + Segments.CLIMB_CATEGORY + " int, "   // climb_category: 	integer [0, 5], higher is harder ie. 5 is Hors catégorie, 0 is uncategorized
                + Segments.CITY + " text, "
                + Segments.STATE + " text, "
                + Segments.COUNTRY + " text, "
                + Segments.PRIVATE + " int, "
                + Segments.STARRED + " int, "
                + Segments.HAZARDOUS + " int, "
                + Segments.PR_TIME + " int, "
                + Segments.BOUND_MIN_LAT + " real, "    // introduced in Version 7
                + Segments.BOUND_MIN_LNG + " real, "    // introduced in Version 7
                + Segments.BOUND_MAX_LAT + " real, "    // introduced in Version 7
                + Segments.BOUND_MAX_LNG + " real)";   // introduced in Version 7


        protected static final String CREATE_TABLE_SEGMENT_STREAMS_V1 = "create table " + Segments.TABLE_SEGMENT_STREAMS + " ("
                + Segments.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Segments.STRAVA_SEGMENT_ID + " int, "
                + Segments.DISTANCE + " real, "
                + Segments.ALTITUDE + " real, "
                + Segments.LATITUDE + " real, "
                + Segments.LONGITUDE + " real)";
        private static final String TAG = SegmentsDbHelper.class.getName();
        private static final boolean DEBUG = TrainingApplication.getDebug(true);


        // Constructor
        public SegmentsDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);

        }

        // Called only once, first time the DB is created
        @Override
        public void onCreate(@NonNull SQLiteDatabase db) {

            db.execSQL(CREATE_TABLE_STARRED_SEGMENTS_V7);
            if (DEBUG) Log.d(TAG, "onCreate sql: " + CREATE_TABLE_STARRED_SEGMENTS_V7);

            db.execSQL(CREATE_TABLE_SEGMENT_STREAMS_V1);
            if (DEBUG) Log.d(TAG, "onCreate sql: " + CREATE_TABLE_SEGMENT_STREAMS_V1);

        }

        @Override
        public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 6) {
                // discard old data and start over for major changes below v6
                db.execSQL("drop table if exists " + Segments.TABLE_STARRED_SEGMENTS);
                db.execSQL("drop table if exists " + Segments.TABLE_SEGMENT_STREAMS);
                onCreate(db);
                return;
            }

            if (oldVersion < 7) {
                Log.i(TAG, "Upgrading Segments DB to Version 7 (Adding spatial bounds)");
                db.execSQL("ALTER TABLE " + Segments.TABLE_STARRED_SEGMENTS + " ADD COLUMN " + Segments.BOUND_MIN_LAT + " real");
                db.execSQL("ALTER TABLE " + Segments.TABLE_STARRED_SEGMENTS + " ADD COLUMN " + Segments.BOUND_MIN_LNG + " real");
                db.execSQL("ALTER TABLE " + Segments.TABLE_STARRED_SEGMENTS + " ADD COLUMN " + Segments.BOUND_MAX_LAT + " real");
                db.execSQL("ALTER TABLE " + Segments.TABLE_STARRED_SEGMENTS + " ADD COLUMN " + Segments.BOUND_MAX_LNG + " real");

                migrateSegmentBounds(db);
            }
        }

        private void migrateSegmentBounds(SQLiteDatabase db) {
            Log.i(TAG, "Populating spatial bounds for segments...");
            try (Cursor cursor = db.query(Segments.TABLE_STARRED_SEGMENTS, 
                    new String[]{Segments.STRAVA_SEGMENT_ID, Segments.MAP_POLYLINE}, 
                    null, null, null, null, null)) {
                int idIdx = cursor.getColumnIndexOrThrow(Segments.STRAVA_SEGMENT_ID);
                int polylineIdx = cursor.getColumnIndexOrThrow(Segments.MAP_POLYLINE);
                
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idIdx);
                    String polyline = cursor.getString(polylineIdx);
                    if (polyline != null && !polyline.isEmpty()) {
                        List<LatLng> decoded = PolyUtil.decode(polyline);
                        if (!decoded.isEmpty()) {
                            double minLat = 90.0, maxLat = -90.0, minLng = 180.0, maxLng = -180.0;
                            for (LatLng p : decoded) {
                                if (p.latitude < minLat) minLat = p.latitude;
                                if (p.latitude > maxLat) maxLat = p.latitude;
                                if (p.longitude < minLng) minLng = p.longitude;
                                if (p.longitude > maxLng) maxLng = p.longitude;
                            }
                            ContentValues cv = new ContentValues();
                            cv.put(Segments.BOUND_MIN_LAT, minLat);
                            cv.put(Segments.BOUND_MIN_LNG, minLng);
                            cv.put(Segments.BOUND_MAX_LAT, maxLat);
                            cv.put(Segments.BOUND_MAX_LNG, maxLng);
                            db.update(Segments.TABLE_STARRED_SEGMENTS, cv, Segments.STRAVA_SEGMENT_ID + "=?", new String[]{String.valueOf(id)});
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error migrating segment bounds", e);
            }
        }
    }
}
