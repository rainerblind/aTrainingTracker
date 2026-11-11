# Stage 1 Analysis: ATT-6724 - [Verbesserung] [Medium] Compute 30d Cardio-Power rolling trend before range slicing, refresh samples reactively, and align with Fitness & Physiology hub

**Ticket**: [ATT-6724](https://rainerblind.atlassian.net/browse/ATT-6724)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-to-Heart-Rate System Identification*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6724`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
In Sprint 2026-46.1 Joint Review of ATT-3906 (`178589a7`), longitudinal season trends for Fresh HR@200W, $\tau$, and $1/K$ were introduced in `CardioPowerTrendsCard`. Four defects/gaps were identified:
1. **Left-Edge Rolling Truncation Bias**: `AthleteProfileViewModel.kt:230-234` filters samples by time range *before* computing the 30-day moving average (`filterPointsByTimeRange` then `computeRollingTrend`). Workouts in $[t_{\text{cutoff}} - 30\text{d}, t_{\text{cutoff}})$ are discarded, biasing boundary points.
2. **Static Sample Ingestion**: `AthleteProfileViewModel.kt:294-297` loads samples only once in `init`. Newly recorded, imported, backfilled, or deleted workouts do not refresh without app recreation.
3. **SQLite UTC Timestamp Shift**: `WorkoutSummariesDatabaseManager.java:1981` uses `strftime('%s', w.time_start) * 1000`. SQLite assumes UTC, shifting timestamps relative to local device time and breaking parity with `WorkoutDataMapper`.
4. **Hub UX Gaps**: `CardioPowerTrendsCard.kt` lacks `1M` range pill (`MetricsTimeRange.ONE_MONTH`), intermediate X-axis date ticks, and touch scrubbing (`ChartGestureDisambiguator`) with vertical cursor and value tooltip.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * *Rolling Trend Order*: Codebase audit confirms `allPts` is sliced prior to `computeRollingTrend` in `AthleteProfileViewModel.kt:231-232`. Computing `allRollingPts = computeRollingTrend(allPts, 30)` first and then slicing both `allPts` and `allRollingPts` using `filterPointsByTimeRange` ensures full 30-day rolling history for every visible point.
  * *Reactive Refresh*: `WorkoutRepository.allWorkouts` (`StateFlow<List<WorkoutData>>`) emits on every session change. Collecting `allWorkouts` in `ViewModel.init` guarantees automated refresh of `cardioTrendSamples` and `workoutLoadSamples`.
  * *Local Timezone Parsing*: In `WorkoutSummariesDatabaseManager.java:1981`, selecting `w.timeStart` directly and parsing in Java with `SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)` preserves local timezone parity.
  * *Hub UI Parity*: `CardioPowerTrendsCard.kt:160-165` currently offers only `3M, 6M, 1Y, All`. Adding `MetricsTimeRange.ONE_MONTH` (`R.string.athlete_profile_range_1m`), intermediate date ticks, and `pointerInput` with `ChartGestureDisambiguator` delivers full parity with `HrvReadinessTrendCard.kt`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Correct rolling trend order of operations in `AthleteProfileViewModel.kt`.
  * Wire reactive collection on `workoutRepository.allWorkouts` in `AthleteProfileViewModel.init`.
  * Replace SQLite `strftime` with Java `SimpleDateFormat` local timezone parsing in `WorkoutSummariesDatabaseManager.java`.
  * Add `1M` pill, intermediate date ticks, and touch scrubbing in `CardioPowerTrendsCard.kt`.
  * Maintain living documentation (`REQ-REV-011`, `TST-REV-012`) and test coverage.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to 1st-order Output-Error dynamical engine (`PowerHrDynamicsCalculator.kt`).
  * No database schema migrations or SQLite table alterations.
  * No navigation drawer alterations (governed by ATT-6720).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-REV-011` (*Cardio-Power Longitudinal Fitness Trends in Athlete Profile*), targeting `WorkoutSummariesDatabaseManager.java`, `AthleteProfileViewModel.kt`, and `CardioPowerTrendsCard.kt`.
* **Historical Origin & Commit Trace**: Added in `ATT-3906` (commit `178589a7`, Sprint 2026-46.1).
* **Root Reason for Existing Formulation**: Rapid prototype in ATT-3906 established preliminary 3M/6M/1Y/All ranges and static initial loading. Slicing before rolling average was an implementation oversight, and `strftime('%s')` was adopted as quick epoch conversion.
* **Preservation of Core Invariants**: $R^2 \ge 0.50$ model quality threshold, Tukey IQR domain clamping, 30-day window width, and 9-locale translation parity are fully preserved while eliminating boundary truncation and UTC drift.

---

## 5. Architectural Strategy & High-Level Solution
| Component | Change Summary |
|---|---|
| `AthleteProfileViewModel.kt` | Full-history `computeRollingTrend(allPts, 30)` prior to `filterPointsByTimeRange`; collect `workoutRepository.allWorkouts`. |
| `WorkoutSummariesDatabaseManager.java` | Query `w.timeStart AS time_start_str` and parse with `SimpleDateFormat` in local timezone. |
| `CardioPowerTrendsCard.kt` | Add `1M` selector pill, intermediate X-axis date ticks, and `ChartGestureDisambiguator` scrubbing with cursor/tooltip. |
| Unit Tests | Verify pre-slicing boundary continuity, reactive reload, timezone parsing, and UI interaction. |

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in existing athlete profile metrics, CP/W', or PMC models.
  2. Database I/O confined to background IO dispatchers.
  3. Parent ticket Human Decision Gate strictly enforced.
* **Risk Rating**: **LOW** (Targeted UI calculation order, reactive subscription, and date parsing; zero schema alterations or cross-module coupling).
