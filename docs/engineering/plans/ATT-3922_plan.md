# Stage 3: Implementation Plan - ATT-3922: Höchstmaß Core Framework & Starter Templates

**Ticket**: [ATT-3922](https://rainerblind.atlassian.net/browse/ATT-3922)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-3922`  
**Requirement Mapping**: `REQ-PRO-035` (`REQ-PRO-035.1`–`REQ-PRO-035.4`)  
**Test Mapping**: `TST-PRO-035` (`TST-PRO-035.1`–`TST-PRO-035.4`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Description & Scope
ATT-3922 initiates Part 1 of the 3-part series to extract the battle-tested Agile Agentic ASPICE (AAA) methodology from *aTrainingTracker* into **„Höchstmaß — The Agile Agentic ASPICE (AAA) Skill Set“** under `tools/hoechstmass/`. It establishes the top-level README, project protocol, generalized Document Triad starter templates, 10 generalized stage/ceremony skills, deliverable templates, and an automated contract test.

---

## 2. System Invariants & Preserved Behavior
1. **Zero Runtime App Code Edits**: `app/src/main/` and production code remain 100% untouched.
2. **Active Project Isolation**: `.agents/` and `docs/` in *aTrainingTracker* remain unchanged; all exported assets reside in `tools/hoechstmass/`.
3. **Generalization Invariant**: Starter templates in `tools/hoechstmass/` contain zero hardcoded `aTrainingTracker` or `ATT-` identifiers.
4. **Code Lock (Rule 4)**: No files in `tools/hoechstmass/` or `app/src/test/...` are created until Gate 3 PASS (`python3 tools/jira_util.py check-gate <KEY>`).
5. **Parent Human Gate (Rule 3)**: Parent ticket resolution is reserved exclusively for the human user in `Final Review (Human)`.

---

## 3. Proposed Architectural Layout (`tools/hoechstmass/`)
* **Documentation Root**: `README.md` and `docs/project_protocol.md` (branded Höchstmaß, ChatGPT epigraph quote, transparent attribution, cross-model adversarial review with Gemini + ChatGPT screenshot review, Höchst·maß `/ˈhøːçstˌmaːs/` etymology box, Swiss Cheese model $P = p_1 \cdot p_2 \cdots p_n$, 5-stage lifecycle).
* **Document Triad Starter Templates**:
  - `.agents/rules/aspice_governance.md` (Universal 8 Core Rules parameterized for any engineering project).
  - `docs/coding_standards.md` (Universal SWE construction invariants §1.1–§1.5 + extensible placeholder sections).
  - `docs/design_guidelines.md` (Universal UI component selection heuristics §1.1–§1.4, §2.1 dialogs).
* **Skills & Deliverable Templates**:
  - `.agents/skills/*/SKILL.md` (10 skills: `stage1-analysis` through `stage5-verification`, `sprint-planner`, `jira-workflow`, `regression-bisect`, `brainstormer`, `release-manager`).
  - `templates/*.md` (6 templates: `analysis`, `test_spec`, `plan`, `walkthrough`, `backlog_ticket`, `sprint_retro`).
* **Scaffold Directories for Parts 2 & 3**: `tools/` (Part 2), `examples/android-jetpack-compose/` and `docs/community/` (Part 3).
* **Contract Test**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/HoechstmassCoreFrameworkContractTest.kt`.
* **UI Consistency**: No app UI changes (documentation & tooling framework export).

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Directory Scaffolding**: Create directory hierarchy under `tools/hoechstmass/` including scaffold directories for Parts 2 and 3 (`tools/`, `examples/android-jetpack-compose/`, `docs/community/`).
2. **Step 2: Core Documentation**: Create `tools/hoechstmass/README.md` and `docs/project_protocol.md` incorporating the ChatGPT epigraph, attribution/provenance rules, cross-model critique, etymology callout box, Swiss Cheese formula, and 5-stage lifecycle.
3. **Step 3: Document Triad Templates**: Create generalized `.agents/rules/aspice_governance.md`, `docs/coding_standards.md`, and `docs/design_guidelines.md`.
4. **Step 4: Skills & Deliverable Templates**: Populate 10 generalized `SKILL.md` files under `tools/hoechstmass/.agents/skills/` and 6 markdown templates under `tools/hoechstmass/templates/`.
5. **Step 5: Automated Contract Test**: Implement `HoechstmassCoreFrameworkContractTest.kt` in `app/src/test/.../build/` asserting presence, non-emptiness, required phrases, and structural invariants.
6. **Step 6: Targeted Verification**: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.HoechstmassCoreFrameworkContractTest"` and `python3 tools/verify_requirement_governance.py`.

---

## 5. Verification & Rollback Plan
* **Verification**: Execute targeted contract test and full build test package (`--tests "com.atrainingtracker.trainingtracker.build.*"`), then verify governance script exits 0.
* **Rollback**: Branch isolation (`feature/ATT-3922`) guarantees clean rollback without impacting `develop` or `sprint/2026-45.9`.
