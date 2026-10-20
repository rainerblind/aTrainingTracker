# Stage 2: Requirement & Test Specification - ATT-4372: Polish BatchImportSportTypeDialog layout (fix truncated segmented buttons and vertical 'Andere' chip wrap) and default to Other

**Ticket**: [ATT-4372](https://rainerblind.atlassian.net/browse/ATT-4372)  
**Sub-task**: [ATT-4393](https://rainerblind.atlassian.net/browse/ATT-4393) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Requirement Mapping**: `REQ-MAP-053` (*Multi-File Route Selection, Batch Import, Sport Type Selection Prompt & External Multi-Share Processing*)  
**Test Spec ID**: `TST-MAP-056`  
**Branch**: `feature/ATT-4372`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-MAP-053)

### 1.1 Problem Statement & Rationale
In Sprint `2026-43.1`, ticket `ATT-4284` introduced [`BatchImportSportTypeDialog.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialog.kt) to prompt athletes when importing multiple GPX/TCX route files, offering a choice between applying a single sport type to all files or configuring each route individually. During the Sprint `2026-43.1` Joint Review (`ATT-4290`), live on-device testing on a German-locale device revealed two visual layout defects and a domain default discrepancy (see screenshot `att_batch_import_dialog.png` attached to ATT-4372):

1. **Truncated Segmented Buttons**:
   The binary mode switcher (`SingleChoiceSegmentedButtonRow`) inside the standard Material 3 `AlertDialog` horizontally truncated the segmented button labels in German:
   * `"Auf alle anwenden"` was clipped mid-word to `"Auf alle anwen"`.
   * `"Individuell anpassen"` was clipped mid-word to `"Individuell anpa"`.
   In other verbose locales (e.g. Spanish `"Configurar individualmente"` [26 chars], French `"Configurer individuellement"` [26 chars], Polish `"Konfiguruj indywidualnie"` [24 chars]), the text truncation is even more severe due to rigid `maxLines = 1` and `softWrap = false` constraints in a narrow ~`280.dp` dialog container.

2. **Vertical Single-Character 'Andere' Chip Wrapping**:
   The 3 sport type selection chips (`BSportType.BIKE`, `BSportType.RUN`, `BSportType.UNKNOWN`) were placed in a single, rigid horizontal `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp))`. In German (`"Radeln"`, `"Laufen"`, `"Andere"`), the first two chips with leading icons consumed nearly all available width within the narrow `AlertDialog`. The remaining width squeezed the third chip (`"Andere"`) into a narrow ~1-character-wide column, forcing the text to break vertically into a 6-line stack:
   ```text
   A
   n
   d
   e
   r
   e
   ```
   This anti-pattern was codified as a retrospective learning in Sprint `2026-43.1` (`ATT-4290`) and established as **Rule 54** ([`docs/design_guidelines.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/design_guidelines.md) Sec 5.13): *Never place 3+ FilterChips inside a single non-wrapping Row within a standard AlertDialog. Always use FlowRow.*

3. **Suboptimal Default Sport Preselection**:
   `BatchImportSportTypeDialog` hardcoded `selectedSport = BSportType.BIKE`. However, GPX and TCX tracks imported in batch frequently originate from walking, running, hiking, or unspecified outdoor activities. Defaulting to `Bike` risks silent misclassification if the athlete leaves the preselection untouched. When sport type is ambiguous or unspecified, the neutral, safe default is `"Other"` (`BSportType.UNKNOWN` / `"Andere"`), matching the domain fallback used across `GpxRouteImporter` and `TcxCourseParser`.

---

### 1.2 Functional & Architectural Requirements

