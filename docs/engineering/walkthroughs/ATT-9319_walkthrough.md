# Stage 5: Walkthrough & Verification - ATT-9319

**Ticket**: [ATT-9319](https://rainerblind.atlassian.net/browse/ATT-9319) (`2 SP`, `severity-major`) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `None (In-Sprint)`
**Active Sprint**: `2026-11-20_02_Spät` (Sprint 466) | **Branch**: `feature/ATT-9319` (Gate 4 Bounce 1 SHA: `80d100d1`, `ATT-9373` PASS)
**Requirement / Test Mapping**: `REQ-PRO-135` (`REQ-PRO-135.1`, `REQ-PRO-135.5` `[Verified]`) / `TST-PRO-135` (`TST-PRO-135.1`–`TST-PRO-135.5` `[Verified]`)
**Author**: Lane C Flash (`agent1`) | **Date**: 2026-11-20

---

## 1. Executive Summary & Rule 7.5 Fast Path

* **Implemented Fixes (`06623b57` + Bounce 1 `80d100d1` — `tools/run_multi_os_emulator_sweep.py` & `tools/test_run_multi_os_emulator_sweep.py`, `0` edits under `app/`)**:
  1. Added `--test-apk` (`test_apk_path`) to CLI and `run_sweep()`, with fail-closed `install_apk()` checks for both `--apk` and `--test-apk`.
  2. Added `verify_instrumentation_output()` enforcing `am instrument` `returncode == 0`, presence of `OK (`, and absence of `FAILURES!!!` / `INSTRUMENTATION_FAILED` / `INSTRUMENTATION_ABORTED` / `Process crashed`.
  3. Fixed post-`am instrument` capture in `run_ui_probe()` by calling `am start -W -n <package>/<activity>`, clearing `/sdcard/att_sweep_dump.xml`, capturing `uiautomator dump` + `exec-out screencap -p`, validating non-empty target-package non-launcher XML via `validate_captured_ui_xml()`, and checking PNG `\x89PNG` signature.
  4. Made `set_app_locale()` API-aware: API >= 33 verifies `cmd locale set-app-locales`/`get-app-locales` strictly via `verify_get_app_locales_output()` (Bounce 1 `80d100d1`: parses bracketed `[...]` tags and verifies `_locale_tag_matches()`, eliminating `"de"` in `"debug"` / `"es"` in `"locales"` substring false positives when `are []`); API < 33 uses `ro.product.locale` check, `adb root` + `uid=0` enforcement (failing transparently on non-rootable Play Store images), `persist.sys.locale` + `settings put system system_locales`, `setprop sys.boot_completed 0` + `stop`/`start` + `service check activity == found` polling, and `am force-stop <package>`.
  5. Isolated per-locale failures via `try/except` in `run_sweep()` and added `## 3. Failure & Defect Diagnostics` to `write_sweep_report()`.
* **Rule 7.5 Doc-Only Fast Path**: `git diff 80d100d1..HEAD -- app/ tools/` is completely empty (`0` lines); Gate 4 Bounce 1 (`ATT-9373` @ `80d100d1`: `4/4` targeted unit tests + `4/4` safety linters + governance) and unchanged API 31 / suite-wide Gate 4 (`ATT-9369` @ `06623b57`: `530/530` Python tests + live `Pixel_5_Android_12` smoke) evidence are reused with zero redundant runs.

---

## 2. Verification Matrix

| Scope / Spec | Exact File / Class / Method Citations | Result | Status |
| :--- | :--- | :--- | :--- |
| `TST-PRO-135.1` | `TestMultiOsEmulatorSweep::test_tst_pro_135_1_serial_multi_os_and_9_locale_sweep_and_cleanup_on_failure`, `TestMultiOsEmulatorSweep::test_tst_pro_135_1_api31_locale_fallback_and_non_rootable_error` (`100%` offline mocked @ `80d100d1`) | **PASS (`2/2`)** | `Verified` |
| `TST-PRO-135.2` | `TestMultiOsEmulatorSweep::test_tst_pro_135_2_fail_closed_install_instrument_launcher_and_screencap` (incl. `[]`/`[en]`/`[de]`/`[es-ES]` & missing-brackets `verify_get_app_locales_output` negative/positive tests), `TestMultiOsEmulatorSweep::test_tst_pro_135_2_physical_device_refusal_and_logcat_and_bounds_defects` (`@ 80d100d1`) | **PASS (`2/2` — `4/4` suite in `0.063s`)** | `Verified` |
| `TST-PRO-135.3`, `.4` | Preserved `ATT-8222` citations (`TestGrandfatheredSysreqsRemoved::test_scenario_89_tst_pro_135_3_emulator_sweeps_allowed_in_engineering_layout`, `TestJiraCliTooling::test_ensure_weekly_reviews_coordinates_both_pillars`, `TestProcessStabilityLegacyTsts::test_tst_pro_135_4_weekly_emulator_sweep_cadence_and_gate_docs`) in full `tools/test_*.py` run (`@ 06623b57`) | **PASS (`530/530` OK, `118.594s`)** | `Verified` |
| `TST-PRO-135.5` (APKs) | Locked `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest` + `MultiLocaleUiSweepAndroidTest::sweepCoreScreensAndTrackingLifecycle_withoutCrashing_TST_PRO_135_5` | **PASS (`BUILD SUCCESSFUL in 1m 19s`)** | `Verified` |
| `TST-PRO-135.5` (Live Smoke) | Bounded `Pixel_5_Android_12` (`emulator-5554`, API 31 `google_apis`) run (`--locales de`, `--apk`, `--test-apk` @ `06623b57`): `en` + `de` `am instrument` (`error=None`), active `MainActivityWithNavigation` App UI verified visually via `read_file` | **App-UI Verified** (exit `1` due to real runtime findings below) | `Verified` |
| Safety Linters & Governance | `check_secrets_hygiene.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`, `lint_cursor_safety.py` (`@ 80d100d1`) + `verify_requirement_governance.py --sync-indexes` & `--check-verified-evidence` | **PASS (`4/4` linters + governance `exit 0`)** | `Verified` |

---

## 3. Live Emulator Smoke, Follow-Ups & Invariants

* **Visual Screenshot Audit (`read_file`)**: `/tmp/att9319_smoke/img/2026-11-20/Pixel_5_Android_12_en.png` and `Pixel_5_Android_12_loc_de.png` both render genuine active `MainActivityWithNavigation` English and German App UI (not Android Launcher). Per PO directive, smoke PNGs were **not** copied into the repository and remain in `/tmp/att9319_smoke/` for Stage 6 Cold Review.
* **Surfaced Runtime Findings -> Backlog Follow-Ups**: The live smoke run accurately failed (`Verdict: FAIL`, exit `1` in `/tmp/att9319_smoke/2026-11-20_sweep.md`) on 7 `StrictMode` lines + 1 `ZERO_OR_INVERTED_BOUNDS` node (`'No routes nearby'` / `'Keine Strecken in der Nähe'`) per locale; filed as Backlog tickets **`ATT-9363`** (filter `logcat` to app PIDs) and **`ATT-9364`** (`'No routes nearby'` `0x0` bounds node).
* **Invariants**: `adb devices` confirms `0` leftover running emulators and physical `Pixel 10` (`66020DLCR002FL`) untouched (`REQ-TST-004`); `Localization Parity: N/A - Python tooling only`; `feature/ATT-9319` preserved intact for Stage 6 Cold Review.
