# Implementation Plan: ATT-536 - Period Longest Workout Metric Integrity

## 1. Overview & Problem Analysis
In the Period summary views (most notably for weekly periods), the "Longest Workout" highlight row under sports with multiple workouts (`stats.count > 1`) frequently displays empty or zero values (`0:00` duration, `""` name, `0.0 km` distance, `0 m` ascent).

Forensic analysis revealed:
1. **Daylight Saving Time (DST) Shift**: `PeriodsRepository.kt:519` used `toEpochSecond(OffsetDateTime.now().offset)` rather than `.atZone(ZoneId.systemDefault()).toEpochSecond()`. For workouts in winter standard time (UTC+1) processed when system time is in summer DST (UTC+2), Monday 00:00:00 is shifted backwards by 3600 seconds to Sunday 23:00:00 of the preceding week. This generates a mismatched `sortKey` (e.g. `2025-W02` instead of `2025-W03`), causing `groups.weeks[it.sortKey]` to be empty and aborting metric hydration in `enrich()`. (The same bug exists in `rollupMonthsToYears` at line 532).
2. **Zero-Duration Propagation in Hierarchy**: `aggregateChildrenToParent()` in `PeriodsRepository.kt:588` attempts to find the longest workout across child periods using `maxByOrNull { it.durationSec }`. However, child periods loaded from SQLite have `durationSec == 0` because `SportStatsContract` only persisted `longest_workout_id` without duration. This causes `maxByOrNull` to tie at `0` and pick arbitrarily.
3. **Fragile In-Memory-Only Hydration**: `enrich()` in `PeriodsRepository.kt` failed to provide fallback hydration if the target workout was missing from `groupWorkouts`.
4. **UI Fallthrough on Zero Metrics**: `PeriodSummaryCard.kt` checked `stats.count > 1 && longestWorkout != null`, displaying dummy zero-metric objects directly to the user.

---

## 2. Requirements & Verification Traceability
* **Requirement**: `REQ-PER-011` (Longest Workout Metric Display).
* **System Invariants**:
  * Period card headers (`totalDurationSec`, `totalWorkouts`) and unit formatting MUST remain strictly unchanged.
  * Spatial map bounds and route framing MUST remain intact.
  * List scrolling performance MUST NOT perform heavy or un-indexed synchronous queries on the main thread.
* **Test Case**: `TST-PER-015` (Period Longest Workout Metric Integrity & Cross-Season Week Keys).

---

## 3. Proposed Changes

### Layer 1: Repository & Aggregation Engine (`ui/aftermath/periodlist/PeriodsRepository.kt`)
1. **DST Timestamp Normalization**:
   * Replace `toEpochSecond(OffsetDateTime.now().offset)` in `rollupDaysToParentPeriods()` (line 519) with `.atZone(java.time.ZoneId.systemDefault()).toEpochSecond()`.
   * Replace `toEpochSecond(OffsetDateTime.now().offset)` in `rollupMonthsToYears()` (lines 532–533) with `.atZone(java.time.ZoneId.systemDefault()).toEpochSecond()`.
2. **Accurate Longest Selection in Hierarchy**:
   * In `aggregateChildrenToParent()`, resolve real durations for child sports' longest workouts (using in-memory workouts or persisted duration) before calling `maxByOrNull { it.durationSec }`.
3. **Resilient Fallback Hydration in `enrich()`**:
   * If `groupWorkouts` does not contain `targetId` (or is empty), fall back to `workoutRepo.allWorkouts.value.find { it.id == targetId }` or querying `workoutSummariesManager.getWorkoutCursor(targetId)` to hydrate real name, duration, distance, and ascent.

### Layer 2: Persistence Layer (`ui/aftermath/periodlist/PeriodSummariesDatabaseManager.kt`)
1. **Schema Evolution**:
   * Add `COLUMN_LONGEST_DURATION = "longest_duration"` to `SportStatsContract`.
   * Persist and read `longest_duration` in `SportStatsContract` to ensure child periods loaded from DB carry genuine durations for hierarchical rollups.
2. **Version Bump & Cleanup**:
   * Bump database version from 25 to 26 (`PeriodSummaries.db`).
   * Add `PeriodSummaries_v25.db` to the cleanup list in `init` and implement `onUpgrade` for `oldVersion < 26` to force a clean re-aggregation with accurate week boundaries.

### Layer 3: Presentation Guard (`ui/aftermath/periodlist/PeriodSummaryCard.kt`)
1. **Defense-in-Depth UI Guard**:
   * Guard the longest workout row visibility with `stats.count > 1 && longestWorkout != null && longestWorkout.durationSec > 0`. Prevents any transient or unhydrated dummy records from ever rendering as zero metrics.

### Layer 4: Unit Testing (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsTemporalKeyTest.kt`)
1. **Automated Test Suite**:
   * Test week sort key generation and week start timestamps across Daylight Saving Time transitions (winter standard time vs. summer daylight time).
   * Verify consistency between `WorkoutData.startTimeS` grouping keys and `aggregateChildrenToParent()` week sort keys.

---

## 4. Verification Plan

### Automated Unit Tests
* Execute `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsTemporalKeyTest`
* Execute `./gradlew testDebugUnitTest` to ensure zero regressions across existing test suites.

### Manual Verification
* Inspect the Period summaries in the app across Day, Week, Month, and Year tabs:
  * Verify that under the Week tab, sports with multiple workouts show the Longest Workout highlight with non-zero duration, correct workout name, distance, and ascent.
  * Verify that Period header metrics (duration and workout count) and maps continue to render seamlessly.
