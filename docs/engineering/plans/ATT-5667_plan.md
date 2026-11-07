# Stage 3: Implementation Plan - ATT-5667: Parameterized Tooling & Jira CLI

**Ticket**: [ATT-5667](https://rainerblind.atlassian.net/browse/ATT-5667)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5667`  
**Requirement Mapping**: `REQ-PRO-036` (`REQ-PRO-036.1`–`REQ-PRO-036.4`)  
**Test Mapping**: `TST-PRO-036` (`TST-PRO-036.1`–`TST-PRO-036.4`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Description & Scope
ATT-5667 executes Part 2 of the 3-part Höchstmaß series. It extracts, parameterizes, and tests the core automation tooling under `tools/hoechstmass/tools/` using a declarative configuration schema (`hoechstmass_config.json` and `.env.jira.example`). It ensures any engineering project can run the 5-stage AAA workflow without hardcoded `ATT` constants, validated by Python unit tests and a JUnit contract test.

---

## 2. System Invariants & Preserved Behavior
1. **Zero Runtime App Code Edits**: `app/src/main/` and production Android code remain 100% untouched.
2. **Active Project Isolation**: Existing root scripts `tools/jira_util.py` and `tools/verify_requirement_governance.py` remain unmodified.
3. **Pure Standard Library**: `tools/hoechstmass/tools/` scripts depend exclusively on Python 3 standard library (no pip packages).
4. **Code Lock (Rule 4)**: No files in `tools/hoechstmass/tools/` or `app/src/test/...` are created until Gate 3 PASS (`python3 tools/jira_util.py check-gate <KEY>`).
5. **Parent Human Gate (Rule 3)**: Parent ticket completion is reserved exclusively for the human user in `Final Review (Human)`.

---

## 3. Proposed Architectural Layout (`tools/hoechstmass/tools/`)
* **Configuration Schema**:
  - `tools/hoechstmass/tools/hoechstmass_config.json`: Declarative JSON specifying `project_key`, `board_id`, `transitions`, `story_points_field`, `roles`, `prefixes`, and `paths`.
  - `tools/hoechstmass/tools/.env.jira.example`: Environment variable template for role-segregated credentials.
* **Parameterized Automation CLI**:
  - `tools/hoechstmass/tools/jira_util.py`: Parameterized Jira automation client reading from `hoechstmass_config.json` and `.env.jira`, providing composite commands (`submit-stage`, `gate-verdict`, `check-gate`, `ensure-retro`), secret masking, and context injection.
* **Parameterized Governance Checker**:
  - `tools/hoechstmass/tools/verify_requirement_governance.py`: Dynamic Chesterton's Fence archaeology validator, engineering directory check, and living doc master table counter parity verifier.
* **Automated Verification**:
  - `tools/hoechstmass/tools/test_hoechstmass_tools.py`: Python unit test suite executing with `python3` (config loading, role resolution, sanitization, governance checking).
  - `app/src/test/java/com/atrainingtracker/trainingtracker/build/HoechstmassToolingContractTest.kt`: JUnit 4 contract test verifying file presence, execution contracts, and zero hardcoded `ATT` strings.
* **UI Consistency**: No app UI changes (command-line tooling and verification export).

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Configuration Schema**: Create `tools/hoechstmass/tools/hoechstmass_config.json` and `.env.jira.example` with clean parameterization schemas.
2. **Step 2: Parameterized `jira_util.py`**: Create `tools/hoechstmass/tools/jira_util.py` implementing dynamic config loading, multi-account auth, composite stage transitions, secret masking, and context injection with zero hardcoded `ATT` references.
3. **Step 3: Parameterized `verify_requirement_governance.py`**: Create `tools/hoechstmass/tools/verify_requirement_governance.py` parameterized to read prefixes, paths, and master index tables dynamically.
4. **Step 4: Python Automated Test Suite**: Author `tools/hoechstmass/tools/test_hoechstmass_tools.py` covering config loading, role resolution, secret masking, and governance verification.
5. **Step 5: JUnit Contract Test**: Implement `HoechstmassToolingContractTest.kt` in `app/src/test/.../build/` asserting presence, execution contracts, and zero hardcoded `ATT` strings.
6. **Step 6: Targeted Verification**: Execute `python3 tools/hoechstmass/tools/test_hoechstmass_tools.py`, `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.HoechstmassToolingContractTest"`, and `python3 tools/verify_requirement_governance.py`.

---

## 5. Verification & Rollback Plan
* **Verification**: Execute Python unit test suite, targeted JUnit contract test, and full build package tests (`--tests "com.atrainingtracker.trainingtracker.build.*"`), then confirm governance script exits 0.
* **Rollback**: Branch isolation (`feature/ATT-5667`) guarantees clean rollback without impacting `develop` or `sprint/2026-45.9`.
