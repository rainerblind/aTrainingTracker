# Stage 3: Implementation Plan - ATT-3317: Configure route elevation profile window scope and rolling lookahead distance individually per tracking tab

**Ticket**: [ATT-3317](https://rainerblind.atlassian.net/browse/ATT-3317)  
**Sub-task**: [ATT-3386](https://rainerblind.atlassian.net/browse/ATT-3386) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-355` (amending & extending with Clause 5)  
**Test Mapping**: `TST-UI-315` (extending test specification)  
**Branch**: `feature/ATT-3317`  
**Author**: AI Agent 1 (Software Construction Planner)  
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

## 1. Architectural Decomposition (SWE.2)

```
[ TrackingViewsDatabaseManager (SQLite VIEWS_TABLE, DB v13) ]
                     │ (IO Thread / Dispatchers.IO)
                     ▼
        [ TrackingViewsRepository ]
                     │ (StateFlow<TrackingViewInfo>)
                     ▼
          [ TrackingViewModel ]
                     │ (StateFlow<TrackingScreenState>)
                     ▼
          [ SensorGridScreen ]
                     │
                     ├─► [ TrackingElevationProfileResolver.resolve ]
                     │        - scope: FULL_ROUTE | ROLLING_WINDOW
                     │        - lookaheadDistanceMeters: 500..10000 (default 2000)
                     │        - 15% athlete position centering
                     │        - boundary clamping [0.0, D_route]
                     │        - route path slicing & interpolation
                     │        - categorized climb intersection filtering
                     │
                     └─► [ ElevationProfile Composable ]
                              - supports non-zero start distance
                              - dynamic altitude bounds & canonical adaptive height
```

1. **Storage Layer (`TrackingViewsDatabaseManager.java`, `TrackingViewsDbHelper`)**:
   - Increments SQLite schema version from 12 to 13.
   - Adds `ElevationProfileScope` (`INTEGER DEFAULT 0`) and `ElevationProfileLookahead` (`INTEGER DEFAULT 2000`) to `VIEWS_TABLE`.
   - Implements `updateElevationProfileScope(long viewId, int scope)` and `updateElevationProfileLookahead(long viewId, int lookaheadMeters)`.

2. **Repository Layer (`TrackingViewsRepository.kt`)**:
   - Defines `enum class ElevationProfileScope(val dbValue: Int) { FULL_ROUTE(0), ROLLING_WINDOW(1) }`.
   - Updates `TrackingViewInfo` data class with `elevationProfileScope: ElevationProfileScope = ElevationProfileScope.FULL_ROUTE` and `elevationProfileLookahead: Int = 2000`.
   - Exposes suspend functions `updateElevationProfileScope` and `updateElevationProfileLookahead` triggering configuration updates.

3. **Presentation / State Layer (`TrackingViewModel.kt`)**:
   - Extends `TrackingScreenState` with `elevationProfileScope` and `elevationProfileLookahead`.
   - Maps `TrackingViewInfo` fields into `TrackingScreenState`.
   - Exposes `onUpdateElevationProfileScope(scope)` and `onUpdateElevationProfileLookahead(lookaheadMeters)`.

4. **Domain Resolution Layer (`TrackingElevationProfileResolver.kt`)**:
   - Extends `resolve(...)` with parameters `scope: ElevationProfileScope = ElevationProfileScope.FULL_ROUTE` and `lookaheadDistanceMeters: Int = 2000`.
   - When `scope == ElevationProfileScope.ROLLING_WINDOW`:
     - Computes $D_{\text{behind}} = \frac{3}{17} D_{\text{forward}}$ and $D_{\text{window}} = \frac{20}{17} D_{\text{forward}}$.
     - Clamps window $[W_{\text{start}}, W_{\text{end}}]$ within $[0.0, D_{\text{route}}]$.
     - Slices path points and linearly interpolates coordinates/altitude at boundary points.
     - Filters climbs intersecting $[W_{\text{start}}, W_{\text{end}}]$.

5. **Canvas Rendering Layer (`ElevationProfile.kt`)**:
   - Derives `baseStartDist = pathPointsDownsampled.firstOrNull()?.distance ?: 0.0`.
   - Sets `totalDist = (pathPointsDownsampled.lastOrNull()?.distance ?: 0.0) - baseStartDist`.
   - Computes canvas X positions relative to `baseStartDist`.
   - Displays start distance tick/label when `baseStartDist > 0.0`.

6. **Configuration UI Layer (`SensorGridScreen.kt`)**:
   - In `ScreenMode.CONFIGURATION`, renders scope toggle and lookahead preset chips beneath the elevation profile toggle card.
   - Wires callbacks through `TabToggleActions` to `TrackingViewModel`.

---

## 2. UI Consistency (Rule 23)

- **Closest Reference Screen**: `SensorGridScreen.kt` configuration mode (lines 448–456, `SpatialCockpitToggleCard`).
- **Reused Components**:
  - `SpatialCockpitToggleCard` (for the parent elevation profile toggle).
  - Material 3 `FilterChip` and `FilterChipDefaults` for lookahead presets (500m, 1km, 2km, 5km, 10km).
  - Material 3 `Surface`, `Row`, `Column`, `Text` adhering to existing token styling.
- **Theme Tokens**:
  - Colors: `MaterialTheme.colorScheme.primary`, `surfaceVariant`, `onSurfaceVariant`, `outline`.
  - Spacing: `8.dp` horizontal/vertical padding, `4.dp` chip gaps.
  - Shapes: `RoundedCornerShape(8.dp)` matching existing spatial toggle cards.
  - Typography: `MaterialTheme.typography.labelMedium`, `labelSmall`.
- **Zero Ad-Hoc Styling**: Strictly adheres to app design guidelines without arbitrary hardcoded hex colors or non-standard padding.

---

## 3. Atomic Step Sequencing

### Step 1: Database Migration & Schema Upgrade in `TrackingViewsDatabaseManager.java`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java`.
- Increment `DB_VERSION` to 13.
- Define constants `ELEVATION_PROFILE_SCOPE = "ElevationProfileScope"` and `ELEVATION_PROFILE_LOOKAHEAD = "ElevationProfileLookahead"`.
- Add columns to `VIEWS_CREATE` table definition.
- Add `onUpgrade` case for DB version 12 -> 13 using `ALTER TABLE VIEWS_TABLE ADD COLUMN ...`.
- Add `updateElevationProfileScope(long viewId, int scope)` and `updateElevationProfileLookahead(long viewId, int lookaheadMeters)`.
- Update `readTrackingView(Cursor cursor)` to parse new columns.

### Step 2: Repository Domain Model & Methods in `TrackingViewsRepository.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingViewsRepository.kt`.
- Define `enum class ElevationProfileScope(val dbValue: Int) { FULL_ROUTE(0), ROLLING_WINDOW(1) }`.
- Add `elevationProfileScope` and `elevationProfileLookahead` to `TrackingViewInfo`.
- Implement `updateElevationProfileScope` and `updateElevationProfileLookahead` with `Dispatchers.IO` dispatch.

### Step 3: ViewModel State & Dispatch in `TrackingViewModel.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt`.
- Add `elevationProfileScope` and `elevationProfileLookahead` to `TrackingScreenState`.
- Populate fields in `combine` flow.
- Add `onUpdateElevationProfileScope` and `onUpdateElevationProfileLookahead`.
- Update `TabToggleActions` in `SensorGridScreen.kt` to include scope and lookahead callbacks.

### Step 4: Geometry, Slicing & Centering in `TrackingElevationProfileResolver.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingElevationProfileResolver.kt`.
- Add `scope` and `lookaheadDistanceMeters` parameters to `resolve()`.
- Implement rolling window calculation:
  - $D_{\text{behind}} = \frac{3}{17} D_{\text{forward}}$.
  - Window clamping at $[0.0, D_{\text{route}}]$.
  - Path point filtering and boundary interpolation.
  - Intersecting climbs filtering.

### Step 5: Canvas Rendering Support for Slices in `ElevationProfile.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`.
- Support non-zero start distance from sliced path points.
- Map distance to canvas X relative to `baseStartDist`.
- Render start label when `baseStartDist > 0.0`.

### Step 6: Configuration UI in `SensorGridScreen.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`.
- In `ScreenMode.CONFIGURATION`, when `showElevationProfile == true`, display scope selector and lookahead chips.
- Wire callbacks to `tabToggleActions`.

### Step 7: 9-Language Localization
- Files: `app/src/main/res/values*/strings.xml`.
- Add string tokens for Full Route, Rolling Window, and lookahead presets across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Step 8: Targeted Unit & Integration Tests
- Files:
  - `TrackingElevationProfileResolutionTest.kt`
  - `TrackingViewsDatabaseManagerTest.kt`
  - `ElevationProfileCoordinateTest.kt`
  - `TranslationParityTest.kt`

---

## 4. Invariant Protection & Rollback Safety

1. **Full Route Default Invariant**: Existing tabs and newly created tabs default to `FULL_ROUTE` and `2000m`, ensuring zero disruption or unexpected visual changes for athletes.
2. **Free Tracking Fallback Invariant**: When no route is active, the profile seamlessly displays recorded workout history starting at 0.0 m.
3. **Adaptive Height & Sizing Invariant**: Canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) and non-weighted wrapping `Surface` (`REQ-UI-346`) remain strictly preserved.
4. **Localization Parity Invariant**: 100% string resource coverage across all 9 languages without fallback crashes.
5. **Clean-Room Regression Invariant**: 100% full-suite unit test pass rate with zero test failures.
