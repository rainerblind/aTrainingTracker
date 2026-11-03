# Stage 1 Analysis: ATT-4929 - Periods Heatmap Corridor Frequency Normalization

**Ticket**: [ATT-4929](https://rainerblind.atlassian.net/browse/ATT-4929)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4929`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation
Sprint Review 2026-44.1 of `ATT-4833` revealed that the Periods heatmap suffers from severe contrast loss in multi-workout views:
1. **Premature Clamping & Loss of Distinction**: Static lookup (`calculateStepIntensity(pathCount)`) saturates at 2–12 passes based solely on workout count. In a year with 150 workouts, a home corridor ridden 150 times and a road ridden 13 times both saturate to maximum indigo; frequent corridors do not stand out.
2. **Tile Discontinuity Risk**: Independent per-tile normalization would cause identical roads crossing tile boundaries to abruptly jump in color.
3. **Card vs. Map Parity**: Summary card mini-maps (`PeriodSummaryCard`) and detailed maps (`InteractivePeriodMap`) instantiate tile providers independently without unified frequency calibration.

---

## 2. Root Cause Analysis & Independent Verification (4-Augen-Prinzip)
Audit of `PolylineHeatmapTileProvider.kt` and `PeriodMapUtils.kt` confirms:
* **Workout Count Heuristic vs. Spatial Density**: `stepIntensity` is derived from aggregate `allPaths.size`, ignoring whether paths overlap or are spatially disjoint across a continent.
* **Linear vs. Compounded Perception**: Linear intensity scaling across high pass ranges either renders single passes invisible or clamps intermediate frequencies into a uniform indigo blob.
* **Decoupled Call Sites**: `PeriodMapUtils.getPeriodMapVisuals()` and `MapContentScope.heatmap()` construct `PolylineHeatmapTileProvider` without passing a shared period-wide corridor frequency ceiling.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Coarse spatial raster estimator (`estimateMaxCorridorPassCount`) executed once per period off the main thread to compute the global `maxCorridorPassCount` ($M$).
  * Non-linear concave intensity mapping (e.g. logarithmic: $I(c) = I_{\text{base}} + (255 - I_{\text{base}}) \cdot \frac{\ln(c)}{\ln(M)}$ for $M > 1$) ensuring $c=1$ renders at base cyan, intermediate frequencies remain distinguishable, and only $c=M$ reaches full saturation (255).
  * Unifying `maxCorridorPassCount` propagation across `PeriodMapUtils`, `PeriodSummaryCard`, and `InteractivePeriodMap`.
  * Deterministic unit tests with synthetic datasets (1x, 13x, 150x overlap) verifying monotonic progression and saturation only at maximum.
* **Out-of-Scope Non-Goals**:
  * Modifying `ThermalColorLut.kt` 3-stop color points (Cyan -> Blue -> Indigo) or alpha tables.
  * Database schema alterations or changes to GPS recording pipelines.
  * Changing Douglas-Peucker simplification tolerances (`getPeriodSimplificationToleranceMeters`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-024.4` (*Corridor Frequency & Intensity Scaling*) and `REQ-MAP-056.5` (*Line-Density Tile Provider*).
* **Historical Origin & Commit Trace**: Commit `548f3995` (`ATT-4480`) and commit `2bea16b7` (`ATT-4833`) introduced the bracketed `calculateStepIntensity` lookup as a preliminary heuristic.
* **Root Reason for Existing Formulation**: Static workout count lookup avoided spatial pre-rasterization overhead before `ATT-4932` established per-workout pixel counting.
* **Preservation of Core Invariants**: Web Mercator projection, $O(1)$ candidate culling, thread safety, and zero UI-thread jank are strictly preserved.

---

## 5. Architectural Strategy & Technical Design
1. **Coarse Spatial Grid Estimator**: `estimateMaxCorridorPassCount(allPaths: List<List<LatLng>>): Int` rasterizes bounding-box normalized paths onto a lightweight coarse grid (e.g. $128 \times 128$) off the main thread on `Dispatchers.Default` in $< 5\text{ ms}$.
2. **Non-Linear Logarithmic Transfer Curve**:
   * For $M = 1$: single pass maps to base cyan intensity ($I_{\text{base}} \approx 85$).
   * For $M > 1$: $I(c) = \text{round}\left(I_{\text{base}} + (255 - I_{\text{base}}) \cdot \frac{\ln(c)}{\ln(M)}\right)$ for $1 \le c \le M$.
3. **Provider Integration**: Add `maxCorridorPassCount: Int? = null` parameter to `PolylineHeatmapTileProvider`.
4. **Call Site Unification**: Propagate calibrated `maxCorridorPassCount` via `PeriodMapVisuals` so card mini-maps and full-screen maps render identically.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. 1 pass always renders in base cyan ($I \ge 85$); max corridor reaches Deep Indigo ($I = 255$).
  2. Monotonicity: $c_1 < c_2 \implies I(c_1) < I(c_2)$ across all intermediate counts.
  3. Spatial continuity: identical intensity across tile boundaries and zoom levels.
  4. 60 fps UI performance: coarse grid calculation executed strictly off the main thread.
* **Risk Rating**: **LOW** — Self-contained mathematical normalization and parameter propagation; zero schema or Android framework lifecycle risks.
