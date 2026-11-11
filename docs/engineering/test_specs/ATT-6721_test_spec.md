# Stage 2 Requirement & Test Specification: ATT-6721 - Calendar-Day X-Axis, Preserve Non-HRV Loads, Readiness-Colored 7d Curve

**Ticket**: [ATT-6721](https://rainerblind.atlassian.net/browse/ATT-6721)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PER-033` (`REQ-PER-033.1`, `REQ-PER-033.2`, `REQ-PER-033.3`, `REQ-PER-033.6`)  
**Test Spec ID**: `TST-PER-041` (`TST-PER-041.1`..`TST-PER-041.10`)  
**Branch**: `feature/ATT-6721` | **Author**: AI Agent 2 (Reviewer / Specifier, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PER-033)

### 1.1 Problem Statement & User Scope
* In `ATT-3557` (commit `4df73e2f`), `HrvReadinessTrendCard.kt` mapped the X-axis via ordinal list indices, collapsing multi-day recording gaps.
* Workout days without a morning HRV reading are dropped in `HrvReadinessTrendMath.kt`, hiding daily training-load bars.
* The 7-day rolling curve renders as a single monotone `primary` path rather than reflecting daily `ReadinessStatus` colors.
* `_selectedHrvPosture` eagerly defaults to `SUPINE`, overriding an athlete's primary posture (e.g. 50 `SEATED` vs 1 `SUPINE`).
* `PeriodsViewModel.kt` reads a cold-start one-shot `allWorkouts.value` snapshot instead of reactively collecting `workoutRepo.allWorkouts`.
* UI polish: Binary FilterChips violate `design_guidelines.md` §1.1; bars lack Rule 69 sharp bottoms; tooltips hardcode `Locale.US` and `Color.White`/`Color.Black`.

### 1.2 Functional & Architectural Requirements
1. **Calendar Epoch X-Axis (`HrvReadinessTrendMath.kt`, `HrvReadinessTrendCard.kt`)**: Map chart X-axis using `LocalDate.toEpochDay()`, preserving proportional day intervals across recording gaps.
2. **Daily Training Load Preservation (`HrvReadinessTrendMath.kt`)**: Retain workout days lacking morning HRV readings across the active date range with `rawValue = null`, preserving daily training-load bars.
3. **Status-Colored 7-Day Rolling Curve (`HrvReadinessTrendCard.kt`)**: Segment and color-code the 7-day rolling curve ($\mu_{7d}$) according to daily `ReadinessStatus` colors (`TTColor`).
4. **Lazy Posture Default & Reactive Hydration (`PeriodsViewModel.kt`)**: Initialize `_selectedHrvPosture` to `null` until `resolveAvailablePostures` evaluates the highest count posture; reactively collect `workoutRepo.allWorkouts`.
5. **Design System & Localization Polish (`HrvReadinessTrendCard.kt`)**: Use `SingleChoiceSegmentedButtonRow`, Rule 69 sharp-bottom bars (`CornerRadius(2.dp, 2.dp)` top, `CornerRadius.Zero` bottom), locale-aware date/number formatting, and Material 3 `inverseSurface`/`inverseOnSurface` tooltip tokens.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (Calendar-Day Scaling & Load Retention)**: *Given* multi-day gaps between morning HRV readings and workouts on non-HRV days, *When* rendering the trend chart, *Then* points scale proportionally on calendar days and non-HRV workout load bars remain visible.
* **AC 2 (Status-Colored 7d Curve)**: *Given* rolling 7d average points, *When* drawing the curve, *Then* segments reflect each day's `ReadinessStatus` color (`OPTIMAL`, `FATIGUED_LOW`, `ABOVE_NORMAL`, `CALIBRATING`).
* **AC 3 (Default Posture Resolution)**: *Given* 50 `SEATED` readings and 1 `SUPINE` reading, *When* initializing `PeriodsViewModel`, *Then* initial posture defaults to `SEATED`.
* **AC 4 (Reactive Load Collection)**: *Given* workouts hydrating asynchronously on cold start, *When* `workoutRepo.allWorkouts` emits, *Then* `refreshHrvTrend` updates trend series with daily training loads.
* **AC 5 (Design Polish)**: *Given* metric mode selector and training-load bars, *When* rendered, *Then* `SingleChoiceSegmentedButtonRow` and Rule 69 sharp-bottom bars are used.

### 1.4 System Invariants
* Zero schema changes to `HrvReadiness.db` or `WorkoutSummaries.db`.
* Strict posture isolation (zero cross-posture contamination in SWC corridor).
* Gesture disambiguation (`ChartGestureDisambiguator`) preserved; 100% clean-room test pass rate.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-PER-033` (`REQ-PER-033.1`, `REQ-PER-033.2`, `REQ-PER-033.3`, `REQ-PER-033.6`). Targets `HrvReadinessTrendMath.kt`, `HrvReadinessTrendCard.kt`, and `PeriodsViewModel.kt`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-3557` (commit `4df73e2f`); refined in `ATT-6721` under Epic `ATT-3539` (*Heart Rate Variability*).
3. *Root Reason for Existing Formulation*: Ordinal indexing was a prototype simplification; non-HRV workouts were dropped during HRV map iteration; single monotone path was drawn because segment status splitting was deferred.
4. *Preservation of Core Invariants*: Posture isolation in SWC math, gesture disambiguation, Rule 48 outlier Y-clamping, and 100% 9-language translation parity are strictly preserved.

---

## 3. Test Specification (TST-PER-041: Longitudinal HRV Readiness Trend Suite)

### Test Case 1: Calendar-Day Epoch X-Axis & Non-HRV Load Retention (`TST-PER-041.7`)
* **Scope**: `HrvReadinessTrendMathTest.kt`. Verify points spaced by `LocalDate.toEpochDay()`, touch projection via epoch days, and non-HRV workout days retained with `rawValue = null` and valid `trainingLoad`.

### Test Case 2: Status-Colored 7-Day Rolling Curve Segments (`TST-PER-041.8`)
* **Scope**: `HrvReadinessTrendCard.kt`. Verify rolling curve renders segmented paths colored by `ReadinessStatus` (`OPTIMAL`, `FATIGUED_LOW`, `ABOVE_NORMAL`, `CALIBRATING`).

### Test Case 3: Default Posture Selection & Reactive Workouts Hydration (`TST-PER-041.9`)
* **Scope**: `PeriodsViewModelTest.kt`. Verify `_selectedHrvPosture` initializes to `null`, resolves to `SEATED` when SEATED count exceeds SUPINE count, and reactively collects `workoutRepo.allWorkouts`.

### Test Case 4: 9-Language Localization & Specifier Audit (`TST-PER-041.5`)
* **Scope**: `TranslationParityTest.kt`. Verify 100% parity across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with valid format specifiers.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-PER-041.6`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`. Verify 100% pass rate.

---

## 4. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PER-041.7]` | Unit | `HrvReadinessTrendMathTest`: Calendar epoch X-axis & non-HRV load retention | `REQ-PER-033.3`, `REQ-PER-033.6` | Specified |
| `[TST-PER-041.8]` | Unit / Compose | `HrvReadinessTrendCard`: Status-colored 7d rolling curve segments | `REQ-PER-033.2` | Specified |
| `[TST-PER-041.9]` | Unit / VM | `PeriodsViewModelTest`: Default posture resolution & reactive hydration | `REQ-PER-033.1` | Specified |
| `[TST-PER-041.5]` | Localization | `TranslationParityTest`: 9-language translation parity | `REQ-PER-033.6`, `REQ-UI-106` | Specified |
| `[TST-PER-041.6]` | Regression | Tier 1 test execution across modified packages | `REQ-PRO-001` | Specified |
