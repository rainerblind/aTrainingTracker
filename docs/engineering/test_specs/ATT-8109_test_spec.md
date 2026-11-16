# Stage 2: Requirement & Test Specification - ATT-8109: Instance-Isolated Radar Connection State

**Ticket**: [ATT-8109](https://rainerblind.atlassian.net/browse/ATT-8109)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Schicht 2026-11-16_02_Spät` | **Branch**: `feature/ATT-8109`  
**Requirement Mapping**: `REQ-CON-044` (*Instance-Isolated Connection State & Multi-Device Gating for BLE & ANT+ Radar*)  
**Test Spec ID**: `TST-CON-044`  
**Author**: AI Agent 1 (Implementer, Lane 1 Flash) | **Date**: 2026-11-16  

---

## 1. Requirement Specification (REQ-CON-044)

### 1.1 Problem Statement & Rationale
When multiple radar peripherals are paired in `DeviceManager` across multiple bikes (e.g. `RearVue8` on bike A and `ATT-Rdr` on bike B), connecting to one radar falsely marks all paired BLE radar peripherals as active/connected on the Start screen and in Cockpit status headers. In `BTLERadarDevice.java:174-176`, `isReceivingData()` checked only global `RadarStateRepository.getState(RadarSource.BLE) == CONNECTED`, discarding `MyBTLEDevice`'s per-instance GATT state (`mState == State.CONNECTED_WITH_SERVICE`). `ANTBikeRadarDevice.kt:226-227` exhibits an identical vulnerability (`ATT-8109`, extending `REQ-CON-028` and `REQ-CON-041.4`).

### 1.2 Attributes
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-002`
- **Implementation**: `BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`, `MyBTLEDevice.java`

### 1.3 Functional & Architectural Requirements
1. **Per-Instance BLE GATT Connection Qualification (`REQ-CON-044.1`)**: `BTLERadarDevice.isReceivingData()` SHALL require that *this specific device instance* has an active GATT connection and discovered services via `super.isReceivingData()` (`mState == State.CONNECTED_WITH_SERVICE`), in conjunction with `RadarStateRepository.getState(RadarSource.BLE).getConnectionState() == RadarConnectionState.CONNECTED`. Paired disconnected peer devices SHALL evaluate to `false`.
2. **Fail-Safe Watchdog Starvation Retention (`REQ-CON-044.2`)**: If a connected `BTLERadarDevice` ceases receiving packets (triggering the 3.5s watchdog and setting `RadarConnectionState.DISCONNECTED_ERROR`), `isReceivingData()` SHALL evaluate to `false` even if the GATT connection remains open.
3. **Per-Instance ANT+ Radar Packet Gating (`REQ-CON-044.3`)**: `ANTBikeRadarDevice.isReceivingData()` SHALL evaluate instance-level packet reception within the watchdog window (`3500L`), in conjunction with `RadarStateRepository.getState(RadarSource.ANT_PLUS).connectionState == RadarConnectionState.CONNECTED`.

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* two paired BLE radar devices (`device1` and `device2`) in `DeviceManager`,
  * *When* `device1` is connected (`CONNECTED_WITH_SERVICE`) and `device2` is disconnected (`DISCONNECTED`),
  * *Then* `device1.isReceivingData()` returns `true`, and `device2.isReceivingData()` returns `false`.
* *Given* a connected `BTLERadarDevice` (`CONNECTED_WITH_SERVICE`),
  * *When* packet starvation occurs and the 3.5s watchdog fires,
  * *Then* `device.isReceivingData()` returns `false`.
* *Given* two configured ANT+ radar devices,
  * *When* only one device receives broadcast data,
  * *Then* only that active instance returns `true`.

### 1.5 System Invariants
Single-radar operation remains unchanged; cross-protocol isolation (`RadarSource.BLE` vs. `ANT_PLUS`) is preserved; 100% test pass rate.

---

## 2. Test Specification (TST-CON-044)

### Test Case 1: Multi-Instance BLE Radar Gating (`TST-CON-044.1`)
* **Scope**: Unit Test (`BTLERadarDeviceTest.kt`).
* **Assertions**: `device1` (connected) returns `true`; `device2` (disconnected) returns `false`; `DeviceManager.getActiveRemoteDevices()` contains only `device1`.

### Test Case 2: Watchdog Starvation Disconnect Retention (`TST-CON-044.2`)
* **Scope**: Unit Test (`BTLERadarDeviceTest.kt`).
* **Assertions**: Connected device with watchdog timeout returns `false` despite `CONNECTED_WITH_SERVICE`.

### Test Case 3: Multi-Instance ANT+ Radar Isolation (`TST-CON-044.3`)
* **Scope**: Unit Test (`ANTBikeRadarDeviceTest.kt`).
* **Assertions**: Only ANT+ radar receiving broadcast packets returns `true`; silent peer returns `false`.

### Test Case 4: Tier-1 Targeted Regression & Governance Verification (`TST-CON-044.4`)
* **Scope**: Regression & Contract Test.
* **Command**: `./gradlew testDebugUnitTest --tests "*BTLERadarDeviceTest*" --tests "*ANTBikeRadarDeviceTest*" --tests "*RadarStateRepositoryTest*"` + `python3 tools/verify_requirement_governance.py`.
* **Assertions**: 100% test pass rate; zero requirement governance violations.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-CON-044.1` | Per-instance BLE GATT connection qualification | `TST-CON-044.1` | Unit Test |
| `REQ-CON-044.2` | Watchdog starvation fail-safe retention | `TST-CON-044.2` | Unit Test |
| `REQ-CON-044.3` | Per-instance ANT+ packet reception gating | `TST-CON-044.3` | Unit Test |
| `REQ-CON-041` / Invariants | Cross-protocol isolation & single-device parity | `TST-CON-044.4` | Targeted Regression |
