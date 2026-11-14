# Test Specification: ATT-7575 Concurrent BLE & ANT+ Radar Source Arbitration

## 1. Traceability
- **Requirement:** `REQ-CON-041` (Radar Multi-Source Arbitration & Isolation)
- **Living Test:** `TST-CON-041` (Radar Multi-Source Arbitration & Isolation Verification)
- **Target Component:** `com.atrainingtracker.banalservice.devices.radar.RadarStateRepository`, `RadarState`, `RadarSource`, `ANTBikeRadarDevice`

## 2. Test Cases & Verification Matrix

### TST-CON-041.1: Priority-Based Active Stream Arbitration
* **Given** an empty `RadarStateRepository`.
* **When** an `ANT_PLUS` packet arrives with `RadarConnectionState.CONNECTED` and threat level `LOW`.
* **Then** `getState()` reflects `source = RadarSource.ANT_PLUS`, `overallThreatLevel = ThreatLevel.LOW`.
* **When** a `BLE` packet arrives with `RadarConnectionState.CONNECTED` and threat level `HIGH`.
* **Then** `getState()` switches active stream to `source = RadarSource.BLE` and threat level `HIGH` due to BLE priority (3 > 2).
* **When** a subsequent `ANT_PLUS` packet arrives with threat level `MEDIUM`.
* **Then** `getState()` remains `source = RadarSource.BLE` and threat level `HIGH` (BLE stream not clobbered by lower-priority ANT+).

### TST-CON-041.2: Secondary Disconnection & Timeout Isolation
* **Given** `BLE` is actively connected as the primary source in `RadarStateRepository`.
* **When** `ANT_PLUS` triggers a watchdog timeout emitting `RadarConnectionState.DISCONNECTED_ERROR` or `DISCONNECTED`.
* **Then** `getState()` remains `RadarConnectionState.CONNECTED` from `BLE`.
* **And** `getState(RadarSource.ANT_PLUS).connectionState` is `DISCONNECTED_ERROR` / `DISCONNECTED`.
* **And** `ANTBikeRadarDevice.isReceivingData()` returns `false` (isolated query).

### TST-CON-041.3: Seamless Failover on Primary Disconnect
* **Given** both `BLE` and `ANT_PLUS` are active and `CONNECTED` in `RadarStateRepository`, with `BLE` arbitrated as active.
* **When** `BLE` emits `RadarConnectionState.DISCONNECTED` or disconnect watchdog expires (> 3500ms).
* **Then** `RadarStateRepository` arbitrates `ANT_PLUS` as the new active source.
* **And** `getState()` reflects `source = RadarSource.ANT_PLUS` with `RadarConnectionState.CONNECTED` and valid target list.

### TST-CON-041.4: Reset Invariant
* **Given** `RadarStateRepository` holding states for multiple sources (`BLE`, `ANT_PLUS`).
* **When** `RadarStateRepository.reset()` is invoked.
* **Then** `getState()` returns a default `RadarState` with `connectionState = DISCONNECTED` and `source = NONE`.
* **And** `getState(RadarSource.BLE)` and `getState(RadarSource.ANT_PLUS)` both return default disconnected states.