* **REQ-MAP-053.6.1: Presentation Container Migration to `AppModalBottomSheet`**:
  * [`BatchImportSportTypeDialog`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialog.kt) SHALL render an [`AppModalBottomSheet`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppModalBottomSheet.kt) instead of `AlertDialog`, matching the modal sheet design language used in `EditRouteScreen` and `RouteFilterBottomSheet` in the routes subsystem.
  * The bottom sheet SHALL display the formatted title (`R.string.batch_import_dialog_title_format`, `"%1$d Routes"`), standard drag handle (`MinimumDragHandle()`), top-right close button, and standard bottom action buttons (`cancel` and confirm).
  * The bottom sheet container SHALL provide full screen width (`~360.dp`–`412.dp`), eliminating narrow horizontal dialog margin bottlenecks.
  * The public invocation signature of `BatchImportSportTypeDialog` SHALL remain 100% backward-compatible:
    ```kotlin
    fun BatchImportSportTypeDialog(
        routeCount: Int,
        onConfirm: (mode: BatchSportMode, selectedSport: BSportType) -> Unit,
        onDismiss: () -> Unit,
        modifier: Modifier = Modifier
    )
    ```

* **REQ-MAP-053.6.2: Responsive Segmented Button Mode Switcher**:
  * Mode selection SHALL use `SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth())` with two segments: "Apply to all" (`R.string.batch_import_mode_all`) and "Configure individually" (`R.string.batch_import_mode_individual`) per Design Guidelines Section 1.1.
  * `SegmentedButton` text composables SHALL configure multi-line centered wrapping:
    * `maxLines = 2`
    * `textAlign = TextAlign.Center`
    * `overflow = TextOverflow.Ellipsis`
    * `style = MaterialTheme.typography.labelMedium`
  * Rigid `maxLines = 1` and `softWrap = false` constraints MUST be eliminated, allowing verbose translations (e.g. Spanish, French, Polish, Portuguese $\le 26$ chars) to wrap cleanly onto 2 lines without mid-word clipping.

* **REQ-MAP-053.6.3: Multi-Chip `FlowRow` for Sport Selection (Rule 54)**:
  * When `selectedMode == BatchSportMode.ALL`, sport selection chips SHALL be laid out inside `FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth())`.
  * The rigid non-wrapping `Row` layout MUST be eliminated.
  * Filter chips SHALL wrap as complete, intact horizontal pills to subsequent rows whenever viewport width or font scaling requires, strictly preventing single-character vertical text stacking.

* **REQ-MAP-053.6.4: Neutral Default Sport Preselection (`BSportType.UNKNOWN`)**:
  * In `BatchImportSportTypeDialog`, `selectedSport` state SHALL default to `BSportType.UNKNOWN` ("Other" / `"Andere"`):
    ```kotlin
    var selectedSport by remember { mutableStateOf(BSportType.UNKNOWN) }
    ```
  * Sport type selection chips SHALL render in order: `listOf(BSportType.UNKNOWN, BSportType.BIKE, BSportType.RUN)`, presenting the neutral default as the primary active selection while keeping `BIKE` and `RUN` immediately selectable.

* **REQ-MAP-053.6.5: Slotted Actions & Mode-Adaptive Confirm Text**:
  * Cancel action SHALL render `TextButton(onClick = onDismiss)` with `R.string.cancel`.
  * Confirm action SHALL render `Button(onClick = { onConfirm(selectedMode, selectedSport) })` with:
    * `R.string.batch_import_action_import` when `selectedMode == BatchSportMode.ALL`.
    * `R.string.batch_import_action_continue` when `selectedMode == BatchSportMode.INDIVIDUAL`.
  * When `selectedMode == BatchSportMode.INDIVIDUAL`, the body SHALL render localized hint `R.string.batch_import_individual_hint`.

---

### Requirement Archaeology & Chesterton's Fence Audit

