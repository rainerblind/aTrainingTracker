# Stage 5: Walkthrough & Verification - ATT-8584: Autonomous Schicht Retro Write-Allowlist

**Ticket**: [ATT-8584](https://rainerblind.atlassian.net/browse/ATT-8584) | **Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_02_Spät` (`596a54f3343ddd822769ccc617e9ece020734d35`)
**Requirement Mapping**: `REQ-PRO-117` (`REQ-PRO-117.1`..`REQ-PRO-117.4`, refining `REQ-PRO-105`, `REQ-STP-010.2`)
**Test Mapping**: `TST-PRO-117` (`TST-PRO-117.1`..`TST-PRO-117.3`)
**Branch**: `feature/ATT-8584` (rebased code SHA `36fb6af33bd2f52234ed6492b340035281d58116`) | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-17

---

## 1. Executive Summary & Verification Overview
Restricted autonomous `chore(ceremony2)` Schicht-close writes strictly to evidence/logs while protecting governance rules, skills, standards, and tooling:
1. **Rules & Skill Boundary (`.agents/rules/aspice_governance.md`, `.agents/skills/sprint-planner/SKILL.md`)**: Codified `Rule 2.7` and Ceremony 2 Step 5 to allow writes ONLY to `docs/engineering/retros/Sprint_Review_and_Retro_*.md`, `docs/engineering/gates/*`, `docs/engineering/qa_audits/*`, `docs/engineering/reviews/*`, `docs/engineering/test_evidence/*`, `docs/engineering/metrics/process_metrics.jsonl`, and `docs/management/risk_register.md` (`REQ-STP-010.2`), routing proposed rule/skill/standard changes to `Sprint_Review_and_Retro_<KEY>.md` + `Wochen Review & Retro`.
2. **Automated Verifier & Umlaut Path Guard (`tools/verify_requirement_governance.py`)**: Implemented `verify_retro_write_allowlist()` (`--check-retro-allowlist`, integrated into `verify_governance()`) using `-c core.quotePath=false` on `git log` / `git diff-tree --root` so UTF-8 umlaut filenames (`spät`/`Spät`/`früh`) parse cleanly, enforcing post-cutoff (`1794925800`) `chore(ceremony2)` commits while exempting pre-cutoff (`8aa881df`), dedicated ticket, and `Interactive-PO:` commits.
3. **Dual-Layer Tests (`tools/test_verify_requirement_governance.py`, `AspiceSweAuditContractTest.kt`)**: Added `test_scenario_77_tst_pro_117_1_retro_write_allowlist_and_exemptions` (including real `tempfile.TemporaryDirectory` git repo tests with umlaut files and protected-path rejections) and `testAutonomousRetroWriteAllowlist_protectedRulesAndSkillsContract`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-117.1`, `117.2` | `TST-PRO-117.2` | `AspiceSweAuditContractTest` (`12/12`) & `HoechstmassCoreFrameworkContractTest` (`7/7`) | **PASSED** (`19/19`) | `Verified` |
| `REQ-PRO-117.3`, `117.4` | `TST-PRO-117.1` | `python3 -m unittest tools/test_verify_requirement_governance.py` | **PASSED** (`83/83`) | `Verified` |
| `REQ-PRO-117` | `TST-PRO-117.3` | `verify_requirement_governance.py` & `--check-retro-allowlist` | **PASSED** (`0`) | `Verified` |

---

## 3. Clean-Room Post-Rebase Automated Test Evidence (Tier 1 Targeted)
* **Rebased atop `sprint/2026-11-17_02_Spät` (`596a54f3`) @ `36fb6af3`**: Executed after rebase and `[Verified]` doc edits (`androidTest` & `TranslationParityTest` skipped as neither `androidTest/` nor `values*/strings*.xml` changed).
```text
Python Unit Suite: python3 -m unittest tools/test_verify_requirement_governance.py -> Ran 83 tests in 50.834s — OK (0 failures, 0 errors, 0 skipped).
Governance CLI: python3 tools/verify_requirement_governance.py && --check-retro-allowlist -> Exit 0 (Total=1219 REQs/975 Verified, Total=1226 TSTs/1004 Verified).
Gradle Targeted Contract Suite: ./gradlew :app:testDebugUnitTest --rerun --tests "*.AspiceSweAuditContractTest" --tests "*.HoechstmassCoreFrameworkContractTest"
  - AspiceSweAuditContractTest.xml: 12 tests, 0 failures, 0 errors, 0 skipped.
  - HoechstmassCoreFrameworkContractTest.xml: 7 tests, 0 failures, 0 errors, 0 skipped.
  - Total Kotlin Contract XMLs: 19/19 PASSED.
```

---

## 4. Physical / Emulator Device Verification & Invariants
* **N/A (Process Governance & Build Contract Scope)**: Zero `app/src/main/` or UI resource modifications.
* **Invariants Preserved**: `[Verified]` `REQ-PRO-105` & `REQ-STP-010.2`, `HoechstmassCoreFrameworkContractTest` markers (`sprint-metrics --record --post-to-retro`, `PA 4.2`, `PA 5.2`), and `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
