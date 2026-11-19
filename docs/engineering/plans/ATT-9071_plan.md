# Stage 3 Implementation Plan: ATT-9071 - qa-audit False Positives Fix

**Ticket**: [ATT-9071](https://rainerblind.atlassian.net/browse/ATT-9071) (`2 SP`, Minor) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`
**Requirements**: `REQ-PRO-134` (`REQ-PRO-134.1`–`REQ-PRO-134.5`, refining `REQ-PRO-091.2/.5/.6`)
**Test Spec**: `TST-PRO-134` (`TST-PRO-134.1`–`TST-PRO-134.5`) | **Branch**: `feature/ATT-9071` | **Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-19

---

## 1. SWE.2 Architecture, Layer Boundaries & UI/SYS Scope
- **Scope & Layering**: Pure Python CLI & governance tooling (`tools/` + `tools/hoechstmass/tools/`). Zero `app/src/main/` production code, zero SQLite schema migrations, zero UI/Compose changes (`UI Consistency: N/A`), zero `SYS.2`/`SYS.3` changes (`REQ-PRO-131.4`).
- **DRY / Reuse**: Reuse existing `NON_FIX_RESOLUTION_LABELS` (`tools/jira_util.py:2991-2995`), `verify_po_approval_changelog()`, `extract_text_from_comment_body()`, and `paged_jql_search()`. Extract small shared helper predicates where root and Höchstmaß logic align (`_is_sup9_non_code_resolution(item)`).

---

## 2. Atomic Implementation Steps (1:1 `REQ` / `TST` Mapping)

| Step | Target File(s) | Atomic Change & Exact Scope | Maps To |
| :--- | :--- | :--- | :--- |
| **1** | `tools/jira_util.py` (`qa_audit`, `format_qa_audit_markdown`, `L4991-5530`) | (a) Add `"labels"` to `fetch_all_sprint_issues` (`L5139`) & `fallback_fields` (`L5170`); (b) Restrict `completed_parents` (`L5199-5203`) strictly to `("final review (human)", "final review", "erledigt", "closed", "done")`; (c) Remove `else parent_issues` WIP fallback (`L5204`), compute `min_sample`/`total_tickets` from `len(completed_parents)` (`min(sample_size, len(completed_parents))`), and render `N/A (0 merged tickets in sprint)` (`COMPLIANT`, 0 CA POSTs) when empty; (d) Detect `NON_FIX_RESOLUTION_LABELS` via helper `_is_sup9_non_code_resolution()`, mark the 5 code checks `N/A (SUP.9 non-code resolution)` (verifying SUP.9 label/comment & `PO Approval Provenance`), zero CA creation; (e) Accept `{key}_plan.md` or `{key}_impl_plan.md` in `deliv_checks["plan"]` (`L5259`). | `REQ-PRO-134.1`..`.4` |
| **2** | `tools/hoechstmass/tools/jira_util.py` (`qa_audit`, `format_qa_audit_markdown`, `L1834-2195`) | Mirror Step 1 in Höchstmaß: add `"description,labels"` to sprint issue query (`L1958`), restrict `completed_parents` (`L1993-1997`), remove WIP fallback + handle `0`/`1` merged tickets (`L1991,1998,1999,2175`), add SUP.9 `NON_FIX_RESOLUTION_LABELS` `N/A` exemption, and accept `{key}_plan.md` or `{key}_impl_plan.md` (`L2033`). | `REQ-PRO-134.1`..`.4` |
| **3** | `tools/verify_requirement_governance.py` (`L712-824`), `tools/hoechstmass/tools/verify_requirement_governance.py` (`L450-528`) | Update `_validate_single_qa_audit()` in both root and Höchstmaß to accept valid `N/A (0 merged tickets in sprint)` (`0 / 0`) and low-completion (`total_cnt < 2`, e.g. `1 / 1`) audit reports while strictly enforcing `< 2` and `>= max(2, ceil(0.10 * N))` whenever `total_cnt >= 2` or no zero/low-merged rationale is present. | `REQ-PRO-134.5` |
| **4** | `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py` | Add offline mocked unit tests (`Rule 5`) tagged `TST-PRO-134.1`..`TST-PRO-134.5` covering: (1) `"final review (agent)"` & WIP exclusion, (2) `1`-merged + WIP and `0`-merged `N/A` handling, (3) `labels` query + `resolution-duplicate`/`rejected`/`cannot-reproduce` `N/A` exemption, (4) `_plan.md` & `_impl_plan.md` acceptance, (5) `_validate_single_qa_audit()` zero/low-merged PASS & `N >= 2` under-sampled FAIL. | `TST-PRO-134.1`..`.5` |
| **5** | `docs/requirements/req_process_stability_part2.md`, `docs/tests/tst_process_stability_part2.md`, `docs/requirements.md`, `docs/tests.md` | Promote `REQ-PRO-134` / `TST-PRO-134` to `[Implemented]` in Stage 4 (and `[Verified]` in Stage 5), bump patch versions, add Change History rows, and run `--sync-indexes`. | `REQ-PRO-134`, `TST-PRO-134` |

---

## 3. System Invariants & Verification Plan
- **Invariants**: (1) Zero Android `app/` edits (`0` Gradle runs needed); (2) `qa_audit()` return signature `(out_path, audit_results)` and `stage3-impl-plan/SKILL.md` canonical `<KEY>_plan.md` guidance preserved; (3) `REQ-PRO-091.1` (`qa`/`agent2` role check), `REQ-PRO-118.2` (fail-loud CA creation), and `REQ-PRO-016` (Human Gate) intact; (4) `Rule 5` offline Jira mocking (`0` live Jira writes); (5) 4 user-owned dirty `tools/` files & `docs/presentations/vorgehensmodell/` untouched.
- **Verification Commands (Stage 4/5)**:
  - `python3 -m unittest tools/test_jira_accounts.py tools/test_verify_requirement_governance.py tools/hoechstmass/tools/test_hoechstmass_tools.py`
  - `python3 tools/verify_requirement_governance.py --base-ref refs/heads/sprint/2026-11-19_03_Nacht --check-verified-evidence --check-qa-audits`