* `Original Requirement ID & Target`: Refines `REQ-MAP-053` (*Multi-File Route Selection, Batch Import, Sport Type Selection Prompt & External Multi-Share Processing*, specifically sub-clause `REQ-MAP-053.6` and its acceptance criteria). Targets [`BatchImportSportTypeDialog.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialog.kt), [`BatchImportSportTypeDialogTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialogTest.kt), [`docs/requirements.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md), and [`docs/tests.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md).
* `Historical Origin & Commit Trace`: Sprint `2026-42.10` under ticket `ATT-4049` (commit `8c418181`: headless batch import); refined in Sprint `2026-43.1` under ticket `ATT-4284` (commit `49133ab1`: introduced `BatchImportSportTypeDialog` as an `AlertDialog` defaulting to `BIKE`). During Sprint `2026-43.1` Joint Review (`ATT-4290`, commit `7fafb143`), live on-device testing revealed clipped segmented buttons and vertical 'Andere' chip crushing in German (`att_batch_import_dialog.png`), establishing Rule 54 (`docs/design_guidelines.md` Sec 5.13). Polished in Sprint `2026-43.2` under `ATT-4372`.
* `Root Reason for Existing Formulation`: `ATT-4284` implemented `BatchImportSportTypeDialog` as an `AlertDialog` with a 2-segment button and 3 chips in a `Row` under the assumption that short English labels would fit horizontally. The author applied `maxLines = 1` citing Rule 38 without budgeting for verbose localized strings in narrow dialog containers. `BSportType.BIKE` was preselected assuming cycling was the primary use case, rather than preserving domain neutrality across mixed outdoor sports.
* `Preservation of Core Invariants`: Call site API contracts in `RoutesScreen` and `GpxImportActivity` remain 100% identical; binary mode toggle (`BatchSportMode.ALL` vs `BatchSportMode.INDIVIDUAL`) remains preserved; all three sport types (`BIKE`, `RUN`, `UNKNOWN`) remain selectable with canonical icons and localized names; non-blocking asynchronous batch import on `Dispatchers.IO` is preserved; single-file preview preservation (`uris.size == 1` $\to$ `EditRouteScreen`) is preserved; 9-language translation parity and 100% clean-room test pass rate under `./gradlew testDebugUnitTest` are strictly maintained.

---

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1: Non-Constrained Full-Width Presentation via `AppModalBottomSheet`**:
  * *Given* an athlete selecting multiple GPX/TCX route files ($N > 1$) in `RoutesScreen` or external share,
  * *When* the batch import prompt is presented,
  * *Then* it SHALL render as an `AppModalBottomSheet` featuring top drag handle, route count header (`R.string.batch_import_dialog_title_format`), and close button.

* **Criterion 2: Multi-Line Non-Clipping Segmented Button Labels across All 9 Locales**:
  * *Given* `BatchImportSportTypeDialog` rendered on any supported screen width in any of the 9 canonical locales (including German, Spanish, French, and Polish),
  * *When* the mode switcher renders,
  * *Then* button labels SHALL wrap into up to 2 centered lines without mid-word clipping or character truncation.

* **Criterion 3: Clean Pill Wrapping in `FlowRow` without 1-Character Text Crushing (Rule 54)**:
  * *Given* `BatchImportSportTypeDialog` in `BatchSportMode.ALL` mode,
  * *When* sport selection chips (`BSportType.UNKNOWN`, `BSportType.BIKE`, `BSportType.RUN`) render,
  * *Then* they SHALL be arranged in a `FlowRow` with 8dp horizontal and vertical spacing, wrapping as intact pills without single-character vertical stacking.

* **Criterion 4: Neutral Default Preselection of `BSportType.UNKNOWN`**:
  * *Given* initial presentation of `BatchImportSportTypeDialog`,
  * *When* the athlete has not made any selection,
  * *Then* `selectedSport` SHALL default to `BSportType.UNKNOWN` ("Other" / "Andere"), matching the domain fallback used in `GpxRouteImporter` and `RoutesViewModel.importRoutesBatch`.

* **Criterion 5: Call Site Backward Compatibility**:
  * *Given* `RoutesScreen.kt` and `GpxImportActivity.kt`,
  * *When* invoking `BatchImportSportTypeDialog(routeCount = ..., onConfirm = ..., onDismiss = ...)`,
  * *Then* parameter types and callback dispatches SHALL remain 100% compatible with zero invocation changes required.

* **Criterion 6: Sequential Individual Import State Machine Preservation**:
  * *Given* an athlete selecting "Configure individually" in `BatchImportSportTypeDialog`,
  * *When* tapping "Continue",
  * *Then* `onConfirm(BatchSportMode.INDIVIDUAL, sportType)` SHALL be dispatched, launching sequential review in `GpxImportActivity` without behavioral regressions.

---

### 1.4 System Invariants

