# Stage 3: Implementation Plan - ATT-4284: [Verbesserung] Prompt for sport type selection (apply to all or individually) during batch GPX route import

**Ticket**: [ATT-4284](https://rainerblind.atlassian.net/browse/ATT-4284)  
**Sub-task**: [ATT-4347](https://rainerblind.atlassian.net/browse/ATT-4347) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-MAP-053` (`REQ-MAP-053.1`–`REQ-MAP-053.7`)  
**Test Mapping**: `TST-MAP-056` (`TST-MAP-056.1`–`TST-MAP-056.7`)  
**Branch**: `feature/ATT-4284`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

In Sprint `2026-42.10`, ticket `ATT-4049` introduced multi-file GPX route picking and batch import (`OpenMultipleDocuments` / `GetMultipleContents()`) to allow athletes to import entire collections of GPX route files in a single action (`RoutesScreen.kt` and `GpxImportActivity.kt`).

However, standard GPX 1.1 XML track files rarely contain a standardized activity or sport type tag (or third-party export utilities omit it). In `GpxRouteImporter.kt` (line 151) and `TcxCourseParser.kt` (line 105), newly parsed route summaries are assigned `bSportType = BSportType.UNKNOWN`. Consequently, during headless batch imports:
1. Every imported route is written to `Routes.db` as `BSportType.UNKNOWN` (rendered under the "Other" / "Sonstige" tab).
2. Athletes must manually navigate to the "Other" tab, tap the edit icon on each imported route card individually in `RoutesScreen`, open `EditRouteScreen`, change the sport type to Bike (or Run), and save each route one by one.
3. In Sprint Review `2026-42.10` (`ATT-4100.md`, Section 1.2), this was flagged by Rainer Blind as a major friction point during batch import of route packages (e.g. 10 cycling holiday stages).

### Objective
Provide a clean, standardized, non-intrusive modal prompt (`BatchImportSportTypeDialog`) upon multi-file selection across both in-app multi-file import (`RoutesScreen`) and external multi-file share (`GpxImportActivity`):
1. **Apply to all**: The athlete chooses a target sport type (`Bike`, `Run`, or `Other`) via visual chips, applying it directly to all routes in a fast batch operation.
2. **Configure individually**: The athlete steps through each imported route sequentially in `EditRouteScreen`, reviewing and customizing route attributes (name, sport type, description) with progress indicator ("Route X of N") and skip affordance before saving.
3. **Single-File Import Invariant**: Single-file import (`uris.size == 1`) remains entirely unaffected, opening `EditRouteScreen` directly as before.

---

## 2. Traceability & Requirements Mapping

* **Primary Requirement**: `REQ-MAP-053` (*Multi-File Route Selection, Batch Import, Sport Type Selection Prompt & External Multi-Share Processing*)
  * `REQ-MAP-053.1` (Multi-File Document Picker Contract): Instantiate `rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents())` accepting `*/*`.
  * `REQ-MAP-053.2` (Single vs. Batch Route Import Flow Discrimination & Sport Type Prompt): `uris.size == 1` launches `GpxImportActivity` directly; `uris.size > 1` presents `BatchImportSportTypeDialog` offering "Apply to all" (`BatchSportMode.ALL`) and "Configure individually" (`BatchSportMode.INDIVIDUAL`).
  * `REQ-MAP-053.3` (Batch Import Execution & Target Sport Type in `RoutesViewModel`): Expose `fun importRoutesBatch(uris: List<Uri>, sportType: BSportType = BSportType.UNKNOWN)` on `Dispatchers.IO`, overriding `summary.bSportType = sportType` when `sportType != BSportType.UNKNOWN`.
  * `REQ-MAP-053.4` (Modal Progress Dialog & Summary Snackbar in `RoutesScreen`): Render non-dismissible modal `Dialog` with `CircularProgressIndicator` during batch import and localized `Snackbar` upon completion.
  * `REQ-MAP-053.5` (External Multi-File Share & Sequential Review in `GpxImportActivity`): Support `ACTION_SEND_MULTIPLE`, handle `EXTRA_INDIVIDUAL_MODE`, prompt with `BatchImportSportTypeDialog`, and coordinate sequential interactive editing in `GpxImportViewModel.startIndividualImport(uris)`.
  * `REQ-MAP-053.6` (Batch Import Sport Type Dialog Component Architecture): Material 3 `AlertDialog` with `SingleChoiceSegmentedButtonRow` mode switcher, `FilterChip` sport type selectors, explanatory hint for individual mode, and confirm/cancel actions.
  * `REQ-MAP-053.7` (9-Language Localization & Positional Specifier Parity): 8 string resource keys with indexed positional specifiers (`%1$d`, `%2$d`) across all 9 canonical locales.

* **Test Mapping**: `TST-MAP-056` (*Batch GPX Route Import Sport Type Selection & Sequential Review Verification Suite*)
  * `TST-MAP-056.1`: `RoutesViewModel` Batch Import with Sport Type Override Unit Test (`RoutesViewModelTest.kt`) $\implies$ Maps to `REQ-MAP-053.3`
  * `TST-MAP-056.2`: `GpxImportViewModel` Batch Handling with Sport Type Override Unit Test (`GpxImportViewModelTest.kt`) $\implies$ Maps to `REQ-MAP-053.5`
  * `TST-MAP-056.3`: `GpxImportViewModel` Sequential Individual Import Flow Unit Test (`GpxImportViewModelTest.kt`) $\implies$ Maps to `REQ-MAP-053.5`
  * `TST-MAP-056.4`: `BatchImportSportTypeDialog` Component Layout & Interaction Test (`BatchImportSportTypeDialogTest.kt`) $\implies$ Maps to `REQ-MAP-053.6`
  * `TST-MAP-056.5`: `RoutesScreen` Multi-File Flow Discrimination Contract Test (`RoutesScreenContractTest.kt`) $\implies$ Maps to `REQ-MAP-053.2`
  * `TST-MAP-056.6`: 9-Language Localization & Format Specifier Parity Test (`TranslationParityTest.kt`) $\implies$ Maps to `REQ-MAP-053.7`, `REQ-UI-106`
  * `TST-MAP-056.7`: Clean-Room Regression Suite Execution (`./gradlew testDebugUnitTest`) $\implies$ Maps to `REQ-PRO-001`, `REQ-MAP-053`

---

## 3. System Invariants & Preserved Behavior

1. **Single-File Direct Preview Invariant**:
   Single-file GPX import (`uris.size == 1`) strictly bypasses `BatchImportSportTypeDialog` and continues to open `EditRouteScreen` directly, preserving the fast single-track workflow.
2. **Backward-Compatible Default Parameter Affinity**:
   `RoutesViewModel.importRoutesBatch(uris: List<Uri>, sportType: BSportType = BSportType.UNKNOWN)` and `GpxImportViewModel.handleBatchUris(uris: List<Uri>, sportType: BSportType = BSportType.UNKNOWN)` maintain default parameter `BSportType.UNKNOWN`. All existing callers and legacy test suites continue to compile and pass without modification.
3. **Modal Overlay Composition Retention (Rule 47 / ATT-4285)**:
   In `RoutesScreen.kt`, `BatchImportSportTypeDialog` is composed as an overlay alongside `RouteTabbedScreen` conditioned on `pendingBatchUris != null`. `RouteTabbedScreen` is never unmounted, preserving tab selection, scroll state, and app bar offsets upon dialog dismissal.
4. **Non-Blocking Asynchronous I/O & Thread Safety**:
   All GPX parsing, XML decoding, and DEM altitude enrichment execute strictly on `Dispatchers.IO`. The UI remains fully responsive with non-dismissible modal progress feedback.
5. **Resilience & Fault Isolation**:
   Corrupt, unreadable, or malformed GPX files do not abort the import of remaining valid routes in either "Apply to all" or "Configure individually" mode. Failed files increment failure counters and report partial success transparently.
6. **Strict Code Lock in Stage 3**:
   Zero production code files in `app/src/...` are created or modified during Stage 3 planning. Construction occurs strictly in Stage 4 after Gate 3 sign-off.
7. **Single Atomic Commit per Ticket (Rule 44)**:
   Stage 3 and Stage 4 changes remain uncommitted on branch `feature/ATT-4284` until the single atomic squash commit at Stage 5.
8. **Subtask Direct Completion & Synchronous Parent Stage Progression (Rule 3 / Rule 45)**:
   Subtask `ATT-4347` transitions autonomously to `Erledigt` via transition `freigabe` upon passing Gate 3 audit, synchronously advancing parent `ATT-4284` to `Implementation`.
9. **Parent Human Gate Invariance (Rule 3)**:
   Moving parent ticket `ATT-4284` to `Erledigt` is reserved exclusively for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ UI LAYER (Jetpack Compose)                                                             │
│                                                                                        │
│  ┌─────────────────────────┐          ┌─────────────────────────┐                      │
│  │     RoutesScreen        │          │   GpxImportActivity     │                      │
│  │ (Multi-File Launcher)   │          │ (ACTION_SEND_MULTIPLE)  │                      │
│  └────────────┬────────────┘          └────────────┬────────────┘                      │
│               │ (uris.size > 1)                    │ (uris.size > 1)                   │
│               ▼                                    ▼                                   │
│  ┌──────────────────────────────────────────────────────────────┐                      │
│  │              BatchImportSportTypeDialog                      │                      │
│  │  - SingleChoiceSegmentedButtonRow: All vs. Individual        │                      │
│  │  - FilterChip Row: Bike / Run / Other                        │                      │
│  │  - Mode actions: "Import" vs. "Continue"                     │                      │
│  └────────────┬────────────────────────────────────┬────────────┘                      │
│               │ (Apply to all)                     │ (Individually)                    │
│               ▼                                    ▼                                   │
│  ┌─────────────────────────┐          ┌─────────────────────────┐                      │
│  │     RoutesViewModel     │          │   GpxImportViewModel    │                      │
│  │  .importRoutesBatch(    │          │  .startIndividualImport │                      │
│  │     uris, sportType)    │          │     queue state machine │                      │
│  └────────────┬────────────┘          └────────────┬────────────┘                      │
│               │                                    │                                   │
│               │                                    ▼                                   │
│               │                       ┌─────────────────────────┐                      │
│               │                       │     EditRouteScreen     │                      │
│               │                       │  (Title: Route i of N)  │                      │
│               │                       │  (Save / Skip actions)  │                      │
│               │                                    │                                   │
│               ▼                                    ▼                                   │
│  ┌──────────────────────────────────────────────────────────────┐                      │
│  │ DATA & PARSING LAYER                                         │                      │
│  │  - GpxRouteImporter: parse & DEM elevation enrichment        │                      │
│  │  - RoutesRepository: insertRoute(summary, points, waypoints) │                      │
│  │  - Routes.db: SQLite persistence with target bSportType      │                      │
│  └──────────────────────────────────────────────────────────────┘                      │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### Component 1: `BatchImportSportTypeDialog.kt` (UI Presentation Layer)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialog.kt`
* **Package**: `com.atrainingtracker.trainingtracker.ui.routes`
* **Role**: Reusable Material 3 `AlertDialog` presenting the sport type choice for batch imports.
* **Public Interface & State**:
  ```kotlin
  enum class BatchSportMode {
      ALL,
      INDIVIDUAL
  }

  @Composable
  fun BatchImportSportTypeDialog(
      routeCount: Int,
      onConfirm: (mode: BatchSportMode, selectedSport: BSportType) -> Unit,
      onDismiss: () -> Unit,
      modifier: Modifier = Modifier
  )
  ```
* **Internal Structure**:
  - `title`: `Text(stringResource(R.string.batch_import_dialog_title_format, routeCount))`
  - `text`:
    - `SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth())` with 2 segments:
      - Index 0: `SegmentedButton` with `R.string.batch_import_mode_all`, `shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)`, `maxLines = 1`, `softWrap = false`.
      - Index 1: `SegmentedButton` with `R.string.batch_import_mode_individual`, `shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)`, `maxLines = 1`, `softWrap = false`.
    - If `mode == BatchSportMode.ALL`:
      - Category title: `Text(stringResource(R.string.route_sport_type), style = MaterialTheme.typography.titleSmall)`
      - `Row` with 8.dp horizontal spacing rendering `FilterChip`s for `BSportType.BIKE` (default), `BSportType.RUN`, and `BSportType.UNKNOWN` (Other), each with leading icon (`painterResource(id = sport.iconResId)`) and localized label (`stringResource(id = sport.stringResId)`).
    - If `mode == BatchSportMode.INDIVIDUAL`:
      - Explanatory body text: `Text(stringResource(R.string.batch_import_individual_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)`.
  - `confirmButton`:
    - `Button(onClick = { onConfirm(selectedMode, selectedSport) }) { Text(stringResource(if (selectedMode == BatchSportMode.ALL) R.string.batch_import_action_import else R.string.batch_import_action_continue)) }`.
  - `dismissButton`:
    - `TextButton(onClick = onDismiss) { Text(stringResource(R.string.Cancel)) }`.
  - Comprehensive `@Preview` composables for all modes and themes (Light, Dark, AMOLED).

### Component 2: `RoutesScreen.kt` & `RoutesViewModel.kt` (In-App Batch Flow)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesViewModel.kt`
* **Changes in `RoutesScreen.kt`**:
  - Add state: `var pendingBatchUris by rememberSaveable { mutableStateOf<ArrayList<Uri>?>(null) }`.
  - In `gpxPickerLauncher`:
    - `uris.isEmpty() -> return`
    - `uris.size == 1 -> launch GpxImportActivity with data = uris.first()`
    - `uris.size > 1 -> pendingBatchUris = ArrayList(uris)`
  - In Compose body (alongside `RouteTabbedScreen`, adhering to Rule 47 modal overlay retention):
    ```kotlin
    pendingBatchUris?.let { uris ->
        BatchImportSportTypeDialog(
            routeCount = uris.size,
            onConfirm = { mode, selectedSport ->
                when (mode) {
                    BatchSportMode.ALL -> {
                        viewModel.importRoutesBatch(uris, selectedSport)
                    }
                    BatchSportMode.INDIVIDUAL -> {
                        val intent = Intent(context, GpxImportActivity::class.java).apply {
                            action = Intent.ACTION_SEND_MULTIPLE
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                            putExtra(GpxImportActivity.EXTRA_INDIVIDUAL_MODE, true)
                        }
                        context.startActivity(intent)
                    }
                }
                pendingBatchUris = null
            },
            onDismiss = {
                pendingBatchUris = null
            }
        )
    }
    ```
* **Changes in `RoutesViewModel.kt`**:
  - Update method signature:
    `fun importRoutesBatch(uris: List<Uri>, sportType: BSportType = BSportType.UNKNOWN)`
  - When inserting routes:
    ```kotlin
    val targetSummary = if (sportType != BSportType.UNKNOWN) {
        data.summary.copy(bSportType = sportType)
    } else {
        data.summary
    }
    routesRepository.insertRoute(targetSummary, data.pathPoints, data.waypoints)
    ```

### Component 3: `GpxImportActivity.kt` & `GpxImportViewModel.kt` (External Share & Sequential Review Flow)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/activities/GpxImportActivity.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/GpxImportViewModel.kt`
* **Changes in `GpxImportActivity.kt`**:
  - Companion constant: `const val EXTRA_INDIVIDUAL_MODE = "com.atrainingtracker.extra.INDIVIDUAL_MODE"`.
  - In `onCreate`:
    ```kotlin
    val isIndividualMode = intent.getBooleanExtra(EXTRA_INDIVIDUAL_MODE, false)
    if (uris.size > 1) {
        if (isIndividualMode) {
            viewModel.startIndividualImport(uris)
        } else {
            viewModel.promptSportType(uris)
        }
    } else {
        viewModel.handleIntent(uris.firstOrNull())
    }
    ```
  - In `setContent`:
    - Handle `is GpxImportViewModel.ImportState.PromptSportType -> BatchImportSportTypeDialog(...)`.
    - Handle `is GpxImportViewModel.ImportState.EditingIndividual -> EditRouteScreen(...)`.
* **Changes in `GpxImportViewModel.kt`**:
  - State extensions in `ImportState`:
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
  - State machine methods:
    - `fun promptSportType(uris: List<Uri>) { uiState = ImportState.PromptSportType(uris) }`
    - `fun handleBatchUris(uris: List<Uri>, sportType: BSportType = BSportType.UNKNOWN)`: overrides `summary.bSportType = sportType`.
    - `fun startIndividualImport(uris: List<Uri>)`: initializes queue and loads first route.
    - `fun saveCurrentIndividualRoute(updatedSummary: RouteSummary)`: saves to repository, advances index.
    - `fun skipCurrentIndividualRoute()`: increments failed/skipped tally, advances index.
    - Helper `private fun loadNextIndividualRoute()`: parses URI $i$, enriches DEM elevation, transitions to `EditingIndividual` or `BatchSuccess` upon completion.

### Component 4: `EditRouteScreen.kt` (Header & Skip Action Enhancement)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/EditRouteScreen.kt`
* **Changes**:
  - Add optional parameters:
    ```kotlin
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun EditRouteScreen(
        routeSummary: RouteSummary,
        onSave: (RouteSummary) -> Unit,
        onCancel: () -> Unit,
        title: String? = null,
        onSkip: (() -> Unit)? = null
    )
    ```
  - `title = title ?: stringResource(R.string.route_edit)`
  - When `onSkip != null`, action row renders Cancel, Skip (`R.string.batch_import_skip_route`), and Save buttons with clean horizontal spacing. When `onSkip == null`, uses existing `AppDialogActions.SaveCancel`.

### Component 5: 9-Language Localization Tables
* **Files**: `app/src/main/res/values*/strings.xml` (all 9 canonical locales)
* Keys:
  - `batch_import_dialog_title_format`: `%1$d` route count
  - `batch_import_mode_all`: "Apply to all" / "Auf alle anwenden"
  - `batch_import_mode_individual`: "Configure individually" / "Individuell anpassen"
  - `batch_import_individual_hint`: Explanatory hint for sequential review
  - `batch_import_action_import`: "Import" / "Importieren"
  - `batch_import_action_continue`: "Continue" / "Weiter"
  - `batch_import_individual_progress_format`: `Route %1$d of %2$d`
  - `batch_import_skip_route`: "Skip" / "Überspringen"

### UI Consistency (Rule 23 — Mandatory Section)
Per `docs/design_guidelines.md` (Section 5):
* **Reference screen / component**:
  - `EditRouteScreen.kt` (for sport type chip row styling, colors, and layout)
  - `DeleteConfirmationDialog.kt` and Material 3 `AlertDialog` patterns in `ui/components/core/`
* **Reused components**:
  - Material 3 `SingleChoiceSegmentedButtonRow` and `SegmentedButton` with `SegmentedButtonDefaults.itemShape` (Design Guidelines Section 1.1)
  - Material 3 `FilterChip` with leading `Icon` for `BSportType` selection matching `EditRouteScreen`
  - `AppModalBottomSheet` and `AppDialogActions` in `EditRouteScreen`
* **Theme tokens**:
  - Shapes: `MaterialTheme.shapes.extraLarge` for dialog surface, `SegmentedButtonDefaults.itemShape(index, count)` for mode toggle pills, `8.dp` for sport filter chips.
  - Spacing: `8.dp`, `16.dp`, `24.dp` (adhering strictly to Design Guidelines Section 5.2).
  - Colors: Surfaces and text from `MaterialTheme.colorScheme` (`surface`, `onSurface`, `onSurfaceVariant`, `primary`). Domain sport icons and strings from canonical `BSportType` properties (`bSportType.iconResId`, `bSportType.stringResId`).
* **New one-off styles & justification**: None. All components strictly adhere to standard Material 3 design tokens and existing app styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Language String Resources & Positional Format Specifiers
* **Files**:
  - `app/src/main/res/values/strings.xml` (EN)
  - `app/src/main/res/values-de/strings.xml` (DE)
  - `app/src/main/res/values-es/strings.xml` (ES)
  - `app/src/main/res/values-fr/strings.xml` (FR)
  - `app/src/main/res/values-it/strings.xml` (IT)
  - `app/src/main/res/values-ja/strings.xml` (JA)
  - `app/src/main/res/values-nl/strings.xml` (NL)
  - `app/src/main/res/values-pl/strings.xml` (PL)
  - `app/src/main/res/values-pt/strings.xml` (PT)
* **Changes**: Add the 8 canonical localized string keys from Section 4 / `ATT-4284_test_spec.md` with indexed positional format specifiers (`%1$d`, `%2$d`).
* **Targeted Verification**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

### Step 2: Implement `BatchImportSportTypeDialog.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialog.kt`
* **Changes**: Implement `enum class BatchSportMode`, `BatchImportSportTypeDialog` composable with `SingleChoiceSegmentedButtonRow`, `FilterChip` sport selector, and multi-state `@Preview` composables.
* **Targeted Verification**:
  `./gradlew assembleDebug`

### Step 3: Enhance `EditRouteScreen.kt` for Sequential Review Mode
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/EditRouteScreen.kt`
* **Changes**: Add optional `title: String? = null` and `onSkip: (() -> Unit)? = null` parameters, rendering custom header title and skip action button during individual batch review while preserving default single-file edit behavior.
* **Targeted Verification**:
  `./gradlew assembleDebug`

### Step 4: Update `RoutesViewModel.kt` & Add Unit Tests
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesViewModel.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesViewModelTest.kt`
* **Changes**:
  - Add `sportType: BSportType = BSportType.UNKNOWN` parameter to `importRoutesBatch`.
  - Override `summary.bSportType` when `sportType != BSportType.UNKNOWN`.
  - Implement unit test `testBatchImport_withSportTypeOverride_TST_MAP_056_1` asserting `summary.bSportType` override and default fallback.
* **Targeted Verification**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RoutesViewModelTest"`

### Step 5: Update `GpxImportViewModel.kt` & Add Unit Tests
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/GpxImportViewModel.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/GpxImportViewModelTest.kt`
* **Changes**:
  - Add `PromptSportType` and `EditingIndividual` to `ImportState`.
  - Add `sportType: BSportType = BSportType.UNKNOWN` to `handleBatchUris`.
  - Implement `startIndividualImport(uris: List<Uri>)`, `saveCurrentIndividualRoute(summary: RouteSummary)`, and `skipCurrentIndividualRoute()`.
  - Implement unit tests:
    - `testHandleBatchUris_withSportTypeOverride_TST_MAP_056_2`
    - `testStartIndividualImport_sequentialReviewFlow_TST_MAP_056_3`
* **Targeted Verification**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.GpxImportViewModelTest"`

### Step 6: Update `GpxImportActivity.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/GpxImportActivity.kt`
* **Changes**:
  - Declare `EXTRA_INDIVIDUAL_MODE`.
  - Dispatch multi-file intents to `startIndividualImport` (when `EXTRA_INDIVIDUAL_MODE == true`) or `promptSportType`.
  - Render `BatchImportSportTypeDialog` in `ImportState.PromptSportType`.
  - Render `EditRouteScreen` with title and skip actions in `ImportState.EditingIndividual`.
* **Targeted Verification**:
  `./gradlew assembleDebug`

### Step 7: Update `RoutesScreen.kt` Document Picker Integration
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreen.kt`
* **Changes**:
  - Introduce `var pendingBatchUris by rememberSaveable { mutableStateOf<ArrayList<Uri>?>(null) }`.
  - Intercept `uris.size > 1` in `gpxPickerLauncher` to store pending URIs.
  - Render `BatchImportSportTypeDialog` overlay alongside `RouteTabbedScreen` (Rule 47 modal overlay retention).
  - Dispatch confirmed mode to `viewModel.importRoutesBatch(uris, sportType)` or `GpxImportActivity` with `EXTRA_INDIVIDUAL_MODE`.
* **Targeted Verification**:
  `./gradlew assembleDebug`

### Step 8: Component & Contract Unit Tests
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialogTest.kt` (`TST-MAP-056.4`)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreenContractTest.kt` (`TST-MAP-056.5`)
* **Changes**: Add unit/contract tests verifying dialog component layout, mode switcher callbacks, and `RoutesScreen` launcher discrimination logic.
* **Targeted Verification**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.*"`

### Step 9: Stage 4 Targeted Unit Test Suite Verification
* **Targeted Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
  ```
* **Assertion**: 100% pass across all route management and localization unit tests (~15 seconds).

---

## 6. Verification & Rollback Plan

### Verification Strategy
1. **Targeted Unit Testing (Stage 4 Construction)**:
   - `RoutesViewModelTest.kt`: Verify batch import with sport type override (`TST-MAP-056.1`).
   - `GpxImportViewModelTest.kt`: Verify batch handling with sport type override (`TST-MAP-056.2`) and sequential review queue lifecycle (`TST-MAP-056.3`).
   - `BatchImportSportTypeDialogTest.kt`: Verify dialog mode switching, sport selection chips, and callbacks (`TST-MAP-056.4`).
   - `RoutesScreenContractTest.kt`: Verify single-file vs. multi-file discrimination contract (`TST-MAP-056.5`).
   - `TranslationParityTest.kt`: Verify 9-language translation and format specifier parity (`TST-MAP-056.6`).
2. **Clean-Room Regression Suite (Stage 5 Verification)**:
   - Full suite execution via `./gradlew testDebugUnitTest` verifying 0 regressions across all 430+ unit test classes.
3. **On-Device Physical Verification (Google Pixel 10)**:
   - Multi-file in-app import in `RoutesScreen`: Select 3 GPX route files -> Choose "Apply to all" with "Bike" -> Verify all 3 routes appear under "Bike" tab.
   - Multi-file in-app import in `RoutesScreen`: Select 2 GPX route files -> Choose "Configure individually" -> Verify Route 1 opens with "Route 1 of 2", save -> Verify Route 2 opens with "Route 2 of 2", save -> Verify routes appear under their respective selected tabs.
   - External share: Share 3 GPX files from file manager -> Choose "Apply to all" with "Run" -> Verify routes imported under "Run".

### Rollback Strategy
* All changes are isolated on git branch `feature/ATT-4284`.
* In the event of an unresolvable defect or regression, the branch can be abandoned or reset via `git checkout develop && git branch -D feature/ATT-4284` with zero impact on production code.
