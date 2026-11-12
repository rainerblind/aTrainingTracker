# Stage 3: Implementation Plan - ATT-6169: Integrate system verification evidence into release process

**Ticket**: [ATT-6169](https://rainerblind.atlassian.net/browse/ATT-6169)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.4` | **Branch**: `feature/ATT-6169`  
**Requirement Mapping**: `REQ-PRO-072` (*Release Manager System Verification Evidence Integration & Release Gating Governance*)  
**Test Spec ID**: `TST-PRO-072`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
In the ASPICE 4.0 pre-assessment of 2026-11-08, finding S-08 (ASPICE SYS.5 Base Practices, SUP.8 Configuration & Release Management) identified that release decisions and release checklists in `.agents/skills/release-manager/SKILL.md` (and Jira release tickets) did not reference system-level evidence. Specifically, neither SYS.4 virtual system integration results (`docs/releases/<tag>/system_integration_results.json`) nor SYS.5 on-device system verification records (`docs/releases/<tag>/sys_verification_record.md`) were checked before tagging a release candidate. To achieve ASPICE CL2 compliance, release decisions must require verified system integration and system verification evidence, explicitly blocking release candidate tag creation if records are missing or failing.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-072` (*Release Manager System Verification Evidence Integration & Release Gating Governance*)
* **Test Mapping**: `TST-PRO-072` (*Release Manager System Verification Evidence Integration & Release Gating Test Suite*)
* **Traceability Matrix**:
  - `REQ-PRO-072.1` -> `TST-PRO-072.1` (`.agents/skills/release-manager/SKILL.md` mandatory system evidence gates)
  - `REQ-PRO-072.2` -> `TST-PRO-072.2` (`.agents/skills/release-manager/SKILL.md` blocking tag creation on missing/failing evidence)
  - `REQ-PRO-072.3` -> `TST-PRO-072.3` (`docs/releases/README.md`, `docs/releases/TEMPLATE.md` release dossier layout & sign-off criteria)
  - `REQ-PRO-072.4` -> `TST-PRO-072.4` (`ReleaseManagerSystemEvidenceContractTest.kt`, `tools/verify_requirement_governance.py`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Code Modification**: No changes to production code in `app/src/main/` or database schemas.
2. **Audit Rule Compliance (ATT-6186)**: Contract tests MUST NOT read production source files in `app/src/main/`.
3. **Release Gating Invariant**: Release candidate tagging and Play Store distribution are strictly blocked without passing SYS.4 and SYS.5 evidence.
4. **GitFlow & ATT-170 Workflow Parity**: The canonical sequential steps are preserved while embedding mandatory system verification gates.

---

## 4. Proposed Architectural Changes
* **Release Manager Skill Updates (`.agents/skills/release-manager/SKILL.md`)**:
  - Incorporate mandatory System Verification Evidence Gate in Overview, Checklist, and Phase 1/Phase 2 detailed procedures.
  - Define explicit blocking rule: Creating release candidate tags (`V<versionName>__<versionCode>`) is strictly blocked if `docs/releases/<tag>/system_integration_results.json` or `docs/releases/<tag>/sys_verification_record.md` is missing, incomplete, or failing.
  - Add execution instructions for `tools/run_system_integration_tests.py` and protocol record compilation.
* **Standardized Release Evidence Directory & Templates (`docs/releases/`)**:
  - Create `docs/releases/README.md` defining directory layout `docs/releases/<tag>/`, artifact schemas, and SYS.4/SYS.5 release gates.
  - Create `docs/releases/TEMPLATE.md` providing a reusable release dossier template referencing SYS.4 and SYS.5 sign-off criteria.
* **Continuous SWE.2 Architecture Maintenance (`docs/architecture.md`)**:
  - Update Section 6 to document Section 6.4 (*Release Process Gating & System Verification Evidence*).
* **JUnit Contract Test (`ReleaseManagerSystemEvidenceContractTest.kt`)**:
  - Implement contract assertions in `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseManagerSystemEvidenceContractTest.kt` validating skill gates, blocking rules, and release template structure.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Create `docs/releases/README.md` and `docs/releases/TEMPLATE.md` (REQ-PRO-072.3)
* Author documentation and template establishing `docs/releases/<tag>/` layout and SYS.4 / SYS.5 sign-off criteria.

### Step 2: Update `.agents/skills/release-manager/SKILL.md` (REQ-PRO-072.1, REQ-PRO-072.2)
* Embed mandatory system verification evidence gates and explicit tag-blocking rules into the release procedure and checklist.

### Step 3: Update `docs/architecture.md` Section 6.4 (ATT-5431 / SYS.5 / SUP.8)
* Document Section 6.4 linking the release process to SYS.4 and SYS.5 system verification evidence.

### Step 4: Implement `ReleaseManagerSystemEvidenceContractTest.kt` (TST-PRO-072.1–TST-PRO-072.4)
* Author JUnit test class validating release-manager skill requirements, tag-blocking rules, and template structures.

### Step 5: Execute Targeted Verification & Governance Parity
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseManagerSystemEvidenceContractTest"` under sprint lock.
* Run `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Local Verification**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseManagerSystemEvidenceContractTest"` + `python3 tools/verify_requirement_governance.py`.
* **Rollback Plan**: Git feature branch isolation ensures clean revert if required.
