# Stage 1: Problem Domain & Root Cause Analysis - ATT-3922

**Ticket**: [ATT-3922](https://rainerblind.atlassian.net/browse/ATT-3922)  
**Parent Epic**: Standalone / Framework Initiative | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-3922`  
**Requirement Mapping**: `REQ-PRO-035` (`REQ-PRO-035.1` - `REQ-PRO-035.4`)  
**Test Mapping**: `TST-PRO-035` (`TST-PRO-035.1` - `TST-PRO-035.4`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Domain & Background
Autonomous AI coding agents frequently suffer from "vibe coding" drift, hallucinations, and regression cascade when operating without strict engineering guardrails. Over 53+ sprints and 1,100+ closed tickets in *aTrainingTracker*, the Agile Agentic ASPICE (AAA) methodology was forged to achieve industrial-grade reliability. ATT-3922 initiates Part 1 of the 3-part series to extract and package this proven methodology into a standalone, project-agnostic open-source skill set: **„Höchstmaß — The Agile Agentic ASPICE (AAA) Skill Set“** under `tools/hoechstmass/`.

---

## 2. Chesterton's Fence & Invariant Archaeology
* `Original Requirement ID & Target`: Net-new requirement `REQ-PRO-035` (building on `REQ-PRO-014`, `REQ-PRO-024`, `REQ-PRO-026`, `REQ-PRO-027`, and `REQ-PRO-034`). Targets `tools/hoechstmass/` and `app/src/test/.../build/HoechstmassCoreFrameworkContractTest.kt`.
* `Historical Origin & Commit Trace`: Evolved from Sprint 2026-39.1 (`ATT-728`, initial governance), Sprint 2026-42.1 (`ATT-3323`, Document Triad), Sprint 2026-44.1 (`ATT-4864`, Watchdogs/FGS), Sprint 2026-45.1 (`ATT-5431`, SWE.2 audit hardening), to Sprint 2026-45.9 (`ATT-3922`).
* `Root Reason for Existing Formulation`: Existing rules and skills in `.agents/` and `docs/` are tightly coupled to *aTrainingTracker* (`ATT-` prefix, fitness telemetry, Android specifics), preventing reuse in general software engineering projects.
* `Preservation of Core Invariants`: Zero changes to existing production code in `app/src/main/`; active governance in `.agents/` and `docs/` remains untouched; Human Decision Gate on parent tickets remains inviolable; clean-room test suite passes 100%.

---

## 3. Scope Bounding & Technical Architecture
1. **Core Framework & Top-Level Documentation (`tools/hoechstmass/`)**:
   - `README.md` & `docs/project_protocol.md`: Feature opening epigraph quote (*„Power without control can become destructive. Power guided by intelligence becomes excellence.“* — **ChatGPT**).
   - Document Provenance & Cross-Model Adversarial Review: Strict human/AI boundary with actor attribution (`agent1`, `agent2`, `coordinator`, `human`), plus cross-model review (intra-sprint `pro` $\leftrightarrow$ `flash` and cross-vendor UI screenshot review via Gemini + ChatGPT).
   - Etymology & Meaning Callout Box: **Höchst·maß** `/ˈhøːçstˌmaːs/` (*Höchst-* = supreme/highest + *-maß* = precision standard / *Maßarbeit*), deliberately retaining German `ö`/`ß` reflecting Automotive SPICE heritage.
   - Swiss Cheese Multiplicative Defect Reduction Model ($P(\text{escaped defect}) = p_1 \cdot p_2 \cdots p_n$) across 4 decorrelation layers: Context Isolation, Cross-Model Diversity, Deterministic Ground-Truth Oracles, and Human Final Gate.
   - 5-Stage Agile ASPICE Lifecycle with Role-Separated Gate Governance.
2. **Generalized Document Triad Starter Templates (`tools/hoechstmass/`)**:
   - `.agents/rules/aspice_governance.md`: Universal 8 Core Rules parameterized for any engineering stack.
   - `docs/coding_standards.md`: Universal construction invariants (1.1 Reuse Before Create/DRY, 1.2 Chesterton's Fence for Callbacks, 1.3 Numeric Primitive Sorting, 1.4 Confirmation for Destructive Ops, 1.5 Reactive Subscriptions) + extensible domain sections.
   - `docs/design_guidelines.md`: Universal UI component selection heuristics (1.1 Segmented Buttons for binary choices, 1.2 Categorical FilterChips, 1.3 Dropdowns for >4 options, 1.4 Discrete Sliders, 2.1 Dialog Hierarchy).
3. **Generalized Stage & Ceremony Skills (`tools/hoechstmass/.agents/skills/`)**:
   - Project-agnostic `SKILL.md` for all 10 skills: `stage1-analysis`, `stage2-req-test-spec`, `stage3-impl-plan`, `stage4-implementation`, `stage5-verification`, `sprint-planner`, `jira-workflow`, `regression-bisect`, `brainstormer`, and `release-manager`.
   - Deliverable markdown templates in `tools/hoechstmass/templates/` (`analysis_template.md`, `test_spec_template.md`, `plan_template.md`, `walkthrough_template.md`, `backlog_ticket_template.md`, `sprint_retro_template.md`).
4. **Scaffolding for Parts 2 & 3**:
   - Establish clean directory layout preparing for Part 2 (`tools/hoechstmass/tools/`) and Part 3 (`tools/hoechstmass/examples/android-jetpack-compose/`, `tools/hoechstmass/docs/community/`).
5. **Automated Verification Contract Test**:
   - Create `app/src/test/java/com/atrainingtracker/trainingtracker/build/HoechstmassCoreFrameworkContractTest.kt` asserting presence, structure, epigraph quote, etymology callout, mathematical model, 8 core rules, 10 skills, and deliverable templates.

---

## 4. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Complete decoupling: `tools/hoechstmass/` must not contain project-specific `ATT` IDs or app domain hardcodings.
  2. Zero side-effects: Active project files (`.agents/`, `docs/`, `app/src/main/`) remain unmodified.
  3. Continuous verification: Automated contract test asserts 100% presence and integrity of all exported framework assets.
* **Risk Rating**: **LOW** (Net-new documentation, skill templates, and contract tests in `tools/hoechstmass/` and `app/src/test/`; zero risk to active Android runtime code).
