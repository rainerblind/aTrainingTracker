# Stage 1 Analysis: ATT-6738 - Fix hallucinated sensors, FGS types, and omitted cloud services in system_context.md

**Ticket**: [ATT-6738](https://rainerblind.atlassian.net/browse/ATT-6738)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6738`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

During Sprint 2026-46.1 review of ATT-6158 (`docs/system/system_context.md`), an automated O(1) source-grounding audit revealed multiple discrepancies between the system context specification and authentic production source code:
1. **Hallucinated On-Board Sensors**: Claims use of `Sensor.TYPE_STEP_COUNTER` and `Sensor.TYPE_ACCELEROMETER`, neither of which exists in `app/src/main/` (auto-pause is GPS/speed-based). Conversely, `Sensor.TYPE_PROXIMITY` (used in `BatterySaverController.kt` for pocket detection) is omitted alongside the real barometer sensor `Sensor.TYPE_PRESSURE` (`AltitudeFromPressureDevice.java`).
2. **Foreground Service Types & Android Version Label**: Section 3.2 lists `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`, whereas `AndroidManifest.xml` and `TrackerService.java` declare and execute only `FOREGROUND_SERVICE_TYPE_LOCATION` and `FOREGROUND_SERVICE_TYPE_HEALTH`. Additionally, `targetSdkVersion: 37` is mislabeled as "Android 16 Vanilla Ice Cream" (API 35 = Android 15 Vanilla Ice Cream, API 36 = Android 16 Baklava, API 37 = Android 17).
3. **Stale Filenames & Location API**: References `StravaUploader.java` and `GoogleDriveUploader.java` instead of `.kt`, and cites `FusedLocationProviderClient` rather than `GoogleApiClient` / `LocationServices.FusedLocationApi` in `SpeedAndLocationDevice_GoogleFused.java`.
4. **Omitted External Services & Capabilities**: Omits Open-Meteo Elevation API (`ElevationService.kt`), Firebase Crashlytics & Analytics (`firebase-bom:34.18.0`), Google Play Auth & In-App Review (`play-services-auth:21.3.0`, `play:review-ktx:2.0.2`), BLE Device Information `0x180A` and Garmin Varia V1 / Light Control UUIDs (`BluetoothConstants.java`), and Health Connect incoming HRV/RHR read capabilities (`HealthConnectHrvImporter.kt`).
5. **Contract Test Gap**: `SystemContextContractTest.kt` performs static substring matches instead of dynamically parsing referenced source files and constants.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Live Grounding Verification**:
  * *Sensor Grounding*: Grepped `app/src/main/` for `Sensor.TYPE_*`. Confirmed exactly two hardware sensor types in production: `Sensor.TYPE_PRESSURE` (`AltitudeFromPressureDevice.java:98`, `HavePressureSensor.java:27`) and `Sensor.TYPE_PROXIMITY` (`BatterySaverController.kt:62, 261`). `TYPE_STEP_COUNTER` and `TYPE_ACCELEROMETER` have 0 references.
  * *FGS Grounding*: Inspected `AndroidManifest.xml` lines 316 and 322 (`android:foregroundServiceType="location|health"`) and `TrackerService.java:900-906` (`determineForegroundServiceType` combining `FOREGROUND_SERVICE_TYPE_LOCATION` and `FOREGROUND_SERVICE_TYPE_HEALTH`). `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` is absent.
  * *Location Grounding*: Verified `SpeedAndLocationDevice_GoogleFused.java:47, 62, 116` uses `GoogleApiClient` and `LocationServices.FusedLocationApi.requestLocationUpdates()`, not `FusedLocationProviderClient`.
  * *External Cloud & Peripherals*: Verified `ElevationService.kt` calls `https://api.open-meteo.com/v1/elevation`, `app/build.gradle` includes `firebase-bom:34.18.0`, `play-services-auth:21.3.0`, and `play:review-ktx:2.0.2`. Verified `BluetoothConstants.java` defines `SERVICE_DEVICE_INFORMATION` (`0x180A`), `CHARACTERISTIC_RADAR_V1`, and `CHARACTERISTIC_RADAR_LIGHT_CONTROL`. Verified `AndroidManifest.xml` and `HealthConnectHrvImporter.kt` require and exercise `READ_HEART_RATE_VARIABILITY` and `READ_RESTING_HEART_RATE`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Update Mermaid diagram and Section 3.1.3 in `docs/system/system_context.md` to reflect `Sensor.TYPE_PRESSURE` and `Sensor.TYPE_PROXIMITY`.
  * Correct Section 3.2 FGS types to `FOREGROUND_SERVICE_TYPE_LOCATION` and `FOREGROUND_SERVICE_TYPE_HEALTH`.
  * Correct `targetSdkVersion: 37` label to Android 17.
  * Update `StravaUploader.kt` and `GoogleDriveUploader.kt` filenames.
  * Ground fused location API with `SpeedAndLocationDevice_GoogleFused.java` (`GoogleApiClient` / `LocationServices.FusedLocationApi`).
  * Add Open-Meteo Elevation API, Firebase Crashlytics/Analytics, Google Play Auth/In-App Review, BLE `0x180A` & Varia V1/Light Control UUIDs, and Health Connect HRV/RHR read capabilities.
  * Harden `SystemContextContractTest.kt` to dynamically verify on-disk existence of backticked code files and symbol parity.
  * Synchronize living documentation in `docs/requirements/req_process_stability.md` and `docs/requirements.md`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Zero modifications to Android production runtime code in `app/src/main/`.
  * No refactoring of `SpeedAndLocationDevice_GoogleFused.java` to modern `FusedLocationProviderClient` (tracked as separate modernization backlog item).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-PRO-053` / `TST-PRO-053` (*System Context and External Interfaces Specification*).
* **Historical Origin & Commit Trace**: Independent ASPICE pre-assessment finding S-01 (2026-11-08) and ticket ATT-6158 (commit `4ced957a`).
* **Root Reason for Existing Formulation**: Initial drafting established the 5-domain structure but incorporated speculative sensor types and stale Java filenames from older app iterations.
* **Preservation of Core Invariants**: The 5-domain ASPICE SYS.2/SYS.3 architecture remains intact; updating specifications to 100% ground against authentic codebase elements strengthens compliance without breaking system invariants.

---

## 5. Architectural Strategy & High-Level Solution

1. **System Context Markdown (`docs/system/system_context.md`)**:
   - Update Mermaid graph: replace step counter/accelerometer with Barometer / Proximity; update FGS label; add Open-Meteo, Firebase, Google Play, BLE device info/light control, and Health Connect HRV/RHR annotations.
   - Update sections 3.1, 3.2, 3.3, 3.4 with precise grounded descriptions and file paths.
2. **System Context Contract Test (`SystemContextContractTest.kt`)**:
   - Add dynamic regex-based verification asserting that every backticked `.kt`, `.java`, `.xml` file exists in the repository.
   - Assert all cited `Sensor.TYPE_*` constants match authentic sensor usages (`TYPE_PRESSURE`, `TYPE_PROXIMITY`).
   - Assert all cited `FOREGROUND_SERVICE_TYPE_*` constants match manifest declarations and `TrackerService.java`.
   - Assert all cited BLE UUID constants match `BluetoothConstants`.
3. **Living Documentation Sync**:
   - Update `docs/requirements/req_process_stability.md`, `docs/requirements.md`, `docs/tests/tst_process_stability.md`, and `docs/tests.md` to reflect the updated specification contracts.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero production runtime bytecode modifications (`app/src/main/` untouched).
  2. 100% unit and contract test pass rate.
  3. Zero source-text reading audit violations per ATT-6186.
* **Risk Rating**: **LOW** (specification accuracy, living doc synchronization, and test hardening).
