# Stage 5: Walkthrough & Verification - ATT-8437: Replace hardcoded live repo count assertions in test_scenario_68/70 with relational invariants

**Ticket**: [ATT-8437](https://rainerblind.atlassian.net/browse/ATT-8437)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht`  
**Requirement Mapping**: `REQ-PRO-102` (`REQ-PRO-102.3`), `REQ-PRO-103` (`REQ-PRO-103.2`)  
**Test Mapping**: `TST-PRO-102` (`TST-PRO-102.3`), `TST-PRO-103` (`TST-PRO-103.2`)  
**Branch**: `feature/ATT-8437` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-17  

---

## 1. Executive Summary & Verification Overview
Remediates a test fragility defect identified during sprint evolution of `ATT-8146` (`REQ-PRO-102`) and `ATT-8147` (`REQ-PRO-103`). In `tools/test_verify_requirement_governance.py`, `test_scenario_68` and `test_scenario_70` originally asserted brittle point-in-time scalar counts against the live repository (`assertEqual(..., 363)`). Adding legitimate new System-Refinement requirements across sprints caused false-positive test failures despite 100% classification compliance. This ticket replaced static snapshot equality with relational mathematical invariants:
1. **Live Forward Coverage Invariants (`test_scenario_68` / `TST-PRO-102.3`)**: Replaced brittle scalar equality with exact forward parity (`report["covered_count"] == report["sysreq_count"] == 12`), lower-bound regression floors (`swe_refinement_total_count >= 362`, `swe_refinement_covered_count >= 346`, `swe_refinement_coverage_pct >= 95.0`), and isolation assertion confirming zero `REQ-DEX-*` or `REQ-PRO-*` pollution in `sys_to_swe_map`.
2. **Live Reverse Classification Invariants (`test_scenario_70` / `TST-PRO-103.2`)**: Enforced mathematical identities guaranteeing 100% classification: `reverse_classified_count == swe_refinement_total_count`, partition completeness (`swe_refinement_covered_count + swe_derived_sw_only_count == swe_refinement_total_count`), zero unclassified list (`unclassified_swe_refinement_reqs == []`), regression floor (`swe_refinement_total_count >= 362`), and `reverse_classified_pct == 100.0`.
3. **Synthetic Mismatch Guard (`test_scenario_69` / `TST-PRO-103.1`)**: Retained and verified synthetic fixture proving unclassified requirements fail loudly under `fail_on_unclassified_swe=True`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-102.3` | `TST-PRO-102.3` | Live Forward SYS Coverage Relational Invariant (`test_scenario_68`) | **PASSED** | `Verified` |
| `REQ-PRO-103.2` | `TST-PRO-103.2` | Live Reverse Classification Mathematical Identity (`test_scenario_70`) | **PASSED** | `Verified` |
| `REQ-PRO-103.1` | `TST-PRO-103.1` | Synthetic Unclassified Refinement Rejection (`test_scenario_69`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Full Python Governance & Tooling Suite (`182/182 PASSED`)
```bash
python3 -m unittest tools/test_jira_accounts.py tools/test_flock_serialization.py tools/test_verify_requirement_governance.py tools/test_generate_test_evidence.py tools/test_review_tool.py
# Result: Ran 182 tests in 48.849s - OK (0 failures, 0 errors)
# - tools/test_jira_accounts.py: 79/79 PASSED
# - tools/test_flock_serialization.py: 10/10 PASSED
# - tools/test_verify_requirement_governance.py: 82/82 PASSED (incl. test_scenario_68 & test_scenario_70)
# - tools/test_generate_test_evidence.py: 5/5 PASSED
# - tools/test_review_tool.py: 5/5 PASSED
```

### Build Contract Suite & Regression Gate (`161/161 PASSED`)
```bash
./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*" :app:compileDebugAndroidTestKotlin assembleDebug --max-workers=2
# Result: BUILD SUCCESSFUL (161/161 tests PASSED across all 24 build contract test classes; assembleDebug clean)
```

---

## 4. Hardware / Physical Verification (Pixel 10)
Developer governance test suite hardening only; zero production runtime code or layout changes in `app/src/main/`; verified via full Python and Gradle build contract test execution.

---

## 5. Invariant & Governance Verification
1. **Living Documentation Synchronized**: `REQ-PRO-102.3`, `REQ-PRO-103.2`, `TST-PRO-102.3`, `TST-PRO-103.2` updated to `[Verified]`; `--sync-indexes` at 100% mathematical parity.
2. **Chesterton's Fence Audit**: Lower-bound floors (`>= 362`, `>= 346`) ensure coverage never silently degrades; mathematical identities strictly prevent unclassified requirements.
3. **Governance Verification**: `python3 tools/verify_requirement_governance.py` passed with code 0 (0 ungrounded requirements, 0 orphans across 2,375 files).
4. **Submodule Integrity**: `ANT-Android-SDKs` verified intact at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.
