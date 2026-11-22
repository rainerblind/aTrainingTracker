# Living Test Specification: Post-Workout Analytics & Periods

**Document ID**: `DOC-TST-PERIODS`  
**Version**: 1.0.39  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `aftermath_periods`  
**Prefixes**: `PER`, `PERF`, `RAD`, `SPT`  
**Scope**: Post-workout analytics, period statistics, zone distributions, graphs and visual scrubbers.  
**Total Tests**: 77

---

### TST-PERF-001: **Heatmap Memory Scalability** [Verified]
- **Requirement(s)**: `REQ-MAP-017`
- **Component**: `PeriodMapUtils.kt`, `PeriodsRepository.kt`, `PolylineHeatmapTileProvider.kt`, `PeriodVertexBudgetTest.kt`, `PeriodsRepositoryMemoryTest.kt`, `MapPolylineMemoryStabilityTest.kt`
- **Jira**: ``ATT-319``
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Test Description**: Given 500+ historical workouts with multi-vertex polylines, verify `PeriodsRepository.enrich` clears eager `workoutIdToPathMap` and caps summary thumbnails at `MAX_SUMMARY_THUMBNAIL_TRACKS` (8), `PeriodMapUtils.prepareCardMapPreview` bounds the LRU preview cache to 64 entries, and `simplifyPathMapToBudget` constrains total card (`<= 2,000`, `MAX_CARD_MINI_MAP_VERTICES`) and detail (`<= 8,000`, `MAX_DETAILED_MAP_VERTICES`) heatmap vertices.

### TST-PERF-002: **Cluster Detail Loading Speed** [Verified]
- **Requirement(s)**: `REQ-PER-001`
- **Component**: `WorkoutClustersViewModel.kt`, `WorkoutClustersViewModelTest.kt`
- **Jira**: ``ATT-362``
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Test Description**: Given a cluster with > 30 recorded workouts, verify `WorkoutClustersViewModel.selectCluster(cluster)` immediately transitions `ClusterMapState.isLoading = true`, asynchronously populates `tracks`, decoded `heatmapPaths`, and `memberMarkers`, clears `isLoading = false` upon completion, and cancels superseded jobs on rapid re-selection.

### TST-PERF-003: **Initial Cluster Loading Progress** [Verified]
- **Requirement(s)**: `REQ-PER-003`
- **Component**: `WorkoutClusterRepository.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClusterRepositoryTest.kt`, `WorkoutClustersViewModelTest.kt`
- **Jira**: ``ATT-366``
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Test Description**: Given an empty or un-enriched cluster store (`previewPaths.isEmpty()`), verify `WorkoutClusterRepository.refreshClusters()` emits `MigrationStatus` loading/healing/enrichment progress updates propagated through `WorkoutClustersViewModel.migrationStatus` and resets `migrationStatus` to `null` upon completion.

### TST-PERF-004: **Incremental Period Visibility** [Verified]
- **Requirement(s)**: `REQ-PER-001`, `REQ-DAT-003`
- **Component**: `PeriodsRepository.kt`, `PeriodSummariesDbTest.kt`
- **Jira**: ``ATT-374``
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Test Description**: Given finished workouts spanning multiple months, verify `PeriodsRepository.resyncAllPeriods()` commits transactions per month bucket and emits intermediate growing `_groupedPeriods` snapshots alongside `Syncing [Month]` `MigrationStatus` updates prior to final year rollup.

### TST-PERF-005: **Dual-Phase Sync Efficiency** [Verified]
- **Requirement(s)**: `REQ-PER-004`
- **Component**: `PeriodsRepository.kt`, `PeriodSummariesDbTest.kt`
- **Jira**: ``ATT-380``
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Test Description**: Verify `PeriodsRepository.resyncAllPeriods()` emits monotonic dual-phase `MigrationStatus` updates: Phase 1 (`Reading` progress `0..1f`) transitions strictly into Phase 2 (`phase1Finished = 1.0f` + `Syncing [Month]` -> `Finalizing 0.99f` -> `null`) without regressing to Phase 1, while `_groupedPeriods` grows after each month update.

### TST-PERF-006: **Multi-Phase Progress Feedback** [Verified]
- **Requirement(s)**: `REQ-PER-005`
- **Component**: `MigrationStatus.kt`, `PeriodsTabsScreen.kt`, `PeriodsProgressAndEmptyStateUiTest.kt`
- **Jira**: ``ATT-383``
- **Priority**: Low
- **Type**: Functional
- **Verification Method**: Unit
- **Test Description**: Using `createComposeRule()` with `isPlayServiceAvailable = false` (isolated from Play Services `GoogleMap`), verify `PeriodsTabsScreen` renders the `MigrationStatus` card displaying `title`, Phase 1/2 formatted labels (`migration_phase_label`), distinct active/completed progress bar `ProgressBarRangeInfo` semantics (`1.0f` vs `0.45f`), and hides the card when `migrationStatus == null`.

### TST-PERF-007: **Workout Cluster Migration Progress** [Verified]
- **Requirement(s)**: `REQ-PER-006`
- **Component**: `WorkoutClusterRepository.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClustersTabsScreen.kt`, `WorkoutClusterRepositoryTest.kt`, `WorkoutClustersViewModelTest.kt`
- **Jira**: ``ATT-393``
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Test Description**: Verify `WorkoutClusterRepository.recalculateClustersWithProgress()` and `WorkoutClustersViewModel.recalculateClusters()` emit multi-phase `MigrationStatus` updates (Phase 1 route grouping -> Phase 1 `1.0f` + Phase 2 metadata enrichment -> `null`), toggle `isRecalculating`, and emit `recalculationFinished`.

### TST-PERF-008: **Reactive Period Detail Verification** [Verified]
- **Requirement(s)**: `REQ-PER-007`
- **Component**: `PeriodsViewModel.kt`, `PeriodsRepository.kt`, `PeriodsViewModelTest.kt`
- **Jira**: ``ATT-442``
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Test Description**: Given a `PeriodSummary` opened via `PeriodsViewModel.showPeriodMap(summary)` while background rollup is incomplete (empty/partial `workoutIdToPolylineMap`), verify `mapState.isLoading` sets `true` and reactively hydrates all in-range `tracks` and `workoutIdToHeatmapPathMap` directly via `periodsRepo.getWorkoutsForRange(...)`.

### TST-PERF-009: **Silent Cluster Refresh** [Verified]
- **Requirement(s)**: `REQ-PER-006`
- **Component**: `WorkoutClusterRepository.kt`, `WorkoutClusterRepositoryTest.kt`
- **Jira**: ``ATT-441``
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Test Description**: Verify `WorkoutClusterRepository.refreshClusters(forceShowProgress = false)` leaves `migrationStatus` strictly `null` when persisted `previewPaths` already exist (silent revisit), while `recalculateClustersWithProgress()` and `refreshClusters(forceShowProgress = true)` actively emit non-null `MigrationStatus`.

### TST-PERF-010: **Persistent Previews Audit** [Verified]
- **Requirement(s)**: `REQ-PER-006`
- **Component**: `WorkoutClusterRepository.kt`, `WorkoutClusterDatabaseManager.kt`, `WorkoutClusterPreviewPersistenceTest.kt`
- **Jira**: ``ATT-441``
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Test Description**: Using Robolectric real SQLite (`@Config(sdk = [34], application = Application::class)`), verify `WorkoutClusterRepository.refreshClusters()` persists enriched `previewPaths` to SQLite so a subsequent repository reload/simulated restart finds `previewPaths.isNotEmpty()` and refreshes silently (`migrationStatus == null`).

### TST-PER-009: **Precise Sport Filtering** [Verified]
- **Requirement(s)**: `REQ-PER-008`
- **Component**: `PeriodsFragment.kt`, `WorkoutSummariesListFragment.kt`, `WorkoutSummariesViewModel.kt`, `WorkoutSummariesViewModelFilterTest.kt`, `PeriodsNavigationFilterTest.kt`
- **Jira**: ``ATT-459``
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Test Description**: Verify `WorkoutSummariesViewModel.getFilteredWorkouts(bSportType, startTimeS, endTimeS)` restricts emitted workouts strictly to matching `BSportType` AND `[startTimeS, endTimeS]` temporal bounds, and verify `PeriodsFragment.startWorkoutSummaryList(periodSummary, bSportType)` attaches exact `ARG_BSPORT_TYPE`, `ARG_START_S`, `ARG_END_S` bundle arguments to `WorkoutSummariesListFragment`.

### TST-PER-010: **Heatmap Toggle Removal** [Verified]
- **Requirement(s)**: `REQ-UI-120`
- **Component**: `PeriodsTabsScreen.kt`, `PeriodMapScreen.kt`, `PeriodsViewModel.kt`, `PeriodsProgressAndEmptyStateUiTest.kt`, `PeriodsViewModelTest.kt`
- **Jira**: ``ATT-460``
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Test Description**: Verify via Robolectric Compose UI semantics (`PeriodsTabsScreen` isolated from `GoogleMap`) that no interactive `Whatshot`/heatmap toggle control exists in the header bar while only canonical sport filter icons (`BIKE`, `RUN`, `UNKNOWN`) appear, and verify `PeriodsViewModel.showPeriodMap(...)` unconditionally populates `workoutIdToHeatmapPathMap` deterministically.

### TST-PER-012: **Period Map Sport Filtering** [Verified]
- **Requirement(s)**: `REQ-PER-009`
- **Component**: `PeriodsViewModel.kt`, `PeriodMapScreen.kt`, `PeriodsViewModelTest.kt`
- **Jira**: ``ATT-466``
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Test Description**: Verify `PeriodsViewModel.toggleSportFilter` and `filterPeriodSummary` symmetrically filter `tracks`, `workoutIdToHeatmapPathMap`, `polylines`, and `extremaMarkers` by active `BSportType` so switching between sports (`BIKE` vs `RUN`) updates both vector overlays and heatmap path sets deterministically.

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
- **Component**: `PeriodList.kt`, `EmptyStatePlaceholder.kt`, `strings.xml`, `PeriodsProgressAndEmptyStateUiTest.kt`, `TranslationParityTest.kt`
- **Jira**: ``ATT-488``
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Test Description**: Using `createComposeRule()` isolated from Play Services (`isPlayServiceAvailable = false`), verify `PeriodList(periods = emptyList())` renders `EmptyStatePlaceholder` displaying `R.string.no_periods_available` and `R.string.no_periods_available_hint`, omits them when `periods` is non-empty, and `TranslationParityTest` verifies 9-locale parity for `no_periods_available` and `no_periods_available_hint`.

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
- **Requirement(s)**: `REQ-PER-015`
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
- **Requirement(s)**: `REQ-PER-016`
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
- **Requirement(s)**: `REQ-PER-017`, `REQ-PER-019`
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
- **Requirement(s)**: `REQ-PER-020`
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
- **Requirement(s)**: `REQ-PER-021`
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
- **Requirement(s)**: `REQ-PER-018`, `REQ-PER-022`
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
- **Component**: `HrvReadinessEntryTest.kt`, `HrvReadinessDatabaseManagerTest.kt`, `HrvReadinessCalculatorTest.kt`, `HrvReadinessRepositoryTest.kt`, `TranslationParityTest.kt`, `DatabaseSchemaRegistryTest.kt`, `BackupManagerHrvReadinessTest.kt`
- **Jira**: `ATT-3545`, `ATT-6718`
- **Test Description**:
  1. *Schema Creation & DTO Cursor Mapping Symmetry Unit Test (`TST-PER-038.1`, `HrvReadinessEntryTest.kt` / `HrvReadinessDatabaseManagerTest.kt`)*:
  • Verify `HrvReadiness.db` schema creation (`DB_VERSION = 1`) with all 14 columns, type constraints, and indexes.
  • Verify symmetric mapping between single-item `fromCursor(Cursor)` and batch `fromCursorBatch(Cursor)` over identical SQLite cursor rows per Rule 20.
  2. *Transactional Primary-Day Deduplication & Pin Promotion Test (`TST-PER-038.2`, `HrvReadinessDatabaseManagerTest.kt`)*:
  • Verify that multiple readings on the same `(date_iso, posture)` designate the earliest valid reading as primary (`is_primary_for_day = 1`).
  • Verify that toggling a manual pin promotes the selected reading to primary and demotes previous readings transactionally.
  • Verify that attempting to pin an entry exceeding `MAX_VALID_ARTIFACT_RATE_PCT` (`> 5.0%`) is rejected/ignored, preserving the earlier valid reading as primary.
  • Verify that deleting the primary reading promotes the next earliest valid reading for that calendar day.
  3. *DatabaseSchemaRegistry & Backup Integration Parity Test (`TST-PER-038.3`, `DatabaseSchemaRegistryTest.kt` / `BackupManagerHrvReadinessTest.kt`)*:
  • Verify that `DatabaseSchemaRegistry.getMaxSupportedVersion("HrvReadiness.db")` matches `HrvReadinessDatabaseManager.DB_VERSION` directly per Rule 62.
  • Verify that `DatabaseConnectionManager.getActiveDatabases(context)` includes `HrvReadiness.db`.
  • Verify that `.attbackup` export snapshots `HrvReadiness.db` via `VACUUM INTO` and restores it completely intact.
  4. *Per-Posture Isolation, Artifact Gate & Localized Calibration Progress Test (`TST-PER-038.4`, `HrvReadinessCalculatorTest.kt` / `TranslationParityTest.kt`)*:
  • Verify complete isolation across `SUPINE`, `SEATED`, and `STANDING` posture baselines.
  • Verify that readings with `artifact_rate_pct > 5.0%` are excluded from 7-day and 60-day baseline averages.
  • Verify that $< 14$ baseline days in 60d returns `CalibrationProgressState.BASELINE_60D` and $< 3$ days in 7d returns `CalibrationProgressState.WEEKLY_7D`.
  • Verify 100% translation parity across all 9 locales in `strings_hrv.xml` for calibration progress messages with zero hardcoded English literals.
  5. *Bessel-Corrected Sample SD & SWC Corridor Classification Test (`TST-PER-038.5`, `HrvReadinessCalculatorTest.kt`)*:
  • Verify that $\sigma_{60d}$ evaluates unbiased sample standard deviation with $N-1$ denominator over daily $\ln(\text{rMSSD})$.
  • Verify SWC corridor calculation $[\mu_{60d} - 0.5\sigma_{60d}, \mu_{60d} + 0.5\sigma_{60d}]$.
  • Verify threshold classifications: `OPTIMAL` when inside corridor, `FATIGUED_LOW` when below, and `ABOVE_NORMAL` when above.
  6. *Bounded Window Query Execution Test (`TST-PER-038.6`, `HrvReadinessRepositoryTest.kt`)*:
  • Verify that `HrvReadinessRepository.calculateReadiness()` queries only the bounded `[D-59, D]` date window via `getPrimaryEntriesInWindow()`.
  7. *Tier 1 Clean-Room Targeted Regression Execution (`TST-PER-038.7`)*:
  • Execute targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.hrv.*"` verifying 100% clean-room test pass rate.

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
- **Requirement(s)**: `REQ-PER-032` (`REQ-PER-032.1`..`REQ-PER-032.6`)
- **Component**: `MorningHrvCheckViewModelTest.kt`, `MorningHrvCheckScreenTest.kt`, `HrvReadinessRepositoryTest.kt`, `TranslationParityTest.kt`, `ThemeColorCentralizationAuditTest.kt`
- **Jira**: `ATT-3556`, `ATT-6719`
- **Test Description**:
  1. *Measurement Lifecycle State Machine & UI Ergonomics (`TST-PER-040.1`, `MorningHrvCheckViewModelTest.kt`)*:
  • Verify transition lifecycle: `READY` -> `STABILIZING` (15–30s countdown) -> `MEASURING` (60s/120s countdown) -> `COMPLETED`.
  • Verify cancel and reset behavior returns state machine cleanly to `READY`.
  • Verify duration selector uses `SingleChoiceSegmentedButtonRow` and `keepScreenOn` is active during countdown.
  2. *Sticky Posture & Duration Preference Contract Test (`TST-PER-040.2`, `MorningHrvCheckViewModelTest.kt`)*:
  • Verify that selecting `SEATED` or `STANDING` persists to `SP_HRV_LAST_POSTURE` and reloads on new ViewModel instances.
  • Verify duration selection (60s vs. 120s) configuration.
  3. *Cold-Start Sensor Search, Ingestion & Scoping Test (`TST-PER-040.3`, `MorningHrvCheckViewModelTest.kt`)*:
  • Verify that `MorningHrvCheckViewModel` invokes `banalServiceRepository.startSearchingForPairedDevices()` on initialization.
  • Verify that `onCleared()` detaches `rrBeatListener` without calling `unbindFromBANALService()`.
  • Verify zero workout generation in `WorkoutSummaries.db`.
  4. *Live Causal Filtering & Post-Hoc Centered Filter Test (`TST-PER-040.4`, `MorningHrvCheckViewModelTest.kt`)*:
  • Verify that `handleRrBeat` evaluates `RrArtifactFilter.filterLiveCausal` live during `STABILIZING` and `MEASURING`, surfacing artifact percentage and amber warning if artifact rate $> 5.0\%$.
  • Verify that completed measurement runs post-hoc `RrArtifactFilter.filterBidirectional` as the single source of truth for persisted metrics.
  5. *Pre-Save In-Memory Readiness Preview & Persistence Test (`TST-PER-040.5`, `MorningHrvCheckViewModelTest.kt`)*:
  • Verify that completion view previews `ReadinessStatus` calculated in-memory with the newly measured entry combined with existing historical records before saving.
  • Verify that saving persists `HrvReadinessEntry` into `HrvReadinessRepository` (`HrvReadiness.db`).
  6. *Localization Parity, Instruction Hygiene & Semantic Color Audit (`TST-PER-040.6`, `TranslationParityTest.kt`, `ThemeColorCentralizationAuditTest.kt`)*:
  • Verify 100% translation parity across all 9 locales in `strings_hrv.xml` with zero hardcoded English or `Locale.US` formatters.
  • Verify `morning_hrv_instructions_desc` across all 9 locales does not contain bladder emptying phrasing and verifies rest phrasing (`TranslationParityTest.kt`).
  • Verify zero raw `Color.White` literals on readiness pills, adhering to `TTColor.ReadinessOnStatus`.
  7. *Tier 1 Clean-Room Targeted Regression Execution (`TST-PER-040.7`)*:
  • Execute targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"` verifying 100% clean-room test pass rate.

