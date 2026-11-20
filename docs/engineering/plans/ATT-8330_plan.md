# Stage 3 Implementation Plan: ATT-8330 — UI Design System 66-TST Verification Plan

**Ticket**: [ATT-8330](https://rainerblind.atlassian.net/browse/ATT-8330) (`3 SP`, Low) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`  
**Scope**: Executable Verification for `47` Automatable TSTs (`8` Bucket A + `12` Bucket B1 + `27` Bucket B2) + `1` Preserved `[Verified]` (`TST-UI-055`) + `18` Bucket C Retained `[Implemented]` (`66/66`)  
**Branch**: `feature/ATT-8330` | **Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-20  

---

## 1. SWE.2 Architecture, Guardrails & Scope
- **Layering & Test Isolation**: Pure test additions/tags under `app/src/test/` exercising domain filters/mappers (`banalservice/filters`, `WorkoutDataMapper`, `MappablePath`), SQLite managers (`SportTypeDatabaseManager`, `TrackingViewsDatabaseManager`, `WorkoutSummariesDatabaseManager`), ViewModels (`WorkoutSummariesViewModel`, `TrackOnMapAftermathViewModel`, `LiveWorkoutSession`), and Jetpack Compose UI (`createComposeRule()` with `LocalInspectionMode` and explicit test state). Strictly `0` `app/src/main/` edits.
- **Hard Guardrails**:
  1. **`0` Production Edits**: Do not modify `app/src/main/`. If a test reveals a genuine pre-existing defect, file a Backlog Bug via `jira_util.py --as agent1 create-issue` (`Rule 5`) and keep that TST at `[Implemented]`.
  2. **`REQ-PRO-133` (`source-reading-baseline == 105`)**: `0` edits to `app/source-reading-baseline.txt`; `0` new `app/src/main/` file reads in any created or modified test (`ThemeColorCentralizationAuditTest` & `DeadCodeEliminationContractTest` remain within existing exemptions).
  3. **`REQ-PRO-132` (`ui-reflection-baseline == 189`)**: `0` edits to `app/ui-reflection-baseline.txt`; `0` reflection-only `Class.forName("...Kt")` stubs; all `27` Bucket B2 tests execute real Compose UI via `createComposeRule()`.
  4. **Anti-Fabrication (`K1`/`M1`) & Multi-TST Guards**: `18` Bucket C TSTs (`TST-UI-001`, `016..018`, `026`, `045`, `047`, `054`, `057`, `058`, `062`, `063`, `067`, `069`, `071..073`, `076`) stay `[Implemented]`; keep file lengths well under `1,500` LoC; `REQ-TRK-004` (`TST-MAN-001`), `REQ-UI-011`, `REQ-UI-101`, `REQ-UI-104` stay `[Implemented]` in Stage 5.
- **UI Consistency & `SYS.2`/`SYS.3` (`REQ-PRO-131.4`)**: `N/A - no app/src/main/ UI, architecture, or system-interface edits`. `Localization Parity: N/A - no UI strings`.

---

## 2. Atomic Stage 4 Implementation Steps (`<= 1,500` LoC / Test File)

| Step | Target File(s) (`app/src/test/`) | Atomic Action | Covers TSTs |
| :--- | :--- | :--- | :--- |
| **0** | Jira Gate 3 Lock | Run `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/jira_util.py --as agent1 check-gate <IMPL_PLAN_SUBTASK_KEY>` (`exit 0`, `Rule 4`) before any `app/` edit (`0` edits to `app/` in Stage 3). | Gate 3 Lock |
| **1** | **Bucket A (`8` existing suites)** | Add explicit hyphenated `// TST-...` tags + focused assertions without altering baseline audit scope: `FilterNullSafetyTest.kt` (`TST-UNT-004` exact numbered/timed sequence avg); `FilterInitializationAndBoundsTest.kt` (`TST-UNT-005` `α·m + (1-α)·y`); `TrackingViewsRealSqliteMigrationTest.kt` (`TST-UNT-011` `v9->v10`/`onCreate` `ShowElevationProfile`); `DatabaseMigrationIndexTest.kt` (`TST-UNT-014` no manual `setTransactionSuccessful`); `InteractivePeriodMapTest.kt` (`TST-UI-041` peak markers + peek); `ElevationProfileOverlayTest.kt` (`TST-UI-051` grade legend `createComposeRule()` toggle); `ThemeColorCentralizationAuditTest.kt` (`TST-UI-061` zero `Color(0x)` outside `Color.kt`); `DeadCodeEliminationContractTest.kt` (`TST-UI-108` assert 3 retired fragments gone & exact `9` active layouts in `res/layout/`). | `TST-UNT-004`, `005`, `011`, `014`, `TST-UI-041`, `051`, `061`, `108` (`8`) |
| **2** | **Bucket B1 (`12` Unit / DB / VM / Resource)** | Implement cohesive deterministic unit/SQLite/VM suites (`0` `src/main` reads): `EquipmentDiscoveryTest.kt` (replace mock tautology with real `EquipmentAndSportTypeDiscoveryManager.resolveIdentity` via Robolectric/mock DB — `TST-UNT-003`); `SportTypeDatabaseManagerStravaTest.kt` (Robolectric SQLite persistent `null` Strava state — `TST-UNT-006`); `WorkoutExtremaBigSixMapperTest.kt` (`WorkoutDataMapper` "Big 6" extrema + `100m/100s=1.0 m/s` speed harmonization — `TST-UNT-009`, `TST-UI-027`); `MappablePathMemoizationTest.kt` (`latLngs ===` identity across `MapTrack`/`MapSegment`/`MapRoute`/`MapWorkoutCluster` — `TST-UNT-010`); `WorkoutSummariesViewModelFilterTest.kt` (`sportId` & `bSportType` period filtering — `TST-UNT-012`, `013`); `UiDesignSystemTerminologyTest.kt` (EN/DE `Elevation`/`Höhenmeter` vs `Altitude`/`Höhe` + Drawer `Display`/`Search`/`Suche` — `TST-UI-028`, `075`); `LiveWorkoutSessionTest.kt` (`start -> stop -> new` reset isolation — `TST-UI-029`); `BANALServiceRepositoryPauseIsolationTest.kt` (`isPaused=true` excludes track/profile movement — `TST-UI-039`); `TrackOnMapAftermathViewModelTest.kt` (technical track prefs persist across VM recreation — `TST-UI-056`). | `TST-UNT-003`, `006`, `009`, `010`, `012`, `013`, `TST-UI-027`, `028`, `029`, `039`, `056`, `075` (`12`) |
| **3** | **Bucket B2 (`27` Robolectric `createComposeRule()` UI)** | Construct cohesive Compose UI suites (`0` `src/main` reads, `0` `Class.forName("...Kt")` stubs): `WorkoutExtremaUiTest.kt` (`[unit]` brackets `011`, label/value `16.sp` scale `012`, `boundsInRoot.bottom` alignment `013`, micro unit `fontSize`+alpha `014`, `"Ø"` header `015`, context-aware `FontWeight.Bold` `019`, `"-"` `TTAlpha.Disabled` `020`, `TTAlpha` alpha hierarchy `059`); `WorkoutDetailsAndMetricUiTest.kt` (Ascent/Descent w/o Min/Max `021`, Workout/Segment vertical row consistency `030`, Elevation text hierarchy `031`, primary color highlighting on `WorkoutDetails`/`MetricItem` `059`, `060`); `RouteAndSegmentCardsUiTest.kt` (`RouteSummaryHeader` Strava vs Local `034`, compact gap `035`, `SegmentList`/`SegmentDetails` branding cap `036`); `PeriodSummaryCardUiTest.kt` (sub-sport & longest highlight `038`, `onLongestWorkoutClick` `053`, weekly `"May 11 - May 17"` date subtitle `074`); `UnifiedCardSpacingUiTest.kt` (`WorkoutSummary`/`RouteItem`/`ClusterItem` `12.dp` horizontal padding `077`); `DeviceManagementUiTest.kt` (`DevicesTabbedScreen`+`EditDeviceDialog` `043`, `DeviceItem` LED left of metadata `044`, 2/3-row identity & `18.dp` LED/battery parity across `DeviceItem`/`EditDeviceDialog`/`SensorSourceDialog` `050`); `StatsAndSensorStatusUiTest.kt` (`StatsSummaryBlock` alignment `046`, `SensorStatus` icon-only `~22.dp` row `048`, sensor icon tap opens `SensorSourceDialog` `049`, identity check `050`); `MapDetailLayoutElevationVisibilityUiTest.kt` (`showElevationProfile=false` omits `ElevationProfile` under `LocalInspectionMode` `066`); `DeleteOldWorkoutsUiTest.kt` (`WorkoutListActions` delete-sweep + `DeleteOldWorkoutsDialog` `"365"` `080`). | `TST-UI-011..015`, `019..021`, `030`, `031`, `034..036`, `038`, `043`, `044`, `046`, `048..050`, `053`, `059`, `060`, `066`, `074`, `077`, `080` (`27`) |
| **4** | **Locked Gradle Verification & Stage 5 Sync** | Execute locked incremental `--rerun --tests ...` run (NEVER `--rerun-tasks`, NEVER `-PstampGitHash=true`) covering all `ATT-8330` suites + `TelemetryChartStackVisibilityTest` (`TST-UI-055`) + 4 build audit contracts, then verify `extract_test_code_tags()` & `--check-verified-evidence`. | All `47` (`+1` `055`, `+18` `[Impl]`) |

---

## 3. Exact Locked Verification Command (`Rule 7.5`)
```bash
python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --rerun \
  --tests "com.atrainingtracker.banalservice.filters.FilterNullSafetyTest" \
  --tests "com.atrainingtracker.banalservice.filters.FilterInitializationAndBoundsTest" \
  --tests "com.atrainingtracker.banalservice.database.SportTypeDatabaseManagerStravaTest" \
  --tests "com.atrainingtracker.banalservice.ui.devices.DeviceManagementUiTest" \
  --tests "com.atrainingtracker.trainingtracker.database.TrackingViewsRealSqliteMigrationTest" \
  --tests "com.atrainingtracker.trainingtracker.database.DatabaseMigrationIndexTest" \
  --tests "com.atrainingtracker.trainingtracker.database.EquipmentDiscoveryTest" \
  --tests "com.atrainingtracker.trainingtracker.tracker.LiveWorkoutSessionTest" \
  --tests "com.atrainingtracker.trainingtracker.repositories.BANALServiceRepositoryPauseIsolationTest" \
  --tests "com.atrainingtracker.trainingtracker.localization.UiDesignSystemTerminologyTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutExtremaBigSixMapperTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.InteractivePeriodMapTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodSummaryCardUiTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesViewModelFilterTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.DeleteOldWorkoutsUiTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileOverlayTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.map.MappablePathMemoizationTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.map.TrackOnMapAftermathViewModelTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutElevationVisibilityUiTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryChartStackVisibilityTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.components.workoutextrema.WorkoutExtremaUiTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsAndMetricUiTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.components.UnifiedCardSpacingUiTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteAndSegmentCardsUiTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.StatsAndSensorStatusUiTest" \
  --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest" \
  --tests "com.atrainingtracker.trainingtracker.build.UiReflectionOnlyTestAuditContractTest" \
  --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" \
  --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest"
```

---

## 4. Invariants & Rollback
- **Invariants**: `0` edits to `app/src/main/`, `app/source-reading-baseline.txt` (`105`), `app/ui-reflection-baseline.txt` (`189`), and the `5` user-owned paths; rollback is isolated solely to `app/src/test/` and living docs on `feature/ATT-8330`.
