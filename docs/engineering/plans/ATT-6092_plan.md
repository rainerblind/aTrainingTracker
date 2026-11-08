# Stage 3: Implementation Plan - ATT-6092: Höchstmaß README & Swiss Cheese SVG Modernization

**Ticket**: [ATT-6092](https://rainerblind.atlassian.net/browse/ATT-6092)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-PRO-035` (`REQ-PRO-035.1`, `REQ-PRO-035.5`)  
**Test Mapping**: `TST-PRO-035` (`TST-PRO-035.1`, `TST-PRO-035.2`, `TST-PRO-035.4`)  
**Branch**: `feature/ATT-6092`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-08  

---

## 1. Problem Description & Background
Following human review in `.reviews/`, `tools/hoechstmass/README.md` and `assets/swiss_cheese_layers.svg` require four refinements: (1) title change to `# Höchstmaß — The AAA Coding Framework`, (2) presenting the AAA paradigm from the inside out (ASPICE $\rightarrow$ Agentic $\rightarrow$ Agile), (3) modernizing the SVG to a 2-column layout with staggered barrier plates and blocked defect rays (`✕`) on the left and clean layer cards on the right, and (4) removing editor-specific Emacs sandbox clutter from the high-level README while maintaining complete protocol documentation in `project_protocol.md`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-035` (`REQ-PRO-035.1` Title, Inside-Out AAA, 2-Column SVG, Decorrelation Math, Epigraph & Etymology; `REQ-PRO-035.5` Protocol Review Workflow).
* **Test Mapping**: `TST-PRO-035` (`TST-PRO-035.1` / `TST-PRO-035.2` in `HoechstmassCoreFrameworkContractTest.kt`, `TST-PRO-035.4` in `verify_requirement_governance.py`).

---

## 3. System Invariants & Preserved Behavior
1. **Mathematical Defect Reduction Model**: Preserves $P(\text{escaped defect}) = \prod_{i=1}^5 p_i = p_1 \cdot p_2 \cdot p_3 \cdot p_4 \cdot p_5$ and multi-model decorrelation.
2. **Epigraph & Etymology**: ChatGPT epigraph quote and German Höchst·maß (`/ˈhøːçstˌmaːs/`) etymology box remain intact.
3. **Protocol Integrity**: Complete `.reviews/` Emacs sandbox workflow remains fully documented in `docs/project_protocol.md` and `tools/hoechstmass/docs/project_protocol.md`.
4. **Zero Production Impact**: Zero modifications to runtime app code in `app/src/main/`.

---

## 4. Proposed Architectural Changes
* **Framework README Structure**: Reorganize Section 1 into three concentric inside-out layers (ASPICE core structure $\rightarrow$ Agentic execution loop $\rightarrow$ Agile outer cadence). Remove tool-specific Emacs details from Section 3 and PA 5.1 in Section 4.
* **Vector Asset Redesign**: Update `swiss_cheese_layers.svg` (900x540 viewBox) to two distinct columns: left trajectory arena (staggered plates `L1`–`L5`, vertical defect rays, `✕` blocked markers) and right column (unobstructed layer description cards `Layer 1 (p1)`–`Layer 5 (p5)`).
* **Contract Test Realignment**: Adjust `HoechstmassCoreFrameworkContractTest.kt` to assert the updated framework title, inside-out AAA paradigm, 2-column SVG attributes (`L1`–`L5`, `✕`), and verify `.reviews/` + `Emacs` in `protocolContent`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Framework README & Swiss Cheese SVG Synchronization
* Files: `tools/hoechstmass/README.md`, `tools/hoechstmass/assets/swiss_cheese_layers.svg`
* Action: Copy verified assets from `.reviews/tools/hoechstmass/README.md` and `.reviews/assets/swiss_cheese_layers.svg` (or download from Jira attachments on `ATT-6092`). Visually inspect `swiss_cheese_layers.svg` via `read_file` (Rule 71).

### Step 2: Contract Test Enhancement
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/build/HoechstmassCoreFrameworkContractTest.kt`
* Action: Update `testCoreFrameworkDocumentation_existsAndContainsMandatorySections()` to assert `# Höchstmaß — The AAA Coding Framework`, inside-out AAA sections (`ASPICE`, `Agentic`, `Agile`), and assert `.reviews/` + `Emacs` in `protocolContent`. Update `testIsolatedReviewWorkflow_assetsAndHelperExist()` to verify 2-column SVG markers (`L1`..`L5`, `✕`).

### Step 3: Targeted Unit Tests & Governance Verification
* Commands:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.HoechstmassCoreFrameworkContractTest"`
  - `python3 tools/verify_requirement_governance.py`
  - `python3 tools/jira_util.py recalc-docs --verify`

---

## 6. Verification & Rollback Plan
* **Verification**: `HoechstmassCoreFrameworkContractTest` passes 100%; `verify_requirement_governance.py` exits 0 with full parity and valid archaeology audit.
* **Rollback**: Discard changes via `git checkout -- tools/hoechstmass/ app/src/test/` if any unexpected regression occurs.
