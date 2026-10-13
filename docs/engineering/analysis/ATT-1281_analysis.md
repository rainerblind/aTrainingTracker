# Stage 1 Analysis: ATT-1281 - Persistent Climbs Database & Live ClimbPro Cockpit Sheet (Route & Free-Riding Support)

**Ticket**: [ATT-1281](https://rainerblind.atlassian.net/browse/ATT-1281)  
**Sub-task**: [ATT-2292](https://rainerblind.atlassian.net/browse/ATT-2292) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-1281`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & Motivation

During outdoor endurance cycling and running, pacing on sustained climbs and steep ascents is critical for athletic performance and energy preservation. Premium modern bike computers and sport watches (such as Garmin ClimbPro and Hammerhead Climber) provide real-time gradient profiles, remaining vertical ascent, distance to summit, and categorized climb classifications. While early implementations strictly required the athlete to navigate along a pre-loaded track, modern implementations support **Free Riding**—automatically recognizing and alerting athletes to upcoming ascents on any road without requiring an active navigation route.

Currently in aTrainingTracker:
- Routes imported from GPX, TCX, or Strava contain elevation data (either embedded or enriched via the Open-Meteo DEM service per `REQ-MAP-025`), but there is no mechanism to extract, identify, or persist climbs as distinct geographical and analytical entities.
- When an athlete is tracking a workout—whether following an active navigation route or riding freely—there is no automated awareness of upcoming climbs, no real-time summit countdown, and no live gradient profile display.
- While Strava Live Segments (`LiveSegmentsRepository.kt`) exist, they require active Strava synchronization and only cover community-created competitive segments, omitting thousands of notable natural climbs and mountain passes.

Introducing a persistent **Climbs Database** and live **ClimbPro Cockpit Sheet** solves this gap. Climbs extracted automatically from routes upon import or scan are permanently stored locally, enabling proactive live climb previews and pacing telemetry both during route navigation and free riding.

---

## 2. Root Cause Analysis (Forensic Investigation & Architectural Gap Analysis)

### 2.1 Database & Persistence Gap
- `RoutesDatabaseManager` manages `routes`, `route_points`, and `route_waypoints` (`Routes.db`).
- There is currently no persistent schema or table for climbs.
- A climb requires dedicated spatial and elevation metadata:
  - `id`: Unique identifier (INTEGER PRIMARY KEY AUTOINCREMENT).
  - `name`: Named ascent (e.g. "Anstieg 1,8 km @ 6,2%" or pass/hill name).
  - `route_id`: Associated source route ID (nullable, for route-origin tracking).
  - `start_lat`, `start_lng`: Start coordinate of the ascent.
  - `end_lat`, `end_lng`: Summit / finish coordinate.
  - `distance_m`: Total climb length in meters.
  - `elevation_gain_m`: Total vertical gain ($\Delta H$ in meters).
  - `avg_grade`: Average gradient in percent.
  - `max_grade`: Maximum sustained gradient in percent.
  - `climb_category`: Categorization (Cat 4, Cat 3, Cat 2, Cat 1, HC).
  - `path_polyline`: Encoded polyline or coordinate stream with embedded distances and altitudes.

### 2.2 Climb Detection & Mathematical Extraction Gap
- No algorithm exists in the codebase to evaluate an altitude profile and partition it into climbs.
- A robust climb detector must:
  1. Smooth raw altitude samples to eliminate high-frequency barometric or GPS jitter.
  2. Identify ascent candidates matching minimum athletic thresholds (default: length $\ge 500\text{ m}$ and average grade $\ge 3.0\%$, with total gain $\ge 20\text{ m}$).
  3. Permit realistic descending dips within a climb (e.g. up to 15m loss or 150m flat/descent) without prematurely aborting the climb, while ensuring the net gradient remains positive and meets the threshold.
  4. Classify climbs according to the international cycling formula:
     $$\text{ClimbScore} = \text{Length (m)} \times \text{Average Grade (\%)} = \Delta H \times 100$$
     - $\text{Score} \ge 8,000$: Category 4
     - $\text{Score} \ge 16,000$: Category 3
     - $\text{Score} \ge 32,000$: Category 2
     - $\text{Score} \ge 64,000$: Category 1
     - $\text{Score} \ge 80,000$: HC (Hors Catégorie)
     - Ascents below 8,000 with $\ge 500\text{m}$ and $\ge 3\%$ are classified as "Uncategorized" / Cat 4 entry.

### 2.3 Spatial Deduplication Gap
- Athletes frequently import multiple routes that traverse the same local hills or passes.
- Without spatial deduplication, the database would accumulate redundant identical climb entries.
- Spatial deduplication must compare start points and summit points: if both start and end coordinates are within $50\text{ m}$ of an existing climb, the duplicate is detected and merged or skipped.

### 2.4 Live Tracking & Cockpit Sheet Gap
- `LiveSegmentsRepository.kt` currently monitors Strava segments using cross-product virtual gates and distance thresholds (`SEGMENT_START_DISTANCE_THRESHOLD = 250m`, `bearingDiff <= 45°`).
- A shared or analogous `LiveClimbsRepository.kt` is required to track climbs during active workouts.
- `SensorGridScreen.kt` hosts a `BottomSheetScaffold` which currently only displays `LiveSegmentSheet`.
- A priority hierarchy is needed:
  - If a Strava Live Segment is active, it takes visual precedence on the sheet.
  - If no Strava Segment is active but a Climb is active/approaching, `LiveClimbSheet` displays the climb profile, remaining distance, remaining ascent ($\Delta h_{\text{rest}}$), and gradient.
  - Once the Strava segment finishes, the sheet immediately returns to the ongoing climb until summit crossing.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Persistent Climbs Database (`ClimbsDatabaseManager.kt`)**: Dedicated SQLite database `Climbs.db` with table `climbs` and indexes for spatial start/end queries, supporting full CRUD operations.
  2. **Automated Climb Detection Engine (`ClimbDetector.kt`)**: Algorithm extracting qualifying ascents ($\ge 500\text{ m}$, $\ge 3.0\%$ gradient) from route trackpoints (`PathPoint`), calculating average and max grade, total ascent, and climb category.
  3. **Spatial Deduplication Engine**: Automated deduplication when adding climbs (skipping or updating existing climbs if start and summit lie within 50m).
  4. **Route Ingestion Hook**: Triggering climb detection during route import/save in `RoutesRepository.insertRouteWithPoints` or background scan.
  5. **Live Climb Tracking Engine (`LiveClimbsRepository.kt`)**: Reactive StateFlow repository monitoring live GPS location and bearing against known climbs (`FAR_FAR_AWAY`, `APPROACHING` within 250m, `ON_CLIMB`, `FINISHED`).
  6. **Route Context vs. Free Riding Support**:
     - *Route Context*: When an active route is followed (`RoutesRepository.activeNavigatedRouteId`), climbs along the route are sequenced and numbered (e.g. "Anstieg 2 von 4").
     - *Free Riding*: When no route is active, any nearby climb in the climbs database is detected and tracked.
  7. **Live Climb Cockpit Sheet UI (`LiveClimbSheet.kt`)**: Jetpack Compose bottom sheet rendering the color-coded gradient elevation profile (`TTColor.Zone1` to `Zone5`), current rider position pin, distance to summit, remaining elevation gain, and current gradient.
  8. **Strava Segment Synergy & Priority**: Seamless coexistence in `SensorGridScreen.kt` giving Strava segments momentary priority during overlap.
  9. **Configurable Preferences & Feature Toggle**: Settings in `TuningPreferencesDataStore` (`showLiveClimbs`, `climbMinLengthMeters`, `climbMinGradientPercent`) and `AdvancedTuningDialog`.
  10. **100% 9-Language Localization Parity**: Localizing all user-facing climb strings across EN, DE, ES, FR, IT, JA, NL, PL, PT.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. *Cloud/Crowdsourced Climb Sync*: Climbs are derived and persisted locally; no external third-party climb servers or public sharing API.
  2. *Auditory Voice Guidance*: Audio countdowns and turn cues belong to ATT-1450 (Turn-by-Turn Navigation Cues). LiveClimbSheet provides ambient visual and haptic presentation.
  3. *Modification of Existing Strava API / Telemetry Recording Pipeline*: Workout recording and Strava segment matching algorithms remain unchanged.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement only (`REQ-MAP-027`). No existing requirements modified.
* **Historical Origin & Commit Trace**: Ticket `ATT-1281`, sprint `2026-40.14`, target release `V4.9.39`, Epic `ATT-66` (*[Epic] Improve Routes*).
* **Root Reason for Existing Formulation**: Athletes tracking workouts on hilly terrain lack visibility into upcoming climbs, summit countdowns, and real-time climbing profiles unless following a third-party head unit.
* **Preservation of Core Invariants**: Existing `RoutesDatabaseManager` (schema v10), `LiveSegmentsRepository`, Strava segment detection, and 100% full-suite unit test pass rate remain strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Architecture Overview

```
                                  [GPX / TCX / Strava Route]
                                              │
                                              ▼
                                      [ClimbDetector]
                         (Smooths profile, detects >=500m & >=3% ascents)
                                              │
                                              ▼
                                 [ClimbsDatabaseManager]
                           (Deduplicates & stores in Climbs.db)
                                              ▲
                                              │
                    ┌─────────────────────────┴─────────────────────────┐
                    │                                                   │
         [Free Riding Mode]                                  [Route Navigation Mode]
   (Queries nearby climbs in DB)                        (Pre-orders climbs along route)
                    │                                                   │
                    └─────────────────────────┬─────────────────────────┘
                                              ▼
                                   [LiveClimbsRepository]
                    (StateFlow<LiveClimbState>, virtual start/end gates,
                     approaching <= 250m, on_climb, finished)
                                              │
                                              ▼
                                     [SensorGridScreen]
                     (Priority arbiter: Strava Segment > Live Climb)
                                              │
                                              ▼
                                      [LiveClimbSheet]
                   (Real-time profile, remaining meters & climb, grade %)
```

### 5.2 Key Components

1. **`Climb.kt` (`models/Climb.kt`)**:
   Data class representing a climb, its category (`ClimbCategory`: `CAT_4`, `CAT_3`, `CAT_2`, `CAT_1`, `HC`, `UNCATEGORIZED`), path points, and live tracking telemetry.
2. **`ClimbDetector.kt` (`climbs/ClimbDetector.kt`)**:
   Pure functional mathematical engine detecting climbs along a sequence of `PathPoint` objects using configurable thresholds.
3. **`ClimbsDatabaseManager.kt` (`database/ClimbsDatabaseManager.kt`)**:
   SQLite database manager (`Climbs.db`) storing climbs, supporting spatial bounding queries and deduplication within 50m.
4. **`LiveClimbsRepository.kt` (`climbs/LiveClimbsRepository.kt`)**:
   Monitors `BANALServiceRepository.currentLocation` and `RoutesRepository.activeNavigatedRouteId`, evaluating proximity and start/summit crossings.
5. **`LiveClimbSheet.kt` (`ui/climbs/LiveClimbSheet.kt`)**:
   Material 3 Compose bottom sheet with color-coded slope segments (`TTColor.Zone1` to `Zone5`) and real-time summit countdown metrics.
6. **`SensorGridScreen.kt` Integration**:
   Dynamic bottom sheet container arbitrating between Strava Segments and Live Climbs.
7. **`TuningPreferencesDataStore.kt`**:
   Preferences for `showLiveClimbs` (default: true), `climbMinLengthMeters` (default: 500), `climbMinGradientPercent` (default: 3.0).

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. 100% clean-room unit test pass rate across all project modules.
  2. Non-interference with Strava Live Segments: Strava Live Segments retain top priority when active.
  3. Single-threaded SQLite database access guaranteeing thread safety and zero database locks.
  4. Parent ticket Human Decision Gate remains strictly guarded (parent terminal status: `Final Review (Human)`).
* **Risk Rating**: **LOW**
  - All additions are modular and non-intrusive.
  - The climbs database is independent, and the detection logic is pure math covered by comprehensive unit tests.
