# Stage 3 Implementation Plan: ATT-6043 - FilterBottomSheets & ActiveFilterChipsRow Deduplication

**Ticket**: [ATT-6043](https://rainerblind.atlassian.net/browse/ATT-6043)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-UI-509` (`509.1`–`509.5`)  
**Test Spec Mapping**: `TST-UI-509` (`509.1`–`509.4`)  
**Branch**: `feature/ATT-6043`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-10  

---

## 1. Technical Architecture & SWE.2 Design
* **Decomposition & Boundaries**:
  - `FilterSearchField.kt`: Reusable composable in `ui/common/filters/` standardizing `OutlinedTextField` search input with leading search icon and trailing tap-to-clear icon (`Icons.Default.Close`).
  - `NumericIntervalFilterSection.kt`: Standardized numeric interval composable in `ui/common/filters/` rendering section title, athletic preset chips (`FlowRow`), min/max text fields with unit indicator, and raw metric value translation via `FilterFormattingUtils`.
  - `StartLocationFilterSection.kt`: Standardized favorite start location composable in `ui/common/filters/` rendering `KnownLocationItem` chips, filtering infrequent locations (`hitCount < 5`) by default, and providing toggle to show all locations.
  - Immutable `draft` State: All 5 filter bottom sheets (`Workout`, `Cluster`, `Route`, `Segment`, `KnownLocations`) manage `var draft by remember(criteria) { mutableStateOf(criteria) }`, replacing up to 27 loose mutable variables while maintaining detached text buffers (`minDistanceText`) to prevent intermediate typing collapse (e.g. `"1."`).
  - Functional Chip Transformer: `ActiveFilterChipsRow.kt` consumes `onUpdateCriteria: ((WorkoutFilterCriteria) -> WorkoutFilterCriteria) -> Unit`, routing individual chip removals directly through `WorkoutSummariesViewModel.updateFilterCriteria`. Dead aliases (`onRemoveMinDistance`, `onRemoveMinDuration`, `onRemoveMinElevation`) purged across chip rows and host screens.

---

## 2. UI Consistency & Theming (Rule 23 & Rule 80)
* **Reference Component**: Existing `FilterBottomSheetScaffold` and `WorkoutFilterBottomSheet` layout.
* **Design System Tokens**: Title style `MaterialTheme.typography.titleMedium` tinted `primary`, spacing `spacedBy(8.dp)` horizontal and `4.dp` vertical in `FlowRow`, `12.dp` spacing between min/max fields, single-line text inputs with `KeyboardType.Decimal` / `KeyboardType.Number`.

---

## 3. Atomic Implementation Steps

| Step | Component | Target Files | Action & Invariants | Traceability |
| :--- | :--- | :--- | :--- | :--- |
| **1** | Shared Primitives | `ui/common/filters/FilterSearchField.kt`<br>`ui/common/filters/NumericIntervalFilterSection.kt`<br>`ui/common/filters/StartLocationFilterSection.kt` | Author shared filter primitives with detached text buffers and preset selection. | `REQ-UI-509.1-3`<br>`TST-UI-509.1` |
| **2** | Metric Sheets | `WorkoutFilterBottomSheet.kt`<br>`ClusterFilterBottomSheet.kt`<br>`RouteFilterBottomSheet.kt` | Delegate search, intervals, and start locations to shared primitives; adopt immutable `draft` state. | `REQ-UI-509.1-4`<br>`TST-UI-509.2` |
| **3** | Secondary Sheets | `SegmentFilterBottomSheet.kt`<br>`KnownLocationsFilterBottomSheet.kt` | Delegate search to `FilterSearchField`; adopt immutable `draft` criteria state. | `REQ-UI-509.1,4`<br>`TST-UI-509.2` |
| **4** | Active Chip Rows | `ActiveFilterChipsRow.kt`<br>`ActiveClusterFilterChipsRow.kt`<br>`ActiveRouteFilterChipsRow.kt`<br>`WorkoutTabsScreen.kt` | Adopt `onUpdateCriteria` transformer in `ActiveFilterChipsRow`; purge dead `onRemoveMin*` alias callbacks. | `REQ-UI-509.5`<br>`TST-UI-509.3` |
| **5** | Tests & Verification | `FilterSearchFieldTest.kt`<br>`NumericIntervalFilterSectionTest.kt`<br>`StartLocationFilterSectionTest.kt`<br>`ActiveFilterChipsRowClearAllContractTest.kt` | Add component unit tests; assert `ActiveFilterChipsRowClearAllContractTest` passes; run targeted test suite. | `REQ-UI-509.1-5`<br>`TST-UI-509.1-4` |

---

## 4. System Invariants & Rollback Safety
* **Code Lock (Rule 4)**: No production source files in `app/src/main` modified until Gate 3 passes and `check-gate` exits 0.
* **Typing Invariant**: Free-text decimal input buffers preserved during recomposition (typing `"1."` remains stable).
* **Clear All Contract**: `ActiveFilterChipsRowClearAllContractTest` remains 100% compliant across all chip rows.
* **Risk Rating**: **LOW** (Declarative UI deduplication and functional transformer adoption; zero DB or service changes).
