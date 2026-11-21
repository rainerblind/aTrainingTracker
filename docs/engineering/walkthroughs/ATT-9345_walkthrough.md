# Walkthrough: ATT-9345 — Polar Verity Sense Workout PPI Gating, HR Stream Health Monitor & Primary HR Arbitration

- **Ticket**: `ATT-9345` (`3 SP`, `severity-critical` Bug, Parent Epic `ATT-5910`)
- **Target Release**: `V5.0.0` (Active/Unreleased)
- **Branch / Verified Code SHA**: `feature/ATT-9345` @ `1099f4a9` (`0` `app/` edits in Stage 5 Bounce 1; `Rule 7.5` Doc-Only Fast Path reusing Gate 4 Bounce 1 `ATT-9575` independent verification)
- **Living Docs Promoted**: `REQ-SET-126` (`docs/requirements/req_settings_profiles.md` `v1.0.39`) & `TST-SET-128` (`docs/tests/tst_settings_profiles.md` `v1.0.38`) -> `[Verified]`
- **Localization Parity**: `N/A - 0 string changes` (`0` edits to `res/values*/strings*.xml`)

## 1. Summary of Changes & Stage 6 Bounce 1/3 Hardening
1. **Verity Sense Resting-Only PPI Gating & Workout Teardown (`REQ-SET-126.1`)**:
   - `PolarOnlineStreamingBridge.kt` (`1,485 / 1,500` LoC; unchanged constructor & public JVM signatures) gates Verity Sense `startPpiStreaming()` strictly to `READY`/`IDLE` via `PolarHrStreamHealthMonitor.shouldStreamVerityPpi(...)`, re-checking inside `launchStream(StreamSlot.PPI)` `preAcquire` AND after `getAvailableOnlineStreamDataTypes()` suspends (closing the `READY -> TRACKING` TOCTOU window).
   - Subscribed `sportChangedReceiver` to `TrainingApplication.TRACKING_STATE_CHANGED`; entering `TRACKING`/`PAUSED` cancels `StreamSlot.PPI`, invokes `api.stopStreaming(sessionId, PmdMeasurementType.PPI)`, immediately clears `mRrIntervalSensor?.newValue(null)`, resets `ppiStreamProcessor` + `perDeviceHrvController.onAccumulatorsReset()` + `hrHealthMonitor.resetRrAndOpticalArtifactState()`, and calls `reevaluatePrimaryHrSensor()` without interrupting `HR`, `ACC`, or `GYRO`.
   - **Bounce 1/3 Hardening**: Guarded `processPpiData()` entry on `shouldStreamVerityPpi(true, mode)` so racing `Dispatchers.IO` frames cannot re-latch `659 ms` during workouts, and added `forceStopFirmwarePpi = true` on workout-mode connect and `recoverHrStream()` to halt orphaned firmware PPI streams left across crash restarts.
2. **HR/RR Stream Health Monitor & Stale/Flatline Recovery (`REQ-SET-126.2`)**:
   - Pure-JVM `PolarHrStreamHealthMonitor.kt` (`389` LoC) integrated into `PolarOnlineStreamingBridge.kt` (via active-owner-guarded `PolarPmdStreamCoordinator.OneShotTimer`, `599 / 1,500` LoC) and `BTLEHeartRateDevice.java` (`592 / 1,500` LoC, Bluetooth SIG `0x2A37` contact-bit parsing + Bounce 1 `decision.getAcceptForSensor()` gate on `0x10` RR parsing).
   - Rejects `hr <= 0`/lost contact without disarming `BleHrmSilentConnectionWatchdog`; clears HR/RR after `>= 10 s` invalid or `> 15 s` silence and RR after `>= 10 s` `ARTIFACT_GAP`-only bursts; detects dual-gated flatlines (`>= 60 s` AND `>= 30` consecutive identical samples during workouts or `>= 95%` optical artifact saturation) with immediate non-sticky recovery on the first changing `hr > 0` sample.
   - **Bounce 1/3 Hardening**: Added drift-free `nextCheckDelayMs(nowMs)` (`MIN_CHECK_DELAY_MS = 250L`) across both bridge & `BTLEHeartRateDevice`, re-armed `hrStalenessTimer` in `resumeAfterOfflineSync()` & `evaluateHrStaleness()` (ensuring `60 s` recovery cooldown reachability), and re-synced `lastPublishedTier` inside `resetRrAndOpticalArtifactState()`.
