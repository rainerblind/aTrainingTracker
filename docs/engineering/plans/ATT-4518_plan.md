# Stage 3: Implementation Plan - ATT-4518: [Verbesserung] Unify manual cloud backup button and automated backup subtitle to dynamically reflect enabled cloud providers (Dropbox & Google Drive)

**Ticket**: [ATT-4518](https://rainerblind.atlassian.net/browse/ATT-4518) | **Sub-task**: Pending Submission (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4518`  
**Requirement Mapping**: `REQ-MIG-036` (Clauses 11 & 12) | **Test Mapping**: `TST-MIG-036` (Clauses 1–6)  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Description & Background
On the **Backup** tab (`BackupTabContent.kt`):
1. The manual cloud upload button is hardcoded to Dropbox (`uploadToDropbox`), ignoring Google Drive, bypassing `BackupManager.acquireLock()`, and failing to persist `last_backup_*` status.
2. The automated backups subtitle hardcodes Dropbox, failing to reflect active targets (local rolling backup + Dropbox and/or Google Drive).

---

## 2. Proposed Architectural Changes (SWE.2)

| Layer / Class | Architectural Responsibility & Modification |
| :--- | :--- |
| **ViewModel**<br>`BackupRestoreViewModel.kt` | Add `uploadToCloud(context)` / `uploadToEnabledClouds(context)`: acquires `BackupManager.acquireLock(context)`, creates local backup, uploads to enabled cloud providers (Dropbox and/or Google Drive), and commits `last_backup_timestamp`, `last_backup_status` (`SUCCESS`/`PARTIAL_SUCCESS`/`Upload failed`), and `last_backup_targets` into `SharedPreferences` matching `BackupWorker`. |
| **Compose UI**<br>`BackupTabContent.kt` | Accept `isGoogleDriveConnected`; dynamically resolve cloud button label and colors across 4 states (Both, Dropbox-only, Drive-only, None); wire muted click to `onNoCloudConnectedClick`; dynamically resolve automated backup subtitle. |
| **Compose Screen**<br>`ImportBackupTabsScreen.kt` | Pass `isGoogleDriveConnected` to `BackupTabContent`; wire `onUploadToCloudClick` to `viewModel.uploadToCloud(context)`; display `AlertDialog` with `cloud_backup_no_provider_dialog_*` when neither provider is connected. |
| **Localization**<br>`values*/strings.xml` | Strings defined and verified across all 9 supported locales (`values`, `-de`, `-es`, `-fr`, `-it`, `-ja`, `-nl`, `-pl`, `-pt`). |

### UI Consistency (Rule 23)
* **Reference**: `BackupTabContent.kt` & `RestoreTabContent.kt` (Material 3 `ElevatedCard`, `Button`, `AlertDialog`).
* **Tokens**: `RoundedCornerShape(16.dp)`, `16.dp` padding, `surfaceVariant` with 38% alpha for muted state, `bodySmall` with `onSurfaceVariant` for subtitle. Zero one-off styles.

---

## 3. Step-by-Step Implementation Sequence (Stage 4)

### Step 1: Implement `uploadToCloud(context)` in `BackupRestoreViewModel.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt`
* Logic: Query `TrainingApplication` for connected Dropbox/Google Drive states; acquire `BackupManager.acquireLock(context)`; create backup on `Dispatchers.IO`; dispatch uploads; persist `last_backup_timestamp`, `last_backup_status`, and `last_backup_targets` (`BackupWorker.formatTargets(targets)`); update `_uiState`. Provide alias `uploadToEnabledClouds(context)`.

### Step 2: Dynamic Cloud Button & Automated Subtitle in `BackupTabContent.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupTabContent.kt`
* Logic: Add `isGoogleDriveConnected: Boolean` and `onNoCloudConnectedClick: () -> Unit`; dynamically select string resource for button label (`create_and_upload_to_both`, `create_and_upload_to_dropbox`, `create_and_upload_to_google_drive`, `create_and_upload_to_cloud`) and automated backup subtitle (`automated_backups_summary_both`, `automated_backups_summary`, `automated_backups_summary_google_drive`, `automated_backups_summary_local_only`); apply muted container/content colors when neither connected.

### Step 3: Screen Wiring & No-Provider Dialog in `ImportBackupTabsScreen.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`
* Logic: Wire `isGoogleDriveConnected`, `onUploadToCloudClick`, and `showNoCloudConnectedDialog`; host `AlertDialog` for no-provider prompt.

### Step 4: Unit Testing & Verification (`TST-MIG-036`)
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModelCloudBackupTest.kt`
* Test cases: Both enabled, Dropbox-only, Drive-only, Neither enabled (error state), partial failure, lock contention.
* Test command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.BackupRestoreViewModelCloudBackupTest"`

---

## 4. System Invariants & Rollback Plan
* **Invariants**: `BackupManager.acquireLock()` guard on all backup operations; `last_backup_targets` format parity; 9-language translation parity; backward-compatible signatures; 100% clean-room test pass rate.
* **Rollback Plan**: All changes isolated to `feature/ATT-4518`; clean git checkout or branch revert if needed.
