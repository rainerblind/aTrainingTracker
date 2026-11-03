# Stage 1 Analysis: ATT-4480 - Replace Gaussian HeatmapTileProvider with True Line-Density PolylineHeatmapTileProvider

**Ticket**: [ATT-4480](https://rainerblind.atlassian.net/browse/ATT-4480)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: Sprint 2026-43.9  
**Active Sprint**: `2026-43.9`  
**Branch**: `feature/ATT-4480`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-23  

---

## 1. Problem Statement & Motivation

Workout heatmaps (`MapUtils.kt:388-454`, `PeriodMapUtils.kt`) rely on Google Maps Utils `HeatmapTileProvider`:
* **Mathematical Mismatch**: 2D Gaussian point-cloud density produces artificial hot-spots on slow climbs, intersections, and sharp turns (10x vertex density/meter vs. fast flats) for single traversals.
* **Point Budget & Artifacts**: `densifyPath()` creates thousands of synthetic points; when budget exceeds `maxPoints`, sampling (`index % samplingStep == 0`) produces dotted/broken lines.
* **Low-Contrast Palette**: 20%–40% cyan/indigo overlay on blue polylines fails to convey dynamic range (1 vs. 5 vs. 50 passes look indistinguishable).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Root Cause**: `HeatmapTileProvider` evaluates point KDE rather than 1D linear trajectory integrals. It lacks path-aware projection and segment line-drawing.
* **Independent Peer Review & Verification (ATT-3534)**:
  * *Audit of Call Sites*: `MapUtils.createHeatmapProvider`, `PeriodMapUtils.getPeriodMapVisuals`, `MapContentScope.heatmap` (`produceState`), and `PeriodSummaryCard`. All accept or consume standard `TileProvider`.
  * *Line-Raster Feasibility*: Web Mercator projection converts $(lat, lon) \rightarrow (px, py)$ for tile $(x, y, z)$. Filtering polylines via bounding-box intersection $[lat_{min}, lon_{min}, lat_{max}, lon_{max}]$ achieves $O(1)$ per-workout culling before projection.
  * *Additive Blending & LUT*: Rasterizing polylines via `android.graphics.Canvas`, `Path`, and `PorterDuff.Mode.ADD` into an intensity buffer cleanly tracks traversal pass counts, which maps to ARGB via a 256-entry thermal LUT.
  * *Concurrency & Caching Guard*: Google Maps calls `TileProvider.getTile(x, y, zoom)` concurrently from background threads. `PolylineHeatmapTileProvider` must be strictly thread-safe, return `TileProvider.NO_TILE` immediately if no lines intersect, and encode PNG bytes efficiently.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Implement `PolylineHeatmapTileProvider: TileProvider` with Web Mercator projection and bounding-box culling.
  * Implement `ThermalColorLut` with 256-entry ARGB gradient (blue $\rightarrow$ cyan $\rightarrow$ amber $\rightarrow$ fiery red $\rightarrow$ hot white) for Light and Dark map styles.
  * Replace `createHeatmapProvider` and `HeatmapTileProvider` in `PeriodMapUtils.kt`, `MapUtils.kt`, and `MapContentScope.kt`.
  * Excise `densifyPath()` overhead and point sampling.
  * Unit tests validating tile projection, bounding box culling, LUT translation, and thread safety.
* **Out-of-Scope Non-Goals**:
  * Period SQLite persistence or N+1 queries (`ATT-4476`).
  * Timeline spacing in `PeriodBarGraph` (`ATT-4478`).
  * Interactive map anchor track separation (`ATT-4475`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-MAP-002`, `REQ-MAP-016`, `REQ-PER-010` in `docs/requirements/req_maps_navigation.md` and `req_aftermath_periods.md`.
* **Historical Origin & Commit Trace**: Commit `06990c88` (ATT-4594), `5d8a0c23` (ATT-1266), ATT-500, ATT-504.
* **Root Reason for Existing Formulation**: Google Maps `HeatmapTileProvider` was adopted as an off-the-shelf component with cyan-to-indigo gradient to avoid implementing custom Web Mercator rasterization.
* **Preservation of Core Invariants**: Replaces point KDE with line rasterization while preserving `TileProvider` interface compatibility, zoom adaptiveness, translucent background integration, and zero main-thread jank.

---

## 5. Architectural Strategy & High-Level Solution

| Component | Responsibility |
| :--- | :--- |
| `PolylineHeatmapTileProvider` | Implements `TileProvider`. Fast bbox intersection $\rightarrow$ Web Mercator project $\rightarrow$ `Canvas.drawPath` (`PorterDuff.Mode.ADD`) $\rightarrow$ LUT color mapping $\rightarrow$ PNG compression $\rightarrow$ `Tile`. |
| `ThermalColorLut` | Precomputed 256-entry int array mapping accumulated 8-bit density to thermal color ramp. |
| `PeriodMapUtils` / `MapContentScope` | Seamless drop-in replacement returning `TileProvider` instead of `HeatmapTileProvider`. |

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. `TileProvider` returns `TileProvider.NO_TILE` when no paths intersect the tile bounding box.
  2. Thread-safe execution across concurrent tile worker threads without memory leaks or bitmap recycling races.
  3. Strict zero-crash guarantee on empty paths, polar bounds, or invalid lat/lng coordinates.
* **Risk Rating**: **MEDIUM** (Custom tile rasterizer algorithm and Web Mercator mathematics; blast radius isolated to map heatmap overlays; zero impact on core sensor tracking, database schemas, or workout recording).
