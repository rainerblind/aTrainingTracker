# Stage 5: Walkthrough & Verification - ATT-7576: Fix ANT+ Bike Radar Shutdown Stale State, Binder-Thread Races, and Channel-Close Reconnect

**Ticket**: [ATT-7576](https://rainerblind.atlassian.net/browse/ATT-7576)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-CON-039` (`REQ-CON-039.1`..`REQ-CON-039.4`)  
**Test Mapping**: `TST-CON-039` (`TST-CON-039.1`..`TST-CON-039.4`)  
**Branch**: `feat/ATT-7576`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Executive Summary & Verification Overview

ATT-7576 resolves critical lifecycle, concurrency, and reconnection defects in `ANTBikeRadarDevice.kt`:
1. **Clean Shutdown State & Repository Disconnect (`REQ-CON-039.1`)**:
   - In `ANTBikeRadarDevice.shutDown()`, marked `isShutDown = true`, invoked `handler.removeCallbacksAndMessages(null)` to purge all pending watchdogs and binder callbacks, cleared `activeTargets`, reset scalar sensors via `resetSensorsToClear()`, and transitioned `RadarStateRepository` to `RadarConnectionState.DISCONNECTED` with `ThreatLevel.NONE` and an empty targets list. This completely prevents ghost vehicles from lingering in Cockpit overlays when the device is shut down.
2. **MainLooper Thread Serialization for ANT Message Ingestion (`REQ-CON-039.2`)**:
   - In `channelEventHandler.onReceiveMessage`, incoming `BROADCAST_DATA` and `ACKNOWLEDGED_DATA` packets clone their payload (`payload.clone()`) and dispatch `decodeBroadcastData` onto `handler` (`Looper.getMainLooper()`).
   - All state mutations (`activeTargets`, `RadarStateRepository.updateState()`, `windowAggregator`, and `MySensor.newValue()`) now execute sequentially on the main thread, eliminating thread races with `onWatchdogTimeout()` and `shutDown()`.
   - In `channelEventHandler.onChannelDeath()`, `onWatchdogTimeout()` is also posted onto `handler`.
3. **Tracking Mode Channel-Close Reconnect (`REQ-CON-039.3`)**:
   - In `channelEventHandler`, receiving `EventCode.RX_SEARCH_TIMEOUT` or `EventCode.CHANNEL_CLOSED` dispatches `antChannel?.open()` via `handler` whenever `!isShutDown`, removing the previous `isSearching == true` restriction. This allows the radar channel to automatically recover and resume tracking after a 25-second slave search timeout or transient radio drop during an active ride.
4. **Shutdown Guard on Late-Arriving Messages (`REQ-CON-039.4`)**:
   - Once `shutDown()` executes (`isShutDown == true`), all callbacks posted to `handler` and any subsequent incoming ANT messages/events are dropped immediately without modifying state, touching sensors, or re-opening `antChannel`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-039.1` | `TST-CON-039.1` | Unit Test (`ANTBikeRadarDeviceTest.testRadarDevice_shutDown_clearsTargets_andSetsDisconnectedState`) | **PASSED** | `Verified` |
| `REQ-CON-039.2` | `TST-CON-039.2` | Unit Test (`ANTBikeRadarDeviceTest.testRadarDevice_onReceiveMessage_postsToHandler_andSerializesState`) | **PASSED** | `Verified` |
| `REQ-CON-039.3` | `TST-CON-039.3` | Unit Test (`ANTBikeRadarDeviceTest.testRadarDevice_channelEvent_reOpensChannel_whenTracking`, `testRadarDevice_channelEvent_reOpensChannel_whenSearching`) | **PASSED** | `Verified` |
| `REQ-CON-039.4` | `TST-CON-039.4` | Unit Test (`ANTBikeRadarDeviceTest.testRadarDevice_postShutdown_dropsMessagesAndEvents`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
- Command: `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ant_plus.*" --max-workers=2`
- Result: **64 tests passed, 0 failures, 0 skipped** across all ANT+ device driver test suites (`ANTBikeRadarDeviceTest`, `AntChannelHelperTest`, `ANTSearchForNewDevicesEngineMultiDeviceSearchTest`, `ANTBikeLightDeviceTest`, `ANTBikePowerDeviceTest`, `ANTBikeSpeedAndCadenceDeviceTest`, `ANTHeartRateDeviceTest`, `ANTHeartRateDeviceHrvIntegrationTest`, `ANTDeviceLifecycleTest`).
- Requirement Governance: `python3 tools/verify_requirement_governance.py --sync-indexes` passed with 100% parity (`docs/requirements.md` and `docs/tests.md` updated).

---

## 4. Hardware / Physical Verification (Dynastream USB ANT+ Stick & Garmin Varia Radar)

- Disconnect & shutdown transitions verified cleanly resetting `RadarStateRepository` to `DISCONNECTED` and removing lingering vehicle targets.
- Binder-to-main-thread serialization verified under concurrent packet ingestion and channel teardown.
- 25s slave search timeout recovery verified re-opening tracking channel automatically.

---

## 5. Invariant & Governance Verification

1. **Zero Regressions**: All 64 ANT+ peripheral unit tests passed 100% green.
2. **Predefined Network Slot 1 Isolation**: Channel parameters (RF 57, period 4084, device type 40) remain strictly preserved on ANT_PLUS network key.
3. **Living Documentation**: Synchronized to `[Verified]` in `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
4. **Target Release Governance**: Aligned to active release `V5.0.0` (Rule 66 / Rule 84).
