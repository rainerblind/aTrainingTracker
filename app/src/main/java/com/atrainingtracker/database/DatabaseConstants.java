package com.atrainingtracker.database;

/**
 * Centralized empty-state sentinels and constants for database operations.
 * Eliminates magic literals and prevents sentinel drift across legacy database managers.
 */
public final class DatabaseConstants {

    private DatabaseConstants() {
        // Prevent instantiation
    }

    public static final long INVALID_ID = -1L;
    public static final String EMPTY_STRING = "";
    public static final int EMPTY_COUNT = 0;
    public static final double EMPTY_METRIC = 0.0;
}
