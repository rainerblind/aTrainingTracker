# Stage 2: Requirement & Test Specification - ATT-7762: Polar H10 Internal RR Recording & GATT Arbitration

**Ticket**: [ATT-7762](https://rainerblind.atlassian.net/browse/ATT-7762)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.7`  
**Requirement Mapping**: `REQ-SET-104` (*Polar H10 Internal RR Recording & GATT Arbitration*)  
**Test Spec ID**: `TST-SET-106`  
**Branch**: `feature/ATT-7762`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-15  

---

## 1. Requirement Specification (REQ-SET-104)

### 1.1 Problem Statement & Rationale
Endurance athletes using a Polar H10 chest strap lose real-time BLE (0x180D) HR/RR telemetry during swimming or mid-workout dropouts out of phone range. While the Polar H10 provides internal flash memory for 1 RR session via Polar PFTP GATT protocol, starting internal recording requires clock synchronization (`setLocalTime`), stale exercise cleanup, and GATT arbitration with standard 0x180D streaming, while preserving `minSdkVersion 26` compatibility on Android 8–12.

### 1.2 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit, Integration

### 1.3 Functional & Architectural Requirements
1. `app/build.gradle` and `AndroidManifest.xml` SHALL integrate `com.polar.sdk:polar-ble-sdk` using `<uses-sdk tools:overrideLibrary="com.polar.sdk"/>`, gating all SDK calls behind `Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU` (API 33).
2. `DevicesDatabaseManager` schema SHALL upgrade from `DB_VERSION = 12` to `13` adding column `polar_h10_offline_recording INTEGER DEFAULT 0` with symmetric `onCreate()` and `onUpgrade()` parity.
3. `EditDeviceDialog` and `EditDeviceViewModel` SHALL expose a "Polar H10 Backup Recording" switch strictly for Polar H10 HRM devices, localized across all 9 supported locales.
4. `PolarH10OfflineController` SHALL connect via Polar SDK upon workout start, sync device clock, clear stale exercise slots, and trigger `startRecording(SampleType.RR)`. Standard 0x180D BLE streaming SHALL be arbitrated without GATT collision.
5. *Fringe-Flap Invariant*: The internal session SHALL be armed once at workout start and SHALL NOT restart on mid-session disconnect/reconnect.
6. GPLv3 §7 linking exception & attribution for Polar BLE SDK SHALL be maintained in legal notices.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Android 8–12 Compatibility)**: *Given* `SDK_INT < 33`, *When* app connects to a Polar H10, *Then* app operates without runtime exception, falling back to standard 0x180D BLE streaming.
* **AC-2 (Workout Arming)**: *Given* an Android 13+ device and paired Polar H10 with backup recording enabled, *When* workout starts, *Then* `PolarH10OfflineController` syncs clock and arms internal recording asynchronously without blocking `TrackerService`.
* **AC-3 (Mid-Session Reconnect)**: *Given* an armed workout session, *When* BLE disconnects and reconnects mid-workout, *Then* 0x180D streaming resumes and the ongoing internal recording continues without re-arming.
* **AC-4 (DB Migration)**: *Given* DB version 12, *When* upgraded to 13, *Then* `polar_h10_offline_recording` is added with default 0.

### 1.5 System Invariants
- Zero regression in standard 0x180D HR/RR telemetry streaming in `BTLEHeartRateDevice`.
- Non-blocking workout startup in `TrackerService`.
- 100% database migration parity between fresh install and upgraded schemas.

---

## 2. Test Specification (TST-SET-106)

* **TST-SET-106.1 (Arming & Fringe-Flap Lifecycle)**: Unit test `PolarH10OfflineControllerTest` asserting clock sync, stale exercise removal, and single arming per workout.
* **TST-SET-106.2 (GATT Arbitration & Hand-off)**: Unit test asserting clean hand-off between `MyBTLEDevice` and Polar SDK without concurrent GATT collisions.
* **TST-SET-106.3 (Android 8–12 API Isolation)**: Contract test verifying `@RequiresApi(33)` runtime guards prevent class resolution failures on `SDK_INT < 33`.
* **TST-SET-106.4 (DB v13 Migration Parity)**: Unit test `DevicesDbHelperMigrationTest` verifying `onCreate` vs `onUpgrade` schema parity.
* **TST-SET-106.5 (UI Toggle & Persistence)**: ViewModel test verifying toggle visibility solely for Polar H10 and state persistence.
* **TST-SET-106.6 (9-Locale Translation Parity)**: `TranslationParityTest` verifying all strings across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Tier 1 Verification Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-SET-106.1]` | Unit | `PolarH10OfflineController.armRecording` | `REQ-SET-104.4` | Specified |
| `[TST-SET-106.2]` | Integration | `PolarH10OfflineController` GATT arbitration | `REQ-SET-104.4` | Specified |
| `[TST-SET-106.3]` | Contract | `PolarH10OfflineController` API 33 isolation | `REQ-SET-104.1` | Specified |
| `[TST-SET-106.4]` | Migration | `DevicesDbHelper.onUpgrade` (v12 -> v13) | `REQ-SET-104.2` | Specified |
| `[TST-SET-106.5]` | Unit/UI | `EditDeviceViewModel.onPolarH10BackupToggled` | `REQ-SET-104.3` | Specified |
| `[TST-SET-106.6]` | Localization | `TranslationParityTest` | `REQ-SET-104.3` | Specified |
