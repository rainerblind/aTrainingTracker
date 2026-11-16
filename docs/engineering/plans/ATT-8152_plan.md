# Stage 3: Implementation Plan - ATT-8152: Fix qa-audit tool criteria to use requirement shards, fetched descriptions and random sampling

**Ticket**: [ATT-8152](https://rainerblind.atlassian.net/browse/ATT-8152)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_02_Spät`  
**Requirement Mapping**: `REQ-PRO-091` (`REQ-PRO-091.2`, `REQ-PRO-091.6`; extends `REQ-PRO-061`, `REQ-PRO-105`)  
**Test Mapping**: `TST-PRO-091` (`TST-PRO-091.7`..`TST-PRO-091.10`)  
**Branch**: `feature/ATT-8152` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-16  

---

## 1. Problem Description & Background
ASPICE 4.0 Audit Finding `M5` (SUP.1 BP2/BP4) identified that `qa-audit` reported 0% compliance across all historical audits (sprints 46.3–46.6) due to 4 systemic bugs: (1) loading only `docs/requirements.md` (summary table with 2 sample REQs) instead of all 9 domain shards, (2) dropping `description` in Jira issue fetch query, (3) display-name substring matching for 4-Augen role separation, and (4) deterministic first-N slicing instead of seeded representative random sampling.

---

## 2. Traceability & Requirements Mapping
* **`REQ-PRO-091.6` $\rightarrow$ `TST-PRO-091.7`**: `qa_audit` scans all 9 domain requirement shards (`docs/requirements/req_*.md`) resolving ~1,194 active requirements.
* **`REQ-PRO-091.6` $\rightarrow$ `TST-PRO-091.8`**: `fetch_all_sprint_issues` includes `description` in Jira Cloud query fields.
* **`REQ-PRO-091.6` $\rightarrow$ `TST-PRO-091.9`**: `qa_audit` verifies 4-Augen role separation by comparing Atlassian `accountId`s against `ROLE_ACCOUNT_IDS`.
* **`REQ-PRO-091.2` $\rightarrow$ `TST-PRO-091.10`**: `qa_audit` samples via seeded pseudo-randomization (`--seed`), recording random seed and sampled keys in the report header.

---

## 3. System Invariants & Preserved Behavior
1. **Dynamic Sample Size Floor**: Preserves $\text{sample\_size} \ge \max(2, \lceil 0.10 \times N \rceil)$ requirement.
2. **Auditor Independence**: Preserves independent role enforcement (`qa` / `agent2`, rejecting `coordinator` / `agent1`).
3. **Pure Python Standard Library**: Zero external runtime dependencies; offline mockable execution.
4. **Submodule & Decision Gate**: `ANT-Android-SDKs` remains untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`; Rule 3 human decision gate preserved.

---

## 4. Proposed Architectural Changes
* **Issue Description Query (`tools/jira_util.py`)**:
  - Add `description` to the `fields` argument string in `fetch_all_sprint_issues`.
* **Shard Scanning (`tools/jira_util.py`)**:
  - In `qa_audit`, load all files matching `docs/requirements/req_*.md` and `docs/tests/tst_*.md` to populate `master_req_ids` with all ~1,194 active living requirement IDs.
* **AccountId-Based 4-Augen Verification (`tools/jira_util.py`)**:
  - Compare comment author `accountId` against `ROLE_ACCOUNT_IDS["agent2"]` / `ROLE_ACCOUNT_IDS["qa"]` and flag violations if `author_id == ROLE_ACCOUNT_IDS["agent1"]`.
* **Seeded Random Sampling & Report Formatting (`tools/jira_util.py`)**:
  - Accept `seed=None` parameter and `--seed` CLI argument; compute `random.randint(10000, 99999)` if omitted.
  - Sample pool via `random.Random(seed).sample(pool, sample_size)`.
  - In `format_qa_audit_markdown`, document Random Seed and Sampled Keys in the Section 1 metadata table.
* **Sprint 46.6 Report Remediation (`docs/engineering/qa_audits/sprint_2026-46.6.md`)**:
  - Re-run `qa-audit` with seeded sampling and commit corrected audit report.
* **UI Consistency**: No UI changes (developer audit tooling).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1 (`REQ-PRO-091.6`)**: Include `description` in `fetch_all_sprint_issues` fields query in `tools/jira_util.py`.
* **Step 2 (`REQ-PRO-091.6`)**: Update `qa_audit` to scan all domain shards in `docs/requirements/req_*.md` for `master_req_ids`.
* **Step 3 (`REQ-PRO-091.6`)**: Replace display-name substring matching with `accountId` checking against `ROLE_ACCOUNT_IDS`.
* **Step 4 (`REQ-PRO-091.2`)**: Implement seeded pseudo-random sampling with `--seed` CLI argument and update markdown report header.
* **Step 5 (`TST-PRO-091.7..10`)**: Add comprehensive unit tests in `tools/test_jira_accounts.py` covering compliant and non-compliant fixtures.
  - Test Command: `python3 -m unittest tools/test_jira_accounts.py`
* **Step 6 (Report Remediation)**: Re-run audit for sprint `2026-46.6` and commit corrected `docs/engineering/qa_audits/sprint_2026-46.6.md`.

---

## 6. Verification & Rollback Plan
* **Verification**: Run `test_jira_accounts.py`, `tools/verify_requirement_governance.py --check-qa-audits`, and verify 0 code orphans.
* **Rollback**: Branch isolation on `feature/ATT-8152` enables immediate revert without affecting `sprint/2026-11-16_02_Spät`.
