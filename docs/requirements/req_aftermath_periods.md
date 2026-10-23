# Living Requirements: Post-Workout Analytics & Periods

**Domain**: `aftermath_periods`  
**Prefixes**: `PER`, `PERF`  
**Scope**: Post-workout analytics, period statistics, zone distributions, graphs and visual scrubbers.  
**Total Requirements**: 21

---

### REQ-PER-007: Reactive Period-Workout Association [Verified]
- **Tests**: `TST-PERF-008`
- **Implementation**: `PeriodsViewModel.kt`, `PeriodsRepository.kt`
- **Rationale**: The system SHALL ensure that all workouts belonging to a specific period are correctly and reactively associated when viewing period details (Map or List). The visualization SHALL automatically update as workout data is loaded or modified in the background, ensuring no sessions are omitted due to partial loading states.

### REQ-PER-008: Precise Period Filtering [Proposed]
- **Tests**: `TST-PER-009`
- **Implementation**: `PeriodsFragment.kt`, `WorkoutSummariesViewModel.kt`
- **Rationale**: The system SHALL correctly filter the workout list when navigating from a period summary highlight (Base Sport or Sub-Sport). This filtering MUST respect both the temporal bounds of the period and the specific sport identity selected to ensure professional analytical depth.

### REQ-PER-009: Detail Map Sport Filtering [Proposed]
- **Tests**: `TST-PER-012`
- **Implementation**: `PeriodMapScreen.kt`, `InteractivePeriodMap.kt`
- **Rationale**: Within the Period Detail view, the system SHALL allow filtering the map visualization by Sport Type. Tapping a sport summary SHALL toggle its visibility on the map, including tracks, markers, and heatmaps, ensuring a clean and focused analytical context.

### REQ-PER-010: Uniform Period Heatmap Visualization [Verified]
- **Tests**: `TST-PER-013`
- **Implementation**: `MapUtils.kt`, `PeriodMapUtils.kt`, `InteractivePeriodMap.kt`
- **Rationale**: The system SHALL render a period heatmap that represents all recorded workout paths across the entire temporal bounds of the period without spatial truncation. The heatmap overlay SHALL be translucent, keeping underlying map features, terrain, and labels legible. **Invariants**: The heatmap rendering SHALL NOT exceed the maximum memory point budget (`maxPoints`).

### REQ-PER-011: Longest Workout Metric Display [Verified]
- **Tests**: `TST-PER-014`, `TST-PER-015`, `TST-PER-016`
- **Implementation**: `PeriodsRepository.kt`, `PeriodSummaryCard.kt`, `PeriodSummariesDatabaseManager.kt`
- **Rationale**: When displaying the Longest Workout highlight within a sport summary, the system SHALL show the workout's name, active duration, total distance, and total ascent. Period summary card headers SHALL display total duration and workout count. **Invariants**: All displayed metrics SHALL be formatted according to the active localized unit settings.

### REQ-PER-012: Period Map Scalability, Memory Budget & Adaptive Layering Invariant [`TST-PER-018`]
- **Tests**: `PeriodsViewModel.kt`, `PeriodMapScreen.kt`, `InteractivePeriodMap.kt`, `AndroidManifest.xml`
- **Implementation**: Ensure smooth, non-crashing (OOM-free) spatial rendering of long-term training periods with hundreds of sessions.
- **Rationale**: The system SHALL ensure high-performance, non-crashing spatial visualization across periods of arbitrary size (days, weeks, months, years with hundreds of workouts):
1. *Adaptive Layering Threshold*: When a period contains more than a configured safety threshold of member workouts (`MAX_VECTOR_WORKOUTS = 30`), the system SHALL NOT instantiate or render individual vector `Polyline`s (`memberTracks`) and individual member markers (`memberMarkers`) for all member workouts simultaneously. Instead, the spatial analytics SHALL render the high-performance raster `Heatmap` layer representing aggregate route density plus the prominent `anchorTracks` (the spatial boundary/longest routes) and period extrema markers (`extremaMarkers`).
2. *Interactive Single-Track Inspection*: When the athlete taps or peeks a specific workout within a large period, that single workout's path SHALL be rendered as a crisp, interactive vector polyline via `peekedTracks` without rendering all other background sessions as vector paths.
3. *Threshold-Filtered Detail*: When the number of displayed workouts is within the safety threshold ($\le 30$, such as in small periods or when filtered by sport type), individual member tracks and technical markers SHALL be rendered as detailed vector overlays.
4. *Memory & Point Budgeting*: In `PeriodsViewModel`, polyline decoding and `MapTrack` instantiation SHALL be budget-aware, avoiding redundant duplicate representations in memory.
5. *Process Heap Headroom*: The application manifest SHALL declare `android:largeHeap="true"` to grant the runtime sufficient memory bandwidth for GIS map overlays and raster heatmaps on high-resolution devices.

