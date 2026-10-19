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

package com.atrainingtracker.trainingtracker.exporter.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.atrainingtracker.database.DatabaseConstants;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.exporter.ExportConfig;
import com.atrainingtracker.trainingtracker.exporter.ExportInfo;
import com.atrainingtracker.trainingtracker.exporter.ExportStatus;
import com.atrainingtracker.trainingtracker.exporter.ExportType;
import com.atrainingtracker.trainingtracker.exporter.FileFormat;
import com.atrainingtracker.util.CursorUtils;
import com.atrainingtracker.util.EnumUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;


/**
 * Repository zur Verwaltung des Export-Status in der Datenbank.
 * Dies ist ein Singleton und der EINZIGE Ort, an dem Datenbankzugriffe
 * für den Export-Status stattfinden. Alle Methoden sind Thread-sicher.
 */
public class ExportStatusDatabaseManager {

    private static final boolean DEBUG = com.atrainingtracker.BuildConfig.DEBUG;
    private static final String TAG = "ExportStatusRepo";
    private static ExportStatusDatabaseManager sInstance;
    private final Context mContext;
    private final ExportStatusDbHelper mDbHelper;
    private SQLiteDatabase mDatabase = null;


    public static final String FORMAT = "Format";
    public static final String TYPE = "Type";
    public static final String EXPORT_STATUS = "Progress"; // TODO: rename to ExportStatus???
    public static final String ANSWER = "Answer";
    public static final String LAST_UPDATED = "LastUpdated";
    public static final String RUN_ATTEMPT_COUNT = "RunAttemptCount";

    public static final long MAX_EXPORT_DURATION_MS = ExportConfig.MAX_EXPORT_DURATION_MS;
    public static final long RECOVERY_GRACE_PERIOD_MS = ExportConfig.RECOVERY_GRACE_PERIOD_MS;

    public static boolean isValidFileBaseName(String fileBaseName) {
        return fileBaseName != null && !fileBaseName.trim().isEmpty() && !"null".equalsIgnoreCase(fileBaseName.trim());
    }

    private ExportStatusDatabaseManager(Context context) {
        mContext = context.getApplicationContext();
        mDbHelper = ExportStatusDbHelper.getInstance(context);
    }

