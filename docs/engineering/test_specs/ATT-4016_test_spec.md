# Stage 2: Requirement & Test Specification - ATT-4016: Period aggregation week and day boundaries wrong on DST transitions

**Ticket**: [ATT-4016](https://rainerblind.atlassian.net/browse/ATT-4016) | **Sprint**: `2026-43.5` | **Release**: `V4.9.40` | **Branch**: `feature/ATT-4016`  
**Requirement Mapping**: `REQ-PER-015` (amending `REQ-PER-014`, `REQ-UI-401`) | **Test Spec ID**: `TST-PER-021`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-10-21  

---

## 1. Requirement Traceability Matrix

| Requirement | Test ID | Scope | Target Component / Method | Verification Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PER-015.1` | `TST-PER-021.1` | Unit | `PeriodsRepository.kt` (`fetchWorkoutsInDay`, `rollupDayToParents`, `rollupDaysToParentPeriods`) | Specified |
| `REQ-PER-015.2` | `TST-PER-021.2` | Unit | `WorkoutDataMapper.kt` (`formatDateTime`), `PeriodsRepository.kt` (`rebuildPeriodsFromWorkouts`) | Specified |
| `REQ-PER-015.3` | `TST-PER-021.3` | Unit | `PowerPhysiologyCalculator.kt` (`computePerformanceManagementChart`) | Specified |
| `REQ-PER-015.4` | `TST-PER-021.4` | Regression | Full clean-room test suite (`./gradlew testDebugUnitTest`) | Specified |

---

## 2. Formal Requirement Specifications

* **REQ-PER-015.1 (Calendar-Aware Local Period Boundaries)**: `PeriodsRepository` SHALL calculate day and week end timestamps using local timezone calendar advancement: `plusDays(1).atStartOfDay(zone).toEpochSecond() - 1` and `plusWeeks(1).atStartOfDay(zone).toEpochSecond() - 1`, strictly preserving 23h (82,799s) and 25h (89,999s) DST transition boundaries without 1-hour overlap or omission. `getPeriodSortKey` string contracts MUST remain invariant.
* **REQ-PER-015.2 (Mapper Sentinel Symmetry & Invalid Timestamp Gating)**: `WorkoutDataMapper.formatDateTime` SHALL return `LocalDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault())` on parse exceptions matching `timestampS = 0L`. `PeriodsRepository` SHALL filter out invalid records (`timestampS <= 0L`) during rebuilds.
* **REQ-PER-015.3 (Local-Timezone Day Bucketing in PMC)**: `PowerPhysiologyCalculator.computePerformanceManagementChart` SHALL bucket sample training load by athlete local calendar day using `Instant.ofEpochMilli(sample.timestampMs).atZone(zoneId).toLocalDate().toEpochDay()` with `@JvmOverloads zoneId: ZoneId = ZoneId.systemDefault()`.
* **REQ-PER-015.4 (Core System Invariants)**: Database schemas remain invariant; EWMA equations ($\text{CTL}$, $\text{ATL}$, $\text{TSB}$) remain unchanged; zero production code modification prior to Gate 3 PASS.

---

## 3. Formal Test Case Specifications (Given / When / Then)

* **TST-PER-021.1: Calendar-Aware DST Transition Period Aggregations (`PeriodsAggregationDstTest.kt`)**:
  * *Given* workouts recorded in `Europe/Berlin` during 23h spring-forward Sunday (2026-03-29, 82,800s) and Monday 00:15:00,
  * *When* `fetchWorkoutsInDay` and `rollupDayToParents` calculate Day and Week summaries,
  * *Then* the Sunday day summary ends at `startS + 82799L` and week ends at `weekStart + 167*3600 - 1`, excluding the Monday workout.
  * *Given* workouts recorded during 25h fall-back Sunday (2026-10-25, 90,000s) at 23:30:00 local time,
  * *When* `fetchWorkoutsInDay` executes,
  * *Then* the day boundary ends at `startS + 89999L` and includes the 23:30 workout.
* **TST-PER-021.2: Sentinel Epoch Parity & Invalid Timestamp Exclusion (`WorkoutDataMapperTest.kt`)**:
  * *Given* an invalid or blank date string parsed by `WorkoutDataMapper.formatDateTime`,
  * *When* `ParseException` is handled,
  * *Then* `result.timestampS == 0L` and `result.localDateTime == LocalDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault())`.
  * *Given* a workout with `timestampS <= 0L`,
  * *When* `PeriodsRepository.rebuildPeriodsFromWorkouts` aggregates periods,
  * *Then* the record is ignored and does not roll up into today's summaries.
* **TST-PER-021.3: Timezone-Aware PMC Day Bucketing (`PowerPhysiologyCalculatorTest.kt`)**:
  * *Given* workout sample recorded at 00:30 local time in `Europe/Berlin` (UTC+2 in summer, 22:30 UTC),
  * *When* `computePerformanceManagementChart` evaluates load with `zoneId = ZoneId.of("Europe/Berlin")`,
  * *Then* load is assigned to the local calendar day `toEpochDay()` rather than previous UTC day.

---

## 4. Edge Case & Regression Matrix

| Edge Case / Scenario | Risk | Mitigation & Assertion |
| :--- | :--- | :--- |
| **Leap Year & Month Transitions** | Misaligned boundary | Verified via `plusDays(1).atStartOfDay(zone) - 1` across Feb 28/29. |
| **Negative / Epoch Zero Timestamp** | Period pollution | Guarded by `if (it.startTimestampS <= 0L) continue` in period rebuild loops. |
| **Cross-Day Workout Crossing DST** | Start vs End ambiguity | Periods bucket workouts strictly by start timestamp (`startTimestampS`). |
| **Null / Unspecified ZoneId** | Caller crash | Default parameter `@JvmOverloads zoneId: ZoneId = ZoneId.systemDefault()`. |

---

## 5. Localization & Resource Parity Matrix

* **String Resource Impact**: Zero UI string changes required (internal repository aggregation, mapper sentinel alignment, and physiology math).
* **Parity Status**: `TranslationParityTest` 100% verified across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
