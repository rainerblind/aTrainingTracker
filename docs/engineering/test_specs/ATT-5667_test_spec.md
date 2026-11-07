# Stage 2: Requirement & Test Specification - ATT-5667: Parameterized Tooling & Jira CLI

**Ticket**: [ATT-5667](https://rainerblind.atlassian.net/browse/ATT-5667)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5667`  
**Requirement Mapping**: `REQ-PRO-036` (`REQ-PRO-036.1`–`REQ-PRO-036.4`) | **Test Spec ID**: `TST-PRO-036` (`TST-PRO-036.1`–`TST-PRO-036.4`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Requirement Specification (`REQ-PRO-036`)

### 1.1 Problem Statement & Rationale
Part 1 of the Höchstmaß series (`ATT-3922`) established the core framework documentation and starter templates. However, the operational CLI and governance tooling (`tools/jira_util.py` and `tools/verify_requirement_governance.py`) remains coupled to *aTrainingTracker* constants: hardcoded `"ATT"` project key, Jira transition IDs, developer account IDs, and fitness domain shards. ATT-5667 (Part 2/3) parameterizes and packages this automation engine into `tools/hoechstmass/tools/` using a declarative configuration schema.

### 1.2 Functional & Technical Invariants
1. **Declarative Configuration Schema & Template (`REQ-PRO-036.1`)**:
   - `hoechstmass_config.json` SHALL define project key, board ID, transition name-to-ID mappings, custom field IDs (story points, sprint), role account ID mappings (`agent1`, `agent2`, `coordinator`, `human`), requirement prefixes, and canonical directory paths.
   - `.env.jira.example` SHALL define environment variable templates for multi-account credentials (`JIRA_URL`, `JIRA_AGENT1_*`, `JIRA_AGENT2_*`, `JIRA_COORDINATOR_*`, `JIRA_HUMAN_ACCOUNT_ID`).
2. **Parameterized Jira CLI Automation (`REQ-PRO-036.2`)**:
   - `tools/hoechstmass/tools/jira_util.py` SHALL read configuration dynamically from `hoechstmass_config.json` and `.env.jira`.
   - Provide composite stage commands (`submit-stage`, `gate-verdict`, `check-gate`, `ensure-retro`), role segregation, token secret masking, and automated context injection without hardcoded `ATT` strings.
3. **Parameterized Requirement Governance Tool (`REQ-PRO-036.3`)**:
   - `tools/hoechstmass/tools/verify_requirement_governance.py` SHALL validate Chesterton's Fence 4-field audit, canonical `docs/engineering/` directory structure, and living documentation master index counter parity dynamically using paths and prefixes from `hoechstmass_config.json`.
4. **Automated Tooling Unit & Contract Verification (`REQ-PRO-036.4`)**:
   - Python unit test suite `test_hoechstmass_tools.py` and JUnit contract test `HoechstmassToolingContractTest.kt` SHALL verify 100% execution contracts and zero hardcoded `ATT` strings. Zero runtime code modifications in `app/src/main/`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Configuration Schema)**: *Given* `tools/hoechstmass/tools/`, *When* inspected, *Then* `hoechstmass_config.json` and `.env.jira.example` define all project, transition, role, and path configurations.
* **AC-2 (Tooling Parameterization)**: *Given* `jira_util.py` and `verify_requirement_governance.py` in `tools/hoechstmass/tools/`, *When* inspected, *Then* neither tool contains hardcoded `ATT` project keys or proprietary account IDs.
* **AC-3 (Python Unit Tests)**: *Given* `test_hoechstmass_tools.py`, *When* executed with `python3`, *Then* all unit tests for config loading, role resolution, secret masking, and governance logic pass 100%.
* **AC-4 (Contract Test & Governance)**: *Given* `HoechstmassToolingContractTest`, *When* executed via Gradle, *Then* all tooling assertions pass 100% and governance checks exit 0.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Net-new `REQ-PRO-036` (building on `REQ-PRO-019`–`022`, `034`, `035`). Targets `tools/hoechstmass/tools/` and `HoechstmassToolingContractTest.kt`.
* `Historical Origin & Commit Trace`: Sprints 2026-39.1 (`ATT-103`), 2026-42.1 (`ATT-4290`), 2026-43.1 (`ATT-4520`), 2026-45.1 (`ATT-5431`), and 2026-45.9 (`ATT-5667`).
* `Root Reason for Existing Formulation`: Tooling in root `tools/` hardcoded project constants for *aTrainingTracker*, preventing open-source adoption.
* `Preservation of Core Invariants`: Active root tools in `tools/` and runtime code in `app/src/main/` remain untouched; clean-room test suite passes 100%.

---

## 2. Test Specification (`TST-PRO-036`)

### Test Case 1: Declarative Configuration Schema Audit (`TST-PRO-036.1`)
* **Target**: `HoechstmassToolingContractTest.kt`
* **Procedure**: Assert existence and non-emptiness of `hoechstmass_config.json` and `.env.jira.example`. Verify presence of keys `project_key`, `board_id`, `transitions`, `roles`, and `paths`.

### Test Case 2: Parameterized Tooling Source & Decoupling Contract (`TST-PRO-036.2`)
* **Target**: `HoechstmassToolingContractTest.kt`
* **Procedure**: Assert existence and non-emptiness of `tools/hoechstmass/tools/jira_util.py` and `verify_requirement_governance.py`. Assert neither tool contains hardcoded `ATT-` or proprietary account IDs.

### Test Case 3: Python Automated Tooling Unit Test Suite (`TST-PRO-036.3`)
* **Target**: `tools/hoechstmass/tools/test_hoechstmass_tools.py`
* **Procedure**: Execute `python3 tools/hoechstmass/tools/test_hoechstmass_tools.py` verifying config loading, role resolution, secret masking, and governance validation pass 100%.

### Test Case 4: Governance & Targeted Build Verification (`TST-PRO-036.4`)
* **Commands**: `python3 tools/verify_requirement_governance.py` && `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% test pass rate and governance verification exit 0.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-PRO-036.1` | Declarative configuration schema and multi-account env template | `TST-PRO-036.1` | `HoechstmassToolingContractTest.kt` |
| `REQ-PRO-036.2` | Parameterized Jira CLI automation tool | `TST-PRO-036.2` | `HoechstmassToolingContractTest.kt` & `test_hoechstmass_tools.py` |
| `REQ-PRO-036.3` | Parameterized requirement governance tool | `TST-PRO-036.3` | `HoechstmassToolingContractTest.kt` & `test_hoechstmass_tools.py` |
| `REQ-PRO-036.4` | Automated contract test & governance verification | `TST-PRO-036.4` | `./gradlew testDebugUnitTest` & `verify_requirement_governance.py` |
