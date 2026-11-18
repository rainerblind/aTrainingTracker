# Living Requirements: UI Components & Design System (Part 3 of 5)

**Document ID**: `DOC-REQ-UI-P3`  
**Version**: 1.0.0  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `ui_design_system_part3`  
**Prefixes**: `UI`, `UNT`  
**Scope**: Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts.  
**Total Requirements**: 88

---

### REQ-UI-298: Route Map Climb Span Polyline Highlighting by Climb Category Classification [Verified]
- **Tests**: `TST-UI-258`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapContentScope.kt`, `RouteOnMapScreen.kt`
- **Rationale**: The system SHALL highlight the spatial polyline path of recognized cycling climbs on the route map (`RouteOnMapScreen.kt`) using their respective climb category color classification (`REQ-UI-274`, `REQ-MAP-027`, ATT-2509):
1. *MapContentScope Climb Highlight API (`MapContentScope.kt`)*:
• `MapContentScope` SHALL provide a declarative function `fun climbs(climbs: List<Climb>)`.
• For each classified climb (`climb.category != ClimbCategory.UNCATEGORIZED`), the system SHALL render a dedicated `Polyline` along `climb.pathPoints.map { it.latLng }` (or `listOf(climb.startLatLng, climb.endLatLng)` as fallback).
• The climb polyline SHALL be rendered at `zIndex = 25f` (above the base route at 20-21f, below markers at 50f) and `width = 10f` (matching `MapVisualization.ROUTE_WIDTH`).
• The polyline color SHALL match the climb's category color: `getClimbCategoryColors(climb.category).first`.
2. *Visual & Structural Harmony in `RouteOnMapScreen.kt`*:
• In `mapContent`, after calling `routes(listOf(route))`, `RouteOnMapScreen` SHALL invoke `climbs(climbs)`.
• Unclassified, flat, and downhill sections of the route SHALL retain their standard route polyline coloring (`RouteSelected` / ForestGreen).
• Climb start markers (`ic_ascent`) continue to mark the inception of each climb at `climb.startLatLng`.
3. *Preservation of Invariants*:
• Route selection, clicking, panning, zoom-to-fit, and scrubbing MUST remain 100% operational without regression.
• Non-classified routes or routes with 0 climbs SHALL render cleanly without visual glitches or empty polyline overlays.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement refining `REQ-UI-274` (*Climb Categorization & Accent Highlights*) and `REQ-MAP-027` (*Climb In-Flight Tracking & Proximity Engine*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2509`, sprint `2026-41.3`, target release `V4.9.39`, Epic `ATT-2582` (*[Epic] Segments: Live Tracking, Exploration & Route Integration*).
3. *Root Reason for Existing Formulation*: In `RouteOnMapScreen.kt`, climbs were represented solely by start marker pins at `climb.startLatLng`. Athletes could not see where a climb extended to or where it crested along the route curve.
4. *Preservation of Core Invariants*: Climb start markers, elevation breakdown chips, route selection, and touch gestures remain 100% intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a route with recognized climbs on `RouteOnMapScreen`,
• *When* the map renders,
• *Then* each classified climb segment SHALL be drawn with its category color on top of the route polyline.
• *Given* unclassified, flat, or downhill sections between climbs,
• *When* the map renders,
• *Then* these sections SHALL retain standard route coloring without distortion.
• *Given* all 5 standard climb categories (`CAT_4`, `CAT_3`, `CAT_2`, `CAT_1`, `HC`),
• *When* rendered,
• *Then* the polyline colors SHALL match `getClimbCategoryColors(climb.category).first`.

**Invariants**: Route clickability, gesture handling, marker zIndex, and 100% test pass rate MUST NOT be broken.

### REQ-UI-299: Preservation of Slope Gradient Coloring on Elevation Profile Curve and Horizontal X-Axis Climb Span Highlighting [Verified]
- **Tests**: `TST-UI-259`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ElevationProfile.kt`
- **Rationale**: The system SHALL preserve intrinsic slope gradient coloration across the elevation profile ridge curve and represent recognized cycling climb spans via dedicated category-colored indicator bars along the horizontal X-axis baseline (`ElevationProfile.kt`) (ATT-2510):
1. *Slope Gradient Ridge Integrity (`ElevationProfile.kt`)*:
• The elevation profile curve SHALL NOT be overwritten by a solid climb category color stroke.
• All curve segments SHALL strictly retain their physical slope gradient coloration (`TTColor.Zone1`..`Zone5`) derived from `ElevationSmoothingMath.calculateGrade`.
2. *Horizontal X-Axis Climb Span Highlighting*:
• For recognized cycling climbs (`climbs.isNotEmpty()` and `!isTimeDomain`), the system SHALL render a dedicated category-colored horizontal span bar along the X-axis baseline (`y = height`) spanning from the climb's start distance to end distance.
• The start and end canvas X coordinates SHALL be evaluated via `ElevationProfileZoomMath.distanceToCanvasX(startDist, currentStartDist, visibleSpan, width)` and `ElevationProfileZoomMath.distanceToCanvasX(endDist, currentStartDist, visibleSpan, width)`.
• The span bar SHALL be drawn with `color = getClimbCategoryColors(climb.category).first`, `strokeWidth = 4.dp.toPx()`, and `cap = StrokeCap.Round`.
• Climbs with `category == ClimbCategory.UNCATEGORIZED` SHALL be omitted from the X-axis span highlights.
3. *Summit Badges Preservation*:
• Summit category badges, pins, and background pills positioned above the elevation profile peaks SHALL remain visible and anchored to `summitPt`.
4. *Preservation of Invariants*:
• Touch scrubbing, telemetry badges, zoom/pan bounds, time-domain rendering, and tick spacing MUST NOT be modified.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement refining `REQ-UI-274` (*Climb Categorization & Accent Highlights*) and `REQ-UI-192` (*ElevationProfile Mathematical Precision & Zoom Engine*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2510`, sprint `2026-41.3`, target release `V4.9.39`, Epic `ATT-2582` (*[Epic] Segments: Live Tracking, Exploration & Route Integration*).
3. *Root Reason for Existing Formulation*: Sprint 2026-41.2 commit `ATT-2358` introduced climb ridge overdrawing (`drawLine` with `strokeWidth = 3.5.dp.toPx()`) which completely obscured the underlying slope grade colors, preventing athletes from seeing gradient changes during a climb.
4. *Preservation of Core Invariants*: Topographic summits, elevation fill shading, grade legend, and scrubbing telemetry remain 100% intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an elevation profile with categorized climbs,
• *When* the profile renders,
• *Then* the elevation ridge curve displays slope/grade gradient colors (`Zone1`..`Zone5`) without being overridden by a solid climb category color.
• *Given* recognized cycling climbs along the route,
• *When* distance-domain rendering is active,
• *Then* a horizontal category-colored bar is drawn along the X-axis baseline delineating the start and end distance of each climb.
• *Given* an uncategorized climb (`UNCATEGORIZED`),
• *When* the profile renders,
• *Then* no X-axis climb span bar is generated for that climb.
• *Given* summit locations of climbs,
• *When* rendered,
• *Then* summit category badges/pins above the peaks remain visible.

**Invariants**: Profile zooming, panning, scrubbing, time domain, and 100% unit test pass rate MUST NOT be broken.

### REQ-UI-300: Dedicated Route Climb Detail View with Focused Map and Zoomed Isolated Elevation Profile [Verified]
- **Tests**: `TST-UI-260`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RouteOnMapScreen.kt`, `ClimbDetailSheet.kt`
- **Rationale**: The system SHALL present a dedicated climb detail bottom sheet (`ClimbDetailSheet.kt`) upon tapping any recognized climb item in the route breakdown list (`RouteOnMapScreen.kt`) providing focused spatial and topographic deep-dive inspection (ATT-2511):
1. *Interactive Climb Item Tap (`RouteOnMapScreen.kt`)*:
• `RouteClimbsBreakdownSection` SHALL support an `onClimbClick: ((Climb) -> Unit)?` callback.
• Tapping each climb's `ElevatedCard` in the breakdown list SHALL invoke `onClimbClick(climb)`, setting `selectedClimbForDetail` state in `RouteOnMapScreen`.
2. *Dedicated Climb Detail Sheet Architecture (`ClimbDetailSheet.kt`)*:
• When a climb is selected, the system SHALL render a Material 3 `AppModalBottomSheet` containing:
a. *Header*: Climb title, `ClimbCategoryChip` indicating UCI climb classification, route counter badge (e.g. "Climb 1 of 4"), and dismiss button.
b. *Climb Metrics Row*: Key ascent telemetry formatted via `LocalMetricFormatter`: total climb distance (km/mi), vertical elevation gain (+m/+ft), average grade (%), maximum grade (%), and start offset along the route.
c. *Focused Map Viewport*: An embedded `ATrainingTrackerMap` with `zoomFocus = MapZoomFocus.EXPLICIT_BOUNDS` set to the climb's geographical bounds, highlighting the climb path with its category color (`width = 10f`, `zIndex = 25f`), a green start marker (`R.drawable.control_start` / `ic_ascent`), and a red summit marker (`R.drawable.control_stop`).
d. *Isolated Zoomed Elevation Profile*: A dedicated elevation profile canvas (`ClimbDetailElevationProfile`) rendering the climb's altitude path strictly from 0 to `distanceMeters`, with slope gradient colored segments (`Zone1`..`Zone5`), shaded elevation fill, and start/summit altitude tags.
3. *State Isolation & Seamless Dismissal*:
• Dismissing the sheet (via close button, drag handle, or back press) SHALL clear `selectedClimbForDetail` without resetting route selection, route bounds, or scroll position in `RouteOnMapScreen`.
4. *100% 9-Language Localization Parity*:
• Newly introduced climb metric strings (`routes_climb_max_grade`) SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
5. *Preservation of Invariants*:
• Route selection toggle, route polyline rendering, and full route elevation profile scrubbing MUST NOT be modified.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement refining `REQ-UI-274` (*Climb Categorization & Accent Highlights*), `REQ-MAP-027` (*Climb In-Flight Tracking & Proximity Engine*), and `REQ-UI-298` (*Route Map Climb Polyline Highlighting*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2511`, sprint `2026-41.3`, target release `V4.9.39`, Epic `ATT-2582` (*[Epic] Segments: Live Tracking, Exploration & Route Integration*).
3. *Root Reason for Existing Formulation*: In `RouteOnMapScreen.kt`, `RouteClimbsBreakdownSection` was rendered as static informational cards without click listeners, preventing athletes from inspecting individual climbs in isolation.
4. *Preservation of Core Invariants*: Full route map, elevation breakdown cards, route scrubbing, and split-pane layout remain 100% intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a route with recognized climbs in `RouteOnMapScreen`,
• *When* tapping any climb card in `RouteClimbsBreakdownSection`,
• *Then* `ClimbDetailSheet` SHALL open presenting the climb's focused map, isolated elevation profile, and metrics.
• *Given* `ClimbDetailSheet` open for a climb,
• *When* observing the map and profile,
• *Then* the map is zoomed to the climb bounds with start/summit markers, and the profile isolates the climb with slope gradient coloring.
• *Given* `ClimbDetailSheet` open,
• *When* the athlete taps the close button or swipes down,
• *Then* the sheet dismisses cleanly returning to `RouteOnMapScreen` with route state preserved.
• *Given* all 9 supported application locales,
• *When* running localization parity tests,
• *Then* all climb detail string resources SHALL exist and be non-empty.

**Invariants**: Route selection state, parent route map/elevation profile, and 100% unit test pass rate MUST NOT be broken.

### REQ-UI-301: Control Tracking Screen Sensor Header Layout Isolation & Collision Elimination [Verified]
- **Tests**: `TST-UI-261`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: **ControlTrackingScreen.kt**, `RemoteDevices.kt`
- **Rationale**: The system SHALL eliminate visual and hit-target collisions between the research button (`ResearchButton`), search status feedback (`SearchArea`), and paired remote device tiles (`RemoteDevices`) in `ControlTrackingScreen.kt` by restructuring the header hierarchy into decoupled, non-overlapping spatial containers (ATT-2479):
1. *Search Status Isolation (`SearchArea.kt`)*:
• `SearchArea` SHALL occupy a dedicated full-width vertical slot positioned above all sensor controls, ensuring the search spinner and progress text ("Suche nach...") never intersect or overlap action buttons.
2. *Side-by-Side Action & Sensor Container*:
• `ResearchButton` and `RemoteDevices` SHALL be arranged in a horizontal `Row` layout rather than an overlapping `Box` container.
• When `showResearchButton == true`: `ResearchButton` SHALL occupy a dedicated leading slot in the row with a right padding margin of 4 dp, and `RemoteDevices` SHALL occupy the remaining horizontal space (`Modifier.weight(1f)`).
• When `devices.isEmpty()`: if `showResearchButton == true`, `ResearchButton` SHALL be centered horizontally (`Arrangement.Center`); if `showResearchButton == false`, the container SHALL collapse cleanly.
3. *Multi-Device Flow & Touch Target Preservation (`RemoteDevices.kt`)*:
• `RemoteDevices` SHALL accept an optional `modifier: Modifier = Modifier` forwarded to its top-level column.
• When multiple sensors (e.g. 5–6 sensors) are connected, `LazyRow` inside `RemoteDevices` SHALL scroll smoothly within its assigned space without clipping outer screen margins or drawing beneath `ResearchButton`.
• `LazyRow` items SHALL maintain consistent spacing (`Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)`) and touch targets.
4. *Preservation of System Invariants*:
• Conditional visibility of `ResearchButton` based on `hasPairedRemoteDevices` (`REQ-UI-259`) MUST remain strictly intact.
• `ResearchButton` disabled state during active search (`searchingFor != null`) MUST be preserved.
• Precise location gating (`REQ-PRI-004`), permission rationale sheets (`REQ-PRI-003`), start/pause/stop button centering, and route selection bottom slot (`REQ-UI-278`, `REQ-UI-279`) MUST NOT be altered.
• Zero regression across the entire clean-room test suite (`./gradlew testDebugUnitTest`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-259` (*Conditional Research Button Visibility Based on Real Paired Remote Devices*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2057` placed `ResearchButton` in an overlapping `Box` with `Alignment.TopStart` to anchor `RemoteDevices` to the center without layout shift when the button was hidden.
3. *Root Reason for Existing Formulation*: Overlapping `Box` assumed device count would remain small (1–2 sensors). When 5–6 sensors are present, the wide `RemoteDevices` row spans the screen width, positioning its first tile directly under `TopStart`.
4. *Preservation of Core Invariants*: Conditional visibility based on `hasPairedRemoteDevices`, search idle interaction, and start button layout geometry remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* 5 or more active/connected sensors displayed in `RemoteDevices` and `showResearchButton == true`,
• *When* viewing `ControlTrackingScreen`,
• *Then* `ResearchButton` ("Suchen") and `RemoteDevices` SHALL NOT overlap at any point, and the leftmost sensor tile SHALL be completely visible and clickable.
• *Given* an active search in progress (`searchingFor != null`),
• *When* `SearchArea` renders the progress spinner and status text,
• *Then* `SearchArea` SHALL render entirely above `ResearchButton` and `RemoteDevices` without vertical overlap.
• *Given* zero connected sensors (`devices.isEmpty()`) and `showResearchButton == true`,
• *When* viewing `ControlTrackingScreen`,
• *Then* `ResearchButton` SHALL be centered horizontally.

**Invariants**: Conditional visibility of ResearchButton, location gating, Start button centering, and 100% unit test pass rate MUST NOT be broken.

### REQ-UI-302: Route Starred Segments Spatial Overlap Matching, Chronological Route Breakdown & Map Synergy [Verified]
- **Tests**: `TST-UI-262`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
f. *Intermediate Point Sampling*: For multi-point segments ($\ge 3$ points), sample midpoint coordinates are validated against route points between start and end indices.
• Output: `List<MatchedRouteSegment>` sorted in ascending order of `startDistanceMeters` along the route.
2. *Route Segments Breakdown UI (`RouteSegmentsBreakdownSection.kt`)*:
• Below the route elevation profile, `RouteSegmentsBreakdownSection` SHALL render an `ElevatedCard` (12.dp rounded corners, `surfaceVariant`) for each matched segment displaying:
- Header: Sport icon, segment name, category badge (if present), and athlete PR badge/text (e.g. "PR 02:45" or localized string).
- Metrics row: Start kilometer along route (e.g. "Start bei km 14.2"), segment length (km/mi), and average gradient (%).
3. *Climbs vs. Segments Coexistence Synergy (`RouteOnMapScreen.kt`)*:
• If a route contains both recognized climbs (`climbs.isNotEmpty()`) and matched segments (`matchedSegments.isNotEmpty()`):
- `RouteOnMapScreen` SHALL render a Material 3 `FilterChip` toggle row directly above the breakdown content (`[ Anstiege (N) ]  [ Segmente (M) ]`).
- Active tab selection state SHALL be persisted via `rememberSaveable` to survive device rotations.
• If only climbs are present: the system SHALL directly render `RouteClimbsBreakdownSection`.
• If only segments are present: the system SHALL directly render `RouteSegmentsBreakdownSection`.
• If neither are present: `analyticsContent` SHALL cleanly evaluate to `null` without rendering empty cards or layout gaps.
4. *Map & Elevation Profile Interactive Synergy*:
• Tapping a matched segment card SHALL highlight its polyline prominently on the map in `RouteOnMapScreen` and focus/scrub the elevation profile to the segment's starting distance along the route.
5. *100% 9-Language Localization Parity*:
• String resources `routes_segments_section_title`, `routes_segments_tab_title`, `routes_segment_start_at`, `routes_segment_pr` SHALL be externalized and translated across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
6. *Preservation of System Invariants*:
• `MapDetailLayout` dragging, split-pane math, and collapsing header measurement remain 100% intact.
• `RouteClimbsBreakdownSection` and `ClimbDetailSheet` functionality remain 100% intact.
• Single-tap route selection toggle and GPX export invariants remain intact.
• Zero regression across clean-room test suite (`./gradlew testDebugUnitTest`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-302`), extending `REQ-UI-274` (*Route Climbs Visual Prominence & Dedicated Breakdown*) and `REQ-EXP-010` (*Strava Segment Personal Best Update & Presentation*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2583`, Sprint `2026-41.3`, target release `V4.9.39`, Epic `ATT-2582` (*Segments: Live Tracking, Exploration & Route Integration*).
3. *Root Reason for Existing Formulation*: Previously, routes and segments were separate data silos in the UI. When Climbs were added (`ATT-1281` / `ATT-2388`), routes gained an ascent breakdown. Segments remained relegated to full-database background lines without correlation to route progression.
4. *Preservation of Core Invariants*: Full route map, climb breakdown, route selection toggle, and 100% unit test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a route passing through two starred Strava segments and near a third non-overlapping or reverse-direction segment,
• *When* opening the route in `RouteOnMapScreen`,
• *Then* exactly the two overlapping forward segments SHALL be matched and listed in ascending order of their start distance along the route.
• *Given* a matched segment card in the breakdown section,
• *When* viewed by the user,
• *Then* the card SHALL display the segment name, start kilometer along the route, length, average gradient, and the athlete's personal record (PR).
• *Given* a route containing both climbs and matched segments,
• *When* inspecting the route breakdown,
• *Then* a FilterChip row SHALL be present allowing the athlete to toggle between Climbs and Segments, with selection persisted across rotation.
• *Given* a route with no overlapping starred segments,
• *When* viewed in `RouteOnMapScreen`,
• *Then* the segments section SHALL remain hidden or display an informative empty state without breaking the layout or elevation profile.
• *Given* all 9 supported application locales,
• *When* running translation parity tests,
• *Then* all new segment breakdown string resources SHALL be non-empty and formatted correctly.

**Invariants**: MapDetailLayout split-pane math, ClimbDetailSheet, single-tap route selection, and 100% unit test pass rate MUST NOT be broken.
- **Implementation**: (D_{\text{end}} - D_{\text{start}}) - \text{segment.distance}
- **Rationale**: The system SHALL match saved and starred Strava/local segments along route paths using multi-tier spatial corridor analysis, display an interactive chronological segments breakdown below the elevation profile with athlete PR context, and provide seamless UI synergy with route climbs and interactive map highlighting (ATT-2583):
1. *Spatial Overlap Matching Engine (`RouteSegmentMatcher.kt`)*:
• The system SHALL evaluate candidate segments from `SegmentsRepository.allSegmentsWithPath` against the active route polyline (`List<PathPoint>`) and sport type (`BSportType`).
• Computation SHALL be offloaded to `Dispatchers.Default` (via `withContext(Dispatchers.Default)` or `produceState`) to prevent main thread blocking during screen composition.
• Candidate matching criteria:
a. *Sport Compatibility*: Segment sport type must match route sport type (`segment.summary.bSportType == route.bSportType` or either is `BSportType.UNKNOWN`).
b. *Corridor Proximity*: Segment start coordinate and end coordinate must lie within corridor distance tolerance ($d \le 25\text{ m}$) of the route polyline.
c. *Directional Ambiguity Elimination & Vector Alignment*: Travel bearing at start and end points must align with route heading within $\Delta \theta \le 45^\circ$ with positive vector dot product ($\cos(\Delta \theta) > 0$).
d. *Forward Progression*: Route distance at segment finish ($D_{\text{end}}$) must strictly exceed route distance at segment start ($D_{\text{start}}$).
e. *Path Length Consistency*: Route span distance $(D_{\text{end}} - D_{\text{start}})$ must approximate intrinsic segment distance within tolerance ($

### REQ-UI-303: Dedicated Route Matched Segment Detail View with Focused Map and Zoomed Isolated Elevation Profile [Verified]
- **Tests**: `TST-UI-263`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RouteOnMapScreen.kt`, `SegmentDetailSheet.kt`
- **Rationale**: The system SHALL present a dedicated segment detail bottom sheet (`SegmentDetailSheet.kt`) upon tapping any matched segment card in the route breakdown list (`RouteOnMapScreen.kt`), providing focused spatial, topographic, and athletic telemetry deep-dive inspection in full parity with `ClimbDetailSheet` (ATT-2774):
1. *Interactive Segment Item Tap (`RouteOnMapScreen.kt`)*:
• In `RouteSegmentsBreakdownSection`, tapping any matched segment card SHALL invoke `onSegmentClick(matched)`.
• `RouteOnMapScreen` SHALL maintain mutable state `var selectedSegmentForDetail by remember { mutableStateOf<MatchedRouteSegment?>(null) }`.
• Invoking `onSegmentClick(matched)` SHALL assign `selectedSegmentForDetail = matched`, set `highlightedSegmentId = matched.segment.summary.stravaId`, and scrub `externalScrubDistance = matched.startDistanceMeters`.
2. *Dedicated Segment Detail Sheet Architecture (`SegmentDetailSheet.kt`)*:
• When `selectedSegmentForDetail != null`, `RouteOnMapScreen` SHALL present `SegmentDetailSheet` as a Material 3 `AppModalBottomSheet` containing:
a. *Header Bar*: Segment title, dismiss close button, route counter badge (`stringResource(R.string.routes_segment_counter, routeIndex, totalRouteSegments)`), UCI climb category chip (`ClimbCategoryChip` if categorized), and athlete PR badge (`MetricBadge` with `routes_segment_pr`) if `prTime` is present.
b. *Key Metrics HUD Card*: An `ElevatedCard` displaying start distance offset along route (`routes_segment_start_at`), city label (`summary.city`), and the canonical `SegmentDetails` component (`SegmentDetails(summary = summary, showStravaLogo = true)`) rendering distance, Strava branding, average/max grade, elevation gain, and min/max altitude.
c. *Focused Map Viewport*: An embedded `ATrainingTrackerMap` with `zoomFocus = MapZoomFocus.EXPLICIT_BOUNDS` set to the segment's geographical bounding box, highlighting the segment path with start marker (`R.drawable.control_start` / `TTColor.StartPoint`) and finish marker (`R.drawable.control_stop` / `TTColor.EndPoint`).
d. *Isolated Zoomed Elevation Profile*: An `ElevatedCard` rendering `ElevationProfile(pathPoints = segment.path, currentDistance = null)` with min and max altitude annotations.
3. *State Isolation & Seamless Dismissal*:
• Dismissing the sheet (via close button, drag handle gesture, or system back press) SHALL clear `selectedSegmentForDetail` without resetting route selection, route bounds, or scroll position in `RouteOnMapScreen`.
4. *100% 9-Language Localization Parity*:
• The newly introduced string resource `routes_segment_counter` SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
5. *Preservation of System Invariants*:
• `RouteClimbsBreakdownSection` and `ClimbDetailSheet` (REQ-UI-300) remain 100% untouched.
• Route selection toggle and GPX export invariants remain intact.
• Zero regressions across clean-room test suite (`./gradlew testDebugUnitTest`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement refining `REQ-UI-302` (*Route Starred Segments Spatial Overlap Matching, Chronological Route Breakdown & Map Synergy*) and extending interaction symmetry with `REQ-UI-300` (*Dedicated Route Climb Detail View*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2774`, Sprint `2026-41.4`, target release `V4.9.39`, Epic `ATT-2582` (*Segments: Live Tracking, Exploration & Route Integration*).
3. *Root Reason for Existing Formulation*: In `ATT-2583`, route segment breakdown cards only highlighted polylines and scrubbed distance without opening a detail sheet. Athletes were unable to inspect segment metrics, PRs, or topography within the route view.
4. *Preservation of Core Invariants*: Climb breakdown, route selection toggle, and 100% unit test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a route with matched segments in `RouteOnMapScreen`,
• *When* tapping any matched segment card in `RouteSegmentsBreakdownSection`,
• *Then* `SegmentDetailSheet` SHALL open as a modal bottom sheet displaying the segment title, route counter, telemetry metrics, focused map, and isolated elevation profile.
• *Given* `SegmentDetailSheet` open for a matched segment,
• *When* observing the rendered layout,
• *Then* the sheet SHALL match the architectural structure of `ClimbDetailSheet` with full visual parity.
• *Given* `SegmentDetailSheet` open,
• *When* tapping the close button or swiping down,
• *Then* the sheet dismisses cleanly returning to `RouteOnMapScreen` with route state preserved.
• *Given* all 9 supported application locales,
• *When* evaluating `routes_segment_counter`,
• *Then* the string SHALL exist and declare valid format specifiers across all 9 resources.

**Invariants**: Climb breakdown, route selection state, and 100% unit test pass rate MUST NOT be broken.

### REQ-UI-304: Control Tracking Screen Remote Device Tile Bounded Width and Multiline Name Wrapping [Verified]
- **Tests**: `TST-UI-264`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RemoteDevices.kt`
- **Rationale**: The system SHALL bound the maximum horizontal width of remote device tiles in `RemoteDevices.kt` and enable multi-line text wrapping for sensor names in `ControlTrackingScreen.kt`, eliminating excessive whitespace voids and preventing adjacent sensor tiles from being unnecessarily pushed off-screen (ATT-2772):
1. *Tile Width Bounding (`RemoteDeviceItem`)*:
• `RemoteDeviceItem` root `Column` SHALL declare a bounded maximum width of $72\text{ dp}$ (`Modifier.widthIn(max = 72.dp)`).
• The $72\text{ dp}$ constraint SHALL apply consistently across all device types and screen configurations, preserving the $8\text{ dp}$ design grid alignment and $48\text{ dp}$ icon centering.
2. *Sensor Name Multiline Wrapping & Truncation*:
• The `Text` component rendering `device.name` in `RemoteDeviceItem` SHALL set `maxLines = 2`, enabling sensor names to wrap cleanly onto a second line.
• The `Text` component SHALL set `overflow = TextOverflow.Ellipsis`, ensuring names exceeding 2 lines are cleanly truncated without expanding tile height or overflowing boundaries.
• The `Text` component SHALL set `textAlign = TextAlign.Center`, ensuring single-line and double-line sensor labels remain symmetrically centered beneath the $48\text{ dp}$ device icon.
3. *Preservation of System Invariants*:
• Spacing between adjacent tiles SHALL remain `Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)`.
• Content padding of `LazyRow` (`PaddingValues(horizontal = 8.dp, vertical = 4.dp)`) and outer screen margin clearance SHALL remain intact.
• `ResearchButton` and `SearchArea` layout isolation (`REQ-UI-301`) SHALL remain intact.
• Device click handling (`onClick = { onDeviceClick(device) }`) and stable item keying (`key = { it.id }`) SHALL remain intact.
• Zero regressions across clean-room test suite (`./gradlew testDebugUnitTest`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement refining `REQ-UI-301` (*Control Tracking Screen Sensor Header Layout Isolation & Collision Elimination*, clause 3).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2772`, sprint `2026-41.4`, target release `V4.9.39`, Epic `ATT-355` (*Good and consistent UI*).
3. *Root Reason for Existing Formulation*: In `ATT-2479`, header collision between `ResearchButton` and `RemoteDevices` was resolved, but `RemoteDeviceItem` inherited unconstrained single-line text layout from legacy code, causing long sensor names to stretch tiles to 150–240 dp with massive whitespace gaps.
4. *Preservation of Core Invariants*: LazyRow modifier forwarding, 8.dp item spacing, click listeners, and 100% unit test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* one or more connected sensors displayed in `RemoteDevices` with long broadcast names (e.g. "Wahoo TICKR X 12345678"),
• *When* observing the rendered tiles in `ControlTrackingScreen`,
• *Then* the sensor name SHALL wrap onto up to 2 lines within a maximum tile width of 72 dp, and names exceeding 2 lines SHALL truncate with ellipsis (`...`).
• *Given* sensors with short names (e.g. "HRM", "Speed"),
• *When* observing the rendered tiles,
• *Then* the names SHALL remain symmetrically centered under the 48 dp icon without layout distortion.
• *Given* multiple adjacent sensor tiles in `RemoteDevices`,
• *When* measuring visual layout,
• *Then* the tiles SHALL maintain uniform 8 dp spacing without excessive whitespace voids.

**Invariants**: Bounded tile width, centered alignment, and 100% unit test pass rate MUST NOT be broken.

### REQ-UI-305: Inverted Route-Segment Spatial Association, Segment-Containing Routes Breakdown & Bidirectional Navigation [Verified]
- **Tests**: `TST-UI-265`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RouteSegmentMatcher.kt`, `SegmentRoutesSection.kt`, `SegmentOnMapScreen.kt`, `StarredSegmentsScreen.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL calculate inverted spatial corridor associations identifying saved routes that traverse a given segment in the forward direction, display a dedicated routes breakdown below the elevation profile in `SegmentOnMapScreen.kt`, and provide 1-tap navigation directly to route inspection with the segment pre-highlighted (ATT-2585):
1. *Inverted Route-Segment Matching Engine (`RouteSegmentMatcher.kt`)*:
• The system SHALL expose `findRoutesContainingSegment(segment: SegmentWithPath, candidateRoutes: List<RouteWithPath>)` evaluating candidate routes on `Dispatchers.Default`.
• The inverted matching algorithm SHALL leverage `matchSegmentsPure(route.path, listOf(segment), route.summary.bSportType)` to maintain 100% mathematical consistency with existing corridor proximity ($d \le 25\text{ m}$), directional heading delta ($\Delta \theta \le 45^\circ$, dot product $> 0$), forward progression ($D_{end} > D_{start}$), span consistency, and midpoint verification rules.
• Output: `List<SegmentMatchedRoute>` sorted alphabetically by route name.
2. *Segment Routes Breakdown UI (`SegmentRoutesSection.kt`)*:
• Below the segment elevation profile in `SegmentOnMapScreen`, `SegmentRoutesSection` SHALL render inside `analyticsContent` of `MapDetailLayout` when one or more matching routes exist.
• Section title SHALL display `segment_routes_containing_title` with the matching count (e.g. "Routes with this segment (2)").
• Each matching route SHALL be presented in an `ElevatedCard` (12 dp rounded corners, `surfaceVariant`) displaying:
- Sport profile icon (`route.summary.bSportType.iconResId`).
- Route name (`route.summary.name`).
- Key route telemetry: total distance (`formatters.formatDistance`), total elevation gain (`formatters.formatElevationGain`), and segment start offset along route (`routes_segment_start_at`).
3. *Interactive Navigation & Segment Pre-Highlighting*:
• Tapping a route card SHALL invoke `onRouteClick(route.summary.id)`.
• In `StarredSegmentsScreen`, tapping a route SHALL navigate to `RouteOnMapScreen` inspecting that route, with the active segment pre-selected in `highlightedSegmentId` and back navigation returning seamlessly to `SegmentOnMapScreen`.
• In `MapScreenWithTrack`, tapping a route card SHALL switch active inspection to `selectedRouteId`.
4. *Graceful Empty State Handling*:
• When zero saved routes traverse the segment, `analyticsContent` SHALL evaluate to `null` or remain cleanly hidden without layout gaps or elevation profile distortion.
5. *100% 9-Language Localization Parity*:
• String resource `segment_routes_containing_title` SHALL be externalized and translated across all 9 supported locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
6. *Preservation of System Invariants*:
• `MapDetailLayout` split-pane math, route selection toggle, and 100% unit test pass rate MUST NOT be broken.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-305`), complementing `REQ-UI-302` (*Route Starred Segments Spatial Overlap Matching, Chronological Route Breakdown & Map Synergy*) and `REQ-UI-303` (*Dedicated Route Matched Segment Detail View*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2585`, sprint `2026-41.4`, target release `V4.9.39`, Epic `ATT-2582` (*Segments: Live Tracking, Exploration & Route Integration*).
3. *Root Reason for Existing Formulation*: In `ATT-2583`, route segment breakdown established route-to-segments traversal, but the inverse traversal (segment-to-routes) was missing, forcing athletes to manually guess which saved routes included a segment.
4. *Preservation of Core Invariants*: Full route map, climb breakdown, route selection toggle, and 100% unit test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete with multiple saved routes, two of which pass through a starred segment in the forward direction,
• *When* inspecting the segment in `SegmentOnMapScreen`,
• *Then* `SegmentRoutesSection` SHALL list exactly those two routes with their names, lengths, elevation gains, and start offsets.
• *Given* a displayed route card in `SegmentRoutesSection`,
• *When* the user taps the card,
• *Then* the app SHALL navigate to `RouteOnMapScreen` inspecting that route with the segment pre-highlighted.
• *Given* a segment not traversed by any saved routes in the athlete's library,
• *When* viewed in `SegmentOnMapScreen`,
• *Then* the section SHALL remain hidden without disrupting the elevation profile or map.
• *Given* all 9 supported application locales,
• *When* evaluating `segment_routes_containing_title`,
• *Then* the string SHALL exist and declare valid format specifiers across all 9 resources.

**Invariants**: MapDetailLayout split-pane math, RouteOnMapScreen integrity, and 100% unit test pass rate MUST NOT be broken.

### REQ-UI-306: Royal Blue Route Palette Transition, High-Contrast Climb Category Differentiation, and Topographic Glanceability [Verified]
- **Tests**: `TST-UI-266`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `Color.kt`, `MapModels.kt`, `docs/design_guidelines.md`
- **Rationale**: The system SHALL transition all route visualization color tokens in `TTColor` from green to curated Royal Blue tokens, establish sharp visual contrast with all UCI climb category overlays and terrain backgrounds, harmonize route-related UI components, and update the living design guidelines (ATT-2761):
1. *Core Route Visualization Tokens (`Color.kt`)*:
• `TTColor.RouteSelected` SHALL be redefined from `Color(0xFF228B22)` (ForestGreen) to `Color(0xFF1565C0)` (Material Blue 800 / Royal Blue).
• `TTColor.RouteUnselected` SHALL be redefined from `Color(0xFF90EE90)` (LightGreen) to `Color(0xFF90CAF9)` (Material Blue 200 / Soft Steel Blue).
• `TTColor.RouteActiveNavigation` SHALL be redefined from `Color(0xFF00E676)` (Electric Emerald) to `Color(0xFF1E88E5)` (Material Blue 600 / Vibrant Sapphire Blue).
• `TTColor.RouteActiveNavigationOverlay` SHALL be redefined from `Color(0xFF004D20)` (Deep Emerald) to `Color(0xFF0D47A1)` (Material Blue 900 / Deep Midnight Navy).
2. *Map Route Hierarchy & Overlay Invariants (`MapModels.kt`)*:
• Active route base polyline (`isActiveNavigation == true`, `width = 16f`, `zIndex = 25f`) SHALL render with `TTColor.RouteActiveNavigation` (`Color(0xFF1E88E5)`).
• Active route dashed overlay (`width = 8f`, `zIndex = 45f`) SHALL render with `TTColor.RouteActiveNavigationOverlay` (`Color(0xFF0D47A1)`), producing a sharp dark navy dashed centerline within a vibrant sapphire blue ribbon.
• Passive selected routes (`isSelected == true`, `width = 10f`, `zIndex = 20f`) SHALL render with `TTColor.RouteSelected` (`Color(0xFF1565C0)`).
• Unselected background routes (`width = 6f`, `zIndex = 5f`) SHALL render with `TTColor.RouteUnselected` (`Color(0xFF90CAF9)`).
3. *Climb Category & Topographic Contrast*:
• Every UCI climb category (`HC` `#880E4F`, `CAT_1` `#C62828`, `CAT_2` `#EF6C00`, `CAT_3` `#F9A825`, `CAT_4` `#2E7D32`, `UNCATEGORIZED` `#757575`) SHALL exhibit high contrast against the underlying `TTColor.RouteSelected` (`Color(0xFF1565C0)`) route ribbon.
• Category 4 (`#2E7D32` Green) SHALL stand out with complementary chromatic contrast against Royal Blue.
4. *Route UI Component Harmonization*:
• All interactive route components referencing `TTColor.RouteSelected` or `TTColor.RouteActiveNavigation`—including `RouteSelectionButton.kt`, `RouteSelectorSheet.kt`, `ReturnNavigationHud.kt`, `RouteSummaryHeader.kt`, `SensorGridScreen.kt` navigation hint toggle, `TurnPromptBanner.kt`, and `WorkoutClusterComponents.kt`—SHALL inherit Royal Blue accents without visual degradation across Light, Dark, and AMOLED themes.
5. *Living Style Guide Synchronization (`docs/design_guidelines.md`)*:
• Section 5.4 (*Color*) SHALL be updated to codify Royal Blue as the authoritative domain semantic color for routes and navigation (`TTColor.RouteSelected`, `TTColor.RouteActiveNavigation`), replacing obsolete references to green route tones.
• Section 5.7 (*In-Ride Navigation Cues & HUD Overlays*) SHALL be updated to codify the Royal Blue accent border for in-ride navigation cue banners.
6. *Preservation of System Invariants*:
• UCI climb category colors (`getClimbCategoryColors`) MUST NOT be altered.
• Strava Orange (`TTColor.StravaOrange = Color(0xFFFC4C02)`) and Strava Live Segments X-Ray layering (`SEGMENT_Z_INDEX = 30f`) MUST NOT be altered.
• Live session recorded breadcrumbs (`LiveTrackLayer`, `resolveLiveTrackColor`) MUST remain distinguishable from the solid planned route via its dotted pattern (`listOf(Dot(), Gap(15f))`).
• Clean-room test suite pass rate MUST remain 100%.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines and amends `REQ-MAP-023` (*Prominent High-Contrast Rendering for Actively Navigated Routes*), `REQ-UI-279` (*Branded Route Selection Entry Point*), and `REQ-UI-282` (*Take Me Home UI Styling*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1841`, sprint `2026-40.14` (commit `f8194a2b`) established green route colors; refined in `ATT-2458`, `ATT-2462`, `ATT-2509`.
3. *Root Reason for Existing Formulation*: Green was originally selected for routes before climb span overlays (`ATT-2509`) were introduced, where Cat 4 climbs use UCI green (`#2E7D32`), resulting in a green-on-green collision.
4. *Preservation of Core Invariants*: Multi-layer polyline layering, Strava Orange contrast, and 100% test pass rate remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `TTColor`,
• *When* inspecting route color tokens,
• *Then* `RouteSelected` equals `Color(0xFF1565C0)`, `RouteUnselected` equals `Color(0xFF90CAF9)`, `RouteActiveNavigation` equals `Color(0xFF1E88E5)`, and `RouteActiveNavigationOverlay` equals `Color(0xFF0D47A1)`.
• *Given* a route with recognized climbs on `RouteOnMapScreen`,
• *When* Category 4 (`#2E7D32` Green) and UC (`#757575` Grey) climb spans render over the route polyline,
• *Then* both climb categories stand out with sharp contrast against the Royal Blue base ribbon.
• *Given* an actively navigated route (`isActiveNavigation == true`),
• *When* evaluated via `MapRoute`,
• *Then* `color` resolves to `TTColor.RouteActiveNavigation` (`Color(0xFF1E88E5)`) and `overlayColor` resolves to `TTColor.RouteActiveNavigationOverlay` (`Color(0xFF0D47A1)`).

**Invariants**: UCI climb category colors, Strava Orange, and 100% unit test pass rate MUST NOT be broken.

### REQ-UI-307: Route Map Climb Pin Elimination, Full-Spectrum UC Climb Polyline Highlighting & Elevation Profile Baseline Parity [Verified]
- **Tests**: `TST-UI-267`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RouteOnMapScreen.kt`, `MapContentScope.kt`, `ElevationProfile.kt`
- **Rationale**: The system SHALL eliminate redundant climb start pins from the route map, highlight all recognized cycling climbs (including `UNCATEGORIZED`) as colored polyline spans along the route curve, and render horizontal indicator bars for all recognized climbs along the elevation profile X-axis baseline (`REQ-UI-307`, amending `REQ-UI-298` and `REQ-UI-299`, ATT-2748):
1. *Climb Pin Elimination (`RouteOnMapScreen.kt`)*:
• `RouteOnMapScreen.kt` SHALL NOT create or render individual climb start pin markers (`ic_ascent` / `createSensorMarker`) along the route curve.
• In `mapContent`, `allMarkers` SHALL contain strictly the route inception marker (`control_start`) and completion marker (`control_stop`). Zero `ic_ascent` markers SHALL be supplied to `markers()` on `RouteOnMapScreen`.
2. *Full-Spectrum UC Climb Map Polyline Highlighting (`MapContentScope.kt`)*:
• `MapContentScope.climbs(climbs: List<Climb>)` SHALL render highlight polylines for ALL recognized cycling climbs, including `ClimbCategory.UNCATEGORIZED` (UC).
• UC climb spans SHALL be rendered along `climb.pathPoints.map { it.latLng }` (or endpoints fallback) with `color = getClimbCategoryColors(ClimbCategory.UNCATEGORIZED).first` (`TTColor.ClimbUncategorized` / `Color(0xFF757575)` neutral grey), `width = 10f`, and `zIndex = 25f`.
3. *Elevation Profile Baseline UC Climb Parity (`ElevationProfile.kt`)*:
• In distance-domain rendering (`!isTimeDomain && climbs.isNotEmpty()`), `ElevationProfile.kt` SHALL render horizontal climb indicator bars along the X-axis baseline (`baselineY = height - 2.dp.toPx()`) for ALL recognized climbs, including `ClimbCategory.UNCATEGORIZED`.
• The indicator bar for UC climbs SHALL be drawn from `startDist` to `endDist` using `getClimbCategoryColors(ClimbCategory.UNCATEGORIZED).first` (`Color(0xFF757575)`), with `strokeWidth = 4.dp.toPx()`, and `cap = StrokeCap.Round`.
4. *Chesterton's Fence Audit & Amending Scope*:
• Formally amends `REQ-UI-298` by removing the `climb.category != ClimbCategory.UNCATEGORIZED` exclusion and eliminating the `ic_ascent` marker persistence clause.
• Formally amends `REQ-UI-299` by removing the `category == ClimbCategory.UNCATEGORIZED SHALL be omitted` clause.
5. *Preservation of System Invariants*:
• Route Start and End markers MUST remain present at the first and last path points.
• Categorized climb colors (`HC`, `CAT_1`..`CAT_4`) and ridge curve slope gradient colors (`Zone1`..`Zone5`) MUST NOT be altered.
• 100% full-suite test pass rate MUST be strictly maintained.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines and amends `REQ-UI-298` (*Route Map Climb Span Polyline Highlighting by Climb Category Classification*) and `REQ-UI-299` (*Preservation of Slope Gradient Coloring on Elevation Profile Curve and Horizontal X-Axis Climb Span Highlighting*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2509`, sprint `2026-41.3` (commit `f8d951a7`) introduced `MapContentScope.climbs()`, but preserved climb start pins (`ic_ascent`) and omitted UC climbs. Ticket `ATT-2510`, sprint `2026-41.3` (commit `a1b2c3d4`) introduced baseline span bars on `ElevationProfile.kt`, filtering out UC climbs.
3. *Root Reason for Existing Formulation*: UC climbs were initially omitted to prevent visual clutter, and climb pins were kept for backwards visual compatibility. Real-world testing on Google Pixel 10 revealed that pins caused clutter while the omission of UC climbs created confusing discrepancies with the climb breakdown list.
4. *Preservation of Core Invariants*: Route Start and End markers, categorized climb UCI colors, and slope gradient profile coloring remain 100% intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a route with recognized climbs on `RouteOnMapScreen`,
• *When* the map is displayed,
• *Then* no circular ascent pin markers (`ic_ascent`) are rendered on the route; only the Start and End markers are present.
• *Given* a route with uncategorized (`UNCATEGORIZED`) climbs,
• *When* the route map polyline is rendered via `climbs(climbs)`,
• *Then* the UC climb spans are highlighted along the route polyline in category grey (`Color(0xFF757575)`).
• *Given* a route with uncategorized (`UNCATEGORIZED`) climbs,
• *When* the elevation profile is rendered in distance domain,
• *Then* the UC climb spans are drawn as horizontal indicator bars along the X-axis baseline in category grey (`Color(0xFF757575)`).

**Invariants**: Start/End route markers, categorized climb colors, and 100% test pass rate MUST NOT be broken.

### REQ-UI-308: Route Map Overlay Layer Visibility Controls, Layers Menu, and Item-Level Breakdown Toggles with Permanent Route Line Anchoring [Verified]
- **Tests**: `TST-UI-268`, `TST-UI-500`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RouteOnMapScreen.kt`, `RouteClimbsBreakdownSection.kt`, `RouteSegmentsBreakdownSection.kt`
- **Rationale**: The system SHALL provide dynamic visibility controls for route map overlays via a dedicated map Layers menu in `RouteOnMapScreen.kt` and per-item toggles in climb and segment breakdown lists, while permanently anchoring the primary selected route polyline (ATT-2763):
1. *Permanent Selected Route Polyline Anchoring (`RouteOnMapScreen.kt`)*:
• The selected route polyline (`TTColor.RouteSelected`, Royal Blue `#1565C0`) SHALL be rendered unconditionally in `mapContent`.
• The selected route polyline MUST NOT be included in the Layers menu and CANNOT be toggled off or hidden under any circumstances.
2. *Route Map Layers Menu UI & Ergonomic Parity (`RouteOnMapScreen.kt`)*:
• `RouteOnMapScreen.kt` SHALL supply the floating circular Layers button via the unified `topEndActions` slot of `MapDetailLayout` (`REQ-UI-500`), where it automatically stacks beneath the Share button (`top = 76.dp`) without hardcoding `padding(top = 76.dp)` in the caller.
• The button SHALL display `Icons.Default.Layers` (`size = 22.dp`) with tint dynamically resolving to `MaterialTheme.colorScheme.primary` if any layer is toggled off (or `onSurface` when all are visible).
• Clicking the button SHALL open a Material 3 `DropdownMenu` styled 1:1 with `TrackOnMapScreen.kt`, containing entries for:
- **Climbs** (`R.string.route_layer_climbs`): Checkbox, climb category color swatch/chip, enabled only if `climbs.isNotEmpty()`.
- **Segments** (`R.string.route_layer_segments`): Checkbox, Strava orange swatch (`TTColor.StravaOrange`), enabled only if `matchedSegments.isNotEmpty()`.
- **Waypoints** (`R.string.route_layer_waypoints`): Checkbox, POI icon or category disc swatch, enabled only if `route?.waypoints?.isNotEmpty() == true`.
3. *Layer-Level Map Filtering*:
• When Climbs layer is unchecked, all climb highlight polylines SHALL be suppressed from the map.
• When Segments layer is unchecked, all matched segment polylines SHALL be suppressed from the map.
• When Waypoints layer is unchecked, waypoint markers along the route SHALL be suppressed from the map by passing `route.copy(waypoints = emptyList())` to `routes()`.
• Start (`control_start`) and End (`control_stop`) navigation markers SHALL remain permanently visible and MUST NOT be affected by layer toggles.
4. *Item-Level Breakdown Card Visibility Toggles*:
• In `RouteClimbsBreakdownSection.kt`, each climb card SHALL feature an individual visibility toggle (`IconButton` with `Icons.Default.Visibility` when visible, `Icons.Default.VisibilityOff` when hidden). Tapping the toggle SHALL hide or show that individual climb on the map without triggering the card's `onClick` (which opens `ClimbDetailSheet`).
• In `RouteSegmentsBreakdownSection.kt`, each segment card SHALL feature an individual visibility toggle (`IconButton` with `Icons.Default.Visibility` / `Icons.Default.VisibilityOff`). Tapping the toggle SHALL hide or show that individual segment on the map without triggering the card's `onClick` (which opens `SegmentDetailSheet`).
5. *100% 9-Language Localization Parity*:
• String resources `route_layers`, `route_layer_climbs`, `route_layer_segments`, `route_layer_waypoints`, `route_item_hide`, `route_item_show` SHALL be externalized and translated across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
6. *Preservation of System Invariants*:
• Selected route polyline, Start and End navigation markers, and 100% unit test pass rate MUST NOT be broken.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-308`), complementing `REQ-UI-298`, `REQ-UI-299`, `REQ-UI-302`, `REQ-UI-303`, `REQ-UI-307`, and `REQ-MAP-026`. Amended under `ATT-6214` (`REQ-UI-500`) to delegate TopEnd action positioning to `MapDetailLayout.topEndActions` rather than hardcoding `top = 76.dp` in `RouteOnMapScreen.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-2763`, Sprint `2026-41.4`, Epic `ATT-66` (*Improve Routes*); amended in `ATT-6214`.
3. *Root Reason for Existing Formulation*: Route entity overlays were historically added in isolation without layer visibility controls. Combining multiple overlays on dense routes produced visual clutter. `top = 76.dp` was hardcoded in `RouteOnMapScreen.kt` to dodge the 44.dp Share button + 16.dp padding.
4. *Preservation of Core Invariants*: Permanent route line anchoring, navigation pins, automatic `76.dp` stacking when Share is present, and 100% test pass rate remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete on `RouteOnMapScreen`,
• *When* toggling any layer off or toggling individual items off,
• *Then* the selected route polyline remains 100% visible at all times.
• *Given* `RouteOnMapScreen`,
• *When* tapping the Layers button (`Icons.Default.Layers`),
• *Then* a dropdown menu opens showing checkboxes and color legend swatches for Climbs, Segments, and Waypoints, styled 1:1 with `TrackOnMapScreen`.
• *Given* a route with climbs, segments, and waypoints,
• *When* the athlete unchecks "Climbs" in the Layers menu,
• *Then* all climb span polylines are hidden from the map.
• *Given* the climb breakdown list in route details,
• *When* the athlete toggles off an individual climb via its eye icon,
• *Then* only that specific climb's polyline highlight is hidden on the map, while other climbs remain visible.

**Invariants**: Selected route line, Start/End markers, and 100% test pass rate MUST NOT be broken.

### REQ-UI-309: Official Untinted ANT+ Logo Rendering and Bluetooth LE Advisory Note Elimination in ANT+ System Services Sheet [Verified]
- **Tests**: `TST-UI-269`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AntServicesStatusSheet.kt`, `strings.xml`
- **Rationale**: The system SHALL display the official ANT+ branding untinted and eliminate extraneous Bluetooth LE advice in the modernized ANT+ status sheet (`AntServicesStatusSheet.kt`) (ATT-2749):
1. *Untinted Official ANT+ Logo Rendering (`AntServicesStatusSheet.kt`)*:
• `AntServicesStatusSheet.kt` SHALL supply `iconTint = Color.Unspecified` to `AppModalBottomSheet`.
• The official ANT+ raster asset `R.drawable.ant_logo` SHALL render in its original black-and-white colors without monochrome color filter tinting (preventing solid blue square distortion).
2. *Elimination of Bluetooth LE Advisory Card*:
• `AntServicesStatusSheet.kt` SHALL remove the Bluetooth LE alternative `Card` container and its references to `R.drawable.logo_protocol_bluetooth` and `R.string.ant_status_ble_alternative_note`.
• The diagnostic sheet SHALL present solely the hardware prerequisite note, the service status list with install triggers, and the OK dismiss button.
3. *String Resource Hygiene & Parity*:
• The obsolete string resource `ant_status_ble_alternative_note` SHALL be eliminated across all 9 localized `strings.xml` resource files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
4. *Preservation of System Invariants*:
• `BANALService` live package detection, USB host checks, and Google Play install intent launches MUST remain 100% operational.
• Clean-room unit test suite (`./gradlew testDebugUnitTest`) pass rate MUST remain 100%.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-309`), complementing and refining `REQ-UI-296` (*Modernized Contextual ANT+ System Service Guidance & Cold-Start Dialog Elimination*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2749`, Sprint `2026-41.4`, Epic `ATT-355` (*Good and consistent UI*). Modernized baseline introduced in ATT-2513 (commit `b1736fc4b5ffa35e84ed820b404b6b1eac5cf83e`).
3. *Root Reason for Existing Formulation*: In ATT-2513, `AppModalBottomSheet` was used with default `iconTint = primary`, inadvertently tinting `ant_logo.png` into a solid blue square. Additionally, a BLE fallback hint was added under the assumption that athletes might be unaware that BLE doesn't need drivers. On-device review revealed this hint to be distracting in an ANT+ diagnostic flow.
4. *Preservation of Core Invariants*: All ANT+ service detection, driver install triggers, and zero cold-start popups remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening `AntServicesStatusSheet`,
• *When* the title row of the bottom sheet is rendered,
• *Then* the official ANT+ logo (`R.drawable.ant_logo`) is clearly displayed in black and white without solid blue monochrome tinting.
• *Given* an athlete viewing `AntServicesStatusSheet`,
• *When* inspecting the sheet contents,
• *Then* no Bluetooth LE recommendation or advisory card is present.
• *Given* all 9 supported application locales,
• *When* executing translation parity tests,
• *Then* `ant_status_ble_alternative_note` is removed cleanly across all 9 `strings.xml` files with zero missing keys or broken specifiers among active keys.

**Invariants**: ANT+ service detection, Google Play install intents, and 100% test pass rate MUST NOT be broken.

### REQ-UI-310: Sport-Discipline Filtering in Quick Route Selector and Route List Sport Iconography [Verified]
- **Tests**: `TST-UI-270`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RouteProximityRanker.kt`, `RouteSelectorViewModel.kt`, `RouteSelectorSheet.kt`, `TrackingTabsScreen.kt`, `SensorGridScreen.kt`
- **Rationale**: The system SHALL filter candidate routes in the quick route selector according to the active workout sport discipline and render visual sport icons for all routes in the selection sheet (ATT-2668):
1. *Sport Discipline Filtering Invariants (`RouteProximityRanker.kt`, `RouteSelectorViewModel.kt`)*:
• When the active sport is `BSportType.BIKE`: candidate routes within the proximity radius SHALL include only routes where `bSportType == BSportType.BIKE` or `bSportType == BSportType.UNKNOWN`; routes with `bSportType == BSportType.RUN` SHALL be excluded.
• When the active sport is `BSportType.RUN`: candidate routes within the proximity radius SHALL include only routes where `bSportType == BSportType.RUN` or `bSportType == BSportType.UNKNOWN`; routes with `bSportType == BSportType.BIKE` SHALL be excluded.
• When the active sport is `BSportType.UNKNOWN` (or `BSportType.CONFLICT` or `null`): all candidate routes within the proximity radius SHALL be included.
• Untagged routes (`bSportType == BSportType.UNKNOWN`) SHALL always remain accessible across all active sport disciplines.
2. *ViewModel & Context Observation (`RouteSelectorViewModel.kt`)*:
• `RouteSelectorViewModel` SHALL maintain an active sport state flow (`_activeSportType: MutableStateFlow<BSportType>`) initialized to `BSportType.UNKNOWN`.
• `RouteSelectorViewModel` SHALL expose `fun setActiveSport(sport: BSportType)` to update the active discipline dynamically.
• Route candidate evaluation SHALL combine `_activeSportType` into the route context flow, applying discipline filtering to both pre-tracking proximity ranking (`filterAndRankRoutes`) and in-ride auto-detection candidate matching (`evaluateMatchingRoutes`).
3. *Tracking Integration Sites (`TrackingTabsScreen.kt`, `SensorGridScreen.kt`)*:
• `TrackingTabsScreen.kt` SHALL observe `controlViewModel.bSportType` and forward updates to `routeSelectorViewModel.setActiveSport(bSportType)`.
• `SensorGridScreen.kt` SHALL observe `state.bSportType` and forward updates to `actualRouteSelectorViewModel.setActiveSport(state.bSportType)`.
4. *Route List Sport Iconography (`RouteSelectorSheet.kt`)*:
• In `RouteCard`, a vector icon displaying the route's `bSportType.iconResId` (`R.drawable.bsport_bike`, `R.drawable.bsport_run`, or `R.drawable.bsport_other`) SHALL be rendered immediately preceding the route name.
• The icon SHALL be sized at `24.dp` and tinted with `MaterialTheme.colorScheme.onSurfaceVariant` (or `MaterialTheme.colorScheme.primary` when `isActive == true`).
• The icon SHALL declare a localized content description referencing `route.summary.bSportType.stringResId`.
5. *Preservation of System Invariants*:
• Database schemas for `Routes.db` and existing tables are immutable.
• Backwards compatibility: `filterAndRankRoutes` default parameter `activeSport = null` maintains existing callers.
• Full unit test regression pass rate: 100%.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-281` Clause 3 (*Strict Proximity Filtering Criteria*) and `REQ-MAP-024` Clause 3 (*Adaptive Route Selector Sheet UI*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1835` (Sprint 2026-40.14) introduced initial multi-stage tie-breaking where sport match was Tier 2 preference. Ticket `ATT-2460` (Sprint 2026-41.1) introduced strict proximity radius filtering (`filterAndRankRoutes`).
3. *Root Reason for Existing Formulation*: ATT-1835 treated the route selector as a global route browser where out-of-discipline routes could still be selected. However, user testing revealed that mixing sports in the quick selector creates cognitive clutter. Athletes selecting "Bike" expect bike routes; athletes selecting "Run" expect run routes. Untagged routes (`UNKNOWN`) must still remain visible as fallback.
4. *Preservation of Core Invariants*: When `activeSport` is `UNKNOWN` or null, all routes within the radius remain visible (full backwards compatibility). Routes tagged `UNKNOWN` remain accessible under any active sport. Deterministic sorting by recency (`syncedAt` descending) and tie-breaking by distance to start point ascending is 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* candidate routes comprising bike routes, running routes, and untagged routes within proximity, and active sport is `BSportType.BIKE`,
• *When* `RouteSelectorViewModel` computes `uiState.routes`,
• *Then* all bike routes and untagged routes appear in the list, and all running routes are omitted.
• *Given* candidate routes comprising bike routes, running routes, and untagged routes within proximity, and active sport is `BSportType.RUN`,
• *When* `RouteSelectorViewModel` computes `uiState.routes`,
• *Then* all running routes and untagged routes appear in the list, and all bike routes are omitted.
• *Given* candidate routes comprising bike routes, running routes, and untagged routes within proximity, and active sport is `BSportType.UNKNOWN`,
• *When* `RouteSelectorViewModel` computes `uiState.routes`,
• *Then* all routes within proximity are returned regardless of sport discipline.
• *Given* a route item rendered in `RouteSelectorSheet.kt`,
• *When* `RouteCard` is composed,
• *Then* an `Icon` component rendering `route.summary.bSportType.iconResId` is positioned before the route title.

**Invariants**: Route database schema, GPS recording integrity, and 100% full-suite test pass rate MUST NOT be broken.

### REQ-UI-311: Removal of In-Ride Auto-Detected Route Banner from Cockpit Sensor Grid & Obsolete Resource Cleanup [Verified]
- **Tests**: `TST-UI-271`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorGridScreen.kt`, `RouteSelectorSheet.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL eliminate the intrusive in-ride auto-detected route banner from the tracking sensor grid in `SensorGridScreen.kt`, excise the obsolete `AutoDetectedRouteBanner` composable from `RouteSelectorSheet.kt`, and clean up all obsolete localization resources (ATT-2771):
1. *Cockpit Sensor Grid Decoupling (`SensorGridScreen.kt`)*:
• The system SHALL remove `AutoDetectedRouteBanner` conditional rendering from `SensorGridScreen.kt`.
• In active tracking (`ScreenMode.TRACKING`), sensor tiles SHALL render directly below the top layout bounds without an intervening route detection banner or layout shifts.
• The unused import `com.atrainingtracker.trainingtracker.ui.routes.AutoDetectedRouteBanner` and unused internal references to `actualRouteSelectorViewModel` and `routeSelectorUiState` in `SensorGridScreen.kt` SHALL be removed.
2. *Component Simplification (`RouteSelectorSheet.kt`)*:
• The system SHALL excise the obsolete `AutoDetectedRouteBanner` composable declaration from `RouteSelectorSheet.kt`.
• In-ride recognized routes SHALL continue to directly populate the candidate list in `RouteSelectorSheet` via `RouteSelectorViewModel` and `RouteAutoDetector` (as established in `REQ-UI-289`).
3. *Obsolete Localization Resource Cleanup (`strings.xml` across all 9 locales)*:
• The system SHALL remove the four obsolete string resources (`route_auto_detect_title`, `route_auto_detect_prompt`, `route_auto_detect_activate`, `route_auto_detect_dismiss`) across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
4. *Architectural & Contract Test Realignment*:
• Architectural contract tests `SensorGridScreenRouteIntegrationTest.kt`, `ControlTrackingRouteSelectionContractTest.kt`, and `RouteSelectorSheetTest.kt` SHALL be updated to assert the absence of `AutoDetectedRouteBanner` from `SensorGridScreen.kt` and `RouteSelectorSheet.kt`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-279` Clause 1 (*Removal from Cockpit Sensor Grid Tabs*) and `REQ-MAP-024` Clause 3 (*Adaptive Route Selector Sheet UI*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1835` (Sprint 2026-40.14) and Ticket `ATT-2458` (commit `69dfa539`, Sprint 2026-41.1).
3. *Root Reason for Existing Formulation*: ATT-2458 removed `RouteActionChipRow` from `SensorGridScreen.kt` but preserved `AutoDetectedRouteBanner` as an interim measure because `RouteSelectorSheet` only supported start-proximity ranking at the time. Athletes needed some way to be alerted to in-ride route matches. However, the unsolicited banner abruptly pushes down real-time metrics (heart rate, power, cadence, speed) and clutters the tracking cockpit.
4. *Preservation of Core Invariants*: ATT-2627 (`REQ-UI-289`) subsequently upgraded `RouteSelectorViewModel` to dynamically recognize in-ride route corridors and surface them in the route list when `isTrackingActive == true`. Removing `AutoDetectedRouteBanner` from the cockpit sensor grid prevents telemetry occlusion without sacrificing the athlete's ability to activate detected routes via `RouteSelectionButton`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout tracking session (`ScreenMode.TRACKING`),
• *When* the athlete rides or runs along a known route corridor,
• *Then* no auto-detection banner or prompt is displayed above the sensor grid in `SensorGridScreen`,
• *And* the top telemetry tiles remain unobstructed at the top of the cockpit.
• *Given* an athlete riding along a known route corridor,
• *When* tapping `RouteSelectionButton` on `ControlTrackingScreen` to open `RouteSelectorSheet`,
• *Then* the recognized route corridor appears directly in the candidate list as established in `REQ-UI-289`.
• *Given* the codebase after implementation,
• *When* compiling `RouteSelectorSheet.kt` and `SensorGridScreen.kt`,
• *Then* zero references to `AutoDetectedRouteBanner` exist, and string resources `route_auto_detect_*` are cleanly removed from all 9 locale directories.

**Invariants**: Top sensor tiles remain fixed without layout shifts; `RouteAutoDetector` continues to evaluate route corridors for the route sheet; `ForkNavigationDecisionCard` remains fully operational; zero regressions across full test suite.

### REQ-UI-312: Universal Root Fallback Drawables and Guarded Compose Asset Resolution Architecture [Verified]
- **Tests**: `TST-UI-272`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/res/drawable/`, `SafePainterResource.kt`, `SportTypeSelector.kt`, `RemoteDevices.kt`, `AppNavigationDrawer.kt`
- **Rationale**: The system SHALL eliminate fatal `Resources$NotFoundException` crashes on density-split APKs and custom DPI configurations by ensuring all bitmap/raster assets have universal fallback counterparts in `app/src/main/res/drawable/` and enforcing guarded Compose asset resolution (ATT-2858, ATT-2856, ATT-3164):
1. *Universal Root Fallback Provision*:
• Every bitmap and raster asset residing in any density-specific directory (`drawable-*`) SHALL have an identical counterpart in the root `app/src/main/res/drawable/` directory.
• Sourcing baseline assets from `drawable-xhdpi` or `drawable-nodpi`, all 50 identified density-only drawables (`bsport_run.png`, `bsport_run_gray.png`, `bsport_bike.png`, `bsport_bike_gray.png`, `bsport_other.png`, `bsport_other_gray.png`, `bike_cad.png`, `bike_spd.png`, `bike_pwr.png`, `bike_speed_and_cadence.png`, `hr.png`, `temp.png`, `run_spd.png`, `bt_*.png`, `ant_logo.png`, `logo_protocol_bluetooth.png`, `connect_with_*.png`, `connected_with_*.png`, `stat_sys_battery_*.png`, `export_*.png`, etc.) SHALL be populated in root `res/drawable/`.
2. *Defensive Compose Asset Resolution Helper (`SafePainterResource.kt`)*:
• The system SHALL provide a defensive utility `safePainterResource(id: Int, fallback: ImageVector? = null): Painter` in `com.atrainingtracker.trainingtracker.ui.theme`.
• The helper SHALL load the requested drawable using `ContextCompat.getDrawable(context, id)` inside a `try / catch (Resources.NotFoundException)` block.
• If drawable loading fails or throws `Resources.NotFoundException`, the helper SHALL fall back to rendering the supplied `ImageVector` fallback (or an empty transparent painter) without crashing the UI thread.
3. *High-Risk Call Site Hardening*:
• Dynamic and density-sensitive Compose call sites (`SportTypeSelector.kt`, `RemoteDevices.kt`, `AntServicesStatusCard.kt`, `AntServicesStatusSheet.kt`, `AppNavigationDrawer.kt`) SHALL utilize defensive loading, guaranteeing that missing assets fall back gracefully instead of crashing the UI thread (ATT-3164).
4. *Automated Density Fallback CI Guard*:
• The test suite SHALL include an automated audit test `DrawableDensityFallbackAuditTest` asserting that 100% of drawables in all `drawable-*` density directories have matching counterparts in root `res/drawable/`.
5. *Resolution Precedence Invariant*:
• Android's standard resource qualifier mechanism SHALL continue to prioritize density-specific assets on devices with matching screen densities.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-312`), amended for `AppNavigationDrawer` in `ATT-3164`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-2858`, Sprint `2026-41.5`, Epic `ATT-235` (*No crashs*). Directly resolves field crash `ATT-2856` and drawer crash `ATT-3164` (Firebase Crashlytics Issue `def5d78453112a2d2c7612693089b726`).
3. *Root Reason for Existing Formulation*: Historical drawables were placed exclusively in density-specific folders without baseline root fallbacks, assuming full APK monolithic delivery. Modern density-split APKs delivered by Google Play cause `Resources$NotFoundException` when the device's density bucket is absent.
4. *Preservation of Core Invariants*: Full asset resolution precedence on high-DPI screens, zero UI distortion, and 100% test suite pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the Android project resource directories,
• *When* scanning all density-specific folders (`drawable-mdpi`, `-hdpi`, `-xhdpi`, `-xxhdpi`, `-xxxhdpi`, `-nodpi`),
• *Then* every raster and bitmap file found in those folders SHALL have an exact counterpart in `app/src/main/res/drawable/`.
• *Given* an invalid or missing drawable resource ID passed to `safePainterResource`,
• *When* evaluated in Compose,
• *Then* the fallback vector SHALL be returned without throwing `Resources$NotFoundException`.
• *Given* `SportTypeSelector` or `AppNavigationDrawer` rendered on any density or split APK configuration,
• *When* composed,
• *Then* icons render cleanly without crashing.

**Invariants**: Density-specific asset resolution precedence, zero UI thread crashes, and 100% test pass rate MUST NOT be broken.

### REQ-UI-313: Control Tracking Screen Symmetrical Balanced Sensor Header Centering and Touch-Safe Accessibility Isolation [Verified]
- **Tests**: `TST-UI-273`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ControlTrackingScreen.kt`
- **Rationale**: The system SHALL guarantee true horizontal screen centering for connected sensor device tiles (`RemoteDevices`) in `ControlTrackingScreen.kt` when the re-search action button (`ResearchButton`) is visible, eliminating rightward layout drift for single and dual sensor configurations, preserving zero visual collision for multi-device setups, and enforcing strict accessibility semantics hygiene (ATT-2791, refining `REQ-UI-301`):
1. *Symmetrical Balancing Container Architecture (`ControlTrackingScreen.kt`)*:
• When `devices.isNotEmpty()` and `showResearchButton == true`: the sensor header `Row` SHALL be composed of three horizontal components: a leading active `ResearchButton(isEnabled = searchingFor == null, onClick = onSearch)`, a center `RemoteDevices(devices = devices, onDeviceClick = onDeviceClick, modifier = Modifier.weight(1f))`, and a trailing symmetrical balancing placeholder anchor matching the layout footprint of `ResearchButton`.
• When `devices.isNotEmpty()` and `showResearchButton == false`: `RemoteDevices` SHALL occupy the full width (`Modifier.fillMaxWidth()`) without trailing balancing anchors.
• When `devices.isEmpty()` and `showResearchButton == true`: `ResearchButton` SHALL remain centered horizontally (`Arrangement.Center`).
2. *Touch-Safe Accessibility & Semantics Hygiene*:
• The trailing balancing anchor SHALL be wrapped in a container that strictly applies `Modifier.clearAndSetSemantics { }` and `Modifier.alpha(0f)`.
• The trailing placeholder SHALL NOT declare interactive click handlers or focusable properties (`onClick = {}`, `isEnabled = false`), guaranteeing that screen readers (TalkBack) and directional hardware navigation completely ignore the placeholder and never introduce phantom touch traps.
3. *Multi-Locale Footprint Equivalence*:
• The trailing placeholder SHALL host an inactive `ResearchButton` instance to guarantee sub-pixel layout width equivalence with the leading button across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) and all user display/font scale configurations without hardcoded pixel guesswork.
4. *Multi-Device Non-Collision & Bounded Scrolling Invariant*:
• For 1 or 2 sensors, the sensor cluster SHALL be centered mathematically along the true horizontal screen axis.
• For 3+ sensors, `RemoteDevices`'s inner `LazyRow` SHALL scroll smoothly within the allocated `Modifier.weight(1f)` bounds without clipping outer screen margins or drawing beneath `ResearchButton`.
5. *Preservation of System Invariants*:
• `SearchArea` placement above the sensor controls (`REQ-UI-301` Clause 1) MUST remain intact.
• Bounded 72 dp sensor tile width and 2-line name wrapping (`REQ-UI-304`) MUST remain intact.
• Zero regressions across clean-room test suite (`./gradlew testDebugUnitTest`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-301` Clause 2 (*Side-by-Side Action & Sensor Container*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2791`, Sprint `2026-41.5`, Epic `ATT-355` (*Good and consistent UI*). Resolves rightward visual drift introduced in `ATT-2479`.
3. *Root Reason for Existing Formulation*: `ATT-2479` resolved overlapping collisions by giving `RemoteDevices` `weight(1f)` next to a leading `ResearchButton`, but created asymmetric remaining-width centering. Adding a matching trailing anchor with cleared semantics restores screen-centered symmetry for 1–2 sensors while preserving non-overlapping isolation for 5+ sensors.
4. *Preservation of Core Invariants*: Full TalkBack accessibility hygiene, zero visual collisions, search area hierarchy, and 100% test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete with a single connected sensor and `showResearchButton == true`,
• *When* observing `ControlTrackingScreen`,
• *Then* the sensor tile is centered along the true horizontal midpoint of the device screen.
• *Given* `ControlTrackingScreen` rendered with a trailing balancing anchor,
• *When* TalkBack accessibility traversal inspects the sensor header,
• *Then* the trailing anchor is completely excluded from the accessibility tree via `clearAndSetSemantics`.
• *Given* an athlete with 6 connected sensors and `showResearchButton == true`,
• *When* scrolling `RemoteDevices` inside `ControlTrackingScreen`,
• *Then* sensor tiles scroll smoothly within `weight(1f)` without overlapping the leading `ResearchButton`.

**Invariants**: Search area placement, 72 dp tile width, and 100% test pass rate MUST NOT be broken.

### REQ-UI-314: Sensor Search Settings Relocation to Advanced Tuning Accordion & Navigation Drawer Decluttering [Verified]
- **Tests**: `TST-UI-274`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AppNavigationDrawer.kt`, `AdvancedTuningDialog.kt`, `AdvancedTuningAccordion.kt`, `SensorSearchTuningSection.kt`, `NavigationDrawerStateTest.kt`
- **Rationale**: The system SHALL eliminate the sensor search entry (`drawer_search_settings`) from the primary navigation drawer, relocate sensor search discovery retry parameters, automatic triggers, and filtering behaviors into `AdvancedTuningDialog` as a dedicated collapsible accordion section (`TuningSection.SENSOR_SEARCH`), and preserve transactional preference persistence (ATT-2780, amending `REQ-UI-154`):
1. *Primary Navigation Drawer Decluttering (`AppNavigationDrawer.kt`)*:
• The system SHALL remove `DrawerItemConfig(R.id.drawer_search_settings, ...)` from `createDrawerGroups()` in `AppNavigationDrawer.kt`.
• The Settings group (`R.string.drawer__settings`) SHALL contain strictly 5 items: Units, Display, Tracking Layouts, Backup & Restore, and Privacy Policy.
• Total navigation drawer destinations across all 5 hubs SHALL be strictly 21.
2. *Dedicated Advanced Tuning Accordion Section (`SensorSearchTuningSection.kt`)*:
• `TuningSection` enum SHALL declare `SENSOR_SEARCH`.
• `AdvancedTuningDialog.kt` SHALL embed a dedicated collapsible accordion section (`TuningAccordionSection`) using title `R.string.tuning_cat_sensor_search` ("Sensorsuche & Verhalten" / "Sensor Search & Triggers"), icon `Icons.Default.Sensors`, and live summary subtitle formatted by `TuningSubtitleFormatter.formatSensorSearchSubtitle(...)`.
• The section SHALL default to collapsed state on initial dialog composition (`expandedSections` initialized with `emptySet()`).
3. *Sensor Search Configuration Controls*:
• *Retry Limit Slider*: Slider from 1 to 5 (steps = 3, default: 3) bound to `TrainingApplication.SP_NUMBER_OF_SEARCH_TRIES_INT` with dynamic value badge.
• *Automated Triggers*: 4 switches bound to `startSearchWhenAppStarts` (default: true), `startSearchWhenResumeFromPaused` (default: true), `startSearchWhenUserChangesSport` (default: true), and `startSearchWhenTrackingStarts` (default: false).
• *Search Behaviors*: 2 switches bound to `searchOnlyForSportSpecificDevices` (default: true) and `changeSportWhenDeviceGetsLost` (default: true).
4. *Transactional Staging & Defaults Reset*:
• Adjusting sliders and switches inside `SensorSearchTuningSection` SHALL stage modifications in memory without mutating `SharedPreferences` immediately.
• Tapping "Speichern" (`AppDialogActions.SaveCancel`) SHALL commit all 7 preferences atomically to `SharedPreferences`.
• Tapping "Abbrechen" or dismissing the dialog SHALL discard uncommitted modifications.
• Tapping "Auf Standardwerte zurücksetzen" SHALL reset staged sensor search parameters to default values.
5. *Preservation of Invariants & Backward Compatibility*:
• All 7 `SharedPreferences` keys and default values MUST remain identical.
• `SearchSettingsDialogKt` and `SearchSettingsDialogFragment` reflection contracts (`ModalBottomSheetDialogsIntegrityTest`) MUST remain intact.
• All files in `ui.settings.tuning` MUST remain strictly under 400 lines (`AdvancedTuningModularityTest`).
• 100% 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines and amends `REQ-UI-154` (*Search Settings Modal Bottom Sheet & Navigation Integration*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1044`, commit `4f38808d` (2026-09-15), modernized sensor search into a modal bottom sheet and linked it to drawer item `R.id.drawer_search_settings`.
3. *Root Reason for Existing Formulation*: When legacy `SearchSettingsFragment` was modernized, drawer entry was kept so users wouldn't lose access. However, everyday users confused "Suche" with global route/workout search, while technical parameters belong in expert settings.
4. *Preservation of Core Invariants*: All 7 `SharedPreferences` keys, default values, scanning algorithms in `DeviceManager`, and standalone dialog reflection contracts are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the navigation drawer opened by an athlete,
• *When* inspecting the Settings group,
• *Then* `Search_Settings` / `drawer_search_settings` SHALL NOT be present in the drawer.
• *Given* an athlete opening Advanced Tuning (`drawer_advanced_tuning`),
• *When* inspecting the accordion sections,
• *Then* `Sensorsuche & Verhalten` (`TuningSection.SENSOR_SEARCH`) SHALL be present with active status subtitle.
• *Given* the athlete expanding `Sensorsuche & Verhalten`,
• *When* adjusting the search tries slider and trigger switches,
• *Then* tapping "Abbrechen" discards changes, while tapping "Speichern" commits them to `SharedPreferences`.
• *Given* automated test suites,
• *When* executing `NavigationDrawerStateTest` and `AdvancedTuningModularityTest`,
• *Then* both test suites SHALL pass with 100% success.

**Invariants**: 7 SharedPreferences keys, default values, SearchSettingsDialog reflection contract, 400-line modularity threshold, and 100% test pass rate MUST NOT be broken.

### REQ-UI-315: Harmonized Visual & UX Architecture for Climb, Segment, and Route Detail Bottom Sheets via MapDetailLayout [Verified]
- **Tests**: `TST-UI-275`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SegmentDetailSheet.kt`, `RouteDetailSheet.kt`, `ClimbDetailSheet.kt`, `SegmentOnMapScreen.kt`, `RouteOnMapScreen.kt`
- **Rationale**: The system SHALL eliminate divergent card-stack implementations in modal detail sheets and enforce unified presentation and code reuse across segments, routes, and climbs by directly reusing `SegmentOnMapScreen` and `RouteOnMapScreen` via `MapDetailLayout` within modal bottom sheet containers (ATT-2860, amending `REQ-UI-300`, `REQ-UI-303`, and `REQ-UI-316`):
1. *Segment Detail Sheet Unification (`SegmentDetailSheet.kt`)*:
• `SegmentDetailSheet` SHALL directly host `SegmentOnMapScreen` within a non-scrolling modal bottom sheet container.
• It SHALL render the canonical `SegmentHeader` (sport icon, title, category chip, PR badge, dismiss close action), `HorizontalDivider`, and `SegmentDetails` (distance, Strava branding, grade, elevation gain, min/max altitude).
• It SHALL render `ATrainingTrackerMap` with bounding box focus (`MapZoomFocus.EXPLICIT_BOUNDS`) and interactive elevation profile via `MapDetailLayout`.
• It SHALL present `SegmentRoutesSection` in `analyticsContent` when containing routes exist.
• Obsolete standalone card composables (`SegmentDetailMetricsCard`, `SegmentDetailMapCard`, `SegmentDetailElevationProfileCard`) SHALL be completely removed.
2. *Route Detail Sheet Unification (`RouteDetailSheet.kt`)*:
• `RouteDetailSheet` SHALL follow this unified architecture by directly hosting `RouteOnMapScreen` within a non-scrolling modal bottom sheet container.
• It SHALL present `RouteHeader`, `RouteDetails`, and `MapDetailLayout` with route polyline, start/stop pins, and elevation profile.
• Obsolete standalone card composables (`RouteDetailMetricsCard`, `RouteDetailMapCard`, `RouteDetailElevationProfileCard`) SHALL be completely removed.
3. *Climb Detail Sheet Alignment (`ClimbDetailSheet.kt`)*:
• `ClimbDetailSheet` SHALL align with this unified layout pattern, presenting structured `ClimbHeader` and `ClimbDetails` rows over `MapDetailLayout` with category-colored climb polyline and gradient elevation profile.
4. *Dismissal & State Isolation*:
• Dismissing a modal detail sheet via close button, drag handle gesture, scrim tap, or system Back press SHALL cleanly dismiss the modal overlay and return to the parent screen with scroll position, route selection, and map zoom state 100% intact.
5. *100% 9-Language Localization Parity*:
• All string resources utilized by detail sheets and headers SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-300` (*Climb Detail Sheet*), `REQ-UI-303` (*Segment Detail Sheet*), and `REQ-UI-316` (*Route Detail Sheet*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2860`, Sprint `2026-41.5` -> `2026-41.6`, target release `V4.9.40`, Epic `ATT-355` (*Good and consistent UI*).
3. *Root Reason for Existing Formulation*: In sprint 2026-41.5, the initial implementation attempted to polish the 3-card stack within `AppModalBottomSheet`. The user rejected this during Sprint Review because having a separate 3-card column layout created a jarring visual and functional disconnect from what athletes see when tapping entities on the map (`SegmentOnMapScreen` / `RouteOnMapScreen` with `MapDetailLayout`). The user explicitly demanded: "The segment popup must look identical to the one that pops up in the map. We should use the same code here. The route popup should follow this layout."
4. *Preservation of Core Invariants*: Bounding box calculation, entity dismiss gestures, sport type filtering, route index counters, and 100% test pass rate are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete tapping a segment in `RouteOnMapScreen`,
• *When* `SegmentDetailSheet` opens,
• *Then* it displays the exact layout and code as `SegmentOnMapScreen` via `MapDetailLayout`.
• *Given* an athlete tapping a route in `SegmentRoutesSection`,
• *When* `RouteDetailSheet` opens,
• *Then* it displays the exact layout and code as `RouteOnMapScreen` via `MapDetailLayout`.
• *Given* modal detail sheets open,
• *When* interacting with the map or scrubbing elevation,
• *Then* gestures operate smoothly without scroll conflicts.
• *Given* any detail sheet,
• *When* dismissed,
• *Then* the calling screen state is 100% preserved.

**Invariants**: MapDetailLayout split-pane math, zoom focus, entity bounds, and 100% test pass rate MUST NOT be broken.

### REQ-UI-316: Dedicated Route Detail Bottom Sheet from Matched Segment Containing Routes View [Verified]
- **Tests**: `TST-UI-276`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `StarredSegmentsScreen.kt`, `RouteDetailSheet.kt`, `SegmentRoutesSection.kt`
- **Rationale**: The system SHALL provide a modal `RouteDetailSheet` bottom sheet overlay (`AppModalBottomSheet`) when an athlete taps any containing route card inside `SegmentRoutesSection` (within `SegmentOnMapScreen`), presenting the containing route's telemetry, map, and elevation profile without navigating away from the segment inspection flow (ATT-2861):
1. *Modal Overlay Architecture (`StarredSegmentsScreen.kt`, `RouteDetailSheet.kt`)*:
• Tapping a containing route item in `SegmentRoutesSection` SHALL set `inspectedRouteId` without unmounting `SegmentOnMapScreen`.
• When `inspectedRouteId != null`, the system SHALL display `RouteDetailSheet(routeWithPath = inspectedRoute, onDismiss = { inspectedRouteId = null })` as an `AppModalBottomSheet` overlaying the segment view.
2. *Header Bar & Sport Identification*:
• The sheet SHALL render a header bar with the route name, sport type icon indicator (`SportItem` / sport drawable), and a dedicated close button.
3. *Key Route Metrics HUD*:
• The sheet SHALL render a metrics card using `RoundedCornerShape(16.dp)` and `surfaceContainer` styling, displaying total distance, total elevation gain, and altitude range (min/max elevation) with vector iconography.
4. *Focused Map Viewport*:
• The sheet SHALL render an embedded map (`ATrainingTrackerMap`) configured with `zoomFocus = MapZoomFocus.EXPLICIT_BOUNDS` enclosing the route path, rendering the route polyline in `TTColor.RouteSelected` with start and finish markers.
5. *Route Elevation Profile*:
• The sheet SHALL render the route's elevation profile card with min/max elevation indicators and total distance axis.
6. *Non-Destructive Back & Dismiss Behavior*:
• Dismissing the sheet (via drag gesture, scrim click, close icon, or system Back press) SHALL dismiss the modal overlay and return directly to the intact `SegmentOnMapScreen`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Net-new requirement extending `REQ-UI-298` (*Show saved routes containing segment* / `ATT-2585`).
2. *Historical Origin & Commit Trace*: Sprint Review 2026-41.4, Ticket `ATT-2861`, target release `V4.9.40`, Epic `ATT-2582` (*Segments: Live Tracking, Exploration & Route Integration*).
3. *Root Reason for Existing Formulation*: In ATT-2585, clicking a route card replaced the segment screen with `RouteOnMapScreen`, forcing the athlete to leave the segment view.
4. *Preservation of Core Invariants*: Full route editing and primary route list navigation in `RouteTabbedScreen` are completely preserved and unaffected.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete inspecting a segment on `SegmentOnMapScreen` that has containing routes,
• *When* tapping a route card in `SegmentRoutesSection`,
• *Then* `RouteDetailSheet` SHALL open as a modal bottom sheet over `SegmentOnMapScreen`.
• *Given* `RouteDetailSheet`,
• *When* inspecting the content,
• *Then* header displays route name and sport icon, metrics card displays distance and elevation gain in 16.dp cards, map card displays route bounds, and elevation profile renders route altitude.
• *Given* `RouteDetailSheet` open,
• *When* the user taps close, drags down, or presses Back,
• *Then* the sheet dismisses and the athlete remains on the segment map screen.

**Invariants**: Segment state persistence, back navigation integrity, 16.dp Material 3 card shapes, and 100% test pass rate MUST NOT be broken.

### REQ-UI-317: Geodesic Polyline Orthogonal Projection & Interval Matching for Route-Segment Correlation [Verified]
- **Tests**: `TST-UI-277`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
4. *Directional Bearing Alignment on Projected Line Segments*:
• Forward bearing alignment SHALL be calculated using the travel bearing along the route's nearest polyline segment $(P_i, P_{i+1})$ containing the projection point, compared to the initial segment traversal bearing.
• The angular difference SHALL satisfy $\Delta \theta \le 45.0^\circ$ (`MAX_BEARING_DELTA_DEGREES = 45.0`) with positive vector dot product ($\cos(\Delta \theta) > 0$).
5. *Preservation of System Invariants & Zero Regression*:
• Genuine reverse traversals ($\Delta \theta > 90^\circ$), perpendicular crossing corridors, conflicting sport types, diverging finishes, and horseshoe midpoints MUST remain strictly rejected.
• Zero Android UI dependencies in `RouteSegmentMatcher.kt` (pure functional evaluation).
• 100% clean-room unit test pass rate across `RouteSegmentMatcherTest.kt` and the full test suite.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines and amends `REQ-UI-302` (*Route Starred Segments Spatial Overlap Matching, Chronological Route Breakdown & Map Synergy*, Clause 1) and `REQ-UI-305` (*Inverted Route-Segment Spatial Association*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2583` (Sprint `2026-41.3`) and Ticket `ATT-2585` (Sprint `2026-41.4`).
3. *Root Reason for Existing Formulation*: `RouteSegmentMatcher` was originally implemented with a simple discrete vertex search loop, validated against synthetic colinear test points with 0 m offset. On real-world outdoor routes (such as "Nur ein Test" and segment "Schönaich Welle 1"), route vertices are spaced 30–80 m apart, causing points on the road to fail vertex distance checks, and out-and-back routes inverted the global min/max indices.
4. *Preservation of Core Invariants*: Sport filtering, directional bearing validation, reverse traversal rejection, and 100% unit test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a route with vertices spaced 50 m apart and a candidate segment whose start and end coordinates fall between route vertices along the road within 35 m cross-track distance,
• *When* evaluating with `RouteSegmentMatcher.matchSegments` or `findRoutesContainingSegment`,
• *Then* the segment SHALL be successfully matched with accurate start and end route distances.
• *Given* an out-and-back route that traverses a segment in the forward direction on the way out and returns in the reverse direction on the way back,
• *When* matching segments,
• *Then* the forward traversal interval SHALL be matched.
• *Given* a segment traveling in reverse or crossing perpendicular to the route,
• *When* evaluated,
• *Then* the segment SHALL remain rejected.

**Invariants**: Pure mathematical evaluation, reverse direction rejection, and 100% unit test pass rate MUST NOT be broken.
- **Implementation**: (D_{\text{end}} - D_{\text{start}}) - \text{intrinsicDistance}
- **Rationale**: The system SHALL evaluate candidate route-segment spatial associations in `RouteSegmentMatcher.kt` using geodesic orthogonal projection onto polyline line segments and interval-based forward traversal tracking (ATT-2862, amending `REQ-UI-302` and `REQ-UI-305`):
1. *Orthogonal Polyline Segment Projection*:
• Rather than testing geodesic distance strictly against isolated route vertices, the system SHALL calculate the orthogonal projection of segment start, end, and sampled midpoint coordinates onto each line segment $(P_i, P_{i+1})$ of the candidate route polyline.
• For each projection, the system SHALL determine the projected coordinate $Q \in (P_i, P_{i+1})$, the orthogonal cross-track distance $d_{\text{cross-track}}$, and the exact interpolated cumulative route distance $D_{\text{along}} = D_i + t \cdot (D_{i+1} - D_i)$ where $t \in [0.0, 1.0]$.
2. *Corridor Distance Tolerance & Real-World GPS Adaptability*:
• A segment terminal point (start/end) SHALL be considered within corridor proximity if its cross-track distance to the route polyline is $\le 35.0\text{ m}$ (`CORRIDOR_TOLERANCE_METERS = 35.0`).
• For multi-point segments ($\ge 3$ points), the midpoint cross-track distance to the route polyline between start and finish projections SHALL be $\le 60.0\text{ m}$ (`MIDPOINT_TOLERANCE_METERS = 60.0`).
3. *Interval-Based Forward Traversal on Circuit, Loop & Out-and-Back Routes*:
• To prevent false rejections caused by independent global minimum selection on out-and-back tracks or figure-8 routes, the system SHALL evaluate localized forward candidate intervals $(proj_{\text{start}}, proj_{\text{end}})$ satisfying:
a. $proj_{\text{end}}.distanceAlongRoute > proj_{\text{start}}.distanceAlongRoute$.
b. Both start and end cross-track distances $\le 35.0\text{ m}$.
c. Span distance consistency: $

### REQ-UI-318: Route Map Segment Polyline Suppression on Layer Unselection & Individual Item Hiding [Verified]
- **Tests**: `TST-UI-278`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
5. *Preservation of System Invariants*:
• Selected route polyline (`TTColor.RouteSelected`), Start (`control_start`) and End (`control_stop`) markers, and Climbs/Waypoints layer controls MUST NOT be affected.
• Non-segment paths in `backgroundPaths` (e.g. tracks or routes, if present) MUST remain visible.
• 100% test pass rate across the unit test suite.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines and amends `REQ-UI-308` (*Route Map Overlay Layer Visibility Controls, Layers Menu, and Item-Level Breakdown Toggles with Permanent Route Line Anchoring*, Clause 3).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2763`, Sprint `2026-41.4`, Epic `ATT-66` (*Improve Routes*).
3. *Root Reason for Existing Formulation*: In `ATT-2763`, layer filtering was applied strictly to `matchedSegments` in `segments(...)`. Because `RoutesScreen.kt` also supplies segments of the same sport type via `backgroundPaths` (which were rendered unconditionally via `contextualPaths(backgroundPaths)`), unselecting "Segments" in the layers menu left background segment polylines visible on the map canvas.
4. *Preservation of Core Invariants*: Permanent route line anchoring, navigation pins, climbs/waypoints toggling, and 100% test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a route on `RouteOnMapScreen` with segment polylines visible on the map,
• *When* the athlete taps the Layers button and unchecks "Segments",
• *Then* ALL segment polylines (matched segments and background segments) SHALL immediately disappear from the map canvas.
• *Given* a route with segment polylines and an individual segment hidden in the segments breakdown list (`hiddenSegmentIds`),
• *When* rendered on the map,
• *Then* that specific segment's polyline SHALL NOT be rendered in either the matched segments layer or the background paths layer.
• *Given* a route with background segments but no matched segments,
• *When* the athlete opens the Layers dropdown menu,
• *Then* the "Segments" checkbox SHALL be enabled and interactive.

**Invariants**: Selected route line, Start/End markers, Climbs and Waypoints toggles, and 100% unit test pass rate MUST NOT be broken.
- **Rationale**: The system SHALL suppress ALL segment polylines (including both matched route segments and background contextual segment paths) from the map canvas when the Segments layer is unselected in the route map layers menu, filter out individual hidden segments from background paths, and accurately reflect Segments layer availability (ATT-2863, amending `REQ-UI-308`):
1. *Full Map Canvas Segment Polyline Suppression (`RouteOnMapScreen.kt`)*:
• When the Segments overlay layer is unchecked (`RouteOverlayLayer.SEGMENTS !in enabledOverlayLayers`), the system SHALL suppress ALL segment polylines from `mapContent`.
• Both `segments(...)` (matched route segments) and any `MapSegment` instances contained in `backgroundPaths` passed to `contextualPaths(...)` SHALL be suppressed from the map canvas.
2. *Individual Breakdown Card Hidden Segment Filtering in Background Paths*:
• When an individual segment is toggled off (`hiddenSegmentIds: Set<Long>`), any `MapSegment` in `backgroundPaths` whose `stravaId` matches an ID in `hiddenSegmentIds` SHALL be excluded from `contextualPaths(...)`, preventing hidden segments from leaking back onto the map as background polylines.
3. *De-duplication of Matched and Background Segments*:
• When the Segments layer is enabled (`RouteOverlayLayer.SEGMENTS in enabledOverlayLayers`), any segment that is already rendered as a matched segment via `segments(...)` SHALL be excluded from `contextualPaths(backgroundPaths)` so that matched segments are rendered exclusively with interactive click handlers and live segment styling.
4. *Segments Layer Menu Availability Refinement*:
• In the route map layers dropdown menu, the Segments layer entry SHALL be enabled if `matchedSegments.isNotEmpty()

### REQ-UI-319: Multi-Tier Route Map Polyline Layering: Solid Climb Overlay and Dashed Segment Overlay Synergy [Verified]
- **Tests**: `TST-UI-279`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapModels.kt`, `MapContentScope.kt`, `MapLayers.kt`, `RouteOnMapScreen.kt`
- **Rationale**: The system SHALL establish an unambiguous multi-tier polyline layering hierarchy on the route map canvas in `RouteOnMapScreen.kt`, rendering climbs as a continuous solid polyline above both layers of the route line and rendering segments as a dashed polyline above the climb polyline with transparent gaps (ATT-2864, amending `REQ-UI-298`, `REQ-UI-302`, and `REQ-UI-319`):
1. *Strict Z-Ordering Hierarchy Across Dual-Layer Routes, Climbs, and Segments (`MapVisualization`, `MapStyle`, `MapModels.kt`, `MapContentScope.kt`)*:
• Selected Route Base Line: `zIndex = MapVisualization.ROUTE_BASE_Z_INDEX = 20.0f` (or `24.0f` for active navigation), rendered as a solid royal blue ribbon (`TTColor.RouteSelected`, width = 10f).
• Selected Route Patterned Overlay: `overlayZIndex = MapVisualization.ROUTE_OVERLAY_Z_INDEX = 22.0f` (or `26.0f` for active navigation), rendered strictly below the climb tier ($22.0\text{f} < 28.0\text{f}$).
• Climb Span Overlay: `zIndex = MapVisualization.CLIMB_Z_INDEX = 28.0f`, rendered as a continuous solid polyline (width = 10f, `jointType = JointType.ROUND`) in its UCI category color (`getClimbCategoryColors`), positioned strictly above both route layers.
• Segment Overlay: `zIndex = MapVisualization.SEGMENT_Z_INDEX = 30.0f`, rendered as a dashed polyline above the climb polyline.
2. *Dashed Segment Overlay Pattern & Transparent Gap Synergy*:
• `MapVisualization` SHALL declare `SEGMENT_DASH_LENGTH = 20f` and `SEGMENT_GAP_LENGTH = 15f`.
• `MapSegment` SHALL declare property `val isDashed: Boolean = false`. When `isDashed == true`, `pattern` SHALL evaluate to `listOf(Dash(SEGMENT_DASH_LENGTH), Gap(SEGMENT_GAP_LENGTH))`.
• `XRayPolyline` in `MapLayers.kt` SHALL support `hasSolidBase: Boolean = !isDashedSegment`, ensuring that when `isDashed == true`, no solid orange base polyline is drawn beneath the dashes. The gaps between dashes SHALL be completely transparent, allowing the underlying solid climb category polyline (or base route ribbon) to be clearly visible without occlusion.
3. *Route On Map Screen Synergy (`RouteOnMapScreen.kt`)*:
• In `RouteOnMapScreen.kt`, all matched segments rendered via `segments(...)` SHALL set `isDashed = true`.
• All background segments rendered via `contextualPaths(...)` SHALL be mapped with `isDashed = true`.
• Standalone segment inspection in `SegmentOnMapScreen.kt` SHALL preserve solid rendering (`isDashed = false`).
4. *Preservation of System Invariants*:
• Selected route line (`TTColor.RouteSelected`) remains permanently anchored at the base (`REQ-UI-308.1`).
• Start (`control_start`) and End (`control_stop`) navigation markers remain visible at all times (`REQ-UI-308.3`).
• UCI climb category colors, Strava Orange, and 100% test pass rate MUST NOT be broken.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-319` (*Multi-Tier Route Map Polyline Layering: Solid Climb Overlay and Dashed Segment Overlay Synergy*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2864`, Sprint `2026-41.5` -> `2026-41.6`, target release `V4.9.40`, Epic `ATT-66` (*Improve Routes*).
3. *Root Reason for Existing Formulation*: Originally in `MapVisualization`, `ROUTE_OVERLAY_Z_INDEX` was set to `40.0f` to ensure the route pattern was visible above legacy elements. However, when climbs (28.0f) and segments (30.0f) were added, the route overlay's z-index at 40.0f remained higher than both, causing the route's upper layer to render over climbs and segments.
4. *Preservation of Core Invariants*: Lowering `ROUTE_OVERLAY_Z_INDEX` to 22.0f and `ROUTE_ACTIVE_OVERLAY_Z_INDEX` to 26.0f groups the route's layers together below climbs (28.0f) and segments (30.0f), while preserving track overlays (50.0f), markers (>= 50.0f), and user location (100.0f) in their correct positions.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a route with recognized climbs on `RouteOnMapScreen`,
• *When* inspecting the map canvas,
• *Then* each climb span SHALL render as a continuous solid polyline in its category color positioned strictly above both route layers ($z = 28.0\text{f} > 22.0\text{f} > 20.0\text{f}$).
• *Given* a route where a Strava segment coincides with a climb span,
• *When* inspecting the coincident section,
• *Then* the segment SHALL render as a dashed Strava Orange polyline positioned above the climb line ($z = 30.0\text{f} > 28.0\text{f}$),
• *And* the solid climb category color SHALL remain clearly visible through the transparent gaps of the segment dashes.
• *Given* an athlete inspecting an isolated segment on `SegmentOnMapScreen`,
• *When* the map is displayed,
• *Then* the segment SHALL render as a solid polyline (`isDashed = false`).

**Invariants**: Route line anchor, start/end markers, climb colors, and 100% unit test pass rate MUST NOT be broken.

### REQ-UI-320: Preservation of Authentic Sport Type Icon Colors in Quick Route Selector [Verified]
- **Tests**: `TST-UI-280`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RouteSelectorSheet.kt`, `RouteSelectorSheetTest.kt`
- **Rationale**: The system SHALL preserve the authentic multi-color vector representation of sport type icons in `RouteSelectorSheet.kt` (`RouteCard`) by rendering the icon painter with `tint = Color.Unspecified`, eliminating monochrome theme tinting (ATT-2865, amending `REQ-UI-310`):
1. *Bypass Monochrome Theme Color Filtering (`RouteCard`)*:
• The `Icon` composable rendering `route.summary.bSportType.iconResId` in `RouteCard` SHALL specify `tint = Color.Unspecified`.
• Monochrome tinting (`if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant`) SHALL be eliminated, ensuring multi-color sport vector assets (`bsport_bike`, `bsport_run`, `bsport_other`) render with their intrinsic full-fidelity XML path colors regardless of whether the route card is in active or inactive state.
2. *Preservation of System Invariants*:
• Active route indication via dedicated `"ACTIVE"` chip (`labelSmall`, `onPrimary` text over `primary` background) and `secondaryContainer` card container color MUST remain strictly intact.
• Localized accessibility description (`contentDescription = stringResource(id = route.summary.bSportType.stringResId)`) MUST be preserved.
• Sport icon dimensions (`24.dp`) and route title/metric typography MUST remain unchanged.
• 100% test pass rate across all route selector unit and contract tests.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines and amends `REQ-UI-310` (*Sport-Specific Visual Representation & Active Navigation Indication in Quick Route Selector*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2668` (Sprint 2026-41.4), following precedent `ATT-2856` (`SportTypeSelectorKt.SportItem`).
3. *Root Reason for Existing Formulation*: Material Design 3 icon components default to applying monochrome tinting (`onSurfaceVariant` / `primary`). For custom multi-color sports vector drawables, monochrome tinting flattened all graphic details into a solid silhouette.
4. *Preservation of Core Invariants*: Active badge chip, card colors, text layout, and 100% test pass rate remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening `RouteSelectorModalBottomSheet` on `TrackingTabsScreen`,
• *When* observing route cards for cycling, running, or other sports,
• *Then* each route card's sport type icon SHALL render in its authentic original multi-color representation (`tint = Color.Unspecified`),
• *And* active routes SHALL continue to display the `"ACTIVE"` badge chip with primary background and secondaryContainer card background.

**Invariants**: Active badge chip, card container styling, and 100% unit test pass rate MUST NOT be broken.

### REQ-UI-321: Top-Level Spatial Overlay & Tab Navigation Hints Gating for In-Ride Fork Route Decision Prompts [Verified]
- **Tests**: `TST-UI-281`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorGridScreen.kt`
- **Rationale**: The system SHALL eliminate cockpit telemetry tile displacement during in-ride fork route alerts and enforce per-tab navigation hints configuration in `SensorGridScreen.kt` by floating `ForkDecisionCard` as a top-level spatial overlay and gating it strictly behind `state.showNavigationHints` (ATT-2874, amending `REQ-MAP-031`):
1. *Top-Level Spatial Box Layering (`SensorGridScreen.kt`)*:
• In tracking and preview modes, `SensorGridScreen` SHALL compose the stationary main screen content (sensor grid, map, elevation profile, and banners) inside a base layout container.
• `ForkDecisionCard` SHALL be rendered within a top-level non-displacing overlay anchored at `Alignment.TopCenter`.
• The presence, appearance, animation expansion, or height changes of `ForkDecisionCard` SHALL NOT alter or displace the layout coordinates, vertical position, or scroll offset of the underlying sensor grid tiles.
2. *Strict Per-Tab Navigation Gating*:
• `ForkDecisionCard` SHALL be conditionally gated strictly behind `state.showNavigationHints`.
• When `state.showNavigationHints == false` on the active tracking tab, `ForkDecisionCard` SHALL be completely excluded from the Compose composition tree, ensuring 0% touch interception and complete suppression of fork decision alerts on navigation-free tabs.
3. *Glanceable Semi-Transparency & Touch Event Pass-Through*:
• `ForkDecisionCard` SHALL continue to apply `tuningConfig.navigationCueTransparency` per Design Guidelines Section 5.7.
• When collapsed or dismissed (`decisionState == null`), the overlay wrapper SHALL NOT intercept touch events or obstruct user interactions with underlying sensor tiles.
4. *Preservation of System Invariants*:
• Manual route selection via `forkNavRepo.selectRouteManually(routeId)` and prompt dismissal via `forkNavRepo.dismissPrompt()` MUST function identically.
• Autonomous route snapping ($D_{\text{past}} \ge 50\text{m}$) and 100% test pass rate across the full test suite MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines and amends `REQ-MAP-031` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts*, Clause 3).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1955` (Sprint 2026-40.16).
3. *Root Reason for Existing Formulation*: In ATT-1955, `ForkDecisionCard` was added as an in-flow sequential composable within `Column`, which caused vertical layout displacement whenever triggered. Additionally, tab-level `showNavigationHints` gating was omitted.
4. *Preservation of Core Invariants*: Bounding-box pre-filtering (ATT-2873), candidate route matching, and all autonomous fork binding logic remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout tracking tab with `showNavigationHints = false`,
• *When* approaching a fork in the road ($D_{\text{fork}} \le 300\text{m}$),
• *Then* `ForkDecisionCard` SHALL NOT be displayed or composed on that tab.
• *Given* an active workout tracking tab with `showNavigationHints = true`,
• *When* approaching a fork in the road ($D_{\text{fork}} \le 300\text{m}$),
• *Then* `ForkDecisionCard` SHALL float at `Alignment.TopCenter` on top of the screen content as an overlay,
• *And* the underlying sensor tiles SHALL remain 100% stationary without any layout shift.
• *Given* tracking without an active fork prompt (`decisionState == null`),
• *When* tapping or scrolling over the upper screen area,
• *Then* touch events SHALL pass through directly to underlying sensor tiles.

**Invariants**: Telemetry stationarity, semi-transparency, touch pass-through, and 100% unit test pass rate MUST NOT be broken.

### REQ-UI-322: General Map Layer Visibility Controls & Dynamic Decluttering Menu [`TST-UI-282`] [Verified]
- **Tests**: `TST-UI-282`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapScreenWithTrack.kt`, `MapFragmentWithTrackViewModel.kt`, `GeneralMapLayer.kt`
- **Rationale**: The system SHALL provide dynamic visibility controls for general map spatial layers via a floating Layers button and Material 3 dropdown menu in `MapScreenWithTrack.kt`, conditional layer composition in `ATrainingTrackerMap`, preference persistence in `MapFragmentWithTrackViewModel`, strict isolation of favorite route clusters (Lieblingsstrecken) from the Cockpit map in `SensorGridScreen.kt`, and full expandable detail sheet with companion stats hydration on the general map (ATT-2931, ATT-4847, ATT-5197, ATT-5196, amending and complementing `REQ-UI-180`, `REQ-UI-308`, and `REQ-MAP-051`):
1. *Layer Model (`GeneralMapLayer`)*:
• The system SHALL define `enum class GeneralMapLayer` in `com.atrainingtracker.trainingtracker.ui.map` declaring five layer types: `ROUTES`, `SEGMENTS`, `KNOWN_LOCATIONS`, `TRACK`, and `CLUSTERS`.
• `GeneralMapLayer` SHALL define `DEFAULT_ENABLED = setOf(ROUTES, SEGMENTS, KNOWN_LOCATIONS, TRACK)`, excluding `CLUSTERS` so favorite route clusters are hidden by default.
2. *ViewModel State & Persistence (`MapFragmentWithTrackViewModel.kt`)*:
• `MapFragmentWithTrackViewModel` SHALL expose `val enabledLayers: StateFlow<Set<GeneralMapLayer>>`.
• On initial instantiation, `MapFragmentWithTrackViewModel` SHALL restore enabled layers from `SharedPreferences` (`PREF_GENERAL_MAP_ENABLED_LAYERS`).
• If no preferences are stored, the system SHALL default to enabling the standard primary layers (`GeneralMapLayer.DEFAULT_ENABLED`), strictly excluding `CLUSTERS`.
• `MapFragmentWithTrackViewModel` SHALL provide `fun toggleLayer(layer: GeneralMapLayer)`.
• Toggling a layer SHALL immediately emit the updated set in `enabledLayers` and commit the updated string set to `SharedPreferences`.
• `MapFragmentWithTrackViewModel` SHALL conditionally gate `mapClustersFlow` on `GeneralMapLayer.CLUSTERS in enabledLayers`: when disabled, `mapClustersFlow` SHALL emit `emptyList()` immediately without executing polyline decoding.
• When `GeneralMapLayer.CLUSTERS` is enabled, `MapFragmentWithTrackViewModel` SHALL combine `WorkoutClusterRepository.allClusters` and `WorkoutClusterRepository.clusterStats`, decode and memoize representative polylines into `MapCluster` instances on `Dispatchers.Default` with referential stability and populated `stats: WorkoutClusterStats?`, and expose them via `MapFragmentUIState.clusters`.
3. *Ergonomic Floating Layers Button & Dropdown Menu (`MapScreenWithTrack.kt`)*:
• `MapScreenWithTrack.kt` SHALL wrap `ATrainingTrackerMap` in a `Box(modifier = Modifier.fillMaxSize())` inside the `BottomSheetScaffold` body.
• `MapScreenWithTrack.kt` SHALL render a floating circular button at `Alignment.TopEnd` with `.statusBarsPadding().padding(top = 16.dp, end = 16.dp)`.
• The button SHALL be a `Surface` with `size = 44.dp`, `shape = CircleShape`, `color = MaterialTheme.colorScheme.surface.copy(alpha = TTAlpha.Overlay)`, `shadowElevation = 6.dp`, and `tonalElevation = 2.dp`.
• The button SHALL contain an `Icon` with `Icons.Default.Layers` (`size = 22.dp`), whose tint dynamically resolves to `MaterialTheme.colorScheme.primary` when any layer is disabled (`enabledLayers.size < GeneralMapLayer.entries.size`) or `MaterialTheme.colorScheme.onSurface` when all layers are enabled.
• Clicking the button SHALL open a Material 3 `DropdownMenu` styled consistently with `TrackOnMapScreen.kt` and `RouteOnMapScreen.kt`.
• The dropdown menu SHALL render five `DropdownMenuItem` entries with checkbox, color legend swatch (`12.dp`, `RoundedCornerShape(2.dp)`: Routes = `TTColor.RouteSelected`, Segments = `TTColor.StravaOrange`, Locations = `MaterialTheme.colorScheme.tertiary`, Track = `TrackType.BEST.color`, Clusters = `TTColor.Cluster`), and localized text label.
4. *Conditional Map Canvas Layer Composition & Harmonized Expandable Cluster Bottom Sheet*:
• In `ATrainingTrackerMap`'s content scope: `knownLocations()`, `segments()`, `routes()`, `clusters()`, and `markers()` + `liveTrack()` SHALL only be composed when their corresponding `GeneralMapLayer` is present in `enabledLayers`.
• Tapping a cluster path SHALL open `ClusterOnMapSheet` using `MapDetailLayout(useStatusBarsPadding = false, showMap = true, onHeaderHeightMeasured = { measuredClusterHeaderHeight = it })`, hosting `MinimumDragHandle()` at the top (Section 5.15, Rule 68).
• The peek header of `ClusterOnMapSheet` SHALL reuse `WorkoutClusterIdentityRow` and `WorkoutClusterMetadataBlock` from `WorkoutClusterComponents.kt`, matching the Lieblingsstrecken list items 1:1 with start location, sport subtitle, gear, distance, elevation, PR, hit count, and relative recency.
• In `ClusterOnMapSheet`, `MapDetailLayout` SHALL NOT render duplicate cluster summary statistics in `metadataContent` (omitting `WorkoutClusterDetailDashboard` to eliminate redundant stacking, ATT-5279).
• When dragged upward into the expanded state, `ClusterOnMapSheet` SHALL display the detailed entity map in the lower section (`showMap = true`) rendered with cluster bounds (`MapZoomFocus.EXPLICIT_BOUNDS`), heatmap paths, linked route, workout member traces, and fingerprint markers (ATT-5279, matching `RouteOnMapScreen` and `SegmentOnMapScreen`).
• When peeked (collapsed), only the header slot (`WorkoutClusterIdentityRow` + `WorkoutClusterMetadataBlock`) is visible above the navigation bar (`measuredClusterHeaderHeight + navBarHeight`), hiding the lower map below the fold.
• `ClusterOnMapSheet` SHALL NOT tint text or sport/distance icons with `TTColor.Cluster` (purple/lila); all popup typography and metric icons SHALL resolve via standard Material 3 color tokens (`onSurface`, `onSurfaceVariant`, `primary`) and `TTColor.Gold` for PR trophy.
• The cluster path polyline stroke color rendered on `ATrainingTrackerMap` canvas SHALL strictly remain `TTColor.Cluster` with zIndex `12f`.
• Sheet peek height for `selectedClusterId != null` in `MapScreenWithTrack.kt` SHALL dynamically adapt based on `measuredClusterHeaderHeight` (with `navigationBarHeight` / `BottomSheetDesign.PeekHeightWorkout` fallback) to sit flush above navigation bars.
5. *Cockpit Live Tracking Map Cluster Isolation & Companion Stats Hydration (`TrackingViewModel.kt`, `SensorGridScreen.kt`, `WorkoutClusterRepository.kt`)*:
• Historical discovery overlays (Lieblingsstrecken / `CLUSTERS`) belong strictly to the general map and SHALL NEVER be computed or displayed on the live tracking cockpit map (`SensorGridScreen.kt`, Rule 68).
• `SensorGridScreen.kt` (`TrackingMapContainer`) SHALL NOT compose `clusters()` on `ATrainingTrackerMap`.
• `TrackingViewModel` SHALL NOT subscribe to `WorkoutClusterRepository` or observe cluster layer state, eliminating unnecessary background collection and polyline decoding overhead during live tracking.
• `WorkoutClusterRepository` SHALL provide a fast, non-blocking asynchronous loading pathway (`loadAllClustersLightweight()`) from the SQLite `RouteClusters` table on `Dispatchers.IO` for the general map (`MapFragmentWithTrackViewModel`); if `_clusterStats.value.isEmpty()`, it SHALL also populate `_clusterStats.value = summariesManager.getWorkoutClusterStatsForAllClusters()` so opening the Map first immediately renders full metadata (Section 5.8, Rule 68).
6. *100% 9-Language Localization Parity*:
• String resources `map_layers`, `map_layer_routes`, `map_layer_segments`, `map_layer_locations`, `map_layer_track`, and `map_layer_clusters` SHALL be externalized and translated across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
7. *Preservation of System Invariants*:
• Entity selection bottom sheets, background GPS tracking, start marker detection, and 100% test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-322`), amending and complementing `REQ-UI-180` (*Display Favorite Locations on Central Navigation Map*) and `REQ-UI-308` (*Route Map Overlay Layer Visibility Controls*). Amended under `ATT-4847` to include `CLUSTERS` layer and Cockpit live tracking map integration. Refined under `ATT-5075` (Sprint 2026-45.1) to harmonize `ClusterOnMapSheet` popup look and information density with `WorkoutClusterComponents` list items, enrich `MapCluster` with `WorkoutClusterStats`, and eliminate purple text tinting. Refined under `ATT-5074` (Sprint 2026-45.1) to disable `GeneralMapLayer.CLUSTERS` by default (`DEFAULT_ENABLED`), gate ViewModel polyline decoding, offload decoding to `Dispatchers.Default`, and decouple map cluster loading from heavy analytical aggregation in `WorkoutClusterRepository`. Amended under `ATT-5197` (Sprint 2026-45.2) to excise clusters completely from the live tracking cockpit map (`SensorGridScreen.kt` and `TrackingViewModel.kt`). Amended under `ATT-5196` (Sprint 2026-45.2) to mandate `MapDetailLayout` with `MinimumDragHandle()`, dynamic peek height, list component reuse (`WorkoutClusterIdentityRow`/`WorkoutClusterMetadataBlock`), and companion `_clusterStats` hydration in `loadAllClustersLightweight()`. Amended under `ATT-5279` (Sprint 2026-45.3) to remove duplicate metadata from `ClusterOnMapSheet` (`WorkoutClusterDetailDashboard` omitted from `metadataContent`) and set `showMap = true` with lower cluster heatmap/member map composition matching `RouteOnMapScreen` and `SegmentOnMapScreen`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-1449` (Sprint 2026-40.4), Ticket `ATT-2763` (Sprint 2026-41.4), Ticket `ATT-4847` (Sprint 2026-44.2), Ticket `ATT-5075` (Sprint 2026-45.1), Ticket `ATT-5074` (Sprint 2026-45.1), Ticket `ATT-5197` (Sprint 2026-45.2), Ticket `ATT-5196` (Sprint 2026-45.2), and Ticket `ATT-5279` (Sprint 2026-45.3).
3. *Root Reason for Existing Formulation*: In ATT-5196, `ClusterOnMapSheet` was migrated to `MapDetailLayout`, but `showMap = false` was set and `WorkoutClusterDetailDashboard` was slotted into `metadataContent`. In `MapDetailLayout.kt`, `metadataContent` is rendered inside the top header Column, duplicating metrics already shown in `WorkoutClusterMetadataBlock`. Furthermore, setting `showMap = false` left the bottom sheet empty when expanded. Removing redundant `metadataContent` and setting `showMap = true` restores 1:1 parity with `RouteOnMapScreen` and `SegmentOnMapScreen` (Section 5.15 / Rule 68).
4. *Preservation of Core Invariants*: List component visual identity (`WorkoutClusterIdentityRow` + `WorkoutClusterMetadataBlock`), `MinimumDragHandle()`, dynamic peek height measurement flush to navigation bar, off-main decoding, and non-purple styling are strictly maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a fresh install or unconfigured map layer preferences,
• *When* viewing `MapScreenWithTrack`,
• *Then* `GeneralMapLayer.CLUSTERS` SHALL be disabled by default (`GeneralMapLayer.DEFAULT_ENABLED`), and favorite route cluster polylines SHALL NOT be displayed on the map canvas until explicitly toggled on by the user.
• *Given* the user opens the map on cold start without visiting the Lieblingsstrecken list screen,
• *When* `GeneralMapLayer.CLUSTERS` is enabled,
• *Then* `WorkoutClusterRepository.loadAllClustersLightweight()` hydrates `_clusterStats` if empty, and favorite route clusters immediately display best time, ascent, and recency without requiring a visit to the cluster list.
• *Given* the athlete viewing `MapScreenWithTrack`,
• *When* tapping a favorite route cluster polyline,
• *Then* `ClusterOnMapSheet` is displayed hosted inside `MapDetailLayout(useStatusBarsPadding = false, showMap = true)`, rendering `MinimumDragHandle()` at the top.
• *And* the peek header reuses `WorkoutClusterIdentityRow` and `WorkoutClusterMetadataBlock` matching the Lieblingsstrecken list items 1:1 without repeating duplicate metrics in `metadataContent`,
• *And* dragging the sheet upward expands to display the detailed cluster map (`showMap = true`) with heatmap, tracks, linked route, and markers instead of blank space,
• *And* peek height dynamically adapts to `measuredClusterHeaderHeight` sitting flush above navigation bars.
• *Given* the athlete in a live tracking session viewing `SensorGridScreen.kt`,
• *When* observing the embedded cockpit map (even when `GeneralMapLayer.CLUSTERS` is enabled on the general map),
• *Then* favorite route clusters (Lieblingsstrecken) SHALL NEVER be displayed on the cockpit map, and `TrackingViewModel` SHALL NOT collect `WorkoutClusterRepository.allClusters`.
• *Given* the athlete viewing `MapScreenWithTrack`,
• *When* inspecting the top map overlay,
• *Then* a floating layers button (`Icons.Default.Layers`) is visible at `Alignment.TopEnd` with `statusBarsPadding().padding(top = 16.dp, end = 16.dp)`.
• *Given* the athlete taps the layers button,
• *When* the dropdown menu opens,
• *Then* checkboxes for Routes, Segments, Favorite Locations, Favorite Tracks / Clusters, and Recorded Track are displayed with color legend swatches and localized labels.
• *Given* an unchecked layer in the dropdown menu,
• *When* observing the map canvas,
• *Then* that layer's elements are immediately hidden without disturbing other active layers or background tracking.
• *Given* modified layer visibility preferences,
• *When* restarting the application or returning to the map,
• *Then* the layer selection state is restored from `SharedPreferences`.
• *Given* `GeneralMapLayer.CLUSTERS` is toggled on in the layers menu,
• *When* loading clusters,
• *Then* cluster data SHALL load asynchronously from the SQLite `RouteClusters` table without blocking on heavy `clusterStats` aggregation,
• *And* representative polylines SHALL decode on `Dispatchers.Default` with referential coordinate memoization, eliminating Main-thread UI stalling.
• *Given* the athlete viewing `MapScreenWithTrack`,
• *When* tapping a favorite route cluster (Lieblingsstrecke) polyline,
• *Then* `ClusterOnMapSheet` is presented at peek height (`PeekHeightWorkout + navBarHeight`),
• *And* the sheet displays a 32dp multi-color sport icon (`Color.Unspecified`), cluster title, sport subtitle, reference distance, elevation gain, PR trophy with best time, hit count in `primary`, and relative recency without any purple text tinting.

**Invariants**: Detail sheet inspection, live GPS tracking, start marker detection, referential polyline stability (`REQ-MAP-051`), and 100% test pass rate MUST NOT be broken.

### REQ-UI-323: Material 3 Compose Migration for ANT Missing Adapter and Missing Dependency Alerts with Direct ANT+ Status Sheet Integration [Verified]
- **Tests**: `TST-UI-283`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: AntAlertDialogs.kt, MainActivityWithNavigation.kt, ATrainingTrackerApp.kt
- **Rationale**: The system SHALL modernize legacy ANT missing adapter and missing dependency alert dialogs into Material 3 Jetpack Compose components in `AntAlertDialogs.kt`, eliminate raw Android View `android.app.AlertDialog.Builder` popups in `MainActivityWithNavigation.kt`, provide direct actionable shortcuts to `AntServicesStatusSheet.kt`, correct the German localization typographical error in `values-de/strings.xml`, and enforce 100% translation parity across all 9 supported application locales (ATT-2939, amending `REQ-UI-296` and complementing `REQ-UI-309`):
1. *Explicit Dialog State Model (`AntDialogState.kt`)*:
• The system SHALL declare a sealed hierarchy `AntDialogState` in `com.atrainingtracker.trainingtracker.ui.ant` with `MissingAdapter` and `MissingDependency(dependencyName, packageName)`.
2. *Material 3 Compose Alert Dialogs (`AntAlertDialogs.kt`)*:
• `AntMissingAdapterDialog` SHALL compose an `androidx.compose.material3.AlertDialog` with untinted ANT+ logo (`safePainterResource(id = R.drawable.ant_logo)`), title, body text, primary action button `ant_dialog_view_status` ("View ANT+ Status"), and dismiss button `ant_dialog_dismiss` ("Got it").
• `AntMissingDependencyDialog` SHALL compose an `androidx.compose.material3.AlertDialog` with untinted ANT+ logo, title, dependency body text, action button `go_to_store`, and dismiss button `cancel`.
3. *Actionable Shortcut & Sheet Integration (`MainActivityWithNavigation.kt` & `ATrainingTrackerApp.kt`)*:
• Tapping "View ANT+ Status" on `AntMissingAdapterDialog` SHALL dismiss the dialog and immediately present `AntServicesStatusSheet`, guiding the athlete on USB-OTG adapter and ANT+ USB stick hardware requirements.
• Tapping "Go to Store" on `AntMissingDependencyDialog` SHALL launch the Google Play Store with `market://details?id=<package>`.
4. *Elimination of Raw Platform Dialogs*:
• `MainActivityWithNavigation.kt` SHALL NOT instantiate `AlertDialog.Builder` or raw `android.app.AlertDialog` for `com.atrainingtracker.ADAPTER_NOT_DETECTED` or `com.atrainingtracker.ANT_DEPENDENCY_MISSING` broadcast receivers.
5. *Non-Repetitive Alert Presentation*:
• In `MainActivityWithNavigation.kt`, incoming failure broadcasts when an alert dialog or `AntServicesStatusSheet` is already visible SHALL NOT duplicate or stack alerts.
6. *German Typographical Error Correction & 9-Language Parity*:
• In `values-de/strings.xml:953`, "intalliert" SHALL be replaced with "installiert".
• String resources `ant_dialog_view_status` and `ant_dialog_dismiss` SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
7. *Preservation of System Invariants*:
• `BANALService` and `MyANTDevice` sensor discovery and pairing, cold-start suppression (`REQ-UI-296`), and 100% test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-296` (*Modernized Contextual ANT+ System Service Guidance & Cold-Start Dialog Elimination*, ATT-2513) Clause 1 & 4, and complements `REQ-UI-309` (*Official Untinted ANT+ Logo Rendering and Bluetooth LE Advisory Note Elimination in ANT+ System Services Sheet*, ATT-2749).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2513` (Sprint 2026-41.3) and Ticket `ATT-2749` (Sprint 2026-41.4). Legacy code in `MainActivityWithNavigation.kt` authored in 2017 (`05.01.17`).
3. *Root Reason for Existing Formulation*: In ATT-2513, cold-start checking was eliminated and `AntServicesStatusSheet` was introduced for sensor tabs. However, the runtime broadcast receivers in `MainActivityWithNavigation.kt` for dynamic scan failure events (`ADAPTER_NOT_DETECTED` and `ANT_DEPENDENCY_MISSING`) were overlooked and retained legacy `android.app.AlertDialog.Builder` popups with raw view styles and dead-end "OK" buttons.
4. *Preservation of Core Invariants*: Hardware detection in `MyANTDevice.java`, Google Play store launches for missing packages, single-instance dialog presentation per scan/session, and 100% test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a smartphone lacking built-in ANT+ hardware,
• *When* the sensor stack emits `ADAPTER_NOT_DETECTED`,
• *Then* `MainActivityWithNavigation` presents `AntMissingAdapterDialog` composed in Material 3 with official untinted ANT+ logo, title, and explanatory text,
• *And* no legacy `android.app.AlertDialog` instance is created.
• *Given* the `AntMissingAdapterDialog`,
• *When* the athlete taps the primary action button ("View ANT+ Status" / "ANT+ Status anzeigen"),
• *Then* the dialog is dismissed and `AntServicesStatusSheet` is immediately displayed on screen.
• *Given* a missing ANT+ system dependency,
• *When* the sensor stack emits `ANT_DEPENDENCY_MISSING`,
• *Then* `AntMissingDependencyDialog` is displayed with dependency name, Google Play action button, and cancel button.
• *Given* `values-de/strings.xml`,
• *When* inspecting string resource `ant_missing_adapter_message`,
• *Then* the word "installiert" is spelled correctly, with zero occurrences of "intalliert".
• *Given* all 9 supported application locales,
• *When* running translation parity tests,
• *Then* `ant_dialog_view_status` and `ant_dialog_dismiss` exist, are non-empty, and resolve with zero errors.

**Invariants**: Sensor discovery and pairing pipelines, Google Play store intent launches, single-instance alert guard, and 100% test pass rate MUST NOT be broken.

### REQ-UI-324: Turn-by-Turn Navigation Prompts Top-Level Spatial Overlay Integration & Design Guideline Harmonization [Verified]
- **Tests**: `TST-UI-284`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorGridScreen.kt`, `TurnPromptBanner.kt`
- **Rationale**: The system SHALL eliminate cockpit telemetry tile displacement during turn-by-turn navigation guidance, gate navigation prompts strictly behind the tab-level navigation hints setting, and harmonize the visual design of `TurnPromptBanner` with Design Guidelines §§ 5.2, 5.3, 5.4, and 5.7 (ATT-2941, amending `REQ-MAP-028` and `REQ-UI-287`):
1. *Top-Level Spatial Overlay Architecture (`SensorGridScreen.kt`)*:
• `TurnPromptBanner` SHALL be removed from the in-flow base cockpit `Column` in `SensorGridScreen.kt`.
• `TurnPromptBanner` SHALL be hosted within the top-level spatial overlay `Column` anchored at `Alignment.TopCenter`, stacked directly above `ReturnNavigationHud` and `ForkDecisionCard`.
• The presence, appearance, vertical expansion, or dismissal of `TurnPromptBanner` SHALL NOT alter the vertical position, layout coordinates, or scroll offset of the underlying sensor grid tiles or live map.
2. *Strict Per-Tab Navigation Gating*:
• `TurnPromptBanner` SHALL be conditionally gated strictly behind `state.showNavigationHints`.
• When `state.showNavigationHints == false` on the active tracking tab, `TurnPromptBanner` SHALL be completely excluded from the Compose composition tree, ensuring 0% touch interception and complete suppression of turn prompts.
3. *Harmonized Card Visuals & Material 3 Styling (`TurnPromptBanner.kt`)*:
• *Card Shape*: `TurnCueCard` and `OffRouteCard` SHALL adopt `RoundedCornerShape(16.dp)`, harmonizing with `ForkDecisionCard` and Section 5.3.
• *Container Color & Transparency*: Approaching turn cue SHALL resolve to `MaterialTheme.colorScheme.surfaceContainer.copy(alpha = overlayAlpha)`; immediate maneuver (`isTurnNow`) SHALL resolve to `MaterialTheme.colorScheme.primaryContainer.copy(alpha = overlayAlpha)`; off-route warning SHALL resolve to `MaterialTheme.colorScheme.errorContainer.copy(alpha = overlayAlpha)`.
• *Border Accent*: Turn cue SHALL apply `BorderStroke(1.dp, TTColor.RouteActiveNavigation.copy(alpha = 0.5f))` enforcing the Royal Blue navigation domain accent (§ 5.4 / § 5.7); off-route warning SHALL apply `BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))`.
• *Spacing Scale*: Outer padding SHALL be `padding(horizontal = 16.dp, vertical = 6.dp)`; inner padding SHALL be `padding(horizontal = 16.dp, vertical = 12.dp)` conforming strictly to Section 5.2.
4. *Preservation of System Invariants*:
• Turn maneuver countdown distances, directional iconography, street name labels, and auto-dismiss timing (`tuningConfig.navigationCueDismissDurationSec`) MUST remain 100% operational.
• AMOLED battery saver wake-up, auditory chime dispatch, and 100% clean-room test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-MAP-028` (*Visual & Auditory Turn-by-Turn Navigation Prompts*, Clause 4, ATT-1450) and `REQ-UI-287` (*Cockpit Turn Prompt Banner*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1450` (Sprint 2026-40.16) and Ticket `ATT-2632`. Introduced before the top-level spatial overlay architecture was standardized in `ATT-2874` (`REQ-UI-321`) and `ATT-2938` (`REQ-MAP-039`).
3. *Root Reason for Existing Formulation*: Originally placed sequentially in the main Column as an MVP to ensure visibility above the sensor grid. Field desk testing revealed that unexpected layout displacement disrupts visual tracking of core metrics in motion.
4. *Preservation of Core Invariants*: Geodesic distance tracking, battery saver wake-up, chime triggers, and full-suite test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout tracking session on `SensorGridScreen`,
• *When* a turn cue or off-route alert appears,
• *Then* `TurnPromptBanner` floats at `Alignment.TopCenter` within the top-level spatial overlay stack,
• *And* underlying sensor tiles remain 100% stationary without layout shifts.
• *Given* an active tracking tab with `showNavigationHints = false`,
• *When* navigating an active route,
• *Then* `TurnPromptBanner` is completely excluded from the Compose composition tree.
• *Given* `TurnPromptBanner` composed on screen,
• *When* inspecting card styling,
• *Then* shape is `RoundedCornerShape(16.dp)`, border is `TTColor.RouteActiveNavigation.copy(alpha = 0.5f)`, container uses `surfaceContainer` / `primaryContainer`, and inner padding is `16.dp` horizontal and `12.dp` vertical.

**Invariants**: Turn detection, battery saver wake-up, audio alerts, and 100% test pass rate MUST NOT be broken.

### REQ-UI-325: Removal of Duplicate Upper Active Route Banner and Direct RouteCard Cancellation Integration [Verified]
- **Tests**: `TST-UI-285`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RouteSelectorSheet.kt`
- **Rationale**: The system SHALL eliminate duplicate active route representation in `RouteSelectorSheet.kt` by removing the pinned upper `ActiveRouteBanner` from `RouteSelectorContent` and integrating direct 1-tap route cancellation onto the active `RouteCard` in the scrollable route list (ATT-2943, amending `REQ-UI-280`):
1. *Elimination of Upper Active Route Banner (`RouteSelectorSheet.kt`)*:
• `RouteSelectorContent` SHALL NOT compose `ActiveRouteBanner` or render an upper active route card beneath the sheet header.
• The active route SHALL appear exclusively within the scrollable `LazyColumn` route list rendered by `RouteCard`.
2. *Direct 1-Tap Active Route Cancellation on `RouteCard`*:
• `RouteCard` SHALL accept parameter `onClearRoute: (() -> Unit)? = null`.
• When `isActive == true`, `RouteCard` SHALL render an `IconButton` with `Icons.Default.Close` (`contentDescription = stringResource(id = R.string.route_action_clear)`), allowing the athlete to stop/clear active navigation with 1 tap directly from the route item.
• Clicking on an inactive `RouteCard` SHALL select that route (`viewModel.selectRoute(route.summary.id)`) and trigger `onRouteSelected`.
3. *Preservation of System Invariants*:
• `ActiveRouteBanner` composable definition SHALL remain present in `RouteSelectorSheet.kt` for API/test contract backward compatibility.
• Active route visual distinction via `secondaryContainer` card background and `"ACTIVE"` badge chip MUST remain strictly intact.
• Mid-Ride Heimweg card (`MidRideHeimwegCard`), authentic sport icon colors (`Color.Unspecified`), empty state handling, and 100% test pass rate MUST be preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-280` (*Streamlined Route Selector: Removal of Sort Tabs and Pinned Heimweg Card in Favor of Proximity-Recency Ranking*, Clause 3, ATT-2459).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2459` (Sprint 2026-41.1) and Ticket `ATT-1835` (Sprint 2026-40.14).
3. *Root Reason for Existing Formulation*: `ActiveRouteBanner` was originally placed at the top because `RouteCard` lacked a stop/cancellation button. However, once `RouteCard` was enhanced with the `"ACTIVE"` badge chip, rendering both the banner and the card caused duplicate visual clutter and occupied unnecessary screen height.
4. *Preservation of Core Invariants*: 1-tap route cancellation, active badge prominence, candidate route switching, and 100% full-suite unit test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active navigated route set in the route repository,
• *When* the athlete opens `RouteSelectorModalBottomSheet`,
• *Then* `RouteSelectorContent` SHALL NOT render the upper `ActiveRouteBanner`,
• *And* the active route SHALL appear exactly once inside the scrollable route list.
• *Given* the active route rendered as `RouteCard` in the route list,
• *When* inspecting the card,
• *Then* it displays the `"ACTIVE"` badge chip and a close/clear action button (`Icons.Default.Close`),
• *And* tapping the close button immediately stops active navigation.
• *Given* an inactive route card in the list,
• *When* the athlete taps the card,
• *Then* that route is selected and the bottom sheet dismisses.

**Invariants**: 1-tap route cancellation, active badge styling, authentic sport icon colors, and 100% unit test pass rate MUST NOT be broken.

### REQ-UI-326: Tracking Map Recomposition Boundary Isolation and Telemetry Decoupling [Verified]
- **Tests**: `TST-UI-286`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorGridScreen.kt`, `TrackingViewModel.kt`
- **Rationale**: The system SHALL isolate the live Google Map composable in `SensorGridScreen.kt` from high-frequency sensor telemetry recomposition churn by establishing an `@Immutable` sub-state model, extracting a dedicated `TrackingMapContainer` recomposition barrier with memoized layer DSL content, and decoupling map data computation from volatile sensor telemetry in `TrackingViewModel.kt` (ATT-2944, complementing `REQ-UI-275` and `REQ-UI-295`):
1. *Sub-State Model Isolation (`TrackingMapState`)*:
• The system SHALL define `@Immutable data class TrackingMapState` in `com.atrainingtracker.trainingtracker.ui.tracking.tracking` encapsulating all map-specific rendering parameters: `showMap: Boolean = false`, `zoomFocus: MapZoomFocus = MapZoomFocus.TRACK_AND_MARKERS`, `userBearing: Float = 0f`, `userSpeed: Float = 0f`, `bSportType: BSportType = BSportType.UNKNOWN`, `currentTrack: List<LatLng> = emptyList()`, `mapTracks: List<MapTrack> = emptyList()`, `mapSegments: List<MapSegment> = emptyList()`, `activeLiveSegmentIds: Set<Long> = emptySet()`, `mapRoutes: List<MapRoute> = emptyList()`, `mapMarkers: List<LocationMarker> = emptyList()`..
• `TrackingScreenState` SHALL incorporate `val mapState: TrackingMapState = TrackingMapState()` and expose backward-compatible accessors for existing map properties.
2. *Dedicated Recomposition Barrier (`TrackingMapContainer`)*:
• The system SHALL implement `@Composable fun TrackingMapContainer(mapState: TrackingMapState, currentLocationFlow: StateFlow<LatLng?>, modifier: Modifier = Modifier)` in `SensorGridScreen.kt`.
• Inside `TrackingMapContainer`, the `MapContentScope.() -> Unit` lambda passed to `ATrainingTrackerMap` SHALL be memoized via `remember(mapState.mapTracks, mapState.mapSegments, mapState.activeLiveSegmentIds, mapState.mapRoutes, mapState.mapMarkers, mapState.currentTrack)`.
• `SensorGridScreen.kt` SHALL compose `TrackingMapContainer` in place of direct `ATrainingTrackerMap` composition.
• When `SensorGridScreen` recomposes solely due to high-frequency sensor telemetry changes (`state.fields`), the Compose compiler SHALL skip `TrackingMapContainer`, eliminating all map DSL re-allocations and bounds calculations.
3. *ViewModel State Decoupling (`TrackingViewModel.kt`)*:
• `TrackingViewModel` SHALL decouple map state generation from `allFilteredSensorData`.
• Emitting new telemetry values (Heart Rate, Cycling Power, Cadence, Speed, 1 Hz timer tick) SHALL preserve the existing `TrackingMapState` instance without re-mapping routes or recreating map layer lists.
4. *Preservation of System Invariants*:
• User location arrow streaming via `currentLocationFlow` and recorded live track updates via `currentTrack` MUST remain responsive.
• Dynamic map visibility (`showMap`), weighted expansion (`Modifier.weight(1f)` in tracking/preview mode), and configuration mode toggle cards (`REQ-UI-295`) MUST remain strictly preserved.
• 100% full-suite unit test pass rate across all tracking and map test suites.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-326`), complementing `REQ-UI-275` (*Unified WYSIWYG Tracking Tab Configuration*, ATT-2360) and `REQ-UI-295` (*Unified Scrollable Container Architecture*, ATT-2620).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2360` (commit `ec2b94b9`) and `ATT-2620` (commit `e5033b5c`).
3. *Root Reason for Existing Formulation*: `ATrainingTrackerMap` was embedded directly in `SensorGridScreen` with an un-remembered trailing lambda during initial cockpit development, causing continuous recomposition churn and DSL re-allocations on every 1 Hz sensor telemetry tick.
4. *Preservation of Core Invariants*: Full backward compatibility of `TrackingScreenState`, live track recording, user location tracking, and 100% test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* active workout tracking on `SensorGridScreen`,
• *When* sensor telemetry updates (e.g. Heart Rate changes, 1 Hz duration timer tick),
• *Then* `SensorGridScreen` recomposes to update sensor metric tiles,
• *And* `TrackingMapContainer` is skipped by the Compose runtime,
• *And* zero layer lambda allocations or bounds recalculations occur.
• *Given* active workout tracking on `SensorGridScreen`,
• *When* an active route is selected/deselected, a map layer is toggled, or a new track point is recorded,
• *Then* `TrackingMapState` updates,
• *And* `TrackingMapContainer` recomposes smoothly with the updated map content.
• *Given* active workout tracking with follow-me mode enabled,
• *When* GPS location fixes arrive on `currentLocationFlow`,
• *Then* the user location arrow and follow-me camera smoothly follow the athlete without stutter or frame drops.

**Invariants**: Dynamic map visibility, user location arrow streaming, recorded track updates, and 100% unit test pass rate MUST NOT be broken.

### REQ-UI-327: Tracking Tab Live Climb and Segment Cockpit BottomSheet Active-Tab Gating & State Synchronization [Verified]
- **Tests**: `TST-UI-287`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
2. *Deterministic Sheet State Lifecycle & Hidden State Synchronization*:
• `scaffoldState` in `SensorGridScreen.kt` SHALL initialize `bottomSheetState` with: `initialValue = if (shouldShowBottomSheet) SheetValue.PartiallyExpanded else SheetValue.Hidden` and `skipHiddenState = false`..
• `SensorGridScreen.kt` SHALL execute a `LaunchedEffect(shouldShowBottomSheet)` that: (a) when `!shouldShowBottomSheet`: if `scaffoldState.bottomSheetState.currentValue != SheetValue.Hidden`, calls `scaffoldState.bottomSheetState.hide()`; (b) when `shouldShowBottomSheet`: if `scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden`, calls `scaffoldState.bottomSheetState.partialExpand()`..
3. *Complete Surface & Peek Artifact Suppression*:
• When `!shouldShowBottomSheet`: `sheetPeekHeight = 0.dp`, `sheetSwipeEnabled = false`, `sheetShadowElevation = 0.dp`, `sheetTonalElevation = 0.dp`, `sheetContainerColor = Color.Transparent`, and `sheetContent` SHALL NOT render any placeholder `Box` (render an empty composable block).
• When `shouldShowBottomSheet`: `sheetPeekHeight = BottomSheetDesign.PeekHeightLiveSegment + navBarHeight`, `sheetSwipeEnabled = true`, `sheetShadowElevation = BottomSheetDesign.SheetShadowElevation`, `sheetTonalElevation = BottomSheetDesign.SheetTonalElevation`, `sheetContainerColor = MaterialTheme.colorScheme.surface`..
4. *State Hygiene & Backward-Compatible Defaults (`TrackingViewModel.kt`)*:
• `TrackingScreenState.showLiveClimbs` SHALL retain its default value of `true` (`val showLiveClimbs: Boolean = true`) for complete backward compatibility with `REQ-UI-275`, while active climb sheet display is strictly gated by `isTabActive && shouldShowBottomSheet` runtime evaluation.
5. *Preservation of System Invariants*:
• Full ClimbPro cockpit functionality (`LiveClimbSheet`) on tabs where `showLiveClimbs == true` during active climbs (`APPROACHING` / `ON_CLIMB`).
• Strava Live Segments (`LiveSegmentSheet`) priority and rendering.
• Dynamic expand/collapse drag gestures and swipe interactions on active tabs.
• Full compliance with `TrackingTabWysiwygContractTest.kt` (`REQ-UI-275` / `REQ-UI-295`).
• 100% full clean-room unit test pass rate across all 2,253+ tests.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-275` (*Unified WYSIWYG Tracking Tab Configuration with Spatial Overlays and Per-Tab Popup Toggles*, Clause 6b, ATT-2360) and `REQ-UI-295` (*Unified Scrollable Container Architecture*, Clause 4, ATT-2620).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1281` (Sprint 2026-40.14), Ticket `ATT-2360` (Sprint 2026-41.1), Ticket `ATT-2620` (Sprint 2026-41.3).
3. *Root Reason for Existing Formulation*: `skipHiddenState = false` was declared, but no code ever drove `bottomSheetState.hide()`, and `initialValue` was hardcoded to `PartiallyExpanded`. In M3 Compose, setting `sheetPeekHeight = 0.dp` without hiding leaves the elevated surface and shadow at the bottom edge, creating a visible peek line, while pre-composition in HorizontalPager bled sheet state across tabs.
4. *Preservation of Core Invariants*: ClimbPro display on enabled tabs, Live Segments priority, swipe gestures, and 100% full-suite test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout tracking session with an active climb,
• *When* viewing a tracking tab where `showLiveClimbs == false`,
• *Then* `scaffoldState.bottomSheetState` is in `SheetValue.Hidden`, `sheetPeekHeight` is `0.dp`, `sheetShadowElevation` is `0.dp` with `Color.Transparent` container, and no climb bottom sheet, surface, or peek banner is visible.
• *Given* an active workout tracking session with an active climb,
• *When* swiping between tabs with Live Climbs enabled and disabled,
• *Then* the climb sheet smoothly hides on disabled tabs and restores on enabled tabs without horizontal drag contention or state bleeding.
• *Given* cold start initialization of a tab with `showLiveClimbs == false`,
• *When* the screen initializes,
• *Then* `isTabActive && shouldShowBottomSheet` strictly gates the bottom sheet, preventing any transient active climb sheet flashes prior to database view info loading.

**Invariants**: ClimbPro cockpit on enabled tabs, Live Segments priority, dynamic drag gestures, and 100% unit test pass rate MUST NOT be broken.
- **Rationale**: The system SHALL strictly suppress the live climb and segment bottom sheet (`BottomSheetScaffold`) on any tracking tab where Live Climbs or Live Segments are disabled or when the tab is not the currently active page in `HorizontalPager`, eliminating bottom sheet peek artifacts, cold-start flashes, and cross-tab state bleeding (ATT-2945, amending `REQ-UI-275` and `REQ-UI-295`):
1. *Active Tab Parameterization & Pre-composition Isolation*:
• `TrackingTabsScreen.kt` SHALL determine active tab status for each page in `HorizontalPager`: `isTabActive = (pagerState.currentPage == targetPage)` (where `targetPage = index + 1` in `TRACKING` mode, accounting for the Control Tab on page 0).
• `TrackingTabGridContent` and `SensorGridScreen` SHALL accept parameter `isTabActive: Boolean = true` (defaulting to `true` for standalone testing and preview).
• In `SensorGridScreen.kt`, bottom sheet activation SHALL be gated strictly behind: `val shouldShowBottomSheet = isTabActive && (showLiveSegments

### REQ-UI-328: Live Climb Cockpit Sheet Visual Design Alignment with Live Segment System [Verified]
- **Tests**: `TST-UI-288`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `LiveClimbSheet.kt`
- **Rationale**: The system SHALL refactor `LiveClimbSheet.kt` to harmonize layout hierarchy, spacing, and typography with the Strava Live Segment popup (`SegmentHeader.kt` / `SegmentLiveDetails.kt`) (ATT-2947, amending `REQ-MAP-027` and harmonizing with `REQ-UI-196`):
1. *Top Header Row (`LiveClimbSheet.kt`)*:
• Dedicated 32dp terrain/climb icon (`Icons.Default.Terrain`, tint = `MaterialTheme.colorScheme.primary`).
• High-prominence climb title rendered in `MaterialTheme.typography.titleLarge`, `FontWeight.Bold`, single-line with `TextOverflow.Ellipsis`, assigned `Modifier.weight(1f)`.
• Right-aligned `ClimbCategoryChip` badge in the top row.
2. *Subtitle Status & Route Counter Row (`LiveClimbSheet.kt`)*:
• Left: Live climb status label/badge (`ClimbStatusBadge`), displaying approaching distance (when `APPROACHING`), "On Climb" (when `ON_CLIMB`), or "Summit" (when `FINISHED`).
• Right: Route climb counter (e.g. "Climb 2 of 5") or total ascent gain formatted in `MaterialTheme.typography.labelLarge`, `FontWeight.Bold`, `color = MaterialTheme.colorScheme.primary`.
3. *Standardized Section Divider*:
• `HorizontalDivider` with 0.5dp thickness, `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)`, and `16.dp` horizontal padding.
4. *Live Telemetry HUD Progression*:
• Left column: Remaining distance to summit in `MaterialTheme.typography.titleLarge` bold and remaining elevation gain in `MaterialTheme.typography.bodyLarge` bold (`color = MaterialTheme.colorScheme.onSurfaceVariant`).
• Right column (end-aligned): Current instantaneous gradient percentage in `MaterialTheme.typography.headlineMedium` bold monospace font (`color = MaterialTheme.colorScheme.primary`), labeled with "Grade" in `MaterialTheme.typography.labelSmall`.
5. *Elevation Profile Presentation*:
• Seamlessly integrated `ClimbProfileCanvas` (height: 64dp) displaying dynamic grade color bands and real-time rider position marker.
6. *9-Language Localization Parity*:
• String resources for all labels, counters, and status badges maintained across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
7. *Preservation of System Invariants*:
• Exact mathematical calculation of distance to summit, remaining elevation gain, and instantaneous gradient.
• Dynamic elevation profile canvas geometry, gradient color mapping, and rider pin interpolation.
• BottomSheetScaffold suppression on disabled/inactive tabs (`REQ-UI-327` / `ATT-2945`).
• 100% full clean-room unit test pass rate across the full test suite.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-MAP-027` (*ClimbPro Real-Time Live Climb Cockpit Sheet with Dynamically Colored Gradient Profile & Elevation Pacing*, ATT-1281).
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-40.14 (`ATT-1281`).
3. *Root Reason for Existing Formulation*: The initial implementation focused on profile canvas math and gradient interpolation. The header and HUD metrics were created ad-hoc with inverted chip placement and small typography, divergent from the `SegmentHeader` design standard.
4. *Preservation of Core Invariants*: `ClimbProfileCanvas` math, rider pin animation, `ClimbCategoryChip` color coding, tab gating (`ATT-2945`), and full test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active live climb rendered in `LiveClimbSheet`,
• *When* inspecting the top header row,
• *Then* it displays a 32dp climb icon on the left, climb name in `titleLarge` bold with ellipsis, and `ClimbCategoryChip` right-aligned.
• *Given* an active climb on a route with 5 climbs,
• *When* approaching climb 2 in 250m,
• *Then* the subtitle row displays `ClimbStatusBadge` with "Approaching (250 m)" on the left, and route counter "Climb 2 of 5" on the right in `labelLarge` bold primary color.
• *Given* `LiveClimbSheet` during active climbing (`ON_CLIMB`),
• *When* inspecting telemetry metrics,
• *Then* a 0.5dp divider separates header from telemetry, remaining distance is `titleLarge` bold, remaining elevation is `bodyLarge` bold, and current gradient is `headlineMedium` bold monospace font.
• *Given* `LiveClimbSheet` rendered in the cockpit,
• *When* inspecting the elevation profile,
• *Then* `ClimbProfileCanvas` renders smoothly with gradient color bands and the rider pin indicator.

**Invariants**: Exact distance/elevation math, profile canvas gradient color mapping, rider pin interpolation, and 100% test pass rate MUST NOT be broken.

### REQ-UI-329: Material 3 Jetpack Compose Workout Recovery Dialog Architecture & Contextual Kill Diagnosis Presentation [Verified]
- **Tests**: `TST-UI-289`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `StartOrResumeDialog.kt`
- **Rationale**: The system SHALL modernize `StartOrResumeDialog.kt` from a legacy Android View dialog to a Material 3 Jetpack Compose dialog architecture, featuring contextual kill reason iconography, structured message containerization, clear action button hierarchy, and full theme integration (ATT-2948, amending `REQ-STB-012` and harmonizing with `REQ-STB-003` and Epic `ATT-355`):
1. *Compose Dialog Migration & DialogFragment Contract*:
• `StartOrResumeDialog` SHALL continue to inherit `DialogFragment` (`androidx.fragment.app.DialogFragment`), strictly preserving `StartOrResumeDialog.TAG`, `attachInterface(Context)`, and `StartOrResumeInterface` callbacks (`chooseStart()`, `chooseResume()`).
• In `onCreateView` (or `onCreateDialog`), the dialog view hierarchy SHALL be hosted inside `ComposeView` with `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed`, wrapped in `ATrainingTrackerTheme`.
• The underlying dialog window SHALL configure a transparent background (`dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)`), delegating modal surface geometry, rounded corners (`RoundedCornerShape(28.dp)`), container coloring (`MaterialTheme.colorScheme.surfaceContainerHigh`), and tonal elevation (`6.dp`) to Material 3 `Surface`.
2. *Contextual Semantic Iconography & Header Hierarchy*:
• The dialog header SHALL display a dedicated 36dp semantic icon and bold title (`MaterialTheme.typography.titleLarge`, `FontWeight.Bold`, centered alignment) dynamically mapped to `KillDiagnosis.reason`:
- `KillReason.BATTERY_KILL`: `Icons.Default.BatteryAlert` with semantic warning/error accent, displaying `R.string.unfinished_workout_title_battery`.
- `KillReason.LOW_MEMORY`: `Icons.Default.Memory` with primary accent, displaying `R.string.unfinished_workout_title_memory`.
- `KillReason.PERMISSION_REVOKED`: `Icons.Default.Security` with semantic error accent, displaying `R.string.unfinished_workout_title_permission`.
- `KillReason.GENERIC_UNFINISHED`: `Icons.Default.DirectionsRun` with primary accent, displaying `R.string.unfinished_workout_title`.
3. *Structured Message Container & Typographic Separation*:
• When `diagnosis.reason != KillReason.GENERIC_UNFINISHED`, the forensic explanation (including progressive escalation stages 1, 2, 3, and Strava activity note) SHALL be formatted inside a subtle tinted callout container (`MaterialTheme.colorScheme.surfaceContainer` with `RoundedCornerShape(12.dp)` and `padding(12.dp)`).
• The user decision prompt (`R.string.start_or_resume_dialog_message`) SHALL be rendered distinctly in `MaterialTheme.typography.bodyMedium` (`color = MaterialTheme.colorScheme.onSurfaceVariant`) below the diagnostic callout container.
4. *Action Button Hierarchy*:
• Primary action: "Fortsetzen" (`R.string.resume_workout`) SHALL be rendered as a filled `Button`, invoking `startOrResumeInterface?.chooseResume()` and dismissing the dialog upon tap.
• Secondary action: "Neues Training" (`R.string.start_new_workout`) SHALL be rendered as an `OutlinedButton`, invoking `startOrResumeInterface?.chooseStart()` and dismissing the dialog upon tap.
• Contextual action: When `diagnosis.shouldShowBatteryButton == true`, the dialog SHALL render a full-width `FilledTonalButton` or `OutlinedButton` labeled "Akku-Optimierung deaktivieren" (`R.string.action_disable_battery_optimization`), invoking `ProcessExitReasonHelper.openBatteryOptimizationSettings(context)` and dismissing the dialog upon tap.
5. *9-Language Localization Parity*:
• All string resources utilized in `StartOrResumeDialog` SHALL be maintained with 100% translation parity across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
6. *Preservation of System Invariants*:
• `StartOrResumeInterface` contracts and `StartOrResumeDialogContractTest.kt` inheritance assertions MUST NOT be broken.
• Direct system intent for battery optimization (`ProcessExitReasonHelper.openBatteryOptimizationSettings`) MUST remain fully functional.
• 100% clean-room test pass rate across all unit tests.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-STB-012` (*Forensic Process Kill Diagnosis, Dynamic Rationale Titles, Empathetic Escalation & Direct Battery Exemption Guidance*, ATT-2079).
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-40.16 (`ATT-2079`, commit `95e0c511`).
3. *Root Reason for Existing Formulation*: The initial implementation focused on integrating Android 11+ `ApplicationExitInfo` and `PowerManager` logic. The UI was wrapped inside `AlertDialog.Builder` as a quick expedient to ensure feature delivery without refactoring to Compose during that sprint.
4. *Preservation of Core Invariants*: Resumption contracts (`chooseStart()`, `chooseResume()`), direct battery optimization settings intent, idempotent escalation counting, and 100% 9-language localization parity remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an interrupted workout detected with kill reason `BATTERY_KILL`,
• *When* `StartOrResumeDialog` renders,
• *Then* the dialog displays `Icons.Default.BatteryAlert` and title "Workout Interrupted: Battery Optimization" in `titleLarge` bold font.
• *Given* an interrupted workout detected with kill reason `LOW_MEMORY`,
• *When* `StartOrResumeDialog` renders,
• *Then* the dialog displays `Icons.Default.Memory` and title "Workout Interrupted: Low Memory".
• *Given* an interrupted workout detected with kill reason `PERMISSION_REVOKED`,
• *When* `StartOrResumeDialog` renders,
• *Then* the dialog displays `Icons.Default.Security` and title "Workout Interrupted: Permission Revoked".
• *Given* an interrupted workout with `GENERIC_UNFINISHED`,
• *When* `StartOrResumeDialog` renders,
• *Then* the dialog displays `Icons.Default.DirectionsRun` and title "Workout Interrupted".
• *Given* an interrupted workout with diagnostic details,
• *When* rendered,
• *Then* the explanation is enclosed within a tinted callout container, and the resume prompt is rendered separately below.
• *Given* action buttons rendered,
• *When* inspecting hierarchy,
• *Then* "Resume prev." is a filled `Button` and "Start new" is an `OutlinedButton`, with "Disable Battery Optimization" visible when `shouldShowBatteryButton == true`.

**Invariants**: Resumption contracts, battery settings intent, DialogFragment inheritance, and 100% test pass rate MUST NOT be broken.

### REQ-UI-330: In-Ride Fork Decision Card Directional Grouping, Clean Surface Styling & Glanceability Optimization [Verified]
- **Tests**: `TST-UI-290`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ForkDecisionCard.kt`
- **Rationale**: The system SHALL modernize `ForkDecisionCard.kt` to optimize glanceability on bike mounts through directional route grouping, crisp white surface styling, and elimination of redundant selection hints (ATT-2962, amending `REQ-MAP-031`):
1. *Clean Surface & Border Styling*:
• The outer container card SHALL use `MaterialTheme.colorScheme.surface` with `overlayAlpha` (crisp white in light theme) instead of `surfaceVariant`..
• The outer card SHALL apply a subtle border: `BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))`..
• Inner direction group cards SHALL use `MaterialTheme.colorScheme.surface` or `surfaceContainerLow` with `BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))` and `RoundedCornerShape(12.dp)`..
2. *Directional Route Grouping*:
• The card SHALL group `decisionState.branches` by `ForkDirection` (`branches.groupBy { it.direction }`)..
• For each direction group: Render a single direction indicator column on the left containing the direction arrow icon and localized heading label (`R.string.fork_direction_*`) in `labelSmall` bold primary color; render a vertical divider separating the direction column from the routes column; in the right-hand column, stack all candidate routes matching that direction, with a 0.5dp `HorizontalDivider` between multiple routes under the same heading..
3. *Redundant Hint Text Removal*:
• The selection hint text (`R.string.fork_select_hint` / "Tippen zum Auswählen" / "Tap to select") SHALL be completely removed from candidate route rows, dedicating the entire row width to the route title (`maxLines = 1`, `TextOverflow.Ellipsis`) and distance/elevation metrics..
4. *Interaction & Callback Preservation*:
• Each route row item SHALL remain interactive, invoking `onRouteSelected(branch.routeId)` upon user click..
• The dismissal close button SHALL invoke `onDismiss()`..
5. *9-Language Localization Parity*:
• Ensure all utilized string resources exist across EN, DE, ES, FR, IT, JA, NL, PL, PT..
6. *Previews & Invariants*:
• Provide `@Preview` composables for light and dark themes; maintain 100% full clean-room unit test pass rate.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-MAP-031` (*In-Ride Fork Route Decision Card HUD Layout & Selection Interactions*, Sprint 2026-41.2, ATT-2481).
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-41.2 (`ATT-2481`).
3. *Root Reason for Existing Formulation*: The original design was a flat list where every route candidate was displayed in a separate card repeating the direction icon and adding a verbose tap hint.
4. *Preservation of Core Invariants*: `ForkDecisionState` consumption, `onRouteSelected` callback, `onDismiss` callback, animated entrance/exit, and countdown distance header formatting remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active fork decision card rendered in the Cockpit HUD,
• *When* inspecting candidate route rows,
• *Then* the "Tippen zum Auswählen" / "Tap to select" hint text is NOT displayed, allowing maximum width for route names and metrics.
• *Given* candidate routes diverging at an approaching fork (e.g. 2 routes straight ahead and 1 route turning right),
• *When* the fork decision card renders,
• *Then* the routes are grouped under their respective directions (one unified group for "Gerade aus" containing both straight routes, and a distinct group for "Rechts" containing the right-turning route).
• *Given* the fork decision card rendered on screen,
• *When* inspecting the visual theme,
• *Then* the card container and inner groups render with `MaterialTheme.colorScheme.surface` and subtle light borders (`outlineVariant`), with no grey container styling.

**Invariants**: Selection and dismiss callbacks, countdown distance header formatting, and 100% test pass rate MUST NOT be broken.

### REQ-UI-331: Live Segment Elevation Profile Dynamic Position Marker & Scrub Distance Synchronization [Verified]
- **Tests**: `TST-UI-291`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `LIveSegmentSheet.kt`, `MapDetailLayout.kt`
- **Rationale**: The system SHALL propagate the athlete's real-time distance along an active segment to the elevation profile chart within `LiveSegmentSheet.kt` and synchronize scrub distance clearance in `MapDetailLayout.kt` (ATT-2967):
1. *Scrub Distance Evaluation (`LIveSegmentSheet.kt`)*:
• `LiveSegmentSheet` SHALL evaluate `val externalScrubDistance: Double?` based on `liveSegment.liveData.segmentStatus`:
- `LiveSegmentStatus.ON_SEGMENT` or `LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH`: evaluates to `liveSegment.liveData.distanceOnSegment_raw`..
- `LiveSegmentStatus.FINISHED`: evaluates to `liveSegment.staticData.summary.distance.toDouble().coerceAtLeast(liveSegment.liveData.distanceOnSegment_raw)`..
- `LiveSegmentStatus.APPROACHING` or `LiveSegmentStatus.FAR_FAR_AWAY`: evaluates to `null`..
• `LiveSegmentSheet` SHALL supply `externalScrubDistance = externalScrubDistance` to `MapDetailLayout`..
2. *Scrub Distance Synchronization & Clearance (`MapDetailLayout.kt`)*:
• `MapDetailLayout` SHALL execute `LaunchedEffect(externalScrubDistance) { selectedDistance = externalScrubDistance }` without null gating, ensuring that transitioning to `null` (e.g. returning to approaching status or sheet reset) immediately clears any active scrubber marker from the elevation profile canvas..
3. *Dynamic Elevation Position Marker Rendering*:
• When `selectedDistance` is non-null, the elevation profile chart components (`ElevationProfileChart.kt` / `ElevationView.kt`) SHALL render the location pin / scrubber dot at the indicated metric distance along the segment profile curve..
4. *Preservation of System Invariants*:
• Clamped wrap-content height without viewport expansion (`REQ-UI-270`, `TST-UI-229`), suppressed map and zoom controls (`REQ-UI-197`), unified surface background (`REQ-UI-196`), and 100% full-suite unit test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-331`), enhancing `LiveSegmentSheet.kt` (`REQ-UI-196` / `REQ-UI-270`) and leveraging `MapDetailLayout` scrub distance synchronization (`REQ-UI-302`).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.15 (`ATT-1644` / `ATT-1735`) and Sprint 2026-40.16 (`ATT-2231` / `ATT-2583`).
3. *Root Reason for Existing Formulation*: `LiveSegmentSheet` was built prior to the introduction of `externalScrubDistance` on `MapDetailLayout`, so `externalScrubDistance` was omitted and defaulted to `null`, preventing the elevation profile from showing athlete position.
4. *Preservation of Core Invariants*: Header structure, 0.5dp divider, metric details, clamped bottom sheet height, and full-suite test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout tracking a live segment in status `ON_SEGMENT` with 152m completed,
• *When* viewing `LiveSegmentSheet`,
• *Then* `externalScrubDistance` is passed as 152.0m, and the elevation profile renders a location marker pin at the 152m point on the profile curve.
• *Given* an approaching live segment in status `APPROACHING`,
• *When* viewing `LiveSegmentSheet`,
• *Then* `externalScrubDistance` is `null`, and no location marker is rendered on the elevation profile ahead.
• *Given* a completed live segment in status `FINISHED`,
• *When* viewing `LiveSegmentSheet`,
• *Then* `externalScrubDistance` snaps to the end of the segment profile.

**Invariants**: Clamped sheet height, surface colors, suppressed zoom controls, and 100% unit test pass rate MUST NOT be broken.

### REQ-UI-332: Workout Details Elevation Profile Display & Time-Domain Telemetry Ingestion for Trackless Workouts with Altitude Data [Verified]
- **Tests**: `TST-UI-292`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
• `showElevationProfile` SHALL be evaluated as:
`showElevationProfile = (hasGpsTrack
- **Rationale**: The system SHALL extract altitude telemetry for trackless workouts and display the elevation profile chart plotted against the Time domain in the workout details aftermath screen (`TrackOnMapScreen.kt`) when altitude data is present (ATT-3046, amending `REQ-UI-235`):
1. *Decoupled Visibility Gating (`TrackOnMapScreen.kt`)*:
• In `TrackOnMapScreen.kt`, the system SHALL compute:
`val hasTracklessAltitude = !hasGpsTrack && (activeScrubPath?.any { it.altitude != 0.0 } == true)`
`val hasAltitudeData = workoutData.minAltitude != null

### REQ-UI-333: Entity Detail Sheet Scaffold & Climb Popup Harmonization via MapDetailLayout [Verified]
- **Tests**: `TST-UI-293`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EntityDetailSheetScaffold.kt`, `ClimbDetailSheet.kt`, `SegmentDetailSheet.kt`, `RouteDetailSheet.kt`
- **Rationale**: The system SHALL eliminate duplicated bottom sheet scaffolding across entity popups and harmonize `ClimbDetailSheet.kt` with `SegmentDetailSheet.kt` and `RouteDetailSheet.kt` by introducing `EntityDetailSheetScaffold` and backing `ClimbDetailSheet` with `MapDetailLayout` (ATT-3053, amending `REQ-UI-315` and `REQ-UI-300`):
1. *Shared Entity Detail Sheet Scaffold (`EntityDetailSheetScaffold.kt`)*:
• The system SHALL provide a reusable composable `EntityDetailSheetScaffold` in `com.atrainingtracker.trainingtracker.ui.components.core` (or `ui.map`).
• The scaffold SHALL encapsulate Material 3 `ModalBottomSheet` configured with `rememberModalBottomSheetState(skipPartiallyExpanded = true)`, `shape = BottomSheetDesign.SheetShape`, `tonalElevation = BottomSheetDesign.SheetTonalElevation`, `containerColor = MaterialTheme.colorScheme.surface`, `dragHandle = null`, and `modifier = modifier.fillMaxHeight()`.
• The scaffold SHALL render a persistent floating circular dismiss close button (`IconButton` wrapping a 2.dp elevated `Surface` with `CircleShape`, `Icons.Default.Close`, semi-transparent surface background) anchored at `Alignment.TopEnd` with `8.dp` padding and `zIndex(10f)`.
• The scaffold SHALL expose slot `content: @Composable BoxScope.() -> Unit`.
• `SegmentDetailSheet.kt`, `RouteDetailSheet.kt`, and `ClimbDetailSheet.kt` SHALL all delegate to `EntityDetailSheetScaffold`, eliminating redundant bottom sheet scaffolding boilerplate.
2. *Climb Screen & Detail Architecture (`ClimbOnMapScreen.kt` & `ClimbDetailSheet.kt`)*:
• `ClimbDetailSheet` SHALL host `ClimbOnMapScreen` inside `EntityDetailSheetScaffold`.
• `ClimbOnMapScreen` SHALL be powered by `MapDetailLayout` with:
a. *Structured Header (`ClimbHeader`)*: Displays sport icon (`BSportType.BIKE.iconResId`), climb name (titleLarge, bold), `ClimbCategoryChip` indicating UCI classification, route counter badge (e.g. `climb_route_counter`, `routeIndex` of `totalRouteClimbs`), and climb start offset along route (`routes_climb_start_at`).
b. *Structured Details (`ClimbDetails`)*: 3-row metric presentation conforming to `SegmentDetails` design tokens: Row 1 displays climb distance (`ic_distance`), Row 2 displays average grade (`routes_climb_avg_grade` / `ic_grade`) and max grade (`routes_climb_max_grade` / `climb_max_grade_label`), Row 3 displays elevation gain (`climb_remaining_elevation` / `ic_ascent`) and min/max elevation (`ic_altitude` / `ic_altitude_min` / `ic_altitude_max`).
c. *Divider*: 0.5dp `HorizontalDivider` with `outlineVariant` color between header and details.
d. *MapDetailLayout Viewport*: Collapsible embedded map with `MapZoomFocus.EXPLICIT_BOUNDS`, `initialBounds = calculateClimbBounds(climb)`, category-colored climb polyline, start/summit markers, and the interactive `ElevationProfile` chart with scrubbing synchronization.
e. `useStatusBarsPadding = false`.
3. *State Isolation & Seamless Dismissal*:
• Dismissing the climb sheet via top-right close button, downward drag gesture, scrim tap, or system back press SHALL dismiss the sheet and return to `RouteOnMapScreen` without clearing route selection, route bounds, or scroll position.
4. *Preservation of System Invariants*:
• `calculateClimbBounds` bounding box logic with single-point fallback, category chip rendering, 9-language localization parity, and 100% full clean-room unit test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-315` (*Harmonized Visual & UX Architecture for Climb, Segment, and Route Detail Bottom Sheets via MapDetailLayout*, Sprint 2026-41.6, ATT-2860) and `REQ-UI-300` (*Dedicated Route Climb Detail View with Focused Map and Zoomed Isolated Elevation Profile*, Sprint 2026-41.3, ATT-2511).
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-41.3 (`ATT-2511`) and refined in Sprint 2026-41.6 (`ATT-2860`).
3. *Root Reason for Existing Formulation*: In ATT-2860, `SegmentDetailSheet` and `RouteDetailSheet` were unified via `MapDetailLayout`, but `ClimbDetailSheet` was left with the obsolete 3-card stack and duplicated scaffolding. The user explicitly requested: "The popup for the climbs must be (alomst) similr to the popup of the segments. From my point of view, the climb popup should share some of the code form the segment (and route) popup. I.e., I think there should be a common base class...".
4. *Preservation of Core Invariants*: Bounding box calculations, UCI category chips, dismiss behavior, and 100% test pass rate remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete tapping a recognized climb in `RouteOnMapScreen`,
• *When* `ClimbDetailSheet` opens,
• *Then* it is presented using `EntityDetailSheetScaffold` and `MapDetailLayout`, displaying harmonized `ClimbHeader`, `ClimbDetails`, interactive map, and scrubbable elevation profile.
• *Given* `SegmentDetailSheet`, `RouteDetailSheet`, and `ClimbDetailSheet`,
• *When* inspecting their source code and structure,
• *Then* all three leverage the common `EntityDetailSheetScaffold`.
• *Given* an athlete scrubbing the elevation profile of `ClimbDetailSheet`,
• *When* moving the scrubber,
• *Then* the elevation profile updates smoothly and synchronizes with the map marker.
• *Given* any entity detail sheet (Climb, Segment, Route),
• *When* tapping the top-right close button,
• *Then* the sheet dismisses cleanly and restores parent screen state.

**Invariants**: Bounding box calculations, UCI category colors, dismiss behavior, and 100% unit test pass rate MUST NOT be broken.

### REQ-UI-334: Newly Discovered Sensor Prioritization, Visual Highlight Badge & Discovery Feedback Notification [Verified]
- **Tests**: `TST-UI-294`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: Prioritize newly discovered sensors at top of scan list with NEW badge, highlight border, snackbar notification, haptic feedback, and auto-scroll.
- **Rationale**: The system SHALL prioritize newly discovered wireless sensors at the very top of the scan list during active pairing discovery, distinguish the card with a prominent "NEW" badge chip and primary highlight border, and provide immediate sensory feedback via in-app snackbar notification, haptic vibration, and auto-scroll to the top item (ATT-3099, amending `REQ-UI-291` and complementing `REQ-UI-140`):
1. *Newly Discovered Sensor Domain Model & Pipeline Flagging (`DeviceData.kt` & `GetMergedDevicesUseCase.kt`)*:
• The system SHALL extend `DeviceUiData` in `DeviceData.kt` with property `val isNewlyDiscovered: Boolean = false`.
• In `GetMergedDevicesUseCase.kt`, when mapping devices from `allDevices` and `newlyFoundDevicesIds`: `val isNewlyDiscovered = foundIds.contains(knownDevice.id)`. `DeviceUiData` instances mapped for newly discovered sensors during the active scanning session SHALL have `isNewlyDiscovered = true`.
2. *Top-Of-List Scan Priority Sorting & Stable List Ordering (`DeviceListViewModel.kt`)*:
• In `DeviceListViewModel.getFilteredDevices(spec: DeviceFilterSpec)`, the device list SHALL be ordered using deterministic multi-tier prioritization:
a. Primary tier: Newly discovered devices (`isNewlyDiscovered == true`) sorted at the top of the list (index 0). Multiple newly discovered devices SHALL be sorted in recency order (most recently discovered first, e.g. reverse index in `foundIds`).
b. Baseline tier: Remaining known / idle / paired devices SHALL be sorted stably and deterministically by device name (case-insensitive alphabetical order) with device ID as tie-breaker. The system SHALL NOT sort non-new devices by `isConnected`, guaranteeing that cards remain strictly stable and do not jump when pairing or connection states change (ATT-5660).
3. *Visual Highlighting & "NEW" Badge Chip (`DeviceItem.kt`)*:
• When `device.isNewlyDiscovered == true`, `DeviceItem` SHALL display:
a. A prominent "NEW" badge chip (`devices_newly_discovered_badge`) next to the device name/LED indicator.
b. An active highlight border around the card: `BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))`.
4. *Immediate Sensor Discovery Feedback & Auto-Scroll (`DevicesTabbedViewModel.kt`, `DevicesTabbedScreen.kt`, `DeviceListScreen.kt`)*:
• `DevicesTabbedViewModel` SHALL observe device discovery (via `BANALService.NEW_DEVICE_FOUND_INTENT` or `newlyFoundDevicesIds` state flow transitions).
• Upon detecting a newly discovered sensor, `DevicesTabbedViewModel` SHALL emit a single-shot event `newDeviceDiscoveredEvent: SharedFlow<String>` containing the localized name of the discovered device.
• In `DevicesTabbedScreen.kt` (and/or `DeviceListScreen.kt`), observing `newDeviceDiscoveredEvent` SHALL:
a. Display an in-app Material 3 `Snackbar` via `SnackbarHostState`: localized `devices_new_sensor_discovered` ("Neuer Sensor gefunden: %1$s" / "New sensor discovered: %1$s").
b. Perform haptic vibration feedback via `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)`.
c. Smoothly auto-scroll the `LazyListState` to index 0 (`scrollState.animateScrollToItem(0)`) so the newly prioritized device is brought immediately into view.
5. *100% 9-Language Localization Parity*:
• String resources `devices_newly_discovered_badge` ("NEU" / "NEW") and `devices_new_sensor_discovered` ("Neuer Sensor gefunden: %1$s" / "New sensor discovered: %1$s") SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero missing entries, matching format specifiers (`%1$s`), and literal `\n` linebreaks.
6. *Preservation of System Invariants*:
• Zero disruption to pairing toggles (`onPairToggle`), device configuration dialogs (`EditDeviceDialog`), protocol/type filtering, or background BLE/ANT+ GATT connection and telemetry streaming.
• 100% full clean-room unit test pass rate across all unit tests.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-291` (*Sensors View Device Inventory Preservation During Active Pairing Scanning*, ATT-2626) and complements `REQ-UI-140` (*Unified Sensors Tabbed Management*); refined by `REQ-UI-334.2` (ATT-5660).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2626` (commit `ec62b083`) established tab filter preservation; `ATT-3099` (commit `7df8d5b0`, Sprint `2026-41.8`) introduced newly discovered sensor prioritization and `isConnected` secondary sorting; refined in `ATT-5660` (Sprint `2026-45.9`) to remove `isConnected` sorting.
3. *Root Reason for Existing Formulation*: Originally, sorting connected sensors ahead was assumed to provide better visibility. However, toggling pairing on internal smartphone sensors (GPS, Barometer, Google Fused) immediately mutates `isConnected`, causing cards to jump across the screen right beneath the user's touch. Dedicated filtering in `DeviceFilterType.CONNECTED` already handles connected-only views.
4. *Preservation of Core Invariants*: Top-of-list `isNewlyDiscovered` prioritization preserved; `CONNECTED` tab filtering preserved; stable alphabetical sorting guarantees zero jumping.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete in `DevicesTabbedScreen` actively searching for sensors with an existing list of 10 known devices,
• *When* a new BLE or ANT+ sensor is discovered by `BANALService`,
• *Then* `isNewlyDiscovered` evaluates to `true` for that sensor in `DeviceUiData`,
• *And* `DeviceListViewModel` sorts the new sensor at index 0 (top of the list),
• *And* `DeviceItem` renders the card with a "NEW" badge chip and primary highlight border,
• *And* a non-blocking Snackbar displays "New sensor discovered: [Device Name]" (or localized equivalent),
• *And* haptic feedback is triggered,
• *And* the list smoothly scrolls to index 0.
• *Given* an athlete stopping search or navigating away,
• *When* viewing the "Bekannt" or "Gekoppelt" tabs,
• *Then* known and paired devices retain their normal display and ordering once scanning stops.
• *Given* an athlete in the "Bekannt" or "Gekoppelt" tab,
• *When* toggling pairing on any device,
• *Then* the device card remains at its exact position in the list without jumping due to connection state changes (ATT-5660).
• *Given* all 9 supported application locales,
• *When* evaluating string resources `devices_newly_discovered_badge` and `devices_new_sensor_discovered`,
• *Then* all 9 locales contain valid non-empty entries with matching format specifiers.

**Invariants**: Background GATT/ANT+ device discovery, pairing toggle states, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-335: Dynamic Map-Aware Navigation Hints Overlay Anchoring over Embedded Map View [Verified]
- **Tests**: `TST-UI-295`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorGridScreen.kt`, `SensorGridScreenRouteIntegrationTest.kt`
- **Rationale**: The system SHALL dynamically anchor the active navigation hints overlay (`TurnPromptBanner`, `ReturnNavigationHud`, `ForkDecisionCard`) based on the presence of an embedded map view within the active tracking tab (ATT-3100, amending `REQ-UI-324` and `REQ-MAP-031`):
1. *Map-Present Navigation Overlay Anchoring (`SensorGridScreen.kt`)*:
• In `SensorGridScreen.kt`, when `state.mapState.showMap == true`, the navigation hints overlay SHALL be anchored inside the map container Box at `Alignment.TopCenter`, situated directly below the in-flow scrollable sensor grid.
• The topmost sensor fields SHALL remain 100% unobstructed and visible during all navigation prompt presentations.
2. *Map-Absent Navigation Overlay Anchoring (`SensorGridScreen.kt`)*:
• When `state.mapState.showMap == false` (e.g. pure sensor grid tabs), the navigation hints overlay SHALL remain anchored at `Alignment.TopCenter` of the root tracking layout `Box`, floating over the top of the tab.
3. *Unified Navigation Hints Overlay Architecture*:
• The system SHALL encapsulate `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard` inside a reusable composable `NavigationHintsOverlay`.
• The placement between map-container overlay and root-container overlay SHALL be strictly mutually exclusive to prevent duplicated rendering.
4. *Zero-Telemetry-Displacement Invariant*:
• Navigation cue appearance, distance countdowns, and dismissals SHALL float over the host container as non-displacing overlays without altering sensor field dimensions, scroll state, or map canvas boundaries.
5. *Preservation of System Invariants*:
• Isolated recomposition barrier (`@Immutable TrackingMapState`), cue transparency tokens (`tuningConfig.navigationCueTransparency`), dismiss durations, and 100% clean-room unit test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-324` (*Align turn-by-turn navigation hints UI with updated design guidelines and floating top-center overlay*, Sprint 2026-41.6, ATT-2941) and `REQ-MAP-031` (*TurnPromptBanner Turn-by-Turn Navigation Prompts & Direction HUD*, ATT-2874).
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-41.6 (`ATT-2941`, commit `68b48574`).
3. *Root Reason for Existing Formulation*: ATT-2941 moved navigation cues to a top-level Box overlay to prevent in-flow layout displacement of sensor tiles. However, unconditionally anchoring at the top of the entire screen caused cues to cover the top row of sensor fields on tabs that contain an embedded map view.
4. *Preservation of Core Invariants*: Floating non-displacing overlay behavior, transparency tuning, dismiss timers, and full-suite test pass rate remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active tracking tab with `showMap == true` and sensor fields above the map,
• *When* a turn prompt, return navigation HUD, or fork decision is active,
• *Then* the navigation overlay renders anchored directly at the top of the map container below the sensor fields,
• *And* all sensor grid tiles remain completely visible.
• *Given* an active tracking tab with `showMap == false`,
• *When* navigation hints are displayed,
• *Then* the overlay renders at the top of the screen (`Alignment.TopCenter` of the root Box).
• *Given* any tracking tab,
• *When* navigation prompts appear or dismiss,
• *Then* neither the sensor grid nor the map view experience layout displacement or resizing.

**Invariants**: Zero telemetry displacement, map recomposition barrier, and 100% test pass rate MUST NOT be broken.

### REQ-UI-336: Cockpit Sensor Field In-Ride Direct Editing via Long-Press Gesture [Verified]
- **Tests**: `TST-UI-296`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorGridScreen.kt`, `SensorFieldView.kt`
- **Rationale**: The system SHALL trigger the sensor field configuration dialog (`EditSensorFieldDialog`) when an athlete performs a long-click gesture on any sensor field tile during active workout tracking (`ScreenMode.TRACKING`) (ATT-3101):
1. *Tracking Mode Long-Click Delegation (`SensorGridScreen.kt`)*:
• In `SensorGridScreen.kt`, the sensor field rendering loop for tracking and preview modes SHALL wire `onEdit` to `gridActions.onEditField(fieldState)`.
• In `SensorFieldView.kt`, performing a long-press gesture (`onLongClick`) when `screenMode == ScreenMode.TRACKING` SHALL trigger `onEdit()`, thereby invoking `gridActions.onEditField(fieldState)` and opening `EditSensorFieldDialog` for the targeted sensor field without interrupting workout tracking.
2. *Single-Tap Safety Invariant (Zero Accidental Triggers)*:
• In `ScreenMode.TRACKING`, single-tap (`onClick`) SHALL remain an intentional no-op to strictly prevent accidental HUD dialog interruptions during vigorous activities (cycling, running).
3. *Tile Reordering & Deletion Gating Invariant*:
• Drag-and-drop / Pick-and-Place reordering (`onStartMove = {}`) and tile deletion (`onDelete = {}`) SHALL remain strictly disabled in `ScreenMode.TRACKING` and `ScreenMode.PREVIEW`.
4. *Configuration Mode Intactness*:
• In `ScreenMode.CONFIGURATION`, single-tap SHALL continue to trigger `onEdit()` (opening the configuration dialog) and long-press SHALL continue to trigger `onStartMove()` (initiating Pick-and-Place tile reordering per `REQ-UI-200`).
5. *Preservation of System Invariants*:
• Zero telemetry displacement, isolated map recomposition barrier, and 100% clean-room unit test pass rate MUST be strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete is on the active tracking screen (`ScreenMode.TRACKING`),
• *When* the athlete performs a long click on any sensor field tile,
• *Then* the sensor field configuration dialog is displayed, allowing selection of sensor type, font/view size, and filtering.
• *Given* the configuration dialog opened via long click in tracking mode,
• *When* the user changes a parameter (e.g. changes sensor type or view size) and confirms,
• *Then* the sensor field updates immediately in the live HUD and changes are saved.
• *Given* the live tracking screen,
• *When* the user single-taps a sensor field,
• *Then* no edit dialog is opened (single-tap remains dedicated to no-op behavior, avoiding accidental triggers).
• *Given* the tracking screen in `ScreenMode.CONFIGURATION`,
• *When* clicking or long-clicking sensor fields,
• *Then* single-tap opens edit dialog and long-click initiates field drag/move as originally designed.

**Invariants**: Zero accidental trigger invariant, zero telemetry displacement, and 100% test pass rate MUST NOT be broken.

### REQ-UI-337: Elimination of Lingering Grey Container Surfaces, Zero Tonal Elevation and Transparent Surface Harmonization on In-Ride Fork Decision Card [Verified]
- **Tests**: `TST-UI-297`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ForkDecisionCard.kt`, `ForkDecisionCardTest.kt`
- **Rationale**: The system SHALL eliminate lingering grey container surfaces, enforce strict zero tonal elevation across all container surfaces, and harmonize opacity and surface coloring in the in-ride fork decision HUD card (`ForkDecisionCard.kt`) (ATT-3104, amending `REQ-UI-330` and complementing `REQ-MAP-031`):
1. *Strict Zero Tonal Elevation Across All Containers (`ForkDecisionCard.kt`)*:
• The outer container SHALL NOT apply tonal elevation (`tonalElevation = 0.dp`), completely eliminating implicit Material 3 elevation tinting (`surfaceColorAtElevation`) that degrades pure white (`#FFFFFF`) in light mode and deep pitch black (`#000000`) in AMOLED mode. The outer container SHALL be implemented via `Surface` with `shadowElevation = 6.dp` and explicit `tonalElevation = 0.dp`, cleanly decoupling shadow depth from surface elevation tinting.
• The nested directional group container (`ForkDirectionGroup`) SHALL strictly enforce `tonalElevation = 0.dp`, eliminating the previously hardcoded `1.dp` elevation overlay tint.
2. *Transparent & Harmonized Surface Coloring (`ForkDecisionCard.kt`)*:
• The nested directional group container (`ForkDirectionGroup`) SHALL match the outer card surface and transparency via `color = MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha)`, eliminating opaque rectangular patch artifacts inside translucent card margins.
• In Light Mode, the card container, nested direction groups, and route rows SHALL render on clean pure white surfaces (`LightSurface = #FFFFFF`).
• In Dark Mode, all containers and rows SHALL render on deep dark surfaces (`DarkSurface = #16161A`).
• In AMOLED Mode, all containers and rows SHALL render on deep pitch black surfaces (`AmoledSurface = #000000`).
• All nested rows (`ForkRouteRow`), direction columns, and route columns SHALL remain free of `surfaceVariant` or conflicting background fills.
3. *Preservation of Structural Elements & Visual Dividers*:
• Subtle vertical and horizontal dividers separating directions and candidate routes SHALL use `MaterialTheme.colorScheme.outlineVariant` with subdued alpha (`0.4f` - `0.5f`), preserving crisp visual separation without adding grey container backgrounds.
• Outer container border (`BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))`) and inner group border (`BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))`) SHALL be preserved.
4. *Preservation of System Invariants*:
• Directional grouping (`decisionState.branches.groupBy { it.direction }`), route selection callback (`onRouteSelected`), dismiss callback (`onDismiss`), countdown distance, and 100% 9-language localization parity MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-330` (*In-Ride Fork Route Decision Card HUD Layout & Selection Interactions*, Sprint 2026-41.6, ATT-2962) and complements `REQ-MAP-031` (*TurnPromptBanner Turn-by-Turn Navigation Prompts & Direction HUD*).
2. *Historical Origin & Commit Trace*: Sprint 2026-41.6 (`ATT-2962`, commit `0f24b2c0`).
3. *Root Reason for Existing Formulation*: During ATT-2962 construction, `tonalElevation = 1.dp` was assigned to `ForkDirectionGroup`, and `CardDefaults.cardElevation(defaultElevation = 6.dp)` was retained on the outer card. In Compose Material 3, tonal elevation adds an elevation color tint (`surfaceTint`) overlay on top of the surface, creating visible grey/tinted container rectangles in both light mode and dark/AMOLED modes.
4. *Preservation of Core Invariants*: Directional grouping, route selection, and HUD glanceability are preserved while visual contrast and sunlight legibility are restored.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete navigating with candidate routes approaching a fork in Light Mode,
• *When* `ForkDecisionCard` is displayed,
• *Then* the outer container and nested direction groups render on pure white surfaces without grey elevation tinting.
• *Given* an athlete riding at night with Dark or AMOLED theme active,
• *When* `ForkDecisionCard` is displayed,
• *Then* all nested rows, direction groups, and container backgrounds render in pure black (`#000000`) without grey washed-out elevation overlays.
• *Given* an active `overlayAlpha` setting (e.g. 0.85f),
• *When* `ForkDecisionCard` renders over the map or telemetry grid,
• *Then* the inner direction groups and the outer card container share the identical alpha opacity, avoiding opaque patches.

**Invariants**: Zero tonal elevation tint invariant, zero surfaceVariant invariant, zero displacement invariant, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-338: Climb Detail Elevation Profile Normalization to Local Climb Distance Domain [Verified]
- **Tests**: `TST-UI-298`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `Climb.kt`, `ClimbOnMapScreen.kt`, `ClimbDetailSheet.kt`
- **Rationale**: The system SHALL normalize the climb path distance coordinate domain to start at 0.0 meters when displaying an individual climb elevation profile and scrubber in `ClimbOnMapScreen` and `ClimbDetailSheet` (ATT-3105, complementing `REQ-UI-300`, `REQ-UI-315`, and `REQ-UI-333`):
1. *Path Normalization Domain (`Climb.kt`)*:
• The system SHALL provide a normalized climb path representation (`Climb.normalizedPathPoints`) wherein all `PathPoint` instances are offset by the climb's initial distance (`pathPoints.first().distance`), such that the first point has `distance = 0.0` and the final point has `distance = climb.distanceMeters` (or `last().distance - first().distance`).
• Latitude, longitude, altitude, and any associated telemetry or speed values SHALL remain strictly unmodified.
• If `pathPoints` is empty or already starts at `0.0`, the system SHALL return the points safely without mutation.
2. *Full-Width Elevation Profile Rendering (`ClimbOnMapScreen.kt`)*:
• `ClimbOnMapScreen` SHALL supply `climb.normalizedPathPoints` as the `activeScrubPath` to `MapDetailLayout`.
• The embedded `ElevationProfile` chart X-axis SHALL span exactly from base ($0.0\text{ m}$) to summit ($d_{\text{summit}}$), utilizing 100% of the horizontal chart width.
• The profile SHALL NOT be compressed into a sliver at the far right edge of the chart when the climb originated from a later section of a parent route.
3. *Interactive Scrubbing & Map Pin Synchronization*:
• When scrubbing the normalized elevation profile, `selectedDistance` ($0.0 \le s \le d_{\text{summit}}$) SHALL resolve to the corresponding geographical `PathPoint` via `TelemetryMetricUtils.findNearestPoint`.
• The active scrubbing pin on the map view SHALL accurately indicate the athlete's scrubbed location along the physical climb polyline.
4. *Preservation of Global Route Context in Header*:
• `ClimbHeader` SHALL continue to display the global route offset (e.g. "Start at 27.4 km") via `climb.pathPoints.firstOrNull()?.distance`, preserving the parent route orientation.
• Global route profiles rendered in `RouteOnMapScreen` SHALL remain unaffected.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Complements `REQ-UI-300` (*Climb Detail Sheet Visual Contract*), `REQ-UI-315` (*Harmonized Route & Climb Detail HUD*), and `REQ-UI-333` (*EntityDetailSheetScaffold Architecture*).
2. *Historical Origin & Commit Trace*: Sprint 2026-41.6 (`ATT-3053`). In ATT-3053, `ClimbOnMapScreen` and `ClimbDetailSheet` migrated to `MapDetailLayout` to harmonize with `SegmentOnMapScreen`. However, unlike segments (which are standalone tracks starting at 0m), climbs detected from routes carry route cumulative distances (e.g. 27.38 km to 28.10 km). `MapDetailLayout` assumes `activeScrubPath` starts at 0, resulting in the elevation profile being squished into the final 3% of the chart width.
3. *Root Reason for Existing Formulation*: Historical standalone canvas (`ClimbDetailElevationProfile`) explicitly normalized distances via `(p.distance - startDist) / totalDist`. This normalization was omitted during the `MapDetailLayout` transition.
4. *Preservation of Core Invariants*: Header route offset indicator, map polyline bounds, and live tracking calculations in `LiveClimbsRepository` are preserved without regressions.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete inspecting a 722 m climb located at km 27.38 of a 28.10 km route,
• *When* `ClimbDetailSheet` or `ClimbOnMapScreen` is displayed,
• *Then* the elevation profile chart spans from 0 m to 722 m across 100% of the chart width.
• *Given* an athlete scrubbing their finger across the climb elevation profile,
• *When* the scrub distance changes from 0 to 722 m,
• *Then* the scrub marker on the map smoothly follows the climb polyline from start to summit.
• *Given* a climb with non-zero start distance on a route,
• *When* `ClimbHeader` renders,
• *Then* the "Start at 27.4 km" badge remains visible and accurate.

**Invariants**: Zero coordinate distortion invariant, full chart width coverage invariant, header route offset invariant, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-339: Dynamic Cockpit Weight Allocation & Flexible Vertical Expansion for Elevation Profile when Map is Disabled [Verified]
- **Tests**: `TST-UI-299`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorGridScreen.kt`, `ElevationProfile.kt`
- **Rationale**: The system SHALL dynamically allocate vertical layout weight and support flexible vertical canvas expansion for the elevation profile on the live tracking screen (`SensorGridScreen.kt`) when the embedded map is disabled (`showMap == false` and `showElevationProfile == true`) (ATT-3107, complementing `REQ-UI-197`, `REQ-UI-225`, and `REQ-UI-326`):
1. *Flexible Sensor Grid Weight Constraint (`SensorGridScreen.kt`)*:
• The scrollable sensor grid `Column` SHALL declare `Modifier.weight(1f, fill = false)`. When sensor tiles consume minimal vertical space (1–2 rows), the column SHALL wrap its content height naturally without forced stretching. When sensor tiles consume extensive vertical space (4–6 rows), the column SHALL be constrained to available proportional height and scroll smoothly without crowding out bottom cockpit views.
2. *Dynamic Elevation Profile Weight Allocation (`SensorGridScreen.kt`)*:
• When `showMap == false` and `showElevationProfile == true` with `state.pathPoints.size >= 2`, the elevation profile wrapping `Surface` SHALL declare `Modifier.weight(1f)`, expanding to utilize the generous vertical space vacated by the map.
• When `showMap == true`, the elevation profile SHALL remain unweighted with its standard top padding, allowing the map to retain primary flexible weight (`weight(1f)`).
• When `state.pathPoints.size < 2`, the elevation profile SHALL NOT be rendered, preventing empty stretched canvas artifacts.
3. *Decoupled Canvas Height Expansion (`ElevationProfile.kt`)*:
• `ElevationProfile` SHALL accept an explicit parameter `fillParentHeight: Boolean = false`.
• When `fillParentHeight == false` (default across all list previews, summaries, and map sheets), the canvas SHALL maintain its fixed `totalCanvasHeight` (`height(totalCanvasHeight)`).
• When `fillParentHeight == true`, the canvas SHALL declare `defaultMinSize(minHeight = totalCanvasHeight).fillMaxHeight()`, dynamically expanding to fill the full available height of the weighted container ($\ge 120\text{dp}$).
4. *Preservation of System Invariants*:
• Ambient `NavigationHintsOverlay` positioning at `Alignment.TopCenter` when `showMap == false` (REQ-UI-335 / ATT-3100), map recomposition barrier isolation (REQ-UI-326), and 100% test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Complements `REQ-UI-197` (*Non-Overlapping Elevation Profile Vertical Layout*), `REQ-UI-225` (*Decoupled Telemetry Badge & Canvas Layout*), and `REQ-UI-326` (*TrackingMapContainer Recomposition Barrier*).
2. *Historical Origin & Commit Trace*: Sprint 2026-41.6 (`ATT-2944`). `TrackingMapContainer` was isolated with `weight(1f)`, but when `showMap == false`, no weight was assigned to `ElevationProfile`, causing the unconstrained sensor grid to push it into a 15–20dp clipped slit.
3. *Root Reason for Existing Formulation*: Historical tracking layout assumed the map was always visible when tracking. When map toggling was added, the elevation profile was not given flexible weight.
4. *Preservation of Core Invariants*: Sensor grid scrollability, unconstrained list preview height, and map + elevation profile coexistence are fully preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete tracking a workout with elevation profile enabled and map disabled,
• *When* `SensorGridScreen` is displayed in `ScreenMode.TRACKING`,
• *Then* the elevation profile expands to fill the remaining screen height ($\ge 120\text{dp}$) without vertical clipping.
• *Given* an athlete with 6 rows of sensor fields with map disabled and elevation profile enabled,
• *When* the screen renders,
• *Then* the sensor grid is vertically scrollable and does not squeeze the elevation profile off the screen.
• *Given* both map and elevation profile enabled,
• *When* the screen renders,
• *Then* the map takes the primary weighted space and the elevation profile renders below it at its standard adaptive height.

**Invariants**: Minimum vertical height $\ge 120\text{dp}$ invariant, list preview height preservation invariant, sensor grid scrollability invariant, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-340: LiveSegmentSheet Type-Safe Numeric Metric Evaluation on Segment Finish [Verified]
- **Tests**: `TST-UI-300`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `LIveSegmentSheet.kt`
- **Rationale**: The system SHALL evaluate the live segment completion scrub distance using the canonical unformatted numeric meter metric (`summary.distance_raw`) instead of parsing the localized formatted string representation (`summary.distance`), preventing fatal `NumberFormatException` upon segment finish in `LiveSegmentSheet.kt` (ATT-3108, superseding the evaluation formula in `REQ-UI-331`):
1. *Type-Safe Scrub Distance Evaluation on Finish (`LIveSegmentSheet.kt`)*:
• When `liveSegment.liveData.segmentStatus` is `LiveSegmentStatus.FINISHED`, `LiveSegmentSheet` SHALL evaluate `externalScrubDistance` as `liveSegment.staticData.summary.distance_raw.coerceAtLeast(liveSegment.liveData.distanceOnSegment_raw)` without performing any string-to-numeric parsing (`toDouble()`).
• When `liveSegment.liveData.segmentStatus` is `LiveSegmentStatus.ON_SEGMENT` or `LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH`, it SHALL evaluate to `liveSegment.liveData.distanceOnSegment_raw`.
• When `liveSegment.liveData.segmentStatus` is `LiveSegmentStatus.APPROACHING` or `LiveSegmentStatus.FAR_FAR_AWAY`, it SHALL evaluate to `null`.
2. *Contract & Layout Integrity (`LiveSegmentSheetContractTest.kt`, `LiveSegmentSheetLayoutTest.kt`)*:
• Contract tests SHALL enforce `distance_raw` evaluation on `LiveSegmentStatus.FINISHED`.
• Unit tests SHALL verify that when `SegmentSummary` has formatted distance strings (such as `"766 m"` or `"1.2 km"`), transitioning to `FINISHED` completes cleanly without throwing `NumberFormatException` and correctly resolves `externalScrubDistance` to `distance_raw`.
3. *Preservation of System Invariants*:
• Bottom sheet wrap-content height (`REQ-UI-270`), suppressed map and zoom controls (`REQ-UI-197`), unified surface background (`REQ-UI-196`), elevation scrubber pin alignment (`REQ-UI-331`), and 100% full-suite unit test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends and supersedes the completion evaluation formula of `REQ-UI-331` (*Live Segment Elevation Profile Dynamic Position Marker & Scrub Distance Synchronization*, Sprint 2026-40.16, ATT-2967).
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-40.16 (`ATT-2967`, commit `54fb2b23`).
3. *Root Reason for Existing Formulation*: In ATT-2967, `externalScrubDistance` was mapped to `summary.distance.toDouble()` under the mistaken assumption that `summary.distance` was numeric. In reality, `summary.distance` is the localized, formatted string representation (e.g. `"766 m"` or `"1.2 km"`), while `summary.distance_raw` is the canonical unformatted numeric meter `Double`. Calling `toDouble()` caused an immediate fatal `NumberFormatException` upon segment finish.
4. *Preservation of Core Invariants*: Elevation profile position pinning at the finish line, wrap-content sheet layout, and 100% test pass rate remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active live segment in `LiveSegmentSheet` with formatted `distance = "766 m"` and `distance_raw = 766.0`,
• *When* the segment status transitions to `LiveSegmentStatus.FINISHED`,
• *Then* `LiveSegmentSheet` calculates `externalScrubDistance` as `766.0` using `distance_raw` without throwing `NumberFormatException`.
• *Given* a finished live segment,
• *When* the elevation profile renders in `LiveSegmentSheet`,
• *Then* the elevation profile marker pin rests at the segment finish distance.

**Invariants**: Type safety, zero string-to-number parsing on formatted UI strings, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-341: Live Climb Cockpit Sheet Interactive Visual Design Optimization, Compose Previews & Ground Line Presentation [Verified]
- **Tests**: `TST-UI-301`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `LiveClimbSheet.kt`
- **Rationale**: The system SHALL refine the visual styling, graphical elevation profile baseline grounding, and developer tooling for the live climb cockpit bottom sheet (`LiveClimbSheet.kt`) (ATT-3103, complementing `REQ-MAP-027` and `REQ-UI-300`):
1. *Multi-State Jetpack Compose `@Preview` Tooling (`LiveClimbSheet.kt`)*:
• `LiveClimbSheet.kt` SHALL expose parameter-driven, theme-wrapped `@Preview` composables enabling instant visual inspection across all primary live climb states:
- `LiveClimbSheet_OnClimb_Preview`: Renders an active climb with non-zero progress fraction, remaining distance/elevation, instantaneous grade, and real-time rider position marker in Light Mode..
- `LiveClimbSheet_OnClimb_Dark_Preview`: Renders the active climb in Dark AMOLED Mode (`uiMode = Configuration.UI_MODE_NIGHT_YES`)..
- `LiveClimbSheet_Approaching_Preview`: Renders the approaching climb status badge with distance-to-start countdown..
- `LiveClimbSheet_Finished_Preview`: Renders the summit reached status with 0m remaining..
2. *Elevation Profile Graphical Refinements (`ClimbProfileCanvas`)*:
• `ClimbProfileCanvas` SHALL draw a subtle horizontal ground line along `y = height - bottomPadding` using `MaterialTheme.colorScheme.outlineVariant` with `0.4f` alpha, anchoring the gradient polygon..
• The profile canvas SHALL maintain a vertical height of `68.dp`, optimizing slope grade visual resolution while preserving compact bottom sheet height..
3. *Preservation of System Invariants*:
• All existing structural tokens (32dp Terrain icon, titleLarge bold climb name, labelLarge route counter, headlineMedium monospace grade, bodyLarge remaining elevation, unified surface background) and 100% 9-language localization parity MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Complements `REQ-MAP-027` (*Live ClimbPro Cockpit Bottom Sheet & Elevation Profile*) and `REQ-UI-300` (*Climb Detail Sheet Visual Contract*).
2. *Historical Origin & Commit Trace*: Sprint 2026-41.7 (`ATT-2947`). During the Sprint 2026-41.7 Joint Review, the human user requested an interactive visual design optimization session for the Live Climb cockpit sheet to fine-tune spacing, typography balance, and graphical presentation interactively.
3. *Root Reason for Existing Formulation*: `LiveClimbSheet.kt` was constructed without `@Preview` composables and without a visual baseline anchor line on the canvas.
4. *Preservation of Core Invariants*: Compact sheet height, surface color tokens, 32dp terrain icon, monospace grade text, and full-suite test pass rate remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `LiveClimbSheet.kt` inspected in Android Studio / IDE Compose preview,
• *When* viewing available previews,
• *Then* `@Preview` composables are available for Light Mode On-Climb, Dark AMOLED On-Climb, Approaching, and Finished states.
• *Given* `ClimbProfileCanvas` rendering a live climb,
• *When* the profile curve is drawn,
• *Then* a subtle outlineVariant baseline ground line anchors the bottom of the profile curve at `height - bottomPadding`.
• *Given* the active tracking screen,
• *When* `LiveClimbSheet` is displayed,
• *Then* all structural typography tokens and localization strings are 100% preserved.

**Invariants**: Visual hierarchy, compact bottom sheet height, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-342: Jetpack Compose Text Input Stylus Handwriting NoClassDefFoundError Runtime Interception & Resilience [Verified]
- **Tests**: `TST-UI-302`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ComposeHandwritingResilience.kt`, `TrainingApplication.java`, `app/build.gradle`
- **Rationale**: The system SHALL intercept and gracefully neutralize the Android platform class linkage failure (`NoClassDefFoundError` on `android.view.inputmethod.SelectGesture`) during Jetpack Compose text input focus on fragmented Android 14+ OEM platforms (ATT-3163, Epic ATT-235):
1. *Dynamic Runtime Interception (`ComposeHandwritingResilience.kt`)*:
• At application startup (`TrainingApplication.onCreate()`), the system SHALL invoke `ComposeHandwritingResilience.install()`.
• On devices reporting `Build.VERSION.SDK_INT >= 34`, `ComposeHandwritingResilience` SHALL verify availability of `android.view.inputmethod.SelectGesture` via `Class.forName()`.
• If `SelectGesture` cannot be resolved (or throws `ClassNotFoundException` / `NoClassDefFoundError`), `ComposeHandwritingResilience` SHALL update `StylusHandwriting_androidKt.isStylusHandwritingSupported` to `false` using memory reflection/Unsafe.
• When `isStylusHandwritingSupported` is false, Compose text input initialization (`EditorInfo_androidKt.update`) SHALL evaluate `shouldEnableStylusHandwriting` as `false`, safely skipping `EditorInfoApi34.setHandwritingGestures` and eliminating runtime linkage crashes.
2. *Architectural & Package Boundary Invariants*:
• The application SHALL NOT inject or shadow stub classes inside the reserved platform `android.*` namespace, preserving Android runtime integrity and classloader isolation.
• Text input, keyboard input methods (IME), selection, cut/copy/paste, and auto-correct across all Compose text fields in the application SHALL continue to function with zero latency or regression.
3. *Compose Bill of Materials (BOM) Alignment*:
• The system SHALL align `androidx.compose:compose-bom` to `2026.09.00` in `app/build.gradle` and remove hardcoded version pins on Compose foundation and UI artifacts.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: New requirement addressing production crash ATT-3163.
2. *Historical Origin & Commit Trace*: Sprint 2026-41.9 (`ATT-3163`). Introduced with Jetpack Compose Foundation 1.7+ upgrade when Google added Android 14 stylus handwriting gestures via `EditorInfoApi34.setHandwritingGestures` without defensive try/catch blocks around class resolution.
3. *Root Reason for Existing Formulation*: Google assumed all Android 14+ devices provide `android.view.inputmethod.SelectGesture`. Fragmentation breaks this on certain OEM builds.
4. *Preservation of Core Invariants*: IME keyboard entry, standard text manipulation, zero namespace hijacking, and 100% test pass rate remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an Android 14+ device where `android.view.inputmethod.SelectGesture` is missing from the platform framework,
• *When* `TrainingApplication.onCreate()` initializes and any non-password text field in Compose receives user focus,
• *Then* `EditorInfoApi34.setHandwritingGestures` is safely bypassed, no `NoClassDefFoundError` is thrown, and the keyboard/IME input connection is successfully established.
• *Given* an Android 14+ device with full native stylus handwriting support,
• *When* `ComposeHandwritingResilience.install()` runs,
• *Then* `SelectGesture` is resolved without error and stylus handwriting gestures remain enabled.
• *Given* `ComposeHandwritingResilienceTest.kt`,
• *When* running unit tests,
• *Then* safe probe execution, fallback handling, and idempotent state mutation are validated with 100% pass rate.

**Invariants**: No platform namespace hijacking, caller stability for all text fields, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-343: Live Segment and Climb Cockpit BottomSheet Default Fully Expanded Presentation [Verified]
- **Tests**: `TST-UI-303`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorGridScreen.kt`, `SensorGridScreenClimbSuppressionTest.kt`
- **Rationale**: The system SHALL default the live climb and segment bottom sheet (`BottomSheetScaffold`) in `SensorGridScreen.kt` to the fully expanded state (`SheetValue.Expanded`) when becoming active, providing immediate visibility of the elevation profile graph, instantaneous gradient profile, and live pacing telemetry HUD without requiring manual touch interaction or swipe gestures while riding or running (ATT-3176, ATT-3319, amending `REQ-UI-327` and aligning with Design Guidelines §5.8):
1. *Initial Sheet Value Evaluation (`SensorGridScreen.kt`)*:
• `initialSheetValue` SHALL evaluate to: `if (shouldShowBottomSheet) SheetValue.Expanded else SheetValue.Hidden`.
2. *Entity-Aware Reactive Transition to Expanded State (`SensorGridScreen.kt`)*:
• The system SHALL compute a composite active entity key (`activeEntityKey`) derived from the active segment (`stravaId`) or active climb (`id`, `routeIndex`, `name`).
• When `shouldShowBottomSheet` is `true`, the `LaunchedEffect(shouldShowBottomSheet, activeEntityKey)` block SHALL unconditionally call `scaffoldState.bottomSheetState.expand()`, smoothly animating the sheet to the fully expanded state upon entering tracking mode or whenever a new segment or climb becomes active, regardless of whether the sheet was previously in `SheetValue.Hidden` or `SheetValue.PartiallyExpanded`.
• When `!shouldShowBottomSheet`, the sheet SHALL call `scaffoldState.bottomSheetState.hide()`.
3. *Preserved Manual Swipe & Collapse Ergonomics*:
• The sheet SHALL maintain `sheetSwipeEnabled = true` when `shouldShowBottomSheet == true`, preserving the athlete's ability to manually swipe the sheet down to peek height (`SheetValue.PartiallyExpanded`) or dismiss it to inspect underlying sensor tiles.
• Manual swipe-down SHALL remain stable during an ongoing segment or climb because `activeEntityKey` is constant during the active lifecycle of the entity.
4. *Preservation of System Invariants*:
• Active-tab gating (`isTabActive`), hidden state handling (`SheetValue.Hidden`), zero peek height and transparent container when hidden, and 100% full-suite unit test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-327` (*Tracking Tab Live Climb and Segment Cockpit BottomSheet Active-Tab Gating & State Synchronization*, Sprint 2026-41.7, ATT-2945).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2945` (Sprint 2026-41.7). In ATT-2945, `SheetValue.PartiallyExpanded` was selected conservatively to avoid covering sensor tiles during initial implementation.
3. *Root Reason for Existing Formulation*: Historical conservatism in ATT-2945 required manual swiping up. Field experience demonstrated that taking hands off handlebars to swipe open a climb or segment sheet while riding is inconvenient and hazardous.
4. *Preservation of Core Invariants*: Tab isolation, suppression on disabled tabs, and swipe-down flexibility remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout tracking session on a tab with live segments or live climbs enabled,
• *When* the athlete enters or approaches an active live segment or route climb (`shouldShowBottomSheet` becomes `true`),
• *Then* `scaffoldState.bottomSheetState` animates to `SheetValue.Expanded`, immediately displaying the full elevation profile canvas and HUD metrics.
• *Given* the sheet in `SheetValue.Expanded`,
• *When* the athlete swipes down on the sheet drag handle,
• *Then* the sheet collapses to peek height (`SheetValue.PartiallyExpanded`), uncovering the sensor tiles.
• *Given* an active workout session with no segment or climb active (`shouldShowBottomSheet == false`),
• *Then* `scaffoldState.bottomSheetState` remains in `SheetValue.Hidden` with `0.dp` peek height.

**Invariants**: Active-tab gating, hidden state suppression, swipe ergonomics, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-344: Default Tracking Views Feature Configuration Matrix on Initial Install [Verified]
- **Tests**: `TST-UI-304`, `TST-UI-384`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingViewsDatabaseManager.java`
- **Rationale**: The system SHALL configure purposeful default feature toggles for the three standard tracking tabs generated per `ActivityType` upon clean application installation or initial database creation (`TrackingViewsDbHelper.onCreate()`), preventing unsolicited visual clutter and aligning with Design Guidelines §5.9 (ATT-3177):
1. *Standard Tab (`layoutNr = 1`, `R.string.text_default`)*:
• `SHOW_NAVIGATION_HINTS = 1` (Enabled)
• `SHOW_LIVE_SEGMENTS = 1` (Enabled)
• `SHOW_LIVE_CLIMBS = 1` (Enabled)
• `SHOW_MAP = 0` (Disabled)
• `SHOW_ELEVATION_PROFILE = 0` (Disabled)
• `SHOW_LAP_BUTTON = 0` (Disabled)
2. *Map Tab (`layoutNr = 2`, `R.string.tab_map`)*:
• `SHOW_NAVIGATION_HINTS = 1` (Enabled)
• `SHOW_LIVE_SEGMENTS = 0` (Disabled)
• `SHOW_LIVE_CLIMBS = 0` (Disabled)
• `SHOW_MAP = 1` (Enabled)
• `SHOW_ELEVATION_PROFILE = 1` (Enabled)
• `SHOW_LAP_BUTTON = 0` (Disabled)
3. *Laps Tab (`layoutNr = 3`, `R.string.tab_laps`)*:
• `SHOW_NAVIGATION_HINTS = 0` (Disabled)
• `SHOW_LIVE_SEGMENTS = 0` (Disabled)
• `SHOW_LIVE_CLIMBS = 0` (Disabled)
• `SHOW_MAP = 0` (Disabled)
• `SHOW_ELEVATION_PROFILE = 0` (Disabled)
• `SHOW_LAP_BUTTON = 1` (Enabled)
4. *API Signature Extension (`TrackingViewsDatabaseManager.java`)*:
• `TrackingViewsDbHelper.addDefaultTab(...)` SHALL accept explicit booleans for `showLiveClimbs` and `showNavigationHints`, replacing the unconditionally hardcoded `1` values.
5. *Preservation of System Invariants*:
• User customization sovereignty via `ConfigTrackingTabsActivity` remains 100% intact.
• Database schema version remains at 12; no migration of existing user databases or mutation of existing custom views.
• Sensor filter defaults (`REQ-UI-198`) and view size seeding (`REQ-UI-253`) remain strictly intact.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement only (`REQ-UI-344`). No existing requirements modified.
2. *Historical Origin & Commit Trace*: Ticket `ATT-3177`, sprint `2026-41.9`, target release `V4.9.39`, Epic `ATT-355` (*Good and consistent UI*). Live Climbs and Navigation Hints were added to `VIEWS_TABLE` in V12 (`ATT-2360`, commit `f731522a`) where `SHOW_LIVE_CLIMBS = 1` and `SHOW_NAVIGATION_HINTS = 1` were temporarily hardcoded.
3. *Root Reason for Existing Formulation*: Setting them to `1` across all default tabs was a convenient prototype default. Field testing showed unsolicited climb bottom sheets on the Map tab and turn cues on the Laps tab created visual distraction.
4. *Preservation of Core Invariants*: Linking `SHOW_ELEVATION_PROFILE` to `showMap ? 1 : 0` (`REQ-UI-009`), sensor filter presets (`REQ-UI-198`), view size seeding (`REQ-UI-253`), and user customization sovereignty remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a fresh app installation or clean database creation (`onCreate`),
• *When* default tracking views are generated for any activity type (e.g. `GENERIC_HR`, `RUN`, `BIKE`, `BIKE_POWER`),
• *Then* the Standard tab (`layoutNr = 1`) SHALL have `SHOW_NAVIGATION_HINTS = 1`, `SHOW_LIVE_SEGMENTS = 1`, `SHOW_LIVE_CLIMBS = 1`, and `SHOW_MAP = 0`, `SHOW_ELEVATION_PROFILE = 0`, `SHOW_LAP_BUTTON = 0`.
• *And* the Map tab (`layoutNr = 2`) SHALL have `SHOW_NAVIGATION_HINTS = 1`, `SHOW_MAP = 1`, `SHOW_ELEVATION_PROFILE = 1`, and `SHOW_LIVE_SEGMENTS = 0`, `SHOW_LIVE_CLIMBS = 0`, `SHOW_LAP_BUTTON = 0`.
• *And* the Laps tab (`layoutNr = 3`) SHALL have `SHOW_LAP_BUTTON = 1`, and `SHOW_NAVIGATION_HINTS = 0`, `SHOW_LIVE_SEGMENTS = 0`, `SHOW_LIVE_CLIMBS = 0`, `SHOW_MAP = 0`, `SHOW_ELEVATION_PROFILE = 0`.

**Invariants**: Custom layout editing, existing user databases, schema version 12, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-345: Workout Recovery Dialog Layout Visibility, Start Interception Guard & Sticky Tracking State Synchronization [Verified]
- **Tests**: `TST-UI-305`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `StartOrResumeDialog.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt`, `WorkoutSummariesDatabaseManager.java`, `TrackerService.java`, `TrainingApplication.java`, `BANALServiceRepository.kt`
- **Rationale**: The system SHALL guarantee full layout visibility of `StartOrResumeDialog` by configuring explicit window bounds, guard `onStartTracking()` in `ControlTrackingViewModel` to intercept unfinalized workouts before starting a new session, ensure robust SQLite matching and updates for `finished IS NULL` and `finished = 0` in `WorkoutSummariesDatabaseManager`, and synchronize tracking state on sticky service resurrection (ATT-3165, amending `REQ-UI-329`, `REQ-STB-003`, and `REQ-STB-012`):
1. *Dialog Window Bounds & Visibility (`StartOrResumeDialog.kt`)*:
• `StartOrResumeDialog` SHALL override `onStart()` to explicitly configure its window layout dimensions: `dialog?.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)`.
• This guarantees that Compose content expands across full dialog width without collapsing or measuring as zero width.
2. *Start Tracking Interception & Unfinished Session Guard (`ControlTrackingViewModel.kt` & `TrackingTabsScreen.kt`)*:
• `ControlNavigation` SHALL declare `data object ShowRecoveryDialog : ControlNavigation()`.
• When `onStartTracking()` is invoked: If `WorkoutSummariesDatabaseManager.hasUnfinishedWorkout() == true` AND `TrainingApplication.isTracking() == false`, the ViewModel SHALL NOT dispatch `REQUEST_START_TRACKING` and SHALL emit `ControlNavigation.ShowRecoveryDialog`. Otherwise, it SHALL dispatch `REQUEST_START_TRACKING` via broadcast as normal.
• When `TrackingTabsScreen` observes `ControlNavigation.ShowRecoveryDialog`, it SHALL invoke `(activity as? StartOrResumeInterface)?.showStartOrResumeDialog()` or instantiate `StartOrResumeDialog().show(...)` if not already visible.
3. *Database Query & Finalization Predicate Robustness (`WorkoutSummariesDatabaseManager.java` & `TrackerService.java`)*:
• In `WorkoutSummariesDatabaseManager.hasUnfinishedWorkout()`, the SQLite query SHALL filter rows matching `(finished IS NULL OR finished = 0)` and return `true` if any unfinalized workout exists.
• In `WorkoutSummariesDatabaseManager.discardOrFinishUnfinishedWorkout()`, the update SHALL match rows with `(finished IS NULL OR finished = 0)`.
• In `TrackerService.createNewWorkout()`, `ContentValues` SHALL explicitly insert `values.put(WorkoutSummaries.FINISHED, 0)`.
4. *Sticky Service Tracking State UI Synchronization (`TrainingApplication.java` & `BANALServiceRepository.kt`)*:
• In `TrainingApplication.setTracking()`, the method SHALL invoke `notifyTrackingStateChanged()`, dispatching the `TRACKING_STATE_CHANGED` broadcast intent and updating notification summary.
• In `BANALServiceRepository.init`, `_trackingMode` LiveData SHALL be initialized with `TrainingApplication.getTrackingMode()` rather than hardcoded `TrackingMode.READY`.
• When `TrackerService` resumes via `RESUME_SERVICE_RECREATION`, `ControlTrackingScreen` SHALL display the active tracking controls (e.g. Pause button) and `StartOrResumeDialog` SHALL NOT be shown.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-329` (*Material 3 Jetpack Compose Workout Recovery Dialog Architecture*), `REQ-STB-003` (*Interrupted Workout Resumption & Unfinished Workout Recovery*), and `REQ-STB-012` (*Forensic Process Kill Diagnosis*).
2. *Historical Origin & Commit Trace*: Sprint 2026-41.9 (`ATT-3165`). Follows migration in `ATT-2948` (commit `fe4e24f1`), where `ComposeView` was hosted in floating `DialogFragment` without explicit window layout dimensions in `onStart()`, resulting in invisible dialog bounds on certain devices.
3. *Root Reason for Existing Formulation*: Historical `StartOrResumeDialog` relied on Android `AlertDialog.Builder`. Compose migration converted it to `DialogFragment` with `ComposeView`, omitting window parameters. In addition, `ControlTrackingViewModel` was previously decoupled from SQLite checks and unconditionally started new sessions, masking unfinalized workouts.
4. *Preservation of Core Invariants*: `StartOrResumeInterface` callbacks (`chooseStart()`, `chooseResume()`), clean-stop finalization (`FINISHED = 1`), battery optimization settings shortcuts, and full clean-room unit test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `StartOrResumeDialog` attached,
• *When* `onStart()` executes,
• *Then* window layout parameters are set to `MATCH_PARENT` width and `WRAP_CONTENT` height.
• *Given* an unfinalized workout exists and the app is not tracking,
• *When* the athlete taps Start Tracking in `ControlTrackingScreen`,
• *Then* tracking start is intercepted and `ShowRecoveryDialog` is emitted.
• *Given* an unfinalized workout with `finished IS NULL` or `finished = 0`,
• *When* `hasUnfinishedWorkout()` or `discardOrFinishUnfinishedWorkout()` executes,
• *Then* the record is matched and updated.
• *Given* `TrackerService` resumes via `RESUME_SERVICE_RECREATION`,
• *When* the UI displays Control Tracking,
• *Then* `trackingMode` evaluates to `TrackingMode.TRACKING`, showing the Pause button without displaying the recovery dialog.

**Invariants**: Dialog callbacks, clean finalization, battery intent shortcuts, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-346: Canonical Adaptive Height & Weight Decoupling for Sole Elevation Profile on Tracking Screen [Verified]
- **Tests**: `TST-UI-306`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorGridScreen.kt`
- **Rationale**: The system SHALL render the elevation profile on tracking screens (`SensorGridScreen.kt`) at its canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) determined by `calculateElevationProfileHeight(range)` regardless of whether the embedded map is enabled or disabled, eliminating vertically stretched or oversized profile rendering when the map is hidden (ATT-3173, amending `REQ-UI-339` and aligning with Design Guidelines §2.3):
1. *Elevation Profile Surface Constraints (`SensorGridScreen.kt`)*:
• The wrapping `Surface` for `ElevationProfile` SHALL NOT apply `Modifier.weight(1f)`.
• The wrapping `Surface` SHALL declare `Modifier.fillMaxWidth().padding(top = 8.dp)` without vertical weight or forced parent height filling.
2. *Canonical Elevation Profile Invocation (`SensorGridScreen.kt`)*:
• `ElevationProfile` SHALL be invoked with `pathPoints`, `currentDistance`, and `modifier = Modifier.fillMaxWidth()`, leaving `fillParentHeight` to its default value (`false`).
• This guarantees that `totalCanvasHeight` evaluates to `cachedData.adaptiveHeight` ($70\text{dp} \dots 200\text{dp}$) computed via `calculateElevationProfileHeight(range)`.
3. *Sensor Grid Flexible Weight & Containment*:
• The scrollable sensor grid `Column` SHALL retain `Modifier.weight(1f, fill = false)`.
• When few sensor fields exist (1–2 rows), the sensor grid wraps its content naturally without stretching.
• When many sensor fields exist (4–6 rows), the sensor grid scrolls smoothly within the remaining space above the elevation profile and never pushes the elevation profile off-screen.
4. *Preservation of System Invariants*:
• When `showMap == true`, the map retains primary flexible space (`weight(1f)`), with `ElevationProfile` rendering below it.
• When `state.pathPoints.size < 2`, the elevation profile remains suppressed.
• Ambient `NavigationHintsOverlay` positioning, map recomposition barrier isolation, and 100% full-suite unit test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-339` (*Dynamic Cockpit Weight Allocation & Flexible Vertical Expansion for Elevation Profile when Map is Disabled*, Sprint 2026-41.8, ATT-3107).
2. *Historical Origin & Commit Trace*: Sprint 2026-41.8 (`ATT-3107`, commit `8d6ef4e1`).
3. *Root Reason for Existing Formulation*: In `ATT-3107`, `weight(1f)` and `fillParentHeight = true` were applied to prevent the elevation profile from being compressed by unconstrained sensor tiles. However, this over-corrected and forced the profile to consume half the screen on modern high-resolution displays.
4. *Preservation of Core Invariants*: Sensor grid scrollability via `weight(1f, fill = false)`, canonical adaptive height via `calculateElevationProfileHeight(range)`, and 100% clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout tracking tab with `showElevationProfile = true` and `showMap = false`,
• *When* `SensorGridScreen` renders in `ScreenMode.TRACKING`,
• *Then* `ElevationProfile` renders at its canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) and does NOT stretch to 50%+ of screen height.
• *Given* 1–2 sensor rows with map disabled and elevation profile enabled,
• *When* the screen renders,
• *Then* sensor tiles wrap content height naturally without forced spacing, and the elevation profile is anchored neatly below.
• *Given* 6 sensor rows with map disabled and elevation profile enabled,
• *When* the screen renders,
• *Then* the sensor grid is vertically scrollable within the remaining space and does not crowd out the elevation profile.
• *Given* both map and elevation profile enabled,
• *When* the screen renders,
• *Then* the map retains `weight(1f)` and the elevation profile renders below it at its standard adaptive height.

**Invariants**: Sensor grid scrollability, canonical adaptive height, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-347: Canonical Route Preview Polyline Styling & Selection State Decoupling for RoutesScreen and RouteDetailSheet [Verified]
- **Tests**: `TST-UI-307`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RoutesScreen.kt`, `RouteDetailSheet.kt`, `RoutesViewModel.kt`
- **Rationale**: The system SHALL decouple route map preview rendering in `RoutesScreen.kt` and `RouteDetailSheet.kt` from unconditional active navigation styling, rendering route polylines according to their actual selection status (`RouteSummary.isSelected`) and active navigation state (`activeNavigatedRouteId`), ensuring that toggling route selection provides immediate dynamic visual feedback on the map preview (ATT-3172, amending `REQ-MAP-040` clause 5):
1. *RoutesViewModel Active Route Exposure (`RoutesViewModel.kt`)*:
• `RoutesViewModel` SHALL expose `val activeNavigatedRouteId: StateFlow<Long?> = routesRepository.activeNavigatedRouteId`.
2. *Dynamic Route State Propagation (`RoutesScreen.kt`)*:
• `RoutesScreen` SHALL observe `viewModel.activeNavigatedRouteId`.
• In the route preview branch, `selectedRoute.toMapRoute()` SHALL receive `isActiveNavigation = (selectedRoute.summary.id == activeNavigatedRouteId)`.
• When not actively navigated, `selectedRoute.toMapRoute()` reflects `selectedRoute.summary.isSelected`.
• When `onToggleSelection` is invoked, `viewModel.toggleRouteSelection(selectedRoute.summary.id, isSelected)` mutates SQLite state, causing `selectedRoute.summary.isSelected` to update and dynamically re-render the polyline.
3. *Decoupled Route Inspection in RouteDetailSheet (`RouteDetailSheet.kt`)*:
• `RouteDetailSheet` SHALL accept optional `isActiveNavigation: Boolean = false` and `onToggleSelection: ((Boolean) -> Unit)? = null` parameters (defaulting to `false` and no-op).
• `RouteOnMapScreen` SHALL be invoked with `routeWithPath.toMapRoute(isActiveNavigation = isActiveNavigation)`, eliminating hardcoded `isActiveNavigation = true` and accurately reflecting `routeWithPath.summary.isSelected`.
4. *Visual Attribute Resolution Invariants (`MapModels.kt`)*:
• When `isActiveNavigation == true`: Color = `TTColor.RouteActiveNavigation` (0xFF1E88E5), Width = 26f, Pattern = `null` (solid ribbon).
• When `isActiveNavigation == false && isSelected == true`: Color = `TTColor.RouteSelected` (0xFF1565C0), Width = 10f, Pattern = `Dash(15f)`, `Gap(15f)`.
• When `isActiveNavigation == false && isSelected == false`: Color = `TTColor.RouteUnselected` (0xFF90CAF9), Width = 6f, Pattern = `Dash(15f)`, `Gap(15f)`.
5. *Preservation of System Invariants*:
• Active turn-by-turn workout navigation styling (`REQ-MAP-040`) remains fully intact.
• Stacking z-index order (`ROUTE_BASE_Z_INDEX < ROUTE_ACTIVE_BASE_Z_INDEX < CLIMB_Z_INDEX < SEGMENT_Z_INDEX < USER_LOCATION_Z_INDEX`) remains preserved.
• Clean-room unit test suite pass rate MUST remain 100%.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-MAP-040` (*High-Glanceability Route Polyline Scaling and Solid Line Rendering for Actively Navigated Route*, ATT-2940), specifically clause 5.
2. *Historical Origin & Commit Trace*: Sprint 2026-41.6 (`ATT-2940`, commit `a1dc652e`).
3. *Root Reason for Existing Formulation*: In ATT-2940, the developer intended to highlight the inspected route on `RoutesScreen` and `RouteDetailSheet` so it would stand out. However, hardcoding `isActiveNavigation = true` conflated passive inspection with active workout navigation, permanently rendering 26f solid blue and masking `summary.isSelected` changes.
4. *Preservation of Core Invariants*: Active turn-by-turn navigation styling during workouts, 3-tier polyline resolution, z-index layering, and 100% clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `RoutesScreen` previewing a route with `isSelected = false` and not actively navigating,
• *When* `RouteOnMapScreen` renders,
• *Then* the polyline color is `TTColor.RouteUnselected`, width is `6f`, and pattern is dashed.
• *Given* `RoutesScreen` previewing a route,
• *When* the athlete toggles the switch in `RouteSummaryHeader` from off to on,
• *Then* the polyline dynamically updates to `TTColor.RouteSelected`, width `10f`, and pattern dashed.
• *Given* an active workout with a route actively navigated (`activeNavigatedRouteId == route.id`),
• *When* inspected in `RoutesScreen`,
• *Then* the polyline renders with `TTColor.RouteActiveNavigation`, width `26f`, and solid pattern (`pattern == null`).
• *Given* `RouteDetailSheet` invoked on a passive route,
• *When* composed,
• *Then* `routeWithPath.toMapRoute(isActiveNavigation = false)` renders according to `routeWithPath.summary.isSelected`.

**Invariants**: 3-tier styling, active workout navigation rendering, z-index hierarchy, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-348: Restoration of Unified Top-of-Tab Navigation Hints Overlay Anchoring Across Tracking Tabs [Verified]
- **Tests**: `TST-UI-308`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorGridScreen.kt`, `SensorGridScreenRouteIntegrationTest.kt`
- **Rationale**: The system SHALL restore unified top-of-tab anchoring for active navigation prompt overlays (`TurnPromptBanner`, `ReturnNavigationHud`, `ForkDecisionCard`) across all tracking tabs, superseding and reverting the map-relative placement of `REQ-UI-335` (ATT-3170, amending `REQ-UI-324` and `REQ-MAP-031`):
1. *Unified Root Overlay Anchoring (`SensorGridScreen.kt`)*:
• In `SensorGridScreen.kt`, the navigation hints overlay SHALL be anchored at `Alignment.TopCenter` within the root tracking `Box` container, floating over the top of the tab content.
• The positioning SHALL be invariant to the presence or absence of the embedded map view (`state.mapState.showMap`).
2. *Decoupling from Map Container*:
• The system SHALL remove the duplicated `NavigationHintsOverlay` call inside the `TrackingMapContainer` `Box`. The map container SHALL NOT host or anchor navigation hint overlays.
3. *Unified Navigation Hints Overlay Architecture*:
• The encapsulation of `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard` inside `NavigationHintsOverlay` SHALL be strictly preserved.
• Navigation cues SHALL render only when `state.showNavigationHints == true`.
4. *Zero-Telemetry-Displacement Invariant*:
• Navigation cues SHALL float as non-displacing ambient overlays without shifting or resizing in-flow sensor grid tiles, elevation profile surfaces, or map canvases.
5. *Preservation of System Invariants*:
• Isolated recomposition barrier (`@Immutable TrackingMapState`), cue transparency tokens (`tuningConfig.navigationCueTransparency`), dismiss durations, and 100% clean-room test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Reverts and supersedes `REQ-UI-335` (*Dynamic Map-Aware Navigation Hints Overlay Anchoring over Embedded Map View*, Sprint 2026-41.8, ATT-3100) and restores the top-of-tab overlay model established in `REQ-UI-324` (*Align turn-by-turn navigation hints UI with updated design guidelines and floating top-center overlay*, Sprint 2026-41.6, ATT-2941) and `REQ-MAP-031` (*TurnPromptBanner Turn-by-Turn Navigation Prompts & Direction HUD*, ATT-2874).
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-41.8 (`ATT-3100`, commit `6fa2b961`).
3. *Root Reason for Existing Formulation*: ATT-3100 moved navigation cues to anchor inside the map container below the sensor grid to prevent obscuring the topmost sensor tiles.
4. *Reason for Invalidation / Revision*: On-device field evaluation during the Sprint 2026-41.8 Joint Review demonstrated that having turn prompts split the middle of the screen above the map canvas compromised at-a-glance readability, caused cognitive inconsistency between tracking tabs, and obstructed the upcoming route on the map. The athlete explicitly mandated restoring top-of-tab anchoring.
5. *Preservation of Core Invariants*: Floating non-displacing overlay behavior, transparency tuning, dismiss timers, and full-suite test pass rate remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active tracking tab with `showMap == true` and `showNavigationHints == true`,
• *When* a navigation cue (turn prompt, return HUD, or fork decision) is active,
• *Then* the navigation overlay renders anchored at `Alignment.TopCenter` of the root tracking container,
• *And* the map container does NOT host a nested navigation overlay.
• *Given* an active tracking tab with `showMap == false` and `showNavigationHints == true`,
• *When* navigation hints are displayed,
• *Then* the overlay renders at `Alignment.TopCenter` of the root tracking container.
• *Given* any tracking tab,
• *When* navigation prompts appear or dismiss,
• *Then* neither the sensor grid nor the map view experience layout displacement or resizing.

**Invariants**: Zero telemetry displacement, map recomposition barrier, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-349: Active Sensor Deletion Disconnection Lifecycle and Defensive Telemetry Boundary Filtering Architecture [Verified]
- **Tests**: `TST-UI-309`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DeviceDataRepository.kt`, `ControlTrackingViewModel.kt`, `EditDeviceDialog.kt`, `DeviceManager.java`
- **Rationale**: The system SHALL eliminate orphaned ghost sensor tiles and non-responsive screen-dimmed dialog states when deleting active or paired sensors (ATT-3169, refining `REQ-UI-278`, `REQ-UI-301`, and ASPICE Governance Rule 35):
1. *Service Disconnection Lifecycle on Device Deletion (`DeviceDataRepository.kt`)*:
• Upon invocation of `deleteDevice(deviceId)`, `DeviceDataRepository` SHALL dispatch `sendPairingChangedBroadcast(deviceId, paired = false)` prior to or in conjunction with database deletion.
• `BANALService` / `DeviceManager.java` receiving `PAIRING_CHANGED` with `paired = false` SHALL invoke `removeAndStopRemoteDevice(deviceId)`, shutting down hardware and GATT connections (`myRemoteDevice.shutDown()`) and removing the device ID from internal active device sets (`mMyRemoteDevices`, `mSearchQueue`).
2. *Defensive Telemetry Boundary Filtering (`ControlTrackingViewModel.kt`)*:
• In `ControlTrackingViewModel`, the `remoteDevices` state flow combining `banalServiceRepository.activeRemoteDevicesIds` with `allDevicesFromDb` SHALL apply defensive filtering (`mapNotNull`).
• Any device ID present in `activeRemoteDevicesIds` that cannot be resolved in `allDevicesFromDb` SHALL be dropped immediately without generating fallback or dummy entities.
• The system SHALL NEVER emit `RemoteDeviceUIData` with `DeviceType.DUMMY` or placeholder label `"New Sensor"` into `remoteDevices`.
3. *Defensive Dialog Auto-Dismiss (`EditDeviceDialog.kt`)*:
• In `EditDeviceDialog.kt`, if `viewModel.deviceSnapshot` is `null` (device missing from SQLite or unresolvable), the dialog SHALL immediately invoke `onDismiss()`.
• The system SHALL NEVER leave an attached `DialogFragment` with `FLAG_DIM_BEHIND` active without renderable content.
4. *Null-Safe Teardown in Device Manager (`DeviceManager.java`)*:
• `DeviceManager.removeAndStopRemoteDevice(long deviceId)` SHALL defensively verify `myRemoteDevice != null` before executing `shutDown()`, preventing null pointer exceptions if the device was previously removed.
5. *Preservation of System Invariants*:
• Sensor header layout isolation and symmetrical centering (`REQ-UI-301`, `REQ-UI-313`) MUST remain intact.
• Telemetry streaming for active, valid sensors MUST remain unaffected.
• Zero regressions across clean-room test suite (`./gradlew testDebugUnitTest`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Refines `REQ-UI-278` (*Pairing Triggers Relocation to Sensor Management*), `REQ-UI-301` (*Sensor Header Layout Isolation*), and ASPICE Governance Rule 35.
2. *Historical Origin & Commit Trace*: Sprint `2026-41.9`, Ticket `ATT-3169`, Epic `ATT-355` (*Good and consistent UI*). Resolves ghost "New Sensor" tile and blank modal trap.
3. *Root Reason for Existing Formulation*: Commit `c69b24c5` originally used "New Sensor" fallback for un-paired hardware scanning. When reused for active telemetry (`activeRemoteDevicesIds`), deleting a sensor from SQLite caused active remote IDs to resolve to the dummy fallback, launching a blank edit dialog that dimmed the screen.
4. *Preservation of Core Invariants*: Legitimate sensor pairing, telemetry updates, edit dialogs for valid devices, and 100% test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active sensor connected and transmitting telemetry displayed in `ControlTrackingScreen`,
• *When* the athlete deletes the sensor from the device management list (*"Meine Sensoren"*),
• *Then* `BANALService` SHALL immediately disconnect and shut down the sensor, and the sensor tile SHALL cleanly disappear from `ControlTrackingScreen` without lingering as a ghost tile.
• *Given* an active remote device ID emitted by `BANALService` that does not exist in SQLite,
• *When* `ControlTrackingViewModel` computes `remoteDevices`,
• *Then* the orphaned ID SHALL be silently dropped via `mapNotNull`, and zero `"New Sensor"` or `DeviceType.DUMMY` tiles SHALL be emitted.
• *Given* an attempt to launch `EditDeviceDialog` with a non-existent device ID,
• *When* the dialog attaches,
• *Then* `EditDeviceDialog` SHALL automatically dismiss without darkening or trapping the screen.

**Invariants**: Normal sensor telemetry, valid edit dialogs, sensor header centering, and 100% test pass rate MUST NOT be broken.

### REQ-UI-350: Edge-to-Edge System Navigation Bar Inset Padding for Post-Workout Alert Snackbars and Action Controls [Verified]
- **Tests**: `TST-UI-310`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutSummariesTabbedScreen.kt`, `WorkoutSummariesListFragment.kt`, `RoutesScreen.kt`
- **Rationale**: The system SHALL guarantee that in-app snackbars and interactive action buttons displayed on aftermath and management screens render cleanly above the Android system navigation bar / gesture navigation handle without visual occlusion or tap interference (ATT-3167, refining `REQ-CON-007` and ASPICE Governance Rule 35):
1. *Navigation Bar Inset Padding on SnackbarHost*:
• In `WorkoutSummariesTabbedScreen.kt`, the `SnackbarHost` composable SHALL configure `modifier = Modifier.navigationBarsPadding()`.
• In `WorkoutSummariesListFragment.kt` and `RoutesScreen.kt`, the `SnackbarHost` composables SHALL also configure `modifier = Modifier.navigationBarsPadding()`.
2. *Unobstructed Action Target*:
• The snackbar container and its action button (`[Zu Sensoren]` / `[View Sensors]`) SHALL be positioned fully above the OS navigation inset boundary across all display configurations (gesture navigation bar, 3-button navigation bar, landscape/portrait rotations).
3. *Preservation of Edge-to-Edge Drawing*:
• The root `Scaffold` SHALL retain `contentWindowInsets = WindowInsets(0.dp)` to prevent unwanted double-padding or layout disruption of inner map views and list containers.
4. *Preservation of System Invariants*:
• Dual-channel post-workout low battery alerting (`REQ-CON-007`), critical battery threshold ($\\le 20\\%$), action click navigation (`drawer_my_sensors`), and 100% test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Refines `REQ-CON-007` (*Modernized Post-Workout Low Sensor Battery Alerting (In-App Snackbar & System Notification)*, Sprint 2026-40.16, ATT-2192).
2. *Historical Origin & Commit Trace*: Sprint 2026-41.9, Ticket ATT-3167, Epic ATT-355 (*Good and consistent UI*).
3. *Root Reason for Existing Formulation*: When converting the post-workout modal dialog to a Material 3 snackbar in ATT-2192, `contentWindowInsets = WindowInsets(0.dp)` was set on the `Scaffold` to keep the workout summary map and tab headers edge-to-edge. However, `SnackbarHost` was left unpadded, causing the snackbar to render flush against the bottom edge beneath the OS navigation bar.
4. *Preservation of Core Invariants*: Dual-channel alerts (system notification + in-app snackbar), sticky alert consumption via `WorkoutNavigationEvents`, navigation to sensor management, and 100% test pass rate remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout that completed with a sensor battery $\\le 20\\%$,
• *When* `WorkoutSummariesTabbedScreen` is displayed on a device with gesture navigation or 3-button navigation,
• *Then* the low battery snackbar floats completely above the navigation bar / gesture handle with zero visual occlusion.
• *Given* the low battery snackbar displayed on `WorkoutSummariesTabbedScreen`,
• *When* the user taps `[Zu Sensoren]`,
• *Then* the action button responds cleanly and navigates to "Meine Sensoren" without intercept or interference from OS gesture navigation.
• *Given* `WorkoutSummariesTabbedScreen` composed on a device,
• *When* inspecting the `SnackbarHost` hierarchy,
• *Then* `Modifier.navigationBarsPadding()` is applied to the host.

**Invariants**: Edge-to-edge map canvas, low battery threshold ($\\le 20\\%$), non-blocking navigation, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-351: Multi-Dimensional Sensor Filtering by Protocol, Category, and Battery Level with Deep-Link Navigation [Verified]
- **Tests**: `TST-UI-311`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DevicesTabbedScreen.kt`, `DeviceListScreen.kt`, `DeviceListViewModel.kt`, `DevicesTabbedViewModel.kt`, `MainActivityWithNavigation.kt`, `SensorBatteryNotificationManager.kt`
- **Rationale**: The system SHALL provide multi-dimensional filtering across Sensors & Devices views (`DevicesTabbedScreen` / `DeviceListScreen`) by communication protocol, device category, and battery health, and SHALL guarantee reliable deep-link routing from low battery notifications directly into a pre-filtered critical battery list (ATT-3166, refining `REQ-CON-007` and ASPICE Governance Rule 35):
1. *Filter Criteria Domain Model (`DeviceFilterCriteria`)*:
• The system SHALL maintain an immutable domain model `DeviceFilterCriteria` supporting `protocol: Protocol?`, `deviceTypes: Set<DeviceType>`, and `batteryFilter: BatteryFilter` (`ALL`, `CRITICAL_ONLY` [$\le 20\%$], `NORMAL` [$> 20\%$]).
• The model SHALL expose `activeFilterCount: Int`, `isEmpty: Boolean`, `isNotEmpty: Boolean`, and `matches(device: DeviceUiData): Boolean`.
• For `CRITICAL_ONLY`, `matches` SHALL return true if and only if `device.batteryPercentage in 0..20`. Devices with unknown battery ($-1$) SHALL NOT match.
• For `NORMAL`, `matches` SHALL return true if and only if `device.batteryPercentage > 20`.
2. *Material 3 Filter Bottom Sheet (`DeviceFilterBottomSheet`)*:
• Built on `FilterBottomSheetScaffold` (`com.atrainingtracker.trainingtracker.ui.common.filters`).
• Section 1 (Protocol): FilterChips for Alle, ANT+, Bluetooth LE.
• Section 2 (Sensor Category): FilterChips for remote sensor device types (HRM, Bike Power, Bike Speed, Bike Cadence, Speed & Cadence, Run Speed, Environment).
• Section 3 (Battery Level): FilterChips for Alle, Kritisch ($\le 20\%$), Normal ($> 20\%$).
• Standard action bar with "Filter zurücksetzen" and "Anwenden".
3. *Top Bar Filter Action & Active Filter Chips Strip*:
• In `DevicesTabbedScreen`, the top header row SHALL render `FilterActionButton` with active filter count badge.
• Below the tab row, `ActiveDeviceFilterChipsRow` SHALL render `RemovableFilterChip` for each active filter criterion (protocol, each category, battery level) and a "Clear all" button when $> 1$ criteria are active.
4. *Reactive ViewModel Integration*:
• `DevicesTabbedViewModel` SHALL expose `filterCriteria: StateFlow<DeviceFilterCriteria>`, `updateFilterCriteria(criteria)`, and `clearFilterCriteria()`.
• `DeviceListViewModel.getFilteredDevices(spec, criteria)` SHALL filter `mergedDevices` reactively across all tabs.
5. *Notification Deep-Link & Cold-Start Race Resolution*:
• `SensorBatteryNotificationManager` SHALL pass `EXTRA_FILTER_CRITICAL_BATTERY = true` in the notification `PendingIntent`.
• `MainActivityWithNavigation` SHALL queue pending navigation if `navController == null`, and dispatch navigation to `R.id.drawer_my_sensors` upon `onNavControllerReady`.
• When launched with `EXTRA_FILTER_CRITICAL_BATTERY`, `MainActivityWithNavigation` SHALL pre-apply `BatteryFilter.CRITICAL_ONLY` on `DevicesTabbedViewModel`.
6. *9-Language Localization Parity*:
• All new filter strings SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Net-new requirement refining `REQ-CON-007` (*Modernized Post-Workout Low Sensor Battery Alerting*, Sprint 2026-40.16, ATT-2192).
2. *Historical Origin & Commit Trace*: Sprint 2026-41.9, Ticket ATT-3166, Epic ATT-754 (*Filtering*).
3. *Root Reason for Existing Formulation*: Previously, sensors views displayed an unfilterable list, and notification taps navigated to the main activity without opening sensors or filtering out normal-battery devices.
4. *Preservation of Core Invariants*: Wireless scanning, sensor pairing, telemetry streaming, equipment matrix tabs, and 100% clean-room test pass rate remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete on `DevicesTabbedScreen`,
• *When* tapping the filter action icon in the top app bar,
• *Then* `DeviceFilterBottomSheet` opens with protocol, category, and battery sections.
• *Given* active filters applied (e.g. ANT+, HRM, $\le 20\%$),
• *When* viewing the sensors list across tabs,
• *Then* only matching devices are displayed, and corresponding removable chips appear below the tabs.
• *Given* a low battery notification posted in the Android notification shade,
• *When* tapped (on cold launch or background),
• *Then* the app navigates directly to Sensors and pre-applies the $\le 20\%$ battery filter with the active filter chip visible.

**Invariants**: Normal pairing flow, BLE/ANT+ communication, equipment matrix tabs, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-352: LiveClimbSheet Design System Harmonization, Shared ElevationProfile Integration, Adaptive Height Scaling & Degenerate Geometry Safeguards [Verified]
- **Tests**: `TST-UI-312`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `LiveClimbSheet.kt`, `ElevationProfile.kt`, `MetricBadge.kt`, `ClimbColorUtils.kt`
- **Rationale**: The system SHALL harmonize `LiveClimbSheet.kt` with the canonical design system, reuse `ElevationProfile.kt` for gradient-shaded climb profiles with adaptive height scaling based on climb elevation gain, and enforce defensive boundary safeguards against degenerate geometries (ATT-3264, ATT-3316, amending `REQ-UI-328` and `REQ-UI-341`, and complementing `REQ-UI-015`):
1. *Shared ElevationProfile Integration & Bespoke Canvas Elimination*:
• In `ElevationProfile.kt`, the system SHALL add support for optional slope gradient color shading via `climbGradientShading: Boolean = false` and `segmentColorProvider: ((Double) -> Color)? = null`.
• In `ElevationProfile.kt`, the system SHALL maintain optional parameter `fixedHeight: Dp? = null`. When provided, `fixedHeight` overrides `totalCanvasHeight` to enforce a compact profile height. When `null`, `totalCanvasHeight` evaluates to `cachedData.adaptiveHeight` ($70\text{dp} \dots 200\text{dp}$) via canonical `calculateElevationProfileHeight(range)`.
• The system SHALL extract `getClimbGradeColor(gradePercent: Double): Color` as a shared public top-level function in `com.atrainingtracker.trainingtracker.ui.climbs`, mapping standard grade bands: $<3\%$ Green, $3-6\%$ Light Green, $6-9\%$ Amber, $9-12\%$ Orange, $12-20\%$ Red, $>20\%$ Dark.
• In `LiveClimbSheet.kt`, bespoke `ClimbProfileCanvas` SHALL be completely excised and replaced with `ElevationProfile(pathPoints = normalizedPoints, currentDistance = currentDistance, showZoomControls = false, climbGradientShading = true, modifier = Modifier.fillMaxWidth())`, allowing profile height to scale adaptively from $70\text{dp}$ to $200\text{dp}$ based on climb elevation gain via `calculateElevationProfileHeight(range)` without hardcoded fixed height constraints (ATT-3316, Rule 36).
2. *Badge & Chip Design System Harmonization (`MetricBadge.kt`)*:
• In `MetricBadge.kt`, `MetricBadge` SHALL accept optional parameter `shape: Shape = RoundedCornerShape(4.dp)`, preserving 100% backward compatibility.
• In `LiveClimbSheet.kt`, `ClimbCategoryChip` and `ClimbStatusBadge` SHALL delegate directly to `MetricBadge` with custom corner shapes ($6\text{dp}$ and $12\text{dp}$).
3. *Header & Telemetry HUD Layout Symmetry*:
• The header row, subtitle row, 0.5dp divider, and 2-column telemetry HUD in `LiveClimbSheet.kt` SHALL maintain strict layout symmetry and typography tokens matching `SegmentHeader.kt` and `SegmentLiveDetails.kt`.
4. *Defensive Degenerate Geometry & Numerical Overflow Safeguards*:
• In `ElevationProfile.kt`, if `pathPoints.size < 2`, the composable SHALL safely return without rendering and without throwing exceptions.
• In `ElevationProfile.kt` and `calculateElevationBounds`, vertical range calculation SHALL enforce `val range = (max - min).coerceAtLeast(1.0)` to eliminate division-by-zero or `NaN` coordinate projections on flat climbs or zero altitude delta.
• In `ElevationProfile.kt`, horizontal distance span SHALL enforce `val totalDist = (pathPoints.last().distance - pathPoints.first().distance).coerceAtLeast(1.0)` to prevent division-by-zero in X coordinate scaling.
5. *Preservation of System Invariants & Localization*:
• Instantaneous grade, remaining distance, remaining elevation gain, and rider marker tracking logic MUST NOT be altered.
• 100% 9-language localization parity (EN, DE, ES, FR, IT, JA, NL, PL, PT) MUST be strictly preserved.
• Zero regression across existing callers of `ElevationProfile.kt`, `SegmentHeader.kt`, `SegmentLiveDetails.kt`, or `MetricBadge.kt`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-328` (*Live Climb Cockpit Sheet Visual Design Alignment with Live Segment System*, ATT-2947) and `REQ-UI-341` (*Live Climb Cockpit Sheet Interactive Visual Design Optimization, Compose Previews & Ground Line Presentation*, ATT-3103), targeting `LiveClimbSheet.kt`, `ElevationProfile.kt`, and `MetricBadge.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.14 (`ATT-1281`) introduced `ClimbProfileCanvas`. Sprint 2026-41.7 (`ATT-2947`, commit `ea5dfc85`) aligned typography with `SegmentHeader`. Sprint 2026-41.8 (`ATT-3103`, commit `479c3b80`) added Compose previews and ground line. Sprint 2026-41.10 (`ATT-3264`, commit `04ad935c`) refactored to reuse `ElevationProfile` with `fixedHeight = 68.dp`. Sprint 2026-42.1 (`ATT-3316`) removes hardcoded fixed height to scale adaptively ($70\text{dp} \dots 200\text{dp}$) based on climb elevation gain per Rule 36.
3. *Root Reason for Existing Formulation*: `ClimbProfileCanvas` originally hardcoded 68dp for compact sheet display. However, constraining all climbs to 68 dp squashes gradient and altitude perception on tall climbs (500–1200 m gain), making them look identical to minor 20 m rollers. The athlete mandated adaptive height scaling based on climb elevation gain.
4. *Preservation of Core Invariants*: Live tracking calculations, rider marker positioning, ground line baseline, multi-state Compose `@Preview` tooling, canonical adaptive height ($70\text{dp} \dots 200\text{dp}$), and 100% test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active live climb displayed in `LiveClimbSheet`,
• *When* the climb profile renders,
• *Then* `ElevationProfile` is invoked with `climbGradientShading = true` and no fixed height override, rendering the elevation polygon with grade-banded colors and a rider position marker at `currentDistance` with canvas height scaling adaptively from $70\text{dp}$ to $200\text{dp}$ based on climb elevation gain.
• *Given* `ClimbCategoryChip` and `ClimbStatusBadge` in `LiveClimbSheet`,
• *When* rendered in the sheet header,
• *Then* both components delegate directly to `MetricBadge` with custom corner shapes ($6\text{dp}$ and $12\text{dp}$) rather than raw `Surface` duplicates.
• *Given* an empty `pathPoints` list, single-point climb, or flat climb (`altSpan == 0.0`),
• *When* `ElevationProfile` or `LiveClimbSheet` is composed,
• *Then* no exception is thrown, altitude range is coerced to $\ge 1.0$, and the UI handles the degenerate state gracefully without division-by-zero or `NaN`.
• *Given* the automated test suite,
• *When* `LiveClimbSheetLayoutTest`, `LiveClimbAdaptiveHeightTest`, and full clean-room unit regression tests execute,
• *Then* 100% of tests pass with zero regressions across `ElevationProfile` callers.

**Invariants**: Telemetry calculations, rider marker positioning, multi-state Compose `@Preview` tooling, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-353: Persistent Missing ANT+ Adapter Dialog Suppression and Advanced Settings Warning Reset [Verified]
- **Tests**: `TST-UI-313`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TuningPreferencesDataStore.kt`, `MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt`, `SensorSearchTuningSection.kt`, `SensorsGpsFilterSection.kt`, `AdvancedTuningDialog.kt`, `AdvancedTuningScreen.kt`
- **Rationale**: The system SHALL persistently suppress the missing ANT+ adapter dialog upon user dismissal and provide an Advanced Tuning toggle in the Sensor Search & Behavior section to inspect and re-enable warnings (ATT-3220, ATT-3454, amending `REQ-UI-323` and complementing `REQ-SET-073`):
1. *Persistent Suppression Preference (`TuningPreferencesDataStore.kt`)*:
• In `TuningPreferencesDataStore.kt`, the system SHALL define preference key `KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG = booleanPreferencesKey("tuning_suppress_ant_missing_adapter_dialog")`.
• On clean installation, the preference SHALL default to `false` (`TuningPreferencesDefaults.SUPPRESS_ANT_MISSING_ADAPTER_DIALOG = false`).
• `TuningConfig` SHALL declare `val suppressAntMissingAdapterDialog: Boolean = TuningPreferencesDefaults.SUPPRESS_ANT_MISSING_ADAPTER_DIALOG` and helper property `val showAntMissingAdapterWarnings: Boolean get() = !suppressAntMissingAdapterDialog`.
• `TuningPreferencesDataStore` SHALL expose `val suppressAntMissingAdapterDialogFlow: Flow<Boolean>` and mutator `suspend fun setSuppressAntMissingAdapterDialog(suppress: Boolean)`.
• Invoking `resetToDefaults()` in `TuningPreferencesDataStore` SHALL restore `suppressAntMissingAdapterDialog = false`.
2. *Broadcast Reception Guard (`MainActivityWithNavigation.kt`)*:
• In `MainActivityWithNavigation.kt`, the activity SHALL observe `suppressAntMissingAdapterDialogFlow` from `TuningPreferencesDataStore`.
• When `mAntAdapterMissingReceiver` receives `com.atrainingtracker.ADAPTER_NOT_DETECTED`:
- If `suppressAntMissingAdapterDialog == true`, the broadcast SHALL be discarded immediately without mutating `antDialogState`.
- If `suppressAntMissingAdapterDialog == false`, the activity SHALL evaluate `showANTAdapterMissingDialog()` and display `AntMissingAdapterDialog` if not already visible.
3. *Dismissal Persistence (`ATrainingTrackerApp.kt`)*:
• When the athlete taps "Verstanden" (`onDismiss`) on `AntMissingAdapterDialog`, the app SHALL asynchronously invoke `tuningPreferencesDataStore.setSuppressAntMissingAdapterDialog(true)`.
• Tapping the primary action button "ANT+ Status anzeigen" (`onViewStatus`) SHALL dismiss the dialog and display `AntServicesStatusSheet` without mutating the suppression preference.
4. *Advanced Settings Re-Enablement Toggle (`SensorSearchTuningSection.kt`, `AdvancedTuningDialog.kt` & `AdvancedTuningScreen.kt`)*:
• In `SensorSearchTuningSection.kt` (under "Suchverhalten" / Search behavior), the system SHALL render a `TuningToggleItem` (ATT-3454):
- Title: `tuning_show_ant_missing_adapter_warnings_title` ("Show missing ANT+ adapter warnings" / "Warnung bei fehlendem ANT+ Adapter")
- Summary: `tuning_show_ant_missing_adapter_warnings_desc` ("Show alert dialog when an ANT+ sensor is searched but no adapter is detected" / "Hinweisdialog anzeigen, wenn nach ANT+ Sensoren gesucht wird, aber kein Adapter erkannt wurde")
- Checked state: `showAntMissingAdapterWarnings` (`!suppressAntMissingAdapterDialog`).
- Toggling off SHALL set `suppressAntMissingAdapterDialog = true`.
- Toggling on SHALL set `suppressAntMissingAdapterDialog = false`.
• In `SensorsGpsFilterSection.kt`, the warning toggle SHALL NOT be rendered, keeping the section exclusively dedicated to numerical telemetry filtering (GPS accuracy, altitude window, slope speed) (ATT-3454).
• `AdvancedTuningDialog.kt` and `AdvancedTuningScreen.kt` SHALL integrate this preference into `SensorSearchTuningSection` and synchronize with DataStore persistence flows.
5. *9-Language Localization Parity*:
• Title and summary strings SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero missing entries and matching typography.
6. *Preservation of System Invariants*:
• First-time alert presentation on clean install MUST be preserved.
• `AntMissingDependencyDialog` (`com.atrainingtracker.ANT_DEPENDENCY_MISSING`) MUST NOT be suppressed by this preference.
• 100% full clean-room unit test pass rate across all suites.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-323` (*Material 3 Compose Migration for ANT Missing Adapter and Missing Dependency Alerts with Direct ANT+ Status Sheet Integration*, Sprint 2026-41.6, ATT-2939) and complements `REQ-SET-073` (*DataStore Expert Tuning Repository*). Relocated under ATT-3454 (*Move missing ANT+ adapter warning toggle to sensor search and behavior tuning section*).
2. *Historical Origin & Commit Trace*: Sprint 2026-41.6 (`ATT-2939`, commit `05cb0ba4`), Sprint 2026-41.10 (`ATT-3220`, commit `e7b966fa`), Sprint 2026-42.2 (`ATT-3454`).
3. *Root Reason for Existing Formulation*: In ATT-3220, `SensorsGpsFilterSection` was chosen because it was the only section titled "Sensors". However, this conflated numerical telemetry filtering (GPS, barometric altitude, slope speed) with peripheral discovery alert behavior. The missing ANT+ adapter warning is triggered specifically during sensor search rounds (`startSearch()`), so it belongs in "Sensorsuche & Verhalten" (`SensorSearchTuningSection.kt`).
4. *Preservation of Core Invariants*: Single-instance presentation, direct status sheet shortcut, Play Store dependency resolution, DataStore suppression persistence, clean-room test pass rate, and file modularity (< 400 lines) remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a device without ANT+ hardware on clean installation (`suppressAntMissingAdapterDialog = false`),
• *When* `com.atrainingtracker.ADAPTER_NOT_DETECTED` is emitted,
• *Then* `AntMissingAdapterDialog` is presented,
• *When* the athlete taps "Verstanden",
• *Then* the dialog is dismissed and `suppressAntMissingAdapterDialog = true` is saved to DataStore.
• *Given* `suppressAntMissingAdapterDialog = true`,
• *When* subsequent `com.atrainingtracker.ADAPTER_NOT_DETECTED` broadcasts are received,
• *Then* `MainActivityWithNavigation` discards the broadcast and `antDialogState` remains `null`.
• *Given* `suppressAntMissingAdapterDialog = true`,
• *When* the athlete navigates to Advanced Settings -> Sensorsuche & Verhalten,
• *Then* the "Show missing ANT+ adapter warnings" toggle is displayed in the OFF position,
• *When* the athlete toggles it to ON and saves,
• *Then* `suppressAntMissingAdapterDialog = false` is saved and future broadcasts will trigger the dialog.
• *Given* the athlete navigates to Advanced Settings -> Sensoren, GPS & Filter,
• *When* inspecting the category items,
• *Then* the "Show missing ANT+ adapter warnings" toggle is absent, containing exclusively GPS accuracy, altitude filter window, and slope minimum speed controls.
• *Given* `suppressAntMissingAdapterDialog = true`,
• *When* the athlete taps "Auf Werkseinstellungen zurücksetzen",
• *Then* `suppressAntMissingAdapterDialog` reverts to `false`.

**Invariants**: Initial alert presentation, status sheet shortcut, Play Store dependency resolution, modular file size < 400 lines, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-354: Whole Integer Formatting for Cycling Power and Mathematical Rounding for Integer Telemetry Metrics (`POWER`, `CADENCE`, `HR`, `SLOPE`) [Verified]
- **Tests**: `TST-UI-314`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `IntegerFormatter.java`, `CadenceFormatter.java`, `SensorType.java`
- **Rationale**: The system SHALL format cycling power as a whole rounded integer without decimal places and enforce mathematical half-up rounding across all integer-based telemetry metrics (ATT-3229, refining `REQ-UI-167` and complementing `REQ-UI-015`):
1. *Mathematical Rounding in IntegerFormatter (`IntegerFormatter.java`)*:
• `IntegerFormatter.java` SHALL format incoming `Number` instances via mathematical half-up rounding: `Long.toString(Math.round(value.doubleValue()))`.
• When `value == null`, or `Double.isNaN(value.doubleValue())`, or `Double.isInfinite(value.doubleValue())`, `IntegerFormatter` SHALL return `--`.
• Downward truncation via `value.intValue()` SHALL be completely excised.
2. *Cadence Formatter Consolidation (`CadenceFormatter.java`)*:
• `CadenceFormatter.java` SHALL delegate directly to `IntegerFormatter` or reuse its rounding logic, ensuring consistent rounding across all cadence views.
3. *SensorType Formatter Configuration (`SensorType.java`)*:
• In `SensorType.java`, `SensorType.POWER` SHALL be configured with `new IntegerFormatter()`, replacing `DefaultNumberFormatter()`. Filtered and instantaneous power values formatted via `SensorType.POWER.getMyFormatter()` SHALL format as whole integers without decimal fractions (e.g., `250` instead of `250.4`).
• In `SensorType.java`, `SensorType.CADENCE` SHALL be configured with `new IntegerFormatter()`.
4. *Preservation of System Invariants*:
• Raw sensor readings and filtered calculations in memory and SQLite storage (`WorkoutSamples.db`) retain full double-precision floating point fidelity; formatting is strictly presentation-tier.
• Non-integer metrics (`SPEED_mps`, `ALTITUDE`, `DISTANCE_m`, `PACE_spm`) remain unaffected.
• 100% clean-room test pass rate across all suites.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Refines `REQ-UI-167` (*Intuitive Smoothing Presets, Simplified Filter Configuration & Smart Athletic Defaults*, Sprint 2026-40.4, ATT-1276) and complements `REQ-UI-015` (*Unified Metric Component System*).
2. *Historical Origin & Commit Trace*: Baseline ANT+ codebase (`commit 5df80cf4`). When smoothing filters were introduced in `ATT-1276` and `ATT-1624`, power output became a floating-point `Double`. Because `SensorType.POWER` was left on `DefaultNumberFormatter`, it exposed the decimal fraction.
3. *Root Reason for Existing Formulation*: Initially, raw sensors provided discrete integer readings directly from ANT+ packets, where truncation via `intValue()` was innocuous. With floating-point filter calculations, truncation causes systematic downward rounding error (e.g., `89.9` -> `89`), and power displayed decimal fractions (`250.4`).
4. *Preservation of Core Invariants*: Full double precision retained in database, `null` and invalid values display `--`, non-integer metrics unaffected, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout tracking session or TCX replay with a cycling power meter,
• *When* viewing a cockpit tile configured with filtered power (e.g. 3-second moving average, 5-second smoothing, or average power),
• *Then* the displayed value is a rounded whole integer without decimal places (e.g., `250` instead of `250.4`).
• *Given* filtered cadence telemetry of 89.8 rpm,
• *When* displayed in a cadence tile or formatted via `SensorType.CADENCE.getMyFormatter()`,
• *Then* the value rounds to `90` rpm instead of truncating to `89` rpm.
• *Given* filtered heart rate telemetry of 154.6 bpm,
• *When* displayed in an HR tile or formatted via `SensorType.HR.getMyFormatter()`,
• *Then* the value rounds to `155` bpm instead of truncating to `154` bpm.
• *Given* no sensor connected (`null` value) or invalid calculation (`NaN` / `Infinite`),
• *When* formatted by `IntegerFormatter` or `CadenceFormatter`,
• *Then* the formatter returns `--`.

**Invariants**: Full double precision retained in SQLite storage, null fallback `--`, non-integer formatters unchanged, and 100% test pass rate MUST NOT be broken.

### REQ-UI-355: Active Route Full Elevation Profile with Map-Matched Position Marker, Categorized Climbs & Per-Tab Rolling Window Scoping During Tracking [Verified]
- **Tests**: `TST-UI-315`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorGridScreen.kt`, `TrackingViewModel.kt`, `TrackingElevationProfileResolver.kt`, `ElevationProfile.kt`
- **Rationale**: The system SHALL dynamically switch the live tracking elevation profile data source to the active navigated route when a route is actively followed, displaying route elevation, categorized climbs, and a map-matched position progress marker, and SHALL support individual per-tab visible window scoping and rolling lookahead configuration (ATT-3199, ATT-3317, extending `REQ-UI-346` and aligning with Design Guidelines §2.3):
1. *Contextual Elevation Profile Switching (`SensorGridScreen.kt`)*:
• The system SHALL identify whether an active navigated route exists: `val activeRoute = state.mapRoutes.firstOrNull { it.isActiveNavigation }`.
• The system SHALL evaluate whether the active route contains valid elevation data: `val hasRouteElevation = activeRoute != null && activeRoute.path.size >= 2 && activeRoute.path.any { it.altitude != 0.0 }`.
• When `hasRouteElevation == true`: (a) Profile path points SHALL be resolved via `TrackingElevationProfileResolver`; (b) Profile climbs SHALL be resolved via `TrackingElevationProfileResolver`.
• When `hasRouteElevation == false`: (a) Profile path points SHALL fall back to `state.pathPoints`; (b) Profile climbs SHALL fall back to `emptyList()`.
• The elevation profile SHALL be rendered if and only if `state.showElevationProfile && profilePoints.size >= 2`.
2. *Map-Matched Progress Marker Projection (`SensorGridScreen.kt`)*:
• When following an active route with elevation data (`hasRouteElevation == true`): (a) The system SHALL collect the athlete's current location from `currentLocationFlow: StateFlow<LatLng?>`; (b) When `currentLocation != null`, the marker distance SHALL be computed via orthogonal segment projection: $d = \text{PolylineProgressTracker.projectLocation}(\text{activeRoute.path}, \text{currentLocation})\text{.alongTrackDistance}$; (c) When `currentLocation == null`, the marker distance SHALL default to $0.0\text{m}$.
• When tracking without an active route (`hasRouteElevation == false`), the marker distance SHALL default to `state.pathPoints.lastOrNull()?.distance`.
3. *Categorized Route Climbs Overlay*:
• The system SHALL pass `climbs = profileClimbs` to `ElevationProfile`, rendering detected climbs along the baseline with their standardized category colors (CAT 4, CAT 3, CAT 2, CAT 1, HC) per `REQ-UI-299` and `REQ-UI-307`.
4. *Preservation of System Invariants*:
• Canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) from `calculateElevationProfileHeight(range)` and non-weighted wrapping `Surface` per `REQ-UI-346` MUST remain strictly preserved.
• Sensor grid flexible scrolling (`Modifier.weight(1f, fill = false)`) MUST remain strictly preserved.
• Free workout tracking without an active route MUST function identically to prior implementation.
• Zoom controls remain suppressed during live tracking (`showZoomControls = false`).
• 100% clean-room test pass rate across all test tasks.
5. *Per-Tab Elevation Profile Scope & Rolling Lookahead Window (`ATT-3317`)*:
• The system SHALL support individual per-tab configuration of elevation profile scope: `FULL_ROUTE` (0, default macro view) vs `ROLLING_WINDOW` (1, focused micro view).
• When `ROLLING_WINDOW` is active, the visible window SHALL be computed from the configured lookahead distance $D_{\text{forward}} \in \{500\text{m}, 1000\text{m}, 2000\text{m}, 5000\text{m}, 10000\text{m}\}$ (default 2,000 m).
• The athlete position marker SHALL be centered at 15% from the left edge of the visible window ($D_{\text{behind}} = \frac{3}{17} D_{\text{forward}}$), with window start and end clamped within $[0.0, D_{\text{route}}]$.
• Path points within $[W_{\text{start}}, W_{\text{end}}]$ SHALL be dynamically sliced and interpolated at window boundaries; categorized climbs intersecting the window SHALL be retained with their relative coordinates.
• Settings SHALL persist per tab in `VIEWS_TABLE` (DB version 13).
• In `ScreenMode.CONFIGURATION`, when `showElevationProfile == true`, the UI SHALL provide a scope selector (Full Route / Rolling Window) and lookahead distance chips (500m, 1km, 2km, 5km, 10km).
• Localization parity SHALL be maintained across all 9 supported languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends and extends `REQ-UI-355` (*Active Route Full Elevation Profile with Map-Matched Position Marker and Categorized Climbs During Tracking*, `ATT-3199`), targeting `TrackingElevationProfileResolver.kt`, `SensorGridScreen.kt`, `ElevationProfile.kt`, `TrackingViewsDatabaseManager.java`, `TrackingViewsRepository.kt`, `TrackingViewModel.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-41.10 (`ATT-3199`, commit `e0197e08`).
3. *Root Reason for Existing Formulation*: `ATT-3199` provided full route elevation profile rendering to give athletes forward visibility instead of only showing historical recorded points. However, on long courses (50–150 km), rendering the entire route uniformly squashes micro-terrain and immediate hill gradients. The athlete requested per-tab configurable scoping to view the full route on one tab (e.g. Map) and a detailed rolling lookahead window on another tab (e.g. Cockpit).
4. *Preservation of Core Invariants*: Full Route behavior remains completely intact and default; free tracking fallback remains intact; categorized climb overlays and map-matched orthogonal position projection remain intact; non-weighted wrapping `Surface` and adaptive height calculations remain intact; 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout tracking tab with `showElevationProfile = true` and no active route,
• *When* viewing the tracking screen with recorded track points,
• *Then* the elevation profile displays the recorded workout track so far with the marker at the leading right edge.
• *Given* an active workout tracking tab with `showElevationProfile = true` and an active navigated route with elevation data in `FULL_ROUTE` scope mode,
• *When* viewing the tracking screen,
• *Then* the elevation profile displays the complete route from 0 km to finish.
• *Given* an active route being navigated with elevation data in `FULL_ROUTE` mode,
• *When* the athlete progresses along the course,
• *Then* the position marker advances smoothly from left to right at the athlete's map-matched route distance.
• *Given* an active route being navigated with a tracking tab configured with scope = `ROLLING_WINDOW` and lookahead = 2,000m,
• *When* the athlete is at route kilometer 10.0,
• *Then* the elevation profile renders a sliced path covering from approximately km 9.647 to km 12.0, with the athlete marker positioned at 15% from the left edge.
• *Given* an athlete tracking within the first 100m of a navigated route in `ROLLING_WINDOW` mode,
• *When* viewing the elevation profile,
• *Then* the window start is clamped to 0.0m and the athlete marker advances smoothly towards 15%.
• *Given* an athlete approaching within lookahead distance of the route finish line in `ROLLING_WINDOW` mode,
• *When* viewing the elevation profile,
• *Then* the window end is clamped to the route finish distance and the athlete marker advances smoothly across the finish line.
• *Given* an active navigated route containing categorized climbs,
• *When* viewing the route elevation profile during tracking,
• *Then* the climbs are highlighted with their respective category colors (CAT 4/3/2/1/HC) along the baseline.
• *Given* an active route without elevation data (all altitudes are 0.0 or path has fewer than 2 points),
• *When* viewing the tracking screen,
• *Then* the app gracefully falls back to displaying the recorded workout's elevation profile without crashing or flatlining.

**Invariants**: Canonical adaptive height, non-weighted wrapping Surface, Full Route default, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-356: Workout Start & In-Ride Low Sensor Battery Alert Snackbars with Navigation Bar Inset Compliance & Semantic Severity Color Tiering [Verified]
- **Tests**: `TST-UI-316`, `TST-STB-019`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingTabsScreen.kt`, `WorkoutSummariesTabbedScreen.kt`, `MainActivityWithNavigation.kt`, `WorkoutNavigationEvents.kt`, `Color.kt`
- **Rationale**: The system SHALL evaluate paired and active sensors at workout start and during active live tracking, dispatching non-blocking Material 3 alert snackbars with system navigation bar inset compliance, floating control clearance, and dedicated semantic severity color tiering (ATT-3313, ATT-3451, refining and extending `REQ-UI-350` and `REQ-CON-007`):
1. *Workout Start Evaluation & Severity Tiering*:
• Upon tracking launch (`REQUEST_START_TRACKING`), the system SHALL asynchronously evaluate all paired remote devices in SQLite storage (`DevicesDbHelper.PAIRED > 0`).
• If one or more paired remote sensors have `LAST_BATTERY_PERCENTAGE >= 0` and `<= 20`, the system SHALL emit a `LowBatteryAlert` event via `WorkoutNavigationEvents.triggerLowBatteryAlert(...)`.
• The alert SHALL assign `BatteryAlertSeverity.CRITICAL` if any alerted sensor has battery $\le 10\%$, and `BatteryAlertSeverity.WARNING` if all alerted sensors have battery in $[11, 20]$.
2. *In-Ride Real-Time Battery Drop & Escalation Evaluation*:
• During active tracking (`TrackingMode.TRACKING` or `TrackingMode.PAUSED`), whenever an active session sensor reports a battery percentage in $[0, 20]$ via `DeviceDataRepository.deviceBatteryUpdates`, the system SHALL evaluate the sensor's alert state.
• If the sensor has not yet alerted in the session, a `LowBatteryAlert` event SHALL be emitted with the appropriate severity (`WARNING` for $11\dots 20\%$, `CRITICAL` for $\le 10\%$).
• If the sensor previously triggered a `WARNING` alert, and subsequently drops to $\le 10\%$, the system SHALL emit an escalated `CRITICAL` alert.
• Repeated updates within the same severity tier SHALL be suppressed by session deduplication.
• Session deduplication sets SHALL be cleared whenever tracking is stopped or a new workout is created.
3. *Tracking & Aftermath Screen Presentation & Preemption (`TrackingTabsScreen.kt`, `WorkoutSummariesTabbedScreen.kt`)*:
• Both screens SHALL host a `SnackbarHost` collecting `WorkoutNavigationEvents.lowBatteryAlert` and displaying `BatterySnackbarVisuals`.
• *Instantaneous Preemption*: When a `CRITICAL` alert arrives while a snackbar is active, the active snackbar SHALL be immediately dismissed via `snackbarHostState.currentSnackbarData?.dismiss()` so the critical alert renders without delay.
• *Warning Tier Styling*: Amber container (`LightWarningContainer` / `DarkWarningContainer`), deep brown/pale amber text (`LightOnWarningContainer` / `DarkOnWarningContainer`), and high-contrast amber action button (`LightWarningAction` / `DarkWarningAction`) with WCAG contrast $\ge 7:1$.
• *Critical Tier Styling*: Red/error container (`MaterialTheme.colorScheme.errorContainer`), high-contrast text (`onErrorContainer`), and action button (`error`).
• *Non-Battery Fallback*: General non-battery snackbars SHALL retain standard Material 3 colors (`inverseSurface` / `inverseOnSurface` / `primary`).
• Action `[Zu Sensoren]` (`R.string.sensor_battery_action_view_sensors`) SHALL navigate to `R.id.drawer_my_sensors` via `MainActivityWithNavigation.navigateToDrawerItem()` and clear the alert via `consumeLowBatteryAlert()`.
4. *Navigation Bar Inset & Floating Control Ergonomics*:
• The `SnackbarHost` in `TrackingTabsScreen.kt` SHALL configure `Modifier.navigationBarsPadding()` ensuring zero visual occlusion by the Android system navigation bar or gesture handle.
• When the `LapButton` is visible and active on the current tab, the `SnackbarHost` SHALL apply additional vertical clearance (`bottom = 64.dp`), ensuring the snackbar floats cleanly above the lap button without overlapping. When the lap button is suppressed, standard padding (`bottom = 8.dp`) SHALL be applied.
5. *Normal Battery Neutrality*:
• When all paired and active sensors have battery $> 20\%$ (or negative unmeasured values), zero snackbars SHALL be triggered.
6. *100% 9-Language Localization Parity*:
• All snackbar messages and action labels SHALL maintain 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Amends and extends `REQ-UI-350` (*Edge-to-Edge System Navigation Bar Inset Padding for Post-Workout Alert Snackbars and Action Controls*, `ATT-3167`) and `REQ-CON-007` (*Modernized Post-Workout Low Sensor Battery Alerting*, `ATT-2192`), targeting `TrackingTabsScreen.kt`, `WorkoutSummariesTabbedScreen.kt`, `MainActivityWithNavigation.kt`, and `WorkoutNavigationEvents.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.16 (`ATT-2192`), Sprint 2026-41.9 (`ATT-3167`), and Sprint 2026-42.1 (`ATT-3313`).
3. *Root Reason for Existing Formulation*: Low battery alerting was previously restricted to workout completion and then expanded to tracking HUD in ATT-3313 using primary blue as a safe placeholder. ATT-3451 introduces semantic color tiering (amber warning, red critical) and preemption.
4. *Preservation of Core Invariants*: The 20% critical threshold, non-blocking M3 snackbar ergonomics, system navigation bar padding (`navigationBarsPadding()`), action navigation to `drawer_my_sensors`, sticky event consumption, and 100% clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a paired sensor with recorded battery percentage 15% in the database,
• *When* the athlete starts a workout,
• *Then* a snackbar is displayed on `TrackingTabsScreen` with amber container and high-contrast text stating `⚠️ [Sensor Name]: Akku schwach (15%)` with action `[Zu Sensoren]` in high-contrast amber.
• *Given* an active workout tracking session where an active sensor drops to 8%,
• *When* the alert is triggered,
• *Then* the snackbar renders in red `errorContainer` and `onErrorContainer`.
• *Given* an active sensor that alerted at 18% (warning),
• *When* the sensor subsequently drops to 9% (critical),
• *Then* an escalated critical alert is triggered.
• *Given* a warning snackbar currently resting on screen when a critical alert arrives,
• *When* processed,
• *Then* the active snackbar is immediately dismissed and replaced by the critical alert.
• *Given* a low battery snackbar displayed on `TrackingTabsScreen`,
• *When* the athlete taps `[Zu Sensoren]`,
• *Then* the app navigates to "Meine Sensoren" and clears the active alert.

**Invariants**: Non-blocking M3 snackbars, 20% threshold, navigation bar insets, session deduplication, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-357: Dedicated Full-Screen Advanced Settings Screen Architecture, Zero-Dismissal Scrolling, and Lifecycle-Driven Category Reorganization [Verified]
- **Tests**: `TST-UI-317`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AdvancedTuningScreen.kt`, `AdvancedTuningAccordion.kt`, `NavRoutes.kt`, `ATrainingTrackerApp.kt`
- **Rationale**: The system SHALL migrate Advanced Settings from a nested `ModalBottomSheet` into a dedicated full-screen Compose destination `AdvancedTuningScreen.kt` (`NavRoutes.ADVANCED_SETTINGS`), eliminating accidental drag-dismissals during list scrolling, and SHALL restructure the category hierarchy into an intuitive 3-phase athletic lifecycle sequence (ATT-3344, amending `REQ-SET-073`, `REQ-SET-074`, and `REQ-UI-222`):
1. *Dedicated Full-Screen Screen Architecture (`AdvancedTuningScreen.kt`)*:
• The system SHALL host Advanced Settings within a Material 3 `Scaffold` in `AdvancedTuningScreen.kt`.
• The screen SHALL include a `TopAppBar` rendering the localized title `R.string.advanced_tuning_title`, a navigation back arrow (`Icons.AutoMirrored.Filled.ArrowBack`) calling `onNavigateBack()`, and an action icon button to restore factory defaults (`Icons.Default.RestartAlt`, `R.string.reset_to_defaults`).
• The screen SHALL provide a sticky bottom bar or action control hosting Save (`R.string.save`) and Cancel actions (`AppDialogActions.SaveCancel`).
• Tapping Back or invoking system back gesture SHALL safely pop back to the previous screen without saving uncommitted edits; tapping Save SHALL atomically persist all settings before navigating back.
2. *Elimination of Scroll Dismissals (Lösung A)*:
• Advanced Settings SHALL NOT be hosted inside `ModalBottomSheet` or `BottomSheetScaffold`.
• The content container SHALL utilize full-height vertical scrolling (`Modifier.fillMaxSize().verticalScroll(rememberScrollState())`) decoupled from modal dismiss gestures, guaranteeing zero accidental closures upon upward scrolling or momentum flings.
3. *Navigation & Route Integration (`NavRoutes.kt`, `ATrainingTrackerApp.kt`)*:
• `NavRoutes` SHALL define `ADVANCED_SETTINGS = "advanced_settings"`.
• `NavRoutes.fromDrawerItemId(R.id.drawer_advanced_tuning)` SHALL return `NavRoutes.ADVANCED_SETTINGS`.
• `NavRoutes.toDrawerItemId(NavRoutes.ADVANCED_SETTINGS)` SHALL return `R.id.drawer_advanced_tuning`.
• `NavRoutes.toBottomSheetType(R.id.drawer_advanced_tuning)` SHALL return `null`.
• `ATrainingTrackerApp.kt` SHALL register `composable(NavRoutes.ADVANCED_SETTINGS)` in `NavHost`.
• `DisplaySettingsDialog.kt`'s tuning link (`onNavigateToTuning`) SHALL navigate directly to `NavRoutes.ADVANCED_SETTINGS`.
4. *Lifecycle-Driven Category Reorganization (`TuningSection`)*:
• The category enum `TuningSection` and the accordion layout SHALL enforce the canonical 7-section sequence:
1. `NAVIGATION`: Navigation & Kartenführung (`R.string.tuning_cat_navigation`, promoted to Section 1).
2. `COCKPIT_TYPOGRAPHY`: Cockpit-Typografie & Sensorfelder (`R.string.tuning_cat_cockpit_typography`).
3. `BATTERY_SAVER`: AMOLED & Akkuschoner (`R.string.tuning_cat_battery_saver`).
4. `SENSORS_GPS`: Sensoren & GPS-Filterung (`R.string.tuning_cat_sensors_gps`).
5. `SENSOR_SEARCH`: Sensorsuche & Verbindungen (`R.string.tuning_cat_sensor_search`).
6. `WORKOUT_MASKS_CARDS`: Workout-Masken & Detailkarten (`R.string.tuning_cat_workout_masks_cards`).
7. `AFTERMATH_ANALYSIS`: Aftermath & Analyse (`R.string.tuning_cat_aftermath`).
5. *Preservation of Invariants & Modularity*:
• All 25+ parameter DataStore keys, default values, and clamping bounds MUST remain strictly identical.
• Modularity constraint: Every file in `ui.settings.tuning` MUST remain strictly < 400 lines of code (`AdvancedTuningModularityTest`).
• All accordion cards retain `RoundedCornerShape(12.dp)`, `surfaceVariant`, `outlineVariant`, animated chevron rotation, live active-value summary subtitles, and initially collapsed state (`emptySet()`).
• 100% 9-language translation parity maintained across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends and modernizes `REQ-SET-073` (*Dedicated Advanced Tuning Preferences Screen with Parameter Clamping and Reset-to-Defaults*, `ATT-1304`), `REQ-SET-074` (*Expert Settings Drawer Positioning*, `ATT-1646`), and `REQ-UI-222` (*Accordion Architecture for Advanced Settings*, `ATT-1821`, `ATT-1957`).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.4 (`ATT-1304`), Sprint 2026-40.8 (`ATT-1821`), Sprint 2026-40.9 (`ATT-1957`), and Sprint 2026-40.10 (`ATT-2033`).
3. *Root Reason for Existing Formulation*: Advanced Tuning was embedded in `ModalBottomSheet` when settings modals were being unified. As tuning options grew across 7 subsections with nested scroll, scrolling to the top triggered parent sheet swipe-to-dismiss, accidentally dumping uncommitted user edits. Furthermore, Navigation options were relegated to the bottom, separating them from Cockpit options.
4. *Preservation of Core Invariants*: Parameter schemas, DataStore persistence (`TuningConfig`), factory reset, category composables, advisory warning card, and 400-line modularity boundaries remain 100% strictly intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete opening Advanced Settings from the drawer or Display Settings dialog,
• *When* the screen renders,
• *Then* it displays as a dedicated full-screen screen with `TopAppBar` and back arrow, decoupled from `ModalBottomSheet`.
• *Given* the athlete browsing settings,
• *When* expanding accordions and rapidly scrolling up/down across the full list,
• *Then* the screen remains steadily open and never dismisses due to scroll overscroll gestures.
• *Given* the category list,
• *When* inspecting the first section,
• *Then* Navigation & Kartenführung appears as Section 1, followed by Cockpit, AMOLED Battery Saver, Sensors, Sensor Search, Workout Masks, and Aftermath Analysis.
• *Given* modified tuning parameters,
• *When* the athlete taps Back without saving,
• *Then* uncommitted changes are discarded and the screen pops backstack.
• *Given* modified tuning parameters,
• *When* the athlete taps Save,
• *Then* all changes are atomically committed to DataStore and SharedPreferences and the screen pops backstack.

**Invariants**: Parameter ranges, DataStore keys, warning card, factory reset, zero tonal elevation, 400-line file limit, and 9-language localization parity MUST NOT be broken.

### REQ-UI-358: Dynamic Cockpit Viewport Allocation, Asymmetric Map Weight Expansion & Bounded Sensor Grid Containment [Verified]
- **Tests**: `TST-UI-319`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorGridScreen.kt`
- **Rationale**: The system SHALL ensure that when a tracking tab displays both a sensor grid and an embedded map view (`showMap == true`), the map expands flexibly to consume 100% of all vertical space remaining between the sensor grid and the elevation profile (or bottom cockpit boundary), eliminating dead white space, while bounding multi-row sensor grids to at most 50% of available viewport height with internal vertical scrolling (ATT-3343, amending `REQ-UI-346` and `REQ-UI-339`, complementing `REQ-UI-355`):
1. *Available Viewport Measurement (`SensorGridScreen.kt`)*:
• The main tracking cockpit container in `SensorGridScreen.kt` SHALL be hosted within `BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding()))`.
• The available height `val availableHeight = maxHeight` SHALL constrain child elements proportionally.
2. *Asymmetric Weight Allocation for Sensor Grid & Map*:
• When `state.mapState.showMap == true`:
- The sensor grid `Column` SHALL NOT declare `Modifier.weight(...)`.
- The sensor grid `Column` SHALL declare `Modifier.fillMaxWidth().heightIn(max = availableHeight * 0.5f).verticalScroll(rememberScrollState())`.
- For 1–2 rows, the sensor grid measures at its exact intrinsic content height; for 4–6 rows, it is capped at 50% of `availableHeight` and scrolls internally.
- The map `Box` SHALL declare `Modifier.fillMaxWidth().weight(1f, fill = true)`.
- Because the map is the sole weighted child in the Column, it expands flexibly to occupy 100% of all vertical space remaining after measuring the sensor grid and elevation profile.
• When `state.mapState.showMap == false`:
- The sensor grid `Column` SHALL declare `Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).weight(1f, fill = false)`, preserving the exact canonical behavior of `REQ-UI-346`.
3. *Elevation Profile Anchoring & Adaptive Height Preservation*:
• The elevation profile `Surface` SHALL remain non-weighted with `Modifier.fillMaxWidth().padding(top = 8.dp)`.
• The elevation profile SHALL continue to calculate its height adaptively between $70\text{dp}$ and $200\text{dp}$ via `calculateElevationProfileHeight(range)` per `REQ-UI-346`.
• When the elevation profile is toggled off or contains fewer than 2 points, the map expands seamlessly down to the bottom screen boundary.
4. *Preservation of System Invariants*:
• Zero regression to `TrackingMapContainer` recomposition barrier isolation (`REQ-UI-326`).
• Ambient navigation overlays, live climb sheets, and configuration mode controls remain fully functional.
• 100% clean-room unit test pass rate across the full test suite.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-346` (*Canonical Adaptive Height & Weight Decoupling for Sole Elevation Profile on Tracking Screen*, Sprint 2026-41.9, ATT-3173) and `REQ-UI-339` (*Dynamic Cockpit Weight Allocation & Flexible Vertical Expansion for Elevation Profile when Map is Disabled*, Sprint 2026-41.8, ATT-3107), and complements `REQ-UI-355` (*Active Route Full Elevation Profile with Map-Matched Position Marker, Categorized Climbs & Per-Tab Rolling Window Scoping During Tracking*, Sprint 2026-41.10 / Sprint 2026-42.1, ATT-3199 & ATT-3317). Targets `SensorGridScreen.kt` and `SensorGridScreenLayoutTest.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-41.8 (`ATT-3107`, commit `8d6ef4e1`) introduced `Modifier.weight(1f, fill = false)` on the sensor grid. Sprint 2026-41.9 (`ATT-3173`, commit `ad0c2020`) removed weight from the elevation profile.
3. *Root Reason for Existing Formulation*: In `ATT-3107`, `weight(1f, fill = false)` was placed on the sensor grid so it wraps 1–2 rows and scrolls for many rows. However, when `showMap == true`, the map was also given `weight(1f)`. In Compose Column, weighted children divide available space proportionally. With `fill = false`, the sensor grid only consumes intrinsic height, but Compose does not donate unused quota to the other weighted child (the map). The map remains capped at 50%, leaving the remaining space blank as dead white space.
4. *Preservation of Core Invariants*: Sensor grid scrolling, canonical adaptive height ($70\text{dp} \dots 200\text{dp}$), non-weighted wrapping elevation profile Surface, map recomposition barrier isolation, ambient overlays, and 100% full-suite test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a tracking view configured with 1 row of sensor fields, a map, and an elevation profile,
• *When* viewing the tracking screen,
• *Then* the map expands to fill all remaining vertical space between the sensor row and the elevation profile, with zero dead white space below the elevation profile.
• *Given* a tracking view configured with 5 rows of sensor fields and a map,
• *When* viewing the screen,
• *Then* the sensor grid is capped at 50% of the available height and scrolls smoothly without pushing the map or elevation profile off screen.
• *Given* a tracking view with the map toggled off (`showMap == false`) and elevation profile enabled,
• *When* viewing the tracking screen,
• *Then* the sensor grid wraps content without stretching and the elevation profile renders at its canonical adaptive height directly below it.
• *Given* an active tracking view with map and elevation profile,
• *When* the athlete toggles the elevation profile off in configuration mode,
• *Then* the map dynamically expands to fill the entire vertical space below the sensor grid down to the screen edge.

**Invariants**: Sensor grid scrollability, canonical adaptive height, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-359: Live Segment and Climb Bottom Sheet Gesture Re-Expansion and Peek Tap-to-Expand [Verified]
- **Tests**: `TST-UI-320`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorGridScreen.kt`
- **Rationale**: The system SHALL ensure that when a Strava Live Segment or Route Climb is active during workout tracking (`shouldShowBottomSheet == true`), the athlete can reliably re-expand the bottom sheet (`BottomSheetScaffold`) to full height (`SheetValue.Expanded`) via an upward swipe gesture along the bottom edge or a single tap on the peek header, after the sheet has been collapsed or dismissed (ATT-3341, amending `REQ-UI-343` and `REQ-UI-327`):
1. *Bottom-Edge Swipe-Up Gesture Interceptor (`SensorGridScreen.kt`)*:
• When `shouldShowBottomSheet == true` and `scaffoldState.bottomSheetState.currentValue != SheetValue.Expanded`, the layout SHALL host a gesture detection area aligned at `Alignment.BottomCenter` covering the bottom edge and peek region (`BottomSheetDesign.PeekHeightLiveSegment + navBarHeight`).
• The gesture detector SHALL monitor vertical drag gestures using `pointerInput(Unit)` and `detectVerticalDragGestures`.
• When an upward drag is registered with accumulated vertical drag delta $\Delta y < -20\text{dp}$ (or negative upward velocity), the system SHALL execute `coroutineScope.launch { scaffoldState.bottomSheetState.expand() }`.
2. *Peek Area Tap-to-Expand Interaction*:
• When `scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded`, tapping anywhere on the visible sheet header or gesture target SHALL invoke `coroutineScope.launch { scaffoldState.bottomSheetState.expand() }`.
3. *Touch Neutrality When Inactive*:
• When `shouldShowBottomSheet == false` (or when `screenMode != ScreenMode.TRACKING` or `isTabActive == false`), zero bottom gesture strips or swipe interceptors SHALL be composed or active, and background views (map panning, zoom controls, sensor grid vertical scrolling, configuration mode) SHALL receive 100% of touch events without interference or latency.
4. *Preservation of System Invariants*:
• The initial unconditional animation to `SheetValue.Expanded` on new entity activation (`REQ-UI-343`) SHALL remain fully operative.
• The sheet container shape, elevation, and contour styling per `BottomSheetDesign` SHALL remain intact.
• 100% clean-room test pass rate across the full test suite.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-343` (*Live Segment and Climb Cockpit BottomSheet Default Fully Expanded Presentation*, Sprint 2026-41.9 / Sprint 2026-42.1, ATT-3176 & ATT-3319) and `REQ-UI-327` (*Tracking Tab Live Climb and Segment Cockpit BottomSheet Active-Tab Gating & State Synchronization*, Sprint 2026-41.7, ATT-2945). Targets `SensorGridScreen.kt` and `SensorGridBottomSheetGestureTest.kt`.
2. *Historical Origin & Commit Trace*: `ATT-2945` established the bottom sheet scaffold with active tab gating. `ATT-3319` updated the initial value and reactive launched effect to default to `SheetValue.Expanded` upon entity activation.
3. *Root Reason for Existing Formulation*: In `ATT-3319`, automatic pop-up was implemented so athletes wouldn't have to manually pull the sheet open on every climb. However, when an athlete manually dismissed or collapsed the sheet to inspect underlying sensor grid tiles (power, cadence, lap timer), `activeEntityKey` remained constant, leaving no reactive or gestural mechanism to re-expand the sheet for the rest of the climb/segment.
4. *Preservation of Core Invariants*: Initial expansion on new entity, hidden state when inactive, clean peek height, and zero interference when inactive are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout tracking session with an active Live Segment or Climb whose bottom sheet was swiped down,
• *When* the athlete swipes upward from the bottom of the screen,
• *Then* the bottom sheet smoothly re-expands to `SheetValue.Expanded`, displaying the full elevation profile and pacing HUD.
• *Given* the sheet is resting in `SheetValue.PartiallyExpanded`,
• *When* the athlete taps the visible header / peek area,
• *Then* the sheet smoothly animates to `SheetValue.Expanded`.
• *Given* no live segment or climb is active (`shouldShowBottomSheet == false`),
• *When* the athlete interacts with the lower portion of the screen,
• *Then* sensor grid scrolling and map gestures operate without interference.

**Invariants**: Initial automatic expansion, active-tab gating, inactive touch neutrality, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-360: Turn-by-Turn Navigation Slight Turn Vector Icon Harmonization and Directional Symbol Compose Preview Gallery [Verified]
- **Tests**: `TST-UI-323`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ic_turn_slight_right.xml`, `ic_turn_slight_left.xml`, `TurnDirection.kt`, `TurnPromptBanner.kt`
- **Rationale**: The system SHALL replace the distorted vector path drawables for slight right (`ic_turn_slight_right.xml`) and slight left (`ic_turn_slight_left.xml`) with canonical, bottom-anchored Material Design vector geometries, and SHALL provide an interactive Jetpack Compose `@Preview` gallery displaying all 8 `TurnDirection` symbols across Light and Dark themes (ATT-3455, complementing `REQ-MAP-028` and `REQ-UI-324`):
1. *Canonical Vector Path Replacement (`ic_turn_slight_right.xml`, `ic_turn_slight_left.xml`)*:
• In `ic_turn_slight_right.xml`, the vector path SHALL rise vertically from baseline $y=20$ with standard 2dp stroke width ($x \in [9, 11]$), transition through a smooth 45° curve to the upper right via control point $(9.59, 11.01)$, and conclude with a symmetric 45° arrowhead with 2dp wing thickness: `M12.34,6V4H18v5.66h-2V7.41l-5,5V20h-2v-7.58c0,-0.53 0.21,-1.04 0.59,-1.41l5,-5H12.34z`
• In `ic_turn_slight_left.xml`, the vector path SHALL provide an exact horizontal mirror across the vertical centerline ($x' = 24 - x$): `M11.66,6V4H6v5.66h2V7.41l5,5V20h2v-7.58c0,-0.53 -0.21,-1.04 -0.59,-1.41l-5,-5H11.66z`
• Both assets SHALL preserve standard 24x24dp viewport (`viewportWidth="24"`, `viewportHeight="24"`, `width="24dp"`, `height="24dp"`) and primary tint fill color token `#FF1976D2`.
2. *Interactive Jetpack Compose `@Preview` Gallery (`TurnPromptBanner.kt`)*:
• The system SHALL provide `TurnCueIconsGallery` and multi-theme `@Preview` composables (`TurnCueIconsGalleryLightPreview`, `TurnCueIconsGalleryDarkPreview`) in `TurnPromptBanner.kt`.
• The gallery SHALL display all 8 `TurnDirection` enum entries (`LEFT`, `RIGHT`, `SLIGHT_LEFT`, `SLIGHT_RIGHT`, `SHARP_LEFT`, `SHARP_RIGHT`, `STRAIGHT`, `U_TURN`) in a structured grid/row layout.
• Each item SHALL display the vector icon using `painterResource(id = direction.iconResId)` and the localized direction title via `stringResource(id = direction.displayNameResId)` with appropriate Material 3 typography and colors.
• Dual `@Preview` annotations SHALL support Light Theme and Dark Theme (`uiMode = Configuration.UI_MODE_NIGHT_YES`) with `showBackground = true`.
3. *Visual & Structural Contract Testing*:
• Unit test `TurnDirectionIconContractTest.kt` SHALL programmatically parse each vector XML resource referenced by `TurnDirection.values()`.
• Test SHALL verify XML root element `<vector>`, valid 24x24 viewport, non-empty `android:pathData`, and bottom-stem grounding ($y=19..20$).
• Test SHALL verify mathematical horizontal symmetry between `ic_turn_slight_right` and `ic_turn_slight_left`.
4. *Preservation of System Invariants*:
• Real-time turn-by-turn guidance math (`TurnByTurnNavigationEngine.kt`), angle threshold classification in `TurnCueDetector.kt`, and audio alerting (`NavigationAudioAlertManager.kt`) MUST remain completely unaltered.
• 100% full clean-room regression pass rate (`./gradlew testDebugUnitTest`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-360`). No existing requirement logic modified. Complements `REQ-MAP-028` (*Visual & Auditory Turn-by-Turn Navigation Prompts*, ATT-1450) and `REQ-UI-324` (*Turn-by-Turn Navigation Prompts Top-Level Spatial Overlay Integration & Design Guideline Harmonization*, ATT-2941).
2. *Historical Origin & Commit Trace*: Baseline turn cue drawables were originally added during initial turn prompt development in `ATT-1450` (Sprint 2026-40.16). While cardinal turns (`LEFT`, `RIGHT`, `STRAIGHT`) used clean paths, `SLIGHT_RIGHT` and `SLIGHT_LEFT` accidentally used preliminary SVG trace data.
3. *Root Reason for Existing Formulation*: The original asset was imported from an unvetted vector snippet that lacked bottom stem grounding.
4. *Preservation of Core Invariants*: Viewport 24x24, color `#FF1976D2`, and integration with `TurnDirection` enum and `TurnPromptBanner.kt` remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an upcoming navigation cue with `TurnDirection.SLIGHT_RIGHT`,
• *When* rendering `ic_turn_slight_right.xml` in the navigation banner,
• *Then* the icon displays a clean vertical base rising from the bottom baseline $y=20$ and smoothly veering 45° to the upper-right with a symmetric arrowhead.
• *Given* an upcoming navigation cue with `TurnDirection.SLIGHT_LEFT`,
• *When* rendering `ic_turn_slight_left.xml`,
• *Then* the icon displays the identical, cleanly mirrored 45° veer to the upper-left.
• *Given* a developer viewing Compose previews in Android Studio,
• *When* rendering `TurnCueIconsGalleryPreview`,
• *Then* all 8 turn icons are displayed side by side in a structured grid with their titles in both Light and Dark themes for immediate visual verification.
• *Given* all 8 `TurnDirection` drawables rendered in the preview gallery,
• *When* inspecting the icon set,
• *Then* stem width (2dp), baseline position ($y=19..20$), and arrowhead proportions are visually harmonious across all directions.

**Invariants**: Pure vector XML assets, no changes to route navigation math, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-361: Turn-by-Turn Navigation Sharp Turn Vector Icon Harmonization and Directional Contract Verification [Verified]
- **Tests**: `TST-UI-324`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
3. *Rotational Progression & Preview Gallery Compatibility*:
• The diagonal $135^\circ$ / $-135^\circ$ arrowheads SHALL complete the consistent rotational progression of the turn icon family in `TurnCueIconsGallery` (`Straight 0°` $\uparrow$ $\to$ `Slight Right 45°` $\nearrow$ $\to$ `Right 90°` $\rightarrow$ $\to$ `Sharp Right 135°` $\searrow$ $\to$ `U-Turn 180°` $\downarrow$ $\to$ `Sharp Left -135°` $\swarrow$ $\to$ `Left -90°` $\leftarrow$ $\to$ `Slight Left -45°` $\nwarrow$).
• Sharp turn cues MUST exhibit distinct visual separation from U-turn cues (`ic_turn_u_turn.xml`), preventing false turn-around interpretations by athletes.
4. *Preservation of System Invariants*:
• Real-time turn-by-turn guidance math (`TurnByTurnNavigationEngine.kt`), angle threshold classification in `TurnCueDetector.kt`, and audio alerting (`NavigationAudioAlertManager.kt`) MUST remain completely unaltered.
• 100% full clean-room regression pass rate (`./gradlew testDebugUnitTest`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-361` (*Turn-by-Turn Navigation Sharp Turn Vector Icon Harmonization and Directional Contract Verification*, ATT-3493) and `TST-UI-324`. Complements `REQ-MAP-028` (*Visual & Auditory Turn-by-Turn Navigation Prompts*, ATT-1450), `REQ-UI-324` (*Turn-by-Turn Navigation Prompts Overlay*, ATT-2941), and `REQ-UI-360` (*Turn-by-Turn Navigation Slight Turn Harmonization*, ATT-3455).
2. *Historical Origin & Commit Trace*: Originally introduced in commit `39610f62` (ATT-3493, Sprint 2026-42.3).
3. *Root Reason for Existing Formulation*: The author in ATT-3493 sought to ground sharp turns to baseline $y=20$ and add downward arrowheads, but accidentally formulated a $180^\circ$ semicircle arch spanning across to $x=18$ paired with a vertical downward shaft and a downward-pointing arrow tip, unintentionally mirroring the exact visual silhouette of `ic_turn_u_turn.xml`.
4. *Preservation of Core Invariants*: Baseline grounding at $y=20$ with uniform 2dp stroke width, standard 24x24dp viewport, `#FF1976D2` tint fill token, contract safety comments, and zero modifications to navigation route heuristics or angle thresholds are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an upcoming navigation cue with `TurnDirection.SHARP_RIGHT`,
• *When* rendering `ic_turn_sharp_right.xml` in the navigation banner,
• *Then* the icon displays a clean vertical base rising from bottom baseline $y=20$, looping through an acute ~135° bend, and exiting with a diagonal shaft and arrowhead pointing South-East ($135^\circ$, $\searrow$), with zero resemblance to a $180^\circ$ U-turn.
• *Given* an upcoming navigation cue with `TurnDirection.SHARP_LEFT`,
• *When* rendering `ic_turn_sharp_left.xml`,
• *Then* the icon displays the identical, cleanly mirrored acute bend and diagonal arrowhead pointing South-West ($-135^\circ$, $\swarrow$).
• *Given* all 8 turn icons displayed in `TurnCueIconsGallery`,
• *When* visually inspecting the rotational sequence from straight to U-turn,
• *Then* each step progresses in consistent, unambiguous angular increments with harmonious 2dp stem widths, baseline grounding, and canonical arrowheads.
• *Given* the vector definitions of sharp right and sharp left,
• *When* parsed by `TurnDirectionIconContractTest.kt`,
• *Then* all 22 path milestone coordinates reflect across $x=12$ with zero divergence ($x_R + x_L = 24.0 \pm 0.01$, $
- **Implementation**: y_R - y_L
- **Rationale**: The system SHALL refine the vector path drawables for sharp right (`ic_turn_sharp_right.xml`) and sharp left (`ic_turn_sharp_left.xml`) with canonical, bottom-anchored Material Design vector geometries featuring an acute ~135° deflection bend and diagonal downward-pointing arrowheads ($\searrow$ and $\swarrow$), completely eliminating visual resemblance to the 180° circular dome loop of U-turns, and SHALL enforce visual and mathematical reflection symmetry in contract testing (ATT-3493, ATT-3573, complementing `REQ-MAP-028`, `REQ-UI-324`, and `REQ-UI-360`):
1. *Canonical Vector Path Replacement (`ic_turn_sharp_right.xml`, `ic_turn_sharp_left.xml`)*:
• In `ic_turn_sharp_right.xml`, the vector path SHALL rise vertically from baseline $y=20$ with standard 2dp stroke width ($x \in [6, 8]$) to $y=8.5$, bend sharply around an acute ~135° deflection arc ($cx=10.5, cy=8.5$, outer radius $R=4.5$, inner radius $r=2.5$), extend diagonally along $(+1, +1)$, and terminate with a diagonal downward-pointing $135^\circ$ arrowhead pointing South-East ($\searrow$): `M6,20h2V8.5c0,-1.38 1.12,-2.5 2.5,-2.5c0.66,0 1.3,0.26 1.77,0.73l2.5,2.5l-1.41,1.41l4.24,0l0,-4.24l-1.41,1.41l-2.5,-2.5c-0.84,-0.84 -1.99,-1.32 -3.18,-1.32c-2.49,0 -4.5,2.01 -4.5,4.5V20z`
• In `ic_turn_sharp_left.xml`, the vector path SHALL provide an exact horizontal reflection across the vertical centerline ($x' = 24 - x, y' = y$): `M18,20h-2V8.5c0,-1.38 -1.12,-2.5 -2.5,-2.5c-0.66,0 -1.3,0.26 -1.77,0.73l-2.5,2.5l1.41,1.41l-4.24,0l0,-4.24l1.41,1.41l2.5,-2.5c0.84,-0.84 1.99,-1.32 3.18,-1.32c2.49,0 4.5,2.01 4.5,4.5V20z`
• Both assets SHALL preserve standard 24x24dp viewport (`viewportWidth="24"`, `viewportHeight="24"`, `width="24dp"`, `height="24dp"`), primary tint fill color token `#FF1976D2`, and contract safety header comment `<!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-324 -->`.
2. *Mathematical Reflection Symmetry & Baseline Grounding Contract Tests*:
• Unit test `TurnDirectionIconContractTest.kt` SHALL programmatically parse each vector XML resource for sharp right and sharp left.
• Test SHALL verify XML root element `<vector>`, valid 24x24 viewport, non-empty `android:pathData`, and bottom-stem grounding at $y=20$ (`M6,20h2` and `V20z` for right; `M18,20h-2` and `V20z` for left).
• Test SHALL verify mathematical horizontal reflection symmetry between `ic_turn_sharp_right` and `ic_turn_sharp_left` across midline $x=12$ ($x_R + x_L = 24.0 \pm 0.01$, $

### REQ-UI-362: Removal of Google Drive Format Scan Option from Workout Import Cards [Verified]
- **Tests**: `TST-UI-325`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ImportBackupTabsScreen.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL remove the Google Drive scan option across all workout format import cards (FIT, TCX, GPX) in `ImportBackupTabsScreen.kt`, eliminating confusing least-privilege OAuth scope restrictions while preserving native Storage Access Framework (SAF) Google Drive file picking and Google Drive full database backup/restore (ATT-3489, amending `REQ-UI-293`):
1. *Composable Format Import Card Decluttering (`ImportBackupTabsScreen.kt`)*:
• In `FormatImportCard`, the system SHALL remove parameters `googleDriveButtonText`, `isGoogleDriveConnected`, and `onGoogleDriveClick`.
• `FormatImportCard` SHALL render exactly two action buttons per card: Button 1 (Local File Picker via SAF) and Button 2 (Dropbox Scan). The third `OutlinedButton` for Google Drive scan SHALL be excised.
• In `ImportTabContent`, the system SHALL remove parameters `isGoogleDriveConnected` and `onGoogleDriveScanClick`, configuring FIT, TCX, and GPX cards strictly with local file and Dropbox handlers.
2. *Screen State & Dialog Simplification (`ImportBackupTabsScreen.kt`)*:
• `ImportBackupTabsScreen` SHALL remove unused state variables `showTuningDialogForGoogleDrive`, `showGoogleDriveDisconnectedDialog`, and `pendingGoogleDriveFormat`.
• The obsolete `showGoogleDriveDisconnectedDialog` alert dialog and `showTuningDialogForGoogleDrive` bottom sheet invocation SHALL be cleanly removed.
3. *String Resource Hygiene (`strings.xml`)*:
• Unused string resources `import_fit_gdrive_button`, `import_tcx_gdrive_button`, and `import_gpx_gdrive_button` SHALL be removed across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero lint or parity regressions.
4. *Preservation of Core Invariants*:
• Google Drive database backup/restore (`.attbackup`) in `BackupTabContent` and automated background export capabilities SHALL remain 100% operational under `drive.file` scope.
• Local multi-document file picking via Android SAF (`ActivityResultContracts.OpenMultipleDocuments()`), supporting Google Drive cloud documents, remains fully preserved.
• Dropbox format scanning across all three format cards (`fit`, `tcx`, `gpx`) remains fully preserved.
• Pre-import cluster parameter tuning (`PreImportTuningBottomSheet`) remains mandatory before local or Dropbox batch imports commence.
• Clean-room unit regression test suite pass rate SHALL remain 100%.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-293` (*Format-Specific Import Tab Architecture with Uniform Multi-Document Source Triad (FIT, TCX, GPX)*, Sprint 2026-41.1, ATT-2623; amended ATT-3315).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2623` (commit `d5a9d217`), Sprint 2026-41.1.
3. *Root Reason for Existing Formulation*: Symmetrically presented Local, Dropbox, and Google Drive for each format. However, least-privilege `drive.file` OAuth scope prevents REST discovery of externally copied files, causing misleading empty scan results. Because SAF natively provides Google Drive file picking without sensitive scopes, the dedicated REST scan button was redundant and misleading.
4. *Preservation of Core Invariants*: Local SAF picking (which imports Google Drive files), Dropbox scanning, pre-import cluster tuning, and Google Drive database backup/restore remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete navigating to the Import tab in `ImportBackupTabsScreen`,
• *When* inspecting the FIT, TCX, and GPX format import cards,
• *Then* each card renders exclusively the Local File Picker button ("Dateien importieren") and the Dropbox Scan button, with zero Google Drive scan buttons present.
• *Given* an athlete importing workouts from Google Drive,
• *When* tapping "Dateien importieren" on any format card,
• *Then* Android SAF document picker opens, allowing selection of `.fit`, `.tcx`, or `.gpx` files stored in Google Drive.
• *Given* an athlete with connected Google Drive,
• *When* accessing the Backup tab or automated export settings,
• *Then* Google Drive backup creation, restore, and automated export remain fully functional.
• *Given* all 9 supported application locales,
• *When* running translation and contract tests,
• *Then* zero orphaned string warnings occur and all contract tests pass.

**Invariants**: Android SAF local file picking, Dropbox format scanning, pre-import cluster parameter tuning, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-363: Recursive Directory Workout File Discovery and Bulk Import via Storage Access Framework [Verified]
- **Tests**: `TST-UI-326`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ImportBackupTabsScreen.kt`, `SafDirectoryScanner.kt`, `BackupRestoreViewModel.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL provide recursive directory workout file discovery and bulk import across all format import cards (FIT, TCX, GPX) using Android Storage Access Framework (SAF) `OpenDocumentTree()`, allowing athletes to bulk-import nested workout folder hierarchies with automated duplicate detection and zero sensitive OAuth scope requirements (ATT-3491, complementing `REQ-UI-293` and `REQ-UI-362`):
1. *Composable Format Import Card & Launcher Integration (`ImportBackupTabsScreen.kt`)*:
• In `FormatImportCard`, the system SHALL introduce `folderButtonText: String` and `onFolderClick: () -> Unit` action parameters.
• `FormatImportCard` SHALL render three action buttons per card: Button 1 (Local File Picker via `OpenMultipleDocuments()`), Button 2 (Directory Folder Scan via `OpenDocumentTree()`), and Button 3 (Dropbox Cloud Scan).
• In `ImportTabContent`, the system SHALL accept `onFolderImportClick: (format: String) -> Unit` and wire FIT, TCX, and GPX cards to trigger directory picking for their respective target format (`fit`, `tcx`, `gpx`).
• In `ImportBackupTabsScreen`, the system SHALL register an `OpenDocumentTree()` activity result launcher (`pickDirectoryLauncher`) that captures the selected directory tree URI and target format (`pendingDirectoryUri`, `pendingDirectoryFormat`).
• Upon tree URI selection, `ImportBackupTabsScreen` SHALL display the pre-import cluster parameter tuning bottom sheet (`PreImportTuningBottomSheet`), confirming cluster tolerances before recursive traversal commences.
2. *Recursive Traversal Engine (`SafDirectoryScanner.kt`)*:
• The system SHALL provide `SafDirectoryScanner` in package `com.atrainingtracker.trainingtracker.migration`.
• `SafDirectoryScanner.scanDirectory(context: Context, treeUri: Uri, targetFormat: String, maxDepth: Int = 6, cancelSignal: (() -> Boolean)? = null): List<Uri>` SHALL recursively traverse `DocumentFile.fromTreeUri(context, treeUri)` subdirectories.
• Traversal SHALL enforce max depth bound (depth $\le$ 6), cycle detection via visited URI set tracking, case-insensitive extension filtering (`.$targetFormat`), and non-blocking coroutine cancellation checks.
3. *Batch Ingestion Pipeline & Post-Import Reconciliation (`BackupRestoreViewModel.kt`)*:
• `BackupRestoreViewModel` SHALL provide `fun importWorkoutDirectory(context: Context, treeUri: Uri, format: String, dispatcher: CoroutineDispatcher = Dispatchers.IO): Job`.
• Progress feedback SHALL report folder scanning state (`R.string.import_directory_scanning`), then transition to sequential file ingestion progress (`R.string.import_workout_files_progress` / `R.string.import_fit_progress`).
• If 0 matching files are discovered, an informative notification (`R.string.import_directory_no_files_found`) SHALL be displayed.
• Discovered files SHALL be ingested sequentially via `LegacyImportEngine`, skipping existing duplicates (`isWorkoutExisting`), and triggering post-import database reconciliation (`WorkoutRepository.loadAllWorkouts()`, `PeriodsRepository.syncPeriodsIfDiscrepancy()`, `WorkoutClusterRepository.refreshClusters()`).
4. *9-Language Localization Parity (`strings.xml`)*:
• String keys `import_fit_folder_button`, `import_tcx_folder_button`, `import_gpx_folder_button`, `import_directory_scanning`, and `import_directory_no_files_found` SHALL maintain 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-363`). Complements `REQ-UI-293` (*Format-Specific Import Tab Architecture with Uniform Multi-Document Source Triad (FIT, TCX, GPX)*) and `REQ-UI-362` (*Removal of Google Drive Format Scan Option from Workout Import Cards*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2623` (commit `d5a9d217`), Sprint 2026-41.1; refined under `ATT-3489` (commit `3961ed8f`), Sprint 2026-42.3.
3. *Root Reason for Existing Formulation*: Following the removal of the direct Google Drive REST API scan (constrained by Google's sensitive scope verification policy), athletes required a non-sensitive, platform-native mechanism to scan and bulk-import folders containing historical workout files across cloud providers (Google Drive, Dropbox, Nextcloud) and local directory trees without OAuth scope friction. Android's native `OpenDocumentTree()` fulfills this need with least-privilege security.
4. *Preservation of Core Invariants*: Local multi-document file picking via `OpenMultipleDocuments()`, Dropbox format scanning, pre-import cluster parameter tuning, and post-import database reconciliation remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete with a folder hierarchy containing nested subdirectories with `.fit`, `.tcx`, or `.gpx` files,
• *When* selecting the root folder via the SAF directory picker and confirming cluster tuning,
• *Then* the app recursively traverses all subfolders up to max depth 6 and identifies all matching workout files.
• *Given* an athlete selecting a folder backed by Google Drive, Dropbox, or local storage in the SAF directory picker,
• *When* the import commences,
• *Then* the app streams and ingests files through the SAF content resolver without prompting for Google Drive OAuth scopes.
• *Given* a directory containing 10 workouts, 4 of which already exist in SQLite,
• *When* directory import executes,
• *Then* 6 new workouts are imported, 4 duplicates are skipped, and the summary displays "6 importiert, 4 Duplikate übersprungen".
• *Given* an empty folder or a folder with no matching extensions,
• *When* traversal concludes,
• *Then* an informative notification displays that no compatible files were found.
• *Given* all 9 supported application locales,
• *When* verifying string resources,
• *Then* all folder import labels, progress text, and empty messages exist and match format specifiers.

**Invariants**: Android SAF least privilege, depth limit bound 6, cycle detection, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-364: Centralized Metric Distance and Altitude Formatting Enforcement Across Navigation and Telemetry Cards (Rule 36) [Verified]
- **Tests**: `TST-UI-327`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ForkDecisionCard.kt`, `ReturnNavigationState.kt`, `RouteSelectionButton.kt`, `RouteSelectorSheet.kt`, `TurnPromptBanner.kt`, `LapSplitVisualizer.kt`, `LiveClimbSheet.kt`, `ClimbOnMapScreen.kt`, `RouteOnMapScreen.kt`, `AltitudeFormatter.java`, `TrainingApplication.java`
- **Rationale**: The system SHALL replace all hardcoded metric template strings ("km", "m") across route selection, active navigation prompts, return navigation HUD, live climb sheets, and lap visualizers with centralized, unit-aware formatters (`DistanceFormatter` and `AltitudeFormatter`), enforcing Rule 36 (Single Source of Truth & DRY) and guaranteeing strict Imperial unit parity (ATT-3361, complementing `REQ-UI-015`, `REQ-UI-016`, `REQ-UI-330`, `REQ-MAP-027`, `REQ-MAP-028`, and `REQ-MAP-029`):
1. *Fork Decision Navigation Card (`ForkDecisionCard.kt`)*:
• In `ForkDecisionCard.kt`, branch distance and elevation metrics SHALL be formatted using `LocalMetricFormatter.current.distance.format_with_units(branch.totalDistanceMeters)` and `"+${LocalMetricFormatter.current.altitude.format_with_units(branch.totalElevationMeters)}"`.
• Hardcoded `String.format(Locale.US, "%.1f km", distKm)` and `"+${branch.totalElevationMeters.roundToInt()} m"` templates SHALL be excised.
2. *Return Navigation & Arrival State (`ReturnNavigationState.kt`)*:
• In `ReturnNavigationState.kt`, `formattedRemainingDistance` SHALL return `DistanceFormatter().format_with_units(remainingDistanceMeters)`.
• In `ReturnNavigationState.kt`, `formattedRemainingClimb` SHALL return `"+${AltitudeFormatter().format_with_units(remainingElevationGainMeters)}"`.
• Hardcoded `String.format(Locale.US, "%.1f km", km)` and `"+$climb m"` templates SHALL be excised.
3. *Route Selection Bar & Button (`RouteSelectionButton.kt` & `RouteSelectorSheet.kt`)*:
• In `RouteSelectionButton.kt`, active route metrics subtitle SHALL format distance via `distanceFormatter.format_with_units(activeRoute.summary.distance)` and elevation via `"+${altitudeFormatter.format_with_units(activeRoute.summary.elevationGain)}"`.
• In `RouteSelectorSheet.kt`, `formatDistanceAndElevation` SHALL format distance and elevation via `DistanceFormatter().format_with_units(distanceMeters)` and `"+${AltitudeFormatter().format_with_units(elevationMeters)}"`.
• Hardcoded `String.format(Locale.getDefault(), "%.1f km", distKm)` and `"+%.0f m"` templates SHALL be excised.
4. *Turn-by-Turn Navigation Prompt Banner (`TurnPromptBanner.kt`)*:
• In `TurnPromptBanner.kt`, remaining distance before turn cue execution SHALL be formatted via `distanceFormatter.format_with_units(distanceRemaining)`.
• Hardcoded `"${distanceRemaining} m"` template SHALL be excised.
5. *Lap Split Visualizer (`LapSplitVisualizer.kt`)*:
• In `LapSplitVisualizer.kt`, `formatSplitDistance` SHALL return `DistanceFormatter().format_with_units(distanceM)`.
• Hardcoded `String.format(Locale.US, "%.2f km", ...)` and `String.format(Locale.US, "%.0f m", ...)` templates SHALL be excised.
6. *Live Climb Sheet & Map Screens (`LiveClimbSheet.kt`, `ClimbOnMapScreen.kt`, `RouteOnMapScreen.kt`)*:
• In `LiveClimbSheet.kt`, remaining elevation gain, finish states, and climb route counter SHALL be formatted via `altitudeFormatter.format_with_units(...)`.
• In `ClimbOnMapScreen.kt` and `RouteOnMapScreen.kt`, hardcoded fallback strings `"${climb.elevationGainMeters.roundToInt()} m"` and `"${(startDistMeters / 1000.0).roundToInt()} km"` SHALL be excised.
7. *Formatter Robustness & Null Safety (`AltitudeFormatter.java`, `DistanceFormatter.java`, `TrainingApplication.java`)*:
• `AltitudeFormatter.format_with_units` and `TrainingApplication.getUnit()` SHALL safeguard against null `ApplicationContext` and null `SharedPreferences` in pure unit test environments without throwing `NullPointerException`.
8. *Preservation of System Invariants*:
• Pure Compose UI state handling, route navigation geometries, audio alerts, and 100% clean-room test pass rate MUST NOT be broken.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-364`). Complements `REQ-UI-015` (*Unified Metric component system*), `REQ-UI-016` (*Centralized metric formatting context*), `REQ-UI-330` (*Turn-by-turn Navigation Prompts Overlay / Fork Decision Card*), `REQ-MAP-027` (*ClimbPro Live Climb Bottom Sheet*), `REQ-MAP-028` (*Turn-by-turn navigation banner*), and `REQ-MAP-029` (*Return Navigation and Dynamic Arrival Metrics*).
2. *Historical Origin & Commit Trace*: Early Compose migration (Sprints 2026-40.16 to 2026-41.8) introduced Compose cards where distance and elevation strings were hand-rolled with raw `"km"` and `"m"` templates.
3. *Root Reason for Existing Formulation*: Quick prototyping in metric locales where developers verified metric formatting, inadvertently bypassing `DistanceFormatter` and `AltitudeFormatter` and breaking Imperial unit support.
4. *Preservation of Core Invariants*: Metric and Imperial unit conversion formulas in `DistanceFormatter` and `AltitudeFormatter`, visual styling of cards, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete with Imperial units selected in settings,
• *When* viewing `TurnPromptBanner`, `ForkDecisionCard`, `RouteSelectionButton`, or `ReturnNavigationHud`,
• *Then* all distance values are formatted with `ft` or `mile`, and elevation values with `ft`, with zero occurrences of raw `km` or `m`.
• *Given* an athlete with Metric units selected in settings,
• *When* viewing these navigation cards,
• *Then* distance and elevation values are formatted with canonical `m` and `km` with standard threshold precision.
• *Given* `LiveClimbSheet` during an active or finished climb,
• *When* viewing remaining distance and elevation gain,
• *Then* metrics route through `DistanceFormatter` and `AltitudeFormatter` without raw inline unit templates.
• *Given* pure JVM unit tests invoking `DistanceFormatter` and `AltitudeFormatter` without an Android application context,
• *When* formatting distances and altitudes,
• *Then* the formatters return valid formatted strings without throwing `NullPointerException`.

**Invariants**: Pure Compose architecture, Single Source of Truth (Rule 36), and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-365: Centralized Speed Unit Conversion, Formatter Harmonization and Progressive Disclosure in Sport Type Editor (Rule 36) [Verified]
- **Tests**: `TST-UI-328`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MyHelper.java`, `TelemetryMetricUtils.kt`, `EditSportTypeDialog.kt`, `SpeedFormatter.java`
- **Rationale**: The system SHALL centralize all speed unit conversions and eliminate manual inline conversion formulas across telemetry graph utilities, sport type dialogs, and speed formatters by routing all conversions strictly through `MyHelper`, and SHALL simplify the sport type configuration dialog by bundling speed thresholds (with auto-detection guidance) and export mappings under an initially collapsed Advanced Options accordion, enforcing Rule 36 (Single Source of Truth) and Design Guidelines Section 3 (*Settings & Accordion Structure*) (ATT-3363, ATT-5821, complementing `REQ-UI-015`, `REQ-UI-016`, and `REQ-UI-364`):
1. *Authoritative Mathematical Conversions in `MyHelper.java`*:
• `MyHelper` SHALL provide symmetrical overloaded methods for bidirectional conversion:
- `mps2userUnit(double speed)` and `mps2userUnit(double speed, @NonNull MyUnits myUnits)`
- `UserUnit2mps(double speed)` and `UserUnit2mps(double speed, @NonNull MyUnits myUnits)`
• The conversion factors SHALL reside strictly within `MyHelper`: METRIC uses factor $3.6$, IMPERIAL uses factor $2.23693629$.
2. *Telemetry Graph & Scrubber Metric Harmonization (`TelemetryMetricUtils.kt`)*:
• In `TelemetryMetricUtils.kt`, `extractMetricValue` for `TelemetryMetricType.SPEED` SHALL delegate conversion to `MyHelper.mps2userUnit(mps, unit)`.
• In `TelemetryMetricUtils.kt`, `formatValue` for `TelemetryMetricType.SPEED` SHALL convert user-domain values to m/s via `MyHelper.UserUnit2mps(value, unit)` prior to passing to `speedFormatter`.
• Inline magic constants `3.6` and `2.236936` in `TelemetryMetricUtils.kt` SHALL be completely excised.
3. *Canonical Numeric Formatting & Locale Resilience in Sport Type Editor (`EditSportTypeDialog.kt`)*:
• In `EditSportTypeDialog.kt`, speed formatting SHALL use standard localized numeric formatting via `String.format(Locale.getDefault(), "%.2f", MyHelper.mps2userUnit(speed))` preserving native decimal formatting without ad-hoc string character replacement (`.replace(",", ".")`).
• In `EditSportTypeDialog.kt`, user input parsing for minimum and maximum speed thresholds SHALL accept both dot and comma decimal delimiters (`it.replace(',', '.').toDoubleOrNull()`), preventing user entry rejection in European/comma-decimal locales.
4. *Formatter DRY Consolidation (`SpeedFormatter.java`)*:
• In `SpeedFormatter.java`, internal unit conversion logic SHALL delegate directly to `MyHelper.mps2userUnit(speed_mps.doubleValue())` instead of duplicating switch-case conversion logic.
5. *Progressive Disclosure & Advanced Options Encapsulation in Sport Type Editor (`EditSportTypeDialog.kt`, `REQ-UI-365.4`)*:
• In `EditSportTypeDialog.kt`, the primary dialog surface SHALL present only everyday configuration fields: Sport Name, Base Sport Type (`bSportType`), Linked Equipment (`MultiSelectEquipmentSpinner`), and Strava Sport Mapping (`stravaName`).
• Technical minimum and maximum speed thresholds (`minSpeed`, `maxSpeed`) SHALL be accompanied by an explanatory hint informing the athlete that these thresholds govern automatic sport type detection during recording (`sport_type_speed_detection_hint`).
• Min/Max speed fields and niche export mappings (TCX Export Name `tcxName`, GoldenCheetah Export Name `gcName`) SHALL be encapsulated inside an expandable accordion section titled `sport_type_advanced_options` ("Erweiterte Optionen") at the bottom of the dialog.
• The accordion section SHALL default to initially collapsed (`isAdvancedExpanded = false` via `rememberSaveable`) to keep the dialog uncluttered and focused on primary settings.
• All new titles, descriptions, and detection hints SHALL maintain 100% localization parity across all 9 supported application locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-365`). Complements `REQ-UI-015` (*Unified Metric component system*), `REQ-UI-016` (*Centralized metric formatting context*), and `REQ-UI-364` (*Centralized metric distance and altitude formatting enforcement*). Targets `MyHelper.java`, `TelemetryMetricUtils.kt`, `EditSportTypeDialog.kt`, and `SpeedFormatter.java`.
2. *Historical Origin & Commit Trace*: `TelemetryMetricUtils.kt` was authored during Sprint 2026-40.16 (`ATT-2031`) for interactive elevation and telemetry profile scrubbing. `EditSportTypeDialog.kt` was written during Sprint 2026-41.4 (`ATT-2751`), where speed fields and export mappings were added linearly. Refined in Sprint 2026-42.3 (`ATT-3363`) and Sprint 2026-45.12 (`ATT-5821`).
3. *Root Reason for Existing Formulation*: Expedient local development led to magic constants and flat dialog forms without progressive disclosure.
4. *Preservation of Core Invariants*: Exact conversion precision, Imperial and Metric parity, decimal input robustness, and database update persistence strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* speed telemetry points displayed on a workout map scrub graph,
• *When* converting speed values between m/s and user display units in Metric or Imperial modes,
• *Then* conversions execute via `MyHelper.mps2userUnit` and `MyHelper.UserUnit2mps` with zero magic constants in `TelemetryMetricUtils.kt`.
• *Given* the sport type editor dialog (`EditSportTypeDialog`) opened in any language locale,
• *When* viewing the primary dialog surface,
• *Then* only Sport Name, Base Sport Type, Linked Equipment, and Strava Sport Name are visible, while speed fields and TCX/GoldenCheetah mappings are collapsed inside "Erweiterte Optionen".
• *Given* the Advanced Options accordion in `EditSportTypeDialog`,
• *When* expanded,
• *Then* an explanatory text explains that speed bounds are used for automatic sport type detection (`sport_type_speed_detection_hint`), followed by the Min/Max speed row and TCX/GoldenCheetah export dropdowns.
• *Given* user input into minimum and maximum speed thresholds,
• *When* typing with comma or dot decimal separators,
• *Then* speed values parse without error and persist correctly on save.

**Invariants**: Mathematical precision, Single Source of Truth (Rule 36), locale robustness, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-366: Harmonized Live Segment and Live Climb Tracking Bottom Sheet Architecture & Container (Rule 36) [Verified]
- **Tests**: `TST-UI-329`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `LiveSegmentSheet.kt`, `LiveClimbSheet.kt`, `SensorGridScreen.kt`
- **Rationale**: The system SHALL harmonize live tracking bottom sheet scaffolding and mounting across Strava Live Segments and Live Climbs (ClimbPro), eliminating duplicate container wrappers in `SensorGridScreen.kt`, normalizing file naming to `LiveSegmentSheet.kt`, and establishing a unified sheet container architecture, enforcing Rule 36 (Single Source of Truth & DRY) and guaranteeing visual and behavioral consistency (ATT-3364, complementing `REQ-MAP-027`, `REQ-UI-196`, `REQ-UI-270`, `REQ-UI-331`, and `REQ-UI-340`):
1. *Filename Normalization & Import Parity (`LiveSegmentSheet.kt`)*:
• The system SHALL rename `LIveSegmentSheet.kt` to `LiveSegmentSheet.kt` with standard PascalCase.
• All imports, reflection tests, and documentation SHALL resolve to `LiveSegmentSheet` and `LiveSegmentSheetKt`.
2. *Unified Sheet Container in Cockpit (`SensorGridScreen.kt`)*:
• The system SHALL extract a reusable container `LiveTrackingSheetContainer` encapsulating `.fillMaxWidth().wrapContentHeight().sheetContour().background(MaterialTheme.colorScheme.surface, shape = BottomSheetDesign.SheetShape)` and partially-expanded click-to-expand handling.
• In `SensorGridScreen.kt`, duplicate `Box` blocks for `activeSegment` and `activeLiveClimb` SHALL be consolidated into a single unified sheet slot hosting `LiveTrackingSheetContainer`.
3. *Visual & Structural Parity*:
• Both `LiveSegmentSheet` and `LiveClimbSheet` SHALL render with identical container surface colors (`MaterialTheme.colorScheme.surface`), shapes (`BottomSheetDesign.SheetShape`), contour borders, and canonical 0.5dp horizontal dividers (`color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)`).
• Segment visual precedence over climbs (`showLiveSegments && activeSegment != null` over `activeLiveClimb`) SHALL strictly be preserved per `REQ-MAP-027` Section 4.
4. *Preservation of System Invariants*:
• Clamped wrap-content height (`REQ-UI-270`), dynamic peek height (`BottomSheetDesign.PeekHeightLiveSegment`), live rider progress pins on the elevation profile (`REQ-UI-331`), type-safe finish evaluation (`REQ-UI-340`), and 100% full-suite unit test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-366`). Harmonizes and consolidates scaffolding of `REQ-MAP-027` (*Persistent Climbs Database & Live ClimbPro Cockpit Sheet*), `REQ-UI-196` (*Live Segment Bottom Sheet Design & Surface Background*), `REQ-UI-270` (*Clamped Bottom Sheet Height to Elevation Profile Content Height*), and `REQ-UI-340` (*LiveSegmentSheet Type-Safe Numeric Metric Evaluation on Segment Finish*).
2. *Historical Origin & Commit Trace*: `LIveSegmentSheet.kt` was authored in Sprint 2026-40.1 (`ATT-1644` / `ATT-1735`), refined in Sprint 2026-40.16 (`ATT-2231`, `ATT-2967`), and Sprint 2026-41.8 (`ATT-3108`). `LiveClimbSheet.kt` was introduced in Sprint 2026-40.14 (`ATT-1281`, commit `ceaf4070`).
3. *Root Reason for Existing Formulation*: `LiveClimbSheet` was developed independently of `LiveSegmentSheet` to quickly implement ClimbPro without coupling to `MapDetailLayout`. Mounting logic in `SensorGridScreen.kt` was copy-pasted to support the new entity.
4. *Preservation of Core Invariants*: Visual precedence of segments over climbs, elevation profile pin synchronization, peek heights, contour clipping, and 100% test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the repository file tree in `ui/segments`,
• *When* inspecting the live segment sheet file,
• *Then* the file is named `LiveSegmentSheet.kt` with standard PascalCase.
• *Given* `SensorGridScreen.kt` rendering bottom sheet content,
• *When* evaluating the active sheet slot for segments or climbs,
• *Then* both entities mount via a single shared `LiveTrackingSheetContainer` composable, eliminating duplicate `Box` modifier chains and expansion lambdas.
• *Given* active segment and active climb bottom sheets in the cockpit,
• *When* comparing sheet appearance,
• *Then* both sheets utilize identical container backgrounds, shapes, contour borders, and 0.5dp dividers.
• *Given* all layout and contract tests in `LiveSegmentSheetContractTest.kt`, `LiveSegmentSheetLayoutTest.kt`, and `LiveClimbSheetLayoutTest.kt`,
• *When* executed on pure JVM,
• *Then* 100% of tests pass, verifying both the renamed file and the unified container.

**Invariants**: Segment precedence over climbs, elevation profile pin alignment, wrap-content height, and 100% full-suite test pass rate MUST NOT be broken.

### REQ-UI-367: First-Time Stop Tracking Swipe and Tab Coachmark Banner on Cockpit Screen with Persistent Acknowledgement and Factory Reset Integration [Verified]
- **Tests**: `TST-UI-330`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
• `TrackingTabsScreen` SHALL gate invoking `trackingTabsViewModel.markCoachmarkRenderedInSession()` on `showStopTrackingCoachmark && pagerState.settledPage > 0 && !pagerState.isScrollInProgress`, ensuring `hasRenderedCoachmarkInSession` is never armed during scroll animations away from `Page 0`.
• `TrackingTabsViewModel.shouldAutoAcknowledgeStopTrackingCoachmark(...)` SHALL accept `(trackingMode, currentPage, settledPage, isScrollInProgress, hasSeenHint, hasRenderedCoachmarkInSession, isDismissPending)` and return `true` iff `hasSeenHint == false && !isDismissPending && hasRenderedCoachmarkInSession && (trackingMode == TrackingMode.TRACKING
- **Rationale**: The system SHALL display a non-intrusive, high-contrast Material 3 onboarding coachmark banner on the cockpit tracking screen during the athlete's global first active workout session (`TrackingMode.TRACKING` or `TrackingMode.PAUSED`) to guide discovering the `Control` tab (`Tab 0`) via horizontal swipe or tab bar tap, and SHALL permanently dismiss the hint upon acknowledgment or navigation to `Tab 0` after the banner has been rendered on a settled cockpit page while resetting it on factory defaults restoration (ATT-3387, hardened in ATT-4456, complementing `REQ-UI-027`, `REQ-UI-216`, `REQ-UI-325`, and `REQ-SET-073`):
1. *Decoupled DataStore Persistence & Factory Reset (`TuningPreferencesDataStore.kt`)*:
• `TuningPreferencesDefaults` SHALL define `const val DEFAULT_HAS_SEEN_STOP_TRACKING_HINT = false`.
• `TuningPreferencesDataStore` SHALL define `KEY_HAS_SEEN_STOP_TRACKING_HINT = booleanPreferencesKey("tuning_has_seen_stop_tracking_hint")` included in `ALL_KEYS`, `val hasSeenStopTrackingHintFlow: Flow<Boolean>`, and `suspend fun setHasSeenStopTrackingHint(seen: Boolean)`.
• Invoking `tuningDataStore.resetToDefaults()` in Advanced Settings SHALL automatically remove `KEY_HAS_SEEN_STOP_TRACKING_HINT` and restore the hint to `false`.
2. *ViewModel Session State & Configuration-Change Survival (`TrackingTabsViewModel.kt`)*:
• `TrackingTabsViewModel` SHALL maintain `val hasRenderedCoachmarkInSession: StateFlow<Boolean>` (`markCoachmarkRenderedInSession()`, `resetCoachmarkSessionState()`), surviving configuration changes and resetting to `false` when `trackingMode` transitions to `TrackingMode.READY`.
3. *Settled Gating & Race-Free Auto-Acknowledgement (`TrackingTabsScreen.kt`, `TrackingTabsViewModel.kt`)*:
• `TrackingTabsScreen` SHALL collect `hasSeenStopTrackingHintFlow` (`initial = true` to prevent flash prior to DataStore load) and `hasRenderedCoachmarkInSession`.
• `TrackingTabsScreen` SHALL evaluate `shouldShowStopTrackingCoachmark(trackingMode, currentPage, screenMode, hasSeenHint, isDismissPending)`, returning `true` iff `hasSeenHint == false && !isDismissPending && (trackingMode == TrackingMode.TRACKING

### REQ-UI-368: Interactive Start/End Workout Cropping with Destructive Sample Pruning and Full Metric Recalculation [Verified]
- **Tests**: `TST-UI-331`, `TST-UI-392`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
• Straight-line displacement `LINE_DISTANCE_m` SHALL be recalculated row-by-row from `newStartLatLng` via Haversine distance (or anchored to `0.0` for GPS-less indoor activities).
• Verify post-op checksum: `remainingRows == expectedRetainedRowCount && deletedRows == expectedDeletedRowCount && remainingRows >= 2`.
5. *Deterministic Lap Metadata Mapping (`LapsDatabaseManager`)*:
• Laps outside the retained window are pruned. Each retained lap $k \in \{1 \dots K\}$ strictly preserves the exact user-authored `NAME` and `DESCRIPTION` from its original parent lap without truncation or heuristic merging.
• Boundary laps $1$ and $K$ have their start time, total time, total distance, and average speed recalculated strictly from their retained samples.
• `LAP_NR` is re-indexed sequentially `1..K` in both `Laps.TABLE` and `WorkoutSamples` in lockstep.
6. *Sanitized Non-Linear Extrema & Sparse Telemetry Handling*:
• Telemetry aggregation (`CropWorkoutMath`) SHALL filter invalid or non-finite values (`Double.isFinite(v)`).
• Non-linear sensor extrema (`HR`, `POWER`, `CADENCE`, `SPEED_mps`, `ALTITUDE`, `TEMPERATURE`) in `ExtremumValues` SHALL be strictly re-derived (`MIN`, `MAX`, `AVG`, `START`, `END`) from retained samples.
• If a sensor stream contains zero valid readings in the cropped window, its extrema fields SHALL be set to `0.0`, signaling `hasSensorData = false` to UI consumers.
7. *Synchronous Export Invalidation, Cloud Sync & Failure Recovery*:
• Active export tasks for `fileBaseName` SHALL be cancelled and cached `.tcx`, `.fit`, `.gpx`, `.csv`, `.json` files deleted synchronously pre-commit.
• Cloud sync status in `SyncStatusDatabaseManager` / `ExportStatusDatabaseManager` SHALL be marked `PENDING_UPDATE` / `WAITING`.
• Fresh export jobs SHALL be enqueued post-commit via `WorkManager.enqueueUniqueWork("export_" + fileBaseName, ExistingWorkPolicy.REPLACE, exportRequest)`.
• If `WorkManager` enqueue fails, `ExportStatus` SHALL be set to `ERROR`, and a "Force Re-Export" button SHALL be displayed in the UI.
• `WorkoutRepository` SHALL invalidate its in-memory cache and re-fetch `WorkoutData` directly from SQLite post-commit under `cropMutex`.
8. *9-Language Localization Parity*:
• All UI strings, button labels, and dialog messages SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-368`). Targets `CropWorkoutScreen.kt`, `CropWorkoutMath.kt`, `EditWorkoutScreen.kt`, `EditWorkoutViewModel.kt`, `WorkoutRepository.kt`, `WorkoutSamplesDatabaseManager.java`, `LapsDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`, `ExportManager.java`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-3274`, Sprint `2026-42.4`, Epic `ATT-3267`.
3. *Root Reason for Existing Formulation*: Athletes occasionally record unwanted leading GPS clusters or trailing car rides; currently no in-app trimming tool exists, causing corrupted summary metrics and exports.
4. *Preservation of Core Invariants*: Rollback atomicity, irreversible destructive confirmation dialog, zero-anchoring with `EPSILON = 1e-6`, lap metadata preservation, synchronous export invalidation, and 9-language translation parity MUST NOT be broken.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a finished workout with $\ge 2$ samples opened in `CropWorkoutScreen`,
• *When* the athlete adjusts the start or end handles on the `RangeSlider`,
• *Then* the map polyline highlights the retained range while dimming trimmed segments, the elevation profile updates, and live preview values for trimmed offsets, new distance, and new duration update continuously.
• *Given* a selection where duration $< 1.0\text{ s}$ or fewer than 2 points,
• *When* rendered in `CropWorkoutScreen`,
• *Then* the "Crop" action button is visually disabled and cannot be tapped.
• *Given* a valid crop range with "Crop" tapped,
• *When* the confirmation dialog appears,
• *Then* it warns that sample deletion is permanent and irreversible.
• *Given* confirmation granted,
• *When* the crop transaction executes,
• *Then* leading/trailing samples are pruned, cumulative metrics are zero-anchored at `startRowId` with `EPSILON = 1e-6` clamping, `LINE_DISTANCE_m` is recalculated row-by-row, laps are re-indexed with user notes preserved, summary extrema are strictly re-derived, and cached export files are replaced.
• *Given* an ongoing crop transaction,
• *When* the user presses the system back button,
• *Then* navigation is blocked by `BackHandler(enabled = isCroppingInProgress)` until the transaction completes.

**Invariants**: SQLite rollback journal safety, post-op checksum assert, `isCroppingInProgress` UI lock, universal `EPSILON = 1e-6` clamping, and 100% clean-room test pass rate MUST NOT be broken.
- **Implementation**: v'
- **Rationale**: The system SHALL provide an interactive workout cropping subview (`CropWorkoutScreen.kt`) with synchronized map polyline and elevation profile range scrubbing, allowing athletes to permanently trim unwanted leading and trailing samples from a recorded workout, and SHALL atomically prune SQLite samples, re-index laps while preserving user-authored notes, re-derive all summary aggregates and sensor extrema with universal floating-point clamping, and synchronously invalidate and replace export artifacts (ATT-3274, complementing `REQ-UI-015`, `REQ-UI-016`, `REQ-TRK-001`, and `REQ-TRK-002`):
1. *Interactive Dual-Range Scrubber & Synchronized Previews (`CropWorkoutScreen.kt`)*:
• Accessible via an action button ("Track zuschneiden" / "Crop workout") in `EditWorkoutScreen.kt` (`EditWorkoutDialog`) for completed activities with $\ge 2$ samples.
• Dual-handle `RangeSlider` with minimum handle separation (`endIndex - startIndex >= minIndexStep`, corresponding to $\ge 1\text{ s}$).
• Track Map preview (`PathPreviewMap.kt`): The retained segment (`[startIndex..endIndex]`) SHALL render as a solid primary polyline; trimmed leading and trailing segments SHALL render as dimmed, dashed polylines (`Dash(15f), Gap(15f)`).
• Elevation Profile preview (`ElevationProfile.kt`): Renders with adaptive canvas height (`calculateElevationProfileHeight(range)`), highlighting the retained interval.
• Live Metric Previews: Dynamic live preview of trimmed start offset, trimmed end offset, new total distance (`DistanceFormatter`), and new total duration (`DateUtils.formatElapsedTime`).
• RTL & Font Scaling: All layout arrangements use RTL-safe start/end primitives and support up to `2.0x` font scaling.
2. *Hard Boundary Pre-Condition Guard & Destructive Confirmation (`Coding Standards Sec 1.4`)*:
• The "Crop" action button SHALL be disabled unless `(endIndex - startIndex >= minIndexStep) && (croppedDurationSec >= 1.0) && (endTimestampMs - startTimestampMs >= 1000L) && !isCroppingInProgress`.
• Tapping "Crop" SHALL display an explicit `AlertDialog` warning that samples outside the selected window will be permanently deleted, the operation is irreversible, and reminding athletes they can use *Save as Route* beforehand to preserve the untrimmed path.
3. *Pre-Flight Storage Verification & Ephemeral Recovery Backup*:
• `WorkoutRepository` SHALL verify internal storage availability via `StatFs` ($\ge 10\text{ MB}$).
• `WorkoutRepository` SHALL write an ephemeral versioned JSON snapshot to `context.cacheDir/workout_crop_backup_<workoutId>_<timestamp>.json` inside a `try ... finally` block, purging it upon successful transaction completion.
4. *Native SQLite Rollback Transactions & Universal Cumulative Clamping*:
• Prune samples outside `[startRowId, endRowId]` in `WorkoutSamplesDatabaseManager`.
• Cumulative monotonic columns (`DISTANCE_m`, `TIME_TOTAL`, `TIME_ACTIVE`, `ASCENT`, `DESCENT`, `CALORIES`) SHALL be shifted by subtracting baseline values at `startRowId`. The new start row (`_id == startRowId`) is zero-anchored (`0.0` / `0`), and for subsequent rows, shifted values with $

### REQ-UI-369: [REVERTED - Architectural Fence] Prohibition of Static BottomSheetScaffold Bottom Inset Padding on Cockpit ElevationProfile Surface [Verified]
- **Tests**: `TST-UI-332`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorGridScreen.kt`
- **Rationale**: The system SHALL NOT apply `bottom = paddingValues.calculateBottomPadding()` from Material 3 `BottomSheetScaffold` to the `ElevationProfile` `Surface` in `SensorGridScreen.kt` (ATT-3311 Post-Review Revert of `f3edad51`, preserving `REQ-UI-346`, `REQ-UI-355`, and `REQ-UI-358`):
1. *Root Cause of Post-Review Revert (Documented Deviation)*:
• In `SensorGridScreen.kt`, `sheetPeekHeight` evaluates to `BottomSheetDesign.PeekHeightLiveSegment + navBarHeight` ($\approx 174\text{dp}$) whenever `shouldShowBottomSheet == true`, and `rememberStandardBottomSheetState` is configured with `skipHiddenState = false`.
• Material 3 `BottomSheetScaffold` passes `PaddingValues(bottom = sheetPeekHeight)` statically to its body content **even when the user swipes the Live Segment / Live Climb sheet down into `SheetValue.Hidden`**. Applying `bottom = paddingValues.calculateBottomPadding()` to the `ElevationProfile` `Surface` therefore creates a permanent $\approx 174\text{dp}$ blank whitespace below the `ElevationProfile` whenever a segment/climb sheet is hidden.
• Furthermore, the original screenshot symptom in `ATT-3311` (`screencap_elevation_missing.png` from Sprint `2026-41.10`, recorded at `0:00:00 s` / `0,00 km` on a navigated route) had already been resolved by `ATT-3199` (`REQ-UI-355`, route elevation profile fallback when recorded points $< 2$) and `ATT-3343` (`REQ-UI-358`, asymmetric map `weight(1f, fill = true)`).
2. *Enforced Layout Invariant*:
• The `ElevationProfile` `Surface` in `SensorGridScreen.kt` SHALL declare `.fillMaxWidth().padding(top = 8.dp)` with zero static bottom scaffold inset padding.

### REQ-UI-370: Decoupled Training Zone Architecture & 7-Zone Coggan Cycling Power Model [Verified]
- **Tests**: `TST-UI-333`, `TST-UI-391`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SettingsDataStore.kt`, `ZonesSettingsActivity.kt`, `TelemetryZoneMath.kt`, `ZoneDistributionCalculator.kt`, `TrackingViewModel.kt`, `Color.kt`
- **Rationale**: The system SHALL decouple the training zone architecture by retaining the canonical 5-zone model for Heart Rate (`HR_RUN`, `HR_BIKE`) while expanding Cycling Power (`PWR_BIKE`) to Dr. Andy Coggan's 7-zone framework across settings, real-time HUD cockpit tiles, and post-workout aftermath analytics (ATT-3184, amending `REQ-UI-203` and complementing `REQ-UI-202` and `REQ-UI-231`):
1. *Color Token System & Theme Parity (`Color.kt`, `color.xml`)*:
• `TTColor.Zone6 = Color(0xFF4E342E)` (Dark Chestnut Brown) and `TTColor.Zone7 = Color(0xFF000000)` (Black).
• In `res/values/color.xml`: `<color name="zone_6">#4E342E</color>`, `<color name="zone_7">#000000</color>`.
• In dark / AMOLED theme, Zone 7 retains Black fill with high-contrast border outline.
2. *DataStore Storage & Dynamic Proportional Migration (`SettingsDataStore.kt`)*:
• `ZoneType` exposes `zoneCount` (5 for HR, 7 for Power) and `boundaryCount` (4 for HR, 6 for Power).
• `Zone` enum defines `ZONE_1(1)`, `ZONE_2(2)`, `ZONE_3(3)`, `ZONE_4(4)`, `ZONE_5(5)`, `ZONE_6(6)`.
• Add preference keys `pwr_bike_zone5_max` and `pwr_bike_zone6_max`.
• For existing user profiles with missing Z5/Z6 keys, the system SHALL calculate proportional defaults based on existing `PWR_BIKE_ZONE4_MAX`: $\text{Estimated FTP} = \max(50, \text{round}(\text{z4Max} / 1.05))$, $\text{Default z5Max} = \max(\text{z4Max} + 10, \text{round}(\text{Estimated FTP} \times 1.20))$, $\text{Default z6Max} = \max(\text{z5Max} + 10, \text{round}(\text{Estimated FTP} \times 1.50))$. Default fallback if z4Max missing: Z1=132, Z2=180, Z3=216, Z4=252, Z5=288, Z6=360 (updated to calculator-aligned baseline in ATT-4521 / REQ-UI-426).
• Migration check and preference initialization SHALL execute inside a single atomic `dataStore.edit` transaction.
• Strict monotonic ascending validation: $0 < z1 < z2 < z3 < z4 < z5 < z6$.
3. *Synchronous Java Helper (`SettingsDataStoreJavaHelper.kt`)*:
• `getZoneMax(context, zoneType, zoneIndex)` SHALL support `zoneIndex == 5` and `zoneIndex == 6`.
4. *Domain Models & Telemetry Mathematics (`ZoneDistributionModels.kt`, `TelemetryZoneMath.kt`, `BatterySaverModel.kt`)*:
• `PowerZoneThresholds(z1Max, z2Max, z3Max, z4Max, z5Max, z6Max)`.
• `TelemetryZoneMath.calculatePowerZoneBands()` SHALL compute 7 bands with colors `Zone1..Zone7` and labels `Z1..Z7`.
• `TelemetryZoneMath.determinePowerZone()` SHALL map instantaneous power to 1-indexed zones `1..7`.
• `TelemetryZoneMath.loadPowerThresholds()` SHALL load all 6 boundaries and validate $0 < z1 < z2 < z3 < z4 < z5 < z6$.
• `BatterySaverModel.calculateZoneIndex()` SHALL map power values to zones `1..7`.
5. *Real-Time Tracking Cockpit HUD (`TrackingViewModel.kt`)*:
• `zoneColors` list expanded to 7 colors (`Zone1..Zone7`).
• `calculateZoneColor()` maps power values across all 7 zones for background, left/right bar, and text color modes.
6. *Settings UI (`ZonesSettingsActivity.kt`)*:
• Dynamically renders 5 boundary cards for HR and 7 boundary cards for Power.
• Linked min/max inputs preserve $z_n \ge z_{n-1} + 1$.
• Live preview renders 7 selector chips (`Z1..Z7`) for Power and 5 chips (`Z1..Z5`) for HR.
7. *Post-Workout Aftermath Analytics & Histograms (`ZoneDistributionCalculator.kt`, `TelemetryHistogramCalculator.kt`)*:
• `calculatePowerDistribution()` computes duration and percentages across 7 zones (`Zone1..Zone7`).
• `calculatePowerHistogram()` maps frequency bins across 7 zones with matching colors.
• `ZoneDistributionColumnChart.kt` dynamically adapts to 7 equal-width columns.
• Computed dynamically on-the-fly from raw samples in `WorkoutSamples` (zero SQLite schema mutations required).
8. *9-Language Localization Parity*:
• Define `zone_6_label` and `zone_7_label` across all 9 application locales with programmatic `Z$zoneIndex` fallback.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-203` (*Aftermath: Cycling Power 5-Zone Distribution (Time-in-Zones) Vertical Column Chart Architecture*, Sprint 2026-40.5, ATT-1390). Targets `SettingsDataStore.kt`, `SettingsDataStoreJavaHelper.kt`, `ZonesSettingsActivity.kt`, `ZoneDistributionModels.kt`, `TelemetryZoneMath.kt`, `TrackingViewModel.kt`, `ZoneDistributionCalculator.kt`, `TelemetryHistogramCalculator.kt`, `Color.kt`, and `res/values*/strings.xml`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.5 (`ATT-1390`, commit `e3966ebf`) introduced cycling power 5-zone distribution in Aftermath, designed to match `REQ-UI-202` (Heart Rate 5-Zone Distribution).
3. *Root Reason for Existing Formulation*: Power was originally configured with 5 zones solely to match Heart Rate (`REQ-UI-202`) and minimize architectural divergence across domain models and settings UI.
4. *Preservation of Core Invariants*: Heart Rate (`HR_RUN`, `HR_BIKE`) 5-zone scheme remains 100% intact; dynamic proportional migration from stored Z4 Max prevents arbitrary defaults; zero schema mutations in `WorkoutSummaries` / `WorkoutSamples`; 100% raw export format compatibility; 9-language localization parity strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opens `ZonesSettingsActivity`,
• *When* selecting Heart Rate (Run or Bike),
• *Then* exactly 5 zone cards and 5 preview chips (`Z1..Z5`) are rendered.
• *When* selecting Cycling Power,
• *Then* exactly 7 zone cards and 7 preview chips (`Z1..Z7`) are rendered with linked min/max editing.
• *Given* an existing user database where `PWR_BIKE_ZONE4_MAX = 210` (corresponding to a 200W FTP athlete) and Z5/Z6 are unconfigured,
• *When* `SettingsDataStore` loads power thresholds,
• *Then* `PWR_BIKE_ZONE5_MAX` resolves to $240\text{W}$ ($120\%$ FTP) and `PWR_BIKE_ZONE6_MAX` resolves to $300\text{W}$ ($150\%$ FTP) within an atomic transaction.
• *Given* an active workout with a power meter connected,
• *When* the athlete produces power in Zone 6 ($Z5_{\max} < P \le Z6_{\max}$),
• *Then* the cockpit power tile resolves `TTColor.Zone6` (Dark Chestnut Brown).
• *When* the athlete produces power in Zone 7 ($P > Z6_{\max}$),
• *Then* the cockpit power tile resolves `TTColor.Zone7` (Black).
• *Given* a completed workout containing power meter telemetry,
• *When* viewing the workout summary in Aftermath,
• *Then* the Power Zone Distribution card renders 7 vertical columns with accurate time-in-zone durations and percentages.
• *Given* all existing unit tests in `HeartRateZoneLocalizationTest`, `PowerZoneDistributionCalculatorTest`, `TelemetryZoneMathTest`, and `SensorFieldZoneDisplayTest`,
• *When* running `./gradlew testDebugUnitTest`,
• *Then* 100% of tests pass without regressions.

**Invariants**: Heart Rate 5-zone scheme, SQLite single-thread confinement, atomic DataStore transactions, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-371: Elevation Profile 7-Zone Gradient Shading and Harmonized Legend [Verified]
- **Tests**: `TST-UI-334`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ElevationProfile.kt`
- **Rationale**: The system SHALL harmonize elevation profile slope gradient shading and the interactive `GradeLegend` in `ElevationProfile.kt` with the application's unified 7-zone design system (`TTColor.Zone1..Zone7`) established in `REQ-UI-370` (ATT-3391, complementing `REQ-UI-352` and `REQ-UI-370`):
1. *7-Zone Slope Gradient Scale & Exact Half-Open Intervals (`REQ-UI-371.1`)*:
• Terrain incline SHALL be categorized into 7 discrete slope gradient intervals following strict half-open interval logic $[x_{n-1}, x_n)$ mapped deterministically to `TTColor`:
- Zone 1 (`TTColor.Zone1` Chartreuse `#7FFF00`): $\text{Grade} \in [-\infty, 2.0\%)$ (Flat, rolling terrain, descents, and non-finite values).
- Zone 2 (`TTColor.Zone2` Green `#00E676`): $2.0\% \le \text{Grade} < 5.0\%$ (Gentle incline).
- Zone 3 (`TTColor.Zone3` Orange `#FFA500`): $5.0\% \le \text{Grade} < 10.0\%$ (Moderate climb).
- Zone 4 (`TTColor.Zone4` Red `#FF0000`): $10.0\% \le \text{Grade} < 15.0\%$ (Steep climb).
- Zone 5 (`TTColor.Zone5` Dark Violet `#8A2BE2`): $15.0\% \le \text{Grade} < 20.0\%$ (Very steep climb).
- Zone 6 (`TTColor.Zone6` Dark Chestnut Brown `#4E342E`): $20.0\% \le \text{Grade} < 25.0\%$ (Extreme ramp; $20.0\%$ inclusive, $25.0\%$ exclusive).
- Zone 7 (`TTColor.Zone7` Black `#000000`): $\text{Grade} \ge 25.0\%$ (Monster grade / Wall; $25.0\%$ inclusive).
2. *Deterministic Top-Level Utility Function & Non-Finite Safeguards (`REQ-UI-371.2`)*:
• `ElevationProfile.kt` SHALL expose a public top-level utility function `getElevationGradeColor(gradePercent: Double): Color` executing monotonic half-open interval evaluation $[x_{n-1}, x_n)$ for segment color resolution when `climbGradientShading == false` and no custom `segmentColorProvider` is supplied.
• If `gradePercent.isNaN()` or `!gradePercent.isFinite()`, the function SHALL safely return `TTColor.Zone1`, strictly preventing UI crashes or undefined rendering on GPS telemetry drops.
3. *Explicit Descent & Negative Slope Handling (`REQ-UI-371.3`)*:
• All downhill slopes and descents ($\text{Grade} < 0.0\%$) and flat terrain ($0.0\% \le \text{Grade} < 2.0\%$) SHALL resolve to `TTColor.Zone1`. Negative gradients represent gravitational assistance with zero upward climbing resistance and are intentionally assigned the baseline recovery zone color.
4. *GradeLegend Harmonization (`REQ-UI-371.4`)*:
• The interactive `GradeLegend` popup in `ElevationProfile.kt` SHALL display exactly 7 legend items in sequence: `< 2%` (`TTColor.Zone1`), `2 - 5%` (`TTColor.Zone2`), `5 - 10%` (`TTColor.Zone3`), `10 - 15%` (`TTColor.Zone4`), `15 - 20%` (`TTColor.Zone5`), `20 - 25%` (`TTColor.Zone6`), `> 25%` (`TTColor.Zone7`). Legend width constraint SHALL be configured to `widthIn(max = 300.dp)` to prevent text truncation on wrapped multi-row layouts.
5. *Accessibility & Contrast Compliance (`REQ-UI-371.5`)*:
• Swatch labels in `GradeLegendItem` SHALL be rendered adjacent to swatches using `MaterialTheme.colorScheme.onSurface` on `MaterialTheme.colorScheme.surface`, providing a contrast ratio $> 10:1$ in both light and dark themes (complying with WCAG 2.1 AA $\ge 4.5:1$).
• For `TTColor.Zone6` (`#4E342E`) and `TTColor.Zone7` (`#000000`), `GradeLegendItem` SHALL render a border outline `BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)` around the swatch to maintain visible boundaries against dark surfaces.
6. *Historical Data Strategy & System Invariants (`REQ-UI-371.6`)*:
• Elevation profiles are rendered on-the-fly from raw GPS sample coordinates in `WorkoutSamples`. Re-rendering historical workouts with the unified 7-zone scale is an intentional visual enhancement providing immediate clarity without database migrations.
• `getClimbGradeColor` in `LiveClimbSheet.kt` (`REQ-UI-352`) and its specific 6-band cycling climb classification ($<3\%, 3-6\%, 6-9\%, 9-12\%, 12-20\%, \ge 20\%$) SHALL remain unaltered, preserving full backward compatibility with `ElevationProfileClimbShadingTest.kt`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-352` (*Real-Time Live Climb Sheet & Slope Shading*, Sprint 2026-42.1, ATT-3343) and complements `REQ-UI-370` (*7-Zone Coggan Cycling Power Model & Palette*, Sprint 2026-42.4, ATT-3184). Targets `ElevationProfile.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.1 (`ATT-3343`, commit `fca58372`) introduced climb slope shading with 6 bands, while default elevation profile slope shading in `ElevationProfile.kt` was restricted to 6 bands due to legacy 5-zone palette limitations.
3. *Root Reason for Existing Formulation*: Prior to ATT-3184, only 5 training zone colors and generic `Color.Black` existed in the design system, forcing all slopes $\ge 20\%$ to be lumped into Black without distinction for extreme ramps.
4. *Preservation of Core Invariants*: Live climb gradient shading (`REQ-UI-352`), zero bitmap caching, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an elevation segment with grade $22.4\%$,
• *When* evaluating `getElevationGradeColor`,
• *Then* `TTColor.Zone6` (Dark Chestnut Brown `#4E342E`) is resolved.
• *Given* an elevation segment with grade $26.8\%$,
• *When* evaluating `getElevationGradeColor`,
• *Then* `TTColor.Zone7` (Black `#000000`) is resolved.
• *Given* a descent segment with grade $-8.5\%$,
• *When* evaluating `getElevationGradeColor`,
• *Then* `TTColor.Zone1` (Chartreuse `#7FFF00`) is resolved.
• *Given* exact boundary values `2.0%`, `5.0%`, `10.0%`, `15.0%`, `20.0%`, and `25.0%`,
• *When* evaluating `getElevationGradeColor`,
• *Then* each boundary maps deterministically to the higher interval ($[x_{n-1}, x_n)$).
• *Given* input values `Double.NaN` or `Double.POSITIVE_INFINITY`,
• *When* evaluating `getElevationGradeColor`,
• *Then* `TTColor.Zone1` is safely returned.
• *Given* the `GradeLegend` popup opened on a dark theme,
• *When* rendered in `ElevationProfile`,
• *Then* all 7 items are displayed with swatches and non-truncated labels, and Zone 6 / Zone 7 swatches have outline borders.

**Invariants**: Live climb shading (`REQ-UI-352`), zero disk/DB cache regressions, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-372: Horizontal Stacked Distribution Bar Component & 3-Way Zone Display Mode Switching in Workout Summary Cards [Verified]
- **Tests**: `TST-UI-335`, `TST-UI-382`, `TST-UI-383`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZoneDistributionStackedBar.kt`, `ZoneDistributionModels.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, `WorkoutMasksAndCardsSection.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`, `MyPreferenceManager.kt`
- **Rationale**: The system SHALL provide a continuous horizontal stacked distribution bar as an alternative display mode alongside the vertical column chart and fine-grained telemetry histogram in workout aftermath zone cards (`HeartRateZoneDistributionCard`, `PowerZoneDistributionCard`) (ATT-3390, amending `REQ-UI-202` and `REQ-UI-203`, and complementing `REQ-UI-231`, `REQ-UI-251`, and `REQ-UI-370`):
1. *Continuous Horizontal Stacked Distribution Bar (`REQ-UI-372.1`)*:
• `ZoneDistributionStackedBar.kt` SHALL expose a composable function `ZoneDistributionStackedBar(distribution: ZoneDistributionData, modifier: Modifier = Modifier, barHeight: Dp = 16.dp, showLegend: Boolean = true)` rendering active workout duration normalized to 100%.
• Active zone entries ($d_i > 0$ and $p_i > 0.0$) SHALL be laid out horizontally in contiguous order (Zone 1 to Zone 5 for Heart Rate; Zone 1 to Zone 7 for Power).
• Each active segment's width SHALL be weighted in Compose `Row` using `Modifier.weight(entry.percentage)` directly, guaranteeing that segments sum to exactly 100% of the bar's inner width without layout overflow or rounding clipping.
• Outer bar container SHALL be clipped with `RoundedCornerShape(6.dp)`, enclosed with a uniform contour border `BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))`, and filled with background `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)`.
• Active segments SHALL be separated by uniform vertical dividers (`1.dp` width, `color = MaterialTheme.colorScheme.surface`).
• If total active time is 0 or all zone entries have 0 duration, the component SHALL render an empty neutral container track without throwing layout or weighting exceptions.
• Zero-duration zones ($d_i = 0$) SHALL be gracefully omitted from the horizontal bar.
2. *Contrast, Border & Theme Uniformity (`REQ-UI-372.2`)*:
• To eliminate visual jitter and ensure consistent aesthetic rhythm, a uniform border outline `BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))` SHALL be applied around the entire outer stacked bar container and around all circular legend badge dots (`7.dp`).
• This guarantees compliant contrast ($\ge 3:1$ Non-Text Contrast per WCAG 2.1 AA 1.4.11) across all theme environments: dark swatches (`TTColor.Zone6`, `TTColor.Zone7`) remain distinctly bounded against dark surfaces, and light swatches (`TTColor.Zone1`) remain distinctly bounded against light surfaces.
3. *Compact Integrated Legend & Responsive Wrapping (`REQ-UI-372.3`)*:
• Below the horizontal bar, a responsive legend SHALL render all configured zones of the metric model (Z1..Z5 for Heart Rate, Z1..Z7 for Cycling Power) to provide full metabolic context.
• Legend items SHALL be arranged in a responsive wrapping layout (`FlowRow` or adaptive wrapping row with `horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)`), ensuring that items wrap gracefully across multiple lines on compact or narrow mobile displays without horizontal clipping or text truncation.
• Each legend item SHALL render: circular color badge (`7.dp`) with matching zone fill and uniform outline border, zone badge text (`Z1`..`Z5` or `Z1`..`Z7`) in `labelSmall` bold, localized zone name, formatted duration (`ZoneDistributionChartMath.formatZoneDuration(entry.durationSec)`), and percentage readout (`String.format(Locale.getDefault(), "%.0f%%", entry.percentage)`).
• Inactive zones ($d_i = 0$) SHALL be visually dimmed (`alpha = 0.45f`, displaying `0:00 (0%)`) to preserve complete metabolic context without implying active bar representation.
4. *Expanded Display Modes & Enum Parity (`REQ-UI-372.4`)*:
• `enum class ZoneCardDisplayMode` in `ZoneDistributionModels.kt` SHALL define: `STACKED_BAR`, `COLUMNS`, `HISTOGRAM`, and backward-compatible legacy alias `FIVE_ZONES` for `COLUMNS` to satisfy existing contract tests.
5. *Card Mode Switching & Segmented Buttons (`REQ-UI-372.5`)*:
• In `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`:
- When `distribution.histogram != null`: The segmented button row SHALL render 3 buttons: `STACKED_BAR` (R.string.zone_mode_stacked_bar), `COLUMNS` (R.string.zone_mode_columns), and `HISTOGRAM` (R.string.zone_mode_histogram).
- When `distribution.histogram == null`: The segmented button row SHALL render 2 buttons: `STACKED_BAR` and `COLUMNS`, enabling athletes to switch between the stacked bar and vertical columns even on activities without raw sample histograms.
- When `effectiveDisplayMode` is `STACKED_BAR`, the card body renders `ZoneDistributionStackedBar`.
- When `effectiveDisplayMode` is `COLUMNS` or `FIVE_ZONES`, the card body renders `ZoneDistributionColumnChart`.
- When `effectiveDisplayMode` is `HISTOGRAM`, the card body renders `TelemetryHistogramChart`.
6. *Preference Persistence (`REQ-UI-372.6`)*:
• `WorkoutCardSectionPreferences` in `MyPreferenceManager.kt` SHALL include `val zoneCardDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.COLUMNS`.
• Athlete's selected mode SHALL be persisted across application restarts via DataStore key `workout_card_zone_display_mode`.
7. *9-Language Localization Parity (`REQ-UI-372.7`)*:
• Add `zone_mode_stacked_bar` and `zone_mode_columns` across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
8. *Default Zone Card Display Mode Selector in Advanced Settings & Workout Summary Wiring (`ATT-3676`)*:
• The system SHALL expose a dedicated `SingleChoiceSegmentedButtonRow` selector for `ZoneCardDisplayMode` in `WorkoutMasksAndCardsSection.kt` under Advanced Settings, displaying options `STACKED_BAR` (`R.string.zone_mode_stacked_bar`), `COLUMNS` (`R.string.zone_mode_columns`), and `HISTOGRAM` (`R.string.zone_mode_histogram`).
• The selector SHALL be displayed conditionally when either `workoutCardPrefs.showZoneAnalysis == true` or `workoutDetailPrefs.showZoneAnalysis == true`.
• The selector SHALL display localized title `settings_zone_card_display_mode_title` across all 9 application locales.
• State Synchronization & Precedence: Tier 1 (Interactive Manual Override) takes precedence for an individual card during an active session; Tier 2 (Reactive Global Default) reflects `preferences.zoneCardDisplayMode` when no manual override exists; Tier 3 (Domain Fallback Guard) gracefully resolves to `COLUMNS` if `HISTOGRAM` is set on activities where `distribution.histogram == null`.
• Modularity limit of $\le 400$ lines in `WorkoutMasksAndCardsSection.kt` (REQ-UI-262), `FIVE_ZONES` legacy compatibility alias, and 100% clean-room test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-202` (*Aftermath: Heart Rate 5-Zone Distribution*, Sprint 2026-40.5, ATT-1390) and `REQ-UI-203` (*Aftermath: Cycling Power 5-Zone Distribution*, Sprint 2026-40.5, ATT-1390). Complements `REQ-UI-231`, `REQ-UI-251`, `REQ-UI-262`, and `REQ-UI-370`. Targets `ZoneDistributionStackedBar.kt`, `ZoneDistributionModels.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, `WorkoutMasksAndCardsSection.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`, `MyPreferenceManager.kt`, and `strings.xml`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.5 (`ATT-1390`, commit `e3966ebf`) introduced vertical columns; Sprint 2026-40.8 (`ATT-1845`, commit `7b14d9a2`) introduced `ZoneCardDisplayMode` with `FIVE_ZONES` and `HISTOGRAM`; Sprint 2026-41.2 (`ATT-2147`, commit `c8192a0e`) introduced `TelemetryMetricGraphMarginalBarsContractTest.kt` verifying `ZoneCardDisplayMode.FIVE_ZONES`; Sprint 2026-42.4 (`ATT-3184`) expanded Power to 7 zones; Sprint 2026-42.4 (`ATT-3390`, commit `38706240`) added stacked bar and DataStore preference key; Sprint 2026-42.5 (`ATT-3676`) exposes default selector in Advanced Settings and wires to workout summaries.
3. *Root Reason for Existing Formulation*: In ATT-3390, primary focus was implementing the `ZoneDistributionStackedBar` composable, the 3-way segmented button switching on individual cards, and defining the enum and DataStore persistence key. Exposing the global default selector in Advanced Settings and passing it down as the card default was deferred to keep ATT-3390 focused on graphical component rendering.
4. *Preservation of Core Invariants*: Interactive per-card session toggling preserved; backward-compatible `FIVE_ZONES` enum alias preserved; telemetry metric graph marginal strip rendering preserved; REQ-UI-262 <= 400 lines limit preserved; and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete in Advanced Settings navigating to Workout-Masken & Detailkarten,
• *When* Zone Analysis is enabled in either List or Details,
• *Then* a dedicated 3-way segmented button selector titled Zone Card Display Mode is displayed with options Stacked Bar, Columns, and Histogram, and selecting any option updates `workoutCardPrefs.zoneCardDisplayMode` and persists to DataStore on Save.
• *Given* `workoutCardPrefs.zoneCardDisplayMode` configured as `STACKED_BAR`,
• *When* viewing the workout journal list (`WorkoutSummary.kt`),
• *Then* all `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` instances initialize displaying the horizontal stacked bar (`ZoneDistributionStackedBar`), and when the global preference in DataStore is updated to `COLUMNS`, all cards without manual overrides reactively re-render as column charts.
• *Given* a workout summary card currently rendering in `STACKED_BAR` by global default,
• *When* the athlete taps the Columns button on that specific card,
• *Then* that card switches to `COLUMNS`, preserving its manual selection during the active session.
• *Given* global preference configured as `HISTOGRAM`,
• *When* viewing an activity without fine-grained telemetry histogram data (`distribution.histogram == null`),
• *Then* `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` gracefully resolve to `COLUMNS`, and the 2-button segmented row highlights Columns as selected.
• *Given* `WorkoutMasksAndCardsSection.kt` after adding the segmented button row selector,
• *When* `AdvancedTuningModularityTest.testAllTuningSourceFilesUnder400Lines` executes,
• *Then* the file length is strictly <= 400 lines.

**Invariants**: Backward-compatible `FIVE_ZONES` enum value, telemetry graph marginal strip preservation, REQ-UI-262 <= 400 lines limit, zero SQLite migrations, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-373: Automatic Training Zone Boundary Calculation from FTP and Heart Rate Baselines [Verified]
- **Tests**: `TST-UI-336`, `TST-UI-391`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZoneCalculationMath.kt`, `ZoneValidator.kt`, `ZoneNumberParser.kt`, `ZoneCalculatorDialog.kt`, `ZonesSettingsActivity.kt`, `SettingsDataStore.kt`
- **Rationale**: The system SHALL provide automated, deterministic training zone boundary calculation directly within the zones settings workflow (`ZonesSettingsActivity.kt`) from physiological benchmarks (Functional Threshold Power for cycling power, and Maximum Heart Rate / Resting Heart Rate for heart rate), while strictly preserving manual customization, guaranteeing single-transaction ACID atomicity in DataStore, enforcing two-layer boundary inversion protection with strict integer increments ($Z_n \ge Z_{n-1} + 1$), guaranteeing historical analytics immutability, and maintaining full 9-language localization parity (ATT-3188, amending `REQ-UI-370` and complementing `REQ-PRO-022`):
1. *Cycling Power Coggan 7-Zone Calculation & Zone 7 Ceiling (`REQ-UI-373.1`)*:
• The system SHALL compute cycling power zone upper boundaries from Functional Threshold Power (FTP, in Watts) using the canonical Coggan percentages: Z1 (55%), Z2 (75%), Z3 (90%), Z4 (105%), Z5 (120%), Z6 (150%).
• The system SHALL apply deterministic half-up rounding (`Math.round(percent * ftp).toInt()`) with boundary rationalization guaranteeing strictly monotonic ascending boundaries ($Z_{n\text{max}} \ge Z_{n-1\text{max}} + 1$).
• Zone 7 upper bound SHALL be capped at physical maximum $2500\text{ Watts}$ (finite storage invariant; zero `Infinity` values stored in DataStore). Zone 7 lower bound is $Z_{6\text{max}} + 1\text{ W}$.
• Allowed FTP input range SHALL be constrained to $[50, 600]\text{ Watts}$. For maximum FTP = $600\text{ W}$, Zone 6 cutoff evaluates to $900\text{ W}$, leaving a valid Zone 7 interval $[901\text{ W}, 2500\text{ W}]$. If any input sets $Z_{6\text{max}} \ge 2500\text{ W}$, `ZoneCalculationMath` and `ZoneValidator` MUST throw `IllegalArgumentException`.
2. *Heart Rate Zone Calculation (% of HRmax and Karvonen HRR) & Explicit Error Messages (`REQ-UI-373.2`)*:
• Model A (% of HRmax): Z1 (59%), Z2 (69%), Z3 (79%), Z4 (89%), Z5 ($HR_{\max}$).
• Model B (Karvonen HRR): Target HR = $\text{round}(HR_{\text{rest}} + (HR_{\max} - HR_{\text{rest}}) \times \text{intensity}\%)$. Z1 (59% HRR), Z2 (69% HRR), Z3 (79% HRR), Z4 (89% HRR), Z5 ($HR_{\max}$).
• Allowed baseline ranges: $HR_{\text{rest}} \in [30, 120]\text{ bpm}$, $HR_{\max} \in [80, 240]\text{ bpm}$.
• Primary Karvonen Invariant Check: Enforces $HR_{\max} > HR_{\text{rest}}$ and $HR_{\max} - HR_{\text{rest}} \ge 20\text{ bpm}$. If $HR_{\max} \le HR_{\text{rest}}$, the UI displays `zone_calc_error_hr_max_greater_rest` (*"HFmax muss größer als HFruhe sein"*) and disables calculation. If $HR_{\max} - HR_{\text{rest}} < 20\text{ bpm}$, the UI displays `zone_calc_error_karvonen_delta` (*"HFmax muss mindestens 20 bpm über HFruhe liegen"*) and disables calculation. `ZoneCalculationMath` throws `IllegalArgumentException` on violation.
3. *Two-Layer Monotonicity Validation & Strict Step Increment (`REQ-UI-373.3` & `REQ-UI-373.4`)*:
• Layer 1 (UI Reactive Barrier): Evaluated synchronously on every keystroke (`onValueChange`). Verifies $Z_{n\text{max}} \ge Z_{n-1\text{max}} + 1$ and $Z_{n\text{max}} \le Z_{n+1\text{max}} - 1$. Minimum integer delta $\Delta = 1$ is mandatory (adjacent zones cannot share identical cutoffs). If violated, sets `isError = true`, displays error message (\"Wert muss zwischen %d und %d liegen\"), and disables Save/Apply buttons.
• Layer 2 (Domain Blocking Guard): `ZoneValidator.validatePowerZoneStack()` and `validateHeartRateZoneStack()` throw `IllegalArgumentException` on any inversion ($Z_i \ge Z_{i+1}$) or out-of-bounds cutoff.
4. *Single-Transaction ACID Atomicity & UI Mutation Lock (`REQ-UI-373.5`, `REQ-UI-373.6` & `REQ-UI-373.10`)*:
• Baseline mutation, customization flag (`pwr_bike_zones_customized`, `hr_zones_customized`), and all cutoffs persist atomically in a single `dataStore.edit { }` block via file-level atomic swap.
• UI maintains `isSaving: Boolean` state; while writing to DataStore, action buttons are disabled with loading indicators, preventing concurrent or re-entrant mutations.
• UI observes state via UDF `collectAsState()` directly from DataStore, eliminating disconnected local state.
5. *Formal Data Integrity Policy & Historical Analytics Immutability (`REQ-UI-373.7`)*:
• Prospective-Only Baseline Mutation: Past completed workouts in SQLite (`WorkoutSummaries.db`, `Extrema.db`) are immutable and NEVER retroactively recalculated.
• Intra-Session Historical Fidelity: Mid-workout baseline changes apply strictly to subsequent telemetry ticks ($t \ge t_{\text{change}}$).
6. *Customization Lifecycle & Destructive Overwrite Warning (`REQ-UI-373.8`)*:
• Profiles transition to `CUSTOMIZED` upon any manual edit of an individual cutoff field.
• Recalculating over customized zones triggers a modal confirmation dialog warning that manual zones will be overwritten. Cancel preserves custom zones with 0 changes.
7. *Locale-Aware Number Parsing & 9-Language Localization Token Parity (`REQ-UI-373.9`)*:
• `ZoneNumberParser.parseIntOrNull()` handles `,` vs `.` and non-breaking space grouping.
• All UI strings maintain 100% translation parity and format specifier token parity (exact count, order, and argument types of `%d`, `%s`, `%1$d`) across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-373`. Amends `REQ-UI-370` (*Decoupled Training Zone Architecture & 7-Zone Coggan Cycling Power Model*, Sprint 2026-42.4, ATT-3184) and complements `REQ-PRO-022`. Targets `ZoneCalculationMath.kt`, `ZoneValidator.kt`, `ZoneNumberParser.kt`, `ZoneCalculatorDialog.kt`, `ZonesSettingsActivity.kt`, `SettingsDataStore.kt`, `SettingsDataStoreJavaHelper.kt`, and `strings.xml`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.5 (`ATT-1390`, commit `e3966ebf`) introduced manual zone configuration; Sprint 2026-42.4 (`ATT-3184`, commit `0da639ba`) expanded power to 7 zones. Active ticket `ATT-3188`.
3. *Root Reason for Existing Formulation*: Zones were previously entered manually because athlete baselines (FTP, HRmax) were not stored in DataStore, treating cutoffs as raw arbitrary thresholds.
4. *Preservation of Core Invariants*: Manual cutoff customization preserved, proportional migration fallback preserved, SQLite historical workout immutability preserved, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete entering FTP = 250W in the calculator dialog,
• *When* tapping "Berechnen",
• *Then* calculated preview displays: Z1 <= 138W, Z2 <= 188W, Z3 <= 225W, Z4 <= 263W, Z5 <= 300W, Z6 <= 375W, Z7 is [376W, 2500W] capped at 2500W.
• *Given* HRmax = 190 and HRrest = 50,
• *When* calculating Karvonen zones,
• *Then* cutoffs resolve to: Z1 <= 133, Z2 <= 147, Z3 <= 161, Z4 <= 175, Z5 <= 190 bpm.
• *Given* HRmax - HRrest < 20 bpm,
• *When* inputting in the dialog,
• *Then* calculation is disabled and the explicit localized error message `zone_calc_error_karvonen_delta` is displayed.
• *Given* HRmax <= HRrest,
• *When* inputting in the dialog,
• *Then* calculation is disabled and the explicit localized error message `zone_calc_error_hr_max_greater_rest` is displayed.
• *Given* manual editing of adjacent zone cutoffs with zero delta ($Z_3 = 200, Z_4 = 200$),
• *When* validated,
• *Then* validation fails because $Z_4$ must be >= $Z_3 + 1$.
• *Given* calculated zones applied from the dialog,
• *When* persisted to DataStore,
• *Then* baseline, timestamp, customization flag, and all cutoffs save atomically in a single transaction while disabling action buttons with a loading state.
• *Given* customized zones and user tapping recalculate,
• *When* modal confirmation appears and user taps cancel,
• *Then* existing custom zones remain completely untouched.

**Invariants**: Historical workout immutability in SQLite, finite Zone 7 bound (2500W ceiling), strict integer monotonicity ($Z_n \ge Z_{n-1} + 1$), canonical SI storage, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-374: Contextual Zone Settings Navigation Upon Physiological Sensor Pairing [Verified]
- **Tests**: `TST-UI-337`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Rationale**: The system SHALL automatically direct the athlete to the relevant training zone settings tab immediately after pairing a new physiological sensor (heart rate monitor or power meter) if physiological zones for that metric have not yet been configured, while maintaining silent pairing completion for configured athletes and non-physiological sensors (ATT-3189, ATT-4452, complementing `REQ-UI-159`, `REQ-UI-370`, and `REQ-UI-373`):
1. *Pairing Detection & Sensor Discrimination (`REQ-UI-374.1`)*:
• The system SHALL monitor pairing transitions when a sensor's state changes from `isPaired == false` to `isPaired == true` in `DeviceListViewModel`.
• The system SHALL discriminate device capability based on `DeviceType`:
- Heart Rate Monitor: `DeviceType.HRM` (BLE Heart Rate or ANT+ Heart Rate).
- Cycling Power Meter: `DeviceType.BIKE_POWER` (BLE Cycling Power or ANT+ Bike Power).
• For all other sensor types (`BIKE_SPEED`, `BIKE_CADENCE`, `BIKE_SPEED_AND_CADENCE`, `RUN_SPEED`, `ENVIRONMENT`, `SPEED_AND_LOCATION_GPS`, etc.), the system SHALL complete pairing silently on the sensor screen without navigating away.
• Unpairing a sensor (`isPaired` transitioning to `false`) SHALL NEVER trigger navigation.
2. *Zone Configuration Status Guard in SettingsDataStore (`REQ-UI-374.2`)*:
• The system SHALL maintain two persistent boolean preferences in `SettingsDataStore`:
- `KEY_IS_HR_ZONES_CONFIGURED = booleanPreferencesKey("is_hr_zones_configured")`
- `KEY_IS_PWR_ZONES_CONFIGURED = booleanPreferencesKey("is_pwr_zones_configured")`
• On a fresh installation, both flags SHALL default to `false`.
• Migration Heuristic / Fallback: If `KEY_IS_HR_ZONES_CONFIGURED` is unset, the system evaluates `hr_max != null

### REQ-UI-376: Real-Time Visual Training Zone Gauge Cockpit Integration [Verified]
- **Tests**: `TST-UI-339`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZoneGaugeBar.kt`, `ZoneGaugeMath.kt`, `SensorFieldView.kt`, `ZoneDisplayOptions.kt`, `ZonesSettingsActivity.kt`
- **Rationale**: The system SHALL provide an integrated real-time visual training zone gauge bar for Heart Rate and Cycling Power sensor tiles in the live tracking cockpit, dynamically adapting to the athlete's configured zone model (5-zone Heart Rate or 7-zone Coggan Cycling Power), calculating high-precision intra-zone needle positions, providing ambient peripheral comprehension, and maintaining strict AMOLED contrast and performance budgets (ATT-1293, extending `REQ-UI-172` and complementing `REQ-UI-370`, `REQ-UI-372`, `REQ-UI-373`, and `REQ-UI-374`):
1. *User Configuration & DataStore Persistence (`REQ-UI-376.1`)*:
• `ZoneDisplayOptions.kt` SHALL define property `val showGauge: Boolean = false`.
• `SettingsDataStore.kt` SHALL serialize and persist `showGauge` across application restarts for all three zone profiles (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`) using preference keys `hr_run_show_zone_gauge`, `hr_bike_show_zone_gauge`, and `pwr_bike_show_zone_gauge`.
• `ZonesSettingsActivity.kt` SHALL include a toggle row \"Zonen-Balken / Zone gauge bar\" (`R.string.zone_display_gauge`) in the \"Zonendarstellung / Zone Visualization\" card, updating the live preview and persisting to DataStore.
2. *Decoupled Segment Geometry & Equal Visual Width (`REQ-UI-376.2`)*:
• The visual gauge track SHALL be partitioned into $K$ contiguous, equal-width horizontal segments matching the active metric's physiological model:
- For Heart Rate (`HR_RUN`, `HR_BIKE`): Exactly $K = 5$ segments using `TTColor.Zone1..Zone5`.
- For Cycling Power (`PWR_BIKE`): Exactly $K = 7$ segments using `TTColor.Zone1..Zone7`.
• Equal visual width per zone ($\frac{1}{K}$ width each) guarantees that narrow high-intensity target zones (e.g. Z4 Sweetspot, Z5 VO2max) remain clear, readable, and trackable without shrinking to unreadable slivers.
3. *Intra-Zone Needle Interpolation & Clamping Math (`ZoneGaugeMath.kt`) (`REQ-UI-376.3`)*:
• For interior closed zones $i \in \{1 \dots K-1\}$ with bounds $[L_i, U_i]$:
$$\text{fraction}_i = \frac{v - L_i}{U_i - L_i} \in [0.0, 1.0]$$
$$x_{\text{needle}} = \frac{(i - 1) + \text{fraction}_i}{K} \in [0.0, 1.0]$$
• Baseline lower bounds: $L_1 = 0.0\text{ W}$ for Power; $L_1 = 30.0\text{ bpm}$ for Heart Rate.
• For readings below baseline ($v \le L_1$): Needle clamps cleanly to $x_{\text{needle}} = 0.0$ (left edge of Zone 1).
• Open-Ended Top Zone Dynamic Ceiling:
- For HR Zone 5: Lower bound $L_5 = Z_{4\text{max}}$. Nominal ceiling $U_5 = \max(H\!R_{\max}, Z_{4\text{max}} + 25\text{ bpm})$.
- For Power Zone 7: Lower bound $L_7 = Z_{6\text{max}}$. Nominal ceiling $U_7 = \min(2500\text{ W}, \max(Z_{6\text{max}} + 300\text{ W}, \text{FTP} \times 2.0))$, conforming to the $2500\text{ W}$ physical ceiling of `REQ-UI-373.1`.
- For top zone ($v \ge L_{\text{top}}$):
$$\text{fraction}_{\text{top}} = \min\left(1.0, \frac{v - L_{\text{top}}}{U_{\text{top}} - L_{\text{top}}}\right)$$
$$x_{\text{needle}} = \frac{(K - 1) + \text{fraction}_{\text{top}}}{K}$$
- Peak sprints ($v \ge U_{\text{top}}$) clamp cleanly to $x_{\text{needle}} = 1.0$ without overflow.
4. *Telemetry Stale / Disconnected / Null State Handling (`REQ-UI-376.4`)*:
• When telemetry is absent, disconnected, unconfigured, or stale (`v == null`, `v.isNaN()`, `!v.isFinite()`, or stale packet age $> 5\text{ s}$):
- The needle SHALL be completely hidden (needle alpha 0.0).
- The segmented zone track SHALL remain rendered with dimmed opacity (alpha 0.35), preserving full visual context of the zone boundaries.
5. *Cockpit UI & AMOLED Contrast Compliance (`REQ-UI-376.5`)*:
• `ZoneGaugeBar.kt` SHALL render a compact horizontal track (`height = 6.dp`, `shape = RoundedCornerShape(3.dp)`).
• Segments SHALL be separated by thin vertical dividers (`1.dp`).
• The outer track and segment dividers SHALL be stroked with `MaterialTheme.colorScheme.outlineVariant` (`#2B3342`), ensuring non-text contrast $\ge 3:1$ against True-Black AMOLED backgrounds (`#000000`) for dark zones (`TTColor.Zone6` and `TTColor.Zone7`).
• The indicator needle SHALL be rendered with a high-contrast inverted marker (Pure White `#FFFFFF` with a 1dp dark shadow/border) with minimum height of `10.dp` centered vertically over the track, achieving $> 12:1$ contrast against any zone segment color.
• When configured, a compact active zone badge (`Z1`..`Z7`) rendered in the active zone color SHALL appear adjacent to the gauge.
6. *60 FPS Performance & Recomposition Decoupling (`REQ-UI-376.6`)*:
• The gauge needle position SHALL be calculated via pure $O(1)$ math.
• Needle rendering in Compose SHALL execute via canvas drawing or drawWithContent without triggering full tile recompositions on high-frequency power packets (2Hz–4Hz).
7. *9-Language Localization Parity (`REQ-UI-376.7`)*:
• Add string token `zone_display_gauge` across all 9 application locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-376`. Extends `REQ-UI-172` (*Configurable Zone Visual Presentation*), complements `REQ-UI-370` (*7-Zone Coggan Cycling Power Model*), `REQ-UI-372` (*Horizontal Stacked Bar*), `REQ-UI-373` (*Automated Zone Boundaries*), and `REQ-UI-374` (*Contextual Zone Settings Navigation*). Targets `ZoneDisplayOptions.kt`, `SensorFieldState.kt`, `SensorFieldView.kt`, `ZoneGaugeBar.kt`, `ZonesSettingsActivity.kt`, and `SettingsDataStore.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.5 (`ATT-1390`, commit `e3966ebf`) introduced `ZoneDisplayOptions` (background tint, edge strips, text color). Sprint 2026-42.4 (`ATT-3184`, commit `0da639ba`) added 7 power zones. Active ticket `ATT-1293`.
3. *Root Reason for Existing Formulation*: Background tint and edge strips indicate categorical zone membership but lack continuous intra-zone target location feedback during hard efforts.
4. *Preservation of Core Invariants*: Full backward compatibility for `ZoneDisplayOptions`, True-Black AMOLED theme contrast, 16ms/60fps frame budget, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete configuring zone display options in Zone Settings,
• *When* enabling "Zonen-Balken / Zone gauge bar",
• *Then* `showGauge` persists to DataStore as true and displays live in the preview tile.
• *Given* active heart rate tracking in the cockpit with `showGauge == true`,
• *When* HR is at the midpoint of Zone 3,
• *Then* `ZoneGaugeBar` displays 5 equal segments (Z1..Z5) and the needle is positioned at $x = 0.50$ (middle of segment 3).
• *Given* active cycling power tracking in the cockpit with `showGauge == true`,
• *When* power is in Zone 6 (Anaerobic Capacity),
• *Then* `ZoneGaugeBar` displays 7 equal segments (Z1..Z7) using `TTColor.Zone1..Zone7` and the needle indicates position within segment 6.
• *Given* dark/AMOLED theme,
• *When* observing `ZoneGaugeBar` in Zone 6 or Zone 7,
• *Then* the track and dividers are outlined with `outlineVariant` and the needle is white with dark outline, maintaining WCAG contrast $> 3:1$.
• *Given* disconnected or null sensor data,
• *When* observing `ZoneGaugeBar`,
• *Then* the needle is hidden and the track is rendered with dimmed opacity (0.35 alpha).

**Invariants**: Existing `ZoneDisplayOptions` backward compatibility, AMOLED True-Black compatibility, 60fps frame budget, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-377: Pedal Power Balance Real-Time Visual Split Indicator Cockpit Integration [Verified]
- **Tests**: `TST-CON-022`, `TST-UI-340`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: \Delta
- **Rationale**: The system SHALL provide an integrated real-time visual pedal power balance split-bar widget for dual-sided power meters in the live tracking cockpit, featuring dual split formatting, center-anchored bidirectional deviation gauging, physiological asymmetry tier color-coding, and strict True-Black AMOLED compliance (ATT-1295, extending `REQ-UI-171` and `REQ-UI-103`, and complementing `REQ-UI-376`):
1. *Mathematical Representation & Telemetry Ingestion (`REQ-UI-377.1`)*:
• `PedalPowerBalanceMath.kt` SHALL consume raw telemetry representing Right pedal percentage $P_R \in [0, 100]\%$.
• The system SHALL calculate Left pedal percentage $P_L = 100 - P_R$.
• For valid readings, the deviation magnitude is $

### REQ-UI-378: Compact Action Buttons and Header Height Containment in ZonesSettingsActivity [Verified]
- **Tests**: `TST-UI-341`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZonesSettingsActivity.kt`
- **Rationale**: The system SHALL ensure header compactness and prevent vertical layout blowout in `ZonesSettingsActivity.kt` by replacing text buttons with compact symbol icon actions and bounding title container width, adhering to `docs/design_guidelines.md` Section 5.6 (ATT-3675, amending `REQ-UI-370` and `REQ-UI-373`):
1. *Compact Symbol Icon Actions (`REQ-UI-378.1`)*:
• In `ZonesSettingsActivity.kt`, the two horizontal `TextButton` components ("Zonen berechnen" and "Auf Standardwerte zurücksetzen") SHALL be replaced with compact Material 3 `IconButton` components: `Icons.Default.Calculate` (contentDescription `R.string.zone_calc_button`) and `Icons.Default.RestartAlt` (contentDescription `R.string.reset_to_defaults`).
• Both icons SHALL use `tint = MaterialTheme.colorScheme.onPrimaryContainer`.
2. *Bounded Title Container & Ellipsis Guard (`REQ-UI-378.2`)*:
• The left-side title container holding `title_edit_zones` and `SuggestionChip` SHALL apply `Modifier.weight(1f, fill = false)`.
• The title `Text` component SHALL declare `maxLines = 1` and `overflow = TextOverflow.Ellipsis`.
3. *Header Height Containment Invariant (`REQ-UI-378.3`)*:
• The total measured height of the header row in `ZonesSettingsActivity.kt` SHALL remain strictly $\le 64\text{ dp}$ across all supported mobile viewports ($360\text{ dp} \dots 600\text{ dp}$) and all 9 application locales.
4. *Accessibility Parity (`REQ-UI-378.4`)*:
• Both action buttons SHALL maintain a minimum $48\text{ dp} \times 48\text{ dp}$ touch target with localized `contentDescription` matching `R.string.zone_calc_button` and `R.string.reset_to_defaults`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends UI presentation of `REQ-UI-370` (*7-Zone Coggan Cycling Power Model*) and `REQ-UI-373` (*Automatic Training Zone Boundary Calculation*). Targets `ZonesSettingsActivity.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.4 (`ATT-3184` commit `0da639ba`, `ATT-3188` commit `c800ca58`).
3. *Root Reason for Existing Formulation*: Actions were initially introduced with textual `TextButton` controls, which caused horizontal layout starvation and 30-line vertical wrapping under localized German strings.
4. *Preservation of Core Invariants*: Full functional calculation and reset-to-defaults capabilities preserved, accessibility descriptions preserved, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `ZonesSettingsActivity` rendered on Google Pixel 10 under German locale,
• *When* the header row renders,
• *Then* header height is $\le 64\text{ dp}$, tab row and boundary cards remain visible above the fold, and zero 1-character vertical text wrapping occurs.
• *Given* tapping the Calculate icon button,
• *When* clicked,
• *Then* `ZoneCalculatorDialog` opens.
• *Given* tapping the Reset icon button,
• *When* clicked,
• *Then* `dataStore.resetToDefaults` is executed.

**Invariants**: Header height $\le 64\text{ dp}$, $48\text{ dp}$ touch targets, WCAG accessibility parity, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-379: State-Aware Live Segment Lifecycle Metrics, Past-Finish Rollout & PR Delta Feedback [Verified]
- **Tests**: `TST-UI-342`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `LiveSegmentSheet.kt`, `SegmentLiveDetails.kt`, `LiveSegmentsRepository.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL decouple post-finish rollout tracking from lateral course offset, provide real-time Personal Record (PR) time delta feedback upon completion, and conditionally render metrics based on segment lifecycle state in `LiveSegmentSheet.kt` and `SegmentLiveDetails.kt` (ATT-3578, complementing `REQ-MAP-027`, `REQ-UI-196`, `REQ-UI-340`, and `REQ-UI-366`):
1. *Domain Model Decoupling (`LiveSegmentData`)*:
• `LiveSegmentData` SHALL declare dedicated fields for post-finish tracking and PR delta: `val timeOnSegment_raw: Long = 0L`, `val distancePastFinish: String = "--"`, `val distancePastFinish_raw: Double = 0.0`, `val prDeltaSeconds: Long? = null`, `val prDeltaFormatted: String? = null`.
• In `LiveSegmentStatus.FINISHED`, `LiveSegmentsRepository` SHALL store `distanceToEnd` exclusively in `distancePastFinish` and `distancePastFinish_raw`.
• `LiveSegmentsRepository` SHALL NOT overload `segmentOffset` or `segmentOffset_raw` with `distanceToEnd`.
2. *Personal Record (PR) Delta Calculation (`LiveSegmentsRepository.kt`)*:
• When a segment finishes with `staticData.summary.prTime_raw > 0`:
- `prDeltaSeconds` SHALL equal `timeOnSegment - prTime_raw`.
- `prDeltaFormatted` SHALL format with explicit sign (`"-M:SS"` if `prDeltaSeconds <= 0`, `"+M:SS"` if `prDeltaSeconds > 0`).
• If `staticData.summary.prTime_raw <= 0`, `prDeltaSeconds` and `prDeltaFormatted` SHALL remain `null`.
3. *State-Aware Metric Visibility (`SegmentLiveDetails.kt`)*:
• **When `LiveSegmentStatus.APPROACHING`**:
- Render Line 1: `R.string.segment_status_start_in` with `distanceToStart` (`titleLarge`, bold).
- Render Line 2: `R.string.segment_status_remaining` with `remainingDistance` (`bodyLarge`, bold).
- The lateral offset row (`R.string.segment_status_offset`) SHALL be completely hidden.
• **When `LiveSegmentStatus.ON_SEGMENT` or `ON_SEGMENT_CLOSE_TO_FINISH`**:
- Render Line 1: `distanceOnSegment`.
- Render Line 2: `R.string.segment_status_remaining` with `remainingDistance`.
- Render Line 3: `R.string.segment_status_offset` with `segmentOffset` and cross-track warning colors (`onSurfaceVariant` / `TTColor.Zone3` / `TTColor.Zone4`).
• **When `LiveSegmentStatus.FINISHED`**:
- Render Line 1: `distanceOnSegment` (`titleLarge`, bold).
- Render Line 2: `stringResource(R.string.segment_status_past_finish, liveSegmentData.distancePastFinish)` in neutral `MaterialTheme.colorScheme.onSurfaceVariant` (`bodyMedium`).
- Neither `remainingDistance` ("Ziel in: 0 m") nor `segmentOffset` ("Daneben: ...") SHALL be rendered.
- In the right column, below `timeOnSegment`: when `prDeltaFormatted != null`, render `stringResource(R.string.segment_status_pr_delta, liveSegmentData.prDeltaFormatted)` colored in `TTColor.Zone2` (green) when `(prDeltaSeconds ?: 1L) <= 0L` and `MaterialTheme.colorScheme.onSurfaceVariant` when `> 0L`.
4. *9-Language Localization Parity (Rule 29)*:
• `segment_status_past_finish` and `segment_status_pr_delta` SHALL exist with non-empty translations and matching format specifiers (`%1$s`) across EN, DE, ES, FR, IT, JA, NL, PL, PT.
• `segment_status_offset` SHALL be corrected in `values-ja` to "コースずれ: %1$s" and clarified in `es`, `fr`, `nl`, `pl`, `pt`.
5. *Preservation of System Invariants*:
• Virtual gate finish line crossing math, polyline orthogonal projection, bottom sheet layout height, and 100% full clean-room unit test pass rate MUST NOT be broken.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-379`). Complements `REQ-MAP-027` (*ClimbPro Cockpit Sheet*), `REQ-UI-196` (*Live Segment Bottom Sheet Design*), `REQ-UI-340` (*LiveSegmentSheet Type-Safe Numeric Metric Evaluation on Segment Finish*), and `REQ-UI-366` (*Harmonized Live Segment and Live Climb Tracking Bottom Sheet Architecture*).
2. *Historical Origin & Commit Trace*: `LiveSegmentData` and `SegmentLiveDetails` were introduced in Sprint 2026-40.1 (`ATT-1644` / `ATT-1735`, commits `50123512`, `7d488f51`) and refined in Sprint 2026-41.8 (`ATT-3108`, commit `91b12a9a`).
3. *Root Reason for Existing Formulation*: When segment tracking was first implemented, `segmentOffset` was the only secondary distance field on `LiveSegmentData`. To display how far the rider had traveled after crossing the finish line before auto-dismissal, `distanceToEnd` was shoved into `segmentOffset`. At the time, `SegmentLiveDetails` simply displayed all three lines statically without state-based visibility branches.
4. *Preservation of Core Invariants*: Active cross-track deviation warnings while on segment, along-track orthogonal projection, finish line virtual gate detection, and 100% full clean-room test suite pass rate are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a live segment transitioning to `LiveSegmentStatus.FINISHED` with `distanceToEnd = 210.0`, `timeOnSegment = 219`, and `prTime_raw = 229`,
• *When* `SegmentLiveDetails` renders,
• *Then* it displays `distanceOnSegment`, "Nach Ziel: 210 m" / "Past finish: 210 m" in neutral `onSurfaceVariant`, and "-0:10 vs. PR" in green (`TTColor.Zone2`), with zero occurrences of "Ziel in: 0 m" or red "Daneben: 210 m".
• *Given* a live segment in `LiveSegmentStatus.APPROACHING` state,
• *When* `SegmentLiveDetails` renders,
• *Then* it displays `distanceToStart` and `remainingDistance`, and completely omits the lateral offset row ("Daneben: --").
• *Given* a live segment in `LiveSegmentStatus.ON_SEGMENT` with `distanceToSegment = 40.0`,
• *When* `SegmentLiveDetails` renders,
• *Then* it displays `distanceOnSegment`, `remainingDistance`, and `segmentOffset` ("Daneben: 40 m") styled with `TTColor.Zone4`.
• *Given* all 9 supported locale `strings.xml` resource files,
• *When* resolving `segment_status_past_finish`, `segment_status_pr_delta`, and `segment_status_offset`,
• *Then* all keys exist, are non-empty, and format strings compile cleanly.

**Invariants**: Pure UI presentation, zero changes to GPS projection math, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-380: Complete High-Density Asset Coverage & Defensive Compose Resource Resolution Architecture [Verified]
- **Tests**: `TST-UI-343`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/res/drawable*`, Compose icon call sites, `DrawableDensityFallbackAuditTest.kt`, `ComposeSafePainterContractTest.kt`
- **Rationale**: The system SHALL eliminate `Resources$NotFoundException` crashes on density-split APKs and custom DPI devices by populating complete high-density raster drawables and systematically migrating dynamic Jetpack Compose icon call sites to defensive `safePainterResource` resolution (ATT-3529, amending `REQ-UI-312` and complementing `REQ-UI-375`):
1. *Pillar 1: Complete High-Density Asset Coverage (`REQ-UI-380.1`)*:
• The system SHALL provide high-density raster assets in `drawable-xxhdpi/` (3x) and `drawable-xxxhdpi/` (4x) for all core protocol, branding, and cloud drawables:
• `ant_logo.png`: `drawable-xxhdpi/` (144x144 px) and `drawable-xxxhdpi/` (192x192 px).
• `logo_protocol_bluetooth.png`: `drawable-xxxhdpi/` (192x192 px).
• `dropbox_logo_blue.png`: `drawable-xxxhdpi/` (192x192 px).
• `logo.png`: `drawable-xxhdpi/` (144x126 px) and `drawable-xxxhdpi/` (192x168 px).
• All populated PNGs SHALL maintain exact aspect ratios, transparent alpha channels, and crisp visual geometry matching existing raster assets.
2. *Pillar 2: Defensive Compose Architecture Migration (`REQ-UI-380.2`)*:
• The system SHALL migrate all dynamic icon rendering call sites from raw `painterResource(id = ...)` to `safePainterResource(id = ..., fallback = ...)` across all 11 target components:
• `PairingButtons.kt`: Pairing protocol items (`ant_logo`, `logo_protocol_bluetooth`).
• `PairingProtocolBottomSheet.kt`: BLE and ANT+ selection items.
• `DeviceTypeSelectionDialog.kt`: Device type list items.
• `DeviceItem.kt`: Discovered and paired device list items.
• `SensorSourceDialog.kt`: Sensor type header and device selection list items.
• `SensorStatus.kt`: Cockpit sensor availability indicators.
• `SearchArea.kt`: Sensor scanning indicator.
• `DropboxConnectionHeader.kt`: Cloud connection banner logo.
• `DropboxSettingsDialog.kt`: Settings dialog title icon.
• `ActivityTypeSelectionDialog.kt`: Sport activity selection dialog icons.
• `ControlTrackingButton.kt`: Primary tracking control action button.
3. *Fallback Vector Provisioning (`REQ-UI-380.3`)*:
• Each migrated call site SHALL supply a context-appropriate Material Design `ImageVector` fallback (e.g. `Icons.Default.Sensors`, `Icons.Default.Bluetooth`, `Icons.Default.Cloud`, `Icons.Default.Sports`) to guarantee graceful visual degradation if an asset is unresolvable.
4. *Automated CI Enforcement (`REQ-UI-380.4`)*:
• `DrawableDensityFallbackAuditTest.kt` SHALL enforce that core protocol and branding drawables exist across `drawable-xhdpi/`, `drawable-xxhdpi/`, and `drawable-xxxhdpi/`.
• `ComposeSafePainterContractTest.kt` SHALL enforce via AST/source code scanning that target Compose components never invoke raw `painterResource`.
5. *Binary & Layout Invariants (`REQ-UI-380.5`)*:
• Resource symbol names (`R.drawable.*`) SHALL remain identical.
• Sizing modifiers (`Modifier.size(...)`), padding, and clipping parameters SHALL remain identical.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-312` (*Universal Root Fallback Drawables and Guarded Compose Asset Resolution Architecture*). Targets `app/src/main/res/drawable*`, Compose icon call sites, and `DrawableDensityFallbackAuditTest.kt`. Formally tracked as `REQ-UI-380`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-2858` (Sprint `2026-41.5`) introduced `safePainterResource` and baseline fallbacks in root `res/drawable/`. Production release V4.9.38.5 (build 269) experienced a fatal crash on device in `PairingButtons.kt` (`R.drawable.ant_logo`) due to density gaps in Google Play split APKs and unguarded `painterResource` usage.
3. *Root Reason for Existing Formulation*: While `safePainterResource` was created, it was only applied to a subset of UI screens. Furthermore, Google Play App Bundle (`.aab`) density slicing omitted lower-density rasters from high-density split APKs.
4. *Preservation of Core Invariants*: Zero UI thread crashes, full density-split immunity, high visual fidelity on QHD+/4K screens, binary resource symbol stability, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the Android project resource directories `drawable-xxhdpi/` and `drawable-xxxhdpi/`,
• *When* auditing `ant_logo.png`, `logo_protocol_bluetooth.png`, `dropbox_logo_blue.png`, and `logo.png`,
• *Then* all assets are present with valid dimensions and non-zero byte size.
• *Given* the 11 target Compose UI components,
• *When* evaluating dynamic icon rendering,
• *Then* each call site resolves via `safePainterResource` with a non-null semantic fallback `ImageVector`.
• *Given* `DrawableDensityFallbackAuditTest` and `ComposeSafePainterContractTest`,
• *When* executed via `./gradlew testDebugUnitTest`,
• *Then* all tests pass with 100% compliance.

**Invariants**: Zero UI thread crashes, binary resource ID stability, visual fidelity, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-381: Centralized Jetpack Compose Design System Colors & Zero Hardcoded Color Literals Architecture [Verified]
- **Tests**: `TST-UI-344`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `Color.kt`, `Theme.kt`, `LiveClimbSheet.kt`, `ClimbDetailSheet.kt`, and 21 other Compose files
- **Rationale**: The system SHALL centralize all Jetpack Compose color tokens, semantic domain colors, and theme palettes in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt` and expose them via the `TTColor` design system object or Compose theme color schemes (ATT-3574):
1. *Centralized Color Token Definitions (`REQ-UI-381.1`)*:
• The system SHALL define AMOLED container tokens in `Color.kt`: `AmoledPrimaryContainer` (`#000000`), `AmoledOnPrimaryContainer` (`#FFFFFF`), `AmoledSurfaceBright` (`#1A1A1A`), `AmoledSurfaceContainerHigh` (`#121212`), `AmoledSurfaceContainerHighest` (`#000000`).
• The system SHALL define climb category colors in `TTColor`: `ClimbCategoryHc` (`#B71C1C`), `ClimbCategory1` (`#D32F2F`), `ClimbCategory2` (`#EF6C00`), `ClimbCategory3` (`#FBC02D`), `ClimbCategory4` (`#388E3C`), and `ClimbCategoryUncategorized` (`#757575`).
• The system SHALL define climb gradient band colors in `TTColor`: `ClimbGradeFlat` (`#4CAF50`), `ClimbGradeMild` (`#8BC34A`), `ClimbGradeMedium` (`#FFEB3B`), `ClimbGradeSteep` (`#FF9800`), `ClimbGradeSevere` (`#F44336`), and `ClimbGradeExtreme` (`#9C27B0`).
• The system SHALL define a single authoritative helper `TTColor.getClimbGradeColor(gradePercent: Double): Color` mapping slope percentages to the 6 discrete climb gradient bands.
• The system SHALL define dark map canvas and track tokens in `TTColor`: `DarkMapCanvas` (`#121212`) and `DarkMapTrackCyan` (`#00E5FF`).
• The system SHALL define geofence and location badge tokens in `TTColor`: `GeofenceFill` (`#332196F3`), `GeofenceStroke` (`#882196F3`), `GeofencePin` (`#2196F3`), `LocationBadgeHomeContainer` (`#E8F5E9`), `LocationBadgeHomeContent` (`#2E7D32`), `LocationBadgeWorkContainer` (`#FFF8E1`), `LocationBadgeWorkContent` (`#F57F17`), `LocationBadgeOtherContainer` (`#FFE0B2`), `LocationBadgeOtherContent` (`#E65100`).
• The system SHALL define cloud and connectivity tokens in `TTColor`: `DropboxBlue` (`#0061FE`) and `ConnectionConnected` (`#2E7D32`).
• The system SHALL define cockpit gauge and sensor border tokens in `TTColor`: `PauseAmber` (`#F59E0B`), `SensorActiveBorder` (`#2196F3`), `SensorBorderDark` (`#383838`), `SensorBorderLight` (`#D6D6D6`), `BalanceGaugeTrack` (`#1B1B1F`), `ZoneGaugeTrack` (`#1B1B1F`), and `WarningBorderLight` (`#E0E0E0`).
2. *Purge of Inline Color Literals Across Consuming UI Components (`REQ-UI-381.2`)*:
• All 67 inline instantiations of `Color(0x...)` across all 23 production files outside `Color.kt` SHALL be migrated to reference their centralized `Color.kt` or `TTColor` tokens.
• Both `LiveClimbSheet.kt` and `ClimbDetailSheet.kt` SHALL delegate elevation gradient resolution to `TTColor.getClimbGradeColor`.
3. *Zero Hex Literals Outside Color.kt Architecture Enforcement (`REQ-UI-381.3`)*:
• A static source code scanning contract test (`ThemeColorCentralizationAuditTest.kt`) SHALL enforce that exactly 0 occurrences of `Color(0x` exist outside `Color.kt` in `app/src/main/java/`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-381` (*Centralized Jetpack Compose Design System Colors & Zero Hardcoded Color Literals Architecture*). Amends architectural design system conventions under Rule 36. Targets `ui/theme/Color.kt`, `TTColor`, and all 23 consuming Jetpack Compose UI files.
2. *Historical Origin & Commit Trace*: Sprint 2026-41.2 (`ATT-1281`: Persistent Climbs Database & Live ClimbPro Cockpit Sheet), Sprint 2026-41.5 (`ATT-1399`: Live Cockpit Location & Barometer Calibration Feedback), and Sprint 2026-41.6 (`ATT-2946`: Speed-Dependent Map Zoom Calibration).
3. *Root Reason for Existing Formulation*: The "fence" (hardcoded inline `Color(0x...)` literals) was erected for high-velocity exploratory prototyping during early feature spikes without the ceremony of updating `Color.kt`. However, as the design system matures toward V4.9.40, this technical debt directly violates the Single Source of Truth & DRY principle (Rule 36), preventing centralized AMOLED/contrast theming.
4. *Preservation of Core Invariants*: All 67 color values are preserved with 100% ARGB numerical identity ($A, R, G, B$ values identical to the original hex literals), preserving pixel-perfect visual fidelity, dark mode contrast, zero UI thread crashes, and 100% full clean-room unit test suite pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* all production Kotlin source files in `app/src/main/java/`,
• *When* executing `ThemeColorCentralizationAuditTest.verifyZeroHexColorsOutsideColorKt`,
• *Then* exactly 0 occurrences of `Color(0x` exist outside `Color.kt`.
• *Given* `LiveClimbSheet.kt` and `ClimbDetailSheet.kt`,
• *When* rendering elevation profiles,
• *Then* both delegate to `TTColor.getClimbGradeColor(gradePercent)`.
• *Given* exact boundary and interior grade percentages across `[-5.0, 0.0, 2.99, 3.0, 5.99, 6.0, 8.99, 9.0, 11.99, 12.0, 14.99, 15.0, 25.0]`,
• *When* evaluating `TTColor.getClimbGradeColor(gradePercent)`,
• *Then* the returned colors match strictly `ClimbGradeFlat`, `ClimbGradeMild`, `ClimbGradeMedium`, `ClimbGradeSteep`, `ClimbGradeSevere`, and `ClimbGradeExtreme`.

**Invariants**: Exact ARGB color parity across all UI screens; zero runtime crashes; 100% full clean-room unit test suite pass rate MUST NOT be broken.

### REQ-UI-382: Comprehensive Canonical Formatting, Unit Conversion, and Dead Control Elimination Architecture [Verified]
- **Tests**: `TST-UI-345`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ClimbDetector.kt`, `LapSplitCalculator.kt`, `AdvancedTuningAccordion.kt`, `NavigationSection.kt`, `SensorsGpsFilterSection.kt`, `ClusterTuningContent.kt`
- **Rationale**: The system SHALL eliminate all hardcoded distance/speed unit conversions, raw metric formatting strings, and obsolete deprecated tuning controls across all remaining production components in strict adherence to Rule 36 (Single Source of Truth & DRY) and Rule 29 (Full 9-Language Localization Parity) (ATT-3575):
1. *Climb Detector Localization & Canonical Distance Formatting (`REQ-UI-382.1`)*:
• `ClimbDetector.kt` SHALL format climb distance via `DistanceFormatter().format_with_units(dist)` instead of manual metric division `dist / 1000.0`.
• `ClimbDetector.kt` SHALL resolve the localized climb title via `TrainingApplication.getAppContext()?.getString(R.string.climb_title) ?: "Climb"` instead of hardcoding German `"Anstieg"`.
• The climb name template SHALL format as `"$climbTitle $formattedDistance @ ${String.format(Locale.getDefault(), "%.1f%%", avgGrade)}"`.
2. *Lap Split Calculator Formatter Delegation (`REQ-UI-382.2`)*:
• In `LapSplitCalculator.kt`, `defaultPaceFormatter(paceSecPerMeter: Double)` SHALL delegate directly to `PaceFormatter().format_with_units(paceSecPerMeter)`, eliminating raw `"%d:%02d /km"`.
• In `LapSplitCalculator.kt`, `defaultSpeedFormatter(speedMps: Double)` SHALL delegate directly to `SpeedFormatter().format_with_units(speedMps)`, eliminating raw magic multiplier `3.6` and `"%.1f km/h"`.
3. *Tuning Accordion and Navigation Section Distance Formatting (`REQ-UI-382.3`)*:
• In `AdvancedTuningAccordion.kt`, `formatNavigationSubtitle` SHALL format route selection radius using `DistanceFormatter().format_with_units((radiusKm * 1000f).toDouble())`, eliminating hardcoded `"%.1f km"`.
• In `NavigationSection.kt`, route selection radius slider `valueText` and default text SHALL format using `DistanceFormatter().format_with_units((routeSelectionRadiusKm * 1000f).toDouble())` and `DistanceFormatter().format_with_units((TuningPreferencesDefaults.DEFAULT_ROUTE_SELECTION_RADIUS_KM * 1000f).toDouble())`.
4. *Sensors GPS Filter Section Canonical Speed Formatting (`REQ-UI-382.4`)*:
• In `SensorsGpsFilterSection.kt`, the slope minimum speed slider `valueText` SHALL format secondary speed via `SpeedFormatter().format_with_units(slopeMinSpeed.toDouble())` within `String.format(Locale.getDefault(), "%.1f m/s (%s)", slopeMinSpeed, SpeedFormatter().format_with_units(slopeMinSpeed.toDouble()))`, eliminating inline `slopeMinSpeed * 3.6f` and `"km/h"`.
5. *Cluster Tuning Dead Slider Elimination (`REQ-UI-382.5`)*:
• In `ClusterTuningContent.kt`, the dead `@Deprecated fun TuningSlider(...)` composable and all related imports SHALL be completely excised from the codebase.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Complements and closes technical debt from `REQ-UI-364` (ATT-3361), `REQ-UI-365` (ATT-3363), and `REQ-SET-077` (ATT-3365). Formally tracked as `REQ-UI-382`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.3 standardized primary UI call sites but omitted helper and tuning modules.
3. *Root Reason for Existing Formulation*: Initial feature spikes used ad-hoc string formatting; subsequent standardization passes did not scan peripheral files.
4. *Preservation of Core Invariants*: Numerical calculations (climb scores, pace values, tuning sliders) remain identical. 9-language localization parity is fully maintained (`R.string.climb_title` is already verified across all 9 languages). 100% full clean-room unit test suite pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `ClimbDetector.kt`, `LapSplitCalculator.kt`, `AdvancedTuningAccordion.kt`, `NavigationSection.kt`, and `SensorsGpsFilterSection.kt`,
• *When* running `MetricFormatterContractTest` and `SpeedConversionContractTest`,
• *Then* all contract assertions pass with zero hardcoded `"%.1f km"`, `"%.1f km/h"`, `"Anstieg"`, or `* 3.6` formulas.
• *Given* `ClusterTuningContent.kt`,
• *When* running `ClusterTuningContractTest`,
• *Then* `fun TuningSlider` is completely absent.

**Invariants**: Exact numerical parity across calculations; zero runtime crashes; 100% full clean-room unit test suite pass rate MUST NOT be broken.

### REQ-UI-383: Unified Internal Tracking Sheet Layout & Shared LiveTrackingEntitySheet Architecture [Verified]
- **Tests**: `TST-UI-346`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `LiveTrackingEntitySheet.kt`, `LiveSegmentSheet.kt`, `LiveClimbSheet.kt`, `LiveTrackingSheetContainerTest.kt`, `LiveSegmentSheetContractTest.kt`, `LiveClimbSheetLayoutTest.kt`
- **Rationale**: The system SHALL eliminate duplicated internal layout structures, divergent outer/inner paddings, and redundant divider declarations across live tracking sheets by introducing a shared internal layout composable `LiveTrackingEntitySheet` in `com.atrainingtracker.trainingtracker.ui.components.core` and refactoring both `LiveSegmentSheet.kt` and `LiveClimbSheet.kt` to strictly delegate to it, enforcing Rule 36 (Single Source of Truth & DRY) and guaranteeing visual and structural parity (ATT-3576, completing Tier 2 of `REQ-UI-366`):
1. *Shared Internal Layout Composable (`LiveTrackingEntitySheet.kt`)*:
• The system SHALL define `LiveTrackingEntitySheet` in package `com.atrainingtracker.trainingtracker.ui.components.core` accepting `headerContent: @Composable (Modifier) -> Unit`, `telemetryHudContent: @Composable (Modifier) -> Unit`, `elevationContent: @Composable () -> Unit`, and optional `modifier: Modifier = Modifier`.
• `LiveTrackingEntitySheet` SHALL enforce the canonical vertical layout structure: outer `Column(modifier = modifier.fillMaxWidth().wrapContentHeight().background(MaterialTheme.colorScheme.surface))`, `headerContent(Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp))`, canonical `HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))`, `telemetryHudContent(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))`, and `elevationContent()`.
2. *Refactor LiveSegmentSheet (`LiveSegmentSheet.kt`)*:
• In `LiveSegmentSheet.kt`, `MapDetailLayout`'s `header` slot SHALL invoke `LiveTrackingEntitySheet`, passing `SegmentHeader` to `headerContent` and `SegmentLiveDetails` to `telemetryHudContent`, completely eliminating direct `HorizontalDivider` instantiation.
3. *Refactor LiveClimbSheet (`LiveClimbSheet.kt`)*:
• In `LiveClimbSheet.kt`, the system SHALL decompose the monolithic layout into `ClimbHeader` and `ClimbLiveDetails`.
• `LiveClimbSheet` SHALL delegate its entire root layout to `LiveTrackingEntitySheet`, passing `ClimbHeader` to `headerContent`, `ClimbLiveDetails` to `telemetryHudContent`, and `ElevationProfile` to `elevationContent`, completely eliminating direct `HorizontalDivider` instantiation and divergent outer padding.
4. *Preservation of System Invariants*:
• Clamped wrap-content height (`REQ-UI-270`), dynamic peek heights (`BottomSheetDesign.PeekHeightLiveSegment`), elevation profile gradient shading and rider pin tracking (`REQ-UI-352`), and 100% full clean-room unit test suite pass rate MUST NOT be broken.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-366` (*Harmonized Live Segment and Live Climb Tracking Bottom Sheet Architecture & Container (Rule 36)*). Formally tracked as `REQ-UI-383`. Targets `LiveTrackingEntitySheet.kt`, `LiveSegmentSheet.kt`, `LiveClimbSheet.kt`, `LiveTrackingSheetContainerTest.kt`, `LiveSegmentSheetContractTest.kt`, and `LiveClimbSheetLayoutTest.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-3364` (Sprint 2026-42.3, commit `31c4eb4f`) implemented Tier 1 (`LiveTrackingSheetContainer` in `SensorGridScreen.kt`), but left `LiveClimbSheet.kt` untouched (0 lines changed).
3. *Root Reason for Existing Formulation*: The outer container consolidation in `SensorGridScreen.kt` took priority in ATT-3364 to prevent overlapping sheets, leaving internal sheet refactoring incomplete.
4. *Preservation of Core Invariants*: Exact divider styling (0.5dp thickness, `outlineVariant` with alpha 0.5f), uniform 16dp horizontal divider margin, HUD padding (horizontal = 16dp, vertical = 4dp), terrain icon, category badges, status badges, and route counters in `LiveClimbSheet`, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `LiveSegmentSheet.kt` and `LiveClimbSheet.kt`,
• *When* inspecting their source code in `LiveSegmentSheetContractTest` and `LiveClimbSheetLayoutTest`,
• *Then* both files invoke `LiveTrackingEntitySheet(` and neither file contains a direct `HorizontalDivider(` call.
• *Given* an active live segment sheet and an active live climb sheet,
• *When* rendered in the cockpit,
• *Then* header margins (`start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp`), 0.5dp divider margins (`horizontal = 16.dp`), and HUD margins (`horizontal = 16.dp, vertical = 4.dp`) are governed by the single shared `LiveTrackingEntitySheet` composable.
• *Given* the complete unit test suite,
• *When* executing `./gradlew testDebugUnitTest`,
• *Then* 100% of tests pass without errors or regressions.

**Invariants**: Wrap-content height, elevation profile pin alignment, divider styling, and 100% full-suite test pass rate MUST NOT be broken.

### REQ-UI-384: Compact Zone-Tinted Bordered Selector Pills in Zone Settings Preview [Verified]
- **Tests**: `TST-UI-347`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZonesSettingsActivity.kt`, `ZonesSettingsPreviewPillsContractTest.kt`
- **Rationale**: The system SHALL prevent horizontal text starvation and vertical line wrapping (`"Z\n1"`..`"Z\n7"`) in the live preview zone selector row of `ZonesSettingsActivity.kt` by replacing Material 3 `FilterChip` + `leadingIcon` controls with compact zone-tinted bordered pills (`ZonePreviewPill`), adhering to Rule 38 (`docs/design_guidelines.md` Section 5.6, ATT-3776, complementing `REQ-UI-172` and `REQ-UI-370`):
1. *Elimination of FilterChip & LeadingIcon Overhead (`REQ-UI-384.1`)*:
• The preview zone selector row in `ZonesSettingsActivity.kt` SHALL NOT use Material 3 `FilterChip` or a separate `leadingIcon` circle indicator.
• Instead, it SHALL render a single horizontal `Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp))` containing `zoneCount` (`5` for Heart Rate, `7` for Cycling Power) equal-weight (`Modifier.weight(1f)`) `ZonePreviewPill` composables.
2. *Zone-Tinted Border, Subtle Fill Styling & WCAG AA Contrast (`REQ-UI-384.2`)*:
• Each `ZonePreviewPill` SHALL use `RoundedCornerShape(8.dp)`.
• Background container color SHALL be tinted with the zone's canonical color (`zone1Color`..`zone7Color`): `zoneColor.copy(alpha = if (selected) 0.28f else 0.12f)`, harmonizing with `ZoneRow`'s `0.12f` card background alpha.
• Border stroke SHALL use the zone's canonical color: `BorderStroke(width = if (selected) 2.dp else 1.dp, color = if (selected) zoneColor else zoneColor.copy(alpha = 0.6f))`.
• Label text color (`MaterialTheme.colorScheme.onSurface`) over the subtle zone-tinted background (`0.12f` / `0.28f` alpha blended over `surface`) SHALL maintain a WCAG AA contrast ratio $\ge 4.5:1$ in both Light and Dark/AMOLED themes.
3. *Single-Line Centered Label & Font Scale Guarantee (`REQ-UI-384.3`)*:
• Each pill SHALL render centered text `"Z1"`..`"Z7"` with `maxLines = 1`, `softWrap = false`, `fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium`, and compact horizontal padding (`<= 4.dp`), guaranteeing zero vertical line wrapping across all 5-zone and 7-zone tabs even under Large/Largest system font scales (up to 130%).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-384`, complementing `REQ-UI-172` (`ATT-1390`) and `REQ-UI-370` (`ATT-3184`). Targets `ZonesSettingsActivity.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.5 (`ATT-1390`, commit `e3966ebf`) introduced 5 `FilterChip`s for Material 3 compliance; Sprint 2026-42.4 (`ATT-3184`, commit `0da639ba`) expanded `PWR_BIKE` to 7 `FilterChip`s.
3. *Root Reason for Existing Formulation*: Material 3 `FilterChip` with a `10.dp` `leadingIcon` circle was initially chosen to show zone color alongside selection state, but its ~32dp internal horizontal padding plus 10dp icon overhead proved incompatible with the density of 7-zone labels on standard 360dp–412dp screen widths.
4. *Preservation of Core Invariants*: Encoding zone color into the pill border and background tint preserves both color identity, WCAG AA contrast ($\ge 4.5:1$), and selection state while freeing ~32dp of horizontal padding per pill.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `ZonesSettingsActivity` on either a 5-zone HR tab or 7-zone Cycling Power tab (including 130% system font scale),
• *When* the live preview zone selector row renders,
• *Then* all `Z1`..`Z5` or `Z1`..`Z7` pills render on a single line (`maxLines = 1`, `softWrap = false`) with zone-colored borders, subtle zone-tinted fills ($\ge 4.5:1$ text contrast), and clicking any pill updates `previewZone`.

**Invariants**: Single-line rendering across 5 and 7 zones up to 130% font scale (Rule 38); WCAG AA contrast $\ge 4.5:1$; interactive `previewZone` selection preserved; 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-385: Streamlined Single-Action Header in ZonesSettingsActivity [Verified]
- **Tests**: `TST-UI-348`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZonesSettingsActivity.kt`, `ZonesSettingsHeaderContractTest.kt`
- **Rationale**: The system SHALL streamline the top header bar of `ZonesSettingsActivity.kt` by removing the status `SuggestionChip`, the Reset-to-Defaults `IconButton`, the "Last updated / Calibration recommended" row, and the "Athlete Weight" row, leaving strictly the screen title, the Calculate `IconButton`, and the `PrimaryScrollableTabRow` (ATT-3777, superseding the secondary header elements of `REQ-UI-373`, `REQ-UI-378`, and `REQ-TRK-019.4`):
1. *Single-Action Top Bar (`REQ-UI-385.1`)*:
• The top header bar of `ZonesSettingsActivity.kt` (`ZoneSettingsScreen`) SHALL contain strictly the screen title (`R.string.title_edit_zones`, with `maxLines = 1` and `overflow = TextOverflow.Ellipsis` inside a `Modifier.weight(1f, fill = false)` container) on the left and the single Calculate `IconButton` (`Icons.Default.Calculate`, `contentDescription = stringResource(R.string.zone_calc_button)`) on the right.
2. *Removal of Redundant Header Elements (`REQ-UI-385.2`)*:
• The header of `ZonesSettingsActivity.kt` SHALL NOT render:
- The status `SuggestionChip` (`R.string.zone_calc_status_customized` / `R.string.zone_calc_status_calculated`).
- The Reset-to-Defaults `IconButton` (`Icons.Default.RestartAlt` / `R.string.reset_to_defaults`).
- The "Last updated / Calibration recommended" row (`R.string.zone_calc_last_updated` / `R.string.zone_calc_calibration_recommended`).
- The "Athlete Weight" row (`R.string.athlete_weight_title` / `R.string.athlete_weight_edit`) and its `showAthleteWeightDialog` block.
3. *Preservation of Tab Bar & Calculator Dialog (`REQ-UI-385.3`)*:
• Directly beneath the title/action row, the header `Surface` SHALL render `PrimaryScrollableTabRow` (`tab_hr_run`, `tab_hr_bike`, `tab_pwr_bike`).
• Tapping the `Icons.Default.Calculate` `IconButton` SHALL continue to open `ZoneCalculatorDialog` with full `ZoneCalculatorStateHolder` state.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-385`, superseding secondary header elements of `REQ-UI-373` (`ATT-3188`), `REQ-UI-378` (`ATT-3675`), and `REQ-TRK-019.4` (`ATT-1289`). Targets `ZonesSettingsActivity.kt` and `ZonesSettingsHeaderContractTest.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.4 (`ATT-3188` commit `c800ca58`, `ATT-1289` commit `5eee662f`) and Sprint 2026-42.5 (`ATT-3675` commit `0cf5f417`).
3. *Root Reason for Existing Formulation*: Status chip, reset button, last-updated timestamp, and athlete weight row were initially placed in the `ZonesSettingsActivity` header before the dedicated Athlete Profile screen (`ATT-3704`) was designed and before on-device review revealed excessive header clutter.
4. *Preservation of Core Invariants*: Retaining `title_edit_zones`, `Icons.Default.Calculate`, and `PrimaryScrollableTabRow` preserves full zone calculation and tab switching while keeping `SettingsDataStore` methods (`resetToDefaults`, `ftpUpdatedAtFlow`, `athleteWeightKgFlow`) intact for unit tests and `ATT-3704`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `ZonesSettingsActivity` opened on any tab (`HR_RUN`, `HR_BIKE`, or `PWR_BIKE`),
• *When* the top header `Surface` renders,
• *Then* only the title (`title_edit_zones`), the `Icons.Default.Calculate` button, and the `PrimaryScrollableTabRow` are rendered in the header, and zero status chips, reset buttons, last-updated rows, or athlete weight rows appear in the header.
• *Given* the `Icons.Default.Calculate` button in the header,
• *When* tapped by the user,
• *Then* `ZoneCalculatorDialog` opens for the active `ZoneType`.

**Invariants**: Header row height $\le 64\text{ dp}$; single-line ellipsis title; $48\text{ dp}$ Calculate touch target; `SettingsDataStore` API stability; 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-386: Single-Line Non-Wrapping Zone Card Display Mode Segmented Button Architecture [Verified]
- **Tests**: `TST-UI-349`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, `values*/strings.xml`
- **Rationale**: The system SHALL prevent vertical word and character wrapping (`"Histo-\ngramm"`) across all 3-way `ZoneCardDisplayMode` `SingleChoiceSegmentedButtonRow` controls in `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, and `PowerZoneDistributionCard.kt` by enforcing compact single-line `SegmentedButton` layout and concise 9-locale chart labels (`<= 11` characters) in strict compliance with Rule 38 (`docs/design_guidelines.md` Section 5.6, ATT-3771, amending `REQ-UI-372`):
1. *Checkmark Suppression & Compact Horizontal Padding (`REQ-UI-386.1`)*:
• Every `SegmentedButton` in the 3-way `ZoneCardDisplayMode` `SingleChoiceSegmentedButtonRow` across `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, and `PowerZoneDistributionCard.kt` SHALL specify `icon = {}` and `contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)`.
• `SingleChoiceSegmentedButtonRow` in `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` SHALL include `Modifier.fillMaxWidth().height(28.dp)`.
2. *Single-Line Typography & Dual Selection Cue (`REQ-UI-386.2`)*:
• Every `SegmentedButton` label `Text` in the 3-way `ZoneCardDisplayMode` selector SHALL specify `style = MaterialTheme.typography.labelSmall`, `fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium`, `maxLines = 1`, `softWrap = false`, and `overflow = TextOverflow.Ellipsis`.
3. *Concise 9-Locale Chart Mode Labels (`REQ-UI-386.3`)*:
• Across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`), `zone_mode_stacked_bar`, `zone_mode_columns`, and `zone_mode_histogram` SHALL use concise statistical chart nouns of at most `11` characters (`values`: `"Stacked Bar"`, `values-de`: `"Balken"`, `values-es`: `"Barra"`, `values-fr`: `"Barre"`, `values-it`: `"Barra"`, `values-ja`: `"スタックバー"`, `values-nl`: `"Balk"`, `values-pl`: `"Pasek"`, `values-pt`: `"Barra"`), ensuring zero line wrapping and zero truncation even at 150% system font scale on 360dp viewports.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-386`, amending `REQ-UI-372` (`ATT-3390` & `ATT-3676`) under Rule 38. Targets `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, and `values*/strings.xml`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.4 (`ATT-3390` commit `63ccd164`) and Sprint 2026-42.5 (`ATT-3676` commit `b989e1ce`).
3. *Root Reason for Existing Formulation*: `WorkoutMasksAndCardsSection.kt` copied the 2-way `LapDisplayMode` `SingleChoiceSegmentedButtonRow` without `icon = {}` or `labelSmall`, and 6 locales used verbose 13–17 char translations for `zone_mode_stacked_bar`.
4. *Preservation of Core Invariants*: 3-way `ZoneCardDisplayMode` switching (`STACKED_BAR`, `COLUMNS`, `HISTOGRAM`), WCAG 2.1 AA contrast (`>= 4.5:1` text, `>= 3.0:1` boundary), dual non-color selection cues (`FontWeight.Bold` + `secondaryContainer`), and `< 400` line limit on `WorkoutMasksAndCardsSection.kt` are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, and `PowerZoneDistributionCard.kt`,
• *When* rendering the 3-way `ZoneCardDisplayMode` `SingleChoiceSegmentedButtonRow` in any of the 9 supported locales (up to 150% system font scale on a 360dp viewport),
• *Then* each `SegmentedButton` renders with `icon = {}`, `contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)`, `style = MaterialTheme.typography.labelSmall`, `maxLines = 1`, `softWrap = false`, and `overflow = TextOverflow.Ellipsis` on a single horizontal line without wrapping.
• *Given* all 9 `strings.xml` files,
• *When* auditing `zone_mode_stacked_bar`, `zone_mode_columns`, and `zone_mode_histogram`,
• *Then* all 27 strings are non-empty and have length `<= 11` characters.

**Invariants**: Zero line wrapping or mid-word breaks across all 9 locales up to 150% font scale (Rule 38); WCAG 2.1 AA contrast; `< 400` lines in `WorkoutMasksAndCardsSection.kt`; 100% clean-room test pass rate MUST NOT be broken.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-18 | AI Agent (`ATT-8206`) | `ATT-8206` | Split oversized shard into compliant sub-shard (<= 4,000 lines, `REQ-PRO-093.4`, `REQ-PRO-127.3`). |
