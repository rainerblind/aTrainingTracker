# Stage 1 Analysis: ATT-4956 - Update BTLE sensor icons: full-bleed badge, large uppercase labels (PWR/SPD/CAD/S&C/HR), and top-right sensor mini-icons

**Ticket**: [ATT-4956](https://rainerblind.atlassian.net/browse/ATT-4956)  
**Sub-task**: [ATT-5184](https://rainerblind.atlassian.net/browse/ATT-5184) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Branch**: `feature/ATT-4956`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Statement & Motivation
* When Bluetooth LE (`bt_*.xml`) sensor icons are rendered at compact UI dimensions (`20dp`–`24dp`, e.g., status bar, device pairing/matrix rows), three visual deficiencies appear compared to ANT+ icons:
  1. *Tiny / Missing Bottom Labels*: Current `bt_bike_*` drawables spell out full lowercase words (`power`, `speed`, `cadence`, `s & c`) with glyph heights of only ~`12`–`16` units inside `144x144` (~`9%` viewport height), making them illegible at small sizes. `bt_hr.xml` and `bt_run.xml` omit bottom labels entirely. In contrast, ANT+ icons use bold uppercase abbreviations (`PWR`, `SPD`, `CAD`, `S&C`, `HR`, `SPD`) occupying ~`16.7%` viewport height (`y = 75.5..90.5` in `96x96`).
  2. *Un-differentiated Upper Graphic*: All 4 `bt_bike_*` drawables share an identical cyclist silhouette, lacking upper motif distinction. Adding sensor-type mini-icons in the top-right corner ensures instant recognition.
  3. *Inset Margin*: All 6 `bt_*.xml` icons use a `#07377F` background inset by 10 units (`10..134` in `144x144`), making them appear ~14% smaller than ANT+ icons (`0..96` full-bleed).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**:
  * Legacy vector conversion retained historical 10-unit inset bounds (`10.0..134.0`, corner radius `r = 13.5`) and micro lowercase lettering from early bitmap sketches.
  * ANT+ vector drawables were previously upgraded to full-bleed (`0..96`, corner `r = 10.0`) and uppercase labels, leaving BTLE assets visually mismatched.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Inspected all 6 BTLE XML files: confirmed 10-unit inset paths and lowercase or missing bottom typography.
  * Audited UI call sites (`EquipmentSensorMatrixScreen.kt`, `EditDeviceDialog.kt`, `PairDevicesScreen.kt`, `UIHelper.java`): all access drawables through standard Android resource IDs (`R.drawable.bt_*`). Pure vector drawable updates will seamlessly reflect without UI code changes.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Update 6 BTLE VectorDrawables (`bt_bike_pwr.xml`, `bt_bike_spd.xml`, `bt_bike_cad.xml`, `bt_bike_speed_and_cadence.xml`, `bt_hr.xml`, `bt_run.xml`) with full-bleed `#07377F` rounded badges (`0..144`, `r = 22.0`).
  2. Implement bold uppercase bottom labels (`PWR`, `SPD`, `CAD`, `S&C`, `HR`, `SPD`) at `y ≈ 112..136` (~`16.7%` viewport height) with `evenOdd` fillType for counter holes.
  3. Shift cyclist motif down-left (`x ≈ 8..108, y ≈ 22..106`) on bike icons and add top-right sensor mini-icons (lightning ⚡, speedometer arc, crank ↻, combined gauge+arrow). Scale `bt_hr` and `bt_run` motifs cleanly above bottom text.
  4. Extend `VectorAssetResolutionContractTest.kt` to enforce full-bleed bounds, label presence, and Rule 37 compliance.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Altering ANT+ vector drawables or other sport type icons (`bsport_*.xml`).
  2. Modifying Bluetooth GATT discovery, pairing logic, or sensor telemetry processing.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Amends `REQ-UI-375` (*Vector Asset Resolution and Geometric Fidelity System*, introduced in `ATT-2859`). Targets `app/src/main/res/drawable/bt_*.xml` and `VectorAssetResolutionContractTest.kt`.
* **Historical Origin & Commit Trace**: `ATT-2859` introduced mathematical vector drawables and contract tests. Inset margins and lowercase labels were carried over from legacy assets.
* **Preservation of Core Invariants**: Retains Rule 37 constraints: pure analytical vector paths (`M`, `L`, `H`, `V`, `C`, `A`, `Z`), $\le 45$ `L` commands per path, and `#07377F` protocol badge color token.

---

## 5. Architectural Strategy & High-Level Solution
* **Vector Geometry Redesign**:
  * Badge: Full-bleed `0..144` path with `r = 22.0` analytical arcs: `M 22,0 H 122 A 22,22 0 0 1 144,22 V 122 A 22,22 0 0 1 122,144 H 22 A 22,22 0 0 1 0,122 V 22 A 22,22 0 0 1 22,0 Z`.
  * Bike motifs: Reposition cyclist silhouette to clear the top-right quadrant (`x ≈ 104..136, y ≈ 8..38`) for distinct sensor mini-icons.
  * Typography: Large bold uppercase glyphs (`PWR`, `SPD`, `CAD`, `S&C`, `HR`) matching ANT+ height proportions with `evenOdd` fill for internal holes.
* **Automated Verification**:
  * Update `VectorAssetResolutionContractTest.kt` with tests for full-bleed dimensions and presence of uppercase labels and mini-icons.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Rule 37 vector geometry contract compliance ($\le 45$ `L` commands per path, analytical arcs).
  2. 100% test pass rate on `VectorAssetResolutionContractTest` and unit test suite.
  3. Parent ticket Human Decision Gate strictly enforced.
* **Risk Rating**: **LOW** — Vector asset visual refinements and unit test assertions only; zero risk to application business logic or persistence.
