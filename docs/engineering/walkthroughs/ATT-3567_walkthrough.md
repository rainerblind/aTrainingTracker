# Stage 5 Walkthrough - ATT-3567: Remove Unnecessary FOREGROUND_SERVICE_CONNECTED_DEVICE Permission from Manifest and TrackerService

**Ticket**: [ATT-3567](https://atrainingtracker.atlassian.net/browse/ATT-3567)  
**Parent Epic**: [ATT-431](https://atrainingtracker.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Target Release**: `V4.9.40`  
**Requirement Mapping**: `REQ-TRK-022` (*Streamlined Dual-Type (Location and Health) Foreground Service Architecture & Removal of Redundant Connected Device Permission*)  
**Test Spec ID**: `TST-TRK-014` (*Streamlined Dual-Type (Location and Health) Foreground Service Verification Suite*)  
**Branch**: `feature/ATT-3567`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Executive Summary

In ticket `ATT-2773`, `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE` and `foregroundServiceType="...|connectedDevice"` were introduced as part of the initial Android 14 (API 34) migration.

However, subsequent platform analysis and Google Play Console policy audits revealed that:
1. Declaring `FOREGROUND_SERVICE_CONNECTED_DEVICE` triggers a specialized Google Play Console review questionnaire for external connected devices / automotive keys, which is mismatched with an athletic workout tracking app.
2. `aTrainingTracker`'s core workout recording engine already operates under `FOREGROUND_SERVICE_LOCATION` (GPS tracking) and `FOREGROUND_SERVICE_HEALTH` (heart rate, cadence, power sensor telemetry), both of which are officially approved for health & fitness apps and keep the service process alive in the foreground with partial CPU wake locks.
3. BLE sensor discovery and peripheral scanning (`BTSearchForNewDevicesEngine`) run interactively in the foreground UI, while background BLE GATT callbacks are delivered reliably without needing the separate `connectedDevice` FGS type.

Ticket **ATT-3567** streamlines the Foreground Service architecture:
- Purged `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />` from `AndroidManifest.xml`.
- Streamlined `TrackerService` and `BANALService` declarations to `android:foregroundServiceType="location|health"`.
- Simplified `TrackerService.determineForegroundServiceType()` to evaluate strictly `FOREGROUND_SERVICE_TYPE_LOCATION` and permission-gated `FOREGROUND_SERVICE_TYPE_HEALTH`, completely eliminating `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`.
- Authored `MergedManifestFgsAuditTest.kt` to permanently guard manifest compliance.
- Passed 100% of the full clean-room unit test suite (`./gradlew testDebugUnitTest`).

---

## 2. Changes Implemented

### 2.1 Manifest Permission & Service Cleanup (`app/src/main/AndroidManifest.xml`)
- Removed line 43: `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />`.
- Retained `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, and `FOREGROUND_SERVICE_HEALTH`.
- Updated `<service android:name=".trainingtracker.tracker.TrackerService" android:foregroundServiceType="location|health" />`.
- Updated `<service android:name=".banalservice.BANALService" android:foregroundServiceType="location|health" />`.

### 2.2 TrackerService Foreground Service Type Simplification (`TrackerService.java`)
- Simplified `determineForegroundServiceType()`:
  - Base type is `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION`.
  - On API 34+ (UPSIDE_DOWN_CAKE), adds `ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH` if `hasHealthPermission()` is true.
  - Never includes `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`.
- Removed dead helper method `hasBluetoothPermission()`.

### 2.3 Test Suite & Architectural Audit Guard
- `TrackerServiceFGSTypeTest.kt`:
  - Verified `determineForegroundServiceType()` returns `LOCATION` when health is not granted.
  - Verified `determineForegroundServiceType()` returns `LOCATION | HEALTH` when health is granted.
  - Verified `CONNECTED_DEVICE` bit is strictly 0 under all circumstances.
  - Verified pre-API 34 fallback behavior.
- `TrackerServiceFinalizationTest.kt`:
  - Updated `testDetermineForegroundServiceType_WithoutHealthPermission_OmitsHealthType()` to expect `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION` and assert absence of `CONNECTED_DEVICE`.
- `MergedManifestFgsAuditTest.kt`:
  - Authored new contract test verifying `AndroidManifest.xml` does not declare `FOREGROUND_SERVICE_CONNECTED_DEVICE` and that both `TrackerService` and `BANALService` declare `location|health`.

---

## 3. Verification & Test Evidence

### 3.1 Targeted Unit Tests
```bash
./gradlew testDebugUnitTest \
  --tests com.atrainingtracker.trainingtracker.tracker.TrackerServiceFGSTypeTest \
  --tests com.atrainingtracker.trainingtracker.tracker.TrackerServiceFinalizationTest \
  --tests com.atrainingtracker.trainingtracker.tracker.MergedManifestFgsAuditTest
```
Result: **BUILD SUCCESSFUL** (100% pass rate).

### 3.2 Clean-Room Test Suite Execution
```bash
./gradlew testDebugUnitTest
```
Result: **BUILD SUCCESSFUL in 4m 45s** (32 actionable tasks, 0 failures).

---

## 4. Invariant & Governance Compliance

- **Rule 2 Mandate**: Autonomously proceeded through all 5 ASPICE gates without halting.
- **Rule 3 Human Gate**: Parent ticket strictly advanced to `Final Review (Human)`. Never transitioned parent tickets to `Erledigt`.
- **Chesterton's Fence**: Preserved `REQ-CON-019` GATT connection logic and `REQ-TRK-015` health permission gating while safely eliminating unnecessary Google Play review overhead.
- **Rule 6 & Rule 19**: No premature Fix Version assigned; subtasks have no Fix Version.
- **UI Invariant (Rule 23)**: Pure service/manifest refactoring; zero UI modifications.
