# Stage 1: Problem Domain & Root Cause Analysis - ATT-5667

**Ticket**: [ATT-5667](https://rainerblind.atlassian.net/browse/ATT-5667)  
**Parent Epic**: Standalone / Framework Initiative | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5667`  
**Requirement Mapping**: `REQ-PRO-036` (`REQ-PRO-036.1`–`REQ-PRO-036.4`)  
**Test Mapping**: `TST-PRO-036` (`TST-PRO-036.1`–`TST-PRO-036.4`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Domain & Background
Part 1 of the Höchstmaß series (`ATT-3922`) established the core framework documentation, mathematical defect reduction model, and starter templates under `tools/hoechstmass/`. However, the operational CLI and governance tooling (`tools/jira_util.py` and `tools/verify_requirement_governance.py`) remains tightly coupled to *aTrainingTracker* constants: hardcoded `"ATT"` project key, specific Jira transition IDs (`11`, `21`, `31`, `41`), developer account IDs, `"customfield_10016"`, and hardcoded fitness domain shards.

ATT-5667 (Part 2 of 3) extracts, parameterizes, and hardens this automation tooling into `tools/hoechstmass/tools/` so any software engineering team can adopt the Agile Agentic ASPICE (AAA) workflow via declarative configuration (`hoechstmass_config.json` and `.env.jira.example`).

---

## 2. Chesterton's Fence & Invariant Archaeology
* `Original Requirement ID & Target`: Net-new requirement `REQ-PRO-036` (building on `REQ-PRO-019`, `REQ-PRO-020`, `REQ-PRO-021`, `REQ-PRO-022`, `REQ-PRO-034`, and `REQ-PRO-035`). Targets `tools/hoechstmass/tools/` and `app/src/test/.../build/HoechstmassToolingContractTest.kt`.
* `Historical Origin & Commit Trace`: Evolved from `ATT-103` (initial CLI), `ATT-4290` (composite stage commands), `ATT-4520` (multi-account authentication), `ATT-5431` (SWE.2 living doc recalculation), and `ATT-3922` (Part 1 core framework).
* `Root Reason for Existing Formulation`: Tooling in repository root `tools/` was built directly for *aTrainingTracker*. Hardcoding project constants allowed rapid sprint execution but prevents open-source, project-agnostic adoption.
* `Preservation of Core Invariants`: Zero changes to existing production code in `app/src/main/` or active tooling in `tools/`; active `.agents/` and `docs/` remain untouched; Human Decision Gate on parent tickets remains inviolable; clean-room test suite passes 100%.

---

## 3. Scope Bounding & Technical Architecture
1. **Declarative Configuration Schema (`tools/hoechstmass/tools/`)**:
   - `hoechstmass_config.json`: Project key, board ID, transition name-to-ID mappings, custom field IDs (story points, sprint), role account ID mappings (`agent1`, `agent2`, `coordinator`, `human`), requirement prefix list, and canonical engineering paths.
   - `.env.jira.example`: Environment variable template for multi-account credentials (`JIRA_URL`, `JIRA_AGENT1_*`, `JIRA_AGENT2_*`, `JIRA_COORDINATOR_*`, `JIRA_HUMAN_ACCOUNT_ID`).
2. **Generalized Jira CLI Tool (`tools/hoechstmass/tools/jira_util.py`)**:
   - Reads project key, transitions, and role mappings dynamically from `hoechstmass_config.json` and `.env.jira`.
   - Preserves all composite stage transitions (`submit-stage`, `gate-verdict`, `check-gate`, `ensure-retro`), role segregation, token secret masking, and automatic context injection without hardcoded `ATT` strings.
3. **Generalized Requirement Governance Tool (`tools/hoechstmass/tools/verify_requirement_governance.py`)**:
   - Reads requirement/test prefixes and canonical directory paths from `hoechstmass_config.json`.
   - Validates Chesterton's Fence 4-field audit, canonical `docs/engineering/` directory structure, and living documentation summary table parity.
4. **Automated Verification Suite**:
   - Python unit test suite `tools/hoechstmass/tools/test_hoechstmass_tools.py` verifying config loading, role resolution, secret masking, and governance verification logic.
   - JUnit contract test `HoechstmassToolingContractTest.kt` (`TST-PRO-036`) in `app/src/test/.../build/` verifying file presence, execution contracts, and zero hardcoded `ATT` references.

---

## 4. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Complete decoupling: `tools/hoechstmass/tools/` must not contain hardcoded `ATT` project keys or proprietary account IDs.
  2. Active project isolation: Active root tools (`tools/jira_util.py`, `tools/verify_requirement_governance.py`) remain unmodified.
  3. Pure Python 3 standard library: Zero external pip dependencies.
* **Risk Rating**: **LOW** (Exported automation tools and contract tests; zero risk to active Android runtime code).
