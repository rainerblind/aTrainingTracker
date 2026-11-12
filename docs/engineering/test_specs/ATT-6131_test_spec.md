# Stage 2: Requirement & Test Specification - ATT-6131: Backfill test evidence for Maps and Navigation requirements

**Ticket**: [ATT-6131](https://rainerblind.atlassian.net/browse/ATT-6131)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-081` (*Maps and Navigation Test Evidence Backfill & Bidirectional Traceability Governance*), `REQ-MAP-037`  
**Test Spec ID**: `TST-PRO-081`, `TST-MAP-038`  
**Branch**: `subagent-Lane-3-Agent---ATT-6131-self-93a50f27`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PRO-081)

### 1.1 Problem Statement & Rationale
Under ASPICE SWE.6 BP4, SWE.1 BP5, and Remediation A-08.5 (ATT-6131), all requirements in `docs/requirements/req_maps_navigation.md` must link to valid, existing test specifications in `docs/tests/tst_maps_navigation.md` with verified automated test code implementations in `app/src/test/` or be downgraded to `[Implemented]`. Bidirectional reference integrity must guarantee that every referenced TST cites all of its parent REQs back with zero dangling references.

### 1.2 Attributes
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review

### 1.3 Functional & Architectural Requirements
1. *Complete TST Coverage & Evidence Verification (`REQ-PRO-081.1`)*:
   • Every requirement in `docs/requirements/req_maps_navigation.md` SHALL cite one or more valid, existing backticked `TST-*` IDs in its `Tests:` field matching headings across living doc shards.
   • Requirements lacking automated test code (`REQ-MAP-001` "Roughness" and `REQ-MAP-002` "Cyan->Indigo heatmaps") SHALL be downgraded from `[Verified]` to `[Implemented]` per `REQ-PRO-073`. All other 73 requirements SHALL retain `[Verified]` status backed by automated unit tests in `app/src/test/`.
2. *Dangling Reference Elimination & Bidirectional Citation Integrity (`REQ-PRO-081.2`)*:
   • Missing requirement `REQ-MAP-037` (*Route Waypoint Marker Badge Diameter Optimization & Glanceability Proportional Scaling*) SHALL be restored in `req_maps_navigation.md` citing `TST-MAP-039`, and removed from `LEGACY_DANGLING_REQ_REFS`.
   • Missing test specification `TST-MAP-038` (*Known Locations: Heim-Basis Redundant Badge Excision & Start-Count Home Resolution Alignment Verification*) SHALL be restored in `tst_maps_navigation.md` citing `REQ-MAP-036`.
   • Every test specification in `tst_maps_navigation.md` SHALL be cited back by 100% of the requirements it declares in its `Requirement(s):` field (`REQ-MAP-055`, `REQ-MAP-025`, `REQ-MAP-027` citing `TST-MAP-070`; `REQ-GEO-002` citing `TST-NAV-056`; `REQ-MAP-004` citing `TST-MAP-044`; `REQ-MAP-007` citing `TST-UNT-010`; `REQ-MAP-016` citing `TST-MAP-010`).
   • External non-domain requirement `REQ-UI-106` SHALL be removed from `TST-MAP-056`.
3. *Test Code Tag Synchronization (`REQ-PRO-081.3`)*:
   • `WaypointBadgeMarkerTest.kt` SHALL be annotated with `TST-MAP-039` to eliminate unmapped test code tags.