### TST-PER-041: **Longitudinal HRV Readiness Trend, Posture SWC Corridor & Periods Integration Suite** [Verified]
- **Requirement(s)**: `REQ-PER-033` (`REQ-PER-033.1`..`REQ-PER-033.6`)
- **Component**: `HrvReadinessTrendMathTest.kt`, `PeriodsViewModelTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-3557`, `ATT-6721`
- **Test Description**:
  1. *Posture Filtering, Deduplication & Series Calculation Test (`TST-PER-041.1`, `HrvReadinessTrendMathTest.kt`)*:
  • Verify that `HrvReadinessTrendMath.calculateTrendSeries` segregates entries by posture, selects daily primary readings (`is_primary_for_day` then timestamp), and filters out artifact rate > 5.0%.
  • Verify default posture selection picks posture with the highest count of readings.
  • Verify retention of daily training loads on days without morning HRV readings, and calendar-day epoch projection.
  2. *7-Day Rolling Mean & 60-Day Bessel-Corrected SWC Corridor Test (`TST-PER-041.2`, `HrvReadinessTrendMathTest.kt`)*:
  • Verify 7-day rolling window: null if $N_{7d} < 3$, mean of daily $\ln(\text{rMSSD})$ when $N_{7d} \ge 3$.
  • Verify 60-day baseline corridor: null if $N_{60d} < 14$, $\mu_{60d} \pm 0.5\sigma_{60d}$ with $N-1$ Bessel denominator when $N_{60d} \ge 14$.
  • Verify status classification: `OPTIMAL`, `FATIGUED_LOW`, `ABOVE_NORMAL`, `CALIBRATING`.
  3. *Training Load Alignment & Touch Scrubbing Math Test (`TST-PER-041.3`, `HrvReadinessTrendMathTest.kt`)*:
  • Verify time-series alignment of daily training load (TSS/ATL) with HRV calendar dates and retention on non-HRV workout dates.
  • Verify touch coordinate projection correctly resolves nearest calendar-day epoch point and tooltip payload.
  4. *PeriodsViewModel Integration & Graceful Empty State Test (`TST-PER-041.4`, `PeriodsViewModelTest.kt`)*:
  • Verify `PeriodsViewModel` exposes `hrvReadinessTrendState` Flow.
  • When `HrvReadinessRepository` has zero entries, verify state indicates empty/omitted.
  • When entries exist, verify populated trend state with active posture initialized to `defaultPosture` (not eagerly defaulted to SUPINE) and reactive collection of `workoutRepo.allWorkouts`.
  5. *9-Language Localization Parity Test (`TST-PER-041.5`, `TranslationParityTest.kt`)*:
  • Verify all new HRV readiness trend strings in `strings_hrv.xml` match 100% across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with valid format specifiers.
  6. *Tier 1 Clean-Room Targeted Regression Execution (`TST-PER-041.6`)*:
  • Execute targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"` verifying 100% clean-room test pass rate.

### TST-PER-042: **Lossless WorkoutRrIntervals, Centered Recompute & Garmin FIT HrvMesg Suite** [Verified]
- **Requirement(s)**: `REQ-PER-034` (`REQ-PER-034.1`..`REQ-PER-034.6`)
- **Component**: `WorkoutRrDatabaseManagerTest.kt`, `RrBeatTimestampReconstructionTest.kt`, `HrvPostWorkoutRecomputerTest.kt`, `FitFileWriterHrvTest.kt`, `LegacyImportEngineFitHrvTest.kt`, `TrackerServiceTest.kt`
- **Jira**: `ATT-3543`, `ATT-6716`
- **Test Description**:
  1. *WorkoutRrDatabaseManager & Schema Integrity Test (`TST-PER-042.1`, `WorkoutRrDatabaseManagerTest.kt`)*:
  • Verify creation of table `workout_rr_intervals` with columns `workout_id`, `beat_index`, `beat_timestamp_ms`, `rr_ms`, `artifact_flag`, `gap_beats`.
  • Verify `UNIQUE(workout_id, beat_index)` index in `WorkoutRrDbHelper` v2 and upgrade path from v1.
  • Verify batch insertion, conflict handling, and query by `workout_id` ordered by `beat_index`.
  • Verify cascading deletion via `WorkoutDeletionHelper` when parent workout is removed.
  • Verify registration in `DatabaseSchemaRegistry`.
  2. *Beat Timestamp Reconstruction & Incremental Flushing Test (`TST-PER-042.2`, `RrBeatTimestampReconstructionTest.kt`, `TrackerServiceTest.kt`)*:
  • Verify cumulative timestamp progression $t_k = t_{k-1} + \text{rr\_ms}_k$ preserves exact interval durations.
  • Verify drift resynchronization when $|t_{\text{pkt}} - t_{\text{beat}}| > 2000\text{ ms}$.
  • Verify gating in `TrackerService`: ignore pre-start and paused beats (`workoutId <= 0`).
  • Verify periodic batched flushing to SQLite and restoration of `mRrBeatIndexCounter` to `MAX(beat_index) + 1` on workout resume.
  3. *Two-Pointer 5s Grid Recomputation & Session SDNN Test (`TST-PER-042.3`, `HrvPostWorkoutRecomputerTest.kt`)*:
  • Verify two-pointer centered sliding window evaluation for DFA $\alpha_1$ ($t \pm 60\text{s}$) and $\text{rMSSD}$ ($t \pm 30\text{s}$) on a 5s grid, step-latching values to intermediate 1-Hz samples.
  • Verify $O(N)$ computational scaling on multi-hour synthetic datasets.
  • Verify calculation and persistence of unbiased session `SDNN` into `WorkoutSummaries` session extrema.
  • Verify clean sequencing after `endWorkout()` summary extrema and dispatch of `WorkoutRepository.refreshWorkout(workoutId)`.
  4. *Garmin FIT HrvMesg Export Interleaving Test (`TST-PER-042.4`, `FitFileWriterHrvTest.kt`)*:
  • Verify `FitFileWriter` exports standard `HrvMesg` messages interleaved with `RecordMesg` trackpoints when RR data exists.
  • Verify time intervals are scaled in fractional seconds (FIT standard).
  • Verify workouts without RR intervals write zero `HrvMesg` messages.
  5. *Multi-Minute Garmin FIT Import & Anchor Reconstruction Test (`TST-PER-042.5`, `LegacyImportEngineFitHrvTest.kt`)*:
  • Verify `FitWorkoutParser` associates `HrvMesg` intervals with chronological `RecordMesg` timestamp anchors (`FitRrBeat(rrMs, anchorTimestampMs)`).
  • Verify `LegacyImportEngine` passes active anchor timestamps to reconstructor, preventing false 2000 ms collapse across multi-minute workouts.
  • Verify extracted beats are persisted in `WorkoutRr.db` and non-null post-workout centered HRV streams are recomputed across the entire workout duration.
  6. *Tier 1 Clean-Room Targeted Regression Execution (`TST-PER-042.6`)*:
  • Execute targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.exporter.writer.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.tracker.*" --tests "com.atrainingtracker.trainingtracker.build.*"` verifying 100% clean-room test pass rate.

### TST-PER-043: **Interactive DFA a1/rMSSD Charts & a1 Scatter Plot Suite** [Verified]
- **Requirement(s)**: `REQ-PER-035` (`REQ-PER-035.1`..`REQ-PER-035.6`)
- **Component**: `TelemetryMetricGraphHrvTest.kt`, `HrvAlpha1ScatterChartTest.kt`, `HrvPathPointEnrichmentTest.kt`, `ThemeColorCentralizationAuditTest`, `TranslationParityTest`
- **Jira**: `ATT-3544`, `ATT-6717`
- **Test Description**:
  1. *PathPoint HRV Enrichment Unit Test (`TST-PER-043.1`, `HrvPathPointEnrichmentTest.kt`)*:
  • Verify `WorkoutRepository` enriches `PathPoint` instances with `hrvDfaA1`, `hrvRmssd`, and `hrvArtifactRate` from `WorkoutSamples.db`.
  • Verify `@JvmOverloads` constructor backwards compatibility when HRV parameters are omitted (defaults to null).
  2. *TelemetryMetricGraph DFA a1 & rMSSD Tracks (`TST-PER-043.2`, `TelemetryMetricGraphHrvTest.kt`)*:
  • Verify `TelemetryMetricGraph` renders continuous paths for `TelemetryMetricType.HRV_DFA_A1` and `TelemetryMetricType.HRV_RMSSD`.
  • Verify horizontal plot bounds (`startPadding = 50.dp`) match `ElevationProfile` and other telemetry graphs per Rule 64.
  • Verify dashed horizontal threshold lines and Y-axis labels at athlete-configured `dfaA1ThresholdT1` and `dfaA1ThresholdT2` (defaulting to 0.75 and 0.50) when `metricType == HRV_DFA_A1`.
  • Verify Y-axis clamped within $[0.2, 1.4]$ for DFA $\alpha_1$.
  3. *Artifact Gap & Non-Interpolation Hatching Test (`TST-PER-043.3`, `TelemetryMetricGraphHrvTest.kt`)*:
  • Verify path breaks and does NOT draw continuous line segments across dropouts where `hrvArtifactRate > 5.0%` or `hrvDfaA1 == null`.
  • Verify hatched background path overlay is rendered across artifact-suppressed regions.
  4. *HrvAlpha1ScatterChart Dynamics & Regression Line Test (`TST-PER-043.4`, `HrvAlpha1ScatterChartTest.kt`)*:
  • Verify scatter points are plotted for $(\text{HR}, \alpha_1)$, $(\text{Power}, \alpha_1)$, and $(\text{Pace}, \alpha_1)$ strictly within valid transition domain $\alpha_1 \in [0.35, 1.15]$ and artifact rate $\le 5\%$ with 5-second sample decimation.
  • Verify ordinary least squares linear regression slope ($m$) and intercept ($b$) calculation with athlete-configured threshold intersection estimates.
  • Verify graceful fallback/empty state when zero valid points exist.
  5. *9-Language Localization & Color Centralization Audit (`TST-PER-043.5`)*:
  • Execute `TranslationParityTest` verifying all 9 locales match with 100% parity for new `strings_hrv.xml` keys.
  • Execute `ThemeColorCentralizationAuditTest` verifying all chart and card colors use centralized `TTColor` tokens.
  6. *Tier 1 Clean-Room Targeted Regression Execution (`TST-PER-043.6`)*:
  • Execute targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"` verifying 100% clean-room test pass rate.
  7. *Deduplicated Rendering & Wrapper Cleanliness (`TST-PER-043.7`)*:
  • Verify canonical invocation in `WorkoutDetailTabbedContent.kt`, removal from `MapDetailLayout.kt`, and zero references to `ui.aftermath.hrv.*`.

### TST-PER-044: **Confidence-Gated HRVT1/HRVT2 Threshold Estimation & Summary Card Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PER-036` (`REQ-PER-036.1`..`REQ-PER-036.6`)
- **Component**: `HrvThresholdEstimatorTest.kt`, `HrvThresholdSummaryCardTest.kt`, `TranslationParityTest`
- **Jira**: `ATT-3554`
- **Test Description**:
  1. *Fresh-Window Telemetry Filtering Test (`TST-PER-044.1`, `HrvThresholdEstimatorTest.kt`)*:
  • Verify `HrvThresholdEstimator` ignores samples before $t = 600\text{ s}$ and after $t = 3600\text{ s}$ for free workouts.
  • Verify samples with artifact rate $> 5.0\%$ or $\alpha_1$ outside $[0.35, 1.15]$ are discarded.
  2. *Decimated Grid Decorrelation & R² Calculation Test (`TST-PER-044.2`, `HrvThresholdEstimatorTest.kt`)*:
  • Verify samples are decimated into 60s window averages ($N_{\text{eff}} = \lfloor K / 12 \rfloor$).
  • Verify $R^2$ is computed against decimated points rather than 5s raw points, preventing autocorrelation bias.
  • Verify continuous ramp Mean Response Time correction ($\tau_{\text{MRT}} = 45\text{ s}$) shifts timestamps accurately.
  3. *Independent Span Gating & Extrapolation Prevention Test (`TST-PER-044.3`, `HrvThresholdEstimatorTest.kt`)*:
  • Verify session with $\alpha_1 \in [0.60, 1.05]$ emits valid $HRVT_1$ and returns $HRVT_2 = \text{null}$ with diagnostic reason `INSUFFICIENT_SPAN`.
  • Verify session with $\alpha_1 \in [0.45, 0.60]$ emits valid $HRVT_2$ and returns $HRVT_1 = \text{null}$ with diagnostic reason `INSUFFICIENT_SPAN`.
  • Verify out-of-span linear extrapolation is strictly prevented.
  4. *Three-Tier Confidence Classification & Diagnostic Reasons Test (`TST-PER-044.4`, `HrvThresholdEstimatorTest.kt`)*:
  • Verify `HIGH` confidence assignment when span met, $R^2 \ge 0.70$, artifact rate $\le 3\%$, and $N_{\text{eff}} \ge 10$.
  • Verify `MEDIUM` confidence assignment when span met, $R^2 \ge 0.55$, artifact rate $\le 5\%$, and $N_{\text{eff}} \ge 8$.
  • Verify `LOW` confidence assignment when $R^2 < 0.55$, artifact rate $> 5\%$, or $N_{\text{eff}} < 8$, with accurate localized diagnostic messages.
  5. *HrvThresholdSummaryCard Compose Rendering Test (`TST-PER-044.5`, `HrvThresholdSummaryCardTest.kt`)*:
  • Verify card displays estimated $HRVT_1$ and $HRVT_2$ values with appropriate units (bpm, W, min/km).
  • Verify protocol provenance badge (`FREE_WORKOUT`, `GUIDED_STEP`, `GUIDED_RAMP`) and confidence chip rendering.
  • Verify graceful omission when workout lacks HRV data.
  6. *9-Language Localization Parity Test (`TST-PER-044.6`, `TranslationParityTest`)*:
  • Verify all new strings in `strings_hrv.xml` exist across all 9 language directories with identical XML keys, parameter specifiers, and literal `\n` linebreaks.
  7. *Tier 1 Clean-Room Targeted Regression Execution (`TST-PER-044.7`)*:
  • Execute targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"` verifying 100% clean-room test pass rate.

### TST-RAD-005: **Workout Traffic Persistence, Summary Model & Card Rendering Contract Test** [Verified]
- **Requirement(s)**: `REQ-RAD-005`
- **Component**: `WorkoutExportHeader.kt`, `WorkoutRepository.kt`, `RadarTrafficSummaryCard.kt`, `RadarTrafficSummaryTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-5088`
- **Test Description**:
  1. *Sample Streaming & Persistence Contract Test*: Verify `WorkoutSampleRecord` parses latched radar metrics (`radarThreatCount`, `radarClosestDistanceM`, `radarApproachSpeedMps`, `radarVehicleCount`) with zero SQLite schema migrations.
  2. *Traffic Extrema & Summary Derivation Unit Test*: Verify `RadarTrafficSummary` evaluates total vehicles, density (veh/km, veh/h), max approach speed, and max absolute vehicle speed ($v_{\text{rider}} + v_{\text{approach}}$).
  3. *Summary Card Rendering & Non-Radar Omission Contract Test*: Verify `RadarTrafficSummaryCard` renders under `WorkoutDetailTabbedContent.kt` (`DETAILS`) when radar data is present and is gracefully omitted for non-radar rides.
  4. *9-Language Localization Parity Audit*: Verify 100% translation parity across all 9 supported locales in `strings.xml` for all traffic summary card string keys.















### TST-PER-045: **HRV Threshold Training Zone Comparison, Consensus Gating & Power Zone Update Suite** [Verified]
- **Requirement(s)**: `REQ-PER-037` (`REQ-PER-037.1`..`REQ-PER-037.6`)
- **Component**: `HrvZoneComparisonMathTest.kt`, `HrvThresholdSummaryCardTest.kt`, `TranslationParityTest`
- **Jira**: `ATT-3555`
- **Test Description**:
  1. *Power Zone Delta & Ceiling Calculation Test (`TST-PER-045.1`, `HrvZoneComparisonMathTest.kt`)*:
  • Verify `HrvZoneComparisonMath` computes $\Delta P_{\text{T1}} = P_{\text{HRVT1,ss}} - z2Max$ where $z2Max = \text{round}(0.75 \times FTP)$.
  • Verify $\Delta P_{\text{T2}} = P_{\text{HRVT2,ss}} - FTP$ against configured Functional Threshold Power.
  • Verify delta fields evaluate to `null` gracefully when threshold estimates are absent or unestimated (`INSUFFICIENT_SPAN`).
  2. *Informational Heart Rate Comparison & Non-Overwrite Invariant Test (`TST-PER-045.2`, `HrvZoneComparisonMathTest.kt`)*:
  • Verify $\Delta \text{HR}_{\text{T1}} = \text{HR}_{\text{HRVT1}} - z2Max$ and $\% HR_{\max} = \text{round}(100 \times \text{HR}_{\text{HRVT1}} / HR_{\max})$ calculation.
  • Verify $\Delta \text{HR}_{\text{T2}} = \text{HR}_{\text{HRVT2}} - z4Max$ and $\% HR_{\max}$ calculation.
  • Verify heart rate comparisons produce zero mutations or suggestions to overwrite $HR_{\max}$ or configured HR zones.
  3. *Multi-Session Consensus & Guided Test Gating Logic Test (`TST-PER-045.3`, `HrvZoneComparisonMathTest.kt`)*:
  • Verify consensus gate unlocks (`ConsensusGateStatus.UNLOCKED`) when session protocol is `GUIDED_STEP` or `GUIDED_RAMP` with `Confidence.HIGH`.
  • Verify consensus gate unlocks when $M \ge 3$ recent sessions (within 30 days) with `HIGH` or `MEDIUM` confidence have $HRVT_2$ power values within $\pm 10\text{ W}$ ($\max(P) - \min(P) \le 10\text{ W}$).
  • Verify consensus gate locks (`ConsensusGateStatus.LOCKED`) for single free workouts, sessions with `Confidence.LOW`, sessions $> 30$ days old, or sessions with spread $> 10\text{ W}$.
  4. *Median FTP Consensus Aggregation Test (`TST-PER-045.4`, `HrvZoneComparisonMathTest.kt`)*:
  • Verify suggested FTP is computed as the statistical median of qualifying sessions (e.g. median of $\{278\text{ W}, 280\text{ W}, 284\text{ W}\} = 280\text{ W}$).
  5. *UI Rendering of Zone Comparison Section & Delta Badges (`TST-PER-045.5`, `HrvThresholdSummaryCardTest.kt`)*:
  • Verify `HrvThresholdSummaryCard` renders Training Zone Comparison section when configured zones exist.
  • Verify power and HR delta chips display formatted $+/-$ deltas and $\% HR_{\max}$ values.
  • Verify "Update Power Zones" button is enabled when unlocked and disabled with explanatory lock reason when locked.
  6. *Confirmation Dialog & SettingsDataStore FTP Update Test (`TST-PER-045.6`, `HrvThresholdSummaryCardTest.kt`)*:
  • Verify clicking "Update Power Zones" launches confirmation dialog displaying target FTP, protocol provenance, ramp slope & MRT adjustment, and MLSS warning.
  • Verify confirming updates `SettingsDataStore.setFtp` and triggers Coggan power zone recalculation while leaving HR zones untouched.
  7. *9-Language Localization Parity Test (`TST-PER-045.7`, `TranslationParityTest`)*:
  • Verify all 18 zone comparison and consensus gating string keys exist with 100% parity across all 9 `strings_hrv.xml` files.
  8. *Threshold-Anchored HR Zone Boundary Calculation Test (`TST-PER-045.8`, `ZoneCalculationMathTest.kt`)*:
  • Verify `calculateThresholdAnchoredHeartRateZones` anchors $Z_2 = HRVT_1$ and $Z_4 = HRVT_2$.
  • Verify $Z_3 = \text{round}((HRVT_1 + HRVT_2)/2.0)$ and $Z_1$ calculation with and without $HR_{\text{rest}}$.
  • Verify integer monotonicity $Z_1 < Z_2 < Z_3 < Z_4 < HR_{\max}$.
  9. *Heart Rate Consensus Gating & Median Aggregation Test (`TST-PER-045.9`, `HrvZoneComparisonMathTest.kt`)*:
  • Verify consensus unlocks on `Confidence.HIGH` Guided Ramp/Step Test or $M \ge 3$ sessions within 30 days agreeing within $\le 6\text{ bpm}$.
  • Verify consensus locks on spread $> 6\text{ bpm}$ or $< 3$ sessions.
  • Verify median calculation for $HRVT_1$ and $HRVT_2$.
  10. *Summary Card HR Zone Update UI & Modal Confirmation Flow Contract Test (`TST-PER-045.10`, `HrvThresholdSummaryCardTest.kt`, `TrackOnMapAftermathViewModelTest.kt`)*:
  • Verify `HrvThresholdSummaryCard` renders "Update HR Zones" button with correct enabled/disabled status.
  • Verify modal confirmation dialog renders current vs new boundaries and triggers `updateHrZones` on confirm.
  • Verify `TrackOnMapAftermathViewModel.updateHrZones` saves zones via `SettingsDataStore.saveCalculatedHeartRateZones(method = "HRVT_ANCHORS")`.
  11. *9-Language Localization Parity for HR Consensus & Confirmation Dialog Test (`TST-PER-045.11`, `TranslationParityTest`)*:
  • Verify all newly added HR zone comparison, consensus gating, and confirmation dialog string keys exist with 100% parity across all 9 `strings_hrv.xml` files.
 (ATT-3555: [Verbesserung] Compare estimated HRVT1/HRVT2 against configured training zones with multi-session consensus and anchor-based updates (ZoneCalculationMath))
 (ATT-7310: [Feature] Enable anchor-based Heart Rate training zone updates from HRVT1/HRVT2 consensus)

