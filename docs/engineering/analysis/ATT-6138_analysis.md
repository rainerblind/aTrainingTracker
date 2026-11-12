# Stage 1 Analysis: ATT-6138 - Generate release verification summary per tag

**Ticket**: [ATT-6138](https://rainerblind.atlassian.net/browse/ATT-6138)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `subagent-Lane-2-Agent---ATT-6138-self-aabf75cf`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
Under ASPICE SWE.6 BP5 (Remediation ID: A-11), release decisions must be substantiated by recorded verification evidence. Prior to this improvement:
* No release-level summary existed defining what was verified, with which result, and on which baseline commit.
* Evidence artifacts were fragmented across isolated systems: JUnit unit test reports / archives (ATT-6137), bidirectional requirement-to-test coverage data (ATT-6122), headless system integration simulation results (`system_integration_results.json`, ATT-6166), and on-device system verification protocol records (`sys_verification_record.md`, ATT-6167).
* Without an authoritative summary file per release tag (`docs/releases/<tag>/verification_summary.md`), auditors, release managers, and developers lack a single consolidated document identifying test pass rates, traceability coverage, and open deviations requiring human sign-off.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Tooling & Aggregation Gap**:
   - Previous tickets completed individual evidence producers (JUnit archival in CI via ATT-6137, `--coverage-json` via ATT-6122, SYS.4 integration runner via ATT-6166, SYS.5 protocol template via ATT-6167, and release skill gating via ATT-6169).
   - However, no automated aggregator existed to parse and consolidate these disparate evidence sources into a single structured Markdown summary report during release candidate preparation.
2. **Independent Hypothesis Verification (ATT-3534)**:
   - Verified that `tools/verify_requirement_governance.py --coverage-json` provides machine-readable coverage metrics for REQ<->TST and TST<->Code.
   - Verified that `tools/run_system_integration_tests.py` exports `docs/releases/<tag>/system_integration_results.json` conforming to the schema defined in `docs/system/sys_integration_tests.md`.
   - Verified that `docs/releases/<tag>/sys_verification_record.md` adheres to the markdown sign-off format in `docs/system/sys_verification_protocol.md`.
   - Verified that JUnit XML results from `app/build/test-results/testDebugUnitTest/` and archives in `build-evidence/junit-test-results-<tag>.zip` contain canonical test counts, failures, and execution timings.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Implement `tools/generate_release_verification_summary.py`:
     - CLI flags: `--tag <TAG>` (mandatory), `--commit <SHA>` (optional, default HEAD), `--out <PATH>` (optional, default `docs/releases/<tag>/verification_summary.md`), and optional threshold flags (`--min-req-tst-coverage`, `--min-tst-code-coverage`, `--min-bidirectional-coverage`).
     - Aggregates:
       * Unit & regression test metrics (total tests, passed, failed, skipped) and link to versioned JUnit archive.
       * Bidirectional requirement-to-test coverage matrix via `tools/verify_requirement_governance.py --coverage-json`.
       * SYS.4 System Integration Verification results from `docs/releases/<tag>/system_integration_results.json`.
       * SYS.5 System Verification Protocol sign-off status from `docs/releases/<tag>/sys_verification_record.md`.
       * Open deviations section explicitly enumerating any failing test, missing evidence file, or coverage below threshold under `Deviations Requiring Human Approval`.
     - Generates clean, authoritative Markdown at `docs/releases/<tag>/verification_summary.md`.
  2. Implement automated unit test suite `tools/test_generate_release_verification_summary.py` covering CLI execution, aggregation logic, deviation detection, and output formatting.
  3. Register living documentation entries `REQ-PRO-074` and `TST-PRO-074` in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md` with 100% master index parity.
  4. Update `.agents/skills/release-manager/SKILL.md` and `docs/releases/README.md` to reference `tools/generate_release_verification_summary.py`.
* **Out-of-Scope Non-Goals**:
  - Zero modifications to Android production runtime code in `app/src/main/`.
  - No changes to SQLite database schemas or hardware sensor drivers.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* Net-new requirement only (`REQ-PRO-074` / `TST-PRO-074`). No existing requirements modified.
* Remediation ID: A-11, addressing ASPICE SWE.6 BP5 (Summarize and communicate test results).

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-074` (`REQ-PRO-074.1`–`REQ-PRO-074.5`) and `TST-PRO-074` (`TST-PRO-074.1`–`TST-PRO-074.5`).
* **Affected Architecture Elements**: `tools/generate_release_verification_summary.py`, `tools/test_generate_release_verification_summary.py`, `.agents/skills/release-manager/SKILL.md`, `docs/releases/README.md`.
* **Verification Impact & Test Surface**: Unit tests in `tools/test_generate_release_verification_summary.py` and governance validation via `python3 tools/verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: Low risk (tooling and documentation). Production runtime behavior in `app/src/main/` is completely unaffected.

---

## 6. Architectural Strategy & High-Level Solution
The generator script will be implemented in Python 3 with modular data collection routines:
1. `parse_args()`: Validates CLI parameters (`--tag`, `--commit`, `--out`, `--repo-root`, etc.).
2. `collect_unit_test_summary(repo_root, tag)`: Scans `app/build/test-results/testDebugUnitTest/` XML files or `build-evidence/junit-test-results-<tag>.zip` and extracts test counts, failures, and execution times.
3. `collect_coverage_summary(repo_root, min_req_tst, min_tst_code, min_bidi)`: Invokes or imports `verify_requirement_governance.py --coverage-json` and extracts 4-category coverage metrics.
4. `collect_sys4_summary(release_dir)`: Reads `system_integration_results.json` and evaluates interface simulation test cases (`SYS-INT-001`..`006`).
5. `collect_sys5_summary(release_dir)`: Parses `sys_verification_record.md` and checks overall verdict, test case matrix (`SYS-VRF-001`..`008`), and QA sign-off status.
6. `compile_deviations(...)`: Aggregates any test failures, missing evidence, or threshold gaps into an explicit human approval section.
7. `render_markdown(...)`: Formats a clean, publication-ready verification summary.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero modifications to Android production runtime code in `app/src/main/`.
  2. Pure standard library Python 3 implementation with zero external package dependencies.
  3. Script executes idempotently and outputs well-structured Markdown.
* **Risk Rating**: **LOW** (Tooling, process governance, and living docs).
