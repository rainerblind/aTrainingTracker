# Stage 5 Walkthrough & Verification: ATT-8207 - Preserve Single-Atomic-Commit Invariant Across Stage 6 Cold Review Bounces

**Document ID**: `DOC-ENG-WALKTHROUGH-ATT-8207`  
**Version**: `1.0.3`  
**Status**: `Draft`  
**Approver**: `Pending PO Sign-Off`  
**Ticket**: [ATT-8207](https://rainerblind.atlassian.net/browse/ATT-8207) (`2 SP`) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-17_03_Nacht` | **Branch**: `feature/ATT-8207`  
**Requirement Mapping**: `REQ-PRO-125` (`REQ-PRO-125.1`–`.4`) | **Test Mapping**: `TST-PRO-125` (`TST-PRO-125.1`–`.4`) | **Date**: `2026-11-18`

---

## 1. Executive Summary & Verification Overview
* Deferred Strategy A squash-merge (`--merge-ticket`) onto `sprint/<SPRINT_NAME>` from Gate 5 `PASS` to post-Stage-6 `final-review-verdict PASS`, keeping `feature/<KEY>` intact for read-only Stage 6 audit (`git diff sprint/<SPRINT_NAME>...feature/<KEY>`, zero `./gradlew`, zero `flock` during audit) and preventing bounce commit pollution and `Schicht` rollover leaks.
* Added cross-worktree dirty-worktree fail-fast guard and scoped `docs/requirements.md` / `docs/tests.md` index conflict auto-recovery (`--ours` + `--sync-indexes` + `_stage_living_doc_updates`) in `tools/parallel_sprint_lock.py` and `tools/hoechstmass/tools/parallel_sprint_lock.py`.
* Updated `tools/jira_util.py`, `tools/sprint_metrics.py`, and `tools/hoechstmass/` mirrors for subtask reopen/reuse and `[<Prefix> - Bounce N]` fallback when `Erledigt` has `transitions: []`, preserving `ATT-8168` (`ACTIVE_PROCESS_VERSION = "2.0"`, `p_chart`).
* **Stage 6 Bounce 1/3 (`69a94683`), Pro Bounce 2/3 (`904b1274`), & Pro Bounce 3/3 (`a67bd9b1`) Remediations**: Fixed `tools/hoechstmass/tools/jira_util.py:1151` `[Analysis - Bounce N]` Gate 1 regex; updated `check_gate` (`tools/jira_util.py:795`) to use `STAGE_SPECS["3"]["pattern"]` for `[Impl-Plan - Bounce N]`; expanded `STAGE_ALIASES`, `_resolve_bounce_stage_ids`, `tools/hoechstmass/tools/jira_util.py` `_resolve_bounce_stage_names`, `transition_issue` parent `aliases`, and `final_review_verdict` two-step `'zu erledigen' -> 'analysis'` bounce path for `'1'..'5'`, `'stage1'..'stage5'`, and `'stage 1'..'stage 5'` (verifying `bounce_target="stage 4"` reopens only Stages 4–5 and never Stage 3 `[Impl-Plan]`); added targeted unit coverage in `tools/test_jira_accounts.py` and `tools/hoechstmass/tools/test_hoechstmass_tools.py`. `Localization Parity: N/A - no UI strings`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Scope / Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-125.1` | `TST-PRO-125.1` | Python Unit (Temp Git Repo) | `tools.test_flock_serialization`: **13/13 PASSED** | `Verified` |
| `REQ-PRO-125.2` | `TST-PRO-125.2` | Governance & Contract Review | `AspiceSweAuditContractTest` **13/13 PASSED**; `verify_requirement_governance.py` **exit 0** | `Verified` |
| `REQ-PRO-125.3` | `TST-PRO-125.3` | Python Unit (Mocked Jira, Rule 5) | `TestSup9ProblemResolution` **7/7 PASSED**; `TestGateBypassPrevention` **5/5 PASSED**; `tools.test_sprint_metrics` **19/19 PASSED** | `Verified` |
| `REQ-PRO-125.4` | `TST-PRO-125.4` | Mirror & Contract Suites | `TestHoechstmassPostStage6MergeAndBounceParity` + `MetricsParity` **4/4 PASSED**; Gradle contracts **23/24** (1 pre-existing `.env.jira` worktree error) | `Verified` |

---

## 3. Automated Test Evidence (Targeted Scope — Stage 4 Bounce-3 Code SHA `a67bd9b1`)
* **Targeted Python Suites (`48/48` in `2.737s` — `OK`)**:
  * Combined targeted suite (`tools.test_flock_serialization`, `tools.test_sprint_metrics`, `tools.test_jira_accounts.TestSup9ProblemResolution`, `tools.test_jira_accounts.TestGateBypassPrevention`, `tools.hoechstmass.tools.test_hoechstmass_tools.TestHoechstmassPostStage6MergeAndBounceParity`, `tools.hoechstmass.tools.test_hoechstmass_tools.TestHoechstmassSprintMetricsParity`): `Ran 48 tests in 2.737s — OK`
  * Full `python3 -m unittest tools.hoechstmass.tools.test_hoechstmass_tools -v`: **34/35 PASSED** (sole pre-existing worktree error: `test_hoechstmass_record_review` raising `KeyError: 'JIRA_URL'` due to absent `.env.jira` in worktree).
* **Gradle Contract Suites (`23/24` across 3 targeted classes)**:
  * `AspiceSweAuditContractTest` (`13/13`), `HoechstmassCoreFrameworkContractTest` (`7/7`), `HoechstmassToolingContractTest` (`3/4` — sole failure: `testPythonToolingUnitTests_passViaExecutionContract` invoking pre-existing `test_hoechstmass_record_review` `KeyError: 'JIRA_URL'` in worktrees lacking `.env.jira`).
* **Governance Verification**: `verify_requirement_governance.py --check-doc-headers --check-commit-hygiene --check-conflict-markers` and full `verify_requirement_governance.py` exited `0`.

---

## 4. Clean-Room & Feature-Branch State
* Zero edits to `app/src/main/`, `ANT-Android-SDKs`, or `tools/sprint_bisect.py`; zero code/mirror edits in Stage 5 (`ATT-8030`).
* Per `REQ-PRO-125.1`, changes remain cleanly committed on `feature/ATT-8207` (`sprint/2026-11-17_03_Nacht` untouched) pending Stage 6 Cold Review `PASS`.

---

## 5. Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-18 | AI Agent 1 (Implementer) | `ATT-8207` | Initial Stage 5 walkthrough and living-doc `[Verified]` promotion. |
| `1.0.1` | 2026-11-18 | AI Agent 1 (Implementer) | `ATT-8207` | Stage 5 Bounce 1/3 verification update after commit `69a94683` (`37/37` + `4/4` Python, `23/24` Gradle contracts, `[Verified]` promotion). |
| `1.0.2` | 2026-11-18 | AI Agent 1 (Implementer) | `ATT-8207` | Stage 5 Bounce 2/3 verification update after commit `904b1274` (`44/44` + `4/4` Python, `23/24` Gradle contracts, `[Verified]` promotion). |
| `1.0.3` | 2026-11-18 | AI Agent 1 (Implementer) | `ATT-8207` | Stage 5 Bounce 3/3 verification update after commit `a67bd9b1` (`STAGE_ALIASES`, `_resolve_bounce_stage_ids`, `_resolve_bounce_stage_names` `'stage 1'..'stage 5'` parity, `48/48` Python, `23/24` Gradle contracts, `[Verified]` promotion). |
