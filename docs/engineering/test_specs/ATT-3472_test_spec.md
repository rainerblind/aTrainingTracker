# Stage 2: Test Specification - ATT-3472: Fix automated background backup execution and WorkManager scheduling

**Ticket**: [ATT-3472](https://atrainingtracker.atlassian.net/browse/ATT-3472)  
**Sub-task**: [ATT-3510](https://atrainingtracker.atlassian.net/browse/ATT-3510) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-MIG-036` (*Automated Background Backup WorkManager Scheduling, Monotonic Timer Preservation, and Bounded Local Storage*), amending `REQ-MIG-003` and `REQ-MIG-004`  
**Test Spec ID**: `TST-MIG-033`  
**Branch**: `feature/ATT-3472`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Requirement Specification (REQ-MIG-036)

### 1.1 Problem Statement & Background
Forensic analysis revealed that automated periodic backups have silently failed to execute in production on devices where users launch the application frequently (at least once per day). Coupled root causes were identified:
1. **Periodic Timer Reset Storm (`ExistingPeriodicWorkPolicy.REPLACE`)**: In `BackupWorker.Companion.schedule(context)`, the unique periodic work request was registered using `ExistingPeriodicWorkPolicy.REPLACE`. Because `BackupWorker.schedule()` is invoked during `TrainingApplication.onCreate()` on every cold start, each app launch cancelled the existing periodic work and reset the 24-hour initial delay into the future. For active daily users, the periodic timer was perpetually postponed, preventing execution indefinitely.
2. **Default Preference Inconsistency**: In `schedule()`, `prefs.getBoolean("automated_backups", true)` defaults to `true`, matching `defaultValue="true"` in `prefs_dropbox.xml`. However, in `BackupWorker.doWork()`, `prefs.getBoolean("automated_backups", false)` defaulted to `false`. On devices where the user had not manually toggled the switch in Dropbox settings, the worker was scheduled but immediately terminated with `Result.success()` and skipped execution.
3. **Preference Initialization Ordering**: The application must guarantee that configuration preferences are fully loaded and accessible before background tasks are scheduled to prevent uninitialized state.
4. **Absence of Local Backup Creation Without Cloud Account**: `BackupWorker.doWork()` skipped execution entirely if neither Dropbox nor Google Drive was connected. If cloud credentials expire or an athlete trains offline without cloud storage, zero automated backups were created on device.

### 1.2 Functional & Architectural Requirements
1. **Monotonic WorkManager Scheduling (`BackupWorker.kt`)**:
   - `BackupWorker.Companion.schedule(context)` SHALL register `automated_backup_work` using `ExistingPeriodicWorkPolicy.UPDATE`.
   - WorkManager's `UPDATE` policy preserves the accumulated elapsed run time and monotonic execution window of existing periodic requests, preventing app launches from resetting the schedule.
2. **Preference Fallback Parity (`BackupWorker.kt`)**:
   - `BackupWorker.doWork()` SHALL query `prefs.getBoolean("automated_backups", true)`, guaranteeing strict parity with `schedule()`, `DropboxSettingsDialog.kt`, and `prefs_dropbox.xml`.
3. **Configuration Availability Before Worker Scheduling**:
   - The system SHALL guarantee that application preferences (`SharedPreferences`) are fully initialized and accessible prior to invoking WorkManager background worker scheduling, ensuring background tasks read valid configuration parameters without encountering uninitialized state.
4. **Bounded Local Backup Creation, Atomic File Replacement & Concurrency Safety (`BackupWorker.kt` & `BackupManager.kt`)**:
   - When automated backups are enabled (`automated_backups == true`), `BackupWorker.doWork()` SHALL generate a valid local `.attbackup` archive using `BackupManager.createBackup(context)` regardless of whether cloud providers are connected.
   - **Atomic File Replacement Mechanism**: To eliminate partial file corruption from unexpected process death during zipping, `BackupManager.createBackup` SHALL write to a temporary staging file (`aTrainingTracker_backup.tmp`) and replace the canonical destination file (`aTrainingTracker_backup.attbackup`) via an atomic rename operation (`renameTo()` / atomic move).
   - **Storage Boundedness**: Local backup generation SHALL be strictly bounded to $O(1)$ storage space by maintaining only the single canonical archive. No unbounded file accumulation is permitted on device disk.
   - **Concurrency Safety**: Backup creation SHALL be guarded by a mutex/lock (`BackupManager.backupMutex`) to synchronize background worker execution with any concurrent manual backup initiated by the user.
5. **Decoupled Cloud Upload & Controlled Retry Policy**:
   - If no cloud provider (Dropbox or Google Drive) is connected, `BackupWorker` SHALL record `last_backup_timestamp = System.currentTimeMillis()`, update `last_backup_status = "LOCAL_SUCCESS"`, and return `Result.success()`.
   - If cloud provider(s) are connected, `BackupWorker` SHALL attempt upload. If any connected upload succeeds, it SHALL update `last_backup_timestamp` and set `last_backup_status` to `"SUCCESS"` (or `"PARTIAL_SUCCESS"`). If all connected uploads fail, the atomic local backup remains safely preserved on disk, `last_backup_status` is updated to `"Upload failed"`, and the worker returns `Result.retry()`.
   - WorkManager's exponential backoff policy (`BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS`) controls retries without tight-loop churning or redundant local file re-zipping when the canonical backup is already valid.

### 1.3 Requirement Archaeology & Chesterton's Fence
* **Original Requirements**: `REQ-MIG-003` (Automated Background Backups via WorkManager) and `REQ-MIG-004` (Dropbox and Google Drive Backup Providers).
* **Why `ExistingPeriodicWorkPolicy.REPLACE` was originally chosen**: During initial prototyping under WorkManager 2.7, `REPLACE` was commonly used to ensure changes to interval preferences took effect immediately. WorkManager 2.8+ introduced `UPDATE`, which cleanly applies interval updates while preserving elapsed execution timers.
* **Preserved Invariants**:
  - `WORK_NAME = "automated_backup_work"` remains identical.
  - Work constraints (`NetworkType.UNMETERED`, `setRequiresBatteryNotLow(true)`) remain identical.
  - Exponential backoff policy (`1 hour`) remains identical.
  - Storage bounds: Single canonical archive with atomic temp-to-final rename prevents disk exhaustion and partial reads.
  - Exception safety: `schedule()` catches and logs `Throwable` to ensure non-crashing behavior on devices with missing or broken WorkManager providers (`WorkManagerResilienceTest`).

---

## 2. Acceptance Criteria (Given-When-Then)

### Criterion 1: Monotonic Timer Preservation on App Launch
* **Given** an active periodic work request registered for `automated_backup_work` with remaining time $T_{rem} < T_{period}$,
* **When** `TrainingApplication.onCreate()` executes during app startup and calls `BackupWorker.schedule(context)`,
* **Then** the work request is enqueued with `ExistingPeriodicWorkPolicy.UPDATE`, preserving the monotonic schedule without resetting the initial delay to $T_{period}$.

### Criterion 2: Fallback Default Parity
* **Given** a device with fresh installation where `"automated_backups"` has not been explicitly toggled,
* **When** `BackupWorker.doWork()` executes,
* **Then** `prefs.getBoolean("automated_backups", true)` evaluates to `true`, and the worker proceeds with backup creation.

### Criterion 3: Bounded Local Backup Creation Without Cloud Provider
* **Given** an athlete with automated backups enabled and neither Dropbox nor Google Drive connected,
* **When** `BackupWorker.doWork()` executes,
* **Then** `BackupManager.createBackup(context)` creates a valid local archive bounded to $O(1)$ space via atomic temp-to-final rename, `last_backup_timestamp` is updated, `last_backup_status` is set to `"LOCAL_SUCCESS"`, and the worker returns `Result.success()`.

### Criterion 4: Atomic Rename & Corruption Guard
* **Given** a backup process in progress,
* **When** the archive is created,
* **Then** data is staged in a temporary file and atomically moved to `aTrainingTracker_backup.attbackup`, ensuring the canonical file is never partially written.

### Criterion 5: Cloud Upload Execution When Connected & Controlled Retry
* **Given** an athlete with Dropbox or Google Drive connected,
* **When** `BackupWorker.doWork()` executes,
* **Then** local backup is created and dispatched to the connected cloud providers. If all connected uploads fail, local backup remains intact and `Result.retry()` is scheduled via exponential backoff.

### Criterion 6: WorkManager Exception Resilience
* **Given** an environment or test harness where `WorkManager.getInstance()` throws an exception,
* **When** `BackupWorker.schedule(context)` is invoked,
* **Then** the exception is caught, logged to Logcat, and the application does not crash.

---

## 3. Test Specification (TST-MIG-033)

| Test ID | Scope | Verification Target | Expected Result |
| :--- | :--- | :--- | :--- |
| `TST-MIG-033.1` | Static/AST Contract | `BackupWorker.kt` | Asserts `ExistingPeriodicWorkPolicy.UPDATE` is used in `enqueueUniquePeriodicWork` |
| `TST-MIG-033.2` | Unit Test | `BackupWorkerTest.kt` | Asserts `doWork()` defaults `"automated_backups"` to `true` when preference key is unset |
| `TST-MIG-033.3` | Unit Test | `BackupWorkerTest.kt` | Asserts `doWork()` creates local backup and returns `Result.success()` with `LOCAL_SUCCESS` when no cloud provider is connected |
| `TST-MIG-033.4` | Atomicity & Storage Bounds | `BackupManagerTest.kt` | Asserts backup creation uses atomic temp-file rename (`.tmp` -> `.attbackup`) and bounds storage to $O(1)$ canonical file |
| `TST-MIG-033.5` | Ordering Contract | `TrainingApplication.java` | Asserts `cSharedPreferences` is initialized before `initWorkManager()` and `BackupWorker.schedule()` |
| `TST-MIG-033.6` | Retry & Backoff Contract | `BackupWorkerTest.kt` | Asserts failed cloud upload returns `Result.retry()` with exponential backoff while preserving local backup |
| `TST-MIG-033.7` | Clean-Room Suite | All test modules | `./gradlew testDebugUnitTest` runs with 100% pass rate and zero regressions |

---

## 4. ASPICE Traceability Matrix

| Requirement | Test Spec ID | Target File | Verification Method | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MIG-036.1` (Monotonic Scheduling) | `TST-MIG-033.1` | `BackupWorker.kt` | `BackupWorkerTest.kt` | Specified |
| `REQ-MIG-036.2` (Default Parity) | `TST-MIG-033.2` | `BackupWorker.kt` | `BackupWorkerTest.kt` | Specified |
| `REQ-MIG-036.3` (Configuration Availability) | `TST-MIG-033.5` | `TrainingApplication.java` | `TrainingApplicationTest.kt` | Specified |
| `REQ-MIG-036.4` (Atomic & Bounded Local Backup) | `TST-MIG-033.3`, `TST-MIG-033.4` | `BackupWorker.kt` / `BackupManager.kt` | `BackupWorkerTest.kt`, `BackupManagerTest.kt` | Specified |
| `REQ-MIG-036.5` (Decoupled Cloud & Controlled Retry) | `TST-MIG-033.6` | `BackupWorker.kt` | `BackupWorkerTest.kt` | Specified |
| `REQ-PRO-001` (Clean-Room Zero Regressions) | `TST-MIG-033.7` | Entire Codebase | `./gradlew testDebugUnitTest` | Specified |
