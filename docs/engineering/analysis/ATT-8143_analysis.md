# Stage 1 Analysis: ATT-8143 - Derive TST and REQ Verified status from machine-readable test results

**Ticket**: [ATT-8143](https://rainerblind.atlassian.net/browse/ATT-8143)  
**Sub-task**: [ATT-8274](https://rainerblind.atlassian.net/browse/ATT-8274) (`[Analysis]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Branch**: `feature/ATT-8143` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16  

---

## 1. Problem Statement & Motivation
Under ASPICE 4.0 Major Finding `M1` (SWE.4 BP4/BP5, SWE.6 BP4/BP5, SWE.1 BP5), `[Verified]` status is currently flipped unconditionally in living docs (`docs/requirements.md`, `docs/tests.md`, and shards) upon Stage 5 submission via `auto_verify_living_docs_on_stage5()` in `tools/jira_util.py`, completely decoupled from test execution results. 405 of 1156 test specifications (35%) have no test code tags, yet 1154/1156 are marked `[Verified]`. Furthermore, when tests fail, items remain falsely `[Verified]`. Verification status must be derived from authentic, machine-readable test execution results (JUnit XML / test runner outputs and attested manual protocols).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Origin**: `auto_verify_living_docs_on_stage5()` was introduced in `ATT-4465` (`71fc989d`) and expanded in `ATT-4594` (`06990c88`) and `ATT-5431` (`a3f5bdb7`). It performs regex replacement of status columns on `submit-stage 5` without inspecting test results.
* **Verification Decoupling**: `tools/verify_requirement_governance.py` (`verify_requirement_evidence_status`) only checks whether a `[Verified]` requirement cites a known `TST-XXX` heading. It does not check whether that `TST` has test code or passed.
* **Independent Peer Review & Verification**:
  * Audited `tools/jira_util.py:1099-1197`: Confirmed `auto_verify_living_docs_on_stage5` performs string replacement without verifying execution.
  * Audited `tools/verify_requirement_governance.py:2159-2315`: `extract_test_code_tags` scans only `app/src/test/` for regex matches, ignoring test outcomes; `Category 2` reports 405 unbacked TSTs (64.97% coverage).
  * Confirmed: When a test fails in Gradle/JUnit XML, `verify_requirement_governance.py` does not detect the contradiction.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Parse JUnit XML results (`app/build/test-results/**/TEST-*.xml`) and map test classes/methods to `TST-XXX` IDs.
  2. Implement `verify_requirement_governance.py --check-verified-evidence`: fail if a `[Verified]` TST has failing tests (AC 1).
  3. Add a ratchet baseline for legacy unbacked TSTs to preserve existing builds while rejecting new unbacked items (AC 2).
  4. Update `auto_verify_living_docs_on_stage5()` in `tools/jira_util.py` (and Höchstmaß mirror) to check test evidence before flipping.
  5. Update `.agents/skills/stage5-verification/SKILL.md` and add comprehensive unit tests in `test_verify_requirement_governance.py`.
* **Out-of-Scope Non-Goals**: Modifying Android runtime code; backfilling test implementations for all 405 legacy unbacked TSTs.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-073`, `REQ-PRO-071`, and net-new `REQ-PRO-099` / `TST-PRO-099`.
* **Historical Origin & Commit Trace**: `ATT-4465` (`71fc989d`) added Stage 5 auto-flip; `ATT-6137` (`bd01fb3f`) added JUnit XML archiving.
* **Root Reason for Existing Formulation**: Stage 5 auto-verification eliminated manual markdown status editing.
* **Preservation of Core Invariants**: Retains automated status synchronization upon Stage 5 submission, but conditions `[Verified]` on passing test evidence; ratchet baseline protects existing repository builds.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Formulate net-new `REQ-PRO-099` (`REQ-PRO-099.1`, `REQ-PRO-099.2`) in `docs/requirements/req_process_stability.md`.
* **Affected Architecture Elements**: `tools/verify_requirement_governance.py`, `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `.agents/skills/stage5-verification/SKILL.md`, `docs/tests.md`.
* **Verification Impact & Test Surface**: New test spec `TST-PRO-099` (`TST-PRO-099.1`..`TST-PRO-099.3`) in `docs/tests/tst_process_stability.md`; unit tests in `tools/test_verify_requirement_governance.py` and `tools/test_jira_accounts.py`.
* **Risk Assessment & Invariants**: Medium risk (3 SP). Developer tooling and governance scripts; zero Android runtime bytecode changes in `app/src/main/`.

---

## 6. Architectural Strategy & High-Level Solution
* **JUnit XML Parser**: Ingest `TEST-*.xml` suites, extracting test outcomes (`PASS`, `FAIL`, `SKIPPED`) per class/method.
* **Tag Mapping & Evidence Verification**: Correlate testcases to `TST-XXX` IDs via method names and code tags. Reject `[Verified]` status for any TST whose test failed (AC 1) or any new TST lacking passing test evidence (AC 2).
* **Ratchet Baseline**: Allow legacy unbacked TSTs present in `LEGACY_UNBACKED_VERIFIED_TSTS` baseline; fail if orphan count grows.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Zero regressions across existing passing unit tests; zero Android production code changes; Rule 3 Human Gate intact.
* **Risk Rating**: **MEDIUM** (3 SP — Core governance verification logic affecting living documentation and CI checks).
