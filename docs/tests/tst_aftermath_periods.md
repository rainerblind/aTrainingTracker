# Living Test Specification: Post-Workout Analytics & Periods

**Domain**: `aftermath_periods`  
**Prefixes**: `PER`, `PERF`  
**Scope**: Post-workout analytics, period statistics, zone distributions, graphs and visual scrubbers.  
**Total Tests**: 42

---

### TST-PERF-001: **Heatmap Memory Scalability** [Verified]
- **Requirement(s)**: `REQ-MAP-017`
- **Component**: **Heatmap Memory Scalability**
- **Jira**: ``ATT-319``
- **Test Description**: 1. Import a large volume of workouts (e.g. 500+). 2. Navigate to the 'Periods' view and scroll through Yearly/Monthly summaries.

### TST-PERF-002: **Cluster Detail Loading Speed** [Verified]
- **Requirement(s)**: `REQ-PER-001`
- **Component**: **Cluster Detail Loading Speed**
- **Jira**: ``ATT-362``
- **Test Description**: 1. Identify a cluster with > 30 recordings. 2. Tap on the cluster to open its details. 3. Measure time from tap to full heatmap/list display.

### TST-PERF-003: **Initial Cluster Loading Progress** [Verified]
- **Requirement(s)**: `REQ-PER-003`
- **Component**: **Initial Cluster Loading Progress**
- **Jira**: ``ATT-366``
- **Test Description**: 1. Clear application cache or significantly modify history. 2. Navigate to 'My Locations'.

### TST-PERF-004: **Incremental Period Visibility** [Verified]
- **Requirement(s)**: `REQ-PER-001`
- **Component**: **Incremental Period Visibility**
- **Jira**: ``ATT-374``
- **Test Description**: 1. Trigger a full hierarchical Periods sync. 2. Observe the list as the "Syncing [Month]..." progress updates.

### TST-PERF-005: **Dual-Phase Sync Efficiency** [1. The status SHALL transition from 'Reading...' to 'Syncing...' without jumping back. 2. The period list SHALL grow visibly after each 'Syncing [Month]' update. 3. Scrolling SHALL remain smooth throughout the sync.] [Verified]
- **Requirement(s)**: `REQ-PER-004`
- **Component**: **Dual-Phase Sync Efficiency**
- **Jira**: ``ATT-380``
- **Test Description**: 1. Trigger a full hierarchical Periods sync. 2. Observe the status transitions and UI responsiveness.

### TST-PERF-006: **Multi-Phase Progress Feedback** [Verified]
- **Requirement(s)**: `REQ-PER-005`
- **Component**: **Multi-Phase Progress Feedback**
- **Jira**: ``ATT-383``
- **Test Description**: 1. Trigger a Periods sync. 2. Inspect the progress card UI.

### TST-PERF-007: **Workout Cluster Migration Progress** [Verified]
- **Requirement(s)**: `REQ-PER-006`
- **Component**: **Workout Cluster Migration Progress**
- **Jira**: ``ATT-393``
- **Test Description**: 1. Trigger a Workout Cluster recalculation. 2. Observe the progress card.

### TST-PERF-008: **Reactive Period Detail Verification** [Verified]
- **Requirement(s)**: `REQ-PER-007`
- **Component**: **Reactive Period Detail Verification**
- **Jira**: ``ATT-442``
- **Test Description**: 1. Open the 'Periods' screen. 2. Tap the map icon for a period *immediately* before the background loading completes. 3. Observe the heatmap as it grows.

### TST-PERF-009: **Silent Cluster Refresh** [Verified]
- **Requirement(s)**: `REQ-PER-006`
- **Component**: **Silent Cluster Refresh**
- **Jira**: ``ATT-441``
- **Test Description**: 1. Navigate to My Locations. 2. Navigate away and back. 3. Verify no progress notification on 2nd visit. 4. Trigger recalculation and verify progress DOES appear.

### TST-PERF-010: **Persistent Previews Audit** [Verified]
- **Requirement(s)**: `REQ-PER-006`
- **Component**: **Persistent Previews Audit**
- **Jira**: ``ATT-441``
- **Test Description**: 1. Navigate to My Locations. 2. Wait for enrichment. 3. Kill and restart app. 4. Navigate back to My Locations.

### TST-PER-009: **Precise Sport Filtering** [Verified]
- **Requirement(s)**: `REQ-PER-008`
- **Component**: **Precise Sport Filtering**
- **Jira**: ``ATT-459``
- **Test Description**: 1. Open a Period Summary. 2. Click a Sub-Sport row (e.g. MTB). 3. Verify the resulting list only contains MTB workouts for that period.

### TST-PER-010: **Heatmap Toggle Removal** [Verified]
- **Requirement(s)**: `REQ-UI-120`
- **Component**: **Heatmap Toggle Removal**
- **Jira**: ``ATT-460``
- **Test Description**: 1. Open Periods screen and Period Map. 2. Verify `Whatshot` toggle is gone. 3. Verify heatmap still reacts to global settings.

