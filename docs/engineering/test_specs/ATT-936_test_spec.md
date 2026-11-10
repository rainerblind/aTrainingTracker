# Stage 2: Requirement & Test Specification - ATT-936: Decouple Compose screens from formatting and math via dedicated UI State models

**Ticket**: [ATT-936](https://rainerblind.atlassian.net/browse/ATT-936)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-46.1` | **Branch**: `feature/ATT-936`  
**Requirement Mapping**: `REQ-UI-506` (`docs/requirements/req_ui_design_system.md`)  
**Test Spec ID**: `TST-UI-506` (`docs/tests/tst_ui_design_system.md`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-UI-506)

### 1.1 Problem Statement & Rationale
Core Jetpack Compose composables (`WorkoutLaps.kt`, `WorkoutSummaryCompact.kt`, `WorkoutExtrema.kt`) directly invoke metric formatters and compute statistics (fastest/slowest lap speeds, extrema fallbacks, formatted telemetry strings) during composition loops. The system SHALL decouple these composables via immutable UI state models (`LapRowUiState`, `WorkoutCardUiState`, `ExtremaUiState`) and pure transformation functions, eliminating redundant recomposition overhead and enabling JVM unit tests and static `@Preview`s.

### 1.2 Functional & Architectural Requirements
* **REQ-UI-506.1** (*LapRowUiState & WorkoutLaps Decoupling*): Introduce immutable `LapRowUiState` holding pre-formatted strings (`displayName`, `description`, `timeFormatted`, `distanceFormatted`, `speedPaceFormatted`, `badgeEmoji`, `badgeDescription`) and click callback. Expose pure mapper `WorkoutLapsHelper.buildLapRowUiStates(...)` calculating fastest/slowest badges and formatted values outside composition.
* **REQ-UI-506.2** (*WorkoutCardUiState & WorkoutSummaryCompact Decoupling*): Introduce immutable `WorkoutCardUiState` encapsulating card metadata and formatted metrics (`activeTimeFormatted`, `distanceFormatted`, `speedPaceFormatted`, `ascentFormatted`, `sportIconResId`, `sportEquipmentText`, `formattedDateTime`). Expose pure mapper `WorkoutCardUiState.from(...)`.
* **REQ-UI-506.3** (*ExtremaUiState & WorkoutExtrema Decoupling*): Introduce immutable `ExtremaRowUiState` and `ExtremaUiState` representing ready-to-render extrema rows, decoupling null-coalescing and fallback `"-"` logic from `WorkoutExtrema.kt`. Expose pure mapper `ExtremaUiState.from(ExtremaData)`.
* **REQ-UI-506.4** (*Backward Compatibility & Composable Overloads*): Retain existing domain model composable overloads across `WorkoutLaps`, `WorkoutSummaryCompact`, and `WorkoutExtrema` with zero breaking changes for existing screen callers.
* **REQ-UI-506.5** (*Static Previews & Pure JVM Unit Testability*): Add static Jetpack Compose `@Preview`s populated with UI state models and pure JVM unit tests verifying mapping logic.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends `REQ-UI-141` (Lap badges) and `REQ-UI-011` (Extrema table). Targets `WorkoutLaps.kt`, `WorkoutSummaryCompact.kt`, `WorkoutExtrema.kt`.
2. *Historical Origin & Commit Trace*: Tickets `ATT-510`, `ATT-1742`, `ATT-1870`, `ATT-3390`.
3. *Root Reason for Existing Formulation*: Formatters were accessed directly via `LocalMetricFormatter` during initial prototyping before formal MVVM state hoisting.
4. *Preservation of Core Invariants*: Numerical formatting, units, badge rules, click handling, and 9-locale parity remain 100% identical.

### 1.4 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* `List<LapData>`, *When* mapped via `WorkoutLapsHelper.buildLapRowUiStates(...)`, *Then* fastest lap receives `"🐇"`, slowest receives `"🦔"`, and strings match formatters.
* *Given* `WorkoutData`, *When* mapped via `WorkoutCardUiState.from(...)`, *Then* active time, distance, speed/pace, and ascent are pre-formatted without composable formatting.
* *Given* `ExtremaData` with nulls, *When* converted via `ExtremaUiState.from(...)`, *Then* missing values resolve to `"-"` in state models.
* *Invariants*: 100% visual/functional parity; zero regression across 9 locales; all contract tests green.

---

## 2. Test Specification (TST-UI-506)

### Test Case 1: LapRowUiState Mapping & Badge Determination (`TST-UI-506.1`)
* **Scope**: Automated Unit Test (`WorkoutUiStateMappingTest.kt`)
* **Assertion**: Verify `buildLapRowUiStates(...)` accurately computes fastest/slowest lap badges, formatted time, distance, and speed/pace for RUN and non-RUN sports.

### Test Case 2: WorkoutCardUiState Transformation Contract Test (`TST-UI-506.2`)
* **Scope**: Automated Unit Test (`WorkoutUiStateMappingTest.kt`)
* **Assertion**: Verify `WorkoutCardUiState.from(...)` formats metrics according to athlete settings and resolves sport/equipment text.

### Test Case 3: ExtremaUiState Transformation Contract Test (`TST-UI-506.3`)
* **Scope**: Automated Unit Test (`WorkoutUiStateMappingTest.kt`)
* **Assertion**: Verify `ExtremaUiState.from(...)` maps rows, resolves nulls to `"-"`, respects `isMinRelevant`, and propagates bolding.

### Test Case 4: Composable Signature & Backward Compatibility Test (`TST-UI-506.4`)
* **Scope**: Compilation & UI Contract Test
* **Assertion**: Existing composable callers compile and render without signature breakage.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-UI-506.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.workoutlaps.*" --tests "com.atrainingtracker.trainingtracker.ui.components.workoutextrema.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertion**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-506.1` | Unit | `WorkoutUiStateMappingTest.kt`, `LapRowUiState.kt` | `REQ-UI-506.1` | Specified |
| `TST-UI-506.2` | Unit | `WorkoutUiStateMappingTest.kt`, `WorkoutCardUiState.kt` | `REQ-UI-506.2` | Specified |
| `TST-UI-506.3` | Unit | `WorkoutUiStateMappingTest.kt`, `ExtremaUiState.kt` | `REQ-UI-506.3` | Specified |
| `TST-UI-506.4` | Contract | `WorkoutLaps.kt`, `WorkoutSummaryCompact.kt`, `WorkoutExtrema.kt` | `REQ-UI-506.4` | Specified |
| `TST-UI-506.5` | Regression | `ui.components.workoutlaps.*`, `ui.components.workoutextrema.*`, `build.*` | `REQ-PRO-001` | Specified |
