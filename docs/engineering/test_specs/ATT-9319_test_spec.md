# Stage 2: Requirement & Test Specification - ATT-9319: Fail-Closed Sweep Capture, `--test-apk`/Instrument Checks & API < 33 Locale Fallback

**Ticket**: [ATT-9319](https://issue-tracker/ATT-9319)  
**Sub-task**: Pending creation (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-8138](https://issue-tracker/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11 – Evidence Integrity, Effective Gates & Verification to CL2*)  
**Target Release**: `None (In-Sprint)`  
**Active Sprint**: `2026-11-20_02_Spät` (Sprint 466)  
**Requirement Mapping**: `REQ-PRO-135` (`REQ-PRO-135.1`, `REQ-PRO-135.2`, `REQ-PRO-135.5` — refines `ATT-8222`)  
**Test Spec ID**: `TST-PRO-135` (`TST-PRO-135.1`, `TST-PRO-135.2`, `TST-PRO-135.5`)  
**Branch**: `feature/ATT-9319`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-20  

---

## 1. Requirement Specification (`REQ-PRO-135` Refinement)

### 1.1 Attributes
- **Priority**: Medium | **Type**: Non-Functional | **Verification Method**: Unit / Integration / Review

### 1.2 Functional & Architectural Requirements (`tools/run_multi_os_emulator_sweep.py`)
1. **`--test-apk` & Fail-Closed ADB / `am instrument` Validation (`_run_adb:271-279`, `run_ui_probe:356-416`, `run_sweep:526-529`, `build_cli_parser:604-616`)**:
   - `build_cli_parser()` and `run_sweep()` SHALL accept `--test-apk` (`test_apk_path: Optional[str] = None`) and install it via `adb install -r -d <test_apk_path>` when provided; BOTH `--apk` and `--test-apk` installs MUST raise `RuntimeError` on non-zero `returncode` (with `adb_install` failure text).
   - `run_ui_probe()` SHALL validate `am instrument` fail-closed: `returncode == 0`, absence of `FAILURES!!!`, `INSTRUMENTATION_FAILED`, `INSTRUMENTATION_ABORTED`, or `Process crashed`, AND presence of `OK (` in `stdout` (raising `RuntimeError` or failing the run cleanly on mismatch).
2. **Foreground App-UI Capture & Anti-Launcher Verification (`run_ui_probe:356-416`)**:
   - After `am instrument` finishes (`MultiLocaleUiSweepAndroidTest.kt:87` `ActivityScenario.use {}` closes `MainActivityWithNavigation`), `run_ui_probe()` SHALL explicitly launch `am start -W -n <package>/<DEFAULT_MAIN_ACTIVITY>`, wait briefly (`sleep_fn`), and validate that `uiautomator dump` XML is **non-empty** and does **not** belong to a Launcher package (`nexuslauncher`, `launcher3`, `android.launcher`) and that `exec-out screencap -p` produces a **non-empty** PNG (checking `\x89PNG` header).
3. **API-Aware `set_app_locale()` with Android 12 Fallback (`set_app_locale:345-353`)**:
   - Query `adb shell getprop ro.build.version.sdk`. On **API >= 33**, invoke `cmd locale set-app-locales <package> --locales <locale>` (`returncode == 0`) and verify via `cmd locale get-app-locales <package>`.
   - On **API < 33** (e.g., Android 12 API 31): check `getprop persist.sys.locale`; if already matching `<locale>`, force-stop `<package>` only; otherwise acquire root via `adb root` + `adb wait-for-device` + verify `adb shell id` contains `uid=0` (raising transparent `RuntimeError` on non-rootable/Play Store API < 33 images), set `persist.sys.locale` + `settings put system system_locales`, execute runtime restart (`stop`/`start` + `sys.boot_completed == 1` wait) + `am force-stop <package>`, and verify `getprop persist.sys.locale`.
4. **9-Language Localization Parity**: **N/A — host Python QA verification script only** (`0` `app/src/main/res/values*/strings*.xml` modifications; `9` locales remain tested in sweep runtime).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-135` (`REQ-PRO-135.1`, `.5`), introduced in `ATT-8222` (`e11a8123`), targeting `tools/run_multi_os_emulator_sweep.py` and `tools/test_run_multi_os_emulator_sweep.py`.
* **Historical Origin & Commit Trace**: Commit `e11a8123` (`ATT-8222`, Sprint `2026-11-19_03_Nacht`) implemented the multi-OS & 9-language emulator sweep runner under mocked ADB tests.
* **Root Reason for Existing Formulation**: Unit-test mocks returned empty `stdout` and `returncode=0` for `am instrument`, masking `ActivityScenario.use {}` Activity teardown before `screencap`, missing `--test-apk`, unverified `am instrument` text, and API < 33 `cmd locale` absence.
* **Preservation of Core Invariants**: Strictly preserves Python 3 stdlib-only (`TST-PRO-005.6`), `REQ-TST-004` `emulator-*`-only serial guard (`66020DLCR002FL` untouched), 1-AVD serial execution + `finally` `adb emu kill`, zero `app/src/main/` changes, and 100% offline mocked unit tests (`Rule 5`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (`TST-PRO-135.1`)**: *Given* `--apk` + `--test-apk`, API >= 33 and API < 33 AVDs, and `am instrument` returning `OK (1 test)`, *When* `run_sweep()` executes, *Then* both APKs install, `set_app_locale()` handles API >= 33 (`cmd locale`) & API < 33 (`adb root` + `uid=0` + `persist.sys.locale` + runtime restart), `am start -W` precedes `uiautomator dump` + PNG `screencap`, and `adb emu kill` runs in `finally`.
* **AC 2 (`TST-PRO-135.2`)**: *Given* failing `adb install`, `am instrument` outputting `FAILURES!!!` / `INSTRUMENTATION_FAILED` / missing `OK (` / `returncode != 0`, empty or `nexuslauncher` XML, failed PNG `screencap`, or non-rootable API < 33 `uid != 0`, *When* `run_ui_probe()` / `set_app_locale()` / `run_sweep()` executes, *Then* each fails closed (`passed == False` / `RuntimeError`) with zero silent false passes, and `66020DLCR002FL` raises `RuntimeError`.
* **AC 3 (`TST-PRO-135.5`)**: *Given* a booted local `emulator-5554` (`REQ-TST-004`), *When* a Stage 4 smoke run invokes `run_multi_os_emulator_sweep.py` with `--apk` and `--test-apk`, *Then* `am instrument` passes and captured PNGs show active App UI (not Launcher).

---

## 2. Test Specification (`TST-PRO-135` Updates)
* **`TST-PRO-135.1` / `.2` (`tools/test_run_multi_os_emulator_sweep.py`)**: Run `python3 -m unittest tools/test_run_multi_os_emulator_sweep.py` covering positive `--apk`/`--test-apk`/foreground `am start -W`/API 33+/API < 33 fallback AND negative mocked failure paths (failed `adb install`, `am instrument` non-zero / `FAILURES!!!` / `INSTRUMENTATION_FAILED` / missing `OK (`, launcher XML `com.google.android.apps.nexuslauncher`, empty XML, missing PNG, non-root `uid=2000` on API < 33, physical serial `66020DLCR002FL`).
* **`TST-PRO-135.5` (Stage 4 Real Emulator Smoke)**: Strictly targeting `emulator-5554` (`REQ-TST-004`), assemble debug + androidTest APKs and verify real App-UI screenshot + `am instrument` evaluation cleanly.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-PRO-135.1` | `--test-apk`, fail-closed ADB/`am instrument`, foreground `am start -W`, API < 33 fallback | `TST-PRO-135.1`, `TST-PRO-135.2` | Offline Unit (`Rule 5`) |
| `REQ-PRO-135.2` | `REQ-TST-004` `emulator-*`-only serial guard across all ADB calls (incl. `adb root`) | `TST-PRO-135.2` | Offline Unit (`Rule 5`) |
| `REQ-PRO-135.5` | Offline fail-closed matrix + live `emulator-5554` smoke screenshot/instrument check | `TST-PRO-135.5` | Unit + Emulator Smoke |
