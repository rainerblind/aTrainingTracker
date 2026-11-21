# Stage 2: Requirement & Test Specification - ATT-9386: Weekly Multi-OS & 9-Language Emulator UI Verification Sweep (2026-KW47)

**Ticket**: [ATT-9386](https://issue-tracker/ATT-9386) | **Parent Epic**: [ATT-232](https://issue-tracker/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467)  
**Requirement Mapping**: `REQ-PRO-135` (`REQ-PRO-135.1`–`.5`), `REQ-TST-004` | **Test Spec ID**: `TST-PRO-135` (`TST-PRO-135.1`–`.5`)  
**Living Docs Note**: Operational weekly execution of existing `[Verified]` `REQ-PRO-135` / `TST-PRO-135`; zero edits to `docs/requirements/req_process_stability_part2.md` or `docs/tests/tst_process_stability_part2.md`.  
**Branch**: `feature/ATT-9386` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-21

---

## 1. Operational Requirement Mapping (`REQ-PRO-135`)

### 1.1 Attributes
- **Priority**: Medium | **Type**: Non-Functional | **Verification Method**: Integration / On-Device Manual / Review

### 1.2 Operational Verification Contract & Invariants
1. **Serial Multi-OS Sweep (`REQ-PRO-135.1`, `REQ-TST-004`)**: Execute `tools/run_multi_os_emulator_sweep.py` with freshly assembled `--apk` (`app-debug.apk`) and `--test-apk` (`app-debug-androidTest.apk`) serially (1 AVD at a time on `emulator-5554`, physical `66020DLCR002FL` strictly excluded) across `Pixel_5_Android_12` (`en`), `Pixel_8_Android_15` (`de`), and `Pixel_7_Android_36` (`fr`).
2. **All-9-Locale UI Sweep (`REQ-PRO-135.1`, `REQ-PRO-135.5`)**: On reference AVD `Pixel_8_Android_15`, iterate all 9 supported locales (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) via `cmd locale set-app-locales`, executing `MultiLocaleUiSweepAndroidTest` (`OK (`), `am start -W`, `uiautomator dump` bounds validation, `logcat` defect scan, and `screencap`.
3. **Visual Inspection & Archival (`Rule 8.5`, `REQ-PRO-135.3`)**: Visually inspect every captured `.png` under `docs/engineering/emulator_sweeps/img/2026-11-21/` via `read_file`, archive `docs/engineering/emulator_sweeps/2026-11-21_sweep.md`, and verify `verify_requirement_governance.py` passes.
4. **9-Language Localization Parity & Defect Policy (`Rule 5.1`)**: `0` edits to `app/src/main/` or `strings*.xml` in `ATT-9386`; any genuine UI, OS, or localization defects discovered are documented in the sweep report and handed off / logged as follow-up Backlog Bugs under `ATT-232` / `ATT-8138` per `Rule 5.1`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (Tier-1 Serial 3-AVD Multi-OS Sweep — `TST-PRO-135.1`, `TST-PRO-135.2`)**: *Given* idle `emulator-*` state (`66020DLCR002FL` ignored) and debug + androidTest APKs, *When* `run_multi_os_emulator_sweep.py` boots `Pixel_5_Android_12` (`en`), `Pixel_8_Android_15` (`de`), and `Pixel_7_Android_36` (`fr`) strictly serially, *Then* each AVD installs both APKs, passes `MultiLocaleUiSweepAndroidTest` (`OK (`), captures active `com.atrainingtracker.debug` UI XML + PNG, passes `logcat`/`bounds` checks (or records real defects cleanly), and terminates via `adb emu kill` in `finally`.
* **AC 2 (Tier-2 9-Locale Sweep — `TST-PRO-135.5`)**: *Given* `Pixel_8_Android_15` booted as reference AVD, *When* the 9 locales (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) execute sequentially, *Then* `get-app-locales` confirms each locale, `MultiLocaleUiSweepAndroidTest` exits `OK (`, and 9 valid PNG screenshots + XML bounds analyses complete cleanly.
* **AC 3 (Visual Inspection & Archive Governance — `Rule 8.5`, `TST-PRO-135.3`, `TST-PRO-135.4`)**: *Given* the generated report and PNG screenshots, *When* every PNG is inspected via `read_file` and `verify_requirement_governance.py` runs, *Then* `docs/engineering/emulator_sweeps/2026-11-21_sweep.md` + `img/2026-11-21/*.png` are archived, governance exits `0`, and `is_weekly_emulator_sweep_overdue()` evaluates `False`.

---

## 2. Operational Test Procedure (`TST-PRO-135` Execution)
1. **Build**: `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest` (`flock`-serialized per `Rule 8.6`).
2. **Pre-Flight**: Confirm no active `emulator-*` is occupying port `5554` (stop idle `emulator-5554` cleanly if unused by other lanes); confirm physical `66020DLCR002FL` is never targeted (`REQ-TST-004`).
3. **Execute Sweep**: Run `python3 tools/run_multi_os_emulator_sweep.py --apk app/build/outputs/apk/debug/app-debug.apk --test-apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
4. **Inspect & Govern**: Inspect all 12 captured PNGs (`3` OS + `9` locales) via `read_file` (`Rule 8.5`); verify `python3 tools/verify_requirement_governance.py --repo-root . --base-ref sprint/2026-11-20_03_Nacht` exits `0`.

---

## 3. Traceability Matrix

| Requirement ID | Operational Verification Scope | Test Case ID | Evidence Artifact |
| :--- | :--- | :--- | :--- |
| `REQ-PRO-135.1`, `.5` | Serial 3-AVD Multi-OS + 9-locale `MultiLocaleUiSweepAndroidTest` & `logcat`/bounds | `TST-PRO-135.1`, `.5` | `2026-11-21_sweep.md` + `img/2026-11-21/*.png` |
| `REQ-PRO-135.2`, `REQ-TST-004` | Strict `emulator-*`-only execution (`66020DLCR002FL` untouched) | `TST-PRO-135.2` | `run_multi_os_emulator_sweep.py` `emulator-5554` logs |
| `REQ-PRO-135.3`, `.4` | Archive under `docs/engineering/emulator_sweeps/` + `verify_requirement_governance.py` | `TST-PRO-135.3`, `.4` | `2026-11-21_sweep.md` + governance exit `0` |
