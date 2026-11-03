# Stage 2: Requirement & Test Specification - ATT-4956: Full-Bleed Badge, Large Uppercase Labels & Top-Right Mini-Icons for BTLE Sensor Icons

**Ticket**: [[ATT-4956]](https://rainerblind.atlassian.net/browse/ATT-4956)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-UI-442` (`REQ-UI-442.1`–`REQ-UI-442.4`)  
**Test Spec ID**: `TST-UI-442` (`TST-UI-442.1`–`TST-UI-442.4`)  
**Branch**: `feature/ATT-4956`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-UI-442)

### 1.1 Problem Statement & Rationale
When Bluetooth LE (`bt_*.xml`) sensor icons are rendered at compact UI dimensions (20dp–24dp, e.g. status bar, device pairing, sensor matrix rows), three visual deficiencies appear compared to ANT+ icons: (1) bottom labels are micro lowercase (`power`, `speed`, `cadence`, `s & c`) or completely missing (`bt_hr`, `bt_run`); (2) all 4 bike icons share an identical cyclist silhouette without upper motif differentiation; (3) all 6 BTLE drawables use a 10-unit inset background (`10..134` in `144x144`), making them appear ~14% smaller than ANT+ icons (`0..96` full-bleed). Standardizing BTLE drawables to full-bleed badges, bold uppercase labels, and top-right sensor mini-icons establishes visual parity and instant recognition across both protocols.

### 1.2 Functional & Architectural Requirements
1. **Full-Bleed Rounded Badge Geometry (`REQ-UI-442.1`)**: In all 6 BTLE vector drawables (`bt_bike_pwr.xml`, `bt_bike_spd.xml`, `bt_bike_cad.xml`, `bt_bike_speed_and_cadence.xml`, `bt_hr.xml`, `bt_run.xml`), the background badge path SHALL be full-bleed spanning `0..144` with corner radius $r = 22.0$ (`M 22,0 H 122 A 22,22 0 0 1 144,22 V 122 A 22,22 0 0 1 122,144 H 22 A 22,22 0 0 1 0,122 V 22 A 22,22 0 0 1 22,0 Z`), replacing the historical 10-unit inset margin (`10..134`). The badge fill color SHALL strictly maintain the `#07377F` protocol badge color token.
2. **Large Bold Uppercase Bottom Typography (`REQ-UI-442.2`)**: All 6 BTLE drawables SHALL render bold uppercase labels centered at bottom ($y \approx 113..136$ in `144x144`): `PWR`, `SPD`, `CAD`, `S&C`, `HR`, and `SPD`. Glyphs with internal counters SHALL use `android:fillType="evenOdd"`.
3. **Top-Right Sensor Mini-Icons & Repositioned Motifs (`REQ-UI-442.3`)**: Bike icons SHALL shift the cyclist motif down-left ($x \approx 8..108, y \approx 22..106$) to accommodate distinct sensor mini-icons in the top-right quadrant ($x \approx 104..136, y \approx 8..38$): lightning bolt (power), speedometer arc (speed), crank with rotation arrow (cadence), and combined gauge/arrow (speed & cadence). `bt_hr` and `bt_run` motifs SHALL be scaled cleanly above bottom labels.
4. **Rule 37 Vector Geometry & Fidelity Governance (`REQ-UI-442.4`)**: All modified vector paths MUST adhere to Rule 37 constraints: pure analytical geometry (`M`, `L`, `H`, `V`, `C`, `A`, `Z`), $\le 45$ `L` commands per `<path>`, zero polygon auto-tracing approximations, and 100% clean parsing. Resource resolution in `UIHelper.java` SHALL remain 100% backward compatible.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-375` (*Mathematical VectorDrawable Migration from Xfig/SVG/XCF Masters & Legacy Raster Purge*, `ATT-2859`). Targets `app/src/main/res/drawable/bt_*.xml` and `VectorAssetResolutionContractTest.kt`.
* **Historical Origin & Commit Trace**: `ATT-2859` introduced mathematical vectorization. Inset margins (`10..134`) and lowercase labels were carried over from initial XCF/FIG bitmap sketches. ANT+ badges were updated to full-bleed (`0..96`) and uppercase typography in `ATT-2859`, while BTLE badges remained inset with micro-text.
* **Root Reason for Existing Formulation**: Early vectorization mirrored legacy bitmap assets without standardizing BTLE badges to the modern full-bleed and uppercase typography convention of the design system.
* **Preservation of Core Invariants**: Rule 37 geometric constraints ($\le 45$ `L` commands, analytical arcs), `#07377F` protocol badge color token, and `R.drawable.bt_*` resource IDs are 100% preserved.

