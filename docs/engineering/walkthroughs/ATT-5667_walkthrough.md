# Stage 5 Walkthrough: ATT-5667 - Parameterized Tooling & Jira CLI

**Ticket**: [ATT-5667](https://rainerblind.atlassian.net/browse/ATT-5667)  
**Sub-task**: `[Test]`  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5667`  
**Requirement Mapping**: `REQ-PRO-036` (`REQ-PRO-036.1`–`REQ-PRO-036.4`)  
**Test Mapping**: `TST-PRO-036` (`TST-PRO-036.1`–`TST-PRO-036.4`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Executive Summary & Verification Overview
ATT-5667 (Part 2/3) parameterizes and packages the Agile Agentic ASPICE (AAA) CLI and governance automation engine into `tools/hoechstmass/tools/`. It introduces declarative configuration (`hoechstmass_config.json` and `.env.jira.example`), parameterizes `jira_util.py` and `verify_requirement_governance.py`, and validates execution contracts via Python unit tests and a JUnit contract test with zero hardcoded `ATT` constants.

---

## 2. Acceptance Criteria Verification Matrix

| AC # | Description | Evidence / Verification Method | Result |
| :--- | :--- | :--- | :---: |
| **AC-1** | Declarative configuration schema and multi-account env template | `tools/hoechstmass/hoechstmass_config.json`, `.env.jira.example` | **PASSED** |
| **AC-2** | Parameterized Jira CLI automation client without hardcoded `ATT` | `tools/hoechstmass/tools/jira_util.py` | **PASSED** |
| **AC-3** | Parameterized requirement governance verification tool | `tools/hoechstmass/tools/verify_requirement_governance.py` | **PASSED** |
| **AC-4** | Python automated tooling unit test suite (7 tests) | `python3 -m unittest discover -s tools/hoechstmass/tools -p "test_*.py"` | **PASSED** |
| **AC-5** | Automated contract test & governance verification | `HoechstmassToolingContractTest.kt` (4 tests) & `verify_requirement_governance.py` | **PASSED** |

---

## 3. Requirement & Test Verification Matrix

| Requirement ID | Description | Test Case ID | Scope & Method | Result | Living Doc Status |
| :--- | :--- | :--- | :--- | :---: | :---: |
| `REQ-PRO-036.1` | Declarative configuration schema and multi-account template | `TST-PRO-036.1` | `HoechstmassToolingContractTest.testDeclarativeConfigurationSchema_existsAndIsValidJson` | **PASSED** | `Verified` |
| `REQ-PRO-036.2` | Parameterized Jira CLI automation client | `TST-PRO-036.2` | `HoechstmassToolingContractTest.testParameterizedTooling_existsAndIsDecoupled` | **PASSED** | `Verified` |
| `REQ-PRO-036.3` | Parameterized requirement governance verification tool | `TST-PRO-036.3` | `test_hoechstmass_tools.py` (7 tests, standard library unittest) | **PASSED** | `Verified` |
| `REQ-PRO-036.4` | Automated tooling contract test & governance verification | `TST-PRO-036.4` | Gradle `testDebugUnitTest` & `verify_requirement_governance.py` | **PASSED** | `Verified` |

---

## 4. Test Evidence & Scope Declaration
* **Executed Test Scope**: Python tooling unit tests and Tier 1 targeted build suite (`--tests "com.atrainingtracker.trainingtracker.build.*"`):
  ```text
  Ran 7 tests in 0.005s -> OK (PYTHONDONTWRITEBYTECODE=1)
  BUILD SUCCESSFUL in 1s (32 actionable tasks: 1 from cache, 31 up-to-date)
  HoechstmassToolingContractTest > 4 tests PASSED (100% pass rate)
  ```
* **Governance Verification**:
  ```text
  PASS: Living documentation master tables match all 9 domain shards with 100% parity.
  Net-new requirement(s) detected: REQ-PRO-036. Bypassing archaeology check cleanly.
  ```
* **Runtime Code Invariant**: Zero lines modified in `app/src/main/`. All changes isolated to `tools/hoechstmass/`, `app/src/test/`, and living documentation.