    public static synchronized ExportStatusDatabaseManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ExportStatusDatabaseManager(context.getApplicationContext());
        }
        return sInstance;
    }

    @androidx.annotation.VisibleForTesting
    public static synchronized void resetForTesting(ExportStatusDatabaseManager newInstance) {
        sInstance = newInstance;
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
                mDbHelper.close();
            }
            mDatabase = mDbHelper.getWritableDatabase();
            return mDatabase;
        }
    }


    public void addExportStatus(ContentValues contentValues) {
        if (contentValues == null) return;
        String fileBaseName = contentValues.getAsString(WorkoutSummaries.FILE_BASE_NAME);
        if (!isValidFileBaseName(fileBaseName)) {
            Log.w(TAG, "Cannot add export status for invalid fileBaseName: " + fileBaseName);
            return;
        }
        if (!contentValues.containsKey(LAST_UPDATED)) {
            contentValues.put(LAST_UPDATED, System.currentTimeMillis());
        }
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db != null) {
            db.insert(ExportStatusDbHelper.TABLE, null, contentValues);
        }
    }


    /***********************************************************************************************
     * Updates the status of all 'TRACKING' exports for a given workout to 'WAITING'.
     * This is the most efficient way to perform this batch update.
     *
     * @param fileBaseName The unique identifier for the finished workout.
     * @return The number of rows affected.
     */
    public int workoutFinished(String fileBaseName) {
        if (!isValidFileBaseName(fileBaseName)) {
            Log.w(TAG, "Cannot finish workout for invalid fileBaseName: " + fileBaseName);
            return 0;
        }
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) return 0;

        ContentValues values = new ContentValues();
        values.put(EXPORT_STATUS, ExportStatus.TRACKING_FINISHED.name());
        values.put(LAST_UPDATED, System.currentTimeMillis());

        String whereClause = WorkoutSummaries.FILE_BASE_NAME + " = ? AND " + EXPORT_STATUS + " = ?";
        String[] whereArgs = { fileBaseName, ExportStatus.TRACKING.name() };

        return db.update(ExportStatusDbHelper.TABLE, values, whereClause, whereArgs);
    }

    public void updateExportStatus(ContentValues contentValues, String fileBaseName, ExportType exportType, FileFormat fileFormat) {
        if (!isValidFileBaseName(fileBaseName)) {
            Log.w(TAG, "Cannot update status for invalid fileBaseName: " + fileBaseName);
            return;
        }
        if (contentValues != null && !contentValues.containsKey(LAST_UPDATED)) {
            contentValues.put(LAST_UPDATED, System.currentTimeMillis());
        }
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot update status for " + fileBaseName + ", " + exportType + ", " + fileFormat);
            return;
        }
        if (exportType == null) {
            int rows = db.update(ExportStatusDbHelper.TABLE,
                    contentValues,
                    WorkoutSummaries.FILE_BASE_NAME + "=? AND " + FORMAT + "=?",
                    new String[]{fileBaseName, fileFormat.name()});
            if (rows == 0) {
                for (ExportType type : ExportType.values()) {
                    if (java.util.Arrays.asList(type.getExportToFileFormats()).contains(fileFormat)) {
                        ContentValues insertValues = new ContentValues();
                        insertValues.putAll(contentValues);
                        insertValues.put(WorkoutSummaries.FILE_BASE_NAME, fileBaseName);
                        insertValues.put(TYPE, type.name());
                        insertValues.put(FORMAT, fileFormat.name());
                        if (!insertValues.containsKey(EXPORT_STATUS)) {
                            insertValues.put(EXPORT_STATUS, ExportStatus.UNWANTED.name());
                        }
                        db.insert(ExportStatusDbHelper.TABLE, null, insertValues);
                    }
                }
            }
        } else {
            int rows = db.update(ExportStatusDbHelper.TABLE,
                    contentValues,
                    WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "=? AND " + FORMAT + "=?",
                    new String[]{fileBaseName, exportType.name(), fileFormat.name()});
            if (rows == 0) {
                ContentValues insertValues = new ContentValues();
                insertValues.putAll(contentValues);
                insertValues.put(WorkoutSummaries.FILE_BASE_NAME, fileBaseName);
                insertValues.put(TYPE, exportType.name());
                insertValues.put(FORMAT, fileFormat.name());
                if (!insertValues.containsKey(EXPORT_STATUS)) {
                    insertValues.put(EXPORT_STATUS, ExportStatus.UNWANTED.name());
                }
                db.insert(ExportStatusDbHelper.TABLE, null, insertValues);
            }
        }
    }

    public void updateExportStatus(ContentValues contentValues, ExportInfo exportInfo) {
        if (exportInfo == null) return;
        updateExportStatus(contentValues, exportInfo.getFileBaseName(), exportInfo.getExportType(), exportInfo.getFileFormat());
    }

    public void recordWorkerAttempt(@NonNull String fileBaseName, @NonNull ExportType exportType, @NonNull FileFormat fileFormat, int attemptCount) {
        if (!isValidFileBaseName(fileBaseName)) {
            Log.w(TAG, "Cannot record worker attempt for invalid fileBaseName: " + fileBaseName);
            return;
        }
        ContentValues values = new ContentValues();
        values.put(RUN_ATTEMPT_COUNT, attemptCount);
        values.put(LAST_UPDATED, System.currentTimeMillis());
        updateExportStatus(values, fileBaseName, exportType, fileFormat);
    }

    public void updateHeartbeat(@NonNull String fileBaseName, @NonNull ExportType exportType, @NonNull FileFormat fileFormat) {
        if (!isValidFileBaseName(fileBaseName)) {
            Log.w(TAG, "Cannot update heartbeat for invalid fileBaseName: " + fileBaseName);
            return;
        }
        ContentValues values = new ContentValues();
        values.put(LAST_UPDATED, System.currentTimeMillis());
        updateExportStatus(values, fileBaseName, exportType, fileFormat);
    }

    public void markDependentUploadsFailed(@NonNull String fileBaseName, @NonNull FileFormat fileFormat, @Nullable String reason) {
        if (!isValidFileBaseName(fileBaseName)) {
            Log.w(TAG, "Cannot mark dependent uploads failed for invalid fileBaseName: " + fileBaseName);
            return;
        }
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot mark dependent uploads failed for " + fileBaseName);
            return;
        }

        try {
            db.beginTransaction();
            ContentValues values = new ContentValues();
            values.put(EXPORT_STATUS, ExportStatus.FINISHED_FAILED.name());
            values.put(ANSWER, reason != null ? reason : "Prerequisite local file export failed");
            values.put(LAST_UPDATED, System.currentTimeMillis());

            int updated = db.update(ExportStatusDbHelper.TABLE,
                    values,
                    WorkoutSummaries.FILE_BASE_NAME + "=? AND " + FORMAT + "=? AND " + TYPE + "!=? AND " + EXPORT_STATUS + "=?",
                    new String[]{fileBaseName, fileFormat.name(), ExportType.FILE.name(), ExportStatus.WAITING.name()});

            db.setTransactionSuccessful();
            Log.i(TAG, "Marked " + updated + " dependent uploads failed for " + fileBaseName + " (" + fileFormat + ")");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to mark dependent uploads failed for " + fileBaseName + " (" + fileFormat + ")", t);
        } finally {
            if (db.inTransaction()) {
                db.endTransaction();
            }
        }
    }

    public void markDependentUploadsFailed(@NonNull String fileBaseName, @Nullable String reason) {
        if (!isValidFileBaseName(fileBaseName)) {
            Log.w(TAG, "Cannot mark dependent uploads failed for invalid fileBaseName: " + fileBaseName);
            return;
        }
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot mark dependent uploads failed for " + fileBaseName);
            return;
        }

        try {
            db.beginTransaction();
            ContentValues values = new ContentValues();
            values.put(EXPORT_STATUS, ExportStatus.FINISHED_FAILED.name());
            values.put(ANSWER, reason != null ? reason : "Prerequisite local file export failed");
            values.put(LAST_UPDATED, System.currentTimeMillis());

            int updated = db.update(ExportStatusDbHelper.TABLE,
                    values,
                    WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "!=? AND " + EXPORT_STATUS + "=?",
                    new String[]{fileBaseName, ExportType.FILE.name(), ExportStatus.WAITING.name()});

            db.setTransactionSuccessful();
            Log.i(TAG, "Marked " + updated + " dependent uploads failed for " + fileBaseName);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to mark dependent uploads failed for " + fileBaseName, t);
        } finally {
            if (db.inTransaction()) {
                db.endTransaction();
            }
        }
    }



    private static class StaleCandidate {
        final String fileBaseName;
        final ExportType type;
        final FileFormat format;
        final long lastUpdated;
        final int runAttemptCount;

        StaleCandidate(String fileBaseName, ExportType type, FileFormat format, long lastUpdated, int runAttemptCount) {
            this.fileBaseName = fileBaseName;
            this.type = type;
            this.format = format;
            this.lastUpdated = lastUpdated;
            this.runAttemptCount = runAttemptCount;
        }
    }

    public void healStaleProcessingRows() {
        healStaleProcessingRows(null);
    }

    public void healStaleProcessingRows(@Nullable String targetFileBaseName) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) return;

        List<StaleCandidate> candidates = new ArrayList<>();
        String selection;
        String[] selectionArgs;

        if (targetFileBaseName != null) {
            selection = WorkoutSummaries.FILE_BASE_NAME + "=? AND " + EXPORT_STATUS + "=?";
            selectionArgs = new String[]{targetFileBaseName, ExportStatus.PROCESSING.name()};
        } else {
            selection = EXPORT_STATUS + "=?";
            selectionArgs = new String[]{ExportStatus.PROCESSING.name()};
        }

        // Phase 1: Candidate scan (read-only query, cursor closed immediately)
        try (Cursor cursor = db.query(ExportStatusDbHelper.TABLE,
                new String[]{WorkoutSummaries.FILE_BASE_NAME, TYPE, FORMAT, LAST_UPDATED, RUN_ATTEMPT_COUNT},
                selection,
                selectionArgs,
                null, null, null)) {
            while (cursor.moveToNext()) {
                String baseName = cursor.getString(0);
                ExportType type = EnumUtils.safeValueOfOrNull(ExportType.class, cursor.getString(1), TAG);
                FileFormat format = EnumUtils.safeValueOfOrNull(FileFormat.class, cursor.getString(2), TAG);
                long lastUpdated = cursor.isNull(3) ? 0L : cursor.getLong(3);
                int attemptCount = cursor.isNull(4) ? 0 : cursor.getInt(4);
                if (type != null && format != null) {
                    candidates.add(new StaleCandidate(baseName, type, format, lastUpdated, attemptCount));
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error scanning stale candidate rows", t);
            return;
        }

        if (candidates.isEmpty()) return;

        long now = System.currentTimeMillis();

        for (StaleCandidate candidate : candidates) {
            long elapsed = now - candidate.lastUpdated;
            // Check if elapsed exceeds timeout threshold
            if (elapsed < MAX_EXPORT_DURATION_MS) {
                continue;
            }

            // Phase 2: WorkManager double-check without holding DB lock
            boolean isWorkActive = false;
            try {
                if (mContext != null) {
                    WorkManager wm = WorkManager.getInstance(mContext);
                    List<WorkInfo> infos = wm.getWorkInfosByTag(candidate.fileBaseName).get();
                    if (infos != null) {
                        for (WorkInfo info : infos) {
                            WorkInfo.State state = info.getState();
                            if (state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED) {
                                isWorkActive = true;
                                break;
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                // If WorkManager is unavailable / throws in tests, treat as inactive
                Log.d(TAG, "WorkManager state check skipped/failed for " + candidate.fileBaseName + ": " + t.getMessage());
            }

            if (isWorkActive) {
                Log.i(TAG, "Aborting stale healing for active WorkManager task: " + candidate.fileBaseName);
                continue;
            }

            // Phase 3: Short-lived SQLite transaction re-verifying LAST_UPDATED and RUN_ATTEMPT_COUNT
            try {
                db.beginTransaction();
                try (Cursor cursor = db.query(ExportStatusDbHelper.TABLE,
                        new String[]{LAST_UPDATED, RUN_ATTEMPT_COUNT},
                        WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "=? AND " + FORMAT + "=? AND " + EXPORT_STATUS + "=?",
                        new String[]{candidate.fileBaseName, candidate.type.name(), candidate.format.name(), ExportStatus.PROCESSING.name()},
                        null, null, null)) {
                    if (cursor.moveToFirst()) {
                        long currentLastUpdated = cursor.isNull(0) ? 0L : cursor.getLong(0);
                        int currentAttemptCount = cursor.isNull(1) ? 0 : cursor.getInt(1);

                        // If heartbeat was updated or attempt count changed concurrently, abort
                        if (currentLastUpdated == candidate.lastUpdated && currentAttemptCount == candidate.runAttemptCount) {
                            if (System.currentTimeMillis() - currentLastUpdated >= MAX_EXPORT_DURATION_MS) {
                                ContentValues values = new ContentValues();
                                values.put(EXPORT_STATUS, ExportStatus.FINISHED_FAILED.name());
                                values.put(ANSWER, "Export timed out / process terminated");
                                values.put(LAST_UPDATED, System.currentTimeMillis());

                                db.update(ExportStatusDbHelper.TABLE,
                                        values,
                                        WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "=? AND " + FORMAT + "=?",
                                        new String[]{candidate.fileBaseName, candidate.type.name(), candidate.format.name()});
                                db.setTransactionSuccessful();
                                Log.w(TAG, "Healed stale PROCESSING row to FINISHED_FAILED for " + candidate.fileBaseName + " (" + candidate.format + ")");
                            }
                        } else {
                            Log.i(TAG, "Aborted stale healing for " + candidate.fileBaseName + ": concurrent update detected (" + candidate.lastUpdated + " -> " + currentLastUpdated + ")");
                        }
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed committing stale healing transaction for " + candidate.fileBaseName, t);
            } finally {
                if (db.inTransaction()) {
                    db.endTransaction();
                }
            }
        }
    }

    public EnumMap<ExportType, EnumMap<FileFormat, ExportStatus>> getExportStatusMap(String fileBaseName) {
        if (DEBUG) Log.d(TAG, "getExportStatus");

        if (!isValidFileBaseName(fileBaseName)) {
            return null;
        }

        healStaleProcessingRows(fileBaseName);

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot get the export status for " + fileBaseName + " will return null");
            return null;
        }

        EnumMap<ExportType, EnumMap<FileFormat, ExportStatus>> result = new EnumMap<>(ExportType.class);

        for (ExportType exportType : ExportType.values()) {
            EnumMap<FileFormat, ExportStatus> enumMap = new EnumMap<>(FileFormat.class);
            for (FileFormat fileFormat : FileFormat.values()) {
                try (Cursor cursor = db.query(ExportStatusDbHelper.TABLE,
                        new String[]{EXPORT_STATUS},
                        WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "=? AND " + FORMAT + "=?",
                        new String[]{fileBaseName, exportType.name(), fileFormat.name()},
                        null,
                        null,
                        null)) {
                    ExportStatus status = CursorUtils.mapFirst(cursor, null, c -> {
                        int colIdx = c.getColumnIndex(EXPORT_STATUS);
                        return colIdx >= 0 ? EnumUtils.safeValueOfOrNull(ExportStatus.class, c.getString(colIdx), TAG) : null;
                    });
                    if (status != null) {
                        enumMap.put(fileFormat, status);
                    }
                }
            }
            result.put(exportType, enumMap);
        }

        if (DEBUG) Log.d(TAG, "getExportStatus finished");

        return result;
    }

    public static class ExportRow {
        public final ExportType type;
        public final FileFormat format;
        public final ExportStatus status;
        public final String answer;
        public final long lastUpdated;
        public final int runAttemptCount;

        public ExportRow(ExportType type, FileFormat format, ExportStatus status, String answer) {
            this(type, format, status, answer, 0L, 0);
        }

        public ExportRow(ExportType type, FileFormat format, ExportStatus status, String answer, long lastUpdated, int runAttemptCount) {
            this.type = type;
            this.format = format;
            this.status = status;
            this.answer = answer;
            this.lastUpdated = lastUpdated;
            this.runAttemptCount = runAttemptCount;
        }
    }

    public java.util.List<ExportRow> getExportRows(String fileBaseName) {
        if (!isValidFileBaseName(fileBaseName)) {
            return new java.util.ArrayList<>();
        }

        healStaleProcessingRows(fileBaseName);

        java.util.List<ExportRow> result = new java.util.ArrayList<>();
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(ExportStatusDbHelper.TABLE,
                new String[]{TYPE, FORMAT, EXPORT_STATUS, ANSWER, LAST_UPDATED, RUN_ATTEMPT_COUNT},
                WorkoutSummaries.FILE_BASE_NAME + "=?",
                new String[]{fileBaseName},
                null, null, null)) {
            while (cursor.moveToNext()) {
                ExportType type = EnumUtils.safeValueOfOrNull(ExportType.class, cursor.getString(0), TAG);
                FileFormat format = EnumUtils.safeValueOfOrNull(FileFormat.class, cursor.getString(1), TAG);
                ExportStatus status = EnumUtils.safeValueOfOrNull(ExportStatus.class, cursor.getString(2), TAG);
                if (type == null || format == null || status == null) {
                    Log.w(TAG, "Skipping corrupted or unrecognized export row");
                    continue;
                }
                String answer = cursor.getString(3);
                long lastUpdated = cursor.isNull(4) ? 0L : cursor.getLong(4);
                int attemptCount = cursor.isNull(5) ? 0 : cursor.getInt(5);
                result.add(new ExportRow(type, format, status, answer, lastUpdated, attemptCount));
            }
        }
        return result;
    }

    public EnumMap<FileFormat, ExportStatus> getExportStatusMap(String fileBaseName, ExportType exportType) {
        if (DEBUG) Log.d(TAG, "getExportStatus " + fileBaseName + " " + exportType);

        if (!isValidFileBaseName(fileBaseName)) {
            return null;
        }

        healStaleProcessingRows(fileBaseName);

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot get the export status for " + fileBaseName + " " + exportType + " will return null");
            return null;
        }

        EnumMap<FileFormat, ExportStatus> result = new EnumMap<>(FileFormat.class);

        for (FileFormat fileFormat : FileFormat.values()) {
            try (Cursor cursor = db.query(ExportStatusDbHelper.TABLE,
                    new String[]{EXPORT_STATUS},
                    WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "=? AND " + FORMAT + "=?",
                    new String[]{fileBaseName, exportType.name(), fileFormat.name()},
                    null,
                    null,
                    null)) {
                ExportStatus status = CursorUtils.mapFirst(cursor, null, c -> {
                    int colIdx = c.getColumnIndex(EXPORT_STATUS);
                    return colIdx >= 0 ? EnumUtils.safeValueOfOrNull(ExportStatus.class, c.getString(colIdx), TAG) : null;
                });
                if (status != null) {
                    result.put(fileFormat, status);
                }
            }
        }

        if (DEBUG) Log.d(TAG, "getExportStatus finished");

        return result;
    }


    public ExportStatus getExportStatus(@NonNull ExportInfo exportInfo) {
        if (DEBUG) Log.d(TAG, "getExportStatus");

        if (exportInfo == null || !isValidFileBaseName(exportInfo.getFileBaseName())) {
            return null;
        }

        healStaleProcessingRows(exportInfo.getFileBaseName());

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot get the export status for " + exportInfo + " will return null");
            return null;
        }

        try (Cursor cursor = db.query(ExportStatusDbHelper.TABLE,
                new String[]{EXPORT_STATUS},
                WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "=? AND " + FORMAT + "=?",
                new String[]{exportInfo.getFileBaseName(), exportInfo.getExportType().name(), exportInfo.getFileFormat().name()},
                null,
                null,
                null)) {
            return CursorUtils.mapFirst(cursor, null, c -> {
                int colIdx = c.getColumnIndex(EXPORT_STATUS);
                return colIdx >= 0 ? EnumUtils.safeValueOfOrNull(ExportStatus.class, c.getString(colIdx), TAG) : null;
            });
        }
    }


    public String getExportAnswer(@NonNull ExportInfo exportInfo) {
        if (DEBUG) Log.d(TAG, "getExportAnswer");

        if (exportInfo == null || !isValidFileBaseName(exportInfo.getFileBaseName())) {
            return null;
        }

        healStaleProcessingRows(exportInfo.getFileBaseName());

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot get the export answer for " + exportInfo + " will return null");
            return null;
        }

        try (Cursor cursor = db.query(ExportStatusDbHelper.TABLE,
                new String[]{ANSWER},
                WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "=? AND " + FORMAT + "=?",
                new String[]{exportInfo.getFileBaseName(), exportInfo.getExportType().name(), exportInfo.getFileFormat().name()},
                null,
                null,
                null)) {
            return CursorUtils.mapFirst(cursor, null, c -> {
                int colIdx = c.getColumnIndex(ANSWER);
                return colIdx >= 0 ? c.getString(colIdx) : null;
            });
        }
    }

    public void deleteWorkout(String baseFileName) {
        if (!isValidFileBaseName(baseFileName)) {
            Log.w(TAG, "Ignoring deleteWorkout for invalid baseFileName: " + baseFileName);
            return;
        }
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot delete " + baseFileName);
            return;
        }

        db.delete(ExportStatusDbHelper.TABLE, WorkoutSummaries.FILE_BASE_NAME + "=?", new String[]{baseFileName});
    }


    protected static class ExportStatusDbHelper extends SQLiteOpenHelper {
        public static final String DB_NAME = "ExportStatus.db";
        public static final int DB_VERSION = 2;
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
                + ANSWER + " text, "
                + LAST_UPDATED + " INTEGER DEFAULT 0, "
                + RUN_ATTEMPT_COUNT + " INTEGER DEFAULT 0)";

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
            if (oldVersion < 2) {
                try {
                    db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + LAST_UPDATED + " INTEGER DEFAULT 0");
                } catch (Throwable t) {
                    Log.w(TAG, "Column " + LAST_UPDATED + " might already exist: " + t.getMessage());
                }
                try {
                    db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + RUN_ATTEMPT_COUNT + " INTEGER DEFAULT 0");
                } catch (Throwable t) {
                    Log.w(TAG, "Column " + RUN_ATTEMPT_COUNT + " might already exist: " + t.getMessage());
                }
            }
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            super.onOpen(db);
            ensureColumnExists(db, LAST_UPDATED, "INTEGER DEFAULT 0");
            ensureColumnExists(db, RUN_ATTEMPT_COUNT, "INTEGER DEFAULT 0");
        }

        private void ensureColumnExists(SQLiteDatabase db, String columnName, String columnDef) {
            try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + TABLE + ")", null)) {
                boolean exists = false;
                int nameIdx = cursor.getColumnIndex("name");
                while (cursor.moveToNext()) {
                    if (nameIdx >= 0 && columnName.equalsIgnoreCase(cursor.getString(nameIdx))) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + columnName + " " + columnDef);
                }
            } catch (Throwable t) {
                Log.w(TAG, "Could not verify/add column " + columnName + ": " + t.getMessage());
            }
        }

    }


}
