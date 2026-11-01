# Stage 2: Requirement & Test Specification - ATT-4932: Heatmap Per-Workout Coverage Mask & Unclamped Pixel Counting

**Ticket**: [ATT-4932](https://rainerblind.atlassian.net/browse/ATT-4932)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-MAP-056` (`REQ-MAP-056.1`, `REQ-MAP-056.5`)  
**Test Mapping**: `TST-MAP-058` (`TST-MAP-058.6`..`TST-MAP-058.9`)  
**Branch**: `feature/ATT-4932`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-11-01  

---

## 1. Overview & Verification Strategy
To eliminate vertex bead and standstill blob artifacts in line-density heatmap tiles (`PolylineHeatmapTileProvider`), rasterization switches from per-segment additive accumulation to per-workout coverage masks blended via `max`. Per-path masks are accumulated into an unclamped integer count buffer and mapped through `ThermalColorLut` at tile finalization. Performance overhead is bounded via a single reusable per-tile scratch buffer with dirty-rect clearing.

---

## 2. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Component | Scope & Target |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-056.1` / `.5` | `TST-MAP-058.6` | `PolylineHeatmapTileProviderTest` | Single straight path with $N \ge 5$ collinear vertices yields uniform intensity along the line (zero vertex beads). |
| `REQ-MAP-056.1` / `.5` | `TST-MAP-058.7` | `PolylineHeatmapTileProviderTest` | Clustered GPS standstill jitter / start-stop points contribute at most count = 1 (no blob oversaturation). |
| `REQ-MAP-056.1` / `.5` | `TST-MAP-058.8` | `PolylineHeatmapTileProviderTest` | Two distinct identical/coincident paths accumulate to count = 2 across all covered pixels without clamping. |
| `REQ-MAP-056.3` / `.5` | `TST-MAP-058.9` | `PolylineHeatmapTileProviderTest` | Thread-safe, single scratch buffer allocated per tile call; dirty-rect bounding box clearing avoids full $256 \times 256$ zeroing overhead. |

---

## 3. Concrete Test Scenarios (Given-When-Then)

### TST-MAP-058.6: Collinear Multi-Vertex Uniform Intensity Test
* **Given** a single straight path consisting of 10 collinear vertices across a tile,
* **When** rasterized by `PolylineHeatmapTileProvider.getTile`,
* **Then** all interior line pixels exhibit uniform intensity matching a single 2-point segment, with zero intensity peaks or beads at vertex coordinates.

### TST-MAP-058.7: Standstill Jitter Single-Count Masking Test
* **Given** a workout path containing 20 tightly clustered GPS jitter points within a 5-pixel radius,
* **When** rasterized into the tile buffer,
* **Then** the maximum accumulated count in the cluster region is exactly 1 (intensity $\le$ stepIntensity), preventing premature saturation blobs.

### TST-MAP-058.8: Multi-Workout Coincident Accumulation Test
* **Given** two separate, identical workout paths traversing the same corridor,
* **When** both paths are rendered by `PolylineHeatmapTileProvider`,
* **Then** the accumulated count along the corridor is exactly 2, and the final pixel color matches the two-pass LUT entry without clamping.

### TST-MAP-058.9: Bounded Scratch Buffer & Dirty-Rect Clearing
* **Given** candidate paths intersecting a tile,
* **When** `getTile` executes,
* **Then** a single scratch buffer is reused across all paths on the rendering thread, and only the dirty bounding rectangle $[minX, minY, maxX, maxY]$ is reset between paths.

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-MAP-056.1` (*Web Mercator Line-Raster TileProvider*) and `REQ-PER-024.4` (*Corridor Frequency & Intensity Scaling*).
* **Historical Origin & Commit Trace**: Commit `548f3995` (`ATT-4480`) and commit `2bea16b7` (`ATT-4833`).
* **Root Reason for Existing Formulation**: Per-segment additive accumulation directly into `intensityBuffer` was implemented as a fast prototype, but overlapping segment end-caps at vertices and standstill jitter clusters caused bead/blob artifacts and premature saturation.
* **Preservation of Core Invariants**: Web Mercator projection, $O(1)$ candidate culling, thread-safety, zero heap churn, and 100% clean-room test pass rate are strictly preserved.

---

## 4. Invariants & Localization
* **No String Changes**: Zero user-facing string additions; `TranslationParityTest` remains 100% PASS.
* **Thread Safety**: Concurrent calls to `getTile` operate on independent stack/local scratch buffers without shared mutable state.
* **Target Release Parity**: Verified against Jira active release `V4.9.41`.
