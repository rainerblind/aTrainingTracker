# Stage 1 Analysis: ATT-6721 - Calendar-Day X-Axis, Daily Training-Load Continuity & Readiness-Colored 7d Curve

**Ticket**: [ATT-6721](https://rainerblind.atlassian.net/browse/ATT-6721)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6721` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
* In `ATT-3557` (commit `4df73e2f`), `HrvReadinessTrendCard.kt` mapped the X-axis via ordinal list indices (`indexToX(i) = i / (points.size - 1)`). Irregular recording gaps (e.g., Nov 1 to Nov 5) collapse adjacent, distorting temporal progression.
* Workout days lacking a morning HRV reading are dropped in `HrvReadinessTrendMath.kt:214` (`dailyMap[dateStr] ?: continue`), hiding daily workout training-load bars (`dailyTrainingLoads`).
* The 7-day rolling curve ($\mu_{7d}$) renders as a single monotone `primary` path rather than reflecting daily `ReadinessStatus` colors (`OPTIMAL`, `FATIGUED_LOW`, `ABOVE_NORMAL`, `CALIBRATING`) per `REQ-PER-033.2`.
* `_selectedHrvPosture` initializes to `HrvPosture.SUPINE`, causing a single `SUPINE` entry to preempt 50 `SEATED` entries because `availablePostures.contains(currentSelected)` evaluates true before `defaultPosture` takes effect.
* `PeriodsViewModel.kt:422` reads one-shot `workoutRepo.allWorkouts.value` snapshot during cold start without reactive subscription.
* UI polish: Binary `FilterChip` pair violates `design_guidelines.md` §1.1; load bars use 4-corner rounding rather than Rule 69 sharp bottoms (§2.7); tooltip and axis formatters hardcode `Locale.US` and `Color.White`/`Color.Black`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Call Site & Code Audit**:
  * `HrvReadinessTrendCard.kt:371-375`: `indexToX(i)` divides ordinal index by `points.size - 1`, disregarding calendar dates.
  * `HrvReadinessTrendMath.kt:214-233`: Iterates strictly over `dailyMap.keys.sorted()`. Non-HRV workout dates in `dailyTrainingLoads` are skipped.
  * `HrvReadinessTrendCard.kt:476-492`: Single `Path` drawn with `rollingCurveColor = MaterialTheme.colorScheme.primary`.
  * `PeriodsViewModel.kt:82, 439-447`: `_selectedHrvPosture = MutableStateFlow(HrvPosture.SUPINE)` overrides `defaultPosture`.
  * `PeriodsViewModel.kt:422`: `workoutRepo.allWorkouts.value` returns empty list on cold start before background loading completes.
  * `HrvReadinessTrendCard.kt:416-421, 547-571`: 4-corner `drawRoundRect`, `Locale.US`, and hardcoded `Color.White`/`Color.Black`.
* **Independent Peer Review & Verification**:
  * *Calendar-Day Spacing*: Mapping $X$ via `(epochDay - minEpochDay).toFloat() / daySpan.toFloat()` preserves true proportional day spacing.
  * *Training-Load Retention*: Retaining candidate dates from `dailyMap.keys` and bounded `dailyTrainingLoads.keys` ensures non-HRV workouts display their load bar with `rawValue = null`.
  * *Reactive Hydration*: Subscribing to `workoutRepo.allWorkouts` via `collect` in `viewModelScope` updates the trend once workouts hydrate asynchronously.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Replace ordinal index mapping in `HrvReadinessTrendMath.findNearestPoint` and `HrvReadinessTrendCard.kt` with `LocalDate.toEpochDay()`.
  2. Retain non-HRV workout load points across the trend series range so training-load bars render on workout days without HRV readings.
  3. Color-code 7-day rolling curve segments according to `ReadinessStatus` colors (`TTColor`).
  4. Initialize `_selectedHrvPosture` as `null` until `resolveAvailablePostures` selects `defaultPosture` (most frequent valid posture).
  5. Subscribe reactively to `workoutRepo.allWorkouts` in `PeriodsViewModel`.
  6. Design polish: `SingleChoiceSegmentedButtonRow`, Rule 69 sharp-bottom bars (`CornerRadius(2.dp, 2.dp)` top, `CornerRadius.Zero` bottom), locale-aware formatting, and Material 3 `inverseSurface`/`inverseOnSurface` tooltip tokens.
* **Out-of-Scope Non-Goals**:
  1. Relocating `HrvReadinessTrendCard` to Fitness & Physiology hub (scoped to `ATT-6720`).
  2. Modifying SQLite schema or `HrvReadinessRepository` database queries.
  3. Changing `HrvReadinessCalculator` baseline algorithm.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-033` (`REQ-PER-033.1`, `REQ-PER-033.2`, `REQ-PER-033.3`, `REQ-PER-033.6`). Targets `HrvReadinessTrendMath.kt`, `HrvReadinessTrendCard.kt`, and `PeriodsViewModel.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-3557` (commit `4df73e2f`).
* **Root Reason for Existing Formulation**: Ordinal indexing was a prototype simplification; non-HRV workout days were dropped because `points` only tracked morning HRV measurements; single monotone path was drawn because segment status splitting was deferred; `_selectedHrvPosture` had eager fallback.
* **Preservation of Core Invariants**:
  1. Posture-isolated baseline calculation preserved (zero cross-posture contamination).
  2. Gesture disambiguation (`ChartGestureDisambiguator`) preserved.
  3. Outlier-resilient Y-clamping (Rule 48) preserved.

---

## 5. Architectural Strategy & High-Level Solution
* **Calendar Epoch X-Axis (`HrvReadinessTrendMath.kt` & `HrvReadinessTrendCard.kt`)**: Compute `minEpochDay` and `maxEpochDay` from first/last dates; `dateToX(dateIso) = leftPad + plotW * ((epochDay - minEpochDay).toFloat() / daySpan.toFloat())`. `findNearestPoint` finds closest point by epoch day.
* **Preserve Non-HRV Workouts (`HrvReadinessTrendMath.kt`)**: Include candidate dates from `dailyMap.keys` and `dailyTrainingLoads.keys` within `[minHrvDate, maxHrvDate]`. For workout-only days, set `rawValue = null`, retain `trainingLoad`, and compute rolling $\mu_{7d}$ / SWC from `dailyMap`.
* **Readiness-Colored Curve Segments (`HrvReadinessTrendCard.kt`)**: Draw rolling line segments between adjacent points using `getReadinessColor(p.status)`.
* **Reactive Posture & Workouts (`PeriodsViewModel.kt`)**: Set `_selectedHrvPosture = MutableStateFlow<HrvPosture?>(null)`; collect `workoutRepo.allWorkouts` to trigger `refreshHrvTrend()`.
* **Design & Localization Polish (`HrvReadinessTrendCard.kt`)**: Use `SingleChoiceSegmentedButtonRow`, `RoundRect` with sharp bottoms on load bars, `Locale.getDefault()`, and `inverseSurface` tokens.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in existing unit tests (`HrvReadinessTrendMathTest`, `PeriodsViewModelTest`).
  2. Thread safety via `ioDispatcher` in `PeriodsViewModel`.
  3. 100% 9-language translation parity maintained.
* **Risk Rating**: **LOW** — Pure presentation and UI calculation fixes with zero database schema, background service, or IPC impact (2 SP).
