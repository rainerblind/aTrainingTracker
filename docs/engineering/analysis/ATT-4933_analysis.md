# Stage 1 Analysis: ATT-4933 - Periods Heatmap Look: Zoom-Dependent Stroke Width & Contrasting Palette vs. Base Map

**Ticket**: [ATT-4933](https://rainerblind.atlassian.net/browse/ATT-4933)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4933`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation
Sprint Review 2026-44.1 of `ATT-4833` on Pixel 10 (Juli 2026: 31 workouts) identified two visual deficiencies:
1. **Unscaled Stroke Width**: Heavy static stroke (~5px) on card mini-maps and low zoom levels merges dense town-centre street networks (e.g. Schönaich) into an unreadable solid block.
2. **Palette Camouflage**: Cool cyan/blue/indigo stops in `ThermalColorLut` match the hue of base map water bodies and streams and blend into green terrain, obscuring route density.

---

## 2. Forensic Investigation & Independent Verification (4-Augen-Prinzip)
* **Static Tile Stroke**: `PolylineHeatmapTileProvider.computeAccumulationBuffer()` uses a single static `strokeWidthPx = 5.0f` across all tile zoom levels (0..22). At zoom 10, a 5px line covers ~800m on the ground, destroying urban street legibility.
* **Hardcoded Blue LUT**: `ThermalColorLut.kt` hardcodes a 3-stop blue gradient (Cyan `#00E5FF` $\rightarrow$ Blue `#0000FF` $\rightarrow$ Indigo `#311B92`), clashing with terrain hydrology.
* **Unstyled Light Base Map**: `ATrainingTrackerMap.kt` defaults light mode to standard `MapType.TERRAIN` without desaturation, creating competing visual noise against heatmaps.
* **Card Mini-Map Parameterization**: `PeriodMapUtils.getPeriodMapVisuals()` assigns fixed stroke widths (4f..6f) without container size or dynamic zoom context.

---

## 3. User Scope Grounding & Human Clarification (Ceremony 1)
* **Human Clarification (Ceremony 1)**:
  * Zoom/size-dependent stroke width: thin for card mini-maps and low zoom levels (preserving street readability), wider when zoomed in.
  * Contrasting warm sequential "hot" palette: light yellow $\rightarrow$ orange $\rightarrow$ red $\rightarrow$ dark red via `TTColor` semantic tokens for light & dark mode.
  * Muted/desaturated base map style for Periods heatmap views (`PeriodSummaryCard`, `InteractivePeriodMap`, `PeriodMapScreen`).
  * Style parameterization: keep configuration modular so Workout Clusters (`ATT-4931`) can independently configure palette and stroke.
* **In-Scope**:
  * Dynamic zoom-adaptive stroke scaling in `PolylineHeatmapTileProvider` with mini-map container factor.
  * Warm "hot" sequential palette (`ThermalColorLut.HOT`) with `TTColor` tokens.
  * Muted desaturated base map style option for Periods maps.
  * Parameterized API preserving independent configuration for `ATT-4931`.
* **Out-of-Scope**:
  * Modifying Workout Clusters heatmap (`ATT-4931`).
  * Altering logarithmic corridor normalization (`ATT-4929`) or per-workout pixel mask logic (`ATT-4932`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-024` (*Line-Density Heatmap Visuals*) and `REQ-MAP-056` (*Polyline Heatmap Tile Provider*).
* **Historical Origin & Commit Trace**: `ATT-4480` (line-density rasterizer) and `ATT-4769` (3-stop blue LUT).
* **Root Reason for Existing Formulation**: Initial prototype focused on proof-of-concept line-density rasterization with fixed width and arbitrary blue stops.
* **Preservation of Core Invariants**: Zero main-thread tile rasterization (`Dispatchers.Default`), logarithmic normalization (`ATT-4929`), and single-workout mask accumulation (`ATT-4932`) remain untouched.

---

## 5. Architectural Strategy & Technical Design
1. **Adaptive Stroke Engine**: In `PolylineHeatmapTileProvider`, support zoom-dependent stroke evaluation:
   `fun strokeWidthForZoom(zoom: Int, isMiniMap: Boolean): Float` ($1.8\text{--}2.5\text{px}$ at zoom $\le 11$ / mini-maps; $3.5\text{--}6.5\text{px}$ at zoom $\ge 14$).
2. **Warm "Hot" Palette**: Add `HeatmapHotYellow`, `HeatmapHotOrange`, `HeatmapHotRed`, and `HeatmapHotDarkRed` to `TTColor`; construct `ThermalColorLut.HOT` (Stop 0 `#FFF176` $\rightarrow$ Stop 1 `#FF9800` $\rightarrow$ Stop 2 `#E53935` $\rightarrow$ Stop 3 `#880E4F`).
3. **Muted Map Style**: Introduce muted base map style JSON for Periods heatmap views to eliminate terrain/water hue competition.
4. **Modular Configuration**: Expose `colorLut` and `isMiniMap` / `strokeScaler` parameters in `PeriodMapVisuals` and `MapContentScope`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Thread-safe background tile generation on `Dispatchers.Default`.
  2. Workout Clusters (`ATT-4931`) retains independent palette/stroke settings without regression.
  3. Strict adherence to `TTColor` centralization audit.
* **Risk Rating**: **LOW** — Pure visual presentation enhancement; zero database or background service modifications.
