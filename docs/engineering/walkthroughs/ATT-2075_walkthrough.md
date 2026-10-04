# Stage 5 Verification Walkthrough: ATT-2075 - Modernize Permission Flow: Contextual Just-in-Time Prompts & Graceful Settings Return Handling (Rework Cycle)

**Ticket**: [ATT-2075](https://rainerblind.atlassian.net/browse/ATT-2075)  
**Sub-task**: [ATT-2381](https://rainerblind.atlassian.net/browse/ATT-2381) (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` (per ASPICE Rule 19: Lösungsversion added upon final acceptance)  
**Active Sprint**: `2026-40.15`  
**Branch**: `feature/ATT-2075`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Executive Summary & Verification Scope

This rework iteration resolves the two functional defects reported during the Sprint Review on a physical test device (Google Pixel 10 running Android 16):
1. **Background Location Escalation**: Implemented progressive escalation to `ACCESS_BACKGROUND_LOCATION` ("Allow all the time" / "Immer zulassen") following Android 11+ two-step cascade rules, providing clear athletic guidance on why background location is required when recording in a jersey pocket with locked screen.
2. **Battery Optimization Exemption**: Implemented detection of active battery optimization (`PowerManager.isIgnoringBatteryOptimizations`) and contextual prompting before tracking starts, accompanied by a 3-tier fail-safe intent dispatcher preventing crashes on OEM ROMs (MIUI, EMUI, OneUI).
3. **Reactive State Polling & Warning Badge**: `DisposableEffect(lifecycleOwner)` on `ON_RESUME` polls location and battery optimization states reactively, dynamically updating the amber warning badge on `ControlTrackingButton`.

---

## 2. Verification Results

### 2.1 Targeted Unit Tests
* `ControlTrackingPermissionTest`:
  - `testOnStartTracking_dispatchesStartTrackingBroadcast`: Passed
  - `testWarningBadgePredicate_logicMatchesPermissionState`: Passed
  - `testProgressiveSetupFlow_missingForegroundTransitionsToForegroundRationale`: Passed
  - `testProgressiveSetupFlow_foregroundGrantedMissingBgTransitionsToBgRationale`: Passed
  - `testProgressiveSetupFlow_locationGrantedMissingBatteryTransitionsToBatteryRationale`: Passed
  - `testProgressiveSetupFlow_allGrantedDispatchesStart`: Passed
  - `testFailSafeBatteryOptimizationLaunch_doesNotCrashOnActivityNotFoundException`: Passed
* `PermissionRationaleSheetContractTest`:
  - `testPermissionRationaleSheetComposablesExist`: Passed
  - `testRationaleTypeEnumConstants`: Passed
  - `testRationaleStepEnumConstants`: Passed
  - `testRequiredStringResourcesExist`: Passed
  - `testRequiredDrawableResourcesExist`: Passed
  - `testNineLanguageLocalizationAudit`: Passed (100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT)
* `MainActivityStartupPermissionContractTest`:
  - `testColdStartDoesNotInvokeModalDialogs`: Passed

### 2.2 Clean-Room Full Suite Regression Execution
* Command: `./gradlew testDebugUnitTest`
* Result: 100% pass rate across the full application test suite with 0 failures.

### 2.3 APK Build Verification
* Command: `./gradlew assembleDebug`
* Result: BUILD SUCCESSFUL (debug APK ready for physical device testing).

---

## 3. Changeset Summary

| Component | File Path | Key Changes |
| :--- | :--- | :--- |
| **UI Screen** | `ControlTrackingScreen.kt` | Added `RationaleStep` state machine, dedicated `bgLocationLauncher`, fail-safe `launchBatteryOptimizationIntent`, and `ON_RESUME` reactive polling. |
| **UI BottomSheet** | `PermissionRationaleSheet.kt` | Extended with `RationaleType` enum (`FOREGROUND`, `BACKGROUND_LOCATION`, `BATTERY_OPTIMIZATION`), specific copy, icons, and Material 3 surfaces. |
| **Unit Tests** | `ControlTrackingPermissionTest.kt` | Added progressive state machine tests and fail-safe intent handling verification. |
| **Contract Tests** | `PermissionRationaleSheetContractTest.kt` | Added enum validation, resource verification, and 9-language localization parity audit. |

---

## 4. ASPICE Traceability Matrix

| Requirement | Test Specification | Deliverable | Status |
| :--- | :--- | :--- | :--- |
| **REQ-PRI-003** | `TST-PRI-002` | `ControlTrackingScreen.kt`, `PermissionRationaleSheet.kt` | Verified |