### TST-PER-012: **Period Map Sport Filtering** [Verified]
- **Requirement(s)**: `REQ-PER-009`
- **Component**: **Period Map Sport Filtering**
- **Jira**: ``ATT-466``
- **Test Description**: 1. Open Period Detail Map. 2. Tap a Sport Row (e.g., Bike). 3. Verify map only shows Bike tracks and markers. 4. Verify heatmap only reflects Bike activities.

### TST-PER-013: **Uniform Heatmap & Visual Transparency** [Verified]
- **Requirement(s)**: `REQ-PER-010`
- **Component**: **Uniform Heatmap & Visual Transparency**
- **Jira**: ``ATT-531``
- **Test Description**: 1. Open a Period summary (Month or Year) with > 20 workouts. 2. Verify that ALL workouts across the entire period contribute to the heatmap without geographic truncation. 3. Inspect the heatmap overlay over satellite/terrain maps and verify roads and landmarks remain legible through the translucent gradient.

### TST-PER-014: **Longest Workout Metrics & Period Header** [Verified]
- **Requirement(s)**: `REQ-PER-011`
- **Component**: **Longest Workout Metrics & Period Header**
- **Jira**: ``ATT-532``
- **Test Description**: 1. Open the Period summary list. 2. Verify Period Card headers display duration and workout count. 3. Expand a sport summary row to inspect the Longest Workout highlight.

### TST-PER-015: **Period Longest Workout Metric Integrity & Cross-Season Week Keys** [Verified]
- **Requirement(s)**: `REQ-PER-011`
- **Component**: **Period Longest Workout Metric Integrity & Cross-Season Week Keys**
- **Jira**: ``ATT-536``
- **Test Description**: 1. Execute `PeriodsTemporalKeyTest`. 2. Verify week start calculations across winter/summer DST transitions. 3. Verify real metrics hydrated for weekly summaries in UI without zero fallthrough.

### TST-PER-016: **Multi-Workout Period Longest Workout Selection Integrity** [Verified]
- **Requirement(s)**: `REQ-PER-011`
- **Component**: **Multi-Workout Period Longest Workout Selection Integrity**
- **Jira**: ``ATT-579``
- **Test Description**: 1. Execute `PeriodsAggregationTest`. 2. Verify that in multi-workout periods (such as January 2015), the workout with the true maximum duration and distance (day 31) is selected as longest workout regardless of stale database IDs.

### TST-PER-017: **Period Empty State Placeholder Verification** [Verified]
- **Requirement(s)**: `REQ-UI-125`
- **Component**: **Period Empty State Placeholder Verification**
- **Jira**: ``ATT-488``
- **Test Description**: 1. Open the application with no recorded workouts or filter a sport type with 0 workouts. 2. Navigate to the 'Periods' screen (Woche, Monat, Jahr). 3. Observe the viewport below the period tabs and header when no period summaries exist. 4. Verify that an informative `EmptyStatePlaceholder` is displayed with an appropriate calendar/history icon, clear title ("No period summaries available" / "Keine Zeiträume verfügbar"), and supportive description. 5. Verify proper layout insets (collapsible header offset and bottom navigation bar insets). 6. Verify 9-language localization parity.

### TST-PER-018: **Period Map Scalability, Memory Budget & Adaptive Layering Invariant** [Verified]
- **Requirement(s)**: `REQ-PER-012`
- **Component**: **Period Map Scalability, Memory Budget & Adaptive Layering Invariant**
- **Jira**: ``ATT-1107``
- **Test Description**: 1. Verify `AndroidManifest.xml` declares `android:largeHeap="true"`. 2. Execute unit tests verifying that when a period contains > 30 workouts, individual member vector tracks (`memberTracks`) and member markers (`memberMarkers`) are suppressed, while aggregate `heatmapPaths` and `anchorTracks` remain active. 3. Verify that when workouts <= 30 (or filtered by sport to <= 30), member tracks and markers are fully populated. 4. Verify that tapping/peeking any individual session within a high-volume period populates `peekedTracks` with a single focused vector line without background vector explosion.

### TST-PER-019: **Multi-Region Period Map Viewport Partitioning & Navigation Verification** [Verified]
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

### TST-PER-020: **Immediate Period View Reactive Synchronization on Workout Finalization Verification** [Verified]
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

### TST-PER-021: **Calendar-Aware Period Aggregation Boundaries, Sentinel Epoch Symmetry & Local-Day PMC Bucketing Verification Suite** [Verified]
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

### TST-PER-022: **Period Rebuild Flow Projection, Single-Owner Tracking Finalization Orchestration & Shared Mapper Instance Verification Suite** [Verified]
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

### TST-PER-023: **Period Line-Density Heatmap Visuals & Allocation-Free Tile Rendering Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PER-017`
- **Component**: **Period Heatmap Visuals**
- **Jira**: `ATT-4480`
- **Test Description**:
  1. Verify in `PeriodMapVisualsTest` that `getPeriodMapVisuals(PeriodType.DAY, ...)` returns `heatmapProvider = null`.
  2. Verify that `PeriodType.WEEK`, `MONTH`, and `YEAR` return non-null `PolylineHeatmapTileProvider` instances with calibrated opacity.
  3. Verify that empty path input returns `heatmapProvider = null`.
  4. Run targeted unit test suite `com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*` verifying 100% pass rate.

