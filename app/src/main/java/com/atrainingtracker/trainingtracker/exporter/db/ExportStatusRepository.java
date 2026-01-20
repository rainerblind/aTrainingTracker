package com.atrainingtracker.trainingtracker.exporter.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.annotation.NonNull;

import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.exporter.ExportInfo;
import com.atrainingtracker.trainingtracker.exporter.ExportStatus;
import com.atrainingtracker.trainingtracker.exporter.ExportType;
import com.atrainingtracker.trainingtracker.exporter.FileFormat;

import java.util.EnumMap;


/**
 * Repository zur Verwaltung des Export-Status in der Datenbank.
 * Dies ist ein Singleton und der EINZIGE Ort, an dem Datenbankzugriffe
 * für den Export-Status stattfinden. Alle Methoden sind Thread-sicher.
 */
public class ExportStatusRepository {

    private static final boolean DEBUG = true;
    private static final String TAG = "ExportStatusRepo";
    private static ExportStatusRepository sInstance;
    private final ExportStatusDbHelper mDbHelper;


    public static final String FORMAT = "Format";
    public static final String TYPE = "Type";
    public static final String EXPORT_STATUS = "Progress"; // TODO: rename to ExportStatus???
    public static final String ANSWER = "Answer";

    private ExportStatusRepository(Context context) {
        mDbHelper = ExportStatusDbHelper.getInstance(context);
    }

