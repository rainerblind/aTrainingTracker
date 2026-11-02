# Stage 2: Requirement & Test Specification - ATT-4933: Periods Heatmap Zoom-Dependent Stroke & Contrasting Palette

**Ticket**: [ATT-4933](https://rainerblind.atlassian.net/browse/ATT-4933)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-MAP-057` (`REQ-MAP-057.1`..`REQ-MAP-057.5`, refining `REQ-MAP-056` and `REQ-PER-024`)  
**Test Mapping**: `TST-MAP-059` (`TST-MAP-059.1`..`TST-MAP-059.5`)  
**Branch**: `feature/ATT-4933`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-11-01  

---

## 1. Overview & Verification Strategy
Sprint Review 2026-44.1 of `ATT-4833` on Pixel 10 (Juli 2026) revealed that the Periods heatmap suffers from two visual defects: (1) static 5px stroke width causes dense urban corridors (e.g. Schönaich) to merge into solid blocks on card mini-maps and low zoom levels; (2) cool cyan/blue/indigo `ThermalColorLut` blends into base map water bodies/streams and green terrain. `ATT-4933` resolves this by:
1. Dynamic zoom- and container-dependent stroke width: `strokeWidthPxForZoom(zoom: Int, isMiniMap: Boolean): Float` scales from 1.5f–2.0f at low zoom / mini-maps up to 4.5f–6.0f at high zoom on detailed maps.
2. Warm sequential "hot" palette: `ThermalColorLut.HOT` (Stop 0 Light Yellow `#FFF176` $\rightarrow$ Stop 1 Orange `#FF9800` $\rightarrow$ Stop 2 Red `#E53935` $\rightarrow$ Stop 3 Dark Red `#880E4F`) backed by semantic `TTColor` tokens for light & dark mode.
3. Muted desaturated base map style JSON applied to Periods heatmap views (`PeriodSummaryCard`, `InteractivePeriodMap`, `PeriodMapScreen`).
4. Modular parameterization keeping Workout Clusters (`ATT-4931`) decoupled.

---

## 2. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Component | Scope & Target |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-057.1` | `TST-MAP-059.1` | `PolylineHeatmapTileProviderTest` | Dynamic stroke width scales monotonically with zoom ($1.8\text{px}$ at zoom 10 up to $5.5\text{px}$ at zoom 16); mini-map mode applies scale dampening factor ($\approx 0.65\times$). |
| `REQ-MAP-057.2` | `TST-MAP-059.2` | `ThermalColorLutTest` / `PolylineHeatmapTileProviderTest` | `ThermalColorLut.HOT` maps index 0 transparent and interpolates Yellow $\rightarrow$ Orange $\rightarrow$ Red $\rightarrow$ Dark Red with increasing opacity; semantic tokens in `TTColor`. |
| `REQ-MAP-057.3` | `TST-MAP-059.3` | `PeriodMapScreenTest` / `PeriodSummaryCardTest` | Periods heatmap views configure desaturated/muted base map style, eliminating background water/terrain color competition. |
| `REQ-MAP-057.4` | `TST-MAP-059.4` | `PeriodMapVisualsTest` / `PolylineHeatmapTileProviderTest` | Parameterized API accepts customizable LUT and stroke scalers, preserving default behavior for Workout Clusters (`ATT-4931`). |
| `REQ-MAP-057.5` | `TST-MAP-059.5` | Targeted Test Suite | Full suite regression (`com.atrainingtracker.trainingtracker.ui.map.*` and `com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*`) 100% PASS. |

---

## 3. Concrete Test Scenarios (Given-When-Then)

### TST-MAP-059.1: Dynamic Zoom & Container Stroke Scaling
* **Given** a `PolylineHeatmapTileProvider` configured with zoom-adaptive stroke scaling,
* **When** tiles are requested at zoom 10, zoom 13, and zoom 16 for both standard and mini-map containers,
* **Then** stroke width satisfies $\text{width}(10) < \text{width}(13) < \text{width}(16)$ and $\text{width}_{\text{mini}}(z) < \text{width}_{\text{detail}}(z)$ at all zoom levels.

### TST-MAP-059.2: Warm Sequential "Hot" Palette Verification
* **Given** an accumulated corridor intensity mapped through `ThermalColorLut.HOT`,
* **When** evaluating pixel color at 0.05, 0.40, 0.75, and 1.00 intensity,
* **Then** colors progress strictly from Yellow to Orange to Red to Dark Red, with red/green components contrasting against green terrain and blue water.

### TST-MAP-059.3: Workout Clusters Decoupling & Regression Guard
* **Given** a caller specifying `colorLut = ThermalColorLut.BLUE` or legacy defaults,
* **When** tile generation executes,
* **Then** the blue palette is rendered without regression, proving modular decoupling for `ATT-4931`.

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-MAP-056.1` / `REQ-MAP-056.2` (*Line-Density Polyline Heatmap Tile Provider & 256-Entry Sequential Blue Color LUT*) and `REQ-PER-024.4` (*Corridor Frequency & Intensity Scaling*).
* **Historical Origin & Commit Trace**: Sprint 2026-40.16 (`ATT-504`), Sprint 2026-43.9 (commit `06990c88`, `ATT-4594` / `ATT-4480`), and Sprint Review 2026-44.1 of `ATT-4833`.
* **Root Reason for Existing Formulation**: Initial prototype adopted a static 5px stroke width and hardcoded 3-stop blue gradient (Cyan `#00E5FF` $\rightarrow$ Blue `#0000FF` $\rightarrow$ Indigo `#311B92`) for proof-of-concept tile rasterization without considering zoom scale or base map water/stream color collision.
* **Preservation of Core Invariants**: Zero main-thread tile rasterization (`Dispatchers.Default`), single-workout coverage mask accumulation (`ATT-4932`), logarithmic corridor normalization (`ATT-4929`), and decoupling/backward compatibility for Workout Clusters (`ATT-4931`) remain strictly preserved.

---

## 4. Invariants & Localization
* **No UI String Changes**: Zero user-facing string modifications; `TranslationParityTest` remains 100% PASS.
* **Theme Audit**: All new color tokens strictly declared in `TTColor.kt` and integrated with `ThemeColorCentralizationAuditTest`.
* **Target Release Parity**: `V4.9.41` verified as active/unreleased in Jira.
