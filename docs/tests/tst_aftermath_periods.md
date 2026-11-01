# Living Test Specification: Post-Workout Analytics & Periods

**Domain**: `aftermath_periods`  
**Prefixes**: `PER`, `PERF`  
**Scope**: Post-workout analytics, period statistics, zone distributions, graphs and visual scrubbers.  
**Total Tests**: 32

---

### TST-PERF-001: **Heatmap Memory Scalability** [The application SHALL NOT crash with an `OutOfMemoryError`. Map rendering MUST remain responsive.]
- **Requirement(s)**: `REQ-MAP-017`
- **Component**: **Heatmap Memory Scalability**
- **Jira**: ``ATT-319``
- **Test Description**: 1. Import a large volume of workouts (e.g. 500+). 2. Navigate to the 'Periods' view and scroll through Yearly/Monthly summaries.

### TST-PERF-002: **Cluster Detail Loading Speed** [UI appears and becomes interactive in < 1 second. Heatmap preparation should not block the main thread.]
- **Requirement(s)**: `REQ-PER-001`
- **Component**: **Cluster Detail Loading Speed**
- **Jira**: ``ATT-362``
- **Test Description**: 1. Identify a cluster with > 30 recordings. 2. Tap on the cluster to open its details. 3. Measure time from tap to full heatmap/list display.

### TST-PERF-003: **Initial Cluster Loading Progress** [1. A progress card following the 'Migration Status' style appears immediately. 2. It shows granular messages like \"Self-healing hit counts...\" or \"Loading family X of Y...\".]
- **Requirement(s)**: `REQ-PER-003`
- **Component**: **Initial Cluster Loading Progress**
- **Jira**: ``ATT-366``
- **Test Description**: 1. Clear application cache or significantly modify history. 2. Navigate to 'My Locations'.

### TST-PERF-004: **Incremental Period Visibility** [Each month's period summaries SHALL appear in the UI immediately after its background processing phase completes, before the total migration is finished.]
- **Requirement(s)**: `REQ-PER-001`
- **Component**: **Incremental Period Visibility**
- **Jira**: ``ATT-374``
- **Test Description**: 1. Trigger a full hierarchical Periods sync. 2. Observe the list as the "Syncing [Month]..." progress updates.

### TST-PERF-005: **Dual-Phase Sync Efficiency** [1. The status SHALL transition from 'Reading...' to 'Syncing...' without jumping back. 2. The period list SHALL grow visibly after each 'Syncing [Month]' update. 3. Scrolling SHALL remain smooth throughout the sync.]
- **Requirement(s)**: `REQ-PER-004`
- **Component**: **Dual-Phase Sync Efficiency**
- **Jira**: ``ATT-380``
- **Test Description**: 1. Trigger a full hierarchical Periods sync. 2. Observe the status transitions and UI responsiveness.

### TST-PERF-006: **Multi-Phase Progress Feedback** [1. A bold title ('Introducing Periods Database...') SHALL be visible. 2. Status messages during the reading phase SHALL be prefixed with 'Phase 1:'. 3. Status messages during the aggregation phase SHALL be prefixed with 'Phase 2:'.]
- **Requirement(s)**: `REQ-PER-005`
- **Component**: **Multi-Phase Progress Feedback**
- **Jira**: ``ATT-383``
- **Test Description**: 1. Trigger a Periods sync. 2. Inspect the progress card UI.

### TST-PERF-007: **Workout Cluster Migration Progress** [1. The progress card SHALL show 'Phase 1: Processing routes...' and 'Phase 2: Processing workout X of Y...'. 2. Progress bars SHALL update accurately.]
- **Requirement(s)**: `REQ-PER-006`
- **Component**: **Workout Cluster Migration Progress**
- **Jira**: ``ATT-393``
- **Test Description**: 1. Trigger a Workout Cluster recalculation. 2. Observe the progress card.

### TST-PERF-008: **Reactive Period Detail Verification** [All workouts belonging to the period SHALL eventually appear on the map as they are loaded from the database.]
- **Requirement(s)**: `REQ-PER-007`
- **Component**: **Reactive Period Detail Verification**
- **Jira**: ``ATT-442``
- **Test Description**: 1. Open the 'Periods' screen. 2. Tap the map icon for a period *immediately* before the background loading completes. 3. Observe the heatmap as it grows.

### TST-PERF-009: **Silent Cluster Refresh** [Notification only shown when necessary.]
- **Requirement(s)**: `REQ-PER-006`
- **Component**: **Silent Cluster Refresh**
- **Jira**: ``ATT-441``
- **Test Description**: 1. Navigate to My Locations. 2. Navigate away and back. 3. Verify no progress notification on 2nd visit. 4. Trigger recalculation and verify progress DOES appear.

### TST-PERF-010: **Persistent Previews Audit** [List appears instantly with previews; NO progress UI shown on restart.]
- **Requirement(s)**: `REQ-PER-006`
- **Component**: **Persistent Previews Audit**
- **Jira**: ``ATT-441``
- **Test Description**: 1. Navigate to My Locations. 2. Wait for enrichment. 3. Kill and restart app. 4. Navigate back to My Locations.

### TST-PER-009: **Precise Sport Filtering** [List is correctly filtered by specific Sport ID.]
- **Requirement(s)**: `REQ-PER-008`
- **Component**: **Precise Sport Filtering**
- **Jira**: ``ATT-459``
- **Test Description**: 1. Open a Period Summary. 2. Click a Sub-Sport row (e.g. MTB). 3. Verify the resulting list only contains MTB workouts for that period.

### TST-PER-010: **Heatmap Toggle Removal** [Toggle is removed, global consistency maintained.]
- **Requirement(s)**: `REQ-UI-120`
- **Component**: **Heatmap Toggle Removal**
- **Jira**: ``ATT-460``
- **Test Description**: 1. Open Periods screen and Period Map. 2. Verify `Whatshot` toggle is gone. 3. Verify heatmap still reacts to global settings.

