# Stage 2: Requirement & Test Specification - ATT-5817

**Ticket**: [ATT-5817](https://rainerblind.atlassian.net/browse/ATT-5817)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5817`  
**Requirement Mapping**: `REQ-PRO-035` (*Standalone AAA Core Framework, Swiss Cheese Reliability & Starter Templates*)  
**Test Spec ID**: `TST-PRO-035`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Requirement Specification (REQ-PRO-035)

### 1.1 Problem Statement & Rationale
`tools/hoechstmass/README.md` and related documents currently employ a legacy 4-layer ASCII Swiss Cheese diagram and lack the isolated Emacs in-place Code & Document Review workflow (`.reviews/`) and Entry Barrier section developed in `.reviews/README.md` and `.reviews/assets/swiss_cheese_layers.svg`.

### 1.2 Functional & Architectural Requirements
1. **5-Layer Swiss Cheese Defense Model (`REQ-PRO-035.1`)**:
   - `tools/hoechstmass/README.md` and `docs/project_protocol.md` SHALL feature the 5-layer vector defense model embedding `assets/swiss_cheese_layers.svg`.
   - The documentation SHALL derive statistical decorrelation ($P(\text{escaped defect}) = \prod_{i=1}^5 p_i = p_1 \cdot p_2 \cdot p_3 \cdot p_4 \cdot p_5$).
   - The documentation SHALL incorporate Claude & ChatGPT alongside Gemini in multi-model adversarial reviews, and codify Chapter 5 (*Entry Barrier & Human Discipline*).
2. **Isolated In-Place Code & Document Review Workflow (`REQ-PRO-035.5`)**:
   - The system SHALL isolate `.reviews/` from main Git index and builds via `.git/info/exclude`.
   - The system SHALL codify the in-place review workflow (`emacsclient`, inline `>>` directives, diff harvesting) in protocols, READMEs, and skills.
   - The project SHALL provide `tools/review_tool.py` (and mirror in `tools/hoechstmass/tools/review_tool.py`) for automated file staging and live reload.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (5-Layer Model & Assets)**:
  * *Given* `tools/hoechstmass/README.md` and `tools/hoechstmass/assets/swiss_cheese_layers.svg`,
  * *When* inspected,
  * *Then* the 5 defensive layers ($p_1 \dots p_5$), vector SVG, decorrelation math, and Entry Barrier chapter are present.
* **Criterion 2 (Review Workflow & Tool)**:
  * *Given* `tools/review_tool.py` and `.reviews/` directory,
  * *When* staging files or inspecting diffs,
  * *Then* `.reviews/` operates as an isolated Git repo without polluting root Git status or triggering build re-evaluations.

### 1.4 System Invariants
* Zero modifications to production application source code in `app/src/main/`.
* Complete isolation of `.reviews/` from the main Git index.
* 100% build and contract test suite pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-PRO-035` (`tools/hoechstmass/`, `docs/project_protocol.md`, `tools/review_tool.py`, `HoechstmassCoreFrameworkContractTest.kt`).
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-45.9 (`ATT-3922`, commit `54f85e4`); refined in Sprint 2026-45.12 (`ATT-5817`).
3. *Root Reason for Existing Formulation*: Initial export codified 4 abstract layers and lacked visual SVG representation, statistical decorrelation derivation, Claude multi-vendor reviews, and isolated Emacs review facilities.
4. *Preservation of Core Invariants*: Extends defect reduction math ($P = \prod_{i=1}^5 p_i$), decorrelation rigor, zero runtime app impact, and 100% contract test suite pass rate.

---

## 2. Test Specification (TST-PRO-035)

### Test Case 1: Framework Documentation & 5-Layer Contract (`TST-PRO-035.1`)
* **Scope**: Automated JUnit 4 Contract Test (`HoechstmassCoreFrameworkContractTest.kt`)
* **Assertions**: Assert `tools/hoechstmass/assets/swiss_cheese_layers.svg` exists; assert `tools/hoechstmass/README.md` contains 5 layers ($p_1 \dots p_5$), SVG embed, decorrelation math ($P(\text{escaped defect}) = \prod_{i=1}^5 p_i$), and Entry Barrier chapter.

### Test Case 2: Review Tooling & Protocol Integration Contract (`TST-PRO-035.2`)
* **Scope**: Tooling Contract Test (`HoechstmassToolingContractTest.kt` or new contract assertion)
* **Assertions**: Assert `tools/review_tool.py` exists, is non-empty, and CLI commands (`stage`, `diff`, `apply`) execute help/syntax checks without error.

### Test Case 3: Tier 1 Targeted Contract & Build Suite (`TST-PRO-035.3`)
* **Scope**: Regression execution
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.Hoechstmass*"`
* **Expected Result**: 100% PASS across all framework contract tests; zero failures.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-035.1` | Contract | `HoechstmassCoreFrameworkContractTest` | `REQ-PRO-035.1`, `REQ-PRO-035.4` | Specified |
| `TST-PRO-035.2` | Tooling | `tools/review_tool.py` CLI syntax | `REQ-PRO-035.5` | Specified |
| `TST-PRO-035.3` | Regression | Gradle build test runner | `REQ-PRO-035.4` | Specified |