1. **Call Site Invariant**: The signature `BatchImportSportTypeDialog(routeCount, onConfirm, onDismiss, modifier)` MUST NOT be broken.
2. **Single-File Import Invariant**: Single GPX/TCX file selection (`uris.size == 1`) MUST continue to bypass `BatchImportSportTypeDialog` and navigate directly to `EditRouteScreen`.
3. **9-Language Localization Parity Invariant**: All existing string tokens (`batch_import_dialog_title_format`, `batch_import_mode_all`, `batch_import_mode_individual`, `batch_import_individual_hint`, `batch_import_action_import`, `batch_import_action_continue`, `batch_import_individual_progress_format`, `batch_import_skip_route`) MUST remain 100% defined across all 9 canonical locales with matching positional specifiers.
4. **Clean-Room Regression Suite Invariant**: All unit, ViewModel, and contract tests MUST pass cleanly with 100% pass rate under `./gradlew testDebugUnitTest`.
5. **Human Decision Gate Invariant**: The parent ticket `ATT-4372` Human Decision Gate remains strictly guarded; terminal state for the agent lifecycle is `Final Review (Human)`.

---

## 2. Test Specification (TST-MAP-056)

### Test Case 1: `RoutesViewModel` Batch Import with Sport Type Override Test (`TST-MAP-056.1`)
* **Scope**: ViewModel Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesViewModelTest.kt`
* **Preconditions**: Mocked `RoutesRepository` and `GpxRouteImporter`.
* **Action & Verification**:
  * Provide 3 valid URIs and call `viewModel.importRoutesBatch(uris, sportType = BSportType.BIKE)`.
    * Verify each route is inserted into `routesRepository` with `summary.bSportType == BSportType.BIKE`.
  * Call `viewModel.importRoutesBatch(uris, sportType = BSportType.RUN)`.
    * Verify `summary.bSportType == BSportType.RUN`.
  * Call `viewModel.importRoutesBatch(uris, sportType = BSportType.UNKNOWN)` (and default argument).
    * Verify neutral fallback `summary.bSportType == BSportType.UNKNOWN`.

### Test Case 2: `GpxImportViewModel` Batch Handling with Sport Type Override Test (`TST-MAP-056.2`)
* **Scope**: ViewModel Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/GpxImportViewModelTest.kt`
* **Preconditions**: Mocked `RoutesRepository`.
* **Action & Verification**:
  * Provide URIs and call `viewModel.handleBatchUris(uris, sportType = BSportType.BIKE)`.
    * Verify routes are inserted into `routesRepository` with `bSportType == BSportType.BIKE`.
  * Call with `sportType = BSportType.UNKNOWN`.
    * Verify routes are inserted with `bSportType == BSportType.UNKNOWN`.

### Test Case 3: `GpxImportViewModel` Sequential Individual Import Flow Test (`TST-MAP-056.3`)
* **Scope**: ViewModel Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/GpxImportViewModelTest.kt`
* **Preconditions**: Mocked `RoutesRepository`.
* **Action & Verification**:
  * Provide 3 URIs and invoke `viewModel.startIndividualImport(uris)`.
  * Verify initial state is `ImportState.EditingIndividual(..., current = 1, total = 3)`.
  * Save route 1: verify repository insert and state advances to `current = 2, total = 3`.
  * Skip route 2: verify route 2 is not inserted and state advances to `current = 3, total = 3`.
  * Save route 3: verify repository insert and state transitions to `ImportState.BatchSuccess(importedCount = 2, failedCount = 1)`.

### Test Case 4: `BatchImportSportTypeDialog` Component Layout, `AppModalBottomSheet`, `FlowRow` & Neutral Preselection Contract Test (`TST-MAP-056.4`)
* **Scope**: Architectural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialogTest.kt`
* **Preconditions**: `BatchImportSportTypeDialog.kt` source code is available.
* **Action**: Perform AST / source code inspection on `BatchImportSportTypeDialog.kt`.
* **Expected Result**:
  1. Root modal container uses `AppModalBottomSheet` (replaces legacy `AlertDialog`).
  2. Dialog title uses `R.string.batch_import_dialog_title_format` with `routeCount`.
  3. Binary mode switcher uses `SingleChoiceSegmentedButtonRow` with `Modifier.fillMaxWidth()`.
  4. Segmented buttons use multi-line centered text wrapping (`maxLines = 2`, `textAlign = TextAlign.Center`, `overflow = TextOverflow.Ellipsis`, `style = MaterialTheme.typography.labelMedium`) and do NOT enforce rigid `maxLines = 1` or `softWrap = false`.
  5. Sport selection chips reside in `FlowRow` with 8dp horizontal and vertical spacing (Rule 54) rather than a rigid `Row`.
  6. Initial `selectedSport` state defaults to `BSportType.UNKNOWN` ("Other" / "Andere").
  7. Chips include `BSportType.UNKNOWN`, `BSportType.BIKE`, and `BSportType.RUN`.
  8. Explanatory hint `R.string.batch_import_individual_hint` is displayed when in `INDIVIDUAL` mode.
  9. Confirm button adapts text between `R.string.batch_import_action_import` and `R.string.batch_import_action_continue`.
  10. Dismiss button uses `R.string.cancel`.
  11. Provides both Light and Dark mode `@Preview` composables.

