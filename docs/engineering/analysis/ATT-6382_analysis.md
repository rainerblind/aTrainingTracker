# Stage 1 Analysis: ATT-6382 - MaxValueFilter & ExponentialSmoothingFilter: State Initialization, Zero-Bias Elimination & Alpha Validation (Teilpaket C)

**Ticket**: [ATT-6382](https://rainerblind.atlassian.net/browse/ATT-6382)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-6382`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Under approved complexity split (REQ-PRO-029 from ATT-6046), ATT-6382 addresses **Teilpaket C** (2 SP) for `MaxValueFilter` and `ExponentialSmoothingFilter` in `com.atrainingtracker.banalservice.filters`:
1. **0.0-Bias & Sub-Zero Failure in `MaxValueFilter`**: `mMaxValue` is hardcoded to `0` at instantiation and on `reset()`. For negative telemetry (e.g., sub-zero ambient temperature `-5°C` or below sea level elevation `-10m`), `value > 0.0` is false, causing `MaxValueFilter` to permanently report phantom `0` instead of the true maximum.
2. **Missing Alpha Bounds Validation in `ExponentialSmoothingFilter`**: The constructor accepts any `double alpha` without validation. Values outside $(0.0, 1.0]$ or `NaN` cause divergence, filter freezing, or numerical instability.
3. **0.0-Bias on First Sample & Broken `reseed()` in `ExponentialSmoothingFilter`**: Initializing `mFilteredValue = 0.0` artificially pulls the first sample towards zero (e.g., 100 W with $\alpha=0.5$ outputs 50 W). Calling `MyFilter.reseed(x)` yields $\alpha \cdot x$ instead of cleanly seeding baseline $x$, violating `REQ-FIL-012.5`.
4. **Field Immutability**: `mAlpha` in `ExponentialSmoothingFilter` is not marked `final`, missing safe publication guarantees across thread boundaries.

---

## 2. Root Cause Analysis & Independent Peer Review
* **Root Cause**:
  - `MaxValueFilter.java:30,48,56`: Absence of initialization tracking (`mIsInitialized`). The filter unconditionally compares against `mMaxValue = 0`, failing to seed from the first valid non-paused sample.
  - `ExponentialSmoothingFilter.java:35-39`: Constructor lacks range validation (`alpha <= 0.0 || alpha > 1.0 || Double.isNaN(alpha)`).
  - `ExponentialSmoothingFilter.java:32,65`: Unconditional weighted sum with initial `mFilteredValue = 0.0` without first-sample initialization gating.
* **Independent Peer Review & Verification (4-Augen-Prinzip — ATT-3534)**:
  - *Ticket Hypothesis Audit*: The Jira ticket hypothesized an "Unbounded LinkedList Memory Leak in MaxValueFilter with O(N) scan". Codebase audit disproves this: `MaxValueFilter` has no `LinkedList` or `mValues` collection and its methods are already `synchronized`. The true defects are the 0-bias/sub-zero failure, missing alpha validation, and broken reseed parity.
  - *Pause Contract Verification*: Pre-start / paused samples must continue to be ignored by `MaxValueFilter` (retaining `FilterResetAndPreStartTest.kt:192` returning `0`), but the first **non-paused** sample must cleanly seed `mMaxValue`.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals (Teilpaket C - 2 SP)**:
  - Add `mIsInitialized` tracking to `MaxValueFilter` so the first non-paused sample seeds `mMaxValue`.
  - Validate `0.0 < alpha <= 1.0` in `ExponentialSmoothingFilter` constructor (`IllegalArgumentException`).
  - Add `mIsInitialized` tracking to `ExponentialSmoothingFilter` so the first sample (and `reseed()`) seeds baseline cleanly.
  - Mark `mAlpha` as `final` for thread-safe publication.
  - Update `FilterResetAndPreStartTest.kt` (lines 50, 54, 58) and add comprehensive unit tests (`FilterInitializationAndBoundsTest.kt`).
* **Out-of-Scope Non-Goals**:
  - `ATT-6046` (Teilpaket A: ANT+ PCC handle leaks & field shadowing — closed).
  - `ATT-6381` (Teilpaket B: BTLERunSpeedDevice distance accumulation — closed).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Shard**: Living doc shard `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
* **Refined Requirements**: Introduces `REQ-FIL-015` (*Filter Initialization State Tracking, Alpha Validation & Zero-Bias Elimination*) and `TST-FIL-015`, refining `REQ-FIL-012.5` (`reseed()`) and `REQ-FIL-013` (pre-start pause isolation).
* **Historical Origin**: Baseline filter implementation (`commit 845818c2`, 2019) and pause gating (`commit d937073c`, 2026).
* **Root Reason for Existing Formulation**: Early filters assumed strictly positive telemetry and did not model uninitialized state prior to the first sample.
* **Preservation of Core Invariants**: Cumulative session isolation during pause (`REQ-FIL-013.3`), null safety (`REQ-FIL-011`), thread safety (`synchronized`), and zero regression on existing filters.

---

## 5. Architectural Strategy & High-Level Solution
* `MaxValueFilter`: Maintain `private boolean mIsInitialized = false`. In `newValue(Number)`: when `!isPaused()` and `value != null`, if `!mIsInitialized`, set `mMaxValue = value; mIsInitialized = true;` else update if `value.doubleValue() > mMaxValue.doubleValue()`. In `reset()`: `mIsInitialized = false; mMaxValue = 0;`.
* `ExponentialSmoothingFilter`: Validate `alpha` in constructor; mark `protected final double mAlpha;`. Maintain `private boolean mIsInitialized = false`. In `newValue(Number)`: if `value != null`, if `!mIsInitialized`, set `mFilteredValue = value.doubleValue(); mIsInitialized = true;` else apply smoothing. In `reset()`: `mIsInitialized = false; mFilteredValue = 0.0;`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: 100% backward compatibility for pre-start queries (`0.0`), zero pause leak into session stats, full clean-room unit test suite pass.
* **Risk Rating**: **LOW** (Isolated mathematical filter logic in `BANALService`; no DB schema or UI layout dependencies).
