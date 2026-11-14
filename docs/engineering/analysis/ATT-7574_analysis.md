# Analysis: ATT-7574 Radar State Preservation Across Packet Processing & Simulator Preemption

## 1. Problem Statement & User Impact
Garmin Varia physical telemetry frames stream at 4–8 Hz over BLE (`BTLERadarDevice`) and ANT+ (`ANTBikeRadarDevice`). On target arrival, both drivers invoke `RadarTrafficSimulator.onPhysicalHardwareConnected()`, which triggers `RadarTrafficSimulator.stop()`. In `stop()`, when the simulator was active, `RadarStateRepository.reset()` is invoked. Additionally, in `BTLERadarDevice.processTargets()`, `currentState` is sampled *after* `onPhysicalHardwareConnected()`. Consequently, any active 1-tap audio snooze (`RadarAudioSnoozeState`) or active tail light operating mode (`RadarLightMode`) is wiped to defaults (`INACTIVE`, `OFF`) whenever physical packets arrive or when physical hardware connects.

## 2. Forensic Root Cause Analysis
1. **Destructive Reset in Simulator Stop**:
   - `RadarTrafficSimulator.stop()` unconditionally calls `RadarStateRepository.reset()` whenever `getActiveSource() == RadarSource.SIMULATOR`.
   - `RadarStateRepository.reset()` clears all source states and initializes `_radarState.value` to a blank default `RadarState()`, discarding active `audioSnoozeState` and `lightMode`.
2. **Preemption Invocation Ordering**:
   - In `BTLERadarDevice.processTargets()`, `RadarTrafficSimulator.INSTANCE.onPhysicalHardwareConnected()` is executed *before* `RadarState currentState = RadarStateRepository.getState()`. If simulator preemption occurs, `currentState` captures the wiped post-reset state.
3. **Unconditional Target Page Invocation**:
   - In `ANTBikeRadarDevice.parseTargetsPage()`, `onPhysicalHardwareConnected()` is called on every incoming target page packet rather than checking whether simulation is active.

## 3. Corrective Strategy & Invariants
- **Non-Destructive Simulator Shutdown**: In `RadarTrafficSimulator.stop()`, replace `RadarStateRepository.reset()` with a non-destructive state update setting `RadarSource.SIMULATOR` connection state to `DISCONNECTED` while preserving `lightMode`, `audioSnoozeState`, and `isLightControlSupported`.
- **Gated Preemption & Inverted Sampling**:
  - In `BTLERadarDevice.java`, sample `currentState = RadarStateRepository.getState()` before preemption, and gate preemption with `if (RadarTrafficSimulator.INSTANCE.isSimulating())`.
  - In `ANTBikeRadarDevice.kt`, gate preemption with `if (RadarTrafficSimulator.isSimulating)`.
- **Preservation Invariant**: User-selected tail light mode and active audio snooze MUST persist across physical packet streaming and simulator-to-hardware transitions.

## 4. Change Impact Analysis (REQ-PRO-064 / ASPICE SUP.10)
- **Affected Requirements**: `REQ-CON-042` (Radar Application State Preservation During Preemption & Packet Ingestion).
- **Affected Architecture Elements**: `RadarTrafficSimulator.kt`, `BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`.
- **Verification Impact & Test Surface**: Automated unit tests in `RadarTrafficSimulatorTest.kt`, `BTLERadarDeviceTest.kt`, and `ANTBikeRadarDeviceTest.kt`.
- **Risk Assessment & Invariants**: Low risk. Thread safety protected by `RadarStateRepository` synchronization lock.
