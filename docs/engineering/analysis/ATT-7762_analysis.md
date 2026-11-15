# Stage 1 Analysis: ATT-7762 - Polar H10 Internal RR Recording & GATT Arbitration

**Ticket**: [ATT-7762](https://rainerblind.atlassian.net/browse/ATT-7762)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.7`  
**Branch**: `feature/ATT-7762`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-15  

---

## 1. Problem Statement & Motivation
Endurance athletes using a Polar H10 lose live BLE (0x180D) HR/RR telemetry during swimming or mid-workout dropouts when out of phone range. While the H10 possesses internal flash memory for 1 RR/HR session via Polar PFTP GATT, it requires explicit clock synchronization (`setLocalTime`), stale exercise slot clearance, and `startRecording(SampleType.RR)`. This slice integrates the pure-Coroutines Polar BLE SDK while preserving `minSdkVersion 26` for legacy devices, adds a per-device toggle in `EditDeviceDialog`, and implements GATT-safe recording lifecycle primitives.

---

## 2. Root Cause Analysis & Independent Peer Review & Verification
* **Forensic Investigation**:
  * `app/build.gradle`: `minSdkVersion 26` is globally enforced; Polar BLE SDK requires API 33+. Merging requires `<uses-sdk tools:overrideLibrary="com.polar.sdk"/>` in `AndroidManifest.xml` and `@RequiresApi(33)` guards.
  * `MyBTLEDevice.java` & `BTLEHeartRateDevice.java`: Active standard BLE GATT connection (0x180D) will collide if Polar BLE SDK attempts simultaneous GATT operations on the same MAC. `PolarH10OfflineController` must arbitrate access or pause `MyBTLEDevice` during PFTP operations.
  * `DevicesDatabaseManager.java`: Currently at `DB_VERSION = 12`. Needs persistent per-device storage for Polar H10 backup recording preference (`DB_VERSION = 13` or dedicated column/table).
  * `TrackerService.java`: Workout start sequence in `onStartCommand` must trigger H10 arming asynchronously without blocking workout startup if the strap is temporarily unreachable.
* **Independent Peer Review & Verification**:
  * Verified PO comment approving Polar BLE SDK attribution & GPLv3 §7 linking notice.
  * Audited `settings.gradle`: JitPack repository is already present and active.
  * Verified fringe-flap invariant: internal recording must arm once at workout start and never restart on mid-session BLE disconnect/reconnect.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Integrate `polar-ble-sdk` with `tools:overrideLibrary="com.polar.sdk"` and API 33 runtime isolation.
  * Add per-device toggle in `EditDeviceDialog` and persist in `DevicesDatabaseManager` across 9 locales.
  * Implement `PolarH10OfflineController` for GATT handoff, clock sync, stale exercise cleanup, and arming on workout start.
* **Out-of-Scope Non-Goals**:
  * Workout-stop backfill/import ingestion into `WorkoutSamples.db` (deferred to subsequent backfill slice).
  * Firmware updates or ECG/accelerometer Polar PMD streaming.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Origin**: Net-new requirement only (`REQ-SET-104`). No existing requirements modified.
* **Preservation of Core Invariants**: `minSdkVersion 26` compatibility, standard 0x180D streaming in `BTLEHeartRateDevice`, and database backwards compatibility are 100% preserved.

---

## 5. Change Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-SET-104` (in `req_settings_profiles.md`), `TST-SET-106` (in `tst_settings_profiles.md`).
* **Affected Architecture Elements**: `app/build.gradle`, `AndroidManifest.xml`, `DevicesDatabaseManager.java`, `DeviceUiData.kt`, `EditDeviceDialog.kt`, `EditDeviceViewModel.kt`, `PolarH10OfflineController.kt`, `TrackerService.java`.
* **Verification Impact & Test Surface**: Unit tests for controller lifecycle, Android 8–12 compatibility contract tests, `DevicesDbHelper` migration test, 9-locale `TranslationParityTest`.
* **Risk Assessment & Invariants**: **MEDIUM** (Hardware GATT arbitration, third-party SDK minSdk override). Core invariants: zero legacy device regression, non-blocking workout start, mid-workout disconnect resilience.
