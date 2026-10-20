# Stage 1 Analysis: ATT-4284 — Prompt for sport type selection (apply to all or individually) during batch GPX route import

**Ticket**: [ATT-4284](https://rainerblind.atlassian.net/browse/ATT-4284)  
**Sub-task**: [ATT-4345](https://rainerblind.atlassian.net/browse/ATT-4345) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Branch**: `feature/ATT-4284`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

### Current State
In sprint `2026-42.10`, ticket `ATT-4049` introduced multi-file GPX route import (`OpenMultipleDocuments` / `GetMultipleContents()`) to allow athletes to import collections of GPX route files in a single action (`RoutesScreen.kt` and `GpxImportActivity.kt`). 
However, standard GPX 1.1 XML track files rarely contain a standardized activity or sport type tag (or third-party export tools omit it). In `GpxRouteImporter.kt` (line 151) and `TcxCourseParser.kt` (line 105), newly parsed route summaries are assigned `bSportType = BSportType.UNKNOWN`. 
Consequently, during headless batch imports:
1. Every imported route is unconditionally written to `Routes.db` as `BSportType.UNKNOWN` (rendered under the "Other" / "Sonstige" tab).
2. Athletes must then manually navigate to the "Other" tab, tap the edit icon on every imported route card individually in `RoutesScreen`, open `EditRouteScreen`, change the sport type to Bike (or Run), and save each route one by one.
3. In Sprint Review `2026-42.10` (`ATT-4100.md`), this was flagged as a major friction point during batch import of route packages (e.g. 10 cycling holiday stages).

### Expected State
When multiple GPX/TCX files are selected for batch import (either via the in-app multi-file picker in `RoutesScreen` or through external multi-file share in `GpxImportActivity`):
1. The app prompts the athlete before/during import with a clean, standardized dialog offering two choices:
   - **Apply to all routes**: The athlete chooses a target sport type (`Bike`, `Run`, or `Other`) via visual chips/selectors, and all routes in the batch are imported directly with that sport type.
   - **Configure individually**: The athlete steps through each imported route sequentially in `EditRouteScreen`, allowing custom name, sport type, and description editing for each route before saving.
2. The interaction is completely consistent across both in-app multi-file import (`RoutesScreen`) and external multi-file share (`GpxImportActivity`).
3. Single-file import (`uris.size == 1`) remains entirely unaffected, opening `EditRouteScreen` directly as before.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 File Picker & Import Entry Points
In `RoutesScreen.kt` (lines 82–92):
```kotlin
val gpxPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
    if (uris.isEmpty()) return@rememberLauncherForActivityResult
    if (uris.size == 1) {
        val intent = Intent(context, GpxImportActivity::class.java).apply {
            data = uris.first()
        }
        context.startActivity(intent)
    } else {
        viewModel.importRoutesBatch(uris)
    }
}
```
When `uris.size > 1`, `RoutesScreen` immediately triggers `viewModel.importRoutesBatch(uris)` without providing any opportunity to configure the route attributes or specify the intended sport type.

In `GpxImportActivity.kt` (lines 56–70):
```kotlin
val uris: List<android.net.Uri> = when (intent.action) {
    android.content.Intent.ACTION_SEND_MULTIPLE -> {
        intent.getParcelableArrayListExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)?.filterNotNull() ?: emptyList()
    }
    else -> {
        val single = intent.data ?: intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
        listOfNotNull(single)
    }
}

if (uris.size > 1) {
    viewModel.handleBatchUris(uris)
} else {
    viewModel.handleIntent(uris.firstOrNull())
}
```
When external apps share multiple files via `ACTION_SEND_MULTIPLE`, `GpxImportActivity` immediately triggers `viewModel.handleBatchUris(uris)` without prompting.

### 2.2 Route Parsers & Default Sport Assignment
In `GpxRouteImporter.kt` (lines 142–153):
```kotlin
val summary = RouteSummary(
    id = 0,
    externalId = uri.lastPathSegment ?: "unknown",
    name = parsedGpx.trackName ?: parsedGpx.name ?: "Imported Route",
    description = parsedGpx.trackDesc ?: parsedGpx.description ?: "",
    isSelected = false,
    distance = totalDist,
    elevationGain = calculateElevationGain(pathPoints),
    bSportType = BSportType.UNKNOWN,
    source = RouteSource.LOCAL_GPX
)
```
In `TcxCourseParser.kt` (lines 97–107):
```kotlin
val summary = RouteSummary(
    id = 0,
    externalId = defaultExternalId,
    name = if (courseName.isNotBlank()) courseName else "TCX Course",
    description = data.notes ?: "",
    isSelected = false,
    distance = trackPoints.last().distance,
    elevationGain = calculateElevationGain(trackPoints),
    bSportType = BSportType.UNKNOWN,
    source = RouteSource.LOCAL_GPX
)
```
Neither GPX nor TCX course files provide an authoritative `BSportType` mapping during parsing. Thus, every batch-imported route is given `BSportType.UNKNOWN`.

### 2.3 Headless Persistence in ViewModels
In `RoutesViewModel.kt` (lines 277–284):
```kotlin
val result = gpxRouteImporter.importRouteFromGpx(uri)
if (result.isSuccess) {
    val data = result.getOrNull()
    if (data != null) {
        routesRepository.insertRoute(data.summary, data.pathPoints, data.waypoints)
        imported++
    }
}
```
In `GpxImportViewModel.kt` (lines 76–84):
```kotlin
val result = importer.importRouteFromGpx(uri)
if (result.isSuccess) {
    val data = result.getOrNull()
    if (data != null) {
        repository.insertRoute(data.summary, data.pathPoints, data.waypoints)
        imported++
    }
}
```
Both `RoutesViewModel.importRoutesBatch` and `GpxImportViewModel.handleBatchUris` persist `data.summary` verbatim into the repository without an optional `sportType` override or any intermediate prompt state.

---

## 3. User Scope Grounding (ATT-1250)

### 3.1 In-Scope Goals
1. **Batch Sport Selection Prompt Dialog (`BatchImportSportTypeDialog`)**:
   - Create a reusable Jetpack Compose dialog component for sport type selection during batch import.
   - Follow UI Design Guidelines Section 1.1: Use Material 3 `SingleChoiceSegmentedButtonRow` for the binary choice between **Apply to all** (`BatchSportMode.ALL`) and **Configure individually** (`BatchSportMode.INDIVIDUAL`).
   - Under "Apply to all", display sport option pills (`FilterChip` with icon & label for `BSportType.BIKE`, `BSportType.RUN`, `BSportType.UNKNOWN` / Other) with default selection `BSportType.BIKE`.
   - Under "Configure individually", display clear hint text explaining that each route will be opened sequentially for review before saving.
   - Include "Import" / "Continue" and "Cancel" actions.
2. **In-App Batch Import Flow in `RoutesScreen`**:
   - When `uris.size > 1`, store `pendingBatchUris` and display `BatchImportSportTypeDialog`.
   - If athlete selects "Apply to all", invoke `viewModel.importRoutesBatch(uris, selectedSport)`.
   - If athlete selects "Configure individually", launch `GpxImportActivity` passing `uris` with `EXTRA_INDIVIDUAL_MODE = true`.
   - If athlete cancels, dismiss dialog and clear `pendingBatchUris`.
3. **External Share Flow in `GpxImportActivity`**:
   - When `uris.size > 1` (from `ACTION_SEND_MULTIPLE`), transition to `ImportState.PromptSportType(uris)` displaying `BatchImportSportTypeDialog`.
   - If athlete selects "Apply to all", execute `viewModel.handleBatchUris(uris, selectedSport)`.
   - If athlete selects "Configure individually" (or if launched with `EXTRA_INDIVIDUAL_MODE = true`), execute sequential interactive editing through `startIndividualImport(uris)`.
4. **Sequential Individual Editing Architecture in `GpxImportViewModel`**:
   - Add state machine managing a queue of URIs: parses route $i$, enriches DEM elevation, transitions to `ImportState.EditingIndividual(summary, points, waypoints, current = i + 1, total = N)`.
   - On save: inserts route into `RoutesRepository`, advances to route $i + 1$.
   - On cancel/skip: skips current route without saving, advances to route $i + 1$.
   - Upon completion of all $N$ items: emits `ImportState.BatchSuccess(savedCount, skippedCount)` and finishes.
5. **ViewModel & Repository Parameterization**:
   - Update `RoutesViewModel.importRoutesBatch(uris: List<Uri>, sportType: BSportType = BSportType.UNKNOWN)`.
   - Update `GpxImportViewModel.handleBatchUris(uris: List<Uri>, sportType: BSportType = BSportType.UNKNOWN)`.
   - Ensure complete backward compatibility with existing unit test signatures.
6. **9-Language Localization Parity**:
   - Add localized string resources for dialog title, mode switcher, sport labels, individual mode hints, and actions across all 9 supported languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 3.2 Out-of-Scope Non-Goals (Scope Bounding)
1. **Single-File Import Flow**:
   - Single-file import (`uris.size == 1`) already allows full editing via `EditRouteScreen`. Prompting for sport type on a single file would be redundant and is strictly out of scope.
2. **GPX XML Schema Extensions / Automated Heuristics**:
   - Inferring sport type from speed/cadence/elevation profiles or parsing non-standard vendor XML tags is out of scope. The athlete's explicit selection is the authoritative source.
3. **Bulk Edit of Already Imported Routes**:
   - Multi-select batch editing on the Routes screen list is a separate feature backlog candidate and is not part of the import flow.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  Refining `REQ-MAP-053` (*Multi-File Route Selection, Batch Import & External Multi-Share Processing*, specifically sub-clauses `REQ-MAP-053.2`, `REQ-MAP-053.3`, and `REQ-MAP-053.5`) and introducing `REQ-MAP-056` (*Prompt for Sport Type Selection During Batch GPX Route Import*). Targets:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesViewModel.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/activities/GpxImportActivity.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/GpxImportViewModel.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialog.kt`
  - `app/src/main/res/values*/strings.xml`
* **Historical Origin & Commit Trace**:
  - `ATT-4049` (commit `8c418181`, Sprint 2026-42.10) created `REQ-MAP-053` and introduced multi-file selection with headless batch import.
  - Sprint Review `2026-42.10` (`ATT-4100.md`, Section 1.2): Rainer Blind accepted `ATT-4049` but discovered during live on-device testing that all routes imported as `Other` (`BSportType.UNKNOWN`), forcing tedious manual one-by-one editing of every route. Follow-up ticket `ATT-4284` was immediately created at the top of the backlog to address this exact friction.
* **Root Reason for Existing Formulation**:
  `ATT-4049` designed batch import headlessly to prevent UI thrashing (avoiding popping $N$ modal edit activities consecutively). However, because GPX files lack sport metadata, routes defaulted to `UNKNOWN`. Adding an upfront modal prompt offering "Apply to all" or "Configure individually" provides athletes with the speed of headless import *plus* the exact sport categorization they need, while preserving sequential editing if they desire.
* **Preservation of Core Invariants**:
  1. Single-file interactive editing (`uris.size == 1` $\rightarrow$ `EditRouteScreen`) remains 100% intact.
  2. Asynchronous, fault-tolerant background parsing and DEM elevation enrichment (`GpxRouteImporter`) remain isolated per file with zero UI thread blocking.
  3. Default parameter `sportType: BSportType = BSportType.UNKNOWN` preserves backwards compatibility for all existing callers and unit tests.
  4. Clean-room unit test suite pass rate under `./gradlew testDebugUnitTest` remains 100%.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Reusable Component: `BatchImportSportTypeDialog`
A Material 3 `AlertDialog` implemented in `ui/routes/BatchImportSportTypeDialog.kt`:
```text
┌────────────────────────────────────────────────────────┐
│  Import 5 Routes                                       │
│                                                        │
│  ┌─────────────────────────┬─────────────────────────┐ │
│  │    Apply to all (●)     │    Individually ( )     │ │  <- SingleChoiceSegmentedButtonRow
│  └─────────────────────────┴─────────────────────────┘ │
│                                                        │
│  [When "Apply to all" is selected]:                    │
│  Sport Type:                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ 🚴 Bike (●)  │  │  🏃 Run ( )  │  │ ⚪ Other ( ) │  │  <- FilterChips with icons
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│                                                        │
│  [When "Individually" is selected]:                    │
│  Each route will be opened sequentially for review     │
│  and editing before saving.                            │
│                                                        │
│                           [ Cancel ]   [ Import / OK ] │
└────────────────────────────────────────────────────────┘
```
- **Mode Switcher**: `SingleChoiceSegmentedButtonRow` with two segments (`R.string.batch_import_mode_all` vs. `R.string.batch_import_mode_individual`), adhering strictly to Design Guidelines Section 1.1.
- **Sport Selector**: `Row` of `FilterChip` items for `BSportType.BIKE`, `BSportType.RUN`, and `BSportType.UNKNOWN` (with `bSportType.iconResId` and `bSportType.stringResId`), adhering to Design Guidelines Section 5.5 / Rule 23.
- **Confirmation Actions**: `TextButton`s for Cancel (`R.string.cancel`) and Confirm (`R.string.import_action` / `R.string.batch_import_action_continue`).

### 5.2 Flow Discrimination in `RoutesScreen`
1. When `uris.size > 1`:
   - Store `var pendingBatchUris by rememberSaveable { mutableStateOf<List<Uri>?>(null) }` (or URI string list for Parcelable saveability).
   - Render `BatchImportSportTypeDialog(urisCount = uris.size, onConfirm = { mode, sport -> ... }, onDismiss = { pendingBatchUris = null })`.
2. On Confirm:
   - If `mode == BatchSportMode.ALL`:
     `viewModel.importRoutesBatch(pendingBatchUris!!, sportType = sport)`
   - If `mode == BatchSportMode.INDIVIDUAL`:
     Launch `GpxImportActivity` with `ACTION_SEND_MULTIPLE`, `EXTRA_STREAM = ArrayList(pendingBatchUris!!)`, and `EXTRA_INDIVIDUAL_MODE = true`.
   - Clear `pendingBatchUris = null`.

### 5.3 Flow Discrimination in `GpxImportActivity` & `GpxImportViewModel`
1. `GpxImportViewModel` state machine enhancements:
   ```kotlin
   sealed class ImportState {
       object Loading : ImportState()
       data class PromptSportType(val uris: List<Uri>) : ImportState()
       data class Editing(val summary: RouteSummary, val points: List<PathPoint>, val waypoints: List<RouteWaypoint> = emptyList()) : ImportState()
       data class EditingIndividual(
           val summary: RouteSummary,
           val points: List<PathPoint>,
           val waypoints: List<RouteWaypoint> = emptyList(),
           val current: Int,
           val total: Int
       ) : ImportState()
       object Saving : ImportState()
       object Success : ImportState()
       data class BatchProgress(val current: Int, val total: Int) : ImportState()
       data class BatchSuccess(val importedCount: Int, val failedCount: Int) : ImportState()
       data class Error(val message: String) : ImportState()
   }
   ```
2. When `GpxImportActivity` receives multiple URIs:
   - If intent has `EXTRA_INDIVIDUAL_MODE == true`: immediately call `viewModel.startIndividualImport(uris)`.
   - Else: set `uiState = ImportState.PromptSportType(uris)` to show `BatchImportSportTypeDialog`.
3. In `startIndividualImport(uris)`:
   - Maintains `queue`, `currentIndex`, `savedCount`, `failedCount`.
   - Loads and enriches URI $i$.
   - Transitions to `ImportState.EditingIndividual(..., current = i + 1, total = N)`.
   - `onSave`: persists to repository, advances to next URI.
   - `onCancel`: skips current route, advances to next URI.
   - When queue exhausted: transitions to `ImportState.BatchSuccess(savedCount, failedCount)` -> Toast -> `finish()`.

---

## 6. System Invariants & Risk Assessment

### Core Invariants
1. **Single-File Import Invariant**: Single GPX/TCX file selection (`uris.size == 1`) continues to open `EditRouteScreen` directly without any intermediate prompt.
2. **Backward Compatibility**: `RoutesViewModel.importRoutesBatch(uris)` and `GpxImportViewModel.handleBatchUris(uris)` default to `sportType = BSportType.UNKNOWN`, preserving all existing caller contracts and unit tests.
3. **Asynchronous Non-Blocking Execution**: All file parsing, XML parsing, and DEM altitude lookups execute strictly on `Dispatchers.IO` with non-dismissible UI progress feedback.
4. **Resilience & Fault Tolerance**: Malformed or unreadable files do not abort the import of remaining valid routes in either "Apply to all" or "Individually" mode.
5. **Human Decision Gate**: Parent ticket `ATT-4284` Human Decision Gate remains strictly guarded; terminal state for the agent is `Final Review (Human)`.

### Risk Rating: **LOW**
- **Justification**:
  - Purely additive UI/UX enhancement on top of the established `ATT-4049` batch import engine.
  - No database schema migrations or DTO alterations (uses existing `RouteSummary.bSportType`).
  - Clean separation of concerns between `BatchImportSportTypeDialog` (presentation), `RoutesViewModel` / `GpxImportViewModel` (state machines), and `GpxRouteImporter` (I/O).
  - High unit test coverage already in place for both ViewModels, making contract verification rapid and deterministic.
