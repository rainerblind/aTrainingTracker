# Stage 2: Requirement & Test Specification - ATT-4664: Unify Restore Backup card button labels, icons, and disconnected visibility

**Ticket**: [ATT-4664](https://rainerblind.atlassian.net/browse/ATT-4664)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `Sprint 2026-43.7` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4664`  
**Requirement Mapping**: `REQ-MIG-036` (`REQ-MIG-036.13`)  
**Test Spec ID**: `TST-MIG-045`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Requirement Specification (`REQ-MIG-036`)

### 1.1 Problem Statement & Rationale
In the "Restore Backup" card of `RestoreTabContent.kt`, the four restore buttons mix technical labels (`"Local: Full Restore (Migration)"`, `"Dropbox: Full Restore (Migration)"`) with standardized phrasing (`"Restore from Automatic Local Backup"`, `"Restore from Google Drive"`). Dropbox restore incorrectly displays `Icons.Default.CloudUpload` (an upload arrow), and Google Drive restore is conditionally hidden when disconnected rather than rendered dimmed with `googleDriveRestoreColors` and `googleDriveRestoreBorder`, creating asymmetric UI layout.

### 1.2 Functional & Architectural Requirements
1. **Button Phrasing Harmonization (`REQ-MIG-036.13a`)**: All 4 restore buttons SHALL follow the `"Restore from ..."` pattern across all 9 locales: `restore_automatic_local_backup`, `restore_from_file` (`"Restore from Local File"`), `restore_from_dropbox` (`"Restore from Dropbox"`), and `restore_from_google_drive` (`"Restore from Google Drive"`).
2. **Iconography Correction (`REQ-MIG-036.13b`)**: In `RestoreTabContent.kt`, Dropbox restore SHALL render `Icons.Default.CloudDownload` instead of `Icons.Default.CloudUpload`.
3. **Unconditional Visual Symmetry (`REQ-MIG-036.13c`)**: In `RestoreTabContent.kt`, Google Drive restore SHALL render unconditionally (removing `if (isGoogleDriveConnected)`), applying `googleDriveRestoreColors` and `googleDriveRestoreBorder` (dimmed text and border) when disconnected, matching Dropbox.
4. **Disconnected Dialog Handling (`REQ-MIG-036.13d`)**: In `ImportBackupTabsScreen.kt`, tapping Google Drive restore when disconnected SHALL display `showGoogleDriveDisconnectedDialog` with `R.string.google_drive_disconnected_status`, without launching `showGoogleDriveRestoreConfirm`.

### 1.3. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-MIG-036` (Clause 10 & 11). Targets `RestoreTabContent.kt`, `ImportBackupTabsScreen.kt`, and `values*/strings.xml`.
* **Historical Origin & Commit Trace**: `REQ-MIG-036` introduced in `ATT-3472` (commit `9fa41095`), refined in `ATT-4458`, `ATT-4518`, `ATT-4587`, and extended in `ATT-4664`.
* **Root Reason for Existing Formulation**: `if (isGoogleDriveConnected)` was a temporary safeguard during initial integration, leaving `googleDriveRestoreColors` unexercised. Dropbox restore copy-pasted `CloudUpload`. Technical strings lingered from initial migration work.
* **Preservation of Core Invariants**: Destructive restore confirmation dialog guards (`AlertDialog`), 9-locale translation parity, and `MigrationEngine` contracts remain 100% preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Button Nomenclature & Icon)**: *Given* "Restore Backup" card in `RestoreTabContent`, *When* rendered, *Then* all 4 buttons use the localized "Restore from ..." pattern, and Dropbox displays `Icons.Default.CloudDownload`.
* **Criterion 2 (Unconditional Disconnected Visibility)**: *Given* `isGoogleDriveConnected=false`, *When* rendered, *Then* Google Drive restore is visible and dimmed with `googleDriveRestoreColors` and `googleDriveRestoreBorder`.
* **Criterion 3 (Disconnected Dialog Dispatch)**: *Given* `isGoogleDriveConnected=false`, *When* athlete taps Google Drive restore, *Then* `showGoogleDriveDisconnectedDialog` is displayed showing `R.string.google_drive_disconnected_status`, and `showGoogleDriveRestoreConfirm` is NOT triggered.

### 1.5 System Invariants
Destructive restore confirmation dialogs strictly guard actual full restore execution; 100% 9-locale translation parity; zero regression in clean-room suite.

---

## 2. Test Specification (`TST-MIG-045`)

### Test Cases
* **TST-MIG-045.1 (9-Language Parity)**: Verify all 4 button strings exist across all 9 locales with zero missing keys and adhering strictly to the standardized "Restore from ..." pattern.
* **TST-MIG-045.2 (RestoreTabContent Layout & Icon Contract)**: Contract test verifying Dropbox restore uses `Icons.Default.CloudDownload`, Google Drive renders unconditionally, and dimmed styling is applied when disconnected.
* **TST-MIG-045.3 (ImportBackupTabsScreen Disconnected Dialog Contract)**: Contract test verifying tapping Google Drive restore when disconnected triggers `showGoogleDriveDisconnectedDialog` and suppresses `showGoogleDriveRestoreConfirm`.
* **TST-MIG-045.4 (Clean-Room Suite & Parity)**: Run `TranslationParityTest` and targeted migration unit tests asserting 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MIG-045.1` | Localization | `res/values*/strings.xml` | `REQ-MIG-036.13a` | Proposed |
| `TST-MIG-045.2` | Contract / UI | `RestoreTabContent.kt` | `REQ-MIG-036.13b`, `REQ-MIG-036.13c` | Proposed |
| `TST-MIG-045.3` | Contract / UI | `ImportBackupTabsScreen.kt` | `REQ-MIG-036.13d` | Proposed |
| `TST-MIG-045.4` | Regression | `TranslationParityTest`, Migration Tests | `REQ-PRO-001` | Proposed |
