# Stage 2: Requirement & Test Specification - ATT-4518: [Verbesserung] Unify manual cloud backup button and automated backup subtitle to dynamically reflect enabled cloud providers (Dropbox & Google Drive)

**Ticket**: [ATT-4518](https://rainerblind.atlassian.net/browse/ATT-4518) | **Sub-task**: Pending Submission (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4518`  
**Requirement Mapping**: `REQ-MIG-036` | **Test Spec ID**: `TST-MIG-036`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`) | **Date**: 2026-10-21  

---

## 1. Requirement Specification (REQ-MIG-036 Clauses 11 & 12)

### 1.1 Problem Statement & Rationale
On the **Backup** tab (`BackupTabContent.kt` / `ImportBackupTabsScreen.kt`):
1. The manual cloud backup button is hardcoded to *"Create & Upload to Dropbox"* (`R.string.create_and_upload_to_dropbox`), invoking only `viewModel.uploadToDropbox(context)`. Google Drive is ignored even when configured. In addition, `uploadToDropbox()` bypasses `BackupManager.acquireLock()` and fails to persist `last_backup_timestamp`, `last_backup_status`, and `last_backup_targets`.
2. The automated backups subtitle is hardcoded to *"Regularly upload backups to Dropbox"*, failing to reflect active destinations (local rolling backup + Dropbox and/or Google Drive).

### 1.2 Architectural Clauses
* **Unified Provider-Aware Manual Cloud Backup Button (`REQ-MIG-036.11`)**:
  * `BackupTabContent` SHALL dynamically compute button text and visual state: (a) Both enabled: `create_and_upload_to_both`; (b) Dropbox-only: `create_and_upload_to_dropbox`; (c) Google Drive-only: `create_and_upload_to_google_drive`; (d) Neither enabled: `create_and_upload_to_cloud` in muted style (`surfaceVariant` container, 38% alpha onSurface content).
  * Tapping when neither is enabled SHALL show an informational dialog (`cloud_backup_no_provider_dialog_message`) directing to Synchronization settings.
  * `BackupRestoreViewModel.uploadToCloud(context)` SHALL acquire `BackupManager.acquireLock(context)`, create backup, upload to all connected providers, and persist `last_backup_timestamp`, `last_backup_status`, and `last_backup_targets` with full `BackupWorker` parity.
* **Dynamic Automated Backups Subtitle (`REQ-MIG-036.12`)**:
  * `BackupTabContent` subtitle SHALL dynamically resolve: (a) Both enabled: `automated_backups_summary_both`; (b) Dropbox-only: `automated_backups_summary`; (c) Google Drive-only: `automated_backups_summary_google_drive`; (d) Neither enabled: `automated_backups_summary_local_only`.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* both Dropbox and Google Drive connected, *When* `BackupTabContent` renders, *Then* button displays `Create & Upload (Dropbox & Google Drive)` and subtitle displays `Regularly upload backups to Dropbox and Google Drive`.
* *Given* only Google Drive connected, *When* `BackupTabContent` renders, *Then* button displays `Create & Upload to Google Drive` and subtitle displays `Regularly upload backups to Google Drive`.
* *Given* neither provider connected, *When* `BackupTabContent` renders, *Then* button displays `Create & Upload to Cloud` (muted style), subtitle displays `Regularly create local rolling backups on this device`, and tapping displays no-cloud prompt dialog.
* *Given* cloud provider(s) enabled, *When* `uploadToCloud` executes, *Then* it acquires `BackupManager.acquireLock`, uploads to active providers, and persists `last_backup_*` preferences.

### 1.4 System Invariants
1. `BackupManager.acquireLock()` MUST guard all manual and periodic backup creation.
2. `last_backup_targets` schema (`local:SUCCESS;dropbox:...`) MUST remain 100% compatible.
3. 9-language localization parity across all new strings; 100% clean-room test pass rate.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

* `Original Requirement ID & Target`: Refines `REQ-MIG-036` (targets `BackupTabContent.kt`, `ImportBackupTabsScreen.kt`, `BackupRestoreViewModel.kt`, `res/values*/strings.xml`).
* `Historical Origin & Commit Trace`: Sprint 2026-40.2 (initial `BackupWorker`), Sprint 2026-41.1 (`ATT-1306` Google Drive upload), Sprint 2026-42.3 (`ATT-3472`, commit `9739a5bc`), Sprint 2026-43.4 (`ATT-4458`), Sprint 2026-43.5 (`ATT-4518`).
* `Root Reason for Existing Formulation`: Manual cloud backup triggers predated multi-cloud architecture and were locked to Dropbox; `uploadToDropbox` bypassed `BackupManager` mutex locks and status persistence.
* `Preservation of Core Invariants`: Concurrency mutex locking (`BackupManager.acquireLock`), `last_backup_targets` schema fidelity, $O(1)$ local storage boundedness, 9-language translation parity, and clean-room test pass rate are strictly preserved.

---

## 3. Test Specification (TST-MIG-036)

* **`TST-MIG-036.1`**: Provider-Aware Cloud Backup Button Label & Color State Unit Tests (`BackupTabContentTest.kt`).
* **`TST-MIG-036.2`**: Dynamic Automated Backup Subtitle Unit Tests (`BackupTabContentTest.kt`).
* **`TST-MIG-036.3`**: Cloud Disconnected Explanatory Dialog Prompt Test (`ImportBackupTabsScreenTest.kt`).
* **`TST-MIG-036.4`**: ViewModel `uploadToCloud()` Mutex Lock, Dual-Provider Upload & Status Persistence Test (`BackupRestoreViewModelCloudBackupTest.kt`).
* **`TST-MIG-036.5`**: 9-Language Localization Parity Audit (`TranslationParityTest.kt`).
* **`TST-MIG-036.6`**: Clean-Room Full Suite Regression Execution (`./gradlew testDebugUnitTest`).

---

## 4. Traceability Matrix

| Test Case | Scope | Method Under Test / Subject | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-MIG-036.1]` | Unit | Cloud backup button label & colors across 4 states | `REQ-MIG-036.11` | Planned (ATT-4518) |
| `[TST-MIG-036.2]` | Unit | Automated backups subtitle across 4 states | `REQ-MIG-036.12` | Planned (ATT-4518) |
| `[TST-MIG-036.3]` | Contract | No-cloud connected informational dialog prompt | `REQ-MIG-036.11` | Planned (ATT-4518) |
| `[TST-MIG-036.4]` | Unit | `BackupRestoreViewModel.uploadToCloud` mutex & status | `REQ-MIG-036.11` | Planned (ATT-4518) |
| `[TST-MIG-036.5]` | Localization | 9-locale string parity for new cloud backup keys | `REQ-MIG-036.11`, `REQ-LOC-001` | Planned (ATT-4518) |
| `[TST-MIG-036.6]` | Regression | Full test suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Planned (ATT-4518) |
