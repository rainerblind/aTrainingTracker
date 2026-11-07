# Stage 2: Requirement & Test Specification - ATT-5668: Reference Showcase & Community Launch Posts

**Ticket**: [ATT-5668](https://rainerblind.atlassian.net/browse/ATT-5668)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5668`  
**Requirement Mapping**: `REQ-PRO-037` (`REQ-PRO-037.1`–`REQ-PRO-037.4`) | **Test Spec ID**: `TST-PRO-037` (`TST-PRO-037.1`–`TST-PRO-037.4`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Requirement Specification (`REQ-PRO-037`)

### 1.1 Problem Statement & Rationale
Parts 1 (`ATT-3922`) and 2 (`ATT-5667`) established the core framework and automation engine for „Höchstmaß — The Agile Agentic ASPICE (AAA) Skill Set“. Abstract methodologies fail without empirical grounding and accessible launch articles. ATT-5668 (Part 3/3, concluding the trilogy) populates the real-world Android reference showcase (`tools/hoechstmass/examples/android-jetpack-compose/`) with 3 adversarial Epic review case studies, alongside 5 developer community launch articles (`tools/hoechstmass/docs/community/`).

### 1.2 Functional & Technical Invariants
1. **Production Android & Compose Reference Showcase (`REQ-PRO-037.1`)**:
   - `examples/android-jetpack-compose/` SHALL provide genuine, battle-tested standards (`coding_standards.md`, `design_guidelines.md`) evolved across 53+ sprints.
   - Include representative Stage 1–5 lifecycle deliverables and sprint retrospective artifacts.
2. **Adversarial Epic Review Case Studies (`REQ-PRO-037.2`)**:
   - `CASE_STUDIES.md` SHALL document 3 authentic case studies where cross-model adversarial review caught critical errors missed by solo LLMs:
     1. *Mader-Heck Metabolic Simulation Drift* (`ATT-4786` / `ATT-4785`): Mathematical precision verification vs. floating-point drift.
     2. *Beat-to-Beat RR Intervals & DFA alpha-1 HRV* (`ATT-3539`): Real-time physiological signal processing under memory constraints.
     3. *Cycling Rearview Radar Bytecode Auditing* (`ATT-1286`, `ATT-5111`, `ATT-5117`): Bytecode inspection (`javap`) exposing SDK method hallucinations.
3. **Technical Developer Community Launch Posts (`REQ-PRO-037.3`)**:
   - `docs/community/` SHALL provide 5 technical articles (`r_softwareengineering.md`, `r_androiddev.md`, `r_google_antigravity.md`, `r_vibecoding.md`, `hackernews_show_hn.md`).
   - Feature the ChatGPT epigraph quote (*„Power without control can become destructive. Power guided by intelligence becomes excellence.“*).
   - Document the 2 deliberate attribution reasons (radical provenance/human boundary and cross-model adversarial review with Gemini + ChatGPT UI screenshot review).
   - Feature German **Höchst·maß** etymology box (`/ˈhøːçstˌmaːs/` = *Höchst-* supreme + *-maß* precision standard / *Maßarbeit*).
4. **Automated Showcase & Community Contract Test (`REQ-PRO-037.4`)**:
   - JUnit contract test `HoechstmassShowcaseAndCommunityContractTest.kt` SHALL assert 100% presence, case study contents, epigraph quote, etymology callout, and community post invariants. Zero runtime modifications in `app/src/main/`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Android Showcase & Case Studies)**: *Given* `examples/android-jetpack-compose/`, *When* inspected, *Then* standards and `CASE_STUDIES.md` document the 3 adversarial reviews.
* **AC-2 (Community Launch Posts)**: *Given* `docs/community/`, *When* inspected, *Then* all 5 community articles exist and contain the ChatGPT quote, dual attribution reasons, and Höchst·maß etymology box.
* **AC-3 (Automated Verification)**: *Given* `HoechstmassShowcaseAndCommunityContractTest`, *When* executed via Gradle, *Then* all assertions pass 100% and governance checks exit 0.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Net-new `REQ-PRO-037` (concluding trilogy with `REQ-PRO-035`, `036`). Targets `tools/hoechstmass/examples/android-jetpack-compose/`, `docs/community/`, and contract test.
* `Historical Origin & Commit Trace`: Evolved across 53+ sprints, 1,100+ closed tickets, and tripartite initiative `ATT-3922`, `ATT-5667`, `ATT-5668`.
* `Root Reason for Existing Formulation`: Abstract engineering methodologies fail without empirical evidence and accessible launch articles explaining why defensive engineering gates are necessary.
* `Preservation of Core Invariants`: Zero changes to existing production code in `app/src/main/`; active governance in `.agents/` and `docs/` remains untouched; clean-room test suite passes 100%.

---

## 2. Test Specification (`TST-PRO-037`)

### Test Case 1: Real-World Reference Showcase & Case Studies Audit (`TST-PRO-037.1`)
* **Target**: `HoechstmassShowcaseAndCommunityContractTest.kt`
* **Procedure**: Assert existence of `coding_standards.md`, `design_guidelines.md`, and `CASE_STUDIES.md` under `tools/hoechstmass/examples/android-jetpack-compose/`. Assert `CASE_STUDIES.md` details all 3 adversarial reviews.

### Test Case 2: Developer Community Launch Posts Integrity Audit (`TST-PRO-037.2`)
* **Target**: `HoechstmassShowcaseAndCommunityContractTest.kt`
* **Procedure**: Assert existence of all 5 community articles in `tools/hoechstmass/docs/community/`. Assert all 5 contain the ChatGPT epigraph, dual attribution reasons (provenance and cross-model Gemini + ChatGPT UI review), and Höchst·maß etymology box.

### Test Case 3: Governance & Targeted Build Verification (`TST-PRO-037.3`)
* **Commands**: `python3 tools/verify_requirement_governance.py` && `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% test pass rate and governance verification exit 0.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-PRO-037.1` | Real-world Android & Compose reference standards | `TST-PRO-037.1` | `HoechstmassShowcaseAndCommunityContractTest.kt` |
| `REQ-PRO-037.2` | 3 adversarial Epic review case studies in `CASE_STUDIES.md` | `TST-PRO-037.1` | `HoechstmassShowcaseAndCommunityContractTest.kt` |
| `REQ-PRO-037.3` | 5 developer community launch articles with quote, attribution & etymology | `TST-PRO-037.2` | `HoechstmassShowcaseAndCommunityContractTest.kt` |
| `REQ-PRO-037.4` | Automated contract test & governance verification | `TST-PRO-037.3` | `./gradlew testDebugUnitTest` & `verify_requirement_governance.py` |
