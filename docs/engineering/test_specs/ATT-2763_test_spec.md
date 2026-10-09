# Stage 2: Requirement & Test Specification - ATT-2763: Make climbs, segments, and waypoints toggleable on route map with layers menu while anchoring route line

**Ticket**: [ATT-2763](https://atrainingtracker.atlassian.net/browse/ATT-2763)  
**Sub-task**: [ATT-2833](https://atrainingtracker.atlassian.net/browse/ATT-2833) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `Backlog`  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-308` (*Route Map Overlay Layer Visibility Controls, Layers Menu, and Item-Level Breakdown Toggles with Permanent Route Line Anchoring*)  
**Test Spec ID**: `TST-UI-268` (*Route Map Layers Menu, Overlay Layer Visibility, Item-Level Breakdown Toggles, and Permanent Route Line Parity Verification*)  
**Branch**: `improvement/ATT-2763`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-UI-308)

### 1.1 Problem Statement & Rationale
Complex courses often present multiple overlapping visual elements on `RouteOnMapScreen.kt` (base route polyline, climb highlight polylines across 6 UCI categories, matched Strava segments, and waypoint pins). Athletes need the ability to declutter the map view by selectively showing or hiding overlay layers via a standardized map Layers menu (patterned after `TrackOnMapScreen.kt`) and individual breakdown item toggles, while strictly preserving the visibility of the primary route line.

### 1.2 Functional & Architectural Requirements

1. **Permanent Selected Route Anchoring (`REQ-UI-308.1`)**:
   - `RouteOnMapScreen.kt` SHALL render the selected route polyline (`TTColor.RouteSelected`, Royal Blue `#1565C0`) unconditionally in `mapContent`.
   - The selected route polyline MUST NOT be included in the Layers menu and CANNOT be toggled off or hidden.
2. **Route Map Layers Menu UI & Ergonomic Parity (`REQ-UI-308.2`)**:
   - `RouteOnMapScreen.kt` SHALL provide a floating circular Layers button in the `overlay` slot of `MapDetailLayout` at `Alignment.TopEnd` with `padding(top = 76.dp, end = 16.dp)`.
   - The button SHALL display `Icons.Default.Layers` (`size = 22.dp`) with tint dynamically resolving to `MaterialTheme.colorScheme.primary` if any layer is toggled off (or `onSurface` when all are visible).
   - Clicking the button SHALL open a Material 3 `DropdownMenu` styled 1:1 with `TrackOnMapScreen.kt`, containing entries for:
     * **Climbs** (`R.string.route_layer_climbs`): Checkbox, climb category color swatch/chip, enabled only if `climbs.isNotEmpty()`.
     * **Segments** (`R.string.route_layer_segments`): Checkbox, Strava orange swatch (`TTColor.StravaOrange`), enabled only if `matchedSegments.isNotEmpty()`.
     * **Waypoints** (`R.string.route_layer_waypoints`): Checkbox, POI icon or category disc swatch, enabled only if `route?.waypoints?.isNotEmpty() == true`.
3. **Layer-Level Map Filtering (`REQ-UI-308.3`)**:
   - When the Climbs layer is unchecked, all climb highlight polylines SHALL be suppressed from the map.
   - When the Segments layer is unchecked, all matched segment polylines SHALL be suppressed from the map.
   - When the Waypoints layer is unchecked, waypoint markers along the route SHALL be suppressed from the map by passing `route.copy(waypoints = emptyList())` to `routes()`.
   - Start (`control_start`) and End (`control_stop`) navigation markers SHALL remain permanently visible and MUST NOT be affected by layer toggles.
4. **Item-Level Breakdown Card Visibility Toggles (`REQ-UI-308.4`)**:
   - In `RouteClimbsBreakdownSection.kt`, each climb card SHALL feature an individual visibility toggle (`IconButton` with `Icons.Default.Visibility` when visible, `Icons.Default.VisibilityOff` when hidden). Tapping the toggle SHALL hide or show that individual climb on the map without triggering the card's `onClick` (which opens `ClimbDetailSheet`).
   - In `RouteSegmentsBreakdownSection.kt`, each segment card SHALL feature an individual visibility toggle (`IconButton` with `Icons.Default.Visibility` / `Icons.Default.VisibilityOff`). Tapping the toggle SHALL hide or show that individual segment on the map without triggering the card's `onClick` (which opens `SegmentDetailSheet`).
5. **Localization Parity & Regression Invariant (`REQ-UI-308.5`)**:
   - All newly introduced string keys (`route_layers`, `route_layer_climbs`, `route_layer_segments`, `route_layer_waypoints`, `route_item_hide`, `route_item_show`) SHALL be localized across all 9 supported Android locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
   - 100% full clean-room unit test suite pass rate SHALL be maintained.

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Permanent Route Visibility)**:
  * *Given* an athlete on `RouteOnMapScreen`
  * *When* toggling any layer off or toggling individual items off
  * *Then* the selected route polyline remains 100% visible at all times.
