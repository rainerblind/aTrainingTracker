# Stage 1 Analysis: ATT-6131 - Backfill test evidence for Maps and Navigation requirements

**Ticket**: [ATT-6131](https://rainerblind.atlassian.net/browse/ATT-6131)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `subagent-Lane-3-Agent---ATT-6131-self-93a50f27`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

During the ASPICE 4.0 pre-assessment baseline (baseline develop @ sprint/2026-45.11, Remediation ID: A-08.5, ASPICE SWE.6 BP4, SWE.1 BP5), the Maps and Navigation requirement and test specification shards (`docs/requirements/req_maps_navigation.md` and `docs/tests/tst_maps_navigation.md`) were identified as needing test evidence backfill and bidirectional reference integrity reconciliation:
* **Dangling Requirement Reference**: `docs/tests/tst_maps_navigation.md:446` (`[TST-MAP-039]`) cites non-existent requirement `REQ-MAP-037`, which is currently tolerated as a legacy dangling reference in `tools/verify_requirement_governance.py` (`LEGACY_DANGLING_REQ_REFS`).
* **Unmapped Test Code Tag (Category 3 Gap)**: `TST-MAP-038` is tagged across test code (`KnownLocationsRepositoryTest.kt`, `HomeLocationResolverTest.kt`, `KnownLocationHomeContractTest.kt`), but lacks a living documentation specification heading in `tst_maps_navigation.md`.
* **Corrupted / Outdated Test Citations**:
  - `REQ-MAP-055` cites outdated duplicate `TST-MAP-055` instead of `TST-MAP-070` (renumbered in ATT-6123).
  - `REQ-MAP-007` cites `TST-UNT-009` instead of `TST-UNT-010` (`MappablePath`).
  - `REQ-MAP-016` cites extraneous `TST-SET-002` alongside `TST-MAP-010`.
  - `REQ-GEO-002` lacks back-citation to `TST-NAV-056`.
  - `TST-MAP-056` cites external `REQ-UI-106`, creating an ungrounded cross-domain citation gap.
* **Missing Mandatory Metadata Attributes**: All 74 existing requirements in `req_maps_navigation.md` lack `Priority`, `Type`, and `Verification Method` attributes required by `REQ-PRO-077` (ATT-6136).
* **Ungrounded Status Claims**: `REQ-MAP-001` (Roughness) and `REQ-MAP-002` (Cyan->Indigo heatmaps) claim `[Verified]` on legacy manual testing without automated unit test backing. Per `REQ-PRO-073` (ATT-6126), they must be downgraded to `[Implemented]`.
* **Shard Header Count Drift**: Header counts (`Total Requirements: 58`, `Total Tests: 50`) are severely outdated relative to the actual shard contents (74 REQs, 69 TSTs).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Root Causes**:
  1. *Omission during Living Doc Sharding (`ATT-4594`)*: In sprint 2026-41.3 (`ATT-2628`), `REQ-MAP-037` was specified in `docs/engineering/test_specs/ATT-2628_test_spec.md` and implemented in `WaypointBadgeMarkerTest.kt`. However, when monolithic documentation was partitioned into domain shards, `REQ-MAP-037` was accidentally dropped, while its test specification `TST-MAP-039` was preserved.
  2. *Unsharded Test Heading (`ATT-2629`)*: Similarly, `TST-MAP-038` was specified in `ATT-2629_test_spec.md` and implemented across known locations unit tests, but its heading was omitted from `tst_maps_navigation.md`.
  3. *Renumbering Desynchronization (`ATT-6123`)*: Duplicate test specification `TST-MAP-055` (ATT-4033) was renumbered to `TST-MAP-070`, but `REQ-MAP-055` retained the pre-renumbering citation `TST-MAP-055`.
  4. *Legacy Manual Status Drift*: Initial requirements were authored with `[Verified]` before formal evidence-based status models existed (`REQ-PRO-073`).
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  - Running `python3 tools/verify_requirement_governance.py --coverage` confirms:
    * Category 3 identifies `TST-MAP-038` as 1 of 11 code tags lacking specification headings.
    * Category 4 identifies `TST-MAP-039`, `TST-MAP-056`, `TST-MAP-070`, and `TST-NAV-056` as Maps/Nav bidirectional gaps.
  - Forensic codebase scan verifies test coverage exists for all mapped requirements:
    * `WaypointBadgeMarkerTest.kt` verifies 22 dp marker size and `createWaypointBadgeMarker` (`REQ-MAP-037` / `TST-MAP-039`).
    * `HomeLocationResolverTest.kt`, `KnownLocationsRepositoryTest.kt`, and `KnownLocationHomeContractTest.kt` verify home location hierarchy (`REQ-MAP-036` / `TST-MAP-038`).
    * `ClimbDetectorTest.kt`, `GpxRouteImporterTest.kt`, `TcxCourseParserTest.kt`, `PowerPhysiologyCalculatorTest.kt`, `PowerZoneDistributionCalculatorTest.kt` verify climb and TCX fidelity (`REQ-MAP-055`, `REQ-MAP-025`, `REQ-MAP-027` / `TST-MAP-070`).
    * `TrackingElevationProfileResolverTest.kt` verifies stateful polyline continuity (`REQ-NAV-056`, `REQ-GEO-002` / `TST-NAV-056`).
  - No automated test exists for `Roughness` (legacy Android View) and legacy cyan->indigo heatmap `REQ-MAP-002`. Downgrading both to `[Implemented]` ensures 100% evidence-backed truth.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Restore missing `REQ-MAP-037` in `docs/requirements/req_maps_navigation.md` with full specification and mandatory attributes, citing `TST-MAP-039`.
  2. Restore missing `TST-MAP-038` in `docs/tests/tst_maps_navigation.md` covering `REQ-MAP-036`.
  3. Remove `REQ-MAP-037` from `LEGACY_DANGLING_REQ_REFS` in `tools/verify_requirement_governance.py` and `tools/test_verify_requirement_governance.py`.
  4. Tag `WaypointBadgeMarkerTest.kt` with `TST-MAP-039`.
  5. Correct desynchronized citations (`REQ-MAP-055`, `REQ-MAP-025`, `REQ-MAP-027` -> `TST-MAP-070`; `REQ-GEO-002` -> `TST-NAV-056`; `REQ-MAP-007` -> `TST-UNT-010`; `REQ-MAP-016` -> `TST-MAP-010`; `REQ-MAP-004` -> `TST-MAP-044`).
  6. Clean up `TST-MAP-056` Requirement(s) field by removing external `REQ-UI-106`.
  7. Downgrade `REQ-MAP-001` and `REQ-MAP-002` to `[Implemented]` per `REQ-PRO-073`.
  8. Backfill `Priority`, `Type`, and `Verification Method` attributes across all 75 requirements in `req_maps_navigation.md`.
  9. Update header counts in `req_maps_navigation.md` (Total: 75) and `tst_maps_navigation.md` (Total: 70).
  10. Register `REQ-PRO-081` and `TST-PRO-081` in `req_process_stability.md` and `tst_process_stability.md`.
  11. Synchronize master index tables (`--sync-indexes`) with 100% mathematical parity.
* **Out-of-Scope Non-Goals**:
  - Modifying other domain shards (`req_aftermath_periods.md`, `req_ui_design_system.md`), which are handled in tickets ATT-6129 and ATT-6132.
  - Modifying Android production logic in `app/src/main/`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-MAP-037` (*Route Waypoint Marker Badge Diameter Optimization & Glanceability Proportional Scaling*). Targets `MapUtils.kt`, `MapLayers.kt`, `WaypointBadgeMarkerTest.kt`.
* **Historical Origin & Commit Trace**: Authored in Sprint 2026-41.3 (`ATT-2628`) via commit `9b8fc6a9` and specified in `docs/engineering/test_specs/ATT-2628_test_spec.md`.
* **Root Reason for Existing Formulation**: 32 dp waypoint markers occluded route lines on high-density displays. Reducing default size to 22 dp with proportional hairline/halo scaling preserved Maki glyph clarity while preventing visual clutter.
* **Preservation of Core Invariants**: All 10 distinct waypoint category colors, Maki CC0 icons, `createWaypointBadgeMarker` defaults, and 100% full-suite unit test pass rate remain preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**:
  - `docs/requirements/req_maps_navigation.md`: Restore `REQ-MAP-037`; downgrade `REQ-MAP-001`, `REQ-MAP-002` to `[Implemented]`; backfill attributes for all 75 REQs; correct test citations.
  - `docs/tests/tst_maps_navigation.md`: Restore `TST-MAP-038`; correct requirement back-links for `TST-MAP-070`, `TST-MAP-056`, `TST-MAP-044`.
  - `docs/requirements/req_process_stability.md` & `docs/tests/tst_process_stability.md`: Register `REQ-PRO-081` and `TST-PRO-081`.
  - `tools/verify_requirement_governance.py`: Remove `REQ-MAP-037` from legacy allowlist.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/WaypointBadgeMarkerTest.kt`: Add test tag.
* **Affected Architecture Elements**: Living documentation markdown shards, governance verifier tooling.
* **Verification Impact & Test Surface**:
  - `python3 tools/verify_requirement_governance.py --coverage`
  - `python3 tools/verify_requirement_governance.py --check-links`
  - `python3 tools/verify_requirement_governance.py --check-evidence`
  - `python3 tools/verify_requirement_governance.py --check-attributes`
  - `python3 tools/verify_requirement_governance.py --sync-indexes`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.WaypointBadgeMarkerTest"`
* **Risk Assessment & Invariants**: Low risk. Confined to living documentation alignment, test metadata tags, and verifier allowlists. Zero production bytecode changes.

---

## 6. Architectural Strategy & High-Level Solution

1. **Restore `REQ-MAP-037`**: Reinsert specification from `ATT-2628_test_spec.md` with mandatory attributes between `REQ-MAP-036` and `REQ-MAP-038`.
2. **Restore `TST-MAP-038`**: Reinsert test specification from `ATT-2629_test_spec.md` into `tst_maps_navigation.md`.
3. **Harmonize Traceability & References**:
   - Update `REQ-MAP-055`, `REQ-MAP-025`, `REQ-MAP-027`, `REQ-GEO-002`, `REQ-MAP-007`, `REQ-MAP-016`, `REQ-MAP-004` citations.
   - Align `TST-MAP-070`, `TST-MAP-056`, `TST-MAP-044` requirement lists.
   - Downgrade `REQ-MAP-001` and `REQ-MAP-002` to `[Implemented]`.
4. **Attribute Backfill**: Add `Priority`, `Type`, and `Verification Method` to all 75 `REQ-MAP-*` / `REQ-GEO-*` / `REQ-NAV-*` / `REQ-CLM-*` requirements.
5. **Tag Backfill**: Annotate `WaypointBadgeMarkerTest.kt` with `TST-MAP-039`.
6. **Governance Allowlist Update**: Exclude `REQ-MAP-037` from `LEGACY_DANGLING_REQ_REFS`.
7. **Process Stability Registration**: Add `REQ-PRO-081` and `TST-PRO-081`.
8. **Master Table Synchronization**: Run `--sync-indexes` and verify 100% mathematical parity.

---

## 7. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero production runtime behavior change.
  2. 100% master table mathematical parity across all 9 domain shards (`docs/requirements.md`, `docs/tests.md`).
  3. Maps & Navigation domain achieves 100% REQs with valid TSTs, 0 dangling references, and 100% bidirectional citation parity.
* **Risk Rating**: **LOW** (Documentation and traceability backfill with full automated verification).