### TST-PER-024: **PeriodBarGraph Timeline Gap Interpolation, Sub-Hour Formatting, Sport Stacking & Metric Mode Verification Suite** [Verified]
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

### TST-PER-025: **PeriodCardMapState Background Pipeline, Douglas-Peucker Decimation & Full-Period Route Coverage Verification Suite** [Verified]
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

### TST-PER-026: **Self-Contained Period Persistence, Zero-N+1 Batch Loading & Bidirectional Sync Healing Verification Suite** [Verified]
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
- **Jira**: `ATT-4770, ATT-5828`
- **Test Description**:
  1. *Default Filter Set & Empty Guard Tests (`TST-PER-028.1`, `PeriodsViewModelTest.kt`)*:
  • Assert initial `selectedSports` contains `setOf(BSportType.BIKE, BSportType.RUN)`.
  • In `PeriodsViewModelTest.kt`, all `PeriodsViewModel` instantiations use `createViewModel` with injected `testDispatcher` for both `ioDispatcher` and `defaultDispatcher`, preventing `UncaughtExceptionsBeforeTest` (ATT-5828).
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
- **Jira**: `ATT-4849`, `ATT-5076`, `ATT-5194`, `ATT-5193`
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
  8. *Embedded SportStatsRow Placement & Seamless Styling Test (`TST-PER-031.8`)*:
  • Verify that for `PeriodType.YEAR`, `YearlyContributionGrid` is rendered inside `SportStatsRow` between the header row and sub-sports divider with `showCard = false` and `showSportHeader = false`, omitting redundant outer container borders and sport titles (ATT-5076).
  9. *Initial Viewport Auto-Scroll Alignment Unit Test (`TST-PER-031.9`, `YearlyContributionGridTest.kt`)*:
  • Verify pure helper functions `calculateTargetColumn` and `calculateInitialScrollOffset` across past years, early year, mid year, late year, and leap years, confirming offset clamping to `[0, maxValue]` and alignment of the target week at the right edge (ATT-5194).
  10. *Day-of-Week Label Cadence & Sunday Anchor Unit Test (`TST-PER-031.10`, `YearlyContributionGridTest.kt`)*:
  • Verify `shouldShowDayOfWeekLabel` returns true for row indices 0 (Mon), 2 (Wed), 4 (Fri), and 6 (Sun), and false for indices 1 (Tue), 3 (Thu), and 5 (Sat), confirming the complete 2-day cadence (ATT-5193).

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

### TST-PER-034: **Dual Vertex Budget Enforcement & Adaptive Decimation Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PER-019` (`REQ-PER-019.2`), `REQ-PER-024` (`REQ-PER-024.3`)
- **Component**: `PeriodMapUtilsTest.kt`, `PeriodCardMapStateTest.kt`, `PeriodsViewModelTest.kt`
- **Jira**: `ATT-4930`
- **Test Description**:
  1. *Card Mini-Map Hard Vertex Budget Test (`TST-PER-034.1`, `PeriodMapUtilsTest.kt`)*:
  • Given a synthetic Year period with 300 workouts (each containing 200 GPS vertices, totaling 60,000 raw coordinates), verify that `simplifyPathsToBudget` or `prepareCardMapPreviewSync` bounds total vertices to $\le 2,000$ points (`MAX_CARD_MINI_MAP_VERTICES`).
  2. *Detailed Map Hard Vertex Budget Test (`TST-PER-034.2`, `PeriodMapUtilsTest.kt`)*:
  • Given a synthetic Year period with 300 workouts, verify that detailed map simplification bounds total vertices to $\le 8,000$ points (`MAX_DETAILED_MAP_VERTICES`).
  3. *Base Tolerance Preservation Under Moderate Volume Test (`TST-PER-034.3`, `PeriodMapUtilsTest.kt`)*:
  • Given a period where total vertices at base tolerance (5.0m Day/Week, 6.0m Month, 8.0m Year) are below budget, verify that base tolerances are preserved without unnecessary coarsening, maintaining 100% shared-corridor pixel overlap.
  4. *Adaptive Escalation Convergence Test (`TST-PER-034.4`, `PeriodMapUtilsTest.kt`)*:
  • Given pathological GPS paths with extreme point density, verify that the adaptive decimation algorithm converges within finite passes and never returns an empty path or throws an exception.
  5. *Clean-Room Targeted Regression Execution (`TST-PER-034.5`)*:
  • Execute targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"` verifying 100% test pass rate with zero regressions.

