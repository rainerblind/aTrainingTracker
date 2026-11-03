# Stage 2: Requirement & Test Specification - ATT-4480: Replace Gaussian HeatmapTileProvider with True Line-Density PolylineHeatmapTileProvider

**Ticket**: [ATT-4480](https://rainerblind.atlassian.net/browse/ATT-4480)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: Sprint 2026-43.9 | **Active Sprint**: `2026-43.9` | **Branch**: `feature/ATT-4480`  
**Requirement Mapping**: `REQ-MAP-056`, `REQ-PER-017` (refining `REQ-MAP-002`, `REQ-MAP-016`, `REQ-PER-010`)  
**Test Spec ID**: `TST-MAP-058`, `TST-PER-023`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Requirement Specification (`REQ-MAP-056`, `REQ-PER-017`)

### 1.1 Problem Statement & Rationale
Google Maps `HeatmapTileProvider` evaluates a 2D Gaussian point-cloud kernel over discrete `WeightedLatLng` coordinates. Because GPS trackpoints cluster densely on slow ascents and corners (10x vertex density/meter vs. fast flats), single-pass climbs produce artificial hot-spots. Furthermore, `densifyPath()` creates thousands of synthetic points, while budget pruning drops vertices into disconnected polka-dots. The 20%–40% cyan/indigo overlay on blue polylines also washes out visual contrast. Replacing this with `PolylineHeatmapTileProvider` rasterizes polylines into Web Mercator tiles with additive blending (`PorterDuff.Mode.ADD`) and a 256-entry thermal color LUT, providing true line density without vertex bias.

### 1.2 Functional & Architectural Requirements
1. **Web Mercator Rasterization (`REQ-MAP-056.1`)**: `PolylineHeatmapTileProvider` SHALL implement `com.google.android.gms.maps.model.TileProvider`. For each tile $(x, y, zoom)$, candidate polylines SHALL be culled in $O(1)$ via bounding-box intersection. Disjoint tiles SHALL return `TileProvider.NO_TILE` immediately. Intersecting polylines SHALL be projected into $256 \times 256$ tile pixels and rendered as continuous paths via `Canvas`, `Path`, and `PorterDuff.Mode.ADD`.
2. **Thermal Color LUT (`REQ-MAP-056.2`)**: Accumulated single-channel intensity SHALL map to 32-bit ARGB via a 256-entry thermal LUT (cool electric blue $\rightarrow$ magenta $\rightarrow$ fiery orange $\rightarrow$ hot white).
3. **Period Map Integration (`REQ-PER-017.1`)**: `PeriodMapVisuals.heatmapProvider` SHALL type as `TileProvider?`. `getPeriodMapVisuals` SHALL instantiate `PolylineHeatmapTileProvider` for Week, Month, and Year periods without calling `densifyPath()` or capping points. Day periods SHALL emit `null`.
4. **Thread Safety & Zero Allocations (`REQ-MAP-056.3`)**: `getTile(x, y, zoom)` SHALL be thread-safe across concurrent tile worker threads without memory leaks or bitmap recycling races.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-MAP-002`, `REQ-MAP-016`, `REQ-PER-010`, targeting `MapUtils.kt`, `PeriodMapUtils.kt`, and `MapContentScope.kt`.
* **Historical Origin & Commit Trace**: Commit `06990c88` (ATT-4594), `5d8a0c23` (ATT-1266), ATT-500, ATT-504.
* **Root Reason for Existing Formulation**: `HeatmapTileProvider` was an off-the-shelf component adopted with cyan-to-indigo gradient to avoid implementing custom Web Mercator line-raster shaders.
* **Preservation of Core Invariants**: Standard `TileProvider` compatibility, zoom adaptiveness, transparent overlay blending, and zero main-thread jank strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Uniform Speed Invariance)**: *Given* a workout polyline with varying speed/vertex spacing, *When* rendered by `PolylineHeatmapTileProvider`, *Then* rendered line intensity is uniform along the segment.
* **Criterion 2 (Disjoint Bounds Early-Exit)**: *Given* a tile coordinate $(x, y, zoom)$ disjoint from all polylines, *When* `getTile` executes, *Then* `TileProvider.NO_TILE` is returned immediately without allocating bitmaps.
* **Criterion 3 (Dynamic Color Progression)**: *Given* overlapping paths with 1, 5, and 20+ passes, *When* rendered, *Then* the thermal LUT maps each pass level to distinct hues from electric blue to white-hot.

---

## 2. Test Specification (`TST-MAP-058`, `TST-PER-023`)

### Test Cases
* `TST-MAP-058.1` (**NO_TILE Disjoint Culling**): Assert `getTile(x, y, zoom)` returns `TileProvider.NO_TILE` when tile bounds do not intersect any path bounding boxes.
* `TST-MAP-058.2` (**Intersecting Tile Generation**): Assert intersecting paths produce non-null 256x256 PNG `Tile` bytes.
* `TST-MAP-058.3` (**Additive Intensity Progression**): Assert overlapping paths accumulate intensity and translate through the 256-entry thermal LUT.
* `TST-MAP-058.4` (**Concurrent Thread Safety**): Execute concurrent `getTile` calls across multiple threads; assert zero crashes, deadlocks, or bitmap corruption.
* `TST-PER-023.1` (**Period Map Visuals Integration**): Assert `getPeriodMapVisuals` emits `null` for `PeriodType.DAY` and non-null `PolylineHeatmapTileProvider` for Week, Month, and Year periods.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Function Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MAP-058.1` | Unit | `PolylineHeatmapTileProvider.getTile` | `REQ-MAP-056.1` | Planned |
| `TST-MAP-058.2` | Unit | `PolylineHeatmapTileProvider.getTile` | `REQ-MAP-056.1` | Planned |
| `TST-MAP-058.3` | Unit | `PolylineHeatmapTileProvider`, `ThermalColorLut` | `REQ-MAP-056.2` | Planned |
| `TST-MAP-058.4` | Concurrency | `PolylineHeatmapTileProvider.getTile` | `REQ-MAP-056.3` | Planned |
| `TST-PER-023.1` | Unit | `PeriodMapUtils.getPeriodMapVisuals` | `REQ-PER-017.1` | Planned |