### TST-PER-012: **Period Map Sport Filtering** [Map content is correctly filtered by sport selection.]
- **Requirement(s)**: `REQ-PER-009`
- **Component**: **Period Map Sport Filtering**
- **Jira**: ``ATT-466``
- **Test Description**: 1. Open Period Detail Map. 2. Tap a Sport Row (e.g., Bike). 3. Verify map only shows Bike tracks and markers. 4. Verify heatmap only reflects Bike activities.

### TST-PER-013: **Uniform Heatmap & Visual Transparency** [Heatmap is complete across all workouts and is visually translucent rather than opaque.]
- **Requirement(s)**: `REQ-PER-010`
- **Component**: **Uniform Heatmap & Visual Transparency**
- **Jira**: ``ATT-531``
- **Test Description**: 1. Open a Period summary (Month or Year) with > 20 workouts. 2. Verify that ALL workouts across the entire period contribute to the heatmap without geographic truncation. 3. Inspect the heatmap overlay over satellite/terrain maps and verify roads and landmarks remain legible through the translucent gradient.

### TST-PER-014: **Longest Workout Metrics & Period Header** [The Longest Workout highlight displays the workout's name, active duration, total distance, and total ascent. Period headers display total duration and workout count.]
- **Requirement(s)**: `REQ-PER-011`
- **Component**: **Longest Workout Metrics & Period Header**
- **Jira**: ``ATT-532``
- **Test Description**: 1. Open the Period summary list. 2. Verify Period Card headers display duration and workout count. 3. Expand a sport summary row to inspect the Longest Workout highlight.

### TST-PER-015: **Period Longest Workout Metric Integrity & Cross-Season Week Keys** [Weekly summaries display real metrics for the longest workout; winter weeks align with workout ISO week sort keys; no empty/zero metric rows appear.]
- **Requirement(s)**: `REQ-PER-011`
- **Component**: **Period Longest Workout Metric Integrity & Cross-Season Week Keys**
- **Jira**: ``ATT-536``
- **Test Description**: 1. Execute `PeriodsTemporalKeyTest`. 2. Verify week start calculations across winter/summer DST transitions. 3. Verify real metrics hydrated for weekly summaries in UI without zero fallthrough.

### TST-PER-016: **Multi-Workout Period Longest Workout Selection Integrity** [The period highlight reliably displays the true longest workout among all workouts of the period.]
- **Requirement(s)**: `REQ-PER-011`
- **Component**: **Multi-Workout Period Longest Workout Selection Integrity**
- **Jira**: ``ATT-579``
- **Test Description**: 1. Execute `PeriodsAggregationTest`. 2. Verify that in multi-workout periods (such as January 2015), the workout with the true maximum duration and distance (day 31) is selected as longest workout regardless of stale database IDs.

### TST-PER-017: **Period Empty State Placeholder Verification** [The screen renders an EmptyStatePlaceholder component informing the user that no period summaries are available, rather than displaying an empty blank viewport.]
- **Requirement(s)**: `REQ-UI-125`
- **Component**: **Period Empty State Placeholder Verification**
- **Jira**: ``ATT-488``
- **Test Description**: 1. Open the application with no recorded workouts or filter a sport type with 0 workouts. 2. Navigate to the 'Periods' screen (Woche, Monat, Jahr). 3. Observe the viewport below the period tabs and header when no period summaries exist. 4. Verify that an informative `EmptyStatePlaceholder` is displayed with an appropriate calendar/history icon, clear title ("No period summaries available" / "Keine Zeiträume verfügbar"), and supportive description. 5. Verify proper layout insets (collapsible header offset and bottom navigation bar insets). 6. Verify 9-language localization parity.

### TST-PER-018: **Period Map Scalability, Memory Budget & Adaptive Layering Invariant** [The period detail map renders smoothly without OOM crashes, ANR freezes, or unbounded polyline/marker allocations across periods of any size.]
- **Requirement(s)**: `REQ-PER-012`
- **Component**: **Period Map Scalability, Memory Budget & Adaptive Layering Invariant**
- **Jira**: ``ATT-1107``
- **Test Description**: 1. Verify `AndroidManifest.xml` declares `android:largeHeap="true"`. 2. Execute unit tests verifying that when a period contains > 30 workouts, individual member vector tracks (`memberTracks`) and member markers (`memberMarkers`) are suppressed, while aggregate `heatmapPaths` and `anchorTracks` remain active. 3. Verify that when workouts <= 30 (or filtered by sport to <= 30), member tracks and markers are fully populated. 4. Verify that tapping/peeking any individual session within a high-volume period populates `peekedTracks` with a single focused vector line without background vector explosion.