### TST-PER-035: **Daily Activity Mini-Bar Strip for Months & Weeks Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PER-027`
- **Component**: `DailyActivityStripTest.kt`, `PeriodsRepositoryTest.kt`, `PeriodsViewModelTest.kt`, `PeriodSummaryCardTest.kt`, `DailyActivityStripLocalizationTest.kt`
- **Jira**: `ATT-5195`, `ATT-5280`
- **Test Description**:
  1. *Daily Activity Aggregation & Hydration Unit Test (`TST-PER-035.1`, `PeriodsRepositoryTest.kt`)*:
  • Verify that `PeriodsRepository.enrich()` correctly aggregates daily per-sport activity buckets and peak daily metrics into `dailySportContributions` for `PeriodType.WEEK` and `PeriodType.MONTH`, while leaving `PeriodType.YEAR` and `PeriodType.DAY` untouched.
  2. *7-Day Week & Month Day Matrix Mapping Unit Test (`TST-PER-035.2`, `DailyActivityStripTest.kt`)*:
  • Verify that `DailyActivityStrip` maps 7 days (Monday..Sunday) for `PeriodType.WEEK` and the exact day count (28, 29, 30, or 31) for `PeriodType.MONTH` (including leap years), with rest days rendering baseline tracks and workout days rendering proportional bars.
  3. *Proportional Bar Height & Quartile Calculation Test (`TST-PER-035.3`, `DailyActivityStripTest.kt`)*:
  • Verify that mini-bar fill heights scale proportionally ($0.15 \le \text{ratio} \le 1.0$) according to the active metric value vs. max daily value, and sport semantic colors (`TTColor.BikeSportBarColor`, `RunSportBarColor`, `OtherSportBarColor`) are applied per sport.
  • Contract test verifying that mini-bar shape geometry in `DailyActivityStrip.kt` applies rounded top corners and sharp flat bottom corners without outer container bottom-corner clipping per Rule 69 (ATT-5280).
  4. *Sport Filter Propagation & Decoupling Test (`TST-PER-035.4`, `PeriodsViewModelTest.kt`)*:
  • Verify that `PeriodsViewModel.filterPeriodSummary()` filters `dailySportContributions` symmetrically according to `selectedSports`, without data leakage across unselected sports.
  5. *Interactive Day Selection & Drill-Down Contract Test (`TST-PER-035.5`, `DailyActivityStripTest.kt`)*:
  • Verify that tapping a day bar emits the selected date and activity, displaying the selection banner with canonical formatted metrics and firing `onDayCellClick` for navigation on active days.
  6. *Embedded SportStatsRow Placement & M3 Styling Test (`TST-PER-035.6`, `PeriodSummaryCardTest.kt`)*:
  • Verify that `DailyActivityStrip` renders inside `SportStatsRow` for `WEEK` and `MONTH` below header totals and above sub-sports/longest workout dividers, without extra outer card borders.
  7. *9-Language Localization Parity Audit (`TST-PER-035.7`, `DailyActivityStripLocalizationTest.kt`)*:
  • Verify that all added/referenced string resources (`workout_periods__daily_activity_strip`, `workout_periods__no_activity`) maintain 100% localization parity across all 9 supported locales.
  8. *Targeted Regression Verification (`TST-PER-035.8`)*:
  • Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"` to verify 100% clean-room test pass rate.

### TST-PER-036: **Period Date Range & Bar Graph Metric Decimal Localization Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PER-028` (`REQ-PER-028.1`..`REQ-PER-028.4`)
- **Component**: `PeriodBarGraphTest.kt`, `DateTimeUtilsTest.kt`, `PeriodsRepositoryTest.kt`, `PeriodSummaryCardTest.kt`
- **Jira**: `ATT-5836`
- **Test Description**:
  1. *Weekly Date Range Active Locale Formatting Unit Test (`TST-PER-036.1`, `DateTimeUtilsTest.kt` / `PeriodsRepositoryTest.kt`)*:
  • Verify that weekly date range formatting outputs day before month for German (`de_DE`, e.g. `"2. Nov. – 8. Nov."` or `"2. Nov. - 8. Nov."`) and month before day for US English (`en_US`, e.g. `"Nov 2 – Nov 8"` or `"Nov. 2 - Nov. 8"`).
  2. *PeriodBarGraph Metric Value Decimal Localization Unit Test (`TST-PER-036.2`, `PeriodBarGraphTest.kt`)*:
  • Verify that `formatMetricValue` with duration (e.g. 6120s = 1.7h) produces localized decimal comma `"1,7h"` for `Locale.GERMANY` and dot `"1.7h"` for `Locale.US`.
  • Verify that `formatMetricValue` with distance (e.g. 5400m = 5.4km / 8530m = 5.3mi) produces localized decimal comma (`"5,4km"`, `"5,3mi"`) for `Locale.GERMANY` and dot (`"5.4km"`, `"5.3mi"`) for `Locale.US`.
  3. *Dynamic Date Range Resolution for Cached Periods (`TST-PER-036.3`, `PeriodSummaryCardTest.kt` / `PeriodsRepositoryTest.kt`)*:
  • Verify that cached `PeriodSummary` instances with valid start/end timestamps dynamically resolve and render localized date ranges when evaluated under German vs US English locales.
  4. *Non-Decimal & Edge Duration Preserving Unit Test (`TST-PER-036.4`, `PeriodBarGraphTest.kt`)*:
  • Verify that non-decimal durations (`<1m`, `45m`, `12h`) and exact integer distances/ascents (`250m`, `820ft`, `15km`) preserve existing formatting across all locales.
  5. *Clean-Room Targeted Regression Execution (`TST-PER-036.5`)*:
  • Execute targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"` verifying 100% clean-room test pass rate.

