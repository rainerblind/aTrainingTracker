# Stage 3: Implementation Plan - ATT-7546: Fix ANT+ Bike Radar Cold-Start Channel Acquisition Race

**Ticket**: [ATT-7546](https://rainerblind.atlassian.net/browse/ATT-7546)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-CON-037` (`REQ-CON-037.1`..`REQ-CON-037.4`)  
**Test Mapping**: `TST-CON-037` (`TST-CON-037.1`..`TST-CON-037.5`)  
**Branch**: `feat/ATT-7546`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
When an external USB ANT+ stick is plugged in, `AntRadioService` initializes `UsbAntService` asynchronously on a background executor. Consequently, `onServiceConnected()` in `aTrainingTracker` triggers before channels are available. Currently, `ANTSearchForNewDevicesEngineMultiDeviceSearch` and `ANTBikeRadarDevice` throw `ChannelNotAvailableException` (`SERVICE_INITIALIZING` / `NO_ADAPTERS_EXIST`) and permanently abort without state observation or retry.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-CON-037` (*ANT+ Bike Radar Cold-Start Channel Acquisition and UsbAntService Race Resilience*)
* **Test Mapping**: `TST-CON-037` (*ANT+ Bike Radar Cold-Start Channel Acquisition and Race Resilience Test Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Predefined Network Slot 1 Isolation**: Channel parameters (RF 57, period 4084, device type 40) remain unchanged.
2. **Deterministic Lifecycle Teardown**: Broadcast receivers and handlers are unregistered in `stopAsyncSearch()`, `shutDown()`, and `onServiceDisconnected()`.
3. **Security Non-Reliance**: Broadcast intent triggers an authenticated query to `provider.getNumChannelsAvailable()`; intent extras are never trusted for sensitive state.
4. **Parent Human Gate Invariance**: Terminal ticket progression remains reserved for human approval.

---

## 4. Proposed Architectural Changes

### Component 1: `AntChannelHelper.kt`
* Add `AcquisitionCancellationHandle` and `ChannelAcquisitionListener` interfaces.
* Implement `acquireAntPlusChannelAsync(context, provider, handler, maxRetries, retryIntervalMs, overallTimeoutMs, listener): AcquisitionCancellationHandle`.
* Immediate synchronous attempt; on `SERVICE_INITIALIZING` / `NO_ADAPTERS_EXIST` / `numChannelsAvailable == 0`, register `BroadcastReceiver` (`ACTION_CHANNEL_PROVIDER_STATE_CHANGED`, `RECEIVER_EXPORTED`) + 1000ms retry backoff (5 retries, 10s timeout).

### Component 2: `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`
* Wire `mRadarServiceConnection.onServiceConnected()` to `acquireAntPlusChannelAsync`.
* Add `mChannelAcquisitionHandle` field; cancel in `stopAsyncSearch()` and `onServiceDisconnected()`.

### Component 3: `ANTBikeRadarDevice.kt`
* Wire `serviceConnection.onServiceConnected()` to `acquireAntPlusChannelAsync`.
* Add `channelAcquisitionHandle` field; cancel in `shutDown()`, `onWatchdogTimeout()`, and `onServiceDisconnected()`.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Shared Asynchronous Acquisition Engine in `AntChannelHelper.kt` (`REQ-CON-037.1`, `REQ-CON-037.2`, `REQ-CON-037.4`)
* Add `AcquisitionCancellationHandle`, `ChannelAcquisitionListener`, and `acquireAntPlusChannelAsync`.
* Handle immediate success, `ACTION_CHANNEL_PROVIDER_STATE_CHANGED` receiver, bounded retry runnable, and 10s timeout.

### Step 2: Wire `ANTSearchForNewDevicesEngineMultiDeviceSearch.java` (`REQ-CON-037.3`)
* Replace synchronous acquisition with `acquireAntPlusChannelAsync`.
* Cancel pending handle in `stopAsyncSearch()` and `onServiceDisconnected()`.

### Step 3: Wire `ANTBikeRadarDevice.kt` (`REQ-CON-037.3`)
* Replace synchronous acquisition with `acquireAntPlusChannelAsync`.
* Cancel pending handle in `shutDown()`, `onWatchdogTimeout()`, and `onServiceDisconnected()`.

### Step 4: Unit Test Suite Construction (`TST-CON-037.1`..`TST-CON-037.5`)
* Implement `AntChannelHelperTest.kt` verifying immediate acquisition, broadcast trigger, retry fallback, timeout, and cancellation.
* Update `ANTBikeRadarDeviceTest.kt` and `ANTSearchForNewDevicesEngineMultiDeviceSearchTest.kt`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ant_plus.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted tests under `com.atrainingtracker.banalservice.devices.ant_plus.*` and `verify_requirement_governance.py`.
* **Rollback**: Clean branch isolation on `feat/ATT-7546` allows revert without affecting `sprint/2026-46.6`.
