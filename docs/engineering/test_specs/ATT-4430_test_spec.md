# Stage 2: Requirement & Test Specification - ATT-4430: [Verbesserung] Order sport type chips in BatchImportSportTypeDialog with Other on the far right (Bike, Run, Other)

**Ticket**: [ATT-4430](https://rainerblind.atlassian.net/browse/ATT-4430)  
**Sub-task**: Pending Submission (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.3`  
**Requirement Mapping**: `REQ-MAP-053` (*Multi-File Route Selection, Batch Import, Sport Type Selection Prompt & External Multi-Share Processing*, specifically `REQ-MAP-053.6`)  
**Test Spec ID**: `TST-MAP-056` (*Batch GPX Route Import Sport Type Selection, Responsive Bottom Sheet Layout & Sequential Review Verification Suite*, specifically `TST-MAP-056.4`)  
**Branch**: `feature/ATT-4430`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-MAP-053 / REQ-MAP-053.6)

### 1.1 Problem Statement & Rationale
During Sprint `2026-43.2`, ticket `ATT-4372` resolved layout clipping in `BatchImportSportTypeDialog` (`ui/routes/BatchImportSportTypeDialog.kt`) and established `BSportType.UNKNOWN` ("Other" / "Andere") as the neutral default preselection for batch route imports. However, in doing so, the developer placed `BSportType.UNKNOWN` as the first item in the chip list:
```kotlin
listOf(BSportType.UNKNOWN, BSportType.BIKE, BSportType.RUN).forEach { sport ->
```
During the Sprint `2026-43.2` Joint Review, the human reviewer accepted the `AppModalBottomSheet` layout and neutral default selection, but pointed out an inconsistency with `EditRouteScreen.kt` (which presents `[Bike, Run, Other]`). The review established **Rule 57** (`docs/design_guidelines.md` Section 1.2):
> *Across all sport type selector rows (`FilterChip` rows, segmented buttons), specific primary sports (`Run`, `Bike` / `Bike`, `Run`) MUST appear first, and the catch-all `Other` (`BSportType.UNKNOWN` / `"Andere"`) MUST always be positioned on the **far right** (`[Bike, Run, Other]` or `[Run, Bike, Other]`), even when `Other` is preselected as the default value. Never move `Other` to the leftmost position just because it is the default selection.*

Placing `Other` at the far right conforms with natural left-to-right cognitive scanning (concrete primary options first, generic fallback at the end) and aligns `BatchImportSportTypeDialog` with `EditRouteScreen`.

### 1.2 Functional & Architectural Requirements
1. **Canonical Chip Ordering (Rule 57, REQ-MAP-053.6)**:
   - In `BatchImportSportTypeDialog.kt`, when "Apply to all" mode is active (`BatchSportMode.ALL`), the `FlowRow` SHALL iterate over sport types in the canonical horizontal order `listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN)` (`Bike, Run, Other`), rendering specific primary sports first and catch-all `Other` on the far right, matching `EditRouteScreen.kt` parity.
2. **Neutral Default Preselection Invariant (REQ-MAP-053.6)**:
   - The initial preselected sport state SHALL strictly default to `BSportType.UNKNOWN` (`Other` / `Andere`):
     ```kotlin
     var selectedSport by remember { mutableStateOf(BSportType.UNKNOWN) }
     ```
   - Preselection of `BSportType.UNKNOWN` SHALL NOT alter the visual order of chips (i.e. `Other` remains visually on the far right while preselected).
3. **Responsive Chip Container Preservation (Rule 54, REQ-MAP-053.6)**:
   - Sport type chips SHALL remain hosted inside `FlowRow` with 8dp horizontal and vertical spacing (`horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)`), ensuring pills wrap intact without single-character vertical text distortion across all display densities and languages.
4. **Architectural & Contract Test Enforcement (TST-MAP-056.4)**:
   - `BatchImportSportTypeDialogTest.kt` SHALL explicitly assert the canonical ordering token `listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN)`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1: Canonical Chip Ordering in "Apply to all" Mode**:
  * *Given* the athlete selects multiple GPX files ($N > 1$) in `RoutesScreen` or shares multiple GPX files to `GpxImportActivity`,
  * *When* `BatchImportSportTypeDialog` is displayed in "Apply to all" mode (`BatchSportMode.ALL`),
  * *Then* the sport type `FilterChip` items SHALL render horizontally in the order: `Bike`, `Run`, `Other` (`listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN)`).
* **Criterion 2: Default Preselection on Far-Right Chip**:
  * *Given* `BatchImportSportTypeDialog` appears on screen,
  * *When* the athlete has not yet interacted with the sport type chips,
  * *Then* the `Other` (`BSportType.UNKNOWN` / `"Andere"`) chip on the far right SHALL be preselected with active indicator and filled style, while `Bike` and `Run` remain unselected.
* **Criterion 3: Sport Selection Dispatch**:
  * *Given* `BatchImportSportTypeDialog` with chips ordered `[Bike, Run, Other]`,
  * *When* the athlete taps the `Bike` or `Run` chip and taps "Import",
  * *Then* `onConfirm(BatchSportMode.ALL, selectedSport)` SHALL dispatch with the selected `BSportType.BIKE` or `BSportType.RUN`.
* **Criterion 4: Individual Review Mode Unchanged**:
  * *Given* `BatchImportSportTypeDialog`,
  * *When* the athlete selects "Configure individually" (`BatchSportMode.INDIVIDUAL`),
  * *Then* the sport type chip row is hidden, the explanatory hint `R.string.batch_import_individual_hint` is displayed, and tapping "Continue" launches sequential route editing.

### 1.4 System Invariants
1. **Zero Call Site Disruption**: Public API signatures and parameter contracts in `BatchImportSportTypeDialog.kt`, `RoutesScreen.kt`, and `GpxImportActivity.kt` remain 100% unchanged.
2. **Domain Neutrality**: Default sport preselection remains `BSportType.UNKNOWN`, preventing accidental cycling tagging on hiking/running GPX tracks.
3. **Localization Parity**: 100% string resource parity across all 9 canonical locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`). No new string keys or format specifiers added.
4. **Clean-Room Test Pass Rate**: 100% passing test suite under `./gradlew testDebugUnitTest`.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  - Refines `REQ-MAP-053` (*Multi-File Route Selection, Batch Import, Sport Type Selection Prompt & External Multi-Share Processing*), specifically sub-clause `REQ-MAP-053.6` (*Batch Import Sport Type Dialog Component Architecture*).
  - Targets:
    - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialog.kt`
    - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialogTest.kt`
    - `docs/requirements.md`
    - `docs/tests.md`
