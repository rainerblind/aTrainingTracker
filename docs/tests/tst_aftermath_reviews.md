# Living Test Specification: Jubilee Milestones & App Reviews

**Document ID**: `DOC-TST-REVIEWS`  
**Version**: 1.0.12  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `aftermath_reviews`  
**Prefixes**: `REV`  
**Scope**: Milestone workout auto-naming, jubilee achievements, Google Play in-app reviews, athlete feedback, and supporter tier promotions.  
**Total Tests**: 19

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
- **Component**: `WorkoutDetailTabbedContent.kt`, `MapDetailLayout.kt`, `TrackOnMapScreen.kt`, `WorkoutSummariesTabbedScreen.kt`, `WorkoutShareBottomSheetContractTest.kt`, `MapDetailLayoutSnapshotContractTest.kt`
- **Jira**: `ATT-6322`, `ATT-6685`, `ATT-7316`
- **Test Description**: 1. Verify `WorkoutDetailTabbedContent.kt` does not pass `pagerState.currentPage` to `LaunchedEffect(pendingAutoShare, mapTabIndex)` and invokes `scrollToPage(mapTabIndex)`. 2. Verify `WorkoutDetailTabbedContent` captures `originPageIndex` and restores it on snapshot completion. 3. Verify `TrackOnMapScreen` propagates `onSnapshotComplete` and list containers (`WorkoutSummariesTabbedScreen`, `WorkoutSummariesListFragment`, `WorkoutClustersScreen`) wire it to dismiss the detail screen when `initialAutoShareSnapshot == true`. 4. Verify `MapDetailLayout` supports `showHeader: Boolean = true`, records `headerLayer` offscreen when `showHeader == false`, and `WorkoutDetailTabbedContent` passes `showHeader = false`. 5. *UI-Thread GraphicsLayer Rasterization & Try-Finally Cleanup Contract Test (TST-REV-007.5 / ATT-6685)*: Verify in `MapDetailLayout.kt` that `headerLayer.toImageBitmap()`, `elevationLayer.toImageBitmap()`, and `analyticsLayer.toImageBitmap()` are executed directly on the UI dispatcher without `withContext(Dispatchers.Default)`, that downstream `combineWorkoutAndShare` executes safely, and that `onSnapshotReady` encapsulates execution in a `try-catch-finally` block ensuring `isSharing` is reset to `false` and `onSnapshotComplete?.invoke()` is called under all execution paths. 6. *ForgottenCoroutineScopeException & NonCancellable Share Safety Contract Test (TST-REV-007.6 / ATT-7316)*: Verify in `MapDetailLayout.kt` that `executeSnapshotCaptureAndShare` executes `shareAction` within `NonCancellable`, safely catches and ignores `CancellationException` and `ForgottenCoroutineScopeException` without routing to `onError` (preventing false `R.string.error_sharing_failed` toasts), and ensures `onFinally` is executed under all cancellation scenarios.

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
- **Component**: `PowerHrDynamicsCalculator.kt`, `WorkoutSummariesDatabaseManager.java`, `WorkoutRepository.kt`, `LegacyImportEngine.kt`, `PhysiologyModelsSection.kt`, `PowerHrDynamicsInfoDialog.kt`, `AdvancedTuningAccordion.kt`, `res/values*/strings_cardio_power.xml`
- **Jira**: `ATT-3904`, `ATT-6722`
- **Test Description**:
  1. *Engine Unit Tests (`PowerHrDynamicsCalculatorTest`)*: Verify OE parameter identification on synthetic step-response signals with known $(\tau, \text{HR}_0, K)$, verify window slicing (fresh min 8–55 vs end 45 min), verify rejection of insufficient data (< 20 min or missing stream), and verify drift deltas.
  2. *Database Migration & CRUD Contract Test (`WorkoutSummariesCardioPowerMigrationTest`)*: Verify schema migration from DB v28 to v29 creates table `cardio_power_dynamics`, test single and batch query symmetry, cascade deletion on workout removal, and null-safety.
  3. *Repository & Three-Way Parity Contract Test (`WorkoutRepositoryCardioPowerTest`)*: Verify lazy backfill computes and persists records on demand, verify `LegacyImportEngine` persists dynamics on file import, and verify `TrackerService` finalization parity.
  4. *UI & 5th School Toggle Contract Test (`PhysiologyModelsSectionTest`)*: Verify 5th school toggle binds to DataStore, verifies Info button opens `PowerHrDynamicsInfoDialog`, and verifies UI gating.
  5. *9-Locale Localization Parity Test (`TranslationParityTest`)*: Verify all strings in `strings_cardio_power.xml` across EN, DE, ES, FR, IT, JA, NL, PL, PT with valid linebreaks and matching format specifiers.
  6. *Filter Warm-Up Continuity Test (`PowerHrDynamicsCalculatorTest`)*: Verify pre-warming filter state $x_k(\tau)$ from $t = 0$ to $\text{startIndex} - 1$ eliminates transient step errors compared to un-warmed seeding with $P[\text{startIndex}]$, and converges to identical steady-state values under continuous constant power.
  7. *Physiological Plausibility & Gate Enforcement Test (`PowerHrDynamicsCalculatorTest`)*: Verify rejection (returning `null`) when $K < 0.01$ or $K > 0.45$, $\text{HR}_0 < 30$ or $\text{HR}_0 > 125$, $R^2_{\text{start}} < 0.25$, or active pedaling duration ($P > 0$) $< 15\text{ min}$ ($900\text{s}$), and verify acceptance when all physiological parameters lie within bounds.
  8. *Lazy Backfill Query & Ineligible Cache Test (`WorkoutRepositoryCardioPowerBackfillTest`)*: Verify that `getOrComputeCardioPowerDynamics` executes a projected query (`TIME_ACTIVE`, `TIME_TOTAL`, `TIME`, `POWER`, `HR`) ordered by `_id ASC`, records failed/non-qualifying rides in `ineligibleCardioPowerWorkoutIds` to bypass subsequent SQLite queries, updates in-memory `WorkoutData` on success, and `backfillHistoricalCardioPowerDynamics(limit = 20)` processes at most $N$ eligible historical rides.
  9. *Accordion Subtitle Formatting Test (`AdvancedTuningAccordionTest`)*: Verify `formatPhysiologyModelsSubtitle` appends localized `R.string.cardio_power_domain_label` when `enableCardioPowerDynamics == true`, preserves existing model labels, and maintains 100% 9-language translation parity.
  10. *Tier 1 Targeted Verification Suite*: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculatorTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryCardioPowerBackfillTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningAccordionTest" --tests "com.atrainingtracker.trainingtracker.translation.TranslationParityTest"`


