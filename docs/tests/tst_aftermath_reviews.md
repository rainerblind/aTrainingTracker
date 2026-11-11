# Living Test Specification: Jubilee Milestones & App Reviews

**Domain**: `aftermath_reviews`  
**Prefixes**: `REV`  
**Scope**: Milestone workout auto-naming, jubilee achievements, Google Play in-app reviews, athlete feedback, and supporter tier promotions.  
**Total Tests**: 11

---

### TST-REV-001: **Sport-Specific Milestone Workout Counter Unit Test** [Verified]
- **Requirement(s)**: `REQ-REV-001`
- **Component**: `WorkoutSummariesDatabaseManager.java`, `MilestoneAutoNamingManager.kt`
- **Jira**: `ATT-2308`
- **Test Description**: 1. Insert 99 finished Bike workouts and 99 finished Run workouts. 2. Verify `getFinishedWorkoutCountForSport` returns 99 for each sport. 3. Insert 1 finished Bike workout (hitting 100). 4. Verify Bike counter reaches 100 while Run remains 99. 5. Verify milestone threshold evaluator returns true for 100, 250, 500, 1000, 1500 and false for non-milestone counts.

### TST-REV-002: **Dynamic Milestone Auto-Naming Title Formatting & Export Propagation Contract Test** [Verified]
- **Requirement(s)**: `REQ-REV-002`
- **Component**: `MilestoneAutoNamingManager.kt`, `WorkoutAutoNamingHelper.kt`
- **Jira**: `ATT-2308`
- **Test Description**: 1. Format title for 100th Bike workout with base cluster name "Hausrunde". 2. Verify formatted title matches localized string pattern "[Count]. [SportName]: [ClusterName]" (e.g. "100. Radfahrt: Hausrunde"). 3. Verify title is persisted in `WorkoutSummaries.WORKOUT_NAME` prior to export.

### TST-REV-003: **First-Jubilee Celebration Dialog Tier Gating, Reordering & Intent Dispatch Contract Test** [Verified]
- **Requirement(s)**: `REQ-REV-003`
- **Component**: `FirstJubileeCelebrationDialog.kt`
- **Jira**: `ATT-2308`, `ATT-5401`, `ATT-5399`, `ATT-5467`, `ATT-5659`, `ATT-5658`
- **Test Description**: 1. Launch dialog in Free and Pioneer tier modes. 2. Verify top-to-bottom ordering: milestone message, email feedback prompt and button, Play Store review prompt and button, and financial development support card at the bottom. 3. Launch dialog with active paid Pro tier (Bronze, Silver, Gold, Diamond). 4. Verify financial support card is cleanly hidden, while email and Play Store sections remain visible. 5. Trigger review action and verify visible outcome: verify `Context.findActivity()` resolution, and ensure that in debug mode, or if `launchReviewFlow` terminates immediately (< 300ms) or without Activity, `openPlayStorePage(context)` fallback is immediately launched.

### TST-REV-004: **Single-Prompt Invariant & Zero-Spam Re-prompt Suppression Test** [Verified]
- **Requirement(s)**: `REQ-REV-004`
- **Component**: `SettingsDataStore.kt`, `MilestoneAutoNamingManager.kt`
- **Jira**: `ATT-2308`
- **Test Description**: 1. Verify initial `has_shown_first_jubilee_prompt` is false. 2. Trigger first jubilee celebration dialog and verify flag is persisted as true. 3. Trigger subsequent milestone (e.g. 250th Bike or 100th Run). 4. Verify title is formatted with milestone prefix but celebration dialog is never triggered again.

### TST-REV-005: **9-Locale Localization Parity & String Resource Verification** [Verified]
- **Requirement(s)**: `REQ-REV-002`, `REQ-REV-003`
- **Component**: `res/values/strings.xml`, `TranslationParityTest.kt`
- **Jira**: `ATT-2308`, `ATT-5467`, `ATT-5659`, `ATT-5658`
- **Test Description**: Run `TranslationParityTest` (`TST-LOC-001`) asserting that all milestone title templates, dialog headers, review button labels, email body strings, transitional feedback prompts (`jubilee_email_feedback_prompt`, `jubilee_review_prompt`), and supporter appeal texts exist with 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, and PT with valid literal linebreaks, matching format specifiers, and zero decorative inline Unicode emojis.

