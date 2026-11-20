# Stage 3 Implementation Plan: ATT-8222 - Weekly & Pre-Release Multi-OS & 9-Language Emulator Sweep

**Ticket**: [ATT-8222](https://rainerblind.atlassian.net/browse/ATT-8222) (`3 SP`, Medium) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`
**Requirements / Tests**: `REQ-PRO-135` (`REQ-PRO-135.1`–`REQ-PRO-135.5`) / `TST-PRO-135` (`TST-PRO-135.1`–`TST-PRO-135.5`)
**Branch**: `feature/ATT-8222` | **Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-20

---

## 1. SWE.2 Architecture, Guardrails & Constraints
* **Component Layering**: Pure Python 3 stdlib orchestration (`tools/run_multi_os_emulator_sweep.py`) delegating AVD/ADB primitives to `tools/agent_emulator.py`, Jira backlog scheduling to `tools/jira_util.py`, governance validation to `tools/verify_requirement_governance.py`, and instrumented UI click-through verification to `app/src/androidTest/` (`0` `app/src/main/` modifications).
* **Hard Guardrails (`REQ-TST-004`, `Rule 5`)**:
  1. Strict `emulator-*` serial allow-list (never target physical `Pixel 10` / `66020DLCR002FL`); abort immediately with `RuntimeError` on any non-emulator serial.
  2. Strictly serial AVD execution (max `1` running AVD at a time, `-no-snapshot-save`, `adb emu kill` cleanup guaranteed in `finally`).
  3. `100%` offline mocked unit tests (`subprocess`/`adb`/`emulator`/`avdmanager`/Jira API mocked; `0` live AVD launches in unit tests).
  4. `Localization Parity: N/A - no UI strings`; `0` edits to dirty user-owned `tools/` files or `docs/presentations/vorgehensmodell/`.

---

## 2. Atomic Stage 4 Implementation Steps

| Step | Target File(s) | Atomic Action | Maps to |
| :--- | :--- | :--- | :--- |
| **0** | Jira Gate 3 | Run `python3 tools/jira_util.py --as agent1 check-gate <IMPL_PLAN_SUBTASK_KEY>` (`exit 0`, `Rule 4`) before any edits under `app/`, `tools/`, `docs/management/`, or `.agents/`. | Gate 3 Lock |
| **1** | `tools/run_multi_os_emulator_sweep.py` | Implement stdlib-only 2-tier serial sweep CLI: (a) dynamic AVD selection across `>= 3` API levels via `agent_emulator.get_available_avds()` or `--avds`; (b) `REQ-TST-004` `emulator-*`-only validator raising `RuntimeError` on physical serials; (c) serial boot (`-no-snapshot-save`), APK install, `adb emu geo fix`, and `finally` `adb emu kill`; (d) 9-locale sequential sweep (`en,de,es,fr,it,ja,nl,pl,pt`) on reference AVD via `cmd locale set-app-locales`; (e) `logcat` fatal/ANR/`StrictMode`/`IllegalFormat*` scanner & `uiautomator dump` XML clipped/ellipsized/overlap analyzer; (f) structured markdown report + screenshot gallery writer under `docs/engineering/emulator_sweeps/YYYY-MM-DD_sweep.md`. | `REQ-PRO-135.1`, `.2`, `.3` |
| **2** | `tools/verify_requirement_governance.py`, `docs/management/cm_plan.md`, `docs/engineering/emulator_sweeps/README.md` | Add `"emulator_sweeps"` to `ALLOWED_ENGINEERING_ENTRIES` (`verify_requirement_governance.py:533`), document sweep directory & archive baseline policy in `cm_plan.md`, and initialize `docs/engineering/emulator_sweeps/README.md` so clean-checkout governance passes. | `REQ-PRO-135.3` |
| **3** | `tools/jira_util.py`, `docs/management/qa_plan.md`, `.agents/skills/release-manager/SKILL.md` | Add `is_weekly_emulator_sweep_overdue()` (`> 7 days` since latest `*_sweep.md` & new `develop` commits) and `ensure_weekly_emulator_sweep_ticket()` (deduplicated under `ATT-232`, `rank_top=True`), wire into `ensure_weekly_reviews()` as 3rd pillar; document cadence in `qa_plan.md` §7 and add pre-release sweep currency gate (`<= 7 days`) in `.agents/skills/release-manager/SKILL.md` (`verify_skills.py` stays PASS). | `REQ-PRO-135.4` |
| **4** | `app/src/androidTest/java/.../MultiLocaleUiSweepAndroidTest.kt` | Add deterministic `AndroidJUnit4` instrumented multi-screen navigation & Start/Pause/Stop tracking smoke click-through test (with non-destructive cleanup matching `AppSmokeAndroidTest.kt`) and verify compilation via locked `./gradlew :app:compileDebugAndroidTestKotlin`. | `REQ-PRO-135.5` |
| **5** | `tools/test_run_multi_os_emulator_sweep.py`, `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py` | Add 100% offline mocked tests tagged explicitly with `TST-PRO-135.1`–`TST-PRO-135.5` (including serial mock, `66020DLCR002FL` rejection, `finally` shutdown on exception, `logcat`/`uiautomator` defect fixtures, `ALLOWED_ENGINEERING_ENTRIES` `"emulator_sweeps"`, and updating `test_ensure_weekly_reviews_coordinates_both_pillars` for 3-pillar mocking + `ensure_weekly_emulator_sweep_ticket` tests). Verify `verify_skills.py`, all Python suites, `:app:compileDebugAndroidTestKotlin`, and `verify_requirement_governance.py --check-verified-evidence --check-living-doc-layout`. | `TST-PRO-135.1`–`.5` |

---

## 3. Rollback & Invariant Verification
* **Verification Gate**: `python3 -m unittest` on `tools/test_run_multi_os_emulator_sweep.py`, `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py`, `python3 tools/verify_skills.py`, `python3 tools/parallel_sprint_lock.py ./gradlew :app:compileDebugAndroidTestKotlin`, and `verify_requirement_governance.py --check-verified-evidence --check-living-doc-layout --test-results-dir /tmp/empty_check`.
* **Rollback**: Purely additive tooling, `androidTest` class, and governance docs isolated on `feature/ATT-8222`; `app/src/main/` and existing `ensure_weekly_reviews` callers preserve backward-compatible contracts.
