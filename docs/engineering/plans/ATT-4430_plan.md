# Stage 3: Implementation Plan - ATT-4430: [Verbesserung] Order sport type chips in BatchImportSportTypeDialog with Other on the far right (Bike, Run, Other)

**Ticket**: [ATT-4430](https://rainerblind.atlassian.net/browse/ATT-4430)  
**Sub-task**: [ATT-4446](https://rainerblind.atlassian.net/browse/ATT-4446) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.3`  
**Requirement Mapping**: `REQ-MAP-053` (*Multi-File Route Selection, Batch Import, Sport Type Selection Prompt & External Multi-Share Processing*, specifically `REQ-MAP-053.6`)  
**Test Mapping**: `TST-MAP-056` (*Batch GPX Route Import Sport Type Selection, Responsive Bottom Sheet Layout & Sequential Review Verification Suite*, specifically `TST-MAP-056.4`)  
**Branch**: `feature/ATT-4430`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

In Sprint `2026-43.2`, ticket `ATT-4372` polished [`BatchImportSportTypeDialog.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialog.kt):
1. Migrated the dialog container from `AlertDialog` to [`AppModalBottomSheet`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppModalBottomSheet.kt), resolving horizontal width constraints.
2. Adopted multi-line centered text on `SingleChoiceSegmentedButtonRow` to prevent mid-word clipping in verbose locales.
3. Placed sport selection chips into `FlowRow` per **Rule 54** ([`docs/design_guidelines.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/design_guidelines.md) Sec 5.13), eliminating single-character vertical text crushing on German `"Andere"`.
4. Changed default sport preselection from `BSportType.BIKE` to `BSportType.UNKNOWN` ("Other" / "Andere") for domain neutrality.

However, when setting `BSportType.UNKNOWN` as default, `ATT-4372` also reordered the visual chip list:
```kotlin
listOf(BSportType.UNKNOWN, BSportType.BIKE, BSportType.RUN).forEach { sport ->
```
This placed `"Andere"` as the leftmost chip in the row, conflating initial preselection state with visual scanning hierarchy.

During the Sprint `2026-43.2` Joint Review of `ATT-4372`, the human reviewer confirmed the bottom sheet layout and neutral default selection were much better, but requested one specific UI refinement:
- The catch-all category `"Other"` (`BSportType.UNKNOWN` / `"Andere"`) must always be positioned on the **far right**, with primary concrete sports (`Bike`, `Run`) appearing first.
- This canonical ordering is already established in [`EditRouteScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/EditRouteScreen.kt):
  ```kotlin
  listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN).forEach { bSportType ->
  ```
- This UI principle was codified into **Rule 57** ([`docs/design_guidelines.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/design_guidelines.md) Section 1.2):
  > *Across all sport type selector rows (`FilterChip` rows, segmented buttons), specific primary sports (`Run`, `Bike` / `Bike`, `Run`) MUST appear first, and the catch-all `Other` (`BSportType.UNKNOWN` / `"Andere"`) MUST always be positioned on the far right (`[Bike, Run, Other]` or `[Run, Bike, Other]`), even when `Other` is preselected as the default value. Never move `Other` to the leftmost position just because it is the default selection.*

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-053` (*Multi-File Route Selection, Batch Import, Sport Type Selection Prompt & External Multi-Share Processing*, specifically `REQ-MAP-053.6`)
* **Test Mapping**: `TST-MAP-056` (*Batch GPX Route Import Sport Type Selection, Responsive Bottom Sheet Layout & Sequential Review Verification Suite*, specifically `TST-MAP-056.4`, `TST-MAP-056.6`, `TST-MAP-056.7`)

| Requirement Clause | Functional Scope | Target Test Case | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-053.6` (Rule 57) | Canonical Sport Type Chip Ordering (`[Bike, Run, Other]`) | `TST-MAP-056.4` | Architectural AST & Source Contract Audit in `BatchImportSportTypeDialogTest.kt` |
| `REQ-MAP-053.6` | Neutral Default Preselection (`BSportType.UNKNOWN`) Preserved | `TST-MAP-056.4` | Architectural AST & Source Contract Audit in `BatchImportSportTypeDialogTest.kt` |
| `REQ-MAP-053.6` (Rule 54) | Intact Chip Wrapping in `FlowRow` (8dp spacing) Preserved | `TST-MAP-056.4` | Architectural AST & Source Contract Audit in `BatchImportSportTypeDialogTest.kt` |
| `REQ-MAP-053.7` | 9-Language Localization & Positional Specifier Parity | `TST-MAP-056.6` | `TranslationParityTest.kt` & `testAllRequiredBatchImportStrings_existInValues` |
| `REQ-PRO-001` | Full Clean-Room Test Suite Pass Rate (100% green) | `TST-MAP-056.7` | `./gradlew testDebugUnitTest` |

---

## 3. System Invariants & Preserved Behavior

