# Stage 3: Implementation Plan - ATT-7762: Polar H10 Internal RR Recording & GATT Arbitration

**Ticket**: [ATT-7762](https://rainerblind.atlassian.net/browse/ATT-7762)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7762`  
**Requirement Mapping**: `REQ-SET-104` | **Test Mapping**: `TST-SET-106`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  
**Risk Classification**: `HIGH-RISK` (Touches `TrackerService`, `MyBTLEDevice`, SQLite DB migration, and native BLE SDK)  

---

## 1. Problem Description & Background
Endurance athletes lose live BLE (0x180D) HR/RR telemetry during swimming or radio dropouts. This plan integrates authentic `com.github.polarofficial:polar-ble-sdk:8.6.0` (pure-Coroutines/Flow, zero RxJava3) on JitPack while keeping `minSdkVersion 26` untouched via manifest override (`com.polar.sdk, com.polar.androidcommunications`), upgrades `Devices.db` to version 13 with Default-ON (`DEFAULT 1`) semantics and `onCreate`/`onUpgrade` parity, exposes an M3 toggle in `EditDeviceDialog` across 9 locales, and coordinates non-blocking GATT arbitration in `PolarH10OfflineController`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SET-104` (`REQ-SET-104.1` SDK JitPack/manifest, `.2` DB v13 `DEFAULT 1`, `.3` UI toggle, `.4` arming/GATT, `.5` legal notice)
* **Test Mapping**: `TST-SET-106` (`TST-SET-106.1` arming, `.2` GATT handoff, `.3` API 33 guard, `.4` DB v13 parity, `.5` UI, `.6` 9 locales)

---

## 3. System Invariants & Preserved Behavior
1. **Android 8–12 Compatibility**: Zero runtime failure or `NoClassDefFoundError` on API 26–32; global `minSdkVersion 26` intact.
2. **0x180D Live Streaming Intact**: Standard BLE HR/RR streaming in `BTLEHeartRateDevice` remains completely unregressed.
3. **Mid-Session Fringe-Flap Invariant**: Recording armed strictly once at workout start; mid-workout BLE drops never restart/truncate.
4. **Database Parity & Default-ON**: `polarH10OfflineRecording int DEFAULT 1` ensures default-ON for Polar H10 on Android 13+; 100% schema parity between `onCreate()` and `onUpgrade()`.
5. **Non-Blocking Workout Start**: Failing/unreachable strap never delays or aborts `TrackerService` startup.

---

## 4. Proposed Architectural Changes
* **SDK & Manifest**: Add `com.github.polarofficial:polar-ble-sdk:8.6.0` to `app/build.gradle` and `<uses-sdk tools:overrideLibrary="com.polar.sdk, com.polar.androidcommunications"/>` in `AndroidManifest.xml`.
* **Database & Domain**: Upgrade `DevicesDbHelper.DB_VERSION = 13` adding column `polarH10OfflineRecording int DEFAULT 1`; `DatabaseSchemaRegistry` reflects v13; mapped to `DeviceUiData.polarH10OfflineRecording`. Non-H10 devices ignore column.
* **UI Consistency (`docs/design_guidelines.md` §1 & §5)**:
  * Reused component: Standard `Switch` row matching `AttFusedToggleRow` in `EditDeviceDialog.kt`.
  * Theme tokens: `MaterialTheme.colorScheme.onSurface`, `MaterialTheme.typography.bodyMedium`.
  * API 33 gating: On API < 33, row displays disabled switch with explanatory hint (`device_polar_h10_backup_recording_requires_android_13`).
  * 9-locale parity across all supported language folders in `strings_devices.xml`.
* **Controller & Service**: Implement `PolarH10OfflineController` (`@RequiresApi(33)`) coordinating GATT handoff with `MyBTLEDevice`, time sync (`setLocalTime`), stale exercise removal, and `SampleType.RR` arming; hook asynchronously into `TrackerService.onStartCommand()`. Update SWE.2 documentation in `docs/architecture.md`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: SDK Dependency, Manifest Override & Legal Attribution
* Files: `app/build.gradle`, `app/src/main/AndroidManifest.xml`, `docs/legal/OPEN_SOURCE_LICENSES.md`.
* Changes: Integrate `com.github.polarofficial:polar-ble-sdk:8.6.0`, declare `tools:overrideLibrary="com.polar.sdk, com.polar.androidcommunications"`, document GPLv3 §7 linking exception & attribution.
### Step 2: Database Schema Upgrade (DB_VERSION 12 -> 13) & Parity Contract Test
* Files: `DevicesDatabaseManager.java`, `DevicesDatabaseManagerMigrationV13Test.kt`, `DatabaseSchemaRegistry.kt`.
* Changes: Add `POLAR_H10_OFFLINE_RECORDING = "polarH10OfflineRecording" int DEFAULT 1`, update `CREATE_DEVICE_DB_8` and `onUpgrade(12, 13)`, verify `DatabaseSchemaRegistry` mapping and `onCreate`/`onUpgrade` parity.
### Step 3: Domain Model & Repository Integration
* Files: `DeviceData.kt`, `DeviceDataRepository.kt`, `EditDeviceViewModel.kt`.
* Changes: Add `polarH10OfflineRecording` to `DeviceUiData`, handle cursor extraction and update mutations.
### Step 4: UI Toggle & 9-Locale Localization
* Files: `EditDeviceDialog.kt`, `app/src/main/res/values*/strings_devices.xml` (all 9 locales).
* Changes: Render toggle for Polar H10 HRM, wire to ViewModel, assert parity via `TranslationParityTest`.
### Step 5: PolarH10OfflineController & GATT Arbitration
* Files: `PolarH10OfflineController.kt`, `MyBTLEDevice.java`.
* Changes: Implement API 33-isolated controller, GATT pause/resume handoff, clock sync, and single-shot RR arming.
### Step 6: TrackerService Integration & Continuous SWE.2 Maintenance
* Files: `TrackerService.java`, `docs/architecture.md`.
* Changes: Trigger non-blocking arming in `onStartCommand()`; update `docs/architecture.md` SWE.2 component models.
### Step 7: Tier 1 Verification
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted unit tests (`TST-SET-106.1`–`TST-SET-106.6`), `verify_requirement_governance.py`, and clean-room suite in Stage 5.
* **Rollback**: Atomic feature branch isolation permits clean revert with zero branch contamination.
