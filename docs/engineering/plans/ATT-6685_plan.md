# Stage 3: Implementation Plan - ATT-6685: MapDetailLayout: Fix multithreaded SnapshotStateObserver crash when calling GraphicsLayer.toImageBitmap() on Dispatchers.Default during Map Snapshot share

**Ticket**: [ATT-6685](https://rainerblind.atlassian.net/browse/ATT-6685)  
**Sub-task**: Pending `submit-stage` (`[Impl-Plan]`)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath (WorkoutSummaries, Details & Graphs)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.2`  
**Requirement Mapping**: `REQ-REV-006` (`REQ-REV-006.5`)  
**Test Mapping**: `TST-REV-007` (`TST-REV-007.5`), `TST-LOC-001`, `TST-REG-001`  
**Branch**: `feature/ATT-6685`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background
When sharing a workout map snapshot from `WorkoutDetailTabbedContent`, Compose crashes with `IllegalArgumentException: Detected multithreaded access to SnapshotStateObserver` because `MapDetailLayout.kt` runs `GraphicsLayer.toImageBitmap()` within `withContext(Dispatchers.Default)`. This pollutes `previousThreadId` and violates Compose's single-thread Owner layout/draw observation invariant.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-REV-006.5` (*UI-Thread GraphicsLayer Rasterization & Safe Share Dispatching Contract*)
* **Test Mapping**: `TST-REV-007.5` (`MapDetailLayoutSnapshotContractTest.kt`), `TST-LOC-001` (`TranslationParityTest`), `TST-REG-001` (Tier 1 targeted map package suite).

---

## 3. System Invariants & Preserved Behavior
1. **Thread Affinitiy Invariant**: Compose `GraphicsLayer` tree evaluation and rasterization execute strictly on `Dispatchers.Main.immediate` / `Dispatchers.Main`.
2. **Background Processing Offload**: Heavy bitmap composition, canvas stitching, and disk I/O remain offloaded to `Dispatchers.Default` inside `combineWorkoutAndShare`.
3. **Guaranteed State Reset**: `isSharing = false` and `onSnapshotComplete?.invoke()` are guaranteed to execute via `try-catch-finally`, preventing frozen pager states.
4. **Visual & Offscreen Invariants**: Full fidelity bitmap output and offscreen header measurement (`showHeader = false`) remain 100% intact.

---

## 4. Proposed Architectural Changes

### Component 1: `MapDetailLayout.kt` (UI Layer)
* Strip `withContext(Dispatchers.Default)` wrapping around `headerLayer.toImageBitmap()`, `elevationLayer.toImageBitmap()`, and `analyticsLayer.toImageBitmap()`.
* Wrap the rasterization and share dispatching block inside `try { ... } catch (t: Throwable) { Log.e(...); Toast.makeText(...) } finally { isSharing = false; onSnapshotComplete?.invoke() }`.

### Component 2: `MapDetailLayoutSnapshotContractTest.kt` (Test Layer)
* Create contract and source static analysis test verifying that `toImageBitmap()` is not invoked inside `Dispatchers.Default` and that `try-catch-finally` lifecycle cleanup is enforced.

### UI Consistency (Rule 23)
* **No UI visual changes**: Pure threading and exception safety refactoring inside `onSnapshotReady` callback.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `MapDetailLayout.kt` Dispatching & Error Handling
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
* Changes: Remove `withContext(Dispatchers.Default)` around `toImageBitmap()` calls in `onSnapshotReady`; add `try-catch-finally` block for robust cleanup.

### Step 2: Implement Contract & Lifecycle Test
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutSnapshotContractTest.kt`
* Changes: Verify absence of `Dispatchers.Default` around `toImageBitmap()`, presence of main-thread dispatching, and `try-catch-finally` cleanup contract.

### Step 3: Execute Tier 1 Targeted Verification & Living Docs Governance
* Command:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests (`MapDetailLayoutSnapshotContractTest`, `WorkoutShareBottomSheetContractTest`) followed by clean-room build under POSIX lock in Stage 5.
* **Rollback**: Clean git branch isolation (`feature/ATT-6685`) allows instant revert via `git reset --hard` before merge.
