# Stage 2: Requirement & Test Specification - ATT-1642: Remove Emojis and Reorder Lieblingsort and Lieblingsstrecke to Bottom of Filter Dialogs

**Ticket**: [ATT-1642](https://atrainingtracker.atlassian.net/browse/ATT-1642)  
**Sub-task**: [ATT-1655](https://atrainingtracker.atlassian.net/browse/ATT-1655) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Requirement Mapping**: `REQ-UI-194` (*Filter Dialogs: Clean Chip Formatting (Emoji Removal) and Prioritized Section Ordering*)  
**Test Spec ID**: `TST-UI-148`  
**Branch**: `feature/ATT-1642`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Requirement Specification (REQ-UI-194)

### 1.1 Problem Statement & Rationale
In [WorkoutFilterBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt), [ClusterFilterBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterBottomSheet.kt), [ActiveFilterChipsRow.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/ActiveFilterChipsRow.kt), and [ActiveClusterFilterChipsRow.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ActiveClusterFilterChipsRow.kt), emojis (`🗺️`, `📍`) were prepended to chip labels. This creates visual clutter and is inconsistent with clean Material 3 design standards.

Furthermore, placing "Lieblingsorte" and "Lieblingsstrecken" ahead of scalar thresholds (Distance and Duration intervals) in `WorkoutFilterBottomSheet` (and at the top of `ClusterFilterBottomSheet`) pushes bounded, frequently used filter criteria far down the scrollable view for athletes with many saved locations or route clusters.

### 1.2 Functional & Architectural Requirements
1. **Clean Chip Label Formatting (Emoji Removal)**:
   - In `WorkoutFilterBottomSheet.kt`, chip labels for Favorite Locations (*Lieblingsorte*) SHALL render `loc.name` without any emoji prefix (`📍 ` removed).
   - In `WorkoutFilterBottomSheet.kt`, chip labels for Favorite Tracks (*Lieblingsstrecken*) SHALL render `cluster.name` without any emoji prefix (`🗺️ ` removed).
   - In `ClusterFilterBottomSheet.kt`, chip labels for Known Locations (*Lieblingsorte*) SHALL render `loc.name` without any emoji prefix (`📍 ` removed).
   - In `ActiveFilterChipsRow.kt`, the active chip for starting location SHALL format as `criteria.startLocationName ?: stringResource(R.string.filter_start_location)` without any emoji prefix (`📍 ` removed).
   - In `ActiveFilterChipsRow.kt`, the active chip for route cluster SHALL format as `criteria.clusterName ?: stringResource(R.string.my_locations)` without any emoji prefix (`🗺️ ` removed).
   - In `ActiveClusterFilterChipsRow.kt`, the active chip for starting location SHALL format as `criteria.startLocationName ?: stringResource(R.string.filter_start_location)` without any emoji prefix (`📍 ` removed).
2. **Prioritized Filter Section Ordering**:
   - In `WorkoutFilterBottomSheet.kt`, filter sections SHALL follow a prioritized layout order: (1) Search Text, (2) Date Interval, (3) Sport Sub-Type Selection, (4) Categorized Equipment Selection, (5) Workout Attributes, (6) Distance Interval, (7) Duration Interval, (8) Favorite Locations (*Lieblingsorte*), (9) Favorite Tracks (*Lieblingsstrecken*). Favorite Locations and Favorite Tracks SHALL appear as the final sections at the bottom of the bottom sheet.
   - In `ClusterFilterBottomSheet.kt`, filter sections SHALL follow a prioritized layout order: (1) Equipment Selection, (2) Minimum Reference Distance Thresholds, (3) Minimum Recordings / Hit Count Thresholds, (4) Known Locations (*Lieblingsorte*). Known Locations SHALL appear as the final section at the bottom of the bottom sheet.
3. **100% 9-Language Localization Parity**:
   - All filter section titles and chip descriptors SHALL utilize existing localized string resources across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing entries.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Workout Filter Sheet Formatting & Order)**:
  * *Given* an athlete opens `WorkoutFilterBottomSheet`,
  * *When* inspecting the Favorite Locations and Favorite Tracks sections,
  * *Then* chip labels SHALL NOT display emojis (neither `📍` nor `🗺️`), and both sections SHALL be situated at the bottom of the dialog below Distance and Duration intervals.
* **Criterion 2 (Cluster Filter Sheet Formatting & Order)**:
  * *Given* an athlete opens `ClusterFilterBottomSheet`,
  * *When* inspecting the Known Locations section,
  * *Then* chip labels SHALL NOT display emoji `📍`, and the section SHALL be situated at the bottom of the dialog below Equipment, Distance Thresholds, and Hit Count Thresholds.
* **Criterion 3 (Active Filter Strip Formatting)**:
  * *Given* an active location or cluster filter applied to workouts or clusters,
  * *When* viewing `ActiveFilterChipsRow` or `ActiveClusterFilterChipsRow`,
  * *Then* active chip labels SHALL NOT display emojis (neither `📍` nor `🗺️`).

### 1.4 System Invariants
- Zero regression in filter selection/deselection, clear all, and apply behavior.
- Single-tap dismissal of active filter chips remains 100% functional.
- Zero change to DataStore persistence or database schemas.
- 9-language localization parity strictly preserved.

---

## 2. Test Specification (TST-UI-148)

### Test Case 1: ActiveFilterChipsRow Location Chip Clean Label (`TST-UI-148.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/ActiveFilterChipsRowLocationTest.kt`
* **Preconditions**: `WorkoutFilterCriteria` with `startLocationName = "Zuhause"`.
* **Action**: Evaluate label formatting for starting location chip.
* **Expected Result**: Assert `label == "Zuhause"` (no `📍` prefix). When name is null, assert `label == "Start Location"`.

### Test Case 2: ActiveFilterChipsRow Cluster Chip Clean Label (`TST-UI-148.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/ActiveFilterChipsRowClusterTest.kt`
* **Preconditions**: `WorkoutFilterCriteria` with `clusterName = "Isarrunde"`.
* **Action**: Evaluate label formatting for cluster chip.
* **Expected Result**: Assert `label == "Isarrunde"` (no `🗺️` prefix). When name is null, assert fallback string without emoji.

### Test Case 3: ActiveClusterFilterChipsRow Location Chip Clean Label (`TST-UI-148.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ActiveClusterFilterChipsRowLocationTest.kt`
* **Preconditions**: `ClusterFilterCriteria` with `startLocationName = "Büro"`.
* **Action**: Evaluate label formatting for start location chip in cluster list.
* **Expected Result**: Assert `label == "Büro"` (no `📍` prefix).

### Test Case 4: 9-Language Localization & Specifier Audit (`TST-UI-148.4`)
* **Scope**: Localization Parity Test
* **Goal**: Verify string presence across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries.

### Test Case 5: Clean-Room Regression Suite (`TST-UI-148.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-148.1` | Unit | `ActiveFilterChipsRowLocationTest` | `REQ-UI-194` | Specified |
| `TST-UI-148.2` | Unit | `ActiveFilterChipsRowClusterTest` | `REQ-UI-194` | Specified |
| `TST-UI-148.3` | Unit | `ActiveClusterFilterChipsRowLocationTest` | `REQ-UI-194` | Specified |
| `TST-UI-148.4` | Localization | `TranslationParityTest` | `REQ-UI-194`, `REQ-UI-106` | Specified |
| `TST-UI-148.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
