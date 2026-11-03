# Stage 3: Implementation Plan - ATT-3534: Codify AAA Governance, SQLite Migration Parity, and Ceremony Enhancements

**Ticket**: [ATT-3534](https://rainerblind.atlassian.net/browse/ATT-3534)  
**Sub-task**: Pending creation (`[Impl-Plan]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-43.9`  
**Requirement Mapping**: `REQ-PRO-027` (*AAA Governance & Ceremony Enhancements*), `REQ-DAT-036` (*SQLite Migration Parity*)  
**Test Mapping**: `TST-PRO-020`, `TST-DAT-032`  
**Branch**: `feature/ATT-3534`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-10-23  

---

## 1. Problem Description & Background
* **Context**: Agile Agentic ASPICE (AAA) operationalizes Automotive SPICE for autonomous AI agents. While Sprint 2026-43.x established 8 core rules and the persistent `pro`/`flash` zipper architecture, key quality tripwires (Constructive Resistance, SQLite `onCreate`/`onUpgrade` parity, Stage 5 AAPT2 resource gate, Stage 1 hypothesis auditing, and Ceremony 2 completeness sweep) need authoritative codification.

---

## 2. Traceability & Requirements Mapping
* **Requirement `REQ-PRO-027`** $\rightarrow$ **`TST-PRO-020`**: Codify AAA 3 pillars & Constructive Resistance in protocol and governance; Stage 1 hypothesis audit; Stage 5 AAPT2 check; Ceremony 2 completeness sweep.
* **Requirement `REQ-DAT-036`** $\rightarrow$ **`TST-DAT-032`**: Codify SQLite `onCreate`/`onUpgrade` parity, safe `DEFAULT` column constraints, and automated migration contract tests in `docs/coding_standards.md` §2.3.

---

## 3. System Invariants & Preserved Behavior
1. **8 Core Rules Compactness**: Retain concise governance structure ($\le 80$ lines in `aspice_governance.md`).
2. **Parent Human Decision Gate**: Moving parent tickets to `Erledigt` remains strictly reserved for the human user.
3. **Automated Tooling Parity**: `python3 tools/verify_requirement_governance.py` passes with exit code 0.
4. **Zero Production Regressions**: Pure governance and standards update; zero application code modification.

---

## 4. Proposed Architectural Changes
* **Component 1: Governance & Protocol (`docs/project_protocol.md`, `.agents/rules/aspice_governance.md`)**:
  * Preamble & Protocol: Codify AAA philosophy and 3 pillars (Tripwires, Infinite Context, Constructive Resistance).
* **Component 2: Technical Architecture (`docs/coding_standards.md` §2.3)**:
  * Add §2.3: SQLite Schema Migration Parity (`onCreate`/`onUpgrade` symmetry, safe `DEFAULT`s, migration tests).
* **Component 3: Lifecycle Skills (`stage1-analysis`, `stage5-verification`, `sprint-planner`)**:
  * `stage1-analysis/SKILL.md` & template: Add Independent Hypothesis Verification / 4-Augen-Prinzip.
  * `stage5-verification/SKILL.md`: Mandate `./gradlew processDebugResources` in Tier 1 per-ticket verification.
  * `sprint-planner/SKILL.md`: Mandate Ceremony 2 Step 3 codebase completeness sweep (`rg`/`grep`).
* **UI Consistency**: No UI changes (pure governance/standards ticket).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Protocol & Core Rules Codification
* Files: `docs/project_protocol.md`, `.agents/rules/aspice_governance.md`
* Changes: Add AAA philosophy, 3 pillars, and Constructive Resistance (*Pflicht zum Widerspruch*).

### Step 2: Coding Standards SQLite Migration Parity
* Files: `docs/coding_standards.md`
* Changes: Add §2.3 mandating `onCreate`/`onUpgrade` parity, safe `DEFAULT`s, and automated migration tests.

### Step 3: Lifecycle Skill & Template Hardening
* Files: `.agents/skills/stage1-analysis/SKILL.md`, `.agents/skills/stage1-analysis/templates/analysis_template.md`, `.agents/skills/stage5-verification/SKILL.md`, `.agents/skills/sprint-planner/SKILL.md`
* Changes: Embed hypothesis verification, `./gradlew processDebugResources`, and Ceremony 2 completeness sweep.

### Step 4: Verification Suite
* Commands:
  * `python3 tools/verify_requirement_governance.py`
  * `./gradlew processDebugResources`

---

## 6. Verification & Rollback Plan
* **Verification**: `python3 tools/verify_requirement_governance.py` + `./gradlew processDebugResources`.
* **Rollback**: `git checkout .` to revert branch modifications safely.
