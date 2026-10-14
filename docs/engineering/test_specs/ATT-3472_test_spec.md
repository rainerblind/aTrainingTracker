# Stage 2: Test Specification - ATT-3472: Fix automated background backup execution and WorkManager scheduling

**Ticket**: [ATT-3472](https://atrainingtracker.atlassian.net/browse/ATT-3472)  
**Sub-task**: [ATT-3510](https://atrainingtracker.atlassian.net/browse/ATT-3510) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-MIG-036` (*Automated Background Backup WorkManager Scheduling, Monotonic Timer Preservation, and Local Rolling Storage*), amending `REQ-MIG-003` and `REQ-MIG-004`  
**Test Spec ID**: `TST-MIG-033`  
**Branch**: `feature/ATT-3472`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Requirement Specification (REQ-MIG-036)

### 1.1 Problem Statement & Background
Forensic analysis revealed that automated periodic backups have silently failed to execute in production on devices where users launch the application frequently (at least once per day). Three coupled root causes were identified:
1. **Periodic Timer Reset Storm (`ExistingPeriodicWorkPolicy.REPLACE`)**: In `BackupWorker.Companion.schedule(context)`, the unique periodic work request is registered using `ExistingPeriodicWorkPolicy.REPLACE`. Because `BackupWorker.schedule()` is invoked during `TrainingApplication.onCreate()` on every cold start, each app launch cancels the existing periodic work and resets the 24-hour initial delay into the future. For active daily users, the periodic timer is perpetually postponed, preventing execution indefinitely.
2. **Default Preference Inconsistency**: In `schedule()`, `prefs.getBoolean("automated_backups", true)` defaults to `true`, matching `defaultValue="true"` in `prefs_dropbox.xml`. However, in `BackupWorker.doWork()`, `prefs.getBoolean("automated_backups", false)` defaults to `false`. On devices where the user has not manually toggled the switch in Dropbox settings, the worker is scheduled but immediately terminates with `Result.success()` and skips execution.
3. **Premature SharedPreferences Lifecycle**: In `TrainingApplication.onCreate()`, `initWorkManager()` and `BackupWorker.schedule()` were called on line 1198 before `cSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)` was initialized on line 1213, leaving static preference access vulnerable to null states.
4. **Absence of Local Rolling Backups**: `BackupWorker.doWork()` skipped execution entirely if neither Dropbox nor Google Drive was connected. If cloud credentials expire or an athlete trains offline without cloud storage, zero automated backups are retained on device.

### 1.2 Functional Requirements
1. **Monotonic WorkManager Scheduling (`BackupWorker.kt`)**:
   - `BackupWorker.Companion.schedule(context)` SHALL register `automated_backup_work` using `ExistingPeriodicWorkPolicy.UPDATE`.
   - WorkManager's `UPDATE` policy preserves the accumulated elapsed run time and monotonic execution window of existing periodic requests, preventing app launches from resetting the schedule.
2. **Preference Fallback Parity (`BackupWorker.kt`)**:
   - `BackupWorker.doWork()` SHALL query `prefs.getBoolean("automated_backups", true)`, guaranteeing strict parity with `schedule()`, `DropboxSettingsDialog.kt`, and `prefs_dropbox.xml`.
3. **Application Lifecycle Initialization Ordering (`TrainingApplication.java`)**:
   - `cSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)` SHALL be initialized prior to `initWorkManager()` and worker scheduling in `TrainingApplication.onCreate()`.
4. **Local Rolling Backup Creation & Cloud Upload Decoupling (`BackupWorker.kt`)**:
   - When automated backups are enabled (`automated_backups == true`), `BackupWorker.doWork()` SHALL generate a valid local `.attbackup` archive using `BackupManager.createBackup(context)` regardless of whether cloud providers are connected.
   - If no cloud provider (Dropbox or Google Drive) is connected, `BackupWorker` SHALL record `last_backup_timestamp = System.currentTimeMillis()`, set `last_backup_status = "LOCAL_SUCCESS"`, and return `Result.success()`.
   - If cloud provider(s) are connected, `BackupWorker` SHALL attempt upload. If any connected upload succeeds, it SHALL update `last_backup_timestamp` and set `last_backup_status` to `"SUCCESS"` (or `"PARTIAL_SUCCESS"` if one provider failed). If all connected uploads fail, it SHALL retain the local backup, record status, and return `Result.retry()`.

### 1.3 Requirement Archaeology & Chesterton's Fence
* **Original Requirements**: `REQ-MIG-003` (Automated Background Backups via WorkManager) and `REQ-MIG-004` (Dropbox and Google Drive Backup Providers).
* **Why `ExistingPeriodicWorkPolicy.REPLACE` was originally chosen**: During initial prototyping under WorkManager 2.7, `REPLACE` was commonly used to ensure changes to interval preferences took effect immediately. However, WorkManager 2.8+ introduced `UPDATE`, which cleanly applies interval updates while preserving elapsed execution timers.
* **Preserved Invariants**:
  - `WORK_NAME = "automated_backup_work"` remains identical.
  - Work constraints (`NetworkType.UNMETERED`, `setRequiresBatteryNotLow(true)`) remain identical.
  - Exponential backoff policy (`1 hour`) remains identical.
  - Exception safety: `schedule()` catches and logs `Throwable` to ensure non-crashing behavior on devices with missing or broken WorkManager providers (`WorkManagerResilienceTest`).

---

## 2. Acceptance Criteria (Given-When-Then)

### Criterion 1: Monotonic Timer Preservation on App Launch
* **Given** an active periodic work request registered for `automated_backup_work` with remaining execution time $T_{rem} < T_{period}$,
* **When** `TrainingApplication.onCreate()` executes during app startup and invokes `BackupWorker.schedule(context)`,
* **Then** the work request is enqueued with `ExistingPeriodicWorkPolicy.UPDATE`, preserving the monotonic schedule without resetting the initial delay to $T_{period}$.

### Criterion 2: Fallback Default Parity
* **Given** a device with fresh installation where `"automated_backups"` has not been explicitly toggled,
* **When** `BackupWorker.doWork()` executes,
* **Then** `prefs.getBoolean("automated_backups", true)` evaluates to `true`, and the worker proceeds with backup creation.

### Criterion 3: Local Backup Creation Without Cloud Provider
* **Given** an athlete with automated backups enabled and neither Dropbox nor Google Drive connected,
* **When** `BackupWorker.doWork()` executes,
* **Then** `BackupManager.createBackup(context)` is called, a local `.attbackup` bundle is generated, `last_backup_timestamp` is updated, `last_backup_status` is set to `"LOCAL_SUCCESS"`, and the worker returns `Result.success()`.

### Criterion 4: Cloud Upload Execution When Connected
* **Given** an athlete with Dropbox or Google Drive connected,
* **When** `BackupWorker.doWork()` executes,
* **Then** local backup is created and dispatched to the connected cloud providers, recording upload timestamps upon success.

### Criterion 5: WorkManager Exception Resilience
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
| `TST-MIG-033.4` | Architecture / Ordering | `TrainingApplication.java` | Asserts `cSharedPreferences` is initialized before `initWorkManager()` and `BackupWorker.schedule()` |
| `TST-MIG-033.5` | Clean-Room Suite | All test modules | `./gradlew testDebugUnitTest` runs with 100% pass rate and zero regressions |

---

## 4. ASPICE Traceability Matrix

| Requirement | Test Spec ID | Target File | Verification Method | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MIG-036.1` (Monotonic Scheduling) | `TST-MIG-033.1` | `BackupWorker.kt` | `BackupWorkerTest.kt` | Specified |
| `REQ-MIG-036.2` (Default Parity) | `TST-MIG-033.2` | `BackupWorker.kt` | `BackupWorkerTest.kt` | Specified |
| `REQ-MIG-036.3` (Local Backup) | `TST-MIG-033.3` | `BackupWorker.kt` | `BackupWorkerTest.kt` | Specified |
| `REQ-MIG-036.4` (Lifecycle Ordering) | `TST-MIG-033.4` | `TrainingApplication.java` | `TrainingApplicationTest.kt` | Specified |
| `REQ-PRO-001` (Clean-Room Zero Regressions) | `TST-MIG-033.5` | Entire Codebase | `./gradlew testDebugUnitTest` | Specified |
