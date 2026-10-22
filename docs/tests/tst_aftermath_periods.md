# Living Test Specification: Post-Workout Analytics & Periods

**Domain**: `aftermath_periods`  
**Prefixes**: `PER`, `PERF`  
**Scope**: Post-workout analytics, period statistics, zone distributions, graphs and visual scrubbers.  
**Total Tests**: 23

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
