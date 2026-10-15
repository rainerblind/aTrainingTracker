# Stage 3: Implementation Plan - ATT-3567: Remove Unnecessary FOREGROUND_SERVICE_CONNECTED_DEVICE Permission from Manifest and TrackerService

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

## 1. Executive Summary & Architectural Design (SWE.2)

This plan details the atomic steps to eliminate `FOREGROUND_SERVICE_CONNECTED_DEVICE` from the application manifest, purge `connectedDevice` from foreground service definitions, and simplify runtime FGS type evaluation in `TrackerService.java`.

### Architectural Model
```
+-----------------------------------------------------------------------------------+
|                        Foreground Service Architecture (API 34+)                  |
+-----------------------------------------------------------------------------------+
| AndroidManifest.xml:                                                              |
|   - <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />       |
|   - <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />|
|   - <uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH" />  |
|   - [REMOVED] FOREGROUND_SERVICE_CONNECTED_DEVICE                                 |
|                                                                                   |
| Services:                                                                         |
|   - TrackerService: android:foregroundServiceType="location|health"               |
|   - BANALService:   android:foregroundServiceType="location|health"               |
+-----------------------------------------------------------------------------------+
| Runtime Bitmask Evaluation (TrackerService.determineForegroundServiceType()):     |
|   - Base Type: FOREGROUND_SERVICE_TYPE_LOCATION                                  |
|   - If API >= 34 && hasHealthPermission() -> | FOREGROUND_SERVICE_TYPE_HEALTH    |
|   - FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE: NEVER included                      |
+-----------------------------------------------------------------------------------+
```

---

## 2. Invariants & Guardrails

1. **Uninterrupted Workout Tracking**:
   - `TrackerService` process remains protected in the foreground by `FOREGROUND_SERVICE_TYPE_LOCATION` (and `FOREGROUND_SERVICE_TYPE_HEALTH` when granted).
   - Partial CPU wake lock (`PARTIAL_WAKE_LOCK`) is maintained throughout the workout.
2. **Crash Immunity (Zero SecurityExceptions)**:
   - Dynamic permission gating in `determineForegroundServiceType()` ensures unpermitted types are never passed to `Service.startForeground()`.
3. **Sensor Telemetry Continuity**:
   - BLE GATT connections and incoming sensor notifications continue to be processed without alteration in `BANALService`.
4. **Google Play Policy Compliance**:
   - Eliminates the mandatory Google Play Console declaration requirement for external connected devices / automotive keys.
5. **No UI Changes**:
   - UI consistency check (Rule 23): This is a pure service/manifest refactoring ticket; zero Jetpack Compose or UI layouts are altered.

---

## 3. Atomic Implementation Steps

### Step 1: Manifest Cleanup (`app/src/main/AndroidManifest.xml`)
- Remove line 43: `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />`.
- Update line 190 (`TrackerService` declaration):
  Change `android:foregroundServiceType="location|health|connectedDevice"` to `android:foregroundServiceType="location|health"`.
- Update line 195 (`BANALService` declaration):
  Change `android:foregroundServiceType="location|health|connectedDevice"` to `android:foregroundServiceType="location|health"`.

### Step 2: Runtime Bitmask Derivation Simplification (`TrackerService.java`)
- Modify `determineForegroundServiceType()`:
  - Remove `hasBluetoothPermission()` check and `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` bitmask addition.
  - Retain base `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION` and permission-gated `ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH`.
- Remove dead method `hasBluetoothPermission()` from `TrackerService.java`.

### Step 3: Unit Test Updates (`TrackerServiceFGSTypeTest.kt` & `TrackerServiceFinalizationTest.kt`)
- `TrackerServiceFGSTypeTest.kt`:
  - Remove `bluetoothGranted` property and `override fun hasBluetoothPermission()`.
  - Update `testDetermineForegroundServiceType_WithoutBluetoothAndHealth_ReturnsLocationOnly`:
    - Rename/refactor to `testDetermineForegroundServiceType_WithoutHealth_ReturnsLocationOnly`.
    - Assert `fgsType == ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION`.
    - Assert `(fgsType and ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) == 0`.
    - Assert `(fgsType and ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH) == 0`.
  - Update `testDetermineForegroundServiceType_WithHealth_ReturnsLocationAndHealth`:
    - Assert `fgsType == (ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)`.
    - Assert `(fgsType and ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) == 0`.
  - Remove `testDetermineForegroundServiceType_WithBluetoothOnly_ReturnsLocationAndConnectedDevice`.
- `TrackerServiceFinalizationTest.kt`:
  - Remove `bluetoothGranted` property and `override fun hasBluetoothPermission()`.
  - Update `testDetermineForegroundServiceType_WithoutHealthPermission_OmitsHealthType`:
    - Change expected from `LOCATION or CONNECTED_DEVICE` to `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION`.
    - Assert bitwise AND with `CONNECTED_DEVICE` is 0.

### Step 4: Manifest & Architecture Contract Test (`MergedManifestFgsAuditTest.kt`)
- Author a dedicated test `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/MergedManifestFgsAuditTest.kt` to:
  - Verify `AndroidManifest.xml` does not declare `FOREGROUND_SERVICE_CONNECTED_DEVICE`.
  - Verify `TrackerService` and `BANALService` have `android:foregroundServiceType="location|health"`.

### Step 5: Test Execution & Clean-Room Suite
- Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest \
    --tests com.atrainingtracker.trainingtracker.tracker.TrackerServiceFGSTypeTest \
    --tests com.atrainingtracker.trainingtracker.tracker.TrackerServiceFinalizationTest \
    --tests com.atrainingtracker.trainingtracker.tracker.MergedManifestFgsAuditTest
  ```
- Run manifest processing task to verify merged manifest validity:
  ```bash
  ./gradlew processDebugMainManifest
  ```
- Run clean-room test suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```
