# Stage 1 Analysis: ATT-4049 — Support selecting and importing multiple GPX route files at once

## 1. Executive Summary
- **Ticket**: `ATT-4049` — `[Verbesserung] [Medium] Support selecting and importing multiple GPX route files at once` (Epic: `ATT-66` *Improve Routes*)
- **Classification**: Feature Enhancement / UX Optimization / Architecture Parity
- **Context & Motivation**:
  Currently, importing GPX/TCX route files into the application from the Routes screen (`RoutesScreen.kt:67-74` and `RoutesFragment.kt:78-84`) relies on `ActivityResultContracts.GetContent()`, which restricts the system document picker to selecting a single file at a time. After picking a file, the app immediately launches `GpxImportActivity` with the selected `Uri`, presenting the interactive `EditRouteScreen` to preview, rename, and manually save the single route.
  When athletes want to import a collection of routes (such as curated holiday stages, race courses, or bulk-transferred GPX files from a desktop/GPS computer), repeating this modal picker-preview-save cycle for every single route is cumbersome and inefficient.
  This improvement introduces:
  1. **Multi-File Selection Contract**: Modernizing the picker launcher to `ActivityResultContracts.GetMultipleContents()`, returning `List<Uri>`.
  2. **Single vs. Batch Import Differentiation**:
     - *Single File (`uris.size == 1`)*: Preserves the existing interactive `GpxImportActivity` preview/edit flow (`EditRouteScreen`) so users retain full editorial control over single route imports.
     - *Multiple Files (`uris.size > 1`)*: Automatically triggers a headless batch import across all selected URIs with real-time progress feedback (`Dialog` showing "Importing routes: X of N..."), persisting valid routes directly into `RoutesRepository`, and providing a localized summary Snackbar (`X routes imported`, `X routes imported, Y failed`).
  3. **Fault Tolerance & Resilience**: Malformed or unparseable GPX/TCX files in a batch are gracefully caught, logged, and counted as failures without interrupting or aborting the import of remaining valid files.
  4. **External Multi-File Share Support**: Enhancing `GpxImportActivity` and `AndroidManifest.xml` to support `Intent.ACTION_SEND_MULTIPLE` (`EXTRA_STREAM` `ArrayList<Uri>`), enabling batch import directly from third-party file managers or cloud storage apps.

---

## 2. Problem Domain & Forensic Investigation

### 2.1 Current Route Import Architecture
In `RoutesScreen.kt`:
```kotlin
val gpxPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    uri?.let {
        val intent = Intent(context, GpxImportActivity::class.java).apply {
            data = it
        }
        context.startActivity(intent)
    }
}
```
And in `RouteTabbedScreen.kt`:
```kotlin
onImportClick = { gpxPickerLauncher.launch("*/*") }
```
When `gpxPickerLauncher.launch("*/*")` is invoked, Android opens the SAF system file picker in single-selection mode. The callback receives a single nullable `Uri?`.

In `RoutesFragment.kt`:
Lines 78-85 contain a redundant, orphaned registration of `registerForActivityResult(ActivityResultContracts.GetContent())` which is never referenced or forwarded to `RoutesScreen`.

In `GpxImportActivity.kt`:
```kotlin
val uri = intent.data ?: intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
viewModel.handleIntent(uri)
```
And `AndroidManifest.xml`:
`GpxImportActivity` only declares `<action android:name="android.intent.action.VIEW" />` intent filters. It lacks intent filters for `android.intent.action.SEND` or `android.intent.action.SEND_MULTIPLE`.

### 2.2 ActivityResultContracts: `GetContent` vs. `GetMultipleContents`
- `ActivityResultContracts.GetContent()` initiates `Intent.ACTION_GET_CONTENT` with `EXTRA_ALLOW_MULTIPLE = false`, returning `Uri?`.
- `ActivityResultContracts.GetMultipleContents()` initiates `Intent.ACTION_GET_CONTENT` with `EXTRA_ALLOW_MULTIPLE = true`, returning `List<Uri>`.
Both contracts accept `String` for MIME type filtering (`"*/*"` or `"application/gpx+xml"`). Replacing `GetContent()` with `GetMultipleContents()` is completely backwards-compatible with single-file selection: when the user selects a single file in the multi-select picker, the callback delivers a list containing exactly 1 element (`uris.size == 1`).

### 2.3 Single vs. Batch Processing Semantics
- **Case 1: `uris.size == 1`**:
  Directly launch `GpxImportActivity` passing `data = uris.first()`. The user experiences the exact same workflow as before: elevation enrichment, interactive route editing/preview (`EditRouteScreen`), and manual save confirmation.
