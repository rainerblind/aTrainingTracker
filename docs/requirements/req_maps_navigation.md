# Living Requirements: Maps, Polylines & Navigation

**Domain**: `maps_navigation`  
**Prefixes**: `GEO`, `MAP`, `NAV`, `ROU`  
**Scope**: Map rendering, polylines, navigation overlays, GPX track follow-me, and routing.  
**Total Requirements**: 58

---

### REQ-MAP-001: Interactive workout tracks with "Roughness" [Verified]
- **Tests**: `TST-REG-001`
- **Implementation**: `ATrainingTrackerMap.kt`
- **Rationale**: Smooth map interaction for long activities.
- **Description**: Interactive workout tracks with "Roughness".

### REQ-MAP-002: Training heatmaps (Cyan -> Indigo gradient) [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `ATrainingTrackerMap.kt`
- **Rationale**: Visualize training density professionally.
- **Description**: Training heatmaps (Cyan -> Indigo gradient).

### REQ-MAP-003: Low-pass filtered map camera bearing [Verified]
- **Tests**: `TST-REG-001`
- **Implementation**: `ATrainingTrackerMap.kt`
- **Rationale**: Reduce rotation jitter from noisy GPS.
- **Description**: Low-pass filtered map camera bearing.

### REQ-MAP-004: Zoom depending on speed [Verified]
- **Tests**: `TST-REG-001`
- **Implementation**: `ATrainingTrackerMap.kt`
- **Rationale**: Wider context at speed, detail when slow.
- **Description**: Zoom depending on speed.

### REQ-MAP-005: Strava Segments and Routes as map overlays [Verified]
- **Tests**: `TST-REG-002`
- **Implementation**: `MapFragmentWithTrack.kt`
- **Rationale**: Integrate community data directly on map.
- **Description**: Strava Segments and Routes as map overlays.

### REQ-MAP-006: Declarative Map DSL with modular layers [Verified]
- **Tests**: `TST-STR-005`
- **Implementation**: `ATrainingTrackerMap.kt`, `MapLayers.kt`
- **Rationale**: Standardize map rendering and behavior across screens.
- **Description**: Declarative Map DSL with modular layers.

### REQ-MAP-007: Lazy coordinate projection for path rendering [Verified]
- **Tests**: `TST-UNT-009`
- **Implementation**: `MapModels.kt`
- **Rationale**: Optimize performance for long tracks and multiple layers.
- **Description**: Lazy coordinate projection for path rendering.

### REQ-MAP-008: Global Path Context [In Progress]
- **Tests**: `TST-REG-005`
- **Implementation**: `MapDetailLayout.kt`, `*OnMapScreen.kt`
- **Rationale**: In any detail map view, show all loaded Segments and Routes in the background. Use tiered alpha levels: same sport type = muted (0.3), different sport type = highly muted (0.1).
- **Description**: Global Path Context.

### REQ-MAP-009: Reactive Map Layer Redraw [Verified]
- **Tests**: `TST-UI-045`
- **Implementation**: `ATrainingTrackerMap.kt`, `MapContentScope.kt`
- **Rationale**: The map SHALL immediately redraw and re-style polylines (Route, Segment, Track) when their underlying selection or visibility state changes in the UI.
- **Description**: Reactive Map Layer Redraw.

### REQ-MAP-010: Toggleable Grade Legend [Verified]
- **Tests**: `TST-UI-051`
- **Implementation**: `ElevationProfile.kt`
- **Rationale**: The Elevation Profile SHALL provide a toggleable legend that explains the grade color mapping (e.g., < 2%, 2-5%, etc.) via a dedicated info icon.
- **Description**: Toggleable Grade Legend.

### REQ-MAP-055: Climb Detection Performance, Route/TCX Import Fidelity & Power Calibration [`TST-MAP-055`]
- **Tests**: `ClimbDetector.kt`, `GpxRouteImporter.kt`, `TcxTrackpointParser.kt`, `TrackpointModels.kt`, `PowerPhysiologyCalculator.kt`, `WorkoutRepository.kt`, `ZoneDistributionCalculator.kt`
- **Implementation**: Optimize climb detection performance, apply GPX elevation gain hysteresis and batch resilience, support decimal tags and indoor trackpoints in TCX ingestion, provide interval-aware Normalized Power, and accurately record coasting in Power Zone 1.
- **Rationale**: The system SHALL optimize climb detection performance, apply GPX elevation gain hysteresis and batch resilience, support decimal tags and indoor trackpoints in TCX ingestion, provide interval-aware Normalized Power, and accurately record coasting in Power Zone 1 (ATT-4033, Parent Epic ATT-2565, Status: Verified, refining `REQ-MAP-027`, `REQ-MAP-025`, `REQ-EXP-001`, and `REQ-UI-370`):
1. *Climb Detection Performance & Steep Embedded Ascent Scanning (`ClimbDetector.kt`, `REQ-MAP-055.1`)*:
• In `ClimbDetector.detectClimbs`, candidate search SHALL advance the scanning index past non-viable bumps via `maxOf(i + 1, peakIdx)`, achieving near-linear $O(N)$ execution on large routes ($N \ge 10,000$ points) and eliminating $O(N^2)$ candidate rescanning loops.
• The detector SHALL evaluate local gradient thresholds along the ascent profile to detect and isolate steep climb sections embedded in long gentle ascents prior to distance summit dilution.
• Climb scoring ($\text{Score} = \text{length (m)} \times \text{gradient (\%)}$) and category thresholds (`HC`, `CAT_1`..`CAT_4`) defined in `REQ-MAP-027` SHALL remain strictly preserved.
2. *GPX Elevation Gain Hysteresis & DEM Batch Fault Resilience (`GpxRouteImporter.kt`, `REQ-MAP-055.2`)*:
• In `GpxRouteImporter.calculateElevationGain`, the system SHALL implement an elevation delta hysteresis threshold ($\ge 2.0\text{ m}$), accumulating vertical gain only after elevation changes exceed the hysteresis floor, preventing GPS/DEM high-frequency jitter from inflating total route ascent.
• In `GpxRouteImporter` DEM batch enrichment (`fetchElevationsForBatches`), when an individual batch query fails, times out, or encounters a non-fatal API error, the importer SHALL log a warning and continue processing subsequent batches rather than terminating the entire enrichment loop with `break`.
3. *TCX Decimal Numeric Rounding & Position-less Trackpoint Ingestion (`TcxTrackpointParser.kt`, `TrackpointModels.kt`, `REQ-MAP-055.3`)*:
• In `TcxTrackpointParser`, `<Watts>` and `<Cadence>` XML values SHALL be parsed using floating-point string conversion and rounded to nearest integer (`toDoubleOrNull()?.roundToInt()`), preventing `toIntOrNull()` from dropping decimal telemetry.
• `RawTrackpoint` SHALL declare nullable `lat` and `lng` properties (`Double? = null`). Trackpoints lacking `<Position>` tags SHALL be retained during workout TCX ingestion (`TcxWorkoutParser`), preserving heart rate, power, and cadence telemetry for indoor workouts, while `TcxCourseParser` SHALL filter out position-less points for route polyline projection.
4. *Interval-Aware Normalized Power Rolling Window & Dead Code Elimination (`PowerPhysiologyCalculator.kt`, `REQ-MAP-055.4`)*:
• In `PowerPhysiologyCalculator.calculateNormalizedPower`, the 30-second Coggan rolling window size SHALL be calculated dynamically based on the sampling interval: `maxOf(1, (30.0 / sampleIntervalSec).roundToInt())` with default `sampleIntervalSec = 1.0`, ensuring accurate 30-second rolling averages for non-1Hz power streams.
• In `PowerPhysiologyCalculator.calculateWorkKj`, the redundant `if (activeTimeSeconds > 0)` dead-code branch on lines 843–849 SHALL be cleanly excised.
5. *Power Zone 1 Coasting Inclusion & Non-Power Null Guard (`WorkoutRepository.kt`, `ZoneDistributionCalculator.kt`, `REQ-MAP-055.5`)*:
• In `WorkoutRepository.getPowerZoneDistribution` and `ZoneDistributionCalculator.calculateDistribution`, power samples with value `0 W` (coasting) SHALL be included in Power Zone 1 (Active Recovery) if and only if positive power samples exist (`samples.any { it.value > 0 }`).
• If all power samples are zero or power telemetry is absent/null, the distribution calculation SHALL return `null`, strictly preserving parity with `PowerZoneDistributionCalculatorTest` and preventing phantom Zone 1 cards on non-power workouts.
6. *Preservation of System Invariants (`REQ-MAP-055.6`)*:
• Zero alterations to database schemas (`Climbs.db`, `Routes.db`, `WorkoutSummaries.db`).
• Clean-room full test suite pass rate under `./gradlew testDebugUnitTest` SHALL remain 100%.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-MAP-055` under Parent Epic `ATT-2565` (*Climbs: Detection, Live ClimbPro & Elevation Pacing*), refining `REQ-MAP-027` (*ClimbPro Detection*), `REQ-MAP-025` (*DEM Elevation Enrichment*), `REQ-EXP-001` (*TCX Interoperability*), and `REQ-UI-370` (*Coggan Power Zones*). Targets `ClimbDetector.kt`, `GpxRouteImporter.kt`, `TcxTrackpointParser.kt`, `TrackpointModels.kt`, `PowerPhysiologyCalculator.kt`, `WorkoutRepository.kt`, and `ZoneDistributionCalculator.kt`.
2. *Historical Origin & Commit Trace*: Independent whole-app code review of `feature/ATT-3921` (`0234bfc0`, Oct 2026), tracked in `ATT-4033`. Traces to `ClimbDetector.kt` ($O(N^2)$ candidate re-scan introduced in `ATT-1281`, commit `19177a5b`), `GpxRouteImporter.kt` (raw delta sum and DEM batch break in `ATT-1502`, commit `3a65239e`), `TcxTrackpointParser.kt` (integer-only watts/cadence and non-null position guard in `ATT-58`, commit `2b5f7e41`), `PowerPhysiologyCalculator.kt` (hardcoded 30 samples window in `ATT-3184`, commit `25828a66`), and `ZoneDistributionCalculator.kt` (`pwr > 0` coasting drop in `ATT-3188`, commit `889fcebf`).
3. *Root Reason for Existing Formulation*: Candidate rejection in `ClimbDetector` advanced by single increments (`i++`) to avoid skipping overlapping peaks; GPX elevation gain summed raw deltas without anticipating GPS/DEM jitter; TCX parser assumed all trackpoints contain GPS positions and integer strings; Normalized Power assumed fixed 1 Hz (1s) sampling; Zone distribution filtered positive power (`pwr > 0`) to prevent non-power workouts from emitting empty Zone 1 cards without accounting for legitimate 0 W coasting during cycling.
4. *Preservation of Core Invariants*: Climb categorization scoring (`dist * avgGrade`) and HC..Cat 4 thresholds (`REQ-MAP-027`) remain strictly unchanged; route polyline projection in `TcxCourseParser` continues to require valid coordinates; zero-power sessions return `null` without phantom Zone 1 cards (`PowerZoneDistributionCalculatorTest` parity); default 1.0s sample interval retains exact bit-parity with existing NP/TSS suites; and 100% full clean-room unit test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a 10,000-point route or gentle ascent containing an embedded steep climb,
• *When* `detectClimbs` executes,
• *Then* peak index advances in $O(N)$ without $O(N^2)$ rescanning, and local steep sections are detected while preserving `REQ-MAP-027` scoring thresholds.
• *Given* noisy elevation trackpoints oscillating within $< 2.0\text{ m}$,
• *When* `calculateElevationGain` executes,
• *Then* elevation changes $< 2.0\text{ m}$ are filtered by hysteresis, and if a DEM batch fails, enrichment logs a warning and continues subsequent batches without aborting.
• *Given* TCX trackpoints with decimal `<Watts>250.6</Watts>` or missing `<Position>`,
• *When* parsed,
• *Then* watts and cadence are rounded to nearest integer via `toDoubleOrNull()?.roundToInt()`, and position-less trackpoints are preserved for indoor workouts while filtered for route polyline projection.
• *Given* a power stream sampled at interval $\Delta t \ne 1.0\text{ s}$,
• *When* `calculateNormalizedPower` executes,
• *Then* the rolling window evaluates $\max(1, \text{round}(30.0 / \Delta t))$ samples, and work-kJ dead code is removed.
• *Given* a workout with recorded positive power samples containing 0 W coasting,
• *When* `getPowerZoneDistribution` executes,
• *Then* 0 W samples are categorized into Power Zone 1 (Active Recovery); if all samples are zero or power data is absent, `null` is returned.

**Invariants**: Zero database schema changes, strict preservation of `REQ-MAP-027` climb scoring thresholds, coordinate validation in route importer, and 100% clean-room unit test suite pass rate.

### REQ-MAP-015: High-Fidelity Period Tracks [Verified]
- **Tests**: `TST-UI-058`
- **Implementation**: `PeriodsViewModel.kt`, `InteractivePeriodMap.kt`
- **Rationale**: The `PeriodMapScreen` SHALL display originally sampled (fine-granular) location tracks instead of thinned overviews to provide professional analytical depth.
- **Description**: High-Fidelity Period Tracks.

### REQ-MAP-017: Adaptive Heatmap Performance [Verified]
- **Tests**: `TST-PERF-001`
- **Implementation**: `MapUtils.kt`, `PeriodMapUtils.kt`
- **Rationale**: The system SHALL adapt heatmap point density based on the temporal scale (Period Type) to prevent memory exhaustion (OOM). Coarser resolutions (e.g., 50m+) SHALL be used for large periods (Month/Year) where sub-meter precision is not visible on a summary map.

### REQ-MAP-011: Multi-Track Selection (Workout Details) [Verified]
- **Tests**: `TST-UI-055`
- **Implementation**: `TrackOnMapScreen.kt`, `WorkoutRepository.kt`
- **Rationale**: The Workout Details map SHALL support displaying multiple location tracks (GPS, Fused, Network) alongside the primary "Tracked" path to allow for technical data quality comparison. This feature SHALL NOT be available in workout summaries or periods to maintain visual simplicity.
- **Description**: Multi-Track Selection (Workout Details).

### REQ-MAP-012: Track Selection Menu [Verified]
- **Tests**: `TST-UI-055`
- **Implementation**: `TrackOnMapScreen.kt`
- **Rationale**: The Workout Details map SHALL provide a floating action button that opens a selection menu to individually toggle the visibility of Tracked, GPS, Fused, and Network tracks. The selection menu SHALL include a color legend for each track provider.
- **Description**: Track Selection Menu.

### REQ-MAP-013: Persistent Map Selection [Verified]
- **Tests**: `TST-UI-056`
- **Implementation**: `TrackOnMapAftermathViewModel.kt`, `MyPreferenceManager.kt`
- **Rationale**: The Workout Details map SHALL persist the user's technical track visibility preferences across sessions to streamline repetitive data quality audits.
- **Description**: Persistent Map Selection.

### REQ-MAP-014: Period FAB Hierarchy [Verified]
- **Tests**: `TST-UI-057`
- **Implementation**: `PeriodMapScreen.kt`
- **Rationale**: The `PeriodMapScreen` SHALL position the Sharing FAB at the top of the overlay stack to prioritize community engagement features.
- **Description**: Period FAB Hierarchy.

### REQ-MAP-016: Heatmap DSL Support [Verified]
- **Tests**: `TST-SET-002`, `TST-MAP-010`
- **Implementation**: `MapContentScope.kt`, `MapUtils.kt`
- **Rationale**: The Map DSL SHALL support the declarative definition of Heatmap layers using standardized sequential gradients (Cyan -> Blue -> Indigo). The styling SHALL be zoom-adaptive. As the user zooms in, individual workout traces SHALL become fully opaque (Alpha 1.0) while the heatmap background weight recedes to a subtle density shadow. Member marker pins SHALL be culled at city-scale to prevent visual clutter and OOM crashes.

### REQ-MAP-018: Detailed Map View Heatmap Visibility [Verified]
- **Tests**: `TST-MAP-020`
- **Implementation**: `MapContentScope.kt`, `MapUtils.kt`, `PeriodMapUtils.kt`, `WorkoutClusterHeatmapScreen.kt`
- **Rationale**: The system SHALL render density heatmaps across all map views (including detailed Workout Cluster maps and Period Detail maps) at all zoom levels (zoom 1 to 18). Heatmap point weighting and intensity scaling SHALL ensure point density remains clearly visible without vanishing at high zoom levels. **Invariants**: The heatmap overlay MUST NOT obscure map terrain or road labels and MUST NOT exceed the `maxPoints` memory budget.

### REQ-MAP-019: Authoritative Sensor Extrema Spatial Positioning [`TST-MAP-021`]
- **Tests**: `TrackerService.java`, `LiveWorkoutSession.java`, `WorkoutSamplesDatabaseManager.java`
- **Implementation**: Ensure extrema markers (e.g. min/max altitude pins) accurately reflect true spatial locations on the route map.
- **Rationale**: The system SHALL bind sensor extrema (including minimum altitude `ExtremaType.MIN`, maximum altitude `ExtremaType.MAX`, and maximum speed) strictly to authoritative sensor telemetry (`getBestSensorData`), preventing secondary or noisy fallback sensors from polluting session peak values and spatial coordinates. If tracking commences before spatial fix (`currentPos == null`), the system SHALL anchor initial extrema to the first acquired valid GPS coordinate when the initial reading matches the baseline. Furthermore, legacy database queries for extrema positions SHALL filter out records lacking valid coordinates (`LATITUDE IS NOT NULL AND LONGITUDE IS NOT NULL`).

### REQ-MAP-020: Authoritative Workout Max Line Distance (Apex) Calculation & Self-Anchored Displacement Integrity [`TST-MAP-022`]
- **Tests**: `SpeedAndLocationDevice.java`, `LiveWorkoutSession.java`, `TrackerService.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutDataMapper.kt`
- **Implementation**: Ensure the Max Line Distance marker (`ic_distance`) accurately and reliably indicates the true furthest point from the start location on the route map across all recorded workouts.
- **Rationale**: The system SHALL ensure that a recorded workout's Maximum Line Distance (Apex) value and coordinate (`maxDisplacementLatLng`) accurately and authoritatively reflect the geographical point along the workout's recorded track that has the maximum geodesic displacement from the workout's start point (`startLatLng`) (amended by `REQ-DAT-033` in ATT-4003: apex self-healing writes decoupled from batch cursor iterations). `SpeedAndLocationDevice` SHALL instantiate `mLineDistanceSensor` as an instantaneous scalar gauge `MySensor<Double>` rather than an accumulator (`MyDoubleAccumulatorSensor`), preventing accumulator reset operations from corrupting displacement with negative offsets. During live workout recording, `LiveWorkoutSession` / `TrackerService` SHALL anchor line distance calculations directly to the workout's authoritative start coordinate (`startLatLng`), computing displacement dynamically rather than relying on unanchored or pre-tracking hardware sensor state. Upon workout completion, `TrackerService.finalizeLiveSession()` SHALL deterministically compute the authoritative maximum line distance and apex coordinate by evaluating the geodesic displacement of all recorded track points relative to `startPos`, persisting the result in `WorkoutSummariesDatabaseManager`. The system SHALL provide self-healing recalculation for existing workouts with missing, zero, or geometrically invalid apex coordinates by deriving the apex from the stored `mapPolyline` and `startLatLng`. **Invariants**: Existing raw samples database schema and non-target sensor extrema (Altitude, Speed, HR, Power) MUST NOT be altered.

### REQ-MAP-021: Dark Mode Map Styling for Live Route Tracking, Navigation, Cockpit, and Secondary Map Views [`TST-MAP-023`]
- **Tests**: `DarkMapStyle.kt`, `ATrainingTrackerMap.kt`, `PathPreviewMap.kt`, `EditKnownLocationDialog.kt`, `WorkoutClusterComponents.kt`, `ManualClusterScreen.kt`, `PeriodSummaryCard.kt`, `LapEditBottomSheet.kt`, `ImportBackupTabsScreen.kt`
- **Implementation**: Eliminate blinding white screen glare and save display battery on OLED screens across all map views in dark mode by applying universal AMOLED-optimized dark vector tile styling, anti-flash container backgrounds, and active anti-flash surface masks during list scrolling.
- **Rationale**: The system SHALL provide an AMOLED-optimized dark vector tile style for `ATrainingTrackerMap` and all secondary map views across the application (route previews, cluster heatmaps, lap editors, and dialog mini-maps), eliminating white tile loading flashes and glare in dark mode (ATT-1266, ATT-1479, ATT-1553):
1. *Universal Dynamic Map Style Resolution (`DarkMapStyle.kt`)*:
• `DarkMapStyle` SHALL expose `resolveMapProperties(isDark: Boolean, context: Context, isMyLocationEnabled: Boolean = false, lightMapType: MapType = MapType.TERRAIN): MapProperties`.
• When `isDark == true`, the factory SHALL return `MapProperties(mapType = MapType.NORMAL, mapStyleOptions = DarkMapStyle.getMapStyleOptions(context), isMyLocationEnabled = isMyLocationEnabled)`.
• When `isDark == false`, the factory SHALL return `MapProperties(mapType = lightMapType, mapStyleOptions = null, isMyLocationEnabled = isMyLocationEnabled)`.
• All secondary map composables (`PathPreviewMap.kt`, `EditKnownLocationDialog.kt`, `WorkoutClusterComponents.kt`, `ManualClusterScreen.kt`, `PeriodSummaryCard.kt`, `LapEditBottomSheet.kt`, `ImportBackupTabsScreen.kt`, `ATrainingTrackerMap.kt`) SHALL adopt this dynamic resolution.
2. *Double-Layer Anti-Flash Background & Surface Mask Protection*:
• All map containers and `GoogleMap` composables SHALL enforce `Modifier.background(if (isDark) Color(0xFF121212) else Color.White)` (or surface color token).
• All list preview map composables and preview containers (`PeriodSummaryCard.kt`, `PathPreviewMap.kt`, `WorkoutClusterComponents.kt`, `ManualClusterScreen.kt`, `LapEditBottomSheet.kt`, `EditKnownLocationDialog.kt`) SHALL render an active Anti-Flash Surface Mask (`DarkMapAntiFlashOverlay`) on top of the `GoogleMap` composable in dark mode that remains completely opaque until `onMapLoaded` fires, eliminating transient unstyled light tile flashes during view mounting and list scrolling (ATT-1553).
3. *AMOLED Dark Vector Tile Specification (`res/raw/map_style_dark.json`)*:
• The dark map styling SHALL define base geometry `#121212` or darker, water `#0A1118`, road hierarchy `#212121` to `#383838`, text labels `#9E9E9E`, and POI icons hidden.
4. *High-Contrast Live Track Polyline Adaptability*:
• In `MapLayers.kt`, `LiveTrackLayer` SHALL render the active session track in cyan (`#00E5FF`) in dark mode, and `Color.Blue` in light mode.
5. *Preserved System Invariants*:
• Standard light mode behavior (`MapType.TERRAIN`, null style options) SHALL strictly be preserved.
• Singleton caching in `DarkMapStyle` (<10 KB RAM, <3ms parse) and graceful fallback to unstyled `MapType.NORMAL` on parse errors SHALL be preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-MAP-021` targeting `ATrainingTrackerMap.kt`, `DarkMapStyle.kt`, `PeriodSummaryCard.kt`, `PathPreviewMap.kt`, `WorkoutClusterComponents.kt`.
2. *Historical Origin & Commit Trace*: Commit `5d8a0c23` (ATT-1266, 2026-09-12), commit `ceaf4070` (ATT-1479, 2026-09-28), and ATT-1553 (2026-09-28).
3. *Root Reason for Existing Formulation*: ATT-1479 introduced `DarkMapStyle.resolveMapProperties` and double-layer background protection behind the map. Physical testing on Google Pixel 10 revealed that native `MapView` surfaces render unstyled light base tiles in front of the Compose background before asynchronous style dispatch completes.
4. *Preservation of Core Invariants*: Refining `REQ-MAP-021` to mandate an active anti-flash surface mask placed on top of `GoogleMap` until `onMapLoaded` fires eliminates transient white flashes with mathematical certainty while preserving the light mode baseline (`MapType.TERRAIN`, null style options), singleton style caching (<10 KB RAM, <3ms parse), and custom route polyline colors.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the app running in Dark Mode (or AMOLED mode),
• *When* viewing route previews in `PathPreviewMap`, cluster cards in `WorkoutClusterComponents`, or dialog mini-maps in `EditKnownLocationDialog`,
• *Then* the map SHALL render with `MapType.NORMAL` and custom dark vector tile styling (`R.raw.map_style_dark`).
• *Given* the app running in Dark Mode,
• *When* any map composable mounts, loads tiles asynchronously, or pans into uncached boundaries,
• *Then* the underlying container background SHALL render `#121212` (zero white flashes).
• *Given* the app running in Dark Mode,
• *When* opening or scrolling list views containing maps (`PeriodSummaryCard`, `PathPreviewMap`, `WorkoutClusterComponents`),
• *Then* the map container SHALL display an opaque dark surface mask (`#121212`) covering the map until `onMapLoaded` fires, preventing any visible white frame or unstyled light tiles from appearing.
• *Given* the app running in Light Mode,
• *When* viewing any map composable,
• *Then* the map SHALL render with `MapType.TERRAIN` and null style options, preserving topographic shading.

**Invariants**: Light mode appearance, singleton caching in `DarkMapStyle`, custom route polyline colors, and 100% full-suite unit test pass rate MUST NOT be broken.

