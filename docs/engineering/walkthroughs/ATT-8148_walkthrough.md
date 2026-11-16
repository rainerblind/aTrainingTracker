# Stage 5 Walkthrough: ATT-8148 - Clean up orphan REQ and TST IDs in code, stale shard header counts and missing REQ description fields

**Ticket**: [ATT-8148](https://rainerblind.atlassian.net/browse/ATT-8148)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1`  
**Requirement Mapping**: `REQ-PRO-104` (`REQ-PRO-104.1`..`REQ-PRO-104.4`) [Verified]  
**Test Spec Mapping**: `TST-PRO-104` (`TST-PRO-104.1`..`TST-PRO-104.4`) [Verified]  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Executive Summary & Verification Walkthrough
Remediated ASPICE 4.0 Finding `Minor (SWE.1)` (SWE.1 BP1/BP5, GP 2.2.4) across living documentation and tooling:
1. **Code-to-Shard Orphan Detection & Typo Resolution (`REQ-PRO-104.1`)**: Implemented `find_code_traceability_orphans()` and `verify_code_traceability_orphans()` scanning `app/src/main`, `app/src/test`, `app/src/androidTest`, and `tools/` (2,341 files scanned), with zero false positives on synthetic test tokens. Fixed code comment typos in `SensorSearchTuningSection.kt:93` (`REQ-UI-314`) and `FitFileWriter.kt:110` (`REQ-MIG-053`). Restored missing specifications: `REQ-WKO-035..039` & `TST-WKO-035..039` in `req_aftermath_periods.md`/`tst_aftermath_periods.md`, `REQ-HRV-038..042` & `TST-HRV-038..042`, `REQ-SPT-001/003/004`, and `REQ-UI-292` & `TST-UI-252` in `req_ui_design_system.md`.
2. **Living Doc Shard Header Synchronization (`REQ-PRO-104.2`)**: Extended `recalculate_master_index_tables()` (via `--sync-indexes` and `recalc-docs`) to auto-synchronize `**Total Requirements**: N` and `**Total Tests**: N` across all 18 shards, and added `verify_shard_header_counts()` ensuring 100% header-to-section parity.
3. **Description Field Ratchet & Field Coverage Reporting (`REQ-PRO-104.3`)**: Implemented `verify_req_descriptions()` enforcing `- **Description**:` on all non-baselined requirements (`LEGACY_MISSING_DESCRIPTION_REQS` frozen at 860 items). Added `--report-req-field-coverage` reporting Description (27.4%), GWT (75.3%), and Allocation (96.3%) metrics.
4. **Cross-Check Integrity Safeguards (`REQ-PRO-104.4`)**: Verified all restored requirements in `SYSTEM_REFINED_PREFIXES` declare `- **System Requirement(s)**:`, `TST-WKO-035..039` verified against authentic `// --- TST-WKO-*` unit test tags in `WorkoutRpeFeelTest.kt`, and unbacked restored tests (`TST-UI-252`, `TST-HRV-038..042`) marked `[Implemented]`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-104.1` | `TST-PRO-104.1` | `verify_code_traceability_orphans()` + synthetic orphan injection test | **PASSED** | `Verified` |
| `REQ-PRO-104.2` | `TST-PRO-104.2` | `verify_shard_header_counts()` + header mismatch rejection test | **PASSED** | `Verified` |
| `REQ-PRO-104.3` | `TST-PRO-104.3` | `verify_req_descriptions()` + `--report-req-field-coverage` | **PASSED** | `Verified` |
| `REQ-PRO-104.4` | `TST-PRO-104.4` | `--check-verified-evidence` + `--check-reverse-sys-trace` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence
* **Stage 4 Gradle Tier 1 Scope** *(reused per `Rule 7.5`; zero runtime logic modified in Stage 5)*:
  `./gradlew :app:testDebugUnitTest` (`WorkoutRpeFeelTest`, `AspiceSweAuditContractTest`): **PASSED** (0 failures, 0 regressions).
* **Stage 5 Python Unit Suites**:
  `python3 -m unittest tools/test_verify_requirement_governance.py tools/test_generate_test_evidence.py tools/test_jira_accounts.py tools/hoechstmass/tools/test_hoechstmass_tools.py`: **172 / 172 PASSED** (`0` failures, `0` errors).
* **Stage 5 Governance CLI Gates**:
  `verify_requirement_governance.py` (all checks pass: 0 code orphans across 2,341 files, 18/18 shard headers synchronized, 100% description ratchet compliance, 362/362 classified `System-Refinement` REQs, 0 unbacked `[Verified]` tests): **ALL PASS**.

---

## 4. Hardware / Physical Verification & Invariants
* **Runtime / Submodule Scope**: Zero production logic edits in `app/src/main/` (`FitFileWriter.kt` and `SensorSearchTuningSection.kt` are strictly comment tag corrections); `ANT-Android-SDKs` untouched at `160000` (`0e8cea71`).
* **Living Docs & Gate Invariants**: `REQ-PRO-104` & `TST-PRO-104` promoted to `[Verified]`; master tables and shard headers synchronized at 100% parity; Rule 3 Human Gate preserved.
