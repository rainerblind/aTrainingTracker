# Living Requirements: Post-Workout Analytics & Periods

**Document ID**: `DOC-REQ-PERIODS`  
**Version**: 1.0.45  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `aftermath_periods`  
**Prefixes**: `PER`, `PERF`, `RAD`, `SPT`  
**Scope**: Post-workout analytics, period statistics, zone distributions, graphs and visual scrubbers.  
**Total Requirements**: 75

---

### REQ-PER-007: Reactive Period-Workout Association [Verified]
- **Tests**: `TST-PERF-008`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PeriodsViewModel.kt`, `PeriodsRepository.kt`
- **Rationale**: The system SHALL ensure that all workouts belonging to a specific period are correctly and reactively associated when viewing period details (Map or List). The visualization SHALL automatically update as workout data is loaded or modified in the background, ensuring no sessions are omitted due to partial loading states.

### REQ-PER-008: Precise Period Filtering [Verified]
- **Tests**: `TST-PER-009`, `TST-UNT-012`, `TST-UNT-013`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PeriodsFragment.kt`, `WorkoutSummariesViewModel.kt`
- **Rationale**: The system SHALL correctly filter the workout list when navigating from a period summary highlight (Base Sport or Sub-Sport). This filtering MUST respect both the temporal bounds of the period and the specific sport identity selected to ensure professional analytical depth.

### REQ-PER-009: Detail Map Sport Filtering [Verified]
- **Tests**: `TST-PER-012`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PeriodMapScreen.kt`, `InteractivePeriodMap.kt`
- **Rationale**: Within the Period Detail view, the system SHALL allow filtering the map visualization by Sport Type. Tapping a sport summary SHALL toggle its visibility on the map, including tracks, markers, and heatmaps, ensuring a clean and focused analytical context.

### REQ-PER-010: Uniform Period Heatmap Visualization [Verified]
- **Tests**: `TST-PER-013`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `MapUtils.kt`, `PeriodMapUtils.kt`, `InteractivePeriodMap.kt`
- **Rationale**: The system SHALL render a period heatmap that represents all recorded workout paths across the entire temporal bounds of the period without spatial truncation. The heatmap overlay SHALL be translucent, keeping underlying map features, terrain, and labels legible. **Invariants**: The heatmap rendering SHALL NOT exceed the maximum memory point budget (`maxPoints`).

### REQ-PER-011: Longest Workout Metric Display [Verified]
- **Tests**: `TST-PER-014`, `TST-PER-015`, `TST-PER-016`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `PeriodsRepository.kt`, `PeriodSummaryCard.kt`, `PeriodSummariesDatabaseManager.kt`
- **Rationale**: When displaying the Longest Workout highlight within a sport summary, the system SHALL show the workout's name, active duration, total distance, and total ascent. Period summary card headers SHALL display total duration and workout count. **Invariants**: All displayed metrics SHALL be formatted according to the active localized unit settings.

### REQ-PER-012: Period Map Scalability, Memory Budget & Adaptive Layering Invariant [Verified]
- **Tests**: `TST-PER-018`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PeriodsViewModel.kt`, `PeriodMapScreen.kt`, `InteractivePeriodMap.kt`, `AndroidManifest.xml`
- **Rationale**: The system SHALL ensure high-performance, non-crashing spatial visualization across periods of arbitrary size (days, weeks, months, years with hundreds of workouts):
1. *Adaptive Layering Threshold*: When a period contains more than a configured safety threshold of member workouts (`MAX_VECTOR_WORKOUTS = 30`), the system SHALL NOT instantiate or render individual vector `Polyline`s (`memberTracks`) and individual member markers (`memberMarkers`) for all member workouts simultaneously. Instead, the spatial analytics SHALL render the high-performance raster `Heatmap` layer representing aggregate route density plus the prominent `anchorTracks` (the spatial boundary/longest routes) and period extrema markers (`extremaMarkers`).
2. *Interactive Single-Track Inspection*: When the athlete taps or peeks a specific workout within a large period, that single workout's path SHALL be rendered as a crisp, interactive vector polyline via `peekedTracks` without rendering all other background sessions as vector paths.
3. *Threshold-Filtered Detail*: When the number of displayed workouts is within the safety threshold ($\le 30$, such as in small periods or when filtered by sport type), individual member tracks and technical markers SHALL be rendered as detailed vector overlays.
4. *Memory & Point Budgeting*: In `PeriodsViewModel`, polyline decoding and `MapTrack` instantiation SHALL be budget-aware, avoiding redundant duplicate representations in memory.
5. *Process Heap Headroom*: The application manifest SHALL declare `android:largeHeap="true"` to grant the runtime sufficient memory bandwidth for GIS map overlays and raster heatmaps on high-resolution devices.

### REQ-PER-013: Multi-Region Period Map Viewport Detection & Adaptive Bounding [Verified]
- **Tests**: `TST-PER-019`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `SpatialRegionEngine.kt`, `PeriodsViewModel.kt`, `InteractivePeriodMap.kt`, `PeriodMapScreen.kt`, `PeriodSummaryCard.kt`
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

### REQ-PER-001: The system SHALL load and display the detailed visualization (Heatmap and List) [Implemented]
- **Tests**: `TST-PERF-002`, `TST-PERF-004`, `TST-BUG-002`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClustersViewModel.kt`, `WorkoutClusterRepository.kt`, `WorkoutSummariesDatabaseManager.java`, `WorkoutClusterHeatmapScreen.kt`
- **Rationale**: Ensure a responsive and professional user experience when analyzing recurring routes.
- **Description**: The system SHALL load and display the detailed visualization (Heatmap and List) of a Workout Cluster within 1 second for route families containing up to 50 sessions.

### REQ-PER-002: The system SHALL ensure that only the map state (Heatmap, Tracks, and Markers) o [Implemented]
- **Tests**: `TST-BUG-001`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Review
- **Implementation**: `WorkoutClustersViewModel.kt`
- **Rationale**: Guarantee data consistency and prevent visual artifacts during navigation.
- **Description**: The system SHALL ensure that only the map state (Heatmap, Tracks, and Markers) of the most recently selected Workout Cluster is displayed, preventing race conditions during rapid navigation.

### REQ-PER-003: The system SHALL display a detailed progress notification (Migration Status card [Verified]
- **Tests**: `TST-PERF-003`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClustersViewModel.kt`, `WorkoutClustersTabsScreen.kt`, `WorkoutClusterRepository.kt`
- **Rationale**: Provide immediate feedback and maintain visual consistency with other progressive loading screens (e.g. Periods).
- **Description**: The system SHALL display a detailed progress notification (Migration Status card) during the initial loading and "self-healing" phase of Workout Clusters to inform the user about the background processing state.

### REQ-PER-004: The hierarchical periods aggregation SHALL utilize a 'Dual-Phase' strategy: Phas [Verified]
- **Tests**: `TST-PERF-005`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PeriodsRepository.kt`
- **Rationale**: Eliminate database lock contention and O(N^2) re-grouping overhead to ensure sub-second startup and true real-time visibility.
- **Description**: The hierarchical periods aggregation SHALL utilize a 'Dual-Phase' strategy: Phase 1 (O(N)) for high-speed database reading and global grouping, and Phase 2 for bucketized commits and incremental UI pumping.

### REQ-PER-005: The 'Migration Status' notification SHALL provide a descriptive high-level title [Verified]
- **Tests**: `TST-PERF-006`
- **Priority**: Low
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `PeriodsRepository.kt`, `MigrationStatus.kt`, `PeriodsTabsScreen.kt`
- **Rationale**: Enhance user transparency and professional feedback during complex background operations.
- **Description**: The 'Migration Status' notification SHALL provide a descriptive high-level title (Introducing relational database) and explicitly label Phase 1 (Reading) and Phase 2 (Syncing) to provide clear technical context.

### REQ-PER-006: The system SHALL display a detailed, multi-phase progress notification during th [Verified]
- **Tests**: `TST-PERF-007`, `TST-PERF-009`, `TST-PERF-010`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterEngine.kt`, `WorkoutClusterRepository.kt`, `WorkoutClustersViewModel.kt`
- **Rationale**: Provide technical transparency and maintain consistency with other progressive background tasks.
- **Description**: The system SHALL display a detailed, multi-phase progress notification during the full recalculation or migration of Workout Clusters.

