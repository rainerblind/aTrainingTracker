# Stage 4: Implementation Summary - ATT-3323: Separate ASPICE governance rules from UI design guidelines and technical coding standards

**Ticket**: [ATT-3323](https://rainerblind.atlassian.net/browse/ATT-3323)  
**Sub-task**: [ATT-3420](https://rainerblind.atlassian.net/browse/ATT-3420) (`[Implementation]`)  
**Parent Epic**: [ATT-3079](https://rainerblind.atlassian.net/browse/ATT-3079) (*Sprint 2026-42.1 Governance & Architecture*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-PRO-026`  
**Test Mapping**: `TST-PRO-019`  
**Branch**: `feature/ATT-3323`  
**Author**: AI Agent 1 (Software Construction Engineer)  
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

## 1. Summary of Changes

### 1.1 `.agents/rules/aspice_governance.md` (Consolidated 16 Process Governance Rules)
- Restructured governance rules into 4 descending importance tiers:
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
    - Rule 9: Lean Defect Recording with Immediate Evidence Capture (consolidating legacy Rules 17 and 25)
    - Rule 10: Mandatory Device Deployment Before Review
    - Rule 11: Retrospective & Governance Documentation Before Merge
  - **Tier 3: Standardized Project & Jira Hygiene (Rules 12–14)**:
    - Rule 12: Standardized Sub-Task Naming Scheme
    - Rule 13: Clean Ticket Summaries
    - Rule 14: Fix Version Governance (consolidating legacy Rules 7 and 19)
  - **Tier 4: Exceptional & Environmental Protocols (Rules 15–16)**:
    - Rule 15: Formal GitFlow Hotfix & Dual-Branch Protocol (consolidating legacy Rules 26 and 27)
    - Rule 16: Human Prerequisites & Deferred Field-Testing (consolidating legacy Rules 22 and 28)
- Removed obsolete rules: legacy Rule 3 (redundant agent-1 prompt text) and legacy Rule 24 (redundant build failure troubleshooting).
- Added the authoritative **Historical Mapping Cross-Reference Table** detailing the exact location for all legacy rules 1–36 across the Document Triad.

### 1.2 `docs/design_guidelines.md` (Domain UI & UX Design Guidelines)
- Integrated dedicated sections migrated from the governance document:
  - Section 5: **Visual Consistency Baseline** (migrated from Rule 23).
  - Section 3.1: **Explanatory Scale Direction in Settings Sliders** (migrated from Rule 31).
  - Section 2.1: **Coordinate Space Normalization for Sub-Entity Inspection Charts** (migrated from Rule 32).
  - Section 6.1: **Unified Viewport Anchor Authority for Map Tracking** (migrated from Rule 33).
  - Maintained core Jetpack Compose and Material 3 design standards, 9-language localization parity, and positional format specifier rules.

### 1.3 `docs/coding_standards.md` (Technical Architecture & Engineering Standards)
- Authored new comprehensive engineering guide with sections:
  - Section 1.1: **Single Source of Truth & DRY — Shared Code Over Duplication (Rule 36)**, mandating canonical formatters (`FormatterUtils`, `SpeedUtils`, `ElevationUtils`, `SensorFilterPreferencesHelper`) and prohibiting inline manual formatting.
  - Section 1.2: **Chesterton's Fence for Empty Callback Lambdas & Gesture Contract Tests (Rule 34)**.
  - Section 2.1: **Database & DTO Mapping Symmetry (Rule 20)**.
  - Section 3.1: **Specific Direct Platform Intents Over Generic App Settings (Rule 21)**.
  - Section 3.2: **Defensive Service Lifecycle Finalization & FGS Permission Gating (Rule 29)**.
  - Section 3.3: **Clean Hardware Teardown and Orphan Defense on Persistence Deletion (Rule 35)**.
  - Section 4.1: **Developer Tool Host Pre-Flight Diagnostics (Rule 30)**.
  - Section 5: **Testing Standards & Test Doubles (MockK)**, enforcing strict prohibition of `returnDefaultValues = true`.

### 1.4 Living Documentation & Test Suite
- Updated `docs/requirements.md` adding formal specification `REQ-PRO-026`.
- Updated `docs/tests.md` adding test specification `TST-PRO-019`.
- Clean-room test suite executed: `./gradlew testDebugUnitTest` passed with 100% success (0 failures, 0 errors).

---

## 2. Verification Summary

| Test Case | Description | Result |
|:---|:---|:---:|
| `TST-PRO-019.1` | ASPICE Governance 4-tier structure audit (16 rules, obsolete removal, historical mapping) | **PASS** |
| `TST-PRO-019.2` | UI Design Guidelines consolidation audit (`docs/design_guidelines.md`) | **PASS** |
| `TST-PRO-019.3` | Technical Coding Standards architecture audit (`docs/coding_standards.md`) | **PASS** |
| `TST-PRO-019.4` | Document Triad cross-reference audit | **PASS** |
| `TST-PRO-019.5` | Clean-room test suite execution (`./gradlew testDebugUnitTest`) | **PASS** |
