# Stage 3 Implementation Plan: ATT-8165 - Strengthen Problem Resolution (SUP.9)

**Ticket**: [ATT-8165](https://rainerblind.atlassian.net/browse/ATT-8165) | **Sub-task**: `[Impl-Plan]` | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8165` | **Requirements**: `REQ-PRO-121` | **Tests**: `TST-PRO-121`

---

## 1. Architecture & Component Boundaries (SWE.2)
* **Pure Tooling & CI Scope**: Changes are confined to Python governance/metrics/Jira CLI scripts (`tools/sprint_metrics.py`, `tools/jira_util.py`, `tools/verify_requirement_governance.py`), Git hooks (`.githooks/commit-msg`, `.githooks/pre-commit`), CI workflow (`.github/workflows/android-branch-build.yml`), governance docs (`docs/project_governance.md`, `docs/project_protocol.md`, `.agents/skills/sprint-planner/SKILL.md`), and offline Python tests.
* **UI Consistency**: `N/A` (no Jetpack Compose, Android UI, or `strings*.xml` changes).
* **Isolation Boundaries**: Zero edits to `app/src/main/` or `ANT-Android-SDKs` (`0e8cea71`); zero live Jira API calls in unit tests (`Rule 5`); `REQ-PRO-016` Human Gate (`Erledigt` human-only) strictly preserved.

## 2. Atomic Implementation Steps & 1:1 Traceability
| Step | Target Files | Change Summary | REQ / TST |
| :--- | :--- | :--- | :--- |
| **1** | `tools/sprint_metrics.py`, `tools/jira_util.py` | Fetch `created,resolution,resolutiondate` in `collect_sprint_metrics_for_id()` (`:3715`) & pass `history` in `cmd_sprint_metrics()`; extend `parse_sprint_gate_metrics()` (keeping `(parent_issues, sprint_info)` positional signature) with `fixed_awaiting_human`, `open_bug_age_days` (`avg`/`max`/`gt_7d` per severity), and `bug_trend_history`; render open/closed/age & multi-sprint trend table/ASCII chart in `format_metrics_markdown()` (`--post-to-retro`). | `REQ-PRO-121.1` / `TST-PRO-121.1` |
| **2** | `tools/jira_util.py` | Add `resolve-bug <KEY> <rejected\|duplicate\|cannot-reproduce\|fixed> [--duplicate-of=KEY] [--reason=...]` checking `/rest/api/3/resolution` (graceful `Fertig` `10000` fallback) + writing `resolution-<state>` label & comment while preserving `REQ-PRO-016` (`Final Review (Human)` ceiling); enforce Bug fix-verification evidence check in `transition_issue` / `final-review-verdict` before advancing a non-exempt `Bug` (`10006`) to `Final Review (Human)`. | `REQ-PRO-121.2` / `TST-PRO-121.2` |
| **3** | `tools/verify_requirement_governance.py`, `.githooks/commit-msg`, `.githooks/pre-commit`, `.github/workflows/android-branch-build.yml` | Add `verify_conflict_markers()` (`^<{7}(?: .*)?$\|^={7}$\|^>{7}(?: .*)?$`) and `verify_commit_hygiene()` (`ATT-\d+` on post-cutoff non-merge commits targeting `develop`/`sprint/*`, cleanly exempting pre-cutoff `7e5ff312`, `d7bf7698`, `24ceaf94`, `6a694bb1`) to `verify_governance()` + CLI flags; add `.githooks/{commit-msg,pre-commit}`; wire explicit CI checks in `.github/workflows/android-branch-build.yml` while keeping `python3 tools/verify_requirement_governance.py` before `assembleDebug`. | `REQ-PRO-121.3` / `TST-PRO-121.3` |
| **4** | `tools/jira_util.py` | Add `create_process_escape_bug()` using Cloud v3 `paged_jql_search` deduplication + `create_issue(..., issuetype_id="10006", labels=[..., "process-escape"], severity=...)`; expose `file-process-escape` CLI and optional `--file-escape-bug` on `gate-verdict REVISION`. | `REQ-PRO-121.4` / `TST-PRO-121.4` |
| **5** | `docs/project_governance.md`, `docs/project_protocol.md`, `.agents/skills/sprint-planner/SKILL.md` | Document SUP.9 bug resolution states, fix-verification gate, `.githooks` / commit hygiene, and `process-escape` workflow; monotonically bump `**Version**` + `Change History` on any edited governed doc (`REQ-PRO-120.2`), preserving `AspiceSweAuditContractTest` & `ReleaseCiAndLintConfigContractTest` markers. | `REQ-PRO-121.1–.4` |
| **6** | `tools/test_sprint_metrics.py`, `tools/test_verify_requirement_governance.py`, `tools/test_jira_accounts.py` | Add offline `Rule 5` unit tests (`tempfile` Git repos & mocked Jira HTTP responses) covering `TST-PRO-121.1`..`TST-PRO-121.4` and backward compatibility with existing `process_metrics.jsonl` records. | `REQ-PRO-121.5` / `TST-PRO-121.1–.4` |

## 3. System Invariants & Contract Safeguards
* **Schema & Signature Compatibility**: Existing `parse_sprint_gate_metrics(parent_issues, sprint_info)` calls and `process_metrics.jsonl` lines 1–18 (`verify_sprint_metrics`) remain valid; `problem_trends` additions are additive.
* **Contract Test Markers**: Preserve `REQ-STP-010.2`, `sprint-metrics`, `PA 4.2`, `PA 5.2` in `sprint-planner/SKILL.md` (`AspiceSweAuditContractTest:273-276`), `9 Core Rules` / `Rules 1–85+` / `6-Stage` in `docs/project_protocol.md` (`:364-370`), `DOC-*` + monotonic `Version` bumps (`REQ-PRO-120`), and `python3 tools/verify_requirement_governance.py` before `assembleDebug` in `.github/workflows/android-branch-build.yml` (`ReleaseCiAndLintConfigContractTest:1073-1094`).
* **Historical Cutoff Safety**: `COMMIT_HYGIENE_CUTOFF_TS` exempts `7e5ff312`, `d7bf7698`, `24ceaf94`, `6a694bb1` and merge commits so CI never fails retroactively.

## 4. Verification Plan
1. `python3 -m unittest tools/test_sprint_metrics.py tools/test_verify_requirement_governance.py tools/test_jira_accounts.py` (100% offline `Rule 5`).
2. `python3 tools/verify_requirement_governance.py --check-doc-headers && python3 tools/verify_requirement_governance.py`.
3. `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest" --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"`.
