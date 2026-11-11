# Living Requirements: Post-Workout Analytics & Periods

**Domain**: `aftermath_periods`  
**Prefixes**: `PER`, `PERF`  
**Scope**: Post-workout analytics, period statistics, zone distributions, graphs and visual scrubbers.  
**Total Requirements**: 33

---

### REQ-PER-007: Reactive Period-Workout Association [Verified]
- **Tests**: `TST-PERF-008`
- **Implementation**: `PeriodsViewModel.kt`, `PeriodsRepository.kt`
- **Rationale**: The system SHALL ensure that all workouts belonging to a specific period are correctly and reactively associated when viewing period details (Map or List). The visualization SHALL automatically update as workout data is loaded or modified in the background, ensuring no sessions are omitted due to partial loading states.

### REQ-PER-008: Precise Period Filtering [Verified]
- **Tests**: `TST-PER-009`
- **Implementation**: `PeriodsFragment.kt`, `WorkoutSummariesViewModel.kt`
- **Rationale**: The system SHALL correctly filter the workout list when navigating from a period summary highlight (Base Sport or Sub-Sport). This filtering MUST respect both the temporal bounds of the period and the specific sport identity selected to ensure professional analytical depth.

### REQ-PER-009: Detail Map Sport Filtering [Verified]
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

### REQ-PER-012: Period Map Scalability, Memory Budget & Adaptive Layering Invariant [Verified]
- **Tests**: `PeriodsViewModel.kt`, `PeriodMapScreen.kt`, `InteractivePeriodMap.kt`, `AndroidManifest.xml`
- **Implementation**: Ensure smooth, non-crashing (OOM-free) spatial rendering of long-term training periods with hundreds of sessions.
- **Rationale**: The system SHALL ensure high-performance, non-crashing spatial visualization across periods of arbitrary size (days, weeks, months, years with hundreds of workouts):
1. *Adaptive Layering Threshold*: When a period contains more than a configured safety threshold of member workouts (`MAX_VECTOR_WORKOUTS = 30`), the system SHALL NOT instantiate or render individual vector `Polyline`s (`memberTracks`) and individual member markers (`memberMarkers`) for all member workouts simultaneously. Instead, the spatial analytics SHALL render the high-performance raster `Heatmap` layer representing aggregate route density plus the prominent `anchorTracks` (the spatial boundary/longest routes) and period extrema markers (`extremaMarkers`).
2. *Interactive Single-Track Inspection*: When the athlete taps or peeks a specific workout within a large period, that single workout's path SHALL be rendered as a crisp, interactive vector polyline via `peekedTracks` without rendering all other background sessions as vector paths.
3. *Threshold-Filtered Detail*: When the number of displayed workouts is within the safety threshold ($\le 30$, such as in small periods or when filtered by sport type), individual member tracks and technical markers SHALL be rendered as detailed vector overlays.
4. *Memory & Point Budgeting*: In `PeriodsViewModel`, polyline decoding and `MapTrack` instantiation SHALL be budget-aware, avoiding redundant duplicate representations in memory.
5. *Process Heap Headroom*: The application manifest SHALL declare `android:largeHeap="true"` to grant the runtime sufficient memory bandwidth for GIS map overlays and raster heatmaps on high-resolution devices.

### REQ-PER-013: Multi-Region Period Map Viewport Detection & Adaptive Bounding [Verified]
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

### REQ-PER-001: The system SHALL load and display the detailed visualization (Heatmap and List) [Verified]
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

### REQ-PER-014: Immediate Period View Reactive Synchronization on Workout Finalization [Verified]
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

### REQ-PER-015: Calendar-Aware Period Boundaries Across DST Transitions, Sentinel Epoch Symmetry, and Local Day PMC Bucketing [Verified]
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

### REQ-PER-016: Period Rebuild Flow Projection, Single-Owner Tracking Finalization Orchestration & Shared Mapper Instance [Verified]
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
- **Tests**: `TST-PER-025`, `TST-PER-034`
- **Implementation**: `PeriodSummaryCard.kt`, `PeriodCardMapState.kt`, `PeriodsRepository.kt`
- **Rationale**: The system SHALL provide complete route coverage and smooth 60 fps `LazyColumn` scrolling for Day, Week, Month, and Year summary cards by offloading polyline decoding, simplification, and GIS region clustering to background dispatchers (ATT-4477, Parent Epic ATT-4474, refining `REQ-PER-012`):
1. *Full-Period Route Coverage & Background Offload (`REQ-PER-019.1`)*:
• Period summary cards SHALL NOT truncate route previews to an 8-track compromise (`MAX_SUMMARY_THUMBNAIL_TRACKS = 8`). All available workout polylines for the period (`summary.workoutIdToPolylineMap` or `summary.polylines`) SHALL be processed into the preview.
• Polyline decoding (`PolyUtil.decode`), Douglas-Peucker simplification, region detection (`SpatialRegionEngine.detectRegionsFromPaths`), and `PolylineHeatmapTileProvider` creation SHALL execute exclusively on worker threads (`Dispatchers.Default`) via `produceState<PeriodCardMapState>`.
• The Android main thread SHALL perform zero synchronous polyline decoding or GIS clustering during card composition or `LazyColumn` scrolling.
2. *Douglas-Peucker Adaptive Decimation & Dual Vertex Budget Enforcement (`REQ-PER-019.2`)*:
• The background simplification pipeline SHALL adaptively decimate member workout polylines using Douglas-Peucker simplification (`PolyUtil.simplify`) starting from base tolerances calibrated by period type: `DAY` (5.0m), `WEEK` (5.0m), `MONTH` (6.0m), and `YEAR` (8.0m) (REQ-PER-024.3).
• The pipeline MUST enforce hard aggregate vertex budgets across member paths:
  - For period summary card mini-map previews (`PeriodSummaryCard` / `PeriodCardMapState`), total aggregate vertices SHALL NOT exceed `MAX_CARD_MINI_MAP_VERTICES = 2_000` points.
  - For detailed interactive maps (`PeriodMapScreen` / `InteractivePeriodMap` / `PeriodsViewModel`), total aggregate vertices SHALL NOT exceed `MAX_DETAILED_MAP_VERTICES = 8_000` points.
• When total vertices at base tolerance exceed the respective budget, the system SHALL adaptively escalate decimation tolerance to strictly bound aggregate points while preserving shared-corridor alignment and road network topology.
• Synthetic high-volume periods (e.g. 300 workouts in a Year period) MUST stay strictly within the vertex budget without memory exhaustion or UI scrolling jank.
3. *Background Region Clustering & Viewport Framing (`REQ-PER-019.3`)*:
• When a period spans multiple distinct geographic regions ($> 75\text{ km}$ geodesic span), `SpatialRegionEngine.detectRegionsFromPaths` SHALL partition the simplified paths on `Dispatchers.Default` and select the primary region for initial camera framing (`bounds`).
• The multi-region indicator badge (`REQ-PER-013`) SHALL display the count of distinct regions and primary region paths count when `regionCount > 1`.
4. *Complete Regional Route Network Heatmap Integration & Adaptive Vector Polyline Suppression (`REQ-PER-019.4`)*:
• For `WEEK`, `MONTH`, and `YEAR` cards, `PeriodCardMapState` SHALL feed all simplified regional paths into `PolylineHeatmapTileProvider` (`REQ-PER-017`), rendering the full route density network. For `DAY` cards, `heatmapProvider` SHALL evaluate to `null` and render crisp vector polylines.
• In `PeriodSummaryCard` (`PeriodMultiWorkoutMap`), when a period has $> 30$ workouts (`activePaths.size > MAX_PERIOD_VECTOR_TRACKS`) and an active heatmap (`visuals.heatmapProvider != null`), individual vector `Polyline` composables SHALL be suppressed in favor of the raster heatmap overlay to prevent JNI allocation overhead and visual gradient washout (ATT-4772).
• For periods with $\le 30$ workouts or where `heatmapProvider == null`, individual vector `Polyline` composables SHALL be retained.
• `PeriodCardMapState` SHALL expose `val renderVectorPolylines: Boolean = true`, evaluated as `visuals?.heatmapProvider == null || activePaths.size <= MAX_PERIOD_VECTOR_TRACKS`.
• `prepareCardMapPreviewSync` SHALL prioritize `summary.polylines` over `summary.workoutIdToPolylineMap` when populating preview paths.
5. *System Invariants & Graceful Degradation (`REQ-PER-019.5`)*:
• When a period contains empty or invalid GPS data, the background pipeline SHALL emit a ready state with empty paths and null bounds, rendering the localized "No GPS data" fallback without throwing exceptions.
• Zero mutations to `PeriodSummaries.db` schema.
• 100% clean-room test pass rate under `./gradlew testDebugUnitTest`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-PER-012` (*Period Map Scalability, Memory Budget & Adaptive Layering Invariant*) and `REQ-PER-019.4` (*Regional Route Network Heatmap Integration*). Targets `PeriodSummaryCard.kt`, `PeriodMapUtils.kt`, and `PeriodCardMapStateTest.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-41.2 (commit `1c89f50e` / ATT-1151 / `MAX_SUMMARY_THUMBNAIL_TRACKS = 8`), commit `7e2151ec` (ATT-1107, established `MAX_PERIOD_VECTOR_TRACKS = 30`), commit `da70647f` (ATT-4477, added `PeriodCardMapState`), and `ATT-4772`.
3. *Root Reason for Existing Formulation*: ATT-4477 enabled full-period route ingestion and offloaded polyline decoding to background dispatchers, but `PeriodMultiWorkoutMap` retained unconditional `Polyline` composable drawing underneath `TileOverlay`, causing hundreds of redundant JNI objects on large periods and washing out the translucent heatmap.
4. *Preservation of Core Invariants*: Memory safety without OOMs, multi-region indicator badge (`REQ-PER-013`), single-region camera framing, `DarkMapAntiFlashOverlay`, `DAY` card vector polyline crispness, and non-blocking 60 fps `LazyColumn` scrolling remain 100% strictly preserved.
5. *ATT-4930 Refinement (REQ-PER-019.2 Dual Vertex Budget)*:
• Original Requirement ID & Target: `REQ-PER-019.2` (*Douglas-Peucker Adaptive Decimation & Point Budgeting*). Targets `PeriodMapUtils.kt`, `PeriodData.kt`, `PeriodSummaryCard.kt`, `PeriodsViewModel.kt`.
• Historical Origin & Commit Trace: Commit `da70647f` (`ATT-4477`, established <= 2,000 pts bound with 15/35/75m tolerances), commit `2bea16b7` (`ATT-4833`, lowered tolerances to 5-8m for corridor alignment, removing the point budget guarantee), and `ATT-4930`.
• Root Reason for Existing Formulation: ATT-4833 lowered tolerances to 5-8m to ensure repeated passes align on identical pixels for additive heatmap stacking, but omitted an aggregate vertex ceiling, leading to memory pressure on Year cards with 200-500 workouts.
• Preservation of Core Invariants: Shared-corridor alignment on typical workloads, 60 fps LazyColumn scrolling, thread safety on Dispatchers.Default, and memory safety without OOMs remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a Month or Year period with 30+ workouts,
• *When* `PeriodSummaryCard` is composed in `LazyColumn`,
• *Then* all member workout polylines are decoded and Douglas-Peucker simplified on `Dispatchers.Default`, and all primary region paths are rendered in `PolylineHeatmapTileProvider` rather than truncating to 8 tracks.
• *Given* a Month or Year period with $> 30$ workouts and an active heatmap,
• *When* `PeriodSummaryCard` (`PeriodMultiWorkoutMap`) renders,
• *Then* individual vector `Polyline` composables are suppressed (`renderVectorPolylines == false`) and only the `TileOverlay` heatmap is rendered.
• *Given* a period with $\le 30$ workouts or a Day period (`heatmapProvider == null`),
• *When* `PeriodSummaryCard` renders,
• *Then* individual vector `Polyline` composables are retained (`renderVectorPolylines == true`).
• *Given* a period with empty or malformed GPS polylines,
• *When* `PeriodCardMapState` evaluates on `Dispatchers.Default`,
• *Then* `isReady` is set to true with empty `primaryPaths` and null `bounds`, displaying the no-GPS fallback without throwing exceptions.
• *Given* `LazyColumn` scrolling through `PeriodSummaryCard` items,
• *When* cards compose and recycle,
• *Then* zero `PolyUtil.decode`, `PolyUtil.simplify`, or `SpatialRegionEngine` clustering executions occur on the Android main thread.
• *Given* a high-volume period (e.g. 300 workouts in a Year period),
• *When* `prepareCardMapPreviewSync()` computes `PeriodCardMapState`,
• *Then* the aggregate vertex count across all decoded and simplified paths is $\le 2,000$ points (`MAX_CARD_MINI_MAP_VERTICES`).
• *Given* a high-volume period loaded in `PeriodsViewModel` / `InteractivePeriodMap`,
• *When* heatmap paths are prepared,
• *Then* the aggregate vertex count across all paths is $\le 8,000$ points (`MAX_DETAILED_MAP_VERTICES`).

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

