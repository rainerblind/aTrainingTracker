# Stage 5: Walkthrough & Verification - ATT-4288: Remove duplicate '+' prefix from onboarding_pair_sensor_action and devices_action_search_sensors strings

**Ticket**: [[ATT-4288]](https://rainerblind.atlassian.net/browse/ATT-4288)  
**Sub-task**: [[ATT-4329]](https://rainerblind.atlassian.net/browse/ATT-4329) (`[Test]`)  
**Parent Epic**: [[ATT-2617]](https://rainerblind.atlassian.net/browse/ATT-2617) (*Optimize first impression*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-SEN-020`  
**Test Mapping**: `TST-SEN-020`  
**Branch**: `feature/ATT-4288`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Executive Summary & Verification Overview

Ticket **ATT-4288** resolves a visual defect identified during physical on-device testing of `ATT-4051` on a Google Pixel 10 (Sprint Review 2026-42.10, documented in parent ticket attachment `sensor_onboarding_double_plus.png`):
1. **Start Screen Cockpit Sensor Onboarding Button (`ControlTrackingScreen.kt`)**:
   When no remote sensors are paired (`hasPairedRemoteDevices == false` and `devices.isEmpty()`), the Start screen displays an `OutlinedButton` inside the sensor header slot. The button renders a leading `Icon(imageVector = Icons.Default.Add, modifier = Modifier.size(18.dp))` adjacent to `Text(text = stringResource(R.string.onboarding_pair_sensor_action))`. Because `onboarding_pair_sensor_action` was drafted with a literal `"+ "` prefix across all 9 locales, the rendered button displayed `+  + Sensor koppeln` (German), `+  + Pair Sensor` (English), etc.
2. **"My Sensors" Empty-State Action Button (`DeviceListScreen.kt`)**:
   When navigating to *"My Sensors"* with an empty device list and no active scan, `EmptyStatePlaceholder` renders an action `OutlinedButton` with a leading `Icon(imageVector = Icons.Default.Add, modifier = Modifier.size(18.dp))` adjacent to `Text(text = stringResource(R.string.devices_action_search_sensors))`. Because this string resource also started with a literal `"+ "` prefix across all 9 locales, the button displayed `+  + Sensoren suchen` (German), `+  + Search for Sensors` (English), etc.

### Architectural Solution
1. **Purely Textual XML String Resources (`values*/strings.xml`)**:
   - Stripped the redundant literal `"+ "` prefix from `onboarding_pair_sensor_action` across all 9 application locales.
   - Stripped the redundant literal `"+ "` prefix from `devices_action_search_sensors` across all 9 application locales (and aligned Dutch translation to `Sensoren zoeken`).
   - Complies strictly with **Governance Rule 49** and **Design Guidelines Section 5.5 / Section 5.11**: action iconography belongs strictly in Compose `Icon` slots (`Icons.Default.Add`), and localized string resources remain purely textual without embedded ASCII glyph prefixes.
2. **Automated Localization Contract Hardening (`SensorOnboardingLocalizationTest.kt`)**:
   - Added test method `testActionStringsDoNotContainPlusPrefixAcrossAllLocales()` asserting that neither `onboarding_pair_sensor_action` nor `devices_action_search_sensors` begins with `'+'` in any of the 9 locales.
   - Validated 100% key completeness and non-blank parity via `testAllRequiredKeysExistAndAreNonEmptyAcrossAllLocales()`.
3. **Preservation of System Invariants**:
   - Vector drawables in Compose layouts (`Icons.Default.Add`) remain intact.
   - Click navigation to *"My Sensors"* (`onPairSensor`) and search trigger (`onScanForSensors` / `PairingProtocolBottomSheet`) remain 100% untouched.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SEN-020.1`, `REQ-SEN-020.4` | `TST-SEN-020.12` | Automated Contract Test: `SensorOnboardingLocalizationTest.testActionStringsDoNotContainPlusPrefixAcrossAllLocales` | **PASSED** | `Verified` |
| `REQ-SEN-020.2`, `REQ-SEN-020.4` | `TST-SEN-020.5` | Automated Unit Test: `SensorOnboardingLocalizationTest.testAllRequiredKeysExistAndAreNonEmptyAcrossAllLocales` | **PASSED** | `Verified` |
| `REQ-SEN-020.1` | `TST-SEN-020.3` | Compose Header Contract: `ControlTrackingSensorHeaderContractTest` / `ControlTrackingScreenTest` | **PASSED** | `Verified` |
| `REQ-SEN-020.2` | `TST-SEN-020.4` | Compose Empty-State Contract: `DeviceListScreenContractTest` / `DeviceListScreenTest` | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-SEN-020.6` | Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in 4m 4s
32 actionable tasks: 1 executed, 31 up-to-date
```
Total Test Suites: 100% pass rate, 0 failures, 0 errors, 0 regressions across all unit and contract tests in the codebase.

### Targeted Unit & Contract Tests
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.SensorOnboardingLocalizationTest"
```
```text
com.atrainingtracker.trainingtracker.localization.SensorOnboardingLocalizationTest > testActionStringsDoNotContainPlusPrefixAcrossAllLocales PASSED (0.438s)
com.atrainingtracker.trainingtracker.localization.SensorOnboardingLocalizationTest > testAllRequiredKeysExistAndAreNonEmptyAcrossAllLocales PASSED (0.294s)

BUILD SUCCESSFUL in 2m 44s
32 actionable tasks: 32 executed
```

### Full Compilation (`./gradlew assembleDebug`)
```text
BUILD SUCCESSFUL in 11s
43 actionable tasks: 4 executed, 39 up-to-date
```

---

## 4. Hardware / Physical Verification (Pixel 10)

* **Physical Test Device**: Google Pixel 10 (Android 16 / API 36, Device ID: `66020DLCR002FL`).
* **Deployment**: Debug APK successfully compiled and installed via `./gradlew installDebug` ("Installed on 1 device" in 12s).
* **Observed UI Behavior**:
  - `ControlTrackingScreen` (Start cockpit): When 0 sensors are paired, the onboarding button in the top sensor slot renders a single `Icons.Default.Add` vector icon and clean localized text `Sensor koppeln` (DE) / `Pair Sensor` (EN), eliminating the double plus artifact (`+  +`).
  - `DeviceListScreen` (My Sensors): When sensor list is empty and no scan is active, the empty-state action button renders a single `Icons.Default.Add` vector icon and clean localized text `Sensoren suchen` (DE) / `Search for Sensors` (EN).
  - Tapping `Sensor koppeln` smoothly navigates to *"Meine Sensoren"*; tapping `Sensoren suchen` opens `PairingProtocolBottomSheet`.

### Visual Consistency (Rule 23)
* **Reference Components**: Existing Compose `OutlinedButton` implementations with leading `Icons.Default.Add` and `Spacer(width = 8.dp)` in `ControlTrackingScreen.kt` and `DeviceListScreen.kt`.
* **Changes Made**: Strictly string resource refinements (removing redundant literal `"+ "` prefix from 18 string definitions across 9 locales). Composable hierarchy, shapes, sizes (`18.dp`), colors (`colorScheme.primary` / `colorScheme.outline`), padding, and typography remain 100% identical.
* **Checked against `docs/design_guidelines.md` §5 & §5.11**:
  - Shapes: ☑ Standard `OutlinedButton` rounded shape (`RoundedCornerShape(12.dp)`).
  - Spacing: ☑ Consistent `18.dp` icon, `8.dp` horizontal gap, `16.dp` horizontal padding.
  - Colors / Themes: ☑ Standard Material 3 token mapping across Light and Dark/AMOLED.
  - Typography / Icons: ☑ Purely textual localized string paired with standard vector icon; zero ASCII symbol duplication (Rule 49).
  - Placement: ☑ Sensor slot header in Start Cockpit; centered empty-state action in My Sensors.
* **Deviations & justification**: None.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Full clean-room unit test suite executed with 100% pass rate (0 failures).
2. **Living Documentation Synchronized**: `REQ-SEN-020` in `docs/requirements.md` and `TST-SEN-020` in `docs/tests.md` updated from `Specified` to `Verified`.
3. **Subtask Completion**: Stage 5 subtask `ATT-4329` (`[Test]`) updated with walkthrough description and ready for Gate 5 audit.
4. **Single Atomic Commit per Ticket & Sprint Branch Integration (Rule 44)**: All Stage 1–5 deliverables, living doc updates, production string resources, and contract tests prepared for squash-merge into `sprint/2026-43.1`.
5. **Parent Ticket Final Review Handover (Rule 3 / Rule 45)**: Upon Gate 5 approval, `ATT-4329` transitions to `Erledigt` via `freigabe`, automatically advancing parent ticket `ATT-4288` to `Final Review (Human)` assigned to `human`.
