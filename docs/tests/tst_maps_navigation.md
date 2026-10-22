# Living Test Specification: Maps, Polylines & Navigation

**Domain**: `maps_navigation`  
**Prefixes**: `GEO`, `MAP`, `NAV`, `ROU`  
**Scope**: Map rendering, polylines, navigation overlays, GPX track follow-me, and routing.  
**Total Tests**: 52

---

### TST-NAV-001: **Navigation** [Drawer is organized logically. All promoted items are accessible and functional. Preferences screen is clean and focused.]
- **Requirement(s)**: `REQ-SET-050`
- **Component**: **Navigation**
- **Jira**: ``ATT-239``
- **Test Description**: 1. Open the navigation drawer and verify the new sections (Training, Spatial, My Equipment, Ecosystem, System). 2. Verify 'Sport Types' and 'Training Zones' are in 'My Equipment'. 3. Verify individual items for 'Strava' and 'Dropbox' are in 'Ecosystem'. 4. Open 'Preferences' and verify moved items are no longer present.

### TST-NAV-002: **Navigation** [Drawer labels are updated to professional terminology across supported languages.]
- **Requirement(s)**: `REQ-UI-112`
- **Component**: **Navigation**
- **Jira**: ``ATT-249``
- **Test Description**: 1. Open the navigation drawer. 2. Verify section "Equipment" (EN) / "Ausrüstung" (DE). 3. Verify section "Synchronization" (EN) / "Synchronisation" (DE). 4. Verify item "Dateiexport" (DE).

### TST-NAV-003: **Direct Config Navigation** [1. An 'AlertDialog' to choose Activity Type appears immediately over the current screen. 2. Selecting a type opens the configuration cockpit (TrackingTabsFragment in CONFIGURATION mode) for that type.]
- **Requirement(s)**: `REQ-SET-051`
- **Component**: **Direct Config Navigation**
- **Jira**: ``ATT-258``
- **Test Description**: 1. Open Navigation Drawer -> Settings. 2. Tap 'Tracking Tabs'.

### TST-NAV-004: **Direct Display Settings Dialog & State Parity Verification** [1. Display switches display in the ON state by default, achieving 100% parity with runtime behavior.
2. Multiple switches can be toggled without state overwrites.
3. Host activity dynamically reflects flag and orientation changes upon dismissal.
4. Automated unit tests pass with zero regressions.]
- **Requirement(s)**: `REQ-SET-052`
- **Component**: **Direct Display Settings Dialog & State Parity Verification**
- **Jira**: ``ATT-261` / `ATT-1016``
- **Test Description**:
  1. Ensure `pref_display_options` is unset in SharedPreferences (default state).
  2. Open Navigation Drawer -> Settings -> Display.
  3. Verify all three switches ("force portrait", "keep screen on", "keep screen unlocked") appear in the ON (checked) state, matching `TrainingApplication` defaults.
  4. Toggle "keep screen on" to OFF, then toggle "keep screen unlocked" to OFF. Verify both switches remain OFF and SharedPreferences is updated with `["forcePortrait"]`.
  5. Toggle "force portrait" to OFF and dismiss dialog. Verify `MainActivityWithNavigation` dynamically clears orientation lock (`SCREEN_ORIENTATION_UNSPECIFIED`) and clears `FLAG_SHOW_WHEN_LOCKED`.
  6. Run automated regression suite `./gradlew testDebugUnitTest` including `DisplaySettingsTest`.

### TST-NAV-005: **Direct Units Settings Dialog** [1. A dialog to choose between Metric and Imperial appears immediately over the current screen. 2. Selecting a unit updates the application state.]
- **Requirement(s)**: `REQ-SET-053`
- **Component**: **Direct Units Settings Dialog**
- **Jira**: ``ATT-262``
- **Test Description**: 1. Open Navigation Drawer -> Settings. 2. Tap 'Units'.

### TST-NAV-006: **Direct Export Settings Dialog** [1. A dialog containing file export settings appears immediately over the current screen. 2. Toggling formats works as expected.]
- **Requirement(s)**: `REQ-SET-054`
- **Component**: **Direct Export Settings Dialog**
- **Jira**: ``ATT-264``
- **Test Description**: 1. Open Navigation Drawer -> Ecosystem. 2. Tap 'Export'.

### TST-NAV-007: **Declarative Navigation Drawer & Item Density Verification** [Navigation drawer renders deterministically via Jetpack Compose with compact density, reactive title updates, and zero inflation errors.]
- **Requirement(s)**: `REQ-UI-123`
- **Component**: **Declarative Navigation Drawer & Item Density Verification**
- **Jira**: ``ATT-526` / `ATT-243``
- **Test Description**:
  1. Open Navigation Drawer and verify that `AppNavigationDrawer` renders smoothly without `InflateException` or crashes.
  2. Verify that drawer item vertical spacing is compact (~40-42dp height) and fits all hubs cleanly without excessive scrolling.
  3. Verify all 5 categories (Training, Spatial, Equipment, Synchronization, System) and their navigation items route correctly to corresponding screens or dialogs.
  4. Start tracking, pause tracking, and stop tracking, verifying that the top tracking drawer item label reactively updates between "Start Tracking", "Tracking", and "Pause".
  5. Execute unit tests and run regression suite `./gradlew testDebugUnitTest`.

### TST-NAV-008: **MainActivityWithNavigation Kotlin Migration & Lifecycle Verification** [`MainActivityWithNavigation` compiles cleanly in Kotlin, executes without regression across all existing unit tests, and reproduces 100% of runtime navigation and lifecycle behavior on device.]
- **Requirement(s)**: `REQ-UI-124`
- **Component**: **MainActivityWithNavigation Kotlin Migration & Lifecycle Verification**
- **Jira**: ``ATT-658``
- **Test Description**:
  1. Build debug APK (`./gradlew assembleDebug`) and verify clean compilation of `MainActivityWithNavigation.kt` with zero warnings or errors.
  2. Execute full regression unit test suite (`./gradlew testDebugUnitTest`).
  3. Verify Java-to-Kotlin interop for companion object constants (`MainActivityWithNavigation.EXTRA_RESUME_INTERRUPTED_WORKOUT`, `SELECTED_FRAGMENT_ID`, `SELECTED_FRAGMENT`).
  4. Launch `MainActivityWithNavigation` on test device:
  • Verify initial fragment loading (`TrackingTabsFragment`).
  • Open drawer and navigate between hubs/fragments (Workouts, Periods, Map, Segments, Routes, Locations, Sensors, Bikes, Shoes, Sport Types, Training Zones, Strava, Dropbox, Export).
  • Trigger modal dialogs from drawer (Units, Display Settings, Tracking Layouts / Sport Selection, Export, Search Settings).
  • Test interrupted workout resumption intent handling.
  • Test back gesture closing navigation drawer without exiting the app.

### TST-NAV-009: **Single-Activity Compose Navigation & Architectural Interop Verification** [Single-activity Compose architecture compiles cleanly, passes 100% of unit tests and regression tasks, maintains all Java/bytecode interop contracts, and executes flawlessly on physical hardware with zero UI flicker or sensor drops.]
- **Requirement(s)**: `REQ-UI-159`
- **Component**: **Single-Activity Compose Navigation & Architectural Interop Verification**
- **Jira**: ``ATT-1083``
- **Test Description**:
  1. Verify build classpath alignment with `./gradlew :app:dependencies` asserting `androidx.core:1.15.0` strict pinning under `REQ-STB-007`.
  2. Execute automated unit test suite `MainActivityWithNavigationInteropTest.kt` verifying `@JvmField` constants, `SelectedFragment` enum, class hierarchy, and public method signatures (`navigateToDrawerItem`, `startPairing`).
  3. Verify automated unit tests asserting Compose navigation destination routing, drawer state transitions, and backstack restoration across simulated process death.
  4. Run full repository clean-room regression suite `./gradlew testDebugUnitTest`.
  5. Live physical verification on hardware device (`66020DLCR002FL`):
  • Verify application cold start directly into `NavRoutes.START_TRACKING`.
  • Open navigation drawer and navigate to each of the 21 destinations.
  • Open each settings bottom sheet (Strava, Dropbox, Export, Units, Display, Search) and verify zero status/navigation bar flicker.
  • Open soft keyboard on input sheets and verify IME insets padding (`adjustResize`).
  • Rotate device during active sensor telemetry and verify zero disconnect/rebind storms.
  • Test interrupted workout recovery notification intent launch.

### TST-MAP-010: **Zoom-Adaptive Heatmap Styling** [1. At level 11/12, lines are thin and member markers (pins) are HIDDEN. 2. At level 17+, individual traces are prominent and member markers appear clearly.]
- **Requirement(s)**: `REQ-MAP-016`
- **Component**: **Zoom-Adaptive Heatmap Styling**
- **Jira**: ``ATT-437``
- **Test Description**: 1. Open a Workout Cluster heatmap. 2. Zoom out to level 11/12. 3. Observe line width and marker pins. 4. Zoom in to level 17+.

### TST-MAP-020: **Detailed Map View Heatmap Visibility** [The translucent density heatmap remains clearly visible over the route area at high zoom levels (13 to 18) and does not vanish or turn blank.]
- **Requirement(s)**: `REQ-MAP-018`
- **Component**: **Detailed Map View Heatmap Visibility**
- **Jira**: ``ATT-538``
- **Test Description**: 1. Open a Workout Cluster heatmap view or a Period Detail map view. 2. Zoom in to a detailed level (zoom 13 to 18) over a route area. 3. Inspect the map overlay.

### TST-MAP-021: **Minimum Altitude Marker Spatial Positioning Verification** [The minimum altitude marker renders at the true geographic minimum point of the activity without being diverted by GPS glitches or unanchored start conditions.]
- **Requirement(s)**: `REQ-MAP-019`
- **Component**: **Minimum Altitude Marker Spatial Positioning Verification**
- **Jira**: ``ATT-594``
- **Test Description**: 1. Execute `LiveWorkoutSessionTest` to verify that starting without GPS fix anchors `minPos` to the first valid GPS fix at baseline altitude. 2. Simulate multi-sensor live tracking with barometric and GPS altitude; verify that non-best GPS altitude dips do not contaminate `min` or `minPos`. 3. Open a workout map view and verify that `ic_altitude_min` is positioned at the lowest geographic elevation point (e.g. trailhead), matching the minimum point on the elevation chart.

### TST-MAP-022: **Authoritative Max Line Distance & Apex Coordinate Integrity** [The Max Line Distance marker (`ic_distance`) is positioned exactly at the true point of maximum straight-line distance from the start point across all recorded and legacy workouts.]
- **Requirement(s)**: `REQ-MAP-020`
- **Component**: **Authoritative Max Line Distance & Apex Coordinate Integrity**
- **Jira**: ``ATT-611``
- **Test Description**: 1. Verify `SpeedAndLocationDevice` instantiates `mLineDistanceSensor` as `MySensor<Double>` and does not alter initial value upon `RESET_ACCUMULATORS_INTENT`. 2. Simulate live workout tracking with branching trajectory (short spur vs long return); verify `LiveWorkoutSession` anchors line distance directly to `startLatLng`. 3. Simulate `finalizeLiveSession()` and verify apex coordinate and max line distance are derived deterministically from the recorded polyline geometry. 4. Verify existing workouts with corrupted or missing apex coordinates self-heal to the true maximum displacement point on the track.

### TST-MAP-023: **Dark Mode Map Styling, Vector Tile Configuration & Anti-Flash Surface Mask Verification** [All map views apply AMOLED dark vector tile styling when surface luminance indicates dark mode, containers enforce `#121212` background and surface mask eliminating tile loading flashes during list scrolling, `MapType.TERRAIN` is preserved in light mode; all unit and regression tests pass.]
- **Requirement(s)**: `REQ-MAP-021`
- **Component**: **Dark Mode Map Styling, Vector Tile Configuration & Anti-Flash Surface Mask Verification**
- **Jira**: ``ATT-1266`, `ATT-1479`, `ATT-1536`, `ATT-1537`, `ATT-1538`, `ATT-1539`, `ATT-1553`, `ATT-1555``
- **Test Description**: 1. Unit Test Map Style JSON Validation: Verify `map_style_dark.json` is valid JSON, background `#121212`, water `#0A1118`, POI labels hidden, road styling defined, text labels `#9E9E9E`, singleton caching returns identical instance, and corrupt JSON safely falls back to unstyled `MapType.NORMAL`.\<br\>2. Unit Test Dynamic Map Properties Resolution (`DarkMapStyleResolutionTest.kt`): Verify `resolveMapProperties(isDark, context)` returns `MapType.NORMAL` with dark styling when `isDark=true`, and `MapType.TERRAIN` with null styling when `isDark=false`.\<br\>3. Anti-Flash Surface Mask Verification (`DarkMapAntiFlashOverlayTest.kt`): Verify `DarkMapAntiFlashOverlay` renders an opaque `#121212` Box when `isDark=true` and `isMapLoaded=false`, and renders nothing when `isMapLoaded=true` or `isDark=false`.\<br\>4. Secondary Map Composables & Anti-Flash Audit: Verify `PathPreviewMap`, `EditKnownLocationDialog`, `WorkoutClusterComponents`, `ManualClusterScreen`, `PeriodSummaryCard`, `LapEditBottomSheet`, `ImportBackupTabsScreen`, and `ATrainingTrackerMap` adopt dynamic properties and anti-flash surface mask protection in dark mode.\<br\>5. UI & Clean-Room Regression: Verify dark mode rendering across all map components without white loading flashes and execute `./gradlew testDebugUnitTest`.

