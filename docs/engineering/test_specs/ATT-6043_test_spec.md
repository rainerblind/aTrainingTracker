# Stage 2: Requirement & Test Specification - ATT-6043: FilterBottomSheets & ActiveFilterChipsRow Deduplication

**Ticket**: [ATT-6043](https://rainerblind.atlassian.net/browse/ATT-6043)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-UI-509` (*Shared Filter UI Components, Draft State & Functional Chip Transformers*)  
**Test Spec ID**: `TST-UI-509`  
**Branch**: `feature/ATT-6043`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-UI-509)

### 1.1 Problem Statement & Rationale
Across 5 filter bottom sheets and 5 active filter chip rows, search input (`OutlinedTextField`), distance/elevation numeric intervals (~75 lines each), and "Start at" favorite locations (~65 lines) are duplicated. Furthermore, `WorkoutFilterBottomSheet` maintains 27 loose `mutableStateOf` variables, and `ActiveFilterChipsRow` exposes 19 individual callbacks. The system SHALL extract reusable filter primitives (`FilterSearchField`, `NumericIntervalFilterSection`, `StartLocationFilterSection`), migrate sheets to an immutable `draft` state with intermediate text buffers, and migrate `ActiveFilterChipsRow` to functional criteria transformers (`onUpdateCriteria`).

### 1.2 Functional & Architectural Requirements
1. **`FilterSearchField` (`REQ-UI-509.1`)**: Standardized search `OutlinedTextField` parameterized by `query`, `onQueryChange`, and `label`, featuring leading search icon and single-tap clear icon (`Icons.Default.Close`).
2. **`NumericIntervalFilterSection` (`REQ-UI-509.2`)**: Encapsulates section title, athletic preset chips (`FlowRow`), and min/max inputs with unit label, maintaining raw metric thresholds and intermediate text buffers.
3. **`StartLocationFilterSection` (`REQ-UI-509.3`)**: Encapsulates `KnownLocationItem` chips, `hitCount >= 5` filtering by default, and a toggle chip to show all locations.
4. **Immutable `draft` Pattern (`REQ-UI-509.4`)**: Sheets manage `var draft by remember(criteria) { mutableStateOf(criteria) }`, keeping detached text buffers for in-progress typing (`minDistanceText`) to prevent recomposition collapse.
5. **Functional Chip Transformer (`REQ-UI-509.5`)**: `ActiveFilterChipsRow` consumes `onUpdateCriteria: ((WorkoutFilterCriteria) -> WorkoutFilterCriteria) -> Unit`, delegating chip dismissals directly to `WorkoutSummariesViewModel.updateFilterCriteria` and eliminating dead alias callbacks.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Search Clearing)**: *Given* `FilterSearchField` with query `"tempo"`, *When* clear icon is tapped, *Then* `query` becomes empty and field displays hint label.
* **AC-2 (Decimal Typing Invariant)**: *Given* typing `"1."` into a numeric interval field, *When* recomposition occurs, *Then* text buffer retains `"1."` without premature parse reset or truncation.
* **AC-3 (Location Filter Toggle)**: *Given* `StartLocationFilterSection` with locations `< 5` starts, *When* toggle is tapped, *Then* full location list expands/collapses accordingly.
* **AC-4 (Chip Transformer)**: *Given* active query filter `"long"`, *When* chip remove icon is tapped, *Then* `onUpdateCriteria` receives `{ it.copy(query = "") }`.

### 1.4 System Invariants
Intermediate typing buffers MUST NOT collapse during recomposition; `ActiveFilterChipsRowClearAllContractTest` MUST remain 100% compliant; Imperial unit parity (`REQ-UI-508`) MUST remain intact.

---

## 2. Test Specification (TST-UI-509)

### Test Case 1: Shared Filter Components Contract Tests (`TST-UI-509.1`)
* **Scope**: Unit & Composable Contract Tests (`FilterSearchFieldTest`, `NumericIntervalFilterSectionTest`, `StartLocationFilterSectionTest`).
* **Assertions**: Clear action resets query; preset click updates min/max values; location filtering excludes infrequent locations by default.

### Test Case 2: Filter Bottom Sheets Draft State & Reset Test (`TST-UI-509.2`)
* **Scope**: Unit Test (`WorkoutFilterBottomSheetTest`, `ClusterFilterBottomSheetTest`, `RouteFilterBottomSheetTest`).
* **Assertions**: Draft captures changes and emits on apply; `onClearAll` resets draft to default criteria and clears text buffers.

### Test Case 3: Active Filter Chips Functional Transformer Test (`TST-UI-509.3`)
* **Scope**: Composable Contract Test (`ActiveFilterChipsRowTest`, `ActiveFilterChipsRowClearAllContractTest`).
* **Assertions**: Chip dismissal triggers `onUpdateCriteria` with expected copy transform; clear-all contract test passes.

### Test Case 4: Tier 1 Targeted Regression Suite (`TST-UI-509.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.common.filters.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.*" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.routes.*" --tests "com.atrainingtracker.trainingtracker.ui.segments.*" --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertions**: 100% pass rate across all modified and adjacent UI package tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-509.1` | Unit/Compose | `FilterSearchField`, `NumericIntervalFilterSection`, `StartLocationFilterSection` | `REQ-UI-509.1`, `REQ-UI-509.2`, `REQ-UI-509.3` | Specified |
| `TST-UI-509.2` | Unit/Compose | `WorkoutFilterBottomSheet`, `ClusterFilterBottomSheet`, `RouteFilterBottomSheet` | `REQ-UI-509.4` | Specified |
| `TST-UI-509.3` | Unit/Contract | `ActiveFilterChipsRow`, `ActiveFilterChipsRowClearAllContractTest` | `REQ-UI-509.5` | Specified |
| `TST-UI-509.4` | Regression | Filter UI Components, Theme Audit, Core Components | `REQ-UI-509.1`–`.5` | Specified |
