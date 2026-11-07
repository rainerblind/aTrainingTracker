# Stage 3: Implementation Plan - ATT-5668: Reference Showcase & Community Launch Posts

**Ticket**: [ATT-5668](https://rainerblind.atlassian.net/browse/ATT-5668)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5668`  
**Requirement Mapping**: `REQ-PRO-037` (`REQ-PRO-037.1`–`REQ-PRO-037.4`)  
**Test Mapping**: `TST-PRO-037` (`TST-PRO-037.1`–`TST-PRO-037.4`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Description & Scope
ATT-5668 concludes the tripartite Höchstmaß trilogy (Ticket 17/17). It populates Layer 2 (Real-World Reference Showcase) under `tools/hoechstmass/examples/android-jetpack-compose/` with production standards, representative lifecycle deliverables, and 3 adversarial Epic review case studies. Additionally, it authors ready-to-publish community articles under `tools/hoechstmass/docs/community/` and provides an automated JUnit contract test.

---

## 2. System Invariants & Preserved Behavior
1. **Zero Runtime App Code Edits**: `app/src/main/` and production Android code remain 100% untouched.
2. **Active Project Isolation**: Existing root documents and governance in `.agents/` and `docs/` remain unmodified.
3. **Rigorous Evidence Grounding**: All case studies and community articles ground their technical claims in authentic git history and verified Jira tickets.
4. **Code Lock (Rule 4)**: No files in `tools/hoechstmass/` or `app/src/test/...` are created until Gate 3 PASS (`python3 tools/jira_util.py check-gate <KEY>`).
5. **Parent Human Gate (Rule 3)**: Parent ticket completion is reserved exclusively for the human user in `Final Review (Human)`.

---

## 3. Proposed Architectural Layout
* **Reference Showcase (`tools/hoechstmass/examples/android-jetpack-compose/`)**:
  - `README.md`: Overview of the production Android reference showcase.
  - `CASE_STUDIES.md`: Comprehensive documentation of 3 real-world adversarial reviews:
    1. *Mader-Heck Metabolic Model* (`ATT-4786` / `ATT-4785`): Mathematical precision verification vs. floating-point drift.
    2. *Beat-to-Beat RR Intervals & DFA alpha-1 HRV* (`ATT-3539`): Real-time physiological signal processing under tight memory constraints.
    3. *Cycling Rearview Radar* (`ATT-1286`, `ATT-5111`, `ATT-5117`): Bytecode auditing (`javap`) exposing SDK method hallucinations.
  - `coding_standards.md` & `design_guidelines.md`: Full battle-tested standards evolved over 53+ sprints.
  - `artifacts/`: Representative Stage 1–5 deliverables and sprint retrospective showcase.
* **Community Launch Articles (`tools/hoechstmass/docs/community/`)**:
  - `r_softwareengineering.md`, `r_androiddev.md`, `r_google_antigravity.md`, `r_vibecoding.md`, `hackernews_show_hn.md`.
  - Mandatory sections in each post: ChatGPT epigraph quote, dual attribution reasons (provenance/human boundary and cross-model Gemini + ChatGPT UI screenshot review), Höchst·maß `/ˈhøːçstˌmaːs/` etymology box, and Swiss Cheese model.
* **Automated Contract Test**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/build/HoechstmassShowcaseAndCommunityContractTest.kt`.
* **UI Consistency**: No app UI changes (documentation and community showcase export).

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Android Reference Standards & Artifacts**: Populate `tools/hoechstmass/examples/android-jetpack-compose/` with `README.md`, `coding_standards.md`, `design_guidelines.md`, and representative lifecycle artifacts in `artifacts/`.
2. **Step 2: Adversarial Case Studies**: Author `tools/hoechstmass/examples/android-jetpack-compose/CASE_STUDIES.md` documenting the Mader-Heck, DFA alpha-1 HRV, and Radar bytecode reviews in detail.
3. **Step 3: Community Launch Posts**: Author 5 platform-tailored articles under `tools/hoechstmass/docs/community/` (`r_softwareengineering.md`, `r_androiddev.md`, `r_google_antigravity.md`, `r_vibecoding.md`, `hackernews_show_hn.md`) incorporating epigraph, dual attribution reasons, and etymology.
4. **Step 4: Automated JUnit Contract Test**: Implement `HoechstmassShowcaseAndCommunityContractTest.kt` in `app/src/test/.../build/` asserting presence, case study sections, epigraph attribution, etymology box, and community post invariants.
5. **Step 5: Targeted Verification**: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.HoechstmassShowcaseAndCommunityContractTest"` and `python3 tools/verify_requirement_governance.py`.

---

## 5. Verification & Rollback Plan
* **Verification**: Execute targeted JUnit contract test and full build test package (`--tests "com.atrainingtracker.trainingtracker.build.*"`), then confirm governance script exits 0.
* **Rollback**: Branch isolation (`feature/ATT-5668`) guarantees clean rollback without impacting `develop` or `sprint/2026-45.9`.
