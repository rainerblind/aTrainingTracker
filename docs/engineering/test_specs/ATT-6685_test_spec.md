# Stage 2: Requirement & Test Specification - ATT-6685: MapDetailLayout: Fix multithreaded SnapshotStateObserver crash when calling GraphicsLayer.toImageBitmap() on Dispatchers.Default during Map Snapshot share

**Ticket**: [ATT-6685](https://rainerblind.atlassian.net/browse/ATT-6685)  
**Sub-task**: Pending `submit-stage` (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath (WorkoutSummaries, Details & Graphs)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.2`  
**Requirement Mapping**: `REQ-REV-006` (*Map Snapshot Share Pager Stability, Duplicate Header Elimination & Caller State Restoration*)  
**Test Spec ID**: `TST-REV-007`  
**Branch**: `feature/ATT-6685`  
**Author**: AI Agent 1 (Flash Implementer)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-REV-006.5)

### 1.1 Problem Statement & Rationale
When triggering *Karten- & Analyse-Snapshot* from `WorkoutDetailTabbedContent` (`WorkoutShareBottomSheet`), the app crashes with a fatal `IllegalArgumentException` on the Main UI thread (`AndroidComposeView.dispatchDraw`):
`Detected multithreaded access to SnapshotStateObserver: previousThreadId=131, currentThread={id=2, name=main}`.
In `MapDetailLayout.kt`, `onSnapshotReady` evaluates `headerLayer.toImageBitmap()`, `elevationLayer.toImageBitmap()`, and `analyticsLayer.toImageBitmap()` within `withContext(Dispatchers.Default)`. Evaluating child draw blocks on a background thread pollutes `SnapshotStateObserver.previousThreadId` and violates Compose's single-thread UI observation invariant.

### 1.2 Functional & Architectural Requirements
1. **UI-Thread Layer Rasterization (`REQ-REV-006.5a`)**: In `MapDetailLayout.kt`, `headerLayer.toImageBitmap().asAndroidBitmap()`, `elevationLayer.toImageBitmap().asAndroidBitmap()`, and `analyticsLayer.toImageBitmap().asAndroidBitmap()` inside `onSnapshotReady` MUST execute on the Main UI thread (`Dispatchers.Main.immediate` / `Dispatchers.Main`).
2. **Background Processing Offload (`REQ-REV-006.5b`)**: Downstream multi-bitmap composition, canvas assembly, and file persistence (`combineWorkoutAndShare`) SHALL remain offloaded to `Dispatchers.Default` / `Dispatchers.IO` (already handled inside `combineWorkoutAndShare`).
3. **Guaranteed State Reset & Error Resilience (`REQ-REV-006.5c`)**: `onSnapshotReady` SHALL encapsulate layer rasterization and share dispatching inside a `try-catch-finally` block ensuring that `isSharing = false` and `onSnapshotComplete?.invoke()` are guaranteed to execute even if an unexpected exception occurs, preventing permanently stuck sharing states.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-REV-006` (*Map Snapshot Share Pager Stability, Duplicate Header Elimination & Caller State Restoration*, `ATT-6322`). Targets `MapDetailLayout.kt`.
* **Historical Origin & Commit Trace**: Commit `5248ddab8` (`ATT-1890`) introduced `withContext(Dispatchers.Default)` around `toImageBitmap()`; commit `4fb53a57` (`ATT-6322`) added offscreen header recording when `showHeader = false`.
* **Root Reason for Existing Formulation**: Background dispatching was intended to avoid main-thread jank during bitmap conversions without recognizing that Compose's `GraphicsLayer` tree reads require state observation on the Compose Owner's UI thread.
* **Preservation of Core Invariants**: Heavy bitmap stitching, scaling, and disk I/O in `combineWorkoutAndShare` remain 100% isolated on `Dispatchers.Default`; visual snapshot fidelity, offscreen header recording, and sharing sheet triggers remain completely preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1**:
  * *Given* `WorkoutDetailTabbedContent` active with `showHeader = false`,
  * *When* the user triggers *Karten- & Analyse-Snapshot* from the share sheet,
  * *Then* `headerLayer`, `elevationLayer`, and `analyticsLayer` rasterization executes on `Dispatchers.Main` without throwing `IllegalArgumentException` in `SnapshotStateObserver`, `combineWorkoutAndShare` stitches the full snapshot on `Dispatchers.Default`, and upon completion or failure `isSharing` is reset to false.

### 1.5 System Invariants
* Zero regression in existing snapshot visual fidelity, dimensions, or sharing intent behavior.
* Offscreen header recording for `showHeader = false` remains completely preserved.
* `isSharing` flag and `onSnapshotComplete` lifecycle guarantees maintained under all error conditions.

---

## 2. Test Specification (TST-REV-007.5)

### Test Case 1: `testMapDetailLayout_snapshotCapture_executesOnMainThreadWithoutDefaultDispatcher` (`TST-REV-007.5`)
* **Scope**: Contract / Source Static Analysis & Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutSnapshotContractTest.kt`
* **Preconditions**: `MapDetailLayout.kt` source loaded.
* **Action**: Parse `onSnapshotReady` implementation block.
* **Expected Result**: Assert that:
  1. No `withContext(Dispatchers.Default)` wraps `toImageBitmap()` calls.
  2. `headerLayer.toImageBitmap()`, `elevationLayer.toImageBitmap()`, and `analyticsLayer.toImageBitmap()` execute in the outer coroutine scope on `Dispatchers.Main`.
  3. `try { ... } catch (t: Throwable) { ... } finally { isSharing = false; onSnapshotComplete?.invoke() }` wraps the capture sequence.

### Test Case 2: 9-Language Localization & Specifier Audit (`TST-LOC-001`)
* **Scope**: Localization Parity Test
* **Goal**: Verify string presence and matching `%s`/`%d` tokens across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches. No new strings required for ATT-6685; existing `error_sharing_failed` is utilized.

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-REG-001`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Goal**: Verify 100% pass rate across all modified and adjacent package unit tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-REV-007.5` | Contract / Unit | `MapDetailLayout.onSnapshotReady` | `REQ-REV-006.5` | Specified |
| `TST-LOC-001` | Localization | `TranslationParityTest` | `REQ-REV-006`, `REQ-UI-106` | Specified |
| `TST-REG-001` | Regression (Tier 1) | `--tests "com.atrainingtracker.trainingtracker.ui.map.*"` | `REQ-PRO-001` | Specified |
