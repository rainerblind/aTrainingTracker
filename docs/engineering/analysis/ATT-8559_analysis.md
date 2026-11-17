# Stage 1 Analysis: ATT-8559 - qa-audit Jira CA Creation, Synthetic ATT-CA Guard & Check Heuristics

**Ticket**: [ATT-8559](https://rainerblind.atlassian.net/browse/ATT-8559) (`2 SP`) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)
**Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8559` | **Author**: `agent1` | **Date**: 2026-11-17

---

## 1. Problem Statement & Motivation
During Ceremony 2 QA audits for `2026-11-16_03_Nacht` (`ID: 455`) and `2026-11-17_01_Früh` (`ID: 456`), `qa_audit()` flagged false-positive `FAIL`s across all 4 sampled tickets (`ATT-8426`, `ATT-8462`, `ATT-8456`, `ATT-8575`), failed silently when querying/creating Jira Corrective Action (CA) tickets, and wrote dead placeholder links (`ATT-CA-*`) into `docs/engineering/qa_audits/sprint_2026-11-16_03_nacht.md` and `sprint_2026-11-17_01_früh.md` without failing `verify_requirement_governance.py --check-qa-audits`.

---

## 2. Root Cause Analysis & Independent Peer Review
* **Verified Jira API Failures (`tools/jira_util.py`)**:
  1. **Deprecated Search Endpoint (`L4874`)**: `qa_audit()` hardcodes `GET {base}/rest/api/2/search?jql=...` (`410 Gone` on Jira Cloud) instead of `get_search_endpoint(config)` (`POST /rest/api/3/search/jql`, `L142-149`, or `paged_jql_search()` `L2440`). `L4880` swallows the exception (`existing_key = None`).
  2. **Invalid Issue Type ID (`L4910`)**: `create_issue(..., issuetype_id="10005", parent_key="ATT-8138")` carries comment `# Verbesserung`, but `ISSUE_TYPE_ALIASES["verbesserung"]` (`L2800`) and live Jira Cloud `createmeta` confirm `"10008"` is `Verbesserung` (`"10002"` is `Subtask`; `"10005"` does not exist). Jira Cloud rejects creation with `400 Bad Request`.
  3. **Silent Masking & Hardcoded Browse Host (`L4917-4919`, `L4547-4548`, `L4926`)**: Bare `except Exception:` swallows `create_issue()` errors and emits synthetic `ATT-CA-<slug>` / `ATT-CA-{key}` tokens (using hardcoded `rainerblind.atlassian.net` instead of `base`).
* **Independent Peer Review — Audit Check Heuristic Bugs & Verifier Blind Spot**:
  1. **False `4-Augen Role Separation` FAIL (`L4757-4773`)**: All 22 subtask comments on `ATT-8426`, `ATT-8462`, `ATT-8456`, `ATT-8575` were posted by `ROLE_ACCOUNT_IDS["agent2"]` (`712020:1b6bc33f-f738-43f1-a4cf-373952d25316`), yet `L4757` only counts comments containing literal `"GATE_VERDICT:"`, `"GATE-VERDICT"`, `"VERDICT: PASS"`, or `"VERDICT: REVISION"`, yielding `agent2_verdicts_count == 0` on real `Gate N ... PASS` comments.
  2. **False `Commit Hygiene` FAIL (`L4806-4819`)**: Checks only `^{key}:` prefixes, rejecting valid sprint commits `4479f2af` (`feat(governance): ... (ATT-8426)`), `23317f58` (`[ATT-8575] ...`), and bounce-heal `e9766625` (`fix(ATT-8575): ...`).
  3. **False `Living Documentation Parity` FAIL (`L4788`)**: Regex `\bREQ-[A-Z0-9_-]+\b` matched wildcard `REQ-FIL-*` in `ATT-8575_analysis.md:29` as bare `"REQ-FIL"`, failing `issubset(master_req_ids)`.
  4. **Permissive Verifier (`tools/verify_requirement_governance.py:781`)**: `ca_links` regex explicitly accepts `ATT-CA-[A-Za-z0-9_-]+` in all audit files, masking live API failures. Note: `sprint_2026-11-17_01_früh.md` also currently holds `ATT-CA-*` links and must be re-audited or grandfathered alongside `sprint_2026-11-16_03_nacht.md` so `--check-qa-audits` stays green.
  5. **Offline Test Constraint (`tools/test_jira_accounts.py:2328-2333`)**: `test_tst_pro_091_11` mocks `POST /rest/api/2/issue` (`ATT-CA-100`) and `/rest/api/2/search`; tests must mock HTTP calls (`get_search_endpoint` + `10008`) and never create live Jira issues.

---

## 3. User Scope Grounding (`ATT-1250`)
* **In-Scope (`2 SP`)**: (1) Switch `qa_audit()` CA deduplication to `get_search_endpoint` / `paged_jql_search`, fix `issuetype_id="10008"` (`Verbesserung`) and `base` browse URL, and fail loudly on live Jira CA search/create errors instead of `ATT-CA-*` fallback; (2) Fix `qa_audit()` heuristics for `4-Augen Role Separation` (`Gate N ... PASS|REVISION`), `Commit Hygiene` (`[ATT-X]`, `type(ATT-X):`, `(ATT-X)`, `Ticket: ATT-X`), and wildcard `REQ-XXX-*` exclusion; (3) Tighten `verify_requirement_governance.py:781` to reject `ATT-CA-*` in live sprint audits (allowing only legacy/unit-test fixtures as scoped); (4) Add mocked unit tests in `test_jira_accounts.py` & `test_verify_requirement_governance.py`, update `REQ-PRO-091.3` / `TST-PRO-091.11`, and re-audit `sprint_2026-11-16_03_nacht.md` (and `sprint_2026-11-17_01_früh.md` if needed for verifier parity).
* **Out-of-Scope**: `app/src/main/`, `ANT-Android-SDKs`, or modifying `tools/hoechstmass/` unless required for parity.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
* **Original Requirement ID & Target**: `REQ-PRO-091.3` / `TST-PRO-091.11` (`docs/requirements/req_process_stability.md:5069`, `docs/tests/tst_process_stability.md:3244`), targeting `tools/jira_util.py` and `tools/verify_requirement_governance.py`.
* **Historical Origin & Commit Trace**: `ATT-7558` (Sprint `2026-46.6`), `ATT-8152` & `ATT-8153` (Sprint `2026-11-16_02_Spät`) introduced `REQ-PRO-091.3` (`issuetype_id="10005"` typo) and `ATT-CA-*` fallback regex.
* **Root Reason for Existing Formulation**: `ATT-8153` copied `"10005"` assuming `Verbesserung` and added `ATT-CA-*` to Section 4 regex to match `test_tst_pro_091_11`'s mock key `ATT-CA-100` without separating test mocks from live audits.
* **Preservation of Core Invariants**: Independent role enforcement (`qa`/`agent2`), `>= max(2, ceil(0.10*N))` sampling, `ca_key != audited_key`, deduplicated raw Backlog creation (`add_to_sprint=False`, `rank_top=True`), and `Rule 5` offline mocked unit tests remain preserved.

---

## 5. Change Impact Analysis (`SUP.10`)
* **Affected Requirements**: Update `REQ-PRO-091.3` (`issuetype_id="10008"`, loud failure / non-synthetic `ATT-\d+` live CA links) and `TST-PRO-091.11`/`.12` (or net-new sub-bullet) in `req_process_stability.md` & `tst_process_stability.md`.
* **Affected Architecture Elements**: `tools/jira_util.py` (`qa_audit`, `format_qa_audit_markdown`), `tools/verify_requirement_governance.py` (`_validate_single_qa_audit`), `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py`, `docs/engineering/qa_audits/sprint_2026-11-16_03_nacht.md` (and `sprint_2026-11-17_01_früh.md` check).
* **Verification Impact & Test Surface**: `python3 -m unittest tools/test_jira_accounts.py tools/test_verify_requirement_governance.py` (100% mocked HTTP), `python3 tools/verify_requirement_governance.py --check-qa-audits`, and live `--as agent2 qa-audit` re-run.
* **Risk Assessment & Invariants**: **LOW** (`2 SP`). Tooling/governance only; zero `app/src/main/` changes; `ANT-Android-SDKs` untouched (`0e8cea714a231fb9565170d4ed01eafa868f9798`).

---

## 6. Architectural Strategy & System Invariants
1. Replace `L4874` GET `/rest/api/2/search` with `paged_jql_search(jql, ["summary"], role=role, config=config, max_total=5)` (uses `get_search_endpoint` Cloud POST v3 / DC GET v2) and fix `L4910` to `issuetype_id="10008"`; propagate Jira CA errors loudly (`RuntimeError` / `sys.exit(1)`) in `qa_audit()` rather than fabricating `ATT-CA-*`.
2. Refine `qa_audit()` checks: match `agent2`/`qa` `accountId` on gate review comments (`GATE_VERDICT:` or `Gate \d+.*\b(PASS|REVISION)\b`), recognize standard ticket commit formats (`^ATT-X:`, `[ATT-X]`, `(ATT-X)`, `Ticket: ATT-X`), and ignore family-wildcard `REQ-[A-Z]+-\*` tokens before `issubset(master_req_ids)`.
3. Restrict `verify_requirement_governance.py:781` to real Jira keys (`ATT-\d+|PROJ-\d+`) for live sprint audits (scoping synthetic keys strictly to unit tests / grandfathered audits), then re-audit `sprint_2026-11-16_03_nacht.md`.
