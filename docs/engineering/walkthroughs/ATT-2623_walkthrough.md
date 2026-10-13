# Walkthrough - ATT-2623: Structure Import tab into format-specific blocks (FIT, TCX, GPX) each offering Local, Dropbox, and Google Drive options

**Ticket**: [ATT-2623](https://atrainingtracker.atlassian.net/browse/ATT-2623)
**Sub-task**: ATT-2704 (`[Verification]`)
**Date**: 2026-10-08
**Author**: Antigravity (AI Assistant)
**Reviewer**: Agent 2 (Auditor) / Rainer Blind (Human User)
**Target Branch**: `feature/ATT-2623`

---

## 1. Problem Domain & Objective

In Sprint 2026-41.1, user testing revealed that the "Import" tab in [ImportBackupTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt) was structurally fragmented and asymmetrical:
1. **Asymmetrical Grouping**: The UI grouped FIT import into an isolated "FIT File Import" card at the top, while TCX and GPX were bundled together into a generic "Workout Import" / "Legacy Recovery" block beneath it.
2. **Missing Cloud Actions for TCX/GPX**: While FIT offered a local file picker and general cloud options, athletes seeking to import TCX or GPX directly from Dropbox or Google Drive lacked format-explicit scan options.
3. **Hardcoded Recovery Filter**: Cloud recovery dispatched with a blanket `"all"` parameter, rather than allowing athletes to perform targeted scans for specific formats.

**Objective**:
- Restructure `ImportTabContent` into three dedicated, cohesive format cards: FIT, TCX, and GPX.
- Provide a uniform action triad across all three cards: Local File Picker, Dropbox Scan, and Google Drive Scan.
- Scope cloud recovery to specific format tokens (`"fit"`, `"tcx"`, `"gpx"`) in [BackupRestoreViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt).
- Ensure 100% 9-language localization parity across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
- Retain all core invariants: pre-import cluster parameter tuning sheet, post-import navigation, and cloud disconnected dialogs.

---

## 2. Changes Implemented

### 2.1 Format-Specific Card Layout & Uniform Action Triad
- **File**: [ImportBackupTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt)
  - Created reusable composable `FormatImportCard(...)` following modern Jetpack Compose Material 3 card styling, consistent with `BackupRestoreContent` (elevated card, standard padding, outline buttons).
  - Restructured `ImportTabContent` to render:
    - **FIT Card**: Local multi-document picker (`ActivityResultContracts.OpenMultipleDocuments()`), Dropbox Scan (FIT), Google Drive Scan (FIT).
    - **TCX Card**: Local single-document picker (`ActivityResultContracts.OpenDocument()`), Dropbox Scan (TCX), Google Drive Scan (TCX).
    - **GPX Card**: Local single-document picker (`ActivityResultContracts.OpenDocument()`), Dropbox Scan (GPX), Google Drive Scan (GPX).
  - Added state tracking for format-scoped cloud operations: `pendingDropboxFormat`, `pendingGoogleDriveFormat`, and `pendingSingleLegacyFormat`.
  - Maintained disconnected service handling (`isDropboxConnected`, `isGoogleDriveConnected`), showing dimmed button styling and launching disconnected prompt dialogs on click.

### 2.2 Format-Scoped Cloud Recovery & Test Dispatcher Support
- **File**: [BackupRestoreViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt)
  - Updated `bulkRecoverLegacyData(context: Context, format: String = "all", dispatcher: CoroutineDispatcher = Dispatchers.IO): Job` to match `bulkRecoverGoogleDriveData`, enabling coroutine dispatcher injection and returning `Job` for robust coroutine coordination and test synchronization.
  - Forwarded explicit format string (`"fit"`, `"tcx"`, or `"gpx"`) to `LegacyImportEngine.bulkRecoverFromDropbox` and `LegacyImportEngine.bulkRecoverFromGoogleDrive`.

### 2.3 9-Language Localization Parity (12 New Strings)
- **Files**: `app/src/main/res/values*/strings.xml` across all 9 locales:
  - English (`values/`): `import_fit_dropbox_button`, `import_fit_gdrive_button`, `import_tcx_local_button`, `import_tcx_dropbox_button`, `import_tcx_gdrive_button`, `import_gpx_local_button`, `import_gpx_dropbox_button`, `import_gpx_gdrive_button`, etc.
  - German (`values-de/`), Spanish (`values-es/`), French (`values-fr/`), Italian (`values-it/`), Japanese (`values-ja/`), Dutch (`values-nl/`), Polish (`values-pl/`), Portuguese (`values-pt/`).

### 2.4 Test Suite & Quality Assurance
- **File**: [ImportFormatLocalizationTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportFormatLocalizationTest.kt)
  - Asserts all 12 action string resources exist across all 9 locale directories, are non-empty, and contain valid format specifiers.
- **File**: [ImportFormatCardsContractTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportFormatCardsContractTest.kt)
  - Validates composable architecture, rendering of 3 format cards, uniform 3-button triads, and format-scoped recovery dispatching.
- **File**: [BackupRestoreViewModelFormatScopingTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModelFormatScopingTest.kt)
  - Validates `bulkRecoverLegacyData` passes `"fit"`, `"tcx"`, and `"gpx"` to Dropbox recovery engine.
  - Validates `bulkRecoverGoogleDriveData` passes `"fit"`, `"tcx"`, and `"gpx"` to Google Drive recovery engine.
  - Validates `importLegacyFile` correctly resolves format-scoped parsers.

---

## 3. Verification & Test Results

### 3.1 Targeted Test Execution
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.ImportFormat*" --tests "com.atrainingtracker.trainingtracker.migration.BackupRestoreViewModelFormatScopingTest"
```
**Result**: BUILD SUCCESSFUL in 20s (100% pass rate).

### 3.2 Full Clean-Room Regression Suite
```bash
./gradlew testDebugUnitTest
```
**Result**: BUILD SUCCESSFUL in 9m 2s across 2,053 unit tests with 0 failures and 0 skipped.

### 3.3 Requirement Governance Verification
```bash
python3 tools/verify_requirement_governance.py --base-ref sprint/2026-41.3
```
**Result**: Exit code 0. Net-new requirement `REQ-UI-293` cleanly validated.

### 3.4 Physical / On-Device Verification
- `adb devices`: No physical or emulator devices connected in execution environment. UI contract tests assert card layout, button counts, and dispatch wiring.

---

## 4. Living Documentation Updates
- Updated [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md): `REQ-UI-293` status advanced to `Verified`.
- Updated [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md): `TST-UI-253` status advanced to `Verified`.