### TST-PER-037: **WorkoutShareBottomSheet Option A Automated Snapshot & Share Delegation Suite** [Verified]
- **Requirement(s)**: `REQ-PER-029` (`REQ-PER-029.1`..`REQ-PER-029.4`)
- **Component**: `WorkoutShareBottomSheetTest.kt`, `WorkoutDetailTabbedContentTest.kt`, `MapDetailLayoutTest.kt`, `WorkoutSummaryTest.kt`
- **Jira**: `ATT-6208`
- **Test Description**:
  1. *MapDetailLayout Programmatic Snapshot Trigger Contract Test (`TST-PER-037.1`, `MapDetailLayoutTest.kt`)*:
  • Verify that `MapDetailLayout` accepts `externalSnapshotTrigger` and `onSnapshotComplete`, triggering `shouldTakeSnapshot = true` and invoking `onSnapshotComplete` after capture.
  2. *WorkoutDetailTabbedContent Option A Automated Handoff Contract Test (`TST-PER-037.2`, `WorkoutDetailTabbedContentTest.kt`)*:
  • Verify that selecting Option A in `WorkoutShareBottomSheet` navigates/scrolls to `mapTabIndex` and activates `externalSnapshotTrigger = true`.
  3. *WorkoutSummary List-to-Detail Auto-Share Bridge Contract Test (`TST-PER-037.3`, `WorkoutSummaryTest.kt`)*:
  • Verify that `WorkoutSummary` Option A selection invokes `onShareMapSnapshot` with `initialAutoShareSnapshot = true` for seamless detail navigation and automated snapshot capture.
  4. *Floating Share Button & Top-End Actions Invariant Audit (`TST-PER-037.4`, `MapDetailLayoutTest.kt`)*:
  • Verify that `showFloatingShareButton: Boolean = workoutData == null` is preserved intact and top-end actions dock at `16.dp` without duplicate buttons or layout regressions (REQ-UI-500).
  5. *Clean-Room Targeted Regression Execution (`TST-PER-037.5`)*:
  • Execute targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"` verifying 100% clean-room test pass rate.

### TST-PER-038: **Morning HRV Readiness Database Persistence, Backup Parity & Per-Posture SWC Baseline Calculator Suite** [Verified]
- **Requirement(s)**: `REQ-PER-030` (`REQ-PER-030.1`..`REQ-PER-030.5`)
- **Component**: `HrvReadinessEntryTest.kt`, `HrvReadinessDatabaseManagerTest.kt`, `HrvReadinessCalculatorTest.kt`, `DatabaseSchemaRegistryTest.kt`, `BackupManagerHrvReadinessTest.kt`
- **Jira**: `ATT-3545`
- **Test Description**:
  1. *Schema Creation & DTO Cursor Mapping Symmetry Unit Test (`TST-PER-038.1`, `HrvReadinessEntryTest.kt` / `HrvReadinessDatabaseManagerTest.kt`)*:
  • Verify `HrvReadiness.db` schema creation (`DB_VERSION = 1`) with all 14 columns, type constraints, and indexes.
  • Verify symmetric mapping between single-item `fromCursor(Cursor)` and batch `fromCursorBatch(Cursor)` over identical SQLite cursor rows per Rule 20.
  2. *Transactional Primary-Day Deduplication & Pin Promotion Test (`TST-PER-038.2`, `HrvReadinessDatabaseManagerTest.kt`)*:
  • Verify that multiple readings on the same `(date_iso, posture)` designate the earliest valid reading as primary (`is_primary_for_day = 1`).
  • Verify that toggling a manual pin promotes the selected reading to primary and demotes previous readings transactionally.
  • Verify that deleting the primary reading promotes the next earliest valid reading for that calendar day.
  3. *DatabaseSchemaRegistry & Backup Integration Parity Test (`TST-PER-038.3`, `DatabaseSchemaRegistryTest.kt` / `BackupManagerHrvReadinessTest.kt`)*:
  • Verify that `DatabaseSchemaRegistry.getMaxSupportedVersion("HrvReadiness.db")` matches `HrvReadinessDatabaseManager.DB_VERSION` directly per Rule 62.
  • Verify that `DatabaseConnectionManager.getActiveDatabases(context)` includes `HrvReadiness.db`.
  • Verify that `.attbackup` export snapshots `HrvReadiness.db` via `VACUUM INTO` and restores it completely intact.
  4. *Per-Posture Isolation, Artifact Gate & 14-Day Calibration Gate Test (`TST-PER-038.4`, `HrvReadinessCalculatorTest.kt`)*:
  • Verify complete isolation across `SUPINE`, `SEATED`, and `STANDING` posture baselines.
  • Verify that readings with `artifact_rate_pct > 5.0%` are excluded from 7-day and 60-day baseline averages.
  • Verify that $< 14$ baseline days in 60d or $< 3$ days in 7d returns `ReadinessStatus.CALIBRATING` with exact day progress (`"Day X of 14 baseline calibration"`).
  5. *Bessel-Corrected Sample SD & SWC Corridor Classification Test (`TST-PER-038.5`, `HrvReadinessCalculatorTest.kt`)*:
  • Verify that $\sigma_{60d}$ evaluates unbiased sample standard deviation with $N-1$ denominator over daily $\ln(\text{rMSSD})$.
  • Verify SWC corridor calculation $[\mu_{60d} - 0.5\sigma_{60d}, \mu_{60d} + 0.5\sigma_{60d}]$.
  • Verify threshold classifications: `OPTIMAL` when inside corridor, `FATIGUED_LOW` when below, and `ABOVE_NORMAL` when above.
  6. *Tier 1 Clean-Room Targeted Regression Execution (`TST-PER-038.6`)*:
  • Execute targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.build.*"` verifying 100% clean-room test pass rate.