1. **Domain Neutrality Invariant**: Initial preselected sport state remains strictly `BSportType.UNKNOWN` ("Other" / "Andere"), ensuring imported tracks are not prematurely or falsely coerced into cycling workouts.
2. **Call Site Invariant**: The public invocation signature `BatchImportSportTypeDialog(routeCount, onConfirm, onDismiss, modifier)` remains 100% unchanged. Callers in `RoutesScreen.kt` and `GpxImportActivity.kt` require zero modifications.
3. **Single-File Import Invariant**: Single GPX/TCX file selection (`uris.size == 1`) continues to bypass the batch dialog completely and navigates directly to `EditRouteScreen`.
4. **Responsive Container Invariant (Rule 54)**: Sport selection chips remain hosted inside `FlowRow` with `horizontalArrangement = Arrangement.spacedBy(8.dp)` and `verticalArrangement = Arrangement.spacedBy(8.dp)`, guaranteeing pills wrap intact without single-character vertical text distortion.
5. **9-Language Localization Parity**: Zero new string keys added. All batch-import and route string tokens remain defined and verified across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
6. **Zero Regression Invariant**: 100% unit test pass rate under `./gradlew testDebugUnitTest`.
7. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-4430` remains reserved exclusively for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `BatchImportSportTypeDialog` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialog.kt`)

* **Layer**: UI Presentation (Jetpack Compose)
* **Changes**:
  1. **Canonical Chip Ordering in `FlowRow` (Rule 57)**:
     - Update line 165:
       ```kotlin
       // BEFORE (ATT-4372):
       listOf(BSportType.UNKNOWN, BSportType.BIKE, BSportType.RUN).forEach { sport ->
       
       // AFTER (ATT-4430 / Rule 57):
       listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN).forEach { sport ->
       ```
     - Renders specific primary sports (`Bike`, `Run`) first, and the catch-all `Other` on the far right.
  2. **Preserve Initial Preselection State**:
     - Keep line 95:
       ```kotlin
       var selectedSport by remember { mutableStateOf(BSportType.UNKNOWN) }
       ```
     - The far-right chip (`Other`) is rendered as preselected upon dialog entry.
  3. **Documentation Alignment**:
     - Update KDoc to reference Rule 57 canonical ordering parity with `EditRouteScreen.kt`.

### Component 2: `BatchImportSportTypeDialogTest` (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialogTest.kt`)

* **Layer**: Architectural Unit & Contract Test
* **Changes**:
  - In `testBatchImportSportTypeDialog_sourceArchitectureAndTokens`:
    - Replace unordered set check (lines 111–116):
      ```kotlin
      // BEFORE:
      assertTrue(
          "Must include BSportType.UNKNOWN, BSportType.BIKE, BSportType.RUN",
          content.contains("BSportType.UNKNOWN") &&
                  content.contains("BSportType.BIKE") &&
                  content.contains("BSportType.RUN")
      )
      
      // AFTER:
      assertTrue(
          "Must order sport chips as Bike, Run, Other per Rule 57 and EditRouteScreen",
          content.contains("listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN)")
      )
      ```
    - Retain assertion for default preselection:
      ```kotlin
      assertTrue(
          "Must default preselection to BSportType.UNKNOWN (REQ-MAP-053.6.4)",
          content.contains("mutableStateOf(BSportType.UNKNOWN)")
      )
      ```

### UI Consistency (Rule 23)
* **Reference screen / component**: [`EditRouteScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/EditRouteScreen.kt) lines 148–165. `EditRouteScreen` already orders sport filter chips as `listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN)`.
* **Reused components**: `AppModalBottomSheet`, `SingleChoiceSegmentedButtonRow`, `SegmentedButton`, `FlowRow`, `FilterChip`, `Icon`, `Text`, `Button`, `TextButton`.
* **Theme tokens**: Standard `MaterialTheme.typography.labelMedium`, `titleSmall`, `bodyMedium`, spacing `8.dp` and `16.dp`, shapes `SegmentedButtonDefaults.itemShape`.
* **New one-off styles & justification**: None. Reuses 100% standard route component styling and Material 3 design tokens.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `BatchImportSportTypeDialog.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialog.kt`
* **Changes**:
  - Update `FlowRow` iteration to `listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN).forEach { sport ->`.
  - Preserve `var selectedSport by remember { mutableStateOf(BSportType.UNKNOWN) }`.
  - Update KDoc to document Rule 57 canonical chip ordering.

### Step 2: Update Architectural Contract Test in `BatchImportSportTypeDialogTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialogTest.kt`
* **Changes**:
  - Replace set-membership check with strict canonical ordering assertion `listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN)`.

### Step 3: Execute Targeted Unit Tests & Governance Validation
* **Commands**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.BatchImportSportTypeDialogTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RoutesScreenContractTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.TranslationParityTest"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Gate 3 audit by `subagent_flash` (`--as agent2`).
  - Stage 4 verification via targeted unit tests (`BatchImportSportTypeDialogTest`).
  - Stage 5 clean-room test suite execution (`./gradlew testDebugUnitTest`).
* **Rollback Plan**:
  - Changes are strictly isolated on branch `feature/ATT-4430`.
  - Reversion if necessary: `git checkout feature/ATT-4430 && git checkout HEAD -- app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialog.kt app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialogTest.kt`.