### TST-REV-011: **Cardio-Power Visual Display & Overlay Verification Suite** [Verified]
- **Requirement(s)**: `REQ-REV-010`
- **Component**: `WorkoutDataMapper.kt`, `WorkoutDetailsData.kt`, `WorkoutDetails.kt`, `CardioPowerDynamicsCard.kt`, `TelemetryMetricGraph.kt`, `WorkoutSummary.kt`, `WorkoutDetailTabbedContent.kt`, `PowerHrDynamicsCalculator.kt`, `TrackOnMapScreen.kt`
- **Jira**: `ATT-3905`, `ATT-6723`
- **Test Description**:
  1. *Data Hydration Test (`WorkoutDataMapperTest`)*: Verify mapper hydrates `CardioPowerDynamicsRecord` into `WorkoutData` and `WorkoutDetailsData`.
  2. *Grouped Metric Row Layout & §5.10 Compliance Test (`WorkoutDetailsCardioPowerRowTest`)*: Verify `CardioPowerDynamicsRow` conforms strictly to §5.10 (single `Row`, 3 `MetricItem`s, zero multi-row wrapping, proper divider padding, clean null suppression).
  3. *Telemetry Graph Fresh Model Overlay Test (`TelemetryMetricGraphOverlayTest`)*: Verify toggle chip state management, trajectory simulation sampling, dashed curve drawing parameters, and drift shading bounds.
  4. *Settings Gating & Visibility Test (`WorkoutDetailsVisibilityTest`)*: Verify row visibility honors `enableCardioPowerDynamics`, `showCardioPowerInWorkoutSummary`, and data presence.
  5. *Decimated Step $\Delta t$ Trajectory Simulation Test (`PowerHrDynamicsCalculatorTest`)*: Verify simulation with non-uniform step timestamps ($dt \in [1, 60]\text{ s}$) maintains true exponential response kinetics without decimation-induced phase lag.
  6. *Y-Axis Bounds Fresh Model Inclusion Test (`TelemetryMetricGraphOverlayTest`)*: Verify that `(dataMin, dataMax)` for `HEART_RATE` dynamically includes `freshHrTrajectory` non-null values when overlay is active to prevent boundary clamping.
  7. *Workout Details CardioPowerDynamicsCard Presence & Decoupled Gating Test (`WorkoutDetailsCardioPowerRowTest`)*: Verify `CardioPowerDynamicsCard` renders in full Workout Details when `enableCardioPowerDynamics == true` even if `showCardioPowerInWorkoutSummary == false`.
  8. *9-Locale Localization Parity Test (`TranslationParityTest`)*: Verify all new cardio-power UI tokens in `strings_cardio_power.xml` across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### TST-REV-012: **Cardio-Power Longitudinal Fitness Trends Verification Suite** [Verified]
