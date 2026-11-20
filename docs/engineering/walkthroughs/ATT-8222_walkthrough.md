# Stage 5: Walkthrough & Verification - ATT-8222

**Ticket**: [ATT-8222](https://rainerblind.atlassian.net/browse/ATT-8222) (`3 SP`, Medium) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`
**Active Sprint**: `2026-11-19_03_Nacht` | **Branch**: `feature/ATT-8222` (Gate 4 Code SHA `de14d362`) | **Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-20
**Requirement / Test Mapping**: `REQ-PRO-135` (`REQ-PRO-135.1`–`REQ-PRO-135.5` `[Verified]`) / `TST-PRO-135` (`TST-PRO-135.1`–`TST-PRO-135.5` `[Verified]`)

---

## 1. Executive Summary
1. **Serial Multi-OS & 9-Language Sweep Runner (`REQ-PRO-135.1`, `REQ-PRO-135.2`)**: Added stdlib-only `tools/run_multi_os_emulator_sweep.py` dynamically selecting installed AVDs (`>= 3` API levels via `agent_emulator.get_available_avds()` or `--avds`), booting strictly 1 AVD at a time (`-no-snapshot-save`, `adb emu geo fix`), enforcing `REQ-TST-004` (`assert_emulator_only_serial` strictly refusing physical serials like `66020DLCR002FL`), sweeping all 9 supported locales (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) via `cmd locale set-app-locales`, scanning `logcat` (`FATAL EXCEPTION`, `ANR`, `StrictMode`, `IllegalFormat*`) and `uiautomator dump` XML bounds (clipped/zero-size/ellipsized/overlapping nodes), and guaranteeing `adb emu kill` cleanup in `finally`.
2. **Archive Registry, Weekly Cadence & Pre-Release Gate (`REQ-PRO-135.3`, `REQ-PRO-135.4`)**: Added `"emulator_sweeps"` to `ALLOWED_ENGINEERING_ENTRIES` (`tools/verify_requirement_governance.py`), registered `docs/engineering/emulator_sweeps/` in `docs/management/cm_plan.md`, and created `docs/engineering/emulator_sweeps/README.md`. Integrated `is_weekly_emulator_sweep_overdue()` and `ensure_weekly_emulator_sweep_ticket()` (deduplicated under `ATT-232`, `rank_top=True`) into `tools/jira_util.py ensure_weekly_reviews()`, updated `docs/management/qa_plan.md` (`v1.2.2`), and enforced `<= 7 days` sweep currency in `.agents/skills/release-manager/SKILL.md` Phase 1.
3. **Deterministic Instrumented UI Click-Through (`REQ-PRO-135.5`)**: Added `app/src/androidTest/java/com/atrainingtracker/trainingtracker/MultiLocaleUiSweepAndroidTest.kt` (`sweepCoreScreensAndTrackingLifecycle_withoutCrashing_TST_PRO_135_5`) with `0` edits to `app/src/main/`.

---

## 2. Verification Matrix (Reused Gate 4 Evidence per Rule 7.5 — Zero Code Changes Since `de14d362`)

| TST / Check | Exact Verified Scope & File / Class / Method Citations | Result | Status |
| :--- | :--- | :--- | :--- |
| `TST-PRO-135.1`, `.2` | `python3 -m unittest tools/test_run_multi_os_emulator_sweep.py` (`TestMultiOsEmulatorSweep::test_tst_pro_135_1_serial_multi_os_and_9_locale_sweep_and_cleanup_on_failure`, `test_tst_pro_135_2_physical_device_refusal_and_logcat_and_bounds_defects`, `100%` offline mocked) | **PASS (`2/2` OK)** | `Verified` |
| `TST-PRO-135.3` | `python3 -m unittest tools/test_verify_requirement_governance.py -k tst_pro_135` (`TestGrandfatheredSysreqsRemoved::test_scenario_89_tst_pro_135_3_emulator_sweeps_allowed_in_engineering_layout`) | **PASS (`1/1` OK)** | `Verified` |
| `TST-PRO-135.4` | `python3 -m unittest tools/test_jira_accounts.py` (`TestJiraCliTooling::test_ensure_weekly_reviews_coordinates_both_pillars`, `TestProcessStabilityLegacyTsts::test_tst_pro_135_4_weekly_emulator_sweep_cadence_and_gate_docs`, `Rule 5` offline mocked) | **PASS (`111/111` OK)** | `Verified` |
| `TST-PRO-135.5` | `python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:compileDebugAndroidTestKotlin` (`MultiLocaleUiSweepAndroidTest::sweepCoreScreensAndTrackingLifecycle_withoutCrashing_TST_PRO_135_5`) | **PASS (`BUILD SUCCESSFUL`)** | `Verified` |
| Skills & Governance | `python3 tools/verify_skills.py` (`11/11`), `--sync-indexes`, `--check-verified-evidence --check-living-doc-layout --test-results-dir /tmp/empty_check_8222_s5` | **PASS (`exit 0`, `1,150` backed)** | `Verified` |

---

## 3. On-Device, Localization & Invariants
* **`Rule 7.5` / `Localization`**: Doc-only Stage 5 (`git diff de14d362 -- app/ tools/ .agents/` empty); `0` `app/src/main/` edits; `Localization Parity: N/A - no UI strings`.
* **`REQ-TST-004` & Workspace Hygiene**: Physical device (`66020DLCR002FL`) never targeted; no live AVD booted during unit tests; `feature/ATT-8222` preserved intact for Stage 6 Cold Review; 4 dirty `tools/` files & `docs/presentations/vorgehensmodell/` untouched.
