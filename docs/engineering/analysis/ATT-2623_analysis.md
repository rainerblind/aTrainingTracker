# Stage 1 Analysis: ATT-2623 - Structure Import tab into format-specific blocks (FIT, TCX, GPX) each offering Local, Dropbox, and Google Drive options

**Ticket**: [ATT-2623](https://atrainingtracker.atlassian.net/browse/ATT-2623)  
**Sub-task**: [ATT-2700](https://atrainingtracker.atlassian.net/browse/ATT-2700) (`[Analysis]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2623`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-41.1 review on physical devices (Sprint review for `ATT-2341`), inspecting the Import tab in `ImportBackupTabsScreen.kt` revealed an asymmetrical, fragmented, and confusing layout:
* **Asymmetrical Format Representation**:
  * FIT file import was granted its own dedicated card ("FIT Dateien importieren") offering a single action button to select local FIT files via `ActivityResultContracts.OpenMultipleDocuments()`.
  * In contrast, TCX and GPX formats were combined together into a generic "Legacy Recovery" ("Workout Import") card.
* **Displaced Cloud Ingestion**:
  * Cloud scanning options (Dropbox and Google Drive) were lumped together inside the generic legacy card, detached from the FIT format block.
  * Tapping "Scan Dropbox" or "Scan Google Drive" triggered bulk recovery with hardcoded `format = "all"`, scanning all folders across all extensions simultaneously. Athletes wishing to import only FIT files or only TCX files from their cloud storage could not scope their recovery.
* **Ergonomic Inconsistency**:
  * Athletes have workouts originating from various sources (Garmin, Wahoo, Polar, Komoot, Strava exports). The absence of a uniform action structure across file formats creates visual clutter and cognitive friction.

### Expected Behavior
Reorganize the Import tab (`ImportTabContent` in `ImportBackupTabsScreen.kt`) into three distinct, format-specific cards (FIT, TCX, GPX) providing complete source parity via a uniform action triad:
1. **FIT Card (Garmin / Wahoo / Hammerhead)**:
   - Button 1: Select local FIT file(s) (Single / Batch picker)
   - Button 2: Scan Dropbox (FIT)
   - Button 3: Scan Google Drive (FIT)
2. **TCX Card (Training Center XML)**:
   - Button 1: Select local TCX file
   - Button 2: Scan Dropbox (TCX)
   - Button 3: Scan Google Drive (TCX)
3. **GPX Card (GPS Exchange Format)**:
   - Button 1: Select local GPX file
   - Button 2: Scan Dropbox (GPX)
   - Button 3: Scan Google Drive (GPX)

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Incremental Feature Accretion in `ImportBackupTabsScreen.kt`
Forensic examination of `ImportBackupTabsScreen.kt` (lines 970–1079) explains the architectural divergence:
1. Originally, `ImportBackupTabsScreen` provided a single "Legacy Recovery" card designed to recover TCX and GPX files from Dropbox (`bulkRecoverLegacyData`).
2. Later, when Google Drive cloud recovery was implemented (`ATT-2346` / `REQ-MIG-034`), a "Scan Google Drive" button was added to the existing legacy card, passing format `"all"`.
3. When Garmin binary FIT file support was introduced (`ATT-1828` / `REQ-DAT-019`), a new `FIT Importer Card` was added above the legacy card with only a local file picker (`onFitImportClick`).
4. While Dropbox FIT recovery was subsequently enabled in `LegacyImportEngine` (`ATT-2341` / `REQ-MIG-035`), the UI was never refactored to expose format-specific cloud triggers to athletes.

### 2.2 Existing Engine & ViewModel Readiness
Investigation of the underlying architecture confirms that both `LegacyImportEngine` and `BackupRestoreViewModel` are already built to support format-specific operations:
* `BackupRestoreViewModel.bulkRecoverLegacyData(context: Context, format: String)` (line 507) accepts a format parameter.
* `BackupRestoreViewModel.bulkRecoverGoogleDriveData(context: Context, format: String, dispatcher: CoroutineDispatcher)` (line 554) accepts a format parameter.
* `LegacyImportEngine.bulkRecoverFromDropbox` (lines 153–165):
  ```kotlin
  val possiblePaths = when (format.lowercase()) {
      "tcx" -> listOf("/TCX", "/apps/Workouts/TCX")
      "gpx" -> listOf("/GPX", "/apps/Workouts/GPX")
      "fit" -> listOf("/FIT", "/apps/Workouts/FIT", "/Workouts/FIT")
      else -> listOf("/TCX", "/apps/Workouts/TCX", "/GPX", "/apps/Workouts/GPX", "/FIT", "/apps/Workouts/FIT", "/Workouts/FIT")
  }
  val targetExtensions = when (format.lowercase()) {
      "tcx" -> listOf(".tcx")
      "gpx" -> listOf(".gpx")
      "fit" -> listOf(".fit")
      else -> listOf(".tcx", ".gpx", ".fit")
  }
  ```
* `LegacyImportEngine.bulkRecoverFromGoogleDrive` (lines 325–330):
  ```kotlin
  val targetExtensions = when (format.lowercase()) {
      "fit" -> listOf(".fit")
      "tcx" -> listOf(".tcx")
      "gpx" -> listOf(".gpx")
      else -> listOf(".fit", ".tcx", ".gpx")
  }
  ```
* `BackupRestoreViewModel.importLegacyFile(context: Context, uri: Uri, format: String)` (line 371) uses `format` as fallback when parsing single files without explicit extension.

### 2.3 UI State Limitation in `ImportBackupTabsScreen.kt`
Currently, `ImportBackupTabsScreen` manages cloud recovery triggers using simple booleans without preserving the requested format:
```kotlin
var showTuningDialogForBulk by remember { mutableStateOf(false) }
var showTuningDialogForGoogleDrive by remember { mutableStateOf(false) }
```
When `showTuningDialogForBulk` confirms, it hardcodes:
```kotlin
viewModel.bulkRecoverLegacyData(context, "all")
```
And `showTuningDialogForGoogleDrive` hardcodes:
```kotlin
viewModel.bulkRecoverGoogleDriveData(context, "all")
```
To enable format scoping, the screen must track the pending format for both Dropbox and Google Drive recovery actions (e.g. `pendingDropboxRecoveryFormat`, `pendingGoogleDriveRecoveryFormat`), and similarly preserve the format for single-file legacy imports (`pendingSingleLegacyFormat`).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Objectives**:
  1. Restructure `ImportTabContent` in `ImportBackupTabsScreen.kt` into three format-specific cards:
     - FIT Card: Title, description, Local File Picker (Single/Batch), Dropbox Scan (FIT), Google Drive Scan (FIT).
     - TCX Card: Title, description, Local File Picker, Dropbox Scan (TCX), Google Drive Scan (TCX).
     - GPX Card: Title, description, Local File Picker, Dropbox Scan (GPX), Google Drive Scan (GPX).
  2. Implement uniform action styling, icons, and connection-gating:
     - Cloud scan buttons must reflect authentication state (`isDropboxConnected`, `isGoogleDriveConnected`).
     - When disconnected, buttons display deactivated styling and tapping them opens the existing disconnected explanation dialog.
  3. Track pending format states in `ImportBackupTabsScreen`:
     - Pass the active format into `PreImportTuningBottomSheet`.
     - Upon confirmation, invoke `viewModel.bulkRecoverLegacyData(context, format)` and `viewModel.bulkRecoverGoogleDriveData(context, format)` with `"fit"`, `"tcx"`, or `"gpx"`.
  4. Ensure 100% 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for all new and updated format headers and button labels.
  5. Add unit and contract tests verifying format-scoped recovery dispatch and localization completeness.

* **Out-of-Scope Non-Goals**:
  * Modifying `LegacyImportEngine` parsing routines or coroutine worker channels (already tested and verified).
  * Altering Backup or Restore tabs in `ImportBackupTabsScreen.kt`.
  * Altering Room database schema or workout persistence logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

### 4.1 Targeted Requirements
* **`REQ-UI-293`**: *Format-Specific Import Tab Architecture with Uniform Source Triad (FIT, TCX, GPX)* (Net-new requirement).
* Refines and extends UI presentation originally established under:
  - `REQ-MIG-011`: *Tabbed Import & Backup UI*
  - `REQ-DAT-019`: *External FIT Workout Importer with Duplicate Detection & Sport Mapping*
  - `REQ-MIG-034`: *Google Drive Historical Workout File Discovery & Bulk Recovery*
  - `REQ-MIG-035`: *Dropbox Historical Workout File Discovery & Bulk Recovery for FIT*

### 4.2 Four Mandatory Archaeology Fields
1. **Original Target & Historical Trace**:
   `REQ-UI-293` is a net-new requirement extending Epic `ATT-281` (*Data Sovereignty & Migration*) and Sprint `2026-41.3`. It refines the UI layout originally defined across `REQ-MIG-011`, `REQ-DAT-019`, and `REQ-MIG-034`.
2. **Historical Origin & Commit Trace**:
   Earlier tickets (`ATT-1828` for FIT, `ATT-2341` for Dropbox FIT, `ATT-2346` for Google Drive) incrementally added functionality to the import tab. Because each feature was integrated independently, the UI ended up with an asymmetrical structure (FIT had its own card; TCX/GPX were lumped in legacy recovery; cloud scanning was isolated in legacy recovery with hardcoded `"all"`).
3. **Root Reason for Existing Formulation**:
   The existing formulation was a product of transitional evolutionary steps: TCX and GPX were historically the only supported legacy migration formats, so they shared a single "Legacy Recovery" card. When FIT was introduced later, it was placed in a separate card to avoid disturbing legacy recovery.
4. **Preservation of Core Invariants**:
   * Pre-import cluster parameter tuning via `PreImportTuningBottomSheet` (`REQ-MIG-012`, `REQ-UI-150`) remains mandatory before cloud scanning or single legacy import commences.
   * Cloud disconnection gating (`REQ-MIG-023`, `REQ-MIG-034`) remains strictly enforced with informational alert dialogs.
   * Interactive post-import workout navigation (`REQ-MIG-032`) via `StateOverlaySection` remains unchanged.
   * Multi-document selection (`OpenMultipleDocuments`) for FIT files remains preserved.
   * All database deduplication, clustering, and Strava upload settings remain fully intact.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Modular Format Import Card Composable
Define a reusable, cleanly structured composable `FormatImportCard` within `ImportBackupTabsScreen.kt`:
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
* **ElevatedCard**: Standard rounded corners (16.dp), elevation (2.dp), container styling matching project standards.
* **Layout**:
  - Title: `MaterialTheme.typography.titleMedium`, bold.
  - Description: `MaterialTheme.typography.bodySmall`, `onSurfaceVariant`.
  - Button 1 (Local): `OutlinedButton` with upload icon.
  - Button 2 (Dropbox): `OutlinedButton` with scan styling and disconnected gating.
  - Button 3 (Google Drive): `OutlinedButton` with scan styling and disconnected gating.

### 5.2 Format-Scoped State Management in `ImportBackupTabsScreen`
Update state tracking in `ImportBackupTabsScreen`:
```kotlin
var pendingDropboxFormat by remember { mutableStateOf("all") }
var pendingGoogleDriveFormat by remember { mutableStateOf("all") }
var pendingSingleLegacyFormat by remember { mutableStateOf("auto") }
```
When a cloud button is clicked:
```kotlin
onDropboxScanClick = { format ->
    if (isDropboxConnected) {
        pendingDropboxFormat = format
        showTuningDialogForBulk = true
    } else {
        showDropboxDisconnectedDialog = true
    }
}
```
And on confirmation in `PreImportTuningBottomSheet`:
```kotlin
viewModel.bulkRecoverLegacyData(context, pendingDropboxFormat)
```
Similarly for Google Drive:
```kotlin
viewModel.bulkRecoverGoogleDriveData(context, pendingGoogleDriveFormat)
```
And for local single imports (TCX/GPX):
```kotlin
pendingSingleLegacyFormat = format
pickLegacyFileLauncher.launch(arrayOf("*/*"))
```

### 5.3 Complete 9-Language Localization
Ensure all required strings are defined in:
- `values/strings.xml` (Default / English)
- `values-de/strings.xml` (German)
- `values-es/strings.xml` (Spanish)
- `values-fr/strings.xml` (French)
- `values-it/strings.xml` (Italian)
- `values-ja/strings.xml` (Japanese)
- `values-nl/strings.xml` (Dutch)
- `values-pl/strings.xml` (Polish)
- `values-pt/strings.xml` (Portuguese)

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing backup, restore, or import workflows.
  2. Cloud authentication guards (`isDropboxConnected`, `isGoogleDriveConnected`) remain strictly enforced.
  3. Pre-import cluster parameter persistence to SharedPreferences remains intact.
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  - The underlying recovery engines (`LegacyImportEngine.bulkRecoverFromDropbox` and `bulkRecoverFromGoogleDrive`) already support format filtering.
  - Changes are focused on the UI presentation layer and parameter routing in `ImportBackupTabsScreen.kt`.

---

## 7. Review Gate 1 Readiness

* Forensic analysis completed and root causes identified.
* Scope strictly bounded to Import tab reorganization and format-scoped cloud triggering.
* Chesterton's Fence archaeology documented against `REQ-UI-293`.
* Ready for Gate 1 Audit submission.
