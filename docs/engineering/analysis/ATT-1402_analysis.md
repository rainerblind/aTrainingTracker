# Stage 1 Analysis: ATT-1402 - Lieblingsorte & Lieblingsstrecken Bridge: Starting Location Association, Invariant & UI Integration

**Ticket**: [ATT-1402](https://atrainingtracker.atlassian.net/browse/ATT-1402)  
**Sub-task**: [ATT-1586](https://atrainingtracker.atlassian.net/browse/ATT-1586) (`[Analysis]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `sprint/2026-40.3`  
**Branch**: `feature/ATT-1402`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Statement & Motivation

Athletes inherently structure their outdoor training around physical departure hubs: *"I am at Home / at Work / at the Trailhead — which of my favorite routes (Lieblingsstrecken) can I start from here?"*

In `aTrainingTracker`, two powerful spatial abstractions exist in parallel:
1. **Lieblingsorte** (*Known Start Locations*, stored in `StartLocation2Altitude.db` via `KnownLocationsDatabaseManager`): Precise spatial point anchors with a geofence radius ($r \approx 200\text{m}$) providing barometric altimeter calibration and workout auto-naming.
2. **Lieblingsstrecken** (*Route Clusters*, stored in `WorkoutClusters.db` via `WorkoutClusterDatabaseManager`): Recurring trajectory families learned from repeated workout recordings with spatial bounds, apex centroids, reference distances, and polylines.

### Current Deficiency
Despite their natural synergy, these two systems previously operated as **completely disjoint data silos**:
1. **Missing Anchor Invariant**: A route cluster could be learned and stored without any linked favorite start location. If an athlete regularly ran or rode a route starting from an unnamed trailhead, no anchor location was automatically seeded.
2. **One-Way Dead Ends in UI**:
   - In `KnownLocationsScreen`, an athlete viewing "Zuhause" sees recorded starts and altitude, but cannot see which favorite routes originate there.
   - In `WorkoutClustersScreen`, route cluster cards display sport types, distances, and hit counts, but omit the recognized starting location name (e.g. `📍 Start: Zuhause`).
   - Athletes cannot filter favorite routes by starting location in `ClusterFilterCriteria`.
3. **Cockpit Pre-Start Blindspot**: When acquiring a GPS fix at a known location prior to workout start, the tracking cockpit can confirm altimeter calibration (`REQ-UI-183`), but cannot proactively suggest the favorite routes originating from that hub.

Unifying Lieblingsorte and Lieblingsstrecken bridges the athlete's mental model, enriches journaling and route planning, and strengthens the app's barometric calibration network automatically.

---

## 2. Root Cause Analysis (Forensic Investigation & Architectural Gap Analysis)

### Codebase Investigation & Findings

1. **Route Cluster Domain Model & Database (`WorkoutClusterDatabaseManager.kt`, `WorkoutCluster.kt`)**:
   - `WorkoutCluster` already encapsulates `val startLat: Double` and `val startLng: Double` alongside `refDistance`, `hitCount`, `bSportType`, and bounding box coordinates (`minLat`, `minLng`, `maxLat`, `maxLng`).
   - *Key Architectural Finding*: Because `WorkoutCluster` already stores exact starting coordinates (`startLat`, `startLng`), **ZERO SQLite schema migrations** are required to link clusters to favorite locations. A cluster's starting location can be dynamically evaluated at runtime against `KnownLocationsDatabaseManager.getMyLocation(LatLng(startLat, startLng))` or pre-grouped by spatial geofence matching ($d \le r$).

2. **Automated Cluster Learning Pipeline (`WorkoutClusterEngine.kt`)**:
   - When an athlete saves a workout or manually creates a cluster, `WorkoutClusterEngine.learnFromWorkout(...)` evaluates candidates and creates or updates a `WorkoutCluster`.
   - *Gap*: When creating a new cluster (`dbManager.insertCluster(newCluster)`), `WorkoutClusterEngine` does not verify if a `KnownLocation` exists at `start`. If no location exists within 200m, no anchor location is created, violating the invariant that every favorite route originates at a recognized favorite location.

3. **Cluster Filtering Engine (`ClusterFilterCriteria.kt`)**:
   - `ClusterFilterCriteria` currently filters by text query, sport equipment name, minimum distance, and minimum hit count.
   - *Gap*: It lacks spatial starting location filter properties (`startLocationName`, `startLocationLat`, `startLocationLng`, `startLocationRadiusM`), preventing athletes from filtering route clusters by departure point.

4. **Lieblingsorte UI State & Screen (`KnownLocationsViewModel.kt`, `KnownLocationsScreen.kt`)**:
   - `KnownLocationsViewModel` maintains `locationsFlow: StateFlow<List<KnownLocationItem>>`.
   - *Gap*: It does not observe `WorkoutClusterRepository.allClusters`. As a result, `KnownLocationCard` cannot display route chips for clusters originating at that location, nor navigate to them.

5. **Lieblingsstrecken UI State & Screen (`WorkoutClustersViewModel.kt`, `WorkoutClustersScreen.kt`)**:
   - `WorkoutClustersViewModel` coordinates cluster listings and map states.
   - *Gap*: Cluster cards do not resolve or display a starting location badge (e.g. `📍 Start: Zuhause`).

6. **Cockpit Pre-Start Awareness (`TrackingTabsViewModel.kt`)**:
   - `TrackingTabsViewModel` combines `currentLocation` and `knownLocationsRepository.locationsFlow` to output `LocationCalibrationStatus`.
   - *Gap*: It does not identify or surface candidate `WorkoutCluster` routes originating at the current location for quick pre-start route selection.

---

## 3. User Scope Grounding (ATT-1250)

| User Role / Context | Action | Expected Outcome |
| :--- | :--- | :--- |
| **Athlete on KnownLocationsScreen** | Views location card for "Zuhause". | Card displays linked route clusters starting at "Zuhause" (e.g., `Hausrunde Berg`, `Feierabend-Lauf`). Tapping a cluster chip navigates directly to that cluster's view in `WorkoutClustersScreen`. |
| **Athlete on WorkoutClustersScreen** | Browses route clusters. | Each cluster card displays an informative start location badge (e.g., `📍 Start: Zuhause`). |
| **Athlete filtering Routes by Location** | Applies filter by starting location "Zuhause". | Only clusters originating within the geofence radius of "Zuhause" remain visible in the route cluster list. |
| **Athlete learning a new Route Cluster** | Completes a new recurring route at an unmapped trailhead. | `WorkoutClusterEngine.learnFromWorkout` automatically discovers and seeds a new `KnownLocation` at `start` if none existed, guaranteeing the starting location invariant. |
| **Athlete in Cockpit prior to Start** | Acquires GPS fix at "Zuhause". | Cockpit displays recognized start location and can indicate available favorite routes originating from this hub. |

---

## 4. Chesterton's Fence & Requirement Archaeology

### Existing Artifacts & Invariants
1. **`WorkoutCluster.kt` (ATT-354 / REQ-SET-065)**:
   - Established immutable cluster properties (`startLat`, `startLng`, `minLat`, `minLng`, `maxLat`, `maxLng`, `refDistance`, `hitCount`, `hasCounter`).
   - Must preserve all spatial clustering math, centroid updates, and bounding boxes.
2. **`KnownLocationsDatabaseManager.java` (ATT-1447 / REQ-DAT-015)**:
   - Contains `recordWorkoutStart(pos, altitude)` which atomically discovers and persists new locations with reverse geocoded names and default 200m radius.
   - Single-thread confinement (`KnownLocationsDB-Thread`) and database open-lock guarantees must be preserved.
3. **`WorkoutClusterEngine.distanceBetween(p1, p2)`**:
   - Pure Java Haversine fallback for JVM unit test execution when `android.location.Location.distanceBetween` throws `RuntimeException`.
4. **Zero SQLite Database Migrations**:
   - Both `WorkoutClusters.db` and `StartLocation2Altitude.db` table schemas must remain 100% unchanged. All relationships are evaluated dynamically via geodetic distance ($d \le r$).
5. **Universal Delete Context Menu (`REQ-UI-061`)**:
   - Card interactions in `KnownLocationsScreen` and `WorkoutClustersScreen` must preserve established click and long-press behaviors.
6. **100% 9-Language Localization Parity**:
   - All newly introduced labels and format strings must be translated across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 5. Proposed Solution Architecture & Interfaces

```
┌────────────────────────────────────────────────────────────────────────┐
│                   WorkoutClusterEngine.learnFromWorkout                │
│                                                                        │
│  On learning/creating a new cluster:                                   │
│  1. Check KnownLocationsDatabaseManager.getMyLocation(start)           │
│  2. If absent: call KnownLocationsDatabaseManager.recordWorkoutStart(  │
│        start, minAltPos?.let { ... }                                   │
│     )                                                                  │
│  ==> Guarantees Mandatory Starting Location Invariant                  │
└────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│               Domain / ViewModel Reactive Bridge Pipeline              │
│                                                                        │
│  KnownLocationsViewModel:                                              │
│    Combines locationsFlow + allClusters                                │
│    Maps each KnownLocationItem -> List<WorkoutCluster>                 │
│                                                                        │
│  WorkoutClustersViewModel:                                             │
│    Resolves startLocationName for each WorkoutCluster                  │
│    ClusterFilterCriteria: filters by startLocationName / coordinates   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                       Jetpack Compose UI Layer                         │
│                                                                        │
│  KnownLocationCard:                                                    │
│    [Ascent: 520 m]  [📍 45 Starts ->]                                  │
│    "Lieblingsstrecken ab hier (3):"                                    │
│    [ Hausrunde Berg ]  [ Feierabend-Lauf ]  [ Flache 40km ]            │
│                                                                        │
│  WorkoutClusterCard:                                                   │
│    📍 Start: Zuhause                                                   │
│    [Distance: 12.4 km]  [Hits: 18]                                     │
└────────────────────────────────────────────────────────────────────────┘
```

### Technical Design Specifications

1. **Mandatory Starting Location Invariant in `WorkoutClusterEngine.kt`**:
   - In `learnFromWorkout`:
     ```kotlin
     val existingLocation = KnownLocationsDatabaseManager.getInstance(appContext).getMyLocation(start)
     if (existingLocation == null) {
         KnownLocationsDatabaseManager.getInstance(appContext).recordWorkoutStart(start, minAltPos?.let { ... })
     }
     ```
   - This ensures that every cluster created has a corresponding `KnownLocation` in `StartLocation2Altitude.db`.

2. **Cluster Filter Criteria Extension (`ClusterFilterCriteria.kt`)**:
   - Add properties:
     ```kotlin
     val startLocationName: String? = null,
     val startLocationLat: Double? = null,
     val startLocationLng: Double? = null,
     val startLocationRadiusM: Double? = null
     ```
   - In `matches(cluster: WorkoutCluster, ...)`:
     ```kotlin
     if (startLocationLat != null && startLocationLng != null) {
         val radius = startLocationRadiusM ?: 200.0
         val dist = WorkoutClusterEngine.distanceBetween(
             LatLng(cluster.startLat, cluster.startLng),
             LatLng(startLocationLat, startLocationLng)
         )
         if (dist > radius) return false
     }
     ```
   - Update `activeFilterCount`, `toJson()`, and `fromJson()`.

3. **Lieblingsorte Screen Integration (`KnownLocationsViewModel.kt`, `KnownLocationsScreen.kt`)**:
   - In `KnownLocationsUiState`:
     `val clustersByLocationId: Map<Long, List<WorkoutCluster>> = emptyMap()`
   - In `KnownLocationsViewModel`:
     Observe `WorkoutClusterRepository.allClusters` and compute clusters starting within each location's radius ($d \le r$).
   - In `KnownLocationCard`:
     When `clusters.isNotEmpty()`, render a clean scrollable row or flow of cluster chips (e.g. `AssistChip` / tonal badge with route icon).
     Tapping a chip invokes `onSelectCluster(cluster.id)` navigating to `NavRoutes.cluster(cluster.id)`.

4. **Lieblingsstrecken Screen Integration (`WorkoutClustersScreen.kt`)**:
   - In `WorkoutClusterCard`:
     Display starting location badge: `📍 Start: {startLocationName}` using dynamic resolution from `KnownLocationsDatabaseManager`.

5. **100% 9-Language Localization**:
   - Localize all newly introduced string tokens across 9 supported locales:
     - `cluster_start_location`: "Start: %s"
     - `known_location_routes_header`: "Lieblingsstrecken ab hier (%d)" / "Routes starting here (%d)"
     - `filter_by_start_location`: "Nach Startort filtern" / "Filter by Start Location"

---

## 6. Risk Rating & Mitigation

* **Technical Risk**: **LOW**
* **Justification**:
  - Zero database migrations required: cluster start coordinates and location geofences already exist in production databases.
  - Spatial calculations utilize existing `WorkoutClusterEngine.distanceBetween` with built-in JVM fallback.
  - Seeding in `learnFromWorkout` calls existing `recordWorkoutStart`, which is thread-safe and verified.

---

## 7. Gate 1 Recommendation
**RECOMMEND PASS** for proceeding to Stage 2 (Requirement & Test Specification).
