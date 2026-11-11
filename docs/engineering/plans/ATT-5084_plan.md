# Stage 3: Implementation Plan - ATT-5084: Support ANT+ Cycling Rearview Radar profile (Device Type 0x28) for legacy ANT+ radar units

**Ticket**: [ATT-5084](https://rainerblind.atlassian.net/browse/ATT-5084)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `v2026.46`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-CON-032`  
**Test Mapping**: `TST-CON-032`  
**Branch**: `feature/ATT-5084`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

Modern cycling radars communicate over BLE, but legacy units (Garmin Varia RTL500, RTL510) and several third-party radars (Sigma RECO 80/81 LINK, Bryton Gardia R300L, Magene L508, Trek CarBack) on ANT+-capable Android devices operate exclusively over the standardized ANT+ Bike Radar profile (Device Type `40` / `0x28`). 

The existing `antpluginlib_3-9-0.aar` library does not support Device Type `40`. This implementation links `android_antlib_4-16-0.aar`, acquires a slave channel on Predefined Network slot 1 (`ANT_PLUS`), decodes multi-target broadcast frames (Pages `0x30` and `0x31`), integrates wildcard discovery in `ANTSearchForNewDevicesEngineMultiDeviceSearch`, and routes targets to `RadarStateRepository` and `RadarWindowAggregator`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-CON-032` (*ANT+ Cycling Rearview Radar Profile [Device Type 0x28] Acquisition & Target Demuxing*)
* **Test Mapping**: `TST-CON-032` (*ANT+ Cycling Rearview Radar Profile [Device Type 0x28] Driver & Target Demuxing Test Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **PCC Legacy Invariant**: HRM, Speed, Cadence, Power, Footpod, and Environment sensors continue using `antpluginlib_3-9-0.aar` with zero interference.
2. **Predefined Network Slot 1 Protection**: Channel acquisition strictly targets Predefined Network slot 1 (`ANT_PLUS`), avoiding raw key rejection or RF 57 out-of-range exceptions.
3. **Fail-Safe Disconnect Watchdog**: If broadcast packets cease for $\ge 3.5\text{ s}$, state transitions to `DISCONNECTED_ERROR` and sensors reset to clear state.
4. **Zero SQLite Migration**: Device number persists in existing `ant_device_number` column. `DB_VERSION` remains untouched.
5. **Hardware Preemption**: Physical ANT+ radar data immediately preempts simulator via `RadarTrafficSimulator.onPhysicalHardwareConnected()`.

---

## 4. Proposed Architectural Changes

### Component 1: Build & Channel Acquisition (`app/build.gradle`, `AntChannelHelper.kt`)
- Link `android_antlib_4-16-0.aar` in `app/build.gradle`.
- Provide `AntChannelHelper` to acquire `AntChannel` from `AntChannelProvider` on Predefined Network slot 1 (`ANT_PLUS`) via `IAntChannelProviderCommunicator.acquireChannel(context, 1, ...)`.

### Component 2: Device Model & Registration (`DeviceType.java`, `DeviceManager.java`)
- Add `DeviceType.BIKE_RADAR` to `DeviceType.getRemoteDeviceTypes(Protocol.ANT_PLUS)`.
- In `DeviceManager.createNewANTDevice()`: instantiate `ANTBikeRadarDevice` for `DeviceType.BIKE_RADAR`.

### Component 3: ANT+ Bike Radar Driver (`ANTBikeRadarDevice.java` or `.kt`)
- Extends `MyRemoteDevice`.
- Registers sensors: `RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`.
- Channel configuration:
  - Device Type `40` (`0x28`), RF Frequency `57` ($2457\text{ MHz}$), Period `4084` ($8.0235\text{ Hz}$), `BIDIRECTIONAL_SLAVE`.
- Data Page Decoding (`decodeBroadcastData(byte[] payload)`):
  - Page `0x30` (Targets 1–4) & `0x31` (Targets 5–8):
    - Threat Level: `(byte1 >> (i * 2)) & 0x03`
    - Threat Side: `(byte2 >> (i * 2)) & 0x03`
    - Range: `((ranges >> (i * 6)) & 0x3F) * 3.125f`
    - Closing Speed: `((speeds >> (i * 4)) & 0x0F) * 3.04f`
  - Page `0x01`: System state.
  - Common Pages `0x50`/`0x51`/`0x52`: Manufacturer ID, Model Number, Software Version, Battery Status.
- Starvation watchdog (3.5s timeout) transitioning to `DISCONNECTED_ERROR`.
- Updates `RadarStateRepository` and `RadarWindowAggregator`.

### Component 4: Wildcard Discovery (`ANTSearchForNewDevicesEngineMultiDeviceSearch.java`)
- When searching for `DeviceType.BIKE_RADAR` (or `DeviceType.ALL`), acquire wildcard `AntChannel` (`DeviceNumber = 0`, `DeviceType = 40`, RF `57`, Period `4084`) on slot 1.
- Enable `LibConfig` to receive `ExtendedData.getChannelId().getDeviceNumber()`.
- Dispatch discovered devices to `IANTAsyncSearchEngineInterface.onNewDeviceFound()`.

---

## 5. Step-by-Step Implementation Sequence

### Step 1: SDK Dependency & Channel Helper (`REQ-CON-032.1`, `TST-CON-032.1`)
- Ensure `implementation files('../ANT-Android-SDKs/ANT_Android_SDK/API/android_antlib_4-16-0.aar')` in `app/build.gradle`.
- Implement `AntChannelHelper` to cleanly acquire slot 1 (`ANT_PLUS`) channels from `AntChannelProvider`.

### Step 2: Device Wiring in DeviceType & DeviceManager (`REQ-CON-032.5`, `TST-CON-032.4`)
- Add `BIKE_RADAR` to `DeviceType.getRemoteDeviceTypes(Protocol.ANT_PLUS)`.
- Update `DeviceManager.createNewANTDevice()` to return `ANTBikeRadarDevice`.
- Support device discovery callback without requiring `MultiDeviceSearchResult`.

### Step 3: Implement ANTBikeRadarDevice (`REQ-CON-032.2`, `REQ-CON-032.3`, `REQ-CON-032.4`, `REQ-CON-032.6`)
- Create `ANTBikeRadarDevice` in `com.atrainingtracker.banalservice.devices.ant_plus`.
- Implement channel setup (Device 40, RF 57, Period 4084, Slave).
- Implement bitwise decoders for Pages `0x30`, `0x31`, `0x01`, `0x50`, `0x51`, `0x52`.
- Wire `RadarStateRepository`, `RadarWindowAggregator`, and 3.5s starvation watchdog.

### Step 4: Wildcard Search Integration (`REQ-CON-032.5`, `TST-CON-032.4`)
- Update `ANTSearchForNewDevicesEngineMultiDeviceSearch.java` with wildcard radar scanning.

### Step 5: Unit & Contract Tests (`TST-CON-032.1`..`TST-CON-032.5`)
- Author `ANTBikeRadarDeviceTest.kt` verifying page bit decoding, channel kinematics, and watchdog behavior.
- Run tests under `parallel_sprint_lock.py`.

---

## 6. Verification Commands

```bash
python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest \
  --tests "com.atrainingtracker.banalservice.devices.ant_plus.ANTBikeRadarDeviceTest" \
  --tests "com.atrainingtracker.banalservice.devices.ant_plus.search_new.ANTSearchForNewDevicesEngineMultiDeviceSearchTest" \
  --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"

python3 tools/verify_requirement_governance.py
```
