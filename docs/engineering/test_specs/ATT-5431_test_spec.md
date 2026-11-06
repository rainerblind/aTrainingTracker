# Stage 2: Requirement & Test Specification - ATT-5431: ASPICE Audit Hardening (SWE.1/2/4/6)

**Ticket**: [ATT-5431](https://rainerblind.atlassian.net/browse/ATT-5431)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5431`  
**Requirement Mapping**: `REQ-PRO-034` | **Test Spec ID**: `TST-PRO-034`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-05  

---

## 1. Requirement Specification (`REQ-PRO-034`)

### 1.1 Problem Statement & Rationale
An ASPICE v3.1/v4.0 audit revealed 4 vulnerabilities: outdated `Planned` status across 565 REQ and 647 TST headings despite completed code, an outdated 95-line `docs/architecture.md` lacking Compose/dynamic models, missing master counter auto-recalculation in `jira_util.py`, and omitted image/code-grounding rules in skills.

### 1.2 Functional & Architectural Requirements
1. **SWE.1 & SWE.4/6 Living Doc Parity (`REQ-PRO-034.1`)**: Harmonize all merged `REQ-*` and `TST-*` entries in the 9 domain shards to `[Verified]`; retain unmerged backlog items as `[Planned]`; synchronize master index counts in `docs/requirements.md` and `docs/tests.md`.
2. **ASPICE SWE.2 Modern Architecture (`REQ-PRO-034.2`)**: Re-author `docs/architecture.md` covering all active layers, allocating all 9 requirement domain shards and prefix families (`SWE.2.BP3`), specifying dynamic Mermaid sequence and state models (`SWE.2.BP4`), and documenting concurrency/DB migration invariants (`SWE.2.BP5`).
3. **Automated Master Counter Recalculation (`REQ-PRO-034.3`)**: Extend `auto_verify_living_docs_on_stage5` in `tools/jira_util.py` to recalculate master tables; add counter parity verification to `tools/verify_requirement_governance.py`.
4. **Universal Image Asset Inspection via `read_file` (`REQ-PRO-034.4`)**: Mandate visual inspection via `read_file` for all image assets (`docs/guide/img/`, walkthroughs, store presence, drawables) in `aspice_governance.md` and skills.
5. **Documentation Code-Grounding & Continuous SWE.2 Governance (`REQ-PRO-034.5`)**: Enforce verified codebase alignment for documentation and continuous `docs/architecture.md` updates when adding subsystems.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Status & Counter Parity)**: *Given* 9 living doc shards, *When* verified against git history, *Then* completed items show `[Verified]`, unmerged backlog items show `[Planned]`, and master tables match shard counts.
* **AC-2 (SWE.2 Architecture)**: *Given* `docs/architecture.md`, *When* evaluated against ASPICE SWE.2, *Then* static layers, 9-shard allocations, 3 Mermaid dynamic flows, and threading/DB invariants are fully specified.
* **AC-3 (Automation & Tooling)**: *Given* Stage 5 submission in `jira_util.py`, *When* executed, *Then* master table totals and status counts are auto-recalculated; `verify_requirement_governance.py` passes with exit code 0.
* **AC-4 (Universal Image & Skill Governance)**: *Given* modified skills and `aspice_governance.md`, *When* audited, *Then* universal `read_file` image inspection, code-grounding, and SWE.2 maintenance are explicitly enforced.

### 1.4 System Invariants
Zero modifications to production code in `app/src/main/`; existing test suite passes 100%; human decision gate on parent tickets strictly preserved.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Net-new `REQ-PRO-034` under Epic `ATT-232` (*Process*), consolidating `REQ-PRO-014`, `REQ-PRO-022`, `REQ-PRO-028`, and `REQ-ARC-001`.
* `Historical Origin & Commit Trace`: Sharding introduced in `ATT-4594`; Stage 5 status automation in `ATT-4290`.
* `Root Reason for Existing Formulation`: Sharding preserved original heading inconsistencies; `docs/architecture.md` lagged behind Compose migration; image audit was previously scoped narrowly to walkthroughs.
* `Preservation of Core Invariants`: Full backward compatibility of CLI tooling, zero runtime regression, and strict human gate preservation.

---

## 2. Test Specification (`TST-PRO-034`)

### Test Case 1: Shard Status & Master Table Counter Parity (`TST-PRO-034.1`)
* **Scope**: Shard heading and master table verification.
* **Target Files**: `docs/requirements/`, `docs/tests/`, `docs/requirements.md`, `docs/tests.md`.
* **Expected Result**: 100% heading format compliance; master tables match sum of parsed headings.

### Test Case 2: ASPICE SWE.2 Architecture & Dynamic Behavioral Models (`TST-PRO-034.2`)
* **Scope**: SWE.2 architectural specification audit.
* **Target File**: `docs/architecture.md`.
* **Expected Result**: Covers all layers, 9 domain allocations, 3 Mermaid diagrams (Tracking, Sensors, Aftermath), and threading/DB invariants.

### Test Case 3: Automated Stage-5 Master Counter Recalculation (`TST-PRO-034.3`)
* **Scope**: Tooling unit/integration test.
* **Target Files**: `tools/jira_util.py`, `tools/verify_requirement_governance.py`.
* **Expected Result**: `auto_verify_living_docs_on_stage5` recalculates tables; `verify_requirement_governance.py` validates counts.

### Test Case 4: Universal Image Inspection & Code Grounding Skill Audit (`TST-PRO-034.4`)
* **Scope**: Skill & governance guideline audit.
* **Target Files**: `.agents/rules/aspice_governance.md`, `.agents/skills/`.
* **Expected Result**: Explicit codification of universal `read_file`, code-grounding, and SWE.2 maintenance.

### Test Case 5: Clean-Room Governance & Regression (`TST-PRO-034.5`)
* **Command**: `python3 tools/verify_requirement_governance.py && ./gradlew testDebugUnitTest`
* **Expected Result**: Exit code 0 and 100% test pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Target | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PRO-034.1]` | Shard Audit | `docs/requirements/`, `docs/tests/` | `REQ-PRO-034.1` | Specified |
| `[TST-PRO-034.2]` | Architecture | `docs/architecture.md` | `REQ-PRO-034.2` | Specified |
| `[TST-PRO-034.3]` | Tooling | `jira_util.py`, `verify_requirement_governance.py` | `REQ-PRO-034.3` | Specified |
| `[TST-PRO-034.4]` | Governance | `aspice_governance.md`, `.agents/skills/` | `REQ-PRO-034.4`, `REQ-PRO-034.5` | Specified |
| `[TST-PRO-034.5]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-PRO-034` | Specified |
