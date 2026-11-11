# Stage 2: Requirement & Test Specification - ATT-5084: Support ANT+ Cycling Rearview Radar profile (Device Type 0x28) for legacy ANT+ radar units

**Ticket**: [ATT-5084](https://rainerblind.atlassian.net/browse/ATT-5084)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `v2026.46`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-CON-032`  
**Test Mapping**: `TST-CON-032`  
**Branch**: `feature/ATT-5084`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-11  

---

## 1. Traceability & Requirements Mapping

* **Requirement**: `REQ-CON-032` (*ANT+ Cycling Rearview Radar Profile [Device Type 0x28] Acquisition & Target Demuxing*)
* **Test Specification**: `TST-CON-032` (*ANT+ Cycling Rearview Radar Profile [Device Type 0x28] Driver & Target Demuxing Test Suite*)

---

## 2. Requirements & Verification Matrix

| Requirement ID | Description | Test Case | Target Test Class |
| :--- | :--- | :--- | :--- |
| `REQ-CON-032.1` | `android_antlib_4-16-0.aar` linking & Predefined Network slot 1 (`ANT_PLUS`) channel acquisition | `TST-CON-032.1` | `ANTBikeRadarDeviceTest.kt` |
| `REQ-CON-032.2` | Slave channel configuration (Device Type 40, RF Frequency 57, Period 4084 counts) | `TST-CON-032.1` | `ANTBikeRadarDeviceTest.kt` |
| `REQ-CON-032.3` | Data Page `0x30` (Targets 1–4) & `0x31` (Targets 5–8) multi-target bit demuxing (3.125m range, 3.04m/s speed) | `TST-CON-032.2` | `ANTBikeRadarDeviceTest.kt` |
| `REQ-CON-032.4` | Data Page `0x01` system state & Common Pages `0x50`/`0x51`/`0x52` battery & device metadata decoding | `TST-CON-032.3` | `ANTBikeRadarDeviceTest.kt` |
| `REQ-CON-032.5` | Wildcard discovery (`DeviceNumber = 0`, `DeviceType = 40`) & `DeviceType.BIKE_RADAR` ANT+ pairing | `TST-CON-032.4` | `ANTSearchForNewDevicesEngineMultiDeviceSearchTest.kt` |
| `REQ-CON-032.6` | 3.5s starvation watchdog fail-safe disconnect & simulator physical hardware preemption | `TST-CON-032.5` | `ANTBikeRadarDeviceTest.kt` |

---

## 3. Detailed Acceptance Criteria & Verification Procedures

### TST-CON-032.1: Predefined Network Slot 1 Channel Acquisition & Kinematics Configuration
* **Given**: `android_antlib_4-16-0.aar` linked in build configuration.
* **When**: `ANTBikeRadarDevice.connect()` binds `AntService` and calls `AntChannelProvider.acquireChannel()`.
* **Then**: The channel is acquired on Predefined Network slot 1 (`ANT_PLUS`), configured as `BIDIRECTIONAL_SLAVE`, Device Type 40 (`0x28`), RF Frequency 57 (2457 MHz), and Channel Period 4084 counts without throwing service exceptions.

### TST-CON-032.2: Data Page 0x30 & 0x31 Multi-Target Bit Demuxing
* **Given**: Raw 8-byte ANT+ broadcast frames for Page `0x30` and Page `0x31`.
* **When**: Decoded by `ANTBikeRadarDevice.decodeBroadcastData()`.
* **Then**: Target range ($3.125\text{ m/bit}$), closing speed ($3.04\text{ m/s/bit}$), threat level (`0=None`, `1=Approaching`, `2=Fast Approaching`), and threat side (`0=Behind`, `1=Right`, `2=Left`) are unpacked accurately into `RadarTarget` entities and published via `RadarStateRepository.updateState()`.

### TST-CON-032.3: Data Page 0x01 System Status & Common Pages Decoding
* **Given**: Data Page `0x01` (System State) or Common Pages `0x50`/`0x51`/`0x52` payloads.
* **When**: Processed by `ANTBikeRadarDevice`.
* **Then**: Error states map to `DISCONNECTED_ERROR`; manufacturer, model number, and battery status update device metadata safely without exception.

### TST-CON-032.4: Wildcard Discovery & Device Pairing
* **Given**: `ANTSearchForNewDevicesEngineMultiDeviceSearch` initialized for `DeviceType.BIKE_RADAR` (or `DeviceType.ALL`).
* **When**: Search starts.
* **Then**: Wildcard `AntChannel` (`DeviceNumber = 0`, `DeviceType = 40`) is opened on slot 1, `ExtendedData` extracts device number and RSSI, and `DeviceManager.onDeviceFound()` is notified.

### TST-CON-032.5: Starvation Watchdog & Hardware Preemption
* **Given**: An active ANT+ radar connection streaming targets.
* **When**: Broadcast packets cease for $\ge 3.5\text{ s}$.
* **Then**: Watchdog timer fires, transitioning state to `DISCONNECTED_ERROR` and resetting scalar sensor latches. Upon first valid broadcast frame, `RadarTrafficSimulator.onPhysicalHardwareConnected()` is triggered.

---

## 4. Invariants & Governance

1. **Predefined Network Slot 1 Protection**: Strict usage of slot 1 (`ANT_PLUS`), avoiding raw key rejection or RF 57 failure.
2. **PCC Legacy Invariant**: Coexists with `antpluginlib_3-9-0.aar` with zero interference to existing HRM, Speed, Cadence, or Power sensors.
3. **Fail-Safe Disconnect**: 3.5s starvation watchdog resets radar state cleanly if peripheral loses power or RF link drops.
4. **Zero SQLite Migration**: Preserves schema and `DB_VERSION` unchanged.
