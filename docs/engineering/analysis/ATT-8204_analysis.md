# Stage 1 Analysis: ATT-8204 - Fix record-review misclassifying named human reviewers as Layer 5 External AI Models and correct sprint 2026-46.4 review record

**Ticket**: [ATT-8204](https://rainerblind.atlassian.net/browse/ATT-8204) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-18_01_Früh` (`efa6fa1a`)  
**Branch**: `feature/ATT-8204` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-18

---

## 1. Problem Statement & Motivation
Under ASPICE SUP.1 BP5 / GP 2.2.4 (Audit Gap #7), `record_review()` in `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py` classifies a reviewer as human only when `reviewer.lower() in ("human", "user")`. Any named human reviewer (e.g., `--reviewer "Rainer Blind"`) is misclassified as `Cross-Vendor AI Review (Layer 5)` with `Vendor: Rainer Blind` and `Model & Version: External AI Model (vN/A)`, corrupting QA evidence in `docs/engineering/reviews/sprint_2026-46.4.md:3-16` (and `sprint_2026-47.1.md:3-16`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **True Root Cause**: `record_review()` (`tools/jira_util.py:5474-5485`; `tools/hoechstmass/tools/jira_util.py:2335-2346`) uses a brittle name allowlist (`reviewer.lower() in ("human", "user")`) instead of an explicit `--review-type` selector and explicit `--vendor`/`--model` presence checks, while CLI parsing (`tools/jira_util.py:6047-6128`; `hoechstmass:2598-2679`) lacks `--review-type`.
* **Independent Peer Review & Verification** (against live `efa6fa1a`):
  1. *Line Offset*: `record_review()` sits at `tools/jira_util.py:5401-5551` (bug at `5474-5485`), not `:3180` cited in the ticket.
  2. *Unit Test File Discrepancy*: `tools/test_review_tool.py` already exists and tests `tools/review_tool.py` (`.reviews/` Emacs diff tool), NOT `jira_util.py record-review`. Existing `record_review` tests (`TST-PRO-062.1`) live in `tools/test_jira_accounts.py:941-1088` and `tools/hoechstmass/tools/test_hoechstmass_tools.py:668-709`.
  3. *Additional Affected File*: `docs/engineering/reviews/sprint_2026-47.1.md:3-16` (`NOT PERFORMED`, `--reviewer "Rainer Blind"`) exhibits the identical misclassification (`Cross-Vendor AI Review (Layer 5)`, `External AI Model (vN/A)`).
  4. *`Review Type` Label Tension*: Ticket AC1 requires `"Human Sprint Review (Layer 4)"`, whereas `jira_util.py:5478`, `hoechstmass:2339`, `test_jira_accounts.py:975,982`, and 11 existing review files use `"Human Sprint Review (Ceremony 2)"`. `verify_independent_reviews()` (`tools/verify_requirement_governance.py:895-988`, `hoechstmass:599-695`) validates `Reviewer`, `Date`, `Scope`, and `Verdict` without failing on either label.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**: (1) Add `--review-type=human|cross-vendor-ai` to `record_review` and CLI in `tools/jira_util.py` + `tools/hoechstmass/tools/jira_util.py` (default `human` when `--vendor`/`--model` omitted; infer `cross-vendor-ai` when `--vendor`/`--model` given without `--review-type` for backward compatibility; require both `--vendor` and `--model` when `cross-vendor-ai` is selected); (2) Correct `docs/engineering/reviews/sprint_2026-46.4.md` (and `sprint_2026-47.1.md` if confirmed in Stage 2/3) with transparent correction notes; (3) Add unit tests in `tools/test_jira_accounts.py` + `tools/hoechstmass/tools/test_hoechstmass_tools.py` (note why `tools/test_review_tool.py` is separate); (4) Update `REQ-PRO-062` & `TST-PRO-062`.
* **Out-of-Scope Non-Goals**: Modifying `app/src/main/` or `tools/review_tool.py`; altering `PASS`/`SKIPPED`/`NOT PERFORMED` verdict rules (`ATT-8142`); rewriting unrelated review records (`46.3`, `46.5`–`46.8`, `47.2`, `2026-11-*`).
* **Localization Parity**: `N/A - no UI strings`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-062` (`REQ-PRO-062.1`, `REQ-PRO-062.2`, `docs/requirements/req_process_stability_part2.md:49-88`) & `TST-PRO-062` (`docs/tests/tst_process_stability.md:2486-2506`).
* **Historical Origin & Commit Trace**: Introduced in `ATT-6155` (`2d2f9663`, Sprint `2026-46.3`); amended by `ATT-8142` (`7ccac4fc`, Finding K2) for `SKIPPED`/`NOT PERFORMED` truth-in-evidence.
* **Root Reason for Existing Formulation**: `ATT-6155` assumed human Ceremony 2 invocations would always pass `--reviewer "Human"` and omitted an explicit review-type flag.
* **Preservation of Core Invariants**: Preserve mandatory `--verdict` + `--findings` validation (`ATT-8142`), `[PROCESS DEVIATION — Rule 1.3]` headers, backward compatibility when `--vendor`+`--model` are passed without `--review-type` (`test_jira_accounts.py:992`), and `verify_independent_reviews()` schema checks.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refine `REQ-PRO-062.1`/`.2` (`docs/requirements/req_process_stability_part2.md`, `2308` lines) and `TST-PRO-062.1`/`.3`/`.4` (`docs/tests/tst_process_stability.md`, `3980` lines).
* **Affected Architecture Elements**: `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `docs/engineering/reviews/sprint_2026-46.4.md` (plus `sprint_2026-47.1.md`), `.agents/skills/sprint-planner/SKILL.md` (check usage doc if needed).
* **Verification Impact & Test Surface**: `tools/test_jira_accounts.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`, `tools/test_verify_requirement_governance.py`, `python3 tools/verify_requirement_governance.py --check-reviews`, `python3 tools/shard_living_docs.py --check-size --strict`.
* **Risk Assessment & Invariants**: **LOW** — offline Python tooling & markdown evidence only; zero `app/src/main/` changes; `verify_independent_reviews` and `Rule 3` Human Gate preserved.

---

## 6. Architectural Strategy & Shard Line-Count Plan
* **Tooling Fix**: Replace name-guessing with explicit `review_type` normalization (`human` | `cross-vendor-ai`); reject `cross-vendor-ai` missing `--vendor` or `--model` with `ValueError`/exit `1`; emit `Human Sprint Review (Layer 4)` (updating `test_jira_accounts.py:975,982` in Stage 4 to match AC1 while `verify_independent_reviews` continues accepting legacy `Ceremony 2` records).
* **Critical Shard Line-Count Plan (`tst_process_stability.md` = `3980 / 4000` lines; `req_process_stability_part1.md` = `3979`; `_part2.md` = `2308`)**:
  * **Option A (Primary — Minimal Delta `<= 4000`)**: Update `TST-PRO-062` (`L2486-2506`) in place with concise sub-bullets and 1 consolidated Change History row per stage (`<= 12` net added lines -> `<= 3992` lines, leaving `>= 8` lines below `4000`). Do NOT touch `req_process_stability_part1.md` (`3979`); place all `REQ-PRO-062` updates in `_part2.md` (`2308`).
  * **Option B (Fallback if `> 4000` threatened)**: Split via `python3 tools/shard_living_docs.py --split-shard docs/tests/tst_process_stability.md`, remove monolithic `tst_process_stability.md`, and run `--sync-indexes` (`NonFunctionalRequirementsContractTest.kt` & `SystemArchitectureDesignContractTest.kt` already use `loadShardContent("docs/tests", "tst_process_stability")` supporting `*_partN.md`).

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Zero `app/src/main/` or `ANT-Android-SDKs` edits; PO dirty `tools/` files left untouched; `REQ-PRO-120` monotonic `Version` bump + `Reviewed` on edited governed docs; all unit tests strictly offline (`Rule 5`); Höchstmaß 100% parity maintained.
* **Risk Rating**: **LOW** (`1 SP` bounded Python CLI + review-record correction with full unit test and governance verification).
