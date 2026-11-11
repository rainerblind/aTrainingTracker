# Stage 1 Analysis: ATT-6684 - tools/replay_workout.py: Compute and pass --speed to set-test-provider-location during workout simulation

**Ticket**: [ATT-6684](https://rainerblind.atlassian.net/browse/ATT-6684)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.2`  
**Branch**: `feature/ATT-6684`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

During Sprint 2026-46.1 review of `ATT-6256`, workout replays via `tools/replay_workout.py` on connected test devices injected 1 Hz coordinates into `gps`, `network`, and `fused` mock location test providers via:
```bash
cmd location providers set-test-provider-location <provider> --location <lat>,<lon> --accuracy 2.5 --time <time_ms>
```
without passing `--speed <SPEED_MPS>`.

Because `--speed` is omitted from the injection command and the physical test phone is sitting stationary on a desk (Activity Recognition: `STILL confidence=100`, Significant Motion Detector: `smd stationary: true`), mocked `android.location.Location` objects arrive with `hasSpeed() == false` or `getSpeed() == 0.0f`. This triggers stationary Doppler pinning (`isDopplerStationary == true` or stationary fallback deadband in `SpeedAndLocationDevice.java:322-377`), suppressing speed calculations during desktop test replays.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification**:
  1. **Existing Speed Extraction in `TcxParser`**:
     * In `tools/replay_workout.py:257-273` and `373-408` (`REQ-TOOL-006.1`), `TcxParser` already extracts recorded `<Speed>` from TCX `<Extensions>` or computes ground speed $\Delta \text{dist} / \Delta t$ between consecutive points with zero-division (`dt >= 0.001`) and `math.isfinite` guards.
     * Both `ReplayGuiWindow._emit_telemetry` (L1963) and `run_headless_cli` (L2089) already pass `pt.speed_mps` to `gps.inject_location(pt.lat, pt.lon, pt.ele, pt.speed_mps, pt.bearing, time_sec=pt.time_sec)`.
  2. **The Disconnect in `AdbGpsInjector`**:
     * `AdbGpsInjector.inject_location()` receives `speed_mps` in its signature, but drops it when queuing coordinates (`self._queue.put_nowait((lat, lon, timestamp_ms))`), ignoring `speed_mps` completely.
     * `_worker_loop()` builds commands via `build_inject_cmd(p, lat, lon, time_ms, accuracy=2.5)`, which lacks any `--speed` parameter.
  3. **Critical Chesterton's Fence & AOSP Framework Limitation**:
     * **AOSP Ground Truth**: Testing `adb shell cmd location providers set-test-provider-location gps --location 48.1,11.5 --accuracy 2.5 --speed 5.0` on Android 15 (`emulator-5554`) fails with:
       `java.lang.IllegalArgumentException: Unknown option: --speed` (exit code 255).
     * In standard AOSP `LocationShellCommand.handleSetTestProviderLocation()`, the shell parser exclusively supports `--location`, `--accuracy`, and `--time`.
     * In commit `358d94ec` (`ATT-2969`), `tools/test_replay_workout.py:560` explicitly asserted:
       `self.assertNotIn("--speed", cmd)` because stock Android rejected `--speed`.
     * **Architectural Conclusion**: Unconditionally passing `--speed` will cause 100% of location injections to fail on standard Android 14/15/16 devices. The solution MUST format `--speed {speed_mps:.2f}` when provided, but pair it with a defensive auto-probing fallback: if the shell returns `Unknown option: --speed`, `AdbGpsInjector` immediately disables the flag and transparently retries without `--speed`.
  4. **Downstream Mobile App Stationary Pinning Forensic**:
     * On physical devices resting on a desk, Google Play Services Fused Location Provider may report `hasSpeed == true` and `speed == 0.0f` due to hardware IMU fusion. When `rawDelta <= escapeRadius` (3.75 m at 2.5 m accuracy, i.e., up to ~13.5 km/h), `SpeedAndLocationDevice.java:372` pins the stationary anchor on each 1 Hz tick. While passing `--speed` to test providers is the intended tool enhancement requested in ATT-6684, platform limitations mean stock AOSP cannot receive `--speed` via ADB shell. Mock-aware Doppler pinning in the mobile app (`LocationCompat.isMock()`) represents an adjacent architectural consideration for Epic ATT-166, but is bounded out of scope for this 1 SP tool ticket under ATT-1250.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Add optional `speed_mps: Optional[float] = None` to `AdbGpsInjector.build_inject_cmd()`. When provided and non-negative, append `--speed {speed_mps:.2f}`.
  2. Forward `speed_mps` through `AdbGpsInjector.inject_location()` into `self._queue` as `(lat, lon, timestamp_ms, speed_mps)`, maintaining backward compatibility for 2-tuple and 3-tuple items.
  3. In `_worker_loop()`, unpack `speed_mps` and inject with defensive fallback: if ADB returns exit code 255 with `Unknown option: --speed`, set `self._supports_speed_flag = False`, emit a single explanatory log notice, and re-issue the command without `--speed`.
  4. Expose CLI argument `--speed-injection / --no-speed-injection` in `tools/replay_workout.py`.
  5. Expand `tools/test_replay_workout.py` with comprehensive unit tests for `build_inject_cmd`, queue tuple handling, and fallback behavior.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Zero modifications to Android production code in `app/src/main/` (strictly tool-only scope; 1 SP).
  * No modifications to `TcxParser` speed math (already validated under `REQ-TOOL-006.1`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-TOOL-007` (*Simulated Ground Speed Injection & AOSP Fallback in AdbGpsInjector*), extending `REQ-TOOL-002` and `REQ-TOOL-006` in `docs/requirements/req_process_stability.md`.
* **Historical Origin & Commit Trace**: Commit `358d94ec` (`ATT-2969`), `458f386e` (`ATT-3927`).
* **Root Reason for Existing Formulation**: `tools/test_replay_workout.py` asserted `self.assertNotIn("--speed", cmd)` because standard AOSP `LocationShellCommand` rejected `--speed` with `IllegalArgumentException`.
* **Preservation of Core Invariants**: Calling `build_inject_cmd()` without `speed_mps` produces the exact Android 14+ syntax without `--speed`. When `speed_mps` is provided, `--speed` is passed, with graceful fallback on stock AOSP devices. Existing tests remain 100% green.

---

## 5. Architectural Strategy & High-Level Solution

1. **`AdbGpsInjector.build_inject_cmd`**:
   ```python
   @classmethod
   def build_inject_cmd(cls, provider: str, lat: float, lon: float, time_ms: int, accuracy: float = 2.5, speed_mps: Optional[float] = None) -> str:
       cmd = f"cmd location providers set-test-provider-location {provider} --location {lat:.6f},{lon:.6f} --accuracy {accuracy:.1f} --time {time_ms}"
       if speed_mps is not None and speed_mps >= 0.0:
           cmd += f" --speed {speed_mps:.2f}"
       return cmd
   ```
2. **Asynchronous Injection Queue**:
   * `inject_location(..., speed_mps: float = 0.0, ...)` queues `(lat, lon, timestamp_ms, speed_mps)`.
   * Backward compatibility: `_worker_loop` handles 2-tuples, 3-tuples, and 4-tuples gracefully.
3. **Defensive Auto-Probing & Graceful Fallback**:
   * `self._supports_speed_flag: Optional[bool] = None`.
   * When `self._supports_speed_flag is not False`, inject with `speed_mps`.
   * If ADB stderr contains `Unknown option: --speed`, set `self._supports_speed_flag = False` and retry without `--speed`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero modifications to Android application production code (`app/src/main/`).
  2. 100% backward compatibility: existing 39 unit tests in `tools/test_replay_workout.py` must remain green.
  3. No broken injection sessions: stock AOSP devices must not abort replay when `--speed` is rejected.
* **Risk Rating**: **LOW** (developer tooling change with automatic graceful fallback guard).
