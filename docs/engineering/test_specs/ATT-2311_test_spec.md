# Stage 2: Requirement & Test Specification - ATT-2311: Position Elevation Profile Below Map and Enable Interactive Zooming in Routes and Segments

**Ticket**: [ATT-2311](https://rainerblind.atlassian.net/browse/ATT-2311)  
**Sub-task**: [ATT-2350](https://rainerblind.atlassian.net/browse/ATT-2350) (`[Test-Spec]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*) / [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Requirement ID**: `REQ-UI-267`  
**Test Specification ID**: `TST-UI-226`  
**Branch**: `feature/ATT-2311`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Requirement Specification (`REQ-UI-267`)

### 1.1 Formal Specification
The system SHALL position the elevation profile strictly below the map in Route and Segment detail views (`RouteOnMapScreen` and `SegmentOnMapScreen`), activate a two-pane resizable split layout with an interactive draggable splitter, and enable interactive zooming and panning via `GlobalTelemetryZoomToolbar` (ATT-2311):

1. **Lower Section Presence Definition (`MapDetailLayout.kt`)**:
   - The system SHALL define `hasLowerSection`:
     ```kotlin
     val hasLowerSection = (showElevationProfile || hasTelemetryGraphs) && !activeScrubPath.isNullOrEmpty() || metadataContent != null || analyticsContent != null
     ```
   - For Routes (`RouteOnMapScreen`) and Segments (`SegmentOnMapScreen`), where `showElevationProfile == true` and `activeScrubPath` contains path points, `hasLowerSection` SHALL evaluate to `true`.
2. **Two-Pane Resizable Split-Pane Viewport (`MapDetailLayout.kt`)**:
   - When `showMap == true` and `hasLowerSection == true`:
     - The upper section SHALL render the map (`mapBox`) with `Modifier.weight(splitFraction).heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT).fillMaxWidth()`.
     - An interactive draggable `SplitPaneDivider` SHALL be placed directly between the map and the lower container, handling vertical drag deltas and double-tap gestures to reset `splitFraction` to `SplitPaneMath.DEFAULT_SPLIT_FRACTION` (`0.50f`).
     - The lower section SHALL be placed in a sticky container with `Modifier.weight(1f - splitFraction).fillMaxWidth()`, strictly *below* the map.
     - The lower container SHALL render `GlobalTelemetryZoomToolbar` (when `hasZoomToolbar == true`) followed by `lowerColumn` (containing `ElevationProfile`) wrapped in a vertical scroll container (`Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())`).
     - `ScrubbingTelemetryBadge` (`scrubbingOverlay`) SHALL float within the lower container, synchronized to the scrubbed trackpoint.
3. **Interactive Zooming & Pan Mode in Routes & Segments**:
   - In `RouteOnMapScreen` and `SegmentOnMapScreen`, `GlobalTelemetryZoomToolbar` SHALL be active and interactive:
     - Tapping `+` zooms in horizontally (`profileZoomScale` increases up to 10.0x).
     - Tapping `-` zooms out horizontally (`profileZoomScale` decreases down to 1.0x).
     - Tapping the zoom reset badge restores `1.0x` and default viewport start (`0.0`).
     - Tapping the pan toggle enables horizontal pan mode, allowing single-finger dragging across the zoomed elevation profile without moving the scrub marker.
     - 2-finger pinch-to-zoom gestures on `ElevationProfile` scale the elevation domain around the touch centroid.
4. **Scrubbing & Map Marker Coordination**:
   - Scrubbing along the zoomed or unzoomed elevation profile SHALL update `selectedDistance`, which SHALL highlight the corresponding coordinate on the route/segment map polyline via `ScrubMarkerLayer` in `ATrainingTrackerMap`.
5. **Preservation of Specialized Viewports & Invariants**:
   - Standalone map views without lower content (`WorkoutClusterHeatmapScreen`, or routes with empty paths): `showMap == true && hasLowerSection == false` SHALL render `mapBox(Modifier.fillMaxSize())` across the entire viewport without divider or empty lower space.
   - Ambient / compact bottom sheets (`LiveSegmentSheet.kt`, `showMap == false`): SHALL maintain compact `wrapContentHeight()` presentation without forced full-screen expansion.
   - Workout details (`TrackOnMapScreen.kt`): All telemetry graphs, upper metadata, zone distribution cards, and interval tables SHALL remain 100% functional.

### 1.2 Chesterton's Fence Archaeology & Governance
1. **Original Requirement ID & Target**: Refines and supersedes Clause 4 of `REQ-UI-223` (*Aftermath/Map: Interactive Draggable Splitter and Dynamic Viewport Resizing in MapDetailLayout*) and extends `REQ-UI-197` (*Elevation Profile Contextual Zoom Controls Visibility*).
2. **Historical Origin & Commit Trace**: Sprint `2026-40.7` (`ATT-1890`, commit `4e6224aa`) and Sprint `2026-40.6` (`ATT-1647`).
3. **Root Reason for Existing Formulation**: Clause 4 of `REQ-UI-223` restricted the resizable split-pane strictly to `hasScrollableContent` (`metadataContent != null || analyticsContent != null || hasTelemetryGraphs`), relegating routes and segments to an unaligned fallback `Box`. In Compose, this caused the elevation profile and zoom toolbar to be stacked directly on top of the map at `TopStart`, occluding the map and breaking interactive zooming.
4. **Preservation of Core Invariants**: Minimum map height protection (`SplitPaneMath.MIN_MAP_HEIGHT` = 120dp), safe boundary clamping, double-tap reset (`0.50f`), multi-touch pinch-to-zoom, and synchronized coordinate scrubbing are 100% strictly preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Given** an athlete opening a route in `RouteOnMapScreen` or a segment in `SegmentOnMapScreen`,
* **When** the screen is rendered,
* **Then** the map SHALL occupy the upper section, an interactive `SplitPaneDivider` SHALL be placed in the middle, and the elevation profile SHALL be positioned strictly below the map.
* **Given** the elevation profile displayed below the map in `RouteOnMapScreen` or `SegmentOnMapScreen`,
* **When** inspecting the lower viewport,
* **Then** `GlobalTelemetryZoomToolbar` SHALL be displayed above the elevation profile with `+`, `-`, 1.0x reset, and Pan mode controls.
* **Given** the athlete dragging the `SplitPaneDivider` up or down,
* **When** dragging,
* **Then** the map and elevation profile viewports SHALL dynamically resize in real time, clamping safely at `SplitPaneMath.MIN_MAP_HEIGHT` (120dp).
* **Given** the athlete interacting with `GlobalTelemetryZoomToolbar` or pinching on the elevation profile in `RouteOnMapScreen`,
* **When** zooming in,
* **Then** the elevation profile SHALL magnify its horizontal distance/elevation curve smoothly, and scrubbing along the zoomed profile SHALL accurately position the map marker.
* **Given** a standalone map view without lower content (`WorkoutClusterHeatmapScreen`),
* **When** displayed,
* **Then** the map SHALL fill 100% of the screen with zero divider or empty lower padding.
* **Given** the Live Segment tracking sheet (`LiveSegmentSheet.kt`, `showMap == false`),
* **When** displayed,
* **Then** the sheet SHALL wrap its content height cleanly without expanding to the full screen.

---

## 2. Test Specification (`TST-UI-226`)

### 2.1 Traceability Matrix
| Requirement ID | Test Case ID | Test Scope | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-267.1` | `TST-UI-226.1` | `hasLowerSection` Evaluation | Unit Test (`MapDetailLayoutTest.kt`): assert `hasLowerSection` evaluates `(showElevationProfile || hasTelemetryGraphs) && !activeScrubPath.isNullOrEmpty() \|\| metadataContent != null \|\| analyticsContent != null` |
| `REQ-UI-267.2` | `TST-UI-226.2` | Two-Pane Split Layout Integration | Unit Test (`MapDetailLayoutTest.kt`): assert `if (showMap && hasLowerSection)` activates two-pane split with `SplitPaneDivider` and lower container below map |
| `REQ-UI-267.3` | `TST-UI-226.3` | Interactive Zoom Toolbar Contract | Unit Test (`MapDetailLayoutZoomContractTest.kt`): assert `GlobalTelemetryZoomToolbar` is rendered in lower container when `hasZoomToolbar` is true |
| `REQ-UI-267.4` | `TST-UI-226.4` | Standalone Map & Bottom Sheet Invariants | Unit Test (`LiveSegmentSheetLayoutTest.kt`): assert `showMap == false` preserves wrapped height and standalone map fills full screen |
| `REQ-PRO-001` | `TST-UI-226.5` | Clean-Room Full Suite Regression | Full execution of `./gradlew testDebugUnitTest` with 100% pass rate |

### 2.2 Test Case Descriptions

#### `TST-UI-226.1`: `hasLowerSection` Evaluation
* **Objective**: Verify that `MapDetailLayout.kt` computes `hasLowerSection` taking into account `showElevationProfile` with non-empty `activeScrubPath`.
* **Assertion**: Verify source code and architectural contracts in `MapDetailLayoutTest.kt`:
  - `hasLowerSection` includes `showElevationProfile && !activeScrubPath.isNullOrEmpty()`.
  - Routes and segments with path points activate `hasLowerSection`.

#### `TST-UI-226.2`: Two-Pane Split Layout Integration
* **Objective**: Verify that when `showMap == true` and `hasLowerSection == true`:
  - `mapBox` is weighted in the upper section with `heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT)`.
  - `SplitPaneDivider` separates map and lower viewport.
  - `lowerColumn` is enclosed in the lower container below the divider.

#### `TST-UI-226.3`: Interactive Zoom Toolbar Contract
* **Objective**: Verify that `GlobalTelemetryZoomToolbar` is rendered within the lower sticky container above `lowerColumn` whenever `hasZoomToolbar` is true (which is true for routes and segments).

#### `TST-UI-226.4`: Standalone Map & Bottom Sheet Invariants
* **Objective**: Verify that `LiveSegmentSheet.kt` (`showMap = false`) preserves `wrapContentHeight()` behavior, and standalone map screens (`showMap = true && !hasLowerSection`) fill 100% of the screen.

#### `TST-UI-226.5`: Clean-Room Full Suite Regression
* **Objective**: Execute `./gradlew testDebugUnitTest` verifying 0 test failures across the entire application.
