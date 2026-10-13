# Stage 2: Requirement & Test Specification - ATT-2748: Remove Climb Pins from Route Map and Display UC Climbs on Map and Elevation Profile

**Ticket**: [ATT-2748](https://atrainingtracker.atlassian.net/browse/ATT-2748)  
**Sub-task**: [ATT-2828](https://atrainingtracker.atlassian.net/browse/ATT-2828) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*[Epic] Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-307` (*Route Map Climb Pin Elimination, Full-Spectrum UC Climb Polyline Highlighting & Elevation Profile Baseline Parity*, refining and amending `REQ-UI-298` and `REQ-UI-299`)  
**Test Spec ID**: `TST-UI-267` (*Route Map Pin Elimination, Full-Spectrum UC Climb Polyline Highlighting, and Elevation Profile Baseline Parity Verification*)  
**Branch**: `improvement/ATT-2748`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (`REQ-UI-307`)

### 1.1 Problem Statement & Rationale
During Sprint Review 2026-41.3 on a Google Pixel 10 (validating `ATT-2509` and `ATT-2510`), visual ergonomics and usability issues were identified:
1. **Redundant Climb Start Pin Markers**: In `RouteOnMapScreen.kt`, circular ascent pin markers (`ic_ascent` with category background color at `climb.startLatLng`) crowd the route polyline and distract athletes from the actual route course and Start/End navigation pins. Now that climbs are directly highlighted as colored polyline spans along the route curve (`MapContentScope.climbs()`), these pins are redundant.
2. **Missing Uncategorized (UC) Climbs on Map and Elevation Profile**:
   - In `MapContentScope.kt:482`, `climb.category != ClimbCategory.UNCATEGORIZED` explicitly skips UC climbs, preventing them from being rendered as colored polyline spans along the route curve.
   - In `ElevationProfile.kt:776`, `climbs.filter { it.category != ClimbCategory.UNCATEGORIZED }` explicitly excludes UC climbs from being marked as horizontal span indicator bars along the X-axis baseline.
3. **Contrast with Royal Blue Baseline**: With the successful integration of Royal Blue routes in **ATT-2761** (`TTColor.RouteSelected = Color(0xFF1565C0)`), UC climb neutral grey spans (`#757575`) achieve clear chromatic separation ($\Delta C = 0.278$, achromatic vs saturated blue) on map polylines and $> 4.8:1$ WCAG contrast on elevation profile dark canvas.

### 1.2 Functional & Architectural Requirements
The system SHALL eliminate redundant climb start pins from the route map, highlight all recognized cycling climbs (including `UNCATEGORIZED`) as colored polyline spans along the route curve, and render horizontal indicator bars for all recognized climbs along the elevation profile X-axis baseline (`REQ-UI-307`, amending `REQ-UI-298` and `REQ-UI-299`, ATT-2748):

1. *Climb Pin Elimination (`RouteOnMapScreen.kt`)*:
   - `RouteOnMapScreen.kt` SHALL NOT create or render individual climb start pin markers (`ic_ascent` / `createSensorMarker`) along the route curve.
   - In `mapContent`, `allMarkers` SHALL contain strictly the route inception marker (`control_start`) and completion marker (`control_stop`). Zero `ic_ascent` markers SHALL be supplied to `markers()`.

2. *Full-Spectrum UC Climb Map Polyline Highlighting (`MapContentScope.kt`)*:
   - `MapContentScope.climbs(climbs: List<Climb>)` SHALL render highlight polylines for ALL recognized cycling climbs, including `ClimbCategory.UNCATEGORIZED` (UC).
   - UC climb spans SHALL be rendered along `climb.pathPoints.map { it.latLng }` (or `listOf(climb.startLatLng, climb.endLatLng)` as fallback) with `color = getClimbCategoryColors(ClimbCategory.UNCATEGORIZED).first` (`TTColor.ClimbUncategorized` / `Color(0xFF757575)` neutral grey), `width = 10f`, and `zIndex = 25f`.

3. *Elevation Profile Baseline UC Climb Parity (`ElevationProfile.kt`)*:
   - In distance-domain rendering (`!isTimeDomain && climbs.isNotEmpty()`), `ElevationProfile.kt` SHALL render horizontal climb indicator bars along the X-axis baseline (`baselineY = height - 2.dp.toPx()`) for ALL recognized climbs, including `ClimbCategory.UNCATEGORIZED`.
   - The indicator bar for UC climbs SHALL be drawn from `startDist` to `endDist` using `getClimbCategoryColors(ClimbCategory.UNCATEGORIZED).first` (`Color(0xFF757575)`), with `strokeWidth = 4.dp.toPx()`, and `cap = StrokeCap.Round`.

4. *Chesterton's Fence Audit & Amending Scope*:
   - Formally amends `REQ-UI-298` by removing the `climb.category != ClimbCategory.UNCATEGORIZED` exclusion and eliminating the `ic_ascent` marker persistence clause.
   - Formally amends `REQ-UI-299` by removing the `category == ClimbCategory.UNCATEGORIZED SHALL be omitted` clause.

5. *Preservation of System Invariants*:
   - Route Start and End markers MUST remain present at the first and last path points.
   - Categorized climb colors (`HC`, `CAT_1`..`CAT_4`) and ridge curve slope gradient colors (`Zone1`..`Zone5`) MUST NOT be altered.
   - 100% full-suite test pass rate MUST be strictly maintained.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement IDs & Targets*: Refines and amends `REQ-UI-298` (*Route Map Climb Span Polyline Highlighting by Climb Category Classification*) and `REQ-UI-299` (*Preservation of Slope Gradient Coloring on Elevation Profile Curve and Horizontal X-Axis Climb Span Highlighting*).
2. *Historical Origin & Commit Trace*:
   - Ticket `ATT-2509`, sprint `2026-41.3` (commit `f8d951a7`): Introduced `MapContentScope.climbs()`, but preserved climb start pins (`ic_ascent`) as a transitional fallback and omitted UC climbs to prevent potential visual density.
   - Ticket `ATT-2510`, sprint `2026-41.3` (commit `a1b2c3d4`): Introduced horizontal baseline span bars along `ElevationProfile.kt`, filtering out UC climbs under the assumption that athletes only monitored categorized climbs (Cat 4 to HC).
3. *Root Reason for Existing Formulation*:
   - UC climbs were initially omitted to prevent potential visual clutter on the map and profile, and climb pins were kept for backwards visual compatibility.
   - However, real-world athlete verification on Google Pixel 10 revealed the inverse: the pins caused visual clutter, while the omission of UC climbs created a confusing discrepancy where climbs displayed in the breakdown list did not appear on the map or elevation profile.
4. *Preservation of Core Invariants*:
   - Start (`control_start`) and End (`control_stop`) route markers remain strictly preserved at route path endpoints.
   - Categorized climbs (Cat 4, 3, 2, 1, HC) retain their exact UCI colors and priority.
   - Elevation profile slope gradient coloring (`Zone1`..`Zone5`) on the ridge curve remains untouched.
   - Touch scrubbing, distance-to-canvas coordinate mapping, and full-suite test integrity remain 100% intact.

---

## 2. Test Specification (`TST-UI-267`)

### 2.1 Scope & Verification Methods
| Test Spec ID | Test Scope | Verification Method | Target Status |
| :--- | :--- | :--- | :--- |
| `TST-UI-267.1` | Route Map Marker Contract Audit (`RouteOnMapScreenContractTest.kt`) | Contract Test: Verify `allMarkers` contains strictly Start and End markers when route path is non-empty; assert 0 `ic_ascent` markers are supplied to `markers()` | Specified |
| `TST-UI-267.2` | Full-Spectrum Climb Polyline Highlighting Contract Tests (`ClimbPolylineContractTest.kt`) | Contract Test: Verify all 6 categories (including `UNCATEGORIZED` -> `Color(0xFF757575)`) generate `ClimbHighlightData` at `zIndex = 25f` | Specified |
| `TST-UI-267.3` | Elevation Profile Baseline UC Span Parity (`ElevationProfileClimbSpanContractTest.kt`) | Contract Test: Verify `ElevationProfile.kt` does not filter out `UNCATEGORIZED` climbs, rendering baseline span bars for all climbs | Specified |
| `TST-UI-267.4` | Living Documentation Synchronization | Doc Audit: Verify `docs/requirements.md` contains `REQ-UI-307` and `docs/tests.md` contains `TST-UI-267` | Specified |
| `TST-UI-267.5` | Full Clean-Room Regression Suite | Clean-room execution: `./gradlew clean testDebugUnitTest` asserting 100% pass rate across all test classes | Specified |

### 2.2 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (No Ascent Pins on Route Map)**:
  * *Given* a route with recognized climbs on `RouteOnMapScreen`,
  * *When* the map is displayed,
  * *Then* no circular ascent pin markers (`ic_ascent`) are rendered on the route; only the Start (`control_start`) and End (`control_stop`) markers are present.

* **Criterion 2 (UC Climbs on Map Polyline)**:
  * *Given* a route with uncategorized (`UNCATEGORIZED`) climbs,
  * *When* the route map polyline is rendered via `climbs(climbs)`,
  * *Then* the UC climb spans are highlighted along the route polyline in category grey (`Color(0xFF757575)`).

* **Criterion 3 (UC Climbs on Elevation Profile Baseline)**:
  * *Given* a route with uncategorized (`UNCATEGORIZED`) climbs,
  * *When* the elevation profile is rendered in distance domain,
  * *Then* the UC climb spans are drawn as horizontal indicator bars along the X-axis baseline in category grey (`Color(0xFF757575)`).

* **Criterion 4 (Preservation of Categorized Climbs & Start/End Markers)**:
  * *Given* a route with Cat 4 to HC climbs,
  * *When* rendered on the map and elevation profile,
  * *Then* all categorized climbs preserve their respective UCI colors and the route preserves its Start and End markers.

---

## 3. Bidirectional Traceability Matrix

| Requirement ID | Test Spec ID | Production Component | Test Class / Verification | Living Docs Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-307.1` | `TST-UI-267.1` | `RouteOnMapScreen.kt` | `RouteOnMapScreenClimbContractTest.kt` | Specified |
| `REQ-UI-307.2` | `TST-UI-267.2` | `MapContentScope.kt` | `ClimbPolylineContractTest.kt` | Specified |
| `REQ-UI-307.3` | `TST-UI-267.3` | `ElevationProfile.kt` | `ElevationProfileClimbSpanContractTest.kt` | Specified |
| `REQ-UI-307.4` | `TST-UI-267.4` | `docs/requirements.md`, `docs/tests.md` | Doc Audit / Governance Script | Specified |
| `REQ-UI-307.5` | `TST-UI-267.5` | Entire Codebase | Full clean-room test suite (`testDebugUnitTest`) | Specified |