    public static synchronized ExportStatusRepository getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ExportStatusRepository(context.getApplicationContext());
        }
        return sInstance;
    }


    public synchronized void addExportStatus(ContentValues contentValues) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.insert(ExportStatusDbHelper.TABLE, null, contentValues);
    }


    /***********************************************************************************************
     * Updates the status of all 'TRACKING' exports for a given workout to 'WAITING'.
     * This is the most efficient way to perform this batch update.
     *
     * @param fileBaseName The unique identifier for the finished workout.
     * @return The number of rows affected.
     */
    public int workoutFinished(String fileBaseName) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(EXPORT_STATUS, ExportStatus.TRACKING_FINISHED.name());

        String whereClause = WorkoutSummaries.FILE_BASE_NAME + " = ? AND " + EXPORT_STATUS + " = ?";
        String[] whereArgs = { fileBaseName, ExportStatus.TRACKING.name() };

        return db.update(ExportStatusDbHelper.TABLE, values, whereClause, whereArgs);
    }

    public synchronized void updateExportStatus(ContentValues contentValues, String fileBaseName, ExportType exportType, FileFormat fileFormat) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot update status for " + fileBaseName + ", " + exportType + ", " + fileFormat);
            return;
        }
        db.update(ExportStatusDbHelper.TABLE,
                contentValues,
                WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "=? AND " + FORMAT + "=?",
                new String[]{fileBaseName, exportType.name(), fileFormat.name()});
    }

    public synchronized void updateExportStatus(ContentValues contentValues, ExportInfo exportInfo) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot update status for " + exportInfo);
            return;
        }

        db.update(ExportStatusDbHelper.TABLE,
                contentValues,
                WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "=? AND " + FORMAT + "=?",
                new String[]{exportInfo.getFileBaseName(), exportInfo.getExportType().name(), exportInfo.getFileFormat().name()});
    }



    public synchronized EnumMap<ExportType, EnumMap<FileFormat, ExportStatus>> getExportStatusMap(String fileBaseName) {
        if (DEBUG) Log.d(TAG, "getExportStatus");

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot get the export status for " + fileBaseName + " will return null");
            return null;
        }

        EnumMap<ExportType, EnumMap<FileFormat, ExportStatus>> result = new EnumMap<>(ExportType.class);
        Cursor cursor;

        for (ExportType exportType : ExportType.values()) {

            EnumMap<FileFormat, ExportStatus> enumMap = new EnumMap<>(FileFormat.class);
            for (FileFormat fileFormat : FileFormat.values()) {
                cursor = db.query(ExportStatusDbHelper.TABLE,
                        new String[]{EXPORT_STATUS},
                        WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "=? AND " + FORMAT + "=?",
                        new String[]{fileBaseName, exportType.name(), fileFormat.name()},
                        null,
                        null,
                        null);
                if (cursor.getCount() > 0) {
                    cursor.moveToFirst();
                    enumMap.put(fileFormat, ExportStatus.valueOf(cursor.getString(cursor.getColumnIndex(EXPORT_STATUS))));
                }
                cursor.close();
            }
            result.put(exportType, enumMap);
        }

        if (DEBUG) Log.d(TAG, "getExportStatus finished");

        return result;
    }

    public synchronized EnumMap<FileFormat, ExportStatus> getExportStatusMap(String fileBaseName, ExportType exportType) {
        if (DEBUG) Log.d(TAG, "getExportStatus " + fileBaseName + " " + exportType);

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot get the export status for " + fileBaseName + " " + exportType + " will return null");
            return null;
        }

        EnumMap<FileFormat, ExportStatus> result = new EnumMap<>(FileFormat.class);
        Cursor cursor;

        for (FileFormat fileFormat : FileFormat.values()) {
            cursor = db.query(ExportStatusDbHelper.TABLE,
                    new String[]{EXPORT_STATUS},
                    WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "=? AND " + FORMAT + "=?",
                    new String[]{fileBaseName, exportType.name(), fileFormat.name()},
                    null,
                    null,
                    null);
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                result.put(fileFormat, ExportStatus.valueOf(cursor.getString(cursor.getColumnIndex(EXPORT_STATUS))));
            }
            cursor.close();
        }

        if (DEBUG) Log.d(TAG, "getExportStatus finished");

        return result;
    }


    public synchronized ExportStatus getExportStatus(@NonNull ExportInfo exportInfo) {
        if (DEBUG) Log.d(TAG, "getExportStatus");

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot get the export status for " + exportInfo + " will return null");
            return null;
        }

        ExportStatus exportStatus = null;

        Cursor cursor = db.query(ExportStatusDbHelper.TABLE,
                new String[]{EXPORT_STATUS},
                WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "=? AND " + FORMAT + "=?",
                new String[]{exportInfo.getFileBaseName(), exportInfo.getExportType().name(), exportInfo.getFileFormat().name()},
                null,
                null,
                null);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            exportStatus = ExportStatus.valueOf(cursor.getString(cursor.getColumnIndex(EXPORT_STATUS)));
        }
        cursor.close();

        return exportStatus;
    }


    public synchronized String getExportAnswer(@NonNull ExportInfo exportInfo) {
        if (DEBUG) Log.d(TAG, "getExportAnswer");

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot get the export answer for " + exportInfo + " will return null");
            return null;
        }

        String exportAnswer = null;

        Cursor cursor = db.query(ExportStatusDbHelper.TABLE,
                new String[]{ANSWER},
                WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "=? AND " + FORMAT + "=?",
                new String[]{exportInfo.getFileBaseName(), exportInfo.getExportType().name(), exportInfo.getFileFormat().name()},
                null,
                null,
                null);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            exportAnswer = cursor.getString(cursor.getColumnIndex(ANSWER));
        }
        cursor.close();

        return exportAnswer;
    }

    public synchronized void deleteWorkout(String baseFileName) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot delete " + baseFileName);
        }

        db.delete(ExportStatusDbHelper.TABLE, WorkoutSummaries.FILE_BASE_NAME + "=?", new String[]{baseFileName});
    }


    protected static class ExportStatusDbHelper extends SQLiteOpenHelper {
        public static final String DB_NAME = "ExportStatus.db";
        public static final int DB_VERSION = 1;
        static final String TAG = "ExportStatusDbHelper";
        static final String TABLE = "ExportManager";
        static final String C_ID = BaseColumns._ID;
        static final String RETRIES = "Retries";  // shall not be used --> keep it here.
        protected static final String CREATE_TABLE = "create table " + TABLE + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "

                + WorkoutSummaries.FILE_BASE_NAME + " text, "
                + FORMAT + " text, "  // CSV, GPX, TCX, GC, Strava, RunKeeper, TrainingPeaks
                + TYPE + " text, "  // File, Dropbox, Community

                + EXPORT_STATUS + " text, "
                // + RETRIES + " int, "  // no longer necessary
                + ANSWER + " text)";

        private static ExportStatusDbHelper sInstance;

        /**
         * Ensure that there is only one instance of this DbHelper (Singleton-Pattern).
         */
        public static synchronized ExportStatusDbHelper getInstance(Context context) {
            if (sInstance == null) {
                sInstance = new ExportStatusDbHelper(context.getApplicationContext());
            }
            return sInstance;
        }

        // Constructor
        private ExportStatusDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        // Called only once, first time the DB is created
        @Override
        public void onCreate(@NonNull SQLiteDatabase db) {

            db.execSQL(CREATE_TABLE);

            if (DEBUG) Log.d(TAG, "onCreated sql: " + TABLE);
        }

        //Called whenever newVersion != oldVersion
        @Override
        public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
            // TODO: alter table instead of deleting!

            db.execSQL("drop table if exists " + TABLE);
            if (DEBUG) Log.d(TAG, "onUpgraded");
            onCreate(db);  // run onCreate to get new database
        }

    }


}
