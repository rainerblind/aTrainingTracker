# Stage 3: Implementation Plan - ATT-4016: Period aggregation week and day boundaries wrong on DST transitions

**Ticket**: [ATT-4016](https://rainerblind.atlassian.net/browse/ATT-4016) | **Sub-task**: Pending Submission (`[Impl-Plan]`)  
**Parent Epic**: [ATT-67](https://rainerblind.atlassian.net/browse/ATT-67) (*Improve Periods/Stats*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4016`  
**Requirement Mapping**: `REQ-PER-015` (amending `REQ-PER-014`, `REQ-UI-401`) | **Test Mapping**: `TST-PER-021`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Architecture & Invariant Compliance

* **Temporal Architecture**: Enhance `PeriodsRepository`, `WorkoutDataMapper`, and `PowerPhysiologyCalculator` with calendar-aware timezone advancement:
  1. *Calendar-Aware Period Boundaries (`REQ-PER-015.1`)*: In `PeriodsRepository`, replace fixed-second offsets (`+86399` and `+7*86400-1`) with local timezone calendar advancement: `plusDays(1).atStartOfDay(zone).toEpochSecond() - 1` and `plusWeeks(1).atStartOfDay(zone).toEpochSecond() - 1`, cleanly resolving 23h (82,800s) spring-forward and 25h (90,000s) fall-back DST transitions without 1-hour overlap or omission.
  2. *Mapper Sentinel Symmetry & Zero-Timestamp Gating (`REQ-PER-015.2`)*: In `WorkoutDataMapper.formatDateTime`, on `ParseException`, return `LocalDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault())` matching `timestampS = 0L`. In `PeriodsRepository`, skip workouts with `startTimeS <= 0L` during aggregation to prevent period pollution.
  3. *Local-Day Training Load Bucketing (`REQ-PER-015.3`)*: In `PowerPhysiologyCalculator.computePerformanceManagementChart`, add `@JvmOverloads zoneId: ZoneId = ZoneId.systemDefault()` and index samples by `Instant.ofEpochMilli(sample.timestampMs).atZone(zoneId).toLocalDate().toEpochDay()`, eliminating UTC day shift for late/early workouts.
* **Core Invariants & Preserved Behavior (`REQ-PER-015.4`)**:
  1. *Database Schema Stability*: Zero SQLite schema changes; `PeriodSummaries.db` table layouts and `getPeriodSortKey` (`yyyy-MM-dd`, `yyyy-Www`, `yyyy-MM`, `yyyy`) remain 100% invariant (`REQ-MIG-026`).
  2. *Physiological Invariance*: Coggan EWMA impulse-response math ($\text{CTL}$, $\text{ATL}$, $\text{TSB}$, $\text{ACWR}$, $\text{RampRate}$) remains identical.
  3. *Backward Compatibility*: Default parameters (`ZoneId.systemDefault()`) maintain seamless compatibility for all existing callers.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 2. Atomic Implementation Steps

* **Step 1 — Calendar-Aware Day and Week Boundary Calculation in `PeriodsRepository.kt` (`REQ-PER-015.1`)**:
  * Implement private helper functions `calculateDayEnd(startS: Long, zone: ZoneId = ZoneId.systemDefault()): Long` and `calculateWeekEnd(startS: Long, zone: ZoneId = ZoneId.systemDefault()): Long` using `plusDays(1)` and `plusWeeks(1)` `.atStartOfDay(zone).toEpochSecond() - 1`.
  * Update `fetchWorkoutsInDay(startS)` to query range `[startS, calculateDayEnd(startS)]`.
  * Update `aggregateWorkoutsToDay` to set `endTimestampS = calculateDayEnd(startS)`.
  * Update `rollupDayToParents` and `rollupDaysToParentPeriods` to compute `weekEnd = calculateWeekEnd(weekStart)`.
  * Update `getAffectedPeriodRanges(ldt)` to use `calculateDayEnd(dayStart)` and `calculateWeekEnd(weekStart)`.
  * In period rebuild/sync loops (`rebuildPeriodsFromWorkouts`), add guard `if (w.startTimeS <= 0L) continue`.
* **Step 2 — Mapper Epoch-Zero Sentinel Symmetry in `WorkoutDataMapper.kt` (`REQ-PER-015.2`)**:
  * In `formatDateTime(cursor: Cursor)`: on `ParseException`, return `DateTimeResult(context.getString(R.string.invalid_date), "", 0L, java.time.LocalDateTime.ofInstant(java.time.Instant.EPOCH, java.time.ZoneId.systemDefault()))`.
* **Step 3 — Local-Timezone PMC Day Bucketing in `PowerPhysiologyCalculator.kt` (`REQ-PER-015.3`)**:
  * Update `computePerformanceManagementChart` signature: add `@JvmOverloads zoneId: ZoneId = ZoneId.systemDefault()`.
  * Replace `val dayIdx = sample.timestampMs / MILLIS_PER_DAY` with `Instant.ofEpochMilli(sample.timestampMs).atZone(zoneId).toLocalDate().toEpochDay()`.
  * Replace `nowDayIndex` calculation with `max(maxSampleDayIndex, Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().toEpochDay())`.
* **Step 4 — Targeted Unit Test Suite Authoring (`TST-PER-021.1`–`TST-PER-021.3`)**:
  * Author `PeriodsAggregationDstTest.kt`: verify 23h spring-forward Sunday (82,799s) and 25h fall-back Sunday (89,999s) boundary inclusion, 167h/169h week bounds in `Europe/Berlin`, and exclusion of invalid `startTimeS <= 0L` records.
  * Update `WorkoutDataMapperLapsTest.kt`: verify malformed date string yields `timestampS = 0L` and `Instant.EPOCH` local date-time.
  * Update `PowerPhysiologyCalculatorTest.kt`: verify late-night workout at 00:30 in `Europe/Berlin` is bucketed into the local calendar day rather than the prior UTC day.

---

## 3. File-Level Change Matrix

| File Path | Role | Nature of Modifications |
| :--- | :--- | :--- |
| `app/.../periodlist/PeriodsRepository.kt` | Production | Calendar-aware `calculateDayEnd`/`calculateWeekEnd` boundary math across DST; skip `startTimeS <= 0L`. |
| `app/.../aftermath/WorkoutDataMapper.kt` | Production | Align `formatDateTime` catch block fallback `localDateTime` to `Instant.EPOCH` sentinel. |
| `app/.../physiology/PowerPhysiologyCalculator.kt` | Production | Injectable `zoneId` parameter in `computePerformanceManagementChart`; bucket days via `toEpochDay()`. |
| `app/.../periodlist/PeriodsAggregationDstTest.kt` | Test | Unit tests for 23h/25h DST day/week boundaries, boundary workout inclusion, and invalid record filtering. |
| `app/.../aftermath/WorkoutDataMapperLapsTest.kt` | Test | Unit test verifying invalid date string yields epoch-zero sentinel. |
| `app/.../physiology/PowerPhysiologyCalculatorTest.kt` | Test | Unit test verifying PMC training load bucketing in non-UTC time zones (`Europe/Berlin`). |

---

## 4. Test Execution Strategy

* **Tier 1 — Per-Ticket Verification (Stages 4 & 5)**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataMapper*" \
    --tests "com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculatorTest"
  ```
* **Governance Verification**:
  ```bash
  python3 tools/verify_requirement_governance.py
  ```

---

## 5. Rollback & Risk Mitigation

* **Risk Level**: **MEDIUM**. Touches core temporal aggregation math in periods and athlete profile PMC.
* **Mitigation**: Deterministic pure JVM unit tests in pinned timezones (`Europe/Berlin`, `UTC`); backward-compatible default parameters prevent regressions in existing callers.
* **Rollback Plan**: Zero SQLite schema or persistent database changes; full rollback achievable via `git checkout` / `git revert` on `feature/ATT-4016`.
