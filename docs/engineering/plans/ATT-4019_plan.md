# Stage 3: Implementation Plan - ATT-4019: Reduce redundant period rebuilds and triple tracking-finished handling

**Ticket**: [ATT-4019](https://rainerblind.atlassian.net/browse/ATT-4019)  
**Parent Epic**: [ATT-67](https://rainerblind.atlassian.net/browse/ATT-67) (*Improve Periods/Stats*)  
**Target Release**: `2026-43.5` | **Active Sprint**: `2026-43.5`  
**Requirement Mapping**: `REQ-PER-016` (*Period Rebuild Flow Projection, Single-Owner Tracking Finalization Orchestration & Shared Mapper Instance*)  
**Test Mapping**: `TST-PER-022` | **Branch**: `feature/ATT-4019`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-21  

---

## 1. Problem Description & Background
* `PeriodsRepository` executes full O(N) database-driven period rebuilds (`loadFromDatabase(true)`) on every emission of `allWorkouts`, causing CPU/IO churn during progressive streaming (50-item batches) and non-period updates (Strava logs, renames).
* Dual broadcasts in `TrackerService` combined with dual receivers in `PeriodsRepository` and `WorkoutRepository.reloadWorkoutData` trigger 3 database transactions and 4 rebuilds per finished workout.
* `WorkoutDataMapper` is repeatedly constructed ad-hoc with new DB helper instances.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PER-016` (Amending `REQ-PER-014`, interacting with `REQ-STB-018.1`)
  * `REQ-PER-016.1`: Flow projection & debounced deduplication on period-relevant workout properties.
  * `REQ-PER-016.2`: Single-owner tracking finalization via `WorkoutRepository` & idempotent `onWorkoutFinished`.
  * `REQ-PER-016.3`: Consolidated reusable `WorkoutDataMapper` instance.
  * `REQ-PER-016.4`: Invariant preservation (`REQ-PER-014` immediate sync, `REQ-PER-015` DST bounds, `REQ-MIG-026` O(1) sync).
* **Test Mapping**: `TST-PER-022` (`TST-PER-022.1` to `TST-PER-022.4`).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Schema Migrations**: SQLite schema for `PeriodSummaries.db` and sort keys remain unchanged.
2. **Immediate First-Workout Sync (`REQ-PER-014`)**: Workouts immediately appear in periods upon completion without app restart.
3. **Dispatcher & Thread Safety**: All rollups execute under `rebuildMutex` on `Dispatchers.IO`.
4. **Clean-Room Test Pass Rate**: 100% test pass rate across unit test suite.

---

## 4. Proposed Architectural Changes
* **`PeriodsRepository.kt`**:
  * Consolidate mapper to `private val mapper by lazy { WorkoutDataMapper(application, workoutSummariesManager, ...) }`.
  * Remove redundant `trackingFinishedReceiver` and dual broadcast registrations (`LocalBroadcastManager` and `ContextCompat.registerReceiver`).
  * Add idempotent deduplication guard to `onWorkoutFinished(workout)` via atomic/recent-ID caching.
  * Project `allWorkouts` flow via `PeriodProjectionKey` (ID, timestamps, active seconds, sport, polylines, bounds) + `distinctUntilChanged()` + `debounce(200L)` before calling `loadFromDatabase(true)`.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Consolidated Mapper & Redundant Receiver Removal (`PeriodsRepository.kt`)
* In `PeriodsRepository.kt`, define `private val mapper by lazy { ... }` and replace local instances (lines 79, 212, 789).
* Remove `trackingFinishedReceiver` and its dual registration in `init`.
* Add idempotent deduplication (`lastProcessedWorkoutId`) to `onWorkoutFinished`.

### Step 2: Reactive Flow Projection & Debouncing (`PeriodsRepository.kt`)
* Define private `PeriodProjectionKey` data class capturing period-relevant fields.
* Update `workoutRepo.allWorkouts` subscription in `init`: `map { list -> list.map { it.toPeriodProjectionKey() } }.distinctUntilChanged().debounce(200L).collectLatest { loadFromDatabase(true) }`.

### Step 3: Test Implementation & Regression Verification
* Add `TST-PER-022.1` (flow projection & non-period suppression), `TST-PER-022.2` (single-owner invocation & idempotency), and `TST-PER-022.3` (shared mapper) to `PeriodsRepositorySyncTest.kt`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted period suite (`./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"`), then full clean-room suite in Stage 5.
* **Rollback**: Atomic git revert on `feature/ATT-4019` if regressions occur.
