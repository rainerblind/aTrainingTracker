# Stage 2: Requirement & Test Specification - ATT-6733: Fix master branch name, sprint tags, release contents, and CI inventory in cm_plan.md

**Ticket**: [ATT-6733](https://rainerblind.atlassian.net/browse/ATT-6733)  
**Sub-task**: [ATT-6798](https://rainerblind.atlassian.net/browse/ATT-6798) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-057` (*Configuration Management Repository Grounding (Master Production Branch, Sprint Milestone Tags, Release Contents & Controlled CI Inventory)*)  
**Test Spec ID**: `TST-PRO-057` (*Configuration Management Repository Grounding & cm_plan.md Parity Contract Test Suite*)  
**Branch**: `feature/ATT-6733`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PRO-057)

### 1.1 Problem Statement & Rationale
ASPICE SUP.8 (Finding B-07) requires absolute fidelity between the Configuration Management Plan (`docs/management/cm_plan.md`) and authentic repository configuration. Omission of the `master` production branch convention, sprint milestone tags, controlled documentation/tooling configuration items, and formal release deliverables violates SUP.8.BP1–BP3.

### 1.2 Functional & Architectural Requirements
1. **Production Branch Parity (`REQ-PRO-057.1`)**:
   - The CM plan SHALL document `master` (`origin/master`) as the production release baseline and GitFlow production branch, removing speculative references to `main`.
2. **Milestone and Release Tagging (`REQ-PRO-057.2`)**:
   - The CM plan SHALL document immutable sprint milestone tags (`sprint/<SPRINT_NAME>`) created at sprint close (Ceremony 2 Step 6) and dual release tags (`V<versionName>__<versionCode>` and `V<versionName>`).
3. **Controlled Configuration Items Inventory (`REQ-PRO-057.3`)**:
   - The CI table in `cm_plan.md` SHALL track all repository assets under formal configuration control, including `docs/architecture/*.md`, `docs/management/*.md`, `config/detekt/`, `tools/`, and `.agents/`.
4. **Formal Release Contents Specification (`REQ-PRO-057.4`)**:
   - The CM plan SHALL define the release deliverable package: signed APK, Play Store AAB, `docs/store_presence/` 9-language localized notes, and Jira `FixVersion` baseline traceability.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Branch & Tag Parity)**:
  * *Given* `docs/management/cm_plan.md`,
  * *When* audited for branch and tag naming,
  * *Then* the production release branch is `master` (zero occurrences of `main` as branch name) and `sprint/<SPRINT_NAME>` tags are documented.
* **Criterion 2 (CI Inventory & Release Contents)**:
  * *Given* Section 2 and Section 4 of `docs/management/cm_plan.md`,
  * *When* compared against repository assets,
  * *Then* architecture, management, config, tools, and skills are classified as CIs, and APK, AAB, localized store presence, and FixVersion baselines are defined.

### 1.4 System Invariants
Zero modifications to Android runtime code in `app/src/main/`; remote Git branch structure preserved; 100% test pass rate.

---

## 2. Test Specification (TST-PRO-057)

### Test Case 1: CM Plan Grounding & Release Contents Contract Test (`TST-PRO-057.1`, `ReleaseCiAndLintConfigContractTest.kt`)
* **Scope**: Automated Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
* **Assertions**:
  - Assert `docs/management/cm_plan.md` exists and contains `master` branch references.
  - Assert zero occurrences of `main` branch references in `cm_plan.md`.
  - Assert presence of `sprint/<SPRINT_NAME>` milestone tagging and dual release tagging formats.
  - Assert presence of CIs: `docs/architecture/*.md`, `docs/management/*.md`, `config/detekt/`, `tools/`, `.agents/`.
  - Assert presence of Release Contents: signed APK, Google Play AAB, `docs/store_presence/`, and Jira FixVersion.

### Test Case 2: Rule 67 Living Governance & Index Parity (`TST-PRO-057.2`)
* **Scope**: Automated Governance Audit (`tools/verify_requirement_governance.py`)
* **Assertions**: 100% parity across living doc shards; zero unmounted Composable islands; zero repository leaks; zero architecture drift.

### Test Case 3: Tier 1 Build Contract Regression Suite (`TST-PRO-057.3`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertions**: 100% pass across all build contract test suites.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PRO-057.1]` | Contract | `ReleaseCiAndLintConfigContractTest.testConfigurationManagementPlan_*` | `REQ-PRO-057.1–4` | Specified |
| `[TST-PRO-057.2]` | Governance | `verify_requirement_governance.py` | `REQ-PRO-057.3` | Specified |
| `[TST-PRO-057.3]` | Regression | `./gradlew testDebugUnitTest --tests "build.*"` | `REQ-PRO-001` | Specified |
