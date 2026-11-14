# Test Specification: ATT-7574 Radar State Preservation Across Packet Processing & Simulator Preemption

## 1. Traceability
- **Requirement:** `REQ-CON-042` (Radar Application State Preservation During Preemption & Hardware Packet Ingestion)
- **Living Test:** `TST-CON-042` (Radar Application State Preservation During Preemption & Hardware Packet Ingestion Test Suite)
- **Target Components:** `RadarTrafficSimulator.kt`, `BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`, `RadarStateRepository.kt`

## 2. Test Cases & Verification Matrix

### TST-CON-042.1: BLE Packet Ingestion Preserves Snooze & Light Mode
* **Given** `RadarStateRepository` has active `audioSnoozeState = RadarAudioSnoozeState(isSnoozed = true, mode = SNOOZE_15M)` and `lightMode = RadarLightMode.DAY_FLASH`.
* **When** `BTLERadarDevice` receives V1/V2 target notifications and executes `processTargets()`.
* **Then** `RadarStateRepository.getState().audioSnoozeState.isSnoozed` remains `true` with mode `SNOOZE_15M`.
* **And** `RadarStateRepository.getState().lightMode` remains `RadarLightMode.DAY_FLASH`.

### TST-CON-042.2: ANT+ Packet Ingestion Preserves Snooze & Light Mode
* **Given** `RadarStateRepository` has active `audioSnoozeState = RadarAudioSnoozeState(isSnoozed = true, mode = SNOOZE_30M)` and `lightMode = RadarLightMode.PELOTON`.
* **When** `ANTBikeRadarDevice` processes incoming Page 0x30/0x31 packets via `parseTargetsPage()` and calls `publishTargets()`.
* **Then** `RadarStateRepository.getState().audioSnoozeState.isSnoozed` remains `true`.
* **And** `RadarStateRepository.getState().lightMode` remains `RadarLightMode.PELOTON`.

### TST-CON-042.3: Non-Destructive Simulator Preemption
* **Given** `RadarTrafficSimulator` is running an active scenario (`isSimulating == true`) with pre-configured `audioSnoozeState` or `lightMode`.
* **When** `onPhysicalHardwareConnected()` is triggered.
* **Then** `isSimulating` transitions to `false` and `simulationJob` is cancelled.
* **And** `RadarStateRepository.reset()` is NOT called.
* **And** `RadarStateRepository.getState().audioSnoozeState` and `lightMode` remain intact.

### TST-CON-042.4: Gated Simulator Preemption Call-Site Verification
* **Given** `RadarTrafficSimulator.isSimulating` is `false`.
* **When** `processTargets()` or `parseTargetsPage()` executes.
* **Then** `onPhysicalHardwareConnected()` is bypassed, preventing unnecessary stop or lock acquisition overhead.