### REQ-PER-014: Immediate Period View Reactive Synchronization on Workout Finalization [Verified]
- **Tests**: `TST-PER-020`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PeriodsRepository.kt`, `PeriodsViewModel.kt`, `WorkoutSummariesDatabaseManager.java`
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
- **Tests**: `TST-PER-021`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `PeriodsRepository.kt`, `WorkoutDataMapper.kt`, `PowerPhysiologyCalculator.kt`
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
- **Tests**: `TST-PER-022`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `PeriodsRepository.kt`, `WorkoutRepository.kt`
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
- **Tests**: `TST-PER-023`, `TST-PER-025`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PeriodMapUtils.kt`, `PeriodSummaryCard.kt`, `PeriodMapVisualsTest.kt`
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
- **Tests**: `TST-PER-024`, `TST-PER-028`, `TST-PER-029`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `PeriodBarGraph.kt`, `PeriodBarGraphTest.kt`, `PeriodsTabsScreen.kt`
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
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
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
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
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
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
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
- **Tests**: `TST-PER-028`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
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
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
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
- **Tests**: `TST-PER-030`, `TST-PER-032`, `TST-PER-034`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
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
- **Tests**: `TST-PER-031`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
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
- **Tests**: `TST-PER-033`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
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
- **Tests**: `TST-PER-035`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
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
- **Tests**: `TST-PER-036`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
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
- **Tests**: `TST-PER-037`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
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
- **Tests**: `TST-PER-038`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
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
- **Tests**: `TST-PER-039`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `HealthConnectHrvImporter.kt`, `HealthConnectManager.kt`, `HrvPosture.kt`, `HrvReadinessRepository.kt`, `HealthConnectSettingsDialog.kt`, `AndroidManifest.xml`
- **Rationale**: Passive overnight wearable monitoring (smart rings, smartwatches) provides continuous nocturnal resting HRV and resting heart rate data via Android Health Connect. To allow athletes to utilize nocturnal biometric tracking without contaminating waking spot-check baselines, the system SHALL ingest nocturnal `HeartRateVariabilityRmssdRecord` and `RestingHeartRateRecord` into `HrvReadinessRepository` under an isolated `OVERNIGHT_WEARABLE` cohort:
1. *Permission & User Opt-In Gating (`REQ-PER-031.1`)*: The system SHALL declare `READ_HEART_RATE_VARIABILITY` and `READ_RESTING_HEART_RATE` permissions in `AndroidManifest.xml` and manage runtime permission checks through `HealthConnectManager.kt`. `hasHrvReadPermissions()` SHALL gate strictly on `HeartRateVariabilityRmssdRecord`, treating `RestingHeartRateRecord` as optional. Ingestion SHALL require explicit user opt-in (`isHealthConnectHrvImportEnabled`) configured in `HealthConnectSettingsDialog`.
2. *Nocturnal Log-Normal Multi-Sample Aggregation & Date Attribution (`REQ-PER-031.2`)*: When syncing, `HealthConnectHrvImporter` SHALL query nocturnal records in the target time window, paginating `readRecords` using `pageToken` loops until `pageToken == null` for both HRV and RHR queries. Records recorded between 18:00 and 12:00 SHALL be attributed to the waking calendar day `date_iso` (records before midnight attribute to the following day; records after midnight attribute to the current day). Because nocturnal rMSSD follows a log-normal distribution, multiple sample epochs within the nocturnal window SHALL be aggregated using the arithmetic mean of log values: $\text{ln\_rmssd} = \frac{1}{M}\sum \ln(\text{rMSSD}_i)$ and geometric mean $\text{rmssd\_ms} = \exp(\text{ln\_rmssd})$. Associated resting heart rate SHALL be aggregated as the arithmetic mean of valid nocturnal samples.
3. *Isolated Cohort Persistence (`REQ-PER-031.3`)*: The system SHALL extend `HrvPosture` with `OVERNIGHT_WEARABLE` and persist the aggregated measurement via `HrvReadinessRepository` with `artifact_rate_pct = null`. `HrvReadinessCalculator` SHALL evaluate `OVERNIGHT_WEARABLE` baselines in complete isolation from waking postures (`SUPINE`, `SEATED`, `STANDING`).
4. *Idempotent Ingestion & Source Tracking (`REQ-PER-031.4`)*: Ingestion SHALL be idempotent, deduplicating records by `(date_iso, OVERNIGHT_WEARABLE)` and persisting the source package and record UID in `notes`. When newer nocturnal samples or non-zero resting HR arrive for an unpinned `OVERNIGHT_WEARABLE` entry, the system SHALL update the entry in-place via `updateEntry()`. Subsequent import syncs SHALL NOT overwrite manually pinned entries or create duplicate rows.
5. *Preservation of System Invariants (`REQ-PER-031.5`)*: Standalone isolation of `OVERNIGHT_WEARABLE` baseline math, zero schema changes to `HrvReadiness.db` (v1), full `.attbackup` export/restore parity, and zero unprompted background sync operations when disabled.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-PER-031` (`REQ-PER-031.1`, `031.2`, `031.4`), targeting `HealthConnectHrvImporter.kt`, `HealthConnectManager.kt`, `HealthConnectSettingsDialog.kt`, `HrvPosture.kt`, and `HrvReadinessRepository.kt`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-4981` (commit `04c05e52`), refined in Sprint `2026-46.2` (`ATT-6712`).
3. *Root Reason for Existing Formulation*: Initial ATT-4981 delivery focused on core mathematical ingestion while settings toggles were deferred; RHR was coupled to HRV read permissions out of convenience; pagination was omitted for initial small fixtures.
4. *Preservation of Core Invariants*: Waking spot-check baselines (`SUPINE`, `SEATED`, `STANDING`) and nocturnal baselines (`OVERNIGHT_WEARABLE`) remain strictly isolated; manual check remains restricted to waking postures; zero SQLite schema migrations to `HrvReadiness.db` (v1); 100% clean-room test pass rate.

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
- **Tests**: `TST-PER-040`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `MorningHrvCheckScreen.kt`, `MorningHrvCheckViewModel.kt`, `HrvReadinessRepository.kt`, `RrArtifactFilter.kt`, `ATrainingTrackerApp.kt`, `strings_hrv.xml`
- **Rationale**: Accurate morning resting HRV evaluation requires standardized waking conditions (post-voiding, rested posture, normal breathing) without starting a full GPS workout. The system SHALL provide a dedicated Compose UI measurement screen (`MorningHrvCheckScreen`) and ViewModel (`MorningHrvCheckViewModel`) executing a guided waking protocol:
1. *Standardized Protocol Lifecycle & UI Ergonomics (`REQ-PER-032.1`)*: The measurement state machine SHALL transition through `READY` -> `STABILIZING` -> `MEASURING` -> `COMPLETED`. The stabilization phase SHALL default to 15–30s to allow autonomic balance. The measurement duration SHALL default to 60s (validated per Esco & Flatt 2014) with a selectable 120s extended option, rendered using Material 3 `SingleChoiceSegmentedButtonRow`. The scrollable column SHALL include `navigationBarsPadding()`, and the screen SHALL be kept awake (`keepScreenOn = true`) during `STABILIZING` and `MEASURING`.
2. *Posture Stickiness & Standardization (`REQ-PER-032.2`)*: The screen SHALL provide a posture selector for waking cohorts (`SUPINE`, `SEATED`, `STANDING`) and remember the athlete's last-used posture via SharedPreferences (`SP_HRV_LAST_POSTURE = "hrvLastPosture"`) across app sessions to preserve longitudinal baseline consistency.
3. *Sensor Ingestion, Cold-Start Discovery & Lifecycle Scoping (`REQ-PER-032.3`)*: Measurement SHALL ingest raw beat-to-beat intervals via `BANALServiceRepository` without launching `TrackerService` or creating rows in `WorkoutSummaries.db`. Upon screen entry and ViewModel initialization, `MorningHrvCheckViewModel` SHALL invoke `startSearchingForPairedDevices()` to discover paired BLE/ANT+ HR sensors immediately on cold launch. The ViewModel SHALL be scoped to `NavBackStackEntry` (`NavRoutes.MORNING_HRV`). Upon clearance (`onCleared()`), it SHALL detach `rrBeatListener` and cancel timers without calling `unbindFromBANALService()` on the shared singleton repository.
4. *Live Causal Filtering & Post-Hoc Centered Filter Invariant (`REQ-PER-032.4`)*: Raw RR intervals buffered during the measurement phase SHALL be processed post hoc upon completion using `RrArtifactFilter.filterBidirectional` (Lipponen & Tarvainen 2019 centered filter) as the single source of truth for persisted metrics (`rmssdMs`, `lnRmssd`, `hrvScore100`, `sdnnMs`, `restingHrBpm`, and `artifactRatePct`). During `STABILIZING` and `MEASURING`, incoming beats SHALL be evaluated via `RrArtifactFilter.filterLiveCausal` to display a live artifact percentage and trigger a high-artifact warning (> 5.0%) during countdown.
5. *Persistence, Pre-Save Readiness Preview & Baseline Feedback (`REQ-PER-032.5`)*: When measurement completes, the view SHALL display an in-memory pre-save `ReadinessStatus` calculated via `HrvReadinessCalculator.calculateReadiness(dateIso, posture, existingEntries + entry)` before persistence. When user confirms save, the measurement SHALL be persisted into `HrvReadinessRepository` (`HrvReadiness.db`). If `artifactRatePct > 5.0%`, the UI SHALL display a warning indicating potential electrode drying/poor contact.
6. *Localization, Waking Instruction Hygiene & Theme Token Centralization (`REQ-PER-032.6`)*: All UI strings (error messages, beat count, readiness status labels, metrics formatters, saved button text) SHALL be localized across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) using locale-aware format specifiers without raw English strings or `Locale.US` hardcoding. Waking protocol instruction text (`morning_hrv_instructions_desc`) SHALL emphasize morning rest upon waking, stillness, normal breathing without speaking, and posture consistency, without referring to bladder emptying or clinical voiding terms across all 9 locales. All status labels SHALL use semantic theme tokens (`TTColor.ReadinessOnStatus`) without hardcoded `Color.White` literals.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-PER-032` (`REQ-PER-032.1`, `REQ-PER-032.3`, `REQ-PER-032.4`, `REQ-PER-032.5`, `REQ-PER-032.6`). Targets `MorningHrvCheckScreen.kt`, `MorningHrvCheckViewModel.kt`, `ATrainingTrackerApp.kt`, and `strings_hrv.xml`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-3556` (commit `dc034c08`); refined in `ATT-6719` under Epic `ATT-3539` (*Heart Rate Variability*); instruction phrasing refined in `ATT-7739` (*Remove bladder emptying phrasing*).
3. *Root Reason for Existing Formulation*: Literal adaptation of Esco & Flatt (2014) clinical protocol specifications standardizing bladder state before morning orthostatic testing to avoid sympathetic bladder distention artifact, which was phrased literally in user-facing UI text.
4. *Preservation of Core Invariants*: Zero workout generation, post-hoc centered filter calculation as single source of truth for persisted metrics, strict posture baseline isolation, 100% 9-language translation parity, rest and stillness guidance, and clean-room test pass rate.

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
- **Tests**: `TST-PER-041`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `HrvReadinessTrendCard.kt`, `HrvReadinessTrendMath.kt`, `PeriodsScreen.kt`, `PeriodsTabsScreen.kt`, `PeriodsViewModel.kt`, `strings_hrv.xml`
- **Rationale**: Athletes tracking daily morning resting HRV require longitudinal visualization within the `PeriodsScreen` analytical hub to observe autonomic nervous system adaptation and recovery across days, weeks, and months in relation to training load (*Plews et al. 2013*). The system SHALL render an interactive longitudinal HRV trend visualization:
1. *Posture Isolation, Localization & Provenance Badge (`REQ-PER-033.1`)*: The HRV trend card SHALL provide a posture selector chip row supporting waking postures (`SUPINE`, `SEATED`, `STANDING`) and nocturnal passive monitoring (`OVERNIGHT_WEARABLE`). `OVERNIGHT_WEARABLE` SHALL be localized across all 9 supported locales in `strings_hrv.xml` (`morning_hrv_posture_overnight_wearable`), and entries originating from Health Connect SHALL render a source provenance badge parsed from `notes` (e.g. *"Health Connect (Oura / Fitbit / ...)"*). To preserve baseline validity against orthostatic shifts, each posture's trend, rolling 7-day average, and 60-day baseline corridor SHALL compute strictly in isolation without mixing postures. When multiple postures have data, the initial selection SHALL default to the posture with the greatest number of recorded readings without eager pre-selection overriding the athlete's primary posture. Manual posture selection in `MorningHrvCheckScreen` SHALL remain strictly restricted to waking postures.
2. *Daily Scatter, 7-Day Rolling Trend & 60-Day SWC Corridor (`REQ-PER-033.2`)*: For the selected posture, the chart SHALL render daily primary resting $\ln(\text{rMSSD})$ (or $\text{rMSSD}$ in ms) points, a continuous 7-day rolling average curve ($\mu_{7d}$ when valid daily days $\ge 3$), and a shaded Smallest Worthwhile Change (SWC) baseline corridor $[\mu_{60d} - 0.5\sigma_{60d},\; \mu_{60d} + 0.5\sigma_{60d}]$ (when valid days $\ge 14$, Bessel's correction $N-1$). Points and 7-day rolling curve line segments SHALL reflect autonomic readiness classification colors (`OPTIMAL`, `FATIGUED_LOW`, `ABOVE_NORMAL`, `CALIBRATING`).
3. *Optional Training Load Context Overlay (`REQ-PER-033.3`)*: The card SHALL support toggling a synchronized training load overlay (daily training load / TSS bars or ATL/CTL curves derived from `PowerPhysiologyCalculator`) plotted on the shared time axis, enabling direct visual correlation between training volume/intensity and autonomic recovery excursions. Daily workout training-load bars SHALL be retained across all calendar days within the active time range, including days where no morning HRV reading was recorded.
4. *Touch Scrubbing & Gesture Disambiguation Invariant (`REQ-PER-033.4`)*: The chart surface SHALL support touch scrubbing/tap inspection to inspect exact daily values, 7d rolling average, and SWC bounds via tooltip overlay. In accordance with `design_guidelines.md` §2.2, touch interaction SHALL be governed by `ChartGestureDisambiguator` (`isDominantHorizontal` vs `isDominantVertical`) to ensure horizontal date scrubbing never traps or locks vertical parent list scrolling.
5. *Graceful Empty Omission Invariant (`REQ-PER-033.5`)*: When the athlete has zero morning HRV records across all postures in `HrvReadinessRepository`, the HRV readiness trend card SHALL be completely omitted from `PeriodsScreen`, avoiding empty cards and maintaining clean UI scannability.
6. *Axis Scaling & Rule 48 Outlier Resilience (`REQ-PER-033.6`)*: Canvas drawing SHALL render human-readable X-axis date labels and Y-axis scale markers using true calendar-day epoch spacing (`LocalDate.toEpochDay()`), preserving temporal gaps proportionally. Bottom-anchored training-load bars SHALL render with rounded top corners and sharp bottom corners (Rule 69). Numerical and date formatting SHALL be locale-aware, and tooltips SHALL use Material 3 inverse surface tokens.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-PER-033` (`REQ-PER-033.1`), targeting `HrvReadinessTrendCard.kt`, `HrvReadinessTrendMath.kt`, `PeriodsScreen.kt`, and `strings_hrv.xml`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-3557` (commit `4df73e2f`), refined in `ATT-6721` and `ATT-6712` under Epic `ATT-3539` (*Heart Rate Variability*).
3. *Root Reason for Existing Formulation*: Previously, ordinal index spacing collapsed multi-day gaps; `OVERNIGHT_WEARABLE` fallback to raw enum was unlocalized; non-HRV workouts were discarded during HRV daily map filtering; 7d rolling line was drawn as a single monochrome primary path.
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
- **Tests**: `TST-PER-042`, `TST-TRK-085`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutRrDatabaseManager.kt`, `WorkoutRrDbHelper.kt`, `WorkoutRrRepository.kt`, `WorkoutRrInterval.kt`, `HrvPostWorkoutRecomputer.kt`, `FitFileWriter.java`, `FitWorkoutParser.kt`, `LegacyImportEngine.kt`, `TrackerService.java`
- **Rationale**: Live tracking calculates HRV metrics (DFA $\alpha_1$, $\text{rMSSD}$) using causal trailing windows with ~60s delay and discards raw beat-to-beat intervals upon session completion. Persisting raw beat intervals enables centered-window recomputation to eliminate phase lag and supports bidirectional Garmin FIT `HrvMesg` interoperability. The system SHALL support lossless RR persistence, centered recomputation, and FIT export/import:
1. *Lossless RR Persistence & Schema Integrity (`REQ-PER-034.1`)*: The system SHALL persist raw beat-to-beat intervals into `WorkoutRr.db` table `workout_rr_intervals` (`workout_id: Long`, `beat_index: Int`, `beat_timestamp_ms: Long`, `rr_ms: Float`, `artifact_flag: Int`, `gap_beats: Int`). The database schema SHALL enforce a `UNIQUE(workout_id, beat_index)` constraint with a v2 migration in `WorkoutRrDbHelper`, and data access SHALL be routed through `WorkoutRrRepository` and `WorkoutRrDatabaseManager`, registered in `DatabaseSchemaRegistry`, and cascaded on session deletion via `WorkoutDeletionHelper`.
2. *Incremental Batched Flushing & Monotonic Reconstruction (`REQ-PER-034.2`)*: Live beats from `RrBeatListener` in `TrackerService` SHALL accumulate timestamps monotonically as $t_k = t_{k-1} + \text{round}(\text{rr\_ms}_k)$ with wall-clock drift resynchronization when $|t_{\text{pkt}} - t_{\text{beat}}| > 2000\text{ ms}$. Ingestion SHALL be strictly gated by `mStatus == STATUS.TRACKING && mWorkoutID > 0`, ignoring pre-start and paused beats. Buffered beats SHALL be flushed incrementally in batched SQLite transactions every 30–60s or upon reaching 100 beats, and `mRrBeatIndexCounter` SHALL restore to `MAX(beat_index) + 1` upon workout resume.
3. *Two-Pointer 5s Grid Recomputation & Session SDNN (`REQ-PER-034.3`)*: Upon workout finalization or FIT import, `HrvPostWorkoutRecomputer` SHALL evaluate centered-window DFA $\alpha_1$ ($t \pm 60\text{s}$, $\le 5\%$ artifact gate) and $\text{rMSSD}$ ($t \pm 30\text{s}$) using an $O(N)$ two-pointer sliding window evaluated on a 5s grid (latching values to intermediate 1-Hz trackpoints) via `RrArtifactFilter.filterBidirectionalBeats`. It SHALL compute unbiased session `SDNN` across valid non-artifact intervals ($N \ge 2$) and update `WorkoutSummaries` extrema. Recomputation SHALL sequence strictly after `endWorkout()` summary finalization and dispatch `WorkoutRepository.refreshWorkout(workoutId)`.
4. *Garmin FIT Export Interleaving (`REQ-PER-034.4`)*: `FitFileWriter` SHALL write standard Garmin FIT `HrvMesg` records (packing up to 5 intervals in seconds) interleaved with `RecordMesg` trackpoints when RR data exists. Workouts without RR data SHALL omit `HrvMesg`.
5. *Garmin FIT Import & Interleaved Anchor Reconstruction (`REQ-PER-034.5`)*: `FitWorkoutParser` SHALL preserve chronological `RecordMesg` timestamp anchors associated with parsed `HrvMesg` intervals (`FitRrBeat(rrMs, anchorTimestampMs)`). `LegacyImportEngine` SHALL pass active anchor timestamps to `RrBeatTimestampReconstructor.reconstruct(anchorMs, rrMs)` to prevent false drift resets, reconstruct monotonic timestamps with real pause resync, persist beats into `WorkoutRr.db`, and trigger post-workout recomputation.
6. *Non-HRV Workout Invariant & Asynchronous Execution (`REQ-PER-034.6`)*: Non-HRV workouts SHALL incur zero persistence overhead (0 rows, 0 `HrvMesg`). Recomputation SHALL execute asynchronously on `Dispatchers.IO` without delaying UI finalization or completion intents.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-PER-034` (`REQ-PER-034.1`, `REQ-PER-034.2`, `REQ-PER-034.3`, `REQ-PER-034.5`), targeting `FitWorkoutParser.kt`, `LegacyImportEngine.kt`, `TrackerService.java`, `HrvPostWorkoutRecomputer.kt`, `WorkoutRrDbHelper.kt`, and `WorkoutRrRepository.kt`.
2. *Historical Origin & Commit Trace*: Introduced in Sprint `2026-46.1` (`ATT-3543`, commit `cb0d4fb7`).
3. *Root Reason for Existing Formulation*: Initial ATT-3543 prototype used a 2-second synthetic FIT test fixture that masked the constant `startMs` drift collapse; live recording deferred flushing to `endWorkout()` in RAM; recomputation re-filtered the entire beat array at 1 Hz, creating quadratic slowdown on multi-hour workouts.
4. *Preservation of Core Invariants*: 120s centered DFA $\alpha_1$ (with $\le 5\%$ artifact gate) and 60s centered rMSSD math remain strictly intact; 5s grid decimation exactly aligns with `ATT-3543` Scope §2; database upgrade to v2 strictly preserves existing recorded intervals without data loss; non-HRV workouts continue to incur zero overhead.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a multi-minute Garmin FIT file with interleaved `HrvMesg` records and pauses,
• *When* imported via `LegacyImportEngine`,
• *Then* beat timestamps accumulate monotonically across the session with pause resync, and centered-window DFA $\alpha_1$ / rMSSD are populated without collapsing into the first 2 seconds.
• *Given* an active workout recording with an RR sensor stream,
• *When* tracking progresses or the workout is paused/resumed,
• *Then* beats are flushed incrementally to `WorkoutRr.db` in batched transactions, non-tracking beats are ignored, and `mRrBeatIndexCounter` resumes without collisions.
• *Given* a completed workout with persisted RR intervals,
• *When* `HrvPostWorkoutRecomputer` runs,
• *Then* metrics evaluate via an $O(N)$ two-pointer sliding window on a 5s grid, session SDNN is saved in `WorkoutSummaries`, and `WorkoutRepository.refreshWorkout` is dispatched.

### REQ-PER-035: Interactive Workout Details DFA a1/rMSSD Charts & a1 Scatter Plot [Verified]
- **Tests**: `TST-PER-043`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TelemetryMetricGraph.kt`, `HrvAlpha1ScatterChart.kt`, `HrvAlpha1ScatterCard.kt`, `PathPoint.kt`, `WorkoutRepository.kt`, `MapDetailLayout.kt`, `WorkoutDetailTabbedContent.kt`
- **Rationale**: Workout Details visualizes continuous telemetry curves for Speed, HR, and Power, but lacks visualization for centered post-workout HRV telemetry. Athletes and coaches require interactive inspection of DFA $\alpha_1$ and $\text{rMSSD}$ time-series to assess autonomic recovery, monitor threshold transitions ($\alpha_1 \approx 0.75$ aerobic, $\alpha_1 \approx 0.50$ anaerobic), and identify decoupling via an $\alpha_1$ vs. HR/Power/Pace scatter plot while suppressing high-artifact intervals. The system SHALL support interactive HRV telemetry and scatter visualization:
1. *DFA $\alpha_1$ and rMSSD Telemetry Tracks (`REQ-PER-035.1`)*: `TelemetryMetricGraph` SHALL support `TelemetryMetricType.HRV_DFA_A1` and `TelemetryMetricType.HRV_RMSSD`. Curves SHALL share exact horizontal padding (`startPadding = 50.dp`) and scrubbing synchronization with existing telemetry graphs per Rule 64. Scrubbing lookup for DFA $\alpha_1$ and rMSSD in `MapDetailLayout` SHALL evaluate `isTimeDomain = (activeTelemetryDomain == ProfileXAxisDomain.TIME)` to display accurate nearest-point metrics on GPS workouts.
2. *Artifact Suppression & Gap Hatching (`REQ-PER-035.2`)*: Where `hrvDfaA1` is null or `hrvArtifactRate > 5.0%`, the polyline path SHALL break rather than interpolating across dropouts. An angled hatched pattern SHALL be rendered across suppressed intervals to delineate artifact dropout zones.
3. *Threshold Reference Lines & Clamping (`REQ-PER-035.3`)*: DFA $\alpha_1$ graphs SHALL render horizontal dashed threshold guide lines at the athlete's configured $\alpha_1$ thresholds (`dfaA1ThresholdT1` and `dfaA1ThresholdT2` from `TuningPreferencesDataStore`, defaulting to 0.75 and 0.50). Y-axis scaling SHALL be clamped to $[0.2, 1.4]$ with outlier resilience per Rule 48.
4. *$\alpha_1$ vs. Intensity Scatter Plot & Deduplication (`REQ-PER-035.4`)*: Workout Details SHALL render `HrvAlpha1ScatterCard` strictly once (canonical placement under `WorkoutDetailSectionType.ZONES`), containing `HrvAlpha1ScatterChart` plotting intensity (Heart Rate in bpm, Cycling Power in Watts, or Running Pace in min/km formatted via `PaceFormatter`) on the X-axis against DFA $\alpha_1$ on the Y-axis across valid 5-second decimated samples ($\alpha_1 \in [0.35, 1.15]$, artifact rate $\le 5\%$), with athlete-configured T1/T2 threshold reference lines and regression crossing estimates. The intensity mode switcher SHALL use Material 3 `SingleChoiceSegmentedButtonRow` keyed dynamically via `rememberSaveable(hasHr, hasPower, hasPace)`.
5. *Graceful Omission for Non-HRV Workouts (`REQ-PER-035.5`)*: Workouts without valid HRV trackpoints SHALL completely omit the HRV telemetry tracks and scatter card with zero layout shifts or performance overhead.
6. *Touch Disambiguation & 9-Language Localization (`REQ-PER-035.6`)*: Horizontal scrubbing in HRV charts SHALL be disambiguated from parent vertical scrolling via `ChartGestureDisambiguator`. All labels and threshold descriptions SHALL support 100% localization parity across all 9 supported locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-PER-035` (extending telemetry visualization `REQ-UI-206` and post-workout analytics `REQ-PER-034`). Targets `TelemetryMetricGraph.kt`, `HrvAlpha1ScatterChart.kt`, `HrvAlpha1ScatterCard.kt`, `PathPoint.kt`, `WorkoutRepository.kt`, and `MapDetailLayout.kt`.
2. *Historical Origin & Commit Trace*: Net-new feature introduced in `ATT-3544` (commit `3db57718`); refined in `ATT-6717` under Epic `ATT-3539` (*Heart Rate Variability*).
3. *Root Reason for Existing Formulation*: Previously, workout details only supported speed, HR, and power curves. HRV streams recomputed in `ATT-3543` remained stored in database samples without visual representation. Initial ATT-3544 implementation hardcoded population thresholds and FilterChips without running pace support, and inadvertently rendered duplicate cards in MapDetailLayout and WorkoutDetailTabbedContent.
4. *Preservation of Core Invariants*: Existing telemetry graphs and non-HRV workouts remain 100% unaffected; horizontal padding alignment strictly preserved; zero snapshot state writes during composition (Rule 66); population defaults (0.75 and 0.50) strictly preserved when custom preferences are absent.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout with valid centered HRV samples,
• *When* inspecting telemetry graphs in Workout Details,
• *Then* DFA $\alpha_1$ and $\text{rMSSD}$ graphs are selectable with configured threshold guide lines and high-artifact ($> 5\%$) intervals rendered as hatched gaps.
• *Given* a workout with valid DFA $\alpha_1$ and HR/Power/Pace data,
• *When* viewing the HRV threshold card in Workout Details,
• *Then* an interactive $(\text{HR/Power/Pace}, \alpha_1)$ scatter plot is rendered strictly once under `ZONES` with Material 3 `SingleChoiceSegmentedButtonRow` and 5s-decimated samples.
• *Given* a workout recorded without an HRV sensor,
• *When* opening Workout Details,
• *Then* HRV graphs and the scatter card are gracefully omitted without error or blank cards.

**Invariants**: Pixel-perfect vertical alignment with `startPadding = 50.dp`, non-interpolated artifact gap rendering, zero UI presentation for non-HRV sessions, 100% 9-language localization parity.

### REQ-PER-036: Confidence-Gated HRVT1/HRVT2 Threshold Estimation & Summary Card [Verified]
- **Tests**: `TST-PER-044`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `HrvThresholdEstimator.kt`, `HrvThresholdSummaryCard.kt`, `WorkoutDetailTabbedContent.kt`, `strings_hrv.xml`
- **Rationale**: While `HrvAlpha1ScatterCard` (`REQ-PER-035`) renders unconstrained scatter points across the entire workout, athletes require physiologically validated aerobic ($HRVT_1$, $\alpha_1 = 0.75$) and anaerobic ($HRVT_2$, $\alpha_1 = 0.50$) threshold estimates with lag compensation, fresh-window fatigue bounding, decimated-grid statistical validity, and explicit quality confidence classification. The system SHALL support confidence-gated threshold estimation and presentation:
1. *Fresh-Window Telemetry Selection (`REQ-PER-036.1`)*: For free progression workouts, `HrvThresholdEstimator` SHALL restrict threshold regression strictly to active elapsed time $t \in [600, 3600]\text{ s}$ (`10–60 min`), excluding the initial 10-minute warm-up, paused intervals, late-workout cardiac fatigue drift ($>60\text{ min}$), and samples where artifact rate exceeds $5.0\%$. For workouts containing a guided step or continuous ramp test (`ATT-4969`), `HrvThresholdEstimator` SHALL evaluate the post-warmup monotonic test stage.
2. *Decimated Grid Regression & Serial Decorrelation (`REQ-PER-036.2`)*: To prevent artificial $R^2$ inflation and serial autocorrelation from 5-second samples, regression and goodness-of-fit $R^2$ SHALL be computed on decimated 60-second window averages ($N_{\text{eff}} = \lfloor K / 12 \rfloor$) for free progression and continuous ramps, or discrete step averages for guided step tests. For continuous ramps, a Mean Response Time lag compensation of $\tau_{\text{MRT}} = 45\text{ s}$ SHALL be subtracted from observed step/intensity timestamps before threshold mapping.
3. *Independent Span Gating for $HRVT_1$ and $HRVT_2$ (`REQ-PER-036.3`)*: $HRVT_1$ ($\alpha_{1,\text{T1}} = 0.75$) regression requires decimated samples spanning both $\ge 0.85$ and $\le 0.65$. $HRVT_2$ ($\alpha_{1,\text{T2}} = 0.50$) regression requires decimated samples spanning both $\ge 0.60$ and $\le 0.45$. If only one threshold's span criteria are met, that threshold SHALL be emitted independently while the other threshold SHALL evaluate to `null` (`INSUFFICIENT_SPAN`), strictly prohibiting out-of-span linear extrapolation.
4. *Unified Three-Tier Confidence Classification (`REQ-PER-036.4`)*:
   - `HIGH`: Threshold span satisfied, $R^2 \ge 0.70$ on decimated grid, mean artifact rate $\le 3.0\%$, and $N_{\text{eff}} \ge 10$ decimated points ($\ge 10\text{ min}$) or $\ge 5$ valid steps for `GUIDED_STEP`.
   - `MEDIUM`: Threshold span satisfied, $R^2 \ge 0.55$ on decimated grid, mean artifact rate $\le 5.0\%$, and $N_{\text{eff}} \ge 8$ decimated points ($\ge 8\text{ min}$) or $\ge 4$ valid steps for `GUIDED_STEP`.
   - `LOW` / `INSUFFICIENT_SPAN`: Sessions failing `MEDIUM` criteria; classified as informational-only with explicit diagnostic reasoning (e.g., steady endurance ride, high artifact rate, insufficient duration, low correlation) and excluded from automated zone updating.
5. *Presentation via `HrvThresholdSummaryCard` (`REQ-PER-036.5`)*: The card SHALL be rendered in `WorkoutDetailTabbedContent.kt` under `WorkoutDetailSectionType.ZONES`. The card SHALL display estimated $HRVT_1$ and $HRVT_2$ values (Heart Rate in bpm, Steady-State Power in Watts, Pace formatted via `PaceFormatter`), protocol provenance badge, confidence badge, statistical summary ($R^2$, $N_{\text{eff}}$, mean artifact %), and diagnostic explanations for suppressed/unestimated thresholds.
6. *9-Language Localization & Graceful Omission (`REQ-PER-036.6`)*: All badge labels, diagnostic reasons, and metric headers SHALL support 100% localization parity across all 9 supported locales. For non-HRV sessions or workouts with zero valid HRV trackpoints, `HrvThresholdSummaryCard` SHALL be completely omitted with zero layout shifts.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout where centered-window DFA $\alpha_1$ spans from 1.05 down to 0.60 (crossing $HRVT_1 = 0.75$, but failing to reach 0.45),
• *When* `HrvThresholdEstimator` evaluates the session,
• *Then* it emits a valid $HRVT_1$ estimate and returns $HRVT_2 = \text{null}$ (`INSUFFICIENT_SPAN`) rather than extrapolating $HRVT_2$ outside the observed range.
• *Given* a multi-hour endurance workout exceeding 60 minutes with late-session cardiac drift,
• *When* `HrvThresholdEstimator` runs,
• *Then* regression is strictly evaluated over active elapsed time $t \in [600, 3600]\text{ s}$, preventing downward threshold corruption from late-ride fatigue.
• *Given* a workout rendered in Workout Details,
• *When* `HrvThresholdSummaryCard` is displayed,
• *Then* it renders the confidence badge (`HIGH`, `MEDIUM`, `LOW`, or `INSUFFICIENT_SPAN`), protocol badge, $R^2$, $N_{\text{eff}}$, artifact %, and localized diagnostic reasons across all 9 locales.

**Invariants**: Zero out-of-span linear extrapolation; 60s decimation decorrelation; 100% 9-language localization parity; zero card rendering for non-HRV workouts.

### REQ-RAD-005: Workout Traffic Persistence, Session Totals & Aftermath Summary Card [Verified]
- **Tests**: `TST-RAD-005`
- **System Requirement(s)**: `SYSREQ-TRK-006`, `SYSREQ-TRK-007`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutExportHeader.kt`, `WorkoutRepository.kt`, `WorkoutSummariesDatabaseManager.java`, `RadarTrafficSummaryCard.kt`, `WorkoutDetailTabbedContent.kt`, `strings.xml`
- **Rationale**: Cyclists reviewing rides recorded with cycling rearview radar require post-workout traffic analytics detailing total vehicles passed, traffic density, and peak vehicle speeds. The system SHALL persist 1 Hz scalar radar samples, compute session traffic extrema, and render a dedicated Traffic Summary Card in Workout Details with 100% 9-language localization parity without requiring database schema migrations.
- **Description**:
  1. *Sample Streaming & Persistence*: `WorkoutSampleRecord` in `WorkoutExportHeader.kt` and `WorkoutRepository.streamSamples()` SHALL expose latched 1 Hz radar metrics (`radarThreatCount: Int?`, `radarClosestDistanceM: Double?`, `radarApproachSpeedMps: Double?`, `radarVehicleCount: Int?`) populated from `WorkoutSamples.db` with zero SQLite schema migrations (`DB_VERSION` unchanged).
  2. *Session Traffic Extrema*: The system SHALL aggregate session traffic extrema (`RadarTrafficSummary`): total vehicles passed, traffic density (vehicles/km and vehicles/hour), max approach speed, max absolute vehicle speed ($v_{\text{rider}} + v_{\text{approach}}$), and high-threat passes.
  3. *Aftermath Traffic Summary Card*: `RadarTrafficSummaryCard.kt` SHALL render conditionally in `WorkoutDetailTabbedContent.kt` (`DETAILS`) when radar data is present. The card SHALL display key metrics (total vehicles, density per km/h, max closing speed, high-threat passes) localized across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT). Non-radar workouts SHALL omit the card with zero layout shifts or performance overhead.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout recorded with active radar telemetry,
• *When* the workout is opened in Workout Details,
• *Then* `RadarTrafficSummaryCard` renders total vehicles, traffic density, and peak speeds in the active locale.
• *Given* a workout recorded without a radar sensor,
• *When* opening Workout Details,
• *Then* `RadarTrafficSummaryCard` is gracefully omitted without layout shift or error.

**Invariants**: Zero SQLite schema migration; 100% 9-language localization parity; zero overhead or UI display for non-radar rides.


### REQ-PER-037: HRV Threshold Training Zone Comparison, Multi-Session Consensus Gating & Anchor-Based Power/HR Zone Updates [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-PER-045`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `HrvZoneComparisonMath.kt`, `HrvZoneComparisonResult.kt`, `HrvThresholdSummaryCard.kt`, `ZoneCalculationMath.kt`, `strings_hrv.xml`
- **Rationale**: While `REQ-PER-036` (`ATT-3554`) produces confidence-gated physiological threshold estimates ($HRVT_1$ and $HRVT_2$) and `REQ-TRK-072` (`ATT-4969`) introduces guided step/ramp tests, athletes currently lack contextual comparison against their configured training zones (`ZoneCalculationMath.kt`). Moreover, updating athletic training zones requires strict physiological safeguards: power zones can be updated from threshold estimates with user confirmation, but only when verified via a `Confidence.HIGH` guided test or a 30-day multi-session consensus ($M \ge 3$ agreeing within $\pm 10\text{ W}$), whereas heart rate zones remain purely informational comparisons because submaximal thresholds cannot observe $HR_{\max}$ and legacy HR zones are anchored to $HR_{\max}$. The system SHALL support training zone comparison, multi-session consensus gating, and anchor-based power zone updates:
- **Description**:
  1. *Power Zone Delta & Coggan Anchor Comparison (`REQ-PER-037.1`)*:
     - The system SHALL compare estimated $HRVT_1$ steady-state power ($P_{\text{HRVT1,ss}}$) against the Coggan Zone 2 aerobic ceiling (`z2Max = round(0.75 * ftp)`), computing $\Delta P_{\text{T1}} = P_{\text{HRVT1,ss}} - z2Max$.
     - The system SHALL compare estimated $HRVT_2$ steady-state power ($P_{\text{HRVT2,ss}}$) against configured Functional Threshold Power (`100% FTP`), computing $\Delta P_{\text{T2}} = P_{\text{HRVT2,ss}} - FTP$.
     - If either threshold is unavailable or unestimated (`INSUFFICIENT_SPAN`), its respective power delta SHALL be omitted.
  2. *Informational Heart Rate Comparison & Non-Overwrite Invariant (`REQ-PER-037.2`)*:
     - The system SHALL compare estimated $HRVT_1$ heart rate ($\text{HR}_{\text{HRVT1}}$) against configured Heart Rate `z2Max`, computing $\Delta \text{HR}_{\text{T1}} = \text{HR}_{\text{HRVT1}} - z2Max$, and expressing $\text{HR}_{\text{HRVT1}}$ as a percentage of $HR_{\max}$ ($\% HR_{\max} = \text{round}(100 \times \text{HR}_{\text{HRVT1}} / HR_{\max})$).
     - The system SHALL compare estimated $HRVT_2$ heart rate ($\text{HR}_{\text{HRVT2}}$) against configured Heart Rate `z4Max`, computing $\Delta \text{HR}_{\text{T2}} = \text{HR}_{\text{HRVT2}} - z4Max$, and expressing $\text{HR}_{\text{HRVT2}}$ as $\% HR_{\max}$.
     - Heart rate comparisons SHALL be strictly informational and accompanied by an explanatory tooltip/hint clarifying that physiological $HRVT_1$ typically occurs at $75\text{--}82\% HR_{\max}$ in trained athletes, and existing HR zones in Settings are anchored to $HR_{\max}$. Under no circumstances SHALL the system automatically overwrite Heart Rate zones or $HR_{\max}$.
  3. *Multi-Session Consensus & Guided Test Gating (`REQ-PER-037.3`)*:
     - Automated or one-tap updating of Power Zones (`FTP`) SHALL be gated by a consensus evaluator (`HrvZoneComparisonMath.evaluateConsensusGate`).
     - Gating condition for UNLOCKED:
       - Condition A (Guided Test): The session is a `Confidence.HIGH` Guided Step or Continuous Ramp Test (`ATT-4969`), OR
       - Condition B (Multi-Session Consensus): At least $M \ge 3$ recent workouts within the past 30 days have `Confidence.HIGH` or `Confidence.MEDIUM` threshold estimates whose $HRVT_2$ power values agree within a range of $\le 10\text{ W}$ ($\max(P) - \min(P) \le 10\text{ W}$ across the qualifying sessions), with the suggested FTP calculated as the statistical median of these session estimates.
     - Single unverified free workouts, workouts with `Confidence.LOW`, workouts with insufficient duration/span, or disparate sessions ($> 10\text{ W}$ spread) SHALL evaluate to `LOCKED`.
  4. *Zone Comparison Section in `HrvThresholdSummaryCard` (`REQ-PER-037.4`)*:
     - `HrvThresholdSummaryCard` SHALL display a dedicated Training Zones Comparison section beneath threshold metrics when configured zones are present.
     - The section SHALL render power and HR comparison delta chips, consensus status badge (`LOCKED` or `UNLOCKED`), and an "Update Power Zones" action button.
     - When locked, the button SHALL be disabled with an inline diagnostic reason (e.g., *"Requires High-Confidence Guided Test or 3 agreeing sessions within 30 days"*).
  5. *User-Confirmed Power Zone Update Flow (`REQ-PER-037.5`)*:
     - When unlocked, tapping "Update Power Zones" SHALL launch an explicit modal confirmation dialog.
     - The confirmation dialog SHALL display the target new FTP value, protocol provenance (`3-min Step`, `Continuous Ramp`, or `Multi-Session Median`), ramp slope & MRT adjustment if applicable, and an explicit MLSS cautionary advisory noting that VT2/RCP may sit 3–5% above 60-minute MLSS.
     - Upon explicit athlete confirmation, the system SHALL update `SettingsDataStore.setFtp(newFtp)` and recalculate `calculateCogganPowerZones(newFtp)`.
  6. *Cross-Epic Mader-Heck Calibration Forwarding & Localization Parity (`REQ-PER-037.6`)*:
     - The card SHALL provide an action link forwarding the validated $(P_{\text{HRVT1}}, P_{\text{HRVT2}})$ anchor pair into `MaderCalibrationDialog` (`ATT-4799`).
     - All delta labels, consensus badges, dialog texts, tooltips, and explanatory messages SHALL maintain 100% localization parity across all 9 supported locales in `strings_hrv.xml`.
  7. *Anchor-Based Heart Rate Training Zone Updates from HRVT1/HRVT2 Consensus (`REQ-PER-037.7`)*:
     - The system SHALL calculate 5 heart rate training zone boundaries anchored to physiological thresholds via `ZoneCalculationMath.calculateThresholdAnchoredHeartRateZones(hrvt1, hrvt2, hrMax, hrRest)`:
       - Zone 2 aerobic threshold ceiling SHALL anchor strictly at $HRVT_1$ (`z2Max = hrvt1`).
       - Zone 4 anaerobic threshold / LTHR ceiling SHALL anchor strictly at $HRVT_2$ (`z4Max = hrvt2`).
       - Zone 3 ceiling SHALL be the physiological midpoint $\text{round}((hrvt1 + hrvt2) / 2.0)$.
       - Zone 1 ceiling SHALL be $\text{round}(hrRest + 0.70 \times (hrvt1 - hrRest))$ when $hrRest < hrvt1$, otherwise $\text{round}(0.85 \times hrvt1)$.
       - Boundary rationalization SHALL strictly guarantee integer monotonicity ($Z_1 < Z_2 < Z_3 < Z_4 < HR_{\max}$).
     - Automated or one-tap updating of Heart Rate Zones SHALL be gated by `HrvZoneComparisonMath.evaluateHrConsensusGate`. The gate SHALL unlock (`ConsensusGateStatus.UNLOCKED`) if EITHER:
       - Condition A (Guided Test): The session is a `Confidence.HIGH` Guided Step or Ramp Test (`ATT-4969`) with valid $(HRVT_1, HRVT_2)$ where $HRVT_2 \ge HRVT_1 + 5\text{ bpm}$, OR
       - Condition B (Multi-Session Consensus): At least $M \ge 3$ recent workouts within 30 days have `Confidence.HIGH` or `Confidence.MEDIUM` HR threshold estimates whose $HRVT_1$ and $HRVT_2$ values each agree within $\le 6\text{ bpm}$ ($\max - \min \le 6\text{ bpm}$), with suggested anchors calculated as the statistical median of each threshold series.
     - `HrvThresholdSummaryCard` SHALL display an "Update HR Zones" action button. When unlocked, tapping the button SHALL launch a modal confirmation dialog displaying current vs. target $Z_2$ and $Z_4$ boundaries, protocol provenance, and noting that existing manual custom bounds will be replaced while preserving the ability to manually tweak them in Zone Settings.
     - Upon explicit athlete confirmation, the system SHALL persist the updated thresholds via `SettingsDataStore.saveCalculatedHeartRateZones(zoneType, hrMax, hrRest, method = "HRVT_ANCHORS", cutoffs)` and update the current aftermath UI state.
 (ATT-3555: [Verbesserung] Compare estimated HRVT1/HRVT2 against configured training zones with multi-session consensus and anchor-based updates (ZoneCalculationMath))
 (ATT-7310: [Feature] Enable anchor-based Heart Rate training zone updates from HRVT1/HRVT2 consensus)

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-PER-037` (*HRV Threshold Training Zone Comparison, Multi-Session Consensus Gating & Anchor-Based Power Zone Updates*), extending `REQ-PER-036` (confidence-gated thresholds) and integrating with `REQ-UI-373` (`ZoneCalculationMath.kt`). Targets `HrvZoneComparisonMath.kt`, `HrvThresholdSummaryCard.kt`, `SettingsDataStore.kt`, and `strings_hrv.xml`.
2. *Historical Origin & Commit Trace*: Conceived in Epic `ATT-3539` (*Heart Rate Variability*); companion to `ATT-3554` (commit `5deec7cd`) and `ATT-4969` (commit `424580a2`).
3. *Root Reason for Existing Formulation*: `ZoneCalculationMath` previously supported only manual calculator entry without automated physiological anchor feedback. Directly overwriting FTP from a single noisy free workout risks distorting training zones, while modifying HR zones from submaximal thresholds is physiologically invalid because $HR_{\max}$ cannot be observed without exhaustive testing.
4. *Preservation of Core Invariants*: Integer monotonicity ($Z_n \ge Z_{n-1} + 1$) and 2500 W ceiling in `ZoneCalculationMath` strictly preserved; HR zones remain anchored strictly to $HR_{\max}$; no automated overwrites occur without explicit athlete confirmation dialog; 100% 9-language localization parity maintained.
5. *Chesterton's Fence Audit (REQ-PER-037.2 & REQ-PER-037.7 - ATT-7310)*:
   - *Original Requirement ID & Target*: `REQ-PER-037.2` (Informational HR Comparison & Non-Overwrite Invariant) and net-new `REQ-PER-037.7` in `docs/requirements/req_aftermath_periods.md`.
   - *Historical Origin & Commit Trace*: `ATT-3555` (commit `a216f4a3`).
   - *Root Reason for Existing Formulation*: Protected athletes against erroneous silent overwriting of HR zones or $HR_{\max}$ from single uncalibrated workouts or noisy submaximal data.
   - *Preservation of Core Invariants*: Silent background overwrites remain strictly prohibited; updates require explicit athlete confirmation via modal dialog and are gated behind high-confidence guided tests or 30-day multi-session consensus ($M \ge 3$, spread $\le 6\text{ bpm}$); full support for subsequent manual customization in Zone Settings is preserved; integer monotonicity ($Z_1 < Z_2 < Z_3 < Z_4 < HR_{\max}$) is mathematically guaranteed.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout with estimated $HRVT_1 = 146\text{ bpm} / 215\text{ W}$ and $HRVT_2 = 172\text{ bpm} / 285\text{ W}$, and configured $\text{FTP} = 280\text{ W}$ (`z2Max = 210 W`) and HR `z2Max = 142 bpm`, `z4Max = 170 bpm`,
• *When* `HrvThresholdSummaryCard` is displayed,
• *Then* it shows the deltas against `z2Max` ($+4\text{ bpm}$, $+5\text{ W}$) and against $\text{FTP}$ / HR `z4Max` ($+2\text{ bpm}$, $+5\text{ W vs. FTP}$).
• *Given* a single `Confidence.MEDIUM` free-ride estimate (where fewer than $M = 3$ consistent sessions exist within 30 days and the workout is not a `Confidence.HIGH` Guided Step/Ramp Test),
• *When* displayed on `HrvThresholdSummaryCard`,
• *Then* Power and HR comparison deltas are shown informationally, the "Update Power Zones" and "Update HR Zones" buttons are disabled/locked with an explanatory reason, and HR zones are never overwritten.
• *Given* an unlocked consensus state (e.g. `Confidence.HIGH` Guided Test or 3 agreeing sessions within $\pm 10\text{ W}$),
• *When* the athlete taps "Update Power Zones",
• *Then* a confirmation dialog appears showing target FTP, protocol provenance, ramp slope & MRT adjustment, and an MLSS warning; upon user confirmation, FTP is saved to settings and power zones are updated.
• *Given* an unlocked heart rate consensus state (e.g. `Confidence.HIGH` Guided Test or 3 agreeing sessions within $\le 6\text{ bpm}$ with median $HRVT_1 = 145\text{ bpm}$ and $HRVT_2 = 172\text{ bpm}$),
• *When* the athlete taps "Update HR Zones",
• *Then* a confirmation dialog appears showing target $Z_2$ ($145\text{ bpm}$) and $Z_4$ ($172\text{ bpm}$); upon user confirmation, heart rate zones are saved to settings with method `HRVT_ANCHORS` and current view model state updates accordingly.

**Invariants**: Pure mathematical comparison and non-destructive UI action; Power and HR anchor updates with explicit confirmation; no automated silent overwrites; 100% 9-language localization parity.


### REQ-PER-038: Model DFA a1 Autonomic Durability, Drift & Iso-Intensity Bins Across Multi-Hour Workouts [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-PER-046`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `HrvDurabilityCalculator.kt`, `HrvDurabilityResult.kt`, `ExtremaType.java`, `HrvPostWorkoutRecomputer.kt`, `WorkoutData.kt`, `WorkoutDataMapper.kt`, `HrvDurabilityCard.kt`, `WorkoutDetailTabbedContent.kt`, `strings_hrv.xml`
- **Rationale**: During multi-hour endurance workouts ($\ge 90\text{ min}$), `DFA a1` progressively declines at a given external workload due to autonomic fatigue, vagal withdrawal, and cardiovascular drift (*Rogers et al. 2021; Gronwald et al. 2021*). While `REQ-PER-036` truncates threshold estimation at 60 minutes to prevent fatigue distortion, quantifying the rate of `DFA a1` decline at matched intensity provides an actionable Autonomic Durability metric ($\Delta\alpha_{1,\%}$) that complements cardiovascular stroke-volume Heart Efficiency drift ($\Delta C_{\text{O}_2}\%$) in `ATT-4842`. The system SHALL compute, persist, and visualize autonomic durability:
- **Description**:
  1. *Fresh Baseline & Late Segment Partitioning (`REQ-PER-038.1`)*:
     - The fresh baseline window SHALL span elapsed workout time $t \in [900, 2700]\text{ s}$ (`15–45 min`), excluding warm-up stabilization and early transients.
     - The late fatigue segment SHALL evaluate all valid elapsed workout time $t > 3600\text{ s}$ (`> 60 min`).
     - Telemetry samples SHALL be evaluated from valid 5s-stepped centered-window trackpoints where `HRV_ARTIFACT_RATE <= 5.0%`.
  2. *Iso-Intensity Matched-Power & Matched-Pace Binning (`REQ-PER-038.2`)*:
     - The system SHALL determine the athlete's median aerobic reference intensity during the fresh baseline: reference power $P_{\text{ref}}$ (for cycling / power workouts) or reference speed $v_{\text{ref}}$ (for running / non-power workouts).
     - The system SHALL filter both the fresh baseline ($15\text{--}45\text{ min}$) and the late segment ($> 60\text{ min}$) to samples falling strictly within the iso-intensity band $[0.90 \cdot I_{\text{ref}}, 1.10 \cdot I_{\text{ref}}]$.
     - Minor intra-bin workload variations SHALL be normalized via the autonomic efficiency ratio $\alpha_{1,\text{norm}} = \alpha_1 \cdot (I / I_{\text{ref}})$.
  3. *Autonomic Durability Drift Calculation & Minimum Data Guard (`REQ-PER-038.3`)*:
     - Autonomic Durability Drift SHALL be calculated as:
       $$\Delta\alpha_{1,\%} = \frac{\overline{\alpha}_{1,\text{late,norm}} - \overline{\alpha}_{1,\text{fresh,norm}}}{\overline{\alpha}_{1,\text{fresh,norm}}} \times 100\%$$
     - The system SHALL require at least 10 minutes (600 s / 120 5s trackpoints) of valid matched-intensity data in both the fresh baseline and the late segment. If either window has $< 10\text{ min}$ of matched data, the calculator SHALL return `null` / `INSUFFICIENT_MATCHED_DATA`.
  4. *15-Minute Slice Trajectory Analysis (`REQ-PER-038.4`)*:
     - The calculator SHALL aggregate mean normalized $\alpha_1$ across consecutive 15-minute workout slices (`15–30m`, `30–45m`, `45–60m`, `60–75m`, `75–90m`, ...) where valid matched samples exist, detailing the progressive autonomic fatigue curve.
  5. *Zero-Schema-Migration Extrema Persistence (`REQ-PER-038.5`)*:
     - `ExtremaType` SHALL define `DFA_A1_FRESH_BASELINE`, `DFA_A1_LATE_MEAN`, and `DFA_A1_DRIFT_PCT`.
     - Upon post-workout centered HRV recomputation, `HrvPostWorkoutRecomputer` SHALL evaluate `HrvDurabilityCalculator` and persist non-null results into `WorkoutSummaries.TABLE_EXTREMA_VALUES` via `WorkoutSummariesDatabaseManager.updateExtremaValue` without SQLite schema version bump.
     - `WorkoutData` and `WorkoutDataMapper` SHALL expose `dfaA1FreshBaseline: Double?`, `dfaA1LateMean: Double?`, and `dfaA1DurabilityDriftPct: Double?`.
  6. *Presentation via `HrvDurabilityCard` & Localization Parity (`REQ-PER-038.6`)*:
     - `HrvDurabilityCard` SHALL render in `WorkoutDetailTabbedContent.kt` under `WorkoutDetailSectionType.ZONES` when autonomic durability metrics exist.
     - The card SHALL display fresh baseline $\alpha_1$, late mean $\alpha_1$, drift percentage $\Delta\alpha_1\%$, matched workload range, and 15-minute slice trajectory.
     - For non-HRV workouts or workouts $< 60\text{ min}$, the card SHALL be gracefully omitted with zero layout shift.
     - All labels and status diagnostics SHALL maintain 100% localization parity across all 9 supported locales in `strings_hrv.xml`.
  (ATT-4970: [Feature] Model DFA a1 autonomic durability and drift across multi-hour workouts (paired with ATT-4842 Heart Efficiency))

**Acceptance Criteria (Given-When-Then)**:
• *Given* an endurance workout lasting $\ge 90\text{ min}$ with valid centered-window DFA $\alpha_1$ and steady cycling power,
• *When* `HrvPostWorkoutRecomputer` processes the workout,
• *Then* `HrvDurabilityCalculator` establishes $P_{\text{ref}}$ over `15–45 min`, matches $\pm 10\%$ power windows in fresh and $>60\text{ min}$ segments, and emits $\Delta\alpha_{1,\%}$, fresh baseline mean, and late mean.
• *Given* a workout with $< 10\text{ min}$ of matched-intensity data in the late segment,
• *When* `HrvDurabilityCalculator` executes,
• *Then* it returns `null` (`INSUFFICIENT_MATCHED_DATA`) and preserves null in summary extrema.
• *Given* a completed workout displayed in Workout Details,
• *When* `WorkoutDetailTabbedContent` renders,
• *Then* `HrvDurabilityCard` displays the durability drift percentage and 15-minute slice trajectory in the user's active locale across all 9 supported languages.

**Invariants**: Zero SQLite schema migration; strict 10-minute matched data guard; 100% 9-language localization parity; graceful omission for non-HRV or short workouts.


### REQ-PER-039: Lazy Decoded Path Resolution, Bounded Period Mini-Map Caching and Lite Mode Rendering [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-PER-047`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `PeriodsRepository.kt`, `PeriodSummaryCard.kt`, `PeriodMapUtils.kt`
- **Rationale**: Eagerly decoding `List<LatLng>` objects across all historical periods into the singleton `_groupedPeriods` `StateFlow` and running full interactive OpenGL `GoogleMap` engines in scrolling `PeriodSummaryCard` list items causes `OutOfMemoryError` and thread churn. The system SHALL decouple decoded path storage, bound polyline collections, cache decoded card previews in a bounded LRU cache, and render thumbnail maps using Google Maps Lite Mode (ATT-7666, refining `REQ-PER-021.3`):
- **Description**:
  1. *Decoupled Path Decoding in Singleton Repository (`REQ-PER-039.1`)*:
     - `PeriodsRepository.enrich()` SHALL NOT eagerly invoke `PolyUtil.decode(...)` or retain populated `List<LatLng>` graphs in `PeriodSummary.workoutIdToPathMap`. `workoutIdToPathMap` in the repository singleton SHALL remain empty (`emptyMap()`), resolving paths lazily on demand.
  2. *Bounded Polyline Collections (`REQ-PER-039.2`)*:
     - `PeriodSummary.polylines` SHALL be strictly bounded to `previewWorkouts` (`<= MAX_SUMMARY_THUMBNAIL_TRACKS` / 8 tracks), and `workoutIdToAllPolylinesMap` duplication across all 4 hierarchy levels SHALL be eliminated.
  3. *Bounded LRU Preview Caching (`REQ-PER-039.3`)*:
     - `PeriodMapUtils` SHALL cache computed `PeriodCardMapState` in a thread-safe `LruCache<String, PeriodCardMapState>` (capacity: 64 entries) keyed by period sort key, preventing repetitive polyline decoding and decimation during scroll passes.
  4. *Google Maps Lite Mode Card Thumbnail Rendering (`REQ-PER-039.4`)*:
     - `PeriodSummaryCard.PeriodMultiWorkoutMap` SHALL pass `googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) }` when `visuals.heatmapProvider == null`, eliminating OpenGL context allocation and background thread spawning in `LazyColumn` items while preserving tap-to-open interactive `PeriodMapScreen` navigation.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-PER-021.3` (*Pre-decode anchor paths off main thread*).
2. *Historical Origin & Commit Trace*: Sprint 2026-44.2 (`ATT-4475`). Pre-decoded anchor paths during `enrich()` to prevent main-thread jank when opening maps.
3. *Root Reason for Existing Formulation*: Aimed to eliminate UI-thread `PolyUtil.decode`, but eagerly decoded for all periods at startup into a singleton, exhausting the 512 MB heap during long sessions.
4. *Preservation of Core Invariants*: `InteractivePeriodMap` already decodes `workoutIdToPolylineMap` asynchronously off the main thread upon opening. Setting `workoutIdToPathMap = emptyMap()` in the singleton eliminates OOM while preserving zero main-thread decoding.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a database with many periods across Days, Weeks, Months, and Years,
• *When* `PeriodsRepository.loadFromDatabase()` and `enrich()` execute,
• *Then* `workoutIdToPathMap` remains empty in the singleton `_groupedPeriods`, eliminating eager `LatLng` object retention in heap.
• *Given* period cards scrolling in `LazyColumn`,
• *When* `PeriodSummaryCard` renders mini-maps,
• *Then* `GoogleMapOptions().liteMode(true)` is configured for vector polylines, and preview states are served from the bounded LRU cache without spawning redundant OpenGL threads.
• *Given* a user tapping a Period card map,
• *When* `onMapClick` is triggered,
• *Then* the full interactive `PeriodMapScreen` launches with full zoom/pan capabilities and complete workout tracks.

**Invariants**: Zero visual regression on card thumbnails; tap-to-open interactive `PeriodMapScreen` intact; bounded heap usage during continuous scrolling.


---

### REQ-PER-040: Immediate In-Memory Propagation of RouteCluster Rename, Merge, Delete & Split and Bidirectional Route Sync [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-PER-048`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutClusterRepository.kt`, `WorkoutRepository.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClustersScreen.kt`, `PeriodsViewModel.kt`, `WorkoutSummariesViewModel.kt`, `RoutesRepository.kt`
- **Rationale**: Renaming, merging, deleting, or splitting a `WorkoutCluster` updates `RouteClusters.db` and `WorkoutClusterRepository.allClusters`, but in-memory `WorkoutData.clusterName` and `clusterId` across `WorkoutRepository._allWorkouts`, `WorkoutClustersViewModel`, and `PeriodsViewModel` remain stale until an application restart. Furthermore, `ActiveFilterChipsRow` retains stale cluster names, and cluster renames do not sync to linked routes (`ATT-7758`). The system SHALL guarantee immediate in-memory propagation across all affected repositories and ViewModels while preserving `workoutName` immutability and preventing recursive sync loops.
- **Description**:
  1. *In-Memory Workout Propagation (`REQ-PER-040.1`)*:
     - `WorkoutClusterRepository` operations (`updateCluster`, `deleteCluster`, `mergeClusters`, `splitCluster`, and route-synced renames) SHALL immediately propagate to `WorkoutRepository._allWorkouts` (`clusterName` updated on rename, `clusterId = -1L` and `clusterName = null` on delete, reassigned to `targetId` and `targetName` on merge, and reloaded/reconciled on split).
  2. *ViewModel & Peek Sheet In-Memory Reconciliation (`REQ-PER-040.2`)*:
     - `WorkoutClustersViewModel` (`_clusterWorkouts`, `_peekedWorkoutDataWithTrack`, `_selectedCluster`, `_unclusteredWorkouts`) SHALL immediately reflect cluster mutations.
     - `WorkoutClustersScreen` (`inspectedWorkout`) SHALL resolve reactively by ID from `clusterWorkouts`.
     - `PeriodsViewModel._peekedWorkoutDataWithTrack` SHALL observe `WorkoutRepository.allWorkouts` to reconcile the peeked workout header in-place.
  3. *Active Filter Chip & Filter Criteria Live Synchronization (`REQ-PER-040.3`)*:
     - `WorkoutSummariesViewModel.filterCriteria` SHALL update `clusterName` immediately on cluster rename, clear the cluster filter on delete, and transition to `targetId`/`targetName` on merge so `ActiveFilterChipsRow` stays synchronized.
  4. *Non-Recursive Bidirectional Cluster-Route Name Sync (`REQ-PER-040.4`)*:
     - `WorkoutClusterRepository.updateCluster()` SHALL sync the cluster name to any linked route in `RoutesRepository` without triggering recursion back into `WorkoutClusterRepository`.
     - `RoutesRepository.updateRouteSummary()` SHALL refresh `WorkoutClusterRepository` and propagate in-memory workout updates when a cluster-linked route is renamed.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* workouts cached in `WorkoutRepository._allWorkouts`,
  • *When* a cluster is renamed, deleted, merged, or split,
  • *Then* in-memory `clusterName` / `clusterId` update immediately across all screens without overwriting `WorkoutData.workoutName`.
  • *Given* an active cluster filter in `WorkoutSummariesViewModel`,
  • *When* the cluster is renamed, merged, or deleted,
  • *Then* `filterCriteria` and `ActiveFilterChipsRow` reflect the updated state immediately.
- **Invariants**: `WorkoutData.workoutName` MUST NOT be overwritten on cluster rename; bidirectional `Route <-> Cluster` sync MUST NOT infinite-loop; plain JVM unit tests MUST NOT trigger unmocked `PreferenceManager`/IO leaks in `WorkoutRepository.init` (`coding_standards.md` §5.5).


---

### REQ-PER-041: WorkoutCluster Name Whitespace Normalization and Startup/Refresh Self-Healing [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-PER-049`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutClusterDatabaseManager.kt`, `WorkoutClusterEngine.kt`, `WorkoutClusterRepository.kt`, `WorkoutClustersViewModel.kt`, `EditWorkoutViewModel.kt`, `WorkoutClusterHeatmapScreen.kt`, `RoutesRepository.kt`
- **Rationale**: Soft-keyboard autocomplete often appends trailing spaces to `WorkoutCluster` names (e.g., `"Kurze Runde "`), causing double spaces in `formatClusterWorkoutName` (`"Kurze Runde  #2"`), false negatives in `isNameTaken` / `findUniqueClusterName` uniqueness checks, and asymmetric chip padding (`ATT-7757`). Existing untrimmed `RouteClusters` rows in SQLite also remain stale unless normalized in-place.
- **Description**:
  1. *Write, Lookup & Formatting Whitespace Normalization (`REQ-PER-041.1`)*:
     - All `WorkoutCluster` write, lookup, and formatting paths (`updateClusterIdentity`, `EditWorkoutClusterIdentityDialog`, `addManualCluster`, `executeClusterSplit`, `EditWorkoutViewModel.createNewCluster`, `WorkoutClusterRepository.updateCluster` / `updateClusterSync`, `RoutesRepository.syncClusterNameChange`, `WorkoutClusterEngine.manuallyCreateCluster` / `syncRouteNameChange` / `splitCluster` / `createNewClusterFromWorkout` / `findUniqueClusterName` / `formatClusterWorkoutName`, and `WorkoutClusterDatabaseManager.insertCluster` / `updateCluster` / `isNameTaken` / `getClusterByName`) SHALL trim leading/trailing whitespace and reject blank-only cluster names.
  2. *Startup & Refresh In-Place Self-Healing (`REQ-PER-041.2`)*:
     - `WorkoutClusterRepository.loadAllClustersLightweight()` and `refreshClusters()` SHALL detect any existing `RouteClusters.name` entry with leading/trailing whitespace (`name != name.trim()`), compute a collision-free trimmed name using `findUniqueClusterName` logic (falling back to `"Cluster <id>"` if blank-only), update `RouteClusters.db` in-place, and propagate any healed name immediately to linked `Routes` and in-memory `WorkoutData.clusterName` via `WorkoutRepository.onClusterRenamed` and `ClusterMutationEvent.Renamed`.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* user input `"Kurze Runde "` with leading/trailing whitespace on create, rename, split, or route-sync,
  • *When* persisted or checked via `isNameTaken`, `findUniqueClusterName`, or formatted via `formatClusterWorkoutName`,
  • *Then* `"Kurze Runde"` is stored without leading/trailing whitespace, uniqueness collides with `"Kurze Runde"`, blank-only input is rejected, and `formatClusterWorkoutName(..., 2)` returns `"Kurze Runde #2"` without double spaces.
  • *Given* persisted `RouteClusters` containing `"Kurze Runde "` (and optionally an existing `"Kurze Runde"`),
  • *When* `WorkoutClusterRepository.loadAllClustersLightweight()` or `refreshClusters()` runs,
  • *Then* SQLite, `_allClusters`, linked routes, and cached `WorkoutData.clusterName` reflect the healed unique trimmed name immediately.
- **Invariants**: Internal whitespace inside multi-word names MUST be preserved; existing `REQ-PER-040` in-memory sync and non-recursive `Route <-> Cluster` propagation MUST NOT regress; no SQLite schema version bump.


---

### REQ-SPT-001: The Trophy Vault & Sporting Journey Entry & Architecture [Implemented]
- **Tests**: `TST-SPT-005`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit / Integration
- **Implementation**: `SportingJourneySheet.kt`, `SportingJourneyViewModel.kt`, `TrackingTabsScreen.kt`, `SupporterScreen.kt`
- **Rationale**: Provide entry points and modal bottom sheet hosting for "The Trophy Vault — My Sporting Journey" across tracking tabs, supporter screen, and aftermath navigation (ATT-7022, ATT-7321).
- **Description**: The system SHALL provide a modal bottom sheet entry point for "The Trophy Vault — My Sporting Journey" accessible from `TrackingTabsScreen` and `SupporterScreen`, managing immutable state aggregation and filter transitions.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* the athlete opens tracking tabs or supporter screen,
  • *When* tapping the Sporting Journey entry card or action,
  • *Then* the full-height modal bottom sheet opens with current aggregate state.

---

### REQ-SPT-002: Retrospective Filtering, Cluster Aggregation & Lifetime Engine [Verified]
- **Tests**: `TST-SPT-005`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Target**: `SportingJourneyRepository.kt`, `SportingJourneyModels.kt`, `SportingJourneyViewModel.kt`, `SportingJourneyComponents.kt`
- **Implementation**: `SportingJourneyRepository.kt`, `SportingJourneyModels.kt`, `SportingJourneyViewModel.kt`, `SportingJourneyComponents.kt`
- **Rationale**: Retrospective discovery in "The Trophy Vault" must provide mathematically exact and sport-faithful aggregation of historical workout data across All-Time and Annual Wrapped horizons without heuristics or data loss (ATT-7022, refined by ATT-7321):
- **Description**:
  1. *Recurring Route Cluster Math (`REQ-SPT-002.1`)*:
     - The engine SHALL identify recurring routes by grouping clusters with repeat visits $\text{COUNT}(*) \ge 3$ within the active query scope (`cluster_id > 0`).
     - Singleton or $< 3$ visit clusters SHALL NOT be counted as signature loops / favorite routes.
     - Workouts in recurring clusters ($\text{COUNT}(*) \ge 3$) SHALL be classified as ritual workouts (`ritualWorkoutsCount`), while all remaining workouts SHALL be classified as explorer workouts (`explorerWorkoutsCount = \text{totalWorkouts} - \text{ritualWorkoutsCount}`).
     - Fabricated heuristics (`gems.ritualWorkoutsCount / 20`) SHALL be eliminated, exposing the true recurring route count via `SportingHeroTotals.favoriteRoutesCount` and `SportingHiddenGems.recurringRoutesCount`.
  2. *Inclusive Sport Aggregation (`REQ-SPT-002.2`)*:
     - Under `SportingSportFilter.ALL`, the engine SHALL include all finished workouts (`FINISHED = 1`) across all recorded sports without restricting to `BIKE` or `RUN`.
  3. *Sport-Specific Metric Gating (`REQ-SPT-002.3`)*:
     - When `sportFilter == SportingSportFilter.RUNNING`, bicycle pedal revolutions and cadence ride counts SHALL be suppressed to `0L` and `0`. Under `ALL`, cadence revolutions SHALL only be aggregated from cycling sessions (`B_SPORT = 'BIKE'`).
  4. *Full Historical Year Range (`REQ-SPT-002.4`)*:
     - The UI filter chips and repository SHALL expose all available distinct calendar years (`availableYears`) without artificial truncation (`take(5)`).
  5. *Global Lifetime History & Immediate State Keying (`REQ-SPT-002.5`)*:
     - Athlete lifetime start date (`MIN(timeStart)`) and the Pioneer Athlete badge (`>= 365` days) SHALL be evaluated globally across all database records (`finished = 1`), regardless of the active calendar year horizon.
     - `SportingJourneyViewModel` SHALL immediately key its UI state on `(horizon, sportFilter)` and cancel preceding in-flight queries upon filter changes.
  (ATT-7321: [Verbesserung] SportingJourneyRepository & Aggregation: Fix recurring route cluster math, sport filtering, historical year range, and lifetime evaluation)

**Acceptance Criteria (Given-When-Then)**:
• *Given* workouts belonging to route clusters with $\ge 3$ visits, $< 3$ visits, and unclustered routes,
• *When* `SportingJourneyRepository.loadJourneyData` aggregates routes,
• *Then* `favoriteRoutesCount` and `recurringRoutesCount` reflect only clusters with $\ge 3$ visits, and explorer workouts reflect all remaining sessions.
• *Given* workouts of diverse sports (`BIKE`, `RUN`, `SWIM`, `UNKNOWN`),
• *When* filtered by `SportingSportFilter.ALL`,
• *Then* all workouts are included in hero totals, while pedal revolutions are restricted strictly to cycling.
• *Given* a 14-year database history (2012–2026),
• *When* viewing Annual Wrapped for 2026,
• *Then* `availableYears` renders all historical years, and `Pioneer Athlete` remains unlocked based on the global 2012 inception date.

**Invariants**: Zero SQLite schema migration; exact distance, elevation, and time math preserved; 100% 9-language localization parity.


---

### REQ-SPT-003: Hidden Gems, Signature Milestones & Achievement Room Telemetry [Implemented]
- **Tests**: `TST-SPT-005`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit / Review
- **Implementation**: `SportingJourneyModels.kt`, `SportingJourneyComponents.kt`, `SportingJourneyCalculator.kt`
- **Rationale**: Retrospective discovery in "The Trophy Vault" must surface biological and mechanical Hidden Gems, signature single-workout milestones, and achievement room trophies from offline telemetry (ATT-7022, ATT-7321).
- **Description**: The system SHALL evaluate biological and mechanical Hidden Gems (Pillar 2), signature workout milestones (Pillar 3), and unlocked achievement room trophies (Pillar 4) from SQLite telemetry data, formatting metrics cleanly across All-Time and Annual Wrapped horizons.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* historical telemetry in SQLite,
  • *When* the Trophy Vault is rendered,
  • *Then* Hidden Gems, Signature Milestones, and Achievement Room trophies are computed and displayed without cloud dependencies.

---

### REQ-SPT-004: Athlete Passport Card & Native Social Sharing [Implemented]
- **Tests**: `TST-SPT-006`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SportingJourneyShareHelper.kt`, `SportingJourneyComponents.kt`
- **Rationale**: Athletes need shareable social summary cards of their sporting retrospective without third-party cloud analytics dependencies (ATT-7022, ATT-7323).
- **Description**: `SportingJourneyShareHelper` SHALL format and dispatch the shareable Athlete Passport card via Android `Intent.ACTION_SEND`, incorporating annual summary stats, route highlights, and trophy counts.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* generated sporting journey summary metrics,
  • *When* the user taps share on Athlete Passport,
  • *Then* `Intent.ACTION_SEND` is dispatched with formatted text and statistics.


---

### REQ-SPT-005: SportingJourney UI & UX Redesign, M3 Hierarchy, Grid Layout & Locale Formatting [Verified]
- **Tests**: `TST-SPT-006`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Target**: `SportingJourneySheet.kt`, `SportingJourneyComponents.kt`, `SportingJourneyShareHelper.kt`, `strings_sporting_journey.xml`
- **Implementation**: `SportingJourneySheet.kt`, `SportingJourneyComponents.kt`, `SportingJourneyShareHelper.kt`, `strings_sporting_journey.xml`
- **Rationale**: Retrospective vault presentation must provide authentic Material 3 visual hierarchy, robust anti-wrapping layout mechanics, uniform achievement grid geometry, and internationalization compliance across all supported locales (ATT-7323):
- **Description**:
  1. *Authentic Material 3 Terminology & De-Jargonization (`REQ-SPT-005.1`)*:
     - `strings_sporting_journey.xml` across all 9 supported locales SHALL replace Strava trademark terminology ("Local Legend") with authentic athletic terminology "Route Master".
     - Prototype phrasing "Your %1$d Season Wrapped" SHALL be replaced with "Your %1$d Season Retrospective".
     - SQLite developer jargon "Hidden Gems from SQLite" SHALL be replaced with user-facing "Offline Sensor Insights".
  2. *Zero Decorative Emojis & Material 3 Vector Icons (`REQ-SPT-005.2`)*:
     - All XML string resources in `strings_sporting_journey.xml` across all 9 locales SHALL strictly comply with ATT-5400 / REQ-UI-458.3, containing zero inline decorative Unicode emojis (`🌍`, `🏔️`, etc.).
     - In `SportingJourneyComponents.kt`, `MilestonesSection` and `AchievementRoomSection` SHALL replace raw unstyled emoji text with styled Material 3 vector iconography using standard Compose `ImageVector`s within tonal surfaces.
  3. *Row Layout Weighting & Anti-Wrapping Stability (`REQ-SPT-005.3`)*:
     - In `HeroTotalsCard`, `HiddenGemsSection`, `GemCard`, `MilestonesSection`, `AchievementRoomSection`, and `AthletePassportShareCard`, all horizontal arrangement header rows (`Arrangement.SpaceBetween`) SHALL assign `Modifier.weight(1f)` to their title/content containers to eliminate mid-word wrapping and prevent trailing badges, metric chips, or action buttons from clipping off-screen.
  4. *Uniform Grid Layout for Achievement Room (`REQ-SPT-005.4`)*:
     - In `AchievementRoomSection`, the broken `FlowRow` implementation SHALL be replaced by a deterministic chunked grid (`achievements.chunked(4)`), where every cell has `Modifier.weight(1f)` and incomplete final rows are padded with `Spacer(Modifier.weight(1f))` ensuring uniform card widths and heights.
  5. *Locale.getDefault() Formatting, Imperial Units & Localized Sharing (`REQ-SPT-005.5`)*:
     - All numerical values (integers, floating-point numbers, percentages, hours) in UI cards and share generation SHALL be formatted using `Locale.getDefault()` instead of hardcoded `Locale.US`.
     - Distance and elevation formatting SHALL respect user preferences (`TrainingApplication.getUnit() == MyUnits.IMPERIAL`), formatting distance in miles (`(totalDistanceKm * 1000.0) / BANALService.METER_PER_MILE`) and elevation in feet (`totalElevationM / BANALService.METER_PER_FOOT`).
     - In `SportingJourneyShareHelper`, share passport text SHALL format localized strings using Android string resources and `Locale.getDefault()`, complying with the active unit system.
  (ATT-7323: [Verbesserung] SportingJourneySheet & SportingJourneyComponents: Material 3 redesign, row weight wrapping fixes, grid layout, and locale/unit formatting)

**Acceptance Criteria (Given-When-Then)**:
• *Given* the 9 supported Android locale resource trees,
• *When* strings are audited by automated localization contract tests,
• *Then* zero decorative Unicode emojis, zero Strava trademarks ("Local Legend"), zero developer jargon ("SQLite"), and zero prototype phrases ("Season Wrapped") are present.
• *Given* an arbitrary count of achievements (including counts not divisible by 4),
• *When* rendered in `AchievementRoomSection`,
• *Then* all badge surfaces render with identical width and height without stretching across the final row.
• *Given* device configuration set to `Locale.GERMANY` and Imperial unit system,
• *When* viewing hero totals and sharing the Athlete Passport,
• *Then* numbers display comma decimal separators, distance is converted to miles (`mi`), and elevation is converted to feet (`ft`).

**Invariants**: Zero SQLite schema migration; exact distance, elevation, and time math preserved; 100% 9-language localization parity.

---

### REQ-WKO-035: Workout RPE & Feel Database Schema, Persistence & Default Configuration [Implemented]
- **Tests**: `TST-WKO-035`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Target**: `WorkoutSummariesDatabaseManager.java`, `WorkoutSummariesDbHelper.kt`, `WorkoutDataMapper.kt`
- **Implementation**: `WorkoutSummariesDatabaseManager.java`, `WorkoutSummariesDbHelper.kt`, `WorkoutDataMapper.kt`
- **Rationale**: Enable subjective exertion (RPE 1..10) and feel (1..5) tracking in workout summaries database with symmetric migration and mapping parity (ATT-6879).
- **Description**: `WorkoutSummariesDbHelper` SHALL support `DB_VERSION = 31` with nullable `rpe` (`INTEGER`, `1..10`) and `feel` (`INTEGER`, `1..5`) columns, ensuring `onCreate` and `onUpgrade` parity and symmetric mapping in `WorkoutData` and `WorkoutDataMapper`.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* database creation or migration,
  • *When* upgrading to or creating DB version 31,
  • *Then* `rpe` and `feel` nullable columns exist with symmetric cursor mapping.

---

### REQ-WKO-036: RPE Borg CR10 Scale & Subjective Feel Anchors, Vector Icons & Emojis [Implemented]
- **Tests**: `TST-WKO-036`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Target**: `EditWorkoutScreen.kt`, `WorkoutRpeFeelSection.kt`, `strings_rpe_feel.xml`
- **Implementation**: `EditWorkoutScreen.kt`, `WorkoutRpeFeelSection.kt`, `strings_rpe_feel.xml`
- **Rationale**: Provide intuitive Borg CR10 verbal anchors and Material 3 vector sentiment icons with emoji fallbacks across all 9 supported locales (ATT-6879, ATT-7320).
- **Description**: The system SHALL provide localized Borg CR10 scale anchors (1..10) and subjective feel ratings (1..5) mapped to Material 3 sentiment vector icons and emojis, exposing interactive controls in `EditWorkoutScreen` and section preferences defaulting to disabled.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* the RPE & Feel UI editor,
  • *When* the user views anchors and icons across scales,
  • *Then* Borg 1..10 anchors and Feel 1..5 sentiment icons are rendered accurately.

---

### REQ-WKO-037: Reorderable Section Hierarchy, Section Slotting & Overview Tab Slotting [Implemented]
- **Tests**: `TST-WKO-037`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Target**: `WorkoutRpeFeelSection.kt`, `WorkoutSectionType.kt`, `WorkoutDetailTabConfig.kt`
- **Implementation**: `WorkoutRpeFeelSection.kt`, `WorkoutSectionType.kt`, `WorkoutDetailTabConfig.kt`
- **Rationale**: Support customizable aftermath layout slotting with RPE & Feel positioned above Description or at index 0 of Overview tab (ATT-6879, ATT-7320).
- **Description**: `WorkoutSectionType` and `WorkoutDetailSectionType` SHALL declare `RPE_AND_FEEL` at `index 0` in `DEFAULT_ORDER` and `WorkoutDetailTabConfig.DEFAULT_TABS` Overview preset, rendering above the workout description.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* default workout detail tab configurations,
  • *When* opening the Overview tab,
  • *Then* `RPE_AND_FEEL` occupies slot 0.

---

### REQ-WKO-038: Foster Session-RPE Training Load Score Calculation [Implemented]
- **Tests**: `TST-WKO-038`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Target**: `PowerPhysiologyCalculator.kt`, `WorkoutRpeFeelSection.kt`
- **Implementation**: `PowerPhysiologyCalculator.kt`, `WorkoutRpeFeelSection.kt`
- **Rationale**: Compute validated Foster Session-RPE training load ($sRPE = \text{RPE} \times \text{duration\_minutes}$) for subjective exertion quantification (ATT-6879).
- **Description**: `PowerPhysiologyCalculator.calculateSessionRpe()` SHALL compute Foster Session-RPE training load score as $sRPE = \text{RPE} \times (\text{activeTimeSeconds} / 60.0)$ for valid RPE scores (`1..10`) and positive duration, returning `0.0` for out-of-range or non-positive values.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* valid RPE and active time duration,
  • *When* `calculateSessionRpe()` is called,
  • *Then* it returns the exact mathematical product $RPE \times (duration\_sec / 60.0)$.

---

### REQ-WKO-039: Performance Management Chart (PMC) Low-CTL ACWR Gate & Tier-4 TSS-Normalized Session-RPE Fallback [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-WKO-039`
- **System Requirement(s)**: Derived (SW-only — pure physiology training-load & ACWR math in `PowerPhysiologyCalculator.kt`)
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Target**: `PowerPhysiologyCalculator.kt`, `FitFileWriter.kt`
- **Implementation**: `PowerPhysiologyCalculator.kt`, `FitFileWriter.kt`
- **Localization Parity**: `N/A - domain math only (UI badge localization covered in REQ-UI-2792)`
- **Rationale**: Prevents low-denominator ACWR false injury-risk spikes during cold starts (`CTL < 15.0`) and eliminates 4–5x PMC load inflation caused by feeding raw Foster sRPE (`RPE * minutes`) directly into the TSS-scaled PMC (`ATT-6879`, `ATT-8196`, `ATT-9360`).
- **Description**:
  1. *Minimum Chronic Fitness Gate for ACWR (`REQ-WKO-039.1`)*: `PowerPhysiologyCalculator` SHALL define `const val MIN_CTL_FOR_ACWR: Double = 15.0`. In `computePerformanceManagementChart()`, per-day `acwr` SHALL be computed as `atl / ctl` strictly when `ctl >= MIN_CTL_FOR_ACWR` and SHALL be `null` (with `acwrZone = null`, propagating to `PmcSummaryResult.currentAcwr` / `currentAcwrZone`) whenever `ctl < MIN_CTL_FOR_ACWR`.
  2. *Tier-4 RPE-to-TSS Normalization (`REQ-WKO-039.2`)*: `PowerPhysiologyCalculator` SHALL provide `@JvmStatic fun calculateRpeTss(rpe: Int, activeTimeSeconds: Double): Double` mapping Borg CR10 `rpe` `1..10` to `20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 100.0, 120.0, 140.0` TSS/hour scaled linearly by `(activeTimeSeconds / 3600.0)` (calibrating 1h @ RPE 8 = `100.0` TSS, and returning `0.0` when `rpe !in 1..10` or `!activeTimeSeconds.isFinite() || activeTimeSeconds <= 0.0`).
  3. *Tier-4 Resolution & Foster sRPE Isolation (`REQ-WKO-039.3`)*: `resolveWorkoutTrainingLoad()` SHALL delegate Tier 4 (no power or HR telemetry) to `calculateRpeTss(rpe, activeSec)` while preserving Tier 1–3 precedence (`powerTss` -> derived power `TSS` -> `hrTSS`) and `FitFileWriter` feel/RPE serialization. `PowerPhysiologyCalculator.calculateSessionRpe()` (`REQ-WKO-038`) MUST remain 100% unchanged as authentic Foster sRPE (`RPE * minutes`) for `WorkoutRpeFeelSection.kt`.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a workout history where `CTL_t < 15.0` (e.g., `CTL = 7.0`, `ATL = 22.7`), *When* `computePerformanceManagementChart()` evaluates day `t`, *Then* `acwr == null` and `acwrZone == null`; once `CTL_t >= 15.0`, `acwr == ATL_t / CTL_t` and `acwrZone` classifies normally.
  • *Given* a 60-minute (`3600 s`) RPE-only workout with `RPE = 8`, *When* `resolveWorkoutTrainingLoad()` resolves Tier 4, *Then* it returns `100.0` TSS (and `calculateRpeTss` matches `20..140` TSS/h across `RPE 1..10`, returning `0.0` on invalid/non-finite inputs), while `calculateSessionRpe(7, 3600.0)` continues returning `420.0`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines `REQ-WKO-039` (`PowerPhysiologyCalculator.kt`, `FitFileWriter.kt`) and `REQ-UI-401.1` ACWR threshold.
  2. *Historical Origin & Commit Trace*: `ATT-3859` (`dc62bb3c`) added `ctl > 0.01` ACWR guard; `ATT-6879` (`2c999318`) introduced `REQ-WKO-038`/`REQ-WKO-039` wiring raw `calculateSessionRpe()` into Tier 4; `ATT-8196` added FIT feel/RPE serialization.
  3. *Root Reason for Existing Formulation*: `ctl > 0.01` only guarded divide-by-zero; `ATT-6879` reused `calculateSessionRpe()` directly as a fast Tier-4 fallback without TSS scale calibration.
  4. *Preservation of Core Invariants*: Preserves `REQ-WKO-038` authentic Foster sRPE (`calculateSessionRpe()` untouched for `WorkoutRpeFeelSection.kt:144`), Banister/Coggan `CTL`/`ATL`/`TSB`/`RampRate`, Tier 1–3 precedence, `AcwrZone` thresholds (`0.80 / 1.30 / 1.50`), and `FitFileWriter` metadata export.

---

### REQ-HRV-038: Morning Sleep & Wellness Settings Progressive Disclosure [Implemented]
- **Tests**: `TST-HRV-038`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Target**: `TuningPreferencesDataStore.kt`, `PhysiologyModelsSection.kt`, `MorningCheckSection.kt`
- **Implementation**: `TuningPreferencesDataStore.kt`, `PhysiologyModelsSection.kt`, `MorningCheckSection.kt`
- **Rationale**: Configurable progressive disclosure in Advanced Settings for subjective sleep and morning wellness tracking (ATT-7034).
- **Description**: `SettingsDataStore.kt` and `PhysiologyModelsSection.kt` SHALL provide master toggle `enableMorningSleepAndWellness: Boolean` (default `false`) with subordinate controls for sleep duration, quality, energy, soreness, and context tags.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* default app installation,
  • *When* inspecting Advanced Settings,
  • *Then* `enableMorningSleepAndWellness` is disabled by default.

---

### REQ-HRV-039: Morning Readiness SQLite Schema Evolution & Parity [Implemented]
- **Tests**: `TST-HRV-039`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Target**: `HrvReadinessDbHelper.kt`, `HrvReadinessDatabaseManager.kt`, `HrvReadinessEntry.kt`
- **Implementation**: `HrvReadinessDbHelper.kt`, `HrvReadinessDatabaseManager.kt`, `HrvReadinessEntry.kt`
- **Rationale**: Support standalone subjective wellness entries alongside autonomic HRV measurements with database migration parity (ATT-7034).
- **Description**: `HrvReadinessDbHelper` SHALL support `DB_VERSION = 2` allowing HRV metrics to be nullable for standalone wellness entries and adding nullable columns `sleep_duration_min`, `sleep_quality_1_5`, `morning_energy_1_5`, `muscle_soreness_1_5`, and `context_tags` with `onCreate`/`onUpgrade` parity.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* database creation or upgrade from V1,
  • *When* migrating to `DB_VERSION = 2`,
  • *Then* schema contains all 5 nullable wellness columns with 100% parity.

---

### REQ-HRV-040: Standalone No-Strap Morning Wellness Logging [Implemented]
- **Tests**: `TST-HRV-040`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Target**: `MorningHrvCheckScreen.kt`, `MorningHrvCheckViewModel.kt`
- **Implementation**: `MorningHrvCheckScreen.kt`, `MorningHrvCheckViewModel.kt`
- **Rationale**: Allow athletes to log morning sleep and wellness markers on non-strap days or without an HR chest strap (ATT-7034).
- **Description**: When enabled, `MorningHrvCheckScreen.kt` SHALL permit entering Sleep Duration/Quality and Morning Energy and saving directly without connecting an HR sensor, storing standalone entries with null HRV metrics without corrupting baseline SWC corridors.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* morning check screen with no HR sensor connected,
  • *When* saving morning wellness,
  • *Then* record is saved with null HRV metrics.

---

### REQ-HRV-041: In-Measurement Combined Morning HRV & Wellness Logging [Implemented]
- **Tests**: `TST-HRV-041`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Target**: `MorningHrvCheckScreen.kt`, `MorningHrvCheckViewModel.kt`
- **Implementation**: `MorningHrvCheckScreen.kt`, `MorningHrvCheckViewModel.kt`
- **Rationale**: Allow interactive wellness input during the 60s/120s countdown timer and combine with objective HRV metrics upon save (ATT-7034).
- **Description**: When enabled, subjective wellness inputs SHALL remain interactive before, during, and after the countdown timer, persisting both objective HRV metrics and subjective wellness values into the same daily record upon measurement completion.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* an active HRV countdown measurement,
  • *When* user inputs wellness ratings and measurement completes,
  • *Then* saving persists both objective HRV metrics and subjective wellness into a single record.

---

### REQ-HRV-042: Health Connect Sleep Auto-Prefill & 9-Language Localization [Implemented]
- **Tests**: `TST-HRV-042`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Target**: `HealthConnectManager.kt`, `strings_hrv.xml`
- **Implementation**: `HealthConnectManager.kt`, `strings_hrv.xml`
- **Rationale**: Seamlessly prefill sleep duration from Health Connect when permission is granted, with 100% 9-language localization parity (ATT-7034).
- **Description**: `HealthConnectManager.kt` SHALL optionally prefill sleep duration from `SleepSessionRecord` within the last 24 hours if permission is granted, with all UI strings maintaining 100% parity across all 9 supported locales.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* Health Connect sleep permission granted,
  • *When* opening morning check screen,
  • *Then* sleep duration is pre-filled from the most recent sleep session.

---

### REQ-PER-042: Multi-Sensor Telemetry Chart Overlays & Session Mean Comparison for HR, Power, and Cadence (`ATT-8096`) [Verified]
- **System Requirement(s)**: Derived (SW-only) — Post-workout multi-sensor telemetry overlay visualization and session mean comparison
- **Tests**: `TST-PER-050`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `MapModels.kt`, `WorkoutRepository.kt`, `TrackOnMapAftermathViewModel.kt`, `TelemetryMetricGraph.kt`, `TelemetryMetricUtils.kt`, `TelemetryGraphColorPreferences.kt`, `MapDetailLayout.kt`, `WorkoutDetailTabbedContent.kt`, `res/values*/strings.xml`
- **Rationale**: `TrackerService.java` already records source-qualified columns (`"HR (<deviceName>)"`, `"POWER (<deviceName>)"`, `"CADENCE (<deviceName>)"`) in `WorkoutSamples.db`, yet Workout Details charts only display canonical single-column streams (`HR`, `POWER`) and omit `CADENCE`, preventing athletes from visually comparing dual HR straps or pedal vs. smart-trainer power on-device (`ATT-8096`, Epic `ATT-68`).
- **Description**:
  1. *Strict Multi-Source Column Discovery & Dotted-Name Lookup (`REQ-PER-042.1`)*:
     - `WorkoutRepository.kt` (`loadWorkoutDetailTelemetry` for GPS workouts and `getWorkoutTelemetryPoints` for trackless indoor workouts) SHALL discover source-qualified columns in `WorkoutSamples.db` strictly matching `"^${type.name} \\((.+)\\)$"` for `HR`, `POWER`, and `CADENCE` across `cursor.columnNames` (using `columnNames.indexOf(...)` so device names containing dots such as `"KICKR v5.0"` never fail `Cursor.getColumnIndex()`).
     - Single-sensor workouts recording both canonical `ProxySensor` (`"HR"`) and `1` named column (`"HR (<device>)"`) MUST resolve to `1` named source and MUST NOT trigger multi-source overlay UI. Multi-source overlay UI SHALL activate strictly when `>= 2` distinct named source columns exist for that metric.
  2. *`PathPoint.cadence` & `TelemetryMetricType.CADENCE` Support (`REQ-PER-042.2`)*:
     - `PathPoint` (`MapModels.kt`) SHALL add nullable `cadence: Int? = null`, and `TelemetryMetricType` (`TelemetryMetricGraph.kt`) SHALL add `CADENCE` (wired across `TelemetryMetricUtils.kt`, `MapDetailLayout.kt`, and `WorkoutDetailTabbedContent.kt`).
     - `TelemetryGraphColorPreferences` SHALL resolve `TelemetryMetricType.CADENCE` to the existing `telemetry_graph_color_SENSOR_CADENCE` preference key (`THEME_DEFAULT` default) without altering existing preference keys or defaults (`REQ-UI-530`).
  3. *Progressive-Disclosure `Layers` Overlay, `Ø` Mean Comparison & Synchronized Scrubbing (`REQ-PER-042.3`)*:
     - When `<= 1` named source exists for `HR`, `POWER`, or `CADENCE`, `TelemetryMetricGraph` SHALL display zero additional `Layers` toggles, selectors, or comparison legend rows.
     - When `>= 2` named sources exist, `TelemetryMetricGraph` SHALL expose a compact `Layers` overlay toggle, render enabled secondary device curve(s) with distinct color/stroke styling, show a compact one-line session-mean comparison in the legend (e.g., `Ø %1$d bpm`, `Ø %1$d W`, `Ø %1$d rpm`), and update header/scrubbing readouts synchronously across both `DISTANCE` and `TIME` X-axis domains (GPS and trackless workouts) while strictly preserving `Rule 64` plot paddings (`50.dp`/`25.dp`/`24.dp`) and `Rule 48` axis labels.
  4. *9-Locale Parity & Rule 8.5 Visual Compose Verification (`REQ-PER-042.4`)*:
     - All new chart heading and multi-source overlay/mean strings (`graph_heading_cadence`, overlay toggle & mean labels) SHALL maintain 100% parity across all 9 locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with zero emojis (`ATT-5400`).
     - Stage 4 SHALL render a Robolectric Compose PNG screenshot (`docs/engineering/walkthroughs/img/ATT-8096_multisensor_charts.png`, Light & Dark themes) verifying multi-source overlay curves, `Layers` toggle, `Ø` mean legend, and zero clutter on single-sensor cards (`Rule 8.5`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-PER-042` extending `REQ-UI-206` (`TelemetryMetricGraph`), `REQ-UI-260` (trackless workouts), `REQ-DAT-033` (multi-sensor sample persistence), `REQ-UI-530` (`TelemetryGraphColorPreferences`), and `REQ-TRK-029` (multi-sensor columns).
2. *Historical Origin & Commit Trace*: `TrackerService` multi-sensor SQLite persistence (`REQ-TRK-029`) + `TelemetryMetricGraph` (`ATT-1740`) + `TelemetryGraphColorPreferences` (`ATT-7759`).
3. *Root Reason for Existing Formulation*: `TelemetryMetricGraph` queried only single canonical columns to keep mobile chart layouts clean and decoupled before multi-sensor source overlays were requested.
4. *Preservation of Core Invariants*: Zero UI clutter on single-sensor (`<= 1` named source) workouts; `Rule 64` plot alignment (`50.dp`/`25.dp`/`24.dp` portrait, `0.72f` landscape) and `Rule 48` axis labels intact; `telemetry_graph_color_SENSOR_CADENCE` preference key preserved; `TrackerService.java` and `CSVFileWriter.java` untouched; zero `WorkoutSamples.db` schema bumps; backward-compatible `TelemetryMetricGraph` defaults for `WorkoutSummary.kt` and `HrvRampTestScreen.kt`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a `WorkoutSamples.db` recording with `<= 1` named source column per metric (e.g., canonical `"HR"` + `"HR (Polar H10)"`), *When* opening Workout Details (`CHARTS`), *Then* no `Layers` overlay toggle or mean comparison legend renders on `HR`, `POWER`, or `CADENCE`.
• *Given* a GPS or trackless workout containing `>= 2` named columns for `HR`, `POWER`, or `CADENCE` (including dotted device names such as `"POWER (KICKR v5.0)"`), *When* enabling the `Layers` overlay toggle, *Then* secondary device curve(s) render in distinct stroke/color together with a compact `Ø` mean comparison in the legend.
• *Given* an active multi-sensor overlay on `HR`, `POWER`, or `CADENCE`, *When* scrubbing horizontally across `DISTANCE` or `TIME`, *Then* the vertical marker and header/readout display synchronized values for active overlaid sources in sync with all Workout Details charts.

**Invariants**: `<= 1` named source shows zero overlay UI; `Rule 64` & `Rule 48` preserved; `telemetry_graph_color_SENSOR_CADENCE` reused; zero `WorkoutSamples.db` schema changes; zero emojis across 9 locales (`ATT-5400`).

---

### REQ-PER-043: Safe Eager ViewModel Initialization Order for Periods & Route Clusters (`ATT-8569`) [Verified]
- **System Requirement(s)**: Derived (SW-only) — Safe eager ViewModel init-order initialization for Periods and Route Clusters
- **Target Release**: `V5.0.0`
- **Tests**: `TST-PER-051`
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit, On-Device Manual
- **Implementation**: `PeriodsViewModel.kt`, `WorkoutClustersViewModel.kt`
- **Rationale**: In `PeriodsViewModel.kt`, `init` (`ATT-7758` / `608c4e31`) launches a `viewModelScope` coroutine (`Dispatchers.Main.immediate` on Android) collecting `WorkoutRepository.allWorkouts` (`StateFlow`), which emits synchronously during construction and dereferences `_peekedWorkoutDataWithTrack` before its property initializer executes, crashing with `NullPointerException` upon opening the Periods screen (`ATT-8569`). `WorkoutClustersViewModel.kt` exhibits the same latent ordering hazard (`init` before `_peekedWorkoutDataWithTrack`).
- **Description**:
  1. *`PeriodsViewModel` Init-Order Safety (`REQ-PER-043.1`)*:
     - `PeriodsViewModel` SHALL initialize `_peekedWorkoutDataWithTrack` (and all other ViewModel properties referenced inside `init`) prior to executing `init`, so instantiating `PeriodsViewModel` under `Dispatchers.Main.immediate` or `UnconfinedTestDispatcher` with either an empty or populated `WorkoutRepository.allWorkouts` `StateFlow` completes without throwing `NullPointerException`.
     - Subsequent `allWorkouts` emissions MUST continue reconciling `peekedWorkoutDataWithTrack` in-place while preserving `WorkoutData.workoutName` immutability (`REQ-PER-040.2`).
  2. *`WorkoutClustersViewModel` Defensive Init-Order Safety (`REQ-PER-043.2`)*:
     - `WorkoutClustersViewModel` SHALL initialize `_peekedWorkoutDataWithTrack` (and all other ViewModel properties) prior to executing `init` (`refresh()`, `observeClusterMutations()`), preventing `NullPointerException` even when `clusterMutationEvents` emits synchronously at subscription time.
  3. *Localization Parity*: `N/A - no UI strings`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: `REQ-PER-040` / `REQ-PER-040.2` (`TST-PER-048`, `ATT-7758`), targeting `PeriodsViewModel.kt` and `WorkoutClustersViewModel.kt`.
  2. *Historical Origin & Commit Trace*: Introduced in commit `608c4e31` (`ATT-7758`, 2026-11-15) to propagate `RouteCluster` rename/merge/delete/split updates immediately into peek sheet `WorkoutData`.
  3. *Root Reason for Existing Formulation*: `PeriodsViewModel._peekedWorkoutDataWithTrack` was declared near `selectWorkoutForPeek()` at the bottom of the class while the `allWorkouts` collector `init` block was placed near constructor repositories; `PeriodsViewModelTest` used `StandardTestDispatcher()`, masking synchronous `Dispatchers.Main.immediate` emission.
  4. *Preservation of Core Invariants*: Preserves 100% of `REQ-PER-040.2` reactive peek reconciliation, `WorkoutData.workoutName` immutability, `collectIgnoringUnstubbedMock` MockK resilience, and `StandardTestDispatcher` test behavior.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `WorkoutRepository.allWorkouts` holds an empty or populated `StateFlow` and `Dispatchers.Main` executes eagerly (`Dispatchers.Main.immediate` / `UnconfinedTestDispatcher`),
  • *When* `PeriodsViewModel` is instantiated,
  • *Then* initialization completes without throwing `NullPointerException`, and subsequent `allWorkouts` emissions still reconcile `peekedWorkoutDataWithTrack` without mutating `workoutName`.
  • *Given* `WorkoutClustersViewModel` is constructed when `clusterMutationEvents` emits eagerly or on `Dispatchers.Main.immediate`,
  • *When* `init` invokes `refresh()` and `observeClusterMutations()`,
  • *Then* `_peekedWorkoutDataWithTrack` is already initialized and never throws `NullPointerException`.
  • *Given* a debug build installed on `emulator-5554`,
  • *When* opening the app and navigating to the Periods screen,
  • *Then* the Periods screen renders cleanly without crashing.
- **Invariants**: `REQ-PER-040.2` peek reconciliation and `workoutName` immutability preserved; zero DB schema, API, or `ANT-Android-SDKs` changes; `Localization Parity: N/A - no UI strings`.

---

### REQ-HRV-043: Pure Wellness Mode Gating & HRV Fallback on MorningHrvCheckScreen (`ATT-8598`) [Verified]
- **System Requirement(s)**: Derived (SW-only) — Pure morning sleep & wellness UI gating and HRV fallback on MorningHrvCheckScreen (refining `REQ-HRV-040` and `REQ-PER-032`)
- **Target Release**: `V5.0.0`
- **Tests**: `TST-HRV-043`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `MorningHrvCheckViewModel.kt`, `MorningHrvCheckScreen.kt`, `res/values*/strings_hrv.xml`
- **Rationale**: `ATT-8469` enabled the Start-screen *"Morning Check"* launcher for pure subjective sleep & wellness tracking (`showMorningCheckOnStartScreen = false`, `enableMorningSleepAndWellness = true`), yet `MorningHrvCheckScreen` still displays all HRV measurement cards, HRV TopAppBar metadata, and the *Start Measurement* CTA above `MorningWellnessInputCard` (`ATT-8598`).
- **Description**:
  1. *ViewModel Flag Exposure & Pure Wellness Mode Derivation (`REQ-HRV-043.1`)*:
     - `MorningHrvUiState` (`MorningHrvCheckViewModel.kt`) SHALL expose `showMorningCheckOnStartScreen: Boolean = TuningPreferencesDefaults.DEFAULT_SHOW_MORNING_CHECK_ON_START_SCREEN`, collected reactively from `tuningDataStore.tuningConfigFlow`.
     - Pure wellness mode SHALL evaluate to `true` iff `!uiState.showMorningCheckOnStartScreen && uiState.enableMorningSleepAndWellness && uiState.phase == MorningHrvPhase.READY`.
  2. *Pure Wellness Mode UI & Empty-State Presentation (`REQ-HRV-043.2`)*:
     - In pure wellness mode, `MorningHrvCheckScreen` SHALL hide `WakingProtocolChecklistCard`, `PostureAndDurationCard`, `MeasurementPacingCard`, the TopAppBar HRV Info `IconButton` (`HrvInfoBottomSheet`), and the HRV *Start Measurement* CTA (`morning_hrv_btn_start`).
     - The `TopAppBar` SHALL adapt its title to `R.string.hrv_morning_check_start_button` and subtitle to `R.string.morning_hrv_wellness_subtitle`.
     - `ActionButtonsSection` SHALL promote *Save Morning Wellness* (`onSaveStandaloneWellness`, `morning_hrv_btn_save_wellness`) to the primary filled `Button` (transitioning to the `TTColor.ReadinessOptimal` green *Saved* state with checkmark when `isWellnessSaved == true`).
     - If all 4 wellness dimension sub-toggles (`enableSleepDurationAndQuality`, `enableMorningEnergy`, `enableMuscleSoreness`, `enableContextTags`) are `false`, `MorningWellnessInputCard` SHALL render an informative empty-state hint (`R.string.morning_hrv_wellness_empty_hint`).
  3. *Enabled HRV Mode & Direct Profile Fallback (`REQ-HRV-043.3`)*:
     - When `showMorningCheckOnStartScreen == true`, OR `!enableMorningSleepAndWellness` (e.g., direct navigation via `AthleteProfileScreen` `hrv_measure_morning_cta`), OR `phase != MorningHrvPhase.READY` (active `STABILIZING`/`MEASURING` sensor session or `COMPLETED` review), `MorningHrvCheckScreen` SHALL preserve the complete HRV (+ optional Wellness) layout, TopAppBar HRV title/subtitle/info action, and *Start Measurement* primary button intact.
  4. *9-Language Localization Parity & Compose Previews (`REQ-HRV-043.4`)*:
     - New strings `morning_hrv_wellness_subtitle` and `morning_hrv_wellness_empty_hint` SHALL maintain 100% parity across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with zero emojis (`ATT-5400`).
     - `MorningHrvCheckScreen.kt` SHALL extract stateless `MorningHrvCheckContent` with Light, Dark, and AMOLED `@Preview` composables covering pure wellness mode, full HRV+Wellness mode, and active measurement.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: `REQ-HRV-040` (`Standalone No-Strap Morning Wellness Logging`, `ATT-7034`), `REQ-PER-032` (`REQ-PER-032.1`, `REQ-PER-032.6`, `ATT-3556`/`ATT-6719`), and `REQ-UI-525` (`ATT-8469`), targeting `MorningHrvCheckViewModel.kt` and `MorningHrvCheckScreen.kt`.
  2. *Historical Origin & Commit Trace*: `MorningHrvCheckScreen` was introduced HRV-first in `ATT-3556` (`dc034c08`), extended with optional `MorningWellnessInputCard` in `ATT-7034` (`a77ce176`), and launched from the Start screen in pure wellness mode via `ATT-8469` (`241df3a3`).
  3. *Root Reason for Existing Formulation*: Subjective wellness was originally added as an optional card inside an HRV-centric screen before `ATT-8469` allowed launching the screen with Morning HRV Check disabled.
  4. *Preservation of Core Invariants*: Zero SQLite schema changes (`HrvReadinessDbHelper`), zero workout generation, `keepScreenOn` during `STABILIZING`/`MEASURING` preserved, standalone wellness persistence (`rmssdMs = null`, `isPrimaryForDay = true`) and Start-screen auto-hide preserved (`REQ-UI-525.4`), and `AthleteProfileScreen` HRV CTA fallback intact.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `showMorningCheckOnStartScreen = false` and `enableMorningSleepAndWellness = true` in `MorningHrvPhase.READY`, *When* opening `MorningHrvCheckScreen`, *Then* HRV protocol/posture/duration/pacing cards, info icon, and *Start Measurement* are hidden, TopAppBar shows wellness title/subtitle, and *Save Morning Wellness* renders as the primary filled `Button`.
  • *Given* pure wellness mode with all 4 wellness sub-toggles `false`, *When* `MorningWellnessInputCard` renders, *Then* `morning_hrv_wellness_empty_hint` is displayed inside the card.
  • *Given* `showMorningCheckOnStartScreen = true` OR `enableMorningSleepAndWellness = false` OR `phase != MorningHrvPhase.READY`, *When* `MorningHrvCheckScreen` renders, *Then* the full HRV measurement layout and TopAppBar HRV metadata remain visible.
- **Invariants**: Zero DB migrations, zero changes to `ControlTrackingViewModel`/`MorningCheckSection`, full HRV layout preserved when enabled or measuring, 100% 9-locale parity.

### REQ-HRV-044: DFA alpha 1 Zone-Colored Graph, Dynamic Thresholds & Scatter Parity (`ATT-8710`) [Verified]
- **System Requirement(s)**: Derived (SW-only) — Workout Details DFA $\alpha_1$ 3-zone graph coloring, margin coordination, threshold filtering, and scatter parity (refining `REQ-PER-035.1` and `REQ-PER-035.3`)
- **Target Release**: `V5.0.0`
- **Tests**: `TST-HRV-044`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `TelemetryZoneMath.kt`, `TelemetryMetricGraph.kt`, `TelemetryMetricUtils.kt`, `HrvAlpha1ScatterChart.kt`
- **Rationale**: In Workout Details, `TelemetryMetricGraph` (`HRV_DFA_A1`) renders dashed threshold lines at `dfaA1ThresholdT2`/`dfaA1ThresholdT1` without zone bands or zone-colored curve/fill/histogram/cursor styling, does not guard the `52.dp` right margin against label-vs-histogram overlap, does not filter dashes outside `[0.2, 1.4]` (up to `MAX_DFA_A1_THRESHOLD = 1.50f`), and `HrvAlpha1ScatterChart.kt` omits `t1`/`t2` when calling `HrvZoneIndicator.calculateZoneColor`.
- **Description**:
  1. *Inverted 3-Zone DFA $\alpha_1$ Bands & Curve/Fill/Histogram/Cursor Coloring (`REQ-HRV-044.1`)*:
     - `TelemetryZoneMath` SHALL partition `[dataMin, dataMax]` into up to 3 clamped inverted DFA $\alpha_1$ bands with exact `HrvZoneIndicator.calculateZoneColor` parity: Anaerob (`α1 < T2`, `TTColor.Zone4`, top band `[dataMin, T2]` per inverted effort Y-axis refined in `REQ-HRV-046` / `ATT-8953`), Schwelle (`T2 <= α1 <= T1`, `TTColor.Zone3`, middle band `[T2, T1]`), and Aerob (`α1 > T1`, `TTColor.Zone2`, bottom band `[T1, dataMax]`), preserving `α1 == T1 -> Zone3`.
     - For `TelemetryMetricType.HRV_DFA_A1`, `TelemetryMetricGraph` SHALL render horizontal background bands (`ZONE_BAND_ALPHA`), zone-colored curve stroke and under-curve fill (`Brush.verticalGradient` mapped to `valueToY(T1)`/`valueToY(T2)` across `[dataMin, dataMax]` with `> 5.0%` artifact gap breaks preserved), single-point dot, interactive scrubbing cursor dot/halo, and `nonZonedHistogram` bins (if supplied) colored by zone.
  2. *Right-Margin `52.dp` Layout Coordination (`REQ-HRV-044.2`)*:
     - Inside the `52.dp` `endPadding` strip in portrait (`!isLandscape`), right-hand zone labels (`band.label`) and `nonZonedHistogram` bars MUST remain cleanly disjoint: right-hand zone labels SHALL render only when `zoneDistribution == null && nonZonedHistogram.isEmpty() && !isLandscape`, suppressing duplicate axis text whenever `nonZonedHistogram` occupies the strip.
  3. *Dynamic `T1`/`T2` Propagation & Scatter Parity (`REQ-HRV-044.3`)*:
     - `TelemetryMetricGraph`, `TelemetryZoneMath`, and `HrvAlpha1ScatterChart` (`HrvAlpha1ScatterChart.kt:384`) SHALL propagate the athlete's configured `dfaA1ThresholdT1` / `dfaA1ThresholdT2` from `TuningPreferencesDataStore` (`MapDetailLayout` / `HrvAlpha1ScatterCard`) into zone bands, gradients, cursor dots, and scatter dot colors (`HrvZoneIndicator.calculateZoneColor(p.alpha1, t1 = t1, t2 = t2)`).
  4. *Threshold Dash Range Filtering (`REQ-HRV-044.4`)*:
     - `TelemetryZoneMath.calculateHrvDfaA1ThresholdDashes(t1, t2, dataMin, dataMax)` SHALL filter `T1`/`T2` strictly to `(dataMin, dataMax)` (`it > dataMin && it < dataMax`), matching `calculateThresholdDashes` so out-of-range thresholds (e.g., `MAX_DFA_A1_THRESHOLD = 1.50f > 1.4`) never draw off-axis lines.
  5. *Invariants & Localization (`REQ-HRV-044.5`)*:
     - Existing `HEART_RATE` / `POWER` zone-graph behavior (`REQ-UI-230` solid `accentColor` stroke/fill), `startPadding = 50.dp` / `endPadding = 52.dp` alignment (`Rule 64`), and `TrackingHrvZoneIndicatorTest` boundary polarity SHALL remain intact. Localization parity: `N/A - no UI strings` (if any localized UI string is added, all 9 `values*` locales SHALL be updated).
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: `REQ-PER-035.1` / `REQ-PER-035.3`, `REQ-UI-230`, and `REQ-TRK-061.3` targeting `TelemetryZoneMath.kt`, `TelemetryMetricGraph.kt`, `TelemetryMetricUtils.kt`, and `HrvAlpha1ScatterChart.kt`.
  2. *Historical Origin & Commit Trace*: `3db57718` (`ATT-3544`) introduced `HRV_DFA_A1` graph/scatter; `5deec7cd` (`ATT-3554`) and `ATT-6717` wired `dfaA1ThresholdT1`/`T2`.
  3. *Root Reason for Existing Formulation*: `ATT-3544` focused on centered recompute, `[0.2, 1.4]` clamping, and artifact hatching, leaving `zoneBands` unimplemented for `HRV_DFA_A1` and `HrvAlpha1ScatterChart` on default thresholds.
  4. *Preservation of Core Invariants*: `HR`/`Power` solid curves (`REQ-UI-230`), `[0.2, 1.4]` Y-clamping, `> 5.0%` artifact gap hatching (`REQ-PER-035.2`), `50.dp`/`52.dp` margins, and `α1 == T1 -> Zone3` (`TrackingHrvZoneIndicatorTest`) remain untouched.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* valid `HRV_DFA_A1` trackpoints and custom `T1`/`T2` within `(0.2, 1.4)`, *When* rendering `TelemetryMetricGraph`, *Then* 3 inverted zone bands (`Zone4` Anaerob top, `Zone3` Schwelle middle, `Zone2` Aerob bottom per `REQ-HRV-046`), matching gradient stroke/fill, filtered threshold dashes, and zone-colored cursor dot render across Light/Dark/AMOLED without margin collisions.
  • *Given* `T1 = 1.50f` (`> 1.4`), *When* computing threshold dashes and bands for `[0.2, 1.4]`, *Then* `T1` dash is filtered out and the top band clamps cleanly to `1.4`.
  • *Given* scatter points in `HrvAlpha1ScatterChart` with custom `t1`/`t2`, *When* rendering dots, *Then* each dot color matches `HrvZoneIndicator.calculateZoneColor(p.alpha1, t1, t2)`.
- **Invariants**: `HR`/`Power` solid strokes preserved, `α1 == T1 -> Zone3` preserved, zero `src/main` reads in tests (`REQ-PRO-049.1`), zero DB/DataStore schema changes.

---

### REQ-PER-044: Multi-Source Track Layers, Elevation/Speed/Slope Overlays & Per-Chart Visibility in Workout Details (`ATT-8080`) [Verified]
- **System Requirement(s)**: Derived (SW-only) — Post-workout multi-source track/elevation/telemetry overlays and per-chart visibility selection in Workout Details
- **Target Release**: `V5.0.0`
- **Tests**: `TST-PER-052`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `MapModels.kt`, `WorkoutMultiSourceSampleDiscovery.kt`, `WorkoutRepository.kt`, `MyPreferenceManager.kt`, `TelemetryGraphColorPreferences.kt`, `TelemetryMetricUtils.kt`, `TelemetryOverlayComponents.kt`, `TelemetryMetricGraph.kt`, `ElevationProfileOverlayComponents.kt`, `ElevationProfile.kt`, `TelemetryChartStack.kt`, `MapDetailLayout.kt`, `TrackOnMapScreen.kt`, `TrackOnMapAftermathViewModel.kt`, `PeriodMapScreen.kt`, `PeriodsViewModel.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClustersScreen.kt`, `res/values*/strings*.xml`
- **Rationale**: Alongside `ATT-8096` (`HR`/`POWER`/`CADENCE`), `WorkoutSamples.db` persists parallel location, speed, altitude, and slope streams (`ATT Odometrie-Fusion`, `ATT Fused (Höhe und Steigung)`, `GPS`, `Google Fused`, `Barometer`, `DEM`), yet Workout Details lacks `ATT_FUSED` map track selection (and consistent `showTechnicalTracks = true` on bottom-sheet hosts), lacks multi-source overlays on `ElevationProfile`, `SPEED`/`PACE`, and `SLOPE`, and provides no persisted per-chart visibility filter inside `CHARTS` (`ATT-8080`).
- **Description**:
  1. *`TrackType.ATT_FUSED`, Localized Alias Resolution & Bottom-Sheet Wiring (`REQ-PER-044.1`)*:
     - Extending `REQ-MAP-011` / `REQ-MAP-012`, `MapModels.kt` SHALL add `TrackType.ATT_FUSED` (`LATITUDE_att_fused` / `LONGITUDE_att_fused`), remove `TrackType.NETWORK`, and resolve underscore/localized column aliases for `GPS` and `FUSED`. `PeriodMapScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, and `WorkoutClustersScreen.kt` SHALL wire `showTechnicalTracks = true`, `availableTrackTypes`, `enabledTrackTypes`, `onToggleTrackType`, and `multiSensorOverlays`.
  2. *Generic Multi-Source Discovery, Metric-Aware Accumulation & Helper Extraction (`REQ-PER-044.2`)*:
     - Multi-source column discovery and overlay accumulation SHALL be extracted to `WorkoutMultiSourceSampleDiscovery.kt` (retaining `WorkoutRepository$Companion` delegating methods for `DetailedDesignParityContractTest` and keeping `WorkoutRepository.kt <= 5,117` LoC).
     - `discoverNamedSourceColumns` SHALL match both `"^${Regex.escape(sensorType.name)} \\((.+)\\)$"` (including `"att_fused_altitude_slope"` and nested parentheses like `"Topografische Höhe (DEM)"`) and `"^${Regex.escape(sensorType.name)}_(gps|google_fused|att_fused)$"` (matching `CSVFileWriter.java:87`, excluding `_network`), mapping technical identifiers (`att_fused`, `att_fused_altitude_slope`, `gps`, `google_fused`) to clean display labels while preserving `ATT-8096` `HR`/`POWER`/`CADENCE` behavior (`>= 2` named sources).
     - `discoverMultiSourceOverlayColumns` SHALL discover `ALTITUDE`, `SPEED_mps`, and `SLOPE` alongside `HR`, `POWER`, and `CADENCE` across both `loadWorkoutDetailTelemetry` (GPS) and `loadTracklessWorkoutTelemetry` (trackless). `OverlaySeriesAccumulator` SHALL validate samples per metric: `isFinite()` for `ALTITUDE` and `SLOPE` (retaining `<= 0.0` descents, `0%` flats, and `<= 0 m` coastal elevations), `>= 0.0` for `SPEED_mps` and `POWER`, and `> 0.0` for `HR` and `CADENCE`.
  3. *Multi-Source `ElevationProfile`, `SPEED`/`PACE` Unit Conversion & `TelemetryMetricType.SLOPE` (`REQ-PER-044.3`)*:
     - `TelemetryMetricType` SHALL add `SLOPE` (`%` formatting, `TelemetryGraphColorPreferences` key `telemetry_graph_color_SENSOR_SLOPE` matching `SensorType.SLOPE` with `THEME_DEFAULT` default).
     - `ElevationProfile.kt` (with extracted `ElevationProfileOverlayComponents.kt`, `<= 1,500` LoC) SHALL accept `overlaySeries: List<TelemetrySourceSeries>` and, strictly when `>= 2` `ALTITUDE` sources exist, expose a compact `Layers` selector, render distinct secondary curves within shared bounds, display a compact `Ø` mean comparison legend, and update readouts during synchronized `DISTANCE`/`TIME` scrubbing.
     - `TelemetryMetricGraph.kt` (with extracted `TelemetryOverlayComponents.kt`, `<= 1,965` LoC) SHALL render multi-source overlays for `SPEED` (`MyHelper.mps2userUnit`), `PACE` (user pace min/km or min/mi with inverted Y-axis clamping), and `SLOPE` (`%`) with synchronized scrubbing and `Rule 64` plot bounds.
  4. *Persisted Per-Chart Visibility Selection & Canonical Order in Workout Details (`REQ-PER-044.4`, refined by `ATT-9355`)*:
     - `MyPreferenceManager` SHALL persist per-chart visibility (`hidden_telemetry_charts` DataStore `stringSetPreferencesKey` keyed by enum `.name`, default empty = all charts visible on upgrade) across `TelemetryChartKind` in canonical order: `SPEED_PACE`, `SLOPE`, `CADENCE`, `POWER`, `HEART_RATE`, `W_PRIME_BAL`, `HRV_DFA_A1`, `HRV_RMSSD` (`ATT-9355`).
     - Shared `TelemetryChartStack.kt` (reused by `MapDetailLayout.kt <= 1,500` LoC and `TrackOnMapScreen.kt`) SHALL render both stacked graphs and the Material 3 `DropdownMenu` chart-visibility selector (`design_guidelines.md §1`) in that exact canonical order, stay accessible even when charts are hidden, preserve `Rule 64` horizontal plot alignment and `Rule 48` axis labels, and gate `hasTelemetrySeries` / `hasTelemetryGraphs` across `SLOPE` and `CADENCE` as well.
  5. *9-Locale Parity & Rule 8.5 Visual Compose Verification (`REQ-PER-044.5`)*:
     - All new strings (`track_type_att_fused`, `graph_heading_slope`, chart-visibility filter labels, source display labels, and `Ø` mean strings with indexed positional specifiers `%1$s`/`%1$d`) SHALL achieve 100% 9-locale parity (`EN/DE/ES/FR/IT/JA/NL/PL/PT`) with zero inline emojis (`ATT-5400`), and `track_type_network` SHALL be removed cleanly. Stage 4 SHALL render and inspect `docs/engineering/walkthroughs/img/ATT-8080_multisource_charts_visibility.png` (Light & Dark) via `read_file` (`Rule 8.5`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Extends `REQ-PER-042.1` (`WorkoutRepository.kt` regex), `REQ-MAP-011`, `REQ-MAP-012`, `REQ-SEN-027.2` (`ATT-5492`), `REQ-UI-206`, `REQ-UI-260`, `REQ-DAT-033`, `REQ-UI-408`, and `REQ-UI-530`.
2. *Historical Origin & Commit Trace*: `ATT-5492` excised `SPEED_AND_LOCATION_NETWORK`; `71fdb559` (`ATT-8096`) added `REQ-PER-042` multi-sensor discovery (`(<deviceName>)`) and overlays for `HR`/`POWER`/`CADENCE`.
3. *Root Reason for Existing Formulation*: `ATT-8096` limited regex discovery to `(<deviceName>)` and `> 0.0` samples because BLE/ANT+ HR/Power/Cadence sensors never use underscore suffixes or negative values.
4. *Preservation of Core Invariants*: Single-source (`<= 1` named source) `ElevationProfile` and `TelemetryMetricGraph` render zero `Layers` clutter; `ATT-8096` `HR`/`POWER`/`CADENCE` behavior and `telemetry_graph_color_SENSOR_CADENCE` preserved; all charts remain enabled by default for existing users; `Rule 64` & `Rule 48` alignment and all 4 file LoC ceilings preserved (`WorkoutRepository.kt <= 5,117`, `TelemetryMetricGraph.kt <= 1,965`, `MapDetailLayout.kt <= 1,500`, `ElevationProfile.kt <= 1,500`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout recorded with `SPEED_AND_LOCATION_ATT_FUSED`, `GPS`, and `Google Fused`, *When* opening the Map `Layers` menu in Workout Details (including peek bottom sheets in Period Map and Cluster Heatmap), *Then* `Best`, `GPS`, `Google Fused`, and `ATT Fused` are selectable with distinct colors and `Network` is absent.
• *Given* a `WorkoutSamples.db` table with `>= 2` `ALTITUDE*`, `SPEED_mps*`, or `SLOPE*` columns (including `SPEED_mps_att_fused`, `ALTITUDE_att_fused`, `"ALTITUDE (att_fused_altitude_slope)"`, `"SLOPE (att_fused_altitude_slope)"` with negative descents), *When* toggling `Layers` on `ElevationProfile` or `SPEED`/`PACE`/`SLOPE` charts, *Then* secondary curves render with `Ø` mean comparison and synchronized `DISTANCE`/`TIME` scrubbing.
• *Given* the `CHARTS` section in Workout Details, *When* toggling individual charts off/on (e.g., showing `Slope` and hiding `W'bal`), *Then* visibility updates immediately, persists across workouts, preserves `Rule 64` alignment, and leaves the filter menu accessible.

**Invariants**: `<= 1` source shows zero overlay controls; `ATT-8096` `HR`/`POWER`/`CADENCE` & `WorkoutSummary.kt` unaffected; `Rule 64` & `Rule 48` preserved; zero `WorkoutSamples.db` schema bumps; 100% 9-locale parity; all LoC baselines respected.

---

### REQ-PER-045: Segmented Activity & Phone Carriage Mode Timeline Bar & Phase Breakdown in Workout Details [Verified]
- **System Requirement(s)**: Derived (SW-only) — Post-workout timeline visualization of existing `SensorType.ACTIVITY_AND_CARRIAGE_MODE` 1-Hz telemetry (`REQ-FIL-028.2`, zero `SYS.2`/`SYS.3` external/sensor changes)
- **Target Release**: `V5.0.0`
- **Tests**: `TST-PER-053`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `ActivityCarriageTimeline.kt`, `ActivityCarriageTimelineCalculator.kt`, `ActivityCarriageTimelineCard.kt`, `Color.kt`, `WorkoutRepository.kt`, `TrackOnMapAftermathViewModel.kt`, `TrackOnMapScreen.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutSummariesTabbedScreen.kt`, `WorkoutSummariesListFragment.kt`, `WorkoutClustersScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, `PeriodMapScreen.kt`, `res/values*/strings_workout_details.xml`, `docs/architecture/detailed_design/workout_repository.md`
- **Rationale**: `ATT-8890` (`REQ-FIL-028`) records 1-Hz `SensorType.ACTIVITY_AND_CARRIAGE_MODE` states (`UNKNOWN(-1)`, `STATIONARY(0)`, `WALKING_TRANSITION(1)`, `BIKE_HANDLEBAR(2)`, `BIKE_JERSEY_BACK(3)`, `BIKE_FRONT_POCKET(4)`, `RUN_WAIST_HIP(5)`, `RUN_BICEP_ARM(6)`, `RUN_HANDHELD(7)`) into `WorkoutSamples.db`, yet Workout Details currently surfaces none of this activity/carriage phase history. Athletes reviewing multi-modal rides or brick sessions need an interactive post-workout timeline bar and phase breakdown while cleanly omitting the section on legacy or non-`ATT_FUSED` workouts (`ATT-8916`).
- **Description**:
  1. *Pure-JVM 1-Hz Segment Coalescing & Sliver/Pause/Zero-Distance Rules (`REQ-PER-045.1`)*:
     - `ActivityCarriageTimelineCalculator` (pure Kotlin, JVM-testable) SHALL coalesce full-resolution 1-Hz `(timeSec, distanceMeters, rawCode)` tuples (`NULL` mapped to `PhoneCarriageMode.UNKNOWN`) into contiguous `ActivityCarriageSegment(mode, startSec, endSec, durationSeconds, distanceMeters, durationPercentage, distancePercentage)` spans and per-mode `ActivityCarriageModeSummary(mode, durationSeconds, distanceMeters, durationPercentage, distancePercentage, segmentCount)` totals.
     - Per-row duration `dt_i` SHALL use `(t_{i+1} - t_i).coerceIn(1L, 5L)` (`1L` for trailing/single row, matching `ZoneDistributionCalculator.MAX_DELTA_T_SEC = 5L`) so Auto-Pause/manual pause wall jumps never inflate adjacent segment durations; per-row `deltaDist_i` SHALL attribute `(d_{i+1} - d_i).coerceAtLeast(0.0)` when both distances are finite (`0.0` otherwise).
     - Warmup/transient `PhoneCarriageMode.UNKNOWN` (`-1`) slivers with `durationSec <= 5L` SHALL be absorbed into an adjacent active segment (preceding active span if present, else following active span, coalescing newly adjacent identical active modes), whereas **all** active mode (`0..7`) transitions of `>= 1s` SHALL be preserved without debouncing. Any `UNKNOWN` span with `durationSec > 5L` SHALL be retained as a visible `UNKNOWN` segment.
     - Denominators: `totalDurationSec = segments.sumOf { it.durationSec }` (including retained `> 5s` `UNKNOWN` gaps) and `totalDistanceMeters = segments.sumOf { it.distanceMeters }`. `durationPercentage = (modeDurationSec.toFloat() / totalDurationSec) * 100f`; `distancePercentage = if (totalDistanceMeters > 0.0) (modeDistanceMeters / totalDistanceMeters).toFloat() * 100f else 0f`.
  2. *Legacy & All-`UNKNOWN` Graceful Omission (`REQ-PER-045.2`)*:
     - When `SensorType.ACTIVITY_AND_CARRIAGE_MODE.name` is absent from `WorkoutSamples.db` table columns (`existingColumns`) OR when zero samples decode to an active mode (`0..7`, i.e., all rows are `NULL` or `UNKNOWN(-1)`), `ActivityCarriageTimelineCalculator` / `WorkoutRepository` SHALL return `null` without executing invalid column SQL or emitting an empty placeholder.
  3. *Pre-Decimation Single-Pass Loading & Full Caller Propagation (`REQ-PER-045.3`)*:
     - `WorkoutRepository.loadWorkoutDetailTelemetry(workoutId, bSportType)` SHALL inspect `hasCarriageMode = SensorType.ACTIVITY_AND_CARRIAGE_MODE.name in existingColumns`, include `hasCarriageMode` in the early-exit guard (`L2472`), project `ACTIVITY_AND_CARRIAGE_MODE` in the single-pass cursor scan BEFORE track simplification (`800`-point cap) or trackless decimation (`800`-point cap), and populate `WorkoutDetailTelemetry.activityCarriageTimeline: ActivityCarriageTimeline? = null` (trailing default `= null`).
     - `AftermathMapUIState` (`TrackOnMapAftermathViewModel.kt`), `TrackOnMapScreen`, and `WorkoutDetailTabbedContent` SHALL expose trailing `activityCarriageTimeline: ActivityCarriageTimeline? = null`. `TrackOnMapAftermathViewModel.loadAftermathData` SHALL reset `activityCarriageTimeline = null` when `!isSameWorkout`, propagate `telemetry?.activityCarriageTimeline` in **both** the GPS tracks branch (`L253-257`) AND the trackless `else` fallback branch (`L259-271`), and wire `activityCarriageTimeline` across all 5 `TrackOnMapScreen` hosts (`WorkoutSummariesTabbedScreen`, `WorkoutSummariesListFragment`, `WorkoutClustersScreen`, `WorkoutClusterHeatmapScreen`, `PeriodMapScreen`).
     - `WorkoutSamplesDatabaseManager.java` (`1,533` LoC) MUST incur `0` LoC growth; `WorkoutRepository.kt` MUST stay `<= 5,117` LoC (and `docs/architecture/detailed_design/workout_repository.md` SHALL sync any `WorkoutRepository` telemetry signature changes in Stage 4).
  4. *Segmented Timeline Ribbon, Phase Breakdown UI & `"v3|"` Preservation (`REQ-PER-045.4`)*:
     - `ActivityCarriageTimelineCard` SHALL render conditionally inside `WorkoutDetailSectionType.DETAILS` (`WorkoutDetailTabbedContent.kt`, after `RadarTrafficSummaryCard`) solely when `activityCarriageTimeline != null && activityCarriageTimeline.segments.isNotEmpty()`, leaving `WorkoutDetailTabConfig` `"v3|"` schema, `14`-entry `WorkoutDetailSectionType`/`WorkoutSectionType` enums (`WorkoutDetailTabsTest`), `WorkoutMasksAndCardsSection.kt` (`< 400` LoC), and `WorkoutSummary.kt` untouched.
     - The card SHALL render: (a) a color-coded continuous proportional timeline bar weighted strictly by **duration** (`durationSec.toFloat().coerceAtLeast(1f) / > 0f`, ensuring `STATIONARY` stops and zero-distance indoor workouts render cleanly without `Modifier.weight(<= 0f)` crashes) with `1.dp` surface dividers; (b) per-mode summary chips/rows reusing `PhoneCarriageMode.labelResId` (`9/9` `strings_devices.xml`), semantic carriage icon, duration, distance (when `totalDistanceMeters > 0.0`, or clean time/share focus when `0.0`), and duration percentage; and (c) an expandable chronological phase breakdown list (`isExpanded` toggle) displaying each contiguous segment's time span (`startTimeSec..endTimeSec`), duration, and distance.
  5. *Color Centralization, 9-Locale Parity & Behavioral Compose Verification (`REQ-PER-045.5`)*:
     - All carriage segment color tokens SHALL be centralized in `TTColor` (`ui/theme/Color.kt`) with WCAG 2.1 non-text contrast `>= 3.0:1` across Light/Dark/AMOLED (`ThemeColorCentralizationAuditTest`), with zero `Color(0x...)` literals outside `Color.kt`.
     - New card/breakdown UI strings SHALL achieve 100% 9-locale parity (`EN/DE/ES/FR/IT/JA/NL/PL/PT`) in `values*/strings_workout_details.xml` with zero emojis (`ATT-5400`) and valid positional format specifiers (`%1$d`, `%1$s`). Behavioral Compose UI tests SHALL use `createComposeRule()` (`UiReflectionOnlyTestAuditContractTest`) with zero `src/main` reads (`SourceTextReadingAuditContractTest`) and Stage 4 Light/Dark PNG verification (`Rule 8.5`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-PER-045` refining/consuming `REQ-FIL-028.2` (`PhoneCarriageMode` / `ACTIVITY_AND_CARRIAGE_MODE`), `REQ-UI-2787.1` (`WorkoutDetailTabConfig` `"v3|"`), and `REQ-PRO-129.2` (`WorkoutSamplesDatabaseManager`/`WorkoutRepository` LoC ceilings).
2. *Historical Origin & Commit Trace*: `fee3e805` (`ATT-8890` 1-Hz carriage classification), `58982719` (`ATT-8079` `"v3|"` tab persistence), `624bea5e` / `1b33fcba` (`RadarTrafficSummaryCard` / `CardioPowerDynamicsCard` in `DETAILS`).
3. *Root Reason for Existing Formulation*: `"v3|"` (`14` sections) preserves user section removals across reloads; conditional cards in `DETAILS` (`tab_overview`) surface sensor-conditional analytics without mutating tab schemas or breaching `WorkoutMasksAndCardsSection`'s `400`-LoC limit.
4. *Preservation of Core Invariants*: `PhoneCarriageMode` `-1..7` & `9/9` `strings_devices.xml` keys unchanged; `"v3|"` & `14`-entry enums unchanged; `WorkoutSamplesDatabaseManager.java == 1,533` LoC; `WorkoutRepository.kt <= 5,117` LoC.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a `WorkoutSamples.db` session containing 1-Hz `ACTIVITY_AND_CARRIAGE_MODE` transitions (`UNKNOWN(2s) -> BIKE_FRONT_POCKET -> WALKING_TRANSITION(3s) -> STATIONARY -> WALKING_TRANSITION -> BIKE_FRONT_POCKET`), *When* loaded via `WorkoutRepository.loadWorkoutDetailTelemetry` (GPS or trackless), *Then* warmup `UNKNOWN(<=5s)` absorbs cleanly, all 5 active phases (`1–5s` or longer) survive pre-decimation coalescing with accurate duration/distance totals, and `ActivityCarriageTimelineCard` renders the proportional bar, summary rows, and expandable chronological phases.
• *Given* a legacy workout lacking `ACTIVITY_AND_CARRIAGE_MODE` OR a workout with only `NULL`/`UNKNOWN(-1)` rows, *When* opening Workout Details, *Then* `activityCarriageTimeline` is `null` and `ActivityCarriageTimelineCard` is completely omitted with zero SQL exceptions or empty placeholders.
• *Given* a zero-distance indoor/stationary workout (`totalDistanceMeters == 0.0`) or a `> 5s` `UNKNOWN` sensor gap, *When* computing summaries and rendering `ActivityCarriageTimelineCard`, *Then* `durationPercentage` sums to `100%` (including `> 5s` `UNKNOWN` spans), `distancePercentage == 0f` without `NaN`, and Compose `Modifier.weight` stays strictly `> 0f`.

**Invariants**: Pre-decimation 1-Hz coalescing; `<= 5s` `UNKNOWN` sliver suppression without debouncing `1–5s` active modes (`0..7`); `<= 5s` pause `dt` clamp; dual GPS/trackless `ViewModel` forwarding & workout-switch reset; trailing `= null` constructors; `"v3|"` & `14`-entry enums preserved; `WorkoutSamplesDatabaseManager.java == 1,533` & `WorkoutRepository.kt <= 5,117`; `TTColor` centralized; `9/9` locale parity; `createComposeRule()` UI tests with `0` `src/main` reads.

---

### REQ-HRV-045: Preserve Full HRV Measurement UI via Route Override from AthleteProfile HRV Tab (`ATT-8846`) [Verified]
- **System Requirement(s)**: Derived (SW-only) — Parameterized `NavRoutes.MORNING_HRV` route override (`forceFullHrv`) preserving full HRV measurement UI when launched from `AthleteProfileScreen` HRV tab (refining `REQ-HRV-043`, `REQ-PER-032`, `REQ-HRV-040`, and `REQ-UI-525`)
- **Target Release**: `V5.0.0`
- **Tests**: `TST-HRV-045`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `NavRoutes.kt`, `ATrainingTrackerApp.kt`, `MorningHrvCheckScreen.kt`
- **Rationale**: Following `ATT-8598` (`REQ-HRV-043`), `MorningHrvCheckScreen` enters pure wellness mode whenever `showMorningCheckOnStartScreen == false && enableMorningSleepAndWellness == true && phase == MorningHrvPhase.READY`. Because `TrackingTabsScreen` (`ATrainingTrackerApp.kt:351`) and `AthleteProfileScreen` HRV tab (`ATrainingTrackerApp.kt:633`) both navigate to the identical parameterless `NavRoutes.MORNING_HRV`, athletes who hide the Start Screen HRV prompt cannot initiate an on-demand morning HRV measurement from *Athlete Profile -> HRV* (`ATT-8846`).
- **Description**:
  1. *Parameterized Route & Drawer Symmetry (`REQ-HRV-045.1`)*:
     - `NavRoutes` (`NavRoutes.kt`) SHALL define `ARG_FORCE_FULL_HRV = "forceFullHrv"`, `MORNING_HRV_PATTERN = "morning_hrv?forceFullHrv={forceFullHrv}"`, and `fun morningHrv(forceFullHrv: Boolean = false): String` returning `MORNING_HRV` (`"morning_hrv"`) when `false` and `"morning_hrv?forceFullHrv=true"` when `true`.
     - `NavRoutes.MORNING_HRV == "morning_hrv"`, `NavRoutes.fromDrawerItemId(R.id.drawer_morning_hrv) == MORNING_HRV`, and `NavRoutes.toDrawerItemId(...)` (stripping `?` query parameters) SHALL remain strictly backward compatible.
  2. *Entry-Source Navigation Wiring (`REQ-HRV-045.2`)*:
     - `ATrainingTrackerApp.kt` SHALL register `composable(route = NavRoutes.MORNING_HRV_PATTERN, arguments = listOf(navArgument(NavRoutes.ARG_FORCE_FULL_HRV) { type = NavType.BoolType; defaultValue = false }))` extracting `forceFullHrv` and passing it to `MorningHrvCheckScreen`.
     - `AthleteProfileScreen` HRV tab (`ATrainingTrackerApp.kt:633`) SHALL navigate via `NavRoutes.morningHrv(forceFullHrv = true)`; `TrackingTabsScreen` Start Screen (`ATrainingTrackerApp.kt:351`) SHALL navigate via `NavRoutes.morningHrv(forceFullHrv = false)`.
  3. *Full HRV Override & Pure Wellness Regression Guard (`REQ-HRV-045.3`)*:
     - `MorningHrvCheckScreen` and `MorningHrvCheckContent` (`MorningHrvCheckScreen.kt`) SHALL accept `forceFullHrv: Boolean = false` and derive `isPureWellnessMode = !forceFullHrv && !uiState.showMorningCheckOnStartScreen && uiState.enableMorningSleepAndWellness && uiState.phase == MorningHrvPhase.READY`.
     - When `forceFullHrv == true` (regardless of `showMorningCheckOnStartScreen`), `MorningHrvCheckContent` SHALL render the complete HRV measurement UI (TopAppBar `morning_hrv_title`, `morning_hrv_subtitle`, `morning_hrv_info_button` opening `HrvInfoBottomSheet`, `WakingProtocolChecklistCard`, `PostureAndDurationCard`, `MeasurementPacingCard`, and primary `morning_hrv_start_button`). `MorningWellnessInputCard` and secondary `OutlinedButton` *Save Morning Wellness* (`morning_hrv_save_wellness_secondary_button`) SHALL render iff `enableMorningSleepAndWellness == true`.
     - When `forceFullHrv == false` with `!showMorningCheckOnStartScreen && enableMorningSleepAndWellness && phase == MorningHrvPhase.READY` (Start Screen / Drawer entry), pure wellness mode (`REQ-HRV-043`) SHALL remain strictly preserved.
  4. *Architecture Ceilings, Private Previews & Localization (`REQ-HRV-045.4`)*:
     - `MorningHrvCheckScreen.kt` MUST remain `<= 1,500` LoC (`ArchitectureConformanceContractTest`), `AthleteProfileScreen.kt` MUST incur `0` edits (preserving `1,975/1,975` LoC ceiling), `MorningHrvCheckViewModel.kt` MUST remain untouched, any added `@Preview` composable MUST be `private fun` (`REQ-ARC-003.5` / `DeadCodeEliminationContractTest`), and `Localization Parity: N/A - no UI strings`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: `REQ-HRV-043` (`REQ-HRV-043.1`, `REQ-HRV-043.3`, `ATT-8598`), `REQ-PER-032`, `REQ-HRV-040`, and `REQ-UI-525`, targeting `NavRoutes.kt`, `ATrainingTrackerApp.kt`, and `MorningHrvCheckScreen.kt`.
  2. *Historical Origin & Commit Trace*: `REQ-HRV-043` introduced in `ATT-8598` (`596a54f3`), whose Stage 6 Cold Review explicitly noted shared parameterless `NavRoutes.MORNING_HRV` across Start Screen and `AthleteProfileScreen` as residual risk, leading to `ATT-8846`.
  3. *Root Reason for Existing Formulation*: `ATT-8598` scoped strictly to hiding HRV cards in Start Screen pure wellness mode without introducing route arguments, relying on `!enableMorningSleepAndWellness` as the only profile fallback.
  4. *Preservation of Core Invariants*: Adding optional `forceFullHrv: Boolean = false` preserves 100% of `REQ-HRV-043` Start Screen pure wellness mode, preserves `MorningWellnessInputCard` + secondary save button alongside HRV when `enableMorningSleepAndWellness == true`, preserves active measurement/result phase guards & `keepScreenOn`, and introduces zero DB, ViewModel, or localization changes.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `showMorningCheckOnStartScreen = false` and `enableMorningSleepAndWellness = true`, *When* opening `MorningHrvCheckScreen` from *Athlete Profile -> HRV* tab (`forceFullHrv = true`), *Then* TopAppBar HRV title/subtitle/info button (`HrvInfoBottomSheet`), HRV protocol/posture/duration/pacing cards, primary *Start Measurement* CTA, `MorningWellnessInputCard`, and secondary *Save Morning Wellness* button all render.
  • *Given* `showMorningCheckOnStartScreen = false` and `enableMorningSleepAndWellness = false`, *When* `forceFullHrv = true`, *Then* full HRV measurement UI renders without `MorningWellnessInputCard` or wellness save CTA.
  • *Given* `showMorningCheckOnStartScreen = false` and `enableMorningSleepAndWellness = true`, *When* opening from Start Screen or Drawer (`forceFullHrv = false`), *Then* pure wellness mode (`REQ-HRV-043`) renders unchanged.
- **Invariants**: Start Screen pure wellness mode (`REQ-HRV-043`) preserved; `NavRoutes.MORNING_HRV` & drawer mapping backward compatible; `MorningHrvCheckScreen.kt <= 1,500` LoC; `AthleteProfileScreen.kt == 1,975` LoC (`0` edits); `MorningHrvCheckViewModel.kt` untouched; `Localization Parity: N/A - no UI strings`.

---

### REQ-HRV-046: Inverted Y-Axis Orientation for Workout Details DFA alpha 1 Graph (`ATT-8953`) [Verified]
- **System Requirement(s)**: Derived (SW-only) — Inverted Y-axis mapping (`0.20` Anaerob top -> `1.40` Aerob bottom) for `TelemetryMetricGraph` (`HRV_DFA_A1`) and aligned marginal histograms (refining `REQ-HRV-044.1` and `REQ-PER-035.3`)
- **Target Release**: `V5.0.0`
- **Tests**: `TST-HRV-046`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `TelemetryZoneMath.kt`, `TelemetryMetricGraph.kt`
- **Rationale**: In Workout Details, `TelemetryMetricGraph(HRV_DFA_A1)` stacks vertically alongside Heart Rate, Power, and inverted `PACE` graphs (`Rule 64`), yet currently maps larger $\alpha_1$ values (`1.40` Aerob) to the top and smaller $\alpha_1$ values (`0.20` Anaerob) to the bottom, causing harder exercise intensity to slope downward. Inverting the `HRV_DFA_A1` Y-axis aligns visual effort direction across all stacked telemetry charts (`ATT-8953`).
- **Description**:
  1. *Shared Inverted Y-Axis Mapping Across All 3 Canvases (`REQ-HRV-046.1`)*:
     - `TelemetryZoneMath` SHALL provide shared pure helpers (`isInvertedTelemetryYAxis(metricType)` returning `true` for `PACE` and `HRV_DFA_A1`; `mapTelemetryValueToY(value, dataMin, dataMax, topPaddingPx, chartHeightPx, isInverted)`) reused by `TelemetryMetricGraph`, `AlignedMarginalLinearDistribution`, and `AlignedMarginalZoneDistribution`, mapping `dataMin` (`0.20`, Anaerob) to `topPaddingPx` (top) and `dataMax` (`1.40`, Aerob) to `topPaddingPx + chartHeightPx` (bottom).
  2. *Inverted Vertical Gradient Stops & Exact Boundary Inequalities (`REQ-HRV-046.2`)*:
     - `TelemetryZoneMath.buildHrvDfaA1VerticalGradientStops` SHALL map stop `0f` (top) to `dataMin` and `1f` (bottom) to `dataMax` with monotonically non-decreasing stops (`effT2` at `(effT2 - dataMin) / range`, then `effT1` at `(effT1 - dataMin) / range`), using `topBaseColor = when { dataMin < effT2 -> Zone4; dataMin < effT1 -> Zone3; else -> Zone2 }` and `bottomBaseColor = when { dataMax > effT1 -> Zone2; dataMax > effT2 -> Zone3; else -> Zone4 }`, cleanly handling exact bounds (`T2 = 0.20`, `T1 = 1.40`), degenerate `T1 == T2`, and out-of-range thresholds (`T1 = 1.50f`, `T2 = 0.15f`).
  3. *Inverted Y-Axis Labels, Zone Bands, Fill, Dashes, Histograms & Scatter Invariance (`REQ-HRV-046.3`)*:
     - For `HRV_DFA_A1`, `TelemetryMetricGraph` SHALL render `dataMin` (`0.20`) at the top Y-axis label (`maxLabel`) and `dataMax` (`1.40`) at the bottom Y-axis label (`minLabel`), align Anaerob (`Zone4` red) top / Schwelle (`Zone3` orange) middle / Aerob (`Zone2` green) bottom across zone bands, gradient curve/fill (filling down to bottom `baselineY`), threshold dashes/labels, cursor/single-point dots, and portrait/landscape marginal histograms. `HrvAlpha1ScatterChart.kt` SHALL remain untouched in standard Cartesian orientation (`REQ-PER-035.4`).
  4. *Architecture Ceilings, Asset Integrity & Private Previews (`REQ-HRV-046.4`)*:
     - `TelemetryMetricGraph.kt` MUST stay `<= 1,965` LoC (`ArchitectureConformanceContractTest`), any new `@Preview` MUST be `private fun` (`DeadCodeEliminationContractTest`), tests MUST perform `0` `src/main` reads (`SourceTextReadingAuditContractTest`), historical `docs/engineering/walkthroughs/img/ATT-8710_dfa_a1_zone_graph.png` MUST remain untouched while capturing new Light/Dark PNG to `docs/engineering/walkthroughs/img/ATT-8953_dfa_a1_inverted_yaxis.png`, and `Localization Parity: N/A - no UI strings`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: `REQ-HRV-044` (`REQ-HRV-044.1`, `TST-HRV-044.1`, `ATT-8710`) and `REQ-PER-035.3`, targeting `TelemetryZoneMath.kt` and `TelemetryMetricGraph.kt`.
  2. *Historical Origin & Commit Trace*: `ATT-3544` (`3db57718`) added `[0.2, 1.4]` DFA $\alpha_1$ plotting; `ATT-8710` (`886eae05`) added 3-zone bands and gradient on the non-inverted Y-axis (`Zone2` top, `Zone4` bottom).
  3. *Root Reason for Existing Formulation*: `ATT-8710` matched `TelemetryMetricGraph`'s default `(1.0 - ratio)` orientation without inverting Y, resulting in green easy effort at the top and red hard effort at the bottom.
  4. *Preservation of Core Invariants*: Preserves `[0.2, 1.4]` clamping, `> 5.0%` artifact hatching, `50.dp`/`52.dp` margins (`Rule 64`), `α1 == T1 -> Zone3` boundary polarity, `HEART_RATE`/`POWER` solid curves (`REQ-UI-230`), `PACE` inversion, and `HrvAlpha1ScatterChart` Cartesian orientation (`REQ-PER-035.4`).
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `HRV_DFA_A1` samples across `[0.2, 1.4]` with `T1 = 0.75`, `T2 = 0.50`, *When* rendering `TelemetryMetricGraph` in portrait or landscape, *Then* `0.20` renders at top, `1.40` at bottom, Anaerob `Zone4` red occupies top `[0%..25%]`, Schwelle `Zone3` orange occupies middle `[25%..45.8%]`, Aerob `Zone2` green occupies bottom `[45.8%..100%]`, and curve/fill/cursor/histogram align 1:1.
  • *Given* edge thresholds (`T1 == T2`, exact `T2 = 0.20`/`T1 = 1.40`, out-of-range `T1 = 1.50f`/`T2 = 0.15f`), *When* evaluating `buildHrvDfaA1VerticalGradientStops`, *Then* stops remain monotonically non-decreasing in `[0f, 1f]` with exact inverted colors.
- **Invariants**: `HEART_RATE`/`POWER`/`PACE`/`SLOPE`/`HRV_RMSSD` unchanged; `HrvAlpha1ScatterChart.kt` untouched; `TelemetryMetricGraph.kt <= 1,965` LoC; `ATT-8710_dfa_a1_zone_graph.png` untouched; `Localization Parity: N/A - no UI strings`.

---

### REQ-HRV-047: Dynamic Outlier-Resilient Y-Axis Scaling for Workout Details rMSSD Graph (`ATT-9171`) [Verified]
- **System Requirement(s)**: Derived (SW-only) — Dynamic, outlier-resilient Y-axis bounds and clean axis labels for `TelemetryMetricGraph` (`HRV_RMSSD`) and aligned marginal histograms (refining `REQ-PER-035.1`)
- **Target Release**: `V5.0.0`
- **Tests**: `TST-HRV-047`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `TelemetryZoneMath.kt`, `TelemetryMetricGraph.kt`
- **Rationale**: In Workout Details, `TelemetryMetricGraph(HRV_RMSSD)` hardcodes its Y-axis upper bound to `.coerceAtLeast(100.0)`, assuming resting morning HRV scales (`30-100+ ms`). During aerobic/moderate exercise, rMSSD naturally sits at `2-15 ms` (e.g., `1.89-5.64 ms`), flattening the curve along `0` (>94% wasted vertical height) and collapsing the marginal histogram into a single bottom bin, while transient warmup or artifact spikes can similarly squash steady-state exercise traces (`ATT-9171`).
- **Description**:
  1. *Dynamic, Outlier-Resilient Y-Axis Bounds (`REQ-HRV-047.1`)*:
     - `TelemetryZoneMath.calculateHrvRmssdYAxisBounds(values: List<Double>): Pair<Double, Double>` SHALL filter finite values `>= 0.0` (returning `0.0 to 5.0` when empty), evaluate nearest-rank `p95 = sorted[floor(0.95 * (N - 1)).toInt()]` when `N >= 5`, apply spike-ratio gating (`base = if (N >= 5 && p95 > 0.0 && max > p95 * 2.0) p95 else max`), compute `rawUpper = (base * 1.15).coerceAtLeast(5.0)` with finite-overflow protection for extreme `Double` inputs, and round upward to clean nice steps (`1` for `<= 10`, `2` for `<= 20`, `5` for `<= 50`, `10` for `<= 100`, `20` for `> 100`), returning `0.0 to niceCeiling` with zero clipping (`niceCeiling >= max * 1.15`) on genuine non-spike recordings.
  2. *Artifact Exclusion, Reactive `remember` Keys & Clean Y-Axis Labels (`REQ-HRV-047.2`)*:
     - `TelemetryMetricGraph.kt` SHALL delegate `HRV_RMSSD` bounds to `TelemetryZoneMath.calculateHrvRmssdYAxisBounds`, excluding primary samples with `hrvArtifactRate > 5.0` via `cleanPrimary.ifEmpty { validValues } + overlayValues`, and MUST include `pathPoints` and `rawValues` in the `(dataMin, dataMax)` `remember(...)` keys so bounds never go stale. `TelemetryZoneMath.formatHrvRmssdAxisLabel(value: Double, locale: Locale): String` SHALL map non-finite values and `-0.0` to `"0"`, format exact integers without decimals, and format fractional values as `%.1f`, wired into `TelemetryMetricGraph.kt` `maxLabel`/`minLabel`.
  3. *Portrait/Landscape Rescaled Curve, Multi-Bin Histograms & Metric Invariance (`REQ-HRV-047.3`)*:
     - Both the `HRV_RMSSD` curve/fill and portrait (`nonZonedHistogram`) + landscape (`AlignedMarginalLinearDistribution`) marginal histograms SHALL render against the dynamic `(0.0, dataMax)` domain so exercise ranges (`1.89-5.64 ms`) and spike-guarded streams (`2-6 ms` + `45 ms` spike) span vertical chart height and distribute across multiple histogram bins, while `HRV_DFA_A1`, `HEART_RATE`, `POWER`, `CADENCE`, `SPEED`, `PACE`, `SLOPE`, `ALTITUDE`, `W_PRIME_BAL`, `TelemetryMetricUtils.kt`, and `TelemetryHistogramCalculator.kt` remain untouched.
  4. *Architecture Ceilings, Private Previews, Rule 8.5 PNG & Asset Integrity (`REQ-HRV-047.4`)*:
     - `TelemetryMetricGraph.kt` MUST stay `<= 1,965` LoC (`ArchitectureConformanceContractTest`), any added `@Preview` MUST be `private fun` (`DeadCodeEliminationContractTest`), tests MUST perform `0` `src/main` reads (`SourceTextReadingAuditContractTest`), historical `ATT-8710_dfa_a1_zone_graph.png` and `ATT-8953_dfa_a1_inverted_yaxis.png` MUST remain untouched while capturing deterministic Light/Dark PNG strictly to `docs/engineering/walkthroughs/img/ATT-9171_rmssd_dynamic_yaxis.png`, and `Localization Parity: N/A - no UI strings`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: `REQ-PER-035.1` (`ATT-3544`), targeting `TelemetryMetricGraph.kt`.
  2. *Historical Origin & Commit Trace*: Introduced in `ATT-3544` (`3db57718`, `0.0 to (max * 1.15).coerceAtLeast(100.0)`); palette refreshed in `ATT-7759` (`3ce94b53`).
  3. *Root Reason for Existing Formulation*: `ATT-3544` copied `CADENCE`'s `100.0` floor assuming resting morning HRV scales (`30-100+ ms`) rather than exercise rMSSD (`2-15 ms`).
  4. *Preservation of Core Invariants*: Preserves `dataMin = 0.0`, non-inverted upward orientation, `> 5.0%` artifact hatching (`REQ-PER-035.2`), `50.dp`/`52.dp` alignment (`Rule 64`), `TelemetryMetricUtils.formatValue` (`"%.1f ms"`), and all other metric scaling formulas.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* an exercise workout with `HRV_RMSSD` spanning `1.89-5.64 ms`, *When* `TelemetryMetricGraph(HRV_RMSSD)` renders in portrait or landscape, *Then* `dataMin = 0.0`, `dataMax = 7.0` (labels `"0"` / `"7"`), the curve occupies vertical chart height, and the marginal histogram distributes across multiple bins.
  • *Given* steady-state `2-6 ms` exercise rMSSD with a transient warmup or artifact spike (`45 ms`), *When* Y-axis bounds are computed, *Then* artifact exclusion / P95 spike-ratio gating keeps `dataMax = 7.0` without crushing the main trace along `0`.
  • *Given* a resting or low-intensity recording genuinely reaching `40-120 ms` (or `120-180 ms`), *When* `TelemetryMetricGraph(HRV_RMSSD)` renders, *Then* `dataMax` expands smoothly (`140.0` / `220.0`, `>= max * 1.15`) with zero clipping.
- **Invariants**: `HRV_DFA_A1`/`HEART_RATE`/`POWER`/`CADENCE`/`SPEED`/`PACE`/`SLOPE`/`ALTITUDE`/`W_PRIME_BAL` unchanged; `TelemetryMetricGraph.kt <= 1,965` LoC; `TelemetryMetricUtils.kt` & `TelemetryHistogramCalculator.kt` untouched; `ATT-8710`/`ATT-8953` PNGs untouched; `Localization Parity: N/A - no UI strings`.

---

### REQ-HRV-048: Beat-Level Artifact Flag Persistence in WorkoutRr.db & Live vs Post-Workout HRV Stream Reconciliation (`ATT-9160`) [Verified]
- **System Requirement(s)**: Derived (SW-only) — Post-workout persistence of Lipponen & Tarvainen beat-level `artifact_flag`/`gap_beats` into `WorkoutRr.db`, 180s causal trailing quartile filter context in `HRVMetricsDevice`, and unified artifact gate threshold plumbing in `HrvPostWorkoutRecomputer` (refining `REQ-PER-034.3`, `REQ-TRK-060.2`/`.4`/`.5`)
- **Target Release**: `V5.0.0`
- **Tests**: `TST-HRV-048`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `RrArtifactFilter.kt`, `WorkoutRrDatabaseManager.kt`, `WorkoutRrRepository.kt`, `HrvPostWorkoutRecomputer.kt`, `HRVMetricsDevice.kt`
- **Rationale**: Post-workout `HrvPostWorkoutRecomputer` ran Lipponen & Tarvainen Mode B filtering in memory without writing classified `artifact_flag` and `gap_beats` back to `WorkoutRr.db`, leaving all rows at `(0, 0)` even when clear ectopic/missed-beat outliers existed. Concurrently, `HRVMetricsDevice` pruned `mBeatQueue` at `120 s` before `filterLiveCausal` (starving leading beats of their 91-beat quartile window and inflating live edge artifacts) while `HrvPostWorkoutRecomputer` hardcoded a `5.0%` gate threshold rather than honoring `TuningPreferencesDataStore.hrvArtifactGateThresholdPercent` (`ATT-9160`).
- **Description**:
  1. *1:1 Per-Input-Beat Artifact & Gap Metadata (`REQ-HRV-048.1`)*:
     - `RrArtifactFilter`'s `FilterResult` SHALL expose optional `inputArtifactFlags: List<Int> = emptyList()` and `inputGapBeats: List<Int> = emptyList()` aligned 1:1 with the input `beats` list: preserving `max(event.gapBeats, gapCount - 1)` strictly for `ARTIFACT_GAP (5)`, computing `k - 1` freshly for `ARTIFACT_MISSED (4)` and split `ARTIFACT_LONG (3)` (`>= 1.5 * med`), flagging both constituent indices of merged `ARTIFACT_SHORT (2)` pairs with `gap = 0`, and explicitly outputting `gap = 0` for `NORMAL`/`ECTOPIC`/`SHORT`/non-split `LONG` so backfill re-runs clear stale flags. `filterLiveCausal` SHALL precompute `computeMedianDeviationsCentered(rawRrs)` once in $O(N)$.
  2. *Idempotent Delta-Batched Write-Back in `WorkoutRr.db` (`REQ-HRV-048.2`)*:
     - `WorkoutRrDatabaseManager` and `WorkoutRrRepository` SHALL expose transactional `updateBeatQualityFlags(workoutId, updates)` updating `(artifact_flag, gap_beats)` by `(workout_id, beat_index)` via existing `idx_workout_rr_workout_beat` (`DB_VERSION = 2` unchanged). `HrvPostWorkoutRecomputer` SHALL write ONLY changed beats (`newFlag != existingFlag || newGap != existingGap`), yielding `0` DB writes on idempotent re-runs while clearing stale flags/gaps when RR data is backfilled clean.
  3. *180s Causal Filter Context & 120s Active Window Scope in `HRVMetricsDevice` (`REQ-HRV-048.3`)*:
     - `HRVMetricsDevice` SHALL retain `WINDOW_FILTER_CONTEXT_MS = WINDOW_DFA_MS + 60_000L` (`180_000L`) in `mBeatQueue`, check `mBeatQueue.none { it.beatTimestampMs >= now - WINDOW_DFA_MS }` before filtering (clearing all 3 sensors to `null` and resetting `signalQualityResult` on `120 s` drain), slice `dfaWindowBeats = filteredBeats.filter { it.beatTimestampMs >= now - WINDOW_DFA_MS }` (handling empty `dfaWindowBeats` as `artifactRate = 0.0`, `dfaAlpha1 = null`), evaluate `HRV_ARTIFACT_RATE` & `HRV_DFA_A1` strictly on `dfaWindowBeats`, and pass `dfaWindowBeats` (not the 180s buffer) to `HrvSignalQualityAnalyzer.analyzeBeats(...)`.
  4. *Unified Artifact Gate Threshold Plumbing & Preserved Invariants (`REQ-HRV-048.4`)*:
     - `HrvPostWorkoutRecomputer` SHALL accept optional `@JvmOverloads` `artifactGateThresholdPercent: Double? = null` on `recomputeWorkout`/`recomputeWorkoutBlocking` and default `ARTIFACT_GATE_THRESHOLD_PERCENT` on `computeSessionSdnn`, resolving `TuningPreferencesDataStore.hrvArtifactGateThresholdPercent` clamped to `[3.0, 5.0]` with `hasInitializedInstance()` fallback to `5.0`. `DfaCalculator.kt` (NeuroKit2 `0.2.10` golden parity), `TrackerService.java`, `WorkoutRrDbHelper.kt` (`DB_VERSION = 2`), `MIN_RMSSD_PAIRS = 3`, and `values*/strings_*.xml` MUST remain untouched.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: `REQ-PER-034.1`/`.3`, `REQ-TRK-060.2`/`.4`/`.5`, `REQ-TRK-085.2`/`.3` targeting `RrArtifactFilter.kt`, `HRVMetricsDevice.kt`, `HrvPostWorkoutRecomputer.kt`, `WorkoutRrDatabaseManager.kt`, `WorkoutRrRepository.kt`.
  2. *Historical Origin & Commit Trace*: `ATT-3543` (`e87080a3`, `5808179d`), `ATT-3541` (`4bad7272`), `ATT-6714` (`8b2196d9`), `ATT-8706` (`7dcca5e7`, `6016d5fc`).
  3. *Root Reason for Existing Formulation*: `ATT-3543` computed Mode B classifications only in memory; `ATT-6714`/`ATT-8706` pruned `HRVMetricsDevice` at `120 s` to enforce disconnect clearing and out-of-order eviction; `HrvPostWorkoutRecomputer.MIN_RMSSD_PAIRS = 3` is asserted by `HrvPostWorkoutRecomputerTest.kt:87-104`.
  4. *Preservation of Core Invariants*: Preserves NeuroKit2 `0.2.10` golden tolerances (`|ΔrMSSD| <= 0.5 ms`, `|Δα1| <= 0.02`/`0.03`), `MIN_RMSSD_PAIRS = 3`, Rule 51/52, `120 s` drain-to-null, `REQ-PER-034.3` centered lag elimination, `REQ-TRK-084.4` conditional `AccumulatedSensors`, `DB_VERSION = 2`, and `0` `TrackerService.java` edits.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `WorkoutRr.db` beats with `275.39 ms` / `779.30 ms` / `5196 ms` outliers initially flagged `(0, 0)`, *When* `HrvPostWorkoutRecomputer.recomputeWorkout` runs, *Then* non-zero `artifact_flag` & `gap_beats` persist in `workout_rr_intervals`, a second run executes `0` writes, and clean backfill resets stale flags to `(0, 0)`.
  • *Given* a `> 180 s` RR stream, *When* `HRVMetricsDevice` evaluates `[T - 120s, T]` vs `HrvPostWorkoutRecomputer` at phase-aligned center `T - 60s`, *Then* both apply full 91-beat quartile context and clamped `[3.0, 5.0]` gate thresholds without leading-edge artifact inflation.
  • *Given* `HRVMetricsDevice` drained by `+150 s` (`> 120 s`, `< 180 s` context), *When* `calculateMetrics()` runs, *Then* all 3 sensors emit `null` and `signalQualityResult` resets to `INSUFFICIENT_DATA`.
- **Invariants**: `HrvGoldenOracleTest` bit-identical; `DfaCalculator.kt`, `TrackerService.java`, `WorkoutRrDbHelper.kt` (`DB_VERSION = 2`) untouched; `MIN_RMSSD_PAIRS = 3` preserved; `Localization Parity: N/A - no UI strings`.

---

### REQ-HRV-049: Polar Verity Sense Optical PPI Support in Morning HRV Check, Provenance Persistence & Baseline Isolation (`ATT-9177`) [Verified]
- **System Requirement(s)**: Derived (SW-only) — Backward-compatible optical PPI beat flag, optical-aware morning HRV guidance and signal-quality gating, SQLite v2->v3 modality provenance with `.attbackup` parity, and isolated ECG vs Optical PPI 60-day SWC baselines (refining `REQ-PER-030`, `REQ-PER-032`, `REQ-PER-033`, and `REQ-TRK-067`)
- **Target Release**: `V5.0.0`
- **Tests**: `TST-HRV-049`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `RrBeatEvent.kt`, `HrvSignalQualityAnalyzer.kt`, `HrvSensorModality.kt`, `HrvReadinessEntry.kt`, `HrvReadinessDatabaseManager.kt`, `HrvReadinessCalculator.kt`, `HrvReadinessRepository.kt`, `MorningHrvCheckViewModel.kt`, `MorningHrvCheckScreen.kt`, `MorningHrvOpticalCards.kt`, `HrvReadinessTrendMath.kt`, `HrvReadinessTrendCard.kt`, `res/values*/strings_hrv.xml`
- **Rationale**: Requiring a wet ECG chest strap in bed creates friction for daily morning HRV tracking. Polar Verity Sense optical PPI (`ATT-9176`) enables slip-on morning `rMSSD` checks, but optical PPG pulse-to-pulse intervals carry systematic PPG-vs-ECG offsets (~6–15% MAPE) and mild pulse-wave damping that would otherwise trigger misleading ECG smoothed-stream warnings and silently contaminate an athlete's existing ECG 60-day SWC baseline (`ATT-9177`).
- **Description**:
  1. *Backward-Compatible Optical PPI Contract & Signal Quality Mode (`REQ-HRV-049.1`)*:
     - `RrBeatEvent` SHALL add `val isOpticalPpi: Boolean = false` via `@JvmOverloads constructor(...)`, keeping 6-argument Java (`ANTHeartRateDevice.java`) and Kotlin callers source/binary compatible. `HrvSignalQualityAnalyzer.analyze(...)` and `analyzeBeats(...)` SHALL add `@JvmOverloads` with optional `isOpticalPpi: Boolean = false`: when `isOpticalPpi == true`, `INSUFFICIENT_DATA` (`< 30` beats) and `HIGH_ARTIFACTS` (`> 5.0%`) SHALL remain strictly enforced while ECG-specific `QUANTIZED_SYNTHETIC` and `SMOOTHED_STREAM` gates (`REQ-TRK-067`) SHALL be bypassed.
  2. *Non-Sticky Morning HRV Optical UX, Start-Screen Eligibility, 9-Locale Parity & Rule 8.5 PNG (`REQ-HRV-049.2`)*:
     - `MorningHrvCheckViewModel.handleRrBeat(event)` SHALL update `isOpticalPpiSession = event.isOpticalPpi` non-stickily in `READY`/`STABILIZING` (clearing optical mode immediately when ECG beats arrive) and derive `isOpticalPpiSession = bufferedBeats.any { it.isOpticalPpi }` during `MEASURING`/`completeMeasurement()`, passing `isOpticalPpi` to `HrvSignalQualityAnalyzer.analyzeBeats(...)` and storing `HrvSensorModality.OPTICAL_PPI` vs `ECG` on `computedEntry`.
     - `MorningHrvCheckScreen.kt` (`<= 1,450` LoC; helper composables extracted to `MorningHrvOpticalCards.kt` as needed) SHALL display an optical stillness/snug-contact guidance card when `isOpticalPpiSession == true` (outside pure-wellness mode), display optical armband fit/stillness artifact guidance when `highArtifactWarning && isOpticalPpiSession`, and show an `Optical PPI` provenance badge on `ReadinessResultCard`.
     - `ControlTrackingViewModel` (`0` edits) paired Polar Verity Sense (`DeviceType.HRM`) eligibility and daily completion behavior SHALL be verified by unit test. All new UI strings SHALL achieve 9-language parity (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) in `res/values*/strings_hrv.xml` (`TranslationParityTest`). A Rule 8.5 Light/Dark PNG (`docs/engineering/walkthroughs/img/ATT-9177_morning_hrv_optical_ppi.png`) SHALL be captured via Robolectric (`private fun` `@Preview`, `0` `src/main` reads).
  3. *`HrvSensorModality`, SQLite v2->v3 Migration, `.attbackup` Parity & Per-Modality Primary Isolation (`REQ-HRV-049.3`)*:
     - Introduce `HrvSensorModality` (`ECG`, `OPTICAL_PPI`; fallback `ECG`) and migrate `HrvReadinessDbHelper.DB_VERSION` `2 -> 3` adding `sensor_modality TEXT NOT NULL DEFAULT 'ECG'` with exact schema parity across `onCreate (v3)`, `v1 -> v3`, and `v2 -> v3` (`Rule 64`), symmetric `toContentValues`/`fromCursor`/`fromCursorBatch` (`Rule 20`), and `.attbackup` / `DatabaseSchemaRegistry` (`Rule 62`) symmetry.
     - Scope `HrvReadinessDatabaseManager.recomputePrimaryForDayInternal(...)` by `(date_iso, posture, sensor_modality)` so same-day optical and ECG readings maintain independent primary flags; in `updateEntry(...)`, fetch the pre-update row and recompute the previous `(dateIso, posture, sensorModality)` cohort if any key changed; filter `getPrimaryEntriesInWindow(posture, startDateIso, endDateIso, sensorModality: HrvSensorModality? = null)` via `AND sensor_modality = ?` when specified.
  4. *ECG vs Optical SWC Baseline Isolation & `HrvReadinessTrendCard` Segmented Selector (`REQ-HRV-049.4`)*:
     - `HrvReadinessCalculator.calculateReadiness(...)` (`@JvmOverloads`, default `sensorModality = HrvSensorModality.ECG`) and `HrvReadinessRepository.calculateReadiness(...)` SHALL isolate 7-day rolling averages and 60-day SWC corridors strictly by `(posture, sensorModality)`.
     - `HrvReadinessTrendMath.calculateTrendSeries(...)` SHALL isolate ECG vs Optical PPI cohorts, default to an available modality (`OPTICAL_PPI`) for optical-only athletes, and expose `sensorModality`, `availableModalities`, and `seriesByModality` on `HrvTrendSeries`. `HrvReadinessTrendCard.kt` SHALL display an optical provenance badge and render a `SingleChoiceSegmentedButtonRow` (`icon = {}`, `docs/design_guidelines.md` §1) when both modalities exist for the active posture, keeping `AthleteProfileScreen.kt` untouched (`1,975/1,975` LoC).
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: `REQ-PER-030`, `REQ-PER-032`, `REQ-PER-033`, `REQ-TRK-067` targeting `RrBeatEvent.kt`, `HrvSignalQualityAnalyzer.kt`, `HrvReadiness*.kt`, `MorningHrvCheck*.kt`, `HrvReadinessTrend*.kt`, and `res/values*/strings_hrv.xml`.
  2. *Historical Origin & Commit Trace*: `ATT-3540` (`98df60c9`), `ATT-3545` (`669146df`), `ATT-4981` (`287d8a6b`), `ATT-7034` (`a77ce176`), `ATT-7533` (`86a0549d`), `ATT-7532` (`647ef674`), `ATT-8469` (`241df3a3`), `ATT-8598` (`596a54f3`), `ATT-8846` (`574ae3b5`), `ATT-3557` (`4df73e2f`), `ATT-6712` (`369a830b`).
  3. *Root Reason for Existing Formulation*: `REQ-TRK-067` targeted firmware-smoothed ECG chest straps; `HrvReadiness.db` v2 keyed primary selection and SWC baselines solely on `HrvPosture`; `AthleteProfileScreen.kt` sits at its exact `1,975` LoC ceiling.
  4. *Preservation of Core Invariants*: Preserves Java 6-arg `RrBeatEvent` callers via `@JvmOverloads`, `<= 5.0%` artifact gating, legacy v1/v2 `ECG` default compatibility, `.attbackup` schema parity, `AthleteProfileScreen.kt` `1,975` LoC ceiling (`0` edits), and `MorningHrvCheckScreen.kt <= 1,450` LoC.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* optical PPI beats (`isOpticalPpi = true`), *When* a 60–120s morning check completes, *Then* optical stillness guidance shows, no ECG smoothed-stream warning fires, `> 5.0%` artifacts still warn with optical armband guidance, and `HrvReadinessEntry` saves with `OPTICAL_PPI` provenance.
  • *Given* `READY`/`STABILIZING` optical PPI beats followed by ECG beats (`isOpticalPpi = false`), *When* `handleRrBeat` processes the ECG beat, *Then* `isOpticalPpiSession` resets non-stickily to `false`.
  • *Given* historical ECG morning entries and new optical PPI entries, *When* `HrvReadinessCalculator` and `HrvReadinessTrendCard` evaluate and render, *Then* ECG 60d SWC baselines remain isolated from optical entries, optical-only athletes default to `OPTICAL_PPI`, mixed cohorts render a `SingleChoiceSegmentedButtonRow` (`icon = {}`), and `HrvReadiness.db` v1->v3/v2->v3/onCreate v3 parity holds across `.attbackup`.
  • *Given* all new UI strings across `en, de, es, fr, it, ja, nl, pl, pt`, *When* `TranslationParityTest` runs, *Then* all 9 `strings_hrv.xml` files pass with zero missing keys.
- **Invariants**: `0` edits to `AthleteProfileScreen.kt`, `ControlTrackingViewModel.kt`, `ANTHeartRateDevice.java`, and `PolarOnlineStreamingBridge.kt`; `MorningHrvCheckScreen.kt <= 1,450` LoC; `private fun` `@Preview`s; `0` `src/main` reads in tests; 9-locale parity verified.

---

### REQ-HRV-050: Per-Device HRV Source Selection, Parallel Live Calculation & Rolling SDNN (`ATT-8990`) [Verified]
- **System Requirement(s)**: Derived (SW-only) — Per-device HRV source mapping (`DeviceType.HRM`), `MySensorManager` best-sensor `HRV_PRIORITY`, lazy per-device live HRV calculation (`HRV_DFA_A1`, `HRV_RMSSD`, `HRV_ARTIFACT_RATE`, rolling `HRV_SDNN`), and isolated cockpit telemetry lookup (refining `SYSREQ-TRK-012`, `REQ-TRK-060.1`/`.5`, `REQ-TRK-061`, and `REQ-HRV-048.3`)
- **Target Release**: `V5.0.0`
- **Tests**: `TST-HRV-050`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `DeviceType.java`, `MySensorManager.java`, `HrvRollingEngine.kt`, `HrvTelemetryProvider.kt`, `PerDeviceHrvController.kt`, `HRVMetricsDevice.kt`, `BTLEHeartRateDevice.java`, `ANTHeartRateDevice.java`, `PolarOnlineStreamingBridge.kt`, `SensorFieldState.kt`, `TrackingViewModel.kt`, `SensorFieldView.kt`, `EditSensorFieldDialog.kt`, `HrvRampTestViewModel.kt`
- **Rationale**: Athletes comparing multiple concurrent HR sensors in the cockpit (e.g., Polar H10 ECG chest strap vs. Polar Verity Sense PPG armband) could select a specific `sourceDeviceId` for `HR` and `RR_INTERVAL`, but could not pick a source for `HRV_DFA_A1`, `HRV_RMSSD`, `HRV_ARTIFACT_RATE`, or `HRV_SDNN` because `DeviceType.getDeviceTypeList()` mapped HRV metrics to virtual `DeviceType.HRV_METRICS` (omitting `HRV_SDNN`) and only a single global `HRVMetricsDevice` computed HRV from the arbitrated primary stream (`ATT-8990`).
- **Description**:
  1. *`DeviceType.HRM` Source Mapping & `MySensorManager` Best-Sensor Arbitration (`REQ-HRV-050.1`)*:
     - `DeviceType.getDeviceTypeList(SensorType)` SHALL map `SensorType.HRV_RMSSD`, `SensorType.HRV_DFA_A1`, `SensorType.HRV_ARTIFACT_RATE`, and `SensorType.HRV_SDNN` to `DeviceType.HRM` (retaining enum constant `DeviceType.HRV_METRICS` for the global arbitrated `HRVMetricsDevice`) so `DevicesDatabaseManager.getDeviceIdAndNameLists()`, `EditSensorFieldDialog`, and `SensorSourceDialog` expose all paired HR sensors alongside `"Bester Sensor"` (`-1` / `null`).
     - `MySensorManager` SHALL define `HRV_PRIORITY = { DeviceType.HRV_METRICS, DeviceType.HRM }` and register it via `setPriorityList(...)` and `getPriorityArray(...)` for all 4 `HRV_*` types so `"Bester Sensor"` (`deviceName == null`), `BANALService.getBestSensorData()`, and `TrackerService` ALWAYS resolve to `ECG > ANT+ > PPG` arbitrated `HRVMetricsDevice`, while pinned tiles (`deviceName != null`) resolve via `FilterManager.resolveSensor()` -> `DeviceManager.getMyDeviceByName(deviceName).getSensor(sensorType)`.
  2. *Shared `HrvRollingEngine`, Live Rolling `HRV_SDNN` & `HrvTelemetryProvider` (`REQ-HRV-050.2`)*:
     - Extract causal 180s-context HRV evaluation (`WINDOW_FILTER_CONTEXT_MS = 180_000L`, `WINDOW_DFA_MS = 120_000L`, `WINDOW_RMSSD_MS = 60_000L`, `120 s` drain-to-null guard, configurable `[3.0, 5.0]%` artifact gate with `5.0%` fallback, `HrvSignalQualityAnalyzer`) into `HrvRollingEngine` (`@JvmOverloads`).
     - Both `HRVMetricsDevice` (`getHrvSdnnSensor()`) and per-device controllers SHALL compute live rolling `SensorType.HRV_SDNN` over active `120 s` `dfaWindowBeats` as unbiased sample SD (`sqrt(sum((nn_i - mean)^2) / (N - 1))`) of valid non-artifact NN intervals (`!isArtifact`, `correctedRrMs`), requiring `>= 10` valid beats (`MIN_SDNN_VALID_BEATS = 10`) AND `artifactRate <= effectiveGateThreshold` (`[3.0, 5.0]%`, aligned with `HrvPostWorkoutRecomputer.computeSessionSdnn`), emitting `null` when gated, insufficient, or drained.
     - Expose `HrvTelemetryProvider` (`signalQualityResult: HrvSignalQualityResult`, `latestArtifactRatePercent: Double?`) and `HrvDeviceLookup.resolveProvider(deviceManager, deviceName)` so `DeviceManager.java` (`1,707/1,708`), `TrackerService.java` (`3,538/3,559`), and `DevicesDatabaseManager.java` (`1,669/1,671`) require `0` edits.
  3. *Lazy Per-Device HRV Sensors, Disconnect Stale-Value Guard & Scheduler Lifecycle (`REQ-HRV-050.3`)*:
     - `PerDeviceHrvController` (`@JvmOverloads`, injectable `TimeProvider` and `startScheduler: Boolean = true`) SHALL attach to `BTLEHeartRateDevice`, `ANTHeartRateDevice`, and `PolarOnlineStreamingBridge` (all implementing `HrvTelemetryProvider`) via Java-compatible host callbacks (`addAndRegisterSensors`, `unregisterSensor`, `removeSensor`, `notifySensorsChanged`).
     - Zero HRV sensors and zero scheduler tasks SHALL exist before the first RR beat (`Rule 52`); on first RR beat, batch-register all 4 `MySensor<Double>` instances (`HRV_RMSSD`, `HRV_DFA_A1`, `HRV_ARTIFACT_RATE`, `HRV_SDNN`) bound to the host `MyDevice` via a single `addAndRegisterSensors(list)` call (single `SENSORS_CHANGED` broadcast) and lazily start the `5 s` evaluation scheduler.
     - On `unregisterSensors()`, `onAccumulatorsReset()` (overridden on all 3 HRM devices per `Rule 51`), and `shutDown()`, `PerDeviceHrvController` MUST synchronize mutable state across GATT/ANT+/Polar/scheduler/UI threads, cancel the scheduler task cleanly, push `newValue(null)` to all 4 HRV `MySensor`s **while still activated** *before* `deactivateSensor()`/`unregisterSensor()`/`removeSensor()` so pinned tiles clear immediately to `--`/`--.--`, reset rolling buffers/signal quality, and re-arm cleanly upon reconnect without thread leaks.
  4. *Per-Device Cockpit Isolation, `HRV_SDNN` Info Disclaimer & Ramp-Test Guard (`REQ-HRV-050.4`)*:
     - Add `val sourceDeviceName: String? = null` to `SensorFieldState`; replace scalar `latestArtifactRate` in `TrackingViewModel` with per-device tracking (`latestArtifactRateByDevice[deviceName] ?: HrvDeviceLookup.resolveProvider(dm, deviceName)?.latestArtifactRatePercent`) so pinned `HRV_DFA_A1` tiles gate accurately even without a co-located `HRV_ARTIFACT_RATE` tile.
     - Resolve `HrvSignalQualityStatus` per `deviceName` via `HrvDeviceLookup.resolveProvider(...)` in `TrackingViewModel` and `EditSensorFieldDialog.kt:660` (`deviceName = if (uiState.selectedDeviceId == -1L) null else deviceList.find { it.first == uiState.selectedDeviceId }?.second`).
     - Include `SensorType.HRV_SDNN` in `isHrvMetric` in `SensorFieldView.kt` and `EditSensorFieldDialog.kt` (Epic Medical Disclaimer invariant); guard `HrvRampTestViewModel` to ignore `FilteredSensorData` where `data.deviceName != null`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: `REQ-TRK-060.1`/`.5`, `REQ-TRK-061`, `REQ-HRV-048.3`, `REQ-TRK-085` targeting `DeviceType.java`, `MySensorManager.java`, `HRVMetricsDevice.kt`, `TrackingViewModel.kt`, `SensorTypeHrvTest.kt`.
  2. *Historical Origin & Commit Trace*: `ATT-3541` (`4bad7272`), `ATT-8706` (`7dcca5e7`, `6016d5fc`), `ATT-9160` (`bd7d6627`).
  3. *Root Reason for Existing Formulation*: Originally only global `HRVMetricsDevice` existed; `HRV_SDNN` was added for post-workout recomputation only (`HrvPostWorkoutRecomputer.kt:315`).
  4. *Preservation of Core Invariants*: Retains `DeviceType.HRV_METRICS` for global `HRVMetricsDevice` under `HRV_PRIORITY`, preserves `ECG > ANT+ > PPG` arbitration for `"Bester Sensor"` and `TrackerService`, Rule 51/52, 180s filter context + 120s drain-to-null, NeuroKit2 `0.2.10` golden parity, `@JvmOverloads`, and `0` LoC added to `DeviceManager.java`, `TrackerService.java`, and `DevicesDatabaseManager.java`.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* paired HRMs (`Polar H10`, `Polar Verity Sense`), *When* `DeviceType.getDeviceTypeList` and `DevicesDatabaseManager.getDeviceIdAndNameLists` run for `HRV_RMSSD`, `HRV_DFA_A1`, `HRV_ARTIFACT_RATE`, `HRV_SDNN`, *Then* `DeviceType.HRM` is returned and both sensors appear alongside `"Bester Sensor"` in `EditSensorFieldDialog` & `SensorSourceDialog`.
  • *Given* two connected HRMs emitting distinct RR streams concurrently, *When* both compute live HRV via `PerDeviceHrvController` and `FilterManager` resolves pinned tiles vs `"Bester Sensor"`, *Then* pinned tiles receive each device's distinct `HRV_DFA_A1`, `HRV_RMSSD`, `HRV_ARTIFACT_RATE`, and rolling `HRV_SDNN`, while `"Bester Sensor"` (`deviceName == null`) stays bound to `ECG > ANT+ > PPG` arbitrated `HRVMetricsDevice`.
  • *Given* an active HRM disconnects or `RESET_ACCUMULATORS_INTENT` fires, *When* `unregisterSensors()`/`onAccumulatorsReset()`/`shutDown()` runs, *Then* all 4 per-device HRV sensors emit `null` before deactivation/unregistration, the scheduler cancels cleanly, and reconnecting re-arms fresh calculation.
  • *Given* side-by-side pinned `HRV_DFA_A1 [Polar H10 (VALID, 1% art)]` and `HRV_DFA_A1 [Verity Sense (SMOOTHED_STREAM, 12% art)]` tiles (with no `HRV_ARTIFACT_RATE` tile) plus active `HrvRampTestViewModel`, *When* telemetry ticks arrive, *Then* Polar H10 displays zones un-gated, Verity Sense suppresses zones and shows artifact gating, and `HrvRampTestViewModel` reads only `deviceName == null`.
- **Invariants**: `0` edits to `DeviceManager.java` (`1,707/1,708`), `TrackerService.java` (`3,538/3,559`), `DevicesDatabaseManager.java` (`1,669/1,671`), and `DfaCalculator.kt`; `HrvGoldenOracleTest` bit-identical; `Localization Parity: N/A - no new UI strings`; `@JvmOverloads` on all Java-called Kotlin APIs.

---

### REQ-HRV-051: Polar Verity Sense Optical PPI Streaming & Workout DFA alpha 1 Isolation (`ATT-9176`) [Verified]
- **System Requirement(s)**: `SYSREQ-TRK-012` (Live HRV / RR-Interval Streaming & DFA Alpha-1 Threshold Estimation — Polar BLE SDK PMD optical PPI acquisition on `SYS-EL-02` and strict isolation of optical PPI from workout `DFA alpha-1`)
- **Target Release**: `V5.0.0`
- **Tests**: `TST-HRV-051`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PolarPpiStreamProcessor.kt`, `PolarOnlineStreamingBridge.kt`, `HrvRollingEngine.kt`, `TrackerService.java`
- **Rationale**: Polar Verity Sense does not transmit RR intervals over standard BLE Heart Rate Service (`0x2A37`), blocking slip-on morning resting HRV checks (`ATT-9177`). Acquiring optical Pulse-to-Pulse Intervals (`PolarOnlineStreamingApi.startPpiStreaming`) via `polar-ble-sdk:8.6.0` enables resting `rMSSD` while hardware artifact gating and strict workout `DFA alpha-1` isolation protect ECG-only exercise threshold analytics from optical PPG pulse-wave damping and motion artifacts (`ATT-9176`).
- **Description**:
  1. *Pure-JVM Optical PPI Processing, Hardware Quality Gating & `mRrIntervalSensor` Precision (`REQ-HRV-051.1`)*:
     - Extract a stateful pure-JVM helper `PolarPpiStreamProcessor` processing `PolarPpiData.PolarPpiSample` batches into monotonically timestamped `RrBeatEvent(..., isOpticalPpi = true)` via `RrBeatNormalization`.
     - Drop samples with `ppi <= 0` outright; for `ppi > 0` samples where `blockerBit == true` or `(skinContactSupported && !skinContactStatus)`, preserve timeline monotonicity and stamp `artifactFlag = RrBeatEvent.ARTIFACT_GAP`, `gapBeats = 0` (when physiological), and `isOpticalPpi = true` so `RrArtifactFilter.isGapOrOutOfPhysiologicalRange` never overwrites them, `computeBiasFreeRmssd` excludes adjacent pairs from clean `rMSSD`, and hardware motion/contact artifacts count toward `HIGH_ARTIFACTS (> 5.0%)`.
     - Update `lastNormalRrMs` strictly from clean (`!isHardwareArtifact && !isOutOfPhysiologicalRange`) samples; emit `mRrIntervalSensor?.newValue(...)` ONLY for non-`ARTIFACT_GAP` beats (matching `ANTHeartRateDevice.java:282`); reset processor state in BOTH `unregisterSensors()` and `onAccumulatorsReset()` (`Rule 51`).
  2. *Decoupled Verity Sense PPI Lifecycle, Atomic Re-Entrancy Guard & PMD Stability (`REQ-HRV-051.2`)*:
     - In `PolarOnlineStreamingBridge.kt` (`<= 1,310/1,500` LoC), launch PPI streaming ONLY for optical PPI-capable devices (`isVeritySense == true`) when `FEATURE_POLAR_ONLINE_STREAMING` is ready and `api.getAvailableOnlineStreamDataTypes(sessionId).contains(PolarBleApi.PolarDeviceDataType.PPI)`; H10/ECG chest straps (`isVeritySense == false`) SHALL NEVER probe or launch PPI.
     - Check and launch PPI independently across all 3 readiness hooks (`bleSdkFeatureReady(FEATURE_POLAR_ONLINE_STREAMING)`, `syncAlreadyOpenSessionIfReady()`, and `maybeStartVerityStreamsIfReady()`) — NEVER nested in `else if` behind `accJob`/`gyroJob` or gated on `isGyroStreamingActive`.
     - Guard launch via atomic `isPpiStreamingActive.compareAndSet(false, true)` **before** `scope.launch` (resetting in `finally`) to prevent duplicate `startPpiStreaming()` / PMD `ERROR_ALREADY_IN_STATE` races; cancel `ppiJob` and reset `isPpiStreamingActive` on `cancelActiveStreamJobs()`/`deviceDisconnected`/`blePowerStateChanged(false)`/`shutDown()`, and invoke `api.stopStreaming(streamId, PmdMeasurementType.PPI)` in `shutDown()` without disrupting `HR`/`ACC`/`GYRO` or `PolarVeritySenseOfflineController`.
  3. *Dual Live Workout `DFA alpha-1` Isolation & Optical Signal Quality Mode (`REQ-HRV-051.3`)*:
     - In `HrvRollingEngine.evaluate(nowMs)` (shared by global `HRVMetricsDevice` and per-device `PerDeviceHrvController`), suppress `alpha1Value` to `null` whenever `activeWindowHasOpticalPpi = allBeats.any { it.beatTimestampMs >= dfaCutoff && it.isOpticalPpi }` is true, and forward `isOpticalPpi = activeWindowHasOpticalPpi` to `HrvSignalQualityAnalyzer.analyzeBeats(...)`. Suppression SHALL be non-sticky so switching back to ECG (`isOpticalPpi = false`) automatically resumes `HRV_DFA_A1` once optical beats age out of the active `120 s` window or on `resetSession()`.
  4. *`WorkoutRr.db` & Post-Workout `HrvPostWorkoutRecomputer` Optical Isolation (`REQ-HRV-051.4`)*:
     - Guard `TrackerService.mRrBeatListener.onRrBeat(event)` (`TrackerService.java:293`) in-place with `!event.isOpticalPpi()` (`+0` net LoC, `3,538/3,559`) so optical PPI beats are never persisted into `WorkoutRr.db` and `HrvPostWorkoutRecomputer` never computes or plots optical `HRV_DFA_A1` curves on Verity-only workouts while ECG/ANT+ streams remain untouched.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: `REQ-HRV-049`, `REQ-HRV-050`, `REQ-SET-112`, `REQ-CON-045`, `REQ-CON-050`, `REQ-TRK-085`, `REQ-PER-034` targeting `PolarOnlineStreamingBridge.kt`, `HrvRollingEngine.kt`, `HRVMetricsDevice.kt`, `PerDeviceHrvController.kt`, `TrackerService.java`.
  2. *Historical Origin & Commit Trace*: `ATT-9177` (`b899a864`), `ATT-8990` (`cdc565c0`), `ATT-8714` (`1e7a2f0b`), `ATT-8717` (`9f9e5967`), `ATT-8706` (`7dcca5e7`).
  3. *Root Reason for Existing Formulation*: Verity Sense previously streamed only `HR`/`ACC`/`GYRO` over PMD; `WorkoutRr.db` assumed all workout RR beats came from ECG/ANT+ straps.
  4. *Preservation of Core Invariants*: Preserves `DeviceManager.java` (`1,707/1,708`, `0` edits), `DevicesDatabaseManager.java` (`1,669/1,671`, `0` edits), `TrackerService.java` (`3,538/3,559`, `+0` net LoC), `PolarVeritySenseOfflineController.kt` (`0` edits), `ECG > ANT+ > PPG` arbitration, H10 non-PPI path, `180 s` Lipponen context & NeuroKit2 `0.2.10` golden parity, `Rule 51`/`Rule 52`, and zero DB migration.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a Polar Verity Sense connected via `PolarOnlineStreamingBridge` reporting `PolarDeviceDataType.PPI` support, *When* valid `PolarPpiSample` packets arrive (`ppi > 0`, `blockerBit == false`, `skinContactStatus == true` when `skinContactSupported == true`), *Then* monotonically timestamped `RrBeatEvent(..., isOpticalPpi = true)` events dispatch to `perDeviceHrvController` and `RrBeatListener`s alongside `HR`/`ACC`/`GYRO`, and `mRrIntervalSensor` updates on clean beats.
  • *Given* `PolarPpiSample` packets with `ppi <= 0`, `blockerBit == true`, or `(skinContactSupported && !skinContactStatus)`, *When* processed by `PolarPpiStreamProcessor` & `PolarOnlineStreamingBridge`, *Then* `ppi <= 0` samples are dropped, hardware-flagged samples are stamped `ARTIFACT_GAP` (`gapBeats = 0` when physiological, `isOpticalPpi = true`), never update `lastNormalRrMs` or `mRrIntervalSensor`, never enter clean `rMSSD` pairs, and count toward `HIGH_ARTIFACTS (> 5%)`.
  • *Given* `HrvRollingEngine` / `HRVMetricsDevice` / `PerDeviceHrvController` receives `isOpticalPpi = true` beats, *When* periodic HRV metrics evaluate, *Then* `HRV_DFA_A1` emits `null` while `HRV_RMSSD`/`HRV_SDNN` and optical signal quality evaluate normally, and `HRV_DFA_A1` resumes automatically once ECG beats replace the active `120 s` window.
  • *Given* Verity Sense disconnects, reconnects, receives rapid duplicate readiness callbacks, or shuts down (or an H10 connects), *When* `PolarOnlineStreamingBridge` manages streams, *Then* H10 never launches PPI, Verity launches PPI independently of `ACC`/`GYRO` under atomic `compareAndSet` protection, `ppiJob` cancels cleanly, `stopStreaming(PPI)` runs on `shutDown()`, and `PolarPpiStreamProcessor` resets in both `unregisterSensors()` and `onAccumulatorsReset()`.
  • *Given* an active workout (`AutoPauseState.TRACKING`, `mWorkoutID > 0`), *When* `TrackerService.mRrBeatListener` receives optical PPI vs ECG beats, *Then* optical PPI beats are ignored (zero `WorkoutRr.db` persistence) while ECG beats persist normally.
- **Invariants**: `0` edits to `DeviceManager.java` (`1,707/1,708`), `DevicesDatabaseManager.java` (`1,669/1,671`), `RrBeatEvent.kt`, `PolarVeritySenseOfflineController.kt`, `DfaCalculator.kt`; `+0` net LoC in `TrackerService.java` (`3,538/3,559`); `PolarOnlineStreamingBridge.kt <= 1,500` LoC; `HrvGoldenOracleTest` bit-identical; `Localization Parity: N/A - no new UI strings`.

---

### REQ-PER-046: Authoritative Full-Period Primary Region Selection, Sport-Aware Preview Curation & Bounded Card Caching (`ATT-8664`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-PER-054`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodData.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModel.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapUtils.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummaryCard.kt`
- **Rationale**: After `ATT-7666` (`REQ-PER-039`) eliminated full-period polyline duplication (`workoutIdToAllPolylinesMap = emptyMap()`), `PeriodsViewModel.filterPeriodSummary()` fell back to the `<= 5` spatial extrema anchors (`workoutIdToPolylineMap`), discarding non-anchor preview tracks and causing `PeriodMapUtils.prepareCardMapPreviewSync()` to crown a 2-workout outlier excursion (holding `northId`/`westId`) as the Primary Region instead of the true dominant home region (`150+` workouts) (`ATT-8664`, refining `REQ-PER-013`, `REQ-PER-019`, `REQ-PER-024.5`, and `REQ-PER-039`).
- **Description**:
  1. *Authoritative Full-Period Primary-Region Ranking (`REQ-PER-046.1`)*:
     - `PeriodsRepository.enrich(summary, groupWorkouts)` SHALL compute compact `regionSummaries: List<PeriodRegionPreview>` via `SpatialRegionEngine.detectRegions(groupWorkouts)` (using `startLatLng`/bounding coordinates with `0` `PolyUtil.decode()` calls) retaining `center`, `bounds`, per-sport `sportCounts`, `sportDistancesMeters`, `sportMostRecentTimestampS`, and bounded timestamp-ordered per-sport preview tracks (`<= MAX_SUMMARY_THUMBNAIL_TRACKS` = 8 per sport per region, `startTimeS DESC`).
     - Both `PeriodsRepository.enrich()` and `PeriodsViewModel.filterPeriodSummary()` SHALL rank surviving regions (`filteredCount > 0`) by `(workoutCount DESC, totalDistanceMeters DESC, mostRecentTimestampS DESC)` (matching `SpatialRegionEngine.detectRegions()` 1:1), propagate authoritative `primaryRegionSummary` (`detectedRegionCount`, `primaryRegionWorkoutCount`, `center`, `bounds`), and curate `summary.polylines` (`<= 8`) strictly from the winning Primary Region.
  2. *Sport-Toggle Consistency, Indoor Sport Isolation & `filterPeriodSummary()` Hygiene (`REQ-PER-046.2`)*:
     - For default `{BIKE, RUN}` and any toggled sport filter, `filterPeriodSummary()` SHALL re-rank surviving regions across `sports`, merge up to `8` genuine matching-sport polylines from the winning region (`startTimeS DESC`), update `primaryRegionSummary`, and return `polylines = emptyList()` when all matching workouts are indoor/no-GPS without leaking other sports' tracks.
     - `filterPeriodSummary()` MUST keep `workoutIdToAllPolylinesMap = emptyMap()` whenever input `summary.workoutIdToAllPolylinesMap` is empty while preserving backward compatibility for explicitly populated maps.
  3. *Preview Cache Key, Compose `produceState` Invalidation & Geocoding Center Parity (`REQ-PER-046.3`)*:
     - When `summary.primaryRegionSummary != null`, `PeriodMapUtils.prepareCardMapPreviewSync()` SHALL render curated primary-region `budgetEnforcedPaths`, set `regionCount = detectedRegionCount`, `primaryRegionPathsCount = primaryRegionWorkoutCount`, `activeRegionId = "region_0"`, and resolve `primaryRegionName` using `primaryRegionSummary.center` in BOTH `findNearestKnownLocationName()` (`prepareCardMapPreviewSync`) and `resolvePlaceName()` (`prepareCardMapPreview`), falling back cleanly to `detectRegionsFromPaths()` when `primaryRegionSummary == null`.
     - BOTH `periodCardMapStateCache` key (`PeriodMapUtils.kt`) and `PeriodSummaryCard.kt` `produceState` keys MUST incorporate `summary.primaryRegionSummary` so sport-toggle changes refresh badge count, multi-region visibility, and region name immediately.
  4. *Strict `REQ-PER-039` Memory, LoC & Layering Invariants (`REQ-PER-046.4`)*:
     - Preserve `workoutIdToPathMap == emptyMap()`, `workoutIdToAllPolylinesMap == emptyMap()`, `workoutIdToPolylineMap.size <= 5`, `polylines.size <= 8`, `periodCardMapStateCache <= 64`, `0` `PolyUtil.decode()` inside `enrich()`, `0` SQLite schema changes, `0` edits to `SpatialRegionEngine.kt`/`PeriodsScreen.kt`/protected monoliths, all modified files `<= 1,500` LoC, `Localization Parity: N/A - no UI strings`, and `0` new `src/main` reads (`REQ-PRO-049.1`).
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new `REQ-PER-046`, refining `REQ-PER-013`, `REQ-PER-019`, `REQ-PER-024.5`, and `REQ-PER-039` across `PeriodData.kt`, `PeriodsRepository.kt`, `PeriodsViewModel.kt`, `PeriodMapUtils.kt`, and `PeriodSummaryCard.kt`.
  2. *Historical Origin & Commit Trace*: `074bd857` (`ATT-7666` / `REQ-PER-039`), `ATT-4935` (`REQ-PER-024.5`), `ATT-4477` (`REQ-PER-019`), `ATT-1151` (`REQ-PER-013`).
  3. *Root Reason for Existing Formulation*: `ATT-7666` bounded heap usage by clearing `workoutIdToAllPolylinesMap`, leaving `filterPeriodSummary()` falling back to `<= 5` spatial extrema anchors.
  4. *Preservation of Core Invariants*: All `REQ-PER-039` memory bounds, `SpatialRegionEngine` `75 km`/`60 km` thresholds, `PeriodMapScreen` interactive map, and legacy fallback for synthetic `PeriodSummary` fixtures remain intact.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `159` home-region workouts and `2` outlier vacation workouts holding `northId`/`westId`, *When* enriched, sport-filtered (`{BIKE, RUN}`), and previewed, *Then* Primary Region is home (`regionCount == 2`, `primaryRegionPathsCount == 159`, `activeRegionId == "region_0"`, `<= 8` home preview tracks).
  • *Given* multi-region `BIKE`/`RUN` + indoor `UNKNOWN` workouts, *When* sport filter toggles `{BIKE, RUN}` -> `{RUN}` -> `{UNKNOWN}`, *Then* `{RUN}` re-ranks to the run-dominant region with `<= 8` newest `RUN` polylines, `{UNKNOWN}` yields `polylines == emptyList()`, and `workoutIdToAllPolylinesMap == emptyMap()`.
  • *Given* identical preview polylines with differing `primaryRegionWorkoutCount` or `detectedRegionCount`, *When* previewed, *Then* LRU cache & `produceState` refresh immediately and both sync/async geocoding use `primaryRegionSummary.center`.
- **Invariants**: `workoutIdToPathMap == emptyMap()`; `workoutIdToAllPolylinesMap == emptyMap()`; `workoutIdToPolylineMap.size <= 5`; `polylines.size <= 8`; `0` `PolyUtil.decode()` in `enrich()`; `0` DB schema bumps; `0` monolith edits; `Localization Parity: N/A - no UI strings`.

---

### REQ-PER-047: Live-Updating Active Workout Card via O(1) In-Memory StateFlow [Verified]
- **Tests**: `TST-PER-055`
- **Tickets**: `ATT-9069`
- **Target Release**: `V5.0.0`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration, Review
- **Implementation**: `LiveWorkoutCardStateStore.kt`, `LiveWorkoutBroadcastTracker.kt`, `WorkoutRepository.kt`, `WorkoutSummary.kt`, `WorkoutSummaryCompact.kt`
- **Localization Parity**: `N/A - no new UI strings`
- **Description**: While a workout is actively recording, the unfinished workout card in Workout Summaries (`WorkoutSummary` and `WorkoutSummaryCompact`) SHALL update live at ~1 Hz via the dedicated O(1) in-memory `StateFlow<LiveWorkoutCardSnapshot?>` (`LiveWorkoutCardStateStore`) collected strictly inside the matching unfinished card via `collectAsStateWithLifecycle`, preserving zero SQLite reloads, zero 1-Hz broadcasts, zero `_allWorkouts` churn, and zero finished-card subscriptions (`REQ-STB-020`).
- **Rationale**: While a workout is actively recording, the unfinished workout card in **Workout Summaries** SHALL update live at ~1 Hz via a dedicated O(1) in-memory `StateFlow<LiveWorkoutCardSnapshot?>` (`LiveWorkoutCardStateStore`) while strictly preserving the `ATT-8999` / `REQ-STB-020` zero-OOM invariant (`0` SQLite reloads, `0` 1-Hz `WORKOUT_UPDATED_INTENT` broadcasts, `0` `_allWorkouts` emissions/clones, and `0` downstream `PeriodsRepository` / `AthleteProfileViewModel` / `KnownLocationsViewModel` recalculations).
  1. *O(1) Dedicated StateFlow & Lifecycle Gating (`REQ-PER-047.1`)*: `LiveWorkoutCardStateStore` SHALL expose `StateFlow<LiveWorkoutCardSnapshot?>` (`workoutId`, `activeTimeSec`, `totalTimeSec`, `distanceM`, `avgSpeedMps`, `ascentM`, `descentM`, `avgHeartRateBpm`, `avgPowerWatts`, `avgCadenceRpm`, `laps`, `isPaused`) updated in O(1) memory on active ticks (`recordLiveSample`), paused ticks (`updateWorkoutTotalTime`), and live lap saves (`saveLap`), armed on `onSessionInitialized`, and cleared to `null` on `reset()` / `markWorkoutFinished*` so post-stop executor drain cannot resurrect a cleared snapshot.
  2. *Card-Scoped Lifecycle-Aware Compose Collection (`REQ-PER-047.2`)*: Both `WorkoutSummary` and `WorkoutSummaryCompact` SHALL subscribe to `liveSnapshotFlow` strictly when `!workoutData.finished`, filtering to `snapshot.workoutId == workoutData.id` with `distinctUntilChanged()` via `collectAsStateWithLifecycle`. Finished cards SHALL never subscribe or recompose, non-matching unfinished cards SHALL never recompose on active ticks, and off-screen screens SHALL stop collecting automatically.
  3. *Canonical Live Overlay & Safety Invariants (`REQ-PER-047.3`)*: `applyLiveSnapshot` SHALL overlay non-null live scalar metrics, deduplicated laps, and live HR/Speed/Pace(RUN)/Power/Cadence `ExtremaDataRow` averages formatted via canonical `MetricFormatterContext` / `SensorType.*.myFormatter` formatters (preserving unrelated rows such as `VERTICAL_OSCILLATION`), keeping unfinished `0.5f` alpha styling and active-session delete/mark-finished protection intact.
- **Invariants**: `0` edits to `TrackerService.java`; `0` SQLite reloads per 1-Hz tick; `0` steady-state 1-Hz `WORKOUT_UPDATED_INTENT` broadcasts; `0` `_allWorkouts` emissions/clones during live tracking; `0` finished-card `liveSnapshotFlow` subscriptions (`subscriptionCount == 0`).

---

### REQ-PER-056: Stateless Reactive In-Memory Period Projection & PeriodSummaries.db Decommissioning [Verified]
- **Tests**: `TST-PER-056`
- **Tickets**: `ATT-8131`
- **Target Release**: `V5.0.0`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PeriodsRepository.kt`, `PeriodsViewModel.kt`, `WorkoutRepository.kt`, `DatabaseSchemaRegistry.kt`, `MigrationEngine.kt`, `BulkImportBatchContext.kt`, `TrainingApplication.java`
- **Localization Parity**: `N/A - no new UI strings`
- **Description**: `PeriodsRepository` SHALL replace the redundant materialized SQLite rollup database (`PeriodSummaries.db` / `PeriodSummariesDatabaseManager`) with a stateless reactive in-memory projection over `WorkoutRepository.allWorkouts` executing on `Dispatchers.Default`, ensuring immediate consistency after `cropWorkout` or workout edits while preserving all UI models, memory/OOM budgets, legacy `.attbackup` restore compatibility, and one-time background disk cleanup of obsolete `PeriodSummaries*.db*` files.
- **Rationale**: `WorkoutRepository.allWorkouts` already retains all workout summaries in RAM, while `PeriodSummaries.db` (519 LoC, `DB_VERSION = 27`) caused permanent stale period totals after `cropWorkout` or metric edits at unchanged workout counts and duplicated `WorkoutDataMapper`/`BatchMetadataLoader` wiring.
  1. *Decommission `PeriodSummaries.db` (`REQ-PER-056.1`)*: `PeriodSummariesDatabaseManager` and `PeriodSummaries.db` SHALL be removed from production code and `DatabaseSchemaRegistry` (`21 -> 20` production `SQLiteOpenHelper` subclasses). `PeriodsRepository` SHALL retain zero SQLite / `*DatabaseManager` / `WorkoutDataMapper` references (ratcheting `ArchitectureConformanceContractTest.BASELINE_ALLOWED_UI_DB_CALLERS` `22 -> 20`).
  2. *Reactive In-Memory Projection on `Dispatchers.Default` (`REQ-PER-056.2`)*: `PeriodsRepository.groupedPeriods` SHALL project finished workouts (`it.finished && it.startTimeS > 0L`) from `WorkoutRepository.allWorkouts` via `distinctUntilChangedBy { it.map(WorkoutData::toPeriodProjectionKey) }` and `flowOn(Dispatchers.Default)`, emitting `[DAY, WEEK, MONTH, YEAR]` sorted `sortKey DESC` with 1:1 parity for DST-safe bounds (`REQ-PER-015`), active-locale labels/ranges (`REQ-PER-028`), `sportStats`/`DetailedStats`/`LongestWorkout`, spatial anchors (`<= 5`), `regionSummaries`/`primaryRegionSummary`/`<= 8` `polylines` (`REQ-PER-039`, `REQ-PER-046`), empty `workoutIdToPathMap`/`workoutIdToAllPolylinesMap`, `yearlySportContributions`/`dailySportContributions` (`REQ-PER-025`, `REQ-PER-027`), and `extremaMarkers`. `migrationStatus` SHALL remain `null`, and `getWorkoutsForRange(startS, endS)` SHALL filter `workoutRepo.allWorkouts.value` sorted ascending by `startTimeS`.
  3. *Instant Crop/Edit Consistency & Zero-Rebuild Live Guards (`REQ-PER-056.3`)*: Any finished workout mutation (`cropWorkout`, edit, sport change, delete, import, or `finished: false -> true`) updating `WorkoutRepository.allWorkouts` SHALL immediately recompute Day/Week/Month/Year summaries without imperative push hooks (`onWorkoutFinished`, `onWorkoutSportChanged`, `onWorkoutDeleted`, `resyncAllPeriods`, `syncPeriodsIfDiscrepancy`, `checkIntegrityAndSync`), while non-period edits (`REQ-PER-016.1`) and 1-Hz unfinished live ticks (`REQ-STB-020.3`) SHALL trigger zero recomputations.
  4. *Obsolete Disk Cleanup & Legacy `.attbackup` Compatibility (`REQ-PER-056.4`)*: App startup (`TrainingApplication.onCreate`) SHALL run a one-time async background cleanup gated by a fresh preference key (`period_summaries_db_decommissioned_cleaned`, persisted only when every obsolete file deletion succeeds without I/O error) deleting all `PeriodSummaries*` files (`PeriodSummaries.db`, `-wal`, `-shm`, `-journal`, `PeriodSummaries_v*.db*`) from `databases/`. Restoring older `.attbackup` archives containing `PeriodSummaries.db` SHALL succeed cleanly (`DatabaseSchemaRegistry.isVersionSupported` accepts unregistered legacy filenames) and `MigrationEngine.performFullRestore` SHALL purge restored `PeriodSummaries*` files post-swap.
- **Invariants**: Exact `PeriodSummary` / `PeriodsViewModel` / `PeriodsScreen` / `PeriodBarGraph` contracts preserved; `REQ-PER-015` DST bounds, `REQ-PER-016.1` projection-key dedup, `REQ-STB-020.3` 0-rebuild live ticks, `REQ-PER-039` OOM bounds, `REQ-PER-046` primary region, and `REQ-MIG-041` `.attbackup` backward compatibility strictly preserved; `0` `strings*.xml` edits.

### REQ-PER-057: Deterministic Cold Cluster Preview Enrichment Progress & Version-Guarded Lightweight Hydration [Verified]
- **Tests**: `TST-PER-057`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterRepository.kt`
- **Localization Parity**: `N/A - no new UI strings`
- **Description**: `WorkoutClusterRepository` SHALL decouple cold preview-enrichment progress gating from `_allClusters.value.isEmpty()`, guard `_allClusters`/`_clusterStats` publication with a monotonic state version so asynchronous `init` lightweight hydration cannot suppress cold progress or overwrite newer refreshed/deleted state, guarantee `migrationStatus` cleanup on failure, and expose an injectable `ioDispatcher` for deterministic concurrency verification (refining `REQ-PER-006`, `REQ-PER-003`, and `REQ-UI-322.5`).
- **Rationale**: `ATT-5074` introduced asynchronous `init { loadAllClustersLightweight() }` on `Dispatchers.IO`, hydrating `_allClusters.value` with un-enriched rows before `refreshClusters()` evaluated `showProgress = forceShowProgress || (needsEnrichment && _allClusters.value.isEmpty())`, silently suppressing cold enrichment progress (`TST-PERF-010`), risking stale overwrites of enriched or deletion-synced clusters (`ATT-9607`), and permitting cross-thread `StateFlow` conflation under parallel load.
  1. *Decoupled Cold-Enrichment Progress Gate (`REQ-PER-057.1`)*: `WorkoutClusterRepository` SHALL declare `hasCompletedFullRefresh` (`AtomicBoolean(false)`), `clusterStateVersion` (`AtomicLong(0L)`), and `statePublishLock` above `init {}` (preventing uninitialized-field access under synchronous `Dispatchers.Unconfined`), set `hasCompletedFullRefresh = true` strictly upon normal `refreshClusters()` completion (never in `loadAllClustersLightweight()`), compute `showProgress = forceShowProgress || (needsEnrichment && !hasCompletedFullRefresh.get())`, execute the silent fast-path before any `_migrationStatus` write, and call cooperative `yield()` after each `_migrationStatus` progress write (`Loading`, `Healing`, `Previews`) when `showProgress == true`, emitting observable `MigrationStatus` updates on cold un-enriched loads even when `init` lightweight hydration finished first while remaining silent on warm persisted-preview restarts (`needsEnrichment == false`, `TST-PERF-010`) and same-session revisits (`hasCompletedFullRefresh == true`, `TST-PERF-009`, including un-enrichable indoor/no-GPS clusters).
  2. *Version-Guarded State Publication, SQLite Writeback Guard & Bounded Retry (`REQ-PER-057.2`)*: `WorkoutClusterRepository` SHALL guard in-memory `_allClusters` / `_clusterStats` writes and `clusterStateVersion` increments under a non-blocking `synchronized(statePublishLock)` (holding zero DB I/O or suspending calls so recursive `recalculateClustersWithProgress()` -> `refreshClusters()` and synchronous `ATT-9607` `syncClusterAfterWorkoutDeletion()` stay deadlock-free). `refreshClusters()`, `syncClusterAfterWorkoutDeletion()`, and `propagateClusterRename()` SHALL increment `clusterStateVersion` atomically on publication, and `refreshClusterStats()` SHALL synchronize its `_clusterStats.value` write under `statePublishLock` (DB query outside lock). `loadAllClustersLightweight()` SHALL capture `startVersion` prior to reading SQLite, abort stale SQLite writebacks (`healUntrimmedClusterNames` / `clusterDb.updateCluster`) if `clusterStateVersion.get() != startVersion`, publish strictly when `clusterStateVersion.get() == startVersion`, and execute at most 1 bounded retry when `clusterStateVersion.get() != startVersion && _allClusters.value.isEmpty() && !hasCompletedFullRefresh.get()` (returning `_allClusters.value` otherwise); `init` SHALL skip launching `loadAllClustersLightweight()` when `hasCompletedFullRefresh.get()` is already `true`.
  3. *Guaranteed Progress Cleanup on Failure & Cooperative Cancellation (`REQ-PER-057.3`)*: In `refreshClusters()`, `try { ... } finally { if (showProgress) _migrationStatus.value = null }` SHALL open immediately before the first `_migrationStatus.value = Loading` assignment (after the fast-path return). Combined with cooperative `yield()` checkpoints, any exception or cancellation clears `_migrationStatus` to `null` and leaves `hasCompletedFullRefresh == false` so retries still report progress, while normal completion publishes `_clusterStats` and `_allClusters` before clearing `_migrationStatus` (`Rule 74`).
  4. *Injectable IO Dispatcher (`REQ-PER-057.4`)*: `WorkoutClusterRepository`'s `internal constructor` SHALL accept an optional `ioDispatcher: CoroutineDispatcher = Dispatchers.IO` (preserving `getInstance` signatures verbatim) used by `init`, `loadAllClustersLightweight()`, `refreshClusters()`, `refreshClustersAsync()`, and `refreshClusterStats()`.
- **Invariants**: `0` SQLite schema migrations; `0` UI/string edits; `getInstance` signatures unchanged; `TST-PERF-010` warm restart silence, `TST-PERF-009` revisit silence, `TST-PERF-003`/`007` progress phases, `REQ-UI-322.5`/`322.10` lightweight hydration, and `ATT-9607` `syncClusterAfterWorkoutDeletion()` strictly preserved.

---

### REQ-HRV-052: Morning HRV Check HR Sensor Availability Gate & Disconnected Status Guidance (`ATT-9323`) [Verified]
- **System Requirement(s)**: `SYSREQ-TRK-012` (Live HRV / RR-Interval Streaming & DFA Alpha-1 Threshold Estimation — compatible HR/HRV sensor availability gating in `MorningHrvCheckScreen` and `MorningHrvCheckViewModel`)
- **Target Release**: `V5.0.0`
- **Tests**: `TST-HRV-052`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration, On-Device Manual
- **Implementation**: `MorningHrvCheckViewModel.kt`, `MorningHrvCheckScreen.kt`, `values*/strings_hrv.xml`
- **Localization Parity**: `9/9 locales (values*/strings_hrv.xml: morning_hrv_state_no_sensor, morning_hrv_no_sensor_hint)`
- **Rationale**: `MorningHrvCheckScreen` previously rendered `morning_hrv_start_button` unconditionally enabled and showed `"Ready to Measure"` even with no connected heart rate / HRV sensor, allowing users to start empty measurements that ran a full countdown before failing with `morning_hrv_error_no_rr`. Gating measurement startup on live compatible HR/HRV sensor availability (matching `HrvRampTestScreen.canStartTest`) while preserving standalone Morning Sleep & Wellness saving prevents empty sessions and provides clear sensor guidance (`ATT-9323`).
- **Description**:
  1. *Live Compatible HR/HRV Sensor Availability & Disconnect Reset (`REQ-HRV-052.1`)*:
     - `MorningHrvUiState` SHALL expose `isHrSensorAvailable: Boolean = currentHeartRateBpm != null` and derived `canStartMeasurement: Boolean` (`(phase == READY || phase == COMPLETED) && isHrSensorAvailable`).
     - `MorningHrvCheckViewModel` SHALL observe `BANALServiceRepository.activeSensors` (`SensorType.HR` / `SensorType.RR_INTERVAL`) combined with `BANALServiceRepository.allFilteredSensorData` (positive `SensorType.HR`) alongside `handleRrBeat(event)` (`event.rrMs > 0.0`, covering BLE ECG, ANT+ HRM, and Polar Verity Sense optical PPI).
     - When a previously active repository HR sensor disconnects (`SensorType.HR`/`SensorType.RR_INTERVAL` absent and no positive live HR), `MorningHrvCheckViewModel` SHALL immediately set `isHrSensorAvailable = false` and, while in `MorningHrvPhase.READY`, clear stale `currentHeartRateBpm = null` and `isOpticalPpiSession = false`. `cancelMeasurement()` SHALL also clear stale `currentHeartRateBpm = null` if `!isHrSensorAvailable`.
  2. *Defense-in-Depth ViewModel Start Guard (`REQ-HRV-052.2`)*:
     - `MorningHrvCheckViewModel.startMeasurement()` SHALL reject execution and remain in `MorningHrvPhase.READY` (without starting stabilization/measurement timers or mutating phase) whenever `!_uiState.value.canStartMeasurement`.
  3. *Disabled Start CTA, Semantic Ready Guidance & Standalone Wellness Preservation (`REQ-HRV-052.3`)*:
     - `MorningHrvCheckScreen.kt` SHALL bind `morning_hrv_start_button` to `enabled = uiState.canStartMeasurement`.
     - When `uiState.phase == MorningHrvPhase.READY && !uiState.isHrSensorAvailable`, `MeasurementPacingCard` SHALL display a semantic `errorContainer` / `onErrorContainer` status pill (`R.string.morning_hrv_state_no_sensor`), muted heart icon (`MaterialTheme.colorScheme.outline`), and clear connection guidance (`R.string.morning_hrv_no_sensor_hint`, `testTag("morning_hrv_no_sensor_guidance")`) rather than `"Ready to Measure"`.
     - Standalone saving of **Morning Sleep & Wellness** (`onSaveStandaloneWellness`, both `morning_hrv_save_wellness_secondary_button` in Full HRV mode and `morning_hrv_save_wellness_primary_button` in Pure Wellness mode) SHALL remain enabled and fully functional when `!uiState.isHrSensorAvailable`.
  4. *Localization Parity, Private Previews & LoC Ceiling (`REQ-HRV-052.4`)*:
     - `morning_hrv_state_no_sensor` and `morning_hrv_no_sensor_hint` SHALL maintain 9/9 locale parity across `app/src/main/res/values*/strings_hrv.xml` (`TranslationParityTest` PASS).
     - All modified files SHALL remain `<= 1,500` LoC (`ArchitectureConformanceContractTest`), any added `@Preview` composable SHALL be `private fun` with zero parameters, and `0` new Kotlin compiler warnings SHALL be introduced.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: `REQ-PER-032`, `REQ-HRV-040`, `REQ-HRV-043`, `REQ-HRV-045`, `REQ-HRV-049` targeting `MorningHrvCheckViewModel.kt`, `MorningHrvCheckScreen.kt`, and `values*/strings_hrv.xml`.
  2. *Historical Origin & Commit Trace*: `ATT-3556` (`REQ-PER-032`), `ATT-7034` (`REQ-HRV-040`), `ATT-8598` (`REQ-HRV-043`), `ATT-8846` (`REQ-HRV-045`), `ATT-9177` (`REQ-HRV-049`).
  3. *Root Reason for Existing Formulation*: Initial `MorningHrvCheckScreen` delegated empty-stream handling purely to post-countdown `completeMeasurement()` (`morning_hrv_error_no_rr`), before live pre-start sensor gating (`HrvRampTestScreen.canStartTest`) became the established UX pattern.
  4. *Preservation of Core Invariants*: Preserves zero-workout invariant, sticky posture/duration preferences, pure wellness mode (`REQ-HRV-043`), `forceFullHrv` route override (`REQ-HRV-045`), optical PPI non-sticky modality and baseline isolation (`REQ-HRV-049`), and `completeMeasurement()` empty-beats fallback guard.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `MorningHrvCheckScreen` in `READY` with no HR/HRV sensor connected (`isHrSensorAvailable == false`), *When* rendered in Full HRV mode (with or without wellness enabled), *Then* `MeasurementPacingCard` displays `morning_hrv_state_no_sensor` and `morning_hrv_no_sensor_guidance`, `morning_hrv_start_button` is disabled, programmatic `startMeasurement()` remains in `READY`, and `morning_hrv_save_wellness_secondary_button` remains enabled and saves standalone wellness.
  • *Given* a compatible HR sensor connects via `BANALServiceRepository.activeSensors`/`allFilteredSensorData` or emits an ECG/optical PPI `RrBeatEvent`, *When* `MorningHrvCheckViewModel` processes the update, *Then* `isHrSensorAvailable` and `canStartMeasurement` become `true`, `"Ready to Measure"` replaces the disconnected guidance, and `morning_hrv_start_button` enables and launches the measurement protocol.
  • *Given* an active HR sensor disconnects in `READY`, *When* `BANALServiceRepository.activeSensors` and `allFilteredSensorData` clear HR/RR sensors, *Then* `isHrSensorAvailable` reverts to `false`, `currentHeartRateBpm` resets to `null`, `morning_hrv_start_button` disables, and disconnected guidance reappears.
- **Invariants**: `MorningHrvCheckScreen.kt <= 1,500` LoC; `MorningHrvCheckViewModel.kt <= 1,500` LoC; `0` DB schema migrations; `0` workout creation side effects; `9/9` locale parity across `values*/strings_hrv.xml`.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-17 | AI Agent (`ATT-8164`) | `ATT-8164` | Added standardized `DOC-REQ-PERIODS` work-product control header and Change History. |
| `1.0.1` | 2026-11-18 | AI Agent (`ATT-8710`) | `ATT-8710` | Added `REQ-HRV-044` [Planned] for DFA alpha 1 3-zone graph coloring, 52.dp margin coordination, dash filtering, and scatter threshold parity. |
| `1.0.2` | 2026-11-18 | AI Agent (`ATT-8710`) | `ATT-8710` | Integrated `REQ-HRV-044` [Verified] post-`ATT-8206` (`v1.0.1`) shard reconciliation. |
| `1.0.3` | 2026-11-18 | AI Agent (`ATT-8201`) | `ATT-8201` | Linked refined `REQ-*` entries in-place to net-new `SYSREQ-TRK-007..013` and `SYSREQ-DEX-007..008`. |
| `1.0.4` | 2026-11-18 | AI Agent (`ATT-8080`) | `ATT-8080` | Added and verified (`[Verified]`) `REQ-PER-044` for `ATT_FUSED` track layers, multi-source `ALTITUDE`/`SPEED`/`SLOPE` overlays, and persisted per-chart visibility toggles. |
| `1.0.5` | 2026-11-18 | AI Agent (`ATT-8916`, `--as agent1`) | `ATT-8916` | Added `REQ-PER-045` (`[Planned]`, `.1`–`.5`) for segmented activity & phone carriage mode timeline bar and expandable phase breakdown in Workout Details. |
| `1.0.6` | 2026-11-18 | AI Agent (`ATT-8916`, `--as agent1`) | `ATT-8916` | Promoted `REQ-PER-045` to `[Implemented]` following Stage 4 construction, `Rule 8.5` PNG inspection, and green contract/UI verification (`102/102` tests). |
| `1.0.7` | 2026-11-18 | AI Agent (`ATT-8916`, `--as agent1`) | `ATT-8916` | Promoted `REQ-PER-045` to `[Verified]` after Stage 5 targeted unit/contract/UI regression, `emulator-5554` smoke verification, `Rule 8.5` PNG audit, and living-doc governance sync. |
| `1.0.8` | 2026-11-19 | Lane A Flash (`ATT-8324`, `--as agent1`) | `ATT-8324` | Promoted `REQ-PER-003`, `REQ-PER-004`, `REQ-PER-005`, `REQ-PER-006`, `REQ-PER-007`, and `REQ-PER-009` to `[Verified]` in Stage 5 (`REQ-PER-001` retained `[Implemented]` pending `TST-BUG-002`) reusing Gate 4 `93/93 PASS` evidence (`Rule 7.5`). |
| `1.0.9` | 2026-11-19 | Lane B Author (`ATT-8846`, `--as agent1`) | `ATT-8846` | Added `REQ-HRV-045` (`[Planned]`, `.1`–`.4`) for parameterized `forceFullHrv` route override preserving full HRV measurement UI when launched from `AthleteProfileScreen` HRV tab. |
| `1.0.10` | 2026-11-19 | Lane B Author (`ATT-8846`, `--as agent1`) | `ATT-8846` | Promoted `REQ-HRV-045` to `[Implemented]` following Stage 4 construction (`NavRoutes.kt`, `ATrainingTrackerApp.kt`, `MorningHrvCheckScreen.kt`), Rule 8.5 Light/Dark PNG verification, and green targeted/contract test suites. |
| `1.0.11` | 2026-11-19 | Lane B Author (`ATT-8846`, `--as agent1`) | `ATT-8846` | Promoted `REQ-HRV-045` to `[Verified]` in Stage 5 reusing Gate 4 verified `43/43 PASS` evidence (`Rule 7.5`, `0` `app/` code drift since `c7c6cc76`), Rule 8.5 PNG inspection, and living-doc governance sync atop `6927b2f8`. |
| `1.0.12` | 2026-11-19 | Lane B Author (`ATT-8953`, `--as agent1`) | `ATT-8953` | Added `REQ-HRV-046` (`[Planned]`, `.1`–`.4`) for inverted DFA alpha 1 Y-axis (`0.20` Anaerob top -> `1.40` Aerob bottom) and reconciled `REQ-HRV-044.1` top/bottom band ordering. |
| `1.0.13` | 2026-11-19 | Lane B Author (`ATT-8953`, `--as agent1`) | `ATT-8953` | Promoted `REQ-HRV-046` to `[Implemented]` following Stage 4 construction (`TelemetryZoneMath.kt`, `TelemetryMetricGraph.kt`), Rule 8.5 Light/Dark PNG verification, and 100% pass across all 8 test/contract suites. |
| `1.0.14` | 2026-11-19 | Lane B Author (`ATT-8953`, `--as agent1`) | `ATT-8953` | Promoted `REQ-HRV-046` to `[Verified]` in Stage 5 reusing Gate 4 verified `56/56 PASS` evidence across all 8 suites (`Rule 7.5`, `0` `app/` code drift since `c152801d`), Rule 8.5 PNG certification, and governance sync atop `2d7f1489`. |
| `1.0.15` | 2026-11-19 | Lane B Author (`ATT-9171`, `--as agent1`) | `ATT-9171` | Added `REQ-HRV-047` (`[Planned]`, `.1`–`.4`) for dynamic, outlier-resilient `HRV_RMSSD` Y-axis scaling, artifact exclusion, reactive `remember` keys, and clean axis label formatting. |
| `1.0.16` | 2026-11-19 | Lane B Author (`ATT-9171`, `--as agent1`) | `ATT-9171` | Promoted `REQ-HRV-047` to `[Implemented]` following Stage 4 construction (`TelemetryZoneMath.kt`, `TelemetryMetricGraph.kt`), Rule 8.5 Light/Dark PNG verification (`ATT-9171_rmssd_dynamic_yaxis.png`), and 100% pass across all 7 unit/UI/contract test suites (`59/59`). |
| `1.0.17` | 2026-11-19 | Lane B Author (`ATT-9171`, `--as agent1`) | `ATT-9171` | Promoted `REQ-HRV-047` to `[Verified]` in Stage 5 reusing Gate 4 verified `59/59 PASS` evidence across all 7 suites (`Rule 7.5`, `0` `app/` code drift since `c2979843`), Rule 8.5 PNG certification, and governance sync. |
| `1.0.18` | 2026-11-20 | Lane B Author (`ATT-9160`, `--as agent1`) | `ATT-9160` | Added `REQ-HRV-048` (`[Planned]`, `.1`–`.4`) for beat-level artifact/gap flag persistence in `WorkoutRr.db`, 180s causal filter context in `HRVMetricsDevice`, and unified artifact gate threshold plumbing in `HrvPostWorkoutRecomputer`. |
| `1.0.19` | 2026-11-20 | Lane B Author (`ATT-9160`, `--as agent1`) | `ATT-9160` | Promoted `REQ-HRV-048` to `[Implemented]` following Stage 4 construction (`RrArtifactFilter.kt`, `WorkoutRrDatabaseManager.kt`, `WorkoutRrRepository.kt`, `HrvPostWorkoutRecomputer.kt`, `HRVMetricsDevice.kt`) and 100% pass across all 10 unit/DB/golden/contract test suites. |
| `1.0.20` | 2026-11-20 | Lane B Author (`ATT-9160`, `--as agent1`) | `ATT-9160` | Promoted `REQ-HRV-048` to `[Verified]` in Stage 5 reusing Gate 4 verified `76/76 PASS` evidence across all 10 suites (`Rule 7.5`, `0` `app/` code drift since `f6f7bb41`), clean-checkout `TST-HRV-048` traceability parity, and governance sync. |
| `1.0.21` | 2026-11-20 | Lane B Author (`ATT-9177`, `--as agent1`) | `ATT-9177` | Added `REQ-HRV-049` (`[Planned]`, `.1`–`.4`) for Polar Verity Sense optical PPI support in Morning HRV Check, SQLite v2->v3 provenance persistence, `.attbackup` parity, ECG-vs-optical SWC baseline isolation, `SingleChoiceSegmentedButtonRow` trend selector, and 9-locale localization. |
| `1.0.22` | 2026-11-20 | Lane B Author (`ATT-9177`, `--as agent1`) | `ATT-9177` | Promoted `REQ-HRV-049` to `[Implemented]` following Stage 4 construction (`RrBeatEvent.kt`, `HrvSignalQualityAnalyzer.kt`, `HrvSensorModality.kt`, `HrvReadiness*.kt`, `MorningHrv*.kt`, `HrvReadinessTrend*.kt`, 9-locale `strings_hrv.xml`), Rule 8.5 Light/Dark PNG verification (`ATT-9177_morning_hrv_optical_ppi.png`), and 100% pass across all 16 targeted/contract/localization/UI suites (`133/133`). |
| `1.0.23` | 2026-11-20 | Lane B Author (`ATT-9177`, `--as agent1`) | `ATT-9177` | Promoted `REQ-HRV-049` to `[Verified]` in Stage 5 reusing Gate 4 verified `133/133 PASS` evidence across all 16 suites (`Rule 7.5`, `0` `app/` code drift since `88c46b60`), Rule 8.5 PNG certification, clean-checkout `TST-HRV-049` traceability parity, and governance sync. |
| `1.0.24` | 2026-11-20 | Lane B Author (`ATT-8990`, `--as agent1`) | `ATT-8990` | Added `REQ-HRV-050` (`[Planned]`, `.1`–`.4`) for per-device HRV source selection (`DeviceType.HRM`), `MySensorManager` `HRV_PRIORITY`, shared `HrvRollingEngine` + live rolling `HRV_SDNN`, lazy `PerDeviceHrvController` with disconnect stale-value guard, and isolated cockpit/ramp-test HRV lookup. |
| `1.0.25` | 2026-11-20 | Lane B Author (`ATT-8990`, `--as agent1`) | `ATT-8990` | Promoted `REQ-HRV-050` to `[Implemented]` following Stage 4 construction (`DeviceType.java`, `MySensorManager.java`, `HrvRollingEngine.kt`, `HrvTelemetryProvider.kt`, `PerDeviceHrvController.kt`, `HRVMetricsDevice.kt`, `BTLEHeartRateDevice.java`, `ANTHeartRateDevice.java`, `PolarOnlineStreamingBridge.kt`, `SensorFieldState.kt`, `TrackingViewModel.kt`, `SensorFieldView.kt`, `EditSensorFieldDialog.kt`, `HrvRampTestViewModel.kt`), `:app:assembleDebug`, and `137/137 PASS` across all 17 targeted/contract suites with `0` edits to `DeviceManager.java`, `TrackerService.java`, and `DevicesDatabaseManager.java`. |
| `1.0.26` | 2026-11-20 | Lane B Author (`ATT-8990`, `--as agent1`) | `ATT-8990` | Promoted `REQ-HRV-050` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9247`) verified `137/137 PASS` evidence across all 17 suites + `:app:assembleDebug` (`Rule 7.5`, `0` `app/` code drift since `7a7b387a`), `4/4` Python safety linters PASS, clean-checkout `TST-HRV-050` traceability parity, and governance sync. |
| `1.0.27` | 2026-11-20 | Lane A Flash (`ATT-8330`, `--as agent1`) | `ATT-8330` | Promoted `REQ-PER-008` to `[Verified]` (`TST-PER-009` + `TST-UNT-012`, `TST-UNT-013`, Gate 4 `3e20473b`, `Rule 7.5`). |
| `1.0.28` | 2026-11-20 | Lane B Author (`ATT-9176`, `--as agent1`) | `ATT-9176` | Added and promoted `REQ-HRV-051` (`[Verified]`, `.1`–`.4`, `SYSREQ-TRK-012`) for Polar Verity Sense optical PPI streaming and workout DFA alpha-1 isolation (`121/121 PASS` on `13416d59`). |
| `1.0.29` | 2026-11-20 | AI Agent (`ATT-8664`, `--as agent1`) | `ATT-8664` | Added `REQ-PER-046` `[Planned]` (`REQ-PER-046.1`–`.4`) for authoritative full-period primary region selection (`PeriodRegionPreview`), sport-aware preview track curation (`<= 8` from winning region, indoor isolation, `workoutIdToAllPolylinesMap == emptyMap()`), primary-region-keyed LRU/`produceState` caching & center geocoding parity, and strict `REQ-PER-039` memory bounds (`Localization Parity: N/A - no UI strings`). |
| `1.0.30` | 2026-11-20 | AI Agent (`ATT-8664`, `--as agent1`) | `ATT-8664` | Promoted `REQ-PER-046` to `[Implemented]` following Stage 4 construction (`PeriodData.kt` `288/300`, `PeriodsRepository.kt` `1,211/1,500`, `PeriodsViewModel.kt` `430/1,500`, `PeriodMapUtils.kt` `474/1,500`, `PeriodSummaryCard.kt` `792/1,500`), `:app:assembleDebug`, `0` new Kotlin compiler warnings, and `99/99 PASS` across all 13 targeted/contract suites with `0` edits to `SpatialRegionEngine.kt`, `PeriodsScreen.kt`, and protected monoliths. |
| `1.0.31` | 2026-11-20 | AI Agent (`ATT-8664`, `--as agent1`) | `ATT-8664` | Promoted `REQ-PER-046` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9311`) verified `99/99 PASS` evidence across all 13 suites + `:app:assembleDebug` + `0` new warnings (`Rule 7.5`, `0` `app/` code drift since `a68fb43a`), `4/4` Python safety linters PASS, clean-checkout `TST-PER-054` traceability parity, and governance sync. |
| `1.0.32` | 2026-11-21 | Single-Lane Author (`ATT-9360`, `--as agent1`) | `ATT-9360` | Refined `REQ-WKO-039` (`[Planned]`, `.1`–`.3`, with 4-field Chesterton's Fence Audit) to gate PMC ACWR on `MIN_CTL_FOR_ACWR = 15.0` and normalize Tier-4 RPE fallback onto the TSS scale via `calculateRpeTss()` (`RPE 1..10 -> 20..140` TSS/h, 1h @ RPE 8 = `100.0` TSS) while keeping `REQ-WKO-038` `calculateSessionRpe()` untouched. |
| `1.0.33` | 2026-11-21 | Single-Lane Author (`ATT-9360`, `--as agent1`) | `ATT-9360` | Promoted `REQ-WKO-039` to `[Implemented]` (`PowerPhysiologyCalculator.kt`: `MIN_CTL_FOR_ACWR = 15.0`, `calculateRpeTss()`, Tier-4 delegation, `calculateSessionRpe()` untouched). |
| `1.0.34` | 2026-11-21 | Single-Lane Author (`ATT-9360`, `--as agent1`) | `ATT-9360` | Promoted `REQ-WKO-039` to `[Verified]` in Stage 5 (`Rule 7.5` Doc-Only Fast Path reusing Gate 4 `ATT-9534` @ `51ce6830`: `67/67 PASS` across 7 Tier-1 suites, `0` `app/` drift, `0` new warnings, `3/3` safety linters PASS, `--check-verified-evidence` `exit 0`). |
| `1.0.35` | 2026-11-22 | Lane 2 Author (`ATT-9355`, `--as agent1`) | `ATT-9355` | Refined `REQ-PER-044.4` (`[Verified]`) to swap `CADENCE` and `HEART_RATE` in `TelemetryChartStack.kt` & `MapModels.kt` (`SPEED_PACE → SLOPE → CADENCE → POWER → HEART_RATE → W_PRIME_BAL → HRV_DFA_A1 → HRV_RMSSD`) while preserving DataStore enum-name persistence and `WorkoutSummary.kt` invariants. |
| `1.0.36` | 2026-11-22 | Lane 2 Author (`ATT-9069`, `--as agent1`) | `ATT-9069` | Added and verified (`[Verified]`) `REQ-PER-047` (`.1`–`.3`) for live-updating active workout card via O(1) `LiveWorkoutCardStateStore` `StateFlow<LiveWorkoutCardSnapshot?>`, strictly card-scoped `collectAsStateWithLifecycle` in `WorkoutSummary`/`WorkoutSummaryCompact`, and zero `_allWorkouts` / downstream OOM churn (`43/43` PASS across 8 suites, `0` `TrackerService.java` edits, Light/Dark AMOLED `Rule 8.5` PNG verified). |
| `1.0.37` | 2026-11-22 | Lane 2 Author (`ATT-9069`, `--as agent1`) | `ATT-9069` | Stage 6 Bounce 1 heal: tightened `extractSensorSample` to exact canonical/quoted-device/4-location-provider allowlist (preventing sibling derived columns like `POWER_PEAK_5S`/`POWER_TO_WEIGHT` during dropouts), keyed `LiveExtremaLabels` on `TrainingApplication.getUnit()`, and synced sprint branch (`44/44` PASS across 8 suites). |
| `1.0.38` | 2026-11-23 | Lane 1 Author (`ATT-8131`, `--as agent1`) | `ATT-8131` | Added `REQ-PER-056` (`[Planned]`, `.1`–`.4`) for decommissioning `PeriodSummaries.db` / `PeriodSummariesDatabaseManager`, stateless reactive in-memory `PeriodsRepository` projection on `Dispatchers.Default`, instant crop/edit consistency, one-time background `PeriodSummaries*` disk cleanup, and legacy `.attbackup` restore compatibility. |
| `1.0.39` | 2026-11-23 | Lane 1 Author (`ATT-8131`, `--as agent1`) | `ATT-8131` | Promoted `REQ-PER-056` to `[Verified]` following Stage 4 implementation (`-1,030` net prod LoC), `611/611` PASS across 123 unit/contract suites (`0` new warnings), live `emulator-5554` verification (`0` crashes, `0` `PeriodSummaries*` files on disk, `Rule 8.5` PNGs inspected), and SHA-256 evidence bundle (`docs/engineering/test_evidence/tickets/ATT-8131/`). |
| `1.0.40` | 2026-11-23 | Lane 1 Author (`ATT-8131`, `--as agent1`) | `ATT-8131` | Stage 6 Bounce 1/5 heal: grounded `REQ-PER-056.4` to authentic `MigrationEngine.performFullRestore` and clarified that `period_summaries_db_decommissioned_cleaned` is persisted strictly when all obsolete deletions succeed without error. |
| `1.0.41` | 2026-11-23 | Lane 2 Author (`ATT-9580`, `--as agent1`) | `ATT-9580` | Added `REQ-PER-057` (`[Planned]`, `.1`–`.4`) decoupling cold cluster preview enrichment progress from `_allClusters.value.isEmpty()`, adding version-guarded non-clobbering lightweight load publication, guaranteed `finally` progress cleanup, and injectable `ioDispatcher` (`TST-PERF-010` race fix). |
| `1.0.42` | 2026-11-24 | Lane 2 Author (`ATT-9580`, `--as agent1`) | `ATT-9580` | Gate 3 (`ATT-9671`) revision: refined `REQ-PER-057.1`–`.3` to mandate cooperative `yield()` after each progress write, fast-path return before `try/finally` `_migrationStatus` writes, field declarations above `init {}`, stale SQLite writeback guards + 1-attempt empty-cache retry in `loadAllClustersLightweight()`, and `statePublishLock` parity in `refreshClusterStats()`. |
| `1.0.43` | 2026-11-24 | Lane 2 Author (`ATT-9580`, `--as agent1`) | `ATT-9580` | Promoted `REQ-PER-057` to `[Verified]` following Stage 4/5 implementation (`WorkoutClusterRepository.kt` `1,014 / 1,500` LoC), `62/62` PASS across 8 targeted & contract suites (`0` fail, `0` skip, `0` new warnings), `3/3` static linters PASS, live `emulator-5554` smoke (`0` crashes, `Rule 8.5` PNG inspected), and SHA-256 evidence bundle (`docs/engineering/test_evidence/tickets/ATT-9580/`). |
| `1.0.44` | 2026-11-24 | Lane 1 Author (`ATT-9323`, `--as agent1`) | `ATT-9323` | Added `REQ-HRV-052` (`[Planned]`, `.1`–`.4`) for Morning HRV Check HR/HRV sensor availability gating, defense-in-depth ViewModel start guard, disconnected status guidance, standalone wellness preservation, and 9-locale localization parity. |
| `1.0.45` | 2026-11-24 | Lane 1 Author (`ATT-9323`, `--as agent1`) | `ATT-9323` | Promoted `REQ-HRV-052` to `[Verified]` following Stage 4/5 implementation (`MorningHrvCheckScreen.kt` `1,460/1,500` LoC, `MorningHrvCheckViewModel.kt` `655/1,500` LoC, 9/9 `strings_hrv.xml`), `58/58` PASS across 5 targeted/localization/contract suites (`0` new warnings), live `emulator-5554` Light/Dark PNG verification (`Rule 8.5`, `0` crashes/ANRs), and SHA-256 evidence bundle (`docs/engineering/test_evidence/tickets/ATT-9323/`). |
