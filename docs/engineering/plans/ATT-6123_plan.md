# Stage 3: Implementation Plan - ATT-6123: Resolve Duplicate REQ and TST IDs

**Ticket**: [ATT-6123](https://rainerblind.atlassian.net/browse/ATT-6123)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.4` | **Branch**: `subagent-Lane-3-Agent---ATT-6123-self-2b7f1f9a`  
**Requirement Mapping**: `REQ-PRO-066` (*Living Documentation Unique Requirement & Test Identifier Governance*)  
**Test Mapping**: `TST-PRO-066`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
11 duplicate heading identifiers exist across living documentation domain shards in `docs/requirements/` and `docs/tests/`. ATT-6120 established detection with a temporary baseline allowlist (`LEGACY_DUPLICATE_IDS`). Ticket ATT-6123 remediates all 11 duplicates by renumbering non-canonical instances to sequential IDs at the ends of the respective category sequences, updating all cross-references across living documentation, unit/architecture test comments, and lint tooling, and clearing `LEGACY_DUPLICATE_IDS` so that `--enforce-unique` runs with zero warnings and zero errors.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-066` (`REQ-PRO-066.4`–`REQ-PRO-066.6`)
* **Test Mapping**: `TST-PRO-066` (`TST-PRO-066.5`–`TST-PRO-066.8`)

---

## 3. System Invariants & Preserved Behavior
1. **Total Living Item Counts Preserved**: Shard totals remain 1061 REQs and 1061 TSTs; master index tables in `docs/requirements.md` and `docs/tests.md` retain 100% count parity.
2. **Zero Android Runtime Bytecode Changes**: Changes are strictly confined to Markdown documentation, test comments/tags, lint tooling, and governance verifier scripts.
3. **Deterministic Governance Tooling**: Pure Python 3 standard library; zero third-party dependencies; deterministic exit codes (0 = valid, 1 = failure).
4. **Subtask & Gate Governance**: Standard ASPICE lifecycle gating enforced through composite CLI tooling (`jira_util.py`).

---

## 4. Proposed Architectural Changes

### Component 1: Living Documentation Shards (`docs/requirements/`, `docs/tests/`)
Renumber the 11 non-canonical heading identifiers and their internal sub-item IDs:
* `docs/requirements/req_data_migration_export.md`:
  * `REQ-DAT-001` (Line 2012) $\rightarrow$ `REQ-DAT-043` (`.1`, `.2`, `.3`)
  * `REQ-DAT-026` (Line 2086) $\rightarrow$ `REQ-DAT-044` (`.1`, `.2`, `.3`, `.4`)
  * `REQ-MIG-018` (Line 730) $\rightarrow$ `REQ-MIG-051`
* `docs/requirements/req_ui_design_system.md`:
  * `REQ-UI-116` (Line 10) $\rightarrow$ `REQ-UI-518`
  * `REQ-UI-123` (Line 5627) $\rightarrow$ `REQ-UI-519`
  * `REQ-UI-257` (Line 5649) $\rightarrow$ `REQ-UI-520`
* `docs/tests/tst_data_migration_export.md`:
  * `TST-DAT-001` (Line 1305) $\rightarrow$ `TST-DAT-039` (`.1`, `.2`, `.3`), citing `REQ-DAT-043`
  * `TST-DAT-021` (Line 1338) $\rightarrow$ `TST-DAT-040` (`.1`, `.2`, `.3`, `.4`), citing `REQ-DAT-044`
  * `TST-MIG-036` (Line 1359) $\rightarrow$ `TST-MIG-049` (`.1`, `.2`, `.3`)
* `docs/tests/tst_maps_navigation.md`:
  * `TST-MAP-055` (Line 935) $\rightarrow$ `TST-MAP-070` (`.1`..`.6`)
* `docs/tests/tst_ui_design_system.md`:
  * `TST-UI-216` (Line 3210) $\rightarrow$ `TST-UI-518` (`.1`..`.4`), citing `REQ-UI-520`

### Component 2: Living Docs Process Stability Shards (`req_process_stability.md`, `tst_process_stability.md`)
Update `REQ-PRO-066` and `TST-PRO-066` to document the completed remediation of the 11 legacy duplicates and the enforcement of zero tolerance.

### Component 3: Test Code & Linter References (`app/src/test/`, `tools/lint_cursor_safety.py`)
Update code tags and comments in test suites that cite the renumbered IDs:
* `TryWithResourcesCursorAuditTest.kt`, `CursorUtilsTest.kt`, `lint_cursor_safety.py`, `detekt-baseline.xml` $\rightarrow$ `REQ-DAT-043` / `TST-DAT-039`
* `EquipmentCardSpacingContractTest.kt` $\rightarrow$ `REQ-UI-520` / `TST-UI-518`
* `ClimbDetectorTest.kt`, `GpxRouteImporterElevationTest.kt`, `TcxWorkoutParserTest.kt`, `TcxCourseParserTest.kt`, `PowerPhysiologyCalculatorTest.kt`, `PowerZoneDistributionCalculatorTest.kt` $\rightarrow$ `TST-MAP-070`
* `PowerPhysiologyCalculatorTest.kt` $\rightarrow$ `TST-MIG-049`

### Component 4: Governance Verifier Tooling (`verify_requirement_governance.py`, `test_verify_requirement_governance.py`)
* Clear `LEGACY_DUPLICATE_IDS = frozenset()`.
* Update verifier unit tests in `test_verify_requirement_governance.py` to assert zero duplicates across the repository.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Renumber Shard Headings & Internal Sub-Items
* Files: `docs/requirements/req_data_migration_export.md`, `docs/requirements/req_ui_design_system.md`, `docs/tests/tst_data_migration_export.md`, `docs/tests/tst_maps_navigation.md`, `docs/tests/tst_ui_design_system.md`.
* Changes: Update 11 duplicate heading IDs and their sub-items/citations to assigned sequential IDs.

### Step 2: Synchronize Process Shards & Living Docs Specifications
* Files: `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`.
* Changes: Update `REQ-PRO-066` and `TST-PRO-066` descriptions and acceptance criteria.

### Step 3: Update Test Code Tags, Comments, and Tooling
* Files:
  * `app/src/test/java/com/atrainingtracker/architecture/TryWithResourcesCursorAuditTest.kt`
  * `app/src/test/java/com/atrainingtracker/util/CursorUtilsTest.kt`
  * `app/detekt-baseline.xml`
  * `tools/lint_cursor_safety.py`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentCardSpacingContractTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/climbs/ClimbDetectorTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/routes/GpxRouteImporterElevationTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/migration/parsers/TcxWorkoutParserTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TcxCourseParserTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/physiology/PowerPhysiologyCalculatorTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCalculatorTest.kt`

### Step 4: Clear Verifier Allowlist & Update Verifier Tests
* Files: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`.
* Changes: Set `LEGACY_DUPLICATE_IDS = frozenset()`. Update test cases to assert clean repository execution with 0 duplicates.

### Step 5: Verification & Governance Execution
* Run:
  1. `python3 tools/verify_requirement_governance.py --enforce-unique`
  2. `python3 tools/verify_requirement_governance.py`
  3. `python3 -m unittest tools/test_verify_requirement_governance.py`
  4. Wrapped in sprint lock: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.architecture.TryWithResourcesCursorAuditTest" --tests "com.atrainingtracker.util.CursorUtilsTest" --tests "com.atrainingtracker.trainingtracker.ui.equipment.EquipmentCardSpacingContractTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: `verify_requirement_governance.py --enforce-unique` exiting 0 with zero warnings/errors confirms all 11 duplicates resolved.
* **Rollback**: `git checkout -- .` fully restores original state without side effects on parallel sprint lanes.
