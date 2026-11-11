# Stage 3: Implementation Plan - ATT-6699: Design and wire dedicated ANT+ (bike_radar) and BLE (bt_bike_radar) device vector logos for DeviceType.BIKE_RADAR

**Ticket**: [ATT-6699](https://rainerblind.atlassian.net/browse/ATT-6699)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-UI-514` (`REQ-UI-514.1`, `REQ-UI-514.2`, `REQ-UI-514.3`, `REQ-UI-514.4`)  
**Test Mapping**: `TST-UI-514` (`TST-UI-514.1`, `TST-UI-514.2`, `TST-UI-514.3`, `TST-UI-514.4`)  
**Branch**: `feature/ATT-6699`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

In Sprint 2026-46.1 review of `ATT-5083`, it was identified that `DeviceType.BIKE_RADAR` lacks dedicated protocol vector badges in `app/src/main/res/drawable/`. Currently, paired or discovered radar devices fall back to generic protocol logos (`ant_logo` / `logo_protocol_bluetooth`), breaking visual symmetry with the sensor badge family (`bike_pwr`/`bt_bike_pwr`, `bike_spd`/`bt_bike_spd`, `bike_cad`/`bt_bike_cad`, `hr`/`bt_hr`, `run_spd`/`bt_run`).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-514` (*Dedicated ANT+ and Bluetooth LE Vector Sensor Badges for DeviceType.BIKE_RADAR*)
  * `REQ-UI-514.1`: ANT+ `bike_radar.xml` Vector Asset ($96 \times 96$, `#000000`, centered cyclist, rear radar arcs, uppercase `RDR` with `evenOdd`).
  * `REQ-UI-514.2`: BLE `bt_bike_radar.xml` Vector Asset ($144 \times 144$, `#0A3D91`, centered cyclist, top-left `mini_icon_radar`, rear radar arcs, uppercase `RDR` with `evenOdd`).
  * `REQ-UI-514.3`: Protocol Icon Resolution in `UIHelper.java` and `DeviceData.kt`.
  * `REQ-UI-514.4`: Rule 37 Quality & Contract Test Verification.
* **Test Mapping**: `TST-UI-514`
  * `TST-UI-514.1`: Vector DOM well-formedness and Rule 37 complexity ($\le 45$ `L` commands, analytical curves).
  * `TST-UI-514.2`: Visual fidelity, color tokens (`#000000`, `#0A3D91`), and `RDR` typography.
  * `TST-UI-514.3`: `UIHelper` & `DeviceData` resource resolution for `DeviceType.BIKE_RADAR`.
  * `TST-UI-514.4`: Targeted unit test regression suite.

---

## 3. System Invariants & Preserved Behavior

1. **Rule 37 Vector Constraints**: Pure analytical geometry, $\le 45$ `L` commands per `<path>`, zero polygon auto-tracing approximations, well-formed `<vector>` DOM trees.
2. **Color Token Fidelity**: ANT+ badges use black `#000000`; Bluetooth LE badges strictly use official brand token `#0A3D91`.
3. **UIHelper & DeviceData Contract**: All existing 16 sensor drawables retain exact non-zero resource mappings without breaking changes.
4. **Human Decision Gate**: Parent ticket completion remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `app/src/main/res/drawable/bike_radar.xml`
* Define ANT+ vector badge ($48\text{dp} \times 48\text{dp}$, $96 \times 96$ viewport) with `#000000` rounded background ($r=10.0$), centered white silhouette cyclist, rear radar emission arcs, and bold uppercase `RDR` typography at $y \in [75.5, 90.5]$ using `evenOdd` fillType.

### Component 2: `app/src/main/res/drawable/bt_bike_radar.xml`
* Define Bluetooth LE vector badge ($48\text{dp} \times 48\text{dp}$, $144 \times 144$ viewport) with full-bleed `#0A3D91` rounded background ($r=22.0$), centered cyclist (`translateX="18.18"`), canonical top-left radar wave mini-icon (`mini_icon_radar`), rear radar emission arcs, and bold uppercase `RDR` typography at $y \in [113.25, 135.75]$ using `evenOdd` fillType.

### Component 3: `DeviceData.kt` & `UIHelper.java` Wiring
* Map `DeviceType.BIKE_RADAR` + `Protocol.ANT_PLUS` to `R.drawable.bike_radar`.
* Map `DeviceType.BIKE_RADAR` + `Protocol.BLUETOOTH_LE` to `R.drawable.bt_bike_radar`.

### Component 4: `VectorAssetResolutionContractTest.kt`
* Add `bike_radar` and `bt_bike_radar` to contract verification suites.

### UI Consistency (Rule 23)
* **Reference Component**: Existing sensor badge family (`bike_pwr.xml` / `bt_bike_pwr.xml`, `bike_spd.xml` / `bt_bike_spd.xml`).
* **Reused Elements**: Exact cyclist silhouette vector coordinates, badge dimensions, and typographic geometry.
* **Theme Tokens**: `#000000` (ANT+ badge), `#0A3D91` (BLE badge), `#FFFFFF` (foreground glyphs).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Author `app/src/main/res/drawable/bike_radar.xml`
* File: `app/src/main/res/drawable/bike_radar.xml`
* Changes: Pure analytical XML vector drawable ($96 \times 96$, `#000000` badge, centered cyclist, rear radar wave arcs, `RDR` label).

### Step 2: Author `app/src/main/res/drawable/bt_bike_radar.xml`
* File: `app/src/main/res/drawable/bt_bike_radar.xml`
* Changes: Full-bleed XML vector drawable ($144 \times 144$, `#0A3D91` badge, centered cyclist, top-left `mini_icon_radar`, rear radar wave arcs, `RDR` label).

### Step 3: Wire `DeviceType.BIKE_RADAR` in `DeviceData.kt` and `UIHelper.java`
* Files:
  - `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicedata/DeviceData.kt`
  - `app/src/main/java/com/atrainingtracker/banalservice/helpers/UIHelper.java`
* Changes: Add `DeviceType.BIKE_RADAR` branches resolving to `R.drawable.bike_radar` (ANT+) and `R.drawable.bt_bike_radar` (BLE).

### Step 4: Extend `VectorAssetResolutionContractTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/VectorAssetResolutionContractTest.kt`
* Changes: Add `bike_radar` and `bt_bike_radar` to validation matrices and assert resolution mappings.

---

## 6. Verification & Rollback Plan

* **Verification**: Run targeted unit and contract tests:
  ```bash
  python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.resources.VectorAssetResolutionContractTest"
  ```
* **Rollback**: Clean revert via `git reset --hard origin/sprint/2026-46.2`.
