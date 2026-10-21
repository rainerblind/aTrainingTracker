# Stage 1 Analysis: ATT-4016 - [Bug] [Medium] Period aggregation week and day boundaries wrong on DST transitions

**Ticket**: [ATT-4016](https://rainerblind.atlassian.net/browse/ATT-4016)  
**Parent Epic**: [ATT-67](https://rainerblind.atlassian.net/browse/ATT-67) (*Improve Periods/Stats*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4016`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Domain & Root Cause Analysis

* **Defect 1: Fixed-Second Period Boundaries Across DST Transitions (`PeriodsRepository.kt`)**:
  * In lines `611, 646, 648, 685, 761, 1059, 1060`, period ends and range queries assume uniform fixed seconds: `startS + 86399` (days) and `weekStart + 7 * 86400 - 1` (weeks).
  * *Spring-Forward (23h day = 82,800s, 167h week)*: `+86399` and `+7*86400-1` overshoot by 3,600s (1 hour) into Monday 00:00:00–00:59:59. Workouts completed early Monday morning are fetched and aggregated into the preceding Sunday/week.
  * *Fall-Back (25h day = 90,000s, 169h week)*: `+86399` and `+7*86400-1` terminate at 23:00 local time (1 hour early). Workouts between 23:00:00 and 23:59:59 on fall-back Sunday are completely omitted from Day and Week summaries.
* **Defect 2: Inconsistent Invalid Date Sentinel (`WorkoutDataMapper.kt:951`)**:
  * On `ParseException`, `formatDateTime()` returns `DateTimeResult(timestampS = 0L, localDateTime = LocalDateTime.now())`.
  * Grouping via `localDateTime` places corrupt records into today's period, while SQL range queries by `startTimeS` (0L) fail to find them, corrupting period sync and rollup integrity.
* **Defect 3: UTC Day Indexing in PMC Training Load (`PowerPhysiologyCalculator.kt:1027, 1037`)**:
  * `sample.timestampMs / 86_400_000L` indexes days strictly in UTC. In UTC+1/+2 (Europe/Berlin), rides at 00:30 local time are bucketed into the previous day; in UTC-5 (EST), evening rides at 20:00 are bucketed into the next day, distorting rest days, CTL, ATL, and TSB.
* **Date Formatting Audit**:
  * `getPeriodSortKey` (`yyyy-Www`, `yyyy-MM`, `yyyy-MM-dd`) is an invariant database sorting contract (`REQ-MIG-026`). `periodLabel` formatting (`DateTimeFormatter.ofLocalizedDate`, `MMMM yyyy`) is already locale-sensitive; sort key invariants must remain strictly preserved.

---

## 2. Call Site & Blast Radius Audit

| Component | Target File | Nature of Change & Impact |
| :--- | :--- | :--- |
| **Period Aggregation Engine** | `PeriodsRepository.kt` | Replace `+86399` and `+7*86400-1` with calendar-aware bounds: `plusDays(1).atStartOfDay(zone).toEpochSecond() - 1` and `plusWeeks(1).atStartOfDay(zone).toEpochSecond() - 1`. |
| **Workout DTO Mapper** | `WorkoutDataMapper.kt` | Align `DateTimeResult` invalid date fallback: derive `localDateTime` from `Instant.ofEpochSecond(0L)` (1970-01-01) instead of `LocalDateTime.now()`. |
| **Physiology Calculator** | `PowerPhysiologyCalculator.kt` | Add `zoneId: ZoneId = ZoneId.systemDefault()` overload to `computePerformanceManagementChart`; index `dayIdx` via `Instant.ofEpochMilli(ts).atZone(zoneId).toLocalDate().toEpochDay()`. |
| **Unit Tests** | `PeriodsAggregationTest.kt`, `PowerPhysiologyCalculatorTest.kt` | Add unit tests for 23h spring-forward and 25h fall-back workout boundary inclusion, invalid date epoch parity, and local-day PMC bucketing. |
| **Living Documentation** | `docs/requirements.md`, `docs/tests.md` | Synchronize `REQ-PER-011`, `REQ-PER-014`, `REQ-UI-401.1`, and test IDs `TST-PER-015`, `TST-UI-364`. |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Replace fixed-second day/week arithmetic with calendar-aware ZoneId boundary calculation in `PeriodsRepository.kt`.
  2. Normalize invalid date fallback to epoch `1970-01-01T00:00:00` in `WorkoutDataMapper.kt`.
  3. Align PMC day bucketing to athlete local timezone (`toEpochDay()`) in `PowerPhysiologyCalculator.kt`.
  4. Author targeted unit tests for DST transitions (Europe/Berlin), invalid date mapping, and PMC timezones.
* **Out-of-Scope Non-Goals**:
  1. No changes to `getPeriodSortKey` string contracts (`REQ-MIG-026`).
  2. No changes to database schema or SQLite column layouts.
  3. No changes to UI compose rendering in `PeriodsScreen.kt` or `PerformanceManagementChartCard.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Amends `REQ-PER-011` / `REQ-PER-014` (*Period Aggregation Boundary Stability*) and `REQ-UI-401.1` (*PMC Day Bucketing*).
* **Historical Origin & Commit Trace**:
  * `PeriodsRepository.kt`: Sprint 2026-40.16 (`ATT-346`, commit `7d82d0b8` — fixed-seconds `7*86400` and `86399`).
  * `WorkoutDataMapper.kt`: Sprint 2026-38.1 (`ATT-296`, commit `a562ca80` — `LocalDateTime.now()` fallback).
  * `PowerPhysiologyCalculator.kt`: Sprint 2026-42.7 (`ATT-3859`, commit `dc62bb3c` — UTC `timestampMs / 86_400_000L`).
* **Root Reason for Existing Formulation**: Fixed-second arithmetic simplified math on standard 24h days; `LocalDateTime.now()` satisfied non-null typing without evaluating timestamp parity; UTC PMC indexing assumed global day boundaries.
* **Preservation of Core Invariants**: `getPeriodSortKey` lexicographical ordering, O(1) self-healing sync (`REQ-MIG-026`), and PMC impulse-response EWMA equations ($\text{CTL}$, $\text{ATL}$, $\text{TSB}$) remain 100% preserved.

---

## 5. Architectural Strategy & System Invariants

* **Calendar-Aware Period Boundaries (`PeriodsRepository.kt`)**:
  * Calculate inclusive day end: `Instant.ofEpochSecond(dayStartS).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toEpochSecond() - 1`.
  * Calculate inclusive week end: `Instant.ofEpochSecond(weekStartS).atZone(zone).toLocalDate().plusWeeks(1).atStartOfDay(zone).toEpochSecond() - 1`.
  * Preserves correct 23h spring-forward (82,799s) and 25h fall-back (89,999s) ranges without 1-hour overlap or omission.
* **Sentinel Normalization (`WorkoutDataMapper.kt`)**:
  * Set `localDateTime = Instant.ofEpochSecond(0L).atZone(ZoneId.systemDefault()).toLocalDateTime()`, establishing 1:1 symmetry with `timestampS = 0L`.
* **Local Day PMC Bucketing (`PowerPhysiologyCalculator.kt`)**:
  * `val dayIdx = Instant.ofEpochMilli(sample.timestampMs).atZone(zoneId).toLocalDate().toEpochDay()`, preserving zero-rest-day accuracy across athlete timezones.
* **System Invariants**: Zero database schema migration; `getPeriodSortKey` string invariance; 100% clean-room test pass rate.
* **Risk Rating**: **MEDIUM** — Touches temporal aggregation across periods and physiology; fully mitigated by deterministic pure JVM unit tests in pinned timezones (`Europe/Berlin`, `America/New_York`, `UTC`).
