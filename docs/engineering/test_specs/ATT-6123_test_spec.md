# Stage 2: Requirement & Test Specification - ATT-6123: Resolve Duplicate REQ and TST IDs

**Ticket**: [ATT-6123](https://rainerblind.atlassian.net/browse/ATT-6123)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.4` | **Branch**: `subagent-Lane-3-Agent---ATT-6123-self-2b7f1f9a`  
**Requirement Mapping**: `REQ-PRO-066` (*Living Documentation Unique Requirement & Test Identifier Governance*)  
**Test Spec ID**: `TST-PRO-066`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PRO-066 & Remediation Scope)

### 1.1 Problem Statement & Rationale
Under ASPICE SWE.1 BP5 and SWE.6 BP4, requirement and test specifications must maintain unambiguous bidirectional traceability. The existence of 11 duplicate IDs across living documentation shards compromises automated evidence compilation, creates ambiguous test coverage matrices, and risks false-positive compliance claims. ATT-6120 established detection and a transitional allowlist; ATT-6123 executes the complete remediation of all 11 duplicates and empties the allowlist.

### 1.2 Functional & Architectural Requirements
1. **Duplicate Remapping & Canonical Resolution (`REQ-PRO-066.4`)**:
   The system SHALL renumber all 11 non-canonical duplicate heading identifiers across living documentation shards to new, unique sequential identifiers at the end of their respective category sequences:
   * `REQ-DAT-001` (Try-With-Resources cursor lifecycle, line 2012) $\rightarrow$ `REQ-DAT-043` (Sub-reqs: `.1`, `.2`, `.3`).
   * `REQ-DAT-026` (Authoritative cursor mappers, line 2086) $\rightarrow$ `REQ-DAT-044` (Sub-reqs: `.1`, `.2`, `.3`, `.4`).
   * `REQ-MIG-018` (Stray sparse data import heading, line 730) $\rightarrow$ `REQ-MIG-051`.
   * `REQ-UI-116` (Stray weekly period heading, line 10) $\rightarrow$ `REQ-UI-518`.
   * `REQ-UI-123` (Localized cluster naming interface, line 5627) $\rightarrow$ `REQ-UI-519`.
   * `REQ-UI-257` (Compact equipment card layout, line 5649) $\rightarrow$ `REQ-UI-520`.
   * `TST-DAT-001` (Try-With-Resources cursor verification, line 1305) $\rightarrow$ `TST-DAT-039` (Subtests: `.1`, `.2`, `.3`).
   * `TST-DAT-021` (Workout cluster cursor verification, line 1338) $\rightarrow$ `TST-DAT-040` (Subtests: `.1`, `.2`, `.3`, `.4`).
   * `TST-MAP-055` (Climb detection & power calibration, line 935) $\rightarrow$ `TST-MAP-070` (Subtests: `.1`..`.6`).
   * `TST-MIG-036` (Offline power training metrics math, line 1359) $\rightarrow$ `TST-MIG-049` (Subtests: `.1`, `.2`, `.3`).
   * `TST-UI-216` (Compact equipment card layout verification, line 3210) $\rightarrow$ `TST-UI-518` (Subtests: `.1`..`.4`).
2. **Bidirectional Reference & Code Tag Synchronization (`REQ-PRO-066.5`)**:
   The system SHALL update all cross-references in requirement/test specifications, master tables (`docs/requirements.md`, `docs/tests.md`), unit/contract test headers and comments (`TryWithResourcesCursorAuditTest.kt`, `CursorUtilsTest.kt`, `EquipmentCardSpacingContractTest.kt`, `ClimbDetectorTest.kt`, `GpxRouteImporterElevationTest.kt`, `TcxWorkoutParserTest.kt`, `TcxCourseParserTest.kt`, `PowerPhysiologyCalculatorTest.kt`, `PowerZoneDistributionCalculatorTest.kt`), and static analysis tooling (`lint_cursor_safety.py`, `detekt-baseline.xml`) to reference the newly assigned canonical IDs.