- **Case 2: `uris.size > 1`**:
  Launching `GpxImportActivity` $N$ times or presenting $N$ consecutive edit screens creates UI thrashing. Instead, batch import is executed headlessly in the background via `RoutesViewModel.importRoutesBatch(uris)` (or `GpxImportViewModel.handleUris(uris)` if launched externally):
  - For each URI $i \in [1, N]$:
    - Update progress state: `_batchImportProgress.value = BatchImportProgress(current = i, total = N)`
    - Parse GPX/TCX and enrich DEM elevation via `GpxRouteImporter.importRouteFromGpx(uri)`.
    - On success: persist via `RoutesRepository.insertRoute(...)`, increment `importedCount`.
    - On failure: log error, increment `failedCount`, and proceed to the next URI.
  - Reset progress state to `null`.
  - Emit `BatchImportResult(importedCount, failedCount, totalCount)`.
  - The UI presents a localized Snackbar/Toast:
    - All succeeded: `R.string.routes_import_success` ("%1$d routes imported successfully.")
    - Partial failure: `R.string.routes_import_partial` ("%1$d routes imported, %2$d failed.")
    - All failed: `R.string.routes_import_all_failed` ("Failed to import routes.")

### 2.4 External Multi-File Share Support (`ACTION_SEND_MULTIPLE`)
When sharing multiple files from file managers (e.g. Google Files, Total Commander) or messengers, Android sends an intent with:
- `action == Intent.ACTION_SEND_MULTIPLE`
- `type` set to MIME type or `*/*`
- `Intent.EXTRA_STREAM` containing `ArrayList<Uri>`
By adding `<action android:name="android.intent.action.SEND" />` and `<action android:name="android.intent.action.SEND_MULTIPLE" />` to `AndroidManifest.xml`, `GpxImportActivity` can handle external shares seamlessly:
- Single URI (`ACTION_VIEW` or `ACTION_SEND`) -> `EditRouteScreen`
- Multiple URIs (`ACTION_SEND_MULTIPLE`) -> Batch import progress -> Toast -> `finish()`.

---

## 3. User Scope Grounding (`ATT-1250`)

### 3.1 In-Scope Objectives
1. **Picker Contract Replacement**:
   - Modernize `RoutesScreen.kt` to use `ActivityResultContracts.GetMultipleContents()`.
   - Clean up orphaned registration in `RoutesFragment.kt`.
2. **Batch Import Execution in `RoutesViewModel`**:
   - Introduce `importRoutesBatch(uris: List<Uri>)` with state flows for progress (`BatchImportProgress`) and completion (`BatchImportResult`).
   - Clean constructor injection for `gpxRouteImporter: GpxRouteImporter = GpxRouteImporter(application)` allowing isolated unit testing.
3. **Modal Progress & Completion Feedback in `RoutesScreen`**:
   - Render non-dismissible `Dialog` with `CircularProgressIndicator` and progress label `X of N` during batch import.
   - Display localized summary `Snackbar` upon completion.
4. **Resilience & Fault Tolerance**:
   - Isolate each file's import in `try-catch` / `runCatching` so individual corrupt files do not abort the remaining batch.
5. **External Share Intent Support in `GpxImportActivity`**:
   - Handle `ACTION_SEND_MULTIPLE` in `GpxImportActivity.kt` and `GpxImportViewModel.kt`.
   - Declare `ACTION_SEND` and `ACTION_SEND_MULTIPLE` intent filters in `AndroidManifest.xml`.
6. **9-Language Localization Parity**:
   - Add localized string tokens for batch progress, success, partial success, and failure across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
7. **Comprehensive Unit & Contract Tests**:
   - Add tests for `RoutesViewModel.importRoutesBatch(...)` covering all-success, partial-failure, all-failure, and empty lists.
   - Add tests for `GpxImportViewModel` multi-URI batch handling.

### 3.2 Out-of-Scope Adjacent Refactorings
- Modifying the underlying GPX/TCX XML parser implementations (`GpxTrackpointParser`, `TcxCourseParser`).
- Changing the elevation enrichment logic or Open-Meteo DEM client in `GpxRouteImporter`.
- Rewriting route database schemas or table indexes in `RoutesDatabaseManager`.

---

## 4. Chesterton's Fence Requirement Archaeology (`REQ-PRO-022`)

### 4.1 Original Requirement ID & Target
- Net-new requirement: `REQ-MAP-053` (*Multi-File Route Selection, Batch Import & External Multi-Share Processing*).
- Complements:
  - `REQ-MAP-025` (Automatic DEM Elevation Enrichment for Imported GPX Routes)
  - `REQ-MAP-026` (Support Waypoints, POIs and TCX Course Points)
  - `REQ-UI-159` (Routes tabbed list, detail map view, and editing screen)
- Targets:
  - `RoutesScreen.kt`, `RoutesFragment.kt`, `RoutesViewModel.kt`
  - `GpxImportActivity.kt`, `GpxImportViewModel.kt`
  - `AndroidManifest.xml`
  - `res/values*/strings.xml`

