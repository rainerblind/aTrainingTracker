# Stage 2: Requirement & Test Specification - ATT-3971: Release ADB mock location test providers in replay_workout.py immediately on Pause, Stop, or completion

**Ticket**: [ATT-3971](https://rainerblind.atlassian.net/browse/ATT-3971)  
**Sub-task**: [ATT-4038](https://rainerblind.atlassian.net/browse/ATT-4038) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-3842](https://rainerblind.atlassian.net/browse/ATT-3842) (*Cycling Power Metrics*)  
**Target Release**: `Pending Final Review (Rule 14)`  
**Active Sprint**: `Sprint 2026-42.8`  
**Requirement Mapping**: `REQ-TRK-025` (*Deterministic `AdbGpsInjector` Mock Provider Lifecycle Bound to `PLAYING` State in `tools/replay_workout.py`*)  
**Test Spec ID**: `TST-TRK-017`  
**Branch**: `feature/ATT-3971`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Requirement Specification (REQ-TRK-025)

### 1.1 Problem Statement & Rationale
When `tools/replay_workout.py` registered ADB mock location test providers (`gps`, `network`, `fused`) immediately on startup (`setup()`) and only removed them at process exit (`teardown()`), unplugging a phone while the replay was paused, stopped, or completed left Android's `LocationManager.GPS_PROVIDER` indefinitely trapped in `[mock]` mode, blocking real hardware GNSS updates (`gps_hardware`).

### 1.2 Functional & Architectural Requirements
1. **Proactive Startup & `--cleanup` Test Provider Removal (`REQ-TRK-025.1`)**:
   - On startup (`AdbGpsInjector.setup()`, while `ReplayState.STOPPED`) and when invoked with `--cleanup`, `AdbGpsInjector` SHALL proactively remove any lingering `gps`, `network`, and `fused` test providers (`cmd location providers set-test-provider-enabled <p> false` and `cmd location providers remove-test-provider <p>`) without registering new test providers while `STOPPED` (`self.providers_registered = False`).
2. **Lazy Registration on `PLAYING` & Immediate Removal on `PAUSED` / `STOPPED` / `EOF` (`REQ-TRK-025.2`)**:
   - `AdbGpsInjector` SHALL track `self.providers_registered: bool = False`, register `add-test-provider gps/network/fused` lazily only when entering `ReplayState.PLAYING` (`on_play()` / `enable_providers()` / `inject_location()` when `not self.is_killed and not self.providers_registered`), and immediately execute `release_providers()` (`remove-test-provider gps/network/fused` and `self.providers_registered = False`) whenever playback transitions to `PAUSED` (`on_pause()`), `STOPPED` (`on_stop()`), `kill_gps(True)`, timeline completion (`EOF`), or `teardown()`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Startup & `--cleanup` Cleanup)**:
  * *Given* `AdbGpsInjector.setup()` called at startup while `STOPPED` (or `replay_workout.py --cleanup` invoked from CLI),
  * *When* inspected before playback starts,
  * *Then* lingering `gps`, `network`, and `fused` test providers are removed (`remove-test-provider`) and `providers_registered` is `False`.
* **Criterion 2 (Lazy Registration on `PLAYING` & Immediate Removal on `PAUSED`/`STOPPED`/`EOF`)**:
  * *Given* active playback (`on_play()` / `inject_location()` with `providers_registered == True`),
  * *When* the user pauses (`on_pause()`), stops (`on_stop()`), toggles `kill_gps(True)`, or playback reaches the end of the timeline (`EOF`),
  * *Then* `release_providers()` immediately executes `remove-test-provider` for `gps`, `network`, and `fused` and sets `providers_registered = False`.

### 1.4 System Invariants
1. Zero regression in `tools/test_replay_workout.py` and `./gradlew testDebugUnitTest`.
2. Zero UI changes or string resource modifications required (9-language localization parity unaffected).
3. Hardware GNSS (`gps_hardware`) is immediately restored whenever replay is paused, stopped, or completed.

---

## 2. Test Specification (TST-TRK-017)

### Test Case 1: `test_adb_gps_injector_removes_test_providers_on_pause_stop_and_eof` (`TST-TRK-017.1`)
* **Scope**: Unit Test (`TestAdbGpsInjector` in `tools/test_replay_workout.py`)
* **Target File**: `tools/test_replay_workout.py`
* **Preconditions**: `AdbGpsInjector` initialized with mocked `subprocess.run`.
* **Action**: Call `setup()`, `on_play()`, `on_pause()`, `on_play()`, `on_stop()`, `inject_location()`, and `kill_gps(True)`.
* **Expected Result**:
  * `setup()` executes `remove-test-provider` for `gps`, `network`, `fused` without calling `add-test-provider` (`providers_registered == False`).
  * `on_play()` / `inject_location()` executes `add-test-provider` and sets `providers_registered == True`.
  * `on_pause()`, `on_stop()`, and `kill_gps(True)` immediately execute `remove-test-provider` for `gps`, `network`, `fused` and set `providers_registered == False`.

### Test Case 2: `test_cleanup_cli_argument` (`TST-TRK-017.2`)
* **Scope**: Unit Test (`TestAdbGpsInjector` in `tools/test_replay_workout.py`)
* **Target File**: `tools/test_replay_workout.py`
* **Preconditions**: CLI arguments `["replay_workout.py", "--cleanup", "--device", "pixel10"]`.
* **Action**: Invoke `replay_workout.main()`.
* **Expected Result**: Calls `cleanup_device_providers()` and returns exit code `0` without requiring a `.tcx` file path.

### Test Case 3: Clean-Room Regression Suite (`TST-TRK-017.3`)
* **Command**: `python3 -m unittest tools/test_replay_workout.py && ./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across Python replay tests and Android unit tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-017.1` | Unit | `AdbGpsInjector.setup`, `on_play`, `on_pause`, `on_stop`, `kill_gps` | `REQ-TRK-025.1`, `REQ-TRK-025.2` | Specified |
| `TST-TRK-017.2` | Unit | `replay_workout.main(--cleanup)` | `REQ-TRK-025.1` | Specified |
| `TST-TRK-017.3` | Regression | `test_replay_workout.py` & `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-TRK-025` | Specified |
