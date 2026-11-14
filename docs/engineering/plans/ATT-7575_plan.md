# Implementation Plan: ATT-7575 Concurrent BLE & ANT+ Radar Source Arbitration

## 1. Architectural Overview & Boundaries (SWE.2)
- **Component**: `com.atrainingtracker.banalservice.devices.radar` & `com.atrainingtracker.banalservice.devices.ant_plus`
- **Data Flow**: Hardware transports (`BTLERadarDevice`, `ANTBikeRadarDevice`) push `RadarState` tagged with `RadarSource` into `RadarStateRepository`.
- **Arbitration Invariant**: Active stream selection prioritizes `BLE (3) > ANT_PLUS (2) > SIMULATOR (1) > NONE (0)`. Secondary link errors / timeouts are isolated in `sourceStates` and never clobber an active higher-priority stream. If active primary disconnects, repository seamlessly falls back to highest-priority connected secondary source.

## 2. Proposed Changes
- [`RadarState.kt`](file:///home/rainer/.gemini/antigravity/worktrees/02cffe02-c961-48a9-bc0a-30f7f1c75070/subagent-ATT-1286---Epic-Pro-Agent--Rotated-1--self-a018c5f6/app/src/main/java/com/atrainingtracker/banalservice/devices/radar/RadarState.kt):
  - Define `enum class RadarSource(val priority: Int) { NONE(0), SIMULATOR(1), ANT_PLUS(2), BLE(3) }`.
  - Add `val source: RadarSource = RadarSource.NONE` to `RadarState` constructor with default argument for 100% backward compatibility.
- [`RadarStateRepository.kt`](file:///home/rainer/.gemini/antigravity/worktrees/02cffe02-c961-48a9-bc0a-30f7f1c75070/subagent-ATT-1286---Epic-Pro-Agent--Rotated-1--self-a018c5f6/app/src/main/java/com/atrainingtracker/banalservice/devices/radar/RadarStateRepository.kt):
  - Maintain `sourceStates: ConcurrentHashMap<RadarSource, RadarState>` and `activeSource: RadarSource`.
  - In `updateState(newState)`: record to `sourceStates`; arbitrate active state based on priority, connection state (`CONNECTED`/`CONNECTING`), and secondary error isolation.
  - Add `getState(source: RadarSource): RadarState` for transport-isolated queries.
  - Update `reset()` to clear `sourceStates`, reset `activeSource`, and reset `_radarState.value`.
- [`ANTBikeRadarDevice.kt`](file:///home/rainer/.gemini/antigravity/worktrees/02cffe02-c961-48a9-bc0a-30f7f1c75070/subagent-ATT-1286---Epic-Pro-Agent--Rotated-1--self-a018c5f6/app/src/main/java/com/atrainingtracker/banalservice/devices/ant_plus/ANTBikeRadarDevice.kt):
  - Tag emitted `RadarState` with `source = RadarSource.ANT_PLUS`.
  - Update `isReceivingData()` to query `RadarStateRepository.getState(RadarSource.ANT_PLUS).connectionState == RadarConnectionState.CONNECTED`.
- [`BTLERadarDevice.java`](file:///home/rainer/.gemini/antigravity/worktrees/02cffe02-c961-48a9-bc0a-30f7f1c75070/subagent-ATT-1286---Epic-Pro-Agent--Rotated-1--self-a018c5f6/app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BTLERadarDevice.java):
  - Pass `RadarSource.BLE` when constructing emitted `RadarState`.

## 3. Atomic Implementation Steps
| Step | Action | Target Files | Verification |
| :--- | :--- | :--- | :--- |
| **1** | Define `RadarSource` enum and add `source` to `RadarState` | `RadarState.kt` | Compilation check |
| **2** | Add per-source tracking, arbitration, failover & isolated query in `RadarStateRepository` | `RadarStateRepository.kt` | Unit tests |
| **3** | Tag `ANTBikeRadarDevice` emissions with `RadarSource.ANT_PLUS` and isolate `isReceivingData()` | `ANTBikeRadarDevice.kt` | Unit tests |
| **4** | Tag `BTLERadarDevice` emissions with `RadarSource.BLE` | `BTLERadarDevice.java` | Compilation check |
| **5** | Implement unit tests for priority arbitration, secondary error isolation, failover, and `reset()` | `RadarStateRepositoryTest.kt`, `ANTBikeRadarDeviceTest.kt` | `./gradlew testDebugUnitTest --tests "...RadarStateRepositoryTest" --tests "...ANTBikeRadarDeviceTest"` |
