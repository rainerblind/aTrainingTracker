# Stage 2: Requirement & Test Specification - ATT-5492: Obsolete Network Location Excision & Devices.db v10 Migration

**Ticket**: [ATT-5492](https://rainerblind.atlassian.net/browse/ATT-5492)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: None  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5492`  
**Requirement Mapping**: `REQ-SEN-027`, `REQ-TRK-024` | **Test Spec ID**: `TST-SEN-027`, `TST-TRK-016`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-SEN-027` & `REQ-TRK-024`)

### 1.1 Problem Statement & Rationale
Raw network triangulation (`SpeedAndLocationDevice_Network`, `DeviceType.SPEED_AND_LOCATION_NETWORK`) offers 50–1,500 m accuracy without reliable speed or altitude, which is unsuited for athletic tracking. Google Fused Location already incorporates network signals internally. Keeping obsolete network location clutters the UI, retains dead code in `DeviceManager` and `MySensorManager`, and risks consuming Free-tier sensor slots.

### 1.2 Functional & Architectural Requirements
1. **Sensor Driver & Enum Excision (`REQ-SEN-027.1`)**: Delete `SpeedAndLocationDevice_Network.java`. Excise `SPEED_AND_LOCATION_NETWORK` from `DeviceType.java`, `MySensorManager.java`, `DeviceManager.java`, `TrainingApplication.java`, `UIHelper.java`, and `DeviceData.kt`. Remove `devices_location_Network` across all 9 `strings_devices.xml` locales.
2. **Devices.db Version 10 Migration (`REQ-SEN-027.2`)**: Increment `DevicesDbHelper.DB_VERSION` to 10. Update `onCreate()` to declare `case 10:`. In `onUpgrade()`, add an idempotent step for `oldVersion <= 9` deleting rows with `DEVICE_TYPE = 'SPEED_AND_LOCATION_NETWORK'` from `Devices4`. Omit insertion on fresh installs (`addLocationDevicesToDb`) and delete `getSpeedAndLocationNetworkDeviceId()`.
3. **DatabaseSchemaRegistry Synchronization (`REQ-SEN-027.3`)**: Assert schema version 10 via direct `DevicesDbHelper.DB_VERSION` constant reference (Rule 62).
4. **Priority List & Living Spec Parity (`REQ-TRK-024.1`, `REQ-SEN-027.4`)**: In `MySensorManager`, prune priority arrays to lengths 6 (`SPEED`), 4 (`ALTITUDE`), and 2 (`LOCATION`), preserving `GOOGLE_FUSED > GPS`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (DeviceType Excision)**: *Given* `DeviceType.values()`, *When* inspected, *Then* `SPEED_AND_LOCATION_NETWORK` is absent and `GPS.ordinal < GOOGLE_FUSED.ordinal`.
* **AC-2 (Database Migration v10)**: *Given* a v9 `Devices.db` with a network location row, *When* `onUpgrade(db, 9, 10)` runs, *Then* the row is cleanly deleted without affecting GPS or Google Fused rows.
* **AC-3 (Fresh Installation)**: *Given* fresh DB creation, *When* querying smartphone sensors, *Then* only GPS and Google Fused location rows are inserted.
* **AC-4 (Priority Ranks)**: *Given* location metrics, *When* `getPriorityRank(type, device)` is queried, *Then* `GOOGLE_FUSED` returns 0 and `GPS` returns 1.

### 1.4 System Invariants
Dedicated wheel speed/power and barometric pressure precedence (`BIKE_SPEED > GOOGLE_FUSED > GPS`) strictly maintained; raw GPS fallback preserved; existing athlete pairings untouched; 100% test pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Amends `REQ-TRK-024` (`REQ-TRK-024.1`..`024.6`) targeting `MySensorManager.java`, `DeviceType.java`, `DeviceManager.java`, and `DevicesDatabaseManager.java`.
* `Historical Origin & Commit Trace`: Commit `fc28443c` (`ATT-3961`, Sprint `2026-43.1`), refined in `ATT-4375` (Sprint `2026-43.2`), `ATT-5491` (Sprint `2026-45.8`), and amended in `ATT-5492` (Sprint `2026-45.9`).
* `Root Reason for Existing Formulation`: In `ATT-3961` and `ATT-4375`, `SPEED_AND_LOCATION_NETWORK` was retained as an early experiment to inspect raw network triangulation, but proved obsolete and harmful for sports tracking.
* `Preservation of Core Invariants`: Hardware speed/power and barometric sensors retain top precedence; de-Googled devices fall back to raw GPS; existing user pairings are 100% untouched.

---

## 2. Test Specification (`TST-SEN-027`)

### Test Case 1: DeviceType Declaration & Excision Contract (`TST-SEN-027.1`)
* **Scope**: Unit test in `DeviceTypeEnumOrderTest.kt`.
* **Procedure**: Verify `SpeedAndLocationDevice_Network.java` deleted from disk; verify `DeviceType.values()` does not contain `SPEED_AND_LOCATION_NETWORK`; assert `SPEED_AND_LOCATION_GPS.ordinal < SPEED_AND_LOCATION_GOOGLE_FUSED.ordinal`.

### Test Case 2: Devices Database Version 10 Migration & Idempotent Cleanup (`TST-SEN-027.2`)
* **Scope**: Database migration test in `DevicesDatabaseManagerOrderingTest.kt`.
* **Procedure**: Insert mock v9 `SPEED_AND_LOCATION_NETWORK` row into SQLite database; execute `helper.onUpgrade(db, 9, 10)`; assert network row deleted. Verify fresh install `addLocationDevicesToDb` inserts only 2 location devices. Verify `DatabaseSchemaRegistry` validates `DB_VERSION == 10`.

### Test Case 3: Priority Hierarchy & Rank Excision (`TST-SEN-027.3`)
* **Scope**: Unit test in `MySensorManagerPriorityTest.kt`.
* **Procedure**: Assert `SPEED_PRIORITY` length is 6, `ALTITUDE_PRIORITY` is 4, and `LOCATION_PRIORITY` is 2. Verify `getPriorityRank` returns 0 for Google Fused and 1 for GPS.

### Test Case 4: 9-Language Clean Removal Audit (`TST-SEN-027.4`)
* **Scope**: Localization parity test in `TranslationParityTest.kt`.
* **Procedure**: Verify `TranslationParityTest` passes across all 9 locales with `devices_location_Network` cleanly excised from all `strings_devices.xml` files.

### Test Case 5: Clean-Room Governance & Regression (`TST-SEN-027.5`)
* **Command**: `python3 tools/verify_requirement_governance.py && ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.*" --tests "com.atrainingtracker.trainingtracker.monetization.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: Exit code 0, 100% test pass rate.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-SEN-027.1` | Excision of driver, DeviceType enum & references | `TST-SEN-027.1` | Unit Test (`DeviceTypeEnumOrderTest`) |
| `REQ-SEN-027.2` | Devices.db v10 migration & fresh install cleanup | `TST-SEN-027.2` | Database Test (`DevicesDatabaseManagerOrderingTest`) |
| `REQ-SEN-027.3` | DatabaseSchemaRegistry version 10 synchronization | `TST-SEN-027.2` | Contract Test (`DatabaseSchemaRegistryTest`) |
| `REQ-SEN-027.4` | Priority list pruning & 9-locale string excision | `TST-SEN-027.3`, `TST-SEN-027.4` | Unit & Localization Tests |
| `REQ-TRK-024.1` | Retention of GOOGLE_FUSED > GPS priority hierarchy | `TST-SEN-027.3`, `TST-TRK-016.1` | Unit Test (`MySensorManagerPriorityTest`) |
| `REQ-SEN-027` | Living governance and clean-room regression | `TST-SEN-027.5` | Governance & Gradle Suite |
