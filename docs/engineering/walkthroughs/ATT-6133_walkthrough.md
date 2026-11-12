# Stage 5 Walkthrough: ATT-6133 - Backfill test evidence for Settings and Profiles requirements

**Ticket**: [ATT-6133](https://rainerblind.atlassian.net/browse/ATT-6133)  
**Sub-task**: Pending (`[Test]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-082` (`REQ-PRO-082.1`–`REQ-PRO-082.3`)  
**Test Spec ID**: `TST-PRO-082` (`TST-PRO-082.1`–`TST-PRO-082.4`)  
**Branch**: `subagent-Lane-2-Agent---ATT-6133-self-f93d7836`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

Prior to this remediation (Remediation ID: A-08.7, ASPICE SWE.6 BP4, SWE.1 BP5), the `settings_profiles` living documentation shards (`docs/requirements/req_settings_profiles.md` and `docs/tests/tst_settings_profiles.md`) suffered from evidence grounding gaps, header drifts, shorthand citation syntax, unmapped code tags, and bidirectional citation discrepancies:
* `REQ-PRI-002` (Accessible Privacy Policy in `privacy.md`) claimed `[Verified]` without automated test fixtures, constituting status inflation.
* Several requirements (`REQ-SET-001`, `REQ-SET-002`, `REQ-SET-004`, `REQ-SET-005`, `REQ-PRI-001`) cited generic tests (`TST-MAN-001`, `TST-STR-001`) despite authentic automated unit tests existing in `app/src/test/`.
* Test classes `WorkoutClusterCounterTest.kt` and `WorkoutClusterStatsTest.kt` declared code tags `TST-SET-067` and `TST-SET-070` that lacked corresponding specification headings in living documentation (Category 3 code tags without spec).
* Bidirectional citation discrepancies existed for `TST-LOC-001`, `TST-PRI-005`, `TST-PRI-006`, `TST-SET-043`, and `TST-SET-077`.
* Legacy test specification headings in `tst_settings_profiles.md` contained shorthand numbers (`007`, `008`, `011`, `030`, `038`) that prevented automated bidirectional discovery.
* All 102 requirements in `req_settings_profiles.md` lacked mandatory `Priority`, `Type`, and `Verification Method` attributes required by `REQ-PRO-077`.
* Shard header counters were outdated (`Total Requirements: 90` vs 102 actual, `Total Tests: 77` vs 89 actual).

---

## 2. Key Changes & Architecture

1. **Living Requirements Shard (`docs/requirements/req_settings_profiles.md`)**:
   - Corrected header to `Total Requirements: 102`.
   - Downgraded `REQ-PRI-002` to `[Implemented]` per `REQ-PRO-073`, grounding its status in reality.
   - Reconciled `Tests:` fields:
     * `REQ-SET-001` -> `TST-SET-082` (training zones unit test evidence).
     * `REQ-SET-002` -> `TST-SET-098` (equipment normalization test evidence).
     * `REQ-SET-004` -> `TST-SET-053` (auto-naming ergonomics test evidence).
     * `REQ-SET-005` -> `TST-NAV-005` (units test evidence).
     * `REQ-PRI-001` -> `TST-PRI-002` (permission transparency and startup checks).
     * `REQ-SET-059` -> added `TST-SET-043` alongside `TST-SET-050`, `TST-SET-064`.
     * `REQ-SET-075` -> added `TST-SET-077` alongside `TST-UI-318`, `TST-UI-321`.
     * `REQ-SET-083`, `REQ-SET-084`, `REQ-SET-085`, `REQ-SET-087` -> added `TST-LOC-001`.
   - Populated mandatory `Priority`, `Type`, and `Verification Method` attributes on all 102 requirements per `REQ-PRO-077`.
2. **Living Test Specification Shard (`docs/tests/tst_settings_profiles.md`)**:
   - Corrected header to `Total Tests: 89`.
   - Expanded shorthand numeric citations (`007`, `008`, `011`, `030`, `038`) to canonical IDs (`REQ-SET-007`, `REQ-SET-008`, `REQ-SET-011`, `REQ-SET-030`, `REQ-SET-037`).
   - Refined `Requirement(s):` fields on `TST-PRI-005` and `TST-PRI-006` to scope directly to `REQ-PRI-006` and `REQ-PRI-007`, achieving 100% bidirectional parity.
3. **Test Code Tag Harmonization (`app/src/test/`)**:
   - `WorkoutClusterCounterTest.kt`: Harmonized test tags to `TST-SET-054` (REQ-SET-067).
   - `WorkoutClusterStatsTest.kt`: Harmonized test tag to `TST-SET-055` (REQ-SET-068).
   - Eliminated unmapped tags `TST-SET-067` and `TST-SET-070` from Category 3.
4. **Process Governance Registration (`req_process_stability.md`, `tst_process_stability.md`)**:
   - Registered `REQ-PRO-082` and `TST-PRO-082` with full ASPICE metadata.
5. **Master Index Tables Synchronization (`docs/requirements.md`, `docs/tests.md`)**:
   - Recalculated and synchronized master index tables via `verify_requirement_governance.py --sync-indexes` with 100% mathematical parity across all 9 domain shards.

---

## 3. Verification & Test Evidence

* **Traceability & Verification Coverage Report (`python3 tools/verify_requirement_governance.py --coverage`)**:
  - **Category 1 (REQs with TST)**: 100% in settings_profiles (all 102 REQs cite valid TSTs).
  - **Category 2 (TSTs with Code)**: Increased from 696 to 698.
  - **Category 3 (Code tags without spec)**: Dropped from 8 to 6 (0 unmapped tags in settings_profiles).
  - **Category 4 (Bidirectional Coverage)**: 0 domain gaps for settings_profiles (100% bidirectional parity).
* **Reference & Evidence Governance**:
  - `python3 tools/verify_requirement_governance.py --check-links`: PASS (0 dangling references in settings_profiles).
  - `python3 tools/verify_requirement_governance.py --check-evidence`: PASS (0 ungrounded Verified requirements in settings_profiles).
  - `python3 tools/verify_requirement_governance.py --check-attributes`: PASS (0 attribute violations across all 102 REQs).
  - `python3 tools/verify_requirement_governance.py --check-tests-format`: PASS.
  - `python3 tools/verify_requirement_governance.py --sync-indexes`: PASS (100% parity).
  - `python3 -m unittest tools/test_verify_requirement_governance.py`: PASS (50/50 tests passed).
* **Targeted Unit Test Scope (Tier 1)**:
  - Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutClusterCounterTest" --tests "com.atrainingtracker.trainingtracker.database.WorkoutClusterStatsTest"`
  - Result: BUILD SUCCESSFUL (100% pass rate).