### TST-PER-046: **DFA a1 Autonomic Durability, Drift & Iso-Intensity Bins Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PER-038` (`REQ-PER-038.1`..`REQ-PER-038.6`)
- **Component**: `HrvDurabilityCalculatorTest.kt`, `HrvDurabilityCardTest.kt`, `HrvPostWorkoutRecomputerTest.kt`, `WorkoutDataMapperTest.kt`, `TranslationParityTest`
- **Jira**: `ATT-4970`
- **Test Description**:
  1. *Fresh Baseline & Late Segment Window Selection Test (`TST-PER-046.1`, `HrvDurabilityCalculatorTest.kt`)*:
  • Verify fresh baseline window strictly bounds samples to $t \in [900, 2700]\text{ s}$ (`15–45 min`).
  • Verify late fatigue segment strictly bounds samples to $t > 3600\text{ s}$ (`> 60 min`).
  • Verify artifact rate gate excludes samples with artifact rate $> 5.0\%$.
  2. *Iso-Intensity Matched-Power & Pace Bin Filtering Test (`TST-PER-046.2`, `HrvDurabilityCalculatorTest.kt`)*:
  • Verify median reference intensity ($P_{\text{ref}}$ or $v_{\text{ref}}$) computation over the fresh baseline.
  • Verify sample filtering strictly matches $[0.90 \cdot I_{\text{ref}}, 1.10 \cdot I_{\text{ref}}]$.
  • Verify autonomic efficiency ratio normalization: $\alpha_{1,\text{norm}} = \alpha_1 \cdot (I / I_{\text{ref}})$.
  3. *Autonomic Durability Drift & 10-Minute Minimum Guard Test (`TST-PER-046.3`, `HrvDurabilityCalculatorTest.kt`)*:
  • Verify drift calculation: $\Delta\alpha_{1,\%} = ((\overline{\alpha}_{1,\text{late}} - \overline{\alpha}_{1,\text{fresh}}) / \overline{\alpha}_{1,\text{fresh}}) \times 100\%$.
  • Verify returning `null` / `INSUFFICIENT_MATCHED_DATA` when fresh baseline matched data is $< 600\text{ s}$ (120 samples).
  • Verify returning `null` / `INSUFFICIENT_MATCHED_DATA` when late segment matched data is $< 600\text{ s}$ (120 samples).
  4. *15-Minute Slice Trajectory Aggregation Test (`TST-PER-046.4`, `HrvDurabilityCalculatorTest.kt`)*:
  • Verify mean normalized $\alpha_1$ is aggregated for each 15-minute slice (`15–30m`, `30–45m`, `45–60m`, etc.).
  • Verify slices with zero valid matched points evaluate to null.
  5. *ExtremaType Dynamic Persistence & WorkoutData Mapping Test (`TST-PER-046.5`, `WorkoutDataMapperTest.kt`)*:
  • Verify `ExtremaType.DFA_A1_FRESH_BASELINE`, `DFA_A1_LATE_MEAN`, and `DFA_A1_DRIFT_PCT` can be inserted and queried from `WorkoutSummaries.TABLE_EXTREMA_VALUES` with zero SQLite schema migration.
  • Verify `WorkoutDataMapper` maps extrema values to `WorkoutData.dfaA1FreshBaseline`, `dfaA1LateMean`, and `dfaA1DurabilityDriftPct`.
  6. *UI Rendering of HrvDurabilityCard & Graceful Omission Test (`TST-PER-046.6`, `HrvDurabilityCardTest.kt`)*:
  • Verify `HrvDurabilityCard` renders fresh baseline, late mean, drift percentage, and slice trajectory when metrics exist.
  • Verify `HrvDurabilityCard` is omitted completely for non-HRV workouts or when durability metrics are absent.
  7. *9-Language Localization Parity Test (`TST-PER-046.7`, `TranslationParityTest`)*:
  • Verify all 11 durability string keys exist with 100% parity across all 9 `strings_hrv.xml` files with matching formatting tokens.
  (ATT-4970: [Feature] Model DFA a1 autonomic durability and drift across multi-hour workouts (paired with ATT-4842 Heart Efficiency))


### TST-PER-047: **PeriodsRepository Memory Footprint, Bounded LRU Cache & Lite Mode Map Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PER-039` (`REQ-PER-039.1`..`REQ-PER-039.4`)
- **Component**: `PeriodsRepository.kt`, `PeriodSummaryCard.kt`, `PeriodMapUtils.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepositoryMemoryTest.kt`
- **Jira**: `ATT-7666`
- **Test Description**:
  1. *Empty workoutIdToPathMap in Singleton StateFlow Test (`TST-PER-047.1`, `PeriodsRepositoryMemoryTest.kt`)*:
     • Load and enrich period summaries with simulated workout polylines.
     • Assert `summary.workoutIdToPathMap.isEmpty()` in `_groupedPeriods` to verify zero eager `LatLng` allocation.
  2. *Bounded polylines Count Test (`TST-PER-047.2`, `PeriodsRepositoryMemoryTest.kt`)*:
     • Enrich a period containing > 10 workouts with polylines.
     • Assert `summary.polylines.size <= 8` (`MAX_SUMMARY_THUMBNAIL_TRACKS`), verifying full collection duplication is eliminated.
  3. *LRU Preview Cache Hit & Eviction Test (`TST-PER-047.3`, `PeriodMapUtilsTest.kt`)*:
     • Invoke `prepareCardMapPreviewSync` repeatedly for the same period summary.
     • Verify cache hit returns identical `PeriodCardMapState` without re-running `PolyUtil.decode`.
     • Verify LRU capacity bounds storage to 64 items.
  4. *GoogleMap Lite Mode Configuration Audit (`TST-PER-047.4`, `PeriodSummaryCardTest.kt`)*:
     • Verify `GoogleMapOptions().liteMode(true)` is provided to `GoogleMap` in `PeriodSummaryCard` when rendering vector polylines.
  5. *Tier 1 Targeted Package Regression Execution (`TST-PER-047.5`)*:
     • `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"` asserting 100% pass rate.


### TST-PER-048: **RouteCluster In-Memory Propagation, Filter Chip & Bidirectional Route Sync Test Suite** [Verified]
- **Requirement(s)**: `REQ-PER-040` (`REQ-PER-040.1`..`REQ-PER-040.4`)
- **Component**: `WorkoutClusterRepository.kt`, `WorkoutRepository.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClustersScreen.kt`, `PeriodsViewModel.kt`, `WorkoutSummariesViewModel.kt`, `RoutesRepository.kt`
- **Jira**: `ATT-7758`
- **Test Description**:
  1. *Cluster Rename, Delete, Merge & Split Propagation Test (`TST-PER-048.1`)*:
     • Verify that `WorkoutClusterRepository.updateCluster()`, `deleteCluster()`, `mergeClusters()`, and `splitCluster()` propagate `clusterId`/`clusterName` immediately to `WorkoutRepository._allWorkouts` without mutating `WorkoutData.workoutName`.
     • Verify that `WorkoutClustersViewModel._clusterWorkouts`, `_peekedWorkoutDataWithTrack`, and `_selectedCluster` reflect mutations immediately.
  2. *Periods Peek Sheet Reactive Reconciliation Test (`TST-PER-048.2`)*:
     • Verify that `PeriodsViewModel._peekedWorkoutDataWithTrack` reconciles immediately when `WorkoutRepository.allWorkouts` emits an updated `WorkoutData`.
  3. *Active Filter Chip & Filter Criteria Live Synchronization Test (`TST-PER-048.3`)*:
     • Verify that `WorkoutSummariesViewModel.filterCriteria` updates `clusterName` on rename, clears on delete, and transitions on merge.
  4. *Bidirectional Non-Recursive Route-Cluster Name Sync Test (`TST-PER-048.4`)*:
     • Verify that `WorkoutClusterRepository.updateCluster()` syncs linked routes in `RoutesRepository` without infinite recursion, and `RoutesRepository.updateRouteSummary()` refreshes `WorkoutClusterRepository` and propagates to `WorkoutRepository`.
  5. *Plain JVM Isolation & Targeted Regression Test (`TST-PER-048.5`)*:
     • Verify all unit tests pass under plain JVM without `PreferenceManager`/IO leaks (`coding_standards.md` §5.5).


### TST-PER-049: **RouteCluster Whitespace Normalization & Startup/Refresh Self-Healing Test Suite** [Verified]
- **Requirement(s)**: `REQ-PER-041` (`REQ-PER-041.1`, `REQ-PER-041.2`)
- **Component**: `WorkoutClusterDatabaseManager.kt`, `WorkoutClusterEngine.kt`, `WorkoutClusterRepository.kt`, `WorkoutClustersViewModel.kt`, `EditWorkoutViewModel.kt`, `WorkoutClusterHeatmapScreen.kt`, `RoutesRepository.kt`
- **Jira**: `ATT-7757`
- **Test Description**:
  1. *Write, Lookup & Auto-Naming Whitespace Normalization Test (`TST-PER-049.1`)*:
     • Verify that `updateClusterIdentity`, `addManualCluster`, `executeClusterSplit`, `EditWorkoutViewModel.createNewCluster`, `manuallyCreateCluster`, `syncRouteNameChange`, `splitCluster`, `insertCluster`, `updateCluster`, `isNameTaken`, `findUniqueClusterName`, and `formatClusterWorkoutName` trim leading/trailing whitespace, reject blank-only names, and never emit `"  #N"` double spaces.
  2. *Startup & Refresh Self-Healing & Collision Propagation Test (`TST-PER-049.2`)*:
     • Verify that `WorkoutClusterRepository.loadAllClustersLightweight()` and `refreshClusters()` normalize untrimmed rows (e.g. `"  Kurze Runde  "`), resolve collisions cleanly (`"Kurze Runde var 2"` when `"Kurze Runde"` exists), persist healed names in SQLite, and propagate healed names to `WorkoutRepository.onClusterRenamed`, `RoutesRepository.syncClusterNameChange`, and `clusterMutationEvents`.
  3. *Localization & Regression Isolation Check (`TST-PER-049.3`)*:
     • Verify `TranslationParityTest` and targeted unit tests pass cleanly under plain JVM without unmocked Android/IO dependencies (`coding_standards.md` §5.5).


---

### TST-SPT-005: Retrospective Filtering, Cluster Math, Lifetime Evaluation & ViewModel Tests [Verified]
- **Target Requirement**: `REQ-SPT-001`, `REQ-SPT-002`, `REQ-SPT-003`
- **Target Verification Component**: `SportingJourneyRepositoryTest.kt`, `SportingJourneyViewModelTest.kt`
- **Verification Type**: Automated Unit & State Machine Verification
- **Test Description**:
  1. *Recurring Route Cluster Math Test (`TST-SPT-005.1`, `SportingJourneyRepositoryTest.kt`)*:
  • Verify that clusters with repeat visits $\ge 3$ are counted as recurring routes (`favoriteRoutesCount = 1`, `recurringRoutesCount = 1`).
  • Verify that singleton clusters and clusters with $< 3$ visits are excluded from favorite routes.
  • Verify that ritual workouts count only workouts in recurring clusters, while all remaining workouts evaluate as explorer workouts.
  2. *Sport Inclusivity & Running Cadence Suppression Test (`TST-SPT-005.2`, `SportingJourneyRepositoryTest.kt`)*:
  • Verify that under `SportingSportFilter.ALL`, all finished workouts across all recorded sports (`BIKE`, `RUN`, `SWIM`, `UNKNOWN`) are aggregated into total workouts and distance.
  • Verify that when `sportFilter == SportingSportFilter.RUNNING`, bicycle pedal revolutions and cadence ride counts are strictly suppressed to 0.
  • Verify that under `ALL`, pedal revolutions are aggregated exclusively from cycling sessions.
  3. *Global Lifetime History & Full Year Range Test (`TST-SPT-005.3`, `SportingJourneyRepositoryTest.kt`)*:
  • Verify that under `SportingHorizon.Year(2026)`, `daysSinceFirstWorkout` evaluates globally from the earliest database record (e.g. 2012) and unlocks the `Pioneer Athlete` badge ($> 365$ days).
  • Verify that `availableYears` returns all distinct years without artificial `.take(5)` truncation.
  4. *ViewModel State Keying & Cancellation Test (`TST-SPT-005.4`, `SportingJourneyViewModelTest.kt`)*:
  • Verify that `setHorizon` and `setSportFilter` immediately key `uiState.horizon` and `uiState.sportFilter` with `isLoading = true`.
  • Verify that rapid filter changes cancel obsolete in-flight repository coroutine jobs.
  (ATT-7321: [Verbesserung] SportingJourneyRepository & Aggregation: Fix recurring route cluster math, sport filtering, historical year range, and lifetime evaluation)


---

### TST-SPT-006: SportingJourney UI & UX Redesign, M3 Hierarchy, Grid Layout & Locale Formatting Tests [Verified]
- **Target Requirement**: `REQ-SPT-004`, `REQ-SPT-005`
- **Target Verification Component**: `SportingJourneyLocalizationContractTest.kt`, `SportingJourneyComponentsTest.kt`, `SportingJourneyShareHelperTest.kt`
- **Verification Type**: Automated Unit & Contract Verification
- **Test Description**:
  1. *Terminology & Zero-Emoji XML Parity Contract Test (`TST-SPT-006.1`, `SportingJourneyLocalizationContractTest.kt`)*:
  • Verify that `strings_sporting_journey.xml` across all 9 supported locales contains zero decorative inline Unicode emojis matching the ATT-5400 regex pattern `[\x{1F300}-\x{1F64F}\x{1F680}-\x{1F6FF}\x{2600}-\x{26FF}\x{2700}-\x{27BF}]`.
  • Verify zero occurrences of Strava trademark "Local Legend", zero developer jargon "SQLite", and zero prototype phrases "Season Wrapped".
  2. *Achievement Grid Layout & Header Weighting Test (`TST-SPT-006.2`, `SportingJourneyComponentsTest.kt`)*:
  • Verify that chunked grid layout with 4 items per row pads incomplete rows with equal-weight spacers, preventing stretched cards.
  • Verify that header rows in `HeroTotalsCard`, `HiddenGemsSection`, `GemCard`, `MilestonesSection`, and `AchievementRoomSection` apply `Modifier.weight(1f)` to title columns, preventing mid-word text wrapping and protecting trailing badges from compression.
  3. *Locale.getDefault() & Imperial Unit Formatting Test (`TST-SPT-006.3`, `SportingJourneyShareHelperTest.kt`)*:
  • Verify that number and date formatting across UI cards and share passport respects `Locale.getDefault()`.
  • Verify that when `TrainingApplication.getUnit() == MyUnits.IMPERIAL`, total distance is formatted in miles (`mi`) and total elevation in feet (`ft`) using `BANALService` conversion factors.
  • Verify that `SportingJourneyShareHelper.buildShareText` formats localized string templates.
  (ATT-7323: [Verbesserung] SportingJourneySheet & SportingJourneyComponents: Material 3 redesign, row weight wrapping fixes, grid layout, and locale/unit formatting)

---

### TST-WKO-035: Workout Summaries Database Migration & Column Constants Verification [Verified]
- **Target Requirement**: `REQ-WKO-035`
- **Target Verification Component**: `WorkoutRpeFeelTest.kt`, `WorkoutSummariesDatabaseTest.kt`
- **Verification Type**: Unit
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Test Description**:
  1. *Database Schema & Column Constants Test (`TST-WKO-035`)*:
  • Verify `DB_VERSION = 31`, `WorkoutSummaries.RPE = "rpe"`, and `WorkoutSummaries.FEEL = "feel"` constants and table creation/upgrade parity.

---

### TST-WKO-036: RPE & Feel Anchors, Emojis and Settings Toggle Verification [Verified]
- **Target Requirement**: `REQ-WKO-036`
- **Target Verification Component**: `WorkoutRpeFeelTest.kt`
- **Verification Type**: Unit
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Test Description**:
  1. *Borg CR10 and Feel Sentiment Anchors Test (`TST-WKO-036`)*:
  • Verify Borg CR10 string resource anchors (1..10), feel emojis (1..5), sentiment vector icons, and UI test tags constants.

---

### TST-WKO-037: Reorderable Section Defaults & Overview Tab Configuration Verification [Verified]
- **Target Requirement**: `REQ-WKO-037`
- **Target Verification Component**: `WorkoutRpeFeelTest.kt`, `TrackOnMapScreenSectionSlottingContractTest.kt`
- **Verification Type**: Unit
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Test Description**:
  1. *Section Ordering and Tab Slotting Test (`TST-WKO-037`)*:
  • Verify `WorkoutSectionType.DEFAULT_ORDER[0] == RPE_AND_FEEL` and `Overview` tab contains `RPE_AND_FEEL` at slot 0.

---

### TST-WKO-038: Foster Session-RPE Score Calculation Verification [Verified]
- **Target Requirement**: `REQ-WKO-038`
- **Target Verification Component**: `WorkoutRpeFeelTest.kt`
- **Verification Type**: Unit
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Test Description**:
  1. *Session-RPE Math Test (`TST-WKO-038`)*:
  • Verify `PowerPhysiologyCalculator.calculateSessionRpe()` returns exact scores for 60 min at RPE 7 (420.0), 45 min at RPE 4 (180.0), and 0.0 for out-of-range inputs.

---

