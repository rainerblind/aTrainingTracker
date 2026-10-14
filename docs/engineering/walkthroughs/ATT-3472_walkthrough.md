# Stage 5 Walkthrough - ATT-3472: Fix automated background backup execution and WorkManager scheduling

**Ticket**: [ATT-3472](https://atrainingtracker.atlassian.net/browse/ATT-3472)  
**Stage 5 Subtask**: [ATT-3513](https://atrainingtracker.atlassian.net/browse/ATT-3513) (`[Test]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-MIG-036` (`Verified`)  
**Test Spec ID**: `TST-MIG-033` (`Verified`)  
**Branch**: `feature/ATT-3472`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Executive Summary & Defect Remediation

Automated background backups silently failed to execute in production on devices where users launch the application frequently (at least once per day). Coupled root causes were forensically identified and resolved:
1. **Periodic Timer Reset Storm (`ExistingPeriodicWorkPolicy.REPLACE`)**: In `BackupWorker.Companion.schedule(context)`, unique periodic work was registered using `ExistingPeriodicWorkPolicy.REPLACE`. Because `schedule()` executes on every cold start in `TrainingApplication.onCreate()`, each launch cancelled existing work and reset the 24-hour initial delay into the future.
   - *Fix*: Transitioned to `ExistingPeriodicWorkPolicy.UPDATE` which preserves the monotonic countdown across cold starts. Added one-time migration queue sanitization (`workmanager_backup_policy_migrated_v1`) to break legacy stuck loops on existing devices.
2. **Default Preference Value Discrepancy**: `prefs_dropbox.xml` and `schedule()` defaulted `"automated_backups"` to `true`, while `BackupWorker.doWork()` queried `prefs.getBoolean("automated_backups", false)`. Fresh installations without manual switch toggling skipped execution.
   - *Fix*: Aligned `BackupWorker.doWork()` to default `"automated_backups"` to `true`.
3. **Application Lifecycle Initialization Precedence**: In `TrainingApplication.onCreate()`, `BackupWorker.Companion.schedule(this)` was invoked before `PreferenceManager.setDefaultValues()` and `cSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)`.
   - *Fix*: Moved preference initialization immediately after `cAppContext = getApplicationContext()`, guaranteeing configuration availability prior to worker scheduling.
4. **Cloud-Tethered Execution Trap & Unbounded/Unsafe File Creation**: `BackupWorker.doWork()` previously skipped backup creation entirely if neither Dropbox nor Google Drive was connected. Furthermore, `BackupManager.createBackup` previously deleted and rewrote directly to `aTrainingTracker_backup.attbackup` in `cacheDir` without atomic staging or process-safe locking.
   - *Fix*: Decoupled local backup generation from cloud connectivity. Backups are now stored in persistent internal storage (`context.filesDir/backups/`) immune to OS cache clearing. Multi-process safe dual locking (`ReentrantLock` + `FileLock` on `backup.lock`) coordinates execution, and co-located atomic move (`Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`) with `try-finally` staging file deletion guarantees strictly $O(1)$ storage bounds and zero file corruption on interrupted runs.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MIG-036` (*Automated Background Backup WorkManager Scheduling, Monotonic Timer Preservation, and Bounded Local Storage*) $\to$ **Status: Verified**
* **Test Spec**: `TST-MIG-033` (*Automated Background Backup WorkManager Scheduling, Monotonic Timer Preservation, and Bounded Local Storage Verification*) $\to$ **Status: Verified**

---

## 3. Preserved Invariants & Invariant Verification

1. **Monotonic Schedule Preservation & Queue Sanitization**: WorkManager periodic requests use `ExistingPeriodicWorkPolicy.UPDATE`, preserving elapsed execution timers. Queue sanitization clears legacy `REPLACE` queues on upgrade.
2. **Persistent Storage in `context.filesDir`**: Backups reside in persistent internal storage (`context.filesDir/backups/`) rather than `cacheDir`, ensuring local backups survive OS memory pressure.
3. **Co-located Atomic Staging & Guaranteed Temp Cleanup**: Staging file (`aTrainingTracker_backup.tmp`) resides on the same mount point and moves atomically via `Files.move(..., ATOMIC_MOVE)`. Stale `.tmp` files are purged before bundling, and `try-finally` ensures staging files are deleted on error.
4. **Multi-Process Safe Concurrency (`FileLock` + `ReentrantLock`)**: Concurrency is guarded by an in-memory `ReentrantLock` and an inter-process `FileLock` on `backup.lock`. If locked by a manual backup, `BackupWorker.doWork()` yields and returns `Result.retry()`.
5. **Storage Boundedness ($O(1)$ Invariant)**: Exactly one canonical `.attbackup` archive is maintained on device.
6. **Decoupled Cloud Upload & Controlled Retry**: Local backup is created first. If cloud accounts are unlinked, status is `"LOCAL_SUCCESS"` and returns `Result.success()`. If cloud uploads fail, local backup remains intact and `Result.retry()` is scheduled via exponential backoff (`1 hour`).
7. **Clean-Room Regression**: 100% full suite test pass rate (`./gradlew testDebugUnitTest`).

---

## 4. Test Verification Results

### Unit Test Suites
1. **`BackupWorkerTest.kt`** (6 tests passing):
   - `testCompanionSchedule_usesExistingPeriodicWorkPolicyUPDATE`: verifies `ExistingPeriodicWorkPolicy.UPDATE`, queue migration flag, and fallback default.
   - `testDoWork_whenDisabled_skipsExecution`: verifies clean skip when preference is false.
   - `testDoWork_whenLockContention_returnsRetry`: verifies non-blocking yield and retry on lock contention.
   - `testDoWork_whenNoCloudConnected_createsLocalBackupAndSucceeds`: verifies local backup creation and `"LOCAL_SUCCESS"` status without cloud provider.
   - `testDoWork_whenLocalBackupFails_returnsRetry`: verifies error status and retry if bundling fails.
   - `testDoWork_whenCloudUploadFails_preservesLocalBackupAndReturnsRetry`: verifies local backup preservation and retry on upload failure.

2. **`BackupManagerTest.kt`** (5 tests passing):
   - `testGetBackupDir_createsAndReturnsPersistentBackupsDirectory`: verifies persistent internal directory `filesDir/backups`.
   - `testAcquireLock_acquiresAndReleasesLockCleanly`: verifies dual `ReentrantLock` and `FileLock` lifecycle.
   - `testCreateBackup_cleansUpStaleTempFileBeforeExecution`: verifies purge of stale `.tmp` files from aborted runs.
   - `testCreateBackup_storageIsBoundedToSingleCanonicalArchive`: verifies $O(1)$ single-file storage bounds.
   - `testSourceCodeInvariants_atomicMoveAndTryFinallyGuaranteed`: verifies `ATOMIC_MOVE` and `try-finally` cleanup invariants.

3. **`BackupWorkerGoogleDriveTest.kt`** (updated and passing):
   - `testDoWork_whenNeitherCloudConnected_createsLocalBackupAndSavesStatus`: asserts local backup and `"LOCAL_SUCCESS"`.
   - `testDoWork_whenGoogleDriveConnected_triggersUpload`: asserts cloud upload and `"SUCCESS"`.

### Clean-Room Full Suite Regression Run
* Command: `./gradlew testDebugUnitTest`
* Result: **100% Tests Passed** (0 failures, 0 errors, 0 regressions across all modules in 2m 57s).
