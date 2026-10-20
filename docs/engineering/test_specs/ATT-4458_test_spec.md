# Stage 2: Requirement & Test Specification - ATT-4458: [Bug] Fix LOCAL_SUCCESS / PARTIAL_SUCCESS status display and expose local rolling backup restore in UI

**Ticket**: [ATT-4458](https://rainerblind.atlassian.net/browse/ATT-4458)  
**Sub-task**: Pending Submission (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Requirement Mapping**: `REQ-MIG-036` (*Automated Background Backup WorkManager Scheduling, Monotonic Timer Preservation, Conditional Network Constraints, Status Diagnostics and UI Rolling Restore*)  
**Test Spec ID**: `TST-MIG-033` (*Automated Background Backup WorkManager Scheduling, Conditional Constraints, Status Diagnostics and Local UI Restore Verification*)  
**Branch**: `feature/ATT-4458`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-MIG-036)

### 1.1 Problem Statement & Rationale
Identified during code review and human verification of `ATT-3472` (*Automated Background Backup WorkManager Scheduling, Monotonic Timer Preservation, and Bounded Local Storage*):

1. **False Error Display for `LOCAL_SUCCESS` and Raw Enum Display for `PARTIAL_SUCCESS` (`ImportBackupTabsScreen.kt:1261-1266`)**:
   `BackupWorker.doWork()` writes `last_backup_status` as `"LOCAL_SUCCESS"` (when no cloud provider is connected), `"PARTIAL_SUCCESS"` (when one connected cloud provider succeeds and another fails), or `"SUCCESS"`. However, `ImportBackupTabsScreen.kt` strictly checked `val isSuccess = info.status == "SUCCESS"` and otherwise rendered red error text (`"Last backup failed: LOCAL_SUCCESS"`, `"Last backup failed: PARTIAL_SUCCESS"`), creating false error alarms for successful local backups. Furthermore, neither `PARTIAL_SUCCESS` nor upload failures indicated *which* target provider (Local, Dropbox, Google Drive) succeeded or failed.
2. **Internal Rolling Backup (`filesDir/backups/aTrainingTracker_backup.attbackup`) Unreachable from Restore Tab**:
   `BackupManager.createBackup()` creates the rolling backup inside internal app storage (`context.filesDir/backups/aTrainingTracker_backup.attbackup`). The existing *Local Restore* button in `RestoreTabContent` invokes the Android system document picker (`OpenDocument`), which cannot browse internal `context.filesDir/backups/`. Athletes had no UI entry point to restore directly from the automated local rolling backup.
3. **Unnecessary Wi-Fi Constraint for Local-Only Backups (`BackupWorker.kt:135`)**:
   `BackupWorker.schedule()` unconditionally configured `NetworkType.UNMETERED`, preventing local-only rolling backups from running when the athlete has no cloud provider connected and is offline or on cellular data.

### 1.2 Functional & Architectural Requirements
The system SHALL refine `REQ-MIG-036` with the following clauses:

1. **Conditional WorkManager Network Constraints (`REQ-MIG-036.7`)**:
   - In `BackupWorker.Companion.schedule(context: Context)`, WorkManager network constraints SHALL be evaluated dynamically based on active cloud backup configurations:
     ```kotlin
     val cloudBackupEnabled = (TrainingApplication.uploadToDropbox() && TrainingApplication.readDropboxCredential() != null) ||
         (TrainingApplication.uploadToGoogleDrive() && TrainingApplication.uploadBackupToGoogleDrive())
     val networkType = if (cloudBackupEnabled) NetworkType.UNMETERED else NetworkType.NOT_REQUIRED
     ```
   - When only local rolling backups are active, WorkManager constraints SHALL set `NetworkType.NOT_REQUIRED`, permitting automated backups to run periodically even when offline or on cellular connectivity.
   - When at least one cloud provider (Dropbox or Google Drive) is active, constraints SHALL set `NetworkType.UNMETERED` to conserve cellular data.

2. **Per-Target Status Persistence (`REQ-MIG-036.8`)**:
   - In `BackupWorker.doWork()`, the system SHALL persist serialized per-target outcomes for Local, Dropbox, and Google Drive into SharedPreferences key `"last_backup_targets"` (e.g. `local:SUCCESS;dropbox:FAILED;google_drive:SUCCESS`) alongside `"last_backup_status"`, preserving fine-grained diagnostic visibility for the UI.

3. **Localized Status Presentation & Target Breakdown (`REQ-MIG-036.9`)**:
   - In `BackupTabContent`, the system SHALL evaluate `lastBackupInfo` and render localized status text:
     - `SUCCESS`: `R.string.last_backup` ("Last backup: %1$s") in `MaterialTheme.colorScheme.primary`.
     - `LOCAL_SUCCESS`: `R.string.last_backup_local_success` ("Last backup: %1$s (Local only)") in `MaterialTheme.colorScheme.primary`.
     - `PARTIAL_SUCCESS`: `R.string.last_backup_partial_success` ("Last backup: %1$s (Partial)") in `MaterialTheme.colorScheme.tertiary`.
     - Failures: `R.string.last_backup_failed_format` ("Last backup failed: %1$s") in `MaterialTheme.colorScheme.error`.
   - The UI SHALL render discrete target status badges for Local, Dropbox, and Google Drive displaying success (check) or failure (cross) icons.
   - Hardcoded English string templates (e.g. `"Last backup failed: ${info.status}"`) SHALL be completely eliminated.

4. **Direct UI Rolling Backup Restoration (`REQ-MIG-036.10`)**:
   - `RestoreTabContent` SHALL provide a dedicated *"Restore from Automatic Local Backup"* button showing the timestamp of the internal rolling backup file (`context.filesDir/backups/aTrainingTracker_backup.attbackup`).
   - The button SHALL be enabled only when `aTrainingTracker_backup.attbackup` exists and has non-zero length.
   - Initiating the restore SHALL be guarded by the existing destructive full restore confirmation dialog (`AlertDialog` with `restore_warning_title` / `restore_warning_message`).
   - Upon confirmation, `BackupRestoreViewModel.restoreFromLocalBackup(context)` SHALL invoke `MigrationEngine.performFullRestore` directly against `context.filesDir/backups/aTrainingTracker_backup.attbackup`, bypassing the Android document picker.

5. **Elimination of Literal String Concatenations on Restore Buttons (`REQ-MIG-036.11`)**:
   - Hardcoded `"Local: "` and `"Dropbox: "` string concatenations (`"Local: " + stringResource(R.string.restore_backup)`) SHALL be replaced with dedicated localized string resources `R.string.restore_from_file` and `R.string.restore_from_dropbox`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1: Local-Only Automated Backup Network Gating**:
  * *Given* an athlete with no cloud provider connected and automated backups enabled,
  * *When* `BackupWorker.Companion.schedule(context)` executes,
  * *Then* WorkManager constraints are configured with `NetworkType.NOT_REQUIRED`, permitting periodic local execution without Wi-Fi.
* **Criterion 2: Cloud Automated Backup Network Gating**:
  * *Given* an athlete with Dropbox or Google Drive backup connected and enabled,
  * *When* `BackupWorker.Companion.schedule(context)` executes,
  * *Then* WorkManager constraints are configured with `NetworkType.UNMETERED`, preserving mobile data.
* **Criterion 3: `LOCAL_SUCCESS` Status Presentation**:
  * *Given* an automated backup executed with no cloud providers connected,
  * *When* `BackupTabContent` displays the backup status,
  * *Then* the status text renders `R.string.last_backup_local_success` in `MaterialTheme.colorScheme.primary` without error styling or raw enum tokens.
* **Criterion 4: `PARTIAL_SUCCESS` Status Presentation & Target Breakdown**:
  * *Given* an automated backup where local backup and Google Drive succeeded, but Dropbox upload failed,
  * *When* `BackupTabContent` displays the backup status,
  * *Then* the status text renders `R.string.last_backup_partial_success` in `MaterialTheme.colorScheme.tertiary`, and target badges indicate Local (Success), Dropbox (Failed), and Google Drive (Success).
* **Criterion 5: Automatic Local Backup Availability in Restore Tab**:
  * *Given* `context.filesDir/backups/aTrainingTracker_backup.attbackup` exists on device,
  * *When* the athlete navigates to `RestoreTabContent`,
  * *Then* the *"Restore from Automatic Local Backup"* button is enabled and displays the formatted timestamp of the rolling backup.
* **Criterion 6: Destructive Full Restore Confirmation Guard**:
  * *Given* the athlete taps *"Restore from Automatic Local Backup"*,
  * *When* tapped,
  * *Then* the app displays the destructive full restore confirmation `AlertDialog` before executing `MigrationEngine.performFullRestore`.

### 1.4 System Invariants
1. **Monotonic WorkManager Scheduling (`ExistingPeriodicWorkPolicy.UPDATE`)**: Monotonic countdown across app cold starts is strictly preserved.
2. **Storage Boundedness ($O(1)$ Disk Space)**: Automated local backups maintain strictly a single canonical file in `context.filesDir/backups/`.
3. **Co-Located Atomic Move (`ATOMIC_MOVE`)**: Staging files in `context.cacheDir` move atomically to prevent corruption.
4. **Destructive Guard**: Full restore operations MUST always require explicit athlete confirmation via `AlertDialog`.
5. **9-Language Localization Parity**: 100% translation coverage across all 9 canonical locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
6. **Clean-Room Test Pass Rate**: 100% pass rate under `./gradlew testDebugUnitTest`.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  - Refines `REQ-MIG-036` (*Automated Background Backup WorkManager Scheduling, Monotonic Timer Preservation, and Bounded Local Storage*, introduced in `ATT-3472`).
  - Target files: `BackupWorker.kt`, `BackupRestoreViewModel.kt`, `ImportBackupTabsScreen.kt`, `BackupManager.kt`, `values*/strings.xml`.
* **Historical Origin & Commit Trace**:
  - `ATT-3472` (commit `9739a5bc`, Sprint `2026-42.3`): Introduced `ExistingPeriodicWorkPolicy.UPDATE` for monotonic scheduling, decoupled local backups with `LOCAL_SUCCESS`, and co-located atomic move.
  - `ATT-3982` (commit `b8759380`, Sprint `2026-42.10`): Added Zip-Slip path traversal protection (`REQ-MIG-040`).
  - `ATT-4011` (commit `71550c60`, Sprint `2026-42.10`): Added SQLite `VACUUM INTO` atomic snapshotting and DB helper leak protection (`REQ-DAT-031`).
  - Sprint `2026-43.4` (`ATT-4458`): Code review and human verification identified false error display for `LOCAL_SUCCESS`, raw enum for `PARTIAL_SUCCESS`, lack of internal rolling backup access in `RestoreTabContent`, and hardcoded Wi-Fi constraint.
* **Root Reason for Existing Formulation**:
  - `NetworkType.UNMETERED` was inherited from early versions where automated backups were exclusively cloud uploads to Dropbox. When `ATT-3472` enabled local-only backups, the network constraint was left unmodified in `BackupWorker.schedule()`.
  - In `ImportBackupTabsScreen.kt`, the equality check `info.status == "SUCCESS"` predated the addition of `LOCAL_SUCCESS` and `PARTIAL_SUCCESS` in `ATT-3472`.
  - The document picker (`OpenDocument`) was originally the only restore path because automated backups were intended to be manually shared or exported before local rolling retention was implemented.
* **Preservation of Core Invariants**:
  - Monotonic periodic timer preservation (`ExistingPeriodicWorkPolicy.UPDATE`) is strictly preserved.
  - Single canonical file storage bounds ($O(1)$ disk space) are strictly preserved.
  - Co-located atomic move (`ATOMIC_MOVE`) and stale temp cleanup are strictly preserved.
  - Destructive restore warning confirmation dialog is strictly enforced before initiating any restore.
  - 100% clean-room test pass rate under `./gradlew testDebugUnitTest` is strictly preserved.

---

## 2. Test Specification (TST-MIG-033)

### Test Case 1: `testSchedule_whenCloudDisabled_setsNetworkTypeNotRequired` (`TST-MIG-033.9`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupWorkerTest.kt`
* **Preconditions**: Dropbox and Google Drive are disconnected / disabled in `SharedPreferences`.
* **Action**: Invoke `BackupWorker.Companion.buildConstraints(context)` (or verify scheduled WorkRequest constraints).
* **Expected Result**: `constraints.requiredNetworkType == NetworkType.NOT_REQUIRED`.

### Test Case 2: `testSchedule_whenCloudEnabled_setsNetworkTypeUnmetered` (`TST-MIG-033.10`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupWorkerTest.kt`
* **Preconditions**: Dropbox or Google Drive is connected and enabled with valid credentials.
* **Action**: Invoke `BackupWorker.Companion.buildConstraints(context)` (or verify scheduled WorkRequest constraints).
* **Expected Result**: `constraints.requiredNetworkType == NetworkType.UNMETERED`.

### Test Case 3: `testDoWork_persistsTargetBreakdown_onLocalSuccess` (`TST-MIG-033.11`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupWorkerTest.kt`
* **Preconditions**: Automated backups enabled, no cloud providers connected, `BackupManager.createBackup` succeeds.
* **Action**: Execute `worker.doWork()`.
* **Expected Result**: `last_backup_status` is `"LOCAL_SUCCESS"`, and `last_backup_targets` contains `"local:SUCCESS"`.

### Test Case 4: `testDoWork_persistsTargetBreakdown_onPartialSuccessAndFailure` (`TST-MIG-033.12`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupWorkerTest.kt`
* **Preconditions**: Both Dropbox and Google Drive enabled, Dropbox upload fails, Google Drive succeeds.
* **Action**: Execute `worker.doWork()`.
* **Expected Result**: `last_backup_status` is `"PARTIAL_SUCCESS"`, and `last_backup_targets` contains `"local:SUCCESS;dropbox:FAILED;google_drive:SUCCESS"`.

### Test Case 5: `testLastBackupInfo_parsesTargetBreakdownMap` (`TST-MIG-033.13`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModelTest.kt`
* **Preconditions**: `SharedPreferences` contains `last_backup_status = "PARTIAL_SUCCESS"`, `last_backup_targets = "local:SUCCESS;dropbox:FAILED;google_drive:SUCCESS"`.
* **Action**: Read `viewModel.lastBackupInfo.value`.
* **Expected Result**: `info.targets["local"] == true`, `info.targets["dropbox"] == false`, `info.targets["google_drive"] == true`.

### Test Case 6: `testGetAutomaticBackupTimestamp_returnsTimestampWhenFileExists` (`TST-MIG-033.14`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModelTest.kt`
* **Preconditions**: Create mock/temp file `aTrainingTracker_backup.attbackup` in `filesDir/backups/` with length > 0.
* **Action**: Query `viewModel.getAutomaticBackupTimestamp(mockContext)`.
* **Expected Result**: Returns the file's `lastModified()` timestamp; returns `null` when file is deleted.

### Test Case 7: `testRestoreFromLocalBackup_triggersMigrationEngineAndUpdatesUiState` (`TST-MIG-033.15`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModelTest.kt`
* **Preconditions**: `aTrainingTracker_backup.attbackup` exists, mock `MigrationEngine.performFullRestore` returns `true`.
* **Action**: Call `viewModel.restoreFromLocalBackup(mockContext)`.
* **Expected Result**: `MigrationEngine.performFullRestore` is invoked with the local backup file; `uiState` transitions through `Loading` to `Success` (or `Error` on failure).

### Test Case 8: 9-Language Localization & Positional Specifier Audit (`TST-MIG-033.8`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Goal**: Verify string presence and matching `%1$s` tokens across all 9 canonical locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`):
  - `last_backup_local_success`
  - `last_backup_partial_success`
  - `last_backup_failed_format`
  - `restore_automatic_local_backup`
  - `restore_automatic_local_backup_format`
  - `restore_automatic_backup_not_found`
  - `restore_from_file`
  - `restore_from_dropbox`
  - `backup_target_local`
  - `backup_target_dropbox`
  - `backup_target_google_drive`
* **Expected Result**: 100% translation parity, zero missing resources, zero format specifier mismatches.

### Test Case 9: Clean-Room Full Suite Regression Execution (`TST-MIG-033.9`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full unit test suite without regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-MIG-033.9]` | Unit | `BackupWorker.Companion.schedule` | `REQ-MIG-036.7` | Specified |
| `[TST-MIG-033.10]` | Unit | `BackupWorker.Companion.schedule` | `REQ-MIG-036.7` | Specified |
| `[TST-MIG-033.11]` | Unit | `BackupWorker.doWork` | `REQ-MIG-036.8` | Specified |
| `[TST-MIG-033.12]` | Unit | `BackupWorker.doWork` | `REQ-MIG-036.8` | Specified |
| `[TST-MIG-033.13]` | Unit | `BackupRestoreViewModel.LastBackupInfo` | `REQ-MIG-036.9` | Specified |
| `[TST-MIG-033.14]` | Unit | `BackupRestoreViewModel.getAutomaticBackupTimestamp` | `REQ-MIG-036.10` | Specified |
| `[TST-MIG-033.15]` | Unit | `BackupRestoreViewModel.restoreFromLocalBackup` | `REQ-MIG-036.10` | Specified |
| `[TST-MIG-033.8]` | Localization | `TranslationParityTest` | `REQ-MIG-036.11`, `REQ-LOC-001` | Specified |
| `[TST-MIG-033.9]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
