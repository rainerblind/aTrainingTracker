# Stage 1: Problem Domain & Root Cause Analysis - ATT-3567: Remove Unnecessary FOREGROUND_SERVICE_CONNECTED_DEVICE Permission from Manifest and TrackerService

**Ticket**: [ATT-3567](https://atrainingtracker.atlassian.net/browse/ATT-3567)  
**Parent Epic**: [ATT-431](https://atrainingtracker.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Target Release**: `V4.9.40`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Statement & Motivation

In ticket `ATT-2773` (Sprint `2026-41.4`), as part of the Android 14 (API 34) platform migration, `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE` and `foregroundServiceType="...|connectedDevice"` were introduced into `AndroidManifest.xml` and dynamically evaluated in `TrackerService.java`.

However, in-depth architectural and Google Play policy review has revealed significant drawbacks and unnecessary compliance overhead:

1. **Google Play Console Foreground Service Declaration Policy Review**:
   - Declaring `FOREGROUND_SERVICE_CONNECTED_DEVICE` in `AndroidManifest.xml` subjects the app to an additional mandatory Google Play Console policy review declaration (*"Connected device — Continuous data transfer to an external device / Automotive key / Other"*).
   - This category is intended for apps executing bulk data transfers (e.g., streaming media, firmware updates) or managing smart hardware (automotive digital keys). It is fundamentally mismatched with an athletic workout tracking app that passively receives standard GATT telemetry from heart rate straps and cycling power meters.
2. **Redundancy with Approved Core FGS Types (`location` and `health`)**:
   - `aTrainingTracker` operates its primary workout recording engine under two canonical, fully approved foreground service types:
     - `FOREGROUND_SERVICE_LOCATION`: Continual GPS trackpoint acquisition and velocity calculations.
     - `FOREGROUND_SERVICE_HEALTH`: Heart rate, cadence, cycling power, and exercise metric telemetry.
   - These two types are already declared and approved in the Google Play Console for sports and fitness tracking.
3. **Process Lifecycle & BLE Peripheral Resilience**:
   - Android process management keeps `TrackerService` alive in the foreground based on `FOREGROUND_SERVICE_LOCATION` (and `FOREGROUND_SERVICE_HEALTH` when granted), backed by a CPU wake lock (`PARTIAL_WAKE_LOCK`).
   - Active Bluetooth LE GATT connections and incoming notification callbacks (e.g. Heart Rate Measurement, Cycling Power) operate seamlessly within the process without requiring the separate `connectedDevice` FGS type.
   - Device discovery and BLE peripheral scanning (`BTSearchForNewDevicesEngine`) run interactively while the athlete is actively engaged in the foreground UI (`ControlTrackingScreen` or `DeviceScanDialog`).

Retaining `FOREGROUND_SERVICE_CONNECTED_DEVICE` introduces continuous regulatory friction on the Google Play Store without providing any technical or operational benefit.

---

## 2. Chesterton's Fence Requirement Archaeology

In accordance with `REQ-PRO-022`, the history and original rationale for `FOREGROUND_SERVICE_CONNECTED_DEVICE` must be audited before modification:

### 2.1 Original Requirement IDs & Targets
- `REQ-CON-019`: *Robust Bluetooth LE Sensor Discovery & Direct LE Transport Connection Architecture* (ATT-2773, Sprint 2026-41.4). Item 5 mandated declaring `FOREGROUND_SERVICE_CONNECTED_DEVICE` and `connectedDevice` on `BANALService` and `TrackerService`.
- `REQ-TRK-015`: *Permission-Gated Foreground Service Types in TrackerService* (ATT-2972, Sprint 2026-41.7). Item 1 required including `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` when Bluetooth permissions were granted.

### 2.2 Historical Origin & Commit Trace
- **Commit `67451a9` & ATT-2773**: Introduced `FOREGROUND_SERVICE_CONNECTED_DEVICE` as part of the initial Android 14 compliance migration, adhering broadly to early platform guidance recommending `connectedDevice` for any peripheral communication.
- **Commit `4466c40` & ATT-2972**: Dynamically gated FGS types in `TrackerService.determineForegroundServiceType()` to avoid fatal `SecurityException` crashes when runtime permissions were absent.

### 2.3 Root Reason for Existing Formulation
The initial Android 14 migration defensively adopted all potentially relevant FGS types. At the time, it was assumed that BLE GATT connections might be terminated by Android 14's strict background execution limits unless `connectedDevice` was explicitly declared. Subsequent platform verification confirms that `FOREGROUND_SERVICE_LOCATION` and `FOREGROUND_SERVICE_HEALTH` provide full process execution immunity for athletic tracking applications.

### 2.4 Preservation of Core Invariants
- **Uninterrupted Workout Recording**: `TrackerService` continues to be promoted to the foreground using `FOREGROUND_SERVICE_TYPE_LOCATION` and `FOREGROUND_SERVICE_TYPE_HEALTH`.
- **Zero SecurityExceptions**: Runtime permissions continue to be verified, preventing platform crashes on startup.
- **BLE Telemetry Integrity**: Real-time sensor reception, GATT queue processing, and workout metric calculation remain completely unaffected.
- **100% Clean-Room Unit Test Pass Rate**: Full unit test suite remains compliant.

---

## 3. Scope Bounding & Proposed Architecture

### 3.1 In-Scope Objectives
1. **Manifest Permissions & Service Attributes (`app/src/main/AndroidManifest.xml`)**:
   - Remove `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />`.
   - Update `TrackerService` attribute `android:foregroundServiceType` from `"location|health|connectedDevice"` to `"location|health"`.
   - Update `BANALService` attribute `android:foregroundServiceType` from `"location|health|connectedDevice"` to `"location|health"`.
2. **Foreground Service Bitmask Derivation (`TrackerService.java`)**:
   - In `TrackerService.determineForegroundServiceType()`:
     - Remove `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`.
     - Remove the `if (hasBluetoothPermission())` branch for FGS type assignment.
     - Remove the now unused `hasBluetoothPermission()` helper method.
3. **Unit Test Updates**:
   - `TrackerServiceFGSTypeTest.kt`:
     - Update tests to assert that `determineForegroundServiceType()` returns `FOREGROUND_SERVICE_TYPE_LOCATION` when health is not granted, and `LOCATION | HEALTH` when health is granted.
     - Assert that `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` is never present in the returned bitmask.
   - `TrackerServiceFinalizationTest.kt`:
     - Update `testDetermineForegroundServiceType_WithoutHealthPermission_OmitsHealthType()` to expect `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION`.
4. **Living Documentation & Governance**:
   - Amend `REQ-CON-019` and `REQ-TRK-015` in `docs/requirements.md` to formally deprecate and eliminate `connectedDevice`.
   - Update `TST-CON-011` and `TST-TRK-007` in `docs/tests.md`.

### 3.2 Out-of-Scope Items
- Modifying Bluetooth scanning logic (`BTSearchForNewDevicesEngine.java`), GATT connection procedures (`MyBTLEDevice.java`), or sensor data parsing (`BANALService.java`).
- Altering location tracking logic, GPS device registration (`REQ-TRK-017`), or wake lock acquisition (`REQ-TRK-003`).
- Modifying UI components or settings screens.

---

## 4. Verification Strategy

1. **Targeted Unit Tests**:
   - Run `TrackerServiceFGSTypeTest` and `TrackerServiceFinalizationTest` verifying bitmask derivation across Android versions and permission states.
2. **Merged Manifest Verification**:
   - Execute `./gradlew processDebugMainManifest` and inspect the generated merged manifest (`app/build/intermediates/merged_manifests/debug/processDebugMainManifest/AndroidManifest.xml`) to assert complete absence of `FOREGROUND_SERVICE_CONNECTED_DEVICE` and `connectedDevice`.
3. **Clean-Room Regression Suite**:
   - Execute full suite `./gradlew testDebugUnitTest` ensuring 100% pass rate.
4. **Governance Verification**:
   - Run `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-42.5` ensuring requirement archaeology rules are satisfied.