### TST-MAP-024: **Google Maps CameraUpdateFactory Initialization Resilience and Lifecycle Guarding Verification** [Camera bounds animation in ImportBackupTabsScreen is strictly deferred until map loading completes; sequential cluster candidates in the import queue actively trigger camera re-alignment and zoom bounds fitting once loaded; exceptions during early layout are caught and handled via fallback static centering; 100% test pass rate with zero crashes.]
- **Requirement(s)**: `REQ-MAP-022`
- **Component**: **Google Maps CameraUpdateFactory Initialization Resilience and Lifecycle Guarding Verification**
- **Jira**: ``ATT-1579`, `ATT-2022``
- **Test Description**:
  1. *Map Readiness & Sequential Queue Recomposition Tests (`ImportBackupMapResilienceTest.kt`)*:
  • Verify that when `isMapLoaded == false`, camera animation/movement is deferred even if `bounds != null`.
  • Verify that when `isMapLoaded == true` and `bounds != null`, camera framing execution occurs.
  • Verify that `isMapLoaded` state does NOT reset to `false` when switching items in the recovery queue once loaded (`remember`).
  • Verify that camera bounds update triggers for sequential cluster candidates in the queue (`LaunchedEffect(state, bounds, isMapLoaded)`).
  2. *Defensive Exception Handling & Fallback Framing Tests (`ImportBackupMapResilienceTest.kt`)*:
  • Verify that if `CameraUpdateFactory.newLatLngBounds` throws `NullPointerException` (uninitialized Maps renderer) or `IllegalStateException` (zero-sized layout view), the exception is caught without crashing the application.
  • Verify that upon catching an exception, the fallback camera position centers on `CameraPosition.fromLatLngZoom(bounds.center, 12f)`.
  3. *Comprehensive Bounds Envelope Calculation (Track + Markers) Tests*:
  • Verify that empty or degenerate polylines yield null bounds without throwing exceptions.
  • Verify that valid multi-point decoded polylines build non-null bounds with accurate center coordinates and include `state.start`, `state.end`, and `state.apex`.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-MAP-025: **Prominent High-Contrast Rendering for Actively Navigated Routes & Multi-Layer X-Ray Segment Synergy Verification** [Actively navigated routes render with 16f width and vibrant Electric Emerald `#00E676` color; multi-layer X-Ray polyline sandwich preserves Strava segment visibility; directional chevrons calculate bearing accurately; 100% test pass rate.]
- **Requirement(s)**: `REQ-MAP-023`
- **Component**: **Prominent High-Contrast Rendering for Actively Navigated Routes & Multi-Layer X-Ray Segment Synergy Verification**
- **Jira**: ``ATT-1841``
- **Test Description**:
  1. *Visual Hierarchy & Active Navigation Prominence Unit Tests (`MapRouteActiveNavigationTest.kt`)*:
  • When `isActiveNavigation == true`: verify `color == TTColor.RouteActiveNavigation` (`#00E676`), `width == 16f`, `zIndex == 25f`, `overlayZIndex == 45f`.
  • When `isSelected == true && !isActiveNavigation`: verify `color == TTColor.RouteSelected` (`#228B22`), `width == 10f`, `zIndex == 20f`, `overlayZIndex == 40f`.
  • When unselected: verify `color == TTColor.RouteUnselected`, `width == 6f`, `zIndex == 5f`.
  2. *Multi-Layer X-Ray Synergy Invariant Assertion (`MapRouteActiveNavigationTest.kt`)*:
  • Verify mathematically: $Z_{\text{base,passive}} (20\text{f}) < Z_{\text{base,active}} (25\text{f}) < Z_{\text{segment}} (30\text{f}) < Z_{\text{overlay,passive}} (40\text{f}) < Z_{\text{overlay,active}} (45\text{f})$.
  • Verify active overlay pattern contains alternating `Dash` and `Gap` items.
  3. *RoutesRepository Active Navigation State Tests (`RoutesRepositoryActiveNavigationTest.kt`)*:
  • Verify `activeNavigatedRouteId` defaults to `null`.
  • Verify `setActiveNavigatedRoute(42L)` emits `42L` and `setActiveNavigatedRoute(null)` emits `null`.
  4. *Directional Chevrons Geometry Tests (`MapRouteChevronsTest.kt`)*:
  • Verify `calculateBearing` computes accurate forward heading across cardinal directions.
  • Verify chevron windowing samples polyline segments without index exceptions.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-MAP-026: **Quick Route Selector from Cockpit with GPS Proximity Sorting and Automated Route Detection Verification** [Multi-stage ranking resolves home-hub tie-breaking; adaptive chips display dynamically; 1-tap activation and deselection operational; automated route detection accurately detects matching polylines; 100% test pass rate.]
- **Requirement(s)**: `REQ-MAP-024`
- **Component**: **Quick Route Selector from Cockpit with GPS Proximity Sorting and Automated Route Detection Verification**
- **Jira**: ``ATT-1835``
- **Test Description**:
  1. *Multi-Stage Proximity Sorting & Tie-Breaking Unit Tests (`RouteProximityRankerTest.kt`)*:
  • Tier 1: Routes starting within 250m form immediate top group.
  • Tier 2: Routes matching active sport profile take precedence over non-matching sports.
  • Tier 3: Moving athlete heading alignment ($\Delta \text{bearing} \le 45^\circ$) prioritizes eastbound routes when riding east.
  • Tier 4: Most recently ridden routes (*"Zuletzt gefahren"*) and frequently used home routes rank higher than rarely used tracks.
  • Tier 5: Fallback sorting by ascending distance and alphabetical name.
  • Null/degenerate location handling: routes ranked by recency/name when GPS is unavailable.
  2. *Adaptive Filter Chips Threshold & Clean State Tests (`RouteSelectorSheetTest.kt`)*:
  • When total route count $< 5$: Filter chips (distance, sort) are hidden.
  • When total route count $\ge 5$: Filter chips are visible and interactive.
  • Empty route collection renders empty state without crashing.
  3. *1-Tap Activation & Deselection Unit Tests (`RouteSelectorSheetTest.kt`)*:
  • Tapping a route invokes `RoutesRepository.toggleRouteSelection` / `setActiveNavigatedRoute`.
  • Tapping "Route beenden / abwählen" clears active navigation.
  4. *Automated Route Detection Engine (`RouteAutoDetector`)*:
  • Distance $\le 30\text{ m}$ for $\ge 200\text{ m}$ with heading alignment ($\Delta \theta \le 35^\circ$) triggers candidate detection.
  • Off-route or divergent heading ($\Delta \theta > 35^\circ$) does not trigger detection.
  • Prompt mode vs. seamless auto-join modes verified.
  5. *Tuning Preferences Persistence Tests (`RouteAutoDetectPreferencesTest.kt`)*:
  • `routeAutoDetectEnabled` (default: `true`), join mode (`PROMPT` vs `AUTO`), and threshold (default: `200m`) persist in `TuningPreferencesDataStore`.
  6. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) for route selector tokens.
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-MAP-027: **Automatic DEM Elevation Enrichment for Imported GPX Routes Lacking Altitude Verification** [Altitude-deficient GPX files are detected and automatically enriched via chunked DEM API calls ($\le 100$ coordinates/batch); routes with existing elevations are preserved without network calls; offline and API failures fallback gracefully to 2D track without crashing; 100% test pass rate.]
- **Requirement(s)**: `REQ-MAP-025`
- **Component**: **Automatic DEM Elevation Enrichment for Imported GPX Routes Lacking Altitude Verification**
- **Jira**: ``ATT-1502``
- **Test Description**:
  1. *Deficiency Detection & Elevation Preservation Unit Tests (`GpxRouteImporterElevationTest.kt`)*:
  • Verify that a GPX file with existing non-null, non-zero elevations (`<ele>`) preserves original altitude without invoking `ElevationService`.
  • Verify that a GPX file lacking `<ele>` tags or with flat 0.0m altitude triggers `ElevationService` batch enrichment.
  2. *Batching & Query Length Protection Tests (`GpxRouteImporterElevationTest.kt`)*:
  • Verify that coordinates exceeding 100 points ($N = 250$) are chunked into 3 sequential requests of size 100, 100, and 50, preventing HTTP 414 URI Too Long errors.
  • Verify that retrieved elevations are accurately mapped to the corresponding `PathPoint.altitude` values in correct sequence.
  3. *Metrics Recalculation Tests (`GpxRouteImporterElevationTest.kt`)*:
  • Verify that `RouteSummary.elevationGain` is updated to the sum of positive elevation deltas (> 0m) after enrichment.
  4. *Resilient Offline Fallback & Circuit Breaker Tests (`GpxRouteImporterElevationTest.kt`)*:
  • Verify that network timeout, HTTP 429 rate limit, or server error (500) during enrichment does NOT throw an exception or abort the import.
  • Verify that on failure, the route imports successfully with default 0.0m altitudes and 0.0m elevation gain.
  5. *UI State & ViewModel Integration Tests (`GpxImportViewModelTest.kt`)*:
  • Verify that `GpxImportViewModel` notifies progress or loading state during enrichment and transitions to `Editing` with enriched summary and points.
  6. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify that any new user-facing enrichment strings exist across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-MAP-028: **Support Waypoints, POIs (Benches, Water, Summits) and TCX Course Points Verification** [GPX `<wpt>` and TCX `<CoursePoint>` elements are parsed, classified, projected with cumulative distance along route, persisted in SQLite `route_waypoints` with `ON DELETE CASCADE`, and rendered on the map with interactive markers; 100% test pass rate with zero regressions.]
- **Requirement(s)**: `REQ-MAP-026`
- **Component**: **Support Waypoints, POIs (Benches, Water, Summits) and TCX Course Points Verification**
- **Jira**: ``ATT-58``
- **Test Description**:
  1. *GPX Waypoint Extraction & Classification Unit Tests (`GpxRouteImporterWaypointTest.kt`)*:
  • Verify that `<wpt>` elements in GPX are extracted with latitude, longitude, elevation, name, and description.
  • Verify keyword classification into `POI_WATER`, `POI_BENCH`, `POI_SUMMIT`, `POI_FOOD`, `POI_DANGER`, `POI_VIEWPOINT`, and `GENERIC`.
  2. *TCX Course & Course Point Extraction Unit Tests (`TcxCourseParserTest.kt`)*:
  • Verify TCX XML parsing of `<Course>`, `<Trackpoint>`, and `<CoursePoint>`.
  • Verify mapping of TCX `PointType` (`Left`, `Right`, `Straight`, `Water`, `Food`, `Summit`, `Danger`) to `WaypointType`.
  3. *Distance Along Route Projection Unit Tests (`WaypointDistanceCalculatorTest.kt`)*:
  • Verify perpendicular projection of waypoints onto polyline segments.
  • Verify calculated `distance_from_start` is monotonic and within $[0, \text{totalDistance}]$.
  4. *Database Schema v10 & Cascade Deletion Tests (`RoutesDatabaseManagerWaypointTest.kt`)*:
  • Verify `RoutesDbHelper` upgrades from v9 to v10 creating `route_waypoints` table.
  • Verify inserting and querying waypoints by route ID.
  • Verify deleting a route cascades and purges associated rows from `route_waypoints`.
  5. *Map Layer Marker Integration Tests (`RouteWaypointLayerTest.kt`)*:
  • Verify waypoint marker generation with appropriate `WaypointType` icons and zIndex $\ge 50\text{f}$.
  • Verify marker click dispatches detail callback.
  6. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify all new waypoint strings exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-MAP-029: **Persistent Climbs Database & Live ClimbPro Cockpit Sheet Verification** [Climbs are detected from route profiles, deduplicated within 50m, persisted in SQLite `Climbs.db`, tracked via `LiveClimbsRepository`, and visualized in Cockpit BottomSheet with standard slope colors; 100% test pass rate.]
