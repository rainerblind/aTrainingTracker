# Stage 3: Implementation Plan - ATT-6138: Generate release verification summary per tag

**Ticket**: [ATT-6138](https://rainerblind.atlassian.net/browse/ATT-6138)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-074` (*Release Verification Summary Generation per Tag*)  
**Test Mapping**: `TST-PRO-074` (*Release Verification Summary Generator Test Suite*)  
**Branch**: `subagent-Lane-2-Agent---ATT-6138-self-aabf75cf`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background
Under ASPICE SWE.6 BP5 (Finding A-11), release decisions must be substantiated by recorded, baseline-controlled verification evidence. Prior to this improvement, verification evidence was fragmented across unit test XML reports, coverage matrices, headless system integration simulation results (SYS.4), and on-device protocol records (SYS.5). There was no automated mechanism to compile this evidence into an authoritative per-tag verification summary report (`docs/releases/<tag>/verification_summary.md`) and gate release approvals on unresolved deviations.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-074` (`REQ-PRO-074.1`–`REQ-PRO-074.5`) in `docs/requirements/req_process_stability.md`.
* **Test Mapping**: `TST-PRO-074` (`TST-PRO-074.1`–`TST-PRO-074.5`) in `docs/tests/tst_process_stability.md`.

---

## 3. System Invariants & Preserved Behavior
1. **Zero Production Runtime Code Changes**: Zero edits in `app/src/main/` or database schemas.
2. **Pure Python 3 Standard Library**: Zero external PyPI dependencies in `tools/generate_release_verification_summary.py`.
3. **Idempotence & Predictable Formatting**: Rerunning the summary generator on the same tag produces identical, deterministic output.
4. **Subtask & Gate Lifecycle Invariance**: Subtasks transition directly to `Erledigt` upon passing Gate review; terminal parent closure remains strictly reserved for `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `tools/generate_release_verification_summary.py`
A modular Python 3 CLI tool that aggregates verification evidence across four primary dimensions:
1. `parse_args()`: Parses `--tag`, `--commit`, `--out`, `--repo-root`, `--test-results-dir`, `--junit-archive`, and threshold flags.
2. `collect_unit_test_summary()`: Aggregates unit test counts, pass/fail metrics from JUnit XML files or zip archives, and records the versioned JUnit archive path (`build-evidence/junit-test-results-<tag>.zip` per ATT-6137).
3. `collect_coverage_summary()`: Integrates with `tools/verify_requirement_governance.py --coverage-json` (ATT-6122) to extract 4-category traceability metrics and evaluate coverage thresholds.
4. `collect_sys4_summary()`: Reads `docs/releases/<tag>/system_integration_results.json` (ATT-6166), validating overall pass status and interface simulation test cases (`SYS-INT-001` through `SYS-INT-006`).
5. `collect_sys5_summary()`: Parses `docs/releases/<tag>/sys_verification_record.md` (ATT-6167), validating the overall verdict, 8 formal protocol test cases (`SYS-VRF-001` through `SYS-VRF-008`), and QA sign-off status.
6. `compile_deviations()`: Scans all collected evidence for test failures, missing files, unedited placeholders, or coverage breaches, compiling an explicit list of `Deviations Requiring Human Approval`.
7. `render_markdown_summary()`: Formats the authoritative release verification summary Markdown document.

### Component 2: `tools/test_generate_release_verification_summary.py`
Comprehensive Python unit test suite validating CLI parsing, XML/zip parsing, coverage JSON integration, SYS.4/SYS.5 evidence validation, deviation detection, and markdown rendering.

### Component 3: Release Process Integration
* `.agents/skills/release-manager/SKILL.md`: Update Phase 2.5 to include `tools/generate_release_verification_summary.py` execution step.
* `docs/releases/README.md`: Document `verification_summary.md` within the release evidence directory structure and specify execution syntax.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Gate 3 Check & Script Construction
* File: `tools/generate_release_verification_summary.py`
* Implementation: Construct CLI argument parsing, evidence collection routines (unit tests, coverage, SYS.4, SYS.5), deviation evaluation, and Markdown generator.

### Step 2: Automated Unit Test Suite Construction
* File: `tools/test_generate_release_verification_summary.py`
* Implementation: Author targeted test suite asserting CLI options, XML parsing, coverage integration, deviation classification, and Markdown rendering.

### Step 3: Release Process & Documentation Integration
* Files: `.agents/skills/release-manager/SKILL.md`, `docs/releases/README.md`
* Implementation: Add summary generation step in Phase 2.5 of release manager skill and document artifact in release evidence README.

### Step 4: Verification & Test Execution
* Command: `python3 tools/parallel_sprint_lock.py python3 -m unittest tools/test_generate_release_verification_summary.py`
* Command: `python3 tools/parallel_sprint_lock.py python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `tools/test_generate_release_verification_summary.py`, run `tools/generate_release_verification_summary.py` on a sample release tag, and verify governance parity via `verify_requirement_governance.py`.
* **Rollback**: Clean git branch isolation; changes can be completely reverted via `git checkout sprint/2026-46.4`.