### TST-PER-019: **Multi-Region Period Map Viewport Partitioning & Navigation Verification** [Viewport centers tightly on the athlete's primary training grounds; ocean tiles and Greenland anomaly are eliminated; region switching is seamless and responsive; single-region periods retain 100% parity; all unit and regression tests pass.]
- **Requirement(s)**: `REQ-PER-013`
- **Component**: **Multi-Region Period Map Viewport Partitioning & Navigation Verification**
- **Jira**: ``ATT-1151` / `ATT-1221``
- **Test Description**:
  1. *Unit Test Single-Region Fast-Path & Invariance*:
  • Provide a list of workouts within a local geographic territory (e.g. within 30 km to 50 km diagonal span).
  • Verify `SpatialRegionEngine.detectRegions()` returns exactly 1 region whose bounds match the global coordinate extrema.
  • Verify camera bounds in `InteractivePeriodMap` and `PeriodSummaryCard` are 100% identical to legacy calculations.
  2. *Unit Test Multi-Continent Partitioning & Dominant Ranking*:
  • Provide a period with 100 workouts in Europe (~9°E) and 10 workouts in the USA (~-122°W).
  • Verify `SpatialRegionEngine.detectRegions()` partitions workouts into exactly 2 distinct regions.
  • Verify European region is ranked as primary (`isPrimary = true`, index 0) based on workout frequency.
  • Verify secondary tie-breaking logic (distance, recency) when workout counts are equal.
  • Verify European bounds tightly enclose European workouts and USA bounds tightly enclose USA workouts, with the North Atlantic Ocean completely excluded.
  3. *Unit Test Antimeridian Crossing Normalization*:
  • Provide workouts spanning across the $180^\circ$ meridian (e.g., $+179^\circ$ and $-179^\circ$, with $2^\circ$ geodesic separation).
  • Verify `SpatialRegionEngine` merges workouts into a single connected region and constructs wrapped `LatLngBounds(southwest, northeast)` where `southwest.longitude > northeast.longitude` without constructing an erroneous $358^\circ$ world-spanning box.
  4. *Unit Test Fast-Path Boundary Sensitivity*:
  • Test boundary workouts spanning $45\text{ km}$ diagonal envelope (assert fast-path returns 1 region).
  • Test workouts separated by $110\text{ km}$ ($> \text{REGION\_CLUSTER\_THRESHOLD\_METERS} = 75\text{ km}$) without intermediate bridging (assert 2 distinct regions are returned).
  5. *Unit Test Non-GPS & Corrupted Coordinate Resilience*:
  • Provide workouts with null coordinates, empty polylines, or `(0.0, 0.0)` sentinel coordinates.
  • Verify engine bypasses non-GPS workouts during clustering without NPE or crash while preserving workout metrics.
  6. *Unit Test `PeriodsViewModel` State Persistence & Threading*:
  • Verify `SpatialRegionEngine.detectRegions()` executes off the main thread (`Dispatchers.Default`).
  • Verify `selectedRegionId` is saved and restored via `SavedStateHandle` across configuration changes.
  7. *Compose UI Interaction & Overflow Verification*:
  • Verify `PeriodMapScreen` renders horizontally scrollable chip bar (`Modifier.horizontalScroll(rememberScrollState())`) when `regions.size > 1` and hides the chip bar completely when `regions.size <= 1`.
  • Verify clicking a region chip updates `selectedRegionId` and animates the camera to that region's bounds.
  • Verify clicking \"All Regions\" animates camera to global aggregate bounds.
  8. *Localization Parity Audit*:
  • Verify all new region chip text strings are defined across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  9. *Clean-Room Regression*:
  • Execute `./gradlew testDebugUnitTest` with 0 failures and 0 regressions.

### TST-PER-020: **Immediate Period View Reactive Synchronization on Workout Finalization Verification** [Newly finished workouts on clean installs or initial profiles immediately propagate to Day/Week/Month/Year period views without app restart or manual refresh; migration concurrency is mutex-safe; unfinalized workouts excluded; 100% test pass rate.]
- **Requirement(s)**: `REQ-PER-014`
- **Component**: **Immediate Period View Reactive Synchronization on Workout Finalization Verification**
- **Jira**: ``ATT-3056``
- **Test Description**:
  1. *Fresh Install Direct Broadcast Reception & Period Emission Unit Test (`PeriodsRepositorySyncTest.kt`)*:
  • Initialize `PeriodsRepository` on an empty database (`isSyncFinished == false`, 0 workouts).
  • Verify initial `_groupedPeriods.value` contains empty period lists.
  • Simulate `TrackerService` broadcasting `TRACKING_FINISHED_INTENT` with valid `workoutId`.
  • Verify `PeriodsRepository` processes broadcast, executes `onWorkoutFinished()`, updates `PeriodSummaries.db`, marks `isSyncFinished(true)`, and immediately emits updated Day and Week periods into `groupedPeriods`..
  2. *Concurrency & Mutex Serialization Test (`PeriodsRepositorySyncTest.kt`)*:
  • Invoke `onWorkoutFinished()` concurrently while `performHierarchicalMigration()` is running.
  • Verify execution is serialized via `rebuildMutex.withLock` and no records are wiped or corrupted..
  3. *Unfinished Workout Exclusion Query Test (`WorkoutSummariesDatabaseManagerTest.java` / `PeriodsRepositorySyncTest.kt`)*:
  • Insert an in-progress workout with `FINISHED = 0`.
  • Verify `getWorkoutsInRangeCursor()` / `fetchWorkoutsInDay()` strictly excludes it from period rollups..
  4. *PeriodsViewModel Active Reconcile Test (`PeriodsViewModelTest.kt`)*:
  • Verify `loadPeriods()` triggers `periodsRepo.checkIntegrityAndSync()`..
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-PER-021: **Calendar-Aware Period Aggregation Boundaries, Sentinel Epoch Symmetry & Local-Day PMC Bucketing Verification Suite** [Calendar-aware local timezone period aggregation boundaries across DST transitions, sentinel epoch symmetry in WorkoutDataMapper, and local-day PMC bucketing in PowerPhysiologyCalculator verified across unit tests and full clean-room suite.]
- **Requirement(s)**: `REQ-PER-015`, `REQ-PER-014`, `REQ-UI-401`
- **Component**: **Calendar-Aware Period Aggregation Boundaries, Sentinel Epoch Symmetry & Local-Day PMC Bucketing Verification Suite**
- **Jira**: ``ATT-4016``
- **Test Description**:
  1. *Calendar-Aware DST Day and Week Boundary Unit Tests (`TST-PER-021.1`, `PeriodsAggregationDstTest.kt` / `PeriodsRepositoryTest.kt`)*:
  • In `Europe/Berlin`, verify 23h spring-forward Sunday (e.g. 2026-03-29) ends at `23:59:59` (82,799s length) and does not overshoot into Monday.
  • In `Europe/Berlin`, verify 25h fall-back Sunday (e.g. 2026-10-25) ends at `23:59:59` (89,999s length) and includes late workouts recorded between 23:00 and 23:59:59.
  • Verify week range queries in spring-forward (167h) and fall-back (169h) weeks match exact calendar Monday 00:00:00 to Sunday 23:59:59.
  2. *WorkoutDataMapper Epoch-Zero Sentinel & Invalid Timestamp Gating Unit Tests (`TST-PER-021.2`, `WorkoutDataMapperTest.kt`)*:
  • Verify `formatDateTime` on malformed date string returns `timestampS = 0L` and `localDateTime = LocalDateTime.ofInstant(Instant.EPOCH, zoneId)`.
  • Verify `PeriodsRepository` ignores workouts with `timestampS <= 0L` during aggregation, preventing period pollution.
  3. *Local-Timezone PMC Day Bucketing Unit Tests (`TST-PER-021.3`, `PowerPhysiologyCalculatorTest.kt`)*:
  • Verify `computePerformanceManagementChart` with `ZoneId.of("Europe/Berlin")` places sample at 2026-07-15 00:30 local (2026-07-14 22:30 UTC) into local day 2026-07-15.
  • Verify `computePerformanceManagementChart` with `ZoneId.of("America/New_York")` places sample at 2026-07-15 21:00 local (2026-07-16 01:00 UTC) into local day 2026-07-15.
  • Verify default parameter `zoneId = ZoneId.systemDefault()` maintains full backward compatibility for existing callers.
  4. *Clean-Room Regression Suite Execution (`TST-PER-021.4`)*:
  • Execute targeted unit tests and `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-PER-022: **Period Rebuild Flow Projection, Single-Owner Tracking Finalization Orchestration & Shared Mapper Instance Verification Suite** [Redundant period rebuilds eliminated on non-period workout updates and progressive loading chunks, single-owner tracking finalization ensures exactly one onWorkoutFinished execution per finished session, WorkoutDataMapper allocation consolidated into a single reusable instance, and 100% unit tests pass.]
- **Requirement(s)**: `REQ-PER-016`, `REQ-PER-014`, `REQ-STB-018`
- **Component**: **Period Rebuild Flow Projection, Single-Owner Tracking Finalization Orchestration & Shared Mapper Instance Verification Suite**
- **Jira**: ``ATT-4019``
- **Test Description**:
  1. *Period Flow Projection & Deduplication Unit Tests (`TST-PER-022.1`, `PeriodsRepositoryFlowTest.kt` / `PeriodsRepositorySyncTest.kt`)*:
  • Emit workout updates modifying solely non-period fields (e.g. `notes`, `title`, `stravaStatus`, `fileExportStatus`); verify `PeriodsRepository` skips collection and `loadFromDatabase()` is not triggered.
  • Emit 50-item progressive streaming chunks or rapid emissions; verify period rebuild is debounced/deduplicated rather than re-querying SQLite 4 times per chunk.
  2. *Single-Owner Tracking Finalization Orchestration Unit Tests (`TST-PER-022.2`, `PeriodsRepositorySyncTest.kt`)*:
  • Verify `WorkoutRepository` invokes `PeriodsRepository.onWorkoutFinished(workoutData)` exactly once upon workout completion.
  • Verify direct dual `BroadcastReceiver` registrations (`LocalBroadcastManager` + `ContextCompat.registerReceiver`) are removed from `PeriodsRepository`, eliminating duplicate onReceive triggers.
  • Verify `PeriodsRepository.onWorkoutFinished` idempotency guard prevents double processing when identical workout ID is supplied consecutively.
  3. *Shared WorkoutDataMapper Instance Invariant Tests (`TST-PER-022.3`, `PeriodsRepositoryMapperTest.kt`)*:
  • Verify `PeriodsRepository` uses a single shared/reusable `WorkoutDataMapper` instance across all cursor mapping routines instead of allocating ad-hoc instances and redundant DB helper singletons.
  4. *Clean-Room Regression Suite Execution (`TST-PER-022.4`)*:
  • Execute targeted unit tests and `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-PER-023: **Period Line-Density Heatmap Visuals & Allocation-Free Tile Rendering Verification Suite** [PeriodMapVisuals heatmapProvider integrates PolylineHeatmapTileProvider; Day periods emit null provider; Week, Month, and Year periods instantiate calibrated line-density tile providers without densifyPath or heap point caps; underlying map labels remain legible; 100% clean-room unit test pass rate.]
