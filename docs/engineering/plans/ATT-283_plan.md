# Implementation Plan: Fix Database Upgrade Crash (ATT-283)

Remove redundant transaction management in `WorkoutSummariesDatabaseManager.onUpgrade` to prevent `IllegalStateException` during schema migration.

## Root Cause Analysis
The `WorkoutSummariesDbHelper.onUpgrade` method explicitly calls `db.setTransactionSuccessful()` during the v20 migration. Since `SQLiteOpenHelper` already wraps `onUpgrade` in a transaction and calls `setTransactionSuccessful()` itself, this redundant call violates SQLite's "one call per level" rule, causing a crash when the framework attempts to commit.

## Proposed Changes

### 1. Database Layer: Cleanup Migration Path
#### [MODIFY] [WorkoutSummariesDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java)
- **Requirement**: `REQ-DAT-008` (Atomic Database Upgrades)
- **Test**: `TST-UNT-014` (Upgrade Transaction Integrity)
- **Changes**:
    - Locate the `onUpgrade` block for version 20 (approx. line 1157).
    - Remove the line `db.setTransactionSuccessful();`.
    - Locate the `onUpgrade` block for version 11.
    - Remove the redundant nested transaction (`beginTransaction()`, `setTransactionSuccessful()`, `endTransaction()`) and just keep the functional `addColumn` and migration logic.

## Verification Plan

### Automated Tests
- **Static Audit**: Perform a manual code review of the modified `onUpgrade` method to ensure no `setTransactionSuccessful()` calls remain.
- **Unit Test (TST-UNT-014)**: 
    - Since it's difficult to mock the internal behavior of `SQLiteOpenHelper`, verification will focus on ensuring the project builds and a fresh installation (triggering `onCreate`) works correctly without regression.
    - Static analysis will confirm the removal of the conflicting calls.

### Manual Verification
1. Deploy the app on a device.
2. Ensure it starts and initializes the database correctly (verified via logs).