### TST-REV-006: **SupporterDebugSection Jubilee Preview Trigger Contract Test** [Verified]
- **Requirement(s)**: `REQ-REV-005`
- **Component**: `SupporterDebugSection.kt`
- **Jira**: `ATT-2308`
- **Test Description**: 1. Verify preview button is present when `BuildConfig.DEBUG == true`. 2. Tap preview button and verify mock celebration dialog opens. 3. Confirm `has_shown_first_jubilee_prompt` remains unmutated in DataStore.

### TST-REV-007: **Map Snapshot Share Pager Stability, Header Deduplication & Navigation Restoration Contract Test** [Verified]
- **Requirement(s)**: `REQ-REV-006`
- **Component**: `WorkoutDetailTabbedContent.kt`, `MapDetailLayout.kt`, `TrackOnMapScreen.kt`, `WorkoutSummariesTabbedScreen.kt`, `WorkoutShareBottomSheetContractTest.kt`
- **Jira**: `ATT-6322`
- **Test Description**: 1. Verify `WorkoutDetailTabbedContent.kt` does not pass `pagerState.currentPage` to `LaunchedEffect(pendingAutoShare, mapTabIndex)` and invokes `scrollToPage(mapTabIndex)`. 2. Verify `WorkoutDetailTabbedContent` captures `originPageIndex` and restores it on snapshot completion. 3. Verify `TrackOnMapScreen` propagates `onSnapshotComplete` and list containers (`WorkoutSummariesTabbedScreen`, `WorkoutSummariesListFragment`, `WorkoutClustersScreen`) wire it to dismiss the detail screen when `initialAutoShareSnapshot == true`. 4. Verify `MapDetailLayout` supports `showHeader: Boolean = true`, records `headerLayer` offscreen when `showHeader == false`, and `WorkoutDetailTabbedContent` passes `showHeader = false`.

### TST-REV-008: **Workout Cluster Reassignment Centroid Integrity & Sport Frequency Verification Suite** [Verified]
- **Requirement(s)**: `REQ-REV-007`
- **Component**: `WorkoutClusterEngine.kt`, `EditWorkoutClusterTest.kt`
- **Jira**: `ATT-6159`
- **Test Description**:
  1. *Centroid and Sport Preservation Unit Test*: Verify `moveWorkoutToCluster` calculates and persists updated centroids without overwriting them with stale pre-update copies.
  2. *Reassignment Order Verification*: Verify `assignClusterToWorkout` executes before `getMostFrequentSportIdForCluster`, and the updated sport ID and sport type are persisted to freshly loaded cluster instances.
  3. *Tier 1 Targeted Regression Suite*: Execute targeted unit tests verifying 100% pass rate across cluster database classes and project build contract tests.

