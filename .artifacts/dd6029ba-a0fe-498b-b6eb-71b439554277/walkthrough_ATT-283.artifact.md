# Walkthrough: Fix Database Upgrade Crash (ATT-283)

## Goal
The goal of this task was to resolve an `IllegalStateException` that occurred during database schema migration when upgrading the application.

## Root Cause
The crash was caused by manual calls to `db.setTransactionSuccessful()` within the `onUpgrade` callback of `SQLiteOpenHelper`. Android's `SQLiteOpenHelper` already manages an internal transaction for migrations. Calling `setTransactionSuccessful()` twice on the same transaction level is prohibited and triggers the exception.

## Changes Made

### Database Layer
#### [WorkoutSummariesDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java)
- Removed redundant `db.setTransactionSuccessful()` from the version 20 migration block.
- Removed redundant nested transaction (`beginTransaction`, `setTransactionSuccessful`, `endTransaction`) from the version 11 migration block.

### Documentation
- Updated `docs/requirements.md` to include `REQ-DAT-008` (Atomic Database Upgrades).
- Updated `docs/tests.md` to include `TST-UNT-014` (Upgrade Transaction Integrity).
- Updated `docs/project_protocol.md` to mandate RCA documentation in Jira sub-task descriptions.

## Verification Results

### Automated Tests
- **Static Audit**: Confirmed that all explicit `setTransactionSuccessful()` and manual transaction blocks were removed from `onUpgrade`.
- **Build**: Successfully executed `:app:assembleDebug`.

### Jira Tracking
- **RCA Sub-task**: [ATT-475](https://atrainingtracker.atlassian.net/browse/ATT-475)
- **Plan Sub-task**: [ATT-477](https://atrainingtracker.atlassian.net/browse/ATT-477)
- **Test Sub-task**: [ATT-476](https://atrainingtracker.atlassian.net/browse/ATT-476)
- **Verification Result**: PASS (TST-UNT-014) posted to [ATT-283](https://atrainingtracker.atlassian.net/browse/ATT-283).
