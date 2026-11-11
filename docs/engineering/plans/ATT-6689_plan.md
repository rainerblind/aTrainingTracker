# Stage 3: Implementation Plan - ATT-6689: Fix Graph-Only Sparkline 0px Collapse in IntrinsicSize.Min Rows & Session Reset of Sparkline Buffers

**Ticket**: [ATT-6689](https://rainerblind.atlassian.net/browse/ATT-6689)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6689`  
**Requirement Mapping**: `REQ-TRK-059.10` | **Test Mapping**: `TST-TRK-059.10` (`TST-TRK-059.10a`, `TST-TRK-059.10b`, `TST-TRK-059.10c`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
1. **Graph-Only `0px` Height Collapse in `IntrinsicSize.Min` Rows**: In `SensorFieldView.kt` (`lines 457–465`), when `!fieldState.showValue && fieldState.showGraph` (Graph-only tile), `SensorFieldView` applied `Modifier.weight(1f)` directly to `SensorSparklineView`. Because `SensorGridScreen.kt:1054` wraps non-fill packed rows in `Row(modifier = Modifier.height(IntrinsicSize.Min))` and Compose `Canvas` has `minIntrinsicHeight == 0`, the unconstrained weighted column contributes 0 to intrinsic height. The measured tile height collapses to header label height + padding, causing `SensorSparklineView.kt:113` (`if (size.width <= 0f || size.height <= 0f) return@Canvas`) to exit immediately without rendering the polyline or axis labels.
2. **Session Reset of `sparklineBuffers` on New Workout**: In `BANALServiceRepository.kt`, active `LiveSparklineBuffer` instances are cleared only when entering `TrackingMode.READY`. Sensors streaming 1 Hz telemetry while waiting in `READY` prior to tapping Start, or upon accumulator resets, bleed pre-start samples into *Full Workout* (`horizonSeconds = 0`) sparkline graphs.

---

## 2. System Invariants & Preserved Behavior
1. **Value + Graph & Bar-Only Tile Preservation**: Preserves `resolveSparklineHeight(fieldState.viewSize, isGraphOnly = false)` (`26.dp`, `34.dp`, `44.dp`, `52.dp`) when `showValue == true`, and all zone gauge, pedal balance, and W' battery gauge tracks.
2. **Zero Snapshot State Mutation in Composition (`Rule 66`)**: Sparkline rendering and modifier chaining remain pure; state mutations occur strictly in ViewModel/Repository.
3. **Task & Singleton Buffer Survival**: Buffers survive drawer navigation and backgrounding, resetting strictly on workout start/reset.
4. **UI Consistency**: Reuses `resolveSparklineHeight(fieldState.viewSize, isGraphOnly = true)` (`24.dp`, `36.dp`, `52.dp`, `68.dp`) established in `SensorSparklineView.kt`. Follows Material 3 theme tokens (`MaterialTheme.colorScheme.primary`, `onSurfaceVariant`) and 9-language translation parity for `sparkline_axis_now`.

---

## 3. Proposed Architectural Changes (SWE.2)
* **Presentation Layer (`SensorFieldView.kt`)**: In `SensorFieldView.kt`, update the modifier for `SensorSparklineView` when `!fieldState.showValue` to apply `Modifier.heightIn(min = resolveSparklineHeight(fieldState.viewSize, isGraphOnly = true)).weight(1f)`. This enforces non-zero intrinsic min height during `IntrinsicSize.Min` row measurement while allowing full vertical expansion in weight-fill grids.
* **Repository Layer (`BANALServiceRepository.kt`)**:
  - Register `resetAccumulatorsReceiver` listening for `BANALService.RESET_ACCUMULATORS_INTENT` via `registerInternalReceiver` and invoke `clearSparklineBuffers()`.
  - In `trackingModeReceiver`, detect state change from `TrackingMode.READY` to `TrackingMode.TRACKING` and invoke `clearSparklineBuffers()`.
* **ViewModel Layer (`TrackingViewModel.kt`)**:
  - Expose `fun clearSparklineBuffers()`, which invokes `banalServiceRepository.clearSparklineBuffers()` and synchronously clears `sparklineSamples = emptyList()` and `sparklineSpanSeconds = 0` across all `_uiState.value.fields`.
  - In `init`, observe `banalServiceRepository.trackingMode` to invoke `clearSparklineBuffers()` whenever `previousMode == TrackingMode.READY && mode == TrackingMode.TRACKING`.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Enforce Intrinsic Min Height in SensorFieldView (`REQ-TRK-059.10`, `TST-TRK-059.10a`)
* **Target Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldViewGraphOnlyLayoutTest.kt`.
* **Details**: Update `SensorFieldView.kt` sparkline modifier branch `!fieldState.showValue` to `Modifier.heightIn(min = resolveSparklineHeight(fieldState.viewSize, isGraphOnly = true)).weight(1f).padding(horizontal = 4.dp, vertical = 2.dp)`. Author `SensorFieldViewGraphOnlyLayoutTest.kt` verifying non-zero measured height $\ge \text{resolveSparklineHeight}$ inside `Row(Modifier.height(IntrinsicSize.Min))` for `SMALL`, `NORMAL`, `LARGE`, and `XLARGE`.

### Step 2: Repository Sparkline Buffer Reset on Mode Change & Broadcast (`REQ-TRK-059.10`, `TST-TRK-059.10b`)
* **Target Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepositorySparklineTest.kt`.
* **Details**: Add `resetAccumulatorsReceiver` for `BANALService.RESET_ACCUMULATORS_INTENT` calling `clearSparklineBuffers()`. In `trackingModeReceiver`, call `clearSparklineBuffers()` on `READY -> TRACKING`. Add unit tests in `BANALServiceRepositorySparklineTest.kt`.

### Step 3: TrackingViewModel Sparkline Buffer Reset & UI Synchronization (`REQ-TRK-059.10`, `TST-TRK-059.10c`)
* **Target Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModelSparklineTest.kt`.
* **Details**: Implement `clearSparklineBuffers()` in `TrackingViewModel.kt` updating `_uiState` field states. Collect `banalServiceRepository.trackingMode` to invoke `clearSparklineBuffers()` on `READY -> TRACKING`. Add unit tests in `TrackingViewModelSparklineTest.kt`.

### Step 4: Full Verification & Governance Check
* **Details**: Run unit tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldViewGraphOnlyLayoutTest" --tests "com.atrainingtracker.trainingtracker.repositories.BANALServiceRepositorySparklineTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModelSparklineTest"
  ```
  Run governance script: `python3 tools/verify_requirement_governance.py`.

---

## 5. UI Consistency Checklist
* **Reference Screen**: Live Cockpit Grid (`SensorGridScreen.kt`).
* **Height Scale**: `resolveSparklineHeight(viewSize, isGraphOnly = true)` (`24.dp`, `36.dp`, `52.dp`, `68.dp`).
* **Tokens**: `MaterialTheme.colorScheme.primary` (sparkline line/accent), `onSurfaceVariant` (axis labels), card background/border tokens preserved.