### REQ-MAP-022: Google Maps CameraUpdateFactory Initialization Resilience and Lifecycle Guarding in Import Route Preview [`TST-MAP-024`]
- **Tests**: `ImportBackupTabsScreen.kt`
- **Implementation**: Prevent fatal crashes on cold start and rapid dialog launch in ImportBackupTabsScreen while guaranteeing camera re-alignment and zoom bounds fitting across sequential queue items.
- **Rationale**: The system SHALL guard all camera animation and update calls against premature execution before the Google Maps native renderer and layout view are fully initialized, while guaranteeing that the camera actively re-aligns, centers, and fits bounds for every sequential cluster candidate presented in the import naming queue (ATT-1579, ATT-2022):
1. *Map Lifecycle Synchronization (`ClusterNamingDialog` in `ImportBackupTabsScreen.kt`)*:
• In `ClusterNamingDialog`, the system SHALL track map readiness via `var isMapLoaded by remember { mutableStateOf(false) }`, strictly retaining readiness across sequential queue items once the map is loaded.
• The `GoogleMap` composable SHALL register `onMapLoaded = { isMapLoaded = true }`.
2. *Guarded Camera Update Execution & Sequential Queue Re-Framing*:
• The camera animation effect SHALL bind to explicit keys `LaunchedEffect(state, bounds, isMapLoaded)`.
• The system SHALL only invoke `cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 50))` when `isMapLoaded == true && bounds != null`.
• The camera update execution SHALL be enclosed in a defensive `try-catch` block catching `Exception` (`NullPointerException`, `IllegalStateException`).
• *Explicit Fallback*: If `CameraUpdateFactory.newLatLngBounds` throws an exception, the system SHALL catch the exception without crashing and fall back to static camera centering: `cameraPositionState.position = CameraPosition.fromLatLngZoom(bounds.center, 12f)`.
3. *Comprehensive Envelope Calculation (Track & Markers)*:
• In `ClusterNamingDialog`, `bounds` calculation SHALL encompass all decoded polyline coordinates AND include `state.start`, `state.end`, and `state.apex` (when non-null), ensuring route marker icons are never clipped outside the visible viewport.
4. *Initial Baseline Framing*:
• `rememberCameraPositionState` SHALL initialize with `position = CameraPosition.fromLatLngZoom(bounds?.center ?: LatLng(0.0, 0.0), 12f)`, guaranteeing non-crashing baseline framing prior to map load.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-MAP-022` targeting `ImportBackupTabsScreen.kt`, fulfilling Epic `ATT-111` / `ATT-235` (Target Release: `V4.9.38`).
2. *Historical Origin & Commit Trace*: Commit `5c5d353dc808` (ATT-294), commit `e99cf4c34ebc` (ATT-304/305/306), and commit `2aab3ef6` (ATT-1579, Sprint 2026-40.4).
3. *Root Reason for Existing Formulation*: ATT-1579 introduced `isMapLoaded` to prevent cold start NPEs before `CameraUpdateFactory` initialized. However, `isMapLoaded` was erroneously keyed to `remember(state)`. In Jetpack Compose, progressing through queued clusters recomposes `ClusterNamingDialog` without unmounting the map view. Because the native `onMapLoaded` callback only fires once on initial layout, resetting `isMapLoaded` to `false` caused it to stay `false` permanently, preventing camera bounds re-alignment for subsequent clusters.
4. *Preservation of Core Invariants*: Retaining `isMapLoaded` across recompositions (`remember { mutableStateOf(false) }`), keying `LaunchedEffect(state, bounds, isMapLoaded)`, and including start/end/apex markers in the bounds envelope guarantees smooth camera re-framing for every queued item while strictly preserving the cold-start crash prevention from ATT-1579.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete navigating to the Import/Backup preview tab immediately upon cold start when `CameraUpdateFactory` is not yet initialized by Google Play Services,
• *When* `ClusterNamingDialog` is composed,
• *Then* the application SHALL NOT throw `NullPointerException`, and camera animation SHALL be deferred until `isMapLoaded` becomes `true`.
• *Given* an active `ClusterNamingDialog` where the map completes loading,
• *When* `isMapLoaded` transitions to `true` and `bounds` is non-null,
• *Then* the camera SHALL smoothly animate to frame the track bounds with 50dp padding.
• *Given* an import session with multiple cluster candidates in the queue ($N \ge 2$),
• *When* the athlete assigns or dismisses cluster 1 and the dialog advances to cluster 2,
• *Then* `isMapLoaded` SHALL remain `true`, and the camera SHALL immediately re-align, center, and animate to fit the new bounds of cluster 2 with 50dp padding.
• *Given* a cluster candidate with polyline track points, start pin, end pin, and apex pin,
• *When* `bounds` is calculated,
• *Then* the bounds envelope SHALL encompass all decoded polyline points as well as `state.start`, `state.end`, and `state.apex`.
• *Given* a transient timing anomaly where `CameraUpdateFactory.newLatLngBounds` throws an `IllegalStateException` or `NullPointerException`,
• *When* the exception is caught,
• *Then* the dialog SHALL NOT crash, and camera position SHALL fall back to `CameraPosition.fromLatLngZoom(bounds.center, 12f)`.

**Invariants**: Track polyline geometry, marker positioning, cluster scoring engine, queue sequencing, and cold-start crash prevention MUST NOT be broken.

### REQ-MAP-023: Prominent High-Contrast Rendering for Actively Navigated Routes Preserving Multi-Layer X-Ray Segment Synergy [`TST-MAP-025`]
- **Tests**: `MapModels.kt`, `MapLayers.kt`, `RoutesRepository.kt`, `Color.kt`
- **Implementation**: Provide prominent, high-contrast, directionally clear rendering for actively navigated routes while preserving multi-layer X-Ray Strava segment visibility.
- **Rationale**: The system SHALL visually differentiate actively followed/navigated routes from passive background routes on the tracking and detail maps, rendering active routes with prominent thickness, vibrant high-contrast color, and directional chevrons while strictly preserving the multi-layer X-Ray polyline synergy with Strava Live Segments (ATT-1841):
1. *Active Navigation State in MapRoute & RoutesRepository*:
• `MapRoute` SHALL include property `isActiveNavigation: Boolean = false`.
• `RoutesRepository` SHALL expose `val activeNavigatedRouteId: StateFlow<Long?>` and provide `fun setActiveNavigatedRoute(routeId: Long?)`, establishing a reactive single source of truth for active route navigation.
2. *High-Contrast Visual Hierarchy & Prominence Tokens*:
• *Color*: When `isActiveNavigation == true`, `MapRoute.color` SHALL resolve to `TTColor.RouteActiveNavigation` (Electric Emerald `Color(0xFF00E676)`). When `isSelected == true && !isActiveNavigation`, `color` SHALL resolve to `TTColor.RouteSelected` (`Color(0xFF228B22)` ForestGreen). When unselected, it SHALL resolve to `TTColor.RouteUnselected`.
• *Width*: When `isActiveNavigation == true`, `MapRoute.width` SHALL resolve to `MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH` (16f). When `isSelected == true`, it SHALL resolve to `MapVisualization.ROUTE_WIDTH` (10f). When unselected, it SHALL resolve to `MapVisualization.ROUTE_UNSELECTED_WIDTH` (6f).
3. *Preservation of Multi-Layer X-Ray Polyline Synergy (`XRayPolyline`)*:
• The Z-index hierarchy SHALL strictly enforce:
- Active Route Solid Base: `MapVisualization.ROUTE_ACTIVE_BASE_Z_INDEX` (25.0f).
- Passive Route Solid Base: `MapVisualization.ROUTE_BASE_Z_INDEX` (20.0f).
- Strava Segments: `MapVisualization.SEGMENT_Z_INDEX` (30.0f).
- Passive Route Dashed Overlay: `MapVisualization.ROUTE_OVERLAY_Z_INDEX` (40.0f).
- Active Route Dashed Overlay: `MapVisualization.ROUTE_ACTIVE_OVERLAY_Z_INDEX` (45.0f).
• This invariant guarantees that Strava segments running along the active route render above the solid green base ($30\text{f} > 25\text{f}$) and remain visible through the dash gaps of the active overlay ($45\text{f}$ dashed), preventing polyline masking.
4. *Directional Chevrons (Forward Travel Arrows)*:
• In `MapLayers.kt`, when `path is MapRoute && path.isActiveNavigation && currentZoom > 13f`, the system SHALL render periodic forward-pointing directional chevrons along the polyline using cached `directionIcons`, rotating markers to match polyline segment bearing.
5. *Passive Route Subordination*:
• Passive routes marked visible on the map SHALL retain their 10f width and muted green styling, preventing visual clutter when displayed alongside the active route.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-MAP-023` targeting `MapModels.kt`, `MapLayers.kt`, `RoutesRepository.kt`, `Color.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-1841`, sprint `2026-40.14`, target release `V4.9.39`, Epic `ATT-66` (*[Epic] Improve Routes*).
3. *Root Reason for Existing Formulation*: Baseline route rendering used uniform 10f green lines with no distinction between passive visible routes and the active navigation path, causing low contrast under outdoor sunlight and directional confusion on overlapping loops.
4. *Preservation of Core Invariants*: Passive selected routes retain 10f width and ForestGreen styling; Strava Live Segments retain `SEGMENT_Z_INDEX = 30f` and orange color; the 3-tier X-Ray polyline sandwich (`baseZIndex < SEGMENT_Z_INDEX < overlayZIndex`) remains mathematically preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active route set in `RoutesRepository.setActiveNavigatedRoute(routeId)`,
• *When* rendered on the tracking map,
• *Then* the active route SHALL render with width 16f and vibrant Electric Emerald `#00E676` color.
• *Given* an active route overlapping with a Strava segment,
• *When* rendered on the map,
• *Then* the orange segment SHALL render between the solid active base ($Z=25\text{f}$) and dashed active overlay ($Z=45\text{f}$), fully visible through the overlay dash gaps.
• *Given* the map view zoomed to `zoom > 13f`,
• *When* viewing the active route,
• *Then* directional chevrons SHALL appear along the polyline indicating the route's travel direction.
• *Given* passive routes selected on the map alongside the active route,
• *When* rendered,
• *Then* passive routes SHALL render with width 10f and ForestGreen `#228B22`, establishing a clear visual hierarchy.

**Invariants**: Strava Live Segment detection and rendering (`SEGMENT_Z_INDEX = 30f`), existing route database schema, and 100% full-suite test pass rate MUST NOT be broken.

### REQ-MAP-024: Quick Route Selector from Cockpit with GPS Proximity Sorting and Automated Route Detection [`TST-MAP-026`]
- **Tests**: `RouteProximityRanker.kt`, `RouteAutoDetector.kt`, `RouteSelectorSheet.kt`, `SensorStatus.kt`, `SensorGridScreen.kt`, `TuningPreferencesDataStore.kt`, `AdvancedTuningDialog.kt`
- **Implementation**: Provide fast, frictionless route selection from the Cockpit with smart tie-breaking for home routes and automated in-ride route detection.
- **Rationale**: The system SHALL provide a 1-tap Quick Route Selector accessible directly from the Cockpit, sort and rank candidate routes using multi-stage tie-breaking for shared start locations, and automatically detect matching saved routes during active recording (ATT-1835):
1. *Cockpit 1-Tap Entry Point*:
• The Cockpit top action bar (`SensorStatus.kt` / `SensorGridScreen.kt`) and Map overlay SHALL display a route action button.
• Inactive navigation: Displays route icon with label/affordance to select a route.
• Active navigation: Displays the active route name with checkmark and provides quick stop/clear.
• Tapping the button opens `RouteSelectorSheet`.
2. *Multi-Stage Route Ranking & Tie-Breaking Engine (`RouteProximityRanker`)*:
• *Tier 1 (Proximity Group)*: Routes whose start point is within $R \le 250\text{ m}$ of the athlete's current location form the top group.
• *Tier 2 (Sport Match)*: Within the proximity group, routes matching the active workout sport profile (`route.bSportType == activeSport`) take precedence.
• *Tier 3 (Movement Heading Alignment)*: When the athlete is moving or a valid heading is present, routes whose initial $500\text{ m}$ path departs in the direction of travel ($\Delta \text{bearing} \le 45^\circ$) rank highest.
• *Tier 4 (Recency & Frequency)*: More recently created/synced/used routes (`syncedAt`) take precedence.
• *Tier 5 (Fallback)*: Ascending route length or alphabetical sorting.
• *Out-of-Radius Routes*: Routes starting $> 250\text{ m}$ away are ranked by ascending geodesic distance to start point.
3. *Adaptive Route Selector Sheet UI (`RouteSelectorSheet.kt`)*:
• *Dynamic Filter Chips*: When total available routes $\ge 5$, the sheet SHALL display compact distance chips (`< 40 km`, `40–80 km`, `> 80 km`) and sorting chips (*Nähe*, *Zuletzt gefahren*, *Länge*). When $< 5$ routes exist, filter chips SHALL be hidden.
• *1-Tap Activation & Deselection*: Tapping a route immediately sets it as `activeNavigatedRouteId` via `RoutesRepository.setActiveNavigatedRoute`. When a route is currently active, a dedicated "Route beenden / abwählen" action at the top clears the active route.
4. *Automated Route Detection Engine (`RouteAutoDetector`)*:
• During active recording when `activeNavigatedRouteId == null` and `routeAutoDetectEnabled == true`, the system SHALL evaluate the recorded GPS trajectory against saved routes.
• If cross-track distance is $\le 30\text{ m}$ for a continuous trajectory $\ge \text{threshold}$ (default $200\text{ m}$) and heading aligns ($\Delta \theta \le 35^\circ$), the system SHALL trigger a route detection event.
• In *Prompt* mode (default), the Cockpit displays an actionable banner/dialog allowing the athlete to activate or dismiss the route. In *Seamless* mode, the route is auto-joined immediately.
5. *Navigation Tuning Configuration*:
• `TuningPreferencesDataStore` and `AdvancedTuningDialog` SHALL provide user controls for `routeAutoDetectEnabled` (boolean, default true), `routeAutoJoinMode` (`PROMPT` vs `SEAMLESS`), and `routeAutoDetectThresholdMeters` (integer, default 200m).
6. *Localization Parity*:
• All user-facing strings SHALL maintain 100% parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement only (`REQ-MAP-024`). No existing requirements modified.
2. *Historical Origin & Commit Trace*: Ticket `ATT-1835`, sprint `2026-40.14`, target release `V4.9.39`, Epic `ATT-66` (*[Epic] Improve Routes*).
3. *Root Reason for Existing Formulation*: Route selection was disconnected from the tracking Cockpit, requiring multi-step navigation through the drawer and routes list; multiple routes starting from home lacked directional tie-breaking; and in-ride route following lacked automated detection.
4. *Preservation of Core Invariants*: Existing `RoutesRepository` methods, map layers, tracking lifecycle, and 100% full-suite test pass rate remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete at home with 8 routes starting within 250m and beginning to ride east,
• *When* opening the Route Selector from the Cockpit,
• *Then* routes departing eastward with matching sport profile and recent usage appear at the top.
• *Given* a user with fewer than 5 saved routes,
• *When* opening the Route Selector,
• *Then* filter chips are hidden; when $\ge 5$ routes exist, filter chips are displayed.
• *Given* an active workout recording without an active route,
• *When* the athlete follows a saved route for 200m ($\Delta \text{dist} \le 30\text{m}$, $\Delta \text{bearing} \le 35^\circ$),
• *Then* the Cockpit prompts the athlete with an auto-detect banner to activate the route.
• *Given* an active route in navigation,
• *When* the athlete taps "Route beenden" in the sheet,
• *Then* `activeNavigatedRouteId` is cleared and active highlighting is removed.

**Invariants**: Route database schema, GPS recording integrity, and 100% full-suite test pass rate MUST NOT be broken.

### REQ-MAP-025: Automatic DEM Elevation Enrichment for Imported GPX Routes Lacking Altitude Data [`TST-MAP-027`]
- **Tests**: `GpxRouteImporter.kt`, `ElevationService.kt`, `GpxImportViewModel.kt`
- **Implementation**: Automatically enrich altitude-deficient GPX routes with DEM elevation data to restore elevation profiles, climb analytics, and gain calculations while providing resilient offline fallback.
- **Rationale**: The system SHALL detect imported GPX route files lacking altitude data and automatically enrich their track points with authoritative elevation from the Open-Meteo Digital Elevation Model (DEM) API while guaranteeing robust batching, offline fallback resilience, and preservation of embedded GPX altitude (ATT-1502):
1. *Deficiency Detection & Elevation Preservation*:
• During GPX route parsing in `GpxRouteImporter`, the system SHALL evaluate track and route points (`trackPoints`).
• If at least one point contains non-null, non-zero elevation (`trackPoints.any { it.elevation != null && it.elevation != 0.0 }`), the system SHALL retain the embedded elevations verbatim and MUST NOT execute outbound network enrichment queries.
• If all points lack elevation or have flat 0.0m altitude (`trackPoints.none { it.elevation != null && it.elevation != 0.0 }`), the system SHALL trigger automatic DEM enrichment.
2. *Batching & Query Length Protection*:
• The system SHALL batch coordinates into chunks of at most 100 points (`MAX_BATCH_SIZE = 100`), preventing HTTP 414 (URI Too Long) errors on long routes ($N > 100$).
• Elevation queries SHALL be dispatched sequentially or with bounded concurrency via `ElevationService.getBatchElevationsAsync(chunk)` on `Dispatchers.IO`.
3. *Metrics Recalculation & Integrity*:
• Upon successful enrichment, the system SHALL update each `PathPoint.altitude` with retrieved DEM elevations, retaining point sequence and coordinates.
• The system SHALL recalculate the route's total `elevationGain` from the enriched profile and persist the updated gain in `RouteSummary`.
4. *Resilient Offline Fallback & Circuit Breaker Respect*:
• If network connectivity is unavailable, requests time out, HTTP 429 rate limit is encountered, or the circuit breaker is open, the system SHALL catch the failure gracefully and retain the track with default 0.0m altitude.
• The route import process SHALL NOT fail or throw exceptions due to elevation lookup failures.
5. *UI State & Localization Parity*:
• The import workflow SHALL notify the user of progress during enrichment.
• All new user-facing status messages SHALL maintain 100% translation parity across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement only (`REQ-MAP-025`). No existing requirements modified.
2. *Historical Origin & Commit Trace*: Ticket `ATT-1502`, sprint `2026-40.14`, target release `V4.9.39`, Epic `ATT-66` (*[Epic] Improve Routes*).
3. *Root Reason for Existing Formulation*: GPX routes created by third-party planners often omit `<ele>` altitude tags, resulting in flat lines in `ElevationProfile`, zero elevation gain, and broken climb analytics.
4. *Preservation of Core Invariants*: GPX files with existing valid altitude tags remain untouched; Open-Meteo DEM rate limit backoff and circuit breaker in `ElevationService` are strictly respected; import never crashes or aborts on network error.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an imported GPX route containing valid non-zero `<ele>` elevation tags,
• *When* parsed by `GpxRouteImporter`,
• *Then* `ElevationService` SHALL NOT be called, and original elevations SHALL be preserved.
• *Given* an imported GPX route where all points have null or 0.0m elevation,
• *When* parsed with active network connection,
• *Then* coordinates SHALL be chunked into batches ($\le 100$) and enriched via `ElevationService`, updating `PathPoint.altitude` and recalculating `RouteSummary.elevationGain` > 0m.
• *Given* an altitude-deficient GPX route imported while offline or when the DEM API returns an error / 429 rate limit,
• *When* enrichment fails,
• *Then* `GpxRouteImporter` SHALL NOT throw an exception, and the route SHALL successfully import as a 2D track with 0.0m altitude.

**Invariants**: Existing route database schema, GPX trackpoint ordering, `ElevationService` rate limiting and circuit breaker, and 100% full-suite test pass rate MUST NOT be broken.

### REQ-MAP-026: Support Waypoints, POIs (Benches, Water, Summits) and TCX Course Points [`TST-MAP-028`]
- **Tests**: `GpxRouteImporter.kt`, `TcxCourseParser.kt`, `RoutesDatabaseManager.kt`, `RouteWaypoint.kt`, `MapLayers.kt`, `ATrainingTrackerMap.kt`
- **Implementation**: Support parsing, persisting, projecting, and visualizing waypoints and course points from imported GPX and TCX route files.
- **Rationale**: The system SHALL support parsing, persisting, projecting, and visualizing waypoints and course points from imported GPX and TCX route files (ATT-58):
1. *GPX Waypoint Parsing (`GpxRouteImporter.kt`)*:
• During GPX parsing, the system SHALL extract `<wpt>` elements (`latitude`, `longitude`, `elevation`, `name`, `desc`, `sym`, `type`).
• The system SHALL classify GPX symbols and types into standard `WaypointType` enums: Benches (`POI_BENCH`), Water Sources (`POI_WATER`), Summits/Passes (`POI_SUMMIT`), Food/Cafes (`POI_FOOD`), Viewpoints (`POI_VIEWPOINT`), Hazards (`POI_DANGER`), First Aid (`POI_FIRST_AID`), and Generic landmarks (`GENERIC`).
2. *TCX Course & Course Point Parsing (`TcxCourseParser.kt`)*:
• The system SHALL parse TCX course files (`.tcx`) containing `<Courses><Course>`, extracting trackpoints and `<CoursePoint>` elements.
• The system SHALL map TCX `PointType` values (`Water`, `Food`, `Summit`, `Valley`, `Danger`, `First Aid`, `Left`, `Right`, `Straight`, `Generic`) to corresponding `WaypointType` enums.
3. *Distance Along Route Calculation (`WaypointDistanceCalculator.kt`)*:
• For each imported waypoint, the system SHALL calculate its orthogonal or nearest projection along the route's polyline segments and assign cumulative `distance_from_start` (in meters).
4. *Database Schema Extension & Cascade Deletion (`RoutesDatabaseManager.kt`)*:
• The system SHALL upgrade `RoutesDbHelper.DB_VERSION` from 9 to 10 by creating table `route_waypoints` (`id INTEGER PRIMARY KEY AUTOINCREMENT`, `route_id INTEGER NOT NULL`, `lat REAL NOT NULL`, `lng REAL NOT NULL`, `altitude REAL DEFAULT 0.0`, `name TEXT`, `description TEXT`, `type TEXT NOT NULL`, `distance_from_start REAL DEFAULT 0.0`, with foreign key `route_id REFERENCES routes(id) ON DELETE CASCADE`) and index `idx_route_waypoints_route_id`.
• Deleting a route SHALL automatically purge all associated waypoints via SQLite cascade deletion.
• `RouteWithPath` SHALL include property `val waypoints: List<RouteWaypoint> = emptyList()`, preserving full backwards compatibility.
5. *Map Visualization & Interactive POIs (`MapLayers.kt`, `ATrainingTrackerMap.kt`)*:
• The map SHALL render distinct vector marker icons for waypoints of the active or selected route above the route polyline ($Z \ge 50\text{f}$).
• Tapping a waypoint marker SHALL display an interactive info card showing name, waypoint category, altitude, and distance from start.
6. *9-Language Localization Parity*:
• All waypoint types, category labels, and cue sheet strings SHALL maintain 100% parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement only (`REQ-MAP-026`). No existing requirements modified.
2. *Historical Origin & Commit Trace*: Ticket `ATT-58`, sprint `2026-40.14`, target release `V4.9.39`, Epic `ATT-66` (*[Epic] Improve Routes*).
3. *Root Reason for Existing Formulation*: Routes imported from GPX and TCX discarded all `<wpt>` waypoints and course points, rendering routes as static lines without landmarks, water stops, or turn cues.
4. *Preservation of Core Invariants*: Backwards compatibility of `RoutesDatabaseManager` for existing routes without waypoints; SQLite foreign key cascade deletion; 100% full-suite unit test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a GPX file containing trackpoints and `<wpt>` tags for a water fountain and a rest bench,
• *When* imported via `GpxRouteImporter`,
• *Then* `route_waypoints` in `Routes.db` SHALL store both waypoints classified as `POI_WATER` and `POI_BENCH` with accurate projected `distance_from_start`.
• *Given* a TCX course file containing `<CoursePoint>` elements for `Left` and `Summit`,
• *When* imported,
• *Then* the waypoints SHALL be persisted with types `TURN_LEFT` and `POI_SUMMIT`.
• *Given* an existing route with stored waypoints,
• *When* the route is deleted from `RoutesDatabaseManager`,
• *Then* all associated waypoints in `route_waypoints` SHALL be automatically removed by `ON DELETE CASCADE`.
• *Given* an active route rendered on the map,
• *When* viewing at navigational zoom,
• *Then* waypoint markers with distinct icons SHALL render along the route, and tapping a marker SHALL display its details.

**Invariants**: Route polyline coordinates, DEM elevation enrichment (`REQ-MAP-025`), SQLite cascade deletion, and 100% full-suite test pass rate MUST NOT be broken.

### REQ-MAP-027: Persistent Climbs Database & Live ClimbPro Cockpit Sheet (Route & Free-Riding Support) [`TST-MAP-029`]
- **Tests**: `ClimbDetector.kt`, `ClimbsDatabaseManager.kt`, `LiveClimbsRepository.kt`, `LiveClimbSheet.kt`, `SensorGridScreen.kt`
- **Implementation**: Support detecting, persisting, tracking, and visualizing climbs with dynamic grade profiles during route navigation and free riding.
- **Rationale**: The system SHALL detect, persist, track, and visualize climbs with dynamic grade profiles during route navigation and free riding (ATT-1281):
1. *Automated Climb Detection Engine (`ClimbDetector.kt`)*:
• The system SHALL analyze route trackpoints (`PathPoint`) with elevation profiles to detect ascents matching configurable thresholds: length $\ge 500\text{ m}$, average gradient $\ge 3.0\%$, and vertical gain $\ge 20\text{ m}$.
• The detector SHALL tolerate micro-dips within a climb ($\le 15\text{ m}$ descent or $\le 150\text{ m}$ flat) without splitting the ascent, provided net slope remains positive.
• Climbs SHALL be classified into standard categories based on $\text{Score} = \text{length (m)} \times \text{gradient (\%)}$: `HC` ($\ge 80,000$), `CAT_1` ($\ge 64,000$), `CAT_2` ($\ge 32,000$), `CAT_3` ($\ge 16,000$), `CAT_4` ($\ge 8,000$), or `UNCATEGORIZED`.
2. *Persistent Climbs Database & Spatial Deduplication (`ClimbsDatabaseManager.kt`)*:
• The system SHALL maintain an independent SQLite database `Climbs.db` with table `climbs` (`id INTEGER PRIMARY KEY AUTOINCREMENT`, `name TEXT NOT NULL`, `route_id INTEGER`, `start_lat REAL NOT NULL`, `start_lng REAL NOT NULL`, `end_lat REAL NOT NULL`, `end_lng REAL NOT NULL`, `distance_m REAL NOT NULL`, `elevation_gain_m REAL NOT NULL`, `avg_grade REAL NOT NULL`, `max_grade REAL NOT NULL`, `climb_category TEXT NOT NULL`, `path_polyline TEXT NOT NULL`) and spatial indexes.
• Spatial deduplication SHALL skip or update redundant entries when both start and summit lie within $50\text{ m}$ of an existing climb.
3. *Live Climb Tracking Engine (`LiveClimbsRepository.kt`)*:
• The system SHALL track live GPS coordinates and bearing, managing transitions: `FAR_FAR_AWAY`, `APPROACHING` ($\le 250\text{ m}$, bearing $\Delta \le 45^\circ$), `ON_CLIMB` (crossed start gate), and `FINISHED` (summit crossed).
• When navigating an active route, climbs along the route SHALL be sequenced with index and total count. When free riding, all nearby stored climbs SHALL be monitored.
4. *Live Climb Cockpit Sheet UI (`LiveClimbSheet.kt`, `SensorGridScreen.kt`)*:
• The Cockpit BottomSheet SHALL display the climb name, category badge, real-time color-coded slope profile (`TTColor.Zone1` to `Zone5`), rider position pin, distance to summit, remaining elevation gain, and current gradient.
• If a Strava Live Segment is active (`ON_SEGMENT`), it SHALL take visual precedence, reverting to the climb upon completion.
5. *Tuning & Localization Parity*:
• Configurable toggles `showLiveClimbs`, `climbMinLengthMeters`, `climbMinGradientPercent` in `TuningPreferencesDataStore`. All strings SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement only (`REQ-MAP-027`). No existing requirements modified.
2. *Historical Origin & Commit Trace*: Ticket `ATT-1281`, sprint `2026-40.14`, target release `V4.9.39`, Epic `ATT-66` (*[Epic] Improve Routes*).
3. *Root Reason for Existing Formulation*: Athletes tracking workouts lack automated climb awareness, summit countdowns, and real-time grade profiles during outdoor workouts.
4. *Preservation of Core Invariants*: Existing route database schema v10, Strava Live Segments priority, and 100% full-suite unit test pass rate MUST NOT be broken.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an imported route with a 1.2km hill ascending 84m (7% average grade),
• *When* parsed by `ClimbDetector`,
• *Then* exactly 1 climb SHALL be detected with category `CAT_4`, length ~1200m, and gain ~84m.
• *Given* two routes sharing the same climb within 50m of start and end coordinates,
• *When* saved to `ClimbsDatabaseManager`,
• *Then* the climb SHALL be deduplicated without creating duplicate rows.
• *Given* an athlete approaching a stored climb within 250m with aligned heading,
• *When* tracking in the Cockpit,
• *Then* `LiveClimbSheet` SHALL slide up displaying the color-coded profile, countdown distance, and remaining vertical gain.

**Invariants**: Strava Live Segment priority, single-threaded database access, and 100% full-suite test pass rate MUST NOT be broken.

