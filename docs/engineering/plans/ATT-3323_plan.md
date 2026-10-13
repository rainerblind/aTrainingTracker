# Stage 3: Implementation Plan - ATT-3323: Separate ASPICE governance rules from UI design guidelines and technical coding standards

**Ticket**: [ATT-3323](https://rainerblind.atlassian.net/browse/ATT-3323)  
**Sub-task**: [ATT-3419](https://rainerblind.atlassian.net/browse/ATT-3419) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-3079](https://rainerblind.atlassian.net/browse/ATT-3079) (*Sprint 2026-42.1 Governance & Architecture*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-PRO-026`  
**Test Mapping**: `TST-PRO-019`  
**Branch**: `feature/ATT-3323`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Net-new requirement (`REQ-PRO-026`), establishing the canonical Document Triad architecture:
     - `.agents/rules/aspice_governance.md` (ASPICE Process Governance)
     - `docs/design_guidelines.md` (UI & Interaction Design Guidelines)
     - `docs/coding_standards.md` (Technical Architecture & Software Construction Standards)
   - Complements and harmonizes with `REQ-PRO-014` (ASPICE Quality Gates), `REQ-PRO-016` (Human Decision Gates), and `REQ-PRO-024` (Modular Skill Architecture).
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-39.1 (`ATT-728`): Created `.agents/rules/aspice_governance.md` to prevent premature implementation triggers in AI environments.
   - Sprints 2026-40.1 through 2026-42.1: Lessons learned from Sprint Retrospectives were codified as incremental numbered rules (Rules 1 to 36) in `.agents/rules/aspice_governance.md`.
3. **Root Reason for Existing Formulation**:
   - Accumulating rules linearly in a single file allowed rapid codification during retrospectives.
   - Over time, this mixed process governance, visual styling heuristics, and low-level Android coding standards in a single 28 KB file, burying crucial operating invariants (such as Rule 15 *Autonomous Full-Sprint Execution* and Rule 4 *Gate 3 Pre-Check*) beneath specialized rules.
   - Redundancies and obsolete rules (e.g. Rule 3, Rule 24) degraded cognitive ergonomics and inflated LLM context windows.
4. **Preservation of Core Invariants**:
   - Inviolable Human Decision Gates: Moving parent tickets to `Erledigt` remains an exclusive human prerogative; subtasks complete autonomously upon passing Gate audit.
   - Autonomous Sprint Mandate: AI agents execute the entire sprint backlog autonomously from ticket to ticket without intermediate pauses.
   - Gate 3 Pre-Check: Programmatic `check-gate` verification before editing production code in Stage 4 remains strictly enforced.
   - Canonical Formatting & DRY: Mandatory reuse of formatters (`DistanceFormatter`, `AltitudeFormatter`, etc.) and unit math functions (`MyHelper.mps2userUnit()`).
   - Retrospective Traceability: Historical cross-reference table mapping legacy rule numbers 1–36 to their reorganized locations.
   - 100% Clean-Room Test Suite Pass Rate.

---

## 1. Problem Description & Background

The current `.agents/rules/aspice_governance.md` has expanded to 36 rules across 28 KB, mixing three orthogonal domains:
1. ASPICE agile lifecycle process, subtask workflows, and human decision gates.
2. Android UI styling, Jetpack Compose layouts, charts, and slider UX heuristics.
3. Technical coding standards, DRY canonical formatter reuse, Android service lifecycle, and MockK test requirements.

This monolithic structure creates cognitive overload for developers and AI agents, diluting critical process invariants (like Rule 15 *Autonomous Sprint Execution* and Rule 4 *Gate 3 Pre-Check*) and introducing duplicated rules (e.g. 17+25, 7+19, 26+27).

This implementation plan establishes the **Document Triad**:
1. `.agents/rules/aspice_governance.md`: Strictly process governance, reordered into 4 importance tiers (16 consolidated rules) with a legacy rule mapping table.
2. `docs/design_guidelines.md`: UI and design guidelines (migrating Rules 23, 31, 32, 33 and protocol sections 4.A/4.B).
3. `docs/coding_standards.md`: Software engineering and coding invariants (migrating Rules 20, 21, 29, 30, 34, 35, 36 and test framework invariants).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRO-026` (*Document Triad Architecture: Separation of ASPICE Governance, UI Design Guidelines, and Technical Coding Standards*)
* **Test Mapping**: `TST-PRO-019` (*Document Triad Architecture, Governance Streamlining, and Invariant Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature and test suites continue to pass cleanly (`./gradlew testDebugUnitTest`).
2. **Inviolable Human Decision Gates**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`. Subtasks transition directly to `Erledigt` via transition `freigabe` once Agent 2 passes the gate audit.
3. **Autonomous Sprint Mandate**: AI agents execute the entire sprint backlog autonomously from ticket to ticket without intermediate pauses.
4. **Gate 3 Pre-Check Before Production Code**: Programmatically verify Impl-Plan subtask is `Erledigt` via `python3 tools/jira_util.py check-gate <KEY>` before modifying code in Stage 4.
5. **DRY & Canonical Formatters Invariant**: All distance, altitude, speed, pace, and time presentation MUST route through canonical formatters (`DistanceFormatter`, `AltitudeFormatter`, `SpeedFormatter`, `PaceFormatter`, Compose `LocalMetricFormatter`), and unit math MUST route through `MyHelper.mps2userUnit()` / `UserUnit2mps()`.
6. **Retrospective Traceability**: Historical mapping cross-reference table mapping legacy rule numbers (1 through 36) to their new rule numbers or destination files.

---

## 4. Proposed Architectural Changes

### Component 1: `.agents/rules/aspice_governance.md`
- Restructure into 4 descending importance tiers:
  - **Tier 1: Non-Negotiable Operational & Gate Governance (Rules 1–6)**:
    - Rule 1: Obey the Rules & Mandatory Triad Refresh
    - Rule 2: Autonomous Full-Sprint Execution
    - Rule 3: Human Decision Gates & Terminal States
    - Rule 4: Gate 3 Pre-Check Before Production Code
    - Rule 5: Strict Separation of Ticket Creation vs. Implementation
    - Rule 6: Autonomous In-Sprint Escalation Protocol
  - **Tier 2: Sprint Execution, Quality & Defect Discipline (Rules 7–11)**:
    - Rule 7: Strict Sprint Scope & Backlog Rank Authority
    - Rule 8: Zero Code Changes During Sprint Review
    - Rule 9: Lean Defect Recording with Immediate Evidence Capture (consolidating 17 & 25)
    - Rule 10: Mandatory Device Deployment Before Review
    - Rule 11: Retrospective & Governance Documentation Before Merge
  - **Tier 3: Standardized Project & Jira Hygiene (Rules 12–14)**:
    - Rule 12: Standardized Sub-Task Naming Scheme
    - Rule 13: Clean Ticket Summaries
    - Rule 14: Fix Version Governance (consolidating 7 & 19)
  - **Tier 4: Exceptional & Environmental Protocols (Rules 15–16)**:
    - Rule 15: Formal GitFlow Hotfix & Dual-Branch Protocol (consolidating 26 & 27)
    - Rule 16: Human Prerequisites & Deferred Field-Testing (consolidating 22 & 28)
- Prune obsolete rules: Rule 3 (RequestFeedback: false workaround) and Rule 24 (redundant signingReport warning).
- Append Historical Mapping Cross-Reference Table (Rules 1–36).

### Component 2: `docs/design_guidelines.md`
- Integrate visual and layout rules:
  - Section 5: Visual Consistency Baseline (migrated from Rule 23).
  - Section 3.1: Explanatory Scale Direction in Settings Sliders (migrated from Rule 31).
  - Section 2.1: Coordinate Space Normalization for Sub-Entity Inspection Charts (migrated from Rule 32).
  - Section 6.1: Unified Viewport Anchor Authority for Map Tracking (migrated from Rule 33).
  - Jetpack Compose multi-state `@Preview` loops and 9-language localization parity.

### Component 3: `docs/coding_standards.md`
- Create authoritative technical standards document:
  - Section 1.1: Rule 36: Single Source of Truth & DRY — Shared Code Over Duplication (canonical formatters, unit math).
  - Section 1.2: Rule 34: Chesterton's Fence for Empty Callback Lambdas & Gesture Contract Tests.
  - Section 2.1: Rule 20: Database & DTO Mapping Symmetry (`fromCursor`).
  - Section 3.1: Rule 21: Specific Direct Platform Intents Over Generic App Settings.
  - Section 3.2: Rule 29: Defensive Service Lifecycle Finalization & FGS Permission Gating.
  - Section 3.3: Rule 35: Clean Hardware Teardown and Orphan Defense on Persistence Deletion.
  - Section 4.1: Rule 30: Developer Tool Host Pre-Flight Diagnostics.
  - Section 5: Test Framework Invariants (`returnDefaultValues = true` prohibition, Stage 4 unit tests vs Stage 5 clean-room regression).

### UI Consistency (Rule 23)
* No UI changes (architecture and governance documentation refactoring).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `.agents/rules/aspice_governance.md`
* Restructure into 4 descending importance tiers with 16 consolidated rules.
* Prune obsolete rules (legacy 3 and 24).
* Append the Historical Mapping Cross-Reference Table for Rules 1–36.

### Step 2: Update `docs/design_guidelines.md`
* Incorporate visual consistency baseline (Rule 23), settings sliders explanatory hints (Rule 31), chart coordinate normalization (Rule 32), map viewport anchor authority (Rule 33), and Compose `@Preview` / 9-language localization standards.

### Step 3: Create `docs/coding_standards.md`
* Populate with Rule 36 DRY invariants (mandatory canonical formatter and unit math reuse), DTO mapping symmetry (Rule 20), direct platform intents (Rule 21), defensive service lifecycle (Rule 29), pre-flight diagnostics (Rule 30), gesture contract tests (Rule 34), hardware teardown (Rule 35), and test framework rules.

### Step 4: Align `docs/project_protocol.md` and Skills
* Verify references to the Document Triad across `docs/project_protocol.md` and `.agents/skills/`.

### Step 5: Verification & Governance Audit
* Run `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-42.1`.
* Execute full clean-room unit test suite: `./gradlew testDebugUnitTest`.

---

## 6. Verification & Rollback Plan

* **Verification**: Audit against TST-PRO-019 checklist (16 rules, 4 tiers, legacy table, design guidelines, coding standards) followed by `./gradlew testDebugUnitTest`.
* **Rollback**: Isolated branch `feature/ATT-3323` allows clean rollback to `sprint/2026-42.1` without polluting `develop`.