### TST-PER-039: **Android Health Connect Morning & Overnight Resting HRV Ingestion Suite** [Verified]
- **Requirement(s)**: `REQ-PER-031` (`REQ-PER-031.1`..`REQ-PER-031.5`)
- **Component**: `HealthConnectHrvImporterTest.kt`, `HealthConnectManagerTest.kt`, `HrvReadinessCalculatorTest.kt`, `HrvReadinessRepositoryTest.kt`
- **Jira**: `ATT-4981`
- **Test Description**:
  1. *Permission & Opt-In Gating Contract Test (`TST-PER-039.1`, `HealthConnectHrvImporterTest.kt`)*:
  • Verify that `HealthConnectHrvImporter` halts sync and modifies zero database records when permissions are missing or `isHealthConnectHrvImportEnabled` is false.
  • Verify that `READ_HEART_RATE_VARIABILITY` and `READ_RESTING_HEART_RATE` permissions are declared in `AndroidManifest.xml`.
  2. *Log-Normal Multi-Epoch Aggregation & Date Attribution Unit Test (`TST-PER-039.2`, `HealthConnectHrvImporterTest.kt`)*:
  • Verify that multiple nocturnal `HeartRateVariabilityRmssdRecord` epochs (e.g. 30.0, 40.0, 50.0 ms) aggregate to log-normal mean $\text{ln\_rmssd} = \frac{1}{M}\sum \ln(\text{rMSSD}_i)$ and geometric mean $\text{rmssd\_ms} = \exp(\text{ln\_rmssd})$.
  • Verify that sleep records before midnight (e.g. 23:00) attribute to the waking date `date_iso`, and records after midnight (e.g. 05:00) attribute to the current waking date.
  3. *Isolated Cohort Persistence & Idempotency Test (`TST-PER-039.3`, `HealthConnectHrvImporterTest.kt` / `HrvReadinessRepositoryTest.kt`)*:
  • Verify that imported measurements persist with `posture = HrvPosture.OVERNIGHT_WEARABLE` and `artifactRatePct = null`.
  • Verify that repeated imports do not produce duplicate rows for the same `(date_iso, OVERNIGHT_WEARABLE)` and preserve user pins.
  4. *Posture Isolation in SWC Baseline Calculator Contract Test (`TST-PER-039.4`, `HrvReadinessCalculatorTest.kt`)*:
  • Verify complete baseline isolation between `OVERNIGHT_WEARABLE` and waking postures (`SUPINE`, `SEATED`, `STANDING`).
  5. *Tier 1 Clean-Room Targeted Regression Execution (`TST-PER-039.5`)*:
  • Execute targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.cloud.healthconnect.*" --tests "com.atrainingtracker.trainingtracker.build.*"` verifying 100% clean-room test pass rate.

