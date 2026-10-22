# Stage 1 Analysis: ATT-4518 - [Verbesserung] Unify manual cloud backup button and automated backup subtitle to dynamically reflect enabled cloud providers (Dropbox & Google Drive)

**Ticket**: [ATT-4518](https://rainerblind.atlassian.net/browse/ATT-4518)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4518`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Domain & Root Cause Analysis

On the **Backup** tab (`BackupTabContent.kt` / `ImportBackupTabsScreen.kt`):
1. **Manual Cloud Backup Button**: Hardcoded to *"Create & Upload to Dropbox"* (`R.string.create_and_upload_to_dropbox`), invoking only `viewModel.uploadToDropbox(context)`. Google Drive is completely ignored even when connected. In addition, `uploadToDropbox()` bypasses `BackupManager.acquireLock()` and fails to persist `last_backup_timestamp`, `last_backup_status`, or `last_backup_targets`, diverging from `BackupWorker`.
2. **Automated Backups Subtitle**: Hardcoded to *"Regularly upload backups to Dropbox"* (`R.string.automated_backups_summary`), despite `BackupWorker` creating a local rolling backup and uploading to all enabled providers (Dropbox and/or Google Drive).
3. **Root Cause**: Dropbox was historically the sole cloud provider. When Google Drive backup was introduced in `ATT-1306`, `BackupWorker` was updated to support both providers, but `BackupTabContent` and `BackupRestoreViewModel` manual triggers remained locked to Dropbox.

---

## 2. Call Site & Blast Radius Audit

| Component | Target File | Nature of Change & Impact |
| :--- | :--- | :--- |
| **UI Tab Content** | `BackupTabContent.kt` | Add `isGoogleDriveConnected`; compute dynamic button label/colors and automated backup subtitle; dispatch unified cloud backup or prompt dialog. |
| **Screen Container** | `ImportBackupTabsScreen.kt` | Pass `isGoogleDriveConnected` to `BackupTabContent`; wire `onUploadToCloudClick` and `showNoCloudConnectedDialog`. |
| **View Model** | `BackupRestoreViewModel.kt` | Implement `uploadToCloud(context)` with `BackupManager.acquireLock()`, dual-provider dispatch, and `last_backup_*` persistence parity with `BackupWorker`. |
| **Localization** | `res/values*/strings.xml` (9 locales) | Add 4 dynamic button labels, 4 dynamic subtitle variants, and no-provider dialog prompt. |
| **Unit Tests** | `migration/BackupRestoreViewModelCloudBackupTest.kt` | Unit tests for `uploadToCloud` provider dispatch combinations, lock contention, and status persistence. |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Replace Dropbox-only button with unified cloud upload button adapting label and colors dynamically across 4 provider states (Both, Dropbox-only, Drive-only, None).
  2. Implement explanatory dialog when tapping cloud upload button while neither provider is connected.
  3. Dynamic automated backups subtitle reflecting active targets (Local + Dropbox & Google Drive, Local + Dropbox, Local + Google Drive, or Local only).
  4. Implement `uploadToCloud()` in `BackupRestoreViewModel` with mutex lock and `last_backup_*` persistence.
  5. Enforce 9-language localization parity across all new string resources.
* **Out-of-Scope Non-Goals**:
  1. No changes to `BackupWorker` periodic schedule or WorkManager constraints (stabilized in ATT-3472 / ATT-4458).
  2. No changes to `RestoreTabContent.kt` or `ImportTabContent.kt`.
  3. No changes to cloud authentication workflows or Cloud Sync settings dialogs.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Amends `REQ-MIG-036` (*Automated Background Backup WorkManager Scheduling, Monotonic Timer Preservation, Conditional Network Constraints, Local Rolling Storage, and UI Diagnostics*).
* **Historical Origin & Commit Trace**: `ATT-3472` (`9739a5bc`, Sprint 2026-42.3) and `ATT-4458` (`22916f21`, Sprint 2026-43.4).
* **Root Reason for Existing Formulation**: Manual cloud upload predated multi-cloud architecture and lacked lock/status coordination with `BackupWorker`.
* **Preservation of Core Invariants**: Concurrency mutex locking (`BackupManager.acquireLock`), fine-grained `last_backup_targets` persistence, $O(1)$ local backup boundedness, and clean-room test pass rate are strictly preserved.

---

## 5. System Invariants & Risk Assessment

* **Core Invariants**:
  1. `BackupManager.acquireLock()` MUST guard all manual and periodic backup creation.
  2. `last_backup_targets` schema (`local:SUCCESS;dropbox:...`) MUST remain 100% compatible.
  3. All user-visible strings MUST satisfy 9-language localization parity.
  4. Existing `uploadToDropbox` / `uploadToGoogleDrive` signatures retained for backward compatibility.
* **Risk Rating**: **LOW** — Localized UI orchestration and unification of existing upload methods with established worker patterns; zero database schema or service lifecycle risk.