### REQ-PER-013: Multi-Region Period Map Viewport Detection & Adaptive Bounding [`TST-PER-019`]
- **Tests**: `SpatialRegionEngine.kt`, `PeriodsViewModel.kt`, `InteractivePeriodMap.kt`, `PeriodMapScreen.kt`, `PeriodSummaryCard.kt`
- **Implementation**: Eliminate unusable viewports centered on empty oceans (Greenland anomaly) during multi-region training periods while providing seamless 1-tap region switching and preserving single-region performance.
- **Rationale**: When a period contains workouts separated across geographically distinct regions ($> \text{REGION\_CLUSTER\_THRESHOLD\_METERS} = 75\text{ km}$ geodesic span), the system SHALL NOT center the initial map camera or overview thumbnail over empty oceans or continents between regions (e.g. centering over Greenland / the North Atlantic for athletes training in Europe and North America). Instead, the system SHALL partition workouts into geographic activity regions, prioritize the primary activity region, and provide interactive region switching:
1. *Spatial Region Partitioning & Fast-Path*: In `SpatialRegionEngine.detectRegions(workouts)`, workouts with valid GPS coordinates SHALL be partitioned into connected component clusters using Great-Circle Haversine distance with configurable parameter `REGION_CLUSTER_THRESHOLD_METERS` ($75\text{ km}$). If the global bounding box diagonal is $< \text{SINGLE\_REGION\_ENVELOPE\_METERS}$ ($60\text{ km}$) or total GPS workouts $\le 1$, the engine SHALL return a single region immediately ($O(1)$ fast-path).
2. *Dominant Region Identification*: When multiple regions are detected ($k > 1$), the engine SHALL rank regions deterministically: (a) Primary: Workout frequency/count (descending); (b) Secondary tie-breaker: Total active distance in meters (descending); (c) Tertiary tie-breaker: Timestamp of most recent workout (descending). The highest-ranked region SHALL be marked as primary (`isPrimary = true`, index 0).
3. *Initial Viewport & Thumbnail Framing*: In `PeriodSummaryCard.kt` (overview thumbnail) and `InteractivePeriodMap.kt` (detail map), the initial camera bounds SHALL focus tightly on the primary region's bounding box (`primaryRegion.bounds`), eliminating empty ocean tiles and rendering recognizable route polylines.
4. *Antimeridian & Polar Normalization*: When workouts within a cluster cross the $180^\circ$ meridian, `SpatialRegionEngine` SHALL construct wrapped `LatLngBounds(southwest, northeast)` with `southwest.longitude > northeast.longitude`, ensuring Google Maps fits the local $< 180^\circ$ span rather than a $350^\circ+$ world-spanning box. Polar coordinates SHALL be clamped to $[-85^\circ, 85^\circ]$.
5. *Interactive Region Navigation & Overflow Protection*: When `regions.size > 1`, `PeriodMapScreen.kt` SHALL render an interactive horizontally scrollable chip row (`Modifier.horizontalScroll(rememberScrollState())`) featuring `[ Primary: Region 1 (N) ]`, `[ Region 2 (N) ]`, ..., and `[ All Regions (Total) ]`. Tapping a chip SHALL update `selectedRegionId` and animate the camera to that region's bounds. Tapping \"All Regions\" SHALL fit the aggregate global bounds across all regions. When `regions.size <= 1`, the region switcher bar SHALL be completely hidden (`Visibility.GONE`).
6. *State Persistence & Threading*: `PeriodsViewModel` SHALL execute region detection exclusively on background worker threads (`Dispatchers.Default`) and SHALL persist `selectedRegionId` via `SavedStateHandle` across configuration changes and process recreation.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a period containing 100 workouts in Germany (~9°E) and 10 workouts in California (~-122°W),
• *When* the period overview card or detail map is displayed,
• *Then* the initial map camera SHALL focus tightly on the German training region, with zero centering over Greenland or the North Atlantic.
• *When* viewing `PeriodMapScreen` for this multi-region period,
• *Then* an interactive horizontal chip bar SHALL appear with chips for Region 1 (100), Region 2 (10), and All Regions (110).
• *When* the athlete taps the Region 2 chip,
• *Then* the map camera SHALL animate to the California workouts.
• *When* the athlete taps \"All Regions\",
• *Then* the camera SHALL zoom out to encompass both continents.
• *Given* a period where all workouts are within a 50 km perimeter,
• *When* the period map is displayed,
• *Then* the region chip bar SHALL be hidden and the camera bounds SHALL match legacy bounding behavior 100%.

**Invariants**: SQLite schema for `PeriodSummaries.db` MUST NOT be altered; single-region periods MUST maintain 100% camera and visual parity; vector track limit ($\le 30$) and heatmap rendering per `REQ-PER-012` MUST NOT be regressed; all user-facing chip strings MUST maintain 9-language localization parity.

### REQ-PER-001: The system SHALL load and display the detailed visualization (Heatmap and List)  [Verified]
- **Tests**: `TST-PERF-002`
- **Implementation**: `WorkoutClustersViewModel.kt`, `WorkoutClusterRepository.kt`, `WorkoutSummariesDatabaseManager.java`, `WorkoutClusterHeatmapScreen.kt`
- **Rationale**: Ensure a responsive and professional user experience when analyzing recurring routes.
- **Description**: The system SHALL load and display the detailed visualization (Heatmap and List) of a Workout Cluster within 1 second for route families containing up to 50 sessions.

### REQ-PER-002: The system SHALL ensure that only the map state (Heatmap, Tracks, and Markers) o [Verified]
- **Tests**: `TST-BUG-001`
- **Implementation**: `WorkoutClustersViewModel.kt`
- **Rationale**: Guarantee data consistency and prevent visual artifacts during navigation.
- **Description**: The system SHALL ensure that only the map state (Heatmap, Tracks, and Markers) of the most recently selected Workout Cluster is displayed, preventing race conditions during rapid navigation.

### REQ-PER-003: The system SHALL display a detailed progress notification (Migration Status card [Verified]
- **Tests**: `TST-PERF-003`
- **Implementation**: `WorkoutClustersViewModel.kt`, `WorkoutClustersTabsScreen.kt`, `WorkoutClusterRepository.kt`
- **Rationale**: Provide immediate feedback and maintain visual consistency with other progressive loading screens (e.g. Periods).
- **Description**: The system SHALL display a detailed progress notification (Migration Status card) during the initial loading and "self-healing" phase of Workout Clusters to inform the user about the background processing state.

### REQ-PER-004: The hierarchical periods aggregation SHALL utilize a 'Dual-Phase' strategy: Phas [Verified]
- **Tests**: `TST-PERF-005`
- **Implementation**: `PeriodsRepository.kt`
- **Rationale**: Eliminate database lock contention and O(N^2) re-grouping overhead to ensure sub-second startup and true real-time visibility.
- **Description**: The hierarchical periods aggregation SHALL utilize a 'Dual-Phase' strategy: Phase 1 (O(N)) for high-speed database reading and global grouping, and Phase 2 for bucketized commits and incremental UI pumping.

### REQ-PER-005: The 'Migration Status' notification SHALL provide a descriptive high-level title [Verified]
- **Tests**: `TST-PERF-006`
- **Implementation**: `PeriodsRepository.kt`, `MigrationStatus.kt`, `PeriodsTabsScreen.kt`
- **Rationale**: Enhance user transparency and professional feedback during complex background operations.
- **Description**: The 'Migration Status' notification SHALL provide a descriptive high-level title (Introducing relational database) and explicitly label Phase 1 (Reading) and Phase 2 (Syncing) to provide clear technical context.

### REQ-PER-006: The system SHALL display a detailed, multi-phase progress notification during th [Verified]
- **Tests**: `TST-PERF-007`, `TST-PERF-009`, `TST-PERF-010`
- **Implementation**: `WorkoutClusterEngine.kt`, `WorkoutClusterRepository.kt`, `WorkoutClustersViewModel.kt`
- **Rationale**: Provide technical transparency and maintain consistency with other progressive background tasks.
- **Description**: The system SHALL display a detailed, multi-phase progress notification during the full recalculation or migration of Workout Clusters.

