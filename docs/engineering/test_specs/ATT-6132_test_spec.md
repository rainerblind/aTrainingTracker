# Stage 2: Requirement & Test Specification - ATT-6132: Backfill test evidence for UI Design System requirements

**Ticket**: [ATT-6132](https://rainerblind.atlassian.net/browse/ATT-6132)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-085` (*UI Design System Test Evidence Backfill & Bidirectional Traceability Governance*), `REQ-UI-002`, `REQ-UI-008`, `REQ-UI-009`, `REQ-UI-010`, `REQ-UI-062`, `REQ-UI-135`, `REQ-UI-166`, `REQ-UI-289`  
**Test Spec ID**: `TST-PRO-085`, `TST-UI-041`, `TST-UI-044`, `TST-INT-007`  
**Branch**: `subagent-Lane-3-Agent---ATT-6132-self-d4f75212`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PRO-085)

### 1.1 Problem Statement & Rationale
Under ASPICE SWE.6 BP4, SWE.1 BP5, and Remediation A-08.6 (ATT-6132), all requirements in `docs/requirements/req_ui_design_system.md` must link to valid, existing test specifications in `docs/tests/tst_ui_design_system.md` with verified automated test code implementations in `app/src/test/` or be downgraded to `[Implemented]`. Bidirectional reference integrity must guarantee that every referenced TST cites all of its parent REQs back with zero dangling references.

### 1.2 Attributes
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review

### 1.3 Functional & Architectural Requirements
1. *Complete TST Coverage & Evidence Verification (`REQ-PRO-085.1`)*:
   • Every requirement in `docs/requirements/req_ui_design_system.md` SHALL cite one or more valid, existing backticked `TST-*` IDs in its `Tests:` field matching headings across living doc shards.
   • The 8 missing requirement specifications (`REQ-UI-002`, `REQ-UI-008`, `REQ-UI-009`, `REQ-UI-010`, `REQ-UI-062`, `REQ-UI-135`, `REQ-UI-166`, `REQ-UI-289`) SHALL be fully specified and restored to `req_ui_design_system.md`.
   • All 452 requirements in `req_ui_design_system.md` SHALL maintain `[Verified]` status backed by automated unit tests in `app/src/test/`.
2. *Dangling Reference Elimination & Test Specification Backfill (`REQ-PRO-085.2`)*:
   • Test specifications `TST-UI-041` (*Period Peak Markers Audit*) and `TST-UI-044` (*Mappable Device Items Layout Verification*) SHALL be registered in `docs/tests/tst_ui_design_system.md`.
   • Test specification `TST-INT-007` (*Original Sport Icons Visual Identity Verification*) SHALL be registered in `docs/tests/tst_process_stability.md`.
   • All 5 dangling TST citations and 12 dangling REQ citations in the UI domain SHALL be eliminated.
3. *Bidirectional Citation Integrity & Category 4 Gap Elimination (`REQ-PRO-085.3`)*:
   • Every test specification in `tst_ui_design_system.md` SHALL be cited back by 100% of the requirements it declares in its `Requirement(s):` field.
   • All 41 Category 4 gaps identified in the UI domain SHALL be updated in `req_ui_design_system.md` with reciprocal citations.
4. *Attribute Compliance & Header Parity (`REQ-PRO-085.4`)*:
   • All 452 requirements in `req_ui_design_system.md` SHALL declare valid `Priority`, `Type`, and `Verification Method` attributes per `REQ-PRO-077`.
   • Domain shard headers SHALL reflect accurate totals (`Total Requirements: 452`, `Total Tests: 466`) in 100% mathematical parity with master index tables.
5. *Tooling Allowlist Pruning (`REQ-PRO-085.5`)*:
   • All resolved UI references (`TST-UI-041`, `TST-UI-044`, `TST-INT-007`, `REQ-UI-002`, `REQ-UI-008`, `REQ-UI-009`, `REQ-UI-010`, `REQ-UI-062`, `REQ-UI-135`, `REQ-UI-166`, `REQ-UI-289`, and `REQ-UI-*` in `LEGACY_UNGROUNDED_VERIFIED_REQS`) SHALL be pruned from `tools/verify_requirement_governance.py`.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Traceability & Coverage)**:
  * *Given* `docs/requirements/req_ui_design_system.md`,
  * *When* evaluated via `python3 tools/verify_requirement_governance.py --coverage`,
  * *Then* 100% of requirements cite valid TSTs (0 ungrounded claims), and 100% of domain TSTs are cited back by all referenced REQs (0 Category 4 gaps in domain).
