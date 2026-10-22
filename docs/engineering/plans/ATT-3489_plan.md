# Stage 3: Implementation Plan - ATT-3489: Remove Google Drive scan option across all workout format import cards

**Ticket**: [ATT-3489](https://rainerblind.atlassian.net/browse/ATT-3489)  
**Sub-task**: [ATT-3519](https://rainerblind.atlassian.net/browse/ATT-3519) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `Sprint 2026-42.3`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-UI-362` (*Removal of Google Drive Format Scan Option from Workout Import Cards*), amending `REQ-UI-293`  
**Test Mapping**: `TST-UI-325` (*Removal of Google Drive Format Scan Option from Workout Import Cards Verification*)  
**Branch**: `feature/ATT-3489`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Problem Description & Background

In `ImportBackupTabsScreen.kt`, the format import tab currently renders three distinct format cards (FIT, TCX, GPX). Each card displays an action triad containing:
1. Local File Import (via Android Storage Access Framework)
2. Dropbox Scan
3. Google Drive Scan (`import_fit_gdrive_button`, `import_tcx_gdrive_button`, `import_gpx_gdrive_button`)

Scanning Google Drive via direct REST API queries requires broad OAuth scopes (`https://www.googleapis.com/auth/drive.readonly`). Google classifies this scope as sensitive, requiring mandatory annual CASA security reviews and video verifications. Under the least-privilege `drive.file` scope, the REST API cannot discover files created outside of `aTrainingTracker` (such as externally synced or desktop-copied workout files), leading to confusing empty scan results for athletes.

Furthermore, athletes can already import workout files stored on Google Drive effortlessly without sensitive scopes by using the standard local file picker ("Dateien importieren"), which integrates natively with the Android Storage Access Framework (SAF) Google Drive cloud document provider.

Removing the redundant "Google Drive scannen" button across all three format cards (FIT, TCX, GPX) cleans up the import interface, eliminates misleading permission limitations, and simplifies the codebase.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-362` (*Removal of Google Drive Format Scan Option from Workout Import Cards*), amending `REQ-UI-293`
* **Test Mapping**: `TST-UI-325` (*Removal of Google Drive Format Scan Option from Workout Import Cards Verification*)
* **Living Documentation**:
  - `docs/requirements.md` (`REQ-UI-362` added)
  - `docs/tests.md` (`TST-UI-325` added)

---

## 3. System Invariants & Preserved Behavior

1. **Google Drive Database Backup/Restore & Auto-Export Invariant**: Google Drive database backup/restore (`.attbackup`) in `BackupTabContent` and automated background export capabilities SHALL remain 100% operational under `drive.file` scope.
2. **SAF Cloud Document Interoperability**: Local multi-document file picking via Android SAF (`ActivityResultContracts.OpenMultipleDocuments()`), supporting Google Drive cloud documents, remains fully preserved.
3. **Dropbox Scan Parity**: Dropbox format scanning across all three format cards (`fit`, `tcx`, `gpx`) remains fully preserved.
4. **Pre-Import Parameter Tuning Invariant**: Pre-import cluster parameter tuning (`PreImportTuningBottomSheet`) remains mandatory before local or Dropbox batch imports commence.
5. **Clean-Room Full Suite Regression**: 100% test pass rate across all modules (`./gradlew testDebugUnitTest`).

---

## 4. Proposed Architectural Changes

### Component 1: `ImportBackupTabsScreen.kt`
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`
* **Changes**:
  1. `FormatImportCard`:
     - Remove parameters: `googleDriveButtonText: String`, `isGoogleDriveConnected: Boolean`, `onGoogleDriveClick: () -> Unit`.
     - Remove `googleDriveButtonColors` and `googleDriveBorder`.
     - Remove the third `OutlinedButton` rendering Google Drive scan.
     - Each card now renders an action dyad: Local File Picker (Button 1) and Dropbox Scan (Button 2).
  2. `ImportTabContent`:
     - Remove parameters: `isGoogleDriveConnected: Boolean`, `onGoogleDriveScanClick: (format: String) -> Unit`.
     - Update FIT, TCX, and GPX `FormatImportCard` invocations to pass only local and Dropbox parameters.
  3. `ImportBackupTabsScreen`:
     - Remove state variables: `showTuningDialogForGoogleDrive`, `showGoogleDriveDisconnectedDialog`, and `pendingGoogleDriveFormat`.
     - Remove `onGoogleDriveScanClick` lambda from `ImportTabContent` call site.
     - Remove `if (showTuningDialogForGoogleDrive)` block calling `PreImportTuningBottomSheet`.
     - Remove `if (showGoogleDriveDisconnectedDialog)` block calling `AlertDialog`.

### Component 2: String Resource Hygiene (`strings.xml` across all 9 locales)
* Paths:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* **Changes**:
  - Remove string entries: `import_fit_gdrive_button`, `import_tcx_gdrive_button`, `import_gpx_gdrive_button`.

### Component 3: Unit Tests (`ImportFormatCardsContractTest.kt`, `ImportFormatLocalizationTest.kt`)
* Paths:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportFormatCardsContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportFormatLocalizationTest.kt`
* **Changes**:
  - In `ImportFormatCardsContractTest.kt`:
    - Assert `FormatImportCard` composable is defined with parameters for local and Dropbox, without Google Drive.
    - Assert `ImportTabContent` renders three format blocks with local and Dropbox buttons.
    - Assert `R.string.import_fit_gdrive_button`, `R.string.import_tcx_gdrive_button`, `R.string.import_gpx_gdrive_button`, and `pendingGoogleDriveFormat` are absent from `ImportBackupTabsScreen.kt`.
  - In `ImportFormatLocalizationTest.kt`:
    - Remove `import_fit_gdrive_button`, `import_tcx_gdrive_button`, and `import_gpx_gdrive_button` from `requiredKeys`.

### UI Consistency (Rule 23)
* **Reference Screen / Component**: Existing `ImportBackupTabsScreen.kt` format cards.
* **Reused Components**: `FormatImportCard`, `ElevatedCard` (shape 16.dp, elevation 2.dp), `OutlinedButton`, Material Theme colors.
* **Theme Tokens**: `RoundedCornerShape(16.dp)`, `MaterialTheme.typography.titleMedium`, `MaterialTheme.typography.bodySmall`, `MaterialTheme.colorScheme.onSurfaceVariant`, `ButtonDefaults.outlinedButtonColors()`.
* **New One-Off Styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Composable Cleanup in `ImportBackupTabsScreen.kt`
* Refactor `FormatImportCard` to remove Google Drive scan parameters, styling, and button.
* Refactor `ImportTabContent` to remove Google Drive scan parameter and references.
* Clean up `ImportBackupTabsScreen` state variables (`showTuningDialogForGoogleDrive`, `showGoogleDriveDisconnectedDialog`, `pendingGoogleDriveFormat`) and dialog composables.
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`.

### Step 2: String Resource Deletion Across 9 Locales
* Remove `import_fit_gdrive_button`, `import_tcx_gdrive_button`, and `import_gpx_gdrive_button` across all 9 `strings.xml` files.
* Targets: `app/src/main/res/values*/strings.xml`.

### Step 3: Contract & Localization Test Modernization
* Update `ImportFormatCardsContractTest.kt` to verify the new contract (dyad action layout without Google Drive scan buttons).
* Update `ImportFormatLocalizationTest.kt` to verify active string keys.
* Targets:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportFormatCardsContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportFormatLocalizationTest.kt`

### Step 4: Targeted Unit Test Execution
* Command:
  ```bash
  ./gradlew testDebugUnitTest \
    --tests "com.atrainingtracker.trainingtracker.migration.ImportFormatCardsContractTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.ImportFormatLocalizationTest"
  ```
* Verify 100% pass rate.

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests during construction, followed by full clean-room suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation allows full revert without affecting `sprint/2026-42.3`.
