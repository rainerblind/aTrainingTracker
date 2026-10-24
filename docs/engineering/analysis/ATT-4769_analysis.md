# Stage 1 Analysis: ATT-4769 — Restore Sequential Blue Palette in PolylineHeatmap Color LUT

**Ticket**: [ATT-4769](https://rainerblind.atlassian.net/browse/ATT-4769)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `sprint/2026-43.10` | **Branch**: `feature/ATT-4769`  
**Refining Requirement**: `REQ-MAP-056.2` (realigning with `REQ-MAP-002`) | **Test Spec**: `TST-MAP-058.3`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-10-24  

---

## 1. Problem Statement & Root Cause Analysis
* **Root Cause**: In `ThermalColorLut.kt` (introduced in `ATT-4480`), color stops were hardcoded to a 5-stop multi-hue thermal gradient (Electric Blue $\rightarrow$ Magenta $\rightarrow$ Fiery Orange $\rightarrow$ Yellow $\rightarrow$ White).
* **Deficiency**: While mathematically sound, Sprint 2026-43.9 user review feedback established that this high-contrast fiery scheme visually clashes with underlying topographic/vector maps and brand styling.
* **Objective**: Revert the LUT stops to the proven, aesthetically harmonious 3-stop sequential blue gradient from `MapUtils.kt` (`ATT-500` / `ATT-504`): Light Cyan (`#00E5FF`) $\rightarrow$ Pure Blue (`#0000FF`) $\rightarrow$ Deep Indigo (`#311B92`) with calibrated translucent alpha progression.

---

## 2. Chesterton's Fence Forensics
* **Original Motivation (ATT-4480)**: Multi-hue thermal palette was introduced during the initial line-density rasterizer prototype to maximize contrast on high-pass segments.
* **Why Safe to Refactor**: Reviewers found the blue sequential palette far superior for route density readability without occluding map roads/labels. The line-density raster engine (`PolylineHeatmapTileProvider`) operates independently of color stops; altering `ThermalColorLut` stops preserves the underlying accumulation algorithm without architectural side effects.

---

## 3. Scope Bounding & Proposed Architecture

| Component | Responsibility / Proposed Changes |
| :--- | :--- |
| `ThermalColorLut.kt` | • Replace 5-stop gradient with 3-stop sequential blue gradient:<br>  - Low (0.0): Light Cyan `#00E5FF` (`R=0, G=229, B=255`, base alpha ~20% / `51`)<br>  - Mid (0.5): Pure Blue `#0000FF` (`R=0, G=0, B=255`, base alpha ~30% / `77`)<br>  - High (1.0): Deep Indigo `#311B92` (`R=49, G=27, B=146`, base alpha ~70% / `180`)<br>• Alpha scaling: interpolate base alpha and scale by `opacity`.<br>• Index 0 remains fully transparent (`0`). |
| `PolylineHeatmapTileProviderTest.kt` | Update `testThermalColorLutProgression` to assert Light Cyan $\rightarrow$ Pure Blue $\rightarrow$ Deep Indigo progression (Green decrease, Blue/Indigo dominance). |
| Living Docs | Update `REQ-MAP-056.2` in `req_maps_navigation.md` and `TST-MAP-058.3` in `tst_maps_navigation.md`. |

---

## 4. Invariants & Governance Checklist
* **Zero Allocation Runtime**: 256-entry `IntArray` remains precomputed at construction; `getColor(i)` remains zero-alloc $O(1)$ table lookup.
* **Thread Safety**: Immutable precomputed table is fully safe across concurrent tile fetch workers.
* **Zero Database Impact**: Schema, DAOs, and repository caching untouched.
* **Test Suite Verification**: 100% pass across `PolylineHeatmapTileProviderTest`, theme audit, and `verify_requirement_governance.py`.