### TST-REV-009: **Generic ZoneDistributionCard Architecture, Wrapper Delegation & Display Mode Contract Test** [Verified]
- **Requirement(s)**: `REQ-REV-008`
- **Component**: `ZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, `HeartRateZoneDistributionCard.kt`, `GradeZoneDistributionCard.kt`, `ZoneDistributionCardTest.kt`, `WorkoutSummaryClickContractTest.kt`
- **Jira**: `ATT-6044`
- **Test Description**:
  1. *Unit & Contract Architecture Test*: Verify `ZoneDistributionCard` handles null/present histogram data, correct button counts (2 vs 3), and proper chart dispatch across `STACKED_BAR`, `COLUMNS`, and `HISTOGRAM`.
  2. *Wrapper Delegation & State Isolation Test*: Verify `PowerZoneDistributionCard`, `HeartRateZoneDistributionCard`, and `GradeZoneDistributionCard` delegate to `ZoneDistributionCard` with their respective icons, localized titles, and units (`"W"`, `"bpm"`, `"%"`), maintaining independent display mode state and honoring `showModeSelector`.
  3. *Call Site & Click Contract Preservation*: Verify `WorkoutSummaryClickContractTest` continues to pass, ensuring `.then(mapClickModifier)` chains correctly onto wrapper composable invocations in `WorkoutSummary.kt`.
  4. *Tier 1 Targeted Verification*: Execute targeted unit tests verifying 100% pass rate across zone distribution tests (`ZoneDistributionCardTest`, `GradeZoneDistributionCardTest`, `ZoneDistributionStackedBarTest`) and project contract tests.


### TST-REV-010: **1st-Order Dynamical Power-HR Identification Engine, Persistence, Toggle & 9-Locale Localization Verification Suite** [Verified]
- **Requirement(s)**: `REQ-REV-009`
- **Component**: `PowerHrDynamicsCalculator.kt`, `WorkoutSummariesDatabaseManager.java`, `WorkoutRepository.kt`, `LegacyImportEngine.kt`, `PhysiologyModelsSection.kt`, `PowerHrDynamicsInfoDialog.kt`, `res/values*/strings_cardio_power.xml`
- **Jira**: `ATT-3904`
- **Test Description**:
  1. *Engine Unit Tests (`PowerHrDynamicsCalculatorTest`)*: Verify OE parameter identification on synthetic step-response signals with known $(\tau, \text{HR}_0, K)$, verify window slicing (fresh min 8–55 vs end 45 min), verify rejection of insufficient data (< 20 min or missing stream), and verify drift deltas.
  2. *Database Migration & CRUD Contract Test (`WorkoutSummariesCardioPowerMigrationTest`)*: Verify schema migration from DB v28 to v29 creates table `cardio_power_dynamics`, test single and batch query symmetry, cascade deletion on workout removal, and null-safety.
  3. *Repository & Three-Way Parity Contract Test (`WorkoutRepositoryCardioPowerTest`)*: Verify lazy backfill computes and persists records on demand, verify `LegacyImportEngine` persists dynamics on file import, and verify `TrackerService` finalization parity.
  4. *UI & 5th School Toggle Contract Test (`PhysiologyModelsSectionTest`)*: Verify 5th school toggle binds to DataStore, verifies Info button opens `PowerHrDynamicsInfoDialog`, and verifies UI gating.
  5. *9-Locale Localization Parity Test (`TranslationParityTest`)*: Verify all strings in `strings_cardio_power.xml` across EN, DE, ES, FR, IT, JA, NL, PL, PT with valid linebreaks and matching format specifiers.
  6. *Tier 1 Targeted Verification Suite*: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.physiology.*" --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*" --tests "com.atrainingtracker.trainingtracker.build.*"`


### TST-REV-011: **Cardio-Power Visual Display & Overlay Verification Suite** [Verified]
- **Requirement(s)**: `REQ-REV-010`
- **Component**: `WorkoutDataMapper.kt`, `WorkoutDetailsData.kt`, `WorkoutDetails.kt`, `CardioPowerDynamicsCard.kt`, `TelemetryMetricGraph.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`
- **Jira**: `ATT-3905`
- **Test Description**:
  1. *Data Hydration Test (`WorkoutDataMapperTest`)*: Verify mapper hydrates `CardioPowerDynamicsRecord` into `WorkoutData` and `WorkoutDetailsData`.
  2. *Grouped Metric Row Layout & §5.10 Compliance Test (`WorkoutDetailsCardioPowerRowTest`)*: Verify `CardioPowerDynamicsRow` conforms strictly to §5.10 (single `Row`, 3 `MetricItem`s, zero multi-row wrapping, proper divider padding, clean null suppression).
  3. *Telemetry Graph Fresh Model Overlay Test (`TelemetryMetricGraphOverlayTest`)*: Verify toggle chip state management, trajectory simulation sampling, dashed curve drawing parameters, and drift shading bounds.
  4. *Settings Gating & Visibility Test (`WorkoutDetailsVisibilityTest`)*: Verify row visibility honors `enableCardioPowerDynamics`, `showCardioPowerInWorkoutSummary`, and data presence.
  5. *9-Locale Localization Parity Test (`TranslationParityTest`)*: Verify all new cardio-power UI tokens in `strings_cardio_power.xml` across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### TST-REV-012: **Cardio-Power Longitudinal Fitness Trends Verification Suite** [Verified]
