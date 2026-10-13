# Stage 2: Requirement & Test Specification - ATT-3317: Configure route elevation profile window scope and rolling lookahead distance individually per tracking tab

**Ticket**: [ATT-3317](https://rainerblind.atlassian.net/browse/ATT-3317)  
**Sub-task**: [ATT-3385](https://rainerblind.atlassian.net/browse/ATT-3385) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-355` (amending & extending with Clause 5)  
**Test Mapping**: `TST-UI-315` (extending test specification)  
**Branch**: `feature/ATT-3317`  
**Author**: AI Agent 1 (Requirement & Test Specification Engineer)  
**Date**: 2026-10-13  

---

## 1. Functional Requirement Specification

### REQ-UI-355 (Amended Clause 5): Per-Tab Elevation Profile Scope & Rolling Lookahead Window

*The system SHALL dynamically switch the live tracking elevation profile data source to the active navigated route when a route is actively followed, displaying route elevation, categorized climbs, and a map-matched position progress marker, and SHALL support individual per-tab visible window scoping and rolling lookahead configuration (ATT-3199, ATT-3317, extending `REQ-UI-346` and aligning with Design Guidelines §2.3):*

1. **Per-Tab Scope Configuration (`TrackingViewsDatabaseManager.java`, `TrackingViewsRepository.kt`)**:
   - The database schema for `VIEWS_TABLE` SHALL be upgraded to version 13 (`DB_VERSION = 13`):
     - `ElevationProfileScope`: `int DEFAULT 0` (0 = `FULL_ROUTE`, 1 = `ROLLING_WINDOW`).
     - `ElevationProfileLookahead`: `int DEFAULT 2000` (distance in meters, default 2,000 m).
   - `TrackingViewInfo` SHALL expose:
     - `elevationProfileScope: ElevationProfileScope = ElevationProfileScope.FULL_ROUTE`
     - `elevationProfileLookahead: Int = 2000`
   - Default values for existing and new tabs SHALL be `FULL_ROUTE` and `2000`, preserving complete backward compatibility.

2. **Rolling Window Geometry & Athlete Centering (`TrackingElevationProfileResolver.kt`)**:
   - Supported lookahead presets: 500 m, 1,000 m, 2,000 m, 5,000 m, 10,000 m ($D_{\text{forward}}$).
   - In `ROLLING_WINDOW` scope mode, the athlete position marker SHALL be centered at 15% from the left edge of the visible window:
     $$D_{\text{behind}} = \frac{15}{85} D_{\text{forward}} = \frac{3}{17} D_{\text{forward}}$$
     $$D_{\text{window}} = D_{\text{behind}} + D_{\text{forward}} = \frac{20}{17} D_{\text{forward}}$$
   - Window boundary clamping:
     - If total route distance $D_{\text{route}} \le D_{\text{window}}$, visible span covers the full route ($[0.0, D_{\text{route}}]$).
     - If $alongTrackDist - D_{\text{behind}} < 0.0$ (near route start), window start is clamped to $0.0$, window end to $D_{\text{window}}$, and the athlete marker advances from 0% towards 15%.
     - If $alongTrackDist + D_{\text{forward}} > D_{\text{route}}$ (near route finish), window end is clamped to $D_{\text{route}}$, window start to $\max(0.0, D_{\text{route}} - D_{\text{window}})$, and the athlete marker advances from 15% to 100% across the finish line.
     - General case: $W_{\text{start}} = alongTrackDist - D_{\text{behind}}$, $W_{\text{end}} = alongTrackDist + D_{\text{forward}}$. The athlete marker is locked at 15% from the left edge.

3. **Dynamic Path Point Slicing & Climbs Filtering (`TrackingElevationProfileResolver.kt`)**:
   - `TrackingElevationProfileResolver.resolve` SHALL extract route points within $[W_{\text{start}}, W_{\text{end}}]$.
   - Linear interpolation SHALL be applied at boundaries $W_{\text{start}}$ and $W_{\text{end}}$ to guarantee a smooth, continuous elevation profile curve from edge to edge.
   - Categorized climbs intersecting the window ($\text{climb.endDistance} \ge W_{\text{start}} \land \text{climb.startDistance} \le W_{\text{end}}$) SHALL be retained with their original route distance coordinates.

4. **ElevationProfile Coordinate Invariants (`ElevationProfile.kt`)**:
   - `ElevationProfile.kt` SHALL support path points with non-zero start distance ($D_{\text{start}} > 0.0$):
     - `baseStartDist = pathPointsDownsampled.firstOrNull()?.distance ?: 0.0`
     - `totalDist = (pathPointsDownsampled.lastOrNull()?.distance ?: 0.0) - baseStartDist`
     - Start distance label at left edge SHALL render $D_{\text{start}}$ when $D_{\text{start}} > 0.0$.
     - `calculateElevationBounds` dynamically calculates the vertical min/max altitude and adaptive height based exclusively on the sliced path points in the rolling window.

5. **Cockpit Tab Configuration UI (`SensorGridScreen.kt`)**:
   - In `ScreenMode.CONFIGURATION`, when `showElevationProfile == true`:
     - Provide a Scope Selector (Full Route / Rolling Window).
     - Provide Lookahead Distance chips (500m, 1km, 2km, 5km, 10km) when Rolling Window is selected.
     - Toggling options immediately persists to `TrackingViewsDatabaseManager` and triggers reactive updates.

6. **9-Language Localization Parity**:
   - String tokens for scope and lookahead options SHALL have 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

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

## 3. Acceptance Criteria (Given-When-Then)

- **AC-1 (Default Macro Full Route View)**:
  - *Given* an active workout tracking tab with `showElevationProfile = true` and `elevationProfileScope = FULL_ROUTE`,
  - *When* following an active route with elevation,
  - *Then* the elevation profile renders the complete route from 0 km to finish with the athlete marker at the along-track distance.

- **AC-2 (Rolling Window Lookahead Slicing & Centering)**:
  - *Given* a tracking tab with `showElevationProfile = true`, `elevationProfileScope = ROLLING_WINDOW`, and `elevationProfileLookahead = 2000` (2 km),
  - *When* the athlete is at route kilometer 10.0 along a 50 km route,
  - *Then* the elevation profile renders a sliced route path spanning from approximately km 9.647 to km 12.0, and the athlete marker is positioned at exactly 15% from the left edge of the visible window.

- **AC-3 (Start Boundary Clamping)**:
  - *Given* an athlete tracking in `ROLLING_WINDOW` mode at route kilometer 0.1 (100 m) with a 2 km lookahead,
  - *When* viewing the elevation profile,
  - *Then* the window start is clamped to 0.0 m, the window end is 2,352.9 m, and the athlete marker is positioned at $100 / 2352.9 \approx 4.25\%$ from the left edge.

- **AC-4 (Finish Boundary Clamping)**:
  - *Given* an athlete tracking in `ROLLING_WINDOW` mode at route kilometer 49.5 on a 50.0 km route with a 2 km lookahead,
  - *When* viewing the elevation profile,
  - *Then* the window end is clamped to 50.0 km, and the athlete marker advances smoothly towards 100% across the finish line.

- **AC-5 (Categorized Climbs Filtering)**:
  - *Given* a route with Climb 1 at km 5..7, Climb 2 at km 10..11.5, and Climb 3 at km 25..30,
  - *When* the athlete is at km 10.0 in `ROLLING_WINDOW` mode with a 2 km lookahead ($[9.647\text{ km} \dots 12.0\text{ km}]$),
  - *Then* Climb 2 is included and rendered on the elevation profile, while Climb 1 and Climb 3 are excluded.

- **AC-6 (Per-Tab Independence & Persistence)**:
  - *Given* Tab 1 configured with `FULL_ROUTE` and Tab 2 configured with `ROLLING_WINDOW` (1 km lookahead),
  - *When* switching between tabs,
  - *Then* each tab independently retains and displays its own configured elevation profile scope.

- **AC-7 (Fallback for Free Tracking)**:
  - *Given* a tab configured with `ROLLING_WINDOW` but no active navigated route is present,
  - *When* tracking a free workout,
  - *Then* the elevation profile falls back to rendering the recorded workout points starting at 0.0 m without crashing.

---

## 4. Test Specification (`TST-UI-315`)

### 4.1 Resolver Unit Tests (`TrackingElevationProfileResolutionTest.kt`)
1. `testResolve_fullRouteScope_returnsEntireRoute`:
   - Verify that when `scope == FULL_ROUTE`, all path points (0 km to total distance) and all climbs are returned, with marker at `alongTrackDist`.
2. `testResolve_rollingWindowScope_2000m_centersAthleteAt15Percent`:
   - Setup a 20 km route. Set `currentLocation` at km 10.0 ($alongTrackDist = 10000.0$).
   - Lookahead = 2000 m. $D_{\text{behind}} = \frac{3}{17} \times 2000 = 352.94\text{ m}$.
   - Verify sliced path start is approximately $9647.06\text{ m}$ and end is $12000.0\text{ m}$.
   - Verify marker distance is $10000.0\text{ m}$.
   - Relative marker position: $(10000 - 9647.06) / (12000 - 9647.06) = 352.94 / 2352.94 = 0.1500$ (15%).
3. `testResolve_rollingWindowScope_presets`:
   - Test presets: 500 m, 1,000 m, 2,000 m, 5,000 m, 10,000 m.
   - Verify $D_{\text{behind}} / (D_{\text{behind}} + D_{\text{forward}}) == 0.15$ within $\pm 0.001$.
4. `testResolve_rollingWindowScope_startBoundaryClamping`:
   - Set athlete at km 0.1 ($100\text{ m}$).
   - Verify window start is clamped to $0.0\text{ m}$.
   - Verify window end is $2352.94\text{ m}$.
5. `testResolve_rollingWindowScope_finishBoundaryClamping`:
   - Set athlete at km 19.5 on a 20.0 km route.
   - Verify window end is clamped to $20000.0\text{ m}$.
   - Verify window start is clamped to $20000 - 2352.94 = 17647.06\text{ m}$.
6. `testResolve_rollingWindowScope_climbsFiltering`:
   - Route has Climb A at km 2..4, Climb B at km 9.8..11.2, Climb C at km 15..17.
   - Athlete at km 10.0 ($[9647\text{ m} \dots 12000\text{ m}]$).
   - Verify returned climbs contains only Climb B.
7. `testResolve_rollingWindowScope_fallbackWhenNoRoute`:
   - Verify fallback to recorded workout points starting at 0.0 m when `activeNavigatedRoute == null`.

### 4.2 Database Migration & Tab Persistence Tests (`TrackingViewsDatabaseManagerTest.kt`)
1. `testDatabaseUpgrade_v12_to_v13`:
   - Verify migration adds `ElevationProfileScope` and `ElevationProfileLookahead` columns.
   - Verify existing rows receive default values (scope = 0, lookahead = 2000).
2. `testUpdateElevationProfileScope_and_Lookahead`:
   - Update tab 1 to `ROLLING_WINDOW` and lookahead `5000`.
   - Update tab 2 to `FULL_ROUTE`.
   - Verify querying both tabs returns their respective values independently.

### 4.3 ElevationProfile Non-Zero Start Distance Tests (`ElevationProfileCoordinateTest.kt`)
1. `testElevationProfile_withNonZeroStartDistance`:
   - Given path points from 10,000 m to 12,353 m.
   - Verify `baseStartDist` is 10,000 m, `visibleSpan` is 2,353 m.
   - Verify marker at 10,353 m maps to `canvasX = 0.15 * canvasWidth`.

### 4.4 9-Language Localization Audit (`TranslationParityTest.kt`)
1. Verify presence and validity of all new string tokens across all 9 supported locales:
   - `values/strings.xml` (EN)
   - `values-de/strings.xml` (DE)
   - `values-es/strings.xml` (ES)
   - `values-fr/strings.xml` (FR)
   - `values-it/strings.xml` (IT)
   - `values-ja/strings.xml` (JA)
   - `values-nl/strings.xml` (NL)
   - `values-pl/strings.xml` (PL)
   - `values-pt/strings.xml` (PT)

### 4.5 Clean-Room Regression Execution
1. Run `./gradlew testDebugUnitTest` asserting 100% pass rate.

---

## 5. Traceability Matrix

| Requirement Clause | Test Case | Test File | Target Artifact | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-355.5.1` (DB v13 Schema & Tab Persistence) | `testDatabaseUpgrade_v12_to_v13`, `testUpdateElevationProfileScope_and_Lookahead` | `TrackingViewsDatabaseManagerTest.kt` | `TrackingViewsDatabaseManager.java`, `TrackingViewsRepository.kt` | Specified |
| `REQ-UI-355.5.2` (Rolling Window Geometry & 15% Athlete Centering) | `testResolve_rollingWindowScope_2000m_centersAthleteAt15Percent`, `testResolve_rollingWindowScope_presets` | `TrackingElevationProfileResolutionTest.kt` | `TrackingElevationProfileResolver.kt` | Specified |
| `REQ-UI-355.5.3` (Boundary Clamping at Start and Finish) | `testResolve_rollingWindowScope_startBoundaryClamping`, `testResolve_rollingWindowScope_finishBoundaryClamping` | `TrackingElevationProfileResolutionTest.kt` | `TrackingElevationProfileResolver.kt` | Specified |
| `REQ-UI-355.5.4` (Path Slicing & Climbs Filtering) | `testResolve_rollingWindowScope_climbsFiltering` | `TrackingElevationProfileResolutionTest.kt` | `TrackingElevationProfileResolver.kt` | Specified |
| `REQ-UI-355.5.5` (ElevationProfile Sliced Coordinate Invariants) | `testElevationProfile_withNonZeroStartDistance` | `ElevationProfileCoordinateTest.kt` | `ElevationProfile.kt` | Specified |
| `REQ-UI-355.5.6` (Cockpit Configuration UI & State Dispatch) | `testSensorGridScreen_elevationProfileConfigurationControls` | `SensorGridScreenElevationProfileTest.kt` | `SensorGridScreen.kt`, `TrackingViewModel.kt` | Specified |
| `REQ-UI-355.5.7` (9-Language Localization Parity) | `testTranslationParity_elevationProfileScopeStrings` | `TranslationParityTest.kt` | `res/values*/strings.xml` | Specified |
| System Regression Invariants | Full clean-room test suite execution | Gradle test runner | Entire test suite | Specified |
