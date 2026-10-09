# Stage 2: Requirement & Test Specification - ATT-2749: Display official untinted ANT+ logo and remove BLE hint from ANT+ status sheet

**Ticket**: [ATT-2749](https://atrainingtracker.atlassian.net/browse/ATT-2749)  
**Sub-task**: [ATT-2838](https://atrainingtracker.atlassian.net/browse/ATT-2838) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-309` (*Official Untinted ANT+ Logo Rendering and Bluetooth LE Advisory Note Elimination in ANT+ System Services Sheet*)  
**Test Spec ID**: `TST-UI-269` (*ANT+ Status Sheet Untinted Branding, BLE Hint Elimination, and Localization Parity Verification*)  
**Branch**: `feature/ATT-2749`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-UI-309)

### 1.1 Problem Statement & Rationale
In Sprint Review 2026-41.3 on a Google Pixel 10 (ATT-2513 verification), two visual issues were identified in the modernized ANT+ status sheet (`AntServicesStatusSheet.kt`):
1. **Solid Blue Square**: In the top-left title bar, a solid blue square was displayed instead of the official ANT+ logo because `AppModalBottomSheet` automatically applies a primary tint to `iconPainter`, turning the raster `ant_logo.png` into a solid blue rectangle. The official multi-color/high-contrast ANT+ logo must be displayed untinted (`Color.Unspecified`).
2. **Extraneous Bluetooth LE Hint**: At the bottom of `AntServicesStatusSheet`, an advisory card suggests: *"Tipp: Bluetooth LE Sensoren funktionieren direkt ohne Zusatzhardware oder Systemdienste."* (`ant_status_ble_alternative_note`). In an ANT+ status diagnostic sheet, this recommendation is distracting and redundant; athletes opening this sheet are specifically checking or troubleshooting ANT+ USB hardware and drivers.

### 1.2 Functional & Architectural Requirements
The system SHALL display the authentic untinted ANT+ logo and eliminate extraneous Bluetooth LE hints in `AntServicesStatusSheet.kt` (ATT-2749):
1. *Untinted Official ANT+ Logo Rendering*:
   - `AntServicesStatusSheet.kt` SHALL pass `iconTint = Color.Unspecified` to `AppModalBottomSheet`.
   - The official ANT+ raster asset `R.drawable.ant_logo` SHALL render in its original black-and-white colors without monochrome color filter tinting.
2. *Elimination of Bluetooth LE Advisory Card*:
   - `AntServicesStatusSheet.kt` SHALL remove the Bluetooth LE alternative `Card` container (lines 165–190) and its references to `R.drawable.logo_protocol_bluetooth` and `R.string.ant_status_ble_alternative_note`.
   - The diagnostic sheet SHALL present solely the hardware prerequisite note, the service status list with install triggers, and the OK dismiss button.
3. *String Resource Hygiene & Parity*:
   - The obsolete string resource `ant_status_ble_alternative_note` SHALL be eliminated across all 9 localized `strings.xml` resource files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
4. *Preservation of System Invariants*:
   - `BANALService` live package detection, USB host checks, and Google Play install intent launches MUST remain 100% operational.
   - Zero regression in clean-room unit test suite (`./gradlew testDebugUnitTest`).

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-309`), complementing and refining `REQ-UI-296` (*Modernized Contextual ANT+ System Service Guidance & Cold-Start Dialog Elimination*).
* **Historical Origin & Commit Trace**: Ticket `ATT-2749`, Sprint `2026-41.4`, Epic `ATT-355` (*Good and consistent UI*). Modernized baseline introduced in ATT-2513 (commit `b1736fc4b5ffa35e84ed820b404b6b1eac5cf83e`).
* **Root Reason for Existing Formulation**: In ATT-2513, `AppModalBottomSheet` was used with default `iconTint = primary`, inadvertently tinting `ant_logo.png` into a solid blue square. Additionally, a BLE fallback hint was added under the assumption that athletes might be unaware that BLE doesn't need drivers. On-device review revealed this hint to be distracting in an ANT+ diagnostic flow.
* **Preservation of Core Invariants**: All ANT+ service detection, driver install triggers, and zero cold-start popups remain strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Untinted Official ANT+ Logo)**:
  * *Given* an athlete opening `AntServicesStatusSheet`
  * *When* the title row of the bottom sheet is rendered
  * *Then* the official ANT+ logo (`R.drawable.ant_logo`) is clearly displayed in black and white without solid blue monochrome tinting.
* **Criterion 2 (Elimination of BLE Hint Card)**:
  * *Given* an athlete viewing `AntServicesStatusSheet`
  * *When* inspecting the sheet contents
  * *Then* no Bluetooth LE recommendation or advisory card is present; the sheet focuses strictly on ANT+ hardware prerequisites and system services.
* **Criterion 3 (String Resource Cleanliness & 9-Language Parity)**:
  * *Given* all 9 supported application locales
  * *When* executing translation parity tests
  * *Then* `ant_status_ble_alternative_note` is removed cleanly across all 9 `strings.xml` files with zero missing keys or broken specifiers among active keys.

---

## 2. Test Specification (TST-UI-269)

### Test Case 1: `testAntServicesStatusSheet_passesUntintedIconAndOmitsBleHint` (`TST-UI-269.1`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/ant/AntServicesStatusContractTest.kt`
* **Preconditions**: `AntServicesStatusSheet.kt` exists.
* **Action**: Read and inspect source structure of `AntServicesStatusSheet.kt`.
* **Expected Result**:
  1. Contains `iconTint = Color.Unspecified`.
  2. Does not contain `ant_status_ble_alternative_note`.
  3. Does not reference `logo_protocol_bluetooth`.

### Test Case 2: 9-Language Localization Audit & Cleanup Verification (`TST-UI-269.2`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/ant/AntStatusLocalizationTest.kt`
* **Preconditions**: All 9 `strings.xml` files exist (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
* **Action**: Execute `AntStatusLocalizationTest`.
* **Expected Result**:
  1. `ant_status_ble_alternative_note` is absent from all 9 `strings.xml` files.
  2. All remaining active ANT status strings maintain 100% parity across all 9 locales.

### Test Case 3: Clean-Room Regression Suite (`TST-UI-269.3`)
* **Scope**: Full Clean-Room Regression
* **Command**: `./gradlew testDebugUnitTest`
* **Preconditions**: Code modifications and contract test updates committed on `feature/ATT-2749`.
* **Action**: Execute complete Gradle unit test suite.
* **Expected Result**: 100% pass rate with 0 failures across all unit test classes.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-269.1` | Contract | `AntServicesStatusContractTest.testAntServicesStatusSheet_passesUntintedIconAndOmitsBleHint` | `REQ-UI-309` | Specified |
| `TST-UI-269.2` | Localization | `AntStatusLocalizationTest.testAntStatusStringsParity` | `REQ-UI-309`, `REQ-UI-106` | Specified |
| `TST-UI-269.3` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001`, `REQ-UI-309` | Specified |
