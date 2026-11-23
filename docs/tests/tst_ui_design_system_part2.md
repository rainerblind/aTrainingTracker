# Living Test Specification: UI Components & Design System (Part 2 of 3)

**Document ID**: `DOC-TST-UI-P2`  
**Version**: 1.0.7  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `ui_design_system_part2`  
**Prefixes**: `UI`, `UNT`  
**Scope**: Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts.  
**Total Tests**: 154

---

### TST-UI-255: **Unified Scrollable Container Architecture, Full-Width Spatial Toggles Layout, and Viewport Slotting Verification** [Verified]
- **Requirement(s)**: `REQ-UI-295`
- **Component**: **Unified Scrollable Container Architecture, Full-Width Spatial Toggles Layout, and Viewport Slotting Verification**
- **Jira**: ``ATT-2620`, `ATT-2744``
- **Test Description**:
  1. *SensorGridScreen Container Architecture Contract Tests (`TrackingTabWysiwygContractTest.kt`)*:
  • Verify `SensorGridScreen.kt` implements a unified `verticalScroll(rememberScrollState())` container for `ScreenMode.CONFIGURATION`.
  • Verify the sensor grid `Column` does not declare nested `verticalScroll()` when in `CONFIGURATION` mode.
  • Verify `ATrainingTrackerMap` and `ElevationProfile` are conditionally guarded by `screenMode != ScreenMode.CONFIGURATION`.
  • Verify `BottomSheetScaffold` sets `sheetPeekHeight = 0.dp`, disables swipe, and sets empty `sheetContent` in `CONFIGURATION` mode.
  • Verify bottom container includes `navigationBarsPadding()` to prevent occlusion by the system navigation bar.
  2. *Full-Width Spatial Toggles & Elimination of 2-Column Rows / Grey Dock (`TrackingTabWysiwygContractTest.kt`)*:
  • Verify `SensorGridScreen.kt` declares all 5 lower toggles (Map, Elevation, Live Segments, Live Climbs, Lap Button) as full-width cards within a unified `Column`.
  • Verify zero 2-column side-by-side `Row` constructs for spatial toggles in `CONFIGURATION` mode.
  • Verify absence of arbitrary nested grey dock `Surface` with tonal elevation 2 dp.
  • Verify text and status badge pill layout guarantees full label readability without ellipsis truncation.
  3. *TrackingTabsScreen Floating Lap Button Isolation Contract Test (`TrackingTabWysiwygContractTest.kt`)*:
  • Verify `TrackingTabsScreen.kt` suppresses floating `LapButton` when `screenMode == ScreenMode.CONFIGURATION`.
  4. *Spatial Toggle Interaction & Runtime Gating Contract Tests (`TrackingTabWysiwygContractTest.kt`)*:
  • Verify all 6 spatial toggle cards remain declared and bound to their respective actions and runtime gates.
  5. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-256: **Contextual ANT+ Service Guidance and Startup Dialog Elimination Verification** [Verified]
- **Requirement(s)**: `REQ-UI-296`
- **Component**: **Contextual ANT+ Service Guidance and Startup Dialog Elimination Verification**
- **Jira**: ``ATT-2513``
- **Test Description**:
  1. *Cold-Start Elimination Contract Tests (`AntStartupCheckContractTest.kt`)*:
  • Verify `MainActivityWithNavigation.kt` does not call `checkANTInstallation()` or `showInstallANTShitDialog()` in `onCreate()`.
  • Verify no dialogs are displayed on fresh launch.
  2. *Contextual Guidance UI & Sheet Contract Tests (`AntServicesStatusContractTest.kt`)*:
  • Verify `AntServicesStatusCard` renders in `DevicesTabbedScreen` when `protocol == Protocol.ANT_PLUS` and `BANALService.areAllANTServicesInstalled` is false.
  • Verify `AntServicesStatusSheet` renders live status for ANT Plugin Service, ANT Radio Service, and ANT USB Service.
  • Verify Google Play intent URLs use `market://details?id=...` with fallback.
  • Verify `PairingProtocolBottomSheet` presents ANT guidance when ANT+ is selected and services are missing.
  3. *9-Language Localization Parity Audit (`AntStatusLocalizationTest.kt`)*:
  • Verify all new ANT status string resources exist, are non-empty, and contain zero formatting errors across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-257: **Distance-Weighted Elevation Profile Gaussian Smoothing & Continuous Slope Grade Verification** [Verified]
- **Requirement(s)**: `REQ-UI-297`
- **Component**: **Distance-Weighted Elevation Profile Gaussian Smoothing & Continuous Slope Grade Verification**
- **Jira**: ``ATT-2512`, `ATT-2746``
- **Test Description**:
  1. *Pure Kernel Mathematical Unit Tests (`ElevationSmoothingMathTest.kt`)*:
  • Verify default spatial bandwidth $\sigma = 21.0\,\text{m}$ and default cutoff $3\sigma = 63.0\,\text{m}$.
  • Verify dynamic cutoff scaling ($3.0 * \sigma$) when custom $\sigma$ is supplied.
  • Verify distance-weighted Gaussian smoothing on synthetic quantized step data (0.2m–0.4m steps over 20m plateaus) eliminates flat plateaus and yields smooth, monotonic slope transitions.
  • Verify exact boundary weight renormalization: start boundary ($d=0$) and end boundary ($d=D_{total}$) altitudes match terrain endpoints within 0.1% without artificial sag or peak distortion.
  • Verify true geographic features (crests, saddles, summits) are preserved within $\pm 0.5\text{ m}$ of true elevation.
  • Verify degenerate input handling: empty list, single point, identical coordinates, non-positive distances return cleanly without NaN or exceptions.
  2. *Complexity & Performance Benchmark Unit Test (`ElevationSmoothingMathTest.kt`)*:
  • Verify that smoothing 10,000 dense points ($N = 10,000$) executes in $< 15\text{ ms}$ on JVM.
  3. *DataStore Clamping & Preference Persistence Unit Tests (`TuningPreferencesDataStoreTest.kt`)*:
  • Verify default value of `elevationSmoothingSigmaMeters` is `21.0f`.
  • Verify safety clamping: values $< 10.0\text{f}$ clamp to `10.0f`, values $> 50.0\text{f}$ clamp to `50.0f`.
  • Verify `updateElevationSmoothingSigmaMeters` persists to DataStore and emits via `tuningConfigFlow`.
  • Verify `resetToDefaults()` restores `21.0f`.
  4. *UI Contract & Dynamic Reactivity Tests (`ElevationProfileSmoothingContractTest.kt`, `AftermathAnalysisSectionContractTest.kt`)*:
  • Verify `ElevationProfile.kt` accepts `smoothingSigma` parameter and delegates to `ElevationSmoothingMath.smoothAltitudes(..., sigma = ...)`.
  • Verify `ElevationProfile.kt` reactively observes `tuningConfigFlow` from `TuningPreferencesDataStore`.
  • Verify `AftermathAnalysisSection.kt` renders `TuningSliderItem` with range 10..50 and 40 steps.
  • Verify `AdvancedTuningDialog.kt` binds `elevationSmoothingSigmaMeters` to state, save action, and factory reset.
  5. *9-Language Localization Parity Audit (`AftermathAnalysisLocalizationTest.kt`)*:
  • Verify `tuning_elevation_smoothing_sigma_title` and `tuning_elevation_smoothing_sigma_desc` exist and are non-empty across all 9 locales.
  6. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-258: **Route Map Climb Polyline Highlighting by Climb Category Classification Verification** [Verified]
- **Requirement(s)**: `REQ-UI-298`
- **Component**: **Route Map Climb Polyline Highlighting by Climb Category Classification Verification**
- **Jira**: ``ATT-2509``
- **Test Description**:
  1. *MapContentScope Climb Highlight Contract Tests (`ClimbPolylineContractTest.kt`)*:
  • Verify `MapContentScope.climbs()` creates polyline highlights for all classified climbs (`CAT_4`, `CAT_3`, `CAT_2`, `CAT_1`, `HC`).
  • Verify `ClimbCategory.UNCATEGORIZED` climbs are omitted from colored highlights, retaining standard route polyline coloring.
  • Verify climb polyline coordinates match `climb.pathPoints.map { it.latLng }` with fallback to `listOf(startLatLng, endLatLng)`.
  • Verify polyline colors match `getClimbCategoryColors(climb.category).first`.
  • Verify polyline `zIndex` is 25f (between route polyline and markers).
  2. *RouteOnMapScreen Architectural Integration Contract Tests (`RouteOnMapScreenClimbContractTest.kt`)*:
  • Verify `RouteOnMapScreen.kt` invokes `climbs(climbs)` within `mapContent`.
  3. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-259: **Elevation Profile Slope Gradient Preservation and X-Axis Climb Span Highlight Verification** [Verified]
- **Requirement(s)**: `REQ-UI-299`
- **Component**: **Elevation Profile Slope Gradient Preservation and X-Axis Climb Span Highlight Verification**
- **Jira**: ``ATT-2510``
- **Test Description**:
  1. *Elevation Profile Climb Span Contract Tests (`ElevationProfileClimbSpanContractTest.kt`)*:
  • Verify `ElevationProfile.kt` does NOT render accented ridge strokes over the elevation curve (no `drawLine` over `(px1, py1)` to `(px2, py2)` with climb category color).
  • Verify `ElevationProfile.kt` renders horizontal X-axis span bars for recognized cycling climbs along baseline (`y = height`).
  • Verify start and end distance bounds match `climb.pathPoints.first().distance` and `climb.pathPoints.last().distance` mapped via `distanceToCanvasX`.
  • Verify climb span bar uses `getClimbCategoryColors(climb.category).first` with `strokeWidth = 4.dp.toPx()` and `StrokeCap.Round`.
  • Verify `ClimbCategory.UNCATEGORIZED` climbs are excluded from X-axis span highlights.
  • Verify summit badges/pins remain intact above peak coordinates.
  2. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-260: **Dedicated Route Climb Detail View with Focused Map and Zoomed Elevation Profile Verification** [Verified]
- **Requirement(s)**: `REQ-UI-300`
- **Component**: **Dedicated Route Climb Detail View with Focused Map and Zoomed Elevation Profile Verification**
- **Jira**: ``ATT-2511``
- **Test Description**:
  1. *Climb Breakdown Item Click & Callback Contract Tests (`RouteClimbsBreakdownContractTest.kt`)*:
  • Verify `RouteClimbsBreakdownSection` provides `onClimbClick: ((Climb) -> Unit)?`.
  • Verify tapping a climb card dispatches `onClimbClick` with the corresponding `Climb` instance.
  • Verify `RouteOnMapScreen.kt` declares `selectedClimbForDetail` state and renders `ClimbDetailSheet` when non-null.
  2. *ClimbDetailSheet Structural & Viewport Contract Tests (`ClimbDetailSheetContractTest.kt`)*:
  • Verify `ClimbDetailSheet` contains header with climb title and `ClimbCategoryChip`.
  • Verify metrics row displays distance, elevation gain, avg grade, max grade, and start offset formatted via `LocalMetricFormatter`.
  • Verify `ATrainingTrackerMap` renders within sheet using `MapZoomFocus.EXPLICIT_BOUNDS` mapped from `climb.pathPoints`.
  • Verify isolated elevation profile canvas renders strictly between 0 and climb distance with slope gradient coloration.
  • Verify dismissal callback resets state.
  3. *9-Language Localization Parity Audit (`ClimbDetailLocalizationTest.kt`)*:
  • Verify all climb detail string resources (`routes_climb_max_grade`, etc.) exist and are non-empty across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-261: **Control Tracking Screen Sensor Header Layout Isolation & Collision Elimination Verification** [Verified]
- **Requirement(s)**: `REQ-UI-301`
- **Component**: **Control Tracking Screen Sensor Header Layout Isolation & Collision Elimination Verification**
- **Jira**: ``ATT-2479``
- **Test Description**:
  1. *ControlTrackingScreen Header Architecture Contract Tests (`ControlTrackingSensorHeaderContractTest.kt`)*:
  • Verify `ControlTrackingScreen.kt` implements a non-overlapping `Row` containing `ResearchButton` and `RemoteDevices`.
  • Verify `SearchArea` is rendered in its own dedicated slot above the `Row`.
  • Verify `RemoteDevices` receives `Modifier.weight(1f)` when devices are present.
  • Verify `ResearchButton` centers horizontally when `devices.isEmpty()` and `showResearchButton == true`.
  • Verify `ResearchButton` is omitted when `showResearchButton == false`.
  2. *RemoteDevices Component Parameterization & Spacing Contract Tests (`RemoteDevicesContractTest.kt`)*:
  • Verify `RemoteDevices` accepts `modifier: Modifier = Modifier` and forwards it to the root layout.
  • Verify `LazyRow` uses `Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)` ensuring distinct tile separation.
  3. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-262: **Route Starred Segments Spatial Overlap Matching, Chronological Route Breakdown & Map Synergy Verification** [Verified]
- **Requirement(s)**: `REQ-UI-302`
- **Component**: **Route Starred Segments Spatial Overlap Matching, Chronological Route Breakdown & Map Synergy Verification**
- **Jira**: ``ATT-2583``
- **Test Description**:
  1. *Spatial Overlap Matching Unit Tests (`RouteSegmentMatcherTest.kt`)*:
  • Verify sport type filtering: matching sport types (or `UNKNOWN`) pass, conflicting sport types rejected.
  • Verify corridor proximity: segments whose start and end coordinates fall within 25 m corridor are accepted; segments exceeding 25 m tolerance are rejected.
  • Verify directional bearing alignment: forward traversal ($\Delta \theta \le 45^\circ$, dot product $> 0$) passes; reverse direction ($\Delta \theta > 90^\circ$, negative dot product) or crossing perpendicular corridors rejected.
  • Verify progression and length consistency: $D_{end} > D_{start}$ strictly enforced, and route span distance approximates segment distance within tolerance ($

### TST-UI-263: **Route Matched Segment Detail Bottom Sheet, Focused Map, Isolated Elevation Profile & Interaction Parity Verification** [Verified]
- **Requirement(s)**: `REQ-UI-303`
- **Component**: **Route Matched Segment Detail Bottom Sheet, Focused Map, Isolated Elevation Profile & Interaction Parity Verification**
- **Jira**: ``ATT-2774``
- **Test Description**:
  1. *Segment Tap Interaction & Bottom Sheet State Contract Test (`RouteSegmentsBreakdownContractTest.kt`)*:
  • Verify `RouteSegmentsBreakdownSection` provides `onSegmentClick: ((MatchedRouteSegment) -> Unit)?`.
  • Verify tapping a segment card dispatches `onSegmentClick` with the corresponding `MatchedRouteSegment` instance.
  • Verify `RouteOnMapScreen.kt` declares `selectedSegmentForDetail` mutable state and renders `SegmentDetailSheet` when non-null.
  2. *SegmentDetailSheet Structural & Viewport Contract Tests (`SegmentDetailSheetContractTest.kt`)*:
  • Verify `SegmentDetailSheet` contains header with segment title, route counter badge, `ClimbCategoryChip` (if categorized), and athlete PR badge.
  • Verify metrics card displays start distance along route (`routes_segment_start_at`), city, and full `SegmentDetails` component (distance, Strava branding, avg/max grade, elevation gain, min/max altitude).
  • Verify `ATrainingTrackerMap` renders within sheet using `MapZoomFocus.EXPLICIT_BOUNDS` mapped from `matchedSegment.segment.path` with start and stop markers.
  • Verify isolated elevation profile renders strictly for the segment path with altitude annotations.
  • Verify dismissal callback resets `selectedSegmentForDetail` to null without disturbing route state.
  3. *9-Language Localization Parity Audit (`RouteSegmentsLocalizationTest.kt`)*:
  • Verify all route segment string resources (`routes_segment_counter`, `routes_segment_start_at`, `routes_segment_pr`, etc.) exist and are non-empty across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-264: **Control Tracking Screen Remote Device Tile Bounded Width, Multiline Wrapping & Centering Contract Verification** [Verified]
- **Requirement(s)**: `REQ-UI-304`
- **Component**: **Control Tracking Screen Remote Device Tile Bounded Width, Multiline Wrapping & Centering Contract Verification**
- **Jira**: ``ATT-2772``
- **Test Description**:
  1. *Bounded Maximum Tile Width Contract Test (`RemoteDevicesContractTest.kt`)*:
  • Verify `RemoteDeviceItem` declares `Modifier.widthIn(max = 72.dp)` on its root `Column` container.
  2. *Multiline Text Wrapping & Ellipsis Contract Test (`RemoteDevicesContractTest.kt`)*:
  • Verify the `Text` composable rendering `device.name` configures `maxLines = 2` and `overflow = TextOverflow.Ellipsis`.
  3. *Symmetrical Text Centering Contract Test (`RemoteDevicesContractTest.kt`)*:
  • Verify the `Text` composable rendering `device.name` configures `textAlign = TextAlign.Center`.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-265: **Inverted Route-Segment Association Engine, Segment Details Routes Breakdown & Cross-Navigation Verification** [Verified]
- **Requirement(s)**: `REQ-UI-305`
- **Component**: **Inverted Route-Segment Association Engine, Segment Details Routes Breakdown & Cross-Navigation Verification**
- **Jira**: ``ATT-2585``
- **Test Description**:
  1. *Inverted Spatial Matching Unit Tests (`RouteSegmentMatcherTest.kt`)*:
  • Verify candidate routes traversing the segment in forward direction are correctly matched with valid start/end distance offsets.
  • Verify candidate routes traversing in reverse direction or exceeding corridor tolerance are rejected.
  • Verify empty candidate routes or single-point segment paths return empty list cleanly.
  2. *SegmentRoutesSection Structural & Interaction Contract Tests (`SegmentRoutesSectionContractTest.kt`)*:
  • Verify `SegmentRoutesSection` renders `ElevatedCard` for each matching route displaying sport icon, route name, distance, elevation gain, and start offset.
  • Verify tapping a route card dispatches `onRouteClick` with the route's summary ID.
  • Verify `SegmentOnMapScreen.kt` integrates `SegmentRoutesSection` via `analyticsContent` in `MapDetailLayout`.
  • Verify `StarredSegmentsScreen.kt` renders `RouteOnMapScreen` when a route is clicked, with `BackHandler` returning to the segment.
  3. *9-Language Localization Parity Audit (`SegmentRoutesLocalizationTest.kt`)*:
  • Verify `segment_routes_containing_title` exists, is non-empty, and contains valid format specifiers across all 9 supported locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-266: **Royal Blue Route Color Tokens, Multi-Tier Contrast Hierarchy, and Living Style Guide Synchronization Verification** [Verified]
- **Requirement(s)**: `REQ-UI-306`
- **Component**: **Royal Blue Route Color Tokens, Multi-Tier Contrast Hierarchy, and Living Style Guide Synchronization Verification**
- **Jira**: ``ATT-2761``
- **Test Description**:
  1. *Route Color Tokens Audit (`Color.kt`)*:
  • Assert exact ARGB hex values for `RouteSelected` (`Color(0xFF1565C0)`), `RouteUnselected` (`Color(0xFF90CAF9)`), `RouteActiveNavigation` (`Color(0xFF1E88E5)`), `RouteActiveNavigationOverlay` (`Color(0xFF0D47A1)`).
  2. *Multi-Tier Contrast & Climb Category Differentiation (`RouteColorPaletteContractTest.kt`)*:
  • Verify RGB Euclidean / WCAG contrast between `RouteSelected` and all 6 climb categories (`HC`, `CAT_1`..`CAT_4`, `UC`), asserting Cat 4 green $\Delta C > 0.35$.
  3. *Active Navigation Polyline Hierarchy (`MapRouteActiveNavigationTest.kt`)*:
  • Verify `MapRoute` color resolution, width, and z-index hierarchy with new Royal Blue tokens.
  4. *Living Style Guide Synchronization (`docs/design_guidelines.md`)*:
  • Verify Sections 5.4 and 5.7 codify Royal Blue with zero stale green route references.
  5. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew clean testDebugUnitTest` asserting 100% test pass rate across all test classes.

### TST-UI-267: **Route Map Pin Elimination, Full-Spectrum UC Climb Polyline Highlighting, and Elevation Profile Baseline Parity Verification** [Verified]
- **Requirement(s)**: `REQ-UI-307`
- **Component**: **Route Map Pin Elimination, Full-Spectrum UC Climb Polyline Highlighting, and Elevation Profile Baseline Parity Verification**
- **Jira**: ``ATT-2748``
- **Test Description**:
  1. *Route Map Marker Contract Audit (`RouteOnMapScreenContractTest.kt`)*:
  • Verify `allMarkers` contains strictly Start and End markers when route path is non-empty; assert 0 `ic_ascent` markers are supplied to `markers()` on `RouteOnMapScreen`.
  2. *Full-Spectrum Climb Polyline Highlighting Contract Tests (`ClimbPolylineContractTest.kt`)*:
  • Verify all 6 categories (including `UNCATEGORIZED` -> `Color(0xFF757575)`) generate `ClimbHighlightData` at `zIndex = 25f`.
  3. *Elevation Profile Baseline UC Span Parity (`ElevationProfileClimbSpanContractTest.kt`)*:
  • Verify `ElevationProfile.kt` renders horizontal baseline span indicator bars for all recognized climbs, including `ClimbCategory.UNCATEGORIZED`.
  4. *Living Documentation Synchronization*:
  • Verify `docs/requirements.md` contains `REQ-UI-307` and `docs/tests.md` contains `TST-UI-267`.
  5. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew clean testDebugUnitTest` asserting 100% test pass rate across all test classes.

### TST-UI-268: **Route Map Layers Menu, Overlay Layer Visibility, Item-Level Breakdown Toggles, and Permanent Route Line Parity Verification** [Verified]
- **Requirement(s)**: `REQ-UI-308`
- **Component**: **Route Map Layers Menu, Overlay Layer Visibility, Item-Level Breakdown Toggles, and Permanent Route Line Parity Verification**
- **Jira**: ``ATT-2763``
- **Test Description**:
  1. *Route Map Layers Menu & Overlay Filtering Contract Tests (`RouteOnMapScreenLayersContractTest.kt`)*:
  • Verify `RouteOverlayLayer` declares `CLIMBS`, `SEGMENTS`, `WAYPOINTS`.
  • Verify default state enables all layers.
  • Verify disabling `CLIMBS` filters out all climb highlight polylines.
  • Verify disabling `SEGMENTS` filters out all matched segment polylines.
  • Verify disabling `WAYPOINTS` yields empty waypoints on route while preserving path points.
  • Verify selected route line is permanently anchored and unaffected by layer toggles.
  • Verify Start and End markers remain visible at all times.
  2. *Breakdown Section Visibility Toggle Contract Tests (`RouteBreakdownVisibilityToggleContractTest.kt`)*:
  • Verify `RouteClimbsBreakdownSection` accepts `hiddenClimbIds: Set<Long>` and `onToggleClimbVisibility` callback.
  • Verify `RouteSegmentsBreakdownSection` accepts `hiddenSegmentIds: Set<Long>` and `onToggleSegmentVisibility` callback.
  • Verify toggle icon button interactions trigger respective callbacks.
  3. *9-Language Localization Parity Audit (`RouteLayersLocalizationTest.kt`)*:
  • Verify all route layer string resources (`route_layers`, `route_layer_climbs`, `route_layer_segments`, `route_layer_waypoints`, `route_item_hide`, `route_item_show`) exist and are non-empty across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/` with matching format specifiers.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-269: **ANT+ Status Sheet Untinted Branding, BLE Hint Elimination, and Localization Parity Verification** [Verified]
- **Requirement(s)**: `REQ-UI-309`
- **Component**: **ANT+ Status Sheet Untinted Branding, BLE Hint Elimination, and Localization Parity Verification**
- **Jira**: ``ATT-2749``
- **Test Description**:
  1. *AntServicesStatusSheet Architectural & Contract Tests (`AntServicesStatusContractTest.kt`)*:
  • Verify `AntServicesStatusSheet.kt` passes `iconTint = Color.Unspecified` to `AppModalBottomSheet`.
  • Verify `AntServicesStatusSheet.kt` does not contain `ant_status_ble_alternative_note`.
  • Verify `AntServicesStatusSheet.kt` does not reference `logo_protocol_bluetooth`.
  2. *9-Language Localization Audit & Cleanup Verification (`AntStatusLocalizationTest.kt`)*:
  • Verify `ant_status_ble_alternative_note` is absent across all 9 `strings.xml` files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  • Verify all remaining active ANT status strings maintain 100% parity across all 9 locales.
  3. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-270: **Sport-Discipline Filtering in Quick Route Selector and Route List Sport Iconography Verification** [Verified]
- **Requirement(s)**: `REQ-UI-310`
- **Component**: **Sport-Discipline Filtering in Quick Route Selector and Route List Sport Iconography Verification**
- **Jira**: ``ATT-2668``
- **Test Description**:
  1. *RouteProximityRanker Unit Tests (`RouteProximityRankerTest.kt`)*:
  • Verify `filterAndRankRoutes` with `activeSport = BSportType.BIKE` returns bike and untagged routes, excluding run routes.
  • Verify `filterAndRankRoutes` with `activeSport = BSportType.RUN` returns run and untagged routes, excluding bike routes.
  • Verify `filterAndRankRoutes` with `activeSport = BSportType.UNKNOWN` or null returns all routes within proximity.
  2. *RouteSelectorViewModel Reactive Sport Filtering Unit Tests (`RouteSelectorViewModelTest.kt`)*:
  • Verify `setActiveSport(BSportType.BIKE)` updates `uiState.routes` to include only bike and untagged routes.
  • Verify `setActiveSport(BSportType.RUN)` updates `uiState.routes` to include only run and untagged routes.
  • Verify switching back to `UNKNOWN` restores all nearby candidate routes.
  • Verify in-ride auto-detection candidates match the active sport profile.
  3. *RouteCard Sport Iconography Contract Tests (`RouteSelectorSheetContractTest.kt`)*:
  • Verify `RouteCard` renders `Icon` displaying `route.summary.bSportType.iconResId` preceding route title.
  • Verify icon sizing and semantic tinting.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-271: **Removal of In-Ride Auto-Detected Route Banner and Sensor Grid Decoupling Verification** [Verified]
- **Requirement(s)**: `REQ-UI-311`
- **Component**: **Removal of In-Ride Auto-Detected Route Banner and Sensor Grid Decoupling Verification**
- **Jira**: ``ATT-2771``
- **Test Description**:
  1. *SensorGridScreen Route Integration Contract Test (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify `SensorGridScreen.kt` does NOT contain `AutoDetectedRouteBanner(`.
  • Verify `SensorGridScreen.kt` retains primary sensor grid column and scrolling.
  2. *ControlTracking Route Selection Contract Test (`ControlTrackingRouteSelectionContractTest.kt`)*:
  • Verify `SensorGridScreen.kt` does NOT host `AutoDetectedRouteBanner(`.
  3. *RouteSelectorSheet Component Contract Test (`RouteSelectorSheetTest.kt`)*:
  • Verify `RouteSelectorSheet.kt` does NOT define `fun AutoDetectedRouteBanner(`.
  • Verify `RouteSelectorModalBottomSheet`, `RouteSelectorContent`, `ActiveRouteBanner`, and `RouteCard` remain exposed.
  4. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify `route_auto_detect_title`, `route_auto_detect_prompt`, `route_auto_detect_activate`, and `route_auto_detect_dismiss` are removed across all 9 `strings.xml` files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  • Verify all remaining active string resources pass with 100% parity.
  5. *Clean-Room Full Suite Regression*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate with 0 regressions.

### TST-UI-272: **Root Fallback Drawables Density Audit, Drawer Header Resilience and Guarded Compose Loading Contract Verification** [Verified]
- **Requirement(s)**: `REQ-UI-312`
- **Component**: **Root Fallback Drawables Density Audit, Drawer Header Resilience and Guarded Compose Loading Contract Verification**
- **Jira**: ``ATT-2858`, `ATT-3164``
- **Test Description**:
  1. *Automated Density Fallback Audit Unit Test (`DrawableDensityFallbackAuditTest.kt`)*:
  • Scan all directories in `app/src/main/res` matching `drawable-*`.
  • Collect all unique image filenames (`.png`, `.jpg`, `.webp`, `.xml`).
  • Assert that every collected filename exists in root `app/src/main/res/drawable/`.
  2. *Safe Painter Resource Contract Test (`SafePainterResourceContractTest.kt`)*:
  • Verify `safePainterResource` loads valid drawables correctly.
  • Verify `safePainterResource` with non-existent or invalid resource ID returns provided fallback `ImageVector` without throwing `Resources$NotFoundException`.
  3. *SportTypeSelector Defensive Loading Contract Test (`SportTypeSelectorContractTest.kt`)*:
  • Assert that `SportItem` resolves all sport type drawables (`bsport_run`, `bsport_bike`, `bsport_other`) defensively with vector fallbacks.
  • Verify all `BSportType` drawables exist in root `app/src/main/res/drawable/`.
  4. *AppNavigationDrawer Defensive Loading & Density Bucket Audit Contract Test (`AppNavigationDrawerAssetResilienceTest.kt`)*:
  • Verify `DrawerHeader` invokes `safePainterResource` for `logo_512` with `Icons.Default.DirectionsRun` fallback.
  • Assert `painterResource(id = R.drawable.logo_512)` does NOT occur in `AppNavigationDrawer.kt`.
  • Verify `logo_512.png` exists in `res/drawable/`, `res/drawable-mdpi/`, `res/drawable-hdpi/`, `res/drawable-xhdpi/`, `res/drawable-xxhdpi/`, `res/drawable-xxxhdpi/`, and `res/drawable-nodpi/`.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-273: **Control Tracking Screen Sensor Header Centering & Accessibility Parity Verification** [Verified]
- **Requirement(s)**: `REQ-UI-313`
- **Component**: **Control Tracking Screen Sensor Header Centering & Accessibility Parity Verification**
- **Jira**: ``ATT-2791``
- **Test Description**:
  1. *Sensor Header Contract Tests (`ControlTrackingSensorHeaderContractTest.kt`)*:
  • Verify symmetrical balancing anchor exists when `devices.isNotEmpty()` and `showResearchButton`.
  • Verify trailing anchor declares `Modifier.clearAndSetSemantics { }` and `Modifier.alpha(0f)`.
  • Verify `RemoteDevices` receives `Modifier.weight(1f)` between balanced leading and trailing components.
  • Verify `SearchArea` remains in its dedicated slot above sensor rows.
  • Verify `ResearchButton` centers horizontally when `devices.isEmpty()`.
  • Verify multi-device Compose preview covers 6 devices without layout collision.
  2. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-274: **Navigation Drawer Decluttering and Advanced Tuning Sensor Search Section Contract Verification** [Verified]
- **Requirement(s)**: `REQ-UI-314`
- **Component**: **Navigation Drawer Decluttering and Advanced Tuning Sensor Search Section Contract Verification**
- **Jira**: ``ATT-2780``
- **Test Description**:
  1. *Navigation Drawer State Unit Tests (`NavigationDrawerStateTest.kt`)*:
  • Verify navigation drawer contains exactly 21 items across 5 categories.
  • Verify `drawer__settings` group contains 5 items (Units, Display, Tracking Layouts, Backup & Restore, Privacy Policy).
  • Verify `R.id.drawer_search_settings` is absent from all drawer categories.
  2. *Single Activity Navigation Unit Tests (`SingleActivityNavigationTest.kt`)*:
  • Verify `NavRoutes.fromDrawerItemId(R.id.drawer_search_settings)` is null.
  3. *Advanced Tuning Modularity & Structure Contract Tests (`AdvancedTuningModularityTest.kt`, `AdvancedTuningVisualContractTest.kt`)*:
  • Verify `categories/SensorSearchTuningSection.kt` exists and defines `@Composable fun SensorSearchTuningSection(...)`.
  • Verify all files in `ui.settings.tuning` remain strictly under 400 lines.
  • Verify `TuningSection.SENSOR_SEARCH` exists and is initially collapsed.
  • Verify `TuningSubtitleFormatter.formatSensorSearchSubtitle` generates correct summary.
  4. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify `tuning_cat_sensor_search` and related subtitle strings exist and have valid format tokens across all 9 supported application locales.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-275: **Harmonized Visual & UX Architecture for Climb, Segment, and Route Detail Bottom Sheets via MapDetailLayout Verification** [Verified]
- **Requirement(s)**: `REQ-UI-315`
- **Component**: **Harmonized Visual & UX Architecture for Climb, Segment, and Route Detail Bottom Sheets via MapDetailLayout Verification**
- **Jira**: ``ATT-2860``
- **Test Description**:
  1. *SegmentDetailSheet Map Popup Unification Contract Test (`SegmentDetailSheetContractTest.kt`)*:
  • Verify `SegmentDetailSheet` directly hosts `SegmentOnMapScreen` within a non-scrolling modal bottom sheet container.
  • Verify that header renders `SegmentHeader` and `SegmentDetails` in parity with map popup.
  • Verify obsolete 3-card composables (`SegmentDetailMetricsCard`, `SegmentDetailMapCard`, `SegmentDetailElevationProfileCard`) are eliminated.
  2. *RouteDetailSheet Route Map Screen Unification Contract Test (`RouteDetailSheetContractTest.kt`)*:
  • Verify `RouteDetailSheet` directly hosts `RouteOnMapScreen` within a modal container.
  • Verify obsolete 3-card composables (`RouteDetailMetricsCard`, `RouteDetailMapCard`, `RouteDetailElevationProfileCard`) are eliminated.
  3. *ClimbDetailSheet Layout Alignment Contract Test (`ClimbDetailSheetContractTest.kt`)*:
  • Verify `ClimbDetailSheet` aligns with `MapDetailLayout` and structured climb header.
  4. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify string resources across all detail sheets maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  5. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-276: **Dedicated Route Detail Bottom Sheet from Segment View Verification** [Verified]
- **Requirement(s)**: `REQ-UI-316`
- **Component**: **Dedicated Route Detail Bottom Sheet from Segment View Verification**
- **Jira**: ``ATT-2861``
- **Test Description**:
  1. *RouteDetailSheet Structural & Contract Tests (`RouteDetailSheetContractTest.kt`)*:
  • Verify `RouteDetailSheet` exists and accepts `RouteWithPath`, `onDismiss`, and `modifier`.
  • Verify `RouteDetailSheet` declares `AppModalBottomSheet`.
  • Verify Header contains route title, sport icon, and dedicated close button.
  • Verify Metrics card declares `RoundedCornerShape(16.dp)` and `surfaceContainer` styling.
  • Verify Map card declares `MapZoomFocus.EXPLICIT_BOUNDS` and embeds route polyline in `TTColor.RouteSelected`.
  • Verify Elevation profile card is rendered.
  2. *StarredSegmentsScreen Overlay Navigation Verification*:
  • Verify tapping a containing route in `SegmentRoutesSection` sets `inspectedRouteId` without unmounting `SegmentOnMapScreen`.
  • Verify `inspectedRouteId` renders `RouteDetailSheet` modal overlay.
  • Verify dismissing sheet clears `inspectedRouteId` and preserves segment map screen state.
  3. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify any newly introduced string keys exist across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with matching format specifiers.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-277: **Geodesic Polyline Orthogonal Projection & Interval Matching Verification** [Verified]
- **Requirement(s)**: `REQ-UI-317`
- **Component**: **Geodesic Polyline Orthogonal Projection & Interval Matching Verification**
- **Jira**: ``ATT-2862``
- **Test Description**:
  1. *Orthogonal Projection & Sparse Route Unit Tests (`RouteSegmentMatcherTest.kt`)*:
  • Verify segment starting/ending midway between sparse route vertices (e.g. 50 m spacing, where vertex distance > 25 m but cross-track distance < 10 m) is successfully matched.
  • Verify accurate `distanceAlongRouteMeters` and route span consistency derived from interpolated projection.
  2. *Out-and-Back & Circuit Route Traversal Tests*:
  • Verify an out-and-back route that traverses a segment in forward direction on outbound leg and reverse direction on return leg matches the forward interval without negative progression or span blowout.
  3. *Directional Bearing & Rejection Invariants*:
  • Verify reverse traversal segment is rejected.
  • Verify perpendicular crossing segment is rejected.
  • Verify diverging finish and horseshoe midpoint are rejected.
  • Verify conflicting sport types are rejected.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-278: **Route Map Segment Polyline Suppression & Background Path Layer Filtering Contract Verification** [Verified]
- **Requirement(s)**: `REQ-UI-318`
- **Component**: **Route Map Segment Polyline Suppression & Background Path Layer Filtering Contract Verification**
- **Jira**: ``ATT-2863``
- **Test Description**:
  1. *RouteOnMapScreen Segment Layer Filtering Contract Tests (`RouteOverlayLayersContractTest.kt`)*:
  • Verify that disabling `RouteOverlayLayer.SEGMENTS` filters out all `MapSegment` paths from `backgroundPaths` rendered via `contextualPaths`.
  • Verify that `hiddenSegmentIds` filters out matching `MapSegment` paths from `backgroundPaths`.
  • Verify that `matchedSegmentIds` prevents duplication of segments between `segments(...)` and `contextualPaths(...)`.
  • Verify that `hasSegments` includes `backgroundPaths.any { it is MapSegment }`.
  • Verify selected route line and Start/End markers remain permanently anchored.
  2. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-279: **Multi-Tier Route Map Polyline Layering: Solid Climb Overlay and Dashed Segment Overlay Synergy Verification** [Verified]
- **Requirement(s)**: `REQ-UI-319`
- **Component**: **Multi-Tier Route Map Polyline Layering: Solid Climb Overlay and Dashed Segment Overlay Synergy Verification**
- **Jira**: ``ATT-2864``
- **Test Description**:
  1. *MapVisualization Z-Index Hierarchy Contract (`RouteClimbSegmentLayeringContractTest.kt`)*:
  • Assert `ROUTE_BASE_Z_INDEX` (20.0f) < `ROUTE_OVERLAY_Z_INDEX` (22.0f).
  • Assert `ROUTE_OVERLAY_Z_INDEX` (22.0f) < `CLIMB_Z_INDEX` (28.0f).
  • Assert `ROUTE_ACTIVE_OVERLAY_Z_INDEX` (26.0f) < `CLIMB_Z_INDEX` (28.0f).
  • Assert `CLIMB_Z_INDEX` (28.0f) < `SEGMENT_Z_INDEX` (30.0f).
  • Assert `SEGMENT_DASH_LENGTH` is 20f and `SEGMENT_GAP_LENGTH` is 15f.
  • Assert `CLIMB_WIDTH` is 10f.
  2. *MapSegment Pattern & isDashed Contract (`RouteClimbSegmentLayeringContractTest.kt`)*:
  • Assert `MapSegment.isDashed` defaults to `false`.
  • Assert when `isDashed == false`, `pattern` is `null` (solid polyline).
  • Assert when `isDashed == true`, `pattern` contains `Dash(20f)` and `Gap(15f)`.
  3. *ClimbHighlightData Z-Index Contract (`RouteClimbSegmentLayeringContractTest.kt`)*:
  • Assert `MapContentScope.ClimbHighlightData` default `zIndex` equals `MapVisualization.CLIMB_Z_INDEX` (28f).
  • Assert `MapContentScope.climbs(...)` renders solid polylines with `JointType.ROUND`.
  4. *MappablePathLayer Transparent Gap Base Omission (`RouteClimbSegmentLayeringContractTest.kt`)*:
  • Assert `MappablePathLayer` sets `hasSolidBase = false` when `path is MapSegment && path.isDashed`.
  • Assert `XRayPolyline` skips solid base rendering when `hasSolidBase == false`.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-280: **Preservation of Authentic Sport Type Icon Colors in Quick Route Selector Verification** [Verified]
- **Requirement(s)**: `REQ-UI-320`
- **Component**: **Preservation of Authentic Sport Type Icon Colors in Quick Route Selector Verification**
- **Jira**: ``ATT-2865``
- **Test Description**:
  1. *RouteCard Sport Icon Color & Structural Contract Tests (`RouteSelectorSheetTest.kt`)*:
  • Verify `RouteCard` renders sport icon via `route.summary.bSportType.iconResId`.
  • Verify `RouteCard` specifies `tint = Color.Unspecified`, preserving multi-color vector asset colors without monochrome theme tinting.
  • Verify `RouteCard` does NOT contain `tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant`.
  • Verify `RouteCard` provides localized accessibility description via `stringResource(id = route.summary.bSportType.stringResId)`.
  • Verify `RouteCard` sizes sport icon at `24.dp`.
  • Verify active routes continue to display dedicated `"ACTIVE"` chip (`labelSmall`, `onPrimary` text over `primary` background).
  2. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-281: **Fork Decision Prompt Spatial Overlay & Tab Navigation Hints Gating Verification** [Verified]
- **Requirement(s)**: `REQ-UI-321`
- **Component**: **Fork Decision Prompt Spatial Overlay & Tab Navigation Hints Gating Verification**
- **Jira**: ``ATT-2874``
- **Test Description**:
  1. *Tab Navigation Hints Gating Contract Tests (`TrackingTabWysiwygContractTest.kt`)*:
  • Verify `ForkDecisionCard` in `SensorGridScreen.kt` is conditionally gated by `state.showNavigationHints`.
  2. *Spatial Overlay Container Contract Tests (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify `ForkDecisionCard` is anchored at `Alignment.TopCenter` within a non-displacing overlay container.
  • Verify `ForkDecisionCard` is NOT an in-flow child of the scrollable sensor grid `Column`.
  • Verify `ForkDecisionCard` consumes `tuningConfig.navigationCueTransparency` per Design Guidelines Section 5.7.
  3. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-282: **General Map Layer Visibility Controls & Preference Persistence Verification** [General map Layers menu toggles routes, segments, favorite locations, favorite tracks / clusters, and live track; user layer visibility preferences persisted across sessions in SharedPreferences; CLUSTERS layer disabled by default in DEFAULT_ENABLED; Cockpit map strictly isolates and never renders clusters; fast asynchronous cluster hydration with companion stats parity and off-Main polyline decoding; harmonized ClusterOnMapSheet MapDetailLayout, MinimumDragHandle, list component reuse, and stats enrichment without purple text tinting; deduplicated metadata and lower expanded detailed map parity; 100% 9-language localization parity; 100% unit test pass rate.] [Verified]
- **Requirement(s)**: `REQ-UI-322`
- **Component**: **General Map Layer Visibility Controls & Preference Persistence Verification**
- **Jira**: `ATT-2931`, `ATT-4847`, `ATT-5075`, `ATT-5074`, `ATT-5197`, `ATT-5196`, `ATT-5279`
- **Test Description**:
  1. *GeneralMapLayer Enum & Default State Contract (`GeneralMapLayersContractTest.kt`)*:
  • Verify `GeneralMapLayer` enum declares `ROUTES`, `SEGMENTS`, `KNOWN_LOCATIONS`, `TRACK`, and `CLUSTERS` (exactly 5 entries).
  • Verify `GeneralMapLayer.DEFAULT_ENABLED` contains `ROUTES`, `SEGMENTS`, `KNOWN_LOCATIONS`, and `TRACK` (4 entries), strictly excluding `CLUSTERS`.
  • Verify default state in `MapFragmentWithTrackViewModel` loads `DEFAULT_ENABLED` (favorite route clusters disabled by default) when `SharedPreferences` has no stored preference.
  2. *MapFragmentWithTrackViewModel Layer Toggling & Persistence Contract (`GeneralMapLayersContractTest.kt`)*:
  • Verify initial state loads from `SharedPreferences` or defaults to `DEFAULT_ENABLED`.
  • Verify `toggleLayer(layer)` removes layer if present, adds if absent.
  • Verify toggling updates `_enabledLayers` and persists serialized set to `SharedPreferences`.
  • Verify `MapFragmentWithTrackViewModel` conditionally gates `mapClustersFlow` on `GeneralMapLayer.CLUSTERS in enabledLayers`, emitting `emptyList()` immediately when disabled.
  • Verify `MapFragmentWithTrackViewModel` subscribes to `WorkoutClusterRepository.allClusters` and exposes memoized `clusters: List<MapCluster>` in `uiState` only when `CLUSTERS` is enabled.
  3. *MapScreenWithTrack UI Structure & Conditional Layer Delegation (`GeneralMapLayersContractTest.kt`)*:
  • Verify `MapScreenWithTrack.kt` includes `Icons.Default.Layers` button.
  • Verify floating surface at `Alignment.TopEnd` with `statusBarsPadding().padding(top = 16.dp, end = 16.dp)`.
  • Verify dropdown menu includes checkboxes and swatches for all 5 layers.
  • Verify conditional rendering of `knownLocations()`, `segments()`, `routes()`, `clusters()`, and `markers()` + `liveTrack()`.
  4. *Cockpit Map Cluster Excision & Decoupling Contract (`TrackingMapIsolationContractTest.kt`, `TrackingViewModelMapStateTest.kt`)*:
  • Verify `SensorGridScreen.kt` (`TrackingMapContainer`) never composes `clusters()` on `ATrainingTrackerMap`.
  • Verify `TrackingViewModel` does NOT depend on or observe `WorkoutClusterRepository` or `GeneralMapLayer.CLUSTERS`.
  • Verify `TrackingViewModel.observeMapState()` populates `TrackingMapState` without `WorkoutClusterRepository.allClusters`.
  • Verify live tracking cockpit renders strictly active track, selected routes, and live segments.
  5. *ClusterOnMapSheet Drag Handle, MapDetailLayout & Component Reuse Contract Tests (`GeneralMapLayersContractTest.kt`, `MapScreenWithTrackTest.kt`)*:
  • Verify `ClusterOnMapSheet` is hosted within `MapDetailLayout(useStatusBarsPadding = false, showMap = true)` with `MinimumDragHandle()` at the top.
  • Verify `ClusterOnMapSheet` header reuses canonical `WorkoutClusterIdentityRow` and `WorkoutClusterMetadataBlock` from `WorkoutClusterComponents.kt`.
  • Verify `ClusterOnMapSheet` does NOT include `WorkoutClusterDetailDashboard` in `metadataContent` to avoid duplicate statistics stacking (ATT-5279).
  • Verify `ClusterOnMapSheet` provides detailed cluster map composition in `mapContent` (`heatmap`, `path`, `markers`) with `MapZoomFocus.EXPLICIT_BOUNDS` (ATT-5279).
  • Verify `onHeaderHeightMeasured` dynamically updates `measuredClusterHeaderHeight` for bottom sheet peek calculation.
  • Verify expandable body displays detailed cluster map when dragged upward.
  • Verify `ClusterOnMapSheet` does NOT apply `tint = TTColor.Cluster` or hardcoded purple colors to text or icons.
  6. *MapCluster & MapFragmentWithTrackViewModel Stats Enrichment Contract Tests (`MapModelsTest.kt`, `MapFragmentWithTrackViewModelTest.kt`)*:
  • Verify `MapCluster` includes `stats: WorkoutClusterStats? = null`.
  • Verify `MapFragmentWithTrackViewModel` combines `workoutClusterRepository.clusterStats` into `mapClustersFlow`, populating `stats` on `MapCluster`.
  • Verify `WorkoutCluster.toMapCluster()` populates `stats` while preserving referential stability of decoded coordinates (`latLngs`).
  7. *Fast Asynchronous Hydration with Companion Stats Parity Contract Tests (`WorkoutClusterRepositoryTest.kt`, `MapFragmentWithTrackViewModelTest.kt`)*:
  • Verify `WorkoutClusterRepository.loadAllClustersLightweight()` seeds `_allClusters` from `RouteClusters` and, if `_clusterStats.value.isEmpty()`, automatically populates `_clusterStats.value = summariesManager.getWorkoutClusterStatsForAllClusters()`.
  • Verify opening Map first hydrates cluster stats without requiring a prior visit to the Lieblingsstrecken list screen.
  • Verify `mapClustersFlow` offloads polyline decoding to `Dispatchers.Default` (`flowOn(Dispatchers.Default)`).
  8. *9-Language Localization Parity Audit (`GeneralMapLayersLocalizationTest.kt`)*:
  • Verify string resources `map_layers`, `map_layer_routes`, `map_layer_segments`, `map_layer_locations`, `map_layer_track`, `map_layer_clusters` exist and are non-empty across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  9. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-283: **Material 3 Compose ANT Missing Adapter & Dependency Alerts Verification** [Verified]
- **Requirement(s)**: `REQ-UI-323`
- **Component**: **Material 3 Compose ANT Missing Adapter & Dependency Alerts Verification**
- **Jira**: ``ATT-2939``
- **Test Description**:
  1. *AntMissingAdapterDialog Contract Tests (`AntAlertDialogContractTest.kt`)*:
  • Verify renders Material 3 AlertDialog with untinted ANT+ logo (`R.drawable.ant_logo`).
  • Verify title matches `R.string.ant_missing_adapter_title`.
  • Verify message matches `R.string.ant_missing_adapter_message`.
  • Verify confirm button matches `R.string.ant_dialog_view_status` and invokes `onViewStatus()`.
  • Verify dismiss button matches `R.string.ant_dialog_dismiss` and invokes `onDismiss()`.
  2. *AntMissingDependencyDialog Contract Tests (`AntAlertDialogContractTest.kt`)*:
  • Verify renders Material 3 AlertDialog with untinted ANT+ logo (`R.drawable.ant_logo`).
  • Verify title matches `R.string.ant_missing_dependency_title`.
  • Verify message contains formatted dependency name.
  • Verify confirm button matches `R.string.go_to_store` and invokes `onGoToStore()`.
  • Verify dismiss button matches `R.string.cancel` and invokes `onDismiss()`.
  3. *MainActivityWithNavigation Elimination of Raw Dialogs (`AntAlertDialogContractTest.kt`)*:
  • Verify zero occurrences of `AlertDialog.Builder` within `showANTAdapterMissingDialog` and `showSpecificInstallANTDialog`.
  • Verify state dispatching to Compose `antDialogState` and `showAntStatusSheet`.
  4. *9-Language Localization Parity Audit (`AntDialogLocalizationTest.kt`)*:
  • Verify `ant_dialog_view_status` and `ant_dialog_dismiss` exist, are non-empty, and resolve cleanly across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  • Verify `values-de/strings.xml` contains "installiert" and zero occurrences of "intalliert".
  5. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-284: **Turn-by-Turn Navigation Prompts Spatial Overlay & Visual Harmonization Verification** [Verified]
- **Requirement(s)**: `REQ-UI-324`
- **Component**: **Turn-by-Turn Navigation Prompts Spatial Overlay & Visual Harmonization Verification**
- **Jira**: ``ATT-2941``
- **Test Description**:
  1. *SensorGridScreen Top-Level Spatial Overlay & Tab Gating Contract Tests (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify `TurnPromptBanner` is NOT rendered inside the scrollable in-flow sensor grid `Column`.
  • Verify `TurnPromptBanner` is hosted within the top-level spatial overlay `Column` at `Alignment.TopCenter`.
  • Verify `TurnPromptBanner` is conditionally gated by `state.showNavigationHints`.
  • Verify `TurnPromptBanner` binds `overlayAlpha` to `tuningConfig.navigationCueTransparency`.
  • Verify `TurnPromptBanner` binds `dismissDurationSec` to `tuningConfig.navigationCueDismissDurationSec`.
  2. *TurnPromptBanner Visual Styling & Design Guideline Conformance Tests (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify `TurnCueCard` and `OffRouteCard` apply `RoundedCornerShape(16.dp)`.
  • Verify `TurnCueCard` applies Royal Blue navigation border stroke `BorderStroke(1.dp, TTColor.RouteActiveNavigation.copy(alpha = 0.5f))`.
  • Verify `TurnCueCard` applies `surfaceContainer` (approaching) and `primaryContainer` (`isTurnNow`).
  • Verify inner content padding uses `padding(horizontal = 16.dp, vertical = 12.dp)`.
  3. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-285: **Single Active Route Card Presentation and Direct In-List Cancellation Verification** [Verified]
- **Requirement(s)**: `REQ-UI-325`
- **Component**: **Single Active Route Card Presentation and Direct In-List Cancellation Verification**
- **Jira**: ``ATT-2943``
- **Test Description**:
  1. *RouteSelectorContent Upper Banner Exclusion Contract Tests (`RouteSelectorSheetTest.kt`)*:
  • Verify `RouteSelectorContent` does NOT compose `ActiveRouteBanner` in its layout body.
  • Verify `ActiveRouteBanner` composable definition remains exposed in `RouteSelectorSheet.kt` for backward compatibility.
  2. *RouteCard In-List Cancellation & Interaction Tests (`RouteSelectorSheetTest.kt`)*:
  • Verify `RouteCard` renders `Icons.Default.Close` button with `@string/route_action_clear` when `isActive == true` and `onClearRoute != null`.
  • Verify clicking close button triggers `onClearRoute`.
  • Verify `RouteCard` preserves `secondaryContainer` and `"ACTIVE"` badge styling when active.
  3. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-286: **Tracking Map Recomposition Boundary and State Decoupling Verification** [Verified]
- **Requirement(s)**: `REQ-UI-326`
- **Component**: **Tracking Map Recomposition Boundary and State Decoupling Verification**
- **Jira**: ``ATT-2944``
- **Test Description**:
  1. *Sub-State Model & Compose Stability Tests (`TrackingMapIsolationContractTest.kt`)*:
  • Verify `TrackingMapState` is annotated with `@Immutable`.
  • Verify `TrackingMapState` encapsulates all 11 map-specific rendering properties.
  • Verify `TrackingScreenState` includes `val mapState: TrackingMapState` with backward-compatible defaults.
  2. *TrackingMapContainer Recomposition Barrier Contract Tests (`TrackingMapIsolationContractTest.kt`)*:
  • Verify `SensorGridScreen.kt` defines `fun TrackingMapContainer(`.
  • Verify `TrackingMapContainer` memoizes the `MapContentScope` lambda with `remember(`.
  • Verify `SensorGridScreen` delegates map rendering to `TrackingMapContainer` and no longer calls `ATrainingTrackerMap` directly with an un-remembered trailing lambda.
  3. *Telemetry Decoupling Unit Tests (`TrackingViewModelGridTest.kt`)*:
  • Verify that when `allFilteredSensorData` updates (e.g. Heart Rate changes), `uiState.value.mapState` maintains identity/equality with previous state without re-allocating new route or segment collections.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-287: **Tracking Tab Live Climb and Segment Cockpit BottomSheet Suppression & State Synchronization Verification** [Verified]
- **Requirement(s)**: `REQ-UI-327`
- **Component**: **Tracking Tab Live Climb and Segment Cockpit BottomSheet Suppression & State Synchronization Verification**
- **Jira**: ``ATT-2945``
- **Test Description**:
  1. *Sub-State & Active-Tab Gating Contract Tests (`SensorGridScreenClimbSuppressionTest.kt`)*:
  • Verify `SensorGridScreen` accepts parameter `isTabActive: Boolean = true`..
  • Verify `shouldShowBottomSheet` incorporates `isTabActive`, `(showLiveSegments

### TST-UI-288: **Live Climb Cockpit Sheet Visual Design Alignment & Layout Verification** [Verified]
- **Requirement(s)**: `REQ-UI-328`
- **Component**: **Live Climb Cockpit Sheet Visual Design Alignment & Layout Verification**
- **Jira**: ``ATT-2947``
- **Test Description**:
  1. *Top Header Structure & Icon Tests (`LiveClimbSheetLayoutTest.kt`)*:
  • Verify 32dp climb icon (`Icons.Default.Terrain`).
  • Verify climb title rendered in `MaterialTheme.typography.titleLarge` with bold weight.
  • Verify `ClimbCategoryChip` badge is right-aligned in the top row.
  2. *Subtitle Status & Route Counter Verification (`LiveClimbSheetLayoutTest.kt`)*:
  • Verify `ClimbStatusBadge` rendered in the subtitle row.
  • Verify route climb counter text formatted with `labelLarge` bold primary color.
  3. *Standardized Divider & Telemetry HUD Layout Tests (`LiveClimbSheetLayoutTest.kt`)*:
  • Verify `HorizontalDivider` with 0.5dp thickness and 16dp horizontal padding.
  • Verify remaining distance in `titleLarge` bold and remaining elevation in `bodyLarge` bold.
  • Verify instantaneous gradient in `headlineMedium` bold monospace font.
  4. *Elevation Profile Canvas Verification (`LiveClimbSheetLayoutTest.kt`)*:
  • Verify `ClimbProfileCanvas` rendering.
  5. *9-Language Localization Audit*:
  • Verify climb strings and tokens exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-UI-289: **StartOrResumeDialog Material 3 Compose Migration & Kill Diagnosis Verification** [Verified]
- **Requirement(s)**: `REQ-UI-329`
- **Component**: **StartOrResumeDialog Material 3 Compose Migration & Kill Diagnosis Verification**
- **Jira**: ``ATT-2948``
- **Test Description**:
  1. *Structural & Inheritance Contract Tests (`StartOrResumeDialogContractTest.kt`)*:
  • Verify `StartOrResumeDialog` inherits `DialogFragment`.
  • Verify `StartOrResumeDialog.TAG` equals class name.
  • Verify `attachInterface` enforces `StartOrResumeInterface`.
  2. *Composable Layout & Contextual State Tests (`StartOrResumeDialogLayoutTest.kt`)*:
  • Verify contextual icons and titles for `BATTERY_KILL` (`BatteryAlert`), `LOW_MEMORY` (`Memory`), `PERMISSION_REVOKED` (`Security`), and `GENERIC_UNFINISHED` (`DirectionsRun`).
  • Verify diagnostic callout container formatting, progressive battery escalation copy (stages 1, 2, 3), and Strava active note.
  • Verify button hierarchy: Resume as filled `Button`, Start new as `OutlinedButton`, and Disable Battery Optimization button visibility when `shouldShowBatteryButton == true`.
  • Verify button callbacks trigger `chooseStart()`, `chooseResume()`, and `openBatteryOptimizationSettings()`.
  3. *9-Language Localization Audit (`StartOrResumeLocalizationTest.kt`)*:
  • Verify all recovery dialog string resources exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-UI-290: **In-Ride Fork Decision Card Directional Grouping, Styling & Interaction Verification** [Verified]
- **Requirement(s)**: `REQ-UI-330`
- **Component**: **In-Ride Fork Decision Card Directional Grouping, Styling & Interaction Verification**
- **Jira**: ``ATT-2962``
- **Test Description**:
  1. *Directional Grouping Unit Tests (`ForkDecisionCardTest.kt`)*:
  • Verify candidate branches group by `ForkDirection` cleanly..
  2. *Composable Layout & Styling Contract Tests (`ForkDecisionCardTest.kt`)*:
  • Verify surface and border styling tokens conform to M3 guidelines..
  • Verify hint text `fork_select_hint` is omitted from candidate rows..
  3. *9-Language Localization Audit (`ForkDecisionCardTest.kt`)*:
  • Verify all fork alert string resources exist across EN, DE, ES, FR, IT, JA, NL, PL, PT..
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-UI-291: **Live Segment Elevation Profile Position Marker & Scrub Distance Verification** [Verified]
- **Requirement(s)**: `REQ-UI-331`
- **Component**: **Live Segment Elevation Profile Position Marker & Scrub Distance Verification**
- **Jira**: ``ATT-2967``
- **Test Description**:
  1. *Scrub Distance Calculation Contract Tests (`LiveSegmentSheetContractTest.kt`)*:
  • Verify `LiveSegmentSheet` evaluates `externalScrubDistance` across all `LiveSegmentStatus` states (`ON_SEGMENT`, `ON_SEGMENT_CLOSE_TO_FINISH`, `FINISHED`, `APPROACHING`, `FAR_FAR_AWAY`)..
  • Verify `LiveSegmentSheet` supplies `externalScrubDistance = externalScrubDistance` to `MapDetailLayout`..
  2. *Scrub Distance Synchronization in `MapDetailLayout.kt`*:
  • Verify `MapDetailLayout.kt` synchronizes `selectedDistance = externalScrubDistance` without null gating..
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-UI-292: **Workout Details Elevation Profile Display & Telemetry Ingestion Verification for Trackless Workouts** [Verified]
- **Requirement(s)**: `REQ-UI-332`
- **Component**: **Workout Details Elevation Profile Display & Telemetry Ingestion Verification for Trackless Workouts**
- **Jira**: ``ATT-3046``
- **Test Description**:
  1. *WorkoutRepository Telemetry Altitude Ingestion Tests (`WorkoutRepositoryTelemetryTest.kt`)*:
  • Verify `getWorkoutTelemetryPoints` extracts `SensorType.ALTITUDE` samples and sets `PathPoint.altitude` accurately.
  • Verify samples with only altitude (no HR/power/speed) are retained in `telemetryPath`.
  2. *TrackOnMapScreen Decoupled Visibility Contract Tests (`TracklessAftermathVisualContractTest.kt`, `TrackOnMapScreenDetailPreferencesContractTest.kt`)*:
  • Verify `TrackOnMapScreen.kt` evaluates `hasTracklessAltitude` and decouples `showElevationProfile` from `hasGpsTrack`.
  • Verify `showElevationProfile` evaluates to true for trackless workouts with altitude data.
  • Verify `showElevationProfile` remains conditioned on `activeDetailPrefs.showElevationProfile` and `isElevationPostMap`.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-UI-293: **Entity Detail Sheet Scaffold & Climb Popup Harmonization Verification** [Verified]
- **Requirement(s)**: `REQ-UI-333`
- **Component**: **Entity Detail Sheet Scaffold & Climb Popup Harmonization Verification**
- **Jira**: ``ATT-3053``
- **Test Description**:
  1. *EntityDetailSheetScaffold Architectural Contract Tests (`EntityDetailSheetScaffoldContractTest.kt`)*:
  • Verify `EntityDetailSheetScaffold` exists, is public, and configures `ModalBottomSheet` with `rememberModalBottomSheetState(skipPartiallyExpanded = true)`, `BottomSheetDesign.SheetShape`, `BottomSheetDesign.SheetTonalElevation`, and `dragHandle = null`.
  • Verify top-right dismiss close button (`IconButton` on `CircleShape` Surface with `Icons.Default.Close`).
  • Verify `SegmentDetailSheet.kt`, `RouteDetailSheet.kt`, and `ClimbDetailSheet.kt` all delegate to `EntityDetailSheetScaffold`.
  2. *ClimbDetailSheet & ClimbOnMapScreen Visual & Layout Contract Tests (`ClimbDetailSheetContractTest.kt`)*:
  • Verify `ClimbDetailSheet` delegates to `EntityDetailSheetScaffold` and `ClimbOnMapScreen`.
  • Verify `ClimbOnMapScreen` uses `MapDetailLayout` with `MapZoomFocus.EXPLICIT_BOUNDS` and `calculateClimbBounds`.
  • Verify `ClimbHeader` renders sport icon, climb name, category chip, and route counter badge.
  • Verify `ClimbDetails` renders distance, average grade, maximum grade, elevation gain, and min/max elevation with standard icons (`ic_distance`, `ic_grade`, `ic_ascent`, `ic_altitude`).
  • Verify `calculateClimbBounds` correctly computes bounds for multi-point and single-point climbs.
  3. *9-Language Localization Audit*:
  • Verify existing string resources (`climb_title`, `climb_route_counter`, `routes_climb_start_at`, `climb_remaining_dist`, `climb_remaining_elevation`, `routes_climb_avg_grade`, `routes_climb_max_grade`, `climb_max_grade_label`) exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-UI-294: **Newly Discovered Sensor Prioritization, Visual Highlight Badge & Discovery Feedback Notification Verification** [Verified]
- **Requirement(s)**: `REQ-UI-334`
- **Component**: **Newly Discovered Sensor Prioritization, Visual Highlight Badge & Discovery Feedback Notification Verification**
- **Jira**: `ATT-3099`, `ATT-5660`
- **Test Description**:
  1. *GetMergedDevicesUseCase Newly Discovered Flagging Unit Tests (`GetMergedDevicesUseCaseTest.kt`)*:
  • Verify `DeviceUiData` mapped for device IDs in `newlyFoundDevicesIds` sets `isNewlyDiscovered = true`.
  • Verify devices not in `newlyFoundDevicesIds` set `isNewlyDiscovered = false`.
  2. *DeviceListViewModel Scan Priority Sorting Unit Tests (`DeviceListViewModelPriorityTest.kt`)*:
  • Verify newly discovered devices sort at index 0 and 1 ahead of baseline devices in recency order.
  • Verify baseline devices sort stably and alphabetically by deviceName with id tie-breaker without re-sorting by isConnected.
  3. *DeviceItem Visual Badge & Border Contract Tests (`DeviceItemVisualHighlightTest.kt`)*:
  • Verify `DeviceItem` renders "NEW" badge chip (`devices_newly_discovered_badge`) when `isNewlyDiscovered == true`.
  • Verify `DeviceItem` applies `BorderStroke(1.5.dp, primary.copy(alpha = 0.6f))` when `isNewlyDiscovered == true`.
  4. *Discovery Feedback & Notification Event Flow Unit Tests (`DevicesTabbedViewModelDiscoveryTest.kt`)*:
  • Verify `DevicesTabbedViewModel` emits sensor name to `newDeviceDiscoveredEvent` when a new device is found.
  • Verify `DevicesTabbedScreen` observes event, shows snackbar (`devices_new_sensor_discovered`), triggers haptic vibration, and auto-scrolls to index 0.
  5. *9-Language Localization Audit (`DeviceDiscoveryLocalizationTest.kt`)*:
  • Verify string keys `devices_newly_discovered_badge` and `devices_new_sensor_discovered` exist and are non-empty across EN, DE, ES, FR, IT, JA, NL, PL, PT with valid `%1$s` specifiers.
  6. *Stable Sorting During Pairing / Connection State Changes (`DeviceListViewModelPriorityTest.kt`)*:
  • Verify that mutating `isConnected` does not alter list index positions in `DeviceListViewModel.getFilteredDevices` across pairing transitions (ATT-5660).
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-295: **Dynamic Map-Aware Navigation Hints Overlay Anchoring over Embedded Map View Verification** [Verified]
- **Requirement(s)**: `REQ-UI-335`
- **Component**: **Dynamic Map-Aware Navigation Hints Overlay Anchoring over Embedded Map View Verification**
- **Jira**: ``ATT-3100``
- **Test Description**:
  1. *Map-Present Layout Contract Tests (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify that when `state.mapState.showMap == true`, the navigation hints overlay is hosted inside the weighted map `Box` with `Alignment.TopCenter`, directly beneath the sensor grid.
  • Verify that the topmost sensor grid rows remain unobstructed and outside the navigation overlay container.
  2. *Map-Absent Layout Contract Tests (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify that when `state.mapState.showMap == false`, the navigation hints overlay is hosted at `Alignment.TopCenter` of the root tracking `Box`.
  3. *Component Encapsulation & Mutual Exclusion Contract Tests (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard` are unified in `NavigationHintsOverlay` and mutually exclusive between map and root branches.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-296: **Cockpit Sensor Field In-Ride Direct Editing via Long-Press Gesture Verification** [Verified]
- **Requirement(s)**: `REQ-UI-336`
- **Component**: **Cockpit Sensor Field In-Ride Direct Editing via Long-Press Gesture Verification**
- **Jira**: ``ATT-3101``
- **Test Description**:
  1. *SensorGridScreen Tracking Mode Wiring Contract Test (`SensorGridScreenContractTest.kt`)*:
  • Verify that in `SensorGridScreen.kt`, during tracking mode, `onEdit` on `SensorFieldView` delegates to `gridActions.onEditField(fieldState)`.
  • Verify that `onStartMove` and `onDelete` remain no-ops in tracking and preview modes.
  2. *SensorFieldView Gesture Contract Test (`SensorFieldViewGestureTest.kt`)*:
  • Verify that in `ScreenMode.TRACKING`, single tap does NOT invoke `onEdit` or `onStartMove`.
  • Verify that in `ScreenMode.TRACKING`, long-press invokes `onEdit()`.
  • Verify that in `ScreenMode.CONFIGURATION`, single tap invokes `onEdit()`, and long-press invokes `onStartMove()`.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-UI-297: **In-Ride Fork Decision Card Zero Tonal Elevation & Clean Surface Verification** [Verified]
- **Requirement(s)**: `REQ-UI-337`
- **Component**: **In-Ride Fork Decision Card Zero Tonal Elevation & Clean Surface Verification**
- **Jira**: ``ATT-3104``
- **Test Description**:
  1. *ForkDecisionCard Tonal Elevation & Surface Token Contract Tests (`ForkDecisionCardTest.kt`)*:
  • Verify `ForkDecisionCard.kt` specifies `tonalElevation = 0.dp` for the outer container and `tonalElevation = 0.dp` for `ForkDirectionGroup`.
  • Verify `ForkDecisionCard.kt` contains zero references to `surfaceVariant` for container backgrounds.
  • Verify `ForkDecisionCard.kt` uses `MaterialTheme.colorScheme.surface` with `overlayAlpha` for outer and inner surfaces.
  2. *ForkDirectionGroup Visual Hierarchy & Dividers Contract Tests (`ForkDecisionCardTest.kt`)*:
  • Verify `ForkDirectionGroup` preserves `VerticalDivider` and `HorizontalDivider` with `outlineVariant`.
  • Verify direction column and `ForkRouteRow` do not declare independent grey surface backgrounds.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-298: **Climb Detail Elevation Profile Local Distance Normalization & Scrubbing Synchronization Verification** [Verified]
- **Requirement(s)**: `REQ-UI-338`
- **Component**: **Climb Detail Elevation Profile Local Distance Normalization & Scrubbing Synchronization Verification**
- **Jira**: ``ATT-3105``
- **Test Description**:
  1. *Climb Path Normalization Logic Unit Tests (`ClimbNormalizationTest.kt`)*:
  • Verify `climb.normalizedPathPoints` on climb with route-relative distances (e.g. 27380.0m to 28102.0m) produces points starting at 0.0m and ending at 722.0m.
  • Verify all intermediate point distances are correctly shifted by subtracting `startDist`.
  • Verify latitude, longitude, altitude, and timestamps on each `PathPoint` remain identical.
  • Verify empty `pathPoints` returns empty list.
  • Verify single-point `pathPoints` returns point with distance 0.0.
  • Verify already normalized `pathPoints` (starting at 0.0) returns list unchanged.
  2. *ClimbOnMapScreen Architecture & Integration Contract Tests (`ClimbDetailSheetContractTest.kt`)*:
  • Verify `ClimbOnMapScreen.kt` passes `climb.normalizedPathPoints` (or remembered normalized path) to `MapDetailLayout` as `activeScrubPath`.
  • Verify `ClimbHeader.kt` preserves display of route offset ("Start at 27.4 km") via `climb.pathPoints.firstOrNull()?.distance`.
  3. *Scrubbing Point Lookup & Synchronization Tests (`ClimbNormalizationTest.kt`)*:
  • Verify `TelemetryMetricUtils.findNearestPoint` on normalized path with local distances (e.g. 361.0m) resolves to the midpoint coordinates and altitude.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-299: **Dynamic Cockpit Layout Weighting, Sensor Grid Scrollability & Elevation Profile Vertical Expansion Verification** [Verified]
- **Requirement(s)**: `REQ-UI-339`
- **Component**: **Dynamic Cockpit Layout Weighting, Sensor Grid Scrollability & Elevation Profile Vertical Expansion Verification**
- **Jira**: ``ATT-3107``
- **Test Description**:
  1. *SensorGridScreen Dynamic Weighting Structural Contract Tests (`SensorGridScreenLayoutTest.kt`)*:
  • Verify sensor grid `Column` specifies `Modifier.weight(1f, fill = false)`.
  • Verify `TrackingMapContainer` retains `Modifier.weight(1f)` when `showMap == true`.
  • Verify `ElevationProfile` container `Surface` applies `Modifier.weight(1f)` when `!state.mapState.showMap` and `state.showElevationProfile == true`.
  • Verify `ElevationProfile` receives `fillParentHeight = !state.mapState.showMap` and `modifier = if (!state.mapState.showMap) Modifier.fillMaxSize() else Modifier.fillMaxWidth()`.
  • Verify rendering is guarded by `state.pathPoints.size >= 2`.
  2. *ElevationProfile Decoupled Canvas Height Contract Tests (`ElevationProfileLayoutTest.kt`)*:
  • Verify `ElevationProfile` composables declare `fillParentHeight: Boolean = false` parameter default.
  • Verify that when `fillParentHeight == false`, `baseCanvasModifier` uses `Modifier.fillMaxWidth().height(totalCanvasHeight)`.
  • Verify that when `fillParentHeight == true`, `baseCanvasModifier` uses `Modifier.fillMaxWidth().defaultMinSize(minHeight = totalCanvasHeight).fillMaxHeight()`.
  3. *List Preview Callers Non-Regression Verification (`ElevationProfileLayoutTest.kt`)*:
  • Verify all list preview callers (`WorkoutSummary.kt`, `RouteItem.kt`, `SegmentItem.kt`) do not pass `fillParentHeight = true` and continue relying on default wrapped height.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-300: **LiveSegmentSheet Finish State Type-Safe Metric Resolution & NumberFormatException Elimination Verification** [Verified]
- **Requirement(s)**: `REQ-UI-340`
- **Component**: **LiveSegmentSheet Finish State Type-Safe Metric Resolution & NumberFormatException Elimination Verification**
- **Jira**: ``ATT-3108``
- **Test Description**:
  1. *LiveSegmentSheet Contract Tests (`LiveSegmentSheetContractTest.kt`)*:
  • Verify `LIveSegmentSheet.kt` maps `LiveSegmentStatus.FINISHED` to `liveSegment.staticData.summary.distance_raw.coerceAtLeast(liveSegment.liveData.distanceOnSegment_raw)` without calling `toDouble()`.
  2. *LiveSegmentSheet Finish Status Non-Crashing Unit Tests (`LiveSegmentSheetLayoutTest.kt`)*:
  • Instantiate `LiveSegment` with `LiveSegmentStatus.FINISHED`, `summary.distance = "766 m"`, and `distance_raw = 766.0`.
  • Verify evaluating `externalScrubDistance` does not throw `NumberFormatException` and equals `766.0`.
  • Verify handling when `summary.distance` has localized floating-point formatting (e.g. `"1,2 km"`).
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-301: **Live Climb Cockpit Sheet Visual Design Optimization & Multi-State Compose Previews Verification** [Verified]
- **Requirement(s)**: `REQ-UI-341`
- **Component**: **Live Climb Cockpit Sheet Visual Design Optimization & Multi-State Compose Previews Verification**
- **Jira**: ``ATT-3103``
- **Test Description**:
  1. *Preview Composable Contract Tests (`LiveClimbSheetLayoutTest.kt`)*:
  • Verify `LiveClimbSheet.kt` contains `@Preview` composables for `APPROACHING`, `ON_CLIMB`, and `FINISHED` states.
  • Verify Dark AMOLED preview declares `UI_MODE_NIGHT_YES` or AMOLED dark styling.
  2. *Graphical Canvas Contract Tests (`LiveClimbSheetLayoutTest.kt`)*:
  • Verify `ClimbProfileCanvas` renders the baseline ground line using `outlineVariant`.
  • Verify canvas height is at least `68.dp`.
  3. *Existing Token & Layout Invariants Preservation (`LiveClimbSheetLayoutTest.kt`)*:
  • Verify 32dp Terrain icon, bold titleLarge climb title, labelLarge route counter, headlineMedium monospace grade, bodyLarge remaining elevation, and 9-language localization parity remain 100% compliant.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-302: **Compose Text Input Stylus Handwriting Interception & Resilience Verification** [Verified]
- **Requirement(s)**: `REQ-UI-342`
- **Component**: **Compose Text Input Stylus Handwriting Interception & Resilience Verification**
- **Jira**: ``ATT-3163``
- **Test Description**:
  1. *Safe Probe & Flag Mutation Unit Tests (`ComposeHandwritingResilienceTest.kt`)*:
  • Verify `ComposeHandwritingResilience.disableComposeStylusHandwriting()` updates `StylusHandwriting_androidKt.isStylusHandwritingSupported` to `false`.
  • Verify that when `isStylusHandwritingSupported` is false, Compose reports `isStylusHandwritingSupported() == false`.
  2. *Idempotent & Fault-Tolerant Initialization Tests (`ComposeHandwritingResilienceTest.kt`)*:
  • Verify `ComposeHandwritingResilience.install()` executes idempotently without throwing unhandled exceptions.
  3. *Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 supported locales (0 new UI strings introduced).
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-303: **Tracking Tab Live Segment & Climb BottomSheet Default Fully Expanded Presentation Contract Verification** [Verified]
- **Requirement(s)**: `REQ-UI-343`
- **Component**: **Tracking Tab Live Segment & Climb BottomSheet Default Fully Expanded Presentation Contract Verification**
- **Jira**: ``ATT-3176`, `ATT-3319``
- **Test Description**:
  1. *Initial BottomSheet State Evaluation Contract Tests (`SensorGridScreenClimbSuppressionTest.kt`)*:
  • Verify `SensorGridScreen.kt` initializes `initialSheetValue` to `SheetValue.Expanded` when `shouldShowBottomSheet == true` (replacing `PartiallyExpanded`).
  • Verify `scaffoldState` initializes `bottomSheetState` with `initialValue = initialSheetValue`.
  2. *Entity-Aware Reactive Transition Contract Tests (`SensorGridScreenClimbSuppressionTest.kt`)*:
  • Verify `activeEntityKey` derives unique keys for active segment (`stravaId`) and active climb (`id`, `routeIndex`, `name`).
  • Verify `LaunchedEffect(shouldShowBottomSheet, activeEntityKey)` invokes `scaffoldState.bottomSheetState.expand()` unconditionally when `shouldShowBottomSheet` is `true`, even if previously in `PartiallyExpanded`.
  • Verify `LaunchedEffect` continues to invoke `scaffoldState.bottomSheetState.hide()` when `shouldShowBottomSheet` is `false`.
  3. *Preserved Dynamic Drag & Inactive Tab Suppression Contract Tests (`SensorGridScreenClimbSuppressionTest.kt`)*:
  • Verify `sheetSwipeEnabled` incorporates `isTabActive`, `screenMode == ScreenMode.TRACKING`, and `showLiveSegments

### TST-UI-304: **Default Tracking Views Feature Configuration Matrix Initial Database Seeding Contract Verification** [Verified]
- **Requirement(s)**: `REQ-UI-344`
- **Component**: **Default Tracking Views Feature Configuration Matrix Initial Database Seeding Contract Verification**
- **Jira**: ``ATT-3177``
- **Test Description**:
  1. *Clean Database Creation Seeding Unit Tests (`TrackingViewsDatabaseManagerTest.kt`)*:
  • Verify that when `TrackingViewsDbHelper.onCreate()` executes, the generated default tabs for each `ActivityType` match the canonical toggle matrix:
  - Standard Tab (`layoutNr = 1`): `showNavigationHints = 1`, `showLiveSegments = 1`, `showLiveClimbs = 1`, `showMap = 0`, `showElevationProfile = 0`, `showLapButton = 0`.
  - Map Tab (`layoutNr = 2`): `showNavigationHints = 1`, `showMap = 1`, `showElevationProfile = 1`, `showLiveSegments = 0`, `showLiveClimbs = 0`, `showLapButton = 0`.
  - Laps Tab (`layoutNr = 3`): `showLapButton = 1`, `showNavigationHints = 0`, `showLiveSegments = 0`, `showLiveClimbs = 0`, `showMap = 0`, `showElevationProfile = 0`.
  2. *API Parameter Passing Contract Tests (`TrackingViewsDatabaseManagerTest.kt`)*:
  • Verify `TrackingViewsDbHelper.addDefaultTab(...)` populates `SHOW_LIVE_CLIMBS` and `SHOW_NAVIGATION_HINTS` with the explicit boolean arguments provided rather than hardcoding `1`.
  3. *Living Design Guidelines Verification (`docs/design_guidelines.md`)*:
  • Verify Section 5.9 codifies the default tracking views configuration matrix.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-305: **Workout Recovery Dialog Layout Visibility, Start Interception Guard & Sticky Tracking State Verification** [Verified]
- **Requirement(s)**: `REQ-UI-345`
- **Component**: **Workout Recovery Dialog Layout Visibility, Start Interception Guard & Sticky Tracking State Verification**
- **Jira**: ``ATT-3165``
- **Test Description**:
  1. *Dialog Window Bounds Contract Verification (`StartOrResumeDialogLayoutTest.kt`)*:
  • Verify `StartOrResumeDialog.onStart()` configures `dialog?.window?.setLayout(MATCH_PARENT, WRAP_CONTENT)`.
  2. *Start Tracking Interception & Unfinished Session Guard Unit Tests (`ControlTrackingViewModelTest.kt`)*:
  • Verify `ControlTrackingViewModel.onStartTracking()` checks `hasUnfinishedWorkout()` and `isTracking()`.
  • When unfinalized workout exists and not tracking, verify `ShowRecoveryDialog` event is emitted and `REQUEST_START_TRACKING` broadcast is NOT dispatched.
  • When no unfinalized workout exists or tracking is already active, verify `REQUEST_START_TRACKING` broadcast is dispatched.
  3. *Database Query & Finalization Predicate Tests (`WorkoutSummariesDatabaseManagerTest.kt`)*:
  • Verify `hasUnfinishedWorkout()` evaluates to `true` for records with `finished IS NULL` and `finished = 0`.
  • Verify `hasUnfinishedWorkout()` is not masked by subsequent finished workouts.
  • Verify `discardOrFinishUnfinishedWorkout()` updates records with `finished IS NULL` and `finished = 0` to `finished = 1`.
  • Verify `TrackerService.createNewWorkout()` inserts `finished = 0`.
  4. *Sticky Service Tracking State Sync Contract Tests (`BANALServiceRepositoryTest.kt`)*:
  • Verify `TrainingApplication.setTracking()` invokes `notifyTrackingStateChanged()`.
  • Verify `BANALServiceRepository` initializes `_trackingMode` with current application tracking mode.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-306: **Tracking Screen Sole Elevation Profile Canonical Adaptive Height & Layout Weight Decoupling Verification** [Verified]
- **Requirement(s)**: `REQ-UI-346`
- **Component**: **Tracking Screen Sole Elevation Profile Canonical Adaptive Height & Layout Weight Decoupling Verification**
- **Jira**: ``ATT-3173``
- **Test Description**:
  1. *Elevation Profile Surface Constraint Contract Verification (`SensorGridScreenLayoutTest.kt`)*:
  • Verify `SensorGridScreen.kt` elevation profile `Surface` does NOT apply `Modifier.weight(1f)`.
  • Verify `Surface` declares `Modifier.fillMaxWidth()` and `padding(top = 8.dp)`.
  2. *Elevation Profile Parameter & Modifier Contract Verification (`SensorGridScreenLayoutTest.kt`)*:
  • Verify `ElevationProfile` does NOT pass `fillParentHeight = !state.mapState.showMap`.
  • Verify `ElevationProfile` receives `modifier = Modifier.fillMaxWidth()` (not `Modifier.fillMaxSize()`).
  3. *Sensor Grid Weight & Scrollability Preservation Tests (`SensorGridScreenLayoutTest.kt`)*:
  • Verify sensor grid `Column` retains `Modifier.weight(1f, fill = false)`.
  • Verify elevation profile rendering is guarded by `state.showElevationProfile && state.pathPoints.size >= 2`.
  4. *Adaptive Height Calculation Invariant Verification (`ElevationProfileLayoutTest.kt`)*:
  • Verify `calculateElevationProfileHeight(range)` produces values in $[70\text{dp}, 200\text{dp}]$.
  • Verify default `fillParentHeight = false` in `ElevationProfile` attaches `.height(totalCanvasHeight)`.
  5. *Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 supported locales (0 new UI strings introduced).
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-307: **RoutesScreen and RouteDetailSheet Route Preview Polyline Styling & Selection State Decoupling Verification** [Verified]
- **Requirement(s)**: `REQ-UI-347`
- **Component**: **RoutesScreen and RouteDetailSheet Route Preview Polyline Styling & Selection State Decoupling Verification**
- **Jira**: ``ATT-3172``
- **Test Description**:
  1. *RoutesScreen Dynamic Navigation State Contract Verification (`RoutesScreenContractTest.kt`)*:
  • Verify `RoutesScreen.kt` passes `isActiveNavigation = (selectedRoute.summary.id == activeNavigatedRouteId)` (does not hardcode `isActiveNavigation = true`).
  • Verify `RoutesViewModel` exposes `activeNavigatedRouteId: StateFlow<Long?>`.
  2. *RouteDetailSheet Decoupling Contract Verification (`RouteDetailSheetContractTest.kt`)*:
  • Verify `RouteDetailSheet.kt` does NOT hardcode `isActiveNavigation = true`.
  • Verify `RouteDetailSheet` defaults `isActiveNavigation = false` and propagates `routeWithPath.toMapRoute(isActiveNavigation)`.
  3. *Three-Tier Polyline Visual Resolution Unit Tests (`MapRouteActiveNavigationTest.kt`)*:
  • Verify that when `isActiveNavigation = false` and `isSelected = false`, `color` is `RouteUnselected`, `width` is `6f`, and pattern is dashed.
  • Verify that when `isActiveNavigation = false` and `isSelected = true`, `color` is `RouteSelected`, `width` is `10f`, and pattern is dashed.
  • Verify that when `isActiveNavigation = true`, `color` is `RouteActiveNavigation`, `width` is `26f`, and pattern is solid.
  4. *Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 supported locales (0 new UI strings introduced).
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-308: **Unified Top-of-Tab Navigation Hints Overlay Anchoring Contract & Reversion Verification** [Verified]
- **Requirement(s)**: `REQ-UI-348`
- **Component**: **Unified Top-of-Tab Navigation Hints Overlay Anchoring Contract & Reversion Verification**
- **Jira**: ``ATT-3170``
- **Test Description**:
  1. *Root Container Overlay Placement Contract Tests (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify `SensorGridScreen.kt` renders `NavigationHintsOverlay` anchored at `Alignment.TopCenter` of the root tracking `Box` container.
  • Verify root `NavigationHintsOverlay` is conditioned solely on `state.showNavigationHints` (independent of `state.mapState.showMap`).
  2. *Map Container Decoupling Contract Tests (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify the map container `Box` (`if (state.mapState.showMap)`) does NOT contain `NavigationHintsOverlay`.
  3. *Navigation Hints Encapsulation Verification (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify `NavigationHintsOverlay` encapsulates `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard`.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-309: **Active Sensor Deletion Disconnection Lifecycle and Defensive Telemetry Boundary Filtering Verification** [Verified]
- **Requirement(s)**: `REQ-UI-349`
- **Component**: **Active Sensor Deletion Disconnection Lifecycle and Defensive Telemetry Boundary Filtering Verification**
- **Jira**: ``ATT-3169``
- **Test Description**:
  1. *ViewModel Defensive Filtering Unit Tests (`ControlTrackingViewModelTest.kt`)*:
  • Verify `remoteDevices` drops active remote IDs absent from `allDevicesFromDb`.
  • Verify zero `DeviceType.DUMMY` or `"New Sensor"` items emitted when orphaned IDs are passed.
  • Verify legitimate devices in `allDevicesFromDb` continue to map correctly to `RemoteDeviceUIData`.
  2. *Repository Deletion Broadcast Unit Tests (`DeviceDataRepositoryTest.kt`)*:
  • Verify `deleteDevice(deviceId)` broadcasts `BANALService.PAIRING_CHANGED` with `paired = false` and matching `deviceId`.
  • Verify `devicesDatabaseManager.deleteDevice(deviceId)` is invoked.
  3. *Dialog Missing Snapshot Auto-Dismiss Tests (`EditDeviceDialogTest.kt` / `EditDeviceViewModelTest.kt`)*:
  • Verify `EditDeviceDialog` calls `onDismiss()` when snapshot resolves to `null`.
  4. *DeviceManager Null-Safety Verification (`DeviceManagerTest.kt`)*:
  • Verify `removeAndStopRemoteDevice` executes safely without throwing when device is not in `mMyRemoteDevices`.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-310: WorkoutSummariesTabbedScreenTest, WorkoutSummariesListFragmentTest [Verified]
- **Requirement(s)**: `REQ-UI-350`
- **Component**: `WorkoutSummariesTabbedScreenTest`, `WorkoutSummariesListFragmentTest`
- **Jira**: `**Post-Workout Low Battery Alert Snackbar Navigation Bar Inset Padding Verification.**`
- **Test Description**: Verify that `SnackbarHost` in `WorkoutSummariesTabbedScreen` (and `WorkoutSummariesListFragment`) applies `Modifier.navigationBarsPadding()` to ensure snackbars and action buttons render above system navigation bars with zero occlusion.

### TST-UI-311: **Multi-Dimensional Sensor Filtering by Protocol, Category, and Battery Level with Deep-Link Navigation Verification** [Verified]
- **Requirement(s)**: `REQ-UI-351`
- **Component**: **Multi-Dimensional Sensor Filtering by Protocol, Category, and Battery Level with Deep-Link Navigation Verification**
- **Jira**: ``ATT-3166``
- **Test Description**:
  1. *Filter Criteria Model Unit Tests (`DeviceFilterCriteriaTest.kt`)*:
  • Verify `matches` filters by protocol (`ANT_PLUS`, `BLUETOOTH_LE`).
  • Verify `matches` filters by category set (`Set<DeviceType>`).
  • Verify `matches` filters by battery level: `CRITICAL_ONLY` matches $0..20\%$, rejects $> 20\%$ and unknown ($-1$); `NORMAL` matches $> 20\%$.
  • Verify `activeFilterCount`, `isEmpty`, `isNotEmpty`.
  2. *ViewModel Reactive Filtering Unit Tests (`DeviceListViewModelFilterTest.kt`)*:
  • Verify `getFilteredDevices` dynamically filters `mergedDevices` across `CONNECTED`, `PAIRED`, and `ALL_KNOWN` tabs when criteria change.
  3. *UI & Filter Bottom Sheet Contract Tests (`DevicesTabbedScreenFilterTest.kt`)*:
  • Verify `FilterActionButton` renders in header and displays active badge count.
  • Verify `ActiveDeviceFilterChipsRow` displays removable chips and clears criteria.
  • Verify `DeviceFilterBottomSheet` renders protocol, category, and battery sections.
  4. *Deep-Link & Navigation Race Resolution Tests (`NotificationDeepLinkTest.kt`)*:
  • Verify `SensorBatteryNotificationManager` sets `EXTRA_FILTER_CRITICAL_BATTERY = true`.
  • Verify `MainActivityWithNavigation` queues `mPendingDrawerItemId` when `navController == null` and executes navigation to `drawer_my_sensors` upon `onNavControllerReady`.
  • Verify `BatteryFilter.CRITICAL_ONLY` is applied when launched with extra.
  5. *Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-312: **LiveClimbSheet Design System Harmonization, ElevationProfile Gradient Shading, Adaptive Height Scaling & Degenerate Geometry Verification** [Verified]
- **Requirement(s)**: `REQ-UI-352`
- **Component**: **LiveClimbSheet Design System Harmonization, ElevationProfile Gradient Shading, Adaptive Height Scaling & Degenerate Geometry Verification**
- **Jira**: ``ATT-3264`, `ATT-3316``
- **Test Description**:
  1. *ElevationProfile Gradient Shading & Degenerate Geometry Unit Tests (`ElevationProfileClimbShadingTest.kt`)*:
  • Verify `getClimbGradeColor` maps grade percentages to canonical color bands: $<3\%$ Green, $3-6\%$ Light Green, $6-9\%$ Amber, $9-12\%$ Orange, $12-20\%$ Red, $>20\%$ Dark.
  • Verify `calculateElevationBounds` enforces `range.coerceAtLeast(1.0)` on flat altitudes.
  • Verify `ElevationProfile` handles `pathPoints.isEmpty()` and `pathPoints.size == 1` gracefully without throwing `NoSuchElementException`.
  • Verify `fixedHeight` overrides `totalCanvasHeight` when supplied.
  2. *MetricBadge Shape Parameterization Unit Tests (`MetricBadgeShapeTest.kt`)*:
  • Verify default `shape` evaluates to `RoundedCornerShape(4.dp)`.
  • Verify custom shapes (e.g. `RoundedCornerShape(6.dp)` and `RoundedCornerShape(12.dp)`) compile and apply cleanly.
  3. *LiveClimbSheet Layout & Contract Tests (`LiveClimbSheetLayoutTest.kt`, `LiveClimbAdaptiveHeightTest.kt`)*:
  • Verify `LiveClimbSheet` invokes `ElevationProfile` with `climbGradientShading = true` and without hardcoded `fixedHeight` override.
  • Verify `ElevationProfile` calculates height adaptively from $70\text{dp}$ to $200\text{dp}$ based on climb elevation gain via `calculateElevationProfileHeight(range)`.
  • Verify `ClimbProfileCanvas` is completely excised from `LiveClimbSheet.kt`.
  • Verify `ClimbCategoryChip` and `ClimbStatusBadge` delegate to `MetricBadge`.
  • Verify multi-state Compose `@Preview` composables are present and render.
  4. *Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-313: **Persistent Missing ANT+ Adapter Dialog Suppression, DataStore Persistence, and Advanced Settings Warning Reset Verification** [Verified]
- **Requirement(s)**: `REQ-UI-353`
- **Component**: **Persistent Missing ANT+ Adapter Dialog Suppression, DataStore Persistence, and Advanced Settings Warning Reset Verification**
- **Jira**: `ATT-3220`, `ATT-3454`, `ATT-9065`
- **Test Description**:
  1. *DataStore Preference Serialization & Default Reset Unit Tests (`TuningPreferencesDataStoreRoundTripTest.kt`)*:
  • Verify `TuningConfig` initializes with `suppressAntMissingAdapterDialog = false` and `showAntMissingAdapterWarnings = true`.
  • Verify `setSuppressAntMissingAdapterDialog(true)` persists and emits `true` via `suppressAntMissingAdapterDialogFlow`.
  • Verify `setSuppressAntMissingAdapterDialog(false)` persists and emits `false`.
  • Verify `resetToDefaults()` restores `suppressAntMissingAdapterDialog = false`.
  2. *Broadcast Guard, Cold-Start Race & Compose Dismissal Unit/UI Tests (`AntMissingAdapterDialogSuppressionTest.kt`, `AntMissingAdapterDialogComposeTest.kt`)*:
  • Verify `showANTAdapterMissingDialog()` transitions `antDialogState` to `AntDialogState.MissingAdapter` when hydrated and unsuppressed.
  • Verify `showANTAdapterMissingDialog()` discards the broadcast and `antDialogState` remains `null` when suppressed.
  • Verify `antDialogState != null` prevents duplicate emissions.
  • Verify `createComposeRule()` dismissal of `AntAlertDialogsOverlay` exits composition immediately, synchronously sets `isAntMissingAdapterDialogSuppressed = true`, blocks back-to-back broadcasts, and still completes `setSuppressAntMissingAdapterDialog(true)` on a surviving outer scope.
  • Verify `onViewStatus` ("ANT+ Status anzeigen") opens `AntServicesStatusSheet` without mutating `suppressAntMissingAdapterDialog`.
  • Verify cold-start `ADAPTER_NOT_DETECTED` broadcasts before initial `suppressAntMissingAdapterDialogFlow` emission never open the dialog when persisted preference is `true` and flush cleanly when persisted `false`, plus Tuning re-enablement restores dialog visibility.
  3. *Advanced Settings Toggle Contract Tests (`AntMissingAdapterDialogSuppressionTest.kt`)*:
  • Verify `SensorSearchTuningSection` renders `TuningToggleItem` with `checked = showAntMissingAdapterWarnings` under search behavior (`prefs_search_behavior_title`) (ATT-3454).
  • Verify `SensorsGpsFilterSection` no longer declares `showAntMissingAdapterWarnings` or renders the toggle item (ATT-3454).
  • Verify toggling invokes `onShowAntMissingAdapterWarningsChange`.
  • Verify `AdvancedTuningDialog` and `AdvancedTuningScreen` load `showAntMissingAdapterWarnings` from `tuningConfigFlow` and write back inverted value to `suppressAntMissingAdapterDialog` on save.
  • Verify all files in `ui.settings.tuning` remain strictly < 400 lines of code (`AdvancedTuningModularityTest.kt`).
  4. *Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify `tuning_show_ant_missing_adapter_warnings_title` and `tuning_show_ant_missing_adapter_warnings_desc` exist and are non-empty across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-314: **Whole Integer Cycling Power Formatting, IntegerFormatter Mathematical Rounding, and Cadence Consolidation Verification** [Verified]
- **Requirement(s)**: `REQ-UI-354`
- **Component**: **Whole Integer Cycling Power Formatting, IntegerFormatter Mathematical Rounding, and Cadence Consolidation Verification**
- **Jira**: ``ATT-3229``
- **Test Description**:
  1. *IntegerFormatter Mathematical Rounding Unit Tests (`IntegerFormatterTest.kt`)*:
  • Verify whole numbers (`250.0`, `150L`, `85`) format as `"250"`, `"150"`, `"85"`.
  • Verify half-up mathematical rounding: `245.2` -> `"245"`, `245.49` -> `"245"`, `245.5` -> `"246"`, `245.8` -> `"246"`.
  • Verify Cadence fraction rounding: `89.1` -> `"89"`, `89.8` -> `"90"`.
  • Verify Heart Rate fraction rounding: `154.2` -> `"154"`, `154.7` -> `"155"`.
  • Verify zero handling: `0`, `0.0`, `-0.0` -> `"0"`.
  • Verify edge cases: `null` -> `"--"`, `Double.NaN` -> `"--"`, `Double.POSITIVE_INFINITY` -> `"--"`, `Double.NEGATIVE_INFINITY` -> `"--"`.
  2. *CadenceFormatter Rounding Unit Tests (`CadenceFormatterTest.kt`)*:
  • Verify delegation to `IntegerFormatter` with identical rounding and null handling.
  3. *SensorType Formatter Contract Unit Tests (`SensorTypeFormatterTest.kt`)*:
  • Verify `SensorType.POWER.getMyFormatter()` is an instance of `IntegerFormatter`.
  • Verify `SensorType.POWER.getMyFormatter().format(250.37)` returns `"250"`.
  • Verify `SensorType.CADENCE.getMyFormatter().format(89.8)` returns `"90"`.
  • Verify `SensorType.HR.getMyFormatter().format(154.6)` returns `"155"`.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-315: **Active Route Full Elevation Profile with Map-Matched Position Marker, Categorized Climbs & Per-Tab Rolling Window Verification** [Verified]
- **Requirement(s)**: `REQ-UI-355`
- **Component**: **Active Route Full Elevation Profile with Map-Matched Position Marker, Categorized Climbs & Per-Tab Rolling Window Verification**
- **Jira**: ``ATT-3199`, `ATT-3317``
- **Test Description**:
  1. *Route Elevation Profile Selection & Fallback Unit Tests (`TrackingElevationProfileResolutionTest.kt`)*:
  • Verify that when an active navigated route with altitude data is present, the resolved path points match the full route path (0 km to total distance) and climbs match the route climbs.
  • Verify that when no route is active (`isActiveNavigation == false` across all routes), resolved path points match `state.pathPoints` and climbs are empty.
  • Verify that when an active route has flat 0.0 altitude data across all points, resolved path points fall back to `state.pathPoints` and climbs are empty.
  • Verify that when an active route has fewer than 2 path points, resolved path points fall back to `state.pathPoints`.
  2. *Orthogonal Map-Matched Progress Projection Tests (`TrackingElevationProfileResolutionTest.kt`)*:
  • Verify that when `currentLocation` is at the route start coordinate, the resolved marker distance is approximately $0.0\text{m}$.
  • Verify that when `currentLocation` is midway along the route, the resolved marker distance matches the along-track distance of the projected segment.
  • Verify that when `currentLocation == null`, the resolved marker distance defaults to $0.0\text{m}$.
  • Verify that for free tracking (fallback mode), the marker distance is `state.pathPoints.lastOrNull()?.distance`.
  3. *Per-Tab Rolling Window & Lookahead Slicing Unit Tests (`TrackingElevationProfileResolutionTest.kt`)*:
  • Verify that when `scope == ElevationProfileScope.ROLLING_WINDOW` and lookahead is 2,000m, $D_{\text{behind}} = \frac{3}{17} \times 2000 \approx 352.9\text{m}$, centering the athlete marker at 15% from the left edge.
  • Verify rolling window extraction and interpolation across presets: 500m, 1,000m, 2,000m, 5,000m, 10,000m.
  • Verify start boundary clamping: when $alongTrackDist < D_{\text{behind}}$, window start is clamped to 0.0m.
  • Verify end boundary clamping: when $alongTrackDist + D_{\text{forward}} > D_{\text{route}}$, window end is clamped to $D_{\text{route}}$.
  • Verify categorized climbs intersecting the rolling window are retained, and non-intersecting climbs are excluded.
  4. *Database Schema Migration & Tab Persistence Unit Tests (`TrackingViewsDatabaseManagerTest.kt`)*:
  • Verify upgrade to DB version 13 adds `ElevationProfileScope` (default 0) and `ElevationProfileLookahead` (default 2000).
  • Verify per-tab persistence and independent retrieval across multiple tabs.
  5. *UI Contract, Toggle & Localization Audit Tests (`SensorGridScreenElevationProfileTest.kt`, `TranslationParityTest.kt`)*:
  • Verify `ElevationProfile` renders with non-zero start distance in rolling window mode.
  • Verify scope switch and lookahead chips in configuration mode trigger callbacks.
  • Verify 100% localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-316: **Workout Start & In-Ride Low Sensor Battery Alert Snackbars with Navigation Bar Inset Compliance & Semantic Severity Color Tiering Verification** [Verified]
- **Requirement(s)**: `REQ-UI-356`
- **Component**: **Workout Start & In-Ride Low Sensor Battery Alert Snackbars with Navigation Bar Inset Compliance & Semantic Severity Color Tiering Verification**
- **Jira**: ``ATT-3313`, `ATT-3451``
- **Test Description**:
  1. *Workout Start & In-Ride Severity Classification (`SensorBatteryAlertTest.kt`)*:
  • Verify that when starting a workout or in-ride, sensors with battery $\le 10\%$ are assigned `BatteryAlertSeverity.CRITICAL`.
  • Verify that sensors with battery in $11\dots 20\%$ are assigned `BatteryAlertSeverity.WARNING`.
  • Verify that when all sensors have battery $> 20\%$, zero alerts are dispatched.
  2. *In-Ride Battery Drop & Critical Escalation (`SensorBatteryAlertTest.kt`)*:
  • Verify that when an active session sensor drops to $\le 20\%$ during an active ride, `WorkoutNavigationEvents.lowBatteryAlert` is triggered with `WARNING`.
  • Verify that subsequent reports at $18\%$ or $15\%$ for the same sensor are suppressed by session deduplication.
  • Verify that when the sensor subsequently drops to $\le 10\%$, an escalated `CRITICAL` alert is triggered.
  • Verify duplicate reports at $\le 10\%$ are suppressed.
  3. *Snackbar Preemption & Typed Visuals (`SensorBatteryAlertTest.kt`)*:
  • Verify that when a `CRITICAL` alert arrives while a snackbar is active, `currentSnackbarData?.dismiss()` is executed immediately.
  • Verify `BatterySnackbarVisuals` carries `severity` and satisfies `SnackbarVisuals` interface.
  4. *Semantic Warning Colors & WCAG Contrast (`SensorBatteryAlertColorsTest.kt`)*:
  • Verify `LightWarningContainer`, `LightOnWarningContainer`, `LightWarningAction`, `DarkWarningContainer`, `DarkOnWarningContainer`, and `DarkWarningAction`.
  • Assert WCAG contrast ratios $\ge 4.5:1$ (body/action) and $\ge 7:1$ (container text) across both Light and Dark modes.
  5. *Tracking & Aftermath Screen SnackbarHost Dynamic Styling Contract Tests (`TrackingTabsScreenLayoutTest.kt`)*:
  • Verify `TrackingTabsScreen.kt` and `WorkoutSummariesTabbedScreen.kt` dynamically resolve container and content colors via `getBatterySnackbarColors(visuals.severity)` rather than static `primaryContainer`.
  • Verify fallback to standard Material 3 colors for non-battery snackbars.
  • Verify `SnackbarHost` applies `Modifier.navigationBarsPadding()` and `LapButton` clearance.
  • Verify `WorkoutNavigationEvents.consumeLowBatteryAlert()` is called upon alert handling.
  6. *Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for all sensor battery alert strings.
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-317: **Dedicated Full-Screen Advanced Settings Screen Architecture, Zero-Dismissal Scrolling, and Lifecycle Category Reorganization Verification** [Verified]
- **Requirement(s)**: `REQ-UI-357`
- **Component**: **Dedicated Full-Screen Advanced Settings Screen Architecture, Zero-Dismissal Scrolling, and Lifecycle Category Reorganization Verification**
- **Jira**: ``ATT-3344``
- **Test Description**:
  1. *Dedicated Full-Screen Screen Architecture Contract Tests (`AdvancedTuningScreenTest.kt`)*:
  • Verify `AdvancedTuningScreen` is hosted within a Material 3 `Scaffold`.
  • Verify `TopAppBar` renders back navigation arrow calling `onNavigateBack()`, localized title `R.string.advanced_tuning_title`, and reset action button.
  • Verify sticky bottom bar hosts Save (`R.string.save`) and Cancel buttons (`AppDialogActions.SaveCancel`).
  • Verify tapping Back or invoking system back pops backstack without saving uncommitted changes.
  • Verify tapping Save atomically persists modifications and pops backstack.
  2. *Zero-Dismissal Full-Height Scroll Verification (`AdvancedTuningScreenTest.kt`)*:
  • Verify content uses full-height vertical scroll (`Modifier.fillMaxSize().verticalScroll(rememberScrollState())`).
  • Verify `AdvancedTuningScreen` is NOT hosted inside `ModalBottomSheet` or `BottomSheetScaffold`, guaranteeing zero accidental drag dismissals.
  3. *Navigation & Route Contract Tests (`AdvancedTuningNavigationTest.kt`)*:
  • Verify `NavRoutes.ADVANCED_SETTINGS == "advanced_settings"`.
  • Verify `NavRoutes.fromDrawerItemId(R.id.drawer_advanced_tuning) == NavRoutes.ADVANCED_SETTINGS`.
  • Verify `NavRoutes.toDrawerItemId(NavRoutes.ADVANCED_SETTINGS) == R.id.drawer_advanced_tuning`.
  • Verify `NavRoutes.toBottomSheetType(R.id.drawer_advanced_tuning) == null`.
  • Verify `ATrainingTrackerApp.kt` registers `composable(NavRoutes.ADVANCED_SETTINGS)` in `NavHost`.
  • Verify `DisplaySettingsDialog.kt` tuning link navigates to `NavRoutes.ADVANCED_SETTINGS`.
  4. *Lifecycle Category Reorganization Contract Tests (`AdvancedTuningAccordionTest.kt`)*:
  • Verify `TuningSection` enum sequence: `NAVIGATION`, `COCKPIT_TYPOGRAPHY`, `BATTERY_SAVER`, `SENSORS_GPS`, `SENSOR_SEARCH`, `WORKOUT_MASKS_CARDS`, `AFTERMATH_ANALYSIS`.
  • Verify `AdvancedTuningAccordion` renders sections in this exact canonical sequence.
  5. *Preservation of Modularity & Invariants (`AdvancedTuningModularityTest.kt`)*:
  • Verify every file in `ui.settings.tuning` remains strictly < 400 lines of code.
  • Verify all 25+ parameter DataStore keys, default values, and clamping bounds remain identical.
  6. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify all tuning string resources exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-318: **Dedicated Workout Route Clustering Section in Advanced Settings, Single Source of Truth, and Modularity Preservation Verification** [Verified]
- **Requirement(s)**: `REQ-SET-075`
- **Component**: **Dedicated Workout Route Clustering Section in Advanced Settings, Single Source of Truth, and Modularity Preservation Verification**
- **Jira**: ``ATT-3355``
- **Test Description**:
  1. *Route Clustering Section & Controls Verification (`ClusterTuningSectionTest.kt`)*:
  • Verify that `ClusterTuningSection` renders master sensitivity slider and controls overall tolerances.
  • Verify details switch reveals individual sliders: Endpoint Tolerance (10..500m), Apex Tolerance (10..500m), Distance Tolerance (0.01..0.20), Altitude Position Tolerance (10..500m).
  • Verify switches: "Sportart berücksichtigen" (`useSportTypeForClustering`) and "Höhen-Extrema berücksichtigen" (`useAltitudePosForClustering`).
  • Verify unit formatting adapts to Metric (m, %) and Imperial (mi, %).
  2. *Lifecycle Category Ordering & Subtitle Formatter (`AdvancedTuningAccordionTest.kt`)*:
  • Verify `ROUTE_CLUSTERING` is positioned in Phase C between `WORKOUT_MASKS_CARDS` and `AFTERMATH_ANALYSIS`.
  • Verify `TuningSubtitleFormatter.formatClusterTuningSubtitle(...)` summarizes active tolerances concisely.
  3. *DataStore Persistence & Factory Reset (`TuningPreferencesDataStoreTest.kt`)*:
  • Verify `TuningConfig` initializes with canonical defaults (200m, 400m, 0.20, 400m, true, true).
  • Verify modifications persist to DataStore and SharedPreferences.
  • Verify "Auf Standardwerte zurücksetzen" restores all 6 values to factory defaults.
  4. *Single Source of Truth & DRY Call-Site Contracts (`ClusterTuningContractTest.kt`)*:
  • Verify `ClusterTuningScreen.kt` and `ImportBackupTabsScreen.kt` delegate to `ClusterTuningContent.kt` with zero UI code duplication.
  • Verify contextual recalculation button remains functional in `ClusterTuningScreen.kt`.
  5. *Modularity & File Size Limit Enforcement (`AdvancedTuningModularityTest.kt`)*:
  • Verify every file in `ui/settings/tuning/` strictly obeys `< 400` lines of code.
  6. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% translation coverage for `tuning_cat_route_clustering` and associated tokens across all 9 supported locales.
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-319: **Dynamic Cockpit Viewport Allocation, Asymmetric Map Weight Expansion & Bounded Sensor Grid Containment Verification** [Verified]
- **Requirement(s)**: `REQ-UI-358`
- **Component**: **Dynamic Cockpit Viewport Allocation, Asymmetric Map Weight Expansion & Bounded Sensor Grid Containment Verification**
- **Jira**: ``ATT-3343``
- **Test Description**:
  1. *Compose Container & AST Layout Structure Tests (`SensorGridScreenLayoutTest.kt`)*:
  • Verify `SensorGridScreen.kt` declares `BoxWithConstraints` wrapping the cockpit layout.
  • Verify that when `state.mapState.showMap == true`, the sensor grid Column applies `heightIn(max = ... * 0.5f)` and omits `weight(...)`.
  • Verify that when `state.mapState.showMap == false`, the sensor grid Column declares `weight(1f, fill = false)`.
  • Verify that the map Box declares `Modifier.fillMaxWidth().weight(1f, fill = true)`.
  2. *Geometry & Layout Constraint Unit Verification (`SensorGridScreenLayoutTest.kt`)*:
  • Verify available viewport height calculation and allocation under simulated constraints.
  • Verify 100% consumption of remaining vertical space between sensor grid and elevation profile with zero dead white space.
  • Verify sensor grid scrolling containment when row count is large (e.g. 5+ rows) with map enabled.
  • Verify adaptive elevation profile height preservation ($70\text{dp} \dots 200\text{dp}$) and map expansion when profile is disabled.
  3. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-320: **Live Segment and Climb Bottom Sheet Gesture Re-Expansion, Peek Tap-to-Expand, and Inactive Touch Neutrality Verification** [Verified]
- **Requirement(s)**: `REQ-UI-359`
- **Component**: **Live Segment and Climb Bottom Sheet Gesture Re-Expansion, Peek Tap-to-Expand, and Inactive Touch Neutrality Verification**
- **Jira**: ``ATT-3341``
- **Test Description**:
  1. *Architectural & AST Layout Structure Tests (`SensorGridBottomSheetGestureTest.kt`)*:
  • Verify `SensorGridScreen.kt` instantiates `rememberCoroutineScope` for sheet expansion control.
  • Verify bottom-edge gesture target is conditionally composed when `shouldShowBottomSheet == true` and `scaffoldState.bottomSheetState.currentValue != SheetValue.Expanded`.
  • Verify upward drag gesture (`detectVerticalDragGestures`) triggers `scaffoldState.bottomSheetState.expand()`.
  • Verify tap-to-expand handler on peek area triggers `scaffoldState.bottomSheetState.expand()`.
  2. *Touch Neutrality & Invariant Verification (`SensorGridBottomSheetGestureTest.kt`)*:
  • Verify gesture interceptor is completely suppressed / not composed when `shouldShowBottomSheet == false`.
  • Verify `initialSheetValue` evaluates to `SheetValue.Expanded` when `shouldShowBottomSheet == true`.
  • Verify `LaunchedEffect(shouldShowBottomSheet, activeEntityKey)` preserves unconditional initial expansion.
  3. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (0 new strings introduced).
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-321: **Favorite Routes Cluster Tuning Elimination, Advanced Settings Recalculate Action & Material 3 Confirmation Guard Verification** [Verified]
- **Requirement(s)**: `REQ-SET-075`
- **Component**: **Favorite Routes Cluster Tuning Elimination, Advanced Settings Recalculate Action & Material 3 Confirmation Guard Verification**
- **Jira**: ``ATT-3452``
- **Test Description**:
  1. *Favorite Routes Header & Screen Cleanup Contract Tests (`ClusterTuningContractTest.kt`)*:
  • Verify that `WorkoutClustersTabsScreen.kt` does NOT declare `onTuneClick` parameter or render a tuning settings icon in the top app bar.
  • Verify that `WorkoutClustersScreen.kt` does NOT declare `isTuning` state or compose `ClusterTuningScreen`.
  2. *Advanced Settings Recalculate Action & Material 3 Confirmation Guard (`ClusterTuningContractTest.kt`)*:
  • Verify that `ClusterTuningSection.kt` renders a dedicated action button referencing `@string/cluster_tuning_recalculate_button`.
  • Verify that tapping the button triggers a modal `AlertDialog` referencing warning message `@string/cluster_tuning_warning`.
  • Verify that the dialog specifies `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = 0.dp`.
  • Verify that the confirm button is styled with `MaterialTheme.colorScheme.error` and dismiss button references `@string/cancel`.
  3. *Lifecycle Decoupling, Concurrency & Double-Tap Prevention (`ClusterTuningContractTest.kt`)*:
  • Verify that `ClusterTuningSection.kt` collects `migrationStatus` from `WorkoutClusterRepository`.
  • Verify that when `migrationStatus != null`, the recalculate button is disabled and displays `CircularProgressIndicator` alongside `@string/cluster_tuning_recalculating`.
  • Verify that tolerance sliders and switches in `ClusterTuningContent` are disabled when `migrationStatus != null`.
  4. *Modularity & File Size Limit Enforcement (`AdvancedTuningModularityTest.kt`)*:
  • Verify that all source files in `ui.settings.tuning` remain strictly `< 400` lines of code.
  5. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% translation coverage for `cluster_tuning_recalculate_button`, `cluster_tuning_warning`, `cluster_tuning_recalculating`, and `cancel` across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-322: **Prevent Clipping of Climb Category Marker Badges in Elevation Profile Canvas Verification** [Verified]
- **Requirement(s)**: `REQ-UI-274`
- **Component**: **Prevent Clipping of Climb Category Marker Badges in Elevation Profile Canvas Verification**
- **Jira**: ``ATT-3453``
- **Test Description**:
  1. *Dynamic Font-Scale Aware Top Clearance Verification (`ElevationProfileClimbBadgeClippingTest.kt`)*:
  • Verify that when `climbs.isNotEmpty()` and `!isTimeDomain`, `climbBadgeTopPadding` evaluates dynamically to `(requiredClimbTopClearance - baseTopPadding).coerceAtLeast(0.dp)`.
  • Verify that when `climbs.isEmpty()`, `climbBadgeTopPadding` evaluates to `0.dp`, preserving compact list preview geometry.
  • Verify that when `showZoomControls == true` (detail view), `baseTopPadding = 44.dp` satisfies clearance, yielding `climbBadgeTopPadding == 0.dp`.
  2. *Container Height Stability & Proportional Rescaling (`ElevationProfileClimbBadgeClippingTest.kt`)*:
  • Verify that `totalCanvasHeight` is unchanged (`fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 28.dp else cachedData.adaptiveHeight`), preventing card jumping.
  • Verify that when `fixedHeight != null` (e.g. 120 dp), container height remains constant while chart plotting area rescales without container overflow.
  3. *Accessibility Font Scaling & Boundary Ceiling Safeguards (`ElevationProfileClimbBadgeClippingTest.kt`)*:
  • Verify simulated font scales 1.0x (24 dp), 1.5x (29 dp), and 2.0x (34 dp) expand clearance dynamically.
  • Verify `minAllowedPillTop = -effectiveTopPadding.toPx() + 2.dp.toPx()` prevents negative box coordinates ($y_{\text{box}} \ge 2\text{dp}$) even at peak elevation ($Y = 0$).
  • Verify horizontal safety margins (`minMarginPx = 2.dp.toPx()`) at route start and finish.
  4. *Preservation of Layout and Climb Contract Invariants (`ElevationProfileLayoutTest.kt`, `ElevationProfileClimbSpanContractTest.kt`)*:
  • Verify literal preservation of `val topPadding = if (showZoomControls) 44.dp else 16.dp` and `canvas.nativeCanvas.drawLine(summitX, summitY, summitX, pillBottom, stemPaint)`.
  5. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (0 new strings introduced).
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-323: **Turn-by-Turn Navigation Slight Turn Vector Icon Harmonization and Directional Symbol Compose Preview Gallery Verification** [Verified]
- **Requirement(s)**: `REQ-UI-360`
- **Component**: **Turn-by-Turn Navigation Slight Turn Vector Icon Harmonization and Directional Symbol Compose Preview Gallery Verification**
- **Jira**: ``ATT-3455``
- **Test Description**:
  1. *Vector XML Structure, Viewport & Baseline Grounding Contract Tests (`TurnDirectionIconContractTest.kt`)*:
  • Verify that all 8 `TurnDirection` enum entries reference valid XML vector drawables in `res/drawable/`.
  • Verify that all 8 vector drawables declare `android:viewportWidth="24"` and `android:viewportHeight="24"`.
  • Verify that `ic_turn_slight_right.xml` contains canonical path starting at baseline $y=20$ and ending with closing `z`.
  • Verify that `ic_turn_slight_left.xml` contains canonical path starting at baseline $y=20$ and ending with closing `z`.
  2. *Symmetrical Path Mirroring Unit Tests (`TurnDirectionIconContractTest.kt`)*:
  • Verify that the X coordinates and delta vectors in `ic_turn_slight_left.xml` are the exact mathematical horizontal reflection ($x' = 24 - x$) of `ic_turn_slight_right.xml`.
  3. *Compose Preview Gallery Contract Tests (`TurnDirectionIconContractTest.kt`)*:
  • Verify that `TurnPromptBanner.kt` declares `@Preview` composables `TurnCueIconsGalleryLightPreview` and `TurnCueIconsGalleryDarkPreview`.
  • Verify that `TurnCueIconsGallery` renders all 8 directions with non-null icons and string resources.
  4. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for all 8 `turn_cue_*` strings.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-324: **Turn-by-Turn Navigation Sharp Turn Vector Icon Harmonization and Directional Contract Verification** [Verified]
- **Requirement(s)**: `REQ-UI-361`
- **Component**: **Turn-by-Turn Navigation Sharp Turn Vector Icon Harmonization and Directional Contract Verification**
- **Jira**: ``ATT-3493`, `ATT-3573``
- **Test Description**:
  1. *Vector XML Structure, Viewport & Safety Header Contract Tests (`TurnDirectionIconContractTest.kt`)*:
  • Verify that `ic_turn_sharp_right.xml` and `ic_turn_sharp_left.xml` exist and declare valid 24x24dp viewport (`viewportWidth="24"`, `viewportHeight="24"`).
  • Verify that both vector drawables declare safety header comment `<!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-324 -->`.
  • Verify that both vector drawables declare `android:fillColor="#FF1976D2"`.
  2. *Baseline Grounding & Stem Width Geometry Tests (`TurnDirectionIconContractTest.kt`)*:
  • Verify that `ic_turn_sharp_right.xml` starts at baseline $y=20$ (`M6,20h2`) and closes at $y=20$ (`V20z`).
  • Verify that `ic_turn_sharp_left.xml` starts at baseline $y=20$ (`M18,20h-2`) and closes at $y=20$ (`V20z`).
  • Verify that baseline stem width is exactly 2dp (`h2` for right, `h-2` for left).
  3. *Symmetrical Path Mirroring Unit Tests (`TurnDirectionIconContractTest.kt`)*:
  • Verify that milestone coordinates and vectors in `ic_turn_sharp_left.xml` are the exact mathematical horizontal reflection ($x_R + x_L = 24.0, y_R = y_L$) of `ic_turn_sharp_right.xml` across all 22 milestone vertices.
  4. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for `turn_cue_sharp_right` and `turn_cue_sharp_left`.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-325: **Removal of Google Drive Format Scan Option from Workout Import Cards Verification** [Verified]
- **Requirement(s)**: `REQ-UI-362`
- **Component**: **Removal of Google Drive Format Scan Option from Workout Import Cards Verification**
- **Jira**: ``ATT-3489``
- **Test Description**:
  1. *Import Format Cards Architectural & Contract Tests (`ImportFormatCardsContractTest.kt`)*:
  • Verify that `FormatImportCard` composable is defined with parameters for local import and Dropbox scan, and without parameters for Google Drive.
  • Verify that `ImportTabContent` renders three distinct format blocks (FIT, TCX, GPX).
  • Verify that each format block contains only Local File and Dropbox Scan action buttons.
  • Verify that `R.string.import_fit_gdrive_button`, `R.string.import_tcx_gdrive_button`, and `R.string.import_gpx_gdrive_button` are absent from `ImportBackupTabsScreen.kt`.
  • Verify that `pendingGoogleDriveFormat` is absent from `ImportBackupTabsScreen.kt`.
  2. *9-Language Localization Audit (`ImportFormatLocalizationTest.kt`)*:
  • Verify all remaining format import titles, descriptions, and button labels exist and are non-empty across all 9 supported locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  • Verify that `import_fit_gdrive_button`, `import_tcx_gdrive_button`, and `import_gpx_gdrive_button` are cleanly removed across all 9 locale resource files without dangling references.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-326: **Recursive Directory Workout File Discovery and Bulk Import via Storage Access Framework Verification** [Verified]
- **Requirement(s)**: `REQ-UI-363`
- **Component**: **Recursive Directory Workout File Discovery and Bulk Import via Storage Access Framework Verification**
- **Jira**: ``ATT-3491``
- **Test Description**:
  1. *Recursive Traversal Engine Tests (`SafDirectoryScannerTest.kt`)*:
  • Verify that `SafDirectoryScanner` discovers matching files (`.fit`, `.tcx`, `.gpx`) across nested directory hierarchies.
  • Verify that scanner filters files case-insensitively and ignores incompatible extensions.
  • Verify depth limit bounding ceases traversal at `maxDepth = 6`.
  • Verify cycle detection prevents infinite loops across duplicate or cyclical directory links.
  • Verify empty directories return empty list without crashing.
  • Verify coroutine cancellation stops traversal promptly.
  2. *Import Format Cards Architectural & Contract Tests (`ImportFormatCardsContractTest.kt`)*:
  • Verify that `FormatImportCard` defines `folderButtonText` and `onFolderClick` parameters.
  • Verify that `ImportTabContent` defines `onFolderImportClick` and configures all 3 cards (FIT, TCX, GPX) with folder scan handlers.
  • Verify that each format card displays all 3 action buttons: Local File Import, Folder Scan, and Dropbox Scan.
  3. *9-Language Localization Audit (`ImportFormatLocalizationTest.kt`)*:
  • Verify all newly added folder scan and status string resources exist, are non-empty, and match parameter format specifiers across all 9 supported locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-327: **Centralized Metric Distance and Altitude Formatting Enforcement Across Navigation and Telemetry Cards Verification** [Verified]
- **Requirement(s)**: `REQ-UI-364`
- **Component**: **Centralized Metric Distance and Altitude Formatting Enforcement Across Navigation and Telemetry Cards Verification**
- **Jira**: ``ATT-3361``
- **Test Description**:
  1. *Unit & Contract Tests for Navigation & HUD Metric Formatters (`MetricFormatterContractTest.kt`)*:
  • Verify `ForkDecisionCard` distance & elevation formatting via `DistanceFormatter` and `AltitudeFormatter`.
  • Verify `ReturnNavigationState` formats `formattedRemainingDistance` and `formattedRemainingClimb` using `DistanceFormatter` and `AltitudeFormatter`.
  • Verify `RouteSelectionButton` and `RouteSelectorSheet` format distance and elevation using canonical formatters.
  • Verify `TurnPromptBanner` renders `distanceRemaining` formatted with units.
  • Verify `LapSplitVisualizer` formats split distance via `DistanceFormatter`.
  • Verify `LiveClimbSheet` formats remaining elevation and distance via canonical formatters without raw `" m"` literals.
  2. *Imperial & Metric Unit Parity Verification (`MetricFormatterContractTest.kt`)*:
  • Verify that when `MyUnits.IMPERIAL` is active, distances format with feet (`ft`) and miles (`mile`), and elevations format with feet (`ft`).
  • Verify zero occurrences of raw `"km"` or `" m"` in Imperial mode.
  • Verify that when `MyUnits.METRIC` is active, distances and elevations format with canonical `m` and `km`.
  3. *Formatter JVM Robustness & Null-Safety Tests (`DistanceFormatterTest.kt`, `AltitudeFormatterTest.kt`)*:
  • Verify that `DistanceFormatter.format_with_units()` and `AltitudeFormatter.format_with_units()` execute safely in headless JVM environments without throwing `NullPointerException` when `ApplicationContext` or `SharedPreferences` is null.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-328: **Centralized Speed Unit Conversion, Formatter Harmonization and Progressive Disclosure Verification** [Verified]
- **Requirement(s)**: `REQ-UI-365`
- **Component**: **Centralized Speed Unit Conversion, Formatter Harmonization and Progressive Disclosure Verification**
- **Jira**: `ATT-3363`, `ATT-5821`
- **Test Description**:
  1. *Unit & Precision Tests for MyHelper Conversions (`MyHelperSpeedConversionTest.kt`)*:
  • Verify bidirectional conversion `mps2userUnit(speed, unit)` and `UserUnit2mps(speed, unit)` across `MyUnits.METRIC` (factor 3.6) and `MyUnits.IMPERIAL` (factor 2.23693629).
  • Verify mathematical identity: `UserUnit2mps(mps2userUnit(speed, unit), unit) == speed` within $10^{-6}$ tolerance across wide speed range (0 to 100 m/s).
  • Verify zero/negative speed boundary safety.
  2. *TelemetryMetricUtils Unit & Formatting Tests (`TelemetryMetricUtilsTest.kt`)*:
  • Verify `extractMetricValue` for `SPEED` under both `MyUnits.METRIC` and `MyUnits.IMPERIAL` matches `MyHelper.mps2userUnit(speed, unit)`.
  • Verify `formatValue` for `SPEED` under both `MyUnits.METRIC` and `MyUnits.IMPERIAL` converts user unit values via `MyHelper.UserUnit2mps(value, unit)` prior to passing to `SpeedFormatter`.
  • Verify zero inline occurrences of magic constants `3.6` or `2.236936` in `TelemetryMetricUtils.kt`.
  3. *EditSportTypeDialog Formatting & Input Parsing Tests (`EditSportTypeDialogTest.kt`)*:
  • Verify `formatSpeed` outputs consistent two-decimal-place numbers formatted without ad-hoc string character replacement (`.replace(",", ".")`).
  • Verify decimal input parsing handles both comma (`,`) and dot (`.`) inputs seamlessly, correctly converting user inputs into valid m/s threshold bounds.
  4. *SpeedFormatter DRY Integration Tests (`SpeedFormatterTest.kt`)*:
  • Verify `SpeedFormatter.format_with_units()` delegates conversion to `MyHelper.mps2userUnit()`, matching canonical speeds across metric and imperial settings.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.
  6. *Progressive Disclosure & Advanced Options Encapsulation Contract Test (`TST-UI-328.6`, `EditSportTypeDialogContractTest.kt`)*:
  • Verify `EditSportTypeDialog.kt` source contract: Name, Base Sport, Equipment, and Strava are rendered before the Advanced Options accordion.
  • Verify Min/Max speed fields, speed detection hint, TCX dropdown, and GoldenCheetah dropdown are encapsulated inside the accordion.
  • Verify default collapsed state (`isAdvancedExpanded = false` via `rememberSaveable`).
  • Verify 100% 9-language translation parity for `sport_type_advanced_options`, `sport_type_advanced_options_desc`, and `sport_type_speed_detection_hint`.

### TST-UI-329: **Harmonized Live Segment and Live Climb Tracking Bottom Sheet Architecture & Container Verification** [Verified]
- **Requirement(s)**: `REQ-UI-366`
- **Component**: **Harmonized Live Segment and Live Climb Tracking Bottom Sheet Architecture & Container Verification**
- **Jira**: ``ATT-3364``
- **Test Description**:
  1. *Architectural Contract Tests (`LiveSegmentSheetContractTest.kt`)*:
  • Verify file path resolution to `LiveSegmentSheet.kt` under standard PascalCase.
  • Verify `wrapContentHeight` and map suppression parameters in `MapDetailLayout` integration.
  • Verify `SensorGridScreen.kt` mounts `LiveSegmentSheet` inside a content-wrapped container.
  • Verify `externalScrubDistance` dynamic evaluation (`REQ-UI-331`, `REQ-UI-340`).
  2. *Composable Layout Reflection Tests (`LiveSegmentSheetLayoutTest.kt`, `LiveClimbSheetLayoutTest.kt`)*:
  • Verify `LiveSegmentSheetKt` and `LiveClimbSheetKt` public visibility and composable signatures.
  • Verify design tokens for drag handle, badges, and shared elevation profile embedding.
  3. *Unified Container Component Tests (`LiveTrackingSheetContainerTest.kt`)*:
  • Verify that `LiveTrackingSheetContainer` applies `.fillMaxWidth().wrapContentHeight().sheetContour().background(...)` and triggers click-to-expand in `PartiallyExpanded` state.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-330: **First-Time Stop Tracking Swipe and Tab Coachmark Banner on Cockpit Screen Verification** [Verified]
- **Requirement(s)**: `REQ-UI-367`
- **Component**: **First-Time Stop Tracking Swipe and Tab Coachmark Banner on Cockpit Screen Verification**
- **Jira**: ``ATT-3387`, `ATT-4456``
- **Test Description**:
  1. *Coachmark Visibility, Optimistic Dismissal & Auto-Acknowledgement Predicate Unit Tests (`StopTrackingCoachmarkTest.kt`)*:
  • Verify `shouldShowStopTrackingCoachmark` returns `true` when `hasSeenHint == false`, `isDismissPending == false`, `trackingMode` is `TRACKING` or `PAUSED`, `currentPage > 0`, and `screenMode == ScreenMode.TRACKING`.
  • Verify `shouldShowStopTrackingCoachmark` returns `false` when `trackingMode == TrackingMode.READY`, when `currentPage == 0`, when `screenMode == ScreenMode.CONFIGURATION`, when `hasSeenHint == true`, or immediately when `isDismissPending == true` (simulating synchronous optimistic dismissal while `DataStore` write is delayed/hanging or across interrupted `READY -> TRACKING -> READY -> TRACKING` transitions).
  • Verify `shouldAutoAcknowledgeStopTrackingCoachmark` returns `false` when `hasRenderedCoachmarkInSession == false`, `isDismissPending == true`, `currentPage != 0`, `settledPage != 0`, or `isScrollInProgress == true` (preventing premature dismissal during scroll animations from `Page 0` to `Page 1`), and returns `true` only when `hasRenderedCoachmarkInSession == true`, `currentPage == 0`, `settledPage == 0`, `!isScrollInProgress`, `hasSeenHint == false`, `!isDismissPending`, and tracking is active/paused.
  • Verify in-flight animation states (`currentPage = 1, settledPage = 0, isScrollInProgress = true` and `currentPage = 0, settledPage = 0, isScrollInProgress = true`) strictly return `false`.
  2. *ViewModel Configuration-Change Survival & DataStore Persistence Contract Tests (`StopTrackingCoachmarkTest.kt`)*:
  • Verify `TrackingTabsViewModel.hasRenderedCoachmarkInSession` and `isCoachmarkDismissPending` survive configuration changes across UI recreations and reset appropriately.
  • Verify `TuningPreferencesDataStore` defines `KEY_HAS_SEEN_STOP_TRACKING_HINT = booleanPreferencesKey("tuning_has_seen_stop_tracking_hint")` in `ALL_KEYS`, exposes `hasSeenStopTrackingHintFlow` and `setHasSeenStopTrackingHint(boolean)`, and automatically resets the key upon `resetToDefaults()`.
  3. *Layout Stability, Height Constraint, Settled Gating & 4-Inch Small-Screen Telemetry Clearance Contract Verification (`StopTrackingCoachmarkTest.kt`)*:
  • Verify `TrackingTabsScreen.kt` gates `markCoachmarkRenderedInSession()` on `showStopTrackingCoachmark && pagerState.settledPage > 0 && !pagerState.isScrollInProgress`.
  • Verify `TrackingTabsScreen.kt` hosts `HorizontalPager` and `StopTrackingCoachmarkBanner` inside a constant-weight `Box(modifier = Modifier.weight(1f).fillMaxWidth())` with `Alignment.TopCenter` so appearing/disappearing never causes `HorizontalPager` to jump or resize.
  • Verify `StopTrackingCoachmarkBanner.kt` enforces `heightIn(max = 120.dp)`, `maxLines = 2`, `TextOverflow.Ellipsis`, weighted text layout (`Modifier.weight(1f)`) for 4-inch small-screen telemetry clearance, 48dp minimum touch targets (`defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)`), and zero tonal elevation (`tonalElevation = 0.dp`).
  4. *9-Language Localization Parity Audit (`StopTrackingCoachmarkTest.kt`, `TranslationParityTest.kt`)*:
  • Verify `coachmark_stop_tracking_hint`, `coachmark_stop_tracking_show_tab`, and `coachmark_stop_tracking_dismiss` exist and are non-empty across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-331: **Interactive Start/End Workout Cropping with Destructive Sample Pruning and Full Metric Recalculation Verification** [Verified]
- **Requirement(s)**: `REQ-UI-368`
- **Component**: **Interactive Start/End Workout Cropping with Destructive Sample Pruning and Full Metric Recalculation Verification**
- **Jira**: ``ATT-3274``
- **Test Description**:
  1. *Pure Crop Math & Telemetry Aggregation Unit Tests (`CropWorkoutMathTest.kt`)*:
  • Verify that cumulative monotonic columns (`DISTANCE_m`, `TIME_TOTAL`, `TIME_ACTIVE`, `ASCENT`, `DESCENT`, `CALORIES`) are zero-anchored at `startRowId` and clamped with `EPSILON = 1e-6` for subsequent rows, eliminating floating-point subtraction drift.
  • Verify row-by-row `LINE_DISTANCE_m` Haversine recalculation relative to the new start coordinate, and zero-anchoring when GPS coordinates are null (indoor sessions).
  • Verify strict re-derivation of non-linear extrema (`MIN`, `MAX`, `AVG`) from retained sample rows, discarding pruned points.
  • Verify input sanitization (`Double.isFinite(v)`), ensuring `NaN` and `Infinity` values are filtered out from sums and averages.
  • Verify fallback to `0.0` extrema when a sensor has zero valid readings in the cropped interval.
  • Verify duration guard $\ge 1\text{ s}$ and safe speed division (`activeTimeSec >= 1.0`).
  2. *Lap Recalculation & Metadata Preservation Unit Tests (`CropLapsRecalculationTest.kt`)*:
  • Verify deterministic lap mapping where retained lap $k$ strictly preserves user-authored `NAME` and `DESCRIPTION` from its original parent lap without truncation or heuristic merging.
  • Verify boundary laps 1 and $K$ have their duration, distance, and average speed recalculated strictly from their retained samples.
  • Verify sequential re-indexing `1..K` in lockstep across laps and sample rows.
  • Verify verification assertion $\sum_{k=1}^K \text{DISTANCE\_TOTAL\_m}_k == \text{workout.DISTANCE\_TOTAL\_m}$ within `1e-6`.
  3. *Database Transaction & Rollback Recovery Tests (`WorkoutRepositoryCropTest.kt`)*:
  • Verify SQLite rollback journal guarantees that any exception during sample pruning, lap re-indexing, or summary updates rolls back all modifications cleanly without data corruption.
  • Verify post-op checksum validation (`remainingRows == expectedRetainedRowCount && deletedRows == expectedDeletedRowCount`).
  • Verify pre-flight storage check (`StatFs` $\ge 10\text{ MB}$) rejects crop when storage is insufficient.
  • Verify pre-crop ephemeral JSON snapshot creation and cleanup inside `try ... finally`.
  • Verify synchronous export cache file deletion pre-commit and post-commit `WorkManager` replacement via `ExistingWorkPolicy.REPLACE`.
  • Verify that if `WorkManager` enqueue fails, `ExportStatus` transitions to `ERROR` and SQLite changes remain committed.
  4. *ViewModel & UI Pre-Condition Contract Tests (`CropWorkoutScreenContractTest.kt`)*:
  • Verify `isCropValid` predicate: button is disabled when `(endIndex - startIndex < minIndexStep)` or `croppedDuration < 1.0s` or `isCroppingInProgress == true`.
  • Verify `BackHandler(enabled = isCroppingInProgress)` blocks back navigation during execution.
  • Verify irreversible destructive confirmation `AlertDialog` presentation and text warning.
  • Verify "Force Re-Export" button visibility in workout details when `ExportStatus == ERROR`.
  5. *9-Language Localization Audit (`CropWorkoutLocalizationTest.kt`)*:
  • Verify all crop UI strings, button labels, dialog titles, warnings, and error messages exist and are non-empty across all 9 supported locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-332: **Prohibition of Static BottomSheetScaffold Bottom Inset Padding on Cockpit ElevationProfile Surface Verification (Post-Review Revert Fence)** [Verified]
- **Requirement(s)**: `REQ-UI-369`
- **Component**: **Prohibition of Static BottomSheetScaffold Bottom Inset Padding on Cockpit ElevationProfile Surface Verification (Post-Review Revert Fence)**
- **Jira**: ``ATT-3311``
- **Test Description**:
  1. *ElevationProfile Surface No-Bottom-Padding Fence Verification (`SensorGridScreenLayoutTest.kt`)*:
  • Verify `testElevationProfileSurface_doesNotApplyStaticScaffoldBottomPadding`: `SensorGridScreen.kt` MUST NOT contain `bottom = paddingValues.calculateBottomPadding()`, preventing the $\approx 174\text{dp}$ permanent blank whitespace below the `ElevationProfile` when the Live Segment / Live Climb sheet is swiped down to `SheetValue.Hidden`.
  2. *Sensor Grid Scrollability & Asymmetric Map Weight Preservation Tests (`SensorGridScreenLayoutTest.kt`)*:
  • Verify sensor grid retains `.weight(1f, fill = false)` when map is hidden and `.heightIn(max = availableHeight * 0.5f)` when map is visible (`REQ-UI-358`).

### TST-UI-333: **Decoupled Training Zone Architecture & 7-Zone Coggan Cycling Power Model Verification** [Verified]
- **Requirement(s)**: `REQ-UI-370`
- **Component**: **Decoupled Training Zone Architecture & 7-Zone Coggan Cycling Power Model Verification**
- **Jira**: ``ATT-3184``
- **Test Description**:
  1. *DataStore Dynamic Proportional Migration & Persistence Unit Tests (`SettingsDataStorePowerZonesTest.kt`)*:
  • Verify `SettingsDataStore` dynamically derives missing `PWR_BIKE_ZONE5_MAX` and `PWR_BIKE_ZONE6_MAX` proportionally from existing `ZONE4_MAX` within an atomic transaction.
  • Verify canonical fallback defaults (150W, 200W, 250W, 300W, 350W, 450W) when `ZONE4_MAX` is missing.
  • Verify strict monotonic ascending order validation ($0 < z1 < z2 < z3 < z4 < z5 < z6$).
  2. *Edge-Case Athletic Baseline & Bounds Unit Tests (`SettingsDataStorePowerZonesTest.kt`, `TelemetryZoneMathTest.kt`)*:
  • Verify proportional calculation and monotonic validity across extreme athletic profiles: ultra-low FTP (60W) and ultra-high FTP (500W).
  • Verify negative and zero power clamping ($P \le 0$).
  3. *TelemetryZoneMath 7-Zone Band Calculation Unit Tests (`TelemetryZoneMathTest.kt`)*:
  • Verify `calculatePowerZoneBands` returns exactly 7 non-overlapping, clamped bands with `Zone1..Zone7` colors and labels `Z1..Z7`.
  • Verify `determinePowerZone` maps power values across all 7 intervals correctly.
  • Verify `loadPowerThresholds` loads 6 boundaries.
  4. *PowerZoneDistributionCalculator 7-Zone Unit Tests (`PowerZoneDistributionCalculatorTest.kt`)*:
  • Verify `calculatePowerDistribution` computes cumulative durations and percentages across 7 zones.
  • Verify `calculatePowerHistogram` maps frequency bins across 7 zones with matching colors.
  5. *Tracking Cockpit HUD & Battery Saver Unit Tests (`SensorFieldZoneDisplayTest.kt`, `BatterySaverModelTest.kt`)*:
  • Verify `TrackingViewModel` resolves `TTColor.Zone6` (Dark Chestnut Brown) and `TTColor.Zone7` (Black).
  • Verify `BatterySaverModel.calculateZoneIndex` returns zones `1..7` for cycling power.
  6. *9-Language Localization Audit (`PowerZoneLocalizationTest.kt`)*:
  • Verify `zone_6_label` and `zone_7_label` exist and are non-empty across all 9 supported locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-334: **Elevation Profile 7-Zone Gradient Shading and Legend Verification** [Verified]
- **Requirement(s)**: `REQ-UI-371`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Component**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileOverlayComponents.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileGradeColorTest.kt`
- **Verification Evidence**: `ElevationProfileGradeColorTest#testElevationGradeColor_allZonesAndExactBoundaries`, `ElevationProfileGradeColorTest#testElevationGradeColor_descentsAndNonFiniteValues`, `ElevationProfileGradeColorTest#testElevationProfile_contractAndLegendSevenZones`, `ElevationProfileGradeColorTest#testGradeLegendLocalization_allNineLocalesExist`, `ElevationProfileGradeColorTest#testElevationProfile_marginalAltitudeHistogram_steepStartDoesNotColorHistogramRed` (`5/5` PASS), `ElevationProfileClimbShadingTest#testGetClimbGradeColor_allBandsAndBoundaries` (`4/4` PASS), `ElevationProfileOverlayTest#testElevationProfile_tappingInfoButton_togglesGradeLegendVisibility_TST_UI_051` (`5/5` PASS) (`ATT-9179`, SHA `10bde5ec`, Gate 4 `ATT-9342`)
- **Jira**: `ATT-3391`, `ATT-9179`
- **Test Description**:
  1. *Elevation Slope Gradient Resolution Unit Tests (`TST-UI-334.1`, `ElevationProfileGradeColorTest.kt`)*:
  • Verify `getElevationGradeColor` evaluates all 7 zone color intervals: Zone 1 (< 2%), Zone 2 (2 - 5%), Zone 3 (5 - 10%), Zone 4 (10 - 15%), Zone 5 (15 - 20%), Zone 6 (20 - 25%), Zone 7 (>= 25%).
  • Verify exact boundary transitions ($[x_{n-1}, x_n)$): 2.0% -> Zone 2, 5.0% -> Zone 3, 10.0% -> Zone 4, 15.0% -> Zone 5, 20.0% -> Zone 6, 25.0% -> Zone 7.
  • Verify descent and negative slope handling: -15.0%, -5.0%, -0.1%, 0.0% all map to Zone 1 (`TTColor.Zone1`), and extreme/non-finite inputs (`NaN`, `+/-Infinity`, `100.0%`) resolve safely.
  2. *Behavioral Compose `GradeLegend` & Histogram Verification (`TST-UI-334.2`, `ElevationProfileGradeColorTest.kt`, `ElevationProfileOverlayComponents.kt`)*:
  • *Given* `ElevationProfileGradeColorTest` annotated with `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [34])` and `createComposeRule()` with zero `Class.forName("...Kt")` reflection calls (`REQ-PRO-132`), *When* `GradeLegend()` (defined in `ElevationProfileOverlayComponents.kt:180`) is composed inside `ATrainingTrackerTheme`, *Then* all 7 localized `grade_legend_*` labels (`< 2%`, `2 - 5%`, `5 - 10%`, `10 - 15%`, `15 - 20%`, `20 - 25%`, `> 25%`) are rendered and asserted displayed, replacing legacy `ElevationProfileKt` reflection stubs (`:100-105`, `:129-132`) while retaining all 9-locale XML parity checks (`LocalizationTestCache`) and fixed `TTColor.Zone1` marginal altitude histogram verification (`TelemetryHistogramCalculator.computeLinearMarginalHistogram`).
  3. *Preservation of Climb Shading Invariants (`TST-UI-334.3`, `ElevationProfileClimbShadingTest.kt`)*:
  • Verify that `ElevationProfileClimbShadingTest.kt` passes with 100% assertions satisfied for `REQ-UI-352`.
  4. *Targeted & Clean-Room Regression Verification (`TST-UI-334.4`)*:
  • Execute `:app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileGradeColorTest" --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileOverlayTest" --tests "com.atrainingtracker.trainingtracker.build.UiReflectionOnlyTestAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"` asserting 100% pass rate, zero regressions, and paired `app/ui-reflection-baseline.txt` ratchet (`189 -> 188`).

### TST-UI-335: **Horizontal Stacked Distribution Bar Component & 3-Way Zone Display Mode Switching Verification** [Verified]
- **Requirement(s)**: `REQ-UI-372`
- **Component**: **Horizontal Stacked Distribution Bar Component & 3-Way Zone Display Mode Switching Verification**
- **Jira**: ``ATT-3390`, `ATT-3676``
- **Test Description**:
  1. *Stacked Bar Layout & Math Unit Tests (`ZoneDistributionStackedBarTest.kt`)*:
  • Verify active entries generate proportional segment weights (`entry.percentage`) directly without overflow, and zero-duration entries are cleanly omitted from the bar.
  • Verify empty or all-zero distributions render a neutral placeholder bar without throwing exceptions.
  • Verify 5-zone HR dataset and 7-zone Power dataset rendering parity.
  • Verify outer bar and legend swatches receive uniform accessibility border outlines.
  • Verify dimmed display of zero-duration zones in legend with `0:00 (0%)`.
  2. *ZoneCardDisplayMode Enum & Legacy Compatibility Unit Tests (`ZoneCardDisplayModeTest.kt`, `TelemetryHistogramCardTest.kt`)*:
  • Verify `ZoneCardDisplayMode.values()` contains `STACKED_BAR`, `COLUMNS`, `HISTOGRAM`, and `FIVE_ZONES`.
  • Verify `FIVE_ZONES` acts as a backward-compatible alias for `COLUMNS`.
  3. *WorkoutCardSectionPreferences Persistence Unit Tests (`WorkoutCardSectionPreferencesTest.kt`)*:
  • Verify `zoneCardDisplayMode` defaults to `COLUMNS` and copies cleanly.
  • Verify DataStore round-trip across all modes and legacy `FIVE_ZONES` fallback.
  4. *Contract Regression Verification (`TelemetryMetricGraphMarginalBarsContractTest.kt`)*:
  • Verify 100% assertions satisfied for telemetry graph marginal strip contract.
  5. *9-Language Localization Audit (`ZoneDistributionStackedBarLocalizationTest.kt`)*:
  • Verify `zone_mode_stacked_bar`, `zone_mode_columns`, `zone_mode_histogram`, and `settings_zone_card_display_mode_title` exist and are non-empty across all 9 supported locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  6. *Histogram Fallback Guard Unit Tests (`ZoneCardDisplayModeFallbackTest.kt`)*:
  • Verify that when `mode == HISTOGRAM` and `distribution.histogram == null`, effective mode gracefully resolves to `COLUMNS`.
  • Verify 2-button segmented row highlights Columns when fallback is active.
  7. *Workout Summary State Precedence Tests (`WorkoutSummaryZoneDisplayModeTest.kt`)*:
  • Verify cards initialize to `preferences.zoneCardDisplayMode`.
  • Verify interactive card tap establishes manual override.
  • Verify global preference change updates cards without manual override, while preserving manual overrides.
  8. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-336: **Automatic Training Zone Boundary Calculation from FTP and Heart Rate Baselines Verification** [Verified]
- **Requirement(s)**: `REQ-UI-373`
- **Component**: **Automatic Training Zone Boundary Calculation from FTP and Heart Rate Baselines Verification**
- **Jira**: ``ATT-3188``
- **Test Description**:
  1. *Pure Mathematical Model, Coggan Rounding & Zone 7 Ceiling Unit Tests (`ZoneCalculationMathTest.kt`)*:
  • Verify Coggan 7-zone calculation for FTP = 200W, 250W, 300W matches exact half-up rounded cutoffs.
  • Verify Zone 7 physical ceiling is capped at 2500W with lower bound $Z_6 + 1\text{ W}$.
  • Verify baseline FTP boundary clamping: FTP < 50 throws `IllegalArgumentException`, FTP > 600 throws `IllegalArgumentException`.
  • Verify calculation when $Z_6 \ge 2500\text{ W}$ throws `IllegalArgumentException`.
  • Verify strict monotonicity: $Z_n \ge Z_{n-1} + 1$ for all valid FTP inputs in $[50, 600]$.
  2. *Karvonen & % HRmax Mathematical Models & Invariant Guards (`ZoneCalculationMathTest.kt`)*:
  • Verify % HRmax model for $HR_{\max} = 190\text{ bpm}$ produces monotonic 5-zone cutoffs.
  • Verify Karvonen model for $HR_{\max} = 190, HR_{\text{rest}} = 50$ produces correct HRR cutoffs.
  • Verify Primary Karvonen Invariant: $HR_{\max} \le HR_{\text{rest}}$ throws `IllegalArgumentException`.
  • Verify Karvonen delta constraint: $HR_{\max} - HR_{\text{rest}} < 20$ throws `IllegalArgumentException`.
  • Verify $HR_{\max} \notin [80, 240]$ and $HR_{\text{rest}} \notin [30, 120]$ throw `IllegalArgumentException`.
  3. *Two-Layer Monotonicity Validation & Step Increment (`ZoneValidatorTest.kt`)*:
  • Verify `validatePowerZoneStack` passes strictly monotonic ascending stacks ($Z_n \ge Z_{n-1} + 1$) and throws `IllegalArgumentException` on inversions ($Z_i \ge Z_{i+1}$) or equal adjacent values ($Z_i == Z_{i+1}$) or values $> 2500\text{W}$ or $\le 0$.
  • Verify `validateHeartRateZoneStack` passes valid monotonic 4-cutoff stacks and throws `IllegalArgumentException` on inversions, equal values, or values $> 240\text{ bpm}$.
  4. *Single-Transaction ACID Atomicity, UI Lock & DataStore Baselines (`SettingsDataStoreBaselinesTest.kt`)*:
  • Verify saving calculated power zones persists FTP, timestamp, `pwr_bike_zones_customized = false`, and all 6 cutoffs in a single transaction.
  • Verify manual cutoff mutation flips `pwr_bike_zones_customized` to `true`.
  • Verify `SettingsDataStoreJavaHelper.getFtp(context)` retrieves stored FTP or null when unconfigured.
  • Verify non-destructive migration: existing legacy cutoffs retain custom flag `true` and null FTP.
  5. *Locale-Aware Number Parsing (`ZoneNumberParserTest.kt`)*:
  • Verify parsing integer strings with standard ASCII digits (`"250"` -> `250`), German/European comma decimal separators (`"250,5"` -> `250`), grouping separators and spaces (`"1 200"`, `"1.200"`), and invalid non-numeric strings returning null.
  6. *Destructive Overwrite, Error Copy & Dialog Contract Verification (`ZoneCalculatorDialogContractTest.kt`)*:
  • Verify dialog initial state populates existing stored FTP or default 200W.
  • Verify `shouldShowOverwriteWarning` returns `true` if and only if `isCustomized == true`.
  • Verify cancelling overwrite preserves existing cutoffs without DataStore writes.
  • Verify confirming overwrite applies calculated boundaries and resets `isCustomized` to `false`.
  • Verify that Karvonen delta < 20 bpm surfaces explicit localized error `zone_calc_error_karvonen_delta`.
  • Verify UI mutation lock (`isSaving == true`) disables Apply button during persistence.
  7. *9-Language Localization & Format Specifier Token Audit (`ZoneCalculatorLocalizationTest.kt`)*:
  • Verify presence of all zone calculation string keys across all 9 application locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.
  • Verify Format Specifier Token Audit: for every string containing format tokens (`%d`, `%s`, `%1$d`), assert that every translation contains exactly the identical count, argument types, and indexing order as base `res/values/strings.xml`.
  8. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-337: **Contextual Zone Settings Navigation Upon Physiological Sensor Pairing Verification** [Verified]
- **Requirement(s)**: `REQ-UI-374`
- **Component**: **Contextual Zone Settings Navigation Upon Physiological Sensor Pairing Verification**
- **Jira**: ``ATT-3189`, `ATT-4452``
- **Test Description**:
  1. *Configuration Guard & Migration Heuristics Unit Tests (`SettingsDataStoreConfiguredTest.kt`)*:
  • Verify fresh installation defaults `isHrZonesConfigured` and `isPwrZonesConfigured` to `false`.
  • Verify migration fallback heuristic: if `is_hr_zones_configured` key is null, returns `true` when `hr_max != null` or `hr_run_zones_customized == true` or `hr_bike_zones_customized == true`.
  • Verify migration fallback heuristic: if `is_pwr_zones_configured` key is null, returns `true` when `cycling_ftp != null` or `pwr_bike_zones_customized == true`.
  • Verify explicit mutators: `setHrZonesConfigured(true)` and `setPwrZonesConfigured(true)` update DataStore preferences.
  • Verify auto-calculation: `saveCalculatedPowerZones` sets `is_pwr_zones_configured = true`; `saveCalculatedHeartRateZones` sets `is_hr_zones_configured = true`.
  2. *Pairing Detection & Sensor Discrimination Unit Tests (`DeviceListViewModelPairingTest.kt`)*:
  • Verify pairing `DeviceType.HRM` with `isHrZonesConfigured == false` emits `ZoneOnboardingEvent(initialTab = 0, promptReason = "hr_paired")`.
  • Verify pairing `DeviceType.BIKE_POWER` with `isPwrZonesConfigured == false` emits `ZoneOnboardingEvent(initialTab = 2, promptReason = "pwr_paired")`.
  • Verify pairing `DeviceType.HRM` with `isHrZonesConfigured == true` emits zero events (silent completion).
  • Verify pairing `DeviceType.BIKE_POWER` with `isPwrZonesConfigured == true` emits zero events (silent completion).
  • Verify pairing non-physiological sensors (`BIKE_SPEED`, `BIKE_CADENCE`, `RUN_SPEED`, `ENVIRONMENT`, `SPEED_AND_LOCATION_GPS`) emits zero events.
  • Verify unpairing sensor (`isPaired: true -> false`) emits zero events.
  3. *Navigation Route & Deep-Link Parameter Formatting (`NavRoutesZoneTest.kt`)*:
  • Verify `NavRoutes.trainingZones(initialTab = 0, promptReason = "hr_paired")` formats to `"training_zones?initialTab=0&promptReason=hr_paired"`.
  • Verify `NavRoutes.trainingZones(initialTab = 2, promptReason = "pwr_paired")` formats to `"training_zones?initialTab=2&promptReason=pwr_paired"`.
  • Verify default call `NavRoutes.trainingZones()` produces `"training_zones"`.
  • Verify `NavRoutes.toDrawerItemId` matches `R.id.drawer_training_zones` even with query parameters.
  4. *Contextual Guidance Banner Component & Dismissal Contract (`ZoneOnboardingBannerContractTest.kt`)*:
  • Verify banner displays localized `zone_onboarding_hr_banner` for `promptReason == "hr_paired"`.
  • Verify banner displays localized `zone_onboarding_pwr_banner` for `promptReason == "pwr_paired"`.
  • Verify tapping dismiss button executes callback and marks zones configured.
  5. *9-Language Localization & Format Token Parity Audit (`ZoneOnboardingLocalizationTest.kt`)*:
  • Verify presence and non-emptiness of `zone_onboarding_hr_banner`, `zone_onboarding_pwr_banner`, and `zone_onboarding_dismiss` across all 9 application locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  • Verify exact format token parity across all 9 translations.
  6. *Auto-Pairing Discovery Confirmation Dialog Verification (`DevicesTabbedViewModelTest.kt`, `DevicesTabbedScreenTest.kt`)*:
  • Verify receiving `BANALService.NEW_DEVICE_FOUND_INTENT` for unconfigured `DeviceType.HRM` sets `zonePromptState` to `ZoneOnboardingPrompt(DeviceType.HRM, ...)`.
  • Verify receiving `BANALService.NEW_DEVICE_FOUND_INTENT` for unconfigured `DeviceType.BIKE_POWER` sets `zonePromptState` to `ZoneOnboardingPrompt(DeviceType.BIKE_POWER, ...)`.
  • Verify receiving `BANALService.NEW_DEVICE_FOUND_INTENT` for configured HRM/Power or non-physiological sensors leaves `zonePromptState` null.
  • Verify calling `dismissZonePrompt()` resets `zonePromptState` to null.
  • Verify `DevicesTabbedScreen` renders `AlertDialog` when `zonePromptState` is non-null with localized strings.
  • Verify dialog "Configure Zones" action calls `onNavigateToZones` and dismisses prompt.
  • Verify dialog "Not Now" action dismisses prompt without navigating.
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-339: **Real-Time Visual Training Zone Gauge Verification & Precision Test Suite** [Verified]
- **Requirement(s)**: `REQ-UI-376`
- **Component**: **Real-Time Visual Training Zone Gauge Verification & Precision Test Suite**
- **Jira**: ``ATT-1293``
- **Test Description**:
  1. *Mathematical Unit Tests (`ZoneGaugeMathTest.kt`)*:
  • Verify 5-zone HR intra-zone needle calculation and boundary clamping.
  • Verify 7-zone Power intra-zone needle calculation and boundary clamping.
  • Verify open-ended top zone dynamic ceiling and physical 2500W cap.
  • Verify below-baseline clamping to 0.0.
  • Verify null/NaN/infinite inputs return null needle position.
  2. *DataStore Preference Persistence Tests (`SettingsDataStoreZoneGaugeTest.kt`)*:
  • Verify default `showGauge == false`.
  • Verify updating and saving `showGauge = true` persists and emits on Flow across all profiles.
  3. *Cockpit UI & SensorFieldView Integration Tests (`SensorFieldZoneGaugeTest.kt`)*:
  • Verify `SensorFieldState` carries `showGauge`.
  • Verify `SensorFieldView` renders `ZoneGaugeBar` when enabled for `HR` and `POWER`.
  • Verify `SensorFieldView` does not render gauge for non-zone sensors.
  • Verify needle is hidden on null/stale telemetry.
  4. *9-Language Translation Parity Tests (`TranslationParityTest.kt`)*:
  • Verify `zone_display_gauge` across all 9 application locales.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-340: **Real-Time Pedal Power Balance Verification & Precision Test Suite** [Verified]
• Verify null/non-finite returns "-- \]
- **Requirement(s)**: `REQ-UI-377`
- **Component**: **Real-Time Pedal Power Balance Verification & Precision Test Suite**
- **Jira**: ``ATT-1295``
- **Test Description**:
  1. *Mathematical Unit Tests (`PedalPowerBalanceMathTest.kt`)*:
  • Verify perfect balance: $P_R = 50 \rightarrow P_L = 50$, deviation 0%, tier `OPTIMAL`, $x = 0.50$.
  • Verify Left-dominant optimal: $P_R = 48 \rightarrow P_L = 52$, tier `OPTIMAL`, $x = 0.48$.
  • Verify Right-dominant optimal: $P_R = 52 \rightarrow P_L = 48$, tier `OPTIMAL`, $x = 0.52$.
  • Verify mild asymmetry: $P_R = 46 \rightarrow P_L = 54$, tier `MILD`, $x = 0.46$.
  • Verify severe asymmetry: $P_R = 40 \rightarrow P_L = 60$, tier `SEVERE`, $x = 0.40$.
  • Verify boundary clamping: $P_R < 0 \rightarrow 0\%$, $P_R > 100 \rightarrow 100\%$.
  • Verify null/NaN/infinity inputs return stale state.
  2. *Formatter Tests (`PedalPowerBalanceFormatterTest.kt`)*:
  • Verify valid input formats as "48 \

### TST-UI-341: **Compact Action Buttons & Header Height Containment Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-378`
- **Component**: **Compact Action Buttons & Header Height Containment Verification Suite**
- **Jira**: ``ATT-3675``
- **Test Description**:
  1. *Header Source Contract & Architectural Test (`ZonesSettingsHeaderContractTest.kt`)*:
  • Verify `ZonesSettingsActivity.kt` header row replaces `TextButton` controls with `IconButton` using `Icons.Default.Calculate` and `Icons.Default.RestartAlt`.
  • Verify title `Text` specifies `maxLines = 1` and `overflow = TextOverflow.Ellipsis`.
  • Verify left title container uses `Modifier.weight(1f, fill = false)`.
  2. *9-Language ContentDescription Localization Parity (`ZoneCalculatorLocalizationTest.kt`)*:
  • Verify string tokens `zone_calc_button` and `reset_to_defaults` maintain 100% translation parity across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate across the full unit test suite.

### TST-UI-342: **Live Segment State-Aware Metrics, Past-Finish Rollout & PR Delta Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-379`
- **Component**: **Live Segment State-Aware Metrics, Past-Finish Rollout & PR Delta Verification Suite**
- **Jira**: ``ATT-3578``
- **Test Description**:
  1. *Repository Domain Model & PR Delta Unit Tests (`LiveSegmentsRepositoryTest.kt`)*:
  • Verify that crossing finish line forward sets `segmentStatus == LiveSegmentStatus.FINISHED`.
  • Verify that `distancePastFinish_raw` equals `distanceToEnd` and `distancePastFinish` is formatted (e.g. `"210 m"`).
  • Verify that `segmentOffset` is not overloaded with `distanceToEnd` (`segmentOffset == "--"`, `segmentOffset_raw == 0.0`).
  • Verify PR delta calculations: faster than PR produces negative delta (`"-0:10"`), slower produces positive delta (`"+0:05"`), missing PR (`prTime_raw == 0`) produces null delta.
  2. *UI State-Aware Rendering Contract & AST Tests (`LiveSegmentSheetLayoutTest.kt`)*:
  • Verify `SegmentLiveDetails` evaluates state-based visibility branches.
  • Verify that in `APPROACHING` state, lateral offset row is omitted.
  • Verify that in `FINISHED` state, `segment_status_past_finish` is used, and both `segment_status_remaining` and `segment_status_offset` are omitted.
  • Verify that in `FINISHED` state, `segment_status_pr_delta` is rendered and colored in `TTColor.Zone2` when $\le 0\text{L}$.
  3. *9-Language Localization Parity & Japanese String Fix (`SegmentLiveDetailsLocalizationTest.kt`)*:
  • Verify `segment_status_past_finish` and `segment_status_pr_delta` exist across all 9 locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/` with matching `%1$s` specifiers.
  • Verify `segment_status_offset` in Japanese (`values-ja/strings.xml`) uses `コースずれ` rather than `タイム差`.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions across all 2,964+ unit tests.

### TST-UI-343: **Complete High-Density Asset Coverage & Defensive Compose Resource Resolution Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-380`
- **Component**: **Complete High-Density Asset Coverage & Defensive Compose Resource Resolution Verification Suite**
- **Jira**: ``ATT-3529``
- **Test Description**:
  1. *High-Density Resource Coverage Audit (`DrawableDensityFallbackAuditTest.kt`)*:
  • Verify that `ant_logo.png`, `logo_protocol_bluetooth.png`, `dropbox_logo_blue.png`, and `logo.png` exist with correct dimensions and non-zero size in `drawable-xxhdpi/` and `drawable-xxxhdpi/`.
  • Verify that zero density-specific drawables lack a root fallback in `app/src/main/res/drawable/`.
  2. *Compose Defensive SafePainter Contract Tests (`ComposeSafePainterContractTest.kt`)*:
  • Verify that all 11 critical Compose UI components (`PairingButtons.kt`, `PairingProtocolBottomSheet.kt`, `DeviceTypeSelectionDialog.kt`, `DeviceItem.kt`, `SensorSourceDialog.kt`, `SensorStatus.kt`, `SearchArea.kt`, `DropboxConnectionHeader.kt`, `DropboxSettingsDialog.kt`, `ActivityTypeSelectionDialog.kt`, `ControlTrackingButton.kt`) invoke `safePainterResource` instead of raw `painterResource`.
  • Verify that semantic fallback `ImageVector` instances are provided.
  3. *Defensive Exception Handling Contract Tests (`SafePainterResourceContractTest.kt`)*:
  • Verify that `safePainterResource` catches `Resources.NotFoundException` and delegates to fallback.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions across all unit tests.

### TST-UI-344: **Compose Theme Color Centralization Audit & Climb Gradient Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-381`
- **Component**: **Compose Theme Color Centralization Audit & Climb Gradient Parity Verification Suite**
- **Jira**: ``ATT-3574``
- **Test Description**:
  1. *Zero Hex Colors Outside Color.kt Architecture Contract Tests (`ThemeColorCentralizationAuditTest.kt`)*:
  • Recursively scan all `.kt` files in `app/src/main/java/` and assert exactly 0 occurrences of `Color(0x` outside `Color.kt`.
  2. *Climb Gradient Helper Resolution Tests (`ThemeColorCentralizationAuditTest.kt`)*:
  • Verify that `TTColor.getClimbGradeColor(gradePercent)` resolves correct colors for all gradient thresholds: flat (< 3.0%), mild (< 6.0%), medium (< 9.0%), steep (< 12.0%), severe (< 15.0%), and extreme (>= 15.0%).
  3. *Climb Sheets Gradient Resolution Delegation Audit (`ThemeColorCentralizationAuditTest.kt`)*:
  • Verify that both `LiveClimbSheet.kt` and `ClimbDetailSheet.kt` call `TTColor.getClimbGradeColor` and contain zero duplicate inline grade thresholds.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions across all unit tests.

### TST-UI-345: **Canonical Metric/Speed Formatting Contract Audit and Dead Slider Purge Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-382`
- **Component**: **Canonical Metric/Speed Formatting Contract Audit and Dead Slider Purge Verification Suite**
- **Jira**: ``ATT-3575``
- **Test Description**:
  1. *Metric Formatter Contract Audit (`MetricFormatterContractTest.kt`)*:
  • Verify that `ClimbDetector.kt`, `AdvancedTuningAccordion.kt`, and `NavigationSection.kt` contain zero occurrences of `"%.1f km"`, `"%.2f km"`, or `"Anstieg"`, and explicitly reference `DistanceFormatter`.
  2. *Speed Conversion Contract Audit (`SpeedConversionContractTest.kt`)*:
  • Verify that `LapSplitCalculator.kt` and `SensorsGpsFilterSection.kt` contain zero occurrences of `* 3.6` or `"%.1f km/h"`, and explicitly reference `SpeedFormatter` and `PaceFormatter`.
  3. *Dead Slider Purge Audit (`ClusterTuningContractTest.kt`)*:
  • Verify that `ClusterTuningContent.kt` does NOT declare `fun TuningSlider(`. Assert complete absence of deprecated legacy slider composable.
  4. *Climb & Split Functional Regression Verification (`ClimbDetectorTest.kt`, `LapSplitCalculatorTest.kt`)*:
  • Verify climb detection name formatting with localized climb title and `DistanceFormatter`. Verify lap split pace/speed formatting.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions across all unit tests.

### TST-UI-346: **Unified Internal Tracking Sheet Layout & Shared LiveTrackingEntitySheet Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-383`
- **Component**: **Unified Internal Tracking Sheet Layout & Shared LiveTrackingEntitySheet Verification Suite**
- **Jira**: ``ATT-3576``
- **Test Description**:
  1. *Shared Composable Structural Contract Audit (`LiveTrackingSheetContainerTest.kt`)*:
  • Verify that `LiveTrackingEntitySheet` composable exists and is public via reflection.
  • Verify that `LiveTrackingEntitySheet.kt` applies `fillMaxWidth().wrapContentHeight()`, unbroken `MaterialTheme.colorScheme.surface`, and canonical `HorizontalDivider` (thickness 0.5.dp, horizontal padding 16.dp).
  2. *Live Segment Sheet Contract Audit (`LiveSegmentSheetContractTest.kt`)*:
  • Verify that `LiveSegmentSheet.kt` invokes `LiveTrackingEntitySheet(`.
  • Verify that `LiveSegmentSheet.kt` contains zero direct calls to `HorizontalDivider(`.
  3. *Live Climb Sheet Layout Contract Audit (`LiveClimbSheetLayoutTest.kt`)*:
  • Verify that `LiveClimbSheet.kt` invokes `LiveTrackingEntitySheet(`.
  • Verify that `LiveClimbSheet.kt` contains zero direct calls to `HorizontalDivider(`.
  • Verify that `ClimbHeader` and `ClimbLiveDetails` composable declarations exist.
  • Verify that all visual tokens (Terrain icon 32dp, `climb_route_counter`, `climb_grade`, `climb_remaining_dist`, etc.) remain intact.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions across all unit tests.

### TST-UI-347: **Compact Zone-Tinted Bordered Preview Pills Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-384`
- **Component**: **Compact Zone-Tinted Bordered Preview Pills Verification Suite**
- **Jira**: ``ATT-3776``
- **Test Description**:
  1. *Source Contract Audit (`ZonesSettingsPreviewPillsContractTest.kt`)*:
  • Verify `ZonesSettingsActivity.kt` defines `ZonePreviewPill` and invokes it in the preview zone selector loop.
  • Verify `ZonesSettingsActivity.kt` contains zero `FilterChip(` invocations in the preview zone selector.
  • Verify `ZonePreviewPill` applies `RoundedCornerShape(8.dp)`, zone-tinted background (`0.28f` selected, `0.12f` unselected), zone-colored `BorderStroke` (`2.dp` selected, `1.dp` unselected), and single-line `Text` (`maxLines = 1`, `softWrap = false`).
  2. *Large Font Scale & Width Budget Math Verification (`ZonesSettingsPreviewPillsContractTest.kt`)*:
  • Verify that across a 360dp narrow viewport with 7 pills and 130% Large font scale, the available horizontal text width per pill ($\ge 32\text{ dp}$) strictly exceeds the measured width of `"Z7"` at `12sp * 1.3` ($\approx 20\text{ dp}$), guaranteeing zero line wrapping.
  • Verify WCAG AA contrast ($\ge 4.5:1$) of `onSurface` text over `0.12f` and `0.28f` alpha zone tints.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-348: **Streamlined Single-Action ZonesSettingsActivity Header Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-385`
- **Component**: **Streamlined Single-Action ZonesSettingsActivity Header Verification Suite**
- **Jira**: ``ATT-3777``
- **Test Description**:
  1. *Source Contract Audit (`ZonesSettingsHeaderContractTest.kt`)*:
  • Extract the header block between `// 1. THE HEADER` and `PrimaryScrollableTabRow(` in `ZonesSettingsActivity.kt`.
  • Assert `headerBlock` contains `IconButton`, `Icons.Default.Calculate`, and `R.string.zone_calc_button`.
  • Assert `headerBlock` omits `TextButton`, `SuggestionChip`, `Icons.Default.RestartAlt`, `R.string.reset_to_defaults`, `zone_calc_status_customized`, `zone_calc_status_calculated`, `zone_calc_last_updated`, `zone_calc_calibration_recommended`, `athlete_weight_title`, and `athlete_weight_edit`.
  • Assert `ZonesSettingsActivity.kt` omits `showAthleteWeightDialog` and `AthleteWeightDialog(`.
  2. *Bounded Title Container Verification (`ZonesSettingsHeaderContractTest.kt`)*:
  • Verify `headerBlock` applies `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, and `weight(1f, fill = false)` on the title container.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-349: **Single-Line Non-Wrapping Zone Card Display Mode Segmented Button Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-386`
- **Component**: **Single-Line Non-Wrapping Zone Card Display Mode Segmented Button Verification Suite**
- **Jira**: ``ATT-3771``
- **Test Description**:
  1. *Source Contract Audit (`WorkoutSummaryZoneDisplayModeTest.kt`, `ZoneDistributionStackedBarLocalizationTest.kt`)*:
  • Verify that `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, and `PowerZoneDistributionCard.kt` configure the 3-way `ZoneCardDisplayMode` `SegmentedButton`s with `icon = {}`, `PaddingValues(horizontal = 6.dp, vertical = 0.dp)`, `MaterialTheme.typography.labelSmall`, `maxLines = 1`, `softWrap = false`, and `overflow = TextOverflow.Ellipsis`.
  • Verify that `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` apply `Modifier.fillMaxWidth().height(28.dp)` on `SingleChoiceSegmentedButtonRow`.
  2. *9-Locale Character Budget & Width Math Audit (`ZoneDistributionStackedBarLocalizationTest.kt`)*:
  • Parse `zone_mode_stacked_bar`, `zone_mode_columns`, and `zone_mode_histogram` across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  • Assert every string is non-empty and `value.length <= 11`, guaranteeing zero line wrapping and zero truncation up to 150% system font scale on a 360dp viewport.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-350: **Compact Legend-Free Stacked Bar Display Mode Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-387`
- **Component**: **Compact Legend-Free Stacked Bar Display Mode Verification Suite**
- **Jira**: ``ATT-3772``
- **Test Description**:
  1. *Source & Default Parameter Contract Audit (`ZoneDistributionStackedBarTest.kt`)*:
  • Verify that `ZoneDistributionStackedBar.kt` declares `showLegend: Boolean = false` as the default parameter value on `ZoneDistributionStackedBar`.
  • Verify that `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` invoke `ZoneDistributionStackedBar` with `showLegend = false`.
  • Verify that `ZoneDistributionStackedBarMath` active entry filtering, 5-zone HR / 7-zone Power parity, and `RoundedCornerShape(6.dp)` / `0.5.dp` border invariants remain 100% intact.
  2. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-351: **Decoupled Summary/Details Zone Card Display Mode & Switcher-Free Summary Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-388`
- **Component**: **Decoupled Summary/Details Zone Card Display Mode & Switcher-Free Summary Verification Suite**
- **Jira**: ``ATT-3773``
- **Test Description**:
  1. *Preference Defaults, Legacy Migration & Downgrade Safety Unit Tests (`WorkoutCardSectionPreferencesTest.kt`, `WorkoutSummaryZoneDisplayModeTest.kt`)*:
  • Verify `WorkoutCardSectionPreferences().zoneCardDisplayMode == ZoneCardDisplayMode.STACKED_BAR` and `WorkoutDetailPreferences().zoneCardDisplayMode == ZoneCardDisplayMode.HISTOGRAM`.
  • Verify `parseZoneCardDisplayMode(rawMode, defaultMode)` maps `null` to `defaultMode`, legacy `"FIVE_ZONES"` to `ZoneCardDisplayMode.COLUMNS` for both Summary and Details, valid enum strings (`"STACKED_BAR"`, `"COLUMNS"`, `"HISTOGRAM"`) to their enum values, and unknown/downgrade strings (`"UNKNOWN_FUTURE_MODE"`) to `defaultMode`.
  • Verify `ZoneCardDisplayMode.HISTOGRAM.resolveWithHistogramFallback(hasHistogram = false) == ZoneCardDisplayMode.COLUMNS`.
  2. *Source & Component Wiring Contract Audit (`WorkoutSummaryZoneDisplayModeTest.kt`, `AdvancedTuningModularityTest.kt`)*:
  • Verify `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` declare `showModeSelector: Boolean = true` and gate the segmented button row on `if (showModeSelector && distribution.entries.isNotEmpty())`.
  • Verify `WorkoutSummary.kt` passes `showModeSelector = false` to `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`.
  • Verify `TrackOnMapScreen.kt` resolves `effectiveHrZoneDisplayMode = hrZoneDisplayMode ?: activeDetailPrefs.zoneCardDisplayMode` and `effectivePowerZoneDisplayMode = powerZoneDisplayMode ?: activeDetailPrefs.zoneCardDisplayMode`.
  • Verify `WorkoutMasksAndCardsSection.kt` mutates both `workoutCardPrefs.copy(zoneCardDisplayMode = mode)` and `workoutDetailPrefs.copy(zoneCardDisplayMode = mode)` and stays `< 400` lines.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-352: **Unified 8-Icon Turn Cue Arrowhead Geometry, Baseline Grounding & Reflection Symmetry Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-389`
- **Component**: **Unified 8-Icon Turn Cue Arrowhead Geometry, Baseline Grounding & Reflection Symmetry Verification Suite**
- **Jira**: ``ATT-3774``
- **Test Description**:
  1. *Unified Arrowhead Geometry & Baseline Grounding Contract Tests (`TurnDirectionIconContractTest.kt`)*:
  • Verify that all 8 `ic_turn_*.xml` files exist, have `24dp x 24dp` (`24x24`) viewport, contain a single `<path>` with `fillColor="#FF1976D2"` and `TST-UI-352` safety header and exactly one `M` command, and ground their vertical base stem at $y = 20.0\text{ dp}$ (`V20z` and `2.0dp` stem cut `h2` or `h-2`).
  • Verify that all 4 cardinal icons (`ic_turn_left.xml`, `ic_turn_right.xml`, `ic_turn_straight.xml`, `ic_turn_u_turn.xml`) use the unified filled triangle arrowhead with $3.0\text{ dp}$ wing overhangs (`v3`/`v-3` or `h3`/`h-3`) and $(4.0\text{ dp}, 4.0\text{ dp})$ tip legs ($W = 8.0\text{ dp}, D = 4.0\text{ dp}$).
  • Verify that all 4 diagonal icons (`ic_turn_slight_left.xml`, `ic_turn_slight_right.xml`, `ic_turn_sharp_left.xml`, `ic_turn_sharp_right.xml`) use the unified filled triangle arrowhead with $(2.12\text{ dp}, 2.12\text{ dp})$ perpendicular wing overhangs ($o = 3.0\text{ dp}$) and $5.66\text{ dp}$ axis-aligned outer wing legs ($W = 8.0\text{ dp}, D = 4.0\text{ dp}$).
  2. *Turn Left / Turn Right Vertical Centering & 3-Pair Reflection Symmetry Tests (`TurnDirectionIconContractTest.kt`)*:
  • Verify that `ic_turn_left.xml` and `ic_turn_right.xml` center their arrowhead tips at $y = 10.0$ (`(5.0, 10.0)` and `(19.0, 10.0)`) with symmetrical upper ($y = 6.0$) and lower ($y = 14.0$) wing vertices on the $y \in [9, 11]$ shaft.
  • Verify exact horizontal reflection symmetry ($x_L + x_R = 24.0 \pm 0.01, y_L = y_R \pm 0.01$) across all milestone vertices for all 3 directional pairs: `LEFT`/`RIGHT`, `SLIGHT_LEFT`/`SLIGHT_RIGHT`, and `SHARP_LEFT`/`SHARP_RIGHT`.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-353: **Linear ZoneGaugeBar Scaling, 10% Boundary Clipping, Under-Segment Badge & Primary-Blue Needle Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-390`
- **Component**: **Linear ZoneGaugeBar Scaling, 10% Boundary Clipping, Under-Segment Badge & Primary-Blue Needle Verification Suite**
- **Jira**: ``ATT-3708``
- **Test Description**:
  1. *Proportional Segment Weights & 10% Boundary Clipping Math Tests (`ZoneGaugeMathTest.kt`)*:
  • Verify that for standard Coggan 7-zone power cutoffs (`[55%, 75%, 90%, 105%, 120%, 150%]` of FTP), `ZoneGaugeMath.calculateSegmentWeights(cutoffs, isPower = true)` produces weights `[10/115f, 20/115f, 15/115f, 15/115f, 15/115f, 30/115f, 10/115f]` summing to `1.0f`.
  • Verify that across both 5-zone HR and 7-zone Power cutoffs, `calculateNeedleFraction(C_i, cutoffs)` equals the exact cumulative sum of `segmentWeights.take(i).sum()` within `1e-4f`, and needle velocity $\Delta\text{needleFraction}/\Delta\text{value}$ is strictly constant across $[X_{\min}, X_{\max}]$.
  • Verify clamping to `0.0f` for $\text{value} \le X_{\min}$ and `1.0f` for $\text{value} \ge X_{\max}$.
  2. *Source & State Contract Tests (`SensorFieldZoneGaugeTest.kt`)*:
  • Verify `ZoneGaugeState` stores `segmentWeights` and defaults safely when constructed directly.
  • Verify `ZoneGaugeBar.kt` uses an outer `Column(modifier = modifier.fillMaxWidth())`, weights each track segment and under-segment badge slot by `state.segmentWeights`, centers `"Z${state.activeZoneIndex}"` in `MaterialTheme.colorScheme.onSurface`, and renders a `4.dp` needle in `MaterialTheme.colorScheme.primary` instead of `Color.White`.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-354: **Athlete Profile SQLite History, Unidirectional DataStore Projection, Derived W/kg Time-Series Math, Navigation & 9-Language Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-391`
- **Component**: **Athlete Profile SQLite History, Unidirectional DataStore Projection, Derived W/kg Time-Series Math, Navigation & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3704``
- **Test Description**:
  1. *DTO Mapping Symmetry & Derived W/kg Time-Series Math Tests (`AthleteMetricsDtoAndMathTest.kt`)*:
  • Verify `AthleteMetricEntry.fromCursor` and `AthleteMetricEntry.fromCursorBatch` produce identical objects on mock cursors without closing the cursor (Rule 20).
  • Verify `AthleteMetricsPlotMath.computeDerivedPowerToWeightSeries` computes `F(t) / W(t)` at each distinct measurement timestamp and formats `W/kg` to 2 decimal places (`%.2f`).
  • Verify `AthleteMetricsPlotMath.filterAndConvertSeries` filters points by `1M`, `3M`, `6M`, `1Y`, `ALL` and converts `WEIGHT` via `SettingsDataStore.LBS_PER_KG` when `isImperial = true`.
  • Verify `AthleteMetricType` bounds validation for `WEIGHT_KG` (`30..300`), `HR_REST_BPM` (`30..120`), and `FTP_WATTS` (`50..600`).
  2. *Repository Seeding & Unidirectional Projection State-Machine Tests (`AthleteMetricsRepositorySyncTest.kt`)*:
  • Verify `ensureSeededFromDataStore()` seeds initial `WEIGHT_KG`, `HR_REST_BPM`, and `FTP_WATTS` once and is idempotent.
  • Verify adding/editing a newer entry updates `SettingsDataStore`, adding a backdated entry preserves the newer value in `SettingsDataStore`, deleting the latest entry reverts `SettingsDataStore` to the next most recent remaining entry, and deleting the final entry resets `SettingsDataStore` (`0.0f` / `null`).
  3. *Navigation, Source Contract & 9-Locale Parity Audit (`AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify `NavRoutes.fromDrawerItemId(R.id.drawer_athlete_profile) == NavRoutes.ATHLETE_PROFILE` and `createDrawerGroups` includes `R.id.drawer_athlete_profile` in `R.string.drawer__my_stuff`.
  • Verify `ic_athlete_profile.xml` exists in `res/drawable/` with curved SVG primitives (Rule 37).
  • Verify all new `athlete_profile_*` and `drawer_athlete_profile` strings exist across all 9 locales with matching positional format specifiers.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-355: **Warm Earthy Brown Zone 6 Color Token, XML/Compose Parity & Zone 7 Luminance Contrast Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-392`
- **Component**: **Warm Earthy Brown Zone 6 Color Token, XML/Compose Parity & Zone 7 Luminance Contrast Verification Suite**
- **Jira**: ``ATT-3696``
- **Test Description**:
  1. *Token Hex Value, XML Parity & Contrast Ratio Unit Tests (`SensorFieldZoneDisplayTest.kt`, `SensorFieldZoneGaugeTest.kt`)*:
  • Verify `TTColor.Zone6 == Color(0xFF8B5A2B)` and `TTColor.Zone7 == Color(0xFF000000)`.
  • Parse `app/src/main/res/values/color.xml` and verify `<color name="zone_6">#8B5A2B</color>`.
  • Verify WCAG 2.1 relative luminance contrast ratio $(L_6 + 0.05) / (L_7 + 0.05) \ge 3.0$ ($3.44:1$) between `TTColor.Zone6` and `TTColor.Zone7`.
  2. *9-Locale Zone Label Parity Audit (`PowerZoneLocalizationTest.kt`)*:
  • Verify `zone_6_label` and `zone_7_label` exist and are non-empty across all 9 locales.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-356: **Control Tracking Dead Sensor Pairing Removal & Active Sensor Flow Preservation Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-393`
- **Component**: **Control Tracking Dead Sensor Pairing Removal & Active Sensor Flow Preservation Verification Suite**
- **Jira**: ``ATT-3692``
- **Test Description**:
  1. *Architectural Contract Tests (`ControlTrackingDeadPairingRemovalTest.kt`)*:
  • Verify `PairingButtons.kt` does not exist on disk.
  • Verify `ControlTrackingScreen.kt` omits `isAntSupported`, `isBluetoothSupported`, `onPairingClicked`, `selectingProtocol`, `onDeviceTypeSelected`, `onCancelDeviceTypeSelection`, and `DeviceTypeSelectionDialog`.
  • Verify `ControlTrackingViewModel.kt` omits `ToPairing`, `selectingProtocol`, `_selectingProtocol`, `isAntProperlyInstalled`, `isBluetoothSupported`, `onPairingClicked`, `onDeviceTypeSelected`, and `onCancelDeviceTypeSelection`, while retaining `hasPairedRemoteDevices`, `remoteDevices`, `onSearchClicked`, `ToEditDevice`, and `ShowRecoveryDialog`.
  • Verify `TrackingTabsScreen.kt` omits `selectingProtocol`, `ToPairing`, `isAntSupported`, `isBluetoothSupported`, `onPairingClicked`, `onDeviceTypeSelected`, and `onCancelDeviceTypeSelection`.
  • Verify `MainActivityWithNavigation.kt` omits `fun startPairing(`.
  • Verify `DeviceTypeSelectionDialog.kt` still exists and is referenced by `DevicesTabbedScreen.kt`.
  2. *Safe Painter Contract Verification (`ComposeSafePainterContractTest.kt`)*:
  • Verify `ComposeSafePainterContractTest` passes across all 10 remaining active Compose components.
  3. *9-Language Localization Audit (`ControlTrackingDeadPairingRemovalTest.kt`)*:
  • Verify `devices_pair_protocol_title` remains present and non-empty across all 9 locales for `PairingProtocolBottomSheet.kt`.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-357: **ElevationProfile Empty State Rendering, Canonical Height & 9-Language Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-394`
- **Component**: **ElevationProfile Empty State Rendering, Canonical Height & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3779``
- **Test Description**:
  1. *Source & Canonical Height Contract Tests (`ElevationProfileEmptyStateContractTest.kt`)*:
  • Verify `calculateElevationProfileHeight(0.0) == 70.dp` (`docs/design_guidelines.md` Section 2.3).
  • Verify `ElevationProfile.kt` declares `showEmptyState: Boolean = false` on both `ElevationProfile` overloads, invokes `ElevationProfileEmptyState` when `pathPoints.size < 2 && showEmptyState`, and sizes `ElevationProfileEmptyState` via `fixedHeight ?: calculateElevationProfileHeight(0.0)`.
  • Verify `ElevationProfile.kt` includes `Icons.Default.Terrain`, `R.string.elevation_profile_empty_state`, and Light/Dark `@Preview`s (`ElevationProfileEmptyStateLightPreview`, `ElevationProfileEmptyStateDarkPreview`).
  • Verify `SensorGridScreen.kt` gates the elevation profile `Surface` on `if (state.showElevationProfile)` (without `&& resolvedProfile.pathPoints.size >= 2`) and passes `showEmptyState = true` to `ElevationProfile(...)`.
  2. *9-Language Localization Audit (`ElevationProfileEmptyStateContractTest.kt`)*:
  • Parse all 9 `values*/strings.xml` files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) and assert that `elevation_profile_empty_state` exists and is non-blank in all 9 locales.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-338: **Mathematical VectorDrawable Migration from Xfig/SVG/XCF Masters & Legacy Raster Purge Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-375`
- **Component**: **Mathematical VectorDrawable Migration from Xfig/SVG/XCF Masters & Legacy Raster Purge Verification Suite**
- **Jira**: ``ATT-2859``
- **Test Description**:
  1. *DOM & Rule 37 Geometric Contract Tests (`VectorAssetResolutionContractTest.kt`)*:
  • Verify all 16 `.xml` vector drawables exist in `app/src/main/res/drawable/`, parse cleanly as XML `<vector>` documents, and include provenance headers (`REQ-UI-375`, `TST-UI-338`).
  • Verify every `<vector>` contains curved geometric primitives (`A`, `a`, `C`, `c`, `Q`, `q`) and either stroked Xfig primitives (`android:strokeWidth`) or compound `evenOdd` paths (`android:fillType="evenOdd"`).
  • Verify multi-color fidelity in `bsport_bike.xml`, `bsport_run.xml`, `bsport_other.xml`, ANT+ badges, and BLE badges.
  • Verify zero `.png` files remain for the 16 migrated icons, `bsport_*_gray.png`, or `btle_hr.png` across all `drawable*` directories.
  2. *UI & Density Fallback Tests (`SportTypeSelectorContractTest.kt`, `DrawableDensityFallbackAuditTest.kt`)*:
  • Verify `SportTypeSelector.kt` preserves `tint = Color.Unspecified` with `REQ-UI-375` documentation.
  • Verify `DrawableDensityFallbackAuditTest` passes 100%.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-358: **Categorized & Searchable SensorType Selector, 7-Category Exhaustive Partition & 9-Language Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-395`
- **Component**: **Categorized & Searchable SensorType Selector, 7-Category Exhaustive Partition & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3852``
- **Test Description**:
  1. *Exhaustive 43-Metric `SensorCategory` Partition & Search Filter Unit Tests (`SensorCategoryAndSelectorContractTest.kt`)*:
  • Verify `SensorCategory.values().size == 7` and every category returns a non-zero `titleResId`.
  • Verify all 43 `SensorType.values()` return a non-null `category` matching the exact 7-category partition (`TIME_AND_LAPS` = 5, `SPEED_PACE_AND_DISTANCE` = 4, `CADENCE_AND_RUNNING` = 2, `HEART_RATE_AND_ENERGY` = 2, `POWER_AND_PEDALING` = 13, `ELEVATION_AND_NAVIGATION` = 9, `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS` = 8).
  • Verify `SensorTypeSearchFilter.filterByQuery` matches case-insensitively across localized full name, short name, unit string, and enum name, and returns all items when the query is blank.
  2. *Source Contract, Bottom Sheet Scroll Safety & 9-Language Localization Audit (`SensorCategoryAndSelectorContractTest.kt`)*:
  • Verify `EditSensorFieldDialog.kt` defines and invokes `SensorTypeSelectorSection`, references `SensorCategory`, `R.string.sensor_type_search_hint`, and `R.string.sensor_type_no_matches`, and contains zero `verticalScroll(` or `LazyColumn(` calls (`docs/design_guidelines.md` Section 5.8).
  • Parse all 9 `values*/strings.xml` files and verify all 9 new string keys (`sensor_category_*`, `sensor_type_search_hint`, `sensor_type_no_matches`) exist and are non-blank across all 9 locales.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-359: **AthleteProfileScreen System Navigation Bar Bottom Inset Clearance Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-396`
- **Component**: **AthleteProfileScreen System Navigation Bar Bottom Inset Clearance Verification Suite**
- **Jira**: ``ATT-3850``
- **Test Description**:
  1. *Source Contract Audit (`AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify `AthleteProfileScreen.kt` imports `WindowInsets`, `asPaddingValues`, and `navigationBars` from `androidx.compose.foundation.layout`.
  • Verify `AthleteProfileContent` computes `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` and configures the `LazyColumn` `contentPadding` with `start = 16.dp`, `top = 16.dp`, `end = 16.dp`, and `bottom = 16.dp + navBarBottomPadding` (Rule 41).
  • Verify `AthleteProfileScreen.kt` retains `.statusBarsPadding()` on the top header row.
  2. *9-Language Localization Parity Audit (`AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify all 22 `athlete_profile_*` and `drawer_athlete_profile` strings remain present with matching positional format specifiers across all 9 locales.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-360: **Navigation Drawer 5-Group Restructuring, Consolidated Equipment & Profile/Zones Tabs, and Collapsible Secondary Sections Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-397`
- **Component**: **Navigation Drawer 5-Group Restructuring, Consolidated Equipment & Profile/Zones Tabs, and Collapsible Secondary Sections Verification Suite**
- **Jira**: ``ATT-3851``
- **Test Description**:
  1. *Drawer Structure, Collapsible Groups & Consolidated Selection Tests (`NavigationDrawerStateTest.kt`)*:
  • Verify `createDrawerGroups(R.string.tab_start)` returns 5 groups (`drawer__training` = 3, `drawer__maps` = 5, `drawer__my_stuff` = 4 in Athlete->Gear order `[drawer_athlete_profile, drawer_my_sensors, drawer_equipment, drawer_sport_types]`, `prefsOnlineCommunities` = 4, `drawer__settings` = 6 including `drawer_advanced_tuning`), with `drawer__expert_settings` removed.
  • Verify `collapsible == false` on the top 3 groups (`12` default visible items) and `collapsible == true` on `prefsOnlineCommunities` and `drawer__settings`.
  • Verify `isDrawerGroupInitiallyExpanded` returns `false` by default for collapsible groups and `true` when `selectedItemId` belongs to the collapsible group.
  • Verify `isDrawerItemSelected` matches `drawer_athlete_profile` for both `drawer_athlete_profile` and `drawer_training_zones`, and `drawer_equipment` for `drawer_equipment`, `drawer_bikes`, and `drawer_shoes`.
  2. *Route & Unified Profile/Zones Contract & 9-Locale Parity Tests (`SingleActivityNavigationTest.kt`, `AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify `R.id.drawer_equipment` <-> `NavRoutes.EQUIPMENT` mapping alongside backward-compatible route mappings.
  • Verify `AthleteProfileScreen.kt` hosts `PrimaryScrollableTabRow` and `HorizontalPager` with `athlete_profile_tab_profile`, `tab_hr_run`, `tab_hr_bike`, `tab_pwr_bike`, and `ZoneProfileTabContent`.
  • Verify `drawer_athlete_profile`, `athlete_profile_title`, and `athlete_profile_tab_profile` exist across all 9 locales.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-361: **ZoneDistributionStackedBar Per-Segment Percentage Formatting, Width Threshold & Source Contract Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-398`
- **Component**: **ZoneDistributionStackedBar Per-Segment Percentage Formatting, Width Threshold & Source Contract Verification Suite**
- **Jira**: ``ATT-3853``
- **Test Description**:
  1. *Pure Percentage Formatting & Width-Threshold Unit Tests (`ZoneDistributionStackedBarTest.kt`)*:
  • Verify `ZoneDistributionStackedBarMath.formatSegmentPercentage` formats `0f`, `5.2f`, `24.4f`, `52.6f`, and `100f` as `"0%"`, `"5%"`, `"24%"`, `"53%"`, and `"100%"`.
  • Verify `ZoneDistributionStackedBarMath.minRequiredSegmentWidthDp` and `canFitPercentageLabel` return `true` when `segmentWidthDp >= labelText.length * charWidthDp + horizontalPaddingDp` (e.g., `"24%"` at `24dp`..`60dp`) and `false` when `segmentWidthDp` is too narrow (e.g., `"5%"` at `8dp`, `"24%"` at `15dp`, or empty label).
  2. *Component Source Contract & 9-Language Localization Parity Audit (`ZoneDistributionStackedBarTest.kt`, `ZoneDistributionStackedBarLocalizationTest.kt`)*:
  • Verify `ZoneDistributionStackedBar.kt` uses `BoxWithConstraints(modifier = Modifier.weight(entry.percentage)` in the percentage row beneath the bar, `ZoneDistributionStackedBarMath.formatSegmentPercentage(entry.percentage)`, `ZoneDistributionStackedBarMath.canFitPercentageLabel`, `MaterialTheme.typography.labelSmall`, `maxLines = 1`, `softWrap = false`, and `didOverflowWidth` overflow guard.
  • Verify `ZoneDistributionStackedBarLocalizationTest` passes across all 9 supported application locales.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-362: **Critical Power (CP), W', and Skiba 1-Hz W'bal Athlete Profile, Live Cockpit, Aftermath & 9-Language Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-399`
- **Component**: **Critical Power (CP), W', and Skiba 1-Hz W'bal Athlete Profile, Live Cockpit, Aftermath & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3862``
- **Test Description**:
  1. *Skiba 1-Hz $W'_{\text{bal}}$ & 2-Point CP/W' Solver Math Tests (`PowerPhysiologyCalculatorTest.kt`, `AthleteMetricsDtoAndMathTest.kt`)*:
  • Verify `PowerPhysiologyCalculator.estimateCpAndWPrime` accurately solves $W_i = P_i \cdot t_i = CP \cdot t_i + W'$ for valid 2-point efforts and returns `null` for degenerate inputs ($t_2 \le t_1$, $P_1 \le P_2$, or non-positive $CP$/$W'$).
  • Verify Skiba's 1-Hz depletion ($P > CP$), clamping at $0\text{ J}$ (never negative), exponential reconstitution ($P \le CP$ with $\tau_{W'} = 546 e^{-0.01(CP - P)} + 316$), and summary extrema (`minWPrimeBalKj`, `minWPrimeBalPct`, `isEmptyTankWarning` when $< 10\%$).
  2. *Athlete Profile Sync & Live Cockpit `CyclingPowerMetricsDevice` Tests (`AthleteMetricsRepositorySyncTest.kt`, `CyclingPowerMetricsDeviceTest.kt`, `SensorCategoryAndSelectorContractTest.kt`)*:
  • Verify `AthleteMetricType.CP_WATTS` (`50..1000`) and `AthleteMetricType.W_PRIME_KJ` (`5..60`) validation and unidirectional `SettingsDataStore` synchronization.
  • Verify `CyclingPowerMetricsDevice` emits `W_PRIME_BAL` in kJ on every 1-Hz tick, depletes above $CP$, recharges below $CP$ and during pauses, and resets to $W'$ on `resetSession()`.
  • Verify `SensorCategoryAndSelectorContractTest` passes with `44` total sensors (`14` in `POWER_AND_PEDALING`).
  3. *9-Language Localization & Source Contract Audit (`AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify all new string keys exist and are non-blank with matching positional specifiers across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-363: **Efficiency Factor (EF) & Aerobic Decoupling (Pw:Hr / Pa:Hr) Calculation, DTO Symmetry & 9-Language Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-400`
- **Component**: **Efficiency Factor (EF) & Aerobic Decoupling (Pw:Hr / Pa:Hr) Calculation, DTO Symmetry & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3861``
- **Test Description**:
  1. *Pure Domain Efficiency Factor & Aerobic Decoupling Math Tests (`PowerPhysiologyCalculatorTest.kt`)*:
  • Verify `calculateNormalizedPower` computes 30s rolling 4th-power average (with growing $1..29\text{ s}$ warm-up window) and handles empty/null/zero inputs safely.
  • Verify `calculateEfficiencyFactorPower` ($\text{NP} / \text{Avg\_HR}$ in $\text{W/bpm}$) and `calculateEfficiencyFactorSpeed` ($(\text{Speed}_{\text{m/s}} \times 60.0) / \text{Avg\_HR}$ in $\text{m/min/bpm}$).
  • Verify `calculateAerobicDecouplingFromEf` and `computeAerobicEfficiencyAndDecoupling` split paired active samples into $H_1$ and $H_2$, compute $\text{Decoupling }\% = ((\text{EF}_1 - \text{EF}_2) / \text{EF}_1) \times 100.0$, and classify $< 5.0\%$ as coupled (`isHighDrift = false`) and $\ge 5.0\%$ as high cardiac drift (`isHighDrift = true`) across cycling (`Pw:Hr`) and running (`Pa:Hr`).
  2. *Rule 20 DTO & Snapshot Mapping Symmetry Tests (`WorkoutDataMappingSymmetryTest.kt`)*:
  • Verify `WorkoutDataMapper.fromCursor` (single & batch) and `WorkoutDataMapper.fromSnapshot` produce identical `efficiencyFactor`, `aerobicDecouplingPct`, `isPowerBasedEfficiency`, and `isAerobicDecouplingHighDrift` values on `WorkoutData` and `WorkoutDetailsData`.
  3. *9-Language Localization Parity Audit (`AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify all new EF and Aerobic Decoupling string resources exist and are non-blank with matching positional specifiers across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-364: **Performance Management Chart (PMC) Impulse-Response Math, Multi-Sport hrTSS Fallback & 9-Language Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-401`
- **Component**: **Performance Management Chart (PMC) Impulse-Response Math, Multi-Sport hrTSS Fallback & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3859``
- **Test Description**:
  1. *Pure Domain PMC & Multi-Sport $\text{hrTSS}$ Unit Tests (`PowerPhysiologyCalculatorTest.kt`)*:
  • Verify `calculateHrTss` returns `100.0` for `3600 s` at threshold HR ($HR_r = 0.85$) and scales non-linearly via Banister's $\text{TRIMP}$ ($HR_r \cdot e^{1.92 \cdot HR_r}$).
  • Verify `resolveWorkoutTrainingLoad` follows the 3-tier hierarchy: stored `powerTss` -> power-derived `TSS` (`NP` / `avgPower` + `activeTimeSeconds` + `ftpWatts`) -> `hrTSS` (`avgHrBpm` + `activeTimeSeconds` + `hrRestBpm` / `hrMaxBpm`).
  • Verify `computePerformanceManagementChart` aggregates daily training stress $TSS_t$ across calendar days, fills rest days with $0.0$, and computes $\text{CTL}_t = \text{CTL}_{t-1} + (TSS_t - \text{CTL}_{t-1}) / 42.0$, $\text{ATL}_t = \text{ATL}_{t-1} + (TSS_t - \text{ATL}_{t-1}) / 7.0$, $\text{TSB}_t = \text{CTL}_{t-1} - \text{ATL}_{t-1}$, $\text{ACWR}_t = \text{ATL}_t / \text{CTL}_t$, $\text{RampRate}_t = \text{CTL}_t - \text{CTL}_{t-7}$, `TsbReadinessZone`, `AcwrZone`, and `isAggressiveRampUp` ($> 8.0\text{ CTL/wk}$).
  2. *UI Contract & 9-Language Localization Parity Audit (`AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify `AthleteProfileScreen.kt` defines and invokes `PerformanceManagementChartCard` and `PerformanceManagementChartCanvas`.
  • Verify all 17 `athlete_profile_pmc_*` string keys exist and are non-blank with matching positional format specifiers across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-365: **Power-Based Training Metrics (NP, IF, TSS, Work kJ, W/kg, VI) Symmetric DTO Mapping, Aftermath UI Contract & 9-Language Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-402`
- **Component**: **Power-Based Training Metrics (NP, IF, TSS, Work kJ, W/kg, VI) Symmetric DTO Mapping, Aftermath UI Contract & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3858``
- **Test Description**:
  1. *Rule 20 DTO & Snapshot Mapping Symmetry Tests (`WorkoutDataMappingSymmetryTest.kt`)*:
  • Verify `WorkoutDataMapper.fromCursor` (single & batch) and `WorkoutDataMapper.fromSnapshot` produce identical `normalizedPowerWatts`, `intensityFactor`, `tss`, `workKj`, `avgPowerToWeight`, `maxPowerToWeight`, and `variabilityIndex` values on both `WorkoutData` and `WorkoutDetailsData`.
  • Verify `resolvePowerTrainingSummary` prioritizes `ExtremaType.END` over `ExtremaType.AVG` for cumulative session metrics (`NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`), computes `variabilityIndex = NP / Avg_Power`, and provides on-the-fly fallback derivation from `avgPowerWatts`, `activeTimeSec`, `effectiveFtpWatts`, and `effectiveWeightKg`.
  2. *Aftermath UI Contract & 9-Language Localization Parity Audit (`AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify `WorkoutDetails.kt` renders the Power Training Metrics summary section using `workout_power_metrics_*` string resources.
  • Verify all 6 `workout_power_metrics_*` string keys exist and are non-blank with matching positional format specifiers across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-366: **Physiology & Training Models Advanced Tuning Category, Consumer UI Gating, < 400 Line Modularity & 9-Language Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-403`
- **Component**: **Physiology & Training Models Advanced Tuning Category, Consumer UI Gating, < 400 Line Modularity & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3929``
- **Test Description**:
  1. *TuningConfig Defaults, Accordion Enum & Modularity Tests (`AdvancedTuningAccordionTest.kt`, `AdvancedTuningModularityTest.kt`, `AdvancedTuningScreenTest.kt`)*:
  • Verify `TuningConfig()` defaults `enableCogganPowerMetrics = true`, `enablePmcModel = false`, `enableAerobicEfficiencyModel = false`, and `enableCriticalPowerModel = false`.
  • Verify `TuningSection.entries` includes `PHYSIOLOGY_MODELS`, `PhysiologyModelsSection.kt` exists in `ui/settings/tuning/categories/` and is wired into `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`, and every `.kt` file under `ui/settings/tuning/` has strictly `< 400` lines.
  2. *Consumer UI Gating & Sensor Filter Contract Tests (`PhysiologyModelsVisibilityContractTest.kt`)*:
  • Verify `WorkoutDetails.kt` gates `NP`, `IF`, `TSS`, `VI` on `enableCogganPowerMetrics` while keeping `workKj` and `avgPowerToWeight` (`W/kg`) unconditionally visible when power/work data exists; gates `EF` and `Pw:Hr` / `Pa:Hr` on `enableAerobicEfficiencyModel`; and gates `minWPrimeBalKj` and `isEmptyTankWarning` on `enableCriticalPowerModel`.
  • Verify `AthleteProfileScreen.kt` gates `CP` and `W'` summary cards, `PlotMetricType.CP` / `PlotMetricType.W_PRIME` plot selector chips, and `CP_WATTS` / `W_PRIME_KJ` history filter chips on `enableCriticalPowerModel`, and gates `PerformanceManagementChartCard` on `enablePmcModel`.
  • Verify `TrackOnMapScreen.kt`, `MapDetailLayout.kt`, and `WorkoutSummary.kt` gate `TelemetryMetricType.W_PRIME_BAL` telemetry graphs on `enableCriticalPowerModel`.
  • Verify `EditSensorFieldViewModel.kt` filters `NORMALIZED_POWER`, `INTENSITY_FACTOR`, and `TSS` when `!enableCogganPowerMetrics` and `W_PRIME_BAL` when `!enableCriticalPowerModel`.
  3. *9-Language Localization Parity Audit (`PhysiologyModelsVisibilityContractTest.kt`)*:
  • Verify all new `tuning_cat_physiology_*` and `tuning_physiology_*` string keys exist and are non-blank with matching positional format specifiers across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-367: **WorkoutDetails Section 5.10 AltitudeRow + MetricItem Grouped Row Layout & 9-Language Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-404`
- **Component**: **WorkoutDetails Section 5.10 AltitudeRow + MetricItem Grouped Row Layout & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3930``
- **Test Description**:
  1. *Section 5.10 Grouped Row Structure & Anti-Pattern Removal Contract Tests (`PhysiologyModelsVisibilityContractTest.kt`, `AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify `WorkoutDetails.kt` contains zero `FlowRow` and zero `surfaceContainerHighest` pill badge usages (`docs/design_guidelines.md` Section 5.10).
  • Verify `WorkoutDetails.kt` defines and invokes `WorkoutDomainRow`, `PowerTrainingMetricsSection`, `AerobicEfficiencyRow`, and `AnaerobicBatteryRow` using `24.dp` domain icons (`R.drawable.ic_power`, `R.drawable.ic_cardio_load`, `R.drawable.ic_battery_full`), `Spacer(modifier = Modifier.width(20.dp))`, and equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns.
  • Verify `PowerTrainingMetricsSection` gates `NP`, `IF`, `TSS`, and `VI` on `enableCogganPowerMetrics` while keeping `workKj` and `avgPowerToWeight` (`W/kg`) unconditionally visible in their own `MetricItem` row.
  • Verify `AerobicEfficiencyRow` is gated on `enableAerobicEfficiencyModel && data.efficiencyFactor != null` and renders 2 equal-weight `MetricItem` columns (`EF` and `Pw:Hr` / `Pa:Hr` with Coupled/High Drift `secondaryValue`).
  • Verify `AnaerobicBatteryRow` is gated on `enableCriticalPowerModel && data.minWPrimeBalKj != null && data.minWPrimeBalPct != null` and renders 2 equal-weight `MetricItem` columns (`Min W'bal` in `kJ` and `Min W'bal %` with Empty Tank `secondaryValue`).
  2. *9-Language Localization Parity Audit (`AthleteProfileContractAndLocalizationTest.kt`)*:
  • Parse all 9 `values*/strings.xml` files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) and verify that all new domain/metric label and value format keys exist and are non-blank with matching positional format specifiers across all 9 locales.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-368: **Profile & Zones in Training Drawer Section and Collapsible My Stuff Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-405`
- **Component**: **Profile & Zones in Training Drawer Section and Collapsible My Stuff Verification Suite**
- **Jira**: ``ATT-3928``
- **Test Description**:
  1. *Drawer Structure, Training Group Ordering & Collapsible My Stuff Tests (`NavigationDrawerStateTest.kt`, `AthleteProfileContractAndLocalizationTest.kt`)*:
  • Query `createDrawerGroups(R.string.tab_start)` by `titleRes` (`groups.first { it.titleRes == ... }`) rather than brittle index.
  • Verify `R.string.drawer__training` has `collapsible == false` and places `R.id.drawer_athlete_profile` (`R.drawable.ic_athlete_profile`, `R.string.drawer_athlete_profile`) as its 4th destination item (`[drawer_start_tracking, drawer_workouts, drawer_periods, drawer_athlete_profile]`).
  • Verify `R.string.drawer__my_stuff` has `collapsible == true` and contains strictly 3 destination items: `[R.id.drawer_my_sensors, R.id.drawer_equipment, R.id.drawer_sport_types]`.
  • Verify non-collapsible groups (`Training` = 4 destination items, `Maps` = 5 destination items) yield `9` default visible `DrawerItemConfig` rows (`groups.filter { !it.collapsible }.sumOf { it.items.size } == 9`, separate from the `5` section header rows) and collapsible groups (`My Stuff` = 3, `Online Communities` = 4, `Settings` = 6) yield `13` hidden destination items (`9 + 13 = 22` total unique items across `5` groups).
  • Verify `isDrawerGroupInitiallyExpanded(myStuffGroup, selectedItemId)` returns `false` when `R.id.drawer_start_tracking` is selected and iterates exhaustively over `myStuffGroup.items` (plus consolidated equipment aliases `R.id.drawer_bikes` and `R.id.drawer_shoes`) asserting `true` for every item.
  2. *9-Language Localization Parity Audit (`AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify `drawer_athlete_profile`, `drawer__training`, and `drawer__my_stuff` exist across all 9 supported application locales.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-369: **Transparent Inner Dial Fill (#00000000) on bsport_other.xml Stopwatch Vector Icon Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-406`
- **Component**: **Transparent Inner Dial Fill (`#00000000`) on `bsport_other.xml` Stopwatch Vector Icon Verification Suite**
- **Jira**: ``ATT-3926``
- **Test Description**:
  1. *DOM & Palette Contract Tests (`VectorAssetResolutionContractTest.kt`)*:
  • Verify `bsport_other.xml` parses as a valid `<vector>` DOM document where every `<path>` element — including `path[0]` (the main stopwatch dial circle `M 31.63,110.42 A 63.87,63.87 ...`) — declares `android:fillColor="#00000000"`.
  • Verify `bsport_other.xml` does not contain `#F0F0F0`.
  • Verify `bsport_other.xml` preserves `#FFC700` (gold ring and stems), `#000090` (blue left button cap), `#FF0000` (red center button cap), and `#009000` (green right button cap) strokes.
  2. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-370: **Smart Auto-Collapse & Inline Expansion of Export Status Unit and Contract Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-407`
- **Component**: **Smart Auto-Collapse & Inline Expansion of Export Status Unit and Contract Verification Suite**
- **Jira**: ``ATT-3938``
- **Test Description**:
  1. *Pure Domain Classification & Summary Formatting Unit Tests (`ExportStatusCollapseTest.kt`)*:
  • Verify `isFullySucceeded` and `needsAttentionOrInProgress` across all combinations of `hasContent`, `waitingLine`, `runningLine`, `succeededLine`, and `failedLine`.
  • Verify `formatCollapsedSucceededSummary` returns `"✅ File, Dropbox, Communities"` when all three succeeded, `"✅ File, Dropbox"` when `Communities` has a failure/ongoing job, and `null` when no group is fully succeeded.
  • Verify `filterAttentionGroups` returns `emptyList()` when all groups succeeded, and returns only groups with `waitingLine`, `runningLine`, or `failedLine` when present.
  2. *UI Source Contract & Bottom Sheet Integrity Tests (`ExportStatusCollapseTest.kt`, `ModalBottomSheetDialogsIntegrityTest.kt`)*:
  • Verify `ExportStatusGroup.kt` uses `AnimatedVisibility`, `Icons.Default.ExpandMore`, `Icons.Default.ExpandLess`, `formatCollapsedSucceededSummary`, and `filterAttentionGroups`, and preserves `ExportDetailsDialog` (`ModalBottomSheetDialogsIntegrityTest`).
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-371: **Individually Configurable Workout Detail Tabs, Conflict-Free Layout Modes, Smart Gesture Isolation, Auto-Hiding Empty Tabs & 9-Language Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-408`
- **Component**: **Individually Configurable Workout Detail Tabs, Conflict-Free Layout Modes, Smart Gesture Isolation, Auto-Hiding Empty Tabs & 9-Language Parity Verification Suite**
- **Jira**: `ATT-3920`, `ATT-5483`, `ATT-5655`
- **Test Description**:
  1. *Domain Model, Default 4-Tab Archetype & Serialization Unit Tests (`WorkoutDetailTabsTest.kt`)*:
  • Verify `WorkoutDetailSectionType.entries.size == 11`, `WorkoutSectionType.entries.size == 10`, and `WorkoutDetailTabConfig.DEFAULT_TABS` defines the 4 default tabs (`OVERVIEW`: `[DETAILS, PHOTOS, DESCRIPTION, EXTREMA, EXPORT_STATUS]`, `MAP_AND_GRAPHS`: `[MAP, ELEVATION, CHARTS]`, `ZONES`: `[ZONES]`, `LAPS_AND_SEGMENTS`: `[LAPS, STRAVA]`).
  • Verify `serializeTabs` and `deserializeTabs` round-trip default and custom tabs with `v2|` prefix, auto-migrate legacy `v1|` tabs lacking `PHOTOS` by injecting `PHOTOS` into `tab_overview`, escape delimiters, ignore unknown tokens, deduplicate sections, and fall back to `DEFAULT_TABS` on null/blank/invalid inputs.
  2. *Layout Mode, Map Pinning, Gesture Isolation, Auto-Hiding & CRUD Unit Tests (`WorkoutDetailTabsTest.kt`)*:
  • Verify `normalizedSections` pins `MAP` at index `0` whenever `MAP` is present and `layoutMode` resolves to `MODE_A_CARD_SCROLL` (`MAP` absent), `MODE_B_SPLIT_MAP` (`MAP` + other sections), or `MODE_C_FULL_MAP` (`MAP` only).
  • Verify `requiresHorizontalGestureIsolation` returns `true` for tabs containing active `MAP`, `ELEVATION`, or `CHARTS` and `false` for card-only tabs.
  • Verify `filterVisibleWorkoutDetailTabs` hides tabs with zero available sections for the current workout while keeping at least 1 tab.
  • Verify `addCustomTab`, `renameTab`, `moveTab`, `deleteTab` (min 1 tab), `toggleSectionInTab`, and `moveSectionInTab` (`MAP` pinned at index `0`).
  3. *UI Integration, Tuning Modularity (`< 400` lines) & 9-Language Parity Audit (`WorkoutDetailTabsTest.kt`)*:
  • Verify `TrackOnMapScreen.kt` routes to `WorkoutDetailTabbedContent` and `WorkoutDetailTabsConfigSheet` while preserving legacy `MapDetailLayout` fallback contracts, `MyPreferenceManager.kt` exposes `workoutDetailTabsOrderFlow`, and every `.kt` file under `ui/settings/tuning/` has `< 400` lines.
  • Verify that in `WorkoutDetailTabbedContent`, clicking a lap in `WorkoutDetailSectionType.LAPS` propagates `onLapClick(lap)`, triggering `LapEditBottomSheet` as a modal overlay alongside tabbed content without unmounting or resetting the active tab (`Rule 47`).
  • Verify all new `workout_detail_tab_*`, `workout_detail_section_*`, and `workout_detail_tabs_config_*` string resources exist and are non-blank across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-372: **Default BIKE_POWER Cockpit Tabs (Standard, Power, Map, Laps), W_PRIME_BAL Catalog Inclusion, Live Cockpit Gating & 9-Language Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-409`
- **Component**: **Default `BIKE_POWER` Cockpit Tabs (`Standard`, `Power`, `Map`, `Laps`), `W_PRIME_BAL` Catalog Inclusion, Live Cockpit Gating & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3921`, `ATT-4447``
- **Test Description**:
  1. *`ActivityType.BIKE_POWER` Sensor Catalog Unit Test (`BikePowerCockpitDefaultsAndGatingTest.kt`)*:
  • Verify `ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, null)` contains `POWER`, `POWER_TO_WEIGHT`, `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `WORK_KJ`, `TSS`, `W_PRIME_BAL`, `PEDAL_POWER_BALANCE`, and `TORQUE`.
  2. *`TrackingViewsDbHelper` 4-Tab `BIKE_POWER` Default Layouts & Toggle Matrix Tests (`BikePowerCockpitDefaultsAndGatingTest.kt`, `TrackingViewsDatabaseManagerTest.kt`)*:
  • Verify `onCreate` seeds 3 tabs (`layoutNr` 1, 2, 3) for `GENERIC_HR`, `RUN_SPEED_AND_CADENCE`, and `BIKE_SPEED_AND_CADENCE`, and seeds 4 tabs (`layoutNr` 1 = Standard, `layoutNr` 2 = Power, `layoutNr` 3 = Map, `layoutNr` 4 = Laps) for `BIKE_POWER`.
  • Verify `createViewMap` for `BIKE_POWER` produces the 6-row Standard layout (`TIME_ACTIVE`/`TIME_OF_DAY`, `SPEED_mps`/`DISTANCE_m`, `HR`/`CADENCE`, `POWER` `HUGE`, `POWER_TO_WEIGHT`/`NORMALIZED_POWER`, `ALTITUDE`/`ASCENT`).
  • Verify `getDefaultPowerRowDataList()` produces the 6-row Power tab layout (`TIME_ACTIVE`/`HR`/`CADENCE`, `POWER` `HUGE`, `POWER_TO_WEIGHT`/`NORMALIZED_POWER` `LARGE`, `INTENSITY_FACTOR`/`TSS`/`WORK_KJ` `NORMAL`, `W_PRIME_BAL` `LARGE`, `PEDAL_POWER_BALANCE` `SMALL` spanning full width without `TORQUE`).
  • Verify `getDefaultMapRowDataList(ActivityType.BIKE_POWER)` and `getDefaultLapRowDataList(ActivityType.BIKE_POWER)` include `POWER` and `POWER_TO_WEIGHT`.
  3. *Live-Cockpit Gating, Empty-Row Zero-Height Collapse & Custom-Layout Coordinate Preservation Tests (`BikePowerCockpitDefaultsAndGatingTest.kt`)*:
  • Verify `isSensorTypeVisibleForTuning` and `filterVisibleFieldsForTuning` across `TuningConfig` states (`enableCogganPowerMetrics`, `enableCriticalPowerModel`) and `ScreenMode`s (`TRACKING`, `PREVIEW`, `CONFIGURATION`), including the Empty-Row scenario (verifying that when all fields in a row are gated off, that `rowNr` is completely removed from `visibleFields.groupBy { it.rowNr }` in the `Column` layout so row height collapses to `0`) and a custom user layout simulation verifying that `rowNr`/`colNr` coordinates of remaining fields are never mutated and `ScreenMode.CONFIGURATION` returns all fields unfiltered.
  4. *9-Language Localization Parity Audit (`BikePowerCockpitDefaultsAndGatingTest.kt`)*:
  • Parse all 9 `values*/strings.xml` files and verify `tab_power` exists and is non-blank across all 9 locales.
  5. *All-Gated Empty Grid & Null `TuningConfig` Initial-Boot Fallback Tests (`TST-UI-372.5`, `BikePowerCockpitDefaultsAndGatingTest.kt`)*:
  • Verify that when all fields in a tab are gated off (`visibleFields.isEmpty()`), `filterVisibleFieldsForTuning` and `fieldsByRow` evaluate to `emptyList()` and `emptyMap()` gracefully without throwing `IndexOutOfBoundsException` or `IllegalArgumentException`, and `SensorGridScreen.kt` maintains a safe minimum container height (`defaultMinSize(minHeight = 48.dp)`).
  • Verify that passing `tuningConfig = null` to `isSensorTypeVisibleForTuning` and `filterVisibleFieldsForTuning` safely defaults to `TuningConfig()` during initial app boot.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-373: **Timeout-Resilient Cockpit Tab Navigation Collector, Lifecycle-Safe Dialog Dispatch & Context.findActivity() Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-410`, `REQ-UI-163`
- **Component**: **Timeout-Resilient Cockpit Tab Navigation Collector, Lifecycle-Safe Dialog Dispatch & `Context.findActivity()` Verification Suite**
- **Jira**: ``ATT-4000``
- **Test Description**:
  1. *Post-Timeout Navigation Collector Survival Unit Test (`TST-UI-373.1`, `TrackingTabsNavigationTimeoutTest.kt`)*:
  • Collect `SharedFlow<TabNavigationEvent>` using `navigateToPagerTarget` in `ScreenMode.TRACKING` with `pageCount = 1`, emit `TabNavigationEvent.NavigateTo(2)` (`target = 3`), advance virtual time past the 2000 ms timeout, then update `pageCount = 4` and emit `TabNavigationEvent.NavigateTo(0)` (`target = 1`).
  • Verify that the first event times out cleanly (`returned == false`, `lastKnownPage == 0`, collector coroutine remains active) and the second event navigates the pager to page `1` (`lastKnownPage == 1`, `scrolledPages == listOf(1)`).
  2. *Delayed Page-Count Readiness & Scroll Fallback Unit Test (`TST-UI-373.2`, `TrackingTabsNavigationTimeoutTest.kt`)*:
  • Verify `navigateToPagerTarget` suspends while `pageCount <= target`, resumes immediately when `pageCountFlow` emits `count > target` within the 2000 ms window, updates `lastKnownPage = target`, and falls back to `scrollToPage(target)` if `animateScrollToPage(target)` throws an exception (while propagating outer `CancellationException`).
  3. *`Context.findActivity()`, `canShowDialogFragments()` & Source Contract Test (`TST-UI-373.3`, `TrackingTabsNavigationTimeoutTest.kt`)*:
  • Verify `Context.findActivity()` unwraps nested `ContextWrapper` instances to return `AppCompatActivity` and returns `null` for non-Activity `Context`s.
  • Verify `canShowDialogFragments()` returns `false` when `activity == null`, `isFinishing`, `isDestroyed`, or `supportFragmentManager.isStateSaved == true`, and `true` when active and state is not saved.
  • Verify `TrackingTabsScreen.kt` uses `withTimeoutOrNull`, `repeatOnLifecycle(Lifecycle.State.STARTED)`, `findActivity()`, and `canShowDialogFragments()`, and contains zero `LocalContext.current as androidx.appcompat.app.AppCompatActivity` or `CancellationException("PageCountReady")` patterns.
  4. *9-Language Localization & Clean-Room Full Suite Regression (`TST-UI-373.4`, `TST-UI-373.5`)*:
  • Execute `TranslationParityTest` and `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-374: **Cockpit Sensor Grid Configurable Section Dividers, Automated Row Shift & 9-Language Localization Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-411`
- **Component**: **Cockpit Sensor Grid Configurable Section Dividers, Automated Row Shift & 9-Language Localization Verification Suite**
- **Jira**: ``ATT-4055``
- **Test Description**:
  1. *SQLite Schema Creation & Upgrade Migration Test (`TST-UI-374.1`, `TrackingViewsDatabaseManagerTest.kt`)*:
  • Verify that on fresh database creation (`onCreate`) and upgrade from v13 to v14 (`onUpgrade`), `SectionDividersTable` exists with columns `_id`, `ViewId`, `RowNr`, `Title` and unique constraint on `(ViewId, RowNr)`.
  2. *Section Divider CRUD, Row Shifting on Insert & Grid Normalization Test (`TST-UI-374.2`, `TrackingViewsDatabaseManagerTest.kt`)*:
  • Insert section dividers at rows 2 and 4 for a test tab; verify `getSectionDividers` retrieves them accurately.
  • Insert a new row at row 2 (`colNr = -1`) via `insertSensorFieldAt`; verify that dividers previously at row 2 and 4 shift to rows 3 and 5.
  • Trigger `normalizeGrid`; verify divider row numbers remain synchronized with compacted row indices, and deleting a tab removes its dividers.
  3. *TrackingViewsRepository & TrackingViewModel Flow Emission Test (`TST-UI-374.3`, `TrackingViewsRepositoryTest.kt`)*:
  • Verify `getSectionDividersFlow` emits updated divider lists whenever `configUpdateTrigger` fires.
  • Verify `TrackingViewModel` maps database dividers into `TrackingScreenState.sectionDividers`.
  4. *SensorGridScreen Visual Divider Rendering Test (`TST-UI-374.4`, `SensorGridScreenLayoutTest.kt`)*:
  • In `ScreenMode.TRACKING`, verify an unlabeled divider renders as a clean horizontal line with minimal vertical overhead ($\le 20\text{ dp}$).
  • In `ScreenMode.TRACKING`, verify a labeled divider renders uppercase mini-title with flanking divider rules.
  5. *9-Language Localization Parity & Non-Blank String Test (`TST-UI-374.5`, `SensorGridLocalizationTest.kt`)*:
  • Verify all section divider control, dialog, and preset chip strings exist and are non-blank across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  6. *Clean-Room Full Suite Regression Execution (`TST-UI-374.6`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-375: **Buffered One-Off Navigation Channels across STOPPED Lifecycle and Gesture Cancellation Safety Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-412`, `REQ-UI-163`, `REQ-UI-410`
- **Component**: **Buffered One-Off Navigation Channels across STOPPED Lifecycle and Gesture Cancellation Safety Verification Suite**
- **Jira**: ``ATT-4097``
- **Test Description**:
  1. *Channel Background Buffering across `STOPPED` Lifecycle Test (`TST-UI-375.1`, `TrackingTabsNavigationTimeoutTest.kt`)*:
  • Emit navigation event (`TabNavigationEvent.NavigateTo(0)` or `TabNavigationEvent.EditDevice(42L)`) in `TrackingTabsViewModel` while no collector is active (simulating an Activity in `STOPPED` where `repeatOnLifecycle(STARTED)` cancels collection).
  • Start a collector simulating resumption to `Lifecycle.State.STARTED`.
  • Verify that the buffered event is received and processed immediately upon subscription.
  2. *ControlTrackingViewModel Background Buffering Test (`TST-UI-375.2`, `ControlTrackingViewModelTest.kt`)*:
  • Emit `ControlNavigation.ToEditDevice` and `ControlNavigation.ShowRecoveryDialog` in `ControlTrackingViewModel` with zero subscribers.
  • Start collecting `viewModel.navigationEvent`.
  • Verify each event is received in FIFO order without loss.
  3. *Exactly-Once Consumption & Re-Subscription Cleanliness Test (`TST-UI-375.3`, `TrackingTabsNavigationTimeoutTest.kt`)*:
  • After the first collector consumes the event, cancel collection (simulating screen stop/recreation).
  • Start a second collector.
  • Verify that zero replay events are received (unlike `SharedFlow(replay = 1)`).
  4. *User Gesture Cancellation Resilience Test (`TST-UI-375.4`, `TrackingTabsNavigationTimeoutTest.kt`)*:
  • In `navigateToPagerTarget`, simulate `animateScrollToPage(target)` throwing `kotlinx.coroutines.CancellationException` (e.g. user manual swipe interrupt).
  • Verify `navigateToPagerTarget` returns cleanly without throwing an unhandled exception.
  • Verify that fallback `scrollToPage(target)` is NOT invoked, preserving the user's manual swipe position.
  5. *Non-Cancellation Exception Fallback Scroll Test (`TST-UI-375.5`, `TrackingTabsNavigationTimeoutTest.kt`)*:
  • In `navigateToPagerTarget`, simulate `animateScrollToPage(target)` throwing a standard `IllegalStateException` or `RuntimeException`.
  • Verify that fallback `scrollToPage(target)` IS executed.
  6. *Clean-Room Full Suite Regression Execution (`TST-UI-375.6`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-376: **Cockpit Sensor Field Insertion Atomicity, Tab Isolation, Divider Synchronization, and Rollback Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-413`
- **Component**: **Cockpit Sensor Field Insertion Atomicity, Tab Isolation, Divider Synchronization, and Rollback Verification Suite**
- **Jira**: ``ATT-4010``
- **Test Description**:
  1. *Multi-Tab Real SQLite Isolation Test (`TST-UI-376.1`, `TrackingViewsRealSqliteMigrationTest.kt`)*:
  • Initialize real SQLite database with Tab 1 (`viewId = 1`) and Tab 2 (`viewId = 2`), each populated with rows in `ROWS_TABLE` and records in `SECTION_DIVIDERS_TABLE`.
  • Invoke `insertSensorFieldAt(tabViewId = 1, rowNr = 1, colNr = -1, ...)` (insert new row in Tab 1).
  • Verify rows and dividers in Tab 1 shift down by 1.
  • Verify all rows and dividers in Tab 2 retain exact original `ROW_NR` and `COL_NR` values.
  2. *Transaction Rollback Resilience Test (`TST-UI-376.2`, `TrackingViewsRealSqliteMigrationTest.kt`)*:
  • Simulate failure or constraint error mid-operation during `insertSensorFieldAt`.
  • Verify the entire transaction rolls back completely and zero rows or dividers remain shifted or orphaned.
  3. *Contiguous Coordinate Normalization & Gapped Grid Healing Test (`TST-UI-376.3`, `TrackingViewsDatabaseManagerTest.kt`, `TrackingViewsRealSqliteMigrationTest.kt`)*:
  • Inject a fragmented/gapped database state (e.g. rows 1, 3, 5) with section dividers.
  • Invoke `insertSensorFieldAt` for new column and new row.
  • Verify `normalizeGrid(db, tabViewId)` is invoked on the active transaction database connection, asserting rows compact contiguously to 1, 2, 3 and section dividers re-map accurately.
  4. *Parameter, Boundary & Out-of-Bounds Row Validation Test (`TST-UI-376.4`, `TrackingViewsDatabaseManagerTest.kt`)*:
  • Invoke `insertSensorFieldAt` with non-positive values (`tabViewId <= 0` or `rowNr < 1`); verify method returns immediately without initiating database transactions or mutations.
  • Invoke `insertSensorFieldAt` with out-of-bounds row (`rowNr = 999`); verify field is inserted and compacted cleanly to `currentMaxRow + 1` by `normalizeGrid`.
  5. *9-Language Localization & Specifier Audit (`TST-UI-376.5`)*:
  • Audit `res/values*/strings.xml` confirming zero string modifications.
  6. *Clean-Room Regression Suite Execution (`TST-UI-376.6`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-377: **Workout Cards & Details Settings Streamlining, Collapsible Section Order, Authoritative Tab Activation, and 9-Language Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-414`
- **Component**: **Workout Cards & Details Settings Streamlining, Collapsible Section Order, Authoritative Tab Activation, and 9-Language Parity Verification Suite**
- **Jira**: `ATT-4043`, `ATT-5474`, `ATT-5655`
- **Test Description**:
  1. *Authoritative Tab Data Availability & Section Activation Test (`TST-UI-377.1`, `WorkoutDetailTabsTest.kt`)*:
  • Initialize `WorkoutDetailSectionAvailability` with `hasMap = true` and `hasElevation = true`, alongside `WorkoutDetailPreferences` with `showMap = false` and `showElevationProfile = false`.
  • Evaluate `availability.isSectionActive(WorkoutDetailSectionType.MAP, detailPrefs)` and `availability.isSectionActive(WorkoutDetailSectionType.ELEVATION, detailPrefs)`.
  • Assert both return `true`, confirming section activation is governed by data availability and tab configuration, rather than legacy boolean flags.
  2. *Single-Column Checkbox & Layout Structure Contract Test (`TST-UI-377.2`, `WorkoutMasksAndCardsLayoutTest.kt`)*:
  • Verify `WorkoutMasksAndCardsSection.kt` table header references `tuning_matrix_col_list` (50dp width) and does NOT contain `tuning_matrix_col_details`.
  • Verify feature rows contain a single checkbox for `feature.listChecked` and no second checkbox for details.
  3. *Collapsible Summary Reorder List Contract Test (`TST-UI-377.3`, `WorkoutMasksAndCardsLayoutTest.kt`)*:
  • Verify collapsible toggle state initialized to `false` (collapsed by default).
  • Verify references to `Icons.Default.KeyboardArrowDown` and `Icons.Default.KeyboardArrowUp` for expand/collapse visual feedback.
  • Verify reference to `R.string.workout_card_reorder_toggle` format string.
  4. *Scoped Zone Display Mode & Detail Tabs Action Contract Test (`TST-UI-377.4`, `WorkoutMasksAndCardsLayoutTest.kt`)*:
  • Verify `ZoneDisplayModeSelector` for Summary is placed in the Trainingsliste block (`workoutCardPrefs.zoneCardDisplayMode`).
  • Verify `ZoneDisplayModeSelector` for Details is placed in the Trainings-Details block (`workoutDetailPrefs.zoneCardDisplayMode`) alongside `WorkoutDetailTabsConfigSheet` launch button.
  5. *Modularity & 400-Line Limit Test (`TST-UI-377.5`, `WorkoutMasksAndCardsLayoutTest.kt`)*:
  • Count total lines of `WorkoutMasksAndCardsSection.kt`.
  • Assert line count is strictly < 400 lines.
  6. *9-Language Localization Parity Audit (`TST-UI-377.6`, `TranslationParityTest.kt`)*:
  • Inspect all 9 supported locale directories (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  • Verify `workout_card_reorder_toggle` and `settings_workout_details_title` exist in all 9 files with `%1$d` specifier match and no entity issues.
  7. *Workout Photos Settings, Responsive Viewport Sizing & Layout Contract Test (`TST-UI-377.7` / `ATT-5474`, `ATT-5655`, `WorkoutMasksAndCardsLayoutTest.kt`, `PhotoThumbnailSizeTest.kt`)*:
  • Verify `WorkoutSectionType.PHOTOS` and `WorkoutDetailSectionType.PHOTOS` inclusion, `DEFAULT_ORDER`, and bidirectional mapping.
  • Verify `WorkoutPhotoPreferencesComponents.kt` modularity maintains `WorkoutMasksAndCardsSection.kt` strictly < 400 lines.
  • Verify `PhotoThumbnailSize` enum values (`SMALL`, `MEDIUM`, `LARGE`), DataStore persistence in `WorkoutCardSectionPreferences` and `WorkoutDetailPreferences`, and responsive viewport-relative card dimensions in `WorkoutPhotoStrip.kt`.
  • Verify 9-language translation parity across all `settings_workout_photos_*` keys via `TranslationParityTest`.
  8. *Clean-Room Full Suite Regression Execution (`TST-UI-377.8`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-378: **WorkoutDetails Single-Line Power Row Layout, Domain Row Dividers, Priority Resolution, and 9-Language Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-415`
- **Component**: **WorkoutDetails Single-Line Power Row Layout, Domain Row Dividers, Priority Resolution, and 9-Language Parity Verification Suite**
- **Jira**: ``ATT-4042``
- **Test Description**:
  1. *Power Training Metrics Single-Line Presentation Unit Test (`TST-UI-378.1`, `WorkoutDetailsLayoutTest.kt`)*:
  • Test full Coggan scenario with `NP`, `IF`, `TSS`: verify resolution returns strictly 3 items: `[NORMALIZED_POWER, INTENSITY_FACTOR, TSS]`.
  • Test missing FTP scenario with `NP` (null `IF`/`TSS`): verify resolution returns strictly 1 item: `[NORMALIZED_POWER]` without basic physics fallback.
  • Test Coggan disabled scenario (`enableCogganPowerMetrics = false` or `NP = null`): verify resolution returns strictly empty list.
  • Test all-null scenario: verify resolution returns empty list.
  2. *WorkoutDetails Layout & Divider Source Contract Test (`TST-UI-378.2`, `WorkoutDetailsLayoutTest.kt`)*:
  • Parse `WorkoutDetails.kt` and assert `PowerTrainingMetricsSection` contains exactly one `Row(modifier = Modifier.fillMaxWidth())` call within `WorkoutDomainRow`.
  • Assert `WorkoutDetails` contains `HorizontalDivider` calls conditionally placed before each optional domain row (`AltitudeRow`, `PowerTrainingMetricsSection`, `AerobicEfficiencyRow`, `AnaerobicBatteryRow`).
  • Assert outer column uses `Arrangement.spacedBy(12.dp)`.
  • Assert `WorkoutDetails.kt` file length is strictly < 500 lines.
  3. *Section 5.10 & Physiology Visibility Contract Test (`TST-UI-378.3`, `PhysiologyModelsVisibilityContractTest.kt`)*:
  • Verify `WorkoutDetails.kt` contains zero `FlowRow` and zero `surfaceContainerHighest` calls.
  • Verify `WorkoutDomainRow`, `ic_power`, `ic_cardio_load`, and `ic_battery_full` remain properly referenced.
  4. *9-Language Localization Parity Audit (`TST-UI-378.4`, `AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify all 9 locales define power metric string resources and value format specifiers with 100% parity.
  5. *Clean-Room Full Suite Regression Execution (`TST-UI-378.5`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate with zero regressions.

### TST-UI-379: **W' Balance Battery Gauge, Mathematical Normalization, Layout Invariants, and 9-Language Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-416`
- **Component**: **W' Balance Battery Gauge, Mathematical Normalization, Layout Invariants, and 9-Language Parity Verification Suite**
- **Jira**: ``ATT-1296``
- **Test Description**:
  1. *Mathematical Normalization, Clamping & Thresholds Test (`TST-UI-379.1`, `WPrimeBatteryMathTest.kt`)*:
  • Verify percentage and fraction scaling across Normal, Low, Critical, and Depleted levels.
  • Verify boundary clamping to $[0.0f, 1.0f]$ and $[0, 100]$.
  • Verify stale transitions (>3000ms) and mid-session baseline mutation resilience with clamping to $[0.0, W'_{\text{new}}]$.
  2. *Device Hardening & Fallback Test (`TST-UI-379.2`, `CyclingPowerMetricsDeviceTest.kt`)*:
  • Verify `resetSession()` restores full $W'$ capacity when only $FTP$ is configured using `resolveEffectiveCpWatts()`.
  • Verify continuous 1-Hz depletion at $P > CP$ and exponential reconstitution at $P = 0\text{ W}$ during pause.
  • Verify null safety and fallback to `SettingsDataStore.DEFAULT_CYCLING_FTP` (`200` W).
  3. *Battery Gauge Layout & Stability Contract Test (`TST-UI-379.3`, `WPrimeBatteryGaugeBarLayoutTest.kt`)*:
  • Verify fixed 14.dp height slot across active, stale, and empty states.
  • Verify 3.dp end-cap boundary padding prevents clipping at 0% and 100% capacity.
  • Verify layout stability under system font scaling (1.0x, 1.5x, 2.0x) with `TextOverflow.Clip`.
  • Verify continuous animation updates with `tween(durationMillis = 300, easing = FastOutSlowInEasing)`.
  • Verify warning border color activation when `isWarning == true`.
  • Verify progressive disclosure gating collapses row to 0 when `enableCriticalPowerModel == false`.
  4. *9-Language Localization & Format Audit (`TST-UI-379.4`, `WPrimeBatteryLocalizationTest.kt`)*:
  • Verify 100% parity across all 9 application locales for `w_prime_battery_pct` (`%1$d%%`) and `w_prime_warning_low_battery` (`W\' Battery Low (<20%)`).
  5. *Clean-Room Regression Suite Execution (`TST-UI-379.5`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate.

### TST-UI-380: **Distance Interval Filtering (Min/Max Range and Preset Intervals) Verification Suite across Routes and Favorite Routes** [Verified]
- **Requirement(s)**: `REQ-UI-417`
- **Component**: **Distance Interval Filtering (Min/Max Range and Preset Intervals) Verification Suite across Routes and Favorite Routes**
- **Jira**: ``ATT-4291``
- **Test Description**:
  1. *RouteFilterCriteria Distance Interval & Count Test (`TST-UI-380.1`, `RouteFilterCriteriaTest.kt`)*:
  • Test bounded interval `[25 km, 50 km]` (`min = 25000.0, max = 50000.0`): assert 25 km, 45 km, 50 km match; 5 km, 10 km, 80 km fail.
  • Test upper bound only `< 10 km` (`min = null, max = 10000.0`): assert 5 km, 10 km match; 25 km, 45 km, 50 km, 80 km fail.
  • Test lower bound only `> 50 km` (`min = 50000.0, max = null`): assert 50 km, 80 km match; 5 km, 10 km, 25 km, 45 km fail.
  • Test `activeFilterCount`: assert `minOnly` gives 1, `maxOnly` gives 1, and `bothBounds` gives 1 (strictly 1 dimension for distance).
  2. *RouteFilterCriteria JSON Serialization Roundtrip & Backward Compatibility Test (`TST-UI-380.2`, `RouteFilterCriteriaTest.kt`)*:
  • Call `toJson()` on a two-bound instance, deserialize with `RouteFilterCriteria.fromJson()`, and assert properties match.
  • Deserialize legacy JSON lacking `maxDistanceMeters`: assert `maxDistanceMeters = null` and `minDistanceMeters` is preserved without errors.
  3. *ClusterFilterCriteria Distance Interval & Count Test (`TST-UI-380.3`, `ClusterFilterCriteriaTest.kt`)*:
  • Test bounded interval `[10 km, 35 km]` (`min = 10000.0, max = 35000.0`) on cluster `refDistance`: assert 12.5 km, 25 km, 35 km match; 8 km, 50 km, 75 km fail.
  • Test upper bound only `< 10 km`: assert 8 km matches; 12.5 km, 25 km fail.
  • Test lower bound only `> 50 km`: assert 50 km, 75 km match; 35 km fails.
  • Test `activeFilterCount`: assert setting min, max, or both adds strictly 1 to count.
  4. *ClusterFilterCriteria JSON Serialization Roundtrip & Backward Compatibility Test (`TST-UI-380.4`, `ClusterFilterCriteriaTest.kt`)*:
  • Execute JSON roundtrip with `maxDistanceMeters` and verify legacy JSON without `maxDistanceMeters` parses safely with null.
  5. *Active Filter Chips Row Distance Formatting & Dismissal Contract Test (`TST-UI-380.5`, `RouteFilterCriteriaTest.kt` / `ClusterFilterCriteriaTest.kt`)*:
  • Verify interval formatting produces `"$min - $max km"` (e.g. `"25 - 50 km"`), `"> $min km"` (e.g. `"> 25 km"`), and `"< $max km"` (e.g. `"< 50 km"`).
  • Verify decimal trimming: `12500.0` formats as `"12.5"`, whereas `25000.0` formats as `"25"`.
  • Verify dismissal contract: `criteria.copy(minDistanceMeters = null, maxDistanceMeters = null)` clears both distance bounds.
  6. *9-Language Localization & Specifier Audit (`TST-UI-380.6`, `TranslationParityTest.kt`)*:
  • Verify string resources (`filter_section_distance`, `filter_min_label`, `filter_max_label`, `filter_distance_interval_format`, `filter_min_distance_format`, `filter_max_distance_format`, `sort_elevation_gain`) exist across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with 100% format specifier parity.
  7. *Clean-Room Regression Suite Execution (`TST-UI-380.7`)*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-381: **Double Percent Symbol Removal & 9-Locale Percent Integrity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-418`
- **Component**: **Double Percent Symbol Removal & 9-Locale Percent Integrity Verification Suite**
- **Jira**: ``ATT-4292``
- **Test Description**:
  1. *W' Battery Warning Low Battery Double Percent Removal Test (`TST-UI-381.1`, `WPrimeBatteryLocalizationTest.kt`)*:
  • For each of the 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`), parse `w_prime_warning_low_battery`.
  • Assert `warning.contains("%")` is true.
  • Assert `warning.contains("%%")` is false.
  2. *Repository-Wide Non-Formatted String Percent Integrity Contract Test (`TST-UI-381.2`, `LocalizationPercentIntegrityTest.kt`)*:
  • Scan all XML string resources across all 9 locales using `LocalizationTestCache`.
  • For every `<string>` entry containing `%%`, verify that it contains a valid format specifier token (`%s`, `%d`, `%[0-9]+\$`, `%f`, etc.).
  • Assert that zero non-formatted strings contain `%%`.
  3. *5-Key 9-Locale Single Percent Parity Contract Test (`TST-UI-381.3`, `LocalizationPercentIntegrityTest.kt`)*:
  • For each of the 5 keys (`workout_decoupling_coupled`, `workout_decoupling_drift`, `workout_w_prime_min_pct_label`, `workout_w_prime_empty_tank`, `w_prime_warning_low_battery`) across all 9 locales:
  • Assert that the string exists, is not blank, contains `%`, and does not contain `%%`.
  • Verify exact expected text across all 45 localized entries.
  4. *Clean-Room Regression Suite Execution (`TST-UI-381.4`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-382: **Power Histogram Rare Sprint Spike Folding, Zone Boundary Snapping, and Consistent Symmetrical Header Range Toggle Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-419`, `REQ-UI-372`, `REQ-UI-388`, `REQ-UI-420`
- **Component**: **Power Histogram Rare Sprint Spike Folding, Zone Boundary Snapping, and Consistent Symmetrical Header Range Toggle Verification Suite**
- **Jira**: ``ATT-4370``
- **Test Description**:
  1. *Zone Boundary Snapping, Upper Boundary Alignment & Fallback Test (`TST-UI-382.1`, `TelemetryHistogramCalculatorTest.kt`)*:
  • Test candidate $\le z4Max$: verify `tailCutoff == thresholds.z4Max`.
  • Test candidate between $z4Max$ and $z5Max$: verify `tailCutoff == thresholds.z5Max`.
  • Test candidate between $z5Max$ and $z6Max$: verify `tailCutoff == thresholds.z6Max`.
  • Test candidate $> z6Max$: verify fallback to 50 W multiple rounding.
  • Test bin width alignment: verify `tailCutoff % binWidth == 0`.
  2. *Tail Folding Gating & Symmetric Dataset Non-Folding Test (`TST-UI-382.2`, `TelemetryHistogramCalculatorTest.kt`)*:
  • Test dataset where `dataMax - tailCutoff < 100 W`: verify `tailCutoff == null` and `sprintTail == null`.
  • Test dataset where `dataMax < tailCutoff * 1.25`: verify `tailCutoff == null` and `sprintTail == null`.
  • Test Heart Rate histogram calculation: verify `tailCutoff` is permanently null.
  3. *Main View Overflow Bin & Sprint Tail Return Bin Aggregation Test (`TST-UI-382.3`, `TelemetryHistogramCalculatorTest.kt`)*:
  • Verify Main View contains regular bins up to `tailCutoff` plus 1 overflow bin with `isOverflow = true`, `rangeMin = tailCutoff`, `rangeMax = dataMax`, and duration matching sum of samples $> \text{tailCutoff}$.
  • Verify Sprint Tail dataset contains return bin at index 0 with `isReturn = true`, `rangeMin = dataMin`, `rangeMax = tailCutoff`, and duration matching sum of samples $\le \text{tailCutoff}$.
  • Verify mathematical conservation of total duration and percentages sum to 100% in both Main and Sprint Tail datasets.
  4. *Model Backward Compatibility & Default Arguments Contract Test (`TST-UI-382.4`, `TelemetryHistogramCardTest.kt`)*:
  • Verify existing constructors of `TelemetryHistogramBin` and `TelemetryHistogramData` without new parameters compile and default `isOverflow = false`, `isReturn = false`, `tailCutoff = null`, `sprintTail = null`.
  5. *Symmetrical 2-Segment Header Range Toggle Layout & Affordance Contract Test (`TST-UI-382.5`, `TelemetryHistogramCardTest.kt`)*:
  • Verify independent Y-scaling math in Sprint Tail: `maxDuration` excludes return bin and scales to maximum sprint bin.
  • Verify Main View header layout contract: Segment 1 (`0–X W`) highlighted with `primaryContainer` / `onPrimaryContainer` (non-clickable); Segment 2 (`> X W`) clickable with `surfaceVariant` / `onSurfaceVariant` and forward arrow.
  • Verify Sprint Tail View header layout contract: Segment 1 (`0–X W`) clickable with `surfaceVariant` / `onSurfaceVariant` and back arrow; Segment 2 (`X–Y W`) highlighted with `primaryContainer` / `onPrimaryContainer` (non-clickable).
  • Verify resting readout string formats across both views and selected bin inspection across regular bins.
  6. *9-Language Localization & Format Specifier Parity Test (`TST-UI-382.6`, `TelemetryHistogramLocalizationTest.kt`)*:
  • Verify presence and non-blank values for `power_histogram_overflow_label`, `power_histogram_return_label`, `power_histogram_overflow_desc`, `power_histogram_return_desc` across all 9 locales with exact format token parity (`%1$d`, `%2$s`).
  7. *Clean-Room Full Suite Regression Execution (`TST-UI-382.7`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-383: **7 Cycling Power Zones in Power Histogram, TelemetryMetricGraph Threshold Lines, and Dark Mode Chart Rendering Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-420`, `REQ-UI-419`, `REQ-UI-372`, `REQ-UI-392`
- **Component**: **7 Cycling Power Zones in Power Histogram, TelemetryMetricGraph Threshold Lines, and Dark Mode Chart Rendering Verification Suite**
- **Jira**: ``ATT-4294``
- **Test Description**:
  1. *7-Zone Power Classification & Histogram Binning Unit Test (`TST-UI-383.1`, `TelemetryHistogramCalculatorTest.kt`)*:
  • Verify samples with wattage in Zone 6 (`z5Max < pwr <= z6Max`) produce bins with `zoneIndex = 6` and `color = TTColor.Zone6`.
  • Verify samples with wattage in Zone 7 (`pwr > z6Max`) produce bins with `zoneIndex = 7` and `color = TTColor.Zone7`.
  • Verify `overflowBin` in `calculatePowerHistogram` adopts the peak zone index (6 or 7) and color from `tailBins`.
  • Verify `calculateHeartRateHistogram` continues to classify HR samples strictly into zones 1..5 (`zoneIndex in 1..5`, `color in Zone1..Zone5`).
  • Verify mathematical conservation of total active duration and percentage sum ($100.0\% \pm 0.1\%$).
  2. *6-Threshold Power Dashed Lines Unit Test (`TST-UI-383.2`, `TelemetryZoneMathTest.kt`)*:
  • Verify `calculatePowerThresholdDashes` produces all 6 dashed lines (`z1Max` through `z6Max`) when data range spans below `z1Max` and above `z6Max`.
  • Verify threshold values outside `[dataMin, dataMax]` are filtered out.
  • Verify heart rate 4-threshold dashes (`calculateThresholdDashes`) remain strictly 4 lines.
  3. *Dark Mode Contrast, Readability & Model KDoc Parity Contract Test (`TST-UI-383.3`, `TelemetryHistogramCardTest.kt`)*:
  • Contrast verification: verify `TTColor.Zone7` (`#000000`) paired with `outlineVariant` border guarantees $\ge 3.0:1$ non-text contrast against dark theme surface (`#121212` / `#1E1E1E`).
  • Header readout color resolution: verify Zone 7 selected bin text color resolves to `onSurface` in dark mode.
  • Verify KDoc documentation parity in `ZoneDistributionModels.kt` documenting 1..7 zones for power.
  4. *9-Language Localization & String Audit (`TST-UI-383.4`, `TranslationParityTest.kt`)*:
  • Verify zero new string resources required across all 9 canonical app locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  • Verify existing string resources maintain 100% translation parity and format specifier compatibility.
  5. *Clean-Room Regression Suite Execution (`TST-UI-383.5`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions across the full test suite.

### TST-UI-384: **Electronic Bike Bell with Button, Double-Tap Gesture, and Progressive Loudness Escalation Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-421`, `REQ-UI-344`, `REQ-UI-295`, `REQ-UI-409`
- **Component**: **Electronic Bike Bell with Button, Double-Tap Gesture, and Progressive Loudness Escalation Verification Suite**
- **Jira**: ``ATT-4371``, ``ATT-5131``, ``ATT-5282``
- **Test Description**:
  1. *TrackingViewsDbHelper SQLite v15 Transactional & Idempotent Migration Unit Test (`TST-UI-384.1`, `TrackingViewsRealSqliteMigrationTest.kt`)*:
  • Test database upgrade from v14 to v15: verify `SHOW_BIKE_BELL` column added.
  • Test existing cycling tabs (`BIKE_SPEED_AND_CADENCE`, `BIKE_POWER`) have `SHOW_BIKE_BELL = 1`.
  • Test non-cycling tabs retain `SHOW_BIKE_BELL = 0`.
  • Test idempotency: re-running migration does not throw duplicate column exception.
  • Test `DatabaseSchemaRegistry.kt` asserts version 15 for `"FlexibleGenericView.db"` and `"TrackingViews.db"`.
  2. *TrackingViewsRepository & DTO Mapping Symmetry Unit Test (`TST-UI-384.2`, `TrackingViewsRepositoryTest.kt`)*:
  • Test `TrackingViewInfo` reads and maps `showBikeBell` correctly from single-row and batch queries.
  • Test `updateShowBikeBell(tabViewId, show)` persists toggle state accurately.
  3. *BikeBellAudioSynthesizer Built-in Speaker Routing, Alarm Stream, 5-Mode Brass Synthesis & Volume Boost Test (`TST-UI-384.3`, `BikeBellAudioSynthesizerTest.kt`)*:
  • Verify 16-bit PCM mono buffer synthesis generated at $48\text{ kHz}$ with 40 ms SmartPA amplifier lead-in (`LEAD_IN_SEC = 0.040`), strike 1 at $t=0.040\text{ s}$, strike 2 at $t=0.150\text{ s}$ ($\sim 110\text{ ms}$ separation), and $0.95\text{ s}$ duration with $60\text{ ms}$ cosine fade-out across 5 resonant modes (sub-octave hum $1020\text{ Hz}$, brass fundamental $2040\text{ Hz}$, tierce $3120\text{ Hz}$, nominal $4280\text{ Hz}$, clapper $6200\text{ Hz}$) with smooth attack window ($1 - e^{-dt / 0.0015}$) (ATT-5649).
  • Verify roll PCM synthesis incorporates headroom scaling without hard clipping distortion.
  • Verify `AudioAttributes.USAGE_ALARM` and `AudioAttributes.CONTENT_TYPE_SONIFICATION` audio attributes configuration.
  • Verify conditional speaker routing: `setPreferredDevice(builtInSpeaker)` is invoked only when an external audio device (Bluetooth A2DP/SCO/BLE, wired/USB headset, hearing aid) is detected; sets `setPreferredDevice(null)` when no external devices are connected to prevent port thrashing (ATT-5649).
  • Verify `savedAlarmVolume` capture, hardware volume boost to `getStreamMaxVolume(STREAM_ALARM)` with `flags = 0` only when `currentVolume < maxVolume`, and delayed auto-restore (+5500 ms for ring matching the 5 s escalation window, +150 ms after stopRoll) (ATT-5649).
  • Verify static replay contract: `mockTrack.pause()` (never `stop()`) is invoked prior to `mockTrack.playbackHeadPosition = 0` on every ring and roll start, maintaining warm AudioFlinger mixer sessions (ATT-5649).
  • Verify thread safety: concurrent calls to `ring()`, `startRoll()`, `stopRoll()`, and `close()` from multiple threads execute without exceptions or native crashes.
  4. *3-Stage Perceptual Loudness Escalation Logic Test (`TST-UI-384.4`, `TrackingTabsViewModelTest.kt`)*:
  • Test initial ring: verify volume is 0.35f (Stage 1, -9.1 dB).
  • Test repeat ring within 5 seconds ($t + 2\text{s}$): verify volume escalates to 0.65f (Stage 2, -3.7 dB).
  • Test third ring within 5 seconds ($t + 4\text{s}$): verify volume escalates to 1.00f (Stage 3, 0 dB).
  • Test fourth ring within 5 seconds ($t + 5\text{s}$): verify volume remains capped at 1.00f (Stage 3).
  • Test silence $> 5\text{ seconds}$ ($t + 11\text{s}$): verify volume resets to 0.35f (Stage 1).
  • Test long-press: verify immediate 1.00f maximum volume.
  • Test `onCleared()` releases synthesizer resources cleanly.
  5. *Cockpit Multi-Mode Availability & Pointer Gesture Isolation Contract Test (`TST-UI-384.5`, `TrackingTabsScreenTest.kt` / `BikeBellButtonTest.kt`)*:
  • Verify multi-mode availability: `BikeBellButton` is enabled in `TrackingMode.READY`, `TrackingMode.TRACKING`, and `TrackingMode.PAUSED` (enforcing Rule 53).
  • Verify `TrackingMode.IDLE`: `BikeBellButton` is disabled.
  • Verify gesture isolation: tap triggers, haptic pulse, and shake animation are handled directly by `ElevatedButton(onClick)` without nested or attached `Modifier.pointerInput(detectTapGestures)`.
  • Verify both buttons visible: `LapButton` and `BikeBellButton` render side-by-side in horizontal row.
  • Verify single button: `BikeBellButton` renders centered when `showBikeBell = true` and `showLapButton = false`.
  • Verify `ScreenMode.CONFIGURATION`: both `LapButton` and `BikeBellButton` are suppressed (preserving `REQ-UI-295`).
  • Verify `SnackbarHost` bottom padding adjusts dynamically (64.dp when button visible, 8.dp when hidden).
  • Verify orange theme color styling (`ATT-5283`): `BikeBellButton` container uses `TTColor.BikeBellContainer` (orange) and icon uses `TTColor.BikeBellContent` (high contrast) when `isAvailable`, with zero hardcoded hex literals outside `Color.kt` (`ThemeColorCentralizationAuditTest`).
  6. *SensorGridScreen Multi-Mode Double-Tap Disambiguation & Pager Non-Interference Contract Test (`TST-UI-384.6`, `SensorGridScreenTest.kt`)*:
  • Verify double-tap on sensor grid dispatches bell ring when `screenMode != ScreenMode.CONFIGURATION && state.showBikeBell` (active in both `TRACKING` and `PREVIEW` modes).
  • Verify double-tap is disabled when `screenMode == ScreenMode.CONFIGURATION` or `showBikeBell == false`.
  • Verify horizontal swipe gestures are not consumed and propagate freely to `HorizontalPager`.
  • Verify single tap propagates to `BatterySaverController` for screen wakefulness.
  7. *9-Language Localization & String Specifier Parity Test (`TST-UI-384.7`, `TranslationParityTest.kt`)*:
  • Verify presence of `config_tracking__showBikeBell` and `config_tracking__showBikeBell_subtitle` across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  • Verify zero missing keys and literal `\n` linebreaks.
  8. *Clean-Room Regression Suite Execution (`TST-UI-384.8`)*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.
  9. *Manifest Permission Security Contract (`TST-UI-384.9`, `ManifestAndBuildConfigContractTest.kt`)*:
  • Verify `AndroidManifest.xml` actively declares `android.permission.MODIFY_AUDIO_SETTINGS`.

### TST-UI-385: **Relocate 'Sport Types' Drawer Item to Settings and Rename 'Equipment' Item to 'Bikes & Shoes' Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-422`, `REQ-UI-405`, `REQ-LOC-001`
- **Component**: **Relocate 'Sport Types' Drawer Item to Settings and Rename 'Equipment' Item to 'Bikes & Shoes' Verification Suite**
- **Jira**: ``ATT-4289``
- **Test Description**:
  1. *Drawer Structure & Group Item Count Integrity Unit Test (`TST-UI-385.1`, `NavigationDrawerStateTest.kt`)*:
  • Verify `myStuffGroup.items.map { it.id }` equals `listOf(R.id.drawer_my_sensors, R.id.drawer_equipment)` (strictly 2 items).
  • Verify `settingsGroup.items.map { it.id }` equals `listOf(R.id.drawer_units, R.id.drawer_display_settings, R.id.drawer_tracking_layouts, R.id.drawer_sport_types, R.id.drawer_backup_restore, R.id.drawer_privacy_policy, R.id.drawer_advanced_tuning)` (strictly 7 items with `drawer_sport_types` at index 3).
  • Verify total unique items count across all 5 groups remains strictly 22.
  • Verify default visible items count across non-collapsible groups (`Training` + `Maps`) remains strictly 9.
  2. *Drawer Auto-Expansion & Alias Selection State Unit Test (`TST-UI-385.2`, `NavigationDrawerStateTest.kt`)*:
  • Verify selecting `R.id.drawer_sport_types` auto-expands `settingsGroup` (`isDrawerGroupInitiallyExpanded == true`).
  • Verify selecting `R.id.drawer_sport_types` leaves `myStuffGroup` collapsed (`isDrawerGroupInitiallyExpanded == false`).
  • Verify selecting items in `myStuffGroup` (`drawer_my_sensors`, `drawer_equipment`, `drawer_bikes`, `drawer_shoes`) auto-expands `myStuffGroup`.
  • Verify `isDrawerItemSelected(R.id.drawer_equipment, ...)` alias matching for `drawer_equipment`, `drawer_bikes`, and `drawer_shoes`.
  3. *9-Language Localization & XML Entity Escaping Audit Test (`TST-UI-385.3`, `TranslationParityTest.kt`)*:
  • Verify `R.string.equipment_management_title` is defined across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with exact expected translations and proper XML entity escaping (`&amp;`).
  • Verify zero missing keys and 100% translation parity.
  4. *Screen Header Parity Contract Test (`TST-UI-385.4`, `EquipmentTabsScreenContractTest.kt`)*:
  • Verify `EquipmentTabsScreen.kt` references `R.string.equipment_management_title` for its top header, guaranteeing 1:1 parity with the drawer destination label.
  5. *Clean-Room Regression Suite Execution (`TST-UI-385.5`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions across the full test suite.

### TST-UI-386: **Cockpit Section Divider Prominence, Centered Title Layout, and Unambiguous Split Icon Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-411`
- **Component**: **Cockpit Section Divider Prominence, Centered Title Layout, and Unambiguous Split Icon Verification Suite**
- **Jira**: ``ATT-4286``
- **Test Description**:
  1. *Unlabeled Cockpit Section Divider Prominence & Contrast Test (`TST-UI-386.1`, `SensorGridScreenLayoutTest.kt`)*:
  • Verify `CockpitSectionDivider` for unlabeled divider (`title.isNullOrEmpty()`) specifies `thickness = 2.5.dp` (thickness $\ge 2.0\text{ dp}$).
  • Verify stroke color uses `MaterialTheme.colorScheme.outline` (providing prominent, glanceable contrast against dark and light surface containers).
  • Verify vertical padding remains compact (`padding(vertical = 4.dp)`), guaranteeing total height overhead $\le 10\text{ dp}$.
  2. *Labeled Cockpit Section Divider Centered Title & Symmetric Weights Test (`TST-UI-386.2`, `SensorGridScreenLayoutTest.kt`)*:
  • Verify `CockpitSectionDivider` for titled divider specifies symmetric `Modifier.weight(1f)` on both the leading and trailing `HorizontalDivider` instances.
  • Verify absence of asymmetric flanking weights (`weight(0.12f)` and `weight(0.88f)`).
  • Verify flanking divider lines specify `thickness = 2.dp` with `MaterialTheme.colorScheme.outline`.
  • Verify title retains `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, and row height remains constrained (`heightIn(max = 20.dp)`).
  3. *Configuration Mode Divider Insertion Icon & Minus Affordance Removal Test (`TST-UI-386.3`, `SensorGridScreenLayoutTest.kt`)*:
  • In `SensorGridScreen.kt`, verify inter-row divider action evaluates to `Icons.Default.HorizontalSplit` when `existingDivider == null`.
  • Verify `Icons.Default.HorizontalRule` is strictly absent from divider insertion controls and `SensorGridScreen.kt`.
  • Verify existing divider retains `Icons.Default.Title` with `primary` tint when `existingDivider != null`.
  • Verify content description references `R.string.cockpit_section_divider_add` when `existingDivider == null` and `R.string.cockpit_section_divider_edit` when `existingDivider != null`.
  4. *9-Language Localization Parity & Non-Blank Audit (`TST-UI-386.4`, `SensorGridLocalizationTest.kt`)*:
  • Verify all section divider accessibility descriptions, dialog labels, and preset chips exist and are non-blank across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  • Confirm zero new strings are required and zero format specifiers are mutated.
  5. *Clean-Room Regression Suite Execution (`TST-UI-386.5`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions across the full test suite.

### TST-UI-387: **RouteTabbedScreen Modal Overlay Composition Retention & Tab Scroll Stability Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-423`, `REQ-UI-191`, `REQ-UI-159`
- **Component**: **RouteTabbedScreen Modal Overlay Composition Retention & Tab Scroll Stability Verification Suite**
- **Jira**: ``ATT-4285``
- **Test Description**:
  1. *Composition Retention & Overlay Structural Contract Test (`TST-UI-387.1`, `RoutesScreenContractTest.kt`)*:
  • Verify `RouteTabbedScreen` is rendered unconditionally in the non-details branch (`selectedRouteIdForDetails == null`).
  • Verify `EditRouteScreen` is composed *inside* the same branch alongside `RouteTabbedScreen` conditioned on `if (selectedRouteIdForEdit != null)`.
  • Verify `else if (selectedRouteIdForEdit != null)` does NOT exist as a mutually exclusive sibling branch to `RouteTabbedScreen`.
  2. *BackHandler & State Dismissal Contract Test (`TST-UI-387.2`, `RoutesScreenContractTest.kt`)*:
  • Verify `EditRouteScreen` receives `onSave` updating route via `viewModel.updateRoute(it)` and clearing `selectedRouteIdForEdit = null`.
  • Verify `EditRouteScreen` receives `onCancel` clearing `selectedRouteIdForEdit = null`.
  • Verify `BackHandler` is active while editing to reset `selectedRouteIdForEdit = null`.
  3. *9-Language Localization & String Resource Audit (`TST-UI-387.3`)*:
  • Verify zero new user-facing strings are introduced, and all existing route editing string tokens (`route_edit`, `name`, `route_sport_type`, `save`, `cancel`) remain intact across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  4. *Clean-Room Regression Suite Execution (`TST-UI-387.4`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions across the full test suite.

### TST-UI-388: **Physiology Models 'Show in Workout Summary' Gating, Rule 59 Subordinate Switch Scale & Hierarchy, Lighter Checked Track Color, Coggan-Only PowerMetricSlot, Modularity & 9-Language Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-424`, `REQ-UI-403`, `REQ-UI-415`
- **Component**: **Physiology Models 'Show in Workout Summary' Gating, Rule 59 Subordinate Switch Scale & Hierarchy, Lighter Checked Track Color, Coggan-Only PowerMetricSlot, Modularity & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-4369`, `ATT-4432`, `ATT-4464`, `ATT-4513``
- **Test Description**:
  1. *PowerMetricSlot Resolution without Basic Power Fallback Unit Test (`TST-UI-388.1`, `PowerMetricSlotResolutionTest.kt`)*:
  • *Given* workout telemetry with normalizedPower, intensityFactor, tss, workKj, and avgPowerToWeight,
  • *When* calling `resolvePowerMetricSlots` with `enableCogganPowerMetrics = true` and `showCogganPower = true`,
  • *Then* exactly 3 Coggan slots `[NORMALIZED_POWER, INTENSITY_FACTOR, TSS]` are returned.
  • *When* calling with `enableCogganPowerMetrics = true`, `showCogganPower = false`,
  • *Then* an empty list `[]` is returned (zero basic power fallback).
  • *When* calling with `enableCogganPowerMetrics = false`,
  • *Then* an empty list `[]` is returned.
  • *When* calling with `enableCogganPowerMetrics = true`, `showCogganPower = true`, but null FTP metrics (`intensityFactor == null`, `tss == null`),
  • *Then* strictly 1 slot `[NORMALIZED_POWER]` is returned, without filling from basic power.
  • *When* calling with null `normalizedPowerWatts`,
  • *Then* an empty list `[]` is returned.
  2. *WorkoutDetails Presentation Gating & Divider Integrity Contract Test (`TST-UI-388.2`, `WorkoutDetailsLayoutTest.kt`)*:
  • *Given* `WorkoutDetails` composable,
  • *When* all summary toggles (`showCogganPowerInWorkoutSummary`, `showAerobicEfficiencyInWorkoutSummary`, `showCriticalPowerInWorkoutSummary`) are `false`,
  • *Then* verify `PowerTrainingMetricsSection`, `AerobicEfficiencyRow`, and `AnaerobicBatteryRow` are omitted, and zero dividers are rendered after Time/Distance (or exactly one divider if Altitude is present).
  • *When* only `showCogganPowerInWorkoutSummary = true` and NP is present, verify `PowerTrainingMetricsSection` is rendered with exactly one preceding divider.
  • *When* only `showAerobicEfficiencyInWorkoutSummary = true`, verify `AerobicEfficiencyRow` is rendered with exactly one preceding divider.
  • *When* only `showCriticalPowerInWorkoutSummary = true`, verify `AnaerobicBatteryRow` is rendered with exactly one preceding divider.
  • Verify `WorkoutDetails.kt` line count is strictly `< 500` lines.
  3. *TuningConfig & DataStore Persistence Contract Test (`TST-UI-388.3`, `PhysiologyModelsVisibilityContractTest.kt`, `TuningFormControlsTest.kt`)*:
  • *Given* `TuningConfig()` defaults,
  • *Then* verify: exactly 4 models exist; `showCogganPowerInWorkoutSummary = true`, `showAerobicEfficiencyInWorkoutSummary = true`, `showCriticalPowerInWorkoutSummary = true`.
  • Verify `TuningPreferencesDefaults` contains zero basic power constants (`DEFAULT_ENABLE_BASIC_POWER_METRICS` and `DEFAULT_SHOW_BASIC_POWER_IN_WORKOUT_SUMMARY` removed).
  • Verify `TuningPreferencesDataStore.ALL_KEYS` contains exactly 3 summary preference keys.
  • Verify `PhysiologyModelsSection.kt` renders subordinate "Show in Workout Summary" toggles via `SubordinateTuningToggleItem` (Rule 59) when master switches are enabled.
  • Verify `SubordinateTuningToggleItem` downscales `Switch` to `Modifier.scale(0.6f)`, enforces compact `Modifier.height(24.dp)`, and applies lighter subordinate checked track color `MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)` and thumb `MaterialTheme.colorScheme.primary` via `SwitchDefaults.colors()` (`TuningFormControlsTest.kt`, `PhysiologyModelsVisibilityContractTest.kt`, `ATT-4513`).
  • Verify every `.kt` file under `ui/settings/tuning/` (`PhysiologyModelsSection.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `TuningFormControls.kt`) has strictly `< 400` lines (`AdvancedTuningModularityTest.kt`).
  4. *Live Cockpit & Background Persistence Invariant Contract Test (`TST-UI-388.4`, `PhysiologyModelsVisibilityContractTest.kt`)*:
  • *Given* `EditSensorFieldViewModel.shouldIncludeSensorType` and `SensorGridScreen` field visibility,
  • *When* evaluated,
  • *Then* verify they continue to evaluate master model toggles (`enableCogganPowerMetrics`, `enableCriticalPowerModel`), completely independent of summary toggles.
  • Verify baseline physics metrics (`SensorType.WORK_KJ`, `SensorType.POWER_TO_WEIGHT`) remain fully selectable in live cockpit tiles.
  • Verify background recording in `TrackerService` and SQLite storage remains untouched.
  5. *9-Language Localization Clean-Up & Parity Audit (`TST-UI-388.5`, `PhysiologyModelsVisibilityContractTest.kt`)*:
  • *Given* all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`),
  • *Then* verify string keys `tuning_physiology_basic_power_title` and `tuning_physiology_basic_power_desc` are completely removed across all 9 locales.
  • Verify remaining summary toggle strings exist and are non-blank with matching format specifiers.
  6. *Clean-Room Full Suite Regression Execution (`TST-UI-388.6`)*:
  • *When* executing `./gradlew testDebugUnitTest`,
  • *Then* assert 100% pass rate with zero regressions across the complete test suite.

### TST-UI-389: **Elevation Gain Interval Filtering (Min/Max Range and Preset Intervals) Verification Suite across Routes, Favorite Routes, and Workouts** [Verified]
- **Requirement(s)**: `REQ-UI-425`
- **Component**: **Elevation Gain Interval Filtering (Min/Max Range and Preset Intervals) Verification Suite across Routes, Favorite Routes, and Workouts**
- **Jira**: ``ATT-4367``
- **Test Description**:
  1. *RouteFilterCriteria Elevation Gain Interval & Active Count Test (`TST-UI-389.1`, `RouteFilterCriteriaTest.kt`)*:
  • Test bounded interval `[250 m, 500 m]` (`min = 250.0, max = 500.0`): assert 250 m, 400 m, 500 m match; 50 m, 100 m, 1200 m fail.
  • Test upper bound only `< 100 m` (`min = null, max = 100.0`): assert 50 m, 100 m match; 250 m, 400 m, 500 m, 1200 m fail.
  • Test lower bound only `> 500 m` (`min = 500.0, max = null`): assert 500 m, 1200 m match; 50 m, 100 m, 250 m, 400 m fail.
  • Test `activeFilterCount`: assert `minOnly` gives 1, `maxOnly` gives 1, and `bothBounds` gives 1 (strictly 1 dimension for elevation gain).
  2. *RouteFilterCriteria JSON Serialization Roundtrip & Backward Compatibility Test (`TST-UI-389.2`, `RouteFilterCriteriaTest.kt`)*:
  • Call `toJson()` on a two-bound instance, deserialize with `RouteFilterCriteria.fromJson()`, and assert properties match.
  • Deserialize legacy JSON lacking `maxElevationGainMeters`: assert `maxElevationGainMeters = null` and `minElevationGainMeters` is preserved without errors.
  3. *ClusterFilterCriteria Elevation Gain Interval & Active Count Test (`TST-UI-389.3`, `ClusterFilterCriteriaTest.kt`)*:
  • Test bounded interval `[100 m, 250 m]` (`min = 100.0, max = 250.0`) on cluster resolved ascent: assert 150 m, 250 m match; 50 m, 350 m, 500 m, 800 m fail.
  • Test upper bound only `< 100 m`: assert 50 m matches; 150 m, 250 m fail.
  • Test lower bound only `> 500 m`: assert 500 m, 800 m match; 350 m fails.
  • Test `activeFilterCount`: assert setting min, max, or both contributes strictly 1 to count.
  4. *ClusterFilterCriteria JSON Serialization Roundtrip & Backward Compatibility Test (`TST-UI-389.4`, `ClusterFilterCriteriaTest.kt`)*:
  • Execute JSON roundtrip with `minElevationGainMeters` and `maxElevationGainMeters` and verify legacy JSON without elevation keys parses safely with null.
  5. *WorkoutFilterCriteria Elevation Gain Interval & Active Count Test (`TST-UI-389.5`, `WorkoutFilterCriteriaTest.kt`)*:
  • Test bounded interval `[100 m, 250 m]` (`min = 100.0, max = 250.0`) on workout `ascentMeters`: assert 100L, 200L, 250L match; 40L, 600L, 1200L fail.
  • Test upper bound only `< 100 m`: assert 40L, 100L match; 200L, 250L, 600L fail.
  • Test lower bound only `> 500 m`: assert 600L, 1200L match; 250L fails.
  • Test `activeFilterCount`: assert setting min, max, or both contributes strictly 1 to count.
  6. *WorkoutFilterCriteria JSON Serialization Roundtrip & Backward Compatibility Test (`TST-UI-389.6`, `WorkoutFilterCriteriaTest.kt`)*:
  • Execute JSON roundtrip with `minElevationGainMeters` and `maxElevationGainMeters` and verify legacy JSON without elevation keys parses safely with null.
  7. *Active Filter Chips Row Elevation Formatting & Dismissal Contract Test (`TST-UI-389.7`, `RouteFilterCriteriaTest.kt` / `ActiveFilterChipsRowTest.kt`)*:
  • Verify interval formatting produces `"$min - $max m"` (e.g. `"250 - 500 m"`), `"> $min m"` (e.g. `"> 500 m"`), and `"< $max m"` (e.g. `"< 100 m"`).
  • Verify dismissal contract: `criteria.copy(minElevationGainMeters = null, maxElevationGainMeters = null)` clears both elevation bounds.
  8. *9-Language Localization & Format Specifier Parity Test (`TST-UI-389.8`, `TranslationParityTest.kt`)*:
  • Verify string resources `filter_max_elevation_format` and `filter_elevation_interval_format` exist across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with 100% format specifier parity (`%1$s`, `%2$s`).
  9. *Clean-Room Regression Suite Execution (`TST-UI-389.9`)*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-390: **BikeBellButton MutableInteractionSource Press Monitoring, Long-Press Continuous Ring Disambiguation & Looping Roll Synthesis Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-421`, `REQ-UI-421.2`, `REQ-UI-421.4`
- **Component**: **BikeBellButton MutableInteractionSource Press Monitoring, Long-Press Continuous Ring Disambiguation & Looping Roll Synthesis Verification Suite**
- **Jira**: ``ATT-4431``
- **Test Description**:
  1. *BikeBellButton InteractionSource Press-and-Hold & Continuous Shake Animation Unit Test (`TST-UI-390.1`, `BikeBellButtonContractTest.kt`)*:
  • Verify `BikeBellButton` instantiates `remember { MutableInteractionSource() }` and passes it to `ElevatedButton`.
  • Verify `LaunchedEffect` collects `PressInteraction.Press` and launches long-press delay timer via `LocalViewConfiguration.current.longPressTimeoutMillis` (~400 ms).
  • Verify long-press timer triggers `onContinuousRingStart()`, haptic pulse, and continuous looping shake animation while held.
  2. *BikeBellButton Release, Cancellation & Unmount Teardown Unit Test (`TST-UI-390.2`, `BikeBellButtonContractTest.kt`)*:
  • Verify `PressInteraction.Release` cancels timer, invokes `onContinuousRingStop()`, and resets rotation to 0.
  • Verify `PressInteraction.Cancel` cancels timer, invokes `onContinuousRingStop()`, and resets rotation to 0.
  • Verify `DisposableEffect` invokes `onContinuousRingStop()` if button is unmounted during active roll.
  3. *Single-Tap Disambiguation & Spurious Chime Suppression Unit Test (`TST-UI-390.3`, `BikeBellButtonContractTest.kt`)*:
  • Verify short tap (< 400 ms) invokes `onSingleRing()` via `ElevatedButton(onClick)` with one-shot shake animation.
  • Verify long-press release resets `wasLongPressHandled` and suppresses `onSingleRing()`, preventing duplicate strike on finger release.
  4. *Cockpit Zero Gesture Contention & Multi-Mode Gating Contract Test (`TST-UI-390.4`, `BikeBellButtonContractTest.kt`)*:
  • Verify `BikeBellButton.kt` contains zero `Modifier.pointerInput` or `detectTapGestures` on `ElevatedButton` (enforcing Rules 53 and 58).
  • Verify `isAvailable` evaluates `READY`, `TRACKING`, and `PAUSED` modes.
  5. *BikeBellAudioSynthesizer Looping Roll setLoopPoints Unit Test (`TST-UI-390.5`, `BikeBellAudioSynthesizerTest.kt`)*:
  • Verify `startRoll()` invokes `mockTrack.setLoopPoints(0, totalFrames, -1)` with `totalFrames = rollPcm.size / 2` (110,250 samples).
  • Verify `startRoll()` resets position, sets volume, and starts playback.
  • Verify `stopRoll()` halts track and resets playback head position.
  6. *BikeBellAudioSynthesizer Concurrency Safety & Lifecycle Teardown Test (`TST-UI-390.6`, `BikeBellAudioSynthesizerTest.kt`)*:
  • Verify 50 concurrent interleaved calls across `ring()`, `startRoll()`, `stopRoll()`, and `close()` execute thread-safely under `synchronized(lock)`.
  • Verify `close()` releases native tracks safely and subsequent calls safe no-op.
  7. *9-Language Localization & String Specifier Audit (`TST-UI-390.7`, `TranslationParityTest.kt`)*:
  • Verify `config_tracking__showBikeBell` and `config_tracking__showBikeBell_subtitle` exist with 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  8. *Clean-Room Full Regression Suite Execution (`TST-UI-390.8`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions across the full project test suite.

### TST-UI-391: **Realistic Default Cycling FTP (270 W), Coggan 7-Zone and Karvonen Heart Rate Zone Baselines Verification Suite** [Default cycling FTP is 270 W, default Coggan power cutoffs are [149, 203, 243, 284, 324, 405] W, default Karvonen HR cutoffs are [132, 145, 158, 171] bpm for run and [127, 140, 153, 166] bpm for bike across DataStore, UI calculator dialogs, and tracking ViewModels.] [Verified]
- **Requirement(s)**: `REQ-UI-426`, `REQ-UI-370`, `REQ-UI-373`
- **Component**: **Realistic Default Cycling FTP (270 W), Coggan 7-Zone and Karvonen Heart Rate Zone Baselines Verification Suite**
- **Jira**: ``ATT-4521, ATT-4543``
- **Test Description**:
  1. *SettingsDataStore Default Constants & Snapshot Fallback Unit Tests (`TST-UI-391.1`, `SettingsDataStoreBaselinesTest.kt`)*:
  • Verify `SettingsDataStore.DEFAULT_CYCLING_FTP == 270`.
  • Verify `SettingsDataStore.DEFAULT_HR_MAX == 185` and `DEFAULT_HR_REST == 55`.
  • Verify `SettingsSnapshot.DEFAULT.pwrBikeZoneMax == listOf(149, 203, 243, 284, 324, 405)`.
  • Verify `SettingsSnapshot.DEFAULT.hrRunZoneMax == listOf(132, 145, 158, 171)`.
  • Verify `SettingsSnapshot.DEFAULT.hrBikeZoneMax == listOf(127, 140, 153, 166)`.
  2. *SettingsDataStore getDefaultValue() and resetToDefaults() Unit Tests (`TST-UI-391.2`, `SettingsDataStoreBaselinesTest.kt`)*:
  • Verify `getDefaultValue(ZoneType.PWR_BIKE, zone)` returns 149, 203, 243, 284, 324, 405 for Zones 1..6.
  • Verify `getDefaultValue(ZoneType.HR_RUN, zone)` returns 132, 145, 158, 171 for Zones 1..4.
  • Verify `getDefaultValue(ZoneType.HR_BIKE, zone)` returns 127, 140, 153, 166 for Zones 1..4.
  • Verify `resetToDefaults()` writes these exact canonical baselines to DataStore.
  3. *ZoneCalculatorDialog Default State & Contract Tests (`TST-UI-391.3`, `ZoneCalculatorDialogContractTest.kt`)*:
  • Verify `ZoneCalculatorUiState` defaults `ftpInput` to `"270"`, `hrMaxInput` to `"185"`, and `hrRestInput` to `"55"`.
  • Verify `initialState_populatesStoredFtpOrFallback270W` defaults to 270 W when stored FTP is absent and computes Coggan cutoffs [149, 203, 243, 284, 324, 405].
  • Verify initial Karvonen calculation defaults compute `HR_RUN` cutoffs [132, 145, 158, 171] and `HR_BIKE` cutoffs [127, 140, 153, 166].
  4. *TrackingViewModel Runtime Fallback Cutoffs Contract Test (`TST-UI-391.4`, `TrackingViewModelGridTest.kt`)*:
  • Verify `zoneCutoffsMap` initial fallbacks match canonical cutoffs for PWR_BIKE, HR_RUN, and HR_BIKE.
  5. *Existing BatterySaver & NonBlocking Contract Test Updates (`TST-UI-391.5`, `BatterySaverModelPureTest.kt`, `SettingsDataStoreJavaHelperNonBlockingTest.kt`)*:
  • Update assertions asserting on default zone cutoffs to match 270 W and 185/55 bpm baselines.
  6. *Clean-Room Regression Suite Execution (`TST-UI-391.6`)*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate across the full project test suite.

### TST-UI-392: **Aftermath Loader Concurrency, Process-Death Detail Restoration, Elevation Gesture State Integrity and Lifecycle Safety Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-427`, `REQ-MAP-032`, `REQ-DAT-011`, `REQ-UI-368`
- **Component**: **Aftermath Loader Concurrency, Process-Death Detail Restoration, Elevation Gesture State Integrity and Lifecycle Safety Verification Suite**
- **Jira**: ``ATT-4018``, ``ATT-5478``
- **Test Description**:
  1. *Aftermath & Peek Loader Coroutine Cancellation Unit Tests (`TST-UI-392.1`, `TrackOnMapAftermathViewModelTest.kt`, `WorkoutClustersViewModelTest.kt`, `PeriodsViewModelTest.kt`)*:
  • Verify that dispatching `loadAftermathData` for a second workout cancels the previous `Job` before completing.
  • Verify that calling `selectWorkoutForPeek` consecutively cancels the previous peek coroutine job in both ViewModels.
  2. *Process-Death Restoration & Idempotent In-Place WorkoutData Contract Tests (`TST-UI-392.2`, `WorkoutSummariesTabbedScreenTest.kt`, `TrackOnMapAftermathViewModelTest.kt`)*:
  • Verify that initializing `selectedWorkoutIdForDetails` without `onMapClick` triggers `loadAftermathData` via `LaunchedEffect`.
  • Verify `AftermathMapUIState` exposes `loadedWorkoutId: Long?` populated with the loaded workout ID.
  • Verify that emitting an updated `WorkoutData` instance with the same `id` skips Phase 1 track wipe in `loadAftermathData`, preserving `tracks` and preventing `visibleTabs` tab count shrinkage and `HorizontalPager` tab jumping (`Rule 73`).
  • Verify that switching to a different workout `id` executes Phase 1 track wipe, clearing previous tracks to prevent ghost data.
  3. *Elevation Profile Gesture Updated State Unit/Contract Tests (`TST-UI-392.3`, `ElevationProfileGestureTest.kt`)*:
  • Verify that `ElevationProfile` pointerInput evaluates `rememberUpdatedState(pathPoints)` and retrieves updated telemetry during ongoing scrub gestures.
  4. *Crop Double-Tap Guard & CancellationException Rethrow Unit Tests (`TST-UI-392.4`, `CropWorkoutViewModelTest.kt`, `WorkoutRepositoryCropTest.kt`)*:
  • In `CropWorkoutViewModelTest`, verify that `executeCrop` immediately updates `_uiState` with `isCropping = true` synchronously, rejecting concurrent calls.
  • In `WorkoutRepositoryCropTest`, verify that `cropWorkout` rethrows `CancellationException` rather than returning `Result.failure`.
  5. *Bulk Deletion NonCancellable Resync Unit Tests (`TST-UI-392.5`, `WorkoutRepositoryDeleteTest.kt`)*:
  • In `WorkoutRepositoryDeleteTest`, verify that cancelling the caller's coroutine during `deleteOldWorkouts` does NOT abort `resyncAllPeriods` and `refreshClusters`.
  6. *EditWorkoutViewModel Lifecycle Scoping Contract Tests (`TST-UI-392.6`, `EditWorkoutLifecycleTest.kt`)*:
  • Verify that exiting edit mode cleans up or properly scopes `EditWorkoutViewModel` without unbounded memory accumulation in the host store.
  7. *Localization Parity Audit (`TST-UI-392.7`, `TranslationParityTest.kt`)*:
  • Verify 100% translation parity across all 9 supported locales (0 new UI strings introduced).
  8. *Clean-Room Regression Suite Execution (`TST-UI-392.8`)*:
  • Run targeted unit tests and full clean-room suite verifying 100% test pass rate.

### TST-UI-393: **Atomic Single-Transaction DataStore Zone Persistence, SavedStateHandle Draft Preservation, Deduplicated Profile Loading & UI Decoupling Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-428`, `REQ-UI-172`, `REQ-UI-397`
- **Component**: **Atomic Single-Transaction DataStore Zone Persistence, SavedStateHandle Draft Preservation, Deduplicated Profile Loading & UI Decoupling Verification Suite**
- **Jira**: ``ATT-4021``
- **Test Description**:
  1. *Atomic Single-Transaction Persistence Unit Tests (`TST-UI-393.1`, `SettingsDataStoreZoneAtomicTest.kt`)*:
  • Verify `SettingsDataStore.saveZoneProfile` executes a single atomic `dataStore.edit {}` block writing all zone thresholds (Z1..Z4 or Z1..Z6) and `ZoneDisplayOptions` together.
  • Verify rapid consecutive calls update DataStore serially without race conditions or partial writes.
  2. *ViewModel / SavedStateHandle Draft Preservation Unit Tests (`TST-UI-393.2`, `ZoneSettingsViewModelTest.kt`)*:
  • Simulate entering non-monotonic drafts (e.g. Z2 < Z1); verify draft is held in `SavedStateHandle` without persisting invalid state to DataStore.
  • Simulate ViewModel recreation with restored `SavedStateHandle`; verify uncommitted draft is restored cleanly without data loss.
  • Verify committing a valid monotonic draft triggers atomic save.
  3. *Consolidated Zone Loading & Migration Unit Tests (`TST-UI-393.3`, `ZoneProfileLoaderTest.kt` / `SettingsDataStoreZoneAtomicTest.kt`)*:
  • Verify single consolidated loader reads `ZoneProfileState` for `HR_RUN`, `HR_BIKE`, and `PWR_BIKE` with migration applied.
  • Verify zero duplicate loading logic across screen tabs.
  4. *Contract Test & Architectural Decoupling Audit (`TST-UI-393.4`, `ZonesSettingsHeaderContractTest.kt`, `ZonesSettingsPreviewPillsContractTest.kt`, `AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify all architectural contract tests in `ZonesSettingsHeaderContractTest`, `ZonesSettingsPreviewPillsContractTest`, and `AthleteProfileContractAndLocalizationTest` pass without modification.
  • Verify `AndroidManifest.xml` does not declare unstarted `ZonesSettingsActivity`.
  • Verify safe window insets controller execution without direct `(context as Activity)` cast.
  5. *Localization Parity Audit (`TST-UI-393.5`, `TranslationParityTest.kt`)*:
  • Verify 100% translation parity across all 9 supported locales (0 new UI strings introduced).
  6. *Clean-Room Full Suite Regression Execution (`TST-UI-393.6`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate across the full project test suite.

### TST-UI-394: **Global User Interface Localization Parity, Unit Adaptability, and Accessibility Semantics Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-429`, `REQ-LOC-001`, `REQ-UI-122`, `REQ-UI-157`
- **Component**: **Global User Interface Localization Parity, Unit Adaptability, and Accessibility Semantics Verification Suite**
- **Jira**: ``ATT-4026``
- **Test Description**:
  1. *9-Language Localization & Format Specifier Parity Test (`TST-UI-394.1`, `TranslationParityTest.kt`)*:
  • Verify all extracted string resources exist across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with identical positional argument tokens (`%1$s`, `%1$d`).
  2. *Crop Workout Screen Unit Adaptability Unit Tests (`TST-UI-394.2`, `CropWorkoutScreenTest.kt`)*:
  • Verify distance and speed values and unit labels display in miles and mph when `MyUnits.IMPERIAL` is active, and km and km/h when `MyUnits.METRIC` is active.
  3. *Workout Filter BottomSheet Imperial Conversion Unit Tests (`TST-UI-394.3`, `WorkoutFilterBottomSheetTest.kt`)*:
  • Verify distance chips display miles and convert to meters accurately using imperial multiplier when `MyUnits.IMPERIAL` is active.
  • Verify elevation chips display feet and convert to meters accordingly.
  4. *TalkBack Accessibility & Content Description Semantics Tests (`TST-UI-394.4`, `UiAccessibilityContractTest.kt`)*:
  • Verify interactive `IconButton` components provide non-null localized `contentDescription`s.
  • Verify canvas chart components expose semantic descriptions for TalkBack.
  5. *Clean-Room Full Suite Regression Execution (`TST-UI-394.5`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across the full test suite.

### TST-UI-395: **Multi-Provider Cloud Settings Layout Unification, Symmetric Configuration & Localization Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-430`, `REQ-UI-152`, `REQ-DAT-020.6`, `REQ-LOC-001`
- **Component**: **Multi-Provider Cloud Settings Layout Unification, Symmetric Configuration & Localization Parity Verification Suite**
- **Jira**: ``ATT-4666``
- **Test Description**:
  1. *Dropbox Settings Dialog State & Granular Switches Test (`TST-UI-395.1`, `DropboxSettingsDialogTest.kt`)*:
  • Verify disconnected state hides switches and shows `dropbox_connect_prompt`.
  • Verify connected state displays account email, upload workouts switch, upload backup switch, conditional backup interval selector, Wi-Fi only switch, and last sync/backup status.
  • Verify saving commits `uploadWorkoutsToDropbox`, `uploadBackupToDropbox`, `uploadToDropboxOnlyOnWifi`, and `backup_interval_days`, and reschedules `BackupWorker`.
  2. *Google Drive Settings Dialog Backup Interval & Parity Test (`TST-UI-395.2`, `GoogleDriveSettingsDialogTest.kt`)*:
  • Verify connected state displays backup interval selector when `uploadBackupToGoogleDrive` is true.
  • Verify saving commits `backup_interval_days` and reschedules `BackupWorker`.
  • Verify last backup timestamp is displayed alongside last sync.
  3. *Shared Cloud Component Architectural Contract Test (`TST-UI-395.3`, `ComposeSafePainterContractTest.kt`, `CloudSettingsComponentContractTest.kt`)*:
  • Verify `DropboxConnectionHeader` and `GoogleDriveConnectionHeader` delegate cleanly to `CloudConnectionHeader`.
  • Verify `ComposeSafePainterContractTest` passes with zero regressions on `DropboxSettingsDialog.kt` and `DropboxConnectionHeader.kt`.
  4. *9-Language Localization & Format Specifier Parity Test (`TST-UI-395.4`, `TranslationParityTest.kt`)*:
  • Verify `dropbox_connect_prompt`, `upload_workouts_to_dropbox_summary`, `upload_backup_to_dropbox_summary`, and `last_backup_never` exist across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with identical tokens.
  5. *Clean-Room Full Suite Regression Execution (`TST-UI-395.5`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate across the full project test suite.

### TST-UI-396: **Landscape Compact Aligned Marginal Zone Distribution Bars Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-431`, `REQ-UI-251`, `REQ-UI-206`
- **Component**: **Landscape Compact Aligned Marginal Zone Distribution Bars Verification Suite**
- **Jira**: ``ATT-2145``
- **Test Description**:
  1. *Landscape Proportional Dual-Pane Layout Composition Contract Test (`TST-UI-396.1`, `TelemetryMetricGraphLandscapeContractTest.kt`)*:
  • Verify that in landscape orientation (`Configuration.ORIENTATION_LANDSCAPE`), `TelemetryMetricGraph` branches into a side-by-side `Row` layout.
  • Verify the chart canvas is allocated proportional weight ~72% (`0.72f`) and the marginal distribution pane is allocated ~28% (`0.28f`).
  2. *Vertical Y-Axis Coordinate Alignment & Normalization Math Test (`TST-UI-396.2`, `TelemetryMetricGraphLandscapeContractTest.kt`)*:
  • Verify the top and bottom vertical boundaries of each horizontal zone bar match the exact Y-normalization coordinates of the telemetry graph zone thresholds.
  • Verify that top and bottom graph plot paddings are symmetrically respected by the marginal pane.
  3. *Granular Metrics Legibility & Formatting Contract Test (`TST-UI-396.3`, `TelemetryMetricGraphLandscapeContractTest.kt`)*:
  • Verify zone bars display legible text containing Zone Label (`Z1`–`Z5`), Percentage share, and formatted Duration (`TimeFormatter`).
  • Verify text contrast and bounds clipping prevention logic.
  4. *Synchronous Display Mode Switching Test (`TST-UI-396.4`, `TelemetryMetricGraphLandscapeContractTest.kt`)*:
  • Verify synchronous rendering transitions between 5-Zone horizontal bars (`FIVE_ZONES`, `COLUMNS`, `STACKED_BAR`) and frequency distribution bars (`HISTOGRAM`).
  5. *Portrait Invariant & Scrubber Alignment Regression Test (`TST-UI-396.5`, `TelemetryMetricGraphMarginalBarsContractTest.kt`)*:
  • Verify that portrait mode retains the 25dp inline canvas strip per `REQ-UI-251` with 100% backward compatibility.
  • Verify that `startPadding = 50.dp` is preserved across both orientations to guarantee multi-chart scrubber alignment.
  6. *Clean-Room Full Suite Regression Execution (`TST-UI-396.6`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate across the full project test suite.

### TST-UI-397: **In-App Feature Discovery & Tips Guide, Navigation Drawer Integration & 9-Language Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-432`
- **Component**: **In-App Feature Discovery & Tips Guide Verification Suite**
- **Jira**: ``ATT-2276``, ``ATT-4775``
- **Test Description**:
  1. *NavRoutes Route and Drawer ID Bidirectional Mapping Unit Test (`TST-UI-397.1`, `SingleActivityNavigationTest.kt`)*:
  • Verify `NavRoutes.TIPS_GUIDE == "tips_guide"`.
  • Verify `NavRoutes.fromDrawerItemId(R.id.drawer_tips_guide) == NavRoutes.TIPS_GUIDE`.
  • Verify `NavRoutes.toDrawerItemId(NavRoutes.TIPS_GUIDE) == R.id.drawer_tips_guide`.
  • Verify `NavRoutes.toSettingsBottomSheetType(R.id.drawer_tips_guide) == null`.
  2. *Navigation Drawer Group Structure & 23-Destination Item Integrity Unit Test (`TST-UI-397.2`, `NavigationDrawerStateTest.kt`)*:
  • Verify `createDrawerGroups` maintains exactly 5 category hubs.
  • Verify `settingsGroup.items` contains `R.id.drawer_tips_guide` positioned before `drawer_privacy_policy` and `drawer_advanced_tuning`.
  • Verify total destination count is 23 across all 5 groups, and distinct ID count is 23.
  • Verify default visible count across non-collapsible groups strictly remains 9.
  • Verify `drawer_tips_guide` icon is `R.drawable.ic_tips_and_updates` and title is `R.string.drawer_tips_guide`.
  3. *TipsGuideScreen Model, Expandable State & Deep-Link Dispatch Unit/Contract Test (`TST-UI-397.3`, `TipsGuideScreenContractTest.kt`)*:
  • Verify data model defines exactly 8 curated tips with respective title, subtitle, description, icon, and route targets.
  • Verify `cockpit_customization` uses `R.drawable.ic_table_edit` and `NavRoutes.TRACKING_LAYOUTS`.
  • Verify `sport_types` uses `R.drawable.ic_sports_combined` and `NavRoutes.SPORT_TYPES`.
  • Verify all 8 deep-link action callbacks (`onNavigateToLocations`, `onNavigateToSensors`, `onNavigateToCockpitLayouts`, `onNavigateToSegments`, `onNavigateToEquipment`, `onNavigateToZones`, `onNavigateToBackup`, `onNavigateToSportTypes`) dispatch cleanly when triggered.
  4. *9-Language Localization & Format Specifier Parity Test (`TST-UI-397.4`, `TranslationParityTest.kt`)*:
  • Verify all 36 string resources are defined across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  • Verify 100% translation coverage, matching positional format specifiers, and literal `\n` linebreaks.
  5. *Clean-Room Full Suite Regression Execution (`TST-UI-397.5`)*:
  • Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.localization.*" --tests "com.atrainingtracker.trainingtracker.ui.help.*"` and verify 100% pass rate.

### TST-UI-398: **WorkoutDetailTabbedContent Vertical Space Reclamation, Collapsing Header, Single-Line Landscape Header, Inline Zoom Controls, and 72% Plot Alignment Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-433`, `REQ-UI-408`, `REQ-UI-431`, `REQ-UI-225`
- **Component**: **WorkoutDetailTabbedContent Vertical Space Reclamation Verification Suite**
- **Jira**: ``ATT-4774``
- **Test Description**:
  1. *Collapsing Header NestedScroll Contract Test (`TST-UI-398.1`, `WorkoutDetailTabbedContentTest.kt`)*:
  • Verify `WorkoutDetailTabbedContent` integrates `CollapsingAppBarNestedScrollConnection`.
  • Verify that downward vertical scroll offsets translate the header container upwards up to `appBarMaxHeight`.
  • Verify quick-return behavior on upward scroll and verify landscape collapses both header and tab row.
  2. *Landscape Single-Line Compact WorkoutHeader Layout Test (`TST-UI-398.2`, `WorkoutHeaderLandscapeTest.kt`)*:
  • Verify that under `Configuration.ORIENTATION_LANDSCAPE`, `WorkoutHeader` renders in a single horizontal `Row` of maximum 44dp height.
  • Verify title, cluster pill, sport name, date/time, and action buttons are present in the single row without text truncation.
  • Verify that under `Configuration.ORIENTATION_PORTRAIT`, `WorkoutHeader` maintains its multi-row vertical layout.
  3. *Zero-Extra-Row Inline / Floating Zoom Controls Contract Test (`TST-UI-398.3`, `MapDetailLayoutZoomOverlayTest.kt`)*:
  • Verify `MapDetailLayout` does not allocate a dedicated full-width 36dp vertical row for `GlobalTelemetryZoomToolbar`.
  • Verify zoom controls are positioned as a compact overlay or inlined, leaving vertical space to charts.
  4. *Landscape Stacked Charts 72% Plot Width & Double-Padding Elimination Contract Test (`TST-UI-398.4`, `TelemetryMetricGraphLandscapeAlignmentTest.kt`)*:
  • Verify `ElevationProfile`, `Speed/Pace`, and `W_PRIME_BAL` charts in landscape mode align their right plot boundary to the 72% horizontal boundary when stacked with HR/Power graphs.
  • Verify `TelemetryMetricGraph` with marginal zone distribution sets `endPaddingPx = 0.dp` within its 72% plot area, eliminating double padding.
  • Verify vertical scrubbing cursor and X-axis ticks align 1:1 across all stacked charts.
  5. *Clean-Room Full Suite Regression Execution (`TST-UI-398.5`)*:
  • Execute targeted unit tests and verify 100% pass rate with zero regressions.

### TST-UI-399: **TrackingTabsScreen Landscape Vertical Space Optimization, Center Status Bar Sensor Icons, and Auto-Hiding Floating Tab Row Suite** [Verified]
- **Requirement(s)**: `REQ-UI-434`, `REQ-UI-163`, `REQ-UI-410`, `REQ-UI-412`
- **Component**: **TrackingTabsScreen Landscape Vertical Space Optimization Suite**
- **Jira**: ``ATT-4773``
- **Test Description**:
  1. *Landscape Center Status Bar Sensor Icons Contract Test (`TST-UI-399.1`, `TrackingTabsLandscapeLayoutTest.kt`)*:
  • Verify that under `Configuration.ORIENTATION_LANDSCAPE` during `ScreenMode.TRACKING`, `SensorStatus` availability icons render centered within the status bar strip without redundant vertical `statusBarsPadding()`.
  • Verify that the drawer button does not collide with system insets.
  2. *Auto-Hiding Floating Tab Row Overlay Contract Test (`TST-UI-399.2`, `TrackingTabsLandscapeLayoutTest.kt`)*:
  • Verify `PrimaryScrollableTabRow` is rendered as a floating overlay with `zIndex(1f)` over `HorizontalPager` without taking permanent layout height.
  • Verify visibility is triggered by `pagerState.isScrollInProgress`, page change, or Page 0 (`ControlTab`), and auto-dismisses after a 2.5–3.0 second timer.
  3. *Portrait & Configuration Mode Invariants Contract Test (`TST-UI-399.3`, `TrackingTabsLandscapeLayoutTest.kt`)*:
  • Verify that in portrait orientation, `statusBarsPadding()`, `SensorStatus`, and `PrimaryScrollableTabRow` remain persistently stacked.
  • Verify that in `ScreenMode.CONFIGURATION` and `ScreenMode.PREVIEW`, configuration and preview headers remain persistently visible.
  4. *Clean-Room Full Suite Regression Execution (`TST-UI-399.4`)*:
  • Execute targeted unit tests and verify 100% pass rate with zero regressions.

### TST-UI-435: **Tracking Tabs Mode Transition Initial Page Resolution Test Suite** [Verified]
- **Requirement(s)**: `REQ-UI-435`
- **Component**: `TrackingTabsPageResolverTest.kt`, `TrackingTabsScreen.kt`
- **Jira**: `ATT-4838`
- **Test Description**:
  1. *Drawer Configuration Entry Resolution (`TST-UI-435.1`)*:
  • Unit test in `TrackingTabsPageResolverTest` verifying `resolveInitialPage`:
    - Returns `0` when transitioning from `ScreenMode.TRACKING` to `ScreenMode.CONFIGURATION` regardless of `lastKnownPage` (`lastKnownPage = 1` or `lastKnownPage = 2`).
    - Returns `0` when `activityTypeChanged = true` in `ScreenMode.CONFIGURATION`.
  2. *Editor-Preview Round-Trip Resolution (`TST-UI-435.2`)*:
  • Unit test in `TrackingTabsPageResolverTest` verifying `resolveInitialPage`:
    - Returns `lastKnownPage` when transitioning `ScreenMode.CONFIGURATION -> ScreenMode.PREVIEW`.
    - Returns `lastKnownPage` when transitioning `ScreenMode.PREVIEW -> ScreenMode.CONFIGURATION`.
    - Clamps to valid bounds `0 until tabCount`.
  3. *Preview Exit to Tracking Resolution (`TST-UI-435.3`)*:
  • Unit test in `TrackingTabsPageResolverTest` verifying `resolveInitialPage`:
    - Returns `lastKnownPage + 1` when transitioning `ScreenMode.PREVIEW -> ScreenMode.TRACKING` with `tabCount > 0`.
    - Returns `0` when `tabCount <= 0`.
  4. *Same-Mode Tab Addition/Deletion Stability (`TST-UI-435.4`)*:
  • Unit test verifying `lastKnownPage` is preserved and clamped within valid page bounds when mode is unchanged.

### TST-UI-436: **Active Training Zone Segment Height Emphasis & Bar-Only Sizing Test Suite** [Verified]
- **Requirement(s)**: `REQ-UI-436`
- **Component**: `SensorFieldZoneGaugeTest.kt`, `ZoneGaugeBar.kt`, `SensorFieldView.kt`, `WPrimeBatteryGaugeBarLayoutTest.kt`
- **Jira**: `ATT-4860`, `ATT-4928`
- **Test Description**:
  1. *Active Segment Height Elevation & Inactive Uniformity Test (`TST-UI-436.1`)*:
  • Unit test in `SensorFieldZoneGaugeTest.kt` verifying reserved active segment height calculation (`calculateReservedActiveTrackHeight(trackHeight) = trackHeight * 1.6f`).
  • Verify that outer container height `totalBarHeight` is 100% identical between live (`needleFraction != null`, `activeZoneIndex != null`) and stale/null (`needleFraction == null`, `activeZoneIndex == null`) telemetry across `trackHeight` values (`6.dp`, `10.dp`, `14.dp`, `18.dp`, `22.dp`) (ATT-4928).
  • Verify that when `activeZoneIndex == null` or `isStale == true`, all segments render at base `trackHeight` with `0.35f` opacity without changing outer container height.
  2. *Dynamic Height Scaling in Bar-Only Mode Test (`TST-UI-436.2`)*:
  • Unit test in `SensorFieldZoneGaugeTest.kt` verifying track height resolution across `ViewSize.SMALL`, `NORMAL`, `LARGE`, `EXTRA_LARGE` for `ZoneGaugeBar`, `PedalPowerBalanceBar`, and `WPrimeBatteryGaugeBar` when `!fieldState.showValue && fieldState.showBar`.
  3. *Needle Span Overlay Geometry & Sibling Height Contract Test (`TST-UI-436.3`)*:
  • Verify `ZoneGaugeBar` needle container height accommodates elevated active segment (`calculateReservedActiveTrackHeight(trackHeight) + 4.dp`) in both live and stale states.
  • Verify `PedalPowerBalanceBar` and `WPrimeBatteryGaugeBar` maintain invariant container heights across live and stale states.
  4. *Clean-Room Full Suite Regression Execution (`TST-UI-436.4`)*:
  • Execute targeted unit tests and verify 100% pass rate with zero regressions.
  5. *Code & Test Hygiene Verification (`TST-UI-436.5`)*:
  • Verify removal of unused `calculateActiveTrackHeight(baseTrackHeight: Dp, isActive: Boolean)` overload in `ZoneGaugeBar.kt`.
  • Verify `WPrimeBatteryGaugeBarLayoutTest.kt` asserts `.height(barHeight)` without relying on commented-out code.

### TST-UI-437: **User-Configurable Horizontal Width Ratios & Split Presets in Cockpit Sensor Grid Test Suite** [Verified]
- **Requirement(s)**: `REQ-UI-437`
- **Component**: `TrackingViewsDatabaseManagerTest.kt`, `TrackingViewsRepositoryTest.kt`, `EditSensorFieldViewModelTest.kt`, `SensorGridScreenLayoutTest.kt`, `SensorGridLocalizationTest.kt`
- **Jira**: `ATT-4861`, `ATT-5077`, `ATT-5192`
- **Test Description**:
  1. *SQLite Migration v16 -> v17 & Default Weight Backfill Test (`TST-UI-437.1`)*:
  • Unit test in `TrackingViewsRealSqliteMigrationTest.kt` / `TrackingViewsDatabaseManagerTest.kt` verifying database upgrade from schema version 16 to 17:
    - Adds `ColWeight real DEFAULT 1.0` to `LayoutRowsTable`.
    - Backfills `ColWeight = 1.0` for all existing records.
    - Confirms `DatabaseSchemaRegistry.isVersionSupported("TrackingViews.db", 17)` returns `true`.
  2. *SensorFieldConfig & Cursor Mapping Symmetry Test (`TST-UI-437.2`)*:
  • Unit test in `TrackingViewsRepositoryTest.kt` verifying `colWeight` mapping:
    - Verifies symmetric mapping in single and batch cursor extraction paths (Rule 20).
    - Verifies custom weights (e.g. `3.0f`, `2.0f`, `1.0f`) are preserved and mapped correctly into `SensorFieldConfig` and `SensorFieldState`.
  3. *Edit Dialog Relative Weight Selection Test (`TST-UI-437.3`)*:
  • Unit test in `EditSensorFieldViewModelTest.kt` verifying:
    - Selection of individual relative weights (1x, 2x, 3x) updates state (`colWeight = 1.0f, 2.0f, 3.0f`) and persists to repository.
    - Zero/invalid weights are safely clamped to `1.0f`.
    - Obsolete row-split presets and sibling field mutations are removed.
  4. *Compose Layout Proportional Width Rendering Test (`TST-UI-437.4`)*:
  • Unit test in `SensorGridScreenLayoutTest.kt` verifying:
    - `Modifier.weight(effectiveWeight)` is applied to multi-column rows in `SensorGridContent` and configuration mode.
    - Non-zero safety clamp: invalid weights ($\le 0f$) safely fallback to `1.0f`.
    - Single-column rows naturally take 100% width.
  5. *9-Language Localization Parity Audit (`TST-UI-437.5`)*:
  • Audit in `TranslationParityTest` verifying all active string keys exist across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) and obsolete `sensor_field_row_split_presets` is removed across all 9 locales.
  6. *Edit Dialog Multi-Field Row Relative Width Visibility Test (`TST-UI-437.6`)*:
  • Unit test in `EditSensorFieldViewModelTest.kt` verifying:
    - Pure helper `calculateIsMultiFieldRow(existingFieldsInTargetRow, isNewField)` returns `false` for single-field rows and `true` when row has $\ge 2$ fields.
    - `EditDialogUiState.isMultiFieldRow` is hydrated correctly when editing existing single-field vs multi-field rows, and when inserting new fields into new vs existing rows (ATT-5192).

### TST-UI-438: **Vertical Card Height and Border Stretching in Cockpit Sensor Grid Test Suite** [Verified]
- **Requirement(s)**: `REQ-UI-438`
- **Component**: `SensorGridScreenLayoutTest.kt`, `SensorFieldViewLayoutTest.kt`, `SensorGridLocalizationTest.kt`
- **Jira**: `ATT-4878`
- **Test Description**:
  1. *Multi-Column Row Height Propagation Test (`TST-UI-438.1`)*:
  • Unit/layout test verifying that in `SensorGridScreen.kt`, for multi-column rows in `SensorGridContent`, landscape packing, and configuration mode:
    - Per-cell `Box` applies `Modifier.weight(effectiveWeight).fillMaxHeight()`.
    - `SensorFieldView` receives `modifier = Modifier.fillMaxHeight()`.
    - Shorter cards stretch to the measured height of the tallest card in the row.
  2. *Card & Indicator Full-Height Border Alignment Test (`TST-UI-438.2`)*:
  • Unit/layout test in `SensorFieldViewLayoutTest.kt` verifying:
    - `Card` applies `modifier.fillMaxWidth().fillMaxHeight()`.
    - Inner layout `Row` applies `.fillMaxHeight()`.
    - Indicator strips (`Spacer`) and borders span 100% of the stretched card height.
  3. *Vertical Centering & Layout Docking Test (`TST-UI-438.3`)*:
  • Unit/layout test in `SensorFieldViewLayoutTest.kt` verifying:
    - Label and filter description `Row` remains anchored to top.
    - Configuration `IconButton`s remain anchored to bottom.
    - Value and gauge elements are vertically centered in the body space.
  4. *Localization Parity and Visual Regression Guard (`TST-UI-438.4`)*:
  • Test verifying localization parity across all 9 locales and confirming single-field or uniform-size rows preserve existing dimensions and styling.

### TST-UI-439: **Copy Tracking Tab to Target Activity Types Test Suite** [Verified]
- **Requirement(s)**: `REQ-UI-439`
- **Component**: `TrackingTabConfigHeaderContractTest.kt`, `TrackingViewsDatabaseManagerTest.kt`, `TrackingViewsRepositoryTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-5078`, `ATT-5189`
- **Test Description**:
  1. *Tracking Tab Config Header Copy Button Test (`TST-UI-439.1`)*:
  • Contract test verifying that `TrackingTabConfigHeader.kt` renders an `IconButton` with `Icons.Default.ContentCopy`, content description `R.string.cd_copy_tab`, and invokes `onCopyTab(tabViewId)`.
  2. *Database Transactional Deep-Copy, Append & Target Selection Unselected Default Contract Test (`TST-UI-439.2`)*:
  • Contract/unit test verifying `CopyTrackingTabDialog.kt` initializes `selectedTypes` with `emptySet<ActivityType>()`, ensuring all sport profiles start unselected by default and the Copy action button remains disabled (`saveEnabled = false`) until at least one target profile is explicitly selected (ATT-5189).
  • Unit test in `TrackingViewsDatabaseManagerTest.kt` verifying that calling `copyTabViewToActivityTypes(sourceViewId, targetTypes)`:
    - Atomically duplicates the view row in `VIEWS_TABLE` for each target `ActivityType` at `LAYOUT_NR = max(LAYOUT_NR) + 1`.
    - Duplicates all child `ROWS_TABLE` records with new `view_id`, preserving row/col indices, sensor types, sizes, filters, and column weights.
    - Duplicates all child `SECTION_DIVIDERS_TABLE` records with new `view_id`, preserving row numbers and titles.
    - Supports duplication within the same `ActivityType` (appending duplicate tab at the end of the current profile).
  3. *Repository & ViewModel Auto-Navigation Test (`TST-UI-439.3`)*:
  • Unit test in `TrackingViewsRepositoryTest.kt` and `TrackingTabsViewModelTest.kt` verifying:
    - `copyTabView` executes on `Dispatchers.IO` and triggers `configUpdateTrigger`.
    - When active profile is targeted, `_navigationEvent` emits `TabNavigationEvent.NavigateTo(newIndex)`.
  4. *9-Language Localization Parity Audit (`TST-UI-439.4`)*:
  • Test in `TranslationParityTest.kt` verifying presence and formatting of `cd_copy_tab`, `copy_tab_title`, and `copy_tab_action` across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  5. *Full Clean-Room Unit Regression Suite (`TST-UI-439.5`)*:
  • Clean-room execution of unit tests ensuring zero regressions (`REQ-PRO-001`).

### TST-UI-440: **Move Tracking Tab Left/Right Test Suite** [Verified]
- **Requirement(s)**: `REQ-UI-440`
- **Component**: `TrackingTabConfigHeaderContractTest.kt`, `TrackingViewsDatabaseManagerTest.kt`, `TrackingViewsRepositoryTest.kt`, `TrackingTabsViewModelTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-5079`
- **Test Description**:
  1. *Tracking Tab Config Header Move Buttons & Boundary Gating Test (`TST-UI-440.1`)*:
  • Contract/unit test in `TrackingTabConfigHeaderContractTest.kt` verifying `TrackingTabConfigHeader.kt`:
    - Declares Move Left (`Icons.AutoMirrored.Filled.ArrowBack`) and Move Right (`Icons.AutoMirrored.Filled.ArrowForward`) icon buttons with content descriptions `R.string.cd_move_tab_left` and `R.string.cd_move_tab_right`.
    - Disables Move Left when `tabIndex == 0`.
    - Disables Move Right when `tabIndex >= tabCount - 1`.
  2. *Database Scoped Atomic Tab Swap Test (`TST-UI-440.2`)*:
  • Unit test in `TrackingViewsDatabaseManagerTest.kt` verifying `moveTabView(viewId, moveLeft)`:
    - Atomically swaps `LAYOUT_NR` with adjacent neighbor tab within the same `ActivityType`.
    - Normalizes `LAYOUT_NR` to continuous 1..N order.
    - Leaves tabs belonging to other `ActivityType` profiles completely untouched.
    - Returns updated index.
  3. *Repository & ViewModel Pager Navigation Test (`TST-UI-440.3`)*:
  • Unit test in `TrackingViewsRepositoryTest.kt` and `TrackingTabsViewModelTest.kt` verifying:
    - `moveTabView` executes on `Dispatchers.IO` and triggers `configUpdateTrigger`.
    - `TrackingTabsViewModel.onMoveTab(tabViewId, moveLeft)` sends `TabNavigationEvent.NavigateTo(newIndex)`.
  4. *9-Language Localization Parity Audit (`TST-UI-440.4`)*:
  • Test in `TranslationParityTest.kt` asserting presence and format specifier parity of `cd_move_tab_left` and `cd_move_tab_right` across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  5. *Full Clean-Room Unit Regression Suite (`TST-UI-440.5`)*:
  • Clean-room execution of unit tests ensuring zero regressions (`REQ-PRO-001`).

### TST-UI-441: **Intrinsic Minimum Height Bounding for Standalone Sensor Field View Containers Test Suite** [Verified]
- **Requirement(s)**: `REQ-UI-441`
- **Component**: `ZonesSettingsPreviewLayoutTest.kt`, `SensorFieldViewLayoutTest.kt`
- **Jira**: `ATT-5071`
- **Test Description**:
  1. *ZonesSettingsActivity Preview Container Height Bounding Test (`TST-UI-441.1`)*:
  • Unit/contract test in `ZonesSettingsPreviewLayoutTest.kt` verifying:
    - The preview container wrapping `SensorFieldView` in `ZonesSettingsActivity.kt` applies `Modifier.height(IntrinsicSize.Min)`.
    - When rendered in an unbounded vertical container, `SensorFieldView` does not collapse to zero height or clip telemetry.
  2. *Sensor Field View Telemetry and Indicator Visibility Test (`TST-UI-441.2`)*:
  • Test in `SensorFieldViewLayoutTest.kt` / `ZonesSettingsPreviewLayoutTest.kt` verifying:
    - Telemetry value, units, and gauge bar are measured with non-zero height inside `height(IntrinsicSize.Min)` parent.
    - Left/right indicator strips span the full intrinsic height of the card.
  3. *Cockpit Grid Equal-Height Regression Guard (`TST-UI-441.3`)*:
  • Contract/unit test verifying `SensorGridScreen.kt` multi-column equal-height behavior and `REQ-UI-438` invariants remain intact with zero regression.
  4. *Clean-Room Targeted Test Suite Execution (`TST-UI-441.4`)*:
  • Run targeted package unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.*"` ensuring 100% pass rate.

### TST-UI-442: **Bluetooth LE Sensor Vector Drawables Full-Bleed Badge, Uppercase Labels, Centered Cyclist & Top-Left Mini-Icons Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-442`
- **Component**: `VectorAssetResolutionContractTest.kt`, `UIHelper.java`
- **Jira**: `ATT-4956`, `ATT-5201`
- **Test Description**:
  1. *Full-Bleed Badge Geometry & Official Protocol Color Verification (`TST-UI-442.1`)*:
  • Contract test in `VectorAssetResolutionContractTest.kt` asserting that all 6 BTLE drawables (`bt_bike_pwr`, `bt_bike_spd`, `bt_bike_cad`, `bt_bike_speed_and_cadence`, `bt_hr`, `bt_run`) declare full-bleed `0..144` badge paths with corner radius $r=22.0$ and official Bluetooth color `#0A3D91`, with zero remaining `#07377F` or inset paths.
  2. *Uppercase Bottom Label & evenOdd Counter Hole Contract (`TST-UI-442.2`)*:
  • Test in `VectorAssetResolutionContractTest.kt` verifying that all 6 BTLE drawables include bold uppercase text path data (`PWR`, `SPD`, `CAD`, `S&C`, `HR`, `SPD`) with `evenOdd` fillType for counter holes, and no lowercase words (`power`, `speed`, `cadence`, `s & c`).
  3. *Centered Cyclist, Top-Left Canonical Mini-Icons & Rule 37 Geometric Primitive Audit (`TST-UI-442.3`)*:
  • Test in `VectorAssetResolutionContractTest.kt` verifying that the cyclist motif is centered horizontally ($x \in [28.5, 115.5]$), top-left canonical mini-icons reside in $x \le 52, y \le 50$, and all 6 drawables satisfy Rule 37 ($\le 45$ `L` commands per path, analytical curves `A`/`C`/`Q`).
  4. *Resource Resolution & Clean-Room Suite Execution (`TST-UI-442.4`)*:
  • Assert `UIHelper.getIconId()` and `BSportType` mappings for all BTLE sensors continue to resolve to valid non-zero resource IDs with zero regression (`REQ-PRO-001`).

### TST-UI-443: **Power TelemetryMetricGraph Marginal Histogram Unfolded Linear Bins Projection Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-443`
- **Component**: `ZoneDistributionModels.kt`, `TelemetryMetricGraph.kt`, `TelemetryMetricGraphMarginalBarsContractTest.kt`, `TelemetryMetricGraphLandscapeContractTest.kt`
- **Jira**: `ATT-5190`, `ATT-5281`, `ATT-5311`
- **Test Description**:
  1. *TelemetryHistogramData Unfolded Linear Bins Unit Tests (`TelemetryHistogramCalculatorTest.kt`)*:
  • Unit tests verifying `TelemetryHistogramData.linearBins` returns `bins` directly when `sprintTail == null`.
  • Unit tests verifying `TelemetryHistogramData.linearBins` excludes `isOverflow` and `isReturn` bins and unrolls full linear bins spanning `[dataMin, dataMax]` when `sprintTail != null`.
  2. *Portrait Marginal Strip Unfolded Linear Bins & Boundary Clamping Contract Test (`TelemetryMetricGraphMarginalBarsContractTest.kt`)*:
  • Contract test verifying `TelemetryMetricGraph.kt` portrait marginal strip logic accesses `histogram.linearBins` and computes `maxBinPct` from `linearBins`.
  • Contract test verifying background track rendering is drawn unconditionally for every bin in `linearBins` within `[dataMin, dataMax]` (ATT-5281).
  • Contract test verifying out-of-domain bins (`rangeMax <= dataMin || rangeMin >= dataMax`) are filtered and clamped so no zero-height or stacked rectangles occur at boundaries (`y = 0`) (ATT-5311, Rule 70).
  3. *Landscape Aligned Marginal Zone Distribution Unfolded Linear Bins & Boundary Clamping Contract Test (`TelemetryMetricGraphLandscapeContractTest.kt`)*:
  • Contract test verifying `AlignedMarginalZoneDistribution` accesses `histogram.linearBins` and computes `maxBinPct` from `linearBins`.
  • Contract test verifying background track rendering is drawn unconditionally for every bin in `linearBins` within `[dataMin, dataMax]` (ATT-5281).
  • Contract test verifying out-of-domain bins (`rangeMax <= dataMin || rangeMin >= dataMax`) are filtered and clamped so zero out-of-domain bins stack at `y = 0` (ATT-5311, Rule 70).
  4. *Clean-Room Targeted Test Suite Execution (`TST-UI-443.4`)*:
  • Run targeted package unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"` confirming 100% pass rate.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-18 | AI Agent (`ATT-8206`) | `ATT-8206` | Split oversized shard into compliant sub-shard (<= 4,000 lines, `REQ-PRO-093.4`, `REQ-PRO-127.3`). |
| `1.0.1` | 2026-11-20 | Lane C Flash (`ATT-9179`, `--as agent1`) | `ATT-9179` | Set `TST-UI-334` to `[Planned]` for `ATT-9179`: replace stale `ElevationProfileKt` reflection stubs in `ElevationProfileGradeColorTest.kt` with behavioral `createComposeRule()` rendering of `GradeLegend()` (`ElevationProfileOverlayComponents.kt:180`) across all 7 `grade_legend_*` labels. |
| `1.0.2` | 2026-11-20 | Lane C Flash (`ATT-9179`, `--as agent1`) | `ATT-9179` | Promoted `TST-UI-334` to `[Verified]` in Stage 5 with verbatim test citations (`ATT-9179_walkthrough.md`, `Rule 7.5` Gate 4 `ATT-9342` @ `10bde5ec`, `ElevationProfileGradeColorTest` `5/5`, `ElevationProfileOverlayTest` `5/5`, `ElevationProfileClimbShadingTest` `4/4`). |
| `1.0.3` | 2026-11-21 | AI Agent 1 (`ATT-6041`) | `ATT-6041` | Added `TST-UI-2789` `[Planned]` covering marginal histogram/tick helpers, `PathPreviewMap` cluster thumbnail delegation, and `Rule 8.5` PNG inspection. |
| `1.0.4` | 2026-11-21 | AI Agent 1 (`ATT-6041`) | `ATT-6041` | Promoted `TST-UI-2789` to `[Implemented]` in Stage 4 (`TelemetryChartCanvasHelpersTest.kt`, `ClusterThumbnailPathPreviewDelegationTest.kt`). |
| `1.0.5` | 2026-11-21 | AI Agent 1 (`ATT-6041`) | `ATT-6041` | Promoted `TST-UI-2789` to `[Verified]` in Stage 5 (Rule 7.5 Fast Path reusing Gate 4 `ATT-9433` @ `2b4277ca`: `277/277` PASS across 61 suites). |
| `1.0.7` | 2026-11-23 | AI Agent 1 (`ATT-9065`) | `ATT-9065` | Updated `TST-UI-313` for `ATT-9065` Compose dismissal, cold-start hydration, and broadcast suppression verification; promoted to `[Verified]` (`53/53` Tier-1 tests PASS). |
