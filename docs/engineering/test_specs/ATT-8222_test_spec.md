# Stage 2: Requirement & Test Specification - ATT-8222

**Ticket**: [ATT-8222](https://rainerblind.atlassian.net/browse/ATT-8222) (`3 SP`, Medium) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`
**Branch**: `feature/ATT-8222` | **Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-20
**Requirement / Test IDs**: `REQ-PRO-135` (`REQ-PRO-135.1`–`REQ-PRO-135.5`) / `TST-PRO-135` (`TST-PRO-135.1`–`TST-PRO-135.5`)

---

## 1. Attributes & Invariants
* **Priority**: Medium | **Type**: Non-Functional | **Verification Method**: Unit / Integration / Review
* **`SYS.2` / `SYS.3` Impact**: None (`REQ-PRO-131.4`: test tooling & release-verification infrastructure only).
* **Localization Parity**: `N/A - no UI strings` (tooling tests all 9 app locales at runtime without adding string resources).
* **Invariants**: `0` `app/src/main/` edits; `REQ-TST-004` physical-phone refusal (`66020DLCR002FL` never targeted); max `1` AVD booted at a time with `finally` `adb emu kill`; `100%` offline mocked Python unit tests (`Rule 5`); Python 3 stdlib only (`TST-PRO-005.6`); `0` edits to dirty user `tools/` files.

---

## 2. Requirements (`REQ-PRO-135` `[Planned]` in `docs/requirements/req_process_stability_part2.md`)
* **`REQ-PRO-135.1` (Serial Multi-OS + 9-Language Sweep Runner — `tools/run_multi_os_emulator_sweep.py`)**: Dynamically resolves installed AVDs (`>= 3` API levels, configurable via `--avds`), boots strictly `1` AVD at a time via `agent_emulator.py` helpers, injects mock GPS (`adb emu geo fix`), installs `com.atrainingtracker.debug`, sweeps all `9` locales (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) on the reference AVD, scans `logcat` (`FATAL EXCEPTION`, `ANR`, `StrictMode`, `IllegalFormat*`) & `uiautomator dump` bounds for clipped/overlapping text, and shuts down cleanly in `finally`.
* **`REQ-PRO-135.2` (Physical-Device Guardrail)**: Enforces `emulator-*` serials exclusively and raises `RuntimeError` immediately if any non-emulator serial is passed or resolved.
* **`REQ-PRO-135.3` (Sweep Archive & Layout Registry)**: Writes `docs/engineering/emulator_sweeps/YYYY-MM-DD_sweep.md` + screenshot artifacts; adds `"emulator_sweeps"` to `ALLOWED_ENGINEERING_ENTRIES` in `tools/verify_requirement_governance.py` and registers it in `docs/management/cm_plan.md`.
* **`REQ-PRO-135.4` (Weekly Cadence & Release Gate)**: `tools/jira_util.py` adds `is_weekly_emulator_sweep_overdue()` (`> 7 days` since latest `*_sweep.md` with new `develop` commits) and `ensure_weekly_emulator_sweep_ticket()` (deduplicated under `ATT-232`, `rank_top=True`), wired into `ensure_weekly_reviews()` (updating `tools/test_jira_accounts.py:1648-1655` `TST-PRO-096.3` 3-pillar mock & assertion); updates `docs/management/qa_plan.md` §7 and `.agents/skills/release-manager/SKILL.md` Phase 1 (`<= 7 days` gate).
* **`REQ-PRO-135.5` (Instrumented Click-Through & Offline Tests)**: Adds deterministic `app/src/androidTest/` UI navigation click-through test compiled with `:app:compileDebugAndroidTestKotlin`, plus offline mocked unit suites (`tools/test_run_multi_os_emulator_sweep.py`, `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py`).

---

## 3. Acceptance Criteria & Traceability Matrix (`TST-PRO-135` `[Planned]`)

| TST Sub-ID | Target File / Suite | Given-When-Then Acceptance Criterion |
| :--- | :--- | :--- |
| **`TST-PRO-135.1`** | `tools/test_run_multi_os_emulator_sweep.py` | *Given* mocked `avdmanager`/`emulator`/`adb` across `3+` AVDs and `9` locales (`en..pt`), *When* `run_sweep()` executes, *Then* AVDs boot serially (max 1 concurrent), `adb emu geo fix` runs, all `9` locales switch on the reference AVD, clean shutdown runs even on simulated failure (`finally`), and `YYYY-MM-DD_sweep.md` is archived. |
| **`TST-PRO-135.2`** | `tools/test_run_multi_os_emulator_sweep.py` | *Given* physical serial `66020DLCR002FL`, *When* sweep target validation runs, *Then* `RuntimeError` aborts before any ADB command executes; mocked `logcat` `IllegalFormatConversionException`/`FATAL EXCEPTION`/`ANR` and `uiautomator dump` bounds overlap/zero-size nodes are detected and flagged as defects. |
| **`TST-PRO-135.3`** | `tools/test_verify_requirement_governance.py` | *Given* `docs/engineering/emulator_sweeps/` and `ALLOWED_ENGINEERING_ENTRIES`, *When* `verify_engineering_directory_structure()` runs + `cm_plan.md` is checked, *Then* `"emulator_sweeps"` passes directory layout validation (`exit 0`). |
| **`TST-PRO-135.4`** | `tools/test_jira_accounts.py` | *Given* mocked `docs/engineering/emulator_sweeps/` (`empty` or `> 7 days` with new `develop` commits vs fresh `<= 7 days`), mocked Jira JQL, and updated `test_ensure_weekly_reviews_coordinates_both_pillars` (3 pillars), *When* `ensure_weekly_emulator_sweep_ticket()` / `ensure_weekly_reviews()` run offline (`Rule 5`), *Then* overdue sweeps create deduplicated `rank_top=True` tickets under `ATT-232`, `qa_plan.md` §7 & `release-manager/SKILL.md` document the gate. |
| **`TST-PRO-135.5`** | `:app:compileDebugAndroidTestKotlin` | *Given* new multi-screen UI navigation click-through `androidTest` class under `app/src/androidTest/`, *When* `./gradlew :app:compileDebugAndroidTestKotlin` executes locked, *Then* compilation succeeds with `0` errors and `0` `app/src/main/` modifications. |