* **Historical Origin & Commit Trace**:
  - `ATT-4049` (commit `8c418181`, Sprint `2026-42.10`): Introduced headless batch route import.
  - `ATT-4284` (commit `49133ab1`, Sprint `2026-43.1`): Added `BatchImportSportTypeDialog` as an `AlertDialog` with chips `[BIKE, RUN, UNKNOWN]` but default preselection `selectedSport = BSportType.BIKE`.
  - `ATT-4372` (commit `49c12c7a`, Sprint `2026-43.2`): Migrated dialog to `AppModalBottomSheet`, resolved segmented button truncation and vertical chip crushing via `FlowRow` (Rule 54), and set default preselection to `BSportType.UNKNOWN`. In doing so, placed `UNKNOWN` first in the list: `listOf(BSportType.UNKNOWN, BSportType.BIKE, BSportType.RUN)`.
  - Sprint `2026-43.2` Joint Review: User clarified that `Other` must remain default preselected, but chip ordering must place `Other` on the far right (`[BIKE, RUN, UNKNOWN]`), establishing **Rule 57** (`docs/design_guidelines.md` Section 1.2).
* **Root Reason for Existing Formulation**:
  - The implementer of `ATT-4372` placed `BSportType.UNKNOWN` first because it was the default selection, erroneously assuming that default preselection must always occupy the first visual slot. This conflicted with `EditRouteScreen.kt` and violated left-to-right cognitive scanning expectations where generic fallback options belong at the end.
* **Preservation of Core Invariants**:
  - Default preselection of `BSportType.UNKNOWN` is preserved for domain neutrality.
  - All three sport types (`BIKE`, `RUN`, `UNKNOWN`) remain selectable with canonical icons and localized names.
  - Responsive `FlowRow` layout (Rule 54) and `AppModalBottomSheet` container remain intact.
  - Call site API contracts in `RoutesScreen` and `GpxImportActivity` remain 100% identical.
  - 9-language translation parity maintained with zero missing string entries.
  - 100% clean-room test suite pass rate under `./gradlew testDebugUnitTest`.

---

## 2. Test Specification (TST-MAP-056)

### Test Case 1: `testBatchImportSportTypeDialog_sourceArchitectureAndTokens` (`TST-MAP-056.4`)
* **Scope**: Unit / Architectural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialogTest.kt`
* **Preconditions**: `BatchImportSportTypeDialog.kt` source file exists and compiles.
* **Action**:
  - Read `BatchImportSportTypeDialog.kt` source content.
  - Inspect sport type chip iteration tokens in `FlowRow`.
* **Expected Result**:
  - Assert `content.contains("listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN)")` evaluates to `true` (enforcing Rule 57 and `EditRouteScreen` parity).
  - Assert `content.contains("mutableStateOf(BSportType.UNKNOWN)")` evaluates to `true` (enforcing default preselection neutrality).
  - Assert `AppModalBottomSheet`, `SingleChoiceSegmentedButtonRow`, `FlowRow`, and action button tokens remain valid.

### Test Case 2: 9-Language Localization & Specifier Audit (`TST-MAP-056.6`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialogTest.kt` (`testAllRequiredBatchImportStrings_existInValues`) & `TranslationParityTest.kt`
* **Preconditions**: Resource directories `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/` exist.
* **Action**:
  - Verify string keys `batch_import_dialog_title_format`, `batch_import_mode_all`, `batch_import_mode_individual`, `batch_import_individual_hint`, `batch_import_action_import`, `batch_import_action_continue`, `route_sport_type`, `sport_type_bike`, `sport_type_run`, `sport_type_other`.
* **Expected Result**:
  - 100% presence and non-blank values across all 9 canonical locales.
  - Zero format specifier mismatches.

### Test Case 3: Clean-Room Regression Suite (`TST-MAP-056.7`)
* **Command**: `./gradlew testDebugUnitTest`
* **Scope**: Full Clean-Room Unit Test Suite
* **Goal**: Verify 100% pass rate with zero regressions across the codebase.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MAP-056.4` | Unit / Contract | `BatchImportSportTypeDialog` | `REQ-MAP-053.6`, Rule 57 | Specified |
| `TST-MAP-056.6` | Localization | `BatchImportSportTypeDialogTest` / `TranslationParityTest` | `REQ-MAP-053.7`, `REQ-UI-106` | Specified |
| `TST-MAP-056.7` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
