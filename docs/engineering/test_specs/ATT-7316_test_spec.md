# Stage 2: Requirement & Test Specification - ATT-7316: Fix ForgottenCoroutineScopeException showing false 'Failed to share' toast

**Ticket**: [ATT-7316](https://rainerblind.atlassian.net/browse/ATT-7316)  
**Sub-task**: Pending `submit-stage` (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath (WorkoutSummaries, Details & Graphs)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.4`  
**Requirement Mapping**: `REQ-REV-006` (subclause 5.d / `REQ-REV-006.5`)  
**Test Spec ID**: `TST-REV-007` (subclause 6 / `TST-REV-007.6`)  
**Branch**: `subagent-Lane-3-Agent---ATT-7316-self-f893689e`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-REV-006.5)

### 1.1 Problem Statement & Rationale
When sharing workout map snapshots, opening Android's system share sheet (`ChooserActivity`) causes the bottom sheet or host composable to leave composition. In Compose, any active coroutine launched from `rememberCoroutineScope()` is cancelled with `ForgottenCoroutineScopeException` (or `CancellationException`). In `MapDetailLayout.kt`, catching `Throwable` unconditionally without filtering out coroutine cancellation routes this exception to `onError`, displaying a spurious *"Unable to generate share image. Please try again."* (`R.string.error_sharing_failed`) toast despite the share intent already succeeding.

### 1.2 Functional & Architectural Requirements
* `REQ-REV-006.5.d`: In `MapDetailLayout`, `executeSnapshotCaptureAndShare` SHALL wrap `shareAction` in `withContext(NonCancellable)` so intent launching and file operations run to completion without being cut short by composition transitions.
* `REQ-REV-006.5.e`: In `MapDetailLayout`, `executeSnapshotCaptureAndShare` SHALL catch `CancellationException` (which includes `androidx.compose.runtime.ForgottenCoroutineScopeException`) and ignore it without routing to `onError`, ensuring no failure toast is displayed when coroutines are cancelled due to normal lifecycle unmounts.
* `REQ-REV-006.5.f`: `onError` in `MapDetailLayout` SHALL defensively guard against displaying toasts or logging errors if the received `Throwable` is a `CancellationException`.
* `REQ-REV-006.5.g`: The `finally` block in `executeSnapshotCaptureAndShare` MUST always execute `onFinally()`, guaranteeing `isSharing = false` and invoking `onSnapshotComplete?.invoke()` under all circumstances.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (NonCancellable & Ignored Cancellation)**:
  * *Given* `MapDetailLayout` executing `executeSnapshotCaptureAndShare`,
  * *When* `shareAction` or the coroutine scope encounters a `CancellationException` or `ForgottenCoroutineScopeException`,
  * *Then* `onError` is NOT invoked, no failure toast is displayed, and `onFinally` executes cleanly.
* **Criterion 2 (Preservation of Genuine Error Handling)**:
  * *Given* `MapDetailLayout` executing `executeSnapshotCaptureAndShare`,
  * *When* a non-cancellation exception (e.g. `IllegalStateException`, `IOException`) occurs during capture or sharing,
  * *Then* `onError` receives the exception, logs it, shows `R.string.error_sharing_failed`, and `onFinally` executes.

### 1.4 System Invariants
* Map snapshot visual resolution, multi-layer stitching, and watermark generation remain unchanged.
* `isSharing` reset and `onSnapshotComplete` invocation strictly preserved under all normal, cancelled, and error execution paths.
* Zero regressions in existing `MapDetailLayoutSnapshotContractTest` and `MapDetailLayoutTest`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-REV-006` (subclause 5 `REQ-REV-006.5`, `ATT-6685`). Targets `MapDetailLayout.kt`.
* **Historical Origin & Commit Trace**: Added in `ATT-6685` (`5248ddab8`, `4fb53a57`) where `executeSnapshotCaptureAndShare` introduced `try-catch-finally` to ensure `isSharing = false` cleanup.
* **Root Reason for Existing Formulation**: Intended to catch rendering errors and I/O failures so `isSharing` would not remain permanently `true`. Catching raw `Throwable` without excluding `CancellationException` inadvertently converted Compose scope unmounts into false error toasts.
* **Preservation of Core Invariants**: `finally { onFinally() }` continues to guarantee `isSharing = false` and `onSnapshotComplete?.invoke()`; genuine rendering and I/O exceptions continue to be logged and surfaced via toast.

---

## 2. Test Specification (TST-REV-007.6)

### Test Case 1: `testMapDetailLayout_whenCancellationExceptionThrown_doesNotInvokeOnError_TST_REV_007_6`
* **Scope**: Unit & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutSnapshotContractTest.kt`
* **Preconditions**: Mock bitmaps, `shareAction` throws `CancellationException("Scope cancelled")`.
* **Action**: Invoke `executeSnapshotCaptureAndShare(...)`.
* **Expected Result**: `errorCaught == null`, `finallyInvoked == true`.

### Test Case 2: `testMapDetailLayout_whenForgottenCoroutineScopeException_doesNotInvokeOnError_TST_REV_007_6`
* **Scope**: Unit & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutSnapshotContractTest.kt`
* **Preconditions**: Mock bitmaps, `shareAction` throws `androidx.compose.runtime.ForgottenCoroutineScopeException("rememberCoroutineScope left the composition")`.
* **Action**: Invoke `executeSnapshotCaptureAndShare(...)`.
* **Expected Result**: `errorCaught == null`, `finallyInvoked == true`.

### Test Case 3: `testMapDetailLayout_whenActualExceptionThrown_invokesOnErrorAndFinally_TST_REV_007_6`
* **Scope**: Unit & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutSnapshotContractTest.kt`
* **Preconditions**: Mock bitmaps, `shareAction` throws `IllegalStateException("IO failure")`.
* **Action**: Invoke `executeSnapshotCaptureAndShare(...)`.
* **Expected Result**: `errorCaught is IllegalStateException`, `finallyInvoked == true`.

### Test Case 4: Fast Targeted Regression
* **Scope**: Targeted Unit & Contract Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.map.MapDetailLayout*`
* **Expected Result**: 100% pass rate across all `MapDetailLayout` contract and unit tests.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-REV-006.5.d` | NonCancellable execution for `shareAction` | `TST-REV-007.6` Test Case 1 | Unit & Contract Test |
| `REQ-REV-006.5.e` | Ignore `CancellationException` & `ForgottenCoroutineScopeException` | `TST-REV-007.6` Test Case 1, 2 | Unit & Contract Test |
| `REQ-REV-006.5.f` | Defensive check in `onError` | `TST-REV-007.6` Test Case 1, 2 | Unit & Contract Test |
| `REQ-REV-006.5.g` | Invariant: `finally` executes `onFinally` | `TST-REV-007.6` Test Case 1, 2, 3 | Unit & Contract Test |
| `REQ-REV-006.5` | Targeted Regression Suite | `TST-REV-007.6` Test Case 4 | Fast Targeted Regression |
