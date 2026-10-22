# Stage 3: Implementation Plan - ATT-4665: Add Granular Dropbox Sync Preferences

**Ticket**: [ATT-4665](https://rainerblind.atlassian.net/browse/ATT-4665)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4665`  
**Requirement Mapping**: `REQ-DAT-032` (`REQ-DAT-032.7`), `REQ-MIG-036`  
**Test Mapping**: `TST-DAT-031` (`TST-DAT-031.1`–`5`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Description & Background
Dropbox currently relies on a single coarse `uploadToDropbox` flag, unlike Google Drive's granular preferences. Athletes cannot independently toggle workout uploads versus database backups, enforce Wi-Fi-only transfers, or view account email and sync timestamps. This plan establishes complete architectural parity between Dropbox and Google Drive backend sync services.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-DAT-032` (`REQ-DAT-032.7a`–`d`) & `REQ-MIG-036` (Cloud Backup Gating & Dynamic Constraints)
* **Test Mapping**: `TST-DAT-031` (`TST-DAT-031.1`–`5`, `TrainingApplicationDropboxPreferencesTest`, `ExportManagerDropboxGatingTest`, `BackupWorkerDropboxGatingTest`, `DropboxSyncTimestampTest`)

---

## 3. System Invariants & Preserved Behavior
1. **Backward Compatibility**: All new boolean preferences default to `true`; existing Dropbox users experience zero disruption.
2. **Credential Security**: Account email resides strictly in `AuthCredentialsStore` (`auth_credentials_prefs.xml`) to prevent `.attbackup` archive token leaks (REQ-DAT-032.1).
3. **Concurrency & Atomicity**: `BackupManager.acquireLock` concurrency semantics and atomic file moves remain invariant.
4. **Clean-Room Test Pass Rate**: Full test suite pass rate under `./gradlew testDebugUnitTest` remains 100%.

---

## 4. Proposed Architectural Changes
* **Preference Layer (`TrainingApplication.java`, `AuthCredentialsStore.kt`)**:
  * Add `SP_UPLOAD_WORKOUTS_TO_DROPBOX`, `SP_UPLOAD_BACKUP_TO_DROPBOX`, `SP_DROPBOX_ONLY_WIFI`, `SP_DROPBOX_LAST_SYNC`.
  * Add getters/setters in `TrainingApplication.java`; isolate `KEY_DROPBOX_ACCOUNT_EMAIL` into `AuthCredentialsStore.kt`.
* **Export Pipeline (`ExportManager.java`)**:
  * Gate Dropbox exports on `uploadToDropbox() && uploadWorkoutsToDropbox()` (L149, L330).
  * Enforce `NetworkType.UNMETERED` when `uploadToDropboxOnlyOnWifi()` is true (L421).
* **Backup Scheduling (`BackupWorker.kt`, `BackupRestoreViewModel.kt`, `ImportBackupTabsScreen.kt`)**:
  * Gate Dropbox backup uploads on `uploadToDropbox() && uploadBackupToDropbox() && readDropboxCredential() != null`.
  * Dynamic network constraints: `UNMETERED` if any active cloud provider requires Wi-Fi, `CONNECTED` if cellular allowed, `NOT_REQUIRED` if local only.
* **Sync Auditing & Error Resilience (`DropboxUploader.java`, `DropboxBackupManager.kt`)**:
  * Record `setDropboxLastSyncTimestamp(now)` upon successful upload. Wrap profile lookup in defensive `try/catch`.
* **UI Consistency**: No UI additions in this ticket (backend preferences, workers, and export managers only; UI dialog parity is managed in dedicated ticket).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Code Lock Verification (Rule 4)
* Verify exit code 0 on `python3 tools/jira_util.py check-gate <Impl-Plan-Subtask-Key>`.

### Step 2: Preferences & Credential Isolation
* **Files**: `TrainingApplication.java`, `AuthCredentialsStore.kt`
* **Changes**: Add preference keys and accessors with default `true`; isolate Dropbox email into `auth_credentials_prefs.xml`; purge on disconnect.

### Step 3: ExportManager Gating & Wi-Fi Constraints
* **File**: `ExportManager.java` (L149, L330, L421)
* **Changes**: Gate on `uploadWorkoutsToDropbox()`; enforce `NetworkType.UNMETERED` when `uploadToDropboxOnlyOnWifi()` is true.

### Step 4: BackupWorker & ViewModel Gating and Constraints
* **Files**: `BackupWorker.kt` (L39, L128), `BackupRestoreViewModel.kt` (L255), `ImportBackupTabsScreen.kt` (L158)
* **Changes**: Gate on `uploadBackupToDropbox()`; implement dynamic `buildConstraints(context)` evaluating active providers.

### Step 5: Sync Timestamp Auditing & Defensive Account Profile
* **Files**: `DropboxUploader.java` (L78), `DropboxBackupManager.kt` (L57)
* **Changes**: Invoke `setDropboxLastSyncTimestamp(System.currentTimeMillis())` on upload success; guard `currentAccount` with defensive `try/catch`.

### Step 6: Unit Test Implementation & Verification
* **Files**: `TrainingApplicationDropboxPreferencesTest.kt` (`TST-DAT-031.1`), `ExportManagerDropboxGatingTest.kt` (`TST-DAT-031.2`), `BackupWorkerDropboxGatingTest.kt` (`TST-DAT-031.3`), `DropboxSyncTimestampTest.kt` (`TST-DAT-031.4`)
* **Execution**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.exporter.*" --tests "com.atrainingtracker.trainingtracker.migration.*"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit test execution (`TST-DAT-031.1`–`4`) followed by full test suite (`TST-DAT-031.5`) and requirement governance check.
* **Rollback**: Clean branch isolation on `feature/ATT-4665`; changes discardable via `git checkout -- . && git clean -fd`.