### 4.2 Historical Origin & Commit Trace
- Sprint `2026-42.5` (`ATT-2940`, commit `a1dc652e`): Integrated route navigation and detail sheets.
- Sprint `2026-42.6` (`ATT-3167`, commit `cecb0dca`): Applied `navigationBarsPadding` to `SnackbarHost` in `RoutesScreen`.
- Sprint `2026-42.7` (`ATT-2583`, commit `b20c2da6`): Route waypoints and climb detection.
- Historical route import was strictly single-file oriented because routes were initially imported manually one-by-one with user inspection.

### 4.3 Root Reason for Existing Formulation
The initial single-file picker (`ActivityResultContracts.GetContent()`) was chosen as the minimal SAF contract when route import was first built. The workflow forced every imported route through `EditRouteScreen` to let the user select sport type (`bSportType`) and name. When users acquire route packs (e.g. 10 stages of a bike tour), this single-file constraint requires 10 distinct file picker invocations and 10 manual confirmations.

### 4.4 Preservation of Core Invariants
- **Single-File Preview Invariant**: If a user selects exactly 1 file, `GpxImportActivity` and `EditRouteScreen` are launched immediately, preserving 100% of the existing user experience for individual file imports.
- **Data Integrity Invariant**: All routes imported via batch undergo the exact same validation, distance calculation, DEM elevation enrichment, and climb detection (`ClimbDetector.detectClimbs`) as single imports via `RoutesRepository.insertRoute(...)`.
- **UI Non-Blocking Invariant**: Batch import runs asynchronously on `Dispatchers.IO` via `viewModelScope`, leaving the UI thread unblocked and updating the route list reactively via `RoutesRepository.allRoutes`.

---

## 5. Architectural Design & Component Interactions

```
+-------------------------------------------------------------+
|                        RoutesScreen                         |
|                                                             |
|  ActivityResultContracts.GetMultipleContents()             |
|       |                                                     |
|       +---> uris.size == 1 ---> Launch GpxImportActivity    |
|       |                         (EditRouteScreen)           |
|       |                                                     |
|       +---> uris.size > 1  ---> RoutesViewModel.importRoutesBatch(uris)
|                                      |
|                                      v
|                           [Batch Progress Dialog]           |
|                                      |
|                                      v
|                           [Summary Snackbar]                |
+-------------------------------------------------------------+
                                       |
                                       v
                     +-----------------------------------+
                     |          RoutesViewModel          |
                     |  - _batchImportProgress           |
                     |  - _batchImportResult             |
                     +-----------------------------------+
                                       |
                                       | loops over URIs
                                       v
        +-------------------------------------------------------+
        |                 GpxRouteImporter                      |
        |  - importRouteFromGpx(uri)                            |
        |    -> GpxTrackpointParser / TcxCourseParser           |
        |    -> enrichElevations (DEM Open-Meteo)               |
        |    -> parse waypoints & calculate distances           |
        +-------------------------------------------------------+
                                       |
                                       v
        +-------------------------------------------------------+
        |                 RoutesRepository                      |
        |  - insertRoute(summary, pathPoints, waypoints)        |
        |    -> RoutesDatabaseManager.insertRoute(...)          |
        |    -> ClimbDetector.detectClimbs(...)                 |
        |    -> WorkoutClusterEngine.learnFromRoute(...)        |
        |    -> refreshRoutes()                                 |
        +-------------------------------------------------------+
```

---

## 6. Living Documentation Sync & Traceability Matrix

### Requirements (`docs/requirements.md`)
- **`REQ-MAP-053`**: Multi-File Route Selection, Batch Import & External Multi-Share Processing.

### Tests (`docs/tests.md`)
- **`TST-MAP-055`**: Multi-File Route Selection, Batch Import & External Multi-Share Verification Suite.

---

## 7. Verification & Quality Assurance Strategy

1. **Unit Tests (`RoutesViewModelTest.kt`)**:
   - Single vs. multiple URI branch routing.
   - Batch import with all successful files -> progress emissions and `BatchImportResult(importedCount = 3, failedCount = 0)`.
   - Batch import with partial failure (e.g. 1 corrupt file) -> `BatchImportResult(importedCount = 2, failedCount = 1)`.
   - Batch import with empty list -> no-op.
2. **Unit Tests (`GpxImportViewModelTest.kt`)**:
   - Multi-URI handling for `ACTION_SEND_MULTIPLE`.
   - Batch progress and success states.
3. **Localization Parity Test (`TranslationParityTest.kt`)**:
   - Verify all 4 batch route import string tokens across all 9 languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).
4. **Clean-Room Regression**:
   - Full test suite `./gradlew testDebugUnitTest` asserting 100% pass rate.
