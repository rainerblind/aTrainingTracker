# Stage 2: Requirement & Test Specification - ATT-3534: Codify AAA Governance, SQLite Migration Parity, and Ceremony Enhancements

**Ticket**: [ATT-3534](https://rainerblind.atlassian.net/browse/ATT-3534)  
**Sub-task**: Pending creation (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-43.9`  
**Requirement Mapping**: `REQ-PRO-027` (*AAA Governance & Ceremony Enhancements*), `REQ-DAT-036` (*SQLite Migration Parity*)  
**Test Spec ID**: `TST-PRO-020`, `TST-DAT-032`  
**Branch**: `feature/ATT-3534`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-10-23  

---

## 1. Requirement Specification (`REQ-PRO-027`, `REQ-DAT-036`)

### 1.1 Problem Statement & Rationale
While 8 Core Rules compacted governance, fundamental Automotive SPICE pillars (Tripwires, Infinite Context, Constructive Resistance), SQLite `onCreate`/`onUpgrade` parity, Stage 5 AAPT2 resource checks, Stage 1 hypothesis auditing, and Ceremony 2 completeness sweeps remain implicit across scattered tickets.

### 1.2 Functional & Architectural Requirements
1. **Agile Agentic ASPICE (AAA) Philosophy & Constructive Resistance (`REQ-PRO-027.1`)**:
   - `docs/project_protocol.md` and `.agents/rules/aspice_governance.md` SHALL enshrine the 3 pillars: (1) Intentional Engineering Tripwires, (2) Artifact-Driven Infinite Context, (3) Constructive Resistance & Documented Deviations (*Pflicht zum Widerspruch & Dokumentierte Abweichungserlaubnis*).
2. **Stage 1 Hypothesis Verification (`REQ-PRO-027.2`)**:
   - `.agents/skills/stage1-analysis/SKILL.md` and `templates/analysis_template.md` SHALL instruct agents to treat prior Jira analyses as hypotheses to verify against live code with a dedicated "Independent Peer Review & Verification" subsection.
3. **Fast Stage 5 Resource Gate (`REQ-PRO-027.3`)**:
   - `.agents/skills/stage5-verification/SKILL.md` SHALL mandate `./gradlew processDebugResources` in Tier 1 per-ticket verification to catch AAPT2/XML issues rapidly.
4. **Ceremony 2 Completeness Sweep (`REQ-PRO-027.4`)**:
   - `.agents/skills/sprint-planner/SKILL.md` Ceremony 2 Step 3 SHALL mandate `git show` review and a codebase completeness sweep (`rg`/`grep`) across `app/src/main` on refactoring/cleanup tickets.
5. **SQLite Schema Evolution & Migration Parity (`REQ-DAT-036`)**:
   - `docs/coding_standards.md` §2.3 SHALL mandate symmetrical updates to `onCreate()` and `onUpgrade()` for every `DB_VERSION` increment, safe `DEFAULT` constraints for added `NOT NULL` columns, and automated migration contract tests asserting schema equivalence.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (AAA Philosophy & Constructive Resistance)**:
  * *Given* `docs/project_protocol.md` and `.agents/rules/aspice_governance.md`,
  * *When* reviewed for engineering philosophy and human exception policy,
  * *Then* the 3 pillars are explicitly codified and constructive resistance before exception documentation is enforced.
* **Criterion 2 (SQLite Migration Parity Standards)**:
  * *Given* `docs/coding_standards.md` §2.3,
  * *When* database evolution standards are reviewed,
  * *Then* symmetrical `onCreate()`/`onUpgrade()` updates, safe `DEFAULT` constraints, and automated migration tests are mandated.
* **Criterion 3 (Stage 5 Resource Gate, Stage 1 Hypothesis Audit & Ceremony 2 Sweep)**:
  * *Given* `.agents/skills/stage5-verification/SKILL.md`, `stage1-analysis/SKILL.md`, and `sprint-planner/SKILL.md`,
  * *When* stage responsibilities and ceremony steps are audited,
  * *Then* `./gradlew processDebugResources`, independent hypothesis verification, and Ceremony 2 completeness sweep are strictly specified.

### 1.4 System Invariants
1. Human Decision Gate on parent tickets remains inviolable (Rule 3).
2. Single atomic commit per ticket on `sprint/<SPRINT_NAME>` preserved (Rule 8).
3. Zero regressions in `python3 tools/verify_requirement_governance.py`.

### 1.5 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-PRO-027` (refining `REQ-PRO-026`, `REQ-PRO-024`) and `REQ-DAT-036` (refining `REQ-DAT-008`).
* **Historical Origin & Commit Trace**: `REQ-PRO-026` (`ATT-3323`, commit `6925d864`), `REQ-PRO-024` (`ATT-3676`, commit `7a12b84`), `REQ-DAT-008` (`ATT-103`, commit `28678082`).
* **Root Reason for Existing Formulation**: Earlier rules addressed process bloating and atomic transactions but left human exception governance, AAPT2 resource checks, and hypothesis verification implicit.
* **Preservation of Core Invariants**: The 8 Core Rules structure, Persistent Zipper Principle, and 100% clean-room test pass rate remain strictly preserved.

---

## 2. Test Specification (`TST-PRO-020`, `TST-DAT-032`)

### Test Case 1: AAA Governance, Constructive Resistance & Ceremony Enhancements (`TST-PRO-020`)
* **Scope**: Governance & Process Documentation Contract Audit
* **Target Files**: `docs/project_protocol.md`, `.agents/rules/aspice_governance.md`, `.agents/skills/stage1-analysis/SKILL.md`, `.agents/skills/stage5-verification/SKILL.md`, `.agents/skills/sprint-planner/SKILL.md`
* **Preconditions**: Base documentation in place.
* **Action**: Verify presence and phrasing of AAA pillars, constructive resistance, `./gradlew processDebugResources`, hypothesis audit subsection, and Ceremony 2 completeness sweep.
* **Expected Result**: All 5 files reflect updated standards with zero orphaned legacy instructions.

### Test Case 2: SQLite Schema Migration Parity & Contract Standards (`TST-DAT-032`)
* **Scope**: Technical Architecture Coding Standards Audit
* **Target File**: `docs/coding_standards.md` §2.3
* **Preconditions**: Coding standards Section 2 inspected.
* **Action**: Verify addition of Section 2.3 mandating `onCreate()`/`onUpgrade()` schema parity, safe defaults for `NOT NULL` columns, and automated migration contract tests.
* **Expected Result**: Section 2.3 is explicitly codified and references SQLite schema migration invariants.

### Test Case 3: Requirement Governance Clean-Room Verification
* **Scope**: Tooling Automation Gate
* **Command**: `python3 tools/verify_requirement_governance.py`
* **Expected Result**: Exit code `0` with valid bidirectional requirement/test traceability.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-020` | Governance | Protocol & Skill Documentation Audit | `REQ-PRO-027` | Specified |
| `TST-DAT-032` | Architecture | Coding Standards §2.3 Audit | `REQ-DAT-036` | Specified |
| Governance Gate | Tooling | `verify_requirement_governance.py` | `REQ-PRO-022`, `REQ-PRO-027` | Specified |