- **Requirement(s)**: `REQ-REV-011`
- **Component**: `WorkoutSummariesDatabaseManager.java`, `AthleteProfileViewModel.kt`, `AthleteProfileScreen.kt`, `CardioPowerTrendsCard.kt`
- **Jira**: `ATT-3906`, `ATT-6724`
- **Test Description**:
  1. *Database Trend Query & Timezone Test (`WorkoutSummariesCardioPowerTrendTest`)*: Verify `getCardioPowerDynamicsFitnessTrendSamples` parses timestamps using `SimpleDateFormat` in the local device timezone and returns samples with $R^2 \ge 0.50$ sorted ascending.
  2. *ViewModel Rolling Trend & Reactive Reload Test (`AthleteProfileViewModelCardioPowerTest`)*: Verify `AthleteProfileViewModel` computes the 30-day moving average on full history prior to range slicing (preserving boundary rolling values across 1M, 3M, 6M, 1Y, All), and triggers reactive reload when `workoutRepository.allWorkouts` emits updates.
  3. *Canvas Plot Math & Rule 48 Audit Test (`CardioPowerTrendsPlotMathTest`)*: Verify IQR domain clamping, date labeling, Y-axis scale value formatting, and rolling 30-day average calculations.
  4. *UI Component Contract & Touch Scrubbing Test (`CardioPowerTrendsCardTest`)*: Verify header info icon interaction, 5-pill range selector (`1M`, `3M`, `6M`, `1Y`, `All`), intermediate X-axis date tick rendering, and touch scrubbing (`ChartGestureDisambiguator`) with cursor and tooltip.
  5. *9-Locale Localization Parity Test (`TranslationParityTest`)*: Verify `athlete_profile_range_1m` and all cardio-power trend string tokens across EN, DE, ES, FR, IT, JA, NL, PL, PT.

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

### TST-REV-014: **EditWorkout Tag Tiles, Grouped Cloud Sync Card & Localization Contract Test** [Verified]
- **Requirements Covered**: `REQ-REV-013`
- **Component Under Test**: `EditWorkoutScreen.kt`, `EditWorkoutViewModel.kt`, `res/values*/strings.xml`
- **Test Type**: Unit, Robolectric / Compose Contract
- **Test Description**: Verifies that `EditWorkoutScreen` renders 3 equal-width toggle tiles (`weight(1f)`) with `Modifier.toggleable`, enforces Commute/Indoor mutual exclusivity in `EditWorkoutViewModel`, groups Strava and Health Connect inside a single outlined card with brand vector assets, and verifies 9-locale parity for `indoor_short`.
- **Preconditions**:
  1. Test environment with Robolectric / Compose testing context initialized.
  2. Sample `WorkoutData` instance.
- **Test Procedure**:
  1. *ViewModel Exclusivity Verification*: Invoke `updateIsCommute(true)`, assert `commute == true && trainer == false`. Invoke `updateIsTrainer(true)`, assert `trainer == true && commute == false`. Toggle `updateIsRace(true)`, assert race toggles independently.
  2. *Layout Contract Verification*: Verify `EditWorkoutScreen.kt` structure:
     - 3 equal-width tiles using `weight(1f)` in a single `Row(Arrangement.spacedBy(8.dp))`
     - `Modifier.toggleable` with `role = Role.Checkbox` applied to each tile
     - `Icons.Filled.EmojiEvents` used for race tile with `tertiaryContainer` styling
     - Outlined `Surface` / `Card` with `10.dp` shape for cloud sync grouping
     - `R.drawable.logo_square_strava` and `R.drawable.ic_health_connect` vector drawables used in sync rows
  3. *Localization Parity Verification*: Assert `R.string.indoor_short` exists across all 9 `values*/strings.xml` files.
- **Expected Results**:
  1. Commute and Indoor mutually exclude each other; Race is independent.
  2. Layout contract passes with 3 equal tiles and grouped sync card.
  3. `TranslationParityTest` passes across 9 locales.

### TST-REV-015: **EditWorkout Per-Workout Health Connect Sync & Export Deduplication Contract Test** [Verified]
- **Requirements Covered**: `REQ-REV-014`
- **Component Under Test**: `WorkoutData.kt`, `EditWorkoutViewModel.kt`, `EditWorkoutScreen.kt`, `WorkoutRepository.kt`, `ExportManager.java`
- **Test Type**: Unit, Robolectric / Component Test
- **Test Description**: Verifies that `EditWorkoutViewModel` initializes and updates `syncToHealthConnect`, `EditWorkoutScreen.kt` delegates save cleanly without calling `HealthConnectManager.syncWorkoutAsync` directly in `onSave`, and `ExportManager.exportWorkout` honors `syncToHealthConnect` (skips when false, forces when true, falls back to prefs when null).
- **Preconditions**:
  1. Test environment with Robolectric / MockK context initialized.
  2. Mocked `Context`, `ExportStatusDatabaseManager`, and `HealthConnectManager`.
