# Stage 3: Implementation Plan - ATT-5822: Favorite Routes & Filter Sheets Starts and Hit Count Default Filtering

**Ticket**: [ATT-5822](https://rainerblind.atlassian.net/browse/ATT-5822)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.11`  
**Requirement Mapping**: `REQ-UI-467`  
**Test Mapping**: `TST-UI-467`  
**Branch**: `feature/ATT-5822`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-07  

---

## 1. Problem Description & Background
Routes recorded 1–4 times do not constitute genuine "Lieblingsstrecken", and locations with 1–4 starts flood "Startet bei (Lieblingsorte)" in filter dialogs. The system SHALL default Favorite Routes filtering to $\ge 5$ recordings on screen entry and filter start locations to $\ge 5$ starts with a compact toggle chip (Sprint 2026-45.11, ATT-5822, extending `REQ-UI-187`, `REQ-UI-417`, `REQ-UI-466`).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-467` (*Favorite Routes & Filter Sheets Hit Count Default and Starts-Filtered Location Selection*)
* **Test Mapping**: `TST-UI-467` (*Workout Clusters & Filter Sheets Starts and Hit Count Default Filtering Verification Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Selection Preservation**: Infrequent locations ($< 5$ starts) already selected by the user MUST NOT be hidden.
2. **Accessible Full Dataset**: Infrequent locations remain accessible via 1-tap `"Alle anzeigen"` toggle.
3. **Filter Reset Contract**: Clearing cluster filters (`×` on chip or reset button) sets `minHitCount = null`.
4. **Localization Parity**: 100% 9-language translation parity with zero decorative inline emojis (`ATT-5400`).

---

## 4. Proposed Architectural Changes
* **ViewModel (`WorkoutClustersViewModel.kt`)**: Manage session-scoped `_filterCriteria = MutableStateFlow(ClusterFilterCriteria(minHitCount = 5))`. Synchronous `setFilterCriteria`, `updateFilterCriteria`, and `clearFilterCriteria` (resetting `minHitCount = null`).
* **UI (`ClusterFilterBottomSheet.kt`, `WorkoutFilterBottomSheet.kt`)**: Filter displayed locations: `loc.hitCount >= 5 || isSelected || showAllLocations`. When infrequent locations exist, render toggle `FilterChip` (`filter_locations_show_all` / `filter_locations_show_frequent`).
* **Localization (`strings.xml` across all 9 locales)**: Add `filter_locations_show_all` and `filter_locations_show_frequent`.
* **UI Consistency (Rule 23)**: Reuses `FilterChip`, `FlowRow`, and standard Material 3 tokens. No one-off styling.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Localized String Resources (all 9 locales)
* Files: `app/src/main/res/values*/strings.xml` (EN, DE, ES, FR, IT, JA, NL, PL, PT)
* Add `filter_locations_show_all` and `filter_locations_show_frequent` with 0 emojis.

### Step 2: Update `WorkoutClustersViewModel.kt` Session Default
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt`
* Change `filterCriteria` to `_filterCriteria = MutableStateFlow(ClusterFilterCriteria(minHitCount = 5))`.

### Step 3: Implement Location Starts Filtering in Bottom Sheets
* Files: `ClusterFilterBottomSheet.kt`, `WorkoutFilterBottomSheet.kt`
* Filter `"Startet bei"` chips by `hitCount >= 5 || isSelected || showAllLocations` and add toggle chip.

### Step 4: Unit & Contract Tests
* Files: `WorkoutClustersViewModelTest.kt`, `ClusterFilterBottomSheetLocationStartsTest.kt`, `WorkoutFilterBottomSheetLocationStartsTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests and `verify_requirement_governance.py`.
* **Rollback**: Branch isolation (`feature/ATT-5822`) allows revert via `git restore .`.
