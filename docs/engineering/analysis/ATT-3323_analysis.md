# Stage 1: Problem Domain & Root Cause Analysis - ATT-3323: Separate ASPICE governance rules from UI design guidelines and technical coding standards

**Ticket**: [ATT-3323](https://rainerblind.atlassian.net/browse/ATT-3323)  
**Sub-task**: [ATT-3417](https://rainerblind.atlassian.net/browse/ATT-3417) (`[Analysis]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process & Engineering Workflow*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-PRO-026` (net-new)  
**Test Mapping**: `TST-PRO-019` (net-new)  
**Branch**: `feature/ATT-3323`  
**Author**: AI Agent 1 (Problem Domain & Root Cause Analyst)  
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
   - Sprints 2026-40.1 through 2026-42.1: As the agile team conducted Sprint Retrospectives, lessons learned were continuously codified directly as incremental rules (Rule 1 through Rule 36) in `.agents/rules/aspice_governance.md`.
3. **Root Reason for Existing Formulation**:
   - Historically, appending retrospective lessons directly to `aspice_governance.md` provided a single, fast location to persist learnings.
   - However, over 40+ sprints, this linear accumulation mixed three fundamentally distinct engineering concerns:
     1. Agile process, Jira state transitions, human boundaries, and sprint ceremonies (e.g. Rules 1, 2, 4, 11, 15).
     2. UI styling, coordinate spaces, slider scale hints, and Compose layout patterns (e.g. Rules 23, 31, 32, 33).
     3. Software engineering, Android platform APIs, database symmetries, and formatting DRY invariants (e.g. Rules 20, 21, 29, 30, 34, 35, 36).
   - Furthermore, crucial operating invariants (such as Rule 15 *Autonomous Full-Sprint Execution* or Rule 4 *Gate 3 Pre-Check*) were buried deep in the document beneath specialized historical rules. Redundancies also accumulated (e.g. defect recording in 17 & 25, fix versions in 7 & 19, hotfixes in 26 & 27), and obsolete rules persisted (Rule 3 IDE feedback workaround, Rule 24 redundant signing report).
4. **Preservation of Core Invariants**:
   - Inviolable Human Decision Gates: Parent tickets strictly terminate at `Final Review (Human)`. AI agents must NEVER transition parent tickets to `Erledigt`.
   - Autonomous Sprint Mandate: AI agents execute the entire sprint backlog autonomously without stopping or asking permission.
   - Gate 3 Pre-Check: Programmatic `check-gate` verification before editing production code in Stage 4 remains strictly enforced.
   - Canonical Formatting & DRY: Mandatory reuse of formatters (`DistanceFormatter`, `AltitudeFormatter`, etc.) and unit math functions (`MyHelper.mps2userUnit()`).
   - Retrospective Traceability: Historical cross-reference table mapping legacy rule numbers 1–36 to their reorganized locations.
   - 100% Clean-Room Test Suite Pass Rate.

---

## 1. Problem Statement & Motivation

`.agents/rules/aspice_governance.md` is the primary rulebook loaded into context by AI agents at every ticket start (mandated by Rule 1). Over 40+ agile sprints, the document expanded to 36 numbered rules spanning 28 KB.

### 1.1 Core Problems Identified
1. **Domain Entanglement (Process vs. UI vs. Code)**:
   - Process rules (e.g., ticket transitions, sprint ceremonies) sit adjacent to UI guidelines (e.g., slider scale hints, map viewport padding) and Android software construction rules (e.g., FGS permissions, MockK cursor fixtures, DTO symmetry).
   - When an agent is implementing algorithmic business logic or database queries, UI and process guidelines needlessly bloat the context window.
   - Conversely, when designing UI components, UI rules are scattered between `design_guidelines.md` and `aspice_governance.md`.
2. **Suboptimal Importance Ordering**:
   - The top of the file currently contains Rule 1 (Refresh), Rule 2 (Human Gates), Rule 3 (obsolete IDE feedback workaround), and Rule 4 (Gate 3 Pre-Check).
   - Rule 15 (*Autonomous Full-Sprint Execution*) and Rule 13 (*Unattended Autonomous Escalation*)—the two most vital instructions preventing unwanted agent pauses—are located in the middle of the file.
3. **Duplication & Clutter**:
   - Defect logging is defined across Rule 17 and Rule 25.
   - Fix version rules are defined across Rule 7 and Rule 19.
   - Hotfix branching and integration are split between Rule 26 and Rule 27.
   - Human prerequisites and deferred field tests are split between Rule 22 and Rule 28.
   - Obsolete rules remain: Rule 3 (IDE `RequestFeedback: false` workaround from legacy harness) and Rule 24 (`./gradlew signingReport` avoidance).

---

## 2. Forensic Investigation & Target Architecture: The Document Triad

To achieve structural clarity, high cognitive ergonomics, and clean domain isolation, the engineering knowledge base must be organized into a **Document Triad**:

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                           THE DOCUMENT TRIAD                             │
├─────────────────────────┬────────────────────────┬───────────────────────┤
│  1. ASPICE GOVERNANCE   │   2. UI DESIGN GUIDE   │  3. CODING STANDARDS  │
│  (.agents/rules/        │   (docs/               │  (docs/               │
│   aspice_governance.md) │    design_guidelines)  │   coding_standards)   │
├─────────────────────────┼────────────────────────┼───────────────────────┤
│ • Process & Gates       │ • Visual Harmony       │ • SWE.1 / SWE.2 Arch  │
│ • Sprint Ceremonies     │ • Material 3 Tokens    │ • DRY & Reuse (R36)   │
│ • Jira State Machine    │ • Viewport & Maps      │ • Canonical Formatters│
│ • Human Boundaries      │ • Slider Scale Hints   │ • DTO & DB Symmetry   │
│ • Escalation Protocol   │ • Compose @Previews    │ • Lifecycle & FGS API │
│ • GitFlow Protocol      │ • 9-Lang Localization  │ • Clean Teardown      │
└─────────────────────────┴────────────────────────┴───────────────────────┘
```

### 2.1 Reorganization of `.agents/rules/aspice_governance.md` into 4 Importance Tiers

The revised `aspice_governance.md` will strictly contain **16 consolidated process rules** grouped into 4 descending importance tiers:

#### Tier 1: Core Operating Invariants & Human Boundaries (Top of File)
- **Rule 1: Obey the Rules & Mandatory Triad Refresh**: Mandatory re-reading of governance, project protocol, active stage skill, and design/coding standards. Guaranteed resumption across compactions.
- **Rule 2: Autonomous Full-Sprint Execution**: Autonomous execution across the entire sprint backlog from ticket to ticket without intermediate pauses or permission prompts.
- **Rule 3: Human Decision Gates & Terminal States**: Parent tickets strictly terminate at `Final Review (Human)`. AI agents must NEVER transition parent tickets to `Erledigt`. Subtasks complete autonomously upon passing Gate audit.
- **Rule 4: Gate 3 Pre-Check Before Production Code**: Mandatory execution of `python3 tools/jira_util.py check-gate <Subtask>` before modifying production code in Stage 4.
- **Rule 5: Strict Separation of Ticket Creation vs. Implementation**: Creating an issue stops immediately. Never start realizing it autonomously.
- **Rule 6: Autonomous In-Sprint Escalation Protocol**: When blocked on questions/decisions, assign sub-task to the Human (`rainer`) with an explicit Jira comment and proceed immediately to the next sprint ticket.

#### Tier 2: Sprint Ceremonies & Scope Governance
- **Rule 7: Strict Sprint Scope & Backlog Rank Authority**: Agents cannot pull tickets into sprints autonomously. Process tickets strictly sequentially in backlog rank order (`ORDER BY rank ASC`).
- **Rule 8: Zero Code Changes During Sprint Review**: Never modify code during Ceremony 2 (Joint Review). All feedback and adjustments must become new backlog tickets for the next sprint.
- **Rule 9: Lean Defect Recording with Immediate Evidence Capture**: Consolidates legacy Rules 17 & 25. Record defects without deep root-cause analysis during review; capture on-device screenshots (`adb exec-out screencap`) and attach to the Jira ticket.
- **Rule 10: Mandatory Device Deployment Before Review**: Compile and install the sprint build (`./gradlew installDebug`) onto the physical test device (Google Pixel 10) before starting Joint Review.
- **Rule 11: Retrospective & Governance Documentation Before Merge**: Complete and commit sprint retro and governance updates onto the sprint branch before merging into `develop`.

#### Tier 3: Jira Standards & Versioning
- **Rule 12: Standardized Sub-Task Naming Scheme**: Canonical stage prefixes (`[Analysis]`, `[Test-Spec]`, `[Impl-Plan]`, `[Implementation]`, `[Test]`) including parent summary.
- **Rule 13: Clean Ticket Summaries**: No bracketed category prefixes (`[Bug]`, `[Feature]`) or Epic names in ticket titles.
- **Rule 14: Fix Version Governance**: Consolidates legacy Rules 7 & 19. No `Lösungsversion` on sub-tasks; parent tickets only receive Fix Version when accepted and finished. Emergency hotfix version isolation.

#### Tier 4: Specialized Workflows
- **Rule 15: Formal GitFlow Hotfix & Dual-Branch Protocol**: Consolidates legacy Rules 26 & 27. Branching from master, cherry-picking/merging into active sprint and develop.
- **Rule 16: Human Prerequisites & Deferred Field-Testing**: Consolidates legacy Rules 22 & 28. Identify human-only needs during planning; handle outdoor ride tests across sprint boundaries.

### 2.2 Pruning Obsolete Rules
- **Prune Legacy Rule 3**: *Artifact Metadata Policy (`RequestFeedback: false`)*. This was an ephemeral workaround for a 2025 IDE test harness that fired auto-approval stop hooks. In the current Antigravity environment, this is no longer active or relevant.
- **Prune Legacy Rule 24**: *Avoid Redundant Diagnostic Builds (`./gradlew signingReport`)*. This is general developer diligence and does not warrant a top-level governance rule.

---

## 3. UI Guidelines Consolidation (`docs/design_guidelines.md`)

Visual and layout mandates from `aspice_governance.md` and `project_protocol.md` migrate into `docs/design_guidelines.md`:
1. **Rule 23 (Visual Consistency Baseline)**: Already forms Section 5 of `design_guidelines.md`. Enhanced with explicit reference-matching criteria.
2. **Rule 31 (Explanatory Scale Direction in Settings Sliders)**: Section 3.1 of `design_guidelines.md`.
3. **Rule 32 (Coordinate Space Normalization for Sub-Entity Inspection Charts)**: Section 2.1 of `design_guidelines.md`.
4. **Rule 33 (Unified Viewport Anchor Authority for Map Tracking)**: Section 6.1 of `design_guidelines.md`.
5. **Sections 4.A & 4.B from `project_protocol.md`**: Jetpack Compose `@Preview` loops, AMOLED dark theme tokens, 9-language localization parity, and positional format specifiers (`%1$s`).

---

## 4. Software Construction Standards Creation (`docs/coding_standards.md`)

A new living document `docs/coding_standards.md` is created to authoritatively house SWE.1 / SWE.2 software construction invariants:
1. **Rule 36: Single Source of Truth & DRY (Don't Repeat Yourself) — Shared Code Over Duplication**:
   - Mandatory Reuse: Shared utilities, repositories, and composables over parallel code.
   - Canonical Formatters & Unit Parity: All distance, altitude, speed, pace, and time presentation MUST route through canonical formatters (`DistanceFormatter`, `AltitudeFormatter`, `SpeedFormatter`, `PaceFormatter`, and Compose `LocalMetricFormatter`), and unit math MUST route through `MyHelper.mps2userUnit()` / `UserUnit2mps()`. Inline template formatting (e.g. `String.format("%.1f km", ...)` or `"${meters} m"`) and manual unit math are strictly forbidden.
   - Root Out Divergent Implementations: Proactively inspect existing code before writing new logic; eliminate parallel implementations.
2. **Rule 20: Database & DTO Mapping Symmetry**: Single-item and batch `fromCursor` overloads must map fields symmetrically.
3. **Rule 21: Specific Direct Platform Intents Over Generic App Settings**: Direct system intents (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, permission dialogs) over generic app settings.
4. **Rule 29: Defensive Service Lifecycle Finalization & FGS Permission Gating**: Verify runtime permissions before requesting specialized FGS types (`HEALTH`, `CONNECTED_DEVICE`). Finalize sessions (`FINISHED = 1`) within guaranteed `try/finally` blocks and SQLite transactions.
5. **Rule 30: Developer Tool Host Pre-Flight Diagnostics**: Host hardware interaction tools must execute startup self-diagnostics.
6. **Rule 34: Chesterton's Fence for Empty Callback Lambdas & Gesture Contract Tests**: Never pass `{}` without auditing internal call sites; intent-explicit callback signatures; gesture contract tests.
7. **Rule 35: Clean Hardware Teardown and Orphan Defense on Persistence Deletion**: Disconnect hardware via `BANALService` on database deletion; discard orphaned IDs defensively in ViewModels.
8. **Test Framework Invariants**:
   - Prohibition of `testOptions { unitTests.returnDefaultValues = true }`.
   - Execution Phasing: Stage 4 targeted module unit tests (5–15s) vs. Stage 5 clean-room regression (`./gradlew testDebugUnitTest`).

---

## 5. Historical Mapping Cross-Reference Table

To preserve complete backward traceability for past sprint retrospectives (which cite legacy rule numbers like "Rule 15", "Rule 23", "Rule 36"), `aspice_governance.md` will conclude with a formal mapping table:

| Legacy Rule # | Original Title | New Location / Status |
| :--- | :--- | :--- |
| **Rule 1** | Obey the Rules: Mandatory Rules, Protocols & Skills Refresh | `aspice_governance.md` -> **Rule 1** (Tier 1) |
| **Rule 2** | Absolute Primacy of Jira Governance over IDE Hooks | `aspice_governance.md` -> **Rule 3** (Tier 1) |
| **Rule 3** | Artifact Metadata Policy (`RequestFeedback: false`) | **Pruned** (obsolete legacy harness workaround) |
| **Rule 4** | Mandatory Programmatic Pre-Check Before Stage 4 | `aspice_governance.md` -> **Rule 4** (Tier 1) |
| **Rule 5** | Strict Prohibition on Autonomous Sprint Scope Alterations | `aspice_governance.md` -> **Rule 7** (Tier 2) |
| **Rule 6** | Strict Separation of Ticket Creation vs. Implementation | `aspice_governance.md` -> **Rule 5** (Tier 1) |
| **Rule 7** | Prohibition of "Lösungsversion" on Sub-Tasks | `aspice_governance.md` -> **Rule 14** (Tier 3) |
| **Rule 8** | Continuous In-Sprint Issue Logging in Review & Retro Ticket | `aspice_governance.md` -> Section 2 Ceremonies |
| **Rule 9** | Strict Single-Ticket Focus During Joint Review | `aspice_governance.md` -> Section 2 Ceremonies |
| **Rule 10** | Sub-Task Summary Standard (Canonical Prefixes) | `aspice_governance.md` -> **Rule 12** (Tier 3) |
| **Rule 11** | Absolute Prohibition on Code Modifications During Review | `aspice_governance.md` -> **Rule 8** (Tier 2) |
| **Rule 12** | Retrospective & Governance Documentation Before Merge | `aspice_governance.md` -> **Rule 11** (Tier 2) |
| **Rule 13** | Unattended Autonomous In-Sprint Escalation Protocol | `aspice_governance.md` -> **Rule 6** (Tier 1) |
| **Rule 14** | Strict Ticket Rank Ordering Enforcement During Ceremonies | `aspice_governance.md` -> **Rule 7** (Tier 2) |
| **Rule 15** | Autonomous Full-Sprint Execution Mandate | `aspice_governance.md` -> **Rule 2** (Tier 1) |
| **Rule 16** | Mandatory Device Deployment Invariant Before Sprint Review | `aspice_governance.md` -> **Rule 10** (Tier 2) |
| **Rule 17** | Lean Defect Recording During Sprint Review | `aspice_governance.md` -> **Rule 9** (Tier 2, consolidated with R25) |
| **Rule 18** | Clean Ticket Summaries (No Category/Epic Prefixes) | `aspice_governance.md` -> **Rule 13** (Tier 3) |
| **Rule 19** | Prohibition of Premature Fix Version Assignment | `aspice_governance.md` -> **Rule 14** (Tier 3, consolidated with R7) |
| **Rule 20** | Database & DTO Mapping Symmetry | `docs/coding_standards.md` §2.1 |
| **Rule 21** | Specific Direct Platform Intents Over Generic App Settings | `docs/coding_standards.md` §3.1 |
| **Rule 22** | Human Prerequisite Detection During Sprint Planning | `aspice_governance.md` -> **Rule 16** (Tier 4, consolidated with R28) |
| **Rule 23** | UI Design Consistency ("Look Like the Rest of the App") | `docs/design_guidelines.md` §5 |
| **Rule 24** | Avoid Redundant Diagnostic Builds (`signingReport`) | **Pruned** (basic developer diligence) |
| **Rule 25** | Lean Defect Recording Protocol with Immediate Evidence | `aspice_governance.md` -> **Rule 9** (Tier 2, consolidated with R17) |
| **Rule 26** | Formalized GitFlow Hotfix Workflow & Rebase Protocol | `aspice_governance.md` -> **Rule 15** (Tier 4, consolidated with R27) |
| **Rule 27** | Dual Branch Integration for Production Hotfix FixVersions | `aspice_governance.md` -> **Rule 15** (Tier 4, consolidated with R26) |
| **Rule 28** | Deferred Review State for Field-Tested Features | `aspice_governance.md` -> **Rule 16** (Tier 4, consolidated with R22) |
| **Rule 29** | Defensive Service Lifecycle Finalization & FGS Gating | `docs/coding_standards.md` §3.2 |
| **Rule 30** | Developer Tool Host Pre-Flight Diagnostics | `docs/coding_standards.md` §4.1 |
| **Rule 31** | Explanatory Scale Direction in Settings Sliders | `docs/design_guidelines.md` §3.1 |
| **Rule 32** | Coordinate Space Normalization for Sub-Entity Inspection | `docs/design_guidelines.md` §2.1 |
| **Rule 33** | Unified Viewport Anchor Authority for Map Tracking | `docs/design_guidelines.md` §6.1 |
| **Rule 34** | Chesterton's Fence for Empty Callback Lambdas | `docs/coding_standards.md` §1.2 |
| **Rule 35** | Clean Hardware Teardown and Orphan Defense on Deletion | `docs/coding_standards.md` §3.3 |
| **Rule 36** | Single Source of Truth & DRY (Shared Code Over Duplication) | `docs/coding_standards.md` §1.1 |

---

## 6. Scope Bounding

### In-Scope
1. Reorganize `.agents/rules/aspice_governance.md` into 4 Importance Tiers (16 consolidated rules) and add the legacy mapping table.
2. Create `docs/coding_standards.md` consolidating all SWE.1/SWE.2 architectural invariants, canonical formatters, and testing rules.
3. Update `docs/design_guidelines.md` incorporating visual and layout guidelines from `project_protocol.md` and `aspice_governance.md`.
4. Update `docs/project_protocol.md` and `.agents/skills/` to reference the Document Triad.
5. Define `REQ-PRO-026` in `docs/requirements.md` and `TST-PRO-019` in `docs/tests.md`.
6. Verify governance scripts (`verify_requirement_governance.py`) and full test suite (`./gradlew testDebugUnitTest`).

### Out-of-Scope
1. Modifying production Android application source code or resources (`app/src/main/...`).
2. Modifying runtime database schemas, queries, or UI layouts.
3. Adding new external dependencies or build tools.
