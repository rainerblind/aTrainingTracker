# Stage 3: Implementation Plan - ATT-5492: Remove obsolete Network Location sensor (SPEED_AND_LOCATION_NETWORK / SpeedAndLocationDevice_Network) and migrate Devices.db

**Ticket**: [ATT-5492](https://rainerblind.atlassian.net/browse/ATT-5492)  
**Parent Epic**: None | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5492`  
**Requirement Mapping**: `REQ-SEN-027`, `REQ-TRK-024` | **Test Mapping**: `TST-SEN-027`, `TST-TRK-016`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
`SpeedAndLocationDevice_Network` (`DeviceType.SPEED_AND_LOCATION_NETWORK`, "Netzwerk") was introduced as an early experimental fallback for raw `LocationManager.NETWORK_PROVIDER` coarse positions. For athletic tracking, network triangulation (50–1,500 m accuracy, unreliable speed/altitude) is obsolete and unhelpful, especially with `SPEED_AND_LOCATION_GOOGLE_FUSED` active. Keeping it clutters the UI, retains dead code paths in `DeviceManager` and `MySensorManager`, and risks consuming Free-tier sensor slots. This ticket completely excises the network sensor and migrates `Devices.db` to schema version 10.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SEN-027` (*Obsolete Network Location Excision & Devices.db v10 Migration*), `REQ-TRK-024` (*Smartphone Location Provider Priority*)
* **Test Mapping**: `TST-SEN-027` (*Network Location Excision and Devices.db Migration Verification*), `TST-TRK-016` (*Location Provider Priority and De-Googled Fallback Tests*)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly.
2. **Location Hierarchy Invariance**: Dedicated physical sensors (`RUN_SPEED`, `BIKE_SPEED`, `BIKE_POWER`) and barometric pressure (`ALTITUDE_FROM_PRESSURE`) retain top precedence. `SPEED_AND_LOCATION_GOOGLE_FUSED > SPEED_AND_LOCATION_GPS` is preserved.
3. **De-Googled Fallback Invariance**: Fresh installs without Google Play Services continue pairing `SPEED_AND_LOCATION_GPS` by default.
4. **Idempotent DB Migration**: Upgrades from `Devices.db` $\le 9$ to 10 delete `SPEED_AND_LOCATION_NETWORK` rows without touching any other sensors or user pairings.
5. **DatabaseSchemaRegistry Synchronization**: Registry automatically reflects `DB_VERSION = 10` via constant reference (Rule 62).
6. **Thread Safety & Dispatcher Affinity**: Database operations remain isolated to designated background execution paths.
7. **Strict Code Lock**: No production code in `app/src/` is modified until Gate 3 review passes.

---

## 4. Proposed Architectural Changes

### Component 1: Sensor Hardware Driver & Enum Excision
* Delete `SpeedAndLocationDevice_Network.java`.
* In `DeviceType.java`, remove `SPEED_AND_LOCATION_NETWORK` enum constant and from `getDeviceTypeList(...)`.
* In `MySensorManager.java`, excise `SPEED_AND_LOCATION_NETWORK` from `SPEED_PRIORITY` (6 items), `ALTITUDE_PRIORITY` (4 items), and `LOCATION_PRIORITY` (2 items).
* In `DeviceManager.java`, remove `mSpeedAndLocationDevice_Network` field, initialization, pairing receiver handling, and query accessors.
* In `TrainingApplication.java`, remove `networkDeviceId` check from `trackLocation()`.
* In `UIHelper.java`, remove `SPEED_AND_LOCATION_NETWORK` cases.
* In `DeviceData.kt`, remove `SPEED_AND_LOCATION_NETWORK` icon mapping.
* In all 9 `strings_devices.xml` files, excise `devices_location_Network`.

### Component 2: Devices Database Version 10 Migration
* In `DevicesDatabaseManager.java`:
  * Increment `DevicesDbHelper.DB_VERSION` to 10.
  * In `onCreate()`, add `case 10:` alongside `case 8: case 9:`.
  * In `onUpgrade()`, add `if (oldVersion <= 9)` block executing `migrateRemoveSpeedAndLocationNetwork(db)` (`DELETE FROM Devices4 WHERE deviceType = 'SPEED_AND_LOCATION_NETWORK'`).
  * In `addLocationDevicesToDb()`, omit inserting `SPEED_AND_LOCATION_NETWORK`.
  * Delete `getSpeedAndLocationNetworkDeviceId()`.

### UI Consistency (Rule 23)
No UI changes. (Removal of obsolete sensor driver; UI renders dynamically based on `Devices.db` rows).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Excising Driver & Dead Enum References
* Files: `SpeedAndLocationDevice_Network.java` (delete), `DeviceType.java`, `MySensorManager.java`, `DeviceManager.java`, `TrainingApplication.java`, `UIHelper.java`, `DeviceData.kt`, `strings_devices.xml` (all 9 locales).

### Step 2: Database Migration v10 in DevicesDatabaseManager
* Files: `DevicesDatabaseManager.java` (bump `DB_VERSION = 10`, `case 10:` in `onCreate`, `migrateRemoveSpeedAndLocationNetwork` in `onUpgrade(oldVersion <= 9)`, omit insertion in `addLocationDevicesToDb`, remove `getSpeedAndLocationNetworkDeviceId()`).

### Step 3: Test Suite Updates & Migration Contract
* Files: `DeviceTypeEnumOrderTest.kt`, `DevicesDatabaseManagerOrderingTest.kt` (update fresh install assertions to 2 location sensors and add `testDevicesDbUpgrade_v9ToV10_removesSpeedAndLocationNetworkRow()`), `MySensorManagerPriorityTest.kt`, `SpeedAndLocationDeviceResilienceTest.kt`, `FreshInstallLocationInitTest.kt`, `DeviceManagerLocationInitTest.kt`, `SensorSourceDialogPriorityTest.kt`, `DeviceListViewModelQuotaTest.kt`, `SensorQuotaReconciliationManagerTest.kt`.

### Step 4: Verification Commands
* Targeted unit tests: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.*" --tests "com.atrainingtracker.trainingtracker.monetization.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* Localization parity: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.TranslationParityTest"`
* Requirement governance: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests and translation parity tests pass with 100% success; clean-room full test suite in Stage 5.
* **Rollback**: Isolated branch `feature/ATT-5492` allows zero-cost discard via `git checkout develop && git branch -D feature/ATT-5492`.
