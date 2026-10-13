# Stage 1 Analysis: ATT-2513 - Modernize ANT+ Service Installation Check by Removing Startup Dialog and Adding Contextual Compose Card

**Ticket**: [ATT-2513](https://atrainingtracker.atlassian.net/browse/ATT-2513)  
**Sub-task**: [ATT-2720](https://atrainingtracker.atlassian.net/browse/ATT-2720) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2513`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation
On every fresh app installation on modern Android devices (which lack built-in ANT+ chipsets), an outdated legacy dialog (`InstallANTShitDialog.java`, authored in 2017) immediately pops up during `MainActivityWithNavigation.onCreate()`.

This causes substantial athlete friction:
1. **Intrusive Cold Start**: Athletes installing the app to record a workout using GPS or standard Bluetooth LE sensors (heart rate straps, power meters) are immediately confronted with a technical dialog demanding the installation of proprietary ANT+ background services from Google Play.
2. **Outdated Legacy UI**: The dialog utilizes legacy Android 5 XML view layouts (`check_ant_installation_dialog.xml`), raw `AlertDialog`, hardcoded button weights, and stark red/green status texts (`R.color.dark_green` / `R.color.dark_red`) that clash with the app's modern Material 3 design system.
3. **Shifting Hardware Landscape**: In 2017, many flagship phones (e.g., Samsung Galaxy S6–S10) possessed built-in ANT+ radios. In 2026, modern smartphones require an external USB-OTG adapter plus an ANT+ USB stick (e.g., Dynastream / Garmin USB-m stick) to communicate over ANT+. Suggesting that users install ANT+ services without hardware context is confusing and counterproductive.

---

## 2. Chesterton's Fence & Requirement Archaeology
- **Historical Origin**: In early iterations of `aTrainingTracker` (commit `05.01.17`), ANT+ was the primary sensor protocol for cycling. Checking service readiness on startup ensured users with ANT+-capable phones had the prerequisite software pipeline (`ANT Radio Service`, `ANT+ Plugins Service`, `ANT USB Service`) installed.
- **Why It Must Change**: Modern onboarding must be zero-friction. GPS and Bluetooth LE are standard and require zero supplemental services. Athletes should only be informed of ANT+ prerequisites **in context**: when navigating to ANT+ sensor management or initiating an ANT+ pairing scan.
- **Invariants to Preserve**:
  - Athletes with physical ANT+ dongles must still be able to check service status, download necessary drivers from Google Play, and pair ANT+ sensors seamlessly.
  - The manual diagnostic entry point ("ANT+ Installation prüfen") in the sensor screen header menu must remain functional and open the modernized UI.
  - Existing pairing and data processing in `BANALService` must remain 100% operational.

---

## 3. Root Cause Analysis
1. **Unconditional Invocation**:
   `MainActivityWithNavigation.kt` lines 402–405:
   ```kotlin
   // check ANT+ installation
   if (TrainingApplication.checkANTInstallation() && !BANALService.areAllANTServicesInstalled(this)) {
       showInstallANTShitDialog()
   }
   ```
   `TrainingApplication.checkANTInstallation()` defaults to `true` on cold install. Since modern phones lack `ANT Radio Service`, `!BANALService.areAllANTServicesInstalled(this)` evaluates to `true`, triggering the dialog immediately on `onCreate()`.
2. **Coupling to Legacy DialogFragment**:
   Both `MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt`, and `DevicesTabbedContainerFragment.kt` invoke `InstallANTShitDialog().show(fragmentManager, TAG)`.
3. **Absence of Contextual Guidance**:
   When an athlete views ANT+ sensors or selects ANT+ in `PairingProtocolBottomSheet`, there is no informative Material 3 card explaining USB-OTG requirements or allowing in-place service installation.

---

## 4. Proposed Solution & Architecture

### 4.1 Eliminate Startup Check
- Remove the automatic `checkANTInstallation` / `showInstallANTShitDialog()` invocation from `MainActivityWithNavigation.onCreate()`.
- The app launches cleanly into the main cockpit without any ANT+ prompts.

### 4.2 Modern Material 3 ANT+ Guidance Component (`AntServicesStatusSheet.kt` / `AntServicesStatusCard.kt`)
- Create a reusable Compose component (`AntServicesStatusSheet.kt`) built on `AppModalBottomSheet`:
  - **Contextual Explainer**: Explains that ANT+ on modern smartphones requires an external USB-OTG adapter and ANT+ USB stick, whereas Bluetooth LE requires no additional hardware.
  - **Live Service Status List**:
    - **ANT+ Plugins Service** (`com.dsi.ant.plugins.antplus`): Checks `BANALService.isANTPluginServiceInstalled(context)`. Displays state badge ("Installed" / "Not installed") and an "Install" / "Update" button launching Google Play (`market://details?id=...` with HTTPS fallback).
    - **ANT Radio Service** (`com.dsi.ant.service.socket`): Checks `BANALService.isANTRadioServiceInstalled()`. Displays state badge and Google Play button.
    - **ANT USB Service** (`com.dsi.ant.usbservice`): If USB host is supported (`BANALService.hasUsbHostFeature(context)`), displays state badge and Google Play button.
  - **BLE Reassurance Banner**: Informs the user that standard Bluetooth LE sensors work directly without any extra services.
- In `DevicesTabbedScreen.kt`:
  - If `protocol == Protocol.ANT_PLUS` and `!BANALService.areAllANTServicesInstalled(context)`:
    - Display an inline M3 `AntServicesStatusCard` at the top of the sensor list or empty state.
  - Tapping "ANT+ Installation prüfen" in the header overflow menu opens `AntServicesStatusSheet`.
- In `PairingProtocolBottomSheet.kt`:
  - When the athlete taps ANT+, if `!BANALService.areAllANTServicesInstalled(context)`, present `AntServicesStatusSheet` before or alongside device type selection so the user understands why scanning may not find devices without the services.

### 4.3 Clean-up of Legacy Dialog
- Deprecate or retire `InstallANTShitDialog.java` and `check_ant_installation_dialog.xml` once all triggers are routed through the modern Compose UI.

---

## 5. Scope & Impact
- **Modified Production Files**:
  - `MainActivityWithNavigation.kt`: Remove startup invocation.
  - `DevicesTabbedScreen.kt`: Wire contextual status card and sheet.
  - `PairingProtocolBottomSheet.kt`: Provide contextual service guidance.
  - `ATrainingTrackerApp.kt`: Replace legacy dialog trigger.
  - `res/values*/strings.xml`: 9-language localization parity for new guidance text.
- **New Files**:
  - `com.atrainingtracker.banalservice.ui.devices.ant.AntServicesStatusSheet.kt`
  - Unit and contract tests for ANT+ status guidance and localization.

---

## 6. Risk Assessment
- **Zero Regression Invariant**: Athletes using ANT+ dongles must still be able to install services and pair sensors without impediment.
- **Google Play Invariant**: Service installation intents must only launch upon athlete button tap (Rule 21).
- **Localization Parity**: 100% 9-language translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
