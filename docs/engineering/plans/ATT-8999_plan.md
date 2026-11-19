# Implementation Plan: ATT-8999 - Stop 1-Hz Heap/OOM Cascade on WorkoutRepository._allWorkouts During Live Tracking

**Ticket**: `ATT-8999` (`3 SP`, `Bug`, `high-risk`, `severity-critical`) | **Parent Epic**: `ATT-235` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-18_03_Nacht` | **Branch**: `feature/ATT-8999` (base `7094bc8b`) | **Requirements**: `REQ-STB-020.1`–`.5` | **Tests**: `TST-STB-020.1`–`.4`

## 1. Problem Description & SWE.2/SWE.3 Architectural Boundaries
* **Root Cause (3-Stage 1-Hz Cascade)**: With `2,196` workouts in `WorkoutRepository._allWorkouts`, live tracking caused a `512 MB` `OutOfMemoryError`: (1) `TrackerService.sampleAndWriteToDb` (`:2588`) called `updateExtremaValueInMemory` (`:2687, :2693, :2702, :2789` -> `6–12x/s` list clones) and broadcast `WORKOUT_UPDATED_INTENT` at 1 Hz in active (`:2855`) and paused (`:2620`) states; (2) `WorkoutRepository.workoutUpdateReceiver` (`:1882-1895`) ran `reloadWorkoutData` -> `WorkoutDataMapper.fromCursor` (`:79-315`: 40+ queries + `PolyUtil.decode` `:102, :1152` + `decodeDoubles` `:109, :1210`); (3) `PeriodsRepository` (`:179-187`), `AthleteProfileViewModel` (`:395-405`), and `KnownLocationsViewModel` (`:161-189`) rebuilt aggregates/SQLite/geofence state at 1 Hz.
* **SWE.2 / SWE.3 Alignment (`REQ-PRO-114`, `Rule 8.8`)**: Updated `docs/architecture.md` (`v1.0.7` §2 & §3.5) and `docs/architecture/detailed_design/tracker_service.md` (`v1.0.1`) to register `LiveWorkoutBroadcastTracker` (`<= 3,559`-LoC ceiling on `TrackerService.java`) and document discrete-edge `WORKOUT_UPDATED_INTENT` transitions; zero `IF-SWE-*` signature changes (`docs/architecture/interfaces.md` untouched).
* **Parallel-Lane Isolation & UI Scope**: `WorkoutRepository.kt` (`0` edits; `:1970-2650` untouched for `ATT-8916`), `req_aftermath_periods.md`, `tst_aftermath_periods.md`, `workout_repository.md`, `LegacyImportEngine.kt` (`ATT-9018`), and `ANT-Android-SDKs` stay untouched. `UI Consistency: N/A` (`Localization Parity: N/A - no UI strings`).

## 2. Traceability & Requirements Mapping
| Requirement | Architectural Action | Test ID | Target Verification Suites |
| :--- | :--- | :--- | :--- |
| `REQ-STB-020.1` | Remove 1-Hz `updateExtremaValueInMemory` & steady-state active/paused broadcasts | `TST-STB-020.1` | `TrackerServiceStateMachineCharacterisationTest` |
| `REQ-STB-020.2` | Extract `LiveWorkoutBroadcastTracker` Kotlin helper; emit on discrete edges (`<= 3,559` LoC) | `TST-STB-020.1`, `.4` | `TrackerServiceStateMachineCharacterisationTest`, `ArchitectureConformanceContractTest` |
| `REQ-STB-020.3` | Filter `list.filter { it.finished }` in `PeriodsRepository` & `precalculateGroups` | `TST-STB-020.2` | `PeriodsRepositorySyncTest` |
| `REQ-STB-020.4` | Filter & dedup `!it.finished` in `AthleteProfileVM` & `KnownLocationsVM`; keep `!finished` cards | `TST-STB-020.3` | `AthleteProfileViewModelCardioPowerTest`, `KnownLocationsViewModelTest` |
| `REQ-STB-020.5` | Enforce 3,600-tick churn bound, `ATT-8916` isolation, contract & `0` `src/main` test-read checks | `TST-STB-020.4` | Contract suites, NFR churn test, `verify_requirement_governance.py` |

## 3. System Invariants & Preserved Behavior
* **Tracking & Persistence**: Keep 1-Hz `recordLiveSample` (`:2835`), paused `updateWorkoutTotalTime` (`:2614`, `REQ-TRK-009`), altitude broadcast (`:851`), terminal `TRACKING_FINISHED_INTENT` (`:2551`, `REQ-TRK-010`) + `onWorkoutFinished` (`WorkoutRepository.kt:4487`), live telemetry via `BANALServiceRepository`, and all 27 `DetailedDesignParityContractTest` `TrackerService` members.
* **Projections & `Rule 68` Consumers**: Keep `PeriodProjectionKey.finished` in `toPeriodProjectionKey()` (`PeriodsRepository.kt:1195-1211`, `PeriodsRepositorySyncTest.kt:282`) and `emptyMap()` path deferral (`REQ-PER-039.1`). Keep `WorkoutSummariesViewModel` (`:85, :164-189`, `WhileSubscribed(5000)`) updating active `!finished` cards (`WorkoutSummary.kt:122, 227`) on discrete edges; preserve `PeriodsViewModel.kt:412-421` (`ATrainingTrackerApp.kt:382`) & `WorkoutClustersScreen.kt:96`.
* **Governance & Hygiene**: Zero `WorkoutRepository.kt` edits (`0` conflict with `ATT-8916`), `0` `src/main/` test reads (`REQ-PRO-049`), and `TrackerService.java <= 3,559` LoC (`ArchitectureConformanceContractTest`).

## 4. Proposed Architectural Changes
* **`tracker/LiveWorkoutBroadcastTracker.kt` (new pure/Android Kotlin helper)**: Holds `lastPausedState: Boolean?`, `reset(initialPaused: Boolean? = null)`, `onPauseStateObserved(isPaused: Boolean): Boolean` (edge-only `TRACKING <-> PAUSED` detection), and discrete-edge broadcast dispatcher (`@JvmOverloads` callback for deterministic JVM tests, defaulting to `LocalBroadcastManager` `WORKOUT_UPDATED_INTENT`).
* **`tracker/TrackerService.java`**: Delete 1-Hz `updateExtremaValueInMemory` (`:2687, :2693, :2702, :2789`) & 1-Hz active/paused `WORKOUT_UPDATED_INTENT` (`:2855`, `:2620`); delegate discrete edges (`START_NORMAL`/resume init `:1292, :1915, :1941`, `TRACKING <-> PAUSED` edge, `saveLap` outside `endWorkout` `:776` vs `:2379`, sport changes `:2304, :2334`, while keeping `:851` & `:2551`) to `LiveWorkoutBroadcastTracker` (`<= 3,559` LoC).
* **`ui/aftermath/periodlist/PeriodsRepository.kt`**: Filter `list.filter { it.finished }` before `map { it.toPeriodProjectionKey() }` (`:179-187`) and require `it.startTimeS > 0L && it.finished` in `precalculateGroups` (`:955-963`), leaving `toPeriodProjectionKey()` unchanged.
* **`ui/athleteprofile/AthleteProfileViewModel.kt`**: Filter `it.finished` & `distinctUntilChanged` on finished workout keys before SQLite `loadCardioTrendSamples()` / `getWorkoutTrainingLoadRecords()` (`:395-405`).
* **`ui/knownlocations/KnownLocationsViewModel.kt`**: Filter `it.finished` & `distinctUntilChanged` on finished `(id, startLatLng, bSportType)` before `groupSportStartsByLocation` (`:127, :161-189`).

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
| Step | Atomic Action & Target Files | Trace | Targeted Verification Command (`./gradlew :app:testDebugUnitTest --tests ...`) |
| :--- | :--- | :--- | :--- |
| 1 | Create `tracker/LiveWorkoutBroadcastTracker.kt` + unit checks for pause-edge, reset & discrete-edge dedup | `REQ-STB-020.2` / `TST-STB-020.1` | `--tests "*TrackerServiceStateMachineCharacterisationTest"` |
| 2 | Wire `LiveWorkoutBroadcastTracker` in `TrackerService.java`; drop 1-Hz extrema clones & active/paused broadcasts (`:2620, :2687-2789, :2855`); wire discrete edges; update paused assertions (`:329-365`) | `REQ-STB-020.1`–`.2` / `TST-STB-020.1` | `--tests "*TrackerServiceStateMachineCharacterisationTest" --tests "*TrackerService*Test"` |
| 3 | Add `it.finished` filters in `PeriodsRepository.kt:181, :956` keeping `PeriodProjectionKey.finished`; add `!finished` zero-rebuild test | `REQ-STB-020.3` / `TST-STB-020.2` | `--tests "*PeriodsRepositorySyncTest" --tests "*PeriodsRepositoryMemoryTest"` |
| 4 | Add `it.finished` + key dedup in `AthleteProfileViewModel.kt:396` & `KnownLocationsViewModel.kt:127, :163`; update mocks (`KnownLocationsViewModelTest.kt:228-246, :256-305`, `AthleteProfileViewModelCardioPowerTest.kt:265` with `finished = true` + distinct `id`) + add `!finished` no-op tests | `REQ-STB-020.4` / `TST-STB-020.3` | `--tests "*KnownLocationsViewModel*Test" --tests "*AthleteProfileViewModel*Test" --tests "*WorkoutSummariesViewModel*Test"` |
| 5 | Add 3,600-tick / 2,200-workout churn test (`0` clones/reloads/rebuilds); run Tier 2 contract checks (`ArchitectureConformanceContractTest`, `DetailedDesignParityContractTest`, `InterfaceSignatureDriftContractTest`, `SourceTextReadingAuditContractTest`) | `REQ-STB-020.5` / `TST-STB-020.4` | `--tests "*ContractTest"` + targeted suites above |
| 6 | Stage 5 doc sync (`req_process_stability_part2.md` / `tst_process_stability_part2.md` `[Verified]`, `docs/requirements.md` / `docs/tests.md` `recalc-docs`, `ATT-8999_walkthrough.md`) | `REQ-PRO-114`, `.131` | `python3 tools/verify_requirement_governance.py --check-specs --check-doc-headers --check-living-doc-layout --check-conflict-markers --enforce-links --enforce-unique --enforce-attributes` |

## 6. Verification & Rollback Plan
* **Verification Strategy**: Execute Steps 1–5 suites cleanly on JVM + full `:app:testDebugUnitTest` affected-package runs in Stage 4/5, verify `TrackerService.java <= 3,559` LoC and `0` `WorkoutRepository.kt` / `ATT-8916` diff overlaps, and confirm `verify_requirement_governance.py` passes (`exit 0`).
* **Rollback Plan**: Zero schema or persistent format changes (`WorkoutSamples.db`/`WorkoutSummaries.db` untouched). Reverting the single Stage 5/6 feature commit restores legacy behavior cleanly.
