# Stage 5 Walkthrough: ATT-9612 - Polar Retry Sync GATT Quiescence & Mid-Discovery Recovery

**Ticket**: [ATT-9612](https://rainerblind.atlassian.net/browse/ATT-9612) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) | **Target Release**: `V5.0.0` (`FixVersion`: set upon PO acceptance per `Rule 8.3`)  
**Requirements / Tests**: `REQ-SET-129` (`REQ-SET-129.1`–`.4`) [Verified] / `TST-SET-131` (`TST-SET-131.1`–`.4`) [Verified]  
**Gate 3 Plan Sign-Off**: `ATT-9659` (`GATE_PASSED`, all 4 guardrails implemented)  
**Evidence Bundle**: [`evidence_summary.json`](../test_evidence/tickets/ATT-9612/evidence_summary.json) | [`manifest.json`](../test_evidence/tickets/ATT-9612/manifest.json)  
**Date**: 2026-11-23 | **Author**: Lane 2 Author (`--as agent1`)

---

## 1. Summary of Changes & Gate 3 Guardrail Compliance
- **`PolarOnlineStreamingBridge.kt` (`1,489 / 1,500` LoC, unchanged public JVM signatures)**:
  - Added `gattActionLock` linearizable quiescence barrier in `pauseForOfflineSync()` so in-flight `onSearchTimeout()`, `connectPersistentSession()`, and `onSilentHrWatchdogTimeout()` `DISCONNECT_AND_RECONNECT` complete before `withGattYieldAndPolarApi()` executes `block(api)` and zero bridge `disconnectFromDevice()`/`connectToDevice()`/`forceDisconnectDevice()` calls occur while yielded (`REQ-SET-129.1`).
  - Re-verified `searchAttemptGeneration == expectedGeneration` under `searchTimeoutLock` inside `gattActionLock` (ABA-safe), cleared connecting aliases via `clearConnectingState()`, kept `acquirePersistentSession()` / `syncAlreadyOpenSessionIfReady()` outside `gattActionLock`, and preserved `resumeSearchAfterOfflineSync` / `resumeSilentWatchdogAfterOfflineSync` bailouts.
- **`PolarOfflineGattCoordinator.kt` (`1,485 / 1,500` LoC)**:
  - Added alias-tracked `connectingDeviceIds` (`isDeviceConnecting()`, `clearConnectingState()`) cleared across full `collectAliasClosure(...)` on `deviceConnected`, `deviceDisconnected`, `blePowerStateChanged(false)`, `forceDisconnectDevice`, `releasePolarApi` `1->0`, `withGattYieldAndPolarApi` disconnect exit, and `resetForTesting()`.
  - Upgraded managed-API `connectWithinTimeout()` with blank-ID guard, fast `isDeviceConnected` reuse, non-duplicate `isDeviceConnecting` await, sibling-isolated `CallbackRegistration` + `matchesDeviceInfo` filter, `Channel.UNLIMITED` latest-event coalescing (`[CONNECTED, DISCONNECTED]` bursts resolve to `DISCONNECTED` both before `select` and during backoff) with `finally` cancellation of `waitDeferred`, immediate retry on mid-wait `deviceDisconnected` / transient errors, and `CancellationException` propagation while preserving unmanaged `apiOverride` mocks (`REQ-SET-129.2`).
  - Raised `FEATURE_READY_TIMEOUT_MS` to `20_000L` ms and added MAC/8-hex alias-aware `isFeatureReadyAcrossAliases()` (`REQ-SET-129.3`).
- **H10 & Verity Sense Sibling Parity + SWE.3 Detailed Design**:
  - Routed all offline connect paths in `PolarH10OfflineController.kt` (`898 / 1,500` LoC) and `PolarVeritySenseOfflineController.kt` (`1,492 / 1,500` LoC) through `connectWithinTimeout()` using each entry point's own timeout budget; added managed-API `awaitFeaturesReady()` gating to `PolarH10OfflineController` (`REQ-SET-129.3`).
  - Updated [`docs/architecture/detailed_design/my_btle_device.md`](../../architecture/detailed_design/my_btle_device.md) (`v1.0.13`) with the verified acyclic lock DAG (`gattActionLock -> {offlineYieldLock, apiLock, searchTimeoutLock, reconnectLock, cadenceEstimatorLock}`, zero reverse edges) and `connectingDeviceIds` clear invariants (`REQ-PRO-114`).

---

## 2. Verification Scope & Results (`Tier 1 Targeted + Contracts`)
- **Build & Compiler Hygiene**: `:app:assembleDebug` PASS; `0` new production Kotlin warnings across all 4 modified Polar files.
- **Unit & Contract Suites (`233/233 PASS`, `0` fail, `0` err, `0` skip across 24 suites via `--rerun --tests`)**:
  - `PolarOnlineStreamingBridgeTest` (`58/58`, incl. `testPauseForOfflineSync_quiescesInFlightSearchTimeoutAndPreventsPostYieldTeardown_TST_SET_131_1`)
  - `PolarOfflineGattCoordinatorCoexistenceTest` (`14/14`, incl. `testConnectWithinTimeout_recoversFromMidWaitDisconnectAndIsolatesSiblingDisconnect_TST_SET_131_2`, `testConnectWithinTimeout_reusesConnectedAndInFlightConnectingSessionsAndClearsStateOnRelease_TST_SET_131_2`, `testConnectWithinTimeout_coalescesConnectedThenDisconnectedBurstBeforeSelectAndDuringBackoff_TST_SET_131_2`, `testAwaitFeaturesReady_uses20sDefaultAndResolvesMacHexAliases_TST_SET_131_3`)
  - `PolarVeritySenseOfflineControllerTest` (`30/30`, incl. `testSyncPendingWorkout_recoversFromMidWaitDisconnectAndMidDiscoveryDelayedFeature_TST_SET_131_3`)
  - `PolarH10OfflineControllerTest` (`26/26`, incl. `testSyncPendingWorkout_managedApiRecoversFromMidWaitDisconnectAndAwaitsFeatureReady_TST_SET_131_3`)
  - Adjacent Polar/BLE suites (`68/68`): `PolarPmdStreamCoordinatorTest` (`11`), `PolarRawTelemetryRecorderTest` (`9`), `PolarHrStreamHealthMonitorTest` (`7`), `PolarOfflineCapabilityStoreTest` (`7`), `PolarRawTelemetryPrunerTest` (`6`), `PolarRawTelemetryReaderTest` (`6`), `PolarH10RunningDynamicsEstimatorTest` (`5`), `PolarLiveRunningCadenceEstimatorTest` (`5`), `PolarPpiStreamProcessorTest` (`4`), `PolarRetrySyncCoordinatorTest` (`3`), `BleHrmSilentConnectionWatchdogTest` (`3`), `PolarDualImuBoundedHeapStressTest` (`1`), `WorkoutSamplesHrBackfillTest` (`1`).
  - Contract suites (`37/37`): `ArchitectureConformance` (`11`), `DeadCodeElimination` (`6`), `SourceTextReadingAudit` (`6`), `SystemDataExchangeRequirements` (`4`), `SystemLiveTrackingRequirements` (`4`), `InterfaceSignatureDrift` (`3`), `DetailedDesignParity` (`3`).
- **UI / Localization / Emulator Boundary**: Non-UI BLE/GATT concurrency fix (`0` UI/Compose/resource/string edits; `Localization Parity: N/A - 0 string changes`). Live physical Pixel 10 BLE RF retry validation deferred to PO Ceremony 2 (`Rule 7.6`).
- **Governance & Automated Gate**: `verify_requirement_governance.py --base-ref sprint/2026-11-23_01_Früh` EXIT `0`; `tools/auto_gate.py --ticket ATT-9612 --tier tier-a --write-bundle --advance-jira` PASS (`evidence_summary.json` & SHA-256 `manifest.json` archived under `docs/engineering/test_evidence/tickets/ATT-9612/`).
