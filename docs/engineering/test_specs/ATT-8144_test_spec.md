# Stage 2: Requirement & Test Specification - ATT-8144: Versioned Test & Coverage Evidence

**Ticket**: [ATT-8144](https://rainerblind.atlassian.net/browse/ATT-8144)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Requirement Mapping**: `REQ-PRO-100`, `REQ-PRO-067.3`  
**Test Spec ID**: `TST-PRO-100`, `TST-PRO-067.3`  
**Branch**: `feature/ATT-8144` | **Author**: AI Agent 2 (Auditor, `subagent_flash`) | **Date**: 2026-11-16  

---

## 1. Requirement Specification (REQ-PRO-100, REQ-PRO-067.3)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review`
* **REQ-PRO-100.1 (Canonical Test Evidence Store & Layout)**: The repository SHALL establish `docs/engineering/test_evidence/` as a canonical directory. Every sprint completion / merge and tagged release SHALL persist `docs/engineering/test_evidence/<sprint|release>.md` and companion `.json`.
* **REQ-PRO-100.2 (JUnit Test Metrics & Per-TST Verification Summary)**: `tools/generate_test_evidence.py` SHALL parse JUnit XML results and correlate tests to `TST-XXX` IDs, documenting commit SHA, timestamp, counts, failures, and per-TST pass/fail records.
* **REQ-PRO-100.3 (Kover Code Coverage Ingestion & Package Breakdown)**: The generator SHALL parse `app/build/reports/kover/reportDebug.xml` and record exact line/branch coverage percentages for project global and critical packages (Tracking, Filters, Sensors).
* **REQ-PRO-100.4 (Tooling Automation & Governance Gating)**: `tools/jira_util.py export-test-evidence` SHALL automate evidence generation during Ceremony 2 / release. `tools/verify_requirement_governance.py --check-test-evidence` SHALL verify evidence files exist and pass. `ReleaseCiAndLintConfigContractTest.kt` SHALL assert symlink retirement and evidence folder presence.
* **REQ-PRO-067.3 (Transition to Versioned Test Evidence Archival)**: Volatile `docs/engineering/test_results` symlink into `app/build/` is retired and replaced with persistent evidence under `docs/engineering/test_evidence/`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Modified `REQ-PRO-067.3` / `TST-PRO-067.3` and net-new `REQ-PRO-100` / `TST-PRO-100`. Targets `docs/engineering/test_results` (retired), `docs/engineering/test_evidence/`, `tools/verify_requirement_governance.py`, `ReleaseCiAndLintConfigContractTest.kt`, `cm_plan.md`, `aspice_governance.md` Rule 8.4.
* **Historical Origin & Commit Trace**: Added in Sprint `2026-46.4` via `ATT-6137` under Epic `ATT-6119` (Rule 67); amended in Sprint `2026-46.8` via `ATT-8144` (Finding M7) under Epic `ATT-8138`.
* **Root Reason for Existing Formulation**: Ensured local 1-click test report inspection without committing transient build HTML files, but left results volatile, unversioned, and absent from git configuration management (SUP.8).
* **Preservation of Core Invariants**: Compact markdown and JSON summaries in `test_evidence/` preserve test visibility while establishing immutable versioned baselines; zero Android runtime bytecode changes; Rule 3 Human Gate intact.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Symlink Retirement & Evidence Layout)**: *Given* `docs/engineering/`, *When* inspected, *Then* `test_results` symlink is retired and `test_evidence/` is established as canonical.
* **Criterion 2 (Automated Test Evidence Generation)**: *Given* a sprint merge or release build, *When* `export-test-evidence` runs, *Then* versioned markdown and JSON summaries are generated with commit hash, timestamp, test metrics, per-TST status, and Kover line/branch coverage per package.
* **Criterion 3 (Governance Gating)**: *Given* generated test evidence, *When* `verify_requirement_governance.py --check-test-evidence` runs, *Then* it validates evidence completeness and reports PASS.

---

## 4. Test Specification (TST-PRO-100, TST-PRO-067.3)
* **TST-PRO-100.1 (Evidence Generator Unit Suite)**: In `tools/test_generate_test_evidence.py`, verify parsing JUnit XMLs, Kover XML report, per-TST mapping, and JSON/markdown generation.
* **TST-PRO-100.2 (Governance Gating & Directory Structure Test)**: In `tools/test_verify_requirement_governance.py`, verify `verify_engineering_directory_structure()` allows `test_evidence/` and rejects `test_results` symlink, and test `--check-test-evidence`.
* **TST-PRO-100.3 (Release CI Contract & CM Plan Verification)**: In `ReleaseCiAndLintConfigContractTest.kt`, assert retirement of symlink and presence of `test_evidence/`, and verify `cm_plan.md` registration.
* **TST-PRO-067.3 (Symlink Retirement Contract Test)**: Verify contract test asserts `docs/engineering/test_results` symlink is removed.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-100.1` | Unit | `tools/generate_test_evidence.py` | `REQ-PRO-100.2`, `REQ-PRO-100.3` | Specified |
| `TST-PRO-100.2` | Unit | `tools/verify_requirement_governance.py` | `REQ-PRO-100.1`, `REQ-PRO-100.4` | Specified |
| `TST-PRO-100.3` | Contract | `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-100.1`, `REQ-PRO-100.4` | Specified |
| `TST-PRO-067.3` | Contract | `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-067.3` | Specified |
