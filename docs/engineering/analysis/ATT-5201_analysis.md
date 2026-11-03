# Stage 1 Analysis: ATT-5201 - BTLE sensor icons (bt_*.xml): restore official Bluetooth color (#0A3D91), center cyclist, and move canonical mini-icons to top-left

**Ticket**: [ATT-5201](https://rainerblind.atlassian.net/browse/ATT-5201)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Branch**: `feature/ATT-5201`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03

---

## 1. Problem Statement & Motivation

During Sprint 2026-45.1 review of ATT-4956, three visual and architectural deficiencies were identified across the 6 Bluetooth LE sensor vector drawables (`bt_bike_pwr.xml`, `bt_bike_spd.xml`, `bt_bike_cad.xml`, `bt_bike_speed_and_cadence.xml`, `bt_hr.xml`, `bt_run.xml`):
1. **Off-Brand Background Blue**: The badge background currently uses `#07377F` (sampled from ICC color profile distorted legacy PNGs) rather than the official Bluetooth brand color `#0A3D91` (`rgb(10, 61, 145)`) specified in master `Bluetooth.svg` and `logo_protocol_bluetooth.png`.
2. **Ad-Hoc Mini-Icons**: `bt_bike_cad.xml` and `bt_bike_speed_and_cadence.xml` use hand-drawn mini-icons in the top-right that look distorted (crank line cuts across rotation arc; disconnected half-arcs in combo) instead of reusing canonical domain icons `ic_power.xml`, `ic_speed.xml`, and `ic_cadence.xml` (violating Rule 36: Reuse Before Create).
3. **Cramped Top-Right & Asymmetric Cyclist**: The cyclist motif was shifted down-left, leaving the cyclist off-center and crowding the top-right (where the forward-leaning rider's head and hands sit), while leaving unused open negative space in the top-left (above the rear wheel and behind the rider's back).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification**:
  * **Master Asset Color Verification**: Confirmed master `/home/rainer/Dropbox/workspace/TrainingTracker_with_BT_and_MAP3/Bilder/Bluetooth/Bluetooth.svg` defines `fill="#0a3d91"`. Pixel analysis of `app/src/main/res/drawable-xxxhdpi/logo_protocol_bluetooth.png` confirms the primary background color is `#0A3D91` (9,443 pixels), whereas `#07377F` was an artifact of ICC profile conversion during historical rasterization.
  * **Canonical Icon Path Geometry**: `ic_power.xml`, `ic_speed.xml`, and `ic_cadence.xml` contain clean vector paths ($\le 19$ `L` commands, compliant with Rule 37). Scaling and translating their path data into the top-left of the $144 \times 144$ canvas provides visual consistency across the app.
  * **Horizontal Centering & Spatial Ergonomics**: The cyclist motif spans $\approx 87$ units in width. Centering it horizontally across the 144-wide badge ($x \in [28.5, 115.5]$) provides balanced margins ($28.5$ units left and right) and frees a generous $40 \times 40$ quadrant in the top-left ($x \in [12, 52], y \in [10, 50]$) above the rear wheel and behind the rider's sloping back.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Update badge background color from `#07377F` to official `#0A3D91` in all 6 `bt_*.xml` drawables and `VectorAssetResolutionContractTest.kt`.
  * Center the cyclist horizontally across the $144 \times 144$ viewport in `bt_bike_pwr.xml`, `bt_bike_spd.xml`, `bt_bike_cad.xml`, and `bt_bike_speed_and_cadence.xml`.
  * Position canonical mini-icons (`ic_power`, `ic_speed`, `ic_cadence`, and combo `ic_speed` + `ic_cadence`) in the top-left quadrant of the 4 bike drawables.
  * Update contract tests and living documentation (`REQ-UI-442` in `docs/requirements/req_ui_design_system.md`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not alter ANT+ drawables (`bike_*.xml`, `hr.xml`, `run_spd.xml`) or their `#000000` badge color.
  * Do not alter bottom uppercase typography (`PWR`, `SPD`, `CAD`, `S&C`, `HR`, `SPD`) or their $y \ge 113.25$ baseline.
  * Do not modify `bt_hr.xml` or `bt_run.xml` motif centering (heart and runner are already horizontally centered).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-442` (`REQ-UI-442.1`, `REQ-UI-442.3`). Targets `app/src/main/res/drawable/bt_*.xml`, `VectorAssetResolutionContractTest.kt`, and `docs/requirements/req_ui_design_system.md`.
* **Historical Origin & Commit Trace**: `ATT-4956` (commit `be03a5bc14`, Sprint 2026-45.1).
* **Root Reason for Existing Formulation**:
  * `#07377F` was sampled from ICC-distorted legacy PNGs instead of the SVG master.
  * Top-right placement mirrored legacy ANT+ layout without accounting for cyclist forward lean and bold uppercase label height.
  * Hand-drawn mini-icons were drawn quickly instead of extracting canonical paths from `ic_*.xml`.
* **Preservation of Core Invariants**:
  * Full-bleed badge geometry `0..144`, $r=22.0$ (`REQ-UI-442.1`) is 100% preserved.
  * Bold uppercase typography (`PWR`, `SPD`, `CAD`, `S&C`, `HR`, `SPD`) with `evenOdd` (`REQ-UI-442.2`) is 100% preserved.
  * Rule 37 geometric primitive fidelity ($\le 45$ `L` commands, analytical curves) is 100% preserved.
  * Zero breaking changes to `R.drawable.bt_*` resource IDs and call sites (`UIHelper.java`, `DeviceData.kt`).

---

## 5. Architectural Strategy & High-Level Solution

1. **Color Token Standardization**:
   * Replace `#07377F` with `#0A3D91` in `bt_bike_pwr.xml`, `bt_bike_spd.xml`, `bt_bike_cad.xml`, `bt_bike_speed_and_cadence.xml`, `bt_hr.xml`, and `bt_run.xml`.
2. **Cyclist Motif Horizontal Centering**:
   * Apply an exact $+18.18$ horizontal translation ($\Delta x \approx +18.2$) to all cyclist paths (rear wheel, front wheel, frame tubes, rider body, head, helmet) in the 4 bike drawables, placing bounds symmetrically at $x \in [28.5, 115.5]$.
3. **Canonical Mini-Icon Embedding (Rule 36)**:
   * Scale canonical path data from `ic_power.xml`, `ic_speed.xml`, and `ic_cadence.xml` ($960 \times 960$) into top-left quadrant bounds ($x \approx 12..50, y \approx 10..48$ in $144 \times 144$) via scaled `<path>` data or `<group>` transform.
   * `bt_bike_pwr`: Canonical `ic_power` bolt in top-left.
   * `bt_bike_spd`: Canonical `ic_speed` gauge arc in top-left.
   * `bt_bike_cad`: Canonical `ic_cadence` rotation loop in top-left.
   * `bt_bike_speed_and_cadence`: Dual canonical gauge arc + rotation loop in top-left.
4. **Contract Test Alignment**:
   * Update `VectorAssetResolutionContractTest.kt` to assert `#0A3D91` across all 6 BLE badges, verify top-left mini-icon bounds, and validate Rule 37 invariants.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Full-bleed badge bounds `0..144` with corner radius $r=22.0$ on all 6 drawables.
  2. Bold uppercase bottom labels at $y \ge 113.25$ with `fillType="evenOdd"`.
  3. Strict adherence to Rule 37 ($\le 45$ `L` commands per path, analytical curves).
  4. 100% backward-compatible resource resolution (`UIHelper.java`, `DeviceData.kt`).
* **Side-Effect & Cross-Caller Impact Audit (Rule 68)**:
  * Call sites (`UIHelper.java:54-60`, `DeviceData.kt:259-262`, `ControlTrackingScreen.kt:725-754`, `RemoteDevices.kt:143-144`) resolve drawables by ID without dimension or color assumptions.
  * Only `VectorAssetResolutionContractTest.kt` inspects raw XML tokens.
* **Risk Rating**: **LOW** — Pure XML vector asset and contract test refinement; zero business logic, database, or threading changes.
