# Stage 1 Analysis: ATT-8165 - Strengthen Problem Resolution (SUP.9)

**Ticket**: [ATT-8165](https://rainerblind.atlassian.net/browse/ATT-8165) | **Sub-task**: `[Analysis]` | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-17_02_Spät` | **Branch**: `feature/ATT-8165` | **Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-17

---

## 1. Problem Statement & Motivation
`docs/engineering/metrics/process_metrics.jsonl` records `17` open bugs (`3 Critical`, `0` closed, `0.0%` resolution) in Sprint `443` (`2026-46.6`) and `0` closed bugs across Sprints `443`–`456`, yet Retro scorecards lack open-bug age and cross-sprint trend analysis. Bugs share the exact feature workflow without `Rejected`/`Duplicate`/`Cannot Reproduce` resolution tracking or fix verification before `Final Review (Human)`. Hotfix/Tier-2 fix commits (`7e5ff312`, `24ceaf94`, `6a694bb1`) lack `ATT-` keys, stale conflict markers reached `develop` (`d7bf7698`), and Gate `REVISION` / Tier-2 escapes do not generate `process-escape` Bug records (`SUP.9 BP3/BP4/BP6`).

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Metrics & Retro Gap**: `tools/sprint_metrics.py:195` (`parse_sprint_gate_metrics`) counts `closed_bugs` only at `erledigt`/`closed`/`done`, ignoring bugs resolved to `Final Review (Human)` at Ceremony 2 close; `tools/jira_util.py:3715` (`collect_sprint_metrics_for_id`) omits `created`, `resolution`, and `resolutiondate`. `format_metrics_markdown` (`tools/sprint_metrics.py:595-612`) renders only a single-sprint severity table without open-bug age or multi-sprint trend deltas.
* **Jira Resolution & Workflow Gap (Live API Audit)**:
  1. `GET /rest/api/3/resolution` returns only `id=10000` (`Fertig`); `Rejected`, `Duplicate`, and `Cannot Reproduce` do **not** exist in Jira Cloud's global resolution table (creating global resolutions requires Jira Admin).
  2. `GET /rest/api/3/project/ATT/statuses` shows `Bug` (`10006`) uses the identical 9-status workflow as `Feature` (`10007`)/`Improvement` (`10008`), with no pre-`Final Review (Human)` fix-verification guard in `tools/jira_util.py:1026-1160` (`transition_issue`) or `gate_verdict` (`:2030-2142`).
* **Independent Peer Review & Blind Spots Verified**:
  1. *Historical Commit Cutoff Blind Spot*: `git show` confirms `7e5ff312` (`1794462164`), `d7bf7698` (`1794664711`), `24ceaf94` (`1794685976`), and `6a694bb1` (`1794689602`) have no `ATT-` key; scanning `git log` without a post-`ATT-8165` timestamp cutoff (cf. `DOC_HEADER_CUTOFF_TS` in `tools/verify_requirement_governance.py:4350,4730`) plus merge-commit exemption would retroactively break CI.
  2. *Missing Hook Infrastructure*: `.githooks/` does not exist yet; `.github/workflows/android-branch-build.yml:144-145` invokes `python3 tools/verify_requirement_governance.py`. `git grep` confirms `0` conflict markers currently exist in tracked files.
  3. *Rule 5 & Deduplication*: `gate_verdict` (`tools/jira_util.py:2136-2141`) reopens subtasks on `REVISION` without filing a `process-escape` Bug; `qa_audit` (`:4842-4883`) provides the Cloud v3 `paged_jql_search` + `create_issue(..., severity=...)` deduplication pattern.

## 3. User Scope Grounding (ATT-1250)
* **In-Scope (`2 SP` confirmed)**: Extend `sprint_metrics.py`/`jira_util.py` for open-bug age & multi-sprint trend chart in Retro; add bug resolution metadata/commands (`Rejected`/`Duplicate`/`Cannot Reproduce`) & fix-verification guard before `Final Review (Human)`; add commit-msg/CI `ATT-` key & conflict-marker checks (with historical cutoff) plus `.githooks/`; add deduplicated `process-escape` Bug creation for Gate `REVISION` / Tier-2 CI escapes.
* **Out-of-Scope**: Modifying `app/src/main/` production code, `ANT-Android-SDKs` (`0e8cea71`), or requiring manual Jira Admin global-resolution schema edits to pass CI.

## 4. Requirement Archaeology & Chesterton's Fence Audit
* Net-new `REQ-PRO-121` & `TST-PRO-121` extending `REQ-PRO-063` (`docs/requirements/req_process_stability.md:4064-4103`, `TST-PRO-063` `:2512`) and `REQ-PRO-092` (`:5126-5170`, `TST-PRO-092` `:3256`) while preserving `REQ-PRO-063` schema compatibility, `REQ-PRO-016` Human Decision Gate (`Erledigt` human-only), and `REQ-PRO-120` (`:6063-6075`) monotonic version bumps on governed docs in Stage 2–4.

## 5. Change Impact Analysis
* **Affected Requirements**: Net-new `REQ-PRO-121` (extending `REQ-PRO-063` & `REQ-PRO-092`); references `REQ-PRO-120` header version-bump rules when editing `req_process_stability.md`, `tst_process_stability.md`, `docs/project_governance.md`, `docs/project_protocol.md`, `docs/management/qa_plan.md`.
* **Affected Architecture Elements**: `tools/sprint_metrics.py`, `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `.githooks/commit-msg`, `.githooks/pre-commit`, `.github/workflows/android-branch-build.yml`, `docs/project_governance.md`, `.agents/skills/sprint-planner/SKILL.md`.
* **Verification Impact & Test Surface**: `tools/test_sprint_metrics.py`, `tools/test_jira_accounts.py` (or dedicated `tools/test_jira_problem_resolution.py` with offline HTTP mocking per `Rule 5`), `tools/test_verify_requirement_governance.py` (using isolated temp git repos), `python3 tools/verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: Medium risk of breaking historical CI commits or `verify_sprint_metrics` (`:620-700`) backward compatibility on `process_metrics.jsonl` lines 1–18, or introducing false-positive conflict-marker matches on markdown. Mitigated by timestamp cutoff, additive optional JSONL fields, and strict `^<{7}|^={7}$|^>{7}` regexes.

## 6. Architectural Strategy & High-Level Solution
1. **Bug Age & Trend Analysis**: Fetch `created,resolution,resolutiondate` in `collect_sprint_metrics_for_id`; compute `open_bug_age_days` (`avg`, `max`, `gt_7d` per severity) and `fixed_awaiting_human` vs `closed_bugs` in `parse_sprint_gate_metrics`; render a multi-sprint Bug Trend table/ASCII chart across `history` in `format_metrics_markdown` (`--post-to-retro`).
2. **Bug Resolutions & Fix Verification**: Add `jira_util.py resolve-bug <KEY> <rejected|duplicate|cannot-reproduce|fixed> [--duplicate-of=KEY] [--reason=...]` recording standardized `resolution-*` labels, structured comment, and Jira `resolution` field (when available in Jira, graceful label fallback otherwise) while respecting `REQ-PRO-016` (`Final Review (Human)` ceiling); enforce Bug fix-verification evidence (`reproduction` + `regression test` verified) on `Bug` Stage 5 / `Final Review (Human)` transitions.
3. **Commit & Conflict-Marker Gates**: Add `verify_conflict_markers()` and `verify_commit_hygiene()` (enforcing `ATT-\d+` on post-cutoff non-merge commits targeting `develop`/`sprint/*`) to `tools/verify_requirement_governance.py` default CI flow, plus installable `.githooks/commit-msg` & `.githooks/pre-commit`.
4. **`process-escape` Bug Creation**: Add `create_process_escape_bug()` using Cloud v3 `paged_jql_search` deduplication + `create_issue(..., issuetype_id="10006", labels=[..., "process-escape"], severity=...)`, wired to `gate-verdict REVISION` (when `--file-escape-bug` / systemic flag or CLI `file-process-escape` for Tier-2 CI failures).

## 7. System Invariants & Risk Assessment
* **Core Invariants**: (1) Zero `app/src/main/` or `ANT-Android-SDKs` edits; (2) `REQ-PRO-016` Human Gate (`Erledigt` human-only) & `Rule 5` offline test mocking preserved; (3) Pre-cutoff commits (`7e5ff312`, `d7bf7698`, `24ceaf94`, `6a694bb1`) and existing `process_metrics.jsonl` records remain valid.
* **Risk Rating**: **MEDIUM** — Multi-tool CLI/CI governance additions touching Jira workflows and Git/CI validation; mitigated via offline unit tests, temp git repo tests, and additive schemas.