3. **Allowlist Deprecation & Zero-Tolerance Enforcement (`REQ-PRO-066.6`)**:
   `tools/verify_requirement_governance.py` SHALL clear `LEGACY_DUPLICATE_IDS` (`frozenset()`). Running `verify_requirement_governance.py` (both in standard mode and with `--enforce-unique`) SHALL pass with 0 duplicate warnings and 0 duplicate errors.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* all 11 duplicate headings are renumbered in `docs/requirements/` and `docs/tests/`,
  * *When* `python3 tools/verify_requirement_governance.py --enforce-unique` is executed,
  * *Then* it outputs `PASS: All REQ and TST heading IDs are unique across living documentation shards.` and exits with code 0.
* *Given* test files and tooling referencing renumbered IDs,
  * *When* static checks and targeted unit tests run,
  * *Then* all tags match the renumbered unique IDs with zero dangling references.
* *Given* `test_verify_requirement_governance.py`,
  * *When* executed via `python3 -m unittest`,
  * *Then* 100% of test cases pass cleanly.

### 1.4 System Invariants
* Total requirement count (1061) and total test count (1061) remain preserved across master index tables.
* Zero Android runtime bytecode modifications.
* Zero broken links in living documentation shards.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-066` and the 11 remediated legacy items (`REQ-DAT-001`, `REQ-DAT-026`, `REQ-MIG-018`, `REQ-UI-116`, `REQ-UI-123`, `REQ-UI-257`, `TST-DAT-001`, `TST-DAT-021`, `TST-MAP-055`, `TST-MIG-036`, `TST-UI-216`).
* **Historical Origin & Commit Trace**: Commit `e45969cd` (ATT-6120) introduced `find_duplicate_heading_ids` and `LEGACY_DUPLICATE_IDS` as an intermediate baseline allowlist pending ATT-6123.
* **Root Reason for Existing Formulation**: `LEGACY_DUPLICATE_IDS` was necessary to prevent CI breakage across parallel sprint lanes while ATT-6120 established the enforcement mechanism.
* **Preservation of Core Invariants**: The semantics, acceptance criteria, test assertions, and implementation mappings of all 11 items remain 100% intact; only colliding identifier tokens are renumbered to achieve strict global uniqueness.

---

## 2. Test Specification (TST-PRO-066)

### Test Case 1: Strict Zero-Duplicate Governance Verification (`TST-PRO-066.5`)
* **Scope**: Governance Verification (`tools/verify_requirement_governance.py`).
* **Command**: `python3 tools/verify_requirement_governance.py --enforce-unique`
* **Assertion**: Exits with code 0; exactly 0 duplicates reported across all 9 domain shards.

### Test Case 2: Standard Governance Verification (`TST-PRO-066.6`)
* **Scope**: Governance Verification (`tools/verify_requirement_governance.py`).
* **Command**: `python3 tools/verify_requirement_governance.py`
* **Assertion**: Exits with code 0; zero legacy duplicate warnings emitted; 100% master-table parity preserved.

### Test Case 3: Unit Test Suite for Governance Verifier (`TST-PRO-066.7`)
* **Scope**: Unit Tests (`tools/test_verify_requirement_governance.py`).
* **Command**: `python3 -m unittest tools/test_verify_requirement_governance.py`
* **Assertion**: All unit test scenarios pass 100% with empty `LEGACY_DUPLICATE_IDS`.

### Test Case 4: Targeted Unit & Architecture Test Suite (`TST-PRO-066.8`)
* **Scope**: Architecture & Contract Unit Tests.
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.architecture.TryWithResourcesCursorAuditTest" --tests "com.atrainingtracker.util.CursorUtilsTest" --tests "com.atrainingtracker.trainingtracker.ui.equipment.EquipmentCardSpacingContractTest"`
* **Assertion**: All tests compile and pass with updated tag references.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Command | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PRO-066.5]` | Tooling | `verify_requirement_governance.py --enforce-unique` | `REQ-PRO-066.4`, `REQ-PRO-066.6` | Specified |
| `[TST-PRO-066.6]` | Tooling | `verify_requirement_governance.py` | `REQ-PRO-066.6` | Specified |
| `[TST-PRO-066.7]` | Unit | `test_verify_requirement_governance.py` | `REQ-PRO-066.6` | Specified |
| `[TST-PRO-066.8]` | Unit/Arch | `TryWithResourcesCursorAuditTest`, `EquipmentCardSpacingContractTest` | `REQ-PRO-066.5` | Specified |
