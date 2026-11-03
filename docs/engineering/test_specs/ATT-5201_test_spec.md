# Stage 2: Requirement & Test Specification - ATT-5201: BTLE sensor icons (bt_*.xml): restore official Bluetooth color (#0A3D91), center cyclist, and move canonical mini-icons to top-left

**Ticket**: [ATT-5201](https://rainerblind.atlassian.net/browse/ATT-5201)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-UI-442` (*Full-Bleed Badge Geometry, Large Uppercase Labels, Centered Cyclist & Top-Left Canonical Mini-Icons for Bluetooth LE Sensor Drawables*)  
**Test Spec ID**: `TST-UI-442`  
**Branch**: `feature/ATT-5201`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-UI-442)

### 1.1 Problem Statement & Rationale
Sprint 2026-45.1 review of `ATT-4956` revealed 3 deficiencies in the 6 BTLE sensor drawables:
1. `#07377F` was an ICC-profile distorted color; the official Bluetooth color is `#0A3D91` (`Bluetooth.svg`).
2. Hand-drawn top-right mini-icons look distorted instead of reusing canonical `ic_power.xml`, `ic_speed.xml`, `ic_cadence.xml` (Rule 36).
3. The forward-leaning cyclist creates natural open negative space in the top-left; the cyclist should be horizontally centered and mini-icons placed in the top-left.

### 1.2 Functional & Architectural Requirements
1. **Official Bluetooth Color (`REQ-UI-442.1`)**: All 6 BTLE drawables (`bt_bike_pwr.xml`, `bt_bike_spd.xml`, `bt_bike_cad.xml`, `bt_bike_speed_and_cadence.xml`, `bt_hr.xml`, `bt_run.xml`) SHALL use `#0A3D91` as the full-bleed background badge fill (`0..144`, $r=22.0$).
2. **Typography Preservation (`REQ-UI-442.2`)**: Bold uppercase labels (`PWR`, `SPD`, `CAD`, `S&C`, `HR`, `SPD`) at $y \ge 113.25$ with `fillType="evenOdd"` SHALL remain unchanged.
3. **Centered Cyclist & Top-Left Canonical Mini-Icons (`REQ-UI-442.3`)**:
   - The cyclist silhouette motif on the 4 bike icons SHALL be horizontally centered ($x \in [28.5, 115.5]$).
   - Sensor mini-icons SHALL reside in the top-left quadrant ($x \approx 12..52, y \approx 10..50$) reusing canonical paths from `ic_power.xml`, `ic_speed.xml`, and `ic_cadence.xml`.
4. **Geometric Constraints (`REQ-UI-442.4`)**: All paths MUST adhere to Rule 37 ($\le 45$ `L` commands, analytical curves). Non-zero resource resolution in `UIHelper.java` SHALL be preserved.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-442` (`REQ-UI-442.1`, `REQ-UI-442.3`). Targets `app/src/main/res/drawable/bt_*.xml`, `VectorAssetResolutionContractTest.kt`, and `docs/requirements/req_ui_design_system.md`.
* **Historical Origin & Commit Trace**: `ATT-4956` (commit `be03a5bc14`, Sprint 2026-45.1).
* **Root Reason for Existing Formulation**: `#07377F` was sampled from ICC-distorted legacy PNGs; top-right mini-icons were hand-drawn without evaluating top-left negative space or reusing existing canonical vector drawables.
* **Preservation of Core Invariants**: Full-bleed `0..144` ($r=22.0$) geometry, uppercase labels with `evenOdd`, Rule 37 geometric primitive fidelity, and non-zero resource IDs are 100% preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Official Color)**: *Given* any of the 6 BTLE vector drawables, *When* inspected, *Then* badge fill color is `#0A3D91`.
* **Criterion 2 (Centered Cyclist & Mini-Icons)**: *Given* the 4 BTLE bike drawables, *When* measured, *Then* cyclist is horizontally centered ($x \in [28.5, 115.5]$) and canonical mini-icons are positioned in top-left ($x \le 52, y \le 50$).
* **Criterion 3 (Contract & Resolution)**: *Given* `VectorAssetResolutionContractTest`, *When* executed, *Then* all assertions pass with 100% success.

---

## 2. Test Specification (TST-UI-442)

* **TST-UI-442.1 (Color & Badge Geometry)**: Contract test asserting `#0A3D91` background fill and full-bleed bounds `0..144` ($r=22.0$) across all 6 BTLE drawables.
* **TST-UI-442.2 (Typography)**: Contract test asserting uppercase labels (`PWR`, `SPD`, `CAD`, `S&C`, `HR`, `SPD`) with `evenOdd` at $y \ge 113.25$.
* **TST-UI-442.3 (Centering, Mini-Icons & Rule 37)**: Contract test verifying centered cyclist coordinates ($x \in [28.5, 115.5]$), top-left mini-icon bounds ($x \le 52, y \le 50$), and Rule 37 limits ($\le 45$ `L` commands, analytical curves).
* **TST-UI-442.4 (Resource Resolution & Regression)**: Verify non-zero resource IDs via `UIHelper.getIconId()` and clean `./gradlew testDebugUnitTest --tests "...VectorAssetResolutionContractTest"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-442.1]` | Contract | `VectorAssetResolutionContractTest.testBtleSensorBadgesAreFullBleedAndRetainNavyColor` | `REQ-UI-442.1` | Specified |
| `[TST-UI-442.2]` | Contract | `VectorAssetResolutionContractTest.testBtleSensorBadgesContainBoldUppercaseLabelsWithEvenOdd` | `REQ-UI-442.2` | Specified |
| `[TST-UI-442.3]` | Contract | `VectorAssetResolutionContractTest.testBtleSensorBadgesContainTopRightMiniIconsAndRule37Prims` | `REQ-UI-442.3`, `REQ-UI-442.4` | Specified |
| `[TST-UI-442.4]` | Contract | `VectorAssetResolutionContractTest.testSportAndSensorDrawableMappingsResolveValidResourceIds` | `REQ-PRO-001`, `REQ-UI-442.4` | Specified |
