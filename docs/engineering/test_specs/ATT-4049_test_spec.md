# Stage 2: Requirement & Test Specification - ATT-4049: Support selecting and importing multiple GPX route files at once

**Ticket**: [ATT-4049](https://atrainingtracker.atlassian.net/browse/ATT-4049)  
**Sub-task**: [ATT-4277](https://atrainingtracker.atlassian.net/browse/ATT-4277) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-MAP-053` (*Multi-File Route Selection, Batch Import & External Multi-Share Processing*)  
**Test Spec ID**: `TST-MAP-055`  
**Branch**: `feature/ATT-4049`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Requirement Specification (`REQ-MAP-053`)

### 1.1 Problem Statement & Rationale
Currently, route file import from the Routes screen (`RoutesScreen.kt:67-74`) uses `ActivityResultContracts.GetContent()`, allowing selection of only one file at a time and launching `GpxImportActivity` for that single file. When an athlete transfers a set of route files (e.g. 5–20 stages of a cycling holiday or trail network), selecting and confirming each file individually through the interactive `EditRouteScreen` is tedious and repetitive.

To resolve this limitation while preserving existing workflows:
1. The file picker contract is updated to `ActivityResultContracts.GetMultipleContents()`, allowing users to select either one or multiple files.
2. If a single file is selected (`uris.size == 1`), the existing interactive preview/edit flow (`GpxImportActivity` -> `EditRouteScreen`) is preserved.
3. If multiple files are selected (`uris.size > 1`), a background batch import runs on `Dispatchers.IO`, parsing files via `GpxRouteImporter`, enriching DEM elevations, detecting climbs, and inserting routes directly into `RoutesRepository`.
4. Progress feedback is shown via a non-dismissible modal progress dialog ("Importing routes: X of N..."), followed by a localized summary `Snackbar` upon completion.
5. In addition, `GpxImportActivity` is enhanced to support external multi-file share intents (`Intent.ACTION_SEND_MULTIPLE`).

---

### 1.2 Functional & Architectural Requirements (`REQ-MAP-053`)

1. **Multi-File Document Picker Contract (`REQ-MAP-053.1`)**:
   - In `RoutesScreen.kt`, the route picker launcher SHALL be registered using `rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents())`.
   - The launcher SHALL accept MIME type `*/*` when triggered from `onImportClick`.
   - If the callback receives an empty list (`uris.isEmpty()`), the system SHALL perform no action.

2. **Single vs. Batch Route Import Discrimination (`REQ-MAP-053.2`)**:
   - *Single File Selection (`uris.size == 1`)*: The system SHALL launch `GpxImportActivity` with `intent.data = uris.first()`, opening `EditRouteScreen` to allow previewing, renaming, and editing the route summary before manual saving.
   - *Multiple File Selection (`uris.size > 1`)*: The system SHALL bypass `EditRouteScreen` and execute an asynchronous batch import in `RoutesViewModel.importRoutesBatch(uris)` on `Dispatchers.IO`.

3. **Batch Import Execution & State Management in `RoutesViewModel` (`REQ-MAP-053.3`)**:
   - `RoutesViewModel` SHALL expose:
     - `batchImportProgress: StateFlow<BatchImportProgress?>` where `data class BatchImportProgress(val current: Int, val total: Int)` tracks the 1-based index and total count of files being processed.
     - `batchImportResult: StateFlow<BatchImportResult?>` where `data class BatchImportResult(val importedCount: Int, val failedCount: Int, val totalCount: Int)` records the final import tally.
     - `fun clearBatchImportResult()` to reset the result state after user notification.
   - `RoutesViewModel` SHALL accept `gpxRouteImporter: GpxRouteImporter = GpxRouteImporter(application)` via `@JvmOverloads` constructor.
   - Inside `importRoutesBatch(uris: List<Uri>)`:
     - For each URI $i \in [1, N]$:
       - Update `_batchImportProgress.value = BatchImportProgress(current = i, total = N)`
       - Invoke `gpxRouteImporter.importRouteFromGpx(uri)`.
       - On success: persist via `routesRepository.insertRoute(summary, pathPoints, waypoints)` and increment `importedCount`.
       - On failure or parse exception: log error, increment `failedCount`, and proceed to the next URI without terminating the batch.
     - Upon completion: reset `_batchImportProgress.value = null` and emit `_batchImportResult.value = BatchImportResult(importedCount, failedCount, totalCount)`.

4. **Modal Progress & Summary Notification in `RoutesScreen` (`REQ-MAP-053.4`)**:
   - When `batchImportProgress != null`, `RoutesScreen` SHALL display a modal dialog with `CircularProgressIndicator` and localized text `R.string.routes_import_progress` displaying `current` and `total`.
   - When `batchImportResult != null`, `RoutesScreen` SHALL present a localized `Snackbar`:
     - If `failedCount == 0`: `R.string.routes_import_success` ("%1$d routes imported successfully.")
     - If `failedCount > 0 && importedCount > 0`: `R.string.routes_import_partial` ("%1$d routes imported, %2$d failed.")
     - If `importedCount == 0`: `R.string.routes_import_all_failed` ("Failed to import routes.")
   - Upon presenting the message, `viewModel.clearBatchImportResult()` SHALL be called.

5. **External Multi-File Share Support (`ACTION_SEND_MULTIPLE`) in `GpxImportActivity` (`REQ-MAP-053.5`)**:
   - `AndroidManifest.xml` SHALL declare intent filters on `GpxImportActivity` for `android.intent.action.SEND` and `android.intent.action.SEND_MULTIPLE` supporting GPX/XML/octet-stream MIME types.
   - `GpxImportActivity.kt` and `GpxImportViewModel.kt` SHALL inspect incoming intent action and streams:
     - If single URI (`ACTION_VIEW`, `ACTION_SEND`, or 1 stream): proceed with `ImportState.Editing` and `EditRouteScreen`.
     - If multiple URIs (`ACTION_SEND_MULTIPLE`): execute batch import, emitting `ImportState.BatchProgress(current, total)` and `ImportState.BatchSuccess(importedCount, failedCount)` with `Toast` and `finish()`.

6. **9-Language Localization Parity (`REQ-MAP-053.6`)**:
   - String tokens `routes_import_progress`, `routes_import_success`, `routes_import_partial`, and `routes_import_all_failed` SHALL be localized across EN, DE, ES, FR, IT, JA, NL, PL, and PT without placeholder format mismatches.

---

### 1.3 Given-When-Then Acceptance Criteria

- **AC-1 (Single-File Selection Preserved)**:
  - *Given* an athlete on the Routes screen taps the Import button,
  - *When* the user selects exactly 1 GPX file in the file picker,
  - *Then* `GpxImportActivity` opens `EditRouteScreen`, allowing manual inspection, renaming, and saving.

- **AC-2 (Multi-File Selection & Batch Import)**:
  - *Given* an athlete on the Routes screen taps the Import button,
  - *When* the user selects multiple valid GPX files ($N > 1$),
  - *Then* `RoutesViewModel.importRoutesBatch(...)` runs, displaying the progress dialog with `X of N`, inserting all routes into `RoutesRepository`, and displaying the success `Snackbar`.

- **AC-3 (Batch Fault Tolerance)**:
  - *Given* a multi-file selection containing 2 valid GPX files and 1 corrupted file,
  - *When* batch import executes,
  - *Then* both valid files are successfully imported, the corrupt file is skipped, and the `Snackbar` reports `2 routes imported, 1 failed`.

- **AC-4 (External Multi-File Share)**:
  - *Given* an external app shares multiple GPX files via `ACTION_SEND_MULTIPLE`,
  - *When* `GpxImportActivity` opens,
  - *Then* all valid files are imported in batch and a completion Toast is displayed.

---

## 2. Test Specification (`TST-MAP-055`)

### 2.1 Unit Tests (`RoutesViewModelTest.kt`)

1. **`TST-MAP-055_1`: All Valid Files Batch Import**:
   - Mock `gpxRouteImporter.importRouteFromGpx(uri)` returning valid `RouteImportResult` for 3 distinct URIs.
   - Call `viewModel.importRoutesBatch(listOf(uri1, uri2, uri3))`.
   - Verify:
     - `routesRepository.insertRoute(...)` is called exactly 3 times with the parsed route data.
     - `batchImportProgress` is null after completion.
     - `batchImportResult` equals `BatchImportResult(importedCount = 3, failedCount = 0, totalCount = 3)`.

2. **`TST-MAP-055_2`: Partial Failure Batch Import**:
   - Mock URI 1 and URI 3 as valid, and URI 2 returning `Result.failure(Exception("Corrupt XML"))`.
   - Call `viewModel.importRoutesBatch(listOf(uri1, uri2, uri3))`.
   - Verify:
     - `routesRepository.insertRoute(...)` is called 2 times (for URI 1 and URI 3).
     - `batchImportResult` equals `BatchImportResult(importedCount = 2, failedCount = 1, totalCount = 3)`.

3. **`TST-MAP-055_3`: All Files Failed Batch Import**:
   - Mock all 3 URIs returning failures.
   - Call `viewModel.importRoutesBatch(listOf(uri1, uri2, uri3))`.
   - Verify:
     - `routesRepository.insertRoute(...)` is never called.
     - `batchImportResult` equals `BatchImportResult(importedCount = 0, failedCount = 3, totalCount = 3)`.

4. **`TST-MAP-055_4`: Empty URI List**:
   - Call `viewModel.importRoutesBatch(emptyList())`.
   - Verify no state changes, no repo calls, and no exceptions.

5. **`TST-MAP-055_5`: Clear Batch Import Result**:
   - After batch import sets `batchImportResult`, call `viewModel.clearBatchImportResult()`.
   - Verify `batchImportResult.value == null`.

---

### 2.2 Unit Tests (`GpxImportViewModelTest.kt`)

6. **`TST-MAP-055_6`: Multi-URI Batch Handling in GpxImportViewModel**:
   - Mock `importer.importRouteFromGpx(uri)` for 2 URIs.
   - Call `viewModel.handleBatchUris(listOf(uri1, uri2))`.
   - Verify `repository.insertRoute(...)` called twice, and final state is `ImportState.BatchSuccess(importedCount = 2, failedCount = 0)`.

---

### 2.3 Localization Parity Audit (`TranslationParityTest.kt`)

7. **`TST-MAP-055_7`: 9-Language Localization Verification**:
   - Verify string keys `routes_import_progress`, `routes_import_success`, `routes_import_partial`, and `routes_import_all_failed` exist in:
     - `values/strings.xml` (EN)
     - `values-de/strings.xml` (DE)
     - `values-es/strings.xml` (ES)
     - `values-fr/strings.xml` (FR)
     - `values-it/strings.xml` (IT)
     - `values-ja/strings.xml` (JA)
     - `values-nl/strings.xml` (NL)
     - `values-pl/strings.xml` (PL)
     - `values-pt/strings.xml` (PT)
   - Verify placeholder `%1$d`, `%2$d` consistency across all 9 languages.

---

### 2.4 Clean-Room Full Suite Regression Execution

8. **`TST-MAP-055_8`: Clean-Room Gradle Build & Test Execution**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions across the codebase.

---

## 3. Traceability Matrix

| Requirement Clause | Test ID | Target File | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-053.1` (Multi-File Picker Contract) | `TST-MAP-055_1`, `TST-MAP-055_4` | `RoutesScreen.kt` | Contract & Compose Inspection |
| `REQ-MAP-053.2` (Single vs Batch Flow) | `TST-MAP-055_1` | `RoutesScreen.kt` | Unit & Activity Flow Verification |
| `REQ-MAP-053.3` (Batch Import Execution) | `TST-MAP-055_1`, `TST-MAP-055_2`, `TST-MAP-055_3`, `TST-MAP-055_4`, `TST-MAP-055_5` | `RoutesViewModel.kt`, `RoutesViewModelTest.kt` | Robolectric Unit Tests |
| `REQ-MAP-053.4` (Progress Dialog & Snackbar) | `TST-MAP-055_1`, `TST-MAP-055_2` | `RoutesScreen.kt` | UI State Mapping Verification |
| `REQ-MAP-053.5` (External Multi-Share Support) | `TST-MAP-055_6` | `GpxImportActivity.kt`, `GpxImportViewModel.kt` | Unit & Manifest Contract Tests |
| `REQ-MAP-053.6` (9-Language Parity) | `TST-MAP-055_7` | `res/values*/strings.xml`, `TranslationParityTest.kt` | Automated String Parity Audit |
| Full-Suite Invariant | `TST-MAP-055_8` | Project Wide | `./gradlew testDebugUnitTest` |
