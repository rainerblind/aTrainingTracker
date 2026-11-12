# Stage 1 Analysis: ATT-6132 - Backfill test evidence for UI Design System requirements

**Ticket**: [ATT-6132](https://rainerblind.atlassian.net/browse/ATT-6132)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `subagent-Lane-3-Agent---ATT-6132-self-d4f75212`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

During the ASPICE 4.0 pre-assessment baseline (develop @ sprint/2026-45.11, Remediation ID: A-08.6, ASPICE SWE.6 BP4, SWE.1 BP5), the UI Design System requirement and test specification shards (`docs/requirements/req_ui_design_system.md` and `docs/tests/tst_ui_design_system.md`) were identified as containing dangling references, bidirectional citation gaps, ungrounded verified claims, and missing metadata attributes:
* **Dangling TST References (5 occurrences)**:
  - `REQ-UI-033`, `REQ-UI-035`, `REQ-UI-036` cite `TST-UI-041` (Period Peak Markers Audit), which was omitted from `tst_ui_design_system.md`.
  - `REQ-UI-042` cites `TST-UI-044` (Mappable Device Items), which was omitted from `tst_ui_design_system.md`.
  - `REQ-UI-114` cites `TST-INT-007` (Original Sport Icons Visual Identity), which was omitted from `tst_process_stability.md`.
  - Currently allowlisted in `LEGACY_DANGLING_TST_REFS` and `LEGACY_UNGROUNDED_VERIFIED_REQS` in `tools/verify_requirement_governance.py`.
* **Dangling REQ References (12 occurrences, 8 unique REQ IDs)**:
  - `TST-UNT-002` cites `REQ-UI-002` (TimeFormatter Display Formatting).
  - `TST-STR-001` cites `REQ-UI-008` (Structured Architecture & Elevation Profile Linking).
  - `TST-UI-029`, `TST-INT-004`, `TST-STR-003` cite `REQ-UI-009` (Session Isolation & Workout Transition UI).
  - `TST-INT-005`, `TST-INT-006` cite `REQ-UI-010` (Navigation Drawer Architecture & Tab Configuration Layout).
  - `TST-UI-071` cites `REQ-UI-062` (App Branding & Header Presentation).
  - `TST-UI-088` cites `REQ-UI-135` (Multi-Dimensional Favorite Tracks Filtering & Modal Sheet).
  - `TST-UI-070`, `TST-UI-118` cite `REQ-UI-166` (Lieblingsorte UI/UX Harmonization & Favorite Locations Management).
  - `TST-UI-249` cites `REQ-UI-289` (Context-Aware Route Selection: Start Proximity & In-Ride Corridor Auto-Detection).
  - Currently allowlisted in `LEGACY_DANGLING_REQ_REFS`.
* **Bidirectional Traceability Gaps (Category 4 Gaps)**: 41 test specifications in `tst_ui_design_system.md` declare requirements that do not reciprocally cite the test back in their `Tests:` field.
* **Missing Mandatory Metadata Attributes**: All 444 requirements in `req_ui_design_system.md` lack `Priority`, `Type`, and `Verification Method` attributes required by `REQ-PRO-077` (ATT-6136).
* **Shard Header Count Drift**: Header count in `req_ui_design_system.md` (`Total Requirements: 372`) is stale relative to actual requirements (444 existing + 8 restored = 452).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Root Causes**:
  1. *Omission during Monolith Sharding & Historical Restructuring (`ATT-4594`)*: Headings for `REQ-UI-002`, `REQ-UI-008`, `REQ-UI-009`, `REQ-UI-010`, `REQ-UI-062`, `REQ-UI-135`, `REQ-UI-166`, and `REQ-UI-289` were accidentally excised or unrendered during past refactorings, leaving their corresponding test specifications dangling.
  2. *Asymmetric Link Maintenance*: 41 test specifications in `tst_ui_design_system.md` declared parent requirements, but their parent requirements only cited primary tests, leaving secondary tests unreciprocated.
  3. *Deferred Attribute Population*: `REQ-PRO-077` introduced mandatory attributes across living documents, but deferred shard population to the A-08.x series.
* **Independent Hypothesis Verification (4-Augen-Prinzip)**:
  - `python3 tools/verify_requirement_governance.py --coverage` reports 41 Category 4 gaps in the UI domain.
  - `python3 tools/verify_requirement_governance.py --enforce-unique --enforce-links` flags exactly the 5 dangling TST citations and 12 dangling REQ citations identified above.
  - `python3 tools/verify_requirement_governance.py --check-evidence` flags `REQ-UI-033`, `REQ-UI-035`, `REQ-UI-036`, `REQ-UI-042`, and `REQ-UI-114` as ungrounded because of their dangling test citations.
  - Automated unit tests in `app/src/test/` already exist and pass for all UI components and design system contracts (e.g. `TimeFormatterTest`, `KnownLocationsScreenTest`, `RouteSelectorViewModelTest`, `ThemeColorCentralizationAuditTest`, `GlobalDeleteContextMenuAuditTest`).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Backfill the 8 missing requirement specifications (`REQ-UI-002`, `REQ-UI-008`, `REQ-UI-009`, `REQ-UI-010`, `REQ-UI-062`, `REQ-UI-135`, `REQ-UI-166`, `REQ-UI-289`) in `docs/requirements/req_ui_design_system.md` with full attributes, rationales, invariants, and acceptance criteria.
  2. Backfill test specifications `TST-UI-041` (Period Peak Markers Audit) and `TST-UI-044` (Mappable Device Items) in `docs/tests/tst_ui_design_system.md`, and `TST-INT-007` (Original Sport Icons Visual Identity) in `docs/tests/tst_process_stability.md`.
  3. Resolve all 41 bidirectional Category 4 gaps in `docs/requirements/req_ui_design_system.md` by ensuring every cited requirement references all applicable TSTs.
  4. Backfill mandatory attributes (`Priority`, `Type`, `Verification Method`) across all 452 requirements in `req_ui_design_system.md`.
  5. Prune all resolved UI references from `LEGACY_DANGLING_TST_REFS`, `LEGACY_DANGLING_REQ_REFS`, and `LEGACY_UNGROUNDED_VERIFIED_REQS` in `tools/verify_requirement_governance.py`.
  6. Register `REQ-PRO-085` and `TST-PRO-085` in `req_process_stability.md` and `tst_process_stability.md`.
  7. Synchronize master index tables (`--sync-indexes`) to achieve 100% mathematical parity.
* **Out-of-Scope Non-Goals**:
  - Runtime UI production code edits in `app/src/main/`.
  - Modifying non-UI requirement domains (e.g. `req_tracking_cockpit.md`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Target Requirements**: `REQ-UI-002`, `REQ-UI-008`, `REQ-UI-009`, `REQ-UI-010`, `REQ-UI-062`, `REQ-UI-135`, `REQ-UI-166`, `REQ-UI-289`.
* **Historical Origins**: Implemented across past sprints (`ATT-237`, `ATT-238`, `ATT-737`, `ATT-1523`, `ATT-2627`, `ATT-3851`). Grounded by established test suites (`TimeFormatterTest.java`, `KnownLocationsScreenTest.kt`, `RouteSelectorViewModelTest.kt`, etc.).
* **Preservation of Core Invariants**: 100% test pass rate across unit tests, pure Python 3 standard library tooling execution, zero changes to production APK binaries, and 100% master index mathematical parity.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**:
  - `docs/requirements/req_ui_design_system.md`: 8 restored REQs, 41 reciprocal TST links added, mandatory attributes added to all 452 REQs.
  - `docs/tests/tst_ui_design_system.md`: Added `TST-UI-041`, `TST-UI-044`; updated header count to 466.
  - `docs/tests/tst_process_stability.md`: Added `TST-INT-007`.
  - `docs/requirements/req_process_stability.md` & `docs/tests/tst_process_stability.md`: Registered `REQ-PRO-085` and `TST-PRO-085`.
  - `tools/verify_requirement_governance.py`: Pruned UI references from legacy allowlists.
* **Affected Architecture Elements**: Living documentation markdown files, governance verification script.
* **Verification Impact & Test Surface**:
  - `python3 tools/verify_requirement_governance.py --coverage`
  - `python3 tools/verify_requirement_governance.py --enforce-unique --enforce-links`
  - `python3 tools/verify_requirement_governance.py --check-evidence`
  - `python3 tools/verify_requirement_governance.py --check-attributes`
  - `python3 tools/verify_requirement_governance.py --sync-indexes`
  - Targeted unit tests: `ThemeColorCentralizationAuditTest`, `GlobalDeleteContextMenuAuditTest`, `TimeFormatterTest`.
* **Risk Assessment & Invariants**: LOW risk. Pure living documentation backfill and tooling cleanup with zero production code changes.
