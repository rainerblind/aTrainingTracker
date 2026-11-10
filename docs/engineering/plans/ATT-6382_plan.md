# Stage 3 Implementation Plan: ATT-6382 - MaxValueFilter & ExponentialSmoothingFilter: State Initialization, Zero-Bias Elimination & Alpha Validation (Teilpaket C)

**Ticket**: [ATT-6382](https://rainerblind.atlassian.net/browse/ATT-6382)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-FIL-015` (`REQ-FIL-015.1`..`REQ-FIL-015.4`)  
**Test Spec Mapping**: `TST-FIL-015` (`TST-FIL-015.1`..`TST-FIL-015.7`)  
**Branch**: `feature/ATT-6382`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Technical Architecture & SWE.2 Design
* **Decomposition & Boundaries**:
  - `MaxValueFilter.java`:
    1. **Initialization State Tracking**: Introduce `protected boolean mIsInitialized = false`.
    2. **Sub-Zero & First Non-Paused Sample Seeding**: In `newValue(Number value)`, when `!TrainingApplication.isPaused()` and `value != null`: if `!mIsInitialized`, set `mMaxValue = value; mIsInitialized = true;` else update `mMaxValue` if `value.doubleValue() > mMaxValue.doubleValue()`.
    3. **Lifecycle Reset**: In `reset()`, reset `mMaxValue = 0; mIsInitialized = false;`.
    4. **Pause Isolation & Backward Compatibility**: If queried prior to first unpaused sample, return `0` as before (`REQ-FIL-013.3`).
  - `ExponentialSmoothingFilter.java`:
    1. **Alpha Bounds Validation**: In constructor, enforce `0.0 < alpha <= 1.0` and throw `IllegalArgumentException` on invalid values (`<= 0.0`, `> 1.0`, `NaN`, `Infinite`).
    2. **Field Immutability**: Declare `protected final double mAlpha` for thread-safe publication.
    3. **First-Sample Seeding & Reseed Parity**: Introduce `protected boolean mIsInitialized = false`. In `newValue(Number value)`, when `value != null`: if `!mIsInitialized`, set `mFilteredValue = value.doubleValue(); mIsInitialized = true;` else apply exponential smoothing.
    4. **Lifecycle Reset**: In `reset()`, set `mFilteredValue = 0.0; mIsInitialized = false;`. This guarantees `reseed(v)` (`reset()` followed by `newValue(v)`) directly seeds baseline $v$ without historical decay (`REQ-FIL-012.5`).
* **Invariants**: Thread safety via `synchronized` instance monitors; null safety (`REQ-FIL-011`); backward compatibility for pre-start telemetry; 100% clean-room test pass rate.

---

## 2. Atomic Implementation Steps

| Step | Component | Target File | Action & Invariants | Traceability |
| :--- | :--- | :--- | :--- | :--- |
| **1** | Filter Engine | `MaxValueFilter.java` | Add `mIsInitialized`. In `newValue`, seed `mMaxValue` on first non-paused sample (`!isPaused()`). Reset `mIsInitialized` in `reset()`. | `REQ-FIL-015.1`<br>`TST-FIL-015.1-2` |
| **2** | Filter Engine | `ExponentialSmoothingFilter.java` | Validate `alpha \in (0, 1]`; mark `mAlpha` final. Add `mIsInitialized`. Seed on first sample; reset in `reset()`. | `REQ-FIL-015.2-4`<br>`TST-FIL-015.3-4` |
| **3** | Regression Test Update | `FilterResetAndPreStartTest.kt` | Update lines 50, 54, 58 to reflect unbiased first sample seeding (100.0, 125.0, 125.0). Lines 62, 192, 212 remain 0.0. | `REQ-FIL-015.1,3`<br>`TST-FIL-015.2,4` |
| **4** | Net-New Test Suite | `FilterInitializationAndBoundsTest.kt` | Add unit tests for sub-zero max tracking, alpha exception validation, first-sample/reseed parity, and concurrency stress. | `REQ-FIL-015.1-4`<br>`TST-FIL-015.1-5` |
| **5** | Verification | Test Suite | Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.filters.*"` and requirement governance. | `REQ-PRO-001`<br>`TST-FIL-015.7` |

---

## 3. System Invariants & Rollback Safety
* **Code Lock (Rule 4)**: No production source code modified until Gate 3 is marked `Erledigt` and `check-gate` exits 0.
* **Scope Boundary**: Strictly limited to Teilpaket C (2 SP); Teilpaket A (`ATT-6046`) and Teilpaket B (`ATT-6381`) are complete.
* **Risk Rating**: **LOW** (Encapsulated in filter mathematical smoothing logic; zero schema, IPC, or UI alterations).
