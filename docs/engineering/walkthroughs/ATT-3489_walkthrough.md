# Stage 5 Walkthrough - ATT-3489: Remove Google Drive scan option across all workout format import cards

**Ticket**: [ATT-3489](https://rainerblind.atlassian.net/browse/ATT-3489)  
**Stage 5 Subtask**: [ATT-3521](https://rainerblind.atlassian.net/browse/ATT-3521) (`[Test]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Requirement Mapping**: `REQ-UI-362` (`Verified`)  
**Test Mapping**: `TST-UI-325` (`Verified`)  
**Branch**: `feature/ATT-3489`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Executive Summary & Defect Remediation

In `ImportBackupTabsScreen.kt`, the workout format import tab previously displayed a "Google Drive scannen" button alongside "Dateien importieren" and "Dropbox scannen" for each supported workout file format (FIT, TCX, GPX).

However, scanning Google Drive via the direct REST API for arbitrary user workout files created outside `aTrainingTracker` requires the sensitive OAuth scope `https://www.googleapis.com/auth/drive.readonly`. Google strictly mandates an extensive verification process for sensitive scopes (including YouTube demo video submissions, detailed justification reviews, and potential CASA audits). Under the least-privilege `drive.file` scope, the REST API cannot discover files created outside `aTrainingTracker`, resulting in false expectations and empty scan results for athletes.

Furthermore, athletes can already import workout files stored on Google Drive effortlessly without sensitive scopes by using the standard local file picker ("Dateien importieren"), which integrates natively with the Android Storage Access Framework (SAF) Google Drive cloud provider.

ATT-3489 completely eliminates the obsolete Google Drive scan options across all format import cards:
1. **Composable UI Cleanup (`ImportBackupTabsScreen.kt`)**:
   - Removed `isGoogleDriveConnected` and `onGoogleDriveScanClick` parameters from `ImportTabContent`.
   - Removed `isGoogleDriveConnected`, `googleDriveButtonText`, and `onGoogleDriveScanClick` parameters from `FormatImportCard`.
   - Removed obsolete dialog state (`showTuningDialogForGoogleDrive`, `showGoogleDriveDisconnectedDialog`, `pendingGoogleDriveFormat`) and obsolete dialog composables.
   - Cleaned up button styling and layout in `FormatImportCard` to render strictly the Local SAF Import and Dropbox Scan buttons.
2. **String Resource Cleanup Across 9 Locales**:
   - Removed obsolete button labels `import_fit_gdrive_button`, `import_tcx_gdrive_button`, and `import_gpx_gdrive_button` across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
   - Clean-room translation parity verified with 100% test pass rate.
3. **Architectural & Localization Test Coverage**:
   - Updated `ImportFormatCardsContractTest.kt` to enforce absence of Google Drive button parameters and ensure each format card provides only Local and Dropbox actions.
   - Updated `ImportFormatLocalizationTest.kt` to verify clean removal of Google Drive button string resources across all 9 locales while ensuring all remaining format import strings are present and localized.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-362` (*Removal of Google Drive Format Scan Option from Workout Import Cards*) $\to$ **Status: Verified**
* **Test Mapping**: `TST-UI-325` (*Removal of Google Drive Format Scan Option from Workout Import Cards Verification*) $\to$ **Status: Verified**

---

## 3. Preserved Invariants & Invariant Verification

1. **Storage Access Framework (SAF) Picking Preserved**: Athletes can continue to select FIT, TCX, and GPX files stored in Google Drive via the standard Android system file picker ("Dateien importieren").
2. **Cloud Database Backup / Restore Untouched**: Google Drive integration for full `.attbackup` database backups, automatic exports, and database restore remains 100% operational under the least-privilege `drive.file` scope.
3. **Dropbox Scan Preserved**: Dropbox folder scanning for FIT, TCX, and GPX formats remains fully intact and functional.
4. **9-Language Localization Parity Preserved**: All 9 supported locales maintain 100% translation parity with zero missing or dangling strings.
5. **Zero Full-Suite Regressions**: Full clean-room test suite `./gradlew testDebugUnitTest` executed with 100% pass rate across the entire codebase.

---

## 4. Test Verification Results

### Unit & Architectural Test Suites
All contract and localization tests pass cleanly:

| Test Suite | Description | Result |
| :--- | :--- | :--- |
| `ImportFormatCardsContractTest` | Verifies `FormatImportCard` and `ImportTabContent` have no Google Drive scan parameters and only expose Local SAF & Dropbox actions | **PASS** |
| `ImportFormatLocalizationTest` | Verifies clean removal of Google Drive format button strings across all 9 locales and presence of all remaining format import keys | **PASS** |

### Clean-Room Full Suite Regression Run
* Command: `./gradlew testDebugUnitTest`
* Result: **100% Tests Passed** (0 failures, 0 errors, 0 regressions across all modules).
