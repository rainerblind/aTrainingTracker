# Stage 2: Requirement & Test Specification - ATT-8455: RadarState `isAutoMode` Preservation

**Ticket**: [ATT-8455](https://rainerblind.atlassian.net/browse/ATT-8455) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) | **Branch**: `feature/ATT-8455`  
**Requirement Mapping**: `REQ-CON-046` (extending `REQ-CON-031.8`, `REQ-CON-042.3`) | **Test Spec ID**: `TST-CON-046`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-17

---

## 1. Requirement Specification (`REQ-CON-046`)

* **Priority**: High | **Type**: Functional | **Verification Method**: Unit
* **Implementation**: `RadarState.kt`, `RadarStateRepository.kt`, `BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`, `RadarTrafficSimulator.kt`, `RadarLightController.kt`

### 1.1 Functional & Architectural Requirements
1. **Driver & Simulator `isAutoMode` Preservation (`REQ-CON-046.1`)**: All 6 `RadarState` emission sites in `BTLERadarDevice.java` (`mWatchdogRunnable`, `startSearching`, `notifyStopSearching`, `configureServices`, `processTargets`, `disconnectFromGatt`), all 3 sites in `ANTBikeRadarDevice.kt` (`publishTargets`, `onWatchdogTimeout`, `shutDown`), and `RadarTrafficSimulator.kt` (`generateFrame`, `tick`, `start`, `stop`) SHALL propagate `isAutoMode` without reverting manual overrides to `true`.
2. **Cross-Source Arbitration & Automation Continuity (`REQ-CON-046.2`)**: `RadarStateRepository` SHALL preserve and sync `isAutoMode` (alongside `audioSnoozeState` per `REQ-CON-042.3`) across `sourceStates` during `BLE`/`ANT_PLUS`/`SIMULATOR` failover; `RadarLightController.setAutoMode()` SHALL explicitly restore `isAutoMode = true` and resume `SmartRadarLightEngine`.
3. **Repository Reset Isolation (`REQ-CON-046.3`)**: `RadarStateRepository.reset()` SHALL remain decoupled from `RadarLightController`, resetting `_radarState` to default `RadarState()` cleanly for unit-test isolation.

### 1.2 Acceptance Criteria (Given-When-Then)
* **Given** `RadarStateRepository` has `isAutoMode == false` after manual light selection (`setManualLightMode()` / `cycleNextMode()`),
  * **When** telemetry or lifecycle `RadarState` frames arrive from `BTLERadarDevice` (all 6 sites), `ANTBikeRadarDevice` (all 3 sites), or `RadarTrafficSimulator` (`generateFrame`/`tick`/`start`/`stop`),
  * **Then** `RadarStateRepository.getState().isAutoMode` remains `false` (until `RadarLightController.setAutoMode()` explicitly sets `true`).
* **Given** concurrent `BLE` and `ANT_PLUS` entries in `RadarStateRepository.sourceStates` with `isAutoMode == false`,
  * **When** `BLE` disconnects and arbitration fails over to `ANT_PLUS` or `SIMULATOR`,
  * **Then** `isAutoMode` stays `false` and `SmartRadarLightEngine` stays suspended; calling `RadarStateRepository.reset()` resets repository state cleanly to `RadarState()`.

### 1.3 System Invariants
`REQ-CON-041` source priority (`BLE > ANT_PLUS > SIMULATOR`), `REQ-CON-042.3` `lightMode`/`audioSnoozeState` preservation, `SmartRadarLightEngine` manual override gating, zero SQLite schema changes, zero 9-language string changes.

---

## 2. Test Specification (`TST-CON-046`)

* **`TST-CON-046.1` (`BTLERadarDeviceTest.kt`)**: Verify all 6 `BTLERadarDevice` `RadarState` emission paths (`startSearching`, `notifyStopSearching`, `configureServices`, `processTargets`, `mWatchdogRunnable`, `disconnectFromGatt`) preserve `isAutoMode == false`, and `setAutoMode()` restores `true`.
* **`TST-CON-046.2` (`RadarTrafficSimulatorTest.kt`)**: Verify `generateFrame`, `tick`, `start` loop frames, and `stop` preserve `isAutoMode == false`, and `setAutoMode()` restores `true`.
* **`TST-CON-046.3` (`ANTBikeRadarDeviceTest.kt`)**: Verify `publishTargets`, `onWatchdogTimeout`, and `shutDown` preserve `isAutoMode == false`, and `setAutoMode()` restores `true`.
* **`TST-CON-046.4` (`RadarStateRepositoryTest.kt`)**: Verify `sourceStates` failover (`BLE` -> `ANT_PLUS` / `SIMULATOR`) preserves `isAutoMode == false` and `audioSnoozeState`, while `RadarStateRepository.reset()` isolates cleanly to default `RadarState()`.
* **`TST-CON-046.5` (`RadarLightControllerTest.kt`)**: Verify manual override (`setManualLightMode`, `cycleNextMode`) vs adaptive automation (`SmartRadarLightEngine`) under incoming frames and explicit `setAutoMode()` restoration, plus full radar suite regression.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-046.1` | Unit | `BTLERadarDeviceTest`: 6 emission sites preserve `isAutoMode == false` + restore | `REQ-CON-046.1` | Planned |
| `TST-CON-046.2` | Unit | `RadarTrafficSimulatorTest`: `generateFrame`/`tick`/`start`/`stop` preserve `isAutoMode` | `REQ-CON-046.1` | Planned |
| `TST-CON-046.3` | Unit | `ANTBikeRadarDeviceTest`: 3 emission sites preserve `isAutoMode == false` + restore | `REQ-CON-046.1` | Planned |
| `TST-CON-046.4` | Integration | `RadarStateRepositoryTest`: `sourceStates` failover & `reset()` isolation | `REQ-CON-046.2`, `REQ-CON-046.3` | Planned |
| `TST-CON-046.5` | Integration | `RadarLightControllerTest`: manual override vs adaptive automation & regression | `REQ-CON-046.2`, `REQ-CON-031.8` | Planned |
