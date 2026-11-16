# Stage 1 Analysis: ATT-8144 - Persist versioned test and coverage evidence per sprint and release in git instead of a build symlink

**Ticket**: [ATT-8144](https://rainerblind.atlassian.net/browse/ATT-8144)  
**Sub-task**: [[Analysis]](https://rainerblind.atlassian.net/browse/ATT-8144)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Branch**: `feature/ATT-8144` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16  

---

## 1. Problem Statement & Motivation
Under ASPICE 4.0 Major Finding `M7` (SWE.4 BP5, SWE.5 BP7, SWE.6 BP5, GP 2.2.3), `docs/engineering/test_results` is a volatile symlink to `app/build/reports/tests/testDebugUnitTest`. Build outputs are ephemeral and gitignored, meaning verification results are neither versioned, baseline-controlled, nor traceable to git commits. Furthermore, while Kover coverage floors are enforced in Gradle, measured coverage values (lines/branches) are never recorded or baselined. No TST has an auditable pass/fail record with commit hash and timestamp in the repository.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Origin**: In commit `2da41d08` (`ATT-4936`, Rule 67) and `ATT-6137` (`REQ-PRO-067.3`), `docs/engineering/test_results` was created as a developer convenience symlink pointing to local build outputs.
* **Tooling & Baseline Deficit**:
  * Test results vanish on clean checkouts and are excluded from git configuration management (`SUP.8`).
  * `generate_release_verification_summary.py` collects traceability and JUnit summaries for release tags, but does not capture Kover code coverage metrics (lines/branches) and does not persist sprint-level test evidence.
  * `verify_engineering_directory_structure()` and `ReleaseCiAndLintConfigContractTest.kt` rigidly enforce the symlink to `app/build/`, actively blocking persistent versioned evidence in git.
* **Independent Peer Review & Verification**:
  * Audited `docs/engineering/test_results`: verified symlink target `../../app/build/reports/tests/testDebugUnitTest`.
  * Audited `app/build.gradle`: Kover 0.9.11 enforces 4 coverage rules (`Global Floor` 40%/35%, `Critical Tracking` 55%/45%, `Critical Filters` 70%/50%, `Critical Sensors` 60%/50%), producing `reportDebug.xml` when executed.
  * Audited `ATT-8143`: `parse_junit_xml_results` and `correlate_test_results_to_tsts` parse JUnit XMLs and correlate with `TST-XXX` IDs. Test evidence persistence cleanly integrates with these functions.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Create generator (`tools/generate_test_evidence.py` or extend `tools/generate_release_verification_summary.py`) outputting `docs/engineering/test_evidence/<sprint|release>.md` and `.json` containing commit SHA, date, test counts, failures, per-TST results, and Kover line/branch coverage per package.
  2. Remove `docs/engineering/test_results` symlink; update canonical directories in `verify_requirement_governance.py` and `aspice_governance.md` Rule 8.4 to allow `test_evidence/`.
  3. Update `ReleaseCiAndLintConfigContractTest.kt` to assert `docs/engineering/test_results` symlink is removed and `test_evidence/` is present.
  4. Update `docs/management/cm_plan.md` to classify `docs/engineering/test_evidence/` as a controlled Configuration Item.
  5. Integrate into Ceremony 2 (`sprint-planner/SKILL.md`) and release workflow (`release-manager/SKILL.md`) via `tools/jira_util.py export-test-evidence` and `verify_requirement_governance.py --check-test-evidence`.
* **Out-of-Scope Non-Goals**: Modifying Android runtime code in `app/src/main/`; altering Kover threshold percentages in `app/build.gradle`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-067.3` & `TST-PRO-067.3` in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md`.
* **Historical Origin & Commit Trace**: Added in Sprint `2026-46.3` via `ATT-6137` under Epic `ATT-6141` (Rule 67).
* **Root Reason for Existing Formulation**: Ensured local 1-click test inspection from `docs/engineering/` without committing bulky, ephemeral HTML build directories into git.
* **Preservation of Core Invariants**: Replaced by compact, version-controlled markdown and JSON summaries in `docs/engineering/test_evidence/`. Preserves effortless test visibility while establishing immutable ASPICE SUP.8 baselines.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Amend `REQ-PRO-067.3` (retire symlink) and define `REQ-PRO-100` (`REQ-PRO-100.1`..`REQ-PRO-100.4`) for sprint/release test evidence in `docs/requirements/req_process_stability.md`.
* **Affected Architecture Elements**: `tools/verify_requirement_governance.py`, `tools/generate_release_verification_summary.py`, `tools/jira_util.py`, `tools/generate_test_evidence.py`, `docs/management/cm_plan.md`, `ReleaseCiAndLintConfigContractTest.kt`, `.agents/rules/aspice_governance.md`, `.agents/skills/sprint-planner/SKILL.md`, `.agents/skills/release-manager/SKILL.md`.
* **Verification Impact & Test Surface**: Amend `TST-PRO-067.3` and add `TST-PRO-100` (`TST-PRO-100.1`..`TST-PRO-100.3`) in `docs/tests/tst_process_stability.md`; unit tests in `tools/test_verify_requirement_governance.py` and `tools/test_generate_release_verification_summary.py`; `ReleaseCiAndLintConfigContractTest`.
* **Risk Assessment & Invariants**: Low risk (2 SP). Developer tooling and QA evidence integrity; zero Android runtime bytecode changes; human decision gate and merge lock intact.

---

## 6. Architectural Strategy & High-Level Solution
* **Test Evidence Generator**:
  * Build `tools/generate_test_evidence.py` supporting `--sprint <sprint_id>` and `--release <tag>`.
  * Parse JUnit XMLs via `parse_junit_xml_results` (ATT-8143) and Kover XML via `parse_kover_xml_report` extracting global and critical package line/branch percentages.
  * Emit atomic `docs/engineering/test_evidence/<id>.md` and companion `<id>.json`.
* **Tooling & Governance Integration**:
  * Wire `jira_util.py export-test-evidence` into Ceremony 2 Step 5.
  * Wire `verify_requirement_governance.py --check-test-evidence` validating evidence records.
  * Delete `docs/engineering/test_results` symlink, replace canonical check in `verify_requirement_governance.py`, and update contract test.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression in production source; 100% test pass rate; deterministic living doc parity; Rule 3 Human Gate intact.
* **Risk Rating**: **LOW** (developer tooling, QA evidence integrity, and documentation governance).
