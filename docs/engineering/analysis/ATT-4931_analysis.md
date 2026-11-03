# Stage 1 Analysis: ATT-4931 - Decouple Workout Clusters Heatmap from Periods Styling

**Ticket**: [ATT-4931](https://rainerblind.atlassian.net/browse/ATT-4931)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4931`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation
Sprint Review 2026-44.1 of `ATT-4833` (Periods screen polish) revealed that shared heatmap changes altered Workout Clusters rendering (`WorkoutClusterHeatmapScreen.kt`):
1. **Oversaturated Color Stops**: `ATT-4833` raised `ThermalColorLut` base alphas from 20%/30%/70% to 55%/80%/100% and default opacity from 0.8 to 1.0.
2. **Accelerated Saturation**: `PolylineHeatmapTileProvider` replaced classic step intensity (saturating after 4..29 passes) with `calculateStepIntensity` (saturating in 2..12 passes).
3. **Forced Corridor Normalization**: `ATT-4929` forced `estimateMaxCorridorPassCount` onto all `MapContentScope.heatmap` callers, imposing logarithmic scaling and base cyan intensity (85) on cluster traces.
Because Workout Clusters was out of scope of `ATT-4833`, its heatmap became overly opaque and saturated without review.

---

## 2. Root Cause Analysis & Independent Peer Review (4-Augen-Prinzip)
Audit of `ThermalColorLut.kt`, `PolylineHeatmapTileProvider.kt`, and `MapContentScope.kt` confirms:
* **Shared Default Mutation**: `MapContentScope.heatmap` used `ThermalColorLut.DEFAULT`, which was modified in place for Periods instead of introducing a dedicated palette.
* **Tightly Coupled Intensity Logic**: `PolylineHeatmapTileProvider` defaulted to Periods-specific pass-intensity fractions instead of parameterized strategies.
* **Implicit Normalization Fallback**: `MapContentScopeImpl.Render` executes `data.maxCorridorPassCount ?: PolylineHeatmapTileProvider.estimateMaxCorridorPassCount(data.allPaths)`, preventing callers from opting out of corridor normalization.
* **Call Site Indirection**: `WorkoutClusterHeatmapScreen.kt` (line 560) relied on default `colorLut` and intensity parameters, directly inheriting Periods changes.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Formalize `ThermalColorLut.CLASSIC_BLUE` with pre-`ATT-4833` stops (20% / 30% / 70% alpha, opacity 0.8).
  * Formalize classic linear step intensity strategy in `PolylineHeatmapTileProvider` (<=5: 0.25f, <=20: 0.15f, <=60: 0.08f, <=150: 0.05f, >150: 0.035f).
  * Parameterize `MapContentScope.heatmap` with explicit `colorLut` and opt-in corridor normalization (`normalizeCorridors: Boolean = false`).
  * Explicitly configure `WorkoutClusterHeatmapScreen.kt` with `ThermalColorLut.CLASSIC_BLUE`, classic intensity, and disabled corridor normalization.
  * Unit tests verifying that Periods styling modifications cannot regress Workout Clusters heatmap configuration.
* **Out-of-Scope Non-Goals**:
  * Visual redesign or new color themes for Workout Clusters.
  * Modifications to Periods heatmap look (`ThermalColorLut.HOT`, dynamic stroke scaling, logarithmic normalization).
  * Changes to cluster clustering math, SQLite persistence, or route-linking logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-MAP-056.2` (*256-Entry Sequential Blue Color LUT*), `REQ-MAP-057.4` (*Modular Decoupling & Parameterization*), and `REQ-MAP-002` (*Training heatmaps*).
* **Historical Origin & Commit Trace**: Commit `548f3995` (`ATT-4480`) established the classic 4..29 pass saturation and 20%/30%/70% alpha stops. Commits `2bea16b7` (`ATT-4833`) and `bad11436` (`ATT-4929`) modified shared components directly without use-case isolation.
* **Root Reason for Existing Formulation**: Shared components were treated as Periods-only utilities during fast iteration, omitting explicit configuration boundaries.
* **Preservation of Core Invariants**: Web Mercator line-raster tile generation, single-workout mask accumulation (`ATT-4932`), thread safety, and zero allocations in raster loop are preserved.

---

## 5. Architectural Strategy & Technical Design
1. **Palette Separation (`ThermalColorLut.kt`)**: Add `CLASSIC_BLUE_STOPS` (alphas 51, 77, 180) and `ThermalColorLut.CLASSIC_BLUE` preset (`opacity = 0.8`), preserving `BLUE` and `HOT` for Periods.
2. **Intensity Strategy Decoupling (`PolylineHeatmapTileProvider.kt`)**: Expose `calculateClassicStepIntensity` alongside `calculateStepIntensity` (or `HeatmapIntensityStrategy`). Apply logarithmic normalization only when `maxCorridorPassCount` is explicitly provided.
3. **MapContentScope Parameterization (`MapContentScope.kt`)**: Add `normalizeCorridors: Boolean = false` to `heatmap()`, bypassing automatic corridor estimation when false.
4. **Explicit Call Sites**: Configure `WorkoutClusterHeatmapScreen.kt` to pass `colorLut = ThermalColorLut.CLASSIC_BLUE`, classic step intensity, and `normalizeCorridors = false`. `InteractivePeriodMap.kt` explicitly retains `ThermalColorLut.HOT` and `normalizeCorridors = true`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Periods heatmap visual appearance (`ThermalColorLut.HOT`, dynamic stroke scaling, logarithmic corridor normalization) is 100% preserved.
  2. Workout Clusters heatmap restores exact pre-`ATT-4833` visual appearance (20%/30%/70% alpha stops, opacity 0.8, classic saturation).
  3. All tile rasterization remains thread-safe on `Dispatchers.Default` with zero UI-thread blocking.
* **Risk Rating**: **LOW** — Pure parameterization and decoupled preset definitions without database or threading alterations.
