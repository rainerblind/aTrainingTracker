# ATT-936 Walkthrough: Decouple Compose screens from formatting and math via dedicated UI State models

**Ticket**: [ATT-936](https://rainerblind.atlassian.net/browse/ATT-936)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Sprint**: `2026-46.1` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: `REQ-UI-506` | **Test Mapping**: `TST-UI-506`  

---

## 1. Executive Summary

Historically, `WorkoutLaps.kt`, `WorkoutSummaryCompact.kt`, and `WorkoutExtrema.kt` performed metric formatting, speed/pace math, null-coalescing, and highlight badge evaluations directly inside `@Composable` rendering loops. This caused redundant computation during recomposition, coupled UI composables to Android framework formatters, complicated Compose `@Preview` authoring, and hindered pure JVM unit testing.

Under `ATT-936` (governed by `REQ-UI-506` / `TST-UI-506`):
- **LapRowUiState & WorkoutLaps Decoupling (`REQ-UI-506.1`)**: Introduced immutable `LapRowUiState` holding pre-formatted strings and badge emojis. Implemented pure mapper `WorkoutLapsHelper.buildLapRowUiStates(...)` calculating fastest (`🐇`) and slowest (`🦔`) highlight badges, duration, distance, and pace/speed outside composition.
- **WorkoutCardUiState & WorkoutSummaryCompact Decoupling (`REQ-UI-506.2`)**: Introduced immutable `WorkoutCardUiState` and pure transformation `WorkoutCardUiState.from(...)` pre-formatting active time, distance, speed/pace, ascent, and equipment text while resolving `contentAlpha` and context menu permissions.
- **ExtremaUiState & WorkoutExtrema Decoupling (`REQ-UI-506.3`)**: Introduced immutable `ExtremaRowUiState` and `ExtremaUiState` with `ExtremaUiState.from(...)` pre-resolving fallback `"-"` strings, min-relevance filtering, and bolding flags.
- **Backward-Compatible Overloads (`REQ-UI-506.4`)**: Maintained 100% backward-compatible domain model overloads across all 3 composables (`WorkoutLaps`, `WorkoutSummaryCompact`, `WorkoutExtrema`) ensuring zero breaking changes for existing screen callers (`WorkoutSummary.kt`, `WorkoutList.kt`, `WorkoutDetailTabbedContent.kt`, `TrackOnMapScreen.kt`).
- **Static Previews & Pure JVM Testing (`REQ-UI-506.5`)**: Added static Jetpack Compose `@Preview` composables for all 3 components using pure UI state objects. Authored `WorkoutUiStateMappingTest.kt` verifying transformation math, badge awarding, and edge cases.

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Code | Verification Tests | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-506.1` | `TST-UI-506.1` | `LapRowUiState.kt`, `WorkoutLaps.kt` | `WorkoutUiStateMappingTest.kt`, `WorkoutLapsTest.kt` | **Verified** |
| `REQ-UI-506.2` | `TST-UI-506.2` | `WorkoutCardUiState.kt`, `WorkoutSummaryCompact.kt` | `WorkoutUiStateMappingTest.kt`, `WorkoutSummaryCompactMenuTest.kt` | **Verified** |
| `REQ-UI-506.3` | `TST-UI-506.3` | `ExtremaUiState.kt`, `WorkoutExtrema.kt` | `WorkoutUiStateMappingTest.kt` | **Verified** |
| `REQ-UI-506.4` | `TST-UI-506.4` | `WorkoutLaps.kt`, `WorkoutSummaryCompact.kt`, `WorkoutExtrema.kt` | `WorkoutUiStateMappingTest.kt`, Compilation | **Verified** |
| `REQ-UI-506.5` | `TST-UI-506.5` | Target Components | `WorkoutUiStateMappingTest.kt`, Targeted Test Suite | **Verified** |

---

## 3. Verification & Test Execution

- **Tier 1 Targeted Test Scope (Rule 60)**:
  - Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.trainingtracker.ui.WorkoutUiStateMappingTest" --tests "com.atrainingtracker.trainingtracker.ui.components.workoutlaps.*" --tests "com.atrainingtracker.trainingtracker.ui.components.workoutextrema.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  - Scope: Pure UI state transformation tests, lap table and split visualizer tests, extrema table tests, compact workout card tests, theme color audit, core component contracts, and build hygiene.
  - Result: **100% PASS** (313 tests, 0 failures, 0 errors, 0 regressions).
- **Physical Device Execution**:
  - `adb devices` checked: no physical devices connected. Per Rule 79, emulator launch was omitted.
