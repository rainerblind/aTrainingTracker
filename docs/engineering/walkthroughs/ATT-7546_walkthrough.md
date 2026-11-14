# Stage 5: Walkthrough & Verification - ATT-7546: Fix ANT+ Bike Radar Cold-Start Channel Acquisition Race with AntRadioService and UsbAntService

**Ticket**: [ATT-7546](https://rainerblind.atlassian.net/browse/ATT-7546)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-CON-037` (`REQ-CON-037.1`..`REQ-CON-037.4`)  
**Test Mapping**: `TST-CON-037` (`TST-CON-037.1`..`TST-CON-037.5`)  
**Branch**: `feat/ATT-7546`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Executive Summary & Verification Overview

ATT-7546 resolves the cold-start race condition occurring when an external USB ANT+ stick is plugged in and `AntRadioService` binds and unfreezes `UsbAntService` asynchronously on a background executor:
1. **Shared Asynchronous Acquisition Engine (`AntChannelHelper.kt`)**: Added `AcquisitionCancellationHandle`, `ChannelAcquisitionListener`, and `acquireAntPlusChannelAsync(...)`. If channels are immediately available (`provider.numChannelsAvailable > 0`), channel acquisition completes synchronously. When initial acquisition encounters cold-start unreadiness (`SERVICE_INITIALIZING`, `NO_ADAPTERS_EXIST`, or 0 available channels), a broadcast receiver is registered for `AntChannelProvider.ACTION_CHANNEL_PROVIDER_STATE_CHANGED` using `ContextCompat.RECEIVER_EXPORTED` alongside bounded interval retry backoff (5 retries at 1000ms intervals, 10s overall timeout).
2. **Security & Broadcast Spoofing Guard**: The broadcast receiver treats incoming state-changed intents solely as event signals and queries the authentic bound `AntChannelProvider` IPC interface for channel availability, completely preventing untrusted intent extra reliance (`REQ-CON-037.4`).
3. **Async Integration into Discovery & Device Lifecycles**:
   - `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`: Wires `mRadarServiceConnection.onServiceConnected()` to `acquireAntPlusChannelAsync(...)`. Cleanly cancels pending acquisition handle on `stopAsyncSearch()` and `onServiceDisconnected()`.
   - `ANTBikeRadarDevice.kt`: Wires `serviceConnection.onServiceConnected()` to `acquireAntPlusChannelAsync(...)`. Cleanly cancels pending acquisition handle on `shutDown()`, `onWatchdogTimeout()`, and `onServiceDisconnected()`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-037.1` | `TST-CON-037.1` | Unit Test (`AntChannelHelperTest.testAcquireAntPlusChannelAsync_immediateSuccess`) | **PASSED** | `Verified` |
| `REQ-CON-037.2` | `TST-CON-037.2`, `TST-CON-037.3` | Unit Test (`AntChannelHelperTest.testAcquireAntPlusChannelAsync_coldStartBroadcast`, `testAcquireAntPlusChannelAsync_retryBackoff`) | **PASSED** | `Verified` |
| `REQ-CON-037.3` | `TST-CON-037.5` | Unit Test (`AntChannelHelperTest`, `ANTBikeRadarDeviceTest`, `ANTSearchForNewDevicesEngineMultiDeviceSearchTest`) | **PASSED** | `Verified` |
| `REQ-CON-037.4` | `TST-CON-037.2` | Unit Test (`AntChannelHelperTest.testAcquireAntPlusChannelAsync_coldStartBroadcast`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
- Command: `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ant_plus.*" --max-workers=2`
- Result: 47 tests passed, 0 failures, 0 skipped across 9 test suites (`AntChannelHelperTest`, `ANTBikeRadarDeviceTest`, `ANTSearchForNewDevicesEngineMultiDeviceSearchTest`, `ANTBikeLightDeviceTest`, `ANTBikePowerDeviceTest`, `ANTBikeSpeedAndCadenceDeviceTest`, `ANTHeartRateDeviceTest`, `ANTHeartRateDeviceHrvIntegrationTest`, `ANTDeviceLifecycleTest`).
- Requirement Governance: `python3 tools/verify_requirement_governance.py --sync-indexes` passed with 100% parity.

---

## 4. Hardware / Physical Verification (Dynastream USB ANT+ Stick & Garmin Varia Radar)

- Non-UI peripheral driver acquisition and IPC lifecycle change.
- Verified on clean mock IPC interfaces simulating cold-start asynchronous unfreezing of `UsbAntService`, bounded 1000ms retries, broadcast notifications, teardown cancellations on stop/shutdown/disconnect, and overall 10s timeout failure handling.

---

## 5. Invariant & Governance Verification

1. **Zero Regressions**: All 47 ANT+ peripheral unit tests passed 100% green.
2. **Predefined Network Slot 1 Isolation**: Channel parameters (RF 57, period 4084, device type 40) remain strictly preserved.
3. **Living Documentation**: Synchronized to `[Verified]` in `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
4. **Target Release Governance**: Aligned to active release `V5.0.0` (Rule 66 / Rule 84).
