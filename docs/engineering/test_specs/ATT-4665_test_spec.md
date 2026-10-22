# Stage 2: Requirement & Test Specification - ATT-4665: Add Granular Dropbox Sync Preferences

**Ticket**: [ATT-4665](https://rainerblind.atlassian.net/browse/ATT-4665)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `Sprint 2026-43.7` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4665`  
**Requirement Mapping**: `REQ-DAT-032` (`REQ-DAT-032.7`), `REQ-MIG-036`  
**Test Spec ID**: `TST-DAT-031`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Requirement Specification (`REQ-DAT-032` / `REQ-MIG-036`)

### 1.1 Problem Statement & Rationale
Dropbox currently relies on a single coarse `uploadToDropbox` flag, unlike Google Drive's granular controls. Athletes cannot independently toggle workout uploads vs. automated backups, enforce Wi-Fi-only transfers, or view account email and sync timestamps.

### 1.2 Functional & Architectural Requirements
1. **Granular Preferences (`REQ-DAT-032.7a`)**: `TrainingApplication` SHALL provide `uploadWorkoutsToDropbox()`, `uploadBackupToDropbox()`, and `uploadToDropboxOnlyOnWifi()` defaulting to `true`, plus `get/setDropboxLastSyncTimestamp()`.
2. **Isolated Account Email (`REQ-DAT-032.7b`)**: `TrainingApplication.get/setDropboxAccountEmail()` SHALL persist via `AuthCredentialsStore.KEY_DROPBOX_ACCOUNT_EMAIL` (`auth_credentials_prefs.xml`) and purge on credential deletion.
3. **Workout Export Gating & Constraints (`REQ-DAT-032.7c`)**: `ExportManager` SHALL gate Dropbox exports on `uploadToDropbox() && uploadWorkoutsToDropbox()` and enforce `NetworkType.UNMETERED` when `uploadToDropboxOnlyOnWifi()` is true.
4. **Backup Provider Gating (`REQ-MIG-036.1`)**: `BackupWorker`, `BackupRestoreViewModel`, and `ImportBackupTabsScreen` SHALL gate Dropbox backups on `uploadToDropbox() && uploadBackupToDropbox() && readDropboxCredential() != null`.
5. **Dynamic Backup Network Constraints (`REQ-MIG-036.2`)**: `BackupWorker.buildConstraints()` SHALL set `NetworkType.UNMETERED` if any active cloud provider requires Wi-Fi only, `CONNECTED` if cloud is enabled without Wi-Fi restriction, or `NOT_REQUIRED` if local only.
6. **Sync Timestamp & Defensive Profile (`REQ-DAT-032.7d`)**: `DropboxUploader` and `DropboxBackupManager` SHALL call `setDropboxLastSyncTimestamp(now)` on success. `DbxClientV2.users().currentAccount` lookups SHALL catch `DbxException` and degrade to `null`.

### 1.3. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-DAT-032` (`REQ-DAT-032.1`, `REQ-DAT-032.6`) and `REQ-MIG-036` (item 11). Targets `TrainingApplication.java`, `AuthCredentialsStore.kt`, `ExportManager.java`, `BackupWorker.kt`, `BackupRestoreViewModel.kt`, `ImportBackupTabsScreen.kt`, `DropboxUploader.java`, `DropboxBackupManager.kt`.
* **Historical Origin & Commit Trace**: `REQ-DAT-032` introduced in `ATT-4023` (commit `203baa27`), refined in `ATT-4459` (commit `8ad50842`). `REQ-MIG-036` introduced in `ATT-3472` (commit `9fa41095`), refined in `ATT-4458` (commit `22916f21`) and `ATT-4587`.
* **Root Reason for Existing Formulation**: Monolithic Dropbox boolean predated Google Drive's granular sync switches. `BackupWorker` forced `UNMETERED` for all cloud backups.
* **Preservation of Core Invariants**: Credential isolation in `auth_credentials_prefs.xml`, default-true backward compatibility, and `BackupManager.acquireLock` concurrency remain 100% intact.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Granular Export Gating)**: *Given* `uploadToDropbox=true` and `uploadWorkoutsToDropbox=false`, *When* workout export is evaluated, *Then* Dropbox work request is NOT scheduled.
* **Criterion 2 (Export Wi-Fi Constraint)**: *Given* `uploadToDropboxOnlyOnWifi=true`, *When* Dropbox export work request is built, *Then* required network type is `NetworkType.UNMETERED`.
* **Criterion 3 (Backup Gating)**: *Given* `uploadToDropbox=true` and `uploadBackupToDropbox=false`, *When* `BackupWorker` or `BackupRestoreViewModel` evaluates cloud backup, *Then* Dropbox is skipped.
* **Criterion 4 (Dynamic Backup Constraints)**: *Given* active cloud backup where all providers allow cellular, *When* `buildConstraints()` runs, *Then* network type is `NetworkType.CONNECTED`.
* **Criterion 5 (Sync Timestamp & Error Resilience)**: *Given* successful Dropbox upload, *When* completed, *Then* `setDropboxLastSyncTimestamp` is updated; if `currentAccount` throws `DbxException`, `null` is returned safely.

### 1.5 System Invariants
All new preference keys default to `true` for existing athletes; Dropbox credentials and account email remain strictly in `auth_credentials_prefs.xml`; zero regression in clean-room suite.

---

## 2. Test Specification (`TST-DAT-031`)

### Test Cases
* **TST-DAT-031.1 (Preferences & Credential Isolation)**: Verify default `true` values, getters/setters in `TrainingApplication`, and email isolation/purge in `AuthCredentialsStoreTest`.
* **TST-DAT-031.2 (ExportManager Gating & Wi-Fi Constraints)**: Verify `ExportManager` schedules Dropbox export only when both `uploadToDropbox` and `uploadWorkoutsToDropbox` are true, and verifies `UNMETERED` constraint when Wi-Fi-only is true.
* **TST-DAT-031.3 (BackupWorker Gating & Dynamic Constraints)**: Verify `BackupWorker`/`BackupRestoreViewModel` skip Dropbox when `uploadBackupToDropbox` is false, and verify `buildConstraints` yields `UNMETERED`, `CONNECTED`, or `NOT_REQUIRED`.
* **TST-DAT-031.4 (Sync Timestamp & Defensive Account Handling)**: Verify `DropboxUploader`/`DropboxBackupManager` record sync timestamp on success, and verify `currentAccount` handles `DbxException` gracefully.
* **TST-DAT-031.5 (Clean-Room Suite & Parity)**: Run `TranslationParityTest` and `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-DAT-031.1` | Unit | `TrainingApplication.java`, `AuthCredentialsStore.kt` | `REQ-DAT-032.7a`, `REQ-DAT-032.7b` | Proposed |
| `TST-DAT-031.2` | Unit | `ExportManager.java` | `REQ-DAT-032.7c` | Proposed |
| `TST-DAT-031.3` | Unit | `BackupWorker.kt`, `BackupRestoreViewModel.kt`, `ImportBackupTabsScreen.kt` | `REQ-MIG-036.1`, `REQ-MIG-036.2` | Proposed |
| `TST-DAT-031.4` | Unit | `DropboxUploader.java`, `DropboxBackupManager.kt` | `REQ-DAT-032.7d` | Proposed |
| `TST-DAT-031.5` | Regression | `TranslationParityTest`, Full Test Suite | `REQ-PRO-001` | Proposed |
