# Stage 1 Analysis: ATT-4932 - Heatmap: Count Each Workout Once Per Pixel

**Ticket**: [ATT-4932](https://rainerblind.atlassian.net/browse/ATT-4932)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4932`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation
Sprint Review 2026-44.1 of `ATT-4833` on the Pixel 10 (Periods -> Monate -> Juli 2026, 31 workouts) revealed severe visual rendering artifacts in the Periods heatmap:
1. **Vertex Beads**: Single-pass tracks render as chains of dark beads at each GPS vertex rather than uniform lines.
2. **Standstill Blobs**: Track start/stop points and GPS pause/standstill jitter render as massive, oversaturated dark blobs.
3. **Premature Saturation**: Corridors saturate to deep indigo after only 2–3 passes; frequently vs. rarely used streets are indistinguishable.
4. **Tolerance Magnification**: `ATT-4833` reduced simplification tolerance (75 m down to 8 m for Year cards), multiplying vertex density and exacerbating bead artifacts.

---

## 2. Root Cause Analysis & Independent Peer Review (4-Augen-Prinzip)
Audit of `PolylineHeatmapTileProvider.kt` (lines 140–250) confirms the root cause in `getTile()` and `rasterizeSegment()`:
* **Per-Segment Additive Blending**: `getTile()` iterates over every segment $(P_i, P_{i+1})$ of every candidate path, rasterizing each as a capsule with round endpoint caps of radius $R$ and adding intensity directly to `intensityBuffer[idx] = minOf(255, buffer[idx] + addedVal)`.
* **Vertex Cap Overlap**: Consecutive segments $(P_{i-1}, P_i)$ and $(P_i, P_{i+1})$ share vertex $P_i$. The round caps overlap around $P_i$, summing intensities and counting every vertex twice.
* **Standstill Jitter Clustering**: Multiple jitter points within distance $d < 2R$ create numerous overlapping segment caps, summing repeatedly to 255.
* **Premature Clamping & Semantic Mismatch**: The buffer does not track "number of workouts passing this pixel", but "number of segment caps covering this pixel", prematurely clamped at 255 and preventing subsequent normalization (`ATT-4929`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Per-workout coverage mask using `max` (not sum) blending across segments of the same path so each workout contributes at most 1.0 (with anti-aliasing) per pixel.
  * Accumulating per-path coverage masks into an unclamped count buffer; color mapping via `colorLut` only at the end.
  * Reusable per-tile scratch buffer with dirty-rect bounding-box accumulation and clearing ($O(\text{dirty pixels})$).
  * Unit tests: collinear multi-vertex path has uniform intensity; standstill jitter yields count 1; two identical paths yield count 2.
* **Out-of-Scope Non-Goals**:
  * Global/period-wide maximum frequency normalization across tiles/zoom levels (dedicated scope of `ATT-4929`).
  * Changes to `ThermalColorLut.kt` palette or alpha interpolation (`REQ-MAP-056.2`).
  * Database schema alterations or Google Maps vector polyline modifications.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-MAP-056.1` (*Web Mercator Line-Raster TileProvider*) and `REQ-PER-024.4` (*Corridor Frequency & Intensity Scaling*).
* **Historical Origin & Commit Trace**: Commit `548f3995` (`ATT-4480`) introduced `PolylineHeatmapTileProvider` with per-segment additive accumulation; commit `2bea16b7` (`ATT-4833`) tightened simplification tolerance to 8 m, amplifying bead frequency.
* **Root Reason for Existing Formulation**: Per-segment additive accumulation was chosen during prototyping as a lightweight approximation of additive canvas blending without per-path layer separation.
* **Preservation of Core Invariants**: Web Mercator projection, $O(1)$ bounding-box culling, thread-safety, and zero persistent allocations are strictly preserved.

---

## 5. Architectural Strategy & Technical Design
1. **Per-Tile Scratch Buffer**: Allocate a single reusable `pathMask` buffer (size $256 \times 256$, fixed-point `IntArray` or `FloatArray`) per `getTile()` invocation (thread-local/call-local, zero cross-thread contention).
2. **Per-Path `max` Rasterization**: For each path candidate, rasterize all segments into `pathMask` using `pathMask[idx] = max(pathMask[idx], segmentCoverage)` while expanding a path dirty bounding box `[minX, minY, maxX, maxY]`.
3. **Unclamped Workout Accumulation**: For pixels inside the dirty rect, add `accumulationBuffer[idx] += pathMask[idx]`.
4. **Dirty-Rect Reset**: Zero only the dirty bounding box of `pathMask` for the next path ($O(\text{dirty pixels})$).
5. **Final Deferred Color LUT Mapping**: Map accumulated counts to color intensity at the tile boundary (`(count * stepIntensity).roundToInt().coerceIn(0, 255)`), preparing clean inputs for `ATT-4929`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Straight collinear path with $N$ vertices yields uniform intensity along the line (zero vertex beads).
  2. Standstill jitter / start-stop clustering contributes at most count 1 for that workout.
  3. Concurrent `getTile` calls on background threads remain completely thread-safe without shared mutable state.
  4. Zero heap churn during tile rendering: scratch buffer allocated once per tile call.
* **Risk Rating**: **LOW** — Self-contained rasterization algorithm refactoring within `PolylineHeatmapTileProvider.kt`, verified by deterministic JVM unit tests with zero framework or database dependencies.
