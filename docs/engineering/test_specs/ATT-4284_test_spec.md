# Stage 2: Requirement & Test Specification - ATT-4284: [Verbesserung] Prompt for sport type selection (apply to all or individually) during batch GPX route import

**Ticket**: [ATT-4284](https://rainerblind.atlassian.net/browse/ATT-4284)  
**Sub-task**: [ATT-4346](https://rainerblind.atlassian.net/browse/ATT-4346) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-MAP-053` (*Multi-File Route Selection, Batch Import, Sport Type Selection Prompt & External Multi-Share Processing*)  
**Test Spec ID**: `TST-MAP-056`  
**Branch**: `feature/ATT-4284`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-MAP-053)

### 1.1 Problem Statement & Rationale
In Sprint `2026-42.10`, ticket `ATT-4049` introduced multi-file GPX route picking and headless batch import (`OpenMultipleDocuments` / `GetMultipleContents()`). However, standard GPX 1.1 track files lack standardized activity or sport type metadata tags. Consequently, `GpxRouteImporter` assigns `BSportType.UNKNOWN` to every parsed route, causing all batch-imported routes to be written to `Routes.db` under the "Other" ("Sonstige") tab.

During Sprint Review `2026-42.10` (`ATT-4100.md`, Section 1.2), live on-device testing demonstrated that athletes importing holiday route collections (e.g., 10 cycling stages) were forced to manually locate each route in the "Other" tab, open `EditRouteScreen`, change the sport type to Bike, and save each route one by one.

This specification refines `REQ-MAP-053` to introduce an intuitive modal prompt (`BatchImportSportTypeDialog`) upon multi-file selection. Athletes can either:
1. **Apply to all**: Select a target sport type (`Bike`, `Run`, or `Other`) via visual chips, applying it directly to all imported routes in a fast batch operation.
2. **Configure individually**: Step through each imported route sequentially in `EditRouteScreen` to review and customize route attributes (name, sport type, description) with a progress indicator ("Route X of N") and skip affordance before saving.

### 1.2 Functional & Architectural Requirements
The system SHALL adhere to the following numbered requirement clauses:

1. **Multi-File Picker Contract (`REQ-MAP-053.1`)**:
   * `RoutesScreen.kt` SHALL instantiate `rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents())` accepting `*/*`.
   * When `uris.isEmpty()`, the system SHALL perform no operation.

2. **Single vs. Batch Route Import Flow Discrimination & Sport Type Prompt (`REQ-MAP-053.2`)**:
   * Single File (`uris.size == 1`): The system SHALL launch `GpxImportActivity` passing `intent.data = uris.first()`, presenting `EditRouteScreen` directly to allow inspecting and editing route attributes before saving.
   * Multiple Files (`uris.size > 1`): In `RoutesScreen.kt`, the system SHALL display `BatchImportSportTypeDialog` offering a choice between "Apply to all" (`BatchSportMode.ALL`) and "Configure individually" (`BatchSportMode.INDIVIDUAL`).
   * If "Apply to all" is confirmed, the system SHALL execute `RoutesViewModel.importRoutesBatch(uris, sportType)`.
   * If "Configure individually" is confirmed, the system SHALL launch `GpxImportActivity` with `ACTION_SEND_MULTIPLE`, passing `uris` and `EXTRA_INDIVIDUAL_MODE = true`.

3. **Batch Import Execution & Target Sport Type in `RoutesViewModel` (`REQ-MAP-053.3`)**:
   * `RoutesViewModel` SHALL expose `batchImportProgress: StateFlow<BatchImportProgress?>` (`val current: Int, val total: Int`), `batchImportResult: StateFlow<BatchImportResult?>` (`val importedCount: Int, val failedCount: Int, val totalCount: Int`), and `fun clearBatchImportResult()`.
   * `RoutesViewModel` SHALL expose `fun importRoutesBatch(uris: List<Uri>, sportType: BSportType = BSportType.UNKNOWN)` on `Dispatchers.IO`.
   * For each URI $i \in [1, N]$, the engine SHALL emit `BatchImportProgress(current = i, total = N)`, parse via `gpxRouteImporter.importRouteFromGpx(uri)`, override `summary.bSportType = sportType` (when `sportType != BSportType.UNKNOWN`), insert valid routes via `routesRepository.insertRoute(summary, pathPoints, waypoints)` incrementing `importedCount`, and isolate failures incrementing `failedCount` without aborting remaining files.
   * Upon completion, `_batchImportProgress.value` SHALL reset to `null` and `_batchImportResult.value` SHALL emit the final tally.

4. **Modal Progress Dialog & Summary Snackbar in `RoutesScreen` (`REQ-MAP-053.4`)**:
   * When `batchImportProgress != null`, `RoutesScreen` SHALL render a non-dismissible modal `Dialog` with `CircularProgressIndicator` and localized progress string `R.string.routes_import_progress` ("%1$d of %2$d").
   * When `batchImportResult != null`, `RoutesScreen` SHALL display a localized `Snackbar`: `R.string.routes_import_success` when `failedCount == 0`, `R.string.routes_import_partial` when `failedCount > 0 && importedCount > 0`, or `R.string.routes_import_all_failed` when `importedCount == 0`, and clear the result via `viewModel.clearBatchImportResult()`.

5. **External Multi-File Share & Sequential Review in `GpxImportActivity` (`REQ-MAP-053.5`)**:
   * `AndroidManifest.xml` SHALL declare intent filters for `android.intent.action.SEND` and `android.intent.action.SEND_MULTIPLE`.
   * `GpxImportActivity` and `GpxImportViewModel` SHALL extract all stream URIs: single URI branches directly to `EditRouteScreen`; multiple URIs without `EXTRA_INDIVIDUAL_MODE` SHALL prompt with `BatchImportSportTypeDialog`.
   * When "Apply to all" is selected, `GpxImportViewModel.handleBatchUris(uris, sportType)` SHALL execute headless batch import applying `sportType`.
   * When `EXTRA_INDIVIDUAL_MODE == true` or "Configure individually" is selected, `GpxImportViewModel.startIndividualImport(uris)` SHALL coordinate sequential interactive editing, loading each route into `ImportState.EditingIndividual(summary, points, waypoints, current = i + 1, total = N)`.
   * In individual mode, saving inserts the route into `routesRepository` and advances to route $i + 1$; skipping advances to route $i + 1$ without inserting; upon exhaustion, transitions to `ImportState.BatchSuccess(importedCount, failedCount)` and displays completion Toast.

6. **Batch Import Sport Type Dialog Component Architecture (`REQ-MAP-053.6`)**:
   * `BatchImportSportTypeDialog` (`ui/routes/BatchImportSportTypeDialog.kt`) SHALL render a Material 3 `AlertDialog` displaying dialog title (`R.string.batch_import_dialog_title_format`, "%1$d Routes").
   * Mode selection SHALL use `SingleChoiceSegmentedButtonRow` with two segments: "Apply to all" (`R.string.batch_import_mode_all`) and "Configure individually" (`R.string.batch_import_mode_individual`) per Design Guidelines Section 1.1.
   * When "Apply to all" is selected: SHALL render `Text(stringResource(R.string.route_sport_type))` and a row of `FilterChip` items for `BSportType.BIKE` (default), `BSportType.RUN`, and `BSportType.UNKNOWN` (Other) with corresponding icons and labels.
   * When "Configure individually" is selected: SHALL render localized explanatory hint `R.string.batch_import_individual_hint`.
   * Actions: Cancel (`R.string.cancel`) and Confirm (`R.string.batch_import_action_import` in "Apply to all" mode / `R.string.batch_import_action_continue` in "Configure individually" mode).

7. **9-Language Localization & Positional Specifier Parity (`REQ-MAP-053.7`)**:
   * String tokens `batch_import_dialog_title_format`, `batch_import_mode_all`, `batch_import_mode_individual`, `batch_import_individual_hint`, `batch_import_action_import`, `batch_import_action_continue`, `batch_import_individual_progress_format`, and `batch_import_skip_route`, together with existing tokens `routes_import_progress`, `routes_import_success`, `routes_import_partial`, `routes_import_all_failed`, `route_sport_type`, `sport_type_bike`, `sport_type_run`, and `sport_type_other`, SHALL exist across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with matching indexed positional format specifiers (`%1$d`, `%2$d`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Single File Invariant)**:
  * *Given* an athlete selecting exactly 1 GPX route file in `RoutesScreen`,
  * *When* the document picker returns,
  * *Then* `GpxImportActivity` opens `EditRouteScreen` directly without presenting `BatchImportSportTypeDialog`.
* **Criterion 2 (Multi-File Dialog Presentation)**:
  * *Given* an athlete selecting multiple GPX files ($N > 1$) in `RoutesScreen`,
  * *When* the document picker returns,
  * *Then* `BatchImportSportTypeDialog` is displayed showing title "Import N Routes", mode switcher defaulted to "Apply to all", and sport defaulted to `Bike`.
* **Criterion 3 (Apply to All Execution)**:
  * *Given* `BatchImportSportTypeDialog` open with "Apply to all" and `Bike` selected,
  * *When* the athlete taps "Import",
  * *Then* `RoutesViewModel.importRoutesBatch(uris, sportType = BSportType.BIKE)` executes on `Dispatchers.IO`, all valid routes are persisted to `Routes.db` with `bSportType == BSportType.BIKE`, progress modal is displayed during import, and success Snackbar is displayed upon completion.
* **Criterion 4 (Configure Individually Navigation)**:
  * *Given* `BatchImportSportTypeDialog` open,
  * *When* the athlete selects "Configure individually" and taps "Continue",
  * *Then* `GpxImportActivity` is launched with `EXTRA_INDIVIDUAL_MODE = true`, opening each route sequentially in `EditRouteScreen` with route index progress ("Route 1 of N"), allowing custom title, sport type, and notes editing per route.
* **Criterion 5 (Individual Sequential Save and Skip)**:
  * *Given* sequential editing active on route $i$ of $N$,
  * *When* the athlete taps "Save", route $i$ is inserted into `RoutesRepository` and route $i + 1$ is loaded; when the athlete taps "Skip", route $i$ is skipped without inserting and route $i + 1$ is loaded.
* **Criterion 6 (Dialog Cancellation)**:
  * *Given* `BatchImportSportTypeDialog` open,
  * *When* the athlete taps "Cancel" or dismisses the dialog,
  * *Then* the dialog closes, pending URIs are cleared, and zero routes are imported.
* **Criterion 7 (External Share Parity)**:
  * *Given* an external application sharing multiple GPX files via `ACTION_SEND_MULTIPLE`,
  * *When* `GpxImportActivity` opens,
  * *Then* `BatchImportSportTypeDialog` is presented offering "Apply to all" and "Configure individually" with identical behavior.

### 1.4 System Invariants
1. **Single-File Preview Preservation**: Single-file import (`uris.size == 1`) unconditionally retains its direct interactive edit flow (`EditRouteScreen`) with zero dialog interference.
2. **Non-Blocking Asynchronous Execution**: All file parsing, XML traversal, and DEM elevation lookups execute strictly off the main thread on `Dispatchers.IO` with non-dismissible UI progress indicators.
3. **Fault Tolerance**: Malformed, corrupt, or unreadable route files are caught gracefully, incrementing failure counters without aborting remaining valid files in either batch or individual mode.
4. **API Backward Compatibility**: Parameter `sportType: BSportType = BSportType.UNKNOWN` preserves default behavior for existing callers and legacy unit tests.
5. **Localization Parity**: 100% translation parity across all 9 canonical application locales with indexed positional format specifiers (`%1$d`, `%2$d`).
6. **Clean-Room Test Suite Pass**: 100% pass rate under `./gradlew testDebugUnitTest`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-MAP-053` (*Multi-File Route Selection, Batch Import & External Multi-Share Processing*, specifically sub-clauses `REQ-MAP-053.2`, `REQ-MAP-053.3`, and `REQ-MAP-053.5`) and introduces `REQ-MAP-053.6` and `REQ-MAP-053.7`. Targets `RoutesScreen.kt`, `RoutesViewModel.kt`, `GpxImportActivity.kt`, `GpxImportViewModel.kt`, `BatchImportSportTypeDialog.kt`, and `values*/strings.xml`.
* **Historical Origin & Commit Trace**: Sprint `2026-42.10` under ticket `ATT-4049` (commit `8c418181`). Rainer Blind accepted `ATT-4049` in Sprint Review `2026-42.10` (`ATT-4100.md`, Section 1.2) but identified during on-device testing that all imported GPX routes defaulted to `BSportType.UNKNOWN` ("Other"), requiring tedious manual editing of every route card one by one. Ticket `ATT-4284` was placed at the top of the backlog to resolve this friction.
* **Root Reason for Existing Formulation**: `ATT-4049` designed batch import purely headlessly to prevent UI thrashing (avoiding popping $N$ modal edit activities consecutively). Because GPX 1.1 files lack standardized sport metadata, `GpxRouteImporter` assigned `BSportType.UNKNOWN`. Adding an upfront modal prompt offering "Apply to all" or "Configure individually" combines the speed of headless batch import with the exact sport categorization required, while preserving sequential editing if custom naming/review is desired.
* **Preservation of Core Invariants**: Single-file interactive preview/edit flow (`uris.size == 1` -> `EditRouteScreen`) remains 100% intact; asynchronous, non-blocking I/O on `Dispatchers.IO` is preserved; fault tolerance across corrupt files is preserved; default parameter `sportType = BSportType.UNKNOWN` preserves backward compatibility for existing callers; 9-language translation parity and 100% clean-room test pass rate under `./gradlew testDebugUnitTest` are strictly maintained.

---

## 2. Test Specification (TST-MAP-056)

### Test Case 1: `RoutesViewModel` Batch Import with Sport Type Override (`[TST-MAP-056.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesViewModelTest.kt`
* **Preconditions**: Mock `RoutesRepository` and mock `GpxRouteImporter` configured to return valid `GpxImportResult`.
* **Action**:
  1. Invoke `viewModel.importRoutesBatch(listOf(uri1, uri2, uri3), sportType = BSportType.BIKE)`.
  2. Invoke `viewModel.importRoutesBatch(listOf(uri1), sportType = BSportType.RUN)`.
  3. Invoke `viewModel.importRoutesBatch(listOf(uri1))` (relying on default argument).
* **Expected Result**:
  1. For call 1, `routesRepository.insertRoute(...)` is called 3 times, each with `summary.bSportType == BSportType.BIKE`.
  2. For call 2, `routesRepository.insertRoute(...)` is called with `summary.bSportType == BSportType.RUN`.
  3. For call 3, `routesRepository.insertRoute(...)` is called with `summary.bSportType == BSportType.UNKNOWN`.

### Test Case 2: `GpxImportViewModel` Batch Handling with Sport Type Override (`[TST-MAP-056.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/GpxImportViewModelTest.kt`
* **Preconditions**: Mock `RoutesRepository` and mock `GpxRouteImporter`.
* **Action**:
  1. Call `viewModel.handleBatchUris(listOf(uri1, uri2), sportType = BSportType.BIKE)`.
* **Expected Result**:
  1. Routes are inserted into repository with `bSportType == BSportType.BIKE`.
  2. Final state is `ImportState.BatchSuccess(importedCount = 2, failedCount = 0)`.

### Test Case 3: `GpxImportViewModel` Sequential Individual Import Flow (`[TST-MAP-056.3]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/GpxImportViewModelTest.kt`
* **Preconditions**: Mock `RoutesRepository` and mock `GpxRouteImporter` returning valid routes for `uri1`, `uri2`, `uri3`.
* **Action**:
  1. Call `viewModel.startIndividualImport(listOf(uri1, uri2, uri3))`.
  2. Assert initial state is `ImportState.EditingIndividual(..., current = 1, total = 3)`.
  3. Invoke `viewModel.saveCurrentIndividualRoute(updatedSummary)`.
  4. Assert state advances to `ImportState.EditingIndividual(..., current = 2, total = 3)`.
  5. Invoke `viewModel.skipCurrentIndividualRoute()`.
  6. Assert state advances to `ImportState.EditingIndividual(..., current = 3, total = 3)`.
  7. Invoke `viewModel.saveCurrentIndividualRoute(updatedSummary)`.
* **Expected Result**:
  1. Route 1 and Route 3 are inserted into repository; Route 2 is skipped.
  2. Terminal state is `ImportState.BatchSuccess(importedCount = 2, failedCount = 1)`.

### Test Case 4: `BatchImportSportTypeDialog` Component Layout & Interaction (`[TST-MAP-056.4]`)
* **Scope**: Compose Component Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialogTest.kt`
* **Preconditions**: Test harness with Compose simulation / state validation.
* **Action & Assertions**:
  1. Verify dialog title renders `batch_import_dialog_title_format` with formatted count `%1$d`.
  2. Verify `SingleChoiceSegmentedButtonRow` contains exactly two segments: `batch_import_mode_all` and `batch_import_mode_individual`.
  3. Verify selecting `BatchSportMode.ALL` displays sport selector chips for `BIKE`, `RUN`, and `UNKNOWN` with icons and labels, and confirmation button displays `batch_import_action_import`.
  4. Verify selecting `BatchSportMode.INDIVIDUAL` hides sport selector chips, displays `batch_import_individual_hint`, and confirmation button displays `batch_import_action_continue`.
  5. Verify Cancel button dispatches `onDismiss`.
  6. Verify Confirm button invokes `onConfirm(mode, selectedSport)`.

### Test Case 5: `RoutesScreen` Multi-File Flow Discrimination Contract (`[TST-MAP-056.5]`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreenContractTest.kt`
* **Preconditions**: Route document picker contract harness.
* **Action & Assertions**:
  1. Verify single file returned (`uris.size == 1`): dispatches `startActivity(Intent(context, GpxImportActivity::class.java).apply { data = uris.first() })` with zero dialog presentation.
  2. Verify multiple files returned (`uris.size > 1`): sets `pendingBatchUris` and displays `BatchImportSportTypeDialog`.
  3. Verify confirming "Apply to all" with `BSportType.BIKE`: dispatches `viewModel.importRoutesBatch(uris, BSportType.BIKE)` and clears `pendingBatchUris`.
  4. Verify confirming "Configure individually": launches `GpxImportActivity` with `ACTION_SEND_MULTIPLE`, `uris`, and `EXTRA_INDIVIDUAL_MODE = true`, clearing `pendingBatchUris`.
  5. Verify dismissing dialog: clears `pendingBatchUris` without calling `importRoutesBatch`.

### Test Case 6: 9-Language Localization & Positional Specifier Audit (`[TST-MAP-056.6]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Goal**: Verify string presence, non-blank translations, and matching format specifiers across all 9 canonical app locales:
  * EN (`values/strings.xml`)
  * DE (`values-de/strings.xml`)
  * ES (`values-es/strings.xml`)
  * FR (`values-fr/strings.xml`)
  * IT (`values-it/strings.xml`)
  * JA (`values-ja/strings.xml`)
  * NL (`values-nl/strings.xml`)
  * PL (`values-pl/strings.xml`)
  * PT (`values-pt/strings.xml`)
* **Expected Result**: 100% parity, zero missing keys, zero specifier mismatches.

#### 9-Language Localization Canonical String Table

| String Resource Key | Locale | Localized String Value | Notes / Specifiers |
| :--- | :--- | :--- | :--- |
| `batch_import_dialog_title_format` | EN | `Import %1$d Routes` | `%1$d` route count |
| | DE | `%1$d Routen importieren` | `%1$d` route count |
| | ES | `Importar %1$d rutas` | `%1$d` route count |
| | FR | `Importer %1$d itinéraires` | `%1$d` route count |
| | IT | `Importa %1$d percorsi` | `%1$d` route count |
| | JA | `%1$d 件のルートをインポート` | `%1$d` route count |
| | NL | `%1$d routes importeren` | `%1$d` route count |
| | PL | `Importuj %1$d tras` | `%1$d` route count |
| | PT | `Importar %1$d rotas` | `%1$d` route count |
| `batch_import_mode_all` | EN | `Apply to all` | Mode 1: batch uniform |
| | DE | `Auf alle anwenden` | Mode 1: batch uniform |
| | ES | `Aplicar a todas` | Mode 1: batch uniform |
| | FR | `Appliquer à tous` | Mode 1: batch uniform |
| | IT | `Applica a tutti` | Mode 1: batch uniform |
| | JA | `すべてに適用` | Mode 1: batch uniform |
| | NL | `Op alle toepassen` | Mode 1: batch uniform |
| | PL | `Zastosuj do wszystkich` | Mode 1: batch uniform |
| | PT | `Aplicar a todas` | Mode 1: batch uniform |
| `batch_import_mode_individual` | EN | `Configure individually` | Mode 2: sequential review |
| | DE | `Individuell anpassen` | Mode 2: sequential review |
| | ES | `Configurar individualmente` | Mode 2: sequential review |
| | FR | `Configurer individuellement` | Mode 2: sequential review |
| | IT | `Configura singolarmente` | Mode 2: sequential review |
| | JA | `個別に設定` | Mode 2: sequential review |
| | NL | `Individueel aanpassen` | Mode 2: sequential review |
| | PL | `Konfiguruj indywidualnie` | Mode 2: sequential review |
| | PT | `Configurar individualmente` | Mode 2: sequential review |
| `batch_import_individual_hint` | EN | `Each route will be opened sequentially for review and editing before saving.` | Explanatory hint |
| | DE | `Jede Route wird nacheinander zur Überprüfung und Bearbeitung vor dem Speichern geöffnet.` | Explanatory hint |
| | ES | `Cada ruta se abrirá secuencialmente para su revisión y edición antes de guardar.` | Explanatory hint |
| | FR | `Chaque itinéraire sera ouvert séquentiellement pour révision et modification avant enregistrement.` | Explanatory hint |
| | IT | `Ogni percorso verrà aperto in sequenza per la revisione e la modifica prima del salvataggio.` | Explanatory hint |
| | JA | `保存する前に、各ルートが順番に開かれ確認および編集できます。` | Explanatory hint |
| | NL | `Elke route wordt opeenvolgend geopend voor controle en bewerking voor het opslaan.` | Explanatory hint |
| | PL | `Każda trasa zostanie otwarta sekwencyjnie do przejrzenia i edycji przed zapisaniem.` | Explanatory hint |
| | PT | `Cada rota será aberta sequencialmente para revisão e edição antes de salvar.` | Explanatory hint |
| `batch_import_action_import` | EN | `Import` | Mode 1 confirm button |
| | DE | `Importieren` | Mode 1 confirm button |
| | ES | `Importar` | Mode 1 confirm button |
| | FR | `Importer` | Mode 1 confirm button |
| | IT | `Importa` | Mode 1 confirm button |
| | JA | `インポート` | Mode 1 confirm button |
| | NL | `Importeren` | Mode 1 confirm button |
| | PL | `Importuj` | Mode 1 confirm button |
| | PT | `Importar` | Mode 1 confirm button |
| `batch_import_action_continue` | EN | `Continue` | Mode 2 confirm button |
| | DE | `Weiter` | Mode 2 confirm button |
| | ES | `Continuar` | Mode 2 confirm button |
| | FR | `Continuer` | Mode 2 confirm button |
| | IT | `Continua` | Mode 2 confirm button |
| | JA | `続行` | Mode 2 confirm button |
| | NL | `Doorgaan` | Mode 2 confirm button |
| | PL | `Kontynuuj` | Mode 2 confirm button |
| | PT | `Continuar` | Mode 2 confirm button |
| `batch_import_individual_progress_format` | EN | `Route %1$d of %2$d` | Sequential progress header |
| | DE | `Route %1$d von %2$d` | Sequential progress header |
| | ES | `Ruta %1$d de %2$d` | Sequential progress header |
| | FR | `Itinéraire %1$d sur %2$d` | Sequential progress header |
| | IT | `Percorso %1$d di %2$d` | Sequential progress header |
| | JA | `ルート %1$d / %2$d` | Sequential progress header |
| | NL | `Route %1$d van %2$d` | Sequential progress header |
| | PL | `Trasa %1$d z %2$d` | Sequential progress header |
| | PT | `Rota %1$d de %2$d` | Sequential progress header |
| `batch_import_skip_route` | EN | `Skip` | Skip current route |
| | DE | `Überspringen` | Skip current route |
| | ES | `Omitir` | Skip current route |
| | FR | `Ignorer` | Skip current route |
| | IT | `Salta` | Skip current route |
| | JA | `スキップ` | Skip current route |
| | NL | `Overslaan` | Skip current route |
| | PL | `Pomiń` | Skip current route |
| | PT | `Pular` | Skip current route |

### Test Case 7: Clean-Room Regression Suite Execution (`[TST-MAP-056.7]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Component Under Test | Requirement Mapping | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-MAP-056.1]` | Unit | `RoutesViewModel.importRoutesBatch(uris, sportType)` | `REQ-MAP-053.3` | Specified |
| `[TST-MAP-056.2]` | Unit | `GpxImportViewModel.handleBatchUris(uris, sportType)` | `REQ-MAP-053.5` | Specified |
| `[TST-MAP-056.3]` | Unit | `GpxImportViewModel.startIndividualImport(uris)` | `REQ-MAP-053.5` | Specified |
| `[TST-MAP-056.4]` | UI Component | `BatchImportSportTypeDialog` | `REQ-MAP-053.6` | Specified |
| `[TST-MAP-056.5]` | Contract | `RoutesScreen.kt` launcher & flow discrimination | `REQ-MAP-053.2` | Specified |
| `[TST-MAP-056.6]` | Localization | `TranslationParityTest` (8 string tokens x 9 locales) | `REQ-MAP-053.7`, `REQ-UI-106` | Specified |
| `[TST-MAP-056.7]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
