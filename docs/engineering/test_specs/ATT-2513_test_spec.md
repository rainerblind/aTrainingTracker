# Stage 2: Requirement & Test Specification - ATT-2513

**Ticket**: [ATT-2513](https://atrainingtracker.atlassian.net/browse/ATT-2513)  
**Sub-task**: [ATT-2721](https://atrainingtracker.atlassian.net/browse/ATT-2721) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2513`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Traceability Matrix

| Requirement ID | Test Case ID | Scope / Focus | Success Criteria |
|:---|:---|:---|:---|
| `REQ-UI-296` Clause 1 | `TST-UI-256` Step 1 | Cold-Start Elimination (`MainActivityWithNavigation.kt`) | App launches without `checkANTInstallation()` invocation; zero popups on fresh launch. |
| `REQ-UI-296` Clause 2, 4 | `TST-UI-256` Step 2 | Contextual Guidance Card (`AntServicesStatusCard.kt`) | Renders when viewing ANT+ sensors with missing services; explains USB-OTG requirement. |
| `REQ-UI-296` Clause 3, 4 | `TST-UI-256` Step 2 | Contextual Diagnostic Sheet (`AntServicesStatusSheet.kt`) | Shows individual service states with Google Play install buttons; triggered from card, menu, and pairing. |
| `REQ-UI-296` Clause 5 | `TST-UI-256` Step 3 | 9-Language Localization Parity | All new strings exist, non-empty, and valid across EN, DE, ES, FR, IT, JA, NL, PL, PT. |
| `REQ-UI-296` Clause 6 | `TST-UI-256` Step 4 | Clean-Room Full Suite Regression | 100% test pass rate with 0 regressions across entire test suite. |

---

## 2. Test Specifications

### 2.1 Cold-Start Elimination (`AntStartupCheckContractTest.kt`)
- **Objective**: Ensure cold start is completely clean of any ANT+ installation checks or dialogs.
- **Assertions**:
  - `MainActivityWithNavigation.kt` AST/contract check: no calls to `checkANTInstallation` or `showInstallANTShitDialog` within `onCreate()`.
  - Cold launch does not present any fragment or alert dialog.

### 2.2 Contextual Status UI & Sheet Contract Tests (`AntServicesStatusContractTest.kt`)
- **Objective**: Verify `AntServicesStatusCard` and `AntServicesStatusSheet` render correctly based on system service presence.
- **Scenarios**:
  1. *All Services Installed*:
     - When `BANALService.areAllANTServicesInstalled(context) == true`, `AntServicesStatusCard` is not rendered.
  2. *Services Missing*:
     - When `BANALService.areAllANTServicesInstalled(context) == false` and `protocol == Protocol.ANT_PLUS`:
       - `AntServicesStatusCard` is rendered in `DevicesTabbedScreen`.
       - Tapping the action button opens `AntServicesStatusSheet`.
  3. *Sheet Item Details*:
     - When `isANTPluginServiceInstalled == false`, status shows "Not installed" and "Install" button is enabled with Google Play URI.
     - When `isANTRadioServiceInstalled == false`, status shows "Not installed" and "Install" button is enabled with Google Play URI.
     - When `isANTUSBServiceInstalled == false` and `hasUsbHostFeature == true`, status shows "Not installed" and "Install" button is enabled.
     - When a service is installed, badge displays "Installed" (or checkmark) and install button is disabled or hidden.
     - Bluetooth LE reassurance message is displayed at the bottom of the sheet.
  4. *Pairing Protocol Bottom Sheet Integration*:
     - When athlete selects `Protocol.ANT_PLUS` in `PairingProtocolBottomSheet` while services are missing, `AntServicesStatusSheet` is presented.

### 2.3 9-Language Localization Parity Audit (`AntStatusLocalizationTest.kt`)
- **Objective**: Ensure all 9 supported locales have complete, non-empty translations for all new ANT status string resources.
- **Keys to Verify**:
  - `ant_status_sheet_title`
  - `ant_status_card_desc`
  - `ant_status_card_action`
  - `ant_status_usb_dongle_note`
  - `ant_status_ble_alternative_note`
  - `ant_service_plugin_name`
  - `ant_service_radio_name`
  - `ant_service_usb_name`
  - `ant_service_installed`
  - `ant_service_not_installed`
  - `ant_service_install_button`
- **Locales**: `values/` (EN), `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

### 2.4 Full Clean-Room Regression Suite
- Run `./gradlew testDebugUnitTest` ensuring zero regressions across all modules.
