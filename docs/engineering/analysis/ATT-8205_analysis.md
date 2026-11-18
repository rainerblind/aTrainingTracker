# Stage 1 Analysis: ATT-8205 - Fix sprint_metrics verdict parsing, Gate 6 FTPR, risk/bug mapping, and zero-comment subtask closure

**DOC-ID**: `DOC-ANA-ATT-8205` | **Ticket**: `ATT-8205` | **Epic**: `ATT-8138`  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-18_01_Früh` (`f55c9fee`)  
**Branch**: `feature/ATT-8205` | **Author**: AI Agent 1 (`--as agent1`, `flash`) | **Date**: 2026-11-18

---

## 1. Problem Statement & Motivation
ASPICE 4.0 Gap #8 (`SUP.1 BP3/BP4`, `SUP.9 BP7`, `PA 4.1`) identified 5 metric-parser and workflow-guard distortions across `tools/sprint_metrics.py` and `tools/jira_util.py` (contradicting `export-gates` in `2026-46.6`, masking Gate 6 bounces in `overall_ftpr_percent`, flattening `risk_distribution` to `100% Medium`, miscounting `Final Review (Human)` bugs, and allowing silent zero-comment subtask closure).

---

## 2. Root Cause Analysis & Independent Peer Review & Verification
Audited all 5 ticket claims against live `f55c9fee` (`ATT-8165`/`c50cbf25`, `ATT-8168`/`459f5eb5`, `ATT-8207`/`3b390f63`):
1. **Verdict Regex & `export-gates` Divergence (REFINED)**: `VERDICT_PASS_RE`/`VERDICT_REV_RE` do **not** exist at `sprint_metrics.py:42`; `FINAL_REVIEW_*_RE` (`L45–56`) already use `re.IGNORECASE` and `parse_verdicts_from_comments()` (`L222–250`) uppercases `body_upper` (`L243`). **True root cause**: `parse_verdicts_from_comments()` matches only `"PASS"`/`"REVISION"`, missing `"APPROVED"`/`"FREIGABE"`/`"REWORK"`/`"FAIL"` (causing the 6 `2026-46.6` `Approved:` subtasks e.g. `ATT-7958`, `ATT-7969` to become `unverified`), while `extract_sprint_gate_records()` (`jira_util.py:4346–4398`) skips ADF extraction (`REQ-PRO-092.2`), defaults untagged comments to `agent2`, and still fabricates `PASS` (`passed_first_time += 1`) for zero-comment closed subtasks (`ATT-7796`, `ATT-7798`, violating `REQ-PRO-092.3`).
2. **Gate 6 Excluded from `overall_ftpr_percent` (CONFIRMED)**: `sprint_metrics.py:443–450` sums only `range(1, 6)`. **Caveat**: `REQ-PRO-122.4` (`ATT-8168`, `L533–543`) & `REQ-PRO-107.3` (`L748–764`) require Gates 1–5 FTPR to trigger blind-spot/rubber-stamping warnings unmasked by Gate 6, so we must report `end_to_end_ftpr_percent` (Gates 1–6) alongside Gate 1–5 `overall_ftpr_percent` (`gates_1_5_ftpr_percent`).
3. **Flat `100% Medium` Risk Distribution (CONFIRMED)**: `sprint_metrics.py:315–320` reads only `fields.priority.name` (confirmed `47/47 Medium` in `2026-46.6`, `12/12` in `2026-11-17_03_Nacht`), ignoring `[Highest]`/`[High]`/`[Low]` summary tags, `severity-*` labels, and Stage 1 `Risk Rating`.
4. **Bug Status Mapping & Severity Fallback (PARTIALLY FIXED BY `ATT-8165`)**: `ATT-8165` (`c50cbf25`, `L348–392`, `L526–530`) already added `fixed_awaiting_human` for `Final Review (Human)` and `severity-*` / description precedence before `p_map` (`100.0%` in `2026-11-17_03_Nacht`). **Remaining gaps**: `L297`/`L348` omit `"abgelehnt"`/`"rejected"`/`"geschlossen"`/`"resolved"` (`jira_util.py:5081` uses them), and `tools/hoechstmass/tools/sprint_metrics.py:217–221,376–379` still lacks `ATT-8165` bug-status parity.
5. **Zero-Comment Subtask Closure Bypass (CONFIRMED)**: `jira_util.py:1026–1234` (`transition_issue` via `move`, `L5804`) transitions `[Analysis]`..`[Test]` subtasks to `Erledigt` (`freigabe`/`done`/`erledigt`) and advances the parent (`L1232–1233`) with zero `agent2` verdict comments. Note `test_subtask_freigabe_transitions_to_erledigt` (`test_jira_accounts.py:312`) mocks a summary-less non-stage subtask (`ATT-9999`), so gating strictly on `identify_gate_number(issue_summary) is not None` preserves non-stage subtasks.

---

## 3. Scope Grounding & Non-Goals
* **In-Scope (`2 SP`)**: (1) Unify Gate 1–5 verdict parsing (`PASS`/`APPROVED`/`FREIGABE` vs `REVISION`/`REWORK`/`FAIL`) with ADF extraction and remove the `extract_sprint_gate_records` empty-comment `PASS` fabrication; (2) Add `end_to_end_ftpr_percent` (Gates 1–6) alongside preserved Gate 1–5 `overall_ftpr_percent`; (3) Derive `risk_distribution` from `[Highest]`/`[High]`/`[Low]` summary tags, `severity-*` labels, and Stage 1 `Risk Rating` before priority fallback; (4) Add German/English terminal statuses (`abgelehnt`, `rejected`, `geschlossen`, `resolved`) and sync `tools/hoechstmass/tools/sprint_metrics.py`; (5) Block `jira_util.py move` on stage subtasks (`[Analysis]`..`[Test]`) without an `agent2` PASS verdict comment; (6) Unit tests in `tools/test_sprint_metrics.py` & `tools/test_jira_accounts.py`.
* **Out-of-Scope**: `app/src/main/` changes; altering `overall_ftpr_percent` Gates 1–5 semantics required by `REQ-PRO-122.4`/`REQ-PRO-107.3`; blocking non-stage subtasks in `transition_issue`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Extends `REQ-PRO-058.4` (`ATT-7101`), `REQ-PRO-059.2` (`ATT-6149`), `REQ-PRO-063.3` (`ATT-6156`), `REQ-PRO-092.2/.3` (`ATT-7559`), and `REQ-PRO-121.1` (`c50cbf25`) across `tools/sprint_metrics.py`, `tools/jira_util.py`, and `tools/hoechstmass/tools/sprint_metrics.py`.
* **Historical Origin & Commit Trace**: `ATT-7559` introduced `unverified` in `sprint_metrics.py` (`459f5eb5` `ATT-8168` locked Gate 1–5 plausibility checks in `REQ-PRO-122.4`; `c50cbf25` `ATT-8165` added `fixed_awaiting_human`); `extract_sprint_gate_records` (`ATT-6149`) retained an older `Erledigt -> PASS` fallback.
* **Root Reason for Existing Formulation**: `REQ-PRO-122.4` and `REQ-PRO-107.3` intentionally isolate Gates 1–5 FTPR from Gate 6 so downstream bounces cannot hide upstream rubber-stamping; `test_subtask_freigabe_transitions_to_erledigt` uses a summary-less subtask mock.
* **Preservation of Core Invariants**: Preserve Gate 1–5 `overall_ftpr_percent` for `plausibility_warnings` (`TST-PRO-122.4`) and M3 alerts (`TST-PRO-107.3`), `gate_verdict()` caller flow (`assign` + `add_comment` before `transition_issue`), `process_metrics.jsonl` schema compatibility, and `Rule 5` offline mocked unit tests.

---

## 5. Change Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-PRO-059.2`, `REQ-PRO-092.2/.3`, `REQ-PRO-121.1`, and `REQ-PRO-122` (`docs/requirements/req_process_stability_part1.md`, `part2.md`) + `TST-PRO-*` (`docs/tests/tst_process_stability.md`).
* **Affected Architecture Elements**: `tools/sprint_metrics.py`, `tools/jira_util.py`, `tools/hoechstmass/tools/sprint_metrics.py` (and `tools/hoechstmass/tools/jira_util.py` if mirrored).
* **Verification Impact & Test Surface**: `tools/test_sprint_metrics.py`, `tools/test_jira_accounts.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`, `tools/verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: **LOW** — Offline Python tooling only; zero `app/src/main/` edits; preserves `REQ-PRO-122.4` plausibility/SPC baselines and non-stage subtask `move` compatibility.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: `app/src/main/` untouched; `ANT-Android-SDKs` untouched; `REQ-PRO-120` monotonic `Version` bump + `Reviewed` on edited governed docs; zero live Jira calls in unit tests (`Rule 5`); PO dirty `tools/*` files left unstaged.
* **Risk Rating**: **LOW** (`2 SP` bounded Python tooling & governance fix).