- **Test Procedure**:
  1. *ViewModel State Verification*:
     - Load workout in `EditWorkoutViewModel`. Verify initial `syncToHealthConnect` reflects `TrainingApplication.syncToHealthConnect()`.
     - Call `viewModel.updateSyncToHealthConnect(false)`, verify `workoutData.value.syncToHealthConnect == false`.
     - Call `viewModel.updateSyncToHealthConnect(true)`, verify `workoutData.value.syncToHealthConnect == true`.
  2. *UI Layout Contract Verification*:
     - Verify `EditWorkoutScreen.kt` no longer contains direct invocation of `HealthConnectManager.syncWorkoutAsync` in `onSave` or anywhere in `EditWorkoutDialog`.
     - Verify Health Connect row binds to `viewModel.updateSyncToHealthConnect`.
  3. *ExportManager Pipeline Verification*:
     - With `TrainingApplication.syncToHealthConnect() == true`:
       - Call `exportManager.exportWorkout(workoutData.copy(syncToHealthConnect = false))`. Verify `HealthConnectManager.syncWorkoutAsync` is NOT called.
       - Call `exportManager.exportWorkout(workoutData.copy(syncToHealthConnect = true))`. Verify `HealthConnectManager.syncWorkoutAsync` is called exactly once with `force = true`.
       - Call `exportManager.exportWorkout(workoutData.copy(syncToHealthConnect = null))`. Verify `HealthConnectManager.syncWorkoutAsync` is called once with `force = false`.
     - With `TrainingApplication.syncToHealthConnect() == false`:
       - Call `exportManager.exportWorkout(workoutData.copy(syncToHealthConnect = true))`. Verify `HealthConnectManager.syncWorkoutAsync` is called with `force = true`.
       - Call `exportManager.exportWorkout(workoutData.copy(syncToHealthConnect = null))`. Verify `HealthConnectManager.syncWorkoutAsync` is NOT called.
- **Expected Results**:
  1. `syncToHealthConnect` state transitions correctly in `EditWorkoutViewModel`.
  2. `EditWorkoutScreen.kt` delegates save without redundant duplicate triggers.
  3. `ExportManager` honors per-workout sync selection and preserves fallback when null.

### TST-REV-016: **Offscreen Snapshot Header Click-Through, Semantics Isolation & Frame-Synchronized Capture Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-9168`
- **Requirement(s)**: `REQ-REV-015` (`REQ-REV-015.1`..`REQ-REV-015.2`)
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutOffscreenHeaderUiTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutSnapshotContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutElevationVisibilityUiTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutCollapsingHeaderContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/WorkoutShareBottomSheetContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailHeaderClickContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContentTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/DeadCodeEliminationContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/UiReflectionOnlyTestAuditContractTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Verification Result**: PASS (`77/77` PASS across 12 UI/contract suites on `408d9fb0`, `0` failures, `0` errors, `0` skipped, `0` compiler warnings on `MapDetailLayout.kt` & `MapDetailLayoutOffscreenHeaderUiTest.kt`, `4/4` Python safety linters PASS; verified at Gate 4 Bounce 1 `ATT-9314` with zero `app/` diff post-`408d9fb0` per `Rule 7.5`; `Localization Parity: N/A - no new or modified UI strings`)
- **Verification Evidence**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutOffscreenHeaderUiTest.kt`: `MapDetailLayoutOffscreenHeaderUiTest#idleShowHeaderFalse_allowsTempoLayersClickAndSuppressesDuplicateHeaderSemantics_tstRev016_1`, `MapDetailLayoutOffscreenHeaderUiTest#idleShowHeaderFalse_allowsSichtbareDiagrammeClick_tstRev016_1` (`TST-REV-016.1`); `MapDetailLayoutOffscreenHeaderUiTest#onDemandSnapshotTrigger_recordsFullWidthHeaderInsideClippedContainerWithoutSemanticsLeak_tstRev016_2`, `MapDetailLayoutOffscreenHeaderUiTest#activeSnapshotWithTransparentForeground_blocksPointerClicksOnOffscreenHeader_tstRev016_2` (`TST-REV-016.2`, `4/4` PASS on `408d9fb0`)
  - Regression & contracts: `MapDetailLayoutSnapshotContractTest` (`5/5`), `MapDetailLayoutElevationVisibilityUiTest` (`1/1`), `MapDetailLayoutCollapsingHeaderContractTest` (`8/8`), `MapDetailLayoutTest` (`12/12`), `WorkoutShareBottomSheetContractTest` (`12/12`), `WorkoutDetailHeaderClickContractTest` (`1/1`), `WorkoutDetailTabbedContentTest` (`7/7`), `ArchitectureConformanceContractTest` (`11/11`, `MapDetailLayout.kt = 1,096 / 1,500` LoC), `DeadCodeEliminationContractTest` (`6/6`), `SourceTextReadingAuditContractTest` (`6/6`, `0` `src/main` reads), `UiReflectionOnlyTestAuditContractTest` (`4/4`)
