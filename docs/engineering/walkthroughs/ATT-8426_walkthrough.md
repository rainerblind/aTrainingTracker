# Stage 5: Walkthrough & Verification - ATT-8426: Dual-Vendor Reviews: Model Specification, Claude Protocol & 7-Day Release Gate

**Ticket**: [ATT-8426](https://rainerblind.atlassian.net/browse/ATT-8426)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht` | **SP**: 2 SP  
**Requirement Mapping**: `REQ-PRO-095` (`REQ-PRO-095.1..4`), `REQ-PRO-097` (`REQ-PRO-097.3`)  
**Test Mapping**: `TST-PRO-095` (`TST-PRO-095.2..5`), `TST-PRO-097` (`TST-PRO-097.3`)  
**Branch**: `feature/ATT-8426` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-17  

---

## 1. Executive Summary & Verification Overview
Resolves four dual-vendor code review and release gating governance defects from ATT-8213 and ATT-8162:
1. **Generic Top-Model Specification (`REQ-PRO-095.1`)**: Eliminated transient hardcoded model strings (`Gemini 2.5 Pro / Gemini 4 Carbon`, `Claude 3.7 Sonnet`) across `tools/jira_util.py`, `coverage_matrix.md`, `template_dual_vendor_code_review.md`, `coding_standards.md` §5.12, and `release_plan.md` §1.1. Tooling and policy now generically mandate the current top model of each vendor, recording actual model IDs and versions dynamically in concrete review reports.
2. **Strict Claude Non-Simulation Protocol (`REQ-PRO-095.1`)**: Formulated an explicit cross-vendor handoff protocol in `template_dual_vendor_code_review.md`, `jira_util.py`, and `coding_standards.md` §5.12. Antigravity agents running on Google models SHALL NEVER simulate or fabricate Anthropic Claude reviews (Finding K2 evidence integrity). Pending Claude evaluations are explicitly recorded as `PENDING / EXTERNAL HANDOFF REQUIRED`.
3. **Decoupled 7-Day Release Currency Gate (`REQ-PRO-095.3`, `REQ-PRO-095.4`)**: Updated `is_weekly_review_overdue()` in `tools/jira_util.py` to evaluate true 7-day review currency without early exit on `UNREVIEWED`, eliminating the 9-week release preparation deadlock in `release-manager/SKILL.md`. Subsystem slice selection in `get_next_review_subsystem()` maintains priority on `UNREVIEWED` slices.
4. **Complete Codebase Package Coverage (`REQ-PRO-095.2`)**: Added missing core package rows (`repositories/`, `cloud/`, `onlinecommunities/`, `segments/`, `climbs/`) to `docs/engineering/code_reviews/coverage_matrix.md`.
5. **Release Plan Synchronization (`REQ-PRO-097.3`)**: Synchronized `docs/management/release_plan.md` Section 3 to `docs/releases/V5.0.0/sys_verification_record.md` (replacing deprecated `docs/engineering/field_tests/`) and documented staging of physical on-device / human-execution tickets into pseudo-sprint `Bereit für PO Systemtest` (ID 454).
6. **PO Operational Rules Preserved**: Sprint cadence (`05:00/15:00/22:00`), 2-lane throttle + Stage 6 Pro Sparhebel, and weekly Kover coupling remain strictly preserved.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-095.1` | `TST-PRO-095.3`, `TST-PRO-095.5` | Generic model wording & Claude non-simulation test / policy review | **PASSED** | `Verified` |
| `REQ-PRO-095.2` | `TST-PRO-095.2` | Extended package matrix parsing in `test_get_next_review_subsystem` | **PASSED** | `Verified` |
| `REQ-PRO-095.3` | `TST-PRO-095.3` | Ticket creation with generic models & top-of-backlog placement | **PASSED** | `Verified` |
| `REQ-PRO-095.4` | `TST-PRO-095.4` | Decoupled 7-day currency test in `test_is_weekly_review_overdue` | **PASSED** | `Verified` |
| `REQ-PRO-097.3` | `TST-PRO-097.3` | Release plan field test path & pseudo-sprint ID 454 review | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Full Python Governance & Tooling Suite (`190/190 PASSED`)
```bash
python3 -m unittest tools/test_extract_pixel_field_evidence.py tools/test_jira_accounts.py tools/test_flock_serialization.py tools/test_verify_requirement_governance.py tools/test_generate_test_evidence.py tools/test_review_tool.py
# Result: Ran 190 tests in 58.704s - OK (0 failures, 0 errors)
# - tools/test_jira_accounts.py: 80/80 PASSED (incl. decoupled overdue check, extended matrix parsing, Claude protocol marker)
# - tools/test_extract_pixel_field_evidence.py: 8/8 PASSED
# - tools/test_flock_serialization.py: 10/10 PASSED
# - tools/test_verify_requirement_governance.py: 82/82 PASSED
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
Developer governance tooling, review process, and release gating enhancement; zero production runtime logic or layout changes in `app/src/main/`; verified via full Python tooling suite, Gradle build contract tests, and assembleDebug compilation.

---

## 5. Invariant & Governance Verification
1. **Living Documentation Synchronized**: `REQ-PRO-095.1..4` and `REQ-PRO-097.3` updated to `[Verified]`; `--sync-indexes` at 100% mathematical parity.
2. **Finding K1/K2 Compliance**: Zero synthetic metrics; Claude evaluations strictly non-simulated.
3. **Governance Verification**: `python3 tools/verify_requirement_governance.py` passed with code 0 (100% SYS-to-SWE traceability, 0 ungrounded requirements, 0 orphans across 2,375 files).
4. **Submodule Integrity**: `ANT-Android-SDKs` verified intact at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.
