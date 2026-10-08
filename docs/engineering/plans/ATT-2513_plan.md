# Stage 3 Implementation Plan: ATT-2513 - Modernize ANT+ Service Installation Check by Removing Startup Dialog and Adding Contextual Compose Card

**Ticket**: [ATT-2513](https://atrainingtracker.atlassian.net/browse/ATT-2513)  
**Sub-task**: [ATT-2722](https://atrainingtracker.atlassian.net/browse/ATT-2722) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2513`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Architecture & Design Principles (SWE.2)

### 1.1 Cold-Start Zero Friction
- On startup (`MainActivityWithNavigation.onCreate()`), remove the legacy `checkANTInstallation()` call. No modal dialogs or background checks shall interrupt athletes launching the app for GPS tracking or Bluetooth LE sensor usage.

### 1.2 Contextual Compose Components
- Create `com.atrainingtracker.banalservice.ui.devices.ant.AntServicesStatusSheet.kt` and `AntServicesStatusCard.kt`:
  - **`AntServicesStatusCard`**: A non-blocking Material 3 `ElevatedCard` rendered at the top of `DevicesTabbedScreen` when `protocol == Protocol.ANT_PLUS` and `!BANALService.areAllANTServicesInstalled(context)`. Displays a concise explanation of USB-OTG stick requirements and an action button to open the diagnostic sheet.
  - **`AntServicesStatusSheet`**: An `AppModalBottomSheet` detailing:
    1. Modern hardware explanation (smartphones lack internal ANT+ chips; USB-OTG + ANT+ USB dongle required).
    2. Real-time installation status for each required system service:
       - ANT+ Plugins Service (`com.dsi.ant.plugins.antplus`)
       - ANT Radio Service (`com.dsi.ant.service.socket`)
       - ANT USB Service (`com.dsi.ant.usbservice`, if USB Host supported)
    3. Actionable "Install" buttons targeting Google Play (`market://details?id=...` with HTTPS fallback).
    4. Bluetooth LE reassurance callout noting that standard BLE sensors work directly without extra hardware.
- Wire into `DevicesTabbedScreen.kt`:
  - Header overflow menu item "ANT+ Installation prüfen" directly opens `AntServicesStatusSheet`.
  - When `PairingProtocolBottomSheet` receives a tap on ANT+, if services are absent, it presents `AntServicesStatusSheet` so the athlete understands the hardware/driver prerequisites before scanning.

### 1.3 UI Consistency Invariants (Rule 23)
- Use standard Material 3 design tokens:
  - Cards: `RoundedCornerShape(12.dp)`, `surfaceContainerHigh` or `secondaryContainer`, `8.dp` / `16.dp` padding.
  - Badges: `FilterChip` / `AssistChip` styling or Material 3 status colors (`primary` / `errorContainer`).
  - Google Play intents dispatched strictly on explicit athlete button click (Rule 21).

---

## 2. Step-by-Step Implementation Work Breakdown

### Step 1: 9-Language Localization
- Add strings to all 9 `strings.xml` files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`):
  - `ant_status_sheet_title`
  - `ant_status_card_title`
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

### Step 2: Remove Cold-Start Check in `MainActivityWithNavigation.kt`
- Remove lines 402–405 in `MainActivityWithNavigation.kt`:
  ```kotlin
  // check ANT+ installation
  if (TrainingApplication.checkANTInstallation() && !BANALService.areAllANTServicesInstalled(this)) {
      showInstallANTShitDialog()
  }
  ```

### Step 3: Implement `AntServicesStatusSheet.kt` and `AntServicesStatusCard.kt`
- Create `AntServicesStatusCard.kt` and `AntServicesStatusSheet.kt` in package `com.atrainingtracker.banalservice.ui.devices.ant`.
- Implement service status queries using existing methods in `BANALService`:
  - `BANALService.isANTPluginServiceInstalled(context)`
  - `BANALService.isANTRadioServiceInstalled()`
  - `BANALService.isANTUSBServiceInstalled()`
  - `BANALService.hasUsbHostFeature(context)`
- Implement safe market launcher helper `launchServiceInstallIntent(context, packageName)`.

### Step 4: Wire Contextual Guidance into Sensor Management & Pairing
- In `DevicesTabbedScreen.kt`:
  - Replace `onCheckAntInstallation: () -> Unit` parameter or default implementation to open `AntServicesStatusSheet`.
  - If `protocol == Protocol.ANT_PLUS && !BANALService.areAllANTServicesInstalled(context)`, render `AntServicesStatusCard` above the device list.
  - In `PairingProtocolBottomSheet.kt`: if athlete chooses ANT+ and `!BANALService.areAllANTServicesInstalled(context)`, present the guidance sheet.
- In `ATrainingTrackerApp.kt`: update `NavRoutes.SENSORS` composable.

### Step 5: Test Verification
- Implement `AntStartupCheckContractTest.kt`.
- Implement `AntServicesStatusContractTest.kt`.
- Implement `AntStatusLocalizationTest.kt`.
- Run targeted tests and full regression test suite (`./gradlew testDebugUnitTest`).
