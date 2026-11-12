# Stage 1 Analysis: ATT-6133 - Backfill test evidence for Settings and Profiles requirements

**Ticket**: [ATT-6133](https://rainerblind.atlassian.net/browse/ATT-6133)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `subagent-Lane-2-Agent---ATT-6133-self-f93d7836`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

During the independent ASPICE 4.0 pre-assessment (baseline develop @ sprint/2026-45.11, Remediation ID: A-08.7, ASPICE SWE.6 BP4, SWE.1 BP5), the `settings_profiles` living documentation shards (`docs/requirements/req_settings_profiles.md` and `docs/tests/tst_settings_profiles.md`) were identified as requiring test evidence backfill, bidirectional citation integrity reconciliation, and attribute governance:
* **Outdated Header Counts**: `req_settings_profiles.md` declares *Total Requirements: 90* (actual count: 102), and `tst_settings_profiles.md` declares *Total Tests: 77* (actual count: 89, increasing to 91 with backfilled test specs).
* **Missing Test Specifications (Category 3 Code Tags Without Spec)**: Automated test classes `WorkoutClusterCounterTest.kt` and `WorkoutClusterStatsTest.kt` declare code tags `TST-SET-067` and `TST-SET-070` respectively, but neither test specification exists as a living section heading in `docs/tests/tst_settings_profiles.md`.
* **Generic Test Citation & Ungrounded Status**: Several legacy requirements (`REQ-SET-001`, `REQ-SET-002`, `REQ-SET-005`, `REQ-PRI-001`, `REQ-PRI-002`) cite generic manual test `TST-MAN-001`. `REQ-PRI-002` (Accessible Privacy Policy in `privacy.md`) has no automated test code and must be downgraded to `[Implemented]` per `REQ-PRO-073`. Automated tests for zones (`TST-SET-082`), equipment (`TST-SET-098`), unit systems (`TST-NAV-005`), and permission safety (`TST-PRI-002`) exist in `app/src/test/` and must be linked to replace generic manual citations.
* **Misaligned Test Citations**: `REQ-SET-004` cites `TST-STR-001` instead of `TST-SET-053`; `REQ-SET-067` cites `TST-SET-054` instead of `TST-SET-067`; `REQ-SET-070` cites `TST-SET-059` instead of `TST-SET-070`; `REQ-SET-075` omits `TST-SET-077`.
* **Bidirectional Citation Discrepancies (Category 4)**:
  - `TST-LOC-001` lists requirements `REQ-SET-083`..`088`, but only `REQ-SET-086` and `REQ-SET-088` cite it back.
  - `TST-PRI-005` and `TST-PRI-006` cite out-of-shard `REQ-UI-106` and parent requirements not citing them back.
  - `TST-SET-043` is not cited back by `REQ-SET-059`.
  - `TST-SET-077` is not cited back by `REQ-SET-075`.
  - Abbreviated numbers in `tst_settings_profiles.md` (`007`, `008`, `011`, `030`, `038`) break regex parsing for `TST-SET-001`, `TST-SET-003`, `TST-SET-021`, and `TST-SET-028`.
* **Mandatory Attributes (REQ-PRO-077)**: All 102 requirements in `req_settings_profiles.md` lack mandatory `Priority`, `Type`, and `Verification Method` attributes.
* **Process Stability Requirement**: Registration of `REQ-PRO-082` and `TST-PRO-082` is required.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Root Cause**:
  1. Shard migration (`ATT-4594`) and incremental feature work (`ATT-714`, `ATT-808`, `ATT-221`, `ATT-3365`, `ATT-4455`, `ATT-4449`, `ATT-3998`) introduced new requirements, tests, and test classes, but living documentation headers and bidirectional citations were not synchronously updated.
  2. Test code for `TST-SET-067` (`WorkoutClusterCounterTest.kt`) and `TST-SET-070` (`WorkoutClusterStatsTest.kt`) was authored with standardized tags without adding the corresponding living test specification sections into `docs/tests/tst_settings_profiles.md`.
  3. Abbreviated shorthand notation (e.g. ``007`` instead of ``REQ-SET-007``) was used in legacy test specification headings, preventing automated bidirectional parsers from identifying citations.
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  - Validated test code presence across `app/src/test/`:
    * `WorkoutClusterCounterTest.kt` contains 3 distinct tests annotated for `TST-SET-067`.
    * `WorkoutClusterStatsTest.kt` contains comprehensive tests annotated for `TST-SET-070`.
    * `AthleteMetricsRealSqliteMigrationTest.kt`, `SettingsDataStorePowerZonesTest.kt` cover training zones (`REQ-SET-001`, `REQ-SET-082`).
    * `EquipmentDbHelperNormalizationTest.kt`, `EquipmentRepositorySyncTest.kt` cover equipment management (`REQ-SET-002`, `REQ-SET-098`).
    * `SpeedUtilsTest.kt`, `DistanceFormatterTest.kt`, `TuningConfigTest.kt` cover unit conversion (`REQ-SET-005`).
    * `WorkoutClusterAutoNamingTest.kt` covers naming schemes (`REQ-SET-004`, `REQ-SET-053`).
    * `MainActivityStartupPermissionContractTest.kt`, `LocationPermissionSafetyTest.kt` cover permission flow (`REQ-PRI-001`, `REQ-PRI-003`, `TST-PRI-002`).
  - Confirmed `REQ-PRI-002` references documentation (`privacy.md`) with no automated test fixture; downgrading to `[Implemented]` per `REQ-PRO-073` is methodologically sound and prevents status inflation.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Update headers in `docs/requirements/req_settings_profiles.md` and `docs/tests/tst_settings_profiles.md` to reflect true totals.
  2. Add test specifications `TST-SET-067` and `TST-SET-070` to `docs/tests/tst_settings_profiles.md`.
  3. Reconcile `Tests:` fields in `req_settings_profiles.md` (replacing `TST-MAN-001` where unit tests exist, linking `REQ-SET-004` -> `TST-SET-053`, `REQ-SET-067` -> `TST-SET-067`, `REQ-SET-070` -> `TST-SET-070`, `REQ-SET-075` -> `TST-SET-077`, and adding `TST-LOC-001` across monetization REQs).
  4. Downgrade `REQ-PRI-002` from `[Verified]` to `[Implemented]`.
  5. Fix abbreviated requirement references (`007`, `008`, `011`, `030`, `038`) in `tst_settings_profiles.md`.
  6. Populate `Priority`, `Type`, and `Verification Method` attributes for all 102 requirements in `req_settings_profiles.md`.
  7. Register `REQ-PRO-082` and `TST-PRO-082` in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md`.
  8. Synchronize master index tables via `python3 tools/verify_requirement_governance.py --sync-indexes`.
* **Out-of-Scope Non-Goals**:
  - Refactoring unrelated domain shards (`req_tracking_cockpit.md`, `req_ui_design_system.md`).
  - Modifying Android production runtime code in `app/src/main/`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-082` / `TST-PRO-082` and backfill reconciliation for `REQ-SET-001`..`103`, `REQ-PRI-001`..`007`, `REQ-LOC-001`..`002`. Targets `docs/requirements/req_settings_profiles.md`, `docs/tests/tst_settings_profiles.md`, `req_process_stability.md`, `tst_process_stability.md`.
* **Historical Origin & Commit Trace**: Sprint 2026-46.4 via ticket `ATT-6133` under Epic `ATT-6119` (ASPICE CL2, Remediation A-08.7). Preceded by ATT-6127 (`79ac0b23`), ATT-6128 (`571e16ae`), ATT-6129 (`8a92782e`).
* **Root Reason for Existing Formulation**: Incremental development across settings and route clustering left minor discrepancies in living documentation headers, shorthand requirement references, and ungrounded manual citations.
* **Preservation of Core Invariants**: 100% test pass rate preserved; zero production code modification; living documentation master index mathematical parity strictly preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**: `REQ-SET-001`..`103`, `REQ-PRI-001`..`007`, `REQ-LOC-001`..`002`, net-new `REQ-PRO-082`.
* **Affected Architecture Elements**:
  - `docs/requirements/req_settings_profiles.md`
  - `docs/tests/tst_settings_profiles.md`
  - `docs/requirements/req_process_stability.md`
  - `docs/tests/tst_process_stability.md`
  - `docs/requirements.md` & `docs/tests.md`
* **Verification Impact & Test Surface**:
  - `python3 tools/verify_requirement_governance.py --coverage`
  - `python3 tools/verify_requirement_governance.py --check-links`
  - `python3 tools/verify_requirement_governance.py --check-evidence`
  - `python3 tools/verify_requirement_governance.py --check-attributes`
  - `python3 tools/verify_requirement_governance.py --check-tests-format`
  - `python3 -m unittest tools/test_verify_requirement_governance.py`
  - `./gradlew testDebugUnitTest --tests "*Cluster*" --tests "*Setting*" --tests "*Zone*"`
* **Risk Assessment & Invariants**: Low risk. Confined to living documentation traceability alignment, metadata attributes, and test specs. Zero risk to Android runtime behavior.

---

## 6. Architectural Strategy & High-Level Solution

1. **Add Missing Test Specifications**: Define `TST-SET-067` and `TST-SET-070` in `docs/tests/tst_settings_profiles.md`.
2. **Reconcile Citations**: Align reciprocal links in `req_settings_profiles.md` and `tst_settings_profiles.md`, expanding abbreviated numbers to canonical IDs.
3. **Evidence Status Grounding**: Downgrade `REQ-PRI-002` to `[Implemented]` per `REQ-PRO-073`.
4. **Metadata Enrichment**: Add `Priority`, `Type`, and `Verification Method` across all 102 requirements in `req_settings_profiles.md`.
5. **Governance Registration**: Register `REQ-PRO-082` and `TST-PRO-082` in process stability shards and synchronize master index tables.

---

## 7. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing unit test suites.
  2. 100% mathematical parity across master tables and living shards.
  3. No modification of Android production source code.
* **Risk Rating**: **LOW** (strictly documentation and governance verification alignment).