- **Requirement(s)**: `REQ-PER-017`
- **Component**: **Period Heatmap Visuals**
- **Jira**: `ATT-4480`
- **Test Description**:
  1. Verify in `PeriodMapVisualsTest` that `getPeriodMapVisuals(PeriodType.DAY, ...)` returns `heatmapProvider = null`.
  2. Verify that `PeriodType.WEEK`, `MONTH`, and `YEAR` return non-null `PolylineHeatmapTileProvider` instances with calibrated opacity.
  3. Verify that empty path input returns `heatmapProvider = null`.
  4. Run targeted unit test suite `com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*` verifying 100% pass rate.

### TST-PER-024: **PeriodBarGraph Timeline Gap Interpolation, Sub-Hour Formatting, Sport Stacking & Metric Mode Verification Suite** [PeriodBarGraph correctly interpolates timeline rest gaps, renders subtle baseline ticks for zero-volume intervals, formats sub-hour durations cleanly (<1m, 45m, 1.5h), stacks bars by BSportType, switches metrics across Duration/Distance/Ascent, and handles empty/single lists gracefully without crashing; 100% clean-room test pass rate.]
- **Requirement(s)**: `REQ-PER-018`
- **Component**: **Period Timeline Bar Graph**
- **Jira**: `ATT-4478`
- **Test Description**:
  1. *Timeline Gap Interpolation Unit Tests (`TST-PER-024.1`, `PeriodBarGraphTest.kt`)*:
  • Given non-consecutive periods (e.g. Day 1 and Day 4 with Day 2-3 missing), verify continuous timeline generation inserts zero-volume rest buckets for Day 2 and Day 3.
  • Verify zero-volume rest items have 0 metric values and render with subtle baseline tick height (2 dp).
  2. *Sub-Hour Duration & Value Formatting Tests (`TST-PER-024.2`, `PeriodBarGraphTest.kt`)*:
  • Verify duration `< 60s` formats as `<1m`.
  • Verify duration `45 * 60` (45 min) formats as `45m` (not empty, not `0h`).
  • Verify duration `90 * 60` (1.5 h) formats as `1.5h`.
  • Verify duration `12 * 3600` (12 h) formats as `12h`.
  • Verify distance and ascent metric value formatting for compact display.
  3. *Metric Switching & Proportional Scaling Tests (`TST-PER-024.3`, `PeriodBarGraphTest.kt`)*:
  • Verify bar height fractions scale dynamically relative to max duration in Duration mode, max distance in Distance mode, and max ascent in Ascent mode.
  4. *Sport Breakdown Proportions Tests (`TST-PER-024.4`, `PeriodBarGraphTest.kt`)*:
  • For periods with multiple sports (BIKE, RUN, OTHER), verify stacked segment height fractions match sport proportional contributions.
  5. *Empty & Boundary Protection Tests (`TST-PER-024.5`, `PeriodBarGraphTest.kt`)*:
  • Verify empty period list renders nothing without crashing.
  • Verify single period list renders correctly with height fraction 1.0f.
  6. *Clean-Room Regression Suite Execution (`TST-PER-024.6`)*:
  • Run targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"` verifying 100% pass rate.

