# Stage 3: Implementation Plan - ATT-7318: [Bug] bt_bike_radar.xml & bike_radar.xml: Fix overlapping radar emission arcs with top-left mini-icon, rear wheel, and viewport edge

**Ticket**: [ATT-7318](https://rainerblind.atlassian.net/browse/ATT-7318)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-UI-514`  
**Test Mapping**: `TST-UI-514`  
**Branch**: `subagent-Lane-3-Agent---ATT-7318-self-dbceeb46`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background

In ATT-6699, `bt_bike_radar.xml` combined a top-left canonical mini-icon with colliding rear saddle arcs that cut through the mini-icon, sliced the rear wheel, and clipped past the left border. Meanwhile, `bike_radar.xml` lower arc endpoints plunged into the rear wheel ($y=44.0$ and $49.5$ vs wheel top $39.0$). This plan details the surgical geometric fixes and contract test additions.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-514` (*Dedicated ANT+ and Bluetooth LE Vector Sensor Badges for DeviceType.BIKE_RADAR*)
  * `REQ-UI-514.1`: ANT+ `bike_radar.xml` arc origin $(36.0, 26.0)$, concentric radii $[8.0, 14.0, 20.0]$, $\ge 3.0$ units clearance from wheel, back, and border.
  * `REQ-UI-514.2`: BLE `bt_bike_radar.xml` canonical cyclist family alignment (18 unadorned cyclist paths) and non-overlapping top-left `mini_icon_radar`.
* **Test Mapping**: `TST-UI-514` (`TST-UI-514.5` Geometric Bounding Box & Collision-Free Contract Test, `TST-UI-514.4` Tier 1 Regression Suite).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: All 8 existing tests in `VectorAssetResolutionContractTest.kt` continue to pass cleanly.
2. **Rule 37 Conformance**: All vector paths strictly respect $\le 45$ `L` commands and analytical curve primitives (`A`, `C`, `Q`).
3. **Protocol & Color Invariants**: Official Bluetooth `#0A3D91` badge background, full-bleed $0..144$ ($r=22$), ANT+ `#000000` background ($r=10$), bold `RDR` uppercase label with `evenOdd`.
4. **Code Lock**: Production code modifications remain locked until Gate 3 passes verification via `check-gate`.
5. **Parent Human Gate Invariance**: Terminal transition of ATT-7318 to `Erledigt` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `app/src/main/res/drawable/bt_bike_radar.xml`
- Delete 3 colliding saddle emission arcs from `cyclist` group (lines 142-160).
- Preserve exact 18 canonical cyclist paths matching `bt_bike_pwr.xml`.
- Preserve canonical `mini_icon_radar` in top-left quadrant ($x \in [12.8, 43.6], y \in [17.2, 36.0]$) with $\ge 7.7$ px separation from the cyclist silhouette.

### Component 2: `app/src/main/res/drawable/bike_radar.xml`
- Re-parameterize radar emission arcs path (line 35):
  `M 30.9,19.9 A 8.0,8.0 0 0,0 28.7,29.4 M 27.0,15.3 A 14.0,14.0 0 0,0 23.3,31.9 M 23.1,10.7 A 20.0,20.0 0 0,0 17.9,34.5`
- Origin $(36.0, 26.0)$, concentric radii $[8.0, 14.0, 20.0]$, spans $+50^\circ$ up to $-25^\circ$ down.
- Arc endpoints stay at $y \le 34.5$, guaranteeing $\ge 4.9$ units physical clearance above rear wheel ($y_{\text{top}} = 39.0$), $\ge 8.2$ units to cyclist, and $\ge 9.3$ units to card border.

### UI Consistency (Rule 23)
* **Reference component**: `bt_bike_pwr.xml` and `bike_pwr.xml`.
* **Reused components**: Standard vector path definitions and canonical sensor badge design system.
* **Theme tokens**: Background colors `#0A3D91` (BLE) and `#000000` (ANT+), foreground `#FFFFFF`.
* **New one-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: `bt_bike_radar.xml` Alignment
* **File**: `app/src/main/res/drawable/bt_bike_radar.xml`
* **Changes**: Remove redundant saddle arcs (lines 142-160).

### Step 2: `bike_radar.xml` Arc Origin & Geometry Update
* **File**: `app/src/main/res/drawable/bike_radar.xml`
* **Changes**: Replace path data on line 35 with concentric arcs having $y \le 34.5$ and $\ge 4.9$ units clearance to rear wheel.

### Step 3: Contract Test Implementation
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/VectorAssetResolutionContractTest.kt`
* **Changes**: Add `@Test fun testRadarVectorAssetsGeometricBoundingBoxAndCollisionFreeContract()` validating 18 cyclist paths, zero mini-icon collision in `bt_bike_radar`, and wheel/back/border clearance in `bike_radar`.

### Step 4: Verification Suite Execution
* **Command**: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.resources.VectorAssetResolutionContractTest"`
* **Expected Result**: All 9 unit tests pass cleanly.

---

## 6. Verification & Rollback Plan

* **Verification**: Run targeted unit test suite via `parallel_sprint_lock.py`, followed by living documentation check and git diff inspection.
* **Rollback**: Branch isolation allows full `git restore` without affecting `sprint/2026-46.4`.
