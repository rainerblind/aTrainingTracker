# Stage 5: Walkthrough - ATT-3317: Configure route elevation profile window scope and rolling lookahead distance individually per tracking tab

**Ticket**: [ATT-3317](https://rainerblind.atlassian.net/browse/ATT-3317)  
**Sub-task**: [ATT-3389](https://rainerblind.atlassian.net/browse/ATT-3389) (`[Test]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-355` (Clause 5)  
**Test Mapping**: `TST-UI-315`  
**Branch**: `feature/ATT-3317`  
**Author**: AI Agent 1 (Verification & Quality Engineer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-UI-355` (*Active Route Full Elevation Profile with Map-Matched Position Marker and Categorized Climbs During Tracking*), targeting `TrackingElevationProfileResolver.kt`, `SensorGridScreen.kt`, `ElevationProfile.kt`, `TrackingViewsDatabaseManager.java`, `TrackingViewsRepository.kt`, `TrackingViewModel.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-41.10 (`ATT-3199`, commit `e0197e08`) introduced route elevation profile switching.
3. **Root Reason for Existing Formulation**:
   - `ATT-3199` provided full route elevation profile rendering to give athletes forward visibility instead of only showing historical recorded points. However, on long courses (50–150 km), rendering the entire route uniformly squashes micro-terrain and immediate hill gradients. The athlete requested per-tab configurable scoping to view the full route on one tab (e.g. Map) and a detailed rolling lookahead window on another tab (e.g. Cockpit).
4. **Preservation of Core Invariants**:
   - Full Route Default: `ElevationProfileScope.FULL_ROUTE` remains the default, ensuring 100% backward compatibility for existing tabs and workflows.
   - Free Tracking Fallback: When tracking without an active route, fallback to recorded workout path points remains 100% intact.
   - Categorized Climbs Overlay: Climbs overlay, category coloring (CAT 4/3/2/1/HC), and summit badges remain intact in both full route and rolling window modes.
   - Map-Matched Projection: Orthogonal GPS map-matching via `PolylineProgressTracker` remains the source of truth for the athlete's along-track position.
   - Layout & Sizing Invariants: Canonical adaptive height calculation ($70\text{dp} \dots 200\text{dp}$) and non-weighted wrapping `Surface` per `REQ-UI-346` remain strictly preserved.
   - Clean-Room Test Pass Rate: 100% unit test pass rate across all modules.

---

## 1. Executive Summary

This deliverable fulfills Stage 5 verification for **ATT-3317**. Prior to ATT-3317, following a navigated route displayed the entire elevation profile from 0 km to finish. On long routes (50–150 km), rendering the full course squashed elevation details, making it difficult to judge upcoming gradients or immediate rollers.

ATT-3317 introduces per-tab configurable elevation profile scoping:
1. **Scope Mode Selector**: Each tracking tab can independently choose between `FULL_ROUTE` (default macro course overview) and `ROLLING_WINDOW` (micro tactical lookahead).
2. **Rolling Lookahead Presets**: In `ROLLING_WINDOW` mode, athletes can configure the lookahead distance: `500m`, `1km`, `2km` (default), `5km`, or `10km`.
3. **15% Athlete Centering**: The athlete marker is locked at 15% from the left edge of the visible window ($D_{\text{behind}} = \frac{3}{17} D_{\text{forward}}$), providing immediate context of past terrain while dedicating 85% of visible canvas width to the forward horizon.
4. **Boundary Clamping & Dynamic Slicing**: Windows are smoothly clamped at route start (0.0 m) and route finish ($D_{\text{route}}$). Path points are dynamically sliced with linear interpolation at boundaries, and categorized climbs intersecting the visible span are retained.
5. **Database Persistence**: SQLite schema upgraded from version 12 to 13 (`VIEWS_TABLE` columns `ElevationProfileScope` and `ElevationProfileLookahead`).
6. **Localization Parity**: 100% translation parity maintained across all 9 supported languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 2. Verification Results

### 2.1 Targeted Unit Testing
- **`TrackingElevationProfileResolutionTest`**:
  - `testResolve_fullRouteScope_returnsEntireRoute`: **PASSED**
  - `testResolve_rollingWindowScope_2000m_centersAthleteAt15Percent`: **PASSED** ($D_{\text{behind}} \approx 352.9\text{m}$, marker at 15%)
  - `testResolve_rollingWindowScope_presets`: **PASSED** (verifies 500m, 1km, 2km, 5km, 10km presets)
  - `testResolve_rollingWindowScope_startBoundaryClamping`: **PASSED** (start clamped at 0.0 m)
  - `testResolve_rollingWindowScope_finishBoundaryClamping`: **PASSED** (end clamped at route distance)
  - `testResolve_rollingWindowScope_climbsFiltering`: **PASSED** (intersecting climbs retained, non-intersecting excluded)
  - `testResolve_fallbackWhenNoRoute`: **PASSED** (free tracking fallback preserved)
  - `testSlicePath_interpolatesExactBoundaryAltitudes`: **PASSED** (linear interpolation exact)
- **`TrackingViewsDatabaseManagerTest`**:
  - `testDatabaseVersionIs13`: **PASSED**
  - `testOnUpgrade_from12To13_addsElevationProfileColumns`: **PASSED**
  - `testUpdateElevationProfileScope_updatesViewsTable`: **PASSED**
  - `testUpdateElevationProfileLookahead_updatesViewsTable`: **PASSED**
- **`TrackingViewsRepositoryTest`**: **PASSED** (domain queries and persistence triggers verified)
- **`SensorGridScreenElevationProfileTest`**: **PASSED** (scope switch and lookahead chips in configuration mode verified)
- **`ElevationProfileCoordinateTest`**: **PASSED** (non-zero start distance coordinate transformation verified)
- **`ElevationProfileContractTest`**: **PASSED** (tick label clearance and contract invariants verified)
- **`ElevationProfileLayoutTest`**: **PASSED** (composable parameter signatures and layout invariants verified)
- **`TranslationParityTest`**: **PASSED** (100% translation parity across all 9 locales: EN, DE, ES, FR, IT, JA, NL, PL, PT)

### 2.2 Clean-Room Test Suite Execution
- **Command**: `./gradlew testDebugUnitTest`
- **Result**: **BUILD SUCCESSFUL** (2,521+ tests executed, 100% pass rate, 0 failures)
- **Execution Time**: 2m 30s

---

## 3. Living Documentation Status

- **`docs/requirements.md`**: `REQ-UI-355` updated to `Verified`.
- **`docs/tests.md`**: `TST-UI-315` updated to `Verified`.
- **Chesterton's Fence Audit**: Verified preserving full route default, free tracking fallback, categorized climb overlays, map-matched orthogonal position projection, and canonical adaptive height.

---

## 4. Subtask & Ticket Advancement Plan

1. Subtask **ATT-3389** (`[Test]`) description updated with this walkthrough.
2. Subtask moved to `In Überprüfung` and Gate 5 audit initiated via `tools/review_agent.py audit ATT-3389`.
3. Upon Gate 5 sign-off (`RECOMMEND PASS`), subtask moved to `Erledigt`.
4. Parent ticket **ATT-3317** stepped through lifecycle transitions to `Final Review (Human)`.
5. Changes committed to `feature/ATT-3317`.
6. Branch `feature/ATT-3317` merged into `sprint/2026-42.1` with `--no-ff`, and `feature/ATT-3317` deleted.
