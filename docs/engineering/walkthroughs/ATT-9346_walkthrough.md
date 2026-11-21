# Stage 5 Verification Walkthrough: ATT-9346 — Fix Polar H10 Live Running Dynamics (VO & GCT) Remaining Empty Despite Active ACC Cadence Streaming (Bounce 3)

**Ticket**: `ATT-9346` (`2 SP`, `Bug`, `severity-major`, Risk: `MEDIUM`) | **Parent Epic**: `ATT-5910`  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-21_01_Früh` (Sprint 468) | **Verified Code SHA**: `939725f4` (`feature/ATT-9346` preserved intact for Stage 6 Bounce 3 Cold Re-Audit per `Rule 3.3`)  
**Living Docs Promoted**: `REQ-SET-122` (`docs/requirements/req_settings_profiles.md` `v1.0.25`, `[Verified]`) <-> `TST-SET-124` (`docs/tests/tst_settings_profiles.md` `v1.0.24`, `[Verified]`) | **Localization**: `N/A - 0 UI strings`

---

## 1. Root Cause, Fix & Stage 6 Bounce 1–3 Linearizability Summary
* **Root Cause**: Polar H10 connects immediately on `BANALService.onCreate()` (cold launch and post-crash `RESUME_SERVICE_RECREATION`) while `BSportType.UNKNOWN` is active, negotiating **`100 Hz`** `ACC` (`dt = 10 ms`) once inside `PolarOnlineStreamingBridge.startAccCollection()`. When `BSportType.RUN` activated later, `syncCadenceSensorRegistration(true)` registered `VERTICAL_OSCILLATION` & `GROUND_CONTACT_TIME` without renegotiating `StreamSlot.ACC` to `200 Hz` — so `PolarLiveRunningCadenceEstimator` (50 Hz resampler) streamed `CADENCE`/`STRIDES` cleanly while `PolarH10RunningDynamicsEstimator`'s `avgDtMs <= 7.5 ms` gate suppressed 100% of VO/GCT (`0 / 2,742`).
* **Implemented Fix & Bounce 1–3 Linearizable Concurrency Hardening (`939725f4`)**:
  1. `PolarPmdStreamCoordinator.kt` (`497` LoC): Linearizable `launchStream()` (`preAcquire()` executes under `slotLock` BEFORE `state.active.set(true)`, eliminating speculative `isActive == true` during rejected `preAcquire`); single-flight sibling-scope `renegotiateSlot()` with `onOldSlotCancelled` `ACC_OWNER_ONLINE` release under `slotLock`, atomic `slotLock` handoff (`state.renegotiateJob === myJob`, ownership clear, `isAllowedToRelaunch()`, and `relaunch()` all under `slotLock`), `onAborted` outside `slotLock`, `cancelAllRenegotiations(): Set<StreamSlot>` wired into `cancelAllStreams()`, `computeDesiredAccRateHz()`, and pure `buildSettingWithRate()`.
  2. `PolarOnlineStreamingBridge.kt` (`1,492 / 1,500` LoC): Per-connection `availableAccRates`/`activeAccSampleRateHz` tracking, `MULTISPORT_TRANSITION_INTENT` filter, `maybeRenegotiateAccRate()` (resetting `runningDynamicsEstimator` only to preserve cumulative `STRIDES`, dynamic `isRunSportActive()` re-evaluation on relaunch, zero-loop guard when `200 Hz` absent, Verity Sense `52 Hz` isolated), and linearizable Dekker `resumeAccAfterOfflineSync` handoff in `tryAcquireOnlineAccChannel()` & `onAccRenegotiationAborted()` (guarded by `!isActive(ACC) && !isRenegotiating(ACC)` against stale cancelled renegotiations) consumed in `resumeAfterOfflineSync()`.
  3. `PolarH10RunningDynamicsEstimator.kt` (`546` LoC): Reset `avgDtMs = DEFAULT_DT_MS` inside `resetForGap()`.

---

## 2. Bug Fix Verification Evidence (`REQ-PRO-121.2`, `Rule 7.5` Doc-Only Fast Path)
* **Zero Code Drift Since Gate 4 Bounce 3 (`Rule 7.5`)**: `git diff --name-only 939725f4..HEAD` touches `0` files under `app/`; Stage 5 Bounce 3 reuses Gate 4 Bounce 3 (`ATT-9462` @ `939725f4`) Tier-1 targeted Gradle verification directly without redundant recompilation.
* **Reproduction & Fix Validation (`11/11` `TST-SET-124` Methods)**:
  - `PolarOnlineStreamingBridgeTest#testH10LateRunActivation_renegotiatesAcc100To200HzPreservesStridesAndEmitsVoGct_TST_SET_124_1` & `testH10RapidSportFlipDuringRenegotiation_convergesToLatestSport_andUnsupported200HzHasZeroLoop_TST_SET_124_1`: H10 `UNKNOWN` (`100 Hz`) -> `RUN` (`200 Hz`) inside `processAccData()` preserves `STRIDES` & emits bounded `VO`/`GCT` to `MySensorManager`, downgrades to `100 Hz` on `MULTISPORT_TRANSITION_INTENT` (`BIKE`), converges on rapid mid-renegotiation flips, zero-loops on `50/100 Hz`-only firmware, and isolates Verity Sense (`52 Hz`).
  - `PolarPmdStreamCoordinatorTest#testRenegotiateSlot_*_TST_SET_124_2` (`3` methods) & `testLaunchStream_doesNotSpeculativelyActivateSlotDuringPreAcquire_TST_SET_124_2` *(Bounce 3)*: Proves `isActive(ACC) == false` throughout rejected/in-flight `preAcquire()`, sibling-coroutine self-trigger survival, single-flight dedup, `ACC_OWNER_ONLINE` lease release/re-acquire, atomic `slotLock` handoff (zero TOCTOU), `onAborted`, and `DROP_OLDEST` (`64`).
  - `PolarOnlineStreamingBridgeTest#testH10AccRenegotiation_cancelledOnDisconnectOfflineYieldAndShutdownWithoutLateRestart_TST_SET_124_2`, `testH10OfflineYieldMidStopStreaming_abortsWithoutRestartDuringYieldAndRecovers200HzVoGctOnResume_TST_SET_124_2` *(Bounce 1)*, `testH10FeatureReadyDuringOfflineYield_defersAccAndRecovers200HzVoGctOnResume_TST_SET_124_2` *(Bounce 2)*, & `testH10StaleAbortedRenegotiationAndConcurrentYieldResume_respectsReplacementAndLinearizesPreAcquire_TST_SET_124_2` *(Bounce 3)*: Proves zero ACC starts during offline yield, automatic `200 Hz` `ACC`/`VO`/`GCT` recovery on `resumeAfterOfflineSync()`, and stale cancelled renegotiation `onAborted` never interfering with a replacement renegotiation.
  - `PolarH10RunningDynamicsEstimatorTest#testResetForGap_afterLowRate100HzBurst_recoversOn200HzAsymmetricWaveform_TST_SET_124_3`: `100 Hz` -> `> 500 ms` gap (`resetForGap()`) -> `200 Hz` asymmetric stance/flight sternum waveform recovers `VO` & `GCT` cleanly.