### 1.4 Acceptance Criteria (Given-When-Then) & System Invariants
* **Criterion 1 (Full-Bleed Badge)**: *Given* any of the 6 BTLE drawables, *When* inspected or rendered at 20dp–24dp, *Then* the background badge spans full viewport bounds `0..144` with corner radius $r=22.0$.
* **Criterion 2 (Uppercase Bottom Labels)**: *Given* an athlete viewing BTLE icons in device pairing or status bar, *When* reading the icon, *Then* the uppercase label (`PWR`, `SPD`, `CAD`, `S&C`, `HR`, `SPD`) is clearly legible at compact dimensions.
* **Criterion 3 (Top-Right Sensor Mini-Icons)**: *Given* the 4 BTLE bike icons, *When* comparing icons, *Then* each displays its unique top-right mini-icon (power bolt, speedometer arc, cadence crank, combined dual sensor).
* **Criterion 4 (Contract Test Compliance)**: *Given* `VectorAssetResolutionContractTest`, *When* executed, *Then* all drawables pass Rule 37 checks, full-bleed assertions, and uppercase label validation.
* **Invariants**: Rule 37 geometric primitives, `#07377F` protocol badge token, `R.drawable.bt_*` resource ID mapping, and 100% clean-room test pass rate.

---

## 2. Test Specification (TST-UI-442)

### Test Case 1: `testBtleSensorBadgesAreFullBleedAndRetainNavyColor` (`[TST-UI-442.1]`)
* **Scope**: Contract Test (`VectorAssetResolutionContractTest.kt`)
* **Action**: Parse all 6 BTLE XML files; assert background path starts at `M 22` / spans `0..144` with $r=22.0$, fill color `#07377F`, and zero 10-unit inset paths.

### Test Case 2: `testBtleSensorBadgesContainBoldUppercaseLabelsWithEvenOdd` (`[TST-UI-442.2]`)
* **Scope**: Contract Test (`VectorAssetResolutionContractTest.kt`)
* **Action**: Verify path data contains uppercase glyph paths for `PWR`, `SPD`, `CAD`, `S&C`, `HR`, and `SPD`; assert `android:fillType="evenOdd"` on label paths and absence of lowercase text words.

### Test Case 3: `testBtleSensorBadgesContainTopRightMiniIconsAndRule37Prims` (`[TST-UI-442.3]`)
* **Scope**: Contract Test (`VectorAssetResolutionContractTest.kt`)
* **Action**: Verify bike icons contain top-right mini-icon geometry; verify all 6 drawables satisfy Rule 37 ($\le 45$ `L` commands per path, analytical curves `A`/`C`/`Q`).

### Test Case 4: `testBtleSensorResourceResolutionParity` (`[TST-UI-442.4]`)
* **Scope**: Unit & Contract Test (`VectorAssetResolutionContractTest.kt` / `UIHelper`)
* **Action**: Verify `UIHelper.getIconId(DeviceType, Protocol.BLUETOOTH_LE)` resolves valid non-zero resource IDs with zero regression (`REQ-PRO-001`).

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-442.1]` | Contract | `app/src/main/res/drawable/bt_*.xml` | `REQ-UI-442.1` | Specified |
| `[TST-UI-442.2]` | Contract | `app/src/main/res/drawable/bt_*.xml` | `REQ-UI-442.2` | Specified |
| `[TST-UI-442.3]` | Contract | `app/src/main/res/drawable/bt_*.xml` | `REQ-UI-442.3`, `REQ-UI-442.4` | Specified |
| `[TST-UI-442.4]` | Unit | `UIHelper.java` / `VectorAssetResolutionContractTest.kt` | `REQ-PRO-001` | Specified |
