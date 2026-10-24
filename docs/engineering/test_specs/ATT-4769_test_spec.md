# Stage 2: Requirement & Test Specification - ATT-4769: Restore Sequential Blue Color Palette in Heatmap LUT

**Ticket**: [ATT-4769](https://rainerblind.atlassian.net/browse/ATT-4769)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `sprint/2026-43.10` | **Branch**: `feature/ATT-4769`  
**Requirement Mapping**: `REQ-MAP-056.2` (refining `REQ-MAP-056`, realigning with `REQ-MAP-002`)  
**Test Spec ID**: `TST-MAP-058.3` (refining `TST-MAP-058`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-10-24  

---

## 1. Requirement Specification (REQ-MAP-056.2)

### 1.1 Problem Statement & Rationale
Sprint 2026-43.9 user review of `ATT-4480` verified the true line-density tile engine but mandated replacing the multi-hue thermal palette (Electric Blue $\rightarrow$ Magenta $\rightarrow$ Fiery Orange $\rightarrow$ Yellow $\rightarrow$ White) with the canonical sequential blue palette from `MapUtils.kt` (`ATT-500` / `ATT-504`): Light Cyan (`#00E5FF`) $\rightarrow$ Pure Blue (`#0000FF`) $\rightarrow$ Deep Indigo (`#311B92`) with calibrated translucent alpha progression.

### 1.2 Functional & Architectural Requirements
1. **3-Stop Sequential Blue LUT (`REQ-MAP-056.2`)**:
   - Stop 0.0: Light Cyan `#00E5FF` (`R=0, G=229, B=255`, base alpha ~20% / `51`).
   - Stop 0.5: Pure Blue `#0000FF` (`R=0, G=0, B=255`, base alpha ~30% / `77`).
   - Stop 1.0: Deep Indigo `#311B92` (`R=49, G=27, B=146`, base alpha ~70% / `180`).
2. **Alpha Scaling & Transparency**:
   - Index 0 SHALL be fully transparent (`0x00000000`).
   - Indices 1..255 SHALL interpolate base alpha and scale by `opacity` parameter.
3. **Lookup Performance**:
   - `getColor(intensity)` MUST remain a zero-allocation $O(1)$ lookup into precomputed `IntArray(256)`.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* `ThermalColorLut` with default opacity,
  *When* querying index 0,
  *Then* returned ARGB color is `0` (transparent).
* *Given* `ThermalColorLut`,
  *When* evaluating low (10), mid (128), and high (255) intensity entries,
  *Then* green component strictly decreases while blue and deep indigo dominate.
* *Given* `PolylineHeatmapTileProvider`,
  *When* rasterizing overlapping paths,
  *Then* tile renders smooth density progression in translucent blue hues without visual clashes.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-MAP-056.2` (realigning with `REQ-MAP-002` *Training heatmaps (Cyan -> Indigo gradient)* and `REQ-MAP-016` *Heatmap DSL Support*) under Epic `ATT-4474`, targeting `ThermalColorLut.kt` and `PolylineHeatmapTileProvider.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.16 (ATT-500, ATT-504) and Sprint 2026-43.9 (commit `06990c88`, ATT-4594 / ATT-4480).
3. *Root Reason for Existing Formulation*: A 5-stop fiery thermal gradient (Electric Blue -> Magenta -> Fiery Orange -> Yellow -> White) was introduced in `ThermalColorLut.kt` during initial line-density rasterizer prototyping to maximize contrast. Sprint 2026-43.9 user feedback established that this fiery scheme visually clashes with underlying maps and brand styling.
4. *Preservation of Core Invariants*: Reverting color stops to Light Cyan -> Pure Blue -> Deep Indigo restores brand harmony and subtle route readability without altering the underlying line-density accumulation raster engine, O(1) tile lookup, thread safety, or database integrity.

### 1.5 System Invariants
* Zero SQLite alterations, zero allocations during tile rasterization lookup, thread safety across tile workers, 100% clean-room test pass rate.

---

## 2. Test Specification (TST-MAP-058.3)

* **Test Case 1 (`TST-MAP-058.3`, Unit)**: In `PolylineHeatmapTileProviderTest.kt`, assert `lut.getColor(0) == 0`. Assert low index (10) has high green ($G \approx 220$) and blue ($B = 255$); mid index (128) has low green ($G \approx 0$) and pure blue ($B = 255$); high index (255) matches deep indigo (`R=49, G=27, B=146`). Assert green strictly decreases: $G_{low} > G_{mid} \ge G_{high}$.
* **Test Case 2 (`TST-MAP-058.1` / `TST-MAP-058.2`, Contract)**: Verify `getTile(x, y, zoom)` generates valid 256x256 PNG tiles with sequential blue palette and returns `NO_TILE` for disjoint coordinates.
* **Test Case 3 (`TST-MAP-058.4`, Concurrency)**: Verify thread-safe tile generation across 4 concurrent worker threads executing 20 tasks.
* **Test Case 4 (`TST-MAP-058.5`, Regression)**: Tier 1 unit tests (`com.atrainingtracker.trainingtracker.ui.map.*`) and Theme Audit pass with 100% success.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MAP-058.3` | Unit | `ThermalColorLut.getColor` | `REQ-MAP-056.2` | Specified |
| `TST-MAP-058.2` | Integration | `PolylineHeatmapTileProvider.getTile` | `REQ-MAP-056.1` | Specified |
| `TST-MAP-058.4` | Concurrency | `PolylineHeatmapTileProvider.getTile` | `REQ-MAP-056.3` | Specified |
| `TST-MAP-058.5` | Regression | Full map test suite + Theme Audit | `REQ-PRO-001`, `REQ-MAP-056.4` | Specified |
