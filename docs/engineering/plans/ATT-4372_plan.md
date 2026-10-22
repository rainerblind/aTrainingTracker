# Stage 3: Implementation Plan - ATT-4372: Polish BatchImportSportTypeDialog layout (fix truncated segmented buttons and vertical 'Andere' chip wrap) and default to Other

**Ticket**: [ATT-4372](https://rainerblind.atlassian.net/browse/ATT-4372)  
**Sub-task**: [ATT-4394](https://rainerblind.atlassian.net/browse/ATT-4394) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Requirement Mapping**: `REQ-MAP-053` (*Multi-File Route Selection, Batch Import, Sport Type Selection Prompt & External Multi-Share Processing*)  
**Test Mapping**: `TST-MAP-056` (*Batch GPX Route Import Sport Type Selection & Sequential Review Verification Suite*)  
**Branch**: `feature/ATT-4372`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

In Sprint `2026-43.1`, ticket `ATT-4284` introduced [`BatchImportSportTypeDialog.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialog.kt) to prompt athletes when batch-importing multiple GPX/TCX route files, offering a choice between applying a uniform sport type to all files or configuring each route individually. During the Sprint `2026-43.1` Joint Review (`ATT-4290`), live on-device testing on a German-locale device revealed two visual layout defects and a domain default discrepancy (screenshot `att_batch_import_dialog.png` attached to ATT-4372):

1. **Truncated Segmented Buttons**:
   The binary mode switcher (`SingleChoiceSegmentedButtonRow`) inside the standard Material 3 `AlertDialog` horizontally truncated segmented button labels in German:
   * `"Auf alle anwenden"` was clipped mid-word to `"Auf alle anwen"`.
   * `"Individuell anpassen"` was clipped mid-word to `"Individuell anpa"`.
   In other verbose locales (e.g. Spanish `"Configurar individualmente"` [26 chars], French `"Configurer individuellement"` [26 chars], Polish `"Konfiguruj indywidualnie"` [24 chars]), text truncation is even more pronounced due to rigid `maxLines = 1` and `softWrap = false` constraints in a narrow ~`280.dp` dialog container.

2. **Vertical Single-Character 'Andere' Chip Wrapping**:
   The 3 sport type selection chips (`BSportType.BIKE`, `BSportType.RUN`, `BSportType.UNKNOWN`) were placed in a single, rigid horizontal `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp))`. In German (`"Radeln"`, `"Laufen"`, `"Andere"`), the first two chips with leading icons consumed nearly all available width within the narrow `AlertDialog`. The remaining width squeezed the third chip (`"Andere"`) into a narrow ~1-character-wide column, forcing the text to break vertically into a 6-line stack (`A / n / d / e / r / e`), violating **Rule 54** ([`docs/design_guidelines.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/design_guidelines.md) Sec 5.13).

3. **Default Sport Preselection**:
   `BatchImportSportTypeDialog` hardcoded `selectedSport = BSportType.BIKE`. When GPX/TCX tracks are batch-imported, they frequently lack explicit sport metadata or originate from walking, running, hiking, or mixed activities. When sport type is ambiguous or unspecified, the neutral, safe default is `"Other"` (`BSportType.UNKNOWN` / `"Andere"`), matching the domain fallback used in `GpxRouteImporter`, `TcxCourseParser`, and `RoutesViewModel.importRoutesBatch`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-053` (*Multi-File Route Selection, Batch Import, Sport Type Selection Prompt & External Multi-Share Processing*, specifically `REQ-MAP-053.6.1` through `REQ-MAP-053.6.5`)
* **Test Mapping**: `TST-MAP-056` (*Batch GPX Route Import Sport Type Selection & Sequential Review Verification Suite*, specifically `TST-MAP-056.4` and `TST-MAP-056.7`)

| Requirement Clause | Functional Scope | Target Test Case | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-053.6.1` | Presentation Container Migration to `AppModalBottomSheet` | `TST-MAP-056.4` | Architectural AST & Source Contract Audit |
| `REQ-MAP-053.6.2` | Responsive Segmented Button Mode Switcher (multi-line, non-clipping) | `TST-MAP-056.4` | Architectural AST & Source Contract Audit |
| `REQ-MAP-053.6.3` | Multi-Chip `FlowRow` for Sport Selection (Rule 54) | `TST-MAP-056.4` | Architectural AST & Source Contract Audit |
| `REQ-MAP-053.6.4` | Neutral Default Sport Preselection (`BSportType.UNKNOWN`) | `TST-MAP-056.4` | Architectural AST & Source Contract Audit |
| `REQ-MAP-053.6.5` | Slotted Actions & Mode-Adaptive Confirm Text | `TST-MAP-056.4` | Architectural AST & Source Contract Audit |
| `REQ-MAP-053.7` | 9-Language Localization & Positional Specifier Parity | `TST-MAP-056.6` | `TranslationParityTest.kt` |
| `REQ-PRO-001` | Full Clean-Room Test Suite Pass Rate (100% green) | `TST-MAP-056.7` | `./gradlew testDebugUnitTest` |

