# Stage 2 Requirement & Test Specification: ATT-9175 - Pause PolarOnlineStreamingBridge Reconnect & 5s GATT Teardown During Offline Backfill Sync

**Ticket**: [ATT-9175](https://rainerblind.atlassian.net/browse/ATT-9175) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) | **Target Release**: `V5.0.0`  
**Requirement Mapping**: `REQ-SET-120` (`REQ-SET-120.1`..`REQ-SET-120.4`; Priority: `High`, Type: `Functional`, Verification Method: `Unit, Integration`, System Requirements: `SYSREQ-DEX-007`, `SYSREQ-TRK-002`)  
**Test Mapping**: `TST-SET-122` (`TST-SET-122.1`..`TST-SET-122.4`) — authored in `docs/requirements/req_settings_profiles.md` (`v1.0.11`) & `docs/tests/tst_settings_profiles.md` (`v1.0.10`, `0` edits to `tst_tracking_cockpit.md`)  
**Date**: 2026-11-20 | **Author**: AI Agent 1 (`--as agent1`)

---

## 1. Requirement Specification (`REQ-SET-120`) & Given-When-Then Acceptance Criteria
1. **`REQ-SET-120.1` — Alias-Aware Ref-Counted Offline GATT Yield & Lock-Free Dispatch**:
   - *Normative*: `PolarOfflineGattCoordinator` SHALL maintain per-device alias-aware (`MAC` <-> `8-hex Polar ID`) ref-counted offline GATT yield leases (`isOfflineGattYieldActive()`) and a bridge yield listener registry (`registerOfflineYieldListener` / `unregisterOfflineYieldListener`, cleared in `resetForTesting()`). `withGattYieldAndPolarApi()` SHALL acquire the lease on entry, resolve `DeviceManager` via `deviceManagerProvider`, release strictly in `finally` (including on timeout, exception, and `CancellationException`), and dispatch pause/resume callbacks strictly outside coordinator locks without affecting sibling Polar sensors.
   - *Given* Verity Sense (`24825632` / `24:AC:AC:24:82:56`) and sibling H10 (`AABBCCDD` / `AA:BB:CC:DD:EE:01`) registered as offline yield listeners, *When* `withGattYieldAndPolarApi()` runs nested or single offline calls for `24825632` (completing normally, timing out, throwing, or cancelling), *Then* only the Verity Sense listener receives pause on count `0 -> 1` and resume on final count `1 -> 0` strictly outside coordinator locks, while the H10 listener is untouched.
2. **`REQ-SET-120.2` — `PolarOnlineStreamingBridge` Pause/Resume & Silent-HR Watchdog Deferral**:
   - *Normative*: `PolarOnlineStreamingBridge` SHALL register for offline yield events on init and unregister on `shutDown()`. While yielded, it SHALL cancel active `reconnectJob`, `searchTimeoutJob` (under `searchTimeoutLock`), and `silentHrWatchdogJob` (under `silentHrWatchdogLock`, preserving monotonic generation tokens without leaking timers per `ATT-9155`), and MUST NOT invoke `connectToDevice()`, `disconnectFromDevice()`, or `forceDisconnectDevice()` from `startSearching()`, `scheduleBackoffReconnect()`, `onSearchTimeout()`, `onSilentHrWatchdogTimeout()`, `deviceDisconnected()`, or `blePowerStateChanged(true)`. Upon final release (`count -> 0`, `!isShutDown`), it SHALL sync an already-open shared session via `syncAlreadyOpenSessionIfReady(api)` (or re-arm `silentHrWatchdog` if connected without HR packets) or cleanly resume searching/backoff reconnect if disconnected.
   - *Given* a disconnected/reconnecting or connected-silent `PolarOnlineStreamingBridge` for `24825632`, *When* `withGattYieldAndPolarApi()` holds a yield lease across `> 12 s` of virtual time, *Then* zero `connectToDevice()`/`disconnectFromDevice()`/`forceDisconnectDevice()` calls originate from the bridge; *When* the lease finishes, *Then* the bridge syncs an already-open session or cleanly resumes reconnect without leaking watchdog jobs.
3. **`REQ-SET-120.3` — Single-Shot Bounded `deviceConnecting()` Handshake Grace**:
   - *Normative*: `PolarOnlineStreamingBridge.sdkCallback` SHALL override `deviceConnecting(polarDeviceInfo)` filtered strictly by `matchesDeviceInfo`. When `deviceConnecting` fires during an active search/reconnect attempt (`!isShutDown && !isConnectedFlag && !isOfflineGattYielded()`), it SHALL extend `searchTimeoutJob` (under `searchTimeoutLock` with job-identity cleanup) at most once per attempt to `maxOf(searchTimeoutMsProvider(), connectingHandshakeTimeoutMs)` (`15_000 ms` default, `@VisibleForTesting` injectable).
   - *Given* `searchTimeoutMsProvider = 5_000 ms`, *When* `deviceConnecting` fires for the target sensor at `t = 1_000 ms`, *Then* `disconnectFromDevice()` is NOT called at `t = 5_001 ms`; duplicate `deviceConnecting` callbacks and sibling device callbacks do not extend the deadline further; if `deviceConnected` arrives at `t = 8_000 ms` the search succeeds, whereas if the handshake stalls past `15_000 ms` it times out cleanly; *Given* no `deviceConnecting` ever fires (absent sensor), *Then* timeout still fires promptly at `5_000 ms` (`ATT-8451`).
4. **`REQ-SET-120.4` — Monolith Ceilings, Public Signature Pin & Localization**:
   - *Normative*: `PolarOnlineStreamingBridge.kt <= 1,500` LoC with unchanged constructor & public JVM signatures; `PolarOfflineGattCoordinator.kt <= 1,500` LoC; `0` edits to `PolarVeritySenseOfflineController.kt`, `PolarH10OfflineController.kt`, `DeviceManager.java`, `TrackerService.java`, and `DevicesDatabaseManager.java`; `0` new `src/main` reads; `0` new production Kotlin warnings; `Localization Parity: N/A - no new or modified UI strings`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
1. **Original Requirement ID & Target**: Refines `REQ-SET-118`, `REQ-SET-119`, `REQ-CON-045`, `REQ-CON-050`, `REQ-CON-054` across `PolarOfflineGattCoordinator.kt` and `PolarOnlineStreamingBridge.kt`.
2. **Historical Origin & Commit Trace**: `ATT-8451` (`14070717`), `ATT-8690` (`34b78941`), `ATT-8714` (`1e7a2f0b`), `ATT-8969` (`8e44039d`), `ATT-9155` (`f7ed7d94`).
3. **Root Reason for Existing Formulation**: `withGattYieldAndPolarApi()` paused only `MyBTLEDevice` before `PolarOnlineStreamingBridge` existed; `5 s` `searchTimeoutJob` prevented absent Polar sensors from stalling `DeviceManager`'s sequential search queue (`PolarOnlineStreamingBridgeTest:517-533`).
4. **Preservation of Core Invariants**: Preserves `5 s` absent-sensor timeout (`REQ-CON-045.1`), sibling Polar session isolation (`REQ-SET-118.3`), `PolarOnlineStreamingBridge` constructor/public JVM signatures (`InterfaceSignatureDriftContractTest`), `ATT-9155` monotonic `silentHrWatchdog` generation & lock safety, and `0` edits to `PolarVeritySenseOfflineController.kt` (Lane A `ATT-9154`).

---

## 3. Traceability Matrix & Localization Audit
| Requirement | Test Case | Target Suite / Contract | Planned Verification Procedure |
| :--- | :--- | :--- | :--- |
| `REQ-SET-120.1` | `TST-SET-122.1` | `PolarOfflineGattCoordinatorCoexistenceTest` | Alias-scoped ref-counted yield, nested release on success/timeout/exception/`CancellationException`, sibling isolation, lock-free listener dispatch, `resetForTesting()` |
| `REQ-SET-120.2` | `TST-SET-122.2` | `PolarOnlineStreamingBridgeTest` | Offline yield cancels reconnect/search-timeout/silent-HR-watchdog (`0` competing `connectToDevice`/`disconnectFromDevice`), preserves generation tokens, resumes session sync or reconnect on release |
| `REQ-SET-120.3` | `TST-SET-122.3` | `PolarOnlineStreamingBridgeTest` | `deviceConnecting()` single-shot `15 s` grace, sibling/duplicate immunity, stalled-handshake bounded timeout, `5 s` absent-sensor non-regression |
| `REQ-SET-120.4` | `TST-SET-122.4` | 7 Contract suites | `ArchitectureConformance`, `InterfaceSignatureDrift`, `DeadCodeElimination`, `DetailedDesignParity`, `SourceTextReadingAudit`, `SystemDataExchangeRequirements`, `SystemLiveTrackingRequirements` |

- **9-Language Localization Audit (`EN/DE/ES/FR/IT/JA/NL/PL/PT`)**: `Localization Parity: N/A - no new or modified UI strings` (pure BLE/GATT coordination fix with zero user-facing copy changes).
