# Stage 1 Analysis: ATT-6170 — Preserve `exportStatuses` and In-Memory Metadata in `WorkoutRepository.loadWorkout`

**Ticket**: `ATT-6170` (`1 SP`, `Bug`, `Medium`, `1 SP` confirmed — no split per `REQ-PRO-029`) | **Parent Epic**: `ATT-6033`  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-21_02_Spät` (Sprint 469) | **Branch**: `feature/ATT-6170` (`ae307106`) | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-21

---

## Context & Problem Statement
* **Current Behavior**: Opening the Workout Edit dialog (`EditWorkoutViewModel.loadWorkoutData()`) or executing any route-cluster action (`assignClusterToWorkout`, `unassignClusterFromWorkout`, `createNewClusterFromWorkout`, `WorkoutClusterRepository` move/assign/create/unassign) invokes `WorkoutRepository.loadWorkout(id)`. While `reloadWorkoutData(id)` preserves `exportStatuses` and transient fields via `addOrUpdateWorkout`, `loadWorkout(id)` immediately overwrites the existing entry in `_allWorkouts` via `newList[index] = workout` with a freshly mapped `WorkoutData` whose `exportStatuses` is `emptyList()`, causing Export-Status chips (File / Dropbox / Google Drive / Strava) to vanish from the workout list and detail views.
* **Expected Behavior**: `WorkoutRepository.loadWorkout(id)` preserves existing `exportStatuses` and in-memory transient fields (`mapPolyline`, `encodedAltitudes`, `encodedDistances`, `extremaRows` `minLatLng`/`maxLatLng`) when updating an already-cached workout in `_allWorkouts`, while still emitting the refreshed `WorkoutData` (including updated cluster/sport/gear identity) to `_initialWorkoutLoaded` for `EditWorkoutViewModel`.

---

## Root Cause & Forensics
### Independent Peer Review & Verification (Post-`ATT-6038` @ `ae307106`)
Audited live `sprint/2026-11-21_02_Spät` (`ae307106`, including `ATT-6038` commit `43919672`) across `WorkoutRepository.kt`, `WorkoutDataMapper.kt`, `BatchMetadataLoader.kt`, `WorkoutClusterRepository.kt`, `EditWorkoutViewModel.kt`, and `ArchitectureConformanceContractTest.kt`:

| Hypothesis / Check | Verdict | Live Code Evidence (`ae307106`) |
| :--- | :---: | :--- |
| **1. Bug still live post-`ATT-6038`?** | **CONFIRMED LIVE** | `ATT-6038` (`43919672`) delegated `WorkoutDataMapper.fromCursor` (`:94-98`) to `BatchMetadataLoader.loadForSingleWorkout` + `fromSnapshot(..., reconcile = true)`, but `fromSnapshot` (`:796`) **still sets `exportStatuses = emptyList()`** (`BatchMetadata` does not load export rows; `loadAllWorkouts` attaches them separately at `WorkoutRepository.kt:3839-3874`). |
| **2. Blind list overwrite in `loadWorkout`** | **CONFIRMED** (shifted lines) | `WorkoutRepository.kt:3716-3744` (legacy `:1512-1534`): `:3727` posts `_initialWorkoutLoaded.postValue(workout)` (`EditWorkoutViewModel.kt:165`); `:3734` executes `newList[index] = workout`, wiping `exportStatuses` and transient in-memory fields. |
| **3. Callers beyond `WorkoutRepository`** | **EXPANDED** | Besides `EditWorkoutViewModel.kt:164` (`loadWorkoutData`, legacy `:120`) and `WorkoutRepository.kt:4851,4865,4882,4900`, `WorkoutClusterRepository.kt:801,817,834` (`REQ-UI-2786.3`) also call `reloadWorkoutData` followed by `loadWorkout`, triggering the identical overwrite! |
| **4. Reusable merge helper (`DRY`)** | **CONFIRMED** | `WorkoutRepository.addOrUpdateWorkout(workout)` (`:4363-4396`, legacy `:2127`) already merges `mapPolyline`, `encodedAltitudes`, `encodedDistances`, `extremaRows` (incl. `minLatLng`/`maxLatLng`), and `exportStatuses`, and inserts + sorts descending by `startTimeS` when absent. |
| **5. `WorkoutRepository.kt` monolith ceiling** | **SAFE (`+120` LoC)** | `ArchitectureConformanceContractTest.kt:118` caps `WorkoutRepository.kt` at `5,117` LoC; current `wc -l` is `4,997` LoC. Delegating `:3729-3740` to `addOrUpdateWorkout(workout)` *reduces* LoC (`~4,987`). |

---

## Requirements Archaeology (Chesterton's Fence)
* **Original Requirement ID & Target**: `REQ-DAT-045.4` (`docs/requirements/req_data_migration_export_part2.md:164,170`, `ATT-6038`) explicitly kept `ATT-6170` `loadWorkout` export-status preservation out-of-scope; `REQ-STB-018.3` (`docs/requirements/req_process_stability_part1.md:2015-2019`, `ATT-4002`) governs pure functional `_allWorkouts.update` CAS invariants; `REQ-UI-2786.3` (`WorkoutClusterRepository.kt:789-836`) relies on `reloadWorkoutData` + `loadWorkout`.
* **Historical Origin & Commit Trace**: `ATT-6038` (`43919672`) unified `fromCursor` via `BatchMetadataLoader`; `ATT-4002` (`1c8e1a68`) codified pure `_allWorkouts.update` CAS semantics; `ATT-902` (`WorkoutRepositoryExportStatusTest`) guarded `reloadExportStatusesFor` list updates.
* **Root Reason for Existing Formulation**: `loadWorkout` was written as a single-item loader for `EditWorkoutViewModel` (`_initialWorkoutLoaded`) before `exportStatuses` and in-memory track/extrema caches were merged in `addOrUpdateWorkout` and `emitMergedWorkouts`.
* **Preservation of Core Invariants**: (1) `_initialWorkoutLoaded.postValue(workout)` (`WorkoutRepository.kt:3727`) and `radarTrafficSummary` enrichment (`:3721-3726`) remain intact for `EditWorkoutViewModel`; (2) `fromSnapshot` keeps `exportStatuses = emptyList()` so `WorkoutDataMappingSymmetryTest` (`Rule 20`) and `ATT-6038` batch separation remain untouched; (3) pure functional `_allWorkouts.update` CAS invariants (`REQ-STB-018.3`) preserved; (4) absent-workout insertion still sorts descending by `startTimeS`.

---

## Proposed Scope & Invariants
* **In-Scope**: Update `WorkoutRepository.loadWorkout(id)` (`WorkoutRepository.kt:3729-3740`) to delegate `_allWorkouts` update/insert to `addOrUpdateWorkout(workout)` (`:4363-4396`) after posting `_initialWorkoutLoaded.postValue(workout)` (`:3727`), plus unit regression tests covering: (1) preservation of non-empty `exportStatuses` and transient fields in `_allWorkouts` across `loadWorkout(id)`, (2) `_initialWorkoutLoaded` emission with refreshed DB fields (e.g. `clusterId`/`clusterName`), and (3) insertion + descending `startTimeS` sort when the workout is not yet cached.
* **Out-of-Scope**: `BatchMetadataLoader` / `WorkoutDataMapper.fromSnapshot` changes (`ATT-6038`), `reloadExportStatusesFor`, `EditWorkoutViewModel` UI changes, SQLite schema changes, or `strings*.xml` edits (`0` strings).

---

## Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-DAT-046` / `TST-DAT-042` in roomy **`docs/requirements/req_data_migration_export_part2.md`** (`271 / 4,000` LoC, `+3,729` headroom, alongside `REQ-DAT-045` `ATT-6038`) and **`docs/tests/tst_data_migration_export.md`** (`2,411 / 4,000` LoC, `+1,589` headroom) — strictly avoiding near-cap `req_data_migration_export_part1.md` (`3,994/4,000`) and `req_process_stability_part1.md` (`3,980/4,000`). **SYS.2/SYS.3 Impact (`REQ-PRO-131.4`)**: `None` (internal repository cache merge; zero external sensors, HAL, FGS, or REST APIs affected). `Localization Parity: N/A - 0 new or modified strings`.
* **Affected Architecture Elements**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt` (`loadWorkout`, `4,997 -> ~4,987 <= 5,117` LoC). No new classes, DB schemas, or `docs/architecture.md` subsystem changes.
* **Verification Impact & Test Surface**: Extend `WorkoutRepositoryExportStatusTest.kt` (or new `WorkoutRepositoryLoadWorkoutMergeTest.kt`) verifying `loadWorkout` preserves `exportStatuses`, `mapPolyline`, `extremaRows` `minLatLng`/`maxLatLng`, emits `_initialWorkoutLoaded`, and inserts uncached workouts sorted; run `WorkoutDataMappingSymmetryTest` & `ArchitectureConformanceContractTest`.
* **Risk Assessment & Invariants**: **LOW** (`1 SP`) — surgical reuse of existing `addOrUpdateWorkout(workout)` inside `loadWorkout` with zero `fromCursor`/`BatchMetadata` behavioral drift, preserving `WorkoutRepository.kt <= 5,117` LoC and `Rule 20` mapping symmetry.