### TST-PER-025: **PeriodCardMapState Background Pipeline, Douglas-Peucker Decimation & Full-Period Route Coverage Verification Suite** [PeriodCardMapState offloads polyline decoding, Douglas-Peucker decimation, and region detection to Dispatchers.Default; periods with > 8 workouts include 100% of member routes without 8-track truncation; vertex counts are bounded to <= 2,000 points; Week/Month/Year cards render full regional heatmap tile providers; empty GPS lists degrade gracefully; 100% clean-room test pass rate.]
- **Requirement(s)**: `REQ-PER-019`, `REQ-PER-017`, `REQ-PER-013`, `REQ-PER-012`
- **Component**: **PeriodCardMapState Pipeline**
- **Jira**: `ATT-4477`
- **Test Description**:
  1. *Full-Period Route Ingestion & Truncation Elimination Tests (`TST-PER-025.1`, `PeriodCardMapStateTest.kt`)*:
  • Given a Month period with 25 workouts and a Year period with 100 workouts, verify that `buildPeriodCardMapState` processes all available polylines instead of truncating to `MAX_SUMMARY_THUMBNAIL_TRACKS = 8`.
  • Verify that `primaryRegionPathsCount` reflects the total routes in the primary region.
  2. *Douglas-Peucker Adaptive Decimation Tests (`TST-PER-025.2`, `PeriodCardMapStateTest.kt`)*:
  • Verify that simplification tolerance scales by period type: `DAY` (5.0m), `WEEK` (15.0m), `MONTH` (35.0m), and `YEAR` (75.0m).
  • Verify that unsimplified polylines with 1,000+ raw GPS coordinates are decimated to bounded vertex counts while preserving start, apex, and boundary extrema.
  3. *Spatial Region Clustering & Framing Offload Tests (`TST-PER-025.3`, `PeriodCardMapStateTest.kt`)*:
  • For multi-region periods (e.g. Europe and California), verify that `PeriodCardMapState` identifies multiple regions, designates the dominant cluster as primary (`isPrimary = true`), and bounds the camera tightly to the primary region envelope.
  • Verify that single-region periods return a single region envelope without cross-continental camera centering.
  4. *PolylineHeatmapTileProvider Integration Tests (`TST-PER-025.4`, `PeriodCardMapStateTest.kt`)*:
  • Verify that for `WEEK`, `MONTH`, and `YEAR` cards, `PeriodCardMapState.visuals.heatmapProvider` is an active non-null `PolylineHeatmapTileProvider` containing all simplified primary region paths.
  • Verify that for `DAY` cards, `heatmapProvider` is `null`.
  5. *Empty & Malformed GPS Resilience Tests (`TST-PER-025.5`, `PeriodCardMapStateTest.kt`)*:
  • Verify that empty polyline maps or empty lists return `isReady = true`, `primaryPaths = emptyList()`, and `bounds = null` without throwing exceptions.
  • Verify that malformed encoded strings are skipped safely without crashing the background worker.
  6. *Clean-Room Regression Suite Execution (`TST-PER-025.6`)*:
  • Execute targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"` verifying 100% pass rate.
  7. *Adaptive Vector Polyline Suppression & Source Priority Tests (`TST-PER-025.7`, `PeriodCardMapStateTest.kt`)*:
  • Given a summary with $> 30$ workouts and a non-null heatmap provider, verify that `PeriodCardMapState.renderVectorPolylines` evaluates to `false`.
  • Given a summary with $\le 30$ workouts (e.g. 1, 5, 20, 30 workouts) and a non-null heatmap provider, verify that `PeriodCardMapState.renderVectorPolylines` evaluates to `true`.
  • Given a Day summary (`PeriodType.DAY`) with any number of workouts (null heatmap provider), verify that `PeriodCardMapState.renderVectorPolylines` evaluates to `true`.
  • Given a summary with both `summary.polylines` and `summary.workoutIdToPolylineMap` populated, verify that `prepareCardMapPreviewSync` prioritizes `summary.polylines`.

### TST-PER-026: **Self-Contained Period Persistence, Zero-N+1 Batch Loading & Bidirectional Sync Healing Verification Suite** [getPeriodsByType executes strictly 3 batch queries without tempId loop; PeriodSportStats persists complete LongestWorkout metadata with DB_VERSION 27 and onCreate/onUpgrade parity; checkIntegrityAndSync heals out-of-band deletions (!=); legacy DB file checks execute strictly once; 100% clean-room unit test pass rate.]
- **Requirement(s)**: `REQ-PER-020`, `REQ-MIG-026`, `REQ-PER-011`
- **Component**: **Period Summaries Database & Repository Sync**
- **Jira**: `ATT-4476`
- **Test Description**:
  1. *Zero-N+1 Batch Query Budget Tests (`TST-PER-026.1`, `PeriodSummariesDatabaseManagerTest.kt`)*:
  • Given multiple periods with multiple sports and detailed stats, verify `getPeriodsByType()` executes strictly 3 batch queries total and populates complete `SportStats.longestWorkout` and `detailedStatsMap` without issuing per-row queries.
  2. *Self-Contained LongestWorkout Persistence Tests (`TST-PER-026.2`, `PeriodSummariesDatabaseManagerTest.kt`)*:
  • Save a period with complete `LongestWorkout` metadata (`name`, `duration`, `distanceMeters`, `ascentMeters`).
  • Read the period back from SQLite and assert that `longestWorkout` is fully populated without requiring `WorkoutRepository` in RAM.
  3. *SQLite v26 to v27 Migration Parity Contract Tests (`TST-PER-026.3`, `PeriodSummariesMigrationTest.kt`)*:
  • Create a v26 database populated with sample periods and sport stats.
  • Execute `onUpgrade` to v27; assert table schema (column names, types, default values) is 100% identical to fresh `onCreate` v27 database (Coding Standards §2.1, §2.2, §2.3, Rule 64).
  • Assert pre-existing data in v26 remains intact with default values applied to new columns.
  4. *Bidirectional Deletion Integrity Healing Tests (`TST-PER-026.4`, `PeriodsRepositorySyncTest.kt`)*:
  • Given `finishedWorkouts < periodWorkouts` (simulating workout deletion), assert `checkIntegrityAndSync()` triggers resynchronization.
  • Verify that when `finishedWorkouts == periodWorkouts`, no resynchronization is triggered.
  5. *Gated Legacy File Cleanup Tests (`TST-PER-026.5`, `PeriodSummariesDatabaseManagerTest.kt`)*:
  • Assert that when `legacy_period_dbs_cleaned` preference is false, legacy DB file check executes and sets preference to true.
  • Assert that on subsequent initializations with preference true, file scans are bypassed.
  6. *Clean-Room Regression Suite Execution (`TST-PER-026.6`)*:
  • Execute targeted unit tests and `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-PER-027: **InteractivePeriodMap UI-Thread Decoupling, Deduplication & Large-Period Spatial Hit-Testing Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PER-021`, `REQ-PER-012`, `REQ-PER-013`
