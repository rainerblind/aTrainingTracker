# Stage 2: Requirement & Test Specification - ATT-6133: Backfill test evidence for Settings and Profiles requirements

**Ticket**: [ATT-6133](https://rainerblind.atlassian.net/browse/ATT-6133)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-082` (*Settings and Profiles Test Evidence Backfill & Bidirectional Traceability Governance*)  
**Test Spec ID**: `TST-PRO-082`  
**Branch**: `subagent-Lane-2-Agent---ATT-6133-self-f93d7836`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PRO-082)

### 1.1 Problem Statement & Rationale
Under ASPICE SWE.6 BP4, SWE.1 BP5, and Remediation A-08.7 (ATT-6133), all requirements in `docs/requirements/req_settings_profiles.md` must link to valid, existing test specifications in `docs/tests/tst_settings_profiles.md` with verified test code implementations in `app/src/test/` or be downgraded to `[Implemented]`. Bidirectional reference integrity must guarantee that every referenced TST is cited back by its parent REQs with zero dangling references.

### 1.2 Attributes
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review

### 1.3 Functional & Architectural Requirements
1. *Complete TST Coverage & Evidence Verification (`REQ-PRO-082.1`)*:
   • Every requirement in `docs/requirements/req_settings_profiles.md` SHALL cite one or more valid, existing backticked `TST-*` IDs in its `Tests:` field, achieving 100% test citation coverage.
   • `REQ-PRI-002` SHALL be downgraded from `[Verified]` to `[Implemented]` per `REQ-PRO-073` due to lack of automated test fixtures for documentation policy (`privacy.md`).
   • `REQ-SET-001` SHALL cite `TST-SET-082` (training zones unit test evidence in `SettingsDataStorePowerZonesTest.kt`).
   • `REQ-SET-002` SHALL cite `TST-SET-098` (equipment normalization test evidence in `EquipmentDbHelperNormalizationTest.kt`).
   • `REQ-SET-004` SHALL cite `TST-SET-053` (auto-naming ergonomics test evidence in `WorkoutClusterAutoNamingTest.kt`).
   • `REQ-SET-005` SHALL cite `TST-NAV-005` (units test evidence).
   • `REQ-PRI-001` SHALL cite `TST-PRI-002` (permission transparency and startup checks).
   • `REQ-SET-067` SHALL cite `TST-SET-067` (route cluster counter test evidence in `WorkoutClusterCounterTest.kt`).
   • `REQ-SET-070` SHALL cite `TST-SET-070` (workout header edit navigation and button ordering test evidence in `WorkoutClusterStatsTest.kt`).
   • `REQ-SET-075` SHALL cite `TST-SET-077`, `TST-UI-318`, `TST-UI-321`.
   • `REQ-SET-083`, `REQ-SET-084`, `REQ-SET-085`, `REQ-SET-087` SHALL cite `TST-LOC-001` alongside their respective `TST-SET-*` IDs.
2. *Test Specification Additions & Shorthand Expansion (`REQ-PRO-082.2`)*:
   • Living test specifications `TST-SET-067` and `TST-SET-070` SHALL be added to `docs/tests/tst_settings_profiles.md`, resolving Category 3 code-without-spec gaps.
   • Shorthand numeric requirement citations (`007`, `008`, `011`, `030`, `038`) in `docs/tests/tst_settings_profiles.md` SHALL be expanded to canonical IDs (`REQ-SET-007`, `REQ-SET-008`, `REQ-SET-011`, `REQ-SET-030`, `REQ-SET-037`).
3. *Bidirectional Citation Integrity (`REQ-PRO-082.3`)*:
   • Every test specification in `docs/tests/tst_settings_profiles.md` SHALL be cited back by 100% of the requirements it declares in its `Requirement(s):` field, resolving Category 4 gaps for `TST-LOC-001`, `TST-PRI-005`, `TST-PRI-006`, `TST-SET-043`, `TST-SET-077`.
   • Zero dangling references SHALL exist within the `settings_profiles` domain.
4. *Attribute Compliance & Header Parity (`REQ-PRO-082.4`)*:
   • All 102 requirements in `docs/requirements/req_settings_profiles.md` SHALL declare valid `Priority`, `Type`, and `Verification Method` attributes per `REQ-PRO-077`.
   • Shard headers SHALL reflect accurate totals (`Total Requirements: 102`, `Total Tests: 91`) in 100% mathematical parity with master index tables.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1**:
  * *Given* `docs/requirements/req_settings_profiles.md`,
  * *When* evaluated via `python3 tools/verify_requirement_governance.py --coverage`,
  * *Then* 100% of requirements cite valid TSTs, Category 4 bidirectional gaps for the domain are 0, and Category 3 unmapped tags for SET are eliminated.
* **Criterion 2**:
  * *Given* `docs/requirements/req_settings_profiles.md` and `docs/tests/tst_settings_profiles.md`,
  * *When* checked for dangling references and attributes,
  * *Then* zero dangling references and zero missing attributes are detected.
* **Criterion 3**:
  * *Given* `tools/verify_requirement_governance.py --sync-indexes`,
  * *When* executed,
  * *Then* master index tables achieve 100% mathematical parity.

### 1.5 System Invariants
1. Pure Python 3 standard library governance execution; zero Android runtime production code changes.
2. Master table mathematical parity across all 9 domain shards remains 100% green.
3. All existing test suites continue to pass with 0 failures.

---

## 2. Test Specification (TST-PRO-082)

### Test Case 1: Coverage & Bidirectional Traceability Audit (`TST-PRO-082.1`)
* **Scope**: Governance Verification
* **Target File**: `tools/verify_requirement_governance.py`
* **Preconditions**: `req_settings_profiles.md`, `tst_settings_profiles.md` updated.
* **Action**: `python3 tools/verify_requirement_governance.py --coverage`
* **Expected Result**: 0 Category 3 tags for SET, 0 Category 4 gaps for SET/LOC/PRI/MON, >=95% REQ-TST coverage.

### Test Case 2: Attribute & Link Integrity Audit (`TST-PRO-082.2`)
* **Scope**: Governance Verification
* **Target File**: `tools/verify_requirement_governance.py`
* **Preconditions**: Shards enriched with Priority, Type, and Verification Method attributes.
* **Action**: `python3 tools/verify_requirement_governance.py --check-attributes --check-links --check-evidence --check-unique-ids`
* **Expected Result**: Zero missing attributes, zero dangling references in `settings_profiles`, zero ungrounded `[Verified]` requirements.

### Test Case 3: Master Index Table Synchronization (`TST-PRO-082.3`)
* **Scope**: Master Index Parity
* **Target File**: `docs/requirements.md`, `docs/tests.md`
* **Preconditions**: Shard headers updated to 102 requirements and 91 tests.
* **Action**: `python3 tools/verify_requirement_governance.py --sync-indexes`
* **Expected Result**: 100% mathematical parity across all 9 domain shards with 4-column summary format (`Total | Verified | Implemented | Planned / Open`).

### Test Case 4: Targeted Unit Test Suite Regression (`TST-PRO-082.4`)
* **Scope**: Unit Tests
* **Target Files**: `WorkoutClusterCounterTest.kt`, `WorkoutClusterStatsTest.kt`, `AthleteMetricsRealSqliteMigrationTest.kt`
* **Action**: `./gradlew testDebugUnitTest --tests "*Cluster*" --tests "*Setting*"`
* **Expected Result**: 100% PASS rate across all targeted tests.