### REQ-PER-022: Periods Sport Type Header Filtering & Multi-Sport Projections [Verified]
- **Tests**: `TST-PER-028` | **Tickets**: `ATT-4770`, `ATT-5828`
- **Implementation**: `PeriodsTabsScreen.kt`, `PeriodsViewModel.kt`, `PeriodData.kt`, `PeriodsRepository.kt`
- **Rationale**: The system SHALL provide toggleable sport filter icon buttons in the Periods header row to allow athletes to isolate specific disciplines (`Bike`, `Run`, `Other`) across the entire Periods view (Day, Week, Month, Year), dynamically filtering `PeriodBarGraph`, `PeriodSummaryCard`, and map previews while preserving non-empty filter invariants and database integrity (ATT-4770, refining `REQ-PER-018` and `REQ-PER-019`):
1. *Canonical Sport Type Icon Buttons & Placement (`REQ-PER-022.1`)*:
• In `PeriodsTabsScreen.kt`, the `primaryContainer` header row SHALL display 3 compact toggleable sport icon buttons on the right side opposite the "Periods" title.
• The buttons SHALL be rendered in canonical order `[BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN]` (Rule 57: `Other` always on the far right).
• Icons SHALL use `bSportType.iconResId` (`ic_bike`, `ic_run`, `ic_other` via `BSportType`) with content descriptions from `bSportType.stringResId` (`R.string.sport_type_bike`, `R.string.sport_type_run`, `R.string.sport_type_other`).
• Active/selected sport icons SHALL be rendered with full opacity (`MaterialTheme.colorScheme.onPrimaryContainer`); inactive/deselected icons SHALL be rendered dimmed (`MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = TTAlpha.Disabled)`).
2. *Default Filter Set & Non-Empty Selection Guard (`REQ-PER-022.2`)*:
• In `PeriodsViewModel`, `selectedSports` StateFlow SHALL be initialized by default to `setOf(BSportType.BIKE, BSportType.RUN)`.
• Toggling a sport filter via `toggleSportFilter(sport: BSportType)` SHALL prevent deselecting the final remaining active sport, guaranteeing `selectedSports.isNotEmpty()` at all times to prevent blank/unusable UI states.
3. *Reactive Multi-Sport In-Memory Projection (`REQ-PER-022.3`)*:
• `PeriodSummary` in `PeriodData.kt` SHALL include `val workoutIdToAllPolylinesMap: Map<Long, String> = emptyMap()`, populated during `enrich()` in `PeriodsRepository.kt` from `allPolylineMap`.
• `PeriodsViewModel` SHALL project `groupedPeriods` reactively into `filteredGroupedPeriods: StateFlow<Map<PeriodType, List<PeriodSummary>>>` based on `selectedSports`.
• For each `PeriodSummary`:
  - `sportStats` SHALL retain only entries matching `selectedSports`.
  - Aggregated metrics (`totalWorkouts`, `totalDurationSec`, `totalDistance`) SHALL be recomputed strictly from the filtered `sportStats` values.
  - Periods with 0 filtered workouts (`totalWorkouts == 0`) SHALL be excluded from the filtered list in `filteredGroupedPeriods`.
  - Map preview paths (`polylines` and `workoutIdToPolylineMap`) SHALL be filtered using `summary.workoutIdToAllPolylinesMap.filterKeys { id -> summary.workoutIdToSportMap[id] in selectedSports }` (or fallback to retaining `summary.polylines` if `workoutIdToAllPolylinesMap` is empty).
  - Spatial `extremaMarkers` SHALL retain only markers whose `workoutId` matches a workout of a selected sport type (`summary.workoutIdToSportMap[marker.workoutId] in selectedSports`).
4. *Bar Graph, Summary Card, and Map Dynamic Synchronization (`REQ-PER-022.4`)*:
• `PeriodsTabsScreen.kt` and its child composables (`PeriodBarGraph`, `PeriodList`, `PeriodSummaryCard`) SHALL observe and render `filteredGroupedPeriods`.
• Bar heights, sport segment colors, summary cards, and map preview routes SHALL instantaneously update when sport filters are toggled without requiring database re-queries or screen re-navigation.
5. *Preservation of System Invariants (`REQ-PER-022.5`)*:
• Zero alterations or migrations to `PeriodSummaries.db` (schema remains on v27).
• 60 fps `LazyColumn` scrolling smoothness and Douglas-Peucker simplification pipelines (`REQ-PER-019`) remain strictly preserved.
• 9-language localization parity maintained for all sport strings.
• 100% clean-room test suite pass rate under `./gradlew testDebugUnitTest`; all `PeriodsViewModel` instances in `PeriodsViewModelTest.kt` SHALL inject `testDispatcher` for both `ioDispatcher` and `defaultDispatcher` to eliminate background coroutine leakage and prevent `UncaughtExceptionsBeforeTest` in subsequent tests (ATT-5828).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-PER-022.5` (*Preservation of System Invariants*), amending `REQ-PER-022` (*Periods Sport Type Header Filtering & Multi-Sport Projections*). Targets `PeriodsViewModelTest.kt`, `PeriodsViewModel.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-43.9 (`ATT-4478`), Sprint 2026-43.2 (Rule 57, commit `d5a81e3a` / `ATT-4372`), `ATT-4770`, and `ATT-5828`.
3. *Root Reason for Existing Formulation*: Early unit tests in `PeriodsViewModelTest.kt` omitted explicit dispatcher arguments and defaulted to `Dispatchers.IO` and `Dispatchers.Default`. Lingering background coroutines under JVM runner contention caused sporadic `UncaughtExceptionsBeforeTest` errors in subsequent test runs.
4. *Preservation of Core Invariants*: All existing test assertions, sport filter logic, and 100% clean-room pass rate under `./gradlew testDebugUnitTest` remain strictly preserved without production code changes.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete opens `PeriodsTabsScreen`,
• *When* the top header row renders,
• *Then* 3 sport filter icon buttons `[Bike, Run, Other]` are displayed on the right side of the `primaryContainer` row in canonical order (Other on far right), with Bike and Run active (full opacity) and Other dimmed.
• *Given* `selectedSports` contains `setOf(BSportType.BIKE, BSportType.RUN)`,
• *When* the athlete taps the Run icon button,
• *Then* `selectedSports` becomes `setOf(BSportType.BIKE)`, Run icon is dimmed, and all period cards, bar graphs, and map previews update to show Bike-only metrics.
• *Given* `selectedSports` contains only `setOf(BSportType.BIKE)`,
• *When* the athlete taps the Bike icon button,
• *Then* the tap is ignored (non-empty guard), preserving `selectedSports = setOf(BSportType.BIKE)`.
• *Given* a period containing only "Other" workouts,
• *When* default filters (`Bike + Run`) are active,
• *Then* that period has 0 filtered workouts and is omitted from `filteredGroupedPeriods`.
• *When* the athlete toggles "Other" on,
• *Then* the period dynamically appears in the list and bar graph.

**Invariants**: Zero SQLite schema changes, non-blocking UI thread, crash prevention on empty/edge data, and 100% clean-room test pass rate.

### REQ-PER-023: PeriodBarGraph Full Distance & Imperial Unit Parity [Verified]
- **Tests**: `TST-PER-029`
- **Implementation**: `PeriodBarGraph.kt`
- **Rationale**: The system SHALL display standard SI distance units (`km`, `m`) and imperial distance/ascent units (`mi`, `ft`) in top value labels of `PeriodBarGraph` according to the athlete's configured unit system (`MyUnits`), replacing non-standard `"k"` shorthand while expanding bar and spacing geometry to prevent visual label collision (ATT-4771, refining `REQ-PER-018.2`):
1. *Standard Metric Distance Formatting (`REQ-PER-023.1`)*:
• When `unit == MyUnits.METRIC` (or default):
  - Values $< 1000\text{ m}$ SHALL format as `"${m}m"` (e.g., `500m`).
  - Values $< 10\text{ km}$ ($1000\text{ m} \le \text{value} < 10000\text{ m}$) SHALL format with 1 decimal place as `String.format(Locale.US, "%.1fkm", km)` (e.g., `8.5km`).
  - Values $\ge 10\text{ km}$ ($\ge 10000\text{ m}$) SHALL format as `"${km.roundToInt()}km"` (e.g., `42km`, `125km`).
2. *Imperial Distance Formatting (`REQ-PER-023.2`)*:
• When `unit == MyUnits.IMPERIAL`:
  - Values $< 0.1\text{ mi}$ ($< 160.9344\text{ m}$) SHALL format as `"<0.1mi"`.
  - Values $< 10\text{ mi}$ ($0.1\text{ mi} \le \text{mi} < 10\text{ mi}$) SHALL format with 1 decimal place as `String.format(Locale.US, "%.1fmi", mi)` (e.g., `5.3mi`).
  - Values $\ge 10\text{ mi}$ SHALL format as `"${mi.roundToInt()}mi"` (e.g., `26mi`, `100mi`).
  - Miles conversion SHALL use `BANALService.METER_PER_MILE = 1609.344`.
3. *Ascent Unit Parity (`REQ-PER-023.3`)*:
• In `PeriodBarMetric.ASCENT` mode:
  - Metric SHALL format as `"${m}m"`.
  - Imperial SHALL format as `"${ft}ft"`, converting meters using `BANALService.METER_PER_FOOT = 0.3048`.
4. *Geometry Adjustments for Non-Overlap Invariant (`REQ-PER-023.4`)*:
• `barWidth` for `PeriodType.DAY` SHALL expand from `18.dp` to `24.dp`.
• `barWidth` for `PeriodType.WEEK` SHALL expand from `28.dp` to `32.dp`.
• `spacing` between bars in `LazyRow` SHALL increase from `4.dp` to `6.dp` (yielding $\ge 30\text{ dp}$ horizontal pitch for Day bars).
• Labels SHALL remain centered above each bar, strictly preventing visual collision between adjacent bars.
5. *Preservation of System Invariants (`REQ-PER-023.5`)*:
• Duration formatting (`<1m`, `${m}m`, `${h}h`) in `REQ-PER-018.2` remains untouched.
• Zero changes to `PeriodSummaries.db`.
• 100% clean-room unit test pass rate under `./gradlew testDebugUnitTest`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-PER-023` (*PeriodBarGraph Full Distance & Imperial Unit Parity*), refining `REQ-PER-018.2` (*Sub-Hour Duration & Value Formatting*). Targets `PeriodBarGraph.kt` and `PeriodBarGraphTest.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-43.9 Review (`ATT-4478`), where initial `PeriodBarGraph` was introduced with tight `18.dp` Day bars and shorthand `"k"` suffixes.
3. *Root Reason for Existing Formulation*: `"k"` was used as a single-character shorthand to avoid label truncation on narrow `18.dp` Day bars, while Imperial unit conversion was omitted in the initial MVP commit.
4. *Preservation of Core Invariants*: Expanding Day bars to `24.dp` and pitch to `30.dp` provides ample horizontal clearance for `"km"` and `"mi"` labels without collision, while maintaining unit system parity (Rule 36) and zero SQLite schema alterations.

**Acceptance Criteria (Given-When-Then)**:
• *Given* metric unit system (`MyUnits.METRIC`),
• *When* formatting distance values in `PeriodBarGraph`,
• *Then* values $< 1000\text{m}$ render with `"m"`, $< 10\text{km}$ render with 1 decimal and `"km"`, and $\ge 10\text{km}$ render as integer `"km"`.
• *Given* imperial unit system (`MyUnits.IMPERIAL`),
• *When* formatting distance values in `PeriodBarGraph`,
• *Then* values $< 0.1\text{mi}$ render as `"<0.1mi"`, $< 10\text{mi}$ render with 1 decimal and `"mi"`, and $\ge 10\text{mi}$ render as integer `"mi"`.
• *Given* imperial unit system (`MyUnits.IMPERIAL`),
• *When* formatting ascent values in `PeriodBarGraph`,
• *Then* elevation renders as integer feet with `"ft"`.
• *Given* Day period bar graph,
• *When* rendering consecutive active days with `"42km"` or `"26.2mi"`,
• *Then* adjacent labels do not overlap or truncate.

