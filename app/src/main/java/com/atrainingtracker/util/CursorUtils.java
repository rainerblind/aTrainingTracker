package com.atrainingtracker.util;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility functions for safe, null-checked, and bounds-checked SQLite Cursor navigation.
 * Guarantees zero {@link android.database.CursorIndexOutOfBoundsException} on empty result sets.
 *
 * <p><b>Exception Contract:</b>
 * CursorUtils does NOT swallow any {@link android.database.SQLException},
 * {@link android.database.sqlite.SQLiteException}, or {@link RuntimeException}.
 * Any exception encountered during cursor mapping or movement is propagated cleanly to the caller,
 * allowing standard {@code try-with-resources} blocks to close resources deterministically during unwind.</p>
 */
public final class CursorUtils {

    @FunctionalInterface
    public interface CursorMapper<T> {
        /**
         * Maps the current row of the cursor to an object of type {@code T}.
         *
         * @param cursor The cursor positioned at a valid row.
         * @return The mapped object.
         */
        T map(@NonNull Cursor cursor);
    }

    private CursorUtils() {
        // Utility class
    }

    /**
     * Moves to the first row of the cursor if present and maps it; otherwise returns null.
     *
     * @param cursor The cursor to navigate (can be null).
     * @param mapper The mapper function to apply to the first row.
     * @param <T> The return type.
     * @return The mapped result or {@code null}.
     */
    @Nullable
    public static <T> T mapFirst(@Nullable Cursor cursor, @NonNull CursorMapper<T> mapper) {
        return mapFirst(cursor, null, mapper);
    }

    /**
     * Moves to the first row of the cursor if present and maps it; otherwise returns the specified default value.
     *
     * @param cursor The cursor to navigate (can be null).
     * @param defaultValue The fallback value returned if cursor is null or empty.
     * @param mapper The mapper function to apply to the first row.
     * @param <T> The return type.
     * @return The mapped result or {@code defaultValue}.
     */
    @Nullable
    public static <T> T mapFirst(@Nullable Cursor cursor, @Nullable T defaultValue, @NonNull CursorMapper<T> mapper) {
        if (cursor != null && cursor.moveToFirst()) {
            return mapper.map(cursor);
        }
        return defaultValue;
    }

    /**
     * Moves to the last row of the cursor if present and maps it; otherwise returns null.
     *
     * @param cursor The cursor to navigate (can be null).
     * @param mapper The mapper function to apply to the last row.
     * @param <T> The return type.
     * @return The mapped result or {@code null}.
     */
    @Nullable
    public static <T> T mapLast(@Nullable Cursor cursor, @NonNull CursorMapper<T> mapper) {
        return mapLast(cursor, null, mapper);
    }

    /**
     * Moves to the last row of the cursor if present and maps it; otherwise returns the specified default value.
     *
     * @param cursor The cursor to navigate (can be null).
     * @param defaultValue The fallback value returned if cursor is null or empty.
     * @param mapper The mapper function to apply to the last row.
     * @param <T> The return type.
     * @return The mapped result or {@code defaultValue}.
     */
    @Nullable
    public static <T> T mapLast(@Nullable Cursor cursor, @Nullable T defaultValue, @NonNull CursorMapper<T> mapper) {
        if (cursor != null && cursor.moveToLast()) {
            return mapper.map(cursor);
        }
        return defaultValue;
    }

    /**
     * Iterates over all rows in the cursor using {@code moveToNext()} and maps each row into an unmodifiable list.
     * If the cursor is null or empty, returns {@link Collections#emptyList()}.
     *
     * @param cursor The cursor to navigate (can be null).
     * @param mapper The mapper function applied to each row.
     * @param <T> The element type.
     * @return An unmodifiable list containing all mapped rows, or empty list.
     */
    @NonNull
    public static <T> List<T> mapList(@Nullable Cursor cursor, @NonNull CursorMapper<T> mapper) {
        if (cursor == null) {
            return Collections.emptyList();
        }
        List<T> list = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                list.add(mapper.map(cursor));
            } while (cursor.moveToNext());
        } else {
            // Fallback for mock environments where only moveToNext() is stubbed
            while (cursor.moveToNext()) {
                list.add(mapper.map(cursor));
            }
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Safely moves the cursor to the first row. Returns the cursor if positioned successfully,
     * or {@code null} if the cursor was null or empty.
     *
     * @param cursor The cursor to position.
     * @return The cursor positioned at the first row, or {@code null}.
     */
    @Nullable
    public static Cursor moveToFirstOrNull(@Nullable Cursor cursor) {
        if (cursor != null && cursor.moveToFirst()) {
            return cursor;
        }
        return null;
    }

    /**
     * Safely moves the cursor to the last row. Returns the cursor if positioned successfully,
     * or {@code null} if the cursor was null or empty.
     *
     * @param cursor The cursor to position.
     * @return The cursor positioned at the last row, or {@code null}.
     */
    @Nullable
    public static Cursor moveToLastOrNull(@Nullable Cursor cursor) {
        if (cursor != null && cursor.moveToLast()) {
            return cursor;
        }
        return null;
    }
}
