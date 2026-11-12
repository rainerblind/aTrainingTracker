# Stage 1 Analysis: ATT-7318 - [Bug] bt_bike_radar.xml & bike_radar.xml: Fix overlapping radar emission arcs with top-left mini-icon, rear wheel, and viewport edge

**Ticket**: [ATT-7318](https://rainerblind.atlassian.net/browse/ATT-7318)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.4`  
**Branch**: `subagent-Lane-3-Agent---ATT-7318-self-dbceeb46`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

During Ceremony 2 review of ATT-6699 (introduction of `bike_radar.xml` and `bt_bike_radar.xml`), critical geometric collisions and boundary violations were discovered:
1. In `app/src/main/res/drawable/bt_bike_radar.xml` ($144 \times 144$ viewport), two redundant radar representations were rendered simultaneously: a top-left canonical `mini_icon_radar` ($x \in [12.8, 43.6], y \in [17.2, 36.0]$) and three rear saddle radar arcs ($r=13, 22, 31$ inside `<group android:name="cyclist" android:translateX="18.18">`). The saddle arcs cut directly through `mini_icon_radar` in the top-left quadrant, the lower halves ($y=51..74$) sliced across the rear bicycle tire ($y=52..93$) and seat stay, and the outer arc ($r=31$) clipped past the left viewport boundary ($x = 6.0 - 12.82 < 0$ in global coordinates).
2. In `app/src/main/res/drawable/bike_radar.xml` ($96 \times 96$ viewport), the three emission arcs were constructed with vertical chords symmetric around horizontal ($y=31.0$). Consequently, as radius scaled from $8.5 \to 14.5 \to 20.5$, the lower endpoints plunged down to $y=44.0$ and $y=49.5$, slicing directly into the top of the rear wheel ($y_{\text{top}} = 39.0$).

This defect violates the canonical `bt_bike_*` family convention (`bt_bike_pwr.xml`, `bt_bike_spd.xml`, `bt_bike_cad.xml`), degrades visual fidelity in sensor pairing lists, and violates Rule 37 geometric standards.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Root Cause 1 (`bt_bike_radar.xml`)**: In `ATT-6699`, the author combined two contradictory design patterns: (a) the canonical `bt_bike_*` pattern with an unmodified centered cyclist silhouette (`translateX="18.18"`) and a dedicated top-left mini-icon (`mini_icon_radar`), and (b) an ad-hoc saddle emission arc overlay from `bike_radar.xml`. Because the cyclist group is translated by $18.18$ units and saddle arcs sweep with large radii ($r=13, 22, 31$), the upper arcs collide with the mini-icon at $x \in [12.8, 43.6]$ and clip off-screen, while lower arcs collide with the rear wheel.
* **Root Cause 2 (`bike_radar.xml`)**: In `bike_radar.xml`, the radar emission arcs (`M 30.5,23.5 A 8.5,8.5 ... 30.5,38.5`, `M 26.5,18.0 A 14.5,14.5 ... 26.5,44.0`, `M 22.5,12.5 A 20.5,20.5 ... 22.5,49.5`) were designed with vertical chords centered at $y=31.0$. The rear wheel is centered at $(24.5, 51.5)$ with outer radius $12.5$ ($y \in [39.0, 64.0]$). Arc 2 ($y_{\text{end}}=44.0$) penetrates the wheel by $4.9$ units ($6.3$ with stroke), and Arc 3 ($y_{\text{end}}=49.5$) penetrates by $10.4$ units ($11.8$ with stroke).
* **Independent Peer Review & Verification**:
  * Inspected `bt_bike_pwr.xml`, `bt_bike_spd.xml`, and `bt_bike_cad.xml`. All 3 canonical family members feature clean, unadorned cyclist silhouettes inside `<group android:name="cyclist" android:translateX="18.18">` (18 paths total) with zero overlays on the cyclist and a single top-left mini-icon.
  * Verified mathematical clearances: In `bt_bike_radar.xml`, removing the 3 saddle arcs completely eliminates all collisions, leaving a clean $\ge 7.7$ px margin between `mini_icon_radar` and the cyclist silhouette, and zero viewport clipping.
  * In `bike_radar.xml`, shifting origin to $(36.0, 26.0)$ and adjusting angular span ($+50^\circ$ up, $-25^\circ$ down) with radii $[8.0, 14.0, 20.0]$ establishes $\ge 4.98$ px physical clearance to the rear wheel, $\ge 8.21$ px clearance to the cyclist saddle/back, and $\ge 9.3$ px margin to the card border.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Remove colliding saddle emission arcs from `app/src/main/res/drawable/bt_bike_radar.xml`, aligning with canonical `bt_bike_pwr.xml` / `bt_bike_spd.xml` / `bt_bike_cad.xml` (pure cyclist silhouette + top-left `mini_icon_radar`).
  * Re-parameterize radar emission arcs in `app/src/main/res/drawable/bike_radar.xml` so arcs maintain clean visual separation ($\ge 3.0$ px clearance) from the rear wheel, cyclist back, and card border.
  * Add automated geometric bounding-box non-overlap tests in `VectorAssetResolutionContractTest.kt`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modifications to radar protocol decoders, BLE GATT queues, ANT+ channels, or radar telemetry repositories.
  * No modification to bottom text labels (`RDR`).
  * No modification to non-radar drawables (`bt_bike_pwr`, `bike_spd`, etc.).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Amends `REQ-UI-514` (*Dedicated ANT+ and Bluetooth LE Vector Sensor Badges for DeviceType.BIKE_RADAR*, Sprint 2026-46.3, `ATT-6699`).
* **Historical Origin & Commit Trace**: `ATT-6699` introduced `bike_radar.xml` and `bt_bike_radar.xml`.
* **Root Reason for Existing Formulation**: `REQ-UI-514.2` originally specified both top-left mini-icon and seatpost emission arcs. In practice, having both on a $48\text{dp}$ badge created severe visual noise and geometric overlaps.
* **Preservation of Core Invariants**: The official Bluetooth badge color token (`#0A3D91`), ANT+ badge color token (`#000000`), bold uppercase `RDR` with `evenOdd`, Rule 37 geometric constraints ($\le 45$ `L` commands, analytical curves), and `UIHelper`/`DeviceData` mappings remain 100% intact.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**: `REQ-UI-514` (amended to mandate zero geometric collision and canonical cyclist family alignment).
* **Affected Architecture Elements**: `app/src/main/res/drawable/bt_bike_radar.xml`, `app/src/main/res/drawable/bike_radar.xml`.
* **Verification Impact & Test Surface**: `VectorAssetResolutionContractTest.kt` extended with geometric non-overlap and clearance contract assertions.
* **Risk Assessment & Invariants**: Risk is **LOW**. Changes are restricted to vector path data and unit tests with zero runtime code impact.

---

## 6. Architectural Strategy & High-Level Solution

1. **`bt_bike_radar.xml`**:
   - Delete the 3 saddle arc paths (`M 22.0,34.0 ...`, `M 14.0,26.0 ...`, `M 6.0,18.0 ...`) from the `cyclist` group.
   - Retain the 18 standard cyclist paths, the top-left `mini_icon_radar` group, and the bottom `RDR` text path.
2. **`bike_radar.xml`**:
   - Reconstruct the 3 emission arcs with concentric center at $(36.0, 26.0)$, radii $[8.0, 14.0, 20.0]$, spanning from $+50^\circ$ up (angle $230^\circ$) to $-25^\circ$ down (angle $155^\circ$):
     - Arc 1: `M 30.9,19.9 A 8.0,8.0 0 0,0 28.7,29.4`
     - Arc 2: `M 27.0,15.3 A 14.0,14.0 0 0,0 23.3,31.9`
     - Arc 3: `M 23.1,10.7 A 20.0,20.0 0 0,0 17.9,34.5`
   - Guarantees $\ge 4.9$ units clearance to rear wheel, $\ge 8.2$ units to cyclist, and $\ge 9.3$ units to card border.
3. **`VectorAssetResolutionContractTest.kt`**:
   - Add `testRadarVectorAssetsGeometricBoundingBoxAndCollisionFreeContract()` validating zero collisions, bounding box separation, and clearance invariants.

---

## 7. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Strict adherence to Rule 37 ($\le 45$ `L` commands, analytical curves `A`).
  2. Protocol color tokens (`#000000` ANT+, `#0A3D91` BLE) and full-bleed bounds preserved.
  3. All existing unit tests pass without regression.
* **Risk Rating**: **LOW** (UI asset geometry and test contract update; zero impact on business logic or hardware communication).
