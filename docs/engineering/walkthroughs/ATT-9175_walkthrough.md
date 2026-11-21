# Stage 5 Verification & Walkthrough: ATT-9175 - Pause PolarOnlineStreamingBridge Reconnect & 5s GATT Teardown During Offline Backfill Sync

**Ticket**: [ATT-9175](https://rainerblind.atlassian.net/browse/ATT-9175) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-20_02_Spät` | **Branch**: `feature/ATT-9175`  
**Verified Production SHA**: `d2c4953fd3e1413683aad21375143244d76ffc6d` (Gate 4 Bounce 1 `ATT-9384` PASS; `Rule 7.5` doc-only Stage 5 fast path with `0` `app/` drift: `git diff d2c4953f..HEAD -- app/` is empty)  
**Requirements / Test Spec**: `REQ-SET-120` (`REQ-SET-120.1`..`.4`, refining `SYSREQ-DEX-007` & `SYSREQ-TRK-002`) / `TST-SET-122` (`TST-SET-122.1`..`.4`) — `[Verified]`  
**Date**: 2026-11-20 | **Author**: AI Agent 1 (`--as agent1`)

---

## 1. Root Cause & Summary of Verified Changes
1. **Alias-Aware Ref-Counted Offline GATT Yield & Strict Leaf Lock DAG (`REQ-SET-120.1`)**:
   - `PolarOfflineGattCoordinator.kt` (`1,283 / 1,500` LoC): Added `internal interface OfflineYieldListener`, exact `OfflineYieldLease(polarId, macAddress)` token objects, and `pausedOfflineYieldListeners: MutableSet<OfflineYieldListener>` under leaf `offlineYieldLock`, plus `registerOfflineYieldListener()`/`unregisterOfflineYieldListener()` and live alias-aware `isOfflineGattYieldActive(vararg identifiers)`.
   - `withGattYieldAndPolarApi()` resolves `DeviceManager` via `deviceManagerProvider`, acquires/releases exact lease tokens in `try/finally` (covering normal exit, timeout, exception, and `CancellationException`), and dispatches `onOfflineYieldStarted()`/`onOfflineYieldFinished()` strictly on `pausedOfflineYieldListeners` membership transitions strictly **outside** `offlineYieldLock` and `apiLock` (zero lock inversion, alias-expansion safe, preserving sibling Polar isolation `REQ-SET-118.3`). Yield state clears in `resetForTesting()`.
2. **`PolarOnlineStreamingBridge` Pause/Resume, Keep-Alive & Silent-HR Watchdog Deferral (`REQ-SET-120.2`)**:
   - `PolarOnlineStreamingBridge.kt` (`1,469 / 1,500` LoC; unchanged constructor & public JVM signatures): Registers/unregisters `offlineYieldListener` in `init`/`shutDown()`; uses `PolarOfflineGattCoordinator.isOfflineGattYieldActive(...)` as the single atomic source of truth for `isOfflineGattYielded()` (zero sticky local boolean wedge) and guards `pauseForOfflineSync()` at entry with `if (isShutDown.get() || !isOfflineGattYielded()) return`.
   - Cancels `reconnectJob`, `searchTimeoutJob` (under disjoint `searchTimeoutLock`), and `silentHrWatchdogJob` (under `silentHrWatchdogLock`, setting `resumeSilentWatchdogAfterOfflineSync` on mid-yield deferral), gates `startSearching()`, `scheduleBackoffReconnect()` (pre/post-delay), `onSearchTimeout()`, `onSilentHrWatchdogTimeout()`, `deviceDisconnected()`, and `blePowerStateChanged(true)` on `!isOfflineGattYielded()`, and keeps `shouldKeepAliveOnSearchTimeout() == true` while yielded in `READY`/`WAITING_FOR_START` mode so `DeviceManager` never shuts down a yielded bridge mid-backfill.
   - `resumeAfterOfflineSync()` syncs any already-open shared `PolarBleApi` session via `syncAlreadyOpenSessionIfReady(api)`, clears pending search flags, and arms `silentHrWatchdog` when connected & `state != DISARMED` (including sensors that connected during yield, while staying disarmed if an HR packet arrived during yield).
3. **Single-Shot Bounded `deviceConnecting()` Handshake Grace (`REQ-SET-120.3`)**:
   - Overrode `sdkCallback.deviceConnecting(polarDeviceInfo)` filtered strictly by `matchesBridgeDeviceInfo()` to extend `searchTimeoutJob` (under `searchTimeoutLock` with monotonic `searchAttemptGeneration` and `coroutineContext[Job] === searchTimeoutJob` cleanup) at most once per attempt to `maxOf(searchTimeoutMsProvider(), connectingHandshakeTimeoutMs)` (`15_000 ms` default, `@VisibleForTesting` injectable) and logged the exact elapsed `timeoutMs` in `onSearchTimeout(expectedGeneration, timeoutMs)`, eliminating premature `5 s` `disconnect()/refresh()/close()` teardown during `SESSION_OPENING` `discoverServices()` while preserving `5 s` absent-sensor timeout (`REQ-CON-045.1`).
4. **Monolith Isolation, SWE.3 & System Traceability (`REQ-SET-120.4`)**:
   - Strict **`0` edits** to `PolarVeritySenseOfflineController.kt`, `PolarH10OfflineController.kt`, `DeviceManager.java` (`1,707/1,708`), `TrackerService.java`, `DevicesDatabaseManager.java`, `docs/tests/tst_tracking_cockpit.md`, and `strings*.xml` (`Localization Parity: N/A - no new or modified UI strings`).
   - Updated SWE.3 `docs/architecture/detailed_design/my_btle_device.md` (`v1.0.7`) and linked `REQ-SET-120` bidirectionally under `SYSREQ-DEX-007` & `SYSREQ-TRK-002` (`sysreq_data_exchange.md` `v1.2.2`, `sysreq_live_tracking.md` `v1.2.18`, `system_software_traceability.md` `v1.2.18`).

### Stage 6 Cold Final Review Bounce 1/3 Heals Verified (`d2c4953f`)
1. **[HIGH] Single Atomic Yield Source & Stale Pause Guard**: Removed sticky `isPausedForOfflineSync`; guarded `pauseForOfflineSync()` on `!isOfflineGattYielded()` so stale out-of-order callbacks cannot wedge the bridge or cancel active searches.
2. **[HIGH] Mid-Yield `deviceConnected` Watchdog Deferral & Resume**: Flagged `resumeSilentWatchdogAfterOfflineSync` inside `scheduleSilentHrWatchdogTimer()` when yielded, and re-armed in `resumeAfterOfflineSync()` when connected & `state != DISARMED`.
3. **[HIGH] `DeviceManager` Keep-Alive During Yield**: Updated `shouldKeepAliveOnSearchTimeout()` to include `|| isOfflineGattYielded()` so `DeviceManager` never invokes `shutDown()` -> `forceDisconnectDevice()` mid-backfill.
4. **[LOW/MEDIUM] Alias-Expansion Membership Set & Exact Timeout Log**: Tracked `pausedOfflineYieldListeners` under `offlineYieldLock` across overlapping alias-expanding leases, and passed actual `timeoutMs` into `onSearchTimeout(expectedGeneration, timeoutMs)`.

---

## 2. Verification Evidence (`Rule 7.5` Reused Gate 4 Bounce 1 SHA `d2c4953fd3e1413683aad21375143244d76ffc6d` — `164/164 PASS`)
- **Rule 7.5 Doc-Only Fast Path Justification**: Zero edits under `app/` since Gate 4 Bounce 1 PASS (`ATT-9384`, `git diff d2c4953f..HEAD -- app/` is empty); no redundant Gradle execution performed.
- **Build & Compiler Hygiene**: `:app:assembleDebug` -> **PASS** (`0` new production Kotlin compiler warnings across `PolarOfflineGattCoordinator.kt` and `PolarOnlineStreamingBridge.kt`).
- **Tier 1 Targeted & Contract Verification (`164/164 PASS`, `0` fail, `0` err, `0` skip across 15 suites; `+3` Bounce 1 regression tests)**:

| Test Suite | Result | Requirement / Scope |
| :--- | :---: | :--- |
| `PolarOnlineStreamingBridgeTest` | `43/43 PASS` | `REQ-SET-120.2`, `.3` (`6/6` new tests incl. mid-yield connect, keep-alive & stale pause), `REQ-CON-045`/`050`/`053`/`054` |
| `PolarOfflineGattCoordinatorCoexistenceTest` | `9/9 PASS` | `REQ-SET-120.1` (`3/3` new tests incl. alias-expanding overlapping leases), `REQ-SET-118`, `REQ-CON-054.1` |
| `PolarPmdStreamCoordinatorTest`, `BleHrmSilentConnectionWatchdogTest`, `PolarRetrySyncCoordinatorTest` | `11/11 PASS` | PMD slots (`5/5`), silent-HR watchdog generations (`3/3`), Aftermath retry coordinator (`3/3`) |
| `PolarVeritySenseOfflineControllerTest`, `PolarH10OfflineControllerTest`, `BTLEHeartRateDeviceTest` | `64/64 PASS` | Verity Sense (`28/28`), H10 (`20/20`), and BLE HRM (`16/16`) regression |
| 7 Contract suites (`ArchitectureConformance`, `DeadCodeElimination`, `DetailedDesignParity`, `InterfaceSignatureDrift`, `SourceTextReadingAudit`, `SystemDataExchangeRequirements`, `SystemLiveTrackingRequirements`) | `37/37 PASS` | LoC caps, `0` dead code, SWE.3 parity, public signatures, `0` `src/main` reads, `SYSREQ-DEX/TRK` parity |
| **Total** | **`164/164 PASS`** | **All 15 suites green (`0` fail, `0` err, `0` skip)** |

### Verbatim `TST-SET-122` Method Citations (`9/9` Verified on `d2c4953f`)
- `PolarOfflineGattCoordinatorCoexistenceTest#testWithGattYieldAndPolarApi_pausesAndResumesTargetAliasesWithoutHarmingSibling_TST_SET_122_1`
- `PolarOfflineGattCoordinatorCoexistenceTest#testWithGattYieldAndPolarApi_nestedLeasesAndExceptionOrCancellationReleaseCleanly_TST_SET_122_1`
- `PolarOfflineGattCoordinatorCoexistenceTest#testWithGattYieldAndPolarApi_aliasExpansionAcrossOverlappingLeases_transitionsPausedSetOnce_TST_SET_122_1`
- `PolarOnlineStreamingBridgeTest#testOfflineGattYield_pausesReconnectSearchTimeoutAndSilentWatchdogThenResumes_TST_SET_122_2`
- `PolarOnlineStreamingBridgeTest#testOfflineGattYield_whenSharedSessionConnectedDuringBackfill_syncsOpenSessionOnResume_TST_SET_122_2`
- `PolarOnlineStreamingBridgeTest#testOfflineGattYield_deviceConnectedDuringYield_armsSilentWatchdogOnResumeUnlessDisarmedByHr_TST_SET_122_2`
- `PolarOnlineStreamingBridgeTest#testOfflineGattYield_stalePauseCallDoesNotWedgeAndShouldKeepAliveInWaitingForStartMode_TST_SET_122_2`
- `PolarOnlineStreamingBridgeTest#testDeviceConnecting_extendsSearchTimeoutOnceToHandshakeGraceAndPreserves5sAbsentTimeout_TST_SET_122_3`
- `PolarOnlineStreamingBridgeTest#testDeviceConnecting_ignoresSiblingAndDuplicateCallbacksAndTimesOutAfterHandshakeGrace_TST_SET_122_3`

---

## 3. Static Analysis, Governance & Field-Verification Note
- **4/4 Python Safety Linters**: `check_secrets_hygiene.py`, `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py` -> **PASS**.
- **Governance & Verified Evidence**: `--sync-indexes` + `verify_requirement_governance.py --repo-root . --base-ref refs/heads/sprint/2026-11-20_02_Spät --check-verified-evidence` -> **EXIT `0`**.
- **Hardware-BLE Field Verification Note**: Physical Polar Verity Sense (`24825632`, MAC ending `82:56`) Aftermath retry backfill (*"Jetzt synchronisieren"*) and live RF `SESSION_OPENING` handshake verification on physical `Pixel 10` (`66020DLCR002FL`) is deferred to PO Sprint-End Ceremony 2 per Two-Tier Device Policy (`ATT-6506`, `Rule 60`); physical `Pixel 10` was untouched.
- **Branch Preservation (`REQ-PRO-125`)**: `feature/ATT-9175` kept intact and unmerged for Stage 6 Cold Final Review re-audit.
