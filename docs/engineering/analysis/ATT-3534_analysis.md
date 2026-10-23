# Stage 1 Analysis: ATT-3534 - Codify Agile Agentic ASPICE (AAA) Governance, SQLite Migration Parity, and Ceremony Enhancements

**Ticket**: [ATT-3534](https://rainerblind.atlassian.net/browse/ATT-3534)  
**Sub-task**: Pending creation (`[Analysis]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-43.9`  
**Branch**: `feature/ATT-3534`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-10-23  

---

## 1. Problem Statement & Motivation
* **Context**: While Sprint 2026-43.x successfully compacted governance into 8 core rules and persistent `pro`/`flash` zipper pairing (Rules 56–63), key ASPICE architectural philosophies and quality tripwires remain implicit or spread across legacy tickets:
  1. The core philosophy of **Agile Agentic ASPICE (AAA)** and **Constructive Resistance** (*Pflicht zum Widerspruch & Dokumentierte Abweichungserlaubnis*) lacks explicit codification in `docs/project_protocol.md` and `.agents/rules/aspice_governance.md`.
  2. Database schema evolution lacks an explicit standard for `onCreate`/`onUpgrade` parity, safe `DEFAULT` values for non-null columns, and automated migration contract tests in `docs/coding_standards.md` §2.2.
  3. Stage 5 on non-UI backend tickets lacks a fast resource gate (`./gradlew processDebugResources`) to trap 9-language XML/AAPT2 compilation errors before Gate 5 sign-off.
  4. Stage 1 lacks formal instructions to treat prior Jira ticket analyses as hypotheses subject to independent live-code audit (4-Augen-Prinzip).
  5. Ceremony 2 ticket inspection lacks a mandated completeness sweep (`rg`) across `app/src/main` to catch orphaned deprecated code on refactoring/cleanup tickets.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Hypothesis Audit**: The architectural updates proposed in Jira ticket `ATT-3534` were audited against the current repository state:
  * *`docs/project_protocol.md`*: Currently a 14-line pointer to the Document Triad; contains room to succinctly enshrine the 3 Automotive SPICE pillars (Intentional Engineering Tripwires, Artifact-Driven Infinite Context, Constructive Resistance) without duplicating rules. Verified.
  * *`.agents/rules/aspice_governance.md`*: Governs 8 core rules; needs a clear preamble and explicit Rule 1 / Rule 3 anchoring for Constructive Resistance. Verified.
  * *`docs/coding_standards.md` §2*: Section 2.2 covers `DatabaseSchemaRegistry` version matching; lacks explicit `onCreate`/`onUpgrade` parity, safe defaults, and automated migration testing. Verified.
  * *`.agents/skills/stage5-verification/SKILL.md`*: Recommends `./gradlew assembleDebug` for backend tickets; replacing/augmenting with `./gradlew processDebugResources` catches AAPT2/XML syntax issues in ~5s vs full APK build. Verified.
  * *`.agents/skills/stage1-analysis/SKILL.md` & `templates/analysis_template.md`*: Lacks explicit guidance to independently audit ticket-provided root causes and include a dedicated verification subsection. Verified.
  * *`.agents/skills/sprint-planner/SKILL.md`*: Step 3 focuses on `git show <KEY>` and recommendation; lacks an explicit codebase completeness sweep requirement for refactoring tickets. Verified.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Codify AAA Philosophy and Constructive Resistance in `docs/project_protocol.md` and `.agents/rules/aspice_governance.md`.
  * Define SQLite schema migration and `onCreate`/`onUpgrade` parity rules in `docs/coding_standards.md` §2.2.
  * Add fast AAPT2 check (`processDebugResources`) to `.agents/skills/stage5-verification/SKILL.md`.
  * Add independent hypothesis verification requirements to `.agents/skills/stage1-analysis/SKILL.md` and `templates/analysis_template.md`.
  * Add codebase completeness sweep (`rg`) to Ceremony 2 in `.agents/skills/sprint-planner/SKILL.md`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to production application code or existing SQLite database helpers.
  * No rewriting or expanding the 8 Core Rules into a verbose multi-page document (preserve compact Rule 60 format).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-026` (*Document Triad Architecture*), `REQ-PRO-024` (*Modular Skill Architecture*), and `REQ-DAT-008` (*Atomic Database Upgrades*).
* **Historical Origin & Commit Trace**: Commit `6925d864` (`ATT-3323`, separated Document Triad); commit `28678082` (`REQ-DAT-008`, atomic SQLite migrations).
* **Root Reason for Existing Formulation**: Earlier rules addressed process bloating by creating the Triad and atomic migration transactions, but left human exception governance, AAPT2 resource checks, and hypothesis verification implicit.
* **Preservation of Core Invariants**: The 8 Core Rules structure, single-atomic-commit per ticket, Human Decision Gate, and 100% clean-room test pass rate remain strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution
* **Governance & Standards Updates**:
  1. `docs/project_protocol.md`: Codify AAA definition and 3 Automotive SPICE pillars (Tripwires, Infinite Context, Constructive Resistance).
  2. `.agents/rules/aspice_governance.md`: Embed Constructive Resistance (*Pflicht zum Widerspruch*) into the preamble and Rule 1.
  3. `docs/coding_standards.md`: Add §2.3 codifying SQLite Migration Parity (`onCreate`/`onUpgrade` symmetry, safe `DEFAULT` constraints, migration unit tests).
  4. `.agents/skills/stage1-analysis/SKILL.md` & `templates/analysis_template.md`: Add Hypothesis Verification and peer review instructions.
  5. `.agents/skills/stage5-verification/SKILL.md`: Mandate `./gradlew processDebugResources` in Stage 5 Tier 1 verification.
  6. `.agents/skills/sprint-planner/SKILL.md`: Add codebase completeness sweep (`rg`) to Ceremony 2 Step 3.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Human Decision Gate on parent tickets remains inviolable (Rule 3).
  2. Single atomic commit per ticket on `sprint/<SPRINT_NAME>` preserved (Rule 8).
  3. Zero regressions in governance verification (`tools/verify_requirement_governance.py`).
* **Risk Rating**: **LOW** (pure governance, skill, and living standard specification; zero production code impact).