- **Component**: **Interactive Period Map & Spatial Hit-Testing**
- **Jira**: `ATT-4475`
- **Test Description**:
  1. *Spatial Anchor Scope Isolation Tests (`TST-PER-027.1`)*:
  • Given a period with 25 workouts, verify `PeriodsRepository.enrich()` restricts `summary.workoutIdToPolylineMap` strictly to the $\le 5$ spatial anchor workouts (North, South, East, West, Longest).
  2. *Interactive Map Track Deduplication Tests (`TST-PER-027.2`)*:
  • Given `mapState.isLoading == false`, verify `InteractivePeriodMap` suppresses or deduplicates `anchorTracks` so member tracks are rendered exactly once.
  3. *Zero-UI-Thread Polyline Decoding Tests (`TST-PER-027.3`)*:
  • Verify that polyline decoding for member and anchor tracks is dispatched exclusively on `Dispatchers.Default` and not executed synchronously within Compose `remember`.
  4. *Large-Period Route Visualization & Spatial Tap Hit-Testing Tests (`TST-PER-027.4`)*:
  • Given a period with $> 30$ workouts (e.g. 50 workouts), verify routes are visualized via `PolylineHeatmapTileProvider` without instantiating 50+ Google Maps `Polyline` vector objects.
  • Given a map tap at coordinates near a workout path, verify spatial hit-testing (`PolyUtil.isLocationOnPath`) resolves the matching `workoutId` and triggers workout selection/peeking.
  5. *Clean-Room Regression Suite Execution (`TST-PER-027.5`)*:
  • Execute targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"` verifying 100% pass rate.

### TST-PER-028: **Periods Sport Type Header Filtering & Multi-Sport Projections Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PER-022`, `REQ-PER-018`, `REQ-PER-019`
- **Component**: **Periods Header Sport Filter & ViewModel Projection**
- **Jira**: `ATT-4770`
- **Test Description**:
  1. *Default Filter Set & Empty Guard Tests (`TST-PER-028.1`, `PeriodsViewModelTest.kt`)*:
  • Assert initial `selectedSports` contains `setOf(BSportType.BIKE, BSportType.RUN)`.
  • Toggle `BSportType.RUN`: verify `selectedSports` becomes `setOf(BSportType.BIKE)`.
  • Toggle `BSportType.BIKE`: verify toggle is ignored and `selectedSports` remains `setOf(BSportType.BIKE)` (empty set prevention).
  • Toggle `BSportType.UNKNOWN`: verify `selectedSports` becomes `setOf(BSportType.BIKE, BSportType.UNKNOWN)`.
  2. *Reactive In-Memory Period Projection Tests (`TST-PER-028.2`, `PeriodsViewModelTest.kt`)*:
  • Given a period with 2 Bike workouts (1h, 20km), 1 Run workout (30m, 5km), and 1 Other workout (45m, 0km).
  • Under default `[Bike, Run]`: assert period has `totalWorkouts = 3`, `totalDurationSec = 5400L`, `totalDistance = 25000.0`, and `sportStats` contains only BIKE and RUN.
  • Under `[Bike]`: assert period has `totalWorkouts = 2`, `totalDurationSec = 3600L`, `totalDistance = 20000.0`.
  • Given a period with only "Other" workouts: assert it is omitted from `filteredGroupedPeriods` when `UNKNOWN` is deselected.
  • Toggle `UNKNOWN` on: assert the period appears in `filteredGroupedPeriods`.
  3. *Map Preview & Polylines Filtering Tests (`TST-PER-028.3`, `PeriodsViewModelTest.kt`)*:
  • Given a period with `workoutIdToAllPolylinesMap` containing tracks for Bike (ID 1), Run (ID 2), and Other (ID 3).
  • Under `[Bike]`: assert projected `polylines` and `workoutIdToPolylineMap` retain only ID 1.
  • Assert projected `extremaMarkers` retain only markers matching selected sports.
  4. *Header UI Layout & Canonical Sport Ordering Tests (`TST-PER-028.4`, `PeriodsTabsScreenTest.kt`)*:
  • Verify 3 sport toggle buttons render in canonical order `[BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN]` (Rule 57: `Other` on far right).
  • Verify selected buttons render in `onPrimaryContainer` and deselected buttons render in `onPrimaryContainer.copy(alpha = TTAlpha.Disabled)`.
  • Verify content descriptions match localized sport strings (`R.string.sport_type_bike`, `R.string.sport_type_run`, `R.string.sport_type_other`).
  5. *Clean-Room Regression Suite Execution (`TST-PER-028.5`)*:
  • Execute targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"` verifying 100% pass rate.

