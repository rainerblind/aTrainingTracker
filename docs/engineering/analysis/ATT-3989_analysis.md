# Stage 1: Problem Domain & Root Cause Analysis - ATT-3989: [High] Full restore deletes current data before validating the backup

**Ticket**: [ATT-3989](https://atrainingtracker.atlassian.net/browse/ATT-3989)  
**Sub-task**: [ATT-4156](https://atrainingtracker.atlassian.net/browse/ATT-4156) (`[Analysis]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Forensic Reproduction

In `com.atrainingtracker.trainingtracker.migration.MigrationEngine.kt:53-70`, `performFullRestore` executes a destructive wipe of the application's core data directories (`databases/`, `shared_prefs/`, `datastore/`) immediately after unpacking an `.attbackup` archive, without validating the integrity, structure, or compatibility of the extracted content:

```kotlin
// 2. Wipe current state
listener?.onProgress("Wiping local data...")
val dataDir = context.applicationInfo.dataDir
val databasesDir = File(dataDir, "databases")
val sharedPrefsDir = File(dataDir, "shared_prefs")
val datastoreDir = File(context.filesDir, "datastore")

Log.w(TAG, "Wiping current application state...")
databasesDir.deleteRecursively()
sharedPrefsDir.deleteRecursively()
datastoreDir.deleteRecursively()

// 3. Move restored files into place
listener?.onProgress("Installing restored data...")
File(tempDir, "databases").copyRecursively(databasesDir)
File(tempDir, "shared_prefs").copyRecursively(sharedPrefsDir)
File(tempDir, "datastore").copyRecursively(datastoreDir)
```

### Forensic Defect Analysis
1. **Premature Destructive Wipe on Incomplete or Non-Database Archives**:
   If a user selects an archive that lacks a `databases/` directory (e.g. an incomplete upload, corrupted download, or foreign zip archive), `databasesDir.deleteRecursively()` deletes all local workout history, routes, sensor configurations, and user preferences. Following this wipe, `File(tempDir, "databases").copyRecursively(databasesDir)` throws `NoSuchFileException`. The exception handler cleans up `tempDir` and returns `false`, leaving the user with **100% irrecoverable data loss**.
2. **Missing SQLite Schema Version (`PRAGMA user_version`) Compatibility Check**:
   If an athlete restores a backup created on a newer version of `aTrainingTracker` (e.g. schema version `V26` from a future release restored onto `V25`), `MigrationEngine` installs the newer databases. Upon subsequent app launch, Android's `SQLiteOpenHelper` detects `newVersion < oldVersion` and invokes `onDowngrade()`. Because standard SQLite helpers throw `SQLiteDowngradeException: Can't downgrade database from version X to Y`, the application enters an unrecoverable, infinite startup crash loop.
3. **Absence of SQLite Database Integrity Verification**:
   Truncated or corrupted SQLite files within a zip archive are installed directly over healthy local databases without running `PRAGMA quick_check` / `integrity_check`.
4. **Open Database File Handle Conflicts**:
   Singleton managers (`WorkoutSummariesDatabaseManager`, `RoutesDatabaseManager`, etc.) hold open `SQLiteDatabase` connections. Deleting database files underneath active SQLite connections without closing them or checkpointing WAL files can lead to file descriptor leaks, `SQLiteDatabaseLockedException`, or memory corruption.
5. **No Rollback Capability (Non-Atomic Restore)**:
   If an error occurs while copying files into place (such as `ENOSPC` disk full or I/O failure), the existing state is already gone and no rollback snapshot exists.

---

## 2. Chesterton's Fence & Requirement Archaeology

1. **Original Purpose**: `MigrationEngine` was introduced to support full offline backup and restore of app data across device migrations. The wipe-and-copy approach was intended to provide a clean state before restoring files so stale database rows or extra preferences from the prior install would not linger.
2. **Why the Flaw Occurred**: The original implementation assumed valid `.attbackup` files produced only by the companion `BackupManager.createBackup()`. It did not guard against partial downloads, corrupted media, foreign archives, or forward-version incompatibilities.
3. **Preserved Invariants**:
   - **All-or-Nothing Atomicity**: A restore operation must either completely succeed or leave the current system state 100% intact.
   - **Full State Replacement on Success**: When a valid backup is supplied, all target directories (`databases/`, `shared_prefs/`, `datastore/`) must be cleanly updated and the app restarted.
   - **Progress Transparency**: UI progress callbacks must report archive inspection, validation, staging, and installation.
   - **No Resource Leaks**: All staging, rollback, and test database handles must be closed and temporary directories cleaned up.

---

## 3. Proposed Resolution Strategy

### Phase 1: Pre-Validation (Zero Modification to Current State)
Before touching any production files in `dataDir`:
1. **Archive Structure Check**:
   - Verify `tempDir/databases` exists, is a directory, and contains valid `.db` files (at minimum `WorkoutSummaries.db`).
2. **SQLite Integrity & Version Validation**:
   - For every `.db` file in `tempDir/databases`:
     - Open in read-only mode (`SQLiteDatabase.OPEN_READONLY`).
     - Execute `PRAGMA quick_check` and assert `"ok"`.
     - Query `PRAGMA user_version`. Verify `backupVersion <= CURRENT_SUPPORTED_VERSION` for each known database.
     - Close read-only test handle.
   - If any check fails, immediately abort, purge `tempDir`, log reason, and return `false`.

### Phase 2: Checkpoint & Close Open Connections
- Invoke checkpoint on active database singletons and close open database instances to ensure no file locking or WAL conflict occurs during file substitution.

### Phase 3: Staged Rollback Snapshot & Atomic Installation
1. Move current `databases/`, `shared_prefs/`, and `datastore/` into a temporary rollback directory: `backup_rollback/`.
2. Copy validated restored files from `tempDir/` into `dataDir/`.
3. If any step throws:
   - Immediately delete partially copied files in `dataDir`.
   - Move original files back from `backup_rollback/` into `dataDir`.
   - Purge `tempDir/` and `backup_rollback/`.
   - Return `false`.
4. If copy succeeds:
   - Purge `backup_rollback/` and `tempDir/`.
   - Trigger app restart (`restartApp(context)`).
   - Return `true`.

### Phase 4: Orphan Staging Cleanup on App Startup
- Extend `SecureZipExtractor.purgeStaleStagingDirs` to also purge any lingering `backup_rollback` directory in `TrainingApplication.java:onCreate()`.

---

## 4. Verification Strategy

1. **Targeted Security & Migration Unit Tests (`FullRestoreValidationSecurityTest.kt`)**:
   - Reject backup missing `databases/` -> verify local data remains intact.
   - Reject backup with corrupted SQLite header/table -> verify local data remains intact.
   - Reject backup with higher `PRAGMA user_version` (future schema) -> verify local data remains intact.
   - Test rollback on simulated I/O failure during file installation -> verify original data is completely restored.
   - Verify successful full restore with valid backup.
2. **Clean-Room Regression Suite**:
   - Run full `./gradlew testDebugUnitTest` ensuring zero regressions across all 32+ tasks.
