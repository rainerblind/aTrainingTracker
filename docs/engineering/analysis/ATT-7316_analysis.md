# Stage 1 Analysis: ATT-7316 - MapDetailLayout: Fix ForgottenCoroutineScopeException showing false 'Failed to share' toast

**Ticket**: [ATT-7316](https://rainerblind.atlassian.net/browse/ATT-7316)  
**Sub-task**: Pending `submit-stage` (`[Analysis]`)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath (WorkoutSummaries, Details & Graphs)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.4`  
**Branch**: `subagent-Lane-3-Agent---ATT-7316-self-f893689e`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
* **Symptom**: During workout map snapshot sharing on Pixel 10 / Android 14+ (or when sharing from bottom sheets / tabbed workout details), the system share sheet (`ChooserActivity`) appears successfully with the composed image, but an error toast simultaneously appears at the bottom: *"Unable to generate share image. Please try again."* (`R.string.error_sharing_failed`).
* **Impact**: Athletes receive a false error notification despite the sharing operation succeeding, causing confusion and degrading user trust.
* **Goal**: Prevent false failure toasts by running share intent launching in non-cancellable context and safely ignoring `CancellationException` / `ForgottenCoroutineScopeException` when the composable leaves composition, while preserving robust cleanup and real error reporting.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Failure Sequence**:
  1. In `MapDetailLayout.kt` (`onSnapshotReady`), snapshot capture and share is launched using `scope = rememberCoroutineScope()`, invoking `executeSnapshotCaptureAndShare`.
  2. Inside `executeSnapshotCaptureAndShare`, `shareAction` calls `combineWorkoutAndShare`, which invokes `saveAndShare` and launches Android's system share sheet via `context.startActivity(Intent.createChooser(...))`.
  3. When `ChooserActivity` displays or when the container (e.g. `WorkoutShareBottomSheet` or auto-share sheet) leaves composition, Compose's runtime cancels `rememberCoroutineScope()` with `androidx.compose.runtime.ForgottenCoroutineScopeException: rememberCoroutineScope left the composition`.
  4. `ForgottenCoroutineScopeException` inherits from `kotlinx.coroutines.CancellationException`.
  5. In `executeSnapshotCaptureAndShare`, the generic `catch (t: Throwable)` catches `ForgottenCoroutineScopeException` and routes it to `onError(t)`.
  6. `onError` logs `Failed to capture or share workout snapshot` and posts `Toast.makeText(context, R.string.error_sharing_failed, Toast.LENGTH_SHORT).show()`, displaying the misleading toast.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Verified in `MapDetailLayout.kt:340-370` and `1244-1268`: `executeSnapshotCaptureAndShare` catches `Throwable` unconditionally and forwards to `onError`.
  * Verified class hierarchy: `ForgottenCoroutineScopeException` extends `CancellationException`.
  * Verified fix strategy:
    1. Wrap `shareAction(...)` in `withContext(NonCancellable)` to ensure image saving and intent dispatching cannot be aborted mid-flight if the composition begins tearing down.
    2. Catch `CancellationException` separately in `executeSnapshotCaptureAndShare` without routing to `onError`.
    3. Defensively ignore `CancellationException` in `onError` in `MapDetailLayout.kt`.
    4. Ensure `finally` block continues to execute `onFinally()`, guaranteeing `isSharing = false` and `onSnapshotComplete?.invoke()` under all conditions.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals**:
  1. Update `executeSnapshotCaptureAndShare` in `MapDetailLayout.kt` to execute `shareAction` under `withContext(NonCancellable)`.
  2. Handle `CancellationException` (including `ForgottenCoroutineScopeException`) gracefully in `executeSnapshotCaptureAndShare` and `onError` without showing failure toast.
  3. Guarantee `onFinally` execution to maintain lifecycle invariants (`isSharing = false`, `onSnapshotComplete?.invoke()`).
  4. Extend unit and contract tests in `MapDetailLayoutSnapshotContractTest.kt` to verify that `CancellationException` / `ForgottenCoroutineScopeException` does not trigger `onError`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No changes to bitmap composition or watermark layout math in `ShareUtils.kt`.
  2. No modifications to `WorkoutShareBottomSheet` UI or photo selection workflows.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Amends `REQ-REV-006` (*Map Snapshot Share Pager Stability, Duplicate Header Elimination & Caller State Restoration*, subclause 5 `REQ-REV-006.5`, `ATT-6685`). Targets `MapDetailLayout.kt`.
* **Historical Origin & Commit Trace**: Added in `ATT-6685` (`5248ddab8`, `4fb53a57`) where `executeSnapshotCaptureAndShare` introduced `try-catch-finally` to prevent stuck `isSharing = true` states.
* **Root Reason for Existing Formulation**: Intended to catch rendering errors and I/O failures so `isSharing` wouldn't remain permanently true. However, catching raw `Throwable` without handling `CancellationException` violated standard coroutine lifecycle rules and converted normal Compose unmounts into error toasts.
* **Preservation of Core Invariants**: `finally { onFinally() }` continues to guarantee `isSharing = false` and `onSnapshotComplete?.invoke()`; genuine rendering and I/O exceptions continue to be logged and surfaced via toast.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-REV-006` (subclause 5.c / `REQ-REV-006.5`).
* **Affected Architecture Elements**: `com.atrainingtracker.trainingtracker.ui.map.MapDetailLayout.kt`.
* **Verification Impact & Test Surface**: `MapDetailLayoutSnapshotContractTest.kt`, `MapDetailLayoutTest.kt`.
* **Risk Assessment & Invariants**: Low risk. Isolated to coroutine exception handling; does not alter bitmap rendering or sharing intent parameters.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Map snapshot visual fidelity and multi-layer stitching remain unaltered.
  2. `isSharing` reset and `onSnapshotComplete` invocation strictly guaranteed in `finally`.
  3. Real exceptions (OOM, file I/O failure, SecurityException) still trigger `onError` and toast.
* **Risk Rating**: **LOW** (Targeted coroutine exception filtering in `MapDetailLayout.kt`).
