# Stage 3: Implementation Plan - ATT-5888: Uniform 'Clear all' Action in ActiveFilterChipsRow

**Ticket**: [ATT-5888](https://atrainingtracker.atlassian.net/browse/ATT-5888)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5888`  
**Requirement Mapping**: `REQ-UI-468` | **Test Mapping**: `TST-UI-468`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-08  

---

## 1. Problem Description & Background
In `ActiveFilterChipsRow.kt` (Workouts), `ActiveSegmentFilterChipsRow.kt` (Segments), `ActiveRouteFilterChipsRow.kt` (Routes), `ActiveClusterFilterChipsRow.kt` (Favorite Routes), and `ActiveDeviceFilterChipsRow.kt` (Sensors), the "Clear all" (`R.string.filter_clear_all`) `TextButton` is guarded by `criteria.activeFilterCount > 1` (or `>= 2`), hiding the 1-tap reset action when exactly 1 filter is active (e.g. `minHitCount = 5` on Favorite Routes). Removing these guards aligns all 5 screens with `ActiveKnownLocationsFilterChipsRow.kt`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-468` (*Uniform 'Clear all' Action Across Active Filter Chips Rows*)
* **Test Mapping**: `TST-UI-468` (*Uniform Active Filter Chips Clear All Verification Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Empty State Suppression**: When `criteria.isEmpty == true` (`activeFilterCount == 0`), the composable returns immediately, rendering zero layout elements.
2. **Behavioral Continuity**: Individual chip dismissal callbacks (`onRemove*`) and criteria models remain strictly unchanged.
3. **UI Consistency (Rule 23)**: `TextButton` uses `Modifier.padding(start = 4.dp)`, `MaterialTheme.typography.labelMedium`, `color = MaterialTheme.colorScheme.primary`, and `R.string.filter_clear_all`.

---

## 4. Proposed Architectural Changes

### Component 1: Workouts (`ActiveFilterChipsRow.kt`)
* Remove `if (criteria.activeFilterCount > 1)` wrapper around `item("clearAll")` (line 298).

### Component 2: Segments (`ActiveSegmentFilterChipsRow.kt`)
* Remove `if (criteria.activeFilterCount > 1)` wrapper around `item("clearAll")` (line 126).

### Component 3: Routes (`ActiveRouteFilterChipsRow.kt`)
* Remove `if (criteria.activeFilterCount > 1)` wrapper around `item("clearAll")` (line 131).

### Component 4: Favorite Routes (`ActiveClusterFilterChipsRow.kt`)
* Remove `if (criteria.activeFilterCount >= 2)` wrapper around `item(key = "clear_all")` (line 146) and add `Modifier.padding(start = 4.dp)` for spacing parity.

### Component 5: Sensors (`ActiveDeviceFilterChipsRow.kt`)
* Remove `if (criteria.activeFilterCount > 1)` wrapper around `item("clearAll")` (line 103).

### Component 6: Contract Test Suite (`ActiveFilterChipsRowClearAllContractTest.kt`)
* Assert that all 6 `Active*FilterChipsRow` files contain `R.string.filter_clear_all`, contain `if (criteria.isEmpty) return`, and forbid `activeFilterCount > 1` and `activeFilterCount >= 2`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Remove Plural Guards in the 5 Leaf Composables
* Files: `ActiveFilterChipsRow.kt`, `ActiveSegmentFilterChipsRow.kt`, `ActiveRouteFilterChipsRow.kt`, `ActiveClusterFilterChipsRow.kt`, `ActiveDeviceFilterChipsRow.kt`.

### Step 2: Implement Contract Test
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/common/filters/ActiveFilterChipsRowClearAllContractTest.kt`.

### Step 3: Targeted Verification & Governance Suite
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.common.filters.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.*" --tests "com.atrainingtracker.trainingtracker.ui.segments.segmentlist.*" --tests "com.atrainingtracker.trainingtracker.ui.routes.*" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.banalservice.ui.devices.devicelist.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* Command: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit/contract tests and living docs governance.
* **Rollback**: Branch isolation (`feature/ATT-5888`) allows instant revert.