### TST-WKO-039: PMC Low-CTL ACWR Gate & Tier-4 TSS-Normalized Session-RPE Fallback Verification (`ATT-9360`) [Verified]
- **Requirement(s)**: `REQ-WKO-039` (`REQ-WKO-039.1`–`REQ-WKO-039.3`)
- **Component**: `PowerPhysiologyCalculator.kt`, `PowerPhysiologyCalculatorTest.kt`, `WorkoutRpeFeelTest.kt`
- **Jira**: `ATT-6879`, `ATT-9360` | **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **Test Description**:
  1. *Low-CTL ACWR Gate Test (`TST-WKO-039.1`, `PowerPhysiologyCalculatorTest#computePerformanceManagementChart_gatesAcwrStrictlyAtMinCtl15_tstWko039_1`, `PowerPhysiologyCalculatorTest#computePerformanceManagementChart_computesCtlAtlTsbAcwrRampRateAndZones`)*: Verify `computePerformanceManagementChart()` returns `acwr == null` and `acwrZone == null` for every day with `ctl < 15.0` (cold-start Day 0 `ctl = 2.38` and exact boundary `ctl = 14.99`), and returns exact `atl / ctl` with classified `AcwrZone` as soon as `ctl >= 15.0` (`MIN_CTL_FOR_ACWR`).
  2. *Tier-4 RPE-to-TSS Scale & Foster sRPE Preservation Test (`TST-WKO-039.2`, `PowerPhysiologyCalculatorTest#calculateRpeTss_mapsAllTenBorgCr10LevelsScalesLinearlyAndGuardsInvalidInputs_tstWko039_2`, `PowerPhysiologyCalculatorTest#calculateHrTss_andResolveWorkoutTrainingLoad_followThreeTierHierarchy`, `WorkoutRpeFeelTest#testTier4TrainingLoadFallback`, `WorkoutRpeFeelTest#testSessionRpeCalculation`)*: Verify `calculateRpeTss()` maps all 10 Borg CR10 levels (`1..10`) to `20, 30, 40, 50, 60, 70, 80, 100, 120, 140` TSS/h scaled proportionally by duration (`1800 s` = `0.5x`), returns `0.0` for invalid RPE (`0, -2, 11`) or non-positive/non-finite durations (`0.0, -60.0, NaN, Infinity`), `resolveWorkoutTrainingLoad()` resolves Tier-4 to TSS scale (`1h @ RPE 8 == 100.0`, `RPE 7 == 80.0`, `RPE 6 == 70.0`) while preserving Tier 1–3 precedence, and `calculateSessionRpe(7, 3600.0)` still returns `420.0` (`REQ-WKO-038`).
- **Verified Evidence (`51ce6830`, `ATT-9534` PASS, `67/67` Tier-1 PASS)**: `PowerPhysiologyCalculatorTest#computePerformanceManagementChart_gatesAcwrStrictlyAtMinCtl15_tstWko039_1`, `PowerPhysiologyCalculatorTest#computePerformanceManagementChart_computesCtlAtlTsbAcwrRampRateAndZones`, `PowerPhysiologyCalculatorTest#calculateRpeTss_mapsAllTenBorgCr10LevelsScalesLinearlyAndGuardsInvalidInputs_tstWko039_2`, `PowerPhysiologyCalculatorTest#calculateHrTss_andResolveWorkoutTrainingLoad_followThreeTierHierarchy`, `WorkoutRpeFeelTest#testTier4TrainingLoadFallback`, `WorkoutRpeFeelTest#testSessionRpeCalculation`.

---

### TST-HRV-038: Morning Sleep & Wellness Settings Progressive Disclosure Verification [Implemented]
- **Target Requirement**: `REQ-HRV-038`
- **Target Verification Component**: `SettingsDataStoreTest.kt`, `PhysiologyModelsSectionTest.kt`
- **Verification Type**: Unit
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Test Description**:
  1. *Settings Progressive Disclosure Test (`TST-HRV-038`)*:
  • Verify default `OFF`, subordinate toggle visibility gating, and preference persistence round-trip.

---

### TST-HRV-039: Morning Readiness Database Migration V1->V2 & Symmetric Mapping Verification [Implemented]
- **Target Requirement**: `REQ-HRV-039`
- **Target Verification Component**: `HrvReadinessDatabaseManagerTest.kt`, `HrvReadinessEntryTest.kt`
- **Verification Type**: Unit
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Test Description**:
  1. *Schema V2 Evolution & Symmetric Mapping Test (`TST-HRV-039`)*:
  • Verify `onUpgrade` from V1 preserves legacy records and adds 5 nullable columns; verify `onCreate` equals `onUpgrade` schema and DTO mapping symmetry.

---

### TST-HRV-040: Standalone No-Strap Wellness Logging Verification [Implemented]
- **Target Requirement**: `REQ-HRV-040`
- **Target Verification Component**: `MorningHrvCheckViewModelTest.kt`
- **Verification Type**: Unit
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Test Description**:
  1. *Standalone Wellness Logging Test (`TST-HRV-040`)*:
  • Verify saving with no HR connection inserts entry with null `rmssdMs`; SWC baseline query excludes standalone entries.

---

### TST-HRV-041: Combined In-Measurement Logging Verification [Implemented]
- **Target Requirement**: `REQ-HRV-041`
- **Target Verification Component**: `MorningHrvCheckViewModelTest.kt`
- **Verification Type**: Unit
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Test Description**:
  1. *Combined In-Measurement Logging Test (`TST-HRV-041`)*:
  • Verify entries saved after countdown contain both calculated HRV metrics and input wellness fields.

---

### TST-HRV-042: Health Connect Sleep Prefill & Localization Parity Verification [Implemented]
- **Target Requirement**: `REQ-HRV-042`
- **Target Verification Component**: `HealthConnectManagerTest.kt`, `TranslationParityTest.kt`
- **Verification Type**: Unit
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Test Description**:
  1. *Health Connect Prefill & Localization Test (`TST-HRV-042`)*:
  • Verify graceful fallback when Health Connect permission is absent and 100% string parity across all 9 locales.

---

### TST-PER-050: **Multi-Sensor Telemetry Chart Overlays & Session Mean Comparison Verification Suite** (`ATT-8096`) [Verified]
- **Requirement(s)**: `REQ-PER-042` (`REQ-PER-042.1`..`REQ-PER-042.4`)
- **Component**: `MapModels.kt`, `WorkoutRepository.kt`, `TelemetryMetricGraph.kt`, `TelemetryMetricUtils.kt`, `TelemetryGraphColorPreferences.kt`, `MapDetailLayout.kt`, `WorkoutRepositoryTelemetryTest.kt`, `TelemetryMetricUtilsTest.kt`, `TelemetryGraphColorPreferencesTest.kt`, `TelemetryMetricGraphOverlayTest.kt`, `TelemetryMetricGraphMultiSourceUiTest.kt`, `MapDetailLayoutScrubSynchronizationTest.kt`, `MultiSensorChartsScreenshotTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-8096`
- **Status**: Verified
- **Test Description**:
  1. *Strict Source-Column Discovery & Dotted Device Name Lookup (`TST-PER-050.1`, `WorkoutRepositoryTelemetryTest.kt`)*:
  • Verify `"^${type.name} \\((.+)\\)$"` discovery against `cursor.columnNames` ignores canonical `ProxySensor` (`"HR"`) so `1` named column (`"HR (H10)"`) produces `<= 1` source, while `>= 2` named columns (including dotted `"POWER (KICKR v5.0)"`) resolve all named streams and session means across both `loadWorkoutDetailTelemetry` (GPS) and `getWorkoutTelemetryPoints` (trackless indoor).
  2. *`TelemetryMetricType.CADENCE` & Color Preference Key Invariance (`TST-PER-050.2`, `TelemetryMetricUtilsTest.kt`, `TelemetryGraphColorPreferencesTest.kt`)*:
  • Verify `PathPoint.cadence` extraction, `TelemetryMetricType.CADENCE` metric resolution/formatting (`rpm`), and `TelemetryGraphColorPreferences` persistence mapping to existing `telemetry_graph_color_SENSOR_CADENCE` (`THEME_DEFAULT` default).
  3. *Progressive-Disclosure `Layers` UI, Mean Legend & Zero-Clutter Gating (`TST-PER-050.3`, `TelemetryMetricGraphOverlayTest.kt`)*:
  • Verify `<= 1` named source hides the `Layers` toggle and comparison legend, whereas `>= 2` named sources show the toggle, secondary curve(s), and compact `Ø` mean comparison readout (`Ø %1$d bpm`, `Ø %1$d W`, `Ø %1$d rpm`).
  4. *Synchronized X-Axis Scrubbing Across Distance & Time (`TST-PER-050.4`, `MapDetailLayoutScrubSynchronizationTest.kt`)*:
  • Verify horizontal scrubbing in `DISTANCE` and `TIME` domains (both GPS and trackless workouts) updates overlaid source readout values synchronously while preserving `Rule 64` plot alignment.
  5. *9-Locale Parity & Rule 8.5 Compose PNG (`TST-PER-050.5`, `TranslationParityTest.kt`, `MultiSensorChartsScreenshotTest.kt`)*:
  • Verify 100% 9-locale parity (`EN/DE/ES/FR/IT/JA/NL/PL/PT`) with zero emojis (`ATT-5400`) and inspect rendered `docs/engineering/walkthroughs/img/ATT-8096_multisensor_charts.png` (Light + Dark) via `read_file` (`Rule 8.5`).


---

### TST-PER-051: **Eager Init-Order Safety Verification for PeriodsViewModel & Sibling** (`ATT-8569`) [Verified]
- **Requirement(s)**: `REQ-PER-043` (`REQ-PER-043.1`, `REQ-PER-043.2`)
- **Component**: `PeriodsViewModel.kt`, `WorkoutClustersViewModel.kt`, `PeriodsViewModelTest.kt`, `WorkoutClustersViewModelTest.kt`
- **Jira**: `ATT-8569`
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit, On-Device Manual
- **Localization Parity**: `N/A - no UI strings`
- **Test Description**:
  1. *`PeriodsViewModel` Eager `UnconfinedTestDispatcher` Init & Reconciliation (`TST-PER-051.1`, `PeriodsViewModelTest.kt`)*:
     • Override `Dispatchers.setMain(UnconfinedTestDispatcher())` (simulating `Dispatchers.Main.immediate`) and instantiate `PeriodsViewModel` with both an empty `MutableStateFlow<List<WorkoutData>>(emptyList())` and a populated `MutableStateFlow` holding `WorkoutData` entries; assert construction succeeds without throwing `NullPointerException`, then select a workout for peek (`selectWorkoutForPeek`) and emit a cluster-renamed `WorkoutData` on `allWorkouts` to verify `peekedWorkoutDataWithTrack` reconciles `clusterName` while preserving `workoutName` (`REQ-PER-040.2`).
  2. *`WorkoutClustersViewModel` Defensive Eager Init (`TST-PER-051.2`, `WorkoutClustersViewModelTest.kt`)*:
     • Verify `WorkoutClustersViewModel` initializes `_peekedWorkoutDataWithTrack` prior to `init` (`observeClusterMutations()`), succeeding without `NullPointerException` under `UnconfinedTestDispatcher()` even if `clusterMutationEvents` emits synchronously at subscription time.
  3. *Emulator UI Launch Smoke Check (`TST-PER-051.3`, `emulator-5554`)*:
     • Install debug APK onto `emulator-5554`, launch `com.atrainingtracker.debug`, navigate to the **Periods** hub, verify the screen renders cleanly without crashing (`logcat` free of `FATAL EXCEPTION` / `PeriodsViewModel`), and capture UI/screenshot verification.
  4. *Tier 1 Targeted Unit Test Regression (`TST-PER-051.4`)*:
     • Run `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsViewModelTest" --tests "com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClustersViewModelTest"` asserting 100% pass rate (`N/A - no UI strings`).

---

### TST-HRV-043: **Pure Wellness Mode & HRV Fallback Verification Suite** (`ATT-8598`) [Verified]
- **Requirement(s)**: `REQ-HRV-043` (`REQ-HRV-043.1`..`REQ-HRV-043.4`)
- **Component**: `MorningHrvCheckViewModel.kt`, `MorningHrvCheckScreen.kt`, `MorningHrvCheckViewModelTest.kt`, `MorningHrvCheckScreenTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-8598`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Test Description**:
  1. *ViewModel `showMorningCheckOnStartScreen` Reactive Mapping (`TST-HRV-043.1`, `MorningHrvCheckViewModelTest.kt`)*:
     • Update `TuningPreferencesDataStore` (`showMorningCheckOnStartScreen = false`, `enableMorningSleepAndWellness = true`) and verify `MorningHrvCheckViewModel.uiState` reflects `showMorningCheckOnStartScreen == false` and `enableMorningSleepAndWellness == true`, while `saveStandaloneWellness()` persists with `rmssdMs == null` and sets `isWellnessSaved == true`.
  2. *Compose UI Pure Wellness Mode & Empty-State Verification (`TST-HRV-043.2`, `MorningHrvCheckScreenTest.kt`)*:
     • Render `MorningHrvCheckContent` with `showMorningCheckOnStartScreen = false`, `enableMorningSleepAndWellness = true`, `phase = READY`: assert wellness TopAppBar title/subtitle, `MorningWellnessInputCard` visibility, promoted primary `Save Morning Wellness` button (plus green `Saved` state when `isWellnessSaved = true`), and absence of `WakingProtocolChecklistCard`, `PostureAndDurationCard`, `MeasurementPacingCard`, HRV info button, and `Start Measurement` button.
     • Render with all 4 wellness sub-toggles `false`: assert `morning_hrv_wellness_empty_hint` is displayed.
  3. *Compose UI Enabled HRV & Fallback Verification (`TST-HRV-043.3`, `MorningHrvCheckScreenTest.kt`)*:
     • Verify full HRV layout (TopAppBar HRV title/info icon, protocol/posture/duration/pacing cards, `Start Measurement` button) renders when (a) `showMorningCheckOnStartScreen = true`, (b) `showMorningCheckOnStartScreen = false && enableMorningSleepAndWellness = false` (Athlete Profile fallback), and (c) `phase = MEASURING` (active measurement protection).
  4. *9-Locale Parity & Rule 8.5 Light/Dark Compose PNG (`TST-HRV-043.4`, `TranslationParityTest.kt`, `MorningHrvCheckScreenTest.kt`)*:
     • Verify `morning_hrv_wellness_subtitle` and `morning_hrv_wellness_empty_hint` across all 9 locales (`EN/DE/ES/FR/IT/JA/NL/PL/PT`) with zero emojis, and inspect captured Light & Dark PNG (`docs/engineering/walkthroughs/img/ATT-8598_morning_wellness_mode.png`) via `read_file`.
  5. *Tier 1 Targeted Regression Suite (`TST-HRV-043.5`)*:
     • Run `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.hrv.MorningHrvCheckViewModelTest" --tests "com.atrainingtracker.trainingtracker.ui.hrv.MorningHrvCheckScreenTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`.

### TST-HRV-044: **DFA alpha 1 Zone Graph, Margin Coordination & Scatter Parity Verification Suite** (`ATT-8710`) [Verified]
- **Requirement(s)**: `REQ-HRV-044` (`REQ-HRV-044.1`..`REQ-HRV-044.5`)
- **Component**: `TelemetryZoneMath.kt`, `TelemetryMetricGraph.kt`, `TelemetryMetricUtils.kt`, `HrvAlpha1ScatterChart.kt`, `TelemetryZoneMathTest.kt`, `TelemetryMetricGraphHrvTest.kt`, `HrvAlpha1ScatterChartTest.kt`
- **Jira**: `ATT-8710`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Localization Parity**: `N/A - no UI strings`
- **Test Description**:
  1. *Inverted 3-Zone Math & Threshold Dash Filtering (`TST-HRV-044.1`, `TelemetryZoneMathTest.kt`)*:
     • Verify `calculateHrvDfaA1ZoneBands` clamps `[dataMin, dataMax]` (`[0.2, 1.4]`) into Anaerob (`Zone4`, top `[dataMin, T2]` per inverted effort Y-axis refined in `TST-HRV-046` / `ATT-8953`), Schwelle (`Zone3`, middle `[T2, T1]`), and Aerob (`Zone2`, bottom `[T1, dataMax]`) across default (`0.75`/`0.50`) and custom (`T1 = 1.50f` `MAX_DFA_A1_THRESHOLD`, `T2 = 0.15f`) inputs.
     • Verify `determineHrvDfaA1Zone` preserves `α1 > T1 -> Zone2`, `T2 <= α1 <= T1 -> Zone3` (`α1 == T1 -> Zone3`), `α1 < T2 -> Zone4`; verify `calculateHrvDfaA1ThresholdDashes` excludes out-of-range thresholds (`>= dataMax` or `<= dataMin`).
  2. *Scatter Dot Dynamic Threshold Parity (`TST-HRV-044.2`, `HrvAlpha1ScatterChartTest.kt`)*:
     • Render/evaluate `HrvAlpha1ScatterChart` with custom `t1`/`t2` (e.g., `t1 = 0.85f`, `t2 = 0.60f`) and assert scatter point colors match `HrvZoneIndicator.calculateZoneColor(p.alpha1, t1, t2)` rather than hardcoded defaults.
  3. *Compose UI Render, Margin Disjointness & Rule 8.5 Light/Dark PNGs (`TST-HRV-044.3`, `TelemetryMetricGraphHrvTest.kt`)*:
     • Using `createComposeRule()` (Robolectric, zero `src/main` file reads per `REQ-PRO-049.1`), render `TelemetryMetricGraph` (`HRV_DFA_A1`) with artifact gap (`> 5.0%`), scrubbing `currentDistance`, custom `T1`/`T2`, and `nonZonedHistogram` in Light & Dark themes; assert pixel colors reflect `Zone2`/`Zone3`/`Zone4` bands/curve/cursor, right-margin labels do not collide with `nonZonedHistogram`, `HEART_RATE` regression remains solid `accentColor`, and inspect captured Light/Dark PNGs via `read_file`.
  4. *Tier 1 Targeted Regression (`TST-HRV-044.4`)*:
     • Run `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryZoneMathTest" --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphHrvTest" --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphZoneTest" --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphColorTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvAlpha1ScatterChartTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingHrvZoneIndicatorTest"`.

---

