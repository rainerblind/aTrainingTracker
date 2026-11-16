# Stage 3: Implementation Plan - ATT-8144: Versioned Test & Coverage Evidence in Git

**Ticket**: [ATT-8144](https://rainerblind.atlassian.net/browse/ATT-8144) | **Sub-task**: [[Impl-Plan]](https://rainerblind.atlassian.net/browse/ATT-8144)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-46.8` | **Branch**: `feature/ATT-8144`  
**Requirement Mapping**: `REQ-PRO-100` (`REQ-PRO-100.1`..`REQ-PRO-100.4`), `REQ-PRO-067.3`  
**Test Mapping**: `TST-PRO-100` (`TST-PRO-100.1`..`TST-PRO-100.3`), `TST-PRO-067.3`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16  

---

## 1. Problem Description & Background
Under ASPICE 4.0 Major Finding `M7` (SWE.4 BP5, SWE.5 BP7, SWE.6 BP5, GP 2.2.3), `docs/engineering/test_results` is an ephemeral symlink to `app/build/reports/tests/testDebugUnitTest`, leaving verification unversioned and absent from Git configuration management (`SUP.8`). Kover coverage floors are enforced in Gradle, but measured values are never recorded. This plan retires the symlink, establishes canonical `docs/engineering/test_evidence/` for versioned `<sprint|release>.md` and `.json` artifacts, implements `tools/generate_test_evidence.py` (ingesting JUnit XMLs and Kover XML), adds `jira_util.py export-test-evidence` and `verify_requirement_governance.py --check-test-evidence`, updates `ReleaseCiAndLintConfigContractTest.kt`, and aligns `cm_plan.md`, `aspice_governance.md`, and ceremony skills.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-PRO-100` (*Versioned Test & Coverage Evidence Archival*) & `REQ-PRO-067.3` (*Build Symlink Retirement*)
* **Test Mapping**: `TST-PRO-100.1`..`TST-PRO-100.3`, `TST-PRO-067.3` in `docs/tests/tst_process_stability.md`

---

## 3. System Invariants & Preserved Behavior
1. **Zero Android Production Regressions**: Developer tooling and QA evidence integrity; `app/src/main/` 100% untouched.
2. **Clean-Worktree Safety**: Default `verify_requirement_governance.py` passes without requiring pre-existing build outputs.
3. **Graceful Coverage Ingestion**: If Kover XML is absent (e.g. only `testDebugUnitTest` ran), generator logs a notice, records coverage as not measured, and exits cleanly without failure.
4. **Human Decision Gate**: Rule 3 Human Gate remains strictly intact.

---

## 4. Proposed Architectural Changes
* **Component 1 (`docs/engineering/test_evidence/`)**: Retire `docs/engineering/test_results` symlink; create `docs/engineering/test_evidence/` with `README.md` documenting format and naming conventions.
* **Component 2 (`tools/generate_test_evidence.py`)**: CLI supporting `--sprint <id>` and `--release <tag>`. Ingests JUnit XMLs via `parse_junit_xml_results` (ATT-8143), parses Kover XML (`reportDebug.xml`) for global and package metrics (Tracking, Filters, Sensors), correlates to `TST-XXX` IDs, and renders markdown and companion `.json`.
* **Component 3 (`tools/jira_util.py` & Höchstmaß mirror)**: Add `export-test-evidence [--sprint <ID>] [--release <TAG>]` calling generator.
* **Component 4 (`tools/verify_requirement_governance.py` & mirror)**: Add `test_evidence` to `ALLOWED_ENGINEERING_ENTRIES`, remove `test_results`, and add `--check-test-evidence` validation mode.
* **Component 5 (`ReleaseCiAndLintConfigContractTest.kt` & Governance Docs)**: Update contract test asserting symlink removal and `test_evidence/` presence; update `cm_plan.md`, Rule 8.4, and skills.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Risk Classification**: **STANDARD (Process & Developer Tooling Risk, Zero Runtime Bytecode Risk)**.
* **Step 1: Symlink Removal & Evidence Store**: Delete `docs/engineering/test_results` symlink; create `docs/engineering/test_evidence/` with `README.md`.
* **Step 2: Test Evidence Generator**: Implement `tools/generate_test_evidence.py` (JUnit XML + Kover XML parsing, TST correlation, JSON/MD output).
* **Step 3: Tooling Integration**: Add `export-test-evidence` command to `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`.
* **Step 4: Governance Validation**: Update `verify_engineering_directory_structure()` and implement `--check-test-evidence` in `tools/verify_requirement_governance.py`.
* **Step 5: Contract Test & Governance Docs**: Update `ReleaseCiAndLintConfigContractTest.kt`, `cm_plan.md`, `aspice_governance.md`, `sprint-planner/SKILL.md`, and `release-manager/SKILL.md`.
* **Step 6: Unit Tests & Verification**: Author test suites in `tools/test_generate_test_evidence.py` and `tools/test_verify_requirement_governance.py` with explicit `TST-PRO-100` and `TST-PRO-067.3` tags for ATT-8143 gating.
* **Tier 1 Test Command**:
  ```bash
  python3 -m unittest tools/test_generate_test_evidence.py tools/test_verify_requirement_governance.py tools/test_jira_accounts.py tools/hoechstmass/tools/test_hoechstmass_tools.py && python3 tools/verify_requirement_governance.py --check-test-evidence && ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Execute Tier 1 test command (Python test suites + Gradle contract test), validating mock sprint and release evidence files.
* **Rollback**: Branch isolation (`feature/ATT-8144`) allows complete revert without impact on `develop` or sprint branch.
