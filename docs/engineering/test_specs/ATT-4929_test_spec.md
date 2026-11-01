# Stage 2: Requirement & Test Specification - ATT-4929: Heatmap Maximum Corridor Frequency Normalization

**Ticket**: [ATT-4929](https://rainerblind.atlassian.net/browse/ATT-4929)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-MAP-056` (`REQ-MAP-056.6`, `REQ-PER-024.4`)  
**Test Mapping**: `TST-MAP-058` (`TST-MAP-058.10`..`TST-MAP-058.12`)  
**Branch**: `feature/ATT-4929`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-11-01  

---

## 1. Overview & Verification Strategy
To eliminate premature saturation and loss of visual contrast across high-activity periods (e.g. 13x vs. 150x overlap corridors), `ATT-4929` replaces the static workout-count heuristic with dynamic normalization to the true period-wide maximum corridor frequency ($M$). A lightweight coarse-grid estimator (`estimateMaxCorridorPassCount`) computes $M$ once per period off the main thread. Tile rendering maps accumulated workout counts through a concave logarithmic curve ($I(c) = \text{round}(85 + 170 \cdot \frac{\ln(c)}{\ln(M)})$), ensuring $c=1$ renders in base cyan ($I=85$), intermediate counts remain strictly distinguishable, and only $c \ge M$ reaches Deep Indigo ($I=255$). Normalization is unified across `PeriodSummaryCard` and `InteractivePeriodMap`.

---

## 2. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Component | Scope & Target |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-056.6` | `TST-MAP-058.10` | `PolylineHeatmapTileProviderTest` | Synthetic periods with 1x, 13x, and 150x corridors yield strictly monotonic intensities ($I(1) = 85 < I(13) < I(150) = 255$); saturation only at maximum. |
| `REQ-MAP-056.6` | `TST-MAP-058.11` | `PolylineHeatmapTileProviderTest` | Coarse-grid estimator `estimateMaxCorridorPassCount` detects true maximum overlap in $< 10\text{ ms}$ on `Dispatchers.Default`. |
| `REQ-PER-024.4` / `REQ-MAP-056.6` | `TST-MAP-058.12` | `PeriodCardMapStateTest` & `PeriodMapVisualsTest` | `PeriodMapVisuals` propagates identical calibrated `maxCorridorPassCount` to both summary card and interactive period maps. |

---

## 3. Concrete Test Scenarios (Given-When-Then)

### TST-MAP-058.10: Synthetic 1x, 13x, 150x Corridor Monotonic Logarithmic Intensity Test
* **Given** a synthetic dataset with three distinct road corridors traversed 1x, 13x, and 150x times respectively, with $M = 150$,
* **When** rendered through `PolylineHeatmapTileProvider(maxCorridorPassCount = 150)`,
* **Then** the 1x corridor renders at base cyan ($I = 85$), the 13x corridor renders at intermediate blue ($I \approx 172$), and the 150x corridor reaches full saturation ($I = 255$), with strict monotonicity across all intermediate frequencies.

### TST-MAP-058.11: Coarse Spatial Grid Estimator Verification
* **Given** a collection of 50 geographic polylines sharing an overlapping urban corridor,
* **When** `estimateMaxCorridorPassCount(allPaths)` executes,
* **Then** the estimator returns the accurate maximum overlap count ($\ge 45$) within $< 10\text{ ms}$ on `Dispatchers.Default` without main-thread allocation spikes.

### TST-MAP-058.12: Unified Card Mini-Map & Detailed Map Heatmap Normalization
* **Given** a `PeriodSummary` for a high-workout month,
* **When** `PeriodMapUtils.getPeriodMapVisuals` constructs the card visuals and detail map visuals,
* **Then** both visual configurations receive the identical `maxCorridorPassCount` value, ensuring matching visual contrast.

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-024.4` (*Corridor Frequency & Intensity Scaling*) and `REQ-MAP-056.2` (*256-Entry Sequential Blue Color Lookup Table*).
* **Historical Origin & Commit Trace**: Commit `548f3995` (`ATT-4480`) and commit `2bea16b7` (`ATT-4833`).
* **Root Reason for Existing Formulation**: Static bracketed lookup on period workout count (`pathCount`) was adopted before per-pixel workout counting (`ATT-4932`) was established, causing premature saturation at 12 passes in large periods and failing to distinguish 13x vs. 150x corridors.
* **Preservation of Core Invariants**: Coarse grid computation off-thread preserves 60 fps main-thread performance; logarithmic transfer curve guarantees strict monotonicity and base cyan visibility ($I_{\text{base}} = 85$) for single passes.

---

## 4. Invariants & Localization
* **No String Changes**: Zero user-facing string modifications; `TranslationParityTest` remains 100% PASS.
* **Thread Safety**: Coarse grid estimation runs immutably on `Dispatchers.Default`; tile rendering remains thread-safe.
* **Target Release Parity**: Target Release `V4.9.41` verified against Jira active release.
