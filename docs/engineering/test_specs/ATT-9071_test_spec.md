# Stage 2: Requirement & Test Specification - ATT-9071

**Ticket**: [ATT-9071](https://rainerblind.atlassian.net/browse/ATT-9071) (`2 SP`, Minor) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`
**Requirement**: `REQ-PRO-134` (*qa-audit Merged-Only Sampling, SUP.9 Non-Code Exemption & Plan Variant Support*)
**Test Spec ID**: `TST-PRO-134` | **Branch**: `feature/ATT-9071` | **Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-19

---

## 1. Requirement Specification (`REQ-PRO-134`)

### 1.1 Attributes
- **Priority**: High | **Type**: Non-Functional | **Verification Method**: Unit / Review
- **`SYS.2` / `SYS.3` Impact**: None (`REQ-PRO-131.4`: Python QA audit & governance tooling only).
- **Localization Parity**: `N/A - Python tooling only`.

### 1.2 Formal Requirements
1. **`REQ-PRO-134.1` (Post-Stage-6 Merged Statuses Only)**: `qa_audit()` in `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py` SHALL restrict `completed_parents` strictly to post-Stage-6 merged statuses `("final review (human)", "final review", "erledigt", "closed", "done")`, explicitly excluding `"final review (agent)"` and all WIP states (`zu erledigen`, `analysis`, `test spec`, `implementation plan`, `implementation`, `test`).
2. **`REQ-PRO-134.2` (No WIP Fallback & Zero/Low-Completion Handling)**: `qa_audit()` MUST NOT fall back to unmerged WIP `parent_issues` when `len(completed_parents) < sample_size`. `min_sample` and `total_tickets` SHALL be computed from `len(completed_parents)` (`max(2, ceil(N * 0.10))` when `N >= 2`), sampling `min(sample_size, len(completed_parents))` from `completed_parents`. When `len(completed_parents) == 0`, `qa_audit()` SHALL record an explicit `COMPLIANT` report noting `N/A (0 merged tickets in sprint)` with zero corrective-action ticket creation.
3. **`REQ-PRO-134.3` (SUP.9 Non-Code Resolution Ingestion & Exemption)**: `qa_audit()` SHALL request `"labels"` in `fetch_all_sprint_issues()` / `fallback_fields` (and both `"labels"` and `"description"` in Höchstmaß `qa_audit()`). Tickets carrying `NON_FIX_RESOLUTION_LABELS` (`resolution-duplicate`, `resolution-rejected`, `resolution-cannot-reproduce`) MUST NOT fail code-lifecycle checks (`Lifecycle Deliverables`, `Stage Subtask Progression`, `4-Augen Role Separation`, `Living Documentation Parity`, `Commit Hygiene`); instead `qa_audit()` SHALL mark those 5 checks `N/A (SUP.9 non-code resolution)` while verifying SUP.9 resolution metadata (`resolution-*` label / `[SUP.9 RESOLUTION:` comment) and `PO Approval Provenance`, creating zero spurious CA tickets.
4. **`REQ-PRO-134.4` (Plan Filename Variant Support)**: `qa_audit()` `deliv_checks["plan"]` (root + Höchstmaß) SHALL accept both `docs/engineering/plans/<KEY>_plan.md` and `docs/engineering/plans/<KEY>_impl_plan.md` while keeping Stage 3 guidance (`stage3-impl-plan/SKILL.md`) canonical on `<KEY>_plan.md`.
5. **`REQ-PRO-134.5` (Governance Verifier Compatibility & Offline Mocking)**: `_validate_single_qa_audit()` in `tools/verify_requirement_governance.py` and `tools/hoechstmass/tools/verify_requirement_governance.py` SHALL accept valid `N/A (0 merged tickets in sprint)` and `< 2` merged-ticket audit reports when `total_merged < 2`, while strictly enforcing `>= max(2, ceil(0.10 * N))` whenever `N >= 2`. All new unit tests MUST mock Jira HTTP calls offline (`Rule 5`).

### Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: Refines `REQ-PRO-091` (`REQ-PRO-091.2`, `REQ-PRO-091.5`, `REQ-PRO-091.6`) via net-new `REQ-PRO-134` / `TST-PRO-134`, aligning with `REQ-PRO-121.2` (`NON_FIX_RESOLUTION_LABELS`) and `REQ-PRO-125` (`ATT-8207`). Targets `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/hoechstmass/tools/verify_requirement_governance.py`, `tools/test_jira_accounts.py`, and `tools/hoechstmass/tools/test_hoechstmass_tools.py`.
- **Historical Origin & Commit Trace**: `ATT-6154` (`824b1322`, `REQ-PRO-061`), `ATT-7101` (`f6ef21f2`, added `Final Review (Agent)`), `ATT-7558`/`ATT-8152` (`f3e42aa0`, `REQ-PRO-091`), `ATT-8559` (`b1329131`, `REQ-PRO-118`), `ATT-8207` (`3b390f63`, `REQ-PRO-125` deferred merge until Stage 6 `PASS`).
- **Root Reason for Existing Formulation**: Pre-`ATT-8207`, `Final Review (Agent)` tickets were already squashed on `sprint/<SPRINT>`, `parent_issues` fallback prevented empty reports before `resolve-bug` non-code states (`ATT-8165`), and only `_plan.md` existed in early templates.
- **Preservation of Core Invariants**: Preserves `>= max(2, ceil(0.10 * N))` sampling for `N >= 2`, `agent2`/`qa` independence (`REQ-PRO-091.1`), fail-loud CA creation (`REQ-PRO-118.2`), `REQ-PRO-016` human gate, and zero live Jira HTTP calls in tests (`Rule 5`).

### 1.4 Acceptance Criteria (Given-When-Then)
- *Given* 3 `Final Review (Human)` tickets and 1 `Final Review (Agent)` ticket, *When* `qa-audit` runs, *Then* only `Final Review (Human)` tickets are eligible for sampling (`REQ-PRO-134.1`).
- *Given* 1 `Final Review (Human)` ticket and 2 `Implementation` WIP tickets (or 0 merged tickets), *When* `qa-audit` runs, *Then* only the 1 merged ticket is sampled (or `N/A (0 merged tickets in sprint)` COMPLIANT report is emitted) with zero WIP remediation tickets, and `--check-qa-audits` passes (`REQ-PRO-134.2`, `REQ-PRO-134.5`).
- *Given* a merged bug labeled `resolution-duplicate`, `resolution-rejected`, or `resolution-cannot-reproduce` with 0 commits/subtasks/deliverables, *When* `qa-audit` runs, *Then* `labels` is fetched, code checks report `N/A (SUP.9 non-code resolution)`, and zero CA tickets are created (`REQ-PRO-134.3`).
- *Given* merged tickets with `<KEY>_plan.md` or `<KEY>_impl_plan.md`, *When* `qa-audit` checks Lifecycle Deliverables, *Then* both pass (`REQ-PRO-134.4`).

---

## 2. Test Specification (`TST-PRO-134`) & Traceability Matrix

| Test ID | Target | Procedure & Expected Result (Mocked Offline, `Rule 5`) | Req | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-134.1` | `tools/test_jira_accounts.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py` | Sprint with `Final Review (Human)` + `Final Review (Agent)` + `Implementation` WIP: assert `Final Review (Agent)` and WIP are never sampled. | `REQ-PRO-134.1` | Planned |
| `TST-PRO-134.2` | `tools/test_jira_accounts.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py` | Sprint with `1` merged + `2` WIP, and `0` merged + `2` WIP: assert `1` sampled (no WIP fallback) and `0` sampled `COMPLIANT` `N/A (0 merged tickets in sprint)` with 0 CA POSTs. | `REQ-PRO-134.2` | Planned |
| `TST-PRO-134.3` | `tools/test_jira_accounts.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py` | Assert `fields` query requests `labels` (and `description` in Höchstmaß); tickets with `resolution-duplicate`/`rejected`/`cannot-reproduce` & 0 commits/subtasks pass with `N/A (SUP.9 non-code resolution)` and 0 CA POSTs. | `REQ-PRO-134.3` | Planned |
| `TST-PRO-134.4` | `tools/test_jira_accounts.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py` | Merged tickets with `<KEY>_plan.md` and `<KEY>_impl_plan.md` both pass `deliv_checks["plan"]`. | `REQ-PRO-134.4` | Planned |
| `TST-PRO-134.5` | `tools/test_verify_requirement_governance.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py` | Assert `_validate_single_qa_audit()` passes `N/A (0 merged tickets in sprint)` and `1 / 1` low-completion reports while still rejecting under-sampled `N >= 2` reports. | `REQ-PRO-134.5` | Planned |
