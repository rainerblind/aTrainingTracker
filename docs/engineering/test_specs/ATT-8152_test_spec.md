# Stage 2: Requirement & Test Specification - ATT-8152: Fix qa-audit tool criteria to use requirement shards, fetched descriptions and random sampling

**Ticket**: [ATT-8152](https://rainerblind.atlassian.net/browse/ATT-8152)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_02_Spät`  
**Requirement Mapping**: `REQ-PRO-091` (`REQ-PRO-091.2`, `REQ-PRO-091.6`; extends `REQ-PRO-061`, `REQ-PRO-105`)  
**Test Spec ID**: `TST-PRO-091`  
**Branch**: `feature/ATT-8152` | **Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-16  

---

## 1. Requirement Specification (REQ-PRO-091)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review` | Target Status: `[Verified]`
* **REQ-PRO-091.2 (Seeded Representative Random Sampling)**: `qa_audit` SHALL select sampled tickets from the completed population using seeded pseudo-random sampling (`random.Random(seed).sample`), accepting an optional `--seed` parameter or generating a random integer seed. The audit report in `docs/engineering/qa_audits/<sprint>.md` SHALL explicitly document total population, sample size, random seed, and sampled keys in Section 1.
* **REQ-PRO-091.6 (Objective Process-Compliance Verification Criteria)**:
  * *Living Documentation Shards*: `qa_audit` SHALL resolve and evaluate referenced requirement IDs against all living requirement domain shards in `docs/requirements/req_*.md` (~1,194 active requirements) and `docs/tests/tst_*.md`, rather than summary-only master tables.
  * *Description Ingestion*: `fetch_all_sprint_issues` SHALL query the `description` field from Jira Cloud to evaluate requirements cited within ticket descriptions.
  * *AccountId-Based Role Separation*: `qa_audit` SHALL verify author and reviewer separation by evaluating Atlassian `accountId`s against `ROLE_ACCOUNT_IDS` rather than matching display-name strings.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-PRO-091` and `REQ-PRO-105.3`. Targets `tools/jira_util.py`, `tools/test_jira_accounts.py`, and `docs/engineering/qa_audits/`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-46.3 via `ATT-6154` (`REQ-PRO-061`), extended in Sprint 2026-46.6 via `ATT-7558`, and bugfixed via `ATT-8152` (Finding M5).
* **Root Reason for Existing Formulation**: Early tooling used basic heuristics and single-file master lookups before requirements were partitioned into 9 domain shards.
* **Preservation of Core Invariants**: Dynamic sample size $\ge \max(2, \lceil 0.10 \times N \rceil)$ preserved; independent auditor role enforcement preserved; pure Python stdlib offline execution preserved; `ANT-Android-SDKs` submodule untouched.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Seeded Random Sampling & Metadata Header)**: *Given* an execution of `qa_audit`, *When* tickets are sampled, *Then* selection uses seeded pseudo-random sampling, and the random seed and sampled keys are documented in Section 1.
* **Criterion 2 (Living Document Shard Resolution)**: *Given* a compliant ticket whose requirement resides in a domain shard, *When* `qa_audit` evaluates Living Documentation Parity, *Then* the requirement ID is resolved against `docs/requirements/req_*.md` shards and passes.
* **Criterion 3 (Issue Description Ingestion)**: *Given* a ticket with requirement anchors located in its Jira description, *When* `fetch_all_sprint_issues` runs, *Then* `description` is fetched and evaluated without being dropped.
* **Criterion 4 (AccountId-Based Role Separation)**: *Given* subtasks reviewed by `agent2`, *When* `qa_audit` evaluates 4-Augen Role Separation, *Then* `accountId`s are matched against `ROLE_ACCOUNT_IDS` and pass.

---

## 4. Test Specification (TST-PRO-091)
* **TST-PRO-091.7 (Living Document Shard Resolution Test)**: Verify that `qa_audit` resolves requirement references across all living requirement shards (`docs/requirements/req_*.md`) and passes compliant tickets.
* **TST-PRO-091.8 (Issue Description Ingestion Test)**: Verify that `fetch_all_sprint_issues` requests and parses the `description` field for requirement references.
* **TST-PRO-091.9 (AccountId Role Separation Test)**: Verify that `qa_audit` evaluates comment author `accountId` against `ROLE_ACCOUNT_IDS`, correctly evaluating independent reviews.
* **TST-PRO-091.10 (Seeded Random Sampling Test)**: Verify that `qa_audit` uses seeded pseudo-random sampling, records the random seed and sampled keys in the markdown header, and reproduces identical samples when provided the same seed.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-091.7` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-091.6` | Planned |
| `TST-PRO-091.8` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-091.6` | Planned |
| `TST-PRO-091.9` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-091.6` | Planned |
| `TST-PRO-091.10` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-091.2` | Planned |
