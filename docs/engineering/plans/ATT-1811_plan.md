# Stage 3: Implementation Plan - ATT-1811: Heart Rate and Power Zone Distribution Calculation Computes Total Time as 1 Second

**Ticket**: [ATT-1811](https://rainerblind.atlassian.net/browse/ATT-1811)  
**Sub-task**: [ATT-1837](https://rainerblind.atlassian.net/browse/ATT-1837) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-202`, `REQ-UI-203`  
**Test Mapping**: `TST-UI-156`, `TST-UI-157`  
**Branch**: `feature/ATT-1811`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Description & Background

In ticket ATT-1811, inspecting historical workouts (such as "Lockerer Lauf um die Bärenseen", 2014) in the Aftermath screen showed a total duration of only 0:01 in Zone 2 and 0:00 elsewhere. Investigation proved that:
1. In `WorkoutSamples.db`, 462 out of 509 sample tables lack `TIME_ACTIVE` and `TIME_TOTAL` column data (`NULL` for all rows), because these columns were only populated in recent app versions.
2. `WorkoutRepository.kt` defaulted `timeSec` to `0L` when both `TIME_ACTIVE` and `TIME_TOTAL` were null.
3. `ZoneDistributionCalculator.kt` computed $\Delta t = (t_{i+1} - t_i) = 0\text{s}$ between consecutive samples, resulting in 0s for all samples except the terminal sample (which gets `1L`).
4. Additionally, `WorkoutRepository.getWorkoutTrackPoints` assigned `0L` to `PathPoint.timeSec`, which impairs time-based scrubbing on the elevation profile for historical activities.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-202` (*Aftermath: Heart Rate 5-Zone Distribution Vertical Column Chart Architecture*) & `REQ-UI-203` (*Aftermath: Cycling Power 5-Zone Distribution Vertical Column Chart Architecture*).
* **Test Mapping**: `TST-UI-156` & `TST-UI-157` (*ZoneDistributionCalculator degenerate timestamp unit tests, WorkoutRepository timestamp fallback unit tests, clean-room regression*).

---

## 3. System Invariants & Preserved Behavior

1. **Modern Workout Precedence**: For sessions where `TIME_ACTIVE` is non-null and valid, `TIME_ACTIVE` remains the primary authoritative timestamp source.
2. **Pause Protection Invariant**: Inter-sample delta clamping to `[0L, MAX_DELTA_T_SEC]` (5s) remains intact to prevent pause inflation.
3. **Dispatcher & Thread Safety**: All SQLite queries remain strictly confined to `Dispatchers.IO`.
4. **Zero Schema Migration**: No DDL or SQLite table alterations (`ALTER TABLE`) are introduced.
5. **Human Decision Gate**: Parent ticket ATT-1811 remains in `Analysis` until final walkthrough completion, after which it transitions to `Final Review (Human)`. Subtasks transition to `Erledigt` autonomously upon passing Gate reviews.

---

## 4. Proposed Architectural Changes

### Component 1: `ZoneDistributionCalculator.kt`
* Add degeneracy detection:
  ```kotlin
  val isDegenerate = validSamples.size > 1 && validSamples.first().timeActiveSec == validSamples.last().timeActiveSec
  ```
* When `isDegenerate` is true, assign `dt = 1L` per sample so that all valid samples contribute 1 second of active duration instead of collapsing into a 1-second session.
* Apply identically to both `calculateHeartRateDistribution` and `calculatePowerDistribution`.

### Component 2: `WorkoutRepository.kt`
* Introduce a robust parsing helper `parseTimestampOffset(timeStr: String?, initialEpoch: Long?): Pair<Long?, Long?>` that converts ISO datetime strings (`yyyy-MM-dd HH:mm:ss`) to relative elapsed seconds from the workout start timestamp.
* In `getHeartRateZoneDistribution`, `getPowerZoneDistribution`, and `getWorkoutTrackPoints`:
  * Query `timeIdx = cursor.getColumnIndex(WorkoutSamplesDbHelper.TIME)` alongside `timeActiveIdx` and `timeTotalIdx`.
  * Track `var initialTimestampEpochSec: Long? = null` and `var sampleIndex = 0L`.
  * Derive `timeSec` by prioritizing:
    1. `TIME_ACTIVE` (if present and non-null)
    2. `TIME_TOTAL` (if present and non-null)
    3. `time` column parsed via `parseTimestampOffset`
    4. Sequential `sampleIndex` fallback (1Hz assumption)

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Degeneracy Handling in `ZoneDistributionCalculator.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionCalculator.kt`
* Changes: Add `isDegenerate` check and `dt = 1L` fallback in `calculateHeartRateDistribution` and `calculatePowerDistribution`.

### Step 2: Unit Tests for Degenerate Timestamps in `ZoneDistributionCalculatorTest.kt` & `PowerZoneDistributionCalculatorTest.kt`
* Files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionCalculatorTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCalculatorTest.kt`
* Changes: Add tests asserting that lists of 100 samples with `timeActiveSec = 0L` yield `totalActiveTimeSec = 100L`.

### Step 3: Fallback Timestamp Extraction in `WorkoutRepository.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
* Changes: Add `parseTimestampOffset` and update `getHeartRateZoneDistribution`, `getPowerZoneDistribution`, and `getWorkoutTrackPoints` to fall back to `WorkoutSamplesDbHelper.TIME` and sequential row indexing.

### Step 4: Unit Test for WorkoutRepository Timestamp Fallback
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepositoryTimestampFallbackTest.kt`
* Changes: Unit test validating `parseTimestampOffset` with valid, empty, and invalid date formats.

### Step 5: Execute Targeted Tests & Full Test Suite
* Commands:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.*"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryTimestampFallbackTest"`
  * `./gradlew testDebugUnitTest`

---

## 6. Verification & Rollback Plan

* **Verification**: Verify that targeted unit tests pass, `./gradlew testDebugUnitTest` runs with 100% success across all modules, and install the build to the connected Pixel 10 to inspect "Lockerer Lauf um die Bärenseen".
* **Rollback**: Branch `feature/ATT-1811` can be reverted cleanly via git without impacting `sprint/2026-40.7`.
