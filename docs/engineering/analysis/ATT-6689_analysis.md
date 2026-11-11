# Stage 1 Analysis: ATT-6689 - Fix Graph-Only Sparkline 0px Collapse in IntrinsicSize.Min Rows & Session Reset of Sparkline Buffers

**Ticket**: [ATT-6689](https://rainerblind.atlassian.net/browse/ATT-6689)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6689` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
* **Context & User Observation (Sprint 2026-46.1 Review of `ATT-1294`)**: In the Cockpit layout editor (`EditSensorFieldDialog`), selecting **Diagramm** (*Graph*) while deselecting **Wert** (*Value*) so the tile displays the sparkline graph alone causes the graph not to render at all (empty tile).
* **Pre-Start / Session Bleed**: `LiveSparklineBuffer` accumulated samples in `BANALServiceRepository` are cleared only when entering `TrackingMode.READY`. Because connected sensors stream 1 Hz telemetry while waiting in `READY`, or across accumulators reset, pre-start samples bleed into *Full Workout* (`horizonSeconds = 0`) sparkline graphs.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Call Site Audit**:
  * `SensorSparklineView.kt` (lines 47–55): Defines `resolveSparklineHeight(viewSize, isGraphOnly = true)` returning `24.dp`, `36.dp`, `52.dp`, and `68.dp`.
  * `SensorFieldView.kt` (lines 457–465): Graph-only tiles (`!fieldState.showValue`) applied `Modifier.weight(1f)` directly without specifying minimum intrinsic height.
  * `SensorGridScreen.kt` (line 1054): Non-fill packed rows use `Row(modifier = Modifier.height(IntrinsicSize.Min))`.
  * `BANALServiceRepository.kt` (lines 229–231, 648–650): `clearBreadcrumbs()` invokes `clearSparklineBuffers()` only when `newTrackingMode == TrackingMode.READY`. No listener exists for `RESET_ACCUMULATORS_INTENT` or the transition `TrackingMode.READY -> TRACKING`.
  * `TrackingViewModel.kt` (lines 748–758): Polls `banalServiceRepository` every tick; has no explicit `clearSparklineBuffers()` reset method or tracking mode transition observation.
* **Independent Peer Review & Verification**:
  * *IntrinsicSize.Min Collapse*: In Jetpack Compose, `Canvas` has `minIntrinsicHeight = 0`. An unconstrained `Modifier.weight(1f)` child contributes 0 to the `Column` intrinsic height. Because `showValue = false` omits the metric text row, the entire row height collapses to header label height + padding. Inside `SensorSparklineView.kt:113`, `if (size.width <= 0f || size.height <= 0f) return@Canvas` triggers immediately, preventing polyline and axis rendering.
  * *Pre-Start Telemetry Accumulation*: While waiting in `TrackingMode.READY`, `BANALServiceRepository.startObservingBANALService()` continues ingesting 1 Hz telemetry into `sparklineBuffers`. When the athlete taps Start, the mode transitions `READY -> TRACKING`. Because `clearBreadcrumbs()` was only invoked upon entering `READY`, pre-start samples persist into the workout recording.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. In `SensorFieldView.kt`, apply `Modifier.heightIn(min = resolveSparklineHeight(fieldState.viewSize, isGraphOnly = true)).weight(1f)` when `!fieldState.showValue` so Graph-only tiles maintain intrinsic height in `IntrinsicSize.Min` rows.
  2. In `BANALServiceRepository.kt`, register a receiver for `BANALService.RESET_ACCUMULATORS_INTENT` and clear `sparklineBuffers` when transitioning `TrackingMode.READY -> TRACKING`.
  3. In `TrackingViewModel.kt`, expose `fun clearSparklineBuffers()` and observe `banalServiceRepository.trackingMode` to immediately clear buffers and UI state (`sparklineSamples = emptyList()`, `sparklineSpanSeconds = 0`) on workout start/reset.
  4. Author Compose layout/unit regression test verifying non-zero measured height of `SensorFieldView` inside `Row(Modifier.height(IntrinsicSize.Min))` when `showValue = false, showBar = false, showGraph = true`.
  5. Add unit regression tests in `TrackingViewModelSparklineTest` and `BANALServiceRepositorySparklineTest`.
* **Out-of-Scope Non-Goals**:
  1. Altering SQLite schema or table structure (DB version remains 19).
  2. Modifying `LiveSparklineBuffer` internal ring buffer or decimation logic.
  3. Modifying `SensorGridScreen.kt` row packing or scroll physics.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-059` (*Configurable Live Sparkline Graph Cockpit Tiles with Selectable Time Horizon Across Numeric SensorTypes*), specifically `REQ-TRK-059.5` (Tile Sparkline Rendering) and `REQ-TRK-059.6` (Singleton Repository Buffer Persistence). Targets `SensorFieldView.kt`, `BANALServiceRepository.kt`, and `TrackingViewModel.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-1294` (commit `a1a1b562`), refined by `ATT-6710` (commit `d272bd5f`), `ATT-6695` (commit `ef243477`), and `ATT-6690` (commit `84d754ee`).
* **Root Reason for Existing Formulation**: `ATT-1294` used `Modifier.weight(1f)` for Graph-only mode assuming the tile would expand to fill full screen height in portrait/landscape grids, overlooking `SensorGridScreen.kt:1054` multi-row packing with `IntrinsicSize.Min`. In `ATT-6710`, `clearSparklineBuffers()` was tied to `clearBreadcrumbs()` in `TrackingMode.READY`, missing pre-start sample accumulation during `READY` prior to tapping Start.
* **Preservation of Core Invariants**:
  1. Value + Graph mode sizing (`resolveSparklineHeight(fieldState.viewSize, isGraphOnly = false)`) preserved.
  2. Bar-only and Bar + Graph configurations preserved.
  3. Bounded buffer memory ($\le 300$ samples / $\le 1\text{ KB}$ per tile) preserved.
  4. 100% 9-language translation parity in `strings_cockpit.xml` preserved.

---

## 5. Architectural Strategy & High-Level Solution
* **SensorFieldView Layout Guard**: Replace `Modifier.weight(1f)` with `Modifier.heightIn(min = resolveSparklineHeight(fieldState.viewSize, isGraphOnly = true)).weight(1f)` in `SensorFieldView.kt` when `!fieldState.showValue`.
* **Repository Lifecycle Reset**: In `BANALServiceRepository.kt`, add `resetAccumulatorsReceiver` listening for `BANALService.RESET_ACCUMULATORS_INTENT` and invoke `clearSparklineBuffers()`. In `trackingModeReceiver`, trigger `clearSparklineBuffers()` when transitioning from `TrackingMode.READY` to `TrackingMode.TRACKING`.
* **ViewModel State Synchronization**: In `TrackingViewModel.kt`, add `clearSparklineBuffers()` to clear repository buffers and synchronously update `_uiState` field states with empty samples, and observe `banalServiceRepository.trackingMode` for `READY -> TRACKING`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Value-enabled tiles remain unaffected in layout and typography.
  2. Zero Compose snapshot state mutations inside composition (`Rule 66`).
  3. Sparkline samples survive drawer navigation and backgrounding, resetting strictly on workout start/reset.
* **Risk Rating**: **LOW** — Isolated layout modifier constraint fix and lifecycle reset hooks with zero database schema, background service, or IPC impact (2 SP).
