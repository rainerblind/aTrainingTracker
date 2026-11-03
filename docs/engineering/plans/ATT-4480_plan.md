# Stage 3: Implementation Plan - ATT-4480: Replace Gaussian HeatmapTileProvider with True Line-Density PolylineHeatmapTileProvider

**Ticket**: [ATT-4480](https://rainerblind.atlassian.net/browse/ATT-4480)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: Sprint 2026-43.9 | **Active Sprint**: `2026-43.9`  
**Requirement Mapping**: `REQ-MAP-056`, `REQ-PER-017`  
**Test Mapping**: `TST-MAP-058`, `TST-PER-023`  
**Branch**: `feature/ATT-4480`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Problem Description & Background

Google Maps `HeatmapTileProvider` computes 2D Gaussian point-cloud KDE over discrete `WeightedLatLng` points, creating artificial hot blobs on slow climbs/corners (10x point density/m) and requiring point densification/sampling. `ATT-4480` replaces this with `PolylineHeatmapTileProvider`, a true Web Mercator line-raster `TileProvider` that accumulates 1D line traversals via `PorterDuff.Mode.ADD` and maps intensity through a 256-entry thermal Color LUT.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-056` (*Line-Density Polyline Heatmap Tile Provider & 256-Entry Thermal Color LUT*)
  * `TST-MAP-058.1` (NO_TILE disjoint culling)
  * `TST-MAP-058.2` (Web Mercator tile PNG generation)
  * `TST-MAP-058.3` (Additive intensity accumulation & thermal LUT mapping)
  * `TST-MAP-058.4` (Concurrent worker thread safety)
* **Requirement**: `REQ-PER-017` (*True Line-Density Period Heatmap Visualization*)
  * `TST-PER-023.1` (PeriodMapVisuals integration: null for Day, active PolylineHeatmapTileProvider for Week/Month/Year)

---

## 3. System Invariants & Preserved Behavior

1. **TileProvider Contract**: `getTile(x, y, zoom)` returns `TileProvider.NO_TILE` immediately when tile bounds do not intersect candidate polylines.
2. **Thread Safety**: Complete thread safety across concurrent Google Maps tile fetch workers with no bitmap leaks.
3. **Period Hierarchy Parity**: Day periods remain clean polylines (`null` heatmap); Week, Month, and Year periods render progressive thermal density.
4. **Non-Breaking API Compatibility**: `PeriodMapVisuals` and `MapContentScope` accept standard `TileProvider`.
5. **No DB Regressions**: Zero changes to SQLite databases or sensor tracking lifecycles.

---

## 4. Proposed Architectural Changes

### Component 1: `PolylineHeatmapTileProvider` & `ThermalColorLut` (`ui.map`)
* Implements `TileProvider`.
* Pre-filters paths in $O(1)$ via geographic bounding-box intersection $[lat_{south}, lon_{west}, lat_{north}, lon_{east}]$.
* Projects $(lat, lon) \rightarrow (px, py)$ via Web Mercator equations: $worldX = \frac{lon + 180}{360} \cdot 256 \cdot 2^z$, $worldY = (0.5 - \frac{\ln(\tan(\frac{\pi}{4} + \frac{lat \cdot \pi}{360}))}{2\pi}) \cdot 256 \cdot 2^z$.
* Rasterizes intersecting paths onto a 256x256 bitmap using `Canvas.drawPath` with `PorterDuff.Mode.ADD` to record pass frequency.
* Maps 8-bit intensity channel through `ThermalColorLut` (electric blue $\rightarrow$ cyan $\rightarrow$ amber $\rightarrow$ fiery orange $\rightarrow$ incandescent white).
* Compresses to PNG and returns `Tile(256, 256, bytes)`.

### Component 2: `PeriodMapUtils.kt` & `MapContentScope.kt` Integration
* Updates `PeriodMapVisuals.heatmapProvider` type to `com.google.android.gms.maps.model.TileProvider?`.
* In `getPeriodMapVisuals`: Constructs `PolylineHeatmapTileProvider` without `densifyPath` or `maxPoints` truncations.
* In `MapContentScope`: Directly instantiates `PolylineHeatmapTileProvider` inside `produceState`.

### UI Consistency (Rule 23)
* **Reference Component**: Existing Google Maps `TileOverlay` in `PeriodSummaryCard` and `ATrainingTrackerMap`.
* **Reused Components**: `DarkMapStyle`, `TileOverlay`, standard Compose map properties.
* **Theme Tokens**: Thermal LUT tuned for contrast on both `DarkMapStyle` AMOLED tiles and `MapType.TERRAIN`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement `ThermalColorLut.kt` & `PolylineHeatmapTileProvider.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ThermalColorLut.kt`, `PolylineHeatmapTileProvider.kt`
* Changes: Web Mercator projection, bbox culling, additive line rasterization, thermal LUT translation, thread-safe `getTile`.

### Step 2: Refactor `PeriodMapUtils.kt`, `MapUtils.kt`, and `MapContentScope.kt`
* Files: `PeriodMapUtils.kt`, `MapUtils.kt`, `MapContentScope.kt`
* Changes: Change `HeatmapTileProvider` to `TileProvider`, route period visuals to `PolylineHeatmapTileProvider`, excise `densifyPath` in period pipelines.

### Step 3: Implement Unit & Concurrency Tests
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/PolylineHeatmapTileProviderTest.kt`, `PeriodMapVisualsTest.kt`
* Targeted Test Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit tests, `verify_requirement_governance.py`, and Stage 5 full-suite clean-room regression.
* **Rollback**: Clean git revert of Stage 4 commits on `feature/ATT-4480`.
