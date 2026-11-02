# Stage 3: Implementation Plan - ATT-4933: Periods Heatmap Zoom-Dependent Stroke & Contrasting Palette

**Ticket**: [ATT-4933](https://rainerblind.atlassian.net/browse/ATT-4933)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-MAP-057` (`REQ-MAP-057.1`..`REQ-MAP-057.5`, refining `REQ-MAP-056` and `REQ-PER-024`)  
**Test Mapping**: `TST-MAP-059` (`TST-MAP-059.1`..`TST-MAP-059.5`)  
**Branch**: `feature/ATT-4933`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-11-01  

---

## 1. Problem Description & Background
Sprint Review 2026-44.1 of `ATT-4833` on Pixel 10 (Juli 2026) revealed that Periods heatmaps suffer from two visual flaws: (1) heavy static 5px stroke width merges town-centre route networks (e.g. Schönaich) into an unreadable solid block on card mini-maps and low zoom levels; (2) cool cyan/blue/indigo `ThermalColorLut` blends into terrain hydrology and green landscape. `ATT-4933` introduces dynamic zoom/container-adaptive stroke width scaling, a warm sequential "hot" palette (light yellow $\rightarrow$ orange $\rightarrow$ red $\rightarrow$ dark red via `TTColor`), and muted desaturated base map styling for Periods heatmap views, while keeping Workout Clusters (`ATT-4931`) decoupled.

---

## 2. Traceability & Requirements Mapping
* **`REQ-MAP-057.1` / `TST-MAP-059.1`**: Dynamic zoom-adaptive stroke width in `PolylineHeatmapTileProvider` ($1.8\text{--}2.5\text{px}$ at zoom $\le 11$; $4.5\text{--}6.0\text{px}$ at zoom $\ge 15$) with mini-map dampening ($\approx 0.65\times$, clamped $\le 2.2\text{px}$).
* **`REQ-MAP-057.2` / `TST-MAP-059.2`**: 4-stop warm sequential "hot" palette (`ThermalColorLut.HOT`) using semantic `TTColor` tokens (Light Yellow $\rightarrow$ Orange $\rightarrow$ Red $\rightarrow$ Dark Red).
* **`REQ-MAP-057.3` / `TST-MAP-059.3`**: Muted base map style option (`map_style_muted_light.json`) for Periods heatmap views to eliminate terrain/water hue collisions.
* **`REQ-MAP-057.4` / `TST-MAP-059.4`**: Modular parameterization preserving previous defaults (`ThermalColorLut.BLUE`) for Workout Clusters (`ATT-4931`).
* **`REQ-MAP-057.5` / `TST-MAP-059.5`**: Clean-room regression verification across `ui.map.*` and `periodlist.*` test suites.

---

## 3. System Invariants & Preserved Behavior
1. **Thread Safety & Non-Blocking Tile Generation**: All tile rasterization executes off-thread on `Dispatchers.Default`.
2. **Logarithmic Normalization & Single-Workout Counting**: `ATT-4929` ($M$-based logarithmic curve) and `ATT-4932` (single-pass mask accumulation) remain untouched.
3. **Decoupled Workout Clusters**: `ATT-4931` clusters heatmap remains customizable without behavioral regression.
4. **Code Lock (Rule 4)**: Production source code remains locked until Gate 3 audit passes.

---

## 4. Proposed Architectural Changes (SWE.2)
* **Design System Tokens (`Color.kt` / `TTColor`)**:
  * Define `HeatmapHotYellow` (`0xFFFFF176`), `HeatmapHotOrange` (`0xFFFF9800`), `HeatmapHotRed` (`0xFFE53935`), `HeatmapHotDarkRed` (`0xFF880E4F`).
* **LUT Enhancement (`ThermalColorLut.kt`)**:
  * Add `HOT` palette factory/instance: Stop 0 (`#FFF176`, 55% alpha) $\rightarrow$ Stop 1 (`#FF9800`, 80% alpha) $\rightarrow$ Stop 2 (`#E53935`, 92% alpha) $\rightarrow$ Stop 3 (`#880E4F`, 100% alpha). Retain `BLUE` default.
* **Dynamic Stroke Scaling (`PolylineHeatmapTileProvider.kt`)**:
  * Introduce `isMiniMap: Boolean = false` and compute effective stroke per tile:
    $\text{width}(z) = \text{clamp}(1.8 + (z - 10) \times 0.6, 1.8, 6.0) \times (\text{if } \text{isMiniMap } 0.65\text{ else } 1.0)$.
* **Muted Base Map Styling (`DarkMapStyle.kt` / `map_style_muted_light.json`)**:
  * Add desaturated light map style JSON; expose `resolveHeatmapMapProperties(isDark, context)` in `DarkMapStyle.kt`.
* **Period Map Wiring (`PeriodMapUtils.kt`, `PeriodSummaryCard.kt`, `InteractivePeriodMap.kt`, `MapContentScope.kt`)**:
  * Update `getPeriodMapVisuals` and `MapContentScope.heatmap` to pass `colorLut = ThermalColorLut.HOT` and `isMiniMap` context.

### UI Consistency (Rule 23)
* **Reference Component**: Existing Period Summary mini-maps and `InteractivePeriodMap.kt`.
* **Theme Tokens**: `TTColor.HeatmapHot*`, Material 3 surface elevation, and `DarkMapStyle`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`Color.kt` & `TTColor.kt`)**: Add warm heatmap semantic color tokens.
2. **Step 2 (`ThermalColorLut.kt`)**: Implement `ThermalColorLut.HOT` 4-stop gradient and support custom stop configurations.
3. **Step 3 (`PolylineHeatmapTileProvider.kt`)**: Add zoom-dependent stroke width calculation and `isMiniMap` dampening.
4. **Step 4 (`DarkMapStyle.kt` & Resource JSON)**: Create muted light map style and wire `resolveHeatmapMapProperties`.
5. **Step 5 (`PeriodMapUtils.kt`, `InteractivePeriodMap.kt`, `MapContentScope.kt`)**: Configure `ThermalColorLut.HOT`, `isMiniMap`, and muted map styling.
6. **Step 6 (Test Suite)**: Author/update `PolylineHeatmapTileProviderTest.kt`, `ThermalColorLutTest.kt`, and `ThemeColorCentralizationAuditTest`.

---

## 6. Verification & Rollback Plan
* **Targeted Verification**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `python3 tools/verify_requirement_governance.py`
* **Rollback Plan**: Complete git branch isolation on `feature/ATT-4933`.
