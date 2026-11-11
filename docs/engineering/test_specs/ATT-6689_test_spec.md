# Stage 2: Requirement & Test Specification - ATT-6689: Fix Graph-Only Sparkline 0px Collapse in IntrinsicSize.Min Rows & Session Reset of Sparkline Buffers

**Ticket**: [ATT-6689](https://rainerblind.atlassian.net/browse/ATT-6689) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6689`  
**Requirement Mapping**: `REQ-TRK-059` (`REQ-TRK-059.10`) | **Test Spec ID**: `TST-TRK-059` (`TST-TRK-059.10`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (`REQ-TRK-059.10`)

### 1.1 Functional Scope & Technical Invariants
* **Graph-Only Intrinsic Min Layout Guard (`SensorFieldView.kt`)**: In `SensorFieldView.kt`, when `!fieldState.showValue && fieldState.showGraph` (Graph-only mode), the sparkline container SHALL be sized with `Modifier.heightIn(min = resolveSparklineHeight(fieldState.viewSize, isGraphOnly = true)).weight(1f)` (or `.height(resolveSparklineHeight(..., isGraphOnly = true))` when not filling screen height). This guarantees non-zero measured height matching `resolveSparklineHeight` (`24.dp`, `36.dp`, `52.dp`, `68.dp`) inside `Row(Modifier.height(IntrinsicSize.Min))` (such as `SensorGridScreen.kt:1054`), preventing 0px canvas collapse and early draw return.
* **Repository Lifecycle Reset (`BANALServiceRepository.kt`)**: The repository SHALL register a broadcast receiver for `BANALService.RESET_ACCUMULATORS_INTENT` and invoke `clearSparklineBuffers()`. In `trackingModeReceiver`, transitioning from `TrackingMode.READY` to `TrackingMode.TRACKING` SHALL invoke `clearSparklineBuffers()`, preventing pre-start 1-Hz telemetry samples accumulated during `READY` from bleeding into recorded workout sparklines.
* **ViewModel State Synchronization (`TrackingViewModel.kt`)**: `TrackingViewModel` SHALL expose `fun clearSparklineBuffers()` which delegates to `banalServiceRepository.clearSparklineBuffers()` and synchronously clears `sparklineSamples = emptyList()` and `sparklineSpanSeconds = 0` across all `_uiState` field states. `TrackingViewModel` SHALL observe tracking mode transitions to trigger buffer reset on session start.

### 1.2 Given-When-Then Acceptance Criteria
* **AC-1 (Graph-Only Row Height in IntrinsicSize.Min)**: *Given* a Cockpit tile configured with `showValue == false`, `showBar == false`, and `showGraph == true` inside `Row(Modifier.height(IntrinsicSize.Min))`, *When* measured by the Compose layout engine, *Then* the measured tile height is $\ge \text{resolveSparklineHeight(viewSize, isGraphOnly = true)}$ and `SensorSparklineView` renders the polyline and time axis labels.
* **AC-2 (Reset Accumulators Clears Buffers)**: *Given* active sparkline buffers containing accumulated samples, *When* `BANALService.RESET_ACCUMULATORS_INTENT` is broadcast, *Then* all registered `LiveSparklineBuffer` instances are cleared to empty.
* **AC-3 (READY -> TRACKING Session Transition)**: *Given* sensor data accumulating in sparkline buffers while idling in `TrackingMode.READY`, *When* tracking starts (`READY -> TRACKING`), *Then* sparkline buffers and `TrackingViewModel` UI states are immediately cleared, preventing pre-start data bleed.

### 1.3 System Invariants & Chesterton's Fence Audit
* *Original Requirement ID & Target*: Net-new sub-requirement `REQ-TRK-059.10`, extending `REQ-TRK-059.5` (Sparkline Rendering) and `REQ-TRK-059.6` (Repository Buffer Persistence) on `SensorFieldView.kt`, `BANALServiceRepository.kt`, and `TrackingViewModel.kt`.
* *Historical Origin & Commit Trace*: `ATT-1294` (`Modifier.weight(1f)` without min height), `ATT-6710` (buffer clearance only on entering `READY`), `ATT-6695` (X-axis labels), `ATT-6690` (stepped slider), and `ATT-6689` (layout min height & session reset).
* *Root Reason for Existing Formulation*: `ATT-1294` assumed weight distribution was sufficient without accounting for Compose `IntrinsicSize.Min` measuring canvas children with 0 min height; `ATT-6710` missed pre-start accumulation during `READY` before tapping start.
* *Preservation of Core Invariants*: Value-enabled layouts (`resolveSparklineHeight(..., isGraphOnly = false)`), Bar-only modes, bounded buffer memory ($\le 300$ samples / $\le 1\text{ KB}$ per tile), and 100% 9-language localization parity remain strictly preserved.

---

## 2. Test Specification & Traceability Matrix (`TST-TRK-059.10`)

### 2.1 Concrete Verification Procedures
* **`TST-TRK-059.10a` (SensorFieldView Graph-Only Min Height Layout Test)**: In `SensorFieldViewGraphOnlyLayoutTest.kt`, compose `SensorFieldView` inside `Row(Modifier.height(IntrinsicSize.Min))` with `showValue = false, showBar = false, showGraph = true` across `SMALL`, `NORMAL`, `LARGE`, and `XLARGE` ViewSizes; assert measured height equals or exceeds `resolveSparklineHeight(viewSize, isGraphOnly = true)`.
* **`TST-TRK-059.10b` (BANALServiceRepository Sparkline Lifecycle Reset Test)**: In `BANALServiceRepositorySparklineTest.kt`, feed samples during `READY`, transition to `TRACKING`, and assert `getSparklineSamples` is empty. Populate samples, broadcast `RESET_ACCUMULATORS_INTENT`, and assert `getSparklineSamples` is empty.
* **`TST-TRK-059.10c` (TrackingViewModel Sparkline Clear Test)**: In `TrackingViewModelSparklineTest.kt`, assert `clearSparklineBuffers()` empties `sparklineSamples` and resets `sparklineSpanSeconds` to `0` across all UI field states.

### 2.2 Traceability Matrix

| Req ID | Test Case | Target Component | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-059.10` | `TST-TRK-059.10a` | `SensorFieldViewGraphOnlyLayoutTest` | Non-zero measured height $\ge \text{resolveSparklineHeight}$ in `IntrinsicSize.Min` | Specified |
| `REQ-TRK-059.10` | `TST-TRK-059.10b` | `BANALServiceRepositorySparklineTest` | `clearSparklineBuffers` on `RESET_ACCUMULATORS_INTENT` & `READY -> TRACKING` | Specified |
| `REQ-TRK-059.10` | `TST-TRK-059.10c` | `TrackingViewModelSparklineTest` | ViewModel buffer clearing and `_uiState` sample/span reset | Specified |
| `REQ-PRO-001` | `TST-TRK-059.5` | Targeted Regression Suite | `--tests "com.atrainingtracker.trainingtracker.ui.tracking.*"` | Specified |
