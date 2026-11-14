# Stage 3: Implementation Plan - ATT-7547: Fix ANT+ Bike Radar Discovery Scan Timeout, Channel Events, and ExtendedData Parsing

**Ticket**: [ATT-7547](https://rainerblind.atlassian.net/browse/ATT-7547)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-CON-038` (`REQ-CON-038.1`..`REQ-CON-038.5`)  
**Test Mapping**: `TST-CON-038` (`TST-CON-038.1`..`TST-CON-038.5`)  
**Branch**: `feat/ATT-7547`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
Even after acquiring an ANT+ channel on predefined network slot 1, ANT+ Bike Radar discovery and tracking channels suffer from silent search death after 25s (`RX_SEARCH_TIMEOUT` / `CHANNEL_CLOSED` unhandled), missing background scanning assignment, unhandled `AntCommandFailedException` when setting adapter-wide lib config, and lack of RSSI extraction from `DataMessage`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-CON-038` (*ANT+ Bike Radar Continuous Discovery, Channel Event Re-Opening & ExtendedData SDK Parsing*)
* **Test Mapping**: `TST-CON-038` (*ANT+ Bike Radar Continuous Discovery, Channel Event Re-Opening & ExtendedData Parsing Test Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Predefined Network Slot 1 Isolation**: Channel parameters (RF 57, period 4084, device type 40) remain unchanged.
2. **Search Lifecycle Bounding**: Discovery stops immediately when `stopAsyncSearch()` is invoked; `CHANNEL_EVENT` never re-opens closed channels when search is inactive.
3. **Multi-Channel Adapter Coexistence**: `AntCommandFailedException` on `setAdapterWideLibConfig()` is handled safely without disrupting existing open channels.
4. **Parent Human Gate Invariance**: Terminal ticket progression remains reserved for human approval.

---

## 4. Proposed Architectural Changes

### Component 1: `AntChannelHelper.kt`
* Add `ExtendedDeviceInfo(val deviceNumber: Int, val rssi: Int)`.
* Implement `safeSetAdapterWideLibConfig(channel, libConfig)` catching `AntCommandFailedException`.
* Implement `assignScanChannel(channel): Boolean` assigning `ExtendedAssignment(BACKGROUND_SCANNING)` if supported, falling back to basic slave.
* Implement `extractExtendedDeviceAndRssi(messageType, messageParcel): ExtendedDeviceInfo?` for uniform `BroadcastDataMessage` / `AcknowledgedDataMessage` handling.

### Component 2: `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`
* Use `AntChannelHelper.assignScanChannel(mRadarAntChannel)` and `AntChannelHelper.safeSetAdapterWideLibConfig(...)`.
* Handle `CHANNEL_EVENT`: re-open `mRadarAntChannel` on `RX_SEARCH_TIMEOUT` or `CHANNEL_CLOSED` while `mIsRadarSearchActive == true`.
* Forward real RSSI to `mCallbackInterface.onNewDeviceFound(...)`.

### Component 3: `ANTBikeRadarDevice.kt`
* Use `AntChannelHelper.safeSetAdapterWideLibConfig(channel)` in `configureChannel()`.
* In `channelEventHandler`, handle `CHANNEL_EVENT`: re-open `antChannel` on `RX_SEARCH_TIMEOUT` or `CHANNEL_CLOSED` while `isSearching == true`.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Helper Utilities in `AntChannelHelper.kt` (`REQ-CON-038.3`..`REQ-CON-038.5`)
* Add `ExtendedDeviceInfo`, `assignScanChannel`, `safeSetAdapterWideLibConfig`, and `extractExtendedDeviceAndRssi`.

### Step 2: Continuous Discovery & Parsing in `ANTSearchForNewDevicesEngineMultiDeviceSearch.java` (`REQ-CON-038.1`, `REQ-CON-038.3`, `REQ-CON-038.5`)
* Wire `assignScanChannel` and `safeSetAdapterWideLibConfig`.
* Add `CHANNEL_EVENT` re-open logic and propagate real RSSI.

### Step 3: Re-Open Tracking Channel in `ANTBikeRadarDevice.kt` (`REQ-CON-038.2`, `REQ-CON-038.4`)
* Wire `safeSetAdapterWideLibConfig` and `CHANNEL_EVENT` re-open while `isSearching == true`.

### Step 4: Unit Test Suite Construction (`TST-CON-038.1`..`TST-CON-038.5`)
* Add tests in `AntChannelHelperTest.kt`, `ANTSearchForNewDevicesEngineMultiDeviceSearchTest.kt`, and `ANTBikeRadarDeviceTest.kt`.
* Command: `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ant_plus.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests across `ant_plus.*` and `verify_requirement_governance.py`.
* **Rollback**: Clean branch isolation on `feat/ATT-7547` allows full revert without affecting `sprint/2026-46.6`.
