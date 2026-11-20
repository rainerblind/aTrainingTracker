# Stage 5 Verification & Walkthrough: ATT-9161 - Fix PolarOnlineStreamingBridge Re-Entrant Stream Start Burst and Silent IMU Death on PMD ERROR_ALREADY_IN_STATE

**Ticket**: [ATT-9161](https://rainerblind.atlassian.net/browse/ATT-9161) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-20_01_Früh` | **Branch**: `feature/ATT-9161`  
**Verified Production SHA**: `79e9e1b1` (Gate 4 Bounce 1 `ATT-9313` PASS; `Rule 7.5` doc-only Stage 5 Bounce 1 with `0` `app/` drift: `git diff 79e9e1b1..HEAD -- app/` is empty)  
**Requirements / Test Spec**: `REQ-CON-053` (`REQ-CON-053.1`..`.4`, refining `SYSREQ-TRK-001` & `SYSREQ-TRK-002`) / `TST-CON-053` (`TST-CON-053.1`..`.4`) — `[Verified]`  
**Date**: 2026-11-20 | **Author**: AI Agent 1 (`--as agent1`)

---

## 1. Root Cause & Summary of Verified Changes
1. **Root Cause (`PolarOnlineStreamingBridge.kt`)**:
   - `startHrCollection()` had zero atomic guard and unconditionally cancelled/restarted `hrJob` on overlapping `bleSdkFeatureReady(FEATURE_HR)` / `syncAlreadyOpenSessionIfReady()` callbacks (producing 7× `00002a37` CCCD toggle bursts).
   - `startAccCollection()` and `startGyroCollection()` flipped `is*StreamingActive` only *after* suspending `resolve*Setting()` and lacked `job === myJob` identity guards in `finally`, causing overlapping `REQUEST_MEASUREMENT_START` PMD commands and active-flag / `ACC_OWNER_ONLINE` clobbering.
   - All three PMD streams (`ACC`, `GYRO`, and `ATT-9176` `PPI`) swallowed `BleControlPointCommandError(..., ERROR_ALREADY_IN_STATE)` (triggered by re-entrancy or background `fi.polar.polarflow`) and died silently for the entire session.
2. **`PolarPmdStreamCoordinator.kt` (`300` LoC, `REQ-CON-053.1`–`.3`)**:
   - Unified 4-stream (`HR`, `ACC`, `GYRO`, `PPI`) atomic CAS + `CoroutineStart.LAZY` + `slot.job === myJob` `finally` supervision with atomic `ACC` `preAcquire` rollback (including `try/catch` rollback on unexpected `preAcquire` exceptions — Stage 6 Bounce 1).
   - **Atomic `slotLock` Cleanup Ordering (Stage 6 Bounce 1)**: Executes `onFinalCleanup?.invoke()` inside `synchronized(slotLock)` BEFORE clearing `state.job = null; state.active.set(false)` (wrapped in `try/finally`), and executes `onStreamsCancelled()` inside `synchronized(slotLock)` in `cancelAllStreams()` while keeping `job.cancel()` outside `slotLock`—eliminating any cross-thread release-after-reacquire race on `PolarOfflineGattCoordinator.ACC_OWNER_ONLINE`.
   - Bounded 3-retry PMD `ERROR_ALREADY_IN_STATE` recovery (`runCatching { api.stopStreaming(sessionId, pmdType) }` + injectable `500 ms -> 1,000 ms -> 2,000 ms` backoff, pre/post-delay connection/shutdown guards, fresh stream factory per retry, retry-counter reset on first delivered sample, typed `BleControlPointCommandError` + cause/message detection).
   - Per-session `warningEmittedForSession` deduplication paired with a multi-device-safe companion `StateFlow<PolarPmdStreamWarning?>` and owner-scoped `consumeStreamWarning(polarId)`.
3. **`PolarOnlineStreamingBridge.kt` (shrunk `1,306 -> 1,259 / 1,500` LoC, `-47` LoC, `REQ-CON-053.1`–`.4`)**:
   - Delegated all 4 stream slots and PMD recovery to `PolarPmdStreamCoordinator`, wired `cancelActiveStreamJobs()` to `streamCoordinator.cancelAllStreams { releaseOnlineAccChannels() }`, preserved `0` constructor/public signature changes (`InterfaceSignatureDriftContractTest` 3/3), `!wasConnected` session-warning reset guard in `markBridgeConnected()`, and full `ATT-9176` & `ATT-8994` invariants.
4. **`PolarPmdStreamWarningEffect.kt` (`56` LoC) & 9-Locale Parity (`REQ-CON-053.3`)**:
   - Placed in `com.atrainingtracker.banalservice.ui.devices.polar` (`0` upward `banalservice -> trainingtracker.ui` imports), observing `streamWarning` via `collectLatest` + `try/finally { consumeStreamWarning(warning.polarId) }`, mounted in `TrackingTabsScreen.kt` (`1,419 / 1,500` LoC, `+2` LoC) and `DevicesTabbedScreen.kt` (`813 / 1,500` LoC, `+2` LoC).
   - Added `polar_live_imu_stream_blocked_warning` across all 9 locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) in `strings_devices.xml` with `0` emojis (`TranslationParityTest 11/11 PASS`).