---

## 3. System Invariants & Preserved Behavior

1. **Call Site Invariant**: The invocation signature of `BatchImportSportTypeDialog(routeCount, onConfirm, onDismiss, modifier)` MUST remain 100% backward-compatible. Callers in `RoutesScreen.kt` and `GpxImportActivity.kt` continue calling the component without syntax changes.
2. **Single-File Import Invariant**: Single GPX/TCX file selection (`uris.size == 1`) continues to bypass the batch dialog and navigate directly to `EditRouteScreen`.
3. **Domain Engine Invariant**: Route importing and persistence in `RoutesViewModel.importRoutesBatch` and `GpxImportViewModel.handleBatchUris` remain untouched and continue defaulting to `BSportType.UNKNOWN`.
4. **9-Language Localization Parity**: All 16 batch-import and route string tokens remain defined and validated across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
5. **Zero Regression Invariant**: 100% unit test pass rate under `./gradlew testDebugUnitTest`.
6. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-4372` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `BatchImportSportTypeDialog` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialog.kt`)

* **Layer**: UI Presentation (Jetpack Compose)
* **Changes**:
  1. **Replace Root Modal Container**:
     * Migrate from `AlertDialog` to [`AppModalBottomSheet`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppModalBottomSheet.kt).
     * Bind `title = stringResource(R.string.batch_import_dialog_title_format, routeCount)`.
     * Bind `onDismissRequest = onDismiss`.
     * Provide dedicated `actions` slot with horizontal `Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically)` containing Cancel `TextButton` and dynamic Confirm `Button`.
  2. **Refactor Segmented Button Mode Switcher**:
     * Keep `SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth())` with `SegmentedButton`.
     * Remove rigid `maxLines = 1` and `softWrap = false`.
     * Configure `Text` with:
       ```kotlin
       maxLines = 2,
       textAlign = TextAlign.Center,
       overflow = TextOverflow.Ellipsis,
       style = MaterialTheme.typography.labelMedium
       ```
     * Guarantees clean two-line centered wrapping for long strings across all viewports without mid-word ellipsis.
  3. **Adopt `FlowRow` for Sport Selection (Rule 54)**:
     * Replace rigid horizontal `Row` with:
       ```kotlin
       FlowRow(
           modifier = Modifier.fillMaxWidth(),
           horizontalArrangement = Arrangement.spacedBy(8.dp),
           verticalArrangement = Arrangement.spacedBy(8.dp)
       )
       ```
     * Iterate over `listOf(BSportType.UNKNOWN, BSportType.BIKE, BSportType.RUN)`.
     * When screen width or accessibility font scales constrain horizontal space, chips wrap cleanly as whole pills onto subsequent rows, preventing vertical 1-character text crushing.
  4. **Neutral Default Sport Preselection**:
     * Update initial remember state:
       ```kotlin
       var selectedSport by remember { mutableStateOf(BSportType.UNKNOWN) }
       ```
     * Preselects "Other" / `"Andere"` as the default when sport type is ambiguous or unspecified.
  5. **Preview Composables**:
     * Update `@Preview` composables (`BatchImportSportTypeDialogAllLightPreview` and `BatchImportSportTypeDialogAllDarkPreview`) to render with the new bottom sheet layout.

### Component 2: `BatchImportSportTypeDialogTest` (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialogTest.kt`)

* **Layer**: Test / Architectural Verification
* **Changes**:
  1. Update `testBatchImportSportTypeDialog_sourceArchitectureAndTokens()`:
     * Assert root container uses `AppModalBottomSheet(` instead of `AlertDialog(`.
     * Assert `FlowRow(` is used for sport chips layout (Rule 54).
     * Assert `selectedSport` defaults to `BSportType.UNKNOWN`.
     * Assert sport list includes `BSportType.UNKNOWN`, `BSportType.BIKE`, `BSportType.RUN`.
     * Assert segmented button texts use `maxLines = 2` and `labelMedium`.
     * Assert absence of rigid `softWrap = false` on segmented button labels.
     * Verify title format, hint text, action button text adaptivity, and `@Preview` annotations remain intact.

### UI Consistency (Rule 23 & Rule 54 — Mandatory UI Section)

* **Reference Screen / Component**:
  * [`EditRouteScreen`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/EditRouteScreen.kt) and [`RouteFilterBottomSheet`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteFilterBottomSheet.kt) in `ui/routes/`.
  * `EditRouteScreen` already uses `AppModalBottomSheet` with drag handle, close button, title, and bottom actions in the exact same route import workflow.