- **Requirement(s)**: `REQ-REV-011`
- **Component**: `WorkoutSummariesDatabaseManager.java`, `AthleteProfileViewModel.kt`, `AthleteProfileScreen.kt`, `CardioPowerTrendsCard.kt`
- **Jira**: `ATT-3906`
- **Test Description**:
  1. *Database Trend Query Test (`WorkoutSummariesCardioPowerTrendTest`)*: Verify `getCardioPowerDynamicsFitnessTrendSamples` returns samples with $R^2 \ge 0.50$ sorted ascending by workout start timestamp and excludes lower-fit workouts.
  2. *ViewModel Reactive State Test (`AthleteProfileViewModelCardioPowerTest`)*: Verify `AthleteProfileViewModel` properly filters samples by selected timeframe, toggles active metric type, and respects `enableCardioPowerDynamics` gating.
  3. *Canvas Plot Math & Rule 48 Audit Test (`CardioPowerTrendsPlotMathTest`)*: Verify IQR domain clamping, date labeling, Y-axis scale value formatting, and rolling 30-day average calculations.
  4. *UI Component Contract Test (`CardioPowerTrendsCardTest`)*: Verify header info icon interaction, metric/range selector pill clicks, empty-state message rendering, and layout stability.
  5. *9-Locale Localization Parity Test (`TranslationParityTest`)*: Verify all new cardio-power trend string tokens in `strings_cardio_power.xml` across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### TST-REV-013: **Real-Time Live HR Drift & Cockpit Telemetry Verification Suite** [Verified]
- **Requirement(s)**: `REQ-REV-012`
- **Component**: `CyclingPowerMetricsDevice.java`, `SensorType.java`, `ActivityType.java`, `SignedIntegerFormatter.java`, `TrackingViewModel.kt`, `SensorFieldState.kt`, `res/values*/strings_cardio_power.xml`
- **Jira**: `ATT-3907`, `ATT-6725`
- **Test Description**:
  1. *Real-Time Filter & Propagation Unit Test (`CyclingPowerMetricsDeviceLiveHrDriftTest`)*: Verify 1st-order discrete-time state propagation ($x_k = \alpha x_{k-1} + (1-\alpha) P_k$), lag compensation on step workloads, 30s growing-window residual calculation, and signed integer emission.
  2. *Zero-Query Loop & 30-Day Seed Contract Test (`CyclingPowerMetricsDeviceLiveHrDriftTest`)*: Verify that `calculateMetrics()` performs zero SQLite queries and zero blocking DataStore reads; verify 30-day rolling baseline resolution and session caching in `resetSession()`.
  3. *In-Ride Minute 8–25 Calibration Test (`CyclingPowerMetricsDeviceLiveHrDriftTest`)*: Verify buffering of active 1-Hz `(Power, HR)` pairs during minutes 8–25 and dynamic baseline update via `PowerHrDynamicsCalculator.fitWindow()` when $R^2 \ge 0.50$.
  4. *Sensor-Presence Gating & Sport Scoping Test (`LiveHrDriftGatingTest`)*: Verify Rule 51 constructor parity and `resetSession()`, Rule 52 gating requiring both `POWER` and `HR` sensors, null suppression when unlinked or when `enableCardioPowerDynamics == false`, long pause flush (> 30s), and scoping to `ActivityType.BIKE_POWER` only.
  5. *Formatter & SensorType Registration Test (`SignedIntegerFormatterTest`)*: Verify `SignedIntegerFormatter` formats positive integers with `+` prefix (e.g. `+5`), zero as `0`, negative as `-3`, and handles nulls gracefully. Verify `SensorType.LIVE_HR_DRIFT` category, units, parcelable, and icon.
  6. *Cockpit Grid & Semantic Color State Test (`TrackingViewModelLiveDriftTest`)*: Verify `TrackingViewModel` maps drift ranges ($\le +5.0\text{ bpm}$ Zone 2, $+5.0 < \text{drift} \le +10.0\text{ bpm}$ Zone 3, $> +10.0\text{ bpm}$ Zone 4) to correct semantic zone colors, and verify cell formatting in `SensorFieldState`.
  7. *9-Locale Localization Parity Test (`TranslationParityTest`)*: Verify `live_hr_drift` and `live_hr_drift_short` in `strings_cardio_power.xml` across EN, DE, ES, FR, IT, JA, NL, PL, PT.