**Invariants**: Zero changes to `PeriodSummaries.db`, memory safety, Rule 36 unit parity, and 100% clean-room test pass rate.


### REQ-PER-024: Route-Frequency Heatmap Unification, Tolerance Calibration & Region Propagation [Verified]
- **Tests**: `TST-PER-030`, `TST-PER-032` | **Tickets**: `ATT-4833`, `ATT-4935`
- **Implementation**: `ThermalColorLut.kt`, `PolylineHeatmapTileProvider.kt`, `PeriodMapUtils.kt`, `PeriodSummaryCard.kt`, `PeriodMapScreen.kt`, `InteractivePeriodMap.kt`, `PeriodsViewModel.kt`
- **Rationale**: The system SHALL unify the route-frequency heatmap as the primary path visualization across both the preview card (`PeriodSummaryCard`) and detailed map view (`PeriodMapScreen` / `InteractivePeriodMap`), SHALL eliminate polyline masking and marker clutter in period overviews, SHALL tighten polyline decimation tolerance to preserve shared-corridor additive stacking, SHALL calibrate adaptive frequency normalization and thermal contrast, SHALL propagate the active region across views, and SHALL slave heatmap path rendering and spatial region clustering to the master sport filter (ATT-4833, refined by ATT-4935, refining `REQ-PER-019` and `REQ-PER-021`):
1. *Heatmap-Primary Visualization & Vector Track Suppression (`REQ-PER-024.1`)*:
• In `PeriodSummaryCard`, when `visuals.heatmapProvider != null`, the card SHALL render the line-density heatmap tile layer without underlying vector polylines. Vector polylines SHALL only render when heatmap tile provider is null (e.g. Day periods).
• In `InteractivePeriodMap` and `PeriodMapScreen`, when no individual workout is selected/peeked (`peekedWorkoutDataWithTrack == null`), the system SHALL suppress rendering member vector polylines, anchor polylines, and start/end/apex markers, rendering exclusively the line-density heatmap.
• When a specific workout is selected/peeked, the system SHALL render the selected workout's vector track with prominent high-alpha highlight and render its start, stop, and extremum markers.
2. *Region State Propagation (`REQ-PER-024.2`)*:
• When tapping a period summary card mini-map, the active card region ID (`activeRegionId`) SHALL be propagated via `onMapClick(summary, activeRegionId)` into `PeriodsViewModel.showPeriodMap(summary, initialRegionId)`.
• `PeriodMapScreen` / `InteractivePeriodMap` SHALL initialize its camera bounds and selected region matching the card's active geographic region 1:1.
3. *Corridor Overlap Tolerance Calibration (`REQ-PER-024.3`)*:
• In `PeriodMapUtils.getPeriodSimplificationToleranceMeters`, Douglas-Peucker simplification tolerance SHALL be bounded to $\le 5\text{--}8\text{m}$ across all period types: `PeriodType.DAY -> 5.0m`, `PeriodType.WEEK -> 5.0m`, `PeriodType.MONTH -> 6.0m`, `PeriodType.YEAR -> 8.0m` (replacing historical 15m/35m/75m), ensuring repeated activities along shared road corridors map to identical pixels for additive rasterization.
4. *Adaptive Corridor Frequency Normalization & Thermal Contrast (`REQ-PER-024.4`)*:
• In `PolylineHeatmapTileProvider`, intensity per pass SHALL adapt to the period workout count: target passes to full saturation ($1.0$) SHALL scale from $2$ passes (for $\le 3$ workouts) to $3$ passes ($\le 10$ workouts), $5$ passes ($\le 35$ workouts), $8$ passes ($\le 100$ workouts), and $12$ passes ($> 100$ workouts), so single passes render clearly in base cyan while frequently traveled corridors reliably reach Deep Indigo.
• In `ThermalColorLut`, base alpha progression SHALL increase: Stop 0 (Light Cyan, 0.00) to base alpha $140$ ($\approx 55\%$), Stop 1 (Pure Blue, 0.50) to base alpha $204$ ($\approx 80\%$), and Stop 2 (Deep Indigo, 1.00) to base alpha $255$ ($100\%$).
5. *Master Sport Filter Propagation & Unified Region Clustering (`ATT-4935`, `REQ-PER-024.5`)*:
• The Periods header sport filter (`selectedSports: StateFlow<Set<BSportType>>`) SHALL act as the authoritative Master Sport Filter across both card mini-maps (`PeriodSummaryCard`) and detailed full-screen maps (`PeriodMapScreen` / `InteractivePeriodMap`).
• In `filterPeriodSummary()`, the system SHALL filter all polyline collections symmetrically: `polylines`, `workoutIdToPolylineMap`, `workoutIdToAllPolylinesMap`, and `workoutIdToPathMap` SHALL only contain workouts whose sport is included in `selectedSports`.
• In `selectPeriodForMap()`, workouts loaded for the period date range SHALL be filtered by `selectedSports` before invoking `SpatialRegionEngine.detectRegions()` and generating `heatmapPathMap`.
• Spatial region detection and region chips (`Hauptregion` and secondary regions) SHALL reflect exclusively the geographic clusters and workout counts of the selected sports.
• Local detached sport selection state SHALL be removed from `PeriodMapScreen`, slaving full-screen map rendering directly to the master filter.
• Heatmap per-workout pixel counting (`ATT-4932`) and maximum corridor frequency normalization (`ATT-4929`) SHALL compute strictly on the sport-filtered path set.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-PER-024`, refining `REQ-PER-019` (*Full-Period Asynchronous Mini-Map Preview*) and `REQ-PER-021` (*InteractivePeriodMap Architecture & Large-Period Scaling*).
2. *Historical Origin & Commit Trace*: Sprint 2026-44.0 (`ATT-4480`, `ATT-4475`, `ATT-4769`, `ATT-1151`).
3. *Root Reason for Existing Formulation*: Vector polylines were originally rendered beneath heatmaps as fallback and hit-test targets, and loose 15–75m simplification tolerances were used to bound point counts before true tile-rasterized line-density heatmaps were introduced.
4. *Preservation of Core Invariants*: Pre-simplified background dispatch (`Dispatchers.Default`), zero UI-thread blocking, O(1) tile candidate bounding-box culling, and 100% clean-room test pass rate remain preserved.
5. *ATT-4935 Refinement (REQ-PER-024.1 / REQ-PER-024.5 / REQ-PER-013)*:
• Original Requirement ID & Target: `REQ-PER-024.1` (*Line-Density Heatmap Visuals & Adaptive Filtering*) and `REQ-PER-013` (*Spatial Region Partitioning*).
• Historical Origin & Commit Trace: Commit `548f3995` (`ATT-4480`) and commit `2bea16b7` (`ATT-4833`).
• Root Reason for Existing Formulation: `PeriodMapScreen` originally had its own sport chips during initial prototyping before the master header filter in `PeriodsTabsScreen` was established.
• Preservation of Core Invariants: 60 fps main-thread performance, thread-safe asynchronous tile generation on `Dispatchers.Default`, and fallback to all sports when no chips are selected remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a Period with heatmap available,
• *When* rendered in `PeriodSummaryCard`,
• *Then* the heatmap tile provider is displayed and vector polylines are suppressed.
• *Given* `PeriodMapScreen` opened without a workout selected,
• *When* the map renders,
• *Then* the line-density heatmap is rendered without vector polylines or start/stop markers.
• *Given* a period card in Region 2,
• *When* the user taps the mini-map,
• *Then* `PeriodMapScreen` opens focused on Region 2 with matching bounds.
• *Given* `getPeriodSimplificationToleranceMeters(PeriodType.MONTH)`,
• *When* tolerance is queried,
• *Then* it returns $6.0\text{m}$ ($\le 8\text{m}$).
• *Given* a Period with mixed bike and run workouts,
• *When* the user filters by bike in the Periods header,
• *Then* `PeriodSummaryCard` mini-map displays exclusively bike routes in its heatmap preview.
• *Given* a Period opened in `PeriodMapScreen` with bike filter active,
• *When* `selectPeriodForMap` initializes,
• *Then* `heatmapPathMap` and spatial region clusters contain only bike workouts and region counts match bike workouts.
• *Given* `filterPeriodSummary`,
• *When* filtered by a sport subset,
• *Then* `workoutIdToAllPolylinesMap` and `workoutIdToPathMap` contain strictly IDs matching the selected sports.

**Invariants**: Zero changes to `PeriodSummaries.db`, memory safety, True-Black AMOLED compliance, and 100% clean-room test pass rate.

### REQ-PER-025: GitHub-Style Yearly Activity Contribution Grids per BSportType in Periods (Years) [Verified]
- **Tests**: `TST-PER-031` | **Tickets**: `ATT-4849`, `ATT-5076`, `ATT-5194`, `ATT-5193`
- **Implementation**: `YearlyContributionGrid.kt`, `PeriodSummaryCard.kt`, `PeriodsTabsScreen.kt`, `PeriodsViewModel.kt`, `PeriodBarGraph.kt`
- **Rationale**: In `Periods` (`PeriodType.YEAR`), the system SHALL render a compact GitHub-style $7 \times 53$ activity contribution grid for each active `BSportType`, slaved directly to the master sport filter and active metric selector, enabling athletes to visualize seasonal consistency, volume distributions, rest periods, and training blocks across the 12 months without redundant UI controls (ATT-4849, ATT-5076, ATT-5194, ATT-5193):
1. *PeriodType.YEAR Scope (`REQ-PER-025.1`)*: The contribution grid SHALL be rendered exclusively on `PeriodSummaryCard` when `periodType == PeriodType.YEAR`.
2. *Master Selectors Authority & Hoisting (`REQ-PER-025.2`)*: The active metric for cell volume and color intensity SHALL be slaved to the master metric selector (`PeriodBarMetric`: `DURATION`, `DISTANCE`, `ASCENT`), hoisted to `PeriodsViewModel`. Active sport grids SHALL be slaved to the master sport filter (`selectedSports: Set<BSportType>`).
3. *Matrix Dimensions, Calendar Mapping & Day-of-Week Label Cadence (`REQ-PER-025.3`)*: Each grid SHALL render a $7 \times 53$ calendar day matrix (rows: Monday to Sunday, index 0..6; columns: ISO weeks 1..53). Column header labels SHALL render month markers (`Jan..Dec`) based on ISO week dates. The left day-of-week label column SHALL render abbreviated single-letter/narrow day labels for Monday (index 0), Wednesday (index 2), Friday (index 4), and Sunday (index 6, `DayOfWeek.SUNDAY`) via `shouldShowDayOfWeekLabel(rowIdx)`, establishing an even 2-day cadence (`[M, W, F, S]`). Day-of-week labels SHALL enforce unclipped vertical rendering inside 11 dp cell rows using `PlatformTextStyle(includeFontPadding = false)`, explicit `lineHeight = 9.sp`, and `wrapContentHeight(unbounded = true)` per Design Guidelines Section 5.15 (ATT-5193).
4. *Multi-Sport Separation & Canonical Order (`REQ-PER-025.4`)*: One dedicated grid SHALL be rendered per active `BSportType` that has $\ge 1$ workout in that calendar year and is enabled in `selectedSports`, ordered by Rule 57 canonical order (`[Bike, Run, Other]`).
5. *Quartile Shading & Sport Color Ramps (`REQ-PER-025.5`)*: Each grid cell SHALL be shaded using the sport's semantic palette (`TTColor.BikeSportBarColor`, `RunSportBarColor`, `OtherSportBarColor`) with 5 intensity levels: Level 0 (no activity, muted cell surface), Levels 1..4 (positive quartiles $Q_1, Q_2, Q_3, Q_4$ calibrated per sport's positive daily volumes in that year).
6. *Interactive Day Tap Drill-Down (`REQ-PER-025.6`)*: Tapping an active day cell SHALL highlight the cell, display the daily metric total formatted via Rule 36 canonical formatters (`DistanceFormatter`, `TimeFormatter`, `AltitudeFormatter`), and allow navigating to that day's workouts via `onStartWorkoutList(dayStartS, dayEndS, sport, workoutId)`.
7. *Localization & Persistence Invariants (`REQ-PER-025.7`)*: All added UI strings SHALL maintain 100% 9-language localization parity. Storing daily contribution structures SHALL be in-memory in `PeriodSummary` without altering `PeriodSummaries.db` (v27) schema.
8. *SportStatsRow Embedded Placement & Seamless Styling (`REQ-PER-025.8`)*: For `PeriodType.YEAR`, each sport's contribution grid SHALL be embedded directly inside that sport's `SportStatsRow` box in `PeriodSummaryCard.kt`, placed below the main summary header row and above the sub-sports breakdown (`CompactMetricRow`) and longest workout highlight. The embedded grid SHALL omit outer card borders and duplicate sport icon/title headers while retaining the intensity legend (ATT-5076).
9. *Initial Viewport Auto-Scroll Alignment (`REQ-PER-025.9`)*: Upon initial composition or year selection of `YearlyContributionGrid`, the horizontal scroll state SHALL automatically position the viewport:
- For the current calendar year (`year == LocalDate.now().year`): the viewport SHALL align the current week column at the far right edge of the visible content area (`scrollState.maxValue - (totalCols - 1 - targetCol) * colPitchPx`, clamped to `[0, scrollState.maxValue]`), ensuring historical activity remains visible while future weeks sit off-screen to the right.
- For past calendar years (`year < LocalDate.now().year`): the viewport SHALL scroll to the end of the year (`scrollState.maxValue`).
- Unrestricted Bidirectional Scrolling: The athlete SHALL retain unrestricted manual scrolling freedom left and right without snap-backs on recomposition or cell interaction (ATT-5194).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Modifies `REQ-PER-025` (*GitHub-Style Yearly Activity Contribution Grids per BSportType in Periods (Years)*), specifically amending `REQ-PER-025.3` for day-of-week micro-typography and Sunday anchor cadence. Targets `YearlyContributionGrid.kt`, `YearlyContributionGridTest.kt`, and `docs/requirements/req_aftermath_periods.md` (ATT-5193).
2. *Historical Origin & Commit Trace*: Sprint 2026-44.2 via commit `285d2470` (`ATT-4849`), Sprint 2026-45.1 via commit `4f74d924` (`ATT-5076`), Sprint 2026-45.2 via `ATT-5194` and `ATT-5193`.
3. *Root Reason for Existing Formulation*: Originally displayed only Mon, Wed, Fri following GitHub conventions. Tall glyphs (`F`) suffered vertical truncation in 11 dp cells due to default font padding and line-height overflow.
4. *Preservation of Core Invariants*: $7 \times 53$ matrix mapping, 11 dp cell height, quartile calculations, day-tap drill-down, embedded `SportStatsRow` placement, and bidirectional manual scrolling freedom are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing `YearlyContributionGrid`,
• *When* the left day-of-week label column is rendered,
• *Then* Monday, Wednesday, Friday, and Sunday labels SHALL be displayed without vertical glyph clipping.
• *Given* a Year period summary with Bike and Run workouts,
• *When* rendered in `Periods` with Distance metric selected,
• *Then* dedicated Bike and Run $7 \times 53$ contribution grids render embedded inside each respective `SportStatsRow` below main totals without outer card borders or duplicate sport headers.
• *Given* the sport filter header toggles Bike off,
• *When* observing the Year card,
• *Then* the Bike `SportStatsRow` and embedded contribution grid are hidden reactively while the Run row remains visible.
• *Given* an active day cell is tapped,
• *When* interacting with the embedded grid,
• *Then* the cell is highlighted with formatted daily metrics and navigation to that day's workouts is triggered.
• *Given* an athlete viewing `YearlyContributionGrid` for the current calendar year (e.g. November),
• *When* the card is composed,
• *Then* the grid SHALL auto-scroll so the current week is aligned at the far right edge of the viewport, with previous months visible to the left.
• *Given* an athlete viewing `YearlyContributionGrid` for a past calendar year,
• *When* the card is composed,
• *Then* the grid SHALL auto-scroll to the end of the year (`scrollState.maxValue`).
• *Given* the grid has auto-scrolled,
• *When* the athlete drags or flings horizontally,
• *Then* manual scrolling SHALL proceed smoothly without snap-backs or recomposition resets.

**Invariants**: Zero changes to `PeriodSummaries.db` (v27), 60 fps `LazyColumn` scrolling, Rule 36 unit parity, Rule 57 sport order, and 100% clean-room test pass rate.

### REQ-PER-026: Periods Map Region Place Name Resolution, Fallback Hierarchy & Persistent Caching [Verified]
- **Tests**: `TST-PER-033` | **Tickets**: `ATT-4934`
- **Implementation**: `RegionNameResolver.kt`, `SpatialRegionEngine.kt`, `PeriodMapScreen.kt`, `PeriodSummaryCard.kt`, `PeriodsViewModel.kt`, `PeriodMapUtils.kt`
- **Rationale**: Region chips on `PeriodSummaryCard` and `PeriodMapScreen` historically displayed generic labels such as "Hauptregion: Region 1 (30)" and "Region 2 (1)", giving the athlete zero geographical context without inspecting the map canvas. The system SHALL resolve and display human-readable place names (e.g. "Schönaich (30)") derived from the region center using a deterministic 3-tier fallback hierarchy, asynchronous background execution, and persistent quantized grid caching (ATT-4934):
1. *3-Tier Region Place Name Resolution Hierarchy (`REQ-PER-026.1`)*: When resolving the display name for a detected `SpatialRegion`'s geographical center (`region.center`), the system SHALL evaluate:
   - **Priority 1 (Nearest Known Location)**: Query `KnownLocationsDatabaseManager.getAllLocations()` for the closest location within $\le 10\text{ km}$ ($10,000\text{ m}$) that has a non-placeholder name (`!LocationNameResolver.isPlaceholderName(name)`). If found, its name SHALL be selected.
   - **Priority 2 (Android Geocoder Macro-Locality)**: If no known location matches within $10\text{ km}$, the system SHALL reverse-geocode via Android `Geocoder` and extract the macro-locality (`locality ?: subLocality ?: subAdminArea`).
   - **Priority 3 (Localized Generic Fallback)**: If Geocoder returns null, throws an exception (offline/network unavailable), or yields a blank result, the system SHALL fall back to the localized generic label `"Region n"` (where $n = \text{index} + 1$).
2. *Asynchronous Background Execution (`REQ-PER-026.2`)*: Region place name resolution SHALL execute exclusively on background worker threads (`Dispatchers.IO`) in `PeriodsViewModel.showPeriodMap()` and `PeriodMapUtils.prepareCardMapPreview()`. Zero network, disk I/O, or geocoding calls SHALL execute on the main UI thread.
3. *Two-Tier Persistent Caching (`REQ-PER-026.3`)*: To prevent repeated geocoding on list scrolling and guarantee offline availability after first resolution, resolved place names SHALL be cached in:
   - Level 1: In-memory cache (`ConcurrentHashMap`) keyed by quantized coordinate cell.
   - Level 2: Persistent `SharedPreferences` cache (`period_region_names`) keyed by quantized coordinate cell (`lat_cell_lng_cell`, $\approx 0.04^\circ$ step $\approx 4\text{ km}$).
4. *UI Chip Label Formatting, Hauptregion Semantics & Ellipsis Guard (`REQ-PER-026.4`)*: In `PeriodMapScreen` and `PeriodSummaryCard`:
   - Primary region chips SHALL retain `"Hauptregion"` / `"Primary"` prefix semantics:
     - Named: `R.string.workout_periods__primary_named_region_format` (`"Hauptregion: %1$s (%2$d)"` / `"Primary: %1$s (%2$d)"`).
     - Generic: `R.string.workout_periods__primary_region_format` (`"Hauptregion: Region %1$d (%2$d)"` / `"Primary: Region %1$d (%2$d)"`).
   - Secondary region chips SHALL use:
     - Named: `R.string.workout_periods__named_region_format` (`"%1$s (%2$d)"`).
     - Generic: `R.string.workout_periods__region_format` (`"Region %1$d (%2$d)"`).
   - All region chip labels SHALL enforce single-line layout (`maxLines = 1, overflow = TextOverflow.Ellipsis`) to prevent multi-line wrapping and layout truncation on narrow screens across all 9 supported locales.
5. *9-Language Localization Parity (`REQ-PER-026.5`)*: Added string resources (`workout_periods__named_region_format`, `workout_periods__primary_named_region_format`) SHALL maintain 100% localization parity across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-PER-026` (*Periods Map Region Place Name Resolution, Fallback Hierarchy & Persistent Caching*), refining `REQ-PER-013.5` (*Interactive Region Navigation & Labels*) and `REQ-UI-165` (*Location Name Resolution*). Targets `SpatialRegionEngine.kt`, `PeriodMapScreen.kt`, `PeriodSummaryCard.kt`, `PeriodsViewModel.kt`, and `PeriodMapUtils.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-44.2 (`ATT-4934`), refining Sprint Review 2026-44.1 finding from `ATT-4833`.
3. *Root Reason for Existing Formulation*: `SpatialRegionEngine` was originally implemented as pure JVM mathematical clustering without access to Android Context or geocoding services, hardcoding generic `"Region ${index + 1}"` labels to prevent blocking clustering loops.
4. *Preservation of Core Invariants*: `SpatialRegionEngine` remains pure JVM math without Android framework mocks; resolution is decoupled into `RegionNameResolver` on `Dispatchers.IO`; main-thread 60 fps `LazyColumn` scrolling and offline operation are guaranteed by two-tier caching.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a region whose center is within 10 km of a known location "Schönaich",
• *When* the region name is resolved,
• *Then* Priority 1 succeeds and the region name is "Schönaich".
• *Given* a region with no known location within 10 km, but online Geocoder returns locality "Böblingen",
• *When* the region name is resolved,
• *Then* Priority 2 succeeds and the region name is "Böblingen".
• *Given* an offline device with no known location and no cache entry,
• *When* the region name is resolved,
• *Then* Priority 3 fallback executes and the region label is "Region 1".
• *Given* a resolved region name,
• *When* scrolling the period summary list or reopening the period map offline,
• *Then* the cached name is loaded immediately without invoking Geocoder.
• *Given* a primary region with place name "Herrenberg" and 25 workouts,
• *When* rendered in German locale,
• *Then* the chip displays "Hauptregion: Herrenberg (25)" with single-line ellipsis on narrow screens.

**Invariants**: Zero changes to `PeriodSummaries.db` (v27) schema, zero network/disk I/O on UI thread, 9-language localization parity, and 100% clean-room test pass rate.

### REQ-PER-027: Embedded Daily Activity Intensity / Mini-Bar Strip for Months & Weeks in SportStatsRow [Verified]
- **Tests**: `TST-PER-035` | **Tickets**: `ATT-5195`, `ATT-5280`
- **Implementation**: `DailyActivityStrip.kt`, `PeriodSummaryCard.kt`, `PeriodData.kt`, `PeriodsRepository.kt`, `PeriodsViewModel.kt`
- **Rationale**: For `Periods` in Months (`PeriodType.MONTH`) and Weeks (`PeriodType.WEEK`), the system SHALL embed a compact horizontal daily activity intensity / mini-bar chart inside each sport's `SportStatsRow` below the sport header and above sub-sports/longest workout (Option A), slaved directly to the master sport filter and active metric selector, matching the interactive drill-down and semantic coloring of `YearlyContributionGrid` (ATT-5195):
1. *Month and Week Temporal Scope (`REQ-PER-027.1`)*: The daily activity strip SHALL be rendered inside each sport's `SportStatsRow` on `PeriodSummaryCard` when `periodType == PeriodType.MONTH` or `periodType == PeriodType.WEEK`. `PeriodType.YEAR` and `PeriodType.DAY` SHALL remain unaffected.
2. *Data Hydration & In-Memory Domain Model (`REQ-PER-027.2`)*: `PeriodsRepository.enrich()` SHALL aggregate daily activities and peak metric values into `dailySportContributions: Map<BSportType, DailySportContribution>` for `PeriodType.WEEK` and `PeriodType.MONTH` from the period's workouts without altering `PeriodSummaries.db` (v27) schema. `PeriodsViewModel.filterPeriodSummary()` SHALL filter `dailySportContributions` symmetrically according to `selectedSports`.
3. *Week 7-Day & Month 28..31-Day Mini-Bar Layout (`REQ-PER-027.3`)*:
   - For `PeriodType.WEEK`: The strip SHALL render exactly 7 columns corresponding to Monday through Sunday of that week, labeled with localized day initials.
   - For `PeriodType.MONTH`: The strip SHALL render compact non-scrolling columns for all days in that calendar month (1..28/29/30/31), displaying milestone day labels (1, 10, 20, last day).
4. *Metric Slaving, Proportional Height Scaling & Bar Shape Geometry (`REQ-PER-027.4`)*:
   - Mini-bar heights and color intensity SHALL be slaved to the active metric (`DURATION`, `DISTANCE`, `ASCENT`). Active days SHALL render a filled vertical bar with height proportional to the day's metric value relative to the sport's maximum daily value in that period ($H_{\text{bar}} = \text{clamp}(V_{\text{day}} / V_{\text{max}}, 0.15, 1.0) \times H_{\text{max}}$). Zero/rest days SHALL render a subtle muted baseline track.
   - For bottom-anchored mini-bars in `MonthDayBar` and `WeekDayColumn`:
     • The outer container `Box` SHALL NOT clip its bottom corners, allowing the inner bar and selection border to sit flush on the baseline without bottom corner clipping (ATT-5280, Rule 69).
     • Active mini-bars SHALL render with rounded top corners and flat, sharp bottom corners (`RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 0.dp, bottomEnd = 0.dp)` in `MonthDayBar`; `RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 0.dp, bottomEnd = 0.dp)` in `WeekDayColumn`), aligning with `docs/design_guidelines.md` §2.7 and matching `PeriodBarGraph.kt` reference styling.
5. *Semantic Sport Palette Parity (`REQ-PER-027.5`)*: Filled mini-bars SHALL be colored with the sport's semantic color token (`TTColor.BikeSportBarColor`, `TTColor.RunSportBarColor`, `TTColor.OtherSportBarColor`) and quartile/alpha shading.
6. *Interactive Day Selection & Drill-Down Navigation (`REQ-PER-027.6`)*: Tapping a day bar SHALL toggle a day detail banner below the strip displaying the date and formatted metric total via Rule 36 canonical formatters (`TimeFormatter`, `DistanceFormatter`, `AltitudeFormatter`). For active days, tapping the banner SHALL trigger drill-down navigation via `onDayCellClick` to `WorkoutSummariesListFragment`.
7. *Seamless SportStatsRow Embedding (`REQ-PER-027.7`)*: The strip SHALL be embedded directly inside `SportStatsRow` without extra outer card borders or redundant sport headers, positioned below the sport totals row and above sub-sports / longest workout dividers.
8. *Localization & Invariant Parity (`REQ-PER-027.8`)*: All user-facing strings and accessibility semantics SHALL maintain 100% 9-language localization parity (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-PER-027.4` (*Metric Slaving & Proportional Height Scaling / Mini-Bar Corner Geometry*). Targets `DailyActivityStrip.kt` (`MonthDayBar`, `WeekDayColumn`).
2. *Historical Origin & Commit Trace*: Added in `ATT-5195` (commit `ca6a892f`, Sprint 2026-45.2). Amending ticket: `ATT-5280` (Sprint 2026-45.3).
3. *Root Reason for Existing Formulation*: In `ATT-5195`, the outer container `Box` was given `.clip(RoundedCornerShape(2.dp / 3.dp))` for cell slot encapsulation, which inadvertently clipped the bottom corners of bottom-anchored inner bars while inner bar top corners had smaller radii, creating an inverted rounded-bottom visual defect.
4. *Preservation of Core Invariants*: Click targets, selection borders (`RoundedCornerShape(2.dp / 3.dp)`), empty rest-day baseline tracks (fixed 2.dp height), date mappings, metric calculations, and repository flows are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a Month summary card (`PeriodType.MONTH`),
• *When* the card is displayed,
• *Then* each sport's `SportStatsRow` SHALL render a horizontal mini-bar strip across the exact number of days in that month (28, 29, 30, or 31) showing daily activity for that sport.
• *Given* an active mini-bar rendered in `DailyActivityStrip` (`MonthDayBar` or `WeekDayColumn`),
• *When* the bar is drawn,
• *Then* the bar has rounded top corners and flat sharp bottom corners resting flush on the baseline without outer container bottom-corner clipping (ATT-5280).
• *Given* an athlete viewing a Week summary card (`PeriodType.WEEK`),
• *When* the card is displayed,
• *Then* each sport's `SportStatsRow` SHALL render a 7-day mini-bar strip (Monday through Sunday) showing daily activity for that sport.
• *Given* an active metric switch (Duration, Distance, or Ascent),
• *When* the user toggles the master metric,
• *Then* mini-bar heights in `DailyActivityStrip` SHALL reactively update to reflect the selected metric.
• *Given* an athlete tapping an active day bar in `DailyActivityStrip`,
• *When* tapped,
• *Then* a summary banner is displayed with formatted metrics, and tapping navigates to that day's workouts via `WorkoutSummariesListFragment`.

