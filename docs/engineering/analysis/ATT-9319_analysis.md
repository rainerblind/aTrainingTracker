# Stage 1 Analysis: ATT-9319 - Fix run_multi_os_emulator_sweep.py Capture, Test-APK/Instrument Checks & Android 12 Fallback

**Ticket**: [ATT-9319](https://issue-tracker/ATT-9319)  
**Sub-task**: Pending creation (`[Analysis]`)  
**Parent Epic**: [ATT-8138](https://issue-tracker/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11 – Evidence Integrity, Effective Gates & Verification to CL2*)  
**Target Release**: `None (In-Sprint)`  
**Active Sprint**: `2026-11-20_02_Spät` (Sprint 466)  
**Branch**: `feature/ATT-9319` (from `sprint/2026-11-20_02_Spät` @ `c05689a2`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-20  

---

## 1. Problem Statement & Motivation
* **Current Behavior**: Post-`ATT-8222` (`e11a8123`) code review revealed 3 functional defects in `tools/run_multi_os_emulator_sweep.py` hidden by 100%-mocked unit tests (`tools/test_run_multi_os_emulator_sweep.py`):
  1. `run_ui_probe()` captures `uiautomator dump` + `screencap` **after** `am instrument` finishes, while `MultiLocaleUiSweepAndroidTest.kt:87` wraps `MainActivityWithNavigation` in `ActivityScenario.launch(...).use {}` (which destroys the Activity on exit), capturing the Android Launcher/Home Screen instead of the App UI.
  2. `run_sweep()` installs `--apk` only (`:526-527`, ignoring exit code) with no `--test-apk` support, and `run_ui_probe()` (`:371-387`) never inspects `am instrument` exit code or `stdout` (`OK (` vs `FAILURES!!!` / `INSTRUMENTATION_FAILED`), producing silent false passes.
  3. `set_app_locale()` (`:345-353`) exclusively calls Android 13+ `adb shell cmd locale set-app-locales` (unavailable on Android 12 `Pixel_5_Android_12` API 31) and ignores failures.
* **Expected Behavior**: Fail-closed `--apk`/`--test-apk` installation & `am instrument` verification, guaranteed foreground App-UI capture (`am start -W` + non-empty, non-launcher XML validation), functional API < 33 Android 12 locale fallback with transparent validation, and live `emulator-5554` smoke verification (`REQ-TST-004`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification (Live Codebase + Workstation AVD Audit)**:
  * **Defect 1 Verified (`run_ui_probe():371-416`, `MultiLocaleUiSweepAndroidTest.kt:87-138`)**: Confirmed `ActivityScenario.launch(...).use {}` closes `MainActivityWithNavigation` at end of instrumentation, so subsequent `uiautomator dump` (`:397`) and `exec-out screencap -p` (`:405`) capture the Launcher. Also spotted secondary blind spot: `run_ui_probe()` ignores `screencap` failures (`screenshot_path = None` stays PASS) and `analyze_uiautomator_bounds("")` returns `[]` on empty XML (`:202-203`) -> silent false pass if `uiautomator dump` fails! Fix: after `am instrument`, explicitly launch `am start -W -n {package}/{DEFAULT_MAIN_ACTIVITY}` (plus short settle & fail-closed checks that `uiautomator` XML is non-empty and top/node `package` is not an Android Launcher such as `nexuslauncher`/`launcher`).
  * **Defect 2 Verified (`_run_adb():271-279`, `run_sweep():526-527`, `run_ui_probe():372-387`)**: Confirmed `_run_adb()` does not check `returncode`, CLI lacks `--test-apk`, `adb install -r -d` exit codes are ignored, and `adb shell am instrument` can exit `0` even when `stdout` reports `FAILURES!!!` / `INSTRUMENTATION_FAILED` / missing `OK (`. All ADB mutations (`install`, `am instrument`, `am start -W`, `uiautomator dump`, `emu geo fix`) MUST fail closed on non-zero `returncode` or failed instrumentation output. Note: existing `test_run_multi_os_emulator_sweep.py:63-85` mocks `stdout = ""` for `am instrument` and will need updating alongside new negative tests.
  * **Defect 3 Verified (`set_app_locale():345-353`, workstation AVDs)**: `emulator -list-avds` + AVD configs confirm `Pixel_5_Android_12` / `Pixel_6_Pro_Android_12` use `android-31/google_apis` (API 31, no `cmd locale`, supports `adb root`), while `Pixel_8_Android_15` (`android-35/google_apis`) and `Pixel_7_Android_36` (`android-36/google_apis_playstore`) support `cmd locale`. `app/src/main/` has no manifest `localeConfig` or `AppCompatDelegate.setApplicationLocales` call sites. Fix: query `ro.build.version.sdk`: on API >= 33 use `cmd locale set-app-locales` + verify `get-app-locales`; on API < 33, check `getprop persist.sys.locale`, apply `adb root` + `setprop persist.sys.locale` + `settings put system system_locales` + runtime restart (`stop`/`start` + boot wait if locale differs) + `am force-stop <package>`, and verify/fail loudly on unsupported setups.

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals (`2 SP`)**: Fix all 3 defects in `tools/run_multi_os_emulator_sweep.py`, expand `tools/test_run_multi_os_emulator_sweep.py` (`Rule 5` offline mocks), synchronize `REQ-PRO-135`/`TST-PRO-135`, and execute a real Stage 4 smoke check strictly on `emulator-5554` (`REQ-TST-004`).
* **Out-of-Scope Non-Goals**: No `app/src/main/` or `strings*.xml` edits; physical `Pixel 10` (`66020DLCR002FL`) strictly untouched (`REQ-TST-004`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-135` (`REQ-PRO-135.1`, `.5`) & `TST-PRO-135` (`TST-PRO-135.1`, `.5`) in `docs/requirements/req_process_stability_part2.md:2533` and `docs/tests/tst_process_stability_part2.md:1592`, targeting `tools/run_multi_os_emulator_sweep.py` & `tools/test_run_multi_os_emulator_sweep.py`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-8222` (`e11a8123`, Sprint `2026-11-19_03_Nacht`) under Epic `ATT-8138`.
* **Root Reason for Existing Formulation**: Initial implementation tested workflow logic strictly with mocked ADB responses (`Rule 5`), overlooking Android 12 API < 33 `cmd locale` absence, uninspected `am instrument` exit/stdout, `--test-apk` omission, and `ActivityScenario.use {}` teardown before `screencap`.
* **Preservation of Core Invariants**: Strictly preserves Python 3 stdlib-only (`TST-PRO-005.6`), `emulator-*`-only guardrail (`REQ-TST-004`), single-AVD serial execution + `finally` `adb emu kill`, zero `app/src/main/` modifications, and 100% offline mocked unit tests (`Rule 5`).

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-PRO-135.1` and `REQ-PRO-135.5` (adds `--test-apk`, fail-closed `am instrument` & non-launcher dump checks, API < 33 locale fallback, real `emulator-5554` smoke evidence). `SYS.2`/`SYS.3`: None (developer verification tooling only). Localization parity: N/A (`0` UI string changes).
* **Affected Architecture Elements**: `tools/run_multi_os_emulator_sweep.py`, `tools/test_run_multi_os_emulator_sweep.py` (optionally `MultiLocaleUiSweepAndroidTest.kt` only if needed). Zero production `app/src/main/` changes.
* **Verification Impact & Test Surface**: `TST-PRO-135.1`, `TST-PRO-135.2`, `TST-PRO-135.5`; offline unit test suite `python3 -m unittest tools/test_run_multi_os_emulator_sweep.py`, governance check, plus Stage 4 single-AVD smoke run strictly on `emulator-5554`.
* **Risk Assessment & Invariants**: **MEDIUM** (`2 SP`, `severity-major`) — mitigated by keeping `assert_emulator_only_serial()` ahead of every ADB call (incl. `adb root`), validating mocked negative paths offline, and preserving all 4 user-owned dirty `tools/` files.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: `66020DLCR002FL` unreachable (`REQ-TST-004`); Python 3 stdlib only; `adb emu kill` in `finally`; zero `app/src/main/` changes.
* **Risk Rating**: **MEDIUM** — runtime ADB differences between API 31 (`google_apis`) and API 35/36 (`google_apis` / `playstore`), verified directly on `emulator-5554` in Stage 4.
