# Stage 1 Analysis: ATT-7546 - Fix ANT+ Bike Radar Cold-Start Channel Acquisition Race with AntRadioService and UsbAntService

**Ticket**: [ATT-7546](https://rainerblind.atlassian.net/browse/ATT-7546)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Branch**: `feat/ATT-7546`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
When an external USB ANT+ stick (e.g., Dynastream ANT USBStick2) is plugged in, `AntService.bindService()` binds `AntRadioService`. Inside `AntRadioService`, `AdapterProvider` discovers and binds `UsbAntService` asynchronously on `mChipInitExecutor`. Consequently, `ServiceConnection.onServiceConnected()` in `aTrainingTracker` triggers before `UsbAntService` completes USB initialization.
Currently, `ANTSearchForNewDevicesEngineMultiDeviceSearch` (line 79) and `ANTBikeRadarDevice` (line 118) invoke `AntChannelHelper.acquireAntPlusChannel()` immediately. This fails with `ChannelNotAvailableException` (`SERVICE_INITIALIZING` / `NO_ADAPTERS_EXIST`), causing both classes to log a failure and abort permanently without listening for `AntChannelProvider.ACTION_CHANNEL_PROVIDER_STATE_CHANGED` or retrying.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * Inspected `ANTSearchForNewDevicesEngineMultiDeviceSearch.java:79`: Directly calls `AntChannelHelper.acquireAntPlusChannel(mContext, provider)` in `onServiceConnected()`. On exception, logs error and halts search (`mCallbackInterface.onSearchStopped()`) without retry or broadcast listener.
  * Inspected `ANTBikeRadarDevice.kt:118`: Directly calls `AntChannelHelper.acquireAntPlusChannel(mContext, provider)` in `onServiceConnected()`. On exception, calls `notifyStopSearching(false)` permanently.
  * Confirmed: Channel acquisition logic is duplicated across both classes and lacks state-change observation or retry backoff.
  * Security Review (`RECEIVER_EXPORTED`): `AntRadioService` (`com.dsi.ant.service.socket`) runs out-of-process. Android 14+ requires `ContextCompat.RECEIVER_EXPORTED` to receive `ACTION_CHANNEL_PROVIDER_STATE_CHANGED`. To prevent spoofing, the receiver treats broadcasts purely as an event trigger and queries the authenticated IPC provider directly (`provider.getNumChannelsAvailable() > 0`) before channel acquisition.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Extract shared asynchronous acquisition helper (`AntChannelHelper.acquireAntPlusChannelAsync`) with `AcquisitionCancellationHandle`.
  * Support `BroadcastReceiver` for `AntChannelProvider.ACTION_CHANNEL_PROVIDER_STATE_CHANGED` (with `ContextCompat.RECEIVER_EXPORTED`).
  * Implement bounded backoff: 5 retries spaced 1000ms apart with 10s overall timeout for `SERVICE_INITIALIZING` and `NO_ADAPTERS_EXIST`.
  * Unify channel acquisition in `ANTSearchForNewDevicesEngineMultiDeviceSearch` and `ANTBikeRadarDevice` with clean unregister on `stopAsyncSearch()`, `shutDown()`, and `onServiceDisconnected()`.
* **Out-of-Scope Non-Goals**:
  * BLE radar channels (`BTLERadarDevice`), standard ANT PCC sensors (which use `MultiDeviceSearch`), or modifying `ANTBikeLightDevice` handshake logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-032.1` & `REQ-CON-032.5` (`docs/requirements/req_tracking_cockpit.md`).
* **Historical Origin & Commit Trace**: Introduced in ATT-5084 for ANT+ Bike Radar channel acquisition and wildcard discovery.
* **Root Reason for Existing Formulation**: Assumed synchronous channel availability upon `onServiceConnected()` (valid for built-in SOC ANT chips, but races with USB ANT+ stick asynchronous driver initialization).
* **Preservation of Core Invariants**: Predefined Network Slot 1 (`ANT_PLUS`) isolation, channel parameters (4084 period, RF 57, device type 40), and starvation watchdog remain 100% intact.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-CON-032.1` (channel acquisition resilience) and `REQ-CON-032.5` (wildcard search resilience).
* **Affected Architecture Elements**: `AntChannelHelper.kt`, `ANTBikeRadarDevice.kt`, `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`.
* **Verification Impact & Test Surface**: `AntChannelHelperTest.kt`, `ANTBikeRadarDeviceTest.kt`, `ANTSearchForNewDevicesEngineMultiDeviceSearchTest.kt`.
* **Risk Assessment & Invariants**: Low risk. Thread-safe execution via MainLooper Handler; fail-safe unregistration prevents receiver leaks; bounded timeout prevents hanging searches.

---

## 6. Architectural Strategy & High-Level Solution
* Add `AntChannelHelper.acquireAntPlusChannelAsync(context, provider, handler, listener): CancellationHandle`.
* Immediate synchronous attempt; on `SERVICE_INITIALIZING` or `NO_ADAPTERS_EXIST` or `getNumChannelsAvailable() == 0`, register `BroadcastReceiver` and start 1000ms interval timer (max 5 retries, 10s timeout).
* Call `listener.onChannelAcquired(channel)` upon success, or `listener.onAcquisitionFailed(exception)` on timeout/fatal error.
* Call `CancellationHandle.cancel()` on teardown to unregister receiver and remove handler callbacks.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in existing ANT+ sensors or BLE radar.
  2. Thread safety on main looper callbacks; clean receiver unregistration on disconnect.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** — Self-contained connection layer enhancement with bounded timeouts and fail-safe unregistration.
