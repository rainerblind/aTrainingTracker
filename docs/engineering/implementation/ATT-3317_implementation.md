# Stage 4: Implementation Summary - ATT-3317: Configure route elevation profile window scope and rolling lookahead distance individually per tracking tab

**Ticket**: [ATT-3317](https://rainerblind.atlassian.net/browse/ATT-3317)  
**Sub-task**: [ATT-3388](https://rainerblind.atlassian.net/browse/ATT-3388) (`[Implementation]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-355` (Clause 5)  
**Test Mapping**: `TST-UI-315`  
**Branch**: `feature/ATT-3317`  
**Author**: AI Agent 1 (Software Construction Engineer)  
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

## 1. Summary of Changes

### 1.1 `TrackingViewsDatabaseManager.java` (Database Schema Upgrade v12 -> v13)
- Upgraded SQLite schema version `DB_VERSION` from 12 to 13.
- Added constants `ELEVATION_PROFILE_SCOPE` ("ElevationProfileScope") and `ELEVATION_PROFILE_LOOKAHEAD` ("ElevationProfileLookahead").
- Created `CREATE_VIEWS_TABLE_V13` table definition with new columns:
  - `ElevationProfileScope int DEFAULT 0` (0 = Full Route, 1 = Rolling Window).
  - `ElevationProfileLookahead int DEFAULT 2000` (distance in meters, default 2,000m).
- Implemented `onUpgrade` migration block adding columns via `ALTER TABLE ... ADD COLUMN` and populating defaults for existing records.
- Added `updateElevationProfileScope(long viewId, int scope)` and `updateElevationProfileLookahead(long viewId, int lookaheadMeters)`.
- Updated `addEmptyView` and `addDefaultTab` with default values (scope = 0, lookahead = 2000).

### 1.2 `TrackingViewsRepository.kt` (Domain & Storage Abstraction)
- Introduced `enum class ElevationProfileScope(val dbValue: Int) { FULL_ROUTE(0), ROLLING_WINDOW(1) }`.
- Extended `TrackingViewInfo` with `elevationProfileScope: ElevationProfileScope = ElevationProfileScope.FULL_ROUTE` and `elevationProfileLookahead: Int = 2000`.
- Updated `fetchTrackingViewInfo` and `getTrackingViews` queries to read `ElevationProfileScope` and `ElevationProfileLookahead`.
- Added suspend mutation functions `updateElevationProfileScope` and `updateElevationProfileLookahead` dispatching to `Dispatchers.IO` and incrementing `configUpdateTrigger`.

### 1.3 `TrackingViewModel.kt` (StateFlow & ViewModel Actions)
- Extended `TrackingScreenState` with `elevationProfileScope` and `elevationProfileLookahead`.
- Mapped view info fields in the state combine flow.
- Added `onUpdateElevationProfileScope(scope)` and `onUpdateElevationProfileLookahead(lookaheadMeters)` actions.

### 1.4 `TrackingElevationProfileResolver.kt` (Domain Slicing & 15% Athlete Centering)
- Added `scope: ElevationProfileScope` and `lookaheadDistanceMeters: Int` parameters to `resolve()`.
- Implemented rolling window calculation:
  - $D_{\text{forward}} = lookaheadDistanceMeters$.
  - $D_{\text{behind}} = \frac{3}{17} D_{\text{forward}}$ (centering athlete marker at exactly 15% from left edge).
  - $D_{\text{window}} = D_{\text{behind}} + D_{\text{forward}} = \frac{20}{17} D_{\text{forward}}$.
- Implemented boundary clamping:
  - Start boundary: clamped to $0.0$ if $alongTrackDist - D_{\text{behind}} < 0.0$.
  - Finish boundary: clamped to $D_{\text{route}}$ if $alongTrackDist + D_{\text{forward}} > D_{\text{route}}$.
- Implemented `slicePath(path, startDist, endDist)` with linear interpolation at boundaries for seamless edge-to-edge profile curves.
- Filtered climbs intersecting the visible window ($climbEnd \ge W_{\text{start}} \land climbStart \le W_{\text{end}}$).

### 1.5 `ElevationProfileZoomMath.kt` & `ElevationProfile.kt` (Coordinate Invariants & Canvas Rendering)
- Updated `ElevationProfileZoomMath.canvasXToDistance` to support non-zero start distance from sliced rolling windows.
- Updated `ElevationProfile.kt`:
  - `startDist` defaults to `pathPoints.firstOrNull()?.distance ?: 0.0`.
  - `totalDist` computed as `(pathPoints.last().distance - pathPoints.first().distance).coerceAtLeast(1.0)`.
  - Rendered start distance label at bottom-left when window has non-zero start distance (`currentStartDist > 1.0`).

### 1.6 `SensorGridScreen.kt` & `TrackingTabGridContent.kt` (Cockpit Configuration UI)
- Extended `TabToggleActions` with `onUpdateElevationProfileScope` and `onUpdateElevationProfileLookahead`.
- In `ScreenMode.CONFIGURATION`, when `showElevationProfile == true`, rendered Scope selector (`FilterChip` for Full Route and Rolling Window) and Lookahead preset chips (`500m`, `1km`, `2km`, `5km`, `10km`).
- Passed `scope` and `lookaheadDistanceMeters` into `TrackingElevationProfileResolver.resolve(...)`.

### 1.7 9-Language Localization Parity
- Added string tokens across all 9 supported locales:
  - `config_tracking__elevation_profile_scope`
  - `config_tracking__elevation_profile_scope_full_route`
  - `config_tracking__elevation_profile_scope_rolling_window`
  - `config_tracking__elevation_profile_lookahead`

---

## 2. Targeted Verification Results

1. **`TrackingElevationProfileResolutionTest`**:
   - `testResolve_fullRouteScope_returnsEntireRoute` -> PASSED
   - `testResolve_rollingWindowScope_2000m_centersAthleteAt15Percent` -> PASSED
   - `testResolve_rollingWindowScope_presets` (500m, 1km, 2km, 5km, 10km) -> PASSED
   - `testResolve_rollingWindowScope_startBoundaryClamping` -> PASSED
   - `testResolve_rollingWindowScope_finishBoundaryClamping` -> PASSED
   - `testResolve_rollingWindowScope_climbsFiltering` -> PASSED
   - `testResolve_fallbackWhenNoRoute` -> PASSED
   - `testSlicePath_interpolatesExactBoundaryAltitudes` -> PASSED
2. **`SensorGridScreenElevationProfileTest`**: PASSED (3s)
3. **`TrackingViewsRepositoryTest`**: PASSED (7s)
4. **`ElevationProfileCoordinateTest`**: PASSED (4s)
5. **`TranslationParityTest`**: PASSED (100% parity across all 9 languages)
