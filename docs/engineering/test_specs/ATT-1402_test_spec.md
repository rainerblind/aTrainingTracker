# Stage 2: Requirement & Test Specification - ATT-1402: [Feature] Lieblingsorte & Lieblingsstrecken Bridge: Starting Location Association, Invariant & UI Integration

**Ticket**: [ATT-1402](https://atrainingtracker.atlassian.net/browse/ATT-1402)  
**Sub-task**: [ATT-1587](https://atrainingtracker.atlassian.net/browse/ATT-1587) (`[Test-Spec]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `sprint/2026-40.3`  
**Requirement Mapping**: `REQ-UI-186` (*Lieblingsorte & Lieblingsstrecken Bridge: Starting Location Association, Invariant & UI Integration*)  
**Test Spec ID**: `TST-UI-139`  
**Branch**: `feature/ATT-1402`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Requirement Specification (REQ-UI-186)

### 1.1 Problem Statement & Rationale
In `aTrainingTracker`, athletes mentally anchor their workouts to starting hubs ("Zuhause", "Büro", "Ferienhaus", "Trailhead"). While the application has powerful models for both starting locations (Lieblingsorte in `StartLocation2Altitude.db`) and recurring route trajectories (Lieblingsstrecken / WorkoutClusters in `WorkoutClusters.db`), these subsystems previously operated as isolated data silos. Route clusters could be learned without an anchor location, location management cards did not indicate which route clusters start at that location, and cluster lists lacked departure location filtering. Bridging these entities enforces the invariant that every favorite route originates at a recognized favorite location, enriches navigation, and automatically expands the barometric calibration network without database migrations.

### 1.2 Functional & Architectural Requirements
The system SHALL establish an architectural, algorithmic, and visual bridge between Known Start Locations (Lieblingsorte) and Route Clusters (Lieblingsstrecken):

1. **Mandatory Starting Location Invariant & Automated Seeding (`WorkoutClusterEngine.kt`)**:
   - Every `WorkoutCluster` (Lieblingsstrecke) SHALL originate at a recognized `KnownLocation` (Lieblingsort).
   - In `WorkoutClusterEngine.learnFromWorkout(start, ...)`:
     - When learning or creating a new cluster, the system SHALL check if a `KnownLocation` exists within the geofence radius ($d \le 200\text{m}$) of `start` via `KnownLocationsDatabaseManager.getInstance(appContext).getMyLocation(start)`.
     - If no location exists within 200m, the system SHALL automatically discover and persist a new `KnownLocation` at `start` with a reverse-geocoded name, calibrated altitude, default geofence radius (200m), and provenance `ElevationSource.AUTO_LEARNED` via `KnownLocationsDatabaseManager.getInstance(appContext).recordWorkoutStart(start, altitude)`.

2. **Cluster Spatial Filter Criteria Extension (`ClusterFilterCriteria.kt`)**:
   - `ClusterFilterCriteria` SHALL define four nullable properties:
     `val startLocationName: String? = null`
     `val startLocationLat: Double? = null`
     `val startLocationLng: Double? = null`
     `val startLocationRadiusM: Double? = null`
   - **Predicate Evaluation (`matches`)**: When `startLocationLat != null && startLocationLng != null`, the system SHALL evaluate the cluster's start coordinate `(cluster.startLat, cluster.startLng)`:
     - If geodetic distance between `(cluster.startLat, cluster.startLng)` and `(startLocationLat, startLocationLng)` exceeds `startLocationRadiusM ?: 200.0`, `matches` SHALL return `false`.
     - Geodetic distance SHALL be computed via `WorkoutClusterEngine.distanceBetween(p1, p2)`, ensuring robust execution in both Android runtime and pure JVM test environments.
   - **Active Filter Counter**: `activeFilterCount` SHALL increment by 1 when `startLocationLat != null && startLocationLng != null`.
   - **JSON Serialization Parity**: `toJson()` and `fromJson()` SHALL serialize and deserialize `startLocationName`, `startLocationLat`, `startLocationLng`, and `startLocationRadiusM` losslessly for DataStore preference persistence.

3. **Lieblingsorte Screen Linked Routes Integration (`KnownLocationsViewModel.kt`, `KnownLocationsScreen.kt`)**:
   - `KnownLocationsUiState` SHALL provide:
     `val clustersByLocationId: Map<Long, List<WorkoutCluster>> = emptyMap()`.
   - `KnownLocationsViewModel` SHALL observe `WorkoutClusterRepository.allClusters` and group route clusters whose starting coordinates fall within each location's geofence radius ($d \le \text{radius}$).
   - In `KnownLocationCard`, when a location has linked route clusters, it SHALL render a dedicated route cluster section (e.g., `"Lieblingsstrecken ab hier (3):"`) with interactive chips for each cluster.
   - Tapping a route cluster chip SHALL invoke `onSelectCluster: (Long) -> Unit`, navigating directly to that cluster in `WorkoutClustersScreen`.

4. **Lieblingsstrecken Screen Starting Location Badge (`WorkoutClustersScreen.kt`)**:
   - Each route cluster card in `WorkoutClustersScreen` SHALL resolve and render a prominent starting location badge: `📍 Start: {startLocationName}` using dynamic resolution from `KnownLocationsDatabaseManager`.

5. **100% Localization Parity Across 9 Locales**:
   - String resources `cluster_start_location`, `known_location_routes_header`, and `filter_by_start_location` SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement only (REQ-UI-186). No existing requirements modified.
2. *Historical Origin & Commit Trace*: Ticket `ATT-1402` under Epic `ATT-1396` (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*).
3. *Root Reason for Existing Formulation*: Route clusters and start locations were developed in separate milestones (`ATT-354` for clusters, `ATT-1523` for location management). Unifying them bridges the athlete's mental model without changing schemas.
4. *Preservation of Core Invariants*: SQLite schemas (`WorkoutClusters.db`, `StartLocation2Altitude.db`) remain 100% unchanged; existing cluster math and centroid updates remain intact; universal delete context menu (`REQ-UI-061`) preserved; 9-language localization parity maintained.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Automated Anchor Location Seeding)**:
  * *Given* a new route cluster learned from a workout starting at an unmapped trailhead,
  * *When* `WorkoutClusterEngine.learnFromWorkout` creates the cluster,
  * *Then* a new `KnownLocation` SHALL be automatically persisted in `StartLocation2Altitude.db` with geocoded name and calibrated altitude.
* **Criterion 2 (Linked Routes on Location Card)**:
  * *Given* an athlete on `KnownLocationsScreen` viewing location "Zuhause" which has 3 route clusters starting within its 200m geofence,
  * *When* the card is composed,
  * *Then* 3 route cluster chips SHALL be displayed on the card, and tapping one SHALL navigate directly to that route cluster.
* **Criterion 3 (Start Location Badge on Cluster Card)**:
  * *Given* an athlete browsing `WorkoutClustersScreen`,
  * *When* inspecting a cluster card starting at "Zuhause",
  * *Then* a badge `📍 Start: Zuhause` SHALL be displayed.
* **Criterion 4 (Cluster List Departure Filtering)**:
  * *Given* the cluster list filtered by starting location "Zuhause",
  * *When* evaluating clusters against the filter,
  * *Then* only clusters whose starting coordinates fall within the "Zuhause" geofence radius SHALL be displayed.

---

## 2. Test Specification (TST-UI-139)

### Test Case 1: Automated Location Seeding & Invariant Tests (`TST-UI-139.1`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngineLocationSeedTest.kt`
* **Preconditions**: In-memory database or mocked `KnownLocationsDatabaseManager`.
* **Actions & Assertions**:
  1. Learn workout at new coordinate without existing location -> verify `recordWorkoutStart` is invoked with correct coordinate and altitude.
  2. Learn workout at existing location coordinate -> verify no duplicate location is created, and existing location is preserved.

### Test Case 2: Cluster Spatial Filter Criteria Tests (`TST-UI-139.2`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterCriteriaSpatialTest.kt`
* **Preconditions**: Sample `WorkoutCluster` instances with known start coordinates.
* **Actions & Assertions**:
  1. Cluster starting at (48.137, 11.576) matches spatial filter centered at (48.137, 11.576) with radius 200m -> asserts `true`.
  2. Cluster starting ~111m away matches 200m radius -> asserts `true`.
  3. Cluster starting ~890m away outside 200m radius -> asserts `false`.
  4. Verify `activeFilterCount` includes spatial filter dimension.
  5. Verify lossless JSON serialization and deserialization via `toJson()` and `fromJson()`.

### Test Case 3: Lieblingsorte Linked Routes Association Tests (`TST-UI-139.3`)
* **Scope**: ViewModel / Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsViewModelRoutesTest.kt`
* **Preconditions**: Location "Zuhause" at (48.137, 11.576) and 3 clusters (2 starting at Zuhause, 1 starting at Büro).
* **Actions & Assertions**:
  1. Verify `clustersByLocationId` maps the 2 matching clusters to "Zuhause".
  2. Verify cluster starting at Büro is not associated with "Zuhause".
  3. Verify empty list when a location has 0 starting clusters.

### Test Case 4: 9-Language Localization Audit (`TST-UI-139.4`)
* **Scope**: Static Resource Audit
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Actions & Assertions**:
  1. Verify string resources `cluster_start_location`, `known_location_routes_header`, and `filter_by_start_location` exist across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.

### Test Case 5: Clean-Room Full Suite Regression Execution (`TST-UI-139.5`)
* **Scope**: Clean-room full suite regression
* **Command**: `./gradlew testDebugUnitTest`
* **Assertions**: 100% pass rate, 0 regressions across all tests.

---

## 3. Traceability Matrix

| Requirement ID | Requirement Description | Test Case ID | Test Case Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-UI-186.1** | Mandatory Starting Location Invariant & Automated Seeding | `TST-UI-139.1` | Unit Test (`WorkoutClusterEngineLocationSeedTest`) | Specified |
| **REQ-UI-186.2** | Cluster Spatial Filter Criteria Extension | `TST-UI-139.2` | Unit Test (`ClusterFilterCriteriaSpatialTest`) | Specified |
| **REQ-UI-186.3** | Lieblingsorte Linked Routes Association | `TST-UI-139.3` | Unit Test (`KnownLocationsViewModelRoutesTest`) | Specified |
| **REQ-UI-186.4** | Cluster Card Start Location Badge | `TST-UI-139.3` | Composable / ViewModel | Specified |
| **REQ-UI-186.5** | 100% 9-Language Localization Parity | `TST-UI-139.4` | Resource Test (`TranslationParityTest`) | Specified |
| **REQ-UI-186.6** | Full Suite Zero Regressions | `TST-UI-139.5` | Regression (`testDebugUnitTest`) | Specified |
