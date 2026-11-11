# Stage 1 Analysis: ATT-6685 - Fix multithreaded SnapshotStateObserver crash when calling GraphicsLayer.toImageBitmap() on Dispatchers.Default during Map Snapshot share

**Ticket**: [ATT-6685](https://rainerblind.atlassian.net/browse/ATT-6685)  
**Sub-task**: Pending `submit-stage` (`[Analysis]`)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath (WorkoutSummaries, Details & Graphs)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.2`  
**Branch**: `feature/ATT-6685`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
* **Symptom**: Triggering *Karten- & Analyse-Snapshot* from `WorkoutDetailTabbedContent` / `WorkoutShareBottomSheet` causes an unhandled fatal `IllegalArgumentException` on the Main UI thread (`AndroidComposeView.dispatchDraw`):
  `Detected multithreaded access to SnapshotStateObserver: previousThreadId=131, currentThread={id=2, name=main}`.
* **Impact**: Map snapshot sharing crashes the application or leaves `isSharing = true` permanently stuck, breaking tab navigation and workout sharing.
* **Goal**: Ensure Compose `GraphicsLayer` rasterization executes safely on the UI thread, with downstream bitmap stitching/saving cleanly offloaded to background dispatchers, accompanied by robust error recovery (`try/catch/finally`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Mechanics**: In `MapDetailLayout.kt` (lines 341–358), `onSnapshotReady` launches a coroutine on `Dispatchers.Main.immediate` but explicitly switches to `withContext(Dispatchers.Default)` to call `headerLayer.toImageBitmap().asAndroidBitmap()`, `elevationLayer.toImageBitmap().asAndroidBitmap()`, and `analyticsLayer.toImageBitmap().asAndroidBitmap()`.
* **Failure Sequence**:
  1. `GraphicsLayer.toImageBitmap()` executes `drawWithChildTracking` -> `NodeCoordinator._get_drawBlock_` -> `SnapshotStateObserver.observeReads` on worker thread 131 (`Dispatchers.Default`).
  2. `SnapshotStateObserver.previousThreadId` is mutated to 131.
  3. On the next VSync frame, `AndroidComposeView.dispatchDraw` executes on Main UI thread (`id=2, name=main`), detects `previousThreadId != currentThreadId` and crashes with `IllegalArgumentException`.
  4. In `ATT-6322`, `showHeader = false` introduced offscreen header measurement (`layout(0,0) { placeable.place(0,0) }`) and `headerLayer.record`, which reliably triggers child draw tracking in `headerLayer.toImageBitmap()`.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Hypothesis verified: Confirmed in `MapDetailLayout.kt:341-358` that `withContext(Dispatchers.Default)` wraps `toImageBitmap()` calls.
  * Verified downstream contract: `combineWorkoutAndShare` in `ShareUtils.kt:134` already declares `withContext(Dispatchers.Default)` internally for bitmap allocation, canvas drawing, and file I/O. Wrapping `toImageBitmap()` in `Dispatchers.Default` was redundant and hazardous.
  * Blind Spot Identified: `onSnapshotReady` lacks `try/catch/finally` handling; if any exception occurs, `isSharing` remains `true` and `onSnapshotComplete?.invoke()` never fires.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Move `headerLayer.toImageBitmap()`, `elevationLayer.toImageBitmap()`, and `analyticsLayer.toImageBitmap()` to the Main UI thread in `MapDetailLayout.kt`.
  2. Add `try / catch / finally` inside `onSnapshotReady` to guarantee `isSharing = false` and `onSnapshotComplete?.invoke()` invocation under all conditions.
  3. Expand contract and unit tests in `WorkoutShareBottomSheetContractTest.kt` or `MapDetailLayoutTest.kt` to verify main-thread dispatching contract.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No modifications to `combineWorkoutAndShare` layout math or canvas stitching in `ShareUtils.kt`.
  2. No changes to `WorkoutDetailTabbedContent` pager animation or route navigation contracts.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-REV-006` (*Map Snapshot Share Pager Stability, Duplicate Header Elimination & Caller State Restoration*), targeting `MapDetailLayout.kt`.
* **Historical Origin & Commit Trace**: Commit `5248ddab8` (ATT-1890) introduced `withContext(Dispatchers.Default)` around `toImageBitmap()` under the assumption of offloading work; commit `4fb53a57` (ATT-6322) added offscreen header recording.
* **Root Reason for Existing Formulation**: Aimed to avoid main-thread jank during snapshot generation without recognizing that Compose `GraphicsLayer` tree reads must observe state on the Compose Owner's UI thread.
* **Preservation of Core Invariants**: Retains background execution for heavy image combining and disk I/O in `combineWorkoutAndShare`, while moving layer capture to Main thread.

---

## 5. Architectural Strategy & High-Level Solution
* **Execution Flow**:
  1. In `MapDetailLayout.kt`, execute `headerLayer.toImageBitmap().asAndroidBitmap()`, `elevationLayer.toImageBitmap().asAndroidBitmap()`, and `analyticsLayer.toImageBitmap().asAndroidBitmap()` directly on `Dispatchers.Main` inside `onSnapshotReady`.
  2. Retain `combineWorkoutAndShare(context, hBmp, mapBitmap, eBmp, aBmp)` as a suspend function executing on `Dispatchers.Default`.
  3. Wrap layer rasterization and share dispatching in `try { ... } catch (t: Throwable) { Log.e(...); Toast.makeText(...) } finally { isSharing = false; onSnapshotComplete?.invoke() }`.
  4. Formulate formal requirement sub-clause `REQ-REV-006.5` and test specification `TST-REV-007.5`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in existing snapshot visual fidelity, layout sizes, or sharing intent behavior.
  2. Offscreen header recording for `showHeader = false` remains completely preserved.
  3. `isSharing` flag and `onSnapshotComplete` lifecycle guarantees maintained under all error conditions.
* **Risk Rating**: **LOW** (Targeted threading fix in `MapDetailLayout.kt`; downstream stitching already isolated on `Dispatchers.Default`; no schema or API breakage).
