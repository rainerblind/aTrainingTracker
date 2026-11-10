# Stage 1 Analysis: ATT-6043 - FilterBottomSheets & ActiveFilterChipsRow: Gemeinsame Filter-Komponenten, Draft-State und Chip-Transformer

**Ticket**: [ATT-6043](https://rainerblind.atlassian.net/browse/ATT-6043)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-6043`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Across 5 filter bottom sheets and 5 active filter chip rows, extensive UI layout and state management boilerplate is duplicated:
1. **Duplicated UI Sections**: Search input (`OutlinedTextField`, ~25 lines) is cloned across all 5 filter bottom sheets (`Workout`, `Cluster`, `Route`, `Segment`, `KnownLocations`). Distance and elevation numeric interval sections (~75 lines each with athletic presets and min/max inputs) are cloned across `Workout`, `Cluster`, and `Route` sheets. The "Start at" / favorite locations section (~65 lines with `hitCount >= 5` filter and "Show all" toggle) is cloned between `Workout` and `Cluster` sheets.
2. **State Sprawl (27 Mutable Variables)**: `WorkoutFilterBottomSheet` unpacks `WorkoutFilterCriteria` into 27 separate `mutableStateOf` variables (15 in `ClusterFilterBottomSheet`), manually resetting each in `onClearAll` and repacking 23 arguments in `onApply`.
3. **19-Callback Explosion & Dead Aliases**: `ActiveFilterChipsRow` accepts 19 separate `() -> Unit` callbacks despite `WorkoutSummariesViewModel.updateFilterCriteria { ... }` already existing. Furthermore, legacy aliases (`onRemoveMinDistance`, `onRemoveMinDuration`, `onRemoveMinElevation`) persist unused across `ActiveFilterChipsRow`, `ActiveClusterFilterChipsRow`, and `ActiveRouteFilterChipsRow`.

---

## 2. Root Cause Analysis & Independent Peer Review
* **Root Cause**: Filter sheets were implemented iteratively per entity (workouts, then clusters, routes, segments, known locations) without extracting shared input primitives. ViewModels evolved functional transformers (`updateFilterCriteria`), but UI callbacks remained unrefactored.
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  - Verified search field duplication across 5 sheets: `WorkoutFilterBottomSheet.kt:350`, `ClusterFilterBottomSheet.kt:154`, `RouteFilterBottomSheet.kt:129`, `SegmentFilterBottomSheet.kt:98`, `KnownLocationsFilterBottomSheet.kt:93`.
  - Verified interval sections in `WorkoutFilterBottomSheet` (Z. 583-647, 720-781), `ClusterFilterBottomSheet` (Z. 205-265, 270-330), and `RouteFilterBottomSheet` (Z. 196-256, 261-321). All use identical presets and unit multipliers from `FilterFormattingUtils`.
  - Verified "Start at" section: `WorkoutFilterBottomSheet.kt:790-851` and `ClusterFilterBottomSheet.kt:361-425` are 100% structurally identical.
  - Critical Text Buffer Invariant: Decoupled text buffers (`minDistanceText`, etc.) keyed to initial criteria MUST be preserved alongside `draft: WorkoutFilterCriteria` so in-progress decimal typing (e.g., `"1."`) is not collapsed or overwritten during recomposition.
  - Verified `ActiveFilterChipsRowClearAllContractTest.kt`: Enforces `if (criteria.isEmpty) return`, `onClick = onClearAll`, and `Modifier.padding(start = 4.dp)` on all chip rows. `onClearAll` contract must remain strictly preserved.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals**:
  - Create `FilterSearchField.kt`, `NumericIntervalFilterSection.kt`, and `StartLocationFilterSection.kt` in `ui/common/filters/`.
  - Refactor `WorkoutFilterBottomSheet`, `ClusterFilterBottomSheet`, `RouteFilterBottomSheet`, `SegmentFilterBottomSheet`, and `KnownLocationsFilterBottomSheet` to delegate to these components and adopt immutable `draft` states.
  - Refactor `ActiveFilterChipsRow` to route removals via functional `onUpdateCriteria: ((WorkoutFilterCriteria) -> WorkoutFilterCriteria) -> Unit` while retaining `onClearAll: () -> Unit`.
  - Purge dead alias callbacks (`onRemoveMinDistance`, `onRemoveMinDuration`, `onRemoveMinElevation`) across chip rows and callers.
* **Out-of-Scope Non-Goals**: Modifying unit formatting logic (owned by `ATT-6168`), changing criteria domain models or database schemas, or modifying `ActiveDeviceFilterChipsRow.kt` (already minimal with 3 callbacks).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Net-New Requirement**: Proposing `REQ-UI-509` (*Shared Filter UI Components, Draft State & Functional Chip Transformers*) and `TST-UI-509` in living docs `docs/requirements/req_ui_design_system.md` and `docs/tests/tst_ui_design_system.md`.
* **Refined Requirements**: Extends `REQ-UI-157` (*Workout Filter*), `REQ-UI-187` (*Cluster Filter*), `REQ-UI-425` (*Elevation Filter*), `REQ-UI-468` (*Clear All Contract*), and `REQ-UI-508` (*Imperial Filter Parity*).
* **Historical Origin**: Incremental additions in sprints 2026-40.4 (`ATT-1276`), 2026-42.2 (`ATT-1593`), 2026-45.12 (`ATT-5888`), and 2026-46.1 (`ATT-6168`).
* **Root Reason for Existing Formulation**: Sheets were built independently to prevent coupling during rapid feature rollout; `remember(criteria.x)` states were added piecemeal as criteria fields grew.
* **Preservation of Core Invariants**: 100% visual layout parity, imperial/metric parity (`REQ-UI-508`), text buffer re-key stability, and `ActiveFilterChipsRowClearAllContractTest` compliance strictly preserved.

---

## 5. Architectural Strategy & Deduplication Approach
1. **`FilterSearchField`**: Standardized `OutlinedTextField` parameterized by `query`, `onQueryChange`, and `labelResId`/`label`.
2. **`NumericIntervalFilterSection`**: Encapsulates header text, unit, preset chips (`FlowRow`), and min/max inputs with `onRangeChange(min, max, minText, maxText)`.
3. **`StartLocationFilterSection`**: Encapsulates `knownLocations` mapping, `hitCount >= 5` toggle, and location selection chips.
4. **Immutable `draft` Pattern**: Sheets manage `var draft by remember(criteria) { mutableStateOf(criteria) }` for all criteria fields, keeping only local text buffers for in-progress typing.
5. **Chip Transformer**: `ActiveFilterChipsRow` consumes `onUpdateCriteria: ((WorkoutFilterCriteria) -> WorkoutFilterCriteria) -> Unit`, delegating chip dismissals directly to `WorkoutSummariesViewModel.updateFilterCriteria`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero visual drift or interaction regressions; intermediate text typing preserved; contract tests green.
* **Risk Rating**: **LOW** (UI deduplication & functional refactoring; no database, service, or sensor lifecycle changes).