**Invariants**: Zero changes to `PeriodSummaries.db` (v27) schema, 60 fps `LazyColumn` scrolling, Rule 36 unit parity, Rule 57 sport order, and 100% clean-room test pass rate.

### REQ-PER-028: Period Summary Date Range & Chart Metric Label Active Locale Formatting [Verified]
- **Tests**: `TST-PER-036` | **Tickets**: `ATT-5836`
- **Implementation**: `PeriodBarGraph.kt`, `PeriodsRepository.kt`, `DateTimeUtils.kt`, `PeriodSummaryCard.kt`
- **Rationale**: Weekly period headers and chart metric values SHALL format dates and decimal numbers according to the user's active locale instead of hardcoding US patterns:
1. *Active Locale Weekly Date Range Formatting (`REQ-PER-028.1`)*: Weekly period date ranges (`periodDateRange`) SHALL be formatted according to the active `Locale` (`Locale.getDefault()`). In day-first locales (e.g. German `de_DE`), the date range SHALL display day before month (e.g. `"2. Nov. – 8. Nov."` or `"2. Nov. - 8. Nov."`), and in month-first locales (e.g. US English `en_US`), month before day (e.g. `"Nov 2 – Nov 8"` or `"Nov. 2 - Nov. 8"`).
2. *Active Locale Dynamic Resolution for Cached Periods (`REQ-PER-028.2`)*: To ensure locale changes take immediate effect without database recreation, UI rendering of `periodDateRange` in `PeriodSummaryCard` and `PeriodMapScreen` SHALL dynamically resolve localized date ranges using `startTimestampS` and `endTimestampS` when available, falling back to stored `periodDateRange`.
3. *Active Locale Chart Decimal Separator Formatting (`REQ-PER-028.3`)*: Top-bar metric value labels generated by `formatMetricValue` in `PeriodBarGraph.kt` SHALL accept `locale: Locale = Locale.getDefault()`. Decimals for durations (`1h..10h`, e.g. `1,7h` in German vs `1.7h` in US English) and distances (`km`/`mi`, e.g. `5,4km` / `5,3mi` in German vs `5.4km` / `5.3mi` in US English) SHALL use the locale's decimal separator via `DecimalFormatSymbols.getInstance(locale).decimalSeparator` or localized floating-point formatting.
4. *Preservation of System Invariants (`REQ-PER-028.4`)*: Non-decimal duration formats (`<1m`, `${m}m`, `${h}h`), integer kilometer/mile values, `PeriodSummaries.db` table schema and version, period aggregation boundaries, and 100% clean-room test pass rate SHALL be preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-PER-018.2` (*Sub-Hour Duration & Value Formatting*), `REQ-PER-023` (*PeriodBarGraph Full Distance & Imperial Unit Parity*), and `REQ-LOC-001` (*9-Language Localization Parity*). Targets `PeriodBarGraph.kt`, `PeriodsRepository.kt`, `DateTimeUtils.kt`, and `PeriodSummaryCard.kt`.
2. *Historical Origin & Commit Trace*: Date range formatting introduced in `ATT-4476` (`rangeDateFormatter = DateTimeFormatter.ofPattern("MMM d")`); bar metric formatting introduced in `ATT-4771` (`formatMetricValue` with `Locale.US` and literal `.`).
3. *Root Reason for Existing Formulation*: Initial implementations prioritized compact fixed-width English layouts on test devices before comprehensive multi-locale visual audits (`Rule 71`/`Rule 72`) identified German truncation and formatting divergence in `audit_03_zeitraeume.png`.
4. *Preservation of Core Invariants*: In-memory period data structures (`PeriodSummary`), SQLite persistence schema (`PeriodSummaries.db`), period key sorting (`2026-W45`), and non-decimal formatting rules remain 100% unchanged.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete with device locale set to German (`de_DE`),
• *When* viewing a weekly period summary card (`PeriodType.WEEK`),
• *Then* the date range header displays day before month (e.g. `2. Nov. – 8. Nov.` or `2. Nov. - 8. Nov.`), not US month-first `Nov. 2 - Nov. 8`.
• *Given* an athlete with device locale set to German (`de_DE`),
• *When* viewing `PeriodBarGraph` with durations between 1h and 10h or fractional distances,
• *Then* the bar labels display a comma decimal separator (e.g. `1,7h`, `5,4km`, `5,3mi`), not a US dot `1.7h`, `5.4km`, `5.3mi`.
• *Given* an athlete with device locale set to US English (`en_US`),
• *When* viewing `PeriodBarGraph`,
• *Then* the bar labels retain dot decimal separators (`1.7h`, `5.4km`, `5.3mi`).
• *Given* an existing cached period summary in `PeriodSummaries.db` created under English locale,
• *When* the user switches device locale to German,
• *Then* the rendered date range header dynamically updates to German day-first format.

**Invariants**: Zero changes to `PeriodSummaries.db` (v27) schema, 60 fps `LazyColumn` scrolling, and 100% clean-room test pass rate.

### REQ-PER-029: WorkoutShareBottomSheet Option A ('Karten- & Analyse-Snapshot') Automated Capture & Share Delegation [Verified]
- **Tests**: `TST-PER-037` | **Tickets**: `ATT-6208`
- **Implementation**: `WorkoutShareBottomSheet.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`, `MapDetailLayout.kt`, `ShareUtils.kt`
- **Rationale**: When an athlete selects Option A ("Karten- & Analyse-Snapshot") in `WorkoutShareBottomSheet`, the application SHALL automatically navigate or scroll to the Map tab, capture the composite map and analytics snapshot, and launch the Android system share sheet (`combineWorkoutAndShare`) instead of merely switching views without action:
1. *Programmatic Snapshot Trigger in `MapDetailLayout` (`REQ-PER-029.1`)*: `MapDetailLayout` SHALL accept `externalSnapshotTrigger: Boolean = false` and `onSnapshotComplete: (() -> Unit)? = null`. When `externalSnapshotTrigger == true` and `!isSharing`, `MapDetailLayout` SHALL initiate `isSharing = true` to capture the composite map, header, elevation, and analytics layers and launch the Android Share sheet via `combineWorkoutAndShare`. Upon snapshot completion or error, `isSharing` SHALL reset and `onSnapshotComplete?.invoke()` SHALL be called.
2. *WorkoutDetailTabbedContent Option A Automated Handoff (`REQ-PER-029.2`)*: In `WorkoutDetailTabbedContent`, selecting Option A ('Karten- & Analyse-Snapshot') in `WorkoutShareBottomSheet` SHALL dismiss the bottom sheet, switch or animate the pager to the Map tab (`mapTabIndex`), and programmatically trigger `MapDetailLayout`'s composite snapshot capture (`externalSnapshotTrigger = true`) once the Map tab is active.
3. *WorkoutSummary List-to-Detail Auto-Share Bridge (`REQ-PER-029.3`)*: In `WorkoutSummary`, selecting Option A in `WorkoutShareBottomSheet` SHALL bridge through to `WorkoutDetailScreen` / `TrackOnMapScreen` / `WorkoutDetailTabbedContent` with an auto-share intent (`initialAutoShareSnapshot = true`), navigating directly to the Map tab and triggering the composite snapshot and Android Share sheet.
4. *Preservation of REQ-UI-500 / ATT-6214 Map Tab Controls Invariant (`REQ-PER-029.4`)*: The default parameter `showFloatingShareButton: Boolean = workoutData == null` in `MapDetailLayout` and the `top = 16.dp` docking of `topEndActions` (Layers button) MUST remain preserved, ensuring no duplicate or overlapping floating share button appears on the Map tab when viewing workouts.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-PER-017` (*Composite Post-Workout Snapshot & Share Composition*) and honors `REQ-UI-500` (*MapDetailLayout Unified Top-End Actions Slot*). Targets `WorkoutShareBottomSheet.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`, and `MapDetailLayout.kt`.
2. *Historical Origin & Commit Trace*: Option A introduced in `ATT-30` / `ATT-1472` and refined in `ATT-5809` (centralized workout sharing onto `WorkoutHeader` and suppressed floating Share FAB when `workoutData != null`).
3. *Root Reason for Existing Formulation*: ATT-5809 migrated the share entry point to `WorkoutHeader` and added `WorkoutShareBottomSheet` modal selection, but `onSelectMapSnapshot` was only wired to navigate/scroll to the Map tab without triggering `isSharing` or bridging the auto-share intent from list cards.
4. *Preservation of Core Invariants*: Top-end action stacking (`REQ-UI-500`), status bar inset clearance (`REQ-UI-491`), off-thread bitmap composition (`Dispatchers.Default`), and 100% clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a workout in `WorkoutDetailTabbedContent` on any tab,
• *When* tapping the Share button in `WorkoutHeader` and selecting 'Karten- & Analyse-Snapshot',
• *Then* the bottom sheet dismisses, the view scrolls/switches to the Map tab, and the composite snapshot is automatically captured and launched via the Android Share sheet (`Intent.ACTION_SEND`).
• *Given* an athlete viewing a workout card in the workout list (`WorkoutSummary`),
• *When* tapping the Share button and selecting 'Karten- & Analyse-Snapshot',
• *Then* the detail view opens with `initialAutoShareSnapshot = true`, navigating to the Map tab, capturing the composite snapshot, and launching the Android Share sheet.
• *Given* `MapDetailLayout` rendered with `workoutData != null`,
• *When* the Map tab is displayed,
• *Then* `showFloatingShareButton` is false and top-end actions dock at `16.dp` per `REQ-UI-500`.

