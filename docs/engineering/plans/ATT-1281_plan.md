# Stage 3: Implementation Plan - ATT-1281: Persistent Climbs Database & Live ClimbPro Cockpit Sheet (Route & Free-Riding Support)

**Ticket**: [ATT-1281](https://rainerblind.atlassian.net/browse/ATT-1281)  
**Sub-task**: [ATT-2294](https://rainerblind.atlassian.net/browse/ATT-2294) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Requirement Mapping**: `REQ-MAP-027` (*Persistent Climbs Database & Live ClimbPro Cockpit Sheet*)  
**Test Mapping**: `TST-MAP-029` (*Persistent Climbs Database & Live ClimbPro Cockpit Sheet Verification*)  
**Branch**: `feature/ATT-1281`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Description & Background

Athletes require real-time pacing telemetry and gradient visualizations on sustained ascents during both route navigation and free-riding. This plan introduces a persistent SQLite climbs database, an automated mathematical climb extraction and deduplication engine, a reactive live climb tracking repository, and a live cockpit bottom sheet integrated into `SensorGridScreen` with seamless Strava Live Segment coexistence.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-027` (*Persistent Climbs Database & Live ClimbPro Cockpit Sheet*)
* **Test Mapping**: `TST-MAP-029` (*Persistent Climbs Database & Live ClimbPro Cockpit Sheet Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing route database schema v10 (`RoutesDatabaseManager.kt`), GPX DEM enrichment (`REQ-MAP-025`), waypoints (`REQ-MAP-026`), and unit tests must continue to pass cleanly.
2. **Strava Live Segment Priority**: When a Strava Live Segment is active (`ON_SEGMENT`), it strictly takes visual precedence on the Cockpit sheet over any ongoing climb. Once the segment finishes, the sheet immediately returns to the ongoing climb.
3. **Thread Safety & Dispatcher Affinity**: Database operations on `ClimbsDatabaseManager` are isolated to a dedicated single-threaded dispatcher (`dbDispatcher`), preventing SQLite concurrency locks.
4. **Subtask Direct Completion**: Sub-tasks transition directly to `Erledigt` via transition `freigabe` upon automated Gate audit passing.
5. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-1281` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

```
┌──────────────────────────────────────────────────────────────┐
│                    Domain Models (Climb.kt)                  │
│ Climb, ClimbCategory, LiveClimbStatus, LiveClimbData         │
└──────────────────────────────┬───────────────────────────────┘
                               │
       ┌───────────────────────┴───────────────────────┐
       ▼                                               ▼
┌──────────────────────────────┐       ┌──────────────────────────────┐
│  ClimbDetector (Math Engine) │       │   ClimbsDatabaseManager.kt   │
│  Detects ascents >=500m & 3% │       │   SQLite Climbs.db (CRUD &   │
│  Tolerates micro-dips <=15m  │       │   Spatial Deduplication 50m) │
└──────────────┬───────────────┘       └───────────────┬──────────────┘
               │                                       │
               └───────────────────┬───────────────────┘
                                   ▼
┌──────────────────────────────────────────────────────────────┐
│              LiveClimbsRepository (Tracking Engine)          │
│ Monitors currentLocation & bearing, evaluates start/summit   │
│ gates (approaching <=250m, on_climb, finished)               │
└──────────────────────────────┬───────────────────────────────┘
                               │
       ┌───────────────────────┴───────────────────────┐
       ▼                                               ▼
┌──────────────────────────────┐       ┌──────────────────────────────┐
│  LiveClimbSheet (Composable) │       │  SensorGridScreen & Settings │
│  Profile curve (TTColor.Zone)│       │  Priority arbiter: Strava >  │
│  Remaining dist, gain, grade │       │  Climb; TuningPreferences    │
└──────────────────────────────┘       └──────────────────────────────┘
```

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Domain Models (`Climb.kt`)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/climbs/Climb.kt`
* **Changes**:
  - Define `enum class ClimbCategory { UNCATEGORIZED, CAT_4, CAT_3, CAT_2, CAT_1, HC }`.
  - Define `enum class LiveClimbStatus { FAR_FAR_AWAY, APPROACHING, ON_CLIMB, FINISHED }`.
  - Define data class `Climb` (`id`, `name`, `routeId`, `startLat`, `startLng`, `endLat`, `endLng`, `distanceMeters`, `elevationGainMeters`, `avgGradePercent`, `maxGradePercent`, `category`, `pathPoints`).
  - Define data class `LiveClimbData` (`climb`, `status`, `distanceToStart`, `distanceToSummit`, `remainingElevationGain`, `currentGradePercent`, `routeIndex`, `totalRouteClimbs`).

### Step 2: Climb Detection Engine (`ClimbDetector.kt`)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/climbs/ClimbDetector.kt`
* **Changes**:
  - Implement pure mathematical algorithm `detectClimbs(points: List<PathPoint>, minLengthMeters: Double = 500.0, minGradePercent: Double = 3.0, minGainMeters: Double = 20.0, routeId: Long? = null): List<Climb>`.
  - Traverse polyline with rolling altitude and distance deltas.
  - Implement dip tolerance: tolerate up to 15m vertical descent or 150m flat within an ongoing climb.
  - Implement score calculation $\text{Score} = \text{length (m)} \times \text{gradient (\%)}$ and categorization.

### Step 3: Persistent Climbs Database & Spatial Deduplication (`ClimbsDatabaseManager.kt`)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/database/ClimbsDatabaseManager.kt`
* **Changes**:
  - Implement `ClimbsDbHelper` with table `climbs` and indexes `idx_climbs_start_lat_lng`, `idx_climbs_route_id`.
  - Implement `ClimbsDatabaseManager` with `dbDispatcher`.
  - Implement `insertClimbWithDeduplication(climb: Climb): Long` with 50m geodesic proximity deduplication on start and summit coordinates.
  - Implement CRUD: `getAllClimbs()`, `getClimbsForRoute(routeId: Long)`, `deleteClimb(id: Long)`, `deleteAll()`.

### Step 4: Route Ingestion Hook (`RoutesRepository.kt`)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/RoutesRepository.kt`
* **Changes**:
  - In `insertRouteWithPoints`, invoke `ClimbDetector.detectClimbs` on the enriched route points and persist to `ClimbsDatabaseManager`.

### Step 5: Live Climb Tracking Repository (`LiveClimbsRepository.kt`)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/climbs/LiveClimbsRepository.kt`
* **Changes**:
  - Implement singleton repository monitoring `BANALServiceRepository.currentLocation`, `currentBearing`, and `RoutesRepository.activeNavigatedRouteId`.
  - Calculate perpendicular virtual start and finish gates.
  - State machine: `FAR_FAR_AWAY` $\rightarrow$ `APPROACHING` ($\le 250\text{m}$, bearing $\Delta \le 45^\circ$) $\rightarrow$ `ON_CLIMB` (start gate crossed) $\rightarrow$ `FINISHED` (summit crossed).
  - Expose `val liveClimb: StateFlow<LiveClimbData?>`.

### Step 6: Live Climb Cockpit Sheet UI (`LiveClimbSheet.kt`)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheet.kt`
* **Changes**:
  - Composable bottom sheet with:
    - Header: Climb name, category badge (Cat 4, 3, 2, 1, HC), route index counter (e.g. "Anstieg 2 von 4").
    - Canvas elevation profile curve colored by slope gradient segments (`TTColor.Zone1` to `Zone5`, Black for $>20\%$).
    - Dynamic rider position pin on profile curve.
    - Telemetry metrics: remaining distance, remaining ascent ($\Delta h$), current grade (%).

### Step 7: Cockpit Sheet Integration & Priority Arbiter (`SensorGridScreen.kt`)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Changes**:
  - Collect `liveClimb` from `LiveClimbsRepository`.
  - Arbitrate sheet presentation: Strava Live Segment takes priority; if absent, display `LiveClimbSheet`.

### Step 8: Tuning Configuration & 9-Language Localization Parity
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
  - `app/src/main/res/values/strings.xml` (and `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`)
* **Changes**:
  - Add tuning preferences `showLiveClimbs`, `climbMinLengthMeters`, `climbMinGradientPercent`.
  - Add localized strings: `live_climb_title`, `live_climb_remaining_dist`, `live_climb_remaining_elevation`, `live_climb_grade`, `live_climb_approaching`, `live_climb_summit`, `climb_category_hc`, `climb_category_cat1`, `climb_category_cat2`, `climb_category_cat3`, `climb_category_cat4`, `climb_counter_route`.

### Step 9: Unit Tests & Regression Verification
* **Commands**:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.climbs.ClimbDetectorTest"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.ClimbsDatabaseManagerTest"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.climbs.LiveClimbsRepositoryTest"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"`
  - `./gradlew testDebugUnitTest` (full suite clean-room regression)

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit tests immediately upon implementing each component, verify 9-language translation parity, and validate 100% full-suite test pass rate.
* **Rollback**: Work is isolated on `feature/ATT-1281`; git commit points can be cleanly reverted or reset without contaminating `sprint/2026-40.14`.
