# Stage 5: Walkthrough & Verification - ATT-6685: MapDetailLayout: Fix multithreaded SnapshotStateObserver crash when calling GraphicsLayer.toImageBitmap() on Dispatchers.Default during Map Snapshot share

**Ticket**: [ATT-6685](https://rainerblind.atlassian.net/browse/ATT-6685)  
**Sub-task**: Pending `submit-stage` (`[Test]`)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath (WorkoutSummaries, Details & Graphs)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.2`  
**Requirement Mapping**: `REQ-REV-006` (`REQ-REV-006.5`)  
**Test Mapping**: `TST-REV-007` (`TST-REV-007.5`), `TST-LOC-001`, `TST-REG-001`  
**Branch**: `feature/ATT-6685`  
**Author**: AI Agent 1 (Senior ASPICE Architecture & Pro Verifier)  
**Date**: 2026-11-11  

---

## 1. Executive Summary & Verification Overview
* **Issue Addressed**: Resolved fatal UI-thread `IllegalArgumentException` (`Detected multithreaded access to SnapshotStateObserver: previousThreadId=131, currentThread={id=2, name=main}`) triggered during Map Snapshot sharing from `WorkoutDetailTabbedContent`.
* **Fix Applied**: In `MapDetailLayout.kt`, removed `withContext(Dispatchers.Default)` wrapping around `toImageBitmap().asAndroidBitmap()` for header, elevation, and analytics layers so that rasterization runs directly on the Main UI thread. Retained background dispatching in `combineWorkoutAndShare`. Added `try-catch-finally` to guarantee `isSharing = false` and `onSnapshotComplete?.invoke()` under all execution outcomes.
* **Verification Scope**: Tier 1 targeted unit & contract test suite across map and core components (`MapDetailLayoutSnapshotContractTest`, `WorkoutShareBottomSheetContractTest`, `ThemeColorCentralizationAuditTest`, `ui.components.core.*`) and living documentation governance checks.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-REV-006.5` | `TST-REV-007.5` | `MapDetailLayoutSnapshotContractTest` | **PASSED** | `Verified` |
| `REQ-REV-006` | `TST-REV-007` | `WorkoutShareBottomSheetContractTest` | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-REG-001` | Tier 1 Targeted Regression Suite | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.MapDetailLayout*" \
  --tests "com.atrainingtracker.trainingtracker.ui.map.WorkoutShareBottomSheetContractTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.components.core.*"
BUILD SUCCESSFUL
100% tests passed (0 failures, 0 regressions)
```

### Living Docs & Requirement Governance Check
```text
python3 tools/verify_requirement_governance.py
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Zero unmounted Composable islands detected in git diff.
PASS: Repository encapsulation verified (zero leaky DatabaseManager/Cursor members).
PASS: Zero architecture or system specification modifications detected.
Modified/Altered existing requirement(s) detected: REQ-REV-006.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Hardware / Physical Verification (Pixel 10 / Emulator)
* **Scope**: Concurrency and dispatcher affinity bugfix inside Compose snapshot callback.
* **Visual Parity**: No visual changes to Compose layout widgets or canvas theme colors. Zero APK runtime bytecode regressions for standalone and bottom-sheet map screens.
* **Error Resilience**: `try-catch-finally` prevents frozen UI states when sharing is cancelled or fails.

---

## 5. Invariant & Governance Verification
1. **Dispatcher Affinity Maintained**: `GraphicsLayer.toImageBitmap()` executes on UI thread; background combining remains on `Dispatchers.Default`.
2. **Lifecycle Reset Guaranteed**: `isSharing` resets cleanly in `finally`.
3. **Living Documentation Synchronized**: Living documentation shards updated (`REQ-REV-006.5` and `TST-REV-007.5` flipped to `Verified`).
4. **Parent Decision Gate Enforced**: Parent ticket ATT-6685 progressed to `Final Review (Human)` assigned to `human` (`rainer.blind`).
