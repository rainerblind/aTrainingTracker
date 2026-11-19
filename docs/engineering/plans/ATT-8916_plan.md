# Stage 3: Implementation Plan - ATT-8916: Segmented Activity & Phone Carriage Mode Timeline Bar

**Ticket**: [ATT-8916](https://rainerblind.atlassian.net/browse/ATT-8916) (`2 SP`, Feature) | **Sub-task**: `[Impl-Plan]` (Pending `submit-stage`)
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*) | **Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-18_02_Spät`
**Requirement / Test Mapping**: `REQ-PER-045` (`.1`–`.5`) / `TST-PER-053` (`.1`–`.5`)
**Branch**: `feature/ATT-8916` | **Author**: AI Agent 1 (`agent1`, Pro) | **Date**: 2026-11-18

## 1. Problem Description & Traceability
Workout Details lacks visualization of the 1-Hz `SensorType.ACTIVITY_AND_CARRIAGE_MODE` stream (`ATT-8890`, `REQ-FIL-028.2`). `REQ-PER-045.1`–`.5` / `TST-PER-053.1`–`.5` specify pre-decimation 1-Hz segment coalescing (`ActivityCarriageTimelineCalculator`), dual GPS/trackless repository & ViewModel propagation, conditional `DETAILS` rendering (`ActivityCarriageTimelineCard`), `TTColor` centralization, `9/9` localization, and behavioral `createComposeRule()` verification.

## 2. System Invariants & Architecture Boundaries (SWE.2)
1. **Strict LoC & Enum Invariants**: `WorkoutSamplesDatabaseManager.java == 1,533` LoC (`0` growth); `WorkoutRepository.kt <= 5,117` LoC; `WorkoutDetailTabConfig` `"v3|"` schema, `14`-entry `WorkoutDetailSectionType`/`WorkoutSectionType` enums, `WorkoutMasksAndCardsSection.kt < 400` LoC, and `WorkoutSummary.kt` remain untouched.
2. **Binary/Constructor Compatibility**: Trailing `= null` defaults on `WorkoutDetailTelemetry`, `AftermathMapUIState`, `TrackOnMapScreen`, and `WorkoutDetailTabbedContent` preserve existing callers/tests and `DetailedDesignParityContractTest` / `InterfaceSignatureDriftContractTest`.
3. **Code Lock & Workspace Hygiene**: Zero edits under `app/src/` before Gate 3 `PASS` (`check-gate`); 4 dirty `tools/` files remain untouched.

## 3. UI Consistency (`docs/design_guidelines.md`)
* **Reference & Tokens**: Match `RadarTrafficSummaryCard` / `CardioPowerDynamicsCard` (`ElevatedCard`, `RoundedCornerShape(12.dp)`, `16.dp` padding, `12.dp` spacing, `0.5.dp` `HorizontalDivider`) and `ZoneDistributionStackedBar` (`6.dp` radius bar, `0.5.dp` `outlineVariant` border, `1.dp` `surface` dividers).
* **Layout & Formatting (§5.4, §5.5, §5.20)**: Centralize segment colors in `TTColor` (`Color.kt`, WCAG `>= 3.0:1` across Light/Dark/AMOLED); use `Modifier.weight(1f)` on two-sided text headers; format via `LocalMetricFormatter` + `locales[0]`; no inline emojis (`ATT-5400`), no `FilterChip` mode switches; include Light/Dark/AMOLED `@Preview`s.

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pure-JVM Timeline Models & 1-Hz Coalescer (`REQ-PER-045.1`, `.2`)
* **Files**: New `ui/aftermath/ActivityCarriageTimeline.kt` (`ActivityCarriageSegment`, `ActivityCarriageModeSummary`, `ActivityCarriageTimeline`) & `ActivityCarriageTimelineCalculator.kt` (outside `WorkoutRepository.kt`).
* **Changes**: Coalesce `(timeSec, distanceMeters, rawCode)` rows (`NULL -> UNKNOWN(-1)`): clamp per-row `dt` to `1..5s` (`MAX_DELTA_T_SEC = 5L`, `1L` trailing/single), attribute forward `deltaDist >= 0.0`, absorb `<= 5s` `UNKNOWN` slivers (preceding else following active span, merging adjacent equal active modes) while preserving **all** `>= 1s` active (`0..7`) transitions, retain `> 5s` `UNKNOWN` spans, return `null` on missing column or zero active (`0..7`) rows, compute `durationPercentage` (`100%` sum) & `0f`-safe `distancePercentage` for `totalDistanceMeters == 0.0`.

### Step 2: Repository, ViewModel & 5-Host Wiring + SWE.2/SWE.3 Sync (`REQ-PER-045.3`)
* **Files**: `WorkoutRepository.kt`, `TrackOnMapAftermathViewModel.kt`, `TrackOnMapScreen.kt`, `WorkoutSummariesTabbedScreen.kt`, `WorkoutSummariesListFragment.kt`, `WorkoutClustersScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, `PeriodMapScreen.kt`, `docs/architecture/detailed_design/workout_repository.md`, `docs/architecture.md`.
* **Changes**: In `WorkoutRepository.loadWorkoutDetailTelemetry(...)`, check `hasCarriageMode`, include it in the `L2472` early-exit guard, project `ACTIVITY_AND_CARRIAGE_MODE` on the full-resolution 1-Hz cursor pre-decimation via `ActivityCarriageTimelineCalculator.Accumulator`, and expose trailing `activityCarriageTimeline: ActivityCarriageTimeline? = null` on `WorkoutDetailTelemetry`. Add trailing `= null` `activityCarriageTimeline` on `AftermathMapUIState`, reset to `null` on `!isSameWorkout` (`L133-146`), propagate in **both** GPS (`L253-257`) and trackless `else` (`L259-271`) branches, and forward through `TrackOnMapScreen` across all 5 callers. Sync `workout_repository.md` & `docs/architecture.md`.