### Test Case 5: `RoutesScreen` Multi-File Flow Discrimination Contract Test (`TST-MAP-056.5`)
* **Scope**: Architectural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreenContractTest.kt`
* **Preconditions**: `RoutesScreen.kt` source code is available.
* **Action**: Verify flow discrimination branches.
* **Expected Result**:
  1. `uris.size == 1` launches `GpxImportActivity` directly without bottom sheet.
  2. `uris.size > 1` sets `pendingBatchUris` and composes `BatchImportSportTypeDialog`.
  3. Confirming ALL mode invokes `viewModel.importRoutesBatch(uris, selectedSport)`.
  4. Confirming INDIVIDUAL mode launches `GpxImportActivity` with `EXTRA_INDIVIDUAL_MODE = true`.
  5. Dismissing clears `pendingBatchUris` without import.

### Test Case 6: 9-Language Localization & Format Specifier Parity Test (`TST-MAP-056.6`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Goal**: Verify string presence and matching `%s`/`%d` tokens across all 9 canonical locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **String Tokens Audited**:
  * `batch_import_dialog_title_format` (`%1$d`)
  * `batch_import_mode_all`
  * `batch_import_mode_individual`
  * `batch_import_individual_hint`
  * `batch_import_action_import`
  * `batch_import_action_continue`
  * `batch_import_individual_progress_format` (`%1$d`, `%2$d`)
  * `batch_import_skip_route`
  * `routes_import_progress` (`%1$d`, `%2$d`)
  * `routes_import_success` (`%1$d`)
  * `routes_import_partial` (`%1$d`, `%2$d`)
  * `routes_import_all_failed`
  * `route_sport_type`
  * `sport_type_other`
  * `sport_type_bike`
  * `sport_type_run`
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 7: Clean-Room Regression Suite Execution (`TST-MAP-056.7`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test / Target | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MAP-056.1` | ViewModel | `RoutesViewModel.importRoutesBatch` | `REQ-MAP-053.3` | Specified |
| `TST-MAP-056.2` | ViewModel | `GpxImportViewModel.handleBatchUris` | `REQ-MAP-053.5` | Specified |
| `TST-MAP-056.3` | ViewModel | `GpxImportViewModel.startIndividualImport` | `REQ-MAP-053.5` | Specified |
| `TST-MAP-056.4` | Component Contract | `BatchImportSportTypeDialog` (`AppModalBottomSheet`, `FlowRow`, `BSportType.UNKNOWN`, multi-line) | `REQ-MAP-053.6` | Specified |
| `TST-MAP-056.5` | Architectural Contract | `RoutesScreen` Multi-File Flow Discrimination | `REQ-MAP-053.2` | Specified |
| `TST-MAP-056.6` | Localization | `TranslationParityTest` (9 Canonical Locales) | `REQ-MAP-053.7`, `REQ-UI-106` | Specified |
| `TST-MAP-056.7` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