### TST-PER-029: **PeriodBarGraph Distance & Imperial Unit Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PER-023`, `REQ-PER-018`
- **Component**: **PeriodBarGraph Unit Parity & Formatting**
- **Jira**: `ATT-4771`
- **Test Description**:
  1. *Metric Distance Formatting Unit Tests (`TST-PER-029.1`, `PeriodBarGraphTest.kt`)*:
  • Given `unit = MyUnits.METRIC`:
    - Value `500.0` (500m) formats as `"500m"`.
    - Value `999.0` (999m) formats as `"999m"`.
    - Value `1000.0` (1km) formats as `"1.0km"`.
    - Value `8500.0` (8.5km) formats as `"8.5km"`.
    - Value `9949.0` (9.9km) formats as `"9.9km"`.
    - Value `10000.0` (10km) formats as `"10km"`.
    - Value `42195.0` (42.195km) formats as `"42km"`.
  2. *Imperial Distance Formatting Unit Tests (`TST-PER-029.2`, `PeriodBarGraphTest.kt`)*:
  • Given `unit = MyUnits.IMPERIAL`:
    - Value `100.0` (~0.062mi) formats as `"<0.1mi"`.
    - Value `1609.344` (1.0mi) formats as `"1.0mi"`.
    - Value `8530.0` (~5.3mi) formats as `"5.3mi"`.
    - Value `16093.44` (10.0mi) formats as `"10mi"`.
    - Value `42195.0` (~26.2mi) formats as `"26mi"`.
  3. *Ascent Unit Parity Unit Tests (`TST-PER-029.3`, `PeriodBarGraphTest.kt`)*:
  • For `PeriodBarMetric.ASCENT`:
    - Given `unit = MyUnits.METRIC`: value `250.0` formats as `"250m"`.
    - Given `unit = MyUnits.IMPERIAL`: value `250.0` (~820ft) formats as `"820ft"`.
  4. *Application Preference Fallback Tests (`TST-PER-029.4`, `PeriodBarGraphTest.kt`)*:
  • Verify that when `unit` parameter is defaulted, `formatMetricValue` queries `TrainingApplication.getUnit()`.
  • Verify `TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)` switches output format without explicit parameter.
  5. *Geometry & Bar Layout Integrity Tests (`TST-PER-029.5`, `PeriodBarGraphTest.kt`)*:
  • Verify `barWidth` for `DAY` is `24.dp`, `WEEK` is `32.dp`, and spacing is `6.dp`.
  6. *Clean-Room Full Suite Regression Execution (`TST-PER-029.6`)*:
  • Execute targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"` verifying 100% test pass rate with zero regressions.


### TST-PER-030: **Route-Frequency Heatmap Unification, Tolerance Calibration & Region Propagation Test Suite** [Verified]
- **Requirement(s)**: `REQ-PER-024`
- **Component**: `PolylineHeatmapTileProviderTest.kt`, `PeriodCardMapStateTest.kt`, `InteractivePeriodMapTest.kt`, `PeriodsViewModelTest.kt`
- **Jira**: `ATT-4833`
- **Test Description**:
  1. *ThermalColorLut High-Contrast Progression Test (`TST-PER-030.1`, `PolylineHeatmapTileProviderTest.kt`)*:
  • Verify `ThermalColorLut` progression: Stop 0 base alpha is ~140 (~55%), Stop 1 base alpha is ~204 (~80%), and Stop 2 high intensity alpha at opacity 1.0 is 255 (100%).
  2. *Adaptive Corridor Frequency Normalization Test (`TST-PER-030.2`, `PolylineHeatmapTileProviderTest.kt`)*:
  • Verify `PolylineHeatmapTileProvider` dynamically calculates `stepIntensity` based on workout count: 2 passes to saturation for $\le 3$ workouts, 3 passes for $\le 10$ workouts, 5 passes for $\le 35$ workouts, 8 passes for $\le 100$ workouts, and 12 passes for $> 100$ workouts.
  3. *Corridor Overlap Tolerance Calibration Test (`TST-PER-030.3`, `PeriodCardMapStateTest.kt`)*:
  • Verify `getPeriodSimplificationToleranceMeters` returns $5.0\text{m}$ for Day, $5.0\text{m}$ for Week, $6.0\text{m}$ for Month, and $8.0\text{m}$ for Year.
  4. *Heatmap-Primary Visualization & Track Suppression Test (`TST-PER-030.4`, `InteractivePeriodMapTest.kt`)*:
  • Verify that when `peekedTracks` is empty (no workout selected), `InteractivePeriodMap` and `PeriodMapScreen` suppress vector polylines, anchor lines, and start/stop markers.
  • Verify that when a workout is peeked, only the peeked track is rendered with high alpha and markers.
  5. *Region State Propagation Test (`TST-PER-030.5`, `PeriodsViewModelTest.kt`)*:
  • Verify `showPeriodMap(summary, initialRegionId)` initializes `selectedRegionId` with `initialRegionId`.
  6. *Clean-Room Full Suite Regression Execution (`TST-PER-030.6`)*:
  • Execute targeted unit tests verifying 100% test pass rate with zero regressions.

