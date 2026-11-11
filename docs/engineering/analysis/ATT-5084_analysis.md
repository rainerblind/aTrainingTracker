# Stage 1 Analysis: ATT-5084 - [Feature] Support ANT+ Cycling Rearview Radar profile (Device Type 0x28) for legacy ANT+ radar units

**Ticket**: [ATT-5084](https://rainerblind.atlassian.net/browse/ATT-5084)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `v2026.46`  
**Active Sprint**: `sprint/2026-46.2`  
**Branch**: `feature/ATT-5084`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

While modern rearview radars provide Bluetooth Low Energy connectivity, legacy units (original Garmin Varia RTL500, RTL510) and multiple third-party radars (Sigma RECO 80/81 LINK, Bryton Gardia R300L, Magene L508, Trek CarBack) on ANT+-capable Android devices operate over the standardized ANT+ Bike Radar device profile (Device Type `40` / `0x28`).

Currently, *aTrainingTracker*'s ANT+ connectivity layer is built on `antpluginlib_3-9-0.aar` (`AntPluginPcc`), which completely predates the ANT+ Bike Radar profile:
1. `antpluginlib_3-9-0.aar` has no `AntPlusBikeRadarPcc` class.
2. `com.dsi.ant.plugins.antplus.pcc.defines.DeviceType` does not define `BIKE_RADAR` (Device Type `40`).
3. `MultiDeviceSearch` rejects Device Type `40` and fails to discover ANT+ radars.
4. `DeviceType.getRemoteDeviceTypes(Protocol.ANT_PLUS)` omits `DeviceType.BIKE_RADAR`, preventing manual or automatic pairing.

To support all ANT+ rearview radars, the application must link the low-level ANT channel API (`android_antlib_4-16-0.aar`), acquire an ANT slave channel on predefined network slot 1 (`ANT_PLUS`), decode ANT+ Bike Radar Data Pages (`0x30`, `0x31`, `0x01`, common pages `0x50`/`0x51`/`0x52`), and route multi-target vectors into the unified `RadarStateRepository` and `RadarWindowAggregator` pipeline introduced in `ATT-5083`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification (4-Augen-Prinzip — ATT-3534)**:
  * *Audit of Hypothesis against Codebase*:
    1. **SDK Dependency**: Repository contains `ANT-Android-SDKs/ANT_Android_SDK/API/android_antlib_4-16-0.aar`. `app/build.gradle` currently links only `antpluginlib_3-9-0.aar`. Linking `android_antlib_4-16-0.aar` exposes `AntService`, `AntChannelProvider`, `AntChannel`, and `IAntChannelEventHandler`.
    2. **Predefined Network Slot 1 (ANT_PLUS)**: `Android_ANTRadioService` rejects RF frequency 57 (2457 MHz) on `PredefinedNetwork.PUBLIC` (slot 0) and rejects raw network keys in `new NetworkKey(...)`. The underlying AIDL communicator `IAntChannelProviderCommunicator.acquireChannel(Context, int networkNumber, ...)` accepts raw integer slot 1 (`ANT_PLUS`), allowing legitimate RF 57 channel acquisition.
    3. **Channel Kinematics & Timing**: ANT+ Bike Radar profile specifies Device Type `40` (`0x28`), Channel Period `4084` counts ($8.0235\text{ Hz}$ broadcast rate), RF Frequency `57` ($2457\text{ MHz}$), and Channel Type `BIDIRECTIONAL_SLAVE`.
    4. **Payload Bit-Packing**:
       - Data Page `0x30` (Targets 1–4) & `0x31` (Targets 5–8):
         - Byte 0: Page Number (`0x30` or `0x31`).
         - Byte 1: 2-bit Threat Level per target ($i \in [0..3]$: `(byte1 >> (i * 2)) & 0x03`).
         - Byte 2: 2-bit Threat Side per target ($i \in [0..3]$: `(byte2 >> (i * 2)) & 0x03`).
         - Bytes 3–5: 24-bit packed range ($i \in [0..3]$: `((ranges >> (i * 6)) & 0x3F) * 3.125f` meters, range $0..196.875\text{ m}$).
         - Bytes 6–7: 16-bit packed closing speed ($i \in [0..3]$: `((speeds >> (i * 4)) & 0x0F) * 3.04f` m/s, range $0..45.6\text{ m/s}$).
       - Data Page `0x01`: System state (0 = Normal, 1 = Limited, 2 = Error).
       - Common Pages `0x50` / `0x51` / `0x52`: Manufacturer ID, Model Number, Serial Number, Battery Status/Voltage.
    5. **Discovery & Wildcard Search**:
       - Because `antpluginlib_3-9-0.aar`'s `MultiDeviceSearch` cannot search for Device Type 40, `ANTSearchForNewDevicesEngineMultiDeviceSearch` must open a wildcard `AntChannel` (`DeviceNumber = 0`, `DeviceType = 40`) with `LibConfig` enabled to receive `ExtendedData` (reporting device number and RSSI) when searching for `DeviceType.BIKE_RADAR` (or `DeviceType.ALL`).
    6. **Unified State & Sensor Pipeline**:
       - Demuxed targets map directly into `RadarTarget(targetId, distanceMeters, approachSpeedMps, threatLevel, lateralOffsetMeters = null, vehicleSize = UNKNOWN_OR_LIGHT, laneState = UNKNOWN)`.
       - Emits into `RadarStateRepository.updateState()` and feeds `RadarWindowAggregator` to latch 1 Hz scalar sensors (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`) with zero SQLite migrations.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit

* **In-Scope Goals (ATT-5084 - 3 SP)**:
  * Link `android_antlib_4-16-0.aar` in `app/build.gradle`.
  * Add `DeviceType.BIKE_RADAR` to `DeviceType.getRemoteDeviceTypes(Protocol.ANT_PLUS)`.
  * Implement `ANTBikeRadarDevice` managing `AntService` binding, Predefined Network Slot 1 channel acquisition, and 3.5s starvation watchdog.
  * Implement binary page decoding for Data Pages `0x30`, `0x31`, `0x01`, `0x50`, `0x51`, `0x52`.
  * Wire `ANTBikeRadarDevice` in `DeviceManager.createNewANTDevice()` and `createPairedRemoteDevices()`.
  * Extend `ANTSearchForNewDevicesEngineMultiDeviceSearch` with wildcard `AntChannel` scan for Device Type 40 (`0x28`).
  * Unit tests validating byte unpacking, channel configuration, and discovery callbacks.
* **Out-of-Scope Non-Goals**:
  * ANT+ Bike Lights profile (`Device Type 0x23` / Pages `0x20`, `0x21`, `0x22`) — tracked separately in child ticket `ATT-5103`.
  * Modifications to BLE GATT driver (`BTLERadarDevice.java`) or UI overlay (`RadarEdgeOverlay.kt`).
  * Zero database migrations (`DB_VERSION` unchanged).

---

## 4. Proposed Architectural Changes (SWE.1 / SWE.2 Preview)

```
[ AntService.bindService() ]
            │
            ▼
[ AntChannelProvider (Predefined Network Slot 1 / ANT_PLUS) ]
            │
   Wildcard Scan (Device 0)                     Paired Device (Device N)
            │                                              │
            ▼                                              ▼
[ ANTSearchForNewDevicesEngine ]               [ ANTBikeRadarDevice ]
  - Wildcard 0x28 scan                           - Slave Channel (Period 4084, RF 57)
  - ExtendedData ChannelId extraction            - Page 0x30 / 0x31 Demuxer
  - Reports to DeviceManager                     - 3.5s Starvation Watchdog
                                                           │
                                                           ▼
                                         [ RadarStateRepository & RadarWindowAggregator ]
                                                           │
                                                           ▼
                                      [ Cockpit Overlay / Audio Chime / Scalar Sensors ]
```

---

## 5. System Invariants & Preserved Behavior

1. **PCC Legacy Invariant**: Existing ANT+ PCC devices (HRM, Speed, Cadence, Power, Footpod, Environment) continue using `antpluginlib_3-9-0.aar` with zero behavioral modification.
2. **Predefined Network Slot 1 Protection**: Channel acquisition strictly uses Predefined Network slot 1 (`ANT_PLUS`), avoiding raw key rejection or RF 57 failure.
3. **Fail-Safe Disconnect Watchdog**: If no ANT+ broadcast packets arrive within 3.5s, state transitions to `DISCONNECTED_ERROR` and sensors reset to clear state.
4. **Hardware Preemption**: Physical ANT+ radar frames immediately call `RadarTrafficSimulator.onPhysicalHardwareConnected()`.
5. **Zero SQLite Migration**: Device number stored in existing `ant_device_number` column. Scalar metrics use existing sensor architecture.

---

## 6. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-CON-032` (*ANT+ Cycling Rearview Radar Profile [Device Type 0x28] Acquisition & Target Demuxing*) and test specification `TST-CON-032`, extending `REQ-CON-001` (ANT+ Profiles), `REQ-CON-004` (Async Device Search), and `REQ-CON-028` (Radar Architecture).
* **Chesterton's Fence Invariant**: `antpluginlib_3-9-0.aar` cannot be modified or replaced as it provides stable, certified PCC drivers for HR, Speed, Cadence, Power, and Footpod. Linking `android_antlib_4-16-0.aar` operates strictly alongside `antpluginlib_3-9-0.aar` without classpath collisions.
