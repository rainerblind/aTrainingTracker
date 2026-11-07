# Stage 2: Requirement & Test Specification - ATT-3922: Höchstmaß Core Framework & Starter Templates

**Ticket**: [ATT-3922](https://rainerblind.atlassian.net/browse/ATT-3922)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-3922`  
**Requirement Mapping**: `REQ-PRO-035` (`REQ-PRO-035.1`–`REQ-PRO-035.4`) | **Test Spec ID**: `TST-PRO-035` (`TST-PRO-035.1`–`TST-PRO-035.4`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Requirement Specification (`REQ-PRO-035`)

### 1.1 Problem Statement & Rationale
Autonomous agentic coding without structured process guardrails leads to hallucinations, scope drift, and regressions. The Agile Agentic ASPICE (AAA) methodology was forged across 53+ sprints and 1,100+ tickets in *aTrainingTracker*. ATT-3922 (Part 1/3) extracts and packages the core framework into a standalone, project-agnostic open-source scaffold under `tools/hoechstmass/`.

### 1.2 Functional & Technical Invariants
1. **Branded README, Protocol, Epigraph & Etymology (`REQ-PRO-035.1`)**:
   - `README.md` & `docs/project_protocol.md` SHALL feature the ChatGPT epigraph quote (*„Power without control can become destructive. Power guided by intelligence becomes excellence.“*).
   - Codify transparent actor attribution (`agent1`, `agent2`, `coordinator`, `human`) and cross-model adversarial review (Gemini + ChatGPT, `pro` $\leftrightarrow$ `flash`).
   - Feature German **Höchst·maß** etymology box (`/ˈhøːçstˌmaːs/` = *Höchst-* + *-maß* / *Maßarbeit*).
   - Formalize Swiss Cheese Defect Reduction Model ($P = p_1 \cdot p_2 \cdots p_n$) across 4 decorrelation layers and 5-stage lifecycle.
2. **Universal Document Triad Starter Templates (`REQ-PRO-035.2`)**:
   - Supply parameterized templates: `.agents/rules/aspice_governance.md` (8 Core Rules), `docs/coding_standards.md` (§1.1–§1.5 SWE invariants), and `docs/design_guidelines.md` (M3 component heuristics).
3. **Generalized Stage & Ceremony Skills and Templates (`REQ-PRO-035.3`)**:
   - Supply 10 project-agnostic `SKILL.md` files and 6 deliverable templates (`analysis`, `test_spec`, `plan`, `walkthrough`, `backlog`, `retro`) in `tools/hoechstmass/`.
4. **Automated Framework Contract Test (`REQ-PRO-035.4`)**:
   - `HoechstmassCoreFrameworkContractTest.kt` SHALL assert 100% presence, non-emptiness, and structural integrity. Zero runtime code modifications in `app/src/main/`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Scaffold & Epigraph)**: *Given* `tools/hoechstmass/`, *When* inspected, *Then* `README.md` and `docs/project_protocol.md` contain the ChatGPT quote, cross-model review, etymology box, and Swiss Cheese formula.
* **AC-2 (Document Triad & Skills)**: *Given* templates in `tools/hoechstmass/`, *When* inspected, *Then* 3 Triad templates, 10 `SKILL.md` files, and 6 markdown templates exist in generalized form.
* **AC-3 (Automated Verification)**: *Given* `HoechstmassCoreFrameworkContractTest`, *When* executed via Gradle, *Then* 100% assertions pass and governance checks exit 0.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Net-new `REQ-PRO-035` (building on `REQ-PRO-014`, `024`, `026`, `027`, `034`). Targets `tools/hoechstmass/` and `HoechstmassCoreFrameworkContractTest.kt`.
* `Historical Origin & Commit Trace`: Sprints 2026-39.1 (`ATT-728`), 2026-42.1 (`ATT-3323`), 2026-44.1 (`ATT-4864`), 2026-45.1 (`ATT-5431`), and 2026-45.9 (`ATT-3922`).
* `Root Reason for Existing Formulation`: Rules/skills were hardcoded to *aTrainingTracker* (`ATT-` prefix, fitness telemetry, Android specifics), preventing open-source adoption.
* `Preservation of Core Invariants`: Active `.agents/` and `docs/` remain untouched; Human Final Gate remains inviolable; clean-room test suite passes 100%.

---

## 2. Test Specification (`TST-PRO-035`)

### Test Case 1: Core Framework Documentation & Etymology Contract (`TST-PRO-035.1`)
* **Target**: `HoechstmassCoreFrameworkContractTest.kt`
* **Procedure**: Assert presence and content of `tools/hoechstmass/README.md` and `tools/hoechstmass/docs/project_protocol.md` (epigraph, etymology `/ˈhøːçstˌmaːs/`, Swiss Cheese model).

### Test Case 2: Document Triad Templates Contract (`TST-PRO-035.2`)
* **Target**: `HoechstmassCoreFrameworkContractTest.kt`
* **Procedure**: Assert existence of generalized `.agents/rules/aspice_governance.md`, `docs/coding_standards.md`, and `docs/design_guidelines.md`.

### Test Case 3: 10 Generalized Skills & Deliverable Templates Contract (`TST-PRO-035.3`)
* **Target**: `HoechstmassCoreFrameworkContractTest.kt`
* **Procedure**: Assert presence of 10 `SKILL.md` files in `tools/hoechstmass/.agents/skills/` and 6 markdown templates in `tools/hoechstmass/templates/`.

### Test Case 4: Governance & Targeted Build Verification (`TST-PRO-035.4`)
* **Commands**: `python3 tools/verify_requirement_governance.py` && `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% test pass rate and governance verification exit 0.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-PRO-035.1` | Branded README, protocol, epigraph quote, etymology callout, Swiss cheese model | `TST-PRO-035.1` | `HoechstmassCoreFrameworkContractTest.kt` |
| `REQ-PRO-035.2` | Generalized Document Triad starter templates | `TST-PRO-035.2` | `HoechstmassCoreFrameworkContractTest.kt` |
| `REQ-PRO-035.3` | 10 generalized skills & 6 deliverable templates | `TST-PRO-035.3` | `HoechstmassCoreFrameworkContractTest.kt` |
| `REQ-PRO-035.4` | Automated contract test & governance verification | `TST-PRO-035.4` | `./gradlew testDebugUnitTest` & `verify_requirement_governance.py` |
