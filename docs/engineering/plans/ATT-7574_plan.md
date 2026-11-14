# Implementation Plan: ATT-7574 Radar State Preservation Across Packet Processing & Simulator Preemption

## 1. Architectural Overview & Boundaries (SWE.2)
- **Component**: `com.atrainingtracker.banalservice.devices.radar` & `com.atrainingtracker.banalservice.devices.bluetooth_le` & `com.atrainingtracker.banalservice.devices.ant_plus`
- **Data Flow**: Physical frames from `BTLERadarDevice` and `ANTBikeRadarDevice` update `RadarStateRepository`. Simulator preemption in `RadarTrafficSimulator` stops background simulation loops without mutating repository application state.
- **Invariants**: User-configured `audioSnoozeState` and `lightMode` must remain 100% stable during 4–8 Hz physical packet reception and simulator-to-hardware transitions. `RadarStateRepository.reset()` is never called in production code paths.

## 2. Proposed Changes
- [`RadarTrafficSimulator.kt`](file:///home/rainer/.gemini/antigravity/worktrees/02cffe02-c961-48a9-bc0a-30f7f1c75070/subagent-ATT-1286---Epic-Pro-Agent--Rotated-1--self-a018c5f6/app/src/main/java/com/atrainingtracker/banalservice/devices/radar/RadarTrafficSimulator.kt):
  - In `stop()`, eliminate `RadarStateRepository.reset()`. Emit `RadarConnectionState.DISCONNECTED` for `RadarSource.SIMULATOR` while preserving `currentState.lightMode`, `currentState.audioSnoozeState`, and `currentState.isLightControlSupported`.
- [`BTLERadarDevice.java`](file:///home/rainer/.gemini/antigravity/worktrees/02cffe02-c961-48a9-bc0a-30f7f1c75070/subagent-ATT-1286---Epic-Pro-Agent--Rotated-1--self-a018c5f6/app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BTLERadarDevice.java):
  - In `processTargets()`, read `currentState = RadarStateRepository.getState()` before preemption.
  - Guard preemption invocation with `if (RadarTrafficSimulator.INSTANCE.isSimulating())`.
- [`ANTBikeRadarDevice.kt`](file:///home/rainer/.gemini/antigravity/worktrees/02cffe02-c961-48a9-bc0a-30f7f1c75070/subagent-ATT-1286---Epic-Pro-Agent--Rotated-1--self-a018c5f6/app/src/main/java/com/atrainingtracker/banalservice/devices/ant_plus/ANTBikeRadarDevice.kt):
  - In `parseTargetsPage()`, guard preemption invocation with `if (RadarTrafficSimulator.isSimulating)`.

## 3. Atomic Implementation Steps
| Step | Action | Target Files | Verification |
| :--- | :--- | :--- | :--- |
| **1** | Replace destructive `reset()` in `RadarTrafficSimulator.stop()` with non-destructive state update | `RadarTrafficSimulator.kt` | Compilation check |
| **2** | Invert `currentState` read and gate preemption call in `BTLERadarDevice.processTargets()` | `BTLERadarDevice.java` | Compilation check |
| **3** | Gate preemption call with `isSimulating` in `ANTBikeRadarDevice.parseTargetsPage()` | `ANTBikeRadarDevice.kt` | Compilation check |
| **4** | Add unit tests for state preservation during packet ingestion and preemption | `RadarTrafficSimulatorTest.kt`, `BTLERadarDeviceTest.kt`, `ANTBikeRadarDeviceTest.kt` | `./gradlew testDebugUnitTest --tests "...RadarTrafficSimulatorTest" --tests "...BTLERadarDeviceTest" --tests "...ANTBikeRadarDeviceTest"` |
