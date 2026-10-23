# Stage 3: Implementation Plan - ATT-4769: Restore Sequential Blue Color Palette in Heatmap LUT

**Ticket**: [ATT-4769](https://rainerblind.atlassian.net/browse/ATT-4769)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `sprint/2026-43.10` | **Branch**: `feature/ATT-4769`  
**Requirement Mapping**: `REQ-MAP-056.2` (realigning with `REQ-MAP-002`) | **Test Mapping**: `TST-MAP-058.3`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-10-24  

---

## 1. Problem Description & Background
`ThermalColorLut.kt` currently uses a 5-stop multi-hue thermal palette (blue-magenta-orange-yellow-white) that visually clashes with underlying map details. Review feedback from Sprint 2026-43.9 mandated restoring the brand-consistent 3-stop sequential blue gradient from `MapUtils.kt` (`#00E5FF` $\rightarrow$ `#0000FF` $\rightarrow$ `#311B92`).

---

## 2. Traceability & Requirements Mapping
* `REQ-MAP-056.2` (3-Stop Sequential Blue LUT & Translucent Alpha) $\rightarrow$ `TST-MAP-058.3`
* `REQ-MAP-056.1` (Web Mercator Line-Raster TileProvider) $\rightarrow$ `TST-MAP-058.1`, `TST-MAP-058.2`
* `REQ-MAP-056.3` (Thread Safety & Memory Headroom) $\rightarrow$ `TST-MAP-058.4`
* `REQ-MAP-056.4` (Invariants: Zero DB changes, 100% tests) $\rightarrow$ `TST-MAP-058.5`

---

## 3. System Invariants & Preserved Behavior
* **Zero Runtime Allocation**: Precomputed 256-entry `IntArray` in `ThermalColorLut.init`; $O(1)$ primitive lookup in `getColor`.
* **Zero DB Impact**: No schema, DAO, or telemetry changes.
* **Thread Safety**: Concurrent tile worker rendering in `PolylineHeatmapTileProvider` remains immutable and non-blocking.
* **UI Consistency (Rule 23)**: Matches `MapUtils.kt` (`ATT-500` / `ATT-504`), maintaining subtle route visibility without occluding street labels.

---

## 4. Proposed Architectural Changes

| Component | Responsibility / Proposed Change |
| :--- | :--- |
| `ThermalColorLut.kt` | Update `stops` to 3 sequential blue stops with base alpha:<br>• Stop 0.0: Light Cyan `#00E5FF` (`R=0, G=229, B=255`, base alpha = 51 / ~20%)<br>• Stop 0.5: Pure Blue `#0000FF` (`R=0, G=0, B=255`, base alpha = 77 / ~30%)<br>• Stop 1.0: Deep Indigo `#311B92` (`R=49, G=27, B=146`, base alpha = 180 / ~70%)<br>Interpolate RGB and base alpha, scale alpha by `opacity`. Index 0 = `0x00000000`. |
| `PolylineHeatmapTileProviderTest.kt` | Update `testThermalColorLutProgression` to assert Light Cyan $\rightarrow$ Pure Blue $\rightarrow$ Deep Indigo ($G_{low} > G_{mid} \ge G_{high}$, Blue dominance). |

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Color Stops & Alpha Progression in `ThermalColorLut.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ThermalColorLut.kt`
* Implement 3-stop sequential blue interpolation with translucent base alpha progression.

### Step 2: Update `PolylineHeatmapTileProviderTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/PolylineHeatmapTileProviderTest.kt`
* Update `testThermalColorLutProgression()` to verify green decrease, blue/indigo components, and transparency.

### Step 3: Execute Unit Tests & Verification
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*"`
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
* Run `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests and theme audit with 100% pass rate.
* **Rollback**: Clean git branch revert on `feature/ATT-4769` without side effects.
