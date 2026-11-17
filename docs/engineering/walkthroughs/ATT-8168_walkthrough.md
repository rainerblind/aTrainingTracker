# Stage 5: Walkthrough & Verification - ATT-8168: Correct sprint metrics statistics and suspend unsupported CL3 and CL4 capability claims

**Ticket**: [ATT-8168](https://rainerblind.atlassian.net/browse/ATT-8168)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_03_Nacht`  
**Requirement Mapping**: `REQ-PRO-122` (`REQ-PRO-122.1`–`REQ-PRO-122.6`)  
**Test Mapping**: `TST-PRO-122` (`TST-PRO-122.1`–`TST-PRO-122.6`)  
**Branch**: `feature/ATT-8168` | **Author**: AI Agent 1 (Implementer, `flash`) | **Date**: 2026-11-17  

---

## 1. Executive Summary & Verification Overview
Remediated ASPICE 4.0 Audit Finding `Observation (PA 4.x)` across `tools/sprint_metrics.py`, `tools/hoechstmass/tools/sprint_metrics.py`, `docs/project_protocol.md` (`v1.0.2`), `tools/hoechstmass/docs/project_protocol.md`, `tools/hoechstmass/README.md`, and `HoechstmassCoreFrameworkContractTest.kt`:
* **Bounded SPC & Era Resets**: Added `_compute_percentage_spc_bounds()` using pooled binomial p-chart bounds (clamped `[0.0, 100.0]` fallback when denominators are absent) and `ACTIVE_PROCESS_VERSION = "2.0"` era partitioning in `calculate_spc_limits()`. Against the 19-sprint `process_metrics.jsonl` history, legacy `overall_ftpr` bounds drop from `116.58%` to `[93.35, 99.16]` and `human_acceptance` from `118.01%` to `[57.37, 84.6]`, while `v2.0` cleanly resets (`baseline_reset: True`).
* **Truthful Acceptance, Empirical Escape & Unmasked Plausibility**: Separated terminal `human_accepted_tickets` (`Erledigt`/`Done`/`Closed`) from `completion_rate_percent`, removed synthetic `p = 0.02` floor substitution, and restricted Gate 1–5 plausibility checks to Gate 1–5 revisions (`rev6`/`bounces6` excluded).
* **CL3–CL5 Claim Suspension**: Annotated Level 3–5 entries as `[Target / Not Yet Claimed]` pending verified CL2 attainment while preserving `HoechstmassCoreFrameworkContractTest.kt` matrix pins and `REQ-PRO-120` headers.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-122.1` | `TST-PRO-122.1` | Unit (`tools/test_sprint_metrics.py`) | **PASSED** (`18/18` suite OK) | `Verified` |
| `REQ-PRO-122.2` | `TST-PRO-122.2` | Unit (`tools/test_sprint_metrics.py`) | **PASSED** (`18/18` suite OK) | `Verified` |
| `REQ-PRO-122.3` | `TST-PRO-122.3` | Unit (`tools/test_sprint_metrics.py`) | **PASSED** (`18/18` suite OK) | `Verified` |
| `REQ-PRO-122.4` | `TST-PRO-122.4` | Unit (`tools/test_sprint_metrics.py`) | **PASSED** (`18/18` suite OK) | `Verified` |
| `REQ-PRO-122.5` | `TST-PRO-122.5` | Mirror Unit (`tools/hoechstmass/tools/`) | **PASSED** (`52 + 16` OK) | `Verified` |
| `REQ-PRO-122.6` | `TST-PRO-122.6` | Gradle Contract + Governance CLI | **PASSED** (`24/24` + exit `0`) | `Verified` |

---

## 3. Automated Test Evidence

### Python Offline Suites (Post-Final-Edit, Rule 5 Compliant)
```text
python3 -m unittest tools.test_sprint_metrics tools.test_jira_accounts tools.test_verify_requirement_governance tools.test_baseline_manager tools.test_jira_epics tools.test_review_tool
-> Ran 225 tests (OK)

python3 -m unittest discover -s tools/hoechstmass/tools -p "test_*.py"
-> Ran 52 tests (OK) [test_hoechstmass_tools (48) + test_review_tool (4)]

python3 -m unittest tools.hoechstmass.tools.test_sprint_metrics
-> Ran 16 tests (OK)
```

### Gradle Tier 1 Contract Suites (Verified at Gate 4 `366f1ad0`; `app/`, `tools/`, `.agents/` unchanged)
```text
python3 tools/parallel_sprint_lock.py ./gradlew :app:testDebugUnitTest \
  --tests "...HoechstmassCoreFrameworkContractTest" \
  --tests "...HoechstmassToolingContractTest" \
  --tests "...AspiceSweAuditContractTest" --max-workers=2
-> BUILD SUCCESSFUL in 6m 29s
   HoechstmassCoreFrameworkContractTest: 7/7 PASS | HoechstmassToolingContractTest: 4/4 PASS | AspiceSweAuditContractTest: 13/13 PASS
```

---

## 4. Verification & Clean-Room Audit
* `python3 tools/verify_requirement_governance.py --check-doc-headers --check-commit-hygiene --check-conflict-markers --check-metrics`: **PASS** (exit `0`; 19 JSONL records, 50 headers, 7,789 conflict-free files).
* `python3 tools/verify_requirement_governance.py`: **PASS** (exit `0`; `1,226` REQs / `1,233` TSTs 100% index-shard parity, `0` orphans).
* `python3 tools/verify_requirement_governance.py --text-file docs/engineering/walkthroughs/ATT-8168_walkthrough.md`: **PASS** (exit `0`).
* **Invariants**: `app/src/main/` untouched; `ANT-Android-SDKs` at `0e8cea714a231fb9565170d4ed01eafa868f9798`; `Localization Parity: N/A - no UI strings`.