5. **Protected Monoliths & System Traceability**:
   - `0` edits to `DeviceManager.java` (`1,707/1,708`), `TrackerService.java` (`3,539/3,559`), and `DevicesDatabaseManager.java` (`1,669/1,671`).
   - Bidirectional `SYSREQ-TRK-001` / `SYSREQ-TRK-002` <-> `REQ-CON-053` parity synced with `PolarPmdStreamCoordinator.kt` grounded in `docs/system/sysreq_live_tracking.md` (`v1.2.15`) and `docs/system/system_software_traceability.md` (`v1.2.15`).

---

## 2. Verification Evidence (`Rule 7.5` Reused Gate 4 Bounce 1 SHA `79e9e1b1` — `173/173 PASS`)
- **Build & Compiler Hygiene**: `:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin`, `:app:assembleDebug` -> **PASS** (`0` new Kotlin compiler warnings across all `ATT-9161` main & test files).
- **Test Execution (`173/173 PASS`, `0` failed, `0` errors, `0` skipped across 22 suites)**:

| Test Suite | Result | Requirement / Scope |
| :--- | :---: | :--- |
| `PolarPmdStreamCoordinatorTest` | `5/5 PASS` | `REQ-CON-053.1`–`.3` / `TST-CON-053.1`–`.3` (incl. Bounce 1 atomic `slotLock` test) |
| `PolarOnlineStreamingBridgeTest` | `35/35 PASS` | `REQ-CON-053.1`–`.4` + `ATT-9176` / `ATT-8994` regressions |
| `PolarPmdStreamWarningEffectTest` | `1/1 PASS` | `REQ-CON-053.3` / `TST-CON-053.3` (`v2.createComposeRule`) |
| `TranslationParityTest` | `11/11 PASS` | 9-locale `strings_devices.xml` parity (`REQ-CON-053.3`) |
| 12 Polar sibling suites (`PolarPpiStreamProcessorTest`, `PolarOfflineGattCoordinatorCoexistenceTest`, `PolarVeritySenseOfflineControllerTest`, `PolarH10OfflineControllerTest`, `PolarRawTelemetry*Test`, `Polar*EstimatorTest`, `PolarOfflineCapabilityStoreTest`, `PolarRetrySyncCoordinatorTest`, `WorkoutSamplesHrBackfillTest`) | `87/87 PASS` | Polar PMD/GATT/offline regression |
| 6 Contract suites (`ArchitectureConformance`, `DeadCodeElimination`, `InterfaceSignatureDrift`, `SourceTextReadingAudit`, `SystemLiveTrackingRequirements`, `UiReflectionOnlyTestAudit`) | `34/34 PASS` | LoC ceilings, signatures, `0` dead code, `0` `src/main` reads, `SYSREQ` parity |
| **Total** | **`173/173 PASS`** | **All 22 suites green (`0` failed, `0` skipped)** |

### Verbatim `TST-CON-053` Method Citations (9/9 Verified on `79e9e1b1`)
- `PolarPmdStreamCoordinatorTest#testLaunchStream_enforcesAtomicSingleFlightAndPreAcquireRollback_TST_CON_053_1`
- `PolarPmdStreamCoordinatorTest#testCancelledJobFinally_neverClobbersNewerSlotOrCleanup_TST_CON_053_1`
- `PolarPmdStreamCoordinatorTest#testAtomicSlotCleanupAndPreAcquireException_preservesAccChannelOwnership_TST_CON_053_1`
- `PolarPmdStreamCoordinatorTest#testCollectPmdRecovery_handlesTypedCauseMessageAndResetsCounterOnSample_TST_CON_053_2`
- `PolarPmdStreamCoordinatorTest#testExhaustionDeduplicatesWarningAndRespectsMultiDeviceOwnership_TST_CON_053_3`
- `PolarOnlineStreamingBridgeTest#testReentrantHandshakeBurst_preventsHrToggleStormAndDuplicatePmdStarts_TST_CON_053_1`
- `PolarOnlineStreamingBridgeTest#testPmdAlreadyInStateRecovery_recoversAccGyroAndPpiAfterStopStreamingAndBackoff_TST_CON_053_2`
- `PolarOnlineStreamingBridgeTest#testPmdAlreadyInStateExhaustion_emitsSingleDeduplicatedWarningAndClearsOnDisconnect_TST_CON_053_3`
- `PolarPmdStreamWarningEffectTest#testPolarPmdStreamWarningEffect_displaysLocalizedSnackbarAndDismissesOnDisconnect_TST_CON_053_3`

---

## 3. Static Analysis & Governance Parity
- **4/4 Python Safety Linters**: `check_secrets_hygiene.py`, `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py` -> **PASS**.
- **Governance & Clean-Checkout Evidence**: `--sync-indexes`, `verify_requirement_governance.py --repo-root . --base-ref "$BASE"`, and `--check-verified-evidence --test-results-dir /tmp/empty_check_ATT-9161_s5b1` -> **EXIT `0`**.
- **Branch Preservation (`REQ-PRO-125`)**: `feature/ATT-9161` kept intact and unmerged for Stage 6 Cold Final Review re-audit.
