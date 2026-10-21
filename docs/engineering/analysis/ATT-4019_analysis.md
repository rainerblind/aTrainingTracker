# Stage 1 Analysis: ATT-4019 - Reduce redundant period rebuilds and triple tracking-finished handling

**Ticket**: [ATT-4019](https://rainerblind.atlassian.net/browse/ATT-4019)  
**Parent Epic**: [ATT-67](https://rainerblind.atlassian.net/browse/ATT-67) (*Improve Periods/Stats*)  
**Target Release**: `2026-43.5`  
**Active Sprint**: `2026-43.5`  
**Branch**: `feature/ATT-4019`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-21  

---

## 1. Problem Statement & Motivation
* `PeriodsRepository` triggers full period re-enrichment (`loadFromDatabase(forceIncremental = true)`) on every emission of `WorkoutRepository.allWorkouts` (including 50-item streaming chunks during initial loading, export status updates, and edits).
* When a workout finishes, `TrackerService.endWorkout()` dispatches both system and `LocalBroadcastManager` broadcasts. `PeriodsRepository` listens to both and `WorkoutRepository` also listens and invokes `PeriodsRepository.onWorkoutFinished`, causing 3 sequential database transactions and period rebuilds plus a 4th reactive rebuild via `_allWorkouts`.
* `WorkoutDataMapper` is re-instantiated ad-hoc 3+ times in `PeriodsRepository` (lines 79, 212, 789) creating duplicate database helpers on each query.

---

## 2. Root Cause Analysis (Forensic Investigation)
| Finding | Source Location | Root Cause Mechanism |
| :--- | :--- | :--- |
| **Redundant Rebuilds** | `PeriodsRepository.kt:175-179` | `allWorkouts.collectLatest` reacts to all emissions without projection or `distinctUntilChanged`. In `loadAllWorkouts`, 50-session chunks trigger repeated O(N) grouping and 4x SQLite period queries. |
| **Triple Finished Handling** | `TrackerService.java:1968-1978`, `PeriodsRepository.kt:183-189`, `WorkoutRepository.kt:1846` | Dual broadcast in `TrackerService` + dual receiver registration in `PeriodsRepository` + downstream invocation by `WorkoutRepository` under `REQ-STB-018.1` results in 3x `onWorkoutFinished` + 1x reactive rebuild. |
| **Mapper Allocations** | `PeriodsRepository.kt:79,212,789` | Local instantiations of `WorkoutDataMapper` and DB helpers (`SportTypeDatabaseManager`, `EquipmentDbHelper`, `StravaUploadDbHelper`) rather than a single lazy shared instance. |

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Deduplicate `TRACKING_FINISHED_INTENT` handling so `onWorkoutFinished` executes exactly once per finished workout.
  * Filter/project `allWorkouts` reactive emissions in `PeriodsRepository` to period-relevant fields and debounce progressive stream bursts.
  * Consolidate `WorkoutDataMapper` in `PeriodsRepository` into a single reusable instance.
* **Out-of-Scope Non-Goals**:
  * Refactoring SQLite schemas or altering `PeriodSummaries.db` table contracts.
  * Altering `WorkoutRepository` streaming batch sizes or caching logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-014` (*Immediate Period View Reactive Synchronization on Workout Finalization*, `PeriodsRepository.kt`).
* **Historical Origin & Commit Trace**: Sprint 2026-41.7 (`ATT-3056`, commit `f65f8fdd`).
* **Root Reason for Existing Formulation**: ATT-3056 added dual broadcast listening (`LocalBroadcastManager` + context `sendBroadcast`) to guarantee first-workout appearance on clean installs. Later, `REQ-STB-018.1` (ATT-4002) made `WorkoutRepository.reloadWorkoutData` responsible for invoking `onWorkoutFinished`, creating triple redundant execution.
* **Preservation of Core Invariants**: First-workout immediate display (`REQ-PER-014`), O(1) self-healing sync (`REQ-MIG-026`), and period calculation accuracy remain 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution
* **Single Lifecycle Authority / Deduplication**: Designate `WorkoutRepository` as the single caller of `onWorkoutFinished`, or deduplicate `onWorkoutFinished` in `PeriodsRepository` via an atomic/recent-ID guard and remove dual receiver registration.
* **Distinct Flow Projection**: Map `allWorkouts` to period-relevant keys (ID, `startTimeS`, `activeTimeSec`, bounds, sport) and apply `distinctUntilChanged()` (or debounce progressive emissions).
* **Shared Mapper Instance**: Replace repeated `WorkoutDataMapper(...)` constructor calls in `PeriodsRepository` with `private val mapper by lazy { ... }`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in period card aggregation, volume graphs, and polyline previews.
  2. Immediate period appearance upon workout completion preserved (`REQ-PER-014`).
  3. Clean-room unit test suite pass rate remains 100%.
* **Risk Rating**: **MEDIUM** (Touches core repository reactivity and workout completion lifecycle, but changes are purely optimization and deduplication without schema changes).