- **Requirement(s)**: `REQ-MAP-027`
- **Component**: **Persistent Climbs Database & Live ClimbPro Cockpit Sheet Verification**
- **Jira**: ``ATT-1281``
- **Test Description**:
  1. *Climb Detection & Category Unit Tests (`ClimbDetectorTest.kt`)*:
  • Verify detection of ascents matching length $\ge 500\text{m}$, average grade $\ge 3.0\%$, and vertical gain $\ge 20\text{m}$.
  • Verify micro-dip tolerance preserving single continuous climb.
  • Verify climb category scoring (Cat 4, Cat 3, Cat 2, Cat 1, HC).
  2. *Persistent Climbs Database & Deduplication Tests (`ClimbsDatabaseManagerTest.kt`)*:
  • Verify SQLite schema creation, insertion, queries, and deletion in `Climbs.db`.
  • Verify spatial deduplication skipping or merging ascents within 50m of start and end.
  3. *Live Climb Tracking State Machine Tests (`LiveClimbsRepositoryTest.kt`)*:
  • Verify transitions: `FAR_FAR_AWAY`, `APPROACHING` ($\le 250\text{m}$), `ON_CLIMB`, and `FINISHED`.
  • Verify route navigation climb sequencing vs free riding detection.
  4. *Cockpit Sheet UI Contract & Priority Tests*:
  • Verify `LiveClimbSheet` displays real-time remaining distance, remaining elevation gain, and slope colors (`TTColor.Zone1` to `Zone5`).
  • Verify Strava Live Segment visual priority during active overlap.
  5. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify all new climb strings exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-MAP-030: **Visual & Auditory Turn-by-Turn Navigation Prompts, Battery Saver Wake-Up & Off-Route Alerts Verification** [Turn cues are detected or extracted from course points, countdown distances track accurately, HUD banner displays direction and remaining meters, display wakes in battery saver mode, audio chimes alert athlete, and off-route deviations trigger warnings; 100% test pass rate.]
- **Requirement(s)**: `REQ-MAP-028`
- **Component**: **Visual & Auditory Turn-by-Turn Navigation Prompts, Battery Saver Wake-Up & Off-Route Alerts Verification**
- **Jira**: ``ATT-1450``
- **Test Description**:
  1. *Geometric Turn Detection Unit Tests (`TurnCueDetectorTest.kt`)*:
  • Verify turn detection for sharp, regular, and slight turns ($\ge 30^\circ$) across polyline segments.
  • Verify suppression of minor heading noise ($\le 10^\circ$).
  • Verify accurate and monotonic `distanceFromStart` calculation.
  2. *Turn-by-Turn Navigation Engine State Lifecycle Tests (`TurnByTurnNavigationEngineTest.kt`)*:
  • Verify transition to `APPROACHING` when distance to next cue $\le 150\text{m}$.
  • Verify transition to `TURN_NOW` when distance $\le 25\text{m}$.
  • Verify cue passing and auto-progression to next cue when $> 20\text{m}$ past turn vertex.
  • Verify `OFF_ROUTE` transition when cross-track error $> 50\text{m}$ for 3 consecutive fixes, and recovery back to `ON_ROUTE` when $\le 30\text{m}$.
  3. *Battery Saver Wake-Up & Audio Alert Triggers Tests (`TurnAlertTriggersTest.kt`)*:
  • Verify `BatterySaverController.onWakeupEvent()` called upon `APPROACHING` and `OFF_ROUTE`.
  • Verify distinct audio chime dispatch for approach, immediate turn, off-route, and route recovery.
  4. *Cockpit HUD Banner UI Contract & Priority Tests*:
  • Verify `TurnPromptBanner` displays directional arrow, remaining distance, and street name.
  • Verify off-route warning display with distance back to route.
  5. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify all new turn cue and off-route strings exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-MAP-031: **"Take Me Home" Return Navigation, Remaining Distance & Elevation-Aware ETA HUD Verification** [Home base is resolved from KnownLocations, return corridor snaps to active or saved route, dynamic ETA calculates elevation-aware duration factoring in climb, and Cockpit HUD renders glanceable metrics; 100% test pass rate.]
- **Requirement(s)**: `REQ-MAP-029`
- **Component**: **"Take Me Home" Return Navigation, Remaining Distance & Elevation-Aware ETA HUD Verification**
- **Jira**: ``ATT-1953``
- **Test Description**:
  1. *Home Base Resolution Unit Tests (`HomeLocationResolverTest.kt`)*:
  • Verify resolving location explicitly named "Zu Hause", "Home", or "Zuhause".
  • Verify resolving location with highest hitCount for `ExtremaType.START` when name is generic.
  • Verify graceful null/empty handling when no known locations exist.
  2. *Elevation-Aware Dynamic ETA Unit Tests (`ElevationAwareEtaCalculatorTest.kt`)*:
  • Verify flat terrain ($H_{\text{climb}} = 0$) where $T = D / V$.
  • Verify climbing terrain ($H_{\text{climb}} = 300\text{m}$, $D = 5000\text{m}$) adds climbing ascent penalty ($300\text{m} / \text{VAM}$).
  • Verify downhill descent does not reduce flat travel time below zero.
  • Verify formatted clock time and duration strings.
  3. *Return Corridor Snapper Unit Tests (`ReturnCorridorSnapperTest.kt`)*:
  • Verify active route forward remaining path calculation.
  • Verify active route reverse return path calculation back to start.
  • Verify candidate route selection from `allRoutes` terminating near home.
  4. *Cockpit HUD Banner UI Contract & Priority Tests*:
  • Verify `ReturnNavigationHud` displays destination, remaining distance, remaining elevation gain, and dynamic ETA.
  • Verify 1-tap entry points in `RouteActionChipRow` and `RouteSelectorSheet`.
  5. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify all new return navigation and HUD strings exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-MAP-032: **[SUPERSEDED / REMOVED by ATT-2463 / TST-MAP-037] Corridor-Based Route Grouping & Differentiating Thumbnail Zoom Verification** [Gateway classification and differentiating bounding boxes excised; test suite cleaned; superseded by TST-MAP-037.]
- **Requirement(s)**: `REQ-MAP-030`
- **Component**: **[SUPERSEDED / REMOVED by ATT-2463 / TST-MAP-037] Corridor-Based Route Grouping & Differentiating Thumbnail Zoom Verification**
- **Jira**: ``ATT-1954``
- **Test Description**: **SUPERSEDED & REMOVED**: Corridor-based route grouping and differentiating thumbnail zoom capabilities were removed in ATT-2463. Tests `RouteCorridorClassifierTest.kt`, `RouteBoundingBoxCalculatorTest.kt`, and `RouteCorridorUiIntegrationTest.kt` were deleted. Core geodesic calculations were migrated to `GeoUtilsTest.kt` under `TST-MAP-037`.

### TST-MAP-033: **In-Ride Fork-in-the-Road Route Selection & Decision Alerts Verification** [Route candidates matching outbound corridor are identified, upcoming forks trigger Cockpit HUD card within 300m, directional arrows and route metrics display accurately, manual and autonomous binding activate active navigation with configurable auto-follow and departure auto-unselection; 100% test pass rate.]
- **Requirement(s)**: `REQ-MAP-031`
- **Component**: **In-Ride Fork-in-the-Road Route Selection & Decision Alerts Verification**
- **Jira**: ``ATT-1955`, `ATT-3320`, `ATT-3326``
- **Test Description**:
  1. *Outbound Corridor Matching Unit Tests (`ForkRouteMatcherTest.kt`)*:
  • Verify tracking candidate routes within $\le 50\text{ m}$ outbound buffer for $\ge 300\text{ m}$.
  • Verify filtering out routes that diverge prior to current position or follow reverse orientation.
  2. *Divergence Vertex Detection Unit Tests (`RouteDivergenceDetectorTest.kt`)*:
  • Verify detection of divergence coordinate where candidate paths split $> 40\text{ m}$.
  • Verify remaining distance calculation $D_{\text{fork}}$ to the fork.
  • Verify relative bearing computation mapping branch vectors to Left, Straight, or Right.
  • Verify alert trigger when $0 < D_{\\text{fork}} \le 300\text{ m}$.
  3. *Autonomous Route Binding Unit Tests (`ForkNavigationRepositoryTest.kt`)*:
  • When `autoFollowUniqueRouteEnabled == true` (default): Verify auto-binding when athlete travels $\ge 50\text{ m}$ along candidate branch with cross-track error $< 25\text{ m}$ and alternative $> 50\text{ m}$, verifying immediate activation of turn-by-turn prompts (`REQ-MAP-028`) and climb guidance (`REQ-MAP-027`).
  • When `autoFollowUniqueRouteEnabled == false`: Verify auto-binding is suppressed (zero calls to `setActiveNavigatedRoute`), and the fork decision alert is dismissed cleanly (`state == null`).
  • Verify prompt dismissal when athlete deviates $> 50\text{ m}$ from all candidate routes.
  • Verify manual branch selection on `ForkDecisionCard` binds route immediately regardless of the toggle setting.
  4. *Cockpit Decision HUD Card UI Contract Tests (`ForkDecisionCardTest.kt`)*:
  • Verify card displays directional arrows, route names, distances, and elevation gains.
  • Verify 1-tap branch selection triggers route binding.
  • Verify dismiss button cleans prompt state.
  5. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify all decision card labels, direction hints, dismiss strings, and Advanced Navigation tuning strings (`tuning_auto_follow_unique_route_title`, `tuning_auto_follow_unique_route_desc`) across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.
  7. *Route Selection Provenance & Departure Auto-Unselection (`TurnAlertTriggersTest.kt`, `ForkNavigationRepositoryTest.kt`, `RoutesRepositoryActiveNavigationTest.kt`)*:
  • Verify that when active route was bound automatically (`isRouteAutoSelected == true`), triggering `isOffRoute` clears active route (`setActiveNavigatedRoute(null)`) and dismisses off-route alerts.
  • Verify that when active route was selected manually (`isRouteAutoSelected == false`), triggering `isOffRoute` retains active route selection.
  • Verify that clearing an auto-selected route upon leaving corridor re-enables candidate fork monitoring.

### TST-MAP-034: **Domain-Agnostic Scrubber Marker Synchronization & Telemetry Graph Auto-Centering Verification** [Touching or dragging across ElevationProfile or TelemetryMetricGraph (Speed, HR, Power) in TIME or DISTANCE domain reliably displays the directional scrubber marker and auto-centers the camera; trackless (0,0) coordinates are safely guarded; 100% test pass rate.]
- **Requirement(s)**: `REQ-MAP-032`
- **Component**: **Domain-Agnostic Scrubber Marker Synchronization & Telemetry Graph Auto-Centering Verification**
- **Jira**: ``ATT-2340``
- **Test Description**:
  1. *ScrubMarkerLayer Unit & Contract Tests (`ScrubMarkerLayerTest.kt`)*:
  • Verify that when `activeScrubPoint` is provided with valid GPS coordinates, the marker is placed at `activeScrubPoint.latLng`.
  • Verify that when `activeScrubPoint` is null and `selectedDistance` is provided, the marker resolves via fallback.
  • Verify that when `point.latLng` is `(0.0, 0.0)`, no marker is rendered.
  • Verify that `isWestbound` orientation correctly reflects travel direction along the route.
  2. *ScrubberController Unit Tests (`ScrubberControllerTest.kt`)*:
  • Verify that `ScrubberController` animates camera to `point.latLng` when `activeScrubPoint` is provided.
  • Verify that coordinates at `(0.0, 0.0)` do not trigger camera animations.
  3. *MapDetailLayout Scrub Synchronization Contract Tests*:
  • Verify that `MapDetailLayout` propagates `activeScrubPoint` into `ATrainingTrackerMap`.
  • Verify that `TelemetryMetricGraph` fires `onPointSelected` with the resolved `PathPoint`.
  4. *Clean-Room Full Suite Regression*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-MAP-035: **High-Contrast Maki POI Waypoint Badges, Shelter Classification & Substring Disambiguation Verification** [High-contrast circular badge markers with category-specific vibrant tinting, crisp white halo rings, outer contrast borders, and centered Maki glyphs across interactive maps and route thumbnails; POI_SHELTER classification for shelters and huts; disambiguation of "Kreuzung" false positives; 100% 9-language localization parity; 100% test pass rate.]