4. *Attribute Compliance & Header Parity (`REQ-PRO-081.4`)*:
   • All 75 requirements in `req_maps_navigation.md` SHALL declare valid `Priority`, `Type`, and `Verification Method` attributes per `REQ-PRO-077`.
   • Domain shard headers SHALL reflect accurate totals (`Total Requirements: 75`, `Total Tests: 70`) in 100% mathematical parity with master index tables.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-MAP-037` (*Route Waypoint Marker Badge Diameter Optimization & Glanceability Proportional Scaling*). Targets `MapUtils.kt`, `MapLayers.kt`, `WaypointBadgeMarkerTest.kt`.
* **Historical Origin & Commit Trace**: Authored in Sprint 2026-41.3 (`ATT-2628`) via commit `9b8fc6a9`, specified in `ATT-2628_test_spec.md`.
* **Root Reason for Existing Formulation**: 32 dp waypoint markers occluded polyline tracks on high-density displays. Reducing default size to 22 dp with proportional hairline/halo scaling preserved Maki glyph clarity while preventing visual clutter.
* **Preservation of Core Invariants**: All 10 distinct waypoint category colors, Maki CC0 icons, `createWaypointBadgeMarker` defaults, and 100% full-suite unit test pass rate remain preserved.

### 1.5 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Traceability & Coverage)**:
  * *Given* `docs/requirements/req_maps_navigation.md`,
  * *When* evaluated via `python3 tools/verify_requirement_governance.py --coverage`,
  * *Then* 100% of requirements cite valid TSTs, 0 code tags in maps/nav lack specs (Category 3), and 100% of cited TSTs reference their REQs back.
* **Criterion 2 (Link Integrity & Governance)**:
  * *Given* living documentation shards,
  * *When* evaluated via `python3 tools/verify_requirement_governance.py --check-links --check-evidence --check-attributes`,
  * *Then* zero dangling references in maps/nav, zero ungrounded Verified statuses, and zero missing attributes are detected.
* **Criterion 3 (Master Index Parity)**:
  * *Given* `tools/verify_requirement_governance.py --sync-indexes`,
  * *When* executed,
  * *Then* master index tables achieve 100% mathematical parity.

---

## 2. Test Specification (TST-PRO-081)

### Test Case 1: Coverage & Bidirectional Traceability Audit (`TST-PRO-081.1`)
* **Scope**: Governance Verification
* **Target File**: `tools/verify_requirement_governance.py`
* **Preconditions**: `req_maps_navigation.md`, `tst_maps_navigation.md`, `WaypointBadgeMarkerTest.kt` updated.
* **Action**: `python3 tools/verify_requirement_governance.py --coverage`
* **Expected Result**: Category 1: 100% (75/75 REQs with TST). Category 3: 0 unmapped MAP/NAV tags (resolving `TST-MAP-038`). Category 4: 100% bidirectional citation for all MAP and NAV test specifications.

### Test Case 2: Link Integrity, Evidence Status & Attribute Audit (`TST-PRO-081.2`)
* **Scope**: Governance Verification
* **Target File**: `tools/verify_requirement_governance.py`
* **Preconditions**: Shards updated with attributes and bidirectional links.
* **Action**:
  - `python3 tools/verify_requirement_governance.py --check-links`
  - `python3 tools/verify_requirement_governance.py --check-evidence`
  - `python3 tools/verify_requirement_governance.py --check-attributes`
* **Expected Result**: Zero dangling references in maps_navigation (0 new dangling references globally), zero ungrounded Verified statuses, zero missing attributes.

### Test Case 3: Unit Test Execution (`TST-PRO-081.3`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.WaypointBadgeMarkerTest" --tests "com.atrainingtracker.trainingtracker.routes.HomeLocationResolverTest"`
* **Expected Result**: 100% test pass rate with zero failures.

---

## 3. Traceability Matrix

| Requirement | Description | Test Specification | Verification Status |
| :--- | :--- | :--- | :--- |
| `REQ-PRO-081.1` | Complete TST Coverage & Evidence Verification | `TST-PRO-081.1` | Planned |
| `REQ-PRO-081.2` | Dangling Reference Elimination & Bidirectional Citation Integrity | `TST-PRO-081.1`, `TST-PRO-081.2` | Planned |
| `REQ-PRO-081.3` | Test Code Tag Synchronization | `TST-PRO-081.1`, `TST-PRO-081.3` | Planned |
| `REQ-PRO-081.4` | Attribute Compliance & Header Parity | `TST-PRO-081.2` | Planned |
| `REQ-MAP-037` | Waypoint Marker Badge Diameter Optimization & Scaling | `TST-MAP-039`, `TST-PRO-081.3` | Planned |