* **Criterion 2 (Link Integrity & Governance)**:
  * *Given* living documentation shards,
  * *When* evaluated via `python3 tools/verify_requirement_governance.py --check-links --check-evidence --check-attributes`,
  * *Then* zero dangling references in UI domain, zero ungrounded Verified statuses, and zero missing attributes are detected.
* **Criterion 3 (Master Index Parity)**:
  * *Given* `tools/verify_requirement_governance.py --sync-indexes`,
  * *When* executed,
  * *Then* master index tables achieve 100% mathematical parity.

---

## 2. Test Specification (TST-PRO-085)

### Test Case 1: Coverage & Bidirectional Traceability Audit (`TST-PRO-085.1`)
* **Scope**: Governance Verification
* **Target File**: `tools/verify_requirement_governance.py`
* **Preconditions**: `req_ui_design_system.md`, `tst_ui_design_system.md`, `tst_process_stability.md` updated.
* **Action**: `python3 tools/verify_requirement_governance.py --coverage`
* **Expected Result**: Category 1: 100% (452/452 REQs with TST). Category 4: 0 domain bidirectional citation gaps.
* **Pass Criteria**: Zero ungrounded claims, zero domain gaps in Category 4.

### Test Case 2: Link Integrity, Evidence Status & Attribute Audit (`TST-PRO-085.2`)
* **Scope**: Governance Verification
* **Target File**: `tools/verify_requirement_governance.py`
* **Preconditions**: Shards updated with attributes, bidirectional links, and restored REQ/TST definitions.
* **Action**:
  - `python3 tools/verify_requirement_governance.py --check-links`
  - `python3 tools/verify_requirement_governance.py --check-evidence`
  - `python3 tools/verify_requirement_governance.py --check-attributes`
* **Expected Result**: Zero dangling references in UI domain, zero ungrounded Verified statuses, zero missing attributes.

### Test Case 3: Unit Test Suite Execution (`TST-PRO-085.3`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.banalservice.sensor.formater.TimeFormatterTest" --tests "com.atrainingtracker.trainingtracker.ui.map.*"` and `python3 -m unittest tools/test_verify_requirement_governance.py`
* **Expected Result**: 100% test pass rate with zero failures.

---

## 3. Traceability Matrix

| Requirement | Description | Test Specification | Verification Status |
| :--- | :--- | :--- | :--- |
| `REQ-PRO-085.1` | Complete TST Coverage & REQ Restoration | `TST-PRO-085.1` | Specified |
| `REQ-PRO-085.2` | Dangling Reference Elimination & TST Backfill | `TST-PRO-085.2` | Specified |
| `REQ-PRO-085.3` | Bidirectional Citation Integrity & Cat 4 Resolution | `TST-PRO-085.1` | Specified |
| `REQ-PRO-085.4` | Attribute Compliance & Header Parity | `TST-PRO-085.2` | Specified |
| `REQ-PRO-085.5` | Tooling Allowlist Pruning | `TST-PRO-085.2` | Specified |
| `REQ-UI-002` | TimeFormatter Display Formatting | `TST-UNT-002` | Specified |
| `REQ-UI-008` | Structured Architecture & Elevation Profile Linking | `TST-STR-001` | Specified |
| `REQ-UI-009` | Session Isolation & Workout Transition UI | `TST-UI-029`, `TST-INT-004`, `TST-STR-003` | Specified |
| `REQ-UI-010` | Navigation Drawer & Tab Configuration Layout | `TST-INT-005`, `TST-INT-006` | Specified |
| `REQ-UI-062` | App Branding & Header Presentation | `TST-UI-071` | Specified |
| `REQ-UI-135` | Multi-Dimensional Favorite Tracks Filtering & Modal Sheet | `TST-UI-088` | Specified |
| `REQ-UI-166` | Lieblingsorte UI/UX Harmonization & Management | `TST-UI-070`, `TST-UI-118` | Specified |
| `REQ-UI-289` | Context-Aware Route Selection & Auto-Detection | `TST-UI-249` | Specified |