- **Requirement(s)**: `REQ-MAP-033`
- **Component**: **High-Contrast Maki POI Waypoint Badges, Shelter Classification & Substring Disambiguation Verification**
- **Jira**: ``ATT-2461``
- **Test Description**:
  1. *GPX & TCX Heuristic Disambiguation Tests (`WaypointTypeClassificationTest.kt`)*:
  • Verify "Kreuzung Waldweg" maps to `GENERIC` (not `POI_SUMMIT`).
  • Verify "Gipfelkreuz auf dem Jusi" maps to `POI_SUMMIT`.
  • Verify "Unterstand", "Schutzhütte", "Refuge" map to `POI_SHELTER` (not `POI_BENCH` or `POI_FOOD`).
  • Verify TCX point type `"shelter"` maps to `POI_SHELTER`.
  2. *Real-World GPX Integration Test (`SchwaebischeAlbWaypointIntegrationTest.kt`)*:
  • Verify Nebelhöhle waypoint with description "Unterstand" is asserted as `WaypointType.POI_SHELTER`.
  3. *High-Contrast Badge Marker Rendering Tests (`WaypointBadgeMarkerTest.kt`)*:
  • Verify `createWaypointBadgeMarker` returns non-null `BitmapDescriptor` with category disc, white halo, and outer dark stroke for all `WaypointType`s.
  4. *Route Card Thumbnail Map Integration Tests (`RouteItemWaypointContractTest.kt`)*:
  • Verify `PathPreviewMap.kt` invokes `RouteWaypointsLayer` when `path is MapRoute && path.waypoints.isNotEmpty()`.
  5. *9-Language Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify `waypoint_type_shelter` exists across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  6. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate.

### TST-MAP-036: **Designated Home-Base Selection, Database Migration & Substring Disambiguation Verification** [Explicit home-base selection for return navigation; single-home SQLite transactional exclusivity; complete elimination of "Rathaus"/"Gasthaus" false positives; prominent visual indicators and context menu/edit dialog actions in Known Locations UI; 100% 9-language localization parity; 100% test pass rate.]
- **Requirement(s)**: `REQ-MAP-034`
- **Component**: **Designated Home-Base Selection, Database Migration & Substring Disambiguation Verification**
- **Jira**: ``ATT-2472``
- **Test Description**:
  1. *Database Migration & Transactional Exclusivity (`KnownLocationsDatabaseManagerHomeTest.kt`)*:
  • Verify DB upgrade from v5 to v6 creates column `is_home`.
  • Verify `setHomeLocation(id)` sets `is_home = 1` for target id and clears all other rows atomically.
  • Verify `clearHomeLocation()` clears all `is_home` rows.
  • Verify `getHomeLocation()` returns designated `MyLocation` with `isHome == true`.
  2. *Home Location Resolution & False Positive Prevention (`HomeLocationResolverTest.kt`)*:
  • Verify `resolveHomeLocation` returns designated `isHome` location even if another location has higher hitCount or contains "Gasthaus".
  • Verify locations named "Rathaus" or "Gasthaus" without `isHome` designation do NOT match as home.
  • Verify fallback to highest `hitCount` (> 0) when no location has `isHome`.
  • Verify fallback to first location when all hitCounts are 0.
  3. *Repository Reactive StateFlow Tests (`KnownLocationsRepositoryTest.kt`)*:
  • Verify `setHomeLocation(id)` and `clearHomeLocation()` update reactive `locationsFlow`.
  4. *UI & Dialog Contract Tests (`KnownLocationHomeContractTest.kt`)*:
  • Verify `KnownLocationsScreen.kt` renders Home icon and Home badge when `item.isHome == true`.
  • Verify `KnownLocationCard` context menu contains set/clear home actions.
  • Verify `EditKnownLocationDialog.kt` contains Home Base toggle switch.
  5. *9-Language Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify `known_locations_home_base`, `known_locations_home_badge`, `known_locations_set_home`, `known_locations_remove_home` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  6. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate.