**Invariants**: Zero changes to database schema, zero UI freezes on main thread during snapshot composition, preservation of `showFloatingShareButton: Boolean = workoutData == null`, and 100% clean-room test pass rate.

### REQ-PER-030: Morning HRV Readiness Database Persistence, Backup Parity & Per-Posture SWC Baseline Calculator [Verified]
- **Tests**: `TST-PER-038` | **Tickets**: `ATT-3545`, `ATT-6718`
- **Implementation**: `HrvReadinessDatabaseManager.kt`, `HrvReadinessRepository.kt`, `HrvReadinessEntry.kt`, `HrvPosture.kt`, `ReadinessStatus.kt`, `HrvReadinessCalculator.kt`, `MorningHrvCheckScreen.kt`, `DatabaseSchemaRegistry.kt`, `DatabaseConnectionManager.kt`, `strings_hrv.xml`
- **Rationale**: Resting morning Heart Rate Variability (HRV / $\ln(\text{rMSSD})$) provides objective longitudinal insight into autonomic nervous system recovery and physiological training readiness. To ensure clinically sound baselines without orthostatic contamination, the application SHALL persist daily waking HRV measurements in a dedicated database (`HrvReadiness.db`), integrate with `.attbackup` backup/restore, deduplicate multiple daily readings transactionally, and compute Smallest Worthwhile Change (SWC) baselines strictly isolated per posture:
1. *Dedicated SQLite Persistence & Symmetric DTO Mapping (`REQ-PER-030.1`)*: The system SHALL persist morning readiness records in a dedicated SQLite database `HrvReadiness.db` (table `hrv_readiness_entries`, managed by `HrvReadinessDatabaseManager`, `DB_VERSION = 1`). Columns SHALL include `_id` (INTEGER PRIMARY KEY AUTOINCREMENT), `timestamp_ms` (INTEGER NOT NULL), `date_iso` (TEXT NOT NULL, `YYYY-MM-DD` local calendar date), `timezone_id` (TEXT NOT NULL), `resting_hr_bpm` (INTEGER NOT NULL), `rmssd_ms` (REAL NOT NULL), `ln_rmssd` (REAL NOT NULL), `hrv_score_100` (REAL NOT NULL, $20 \times \ln(\text{rMSSD})$), `sdnn_ms` (REAL), `artifact_rate_pct` (REAL), `duration_sec` (INTEGER NOT NULL), `posture` (TEXT NOT NULL, `SUPINE` / `SEATED` / `STANDING`), `is_primary_for_day` (INTEGER NOT NULL DEFAULT 0), and `notes` (TEXT). Domain model `HrvReadinessEntry` SHALL enforce symmetric single-row and batch `fromCursor` mapping per Rule 20.
2. *Database Schema Registry & Backup Integration (`REQ-PER-030.2`)*: `HrvReadiness.db` SHALL be registered in `DatabaseSchemaRegistry.SUPPORTED_DB_VERSIONS` referencing `HrvReadinessDatabaseManager.DB_VERSION` directly per Rule 62, and registered in `DatabaseConnectionManager.getActiveDatabases()` to ensure atomic `VACUUM INTO` snapshots and write-lock synchronization during `.attbackup` creation and restore (`REQ-DAT-031`).
3. *Daily Deduplication, Artifact Gating & Transactional Primary Reading Maintenance (`REQ-PER-030.3`)*: Daily aggregation SHALL key strictly on `(date_iso, posture)` in the athlete's local calendar day. When evaluating daily readiness, invalid readings (`artifact_rate_pct > 5.0%`) SHALL be excluded. Multiple valid readings on the same day SHALL be resolved deterministically at query time via `ORDER BY is_primary_for_day DESC, timestamp_ms ASC LIMIT 1`. `HrvReadinessDatabaseManager` SHALL maintain `is_primary_for_day` transactionally upon insertion, deletion, and user pin toggles so the earliest valid morning reading or pinned reading is always accurately marked. Read requests or pin toggles (`setPrimaryReading`, `insertEntry`) targeting readings with `artifact_rate_pct > 5.0%` SHALL be rejected or ignored, preventing high-artifact readings from displacing valid primary records.
4. *Per-Posture SWC Baseline Calculator & Bounded Window Queries (`REQ-PER-030.4`)*: `HrvReadinessCalculator` SHALL compute rolling baselines strictly partitioned by `HrvPosture` (`SUPINE`, `SEATED`, `STANDING`). `HrvReadinessRepository.calculateReadiness()` SHALL query only the indexed bounded 60-day window `[D-59, D]` via `getPrimaryEntriesInWindow(posture, dMinus59Iso, targetDateIso)`. The rolling 7-day average $\mu_{7d}$ SHALL require $\ge 3$ valid daily readings in `[D-6, D]`. The rolling 60-day baseline mean $\mu_{60d}$ and sample standard deviation $\sigma_{60d}$ (evaluated using Bessel's correction with $N-1$ denominator over daily $\ln(\text{rMSSD})$) SHALL require $\ge 14$ valid daily readings in `[D-59, D]`. The Smallest Worthwhile Change (SWC) corridor SHALL be defined as $[\mu_{60d} - 0.5\sigma_{60d},\; \mu_{60d} + 0.5\sigma_{60d}]$. Readiness status and structured calibration state (`CalibrationProgressState`: `BASELINE_60D`, `WEEKLY_7D`, `COMPLETE`) SHALL be exposed on `HrvReadinessResult`, formatting localized progress messages across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero hardcoded English UI strings.
5. *Preservation of System Invariants (`REQ-PER-030.5`)*: Standalone isolation of `HrvReadiness.db` SHALL introduce zero modifications or locking dependencies to existing workout, sensor, or lap databases. Mathematical calculations SHALL remain pure Kotlin without Android UI framework coupling.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-PER-030` (`REQ-PER-030.3`, `REQ-PER-030.4`), targeting `HrvReadinessCalculator.kt`, `HrvReadinessRepository.kt`, `HrvReadinessDatabaseManager.kt`, `MorningHrvCheckScreen.kt`, and `strings_hrv.xml`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-3545` (commit `6bfb073e`); refined in `ATT-6718` under Epic `ATT-3539` (*Heart Rate Variability*).
3. *Root Reason for Existing Formulation*: Early prototype concatenated English strings directly; `getAllEntries` was an initial convenience query before windowed queries were integrated; primary pinning was designed without checking electrode artifact flags.
4. *Preservation of Core Invariants*: Zero SQLite schema changes (`HrvReadiness.db` remains at version 1), strict posture isolation, sample thresholds ($\ge 3$ days for 7d, $\ge 14$ days for 60d) and SWC bounds preserved, and 100% clean-room test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete recording 10 `SUPINE` and 10 `SEATED` valid readings across distinct days,
• *When* `HrvReadinessCalculator` evaluates `SUPINE` readiness,
• *Then* `SEATED` readings are strictly excluded from the `SUPINE` baseline and the status remains `CALIBRATING` (`10 / 14 days`).
• *Given* $\ge 14$ daily `SUPINE` $\ln(\text{rMSSD})$ values with $\mu_{60d} = 4.00$ and sample SD $\sigma_{60d} = 0.30$ (SWC corridor $[3.85, 4.15]$), and two readings recorded on the target day (`07:00` and `19:00`),
• *When* daily readiness is evaluated,
• *Then* only the primary morning reading represents that calendar day; $\mu_{7d} = 3.70$ classifies as `FATIGUED_LOW` and $\mu_{7d} = 4.22$ classifies as `ABOVE_NORMAL`.
• *Given* an earlier valid morning reading (`is_primary_for_day = 1`, `artifact_rate_pct = 1.0%`) and a later noisy reading (`artifact_rate_pct = 8.5%`) on the same date,
• *When* attempting to pin the noisy reading via `setPrimaryReading` or `insertEntry`,
• *Then* the noisy reading is rejected from receiving the primary flag and the earlier valid morning reading remains primary.
• *Given* 5 years of historical morning HRV records in `HrvReadiness.db`,
• *When* `HrvReadinessRepository.calculateReadiness()` is invoked for date `D`,
• *Then* only primary valid records in `[D-59, D]` are queried via `getPrimaryEntriesInWindow()`.
• *Given* any of the 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT),
• *When* morning HRV measurement is in `CALIBRATING` state,
• *Then* calibration progress renders localized strings matching `%1$d` and `%2$d` parameters with zero raw English literals.
• *Given* recorded entries in `HrvReadiness.db`,
• *When* an `.attbackup` archive is created and restored onto a clean database state,
• *Then* all `hrv_readiness_entries` rows are restored intact.

