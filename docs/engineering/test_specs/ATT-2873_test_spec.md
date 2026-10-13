# Stage 2: Requirement & Test Specification - ATT-2873: Optimize in-ride fork route detection performance via spatial bounding-box rejection and GPS throttling

**Ticket**: [ATT-2873](https://rainerblind.atlassian.net/browse/ATT-2873)  
**Sub-task**: [ATT-2922](https://rainerblind.atlassian.net/browse/ATT-2922) (`[Test-Spec]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Requirement Mapping**: `REQ-MAP-038` (*In-Ride Fork Route Detection Performance Optimization: Spatial Bounding-Box Rejection, Sport-Type Pre-Filtering, and Location Evaluation Throttling*)  
**Test Spec ID**: `TST-MAP-040`  
**Branch**: `improvement/ATT-2873`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Formal Requirement Specification (`REQ-MAP-038`)

### Requirement Text
The system SHALL optimize in-ride fork-in-the-road route detection performance in `ForkRouteMatcher.kt` and `ForkNavigationRepository.kt` by eliminating brute-force polyline projections for geographically distant routes, filtering by workout sport profile, and throttling location candidate evaluation during quiescent tracking (ATT-2873, amending `REQ-MAP-031`):

1. **Spatial Bounding-Box Pre-Filtering ($O(1)$ Spatial Rejection)**:
   * In `ForkRouteMatcher.findCandidateRoutes()`, the engine SHALL evaluate an initial spatial bounding-box check before executing `projectOntoPolyline()`.
   * `BOUNDING_BOX_CORRIDOR_MARGIN_METERS = 100.0`.
   * The route's latitude/longitude bounding box SHALL be determined from `route.summary.minLat/maxLat/minLng/maxLng` (or computed from `route.path` if null).
   * If `currentPos` lies outside `[minLat - deltaLat, maxLat + deltaLat]` or `[minLng - deltaLng, maxLng + deltaLng]`, the route SHALL be immediately discarded in $O(1)$, bypassing all polyline segment iterations and trigonometric calculations.
2. **Sport-Type Pre-Filtering**:
   * `ForkRouteMatcher.findCandidateRoutes()` SHALL accept an optional `activeSportType: BSportType? = null` parameter.
   * If `activeSportType` is specified, candidate routes SHALL be pre-filtered using `RouteProximityRanker.matchesSport(route.summary.bSportType, activeSportType)`. Incompatible sport routes (e.g. running routes during a cycling workout) SHALL be discarded before geometric projection.
3. **Location Evaluation Throttling during Quiescent Tracking**:
   * In `ForkNavigationRepository`, when no fork decision alert is active (`_forkDecisionState.value == null`), candidate route searches SHALL be throttled:
     * Minimum time interval: `QUIESCENT_THROTTLE_INTERVAL_MS = 3000L` (3 seconds).
     * Minimum displacement threshold: `QUIESCENT_THROTTLE_DISTANCE_METERS = 20.0` (20 meters).
     * If both elapsed time $< 3000\text{ms}$ and distance delta $< 20\text{m}$, candidate route search SHALL be skipped.
   * *Active Alert Responsiveness Preservation*: When a fork decision alert is active (`_forkDecisionState.value != null`), candidate evaluation SHALL execute on every location update (1 Hz) to ensure real-time remaining distance countdown and immediate autonomous route binding.
4. **Preservation of System Invariants**:
   * Divergence alert accuracy ($D_{\text{fork}} \le 300\text{ m}$), auto-binding distance ($\ge 50\text{ m}$ past fork), corridor tolerance ($50.0\text{ m}$), and 100% test pass rate across the full test suite MUST be strictly preserved.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Refines and amends `REQ-MAP-031` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts*, Clauses 1 & 4).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1955` (Sprint 2026-40.16).
3. *Root Reason for Existing Formulation*: Originally authored as an MVP focusing on core geometric calculations without spatial partitioning or rate limiting.
4. *Preservation of Core Invariants*: Bounding-box margin ($\ge 100\text{m}$) strictly envelopes the corridor tolerance ($50.0\text{m}$), mathematically guaranteeing zero false negatives. Auto-binding latency and 100% unit test pass rate remain preserved.

### Acceptance Criteria (Given-When-Then)
* **AC-1 (Bounding-Box Rejection)**:
  * *Given* an athlete tracking a workout at `currentPos`,
  * *When* evaluating routes whose bounding box is $> 100\text{m}$ away,
  * *Then* `ForkRouteMatcher.findCandidateRoutes()` SHALL reject those routes in $O(1)$ without computing segment projections.
* **AC-2 (Sport Filtering)**:
  * *Given* an active cycling workout (`activeSportType = BSportType.BIKE`),
  * *When* candidate routes are evaluated,
  * *Then* running routes (`BSportType.RUN`) SHALL be discarded immediately.
* **AC-3 (Quiescent Throttling)**:
  * *Given* tracking without an active fork prompt,
  * *When* location updates arrive within $< 3000\text{ms}$ and $< 20\text{m}$ displacement,
  * *Then* `ForkNavigationRepository` SHALL skip candidate route search.
* **AC-4 (Active Alert Responsiveness)**:
  * *Given* an active fork decision alert ($D_{\text{fork}} \le 300\text{m}$),
  * *When* location updates arrive,
  * *Then* `ForkNavigationRepository` SHALL evaluate on every fix (1 Hz) without throttling.

---

## 2. Test Specification (`TST-MAP-040`)

### Verification Plan
| Test ID | Scope | Target Component | Method |
| :--- | :--- | :--- | :--- |
| `TST-MAP-040.1` | Unit | `ForkRouteMatcherTest` | Bounding-box spatial rejection ($O(1)$) |
| `TST-MAP-040.2` | Unit | `ForkRouteMatcherTest` | Sport-type pre-filtering |
| `TST-MAP-040.3` | Unit | `ForkNavigationRepositoryTest` | Quiescent candidate evaluation throttling |
| `TST-MAP-040.4` | Unit | `ForkNavigationRepositoryTest` | Unthrottled 1 Hz responsiveness during active fork alerts |
| `TST-MAP-040.5` | Regression | Full Test Suite | Clean-room `./gradlew testDebugUnitTest` execution |

### 9-Language Localization Audit
No new user-facing strings are introduced in `ATT-2873` (pure algorithmic and repository performance optimization).
Existing string keys and translation parity remain 100% verified.

---

## 3. Traceability Matrix

| Requirement Clause | Verification Test | Target File | Status |
| :--- | :--- | :--- | :---: |
| `REQ-MAP-038.1` (Bounding-box rejection) | `TST-MAP-040.1` | `ForkRouteMatcherTest.kt` | Specified |
| `REQ-MAP-038.2` (Sport-type pre-filtering) | `TST-MAP-040.2` | `ForkRouteMatcherTest.kt` | Specified |
| `REQ-MAP-038.3` (Quiescent throttling) | `TST-MAP-040.3` | `ForkNavigationRepositoryTest.kt` | Specified |
| `REQ-MAP-038.3` (Active alert responsiveness) | `TST-MAP-040.4` | `ForkNavigationRepositoryTest.kt` | Specified |
| `REQ-PRO-001` (Zero regressions) | `TST-MAP-040.5` | Full Test Suite | Specified |
