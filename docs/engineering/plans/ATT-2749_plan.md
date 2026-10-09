# Stage 3: Implementation Plan - ATT-2749: Display official untinted ANT+ logo and remove BLE hint from ANT+ status sheet

**Ticket**: [ATT-2749](https://atrainingtracker.atlassian.net/browse/ATT-2749)  
**Sub-task**: [ATT-2839](https://atrainingtracker.atlassian.net/browse/ATT-2839) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-309` (*Official Untinted ANT+ Logo Rendering and Bluetooth LE Advisory Note Elimination in ANT+ System Services Sheet*)  
**Test Mapping**: `TST-UI-269` (*ANT+ Status Sheet Untinted Branding, BLE Hint Elimination, and Localization Parity Verification*)  
**Branch**: `feature/ATT-2749`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Background

During Sprint Review 2026-41.3 on a Google Pixel 10 (ATT-2513 verification), two visual issues were identified in the modernized ANT+ status sheet (`AntServicesStatusSheet.kt`):
1. **Solid Blue Square**: In the top-left title bar, a solid blue square was displayed instead of the official ANT+ logo because `AppModalBottomSheet` automatically applies a primary tint to `iconPainter`, turning the raster `ant_logo.png` into a solid blue rectangle. The official multi-color/high-contrast ANT+ logo must be displayed untinted (`Color.Unspecified`).
2. **Extraneous Bluetooth LE Hint**: At the bottom of `AntServicesStatusSheet`, an advisory card suggests: *"Tipp: Bluetooth LE Sensoren funktionieren direkt ohne Zusatzhardware oder Systemdienste."* (`ant_status_ble_alternative_note`). In an ANT+ status diagnostic sheet, this recommendation is distracting and redundant; athletes opening this sheet are specifically checking or troubleshooting ANT+ USB hardware and drivers.

This plan details the construction steps to display the authentic untinted ANT+ logo in the title bar, remove the BLE card container from `AntServicesStatusSheet.kt`, eliminate obsolete string resources across all 9 locales, and update automated contract tests.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-309` (*Official Untinted ANT+ Logo Rendering and Bluetooth LE Advisory Note Elimination in ANT+ System Services Sheet*)
* **Test Mapping**: `TST-UI-269` (*ANT+ Status Sheet Untinted Branding, BLE Hint Elimination, and Localization Parity Verification*)
  * `TST-UI-269.1`: Unit / Contract Test in `AntServicesStatusContractTest.kt` asserting `iconTint = Color.Unspecified` and absence of BLE card/icon/string references.
  * `TST-UI-269.2`: Localization Parity Test in `AntStatusLocalizationTest.kt` validating removal of `ant_status_ble_alternative_note` across all 9 locales and 100% parity of remaining keys.
  * `TST-UI-269.3`: Clean-Room Regression Test Suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Diagnostic Regressions**: All live package detection methods (`isANTPluginServiceInstalled`, `isANTRadioServiceInstalled`, `isANTUSBServiceInstalled`) and Google Play installation intents in `BANALService` remain 100% functional.
2. **Zero Cold-Start Popups**: No automatic startup dialogs or modal prompts are triggered on cold launch (`MainActivityWithNavigation.kt`).
3. **Subtask Direct Completion**: Sub-tasks transition directly to `Erledigt` upon passing Agent 2 Gate audit via `freigabe`.
4. **Parent Human Gate Invariance**: Terminal transition of `ATT-2749` to `Erledigt` remains exclusively reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `AntServicesStatusSheet.kt` (UI Layer)
* Update `AppModalBottomSheet` call:
  - Add explicit parameter `iconTint = Color.Unspecified`.
  - Import `androidx.compose.ui.graphics.Color`.
* Remove BLE Alternative Note:
  - Delete `Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer ...)) { ... }` block (lines 165–190).
  - Remove unused import of `R.drawable.logo_protocol_bluetooth`.

### Component 2: 9-Language Resource Layer (`strings.xml`)
* Remove `<string name="ant_status_ble_alternative_note">...</string>` from:
  1. `app/src/main/res/values/strings.xml`
  2. `app/src/main/res/values-de/strings.xml`
  3. `app/src/main/res/values-es/strings.xml`
  4. `app/src/main/res/values-fr/strings.xml`
  5. `app/src/main/res/values-it/strings.xml`
  6. `app/src/main/res/values-ja/strings.xml`
  7. `app/src/main/res/values-nl/strings.xml`
  8. `app/src/main/res/values-pl/strings.xml`
  9. `app/src/main/res/values-pt/strings.xml`

### Component 3: Test Layer
* Update `AntStatusLocalizationTest.kt`:
  - Remove `"ant_status_ble_alternative_note"` from `translatableKeys`.
  - Add explicit assertion that `ant_status_ble_alternative_note` does not exist in any locale file.
* Update `AntServicesStatusContractTest.kt`:
  - In `testAntServicesStatusSheetDeclaration()`, assert that `AntServicesStatusSheet.kt` contains `iconTint = Color.Unspecified`.
  - Assert that `AntServicesStatusSheet.kt` does not contain `ant_status_ble_alternative_note` or `logo_protocol_bluetooth`.

### UI Consistency (Rule 23)
* **Reference screen / component**:
  - `PairingButtons.kt` (lines 91–98), which renders protocol icons (`R.drawable.ant_logo` and `R.drawable.logo_protocol_bluetooth`) untinted using `tint = Color.Unspecified`.
  - `AppModalBottomSheet.kt` (line 76 KDoc: `@param iconTint Tint applied to the leading icon (defaults to primary; use [Color.Unspecified] for multi-color sport logos)`).
* **Reused components**: `AppModalBottomSheet`, `ServiceStatusRow`, `CardDefaults`, `Button`.
* **Theme tokens**: `Color.Unspecified` to preserve authentic raster branding colors; standard `MaterialTheme.colorScheme` tokens.
* **New one-off styles & justification**: None. Reuses existing architecture and design system components.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Tests First (TDD)
* Files:
  - `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/ant/AntStatusLocalizationTest.kt`
  - `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/ant/AntServicesStatusContractTest.kt`
* Changes:
  - Update `AntStatusLocalizationTest` to omit `ant_status_ble_alternative_note` from required keys and assert its absence.
  - Update `AntServicesStatusContractTest` to assert `iconTint = Color.Unspecified` and absence of BLE card/string references.

### Step 2: Remove Obsolete String Resource Across All 9 Locales
* Files:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* Changes: Remove `<string name="ant_status_ble_alternative_note">...</string>`.

### Step 3: Update `AntServicesStatusSheet.kt`
* Files:
  - `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/ant/AntServicesStatusSheet.kt`
* Changes:
  - Add `iconTint = Color.Unspecified` to `AppModalBottomSheet`.
  - Remove BLE Alternative Note `Card` container.
  - Remove unused imports.

### Step 4: Targeted Unit Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.ant.*"
  ```
* Expected: All tests in package pass cleanly in ~10 seconds.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Execute targeted unit tests in Stage 4.
  - Execute full clean-room regression suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**:
  - `git checkout sprint/2026-41.4 && git branch -D feature/ATT-2749` completely discards changes with zero impact on main branches.
