# Stage 3: Implementation Plan - ATT-5817: Höchstmaß 5-Layer Swiss Cheese SVG, Entry Barrier & Isolated Emacs Review Workflow (.reviews/)

**Ticket**: [ATT-5817](https://rainerblind.atlassian.net/browse/ATT-5817)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5817`  
**Requirement Mapping**: `REQ-PRO-035` | **Test Mapping**: `TST-PRO-035`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Description & Background
Synchronize `tools/hoechstmass/` with the refined Höchstmaß framework established in `.reviews/`:
1. Adopt 5-Layer Swiss Cheese vector model (`assets/swiss_cheese_layers.svg`), statistical decorrelation math, Claude/ChatGPT/Gemini multi-model review, and Chapter 5 (*Entry Barrier*).
2. Codify the isolated in-place Code & Document Review workflow (`.reviews/` + mini-git + `emacsclient` + `>>`-directives) in project protocols and skills.
3. Provide automated CLI review helper `tools/review_tool.py` (and mirror in `tools/hoechstmass/tools/review_tool.py`).
4. Update `HoechstmassCoreFrameworkContractTest.kt` to enforce the 5-layer model, SVG asset, and review workflow.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-035` (*Standalone AAA Core Framework, Swiss Cheese Reliability & Starter Templates*)
* **Test Mapping**: `TST-PRO-035.1` (5-Layer SVG & Entry Barrier contract), `TST-PRO-035.2` (Review tooling & protocol contract), `TST-PRO-035.3` (Build & regression suite)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime App Impact**: Zero modifications to production Android code in `app/src/main/`.
2. **Main Git Index Isolation**: `.reviews/` remains completely isolated via `.git/info/exclude`.
3. **Continuous 100% Contract Test Suite**: All `Hoechstmass*` contract tests pass cleanly.

---

## 4. Proposed Architectural Changes (SWE.2)
* **Assets & Documentation**:
  * Copy `.reviews/assets/swiss_cheese_layers.svg` $\rightarrow$ `tools/hoechstmass/assets/swiss_cheese_layers.svg`.
  * Sync `tools/hoechstmass/README.md` from `.reviews/README.md`.
  * Update `tools/hoechstmass/docs/project_protocol.md` and `docs/project_protocol.md` with 5-layer model and `.reviews/` workflow.
* **Skill Set Integration**:
  * Document `.reviews/` in-place Code & Document Review workflow in `.agents/skills/stage4-implementation/SKILL.md` and `tools/hoechstmass/.agents/skills/`.
* **Automation Tooling**:
  * Implement `tools/review_tool.py` (and scaffold copy `tools/hoechstmass/tools/review_tool.py`) supporting `stage`, `stage-ticket`, `diff`, and `apply`.
* **Contract Test**:
  * Update `HoechstmassCoreFrameworkContractTest.kt` to assert 5 layers ($p_1 \dots p_5$), SVG existence, decorrelation formula, and entry barrier.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Asset & README Sync)**: Copy SVG asset and synchronize `tools/hoechstmass/README.md`.
2. **Step 2 (Protocol & Skill Harmonization)**: Update `project_protocol.md` files and `.agents/skills/` with the 5-layer model and `.reviews/` Emacs review workflow.
3. **Step 3 (Review Tooling Helper)**: Author `tools/review_tool.py` and `tools/hoechstmass/tools/review_tool.py`.
4. **Step 4 (Contract Test Update & Verification)**: Update `HoechstmassCoreFrameworkContractTest.kt` and run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.Hoechstmass*"`.
5. **Step 5 (Governance Verification)**: Run `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.Hoechstmass*"` + `python3 tools/verify_requirement_governance.py`.
* **Rollback Plan**: Working branch feature isolation (`git checkout -- .`).
