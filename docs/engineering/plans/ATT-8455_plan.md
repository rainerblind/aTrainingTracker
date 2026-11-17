# Stage 3: Implementation Plan - ATT-8455: RadarState `isAutoMode` Preservation

**Ticket**: [ATT-8455](https://rainerblind.atlassian.net/browse/ATT-8455) | **Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8455` | **Risk**: `LOW`  
**Requirement Mapping**: `REQ-CON-046` (`REQ-CON-046.1`..`REQ-CON-046.3`), `REQ-CON-031.8`, `REQ-CON-042.3`  
**Test Mapping**: `TST-CON-046` (`TST-CON-046.1`..`TST-CON-046.5`)

---

## 1. Architecture & Layering (SWE.2)
* **Boundaries**: Transport drivers (`BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`, `RadarTrafficSimulator.kt`) remain stateless telemetry producers on `Dispatchers.Default`/main `Handler`; `RadarLightController` remains authoritative for `isAutoMode`; `RadarStateRepository` remains the thread-safe (`lock`) arbitration hub with zero dependency on `RadarLightController` or Android UI.
* **UI Consistency & Persistence**: Zero UI/Compose/string edits (`TrackingTabsScreen.kt` and `EditDeviceDialog.kt` already consume `isAutoMode` cleanly); zero SQLite migrations.

## 2. Traceability Matrix
| Step | Requirement | Test Case | Target Production & Test Files |
| :--- | :--- | :--- | :--- |
| 1 | `REQ-CON-046.2`, `REQ-CON-046.3` | `TST-CON-046.4` | `RadarStateRepository.kt`, `RadarLightController.kt`, `RadarStateRepositoryTest.kt` |
| 2 | `REQ-CON-046.1` | `TST-CON-046.1` | `BTLERadarDevice.java` (6 sites), `BTLERadarDeviceTest.kt` |
| 3 | `REQ-CON-046.1` | `TST-CON-046.3` | `ANTBikeRadarDevice.kt` (3 sites), `ANTBikeRadarDeviceTest.kt` |
| 4 | `REQ-CON-046.1` | `TST-CON-046.2` | `RadarTrafficSimulator.kt` (`generateFrame`/`tick`/`start`/`stop`), `RadarTrafficSimulatorTest.kt` |
| 5 | `REQ-CON-046.2`, `REQ-CON-031.8` | `TST-CON-046.5` | `RadarLightControllerTest.kt` + full radar unit regression |

## 3. Atomic Implementation Steps (Stage 4 Construction)
1. **Step 1 — Cross-Source Control Sync in `RadarStateRepository.kt`**:
   - Under `synchronized(lock)`, when `updateState(newState)` updates the active source (`activeSource == NONE || newState.source == activeSource`), propagate `isAutoMode` and `audioSnoozeState` across `sourceStates` entries; on failover (`bestActive.source != previousActiveSource`), preserve the active `isAutoMode` and `audioSnoozeState` (`REQ-CON-042.3`) onto `bestActive`. Add `updateControlState(transform: (RadarState) -> RadarState)` (used by `RadarLightController` or compatible with `updateState(current.copy(...))`) while keeping `reset()` decoupled from `RadarLightController`.
   - Add `TST-CON-046.4` in `RadarStateRepositoryTest.kt` verifying `BLE -> ANT_PLUS/SIMULATOR` failover preserves `isAutoMode == false` and `audioSnoozeState`, and `reset()` restores `RadarState()`.
2. **Step 2 — `BTLERadarDevice.java` (6 Sites)**:
   - Update all 6 `new RadarState(...)` calls (`L154` watchdog, `L183` `startSearching`, `L202` `notifyStopSearching`, `L248` `configureServices`, `L490` `processTargets`, `L583` `disconnectFromGatt`) to pass the 9th arg `currentState.isAutoMode()` (and `RadarStateRepository.getState().isAutoMode()` at `L583` after `unregisterRadarDevice`).
   - Add `TST-CON-046.1` in `BTLERadarDeviceTest.kt` covering all 6 sites with `isAutoMode == false` + `setAutoMode()` restoration.
3. **Step 3 — `ANTBikeRadarDevice.kt` (3 Sites)**:
   - Update `publishTargets()` (`L458`), `onWatchdogTimeout()` (`L517`), and `shutDown()` (`L633`) to pass `isAutoMode = currentState.isAutoMode`.
   - Add `TST-CON-046.3` in `ANTBikeRadarDeviceTest.kt` covering all 3 sites + `setAutoMode()` restoration.
4. **Step 4 — `RadarTrafficSimulator.kt` (`generateFrame`, `tick`, `start`, `stop`)**:
   - Add `isAutoMode: Boolean = true` parameter to `generateFrame(...)` (preserving deterministic default), pass `isAutoMode = currentState.isAutoMode` in `tick()` (`L388-393`), `start()` (`L464-469`), and `stop()` (`L503-509`).
   - Add `TST-CON-046.2` in `RadarTrafficSimulatorTest.kt` verifying `generateFrame`, `tick`, `start` ticks, and `stop` preserve `isAutoMode == false` + `setAutoMode()` restoration.
5. **Step 5 — Integration Regression & Verification (`TST-CON-046.5`)**:
   - Add `TST-CON-046.5` in `RadarLightControllerTest.kt` testing `setManualLightMode()` / `cycleNextMode()` + `SmartRadarLightEngine` suspension under incoming frames and explicit `setAutoMode()` restoration.
   - Run targeted tests: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.radar.*" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERadarDeviceTest" --tests "com.atrainingtracker.banalservice.devices.ant_plus.ANTBikeRadarDeviceTest"`.

## 4. System Invariants & Rollback
* **Invariants**: `REQ-CON-041` source priority (`BLE > ANT_PLUS > SIMULATOR`), `REQ-CON-042.2` pre-preemption sampling in `BTLERadarDevice.processTargets`, `REQ-CON-042.3` `lightMode`/`audioSnoozeState` preservation, `SmartRadarLightEngine` override gating, `RadarStateRepository.reset()` isolation, `ANT-Android-SDKs` untouched.
* **Rollback**: Single git revert on `feature/ATT-8455` prior to squash-merge.