### Step 3: Compose Timeline Ribbon, Phase Breakdown UI & 9-Locale Strings (`REQ-PER-045.4`, `.5`)
* **Files**: `Color.kt`, `ActivityCarriageTimelineCard.kt` (new), `WorkoutDetailTabbedContent.kt`, `app/src/main/res/values*/strings_workout_details.xml` (9 locales).
* **Changes**: Add `TTColor` carriage mode tokens + resolver in `Color.kt`. Build `ActivityCarriageTimelineCard` with duration-weighted (`> 0f`-guarded) proportional timeline bar, per-mode summary chips/rows reusing `PhoneCarriageMode.labelResId` (`9/9` `strings_devices.xml`), expandable chronological phase list (`KeyboardArrowUp`/`Down`), and Light/Dark/AMOLED `@Preview`s. Mount inside `WorkoutDetailTabbedContent` (`WorkoutDetailSectionType.DETAILS`, after `RadarTrafficSummaryCard`) strictly when `activityCarriageTimeline?.segments?.isNotEmpty() == true`. Add 9-locale `strings_workout_details.xml` keys (`%1$d`, `%1$s`).

### Step 4: Behavioral Tests, `Rule 8.5` PNG & Governance (`TST-PER-053.1`–`.5`)
* **Test Files** (**0** `src/main` reads; `createComposeRule()` for UI): New `ActivityCarriageTimelineCalculatorTest.kt`, new `ActivityCarriageTimelineCardTest.kt` (`createComposeRule()` + `docs/engineering/walkthroughs/img/ATT-8916_carriage_timeline.png`), plus new tests in `WorkoutRepositoryTelemetryTest.kt` (`> 800`-row GPS/trackless + missing-column/all-`UNKNOWN`), and `TrackOnMapAftermathViewModelTest.kt` (`!isSameWorkout` reset + dual GPS/trackless propagation).
* **Verification**: `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.ActivityCarriageTimelineCalculatorTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.ActivityCarriageTimelineCardTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryTelemetryTest" --tests "com.atrainingtracker.trainingtracker.ui.map.TrackOnMapAftermathViewModelTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabsTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.UiReflectionOnlyTestAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.DetailedDesignParityContractTest"`, `read_file` PNG critique (`Rule 8.5`), and `verify_requirement_governance.py`.

## 5. Rollback Plan
* Isolated to `feature/ATT-8916` prior to Stage 6 squash-merge (`Rule 8.2`); revert cleanly without affecting `sprint/2026-11-18_02_Spät`.
