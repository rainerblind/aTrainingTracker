# Implementation Plan - SCRUM-129: Fix Database Schema Inconsistency

Resolve the `SQLiteException` by correcting the database `onCreate` and `onUpgrade` logic for the `ViewsTable`.

## 1. Requirements Mapping
- **Requirement**: `REQ-PRO-005` (Database Schema Integrity)
- **Test ID**: `TST-UNT-011` (TrackingViewsDb Integrity)

## 2. Impact Analysis
- **Core Component**: `TrackingViewsDatabaseManager.java`.
- **Root Cause**: The `onCreate` method incorrectly uses `CREATE_VIEWS_TABLE_V9`, which is missing the `ShowElevationProfile` column introduced in Version 10. Fresh installations or certain upgrade paths results in a query crash when `TrackingViewsRepository.kt` expects this column.
- **Risk**: Critical (Fatal crash on tracking screen).
- **Side Effects**: This fix will stabilize all future installations and attempt to repair existing broken installations.

## 3. Proposed Changes

### 3.1 Correct Schema Definitions (`TrackingViewsDatabaseManager.java`)
- Define `CREATE_VIEWS_TABLE_V10` which includes `ShowElevationProfile`.
- Update `onCreate` to use `CREATE_VIEWS_TABLE_V10`.

### 3.2 Robust Migration (`TrackingViewsDatabaseManager.java`)
- Refine the `onUpgrade` block for version 10:
    - Use a helper method to check if the column exists before attempting to add it (idempotent migration).
    - If the column is missing, execute the `ALTER TABLE` command and synchronize the default value with `ShowMap`.

## 4. Verification Plan
- **Unit Verification (v10 Fresh Install)**:
    - Wipe data / Fresh install simulation.
    - Verify that `ViewsTable` contains `ShowElevationProfile`.
- **Integration Verification**:
    - Launch the app and enter the Tracking screen.
    - Verify that the query for tracking views no longer throws an exception.
