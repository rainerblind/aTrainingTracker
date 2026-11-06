# Stage 3: Implementation Plan - ATT-5431: ASPICE Audit Hardening (SWE.1/2/4/6)

**Ticket**: [ATT-5431](https://rainerblind.atlassian.net/browse/ATT-5431)  
**Sub-task**: [ATT-5495](https://rainerblind.atlassian.net/browse/ATT-5495) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5431`  
**Requirement Mapping**: `REQ-PRO-034` (`REQ-PRO-034.1`–`5`)  
**Test Mapping**: `TST-PRO-034` (`TST-PRO-034.1`–`5`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-05  

---

## 1. Problem Description & Background
ASPICE audit identified 4 compliance vulnerabilities: drift across 565 REQ / 647 TST headings in living docs, outdated 95-line `docs/architecture.md`, missing master table auto-recalculation in `jira_util.py` and `verify_requirement_governance.py`, and gaps in skills regarding universal image inspection (`read_file`), documentation code-grounding, and continuous SWE.2 maintenance.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-PRO-034.1` (Shard parity), `REQ-PRO-034.2` (SWE.2 architecture), `REQ-PRO-034.3` (Tooling automation), `REQ-PRO-034.4` (Universal image audit), `REQ-PRO-034.5` (Doc code-grounding & SWE.2 governance).
* **Test Mapping**: `TST-PRO-034.1` (Shards & master tables), `TST-PRO-034.2` (SWE.2 audit), `TST-PRO-034.3` (Tooling contract), `TST-PRO-034.4` (Skills & rules audit), `TST-PRO-034.5` (Clean-room regression).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime App Impact**: Zero changes to Android source code in `app/src/main/` or existing unit test semantics.
2. **Backlog Integrity**: Unmerged backlog items (`REQ-FIL-007..010` / `ATT-131`) remain strictly `[Planned]`.
3. **Deterministic Governance**: `verify_requirement_governance.py` must pass with exit code 0.
4. **Code Lock (Rule 4)**: No changes committed until Gate 3 is approved. Parent Human Gate strictly preserved.

---

## 4. Proposed Architectural Changes
* **Living Documentation Shards (`docs/requirements/`, `docs/tests/`)**: Normalize non-standard heading brackets to `[Verified]`; synchronize master summary tables in `docs/requirements.md` and `docs/tests.md`.
* **ASPICE SWE.2 Architecture (`docs/architecture.md`)**: Rebuild as comprehensive architectural specification: 5 static layers, complete 9-shard REQ allocation (`SWE.2.BP3`), 3 Mermaid dynamic flows (Tracking, Sensor Failover, Post-Workout Pipeline; `SWE.2.BP4`), and threading/DB invariants (`SWE.2.BP5`).
* **Governance Tooling (`tools/jira_util.py`, `tools/verify_requirement_governance.py`)**: Automate master table recalculation in `auto_verify_living_docs_on_stage5`; add master table counter verification in `verify_requirement_governance.py`.
* **Skills & Core Rules (`aspice_governance.md`, `.agents/skills/`)**: Mandate universal `read_file` image inspection across all images, documentation code-grounding, and continuous SWE.2 maintenance.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Normalize Living Doc Shards & Master Tables (`REQ-PRO-034.1`, `TST-PRO-034.1`)
* Files: `docs/requirements/req_*.md`, `docs/tests/tst_*.md`, `docs/requirements.md`, `docs/tests.md`
* Actions: Normalize merged item headings to `[Verified]`, retain backlog items (`REQ-FIL-007..010`) as `[Planned]`, and synchronize master table rows and totals.

### Step 2: Modernize `docs/architecture.md` to ASPICE SWE.2 (`REQ-PRO-034.2`, `TST-PRO-034.2`)
* File: `docs/architecture.md`
* Actions: Author layers (Compose UI, ViewModels, Repositories/Engines, Services, DB), 9-shard REQ allocation, 3 Mermaid dynamic diagrams, and concurrency/DB invariants.

### Step 3: Extend `jira_util.py` & `verify_requirement_governance.py` (`REQ-PRO-034.3`, `TST-PRO-034.3`)
* Files: `tools/jira_util.py`, `tools/verify_requirement_governance.py`
* Actions: Add master index table recalculation to `auto_verify_living_docs_on_stage5`; add shard-to-master counter parity validation in `verify_requirement_governance.py`.

### Step 4: Codify Universal Image, Grounding & SWE.2 Rules in Skills (`REQ-PRO-034.4`, `REQ-PRO-034.5`, `TST-PRO-034.4`)
* Files: `.agents/rules/aspice_governance.md`, `.agents/skills/stage1-analysis/SKILL.md`, `.agents/skills/stage3-impl-plan/SKILL.md`, `.agents/skills/stage4-implementation/SKILL.md`, `.agents/skills/stage5-verification/SKILL.md`
* Actions: Embed universal `read_file` image audit, documentation code-grounding, and continuous SWE.2 maintenance obligations across all lifecycle stages.

### Step 5: Verification & Governance Validation (`TST-PRO-034.5`)
* Commands:
  ```bash
  python3 tools/verify_requirement_governance.py
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Run `verify_requirement_governance.py` and targeted build tests.
* **Rollback**: Clean git working tree revert (`git checkout -- .`) restores previous documentation state.
