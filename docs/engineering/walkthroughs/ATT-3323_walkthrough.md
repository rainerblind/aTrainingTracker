# Stage 5: Walkthrough - ATT-3323: Separate ASPICE governance rules from UI design guidelines and technical coding standards

**Ticket**: [ATT-3323](https://rainerblind.atlassian.net/browse/ATT-3323)  
**Sub-task**: [ATT-3421](https://rainerblind.atlassian.net/browse/ATT-3421) (`[Test]`)  
**Parent Epic**: [ATT-3079](https://rainerblind.atlassian.net/browse/ATT-3079) (*Sprint 2026-42.1 Governance & Architecture*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-PRO-026`  
**Test Mapping**: `TST-PRO-019`  
**Branch**: `feature/ATT-3323`  
**Author**: AI Agent 1 (Verification & Quality Engineer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-PRO-026` (*Decoupled Document Triad Architecture: ASPICE Governance, UI Design Guidelines, and Technical Coding Standards*), targeting `.agents/rules/aspice_governance.md`, `docs/design_guidelines.md`, and `docs/coding_standards.md`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-42.1 (`ATT-3323`), establishing a clean separation of concerns across project guidelines. Historical ASPICE rules (formerly Rules 1–36) had accumulated UX/UI guidelines and Java/Kotlin architectural patterns into a single monolithic document.
3. **Root Reason for Existing Formulation**:
   - Rules 1–36 grew organically during successive sprints as new architectural safeguards and UI patterns were added. This conflated process enforcement (human gates, audit roles, git branches) with UI design (sliders, charts, map anchors) and code-level technical standards (DRY canonical formatters, lifecycle guards, database symmetry). The user specifically requested separating these distinct domains into a clean Document Triad to reduce cognitive load and prevent guideline erosion.
4. **Preservation of Core Invariants**:
   - Invariant 1 (ASPICE Core Process Invariants): Autonomous sprint execution, dual-agent verification gates (Gates 1–5), human decision gates, and strict GitFlow branch workflows remain 100% intact in `.agents/rules/aspice_governance.md`.
   - Invariant 2 (UI Invariants): UI standards (coordinate normalization, map anchors, slider descriptions, 9-language localization parity) remain binding and enforceable via `docs/design_guidelines.md`.
   - Invariant 3 (Coding Standards Invariants): Mandatory reuse of canonical formatters (`ATT-3310` / `Rule 36`), DTO symmetry, Android lifecycle safety, and MockK test requirements remain binding via `docs/coding_standards.md`.
   - Invariant 4 (Historical Traceability): A dedicated Historical Mapping Cross-Reference Table in `aspice_governance.md` maps every legacy rule (Rules 1–36) to its active home.

---

## 1. Executive Summary

This walkthrough fulfills Stage 5 verification for **ATT-3323**. Over successive sprints, `.agents/rules/aspice_governance.md` grew into a monolithic 36-rule document mixing agile process discipline, UI layout heuristics, and low-level Android coding standards.

ATT-3323 cleanly decouples this monolithic ruleset into the **Document Triad**:
1. **`.agents/rules/aspice_governance.md` (Process Governance)**:
   - Restructured into 4 logical tiers ordered strictly by importance:
     - **Tier 1 (Rules 1–6)**: Core Operating Invariants & Human Boundaries (Mandatory Refresh, Autonomous Sprint, Human Gates, Gate 3 Pre-Check, Ticket Creation vs Implementation, Autonomous Escalation).
     - **Tier 2 (Rules 7–11)**: Sprint Execution, Quality & Defect Discipline (Strict Sprint Scope & Rank, Zero Code Changes in Review, Lean Defect Recording with Screencaps, Install Before Review, Retro Before Merge).
     - **Tier 3 (Rules 12–14)**: Jira Standards & Versioning (Standardized Sub-Tasks, Clean Summaries, Fix Version Governance).
     - **Tier 4 (Rules 15–16)**: Exceptional & Environmental Protocols (GitFlow Hotfix & Dual-Branch Protocol, Human Prerequisites & Deferred Field-Testing).
   - Pruned obsolete rules: legacy Rule 3 (`RequestFeedback: false` workaround) and legacy Rule 24 (redundant build warning).
   - Consolidated duplicate pairs: 17+25 -> Rule 9; 7+19 -> Rule 14; 26+27 -> Rule 15; 22+28 -> Rule 16.
   - Preserved complete retrospective traceability via the **Historical Mapping Cross-Reference Table** (Rules 1–36).
2. **`docs/design_guidelines.md` (UI & Interaction Design Guidelines)**:
   - Houses all visual consistency baselines (Rule 23), settings slider scale directions (Rule 31), sub-entity chart coordinate normalization (Rule 32), map viewport anchor authority (Rule 33), and Jetpack Compose `@Preview` / 9-language localization standards.
3. **`docs/coding_standards.md` (Technical Architecture & Coding Standards)**:
   - Established as the single source of truth for SWE.1/SWE.2 software construction standards: Rule 36 DRY invariants (mandatory canonical formatters and unit math reuse, prohibition of inline manual formatting), DTO mapping symmetry (Rule 20), direct platform intents (Rule 21), defensive service lifecycle finalization and FGS permission gating (Rule 29), pre-flight diagnostics (Rule 30), gesture contract tests (Rule 34), clean hardware teardown (Rule 35), and test framework rules (prohibition of `returnDefaultValues = true`).
4. **`docs/project_protocol.md`**:
   - Updated to cross-reference the Document Triad.

---

## 2. Verification Results

### 2.1 Audit Checklist (`TST-PRO-019`)

| Checklist Item | Description | Result |
| :--- | :--- | :---: |
| **TST-PRO-019.1** | ASPICE Governance 4-tier structure (16 rules, obsolete removal, historical mapping) | **PASS** |
| **TST-PRO-019.2** | UI Design Guidelines consolidation audit (`docs/design_guidelines.md`) | **PASS** |
| **TST-PRO-019.3** | Technical Coding Standards architecture audit (`docs/coding_standards.md`) | **PASS** |
| **TST-PRO-019.4** | Document Triad cross-reference audit (`docs/project_protocol.md`) | **PASS** |
| **TST-PRO-019.5** | Clean-room regression test suite (`./gradlew testDebugUnitTest`) | **PASS** |

### 2.2 Requirement Governance Verification
- Command: `python3 tools/verify_requirement_governance.py --text-file docs/engineering/walkthroughs/ATT-3323_walkthrough.md --base-ref sprint/2026-42.1`
- Result: **0 ERRORS** (`Net-new requirement(s) detected: REQ-PRO-026. Bypassing archaeology check cleanly.`).

### 2.3 Full Clean-Room Regression Execution
- Command: `./gradlew testDebugUnitTest`
- Total Test Classes: 436 test classes (>2,130 unit tests)
- Failures: 0, Errors: 0, Skipped: 0
- Status: **100% PASS**

---

## 3. Human Gate & Next Steps

1. Gate 5 audit of `ATT-3421` by Agent 2 (`review_agent.py audit ATT-3421`).
2. Advance parent ticket `ATT-3323` to `Final Review (Human)`.
3. Commit all changes on branch `feature/ATT-3323`.
4. Switch to `sprint/2026-42.1`, merge `feature/ATT-3323` with `--no-ff`, and delete local feature branch.
5. Sprint 2026-42.1 complete! All sprint tickets are now in `Final Review (Human)`.
