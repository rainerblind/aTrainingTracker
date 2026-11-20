# Stage 3: Implementation Plan - ATT-9319: Fail-Closed Sweep Capture, `--test-apk`/Instrument Checks & API < 33 Locale Fallback

**Ticket**: [ATT-9319](https://issue-tracker/ATT-9319)  
**Sub-task**: Pending creation (`[Impl-Plan]`)  
**Parent Epic**: [ATT-8138](https://issue-tracker/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11 – Evidence Integrity, Effective Gates & Verification to CL2*)  
**Target Release**: `None (In-Sprint)`  
**Active Sprint**: `2026-11-20_02_Spät` (Sprint 466)  
**Requirement Mapping**: `REQ-PRO-135` (`REQ-PRO-135.1`, `REQ-PRO-135.2`, `REQ-PRO-135.5`)  
**Test Mapping**: `TST-PRO-135` (`TST-PRO-135.1`, `TST-PRO-135.2`, `TST-PRO-135.5`)  
**Branch**: `feature/ATT-9319`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-20  

---

## 1. Problem Description & Background
Post-`ATT-8222` (`e11a8123`) code review identified three functional gaps in `tools/run_multi_os_emulator_sweep.py`: (1) `run_ui_probe` captures `uiautomator dump` + `screencap` after `ActivityScenario.use {}` closes `MainActivityWithNavigation`, capturing the Android Launcher; (2) CLI/runner omits `--test-apk` and silently ignores `adb install` / `am instrument` failures; and (3) `set_app_locale()` calls Android 13+ `cmd locale set-app-locales` unconditionally without an API < 33 Android 12 fallback (`Pixel_5_Android_12`).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-135.1` (runner & API < 33 fallback), `REQ-PRO-135.2` (`emulator-*` isolation), `REQ-PRO-135.5` (offline tests & local `emulator-5554` smoke run)
* **Test Mapping**: `TST-PRO-135.1`, `TST-PRO-135.2`, `TST-PRO-135.5` (`tools/test_run_multi_os_emulator_sweep.py` + `emulator-5554` smoke)

---

## 3. System Invariants & Preserved Behavior
1. **Strict Physical-Device Isolation (`REQ-TST-004`)**: Every ADB mutation (including `adb root`, `wait-for-device`, `setprop`, `stop`/`start`) routes through `assert_emulator_only_serial()` (`emulator-*` only; `66020DLCR002FL` strictly unreachable).
2. **Stdlib-Only & Zero App Production Changes**: `tools/run_multi_os_emulator_sweep.py` remains pure Python 3 stdlib (`TST-PRO-005.6`); `0` `app/src/main/` or `strings*.xml` modifications (`UI Consistency: N/A`).
3. **Serial Execution & `finally` Cleanup**: Strictly 1 AVD booted at a time with `shutdown_emulator()` guaranteed in `finally`; all unit tests in `tools/test_*.py` run 100% offline with mocks (`Rule 5`).

---

## 4. Proposed Architectural Changes (`tools/run_multi_os_emulator_sweep.py`)
* **Fail-Closed ADB Execution & Instrumentation Parser**: Add checked ADB helper (`_run_adb_checked` or `check=True` option on `_run_adb:271-279`) and pure parser `verify_instrumentation_output(returncode, stdout, stderr)` rejecting non-zero exit codes, `FAILURES!!!`, `INSTRUMENTATION_FAILED`, `INSTRUMENTATION_ABORTED`, `Process crashed`, or missing `OK (`.
* **Active UI Capture Validator**: After `am instrument`, run `am start -W -n <package>/<DEFAULT_MAIN_ACTIVITY>`, settle briefly (`sleep_fn`), dump XML & capture PNG, and validate via pure helper `validate_captured_ui_xml(xml_text, package)` that XML is non-empty, free of launcher packages (`nexuslauncher`, `launcher3`, `android.launcher`), and contains nodes with `package == package`, plus verify PNG starts with `\x89PNG` (`screencap` non-empty).
* **API-Aware Locale Switcher**: Extract SDK resolver (`get_device_sdk_int`) + API >= 33 (`cmd locale set-app-locales` + `get-app-locales` verification) vs API < 33 (`adb root` + `wait-for-device` + `id` containing `uid=0` check + `persist.sys.locale` / `system_locales` + runtime `stop`/`start` & `sys.boot_completed == 1` wait when locale differs + `am force-stop <package>`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`REQ-PRO-135.1` — `--test-apk`, Fail-Closed Install & `am instrument` Checks in `_run_adb:271-279`, `run_ui_probe:356-416`, `run_sweep:466-601`, `build_cli_parser:604-616`)**:
   - Add `--test-apk` CLI flag (`test_apk_path: Optional[str] = None` in `run_sweep()`).
   - Install `--apk` and `--test-apk` via checked `adb install -r -d` raising `RuntimeError` if `returncode != 0` or `Failure` appears.
   - Validate `am instrument` via `verify_instrumentation_output(...)` (`returncode == 0`, contains `OK (`, rejects `FAILURES!!!` / `INSTRUMENTATION_FAILED` / `INSTRUMENTATION_ABORTED` / `Process crashed`).
2. **Step 2 (`REQ-PRO-135.1` — Active App-UI Capture & Anti-Launcher Validation in `run_ui_probe:356-416`)**:
   - Execute `am start -W -n {package}/{DEFAULT_MAIN_ACTIVITY}` after `am instrument` (checking `returncode == 0` and no `Error:` line) and call `sleep_fn(1.0)` to let Compose/Views settle.
   - Run `uiautomator dump` + `cat` and validate `validate_captured_ui_xml(xml_text, package)` (non-empty XML, rejects launcher packages like `nexuslauncher`/`launcher3`, requires at least one node with `package == package`); also require non-empty `\x89PNG` from `exec-out screencap -p` (recording explicit defect/failure on invalid XML or missing/corrupt PNG).
3. **Step 3 (`REQ-PRO-135.1`, `.2` — API-Aware `set_app_locale:345-353`)**:
   - Query `getprop ro.build.version.sdk`. If `sdk >= 33`: run `cmd locale set-app-locales` + verify `cmd locale get-app-locales` contains `<locale>`.
   - If `sdk < 33`: check current `getprop persist.sys.locale`; if already matching `<locale>`, run `am force-stop <package>`; otherwise acquire root via `adb root` + `wait-for-device` (via `assert_emulator_only_serial`), verify `adb shell id` contains `uid=0` (raising transparent `RuntimeError` on non-rootable/Play Store API < 33 images), set `persist.sys.locale` + `settings put system system_locales`, restart Android runtime (`stop` + `start` + poll `boot_check_fn` up to timeout), `am force-stop <package>`, and verify `getprop persist.sys.locale` matches.
4. **Step 4 (`TST-PRO-135.1`, `.2` — Offline Unit Tests in `tools/test_run_multi_os_emulator_sweep.py`)**:
   - Update existing positive `fake_runner` mock (`test_run_multi_os_emulator_sweep.py:63-85`) to return `"OK (1 test)\n"` for `am instrument`, `package="com.atrainingtracker.debug"` XML nodes, `uid=0` / SDK responses, and valid `\x89PNG` bytes.
   - Add offline mocked tests covering: `--test-apk` installation + failed `adb install`, `am instrument` failures (`returncode != 0`, `FAILURES!!!`, `INSTRUMENTATION_FAILED`, missing `OK (`), launcher/empty XML rejection, missing/invalid PNG rejection, API >= 33 `cmd locale` + verification, API < 33 `adb root` + `uid=0` + `persist.sys.locale` + runtime restart, and non-rootable API < 33 `uid=2000` `RuntimeError`.
5. **Step 5 (`TST-PRO-135.5` — Verification & Live `emulator-5554` Smoke Run)**:
   - Run `python3 -m unittest discover -s tools -p 'test_*.py'` (`100%` pass) and `verify_requirement_governance.py`.
   - Build `:app:assembleDebug :app:assembleDebugAndroidTest` under `parallel_sprint_lock.py` (with temporary `trap EXIT` symlinks) if APKs aren't already built, then execute 1 bounded live smoke sweep strictly on `emulator-5554` (`--allow-fewer-avds --min-levels 1 --avds Pixel_8_Android_15` + `--apk` + `--test-apk`, plus quick check or `Pixel_5_Android_12` verification if feasible), inspect generated PNG via `read_file` to prove active `com.atrainingtracker.debug` App-UI (not Launcher) + `am instrument` `PASS`, and verify clean `shutdown_emulator` (`adb devices` free of lingering emulators).

---

## 6. Verification & Rollback Plan
* **Verification**: Full `tools/` Python unit test suite + `verify_requirement_governance.py` + real local `emulator-5554` smoke run & visual PNG `read_file` check (`66020DLCR002FL` strictly untouched).
* **Rollback**: Isolated on `feature/ATT-9319`; clean `git revert` restores `ATT-8222` baseline.
