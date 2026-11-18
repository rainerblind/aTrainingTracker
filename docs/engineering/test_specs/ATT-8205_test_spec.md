# Stage 2: Requirement & Test Specification - ATT-8205

**DOC-ID**: `DOC-TST-ATT-8205` | **Ticket**: [ATT-8205](https://rainerblind.atlassian.net/browse/ATT-8205) (`2 SP`) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8205`  
**Requirement Mapping**: `REQ-PRO-128` (`[Planned]`) | **Test Spec ID**: `TST-PRO-128` (`[Planned]`)  
**Localization parity**: `N/A - no UI strings` | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-PRO-128` `[Planned]`)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review`
* **Implementation**: `tools/sprint_metrics.py`, `tools/jira_util.py`, `tools/hoechstmass/tools/sprint_metrics.py`
* **REQ-PRO-128.1 (Extended Verdict Keywords, ADF Extraction & Zero-Comment `unverified` Parity)**: `parse_verdicts_from_comments()` (`tools/sprint_metrics.py`) and `extract_sprint_gate_records()` (`tools/jira_util.py`) SHALL recognize `PASS`/`APPROVED`/`FREIGABE` (pass) and `REVISION`/`REWORK`/`FAIL` (revision) across plain-text and Jira Cloud v3 ADF comment bodies (`extract_text_from_comment_body()`), SHALL NOT default untagged non-reviewer comments to `agent2`, and SHALL classify zero-comment closed stage subtasks (`Erledigt`/`Closed`/`Done`) as `unverified` instead of fabricating `passed_first_time += 1` or synthetic `PASS` records.
* **REQ-PRO-128.2 (Additive End-to-End FTPR Preserving Gate 1–5 `overall_ftpr_percent`)**: `parse_sprint_gate_metrics()` (`tools/sprint_metrics.py`) SHALL emit an additive `end_to_end_ftpr_percent` metric across Gates 1–6 in `quality_gates` and `format_metrics_markdown()`, while preserving `overall_ftpr_percent` strictly over Gates 1–5 (`range(1, 6)`) so plausibility warnings and multi-sprint rubber-stamping alarms remain unmasked by Gate 6 bounces.
* **REQ-PRO-128.3 (Multi-Signal Risk Distribution Parsing)**: `parse_sprint_gate_metrics()` SHALL classify `risk_distribution` (`Low`/`Medium`/`High`/`Highest`) from `severity-*` labels, `[Highest]`/`[High]`/`[Low]` summary tags, and Stage 1 `**Risk Rating**` / `Risk Rating:` text before falling back to `fields.priority.name`.
* **REQ-PRO-128.4 (Extended Bug Status Mapping & Höchstmaß Parity)**: `parse_sprint_gate_metrics()` in `tools/sprint_metrics.py` and `tools/hoechstmass/tools/sprint_metrics.py` SHALL treat `abgelehnt`, `rejected`, `geschlossen`, and `resolved` as terminal closed statuses alongside `erledigt`/`closed`/`done`, and mirror `fixed_awaiting_human` (`Final Review (Human)` / `Final Review`) parity in Höchstmaß.
* **REQ-PRO-128.5 (Zero-Comment Stage Subtask Closure Guard)**: `transition_issue()` (`tools/jira_util.py`, invoked by `move`) SHALL reject transitions of stage subtasks (`identify_gate_number(issue_summary) is not None`) to `freigabe`/`Erledigt`/`done` when no `agent2` `PASS` verdict comment exists, while preserving transitions on summary-less or non-stage subtasks (`tools/test_jira_accounts.py:312`).
* **Given-When-Then Acceptance Criteria**:
  * *Given* stage comments containing `APPROVED`/`FREIGABE`/`REWORK`/`FAIL` (plain text or ADF) and zero-comment `Erledigt` subtasks, *When* `parse_verdicts_from_comments()` and `extract_sprint_gate_records()` execute, *Then* synonym verdicts are extracted and zero-comment subtasks become `unverified` with zero fabricated `PASS` records.
  * *Given* Gate 1–5 subtasks and Gate 6 reviews, *When* `parse_sprint_gate_metrics()` executes, *Then* `overall_ftpr_percent` covers Gates 1–5 (preserving plausibility warnings) while `end_to_end_ftpr_percent` covers Gates 1–6.
  * *Given* `severity-*` labels, `[Highest]`/`[High]`/`[Low]` summary tags, Stage 1 `Risk Rating`, and `abgelehnt`/`rejected`/`geschlossen`/`resolved` bug statuses, *When* root and Höchstmaß `parse_sprint_gate_metrics()` run, *Then* `risk_distribution` and `problem_trends` classify all buckets accurately.
  * *Given* a stage subtask without an `agent2` `PASS` comment vs a summary-less subtask (`ATT-9999`), *When* `transition_issue(..., "freigabe")` runs, *Then* the stage subtask fails non-zero while the summary-less subtask transitions cleanly.
* **System Invariants**: Zero `app/src/main/` edits; `ANT-Android-SDKs` untouched; `process_metrics.jsonl` schema backward-compatible; `Localization parity: N/A - no UI strings`.

## 2. Requirement Archaeology
* **Original Requirement ID & Target**: Net-new `REQ-PRO-128` (`REQ-PRO-128.1`–`REQ-PRO-128.5`), refining `REQ-PRO-059.2` (`ATT-6149`), `REQ-PRO-092.2/.3` (`ATT-7559`), `REQ-PRO-107.3` (`ATT-8164`), `REQ-PRO-121.1` (`ATT-8165`), and `REQ-PRO-122.4` (`ATT-8168`).
* **Historical Origin & Commit Trace**: `ATT-7559` introduced `unverified` in `sprint_metrics.py`; `ATT-6149` left an `Erledigt -> PASS` fallback in `extract_sprint_gate_records()`; `c50cbf25` (`ATT-8165`) added `fixed_awaiting_human`; `459f5eb5` (`ATT-8168`) locked Gate 1–5 plausibility checks (`f55c9fee` baseline).
* **Root Reason for Existing Formulation**: `REQ-PRO-122.4` and `REQ-PRO-107.3` intentionally isolate Gates 1–5 FTPR from Gate 6 so Stage 6 bounces cannot mask upstream blind spots; `test_subtask_freigabe_transitions_to_erledigt` (`test_jira_accounts.py:312`) mocks a summary-less subtask (`ATT-9999`).
* **Preservation of Core Invariants**: Preserves Gate 1–5 `overall_ftpr_percent` for plausibility and M3 rubber-stamping alarms, preserves `gate_verdict()` `assign`+`add_comment` flow before `transition_issue()`, and preserves summary-less non-stage subtask transitions.

---

## 3. Test Specification (`TST-PRO-128` `[Planned]`)
> **Evidence Tagging Note**: Stage 4 Python test cases in `tools/test_sprint_metrics.py`, `tools/test_jira_accounts.py`, and `tools/hoechstmass/tools/test_hoechstmass_tools.py` MUST be tagged in docstrings with `TST-PRO-128` / `TST-PRO-128.1`–`TST-PRO-128.5` so `verify_requirement_governance.py --check-verified-evidence` passes cleanly.
* **`TST-PRO-128.1` (`tools/test_sprint_metrics.py`, `tools/test_jira_accounts.py`)**: Verify `APPROVED`/`FREIGABE`/`REWORK`/`FAIL` parsing (plain text + ADF), non-`agent2` untagged author handling, and `unverified` segregation (zero fabricated `PASS`) across `parse_verdicts_from_comments()` and `extract_sprint_gate_records()`.
* **`TST-PRO-128.2` (`tools/test_sprint_metrics.py`)**: Verify `end_to_end_ftpr_percent` (Gates 1–6) alongside preserved Gate 1–5 `overall_ftpr_percent` and unmasked `PA 4.1 Plausibility Warning` emission.
* **`TST-PRO-128.3` (`tools/test_sprint_metrics.py`)**: Verify `risk_distribution` extraction from `severity-*` labels, `[Highest]`/`[High]`/`[Low]` summary tags, Stage 1 `Risk Rating`, and priority fallback.
* **`TST-PRO-128.4` (`tools/test_sprint_metrics.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`)**: Verify `abgelehnt`/`rejected`/`geschlossen`/`resolved` status mapping and `fixed_awaiting_human` parity in root and Höchstmaß `sprint_metrics.py`.
* **`TST-PRO-128.5` (`tools/test_jira_accounts.py`)**: Verify `transition_issue()` blocks zero-comment closure on stage subtasks (`[Analysis]`..`[Test]`) while preserving summary-less subtasks (`test_subtask_freigabe_transitions_to_erledigt` at `:312`, `Rule 5` offline mocks).

---

## 4. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-128.1` | Python Unit (Offline) | `parse_verdicts_from_comments()` & `extract_sprint_gate_records()` | `REQ-PRO-128.1` | Planned |
| `TST-PRO-128.2` | Python Unit (Offline) | `parse_sprint_gate_metrics()` (`overall_ftpr_percent` + `end_to_end_ftpr_percent`) | `REQ-PRO-128.2` | Planned |
| `TST-PRO-128.3` | Python Unit (Offline) | `parse_sprint_gate_metrics()` (`risk_distribution` multi-signal parsing) | `REQ-PRO-128.3` | Planned |
| `TST-PRO-128.4` | Python Unit (Offline) | Root & Höchstmaß `sprint_metrics.py` (`abgelehnt`/`rejected`/`geschlossen`/`resolved`) | `REQ-PRO-128.4` | Planned |
| `TST-PRO-128.5` | Python Unit (Offline) | `transition_issue()` stage subtask closure guard & `:312` preservation | `REQ-PRO-128.5` | Planned |
