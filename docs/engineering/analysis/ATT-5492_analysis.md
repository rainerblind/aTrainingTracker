# Stage 1 Analysis: ATT-5492 - Remove obsolete Network Location sensor (SPEED_AND_LOCATION_NETWORK / SpeedAndLocationDevice_Network) and migrate Devices.db

**Ticket**: [ATT-5492](https://rainerblind.atlassian.net/browse/ATT-5492)  
**Parent Epic**: None | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5492`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
`SpeedAndLocationDevice_Network` (`DeviceType.SPEED_AND_LOCATION_NETWORK`, "Netzwerk") was added as an early experiment to inspect raw `LocationManager.NETWORK_PROVIDER` cell-tower and Wi-Fi triangulated positions.
For an athletic sports tracker, network triangulation (50–1,500 m accuracy, no reliable speed/altitude) offers no practical tracking value, especially since `SPEED_AND_LOCATION_GOOGLE_FUSED` already incorporates network/Wi-Fi signals internally. Keeping it clutters the Smartphone Sensors list, leaves dead code paths across `DeviceManager` and `MySensorManager`, and risks consuming one of the 2 Free-tier sensor slots if accidentally enabled.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: The network sensor is legacy dead code. It remains in `DeviceType`, `MySensorManager` priority arrays, `DeviceManager` lifecycle checks, and `Devices.db` fresh-install defaults (`DevicesDbHelper.addLocationDevicesToDb`).
* **Independent Peer Review & Verification**:
  * Audited `SpeedAndLocationDevice_Network.java`: only instantiated in `DeviceManager.java` (L391, L474, L575); safely deletable.
  * Audited `DeviceManager.java`: `mSpeedAndLocationDevice_Network` fields, initialization (L385–398, L468–480), pairing receiver (L569–588), and query accessors (L784–787, L883–886) can be cleanly removed.
  * Audited `MySensorManager.java`: `SPEED_AND_LOCATION_NETWORK` is the trailing element in `SPEED_PRIORITY` (L57), `ALTITUDE_PRIORITY` (L72), and `LOCATION_PRIORITY` (L78). Removing it leaves clean arrays.
  * Audited `DevicesDatabaseManager.java`: `getSpeedAndLocationNetworkDeviceId()` (L906–908) and insertion in `addLocationDevicesToDb` (L1366–1372) can be excised. `DevicesDbHelper.DB_VERSION = 9` (L1037) needs bumping to 10 with an idempotent deletion migration in `onUpgrade`.
  * Audited `DatabaseSchemaRegistry.kt`: Line 37 directly references `DevicesDbHelper.DB_VERSION` (Rule 2.2 / Rule 62), so the schema registry automatically reflects the new version.
  * Audited `DeviceData.kt` (L269), `UIHelper.java` (L116, L152), and `TrainingApplication.java` (L484–487): all have straightforward references to remove.
  * Audited resource strings: `devices_location_Network` in `strings_devices.xml` (all 9 locales) can be removed.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  * Delete `SpeedAndLocationDevice_Network.java`.
  * Remove `DeviceType.SPEED_AND_LOCATION_NETWORK` and references across `MySensorManager`, `DeviceManager`, `TrainingApplication`, `UIHelper`, `DeviceData.kt`, and `strings_devices.xml` (all 9 locales).
  * In `DevicesDatabaseManager.java`: remove `getSpeedAndLocationNetworkDeviceId()`, remove insertion in `addLocationDevicesToDb()`, bump `DevicesDbHelper.DB_VERSION` to 10, and implement idempotent `onUpgrade` deletion for `SPEED_AND_LOCATION_NETWORK`.
  * Update living docs (`req_tracking_cockpit.md`, `docs/architecture.md`) and unit test suites (`SpeedAndLocationDeviceResilienceTest`, `FreshInstallLocationInitTest`, `MySensorManagerPriorityTest`, `DevicesDatabaseManagerOrderingTest`, `DeviceTypeEnumOrderTest`, `SensorSourceDialogPriorityTest`).
* **Out-of-Scope Non-Goals**:
  * Modifying GPS or Google Fused priority logic (`ATT-5491`).
  * Changing `Equipment.db` table schemas or `EquipmentSensorOrdering`.
* **Complexity Rating**: **1 SP (Trivial)** (Dead code excision and single-table migration step).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-TRK-024` (`REQ-TRK-024.1`..`024.6`) and `REQ-STB-004`/`REQ-STB-008` in `req_tracking_cockpit.md` and `req_process_stability.md`. Introduces `REQ-SEN-027` for `Devices.db` v10 migration.
* **Historical Origin & Commit Trace**: `ATT-3961` (`fc28443c`, Sprint `2026-43.1`), `ATT-4375` (Sprint `2026-43.2`), and `ATT-5491` (`1ad11b81`, Sprint `2026-45.9`).
* **Root Reason for Existing Formulation**: `SPEED_AND_LOCATION_NETWORK` was retained as an experimental fallback for coarse location before Google Fused Location was fully established as the preferred provider.
* **Preservation of Core Invariants**: `SPEED_AND_LOCATION_GOOGLE_FUSED` and `SPEED_AND_LOCATION_GPS` provide complete location coverage; physical sensor precedence (`BIKE_SPEED > GOOGLE_FUSED > GPS`) is 100% preserved; existing user device pairings are untouched.

---

## 5. Architectural Strategy & High-Level Solution
* **Sensor Driver Excision**: Remove `SpeedAndLocationDevice_Network.java` and all references in `banalservice` and `trainingtracker`.
* **Devices.db Version 10 Migration**:
  * Bump `DevicesDbHelper.DB_VERSION` from 9 to 10.
  * Update `onCreate()` to include `case 10:`.
  * In `onUpgrade()`, add `oldVersion <= 9` migration: delete rows with `DEVICE_TYPE = 'SPEED_AND_LOCATION_NETWORK'` from `Devices4` table and clean up any linked equipment rows defensively.
* **Test Parity**: Update existing tests to reflect 2 location sensors (`GOOGLE_FUSED` and `GPS`) and add an upgraded-database migration verification test.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Dedicated physical sensor priority strictly maintained; de-Googled raw GPS fallback preserved; existing athlete pairings untouched; `DatabaseSchemaRegistry` matches `DevicesDbHelper.DB_VERSION` (Rule 62).
* **Risk Rating**: **LOW** (Dead code removal and safe idempotent single-table record cleanup with zero behavioral impact on GPS/Fused tracking).
