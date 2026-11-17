# Stage 1 Analysis: ATT-8451 — PolarOnlineStreamingBridge Search Timeout & GATT Contention Recovery

**Ticket**: [ATT-8451](https://issue-tracker/ATT-8451) (`2 SP`) | **Parent Epic**: `ATT-5910` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-16_03_Nacht` | **Branch**: `feature/ATT-8451` | **Author**: `agent1` (`subagent_pro`)  

## 1. Problem Statement & Motivation
- During sequential sensor search (`DeviceManager.mSearchQueue`, `DeviceManager.java:750-786`), `PolarOnlineStreamingBridge.startSearching()` calls `notifyStartSearching()` without scheduling a search timeout.
- When a sensor is powered off, out of range (e.g. Polar H10), or held in an existing OS GATT connection by an external app (e.g. `fi.polar.polarflow` on Polar Verity Sense after BT restart), Polar SDK's internal advertisement scan (`BleDeviceListener.search(true)`) never receives connectable advertisements.
- Without a timeout, `notifyStopSearching(false)` is never broadcast, locking `cMyRemoteDeviceCurrentlySearchingFor` and halting `mSearchQueue` indefinitely—blocking all subsequent sensors (e.g. Garmin Varia Radar, powermeters, cadence sensors).
- Additionally, `connectPersistentSession()` returns silently when `polarApi != null`, leaving repeated search triggers and post-disconnect / post-BT-restart attempts permanently stuck in "searching" state.

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Primary Root Causes**:
  1. *Missing Search Timeout*: Unlike `MyBTLEDevice` (which enforces `TrainingApplication.getBtleSearchPeriodMs()` via `mSearchTimeoutRunnable`), `PolarOnlineStreamingBridge` lacks any search watchdog, never terminating failed scans.
  2. *Silent Exit on Cached `polarApi`*: `connectPersistentSession()` executes `if (polarApi != null) return` (`L289`) after `notifyStartSearching()` was already dispatched. On repeated searches or after `deviceDisconnected`, no connection is initiated and the search queue deadlocks.
  3. *Unobserved Bluetooth Adapter Power Cycles*: `sdkCallback` does not implement `blePowerStateChanged(Boolean)`, leaving invalid session state and active coroutine jobs unpruned when the Bluetooth radio toggles off/on.
  4. *GATT Contention Advertisement Starvation*: When `fi.polar.polarflow` or another client holds an active OS GATT link, the peripheral stops BLE advertising. Polar SDK waits indefinitely for advertisement packets rather than aborting or resolving via direct MAC connection.
- **Independent Peer Review & Verification (4 Critical Findings)**:
  1. *Queue Blockade Cascade*: Auditing `DeviceManager.java:138-163` confirms `cMyRemoteDeviceCurrentlySearchingFor` is cleared and `searchForNextRemoteDevice()` invoked strictly upon `SEARCHING_STOPPED_FOR_ONE_INTENT`. If the bridge fails to broadcast `notifyStopSearching(false)`, the entire queue stalls forever.
  2. *State Consistency on Timeout*: On search timeout, if `!isReceivingData()`, the bridge must call `api.disconnectFromDevice(polarId)` to abort Polar SDK's internal scan coroutine, cancel any pending timeout job, and invoke `notifyStopSearching(false)`.
  3. *Reconnection Guard & Idempotency*: If `polarApi != null` when `startSearching()` is invoked: if already connected, immediately emit `notifyStopSearching(true)`; if disconnected, re-trigger `api.connectToDevice(polarId)` (or re-instantiate if the SDK session is dead) and start the timeout timer.
  4. *Submodule Protection*: Zero edits in `ANT-Android-SDKs` (`0e8cea71`).

## 3. Scope Grounding & Non-Goals
- **In-Scope**: Deterministic search timeout (`TrainingApplication.getBtleSearchPeriodMs()`) calling `notifyStopSearching(false)` and canceling ongoing scan attempts; idempotent `startSearching()` handling existing `polarApi` instances; `blePowerStateChanged` lifecycle handling; clean session teardown on timeout/disconnect; unit tests in `PolarOnlineStreamingBridgeTest`.
- **Out-of-Scope**: Altering Polar BLE SDK internal implementation (`polar-ble-sdk:8.6.0`); modifying standard `BTLEHeartRateDevice` search logic; modifying `ANT-Android-SDKs`.
- **Stage 4 Risk Classification (`Rule 7.1`)**: **HIGH-RISK** (Sensor search concurrency, BLE GATT arbitration, Bluetooth adapter state transitions, `severity-critical` queue deadlock).

## 4. Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: `REQ-SET-112.2` (`PolarOnlineStreamingBridge.kt`), `REQ-CON-004` (`DeviceManager.java`).
- **Historical Origin & Commit Trace**: `ATT-8016` (`commit d9f9a263`, persistent single-GATT Polar live IMU streaming bridge); `ATT-8019` (`commit 11778211`, raw telemetry recorder).
- **Root Reason for Existing Formulation**: `if (polarApi != null) return` was intended to avoid duplicate `PolarBleApi` allocations across start/stop tracking cycles, but overlooked that `startSearching()` needs to actively re-trigger connection if disconnected. Timeout was omitted because initial implementation focused on live streaming data paths rather than out-of-range / multi-app contention failure modes.
- **Preservation of Core Invariants**: Standard `BTLEHeartRateDevice` fallback when live IMU is disabled or `< API 33` remains untouched; `CADENCE`/`STRIDES` arbitration priority (`REQ-SET-112.4`) remains strictly preserved; offline session sharing via `PolarOfflineGattCoordinator` (`REQ-SET-112.3`) remains intact.

## 5. Change-Request Impact Analysis (SUP.10)
- **Affected Requirements**: Net-new `REQ-CON-045` / `TST-CON-045` (Polar Streaming Bridge Search Timeout & GATT Contention Recovery, extending `REQ-SET-112.2` & `REQ-CON-004`).
- **Affected Architecture Elements**: `PolarOnlineStreamingBridge.kt`, `DeviceManager.java` (unmodified consumer), `PolarOnlineStreamingBridgeTest.kt`.
- **Verification Impact & Test Surface**: Robolectric unit tests in `PolarOnlineStreamingBridgeTest` verifying (1) search timeout triggers `notifyStopSearching(false)`, (2) repeated `startSearching()` with existing `polarApi` re-triggers connection and timeout, (3) `blePowerStateChanged(false)` resets state and aborts pending searches, and (4) connected sensor emits `notifyStopSearching(true)` and cancels timeout.
- **Risk Assessment & Invariants**: Concurrency race between search timeout firing and `deviceConnected` callback arriving simultaneously is mitigated via synchronized / atomic state checks.

## 6. System Invariants & Risk Assessment
- **Core Invariants**: `TrainingApplication.getBtleSearchPeriodMs()` remains source of truth for search duration; `mSearchQueue` always progresses; zero modifications to `ANT-Android-SDKs`; `API < 33` isolated.
- **Risk Rating**: **HIGH** (`severity-critical` deadlock potential in peripheral search pipeline, multi-threaded BLE event dispatch).
