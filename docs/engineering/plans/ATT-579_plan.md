# Implementation Plan: ATT-579 - Period Longest Workout Selection Integrity

## 1. Problem Summary
In Period summaries (such as the January 2015 Month summary shown in attachment 28286.png), the Running summary displays an incorrect longest workout:
* Reported: *Run to home (Uni -> Rohr) #2* (44:17 min, 8.14 km) from 02.01.2015.
* Actual longest workout: *10 k im Rohrer Wald #8* (50:14 min, 9.19 km) from 31.01.2015.

### Root Causes
1. **Zero-Duration Tie Breaking in SQLite Aggregation**: Prior to DB version 26, `SportStatsContract` omitted `COLUMN_LONGEST_DURATION`. When daily child records were queried to roll up Months/Years, `it.longestWorkout.durationSec` was 0 for all days. `maxByOrNull { it.durationSec }` tied at 0 for all workouts and returned the first element in iteration order (the earliest run of the month, January 2), storing that incorrect workout ID in `PeriodSportStats`.
2. **Blind Trust of Stored `targetId` in `enrich()`**: `PeriodsRepository.kt:enrich()` prioritized `targetId` over calculating the maximum from `groupWorkouts`. Even when `groupWorkouts` contained all 55 workouts of that month, it queried `groupWorkouts.find { it.id == targetId }` directly, locking the UI into displaying the stale/incorrect selection.

---

## 2. Proposed Changes

### Presentation Layer & Repository
#### [PeriodsRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt)
* **Primary Authority from In-Memory Workouts in `enrich()`**:
  Update `enrich()` to evaluate `groupWorkouts.filter { it.bSportType == sport }.maxByOrNull { it.activeTimeSec }` first.
  If `groupWorkouts` contains workouts for that sport, use that maximum directly.
  Only if `groupWorkouts` is empty or has no workouts of that sport, fall back to `targetId` (via `workoutRepo.allWorkouts` and SQLite `getWorkoutCursor(targetId)`).
* **Spatial Anchor Robustness**:
  In `enrich()`, ensure `longestId` used in `anchorIds` also checks `groupWorkouts.maxByOrNull { it.activeTimeSec }?.id` before falling back to `summary.longestId`.

### Testing & Traceability
#### [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md)
* Update `REQ-PER-011` to reference `TST-PER-016`.

#### [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md)
* Add `TST-PER-016`: Multi-Workout Period Longest Workout Selection Integrity.

#### [PeriodsAggregationTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsAggregationTest.kt)
* Create automated unit test suite verifying:
  1. `enrich()` selection with workouts ordered chronologically (day 2 vs day 31): verifies day 31 (longer duration and distance) is chosen regardless of stale `targetId`.
  2. `aggregateChildrenToParent()` child sport stats duration resolution across multiple child days.
  3. Empty `groupWorkouts` fallback to `targetId`.

---

## 3. Invariants & Safety Guardrails
* **Zero UI thread blocking**: All heavy queries remain off the main thread.
* **Backward compatibility**: Preserves existing contracts and UI components.
* **Layout and formatting**: Period card header totals and metric formatting are untouched.

---

## 4. Verification Plan
* Run `:app:testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsAggregationTest`.
* Run full unit test suite `:app:testDebugUnitTest`.
