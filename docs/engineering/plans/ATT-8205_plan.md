# Stage 3: Implementation Plan - ATT-8205

**DOC-ID**: `DOC-PLN-ATT-8205` | **Ticket**: `ATT-8205` (`2 SP`) | **Epic**: `ATT-8138`  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-18_01_Früh` | **Branch**: `feature/ATT-8205`  
**Requirements**: `REQ-PRO-128` (`128.1`–`128.5`) | **Tests**: `TST-PRO-128` (`128.1`–`128.5`)  
**Author**: AI Agent 1 (`--as agent1`, `flash`) | **Date**: 2026-11-18

---

## 1. Traceability & System Invariants
* **`REQ-PRO-128.1` $\rightarrow$ `TST-PRO-128.1`**: Extended verdict synonyms (`PASS`/`APPROVED`/`FREIGABE` vs `REVISION`/`REWORK`/`FAIL`), ADF text extraction, author role fidelity, and zero-comment `unverified` parity across `sprint_metrics.py` & `jira_util.py`.
* **`REQ-PRO-128.2` $\rightarrow$ `TST-PRO-128.2`**: Additive `end_to_end_ftpr_percent` (Gates 1–6) preserving Gate 1–5 `overall_ftpr_percent` & `REQ-PRO-122.4` / `REQ-PRO-107.3` alerts.
* **`REQ-PRO-128.3` $\rightarrow$ `TST-PRO-128.3`**: Multi-signal `risk_distribution` (`severity-*`, `[Highest]`/`[High]`/`[Low]`, Stage 1 `Risk Rating`) before `fields.priority.name` fallback.
* **`REQ-PRO-128.4` $\rightarrow$ `TST-PRO-128.4`**: Extended terminal statuses (`abgelehnt`, `rejected`, `geschlossen`, `resolved`) & Höchstmaß `fixed_awaiting_human` parity.
* **`REQ-PRO-128.5` $\rightarrow$ `TST-PRO-128.5`**: Stage-subtask (`identify_gate_number(summary) is not None`) zero-comment closure guard in `transition_issue()`.
* **Invariants**: Zero `app/src/main/` edits; `ANT-Android-SDKs` untouched; `overall_ftpr_percent` (Gates 1–5) & SPC limits (`REQ-PRO-122.1/.4`) preserved; `test_subtask_freigabe_transitions_to_erledigt` (`test_jira_accounts.py:312`) preserved; `tst_process_stability.md` kept `<= 4000` lines (`3977` currently — update status badge & 1-line Change History concisely); `Rule 5` offline mocked unit tests only.

---

## 2. Atomic Stage 4 Implementation Sequence
1. **Step 1 (`tools/sprint_metrics.py`) — Verdict Keywords, End-to-End FTPR, Risk & Bug Statuses (`REQ-PRO-128.1..4`)**:
   - Extract shared helper `_parse_subtask_verdict_token(body_text)` matching word-bounded `REVISION`/`REWORK`/`FAIL` (revision) and `PASS`/`APPROVED`/`FREIGABE` (pass) after `extract_text_from_comment_body()`.
   - Add `end_to_end_ftpr_percent` (`sum(pft[1..6]) / sum(total[1..6]) * 100.0`) in `quality_gates` & `format_metrics_markdown()`, leaving `overall_ftpr_percent` (`range(1, 6)`) untouched.
   - Add `_classify_issue_risk(issue, fields, repo_root=None)` checking `severity-*` labels (`critical`→`Highest`, `major`→`High`, `minor`→`Medium`, `cosmetic`→`Low`), `[Highest]`/`[High]`/`[Low]` summary tags, and Stage 1 `Risk Rating` (`description` or `docs/engineering/analysis/<KEY>_analysis.md`) before `fields.priority.name`.
   - Extend terminal statuses (`"erledigt"`, `"closed"`, `"done"`, `"abgelehnt"`, `"rejected"`, `"geschlossen"`, `"resolved"`) in `is_human_accepted` / `is_terminal_status`.
2. **Step 2 (`tools/jira_util.py`) — `export-gates` Parity & Stage-Subtask Closure Guard (`REQ-PRO-128.1, .5`)**:
   - In `extract_sprint_gate_records()`, normalize comments via `extract_text_from_comment_body()`, resolve untagged non-reviewer authors as `"unknown"` (not `"agent2"`), use shared verdict matching, and remove the `elif st_status in (...)` fabricated `PASS` branch (count `unverified` instead).
   - In `transition_issue()`, when `is_subtask`, `normalized_target == "freigabe"` (or target `Erledigt`), and `identify_gate_number(issue_summary) is not None`, query `/comment` (or subtask comments) and exit `1` unless a valid `agent2` `PASS`/`APPROVED`/`FREIGABE` verdict comment exists (after `gate_verdict()`'s `add_comment` step; summary-less `ATT-9999` unaffected).
3. **Step 3 (`tools/hoechstmass/tools/sprint_metrics.py`) — Höchstmaß Mirror Parity (`REQ-PRO-128.1..4`)**:
   - Sync extended verdict keywords, `end_to_end_ftpr_percent`, `_classify_issue_risk()`, `fixed_awaiting_human`, and `abgelehnt`/`rejected`/`geschlossen`/`resolved` terminal mapping while preserving `HoechstmassCoreFrameworkContractTest` & `HoechstmassToolingContractTest`.
4. **Step 4 (Offline Unit Tests & Governance Verification — `TST-PRO-128.1..5`)**:
   - Add unit tests tagged with `TST-PRO-128.1`–`TST-PRO-128.5` in docstrings across `tools/test_sprint_metrics.py`, `tools/test_jira_accounts.py`, and `tools/hoechstmass/tools/test_sprint_metrics.py` (`tools/hoechstmass/tools/test_hoechstmass_tools.py` mirror check if needed).
   - Run `python3 -m unittest tools.test_sprint_metrics tools.test_jira_accounts tools.hoechstmass.tools.test_sprint_metrics tools.hoechstmass.tools.test_hoechstmass_tools` and `python3 tools/verify_requirement_governance.py --check-size --check-shard-headers --check-doc-headers --check-links`.

---

## 3. Rollback Plan
* `git checkout feature/ATT-8205 -- tools/` restores all modified Python modules cleanly on the isolated branch.
