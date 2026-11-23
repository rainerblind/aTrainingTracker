# Stage 3 Implementation Plan: ATT-9612 - Polar Retry Sync GATT Quiescence & Mid-Discovery Recovery

**Ticket**: [ATT-9612](https://rainerblind.atlassian.net/browse/ATT-9612) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) | **Target Release**: `V5.0.0`  
**Requirements / Tests**: `REQ-SET-129` (`REQ-SET-129.1`..`.4`) / `TST-SET-131` (`TST-SET-131.1`..`.4`)  
**UI / Localization Consistency**: `N/A - 0 UI changes, 0 string edits, zero R.jar invalidation`  
**SQLite / Schema Impact**: `None` (zero DB/schema/migration edits)  
**Date**: 2026-11-23 | **Author**: Lane 2 Author (`--as agent1`)

---

## 1. Architecture, Threading & Lock DAG (`SWE.2`)
- **Strict Acyclic Lock DAG**:
  1. `PolarOfflineGattCoordinator.offlineYieldLock`: pure leaf lock protecting `activeOfflineYieldLeases`, `offlineYieldListeners`, `pausedOfflineYieldListeners`. NEVER acquires `apiLock` or `gattActionLock`; dispatches `onOfflineYieldStarted()`/`onOfflineYieldFinished()` strictly outside coordinator locks (`REQ-SET-120.1`).
  2. `PolarOnlineStreamingBridge.gattActionLock`: serializes bridge GATT-mutating actions (`onSearchTimeout`, `connectPersistentSession`, `onSilentHrWatchdogTimeout` `DISCONNECT_AND_RECONNECT`) with `pauseForOfflineSync()`'s quiescence barrier. May read `isOfflineGattYielded()` (acquires leaf `offlineYieldLock`), NEVER acquires `searchTimeoutLock` or `reconnectLock` while holding `gattActionLock` (`searchTimeoutLock` / `reconnectLock` remain disjoint).
  3. `PolarOfflineGattCoordinator.connectingDeviceIds`: lock-free `ConcurrentHashMap.newKeySet<String>()` updated alongside `connectedDeviceIds` in `multiplexerCallback`, `forceDisconnectDevice`, and `resetForTesting()`.
- **Monolith Budgets & Signature Pin**:
  - `PolarOfflineGattCoordinator.kt`: `1,315 -> ~1,400 / 1,500` LoC (`+~85`).
  - `PolarOnlineStreamingBridge.kt`: `1,477 -> ~1,488 / 1,500` LoC (`+~11`, constructor & public JVM signatures untouched).
  - `PolarH10OfflineController.kt`: `868 -> ~893 / 1,500` LoC (`+~25`).
  - `PolarVeritySenseOfflineController.kt`: `1,483 -> ~1,485 / 1,500` LoC (`+~2`).
  - **Strict `0` Edits**: `DeviceManager.java`, `TrackerService.java`, `DevicesDatabaseManager.java`, `PolarRetrySyncCoordinator.kt`, `strings*.xml`.

---

## 2. Bite-Sized Atomic Implementation Steps
| Step | Target File(s) | Requirement / Test | Atomic Implementation Scope |
| :---: | :--- | :--- | :--- |
| **1** | `PolarOnlineStreamingBridge.kt` | `REQ-SET-129.1` / `TST-SET-131.1` | Add `private val gattActionLock = Any()`. In `pauseForOfflineSync()`, after cancelling `reconnectJob`/`searchTimeoutJob`/`silentHrWatchdog`/`hrStalenessTimer`/`streamCoordinator` renegotiations, execute `synchronized(gattActionLock) {}` as a linearizable quiescence barrier. Guard `onSearchTimeout()` (`disconnectFromDevice`), `connectPersistentSession()` (`sessionOpenPendingFeatures` + `connectToDevice`), and `onSilentHrWatchdogTimeout()` `DISCONNECT_AND_RECONNECT` (`detachBridgeSessionState` + `forceDisconnectDevice`) inside `synchronized(gattActionLock)` with a re-check of `!isShutDown.get() && !isOfflineGattYielded()`. Keep `<= 1,492 / 1,500` LoC and zero public signature changes. |
| **2** | `PolarOfflineGattCoordinator.kt` | `REQ-SET-129.2`, `REQ-SET-129.3` / `TST-SET-131.2`, `TST-SET-131.3` | Raise `FEATURE_READY_TIMEOUT_MS` from `10_000L` to `20_000L`. Add `connectingDeviceIds` + `@JvmStatic fun isDeviceConnecting(polarId: String?): Boolean`, `@VisibleForTesting internal var connectRetryBackoffMs: Long = 250L`, `internal fun isManagedApi(api: PolarBleApi)`, and `private fun isFeatureReadyAcrossAliases(api, polarId, feature)` used in `awaitFeaturesReady()` / `awaitFeaturesViaCallback()`. Upgrade `connectWithinTimeout(api, polarId, timeoutMs)`: preserve exact legacy behavior when `!isManagedApi(api)`; on managed APIs, fast-return `true` if `isDeviceConnected(polarId)`, register a `finally`-removed transient `CallbackRegistration` feeding a `Channel<ConnectionEvent>` (`CONNECTED`/`DISCONNECTED`), skip duplicate initial `connectToDevice()` when `isDeviceConnecting(polarId)`, race `api.waitForConnection(polarId)` against `ConnectionEvent` via `coroutineScope` + `select` so `DISCONNECTED` (or transient non-cancellation disconnect error) immediately cancels the wedged wait, delays `connectRetryBackoffMs`, and re-issues `api.connectToDevice(polarId)` within `timeoutMs` while rethrowing `CancellationException`. Clear `connectingDeviceIds` and reset `connectRetryBackoffMs` in `resetForTesting()`. |
| **3** | `PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt` | `REQ-SET-129.3` / `TST-SET-131.3` | In `PolarH10OfflineController`, route `syncPendingWorkout()` and `executeArming()` through `connectWithinTimeout()`, and on managed APIs (`isManagedApi(api)`) await `FEATURE_POLAR_H10_EXERCISE_RECORDING` (backfill/pending sync) or `H10_OFFLINE_FEATURES` (arming) via `awaitFeaturesReady()` before PFTP calls (update `PolarH10OfflineControllerTest#testBackfillAndPendingSync_reuseSharedCoordinatorSingletonWithoutCleanup` to stub `sharedMockApi.isFeatureReady` symmetrically with `PolarVeritySenseOfflineControllerTest:1168`). In `PolarVeritySenseOfflineController`, route `performArmingSequence()`, `stopAndFetchOfflineHr()`, and `removeOfflineRecord()` through `connectWithinTimeout()` (`<= 1,485 / 1,500` LoC). |
| **4** | `docs/architecture/detailed_design/my_btle_device.md`, 4 Polar unit test suites | `REQ-SET-129.1`..`.4` / `TST-SET-131.1`..`.4` | Update `my_btle_device.md` (`REQ-PRO-114`) with `gattActionLock` quiescence, `connectingDeviceIds` / disconnect-recovering `connectWithinTimeout()`, and alias-aware `20 s` `awaitFeaturesReady()`. Add deterministic concurrency & retry tests in `PolarOnlineStreamingBridgeTest`, `PolarOfflineGattCoordinatorCoexistenceTest`, `PolarVeritySenseOfflineControllerTest`, and `PolarH10OfflineControllerTest` reproducing the exact `10:57:36` mid-wait disconnect + `10:58:11` mid-discovery retry race and proving single-attempt completion. |
| **5** | Living Docs (`req_settings_profiles.md`, `tst_settings_profiles.md`, indexes) | `REQ-SET-129.4` / `TST-SET-131.4` | Promote `REQ-SET-129` / `TST-SET-131` to `[Implemented]` with verbatim `TestClass#testMethod` citations, run `--sync-indexes`, `verify_requirement_governance.py`, safety linters, and locked incremental Gradle (`--rerun`, NO `--rerun-tasks`, NO `-PstampGitHash=true`) across all Polar + 7 contract suites + `:app:assembleDebug` (`0` new warnings). |

---

## 3. Non-Negotiable Invariants & Verification Command Plan
- **Invariants**: `H10 BACKFILL_TIMEOUT_MS = 30_000L`, `Verity BACKFILL_TIMEOUT_MS = 45_000L`, `STOP_CONNECTION_TIMEOUT_MS = 5_000L`, `5 s` absent-sensor timeout (`REQ-CON-045.1`), sibling Polar isolation (`REQ-SET-118.3`), `CancellationException` rethrow (`REQ-SET-119.2`), unmanaged `apiOverride` compatibility, pinned JVM signatures, `0` UI/string/DB edits, `0` new `src/main` reads.
- **Stage 4 Targeted Test Command** (`python3 tools/parallel_sprint_lock.py -- ./gradlew :app:assembleDebug :app:testDebugUnitTest --rerun --tests ...`):
  - `PolarOfflineGattCoordinatorCoexistenceTest`, `PolarOnlineStreamingBridgeTest`, `PolarVeritySenseOfflineControllerTest`, `PolarH10OfflineControllerTest`, `PolarPmdStreamCoordinatorTest`, `PolarRetrySyncCoordinatorTest`, `BleHrmSilentConnectionWatchdogTest`, plus 7 Contract suites (`ArchitectureConformance`, `InterfaceSignatureDrift`, `DeadCodeElimination`, `DetailedDesignParity`, `SourceTextReadingAudit`, `SystemDataExchangeRequirements`, `SystemLiveTrackingRequirements`).