- **Procedure**:
  1. *Idle Click-Through & Zero Duplicate Accessibility Semantics (`TST-REV-016.1`, `MapDetailLayoutOffscreenHeaderUiTest.kt`, `androidx.compose.ui.test.junit4.v2.createComposeRule`)*:
     • Render `MapDetailLayout(showHeader = false, showMap = false, showTelemetryCharts = true, ...)` with a clickable `snapshotHeader` (simulating `WorkoutHeader`'s clickable route-cluster chip) and top-viewport `Sichtbare Diagramme` + `Tempo` multi-source layers controls.
     • Assert the `snapshotHeader` node does NOT exist in composition/semantics (`assertDoesNotExist()`) while idle, and tapping both `Sichtbare Diagramme` and `Tempo` layers controls opens their menus reliably (`0` deprecation warnings, `0` `src/main` file reads).
  2. *On-Demand Non-Interactive Capture & Frame-Synchronized `headerLayer` Recording (`TST-REV-016.2`, `MapDetailLayoutOffscreenHeaderUiTest.kt`, `MapDetailLayoutSnapshotContractTest.kt`, Contracts)*:
     • Trigger `externalSnapshotTrigger = true` (or `isSharing = true`) on `MapDetailLayout(showHeader = false, ...)` inside both a `.clipToBounds()` container and a transparent-foreground root (`showMap = false, showElevationProfile = false, showTelemetryCharts = false`), invoke `onRoot().captureToImage()` to execute the native Compose/Robolectric draw pass (`headerDrawCount > 0`), and assert `measuredHeaderSize > 0` even at off-viewport placement `placeable.place(0, -placeable.height - 10_000)` while tapping top-viewport coordinates over transparent foreground leaves `headerClicks == 0` and zero duplicate `WorkoutHeader` accessibility semantics leak (`.clearAndSetSemantics {}`).
     • Execute `MapDetailLayoutSnapshotContractTest`, `ArchitectureConformanceContractTest` (`MapDetailLayout.kt <= 1,500` LoC), and `SourceTextReadingAuditContractTest` (`0` new `src/main` reads; `Localization Parity: N/A - no new or modified UI strings`).

### TST-REV-017: **Workout Details Map Floating Layers Gesture Isolation & Track Selection Behavioral Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-9067`
- **Requirement(s)**: `REQ-REV-016` (`REQ-REV-016.1`..`REQ-REV-016.2`)
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapLayersUiTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutOffscreenHeaderUiTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContentTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/DeadCodeEliminationContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/UiReflectionOnlyTestAuditContractTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Verification Result**: PASS (`103/103` PASS across 25 UI/contract/ViewModel suites on `1c1df08e`, `0` failures, `0` errors, `0` skipped, `0` compiler warnings on `MapDetailLayout.kt`, `TrackOnMapScreen.kt`, & `TrackOnMapLayersUiTest.kt`, `4/4` Python safety linters PASS; verified at Gate 4 `ATT-9334` with zero `app/` diff post-`1c1df08e` per `Rule 7.5`; `Localization Parity: N/A - no new or modified UI strings`)
- **Verification Evidence**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapLayersUiTest.kt`: `TrackOnMapLayersUiTest#tabbedMap_trackLayersButtonTapOpensMenuAndTogglesTrackWithoutLockingPager_tstRev017_1`, `TrackOnMapLayersUiTest#mapDetailLayout_isolatesFloatingControlsFromCanvasTouchCallback_tstRev017_1` (`TST-REV-017.1`); `TrackOnMapLayersUiTest#nonTabbedFallback_trackLayersButtonOpensMenuAndTogglesTrack_tstRev017_2` (`TST-REV-017.2`, `3/3` PASS on `1c1df08e`)
  - Regression & contracts: `MapDetailLayoutOffscreenHeaderUiTest` (`4/4`), `MapDetailLayoutTest` (`12/12`), `MapDetailLayoutCollapsingHeaderContractTest` (`8/8`), `MapDetailLayoutSnapshotContractTest` (`5/5`), `MapDetailLayoutElevationVisibilityUiTest` (`1/1`), `WorkoutDetailTabbedContentTest` (`7/7`), `WorkoutDetailTabsTest` (`1/1`), `WorkoutSectionReorderContractTest` (`1/1`), `WorkoutShareBottomSheetContractTest` (`1/1`), `TracklessAftermathVisualContractTest` (`1/1`), `TelemetryMetricGraphMarginalBarsContractTest` (`1/1`), `TrackOnMap*` (`31/31` across 9 suites), `ArchitectureConformanceContractTest` (`11/11`, `MapDetailLayout.kt = 1,113/1,500`, `TrackOnMapScreen.kt = 1,013/1,500`), `DeadCodeEliminationContractTest` (`6/6`), `SourceTextReadingAuditContractTest` (`6/6`, `0` `src/main` reads), `UiReflectionOnlyTestAuditContractTest` (`4/4`)
- **Procedure**:
  1. *Tabbed Map Layers Click, Gesture Isolation & Menu Selection (`TST-REV-017.1`, `TrackOnMapLayersUiTest.kt`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)` `androidx.compose.ui.test.junit4.v2.createComposeRule`)*:
     • Render `WorkoutDetailTabbedContent` hosting `MapDetailLayout(showMap = true)` with `TrackLayersMenuButton` in `mapTopEndActions` (`showTechnicalTracks = true`, `availableTrackTypes = setOf(TrackType.BEST, TrackType.GPS, TrackType.ATT_FUSED)`).
     • Tap `track_layers_button` (including inner `< width - 32.dp` coordinates), assert `onInteractiveCanvasTouch(true)` is NOT emitted and `HorizontalPager(userScrollEnabled)` remains stable (while canvas touch outside `FLOATING CONTROLS` emits `onInteractiveCanvasTouch(true)`), assert `track_layers_menu` (`DropdownMenu`) opens displaying all `TrackType.entries` (`BEST`, `GPS`, `FUSED`, `ATT_FUSED`), and click `track_layers_item_GPS` / `track_layers_item_ATT_FUSED` asserting `onToggleTrackType` updates the selected track sources (`0` deprecation warnings, `0` `src/main` reads).
  2. *Non-Tabbed Fallback & Architecture/Governance Contracts (`TST-REV-017.2`, `TrackOnMapLayersUiTest.kt`, Contracts)*:
     • Render standalone `TrackLayersMenuButton` inside `MapDetailLayout(showMap = true, showHeader = true)` (`useStatusBarsPadding = true`); verify `track_layers_button` click opens `track_layers_menu` and toggles `TrackType`.
     • Execute `MapDetailLayoutTest`, `MapDetailLayoutOffscreenHeaderUiTest`, `WorkoutDetailTabbedContentTest`, `ArchitectureConformanceContractTest` (`MapDetailLayout.kt <= 1,500`, `TrackOnMapScreen.kt <= 1,500`), `SourceTextReadingAuditContractTest` (`0` `src/main` reads), and `UiReflectionOnlyTestAuditContractTest` (`Localization Parity: N/A - no new or modified UI strings`).

### TST-REV-018: **`WorkoutCluster` Encapsulation, Move/Delete Altitude Extrema & Bounds Verification Suite (`ATT-6047`)** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-6047`
- **Requirement(s)**: `REQ-REV-017` (`REQ-REV-017.1`..`REQ-REV-017.3`)
- **Component**: `WorkoutClusterDatabaseManager.kt`, `WorkoutClusterEngine.kt`, `WorkoutClusterApexTest.kt`, `EditWorkoutClusterTest.kt`, `ArchitectureConformanceContractTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Implemented Unit Tests**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterApexTest.kt`: `testWithAddedWorkout_encapsulatesCentroidsBoundsAndAltitudeExtrema`, `testWithRemovedWorkout_reversesCentroidsAndAltitudeExtremaWithHitCountGuard` (`TST-REV-018.1`); `testOnWorkoutDeleted_reversesAltitudeExtremaFromInMemorySnapshot` (`TST-REV-018.2`)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/EditWorkoutClusterTest.kt`: `testMoveWorkoutToCluster_updatesAltitudeExtremaAndRecalculatesBounds` (`TST-REV-018.2`)
- **Test Description**:
  1. *`WorkoutCluster.withAddedWorkout` & `withRemovedWorkout` Contract (`TST-REV-018.1`)*:
  • Verify `withAddedWorkout` updates running-mean `start`/`end`/`maxDisp`/`refDistance`, nullable `minAlt`/`maxAlt` LatLngs, and `minOf`/`maxOf` bounds while preserving `hitCount`.
  • Verify `withRemovedWorkout` reverses centroids and nullable `minAlt`/`maxAlt` LatLngs, respects `decrementHitCount`, and guards `hitCount <= 1`.
  2. *`moveWorkoutToCluster` & `onWorkoutDeleted` Extrema/Bounds Completeness (`TST-REV-018.2`)*:
  • Verify `moveWorkoutToCluster` updates source (`withRemovedWorkout`) & target (`withAddedWorkout` + expanded bounds + altitude extrema) before `assignClusterToWorkout`, then calls `recalculateClusterAnchors` on the source cluster to shrink bounds cleanly.
  • Verify `onWorkoutDeleted` reverses `minAltLat/Lng` & `maxAltLat/Lng` from nullable in-memory `w.minAltitudeLatLng`/`w.maxAltitudeLatLng` before `recalculateClusterAnchors` when `isAnchor` (and resets `hitCount = 0` when `hitCount <= 1`).
  3. *Targeted Cluster & Contract Regression (`TST-REV-018.3`)*:
  • Execute `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.database.WorkoutCluster*" --tests "com.atrainingtracker.trainingtracker.database.EditWorkoutClusterTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"`, confirming `WorkoutClusterEngine.kt` LoC ratchet compliance and `0` `.ui.*` imports.

### TST-REV-019: **Two-Tier Workout Classification UI, Epic Header Badge & Epic Filter Verification Suite** [Verified]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration, On-Device Manual
- **Requirement(s)**: `REQ-REV-018` (`REQ-REV-018.1`..`REQ-REV-018.4`)
- **Component**: `Color.kt`, `EditWorkoutScreen.kt`, `WorkoutHeaderData.kt`, `WorkoutData.kt`, `WorkoutHeader.kt`, `WorkoutFilterCriteria.kt`, `WorkoutFilterBottomSheet.kt`, `ActiveFilterChipsRow.kt`, `WorkoutFilterCriteriaEpicTest.kt`, `WorkoutClassificationAndEpicUiTest.kt`
- **Jira**: `ATT-9022` | **Target Release**: `V5.0.0`
- **Test Description**:
  1. *`WorkoutFilterCriteria` Epic Matching, Active Count & JSON Round-Trip (`TST-REV-019.1`, `WorkoutFilterCriteriaEpicTest.kt`)*:
  • Verify `isEpic = true`, `isEpic = false`, `isEpic = null`, combined `isRace = true && isEpic = true`, `activeFilterCount`, `toJson()`/`fromJson()` round-trip, and legacy JSON backward compatibility without `"isEpic"`.
  • Verify `WorkoutData.headerData` propagates `epic = true/false` to `WorkoutHeaderData.epic`, and `TTColor.resolveEpicBadgeColors(isDark)` returns distinct WCAG-compliant Light vs Dark palettes.
  2. *Behavioral Compose UI Two-Tier Classification, Epic Header Badge & Filter Sheet/Chips (`TST-REV-019.2`, `WorkoutClassificationAndEpicUiTest.kt`)*:
  • Render `WorkoutClassificationSection` via Robolectric `createComposeRule()` verifying Routine Context (`Commute`, `Indoor`) mutual exclusion vs Special Distinction (`Race`, `Epic`) independent & simultaneous toggling (`Race + Epic`).
  • Render `WorkoutHeader` verifying prominent `Epic` badge alongside `Race` badge while `Commute`/`Indoor` stay secondary text, and omitting `Epic` badge when `epic = false`.
  • Render `WorkoutFilterBottomSheet` and `ActiveFilterChipsRow` verifying `Epic` chip selection, apply callback (`isEpic = true`), active chip display, and single-tap removal (`isEpic = null`).
  3. *Localization, Theme, Contract & Live Emulator Visual Inspection (`TST-REV-019.3`)*:
  • Run `TranslationParityTest`, `ThemeColorCentralizationAuditTest`, `EditWorkoutClassificationLayoutContractTest`, `WorkoutHeaderRaceBadgeVisualContractTest`, `WorkoutFilterCriteriaRaceTest`, and `ArchitectureConformanceContractTest`, plus live `emulator-5554` Light & Dark screenshots inspected via `read_file` (`Rule 8.5`).

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-17 | AI Agent (`ATT-8164`) | `ATT-8164` | Added standardized `DOC-TST-REVIEWS` work-product control header and Change History. |
| `1.0.1` | 2026-11-20 | AI Agent (`ATT-9168`, `--as agent1`) | `ATT-9168` | Added `TST-REV-016` `[Planned]` (`TST-REV-016.1`–`.2`, strictly symmetric `REQ-REV-015`) using `v2.createComposeRule` for idle click-through, zero duplicate semantics, on-demand frame-synced `headerLayer` capture, and contract hygiene (`N/A - no new or modified UI strings`). |
| `1.0.2` | 2026-11-20 | AI Agent (`ATT-9168`, `--as agent1`) | `ATT-9168` | Promoted `TST-REV-016` to `[Implemented]` following Stage 4 test construction (`MapDetailLayoutOffscreenHeaderUiTest.kt`) and incremental Gradle verification. |
| `1.0.3` | 2026-11-20 | AI Agent (`ATT-9168`, `--as agent1`) | `ATT-9168` | Promoted `TST-REV-016` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9302`) `76/76 PASS` across 12 suites on `2251a7de` (`Rule 7.5`, zero `app/` diff post-`2251a7de`, `0` compiler warnings, `4/4` linters PASS), clean-checkout `--check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-9168_walkthrough.md`. |
| `1.0.4` | 2026-11-20 | AI Agent (`ATT-9168`, `--as agent1`) | `ATT-9168` | Stage 6 Bounce 1 (`Implemented`): added `activeSnapshotWithTransparentForeground_blocksPointerClicksOnOffscreenHeader_tstRev016_2` and `onRoot().captureToImage()` draw-pass verification (`headerDrawCount > 0`), re-verified `77/77 PASS` across 12 suites (`0` compiler warnings, `4/4` linters PASS). |
| `1.0.5` | 2026-11-20 | AI Agent (`ATT-9168`, `--as agent1`) | `ATT-9168` | Promoted `TST-REV-016` to `[Verified]` in Stage 5 Bounce 1 reusing Gate 4 Bounce 1 (`ATT-9314`) `77/77 PASS` across 12 suites on `408d9fb0` (`Rule 7.5`, zero `app/` diff post-`408d9fb0`, `0` compiler warnings, `4/4` linters PASS), clean-checkout `--check-verified-evidence` `exit 0`, and updated `docs/engineering/walkthroughs/ATT-9168_walkthrough.md`. |
| `1.0.6` | 2026-11-20 | AI Agent (`ATT-9067`, `--as agent1`) | `ATT-9067` | Added `TST-REV-017` `[Planned]` (`TST-REV-017.1`–`.2`, strictly symmetric `REQ-REV-016`) using `@GraphicsMode(NATIVE)` `v2.createComposeRule` for tabbed & fallback `TrackLayersMenuButton` click-through, `FLOATING CONTROLS` `.zIndex(1f)` & `onInteractiveCanvasTouch(false)` gesture isolation, `DropdownMenu` track selection, and contract hygiene (`N/A - no new or modified UI strings`). |
| `1.0.7` | 2026-11-20 | AI Agent (`ATT-9067`, `--as agent1`) | `ATT-9067` | Promoted `TST-REV-017` to `[Implemented]` following Stage 4 construction of `TrackOnMapLayersUiTest.kt`. |
| `1.0.8` | 2026-11-20 | AI Agent (`ATT-9067`, `--as agent1`) | `ATT-9067` | Promoted `TST-REV-017` to `[Verified]` in Rule 7.5 doc-only Stage 5 reusing Gate 4 (`ATT-9334`) `103/103 PASS` across 25 suites on `1c1df08e` (`0` `app/` diff post-`1c1df08e`, `0` compiler warnings, `4/4` linters PASS), `--check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-9067_walkthrough.md`. |
| `1.0.9` | 2026-11-21 | Lane B Flash (`ATT-6047`, `--as agent1`) | `ATT-6047` | Added `[Planned]` `TST-REV-018` verification suite for `REQ-REV-017` (`WorkoutCluster` encapsulation, move/delete altitude extrema & bounds, and contract checks). |
| `1.0.10` | 2026-11-21 | Lane B Flash (`ATT-6047`, `--as agent1`) | `ATT-6047` | Promoted `TST-REV-018` to `[Implemented]` following Stage 4 unit test additions in `WorkoutClusterApexTest.kt` and `EditWorkoutClusterTest.kt`. |
| `1.0.11` | 2026-11-21 | Lane B Flash (`ATT-6047`, `--as agent1`) | `ATT-6047` | Promoted `TST-REV-018` to `[Verified]` in Rule 7.5 doc-only Stage 5 reusing Gate 4 (`ATT-9407`) `106/106` & Stage 4 `131/131` PASS (`+1/1` isolated `WorkoutClusterPreviewPersistenceTest`) + `4/4` linters PASS @ `e9af7578`, `--check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-6047_walkthrough.md`. |
| `1.0.12` | 2026-11-24 | Lane 2 Author (`ATT-9022`, `--as agent1`) | `ATT-9022` | Added and verified `TST-REV-019` (`TST-REV-019.1`..`TST-REV-019.3`): `WorkoutFilterCriteriaEpicTest` (`8/8`), `WorkoutClassificationAndEpicUiTest` (`4/4` Robolectric Compose), plus `WorkoutFilterCriteriaRaceTest`, `ActiveFilterChipsRowTest`, `EditWorkoutClassificationLayoutContractTest`, `EditWorkoutViewModelTest`, `WorkoutHeaderRaceBadgeVisualContractTest`, `TranslationParityTest`, `ThemeColorCentralizationAuditTest`, and `ArchitectureConformanceContractTest` (`72/72 PASS`, `0` new warnings), plus 6 Light/Dark `emulator-5554` screenshots inspected via `read_file` (`Rule 8.5`). |
