# Stage 1 Analysis: ATT-5822 - Favorite Routes & Filter Sheets Starts and Hit Count Default Filtering

**Ticket**: [ATT-5822](https://rainerblind.atlassian.net/browse/ATT-5822)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.11`  
**Requirement Mapping**: `REQ-UI-467` (refining `REQ-UI-187`, `REQ-UI-417`, `REQ-UI-466`)  
**Test Mapping**: `TST-UI-467`  
**Branch**: `feature/ATT-5822`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-07  

---

## 1. Problem Statement & Motivation
Routes recorded only 1–4 times do not constitute genuine "Lieblingsstrecken" (Favorite Routes). Similarly, locations with 1–4 starts (e.g., vacation routes) flood the "Startet bei (Lieblingsorte)" chip selection in filter dialogs. Athletes require:
1. `WorkoutClustersViewModel` to always default to `minHitCount = 5` on screen entry as a transparent session filter, surfacing `"≥ 5 Aufzeichnungen"` in `ActiveClusterFilterChipsRow`.
2. `WorkoutFilterBottomSheet` and `ClusterFilterBottomSheet` to filter "Startet bei" chips to `loc.hitCount >= 5` (plus active selection) by default, offering a compact `"Alle anzeigen"` / `"Nur ≥ 5 Starts"` toggle chip when `< 5` starts exist.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Lieblingsstrecken Default Filter**: In `WorkoutClustersViewModel.kt` (L156–161), `filterCriteria` is derived from `preferenceManager.clusterFilterCriteriaFlow`, restoring empty criteria (`minHitCount = null`) from DataStore. Switching to a session-scoped `_filterCriteria` initialized with `ClusterFilterCriteria(minHitCount = 5)` guarantees that every screen entry starts with `≥ 5` recordings while preserving 1-tap removal via `onRemoveMinHitCount` and "Filter aufheben" (`minHitCount = null`).
* **Start Location Chip Flooding**: In both `WorkoutFilterBottomSheet.kt` (L818–860) and `ClusterFilterBottomSheet.kt` (L360–396), `knownLocations` are rendered unconditionally. Filtering by `loc.hitCount >= 5 || isSelected || showAllLocations` and conditionally providing a toggle `FilterChip` eliminates visual clutter without hiding active selections.
* **Independent Hypothesis Verification**: Verified that `ClusterFilterCriteria` primary constructor must keep `minHitCount = null` so `ClusterFilterCriteria().isEmpty` and existing unit tests (`ClusterFilterCriteriaTest`) remain untouched; the default `5` applies at the ViewModel session layer.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Initialize `WorkoutClustersViewModel._filterCriteria` with `minHitCount = 5` session default.
  2. Implement `showAllLocations` toggle and $\ge 5$ starts filtering in `ClusterFilterBottomSheet` and `WorkoutFilterBottomSheet`.
  3. 9-language localization parity for `filter_locations_show_all` and `filter_locations_show_frequent`.
  4. Unit test coverage in `WorkoutClustersViewModelTest` and filter sheet contract tests.
* **Out-of-Scope Non-Goals**:
  - Modifying clustering spatial algorithms or SQLite schema.
  - Adding starts filtering to unrelated screen pickers.
* **Complexity Rating**: **2 SP (Standard)**. Multi-component UI/ViewModel improvement adhering to standard filter patterns.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-187` (*Filter Bottom Sheets*), `REQ-UI-466` (*Known Locations Starts Threshold*).
* **Historical Origin & Commit Trace**: `ATT-1402` (commit `91acde33`), `ATT-5823` (commit `3b1f3b33`).
* **Root Reason for Existing Formulation**: Sheets originally displayed all locations because early datasets were small. As athletes logged years of workouts, incidental vacation starts cluttered filter sheets.
* **Preservation of Core Invariants**: User can always reveal all locations via `"Alle anzeigen"`; active selection is never omitted; clearing cluster filter reveals all clusters; 100% 9-language parity with zero emojis (`ATT-5400`).

---

## 5. Architectural Strategy & High-Level Solution
* **ViewModel**: `WorkoutClustersViewModel` manages session `_filterCriteria = MutableStateFlow(ClusterFilterCriteria(minHitCount = 5))`. `clearFilterCriteria()` sets `minHitCount = null`.
* **Filter Sheets**: `displayedLocations = if (showAll || !hasInfrequent) knownLocations else knownLocations.filter { it.hitCount >= 5 || it == selected }`. Append toggle `FilterChip` if `hasInfrequent`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression in workout/cluster filtering predicates; active selections never hidden; no DataStore corruption.
* **Risk Rating**: **LOW** (Presentation-layer filtering and ViewModel session default; fully covered by unit tests).
