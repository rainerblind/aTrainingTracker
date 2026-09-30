# Stage 3: Implementation Plan - ATT-1642: Remove Emojis and Reorder Lieblingsort and Lieblingsstrecke to Bottom of Filter Dialogs

**Ticket**: [ATT-1642](https://atrainingtracker.atlassian.net/browse/ATT-1642)  
**Sub-task**: [ATT-1656](https://atrainingtracker.atlassian.net/browse/ATT-1656) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Requirement Mapping**: `REQ-UI-194` (*Filter Dialogs: Clean Chip Formatting (Emoji Removal) and Prioritized Section Ordering*)  
**Test Mapping**: `TST-UI-148`  
**Branch**: `feature/ATT-1642`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Problem Description & Background

In the filter bottom sheets (`WorkoutFilterBottomSheet` and `ClusterFilterBottomSheet`) and the active filter chip indicators (`ActiveFilterChipsRow` and `ActiveClusterFilterChipsRow`), chip labels currently prepend emojis (`📍 `, `🗺️ `). This adds visual noise and violates Material 3 typography standards.

Furthermore, placing "Lieblingsort" and "Lieblingsstrecke" ahead of distance and duration thresholds in `WorkoutFilterBottomSheet` (and placing "Lieblingsort" at the very top of `ClusterFilterBottomSheet`) forces athletes with numerous saved tracks or locations to scroll past large chip lists to adjust scalar thresholds. Reordering them to the bottom optimizes the visual layout hierarchy.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-194` (*Filter Dialogs: Clean Chip Formatting (Emoji Removal) and Prioritized Section Ordering*)
* **Test Mapping**: `TST-UI-148` (*Filter Dialogs Clean Chip Formatting & Prioritized Section Ordering Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Filter Semantics & State Persistence**: All filter criteria evaluation (`matches`), DataStore persistence, and clearing behaviors remain 100% intact.
2. **Single-Tap Dismissal**: Tapping the remove 'X' icon on active filter chips continues to clear the respective criterion immediately.
3. **9-Language Localization Parity**: Existing localized strings are retained without modification.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `ActiveFilterChipsRow.kt` & `ActiveClusterFilterChipsRow.kt`
- Remove the `"📍 "` and `"🗺️ "` prefix strings when rendering chip labels for `startLocation` and `cluster`.
- Render pure text: `criteria.startLocationName ?: stringResource(R.string.filter_start_location)` and `criteria.clusterName ?: stringResource(R.string.my_locations)`.

### Component 2: `WorkoutFilterBottomSheet.kt`
- Remove `"📍 "` and `"🗺️ "` prefixes from `FilterChip` labels in the Favorite Locations and Favorite Tracks sections.
- Reorder the vertical `Column` sections:
  1. Search Text
  2. Date Interval
  3. Sport Sub-Type Selection
  4. Categorized Equipment Selection (Bikes, Shoes, Other)
  5. Workout Attributes (Commute, Trainer, GPS)
  6. Distance Interval
  7. Duration Interval
  8. Favorite Locations (*Lieblingsorte*)
  9. Favorite Tracks (*Lieblingsstrecken*)

### Component 3: `ClusterFilterBottomSheet.kt`
- Remove `"📍 "` prefix from `FilterChip` labels in the Known Locations section.
- Reorder the vertical `Column` sections:
  1. Equipment Selection
  2. Minimum Reference Distance Thresholds
  3. Minimum Recordings / Hit Count Thresholds
  4. Known Locations (*Lieblingsort*)

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Emoji Removal in Active Filter Chips Rows
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/ActiveFilterChipsRow.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ActiveClusterFilterChipsRow.kt`
* **Changes**: Strip emoji string literals from label computations.

### Step 2: Emoji Removal & Section Reordering in `WorkoutFilterBottomSheet.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt`
* **Changes**: Strip emojis from location/cluster chip labels and relocate the two Composable blocks below the Duration Interval section.

### Step 3: Emoji Removal & Section Reordering in `ClusterFilterBottomSheet.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterBottomSheet.kt`
* **Changes**: Strip emojis from location chip labels and relocate the Known Locations Composable block to the bottom of the column.

### Step 4: Update Unit Tests
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/ActiveFilterChipsRowLocationTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/ActiveFilterChipsRowClusterTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ActiveClusterFilterChipsRowLocationTest.kt`
* **Changes**: Update expected label assertions to check for clean strings without emojis.
* **Targeted Test Command**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.ActiveFilterChipsRow*" --tests "com.atrainingtracker.trainingtracker.ui.clusters.ActiveClusterFilterChipsRow*"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Execute targeted unit tests during Stage 4.
  - Run the full clean-room regression test suite (`./gradlew testDebugUnitTest`) during Stage 5.
* **Rollback Plan**:
  - All changes are isolated on `feature/ATT-1642` and can be reverted cleanly via git without affecting `sprint/2026-40.5` or `develop`.
