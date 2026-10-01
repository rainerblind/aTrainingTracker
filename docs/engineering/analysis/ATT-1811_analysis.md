# Stage 1 Analysis: ATT-1811 - Heart Rate and Power Zone Distribution Calculation Computes Total Time as 1 Second

**Ticket**: [ATT-1811](https://rainerblind.atlassian.net/browse/ATT-1811)  
**Sub-task**: [ATT-1834](https://rainerblind.atlassian.net/browse/ATT-1834) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Branch**: `feature/ATT-1811`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

During review of ATT-1739 on Pixel 10, inspecting a ~53-minute, 9.58 km run ("Lockerer Lauf um die Bärenseen", workout ID 3356, table `2014-03-25_1059`) revealed that the 5-column vertical Heart Rate Zone chart displayed a total duration of only 0:01 (1 second) in Zone 2 (100%), and 0:00 across all other zones. This occurred despite continuous HR sensor telemetry recorded between 87 and 176 bpm across all 3,188 samples of the activity.

The user expects the heart rate and cycling power zone distribution charts to reflect the true active duration spent in each training zone across the full workout, correctly integrating time intervals for all recorded telemetry samples.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of the database schema and sample rows on the connected Pixel 10 (`WorkoutSamples.db`) and repository code revealed a multi-layered root cause:

### 2.1 Database Forensic Audit: Missing `TIME_ACTIVE` / `TIME_TOTAL` in Historical Recordings
1. In `WorkoutRepository.kt` (`getHeartRateZoneDistribution` and `getPowerZoneDistribution`):
   ```kotlin
   val timeActiveIdx = cursor.getColumnIndex(SensorType.TIME_ACTIVE.name)
   val timeTotalIdx = cursor.getColumnIndex(SensorType.TIME_TOTAL.name)

   while (cursor.moveToNext()) {
       if (!cursor.isNull(hrIdx)) {
           val hr = cursor.getInt(hrIdx)
           if (hr > 0) {
               val timeSec = when {
                   timeActiveIdx != -1 && !cursor.isNull(timeActiveIdx) -> cursor.getLong(timeActiveIdx)
                   timeTotalIdx != -1 && !cursor.isNull(timeTotalIdx) -> cursor.getLong(timeTotalIdx)
                   else -> 0L
               }
               samples.add(ZoneSample(timeActiveSec = timeSec, value = hr))
           }
       }
   }
   ```
2. In the target workout `2014-03-25_1059`:
   - Total rows: 3,188
   - `COUNT(HR)`: 3,188 (valid heart rate data throughout)
   - `COUNT(TIME_ACTIVE)`: 0 (all 3,188 rows have `NULL`)
   - `COUNT(TIME_TOTAL)`: 0 (all 3,188 rows have `NULL`)
3. A comprehensive query across all 509 sample tables in `WorkoutSamples.db` revealed that **462 out of 509 tables (90.8%) have `COUNT(TIME_ACTIVE) == 0`**. In all historical workouts (recorded before modern accumulator columns were added to the sample write path), `TIME_ACTIVE` and `TIME_TOTAL` are completely `NULL`.
4. However, every sample table in `WorkoutSamples.db` contains SQLite's standard `time` column (`WorkoutSamplesDbHelper.TIME`, e.g. `'2014-03-25 09:59:51'`), populated with 1Hz `DATETIME` timestamps.
5. Because `timeActiveIdx` and `timeTotalIdx` were `NULL` for every row, `timeSec` defaulted to `0L` for all 3,188 samples in `samples`.

### 2.2 Mathematical Engine Degeneracy: Delta Collapse in `ZoneDistributionCalculator.kt`
In `ZoneDistributionCalculator.kt`:
```kotlin
for (i in validSamples.indices) {
    val sample = validSamples[i]
    val zoneIdx = determineHeartRateZone(sample.value, thresholds)

    val dt = if (i < validSamples.size - 1) {
        val nextSample = validSamples[i + 1]
        val rawDt = nextSample.timeActiveSec - sample.timeActiveSec
        when {
            rawDt < 0L -> 0L
            rawDt > MAX_DELTA_T_SEC -> MAX_DELTA_T_SEC
            else -> rawDt
        }
    } else {
        DEFAULT_LAST_SAMPLE_DURATION_SEC
    }

    zoneDurations[zoneIdx] += dt
}
```
1. Because `sample.timeActiveSec` is `0L` for every sample, `rawDt = 0L - 0L = 0L` for all indices $i = 0 \dots N-2$.
2. For indices $0 \dots 3186$, `dt = 0L`. Zero duration was accumulated into any zone.
3. For the final terminal sample ($i = 3187$), `dt = DEFAULT_LAST_SAMPLE_DURATION_SEC = 1L`.
4. Since the final sample happened to have a heart rate falling into Zone 2, `zoneDurations[1]` accumulated exactly `1L`, and all other zones accumulated `0L`.
5. `totalActiveTimeSec = 1L`. Zone 2 showed 0:01 (100%), and Zones 1, 3, 4, 5 showed 0:00 (0%).

### 2.3 Secondary Impact on `getWorkoutTrackPoints`
In `WorkoutRepository.getWorkoutTrackPoints()`, `PathPoint.timeSec` also falls back to `0L` when `TIME_ACTIVE` and `TIME_TOTAL` are `NULL`. When scrubbing along the elevation profile in `ElevationProfile.kt` or `TelemetryMetricGraph.kt` with X-axis domain set to `TIME`, all points collapse to 0s for historical workouts.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * **Fallback Timestamp Extraction in `WorkoutRepository.kt`**:
    * In `getHeartRateZoneDistribution`, `getPowerZoneDistribution`, and `getWorkoutTrackPoints`: when `TIME_ACTIVE` and `TIME_TOTAL` are null/absent, extract timestamps from `WorkoutSamplesDbHelper.TIME` (`"time"`), computing relative elapsed seconds from the initial sample (`t - t0`).
    * Fall back to sequential 1-second sample intervals if the `time` column is missing or unparseable.
  * **Defensive Degeneracy Handling in `ZoneDistributionCalculator.kt`**:
    * In `calculateHeartRateDistribution` and `calculatePowerDistribution`: detect degenerate or non-advancing timestamps (e.g. `validSamples.size > 1` but `validSamples.first().timeActiveSec == validSamples.last().timeActiveSec`). In such degenerate cases, fall back to treating each sample as a 1-second interval (`dt = 1L`).
  * **Unit Test Coverage**:
    * Comprehensive test cases in `ZoneDistributionCalculatorTest.kt` and `PowerZoneDistributionCalculatorTest.kt` verifying that degenerate 0-timestamp sample lists compute the full sample count duration rather than 1 second.
    * Unit verification for `WorkoutRepository` timestamp fallback parsing.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to database schema (no `ALTER TABLE` or migration for historical tables).
  * No modification to live recording in `TrackerService.java` (modern workouts already write `TIME_ACTIVE`).
  * No modification to UI rendering composables (`ZoneDistributionColumnChart.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-202` (*Aftermath: Heart Rate 5-Zone Distribution Vertical Column Chart Architecture*) and `REQ-UI-203` (*Aftermath: Cycling Power 5-Zone Distribution Vertical Column Chart Architecture*).
* **Historical Origin & Commit Trace**:
  * `REQ-UI-202` introduced in `ATT-1389`, refined in `ATT-1739`.
  * `REQ-UI-203` introduced in `ATT-1390`, refined in `ATT-1739`.
* **Root Reason for Existing Formulation**:
  * Item 3 specified querying `TIME_ACTIVE` and `TIME_TOTAL` from the workout samples table, assuming modern workouts where `ClockDevice` populates `TIME_ACTIVE`. It did not account for historical workouts (recorded prior to `TIME_ACTIVE` persistence) where `TIME_ACTIVE` is `NULL`.
* **Preservation of Core Invariants**:
  * Adding a fallback to SQLite's `time` column (`WorkoutSamplesDbHelper.TIME`) and sequential 1-second intervals fully preserves modern workout behavior (`TIME_ACTIVE` is used when present) while restoring 100% telemetry fidelity for historical workouts.
  * Mathematical zone boundaries ($Z_1 \dots Z_5$), clamping to $[0\text{s}, 5\text{s}]$, `Dispatchers.IO` confinement, and 9-language localization remain completely untouched.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Telemetry Timestamp Extraction in `WorkoutRepository.kt`
Introduce a helper or inline extraction strategy in `WorkoutRepository.kt` across `getHeartRateZoneDistribution`, `getPowerZoneDistribution`, and `getWorkoutTrackPoints`:
1. Check `timeActiveIdx` and `timeTotalIdx`.
2. Check `timeIdx = cursor.getColumnIndex(WorkoutSamplesDbHelper.TIME)`.
3. If `timeActive` is present and valid, use it directly.
4. Else if `timeTotal` is present and valid, use it directly.
5. Else if `time` string is present, parse with `java.time.LocalDateTime.parse(timeStr.replace(' ', 'T')).toEpochSecond(ZoneOffset.UTC)`:
   - Anchor `t0` to the first parsed timestamp.
   - For subsequent rows, `elapsedSec = (t - t0).coerceAtLeast(0L)`.
6. Else, fall back to sequential sample counter (`sampleIndex++`).

### 5.2 Degeneracy Immunity in `ZoneDistributionCalculator.kt`
In `calculateHeartRateDistribution` and `calculatePowerDistribution`:
1. Check if `validSamples.size > 1 && validSamples.first().timeActiveSec == validSamples.last().timeActiveSec`.
2. If all timestamps are identical (degenerate timestamps), assign `dt = 1L` for every sample, yielding a total duration equal to `validSamples.size`.
3. Otherwise, retain the standard delta-t accumulation clamped to `[0L, MAX_DELTA_T_SEC]`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features, tests, and modern workout calculations (`TIME_ACTIVE` precedence preserved).
  2. Single-thread database safety maintained on `Dispatchers.IO`.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  - The fix adds robust fallbacks for missing columns and degenerate inputs without altering database schemas or breaking existing contracts.
