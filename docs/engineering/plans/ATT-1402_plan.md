# Stage 3: Implementation Plan - ATT-1402: [Feature] Lieblingsorte & Lieblingsstrecken Bridge: Starting Location Association, Invariant & UI Integration

**Ticket**: [ATT-1402](https://atrainingtracker.atlassian.net/browse/ATT-1402)  
**Sub-task**: [ATT-1589](https://atrainingtracker.atlassian.net/browse/ATT-1589) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `sprint/2026-40.3`  
**Requirement Mapping**: `REQ-UI-186` (*Lieblingsorte & Lieblingsstrecken Bridge: Starting Location Association, Invariant & UI Integration*)  
**Test Mapping**: `TST-UI-139` (*Lieblingsorte & Lieblingsstrecken Bridge Verification*)  
**Branch**: `feature/ATT-1402`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Description & Background

In `aTrainingTracker`, athletes mentally associate their recurring workouts and route clusters (*Lieblingsstrecken*) with fixed starting hubs (*Lieblingsorte*), such as "Zuhause", "Büro", "Ferienhaus", or a specific trailhead.

### Architectural Gap & Siloed Data Models
1. **Isolated Data Silos**: Although both favorite locations (`StartLocation2Altitude.db`) and route clusters (`WorkoutClusters.db`) exist in the application, they have operated as disconnected subsystems.
2. **Missing Starting Location Invariant**: A route cluster can be learned and created without an associated anchor location. When workouts are recorded away from established hubs, clusters are formed without expanding the athlete's recognized location portfolio.
3. **Navigational Dead Ends in Location Management**: In `KnownLocationsScreen.kt`, athletes see their favorite locations and hit counts, but have no way of knowing which favorite route trajectories originate at that specific location.
4. **Lack of Departure Location Context in Clusters**: In `WorkoutClustersScreen.kt`, cluster cards display sport, reference distance, PR time, and recording counts, but do not indicate the starting hub name where the route begins.
5. **No Departure-Based Cluster Filtering**: While athletes can filter clusters by sport, equipment, distance, and hit count, they cannot filter route clusters by departure location.

By establishing algorithmic association (geodetic distance $\le \text{geofence radius}$), enforcing automated location seeding upon cluster creation, rendering interactive route cluster chips on favorite location cards, displaying starting location badges on cluster cards, and introducing spatial cluster filtering, this bridge harmonizes the athlete's spatial mental model with **zero database schema migrations**.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-186` (*Lieblingsorte & Lieblingsstrecken Bridge: Starting Location Association, Invariant & UI Integration*)
* **Test Mapping**: `TST-UI-139` (*Lieblingsorte & Lieblingsstrecken Bridge Verification*)
  * `TST-UI-139.1`: Mandatory Starting Location Invariant & Automated Seeding Tests (`WorkoutClusterEngineLocationSeedTest.kt`)
  * `TST-UI-139.2`: Cluster Spatial Filter Criteria Tests (`ClusterFilterCriteriaSpatialTest.kt`)
  * `TST-UI-139.3`: Lieblingsorte Linked Routes Association & ViewModel Tests (`KnownLocationsViewModelRoutesTest.kt`)
  * `TST-UI-139.4`: 9-Language Localization & Format Specifier Audit (`TranslationParityTest.kt`)
  * `TST-UI-139.5`: Clean-Room Full Suite Regression Execution (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Database Schema Migrations**: `WorkoutClusters.db` and `StartLocation2Altitude.db` SQLite schemas remain 100% unchanged. Location-to-cluster association is computed dynamically via geodetic distance between `(location.lat, location.lng)` and `(cluster.startLat, cluster.startLng)`.
2. **Cluster Geometry & Math Invariants**: Running centroid calculations (`startLat`, `startLng`, `endLat`, `endLng`, `maxDispLat`, `maxDispLng`, `refDistance`), apex tracking, and bounding box expansion (`minLat`, `maxLat`, `minLng`, `maxLng`) in `WorkoutClusterEngine.kt` remain strictly intact.
3. **Card Interaction Invariants**:
   - In `KnownLocationsScreen.kt`, single-tap on card body opens `EditKnownLocationDialog`, and long-press strictly preserves the universal delete-only context menu (`REQ-UI-061`).
   - Starts badge continues to support 1-tap drill-down filtering to workouts (`REQ-UI-185`).
   - Linked route cluster chips provide a dedicated interactive flow directly to `WorkoutClusterHeatmapScreen` via `NavRoutes.locations(clusterId)`.
4. **Pure JVM Test Environment Safety**: Geodetic distance calculations must use `WorkoutClusterEngine.distanceBetween(p1, p2)`, ensuring robust Haversine fallback when `android.location.Location.distanceBetween` throws `RuntimeException` in unit tests.
5. **Lossless JSON Persistence**: `toJson()` and `fromJson()` in `ClusterFilterCriteria.kt` must serialize and deserialize spatial fields (`startLocationName`, `startLocationLat`, `startLocationLng`, `startLocationRadiusM`) without data loss or exceptions when fields are absent in legacy stored JSON payloads.
6. **100% 9-Language Localization Parity**: All newly introduced string tokens (`cluster_start_location`, `known_location_routes_header`, `filter_by_start_location`) are localized across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero missing entries.
7. **Subtask Direct Completion**: Subtask `ATT-1589` transitions directly to `Erledigt` upon passing Gate 3 audit via `freigabe`.
8. **Parent Human Gate Invariance**: Terminal completion of parent `ATT-1402` remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2)

```
┌────────────────────────────────────────────────────────────────────────┐
│                      WorkoutClusterEngine                              │
│                                                                        │
│  learnFromWorkout(start, end, apex, distance, ...)                     │
│  │                                                                     │
│  ├─► Insert/Learn Cluster                                              │
│  │                                                                     │
│  └─► Mandatory Location Invariant:                                     │
│      val existingLoc = KnownLocationsDatabaseManager.getMyLocation(start)│
│      if (existingLoc == null) {                                        │
│          KnownLocationsDatabaseManager.recordWorkoutStart(start, null) │
│      }                                                                 │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│                      KnownLocationsViewModel                           │
│                                                                        │
│  Combines:                                                             │
│  - KnownLocationsRepository.locationsFlow                              │
│  - WorkoutClusterRepository.allClusters                                │
│                                                                        │
│  Emits in KnownLocationsUiState:                                       │
│  clustersByLocationId: Map<Long, List<WorkoutCluster>>                 │
│  (Filtered by distanceBetween(location.latLng, cluster.start) <= radius)│
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        KnownLocationsScreen                            │
│                                                                        │
│  KnownLocationCard: "Zuhause"                                          │
│  [Ascent: 520 m]  [📍 45 Starts ->]                                    │
│  "Lieblingsstrecken ab hier (2):"                                      │
│  [ Chip: Feierabendrunde ]  [ Chip: Isartrail ]                        │
│             │                                                          │
│             └─► onSelectCluster(cluster.id)                            │
│                      │                                                 │
│                      ▼                                                 │
│             navController.navigate(NavRoutes.locations(clusterId))     │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│                      WorkoutClustersScreen                             │
│                                                                        │
│  ClusterCard: "Feierabendrunde"                                        │
│  📍 Start: Zuhause                                                     │
│  [ 24.5 km ]  [ 🏆 0:54:12 ]  [ MTB ]  [ 18 Aufzeichnungen ]           │
└────────────────────────────────────────────────────────────────────────┘
```

### Component 1: Mandatory Starting Location Invariant & Automated Seeding (`WorkoutClusterEngine.kt`)
* In `WorkoutClusterEngine.learnFromWorkout`:
  After inserting a new cluster or learning an existing one:
  ```kotlin
  try {
      val knownLocManager = KnownLocationsDatabaseManager.getInstance(appContext)
      if (knownLocManager.getMyLocation(start) == null) {
          knownLocManager.recordWorkoutStart(start, null)
      }
  } catch (e: Exception) {
      Log.w(TAG, "Failed to seed starting location anchor: ${e.message}")
  }
  ```
* Ensures that no route cluster exists without an anchor favorite location in `StartLocation2Altitude.db`.

### Component 2: Cluster Spatial Filter Criteria Extension (`ClusterFilterCriteria.kt`)
* Add four nullable spatial fields to `ClusterFilterCriteria`:
  ```kotlin
  val startLocationName: String? = null,
  val startLocationLat: Double? = null,
  val startLocationLng: Double? = null,
  val startLocationRadiusM: Double? = null
  ```
* In `activeFilterCount`:
  ```kotlin
  if (startLocationLat != null && startLocationLng != null) count++
  ```
* In `matches(cluster: WorkoutCluster, linkedEquipment: Set<String> = emptySet())`:
  ```kotlin
  if (startLocationLat != null && startLocationLng != null) {
      val radius = startLocationRadiusM ?: 200.0
      val clusterStart = LatLng(cluster.startLat, cluster.startLng)
      val filterCenter = LatLng(startLocationLat, startLocationLng)
      val distance = WorkoutClusterEngine.distanceBetween(clusterStart, filterCenter)
      if (distance > radius) {
          return false
      }
  }
  ```
* In `toJson()` and `fromJson()`: serialize and deserialize the four spatial properties losslessly.

### Component 3: Lieblingsorte Linked Routes Association (`KnownLocationsViewModel.kt`, `KnownLocationsScreen.kt`)
* In `KnownLocationsUiState`:
  ```kotlin
  val clustersByLocationId: Map<Long, List<WorkoutCluster>> = emptyMap()
  ```
* In `KnownLocationsViewModel`:
  Inject `clusterRepository: WorkoutClusterRepository = WorkoutClusterRepository.getInstance(application)`.
  Combine `repository.locationsFlow` and `clusterRepository.allClusters` to populate `clustersByLocationId`.
* In `KnownLocationsScreen.kt`:
  Expose `onSelectCluster: (Long) -> Unit = {}` in `KnownLocationsScreen` and `KnownLocationsListContent`.
  In `KnownLocationCard`, extract `linkedClusters = clustersByLocationId[item.id]`.
  When `linkedClusters.isNotEmpty()`, render:
  ```kotlin
  Text(
      text = stringResource(R.string.known_location_routes_header, linkedClusters.size),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
  )
  FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
      linkedClusters.forEach { cluster ->
          SuggestionChip(
              onClick = { onSelectCluster(cluster.id) },
              label = {
                  Text(
                      text = cluster.name,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                  )
              }
          )
      }
  }
  ```

### Component 4: Cluster Departure Location Badge (`WorkoutClustersViewModel.kt`, `WorkoutClusterComponents.kt`)
* In `WorkoutClustersViewModel`:
  ```kotlin
  fun getStartLocationName(lat: Double, lng: Double): String? {
      return try {
          val loc = KnownLocationsDatabaseManager.getInstance(application).getMyLocation(LatLng(lat, lng))
          loc?.name
      } catch (_: Exception) {
          null
      }
  }
  ```
* In `WorkoutClusterMetadataBlock`:
  Resolve `startLocationName = remember(cluster.startLat, cluster.startLng) { viewModel.getStartLocationName(cluster.startLat, cluster.startLng) }`.
  When `startLocationName != null`:
  Render badge:
  ```kotlin
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
      Icon(
          imageVector = Icons.Default.Place,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
          tint = MaterialTheme.colorScheme.primary
      )
      Text(
          text = stringResource(R.string.cluster_start_location, startLocationName),
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
      )
  }
  ```

### Component 5: Navigation Host Integration (`ATrainingTrackerApp.kt`)
* In `composable(NavRoutes.START_LOCATIONS)`:
  Wire `onSelectCluster = { clusterId -> navController.navigate(NavRoutes.locations(clusterId)) }`.

### Component 6: 100% 9-Language Localization Parity (`strings.xml`)
* Define string resources across all 9 supported locales:
  * `cluster_start_location`:
    - `values`: "Start: %s"
    - `values-de`: "Start: %s"
    - `values-es`: "Inicio: %s"
    - `values-fr`: "Départ : %s"
    - `values-it`: "Partenza: %s"
    - `values-ja`: "開始: %s"
    - `values-nl`: "Start: %s"
    - `values-pl`: "Start: %s"
    - `values-pt`: "Início: %s"
  * `known_location_routes_header`:
    - `values`: "Routes starting here (%d):"
    - `values-de`: "Lieblingsstrecken ab hier (%d):"
    - `values-es`: "Rutas desde aquí (%d):"
    - `values-fr`: "Itinéraires au départ d'ici (%d) :"
    - `values-it`: "Percorsi da qui (%d):"
    - `values-ja`: "ここからのルート (%d):"
    - `values-nl`: "Routes vanaf hier (%d):"
    - `values-pl`: "Trasy stąd (%d):"
    - `values-pt`: "Rotas a partir daqui (%d):"
  * `filter_by_start_location`:
    - `values`: "Filter by Start Location"
    - `values-de`: "Nach Startort filtern"
    - `values-es`: "Filtrar por lugar de inicio"
    - `values-fr`: "Filtrer par lieu de départ"
    - `values-it`: "Filtra per luogo di partenza"
    - `values-ja`: "開始地点で絞り込む"
    - `values-nl`: "Filteren op startlocatie"
    - `values-pl`: "Filtruj wg miejsca startu"
    - `values-pt`: "Filtrar por local de início"

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Language String Resources Definition
* Files:
  * `app/src/main/res/values/strings.xml`
  * `app/src/main/res/values-de/strings.xml`
  * `app/src/main/res/values-es/strings.xml`
  * `app/src/main/res/values-fr/strings.xml`
  * `app/src/main/res/values-it/strings.xml`
  * `app/src/main/res/values-ja/strings.xml`
  * `app/src/main/res/values-nl/strings.xml`
  * `app/src/main/res/values-pl/strings.xml`
  * `app/src/main/res/values-pt/strings.xml`
* Action: Add `cluster_start_location`, `known_location_routes_header`, and `filter_by_start_location` across all 9 resource files.
* Test command:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"`

### Step 2: Mandatory Starting Location Invariant in WorkoutClusterEngine
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt`
* Action:
  * In `learnFromWorkout`, when creating a new cluster, check `getMyLocation(start)`.
  * If null, call `recordWorkoutStart(start, null)` to seed an anchor favorite location in `StartLocation2Altitude.db`.

### Step 3: Spatial Filter Criteria Extension in ClusterFilterCriteria
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterCriteria.kt`
* Action:
  * Add properties `startLocationName`, `startLocationLat`, `startLocationLng`, `startLocationRadiusM`.
  * Update `activeFilterCount` to increment when coordinates are present.
  * Implement geodetic distance evaluation in `matches(cluster: WorkoutCluster, ...)`.
  * Implement lossless JSON serialization and deserialization in `toJson()` and `fromJson()`.

### Step 4: Lieblingsorte Linked Routes Association in ViewModel & UI
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsViewModel.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt`
* Action:
  * In `KnownLocationsUiState`, add `clustersByLocationId: Map<Long, List<WorkoutCluster>>`.
  * In `KnownLocationsViewModel`, observe `WorkoutClusterRepository.allClusters` and associate clusters with locations whose geofence encompasses cluster start coordinates.
  * In `KnownLocationsScreen.kt` and `KnownLocationCard`, render linked route cluster chips with `onSelectCluster: (Long) -> Unit`.

### Step 5: Route Cluster Card Departure Location Badge
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt`
* Action:
  * Add `getStartLocationName(lat: Double, lng: Double): String?` to `WorkoutClustersViewModel`.
  * In `WorkoutClusterMetadataBlock`, resolve starting location and render `📍 Start: {startLocationName}` badge.

### Step 6: Navigation Host Integration in ATrainingTrackerApp
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt`
* Action:
  * In `NavRoutes.START_LOCATIONS`, wire `onSelectCluster = { clusterId -> navController.navigate(NavRoutes.locations(clusterId)) }`.

### Step 7: Targeted Unit Tests Implementation & Execution
* Target test files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngineLocationSeedTest.kt`:
    - New cluster seeds start location when unmapped.
    - Existing location preserved when cluster created at known location.
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterCriteriaSpatialTest.kt`:
    - Exact match, within radius match, outside radius rejection, active filter counter, and JSON serialization.
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsViewModelRoutesTest.kt`:
    - Clusters associated with correct starting location, no false positives for distinct locations, empty list when 0 clusters match.
* Execution commands:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutClusterEngineLocationSeedTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.ClusterFilterCriteriaSpatialTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationsViewModelRoutesTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Run all targeted unit test suites during construction.
  - Run clean-room full suite regression `./gradlew testDebugUnitTest` (890+ unit tests) in Stage 5.
* **Rollback Plan**:
  - All changes reside on dedicated branch `feature/ATT-1402`.
  - In the event of an unresolvable defect or regression, the branch can be cleanly reset or deleted without impacting `sprint/2026-40.3`.