### TST-PER-052: **Multi-Source Track Layers, Elevation/Speed/Slope Overlays & Per-Chart Visibility Verification Suite** (`ATT-8080`) [Verified]
- **Requirement(s)**: `REQ-PER-044` (`REQ-PER-044.1`..`REQ-PER-044.5`), `REQ-MAP-011`, `REQ-MAP-012`
- **Component**: `MapModels.kt`, `WorkoutMultiSourceSampleDiscovery.kt`, `WorkoutRepository.kt`, `MyPreferenceManager.kt`, `TelemetryGraphColorPreferences.kt`, `TelemetryMetricUtils.kt`, `TelemetryOverlayComponents.kt`, `TelemetryMetricGraph.kt`, `ElevationProfileOverlayComponents.kt`, `ElevationProfile.kt`, `TelemetryChartStack.kt`, `MapDetailLayout.kt`, `TrackOnMapScreen.kt`, `WorkoutRepositoryProjectionTest.kt`, `WorkoutRepositoryTelemetryTest.kt`, `TelemetryMetricUtilsTest.kt`, `TelemetryGraphColorPreferencesTest.kt`, `ElevationProfileOverlayTest.kt`, `TelemetryMetricGraphOverlayTest.kt`, `MapDetailLayoutScrubSynchronizationTest.kt`, `TelemetryChartStackVisibilityTest.kt`, `TranslationParityTest.kt`, `ArchitectureConformanceContractTest.kt`, `DetailedDesignParityContractTest.kt`
- **Jira**: `ATT-8080`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Test Description**:
  1. *`TrackType.ATT_FUSED`, Alias Resolution & `NETWORK` Removal (`TST-PER-052.1`, `WorkoutRepositoryProjectionTest.kt`, `WorkoutRepositoryTelemetryTest.kt`, `TelemetryChartStackVisibilityTest.kt`)*:
     • Verify `TrackType.entries` contains `BEST`, `GPS`, `FUSED`, `ATT_FUSED` (no `NETWORK`), `ATT_FUSED` extracts `LATITUDE_att_fused`/`LONGITUDE_att_fused`, `GPS`/`FUSED` resolve both `"LATITUDE (GPS)"` / `"LATITUDE (Google Fused)"`, underscore `_gps`/`_google_fused`, and localized device column names (`"Phone GPS (Satellite)"`, `"Smartphone GPS (Satellit)"`), legacy `"NETWORK"` entries in DataStore `enabled_track_types` are ignored safely, and `PeriodMapScreen`/`WorkoutClusterHeatmapScreen`/`WorkoutClustersScreen` wire `showTechnicalTracks = true` and `multiSensorOverlays`.
  2. *Generic Column Discovery & Negative/Zero Value Retention (`TST-PER-052.2`, `WorkoutRepositoryTelemetryTest.kt`)*:
     • Verify `discoverNamedSourceColumns` / `discoverMultiSourceOverlayColumns` resolve `<SENSOR> (<name>)` (including `"ALTITUDE (att_fused_altitude_slope)"` and `"ALTITUDE (Topografische Höhe (DEM))"`) and `_(gps|google_fused|att_fused)` (`SPEED_mps_att_fused`, `ALTITUDE_att_fused`) while excluding `_network` and preserving `ATT-8096` `HR`/`POWER`/`CADENCE` behavior (`>= 2` sources).
     • Verify `OverlaySeriesAccumulator` retains negative `SLOPE` (`-8.0%`), `0.0%` flat, and `<= 0.0 m` sea-level `ALTITUDE` samples across GPS and trackless workouts while maintaining `> 0` gating for `HR`/`CADENCE` and `>= 0` for `POWER`/`SPEED_mps`.
  3. *`ElevationProfile` Overlays, `SPEED`/`PACE` Unit Conversion & `SLOPE` Graph (`TST-PER-052.3`, `TelemetryMetricUtilsTest.kt`, `TelemetryGraphColorPreferencesTest.kt`, `ElevationProfileOverlayTest.kt`, `TelemetryMetricGraphOverlayTest.kt`, `MapDetailLayoutScrubSynchronizationTest.kt`, `TelemetryChartStackVisibilityTest.kt`)*:
     • Verify `TelemetryMetricType.SLOPE` (`%` formatting, `telemetry_graph_color_SENSOR_SLOPE` key), `SPEED`/`PACE` m/s -> user-unit (`km/h`/`mi/h`) and inverted-pace (`min/km`/`min/mi`) overlay conversion, `ElevationProfile` `Layers` toggle (`<= 1` hidden, `>= 2` shows curves + `Ø` mean legend), and synchronized `DISTANCE`/`TIME` scrubbing across `ElevationProfile`, `SPEED`/`PACE`, `SLOPE`, `HR`, `POWER`, and `CADENCE`.
  4. *Persisted Per-Chart Visibility Toggles, Canonical Order & `Rule 64` Alignment (`TST-PER-052.4`, `TelemetryChartStackVisibilityTest.kt`, refined by `ATT-9355`)*:
     • Verify DataStore `hidden_telemetry_charts` defaults to all charts enabled and decodes by enum `.name`, toggling individual charts updates `CHARTS` immediately and persists across sessions, filter trigger remains accessible when all charts are hidden, `Rule 64` horizontal plot alignment holds, and both rendered graphs and `"Visible Charts"` dropdown items follow `SPEED_PACE -> SLOPE -> CADENCE -> POWER -> HEART_RATE -> W_PRIME_BAL -> HRV_DFA_A1 -> HRV_RMSSD` (`testTelemetryChartStack_ordersCadenceBeforePowerAndHeartRateAfterPower_att9355`, `testCaptureCadenceHeartRateOrderLightAndDarkScreenshot_att9355`).
  5. *9-Locale Parity, LoC Ceilings & `Rule 8.5` PNG (`TST-PER-052.5`, `TranslationParityTest.kt`, `ArchitectureConformanceContractTest.kt`, `DetailedDesignParityContractTest.kt`, `TelemetryChartStackVisibilityTest.kt`)*:
     • Verify 100% 9-locale parity (`track_type_att_fused`, `graph_heading_slope`, visibility/overlay strings, zero `track_type_network`, zero emojis), pass `ArchitectureConformanceContractTest` (`WorkoutRepository.kt <= 5,117`, `TelemetryMetricGraph.kt <= 1,965`, `MapDetailLayout.kt <= 1,500`, `ElevationProfile.kt <= 1,500`) and `DetailedDesignParityContractTest`, and inspect `docs/engineering/walkthroughs/img/ATT-8080_multisource_charts_visibility.png` (Light & Dark) via `read_file`.

---

### TST-PER-053: **Activity & Phone Carriage Mode Timeline Coalescing, ViewModel Propagation & Behavioral Compose UI Suite** (`ATT-8916`) [Verified]
- **Requirement(s)**: `REQ-PER-045` (`REQ-PER-045.1`..`REQ-PER-045.5`)
- **Component**: `ActivityCarriageTimeline.kt`, `ActivityCarriageTimelineCalculator.kt`, `ActivityCarriageTimelineCard.kt`, `Color.kt`, `WorkoutRepository.kt`, `TrackOnMapAftermathViewModel.kt`, `TrackOnMapScreen.kt`, `WorkoutDetailTabbedContent.kt`, `ActivityCarriageTimelineCalculatorTest.kt`, `WorkoutRepositoryTelemetryTest.kt`, `TrackOnMapAftermathViewModelTest.kt`, `ActivityCarriageTimelineCardTest.kt`, `ThemeColorCentralizationAuditTest.kt`, `TranslationParityTest.kt`, `UiReflectionOnlyTestAuditContractTest.kt`, `SourceTextReadingAuditContractTest.kt`, `ArchitectureConformanceContractTest.kt`
- **Jira**: `ATT-8916`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Test Description**:
  1. *Pure-JVM Coalescing, Sliver Absorption & Edge Math (`TST-PER-053.1`, `ActivityCarriageTimelineCalculatorTest.kt`)*:
     • Verify bakery-tour sequence (`UNKNOWN(2s)` warmup -> `BIKE_FRONT_POCKET` -> `WALKING_TRANSITION` -> `STATIONARY` -> `WALKING_TRANSITION` -> `BIKE_FRONT_POCKET`) absorbs `<= 5s` `UNKNOWN` into adjacent active span, preserves all 5 chronological segments (`startTimeSec`, `endTimeSec`, `durationSec`, `distanceMeters`), and aggregates per-mode summaries (`durationPercentage` sums to `100%`).
     • Verify genuine `1–5s` active transitions (`0..7`) are NEVER debounced away, `> 5s` `UNKNOWN(-1)` gaps are retained as distinct `UNKNOWN` segments included in `totalDurationSec`, pause gaps (`dt > 5s`) clamp `dt <= 5s`, and zero-distance indoor/stationary workouts produce `totalDistanceMeters == 0.0`, `distancePercentage == 0f` (no `NaN`), and strictly positive duration weights.
  2. *Repository Pre-Decimation Extraction & Legacy/All-`UNKNOWN` Omission (`TST-PER-053.2`, `WorkoutRepositoryTelemetryTest.kt`)*:
     • Verify `WorkoutRepository.loadWorkoutDetailTelemetry` returns `activityCarriageTimeline = null` for legacy tables without `ACTIVITY_AND_CARRIAGE_MODE` AND for tables containing exclusively `NULL`/`UNKNOWN(-1)` rows.
     • Verify `> 800`-row GPS and trackless (`hasCarriageMode`-only or scalar+carriage) workouts preserve brief mid-workout `WALKING_TRANSITION` / `STATIONARY` segments without decimation loss.
  3. *ViewModel Reset, Dual GPS/Trackless Forwarding & All-5-Host Wiring (`TST-PER-053.3`, `TrackOnMapAftermathViewModelTest.kt`)*:
     • Verify `TrackOnMapAftermathViewModel.loadAftermathData` resets `activityCarriageTimeline = null` when switching workouts (`!isSameWorkout`), and propagates `telemetry.activityCarriageTimeline` in both the GPS `fullTracks.isNotEmpty()` path AND the trackless `else` fallback path.
  4. *Behavioral Compose UI (`createComposeRule()`), Graceful Omission & `Rule 8.5` Light/Dark PNG (`TST-PER-053.4`, `ActivityCarriageTimelineCardTest.kt`)*:
     • Using `createComposeRule()` with strictly `0` `src/main` file reads (`SourceTextReadingAuditContractTest`, `UiReflectionOnlyTestAuditContractTest`), verify `ActivityCarriageTimelineCard` (standalone and inside `WorkoutDetailTabbedContent` `DETAILS` section): renders proportional timeline bar + summary mode labels (`PhoneCarriageMode.labelResId`), handles zero-distance stationary/indoor timelines without `Modifier.weight(<= 0f)` crashes, toggles expandable chronological phase breakdown rows on tap, and renders nothing when `timeline == null` or empty. Capture and visually inspect Light & Dark PNG (`docs/engineering/walkthroughs/img/ATT-8916_carriage_timeline.png`) via `read_file`.
  5. *9-Locale Parity, Theme Color & Build Contracts (`TST-PER-053.5`, `TranslationParityTest.kt`, `ThemeColorCentralizationAuditTest.kt`, `WorkoutDetailTabsTest.kt`, `ArchitectureConformanceContractTest.kt`)*:
     • Verify 100% 9-locale parity in `values*/strings_workout_details.xml` (zero emojis), `0` `Color(0x...)` outside `Color.kt`, `"v3|"` & `14`-entry `WorkoutDetailSectionType` intact, `WorkoutSamplesDatabaseManager.java == 1,533` LoC, and `WorkoutRepository.kt <= 5,117` LoC.

---

### TST-HRV-045: **Full HRV Route Override (`forceFullHrv`) & Pure Wellness Regression Suite** (`ATT-8846`) [Verified]
- **Requirement(s)**: `REQ-HRV-045` (`REQ-HRV-045.1`..`REQ-HRV-045.4`)
- **Component**: `NavRoutes.kt`, `ATrainingTrackerApp.kt`, `MorningHrvCheckScreen.kt`, `SingleActivityNavigationTest.kt`, `MorningHrvCheckScreenTest.kt`, `ArchitectureConformanceContractTest.kt`, `SourceTextReadingAuditContractTest.kt`, `DeadCodeEliminationContractTest.kt`
- **Jira**: `ATT-8846`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Localization Parity**: `N/A - no UI strings`
- **Test Description**:
  1. *Parameterized Route & Drawer Query-Stripping Runtime Unit Tests (`TST-HRV-045.1`, `SingleActivityNavigationTest.kt`)*:
     • Strictly zero `src/main` file reads (`SourceTextReadingAuditContractTest`). Verify `NavRoutes.MORNING_HRV == "morning_hrv"`, `NavRoutes.ARG_FORCE_FULL_HRV == "forceFullHrv"`, `NavRoutes.MORNING_HRV_PATTERN == "morning_hrv?forceFullHrv={forceFullHrv}"`, `NavRoutes.morningHrv() == "morning_hrv"`, `NavRoutes.morningHrv(false) == "morning_hrv"`, `NavRoutes.morningHrv(true) == "morning_hrv?forceFullHrv=true"`, `NavRoutes.fromDrawerItemId(R.id.drawer_morning_hrv) == NavRoutes.MORNING_HRV`, and `NavRoutes.toDrawerItemId("morning_hrv?forceFullHrv=true") == R.id.drawer_morning_hrv`.
  2. *Compose UI `forceFullHrv = true` Override Verification (`TST-HRV-045.2`, `MorningHrvCheckScreenTest.kt`)*:
     • Using `createComposeRule()`, render `MorningHrvCheckContent` with `forceFullHrv = true`, `showMorningCheckOnStartScreen = false`, `enableMorningSleepAndWellness = true`, `phase = MorningHrvPhase.READY`: assert TopAppBar displays `morning_hrv_title`, `morning_hrv_subtitle`, and clickable `morning_hrv_info_button` opening `HrvInfoBottomSheet`; assert `morning_hrv_protocol_card`, `morning_hrv_posture_duration_card`, `morning_hrv_wellness_card`, `morning_hrv_pacing_card`, `morning_hrv_start_button`, and `morning_hrv_save_wellness_secondary_button` are displayed while `morning_hrv_save_wellness_primary_button` does not exist.
     • Render `MorningHrvCheckContent` with `forceFullHrv = true`, `showMorningCheckOnStartScreen = false`, `enableMorningSleepAndWellness = false`, `phase = MorningHrvPhase.READY`: assert full HRV measurement UI is displayed while `morning_hrv_wellness_card` and wellness save buttons do not exist.
  3. *Compose UI Pure Wellness Mode Regression (`TST-HRV-045.3`, `MorningHrvCheckScreenTest.kt`)*:
     • Render `MorningHrvCheckContent` with `forceFullHrv = false` (default), `showMorningCheckOnStartScreen = false`, `enableMorningSleepAndWellness = true`, `phase = MorningHrvPhase.READY` (Start Screen / Drawer entry): assert pure wellness mode (`REQ-HRV-043`) remains strictly preserved (HRV cards, info button, and `morning_hrv_start_button` absent; `morning_hrv_save_wellness_primary_button` displayed).
  4. *Rule 8.5 Light/Dark Compose PNG & Live `emulator-5554` E2E Verification (`TST-HRV-045.4`, `MorningHrvCheckScreenTest.kt`, `emulator-5554`)*:
     • Capture side-by-side Light & Dark PNG (`docs/engineering/walkthroughs/img/ATT-8846_morning_hrv_force_full.png`) rendering `MorningHrvCheckContent` with `forceFullHrv = true`, `showMorningCheckOnStartScreen = false`, `enableMorningSleepAndWellness = true` and inspect via `read_file` against the 4-point Visual Self-Inspection Checklist.
     • Deploy debug APK onto `emulator-5554` and verify with `showMorningCheckOnStartScreen = false` & `enableMorningSleepAndWellness = true` that tapping Start Screen *"Morning Check"* opens pure wellness mode while tapping *Athlete Profile -> HRV* tab *"Measure Morning HRV"* opens full HRV measurement UI with clean `logcat`.
  5. *Tier 1 Targeted Regression & Architecture Contracts (`TST-HRV-045.5`)*:
     • Run `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.ui.hrv.MorningHrvCheckScreenTest" --tests "com.atrainingtracker.trainingtracker.ui.navigation.SingleActivityNavigationTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest"` verifying `MorningHrvCheckScreen.kt <= 1,500` LoC, `AthleteProfileScreen.kt == 1,975` LoC (`0` edits), `private fun` `@Preview`s, and `0` `src/main` reads in tests.

---

### TST-HRV-046: **Inverted DFA alpha 1 Y-Axis Mapping, Gradient Stops & Spatial Pixel Verification Suite** (`ATT-8953`) [Verified]
- **Requirement(s)**: `REQ-HRV-046` (`REQ-HRV-046.1`..`REQ-HRV-046.4`)
- **Component**: `TelemetryZoneMath.kt`, `TelemetryMetricGraph.kt`, `TelemetryZoneMathTest.kt`, `TelemetryMetricGraphHrvUiTest.kt`, `TelemetryMetricGraphHrvTest.kt`, `HrvAlpha1ScatterChartUiTest.kt`, `ArchitectureConformanceContractTest.kt`, `DeadCodeEliminationContractTest.kt`, `SourceTextReadingAuditContractTest.kt`
- **Jira**: `ATT-8953`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Localization Parity**: `N/A - no UI strings`
- **Test Description**:
  1. *Shared Y-Inversion Helper & Inverted Gradient Boundary Unit Tests (`TST-HRV-046.1`, `TelemetryZoneMathTest.kt`)*:
     • Verify `isInvertedTelemetryYAxis` returns `true` for `PACE` and `HRV_DFA_A1` and `false` for `HEART_RATE`, `POWER`, `SPEED`, `SLOPE`, `HRV_RMSSD`.
     • Verify `mapTelemetryValueToY` maps `0.20 -> top`, `0.50 -> 25%`, `0.75 -> 45.83%`, `1.40 -> bottom` when inverted.
     • Verify `buildHrvDfaA1VerticalGradientStops` produces monotonically non-decreasing stops in `[0f, 1f]` with `0f = Zone4` (Anaerob top) -> `1f = Zone2` (Aerob bottom) for default (`0.75/0.50`), degenerate (`T1 == T2 = 0.70`), exact boundary (`T2 = 0.20`, `T1 = 1.40`), and out-of-range (`T1 = 1.50f`/`T2 = 0.50f`, `T1 = 1.50f`/`T2 = 0.15f`) thresholds.
  2. *Spatial Top-vs-Bottom Compose UI Pixel Verification — Portrait & Landscape (`TST-HRV-046.2`, `TelemetryMetricGraphHrvUiTest.kt`)*:
     • Using `createComposeRule()` (`0` `src/main` reads), render `TelemetryMetricGraph(HRV_DFA_A1)` in portrait (`isLandscape = false`) and landscape (`isLandscape = true` with `AlignedMarginalLinearDistribution`): assert top band of plot & marginal histogram renders `Zone4` red (and NOT `Zone2` green) while bottom band renders `Zone2` green (and NOT `Zone4` red).
  3. *Rule 8.5 Light/Dark PNG Capture & Historical Asset Integrity (`TST-HRV-046.3`, `TelemetryMetricGraphHrvUiTest.kt`)*:
     • Preserve `docs/engineering/walkthroughs/img/ATT-8710_dfa_a1_zone_graph.png` untouched; capture deterministic side-by-side Light & Dark PNG to `docs/engineering/walkthroughs/img/ATT-8953_dfa_a1_inverted_yaxis.png` and inspect via `read_file` against the 4-point Rule 8.5 checklist.
  4. *Out-of-Range Thresholds, Heart Rate & Scatter Orientation Invariance (`TST-HRV-046.4`, `TelemetryMetricGraphHrvUiTest.kt`, `HrvAlpha1ScatterChartUiTest.kt`)*:
     • Assert `T1 = 1.50f` emits `Zone4` top / `Zone3` bottom without `Zone2`, `HEART_RATE` preserves solid `CRIMSON` stroke, and `HrvAlpha1ScatterChart` keeps standard Cartesian orientation (`REQ-PER-035.4`).
  5. *Tier 1 Targeted Regression & Architecture Contracts (`TST-HRV-046.5`)*:
     • Run `./gradlew :app:testDebugUnitTest --rerun --tests "*TelemetryZoneMathTest" --tests "*TelemetryMetricGraphHrvUiTest" --tests "*TelemetryMetricGraphHrvTest" --tests "*TelemetryMetricGraphZoneTest" --tests "*HrvAlpha1ScatterChartUiTest" --tests "*ArchitectureConformanceContractTest" --tests "*DeadCodeEliminationContractTest" --tests "*SourceTextReadingAuditContractTest"` verifying `TelemetryMetricGraph.kt <= 1,965` LoC, `private fun` `@Preview`s, and `0` `src/main` reads.

---

