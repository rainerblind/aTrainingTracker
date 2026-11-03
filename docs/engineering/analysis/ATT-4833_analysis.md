# Stage 1: Analysis - ATT-4833: Review and polish Periods screen visual appearance, mini-maps, and summary cards

**Ticket**: [ATT-4833](https://rainerblind.atlassian.net/browse/ATT-4833)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Active Sprint**: `2026-44.1`  
**Author**: AI Agent 1 (Forensic Analyst)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Forensic Investigation

The Periods map experience presents two visual and ergonomic inconsistencies:
1. **Visual Clutter & Disconnected Overview**: In `InteractivePeriodMap`, when no individual workout is selected, up to 30 vector polylines, 5 anchor tracks, and numerous start/stop/apex markers are rendered over the map. This masks the route-frequency heatmap, creates visual confusion, and diverges from the clean heatmap preview on `PeriodSummaryCard`.
2. **Diluted Heatmap & Route Misalignment**:
   - In `ThermalColorLut`, single-pass base alpha was set to ~20% (effective alpha ~15%), making single routes virtually invisible against map canvas.
   - In `PolylineHeatmapTileProvider`, static workout-count `stepIntensity` caused high-volume periods (Month/Year) to require 15–28 passes to reach saturation, preventing prominent shared corridors from ever reaching Deep Indigo (1.0).
   - In `PeriodMapUtils`, Douglas-Peucker simplification tolerance was set to 15m/35m/75m for Week/Month/Year, causing identical roads on different days to deviate across pixels rather than stacking additively.
3. **Region State Propagation Gap**: When a period spans multiple geographic regions, tapping the card's map preview fails to pass the card's active `selectedRegionId` to `PeriodMapScreen`, causing the detailed view to default back to region 0 or arbitrary bounds.

---

## 2. Chesterton's Fence Audit

- **Historical Trace**: `ATT-440` (Cluster map algorithm), `ATT-4480` (PolylineHeatmapTileProvider introduction), `ATT-4769` (ThermalColorLut 3-stop palette), `ATT-1151` (Multi-region clustering).
- **Original Intent**: Vector polylines were initially drawn underneath heatmaps to ensure tap hit-testing and give immediate feedback while heatmaps rasterized. Multi-region badges were added to indicate scattered activities.
- **Why It Must Change**: True line-density heatmaps are self-sufficient, high-performance path visualizations. Vector tracks should only highlight a specific workout when selected/peeked. Tighter simplification tolerances ($\le 5\text{--}8\text{m}$) are computationally safe on modern devices while guaranteeing pixel-perfect corridor overlap.

---

## 3. Scope Bounding & Target Deliverables

1. **`ThermalColorLut.kt`**: Increase base alpha progression: Stop 0 (Light Cyan) $\approx 55\%$ ($140/255$), Stop 1 (Pure Blue) $\approx 80\%$ ($204/255$), Stop 2 (Deep Indigo) $100\%$ ($255/255$).
2. **`PolylineHeatmapTileProvider.kt`**: Implement adaptive corridor frequency normalization where $1$ pass renders vibrant base cyan, and $2$ (Week) to $12$ (Year) passes reach full Deep Indigo saturation.
3. **`PeriodMapUtils.kt`**: Lower simplification tolerance across all period levels to $\le 5\text{--}8\text{m}$ ($5\text{m}$ Day/Week, $6\text{m}$ Month, $8\text{m}$ Year).
4. **`PeriodSummaryCard.kt`**: Render pure heatmap in `PeriodMultiWorkoutMap` when heatmap is available; pass `activeRegionId` to `onMapClick(summary, activeRegionId)`.
5. **`PeriodsViewModel.kt` & `PeriodMapScreen.kt`**: Accept and apply `initialRegionId` in `showPeriodMap`; in detailed overview when no workout is peeked, suppress vector lines and pins, rendering pure frequency heatmap; render vector track and markers only when a single workout is peeked.

---

## 4. Invariants

- Zero SQLite schema alterations (`PeriodSummaries.db` remains on v27).
- 60 fps `LazyColumn` scrolling smoothness.
- True-Black AMOLED contrast compliance and 100% test pass rate across living test suites.
