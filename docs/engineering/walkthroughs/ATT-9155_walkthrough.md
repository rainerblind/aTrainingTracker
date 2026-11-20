# Stage 5 Verification & Walkthrough: ATT-9155 - Fix BLE HRM Silent Connection Without HR Data and Lingering GATT/Polar Session Blocking Rediscovery After Unpair

**Ticket**: [ATT-9155](https://rainerblind.atlassian.net/browse/ATT-9155) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-20_02_Spät` | **Branch**: `feature/ATT-9155`  
**Verified Production SHA**: `98a377f60539f4f814bef7acc11ee6154b7d87f1` (Gate 4 Bounce 1 `ATT-9351` PASS; `Rule 7.5` doc-only Stage 5 fast path with `0` `app/` drift: `git diff 98a377f6..HEAD -- app/` is empty)  
**Requirements / Test Spec**: `REQ-CON-054` (`REQ-CON-054.1`..`.4`, refining `SYSREQ-TRK-001` & `SYSREQ-TRK-002`) / `TST-CON-054` (`TST-CON-054.1`..`.4`) — `[Verified]`  
**Date**: 2026-11-20 | **Author**: AI Agent 1 (`--as agent1`)

---

## 1. Root Cause & Summary of Verified Changes
1. **Alias-Complete Unpair/Delete GATT & Polar Teardown (`REQ-CON-054.1`)**:
   - `PolarOfflineGattCoordinator.kt` (`1,160 / 1,500` LoC): Added `forceDisconnectDevice(context, macAddress, polarId, clearRegistrations = true)` and `disconnectUnpairedSessions(context, pairedIdentifiersProvider)` to disconnect every MAC and canonical 8-hex Polar ID alias on `sharedApi` without shutting down `sharedApi` or disturbing sibling paired Polar sensors (`REQ-SET-118.3`).
   - `PolarOnlineStreamingBridge.kt` (`1,382 / 1,500` LoC), `BTLEHeartRateDevice.java` (`491 / 1,500` LoC), `DeviceDataRepository.kt` (`777 / 1,500` LoC), and `DeviceManager.java` (`1,707 / 1,708` LoC, **`+0` net LoC**): Tear down lingering Polar/GATT sessions on `shutDown()`, unpair, pre-delete MAC capture in `deleteDevice()`, and `pairingChanged(id, false)` even when absent from `mMyRemoteDevices`.
   - `MyBTLEDevice.java` (`1,324 / 1,500` LoC): Executes `disconnectFromGatt()` synchronously when `onGattClosed == null && mHandler.getLooper() != null && mHandler.getLooper() == Looper.myLooper()` (null/exception-safe for pure-JVM tests; preserves `TST-CON-050.1` async callback ordering when `onGattClosed != null`); falls back to `closeRunnable.run()` synchronously when `mHandler.post(...)` returns `false` so `gattToClose.disconnect()`, `gattToClose.close()`, and `onGattClosed.run()` always run; immediately closes late `onConnectionStateChange` callbacks strictly when `mIsShutDown == true` (preserving `MyBTLEDeviceLifecycleTest:323`).
2. **Connected-GATT Rediscovery Fallback (`REQ-CON-054.2`)**:
   - `BTSearchForNewDevicesEngine.java` (`699 / 1,500` LoC): Releases unpaired Polar SDK sessions on `DeviceType.HRM` search and enumerates `BluetoothManager.getConnectedDevices(BluetoothProfile.GATT)` (permission-guarded), skipping already-paired DB sensors and strictly verifying target GATT service UUID presence + closing non-matching probes on `mConnectedGattProbes`.
3. **Shared `12 s` Silent-HR Watchdog, Generation Lock & CCCD Retry (`REQ-CON-054.3`)**:
   - Extracted pure-JVM `BleHrmSilentConnectionWatchdog.kt` (`124` LoC) with monotonic `generation: Long` incremented on `armOnConnected(): Long`, `reset()`, and `onHrPacketReceived()`, plus `getGeneration(): Long` and generation-checked `onWatchdogElapsed(expectedGeneration: Long)` returning `Action.NONE` on mismatch, alongside single-slot `PolarPmdStreamCoordinator.cancelSlot()` (`316 / 1,500` LoC).
   - `BTLEHeartRateDevice` preserves `REQ-CON-049` DIS `0x2A24`/`0x2A25` before HR CCCD queue order (`BTLEHeartRateDeviceTest:561`), ignores stale `gatt != mBluetoothGatt` callbacks in `onDescriptorWriteCompleted()`, retries failed `0x2A37` CCCD status once, defers watchdog while `isGattPaused()`, binds timeout runnables immutably to `mSilentHrWatchdogGeneration`, and escalates `12 s` silence to CCCD re-enable then `triggerSilentConnectionReconnect()`, disarming on first HR packet.
   - `PolarOnlineStreamingBridge` wires the same watchdog (via `@VisibleForTesting internal var silentHrWatchdogTimeoutMs`, preserving constructor/public signatures for `InterfaceSignatureDriftContractTest`) under a dedicated `silentHrWatchdogLock` with identity-checked job cleanup (`coroutineContext[Job] === silentHrWatchdogJob`) and generation re-verification before `startHrCollection()` and follow-up timer scheduling; if still silent, calls `forceDisconnectDevice(..., clearRegistrations = false)` + `scheduleBackoffReconnect()` so reconnect callbacks remain registered.
4. **Monolith Ceilings, SWE.3 & System Traceability (`REQ-CON-054.4`)**:
   - `DeviceManager.java` exact `1,707 / 1,708` LoC (`+0` net LoC); `0` edits to `TrackerService.java` (`3,539/3,559`) & `DevicesDatabaseManager.java` (`1,669/1,671`); `Localization Parity: N/A - no new or modified UI strings`.
   - Updated SWE.3 detailed design (`my_btle_device.md`, `banal_service.md`) and grounded `BleHrmSilentConnectionWatchdog.kt` / `BTLEHeartRateDevice.java` under `SYSREQ-TRK-001` & `SYSREQ-TRK-002` (`sysreq_live_tracking.md`, `system_software_traceability.md`, `Rule 81`).

### Stage 6 Cold Final Review Bounce 1/3 Heals Verified (`98a377f6`)
1. **[HIGH] Stale Watchdog Generation & Unsynchronized Job Lifecycle**: Added monotonic `generation` in `BleHrmSilentConnectionWatchdog.kt`, dedicated `silentHrWatchdogLock` + job-identity cleanup + generation re-verification in `PolarOnlineStreamingBridge.kt`, and immutable `expectedGeneration` binding in `BTLEHeartRateDevice.java`.
2. **[MEDIUM] Stale GATT Callback Descriptor Retry**: Added early `if (gatt != mBluetoothGatt) return;` guard in `BTLEHeartRateDevice.onDescriptorWriteCompleted(...)`.
3. **[MEDIUM] `!posted` GATT Close Fallback**: Updated `MyBTLEDevice.disconnectFromGatt()` so `if (!posted)` runs `closeRunnable.run()` synchronously (guaranteeing `disconnect()`, `close()`, and `onGattClosed`).
4. **[MINOR] SWE.3 Doc Precision**: Clarified exact `onGattClosed == null && mHandler.getLooper() != null && mHandler.getLooper() == Looper.myLooper()` fast-path condition and `!posted` fallback in `docs/architecture/detailed_design/my_btle_device.md`.

---

## 2. Verification Evidence (`Rule 7.5` Reused Gate 4 Bounce 1 SHA `98a377f60539f4f814bef7acc11ee6154b7d87f1` — `184/184 PASS`)
- **Rule 7.5 Doc-Only Fast Path Justification**: Zero edits under `app/` since Gate 4 Bounce 1 PASS (`ATT-9351`, `git diff 98a377f6..HEAD -- app/` is empty); no redundant Gradle execution performed.
- **Build & Compiler Hygiene**: `:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin`, `:app:assembleDebug` -> **PASS** (`0` new production Kotlin compiler warnings across all created/edited Kotlin files).
- **Tier 1 Targeted & Contract Verification (`184/184 PASS`, `0` fail, `0` err, `0` skip across 19 suites; `+4` Bounce 1 regression tests)**:

| Test Suite | Result | Requirement / Scope |
| :--- | :---: | :--- |
| `BleHrmSilentConnectionWatchdogTest` | `3/3 PASS` | `REQ-CON-054.3` / `TST-CON-054.3` pure-JVM escalation, disarm & stale generation |
| `BTLEHeartRateDeviceTest` | `16/16 PASS` | `REQ-CON-054.1`, `.3`, stale GATT descriptor ignore, `REQ-CON-049` DIS order, `ATT-8994` |
| `MyBTLEDeviceLifecycleTest` | `14/14 PASS` | `REQ-CON-054.1` sync looper close, `!posted` close fallback & post-shutdown late GATT close |
| `MyBTLEDeviceQueueTest`, `MyBTLEDeviceAddressAndSearchTest`, `BLEReconnectRobustnessTest` | `32/32 PASS` | `MyBTLEDevice` queue, scan assist & reconnect regression |
| `BTSearchForNewDevicesEngineTest` | `21/21 PASS` | `REQ-CON-054.2` connected-GATT fallback, paired skip & UUID guard |
| `PolarOnlineStreamingBridgeTest` | `37/37 PASS` | `REQ-CON-054.1`, `.3`, `silentHrWatchdogLock` stale tick, `REQ-CON-050`, `REQ-CON-053`, `ATT-9176` |
| `PolarOfflineGattCoordinatorCoexistenceTest`, `PolarPmdStreamCoordinatorTest` | `11/11 PASS` | `REQ-CON-054.1` alias teardown, sibling isolation & PMD slots |
| `DeviceManagerTeardownTest`, `DeviceManagerSearchQueueTest`, `DeviceManagerDiscoveryTest` | `17/17 PASS` | `REQ-CON-054.1` unpair Polar teardown & queue/discovery regression |
| 6 Contract suites (`ArchitectureConformance`, `DeadCodeElimination`, `DetailedDesignParity`, `InterfaceSignatureDrift`, `SourceTextReadingAudit`, `SystemLiveTrackingRequirements`) | `33/33 PASS` | LoC caps, `0` dead code, SWE.3 parity, signatures, `0` `src/main` reads, `SYSREQ` parity |
| **Total** | **`184/184 PASS`** | **All 19 suites green (`0` fail, `0` err, `0` skip)** |

### Verbatim `TST-CON-054` Method Citations (`14/14` Verified on `98a377f6`)
- `PolarOfflineGattCoordinatorCoexistenceTest#testForceDisconnectDeviceAndDisconnectUnpairedSessions_clearsTargetAliasesWithoutHarmingSibling_TST_CON_054_1`
- `DeviceManagerTeardownTest#testPairingChangedUnpair_whenAbsentFromRemoteDevices_forceDisconnectsPolarSession_TST_CON_054_1`
- `MyBTLEDeviceLifecycleTest#testOnConnectionStateChange_afterShutDown_immediatelyClosesLateGattCallback_TST_CON_054_1`
- `MyBTLEDeviceLifecycleTest#testDisconnectFromGatt_whenOnHandlerLooper_closesSynchronouslyWithoutPosting_TST_CON_054_1`
- `MyBTLEDeviceLifecycleTest#testDisconnectFromGatt_whenPostReturnsFalse_stillDisconnectsClosesAndRunsCallback_TST_CON_054_1`
- `BTSearchForNewDevicesEngineTest#testStartAsyncSearch_probesConnectedGattHrmSkipsPairedAndRejectsNonMatching_TST_CON_054_2`
- `BTSearchForNewDevicesEngineTest#testStartAsyncSearch_missingBluetoothConnectPermission_skipsConnectedProbeGracefully_TST_CON_054_2`
- `BleHrmSilentConnectionWatchdogTest#testWatchdogEscalation_reenablesCccdThenDisconnectsAndStops_TST_CON_054_3`
- `BleHrmSilentConnectionWatchdogTest#testOnHrPacketReceived_disarmsInitialAndPostResubscribeStates_TST_CON_054_3`
- `BleHrmSilentConnectionWatchdogTest#testStaleGeneration_isRejectedAfterResetDisarmOrRearm_TST_CON_054_3`
- `BTLEHeartRateDeviceTest#testSilentHrWatchdog_cccdRetryPauseDeferralDisconnectAndPolarShutDown_TST_CON_054_3`
- `BTLEHeartRateDeviceTest#testOnDescriptorWriteCompleted_ignoresStaleGattWithoutBurningRetryFlag_TST_CON_054_3`
- `PolarOnlineStreamingBridgeTest#testSilentHrWatchdog_resubscribesThenDisconnectsPreservingRegistrationsAndShutDownDisconnectsAllAliases_TST_CON_054_3`
- `PolarOnlineStreamingBridgeTest#testStaleWatchdogGenerationTick_afterDisconnectReconnectOrHrPacket_doesNotCancelHrSlotOrLeakTimer_TST_CON_054_3`

---

## 3. Static Analysis, Governance & Field-Verification Note
- **4/4 Python Safety Linters**: `check_secrets_hygiene.py`, `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py` -> **PASS**.
- **Governance & Verified Evidence**: `--sync-indexes` + `verify_requirement_governance.py --repo-root . --base-ref refs/heads/sprint/2026-11-20_02_Spät --check-verified-evidence` -> **EXIT `0`**.
- **Hardware-BLE Field Verification Note**: Physical Polar Verity Sense (`24:AC:AC:24:82:56`) unpair/rediscovery and live RF `0x2A37` CCCD silent-recovery check on physical `Pixel 10` is deferred to PO Sprint-End Ceremony 2 per Two-Tier Device Policy (`ATT-6506`, `Rule 60`); physical `Pixel 10` (`66020DLCR002FL`) was untouched.
- **Branch Preservation (`REQ-PRO-125`)**: `feature/ATT-9155` kept intact and unmerged for Stage 6 Cold Final Review re-audit.
