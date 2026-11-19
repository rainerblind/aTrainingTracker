# Stage 5 Walkthrough (Bounce 2): ATT-8965 — Extended Sensor Reconnect / Re-Search Backoff & Search Settings

**Ticket**: [ATT-8965](https://rainerblind.atlassian.net/browse/ATT-8965) (`REQ-CON-052`, `TST-CON-052`) | **Branch**: `feature/ATT-8965`  
**Owner**: Agent 1 (`--as agent1`) | **Status**: Verified | **Date**: 2026-11-19

## 1. Summary of Verified Implementation & Bounce 2 Hardening
1. **Shared Extended Backoff, Live Clamping & Preferences (`SensorReconnectBackoffPolicy.kt`, `SensorSearchSettings.kt`)**:
   - Progresses reconnect/re-search delays along `1s -> 2s -> 4s -> 8s -> 16s -> 32s -> 64s -> 128s (~2 min) -> <finalPeriodMs>` (default `300,000 ms` = `5 min`, configurable discrete presets `1m/2m/5m/10m`) and resets to `1s` on connect.
   - `effectiveDelayMs(...)` clamps stale backoff delays against live settings (`16,000 ms` when disabled, `finalPeriodMs` when enabled) BEFORE scheduling retries in both `MyBTLEDevice.java` (`L493`, `L604`) and `PolarOnlineStreamingBridge.kt`.
2. **BLE, Polar SDK & DeviceManager Concurrency + Cleanup (`MyBTLEDevice.java`, `PolarOnlineStreamingBridge.kt`, `DeviceSearchPeriodicRetryCoordinator.kt`, `DeviceManager.java`)**:
   - Preserves `MyBTLEDevice.INITIAL_RECONNECT_DELAY_MS = 1000L` & `MAX_RECONNECT_DELAY_MS = 16000L` (`SYSREQ-TRK-002`), `DeviceManager.java = 1,707` LoC (`<= 1,708`), and `TrainingApplication.java = 2,134` LoC (`<= 2,135`).
   - `DeviceSearchPeriodicRetryCoordinator`: invokes `onDisabledGiveUp` (`shutDown()` + `mMyRemoteDevices` removal) when periodic re-search is disabled while a retry timer is pending, schedules inside `synchronized(lock)` with runnable-identity checks, and uses double-checked synchronized init in `DeviceManager.getPeriodicRetryCoordinator()`.
   - `PolarOnlineStreamingBridge`: prevents `1s -> 4s` double-advance on timeout/disconnect echo via `reconnectLock` + active-job guard, reschedules on transient `connectPersistentSession()` exceptions when active & BT enabled (`notifyStopSearching(false)` only if `isSearching`), and cancels pending reconnect on `blePowerStateChanged(false)` / resumes on `blePowerStateChanged(true)`.
3. **Dual Search Settings UI & 9-Locale Parity**:
   - Synchronized toggle & final-period selector in `SearchSettingsDialog.kt` (`SingleChoiceSegmentedButtonRow`, `icon = {}`) and `SensorSearchTuningSection.kt` (`TuningToggleItem` + `TuningSegmentedButtonRow` per `docs/design_guidelines.md` §1.4), `res/xml/prefs_search.xml`, and all 9 `res/values*/strings_devices.xml` locales (validated on `emulator-5554` in `de` locale during Stage 6 Bounce 1 review).

## 2. Verification & Static Analysis Evidence (`Rule 7.5` — Reused Directly from Gate 4 Bounce 2 `587730da`)
- Zero `app/` changes since Gate 4 Bounce 2 (`git diff 587730da HEAD -- app/` empty); zero redundant `./gradlew` runs.
- **Gate 4 Bounce 2 (`ATT-9088`) & Author Stage 4 Bounce 2 (`587730da`)**: **`128/128` PASS** (`0` failed, `0` skipped, `0` new compiler warnings) across 16 incremental `--rerun` suites: `SensorReconnectBackoffPolicyTest` (6/6), `DeviceSearchPeriodicRetryCoordinatorTest` (5/5), `BLEReconnectRobustnessTest` (13/13), `DeviceManagerSearchQueueTest` (6/6), `PolarOnlineStreamingBridgeTest` (26/26), `SearchSettingsDialogTest` (2/2), `SensorSearchTuningSectionTest` (1/1), `SensorSearchTuningContractTest` (6/6), `TuningDesignGuidelinesContractTest` (5/5), `TranslationParityTest` (10/10), `SystemLiveTrackingRequirementsContractTest` (4/4), `DetailedDesignParityContractTest` (3/3), `ArchitectureConformanceContractTest` (11/11), `MyBTLEDeviceLifecycleTest` (11/11), `MyBTLEDeviceAddressAndSearchTest` (11/11), `MyBTLEDeviceQueueTest` (8/8).
- **Static Baselines, Linters & Governance**: `check_static_analysis_baselines.py` (`detekt = 9509`, `lint = 238`), `check_secrets_hygiene.py`, `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`, and `verify_requirement_governance.py --base-ref refs/heads/sprint/2026-11-19_01_Früh`: all `exit 0`.
- **Hardware Boundary (`Rule 7.6`)**: Physical Polar Verity Sense long-range BLE walk-away timing remains optional PO hardware acceptance.