### TST-PER-040: **Guided Morning Resting HRV Measurement & Waking Protocol Suite** [Verified]
- **Requirement(s)**: `REQ-PER-032` (`REQ-PER-032.1`..`REQ-PER-032.5`)
- **Component**: `MorningHrvCheckViewModelTest.kt`, `MorningHrvCheckScreenTest.kt`, `HrvReadinessRepositoryTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-3556`
- **Test Description**:
  1. *Measurement Lifecycle State Machine Unit Test (`TST-PER-040.1`, `MorningHrvCheckViewModelTest.kt`)*:
  • Verify transition lifecycle: `READY` -> `STABILIZING` (15–30s countdown) -> `MEASURING` (60s/120s countdown) -> `COMPLETED`.
  • Verify cancel and reset behavior returns state machine cleanly to `READY`.
  2. *Sticky Posture & Duration Preference Contract Test (`TST-PER-040.2`, `MorningHrvCheckViewModelTest.kt`)*:
  • Verify that selecting `SEATED` or `STANDING` persists to `SP_HRV_LAST_POSTURE` and reloads on new ViewModel instances.
  • Verify duration selection (60s vs. 120s) configuration.
  3. *Sensor Ingestion & Zero-Workout Invariant (`TST-PER-040.3`, `MorningHrvCheckViewModelTest.kt`)*:
  • Verify that `MorningHrvCheckViewModel` consumes `RrBeatListener` stream from `BANALServiceRepository` without launching `TrackerService` or writing to `WorkoutSummaries.db`.
  4. *Post-Hoc Full-Window Centered Filter Calculation Test (`TST-PER-040.4`, `MorningHrvCheckViewModelTest.kt`)*:
  • Verify that completed measurement runs `RrArtifactFilter.filterBidirectional` across all buffered RR intervals, computing post-hoc `rmssd_ms`, `ln_rmssd`, `resting_hr_bpm`, and `artifact_rate_pct`.
  5. *Persistence, Artifact Warning & SWC Feedback Test (`TST-PER-040.5`, `MorningHrvCheckViewModelTest.kt`)*:
  • Verify that saving persists `HrvReadinessEntry` into `HrvReadinessRepository` (`HrvReadiness.db`).
  • Verify that artifact rate > 5.0% surfaces warning state.
  • Verify completion view displays `ReadinessStatus` evaluated against per-posture 7d/60d SWC baseline.
  6. *Tier 1 Clean-Room Targeted Regression Execution (`TST-PER-040.6`)*:
  • Execute targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.hrv.*" --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"` verifying 100% clean-room test pass rate.

### TST-PER-041: **Longitudinal HRV Readiness Trend, Posture SWC Corridor & Periods Integration Suite** [Verified]
- **Requirement(s)**: `REQ-PER-033` (`REQ-PER-033.1`..`REQ-PER-033.6`)
- **Component**: `HrvReadinessTrendMathTest.kt`, `PeriodsViewModelTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-3557`
- **Test Description**:
  1. *Posture Filtering, Deduplication & Series Calculation Test (`TST-PER-041.1`, `HrvReadinessTrendMathTest.kt`)*:
  • Verify that `HrvReadinessTrendMath.calculateTrendSeries` segregates entries by posture, selects daily primary readings (`is_primary_for_day` then timestamp), and filters out artifact rate > 5.0%.
  • Verify default posture selection picks posture with the highest count of readings.
  2. *7-Day Rolling Mean & 60-Day Bessel-Corrected SWC Corridor Test (`TST-PER-041.2`, `HrvReadinessTrendMathTest.kt`)*:
  • Verify 7-day rolling window: null if $N_{7d} < 3$, mean of daily $\ln(\text{rMSSD})$ when $N_{7d} \ge 3$.
  • Verify 60-day baseline corridor: null if $N_{60d} < 14$, $\mu_{60d} \pm 0.5\sigma_{60d}$ with $N-1$ Bessel denominator when $N_{60d} \ge 14$.
  • Verify status classification: `OPTIMAL`, `FATIGUED_LOW`, `ABOVE_NORMAL`, `CALIBRATING`.
  3. *Training Load Alignment & Touch Scrubbing Math Test (`TST-PER-041.3`, `HrvReadinessTrendMathTest.kt`)*:
  • Verify time-series alignment of daily training load (TSS/ATL) with HRV calendar dates.
  • Verify touch coordinate projection correctly resolves nearest date point and tooltip payload.
  4. *PeriodsViewModel Integration & Graceful Empty State Test (`TST-PER-041.4`, `PeriodsViewModelTest.kt`)*:
  • Verify `PeriodsViewModel` exposes `hrvReadinessTrendState` Flow.
  • When `HrvReadinessRepository` has zero entries, verify state indicates empty/omitted.
  • When entries exist, verify populated trend state with active posture and metrics.
  5. *9-Language Localization Parity Test (`TST-PER-041.5`, `TranslationParityTest.kt`)*:
  • Verify all new HRV readiness trend strings in `strings_hrv.xml` match 100% across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with valid format specifiers.
  6. *Tier 1 Clean-Room Targeted Regression Execution (`TST-PER-041.6`)*:
  • Execute targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"` verifying 100% clean-room test pass rate.