### TST-MAP-037: **Standalone Geodesic Calculation Utilities & Flat Route List Presentation Verification** [Standalone GeoUtils calculates geodesic distance and initial bearing deterministically; RouteTabbedScreen presents clean flat route list without gateway direction chips; RouteItem previews render full route bounds; obsolete classes and strings cleanly excised; 100% test pass rate.]
- **Requirement(s)**: `REQ-MAP-035`
- **Component**: **Standalone Geodesic Calculation Utilities & Flat Route List Presentation Verification**
- **Jira**: ``ATT-2463``
- **Test Description**:
  1. *Standalone Geodesic Utilities (`GeoUtilsTest.kt`)*:
  • Verify `GeoUtils.haversineDistanceMeters` against known geographic coordinates (e.g. 0,0 to 0,1; Paris to London; antipodal/identical points).
  • Verify `GeoUtils.calculateInitialBearing` across cardinal and intercardinal bearings ($0^\circ \text{ North}, 90^\circ \text{ East}, 180^\circ \text{ South}, 270^\circ \text{ West}$) and normalized to $[0.0, 360.0)$.
  • Verify mathematical parity with callers `ForkRouteMatcher.kt` and `RouteDivergenceDetector.kt`.
  2. *Clean Flat Route List Presentation Contract (`RouteTabbedCleanLayoutTest.kt`)*:
  • Verify `RouteTabbedScreen.kt` does NOT reference `GatewayFilterChipsRow` or gateway directions.
  • Verify `RouteList.kt` does NOT accept or forward `focusedThumbnailZoomEnabled`.
  • Verify `RouteItem.kt` does NOT invoke `RouteBoundingBoxCalculator` and passes `targetBounds = null` to `PathPreviewMap`.
  3. *Excision & DataStore Tolerant Fallback (`TuningPreferencesDataStoreTest.kt`)*:
  • Verify absence of active `corridorGroupingEnabled` and `focusedThumbnailZoomEnabled` properties.
  • Verify legacy DataStore entries do not crash or throw exceptions.
  4. *9-Language Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify obsolete gateway direction strings (`gateway_all`, `gateway_north`, etc.) and corridor preferences are completely removed across all 9 locales without dangling keys.
  5. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-MAP-039: **Route Waypoint Marker Badge Diameter Optimization & Glanceability Proportional Scaling Verification** [Route waypoint badge marker default diameter reduced to 22 dp with proportional stroke scaling; MapLayers integrates markerSizeDp; 100% 9-language translation parity; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-MAP-037`
- **Component**: **Route Waypoint Marker Badge Diameter Optimization & Glanceability Proportional Scaling Verification**
- **Jira**: ``ATT-2628``
- **Test Description**:
  1. *Default Marker Diameter Constant & Scaling Verification (`WaypointBadgeMarkerTest.kt`)*:
  • Verify `DEFAULT_WAYPOINT_MARKER_SIZE_DP == 22`.
  • Verify `createWaypointBadgeMarker` adopts `DEFAULT_WAYPOINT_MARKER_SIZE_DP` as its default argument.
  • Verify `createWaypointBadgeMarker` calculates proportional stroke scaling with minimal crisp hairline clamping across sizes (20 dp, 22 dp, 24 dp, 32 dp).
  • Verify non-empty, valid BitmapDescriptor generation across all 10 `WaypointType`s at 22 dp.
  2. *Composable Map Layer Integration Contract Test (`RouteItemWaypointContractTest.kt`)*:
  • Verify `RouteWaypointsLayer` exposes parameter `markerSizeDp: Int = DEFAULT_WAYPOINT_MARKER_SIZE_DP`.
  • Verify `RouteWaypointsLayer` invokes `createWaypointBadgeMarker(ctx, waypoint.type, markerSizeDp)` and includes `markerSizeDp` in its remember cache key.
  3. *9-Language Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify string resources for all waypoint types across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-MAP-040: **In-Ride Fork Route Detection Performance Optimization Verification** [Bounding box spatial pre-filtering eliminates distant routes in O(1); sport profile filtering excludes incompatible disciplines; GPS evaluation throttled to 3s / 20m during quiescent tracking while preserving 1 Hz responsiveness during active fork alerts; 100% test pass rate.]
- **Requirement(s)**: `REQ-MAP-038`
- **Component**: **In-Ride Fork Route Detection Performance Optimization Verification**
- **Jira**: ``ATT-2873``
- **Test Description**:
  1. *Spatial Bounding-Box Rejection Unit Tests (`ForkRouteMatcherTest.kt`)*:
  • Verify that distant routes (e.g. routes starting 500m or 10km away whose bounding box does not enclose `currentPos`) are rejected in $O(1)$ without polyline projections.
  • Verify that nearby routes whose bounding box encloses `currentPos` within the 100m margin proceed to corridor matching.
  2. *Sport-Type Filtering Unit Tests (`ForkRouteMatcherTest.kt`)*:
  • Verify that when `activeSportType = BSportType.BIKE`, running routes (`BSportType.RUN`) are filtered out.
  • Verify that generic/untagged routes (`BSportType.UNKNOWN`) and matching sport routes are retained.
  3. *Quiescent Evaluation Throttling Unit Tests (`ForkNavigationRepositoryTest.kt`)*:
  • Verify that successive location updates within $< 3000\text{ms}$ and $< 20\text{m}$ skip candidate search when no alert is active.
  • Verify that location updates $\ge 3000\text{ms}$ or displacement $\ge 20\text{m}$ trigger candidate search.
  • Verify that when an alert is active, location updates trigger evaluation on every call (1 Hz) without throttling.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with 0 regressions.

### TST-MAP-041: **Strict "Take Me Home" Activation Decoupling, Tab Hints Gating, and Non-Displacing Overlay Verification** [ReturnNavigationHud active strictly on explicit Take Me Home activation; suppressed on tabs with navigation hints disabled; floats as non-displacing top overlay without shifting sensor tiles; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-MAP-039`
- **Component**: **Strict "Take Me Home" Activation Decoupling, Tab Hints Gating, and Non-Displacing Overlay Verification**
- **Jira**: ``ATT-2938``
- **Test Description**:
  1. *ReturnNavigationRepository Activation Mode Decoupling Unit Tests (`ReturnNavigationRepositoryTest.kt`)*:
  • Verify selecting a route terminating near home when `isTakeMeHomeMode == false` results in `isActive == false` and `hasRemainingMetrics == false`.
  • Verify calling `startTakeMeHome()` activates return metrics and resolves destination name to home with dynamic ETA.
  • Verify calling `stopTakeMeHome()` resets `isActive == false` and `hasRemainingMetrics == false`.
  2. *Lifecycle State Reset Contract Unit Tests (`ReturnNavigationRepositoryTest.kt`)*:
  • Verify when in `isTakeMeHomeMode == true`, clearing the route or stopping return navigation resets `isTakeMeHomeMode = false`, `isReverseReturn = false`, and emits `ReturnNavigationState(isActive = false)`.
  3. *SensorGridScreen Non-Displacing Overlay & Tab Gating Contract Tests (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify `ReturnNavigationHud` is NOT rendered inside the scrollable in-flow sensor grid `Column`.
  • Verify `ReturnNavigationHud` is hosted in top-level overlay at `Alignment.TopCenter`.
  • Verify `ReturnNavigationHud` is conditionally gated by `state.showNavigationHints`.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-MAP-042: **High-Glanceability Route Polyline Scaling and Solid Line Verification** [Actively navigated routes render with 26f width and solid pattern (null); active non-navigated routes preserve 10f width and dashed pattern; Z-index stacking hierarchy preserved; ViewModel and inspection views propagate active navigation; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-MAP-040`
- **Component**: **High-Glanceability Route Polyline Scaling and Solid Line Verification**
- **Jira**: ``ATT-2940``
- **Test Description**:
  1. *Visual Hierarchy & Polyline Dimension Unit Tests (`MapRouteActiveNavigationTest.kt`)*:
  • When `isActiveNavigation == true`: verify `width == 26f` (`MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH`) and `color == TTColor.RouteActiveNavigation`.
  • When `isSelected == true && !isActiveNavigation`: verify `width == 10f` (`MapVisualization.ROUTE_WIDTH`) and `color == TTColor.RouteSelected`.
  • When unselected: verify `width == 6f` (`MapVisualization.ROUTE_UNSELECTED_WIDTH`) and `color == TTColor.RouteUnselected`.
  2. *Clean Solid Polyline Pattern Nullification & Dash Overlay Preservation Tests (`MapRouteActiveNavigationTest.kt`)*:
  • When `isActiveNavigation == true`: verify `pattern == null`, eliminating dashed overlay clutter and ensuring clean solid polyline rendering.
  • When `isActiveNavigation == false`: verify `pattern != null` and contains `Dash(15f)` and `Gap(15f)`, preserving standard active route styling.
  3. *Z-Index Stacking Hierarchy Invariant Assertion (`MapRouteActiveNavigationTest.kt`)*:
  • Verify mathematically: $ROUTE\_BASE\_Z\_INDEX (20f) < ROUTE\_ACTIVE\_BASE\_Z\_INDEX (24f) < CLIMB\_Z\_INDEX (28f) < SEGMENT\_Z\_INDEX (30f) < USER\_LOCATION\_Z\_INDEX (100f)$.
  4. *MapFragmentWithTrackViewModel Active Route Propagation Tests (`MapFragmentWithTrackViewModelTest.kt`)*:
  • Verify `routesRepository.activeNavigatedRouteId` is combined into `uiState.routes`, mapping matching route with `isActiveNavigation = true` and non-matching routes with `isActiveNavigation = false`.
  5. *Route Detail & Inspection Screen Prominence Contract Tests (`RouteDetailSheetTest.kt`)*:
  • Verify `RouteDetailSheet` and `RoutesScreen` pass `selectedRoute.toMapRoute(isActiveNavigation = true)` to `RouteOnMapScreen`.
  6. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with 0 regressions.

### TST-MAP-043: **Active Selected Route Fork Candidate Filtering & Quiescent Short-Circuit Verification** [Fork decision candidate matching only evaluates routes where summary.isSelected is true; unselected routes ignored; short-circuits in O(1) when fewer than 2 routes selected; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-MAP-041`
- **Component**: **Active Selected Route Fork Candidate Filtering & Quiescent Short-Circuit Verification**
- **Jira**: ``ATT-2942``
- **Test Description**:
  1. *ForkNavigationRepository Selected Route Candidate Filtering & Quiescent Short-Circuit Unit Tests (`ForkNavigationRepositoryTest.kt`)*:
  • Verify that when `selectedRoutes.size < 2` (0 or 1 route selected), `onLocationChanged()` immediately short-circuits and does not trigger a fork decision state even when approaching a fork.
  • Verify that when an active fork alert is present and selected routes drop below 2, `forkDecisionState` is immediately reset to `null`.
  • Verify that when 2 routes are selected and an unselected 3rd route shares the corridor, only the selected routes are evaluated and included in `forkDecisionState.branches`.
  2. *ForkRouteMatcher Selection Parameter Unit Tests (`ForkRouteMatcherTest.kt`)*:
  • Verify `ForkRouteMatcher.findCandidateRoutes(requireSelected = true)` filters out routes where `isSelected == false`.
  • Verify `ForkRouteMatcher.findCandidateRoutes(requireSelected = false)` evaluates routes regardless of `isSelected` flag, preserving backward compatibility.
  3. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-MAP-044: **Speed-Dependent Map Zoom Calibration, Lookahead Padding & Tuning Preferences Verification** [Speed-dependent zoom scales accurately with speed in km/h; speed zoom toggle locks base zoom; bottom content padding positions rider in lower third during Follow-Me tracking; tilt is configurable; all parameters persist in DataStore and reset atomically; 100% 9-language translation parity; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-MAP-042`
- **Component**: **Speed-Dependent Map Zoom Calibration, Lookahead Padding & Tuning Preferences Verification**
- **Jira**: ``ATT-2946``
- **Test Description**:
  1. *Follow-Me Speed Zoom Scaling Unit Tests (`FollowMeCameraZoomTest.kt`)*:
  • Verify stationary speed ($0\text{ m/s}$ / $0\text{ km/h}$) yields base zoom ($20.0\text{f}$).
  • Verify cruising speed ($5.556\text{ m/s}$ / $20\text{ km/h}$) yields cruising zoom ($18.0\text{f}$).
  • Verify high speed ($11.111\text{ m/s}$ / $40\text{ km/h}$) yields linear scaled zoom ($16.0\text{f}$).
  • Verify `speedZoomEnabled == false` locks zoom to base zoom across all speeds.
  • Verify zoom is clamped within $[12.0\text{f}, 21.0\text{f}]$.
  • Verify tilt angle is clamped within $[0.0\text{f}, 70.0\text{f}]$.
  2. *Forward Lookahead Bottom Padding Contract Tests (`FollowMePaddingContractTest.kt`)*:
  • Verify `ATrainingTrackerMap` applies bottom content padding derived from viewport height and `mapFollowMeLookaheadPaddingPercent` during `FOLLOW_ME` mode.
  • Verify `ATrainingTrackerMap` applies `0.dp` bottom content padding in non-Follow-Me modes (`FIT_ALL`, `EXPLICIT_BOUNDS`).
  3. *DataStore & Advanced Tuning Preferences Unit Tests (`MapCameraTuningPreferencesTest.kt`)*:
  • Verify default values on `TuningConfig` for all 5 camera parameters.
  • Verify DataStore read/write persistence, flow emissions, and value range coercion.
  • Verify `resetToDefaults()` atomically restores all 5 parameters.
  4. *9-Language Localization Audit (`MapCameraLocalizationTest.kt`)*:
  • Verify all new camera string tokens exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-NAV-045: **In-Ride Fork Decision Direction Diversity & Alert Suppression Verification** [Fork decision alerts are strictly suppressed when all candidate routes share the same direction; active alerts trigger cleanly when routes diverge into distinct directions; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-NAV-043`
- **Component**: **In-Ride Fork Decision Direction Diversity & Alert Suppression Verification**
- **Jira**: ``ATT-2964``
- **Test Description**:
  1. *Single-Direction Divergence Suppression Tests (`RouteDivergenceDetectorTest.kt`)*:
  • Verify that when two candidate routes separate by > 40m but both classify as `ForkDirection.STRAIGHT`, `detectDivergence()` returns `null`..
  2. *Multi-Direction Divergence Activation Tests (`RouteDivergenceDetectorTest.kt`)*:
  • Verify that when candidate routes branch into at least two distinct directions (`STRAIGHT` and `RIGHT`, or `LEFT` and `RIGHT`), `detectDivergence()` returns a non-null `ForkDecisionState`..
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-MAP-045: **Multi-Scale Turn Cue Detection, Spatial Heading Smoothing & Curvature Classification Verification** [Multi-scale sliding window detection captures sharp and sweeping turns; 5m spatial heading average filters GPS noise with sparse fallback; cues anchor at true curvature apex; 180-degree hairpins consolidated into single U-turn; short routes safely guarded; 100% test pass rate.]
- **Requirement(s)**: `REQ-MAP-028`
- **Component**: **Multi-Scale Turn Cue Detection, Spatial Heading Smoothing & Curvature Classification Verification**
- **Jira**: ``ATT-3494``
- **Test Description**:
  1. *Multi-Scale Sliding Window & Curvature Detection Unit Tests (`TurnCueDetectorTest.kt`)*:
  • Verify sharp 90-degree intersection turn detected via short window ($W_{\text{short}} = 12\text{--}15\text{m}$) with `TurnDirection.RIGHT` / `LEFT` and anchored within 5m of apex.
  • Verify sweeping 90-degree curve over 45m (point-to-point chords $< 20^\circ$) detected via long window ($W_{\text{long}} = 35\text{--}50\text{m}$) with single cue at apex.
  • Verify continuous gentle bends ($< 25^\circ$) emit zero false turn cues.
  • Verify circular arc with constant curvature anchors cue at median midpoint vertex.
  • Verify 180-degree mountain hairpin / switchback emits single `TurnDirection.U_TURN` cue without duplicate cues.
  • Verify short routes ($< 20\text{m}$ or $< 3$ points) return empty list safely without boundary or index exceptions.
  • Verify boundary clamps suppress cues within initial and terminal 10m.
  2. *Spatial Heading-Domain Smoothing Unit Tests (`TurnCueDetectorTest.kt`)*:
  • Verify 5-meter spatial moving average filter suppresses $\pm 3\text{m}$ high-frequency GPS zig-zag noise along straight tracks.
  • Verify sparse polyline fallback ($K < 2$ points in 5m window) safely uses raw chord bearing without division-by-zero.
  • Verify non-destructive smoothing maintains polyline coordinate precision without vertex decimation.
  3. *Performance & Zero Heap Allocation Benchmark Unit Tests (`TurnCueDetectorTest.kt`)*:
  • Verify single-pass $O(N)$ execution on 5,000-point route completes in $< 100\text{ms}$ with zero heap allocation churn.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-MAP-046: **Forward Lookahead Camera Top Padding Inversion & Zoom Level Directional Guidance Verification** [Top content padding applied in follow-me mode to anchor rider in lower third; non-follow-me modes resolve to 0dp; zoom sliders explain direction; forward lookahead title cleaned; 100% 9-language translation parity; 100% test pass rate.]
- **Requirement(s)**: `REQ-MAP-044`
- **Component**: **Forward Lookahead Camera Top Padding Inversion & Zoom Level Directional Guidance Verification**
- **Jira**: ``ATT-3054``
- **Test Description**:
  1. *Follow-Me Top Padding Structural Contract Tests (`FollowMePaddingContractTest.kt`)*:
  • Verify `ATrainingTrackerMap.kt` calculates `val topPadding = if (zoomFocus == MapZoomFocus.FOLLOW_ME) maxHeight * (tuningConfig.mapFollowMeLookaheadPaddingPercent / 100f) else 0.dp`.
  • Verify `GoogleMap` receives `contentPadding = PaddingValues(top = topPadding)`.
  • Verify `bottomPadding` is removed or replaced by `topPadding`.
  2. *Padding Percentage Mathematical Tests (`FollowMePaddingContractTest.kt`)*:
  • Given a $400\text{dp}$ viewport and default $30\%$ lookahead padding, assert calculated top padding is exactly $120\text{dp}$.
  • Given non-follow-me modes (`FIT_ALL`, `EXPLICIT_BOUNDS`), assert calculated top padding is $0\text{dp}$.
  3. *9-Language Localization Audit (`MapCameraLocalizationTest.kt`)*:
  • Verify that all 11 map camera tuning string tokens exist and are non-empty across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  • Verify that `tuning_map_base_zoom_desc` and `tuning_map_cruising_zoom_desc` contain direction guidance substrings.
  • Verify that `tuning_map_lookahead_padding_title` is updated cleanly without "bottom" references.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-MAP-047: **Automatic Active Workout Sport Type Synchronization on Route Selection (Bike / Run) Verification** [RouteSelectorViewModel synchronizes workout sport type to BIKE or RUN upon route selection or candidate activation; UNKNOWN and CONFLICT routes do not alter sport; stopping route does not alter sport; BANALServiceRepository updates state synchronously; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-MAP-045`
- **Component**: **Automatic Active Workout Sport Type Synchronization on Route Selection (Bike / Run) Verification**
- **Jira**: ``ATT-3092``
- **Test Description**:
  1. *Route Selection Sport Type Auto-Synchronization Unit Tests (`RouteSelectorViewModelTest.kt`)*:
  • Verify selecting a route with `summary.bSportType == BSportType.BIKE` invokes `banalServiceRepository.setUserSelectedSportType(BSportType.BIKE)` and activates the route.
  • Verify selecting a route with `summary.bSportType == BSportType.RUN` invokes `banalServiceRepository.setUserSelectedSportType(BSportType.RUN)` and activates the route.
  • Verify selecting a route with `summary.bSportType == BSportType.UNKNOWN` does NOT invoke `banalServiceRepository.setUserSelectedSportType(...)`.
  • Verify selecting a route with `summary.bSportType == BSportType.CONFLICT` does NOT invoke `banalServiceRepository.setUserSelectedSportType(...)`.
  2. *Auto-Detected Candidate Route Sport Synchronization Unit Tests (`RouteSelectorViewModelTest.kt`)*:
  • Verify invoking `activateCandidate(routeId)` with a `BIKE` or `RUN` route sets active route and invokes `banalServiceRepository.setUserSelectedSportType(...)`.
  3. *Decoupled Route Clearing / Stop Preservation Unit Tests (`RouteSelectorViewModelTest.kt`)*:
  • Verify invoking `stopRoute()` or `clearRoute()` clears the active navigated route (`setActiveNavigatedRoute(null)`) but does NOT alter or reset the active workout sport type.
  4. *Immediate State Flow Emission Unit Tests (`BANALServiceRepositoryTest.kt`)*:
  • Verify `banalServiceRepository.setUserSelectedSportType(bSportType)` immediately sets `_bSportType.value = bSportType` synchronously in addition to invoking the service binder.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-MAP-048: **Unified Viewport Anchor Authority & Lookahead Camera Targeting Verification** [Camera target projected ahead of athlete along bearing; zero padding in GoogleMap avoids oscillation; lookahead scales with zoom and latitude; 100% clean-room unit test pass rate.]
- **Requirement(s)**: `REQ-MAP-046`
- **Component**: **Unified Viewport Anchor Authority & Lookahead Camera Targeting Verification**
- **Jira**: ``ATT-3102``
- **Test Description**:
  1. *Follow-Me Camera Target Mathematical Unit Tests (`FollowMeCameraTargetTest.kt`)*:
  • Verify `calculateFollowMeCameraTarget` projects target coordinate ahead of athlete along `bearing`.
  • Verify `lookaheadPaddingPercent == 0f` returns `currentLocation` unchanged.
  • Verify `viewportHeightDp <= 0f` returns `currentLocation` unchanged.
  • Verify ground offset scales inversely with zoom level ($2^Z$) and cosine of latitude.
  • Verify bearing rotation (0°, 90°, 180°, 270°) shifts target in correct cardinal direction.
  2. *Follow-Me Unified Anchor Authority Structural Contract Tests (`FollowMeUnifiedAnchorContractTest.kt`)*:
  • Verify `ATrainingTrackerMap.kt` passes `PaddingValues(0.dp)` as `contentPadding` to `GoogleMap` in `FOLLOW_ME` mode (eliminating competing padding).
  • Verify `ATrainingTrackerMap.kt` passes `lookaheadPaddingPercent` and `viewportHeightDp` to `followMeController`.
  • Verify `followMeController` in `MapBehaviors.kt` invokes `calculateFollowMeCameraTarget` to calculate camera position target.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-MAP-049: **Live Climb Continuous Linear Projection, Telemetry Metric Interpolation & Cross-Track Off-Route Verification** [Orthogonal segment projection continuously interpolates distance, altitude, progress fraction, and gradient on sparse tracks; cross-track distance calculation prevents false off-route triggers; single-point fallback preserved; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-MAP-047`
- **Component**: **Live Climb Continuous Linear Projection, Telemetry Metric Interpolation & Cross-Track Off-Route Verification**
- **Jira**: ``ATT-3174``
- **Test Description**:
  1. *Continuous Linear Projection & Metric Interpolation Unit Tests (`LiveClimbsRepositoryTest.kt`)*:
  • Verify progress calculation on sparse path points (e.g. vertices at 0m and 500m):
  - At 25% distance (125m along segment), assert `completedDist` $\approx 125.0\text{m}$, `progressFraction` $\approx 0.25\text{f}$, and altitude $\approx \text{alt}_0 + 0.25 \times (\text{alt}_1 - \text{alt}_0)$.
  - At 50% distance (250m along segment), assert `completedDist` $\approx 250.0\text{m}$, `progressFraction` $\approx 0.50\text{f}$, and altitude $\approx \text{alt}_0 + 0.50 \times (\text{alt}_1 - \text{alt}_0)$.
  - At 75% distance (375m along segment), assert `completedDist` $\approx 375.0\text{m}$, `progressFraction` $\approx 0.75\text{f}$, and altitude $\approx \text{alt}_0 + 0.75 \times (\text{alt}_1 - \text{alt}_0)$.
  • Verify instantaneous grade percentage reflects the slope of the containing segment ($\frac{\Delta \text{alt}}{\Delta \text{dist}} \times 100$).
  2. *Cross-Track Off-Route Threshold & Segment Tolerance Unit Tests (`LiveClimbsRepositoryTest.kt`)*:
  • Verify that an athlete located at the midpoint of a 180m segment with cross-track offset of 10m (distance to nearest vertex $\approx 90.5\text{m}$) remains ON-ROUTE (`state != null`).
  • Verify that an athlete with cross-track distance $> 100.0\text{m}$ (e.g. 105m perpendicular offset) triggers off-route cancellation (`state == null`).
  3. *Single-Point Geometry Fallback Unit Tests (`LiveClimbsRepositoryTest.kt`)*:
  • Verify that a climb with a single path point executes without `IndexOutOfBoundsException` and resolves metrics against $P_0$.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-MAP-050: **Follow-Me Camera Target Forward Lookahead Projection Scaling, Tilt Foreshortening Compensation & Scale Direction Transparency Verification** [Ground offset scales proportionally across slider; tilt foreshortening compensated up to 70°; controller propagates effective tilt; all 9 locales updated; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-MAP-048`
- **Component**: **Follow-Me Camera Target Forward Lookahead Projection Scaling, Tilt Foreshortening Compensation & Scale Direction Transparency Verification**
- **Jira**: ``ATT-3171``
- **Test Description**:
  1. *Mathematical Scaling Unit Tests (`FollowMeCameraTargetTest.kt`)*:
  • Verify ground offset distance scales strictly monotonically across slider range (5x from 10% to 50%, 3x from 10% to 30%).
  2. *Tilt Foreshortening Compensation Unit Tests (`FollowMeCameraTargetTest.kt`)*:
  • Verify that at `tiltAngle = 70f`, the calculated ground offset is scaled up by $\approx 2.92\times$ relative to `tiltAngle = 0f`.
  • Verify that extreme tilt angles (e.g. 90f, -10f) clamp safely to $[0^\circ, 70^\circ]$ without division by zero.
  3. *FollowMeController Contract Verification (`FollowMeUnifiedAnchorContractTest.kt`)*:
  • Verify `followMeController` passes `tiltAngle = effectiveTilt` to `calculateFollowMeCameraTarget`.
  • Verify `calculateFollowMeCameraTarget` signature includes default `tiltAngle: Float = 0f`.
  4. *Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 supported locales for `tuning_map_lookahead_padding_desc` including explanatory scale direction.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-GEO-001: **Unified Polyline Progress Tracking Engine with Directional Virtual Gates and Orthogonal Segment Projection Verification** [\Delta\theta]
- **Requirement(s)**: `REQ-GEO-001`
- **Component**: **Unified Polyline Progress Tracking Engine with Directional Virtual Gates and Orthogonal Segment Projection Verification**
- **Jira**: ``ATT-3266``
- **Test Description**:
  1. *Virtual Gate Construction & Directional Crossing Unit Tests (`PolylineProgressTrackerTest.kt`)*:
  • Verify perpendicular gate calculation from start/end track vectors.
  • Verify forward gate crossing detection via 2D vector cross-product sign flip.
  • Verify rejection of reverse crossings and parallel switchback passes without cross-product sign change.
  2. *Continuous Orthogonal Segment Projection Unit Tests (`PolylineProgressTrackerTest.kt`)*:
  • Verify orthogonal projection parameter $t \in [0, 1]$ and along-track completed distance.
  • Verify geodesic cross-track error calculation and resilience to lateral GPS weaving.
  • Verify 3D linear altitude and instantaneous grade percentage interpolation.
  3. *Smooth Finish Convergence & Approach Proximity Unit Tests (`PolylineProgressTrackerTest.kt`)*:
  • Verify smooth $\lambda$-blended remaining distance convergence to exactly $0.0\text{m}$ at finish line.
  • Verify approach filtering ($D \le 250\text{m},

### TST-MAP-051: **Follow-Me Speed Zoom Smoothing, Zoom Hysteresis Deadband, and Configurable Filter Calibration Verification** [Speed zoom smoothing eliminates camera pumping; zoom hysteresis suppresses micro-animations; configurable filter sliders integrated in Advanced Settings; 100% localization parity; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-MAP-049`
- **Component**: **Follow-Me Speed Zoom Smoothing, Zoom Hysteresis Deadband, and Configurable Filter Calibration Verification**
- **Jira**: ``ATT-3227``
- **Test Description**:
  1. *Mathematical Low-Pass Smoothing & Hysteresis Unit Tests (`FollowMeSmoothingTest.kt`)*:
  • Verify speed low-pass exponential smoothing ($\alpha = 0.10f$) smooths rapid speed spikes.
  • Verify bearing low-pass exponential smoothing with configurable alpha.
  • Verify zoom hysteresis deadband ($\Delta \text{zoom} < 0.10f$) suppresses minor zoom micro-animations.
  • Verify large speed transitions ($> 0.10f$ zoom delta) update `activeZoom` correctly.
  2. *DataStore Preference Serialization & Default Reset Unit Tests (`TuningConfigTest.kt`)*:
  • Verify `TuningConfig` initializes with `mapFollowMeSpeedSmoothingAlpha = 0.10f` and `mapFollowMeBearingSmoothingAlpha = 0.15f`.
  • Verify persistence and range clamping ($[0.02f, 0.50f]$ for speed, $[0.05f, 0.50f]$ for bearing).
  • Verify `resetToDefaults()` restores default alphas.
  3. *Advanced Settings UI Integration Contract Tests (`NavigationSectionTest.kt`)*:
  • Verify Speed Smoothing slider visibility is gated on `mapFollowMeSpeedZoomEnabled`.
  • Verify Bearing Smoothing slider renders with 9 steps ($0.05f$ to $0.50f$).
  • Verify callbacks fire on value change.
  4. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify all 4 new string tokens exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-MAP-052: **Follow-Me Piecewise Logarithmic Zoom Calibration & UI Simplification Verification Suite** [Piecewise linear-to-logarithmic Follow-Me camera zoom calibration, minimum zoom clamping at 16.0f, autonomous controller invocation, and removal of camera sliders from Advanced Settings verified across unit and contract tests; 100% test pass rate.]
- **Requirement(s)**: `REQ-MAP-050`
- **Component**: **Follow-Me Piecewise Logarithmic Zoom Calibration & UI Simplification Verification Suite**
- **Jira**: ``ATT-3581``
- **Test Description**:
  1. *Piecewise Logarithmic Speed Zoom Unit Tests (`FollowMeCameraZoomTest.kt`)*:
  • Verify low-speed linear interpolation ($0 \le \text{speed} \le 20\text{ km/h}$): $0\text{ km/h} \to 20.0f$, $10\text{ km/h} \to 19.0f$, $20\text{ km/h} \to 18.0f$.
  • Verify high-speed logarithmic scaling ($> 20\text{ km/h}$): $30\text{ km/h} \to 17.42f$, $40\text{ km/h} \to 17.00f$, $50\text{ km/h} \to 16.68f$, $60\text{ km/h} \to 16.42f$, $80\text{ km/h} \to 16.00f$, $90\text{ km/h} \to 16.00f$ (clamped).
  • Verify `speedZoomEnabled == false` locks to `baseZoom` ($20.0f$).
  • Verify `minZoom` defaults to `16.0f` (`DEFAULT_MAP_FOLLOW_ME_MIN_ZOOM`).
  2. *Navigation Tuning UI Contract Tests (`AdvancedTuningVisualContractTest.kt`)*:
  • Verify `NavigationSection.kt` omits `tuning_map_camera_section_title` and all 7 Follow-Me camera slider items.
  • Verify remaining Navigation settings (Route Selection Radius, Cue Auto-Dismiss Duration, Turn Cue Countdown Distance, Auto-Follow Unique Route) are active.
  3. *Autonomous Camera Controller Contract Tests (`FollowMePaddingContractTest.kt`)*:
  • Verify `ATrainingTrackerMap.kt` invokes `followMeController(...)` using canonical defaults from `TuningPreferencesDefaults`.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate across all 2,964+ unit tests.

### TST-MAP-053: **Map Polyline Memory Stability, Referential Identity & Preview Downsampling Verification Suite** [Referentially stable `latLngs` and `PatternItem` lists, decoupled static map layer flows, keyed `MappablePathLayer` composition, and bounded 500-point preview downsampling in `CropWorkoutScreen`; 100% test pass rate.]
- **Requirement(s)**: `REQ-MAP-051`
- **Component**: **Map Polyline Memory Stability, Referential Identity & Preview Downsampling Verification Suite**
- **Jira**: ``ATT-3690``
- **Test Description**:
  1. *Referential Identity & Constant Pattern Tests (`MapPolylineMemoryStabilityTest.kt`)*:
  • Verify that `MapTrack(type = TrackType.BEST).pattern`, `MapSegment(isDashed = true).pattern`, and `MapRoute(isActiveNavigation = false).pattern` return referentially identical (`===`) singleton `List<PatternItem>` instances across repeated property reads.
  • Verify that `RouteWithPath.toMapRoute(false).latLngs === RouteWithPath.toMapRoute(true).latLngs` and `SegmentWithPath.latLngs` share the exact same `List<LatLng>` reference (`===`).
  2. *CropWorkoutScreen Preview Downsampling Math Tests (`MapPolylineMemoryStabilityTest.kt`)*:
  • Verify that `CropWorkoutPreviewMath.downsamplePathPoints(points, maxPoints = 500)` downsamples a 10,000-point path to at most 500 points while preserving the exact first and last `PathPoint` instances.
  • Verify that paths with `<= 500` points are returned without loss.
  3. *Architectural Contract Audit (`MapPolylineMemoryStabilityTest.kt`)*:
  • Verify that `MapContentScope.kt` wraps `MappablePathLayer` loop invocations in `key(data.path.id)`.
  • Verify that `ATrainingTrackerMap.kt` does not include `content` in `remember(...)` keys of `MapContentScopeImpl`.
  • Verify that `TrackingViewModel.kt` decouples `staticMapSegmentsFlow` and `mapRoutesFlow` from 1-Hz `currentTrack` emissions.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions across all unit tests.

### TST-MAP-054: **Reactive Speed/Bearing Flow Subscription in TrackingViewModel & Continuous Follow-Me Smoothing Convergence Verification Suite** [Reactive `currentSpeed` and `currentBearing` updates in `TrackingViewModel.observeMapState` (Rule 39) and continuous Follow-Me EMA zoom/bearing convergence in `MapBehaviors.kt` verified across unit tests and full clean-room suite.]
- **Requirement(s)**: `REQ-MAP-052`
- **Component**: **Reactive Speed/Bearing Flow Subscription in TrackingViewModel & Continuous Follow-Me Smoothing Convergence Verification Suite**
- **Jira**: ``ATT-3775``
- **Test Description**:
  1. *Reactive `currentSpeed` and `currentBearing` Subscription Unit Tests (`TrackingViewModelMapStateTest.kt`)*:
  • Verify that emitting a new speed on `banalServiceRepository.currentSpeed` immediately updates `viewModel.mapState.value.userSpeed` and `viewModel.uiState.value.userSpeed` when `currentTrack` is empty (`READY` mode) and when `currentTrack` is non-empty (`TRACKING` mode) without any `currentTrack` emission.
  • Verify that emitting a new bearing on `banalServiceRepository.currentBearing` immediately updates `viewModel.mapState.value.userBearing` and `viewModel.uiState.value.userBearing` without any `currentTrack` emission.
  • Verify that `null` emissions on `currentSpeed` and `currentBearing` safely reset `userSpeed` and `userBearing` to `0.0f`.
  • Verify that `staticLayers` (`mapSegments`, `mapRoutes`) preserve referential identity (`===`) across `currentSpeed` and `currentBearing` emissions (`REQ-MAP-051`).
  2. *Follow-Me EMA Convergence & Source Contract Tests (`TrackingViewModelMapStateTest.kt`)*:
  • Verify `stepFollowMeSmoothing` converges `filteredSpeed`, `filteredBearing` (including $350^\circ \to 10^\circ$ wrap-around), and `effectiveZoom` to `calculateFollowMeTargetZoom(targetSpeed)` across steady-state iterations, including low-speed (`2.5 m/s` = `9 km/h`) and high-speed (`11.11 m/s` = `40 km/h`) targets.
  • Verify `TrackingViewModel.kt` combines `banalServiceRepository.currentSpeed` and `banalServiceRepository.currentBearing` in `liveTelemetryFlow` and never reads `currentSpeed.value` or `currentBearing.value` inside `observeMapState()`.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-MAP-055: **Multi-File Route Selection, Batch Import & External Multi-Share Verification Suite** [Multi-file route picking, batch progress dialog, fault-tolerant batch persistence, external multi-share intent handling, 9-language translation parity, and full test suite pass verified.]
- **Requirement(s)**: `REQ-MAP-053`
- **Component**: **Multi-File Route Selection, Batch Import & External Multi-Share Verification Suite**
- **Jira**: ``ATT-4049``
- **Test Description**:
  1. *All Valid Files Batch Import Test (`TST-MAP-055.1`, `RoutesViewModelTest.kt`)*:
  • Provide 3 valid route URIs.
  • Call `viewModel.importRoutesBatch(uris)`.
  • Verify progress emissions, repository insert calls (3), null progress upon completion, and `BatchImportResult(3, 0, 3)`.
  2. *Partial Failure Batch Import Test (`TST-MAP-055.2`, `RoutesViewModelTest.kt`)*:
  • Provide 2 valid URIs and 1 corrupted URI.
  • Call `viewModel.importRoutesBatch(uris)`.
  • Verify valid routes are inserted (2), corrupt file is caught, and `BatchImportResult(2, 1, 3)` is emitted.
  3. *All Files Failed Test (`TST-MAP-055.3`, `RoutesViewModelTest.kt`)*:
  • Provide 3 failing URIs.
  • Call `viewModel.importRoutesBatch(uris)`.
  • Verify zero repository inserts and `BatchImportResult(0, 3, 3)`.
  4. *Empty List Guard Test (`TST-MAP-055.4`, `RoutesViewModelTest.kt`)*:
  • Call `viewModel.importRoutesBatch(emptyList())`.
  • Verify no-op and zero state changes.
  5. *Result Reset Test (`TST-MAP-055.5`, `RoutesViewModelTest.kt`)*:
  • Call `viewModel.clearBatchImportResult()`.
  • Verify `batchImportResult` resets to null.
  6. *GpxImportViewModel Batch Handling Test (`TST-MAP-055.6`, `GpxImportViewModelTest.kt`)*:
  • Provide multi-URI list to `viewModel.handleBatchUris(uris)`.
  • Verify batch progress and success states.
  7. *9-Language Localization Audit (`TST-MAP-055.7`, `TranslationParityTest.kt`)*:
  • Verify `routes_import_progress`, `routes_import_success`, `routes_import_partial`, and `routes_import_all_failed` across all 9 locales.
  8. *Clean-Room Regression Suite Execution (`TST-MAP-055.8`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-MAP-056: **Batch GPX Route Import Sport Type Selection, Responsive Bottom Sheet Layout & Sequential Review Verification Suite** [Batch GPX route import sport type selection prompt, AppModalBottomSheet layout container, FlowRow intact chip wrapping, multi-line centered segmented button labels, neutral BSportType.UNKNOWN preselection, canonical chip ordering with Other on far right (Rule 57), apply-to-all sport override in RoutesViewModel and GpxImportViewModel, sequential individual review state machine, RoutesScreen flow discrimination, 9-language localization parity, and clean-room test pass specified.]
- **Requirement(s)**: `REQ-MAP-053`, `REQ-UI-106`
- **Component**: **Batch GPX Route Import Sport Type Selection, Responsive Bottom Sheet Layout & Sequential Review Verification Suite**
- **Jira**: ``ATT-4372 / ATT-4430``
- **Test Description**:
  1. *RoutesViewModel Batch Import with Sport Type Override Test (`TST-MAP-056.1`, `RoutesViewModelTest.kt`)*:
  • *Given* 3 valid route URIs,
  • *When* calling `viewModel.importRoutesBatch(uris, sportType = BSportType.BIKE)`,
  • *Then* each route is inserted into `routesRepository` with `summary.bSportType == BSportType.BIKE`.
  • *When* calling `viewModel.importRoutesBatch(uris, sportType = BSportType.RUN)`,
  • *Then* `summary.bSportType == BSportType.RUN`.
  • *When* calling `viewModel.importRoutesBatch(uris, sportType = BSportType.UNKNOWN)` (and default argument),
  • *Then* `summary.bSportType == BSportType.UNKNOWN`.
  2. *GpxImportViewModel Batch Handling with Sport Type Override Test (`TST-MAP-056.2`, `GpxImportViewModelTest.kt`)*:
  • *Given* a batch of route URIs,
  • *When* calling `viewModel.handleBatchUris(uris, sportType = BSportType.BIKE)`,
  • *Then* routes are inserted into `routesRepository` with `bSportType == BSportType.BIKE`.
  • *When* calling `viewModel.handleBatchUris(uris, sportType = BSportType.UNKNOWN)`,
  • *Then* routes are inserted with `bSportType == BSportType.UNKNOWN`.
  3. *GpxImportViewModel Sequential Individual Import Flow Test (`TST-MAP-056.3`, `GpxImportViewModelTest.kt`)*:
  • *Given* 3 route URIs,
  • *When* invoking `viewModel.startIndividualImport(uris)`,
  • *Then* initial state is `ImportState.EditingIndividual(..., current = 1, total = 3)`.
  • *When* saving route 1, route 1 is inserted and state advances to `current = 2, total = 3`.
  • *When* skipping route 2, route 2 is not inserted and state advances to `current = 3, total = 3`.
  • *When* saving route 3, route 3 is inserted and state transitions to `ImportState.BatchSuccess(importedCount = 2, failedCount = 1)`.
  4. *BatchImportSportTypeDialog Component Layout, AppModalBottomSheet, FlowRow, Neutral Preselection & Canonical Order Contract Test (`TST-MAP-056.4`, `BatchImportSportTypeDialogTest.kt`)*:
  • *Given* `BatchImportSportTypeDialog.kt` source and runtime composable,
  • *When* inspected,
  • *Then* verify container uses `AppModalBottomSheet` (eliminating `AlertDialog` width constriction).
  • Verify title uses `batch_import_dialog_title_format` with formatted route count `%1$d`.
  • Verify `SingleChoiceSegmentedButtonRow` displays "Apply to all" and "Configure individually" options.
  • Verify segmented buttons use multi-line centered wrapping (`maxLines = 2`, `textAlign = TextAlign.Center`, `overflow = TextOverflow.Ellipsis`) and do NOT enforce rigid `maxLines = 1` or `softWrap = false`.
  • Verify sport selection chips in "Apply to all" mode reside in `FlowRow` with 8dp horizontal and vertical spacing (Rule 54) rather than a rigid `Row`.
  • Verify initial `selectedSport` state defaults to `BSportType.UNKNOWN` ("Other" / "Andere").
  • Verify chips are ordered as Bike, Run, Other: `listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN)` matching `EditRouteScreen.kt` and Rule 57.
  • Verify selecting "Configure individually" displays explanatory hint text (`batch_import_individual_hint`) and "Continue" button.
  • Verify clicking Cancel or close button dispatches `onDismiss`.
  • Verify clicking Confirm passes selected mode and sport type to `onConfirm`.
  5. *RoutesScreen Multi-File Flow Discrimination Contract Test (`TST-MAP-056.5`, `RoutesScreenContractTest.kt`)*:
  • *Given* file picker selection in `RoutesScreen`,
  • *When* `uris.size == 1`, verify `GpxImportActivity` launches directly without bottom sheet.
  • *When* `uris.size > 1`, verify `pendingBatchUris` is set and `BatchImportSportTypeDialog` is displayed.
  • *When* confirming "Apply to all", verify invocation of `viewModel.importRoutesBatch(uris, sportType)`.
  • *When* confirming "Configure individually", verify launch of `GpxImportActivity` with `EXTRA_INDIVIDUAL_MODE = true`.
  • *When* dismissing sheet, verify `pendingBatchUris` is cleared without importing.
  6. *9-Language Localization & Format Specifier Parity Test (`TST-MAP-056.6`, `TranslationParityTest.kt`)*:
  • *Given* all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`),
  • *When* auditing string resources,
  • *Then* verify string tokens `batch_import_dialog_title_format`, `batch_import_mode_all`, `batch_import_mode_individual`, `batch_import_individual_hint`, `batch_import_action_import`, `batch_import_action_continue`, `batch_import_individual_progress_format`, and `batch_import_skip_route` exist across all 9 locales with matching indexed positional format specifiers (`%1$d`, `%2$d`) and zero missing keys.
  7. *Clean-Room Regression Suite Execution (`TST-MAP-056.7`)*:
  • *When* executing `./gradlew testDebugUnitTest`,
  • *Then* assert 100% pass rate with zero regressions across the complete unit test suite.

### TST-MAP-057: **TurnCueDetector Dead Code Cleanup, Exact Distance Window Boundary Interpolation & Sparse-Vertex Route Geometry Verification Suite** [Dead code eliminated; lookaround chords interpolated at exact $d_{\text{cur}} \pm W$ boundaries; sparse-vertex corners accurately detected; primitive coordinate bearing verified; 100% test pass rate with zero regressions.]
- **Requirement(s)**: `REQ-MAP-054`, `REQ-MAP-028`
- **Component**: **TurnCueDetector Dead Code Cleanup, Exact Distance Window Boundary Interpolation & Sparse-Vertex Route Geometry Verification Suite**
- **Jira**: ``ATT-4460``
- **Test Description**:
  1. *Sparse-Vertex 90-Degree Corner Detection Unit Test (`TST-MAP-057.1`, `TurnCueDetectorTest.kt`)*:
  • Provide polyline with a 5m corner segment immediately followed by a 100m straight segment.
  • Call `TurnCueDetector.detectGeometricCues(path, config)`.
  • Verify detected turn cue at the corner apex with `TurnDirection.RIGHT` / `LEFT` and deflection angle $\approx 90^\circ$, verifying that continuous boundary interpolation at $d_{\text{cur}} + 10\text{ m}$ prevents chord dilution.
  2. *Sparse Inbound Segment Boundary Interpolation Unit Test (`TST-MAP-057.2`, `TurnCueDetectorTest.kt`)*:
  • Provide polyline with an 80m approach segment followed by a 5m corner segment and 50m exit.
  • Call `TurnCueDetector.detectGeometricCues(path, config)`.
  • Verify inbound chord is interpolated at $d_{\text{cur}} - 10\text{ m}$ along the approach segment, cleanly detecting the turn without under- or over-windowing.
  3. *Dead Code Elimination & Buffer Retirement Contract Test (`TST-MAP-057.3`, `TurnCueDetectorTest.kt`)*:
  • Verify `TurnCueDetector.kt` contains zero references to `smoothedBearings`, `calcWindowBearing`, or `inBearings`.
  • Verify `TurnCueDetectorConfig.smoothingRadiusMeters` remains defined with default value `2.5` to maintain binary and source compatibility.
  4. *Zero-Allocation Primitive Coordinate Bearing Overload Unit Test (`TST-MAP-057.4`, `RouteAutoDetectorTest.kt`)*:
  • Verify `RouteAutoDetector.computeBearing(lat1, lon1, lat2, lon2)` produces results identical to `computeBearing(LatLng(lat1, lon1), LatLng(lat2, lon2))` within $0.001^\circ$ precision across all cardinal and diagonal directions.
  5. *5,000-Point Performance & Zero Heap Allocation Benchmark Test (`TST-MAP-057.5`, `TurnCueDetectorTest.kt`)*:
  • Verify `detectGeometricCues` on a 5,000-point route completes in $< 100\text{ ms}$ with zero internal loop allocation churn.
  6. *Full Existing Test Suite Regression Execution (`TST-MAP-057.6`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions across all 18 existing test cases in `TurnCueDetectorTest` (`TST-MAP-045.1` - `TST-MAP-045.18`) and the entire project test suite.

### TST-GEO-002: **Equirectangular Scaled Polyline Projection, Orthogonal Virtual Gates & Windowed Out-and-Back Route Progress Tracking Verification Suite** [Equirectangular scaling eliminates projection distortion and virtual gate skew at mid-latitudes; windowed forward search prevents out-and-back leg jumping with robust corridor fallback; 100% backward compatibility and test pass rate.]
- **Requirement(s)**: `REQ-GEO-002`
- **Component**: **Equirectangular Scaled Polyline Projection, Orthogonal Virtual Gates & Windowed Out-and-Back Route Progress Tracking Verification Suite**
- **Jira**: ``ATT-4015``
- **Test Description**:
  1. *Equirectangular Projection & Orthogonality Unit Tests (`TST-GEO-002.1`, `PolylineProgressTrackerTest.kt`)*:
  • Verify diagonal segment projection at $50^\circ\text{ N}$ yields correct parameter $t \approx 0.50$ under $50\text{m}$ lateral offset, eliminating unscaled degree distortion.
  • Verify perpendicular virtual gate endpoints in `createGate` form exact $90.0^\circ$ physical angle with track heading at non-equatorial latitudes.
  2. *Virtual Gate Crossing & Cross-Product Parity Unit Tests (`TST-GEO-002.2`, `PolylineProgressTrackerTest.kt`)*:
  • Verify forward gate crossing detection triggers on perpendicular crossing with $\cos(\text{lat})$ scaled cross-products.
  • Verify reverse crossings and parallel passes do not trigger false gate crossings.
  3. *Windowed Forward Segment Continuity Unit Tests (`TST-GEO-002.3`, `PolylineProgressTrackerTest.kt`)*:
  • On an out-and-back course with overlapping outbound (km 1) and return (km 19) legs, verify `projectLocation` with `lastSegmentIndex` on outbound leg maintains outbound segment under lateral jitter toward return leg.
  • Verify forward progress advances smoothly without jumping backward or skipping forward beyond the lookahead window.
  4. *Corridor Exceedance & Off-Route Fallback Unit Tests (`TST-GEO-002.4`, `PolylineProgressTrackerTest.kt`)*:
  • Verify that when lateral distance exceeds `maxCorridorMeters` ($50\text{m}$), `projectLocation` falls back to global search.
  • Verify route rejoin after off-route excursion locks onto correct segment.
  5. *Stateless Caller Backward Compatibility Contract Tests (`TST-GEO-002.5`, `PolylineProgressTrackerTest.kt`)*:
  • Verify calling `projectLocation(path, location)` with default `lastSegmentIndex = null` executes global search identically to legacy behavior.
  • Verify `TrackingElevationProfileResolver`, `LiveSegmentsRepository`, and `LiveClimbsRepository` continue functioning without regressions.
  6. *Localization Parity Audit (`TST-GEO-002.6`, `TranslationParityTest.kt`)*:
  • Verify 100% translation parity across all 9 supported locales (0 new UI strings introduced).
  7. *Clean-Room Regression Suite Execution (`TST-GEO-002.7`)*:
  • Run targeted unit tests and full clean-room suite verifying 100% test pass rate.

### TST-NAV-056: **Stateful Polyline Progress Tracking Continuity & Production Caller Propagation Verification Suite** [Stateful lastSegmentIndex propagation verified across TrackingElevationProfileResolver, LiveSegmentsRepository, and LiveClimbsRepository; deterministic lifecycle reset rules enforced; out-and-back jumping eliminated in production; 100% clean-room test pass rate.]
- **Requirement(s)**: `REQ-NAV-056`, `REQ-GEO-002`
- **Component**: **Stateful Polyline Progress Tracking Continuity & Production Caller Propagation Verification Suite**
- **Jira**: ``ATT-4015``
- **Test Description**:
  1. *TrackingElevationProfileResolver Stateful Continuity Unit Tests (`TST-NAV-056.1`, `TrackingElevationProfileResolverTest.kt`)*:
  • Verify that passing `lastSegmentIndex` to `resolve()` propagates to `PolylineProgressTracker.projectLocation` and returns updated `segmentIndex`.
  • On an out-and-back route, verify continuous calls with GPS fixes on the outbound leg maintain outbound progress without snapping to the return leg.
  • Verify route change or off-corridor deviation resets `lastSegmentIndex` to `null`.
  2. *LiveSegmentsRepository Stateful Continuity Unit Tests (`TST-NAV-056.2`, `LiveSegmentsRepositoryTest.kt`)*:
  • Verify that `LiveSegmentMath.lastSegmentIndex` is initialized on segment start, passed to `projectLocation` during `ON_SEGMENT`, and updated on each fix.
  • Verify that transitioning to `FAR_FAR_AWAY`, `reconcileSegments`, or completion resets `lastSegmentIndex` to `null`.
  • Verify out-and-back segment tracking does not jump to the return leg under lateral GPS jitter.
  3. *LiveClimbsRepository Stateful Continuity Unit Tests (`TST-NAV-056.3`, `LiveClimbsRepositoryTest.kt`)*:
  • Verify `LiveClimbData.lastSegmentIndex` is persisted and passed to `projectLocation` in `updateClimbLiveProgress`.
  • Verify climb finish, off-climb deviation (>50m), or route change resets `lastSegmentIndex` to `null`.
  • Verify out-and-back climb progress advances smoothly.
  4. *Stateless Caller Backward Compatibility Tests (`TST-NAV-056.4`, `PolylineProgressTrackerTest.kt`)*:
  • Verify callers passing default `lastSegmentIndex = null` execute global search without regression.
  5. *9-Language Localization Audit (`TST-NAV-056.5`, `TranslationParityTest.kt`)*:
  • Verify 100% translation parity across all 9 locales (0 new UI strings introduced).
  6. *Clean-Room Full Suite Regression Execution (`TST-NAV-056.6`)*:
  • Run targeted caller unit tests and `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-MAP-055: **Climb Detection Performance, Route/TCX Import Fidelity & Power Calibration Verification Suite** [Climb detector executes in near-linear time and detects steep sections without summit dilution; GPX elevation gain ignores sub-2m noise and DEM enrichment continues on batch failures; TCX parses decimal watts/cadence and retains indoor telemetry; NP rolling window adapts to sample interval; Zone 1 includes 0W coasting when positive power exists while returning null for non-power workouts; 100% unit tests pass.]
- **Requirement(s)**: `REQ-MAP-055`, `REQ-MAP-027`, `REQ-MAP-025`, `REQ-EXP-001`, `REQ-UI-370`
- **Component**: **Climb Detection Performance, Route/TCX Import Fidelity & Power Calibration Verification Suite**
- **Jira**: ``ATT-4033``
- **Test Description**:
  1. *Climb Detector O(N) Advancement & Steep Embedded Climb Unit Test (`TST-MAP-055.1`, `ClimbDetectorTest.kt`)*:
  • Verify candidate search advances scanning index past non-viable bumps via `maxOf(i + 1, peakIdx)` in $O(N)$ time on a 10,000-point route in $\le 100\text{ ms}$.
  • Verify detection of steep climb sections embedded in long gentle ascents before summit dilution.
  • Verify climb scoring ($\text{Score} = \text{length} \times \text{gradient}$) and category thresholds (`HC`, `CAT_1`..`CAT_4`) match `REQ-MAP-027` exactly.
  2. *GPX Elevation Gain Hysteresis & Resilient DEM Batch Enrichment Unit Test (`TST-MAP-055.2`, `GpxRouteImporterTest.kt`)*:
  • Verify `calculateElevationGain` filters sub-2.0m elevation noise fluctuations and only accumulates deltas $\ge 2.0\text{ m}$.
  • Verify DEM batch enrichment logs warning and continues subsequent batches on transient batch failure rather than breaking the loop.
  3. *TCX Decimal Numeric Rounding & Position-less Trackpoint Parsing Unit Test (`TST-MAP-055.3`, `TcxCourseParserTest.kt`, `TcxWorkoutParserTest.kt`)*:
  • Verify `<Watts>` and `<Cadence>` with decimal strings round to nearest integer via `toDoubleOrNull()?.roundToInt()`.
  • Verify `TcxWorkoutParser` retains indoor trackpoints lacking `<Position>` (nullable `lat`/`lng`), preserving heart rate, power, and cadence.
  • Verify `TcxCourseParser` filters out position-less trackpoints for route polyline projection.
  4. *Interval-Aware Normalized Power & Work-kJ Cleanup Unit Test (`TST-MAP-055.4`, `PowerPhysiologyCalculatorTest.kt`)*:
  • Verify `calculateNormalizedPower` scales rolling window dynamically: `maxOf(1, (30.0 / sampleIntervalSec).roundToInt())`.
  • Verify default `sampleIntervalSec = 1.0` preserves exact bit-parity with existing NP/TSS test suites.
  • Verify redundant work-kJ dead code branch is cleanly excised.
  5. *Power Zone 1 Coasting Inclusion & Zero-Power Null Guard Unit Test (`TST-MAP-055.5`, `PowerZoneDistributionCalculatorTest.kt`, `WorkoutRepositoryTest.kt`)*:
  • Verify 0 W coasting samples are categorized into Power Zone 1 (Active Recovery) when `samples.any { it.value > 0 }`.
  • Verify `null` is returned when all power samples are 0 W or absent, preserving parity with `PowerZoneDistributionCalculatorTest`.
  6. *Clean-Room Full Suite Regression Execution (`TST-MAP-055.6`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across the full project test suite.