**Invariants**: Zero changes to existing workout/sensor databases, pure Kotlin calculation logic, and 100% clean-room test pass rate.

### REQ-PER-031: Android Health Connect Morning and Overnight Resting HRV Ingestion [Verified]
- **Tests**: `TST-PER-039` | **Tickets**: `ATT-4981`
- **Implementation**: `HealthConnectHrvImporter.kt`, `HealthConnectManager.kt`, `HrvPosture.kt`, `HrvReadinessRepository.kt`, `AndroidManifest.xml`
- **Rationale**: Passive overnight wearable monitoring (smart rings, smartwatches) provides continuous nocturnal resting HRV and resting heart rate data via Android Health Connect. To allow athletes to utilize nocturnal biometric tracking without contaminating waking spot-check baselines, the system SHALL ingest nocturnal `HeartRateVariabilityRmssdRecord` and `RestingHeartRateRecord` into `HrvReadinessRepository` under an isolated `OVERNIGHT_WEARABLE` cohort:
1. *Permission & User Opt-In Gating (`REQ-PER-031.1`)*: The system SHALL declare `READ_HEART_RATE_VARIABILITY` and `READ_RESTING_HEART_RATE` permissions in `AndroidManifest.xml` and manage runtime permission checks through `HealthConnectManager.kt`. Ingestion SHALL require explicit user opt-in (`isHealthConnectHrvImportEnabled`) in preferences.
2. *Nocturnal Log-Normal Multi-Sample Aggregation & Date Attribution (`REQ-PER-031.2`)*: When syncing, `HealthConnectHrvImporter` SHALL query nocturnal records in the target time window. Records recorded between 18:00 and 12:00 SHALL be attributed to the waking calendar day `date_iso` (records before midnight attribute to the following day; records after midnight attribute to the current day). Because nocturnal rMSSD follows a log-normal distribution, multiple sample epochs within the nocturnal window SHALL be aggregated using the arithmetic mean of log values: $\text{ln\_rmssd} = \frac{1}{M}\sum \ln(\text{rMSSD}_i)$ and geometric mean $\text{rmssd\_ms} = \exp(\text{ln\_rmssd})$. Associated resting heart rate SHALL be aggregated as the arithmetic mean of valid nocturnal samples.
3. *Isolated Cohort Persistence (`REQ-PER-031.3`)*: The system SHALL extend `HrvPosture` with `OVERNIGHT_WEARABLE` and persist the aggregated measurement via `HrvReadinessRepository` with `artifact_rate_pct = null`. `HrvReadinessCalculator` SHALL evaluate `OVERNIGHT_WEARABLE` baselines in complete isolation from waking postures (`SUPINE`, `SEATED`, `STANDING`).
4. *Idempotent Ingestion & Source Tracking (`REQ-PER-031.4`)*: Ingestion SHALL be idempotent, deduplicating records by `(date_iso, OVERNIGHT_WEARABLE)` and persisting the source package and record UID in `notes`. Subsequent import syncs SHALL NOT overwrite manually pinned entries or create duplicate rows.
5. *Preservation of System Invariants (`REQ-PER-031.5`)*: Standalone isolation of `OVERNIGHT_WEARABLE` baseline math, zero schema changes to `HrvReadiness.db` (v1), full `.attbackup` export/restore parity, and zero unprompted background sync operations when disabled.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-PER-031` (extending `REQ-PER-030` morning HRV readiness persistence and `REQ-SET-080` Health Connect integration). Targets `HealthConnectHrvImporter.kt`, `HealthConnectManager.kt`, `HrvPosture.kt`, `HrvReadinessRepository.kt`, and `AndroidManifest.xml`.
2. *Historical Origin & Commit Trace*: Net-new capability introduced in `ATT-4981` under Epic `ATT-3539` (*Heart Rate Variability*).
3. *Root Reason for Existing Formulation*: Wearables record multi-sample nocturnal readings that exhibit distinct physiological baselines from orthostatic waking spot checks. Introducing `OVERNIGHT_WEARABLE` cleanly partitions wearable data from chest-strap measurements.
4. *Preservation of Core Invariants*: Waking postures (`SUPINE`, `SEATED`, `STANDING`) and `HrvReadiness.db` SQLite schema remain 100% untouched.

**Acceptance Criteria (Given-When-Then)**:
• *Given* nocturnal Health Connect records across sleep epochs (e.g. 30.0 ms, 40.0 ms, 50.0 ms),
• *When* imported by `HealthConnectHrvImporter`,
• *Then* $\text{ln\_rmssd}$ is the mean of logs ($\approx 3.655$), $\text{rmssd\_ms} \approx 38.67\text{ ms}$, and attributed to waking date `date_iso`.
• *Given* an athlete with 14 `OVERNIGHT_WEARABLE` days and 10 `SUPINE` waking spot-checks,
• *When* evaluating `SUPINE` readiness,
• *Then* `OVERNIGHT_WEARABLE` records are strictly excluded and `SUPINE` remains `CALIBRATING` (`10 / 14 days`).
• *Given* user has not opted in or granted Health Connect permissions,
• *When* import sync is invoked,
• *Then* import aborts safely without exception and zero database records are modified.

**Invariants**: Zero changes to `HrvReadiness.db` (v1) schema, strict posture isolation, and 100% clean-room test pass rate.

### REQ-PER-032: Guided Morning Resting HRV Measurement Screen & Waking Protocol [Verified]
- **Tests**: `TST-PER-040` | **Tickets**: `ATT-3556`, `ATT-6719`
- **Implementation**: `MorningHrvCheckScreen.kt`, `MorningHrvCheckViewModel.kt`, `HrvReadinessRepository.kt`, `RrArtifactFilter.kt`, `ATrainingTrackerApp.kt`, `strings_hrv.xml`
- **Rationale**: Accurate morning resting HRV evaluation requires standardized waking conditions (post-voiding, rested posture, normal breathing) without starting a full GPS workout. The system SHALL provide a dedicated Compose UI measurement screen (`MorningHrvCheckScreen`) and ViewModel (`MorningHrvCheckViewModel`) executing a guided waking protocol:
1. *Standardized Protocol Lifecycle & UI Ergonomics (`REQ-PER-032.1`)*: The measurement state machine SHALL transition through `READY` -> `STABILIZING` -> `MEASURING` -> `COMPLETED`. The stabilization phase SHALL default to 15–30s to allow autonomic balance. The measurement duration SHALL default to 60s (validated per Esco & Flatt 2014) with a selectable 120s extended option, rendered using Material 3 `SingleChoiceSegmentedButtonRow`. The scrollable column SHALL include `navigationBarsPadding()`, and the screen SHALL be kept awake (`keepScreenOn = true`) during `STABILIZING` and `MEASURING`.
2. *Posture Stickiness & Standardization (`REQ-PER-032.2`)*: The screen SHALL provide a posture selector for waking cohorts (`SUPINE`, `SEATED`, `STANDING`) and remember the athlete's last-used posture via SharedPreferences (`SP_HRV_LAST_POSTURE = "hrvLastPosture"`) across app sessions to preserve longitudinal baseline consistency.
3. *Sensor Ingestion, Cold-Start Discovery & Lifecycle Scoping (`REQ-PER-032.3`)*: Measurement SHALL ingest raw beat-to-beat intervals via `BANALServiceRepository` without launching `TrackerService` or creating rows in `WorkoutSummaries.db`. Upon screen entry and ViewModel initialization, `MorningHrvCheckViewModel` SHALL invoke `startSearchingForPairedDevices()` to discover paired BLE/ANT+ HR sensors immediately on cold launch. The ViewModel SHALL be scoped to `NavBackStackEntry` (`NavRoutes.MORNING_HRV`). Upon clearance (`onCleared()`), it SHALL detach `rrBeatListener` and cancel timers without calling `unbindFromBANALService()` on the shared singleton repository.
4. *Live Causal Filtering & Post-Hoc Centered Filter Invariant (`REQ-PER-032.4`)*: Raw RR intervals buffered during the measurement phase SHALL be processed post hoc upon completion using `RrArtifactFilter.filterBidirectional` (Lipponen & Tarvainen 2019 centered filter) as the single source of truth for persisted metrics (`rmssdMs`, `lnRmssd`, `hrvScore100`, `sdnnMs`, `restingHrBpm`, and `artifactRatePct`). During `STABILIZING` and `MEASURING`, incoming beats SHALL be evaluated via `RrArtifactFilter.filterLiveCausal` to display a live artifact percentage and trigger a high-artifact warning (> 5.0%) during countdown.
5. *Persistence, Pre-Save Readiness Preview & Baseline Feedback (`REQ-PER-032.5`)*: When measurement completes, the view SHALL display an in-memory pre-save `ReadinessStatus` calculated via `HrvReadinessCalculator.calculateReadiness(dateIso, posture, existingEntries + entry)` before persistence. When user confirms save, the measurement SHALL be persisted into `HrvReadinessRepository` (`HrvReadiness.db`). If `artifactRatePct > 5.0%`, the UI SHALL display a warning indicating potential electrode drying/poor contact.
6. *Localization & Theme Token Centralization (`REQ-PER-032.6`)*: All UI strings (error messages, beat count, readiness status labels, metrics formatters, saved button text) SHALL be localized across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) using locale-aware format specifiers without raw English strings or `Locale.US` hardcoding. All status labels SHALL use semantic theme tokens (`TTColor.ReadinessOnStatus`) without hardcoded `Color.White` literals.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-PER-032` (`REQ-PER-032.1`, `REQ-PER-032.3`, `REQ-PER-032.4`, `REQ-PER-032.5`, `REQ-PER-032.6`). Targets `MorningHrvCheckScreen.kt`, `MorningHrvCheckViewModel.kt`, `ATrainingTrackerApp.kt`, and `strings_hrv.xml`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-3556` (commit `dc034c08`); refined in `ATT-6719` under Epic `ATT-3539` (*Heart Rate Variability*).
3. *Root Reason for Existing Formulation*: Passive binding was used to avoid duplicate broadcasts; live artifact filtering was deferred to save CPU; ViewModel was assigned to `activity` out of convenience during navigation setup.
4. *Preservation of Core Invariants*: Zero workout generation, post-hoc centered filter calculation as single source of truth for persisted metrics, strict posture baseline isolation, 100% 9-language translation parity, and clean-room test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete launching `MorningHrvCheckScreen`,
• *When* the screen initializes,
• *Then* it displays the sticky last-used posture (defaulting to `SUPINE`), 60s standard duration in a segmented button row, and standardized waking checklist.
• *Given* an athlete launching Morning HRV directly after cold app startup,
• *When* the screen initializes,
• *Then* `BANALServiceRepository.startSearchingForPairedDevices()` is invoked and the display remains awake throughout stabilization and measurement countdowns.
• *Given* ectopic beats or poor sensor contact during measurement yielding $> 5.0\%$ artifacts,
• *When* beats are buffered live,
• *Then* `filterLiveCausal` updates live artifact percentage and triggers an amber warning during countdown.
• *Given* an active measurement session reaching 60s,
• *When* the measurement phase concludes,
• *Then* `filterBidirectional` evaluates the buffered RR intervals post hoc, computing `rmssdMs`, `lnRmssd`, `hrvScore100`, `sdnnMs`, `restingHrBpm`, and `artifactRatePct`, and today's evaluated readiness status is previewed in-memory against historical records prior to saving.
• *Given* user taps "Save Reading",
• *When* persisted,
• *Then* the measurement is stored in `HrvReadiness.db` without creating any workout sessions in `WorkoutSummaries.db`.

**Invariants**: Zero workout generation, post-hoc centered filter calculation, 100% clean-room test pass rate.

### REQ-PER-033: Longitudinal HRV Readiness Trend, Posture-Matched 60-Day SWC Corridor & Training Load Overlay [Verified]
- **Tests**: `TST-PER-041` | **Tickets**: `ATT-3557`, `ATT-6721`
- **Implementation**: `HrvReadinessTrendCard.kt`, `HrvReadinessTrendMath.kt`, `PeriodsScreen.kt`, `PeriodsTabsScreen.kt`, `PeriodsViewModel.kt`, `strings_hrv.xml`
- **Rationale**: Athletes tracking daily morning resting HRV require longitudinal visualization within the `PeriodsScreen` analytical hub to observe autonomic nervous system adaptation and recovery across days, weeks, and months in relation to training load (*Plews et al. 2013*). The system SHALL render an interactive longitudinal HRV trend visualization:
1. *Posture Isolation & Automatic Default (`REQ-PER-033.1`)*: The HRV trend card SHALL provide a posture selector chip row (`SUPINE`, `SEATED`, `STANDING`). To preserve baseline validity against orthostatic shifts, each posture's trend, rolling 7-day average, and 60-day baseline corridor SHALL compute strictly in isolation without mixing postures. When multiple postures have data, the initial selection SHALL default to the posture with the greatest number of recorded readings without eager pre-selection overriding the athlete's primary posture.
2. *Daily Scatter, 7-Day Rolling Trend & 60-Day SWC Corridor (`REQ-PER-033.2`)*: For the selected posture, the chart SHALL render daily primary resting $\ln(\text{rMSSD})$ (or $\text{rMSSD}$ in ms) points, a continuous 7-day rolling average curve ($\mu_{7d}$ when valid daily days $\ge 3$), and a shaded Smallest Worthwhile Change (SWC) baseline corridor $[\mu_{60d} - 0.5\sigma_{60d},\; \mu_{60d} + 0.5\sigma_{60d}]$ (when valid days $\ge 14$, Bessel's correction $N-1$). Points and 7-day rolling curve line segments SHALL reflect autonomic readiness classification colors (`OPTIMAL`, `FATIGUED_LOW`, `ABOVE_NORMAL`, `CALIBRATING`).
3. *Optional Training Load Context Overlay (`REQ-PER-033.3`)*: The card SHALL support toggling a synchronized training load overlay (daily training load / TSS bars or ATL/CTL curves derived from `PowerPhysiologyCalculator`) plotted on the shared time axis, enabling direct visual correlation between training volume/intensity and autonomic recovery excursions. Daily workout training-load bars SHALL be retained across all calendar days within the active time range, including days where no morning HRV reading was recorded.
4. *Touch Scrubbing & Gesture Disambiguation Invariant (`REQ-PER-033.4`)*: The chart surface SHALL support touch scrubbing/tap inspection to inspect exact daily values, 7d rolling average, and SWC bounds via tooltip overlay. In accordance with `design_guidelines.md` §2.2, touch interaction SHALL be governed by `ChartGestureDisambiguator` (`isDominantHorizontal` vs `isDominantVertical`) to ensure horizontal date scrubbing never traps or locks vertical parent list scrolling.
5. *Graceful Empty Omission Invariant (`REQ-PER-033.5`)*: When the athlete has zero morning HRV records across all postures in `HrvReadinessRepository`, the HRV readiness trend card SHALL be completely omitted from `PeriodsScreen`, avoiding empty cards and maintaining clean UI scannability.
6. *Axis Scaling & Rule 48 Outlier Resilience (`REQ-PER-033.6`)*: Canvas drawing SHALL render human-readable X-axis date labels and Y-axis scale markers using true calendar-day epoch spacing (`LocalDate.toEpochDay()`), preserving temporal gaps proportionally. Bottom-anchored training-load bars SHALL render with rounded top corners and sharp bottom corners (Rule 69). Numerical and date formatting SHALL be locale-aware, and tooltips SHALL use Material 3 inverse surface tokens.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-PER-033` (extending `REQ-PER-030` morning HRV persistence, `REQ-PER-032` guided waking check, and `REQ-UI-159` Periods screen). Targets `HrvReadinessTrendCard.kt`, `HrvReadinessTrendMath.kt`, `PeriodsScreen.kt`, `PeriodsTabsScreen.kt`, `PeriodsViewModel.kt`, and `strings_hrv.xml`.
2. *Historical Origin & Commit Trace*: Net-new capability introduced in `ATT-3557` (commit `4df73e2f`); refined in `ATT-6721` for true calendar epoch scaling, non-HRV load preservation, and readiness-colored rolling curve segments under Epic `ATT-3539` (*Heart Rate Variability*).
3. *Root Reason for Existing Formulation*: Previously, ordinal index spacing collapsed multi-day gaps; non-HRV workouts were discarded during HRV daily map filtering; 7d rolling line was drawn as a single monochrome primary path.
4. *Preservation of Core Invariants*: Existing `PeriodsScreen` tabs, sport filters, and spatial map navigation remain 100% untouched; strict posture isolation in SWC baseline math; zero empty UI clutter when no HRV data exists; touch scrubbing gesture disambiguation is strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* historical morning HRV entries stored in `HrvReadinessRepository`,
• *When* viewing `PeriodsScreen`,
• *Then* `HrvReadinessTrendCard` renders daily values, the 7-day rolling curve, and the shaded 60-day SWC corridor for the selected posture, with optional training-load context.
• *Given* an athlete with zero morning HRV entries,
• *When* opening `PeriodsScreen`,
• *Then* no empty HRV card is displayed, preserving unobstructed access to period summaries.
• *Given* an athlete scrubbing horizontally across the HRV trend chart,
• *When* dragging along the time axis,
• *Then* `ChartGestureDisambiguator` consumes horizontal drag for date tooltips while releasing vertical drag to parent list scrolling.

