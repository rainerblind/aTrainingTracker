# Stage 3: Implementation Plan - ATT-4932: Heatmap Per-Workout Coverage Mask & Unclamped Pixel Counting

**Ticket**: [ATT-4932](https://rainerblind.atlassian.net/browse/ATT-4932)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-MAP-056` (`REQ-MAP-056.1`, `REQ-MAP-056.5`)  
**Test Mapping**: `TST-MAP-058` (`TST-MAP-058.6`..`TST-MAP-058.9`)  
**Branch**: `feature/ATT-4932`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-11-01  

---

## 1. Problem Description & Background
Sprint Review 2026-44.1 of `ATT-4833` identified bead artifacts along single-pass tracks and dark oversaturation blobs at GPS pause/standstill jitter locations. In `PolylineHeatmapTileProvider.getTile()`, segments of the same polyline additively accumulate into a shared buffer clamped at 255 (`minOf(255, ...)`). Overlapping endpoint caps at shared vertices count twice, while tight standstill clusters accumulate to 255 after 2–3 segments. The buffer represents segment-cap counts rather than distinct workout passes, causing premature saturation and blocking true frequency normalization (`ATT-4929`).

---

## 2. Traceability & Requirements Mapping
* **`REQ-MAP-056.1` / `TST-MAP-058.6`**: Multi-vertex collinear path yields uniform interior intensity matching a 2-point segment with zero vertex beads.
* **`REQ-MAP-056.5` / `TST-MAP-058.7`**: GPS standstill jitter clusters contribute at most count = 1 for that workout path.
* **`REQ-MAP-056.5` / `TST-MAP-058.8`**: Two coincident identical workout paths accumulate to count = 2 across the corridor without intermediate clamping.
* **`REQ-MAP-056.3` / `TST-MAP-058.9`**: Scratch buffer allocated once per tile call; dirty-rect bounding-box accumulation and reset avoids full $256 \times 256$ zeroing overhead.

---

## 3. System Invariants & Preserved Behavior
1. **Thread Safety & Zero Race Conditions**: Stack/call-local scratch buffers ensure concurrent `getTile` calls on background tile threads remain isolated.
2. **Web Mercator & O(1) Candidate Culling**: Geographic bounding-box projection and disjoint tile culling (`TileProvider.NO_TILE`) remain intact.
3. **Palette & Color Contract**: Existing 3-stop sequential blue LUT (`ThermalColorLut`) and opacity scaling remain unchanged.
4. **Code Lock (Rule 4)**: Production source code remains locked until Gate 3 audit passes.

---

## 4. Proposed Architectural Changes (SWE.2)
* **Call-Local Scratch Mask (`PolylineHeatmapTileProvider.kt`)**:
  * Allocate a single `scratchMask = FloatArray(tileSize * tileSize)` and `accumulationBuffer = FloatArray(tileSize * tileSize)` per `getTile()` invocation.
* **Per-Segment `max` Rasterization**:
  * Modify `rasterizeSegment` to write into `scratchMask` using `scratchMask[idx] = max(scratchMask[idx], weight)`, tracking path dirty bounds $[pathMinX, pathMinY, pathMaxX, pathMaxY]$.
* **Unclamped Accumulation & In-Place Reset**:
  * Accumulate `accumulationBuffer[idx] += scratchMask[idx]` across dirty bounds, simultaneously resetting `scratchMask[idx] = 0f` for the next path without full-array zeroing.
* **Tile Finalization & Deferred LUT Mapping**:
  * Map unclamped accumulation values to color: `val intensity = (accumulationBuffer[idx] * stepIntensity).roundToInt().coerceIn(0, 255)`.

### UI Consistency (Rule 23)
* **Reference Component**: Existing Google Maps tile overlay pipeline (`PeriodMapScreen.kt`, `InteractivePeriodMap.kt`).
* **Visual Theme Tokens**: Canonical sequential blue palette (Cyan `#00E5FF` -> Pure Blue `#0000FF` -> Deep Indigo `#311B92`) preserved.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`PolylineHeatmapTileProvider.kt`)**: Refactor `getTile` to allocate `accumulationBuffer` and `scratchMask` (`FloatArray(tileSize * tileSize)`).
2. **Step 2 (`PolylineHeatmapTileProvider.kt`)**: Refactor `rasterizeSegment` to blend segment edge weights using `max` into `scratchMask` and update path dirty bounding box.
3. **Step 3 (`PolylineHeatmapTileProvider.kt`)**: Implement per-path dirty-rect accumulation into `accumulationBuffer` with immediate in-place `scratchMask` zeroing.
4. **Step 4 (`PolylineHeatmapTileProvider.kt`)**: Defer `colorLut` mapping to tile completion using unclamped accumulated workout counts.
5. **Step 5 (`PolylineHeatmapTileProviderTest.kt`)**: Implement `TST-MAP-058.6`, `TST-MAP-058.7`, `TST-MAP-058.8`, and `TST-MAP-058.9`.

---

## 6. Verification & Rollback Plan
* **Targeted Verification**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.PolylineHeatmapTileProviderTest"`
  * `python3 tools/verify_requirement_governance.py`
* **Rollback Plan**: Full git branch isolation on `feature/ATT-4932`.
