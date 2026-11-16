# Stage 3: Implementation Plan - ATT-8148: Clean up orphan REQ and TST IDs in code, stale shard header counts and missing REQ description fields

**Ticket**: [ATT-8148](https://rainerblind.atlassian.net/browse/ATT-8148)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-47.1` | **Branch**: `feature/ATT-8148`  
**Requirement Mapping**: `REQ-PRO-104` (`REQ-PRO-104.1`..`REQ-PRO-104.4`)  
**Test Mapping**: `TST-PRO-104` (`TST-PRO-104.1`..`TST-PRO-104.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Problem Description & Background
Following the ASPICE 4.0 audit (SWE.1 BP1/BP5, GP 2.2.4), living documentation integrity suffers from 3 drift vectors:
1. 14 REQ and 5 TST IDs cited in `app/src/` do not exist in any shard (`req_*.md` / `tst_*.md`).
2. 14 of 18 domain shards publish stale `**Total Requirements / Tests**` header counts.
3. 860 / 1169 REQs (73.6%) lack the `- **Description**:` field.
This plan details the SWE.2 architecture to add code-to-shard orphan detection, shard header synchronization, frozen legacy description ratcheting, typo corrections, and missing shard specifications.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-104` (`REQ-PRO-104.1`..`REQ-PRO-104.4`) in `docs/requirements/req_process_stability.md`
* **Test Mapping**: `TST-PRO-104` (`TST-PRO-104.1`..`TST-PRO-104.4`) in `docs/tests/tst_process_stability.md`

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Logic Modification**: Zero production logic edits in `app/src/main/`; modifications in `app/` are strictly comment/docstring tag fixes (`SensorSearchTuningSection.kt`, `FitFileWriter.kt`).
2. **Submodule Stability**: `ANT-Android-SDKs` remains untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.
3. **Traceability & Verification Invariants (ATT-8145 & ATT-8147)**:
   - All restored REQs in `SYSTEM_REFINED_PREFIXES` declare `- **System Requirement(s)**:` (`SYSREQ-*` or `Derived (SW-only)`).
   - Every restored REQ includes a non-empty `- **Description**:`.
   - `TST-*` entries are marked `[Verified]` ONLY when real test files contain matching `// TST-*` tags (`TST-WKO-035..039`), and `[Implemented]` otherwise (`TST-UI-252`).
4. **False-Positive Prevention**: Scanner strictly ignores synthetic test tokens (`tools/test_*.py`, contract test string literals) and permits developer tool simulator tags (`REQ-SIM-*`, `TST-SIM-*`).

---

## 4. Proposed Architectural Changes (SWE.2)
* **`tools/verify_requirement_governance.py`**:
  - `find_code_traceability_orphans()` & `verify_code_traceability_orphans()`: Scans `app/src/main`, `app/src/test`, `app/src/androidTest`, and non-test `tools/` for `REQ-*` and `TST-*` tokens, asserting membership in declared shard headings.
  - `verify_shard_header_counts()`: Asserts equality between declared header counts and parsed section headings across all 18 shards.
  - `recalculate_master_index_tables()`: Rewrites `**Total Requirements**: N` and `**Total Tests**: N` in all 18 shards and updates master tables.
  - `LEGACY_MISSING_DESCRIPTION_REQS = frozenset({...})`: 860-item frozen baseline. `verify_req_descriptions()` enforces `- **Description**:` on all non-baselined REQs.
  - `--report-req-field-coverage`: Computes and prints Description, GWT, and Allocation percentages.
* **Code Comment Corrections (`app/src/main/`)**:
  - `SensorSearchTuningSection.kt:93`: Replace `REQ-UI-2780` with `REQ-UI-314`.
  - `FitFileWriter.kt:110`: Replace `REQ-EXP-041 / REQ-MIG-053` with `REQ-MIG-053`.
* **Living Documentation Shards (`docs/requirements/` & `docs/tests/`)**:
  - Add missing specifications: `REQ-WKO-035..039` & `TST-WKO-035..039` (workout), `REQ-HRV-038..042` & `TST-HRV-038..042` (HRV), `REQ-SPT-001/003/004` & tests (aftermath), `REQ-UI-292` & `TST-UI-252` (UI).
  - Execute `--sync-indexes` to reconcile all 18 shard headers and master index tables.
* **Unit Tests (`tools/test_verify_requirement_governance.py`)**:
  - Add tests `TST-PRO-104.1`..`TST-PRO-104.4` validating code orphan detection, shard header synchronization, Description ratchets, and zero false positives.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Risk Classification**: **STANDARD (Medium Process Risk, Zero Runtime Risk)** — executed by **`subagent_flash`**.
* **Step 1 (`tools/verify_requirement_governance.py`)**: Implement `find_code_traceability_orphans()`, `verify_code_traceability_orphans()`, `verify_shard_header_counts()`, updated `recalculate_master_index_tables()`, `LEGACY_MISSING_DESCRIPTION_REQS`, `verify_req_descriptions()`, and `--report-req-field-coverage`.
* **Step 2 (`app/src/main/`)**: Fix comment tags in `SensorSearchTuningSection.kt` (`REQ-UI-314`) and `FitFileWriter.kt` (`REQ-MIG-053`).
* **Step 3 (`docs/requirements/` & `docs/tests/`)**: Restore missing specs (`WKO`, `HRV`, `SPT`, `UI`), update `REQ-PRO-104`/`TST-PRO-104` to `[Implemented]`, and run `--sync-indexes`.
* **Step 4 (`tools/test_verify_requirement_governance.py`)**: Add `TST-PRO-104.1`..`TST-PRO-104.4` test suite.
* **Step 5 (Verification Execution)**: Run Python unit tests, governance suite, and contract suites:
  ```bash
  python3 -m unittest tools/test_verify_requirement_governance.py && \
  python3 tools/verify_requirement_governance.py && \
  python3 tools/verify_requirement_governance.py --report-req-field-coverage && \
  python3 tools/verify_requirement_governance.py --verify-sys-trace --check-reverse-sys-trace && \
  python3 tools/verify_requirement_governance.py --check-verified-evidence && \
  python3 tools/parallel_sprint_lock.py ./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRpeFeelTest"
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: All Python governance tests, live repo governance checks (0 orphans, 0 header mismatches, 0 ungrounded verified), and Gradle contract tests exit `0`.
* **Rollback**: Branch isolation (`feature/ATT-8148`) allows clean rollback via `git reset --hard 1d881158`.
