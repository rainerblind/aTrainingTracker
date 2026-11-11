# Stage 2 Requirement & Test Specification: ATT-6722 - Lazy Cardio-Power Backfill, Physiological Bounds & Filter Warm-Up, and Accordion Subtitle

**Ticket**: [ATT-6722](https://rainerblind.atlassian.net/browse/ATT-6722)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-to-Heart-Rate System Identification*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6722` | **Author**: AI Agent 2 (Reviewer / Specifier, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-REV-009)

### 1.1 Problem Statement & User Scope
* Historical workouts lack cardio-power dynamics because `getOrComputeCardioPowerDynamics` has no callers and performs unindexed full queries without ineligible caching.
* `PowerHrDynamicsCalculator.kt` lacks filter pre-warming ($t=0$ to $\text{startIndex}-1$), admits non-physiological parameters ($K \notin [0.01, 0.45]$, $\text{HR}_0 \notin [30, 125]$, $R^2 < 0.25$, active pedaling $< 15\text{ min}$), and ignores variable $\Delta t$ timestamps.
* `AdvancedTuningAccordion.kt` omits `enableCardioPowerDynamics` from its collapsed summary subtitle.

### 1.2 Functional & Architectural Requirements
1. **Optimized Backfill & Ineligible Cache (`WorkoutRepository.kt`)**: Project `[TIME_ACTIVE_s, TIME_TOTAL_s, TIME_s, POWER, HR]` ordered by `_id ASC`, maintain in-memory `ineligibleCardioPowerWorkoutIds`, update `WorkoutData` reactively, and add `backfillHistoricalCardioPowerDynamics(limit = 20)` called by `WorkoutSummariesViewModel`.
2. **Filter State Pre-Warm & True $\Delta t$ (`PowerHrDynamicsCalculator.kt`)**: Pre-warm discrete filter $x_k(\tau) = e^{-\Delta t/\tau} x_{k-1}(\tau) + (1 - e^{-\Delta t/\tau}) P_k$ across $t \in [0, \text{startIndex}-1]$ and evaluate timestamps $\Delta t_i = (t_i - t_{i-1}).\text{coerceIn}(1\text{L}, 60\text{L})\text{.toDouble}()$.
3. **Physiological Plausibility Gates (`PowerHrDynamicsCalculator.kt`)**: Reject rides (return `null`) if active pedaling ($P > 0$) $< 15\text{ min}$ ($900\text{s}$), $K \notin [0.01, 0.45]$, $\text{HR}_0 \notin [30, 125]$, or $R^2_{\text{start}} < 0.25$.
4. **Accordion Subtitle Parity (`AdvancedTuningAccordion.kt`)**: Add `enableCardioPowerDynamics: Boolean = false` to `formatPhysiologyModelsSubtitle` and append `R.string.cardio_power_domain_label` when true.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (Filter Pre-Warm & Boundaries)**: *Given* power data starting with coasting or high surges before min 8, *When* fitting calibration windows, *Then* filter pre-warming eliminates transient step bias and conforms to continuous ODE dynamics.
* **AC 2 (Physiological & Coupling Gates)**: *Given* non-qualifying telemetry ($K \notin [0.01, 0.45]$, $\text{HR}_0 \notin [30, 125]$, $R^2 < 0.25$, or active pedaling $< 15\text{ min}$), *When* calculating dynamics, *Then* calculation safely returns `null`.
* **AC 3 (Optimized Lazy Backfill & Cache)**: *Given* historical rides, *When* viewing details or running backfill, *Then* projected query computes missing dynamics, populates memory and DB, and ineligible rides are cached to prevent repeated DB reads.
* **AC 4 (Accordion Summary Parity)**: *Given* `enableCardioPowerDynamics == true`, *When* viewing collapsed Physiology Models accordion, *Then* subtitle appends localized "Cardio-Power" across all 9 locales.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-REV-009` (`REQ-REV-009.1`, `REQ-REV-009.2`, `REQ-REV-009.4`), targeting `WorkoutRepository.kt`, `PowerHrDynamicsCalculator.kt`, and `AdvancedTuningAccordion.kt`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-3904` (commit `1f70fb15`).
3. *Root Reason for Existing Formulation*: Foundation calculation initially drafted without caller hooks or ineligible caching; ODE solver assumed uniform 1-Hz sampling and seeded at `startIndex` without warm-up propagation.
4. *Preservation of Core Invariants*: Bounded IO without UI blocking, pure mathematical determinism, Java caller compatibility (`@JvmOverloads`), and 100% 9-language translation parity are strictly preserved.

---

## 3. Test Specification (TST-REV-010: 1st-Order Dynamical Power-HR Suite)

### Test Case 1: Filter Warm-Up Continuity (`TST-REV-010.6`)
* **Scope**: `PowerHrDynamicsCalculatorTest`. Verify pre-warming $x_k(\tau)$ from $t = 0$ to $\text{startIndex} - 1$ eliminates warm-up boundary transients and matches analytical continuous response.

### Test Case 2: Physiological Plausibility & Gate Enforcement (`TST-REV-010.7`)
* **Scope**: `PowerHrDynamicsCalculatorTest`. Verify rejection when $K \notin [0.01, 0.45]$, $\text{HR}_0 \notin [30, 125]$, $R^2_{\text{start}} < 0.25$, or active pedaling $< 900\text{s}$, and acceptance for valid physiology.

### Test Case 3: Lazy Backfill Query & Ineligible Cache (`TST-REV-010.8`)
* **Scope**: `WorkoutRepositoryCardioPowerBackfillTest`. Verify projected query `ORDER BY _id ASC`, ineligible ID caching, in-memory `WorkoutData` updates, and bounded backfill limit ($N \le 20$).

### Test Case 4: Accordion Subtitle Formatting & 9-Locale Parity (`TST-REV-010.9`)
* **Scope**: `AdvancedTuningAccordionTest`. Verify `formatPhysiologyModelsSubtitle` appends localized `R.string.cardio_power_domain_label` when active across all 9 languages.

---

## 4. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-REV-010.6]` | Unit | `PowerHrDynamicsCalculatorTest`: Filter pre-warming continuity from $t=0$ | `REQ-REV-009.1e` | Specified |
| `[TST-REV-010.7]` | Unit | `PowerHrDynamicsCalculatorTest`: Plausibility gates ($K, \text{HR}_0, R^2, \text{pedaling}$) | `REQ-REV-009.1d` | Specified |
| `[TST-REV-010.8]` | Unit / Integration | `WorkoutRepositoryCardioPowerBackfillTest`: Projected query, caching & backfill | `REQ-REV-009.2c` | Specified |
| `[TST-REV-010.9]` | Unit / UI | `AdvancedTuningAccordionTest`: Accordion subtitle string formatting parity | `REQ-REV-009.4c` | Specified |
