# Stage 1 Analysis: ATT-5817 - Höchstmaß 5-Layer Swiss Cheese SVG, Entry Barrier & Isolated Emacs Review Workflow (.reviews/)

**Ticket**: [ATT-5817](https://rainerblind.atlassian.net/browse/ATT-5817)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5817`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Statement & Motivation
During the architectural review of `tools/hoechstmass/README.md`, two key improvements emerged:
1. **Methodological Refinement**: Evolve the 4-layer ASCII Swiss Cheese defect model into a formal 5-Layer model ($p_1 \dots p_5$) rendered via authentic SVG (`assets/swiss_cheese_layers.svg`), formalize statistical decorrelation ($P(\text{escaped defect}) = \prod_{i=1}^5 p_i$), incorporate Claude & ChatGPT alongside Gemini, and add Chapter 5 ("Entry Barrier & Human Discipline").
2. **Isolated In-Place Review Workflow (`.reviews/`)**: Eliminate checkout and build collision risks when the human reviews code or documents concurrently while autonomous agents compile and switch branches, utilizing an isolated mini-git (`.reviews/`, excluded via `.git/info/exclude`), `emacsclient`, inline `>>` directives, and automated diff harvesting.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Current State**: `tools/hoechstmass/README.md` still contains legacy 4-layer ASCII art and lacks the Emacs in-place review workflow and entry barrier chapter. `.reviews/README.md` and `.reviews/assets/swiss_cheese_layers.svg` already contain the reviewed and validated state.
* **Independent Peer Review & Verification**:
  * Inspected `.reviews/assets/swiss_cheese_layers.svg` visually via `read_file` per Rule 71: validated 5 layers ($p_1$ to $p_5$), offset holes, defect ray blocks, and clean styling.
  * Verified `.git/info/exclude`: `.reviews/` is already isolated from the main Git index and Gradle builds.
  * Inspected `HoechstmassCoreFrameworkContractTest.kt`: currently checks legacy 4-layer text; requires updating to assert 5 layers and SVG asset presence.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Synchronize `tools/hoechstmass/README.md` and `tools/hoechstmass/assets/swiss_cheese_layers.svg` from `.reviews/`.
  2. Harmonize `tools/hoechstmass/docs/project_protocol.md` and `docs/project_protocol.md` with the 5 layers and `.reviews/` workflow.
  3. Codify the `.reviews/` in-place Code & Document Review workflow in active `.agents/skills/` (and `tools/hoechstmass/.agents/skills/`).
  4. Implement `tools/review_tool.py` (and mirror in `tools/hoechstmass/tools/review_tool.py`) for automated file staging into `.reviews/`, Emacs launching, diff inspection, and live reload.
  5. Refine `REQ-PRO-035` / `TST-PRO-035` and update `HoechstmassCoreFrameworkContractTest.kt`.
* **Out-of-Scope Non-Goals**:
  * Modifying production app source code (`app/src/main/`).
  * Changing core GitFlow or in-sprint branching rules.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-PRO-035` (`req_process_stability.md`), `HoechstmassCoreFrameworkContractTest.kt`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-45.9 (`ATT-3922`, commit `54f85e4`).
* **Root Reason for Existing Formulation**: Initial export codified 4 abstract layers and lacked concurrent review isolation.
* **Preservation of Core Invariants**: Extends defect reduction math ($P = \prod_{i=1}^5 p_i$), decorrelation rigor, zero runtime app impact, and 100% contract test suite pass rate.

---

## 5. Architectural Strategy & High-Level Solution
* **Asset & Documentation Sync**: Copy SVG to `tools/hoechstmass/assets/` and sync `tools/hoechstmass/README.md`.
* **CLI Review Helper**: Provide `tools/review_tool.py` with commands (`stage`, `stage-ticket`, `diff`, `apply`) interacting safely with `.reviews/` mini-git and `emacsclient`.
* **Contract Test Alignment**: Update `HoechstmassCoreFrameworkContractTest.kt` to assert 5 layers, SVG file, and new sections.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero runtime app code modifications; 100% build and contract test pass rate; main repo Git index isolation for `.reviews/`.
* **Risk Rating**: **LOW** — Documentation, tooling, and test contract enhancements with no runtime app footprint.