### TST-HRV-047: **Dynamic Outlier-Resilient rMSSD Y-Axis Bounds, Labels & Spatial Spread Verification Suite** (`ATT-9171`) [Verified]
- **Requirement(s)**: `REQ-HRV-047` (`REQ-HRV-047.1`..`REQ-HRV-047.4`)
- **Component**: `TelemetryZoneMath.kt`, `TelemetryMetricGraph.kt`, `TelemetryZoneMathTest.kt`, `TelemetryMetricGraphHrvUiTest.kt`, `TelemetryMetricGraphHrvTest.kt`, `TelemetryMetricGraphZoneTest.kt`, `ArchitectureConformanceContractTest.kt`, `DeadCodeEliminationContractTest.kt`, `SourceTextReadingAuditContractTest.kt`
- **Jira**: `ATT-9171`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Localization Parity**: `N/A - no UI strings`
- **Test Description**:
  1. *Dynamic Bounds, Outlier Guard & Axis Label Unit Tests (`TST-HRV-047.1`, `TelemetryZoneMathTest.testCalculateHrvRmssdYAxisBounds_dynamicOutlierResilientAndEdgeCases_TST_HRV_047`, `TelemetryZoneMathTest.testFormatHrvRmssdAxisLabel_cleanIntegerFractionalAndDefensive_TST_HRV_047`)*:
     • Verify `calculateHrvRmssdYAxisBounds` across exercise `1.89-5.64 ms -> (0.0, 7.0)`, moderate `8-45 ms -> (0.0, 60.0)`, transient spike `45 ms` over `2-6 ms` steady-state (`N >= 5`) -> `(0.0, 7.0)`, genuine resting `40-120 ms -> (0.0, 140.0)` (`>= max * 1.15`) and `120-180 ms -> (0.0, 220.0)`, empty/zero/single-value `[3.2] -> (0.0, 5.0)` & `[120.0] -> (0.0, 140.0)`, `NaN`/`Infinity`/negative filtering, and `Double.MAX_VALUE` overflow safety.
     • Verify `formatHrvRmssdAxisLabel` (`0.0 -> "0"`, `-0.0 -> "0"`, `7.0 -> "7"`, `6.5 -> "6.5"`/`"6,5"`, `NaN`/`Infinity -> "0"`).
  2. *Portrait & Landscape Spatial Spread + Artifact/Spike Resilience Compose UI Verification (`TST-HRV-047.2`, `TelemetryMetricGraphHrvUiTest.testRmssdGraph_dynamicYAxisLightDarkPngAndSpatialSpread_TST_HRV_047`, `TelemetryMetricGraphHrvUiTest.testRmssdGraph_landscapeHistogramAndRestingScaling_TST_HRV_047`)*:
     • Using `createComposeRule()` (`0` `src/main` reads), render `TelemetryMetricGraph(HRV_RMSSD)` for exercise `1.89-5.64 ms` and spike-contaminated (`2-6 ms` + `45 ms` warmup/artifact spike) streams in portrait (`isLandscape = false`) and landscape (`isLandscape = true` with `AlignedMarginalLinearDistribution`): assert TEAL curve and marginal histogram pixels span upper-middle/upper vertical chart regions (not flattened along bottom 10%) and populate multiple histogram bins, while resting `40-120 ms` renders cleanly with zero clipping.
  3. *Rule 8.5 Light/Dark PNG Capture & Legacy Asset Integrity (`TST-HRV-047.3`, `TelemetryMetricGraphHrvUiTest.testRmssdGraph_dynamicYAxisLightDarkPngAndSpatialSpread_TST_HRV_047`)*:
     • Preserve `docs/engineering/walkthroughs/img/ATT-8710_dfa_a1_zone_graph.png` and `ATT-8953_dfa_a1_inverted_yaxis.png` untouched; capture deterministic side-by-side Light & Dark PNG (`1.89-5.64 ms` exercise rMSSD) strictly to `docs/engineering/walkthroughs/img/ATT-9171_rmssd_dynamic_yaxis.png` and inspect via `read_file` against the 4-point Rule 8.5 checklist.
  4. *Tier 1 Targeted Regression & Architecture Contracts (`TST-HRV-047.4`)*:
     • Run `./gradlew :app:testDebugUnitTest --rerun --tests "*TelemetryZoneMathTest" --tests "*TelemetryMetricGraphHrvUiTest" --tests "*TelemetryMetricGraphHrvTest" --tests "*TelemetryMetricGraphZoneTest" --tests "*ArchitectureConformanceContractTest" --tests "*DeadCodeEliminationContractTest" --tests "*SourceTextReadingAuditContractTest"` verifying `TelemetryMetricGraph.kt <= 1,965` LoC, `private fun` `@Preview`s, `0` `src/main` reads, and zero regressions on `HRV_DFA_A1` and `HEART_RATE`.

---

### TST-HRV-048: **WorkoutRr.db Beat-Level Artifact Flag Persistence & Live vs Post-Workout HRV Reconciliation Suite** (`ATT-9160`) [Verified]
- **Requirement(s)**: `REQ-HRV-048` (`REQ-HRV-048.1`..`REQ-HRV-048.4`)
- **Component**: `RrArtifactFilter.kt`, `WorkoutRrDatabaseManager.kt`, `WorkoutRrRepository.kt`, `HrvPostWorkoutRecomputer.kt`, `HRVMetricsDevice.kt`, `RrArtifactFilterTest.kt`, `HrvGoldenOracleTest.kt`, `WorkoutRrDatabaseManagerTest.kt`, `WorkoutRrRepositoryTest.kt`, `HrvPostWorkoutRecomputerTest.kt`, `HRVMetricsDeviceTest.kt`, `DfaCalculatorTest.kt`, `ArchitectureConformanceContractTest.kt`, `DeadCodeEliminationContractTest.kt`, `SourceTextReadingAuditContractTest.kt`
- **Jira**: `ATT-9160`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Localization Parity**: `N/A - no UI strings`
- **Test Description**:
  1. *1:1 Input Artifact/Gap Metadata & NeuroKit2 Golden Parity (`TST-HRV-048.1`, `RrArtifactFilterTest.testInputArtifactFlagsAndGapBeats_aligned1To1AcrossArtifactsAndHardwareGaps_TST_HRV_048`, `HrvGoldenOracleTest.kt`, `DfaCalculatorTest.kt`)*:
     • Verify `inputArtifactFlags`/`inputGapBeats` size equals input `beats.size` across clean, ectopic (`1`, `gap=0`), merged short pair (both indices `2`, `gap=0`), missed `779.3 ms` amidst `385 ms` (`4`, `gap=1`), unflagged `5196 ms` gap (`5`, `gap>=5`), hardware `ARTIFACT_GAP` (`max(event.gapBeats, gapCount - 1)` preserved), and stale-flag reset (`-> 0`); verify `HrvGoldenOracleTest` stays within NeuroKit2 `0.2.10` tolerances (`|ΔrMSSD| <= 0.5 ms`, `|Δα1| <= 0.02`/`0.03`).
  2. *Delta-Batched DB Flag Persistence, Idempotency & Configurable Gate Threshold (`TST-HRV-048.2`, `WorkoutRrDatabaseManagerTest.updateBeatQualityFlags_updatesTargetBeatsTransactionallyAndCountsModifiedRows_TST_HRV_048`, `WorkoutRrRepositoryTest.repository_updateBeatQualityFlags_persistsAndClearsFlags_TST_HRV_048`, `HrvPostWorkoutRecomputerTest.recomputeWorkout_persistsArtifactFlagsIdempotentlyAndResetsAfterCleanBackfill_TST_HRV_048`, `HrvPostWorkoutRecomputerTest.recomputeWorkout_andComputeSessionSdnn_honorClampedArtifactGateThreshold_TST_HRV_048`)*:
     • Verify `updateBeatQualityFlags` updates `(artifact_flag, gap_beats)` transactionally by `(workout_id, beat_index)` without mutating `rr_ms`/`beat_timestamp_ms`.
     • Verify `HrvPostWorkoutRecomputer.recomputeWorkout` persists flags on `275.39 ms`/`779.30 ms`/`5196 ms` outliers, performs `0` DB writes on idempotent re-run, resets stale flags to `(0, 0)` after clean backfill, and honors configurable `artifactGateThresholdPercent` clamped to `[3.0, 5.0]` for DFA $\alpha_1$ and `computeSessionSdnn`.
  3. *180s Causal Filter Context, Drain-to-Null, Signal Quality Scope & Phase-Aligned Reconciliation (`TST-HRV-048.3`, `HRVMetricsDeviceTest.test180sFilterContext_preservesTrailingQuartileAccuracyAnd120sSignalQualityScope_TST_HRV_048`, `HRVMetricsDeviceTest.testEmptyActiveDfaWindowAfterLookaheadTrim_emitsZeroArtifactRateAndNullDfa_TST_HRV_048`, `HrvPostWorkoutRecomputerTest.recomputeWorkout_phaseAlignedReconciliationWithLive180sContext_TST_HRV_048`)*:
     • Verify 180s context supplies full 91-beat trailing quartile windows across rolling 120s ticks without inflating leading-edge artifacts; verify `+150 s` drain clears all 3 sensors to `null`; verify empty `dfaWindowBeats` (all active beats in 5-beat lookahead tail) emits `artifactRate = 0.0`, `dfaAlpha1 = null`; verify `signalQualityResult.beatCount` reflects the 120s `dfaWindowBeats` slice; verify phase-aligned live `[T - 120s, T]` vs recomputed `T - 60s` reconcile tightly on steady-state streams.
  4. *Tier 1 Targeted Regression & Architecture Contracts (`TST-HRV-048.4`)*:
     • Run locked incremental `./gradlew :app:testDebugUnitTest --rerun --tests "*RrArtifactFilterTest" --tests "*HrvGoldenOracleTest" --tests "*HRVMetricsDeviceTest" --tests "*WorkoutRrDatabaseManagerTest" --tests "*WorkoutRrRepositoryTest" --tests "*HrvPostWorkoutRecomputerTest" --tests "*DfaCalculatorTest" --tests "*ArchitectureConformanceContractTest" --tests "*DeadCodeEliminationContractTest" --tests "*SourceTextReadingAuditContractTest"` with hyphenated `TST-HRV-048` code tags for clean-checkout `REQ-PRO-099.3` parity.

---

### TST-HRV-049: **Polar Verity Sense Optical PPI Morning HRV Check, DB v3 Provenance & Baseline Isolation Suite** (`ATT-9177`) [Verified]
- **Requirement(s)**: `REQ-HRV-049` (`REQ-HRV-049.1`..`REQ-HRV-049.4`)
- **Component**: `RrBeatEvent.kt`, `HrvSignalQualityAnalyzer.kt`, `HrvSensorModality.kt`, `HrvReadinessEntry.kt`, `HrvReadinessDatabaseManager.kt`, `HrvReadinessCalculator.kt`, `HrvReadinessRepository.kt`, `MorningHrvCheckViewModel.kt`, `MorningHrvCheckScreen.kt`, `MorningHrvOpticalCards.kt`, `HrvReadinessTrendMath.kt`, `HrvReadinessTrendCard.kt`, `res/values*/strings_hrv.xml`, `HrvSignalQualityAnalyzerTest.kt`, `HrvReadinessEntryTest.kt`, `HrvReadinessDatabaseManagerTest.kt`, `DatabaseSchemaRegistryHrvTest.kt`, `HrvReadinessCalculatorTest.kt`, `HrvReadinessRepositoryTest.kt`, `MorningHrvCheckViewModelTest.kt`, `MorningHrvOpticalPpiUiTest.kt`, `MorningHrvCheckScreenTest.kt`, `HrvReadinessTrendMathTest.kt`, `HrvReadinessTrendCardTest.kt`, `ControlTrackingViewModelTest.kt`, `TranslationParityTest.kt`, `ArchitectureConformanceContractTest.kt`, `DeadCodeEliminationContractTest.kt`, `SourceTextReadingAuditContractTest.kt`
- **Jira**: `ATT-9177`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Localization Parity**: `9-locale verified (en, de, es, fr, it, ja, nl, pl, pt)` in `res/values*/strings_hrv.xml` via `TranslationParityTest`
- **Test Description**:
  1. *Optical PPI Contract & Signal Quality Mode (`TST-HRV-049.1`, `HrvSignalQualityAnalyzerTest.testOpticalPpiMode_bypassesSmoothedAndQuantizedFlagsWhileEnforcingArtifactAndBeatGates_TST_HRV_049`)*:
     • Verify `@JvmOverloads` `RrBeatEvent` defaults `isOpticalPpi = false`, 6-arg Java `copy(...)` preserves `isOpticalPpi`, and Java callers (`ANTHeartRateDevice.java`, `DeviceManager.java`) compile unchanged.
     • Verify `HrvSignalQualityAnalyzer.analyze`/`analyzeBeats(..., isOpticalPpi = true)` returns `VALID` on low-variance/integer-ms optical PPI streams (no `QUANTIZED_SYNTHETIC` or `SMOOTHED_STREAM` false positives) while still enforcing `INSUFFICIENT_DATA` (`< 30` beats) and `HIGH_ARTIFACTS` (`> 5.0%`), and preserving existing ECG checks when `isOpticalPpi = false`.
  2. *Non-Sticky Morning HRV Optical UX, Start-Screen Eligibility, Rule 8.5 PNG & 9-Locale Parity (`TST-HRV-049.2`, `MorningHrvCheckViewModelTest.testOpticalPpiNonStickyMode_andIsolatedPersistenceWithoutSmoothedEcgFalsePositive_TST_HRV_049`, `MorningHrvCheckViewModelTest.testOpticalPpiHighArtifactSession_triggersHighArtifactWarning_TST_HRV_049`, `MorningHrvOpticalPpiUiTest.testOpticalPpiMorningHrv_showsGuidanceProvenanceAndOpticalArtifactAdvice_TST_HRV_049`, `MorningHrvOpticalPpiUiTest.testCaptureOpticalPpiMorningHrvLightDarkPng_TST_HRV_049`, `ControlTrackingViewModelTest.isMorningHrvCheckVisible_whenPolarVeritySensePaired_remainsEligibleAndHidesAfterOpticalSave_TST_HRV_049`, `HrvReadinessTrendCardTest.testNineLocaleLocalizationParity_allHrvReadinessTrendStringsPresent`, `TranslationParityTest`)*:
     • Verify `MorningHrvCheckViewModel` toggles `isOpticalPpiSession` non-stickily in `READY`/`STABILIZING` (clearing on ECG beats), completes and persists `HrvSensorModality.OPTICAL_PPI` entries without smoothed-ECG alerts, and flags `highArtifactWarning` when optical artifacts exceed `5.0%`.
     • Verify `MorningHrvCheckScreen` behavioral Compose UI renders optical stillness/snug-contact guidance, optical armband artifact advice, `Optical PPI` provenance badge on `ReadinessResultCard`, and captures Rule 8.5 Light/Dark PNG (`docs/engineering/walkthroughs/img/ATT-9177_morning_hrv_optical_ppi.png`) with `private fun` `@Preview`s and `0` `src/main` reads.
     • Verify `ControlTrackingViewModel` keeps the Morning HRV Start-screen CTA eligible for a paired Polar Verity Sense (`DeviceType.HRM`) and hides it after an optical morning check is saved; verify `TranslationParityTest` passes across all 9 locales.
  3. *SQLite v2->v3 Migration, `.attbackup` Parity & Per-Modality Primary Isolation (`TST-HRV-049.3`, `HrvReadinessEntryTest.sensorModality_roundTripsSymmetricallyAndDefaultsLegacyCursorToEcg_TST_HRV_049`, `HrvReadinessDatabaseManagerTest.schemaUpgrade_v1ToV3_and_v2ToV3_matchOnCreateV3ExactParity_TST_HRV_049`, `HrvReadinessDatabaseManagerTest.sameDayEcgAndOpticalEntries_maintainIndependentPrimariesAndFilteredWindowQueries_TST_HRV_049`, `DatabaseSchemaRegistryHrvTest.databaseSchemaRegistry_referencesHrvReadinessDbVersionDirectly_Rule62`, `HrvReadinessRepositoryTest.repositoryCalculateReadiness_isolatesOpticalFromEcgCohort_TST_HRV_049`)*:
     • Verify `v1 -> v3`, `v2 -> v3`, and `onCreate (v3)` exact schema parity (`Rule 64`), legacy rows defaulting to `ECG`, symmetric `toContentValues`/`fromCursor`/`fromCursorBatch` (`Rule 20`), and `DatabaseSchemaRegistryHrvTest` (`v3` supported, `v4` rejected for `.attbackup`).
     • Verify same-day `ECG` and `OPTICAL_PPI` entries retain independent `is_primary_for_day = 1` flags, `updateEntry` recomputes the old `(dateIso, posture, sensorModality)` cohort when keys change, and `getPrimaryEntriesInWindow(..., sensorModality)` filters strictly by `AND sensor_modality = ?`.
  4. *ECG vs Optical SWC Baseline Isolation, Trend Card Segmented Selector & Architecture Contracts (`TST-HRV-049.4`, `HrvReadinessCalculatorTest.calculateReadiness_strictlyIsolatesEcgAndOpticalPpiSwcBaselines_TST_HRV_049`, `HrvReadinessTrendMathTest.calculateTrendSeries_isolatesEcgAndOpticalCohortsAndDefaultsOpticalOnlyAthletes_TST_HRV_049`, `MorningHrvOpticalPpiUiTest.testTrendCard_segmentedModalitySelectorSwitchesBetweenEcgAndOpticalCohorts_TST_HRV_049`, `ArchitectureConformanceContractTest`, `DeadCodeEliminationContractTest`, `SourceTextReadingAuditContractTest`)*:
     • Verify `HrvReadinessCalculator` and `HrvReadinessTrendMath` isolate 7d rolling average and 60d SWC corridors by `(posture, sensorModality)`, default optical-only athletes to `OPTICAL_PPI`, and render a `SingleChoiceSegmentedButtonRow` (`icon = {}`) cohort selector + provenance badge in `HrvReadinessTrendCard` without modifying `AthleteProfileScreen.kt` (`1,975/1,975` LoC) while keeping `MorningHrvCheckScreen.kt <= 1,450` LoC and tagging all new tests with hyphenated `TST-HRV-049` comments (`REQ-PRO-099.3`).

---