### REQ-PER-014: Immediate Period View Reactive Synchronization on Workout Finalization [`TST-PER-020`]
- **Tests**: `PeriodsRepository.kt`, `PeriodsViewModel.kt`, `WorkoutSummariesDatabaseManager.java`
- **Implementation**: Immediate period view reactive synchronization upon workout completion on fresh installs.
- **Rationale**: The system SHALL guarantee that newly finalized workouts are immediately aggregated and presented across all period analytics views (Day, Week, Month, Year) without requiring an app restart, navigation change, or manual refresh, even on clean installations or initial profiles (ATT-3056, refining `REQ-PER-001` and `REQ-MIG-026`):
1. *Direct Workout Finalization Broadcast Handling (`PeriodsRepository.kt`)*:
• `PeriodsRepository` SHALL register an active `BroadcastReceiver` listening for `TrackerService.TRACKING_FINISHED_INTENT` via both `LocalBroadcastManager` and context `sendBroadcast`..
• Upon receiving `TRACKING_FINISHED_INTENT` with a valid `workoutId`, `PeriodsRepository` SHALL query the fresh `WorkoutData` from SQLite, verify `finished == true`, and execute `onWorkoutFinished(workout)`..
2. *Guaranteed Sync State & Forced Dynamic Emission (`PeriodsRepository.kt`)*:
• In `PeriodsRepository.onWorkoutFinished(workout: WorkoutData)`: The method SHALL execute under `rebuildMutex.withLock` to eliminate race conditions with background migrations (`performHierarchicalMigration()`)..
• In the SQLite transaction, after upserting the day summary and rolling up to Week, Month, and Year parent periods, the repository SHALL execute `dbManager.setSyncFinished(db, true)`, ensuring `isSyncFinished()` evaluates to `true` on fresh installs..
• Following the transaction, `onWorkoutFinished()` SHALL invoke `loadFromDatabase(forceIncremental = true)`, forcing immediate in-memory loading and emitting the updated period lists into `_groupedPeriods.value` regardless of migration flag status..
3. *Finished Workout Gating in Range Queries (`WorkoutSummariesDatabaseManager.java` & `PeriodsRepository.kt`)*:
• In `WorkoutSummariesDatabaseManager.getWorkoutsInRangeCursor()`, the query SHALL append `AND WorkoutSummaries.FINISHED = 1` (or equivalent filter in `fetchWorkoutsInDay()`), ensuring unfinalized or in-progress workouts are strictly excluded from historical period summaries..
4. *Active Period View Refresh (`PeriodsViewModel.kt`)*:
• In `PeriodsViewModel.loadPeriods()`: In addition to `workoutRepo.loadAllWorkouts()`, the ViewModel SHALL trigger `periodsRepo.checkIntegrityAndSync()` to ensure any pending discrepancy between SQLite and `PeriodSummaries.db` is reconciled upon screen entry..
5. *Preservation of System Invariants*:
• O(1) self-healing integrity check (`REQ-MIG-026`), background hierarchical dual-phase migration (`REQ-PER-001`), spatial boundary framing, memory caps on vector tracks (`REQ-PER-012`), and 100% full clean-room unit test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-PER-001` (*Historical Hierarchical Period Summaries*, Sprint 2026-40.16, ATT-346) and `REQ-MIG-026` (*O(1) Self-Healing Integrity Verification between SQLite WorkoutSummaries and PeriodSummaries.db*, Sprint 2026-41.2, ATT-909).
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-40.16 (`ATT-346`) and Sprint 2026-41.2 (`ATT-909`).
3. *Root Reason for Existing Formulation*: `loadFromDatabase()` enforced an `isSyncFinished()` check to prevent displaying partial data during long migrations. However, on fresh installs, `isSyncFinished()` defaulted to false and `onWorkoutFinished()` never set it to true nor used `forceIncremental = true`, completely blocking the first workout from appearing.
4. *Preservation of Core Invariants*: Long-term migration safety, dual-phase rollup formulas, and 100% test pass rate remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a fresh install or clean profile with zero previous workouts,
• *When* the athlete completes and saves their first workout (`TRACKING_FINISHED_INTENT`),
• *Then* `PeriodsRepository` processes the intent, updates `PeriodSummaries.db`, sets `isSyncFinished(true)`, and emits populated Day and Week periods into `groupedPeriods` immediately without requiring an app restart.
• *Given* an athlete viewing `PeriodsScreen` on an empty profile,
• *When* the first workout is finished,
• *Then* the UI automatically transitions from `EmptyStatePlaceholder` to displaying populated period cards.
• *Given* `onWorkoutFinished()` invoked while a background migration is pending or active,
• *When* updating database records,
• *Then* execution is serialized via `rebuildMutex.withLock` to prevent data wipe or race conditions.

**Invariants**: O(1) integrity check formula, hierarchical rollup math, and 100% test pass rate MUST NOT be broken.

### REQ-PER-015: Calendar-Aware Period Boundaries Across DST Transitions, Sentinel Epoch Symmetry, and Local Day PMC Bucketing [`TST-PER-021`]
- **Tests**: `PeriodsRepository.kt`, `WorkoutDataMapper.kt`, `PowerPhysiologyCalculator.kt`
- **Implementation**: Calendar-aware local timezone period aggregation boundaries across DST transitions, sentinel epoch symmetry in WorkoutDataMapper, and local-day PMC bucketing in PowerPhysiologyCalculator.
- **Rationale**: The system SHALL calculate temporal aggregation boundaries (Day, Week, Month, Year) in `PeriodsRepository` using calendar-aware local timezone offsets (`plusDays(1)` / `plusWeeks(1)` `.atStartOfDay(zoneId).toEpochSecond() - 1`) to eliminate 1-hour workout overlap or omission across Daylight Saving Time (DST) spring-forward (23h) and fall-back (25h) transitions, SHALL establish 1:1 symmetry in `WorkoutDataMapper.formatDateTime` by returning `Instant.EPOCH.atZone(zoneId).toLocalDateTime()` (epoch 1970-01-01T00:00:00) on parse failures to match sentinel `timestampS = 0L` while filtering out invalid `timestampS <= 0L` in period rebuilds, and SHALL bucket daily training load in `PowerPhysiologyCalculator.computePerformanceManagementChart` according to the athlete's local calendar day (`Instant.ofEpochMilli(sample.timestampMs).atZone(zoneId).toLocalDate().toEpochDay()`) using an injectable `ZoneId = ZoneId.systemDefault()`, while preserving `getPeriodSortKey` database string contracts and 100% clean-room test pass rate (ATT-4016, Implemented in: ATT-4016, Status: Planned, amending `REQ-PER-014` and `REQ-UI-401`):
1. *Calendar-Aware Local Timezone Period Boundaries (`PeriodsRepository.kt`, `REQ-PER-015.1`)*:
• In `PeriodsRepository`, day boundary calculation in `fetchWorkoutsInDay(startS)`, `aggregateDay()`, and `getAffectedPeriodRanges(ldt)` SHALL evaluate the inclusive day end timestamp as: `Instant.ofEpochSecond(dayStartS).atZone(zoneId).toLocalDate().plusDays(1).atStartOfDay(zoneId).toEpochSecond() - 1`.
• In `rollupDayToParents` and `rollupDaysToParentPeriods`, week boundary calculation SHALL evaluate the inclusive week end timestamp as: `Instant.ofEpochSecond(weekStartS).atZone(zoneId).toLocalDate().plusWeeks(1).atStartOfDay(zoneId).toEpochSecond() - 1`.
• On spring-forward DST transitions (23h day = 82,800s, 167h week), the boundary SHALL terminate at exactly 82,799s (not overshooting +86,399s into Monday 00:00:00–00:59:59).
• On fall-back DST transitions (25h day = 90,000s, 169h week), the boundary SHALL terminate at exactly 89,999s (not terminating 1 hour early at 23:00 local time).
• `getPeriodSortKey` string contracts (`yyyy-MM-dd`, `yyyy-Www`, `yyyy-MM`, `yyyy`) SHALL remain strictly preserved for SQLite index and sorting stability (`REQ-MIG-026`).
2. *WorkoutDataMapper Epoch-Zero Sentinel Parity & Invalid Timestamp Guarding (`WorkoutDataMapper.kt`, `PeriodsRepository.kt`, `REQ-PER-015.2`)*:
• In `WorkoutDataMapper.formatDateTime(startTimeString, dbFormat)`, on `ParseException` or invalid date string, the returned `DateTimeResult` SHALL pair `timestampS = 0L` with `localDateTime = LocalDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault())` instead of `LocalDateTime.now()`, ensuring 1:1 parity between timestamp and date representation.
• In `PeriodsRepository.rebuildPeriodsFromWorkouts` and period aggregations, workouts with `timestampS <= 0L` or unparseable timestamps SHALL be safely skipped, preventing corrupt records from polluting current period cards or rolling up into today's summaries.
3. *Local-Timezone Day Bucketing in PMC Evolution (`PowerPhysiologyCalculator.kt`, `REQ-PER-015.3`)*:
• In `PowerPhysiologyCalculator.computePerformanceManagementChart`, day index bucketing SHALL evaluate: `val dayIdx = Instant.ofEpochMilli(sample.timestampMs).atZone(zoneId).toLocalDate().toEpochDay()` with parameter `zoneId: ZoneId = ZoneId.systemDefault()`.
• `nowDayIndex` SHALL evaluate `max(maxSampleDayIndex, Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().toEpochDay())`.
• Workouts completed late in the evening or early morning across non-UTC athlete timezones (e.g. UTC+1/+2 Europe/Berlin, UTC-5 EST) SHALL be assigned strictly to the athlete's local calendar day, preventing false rest days and skewed CTL/ATL/TSB/ACWR curves.
4. *Preservation of System Invariants (`REQ-PER-015.4`)*:
• Zero SQLite schema migration; `PeriodSummaries.db` table schemas and sort key lexicographical indexing remain unchanged.
• EWMA impulse-response math for CTL ($\\tau=42$), ATL ($\\tau=7$), and TSB remains strictly unchanged.
• Clean-room full test suite pass rate under `./gradlew testDebugUnitTest` SHALL remain 100%.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-PER-011` / `REQ-PER-014` (*Period Aggregation Boundary Stability*) and `REQ-UI-401.1` (*PMC Day Bucketing*). Targets `PeriodsRepository.kt`, `WorkoutDataMapper.kt`, `PowerPhysiologyCalculator.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.16 (`ATT-346`, commit `7d82d0b8` — fixed-seconds `7*86400` and `86399`), Sprint 2026-38.1 (`ATT-296`, commit `a562ca80` — `LocalDateTime.now()` fallback in mapper), Sprint 2026-42.7 (`ATT-3859`, commit `dc62bb3c` — UTC `timestampMs / 86_400_000L` PMC indexing).
3. *Root Reason for Existing Formulation*: Fixed-second arithmetic simplified math on standard 24h days; `LocalDateTime.now()` satisfied non-null typing without evaluating timestamp parity; UTC PMC indexing assumed global day boundaries.
4. *Preservation of Core Invariants*: `getPeriodSortKey` lexicographical ordering, O(1) self-healing sync (`REQ-MIG-026`), and PMC impulse-response EWMA equations ($\\text{CTL}$, $\\text{ATL}$, $\\text{TSB}$) remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete in `Europe/Berlin` on the spring-forward DST transition Sunday (23h day, 82,800s),
• *When* `fetchWorkoutsInDay` or `rollupDayToParents` computes the Day and Week boundaries,
• *Then* the day interval ends at `startS + 82799L` and week ends at `weekStart + 167*3600 - 1`, strictly excluding workouts recorded on Monday 00:15:00.
• *Given* an athlete in `Europe/Berlin` on the fall-back DST transition Sunday (25h day, 90,000s),
• *When* `fetchWorkoutsInDay` computes the Day interval,
• *Then* the day interval ends at `startS + 89999L`, capturing workouts recorded at 23:30:00 local time without omission.
• *Given* a corrupt or blank workout date string during `WorkoutDataMapper.formatDateTime`,
• *When* parse exception occurs,
• *Then* `timestampS = 0L` and `localDateTime` reflects `Instant.EPOCH` (1970-01-01T00:00:00) rather than `now()`, and `PeriodsRepository` excludes invalid `timestampS <= 0L` records from period rollups.
• *Given* a workout sample at 00:30 local time in `Europe/Berlin` (UTC+2 in summer),
• *When* `computePerformanceManagementChart` evaluates daily training stress with `ZoneId.of("Europe/Berlin")`,
• *Then* the workout TSS is attributed to the local calendar day rather than the previous UTC day.

**Invariants**: Zero SQLite schema migration; `getPeriodSortKey` string invariance; EWMA math invariance; 100% clean-room test pass rate.

### REQ-PER-016: Period Rebuild Flow Projection, Single-Owner Tracking Finalization Orchestration & Shared Mapper Instance [`TST-PER-022`]
- **Tests**: `PeriodsRepository.kt`, `WorkoutRepository.kt`
- **Implementation**: Eliminate redundant period rebuilds on non-period workout updates and progressive loading chunks, establish single-owner tracking finalization orchestration, and consolidate WorkoutDataMapper allocations in PeriodsRepository.
- **Rationale**: The system SHALL eliminate redundant period rebuilds on non-period workout updates and progressive loading chunks, establish single-owner tracking finalization orchestration, and consolidate `WorkoutDataMapper` allocations across `PeriodsRepository` (ATT-4019, Implemented in: ATT-4019, Status: Verified, amending `REQ-PER-014` and interacting with `REQ-STB-018.1`):
1. *Period-Relevant Flow Projection & Debounced Deduplication (`PeriodsRepository.kt`, `REQ-PER-016.1`)*:
• In `PeriodsRepository`, reactive collection of `workoutRepo.allWorkouts` SHALL be projected and deduplicated using `distinctUntilChanged` on period-relevant workout properties (workout ID, `startTimeS`, `activeTimeSec`, spatial bounds, and sport type), preventing export status mutations (`stravaStatus`, `fileExportStatus`), notes/title edits, and intermediate 50-item streaming chunks from triggering redundant `loadFromDatabase(forceIncremental = true)` invocations and full SQLite period re-queries.
2. *Single-Owner Tracking Finalization Orchestration (`WorkoutRepository.kt`, `PeriodsRepository.kt`, `REQ-PER-016.2`)*:
• `WorkoutRepository` SHALL serve as the single authoritative orchestrator for dispatching workout completion to `PeriodsRepository.onWorkoutFinished(workoutData)`.
• Duplicate direct `BroadcastReceiver` registrations for `TrackerService.TRACKING_FINISHED_INTENT` in `PeriodsRepository` (across both `LocalBroadcastManager` and `ContextCompat.registerReceiver`) SHALL be eliminated, resolving triple and quadruple redundant period aggregations down to strictly one execution per finalized workout.
• `onWorkoutFinished(workout)` SHALL maintain an idempotent recent-workout guard to defensively drop duplicate calls.
3. *Single Lazy / Shared WorkoutDataMapper Allocation (`PeriodsRepository.kt`, `REQ-PER-016.3`)*:
• In `PeriodsRepository`, ad-hoc instantiations of `WorkoutDataMapper(application, workoutSummariesManager, ...)` (such as lines 79, 212, 789) SHALL be consolidated into a single reusable instance (e.g. `private val mapper: WorkoutDataMapper by lazy { ... }` or dependency-injected), eliminating redundant database helper instantiations (`SportTypeDatabaseManager`, `EquipmentDbHelper`, `StravaUploadDbHelper`) per query.
4. *Preservation of System Invariants (`REQ-PER-016.4`)*:
• Immediate period view update upon workout completion (`REQ-PER-014`), fresh install first-workout appearance, O(1) self-healing synchronization (`REQ-MIG-026`), calendar-aware aggregation boundaries across DST transitions (`REQ-PER-015`), and 100% clean-room test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-PER-014` (*Immediate Period View Reactive Synchronization on Workout Finalization*, Sprint 2026-41.7, ATT-3056) and interacts with `REQ-STB-018.1` (*WorkoutRepository Concurrency Invariants & Conflated Dirty-Flag Reloads*, Sprint 2026-43.3, ATT-4002). Targets `PeriodsRepository.kt` and `WorkoutRepository.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-41.7 (`ATT-3056`, commit `f65f8fdd`) registered dual broadcast receivers directly inside `PeriodsRepository`. Later, Sprint 2026-43.3 (`ATT-4002`, commit `1c8e1a68`) introduced `REQ-STB-018.1` delegating downstream component notifications to `WorkoutRepository.reloadWorkoutData`.
3. *Root Reason for Existing Formulation*: ATT-3056 registered both `LocalBroadcastManager` and system context receivers directly in `PeriodsRepository` to ensure the first workout on fresh installs immediately populated empty period views. Subsequent architectural delegation to `WorkoutRepository` resulted in `TrackerService` broadcasting twice, `PeriodsRepository` receiving twice, `WorkoutRepository` receiving and calling `onWorkoutFinished`, and `_allWorkouts` reactive emissions triggering a 4th period reload.
4. *Preservation of Core Invariants*: Immediate period recalculation and UI propagation on workout completion (`REQ-PER-014`), first-workout appearance on clean installs, O(1) self-healing synchronization (`REQ-MIG-026`), and period card aggregation metrics remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete updating workout notes or exporting to Strava/FIT (modifying non-period fields in `WorkoutData`),
• *When* `WorkoutRepository.allWorkouts` emits the updated list,
• *Then* `PeriodsRepository`'s projected/distinct flow skips the emission and does NOT trigger `loadFromDatabase()`.
• *Given* an active workout tracking session finished by `TrackerService`,
• *When* `TRACKING_FINISHED_INTENT` is broadcast,
• *Then* `WorkoutRepository` processes the intent and invokes `PeriodsRepository.onWorkoutFinished(workout)` exactly once,
• *And* `PeriodsRepository` does NOT register redundant direct broadcast receivers, eliminating duplicate database transactions.
• *Given* `PeriodsRepository` performing migration or query mapping,
• *When* mapping cursor rows to domain entities,
• *Then* operations reuse a single shared `WorkoutDataMapper` instance without repeated allocations of database helpers.

**Invariants**: Immediate period finalization display (`REQ-PER-014`), calendar-aware aggregation boundaries (`REQ-PER-015`), O(1) self-healing sync (`REQ-MIG-026`), zero SQLite schema migrations, and 100% clean-room test pass rate.

### REQ-PER-017: True Line-Density Period Heatmap Visualization [Verified]
- **Tests**: `PeriodMapUtils.kt`, `PeriodSummaryCard.kt`, `PeriodMapVisualsTest.kt`
- **Implementation**: Replace point-cloud HeatmapTileProvider in PeriodMapUtils with PolylineHeatmapTileProvider and calibrate period-level opacity.
- **Rationale**: The system SHALL visualize period training density using true line-density rasterization without point densification overhead (ATT-4480, Parent Epic ATT-4474, Status: Verified, refining `REQ-PER-010`):
1. *Period Line-Density Integration (`PeriodMapUtils.kt`, `REQ-PER-017.1`)*:
• `PeriodMapVisuals` SHALL declare `val heatmapProvider: com.google.android.gms.maps.model.TileProvider?` instead of restricting to `com.google.maps.android.heatmaps.HeatmapTileProvider?`.
• `getPeriodMapVisuals` SHALL construct `PolylineHeatmapTileProvider` for Week, Month, and Year periods using all active period paths without calling `densifyPath()` or capping points via `maxPoints`.
2. *Calibrated Period Dynamic Range (`REQ-PER-017.2`)*:
• Intensity scaling and opacity SHALL be calibrated per `PeriodType` (Week: lighter base; Month: balanced; Year: high dynamic range) to preserve underlying road label legibility.
3. *Preservation of System Invariants (`REQ-PER-017.3`)*:
• Day periods (`PeriodType.DAY`) SHALL return `heatmapProvider = null`.
• Zero changes to `PeriodSummaries.db` schema or period statistics.
• 100% clean-room test pass rate under `./gradlew testDebugUnitTest`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-PER-010` (*Uniform Period Heatmap Visualization*), targeting `PeriodMapUtils.kt`, `PeriodMapVisuals`, and `PeriodSummaryCard.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.16 (ATT-440, ATT-504) and commit `06990c88` (ATT-4594).
3. *Root Reason for Existing Formulation*: `REQ-PER-010` relied on `createHeatmapProvider` with aggressive downsampling (`samplingStep`) and artificial point caps (`maxPoints = 8000..100000`) to avoid OOM crashes during 2D Gaussian convolution.
4. *Preservation of Core Invariants*: Visual distinction across PeriodType (Day/Week/Month/Year), non-blocking UI thread, and legibility of map labels and terrain remain 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a Month or Year period with multiple workout paths,
• *When* `getPeriodMapVisuals` is called,
• *Then* `heatmapProvider` returns an active `PolylineHeatmapTileProvider` instance without allocating `WeightedLatLng` point clouds.
• *Given* a Day period (`PeriodType.DAY`),
• *When* `getPeriodMapVisuals` is called,
• *Then* `heatmapProvider` returns `null`.

**Invariants**: Zero changes to `PeriodSummaries.db`, memory safety without OOMs, and 100% clean-room unit test pass rate.

### REQ-PER-018: Continuous Timeline PeriodBarGraph, Sub-Hour Value Formatting & Sport/Metric Modes [Verified]
- **Tests**: `PeriodBarGraph.kt`, `PeriodBarGraphTest.kt`, `PeriodsTabsScreen.kt`
- **Implementation**: Standalone PeriodBarGraph composable with timeline gap interpolation, sub-hour formatting, compact X-axis period labels, sport-stacked proportional bars, and metric mode toggle.
- **Rationale**: The system SHALL provide continuous timeline analytics, sub-hour duration legibility, and multi-metric sport visualization in the PeriodBarGraph header (ATT-4478, Parent Epic ATT-4474, Status: Verified, refining `REQ-PER-007`):
1. *Timeline Gap Interpolation & Rest Buckets (`REQ-PER-018.1`)*:
• When rendering `periods` in `PeriodBarGraph`, the system SHALL generate continuous chronological buckets including zero-volume rest periods between active workouts up to a bounded window.
• Zero-volume rest periods SHALL render as subtle baseline ticks (height 2 dp, disabled tint) preserving temporal spacing across Days, Weeks, and Months.
2. *Sub-Hour Duration & Value Formatting (`REQ-PER-018.2`)*:
• Durations `< 60s` SHALL format as `<1m`, `< 60m` SHALL format as `${m}m` (e.g., `45m`), `1h..10h` SHALL format with one decimal or integer hours (e.g., `1.5h` or `2h`), and `> 10h` SHALL format as `${h}h`.
• Each bar SHALL display a compact X-axis period identifier (e.g., day/month, `W42`, `Oct`, `2026`) beneath the bar.
3. *Sport-Stacked Proportions & Metric Switching (`REQ-PER-018.3`)*:
• Active bars SHALL support proportional vertical segments colored by `BSportType` (`BIKE`, `RUN`, `OTHER`) using canonical theme colors.
• The header SHALL provide a compact metric selector toggling between Duration, Distance, and Ascent, scaling bar heights dynamically to the maximum active value in the visible window.
4. *Bidirectional Scroll & Navigation Synchronization (`REQ-PER-018.4`)*:
• Clicking an active bar SHALL scroll the `PeriodList` to the corresponding `PeriodSummaryCard`.
• Scrolling `PeriodList` SHALL keep `PeriodBarGraph` centered/synchronized with the active item via `LaunchedEffect`. Zero-volume rest ticks SHALL be non-interactive.
5. *Preservation of System Invariants (`REQ-PER-018.5`)*:
• Zero alterations to `PeriodSummaries.db` schema or database queries.
• Strict non-crashing behavior on empty or single-item period lists.
• 9-language localization parity maintained for all metric and accessibility labels.
• Clean-room full test suite pass rate under `./gradlew testDebugUnitTest` SHALL remain 100%.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-PER-007` (*Reactive Period-Workout Association & Bar Graph*), targeting `PeriodsTabsScreen.kt` and `PeriodBarGraph.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.16 (ATT-375 scroll sync, ATT-440 layout spacing, ATT-488).
3. *Root Reason for Existing Formulation*: Originally designed as a minimal inline 70-line overview plotting raw non-zero periods directly from the database without timeline gap interpolation or metric toggles to minimize initial layout complexity.
4. *Preservation of Core Invariants*: `LaunchedEffect` scroll synchronization, selection highlighting, and click navigation to `PeriodSummaryCard` remain 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a list of periods with calendar gaps (e.g. rest days between Day 1 and Day 4),
• *When* `PeriodBarGraph` renders,
• *Then* continuous chronological buckets SHALL be generated with rest days rendered as baseline ticks preserving accurate temporal spacing.
• *Given* an active period with duration of 45 minutes,
• *When* value labels are drawn,
• *Then* the label SHALL display `45m` rather than being suppressed or truncated to 0.
• *Given* metric mode toggled to `Distance` or `Ascent`,
• *When* bars render,
• *Then* bar heights and values SHALL scale according to the selected metric's values across sports.

**Invariants**: Zero database schema migrations, non-blocking UI thread, crash prevention on empty lists, and 100% clean-room test pass rate.
 
### REQ-PER-019: Full-Period Asynchronous Mini-Map Preview & Background Simplification Pipeline [Verified]
- **Tests**: `TST-PER-025`
- **Implementation**: `PeriodSummaryCard.kt`, `PeriodCardMapState.kt`, `PeriodsRepository.kt`
- **Rationale**: The system SHALL provide complete route coverage and smooth 60 fps `LazyColumn` scrolling for Day, Week, Month, and Year summary cards by offloading polyline decoding, simplification, and GIS region clustering to background dispatchers (ATT-4477, Parent Epic ATT-4474, refining `REQ-PER-012`):
1. *Full-Period Route Coverage & Background Offload (`REQ-PER-019.1`)*:
• Period summary cards SHALL NOT truncate route previews to an 8-track compromise (`MAX_SUMMARY_THUMBNAIL_TRACKS = 8`). All available workout polylines for the period (`summary.workoutIdToPolylineMap` or `summary.polylines`) SHALL be processed into the preview.
• Polyline decoding (`PolyUtil.decode`), Douglas-Peucker simplification, region detection (`SpatialRegionEngine.detectRegionsFromPaths`), and `PolylineHeatmapTileProvider` creation SHALL execute exclusively on worker threads (`Dispatchers.Default`) via `produceState<PeriodCardMapState>`.
• The Android main thread SHALL perform zero synchronous polyline decoding or GIS clustering during card composition or `LazyColumn` scrolling.
2. *Douglas-Peucker Adaptive Decimation & Point Budgeting (`REQ-PER-019.2`)*:
• The background pipeline SHALL adaptively simplify member workout polylines using Douglas-Peucker decimation (`PolyUtil.simplify`) scaled by period type: `DAY` tolerance = 5.0m, `WEEK` tolerance = 15.0m, `MONTH` tolerance = 35.0m, and `YEAR` tolerance = 75.0m.
• The simplification pipeline MUST bound total aggregate vertices per card ($\le 2,000$ points) to prevent GPU memory pressure and eliminate UI jank.
3. *Background Region Clustering & Viewport Framing (`REQ-PER-019.3`)*:
• When a period spans multiple distinct geographic regions ($> 75\text{ km}$ geodesic span), `SpatialRegionEngine.detectRegionsFromPaths` SHALL partition the simplified paths on `Dispatchers.Default` and select the primary region for initial camera framing (`bounds`).
• The multi-region indicator badge (`REQ-PER-013`) SHALL display the count of distinct regions and primary region paths count when `regionCount > 1`.
4. *Complete Regional Route Network Heatmap Integration (`REQ-PER-019.4`)*:
• For `WEEK`, `MONTH`, and `YEAR` cards, `PeriodCardMapState` SHALL feed all simplified regional paths into `PolylineHeatmapTileProvider` (`REQ-PER-017`), rendering the full route density network. For `DAY` cards, `heatmapProvider` SHALL evaluate to `null` and render crisp vector polylines.
5. *System Invariants & Graceful Degradation (`REQ-PER-019.5`)*:
• When a period contains empty or invalid GPS data, the background pipeline SHALL emit a ready state with empty paths and null bounds, rendering the localized "No GPS data" fallback without throwing exceptions.
• Zero mutations to `PeriodSummaries.db` schema.
• 100% clean-room test pass rate under `./gradlew testDebugUnitTest`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-PER-012` (*Period Map Scalability, Memory Budget & Adaptive Layering Invariant*), targeting `PeriodSummaryCard.kt` and `PeriodsRepository.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-41.2 (commit `1c89f50e` / ATT-1151 / `MAX_SUMMARY_THUMBNAIL_TRACKS = 8`).
3. *Root Reason for Existing Formulation*: Imposed an 8-track hard cap as a temporary mitigation against UI-thread ANRs and OOM crashes when Google Maps `MapView` and `HeatmapTileProvider` decoded and rendered unsimplified vector polylines synchronously inside Compose `remember` blocks during list scrolling.
4. *Preservation of Core Invariants*: Memory safety without OOMs, multi-region indicator badge (`REQ-PER-013`), single-region camera framing, `DarkMapAntiFlashOverlay`, and non-blocking 60 fps `LazyColumn` scrolling remain 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a Month or Year period with 30+ workouts,
• *When* `PeriodSummaryCard` is composed in `LazyColumn`,
• *Then* all member workout polylines are decoded and Douglas-Peucker simplified on `Dispatchers.Default`, and all primary region paths are rendered in `PolylineHeatmapTileProvider` rather than truncating to 8 tracks.
• *Given* a period with empty or malformed GPS polylines,
• *When* `PeriodCardMapState` evaluates on `Dispatchers.Default`,
• *Then* `isReady` is set to true with empty `primaryPaths` and null `bounds`, displaying the no-GPS fallback without throwing exceptions.
• *Given* `LazyColumn` scrolling through `PeriodSummaryCard` items,
• *When* cards compose and recycle,
• *Then* zero `PolyUtil.decode`, `PolyUtil.simplify`, or `SpatialRegionEngine` clustering executions occur on the Android main thread.

**Invariants**: Zero database schema mutations, memory safety without OOMs, non-blocking UI thread, and 100% clean-room test pass rate.

### REQ-PER-020: Self-Contained Period Persistence, Zero-N+1 Startup Loading & Bidirectional Sync Healing [Verified]
- **Tests**: `TST-PER-026`
- **Implementation**: `PeriodSummariesDatabaseManager.kt`, `PeriodData.kt`, `PeriodsRepository.kt`, `DatabaseSchemaRegistry.kt`
- **Rationale**: The system SHALL eliminate N+1 startup queries in `getPeriodsByType()`, persist complete `LongestWorkout` metadata directly in `PeriodSummaries.db`, heal out-of-band workout deletions, and gate legacy file checks (ATT-4476, Parent Epic ATT-4474, refining `REQ-MIG-026` and `REQ-PER-011`):
1. *Zero-N+1 In-Memory Batch Linking in `getPeriodsByType()` (`REQ-PER-020.1`)*:
• In `PeriodSummariesDatabaseManager.getPeriodsByType()`, loading periods SHALL execute strictly 3 batch SQLite queries total: (1) `PeriodSummaries`, (2) `PeriodSportStats`, and (3) `PeriodDetailedStats`.
• Overwriting `stats.longestWorkout` with a synthetic `sId` pointer SHALL be eliminated. Detailed stats SHALL be indexed in memory by `sport_stats_id` (`Map<Long, MutableMap<String, DetailedStats>>`) and joined to their parent `SportStats` without secondary per-row `SELECT` queries.
2. *Self-Contained `LongestWorkout` Persistence in `PeriodSportStats` (`REQ-PER-020.2`)*:
• `SportStatsContract` and `PeriodSportStats` table schema SHALL include `COLUMN_LONGEST_WORKOUT_NAME` (`TEXT NOT NULL DEFAULT ''`), `COLUMN_LONGEST_DISTANCE` (`REAL NOT NULL DEFAULT 0.0`), and `COLUMN_LONGEST_ASCENT` (`INTEGER NOT NULL DEFAULT 0`).
• `PeriodSummariesDbHelper` SHALL increment `DB_VERSION` from 26 to 27 with 100% schema parity between `onCreate()` and `onUpgrade()`, registered in `DatabaseSchemaRegistry.kt` referencing the constant directly.
• `savePeriod()` and `getPeriodsByType()` SHALL write and read complete `LongestWorkout` metadata directly to and from SQLite, rendering `PeriodSummary` fully self-contained upon startup without requiring `WorkoutRepository.allWorkouts` in RAM or fallback queries against `WorkoutSummaries.db`.
3. *Bidirectional Deletion Healing in `checkIntegrityAndSync()` (`REQ-PER-020.3`)*:
• In `PeriodsRepository.checkIntegrityAndSync()`, the integrity condition SHALL evaluate `if (finishedWorkouts != periodWorkouts)`. When workouts are deleted out-of-band (`finishedWorkouts < periodWorkouts`), the repository SHALL trigger resynchronization to purge orphaned periods.
4. *Gated Legacy Experimental Database Cleanup (`REQ-PER-020.4`)*:
• The 66 legacy file existence checks (`PeriodSummaries_v4.db` through `v25.db` plus `-shm`/`-wal`) in `PeriodSummariesDatabaseManager.init` SHALL execute strictly once per installation, guarded by a persistent `SharedPreferences` boolean flag (`legacy_period_dbs_cleaned`), eliminating redundant cold-start file I/O.
5. *Preservation of System Invariants & Migration Parity (`REQ-PER-020.5`)*:
• SQLite migration from v26 to v27 MUST preserve all existing period and sport statistics without data loss.
• Automated migration contract test MUST prove schema identity between fresh `onCreate` and upgraded `onUpgrade` instances.
• Clean-room full test suite pass rate under `./gradlew testDebugUnitTest` SHALL remain 100%.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-MIG-026` (*O(1) Self-Healing Period Synchronization*) and `REQ-PER-011` (*Longest Workout Metric Display*). Targets `PeriodSummariesDatabaseManager.kt`, `PeriodData.kt`, `PeriodsRepository.kt`, `DatabaseSchemaRegistry.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.16 (ATT-346 / commit `7a192b0c` for `tempId` linkage) and Sprint 2026-41.2 (ATT-909 for `>` integrity check).
3. *Root Reason for Existing Formulation*: `tempId` pointer was introduced as an expedient temporary hack when relational detailed stats were first split from sport stats; `>` was assumed because workouts were recorded incrementally and deletion was rare; incomplete sport stats schema avoided an earlier DB version bump.
4. *Preservation of Core Invariants*: Normalized relational structure, sort key indexing (`REQ-MIG-026`), zero data loss, non-blocking startup performance, and 100% clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete with hundreds of periods and sports in `PeriodSummaries.db`,
• *When* `PeriodSummariesDatabaseManager.getPeriodsByType()` executes,
• *Then* strictly 3 batch SQLite queries are executed, and zero per-row secondary queries are performed.
• *Given* an existing v26 database upgraded to v27,
• *When* `onUpgrade()` executes,
• *Then* columns `longest_workout_name`, `longest_distance`, and `longest_ascent` are added with non-null defaults, existing data is preserved, and the schema matches fresh `onCreate()`.
• *Given* out-of-band deletion of workouts resulting in `finishedWorkouts < periodWorkouts`,
• *When* `checkIntegrityAndSync()` runs,
• *Then* resynchronization is triggered and orphaned periods are pruned.
• *Given* subsequent application cold starts after initial cleanup,
• *When* `PeriodSummariesDatabaseManager.init` runs,
• *Then* legacy experimental database file checks are skipped.

**Invariants**: Zero data loss across migration; $O(1)$ query budget ($\le 3$ batch queries); bidirectional sync healing; 100% clean-room test pass rate.

### REQ-PER-021: InteractivePeriodMap Double-Rendering Elimination, UI-Thread Decoupling & Large-Period Spatial Hit-Testing [Verified]
- **Tests**: `TST-PER-027`
- **Implementation**: `InteractivePeriodMap.kt`, `PeriodMapUtils.kt`, `PeriodsRepository.kt`, `PeriodsViewModel.kt`
- **Rationale**: The system SHALL eliminate polyline double-rendering, offload all polyline decoding from the Android main thread, prevent OOM bypasses, and preserve workout route clickability on large periods (> 30 workouts) (ATT-4475, Parent Epic ATT-4474, refining `REQ-PER-012` and `REQ-PER-013`):
1. *Spatial Anchor Scope Isolation in `groupedPeriods` (`REQ-PER-021.1`)*:
• In `PeriodsRepository.enrich()`, `PeriodSummary.workoutIdToPolylineMap` SHALL be strictly scoped to spatial anchor workouts ($\le 5$ workouts: North, South, East, West, Longest).
• Full member polylines for interactive exploration SHALL NOT be replicated 4x across Day, Week, Month, and Year lists in `groupedPeriods`; they SHALL be loaded on-demand in `PeriodsViewModel.showPeriodMap()` into `PeriodMapState`.
2. *Double-Rendering Elimination in `InteractivePeriodMap` (`REQ-PER-021.2`)*:
• In `InteractivePeriodMap.kt`, `anchorTracks` SHALL NOT render simultaneously with `memberTracks`. When `mapState.isLoading == false`, `anchorTracks` SHALL be hidden or deduplicated, preventing identical paths from being rendered twice at differing alpha values ($\alpha=0.5$ and $\alpha=0.3$).
3. *Asynchronous Background Polyline Decoding (`REQ-PER-021.3`)*:
• All `PolyUtil.decode` calls for period map tracks SHALL execute exclusively on worker threads (`Dispatchers.Default`) in `PeriodsViewModel` or repository loaders.
• Synchronous `PolyUtil.decode` execution inside Compose `remember` on the Android UI thread in `InteractivePeriodMap.kt` SHALL be eliminated.
4. *Large-Period Route Network & Spatial Tap Hit-Testing (`REQ-PER-021.4`)*:
• For large periods exceeding `MAX_PERIOD_VECTOR_TRACKS = 30`, routes SHALL be rendered via `PolylineHeatmapTileProvider` (refining `REQ-PER-012` hard cliff) to eliminate Google Maps `Polyline` object allocation overhead.
• Workout track clickability and peeking SHALL be supported on large periods via spatial tap hit-testing (`onMapClick(latLng)` invoking `PolyUtil.isLocationOnPath(latLng, path, true, toleranceMeters)`), dynamically selecting and highlighting the clicked workout route without instantiating hundreds of vector polylines.
5. *Preservation of System Invariants (`REQ-PER-021.5`)*:
• Zero alterations to `PeriodSummaries.db` schema.
• Instant geographic framing via bounds and anchor markers remains strictly preserved.
• 100% clean-room unit test pass rate under `./gradlew testDebugUnitTest`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-PER-012` (*Vector Track Safety Threshold*) and `REQ-PER-013` (*Period Map Performance*). Targets `InteractivePeriodMap.kt`, `PeriodMapUtils.kt`, `PeriodsRepository.kt`, `PeriodsViewModel.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.16 (ATT-346 / commit `7a192b0c` for anchor framing; ATT-440 for `PeriodMapState`).
3. *Root Reason for Existing Formulation*: Anchors were designed for instant framing before full data loaded. When `allPolylineMap` was added to feed heatmaps, `workoutIdToPolylineMap` was overloaded without isolating anchor IDs, causing small periods to double-render and large periods to decode 300+ polylines on the UI thread while dropping vector clickability.
4. *Preservation of Core Invariants*: Instant camera framing, memory safety (zero OOMs), 60 fps gesture smoothness, and 100% clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a period with $\le 30$ workouts,
• *When* `InteractivePeriodMap` completes loading (`mapState.isLoading == false`),
• *Then* member workout paths are rendered exactly once without duplicate underlying anchor vector tracks.
• *Given* a Month or Year period with $> 30$ workouts,
• *When* `InteractivePeriodMap` renders,
• *Then* the Android UI thread executes zero synchronous `PolyUtil.decode` calls inside Compose `remember`,
• *And* tapping a route on the map triggers spatial hit-testing (`PolyUtil.isLocationOnPath`) and peeks the selected workout.
• *Given* `groupedPeriods` in `PeriodsRepository`,
• *When* period summaries are enriched,
• *Then* `summary.workoutIdToPolylineMap` contains at most 5 spatial anchor polylines rather than the full period workout dataset.

**Invariants**: Zero changes to `PeriodSummaries.db`, memory safety without OOMs, and 100% clean-room test pass rate.



