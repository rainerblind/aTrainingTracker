# Stage 2: Requirement & Test Specification - ATT-1593: Selection of Lieblingsorte & Lieblingsstrecken in Filter Dialogs

**Ticket**: [ATT-1593](https://atrainingtracker.atlassian.net/browse/ATT-1593)  
**Sub-task**: [ATT-1602](https://atrainingtracker.atlassian.net/browse/ATT-1602) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.4`  
**Requirement Mapping**: `REQ-UI-187` (*Selection of Lieblingsorte and Lieblingsstrecken in Workout and Cluster Filter Dialogs*)  
**Test Spec ID**: `TST-UI-141` (`TST-UI-141.1`, `TST-UI-141.2`, `TST-UI-141.3`, `TST-UI-141.4`, `TST-UI-141.5`, `TST-UI-141.6`)  
**Branch**: `feature/ATT-1593`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Requirement Specification (REQ-UI-187)

### 1.1 Problem Statement & Rationale
Athletes need direct, interactive selection of spatial start locations (*Lieblingsorte*) and favorite route families (*Lieblingsstrecken*) within filter dialog sheets. Currently, while underlying geofence distance predicates exist in `WorkoutFilterCriteria` (REQ-UI-185) and `ClusterFilterCriteria` (REQ-UI-186) for card drill-down, neither `WorkoutFilterBottomSheet` nor `ClusterFilterBottomSheet` provides chip selection sections for favorite locations. Furthermore, `WorkoutFilterCriteria` lacks route cluster filtering dimensions (`clusterId`, `clusterName`), preventing athletes from filtering their workout logs by recurring route families.

### 1.2 Functional & Architectural Requirements
The system SHALL provide interactive selection of favorite start locations (*Lieblingsorte*) and route clusters (*Lieblingsstrecken*) across workout and cluster filter dialogs (ATT-1593):

1. *Workout Filter Domain Extension (`WorkoutFilterCriteria.kt`)*:
   - `WorkoutFilterCriteria` SHALL define nullable properties: `clusterId: Long? = null` and `clusterName: String? = null`.
   - *Predicate Evaluation (`matches`)*: When `clusterId != null`, the system SHALL evaluate `workout.clusterId`. If `workout.clusterId != clusterId`, `matches` SHALL return `false`.
   - `activeFilterCount` SHALL increment by 1 when `clusterId != null`.
   - `toJson()` and `fromJson()` SHALL serialize and deserialize `clusterId` and `clusterName` losslessly for DataStore preference persistence.
2. *Workout Filter Bottom Sheet Enhancement (`WorkoutFilterBottomSheet.kt`)*:
   - `WorkoutFilterBottomSheet` SHALL accept `knownLocations: List<KnownLocationItem> = emptyList()` and `availableClusters: List<WorkoutCluster> = emptyList()`.
   - When `knownLocations` is non-empty, the sheet SHALL render a "Lieblingsorte" (*Favorite Locations*) section with single-selectable `FilterChip` items for each location.
   - When `availableClusters` is non-empty, the sheet SHALL render a "Lieblingsstrecken" (*Favorite Tracks*) section with single-selectable `FilterChip` items for each cluster.
   - Tapping an already selected chip SHALL deselect it.
   - `onApply` SHALL preserve and propagate selected `startLocation*` and `cluster*` criteria.
   - `onClearAll` SHALL reset both location and cluster selections.
3. *Lieblingsstrecken Filter Bottom Sheet Enhancement (`ClusterFilterBottomSheet.kt`)*:
   - `ClusterFilterBottomSheet` SHALL accept `knownLocations: List<KnownLocationItem> = emptyList()`.
   - When `knownLocations` is non-empty, the sheet SHALL render a "Lieblingsorte" (*Favorite Locations*) section with single-selectable `FilterChip` items.
   - Selecting a location chip SHALL bind its coordinates (`startLocationLat`, `startLocationLng`), radius (`startLocationRadiusM`), and display name (`startLocationName`) to `ClusterFilterCriteria`.
   - `onApply` SHALL propagate the spatial start location criteria, and `onClearAll` SHALL reset them.
4. *Active Filter Chips Row Presentation*:
   - In `ActiveFilterChipsRow.kt` (workouts), when `clusterId != null` or `!clusterName.isNullOrBlank()`, the system SHALL display an active chip: `"🗺️ ${criteria.clusterName ?: stringResource(R.string.my_locations)}"`. Tapping remove SHALL invoke `onRemoveCluster: () -> Unit`.
   - In `ActiveClusterFilterChipsRow.kt` (clusters), when `startLocationLat != null && startLocationLng != null` or `!startLocationName.isNullOrBlank()`, the system SHALL display an active chip: `"📍 ${criteria.startLocationName ?: stringResource(R.string.filter_start_location)}"`. Tapping remove SHALL invoke `onRemoveStartLocation: () -> Unit`.
5. *Screen & ViewModel Data Hoisting*:
   - `WorkoutSummariesViewModel` SHALL collect `KnownLocationsRepository.locationsFlow` and `WorkoutClusterRepository.allClusters`, passing them to `WorkoutSummariesTabbedScreen` -> `WorkoutTabsScreen` -> `WorkoutFilterBottomSheet`.
   - `WorkoutClustersViewModel` SHALL collect `KnownLocationsRepository.locationsFlow`, passing them to `WorkoutClustersTabsScreen` -> `ClusterFilterBottomSheet`.
6. *100% 9-Language Localization Parity*:
   - Reused string resources `known_locations_title`, `my_locations`, and `filter_start_location` SHALL be verified across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing entries.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Net-new requirement (REQ-UI-187), complementing `REQ-UI-185` (*Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location*) and `REQ-UI-186` (*Lieblingsorte & Lieblingsstrecken Bridge*).
* **Historical Origin & Commit Trace**: Ticket `ATT-1401` (commit `6bfbcfc7`) and `ATT-1402` (commit `91acde33`) under Epic `ATT-1396`.
* **Root Reason for Existing Formulation**: Earlier tickets focused on establishing the spatial distance calculation engine and 1-tap navigation from list cards. Integrating interactive chips into the modal bottom sheets was deferred to maintain small, focused deliverables.
* **Preservation of Core Invariants**: 1-tap card drill-down navigation remains 100% functional; DataStore JSON serialization defaults to null for backward compatibility; geodetic distance calculations via `WorkoutClusterEngine.distanceBetween` remain unchanged; 9-language localization parity strictly maintained.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Workout Filter Lieblingsorte Selection)**:
  * *Given* an athlete opening `WorkoutFilterBottomSheet` in the Workouts tab with available known locations,
  * *When* selecting a location chip (e.g. "Zuhause") and applying the filter,
  * *Then* the workout list SHALL filter to workouts starting within the geofence radius of "Zuhause", and `ActiveFilterChipsRow` SHALL display `"📍 Zuhause"`.
* **Criterion 2 (Workout Filter Lieblingsstrecken Selection)**:
  * *Given* an athlete opening `WorkoutFilterBottomSheet` with available route clusters,
  * *When* selecting a cluster chip (e.g. "Isarrunde") and applying the filter,
  * *Then* the workout list SHALL filter strictly to workouts matching `clusterId`, and `ActiveFilterChipsRow` SHALL display `"🗺️ Isarrunde"`.
* **Criterion 3 (Cluster Filter Lieblingsorte Selection)**:
  * *Given* an athlete opening `ClusterFilterBottomSheet` in the Lieblingsstrecken tab,
  * *When* selecting a location chip (e.g. "Büro") and applying the filter,
  * *Then* the cluster list SHALL filter to route clusters starting within the geofence radius of "Büro", and `ActiveClusterFilterChipsRow` SHALL display `"📍 Büro"`.
* **Criterion 4 (Deselection & Clear-All)**:
  * *Given* active location or cluster filter chips in either filter sheet,
  * *When* the athlete taps the chip again or taps "Alle zurücksetzen",
  * *Then* the corresponding filter criteria SHALL be cleared.
* **Criterion 5 (Active Chip Row Dismissal)**:
  * *Given* an active `"🗺️ Isarrunde"` chip in the workout list or `"📍 Büro"` chip in the cluster list,
  * *When* the athlete taps the remove 'X' icon on the chip,
  * *Then* that specific filter dimension SHALL be cleared while preserving all other active criteria.

---

## 2. Test Specification (TST-UI-141)

### Test Case 1: Workout Filter Criteria Domain & Persistence Tests (`[TST-UI-141.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterCriteriaClusterTest.kt`
* **Test Procedures**:
  1. Verify `matches` returns `true` when `workout.clusterId == criteria.clusterId`.
  2. Verify `matches` returns `false` when `workout.clusterId != criteria.clusterId`.
  3. Verify `matches` returns `true` when `criteria.clusterId == null` regardless of workout cluster.
  4. Verify `activeFilterCount` increments by 1 when `clusterId != null`.
  5. Verify round-trip JSON serialization and deserialization retains `clusterId` and `clusterName`.
  6. Verify legacy JSON strings lacking `clusterId` deserialize cleanly with `clusterId == null`.

### Test Case 2: Workout Filter Bottom Sheet Component Tests (`[TST-UI-141.2]`)
* **Scope**: Composable Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheetLocationClusterTest.kt`
* **Test Procedures**:
  1. Verify "Lieblingsorte" section renders `FilterChip`s for each supplied `KnownLocationItem`.
  2. Verify "Lieblingsstrecken" section renders `FilterChip`s for each supplied `WorkoutCluster`.
  3. Verify clicking a location chip selects it and toggling it again deselects it.
  4. Verify clicking a cluster chip selects it and toggling it again deselects it.
  5. Verify `onApply` bundles both selected location and cluster into the applied criteria.
  6. Verify `onClearAll` resets both location and cluster local states.

### Test Case 3: Cluster Filter Bottom Sheet Component Tests (`[TST-UI-141.3]`)
* **Scope**: Composable Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterBottomSheetLocationTest.kt`
* **Test Procedures**:
  1. Verify "Lieblingsorte" section renders `FilterChip`s for each supplied `KnownLocationItem`.
  2. Verify selecting a location chip sets `startLocationLat`, `Lng`, `RadiusM`, `Name`.
  3. Verify deselecting a location chip clears `startLocation*`.
  4. Verify `onApply` and `onClearAll` handle spatial location fields properly.

### Test Case 4: Active Filter Chips Rows Integration Tests (`[TST-UI-141.4]`)
* **Scope**: Composable Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/ActiveFilterChipsRowClusterTest.kt` and `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ActiveClusterFilterChipsRowLocationTest.kt`
* **Test Procedures**:
  1. In `ActiveFilterChipsRow`, verify `"🗺️ ${clusterName}"` chip renders when `clusterId` is active, and clicking remove invokes `onRemoveCluster`.
  2. In `ActiveClusterFilterChipsRow`, verify `"📍 ${startLocationName}"` chip renders when `startLocationLat` is active, and clicking remove invokes `onRemoveStartLocation`.

### Test Case 5: 9-Language Localization Audit (`[TST-UI-141.5]`)
* **Scope**: Localization Parity Test
* **Target**: Verify `known_locations_title`, `my_locations`, and `filter_start_location` across all 9 locales:
  - `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`
* **Expected Result**: 100% parity, zero missing entries.

### Test Case 6: Clean-Room Full Suite Regression Execution (`[TST-UI-141.6]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% test pass rate across all modules with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-141.1]` | Unit | `WorkoutFilterCriteria.kt` | `REQ-UI-187` | Specified |
| `[TST-UI-141.2]` | UI / Unit | `WorkoutFilterBottomSheet.kt` | `REQ-UI-187` | Specified |
| `[TST-UI-141.3]` | UI / Unit | `ClusterFilterBottomSheet.kt` | `REQ-UI-187` | Specified |
| `[TST-UI-141.4]` | UI / Unit | `ActiveFilterChipsRow.kt`, `ActiveClusterFilterChipsRow.kt` | `REQ-UI-187` | Specified |
| `[TST-UI-141.5]` | Localization | `strings.xml` (all 9 locales) | `REQ-UI-187`, `REQ-UI-106` | Specified |
| `[TST-UI-141.6]` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Specified |