### TST-HRV-050: **Per-Device HRV Source Selection, Parallel Live Calculation & Rolling SDNN Suite** (`ATT-8990`) [Verified]
- **Requirement(s)**: `REQ-HRV-050` (`REQ-HRV-050.1`..`REQ-HRV-050.4`)
- **Component**: `DeviceType.java`, `MySensorManager.java`, `HrvRollingEngine.kt`, `HrvTelemetryProvider.kt`, `PerDeviceHrvController.kt`, `HRVMetricsDevice.kt`, `BTLEHeartRateDevice.java`, `ANTHeartRateDevice.java`, `PolarOnlineStreamingBridge.kt`, `SensorFieldState.kt`, `TrackingViewModel.kt`, `SensorFieldView.kt`, `EditSensorFieldDialog.kt`, `HrvRampTestViewModel.kt`, `SensorTypeHrvTest.kt`, `MySensorManagerHrvPriorityTest.kt`, `HrvRollingEngineTest.kt`, `HRVMetricsDeviceTest.kt`, `HrvGoldenOracleTest.kt`, `PerDeviceHrvControllerTest.kt`, `BTLEHeartRateDeviceTest.kt`, `ANTHeartRateDeviceTest.kt`, `PolarOnlineStreamingBridgeTest.kt`, `TrackingViewModelHrvTest.kt`, `HrvRampTestViewModelTest.kt`, `ArchitectureConformanceContractTest.kt`, `DeadCodeEliminationContractTest.kt`, `InterfaceSignatureDriftContractTest.kt`, `SourceTextReadingAuditContractTest.kt`
- **Jira**: `ATT-8990`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Localization Parity**: `N/A - no UI strings`
- **Test Description**:
  1. *`DeviceType.HRM` Mapping, `MySensorManager` `HRV_PRIORITY` & `FilterManager` Routing (`TST-HRV-050.1`, `SensorTypeHrvTest.testHrvRmssdProperties`, `SensorTypeHrvTest.testHrvDfaA1Properties`, `SensorTypeHrvTest.testHrvArtifactRateProperties`, `SensorTypeHrvTest.testHrvSdnnProperties`, `MySensorManagerHrvPriorityTest.testHrvPriorityArray_prefersHrvMetricsOverHrmForAllFourHrvTypes_TST_HRV_050_1`, `MySensorManagerHrvPriorityTest.testBestSensorResolution_remainsOnHrvMetricsEvenWhenHrmRegistersFirst_TST_HRV_050_1`, `MySensorManagerPriorityTest`, `FilterManagerRebindTest`)*:
     • Verify `DeviceType.getDeviceTypeList()` maps `HRV_RMSSD`, `HRV_DFA_A1`, `HRV_ARTIFACT_RATE`, and `HRV_SDNN` to `DeviceType.HRM` while retaining `DeviceType.HRV_METRICS` enum constant for global `HRVMetricsDevice`.
     • Verify `MySensorManager` `HRV_PRIORITY = { HRV_METRICS, HRM }` keeps `"Bester Sensor"` (`deviceName == null`) bound to global arbitrated `HRVMetricsDevice` even when `HRM` devices register HRV sensors first; verify `FilterManager.resolveSensor()` routes pinned `deviceName` to the named HRM device and `null` to `HRVMetricsDevice`.
  2. *Shared `HrvRollingEngine`, Live Rolling `HRV_SDNN` & NeuroKit2 Golden Parity (`TST-HRV-050.2`, `HrvRollingEngineTest.testRollingSdnn_computesUnbiasedSampleStdDevOver120sWindow_TST_HRV_050_2`, `HrvRollingEngineTest.testRollingSdnn_suppressedBelowMinBeatsAndAboveArtifactGateAndOnDrain_TST_HRV_050_2`, `HrvRollingEngineTest.testEmptyLookaheadTrimmedActiveWindow_emitsZeroArtifactRateAndNullSdnn_TST_HRV_050_2`, `HRVMetricsDeviceTest.testLiveRollingSdnnAndTelemetryProviderExposure_TST_HRV_050_2`, `HrvGoldenOracleTest`)*:
     • Verify `HrvRollingEngine` and `HRVMetricsDevice.getHrvSdnnSensor()` compute unbiased $N-1$ rolling `HRV_SDNN` over active 120s non-artifact NN intervals (`>= 10` valid beats), suppress `HRV_SDNN` to `null` when artifact rate exceeds configured `[3.0, 5.0]%` gate or `< 10` valid beats or `+150 s` drained, expose `latestArtifactRatePercent` and `signalQualityResult` via `HrvTelemetryProvider`, and keep `HrvGoldenOracleTest` bit-identical.
  3. *Lazy Per-Device HRV Lifecycle, Disconnect Stale-Value Guard & Parallel HRMs (`TST-HRV-050.3`, `PerDeviceHrvControllerTest.testLazySingleBatchRegistration_andDisconnectNullWhileActivatedGuard_TST_HRV_050_3`, `PerDeviceHrvControllerTest.testOnAccumulatorsReset_pushesNullKeepsSensorsRegisteredAndPausesScheduler_TST_HRV_050_3`, `PerDeviceHrvControllerTest.testConcurrentBeatsCalculateAndReset_remainsThreadSafeWithoutExceptions_TST_HRV_050_3`, `PerDeviceHrvControllerTest.testRacingOnRrBeatVsShutDown_leavesZeroOrphanedSensors_TST_HRV_050_3`, `PerDeviceHrvControllerTest.testRacingOnDisconnectVsReconnectOnRrBeat_neverClobbersReconnectSensors_TST_HRV_050_3`, `PerDeviceHrvControllerTest.testDelayedOnAccumulatorsReset_afterFreshPostResetBeatsDoesNotOverwriteValidMetricsWithNull_TST_HRV_050_3`, `PerDeviceHrvControllerTest.testRacingColdStartOnRrBeatVsOnAccumulatorsReset_neverWedgesSensorsUnregistered_TST_HRV_050_3`, `BTLEHeartRateDeviceTest.testPerDeviceHrv_parallelBtleDevicesComputeDistinctValuesAndClearOnDisconnectAndReset_TST_HRV_050_3`, `ANTHeartRateDeviceTest.testPerDeviceHrv_antHeartRateRegistersLazilyCalculatesAndClearsOnDisconnectAndReset_TST_HRV_050_3`, `PolarOnlineStreamingBridgeTest.testPerDeviceHrv_polarBridgeRegistersLazilyCalculatesAndClearsOnDisconnectAndReset_TST_HRV_050_3`)*:
     • Verify Rule 52 lazy single-batch registration of all 4 HRV `MySensor`s on first RR beat across `BTLEHeartRateDevice`, `ANTHeartRateDevice`, and `PolarOnlineStreamingBridge` (single `SENSORS_CHANGED` broadcast, zero sensors/scheduler before first RR beat).
     • Verify two concurrent HRMs produce independent live `HRV_DFA_A1`, `HRV_RMSSD`, `HRV_ARTIFACT_RATE`, and `HRV_SDNN` values; verify `unregisterSensors()`, `onAccumulatorsReset()`, and `shutDown()` push `newValue(null)` while still activated before deactivation/unregistration, cancel the scheduler cleanly, re-arm on reconnect, unify all lifecycle mutations (`onRrBeat` cold-start/reconnect, `onAccumulatorsReset`, `onDisconnect`, `shutDown`) under `publishLock -> lock` with defense-in-depth rollback (zero orphaned sensors, zero reconnect clobber, zero permanent-wedge trap), prevent delayed accumulator resets from overwriting fresh post-reset metrics, and remain thread-safe across concurrent beat/calculate/reset calls.
  4. *Per-Device Cockpit Isolation, `HRV_SDNN` Disclaimer, Ramp-Test Guard & Architecture Contracts (`TST-HRV-050.4`, `TrackingViewModelHrvTest.testSideBySidePinnedHrvTiles_isolateSignalQualityZoneColorAndArtifactGatingWithoutCoLocatedArtifactTile_TST_HRV_050_4`, `HrvRampTestViewModelTest.testRampTestIgnoresPinnedPerDeviceHrvTelemetry_TST_HRV_050_4`, `ArchitectureConformanceContractTest`, `DeadCodeEliminationContractTest`, `InterfaceSignatureDriftContractTest`, `SourceTextReadingAuditContractTest`)*:
     • Verify side-by-side pinned `HRV_DFA_A1` tiles isolate `hrvSignalQualityStatus`, zone suppression, and `isArtifactGated` per `sourceDeviceName` (including without a co-located `HRV_ARTIFACT_RATE` tile via `HrvDeviceLookup`); verify `EditSensorFieldDialog` resolves per-device `signalQualityStatus` and includes `HRV_SDNN` alongside `SensorFieldView` in `isHrvMetric`; verify `HrvRampTestViewModel` ignores `deviceName != null`; verify `0` LoC added to `DeviceManager.java` (`1,707/1,708`), `TrackerService.java` (`3,538/3,559`), and `DevicesDatabaseManager.java` (`1,669/1,671`) and hyphenated `TST-HRV-050` tags (`REQ-PRO-099.3`).
- **Verification Evidence**: Gate 4 Bounce 2 (`ATT-9263`) verified SHA `0f641afc` (`Rule 7.5`, `0` `app/` code drift) — `:app:compileDebugKotlin` & `:app:compileDebugUnitTestKotlin` `0` warnings + `141/141 PASS` (`0` failed, `0` skipped) across all 17 suites (`PerDeviceHrvControllerTest` `7/7` including `testRacingOnRrBeatVsShutDown_leavesZeroOrphanedSensors_TST_HRV_050_3`, `testRacingOnDisconnectVsReconnectOnRrBeat_neverClobbersReconnectSensors_TST_HRV_050_3`, `testDelayedOnAccumulatorsReset_afterFreshPostResetBeatsDoesNotOverwriteValidMetricsWithNull_TST_HRV_050_3`, `testRacingColdStartOnRrBeatVsOnAccumulatorsReset_neverWedgesSensorsUnregistered_TST_HRV_050_3`); `4/4` Python safety linters PASS; clean-checkout `verify_requirement_governance.py` EXIT `0`.

---

### TST-HRV-051: **Polar Verity Sense Optical PPI Streaming, Hardware Gating & Workout DFA alpha 1 Isolation Suite** (`ATT-9176`) [Verified]
- **Requirement(s)**: `REQ-HRV-051` (`REQ-HRV-051.1`..`REQ-HRV-051.4`)
- **Component**: `PolarPpiStreamProcessor.kt`, `PolarOnlineStreamingBridge.kt`, `HrvRollingEngine.kt`, `HRVMetricsDevice.kt`, `PerDeviceHrvController.kt`, `TrackerService.java`, `PolarPpiStreamProcessorTest.kt`, `PolarOnlineStreamingBridgeTest.kt`, `HrvRollingEngineTest.kt`, `HRVMetricsDeviceTest.kt`, `PerDeviceHrvControllerTest.kt`, `HrvGoldenOracleTest.kt`, `HrvSignalQualityAnalyzerTest.kt`, `RrArtifactFilterTest.kt`, `TrackerServiceRrOpticalIsolationTest.kt`, `TrackerServiceRrFlushTest.kt`, `ArchitectureConformanceContractTest.kt`, `SystemLiveTrackingRequirementsContractTest.kt`, `DeadCodeEliminationContractTest.kt`, `InterfaceSignatureDriftContractTest.kt`, `SourceTextReadingAuditContractTest.kt`
- **Jira**: `ATT-9176`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Localization Parity**: `N/A - no UI strings`
- **Test Description**:
  1. *Pure-JVM Optical PPI Filtering, Hardware Artifact Stamping & Reset (`TST-HRV-051.1`, `PolarPpiStreamProcessorTest.testValidSamples_reconstructMonotonicTimestampsTaggedOpticalPpi_TST_HRV_051_1`, `PolarPpiStreamProcessorTest.testNonPositivePpiDropped_andHardwareArtifactsStampedGapPreservingTimeline_TST_HRV_051_1`, `PolarPpiStreamProcessorTest.testCleanReferenceRrNotMutatedByHardwareArtifact_andGapBeatsAccurateForSubsequentOutOfRange_TST_HRV_051_1`, `PolarPpiStreamProcessorTest.testHardwareArtifactsSurviveRrArtifactFilterAndNeverEnterCleanRmssdPairs_TST_HRV_051_1`)*:
     • Verify valid `PolarPpiSample` batches (`ppi > 0`, `!blockerBit`, skin contact OK or unsupported) reconstruct monotonic UTC timestamps with `isOpticalPpi = true` and `ARTIFACT_NORMAL`.
     • Verify `ppi <= 0` samples are dropped completely; verify `blockerBit == true` and `(skinContactSupported && !skinContactStatus)` samples preserve monotonic timeline spacing, are stamped `RrBeatEvent.ARTIFACT_GAP` (`gapBeats = 0` when physiological, `isOpticalPpi = true`), never mutate `lastNormalRrMs`, preserve clean-reference `physGap` for `i > 0` out-of-range beats, survive `RrArtifactFilter.filterLiveCausal` & `filterBidirectionalBeats` as artifacts, and never enter clean `rMSSD` beat pairs.
     • Verify `PolarPpiStreamProcessor.reset()` clears monotonic timestamp, reference RR, and packet counter state.
  2. *Decoupled Verity Sense PPI Lifecycle, H10 Guard, Atomic Re-Entrancy & `mRrIntervalSensor` Precision (`TST-HRV-051.2`, `PolarOnlineStreamingBridgeTest.testVeritySensePpiStreaming_decoupledLifecycleAtomicReentrancyAndTeardown_TST_HRV_051_2`, `PolarOnlineStreamingBridgeTest.testPolarH10_neverProbesStartsOrStopsOpticalPpiStreaming_TST_HRV_051_2`, `PolarOnlineStreamingBridgeTest.testVeritySensePpi_lateDisIdentificationAndExceptionIsolation_TST_HRV_051_2`, `PolarOnlineStreamingBridgeTest.testVeritySensePpi_syncAlreadyOpenSessionLaunchesPpiIndependently_TST_HRV_051_2`)*:
     • Verify Verity Sense probes `getAvailableOnlineStreamDataTypes` and launches `startPpiStreaming` independently of `accJob`/`gyroJob` across all 3 readiness hooks (`bleSdkFeatureReady(FEATURE_POLAR_ONLINE_STREAMING)`, `syncAlreadyOpenSessionIfReady()`, and late DIS/`FEATURE_POLAR_OFFLINE_RECORDING` identification).
     • Verify Polar H10 (`isVeritySense == false`) NEVER probes or calls `startPpiStreaming` or `stopStreaming(PmdMeasurementType.PPI)`.
     • Verify rapid duplicate readiness callbacks trigger strictly one `startPpiStreaming` subscription via atomic `isPpiStreamingActive.compareAndSet(false, true)` + `CoroutineStart.LAZY` + job-identity `finally` cleanup (zero PMD `ERROR_ALREADY_IN_STATE` races, full non-cancellation exception isolation).
     • Verify `ARTIFACT_GAP` optical PPI samples dispatch to `perDeviceHrvController` and `RrBeatListener`s WITHOUT updating `mRrIntervalSensor`; verify `deviceDisconnected`, `blePowerStateChanged(false)`, `onAccumulatorsReset()`, and `shutDown()` cancel `ppiJob`, reset `PolarPpiStreamProcessor`, and invoke `stopStreaming(streamId, PmdMeasurementType.PPI)` on `shutDown()` without disrupting `HR`/`ACC`/`GYRO`.
  3. *Dual Live Workout `DFA alpha-1` Isolation & Optical Signal Quality Mode (`TST-HRV-051.3`, `HrvRollingEngineTest.testOpticalPpiSuppressesDfaAlpha1_preservesRmssdSdnnOpticalQuality_andRecoversNonStickilyOnEcg_TST_HRV_051_3`, `HRVMetricsDeviceTest.testOpticalPpiSuppressesLiveDfaAlpha1AndResumesOnEcg_TST_HRV_051_3`, `PerDeviceHrvControllerTest.testPerDeviceOpticalPpiSuppressesDfaAlpha1WhileComputingRmssdAndSdnn_TST_HRV_051_3`, `HrvGoldenOracleTest`, `HrvSignalQualityAnalyzerTest`, `RrArtifactFilterTest`)*:
     • Verify `isOpticalPpi = true` beats suppress `HRV_DFA_A1` to `null` in `HrvRollingEngine`, global `HRVMetricsDevice`, and pinned `PerDeviceHrvController` while `HRV_RMSSD`, `HRV_SDNN`, `HRV_ARTIFACT_RATE`, and optical `HrvSignalQualityAnalyzer` status continue evaluating accurately.
     • Verify non-sticky recovery: when ECG beats (`isOpticalPpi = false`) replace the active `120 s` window (or after `resetSession()`), `HRV_DFA_A1` resumes computing and emitting normal values; verify `HrvGoldenOracleTest` remains bit-identical.
  4. *`WorkoutRr.db` Isolation & Architecture/System Governance Contracts (`TST-HRV-051.4`, `TrackerServiceRrOpticalIsolationTest.testOpticalPpiBeatsExcludedFromWorkoutRrQueue_whileEcgBeatsPersistNormally_TST_HRV_051_4`, `TrackerServiceRrFlushTest`, `ArchitectureConformanceContractTest`, `SystemLiveTrackingRequirementsContractTest`, `DeadCodeEliminationContractTest`, `InterfaceSignatureDriftContractTest`, `SourceTextReadingAuditContractTest`)*:
     • Verify `TrackerService.mRrBeatListener` drops `isOpticalPpi == true` beats during `AutoPauseState.TRACKING` (`mWorkoutID > 0`) while queuing ECG beats (`isOpticalPpi == false`) normally into `WorkoutRr.db`.
     • Verify all architecture LoC caps (`DeviceManager.java` `1,707/1,708`, `DevicesDatabaseManager.java` `1,669/1,671`, `TrackerService.java` `3,538/3,559`, `PolarOnlineStreamingBridge.kt` `1,301/1,500`), `SystemLiveTrackingRequirementsContractTest`, dead-code/signature/source-read contracts, and hyphenated `TST-HRV-051` tags (`REQ-PRO-099.3`).
- **Verification Evidence**: Gate 4 (`ATT-9277`) independently verified SHA `13416d59` (`Rule 7.5`, `0` `app/` code drift) — `:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin`, and `:app:assembleDebug` PASS with `0` new Kotlin compiler warnings + `121/121 PASS` (`0` failed, `0` errors, `0` skipped) across all 15 suites; `4/4` Python safety linters PASS (`check_secrets_hygiene.py`, `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`); clean-checkout `verify_requirement_governance.py` EXIT `0`.

---

### TST-PER-054: **Authoritative Period Primary Region Selection, Sport-Aware Preview Curation & Bounded Caching Verification** (`ATT-8664`) [Verified]
- **Requirement(s)**: `REQ-PER-046`
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodPrimaryRegionSelectionTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepositoryMemoryTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepositorySyncTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodCardMapStateTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModelTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/SpatialRegionEngineTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummaryCardUiTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodVertexBudgetTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapVisualsTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapAdaptiveLayeringTest.kt`
- **Jira**: `ATT-8664`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Localization Parity**: `N/A - no UI strings`
- **Test Description**:
  1. *Authoritative Primary-Region Ranking & Extrema Outlier Immunity (`TST-PER-054.1`, `PeriodPrimaryRegionSelectionTest#testMultiRegionExtremaOutliers_selectsTrueHomePrimaryRegionWithTrueCount_tstPer054_1`)*:
     • Enrich a multi-region period (`159` home workouts + `2` outlier vacation workouts defining `northId`/`westId`), pass through `PeriodsViewModel.filterPeriodSummary(..., {BIKE, RUN})` and `PeriodMapUtils.prepareCardMapPreviewSync()`, and assert home region is selected as Primary (`regionCount == 2`, `primaryRegionPathsCount == 159`, `activeRegionId == "region_0"`, `polylines.size == 8` all in home region), matching `SpatialRegionEngine.detectRegions()` 1:1.
  2. *Sport-Toggle Re-Ranking, Timestamp Ordering & Indoor Isolation (`TST-PER-054.2`, `PeriodPrimaryRegionSelectionTest#testSportToggleRerankingAndIndoorIsolation_updatesPrimaryRegionAndPolylines_tstPer054_2`, `PeriodsViewModelTest.kt`)*:
     • Verify toggling `{BIKE, RUN}` -> `{BIKE}` -> `{RUN}` -> `{UNKNOWN}` re-ranks surviving regions (`filteredCount > 0`), merges up to `8` newest genuine matching-sport polylines (`startTimeS DESC`), returns `emptyList()` for 100%-indoor sports without leaking tracks, and preserves `workoutIdToAllPolylinesMap == emptyMap()`.
  3. *Cache Key Invalidation & Geocoding Center Parity (`TST-PER-054.3`, `PeriodPrimaryRegionSelectionTest#testCacheInvalidationOnRegionCountChangeAndCenterGeocodingParity_tstPer054_3`, `PeriodCardMapStateTest.kt`, `PeriodSummaryCardUiTest.kt`)*:
     • Verify distinct LRU cache entries when `primaryRegionWorkoutCount` or `detectedRegionCount` changes under identical preview polylines, and verify both `prepareCardMapPreviewSync()` and `prepareCardMapPreview()` geocode using `primaryRegionSummary.center`.
  4. *`REQ-PER-039` Memory Bounds, Legacy Fallback & Governance Contracts (`TST-PER-054.4`, `PeriodPrimaryRegionSelectionTest#testHighVolume520Workouts_preservesReqPer039MemoryBoundsAndLegacyFallback_tstPer054_4`, `PeriodsRepositoryMemoryTest.kt`, `SpatialRegionEngineTest.kt`, Contracts)*:
     • Verify 520-workout multi-region enrichment preserves `workoutIdToPathMap == emptyMap()`, `workoutIdToAllPolylinesMap == emptyMap()`, `workoutIdToPolylineMap.size <= 5`, `polylines.size <= 8`, legacy synthetic `PeriodSummary` fallback, and all architecture/dead-code/source-read contracts (`0` monolith edits, `0` new `src/main` reads).
