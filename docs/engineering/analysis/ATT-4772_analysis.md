# Stage 1 Analysis: ATT-4772 - Suppress Redundant Vector Polylines in PeriodSummaryCard Mini-Map

**Ticket**: [ATT-4772](https://rainerblind.atlassian.net/browse/ATT-4772)  
**Sub-task**: [ATT-4804](https://rainerblind.atlassian.net/browse/ATT-4804) (`[Analysis]`)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: Sprint 2026-43.10 | **Active Sprint**: `sprint/2026-43.10`  
**Branch**: `feature/ATT-4772`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Problem Statement & Motivation

In `PeriodSummaryCard.kt:551-565` (`PeriodMultiWorkoutMap`), every path in `allPaths` is unconditionally rendered as an individual vector `Polyline` composable. Following `ATT-4477`'s enablement of full-period track ingestion (`summary.polylines`), Month and Year cards render both 50–300 vector `Polyline` objects and a `TileOverlay` heatmap on top.
* **JNI & Composition Churn**: Composing hundreds of Google Maps vector polylines per card inside `LazyColumn` increases memory pressure and GPU draw calls.
* **Visual Washout**: Solid vector polylines bleed through and wash out the translucent `PolylineHeatmapTileProvider` gradient.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification (ATT-3534)

* **Root Cause**: `ATT-4477` moved polyline decoding to `Dispatchers.Default` but retained `PeriodMultiWorkoutMap`'s legacy `allPaths.forEach { Polyline(...) }` loop alongside `TileOverlay(tileProvider = it)`.
* **Independent Peer Review & Verification**:
  * *Threshold Constant Audit*: The ticket states `MAX_PERIOD_VECTOR_TRACKS = 25`. Forensic audit of `PeriodData.kt:30`, `PeriodMapScreen.kt:162`, `PeriodMapAdaptiveLayeringTest.kt:83`, and `req_aftermath_periods.md` confirms the canonical constant is `MAX_PERIOD_VECTOR_TRACKS = 30` (asserted in `testAdaptiveThresholdConstant_isThirty`). `PeriodSummaryCard` must reference this canonical constant directly (Coding Standards §1.1, DRY) rather than introducing a divergent literal (`25`).
  * *Adaptive Layering Invariant*: When `visuals.heatmapProvider != null` and `paths.size > MAX_PERIOD_VECTOR_TRACKS`, vector `Polyline` composables must be suppressed.
  * *Polyline Source Prioritization*: `prepareCardMapPreviewSync` (`PeriodMapUtils.kt:127-130`) already prioritizes `summary.polylines` over `summary.workoutIdToPolylineMap`. Explicit unit test coverage in `PeriodCardMapStateTest.kt` is required.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Add `renderVectorPolylines: Boolean` to `PeriodCardMapState` and gate `Polyline` rendering in `PeriodSummaryCard.kt`.
  * Align suppression threshold with `MAX_PERIOD_VECTOR_TRACKS` (suppress polylines when heatmap is active and paths $> 30$).
  * Add unit tests in `PeriodCardMapStateTest.kt` verifying adaptive suppression, small-period vector retention, and `summary.polylines` priority.
* **Out-of-Scope Non-Goals**:
  * Modifying `PeriodMapScreen.kt` or `InteractivePeriodMap.kt` (already stabilized in `ATT-4475`).
  * Altering `PeriodSummaries.db` schema or repository sync logic (`ATT-4476`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-PER-019.4` (*Regional Route Network Heatmap Integration*) and `REQ-PER-012` (*Period Map Scalability, Memory Budget & Adaptive Layering Invariant*). Targets `PeriodSummaryCard.kt`, `PeriodMapUtils.kt`, `PeriodCardMapStateTest.kt`.
* **Historical Origin & Commit Trace**: Commit `7e2151ec` (`ATT-1107`, established `MAX_PERIOD_VECTOR_TRACKS = 30`) and commit `da70647f` (`ATT-4477`, added `PeriodCardMapState`).
* **Root Reason for Existing Formulation**: `ATT-4477` decoupled polyline decoding from the UI thread but left card-level rendering unconditional.
* **Preservation of Core Invariants**: `DAY` cards and periods with $\le 30$ workouts retain crisp vector polylines; multi-region framing and dark-mode anti-flash overlays remain 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution

| Component | Modification |
| :--- | :--- |
| `PeriodCardMapState` (`PeriodMapUtils.kt`) | Add `val renderVectorPolylines: Boolean = true`, evaluated as `visuals?.heatmapProvider == null \|\| activePaths.size <= MAX_PERIOD_VECTOR_TRACKS`. |
| `PeriodMultiWorkoutMap` (`PeriodSummaryCard.kt`) | Pass `renderVectorPolylines` and guard `allPaths.forEach { Polyline(...) }` with `if (renderVectorPolylines)`. |
| `PeriodCardMapStateTest.kt` | Add tests for $> 30$ suppression, $\le 30$ retention, Day card polyline preservation, and `summary.polylines` source priority. |

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**: Zero `PeriodSummaries.db` schema changes; smooth 60 fps `LazyColumn` scrolling; zero regression on Day cards; 100% clean-room test pass rate.
* **Risk Rating**: **LOW** (Isolated Compose UI condition and pure state/test logic; zero database or background service risk).
