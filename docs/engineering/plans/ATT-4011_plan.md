# Stage 3: Implementation Plan - ATT-4011: Backup snapshots are inconsistent under concurrent writes and leak DB helpers

**Ticket**: [ATT-4011](https://rainerblind.atlassian.net/browse/ATT-4011)  
**Sub-task**: [ATT-4233](https://rainerblind.atlassian.net/browse/ATT-4233) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-DAT-031` (*Atomic Concurrent-Safe Database Backup Snapshots, Storage Verification, and Singleton Helper Lifecycle*)  
**Test Spec ID**: `TST-DAT-026` (*Atomic Concurrent-Safe Database Backup Snapshots, Storage Verification, and Singleton Helper Lifecycle Verification Suite*)  
**Branch**: `feature/ATT-4011`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Root Cause Analysis

During backup bundle generation (`BackupManager.createBackup()`), the application checkpoints active SQLite databases using `DatabaseConnectionManager.quiesceAllDatabases(closeConnections = false)`. However:
1. **Unvalidated Checkpoint & Excluded WAL Files**:
   - `quiesceAllDatabases` executes `PRAGMA wal_checkpoint(TRUNCATE)` without checking if SQLite returned `BUSY` (`result == 1`). If background threads (`TrackerService` writing sensor telemetry, `ExportWorker` writing export status, Strava sync updating upload tables) hold an open transaction, the checkpoint fails to truncate or quiesce the WAL.
   - Immediately following the checkpoint, `zipFolder` archives the raw `.db` files from `dataDir/databases` while strictly excluding all `-wal`, `-shm`, and `-journal` files:
     ```kotlin
     if (file.name.endsWith("-journal") || file.name.endsWith("-shm") || file.name.endsWith("-wal")) {
         continue
     }
     ```
   - Consequently, uncheckpointed pages remaining in the WAL due to `BUSY` are omitted from the `.attbackup` archive, resulting in data loss.
2. **Torn Page Reads Under Concurrent Writers**:
   - If SQLite background threads or writer threads write to the `.db` file while `FileInputStream` reads it in 8KB blocks, the database file can be copied in a torn, internally inconsistent state. Restoring such an archive produces a corrupted database that fails `PRAGMA integrity_check`.
3. **Leaked Database Helper Instances**:
   - In `DatabaseConnectionManager.kt:72-90`, `getActiveDatabases(context)` instantiates new unmanaged `SQLiteOpenHelper` instances on every call:
     ```kotlin
     try { StravaUploadDbHelper(context).writableDatabase } catch (_: Throwable) { null },
     try { EquipmentDbHelper(context).writableDatabase } catch (_: Throwable) { null },
     try { ActiveDevicesDbHelper(context).writableDatabase } catch (_: Throwable) { null }
     ```
   - Similar unclosed ad-hoc helper instantiations occur in `LegacyImportEngine.kt`, `WorkoutDeletionHelper.java`, `StravaUploader.kt`, `TrackerService.java`, `StravaDataPurgeManager.kt`, and repository classes.
   - Every ad-hoc instantiation leaks an open database handle, connection pool, and file descriptor.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-DAT-031` (*Atomic Concurrent-Safe Database Backup Snapshots, Storage Verification, and Singleton Helper Lifecycle*)
  * `REQ-DAT-031.1`: Free disk space pre-verification before staging (`context.filesDir.usableSpace >= totalDbSize * 1.5`), aborting cleanly returning `null` if space is insufficient.
  * `REQ-DAT-031.2`: API 30+ primary snapshot pipeline via SQLite `VACUUM INTO` into temporary staging directory (`backups/temp_db_snapshot/`), creating self-contained, fully checkpointed, non-WAL snapshots without blocking concurrent writers.
  * `REQ-DAT-031.3`: API 26–29 fallback snapshot pipeline with writer-synchronized checkpoint retry backoff on `BUSY` (up to 3 retries: 10ms, 25ms, 50ms) under `DatabaseConnectionManager.databaseLock`.
  * `REQ-DAT-031.4`: Thread-safe singleton helper lifecycle for `StravaUploadDbHelper`, `EquipmentDbHelper`, and `ActiveDevicesDbHelper` via `getInstance(Context)` with double-checked locking, eliminating all ad-hoc instantiations and connection leaks.
  * `REQ-DAT-031.5`: Staging snapshot `PRAGMA integrity_check` verification before packaging, atomic move to canonical backup file, and staging cleanup in `finally`.
* **Test Mapping**: `TST-DAT-026` (*Atomic Concurrent-Safe Database Backup Snapshots, Storage Verification, and Singleton Helper Lifecycle Verification Suite*)
  * `TST-DAT-026.1`: `testCreateBackup_insufficientStorage_abortsCleanly` in `BackupManagerTest.kt`.
  * `TST-DAT-026.2`: `testVacuumIntoSnapshot_realSqlite_atomicAndConsistent` in `BackupManagerRealSqliteTest.kt`.
  * `TST-DAT-026.3`: `testCheckpointWithRetry_handlesBusyWithBackoff` in `BackupManagerTest.kt`.
  * `TST-DAT-026.4`: `testBackupDuringActiveConcurrentWrites_preservesIntegrity` in `BackupManagerConcurrencyTest.kt`.
  * `TST-DAT-026.5`: `testDbHelperSingletons_identityAndReset` in `DbHelperSingletonTest.kt`.
  * `TST-DAT-026.6`: `testQuiesceAllDatabases_zeroLeakedHelperInstances` in `DatabaseConnectionManagerTest.kt`.
  * `TST-DAT-026.7`: 9-language localization audit (confirming zero string modifications).
  * `TST-DAT-026.8`: Clean-room full suite regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Archive Compatibility**: `.attbackup` format and internal layout (`databases/`, `shared_prefs/`, `datastore/`) remain 100% compatible with existing backup/restore logic (`RestoreManager.kt`).
2. **MinSdk 26 Compatibility**: System operates safely on Android 8.0 (API 26) through Android 15+ (API 35+). `VACUUM INTO` is strictly gated behind `Build.VERSION.SDK_INT >= Build.VERSION_CODES.R` (API 30+).
3. **Passive `onOpen()` Invariant (`REQ-CON-017 / ATT-3990`)**: Database helpers perform zero mutations in `onOpen()`.
4. **Single Atomic Commit per Ticket (Rule 44)**: Changes remain uncommitted on `feature/ATT-4011` until Stage 5 clean-room verification is signed off.
5. **Zero Leaked Open Helpers**: Every SQLite database open helper in the app must be managed as a thread-safe singleton or explicitly closed.

---

## 4. Proposed Architectural Changes (SWE.2)

```
+-------------------------------------------------------------------------------------------------+
|                                 BackupManager.createBackup(context)                             |
+-------------------------------------------------------------------------------------------------+
| 1. Acquire jvmLock & backup.lock                                                                |
| 2. Verify Storage:                                                                              |
|    val totalDbSize = calculateTotalDatabaseSize(context)                                        |
|    if (context.filesDir.usableSpace < totalDbSize * 1.5) {                                      |
|        Log.w(TAG, "Insufficient storage space for backup snapshot staging");                    |
|        return null;                                                                             |
|    }                                                                                            |
| 3. Create Staging Directory: File(backupDir, "temp_db_snapshot")                                |
| 4. Snapshot Pipeline:                                                                           |
|    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {                                        |
|        // API 30+ Primary Path: Zero writer blocking via SQLite VACUUM INTO                     |
|        snapshotDatabasesViaVacuum(context, temp_db_snapshot)                                    |
|    } else {                                                                                     |
|        // API 26-29 Fallback Path: Writer-synchronized checkpoint + retry                       |
|        snapshotDatabasesViaFallback(context, temp_db_snapshot)                                  |
|    }                                                                                            |
| 5. Integrity Verification:                                                                      |
|    verifySnapshotIntegrity(temp_db_snapshot) // PRAGMA integrity_check                          |
| 6. Bundle Zip:                                                                                  |
|    zipFolder(temp_db_snapshot, "databases", zos)                                                |
|    zipFolder(sharedPrefsDir, "shared_prefs", zos)                                               |
|    zipFolder(datastoreDir, "datastore", zos)                                                    |
| 7. Atomic Move: tempBackupFile -> canonicalBackupFile                                           |
| 8. Finally: Cleanup temp_db_snapshot & Release Locks                                            |
+-------------------------------------------------------------------------------------------------+
```

### 4.1 Component Details

#### 1. `StravaUploadDbHelper.java`
- Introduce thread-safe singleton pattern:
  ```java
  private static volatile StravaUploadDbHelper sInstance;

  @NonNull
  public static StravaUploadDbHelper getInstance(@NonNull Context context) {
      if (sInstance == null) {
          synchronized (StravaUploadDbHelper.class) {
              if (sInstance == null) {
                  sInstance = new StravaUploadDbHelper(context.getApplicationContext());
              }
          }
      }
      return sInstance;
  }

  @androidx.annotation.VisibleForTesting
  public static void resetForTesting() {
      synchronized (StravaUploadDbHelper.class) {
          if (sInstance != null) {
              try { sInstance.close(); } catch (Exception ignored) {}
              sInstance = null;
          }
      }
  }
  ```

#### 2. `EquipmentDbHelper.java`
- Introduce thread-safe singleton pattern:
  ```java
  private static volatile EquipmentDbHelper sInstance;

  @NonNull
  public static EquipmentDbHelper getInstance(@NonNull Context context) {
      if (sInstance == null) {
          synchronized (EquipmentDbHelper.class) {
              if (sInstance == null) {
                  sInstance = new EquipmentDbHelper(context.getApplicationContext());
              }
          }
      }
      return sInstance;
  }

  @androidx.annotation.VisibleForTesting
  public static void resetForTesting() {
      synchronized (EquipmentDbHelper.class) {
          if (sInstance != null) {
              try { sInstance.close(); } catch (Exception ignored) {}
              sInstance = null;
          }
      }
  }
  ```
- Replace line 94 `new ActiveDevicesDbHelper(mContext)` with `ActiveDevicesDbHelper.getInstance(mContext)`.

#### 3. `ActiveDevicesDbHelper.java`
- Introduce thread-safe singleton pattern:
  ```java
  private static volatile ActiveDevicesDbHelper sInstance;

  @NonNull
  public static ActiveDevicesDbHelper getInstance(@NonNull Context context) {
      if (sInstance == null) {
          synchronized (ActiveDevicesDbHelper.class) {
              if (sInstance == null) {
                  sInstance = new ActiveDevicesDbHelper(context.getApplicationContext());
              }
          }
      }
      return sInstance;
  }

  @androidx.annotation.VisibleForTesting
  public static void resetForTesting() {
      synchronized (ActiveDevicesDbHelper.class) {
          if (sInstance != null) {
              try { sInstance.close(); } catch (Exception ignored) {}
              sInstance = null;
          }
      }
  }
  ```

#### 4. `DatabaseConnectionManager.kt`
- Add shared `databaseLock`:
  ```kotlin
  val databaseLock = java.util.concurrent.locks.ReentrantReadWriteLock()
  ```
- Refactor `getActiveDatabases(context)`:
  - Call `StravaUploadDbHelper.getInstance(context).writableDatabase`
  - Call `EquipmentDbHelper.getInstance(context).writableDatabase`
  - Call `ActiveDevicesDbHelper.getInstance(context).writableDatabase`
- Refactor `quiesceAllDatabases(context, closeConnections = false)`:
  - Check `wal_checkpoint` result code. If result == 1 (`BUSY`), retry with backoff up to 3 times (10ms, 25ms, 50ms).
  - Do NOT close database connections if `closeConnections = false`.

#### 5. Ad-Hoc Call Site Elimination
Update all constructor calls in:
- `LegacyImportEngine.kt`: `StravaUploadDbHelper.getInstance(context).deleteWorkout(...)`
- `WorkoutDeletionHelper.java`: `StravaUploadDbHelper.getInstance(context)`
- `StravaUploader.kt`: `EquipmentDbHelper.getInstance(mContext)`
- `TrackerService.java`: `ActiveDevicesDbHelper.getInstance(this)`
- `StravaDataPurgeManager.kt`: `StravaUploadDbHelper.getInstance(context)`, `EquipmentDbHelper.getInstance(context)`
- `StravaEquipmentSynchronizeThread.java`: `EquipmentDbHelper.getInstance(mContext).getWritableDatabase()`
- Repositories & ViewModels: `EquipmentDbHelper.getInstance(...)`, `StravaUploadDbHelper.getInstance(...)`
- `ImportBackupTabsScreen.kt`: `EquipmentDbHelper.getInstance(context).equipmentItems`

#### 6. `BackupManager.kt`
- Implement storage calculation:
  ```kotlin
  fun calculateTotalDatabaseSize(context: Context): Long {
      val databasesDir = File(context.applicationInfo.dataDir, "databases")
      if (!databasesDir.exists()) return 0L
      val files = databasesDir.listFiles() ?: return 0L
      return files.filter { it.isFile && (it.name.endsWith(".db") || it.name.endsWith("-wal")) }.sumOf { it.length() }
  }
  ```
- Implement `snapshotDatabasesViaVacuum(context, stagingDir)`:
  - For each active `db` in `DatabaseConnectionManager.getActiveDatabases(context)`:
    - `val targetFile = File(stagingDir, File(db.path).name)`
    - `db.rawQuery("VACUUM INTO ?", arrayOf(targetFile.absolutePath)).use { it.moveToFirst() }`
  - Also copy any non-active `.db` files from `databasesDir` if not already snapshotted.
- Implement `snapshotDatabasesViaFallback(context, stagingDir)`:
  - Acquire `DatabaseConnectionManager.databaseLock.writeLock()`
  - Checkpoint all active databases with retry backoff.
  - Copy `.db` and any non-empty `-wal` files to `stagingDir`.
  - Release lock.
- Implement `verifySnapshotIntegrity(stagingDir)`:
  - Iterate `.db` files in `stagingDir`. Open SQLiteDatabase read-only and query `PRAGMA integrity_check`.
  - Assert result is `"ok"`.
- Zip from `stagingDir` as `"databases"`.
- Clean up `stagingDir` in `finally`.

---

## 5. UI Consistency (Rule 23)

* **Scope**: UI Consistency: N/A.
* **Justification**: This ticket concerns persistent storage backup snapshotting, SQLite WAL concurrency, and database helper singleton lifecycle. Zero UI composables, themes, layout resources, or user-facing strings are modified.

---

## 6. Atomic Implementation Steps

### Step 1: Singleton Lifecycle Transformation for Database Helpers
- **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/db/StravaUploadDbHelper.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/database/EquipmentDbHelper.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/database/ActiveDevicesDbHelper.java`
- Add `getInstance(Context context)` with double-checked locking using application context.
- Add `@VisibleForTesting public static void resetForTesting()`.
- Update `EquipmentDbHelper.java:94` to call `ActiveDevicesDbHelper.getInstance(mContext)`.

### Step 2: Eliminate Ad-Hoc Call Sites Across Codebase
- **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/migration/DatabaseConnectionManager.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutDeletionHelper.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/StravaUploader.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaDataPurgeManager.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaEquipmentSynchronizeThread.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/EquipmentRepository.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentViewModel.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`
- Replace direct `new ...DbHelper(context)` or `...DbHelper(context)` with `...DbHelper.getInstance(context)`.

### Step 3: Hardened Checkpoint & Locking in `DatabaseConnectionManager.kt`
- Add `val databaseLock = ReentrantReadWriteLock()`.
- Update `quiesceAllDatabases` to check `wal_checkpoint` result code and retry up to 3 times with exponential backoff on `BUSY`.
- Expose `checkpointWithRetry(db: SQLiteDatabase): Boolean`.

### Step 4: Storage Pre-Verification & Staging Snapshot Pipeline in `BackupManager.kt`
- Implement storage calculation: `calculateTotalDatabaseSize(context)`.
- Pre-check storage: `usableSpace >= totalDbSize * 1.5`, abort cleanly returning `null` if insufficient.
- Implement API 30+ `VACUUM INTO` snapshotting to `temp_db_snapshot/`.
- Implement API 26–29 writer-synchronized fallback snapshotting under `databaseLock`.
- Implement `PRAGMA integrity_check` verification on staged database files.
- Zip `temp_db_snapshot` as `databases/`.
- Ensure clean cleanup of `temp_db_snapshot/` in `finally`.

### Step 5: Author Unit & Integration Test Suites
- `DbHelperSingletonTest.kt`: verify singleton identity, thread-safety, and `resetForTesting()` behavior.
- `DatabaseConnectionManagerTest.kt`: verify zero leaked open helpers during `quiesceAllDatabases` / `checkpointAll`.
- `BackupManagerTest.kt`: verify storage pre-check abort, retry backoff on `BUSY`, and staging cleanup.
- `BackupManagerRealSqliteTest.kt`: verify real SQLite `VACUUM INTO` snapshotting with WAL data in Robolectric.
- `BackupManagerConcurrencyTest.kt`: verify backup snapshot consistency during active concurrent background writes.

### Step 6: Targeted Verification & Clean-Room Full Suite Regression
- Run targeted tests:
  `./gradlew testDebugUnitTest --tests "*DbHelperSingletonTest*" --tests "*DatabaseConnectionManagerTest*" --tests "*BackupManager*"`
- Run full clean-room suite:
  `./gradlew testDebugUnitTest`

---

## 7. Review and Gate Audit Preparation

- Update subtask `ATT-4233` description with this document.
- Transition `ATT-4233` to `In Überprüfung`.
- Run Gate 3 review audit via `python3 tools/review_agent.py audit ATT-4233`.
- Upon passing Gate 3, execute `python3 tools/jira_util.py move ATT-4233 freigabe` to advance parent `ATT-4011` to `Implementation`.