- **Verification Evidence**: Gate 4 (`ATT-9311`) independently verified SHA `a68fb43a` (`Rule 7.5`, `0` `app/` code drift) — `:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin`, and `:app:assembleDebug` PASS with `0` new Kotlin compiler warnings + `99/99 PASS` (`0` failed, `0` errors, `0` skipped) across all 13 suites (`PeriodPrimaryRegionSelectionTest` `4/4`, `PeriodsRepositoryMemoryTest` `6/6`, `PeriodsRepositorySyncTest` `8/8`, `PeriodCardMapStateTest` `11/11`, `PeriodsViewModelTest` `16/16`, `SpatialRegionEngineTest` `8/8`, `PeriodSummaryCardUiTest` `1/1`, `PeriodVertexBudgetTest` `6/6`, `PeriodMapVisualsTest` `7/7`, `PeriodMapAdaptiveLayeringTest` `9/9`, `ArchitectureConformanceContractTest` `11/11`, `DeadCodeEliminationContractTest` `6/6`, `SourceTextReadingAuditContractTest` `6/6`); `4/4` Python safety linters PASS (`check_secrets_hygiene.py`, `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`); clean-checkout `verify_requirement_governance.py` EXIT `0`.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-17 | AI Agent (`ATT-8164`) | `ATT-8164` | Added standardized `DOC-TST-PERIODS` work-product control header and Change History. |
| `1.0.1` | 2026-11-18 | AI Agent (`ATT-8710`) | `ATT-8710` | Added `TST-HRV-044` [Planned] covering DFA alpha 1 zone math, Compose UI rendering, Rule 8.5 Light/Dark PNGs, and scatter threshold parity. |
| `1.0.2` | 2026-11-18 | AI Agent (`ATT-8710`) | `ATT-8710` | Integrated `TST-HRV-044` [Verified] post-`ATT-8206` (`v1.0.1`) shard reconciliation. |
| `1.0.3` | 2026-11-18 | AI Agent (`ATT-8080`) | `ATT-8080` | Added and verified (`[Verified]`) `TST-PER-052` for `ATT-8080` multi-source tracks, elevation/speed/slope overlays, and per-chart visibility toggles. |
| `1.0.4` | 2026-11-18 | AI Agent (`ATT-8916`, `--as agent1`) | `ATT-8916` | Added `TST-PER-053` (`[Planned]`, `.1`–`.5`) covering pure-JVM coalescing, pre-decimation DB extraction, dual ViewModel propagation, behavioral `createComposeRule()` UI, and contract parity. |
| `1.0.5` | 2026-11-18 | AI Agent (`ATT-8916`, `--as agent1`) | `ATT-8916` | Promoted `TST-PER-053` to `[Implemented]` with all 5 verification tiers green (`102/102` tests) and `Rule 8.5` PNG (`ATT-8916_carriage_timeline.png`) verified. |
| `1.0.6` | 2026-11-18 | AI Agent (`ATT-8916`, `--as agent1`) | `ATT-8916` | Promoted `TST-PER-053` to `[Verified]` following Stage 5 targeted unit/contract/Compose verification, `emulator-5554` smoke launch, and governance parity check. |
| `1.0.7` | 2026-11-19 | Lane A Flash (`ATT-8324`, `--as agent1`) | `ATT-8324` | Updated `Component`, metadata (`Priority`/`Type`/`Verification Method`), and executable `Test Description` contracts for all 14 legacy Aftermath/Periods specs (`TST-PERF-001..010`, `TST-PER-009`, `TST-PER-010`, `TST-PER-012`, `TST-PER-017`) while keeping `[Implemented]` status pending Stage 4/5 execution. |
| `1.0.8` | 2026-11-19 | Lane A Flash (`ATT-8324`, `--as agent1`) | `ATT-8324` | Promoted all 14 Aftermath/Periods test specs (`TST-PERF-001..010`, `TST-PER-009`, `TST-PER-010`, `TST-PER-012`, `TST-PER-017`) to `[Verified]` reusing Gate 4 verified evidence (`Rule 7.5`: `93/93 PASS` across 12 suites, `0` `app/` edits since Gate 4 `3fc8ccf6`). |
| `1.0.9` | 2026-11-19 | Lane A Flash (`ATT-8324`, `--as agent1`) | `ATT-8324` | Stage 6 Bounce 1/3 heal: aligned `TST-PERF-001` card vertex budget to `<= 2,000` (`MAX_CARD_MINI_MAP_VERTICES`) and added `ProgressBarRangeInfo` (`1.0f` vs `0.45f`) Compose assertions in `PeriodsProgressAndEmptyStateUiTest` matching `TST-PERF-006`. |
| `1.0.10` | 2026-11-19 | Lane B Author (`ATT-8846`, `--as agent1`) | `ATT-8846` | Added `TST-HRV-045` (`[Planned]`, `.1`–`.5`) covering parameterized `NavRoutes.morningHrv`, `MorningHrvCheckContent` `forceFullHrv` override/regression, Rule 8.5 Light/Dark PNG, `emulator-5554` verification, and architecture contracts. |
| `1.0.11` | 2026-11-19 | Lane B Author (`ATT-8846`, `--as agent1`) | `ATT-8846` | Promoted `TST-HRV-045` to `[Implemented]` with `MorningHrvCheckScreenTest` `10/10`, `SingleActivityNavigationTest` `16/16`, `ArchitectureConformanceContractTest` `11/11`, `SourceTextReadingAuditContractTest` `6/6` PASS, and Rule 8.5 Light/Dark PNG (`ATT-8846_morning_hrv_force_full.png`) inspected. |
| `1.0.12` | 2026-11-19 | Lane B Author (`ATT-8846`, `--as agent1`) | `ATT-8846` | Promoted `TST-HRV-045` to `[Verified]` in Stage 5 reusing Gate 4 `43/43 PASS` evidence (`Rule 7.5`, `0` `app/` code drift since `c7c6cc76`), Rule 8.5 PNG certification, and governance parity check atop `6927b2f8`. |
| `1.0.13` | 2026-11-19 | Lane B Author (`ATT-8953`, `--as agent1`) | `ATT-8953` | Added `TST-HRV-046` (`[Planned]`, `.1`–`.5`) for inverted DFA alpha 1 Y-axis unit math, portrait+landscape spatial pixel verification, Rule 8.5 Light/Dark PNG, and architecture contracts; reconciled `TST-HRV-044.1` top/bottom band order. |
| `1.0.14` | 2026-11-19 | Lane B Author (`ATT-8953`, `--as agent1`) | `ATT-8953` | Promoted `TST-HRV-046` to `[Implemented]` with 100% PASS across all 8 suites (`56/56`) and Rule 8.5 Light/Dark PNG (`ATT-8953_dfa_a1_inverted_yaxis.png`) inspected. |
| `1.0.15` | 2026-11-19 | Lane B Author (`ATT-8953`, `--as agent1`) | `ATT-8953` | Promoted `TST-HRV-046` to `[Verified]` in Stage 5 reusing Gate 4 verified `56/56 PASS` evidence across all 8 suites (`Rule 7.5`, `0` `app/` code drift since `c152801d`), Rule 8.5 PNG certification, and governance sync atop `2d7f1489`. |
| `1.0.16` | 2026-11-19 | Lane B Author (`ATT-9171`, `--as agent1`) | `ATT-9171` | Added `TST-HRV-047` (`[Planned]`, `.1`–`.4`) covering dynamic rMSSD bounds/outlier guard/label unit tests, portrait & landscape spatial spread, Rule 8.5 Light/Dark PNG, and architecture contracts. |
| `1.0.17` | 2026-11-19 | Lane B Author (`ATT-9171`, `--as agent1`) | `ATT-9171` | Promoted `TST-HRV-047` to `[Implemented]` with 100% PASS across all 7 targeted/contract suites (`59/59`) and Rule 8.5 Light/Dark PNG (`ATT-9171_rmssd_dynamic_yaxis.png`) inspected. |
| `1.0.18` | 2026-11-19 | Lane B Author (`ATT-9171`, `--as agent1`) | `ATT-9171` | Promoted `TST-HRV-047` to `[Verified]` in Stage 5 reusing Gate 4 verified `59/59 PASS` evidence across all 7 suites (`Rule 7.5`, `0` `app/` code drift since `c2979843`), Rule 8.5 PNG certification, and governance sync. |
| `1.0.19` | 2026-11-20 | Lane B Author (`ATT-9160`, `--as agent1`) | `ATT-9160` | Added `TST-HRV-048` (`[Planned]`, `.1`–`.4`) covering 1:1 input artifact/gap metadata, idempotent delta DB persistence, 180s causal filter context & phase-aligned reconciliation, and unified artifact gate thresholds. |
| `1.0.20` | 2026-11-20 | Lane B Author (`ATT-9160`, `--as agent1`) | `ATT-9160` | Promoted `TST-HRV-048` to `[Implemented]` with 100% PASS across all 10 unit/DB/golden/contract test suites and static `TST-HRV-048` code tag verification. |
| `1.0.21` | 2026-11-20 | Lane B Author (`ATT-9160`, `--as agent1`) | `ATT-9160` | Promoted `TST-HRV-048` to `[Verified]` in Stage 5 reusing Gate 4 verified `76/76 PASS` evidence across all 10 suites (`Rule 7.5`, `0` `app/` code drift since `f6f7bb41`), clean-checkout `TST-HRV-048` traceability parity, and governance sync. |
| `1.0.22` | 2026-11-20 | Lane B Author (`ATT-9177`, `--as agent1`) | `ATT-9177` | Added `TST-HRV-049` (`[Planned]`, `.1`–`.4`) covering `@JvmOverloads` optical PPI contract & signal quality mode, non-sticky Morning HRV optical UX/Rule 8.5 PNG/9-locale parity, SQLite v2->v3 `.attbackup` & primary isolation, and SWC baseline isolation with `SingleChoiceSegmentedButtonRow` trend selector. |
| `1.0.23` | 2026-11-20 | Lane B Author (`ATT-9177`, `--as agent1`) | `ATT-9177` | Promoted `TST-HRV-049` to `[Implemented]` with `133/133 PASS` across all 16 targeted/contract/localization/UI suites, static `TST-HRV-049` code tag verification, and Rule 8.5 Light/Dark PNG (`ATT-9177_morning_hrv_optical_ppi.png`) inspected. |
| `1.0.24` | 2026-11-20 | Lane B Author (`ATT-9177`, `--as agent1`) | `ATT-9177` | Promoted `TST-HRV-049` to `[Verified]` in Stage 5 reusing Gate 4 verified `133/133 PASS` evidence across all 16 suites (`Rule 7.5`, `0` `app/` code drift since `88c46b60`), Rule 8.5 PNG certification, verbatim test method citations, clean-checkout `TST-HRV-049` traceability parity, and governance sync. |
| `1.0.25` | 2026-11-20 | Lane B Author (`ATT-8990`, `--as agent1`) | `ATT-8990` | Added `TST-HRV-050` (`[Planned]`, `.1`–`.4`) covering `DeviceType.HRM` mapping & `HRV_PRIORITY`, shared `HrvRollingEngine` + rolling `HRV_SDNN`, lazy `PerDeviceHrvController` lifecycle & disconnect stale-value guard across BLE/ANT+/Polar, and isolated cockpit/ramp-test HRV lookup + contracts. |
| `1.0.26` | 2026-11-20 | Lane B Author (`ATT-8990`, `--as agent1`) | `ATT-8990` | Promoted `TST-HRV-050` to `[Implemented]` with `137/137 PASS` across all 17 targeted/contract suites (`SensorTypeHrvTest`, `MySensorManagerHrvPriorityTest`, `MySensorManagerPriorityTest`, `FilterManagerRebindTest`, `HrvRollingEngineTest`, `HRVMetricsDeviceTest`, `HrvGoldenOracleTest`, `PerDeviceHrvControllerTest`, `BTLEHeartRateDeviceTest`, `ANTHeartRateDeviceTest`, `PolarOnlineStreamingBridgeTest`, `TrackingViewModelHrvTest`, `HrvRampTestViewModelTest`, `ArchitectureConformanceContractTest`, `DeadCodeEliminationContractTest`, `InterfaceSignatureDriftContractTest`, `SourceTextReadingAuditContractTest`) and static `TST-HRV-050` tag parity. |
| `1.0.27` | 2026-11-20 | Lane B Author (`ATT-8990`, `--as agent1`) | `ATT-8990` | Promoted `TST-HRV-050` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9247`) verified `137/137 PASS` evidence across all 17 suites + `:app:assembleDebug` (`Rule 7.5`, `0` `app/` code drift since `7a7b387a`), verbatim test method citations, `4/4` Python safety linters PASS, clean-checkout `TST-HRV-050` traceability parity, and governance sync. |
| `1.0.28` | 2026-11-20 | Lane B Author (`ATT-8990`, `--as agent1`) | `ATT-8990` | Stage 6 Bounce 1 heal: updated `TST-HRV-050` citations & `Verification Evidence` to Gate 4 Bounce 1 (`ATT-9255`) verified SHA `88ed018b` (`140/140 PASS`, `0` Kotlin compiler warnings, `PerDeviceHrvControllerTest` `6/6` adding 3 deterministic race tests). |
| `1.0.29` | 2026-11-20 | Lane B Author (`ATT-8990`, `--as agent1`) | `ATT-8990` | Stage 6 Bounce 2 heal: updated `TST-HRV-050` citations & `Verification Evidence` to Gate 4 Bounce 2 (`ATT-9263`) verified SHA `0f641afc` (`141/141 PASS`, `0` Kotlin compiler warnings, `PerDeviceHrvControllerTest` `7/7` adding `testRacingColdStartOnRrBeatVsOnAccumulatorsReset_neverWedgesSensorsUnregistered_TST_HRV_050_3`). |
| `1.0.30` | 2026-11-20 | Lane B Author (`ATT-9176`, `--as agent1`) | `ATT-9176` | Added `TST-HRV-051` (`[Planned]`, `.1`–`.4`) covering pure-JVM `PolarPpiStreamProcessor` filtering/artifact stamping/reset, decoupled Verity Sense PPI lifecycle/H10 guard/atomic re-entrancy/`mRrIntervalSensor`, dual live `DFA alpha-1` suppression, and `TrackerService` `WorkoutRr.db` optical exclusion + contracts. |
| `1.0.31` | 2026-11-20 | Lane B Author (`ATT-9176`, `--as agent1`) | `ATT-9176` | Promoted `TST-HRV-051` to `[Implemented]` following Stage 4 implementation and 100% PASS across all 15 targeted & contract suites (`PolarPpiStreamProcessorTest`, `PolarOnlineStreamingBridgeTest`, `HrvRollingEngineTest`, `HRVMetricsDeviceTest`, `PerDeviceHrvControllerTest`, `HrvGoldenOracleTest`, `HrvSignalQualityAnalyzerTest`, `RrArtifactFilterTest`, `TrackerServiceRrOpticalIsolationTest`, `TrackerServiceRrFlushTest`, `ArchitectureConformanceContractTest`, `SystemLiveTrackingRequirementsContractTest`, `DeadCodeEliminationContractTest`, `InterfaceSignatureDriftContractTest`, `SourceTextReadingAuditContractTest`) with `0` Kotlin compiler warnings. |
| `1.0.32` | 2026-11-20 | Lane B Author (`ATT-9176`, `--as agent1`) | `ATT-9176` | Promoted `TST-HRV-051` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9277`) verified `121/121 PASS` evidence across all 15 suites + `:app:assembleDebug` + `0` new warnings (`Rule 7.5`, `0` `app/` code drift since `13416d59`), exact verbatim method citations, `4/4` Python safety linters PASS, and governance sync. |
| `1.0.33` | 2026-11-20 | AI Agent (`ATT-8664`, `--as agent1`) | `ATT-8664` | Added `TST-PER-054` `[Planned]` (`TST-PER-054.1`–`.4`, strictly symmetric `REQ-PER-046`) covering full-period primary-region selection vs extrema outliers, sport-toggle re-ranking & indoor isolation, primary-region-keyed LRU/`produceState` cache invalidation & center geocoding parity, and `REQ-PER-039` memory bounds & contracts (`Localization Parity: N/A - no UI strings`). |
| `1.0.34` | 2026-11-20 | AI Agent (`ATT-8664`, `--as agent1`) | `ATT-8664` | Promoted `TST-PER-054` to `[Implemented]` with exact verbatim `PeriodPrimaryRegionSelectionTest` method citations (`4/4 PASS`) and `99/99 PASS` across all 13 targeted/contract suites + `:app:assembleDebug` + `0` new Kotlin compiler warnings. |
| `1.0.35` | 2026-11-20 | AI Agent (`ATT-8664`, `--as agent1`) | `ATT-8664` | Promoted `TST-PER-054` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9311`) verified `99/99 PASS` evidence across all 13 suites + `:app:assembleDebug` + `0` new warnings (`Rule 7.5`, `0` `app/` code drift since `a68fb43a`), verbatim `TestClass#testMethod` citations, `4/4` Python safety linters PASS, and clean-checkout governance sync. |
| `1.0.36` | 2026-11-21 | Single-Lane Author (`ATT-9360`, `--as agent1`) | `ATT-9360` | Refined `TST-WKO-039` (`[Planned]`, `.1`–`.2`) covering `MIN_CTL_FOR_ACWR = 15.0` low-CTL gating, all 10 `calculateRpeTss` levels (`20..140` TSS/h) + invalid/non-finite guards, Tier-4 TSS equivalence (`1h @ RPE 8 == 100.0`), and `REQ-WKO-038` Foster sRPE preservation. |
| `1.0.37` | 2026-11-21 | Single-Lane Author (`ATT-9360`, `--as agent1`) | `ATT-9360` | Promoted `TST-WKO-039` to `[Implemented]` with exact verbatim `PowerPhysiologyCalculatorTest` and `WorkoutRpeFeelTest` method citations. |
| `1.0.38` | 2026-11-21 | Single-Lane Author (`ATT-9360`, `--as agent1`) | `ATT-9360` | Promoted `TST-WKO-039` to `[Verified]` in Stage 5 (`Rule 7.5` Doc-Only Fast Path reusing Gate 4 `ATT-9534` @ `51ce6830`: `67/67 PASS` across 7 suites, `0` `app/` drift, `3/3` safety linters PASS, `--check-verified-evidence` `exit 0`). |
| `1.0.39` | 2026-11-22 | Lane 2 Author (`ATT-9355`, `--as agent1`) | `ATT-9355` | Updated `TST-PER-052.4` (`[Verified]`) adding behavioral Compose UI bounds & dropdown order tests + Light/Dark PNG (`TelemetryChartStackVisibilityTest.kt`) for swapped `CADENCE`/`HEART_RATE` order (`SPEED_PACE → SLOPE → CADENCE → POWER → HEART_RATE → W_PRIME_BAL → HRV_DFA_A1 → HRV_RMSSD`). |
