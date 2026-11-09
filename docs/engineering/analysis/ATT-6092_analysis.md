# Stage 1 Analysis: ATT-6092 - Höchstmaß README & Swiss Cheese SVG: Rename to 'The AAA Coding Framework', explain ASPICE->Agentic->Agile inside-out & redesign 2-column SVG

**Ticket**: [ATT-6092](https://rainerblind.atlassian.net/browse/ATT-6092)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Branch**: `feature/ATT-6092`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
Following the interactive human review in `.reviews/`, `tools/hoechstmass/README.md` and `assets/swiss_cheese_layers.svg` require four structural and visual refinements:
1. **Title & Subtitle**: Rename title to `# Höchstmaß — The AAA Coding Framework` with subtitle `**Agile · Agentic · ASPICE — Precision Software Engineering with Autonomous AI Pairs**`.
2. **Inside-Out AAA Paradigm**: Restructure Section 1 to introduce the framework from the inside out: **ASPICE** (Core Task Structure: 5 gated Sub-Tasks + Final Human Review, Code Lock until Stage 3) $\rightarrow$ **Agentic** (Execution Loop: autonomous author/auditor loop on alternating `pro` $\leftrightarrow$ `flash` tiers) $\rightarrow$ **Agile** (Outer Cadence: sprint bundling & retrospective).
3. **Redesigned 2-Column SVG**: Modernize `assets/swiss_cheese_layers.svg` into a clean two-column layout: left column with 5 staggered barrier plates `L1`–`L5` and 4 straight vertical defect rays blocked with `✕`; right column with unobstructed `Layer 1 (p1)`–`Layer 5 (p5)` cards free from overlapping dashed circles or occluded text.
4. **Focused High-Level Content**: Remove the editor-specific `.reviews/ + Emacs` sandbox subsection from Section 3 and the Emacs reference from `PA 5.1` in Section 4, framing Section 4 as a grounded maturity roadmap (keeping Emacs/.reviews in `project_protocol.md`).

---

## 2. Root Cause Analysis, Independent Verification & Code-Grounding (ATT-5431)
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  - Audited `.reviews/tools/hoechstmass/README.md` (16,468 bytes) and `.reviews/assets/swiss_cheese_layers.svg` (8,068 bytes) against live `tools/hoechstmass/`: verified that diff cleanly implements inside-out structure, removes tool-specific Emacs sandbox from Section 3 and PA 5.1, and redesigns SVG into left barrier column / right card column without text overlap.
  - Inspected `HoechstmassCoreFrameworkContractTest.kt`: currently requires `.reviews/` and `Emacs` in `README.md` (lines 104-105). Contract test must be updated to verify `.reviews/` and `Emacs` in `protocolContent` (`project_protocol.md`) and assert the new title and inside-out AAA structure in `README.md`.
* **Documentation Code-Grounding Audit (ATT-5431)**:
  - `README.md` adheres to real codebase invariants: 5 Sub-Tasks, Final Review (Human), Code Lock until Stage 3 (`tools/jira_util.py check-gate`), and asymmetric zipper ping-pong (`aspice_governance.md`).
  - `.reviews/` and `emacsclient` workflow remains fully documented and tested in `docs/project_protocol.md`, `tools/hoechstmass/docs/project_protocol.md`, and `tools/review_tool.py`.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail
* **In-Scope Goals**:
  1. Synchronize `tools/hoechstmass/README.md` and `tools/hoechstmass/assets/swiss_cheese_layers.svg` from reviewed versions attached to `ATT-6092` / `.reviews/`.
  2. Update `HoechstmassCoreFrameworkContractTest.kt` to assert new title, inside-out structure, 2-column SVG elements (`L1`..`L5`, `✕`), and protocol review workflow.
  3. Refine `REQ-PRO-035` and `TST-PRO-035` in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md`.
  4. Ensure `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.HoechstmassCoreFrameworkContractTest"` and `verify_requirement_governance.py` pass 100%.
* **Out-of-Scope**: Modifications to runtime app code in `app/src/main/` or altering core governance rules in `aspice_governance.md`.
* **Complexity Rating**: **1 SP (Trivial / Documentation & Asset Synchronization)**. Well within $\le 3\text{ SP}$ limit.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-035` (*Standalone Agile Agentic ASPICE (AAA) Core Framework, Swiss Cheese Reliability & Starter Templates*), originally introduced in `ATT-3922` and extended in `ATT-5817`.
* **Historical Origin & Commit Trace**: Commit `ada99247` (`ATT-3922`) exported the initial framework; commit `a00d32ee` (`ATT-5817`) added 5-Layer Swiss Cheese SVG, Entry Barrier, and `.reviews/` Emacs workflow.
* **Root Reason for Existing Formulation**: Early draft placed editor-specific review tool details into the high-level README before `project_protocol.md` was decoupled as the comprehensive engineering protocol.
* **Preservation of Core Invariants**: Retains the 5-layer Swiss Cheese mathematical formula ($P = \prod_{i=1}^5 p_i$), epigraph quote, etymology box, 5-stage lifecycle, and full `.reviews/` coverage in `project_protocol.md`.

---

## 5. Architectural Strategy & Test Verification
* **Asset & Markdown Integration**: Copy reviewed `README.md` and `swiss_cheese_layers.svg` into `tools/hoechstmass/`.
* **Contract Test Update**: Refactor assertions in `HoechstmassCoreFrameworkContractTest.kt` to validate the updated title, inside-out AAA sections, and protocol-level review workflow assertions.
* **Living Documentation**: Synchronize `req_process_stability.md` and `tst_process_stability.md` via `python3 tools/verify_requirement_governance.py`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `HoechstmassCoreFrameworkContractTest` passes 100% with no regression across existing tests.
  2. Zero modifications to runtime app sources in `app/src/main/`.
  3. `verify_requirement_governance.py` passes with 100% parity across living docs.
* **Risk Rating**: **LOW**. Documentation, SVG asset, and contract test updates only.