**Invariants**: Zero schema changes to `PeriodSummaries.db` or `HrvReadiness.db`, strict posture segregation in baseline math, 100% clean-room test pass rate.

### REQ-PER-034: Lossless WorkoutRrIntervals Persistence, Centered Recompute & Garmin FIT HrvMesg [Verified]
- **Tests**: `TST-PER-042` | **Tickets**: `ATT-3543`
- **Implementation**: `WorkoutRrDatabaseManager.kt`, `WorkoutRrInterval.kt`, `HrvPostWorkoutRecomputer.kt`, `FitFileWriter.java`, `FitWorkoutParser.kt`, `LegacyImportEngine.kt`, `TrackerService.java`
- **Rationale**: Live tracking calculates HRV metrics (DFA $\alpha_1$, $\text{rMSSD}$) using causal trailing windows with ~60s delay and discards raw beat-to-beat intervals upon session completion. Persisting raw beat intervals enables centered-window recomputation to eliminate phase lag and supports bidirectional Garmin FIT `HrvMesg` interoperability. The system SHALL support lossless RR persistence, centered recomputation, and FIT export/import:
1. *Lossless RR Persistence (`REQ-PER-034.1`)*: The system SHALL persist raw beat-to-beat intervals into `WorkoutRr.db` table `workout_rr_intervals` (`workout_id: Long`, `beat_index: Int`, `beat_timestamp_ms: Long`, `rr_ms: Float`, `artifact_flag: Boolean`, `gap_beats: Int`). Access SHALL be encapsulated in `WorkoutRrDatabaseManager`, registered in `DatabaseSchemaRegistry`, and cascaded on session deletion via `WorkoutDeletionHelper`.
2. *Monotonic Timestamp Reconstruction (`REQ-PER-034.2`)*: Live beats buffered from `RrBeatListener` in `TrackerService` SHALL accumulate timestamps monotonically as $t_k = t_{k-1} + \text{round}(\text{rr\_ms}_k)$ with wall-clock drift resynchronization when $|t_{\text{pkt}} - t_{\text{beat}}| > 2000\text{ ms}$, ensuring exact UTC epoch alignment with `WorkoutSamples`.
3. *Lag-Free Centered-Window Recomputation (`REQ-PER-034.3`)*: Upon workout finalization or FIT import, `HrvPostWorkoutRecomputer` SHALL evaluate centered-window DFA $\alpha_1$ ($t \pm 60\text{s}$) and $\text{rMSSD}$ ($t \pm 30\text{s}$) via `RrArtifactFilter.filterBidirectional`, updating `WorkoutSamples` and `WorkoutSummaries` session extrema.
4. *Garmin FIT Export Interleaving (`REQ-PER-034.4`)*: `FitFileWriter` SHALL write standard Garmin FIT `HrvMesg` records (packing up to 5 intervals in seconds) interleaved with `RecordMesg` trackpoints when RR data exists. Workouts without RR data SHALL omit `HrvMesg`.
5. *Garmin FIT Import & Timestamp Reconstruction (`REQ-PER-034.5`)*: `FitWorkoutParser` and `LegacyImportEngine` SHALL extract `HrvMesg` intervals, reconstruct timestamps anchored to session start UTC with pause resync, persist beats into `WorkoutRr.db`, and trigger post-workout recomputation.
6. *Non-HRV Workout Invariant & Asynchronous Execution (`REQ-PER-034.6`)*: Non-HRV workouts SHALL incur zero persistence overhead (0 rows, 0 `HrvMesg`). Recomputation SHALL execute asynchronously on `Dispatchers.IO` without delaying UI finalization or completion intents.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-PER-034` (extending live RR ingestion `REQ-TRA-018`, workout samples `REQ-TRA-002`, and FIT export/import `REQ-EXP-001`). Targets `WorkoutRrDatabaseManager.kt`, `WorkoutRrInterval.kt`, `HrvPostWorkoutRecomputer.kt`, `FitFileWriter.java`, `FitWorkoutParser.kt`, `LegacyImportEngine.kt`, and `TrackerService.java`.
2. *Historical Origin & Commit Trace*: Net-new capability introduced in `ATT-3543` under Epic `ATT-3539` (*Heart Rate Variability*).
3. *Root Reason for Existing Formulation*: Previously, raw beat-to-beat intervals were discarded at the end of tracking; live 1-Hz sample tables could not store high-frequency intervals, preventing centered post-workout signal recomputation and FIT HRV export.
4. *Preservation of Core Invariants*: Existing `WorkoutSamples` 1-Hz cadence and non-HRV workout flows remain 100% untouched; zero performance impact for non-HRV workouts; asynchronous non-blocking execution.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout recorded with a BLE/ANT+ RR sensor stream,
• *When* the workout is completed and finalized,
• *Then* raw beats are persisted in `WorkoutRr.db` and centered-window HRV metrics are recomputed and populated in `WorkoutSamples`.
• *Given* a workout with persisted RR intervals,
• *When* exported to Garmin FIT and imported into another instance,
• *Then* `HrvMesg` intervals are preserved losslessly and post-workout HRV streams recompute identically.
• *Given* a workout recorded without an RR sensor,
• *When* saved or exported to FIT,
• *Then* zero rows are inserted into `workout_rr_intervals` and zero `HrvMesg` messages are written.

**Invariants**: Raw RR interval values preserved losslessly unmodified alongside artifact flags, monotonic beat timestamps strictly anchored in UTC epoch milliseconds, zero overhead and 100% backward compatibility for non-HRV workouts.

### REQ-PER-035: Interactive Workout Details DFA a1/rMSSD Charts & a1 Scatter Plot [Verified]
- **Tests**: `TST-PER-043` | **Tickets**: `ATT-3544`
- **Implementation**: `TelemetryMetricGraph.kt`, `HrvAlpha1ScatterChart.kt`, `HrvAlpha1ScatterCard.kt`, `PathPoint.kt`, `WorkoutRepository.kt`, `MapDetailLayout.kt`
- **Rationale**: Workout Details visualizes continuous telemetry curves for Speed, HR, and Power, but lacks visualization for centered post-workout HRV telemetry. Athletes and coaches require interactive inspection of DFA $\alpha_1$ and $\text{rMSSD}$ time-series to assess autonomic recovery, monitor threshold transitions ($\alpha_1 \approx 0.75$ aerobic, $\alpha_1 \approx 0.50$ anaerobic), and identify decoupling via an $\alpha_1$ vs. HR/Power scatter plot while suppressing high-artifact intervals. The system SHALL support interactive HRV telemetry and scatter visualization:
1. *DFA $\alpha_1$ and rMSSD Telemetry Tracks (`REQ-PER-035.1`)*: `TelemetryMetricGraph` SHALL support `TelemetryMetricType.HRV_DFA_A1` and `TelemetryMetricType.HRV_RMSSD`. Curves SHALL share exact horizontal padding (`startPadding = 50.dp`) and scrubbing synchronization with existing telemetry graphs per Rule 64.
2. *Artifact Suppression & Gap Hatching (`REQ-PER-035.2`)*: Where `hrvDfaA1` is null or `hrvArtifactRate > 5.0%`, the polyline path SHALL break rather than interpolating across dropouts. An angled hatched pattern SHALL be rendered across suppressed intervals to delineate artifact dropout zones.
3. *Threshold Reference Lines & Clamping (`REQ-PER-035.3`)*: DFA $\alpha_1$ graphs SHALL render horizontal dashed threshold guide lines at $\alpha_1 = 0.75$ (Aerobic Threshold / HRVT1) and $\alpha_1 = 0.50$ (Anaerobic Threshold / HRVT2). Y-axis scaling SHALL be clamped to $[0.2, 1.4]$ with outlier resilience per Rule 48.
4. *$\alpha_1$ vs. Power/HR Scatter Plot (`REQ-PER-035.4`)*: Workout Details SHALL provide `HrvAlpha1ScatterCard` containing `HrvAlpha1ScatterChart` plotting intensity (Heart Rate in bpm or Cycling Power in Watts) on the X-axis against DFA $\alpha_1$ on the Y-axis across valid samples ($\alpha_1 \in [0.35, 1.15]$, artifact rate $\le 5\%$), with toggleable linear regression overlay.
5. *Graceful Omission for Non-HRV Workouts (`REQ-PER-035.5`)*: Workouts without valid HRV trackpoints SHALL completely omit the HRV telemetry tracks and scatter card with zero layout shifts or performance overhead.
6. *Touch Disambiguation & 9-Language Localization (`REQ-PER-035.6`)*: Horizontal scrubbing in HRV charts SHALL be disambiguated from parent vertical scrolling via `ChartGestureDisambiguator`. All labels and threshold descriptions SHALL support 100% localization parity across all 9 supported locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-PER-035` (extending telemetry visualization `REQ-UI-206` and post-workout analytics `REQ-PER-034`). Targets `TelemetryMetricGraph.kt`, `HrvAlpha1ScatterChart.kt`, `HrvAlpha1ScatterCard.kt`, `PathPoint.kt`, `WorkoutRepository.kt`, and `MapDetailLayout.kt`.
2. *Historical Origin & Commit Trace*: Net-new feature introduced in `ATT-3544` under Epic `ATT-3539` (*Heart Rate Variability*).
3. *Root Reason for Existing Formulation*: Previously, workout details only supported speed, HR, and power curves. HRV streams recomputed in `ATT-3543` remained stored in database samples without visual representation.
4. *Preservation of Core Invariants*: Existing telemetry graphs and non-HRV workouts remain 100% unaffected; horizontal padding alignment strictly preserved; zero snapshot state writes during composition (Rule 66).

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout with valid centered HRV samples,
• *When* inspecting telemetry graphs in Workout Details,
• *Then* DFA $\alpha_1$ and $\text{rMSSD}$ graphs are selectable with threshold guide lines ($\alpha_1 = 0.75, 0.50$) and high-artifact ($> 5\%$) intervals rendered as hatched gaps.
• *Given* a workout with valid DFA $\alpha_1$ and HR/Power data,
• *When* viewing the HRV threshold card in Workout Details,
• *Then* an interactive $(\text{HR/Power}, \alpha_1)$ scatter plot is rendered with a toggleable linear regression line.
• *Given* a workout recorded without an HRV sensor,
• *When* opening Workout Details,
• *Then* HRV graphs and the scatter card are gracefully omitted without error or blank cards.

**Invariants**: Pixel-perfect vertical alignment with `startPadding = 50.dp`, non-interpolated artifact gap rendering, zero UI presentation for non-HRV sessions, 100% 9-language localization parity.









