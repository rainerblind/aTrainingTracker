# Stage 3: Implementation Plan - ATT-7316: Fix ForgottenCoroutineScopeException showing false 'Failed to share' toast

**Ticket**: [ATT-7316](https://rainerblind.atlassian.net/browse/ATT-7316)  
**Sub-task**: Pending `submit-stage` (`[Impl-Plan]`)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath (WorkoutSummaries, Details & Graphs)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.4`  
**Requirement Mapping**: `REQ-REV-006` (subclause 5.d / `REQ-REV-006.5`)  
**Test Mapping**: `TST-REV-007` (subclause 6 / `TST-REV-007.6`)  
**Branch**: `subagent-Lane-3-Agent---ATT-7316-self-f893689e`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background
When sharing a workout map snapshot, `executeSnapshotCaptureAndShare` in `MapDetailLayout.kt` delegates to `shareAction`, which launches Android's system share sheet (`ChooserActivity`). When ChooserActivity opens or the hosting bottom sheet leaves composition, the coroutine scope (`rememberCoroutineScope()`) cancels running jobs with `ForgottenCoroutineScopeException` (which extends `CancellationException`). The unspecialized `catch (t: Throwable)` block in `executeSnapshotCaptureAndShare` routes this cancellation to `onError`, emitting a spurious *"Unable to generate share image. Please try again."* (`R.string.error_sharing_failed`) toast despite the share intent already succeeding.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-REV-006` (subclause 5.d / `REQ-REV-006.5`: *Safe NonCancellable Share Dispatching & Cancellation Exception Filtering*)
* **Test Mapping**: `TST-REV-007` (subclause 6 / `TST-REV-007.6`: *ForgottenCoroutineScopeException & NonCancellable Share Safety Contract Test*)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Unintended Regressions**: Existing `MapDetailLayout` rendering, zooming, scrubbing, and snapshot tests continue to pass 100%.
2. **Guaranteed Cleanup Lifecycle**: `onFinally()` strictly executes in `finally`, guaranteeing `isSharing = false` and calling `onSnapshotComplete?.invoke()` under all conditions.
3. **Genuine Error Surfacing**: Non-cancellation exceptions (`IllegalStateException`, `IOException`, OOM, `SecurityException`) continue to be routed to `onError` and surfaced via error toast.
4. **Parent Human Gate Invariance**: Terminal completion of the parent ticket remains reserved for the human user at Final Review.

---

## 4. Proposed Architectural Changes
### Component 1: `MapDetailLayout.kt` (`com.atrainingtracker.trainingtracker.ui.map`)
* In `executeSnapshotCaptureAndShare`:
  * Wrap `shareAction(hBmp, mapBitmap, eBmp, aBmp)` in `withContext(NonCancellable)` so intent launching and file operations cannot be cancelled mid-flight.
  * Add an explicit `catch (_: CancellationException)` block that ignores coroutine cancellation (including `ForgottenCoroutineScopeException`) without forwarding to `onError`.
  * Retain `catch (t: Throwable)` for authentic runtime failures.
  * In `onError` in `MapDetailLayout`, add a defensive `if (t !is CancellationException)` check before logging and displaying `R.string.error_sharing_failed`.

### Component 2: `MapDetailLayoutSnapshotContractTest.kt` (`com.atrainingtracker.trainingtracker.ui.map`)
* Add contract test verifying that when `shareAction` throws `CancellationException`, `onError` is NOT invoked and `onFinally` executes.
* Add contract test verifying that when `shareAction` throws `ForgottenCoroutineScopeException`, `onError` is NOT invoked and `onFinally` executes.
* Add contract test verifying that genuine runtime exceptions (e.g., `IllegalStateException`) continue to invoke `onError` and `onFinally`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Code Lock Verification)**: Confirm Gate 3 approval using `python3 tools/jira_util.py check-gate <SUBTASK_KEY>`.
2. **Step 2 (MapDetailLayout Update)**: Update `executeSnapshotCaptureAndShare` and `onError` in `MapDetailLayout.kt` with `NonCancellable` and `CancellationException` handling.
3. **Step 3 (Contract Test Augmentation)**: Implement `TST-REV-007.6` test cases in `MapDetailLayoutSnapshotContractTest.kt`.
4. **Step 4 (Targeted Verification)**: Run `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutSnapshotContractTest`.
5. **Step 5 (Full MapDetailLayout Regression)**: Run `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.map.MapDetailLayout*`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit and contract tests in `MapDetailLayoutSnapshotContractTest.kt` and full suite test execution.
* **Rollback**: Revert branch changes to restore previous commit state.
