# Stage 3: Implementation Plan - ATT-936: Decouple Compose screens via UI State models

**Ticket**: [ATT-936](https://rainerblind.atlassian.net/browse/ATT-936) | **Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) | **Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.1` | **Branch**: `feature/ATT-936` | **Req/Test**: `REQ-UI-506` / `TST-UI-506`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Architecture Rationale
`WorkoutLaps.kt`, `WorkoutSummaryCompact.kt`, and `WorkoutExtrema.kt` perform metric formatting and statistical math directly in `@Composable` rendering loops. We introduce pure immutable UI state models (`LapRowUiState`, `WorkoutCardUiState`, `ExtremaUiState`) and pure transformation functions (`buildLapRowUiStates(...)`, `WorkoutCardUiState.from(...)`, `ExtremaUiState.from(...)`). This eliminates redundant formatting on recomposition, enables 100% JVM unit testability, and enables instant Compose `@Preview`s.

---

## 2. System Invariants & Preserved Behavior
1. **Visual & Formatting Parity**: Numerical formatting, unit strings, rabbit (`🐇`)/hedgehog (`🦔`) badges, and layouts remain 100% identical.
2. **Backward Compatibility**: Existing composable signatures preserved with default mapper overloads for seamless caller compatibility (`WorkoutSummary.kt`, `WorkoutDetailTabbedContent.kt`, `TrackOnMapScreen.kt`, `WorkoutList.kt`).
3. **Numeric Primitive Comparison**: Comparisons and thresholds evaluate raw numbers (`Double`), never formatted strings (`ATT-3324`).
4. **Localization & Governance**: 9-language translation parity and requirement governance remain clean.

---

## 3. UI Consistency (Design Guidelines)
* **Reused Tokens**: `MaterialTheme.typography`, `MaterialTheme.colorScheme`, `BadgeBox`, `BadgeSpacer`, `HorizontalDivider`, `RoundedCornerShape(12.dp)`.
* **Tooling Previews**: Dedicated `@Preview` composables using static UI state models across all three components.

---

## 4. Step-by-Step Implementation Sequence

### Step 1: LapRowUiState & WorkoutLapsHelper Mapper (`REQ-UI-506.1`, `REQ-UI-506.4`)
* **Files**: `ui/components/workoutlaps/LapRowUiState.kt`, `ui/components/workoutlaps/WorkoutLaps.kt`
* **Changes**: Create immutable `LapRowUiState`; implement pure `WorkoutLapsHelper.buildLapRowUiStates(...)`; refactor `WorkoutLaps` to render from `List<LapRowUiState>` while maintaining backward-compatible `List<LapData>` overload; add `@Preview`.

### Step 2: WorkoutCardUiState & WorkoutSummaryCompact Decoupling (`REQ-UI-506.2`, `REQ-UI-506.4`)
* **Files**: `ui/aftermath/workoutlist/WorkoutCardUiState.kt`, `ui/aftermath/workoutlist/WorkoutSummaryCompact.kt`
* **Changes**: Create immutable `WorkoutCardUiState`; implement pure `WorkoutCardUiState.from(...)`; refactor `WorkoutSummaryCompact` to render from `WorkoutCardUiState` while preserving `WorkoutData` overload; add `@Preview`.

### Step 3: ExtremaUiState & WorkoutExtrema Decoupling (`REQ-UI-506.3`, `REQ-UI-506.4`)
* **Files**: `ui/components/workoutextrema/ExtremaUiState.kt`, `ui/components/workoutextrema/WorkoutExtrema.kt`
* **Changes**: Create immutable `ExtremaRowUiState` and `ExtremaUiState` pre-resolving fallback `"-"` strings and bold flags; implement `ExtremaUiState.from(...)`; refactor `WorkoutExtrema` to render from `ExtremaUiState` with backward-compatible `ExtremaData` overload; add `@Preview`.

### Step 4: Pure JVM Unit & Mapping Tests (`TST-UI-506.1` – `TST-UI-506.4`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/WorkoutUiStateMappingTest.kt`
* **Changes**: Author unit tests verifying `buildLapRowUiStates`, `WorkoutCardUiState.from`, `ExtremaUiState.from`, badge awarding, fallback strings, and overload signature contracts.

### Step 5: Living Documentation Verification (`REQ-UI-506`, `TST-UI-506`)
* **Files**: `docs/requirements/req_ui_design_system.md`, `docs/tests/tst_ui_design_system.md`
* **Changes**: Update status tags from `[Draft]` / `[Planned]` to `[Verified]`.

### Step 6: Targeted Unit & Governance Test Execution (`TST-UI-506.5`)
* **Commands**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.workoutlaps.*" --tests "com.atrainingtracker.trainingtracker.ui.components.workoutextrema.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.*" --tests "com.atrainingtracker.trainingtracker.ui.WorkoutUiStateMappingTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  * `python3 tools/verify_requirement_governance.py`
