# Stage 1 Analysis: ATT-1642 - Remove Emojis and Reorder Lieblingsort and Lieblingsstrecke to Bottom of Filter Dialogs

**Ticket**: [ATT-1642](https://atrainingtracker.atlassian.net/browse/ATT-1642)  
**Sub-task**: [ATT-1654](https://atrainingtracker.atlassian.net/browse/ATT-1654) (`[Analysis]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1642`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Problem Statement & Motivation

1. In [WorkoutFilterBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt), [ClusterFilterBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterBottomSheet.kt), [ActiveFilterChipsRow.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/ActiveFilterChipsRow.kt), and [ActiveClusterFilterChipsRow.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ActiveClusterFilterChipsRow.kt), emojis (`🗺️`, `📍`) are currently placed in front of chip labels. This creates visual clutter and is inconsistent with clean Material 3 design conventions.
2. In [WorkoutFilterBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt), Favorite Locations (*Lieblingsorte*) and Favorite Tracks (*Lieblingsstrecken*) are positioned in the middle of the sheet (before Distance and Duration intervals). For athletes with many favorite tracks or locations, the chip cloud pushes subsequent essential filters (Distance presets/inputs and Duration presets/inputs) far down, requiring excessive scrolling.
3. Similarly in [ClusterFilterBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterBottomSheet.kt), Known Locations (*Lieblingsorte*) is currently placed as the first section at the top, pushing equipment, distance thresholds, and hit-count thresholds down.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Investigation of Emoji Attachments:
- **`ActiveClusterFilterChipsRow.kt`**:
  - Line 78: `"📍 ${criteria.startLocationName}"`
  - Line 80: `"📍 ${stringResource(R.string.filter_start_location)}"`
- **`ClusterFilterBottomSheet.kt`**:
  - Line 169: `label = { Text("📍 ${loc.name}") }`
- **`ActiveFilterChipsRow.kt`**:
  - Line 101: `"📍 ${criteria.startLocationName}"`
  - Line 103: `"📍 ${stringResource(R.string.filter_start_location)}"`
  - Line 116: `"🗺️ ${criteria.clusterName}"`
  - Line 118: `"🗺️ ${stringResource(R.string.my_locations)}"`
- **`WorkoutFilterBottomSheet.kt`**:
  - Line 552: `label = { Text("📍 ${loc.name}") }`
  - Line 585: `label = { Text("🗺️ ${cluster.name}") }`

### Investigation of Layout Ordering:
- In `WorkoutFilterBottomSheet.kt`:
  - Current order:
    1. Search Text (lines 351–364)
    2. Date Interval (lines 366–435)
    3. Sport Sub-Type Selection (lines 438–459)
    4. Categorized Equipment Selection (Bikes, Shoes, Other) (lines 462–487)
    5. Workout Attributes (Commute, Trainer, GPS) (lines 490–519)
    6. Favorite Locations (*Lieblingsorte*) (lines 522–557)
    7. Favorite Tracks (*Lieblingsstrecken*) (lines 560–590)
    8. Distance Interval (lines 593–661)
    9. Duration Interval (lines 664–732)
  - Target order:
    1. Search Text
    2. Date Interval
    3. Sport Sub-Type Selection
    4. Categorized Equipment Selection
    5. Workout Attributes
    6. Distance Interval
    7. Duration Interval
    8. Favorite Locations (*Lieblingsorte*)
    9. Favorite Tracks (*Lieblingsstrecken*)
- In `ClusterFilterBottomSheet.kt`:
  - Current order:
    1. Known Locations (*Lieblingsort*) (lines 140–174)
    2. Equipment Selection (lines 177–200)
    3. Minimum Reference Distance Thresholds (lines 203–226)
    4. Minimum Recordings / Hit Count Thresholds (lines 229–252)
  - Target order:
    1. Equipment Selection
    2. Minimum Reference Distance Thresholds
    3. Minimum Recordings / Hit Count Thresholds
    4. Known Locations (*Lieblingsort*)

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Remove all emojis (`📍`, `🗺️`) in front of chip labels in `WorkoutFilterBottomSheet`, `ClusterFilterBottomSheet`, `ActiveFilterChipsRow`, and `ActiveClusterFilterChipsRow`.
  * Move Favorite Locations (*Lieblingsorte*) and Favorite Tracks (*Lieblingsstrecken*) to the bottom of `WorkoutFilterBottomSheet`.
  * Move Known Locations (*Lieblingsorte*) to the bottom of `ClusterFilterBottomSheet`.
  * Update existing unit tests (`ActiveFilterChipsRowLocationTest`, `ActiveFilterChipsRowClusterTest`, `ActiveClusterFilterChipsRowLocationTest`) to assert clean labels without emojis.
  * Update requirement specifications `REQ-UI-185` and `REQ-UI-187` in `docs/requirements.md` and test specifications in `docs/tests.md`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not alter underlying filtering algorithms, spatial geofence calculation (`WorkoutClusterEngine.distanceBetween`), or cluster matching.
  * Do not change DataStore preference serialization or SQLite schema.
  * Do not introduce new string resources; reuse existing localized strings.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: `REQ-UI-185` (*Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location*) and `REQ-UI-187` (*Selection of Lieblingsorte and Lieblingsstrecken in Workout and Cluster Filter Dialogs*).
* **Historical Origin & Commit Trace**: Commits `6bfbcfc7` (ATT-1401) and `91acde33` (ATT-1402, ATT-1593) under Epic [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396).
* **Root Reason for Existing Formulation**: Emojis (📍, 🗺️) were originally added to visually differentiate spatial start location filters and route cluster filters from plain text query chips.
* **Preservation of Core Invariants**: Removing emojis simplifies UI aesthetics and complies with Material 3 typography standards. Reordering sections places bounded, frequently adjusted scalar controls (Distance, Duration) before unbounded, potentially long lists of user-created locations and tracks. All filter state bindings, dismissal flows, and 9-language translations remain 100% intact.

---

## 5. Architectural Strategy & High-Level Solution

1. **Emoji Stripping**:
   - In `ActiveClusterFilterChipsRow.kt`: Strip `"📍 "` prefix. Render `criteria.startLocationName ?: stringResource(R.string.filter_start_location)`.
   - In `ClusterFilterBottomSheet.kt`: Strip `"📍 "` prefix. Render `Text(loc.name)`.
   - In `ActiveFilterChipsRow.kt`: Strip `"📍 "` and `"🗺️ "` prefixes. Render `criteria.startLocationName ?: stringResource(R.string.filter_start_location)` and `criteria.clusterName ?: stringResource(R.string.my_locations)`.
   - In `WorkoutFilterBottomSheet.kt`: Strip `"📍 "` and `"🗺️ "` prefixes. Render `Text(loc.name)` and `Text(cluster.name)`.
2. **Reordering in Compose Hierarchy**:
   - In `WorkoutFilterBottomSheet.kt`: Move the Composable blocks for `knownLocations` and `availableClusters` directly below the Duration Interval section.
   - In `ClusterFilterBottomSheet.kt`: Move the Composable block for `knownLocations` to the bottom, below the Minimum Recordings section.
3. **Unit Test Alignment**:
   - Update `ActiveFilterChipsRowLocationTest`, `ActiveFilterChipsRowClusterTest`, and `ActiveClusterFilterChipsRowLocationTest` to assert expected strings without emoji prefixes.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Filter selection, deselection, clear-all, and apply mechanics remain 100% functional.
  2. Single-tap chip removal in active filter chip rows remains 100% functional.
  3. Zero regression in 9-language localization parity.
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** (Presentation and layout order adjustments only, zero changes to data models or business logic).
