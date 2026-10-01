# Stage 2: Requirement & Test Specification - ATT-1811: Heart Rate and Power Zone Distribution Calculation Computes Total Time as 1 Second

**Ticket**: [ATT-1811](https://rainerblind.atlassian.net/browse/ATT-1811)  
**Sub-task**: [ATT-1836](https://rainerblind.atlassian.net/browse/ATT-1836) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-202` (*Aftermath: Heart Rate 5-Zone Distribution Vertical Column Chart Architecture*) and `REQ-UI-203` (*Aftermath: Cycling Power 5-Zone Distribution Vertical Column Chart Architecture*)  
**Test Spec ID**: `TST-UI-156` / `TST-UI-157`  
**Branch**: `feature/ATT-1811`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (REQ-UI-202 / REQ-UI-203 Refinement)

### 1.1 Problem Statement & Rationale
When inspecting historical workouts (such as "Lockerer Lauf um die Bärenseen", recorded in 2014) where modern accumulator columns (`TIME_ACTIVE`, `TIME_TOTAL`) are null in `WorkoutSamples.db`, `WorkoutRepository` extracted `timeSec = 0L` for every sample. In turn, `ZoneDistributionCalculator` evaluated $\Delta t = 0\text{s}$ between consecutive samples, collapsing thousands of telemetry points into 0s and allocating only 1 second (`DEFAULT_LAST_SAMPLE_DURATION_SEC = 1L`) to the final sample's zone.

This specification refines telemetry timestamp extraction in `WorkoutRepository.kt` to fall back to SQLite's `time` column (`WorkoutSamplesDbHelper.TIME`) and sequential unit intervals, and introduces degeneracy immunity in `ZoneDistributionCalculator.kt` to ensure non-advancing timestamps do not cause duration collapse.

### 1.2 Functional & Architectural Requirements

1. **Full-Fidelity SQLite Telemetry Extraction Fallback (`WorkoutRepository.kt`)**:
   - In `getHeartRateZoneDistribution(workoutId, bSportType)`, `getPowerZoneDistribution(workoutId)`, and `getWorkoutTrackPoints(workoutId, trackType)`:
     - The repository SHALL query `TIME_ACTIVE`, `TIME_TOTAL`, and `time` (`WorkoutSamplesDbHelper.TIME`).
     - If `TIME_ACTIVE` is non-null and valid, its value SHALL be used directly as elapsed seconds.
     - Else if `TIME_TOTAL` is non-null and valid, its value SHALL be used directly as elapsed seconds.
     - Else if `time` is non-null and valid, the system SHALL parse the ISO timestamp (`yyyy-MM-dd HH:mm:ss`) to epoch seconds and derive relative elapsed time $t_{\text{rel}} = (t - t_0)$ anchored to the initial sample timestamp $t_0$.
     - Else, the system SHALL fall back to the sequential sample index ($0, 1, 2, \dots$) representing 1Hz recording.
   - Database operations SHALL execute on `Dispatchers.IO`.

2. **Degeneracy Immunity in Pure Mathematical Engine (`ZoneDistributionCalculator.kt`)**:
   - In `ZoneDistributionCalculator.calculateHeartRateDistribution` and `calculatePowerDistribution`:
     - If `validSamples.size > 1` and `validSamples.first().timeActiveSec == validSamples.last().timeActiveSec` (all samples have identical non-advancing timestamps), the calculator SHALL defensively assign `dt = 1L` for every sample, producing a valid aggregate duration equal to `validSamples.size` seconds.
     - For standard advancing timestamps, active duration spent in each zone SHALL continue to be accumulated from consecutive sample deltas $\Delta t = (t_{i+1} - t_i)$ clamped to $[0\text{s}, 5\text{s}]$.
     - If total active duration is 0 or no valid samples exist ($value > 0$), the calculator SHALL return `null`.

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Historical Workout with NULL `TIME_ACTIVE`)**:
  * *Given* a workout recorded without `TIME_ACTIVE` or `TIME_TOTAL` columns populated, but containing valid `time` (`DATETIME`) and heart rate telemetry (e.g. "Lockerer Lauf um die Bärenseen"),
  * *When* the athlete views the workout details in Aftermath (`TrackOnMapScreen`),
  * *Then* `WorkoutRepository.getHeartRateZoneDistribution` SHALL parse sample timestamps from `time`, and the 5-zone Heart Rate distribution card SHALL display the true multi-minute active duration distributed across the physiological zones.
* **Criterion 2 (Degenerate Non-Advancing Timestamps)**:
  * *Given* a telemetry sample list where all samples contain the identical timestamp `timeActiveSec = 0L`,
  * *When* `calculateHeartRateDistribution` or `calculatePowerDistribution` is invoked,
  * *Then* the calculator SHALL fall back to unit duration ($1\text{s}$ per sample) and compute the aggregate duration equal to the number of valid samples.
* **Criterion 3 (Normal Advancing Timestamps & Pause Clamping)**:
  * *Given* modern telemetry samples with monotonically advancing `timeActiveSec` and pause gaps $> 5\text{s}$,
  * *When* zone distribution is calculated,
  * *Then* inter-sample deltas SHALL be clamped to $5\text{s}$ exactly as before, preserving pause protection.
* **Criterion 4 (PathPoint Elevation Scrubbing Time Synchronization)**:
  * *Given* a workout with null `TIME_ACTIVE` inspected with elevation profile X-axis domain set to `TIME`,
  * *When* scrubbing along the elevation curve,
  * *Then* `PathPoint.timeSec` SHALL monotonically increase from $0\text{s}$ to the total duration rather than remaining at $0\text{s}$.

### 1.4 System Invariants
- Modern workouts with populated `TIME_ACTIVE` MUST NOT change behavior or calculation results.
- Single-thread SQLite database confinement on `Dispatchers.IO` MUST NOT be violated.
- Zero database migrations (`ALTER TABLE` or DDL modifications).
- 100% 9-language localization parity MUST NOT be broken.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-UI-202` (*Aftermath: Heart Rate 5-Zone Distribution Vertical Column Chart Architecture*) and `REQ-UI-203` (*Aftermath: Cycling Power 5-Zone Distribution Vertical Column Chart Architecture*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1389` / `ATT-1390`, Sprint `2026-40.5`. Refined in ticket `ATT-1739`, Sprint `2026-40.6`.
3. *Root Reason for Existing Formulation*: Item 3 specified querying `TIME_ACTIVE` and `TIME_TOTAL` from the workout samples table, assuming modern workouts where `ClockDevice` populates `TIME_ACTIVE`. It did not account for historical workouts (recorded prior to `TIME_ACTIVE` persistence) where `TIME_ACTIVE` is `NULL`.
4. *Preservation of Core Invariants*: Adding a fallback to SQLite's `time` column (`WorkoutSamplesDbHelper.TIME`) and sequential 1-second intervals fully preserves modern workout behavior (`TIME_ACTIVE` is used when present) while restoring 100% telemetry fidelity for historical workouts. Mathematical zone boundaries ($Z_1 \dots Z_5$), clamping to $[0\text{s}, 5\text{s}]$, `Dispatchers.IO` confinement, and 9-language localization remain completely untouched.

---

## 2. Test Specification (TST-UI-156 / TST-UI-157 Refinement)

### Test Case 1: `testCalculateHeartRateDistribution_degenerateIdenticalTimestamps_fallsBackToUnitDuration` (`TST-UI-156.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionCalculatorTest.kt`
* **Preconditions**: A list of 100 valid HR samples all having `timeActiveSec = 0L` (identical degenerate timestamps).
* **Action**: Invoke `ZoneDistributionCalculator.calculateHeartRateDistribution(samples, thresholds)`.
* **Expected Result**:
  * Result is non-null.
  * Total duration is exactly 100L (100 seconds, not 1 second).
  * Entries reflect the proportion of samples in each zone matching threshold classification.

### Test Case 2: `testCalculatePowerDistribution_degenerateIdenticalTimestamps_fallsBackToUnitDuration` (`TST-UI-157.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCalculatorTest.kt`
* **Preconditions**: A list of 100 valid Power samples all having `timeActiveSec = 0L`.
* **Action**: Invoke `ZoneDistributionCalculator.calculatePowerDistribution(samples, thresholds)`.
* **Expected Result**:
  * Result is non-null.
  * Total duration is exactly 100L.
  * Entries reflect the proportion of samples in each power zone.

### Test Case 3: `testGetHeartRateZoneDistribution_fallbackToTimeColumnWhenTimeActiveNull` (`TST-UI-156.2`)
* **Scope**: Unit / Repository Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepositoryZoneFallbackTest.kt`
* **Preconditions**: Mock database cursor returning samples where `TIME_ACTIVE` and `TIME_TOTAL` are null, but `time` contains `"2014-03-25 09:59:51"`, `"2014-03-25 09:59:52"`, `"2014-03-25 09:59:53"`, with valid `HR`.
* **Action**: Query heart rate zone distribution.
* **Expected Result**: Total duration matches the elapsed time between timestamps (3 seconds), with correctly binned zones.

### Test Case 4: 9-Language Localization & Specifier Audit (`TST-UI-156.3`)
* **Scope**: Localization Parity Test
* **Goal**: Verify string presence and matching `%s`/`%d` tokens across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 5: Clean-Room Regression Suite (`TST-UI-156.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-156.1` | Unit | `ZoneDistributionCalculator.calculateHeartRateDistribution` | `REQ-UI-202` | Specified |
| `TST-UI-157.1` | Unit | `ZoneDistributionCalculator.calculatePowerDistribution` | `REQ-UI-203` | Specified |
| `TST-UI-156.2` | Repository | `WorkoutRepository.getHeartRateZoneDistribution` | `REQ-UI-202` | Specified |
| `TST-UI-157.2` | Repository | `WorkoutRepository.getPowerZoneDistribution` | `REQ-UI-203` | Specified |
| `TST-UI-156.3` | Localization | `TranslationParityTest` | `REQ-UI-106`, `REQ-UI-202` | Specified |
| `TST-UI-156.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
