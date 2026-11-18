# Stage 2: Requirement & Test Specification - ATT-8916: Segmented Activity & Phone Carriage Mode Timeline Bar

**Ticket**: [ATT-8916](https://rainerblind.atlassian.net/browse/ATT-8916) (`2 SP`, Feature) | **Sub-task**: `[Req & Test Spec]` (`ATT-9015`)
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*) | **Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-18_02_Spät`
**Requirement Mapping**: `REQ-PER-045` (`[Verified]`, `.1`–`.5`) | **Test Spec ID**: `TST-PER-053` (`[Verified]`, `.1`–`.5`)
**Branch**: `feature/ATT-8916` | **Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-18

## 1. Requirement Specification (`REQ-PER-045` [Verified])

### 1.1 Rationale & Attributes (`REQ-PRO-077` / `REQ-PRO-131.4`)
* **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
* **System Impact**: Derived (SW-only) — visualizes existing `SensorType.ACTIVITY_AND_CARRIAGE_MODE` 1-Hz telemetry (`ATT-8890`); zero `SYS.2`/`SYS.3` impact.
* **Rationale**: Athletes inspecting multi-modal tours (`ATT-8890`) need a segmented timeline ribbon and expandable phase breakdown in Workout Details while cleanly omitting legacy and all-`UNKNOWN` workouts.

### 1.2 Functional & Architectural Requirements (`SHALL` / `MUST`)
1. **Pre-Decimation Coalescing & Sliver/Pause/Zero-Distance Math (`REQ-PER-045.1`)**:
   * Pure-JVM `ActivityCarriageTimelineCalculator` SHALL coalesce 1-Hz `(timeSec, distanceMeters, rawCode)` rows (`NULL -> UNKNOWN(-1)`) into contiguous `ActivityCarriageSegment(mode, startTimeSec, endTimeSec, durationSec, distanceMeters)` spans + `ActivityCarriageModeSummary` totals.
   * Per-row duration `dt_i` SHALL clamp `(t_{i+1} - t_i).coerceIn(1L, 5L)` (`1L` for last/single row); `deltaDist_i = (d_{i+1} - d_i).coerceAtLeast(0.0)` when finite (`0.0` otherwise).
   * `UNKNOWN(-1)` slivers `<= 5s` (warmup/transient) SHALL absorb into an adjacent active span (preceding active span else following active span, coalescing newly adjacent equal active modes); genuine `1–5s` active transitions (`0..7`) SHALL NEVER be debounced away; `UNKNOWN` spans `> 5s` SHALL be retained.
   * Summary denominators: `totalDurationSec = segments.sumOf { it.durationSec }` (incl. `> 5s` `UNKNOWN` gaps) and `totalDistanceMeters = segments.sumOf { it.distanceMeters }`; `durationPercentage = (modeDuration / totalDuration) * 100f`; `distancePercentage = if (totalDistanceMeters > 0.0) (modeDistance / totalDistanceMeters).toFloat() * 100f else 0f`.
2. **Legacy & All-`UNKNOWN` Graceful Omission (`REQ-PER-045.2`)**: Absent `ACTIVITY_AND_CARRIAGE_MODE` column OR zero active (`0..7`) samples SHALL return `null` with zero SQL errors or empty card UI.
3. **Single-Pass Loading & Full Caller Propagation (`REQ-PER-045.3`)**:
   * `WorkoutRepository.loadWorkoutDetailTelemetry` SHALL guard `hasCarriageMode`, read raw 1-Hz rows pre-decimation, and expose trailing `activityCarriageTimeline: ActivityCarriageTimeline? = null` on `WorkoutDetailTelemetry`.
   * `AftermathMapUIState`, `TrackOnMapScreen`, and `WorkoutDetailTabbedContent` SHALL add trailing `= null` `activityCarriageTimeline`; `TrackOnMapAftermathViewModel.loadAftermathData` SHALL reset `activityCarriageTimeline = null` on `!isSameWorkout`, propagate it in **both** GPS (`L253-257`) and trackless `else` (`L259-271`) branches, and wire all 5 `TrackOnMapScreen` hosts.
   * `WorkoutSamplesDatabaseManager.java` MUST incur `0` LoC growth (`1,533`); `WorkoutRepository.kt` MUST stay `<= 5,117` LoC (updating `docs/architecture/detailed_design/workout_repository.md` in Stage 4 if signatures change).
4. **Segmented Timeline Ribbon & Phase Breakdown UI (`REQ-PER-045.4`)**:
   * `ActivityCarriageTimelineCard` SHALL render inside `WorkoutDetailSectionType.DETAILS` (after `RadarTrafficSummaryCard`) solely when `segments.isNotEmpty()`, preserving `"v3|"` schema, `14`-entry enums, `WorkoutMasksAndCardsSection.kt < 400` LoC, and `WorkoutSummary.kt`.
   * Timeline bar weights SHALL strictly use duration (`> 0f` guard) so `STATIONARY` stops and `totalDistance == 0.0` indoor workouts never trigger `Modifier.weight(<= 0f)`; summaries reuse `PhoneCarriageMode.labelResId` + expandable chronological phase list.
5. **Color Centralization, 9-Locale Parity & Behavioral UI Tests (`REQ-PER-045.5`)**: All colors centralized in `TTColor` (`Color.kt`); `9/9` `values*/strings_workout_details.xml` parity (zero emojis, positional `%1$d`/`%1$s`); `createComposeRule()` UI tests with `0` `src/main` reads (`Rule 8.5` Light/Dark PNG).

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-PER-045` consuming `ATT-8890` (`ACTIVITY_AND_CARRIAGE_MODE`), preserving `REQ-UI-2787.1` (`WorkoutDetailTabConfig` `"v3|"`) and `REQ-PRO-129.2` (`WorkoutSamplesDatabaseManager`/`WorkoutRepository` LoC ceilings).
* **Historical Origin & Commit Trace**: `fee3e805` (`ATT-8890` 1-Hz carriage classification), `58982719` (`ATT-8079` `"v3|"` persistence), `624bea5e` / `1b33fcba` (`RadarTrafficSummaryCard` / `CardioPowerDynamicsCard` in `DETAILS`).
* **Root Reason for Existing Formulation**: `"v3|"` (`14` sections) preserves user section removals; conditional cards inside `DETAILS` (`tab_overview`) surface sensor-conditional analytics without mutating tab schemas or breaching `WorkoutMasksAndCardsSection`'s `400`-LoC cap.
* **Preservation of Core Invariants**: `PhoneCarriageMode` `-1..7` & `9/9` `strings_devices.xml` unchanged; `"v3|"` & `14`-entry enums unchanged; `WorkoutSamplesDatabaseManager.java == 1,533` LoC; `WorkoutRepository.kt <= 5,117` LoC.

