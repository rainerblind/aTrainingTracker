# Stage 2: Requirement & Test Specification - ATT-6382: MaxValueFilter & ExponentialSmoothingFilter State Initialization, Zero-Bias Elimination & Alpha Validation

**Ticket**: [ATT-6382](https://rainerblind.atlassian.net/browse/ATT-6382) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6382`  
**Requirement Mapping**: `REQ-FIL-015` (`REQ-FIL-015.1`..`REQ-FIL-015.4`) | **Test Spec ID**: `TST-FIL-015` (`TST-FIL-015.1`..`TST-FIL-015.7`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (`REQ-FIL-015`)

### 1.1 Problem Statement & Rationale
`MaxValueFilter` previously hardcoded `mMaxValue = 0` and lacked initialization tracking, permanently reporting phantom `0` when encountering sub-zero telemetry (e.g. ambient temperature or negative elevation). `ExponentialSmoothingFilter` accepted unbounded $\alpha$ values (`<= 0`, `> 1`, `NaN`) without validation and attenuated the initial sample by 50% toward zero due to missing first-sample seeding, which also broke `reseed(value)` baseline parity (`REQ-FIL-012.5`).

### 1.2 Functional & Architectural Requirements
* **REQ-FIL-015.1** (*MaxValueFilter Sub-Zero Seeding*): `MaxValueFilter` SHALL maintain private `mIsInitialized` state. When `!TrainingApplication.isPaused()` and non-null sample $v$ arrives: if `!mIsInitialized`, set `mMaxValue = v; mIsInitialized = true;`. If initialized, update `mMaxValue` if $v > mMaxValue$. On `reset()`, reset `mIsInitialized = false; mMaxValue = 0;`. Samples received while paused MUST NOT alter filter state (`REQ-FIL-013.3`).
* **REQ-FIL-015.2** (*ExponentialSmoothingFilter Alpha Validation*): `ExponentialSmoothingFilter` constructor SHALL validate $\alpha \in (0.0, 1.0]$ and throw `IllegalArgumentException` on values $\le 0.0$, $> 1.0$, `NaN`, or infinite. Field `mAlpha` SHALL be declared `final`.
* **REQ-FIL-015.3** (*ExponentialSmoothingFilter First Sample Seeding & Reseed Parity*): `ExponentialSmoothingFilter` SHALL maintain `mIsInitialized` state. The first non-null sample SHALL seed `mFilteredValue = value.doubleValue()` directly (`mIsInitialized = true`) without 0.0-bias. In `reseed(Number value)`, invoking `reset()` followed by `newValue(value)` SHALL directly seed the new baseline without historical decay (`REQ-FIL-012.5`).
* **REQ-FIL-015.4** (*Thread Safety & Null Invariant Preservation*): `newValue`, `getFilteredValue`, and `reset` in both filters MUST remain `synchronized`. Passing `null` MUST NOT throw NPE or mutate state (`REQ-FIL-011`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Sub-Zero Max Tracking)**: *Given* a reset `MaxValueFilter`, *When* unpaused sample `-5.0` arrives, *Then* `getFilteredValue()` returns `-5.0`.
* **AC-2 (Pause Session Isolation)**: *Given* `MaxValueFilter` while paused, *When* sample `500` arrives, *Then* `getFilteredValue()` remains `0` and `mIsInitialized` remains false.
* **AC-3 (Alpha Range Validation)**: *Given* $\alpha \le 0.0$, $\alpha > 1.0$, or `NaN`, *When* constructing `ExponentialSmoothingFilter`, *Then* `IllegalArgumentException` is thrown.
* **AC-4 (First Sample Seeding)**: *Given* `ExponentialSmoothingFilter` ($\alpha=0.5$), *When* first sample `100.0` arrives, *Then* `getFilteredValue()` returns `100.0` (zero 0.0-bias).
* **AC-5 (Reseed Parity)**: *Given* `ExponentialSmoothingFilter`, *When* `reseed(100.0)` is called, *Then* `getFilteredValue()` returns `100.0` immediately.

---

## 2. Test Specification & Traceability Matrix (`TST-FIL-015`)

| Req ID | Test Case | Target Component | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-FIL-015.1` | `TST-FIL-015.1` | `FilterInitializationAndBoundsTest.kt` | Sub-zero sample seeding and maximum tracking in `MaxValueFilter` |
| `REQ-FIL-015.1` | `TST-FIL-015.2` | `FilterResetAndPreStartTest.kt` | Pause isolation and reset flushes max value in `MaxValueFilter` |
| `REQ-FIL-015.2` | `TST-FIL-015.3` | `FilterInitializationAndBoundsTest.kt` | Range validation throwing `IllegalArgumentException` in `ExponentialSmoothingFilter` |
| `REQ-FIL-015.3` | `TST-FIL-015.4` | `FilterInitializationAndBoundsTest.kt` | First-sample baseline seeding and `reseed()` parity in `ExponentialSmoothingFilter` |
| `REQ-FIL-015.4` | `TST-FIL-015.5` | `FilterInitializationAndBoundsTest.kt` | Concurrent multi-threaded read/write stress test |
| `REQ-FIL-015` | `TST-FIL-015.6` | `TranslationParityTest.kt` | 9-locale parity audit (zero new strings required) |
| `REQ-FIL-015` | `TST-FIL-015.7` | Targeted Test Suite | Targeted filter suite: `com.atrainingtracker.banalservice.filters.*` |
