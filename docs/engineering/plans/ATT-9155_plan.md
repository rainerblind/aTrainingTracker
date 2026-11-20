# Stage 3 Implementation Plan: ATT-9155 - Fix BLE HRM Silent Connection Without HR Data and Lingering GATT/Polar Session Blocking Rediscovery After Unpair

**Ticket**: [ATT-9155](https://rainerblind.atlassian.net/browse/ATT-9155) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-20_02_Spät` | **Branch**: `feature/ATT-9155`  
**Requirement**: `REQ-CON-054` (`REQ-CON-054.1`..`.4`, refining `SYSREQ-TRK-001`, `SYSREQ-TRK-002`) | **Test Spec**: `TST-CON-054` (`TST-CON-054.1`..`.4`)  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-20

---

## 1. Architecture, Threading & Target LoC Budget (SWE.2)

| File | Current LoC | Budget / Cap | Action | Requirement |
| :--- | :---: | :---: | :--- | :--- |
| `BleHrmSilentConnectionWatchdog.kt` *(new)* | `0` | `<= 90` | Pure-JVM silent-HR watchdog state machine (`12 s` default, injectable) reused by `BTLEHeartRateDevice` & `PolarOnlineStreamingBridge` | `REQ-CON-054.3` |
| `PolarOfflineGattCoordinator.kt` | `1,020` | `<= 1,120 / 1,500` | Add `forceDisconnectDevice()` & `disconnectUnpairedSessions()` (injectable paired-set lookup, sibling-safe alias teardown) | `REQ-CON-054.1`, `.2` |
| `PolarPmdStreamCoordinator.kt` | `300` | `<= 325 / 1,500` | Add single-slot `cancelSlot(slot)` so silent-HR watchdog can cleanly re-launch `StreamSlot.HR` without touching sibling slots | `REQ-CON-054.3` |
| `PolarOnlineStreamingBridge.kt` | `1,259` | `<= 1,340 / 1,500` | Multi-alias `forceDisconnectDevice()` in `shutDown()`; wire `BleHrmSilentConnectionWatchdog` (`12 s` injectable) for HR re-subscribe -> clean reconnect | `REQ-CON-054.1`, `.3`, `.4` |
| `MyBTLEDevice.java` | `1,274` | `<= 1,310 / 1,500` | Null-safe same-looper sync close in `disconnectFromGatt()`; strict `mIsShutDown == true` late `STATE_CONNECTED` close; protected `triggerSilentConnectionReconnect()` hook | `REQ-CON-054.1`, `.3` |
| `BTLEHeartRateDevice.java` | `361` | `<= 470 / 1,500` | Wire `BleHrmSilentConnectionWatchdog` (`12 s` injectable), `0x2A37` CCCD failure single retry, `isGattPaused()` deferral, `PolarOfflineGattCoordinator.forceDisconnectDevice()` in `shutDown()` | `REQ-CON-054.1`, `.3` |
| `BTSearchForNewDevicesEngine.java` | `573` | `<= 670 / 1,500` | Retain `BluetoothManager`, release unpaired Polar SDK sessions for `HRM`, probe `getConnectedDevices(GATT)` skipping paired DB rows & validating target UUID strictly on `mConnectedGattProbes` | `REQ-CON-054.2` |
| `DeviceDataRepository.kt` | `770` | `<= 795 / 1,500` | Invoke `PolarOfflineGattCoordinator.forceDisconnectDevice()` on unpair (`isPaired: true -> false`) and pre-delete MAC capture in `deleteDevice()` | `REQ-CON-054.1` |
| `DeviceManager.java` | `1,707` | **`1,707 / 1,708` (`+0` LoC)** | Replace `L925` in-place (`+0` net LoC) with 1-line `PolarOfflineGattCoordinator.forceDisconnectDevice(mContext, mDeviceDataRepository.getBluetoothMACAddress(deviceId), null);` | `REQ-CON-054.1`, `.4` |
| `TrackerService.java`, `DevicesDatabaseManager.java` | protected | **`0` edits** | Untouched protected monoliths | `REQ-CON-054.4` |

- **Threading**: `MyBTLEDevice` & `BTLEHeartRateDevice` watchdog/GATT callbacks execute on `mHandler` (`MainLooper`) guarded by `mGattLock`; `PolarOnlineStreamingBridge` watchdog runs on injected `dispatcher` (`Dispatchers.IO` / `StandardTestDispatcher`); `PolarOfflineGattCoordinator` collections remain lock-free `ConcurrentHashMap` / `apiLock`-guarded.
- **UI Consistency & Localization**: `N/A - no new or modified UI screens, Compose components, or strings` (`0` XML edits).
- **SQLite**: Zero schema changes (`0` edits to `DevicesDatabaseManager.java`).

---

## 2. Atomic Implementation Steps

### Step 1: Pure-JVM `BleHrmSilentConnectionWatchdog.kt` (`REQ-CON-054.3` / `TST-CON-054.3`)
- Create `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BleHrmSilentConnectionWatchdog.kt` (`<= 90` LoC):
  - `const val DEFAULT_WATCHDOG_TIMEOUT_MS = 12_000L`
  - `enum class State { IDLE, ARMED_INITIAL, ARMED_AFTER_RESUBSCRIBE, DISARMED, RECOVERING }`
  - `enum class Action { NONE, REENABLE_CCCD, DISCONNECT_AND_RECONNECT }`
  - Thread-safe (`@Synchronized`) transitions: `armOnConnected()` -> `ARMED_INITIAL`, `onHrPacketReceived()` -> `DISARMED`, `onWatchdogElapsed()` (`ARMED_INITIAL` -> `ARMED_AFTER_RESUBSCRIBE` returning `REENABLE_CCCD`; `ARMED_AFTER_RESUBSCRIBE` -> `RECOVERING` returning `DISCONNECT_AND_RECONNECT`; otherwise `NONE`), `reset()` -> `IDLE`.
- **Unit Test**: Create `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BleHrmSilentConnectionWatchdogTest.kt` (`// REQ-CON-054.3 / TST-CON-054.3`).

### Step 2: Alias-Complete Polar Teardown (`PolarOfflineGattCoordinator.kt`, `DeviceDataRepository.kt`, `DeviceManager.java`) (`REQ-CON-054.1`, `.4` / `TST-CON-054.1`)
- In `PolarOfflineGattCoordinator.kt`:
  - Add `@JvmStatic @JvmOverloads fun forceDisconnectDevice(context: Context?, macAddress: String?, polarId: String? = null)`: resolves all known aliases (`macAddress`, `polarId`, persisted canonical Polar ID from `PolarOfflineCapabilityStore.getPolarDeviceId(context, macAddress)`, plus transitive `deviceAliases`), clears `connectedDeviceIds`, `activeAccOwners`, and matching `activeSessions` / `activeSessionCallbacks` / `callbackListeners` ONLY for those target aliases, and invokes `api.disconnectFromDevice(alias)` (defensively catching `Throwable`) for each distinct alias on `sharedApi` (and any distinct session API) WITHOUT calling `api.shutDown()` or disturbing sibling paired Polar devices (`REQ-SET-118.3`).
  - Add `@JvmStatic @JvmOverloads fun disconnectUnpairedSessions(context: Context, pairedIdentifiersProvider: (() -> Set<String>)? = null)`: snapshots `connectedDeviceIds` + `activeSessions.keys`, determines paired identifiers (defaulting to paired BLE HRM MACs + persisted Polar IDs from `DevicesDatabaseManager`), and invokes `forceDisconnectDevice(context, id, null)` strictly for connected IDs not matching any paired identifier or alias.
- In `DeviceDataRepository.kt`:
  - In `updateDevice()`: when `originalState.isPaired && !finalState.isPaired`, invoke `PolarOfflineGattCoordinator.forceDisconnectDevice(application, devicesDatabaseManager.getBluetoothMACAddress(finalState.id), null)`.
  - In `deleteDevice(deviceId)`: capture `val mac = devicesDatabaseManager.getBluetoothMACAddress(deviceId)` **before** `devicesDatabaseManager.deleteDevice(deviceId)`, send `PAIRING_CHANGED(false)`, and invoke `PolarOfflineGattCoordinator.forceDisconnectDevice(application, mac, null)`.
- In `DeviceManager.java:925`: replace the single `Log.d(TAG, "BUG: an unpaired device became unpaired");` line in-place (`+0` net LoC -> stays `1,707 / 1,708`) with `PolarOfflineGattCoordinator.forceDisconnectDevice(mContext, mDeviceDataRepository.getBluetoothMACAddress(deviceId), null);`.
- **Unit Tests**: Extend `PolarOfflineGattCoordinatorCoexistenceTest.kt` and `DeviceManagerTeardownTest.kt` (`// REQ-CON-054.1 / TST-CON-054.1`).

### Step 3: Raw GATT Teardown Hardening & Standard BLE HRM Watchdog (`MyBTLEDevice.java`, `BTLEHeartRateDevice.java`) (`REQ-CON-054.1`, `.3` / `TST-CON-054.1`, `.3`)
- In `MyBTLEDevice.java`:
  - In `disconnectFromGatt(@Nullable Runnable onGattClosed)`: compute `Looper handlerLooper = mHandler != null ? mHandler.getLooper() : null; boolean onHandlerThread = handlerLooper != null && handlerLooper == Looper.myLooper();` — if `onHandlerThread`, execute `closeRunnable.run()` synchronously; otherwise keep existing `mHandler.post(closeRunnable)` (100% safe for mocked-looper pure-JVM tests!).
  - In `onConnectionStateChange(gatt, status, newState)`: strictly when `mIsShutDown` is `true`, defensively disconnect & close `gatt` (permission-checked) and `return` immediately (preserving `MyBTLEDeviceLifecycleTest.kt:323` where `mIsShutDown == false && mBluetoothGatt == null`).
  - Add protected helper `triggerSilentConnectionReconnect()` that calls `unregisterSensors()`, `disconnectFromGatt()`, and (if `!mIsShutDown && !mIsGattPaused`) schedules `mReconnectRunnable` after `INITIAL_RECONNECT_DELAY_MS` with `mIsBackgroundReconnecting = true`.
- In `BTLEHeartRateDevice.java`:
  - Add `BleHrmSilentConnectionWatchdog mSilentHrWatchdog` + configurable `mSilentHrWatchdogTimeoutMs = BleHrmSilentConnectionWatchdog.DEFAULT_WATCHDOG_TIMEOUT_MS` + `mCccdFailureRetried` flag + `mSilentHrWatchdogRunnable`.
  - In `configureServices(gatt)`: keep `0x2A24`/`0x2A25` DIS reads enqueued BEFORE `super.configureServices(gatt)` (`REQ-CON-049` / `BTLEHeartRateDeviceTest.kt:561` invariant!), then arm `mSilentHrWatchdog` & schedule `mSilentHrWatchdogRunnable` after `mSilentHrWatchdogTimeoutMs`.
  - Override `onDescriptorWriteCompleted(gatt, descriptor, status)`: if `descriptor` is `0x2902` on `0x2A37` and `status != BluetoothGatt.GATT_SUCCESS && !mIsShutDown && !mCccdFailureRetried`, set `mCccdFailureRetried = true` and re-enqueue HR notification + CCCD write once.
  - In `parseHeartRateMeasurement(...)`: disarm `mSilentHrWatchdog` and cancel `mSilentHrWatchdogRunnable` on any valid HR packet (`value != null && value.length >= requiredLength`).
  - In `mSilentHrWatchdogRunnable`: if `mIsShutDown || mState != State.CONNECTED_WITH_SERVICE` return; if `isGattPaused()` re-post after `mSilentHrWatchdogTimeoutMs` (PFTP yield safety!); else consult `mSilentHrWatchdog.onWatchdogElapsed()`: on `REENABLE_CCCD`, re-call `gatt.setCharacteristicNotification(hrChar, true)` + `enqueueWriteDescriptor(hrCccd, ENABLE_NOTIFICATION_VALUE)` and re-post watchdog; on `DISCONNECT_AND_RECONNECT`, call `triggerSilentConnectionReconnect()`.
  - In `unregisterSensors()` & `shutDown()`: reset watchdog & remove callbacks; in `shutDown()`, also invoke `PolarOfflineGattCoordinator.forceDisconnectDevice(mContext, getAddress(), null)`.
- **Unit Tests**: Extend `MyBTLEDeviceLifecycleTest.kt` and `BTLEHeartRateDeviceTest.kt` (`// REQ-CON-054.1 / TST-CON-054.1`, `// REQ-CON-054.3 / TST-CON-054.3`).

### Step 4: Polar SDK Bridge Multi-Alias Teardown & Silent-HR Watchdog (`PolarPmdStreamCoordinator.kt`, `PolarOnlineStreamingBridge.kt`) (`REQ-CON-054.1`, `.3` / `TST-CON-054.1`, `.3`)
- In `PolarPmdStreamCoordinator.kt`: add `fun cancelSlot(slot: StreamSlot)` (atomically clears & returns single slot's job under `slotLock`, cancels outside `slotLock`).
- In `PolarOnlineStreamingBridge.kt`:
  - Add optional injectable `silentHrWatchdogTimeoutMs: Long = BleHrmSilentConnectionWatchdog.DEFAULT_WATCHDOG_TIMEOUT_MS` (default preserves all existing Java/Kotlin constructors via `@JvmOverloads` & `InterfaceSignatureDriftContractTest`!).
  - Arm `BleHrmSilentConnectionWatchdog` coroutine (`silentHrWatchdogJob`) in `markBridgeConnected(...)`; disarm & cancel in `processHrData(...)` when `data.samples.isNotEmpty()`; cancel/reset in `deviceDisconnected()`, `blePowerStateChanged(false)`, and `shutDown()`.
  - On `REENABLE_CCCD`: cancel `StreamSlot.HR` via `streamCoordinator.cancelSlot(StreamSlot.HR)`, re-invoke `startHrCollection()`, and re-arm for a second window.
  - On `DISCONNECT_AND_RECONNECT`: cancel active stream jobs, set `isConnectedFlag = false`, `unregisterSensors()`, invoke `PolarOfflineGattCoordinator.forceDisconnectDevice(mContext, macAddress, sdkSessionId)` across all aliases, and call `scheduleBackoffReconnect()`.
  - In `shutDown()`: invoke `PolarOfflineGattCoordinator.forceDisconnectDevice(mContext, macAddress, streamId)` (also passing `coordKey`/`polarId` via aliases) alongside existing `releasePersistentSession(...)` so every alias disconnects cleanly without breaking sibling paired Polar sensors.
- **Unit Tests**: Extend `PolarOnlineStreamingBridgeTest.kt` (`// REQ-CON-054.1 / TST-CON-054.1`, `// REQ-CON-054.3 / TST-CON-054.3`).

### Step 5: Connected-GATT Discovery Fallback (`BTSearchForNewDevicesEngine.java`) (`REQ-CON-054.2` / `TST-CON-054.2`)
- Retain `protected BluetoothManager mBluetoothManager;` in constructor and add `protected final Set<String> mConnectedGattProbes = new HashSet<>();` (cleared in `resetTrackingState()`).
- In `startAsyncSearch()`: if `mDeviceType == DeviceType.HRM`, invoke `PolarOfflineGattCoordinator.disconnectUnpairedSessions(mContext)`. After `scanner.startScan(...)`, call `probeAlreadyConnectedGattDevices()`:
  - Check `BLUETOOTH_CONNECT` permission (API 31+); defensively query `mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT)`.
  - For each non-null device with non-null address not already in `mBTGatts`: skip if already paired in `mDevicesDatabaseManager` (`isBluetoothDeviceInDB(mDeviceType, addr) && isPaired(getDeviceId(mDeviceType, addr))`); otherwise record `addr` in `mConnectedGattProbes` and connect via `connectGatt(mContext, false, mGattCallback, BluetoothDevice.TRANSPORT_LE)`.
- In `onServicesDiscovered()` / `readNextCharacteristic()`: strictly when `mConnectedGattProbes.contains(address)`, verify target service UUID presence (`BluetoothConstants.getServiceUUID(mDeviceType)`, plus `UUID_SERVICE_GARMIN_MEMBER` for `BIKE_RADAR`); if absent, close & remove that probe GATT handle without calling `newDeviceFound()` (leaving existing scan-path tests 100% untouched!).
- **Unit Tests**: Extend `BTSearchForNewDevicesEngineTest.kt` (`// REQ-CON-054.2 / TST-CON-054.2`).

### Step 6: SWE.3 Detailed Design, `Rule 81` System Traceability, Living Docs & Verification (`REQ-CON-054.4` / `TST-CON-054.4`)
- Update `docs/architecture/detailed_design/my_btle_device.md` (LoC, same-looper sync close, strict `mIsShutDown == true` late-callback close, silent-HR reconnect hook) and `docs/architecture/detailed_design/banal_service.md` (unpaired Polar session cleanup on `pairingChanged`, `1,707` LoC unchanged).
- Ground `BleHrmSilentConnectionWatchdog.kt` in `docs/system/sysreq_live_tracking.md` & `docs/system/system_software_traceability.md` (`Rule 81`).
- Promote `REQ-CON-054` & `TST-CON-054` to `[Implemented]` (keeping `docs/tests/tst_tracking_cockpit.md <= 3,975 / 4,000` lines!), run `--sync-indexes` & `verify_requirement_governance.py`, and run locked incremental Gradle verification (`--rerun`, NO `--rerun-tasks`, NO `-PstampGitHash=true`, `0` new compiler warnings):
  `BleHrmSilentConnectionWatchdogTest`, `BTLEHeartRateDeviceTest`, `MyBTLEDeviceLifecycleTest`, `MyBTLEDeviceQueueTest`, `MyBTLEDeviceAddressAndSearchTest`, `BLEReconnectRobustnessTest`, `BTSearchForNewDevicesEngineTest`, `PolarOfflineGattCoordinatorCoexistenceTest`, `PolarOnlineStreamingBridgeTest`, `PolarPmdStreamCoordinatorTest`, `DeviceManagerTeardownTest`, `DeviceManagerSearchQueueTest`, `DeviceManagerDiscoveryTest`, `ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest`, `DetailedDesignParityContractTest`, `DeadCodeEliminationContractTest`, `SystemLiveTrackingRequirementsContractTest`, `SourceTextReadingAuditContractTest`, plus `:app:assembleDebug`.

---

## 3. Invariants & Hygiene Checklist
- **Protected Monoliths**: `DeviceManager.java` exact `1,707 / 1,708` LoC (`+0` net LoC); `0` edits to `TrackerService.java` & `DevicesDatabaseManager.java`; all edited Kotlin/Java files `<= 1,500` LoC.
- **Preserved Contracts**: `REQ-CON-049` DIS-before-HR-CCCD queue order (`BTLEHeartRateDeviceTest:561`), `MyBTLEDeviceLifecycleTest:323` non-shutdown null-GATT callback, `REQ-CON-050.1` ordered teardown callback, `REQ-SET-118.3` sibling Polar session isolation, `REQ-CON-053` 4-stream atomic coordination, `REQ-FIL-038.2` `deactivateSensor()` null clearing, `0` new `src/main` reads (`REQ-PRO-049.1`), `0` UI strings (`N/A`), and zero touches to user-owned dirty/untracked paths.
