# Stage 3: Implementation Plan - ATT-4956: Full-Bleed Badge, Large Uppercase Labels & Top-Right Mini-Icons for BTLE Sensor Icons

**Ticket**: [ATT-4956](https://rainerblind.atlassian.net/browse/ATT-4956)  
**Sub-task**: [ATT-5186](https://rainerblind.atlassian.net/browse/ATT-5186) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-UI-442` (`REQ-UI-442.1`–`REQ-UI-442.4`)  
**Test Mapping**: `TST-UI-442` (`TST-UI-442.1`–`TST-UI-442.4`)  
**Branch**: `feature/ATT-4956`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background
When Bluetooth LE (`bt_*.xml`) sensor icons are rendered at compact UI dimensions (`20dp`–`24dp`, e.g. status bar, device pairing, sensor matrix rows), three visual deficiencies appear compared to ANT+ icons: (1) bottom labels are micro lowercase (`power`, `speed`, `cadence`, `s & c`) or completely missing (`bt_hr`, `bt_run`); (2) all 4 bike icons share an identical cyclist silhouette without upper motif differentiation; (3) all 6 BTLE drawables use a 10-unit inset background (`10..134` in `144x144`), making them appear ~14% smaller than ANT+ icons (`0..96` full-bleed). Standardizing BTLE drawables to full-bleed badges, bold uppercase labels, shifted cyclist silhouettes, and distinct top-right sensor mini-icons establishes visual parity and instant recognition across protocols.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-442` (*Full-Bleed Badge Geometry, Uppercase Labels & Mini-Icons for BTLE Drawables*)
  * `REQ-UI-442.1`: Full-bleed background badge path (`0..144`, $r=22.0$) with `#07377F` protocol token.
  * `REQ-UI-442.2`: Large bold uppercase bottom labels (`PWR`, `SPD`, `CAD`, `S&C`, `HR`, `SPD`) with `evenOdd` counters.
  * `REQ-UI-442.3`: Shifted cyclist motif down-left and top-right mini-icons (bolt, speedometer arc, crank, combined).
  * `REQ-UI-442.4`: Rule 37 mathematical vector fidelity ($\le 45$ `L` cmds/path, analytical curves `A`/`C`).
* **Test Mapping**: `TST-UI-442` (`TST-UI-442.1`–`TST-UI-442.4`) in `VectorAssetResolutionContractTest.kt`.

---

## 3. System Invariants & Preserved Behavior
1. **Rule 37 Vector Geometry**: Pure analytical primitives (`M`, `L`, `H`, `V`, `C`, `A`, `Z`), max 45 `L` commands per path, zero polygon-tracing rasterization.
2. **Protocol Color Token**: Background fill strictly preserves `#07377F` across all 6 drawables.
3. **Resource Identifier Compatibility**: `R.drawable.bt_*` and `UIHelper.java` resolution remain 100% backward compatible (`REQ-PRO-001`).
4. **ANT+ & Other Assets**: ANT+ drawables (`bike_*.xml`, `hr.xml`, `run_spd.xml`) and `bsport_*.xml` remain untouched.

---

## 4. Proposed Architectural Changes

### Component 1: BTLE Bike Sensor Drawables (`bt_bike_*.xml`)
* `bt_bike_pwr.xml`, `bt_bike_spd.xml`, `bt_bike_cad.xml`, `bt_bike_speed_and_cadence.xml`: Full-bleed `0..144` path ($r=22.0$, `#07377F`), repositioned cyclist ($x \approx 8..108, y \approx 22..106$), distinct top-right mini-icons ($x \approx 104..136, y \approx 8..38$: bolt, speedo arc, crank, combined), and bold uppercase labels (`PWR`, `SPD`, `CAD`, `S&C`) at $y \approx 113..136$ with `evenOdd`.

### Component 2: BTLE HR & Run Sensor Drawables (`bt_hr.xml`, `bt_run.xml`)
* `bt_hr.xml`: Full-bleed `#07377F` badge, scaled heart ($y \approx 12..104$), bold uppercase `HR` label with `evenOdd`.
* `bt_run.xml`: Full-bleed `#07377F` badge, scaled runner ($y \approx 10..104$), bold uppercase `SPD` label with `evenOdd`.

### Component 3: Vector Asset Contract Tests (`VectorAssetResolutionContractTest.kt`)
* Implement `TST-UI-442.1`–`TST-UI-442.4`: assert full-bleed bounds `0..144`, uppercase text labels, presence of top-right mini-icons, and Rule 37 compliance.

### UI Consistency (Rule 23)
* **Master**: ANT+ drawables (`bike_*.xml`, `hr.xml`, `run_spd.xml`); **Tokens**: `#07377F`, `#FFFFFF`, $144\times 144$ viewport.
* **New styles**: None (standard design system alignment).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Power and Speed BTLE Drawables
* Files: `app/src/main/res/drawable/bt_bike_pwr.xml`, `app/src/main/res/drawable/bt_bike_spd.xml`
* Implement full-bleed badge, shifted cyclist, lightning bolt / speedometer mini-icons, and `PWR` / `SPD` labels.

### Step 2: Update Cadence and Speed & Cadence BTLE Drawables
* Files: `app/src/main/res/drawable/bt_bike_cad.xml`, `app/src/main/res/drawable/bt_bike_speed_and_cadence.xml`
* Implement full-bleed badge, shifted cyclist, crank / combined mini-icons, and `CAD` / `S&C` labels.

### Step 3: Update Heart Rate and Running Speed BTLE Drawables
* Files: `app/src/main/res/drawable/bt_hr.xml`, `app/src/main/res/drawable/bt_run.xml`
* Implement full-bleed badge, scaled heart / runner motifs, and centered `HR` / `SPD` uppercase labels.

### Step 4: Update Vector Asset Resolution Contract Tests & Verification
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/VectorAssetResolutionContractTest.kt`
* Implement TST-UI-442 test cases; verify via `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.resources.VectorAssetResolutionContractTest"` and full suite.

---

## 6. Verification & Rollback Plan
* **Verification**: 100% pass rate on `VectorAssetResolutionContractTest` and clean-room test suite.
* **Rollback**: Clean git restore of modified drawables and test files on `feature/ATT-4956`.
