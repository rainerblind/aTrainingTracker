# Stage 2 Req & Test Spec: ATT-8165 - Strengthen Problem Resolution (SUP.9)

**Ticket**: [ATT-8165](https://rainerblind.atlassian.net/browse/ATT-8165) | **Sub-task**: `[Req & Test Spec]` | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8165` | **Reserved IDs**: `REQ-PRO-121`, `TST-PRO-121`

---

## 1. Requirements Specification (`REQ-PRO-121` [Planned])
* **Attributes**: `Priority: High` | `Type: Non-Functional` | `Verification Method: Unit / Review` | `Localization Parity: N/A - no UI strings`
* **REQ-PRO-121.1 (Bug Metrics, Age & Multi-Sprint Trend)**: `collect_sprint_metrics_for_id()` SHALL fetch `created`, `resolution`, and `resolutiondate`; `parse_sprint_gate_metrics()` SHALL compute `open_bug_age_days` (`avg`, `max`, `gt_7d` per severity), `fixed_awaiting_human` (`Final Review (Human)`), `closed_bugs`, and `bug_trend_history`; `format_metrics_markdown()` (`--post-to-retro`) SHALL render per-severity open/closed/age metrics and a multi-sprint Bug Trend table/ASCII chart.
* **REQ-PRO-121.2 (Resolution States & Fix Verification)**: `tools/jira_util.py` SHALL provide `resolve-bug <KEY> <rejected|duplicate|cannot-reproduce|fixed> [--duplicate-of=KEY] [--reason=...]` recording `resolution-<state>` labels, audit comment, and Jira `resolution` field when present in `/rest/api/3/resolution` (accounting cleanly for Jira Cloud's current single `id=10000` `Fertig` resolution). Advancing a `Bug` (`10006`) to `Final Review (Human)` without a non-fix resolution MUST require fix-verification evidence.
* **REQ-PRO-121.3 (Commit Hygiene, Conflict Markers & Git Hooks)**: `tools/verify_requirement_governance.py` (`verify_conflict_markers()`, `verify_commit_hygiene()`, wired into `verify_governance()` / CI) SHALL fail (`exit 1`) on tracked files matching `^<{7}(?: .*)?$|^={7}$|^>{7}(?: .*)?$` or post-cutoff non-merge commits on `develop`/`sprint/*` lacking `ATT-\d+` (preserving pre-cutoff `7e5ff312`, `d7bf7698`, `24ceaf94`, `6a694bb1`). `.githooks/{commit-msg,pre-commit}` SHALL enforce both locally.
* **REQ-PRO-121.4 (`process-escape` Problem Records)**: `tools/jira_util.py` SHALL expose `create_process_escape_bug()` (`file-process-escape` CLI & optional `gate-verdict REVISION --file-escape-bug`) using Cloud v3 `paged_jql_search` deduplication + `create_issue(..., issuetype_id="10006", labels=[..., "process-escape"], severity=...)` for gate/Tier-2 escapes.
* **REQ-PRO-121.5 (Offline Tests)**: All unit tests SHALL run 100% offline via `tempfile` Git repos and HTTP/Jira mocks (`Rule 5`).
* **Acceptance Criteria (Given-When-Then)**:
  * *Given* `sprint-metrics --record --post-to-retro` with multi-sprint `process_metrics.jsonl` history, *When* rendered, *Then* Retro output contains bug counts/age per severity and a multi-sprint trend table/chart.
  * *Given* a post-cutoff commit on `develop` without `ATT-\d+` or conflict markers in a tracked file, *When* `verify_requirement_governance.py` (or `.githooks/`) runs, *Then* it exits `1`, while historical commits `7e5ff312`, `d7bf7698`, `24ceaf94`, `6a694bb1` pass.
  * *Given* mocked Jira Cloud v3 HTTP calls, *When* `resolve-bug`, Bug `Final Review (Human)` gate, or `file-process-escape` runs, *Then* `resolution-*` metadata records cleanly, missing fix verification blocks transition, `Erledigt` stays human-only (`REQ-PRO-016`), and `paged_jql_search` prevents duplicate `process-escape` Bugs.
* **Invariants**: Zero `app/src/main/` or `ANT-Android-SDKs` edits; `REQ-PRO-063.4` JSONL backward compatibility; `REQ-PRO-016` Human Gate; `REQ-PRO-120` header versioning.

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-PRO-121` (`REQ-PRO-121.1`–`REQ-PRO-121.5`), extending `REQ-PRO-063` and `REQ-PRO-092` across `tools/sprint_metrics.py`, `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `.githooks/`, and `.github/workflows/android-branch-build.yml`.
* **Historical Origin & Commit Trace**: `REQ-PRO-063` (`ATT-6156`, `2026-46.3`) and `REQ-PRO-092` (`ATT-7559`), extended per ASPICE 4.0 Audit Finding `SUP.9` (`ATT-8165`).
* **Root Reason for Existing Formulation**: Prior rules tracked per-sprint severity counts but lacked bug age, trend charts, non-fix resolution states, fix-verification gating, commit/conflict CI checks, and `process-escape` bug logging.
* **Preservation of Core Invariants**: Preserves `REQ-PRO-063.4` schema compatibility, `REQ-PRO-016` Human Gate, `REQ-PRO-120` header bumps, and pre-cutoff historical commits (`7e5ff312`, `d7bf7698`, `24ceaf94`, `6a694bb1`).

## 3. Test Specification & Traceability Matrix (`TST-PRO-121` [Planned])
| Requirement | Test Case | Test File & Verification Scope |
| :--- | :--- | :--- |
| `REQ-PRO-121.1` | `TST-PRO-121.1` | `tools/test_sprint_metrics.py` — `created`/`resolution`/`resolutiondate` fetch, age (`avg`/`max`/`gt_7d`), `fixed_awaiting_human`, multi-sprint trend table/ASCII chart, `verify_sprint_metrics` compatibility |
| `REQ-PRO-121.2` | `TST-PRO-121.2` | `tools/test_jira_accounts.py` — offline HTTP mock for `resolve-bug` (`rejected`/`duplicate`/`cannot-reproduce`/`fixed`), single-resolution `Fertig` fallback, `Erledigt` human guard, fix-verification gate |
| `REQ-PRO-121.3` | `TST-PRO-121.3` | `tools/test_verify_requirement_governance.py` — `tempfile` Git repo checks for conflict markers, post-cutoff `ATT-\d+` enforcement, pre-cutoff exemptions, `.githooks/{commit-msg,pre-commit}` |
| `REQ-PRO-121.4` | `TST-PRO-121.4` | `tools/test_jira_accounts.py` — offline Cloud v3 `paged_jql_search` deduplication + `process-escape` Bug creation via `file-process-escape` & `gate-verdict REVISION --file-escape-bug` (`Rule 5`) |

* **Localization Parity**: `N/A - no UI strings`.