## 3. Acceptance Criteria (Given-When-Then)
* **AC-1 (Bakery Tour & Short Transitions)**: *Given* 1-Hz `UNKNOWN(2s) -> BIKE_FRONT_POCKET -> WALKING_TRANSITION(3s) -> STATIONARY -> WALKING_TRANSITION -> BIKE_FRONT_POCKET` (`> 800` rows), *When* opening Workout Details, *Then* warmup `UNKNOWN` absorbs, all 5 active phases survive pre-decimation coalescing, and proportional bar + summaries + expandable phases render.
* **AC-2 (Legacy & All-`UNKNOWN` Omission)**: *Given* a legacy table lacking `ACTIVITY_AND_CARRIAGE_MODE` or all `NULL`/`UNKNOWN(-1)` rows, *When* opening Workout Details, *Then* `activityCarriageTimeline == null` and no timeline card renders.
* **AC-3 (`> 5s` `UNKNOWN` & Zero-Distance Indoor/Trackless)**: *Given* a trackless `totalDistance == 0.0` workout or a `> 5s` `UNKNOWN` gap, *When* loaded and rendered, *Then* `durationPercentage` sums to `100%`, `distancePercentage == 0f`, trackless ViewModel forwarding succeeds, and `Modifier.weight` stays `> 0f`.

## 4. Test Specification (`TST-PER-053` [Verified]) & Traceability Matrix
| Test ID | Scope & Target File | Verification Summary | REQ |
| :--- | :--- | :--- | :--- |
| `TST-PER-053.1` | Unit (`ActivityCarriageTimelineCalculatorTest.kt`) | Bakery tour coalescing, `<= 5s` `UNKNOWN` absorb, `1–5s` active preservation, `> 5s` `UNKNOWN` gap denominator, `dt <= 5s` pause clamp, zero-distance `0f` safety | `REQ-PER-045.1`, `.2` |
| `TST-PER-053.2` | Repo (`WorkoutRepositoryTelemetryTest.kt`) | `> 800`-row pre-decimation GPS & trackless carriage extraction; legacy missing-column & all-`UNKNOWN` `null` return | `REQ-PER-045.2`, `.3` |
| `TST-PER-053.3` | ViewModel (`TrackOnMapAftermathViewModelTest.kt`) | Reset on `!isSameWorkout`, propagation in both GPS (`L253-257`) & trackless `else` (`L259-271`) branches | `REQ-PER-045.3` |
| `TST-PER-053.4` | Compose UI (`ActivityCarriageTimelineCardTest.kt`) | `createComposeRule()`, `0` `src/main` reads: ribbon + labels, zero-distance `weight > 0f`, expand/collapse phases, `null`/empty omission, `Rule 8.5` PNG | `REQ-PER-045.4`, `.5`, `REQ-PRO-132` |
| `TST-PER-053.5` | Parity/Contracts (`TranslationParityTest`, `ThemeColorCentralizationAuditTest`, `WorkoutDetailTabsTest`, `ArchitectureConformanceContractTest`) | `9/9` `strings_workout_details.xml` parity, `0` hex outside `Color.kt`, `"v3|"` / `14` sections intact, `WorkoutSamplesDatabaseManager == 1,533` & `WorkoutRepository <= 5,117` | `REQ-PER-045.3`–`.5`, `REQ-UI-106` |
