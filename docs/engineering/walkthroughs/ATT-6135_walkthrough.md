# Walkthrough: Backfill test evidence for Store Presence (project_governance shard) requirements (ATT-6135)

* **Parent Ticket**: [ATT-6135](https://rainerblind.atlassian.net/browse/ATT-6135) (*[Verbesserung] Backfill test evidence for Store Presence (project_governance shard) requirements*)
* **Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)
* **Sub-Tasks**:
  * [ATT-7499](https://rainerblind.atlassian.net/browse/ATT-7499) (*[Analysis] Backfill test evidence for Store Presence (project_governance shard) requirements*) - `Erledigt`
  * [ATT-7500](https://rainerblind.atlassian.net/browse/ATT-7500) (*[Req & Test Spec] Backfill test evidence for Store Presence (project_governance shard) requirements*) - `Erledigt`
  * [ATT-7501](https://rainerblind.atlassian.net/browse/ATT-7501) (*[Impl-Plan] Backfill test evidence for Store Presence (project_governance shard) requirements*) - `Erledigt`
  * [ATT-7505](https://rainerblind.atlassian.net/browse/ATT-7505) (*[Implementation] Backfill test evidence for Store Presence (project_governance shard) requirements*) - `Erledigt`
* **Target Version**: `V5.0.0` | **Active Sprint**: `2026-46.4`
* **Requirements**: [`REQ-STP-001` .. `REQ-STP-010`](file:///home/rainer/.gemini/antigravity/worktrees/e228d70c-9ed0-47cc-a7a4-f4fb443c5e2b/subagent-Lane-2-Agent---ATT-6135-self-487a5904/docs/requirements/req_store_presence.md), [`REQ-PRO-084`](file:///home/rainer/.gemini/antigravity/worktrees/e228d70c-9ed0-47cc-a7a4-f4fb443c5e2b/subagent-Lane-2-Agent---ATT-6135-self-487a5904/docs/requirements/req_process_stability.md)
* **Test Specifications**: [`TST-STP-001` .. `TST-STP-010`](file:///home/rainer/.gemini/antigravity/worktrees/e228d70c-9ed0-47cc-a7a4-f4fb443c5e2b/subagent-Lane-2-Agent---ATT-6135-self-487a5904/docs/tests/tst_store_presence.md), [`TST-PRO-084`](file:///home/rainer/.gemini/antigravity/worktrees/e228d70c-9ed0-47cc-a7a4-f4fb443c5e2b/subagent-Lane-2-Agent---ATT-6135-self-487a5904/docs/tests/tst_process_stability.md)
* **Branch**: `subagent-Lane-2-Agent---ATT-6135-self-487a5904` -> `sprint/2026-46.4`
* **Author**: AI Agent 1 (Implementer) | **Reviewer**: AI Agent 2 (Chief Inspector) | **Date**: 2026-11-12

---

## 1. Executive Summary & Root Cause Analysis

Under ASPICE CL2 remediation (Remediation ID `A-08.9`, SWE.6 BP4, SWE.1 BP5), the `project_governance` domain (renamed in ATT-6140 to `req_store_presence.md` and `tst_store_presence.md` because it exclusively contains `STP` requirements) required audit and backfill of bidirectional traceability, test code evidence, and mandatory ASPICE attributes.

### Key Remediation Achievements:
1. **Mandatory ASPICE Attribute Backfill**:
   - Added `Priority`, `Type`, and `Verification Method` across all 10 requirements (`REQ-STP-001` .. `REQ-STP-010`) and all 10 test specifications (`TST-STP-001` .. `TST-STP-010`).
2. **Automated Test Code Evidence Tagging**:
   - Enhanced `app/src/test/java/com/atrainingtracker/trainingtracker/build/PlayStoreListingContractTest.kt` with explicit test tags and docstrings linking to `TST-STP-002` (verifying `REQ-STP-002`, `REQ-STP-004`) and `TST-STP-004` (verifying `REQ-STP-004`).
   - All 10 `TST-STP-*` specifications now possess concrete automated test code in `com.atrainingtracker.trainingtracker.build.*` contract test suites:
     * `TST-STP-001`, `TST-STP-002`, `TST-STP-004`: `PlayStoreListingContractTest.kt`
     * `TST-STP-003`: `PromoVideoAssetsContractTest.kt`
     * `TST-STP-005`, `TST-STP-006`: `ReadmePlayStoreBadgeContractTest.kt`
     * `TST-STP-007`, `TST-STP-008`, `TST-STP-009`, `TST-STP-010`: `ReleaseCiAndLintConfigContractTest.kt`
   - Zero requirements required downgrade from `[Verified]` to `[Implemented]`.
3. **100% Bidirectional Link Traceability**:
   - 10/10 `REQ-STP-*` cite existing `TST-STP-*` IDs (100% coverage, 0 Category 1 gaps).
   - 10/10 `TST-STP-*` have automated test code (100% code coverage, 0 Category 2 gaps for STP).
   - 10/10 `TST-STP-*` cite their corresponding `REQ-STP-*` back (100% bidirectional citation, 0 Category 4 gaps for STP).
   - 0 dangling references in the Store Presence domain.
4. **Process Governance Registration**:
   - Registered `REQ-PRO-084` and `TST-PRO-084` in `req_process_stability.md` and `tst_process_stability.md` with complete mandatory attributes.
5. **Living Documentation Synchronisation**:
   - Master tables in `docs/requirements.md` and `docs/tests.md` synchronized via `--sync-indexes` with 100% mathematical parity (Requirements: 1086 Total, 1079 Verified, 7 Implemented; Tests: 1092 Total, 1092 Verified).

---

## 2. Summary of Implementation Changes

### 2.1 Test Code Tagging (`PlayStoreListingContractTest.kt`)
Tagged test methods with requirement references and TST headings:
* `fullDescriptionsContainMandatoryV5Features`: Added `TST-STP-002` / `REQ-STP-002` tag.
* `storeListingsDoNotContainUnimplementedBacklogFeatures`: Added `TST-STP-002` / `REQ-STP-002` tag.
* `storeScreenshotsAreUniqueAndValid`: Added `TST-STP-004` / `REQ-STP-004` tag.

### 2.2 Living Documentation (`req_store_presence.md` & `tst_store_presence.md`)
Added mandatory metadata lines to all 10 items:
* `REQ-STP-001` .. `010`: `Priority: High/Medium/Low`, `Type: Functional/Safety`, `Verification Method: Test`.
* `TST-STP-001` .. `010`: `Priority: High/Medium/Low`, `Type: Functional/Safety`, `Verification Method: Automated Test / Contract Test`.

### 2.3 Process Stability Shards (`req_process_stability.md` & `tst_process_stability.md`)
* Added `REQ-PRO-084: Store Presence Test Evidence Backfill & Bidirectional Traceability Governance [Verified]`
* Added `TST-PRO-084: Store Presence Test Evidence Backfill & Traceability Verification Suite [Verified]`

### 2.4 Index Master Tables (`docs/requirements.md` & `docs/tests.md`)
Synchronized row counts and category sums across all shards.

---

## 3. Verification & Test Evidence

### 3.1 Targeted Unit & Contract Test Execution
Executed the contract test suites covering all 10 Store Presence specifications:
```bash
./gradlew testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.build.PlayStoreListingContractTest" \
  --tests "com.atrainingtracker.trainingtracker.build.ReadmePlayStoreBadgeContractTest" \
  --tests "com.atrainingtracker.trainingtracker.build.PromoVideoAssetsContractTest" \
  --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"
```
**Result**: `BUILD SUCCESSFUL in 17s`, all tests green (100% pass).

### 3.2 ASPICE Requirement Governance Verification
```bash
python3 tools/verify_requirement_governance.py
```
**Result**:
- `PASS: Living documentation master tables match all 9 domain shards with 100% parity.`
- `PASS: All REQ and TST heading IDs are unique across living documentation shards.`
- `PASS: Zero new dangling REQ/TST references detected.`
- `PASS: Zero ungrounded [Verified] requirements detected.`
- `PASS: All Tests fields in requirement shards conform to valid TST ID and placeholder syntax (REQ-PRO-075).`
- `PASS: Zero attribute violations detected.`
- `PASS: 100% System-to-Software requirement traceability verified.`

### 3.3 Coverage Evaluation
```bash
python3 tools/verify_requirement_governance.py --coverage
```
**Result**:
- Category 1: 99.91% (STP: 100% - 10/10)
- Category 2: 64.29% (STP: 100% - 10/10 automated test methods)
- Category 3: 99.29% (STP: 100% - 0 orphaned tags)
- Category 4: 92.12% (STP: 100% - 10/10 cited back)

### 3.4 Governance Tool Self-Tests
```bash
python3 -m unittest tools/test_verify_requirement_governance.py
```
**Result**: `Ran 50 tests in 0.057s - OK`.

---

## 4. Traceability Matrix

| Artifact / Entity | ID | Status | Notes |
|:---|:---|:---|:---|
| Requirements | `REQ-STP-001` .. `REQ-STP-010` | **Verified** | All mandatory attributes populated; 100% test backed |
| Test Specifications | `TST-STP-001` .. `TST-STP-010` | **Verified** | All mandatory attributes populated; 100% automated |
| Process Requirement | `REQ-PRO-084` | **Verified** | Registered in `req_process_stability.md` |
| Process Test Spec | `TST-PRO-084` | **Verified** | Registered in `tst_process_stability.md` |
| Subtask (Stage 1) | `ATT-7499` | **Erledigt** | Stage 1 Forensic Analysis & Scope Bounding |
| Subtask (Stage 2) | `ATT-7500` | **Erledigt** | Stage 2 Requirement & Test Specification |
| Subtask (Stage 3) | `ATT-7501` | **Erledigt** | Stage 3 Architecture & Implementation Plan |
| Subtask (Stage 4) | `ATT-7505` | **Erledigt** | Stage 4 Construction & Contract Test Tagging |
| Subtask (Stage 5) | Pending | **In Überprüfung** | Stage 5 Verification, Regression & Walkthrough |
| Parent Ticket | `ATT-6135` | **Test** | Advancing to Final Review (Agent) -> Final Review (Human) |
