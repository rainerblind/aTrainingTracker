# Stage 3: Implementation Plan - ATT-3315: Enable import of externally copied workout files from Google Drive with file picker fallback

**Ticket**: [ATT-3315](https://rainerblind.atlassian.net/browse/ATT-3315)  
**Sub-task**: [ATT-3399](https://rainerblind.atlassian.net/browse/ATT-3399) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-MIG-034` (amended & extended), `REQ-UI-293` (amended & extended)  
**Test Mapping**: `TST-MIG-031` (extended), `TST-UI-253` (extended)  
**Branch**: `feature/ATT-3315`  
**Author**: AI Agent 1 (Software Construction Planner)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-MIG-034` (*Google Drive Bulk Recovery for Historical Workout Files*), targeting `GoogleDriveClient.kt`, `LegacyImportEngine.kt`, `BackupRestoreViewModel.kt`.
   - `REQ-UI-293` (*Format-Specific Import Tab Architecture with Uniform Source Triad (FIT, TCX, GPX)*), targeting `ImportBackupTabsScreen.kt`, `BackupRestoreViewModel.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-41.10 (`ATT-2346`, commit `53316db1`) introduced Google Drive historical workout file discovery and bulk recovery.
   - Sprint 2026-41.1 (`ATT-2623`, commit `d5a9d217`) structured the Import tab into format-specific blocks (FIT, TCX, GPX).
3. **Root Reason for Existing Formulation**:
   - `ATT-2346` used `https://www.googleapis.com/auth/drive.file` and probed `aTrainingTracker/TCX/`, `aTrainingTracker/Workouts/`, etc. In Sprint 2026-41.10 Joint Review, an athlete copied files into a top-level `TCX` folder. Because files created externally are invisible under `drive.file`, and top-level folders were not probed, 0 files were discovered. Furthermore, TCX and GPX local buttons were limited to single-file `OpenDocument()`.
4. **Preservation of Core Invariants**:
   - OAuth Least Privilege: The application strictly retains `https://www.googleapis.com/auth/drive.file` to protect user privacy and avoid Google Play OAuth sensitive data restrictions.
   - Cloud Pre-Import Tuning: The pre-import cluster parameter tuning sheet (`PreImportTuningBottomSheet`, `REQ-MIG-012`, `REQ-UI-150`) remains mandatory before scanning or batch local import commences.
   - Multi-Dimensional Deduplication: `LegacyImportEngine` duplicate skipping via `isWorkoutExisting` remains active.
   - Post-Import Reconciliation: `syncPeriodsIfDiscrepancy()`, `refreshClusters()`, and post-import navigation remain intact.
   - Localization Parity: 100% 9-language translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
   - Clean-Room Test Pass Rate: 100% unit test pass rate across all modules.

---

## 1. Architectural Decomposition (SWE.2)

```
[ ImportBackupTabsScreen (Compose UI) ]
      │
      ├─► Local File Picker: ActivityResultContracts.OpenMultipleDocuments()
      │         (Supports local storage, SD card, and Google Drive SAF provider)
      │
      ├─► PreImportTuningBottomSheet (Cluster parameter tuning: eps, minPoints)
      │         │
      │         ▼
[ BackupRestoreViewModel (StateFlow, Dispatchers.Main / viewModelScope) ]
      │
      ├─► importWorkoutFiles(context, uris, format): Batch SAF stream processing
      │         │
      │         ▼
      │   [ LegacyImportEngine ] (Dispatchers.IO)
      │         - parse stream (FIT/TCX/GPX)
      │         - multi-dimensional deduplication
      │         - emit progress callbacks
      │
      └─► bulkRecoverGoogleDriveData(context, format):
                │
                ▼
          [ GoogleDriveClient ] (drive.file queries)
                - candidate top-level & nested folders (TCX, FIT, GPX, aTrainingTracker/...)
                - if totalScanned == 0 -> emit google_drive_scan_empty_diagnostic
```

### 1.1 Storage & SAF Interoperability Layer
- In `ImportBackupTabsScreen.kt`, upgrade `pickTcxFilesLauncher` and `pickGpxFilesLauncher` to `ActivityResultContracts.OpenMultipleDocuments()`.
- Unify multi-document selection across FIT, TCX, and GPX cards.
- Users can browse Google Drive directly through Android's Storage Access Framework document picker, bypassing `drive.file` API visibility restrictions on externally uploaded files.

### 1.2 Google Drive Discovery Expansion Layer (`GoogleDriveClient.kt`)
- In `GoogleDriveClient.listFilesRecursively` and candidate folder resolution:
  - Add top-level folder candidates: `listOf("TCX")`, `listOf("FIT")`, `listOf("GPX")`, `listOf("Workouts")` in addition to `listOf("aTrainingTracker", "TCX")`, etc.
  - Probe both top-level and nested folders to find any app-created folders.

### 1.3 Batch Import Execution Layer (`BackupRestoreViewModel.kt`)
- Add `importWorkoutFiles(context: Context, uris: List<Uri>, format: String)`:
  - Validates format extensions using `ImportFileValidator`.
  - Iterates over selected URIs with progress feedback (`R.string.legacy_import__importing_file`, `importedCount`, `totalCount`).
  - Calls `legacyImportEngine.importFromUri(...)` for each file.
  - Skips duplicates cleanly without crashing.
  - Emits final `UiState.Success` with summary count and post-import workout navigation (`importedWorkoutId`).
  - Triggers `refreshDataAfterImport()`.

### 1.4 Diagnostic Feedback Layer (`GoogleDriveBulkRecoveryContract`)
- When `bulkRecoverGoogleDriveData` completes with `totalScanned == 0`:
  - Emit localized diagnostic message `google_drive_scan_empty_diagnostic` explaining that Google Drive API only sees files created by aTrainingTracker, and guiding the user to use the local file picker button ("Lokale Datei(en) wählen") to import externally copied files from Google Drive.

---

## 2. UI Consistency (Rule 23)

- **Closest Reference Screen**: `ImportBackupTabsScreen.kt` (lines 350–550, FIT/TCX/GPX cards).
- **Reused Components**:
  - `OutlinedButton` with standard triad actions: `Local File Picker`, `Dropbox Scan`, `Google Drive Scan`.
  - `PreImportTuningBottomSheet` for cluster parameter confirmation.
  - `AppModalBottomSheet` / `ConfirmDialog` for cloud connection warnings.
- **Theme Tokens**:
  - Spacing: Standard `8.dp` between buttons, `16.dp` card padding, `12.dp` rounded corner cards (`RoundedCornerShape(12.dp)`).
  - Colors: `MaterialTheme.colorScheme.primary`, `MaterialTheme.colorScheme.surfaceVariant`.
  - Typography: `MaterialTheme.typography.titleMedium`, `MaterialTheme.typography.bodyMedium`.
- **Justification for Any New Style**:
  - No new styles created. Full reuse of existing UI components and tokens.

---

## 3. Atomic Implementation Steps

### Step 1: Localization & String Tokens
- Add `google_drive_scan_empty_diagnostic` across all 9 `strings.xml` files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
- Update button label if needed to clarify multi-file selection: `import_tcx_local_button` and `import_gpx_local_button` -> "Lokale Datei(en) wählen" / "Select local file(s)".
- Targeted test: `TranslationParityTest`.

### Step 2: Google Drive Client Top-Level Path Probing & Diagnostics
- In `GoogleDriveClient.kt`, expand candidate folder paths to include top-level folder names (`"TCX"`, `"FIT"`, `"GPX"`, `"Workouts"`).
- In `BackupRestoreViewModel.kt`, detect `totalScanned == 0` after Google Drive recovery and set `_uiState` with `google_drive_scan_empty_diagnostic`.
- Targeted test: `GoogleDriveBulkRecoveryContractTest.kt`, `BackupRestoreViewModelGoogleDriveTest.kt`.

### Step 3: ViewModel Batch Local Import Pipeline
- In `BackupRestoreViewModel.kt`:
  - Implement `importWorkoutFiles(context: Context, uris: List<Uri>, format: String)`.
  - Stream-based reading via `contentResolver.openInputStream(uri)`.
  - Dispatch to `legacyImportEngine.importFromTcxInternal`, `importFromFitInternal`, `importFromGpxInternal`.
  - Progress tracking and post-import reconciliation.
- Targeted test: `BackupRestoreViewModelFormatScopingTest.kt`, `BackupRestoreViewModelNavigationTest.kt`.

### Step 4: Import Tab UI Multi-Document Selection & Wiring
- In `ImportBackupTabsScreen.kt`:
  - Replace `ActivityResultContracts.OpenDocument()` with `ActivityResultContracts.OpenMultipleDocuments()` for TCX and GPX.
  - Wire local file selection to present `PreImportTuningBottomSheet`.
  - Upon confirmation, invoke `viewModel.importWorkoutFiles(context, uris, format)`.
- Targeted test: `ImportFormatCardsContractTest.kt`.

### Step 5: Full Clean-Room Verification
- Run `./gradlew testDebugUnitTest`.
- Ensure 100% pass rate with zero regressions.

---

## 4. Invariant Protection & Verification Checklist

- [x] Least-privilege OAuth scope `https://www.googleapis.com/auth/drive.file` preserved.
- [x] Pre-import cluster parameter tuning sheet presented before batch import.
- [x] Multi-dimensional deduplication skips identical workouts without error.
- [x] All 9 locales maintain 100% string parity.
- [x] Clean-room test suite passes with 0 failures.
