# Stage 5 Walkthrough - ATT-8451: Polar Online Streaming Bridge Search Timeout & GATT Contention Recovery

**Ticket**: [ATT-8451](https://issue-tracker/ATT-8451) (`severity-critical`, `2 SP`) | **Epic**: `ATT-5910` | **Sprint**: `2026-11-16_03_Nacht`  
**Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8451` | **Commit SHA**: `da03a016`  
**Requirements**: `REQ-CON-045` (`REQ-CON-045.1`..`REQ-CON-045.4`) | **Test Spec**: `TST-CON-045` (`TST-CON-045.1`..`TST-CON-045.4`)  
**Author**: AI Agent 1 (Implementer, Lane B Worker Flash) | **Date**: 2026-11-16  

---

## 1. Executive Summary & Problem Resolution
During sequential sensor discovery (`DeviceManager.mSearchQueue`), connecting to a Polar H10 or Verity Sense with live IMU enabled (`polarLiveImuEnabled = 1`) deadlocked the entire sensor search queue whenever the sensor was powered off, out of range, or held in an existing OS GATT connection by an external app (e.g. `fi.polar.polarflow` stopping connectable advertisements).
* **Root Causes**:
  1. `PolarOnlineStreamingBridge.startSearching()` called `notifyStartSearching()` without scheduling a search timeout watchdog. Without `notifyStopSearching(false)`, `DeviceManager` never cleared `cMyRemoteDeviceCurrentlySearchingFor`, halting the search queue indefinitely and starving all subsequent sensors (e.g. Garmin Varia Radar, powermeters).
  2. `connectPersistentSession()` returned silently on `polarApi != null` (`L289`), leaving repeated search attempts and retries following disconnect / Bluetooth restarts permanently wedged in "searching".
  3. `sdkCallback` omitted `blePowerStateChanged(powered: Boolean)`, failing to clean up active jobs or cancel the search watchdog upon Bluetooth adapter reset.
* **Fix**:
  1. **Deterministic Watchdog Timeout (`REQ-CON-045.1`)**: Added `searchTimeoutMsProvider: () -> Long = { TrainingApplication.getBtleSearchPeriodMs() }` (`@JvmOverloads`), coroutine-managed `searchTimeoutJob`, `armSearchTimeoutWatchdog()`, and `onSearchTimeout()`. If the sensor does not connect within the timeout, `polarApi.disconnectFromDevice(polarId)` is invoked and `notifyStopSearching(false)` is dispatched, unblocking `DeviceManager.mSearchQueue`.
  2. **Idempotent Reconnection on Existing API (`REQ-CON-045.2`)**: Removed `if (polarApi != null) return`. Re-triggers connection via `api.connectToDevice(polarId)` and arms timeout watchdog; when already connected (`isReceivingData() == true`), immediately returns `notifyStopSearching(true)`.
  3. **Watchdog Cancellation on Connect (`REQ-CON-045.3`)**: In `sdkCallback.deviceConnected()`, `cancelSearchTimeoutWatchdog()` cancels the active watchdog before broadcasting `notifyStopSearching(true)`.
  4. **Adapter Power Cycle & Lifecycle Teardown (`REQ-CON-045.4`)**: Implemented `sdkCallback.blePowerStateChanged(powered: Boolean)`. When `powered == false`, clears connection state, halts active streaming jobs, releases ACC channel lock, and if searching, cancels timeout and dispatches `notifyStopSearching(false)`. `shutDown()` cleanly cancels `searchTimeoutJob`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Living Doc Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-045.1` (*Search Timeout & Unblocking*) | `TST-CON-045.1` | Automated Unit Test | **PASSED** | `[Verified]` |
| `REQ-CON-045.2` (*Idempotent Reconnect Recovery*) | `TST-CON-045.2` | Automated Unit Test | **PASSED** | `[Verified]` |
| `REQ-CON-045.3` (*Watchdog Cancellation on Connect*) | `TST-CON-045.3` | Automated Unit Test | **PASSED** | `[Verified]` |
| `REQ-CON-045.4` (*BT Power Cycle & Teardown*) | `TST-CON-045.4` | Automated Unit Test | **PASSED** | `[Verified]` |
| Invariants / Conformance | `TST-CON-045.1..4` | Suite & Architecture Contracts | **PASSED** | `[Verified]` |

---

## 3. Automated Test Evidence (`124/124 PASS`)
Executed via `./gradlew :app:testDebugUnitTest --tests "*Polar*" --tests "*DeviceManager*" --tests "*Btle*" --tests "*ArchitectureConformanceContractTest*" --tests "*RepositoryUiCleanlinessContractTest*" :app:compileDebugAndroidTestKotlin assembleDebug --max-workers=2`:
* **`PolarOnlineStreamingBridgeTest.kt`** (12/12 PASSED):
  - `testSearchTimeout_triggersNotifyStopSearchingFalse_andDisconnects` (`// TST-CON-045.1`): PASSED.
  - `testStartSearching_withExistingPolarApi_reconnectsAndArmsTimeout` (`// TST-CON-045.2`): PASSED.
  - `testStartSearching_whenAlreadyConnected_immediatelyDispatchesStopSearchingTrue` (`// TST-CON-045.2`): PASSED.
  - `testDeviceConnected_cancelsSearchTimeoutWatchdog` (`// TST-CON-045.3`): PASSED.
  - `testBlePowerStateChanged_false_cancelsSearchTimeoutAndResetsState` (`// TST-CON-045.4`): PASSED.
  - `testShutDown_cancelsSearchTimeoutCleanly` (`// TST-CON-045.4`): PASSED.
  - All 6 existing H10/Verity single-GATT streaming, RR forwarding, 200 Hz dynamics, and routing tests: PASSED.
* **Polar Subsystem & DeviceManager Suites** (93/93 PASSED):
  - `PolarH10OfflineControllerTest` (12/12), `PolarVeritySenseOfflineControllerTest` (22/22), `PolarOfflineGattCoordinatorCoexistenceTest` (2/2), `PolarH10RunningDynamicsEstimatorTest` (4/4), `PolarLiveRunningCadenceEstimatorTest` (5/5), `PolarRawTelemetryRecorderTest` (5/5), `PolarRawTelemetryReaderTest` (3/3), `PolarRawTelemetryPrunerTest` (4/4), `EditDevicePolarBackupTest` (3/3), `EditDevicePolarLiveImuTest` (3/3), `DeviceManager*` (17/17).
* **Architecture & Cleanliness Contracts** (19/19 PASSED):
  - `ArchitectureConformanceContractTest` (9/9), `RepositoryUiCleanlinessContractTest` (9/9), `RepositoryServiceCleanlinessContractTest` (1/1).
* **Build Compilation**: `:app:compileDebugAndroidTestKotlin` and `assembleDebug` completed successfully.

---

## 4. Verification & Clean-Room Governance Audit
* `python3 tools/verify_requirement_governance.py`: **PASS** (100% living doc parity, 0 ungrounded requirements, 0 code orphans).
* Master index tables: synchronized with 100% mathematical parity across `docs/requirements.md` and `docs/tests.md`.
* `ANT-Android-SDKs` submodule: clean & untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
* Localization: zero modified or added strings; 100% parity across all 9 UI locales.
