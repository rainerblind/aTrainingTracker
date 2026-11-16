# Stage 5 Walkthrough: ATT-8145 - Triage Verified requirements and test specs without executable test evidence

**Ticket**: [ATT-8145](https://rainerblind.atlassian.net/browse/ATT-8145)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1`  
**Requirement Mapping**: `REQ-PRO-101` (`REQ-PRO-101.1`..`REQ-PRO-101.5`) [Verified]  
**Test Spec Mapping**: `TST-PRO-101` (`TST-PRO-101.1`..`TST-PRO-101.5`) [Verified]  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Executive Summary & Verification Walkthrough
Remediated ASPICE 4.0 Finding `M1` (`LEGACY_UNBACKED_VERIFIED_TSTS` reduced from `405` to `0` = `frozenset()`):
1. **Deterministic Triage CLI & Ledger (`tools/verify_requirement_governance.py`)**: Implemented `triage_unbacked_verified_tsts()`, `--triage-unbacked`, and `--triage-csv`, exporting `docs/engineering/test_evidence/ATT-8145_unbacked_triage.csv` (`364` rows: `162` `unit-test-backed`, `86` `UI/manual-only`, `1` `obsolete/duplicate`, `115` `missing test code`). Filtered synthetic governance test files in `extract_test_code_tags()`.
2. **Test-Tag Reconciliation & Truthful Downgrades**: Tagged `158` unit-test-backed `TST-*` specs across `119` existing test files (`114` `.kt/.java` + `5` `.py`). Downgraded `207` unbacked TST headings (incl. `TST-MIG-006`, obsolete `TST-MAP-032`) and `208` unbacked `[Verified]` REQ headings (incl. `14` `[Verified]` orphans in `tools/traceability_baseline.json`) to `[Implemented]`, keeping valid `[Verified|Implemented|Planned|Open]` suffixes and `--sync-indexes` parity.
3. **Domain Follow-Up Backlog Tickets (Product Backlog Only)**: Filed `7` tickets under `ATT-8138`:
   * `ATT-8324` (Aftermath Periods, 14 TSTs) | `ATT-8325` (Data Migration & Export, 25 TSTs)
   * `ATT-8326` (Maps & Navigation, 7 TSTs) | `ATT-8327` (Process Stability & Governance, 48 TSTs)
   * `ATT-8328` (Settings & Profiles, 43 TSTs) | `ATT-8329` (Tracking Cockpit, 3 TSTs)
   * `ATT-8330` (UI Design System, 66 TSTs)
4. **Mirror & Cross-Cutting Test Maintenance**: Added parameterized `triage_unbacked_verified_tsts()` to `tools/hoechstmass/tools/verify_requirement_governance.py` + `test_hoechstmass_tools.py` and updated `tools/test_verify_requirement_governance.py` (`test_scenario_5`/`7`/`60`/`61`/`63`..`65`) and `tools/test_jira_accounts.py`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-101.1` | `TST-PRO-101.1` | `test_scenario_63` (`--triage-unbacked` & CSV ledger) | **PASSED** | `Verified` |
| `REQ-PRO-101.2` | `TST-PRO-101.2` | `test_scenario_64` + 113 tagged Gradle test classes (`954/954`) | **PASSED** | `Verified` |
| `REQ-PRO-101.3` | `TST-PRO-101.3` | `AspiceSweAuditContractTest` + `--sync-indexes` | **PASSED** | `Verified` |
| `REQ-PRO-101.4` | `TST-PRO-101.4` | `test_scenario_60`/`61`/`64` + `--check-verified-evidence` (`0` unbacked) | **PASSED** | `Verified` |
| `REQ-PRO-101.5` | `TST-PRO-101.5` | `HoechstmassToolingContractTest` + `test_hoechstmass_tools.py` (`test_scenario_65`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence
* **Stage 4 Gradle Tier 1 Scope** *(reused per `Rule 7.5`; zero runtime/test code modified in Stage 5)*:
  `./gradlew :app:testDebugUnitTest` across `116` classes (`AspiceSweAuditContractTest`, `HoechstmassToolingContractTest`, `ReleaseCiAndLintConfigContractTest` + `113` tagged `.kt/.java` suites): **954 / 954 PASSED** (`0` failures, `0` errors, `0` skipped).
* **Stage 5 Python Unit Suites**:
  `python3 -m unittest tools/test_verify_requirement_governance.py tools/test_generate_test_evidence.py tools/test_jira_accounts.py tools/hoechstmass/tools/test_hoechstmass_tools.py`: **162 / 162 PASSED** (`131` root + `31` Hoechstmass; `49/49` via `discover -s tools/hoechstmass/tools`).
* **Stage 5 Governance CLI Gates**:
  `verify_requirement_governance.py` (default + `--check-test-evidence` + `--check-verified-evidence` + `--check-orphans` + `--check-attributes` + `--check-tests-format` + `--verify-sys-trace`): **ALL PASS** (`0` legacy unbacked TSTs tolerated, `955` backed in Stage 4 -> `956` after `TST-PRO-101` promotion).

---

## 4. Hardware / Physical Verification & Invariants
* **Runtime / Device Scope**: Zero `app/src/main/` production or UI edits; no emulator/APK deployment required (`Rule 7.5`).
* **Submodule & Gate Invariants**: `ANT-Android-SDKs` untouched at `160000` (`0e8cea71`); `ALLOWED_ENGINEERING_ENTRIES` clean; `REQ-PRO-101` & `TST-PRO-101` `[Verified]`; Rule 3 Human Gate intact.
