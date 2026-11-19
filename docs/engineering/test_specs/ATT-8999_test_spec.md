# Stage 2: Requirement & Test Specification - ATT-8999: Stop 1-Hz Heap/OOM Cascade on WorkoutRepository._allWorkouts During Live Tracking

**Ticket**: `ATT-8999` (`3 SP`, `Bug`, `high-risk`, `severity-critical`) | **Parent Epic**: `ATT-235` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-18_03_Nacht` | **Requirement Mapping**: `REQ-STB-020` | **Test Spec ID**: `TST-STB-020` | **Status**: `[Verified]`  
**Branch**: `feature/ATT-8999` | **Sub-task**: `[Req & Test Spec]` | **Author**: AI Agent 1 (`Pro`) | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-STB-020`)

### 1.1 Problem Statement & Rationale
During active workouts with `2,196` historical sessions, `TrackerService` drove a 3-stage 1-Hz heap churn cascade: (1) `6–12x/s` `_allWorkouts` list clones (`updateExtremaValueInMemory`), (2) 1-Hz active & paused `WORKOUT_UPDATED_INTENT` DB reloads running 40+ SQLite queries + `PolyUtil.decode` (`WorkoutDataMapper.fromCursor`), and (3) 1-Hz `PeriodsRepository` rebuilds (`precalculateGroups` + `enrich`) plus activity-scoped `AthleteProfileViewModel` SQLite queries and `KnownLocationsViewModel` $O(\text{locations} \times N)$ geofence scans, causing `512 MB` `OutOfMemoryError` (`REQ-NFR-005`).

### 1.2 Attributes
- **Priority**: Critical | **Type**: Non-Functional | **Verification Method**: Unit / Integration
- **`SYS.2` / `SYS.3` Impact (`REQ-PRO-131`)**: None (external sensors, BLE/ANT+, FGS/WakeLock, 1-Hz SQLite sample persistence, and cloud APIs unchanged; `STB` is `SWE_DERIVED_PREFIXES`).
- **9-Language Localization**: `Localization Parity: N/A - no UI strings`.

