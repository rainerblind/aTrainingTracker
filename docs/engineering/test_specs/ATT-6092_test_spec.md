# Stage 2: Requirement & Test Specification - ATT-6092

**Ticket**: [ATT-6092](https://rainerblind.atlassian.net/browse/ATT-6092)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-PRO-035` (*Standalone Agile Agentic ASPICE (AAA) Core Framework, Swiss Cheese Reliability & Starter Templates*)  
**Test Spec ID**: `TST-PRO-035`  
**Branch**: `feature/ATT-6092`  
**Author**: AI Agent 1 (`subagent_flash`)  
**Date**: 2026-11-08  

---

## 1. Requirement Specification (`REQ-PRO-035`)

### 1.1 Problem Statement & Rationale
Refine `tools/hoechstmass/README.md` and `assets/swiss_cheese_layers.svg` per the approved `.reviews/` staging: rename to `# Höchstmaß — The AAA Coding Framework`, structure Section 1 from the inside out (ASPICE $\rightarrow$ Agentic $\rightarrow$ Agile), modernize the SVG into a 2-column layout (left: staggered barrier plates `L1`–`L5` with `✕` markers; right: clean layer cards), and remove tool-specific Emacs sandbox clutter from the high-level README while preserving full protocol coverage in `project_protocol.md`.

### 1.2 Functional & Architectural Requirements
1. `tools/hoechstmass/README.md` SHALL carry title `# Höchstmaß — The AAA Coding Framework` and subtitle `**Agile · Agentic · ASPICE — Precision Software Engineering with Autonomous AI Pairs**`.
2. Section 1 of `README.md` SHALL explain AAA inside-out: **ASPICE** (5 gated Sub-Tasks + Final Human Review, Code Lock) $\rightarrow$ **Agentic** (author/auditor loop on alternating `pro` $\leftrightarrow$ `flash` tiers) $\rightarrow$ **Agile** (sprint bundling & retrospective).
3. `tools/hoechstmass/assets/swiss_cheese_layers.svg` SHALL render a 2-column layout: left column with 5 staggered barrier plates `L1`–`L5` blocking 4 straight vertical defect rays with `✕`; right column with unobstructed cards for `Layer 1 (p1)` through `Layer 5 (p5)`.
4. Section 3 of `README.md` SHALL omit `.reviews/ + Emacs` sandbox details and Section 4 SHALL frame ISO/IEC 33020 as a grounded maturity roadmap without Emacs references (preserving complete review sandbox specification in `project_protocol.md`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Title & Inside-Out AAA)**:
  * *Given* `tools/hoechstmass/README.md`
  * *When* inspected
  * *Then* title is `# Höchstmaß — The AAA Coding Framework` and Section 1 explains ASPICE $\rightarrow$ Agentic $\rightarrow$ Agile inside-out.
* **Criterion 2 (2-Column SVG & Clean Content)**:
  * *Given* `tools/hoechstmass/assets/swiss_cheese_layers.svg` and `README.md`
  * *When* rendered and verified
  * *Then* the SVG shows 2-column layout with `L1`–`L5` barrier plates and `✕` markers, and `README.md` contains zero `.reviews/ + Emacs` tool clutter.

### 1.4 System Invariants
* 5-Layer mathematical defect reduction formula ($P = \prod_{i=1}^5 p_i$) and epigraph quote preserved.
* Zero modification to runtime application code in `app/src/main/`.

---

## 2. Test Specification (`TST-PRO-035`)

### Test Case 1: Framework Contract Test (`TST-PRO-035.1`, `TST-PRO-035.2`)
* **Scope**: Automated Unit / Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/HoechstmassCoreFrameworkContractTest.kt`
* **Preconditions**: Staged README, 2-column SVG, and updated contract assertions in place.
* **Action**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.HoechstmassCoreFrameworkContractTest"`
* **Expected Result**: 100% PASS; validates new title, inside-out AAA sections, 2-column SVG structure, and protocol review workflow.

### Test Case 2: Living Documentation Governance (`TST-PRO-035.4`)
* **Scope**: Governance Parity & Chesterton's Fence Audit
* **Command**: `python3 tools/verify_requirement_governance.py`
* **Expected Result**: Exit code 0, 100% parity across living doc shards and valid 4-field Chesterton's Fence audit on `REQ-PRO-035`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PRO-035.1]` | Contract | `testCoreFrameworkDocumentation_existsAndContainsMandatorySections` | `REQ-PRO-035.1` | Specified |
| `[TST-PRO-035.2]` | Contract | `testIsolatedReviewWorkflow_assetsAndHelperExist` | `REQ-PRO-035.5` | Specified |
| `[TST-PRO-035.4]` | Governance | `verify_requirement_governance.py` | `REQ-PRO-035` | Specified |

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-035` (*Standalone Agile Agentic ASPICE (AAA) Core Framework, Swiss Cheese Reliability & Starter Templates*), originally introduced in `ATT-3922` and extended in `ATT-5817`.
* **Historical Origin & Commit Trace**: Commit `ada99247` (`ATT-3922`) exported the initial framework; commit `a00d32ee` (`ATT-5817`) added 5-Layer Swiss Cheese SVG, Entry Barrier, and `.reviews/` Emacs workflow. Refined in `ATT-6092`.
* **Root Reason for Existing Formulation**: Early draft placed editor-specific review tool details into the high-level README before `project_protocol.md` was decoupled as the comprehensive engineering protocol, and used a 1-column SVG with overlapping circles behind text.
* **Preservation of Core Invariants**: Retains the 5-layer Swiss Cheese mathematical formula ($P = \prod_{i=1}^5 p_i$), epigraph quote, etymology box, 5-stage lifecycle, and full `.reviews/` coverage in `project_protocol.md`. Zero runtime application code impact.