3. **Health- & Modality-Aware Primary `HR` Arbitration (`REQ-SET-126.3`)**:
   - Lock-free `HrSensorHealthProvider.kt` (`72` LoC) and pure-JVM `PrimaryHrSensorArbitrator.kt` (`144` LoC) in `MySensorManager.java` (`733 / 1,500` LoC, outside-lock `ProxySensor` dispatch per `REQ-SEN-021.3`) order HR candidates deterministically by `(healthTier, modalityTier [ECG < ANT+ < Optical PPG < Other], registrationOrder)`, retaining highest-modality binding when all candidates are stale.
4. **Architecture & Detailed Design (`REQ-SET-126.4`, `REQ-PRO-114`)**:
   - Updated `docs/architecture/detailed_design/my_btle_device.md` (`v1.0.9`). Zero edits to `DeviceManager.java`, `TrackerService.java`, `DevicesDatabaseManager.java`, and `PolarVeritySenseOfflineController.kt`.

## 2. Verification Evidence (`REQ-PRO-121.2` Bug-Fix Matrix & `Rule 7.5` Reuse)
Reused Gate 4 Bounce 1 (`ATT-9575` @ `1099f4a9`) independent **Tier-1 Targeted Unit & Contract Suite (`130/130 PASS`, `0` failures, `0` errors, `0` skips, `0` new production compiler warnings)** — zero redundant Gradle re-runs since `0` `app/` lines changed in Stage 5 Bounce 1:

| Suite (`app/src/test/...`) | Result | Verified Scope (`REQ-SET-126` / `TST-SET-128`, `24/24` new methods) |
| :--- | :--- | :--- |
| `PolarHrStreamHealthMonitorTest` | `7/7 PASS` | Resting PPI gating; `hr <= 0`/lost-contact watchdog preservation & `10 s` clear; `15 s` silence & `60 s` cooldown; `10 s` artifact RR clear; dual-gated `60 s` + `30`-sample flatline & non-sticky recovery; drift-free `nextCheckDelayMs` & optical reset tier re-sync |
| `PrimaryHrSensorArbitratorTest` | `6/6 PASS` | Healthy ECG H10 > Optical Verity regardless of order; failover on stale/degraded H10; healthy H10 > flatlined Verity; `ECG < ANT+ < Optical < Other`; all-stale retention; non-relaxed MockK guard |
| `PolarOnlineStreamingBridgeTest` | `56/56 PASS` | `+7` new: Verity `READY -> TRACKING` explicit `stopStreaming(PPI)` + RR clear; `hr=0`/lost-contact watchdog & `10 s` clear; `10 s` `ARTIFACT_GAP` RR clear; `processPpiData` workout drop; workout-connect & `recoverHrStream` `forceStopFirmwarePpi`; `startPpiCollection` suspend TOCTOU abort; `resumeAfterOfflineSync` & `evaluateHrStaleness` timer re-arm |
| `PolarPmdStreamCoordinatorTest` | `11/11 PASS` | `+1` new: `OneShotTimer` active-owner guard prevents superseded timers from invoking stale `onElapsed()` |
| `MySensorManagerPriorityTest` | `8/8 PASS` | `+1` new: Verity-first registration binds H10 ECG, dynamic failover/failback via `reevaluatePrimaryHrSensor()` verified outside `synchronized (sensorManager)` |
| `BTLEHeartRateDeviceTest` | `18/18 PASS` | `+2` new: `0x2A37` `hr=0`/lost-contact watchdog preservation & `10 s` clear + `0x10` RR-bit gate blocking bogus RR dispatch on lost-contact/zero packets |
| `BleHrmSilentConnectionWatchdogTest`, `PolarPpiStreamProcessorTest` | `7/7 PASS` | `3/3` + `4/4` adjacent regression suites green |
| `ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest`, `DetailedDesignParityContractTest` | `17/17 PASS` | `11/11` + `3/3` + `3/3` monolith ceilings, public JVM signature pin, and `my_btle_device.md` parity verified |

- **Python Safety & Governance Checks**: `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`, `check_secrets_hygiene.py` (`4/4 PASS`), and `verify_requirement_governance.py --base-ref sprint/2026-11-21_02_Spät --check-verified-evidence --check-living-doc-layout --test-results-dir /tmp/no_xmls` (`EXIT 0`, `0` unbacked).
- **Emulator / Runtime Check**: Non-UI BLE/arbitration fix (`0` UI/string changes); independent live `emulator-5554` verification executes in Stage 6 Cold Neon Review (`Rule 3.3`).
- **Branch State (`Rule 3.3` / `Rule 8.2`)**: `feature/ATT-9345` preserved intact (not merged onto `sprint/2026-11-21_02_Spät` prior to Stage 6 `PASS`).