### REQ-MAP-028: Visual & Auditory Turn-by-Turn Navigation Prompts, Battery Saver Wake-Up & Off-Route Alerts [Provide visual countdown prompts, auditory alert chimes, automated AMOLED battery saver display wake-up, and off-route corridor deviation monitoring during active route navigation.]
- **Tests**: $ with deterministic tie-breaking ($i_{\text{apex}} = i_{\text{start}} + \lfloor (K-1)/2 \rfloor$) and 5° hysteresis buffer, suppressing gentle road bends ($< 25^\circ$), clamping boundaries (10m from start/end, empty list for routes $< 20\text{m}$ or $< 3$ points), and consolidating $180^\circ$ hairpin switchbacks into a single `U_TURN` cue (ATT-3494).
2. *Real-Time Navigation Engine (`TurnByTurnNavigationEngine.kt`)*:
• When `RoutesRepository.activeNavigatedRouteId` is non-null, the engine SHALL project the athlete's current location onto the active route polyline, computing distance along the route ($D_{\text{current}}$) and cross-track orthogonal error ($D_{\text{cross}}$).
• The engine SHALL identify the next upcoming turn cue ahead ($D_{\text{cue}} > D_{\text{current}}$) and calculate remaining countdown distance $D_{\text{remain}} = D_{\text{cue}} - D_{\text{current}}$.
• State Lifecycle: `APPROACHING` when $D_{\text{remain}} \le \text{countdownThreshold}$ (default $150\text{ m}$); `TURN_NOW` when $D_{\text{remain}} \le 25\text{ m}$; `PASSED` when $D_{\text{current}} > D_{\text{cue}} + 20\text{ m}$, cleanly advancing to the next cue.
3. *Visual Cockpit HUD Banner (`TurnPromptBanner.kt`)*:
• Rendered as a prominent, glanceable overlay in `SensorGridScreen.kt` when a turn cue is approaching ($\le 150\text{ m}$) or when the athlete is off-route.
• Displays turn direction arrow icon, countdown distance in meters (e.g., "150 m", "50 m", or "JETZT" / "NOW" when $\le 25\text{ m}$), and way/street name if available.
• For off-route state, displays a high-visibility warning banner with "Off Route / Route verlassen" and distance back to the route corridor.
4. *Automated AMOLED Battery Saver Display Wake-Up*:
• Upon entering the `APPROACHING` threshold ($\le 150\text{m}$), the system SHALL invoke `BatterySaverController.onWakeupEvent()`, restoring 100% display brightness for the configured wake-up duration.
• Upon entering `OFF_ROUTE`, the system SHALL also invoke `BatterySaverController.onWakeupEvent()`.
5. *Auditory Chime Alerts (`NavigationAudioAlertManager.kt`)*:
• When audio cues are enabled, the system SHALL emit distinct audio chimes via Android `ToneGenerator` (`AudioManager.STREAM_NOTIFICATION` or `STREAM_MUSIC`): approach prompt double tone alert ($\le 150\text{ m}$), immediate turn ascending confirmation chime ($\le 25\text{ m}$), off-route descending warning chime, and rejoining route confirmation tone.
6. *Off-Route Corridor Monitoring & Recovery*:
• If cross-track distance $D_{\text{cross}} > \text{corridorThreshold}$ (default $50\text{ m}$) for $\ge 3$ consecutive GPS fixes, the system transitions to `OFF_ROUTE`.
• When returning to $D_{\text{cross}} \le 30\text{ m}$, the system transitions back to `ON_ROUTE`.
7. *Tuning Preferences & 100% 9-Language Localization Parity*:
• `TuningPreferencesDataStore` provides `turnPromptsEnabled: Boolean` (default true), `turnAudioAlertsEnabled: Boolean` (default true), `turnCueCountdownDistanceMeters: Int` (default 150), and `offRouteCorridorThresholdMeters: Int` (default 50).
• All user-facing strings SHALL maintain 100% parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-MAP-028` (Section 1: *Turn Cue Source & Geometric Synthesis*), targeting `TurnCueDetector.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-1450`, sprint `2026-40.14`, commit `54c5c2d3` (target release `V4.9.39`, Epic `ATT-66` / `ATT-2564`), amended in `ATT-3494` (sprint `2026-42.3`, target release `V4.9.40`).
3. *Root Reason for Existing Formulation*: Baseline single-chord 15-meter window was a minimal heuristic that missed sweeping curves (30–80m), emitted nuisance cues on winding roads, and anchored cues to early threshold vertices rather than the geometric apex.
4. *Preservation of Core Invariants*: Stored course points continue to take absolute precedence; `TurnCue` DTO schema and monotonic distance ordering are strictly preserved; downstream navigation lifecycle and caller distance timing thresholds (150m for `APPROACHING`, 25m for `TURN_NOW`) remain unchanged; offline sovereignty and 100% full-suite test pass rate maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active route with a turn cue ahead,
• *When* the athlete enters the countdown threshold ($\le 150\text{m}$),
• *Then* `TurnPromptBanner` SHALL appear showing the turn arrow and countdown distance, an approach chime SHALL sound, and `BatterySaverController.onWakeupEvent()` SHALL be invoked.
• *Given* an approaching turn cue reaching $\le 25\text{m}$,
• *Then* the banner SHALL display "JETZT" / "NOW" and emit an immediate turn confirmation chime.
• *Given* an active navigation session where the athlete deviates $> 50\text{m}$ from the route for 3 fixes,
• *Then* the system SHALL transition to `OFF_ROUTE`, display the off-route warning banner, sound a warning chime, and wake the display.

**Invariants**: Offline calculation sovereignty, single-thread safety, and 100% full-suite test pass rate MUST NOT be broken.
- **Implementation**: \frac{d\theta}{ds}
- **Rationale**: The system SHALL provide visual countdown prompts, auditory alert chimes, automated AMOLED battery saver display wake-up, and off-route corridor deviation monitoring during active route navigation (ATT-1450, ATT-3494):
1. *Turn Cue Source & Geometric Synthesis (`TurnCueDetector.kt`)*:
• The system SHALL extract stored course points and waypoints with category `WaypointCategory.TURN_CUE` (`TURN_LEFT`, `TURN_RIGHT`, `TURN_STRAIGHT`) from `RoutesDatabaseManager.getWaypointsForRoute(routeId)` (`REQ-MAP-026`).
• For routes lacking embedded turn points, `TurnCueDetector` SHALL synthesize directional turn cues (`SLIGHT_LEFT`, `LEFT`, `SHARP_LEFT`, `SLIGHT_RIGHT`, `RIGHT`, `SHARP_RIGHT`, `U_TURN`) by pre-smoothing polyline bearings using a continuous distance-weighted spatial circular vector mean over a 5-meter envelope ($\pm 2.5\text{m}$ along cumulative distance) without coordinate decimation ($(\bar{x}, \bar{y}) = (\sum \Delta s_k \cos \theta_k, \sum \Delta s_k \sin \theta_k)$, falling back to chord bearing when total length $\le 10^{-4}\text{m}$), evaluating curvature across multi-scale forward sliding windows ($W_{\text{short}} = 12\text{--}15\text{m}$, $W_{\text{long}} = 35\text{--}50\text{m}$) in single-pass linear $O(N)$ execution with zero heap object allocation during internal processing (pre-allocated primitive arrays, index loops), anchoring cues at the true curvature apex $\max

### REQ-MAP-029: "Take Me Home" Return Navigation, Remaining Distance & Elevation-Aware ETA HUD [`TST-MAP-031`]
- **Tests**: `HomeLocationResolver.kt`, `ReturnCorridorSnapper.kt`, `ElevationAwareEtaCalculator.kt`, `ReturnNavigationHud.kt`, `SensorGridScreen.kt`, `RouteSelectorSheet.kt`
- **Implementation**: Provide one-tap "Take Me Home" return guidance, calculate remaining distance and elevation-aware ETA factoring in climbing ascents, and display the metrics in the Cockpit HUD.
- **Rationale**: The system SHALL provide a one-tap "Take Me Home" return guidance mode, calculate remaining distance and elevation-aware ETA, and display the metrics in the Cockpit HUD (ATT-1953):
1. *Home Base Resolution (`HomeLocationResolver.kt`)*:
• The system SHALL resolve the athlete's primary Home destination by querying `KnownLocationsDatabaseManager`.
• Priority 1: Any location whose name contains `"haus"`, `"home"`, or `"zuhause"` (case-insensitive substring match).
• Priority 2: In the absence of an explicitly named location, the location with `ExtremaType.START` having the highest `hitCount` (most frequent departure/arrival location).
• If resolved, returns `HomeDestination(id, name, latLng, altitude)`.
2. *Return Corridor Snapping (`ReturnCorridorSnapper.kt`)*:
• *Active Route Mode*: When `RoutesRepository.activeNavigatedRouteId` is non-null:
- For outbound/forward travel, destination is the route's endpoint (or start if loop). Remaining path spans from current projection point to route end.
- When "Return Home along route" is active, guidance follows the route polyline in reverse from current location back to route start ($D_{\text{remaining}} = D_{\text{current}}$, and climb $H_{\text{climb}}$ computed by summing positive elevations in reverse).
• *Unrouted Saved Route Snapping*: When no route is actively navigated:
- The system SHALL evaluate saved routes in `RoutesRepository.allRoutes` to identify candidate routes that terminate near Home ($\le 500\,\text{m}$) and where the athlete is within corridor proximity ($\le 100\,\text{m}$).
• *Direct Geodesic Fallback*: If no matching route is available, the system projects a direct return vector to the Home destination.
3. *Elevation-Aware Dynamic ETA Engine (`ElevationAwareEtaCalculator.kt`)*:
• The system SHALL calculate remaining climb $H_{\text{climb}}$ by summing all positive vertical elevation gains along the remaining path segments: $H_{\text{climb}} = \sum_{k} \max(0.0, \text{altitude}_{k+1} - \text{altitude}_{k})$.
• The system SHALL calculate predicted travel duration $T_{\text{remaining}}$ using the athletic climbing model: $T_{\text{remaining}} = \frac{D_{\text{remaining}}}{V_{\text{flat}}} + \frac{H_{\text{climb}}}{\text{VAM}_{\text{climb}}}$.
• Where $V_{\text{flat}}$ is smoothed rolling speed when moving ($> 1.5\,\text{m/s}$), clamped to athletic limits; defaults to activity profile baseline ($20.0\,\text{km/h} \approx 5.56\,\text{m/s}$ for cycling; $10.0\,\text{km/h} \approx 2.78\,\text{m/s}$ for running; $5.0\,\text{km/h} \approx 1.39\,\text{m/s}$ for walking).
• Where $\text{VAM}_{\text{climb}}$ is effective vertical ascent rate (default $500\,\text{m/h} \approx 0.1389\,\text{m/s}$ for cycling; $400\,\text{m/h} \approx 0.1111\,\text{m/s}$ for running/walking).
• Clock ETA: $\text{ETA} = \text{System.currentTimeMillis()} + (T_{\text{remaining}} \times 1000)$.
• Formatting: Exposes formatted clock arrival time (e.g. `"18:42"`) and remaining duration (e.g. `"~34 min"`).
4. *Cockpit HUD Integration (`ReturnNavigationHud.kt` in `SensorGridScreen.kt`)*:
• In `SensorGridScreen.kt`, when Return Navigation is active or an active route has remaining progress, render a high-visibility HUD banner displaying: destination name & icon ("🏠 Zu Hause" or route name), remaining distance in kilometers (e.g. `"12.4 km"`), remaining vertical gain in meters (e.g. `"+180 m"`), and elevation-adjusted dynamic ETA (e.g. `"18:42 (~34 min)"`).
5. *1-Tap Entry Points*:
• In `RouteActionChipRow` (`SensorGridScreen.kt`), display remaining distance and ETA directly in the chip row when navigation is active.
• In `RouteSelectorSheet.kt`, provide a prominent "Take Me Home" ("Heimweg") card/button at the top to initiate return navigation immediately.
6. *100% 9-Language Localization Parity*:
• All user-facing strings SHALL maintain 100% parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement only (`REQ-MAP-029`). No existing requirements modified.
2. *Historical Origin & Commit Trace*: Ticket `ATT-1953`, sprint `2026-40.16`, target release `V4.9.39`, Epic `ATT-66` (*[Epic] Improve Routes*).
3. *Root Reason for Existing Formulation*: Athletes tracking workouts lacked real-time visibility into remaining distance, remaining climb, and dynamic elevation-aware arrival times, leaving them vulnerable to pacing miscalculations and exhaustion in hilly terrain.
4. *Preservation of Core Invariants*: Existing route database schema v10, `RoutesRepository`, `TurnByTurnNavigationRepository`, `ClimbsDatabaseManager`, and 100% full-suite test pass rate MUST NOT be broken.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active tracking session with a loaded route or established home location,
• *When* the user activates "Take Me Home" or rides along the return corridor,
• *Then* `ReturnNavigationHud` SHALL display the destination name, remaining distance, remaining elevation gain, and predicted ETA.
• *Given* an active "Take Me Home" navigation session,
• *When* the remaining route segment contains significant uphill climbing ($H_{\text{climb}} > 0$),
• *Then* the predicted ETA SHALL explicitly incorporate the climbing ascent time penalty ($H_{\text{climb}} / \text{VAM}$) rather than naive flat-line speed division.
• *Given* an athlete navigating home,
• *When* snapping to the return route,
• *Then* guidance SHALL strictly follow the recorded route path or established return corridor rather than raw unverified motor routing.
• *Given* an athlete navigating an active out-and-back route,
• *When* initiating return home along the route,
• *Then* remaining distance and remaining elevation gain SHALL be calculated in reverse from current location back to route start.

**Invariants**: SQLite schemas for `Routes.db` and `StartLocation2Altitude.db` must not be altered, calculations must be 100% offline, and 100% full-suite test pass rate MUST NOT be broken.

### REQ-MAP-030: [SUPERSEDED / REMOVED by ATT-2463 / REQ-MAP-035] Corridor-Based Route Grouping & Differentiating Thumbnail Zoom [`TST-MAP-032`]
- **Tests**: `GeoUtils.kt`, `RouteTabbedScreen.kt`, `RouteItem.kt`, `RouteList.kt`
- **Implementation**: Excision of corridor-based route grouping and differentiating thumbnail zoom; migration of geodesic calculation utilities to GeoUtils; restoration of flat route list and full-extent previews.
- **Rationale**: **SUPERSEDED & REMOVED**: Corridor-based route grouping and differentiating thumbnail zoom capabilities were removed in ATT-2463 per human decision from the Sprint 2026-40.16 Joint Review. Route lists revert to standard flat list presentation, and route card thumbnails revert to standard full-extent route bounding box previews. Geodesic distance and initial bearing calculations required by Fork Navigation (`REQ-MAP-031`) were cleanly extracted into `GeoUtils.kt` (`REQ-MAP-035`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-MAP-030` (*Corridor-Based Route Grouping & Differentiating Thumbnail Zoom*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1954`, sprint `2026-40.16`, target release `V4.9.39`, Epic `ATT-66` (*[Epic] Improve Routes*).
3. *Root Reason for Existing Formulation*: Routes list displayed all routes in flat unorganized order, and full-extent thumbnails compressed unique middle loops into tiny indistinct squiggles.
4. *Preservation of Core Invariants*: Physical on-device review revealed that departure gateway chips added visual clutter and cropped middle-loop thumbnails lacked sufficient geographical orientation. Reverting to full-extent previews and a clean flat list restores optimal user scanning while preserving all surrounding route management and navigation invariants (`REQ-MAP-024`, `REQ-MAP-028`, `REQ-MAP-029`, `REQ-MAP-031`, `REQ-MAP-033`, `REQ-MAP-034`).

### REQ-MAP-035: Standalone Geodesic Calculation Utilities & Flat Route List Presentation [`TST-MAP-037`]
- **Tests**: `GeoUtils.kt`, `RouteTabbedScreen.kt`, `RouteItem.kt`, `RouteList.kt`, `ForkRouteMatcher.kt`, `RouteDivergenceDetector.kt`
- **Implementation**: Provide standalone geodesic calculation utilities, maintain clean flat route list presentation without gateway direction chips, and render full-extent route bounding box thumbnails.
- **Rationale**: The system SHALL provide pure standalone geodesic calculation utilities (`GeoUtils.kt`) for great-circle distance and initial azimuth bearing, maintain clean flat route list presentation in `RouteTabbedScreen` without gateway direction chips, and render full-extent route bounding box thumbnails in `RouteItem` (ATT-2463):
1. *Standalone Geodesic Utilities (`GeoUtils.kt`)*:
• The system SHALL provide `GeoUtils.haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double` calculating geodesic great-circle distance using Earth radius $R = 6371000\text{ m}$.
• The system SHALL provide `GeoUtils.calculateInitialBearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double` calculating forward azimuth bearing in degrees $[0.0, 360.0)$ clockwise from True North.
• `ForkRouteMatcher.kt` and `RouteDivergenceDetector.kt` SHALL consume `GeoUtils` with 100% mathematical equivalence.
2. *Flat Route List Presentation (`RouteTabbedScreen.kt`)*:
• `RouteTabbedScreen.kt` SHALL present routes in each sport tab as a clean flat list without departure gateway direction filter chips (`GatewayFilterChipsRow`).
3. *Full-Extent Route Thumbnails (`RouteItem.kt`)*:
• `RouteItem.kt` SHALL supply `targetBounds = null` to `PathPreviewMap`, guaranteeing map preview thumbnails render the global route bounding box encompassing the entire polyline.
4. *100% Clean Excision & Backward Compatibility*:
• Obsolete classes `RouteCorridorClassifier.kt`, `RouteBoundingBoxCalculator.kt`, and `GatewayFilterChipsRow.kt` SHALL be completely eliminated.
• Persisted DataStore settings from prior releases SHALL be ignored gracefully without deserialization or runtime errors.
• Obsolete gateway direction and corridor preference strings SHALL be removed across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Supersedes and removes `REQ-MAP-030` (*Corridor-Based Route Grouping & Differentiating Thumbnail Zoom*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2463`, Sprint `2026-41.1`, following physical device findings on Pixel 10 in Sprint `2026-40.16` Joint Review.
3. *Root Reason for Existing Formulation*: ATT-1954 attempted to group routes by initial departure vector and crop common corridors in map thumbnails. The resulting UX was deemed cluttered and disorienting.
4. *Preservation of Core Invariants*: In-ride fork decision alerts (`REQ-MAP-031`), turn-by-turn guidance (`REQ-MAP-028`), return navigation (`REQ-MAP-029`), and 100% full-suite test pass rate MUST NOT be broken.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing `RouteTabbedScreen`,
• *When* observing the route list,
• *Then* no compass gateway filter chips SHALL appear, and routes SHALL be displayed in a clean flat list.
• *Given* route cards in `RouteList`,
• *When* previewing route map thumbnails,
• *Then* each thumbnail SHALL render the full global route bounding box.
• *Given* `ForkRouteMatcher` and `RouteDivergenceDetector`,
• *When* evaluating fork points and approach bearings,
• *Then* `GeoUtils.haversineDistanceMeters` and `GeoUtils.calculateInitialBearing` SHALL produce deterministic, mathematically equivalent results.
• *Given* all 9 supported locales,
• *When* translation parity test runs,
• *Then* zero dangling gateway string resources SHALL exist.

**Invariants**: Pure geodesic mathematical functions with zero Android framework dependencies, offline local operation, and 100% full-suite test pass rate MUST NOT be broken.

### REQ-MAP-031: In-Ride Fork-in-the-Road Route Selection & Decision Alerts [`TST-MAP-033`]
- **Tests**: `ForkRouteMatcher.kt`, `RouteDivergenceDetector.kt`, `ForkNavigationRepository.kt`, `TurnByTurnNavigationRepository.kt`, `RoutesRepository.kt`, `ForkDecisionCard.kt`, `SensorGridScreen.kt`, `TuningPreferencesDataStore.kt`
- **Implementation**: Detect upcoming route divergence points along shared departure corridors during active tracking, present directional decision cards in Cockpit HUD, and provide 1-tap and autonomous route binding with configurable auto-follow and departure auto-unselection.
- **Rationale**: The system SHALL detect upcoming route divergence points along shared departure corridors during active tracking, present directional decision cards in the Cockpit HUD, provide 1-tap and autonomous route binding with configurable auto-follow, and automatically unselect an auto-bound route when the athlete departs from the route corridor (ATT-1955, ATT-3320, ATT-3326):
1. *Dynamic Outbound Corridor Matching (`ForkRouteMatcher.kt`)*:
• The system SHALL continuously evaluate the athlete's current workout trajectory against stored routes in `RoutesRepository.allRoutes` to track active candidates within spatial corridor tolerance ($\le 50\text{ m}$).
• Candidate routes must share the athlete's current outbound path for $\ge 300\text{ m}$.
2. *Divergence Vertex Detection & Alert Window (`RouteDivergenceDetector.kt`)*:
• The system SHALL analyze candidate polylines ahead to locate the upcoming divergence vertex where paths separate by $> 40\text{ m}$.
• The system SHALL calculate remaining distance along the corridor to the fork: $D_{\text{fork}}$.
• When $0 < D_{\text{fork}} \le 300\text{ m}$ and at least 2 distinct route branches diverge, the system SHALL activate the fork decision alert state.
3. *Cockpit Decision HUD Card (`ForkDecisionCard.kt` in `SensorGridScreen.kt`)*:
• When the fork decision alert is active and no single route is locked, the Cockpit SHALL render a prominent decision card presenting each branching choice.
• Each branch card SHALL display: relative directional arrow (Left, Straight, Right derived from branch bearing relative to corridor approach), route name, remaining/total distance, and remaining elevation gain.
• The athlete can manually tap any branch option to immediately bind that route to `RoutesRepository.activeNavigatedRouteId` and dismiss the prompt.
• The athlete can dismiss the card via a close/dismiss action.
4. *Autonomous Route Binding, Provenance & Auto-Unselection on Departure (`ForkNavigationRepository.kt`, `RoutesRepository.kt`, `TurnByTurnNavigationRepository.kt`)*:
• In `Advanced Settings -> Navigation`, the system SHALL provide a configurable toggle `autoFollowUniqueRouteEnabled` (`TuningPreferencesDefaults.DEFAULT_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED = true`, `TuningConfig`, `TuningPreferencesDataStore`) controlling fork autonomous route binding (ATT-3320).
• If the athlete proceeds past the divergence point and travels $\ge 50\text{ m}$ along one of the branches (cross-track error $< 25\text{ m}$ on chosen branch while cross-track error on alternative branches $> 50\text{ m}$):
- When `autoFollowUniqueRouteEnabled == true` (default): the system SHALL automatically bind the chosen route to `RoutesRepository.setActiveNavigatedRoute(chosenRoute.summary.id, isAutoSelected = true)`, dismiss the decision card, and initiate active turn prompts (`REQ-MAP-028`) and climb guidance (`REQ-MAP-027`).
- When `autoFollowUniqueRouteEnabled == false`: the system SHALL suppress autonomous route locking, dismiss the fork decision card, and preserve all currently selected candidate routes active on the map without binding `activeNavigatedRouteId`.
• `RoutesRepository` SHALL track route provenance via `setActiveNavigatedRoute(routeId: Long?, isAutoSelected: Boolean = false)` and expose `val isRouteAutoSelected: StateFlow<Boolean>`. Manual route selections (`selectRoute`, `selectRouteManually`) evaluate `isAutoSelected = false`, and clearing the route (`routeId == null`) resets `isRouteAutoSelected` to `false`.
• In `TurnByTurnNavigationRepository.kt`, when `newState.isOffRoute` triggers:
- If `routesRepository.isRouteAutoSelected.value == true`: the system SHALL automatically clear the active route (`routesRepository.setActiveNavigatedRoute(null)`), dismiss off-route banners, and re-enable candidate monitoring in `ForkNavigationRepository` among remaining selected routes.
- If `routesRepository.isRouteAutoSelected.value == false`: the system SHALL retain the active route, continuing standard off-route prompts and return guidance.
• If the athlete deviates $> 50\text{ m}$ from all candidate branches during a fork decision prompt, the fork decision card SHALL be dismissed without binding any route.
5. *100% 9-Language Localization Parity*:
• All decision card labels, directional cues, dismiss actions, and tuning preference strings SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new in `ATT-1955`; amended in `ATT-3320` (Clause 4, configurable auto-switch toggle in Advanced Navigation Settings); amended in `ATT-3326` (Clause 4, provenance tracking and auto-unselection of auto-bound routes upon leaving corridor).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1955`, sprint `2026-40.16` (commit `34c752d2` / `419e6ebe`); amended in Sprint `2026-42.1` (`ATT-3320`, `ATT-3326`).
3. *Root Reason for Existing Formulation*: When autonomous route binding was introduced in ATT-1955, routes were considered locked indefinitely once bound, treating auto-bound and manually selected routes identically. In field testing, an athlete who diverged onto a branch at a fork but subsequently took a detour was subjected to persistent "Off Route" audio alerts and red HUD cards for a route they never manually chose. Auto-unselecting auto-bound routes when departing from the corridor restores hands-free, clutter-free cockpit navigation.
4. *Preservation of Core Invariants*: Default enabled state (`true`) preserves hands-free auto-binding; manual selection on `ForkDecisionCard` and Route Sheet remains strictly preserved and immune to auto-unselection; pure geodesic math, offline operation, and 100% full-suite test pass rate MUST NOT be broken.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout tracking along a corridor shared by 2 or more stored routes,
• *When* the athlete approaches within $300\text{ m}$ of the divergence point ($D_{\text{fork}} \le 300\text{ m}$),
• *Then* the Cockpit SHALL display an ambient decision card presenting each branch's relative direction, route name, total distance, and elevation gain.
• *Given* an active fork decision prompt displayed in the Cockpit and `autoFollowUniqueRouteEnabled == true`,
• *When* the rider turns onto one of the diverging branches and travels $\ge 50\text{ m}$ along it,
• *Then* the system SHALL automatically bind the chosen route via `routesRepository.setActiveNavigatedRoute(chosenRoute.summary.id, isAutoSelected = true)`, dismiss the decision card, and initiate active guidance.
• *Given* an active route bound automatically (`isRouteAutoSelected == true`),
• *When* the athlete leaves the route corridor and `isOffRoute` triggers,
• *Then* the system SHALL automatically unselect the route (`routesRepository.setActiveNavigatedRoute(null)`), dismiss off-route alerts, and re-enable candidate monitoring.
• *Given* an active route chosen manually by the athlete (`isRouteAutoSelected == false`),
• *When* the athlete deviates off route and `isOffRoute` triggers,
• *Then* the route SHALL remain active and off-route alerts SHALL continue.
• *Given* an active fork decision card in the Cockpit,
• *When* the athlete taps one of the branch options,
• *Then* the selected route SHALL immediately be bound as manual (`isAutoSelected = false`).
• *Given* an active fork prompt,
• *When* the athlete taps dismiss or deviates from all candidate branches ($> 50\text{ m}$ from all routes),
• *Then* the prompt SHALL be dismissed without binding a route.

**Invariants**: Pure geodesic calculations without android framework dependencies for unit testability, 100% offline local database operation, and 100% full-suite test pass rate MUST NOT be broken.

### REQ-MAP-032: Domain-Agnostic Scrubber Marker Synchronization & Telemetry Graph Auto-Centering [Guarantee synchronized directional map scrubber marker visibility and camera auto-centering across both TIME and DISTANCE domain charts.]
- **Tests**: point.latLng.longitude != 0.0)` and the coordinate lies outside the 20% safe margin of the current viewport, the camera SHALL smoothly animate to center on `point.latLng`.
4. *Detailed Layout Integration (`MapDetailLayout.kt`)*:
• `MapDetailLayout` SHALL pass its resolved `activeScrubPoint` directly to `ATrainingTrackerMap`.
• `TelemetryMetricGraph` SHALL support `onPointSelected: (PathPoint?) -> Unit = {}`, emitting the resolved nearest `PathPoint` during horizontal scrubbing and tap gestures.
• `MapDetailLayout` SHALL update `activeScrubPoint` reactively from both `ElevationProfile` and `TelemetryMetricGraph`.
5. *100% Backward Compatibility*:
• Callers providing only `selectedDistance: Double?` (such as RouteOnMapScreen or SegmentOnMapScreen) SHALL continue to function with zero behavioral degradation.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement only (`REQ-MAP-032`) extending `REQ-MAP-006` and `REQ-UI-206` under Epic `ATT-111` (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2340`, sprint `2026-41.1`, target release `V4.9.39`.
3. *Root Reason for Existing Formulation*: `ScrubMarkerLayer` and `ScrubberController` historically performed distance-only threshold lookup (`it.distance >= targetDist`), which failed when scrubbing time-domain charts or unpopulated distance streams.
4. *Preservation of Core Invariants*: Existing distance-based callers and (0,0) trackless safety remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout with GPS track and speed telemetry displayed in `MapDetailLayout`,
• *When* the athlete touches or scrubs across the Speed/Pace graph operating in the TIME domain,
• *Then* the directional scrubber marker appears on the map at the exact GPS coordinate corresponding to the scrubbed point.
• *Given* `ElevationProfile` operating in the DISTANCE domain,
• *When* the athlete touches or scrubs across the elevation curve,
• *Then* the map marker tracks along the route with identical precision and camera auto-centering.
• *Given* a trackless workout where coordinates are `(0.0, 0.0)`,
• *When* the athlete scrubs across HR or Power graphs,
• *Then* the system updates the telemetry badge without rendering spurious markers at `(0.0, 0.0)` in the ocean.
- **Rationale**: The system SHALL guarantee that touching or dragging across any workout chart (ElevationProfile or TelemetryMetricGraph for Speed, Pace, Heart Rate, Power) displays a synchronized directional scrubber marker at the exact GPS coordinate along the route and auto-centers the map view, irrespective of whether the chart operates in the TIME or DISTANCE domain (ATT-2340):
1. *Direct PathPoint Propagation (`ATrainingTrackerMap.kt`)*:
• `ATrainingTrackerMap` SHALL accept optional parameter `activeScrubPoint: PathPoint? = null`.
• When provided, `ATrainingTrackerMap` SHALL forward `activeScrubPoint` down to both `ScrubMarkerLayer` and `ScrubberController`.
• The legacy `selectedDistance: Double? = null` parameter SHALL be preserved with 100% backward compatibility.
2. *Robust Scrubber Marker Placement (`ScrubMarkerLayer.kt`)*:
• `ScrubMarkerLayer` SHALL accept optional parameter `activeScrubPoint: PathPoint? = null`.
• The target marker point SHALL resolve via: `val point = activeScrubPoint ?: selectedDistance?.let { targetDist -> TelemetryMetricUtils.findNearestPoint(activePath, targetDist, isTimeDomain = false) ?: activePath.find { it.distance >= targetDist } }`.
• Marker rendering SHALL be guarded: if `point == null` or `point.latLng` is `(0.0, 0.0)` (e.g. trackless workouts without GPS fixes), no marker SHALL be rendered on the map.
• The directional orientation (`isWestbound`) SHALL be evaluated using the resolved point's position within `activePath`.
3. *Domain-Aware Camera Centering (`ScrubberController.kt`)*:
• `ScrubberController` SHALL accept optional parameter `activeScrubPoint: PathPoint? = null`.
• When `point != null && (point.latLng.latitude != 0.0

### REQ-MAP-033: High-Contrast Maki POI Waypoint Badges, Shelter Classification & Substring Disambiguation [Provide high-contrast circular badge markers with category-specific vibrant tinting, crisp white halo rings, outer contrast borders, and centered Maki glyphs across interactive maps and route thumbnails, introduce POI_SHELTER, and disambiguate Kreuzung false positives.]
- **Tests**: (text.contains("kreuz") && !text.contains("kreuzung"))`.
• Shelter keywords ("unterstand", "schutzhütte", "schutzhuette", "shelter", "refuge", "biwak", "bivouac") SHALL be classified as `POI_SHELTER`.
• In `fromTcx`, `"shelter"` SHALL map to `POI_SHELTER`.
3. *High-Contrast Circular Badge Marker Rendering (`MapUtils.kt`)*:
• The system SHALL provide `createWaypointBadgeMarker(context: Context, type: WaypointType, sizeDp: Int = 32): BitmapDescriptor?`.
• Geometry SHALL comprise: (a) Outer subtle dark contrast hairline / shadow ring (radius $R$, stroke 1 dp, `#33000000`); (b) Inner crisp white halo ring (radius $R - 0.5\text{ dp}$, stroke 2 dp, `#FFFFFFFF`); (c) Category-specific vibrant filled disc (radius $R - 2.5\text{ dp}$): Summit `#E65100`, Water `#0288D1`, Bench `#2E7D32`, Shelter `#00695C`, Food `#EF6C00`, Viewpoint `#6A1B9A`, Hazard `#D32F2F`, First Aid `#C62828`, Turn Cues `#1565C0`, Generic `#546E7A`; (d) Centered white vector glyph (~16–18 dp) drawn with `PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)`.
4. *Interactive Map Layer Integration (`MapLayers.kt`)*:
• `RouteWaypointsLayer` SHALL resolve icon descriptors via `createWaypointBadgeMarker(ctx, waypoint.type, 32)` above polylines ($Z \ge 50\text{f}$) and retain clickable InfoWindows displaying waypoint name, description, and altitude.
5. *Route Thumbnail & Preview Map Integration (`PathPreviewMap.kt`)*:
• In `PathPreviewMap.kt`, when `path is MapRoute && path.waypoints.isNotEmpty()`, the composable SHALL render `RouteWaypointsLayer(waypoints = path.waypoints, context = context)`.
6. *100% 9-Language Localization Parity*:
• String resource `waypoint_type_shelter` SHALL be defined across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Refines Clause 1 & Clause 5 of `REQ-MAP-026` (*Support Waypoints, POIs (Benches, Water, Summits) and TCX Course Points*).
2. *Historical Origin & Commit Trace*: Sprint `2026-40.14`, Ticket `ATT-58`, commit `7dfd4649`.
3. *Root Reason for Existing Formulation*: ATT-58 rendered bare vector drawables without background discs or contrast borders. Substring matching did not account for compound words such as "Kreuzung", and shelters lacked a dedicated category.
4. *Preservation of Core Invariants*: SQLite schema v10 cascade deletion, TCX course point handling, turn cues, and 100% full-suite test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a GPX waypoint with name "Kreuzung Waldweg",
• *When* evaluated by `WaypointType.fromGpx`,
• *Then* it SHALL NOT be classified as `POI_SUMMIT` and SHALL fall back to `GENERIC`.
• *Given* a GPX waypoint with name "Gipfelkreuz auf dem Jusi",
• *When* evaluated by `WaypointType.fromGpx`,
• *Then* it SHALL be classified as `POI_SUMMIT`.
• *Given* a GPX waypoint with name or description containing "Unterstand" or "Schutzhütte",
• *When* evaluated by `WaypointType.fromGpx`,
• *Then* it SHALL be classified as `POI_SHELTER`.
• *Given* a route with waypoints rendered in `PathPreviewMap`,
• *When* displayed in route cards or previews,
• *Then* waypoint badges SHALL render along the route polyline.
• *Given* `createWaypointBadgeMarker`,
• *When* generating badges across all `WaypointType`s,
• *Then* every marker SHALL render with outer dark stroke, white halo, category fill, and white centered icon.

**Invariants**: Database schema v10 in `Routes.db`, Google Maps Lite Mode in list items, and 100% test pass rate MUST NOT be broken.
- **Rationale**: The system SHALL provide high-contrast circular badge markers with category-specific vibrant tinting, crisp white halo rings, outer contrast borders, and centered Mapbox Maki glyphs across both interactive map views and route card thumbnails, introduce `WaypointType.POI_SHELTER`, disambiguate substring classification false positives, and ensure 100% 9-language localization parity (ATT-2461):
1. *`WaypointType.POI_SHELTER` & Maki Vector Asset (`RouteWaypoint.kt`, `ic_poi_shelter.xml`)*:
• `RouteWaypoint.kt` SHALL define `POI_SHELTER(WaypointCategory.LANDMARK, R.drawable.ic_poi_shelter, R.string.waypoint_type_shelter)`.
• The system SHALL provide `ic_poi_shelter.xml` as a clean Android Vector Drawable (24x24dp viewport, white fill) modeled after Mapbox Maki CC0 shelter icon.
• `TurnCue.fromWaypointType(type)` SHALL return `null` for `POI_SHELTER`.
• SQLite persistence in `RoutesDatabaseManager.kt` via `type.name` string serialization SHALL retain full forward and backward compatibility without database migration.
2. *GPX / TCX Heuristic Parsing & Disambiguation (`RouteWaypoint.kt`)*:
• Substring "Kreuzung" (trail intersection / junction) SHALL NOT be classified as mountain summit (`POI_SUMMIT`). Mountain summit cross detection SHALL require: `val hasKreuz = text.contains("gipfelkreuz")

### REQ-MAP-034: Designated Home-Base Selection for Return Navigation & Substring Disambiguation [`TST-MAP-036`]
- **Tests**: `KnownLocationsDatabaseManager.java`, `KnownLocationsRepository.kt`, `KnownLocationsViewModel.kt`, `HomeLocationResolver.kt`, `KnownLocationsScreen.kt`, `EditKnownLocationDialog.kt`, `strings.xml` (all 9 locales)
- **Implementation**: Provide explicit designation of a favorite location as home-base for return navigation, guarantee single-home transactional exclusivity, eliminate substring false positives, and display home visual badges in Known Locations UI.
- **Rationale**: The system SHALL provide explicit designation of a favorite location (Lieblingsort / Known Location) as the athlete's home-base for return navigation ("Take Me Home"), guarantee single-home transactional exclusivity in `StartLocation2Altitude.db`, eliminate naive substring matching false positives, display prominent visual indicators in the locations list, and maintain 100% 9-language localization parity (ATT-2472):
1. *Database Schema Upgrade v6 (`KnownLocationsDatabaseManager.java`, `KnownLocationsDbHelper`)*:
• `KnownLocationsDbHelper.DB_VERSION` SHALL be upgraded to `6`.
• Table `StartLocation2Altitude` SHALL include column `is_home integer default 0`.
• Migration for `oldVersion < 6` SHALL execute: `ALTER TABLE StartLocation2Altitude ADD COLUMN is_home integer default 0;`.
• `KnownLocationsDatabaseManager` SHALL provide `setHomeLocation(long id)` which atomically clears `is_home = 0` on all rows and sets `is_home = 1` for the specified `id` within an exclusive SQLite transaction block. If `id <= 0`, all locations are cleared.
• `KnownLocationsDatabaseManager` SHALL provide `clearHomeLocation()`.
• `KnownLocationsDatabaseManager` SHALL provide `getHomeLocation(): MyLocation?` querying `is_home = 1`.
• `MyLocation` and `cursorToMyLocation` SHALL parse and retain `isHome: Boolean`.
2. *Repository & Domain Integration (`KnownLocationsRepository.kt`, `KnownLocationItem`)*:
• `KnownLocationItem` SHALL expose `val isHome: Boolean = false`.
• `KnownLocationsRepository` SHALL provide `setHomeLocation(id: Long)` and `clearHomeLocation()`, dispatching to `KnownLocationsDB-Thread` and updating `locationsFlow`.
3. *ViewModel & Presentation Layer Integration (`KnownLocationsViewModel.kt`, `KnownLocationsScreen.kt`, `EditKnownLocationDialog.kt`)*:
• `KnownLocationsViewModel` SHALL expose `setHomeLocation(id: Long)` and `clearHomeLocation()`.
• In `KnownLocationCard`, when `item.isHome == true`, the UI SHALL render a Home icon (`Icons.Default.Home`) next to the title and a dedicated Home Base Badge (`known_locations_home_badge`) in the badges row.
• The long-press context menu of `KnownLocationCard` SHALL provide an item to set/clear home-base ("Als Heim-Basis festlegen" / "Heim-Basis aufheben").
• `EditKnownLocationDialog` SHALL provide a toggle switch to set/remove the location as Home Base.
4. *Home Location Resolution Engine (`HomeLocationResolver.kt`)*:
• `resolveHomeLocation(knownLocationsManager: KnownLocationsDatabaseManager): HomeDestination?` SHALL evaluate destinations in strict priority:
- Priority 1: The location with `isHome == true`.
- Priority 2 (Graceful fallback): The location with the highest `hitCount` (where `hitCount > 0`).
- Priority 3: The first available location if any exists.
- Substring keywords ("haus", "home", "zuhause") matching indiscriminately SHALL be eliminated, preventing "Rathaus" or "Gasthaus" false positives.
5. *100% 9-Language Localization Parity*:
• String resources `known_locations_home_base`, `known_locations_home_badge`, `known_locations_set_home`, `known_locations_remove_home` SHALL be defined across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Refines and amends Clause 1 of `REQ-MAP-029` (*"Take Me Home" Return Navigation, Remaining Distance & Elevation-Aware ETA HUD*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1953`, Sprint `2026-40.16`, commit `0994f794`.
3. *Root Reason for Existing Formulation*: In ATT-1953, the home resolver was created to support instant "Take Me Home" functionality without requiring a configuration UI upfront. Heuristic substring matching on "haus"/"home" was used as a rapid expedient.
4. *Preservation of Core Invariants*: Full backward compatibility for existing `StartLocation2Altitude.db` files via standard `onUpgrade` (v5 -> v6); graceful fallback to highest `hitCount`; return navigation HUD, reverse route travel, and 100% test pass rate remain completely intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* multiple saved locations in `KnownLocationsDatabaseManager`,
• *When* the user designates location A as Home Base,
• *Then* location A has `is_home = 1` and all other locations have `is_home = 0`.
• *Given* a designated home location A and a location B named "Rathausplatz" with higher hit count,
• *When* `HomeLocationResolver.resolveHomeLocation` is invoked,
• *Then* location A SHALL be returned as the home destination.
• *Given* no location has been explicitly designated as Home Base,
• *When* `HomeLocationResolver.resolveHomeLocation` is invoked,
• *Then* it SHALL return the location with the highest `hitCount` (where `hitCount > 0`), without matching "Rathaus" or "Gasthaus" erroneously.
• *Given* the Known Locations list,
• *When* displaying the designated home location,
• *Then* a Home icon beside the name and a dedicated Home Base Badge SHALL be visible.
• *Given* `EditKnownLocationDialog` for a location,
• *When* opened,
• *Then* a toggle switch to designate or remove Home Base status SHALL be present and functional.

**Invariants**: Single-home exclusivity, thread confinement on `KnownLocationsDB-Thread`, non-breaking database migration, and 100% test pass rate MUST NOT be broken.

### REQ-MAP-036: Known Locations: Heim-Basis Redundant Badge Excision & Start-Count Home Resolution Alignment [`TST-MAP-038`]
- **Tests**: `HomeLocationResolver.kt`, `KnownLocationsRepository.kt`, `KnownLocationsScreen.kt`
- **Implementation**: Excise redundant Heim-Basis badge chip from KnownLocationCard, unify home location resolution across domain repositories and navigation engines, and default to location with most starts when no home-base set.
- **Rationale**: The system SHALL excise the redundant "Heim-Basis" badge chip from `KnownLocationCard.kt`, unify home location resolution across domain repositories and navigation engines, and automatically display the Home icon on the effective home-base when no explicit designation exists (ATT-2629):
1. *Unified Home Location Resolution Engine (`HomeLocationResolver.kt`)*:
• `HomeLocationResolver` SHALL expose `fun resolveHomeLocationId(locations: List<MyLocation>): Long?`.
• It SHALL evaluate candidate locations according to a strict 3-tier priority hierarchy:
- Tier 1 (Explicit User Designation): If any location has `isHome == true`, return its `id`.
- Tier 2 (Workout Start Frequency Fallback): If no explicit designation exists, return the `id` of the location with the maximum `hitCount` among locations where `hitCount > 0`. If multiple locations share the identical maximum `hitCount > 0`, the first one encountered SHALL be returned.
- Tier 3 (First Location Fallback): If no location has `hitCount > 0`, return the `id` of the first location in the list.
- If the list is empty, return `null`.
• `HomeLocationResolver.resolveHomeLocation(knownLocationsManager: KnownLocationsDatabaseManager): HomeDestination?` SHALL delegate its candidate selection to `resolveHomeLocationId(rawLocations)`.
2. *Repository & Domain Model State Alignment (`KnownLocationsRepository.kt`, `KnownLocationItem`)*:
• In `KnownLocationsRepository.loadLocations()`, the system SHALL query `rawLocations = databaseManager.allLocations`.
• It SHALL evaluate `effectiveHomeId = HomeLocationResolver.resolveHomeLocationId(rawLocations)`.
• When mapping `rawLocations` to `KnownLocationItem`, `KnownLocationItem.isHome` SHALL evaluate to `(loc.id == effectiveHomeId)`.
• This ensures `locationsFlow` emits items where exactly one location has `isHome == true` whenever saved locations exist, perfectly matching the return navigation destination.
3. *Redundant Badge Excision in Presentation Layer (`KnownLocationsScreen.kt`)*:
• In `KnownLocationsScreen.kt` (`KnownLocationCard`), the redundant `Surface` badge chip displaying `R.string.known_locations_home_badge` with test tag `location_home_badge_${item.id}` inside the metadata `FlowRow` SHALL be completely excised.
• The primary title row Home icon (`Icons.Default.Home`) with test tag `location_home_icon_${item.id}`, tinted `MaterialTheme.colorScheme.primary`, and accessibility description `@string/known_locations_home_base` SHALL remain the single, elegant, and prominent indicator of home-base identity.
4. *Explicit User Designation & Transactional Exclusivity Preservation*:
• Setting a home location via `KnownLocationsViewModel.setHomeLocation(id)` or `EditKnownLocationDialog` SHALL atomically mark that location as `is_home = 1` in SQLite while clearing all other rows to `is_home = 0`.
• Clearing the home location via `clearHomeLocation()` SHALL reset all rows in SQLite to `is_home = 0`. Upon the subsequent repository refresh, the location with the highest start count (`hitCount > 0`, or first) SHALL automatically resume acting as the effective home-base, updating the UI reactively.
5. *100% 9-Language Localization & String Resource Integrity*:
• String resource `known_locations_home_base` SHALL be preserved for the Home icon's accessibility description across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
• String resource `known_locations_home_badge` MAY be retained to prevent breaking legacy references or translations, but MUST NOT be rendered in `KnownLocationCard`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines Clause 3 of `REQ-MAP-034` (*Designated Home-Base Selection for Return Navigation & Substring Disambiguation*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2472`, Sprint `2026-41.1`, commit `0994f794`.
3. *Root Reason for Existing Formulation*: In ATT-2472, both the title icon and a dedicated metadata badge chip were introduced to ensure maximum visual discoverability when home-base designation was first launched. At the time, `KnownLocationsRepository` mapped `isHome = loc.isHome` directly from the raw database query, omitting the fallback logic in `HomeLocationResolver` and leaving the UI without an active indicator when no explicit home was designated.
4. *Preservation of Core Invariants*: Single-home transactional exclusivity in SQLite (`StartLocation2Altitude.db`), explicit user designation strictly overriding start-count heuristics, context menu and edit dialog actions (`setHomeLocation`, `clearHomeLocation`), return navigation routing integrity, thread safety on `KnownLocationsDB-Thread`, and 100% full-suite clean-room unit test pass rate MUST NOT be broken.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a location item with `isHome == true`,
• *When* `KnownLocationCard` renders,
• *Then* the Home icon beside the title SHALL be displayed, and NO "Heim-Basis" badge chip SHALL be rendered in the metadata `FlowRow`.
• *Given* saved locations where Location A has `isHome = true` (hitCount = 2) and Location B has `isHome = false` (hitCount = 50),
• *When* `KnownLocationsRepository.loadLocations()` or `HomeLocationResolver.resolveHomeLocationId()` evaluates,
• *Then* Location A SHALL be resolved as the home location (`effectiveHomeId == Location A.id`).
• *Given* saved locations where all locations have `isHome = false`, Location A has `hitCount = 10`, and Location B has `hitCount = 25`,
• *When* `KnownLocationsRepository.loadLocations()` executes,
• *Then* Location B SHALL have `item.isHome == true` and Location A SHALL have `item.isHome == false`.
• *Given* saved locations where all locations have `isHome = false` and all `hitCount == 0`,
• *When* `KnownLocationsRepository.loadLocations()` executes,
• *Then* the first location in the list SHALL have `item.isHome == true`.
• *Given* an empty list of saved locations,
• *When* `HomeLocationResolver.resolveHomeLocationId(emptyList())` is called,
• *Then* it SHALL safely return `null` without throwing an exception.
• *Given* Location B resolved as default home due to highest start count,
• *When* the athlete explicitly designates Location A as home,
• *Then* Location A becomes `isHome == true`.
• *When* the athlete subsequently clears explicit home designation,
• *Then* Location B automatically resumes `isHome == true`.

**Invariants**: SQLite schema version 6 and column `is_home integer default 0` in `StartLocation2Altitude.db`, return navigation math, and 100% full-suite unit test pass rate MUST NOT be broken.

### REQ-MAP-038: In-Ride Fork Route Detection Performance Optimization: Spatial Bounding-Box Rejection, Sport-Type Pre-Filtering, and Location Evaluation Throttling [`TST-MAP-040`]
- **Tests**: `ForkRouteMatcher.kt`, `ForkNavigationRepository.kt`
- **Implementation**: In-ride fork route detection performance optimization via bounding-box rejection, sport filtering, and GPS throttling.
- **Rationale**: The system SHALL optimize in-ride fork-in-the-road route detection performance in `ForkRouteMatcher.kt` and `ForkNavigationRepository.kt` by eliminating brute-force polyline projections for geographically distant routes, filtering by workout sport profile, and throttling location candidate evaluation during quiescent tracking (ATT-2873, amending `REQ-MAP-031`):
1. *Spatial Bounding-Box Pre-Filtering ($O(1)$ Spatial Rejection)*:
• In `ForkRouteMatcher.findCandidateRoutes()`, the engine SHALL evaluate an initial spatial bounding-box check before executing `projectOntoPolyline()`.
• `BOUNDING_BOX_CORRIDOR_MARGIN_METERS = 100.0`.
• The route's latitude/longitude bounding box SHALL be determined from `route.summary.minLat/maxLat/minLng/maxLng` (or computed from `route.path` if null).
• If `currentPos` lies outside `[minLat - deltaLat, maxLat + deltaLat]` or `[minLng - deltaLng, maxLng + deltaLng]`, the route SHALL be immediately discarded in $O(1)$, bypassing all polyline segment iterations and trigonometric calculations.
2. *Sport-Type Pre-Filtering*:
• `ForkRouteMatcher.findCandidateRoutes()` SHALL accept an optional `activeSportType: BSportType? = null` parameter.
• If `activeSportType` is specified, candidate routes SHALL be pre-filtered using `RouteProximityRanker.matchesSport(route.summary.bSportType, activeSportType)`. Incompatible sport routes (e.g. running routes during a cycling workout) SHALL be discarded before geometric projection.
3. *Location Evaluation Throttling during Quiescent Tracking*:
• In `ForkNavigationRepository`, when no fork decision alert is active (`_forkDecisionState.value == null`), candidate route searches SHALL be throttled:
- Minimum time interval: `QUIESCENT_THROTTLE_INTERVAL_MS = 3000L` (3 seconds).
- Minimum displacement threshold: `QUIESCENT_THROTTLE_DISTANCE_METERS = 20.0` (20 meters).
- If both elapsed time $< 3000\text{ms}$ and distance delta $< 20\text{m}$, candidate route search SHALL be skipped.
• *Active Alert Responsiveness Preservation*: When a fork decision alert is active (`_forkDecisionState.value != null`), candidate evaluation SHALL execute on every location update (1 Hz) to ensure real-time remaining distance countdown and immediate autonomous route binding.
4. *Preservation of System Invariants*:
• Divergence alert accuracy ($D_{\text{fork}} \le 300\text{ m}$), auto-binding distance ($\ge 50\text{ m}$ past fork), corridor tolerance ($50.0\text{ m}$), and 100% test pass rate across the full test suite MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines and amends `REQ-MAP-031` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts*, Clauses 1 & 4).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1955` (Sprint 2026-40.16).
3. *Root Reason for Existing Formulation*: Originally authored as an MVP focusing on core geometric calculations without spatial partitioning or rate limiting.
4. *Preservation of Core Invariants*: Bounding-box margin ($\ge 100\text{m}$) strictly envelopes the corridor tolerance ($50.0\text{m}$), mathematically guaranteeing zero false negatives. Auto-binding latency and 100% unit test pass rate remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete tracking a workout at `currentPos`,
• *When* evaluating routes whose bounding box is $> 100\text{m}$ away,
• *Then* `ForkRouteMatcher.findCandidateRoutes()` SHALL reject those routes in $O(1)$ without computing segment projections.
• *Given* an active cycling workout (`activeSportType = BSportType.BIKE`),
• *When* candidate routes are evaluated,
• *Then* running routes (`BSportType.RUN`) SHALL be discarded immediately.
• *Given* tracking without an active fork prompt,
• *When* location updates arrive within $< 3000\text{ms}$ and $< 20\text{m}$ displacement,
• *Then* `ForkNavigationRepository` SHALL skip candidate route search.
• *Given* an active fork decision alert ($D_{\text{fork}} \le 300\text{m}$),
• *When* location updates arrive,
• *Then* `ForkNavigationRepository` SHALL evaluate on every fix (1 Hz) without throttling.

**Invariants**: Auto-binding latency, alert accuracy, corridor tolerance, and 100% unit test pass rate MUST NOT be broken.

### REQ-MAP-039: Strict "Take Me Home" Return Navigation Activation Decoupling, Tab Navigation Hints Gating, and Non-Displacing Cockpit Overlay Integration [TST-MAP-041]
- **Tests**: ReturnNavigationRepository.kt, SensorGridScreen.kt
- **Implementation**: Strict Take Me Home activation decoupling, tab navigation hints gating, and non-displacing cockpit overlay placement.
- **Rationale**: The system SHALL strictly decouple "Take Me Home" return navigation from ordinary route navigation in `ReturnNavigationRepository.kt`, conditionally gate `ReturnNavigationHud` behind `state.showNavigationHints` in `SensorGridScreen.kt`, host the HUD as a top-level non-displacing overlay anchored at `Alignment.TopCenter`, and enforce clean lifecycle state reset invariants (ATT-2938, amending `REQ-MAP-029` and `REQ-UI-282`):
1. *Strict "Take Me Home" Activation Mode (`ReturnNavigationRepository.kt`)*:
• `ReturnNavigationRepository.recalculateNavigationMetrics` SHALL evaluate `isActive = isTakeMeHomeMode`.
• Following or selecting a regular navigation route (`currentActiveRoute != null`) without explicitly activating "Take Me Home" SHALL NOT activate return navigation metrics (`isActive = false`, `hasRemainingMetrics = false`).
• When `isTakeMeHomeMode == false`, `recalculateNavigationMetrics` SHALL immediately emit `ReturnNavigationState(isActive = false)` and bypass corridor snapping.
2. *Non-Hijacking Destination Resolution (`ReturnCorridorSnapper.kt` / `ReturnNavigationRepository.kt`)*:
• `cachedHomeDestination` SHALL only be supplied to corridor snapping when `isTakeMeHomeMode == true`.
• When following a regular route without "Take Me Home", the route summary title SHALL NOT be overridden with the Home destination name ("Zu Hause").
3. *Lifecycle State Reset Invariants*:
• When return navigation is stopped (`stopTakeMeHome()`), when the active route is deselected/cleared (`activeNavigatedRouteId == null`), or when a workout session terminates, `isTakeMeHomeMode` and `isReverseReturn` SHALL reset to `false`, and `_navigationState` SHALL transition to `ReturnNavigationState(isActive = false)`.
4. *Per-Tab Navigation Hints Gating & Non-Displacing Top-Level Overlay (`SensorGridScreen.kt`)*:
• `ReturnNavigationHud` SHALL be moved out of the in-flow base cockpit `Column`.
• `ReturnNavigationHud` SHALL be hosted within a top-level non-displacing overlay anchored at `Alignment.TopCenter`, strictly gated by `if (state.showNavigationHints)`.
• On tracking tabs where `state.showNavigationHints == false`, `ReturnNavigationHud` SHALL be completely excluded from the Compose composition tree, ensuring 0% touch interception and complete suppression of return banners.
• The appearance, animation, or dismissal of `ReturnNavigationHud` SHALL NOT alter the vertical position or scroll coordinates of the underlying sensor grid tiles.
5. *Preservation of System Invariants*:
• Mid-ride "Take Me Home" entry in `RouteSelectorSheet.kt` (`REQ-UI-282`), reverse route return, elevation-aware dynamic ETA calculations, and 100% test pass rate across the full test suite MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-MAP-029` Clause 4 (*Cockpit HUD Integration*) and interfaces with `REQ-UI-282` Clause 2 (*Harmonized ReturnNavigationHud*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1953`, Sprint `2026-40.16` (commit `c8ac2c5e` / `0994f794`) and Ticket `ATT-2462`, Sprint `2026-41.1` (commit `6d0a5d24`).
3. *Root Reason for Existing Formulation*: In ATT-1953, Clause 4 originally stated: "when Return Navigation is active or an active route has remaining progress, render a high-visibility HUD banner". The intent was to show remaining distance/climb for any loaded route. However, because round-trip routes end near home, this caused `snapActiveRoute` to label the destination as "Zu Hause" and display the Home icon, misleading athletes into thinking "Take Me Home" was unexpectedly active. Furthermore, in-flow layout placement in `SensorGridScreen` pushed cockpit tiles down, creating unwanted visual displacement.
4. *Preservation of Core Invariants*: Explicit "Take Me Home" activation (`startTakeMeHome()`), reverse route return (`setReverseReturn()`), elevation-aware dynamic ETA calculations, and 100% test pass rate across the full test suite MUST be strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete navigating a regular route that terminates near home,
• *When* viewing the tracking screen without having activated "Take Me Home",
• *Then* `ReturnNavigationRepository.navigationState.value.isActive` SHALL be `false`,
• *And* `ReturnNavigationHud` SHALL NOT be displayed.
• *Given* an active workout tracking session with or without a route,
• *When* the athlete activates "Take Me Home" (`isTakeMeHomeMode = true`),
• *Then* `ReturnNavigationHud` SHALL display destination name, remaining distance, climb, and dynamic ETA.
• *Given* active "Take Me Home" navigation,
• *When* viewing a tracking tab with `showNavigationHints = false`,
• *Then* `ReturnNavigationHud` SHALL be completely excluded from the composition tree.
• *When* viewing a tracking tab with `showNavigationHints = true`,
• *Then* `ReturnNavigationHud` SHALL be visible.
• *Given* `SensorGridScreen`,
• *When* `ReturnNavigationHud` appears, expands, or dismisses,
• *Then* underlying sensor tiles SHALL remain 100% stationary without any layout shift.
• *Given* an active "Take Me Home" navigation session,
• *When* the athlete stops return navigation or deselects the active route,
• *Then* `isTakeMeHomeMode` SHALL reset to `false`,
• *And* `_navigationState` SHALL immediately transition to inactive default (`ReturnNavigationState(isActive = false)`).

**Invariants**: Explicit "Take Me Home" activation, reverse route return, elevation-aware ETA calculations, and 100% test pass rate MUST NOT be broken.

### REQ-MAP-040: High-Glanceability Route Polyline Scaling and Solid Line Rendering for Actively Navigated Route [`TST-MAP-042`]
- **Tests**: `MapModels.kt`, `MapVisualization.kt`, `MapFragmentWithTrackViewModel.kt`, `RouteDetailSheet.kt`
- **Implementation**: High-glanceability route polyline scaling (26f) and solid ribbon rendering for actively navigated route, preserving 10f dashed baseline for active background routes and propagating through MapFragmentWithTrackViewModel.
- **Rationale**: The map visualization system SHALL render the actively navigated route (the single selected route followed by the athlete) with an increased polyline stroke width of `26f` and a clean, solid ribbon rendering (`pattern = null`), ensuring effortless glanceability under bright outdoor sunlight and at bicycle handlebar distance. Furthermore, all other active/background visible routes SHALL NOT be altered and SHALL retain their baseline width of `10f` and standard dashed pattern (`Dash(15f)`, `Gap(15f)`), while unselected routes maintain a width of `6f` (ATT-2940, amending `REQ-MAP-023` and `REQ-UI-306`):
1. *Actively Navigated Route Scaling (`MapModels.kt`)*:
• When `isActiveNavigation == true`, `MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH` and `MapStyle.routeActiveNavigationWidth` SHALL be `26f`.
• In `MapRoute`, `pattern` SHALL evaluate to `null` when `isActiveNavigation == true`, causing `XRayPolyline` in `MapLayers.kt` to omit secondary dashed overlay lines and render a clean, solid high-contrast ribbon.
2. *Active Non-Navigated Route Baseline Preservation (`MapModels.kt`)*:
• When `isActiveNavigation == false && isSelected == true`, `MapVisualization.ROUTE_WIDTH` and `MapStyle.routeWidth` SHALL remain `10f`.
• In `MapRoute`, `pattern` SHALL return `listOf(Dash(ROUTE_DASH_LENGTH), Gap(ROUTE_GAP_LENGTH))` when `isActiveNavigation == false`.
3. *Unselected Background Route Width Preservation*:
• When `isSelected == false && isActiveNavigation == false`, `MapVisualization.ROUTE_UNSELECTED_WIDTH` SHALL remain `6f`.
• `pattern` SHALL return `listOf(Dash(ROUTE_DASH_LENGTH), Gap(ROUTE_GAP_LENGTH))`.
4. *Directional Chevrons & Layering Invariants*:
• Forward travel chevrons (`ActiveRouteDecorations`) SHALL remain active on top of the solid polyline.
• Stacking order `ROUTE_BASE_Z_INDEX (20f) < ROUTE_ACTIVE_BASE_Z_INDEX (24f) < CLIMB_Z_INDEX (28f) < SEGMENT_Z_INDEX (30f) < USER_LOCATION_Z_INDEX (100f)` SHALL be strictly preserved.
5. *ViewModel Data Flow & Inspection Screen Propagation*:
• `MapFragmentWithTrackViewModel` SHALL observe `routesRepository.activeNavigatedRouteId` and pass `isActiveNavigation = (it.summary.id == activeNavigatedRouteId)` when mapping `allRoutes` into `uiState.routes`.
• `RouteDetailSheet` and `RoutesScreen` SHALL pass `route = selectedRoute.toMapRoute(isActiveNavigation = true)` to `RouteOnMapScreen` to ensure prominent inspection of the focused route.
6. *Preservation of System Invariants*:
• Clean-room unit test suite pass rate MUST remain 100%.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-MAP-023` (*Prominent High-Contrast Rendering for Actively Navigated Routes*, ATT-1841) and `REQ-UI-306` (*Royal Blue Route Palette Transition*, ATT-2761).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1841` (commit `f8194a2b`) established `isActiveNavigation`, `ROUTE_ACTIVE_NAVIGATION_WIDTH = 16f`, and the dual-layer dashed overlay. Ticket `ATT-2761` (commit `e4871ad2`) shifted colors to Royal Blue while preserving the 16f width and dashed pattern. Ticket `ATT-2940` in Sprint 2026-41.6 initially over-applied 18f and null pattern to all active routes, which was rejected in review.
3. *Root Reason for Existing Formulation*: The user specifically requested highlighting only the route the user wants to follow ("the selected one"), keeping active non-navigated routes untouched at their original 10f dashed baseline.
4. *Preservation of Core Invariants*: Z-index stacking order remains preserved, directional chevrons remain rendered along active routes, and Strava Live Segment orange dashed styling and Climb Category colors remain completely intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active route navigation session with `isActiveNavigation = true`,
• *When* the route polyline visual properties are queried from `MapRoute`,
• *Then* the polyline width SHALL be exactly `26f` (`MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH`),
• *And* the polyline pattern SHALL be `null` to eliminate dashed overlay clutter and render a clean, solid ribbon.
• *Given* a route selected on the map (`isSelected = true`, `isActiveNavigation = false`),
• *When* the route polyline visual properties are queried from `MapRoute`,
• *Then* the polyline width SHALL be exactly `10f` (`MapVisualization.ROUTE_WIDTH`),
• *And* the polyline pattern SHALL be non-null containing `Dash(15f)` and `Gap(15f)`.
• *Given* an unselected route (`isSelected = false`, `isActiveNavigation = false`),
• *When* the unselected route polyline visual properties are queried from `MapRoute`,
• *Then* the polyline width SHALL remain `6f` (`MapVisualization.ROUTE_UNSELECTED_WIDTH`),
• *And* the polyline pattern SHALL be non-null containing `Dash(15f)` and `Gap(15f)`.
• *Given* `MapFragmentWithTrackViewModel.uiState`,
• *When* an active navigated route ID is set in `routesRepository`,
• *Then* the matching route in `uiState.routes` SHALL have `isActiveNavigation == true`.
• *Given* `RouteDetailSheet` or `RoutesScreen`,
• *When* rendering `RouteOnMapScreen`,
• *Then* the route SHALL be composed with `isActiveNavigation == true`.

**Invariants**: Z-index stacking order, directional chevrons, and 100% test pass rate MUST NOT be broken.

### REQ-MAP-041: Restricting In-Ride Fork Route Candidate Matching to Active Selected Routes & Quiescent Short-Circuit [`TST-MAP-043`]
- **Tests**: `ForkNavigationRepository.kt`, `ForkRouteMatcher.kt`
- **Implementation**: Restrict in-ride fork route candidate matching to active selected routes with O(1) quiescent short-circuit.
- **Rationale**: The system SHALL restrict in-ride fork-in-the-road route candidate matching in `ForkNavigationRepository.kt` and `ForkRouteMatcher.kt` to only evaluate routes that have been explicitly selected/activated by the athlete (`route.summary.isSelected == true`), and immediately short-circuit candidate matching in $O(1)$ when fewer than 2 active selected routes exist (ATT-2942, amending `REQ-MAP-031` and `REQ-MAP-038`):
1. *Active Selected Route Candidate Filtering (`ForkNavigationRepository.kt`)*:
• In `ForkNavigationRepository.onLocationChanged()`, candidate route matching SHALL only evaluate routes where `route.summary.isSelected == true`.
• Unselected routes in the local database (`route.summary.isSelected == false`) SHALL be completely ignored, preventing spurious fork decision alerts from unrelated historical routes sharing the departure corridor.
2. *O(1) Quiescent Short-Circuit for Insufficient Candidates*:
• If fewer than 2 active/selected routes exist in `routesRepository.allRoutes` (`selectedRoutes.size < 2`), `ForkNavigationRepository` SHALL immediately short-circuit in $O(1)$ without evaluating candidate search throttling, spatial bounding-box checks, or polyline projections.
• If a fork decision prompt is currently active (`_forkDecisionState.value != null`) when selected routes drop below 2 (e.g. route unselected mid-ride), the alert state SHALL immediately be cleared (`_forkDecisionState.value = null`).
3. *Parameterized Selection Enforcement in `ForkRouteMatcher.kt`*:
• `ForkRouteMatcher.findCandidateRoutes()` SHALL support parameter `requireSelected: Boolean = false`.
• When `requireSelected == true`, routes with `!route.summary.isSelected` SHALL be discarded in $O(1)$ before segment projections.
4. *Preservation of System Invariants*:
• Quiescent tracking throttling (`QUIESCENT_THROTTLE_INTERVAL_MS = 3000L`, `QUIESCENT_THROTTLE_DISTANCE_METERS = 20.0`), unthrottled 1 Hz active alert responsiveness (`REQ-MAP-038`), autonomous route snapping ($\\ge 50\\text{ m}$ past divergence), 1-tap manual route binding, and 100% test pass rate across the full test suite MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-MAP-031` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts*, Clauses 1 & 4) and `REQ-MAP-038` (*In-Ride Fork Route Detection Performance Optimization*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1955` (Sprint 2026-40.16) and Ticket `ATT-2873` (Sprint 2026-41.5).
3. *Root Reason for Existing Formulation*: In ATT-1955 and ATT-2873, `ForkNavigationRepository` scanned `allRoutes` under the assumption of small test libraries. In real-world usage, scanning hundreds of unselected routes triggered spurious fork alerts for old routes sharing initial road sections and consumed unnecessary CPU cycles.
4. *Preservation of Core Invariants*: Bounding-box pre-filtering, sport filtering, auto-binding, and 100% full-suite unit test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout tracking session with 0 or 1 route selected in the route database,
• *When* the athlete travels along any road or trail,
• *Then* `ForkNavigationRepository` SHALL short-circuit candidate matching in $O(1)$ and emit zero fork alerts.
• *Given* an active workout tracking session where 2 routes (Route A and Route B) are selected, and an unselected Route C shares the departure corridor,
• *When* the athlete approaches the divergence point,
• *Then* `ForkNavigationRepository` and `ForkRouteMatcher` SHALL only evaluate Route A and Route B,
• *And* Route C SHALL NOT appear in the fork decision branches.
• *Given* an active fork decision prompt displayed in the Cockpit,
• *When* the active/selected routes drop below 2,
• *Then* the active fork alert state SHALL immediately be cleared and dismissed.

**Invariants**: Quiescent GPS throttling, autonomous snapping, 1-tap binding, and 100% unit test pass rate MUST NOT be broken.

### REQ-MAP-042: Calibratable Speed-Dependent Map Zoom Curve, Follow-Me Forward Lookahead Camera Padding & Pitch Tuning [`TST-MAP-044`]
- **Tests**: `MapBehaviors.kt`, `ATrainingTrackerMap.kt`, `TuningPreferencesDataStore.kt`, `NavigationSection.kt`, `AdvancedTuningDialog.kt`
- **Implementation**: Calibrate speed-dependent map zoom curve, apply bottom lookahead padding, and add camera tuning parameters to Advanced Settings.
- **Rationale**: The system SHALL calibrate the speed-dependent map zoom curve in Follow-Me tracking navigation (`MapZoomFocus.FOLLOW_ME`), apply bottom camera content padding to anchor the rider in the lower third for forward road lookahead, and integrate user-configurable camera tuning parameters into `TuningConfig` and `AdvancedTuningDialog` (ATT-2946, amending `REQ-MAP-004`):
1. *Speed Unit Normalization & Linear Speed-Zoom Curve (`MapBehaviors.kt`)*:
• In `followMeController`, sensor telemetry speed supplied in $\text{m/s}$ SHALL be converted to $\text{km/h}$ via $\text{speedKmh} = \text{speedMps} \times 3.6\text{f}$.
• When `speedZoomEnabled` is true, the target camera zoom $Z_{\text{target}}$ SHALL scale linearly between base zoom $Z_{\text{base}}$ at $0\text{ km/h}$ and cruising zoom $Z_{\text{cruise}}$ at $20\text{ km/h}$: $\text{zoomSlope} = \frac{Z_{\text{base}} - Z_{\text{cruise}}}{20.0\text{f}}$, $Z_{\text{target}} = (Z_{\text{base}} - \text{zoomSlope} \times \text{speedKmh}).\text{coerceIn}(12.0\text{f}, 21.0\text{f})$.
• When `speedZoomEnabled` is false, $Z_{\text{target}} = Z_{\text{base}}.\text{coerceIn}(12.0\text{f}, 21.0\text{f})$, locking the camera firmly at the selected base zoom regardless of velocity.
2. *Forward Lookahead Bottom Content Padding (`ATrainingTrackerMap.kt`)*:
• In `ATrainingTrackerMap.kt`, the root map canvas SHALL be hosted inside `BoxWithConstraints`.
• When `zoomFocus == MapZoomFocus.FOLLOW_ME`, `GoogleMap` SHALL apply bottom content padding: $\text{paddingBottom} = \text{maxHeight} \times \frac{\text{mapFollowMeLookaheadPaddingPercent}}{100\text{f}}$, $\text{contentPadding} = \text{PaddingValues}(\text{bottom} = \text{paddingBottom})$, visually anchoring the rider in the lower third ($\approx 25\% - 35\%$ from bottom) and dedicating $\approx 65\% - 75\%$ of the viewport to the forward route corridor.
• When `zoomFocus != MapZoomFocus.FOLLOW_ME` (e.g. `EXPLICIT_BOUNDS`, `FIT_ALL`, `MANUAL`), bottom padding SHALL resolve to `0.dp` (`PaddingValues(0.dp)`).
3. *Calibratable 3D Camera Tilt Perspective*:
• `followMeController` SHALL apply `tilt(mapFollowMeTiltAngle.coerceIn(0f, 70f))` to the `CameraPosition.builder()`.
4. *DataStore & Advanced Tuning Integration (`TuningPreferencesDataStore.kt`, `TuningConfig.kt`)*:
• `TuningConfig` SHALL expose 5 new immutable properties: `mapFollowMeInitialZoom: Float = 20.0f` (range: $15.0\text{f} .. 21.0\text{f}$, step $0.5\text{f}$); `mapFollowMeSpeedZoomEnabled: Boolean = true`; `mapFollowMeCruisingZoom: Float = 18.0f` (range: $14.0\text{f} .. 19.5\text{f}$, step $0.5\text{f}$); `mapFollowMeTiltAngle: Float = 70.0f` (range: $0.0\text{f} .. 70.0\text{f}$, step $5.0\text{f}$); `mapFollowMeLookaheadPaddingPercent: Float = 30.0f` (range: $10.0\text{f} .. 50.0\text{f}$, step $5.0\text{f}$).
• All 5 preference keys registered in `ALL_KEYS` for atomic factory reset in `resetToDefaults()`.
5. *UI Settings Integration (`NavigationSection.kt`, `AdvancedTuningDialog.kt`)*:
• `NavigationSection.kt` SHALL render interactive controls for base zoom, speed-zoom toggle, cruising zoom, tilt angle, and forward lookahead padding.
• `TuningResetDefaultsButton` in `AdvancedTuningDialog.kt` resets all 5 parameters to factory defaults.
6. *9-Language Localization Parity*:
• String resources for all labels, descriptions, and value formatters defined across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero missing entries.
7. *Preservation of System Invariants*:
• Low-pass filtered bearing stabilization (`REQ-MAP-003`), smooth camera animation via `cameraPositionState.animate(..., 400)`, manual user pan/zoom gestures and explicit route bounds framing (`REQ-MAP-022`), and 100% full clean-room unit test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-MAP-004` (*Zoom depending on speed: Wider context at speed, detail when slow*, ATT-437).
2. *Historical Origin & Commit Trace*: Initial follow-me camera prototype implementation in `followMeController`.
3. *Root Reason for Existing Formulation*: `followMeController` originally consumed `speed` directly in $\text{m/s}$, resulting in a negligible $0.56$ zoom level drop at $20\text{ km/h}$, with hardcoded $70^\circ$ tilt and centered rider anchor without lookahead padding.
4. *Preservation of Core Invariants*: Bearing smoothing, camera animation, manual zoom override, map snapshots, and Map DSL layer rendering remain 100% operational.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete riding with Follow-Me camera tracking active and default parameters ($Z_{\text{base}} = 20.0$, $Z_{\text{cruise}} = 18.0$ at $20\text{ km/h}$),
• *When* the athlete is stationary ($0\text{ km/h}$),
• *Then* the camera zoom is $20.0\text{f}$.
• *When* the athlete accelerates to $20\text{ km/h}$ ($5.56\text{ m/s}$),
• *Then* the camera zoom scales smoothly to $18.0\text{f}$.
• *Given* an athlete sets `mapFollowMeSpeedZoomEnabled = false` and `mapFollowMeInitialZoom = 19.0f`,
• *When* riding at varying speeds,
• *Then* the camera zoom remains locked at $19.0\text{f}$.
• *Given* `ATrainingTrackerMap` in `FOLLOW_ME` mode on a $400\text{dp}$ tall viewport with default $30\%$ padding,
• *Then* bottom content padding is $120\text{dp}$, anchoring the rider in the lower third.
• *Given* `ATrainingTrackerMap` in `FIT_ALL` or `EXPLICIT_BOUNDS` mode,
• *Then* bottom content padding is $0\text{dp}$.
• *Given* custom camera tuning in `AdvancedTuningDialog`,
• *When* tapping `TuningResetDefaultsButton`,
• *Then* all 5 camera parameters restore to defaults ($20.0\text{f}$, `true`, $18.0\text{f}$, $70.0\text{f}$, $30.0\text{f}$).

**Invariants**: Bearing smoothing, camera animation, manual zoom override, and 100% test pass rate MUST NOT be broken.

### REQ-NAV-043: In-Ride Fork Decision Alert Direction-Diversity Gating & False-Positive Suppression [`TST-NAV-045`]
- **Tests**: `RouteDivergenceDetector.kt`
- **Implementation**: Gating in-ride fork decision alerts behind relative direction diversity to suppress false-positive alerts when all routes share the same heading.
- **Rationale**: The system SHALL gate in-ride fork decision alerts behind relative direction diversity across candidate routes to eliminate false positives at junctions where all candidate routes follow the same relative directional heading (ATT-2964, amending `REQ-NAV-028`):
1. *Direction Diversity Gating (`RouteDivergenceDetector.kt`)*:
• In `RouteDivergenceDetector.detectDivergence()`, after calculating `branchOptions` and classifying each branch's relative `ForkDirection` (`LEFT`, `STRAIGHT`, `RIGHT`), the system SHALL compute the distinct directional set: `val distinctDirections = branchOptions.map { it.direction }.distinct()`..
• If `distinctDirections.size < 2`, `detectDivergence()` SHALL immediately return `null`, suppressing the in-ride fork alert..
2. *Decision Point Activation*:
• If `distinctDirections.size >= 2` (e.g. `STRAIGHT` and `RIGHT`, `LEFT` and `STRAIGHT`, or `LEFT` and `RIGHT`), `detectDivergence()` SHALL return the populated `ForkDecisionState` within the alert window (`ALERT_WINDOW_MIN_METERS < distanceToFork <= ALERT_WINDOW_MAX_METERS`)..
3. *Preservation of System Invariants*:
• Spatial separation thresholds (`DIVERGENCE_INITIATION_THRESHOLD_METERS = 15.0m`, `DIVERGENCE_SEPARATION_THRESHOLD_METERS = 40.0m`, `LOOKAHEAD_METERS = 600.0m`), angular relative classification ranges (`[-20, 20]` for `STRAIGHT`), active route candidate matching restriction (`REQ-MAP-041` / `ATT-2942`), and 100% clean-room test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-NAV-028` (*In-Ride Fork Route Candidate Matching and Divergence Detection*, Sprint 2026-41.2, ATT-2481).
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-41.2 (`ATT-2481`).
3. *Root Reason for Existing Formulation*: The original detector relied solely on physical separation exceeding 40m without evaluating whether candidate routes actually branched in diverging directions.
4. *Preservation of Core Invariants*: Genuine multi-direction splits, spatial threshold checks, and corridor projection tolerances remain 100% functional.

**Acceptance Criteria (Given-When-Then)**:
• *Given* two or more candidate routes that physically diverge by > 40m ahead,
• *When* all candidate routes evaluate to the exact same `ForkDirection` (e.g. all `STRAIGHT`),
• *Then* `RouteDivergenceDetector.detectDivergence()` returns `null` and no fork decision prompt is shown.
• *Given* candidate routes that diverge ahead into at least two distinct `ForkDirection`s (e.g. one `STRAIGHT` and one `RIGHT`),
• *When* approaching within 300m of the divergence vertex,
• *Then* `RouteDivergenceDetector.detectDivergence()` returns a valid `ForkDecisionState` and the fork decision card is presented in the HUD.

**Invariants**: Lookahead distances, separation thresholds, angular direction classification math, and 100% test pass rate MUST NOT be broken.

### REQ-MAP-044: Forward Lookahead Camera Top Content Padding Inversion & Settings Zoom Directional Guidance [`TST-MAP-046`]
- **Tests**: `ATrainingTrackerMap.kt`, `NavigationSection.kt`, `strings.xml`
- **Implementation**: Invert map camera lookahead padding to top padding and clarify zoom level direction on camera sliders in settings.
- **Rationale**: The system SHALL correct the Google Maps camera content padding in `ATrainingTrackerMap.kt` to apply top content padding during follow-me navigation (`MapZoomFocus.FOLLOW_ME`), anchoring the athlete marker in the lower third of the display to maximize forward road visibility, and SHALL clarify the zoom level direction on camera sliders in Advanced Settings across all 9 supported application locales (ATT-3054, amending `REQ-MAP-042` and `REQ-MAP-004`):
1. *Forward Lookahead Top Content Padding (`ATrainingTrackerMap.kt`)*:
• In `ATrainingTrackerMap.kt`, the root map canvas SHALL be hosted inside `BoxWithConstraints`.
• When `zoomFocus == MapZoomFocus.FOLLOW_ME`, `GoogleMap` SHALL apply top content padding: $\text{topPadding} = \text{maxHeight} \times \frac{\text{tuningConfig.mapFollowMeLookaheadPaddingPercent}}{100\text{f}}$, $\text{contentPadding} = \text{PaddingValues}(\text{top} = \text{topPadding})$, visually shifting the camera focal point downwards into the lower third ($\approx 25\% - 35\%$ from bottom) and dedicating $\approx 65\% - 75\%$ of the viewport to the forward route corridor.
• When `zoomFocus != MapZoomFocus.FOLLOW_ME` (e.g. `EXPLICIT_BOUNDS`, `FIT_ALL`, `MANUAL`), top padding SHALL resolve to `0.dp` (`PaddingValues(0.dp)`).
• Bottom content padding SHALL be `0.dp` across all modes.
2. *Directional Clarity for Map Camera Zoom Settings (`NavigationSection.kt`, `strings.xml`)*:
• The helper text for Follow-Me Base Zoom (`tuning_map_base_zoom_desc`) and Cruising Speed Zoom (`tuning_map_cruising_zoom_desc`) SHALL explicitly clarify that a higher numerical value corresponds to a closer zoom level.
• The lookahead padding setting title (`tuning_map_lookahead_padding_title`) and helper text (`tuning_map_lookahead_padding_desc`) SHALL be updated to remove misleading "Bottom" references, standardizing on *Forward Lookahead Padding* across all 9 supported languages.
3. *9-Language Localization Parity*:
• All string tokens (`tuning_map_base_zoom_desc`, `tuning_map_cruising_zoom_desc`, `tuning_map_lookahead_padding_title`, `tuning_map_lookahead_padding_desc`) MUST exist and contain non-empty translations with 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
4. *Preservation of System Invariants*:
• Linear speed-dependent zoom scaling (`REQ-MAP-042`), low-pass filtered bearing stabilization (`REQ-MAP-003`), 3D tilt pitch angle, DataStore persistence and atomic reset (`resetToDefaults()`), explicit route bounds framing (`REQ-MAP-022`), and 100% full clean-room unit test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-MAP-042` (*Calibratable Speed-Dependent Map Zoom Curve, Follow-Me Forward Lookahead Camera Padding & Pitch Tuning*, ATT-2946) and `REQ-MAP-004` (*Zoom depending on speed*, ATT-437).
2. *Historical Origin & Commit Trace*: Introduced in commit `d8177e06` (`ATT-2946`).
3. *Root Reason for Existing Formulation*: In ATT-2946, the author mistakenly applied `bottom = bottomPadding` under the assumption that bottom padding positions the rider at the bottom. In Google Maps camera geometry, bottom padding shifts the focal center upwards, pushing the rider to the top of the screen beneath telemetry HUD overlays.
4. *Preservation of Core Invariants*: Velocity-dependent zoom scaling, camera animation duration, bearing smoothing, manual zoom override, and full-suite test pass rate remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `ATrainingTrackerMap` in `FOLLOW_ME` mode on an $800\text{dp}$ tall viewport with default $30\%$ padding,
• *When* `contentPadding` is calculated,
• *Then* top padding is $240\text{dp}$ and bottom padding is $0\text{dp}$, anchoring the rider in the lower third.
• *Given* `ATrainingTrackerMap` in `FIT_ALL` or `EXPLICIT_BOUNDS` mode,
• *When* `contentPadding` is evaluated,
• *Then* top padding is $0\text{dp}$ and bottom padding is $0\text{dp}$.
• *Given* an athlete viewing Map Camera settings in `NavigationSection`,
• *When* inspecting the helper copy for Base Zoom and Cruising Zoom,
• *Then* the copy explicitly informs the athlete that higher numerical values mean closer zoom.
• *Given* the lookahead padding slider in `NavigationSection`,
• *When* displayed on screen,
• *Then* the title reads "Forward Lookahead Padding" without contradictory "Bottom" phrasing.
• *Given* the 9 supported application locales,
• *When* running localization and unit test suites,
• *Then* 100% of string keys exist and 100% of unit tests pass with zero regressions.

**Invariants**: Linear speed-dependent zoom scaling, bearing smoothing, tilt pitch, DataStore persistence, and 100% test pass rate MUST NOT be broken.

### REQ-MAP-045: Automatic Active Workout Sport Type Synchronization on Route Selection (Bike / Run) [`TST-MAP-047`]
- **Tests**: `RouteSelectorViewModel.kt`, `TrackingTabsScreen.kt`, `BANALServiceRepository.kt`
- **Implementation**: Automatically set selected workout sport type to Bike or Run when selecting a typed route in RouteSelectorViewModel.
- **Rationale**: The system SHALL automatically synchronize the active workout sport type in `BANALServiceRepository` and `BANALService` whenever an athlete selects a route tagged as Cycling / Bike or Running, eliminating manual sport switching friction when preparing or navigating workouts (ATT-3092, amending and complementing `REQ-UI-280` and `REQ-TRK-001`):
1. *Automatic Sport Synchronization on Route Selection (`RouteSelectorViewModel.kt`)*:
• In `RouteSelectorViewModel.selectRoute(routeId: Long)`, after invoking `routesRepository.setActiveNavigatedRoute(routeId)`, the system SHALL look up the selected route by `routeId` in `routesRepository.allRoutes.value`.
• If `route.summary.bSportType == BSportType.BIKE`, `RouteSelectorViewModel` SHALL invoke `banalServiceRepository?.setUserSelectedSportType(BSportType.BIKE)`.
• If `route.summary.bSportType == BSportType.RUN`, `RouteSelectorViewModel` SHALL invoke `banalServiceRepository?.setUserSelectedSportType(BSportType.RUN)`.
• If `route.summary.bSportType == BSportType.UNKNOWN` or `BSportType.CONFLICT`, `RouteSelectorViewModel` SHALL NOT modify the active workout sport type, strictly preserving the athlete's current sport selection.
2. *Auto-Detected Candidate Route Sport Synchronization*:
• In `RouteSelectorViewModel.activateCandidate(routeId: Long)`, candidate route activation SHALL delegate to `selectRoute(routeId)`, guaranteeing identical automatic sport type synchronization when accepting an auto-detected candidate route.
3. *Decoupled Route Clearing / Stop Preservation*:
• Invoking `stopRoute()` or `clearRoute()` in `RouteSelectorViewModel` SHALL clear the active navigated route (`setActiveNavigatedRoute(null)`) but SHALL NOT alter or reset the active workout sport type.
4. *Immediate State Flow Emission (`BANALServiceRepository.kt`)*:
• In `BANALServiceRepository.setUserSelectedSportType(bSportType: BSportType)`, the repository SHALL immediately assign `_bSportType.value = bSportType` in addition to invoking `_serviceBinder.value?.setUserSelectedSportType(bSportType)`, guaranteeing instantaneous reactive UI update without 1-second background sync lag.
5. *Tracking Screen Wiring (`TrackingTabsScreen.kt`)*:
• In `TrackingTabsScreen.kt`, the `RouteSelectorViewModel` factory SHALL provide `banalServiceRepository = controlViewModel.banalServiceRepository`.
6. *Preservation of System Invariants*:
• Dynamic sensor scanning on sport changes (`startSearchWhenUserChangesSport`), proximity route ranking, radius filtering, and 100% full clean-room unit test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends and complements `REQ-UI-280` (*Streamlined Route Selector: Removal of Sort Tabs and Pinned Heimweg Card in Favor of Proximity-Recency Ranking*, ATT-2459) and `REQ-TRK-001` / `REQ-UI-259` (*Sport Type Selection in Control Tracking*).
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-41.1 (`ATT-2459`, commit `3168d18b`). Route selection originally decoupled navigation polyline binding from the workout sport state machine.
3. *Root Reason for Existing Formulation*: Originally, sport selection was manually controlled exclusively on the Control tracking tab. However, because routes contain definitive sport classification (`bSportType`), requiring manual sport switching after selecting a bike or run route created unnecessary friction and risk of wrong sport tracking.
4. *Preservation of Core Invariants*: Routes with `UNKNOWN` or `CONFLICT` sport types must never override active sport; clearing routes must never reset sport; full-suite test pass rate remains strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete is on the tracking screen with sport set to `UNKNOWN` or `RUN`,
• *When* the athlete selects a route with `bSportType == BSportType.BIKE` from `RouteSelectorSheet` or auto-detection,
• *Then* `banalServiceRepository.bSportType` automatically transitions to `BSportType.BIKE`,
• *And* `BANALService.setUserSelectedSportType(BSportType.BIKE)` is called.
• *Given* the athlete on the tracking screen with sport set to `BIKE`,
• *When* the athlete selects a route with `bSportType == BSportType.RUN`,
• *Then* `banalServiceRepository.bSportType` automatically transitions to `BSportType.RUN`.
• *Given* an active workout tracking session with a selected sport,
• *When* the athlete selects a route with `bSportType == BSportType.UNKNOWN` or `CONFLICT`,
• *Then* the active workout sport type remains completely unchanged.
• *Given* an active navigated route,
• *When* the athlete stops or clears the route (`stopRoute()`),
• *Then* the active workout sport type remains completely unchanged.

**Invariants**: Dynamic sensor search, route ranking, candidate auto-detection, and 100% test pass rate MUST NOT be broken.

### REQ-MAP-046: Unified Viewport Anchor Authority for Follow-Me Map Tracking & Analytical Forward Lookahead Calculation [`TST-MAP-048`]
- **Tests**: `MapBehaviors.kt`, `ATrainingTrackerMap.kt`
- **Implementation**: Unified viewport anchor authority and analytical lookahead camera targeting in followMeController, eliminating GoogleMap content padding conflicts and camera oscillation jitter.
- **Rationale**: The system SHALL unify camera positioning, forward lookahead offset calculation, and viewport padding under a single spatial authority in `followMeController` (`MapBehaviors.kt`), eliminating competing Google Maps layout recentering loops and camera oscillation jitter during active workout tracking (ATT-3102, amending `REQ-MAP-044` and `REQ-MAP-042` under Rule 33):
1. *Unified Viewport Anchor Authority (`MapBehaviors.kt`)*:
• The map camera controller (`followMeController`) SHALL be the single spatial authority governing camera target, bearing smoothing, speed-dependent zoom, and 3D pitch tilt in `MapZoomFocus.FOLLOW_ME` mode.
• In `followMeController`, forward lookahead SHALL be computed analytically by projecting the camera focal target coordinate ahead of the athlete along `filteredBearing` based on `tuningConfig.mapFollowMeLookaheadPaddingPercent`, viewport height, and map zoom level.
• Target projection math: $\text{offsetFraction} = (\text{lookaheadPaddingPercent} / 100\text{f}) \times 0.5\text{f}$, $\text{metersPerDp} = (156543.03392 \times \cos(\text{latRad})) / 2^{\text{targetZoom}}$, $\text{offsetMeters} = \text{viewportHeightDp} \times \text{offsetFraction} \times \text{metersPerDp}$, $\mathbf{x}_{\text{target}} = \text{SphericalUtil.computeOffset}(\text{currentLocation}, \text{offsetMeters}, \text{filteredBearing})$.
2. *Decoupling of GoogleMap Content Padding (`ATrainingTrackerMap.kt`)*:
• In `ATrainingTrackerMap.kt`, during `MapZoomFocus.FOLLOW_ME` mode, `GoogleMap` SHALL receive `contentPadding = PaddingValues(0.dp)`, completely eliminating competing layout recentering loops between Google Maps native padding and `cameraPositionState.animate(...)`.
• `BoxWithConstraints` SHALL pass `maxHeight.value` as `viewportHeightDp` and `tuningConfig.mapFollowMeLookaheadPaddingPercent` as `lookaheadPaddingPercent` to `followMeController`.
3. *Smooth Camera Animation & Zero-Jitter Invariant*:
• When a new GPS coordinate arrives, `followMeController` SHALL animate the camera to `CameraPosition.builder().target(target).bearing(filteredBearing).zoom(targetZoom).tilt(effectiveTilt).build()`.
• The camera SHALL stably anchor the athlete marker at the lower third (~35% from bottom / ~65% from top) without oscillating or jumping to the physical screen center (50% Y).
4. *Preservation of Non-Follow-Me Modes & System Invariants*:
• In non-Follow-Me modes (`EXPLICIT_BOUNDS`, `TRACK_AND_MARKERS`, `LOCAL_SEGMENTS`, `FIT_PRIMARY`), `followMeController` SHALL remain inactive and `contentPadding` SHALL remain `0.dp`.
• Speed zoom calibration ($0\text{ km/h} \to 20.0$, $20\text{ km/h} \to 18.0$), bearing smoothing ($\alpha = 0.15$), pitch angle ($0^\circ - 70^\circ$), DataStore preference persistence, and 100% full clean-room unit test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-MAP-044` (*Forward Lookahead Camera Top Content Padding Inversion*, Sprint 2026-41.7, ATT-3054) and `REQ-MAP-042` (*Calibratable Speed-Dependent Map Zoom Curve, Follow-Me Forward Lookahead Camera Padding & Pitch Tuning*, Sprint 2026-41.6, ATT-2946).
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-41.6 (`ATT-2946`, commit `d8177e06`) and modified in Sprint 2026-41.7 (`ATT-3054`, commit `8cdc39f0`).
3. *Root Reason for Existing Formulation*: ATT-3054 inverted bottom padding to top padding on `GoogleMap` to shift the camera center downward. However, `CameraPosition.builder().target(currentLocation)` in `followMeController` always centers the camera target at the unpadded physical screen center (50% Y), while Google Maps native padding shifts it downward (65% Y). This caused a continuous 1 Hz oscillation between center and lower third on every GPS update.
4. *Preservation of Core Invariants*: Stable rider positioning in lower third, speed-dependent zoom curve, tilt pitch, DataStore tuning preferences, and 100% test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout tracking session in `FOLLOW_ME` mode,
• *When* GPS telemetry updates are received at 1 Hz,
• *Then* `followMeController` animates the camera target to $\mathbf{x}_{\text{target}}$ ahead of the athlete along `filteredBearing`,
• *And* the athlete marker remains stably positioned at the lower third without oscillating to the viewport center.
• *Given* `GoogleMap` in `FOLLOW_ME` mode in `ATrainingTrackerMap.kt`,
• *When* inspecting `contentPadding`,
• *Then* `contentPadding` evaluates to `PaddingValues(0.dp)`.
• *Given* `tuningConfig.mapFollowMeLookaheadPaddingPercent` configured in Settings,
• *When* the athlete adjusts the slider,
• *Then* `followMeController` dynamically scales the forward lookahead ground offset accordingly.
• *Given* non-Follow-Me modes (`EXPLICIT_BOUNDS`, `TRACK_AND_MARKERS`),
• *When* rendered,
• *Then* `contentPadding` remains `0.dp` and `MapBoundsController` governs camera framing.

**Invariants**: Single spatial authority (Rule 33), speed zoom curve, bearing smoothing, pitch angle, DataStore settings, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-MAP-047: Continuous Linear Polyline Projection & Progress Metric Interpolation for Live Climbs [`TST-MAP-049`]
- **Tests**: `LiveClimbsRepository.kt`, `LiveClimbsRepositoryTest.kt`
- **Implementation**: Project athlete position orthogonally onto polyline segments of active climb and continuously interpolate telemetry metrics and rider pin position.
- **Rationale**: The system SHALL project the athlete's real-time GPS position orthogonally onto polyline segments of the active climb path and continuously interpolate distance, altitude, progress fraction, and instantaneous gradient to prevent UI stutter and eliminate false off-route cancellations between sparse trackpoints (ATT-3174, amending `REQ-MAP-027` and aligning with Design Guidelines §2.4):
1. *Continuous Orthogonal Segment Projection (`LiveClimbsRepository.kt`)*:
• For each consecutive polyline segment $[P_i, P_{i+1}]$ in `path` ($i = 0 \dots \text{path.size} - 2$):
- Compute the segment vector $\vec{v} = (\Delta \text{lat}, \Delta \text{lng}) = (P_{i+1}.\text{lat} - P_i.\text{lat}, P_{i+1}.\text{lng} - P_i.\text{lng})$.
- Compute segment squared Euclidean length $L^2 = \Delta \text{lat}^2 + \Delta \text{lng}^2$.
- If $L^2 > 0.0$, compute the scalar projection parameter $t = \frac{(\text{location.lat} - P_i.\text{lat})\Delta \text{lat} + (\text{location.lng} - P_i.\text{lng})\Delta \text{lng}}{L^2}$ clamped to $[0.0, 1.0]$; otherwise $t = 0.0$.
- Derive projected coordinate $P_{\text{proj}} = (\text{projLat}, \text{projLng}) = (P_i.\text{lat} + t \times \Delta \text{lat}, P_i.\text{lng} + t \times \Delta \text{lng})$.
- Measure geodesic cross-track distance between athlete $(\text{location.lat}, \text{location.lng})$ and $P_{\text{proj}}$ using `Location.distanceBetween`.
- Identify the optimal segment index $i$ and scalar $t$ that minimize cross-track distance across all segments.
2. *Cross-Track Off-Route Protection*:
• The off-route check SHALL evaluate the minimum cross-track distance to the projected polyline point against `CLIMB_OFF_ROUTE_THRESHOLD_METERS` (100.0m).
• If $\min(\text{crossTrackDist}) > \text{CLIMB\_OFF\_ROUTE\_THRESHOLD\_METERS}$, `processLocation()` SHALL return `null` (off climb).
• When riding along a straight road with sparse vertices (e.g. 180m apart), an athlete traveling on-segment SHALL NOT be falsely marked off-route.
3. *Continuous Telemetry & Progress Interpolation*:
• Using the optimal segment $[P_i, P_{i+1}]$ and scalar $t$, the system SHALL compute:
- $\text{completedDist} = ((P_i.\text{distance} - \text{path.first().distance}) + t \times (P_{i+1}.\text{distance} - P_i.\text{distance})).\text{coerceAtLeast}(0.0)$
- $\text{remainingDist} = (\text{climb.distanceMeters} - \text{completedDist}).\text{coerceAtLeast}(0.0)$
- $\text{currentAlt} = P_i.\text{altitude} + t \times (P_{i+1}.\text{altitude} - P_i.\text{altitude})$
- $\text{remainingGain} = (\text{summitAlt} - \text{currentAlt}).\text{coerceAtLeast}(0.0)$
- $\text{progressFraction} = (\text{completedDist} / \text{climb.distanceMeters}).\text{coerceIn}(0.0, 1.0).\text{toFloat}()$
- $\text{currentGradePercent} = \text{if } (P_{i+1}.\text{distance} - P_i.\text{distance} > 0.0) \frac{P_{i+1}.\text{altitude} - P_i.\text{altitude}}{P_{i+1}.\text{distance} - P_i.\text{distance}} \times 100.0 \text{ else } \text{climb.avgGradePercent}$
4. *Single-Point Geometry Fallback*:
• If `path.size < 2`, the system SHALL fall back to discrete vertex evaluation at $P_0$, using geodesic distance to $P_0$ for off-route check, $P_0.\text{altitude}$, and $\text{completedDist} = 0.0$.
5. *Preservation of System Invariants*:
• Summit gate detection (`CLIMB_SUMMIT_GATE_THRESHOLD_METERS = 35.0m`), start gate detection (`CLIMB_START_GATE_THRESHOLD_METERS = 30.0m`), approach detection (`CLIMB_APPROACH_THRESHOLD_METERS = 250.0m`), approach bearing tolerance ($\le 60^\circ$), and 100% full clean-room unit test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-MAP-027` (*ClimbPro Real-Time Live Climb Cockpit Sheet with Dynamically Colored Gradient Profile & Elevation Pacing*, Sprint 2026-40.14, ATT-1281) and aligns with `docs/design_guidelines.md` §2.4.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.14 (`ATT-1281`). The initial engine implementation adopted nearest-neighbor discrete vertex lookup for simplicity and minimum CPU overhead. Refined in Sprint 2026-41.7 (`ATT-2947`, `REQ-UI-328`) and Sprint 2026-41.8 (`ATT-3103`, `REQ-UI-341`), focusing on UI layout, typography, and `@Preview` tooling.
3. *Root Reason for Existing Formulation*: Nearest-neighbor discrete vertex snapping was implemented as a minimum viable progress tracking solution. Field testing on high-resolution displays (Google Pixel 10) demonstrated that on sparse tracks (50m–200m trackpoint spacing), telemetry metrics (remaining distance, elevation gain, current gradient) and the elevation profile rider indicator pin freeze and jump discontinuously, giving the impression of GPS lag or frozen UI. Furthermore, computing distance against discrete vertices rather than polyline segments left riders vulnerable to false 100m off-route cancellations between distant vertices.
4. *Preservation of Core Invariants*: Summit gate threshold (35m), start gate threshold (30m), approach bearing tolerance ($\le 60^\circ$), off-route threshold (100m cross-track), and full clean-room unit test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active live climb with sparse trackpoints 500m apart (vertex 0 at 0m, vertex 1 at 500m),
• *When* the athlete is located at the 250m midpoint (50% progress),
• *Then* `completedDist` evaluates to 250.0m (not 0.0m or 500.0m), `progressFraction` evaluates to 0.5f, and `currentAlt` is interpolated smoothly between base and summit.
• *Given* an athlete traveling along a road segment between two vertices spaced 180m apart,
• *When* the athlete is at the 90m midpoint 10m perpendicular to the segment (cross-track distance 10m, vertex distance 90.5m),
• *Then* `processLocation()` evaluates cross-track distance as 10m and retains the climb as active (does not trigger false off-route cancellation).
• *Given* an athlete who deviates > 100m perpendicular to all polyline segments of the climb,
• *When* `processLocation()` is called,
• *Then* `processLocation()` returns `null`, cleanly dismissing the off-route climb.
• *Given* a single-point climb path (`path.size == 1`),
• *When* `processLocation()` is evaluated,
• *Then* the engine falls back safely to $P_0$ without throwing an `IndexOutOfBoundsException`.

**Invariants**: Gate thresholds, approach bearing checks, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-MAP-048: Follow-Me Camera Target Forward Lookahead Projection Scaling, Tilt Foreshortening Compensation & Settings Scale Direction Transparency [`TST-MAP-050`]
- **Tests**: `MapBehaviors.kt`, `strings.xml` (all 9 locales)
- **Implementation**: Follow-Me camera target forward lookahead projection scaling, tilt foreshortening compensation and settings scale direction transparency.
- **Rationale**: The system SHALL scale and project the forward lookahead camera target coordinate in `calculateFollowMeCameraTarget` (`MapBehaviors.kt`) such that adjusting the "Vorausschau-Kameraabstand" (*Forward Lookahead Padding*) slider produces a proportional, visually distinct shift of the athlete marker between screen center and lower viewport across all camera tilt angles, compensating for 3D perspective tilt foreshortening and providing explanatory scale direction in user settings (ATT-3171, amending `REQ-MAP-046` and aligning with Design Guidelines §3.1 & §6.1):
1. *Analytical Forward Lookahead Projection Calibration (`MapBehaviors.kt`)*:
• `calculateFollowMeCameraTarget` SHALL accept `tiltAngle: Float = 0f`.
• The offset fraction calculation SHALL scale as: $\text{offsetFraction} = (\text{lookaheadPaddingPercent} / 100\text{f}) \times 0.6\text{f}$, mapping slider values from $10\% \dots 50\%$ to $6\% \dots 30\%$ of viewport height (anchoring the marker from $56\%$ Y just below center to $80\%$ Y in the lower cockpit, with the $30\%$ default anchoring at $68\%$ Y in the canonical lower third).
2. *Perspective Tilt Foreshortening Compensation (`MapBehaviors.kt`)*:
• The ground offset projection SHALL compensate for 3D perspective pitch tilt foreshortening: $\text{tiltFactor} = \cos(\text{Math.toRadians(tiltAngle.toDouble().coerceIn(0.0, 70.0))).toFloat().coerceAtLeast(0.34f)}$, $\text{offsetMeters} = (\text{viewportHeightDp} \times \text{offsetFraction} \times \text{metersPerDp}) / \text{tiltFactor}$.
• At $\text{tiltAngle} = 70^\circ$, $\text{tiltFactor} \approx 0.342$, scaling ground offset by $\approx 2.92\times$ so that when Google Maps renders the 3D perspective camera, the on-screen displacement matches the intended fraction of viewport height.
3. *Follow-Me Controller Delegation (`MapBehaviors.kt`)*:
• `followMeController` SHALL pass `tiltAngle = effectiveTilt` into `calculateFollowMeCameraTarget`.
4. *Settings Scale Direction Transparency (Design Guidelines §3.1)*:
• In accordance with Design Guidelines §3.1, `tuning_map_lookahead_padding_desc` SHALL explicitly state physical scale direction across all 9 supported locales ("higher value = rider further down / more road ahead" / "höherer Wert = Fahrer weiter unten / mehr Vorausschau").
5. *Preservation of System Invariants*:
• Single spatial authority (Rule 33 / Design Guidelines §6.1) without competing Google Maps content padding MUST be strictly preserved.
• Bearing low-pass filter smoothing ($\alpha = 0.15$), speed-dependent zoom curve, DataStore preference schema and defaults ($30\%$), and 100% full clean-room unit test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-MAP-046` (*Unified Viewport Anchor Authority for Follow-Me Map Tracking*, Sprint 2026-41.8, ATT-3102).
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-41.8 (`ATT-3102`, commit `611d3781`).
3. *Root Reason for Existing Formulation*: In ATT-3102, forward lookahead was decoupled from Google Maps content padding to eliminate 1 Hz camera jitter. To approximate the old 15% midpoint shift, the developer used `* 0.5f`. However, `tiltAngle` was omitted from `calculateFollowMeCameraTarget`, and under default 70° tilt (`DEFAULT_MAP_FOLLOW_ME_TILT_ANGLE = 70.0f`), perspective foreshortening ($\cos(70^\circ) \approx 0.342$) squashed the displacement to ~6.8% of the screen across the entire 10%–50% slider range, making adjustments imperceptible.
4. *Preservation of Core Invariants*: Single spatial authority, smooth bearing filtering, speed-dependent zoom, and 100% clean-room test pass rate remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout tracking session in `FOLLOW_ME` mode with a 600dp viewport at zoom 18f,
• *When* `lookaheadPaddingPercent` is increased from 10% to 30% and to 50%,
• *Then* the calculated ground offset distance in meters SHALL scale strictly proportionally (5x from 10% to 50%, and 3x from 10% to 30%).
• *Given* a fixed `lookaheadPaddingPercent = 30f` and viewport height of 600dp,
• *When* `tiltAngle` is 70°,
• *Then* the calculated ground offset distance in meters SHALL be scaled up by approximately $2.92\times$ relative to `tiltAngle = 0°`, compensating for 3D perspective foreshortening.
• *Given* `calculateFollowMeCameraTarget` invoked with extreme tilt angles (e.g. 90° or negative angles),
• *When* evaluating the tilt compensation factor,
• *Then* `tiltAngle` SHALL be safely coerced to $[0^\circ, 70^\circ]$ and `tiltFactor` SHALL be clamped to $\ge 0.34\text{f}$.
• *Given* `followMeController` executing during workout tracking,
• *When* invoking `calculateFollowMeCameraTarget`,
• *Then* `followMeController` SHALL pass `tiltAngle = effectiveTilt`.
• *Given* all 9 supported application locales,
• *When* inspecting `tuning_map_lookahead_padding_desc`,
• *Then* each locale SHALL include the explicit scale direction indicator with zero missing entries.

**Invariants**: Single spatial authority, speed zoom curve, bearing smoothing, pitch angle, DataStore settings, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-GEO-001: Unified Polyline Progress Tracking Engine with Directional Virtual Gates and Orthogonal Segment Projection [Extract unified PolylineProgressTracker engine in geo/ with directional virtual gates, orthogonal projection, and smooth finish blending, delegating from LiveSegmentsRepository and LiveClimbsRepository.]
- **Tests**: \le \theta_{\text{tol}}$ (default $45^\circ$).
• Off-track deviation SHALL trigger when minimum cross-track error $D_{\text{cross}} > D_{\text{offTrack}}$ (default $50\text{m}$ for segments, $100\text{m}$ for climbs), resetting state to `FAR_FAR_AWAY`.
6. *Repository Delegation & Migration*:
• `LiveSegmentsRepository.kt` SHALL delegate gate creation, gate crossing, and polyline projection to `PolylineProgressTracker`, replacing raw odometer subtraction ($D_{\text{current}} - D_{\text{start}}$) with orthogonal along-track distance.
• `LiveClimbsRepository.kt` SHALL delegate start/summit gate detection and orthogonal projection to `PolylineProgressTracker`, replacing radial circles with directional virtual gates.
7. *Preservation of Domain Models & Invariants*:
• Segment-specific telemetry (PR comparison, time delta, elapsed time) and Climb-specific telemetry (remaining ascent, climb category, route sequencing) MUST remain intact.
• Multi-track candidate concurrency in `LiveSegmentsRepository` and active route vs free-riding climb detection in `LiveClimbsRepository` MUST NOT be altered.
• 100% clean-room unit regression test suite pass rate.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-LIV-001` (*Detect segments using "Gates" and cross-products*), `REQ-LIV-002` (*Real-time progress on starred Strava segments*), `REQ-LIV-004` (*Filter segments by bearing alignment*), `REQ-MAP-027` (*ClimbPro Real-Time Live Climb Cockpit Sheet*), and `REQ-MAP-047` (*Continuous Linear Polyline Projection for Live Climbs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.14 (`ATT-1281`), Sprint 2026-41.9 (`ATT-3174`), Sprint 2026-41.10 (`ATT-3266`).
3. *Root Reason for Existing Formulation*: Climbs and Segments were delivered under separate feature epics with divergent math (odometer subtraction vs orthogonal projection, radial circles vs virtual gates).
4. *Preservation of Core Invariants*: Starred segment PR pacing, ClimbPro elevation profile, rider marker progress fraction, and 100% clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete approaching a virtual start or finish gate,
• *When* crossing the gate line in the correct track heading direction,
• *Then* `PolylineProgressTracker` detects the crossing via cross-product sign flip and transitions state cleanly.
• *Given* an athlete riding on a parallel switchback near the gate,
• *When* location updates are evaluated,
• *Then* the gate is NOT triggered unless the perpendicular line is physically crossed in the designated direction.
• *Given* an athlete traveling along a live segment or climb,
• *When* GPS fixes arrive,
• *Then* progress is projected orthogonally onto the polyline, providing accurate along-track distance and cross-track error independent of odometer drift.
• *Given* an athlete approaching the finish or summit gate within finish threshold,
• *When* remaining distance is computed,
• *Then* $\lambda$-blending produces a smooth, continuous decrease reaching exactly $0.0\text{m}$ at the finish line.
• *Given* an athlete on a segment or climb who turns away onto another road ($D_{\text{cross}} > D_{\text{offTrack}}$),
• *When* tracking updates,
• *Then* the tracker immediately disengages and reverts state to `FAR_FAR_AWAY`.
• *Given* the automated test suite,
• *When* `LiveClimbsRepositoryTest`, `LiveSegmentsRepositoryTest`, `PolylineProgressTrackerTest`, and full application regression tests execute,
• *Then* 100% of tests pass with 0 failures.

**Invariants**: Starred segment PR comparison, ClimbPro live profile, rider pin tracking, and 100% clean-room test pass rate MUST NOT be broken.
- **Implementation**: \Delta\theta
- **Rationale**: The system SHALL extract and maintain a unified, reusable `PolylineProgressTracker` engine in `com.atrainingtracker.trainingtracker.geo` and delegate polyline tracking from both `LiveSegmentsRepository` and `LiveClimbsRepository` (ATT-3266, amending `REQ-LIV-001`, `REQ-LIV-002`, `REQ-LIV-004`, `REQ-MAP-027`, and `REQ-MAP-047`):
1. *Unified PolylineProgressTracker Engine Architecture*:
• The system SHALL define `PolylineProgressTracker` in package `com.atrainingtracker.trainingtracker.geo` as a pure-Kotlin utility decoupled from Android framework dependencies and database persistence.
• Core domain models SHALL include: `PolylineGate` (directional perpendicular virtual gate with endpoints $A, B$, reference forward point $N$, reference cross-product, and configurable gate distance threshold); `PolylineProjection` (projected point, optimal segment index, segment parameter $t \in [0, 1]$, along-track completed distance, cross-track error in meters, progress fraction $\in [0f, 1f]$, interpolated altitude, and instantaneous grade percentage); `PolylineTrackingState` (`FAR_FAR_AWAY`, `APPROACHING`, `ON_TRACK`, `FINISHED`).
2. *Directional Virtual Gate Crossing via 2D Vector Cross-Products*:
• For any polyline entry or exit, `PolylineProgressTracker` SHALL construct virtual gates perpendicular to the track entry vector ($[P_0, P_{\text{sample}}]$) and exit vector ($[P_{\text{last}-\text{sample}}, P_{\text{last}}]$).
• Gate crossing SHALL be evaluated via 2D vector cross-product: $\text{cross}(A, B, C) = (B_{\text{lat}} - A_{\text{lat}})(C_{\text{lng}} - A_{\text{lng}}) - (B_{\text{lng}} - A_{\text{lng}})(C_{\text{lat}} - A_{\text{lat}})$.
• A gate crossing event SHALL trigger if and only if: (a) Current location is within gate distance threshold ($D \le D_{\text{gate}}$); (b) Cross-product sign has flipped relative to the previous fix: $\text{sign}(\text{currCross}) \ne \text{sign}(\text{prevCross})$; (c) Cross-product sign matches the forward trajectory reference: $\text{sign}(\text{currCross}) == \text{sign}(\text{refCross})$ (for start gate) or $\text{sign}(\text{currCross}) \ne \text{sign}(\text{refCross})$ (for finish gate).
• Radial entry without cross-product sign flip (e.g. parallel pass or hairpin approach) SHALL NOT trigger gate crossing.
3. *Continuous Orthogonal Segment Projection & 3D Telemetry*:
• For an athlete at coordinate $L$, the engine SHALL project $L$ orthogonally onto each consecutive polyline segment $[P_i, P_{i+1}]$ using scalar parameter: $u = \frac{(L_{\text{lat}} - P_{i.\text{lat}})\Delta\text{lat} + (L_{\text{lng}} - P_{i.\text{lng}})\Delta\text{lng}}{\Delta\text{lat}^2 + \Delta\text{lng}^2}, \quad t = \text{coerceIn}(u, 0.0, 1.0)$.
• The engine SHALL select the segment index $i$ and scalar $t$ that minimize geodesic cross-track distance $D_{\text{cross}}$.
• Along-track completed distance SHALL evaluate to: $D_{\text{completed}} = (P_i.\text{distance} - P_{\text{first}}.\text{distance}) + t \times (P_{i+1}.\text{distance} - P_i.\text{distance})$.
• Altitude and grade SHALL interpolate continuously along the optimal segment: $\text{alt} = P_i.\text{altitude} + t \times (P_{i+1}.\text{altitude} - P_i.\text{altitude}), \quad \text{grade} = \frac{\Delta\text{altitude}}{\Delta\text{distance}} \times 100\%$.
4. *Smooth Finish Remaining Distance Blending*:
• When within finish proximity threshold ($D_{\text{end}} \le D_{\text{finishThreshold}}$), the engine SHALL blend along-track remaining distance with direct geodesic line distance to the finish gate using linear $\lambda$-interpolation: $\lambda = \min\left(1.0, \frac{D_{\text{end}}}{D_{\text{finishThreshold}}}\right), \quad D_{\text{rem}} = (D_{\text{total}} - D_{\text{completed}}) \times \lambda + (1.0 - \lambda) \times D_{\text{end}}$.
• This guarantees remaining distance continuously and monotonically reaches exactly $0.0\text{m}$ at the finish line without discrete sampling jumps.
5. *Approach Filtering, Heading Alignment & Off-Track Detection*:
• Approaching state SHALL activate when athlete distance to start gate $D_{\text{start}} \le D_{\text{approach}}$ (default $250\text{m}$) AND bearing difference $

### REQ-MAP-049: Follow-Me Camera Speed Zoom Smoothing, Zoom Hysteresis Deadband, and Configurable Speed/Bearing Filter Calibration [Follow-Me camera speed zoom smoothing, zoom hysteresis deadband, and configurable speed and bearing smoothing filter calibration in Advanced Settings.]
- **Tests**: \ge 0.10f \implies \text{activeZoom} = \text{targetZoom}$, otherwise retaining `activeZoom` without animation.
• `activeZoom` SHALL be passed to `calculateFollowMeCameraTarget` and `CameraPosition.builder().zoom(activeZoom)`.
3. *Configurable Bearing Smoothing Alpha (`MapBehaviors.kt`)*:
• `followMeController` SHALL accept parameter `bearingSmoothingAlpha: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA`.
• The bearing difference update SHALL use $\alpha_{\text{bearing}}$ clamped to $[0.05f, 0.50f]$, replacing hardcoded `0.15f`.
4. *Tuning Preferences Schema & Persistence (`TuningPreferencesDataStore.kt`)*:
• Define `KEY_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA = floatPreferencesKey("tuning_map_follow_me_speed_smoothing_alpha")` (default `0.10f`, range `0.02f`..`0.50f`).
• Define `KEY_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA = floatPreferencesKey("tuning_map_follow_me_bearing_smoothing_alpha")` (default `0.15f`, range `0.05f`..`0.50f`).
• Integrate into `TuningConfig`, `ALL_KEYS`, `tuningConfigFlow` with `.coerceIn()`, and `resetToDefaults()`.
5. *Advanced Settings UI Integration (`NavigationSection.kt` & `AdvancedTuningDialog.kt`)*:
• In `NavigationSection.kt`, render `TuningSliderItem` for Speed Smoothing (gated on `mapFollowMeSpeedZoomEnabled`, range `0.02f..0.50f`, 24 steps) and Bearing Smoothing (range `0.05f..0.50f`, 9 steps).
• Wire state and callbacks cleanly in `AdvancedTuningDialog.kt` and `ATrainingTrackerMap.kt`.
6. *9-Language Localization Parity*:
• String resources `tuning_map_speed_smoothing_title`, `tuning_map_speed_smoothing_desc`, `tuning_map_bearing_smoothing_title`, and `tuning_map_bearing_smoothing_desc` SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
7. *Preservation of System Invariants*:
• Forward lookahead projection and tilt compensation (`REQ-MAP-048`) and speed zoom curve calculation (`REQ-MAP-042`) MUST remain intact.
• 100% clean-room test pass rate across the full test suite.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Amends `REQ-MAP-042` (*Speed-Dependent Map Zoom Calibration*, Sprint 2026-41.6, ATT-2946) and `REQ-MAP-048` (*Follow-Me Camera Target Forward Lookahead Projection Scaling*, Sprint 2026-41.9, ATT-3171). Commit `300470962` set hardcoded `alpha = 0.15f` for bearing.
2. *Root Reason for Existing Formulation*: Instantaneous speed was directly used without low-pass filtering, causing continuous visual camera zoom pumping. Hardcoding bearing alpha was done for convenience during Compose migration.
3. *Preservation of Core Invariants*: Default bearing alpha remains `0.15f`, default speed alpha is `0.10f`, forward lookahead padding and tilt angle logic are preserved, and 100% full-suite unit test pass rate remains intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* active workout tracking or TCX replay in `FOLLOW_ME` mode with speed zoom enabled,
• *When* speed fluctuates with small noise ($\Delta \text{speed} < 1.0\text{ km/h}$),
• *Then* `activeZoom` remains unchanged due to the $0.10f$ hysteresis deadband, preventing jittery micro-animations.
• *Given* an athlete accelerating steadily from $10\text{ km/h}$ to $35\text{ km/h}$,
• *When* location updates arrive,
• *Then* `filteredSpeed` tracks the acceleration smoothly according to `speedSmoothingAlpha` and camera zoom scales progressively without abrupt steps.
• *Given* the athlete opening Advanced Settings -> Navigation,
• *When* adjusting the Speed Smoothing or Bearing Smoothing sliders,
• *Then* the slider values update reactively and persist to `TuningPreferencesDataStore`.
• *Given* the athlete tapping "Auf Werkseinstellungen zurücksetzen",
• *When* preferences reset,
• *Then* `mapFollowMeSpeedSmoothingAlpha` reverts to `0.10f` and `mapFollowMeBearingSmoothingAlpha` reverts to `0.15f`.

**Invariants**: Forward lookahead projection, tilt compensation, speed curve formula, and 100% clean-room test pass rate MUST NOT be broken.
- **Implementation**: \text{targetZoom} - \text{activeZoom}
- **Rationale**: The system SHALL apply low-pass exponential smoothing to speed in Follow-Me tracking mode, enforce a zoom hysteresis deadband ($\Delta \text{zoom} \ge 0.10f$) to suppress micro-animations, and provide configurable filter sliders for speed and bearing smoothing in Advanced Settings (ATT-3227, amending `REQ-MAP-042` and `REQ-MAP-048`, and aligning with Design Guidelines §3.1 & §6.1):
1. *Low-Pass Exponential Speed Smoothing (`MapBehaviors.kt`)*:
• In `followMeController`, the system SHALL maintain smoothed speed state: `var filteredSpeed by remember { mutableFloatStateOf(speed) }`.
• When `zoomFocus == MapZoomFocus.FOLLOW_ME` initializes, `filteredSpeed` SHALL snap to incoming `speed`.
• On location updates, `filteredSpeed` SHALL update via: $\text{filteredSpeed} += \alpha_{\text{speed}} \times (\text{speed} - \text{filteredSpeed})$, clamping $\alpha_{\text{speed}}$ within $[0.02f, 0.50f]$.
• `calculateFollowMeTargetZoom` SHALL consume `filteredSpeed` instead of raw instantaneous speed.
2. *Zoom Hysteresis Deadband (`MapBehaviors.kt`)*:
• In `followMeController`, the system SHALL maintain `var activeZoom by remember { mutableFloatStateOf(baseZoom) }`.
• When `speedZoomEnabled == false`, `activeZoom` SHALL lock strictly to `baseZoom`.
• When `speedZoomEnabled == true`: if $

### REQ-MAP-050: Calibrated Piecewise Linear-to-Logarithmic Follow-Me High-Speed Zoom and Autonomous Camera Control [`TST-MAP-052`]
- **Tests**: `MapBehaviors.kt`, `TuningPreferencesDataStore.kt`, `NavigationSection.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `ATrainingTrackerMap.kt`
- **Implementation**: Calibrated piecewise linear-to-logarithmic Follow-Me high-speed zoom and autonomous camera control.
- **Rationale**: The system SHALL calibrate Follow-Me camera zoom using a piecewise linear-to-logarithmic curve, raise minimum zoom to street-level resolution, and streamline Advanced Settings by removing manual camera tuning controls (ATT-3581, amending `REQ-MAP-042` and `REQ-MAP-049`):
1. *Piecewise Linear-to-Logarithmic Zoom Curve (`calculateFollowMeTargetZoom` in `MapBehaviors.kt`)*:
• For speeds $0.0 \le \text{speedKmh} \le 20.0\text{ km/h}$, target zoom SHALL interpolate linearly between `baseZoom` ($20.0f$ at $0\text{ km/h}$) and `cruisingZoom` ($18.0f$ at $20\text{ km/h}$): $\text{targetZoom} = \text{baseZoom} - \frac{\text{baseZoom} - \text{cruisingZoom}}{20.0} \times \text{speedKmh}$.
• For speeds $\text{speedKmh} > 20.0\text{ km/h}$, target zoom SHALL scale logarithmically with $\log_2(\text{speedKmh} / 20.0)$: $\text{rawZoom} = \text{cruisingZoom} - \frac{\text{baseZoom} - \text{cruisingZoom}}{2.0} \times \frac{\ln(\text{speedKmh} / 20.0)}{\ln(2.0)}$, returning $\text{rawZoom.coerceIn(minZoom, maxZoom)}$.
• When `speedZoomEnabled == false`, the camera SHALL lock firmly to $\text{baseZoom.coerceIn(minZoom, maxZoom)}$.
• Default `minZoom` SHALL equal `16.0f` (`DEFAULT_MAP_FOLLOW_ME_MIN_ZOOM = 16.0f` in `TuningPreferencesDefaults`).
2. *Autonomous Camera Parameter Wiring (`ATrainingTrackerMap.kt`)*:
• `followMeController(...)` SHALL be invoked using canonical defaults from `TuningPreferencesDefaults` for all camera tuning parameters (`baseZoom = DEFAULT_MAP_FOLLOW_ME_INITIAL_ZOOM`, `speedZoomEnabled = DEFAULT_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED`, `cruisingZoom = DEFAULT_MAP_FOLLOW_ME_CRUISING_ZOOM`, `tiltAngle = DEFAULT_MAP_FOLLOW_ME_TILT_ANGLE`, `lookaheadPaddingPercent = DEFAULT_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT`, `speedSmoothingAlpha = DEFAULT_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA`, `bearingSmoothingAlpha = DEFAULT_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA`).
• Legacy persisted DataStore tweaks SHALL NOT override calibrated physical camera behavior.
3. *Streamlined Navigation Advanced Settings UI (`NavigationSection.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`)*:
• In `NavigationSection.kt`, the Follow-Me map camera sub-section divider, header (`tuning_map_camera_section_title`), and the 7 camera tuning items (`Initial Zoom`, `Speed-Dependent Zoom Enabled`, `Cruising Speed Zoom`, `Tilt Angle`, `Lookahead Bottom Padding`, `Speed Smoothing`, `Bearing Smoothing`) SHALL be removed.
• Unused Follow-Me parameters in `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt` SHALL be removed.
• Remaining Navigation items (`routeSelectionRadiusKm`, `navigationCueDismissDurationSec`, `turnCueCountdownDistanceMeters`, `autoFollowUniqueRouteEnabled`) SHALL remain fully active and configurable.
4. *Preservation of System Invariants*:
• Low-speed linear progression ($\le 20\text{ km/h}$), exponential speed/bearing smoothing, hysteresis deadband ($0.10f$), forward lookahead projection, and 100% full clean-room unit test suite pass rate MUST NOT be broken.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-MAP-042` (*Speed-Dependent Map Zoom Calibration*, Sprint 2026-41.6, `ATT-2946`) and `REQ-MAP-049` (*Follow-Me Camera Speed Zoom Smoothing...*, Sprint 2026-42.0, `ATT-3227`). Formally tracked as `REQ-MAP-050`.
2. *Historical Origin & Commit Trace*: `REQ-MAP-042` introduced linear speed-dependent zoom and camera sliders in Sprint 2026-41.6 (commit `3c59a421`). `REQ-MAP-048` added forward lookahead projection in Sprint 2026-41.9 (commit `d97e201b`). `REQ-MAP-049` added exponential smoothing in Sprint 2026-42.0 (commit `c891ba32`).
3. *Root Reason for Existing Formulation*: Early Compose prototyping used linear interpolation and exposed developer sliders for quick experimentation. Because Google Maps ground resolution scales as $2^{-\text{zoom}}$, continuing linear slope above $20\text{ km/h}$ caused exponential viewport blowout.
4. *Preservation of Core Invariants*: Low-speed behavior ($0\text{–}20\text{ km/h}$) remains identical. Smoothing filters, hysteresis deadband, forward projection, and 100% full clean-room unit test pass rate are preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* speeds between $0\text{ km/h}$ and $20\text{ km/h}$,
• *When* `calculateFollowMeTargetZoom` is evaluated with default parameters,
• *Then* zoom interpolates linearly from $20.0f$ ($0\text{ km/h}$) to $19.0f$ ($10\text{ km/h}$) and $18.0f$ ($20\text{ km/h}$).
• *Given* descent speeds of $30\text{ km/h}$, $40\text{ km/h}$, $50\text{ km/h}$, $60\text{ km/h}$, and $90\text{ km/h}$,
• *When* `calculateFollowMeTargetZoom` is evaluated,
• *Then* it returns $17.42f \pm 0.01f$ at $30\text{ km/h}$, $17.00f \pm 0.01f$ at $40\text{ km/h}$, $16.68f \pm 0.01f$ at $50\text{ km/h}$, $16.42f \pm 0.01f$ at $60\text{ km/h}$, and clamps at $16.0f$ at $90\text{ km/h}$.
• *Given* `speedZoomEnabled == false`,
• *When* `calculateFollowMeTargetZoom` is evaluated at any speed,
• *Then* it returns `baseZoom` ($20.0f$).
• *Given* the athlete opening Advanced Settings -> Navigation,
• *When* rendered,
• *Then* the obsolete Follow-Me camera tuning sliders are omitted, while Route Selection Radius, Cue Dismiss Duration, Turn Cue Countdown Distance, and Auto-Follow Unique Route remain present.

**Invariants**: Pure UI presentation and zoom curve calibration; GPS position tracking and 100% clean-room test pass rate MUST NOT be broken.

### REQ-MAP-051: Referentially Stable Map Polyline Allocation, Static Layer Flow Decoupling, Keyed Layer Composition & Preview Downsampling [`TST-MAP-053`]
- **Tests**: `TrackingViewModel.kt`, `MapModels.kt`, `MapLayers.kt`, `MapContentScope.kt`, `ATrainingTrackerMap.kt`, `CropWorkoutScreen.kt`, `RoutesDatabaseManager.kt`, `SegmentsRepository.kt`
- **Implementation**: Referentially stable map polyline allocation, static layer flow decoupling, keyed layer composition & preview downsampling.
- **Rationale**: The system SHALL prevent `OutOfMemoryError` in Google Maps SDK polyline tessellation (`Polyline.setPoints` / `lib6.phoenix`) by decoupling static map layers from 1-Hz telemetry flows, memoizing `latLngs` and `PatternItem` lists with referential identity (`===`), stabilizing `MapContentScopeImpl` with keyed layer composition, and downsampling workout crop preview polylines (ATT-3690):
1. *Decoupled Static Map Layer Flows (`REQ-MAP-051.1`)*:
• The system SHALL decouple static segment and route polyline layer streams from 1-Hz live GPS track and live telemetry updates so that static route and segment map models are only recomputed when underlying static segment paths, stored routes, or active navigation targets change — never on 1-Hz live track, speed, or bearing emissions.
• Live athlete speed and bearing streams SHALL remain reactively subscribed in compliance with Rule 39.
2. *Referential Memoization of Coordinate Lists and Stroke Patterns (`REQ-MAP-051.2`)*:
• The system SHALL precompute and share immutable `List<LatLng>` coordinate lists across domain path entities and map presentation models so that coordinate list references remain referentially identical (`===`) across wrapper updates.
• The system SHALL reuse immutable singleton `List<PatternItem>` stroke pattern definitions for dashed and dotted polylines instead of allocating new pattern lists on each property access or recomposition.
3. *Stable Map Content Scope & Keyed Layer Rendering (`REQ-MAP-051.3`)*:
• The map container SHALL preserve its content orchestration scope across content lambda updates without discarding and recreating internal state containers on 1-Hz live track emissions.
• The map rendering loop SHALL key each rendered path layer by its stable path identifier (`id`) and avoid mutating state lists when path collections are unchanged.
4. *Bounded Preview Downsampling in Workout Cropping (`REQ-MAP-051.4`)*:
• The workout crop preview map SHALL pre-extract valid path coordinates once per loaded workout and downsample the active preview slice to at most 500 points (strictly preserving the exact start and end coordinates of the cropped range) before rendering.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-MAP-051`, complementing `REQ-UI-326` (*TrackingMapContainer Recomposition Barrier*, ATT-2944) and `REQ-MAP-046` (*Unified Viewport Anchor Authority*).
2. *Historical Origin & Commit Trace*: Sprint 2026-41.6 (`ATT-2944`) and Sprint 2026-42.5 (`ATT-3274`).
3. *Root Reason for Existing Formulation*: `MapRoute`, `MapSegment`, and `MapTrack` originally used `by lazy { path.map { it.latLng } }` assuming wrapper instances were long-lived, while `TrackingViewModel` reconstructed wrapper instances inside the 1-Hz `currentTrack` combine loop.
4. *Preservation of Core Invariants*: All polyline visual styling, dash patterns, live segment activation, and 100% clean-room test suite pass rate are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a `RouteWithPath`, `SegmentWithPath`, `MapRoute`, `MapSegment`, or `MapTrack` instance,
• *When* `toMapRoute(...)` is invoked or `.pattern` is read multiple times,
• *Then* the returned `latLngs` and `pattern` references are referentially identical (`===`) with zero transient list allocations.
• *Given* `TrackingViewModel` observing 1-Hz `currentTrack` and `liveSegments` updates,
• *When* static segment paths and routes are unchanged,
• *Then* `mapState.mapRoutes` and `mapState.mapSegments` are not reallocated.
• *Given* a workout with 10,000 GPS points in the workout crop screen,
• *When* the preview path is evaluated for any slider range,
• *Then* the returned preview path contains at most 500 points and preserves exact first and last points.

**Invariants**: Zero visual change to map polylines; referential identity (`===`) of static `latLngs` and `pattern` lists; 100% clean-room test pass rate MUST NOT be broken.

### REQ-MAP-052: Reactive Speed/Bearing Flow Subscription in TrackingViewModel & Continuous Follow-Me Smoothing Convergence [targetSpeed - filteredSpeed]
- **Tests**: < 0.5f`), exponential speed smoothing (`filteredSpeed += sAlpha * (targetSpeed - filteredSpeed)`, snapping to `targetSpeed` when `
- **Implementation**: diff
- **Rationale**: The system SHALL subscribe reactively to `banalServiceRepository.currentSpeed` and `banalServiceRepository.currentBearing` in `TrackingViewModel.observeMapState()` (`liveTelemetryFlow`, Rule 39) and SHALL continuously step Follow-Me camera smoothing (`stepFollowMeSmoothing` in `MapBehaviors.kt`) until `filteredSpeed`, `filteredBearing`, and `activeZoom` converge to their target values even when speed, bearing, or location remain steady across subsequent 1-Hz ticks (ATT-3775, amending `REQ-MAP-049` and `REQ-MAP-050`, and complementing `REQ-MAP-051`):
1. *Reactive Kinematics Subscription (`REQ-MAP-052.1` — Rule 39)*:
• `TrackingViewModel.observeMapState()` SHALL combine `banalServiceRepository.currentTrack`, `banalServiceRepository.bSportType`, `banalServiceRepository.currentSpeed`, and `banalServiceRepository.currentBearing` inside `liveTelemetryFlow` (`LiveTelemetryTuple`).
• Any emission on `currentSpeed` or `currentBearing` SHALL immediately emit a new `TrackingMapState` (`_mapState` and `_uiState`) in both `READY` (empty `currentTrack`) and `TRACKING` (non-empty `currentTrack`) modes without requiring a concurrent `currentTrack` emission, converting `null` speed/bearing safely to `0.0f`.
2. *Continuous Follow-Me EMA Convergence (`REQ-MAP-052.2`)*:
• `MapBehaviors.kt` SHALL define pure function `stepFollowMeSmoothing(currentFilteredSpeed: Float, currentFilteredBearing: Float, currentActiveZoom: Float, targetSpeed: Float, targetBearing: Float, baseZoom: Float, speedZoomEnabled: Boolean, cruisingZoom: Float, minZoom: Float, maxZoom: Float, speedSmoothingAlpha: Float, bearingSmoothingAlpha: Float): FollowMeSmoothingStep` returning `FollowMeSmoothingStep(filteredSpeed, filteredBearing, activeZoom, effectiveZoom, isConverged)`.
• `stepFollowMeSmoothing` SHALL compute shortest-arc angular interpolation for bearing (`[-180f, 180f]`, snapping to `targetBearing` when `

### REQ-MAP-053: Multi-File Route Selection, Batch Import, Sport Type Selection Prompt & External Multi-Share Processing [`TST-MAP-055, TST-MAP-056`]
- **Tests**: `RoutesScreen.kt`, `RoutesViewModel.kt`, `GpxImportActivity.kt`, `GpxImportViewModel.kt`, `BatchImportSportTypeDialog.kt`, `AndroidManifest.xml`
- **Implementation**: Support selecting and importing multiple GPX route files at once with responsive batch sport type selection bottom sheet (AppModalBottomSheet, FlowRow chip wrapping, multi-line centered segmented buttons, neutral Other preselection), sequential review mode, progress dialog, and external share support.
- **Rationale**: `RoutesScreen.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreen.kt`), `RoutesViewModel.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesViewModel.kt`), `GpxImportActivity.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/activities/GpxImportActivity.kt`), `GpxImportViewModel.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/GpxImportViewModel.kt`), and `BatchImportSportTypeDialog.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialog.kt`) SHALL support multi-file document picking via `ActivityResultContracts.GetMultipleContents()`, prompt the athlete with `BatchImportSportTypeDialog` offering a choice between "Apply to all" (with sport type selection: `BIKE`, `RUN`, `UNKNOWN`) and "Configure individually" (sequential interactive editing via `EditRouteScreen`), preserve single-file interactive editing (`EditRouteScreen`) without intermediate prompts, execute fault-tolerant batch imports with non-dismissible modal progress dialog and localized summary notifications, AND SHALL support external multi-file share intents (`Intent.ACTION_SEND_MULTIPLE`) (ATT-4049, refined by ATT-4284, ATT-4372, and ATT-4430, complementing `REQ-MAP-025`, `REQ-MAP-026`, and `REQ-UI-159`):
1. *Multi-File Picker Contract (`REQ-MAP-053.1`)*:
• `RoutesScreen.kt` SHALL instantiate `rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents())` accepting `*/*`.
• When `uris.isEmpty()`, the system SHALL perform no operation.
2. *Single vs. Batch Route Import Flow Discrimination & Sport Type Prompt (`REQ-MAP-053.2`)*:
• Single File (`uris.size == 1`): The system SHALL launch `GpxImportActivity` passing `intent.data = uris.first()`, presenting `EditRouteScreen` directly to allow inspecting and editing route attributes before saving.
• Multiple Files (`uris.size > 1`): In `RoutesScreen.kt`, the system SHALL display `BatchImportSportTypeDialog` offering a choice between "Apply to all" (`BatchSportMode.ALL`) and "Configure individually" (`BatchSportMode.INDIVIDUAL`). If "Apply to all" is confirmed, the system SHALL execute `RoutesViewModel.importRoutesBatch(uris, sportType)`. If "Configure individually" is confirmed, the system SHALL launch `GpxImportActivity` with `ACTION_SEND_MULTIPLE`, passing `uris` and `EXTRA_INDIVIDUAL_MODE = true`.
3. *Batch Import Execution & Target Sport Type in `RoutesViewModel` (`REQ-MAP-053.3`)*:
• `RoutesViewModel` SHALL expose `batchImportProgress: StateFlow<BatchImportProgress?>` (`val current: Int, val total: Int`), `batchImportResult: StateFlow<BatchImportResult?>` (`val importedCount: Int, val failedCount: Int, val totalCount: Int`), and `fun clearBatchImportResult()`.
• `RoutesViewModel` SHALL expose `fun importRoutesBatch(uris: List<Uri>, sportType: BSportType = BSportType.UNKNOWN)` on `Dispatchers.IO`.
• For each URI $i \in [1, N]$, the engine SHALL emit `BatchImportProgress(current = i, total = N)`, parse via `gpxRouteImporter.importRouteFromGpx(uri)`, override `summary.bSportType = sportType` (when `sportType != BSportType.UNKNOWN`), insert valid routes via `routesRepository.insertRoute(summary, pathPoints, waypoints)` incrementing `importedCount`, and isolate failures incrementing `failedCount` without aborting remaining files.
• Upon completion, `_batchImportProgress.value` SHALL reset to `null` and `_batchImportResult.value` SHALL emit the final tally.
4. *Modal Progress Dialog & Summary Snackbar in `RoutesScreen` (`REQ-MAP-053.4`)*:
• When `batchImportProgress != null`, `RoutesScreen` SHALL render a non-dismissible modal `Dialog` with `CircularProgressIndicator` and localized progress string `R.string.routes_import_progress` ("%1$d of %2$d").
• When `batchImportResult != null`, `RoutesScreen` SHALL display a localized `Snackbar`: `R.string.routes_import_success` when `failedCount == 0`, `R.string.routes_import_partial` when `failedCount > 0 && importedCount > 0`, or `R.string.routes_import_all_failed` when `importedCount == 0`, and clear the result via `viewModel.clearBatchImportResult()`.
5. *External Multi-File Share & Sequential Review in `GpxImportActivity` (`REQ-MAP-053.5`)*:
• `AndroidManifest.xml` SHALL declare intent filters for `android.intent.action.SEND` and `android.intent.action.SEND_MULTIPLE`.
• `GpxImportActivity` and `GpxImportViewModel` SHALL extract all stream URIs: single URI branches to `EditRouteScreen`; multiple URIs without `EXTRA_INDIVIDUAL_MODE` SHALL prompt with `BatchImportSportTypeDialog`.
• When "Apply to all" is selected, `GpxImportViewModel.handleBatchUris(uris, sportType)` SHALL execute headless batch import applying `sportType`.
• When `EXTRA_INDIVIDUAL_MODE == true` or "Configure individually" is selected, `GpxImportViewModel.startIndividualImport(uris)` SHALL coordinate sequential interactive editing, loading each route into `ImportState.EditingIndividual(summary, points, waypoints, current = i + 1, total = N)`. Saving inserts the route into `routesRepository` and advances to route $i + 1$; skipping advances to route $i + 1$ without inserting; upon exhaustion, transitions to `ImportState.BatchSuccess(importedCount, failedCount)` and displays completion Toast.
6. *Batch Import Sport Type Dialog Component Architecture (`REQ-MAP-053.6`)*:
• `BatchImportSportTypeDialog` (`ui/routes/BatchImportSportTypeDialog.kt`) SHALL render an `AppModalBottomSheet` (from `ui/components/core/AppModalBottomSheet.kt`) displaying sheet title (`R.string.batch_import_dialog_title_format`, "%1$d Routes"), top drag handle, and close button, matching the route modal presentation standard.
• Mode selection SHALL use `SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth())` with two segments: "Apply to all" (`R.string.batch_import_mode_all`) and "Configure individually" (`R.string.batch_import_mode_individual`) per Design Guidelines Section 1.1.
• Segmented button labels SHALL support multi-line centered wrapping (`maxLines = 2`, `textAlign = TextAlign.Center`, `overflow = TextOverflow.Ellipsis`, `style = MaterialTheme.typography.labelMedium`) to prevent mid-word clipping across all locales.
• When "Apply to all" is selected: SHALL render `Text(stringResource(R.string.route_sport_type))` and a `FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth())` containing `FilterChip` items arranged in canonical horizontal order `listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN)` (Rule 57 / `EditRouteScreen.kt` parity: specific primary sports `Bike` and `Run` first, with catch-all `Other` on the far right) with corresponding icons and labels, ensuring pills wrap intact without single-character vertical text distortion (Rule 54).
• Initial preselected sport state SHALL default to `BSportType.UNKNOWN` ("Other") to ensure neutrality for mixed or unspecified outdoor activities, remaining preselected on the far right without displacing primary sports (Rule 57).
• When "Configure individually" is selected: SHALL render localized explanatory hint `R.string.batch_import_individual_hint`.
• Actions slot: Cancel (`R.string.cancel`) and Confirm (`R.string.batch_import_action_import` in "Apply to all" mode / `R.string.batch_import_action_continue` in "Configure individually" mode).
7. *9-Language Localization & Positional Specifier Parity (`REQ-MAP-053.7`)*:
• String tokens `batch_import_dialog_title_format`, `batch_import_mode_all`, `batch_import_mode_individual`, `batch_import_individual_hint`, `batch_import_action_import`, `batch_import_action_continue`, `batch_import_individual_progress_format`, and `batch_import_skip_route`, together with existing tokens `routes_import_progress`, `routes_import_success`, `routes_import_partial`, `routes_import_all_failed`, `route_sport_type`, `sport_type_bike`, `sport_type_run`, and `sport_type_other`, SHALL exist across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with matching indexed positional format specifiers (`%1$d`, `%2$d`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-MAP-053` (*Multi-File Route Selection, Batch Import & External Multi-Share Processing*, specifically sub-clauses `REQ-MAP-053.2`, `REQ-MAP-053.3`, `REQ-MAP-053.5`, and `REQ-MAP-053.6`). Targets `RoutesScreen.kt`, `RoutesViewModel.kt`, `GpxImportActivity.kt`, `GpxImportViewModel.kt`, `BatchImportSportTypeDialog.kt`, `BatchImportSportTypeDialogTest.kt`, `docs/requirements.md`, and `docs/tests.md`.
2. *Historical Origin & Commit Trace*: Sprint `2026-42.10` under ticket `ATT-4049` (commit `8c418181`: headless batch import); refined in Sprint `2026-43.1` under ticket `ATT-4284` (commit `49133ab1`: introduced `BatchImportSportTypeDialog` as an `AlertDialog` defaulting to `BIKE`). During Sprint `2026-43.1` Joint Review (`ATT-4290`, commit `7fafb143`), live on-device testing revealed clipped segmented buttons and vertical 'Andere' chip crushing in German (`att_batch_import_dialog.png`), establishing Rule 54 (`docs/design_guidelines.md` Sec 5.13). Polished in Sprint `2026-43.2` under `ATT-4372`. Refined in Sprint `2026-43.3` under `ATT-4430` to enforce Rule 57 (ordering sport type chips with Other on the far right: `Bike, Run, Other`, matching `EditRouteScreen.kt`).
3. *Root Reason for Existing Formulation*: `ATT-4284` implemented `BatchImportSportTypeDialog` as an `AlertDialog` with a 2-segment button and 3 chips in a `Row` under the assumption that short English labels would fit horizontally. The author applied `maxLines = 1` citing Rule 38 without budgeting for verbose localized strings in narrow dialog containers. `BSportType.BIKE` was preselected assuming cycling was the primary use case, rather than preserving domain neutrality across mixed outdoor sports. In `ATT-4372`, when making `BSportType.UNKNOWN` the default preselection, the author placed it first in the chip list under the false assumption that default preselection must occupy the first visual slot, violating Rule 57 and creating visual inconsistency with `EditRouteScreen.kt`.
4. *Preservation of Core Invariants*: Call site API contracts in `RoutesScreen` and `GpxImportActivity` remain 100% identical; binary mode toggle (`BatchSportMode.ALL` vs `BatchSportMode.INDIVIDUAL`) remains preserved; all three sport types (`BIKE`, `RUN`, `UNKNOWN`) remain selectable with canonical icons and localized names; non-blocking asynchronous batch import on `Dispatchers.IO` is preserved; single-file preview preservation (`uris.size == 1` $\to$ `EditRouteScreen`) is preserved; 9-language translation parity and 100% clean-room test pass rate under `./gradlew testDebugUnitTest` are strictly maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete selecting exactly 1 GPX file in `RoutesScreen`,
• *When* the picker returns,
• *Then* `GpxImportActivity` opens `EditRouteScreen` directly without displaying `BatchImportSportTypeDialog`.
• *Given* an athlete selecting multiple GPX files ($N > 1$) in `RoutesScreen`,
• *When* the picker returns,
• *Then* `BatchImportSportTypeDialog` is displayed in an `AppModalBottomSheet` showing "Import N Routes" with mode switcher defaulting to "Apply to all", sport chips ordered as Bike, Run, Other (`listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN)` with Other on the far right per Rule 57), and sport defaulting to `Other` (`BSportType.UNKNOWN`) preselected.
• *Given* `BatchImportSportTypeDialog` rendered on any supported device in any of the 9 canonical locales (including German, Spanish, French, and Polish),
• *When* displayed,
• *Then* segmented button labels display without mid-word truncation using multi-line centered wrapping, and sport type filter chips render as intact pills in a `FlowRow` with zero single-character vertical text stacking.
• *Given* `BatchImportSportTypeDialog` with "Apply to all" and `Other` (`BSportType.UNKNOWN`) selected,
• *When* the athlete taps "Import",
• *Then* `RoutesViewModel.importRoutesBatch(uris, sportType = BSportType.UNKNOWN)` executes, importing all routes with sport type `UNKNOWN`, showing modal progress and success Snackbar.
• *Given* `BatchImportSportTypeDialog`,
• *When* the athlete selects "Configure individually" and taps "Continue",
• *Then* `GpxImportActivity` launches with `EXTRA_INDIVIDUAL_MODE = true`, opening each route sequentially in `EditRouteScreen` with route index progress, allowing custom editing and saving/skipping per route.
• *Given* external apps sharing multiple GPX files via `ACTION_SEND_MULTIPLE`,
• *When* `GpxImportActivity` opens,
• *Then* `BatchImportSportTypeDialog` is displayed offering "Apply to all" or "Configure individually", executing the corresponding flow.

**Invariants**: Single-file preview preservation, non-blocking asynchronous I/O, fault tolerance across corrupt files, 9-language translation parity, and 100% clean-room test pass rate.

### REQ-MAP-054: TurnCueDetector Dead Code Cleanup, Unused Circular Smoothing Removal & Continuous Chord Window Boundary Interpolation Architecture [`TST-MAP-057`]
- **Tests**: `TurnCueDetector.kt`, `RouteAutoDetector.kt`
- **Implementation**: Excise dead smoothing code and buffers in TurnCueDetector and interpolate lookaround chords at exact distance window boundaries to eliminate sparse-vertex angle dilution.
- **Rationale**: The system SHALL excise unused bearing smoothing buffers, dead trigonometric circular vector mean calculations, nested functions, and unused incoming bearing array allocations from `TurnCueDetector` (`TurnCueDetector.kt`), and SHALL interpolate lookaround chord endpoints continuously at exact distance envelope boundaries ($d_{\text{cur}} \pm W$, where $W_{\text{short}} = 10.0\text{ m}$ and $W_{\text{long}} = 40.0\text{ m}$) on sparse and variable-pitch polylines to prevent discrete vertex overshoot and angle dilution, while maintaining $O(N)$ execution with zero heap object allocation via primitive coordinate bearing computation, preserving public detector APIs, and maintaining 100% test pass rate across all existing turn detection test cases (ATT-4460, refining `REQ-MAP-028`):
1. *Excise Unused Smoothing Buffers & Dead Code Allocations (`TurnCueDetector.kt`, `REQ-MAP-054.1`)*:
• The system SHALL remove `val smoothedBearings = FloatArray(m - 1)` (former line 180).
• The system SHALL remove Step 4 distance-weighted spatial circular vector mean loop (former lines 200–245).
• The system SHALL remove nested helper `fun calcWindowBearing(fromIdx: Int, toIdx: Int): Float` (former lines 248–264).
• The system SHALL remove `val inBearings = FloatArray(m)` and assignment `inBearings[i] = bInShort` (former lines 268, 299).
• `TurnCueDetectorConfig` SHALL retain `smoothingRadiusMeters: Double = 2.5` with its default value to preserve full binary and source compatibility for external configuration callers.
2. *Continuous Distance Chord Window Boundary Interpolation (`REQ-MAP-054.2`)*:
• For both short window ($W_{\text{short}} = 10.0\text{ m}$) and long window ($W_{\text{long}} = 40.0\text{ m}$), chord endpoints SHALL be interpolated at exact distance envelope boundaries along the polyline segments spanning the window boundaries:
- Inbound boundary target: $D_{\text{in}} = \max(0.0, d_{\text{cur}} - W)$ on segment $[prevIdx, prevIdx + 1]$. When $prevIdx + 1 < i$, start point coordinate $P(D_{\text{in}})$ is interpolated linearly along segment $[prevIdx, prevIdx + 1]$ at $t = (D_{\text{in}} - \text{distances}[prevIdx]) / (\text{distances}[prevIdx + 1] - \text{distances}[prevIdx])$, and chord bearing is evaluated from $P(D_{\text{in}})$ to vertex $i$. When $prevIdx + 1 == i$ or $d_{\text{cur}} - D_{\text{in}} \le 10^{-4}\text{ m}$, chord collapses to segment bearing `rawBearings[i - 1]`.
- Outbound boundary target: $D_{\text{out}} = \min(\text{totalDistance}, d_{\text{cur}} + W)$ on segment $[nextIdx - 1, nextIdx]$. When $nextIdx > i + 1$, end point coordinate $P(D_{\text{out}})$ is interpolated linearly along segment $[nextIdx - 1, nextIdx]$ at $t = (D_{\text{out}} - \text{distances}[nextIdx - 1]) / (\text{distances}[nextIdx] - \text{distances}[nextIdx - 1])$, and chord bearing is evaluated from vertex $i$ to $P(D_{\text{out}})$. When $nextIdx == i + 1$ or $D_{\text{out}} - d_{\text{cur}} \le 10^{-4}\text{ m}$, chord collapses to segment bearing `rawBearings[i]`.
3. *Zero Heap Object Allocation Invariant & Primitive Coordinate Bearing Overload (`RouteAutoDetector.kt`, `REQ-MAP-054.3`)*:
• The Step 5 chord calculation loop MUST NOT allocate heap objects (`LatLng`, arrays, or lambdas).
• `RouteAutoDetector` SHALL provide a primitive coordinate overload: `fun computeBearing(lat1Deg: Double, lon1Deg: Double, lat2Deg: Double, lon2Deg: Double): Float`, with `computeBearing(from: LatLng, to: LatLng)` delegating to it.
4. *Preservation of Public API Contracts & Downstream Invariants (`REQ-MAP-054.4`)*:
• Public API signatures of `TurnCueDetector.extractOrDetectCues`, `detectGeometricCues`, and `detectGeometricCuesFromLatLng` remain strictly unchanged.
• Downstream navigation engine (`TurnByTurnNavigationEngine.kt`), UI prompt banners (`TurnPromptBanner.kt`), audio alerts (`NavigationAudioAlertManager.kt`), and turn angle classification thresholds (`classifyTurn`) remain strictly unchanged.
• 100% clean-room test suite pass rate across all 18 existing test cases (`TST-MAP-045.1` - `TST-MAP-045.18`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-MAP-028` (Section 1: *Turn Cue Source & Geometric Synthesis*). Targets `TurnCueDetector.kt`, `RouteAutoDetector.kt`, and `TurnCueDetectorTest.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-1450` (Sprint `2026-40.14`, commit `54c5c2d3`: initial geometric detection), refined in `ATT-3494` (Sprint `2026-42.3`, commit `7aabbd0c`: multi-scale sliding windows, apex localization, and circular smoothing specification).
3. *Root Reason for Existing Formulation*: Circular vector mean smoothing was prototyped in `ATT-3494` to filter bearing jitter before calculating deflection, but multi-scale chords combined with perpendicular displacement jitter filtering provided superior apex localization and noise rejection. The smoothing code was left in place but disconnected. The discrete index loop advanced vertex pointers without continuous distance interpolation, causing massive chord window overshoot (up to 10x) on sparse polylines where a short corner segment is adjacent to long 80m–150m straightaways.
4. *Preservation of Core Invariants*: Public APIs unchanged; `TurnCueDetectorConfig.smoothingRadiusMeters` preserved for binary/source compatibility; pure, stateless, thread-safe, re-entrant execution preserved; zero heap allocation during geometric processing preserved; and 100% pass rate across all 18 existing unit tests in `TurnCueDetectorTest.kt`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a sparse polyline with a sharp 90-degree corner (e.g. 5m segment) followed by an 80m–150m straightaway,
• *When* `detectGeometricCues` is evaluated,
• *Then* the outbound chord is interpolated at exact boundary $d_{\text{cur}} + 10.0\text{ m}$ rather than jumping to the distant vertex, detecting the 90-degree turn accurately with `TurnDirection.RIGHT` / `LEFT`.
• *Given* a route processed by `TurnCueDetector`,
• *When* inspected,
• *Then* zero CPU cycles are spent computing unused circular vector mean smoothed bearings, and neither `smoothedBearings` nor `inBearings` arrays are allocated.
• *Given* a route with 5,000 points,
• *When* processed by `TurnCueDetector`,
• *Then* execution completes in $< 100\text{ ms}$ with zero heap allocation churn during internal window processing.
• *Given* all existing 18 test cases in `TurnCueDetectorTest.kt`,
• *When* executed,
• *Then* 100% pass without regression.

**Invariants**: Zero heap allocation during Step 5 loop, thread safety, pure stateless execution, public API backward compatibility, and 100% full-suite test pass rate.

### REQ-GEO-002: Equirectangular Scaled Polyline Projection, Orthogonal Virtual Gates & Windowed Out-and-Back Route Progress Tracking [`TST-GEO-002`]
- **Tests**: `PolylineProgressTracker.kt`
- **Implementation**: Implement equirectangular coordinate scaling and windowed forward segment search in PolylineProgressTracker to eliminate projection skew and prevent out-and-back jumping.
- **Rationale**: The system SHALL implement local equirectangular $\cos(\text{lat})$ coordinate scaling and windowed forward segment continuity search in `PolylineProgressTracker` to eliminate latitude projection distortion and prevent segment jumping on out-and-back and lollipop routes (ATT-4015, Implemented in: ATT-4015, Status: Verified, amending `REQ-GEO-001`):
1. *Local Equirectangular Coordinate Scaling (`REQ-GEO-002.1`)*:
• For any polyline segment between $P_1$ and $P_2$, `PolylineProgressTracker` SHALL compute mean latitude $\phi = \frac{P_1.\text{lat} + P_2.\text{lat}}{2}$ and longitude scaling factor $\cos\phi = \cos(\text{toRadians}(\phi))$.
• Scaled segment delta vectors SHALL evaluate to: $dX = (P_2.\text{lng} - P_1.\text{lng}) \times \cos\phi, \quad dY = P_2.\text{lat} - P_1.\text{lat}$.
• For location $L$, projection scalar $u$ SHALL evaluate to: $u = \frac{(L.\text{lng} - P_1.\text{lng})\cos\phi \cdot dX + (L.\text{lat} - P_1.\text{lat}) \cdot dY}{dX^2 + dY^2}, \quad t = \text{coerceIn}(u, 0.0, 1.0)$.
• Gate endpoint vectors in `createGate` SHALL apply equirectangular scaling: $(\Delta\text{lat}_\perp, \Delta\text{lng}_\perp) = (dX, -dY / \cos\phi)$ scaled to gate length, guaranteeing true physical $90.0^\circ$ orthogonality at all latitudes.
• Virtual gate crossing cross-products SHALL scale longitude deltas by $\cos\phi$ before computing z-component sign flips.
2. *Windowed Forward Segment Search with Corridor Fallback (`REQ-GEO-002.2`)*:
• `PolylineProgressTracker.projectLocation` SHALL support optional parameters: `lastSegmentIndex: Int? = null`, `searchWindowBackward: Int = 2`, `searchWindowForward: Int = 10`, `maxCorridorMeters: Double = 50.0`.
• When `lastSegmentIndex != null`, the search SHALL evaluate candidate segments in $[(lastSegmentIndex - searchWindowBackward).\text{coerceAtLeast}(0), (lastSegmentIndex + searchWindowForward).\text{coerceAtMost}(N - 2)]$.
• If the minimum cross-track error within the window satisfies $D_{\text{cross}} \le maxCorridorMeters$, the windowed candidate SHALL be selected, preserving directional continuity and preventing jumping across opposing legs on out-and-back or lollipop courses.
• If $D_{\text{cross}} > maxCorridorMeters$ (genuine off-route deviation or route rejoin) or `lastSegmentIndex == null` (initial fix or stateless callers), the search SHALL fall back to global search across all segments $0 \dots N - 2$.
3. *Preservation of Core Invariants (`REQ-GEO-002.3`)*:
• Pure Kotlin JVM object without Android SDK or SQLite dependencies MUST be strictly preserved.
• 100% backward compatibility for existing callers passing `lastSegmentIndex = null` (`LiveSegmentsRepository`, `LiveClimbsRepository`, `TrackingElevationProfileResolver`) MUST be preserved.
• 3D altitude and grade interpolation, $\lambda$-blended finish convergence, and clean-room unit regression pass rate MUST remain strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-GEO-002` under Epic `ATT-2564` (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*), amending `REQ-GEO-001` (*Unified Polyline Progress Tracking Engine with Directional Virtual Gates and Orthogonal Segment Projection*). Targets `PolylineProgressTracker.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-41.10 (`ATT-3266`, commits `e9e43fba`, `d480540c`). Discovered during route progress tracking on out-and-back courses at mid-latitudes, tracked in `ATT-4015`.
3. *Root Reason for Existing Formulation*: `ATT-3266` unified divergent climb and segment tracking logic using Euclidean 2D vector math that implicitly assumed spherical isotropy ($1^\circ \text{lat} = 1^\circ \text{lng}$) and unwindowed linear search. At $50^\circ\text{ N}$, $1^\circ \text{lng}$ is ~36% shorter, skewing gates to $114.5^\circ$ and causing lateral GPS jitter to snap to opposing legs.
4. *Preservation of Core Invariants*: Directional gate crossing via sign flips, 3D altitude/grade interpolation, smooth finish $\lambda$-blending, and pure JVM decoupling remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete at latitude $50^\circ\text{ N}$ moving along a diagonal polyline segment with lateral offset,
• *When* `projectLocation` is invoked,
• *Then* projection parameter $t$ evaluates using $\cos(\text{lat})$ equirectangular scaling, eliminating lateral projection distortion.
• *Given* a virtual gate created for a diagonal track at $50^\circ\text{ N}$,
• *When* `createGate` generates endpoints,
• *Then* the gate line is physically perpendicular ($90.0^\circ \pm 0.5^\circ$) to the track direction.
• *Given* an athlete on the outbound leg of an out-and-back route with `lastSegmentIndex` tracking outbound progress,
• *When* GPS jitter places the location closer to the return leg within corridor tolerance ($D_{\text{cross}} \le 50\text{m}$),
• *Then* `projectLocation` maintains forward continuity within $[i-2, i+10]$ and does NOT jump to the return leg.
• *Given* an athlete who deviates off-route ($D_{\text{cross}} > 50\text{m}$) and rejoins at a different point,
• *When* `projectLocation` evaluates the fix,
• *Then* the tracker falls back to global search and locks onto the new segment cleanly.
• *Given* existing callers passing `lastSegmentIndex = null`,
• *When* invoking `projectLocation`,
• *Then* the engine performs global search preserving 100% backward compatibility.

**Invariants**: Pure Kotlin JVM execution, 3D altitude/grade interpolation, $\lambda$-finish blending, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-NAV-056: Stateful Polyline Progress Tracking Continuity, lastSegmentIndex Propagation & Reset Lifecycle Invariants across Production Callers [`TST-NAV-056`]
- **Tests**: `TrackingElevationProfileResolver.kt`, `LiveSegmentsRepository.kt`, `LiveClimbsRepository.kt`
- **Implementation**: Enforce stateful lastSegmentIndex propagation and lifecycle reset rules across TrackingElevationProfileResolver, LiveSegmentsRepository, and LiveClimbsRepository to eliminate out-and-back route jumping in production.
- **Rationale**: The system SHALL propagate stateful polyline progress segment indices (`lastSegmentIndex: Int?`) into `PolylineProgressTracker.projectLocation` across all production tracking callers (`TrackingElevationProfileResolver`, `LiveSegmentsRepository`, and `LiveClimbsRepository`) to activate windowed forward continuity search and eliminate progress jumping across opposing legs on out-and-back and lollipop routes, AND SHALL enforce deterministic reset rules across navigation, segment, and climb lifecycle boundaries (ATT-4015, Implemented in: ATT-4015, Status: Verified, complementing `REQ-GEO-002` and `REQ-GEO-001`):
1. *Tracking Cockpit Elevation Profile Resolver Stateful Propagation (`TrackingElevationProfileResolver.kt`, `REQ-NAV-056.1`)*:
• `TrackingElevationProfileResolver.resolve` SHALL accept an optional `lastSegmentIndex: Int? = null` parameter.
• When projecting `currentLocation` onto `activeNavigatedRoute.path`, `resolve` SHALL pass `lastSegmentIndex` to `PolylineProgressTracker.projectLocation(activeNavigatedRoute.path, loc, lastSegmentIndex = lastSegmentIndex)`.
• `ResolvedElevationProfileData` SHALL expose the projected segment index: `val segmentIndex: Int? = null`.
• The stateful caller (e.g. `SensorGridScreen` / `TrackingViewModel`) SHALL maintain `lastSegmentIndex` across consecutive location fixes while navigating the active route.
• Reset Rule: `lastSegmentIndex` SHALL reset to `null` whenever `activeNavigatedRoute` changes, navigation stops/clears, or athlete deviates off-corridor ($D_{\text{cross}} > 50\text{m}$).
2. *Strava Live Segments Stateful Propagation (`LiveSegmentsRepository.kt`, `LiveSegmentMath`, `REQ-NAV-056.2`)*:
• `LiveSegmentMath` SHALL maintain stateful `lastSegmentIndex: Int? = null`.
• During active segment tracking (`LiveSegmentStatus.ON_SEGMENT` and `LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH`), `LiveSegmentsRepository` SHALL pass `liveSegment.math.lastSegmentIndex` into `PolylineProgressTracker.projectLocation(liveSegment.staticData.path, currentLocation, lastSegmentIndex = liveSegment.math.lastSegmentIndex)`.
• Upon receiving a valid projection, `LiveSegmentsRepository` SHALL update `liveSegment.math.lastSegmentIndex = projection.segmentIndex`.
• In the terminal `LiveSegmentStatus.FINISHED` check, `projectLocation` SHALL likewise pass `liveSegment.math.lastSegmentIndex`.
• Reset Rule: `lastSegmentIndex` SHALL reset to `null` whenever a segment transitions to `FAR_FAR_AWAY`, when segments are reconciled (`reconcileSegments`), or upon segment finish/abandonment. It SHALL be initialized cleanly when transitioning from `APPROACHING` to `ON_SEGMENT`.
3. *Live Climbs Telemetry Stateful Propagation (`LiveClimbsRepository.kt`, `LiveClimbData`, `REQ-NAV-056.3`)*:
• `LiveClimbData` SHALL maintain `lastSegmentIndex: Int? = null`.
• In `LiveClimbsRepository.updateClimbLiveProgress`, the system SHALL pass `current.lastSegmentIndex` into `PolylineProgressTracker.projectLocation(path, location, lastSegmentIndex = current.lastSegmentIndex)`.
• The returned `LiveClimbData` copy SHALL persist the updated segment index: `current.copy(..., lastSegmentIndex = projection.segmentIndex)`.
• Reset Rule: `lastSegmentIndex` SHALL reset to `null` when a climb completes (summit reached), on off-climb clear ($D_{\text{cross}} > 50\text{m}$), on climb tracking timeout, or when `activeNavigatedRouteId` changes during `refreshClimbs`.
4. *Preservation of System Invariants (`REQ-NAV-056.4`)*:
• Backward compatibility: Unseeded callers passing `lastSegmentIndex = null` SHALL retain global unwindowed search.
• Pure JVM mathematical decoupling: Zero Android SDK or SQLite dependencies in `PolylineProgressTracker`.
• Thread safety: Mutable state in `LiveSegmentsRepository` and `LiveClimbsRepository` SHALL remain synchronized and safe under concurrent GPS updates.
• Clean-room full test suite pass rate under `./gradlew testDebugUnitTest` SHALL remain 100%.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-NAV-056` under Epic `ATT-2564` (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*), complementing `REQ-GEO-002` (*Equirectangular Scaled Polyline Projection, Orthogonal Virtual Gates & Windowed Out-and-Back Route Progress Tracking*) and `REQ-GEO-001`. Targets `TrackingElevationProfileResolver.kt`, `LiveSegmentsRepository.kt`, and `LiveClimbsRepository.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-41.10 (`ATT-3266`) and Sprint 2026-43.6 (`ATT-4015`, commit `0da4472d`). Discovered during Final Review of `ATT-4015` where engine supported `lastSegmentIndex` windowing but all 3 production callers passed default `null` unwindowed.
3. *Root Reason for Existing Formulation*: `ATT-3266` introduced unified polyline progress tracking, but left caller integrations stateless (`lastSegmentIndex = null`). Consequently, GPS jitter caused elevation markers, segment PR matching, and climb telemetry to jump across opposing legs on out-and-back courses.
4. *Preservation of Core Invariants*: Seamless fallback to global search when unseeded (`null`) or off-corridor ($>50\text{m}$), thread-safe caller state encapsulation, 3D altitude/grade interpolation, and pure JVM unit test decoupling remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active navigated out-and-back route where outbound and return legs overlap within 50m,
• *When* `TrackingElevationProfileResolver.resolve` is called repeatedly with consecutive GPS fixes and propagating `lastSegmentIndex`,
• *Then* `alongTrackDist` and `segmentIndex` advance monotonically along the outbound leg without jumping to the return leg.
• *Given* an active navigated route being tracked with `lastSegmentIndex = 25`,
• *When* the user switches to a different active navigated route or clears navigation,
• *Then* `lastSegmentIndex` resets to `null`, preventing stale index contamination across different polylines.
• *Given* an athlete riding an out-and-back Strava Live Segment,
• *When* `LiveSegmentsRepository` processes location updates on the segment,
• *Then* `liveSegment.math.lastSegmentIndex` is propagated into `projectLocation` and updated sequentially, maintaining forward segment continuity.
• *Given* an active live segment tracking session that deviates $> 50\text{m}$ off-segment or finishes,
• *When* status transitions to `FAR_FAR_AWAY` or `FINISHED`,
• *Then* `liveSegment.math.lastSegmentIndex` is reset to `null`.
• *Given* a rider tackling a switchback/out-and-back Live Climb,
• *When* `LiveClimbsRepository.updateClimbLiveProgress` executes with previous `lastSegmentIndex`,
• *Then* `current.lastSegmentIndex` is passed to `projectLocation` and updated in `LiveClimbData`, preventing climb summit jumps.
• *Given* a completed or aborted climb,
• *When* climb tracking resets or route refreshes,
• *Then* `lastSegmentIndex` resets to `null`.

**Invariants**: Backward compatibility for stateless callers (`null`), pure Kotlin JVM decoupling, thread safety, 100% clean-room test pass rate.

### REQ-MAP-056: Line-Density Polyline Heatmap Tile Provider & 256-Entry Thermal Color LUT [Verified]
- **Tests**: `PolylineHeatmapTileProvider.kt`, `MapUtils.kt`, `MapContentScope.kt`, `PolylineHeatmapTileProviderTest.kt`
- **Implementation**: Replace Google Maps Gaussian point-cloud HeatmapTileProvider with custom Web Mercator line-density PolylineHeatmapTileProvider and 256-entry thermal color lookup table (LUT).
- **Rationale**: The system SHALL replace Gaussian point-cloud heatmap tile rendering with a true line-density tile provider (`PolylineHeatmapTileProvider`) and thermal color LUT across all map views (ATT-4480, Parent Epic ATT-4474, Status: Verified, refining `REQ-MAP-002` and `REQ-MAP-016`):
1. *Web Mercator Line-Raster TileProvider (`PolylineHeatmapTileProvider.kt`, `REQ-MAP-056.1`)*:
• `PolylineHeatmapTileProvider` SHALL implement `com.google.android.gms.maps.model.TileProvider`.
• For each requested tile $(x, y, \text{zoom})$, candidate polylines SHALL be filtered in $O(1)$ time using bounding-box intersection against the tile's geographic coordinates $[lat_{\min}, lon_{\min}, lat_{\max}, lon_{\max}]$ with a stroke margin.
• If no paths intersect the tile bounding box, `getTile` SHALL return `TileProvider.NO_TILE` immediately without allocating bitmaps.
• Intersecting polylines SHALL be projected into Web Mercator tile pixel coordinates ($256 \times 256$) and rasterized using `android.graphics.Canvas`, `Path`, and additive blending (`PorterDuff.Mode.ADD`) to accumulate 1D line density without vertex spacing or speed bias.
2. *256-Entry Thermal Color Lookup Table & Dynamic Range (`REQ-MAP-056.2`)*:
• Accumulated single-channel intensity values SHALL be mapped to 32-bit ARGB pixels using a precomputed 256-entry thermal Color LUT spanning from cool electric blue/violet (1 pass) through magenta/crimson (moderate) and fiery orange/amber to incandescent yellow/white-hot (maximum passes).
• The provider SHALL calibrate intensity scaling ($\Delta I$) based on dataset size and zoom level to ensure full dynamic range utilization across both small and large workout collections.
3. *Thread Safety & Memory Headroom (`REQ-MAP-056.3`)*:
• `getTile(x, y, zoom)` SHALL be completely thread-safe across concurrent tile fetch worker threads.
• The provider SHALL eliminate synthetic point interpolation (`densifyPath`) and `WeightedLatLng` allocations.
4. *Preservation of System Invariants (`REQ-MAP-056.4`)*:
• Zero alterations to database schemas or workout telemetry recording.
• Compatibility with standard Google Maps `TileOverlay` composable across Light, Dark, and AMOLED map themes.
• Clean-room full test suite pass rate under `./gradlew testDebugUnitTest` SHALL remain 100%.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-MAP-002` (*Training heatmaps (Cyan -> Indigo gradient)*) and `REQ-MAP-016` (*Heatmap DSL Support*) under Epic `ATT-4474`, targeting `MapUtils.kt`, `MapContentScope.kt`, and `PolylineHeatmapTileProvider.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.16 (ATT-500, ATT-504) and Sprint 2026-43.9 (commit `06990c88`, ATT-4594).
3. *Root Reason for Existing Formulation*: Google Maps `HeatmapTileProvider` was originally adopted as an off-the-shelf component to provide density visualization without writing custom Web Mercator rasterization shaders. Because it evaluated 2D Gaussian point-cloud KDE, it generated artificial heat blobs on slow climbs and corners while requiring synthetic point densification.
4. *Preservation of Core Invariants*: Standard Google Maps `TileProvider` interface compatibility, zoom-adaptive rendering, non-blocking asynchronous tile delivery, and legible underlying map labels/terrain remain 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout polyline traversed at varying speeds (fast descent vs. slow climb),
• *When* rendered by `PolylineHeatmapTileProvider`,
• *Then* the rendered intensity along the path SHALL remain uniform regardless of GPS point density or instantaneous speed.
• *Given* a requested tile coordinate $(x, y, zoom)$ with no intersecting workout paths,
• *When* `getTile` executes,
• *Then* the provider SHALL return `TileProvider.NO_TILE` immediately without allocating bitmaps.
• *Given* overlapping paths traversed 1 time, 5 times, and 20+ times,
• *When* the heatmap tile is generated,
• *Then* the thermal color LUT SHALL map each traversal level to visually distinct hues from cool electric blue to incandescent yellow-white.

**Invariants**: Zero changes to SQLite databases, thread-safe concurrent execution, zero main-thread jank, and 100% clean-room unit test pass rate.