| Tier-1 Targeted Suite (`--rerun`, SHA `939725f4`) | Result | Traces |
| :--- | :---: | :--- |
| `PolarPmdStreamCoordinatorTest` | **`10/10 PASS`** | `TST-SET-124.2` (`+1` Bounce 3), `ATT-9161`, `ATT-9343` |
| `PolarOnlineStreamingBridgeTest` | **`49/49 PASS`** | `TST-SET-124.1`/`.2`/`.3` (`+1` Bounce 3), `ATT-9175` |
| `PolarH10RunningDynamicsEstimatorTest` | **`5/5 PASS`** | `TST-SET-124.3`, `REQ-SET-116.2` |
| `PolarOfflineGattCoordinatorCoexistenceTest` | **`10/10 PASS`** | `REQ-SET-118`, `REQ-SET-120` |
| `PolarDualImuBoundedHeapStressTest` | **`1/1 PASS`** | `REQ-STB-023` (`DROP_OLDEST` bounded heap) |
| `PolarLiveRunningCadenceEstimatorTest` | **`5/5 PASS`** | `REQ-SET-114` (`strides/min` continuity) |
| `ArchitectureConformanceContractTest` | **`11/11 PASS`** | `TST-SET-124.4` (`PolarOnlineStreamingBridge.kt` `1,492/1,500`) |
| `InterfaceSignatureDriftContractTest` | **`3/3 PASS`** | `TST-SET-124.4` (`0` JVM signature drift) |
| **Tier-1 Targeted Total** | **`94/94 PASS`** (`0` fail, `0` err, `0` skip, `0` new prod warnings) | Scope: 8 polar/build suites; full suite deferred to Tier-2 (`Rule 7.5`) |

* **Non-UI Backend Scope (`Rule 7.6`)**: Pure BLE PMD streaming & DSP bugfix with zero UI/resource changes; physical Polar H10 outdoor run validation is deferred to Ceremony 2 (`Pixel 10`) per `Rule 7.6`.
* **Safety Linters & Governance**: `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py` (`3/3 PASS`); `verify_requirement_governance.py --sync-indexes`, `--check-living-doc-layout`, and `--check-verified-evidence` (`EXIT 0`); `ANT-Android-SDKs` (`160000`) intact.
* **Branch Readiness (`Rule 3.3`)**: `feature/ATT-9346` remains intact (NOT squashed onto `sprint/2026-11-21_01_Früh`) and ready for Gate 5 Bounce 3 + Stage 6 Bounce 3 Cold Re-Audit.
