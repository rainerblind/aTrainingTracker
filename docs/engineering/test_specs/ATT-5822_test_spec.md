# Stage 2: Requirement & Test Specification - ATT-5822: Favorite Routes & Filter Sheets Starts and Hit Count Default Filtering

**Ticket**: [ATT-5822](https://rainerblind.atlassian.net/browse/ATT-5822)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.11`  
**Requirement Mapping**: `REQ-UI-467` (*Favorite Routes & Filter Sheets Hit Count Default and Starts-Filtered Location Selection*)  
**Test Spec ID**: `TST-UI-467`  
**Branch**: `feature/ATT-5822`  
**Author**: AI Agent 1 (`subagent_flash`)  
**Date**: 2026-11-07  

---

## 1. Requirement Specification (REQ-UI-467)

### 1.1 Problem Statement & Rationale
Routes recorded 1–4 times are not genuine "Lieblingsstrecken" (Favorite Routes), and locations with 1–4 starts (e.g. vacation points) flood the "Startet bei (Lieblingsorte)" chip selection in filter dialogs. The system SHALL default Favorite Routes filtering to $\ge 5$ recordings on screen entry and filter start locations to $\ge 5$ starts with a compact toggle chip (Sprint 2026-45.11, ATT-5822, extending `REQ-UI-187`, `REQ-UI-417`, `REQ-UI-466`).

### 1.2 Functional & Architectural Requirements
1. *Favorite Routes Default Minimum Hit Count (`REQ-UI-467.1`)*: On initialization of `WorkoutClustersViewModel`, active filter criteria SHALL default to `minHitCount = 5` (`ClusterFilterCriteria(minHitCount = 5)`). In `ActiveClusterFilterChipsRow`, the chip (*"≥ 5 Aufzeichnungen"*) SHALL display immediately with 1-tap removal.
2. *Start Location Selection Filtering (`REQ-UI-467.2`)*: In `ClusterFilterBottomSheet` and `WorkoutFilterBottomSheet`, the "Startet bei" section SHALL filter `knownLocations` by default to `loc.hitCount >= 5` or active selection (`loc == selectedLocation`).
3. *Show All / Frequent Locations Toggle (`REQ-UI-467.3`)*: When locations with $< 5$ starts exist, a toggle chip SHALL toggle between `"Alle anzeigen"` (`filter_locations_show_all`) and `"Nur ≥ 5 Starts"` (`filter_locations_show_frequent`).
4. *9-Language Localization Parity (`REQ-UI-467.4`)*: New string keys SHALL maintain 100% parity across all 9 locales with zero inline emojis (`ATT-5400`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Favorite Routes Default)**: *Given* the athlete opens Favorite Routes, *when* rendered, *then* `minHitCount = 5` is active and `"≥ 5 Aufzeichnungen"` is displayed in the active chips row.
* **Criterion 2 (Clear Filter)**: *Given* active `minHitCount = 5`, *when* the athlete taps `×` on the chip or clears all filters, *then* `minHitCount` is set to `null` and all routes are shown.
* **Criterion 3 (Start Locations Filter & Toggle)**: *Given* filter bottom sheet with locations having $< 5$ starts, *when* viewed, *then* only locations with $\ge 5$ starts (and selected location) are shown with an `"Alle anzeigen"` chip that expands all locations on tap.

### 1.4 System Invariants
* DataStore preference criteria are not corrupted; toggling locations does not deselect active filter coordinate; zero regressions in `ClusterFilterCriteria.matches()`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Extending `REQ-UI-187`, `REQ-UI-417`, and `REQ-UI-466`.
* **Historical Origin & Commit Trace**: `ATT-1402` (commit `91acde33`), `ATT-5823` (commit `3b1f3b33`).
* **Root Reason for Existing Formulation**: Filter sheets initially rendered all locations because datasets were small; as athlete histories grew, 1-start vacation locations flooded the sheets.
* **Preservation of Core Invariants**: Full dataset accessible via 1-tap toggle; active selection never hidden; clearing cluster filter reveals all clusters; 100% 9-language parity with zero emojis.

---

## 2. Test Specification (TST-UI-467)

### Test Case 1: WorkoutClustersViewModel Default Hit Count (`[TST-UI-467.1]`)
* **Scope**: ViewModel Unit Test (`WorkoutClustersViewModelTest.kt`)
* **Preconditions**: Fresh `WorkoutClustersViewModel` instance.
* **Action & Expected Result**: Assert `filterCriteria.value.minHitCount == 5`; calling `clearFilterCriteria()` sets `minHitCount == null`.

### Test Case 2: ClusterFilterBottomSheet Location Starts Filtering (`[TST-UI-467.2]`)
* **Scope**: UI Contract Test (`ClusterFilterBottomSheetLocationStartsTest.kt`)
* **Expected Result**: Locations with $< 5$ starts hidden by default; selected location preserved; toggle chip expands/collapses list.

### Test Case 3: WorkoutFilterBottomSheet Location Starts Filtering (`[TST-UI-467.3]`)
* **Scope**: UI Contract Test (`WorkoutFilterBottomSheetLocationStartsTest.kt`)
* **Expected Result**: Locations with $< 5$ starts hidden by default; selected location preserved; toggle chip expands/collapses list.

### Test Case 4: 9-Language Localization & Specifier Audit (`[TST-UI-467.4]`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Expected Result**: `filter_locations_show_all` and `filter_locations_show_frequent` present across all 9 locales with zero emojis.

### Test Case 5: Tier 1 Targeted Regression Suite (`[TST-UI-467.5]`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.*" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-467.1]` | Unit | `WorkoutClustersViewModel.filterCriteria` | `REQ-UI-467.1` | Specified |
| `[TST-UI-467.2]` | Contract | `ClusterFilterBottomSheet` location chips | `REQ-UI-467.2`, `REQ-UI-467.3` | Specified |
| `[TST-UI-467.3]` | Contract | `WorkoutFilterBottomSheet` location chips | `REQ-UI-467.2`, `REQ-UI-467.3` | Specified |
| `[TST-UI-467.4]` | Localization | `TranslationParityTest` | `REQ-UI-467.4` | Specified |
| `[TST-UI-467.5]` | Regression | Clusters, Workout List, Build | `REQ-PRO-001` | Specified |
