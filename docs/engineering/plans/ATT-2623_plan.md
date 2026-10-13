# Stage 3 Implementation Plan - ATT-2623: Structure Import tab into format-specific blocks (FIT, TCX, GPX) each offering Local, Dropbox, and Google Drive options

**Ticket**: [ATT-2623](https://atrainingtracker.atlassian.net/browse/ATT-2623)  
**Sub-task**: [ATT-2702](https://atrainingtracker.atlassian.net/browse/ATT-2702) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.3`  
**Branch**: `feature/ATT-2623`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Executive Summary & Problem Domain

### Problem Domain
During Sprint 2026-41.1 review (`ATT-2341`), inspecting `ImportBackupTabsScreen.kt` revealed that the Import tab suffers from an asymmetrical and fragmented organization:
* **Fragmented Formats**: FIT files have their own dedicated card with a single local file picker, while TCX and GPX files are lumped together in a generic legacy card.
* **Detached Cloud Scanning**: Dropbox and Google Drive scans are located only inside the legacy recovery card, hardcoded to scan all formats (`"all"`), depriving athletes of format-scoped cloud imports.
* **Ergonomic Inconsistency**: The lack of a uniform action structure across file formats creates visual clutter and navigational confusion.

### Proposed Architecture
Reorganize `ImportTabContent` in `ImportBackupTabsScreen.kt` into three dedicated format cards (FIT, TCX, GPX). Each card exposes a uniform action triad:
1. **Local File Picker**: Single/Batch for FIT via `ActivityResultContracts.OpenMultipleDocuments()`; single document picker for TCX and GPX via `ActivityResultContracts.OpenDocument()`.
2. **Dropbox Scan**: Scoped to the card's specific format (`"fit"`, `"tcx"`, `"gpx"`), opening `PreImportTuningBottomSheet` or showing disconnected dialog when unlinked.
3. **Google Drive Scan**: Scoped to the card's specific format (`"fit"`, `"tcx"`, `"gpx"`), opening `PreImportTuningBottomSheet` or showing disconnected dialog when unlinked.

---

## 2. Technical Architecture & Component Mapping (SWE.2)

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ImportBackupTabsScreen.kt                       │
│                                                                        │
│  State:                                                                │
│  - pendingDropboxFormat: String ("fit" / "tcx" / "gpx")                │
│  - pendingGoogleDriveFormat: String ("fit" / "tcx" / "gpx")            │
│  - pendingSingleLegacyFormat: String ("auto" / "tcx" / "gpx")          │
│                                                                        │
│  Layout: ImportTabContent                                              │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ FormatImportCard (FIT)                                           │  │
│  │  - Button 1: Local FIT Picker (Single/Batch)                     │  │
│  │  - Button 2: Scan Dropbox (FIT)                                  │  │
│  │  - Button 3: Scan Google Drive (FIT)                             │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ FormatImportCard (TCX)                                           │  │
│  │  - Button 1: Local TCX Picker                                    │  │
│  │  - Button 2: Scan Dropbox (TCX)                                  │  │
│  │  - Button 3: Scan Google Drive (TCX)                             │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ FormatImportCard (GPX)                                           │  │
│  │  - Button 1: Local GPX Picker                                    │  │
│  │  - Button 2: Scan Dropbox (GPX)                                  │  │
│  │  - Button 3: Scan Google Drive (GPX)                             │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ triggers format-scoped recovery
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        BackupRestoreViewModel.kt                       │
│                                                                        │
│  + bulkRecoverLegacyData(context, format = "fit" | "tcx" | "gpx")      │
│  + bulkRecoverGoogleDriveData(context, format = "fit" | "tcx" | "gpx") │
│  + importLegacyFile(context, uri, format = "tcx" | "gpx")              │
│  + importFitFiles(context, uris)                                       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ delegates
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                         LegacyImportEngine.kt                          │
│                                                                        │
│  + bulkRecoverFromDropbox(context, format, listener, strava)           │
│  + bulkRecoverFromGoogleDrive(context, format, listener, strava)       │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Step-by-Step Implementation Sequence

### Step 1: Define String Resources Across All 9 Locales
Add format titles, descriptions, and button strings to:
* `app/src/main/res/values/strings.xml`
* `app/src/main/res/values-de/strings.xml`
* `app/src/main/res/values-es/strings.xml`
* `app/src/main/res/values-fr/strings.xml`
* `app/src/main/res/values-it/strings.xml`
* `app/src/main/res/values-ja/strings.xml`
* `app/src/main/res/values-nl/strings.xml`
* `app/src/main/res/values-pl/strings.xml`
* `app/src/main/res/values-pt/strings.xml`

New keys:
* FIT: `import_fit_dropbox_button`, `import_fit_gdrive_button` (alongside existing `import_fit_title`, `import_fit_description`, `import_fit_button`).
* TCX: `import_tcx_title`, `import_tcx_description`, `import_tcx_button`, `import_tcx_dropbox_button`, `import_tcx_gdrive_button`.
* GPX: `import_gpx_title`, `import_gpx_description`, `import_gpx_button`, `import_gpx_dropbox_button`, `import_gpx_gdrive_button`.

### Step 2: Implement `FormatImportCard` Composable in `ImportBackupTabsScreen.kt`
Create modular composable:
```kotlin
@Composable
private fun FormatImportCard(
    title: String,
    description: String,
    localButtonText: String,
    onLocalClick: () -> Unit,
    dropboxButtonText: String,
    isDropboxConnected: Boolean,
    onDropboxClick: () -> Unit,
    googleDriveButtonText: String,
    isGoogleDriveConnected: Boolean,
    onGoogleDriveClick: () -> Unit,
    isBusy: Boolean
)
```
* Renders `ElevatedCard` (shape = 16.dp, defaultElevation = 2.dp).
* Displays bold title (`titleMedium`) and secondary description (`bodySmall`).
* Renders uniform button triad with standard icons (`Icons.Default.CloudUpload` or document icons) and connection-aware borders/colors.

### Step 3: Refactor `ImportTabContent` and Screen State Dispatch
* In `ImportBackupTabsScreen.kt`:
  - Introduce `pendingDropboxFormat: String`, `pendingGoogleDriveFormat: String`, and `pendingSingleLegacyFormat: String`.
  - In `ImportTabContent`, render three `FormatImportCard` instances (FIT, TCX, GPX).
  - Update `PreImportTuningBottomSheet` confirmation handlers to pass `pendingDropboxFormat` to `viewModel.bulkRecoverLegacyData(context, pendingDropboxFormat)` and `pendingGoogleDriveFormat` to `viewModel.bulkRecoverGoogleDriveData(context, pendingGoogleDriveFormat)`.
  - Update single legacy file picker to pass `pendingSingleLegacyFormat` to `viewModel.importLegacyFile(context, uri, pendingSingleLegacyFormat)`.

### Step 4: Unit, Contract & Localization Tests
1. `ImportFormatLocalizationTest.kt`:
   - Parse all 9 `strings.xml` files.
   - Assert all 12 format-related keys exist and contain non-empty text.
2. `BackupRestoreViewModelFormatScopingTest.kt`:
   - Verify `bulkRecoverLegacyData` passes `"fit"`, `"tcx"`, and `"gpx"` to `LegacyImportEngine`.
   - Verify `bulkRecoverGoogleDriveData` passes `"fit"`, `"tcx"`, and `"gpx"` to `LegacyImportEngine`.
3. `ImportFormatCardsContractTest.kt`:
   - Verify structural layout of `ImportTabContent` and triad button existence.

---

## 4. Invariants & Guardrails

1. **Pre-Import Cluster Tuning Enforcement**:
   - `PreImportTuningBottomSheet` (`REQ-MIG-012`, `REQ-UI-150`) MUST continue to be presented prior to starting cloud recovery or single file import.
2. **Cloud Connection Gating**:
   - Clicking Dropbox or Google Drive scan while unlinked MUST display `showDropboxDisconnectedDialog` or `showGoogleDriveDisconnectedDialog` (`REQ-MIG-023`, `REQ-MIG-034`).
3. **Multi-Document FIT Ingestion Parity**:
   - FIT local picker MUST continue using `ActivityResultContracts.OpenMultipleDocuments()` (`REQ-DAT-019`).
4. **Post-Import Navigation Agency**:
   - Success overlay (`REQ-MIG-032`) displaying `[Anzeigen]` and `[OK]` remains 100% functional.
5. **No Regressions**:
   - Full clean-room test suite `./gradlew testDebugUnitTest` must pass with 0 regressions.

---

## 5. UI Consistency (Rule 23)

* **Closest Reference Screen**: `ImportBackupTabsScreen.kt` (current screen) and `RestoreTabContent`.
* **Reused Components**:
  - `ElevatedCard`, `OutlinedButton`, `ButtonDefaults`, `MaterialTheme.typography`, `MaterialTheme.colorScheme`.
  - `LayoutConstants.HEADER_TITLE_ROW_HEIGHT`, `AppModalBottomSheet`.
* **Theme Tokens**:
  - Card shape: `RoundedCornerShape(16.dp)`, elevation `2.dp`.
  - Spacing: Root column uses `Arrangement.spacedBy(16.dp)`, inner card padding `16.dp`, inter-button spacing `8.dp`.
  - Deactivated buttons: `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)` for content and `alpha = 0.12f` for borders.
* **Justification for Style**: The format-specific card layout provides complete visual parity with existing Material 3 elevated card patterns while replacing the fragmented legacy card with a consistent, modern triad layout.

---

## 6. Verification & Test Plan

1. **Targeted Unit & Localization Tests**:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.ImportFormatLocalizationTest"
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.BackupRestoreViewModelFormatScopingTest"
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.ImportFormatCardsContractTest"
   ```
2. **Full Clean-Room Regression Suite**:
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## 7. Gate 3 Readiness Checklist

- [x] Architectural decomposition complete (SWE.2).
- [x] Step-by-step sequencing defined.
- [x] Invariants and rollback safety verified.
- [x] UI Consistency section populated per Rule 23.
- [x] Ready for Gate 3 Audit submission.
