# ATT-6043 Walkthrough: Filter-Sheets & ActiveFilterChipsRow Deduplication

**Ticket**: [ATT-6043](https://atrainingtracker.atlassian.net/browse/ATT-6043)  
**Parent Epic**: [ATT-6033](https://atrainingtracker.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Sprint**: `2026-46.1` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: `REQ-UI-509` (`509.1`–`509.5`) | **Test Mapping**: `TST-UI-509` (`509.1`–`509.4`)  

---

## 1. Executive Summary

Under `ATT-6043` (governed by `REQ-UI-509` / `TST-UI-509`):
- **Shared Filter Primitives (`REQ-UI-509.1`)**: Extracted `FilterSearchField.kt`, `NumericIntervalFilterSection.kt`, and `StartLocationFilterSection.kt` into `ui/common/filters/`, eliminating duplicated search fields, interval inputs, and "Start at" chips.
- **Immutable Draft State Architecture (`REQ-UI-509.2`)**: Migrated all 5 filter bottom sheets (`Workout`, `Cluster`, `Route`, `Segment`, `KnownLocations`) to manage an immutable `draft` criteria state (`var draft by remember(criteria) { mutableStateOf(criteria) }`), replacing loose mutable state variables while decoupling text input buffers (`minDistanceText`, `maxDistanceText`, `minDurationText`, etc.) to preserve intermediate typing stability (e.g. `"1."`).
- **Functional Transformer Architecture (`REQ-UI-509.3`)**: Streamlined `ActiveFilterChipsRow.kt` to accept `onUpdateCriteria: ((WorkoutFilterCriteria) -> WorkoutFilterCriteria) -> Unit`, collapsing 41 lines of boilerplate removal callbacks in `WorkoutTabsScreen.kt` to 7 lines.
- **Dead Alias Elimination (`REQ-UI-509.4`)**: Purged dead `onRemoveMin*` alias callbacks across `ActiveFilterChipsRow`, `ActiveClusterFilterChipsRow`, and `ActiveRouteFilterChipsRow`.
- **Invariants Preserved (`REQ-UI-509.5`)**: Verified 100% compliance with `ActiveFilterChipsRowClearAllContractTest.kt`, Imperial unit parity (`REQ-UI-508`), and theme color centralization (`Rule 23`).

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Code | Verification Tests | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-509.1` | `TST-UI-509.1` | `FilterSearchField.kt` | `FilterSearchFieldTest.kt` | **Verified** |
| `REQ-UI-509.2` | `TST-UI-509.1` | `NumericIntervalFilterSection.kt` | `NumericIntervalFilterSectionTest.kt` | **Verified** |
| `REQ-UI-509.3` | `TST-UI-509.1` | `StartLocationFilterSection.kt` | `StartLocationFilterSectionTest.kt` | **Verified** |
| `REQ-UI-509.4` | `TST-UI-509.2` | Bottom Sheets (`Workout`, `Cluster`, `Route`, `Segment`, `KnownLocations`) | Sheet Unit & Contract Tests | **Verified** |
| `REQ-UI-509.5` | `TST-UI-509.3` | `ActiveFilterChipsRow.kt`, `WorkoutTabsScreen.kt` | `ActiveFilterChipsRowTest.kt`, `ActiveFilterChipsRowClearAllContractTest.kt` | **Verified** |
| `REQ-UI-509` | `TST-UI-509.4` | Targeted UI Test Suite | Targeted UI Test Suite | **Verified** |

---

## 3. Verification & Test Execution

- **Tier 1 Targeted Test Scope (Rule 60)**:
  - Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.trainingtracker.ui.common.filters.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.*" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.routes.*" --tests "com.atrainingtracker.trainingtracker.ui.segments.*" --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  - Scope: `Tier 1 targeted: ui.common.filters.*, ui.aftermath.workoutlist.*, ui.clusters.*, ui.routes.*, ui.segments.*, ui.knownlocations.*, ui.theme.ThemeColorCentralizationAuditTest, ui.components.core.*, build.*`
  - Result: **100% PASS** (713+ tests completed, 0 failures, 0 errors, 0 regressions in 32s).
- **Physical Device Execution**:
  - `adb devices` reported no physical devices attached. Per `Rule 79`, emulator launch was omitted.