* **Reused Components**:
  * [`AppModalBottomSheet`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppModalBottomSheet.kt) from `ui/components/core/`
  * `androidx.compose.foundation.layout.FlowRow`
  * `androidx.compose.material3.SingleChoiceSegmentedButtonRow` & `SegmentedButton`
  * `androidx.compose.material3.FilterChip`
  * `androidx.compose.material3.MinimumDragHandle`
* **Theme Tokens**:
  * Shapes: `BottomSheetDesign.SheetShape` (20dp top rounded corners)
  * Spacing: `8.dp` chip gap, `8.dp` vertical row gap, `16.dp` section spacing, `4.dp` horizontal body padding
  * Colors: `MaterialTheme.colorScheme.surface`, `MaterialTheme.colorScheme.onSurfaceVariant`, `MaterialTheme.colorScheme.primary`
  * Typography: `MaterialTheme.typography.titleSmall` for section header, `MaterialTheme.typography.labelMedium` for segmented buttons, `MaterialTheme.typography.bodyMedium` for hint text
* **New One-Off Styles & Justification**: None. The implementation strictly reuses the standard design system tokens and shared bottom sheet infrastructure.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Component Refactoring in `BatchImportSportTypeDialog.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialog.kt`
* **Changes**:
  1. Add imports:
     * `androidx.compose.foundation.layout.FlowRow`
     * `androidx.compose.foundation.layout.width`
     * `androidx.compose.foundation.layout.padding`
     * `androidx.compose.ui.Alignment`
     * `androidx.compose.ui.text.style.TextAlign`
     * `androidx.compose.ui.text.style.TextOverflow`
     * `com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet`
  2. Remove imports:
     * `androidx.compose.material3.AlertDialog`
  3. Update initial state: `var selectedSport by remember { mutableStateOf(BSportType.UNKNOWN) }`.
  4. Replace `AlertDialog` with `AppModalBottomSheet(title = stringResource(R.string.batch_import_dialog_title_format, routeCount), onDismissRequest = onDismiss, actions = { ... }, modifier = modifier)`.
  5. In `SingleChoiceSegmentedButtonRow`, configure `Text` with `maxLines = 2`, `textAlign = TextAlign.Center`, `overflow = TextOverflow.Ellipsis`, `style = MaterialTheme.typography.labelMedium`.
  6. In `BatchSportMode.ALL`, wrap sport chips in `FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp))` with `listOf(BSportType.UNKNOWN, BSportType.BIKE, BSportType.RUN)`.

### Step 2: Contract Test Update in `BatchImportSportTypeDialogTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialogTest.kt`
* **Changes**:
  1. Update `testBatchImportSportTypeDialog_sourceArchitectureAndTokens()`:
     * Assert `content.contains("AppModalBottomSheet(")` (replacing `AlertDialog(`).
     * Assert `content.contains("FlowRow(")` (Rule 54).
     * Assert `content.contains("mutableStateOf(BSportType.UNKNOWN)")` for neutral preselection.
     * Assert `content.contains("maxLines = 2")` and `content.contains("labelMedium")`.
     * Assert `!content.contains("softWrap = false")`.
     * Assert chips include `BSportType.UNKNOWN`, `BSportType.BIKE`, `BSportType.RUN`.

### Step 3: Targeted Unit & Contract Test Execution
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.BatchImportSportTypeDialogTest"
  ```
* **Expected Result**: 100% pass rate on `BatchImportSportTypeDialogTest`.

### Step 4: Subsystem Route & Import Regression Execution
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.*"
  ```
* **Expected Result**: All tests in `com.atrainingtracker.trainingtracker.ui.routes` pass cleanly, verifying zero regressions in `RoutesViewModelTest`, `GpxImportViewModelTest`, and `RoutesScreenContractTest`.

### Step 5: Full Clean-Room Regression Verification (Stage 5 Pre-check)
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* **Expected Result**: Full test suite passes 100% cleanly with zero regressions.

---

## 6. Verification & Rollback Plan

### Verification Plan
* **Automated Unit Testing**:
  * Execute targeted component tests: `BatchImportSportTypeDialogTest`
  * Execute route flow tests: `RoutesScreenContractTest`, `RoutesViewModelTest`, `GpxImportViewModelTest`
  * Execute localization tests: `TranslationParityTest`
* **Clean-Room Verification**:
  * Full `./gradlew testDebugUnitTest` execution across all modules.

### Rollback Plan
* All changes are isolated on branch `feature/ATT-4372`.
* In the event of an unexpected regression, changes can be rolled back cleanly via `git checkout -- app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialog.kt app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialogTest.kt`.
