# Stage 2: Requirement & Test Specification - ATT-3489: Remove Google Drive scan option across all workout format import cards

**Ticket**: [ATT-3489](https://rainerblind.atlassian.net/browse/ATT-3489)  
**Sub-task**: [ATT-3518](https://rainerblind.atlassian.net/browse/ATT-3518) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `Sprint 2026-42.3`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-UI-362` (*Removal of Google Drive Format Scan Option from Workout Import Cards*), amending `REQ-UI-293`  
**Test Spec ID**: `TST-UI-325`  
**Branch**: `feature/ATT-3489`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Requirement Specification (REQ-UI-362)

### 1.1 Problem Statement & Rationale
In `ImportBackupTabsScreen.kt`, the format import tab currently displays a "Google Drive scannen" button on each supported workout file format card (FIT: `import_fit_gdrive_button`, TCX: `import_tcx_gdrive_button`, GPX: `import_gpx_gdrive_button`).

Scanning Google Drive via the REST API for externally copied workout files (e.g. copied from desktop GPS sync or web browser) requires the sensitive OAuth scope `https://www.googleapis.com/auth/drive.readonly`. Google strictly mandates an extensive, recurring verification process for sensitive scopes (including annual CASA security audits and demo video justifications). Under the least-privilege `drive.file` scope, the REST API cannot discover files created outside of `aTrainingTracker`, leading to false expectations and confusing empty scan results for athletes.

Furthermore, athletes can already import workout files stored on Google Drive effortlessly without sensitive scopes by using the standard local file picker ("Dateien importieren"), which integrates natively with the Android Storage Access Framework (SAF) Google Drive cloud provider.

Removing the redundant "Google Drive scannen" button across all three format cards (FIT, TCX, GPX) cleans up the import interface, eliminates confusing permission limitations, and simplifies the codebase.

### 1.2 Functional & Architectural Requirements
1. **Composable UI Decluttering (`ImportBackupTabsScreen.kt`)**:
   - `FormatImportCard` SHALL remove `googleDriveButtonText`, `isGoogleDriveConnected`, and `onGoogleDriveClick` parameters.
   - `FormatImportCard` SHALL render only two action buttons per card: Button 1 (Local File Picker via SAF) and Button 2 (Dropbox Scan).
   - `ImportTabContent` SHALL remove `isGoogleDriveConnected` and `onGoogleDriveScanClick` parameters.
   - `ImportBackupTabsScreen` SHALL remove state variables `showTuningDialogForGoogleDrive`, `showGoogleDriveDisconnectedDialog`, and `pendingGoogleDriveFormat`.
   - `ImportBackupTabsScreen` SHALL remove the obsolete `showGoogleDriveDisconnectedDialog` alert dialog and `showTuningDialogForGoogleDrive` bottom sheet.
2. **String Resources Cleanup (`strings.xml`)**:
   - String resources `import_fit_gdrive_button`, `import_tcx_gdrive_button`, and `import_gpx_gdrive_button` SHALL be cleanly removed across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with zero lint regressions.
3. **Preservation of Core Cloud Invariants**:
   - Google Drive database backup/restore (`.attbackup`) in `BackupTabContent` and automated background export capabilities SHALL remain 100% operational under `drive.file` scope.
   - Dropbox scanning across all format cards (`fit`, `tcx`, `gpx`) and local multi-document SAF picking SHALL remain 100% operational.
   - Pre-import cluster parameter tuning (`PreImportTuningBottomSheet`) and post-import reactive reconciliation SHALL remain fully preserved for local and Dropbox imports.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-UI-293` (*Format-Specific Import Tab Architecture with Uniform Multi-Document Source Triad (FIT, TCX, GPX)*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2623` (commit `d5a9d217`), Sprint 2026-41.1, amended under `ATT-3315` in Sprint 2026-42.1.
3. *Root Reason for Existing Formulation*: Originally conceived as a symmetric triad (Local, Dropbox, Google Drive) for each format. However, because Google Drive REST API queries under least-privilege `drive.file` scope cannot discover files placed into Drive by external apps or desktop sync, the button produced misleading empty scan results. Because Android SAF provides native Google Drive file picking without sensitive scopes, the dedicated REST scan button is redundant and detrimental to user experience.
4. *Preservation of Core Invariants*: Local multi-document file picker (supporting Google Drive via SAF), Dropbox format scan, pre-import cluster parameter tuning, and Google Drive full database backup/restore remain strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1: Elimination of Google Drive Buttons on Format Cards**:
  - *Given* an athlete navigates to the "Import & Backup" screen and views the Import tab,
  - *When* inspecting the FIT, TCX, and GPX format import cards,
  - *Then* each card renders exclusively the Local File Picker button ("Dateien importieren") and the Dropbox Scan button, with zero Google Drive scan buttons present.
* **Criterion 2: Functional Preservation of SAF & Cloud Operations**:
  - *Given* an athlete importing workouts from Google Drive,
  - *When* tapping "Dateien importieren" on any format card,
  - *Then* the Android SAF document picker opens, allowing selection of `.fit`, `.tcx`, or `.gpx` files from Google Drive cloud storage.
* **Criterion 3: Preservation of Backup & Export Workflows**:
  - *Given* an athlete with connected Google Drive,
  - *When* accessing the Backup tab or automated export settings,
  - *Then* Google Drive backup creation, restore, and automated export remain fully functional.
* **Criterion 4: Localization & Test Parity**:
  - *Given* all 9 supported application locales,
  - *When* running translation and contract tests,
  - *Then* zero orphaned string warnings occur and all contract tests pass.

### 1.5 System Invariants
1. **Material 3 Composable Design Invariant**: `FormatImportCard` adheres strictly to standard card elevation (2dp), rounded corners (16dp), outlined button styles, and theme colors without regression.
2. **Clean-Room Full Suite Regression Invariant**: 100% test pass rate across all modules (`./gradlew testDebugUnitTest`).

---

## 2. Test Specification (TST-UI-325)

### Test Case 1: `ImportFormatCardsContractTest` Contract Assertions (`TST-UI-325.1`)
* **Scope**: Unit / Architecture Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportFormatCardsContractTest.kt`
* **Preconditions**: `ImportBackupTabsScreen.kt` source code loaded.
* **Action**: Run contract assertions on `FormatImportCard` and `ImportTabContent`.
* **Expected Result**:
  - Assert that `FormatImportCard` defines only Local and Dropbox action buttons.
  - Assert that `R.string.import_fit_gdrive_button`, `R.string.import_tcx_gdrive_button`, and `R.string.import_gpx_gdrive_button` do NOT appear in `ImportBackupTabsScreen.kt`.
  - Assert that `pendingGoogleDriveFormat` does NOT appear in `ImportBackupTabsScreen.kt`.
  - Assert that Dropbox scan and local import buttons remain present across all format blocks.

### Test Case 2: 9-Language Localization Audit (`TST-UI-325.2`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportFormatLocalizationTest.kt`
* **Goal**:
  - Verify that active format import strings exist and are non-empty across all 9 locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.
  - Verify that removed keys (`import_fit_gdrive_button`, `import_tcx_gdrive_button`, `import_gpx_gdrive_button`) are removed from required keys and from all 9 locale XML files.
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 3: Clean-Room Regression Suite (`TST-UI-325.3`)
* **Scope**: Clean-room regression test
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with 0 failures and 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-325.1` | Contract Unit | `ImportFormatCardsContractTest.testImportTabContent_containsThreeFormatBlocksWithoutGoogleDriveScan` | `REQ-UI-362` | Specified |
| `TST-UI-325.2` | Localization | `ImportFormatLocalizationTest.testAllFormatImportStringsExistAndAreNonEmptyAcrossAllNineLocales` | `REQ-UI-362`, `REQ-LOC-001` | Specified |
| `TST-UI-325.3` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
