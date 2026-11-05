# Stage 2: Requirement & Test Specification - ATT-5401: Guarantee Visible Action on 'Bei Google Play bewerten'

**Ticket**: [ATT-5401](https://rainerblind.atlassian.net/browse/ATT-5401)  
**Parent Epic**: Standalone Ticket (None)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.6`  
**Requirement Mapping**: `REQ-REV-003` (*First-Jubilee Celebration Dialog & Multi-Channel Engagement*)  
**Test Spec ID**: `TST-REV-003`  
**Branch**: `feature/ATT-5401`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-REV-003)

### 1.1 Problem Statement & Rationale
In `FirstJubileeCelebrationDialog.kt`, clicking *"Bei Google Play bewerten"* silently no-ops when Google Play Core's `ReviewManager.requestReviewFlow()` returns synthetic `ReviewInfo` tokens that complete in ~0 ms without showing UI (debug builds, sideloaded installs, quota limits), or when `Context` is wrapped in a `ContextWrapper`. Because the user explicitly requested a review, the app MUST guarantee a visible outcome.

### 1.2 Functional & Architectural Requirements
1. **Context Activity Resolution (`REQ-REV-003.1`)**: `FirstJubileeCelebrationDialog.kt` SHALL unwrap `Context` via `tailrec fun Context.findActivity(): Activity?` traversing `ContextWrapper.baseContext` to resolve underlying activities.
2. **Deterministic Visible Action (`REQ-REV-003.2`)**: In `FirstJubileeActionHelper.launchInAppReviewOrStore(context)`, if `BuildConfig.DEBUG == true`, if activity cannot be resolved, or if `launchReviewFlow` completes in $< 300\text{ ms}$ without rendering UI, the system SHALL immediately invoke `openPlayStorePage(context)` (`market://details?id=com.atrainingtracker` with web fallback).
3. **Dialog Dismissal & Clean Lifecycle (`REQ-REV-003.3`)**: Triggering the review action SHALL dismiss the jubilee dialog cleanly and preserve the single-prompt invariant (`REQ-REV-004`).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-REV-003`, amending `FirstJubileeActionHelper.launchInAppReviewOrStore` and `FirstJubileeCelebrationDialog.kt`.
* **Historical Origin & Commit Trace**: Commit `d60ee455` (Sprint 2026-45.5, `ATT-2308`), introducing jubilee celebration dialog and In-App Review helper. Amending ticket `ATT-5401` (Sprint 2026-45.6).
* **Root Reason for Existing Formulation**: `ATT-2308` assumed Play Core would either render an in-app sheet or fail via `task.isSuccessful == false`. Play Core's silent no-op behavior on debug/sideloaded/throttled runs was unaccounted for.
* **Preservation of Core Invariants**: `REQ-REV-004` single-prompt persistence invariant, milestone counting contracts, and unthrottled production In-App Review flow are fully preserved; fallback guarantees non-empty user feedback.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Debug & Sideload Fallback)**: *Given* `BuildConfig.DEBUG == true` or a sideloaded/throttled environment, *When* the user taps "Bei Google Play bewerten", *Then* the app SHALL launch `openPlayStorePage(context)` without silent failure.
* **Criterion 2 (ContextWrapper Resilience)**: *Given* a `ContextWrapper` instance passed to `FirstJubileeActionHelper`, *When* `findActivity()` is evaluated, *Then* the underlying `Activity` is resolved successfully or handled cleanly via store fallback.

---

## 2. Test Specification (TST-REV-003)

### Test Case 1: `testFirstJubileeActionHelper_debugBuildLaunchesPlayStoreFallback` (`TST-REV-003.DEBUG`)
* **Scope**: Contract / Behavioral Unit Test (`FirstJubileeCelebrationDialogTest.kt`)
* **Preconditions**: Mock `Context` and `Intent` capturing.
* **Action**: Invoke `FirstJubileeActionHelper.launchInAppReviewOrStore(mockContext)` in debug mode.
* **Expected Result**: An `ACTION_VIEW` intent with `market://details?id=com.atrainingtracker` (or web URL) is dispatched to `startActivity`.

### Test Case 2: `testContext_findActivity_unwrapsContextWrappers` (`TST-REV-003.UNWRAP`)
* **Scope**: Unit Test (`FirstJubileeCelebrationDialogTest.kt`)
* **Preconditions**: Nested `ContextWrapper(ContextWrapper(mockActivity))`.
* **Action**: Call `context.findActivity()`.
* **Expected Result**: Successfully returns `mockActivity`.

### Test Case 3: 9-Language Localization & Specifier Audit (`TST-REV-003.LOC`)
* **Scope**: Localization Parity Test (`TranslationParityTest`)
* **Expected Result**: 100% parity across all 9 locales; no string changes required.

### Test Case 4: Targeted Clean-Room Test Suite (`TST-REV-003.REG`)
* **Scope**: Targeted Unit Tests (`ui.dialogs.*`, `build.*`, `ThemeColorCentralizationAuditTest`)
* **Expected Result**: 100% test pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-REV-003.DEBUG` | Contract | `FirstJubileeCelebrationDialogTest.kt` | `REQ-REV-003.2` | Specified |
| `TST-REV-003.UNWRAP` | Unit | `FirstJubileeCelebrationDialogTest.kt` | `REQ-REV-003.1` | Specified |
| `TST-REV-003.LOC` | Localization | `TranslationParityTest.kt` | `REQ-UI-106` | Specified |
| `TST-REV-003.REG` | Regression | Full targeted unit test suite | `REQ-PRO-001`, `REQ-REV-004` | Specified |