### TST-PER-031: **Yearly Activity Contribution Grids Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PER-025`
- **Component**: `YearlyContributionGridTest.kt`, `PeriodsViewModelTest.kt`, `PeriodSummaryCardTest.kt`, `YearlyContributionLocalizationTest.kt`
- **Jira**: `ATT-4849`
- **Test Description**:
  1. *Daily Aggregation & Calendar Matrix Mapping Test (`TST-PER-031.1`, `YearlyContributionGridTest.kt`)*:
  • Verify that daily workout totals for a given calendar year map deterministically to a 7-row (Mon–Sun) by 53-column (ISO weeks 1..53) calendar day matrix for both standard (365-day) and leap (366-day) years.
  2. *Quartile Calculation & Level Assignment Test (`TST-PER-031.2`, `YearlyContributionGridTest.kt`)*:
  • Verify that positive volume values are partitioned into quartiles $Q_1, Q_2, Q_3, Q_4$, yielding levels 1..4 (with level 0 for zero/rest days) across all supported metrics (`DURATION`, `DISTANCE`, `ASCENT`).
  3. *Multi-Sport Separation & Canonical Order Test (`TST-PER-031.3`, `YearlyContributionGridTest.kt`)*:
  • Verify that distinct contribution grids are instantiated per active `BSportType` with sport-specific color ramps (`TTColor.BikeSportBarColor`, `RunSportBarColor`, `OtherSportBarColor`), ordered according to Rule 57 (`[Bike, Run, Other]`).
  4. *Master Selector Slaving & Filter Reactivity Test (`TST-PER-031.4`, `PeriodsViewModelTest.kt`)*:
  • Verify that `PeriodsViewModel` exposes `selectedMetric: StateFlow<PeriodBarMetric>` slaving both `PeriodBarGraph` and contribution grids; verify that toggling `selectedSports` reactively shows/hides the respective sport contribution grids.
  5. *Interactive Day Cell Selection & Drill-Down Contract Test (`TST-PER-031.5`, `YearlyContributionGridTest.kt`)*:
  • Verify that tapping an active day cell emits that day's start/end epoch second timestamps, sport type, and workout metadata for drill-down into `WorkoutSummariesListFragment`.
  6. *9-Language Localization Parity Audit (`TST-PER-031.6`, `YearlyContributionLocalizationTest.kt`)*:
  • Verify that all added string resources (`workout_periods__yearly_activity_grid`, `workout_periods__no_activity`) exist with matching keys across all 9 supported locales.
  7. *Targeted Regression Verification (`TST-PER-031.7`)*:
  • Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"` to verify 100% clean-room test pass rate.

### TST-PER-032: **Master Sport Filter Heatmap & Region Clustering Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PER-024` (`REQ-PER-024.5`)
- **Component**: `PeriodsViewModelTest.kt`, `PeriodCardMapStateTest.kt`, `InteractivePeriodMapTest.kt`
- **Jira**: `ATT-4935`
- **Test Description**:
  1. *Symmetric DTO Polyline Sport Filtering Unit Test (`TST-PER-032.1`, `PeriodsViewModelTest.kt`)*:
  • Verify that `filterPeriodSummary()` filters `workoutIdToAllPolylinesMap` and `workoutIdToPathMap` symmetrically alongside `workoutIdToPolylineMap` according to `selectedSports`, preventing route leaking into card previews.
  2. *PeriodMapScreen / InteractivePeriodMap Sport-Filtered Track & Heatmap Decoupling Test (`TST-PER-032.2`, `PeriodsViewModelTest.kt` / `InteractivePeriodMapTest.kt`)*:
  • Verify that `selectPeriodForMap()` filters workouts using `selectedSports.value` prior to spatial clustering and heatmap path map generation, ensuring excluded sports never appear in the map's route collection.
  3. *Spatial Region Clustering Sport Sensitivity & Workout Count Accuracy Test (`TST-PER-032.3`, `PeriodsViewModelTest.kt`)*:
  • Verify that region detection (`detectRegions`) and resulting `Hauptregion` / region chips reflect the sport-filtered subset, matching the selected sport's geographic distribution and workout counts.
  4. *Corridor Frequency Calibration on Sport-Filtered Path Set Test (`TST-PER-032.4`, `PeriodCardMapStateTest.kt`)*:
  • Verify that `estimateMaxCorridorPassCount` and `PolylineHeatmapTileProvider` intensity normalization (`ATT-4929` / `ATT-4932`) compute strictly on the filtered routes without skew from hidden sports.
  5. *Clean-Room Targeted Regression Execution (`TST-PER-032.5`)*:
  • Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*"` to verify 100% test pass rate with zero regressions.

### TST-PER-033: **Region Place Name Resolution, Hierarchy Fallback, Caching & Chip Ellipsis Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PER-026` (`REQ-PER-026.1`..`REQ-PER-026.5`)
- **Component**: `RegionNameResolverTest.kt`, `PeriodsViewModelTest.kt`, `PeriodCardMapStateTest.kt`, `PeriodMapScreenTest.kt`
- **Jira**: `ATT-4934`
- **Test Description**:
  1. *Priority 1 Known Location Resolution Test (`TST-PER-033.1`, `RegionNameResolverTest.kt`)*:
  • Verify that when a non-placeholder `MyLocation` exists within 10 km of the region center, its name is returned immediately without querying `Geocoder`.
  • Verify that placeholder names (e.g. "Startort", "Auto-learned") are ignored.
  2. *Priority 2 Geocoder Macro-Locality Resolution Test (`TST-PER-033.2`, `RegionNameResolverTest.kt`)*:
  • Verify that when no known location exists within 10 km, `Geocoder` macro-locality (`locality ?: subLocality ?: subAdminArea`) is resolved and returned.
  3. *Priority 3 Localized Fallback Test (`TST-PER-033.3`, `RegionNameResolverTest.kt`)*:
  • Verify that when both known location and Geocoder fail or are offline, the resolver returns the localized fallback `"Region n"`.
  4. *Two-Tier Cache Hit & Offline Retention Test (`TST-PER-033.4`, `RegionNameResolverTest.kt`)*:
  • Verify that a resolved name is persisted to both memory and `SharedPreferences` cache and returned on subsequent lookups without triggering `Geocoder`.
  5. *Hauptregion Semantics & Long Name Ellipsis Test (`TST-PER-033.5`, `PeriodMapScreenTest.kt` / `PeriodSummaryCardTest.kt`)*:
  • Verify that primary region chips format with "Hauptregion: ..." / "Primary: ..." and secondary chips format with place name and workout count.
  • Verify that long place names enforce single-line layout (`maxLines = 1`) and `TextOverflow.Ellipsis`.
  6. *9-Language Localization Parity Audit (`TST-PER-033.6`, `TranslationParityTest.kt`)*:
  • Verify that `workout_periods__named_region_format` and `workout_periods__primary_named_region_format` exist with valid `%1$s` and `%2$d` tokens across all 9 locales.
  7. *Clean-Room Targeted Regression Execution (`TST-PER-033.7`)*:
  • Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.location.*"` to verify 100% test pass rate with zero regressions.