### TST-PER-042: **Lossless WorkoutRrIntervals, Centered Recompute & Garmin FIT HrvMesg Suite** [Verified]
- **Requirement(s)**: `REQ-PER-034` (`REQ-PER-034.1`..`REQ-PER-034.6`)
- **Component**: `WorkoutRrDatabaseManagerTest.kt`, `RrBeatTimestampReconstructionTest.kt`, `HrvPostWorkoutRecomputerTest.kt`, `FitFileWriterHrvTest.kt`, `LegacyImportEngineFitHrvTest.kt`
- **Jira**: `ATT-3543`
- **Test Description**:
  1. *WorkoutRrDatabaseManager & Schema Integrity Test (`TST-PER-042.1`, `WorkoutRrDatabaseManagerTest.kt`)*:
  • Verify creation of table `workout_rr_intervals` with columns `workout_id`, `beat_index`, `beat_timestamp_ms`, `rr_ms`, `artifact_flag`, `gap_beats`.
  • Verify batch insertion and query by `workout_id` ordered by `beat_index`.
  • Verify cascading deletion via `WorkoutDeletionHelper` when parent workout is removed.
  • Verify registration in `DatabaseSchemaRegistry`.
  2. *Beat Timestamp Cumulative Reconstruction Test (`TST-PER-042.2`, `RrBeatTimestampReconstructionTest.kt`)*:
  • Verify cumulative timestamp progression $t_k = t_{k-1} + \text{rr\_ms}_k$ preserves exact interval durations.
  • Verify drift resynchronization when $|t_{\text{pkt}} - t_{\text{beat}}| > 2000\text{ ms}$.
  • Verify strict monotonic order of timestamps.
  3. *Centered-Window Post-Workout Recomputation Test (`TST-PER-042.3`, `HrvPostWorkoutRecomputerTest.kt`)*:
  • Verify centered window evaluation for DFA $\alpha_1$ ($t \pm 60\text{s}$) and $\text{rMSSD}$ ($t \pm 30\text{s}$) using bidirectional filtering.
  • Verify elimination of ~60s trailing filter lag when comparing against step-change synthetic data.
  • Verify updates to `WorkoutSamples` and `WorkoutSummaries` session extrema.
  4. *Garmin FIT HrvMesg Export Interleaving Test (`TST-PER-042.4`, `FitFileWriterHrvTest.kt`)*:
  • Verify `FitFileWriter` exports standard `HrvMesg` messages interleaved with `RecordMesg` trackpoints when RR data exists.
  • Verify time intervals are scaled in fractional seconds (FIT standard).
  • Verify workouts without RR intervals write zero `HrvMesg` messages.
  5. *Garmin FIT HrvMesg Import & Recomputation Trigger Test (`TST-PER-042.5`, `LegacyImportEngineFitHrvTest.kt`)*:
  • Verify `FitWorkoutParser` and `LegacyImportEngine` parse `HrvMesg` intervals and reconstruct timestamps across pause events.
  • Verify extracted beats are persisted in `WorkoutRr.db` and post-workout recomputation is triggered.
  6. *Tier 1 Clean-Room Targeted Regression Execution (`TST-PER-042.6`)*:
  • Execute targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.exporter.writer.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.build.*"` verifying 100% clean-room test pass rate.

### TST-PER-043: **Interactive DFA a1/rMSSD Charts & a1 Scatter Plot Suite** [Verified]
- **Requirement(s)**: `REQ-PER-035` (`REQ-PER-035.1`..`REQ-PER-035.6`)
- **Component**: `TelemetryMetricGraphHrvTest.kt`, `HrvAlpha1ScatterChartTest.kt`, `HrvPathPointEnrichmentTest.kt`, `ThemeColorCentralizationAuditTest`, `TranslationParityTest`
- **Jira**: `ATT-3544`
- **Test Description**:
  1. *PathPoint HRV Enrichment Unit Test (`TST-PER-043.1`, `HrvPathPointEnrichmentTest.kt`)*:
  • Verify `WorkoutRepository` enriches `PathPoint` instances with `hrvDfaA1`, `hrvRmssd`, and `hrvArtifactRate` from `WorkoutSamples.db`.
  • Verify `@JvmOverloads` constructor backwards compatibility when HRV parameters are omitted (defaults to null).
  2. *TelemetryMetricGraph DFA a1 & rMSSD Tracks (`TST-PER-043.2`, `TelemetryMetricGraphHrvTest.kt`)*:
  • Verify `TelemetryMetricGraph` renders continuous paths for `TelemetryMetricType.HRV_DFA_A1` and `TelemetryMetricType.HRV_RMSSD`.
  • Verify horizontal plot bounds (`startPadding = 50.dp`) match `ElevationProfile` and other telemetry graphs per Rule 64.
  • Verify dashed horizontal threshold lines at $\alpha_1 = 0.75$ and $\alpha_1 = 0.50$ when `metricType == HRV_DFA_A1`.
  • Verify Y-axis clamped within $[0.2, 1.4]$ for DFA $\alpha_1$.
  3. *Artifact Gap & Non-Interpolation Hatching Test (`TST-PER-043.3`, `TelemetryMetricGraphHrvTest.kt`)*:
  • Verify path breaks and does NOT draw continuous line segments across dropouts where `hrvArtifactRate > 5.0%` or `hrvDfaA1 == null`.
  • Verify hatched background path overlay is rendered across artifact-suppressed regions.
  4. *HrvAlpha1ScatterChart Dynamics & Regression Line Test (`TST-PER-043.4`, `HrvAlpha1ScatterChartTest.kt`)*:
  • Verify scatter points are plotted for $(\text{HR}, \alpha_1)$ and $(\text{Power}, \alpha_1)$ strictly within valid transition domain $\alpha_1 \in [0.35, 1.15]$ and artifact rate $\le 5\%$.
  • Verify ordinary least squares linear regression slope ($m$) and intercept ($b$) calculation with threshold intersection estimates.
  • Verify graceful fallback/empty state when zero valid points exist.
  5. *9-Language Localization & Color Centralization Audit (`TST-PER-043.5`)*:
  • Execute `TranslationParityTest` verifying all 9 locales match with 100% parity for new `strings_hrv.xml` keys.
  • Execute `ThemeColorCentralizationAuditTest` verifying all chart and card colors use centralized `TTColor` tokens.
  6. *Tier 1 Clean-Room Targeted Regression Execution (`TST-PER-043.6`)*:
  • Execute targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"` verifying 100% clean-room test pass rate.













