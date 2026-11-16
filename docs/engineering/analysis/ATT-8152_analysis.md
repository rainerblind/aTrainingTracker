# Stage 1 Analysis: ATT-8152 - Fix qa-audit tool criteria to use requirement shards, fetched descriptions and random sampling

**Ticket**: [ATT-8152](https://rainerblind.atlassian.net/browse/ATT-8152)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_02_Spät`  
**Branch**: `feature/ATT-8152` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-16  

---

## 1. Problem Statement & Motivation
Under ASPICE 4.0 Audit Finding `M5` (SUP.1 BP2, BP4, GP 2.2), all 4 historical QA audits (sprints 46.3–46.6) reported 0% compliance ("NON-COMPLIANT") due to four systemic evaluation defects in `tools/jira_util.py qa_audit`:
1. **Living Documentation Shard Disparity**: The check compared requirement references strictly against `docs/requirements.md` (which only contains summary tables and 2 sample IDs) rather than the 9 domain shards (`docs/requirements/req_*.md`), causing 100% false-positive parity failures.
2. **Missing `description` Field in Query**: `fetch_all_sprint_issues` omitted `description` from the requested fields, causing `item.get("fields", {}).get("description")` to always be empty (`""`) and missing requirement references in ticket bodies.
3. **Flawed Role Separation String Matching**: The 4-Augen check matched author display-name substrings rather than comparing Atlassian `accountId`s against role mappings, producing false-positive role violation flags.
4. **Deterministic First-N Selection Instead of Random Sampling**: The audit sampled `pool[:sample_size]` instead of a representative random sample and omitted the random seed and sampled keys from the report header.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (`docs/requirements.md` vs Domain Shards)**: `tools/jira_util.py` (L4501–4516, L4604–4630) populated `master_req_ids` solely from `docs/requirements.md`. Because individual requirements reside in `docs/requirements/req_*.md`, `referenced_reqs.issubset(master_req_ids)` always failed.
* **RCA 2 (Omitted `description` Field in Query)**: In `tools/jira_util.py` (L4455), `fetch_all_sprint_issues` queried `summary,status,issuetype,priority,subtasks,comment,{STORY_POINTS_FIELD}` without `description`.
* **RCA 3 (Display-Name vs AccountId Role Matching)**: In `tools/jira_util.py` (L4586–4601), author validation matched display-name strings (`"agent1"`, `"reviewer"`), failing valid reviews.
* **RCA 4 (First-N Slicing)**: In `tools/jira_util.py` (L4496), `pool[:sample_size]` deterministically selected the first $N$ tickets without randomization or recorded seed.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Audited `docs/engineering/qa_audits/sprint_2026-46.6.md`: Confirmed all 5 sampled tickets had `FAIL` on Living Docs Parity and 4/5 had `FAIL` on Role Separation, yielding false 0% compliance.
  * Audited `tools/jira_util.py`: Verified all 4 defect call sites in lines 4455, 4496, 4501, 4586.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Update living doc check to inspect all domain shards in `docs/requirements/req_*.md` and `docs/tests/tst_*.md`.
  2. Include `description` in `fetch_all_sprint_issues` query parameters.
  3. Validate 4-Augen role separation using authentic Atlassian `accountId`s (`ROLE_ACCOUNT_IDS`).
  4. Implement seeded pseudo-random sampling (`random.Random(seed).sample`), recording seed and sampled keys in `docs/engineering/qa_audits/<sprint>.md`.
  5. Add unit tests for compliant and non-compliant ticket fixtures in `tools/test_jira_accounts.py`.
  6. Re-run and commit the corrected QA audit report for sprint `2026-46.6`.
* **Out-of-Scope Non-Goals**:
  - Changing the dynamic sample size formula ($\ge \max(2, \lceil 0.10 \times N \rceil)$) or altering Jira client architecture.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-PRO-091` (`REQ-PRO-091.2` sampling & `REQ-PRO-091.5` governance verification) and `REQ-PRO-105.3` (QA audit dimensions).
* **Historical Origin & Commit Trace**: `ATT-6154` (`REQ-PRO-061`), `ATT-7558` (`REQ-PRO-091`), `ATT-8030` (`REQ-PRO-105`), `ATT-8151` (`REQ-PRO-108`).
* **Root Reason for Existing Formulation**: Early QA audit scripts used basic heuristics and single-file master lookups before requirements were split into 9 domain shards.
* **Preservation of Core Invariants**: Dynamic sample size calculation $\ge \max(2, \lceil 0.10 \times N \rceil)$, pure Python stdlib offline resilience, and `ANT-Android-SDKs` submodule integrity (`160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`) are preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Amends `REQ-PRO-091` (`REQ-PRO-091.2`, `REQ-PRO-091.5`) and `REQ-PRO-105.3`; targets `TST-PRO-091`.
* **Affected Architecture Elements**: `tools/jira_util.py` (`qa_audit`, `format_qa_audit_markdown`), `tools/test_jira_accounts.py`, `docs/engineering/qa_audits/sprint_2026-46.6.md`.
* **Verification Impact & Test Surface**: Unit tests in `tools/test_jira_accounts.py`, `tools/verify_requirement_governance.py --check-qa-audits`.
* **Risk Assessment & Invariants**: **LOW** (2 SP bugfix in QA audit tooling; zero Android runtime production code changes).

---

## 6. Architectural Strategy & High-Level Solution
* **Shard Scanning**: In `qa_audit`, scan all files in `docs/requirements/req_*.md` to populate `master_req_ids` with all ~1,194 active living requirement IDs.
* **Query Description**: Add `description` to the `fields` argument in `fetch_all_sprint_issues`.
* **AccountId Matching**: Check comment `author.get("accountId")` against `ROLE_ACCOUNT_IDS["agent1"]` vs `ROLE_ACCOUNT_IDS["agent2"]` / `ROLE_ACCOUNT_IDS["qa"]`.
* **Seeded Random Sampling**: Use `random.Random(seed).sample(pool, sample_size)`, defaulting to a generated integer seed if not provided, and record seed and sampled keys in Section 1.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: (1) Dynamic sample size formula preserved; (2) Pure Python standard library with zero external dependencies; (3) Clean governance with zero code orphans; (4) `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
* **Risk Rating**: **LOW** (QA developer tooling bugfix with targeted unit tests).