* **Criterion 2 (Layers Control Menu)**:
  * *Given* `RouteOnMapScreen`
  * *When* tapping the Layers button (`Icons.Default.Layers`)
  * *Then* a dropdown menu opens showing checkboxes and color legend swatches for Climbs, Segments, and Waypoints, styled 1:1 with `TrackOnMapScreen`.
* **Criterion 3 (Layer Visibility Toggling)**:
  * *Given* a route with climbs, segments, and waypoints
  * *When* the athlete unchecks "Climbs" in the Layers menu
  * *Then* all climb span polylines are hidden from the map.
  * *When* the athlete unchecks "Waypoints" in the Layers menu
  * *Then* all waypoint markers are hidden from the map, while Start and End markers remain visible.
* **Criterion 4 (Individual Climb / Segment Toggling)**:
  * *Given* the climb breakdown list in route details
  * *When* the athlete toggles off an individual climb via its eye icon
  * *Then* only that specific climb's polyline highlight is hidden on the map, while other climbs remain visible.
  * *When* the athlete taps the card body
  * *Then* `ClimbDetailSheet` opens as expected without altering the toggle state.
* **Criterion 5 (Visual & Ergonomic Parity)**:
  * *Given* the layer selection UI
  * *When* rendered
  * *Then* its spacing, typography, checkbox styling, and color chips match `TrackOnMapScreen.kt`.

### 1.4 System Invariants
1. Selected route polyline (`TTColor.RouteSelected`) is never hidden or toggled off.
2. Route Start and End navigation markers remain visible at all times.
3. Clicking on climb/segment cards continues to open `ClimbDetailSheet` and `SegmentDetailSheet`.
4. 100% test pass rate across all 2,172 existing clean-room unit tests.

---

## 2. Test Specification (TST-UI-268)

### Test Case 1: `RouteOnMapScreenLayersContractTest` (`TST-UI-268.1`)
* **Scope**: Unit & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreenLayersContractTest.kt`
* **Preconditions**: `RouteOnMapScreen` with test route containing climbs, segments, and waypoints.
* **Action**:
  1. Verify `RouteOverlayLayer` enum declares `CLIMBS`, `SEGMENTS`, `WAYPOINTS`.
  2. Verify initial layer state defaults to all layers enabled.
  3. Verify filtering logic:
     - Disabling `CLIMBS` filters out all climbs.
     - Disabling `SEGMENTS` filters out all segments.
     - Disabling `WAYPOINTS` yields empty waypoints on the route while preserving route path points.
     - Adding a climb ID to `hiddenClimbIds` hides only that specific climb.
     - Adding a segment ID to `hiddenSegmentIds` hides only that specific segment.
     - Selected route path is never modified or filtered.
     - Start and End markers are never modified or filtered.

### Test Case 2: `RouteBreakdownVisibilityToggleContractTest` (`TST-UI-268.2`)
* **Scope**: Unit & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteBreakdownVisibilityToggleContractTest.kt`
* **Preconditions**: Breakdown sections `RouteClimbsBreakdownSection` and `RouteSegmentsBreakdownSection`.
* **Action**:
  1. Verify `RouteClimbsBreakdownSection` accepts `hiddenClimbIds: Set<Long>` and `onToggleClimbVisibility: ((Long) -> Unit)?`.
  2. Verify `RouteSegmentsBreakdownSection` accepts `hiddenSegmentIds: Set<Long>` and `onToggleSegmentVisibility: ((Long) -> Unit)?`.
  3. Verify visibility icon toggle triggers `onToggleClimbVisibility` / `onToggleSegmentVisibility` callback with expected entity IDs.

### Test Case 3: 9-Language Localization & Specifier Audit (`TST-UI-268.3`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteLayersLocalizationTest.kt`
* **Goal**: Verify string presence across all 9 locales:
  * `route_layers`, `route_layer_climbs`, `route_layer_segments`, `route_layer_waypoints`, `route_item_hide`, `route_item_show`.
* **Expected Result**: 100% parity across all 9 locales with zero missing keys.

### Test Case 4: Clean-Room Full-Suite Regression (`TST-UI-268.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full unit test suite without regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-268.1` | Unit/Contract | `RouteOverlayLayer` & filtering logic | `REQ-UI-308.1`, `REQ-UI-308.2`, `REQ-UI-308.3` | Specified |
| `TST-UI-268.2` | Unit/Contract | Breakdown section toggle integration | `REQ-UI-308.4` | Specified |
| `TST-UI-268.3` | Localization | `RouteLayersLocalizationTest` | `REQ-UI-308.5`, `REQ-UI-106` | Specified |
| `TST-UI-268.4` | Clean-Room Suite | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-UI-308.5` | Specified |

---

## 4. Conclusion

Stage 2 Requirement & Test Specification for `ATT-2763` is complete. Deliverables are defined with clear Given-When-Then criteria, system invariants, and deterministic test specifications ready for implementation planning.