### 1.3 Functional & Architectural Requirements
1. **`REQ-STB-020.1` (Eliminate 1-Hz List Clones & Active/Paused Steady-State Broadcasts)**: `TrackerService.sampleAndWriteToDb()` (`:2588`) SHALL NOT call `updateExtremaValueInMemory` on 1-Hz ticks and MUST NOT dispatch `WORKOUT_UPDATED_INTENT` on steady-state active (`:2855`) or steady-state paused (`:2620`) ticks, while preserving 1-Hz asynchronous `recordLiveSample` (`:2835`) and paused `updateWorkoutTotalTime` (`:2614`, `REQ-TRK-009`). Live cockpit telemetry MUST remain strictly sourced via `BANALServiceRepository`.
2. **`REQ-STB-020.2` (Discrete Lifecycle Edge Broadcasts & `<= 3,559`-LoC Ceiling)**: `TrackerService` SHALL dispatch `WORKOUT_UPDATED_INTENT` via `LocalBroadcastManager` strictly on discrete lifecycle edges: `START_NORMAL`/resume init (`:1292, :1915, :1941`), pause/resume state transitions (`TRACKING <-> PAUSED`), `saveLap` outside `endWorkout` (`:776` vs `:2379`), sport changes (`:2304, :2334`), existing altitude calibration (`:851`), and terminal `TRACKING_FINISHED_INTENT` (`:2551`, `REQ-TRK-010`). `TrackerService.java` MUST remain `<= 3,559` LoC (`REQ-PRO-129`, `ArchitectureConformanceContractTest`), extracting a small Kotlin helper if edits approach `3,559` LoC.
3. **`REQ-STB-020.3` (`PeriodsRepository` Unfinished-Session Filter)**: `PeriodsRepository` (`:179-187`) SHALL filter `list.filter { it.finished }` before `map { it.toPeriodProjectionKey() }` and `precalculateGroups` (`:955-963`) SHALL require `it.startTimeS > 0L && it.finished`, ensuring `!finished` sessions trigger `0` `loadFromDatabase(forceIncremental = true)` rebuilds while retaining `PeriodProjectionKey.finished` (`REQ-PER-016`, `PeriodsRepositorySyncTest.kt:282`) and `onWorkoutFinished` at stop (`WorkoutRepository.kt:4487`).
4. **`REQ-STB-020.4` (`AthleteProfileViewModel` & `KnownLocationsViewModel` Guards & Consumer Parity)**: `AthleteProfileViewModel` (`:395-405`) SHALL filter `it.finished` and `distinctUntilChanged()` on finished workout keys before running `loadCardioTrendSamples()` / `getWorkoutTrainingLoadRecords()` (`REQ-REV-011`). `KnownLocationsViewModel` (`:127, 161-189`) SHALL filter `it.finished` and deduplicate on finished `(id, startLatLng, bSportType)` before `groupSportStartsByLocation` (`REQ-DAT-016`). Downstream consumers (`WorkoutSummariesViewModel.kt:164-189` `WhileSubscribed(5000)` + `WorkoutSummary.kt:122, 227` active `!finished` cards, `PeriodsViewModel.kt:412-421`, `WorkoutClustersScreen.kt:96`) MUST retain valid behavior.
5. **`REQ-STB-020.5` (Parallel-Lane Isolation & Heap/Contract Compliance)**: `WorkoutRepository.kt:1970-2650` (`ATT-8916`), `req_aftermath_periods.md`, `tst_aftermath_periods.md`, `LegacyImportEngine.kt` (`ATT-9018`), and `ANT-Android-SDKs` MUST remain untouched (`0` `WorkoutRepository.kt` edits preferred); `0` `src/main/` test reads (`REQ-PRO-049`).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-STB-020` (`REQ-STB-020.1`–`.5`) in `req_process_stability_part2.md`, refining `REQ-TRK-009` (`SCRUM-97`), `REQ-TRK-010` (`ATT-505`), `REQ-PER-016` (`ATT-4019`), `REQ-REV-011` (`ATT-6724`), and `REQ-DAT-016` (`ATT-1734`), enforcing `REQ-NFR-005`.
* **Historical Origin & Commit Trace**: `1e6acd7f` (`SCRUM-97`), `e21292d5` (`ATT-8158`), `a7f4f22d` (`ATT-6724`), `3d818885` (`ATT-1734`).
* **Root Reason for Existing Formulation**: `SCRUM-97` refreshed active cards at 1 Hz; `ATT-8158` replaced 1-Hz SQLite extrema writes with `updateExtremaValueInMemory` while retaining per-sensor `_allWorkouts` cloning and 1-Hz `WORKOUT_UPDATED_INTENT`.
* **Preservation of Core Invariants**: Preserves 1-Hz `recordLiveSample` & paused `TIME_TOTAL_s` DB writes, `TRACKING_FINISHED_INTENT` finalization, `toPeriodProjectionKey()` contract, active `!finished` cards on discrete transitions, `LegacyImportEngine` reloads, `ATT-8916` isolation, and `TrackerService.java <= 3,559` LoC.

---

## 3. Acceptance Criteria (Given-When-Then) & Invariants
* **Criterion 1 (Zero 1-Hz Churn)**: *Given* active or paused tracking with `2,196` cached workouts, *When* `sampleAndWriteToDb` ticks repeatedly at 1 Hz across changing extrema, *Then* `0` `updateExtremaValueInMemory` calls and `0` steady-state `WORKOUT_UPDATED_INTENT` broadcasts occur while DB sample/total-time writes succeed.
* **Criterion 2 (Discrete Lifecycle Edges)**: *Given* `START_NORMAL`/resume, `TRACKING <-> PAUSED` edge, `saveLap` outside `endWorkout`, sport change, altitude correction, and `endWorkout`, *When* triggered, *Then* each dispatches once cleanly without duplicate stop-lap broadcasts.
* **Criterion 3 (`!finished` Consumer Isolation)**: *Given* `!finished` emissions vs. `finished = true` transitions in `allWorkouts`, *When* collected by `PeriodsRepository`, `AthleteProfileViewModel`, and `KnownLocationsViewModel`, *Then* `!finished` triggers `0` rebuilds/SQLite/geofence passes while `finished = true` updates state immediately.

---

## 4. Test Specification (`TST-STB-020`)
* **`TST-STB-020.1` (`TrackerServiceStateMachineCharacterisationTest`)**: Assert `0` `updateExtremaValueInMemory` calls & `0` steady-state active/paused `WORKOUT_UPDATED_INTENT` across repeated ticks; verify paused `updateWorkoutTotalTime` persists; assert single broadcast per discrete edge (`START_NORMAL`/resume, `TRACKING <-> PAUSED`, `saveLap` outside `endWorkout`, sport change, altitude, `endWorkout` `TRACKING_FINISHED_INTENT`).
* **`TST-STB-020.2` (`PeriodsRepositorySyncTest`)**: Assert `!finished` changes in `allWorkouts` trigger `0` `loadFromDatabase` / `getPeriodsByType` rebuilds, `precalculateGroups` excludes `!finished`, `toPeriodProjectionKey()` keeps `finished` differentiation (`:282`), and `finished: false -> true` triggers sync.
* **`TST-STB-020.3` (`AthleteProfileViewModelCardioPowerTest`, `KnownLocationsViewModelTest`)**: Update relaxed mocks (`KnownLocationsViewModelTest.kt:228-246` lines `232-236`, `:256-305`, and `AthleteProfileViewModelCardioPowerTest.kt:265` with `finished = true` & distinct non-default `id`) and verify `!finished` triggers `0` SQLite/geofence runs while `finished = true` refreshes UI state.
* **`TST-STB-020.4` (Sustained 3,600-Tick OOM-Churn & Contracts)**: Replay `>= 3,600` 1-Hz ticks against `2,200` workouts (`0` `_allWorkouts` clones, `0` reloads, `0` downstream rebuilds); pass `ArchitectureConformanceContractTest` (`TrackerService.java <= 3,559` LoC), `DetailedDesignParityContractTest`, `SourceTextReadingAuditContractTest` (`0` `src/main` reads), and `verify_requirement_governance.py` (`exit 0`).

---

## 5. Traceability Matrix

| Requirement | Description | Test Case | Target Suites | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-STB-020.1` | Stop 1-Hz extrema cloning & steady-state active/paused broadcasts | `TST-STB-020.1` | `TrackerServiceStateMachineCharacterisationTest` | `[Verified]` |
| `REQ-STB-020.2` | Discrete lifecycle edge broadcasts & `TrackerService.java <= 3,559` LoC | `TST-STB-020.1`, `.4` | `TrackerServiceStateMachineCharacterisationTest`, `ArchitectureConformanceContractTest` | `[Verified]` |
| `REQ-STB-020.3` | Filter `!finished` in `PeriodsRepository` & `precalculateGroups` | `TST-STB-020.2` | `PeriodsRepositorySyncTest` | `[Verified]` |
| `REQ-STB-020.4` | Filter/dedup `!finished` in `AthleteProfileVM` & `KnownLocationsVM` | `TST-STB-020.3` | `AthleteProfileViewModelCardioPowerTest`, `KnownLocationsViewModelTest` | `[Verified]` |
| `REQ-STB-020.5` | `ATT-8916` isolation, 3,600-tick OOM check, `N/A` l10n, `0` `src/main` reads | `TST-STB-020.4` | Contract, Stress/Churn & `verify_requirement_governance.py` | `[Verified]` |
