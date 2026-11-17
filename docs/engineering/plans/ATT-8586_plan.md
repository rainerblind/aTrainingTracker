# Stage 3: Implementation Plan - ATT-8586: RISK-003 Mitigation Citation Fix & Contract Guard

**Ticket**: [ATT-8586](https://rainerblind.atlassian.net/browse/ATT-8586) | **Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_02_Spät`
**Requirement Mapping**: `REQ-STP-011` (`REQ-STP-011.1`..`REQ-STP-011.4`, refining `REQ-STP-010`)
**Test Mapping**: `TST-STP-011` (`TST-STP-011.1`..`TST-STP-011.4`)
**Branch**: `feature/ATT-8586` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-17

---

## 1. Problem Description & Architectural Scope
`docs/management/risk_register.md` (`RISK-003` row, L99) cites `Continuous Target SDK Maintenance (ATT-8084)`, where `ATT-8084` is an unrelated Garmin FIT Manufacturer ID feature ticket (`Zu erledigen`). This plan removes the erroneous Jira key, preserves verified `RISK-001`..`RISK-011` citations, and adds an automated regression guard in `ReleaseCiAndLintConfigContractTest.kt`.
* **UI Consistency**: `N/A` (documentation and JUnit 4 build contract test only; zero Compose/XML UI changes).

---

## 2. Traceability & Requirements Mapping
* **`REQ-STP-011.1` $\rightarrow$ `TST-STP-011.1`**: Replace `Continuous Target SDK Maintenance (ATT-8084)` in `RISK-003` (`docs/management/risk_register.md`) with `Continuous Target SDK Maintenance (Rule 29 & ManifestSecurityContractTest)` (zero phantom Jira keys).
* **`REQ-STP-011.2` $\rightarrow$ `TST-STP-011.2`**: Preserve verified Jira citations across `RISK-001`..`RISK-011` and High-exposure ($E \ge 15$) links (`ATT-6148`/`ATT-6152`, `ATT-6150`/`ATT-6141`).
* **`REQ-STP-011.3` $\rightarrow$ `TST-STP-011.1`**: Confirm `Last Review Date` (`2026-11-17`) and `Sprint Review Log` alignment in `docs/management/risk_register.md`.
* **`REQ-STP-011.4` $\rightarrow$ `TST-STP-011.3`**: Assert both `!riskContent.contains("ATT-8084")` and `riskContent.contains("Continuous Target SDK Maintenance (Rule 29 & ManifestSecurityContractTest)")` in `ReleaseCiAndLintConfigContractTest.testProjectRiskRegister_existsAndHighExposureMitigated` referencing `REQ-STP-011` / `TST-STP-011`.

---

## 3. System Invariants & Preserved Behavior
1. **Code Lock & Zero Production Edits**: Zero modifications under `app/src/main/`; zero edits to `docs/management/risk_register.md` or `app/src/test/` until `check-gate` passes in Stage 4.
2. **Existing `REQ-STP-010` / `TST-STP-010` Parity**: Preserve `MAN.5` + `MAN.3.BP5` headers, `RISK-001`..`RISK-011` rows, `ATT-6148` / `ATT-6150` High-exposure links, `2026-46.1` & `2026-46.6` `SKIPPED` log rows, and `docs/management/` placement outside `docs/engineering/`.
3. **Submodule & Worktree Purity**: Keep `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`; never create symlinks at submodule paths.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1 (`REQ-STP-011.1`, `REQ-STP-011.2`, `REQ-STP-011.3` / `docs/management/risk_register.md`)**:
  - Verify Gate 3 sign-off via `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>`.
  - In `RISK-003` (L99), replace `Continuous Target SDK Maintenance (ATT-8084)` with `Continuous Target SDK Maintenance (Rule 29 & ManifestSecurityContractTest)`.
  - Verify `Last Review Date` is `2026-11-17`, `Sprint Review Log` aligns cleanly, and all `RISK-001`..`RISK-011` & High-exposure ticket citations remain intact.
* **Step 2 (`REQ-STP-011.4` / `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`)**:
  - In `testProjectRiskRegister_existsAndHighExposureMitigated` (comment & body), reference `REQ-STP-011` / `TST-STP-011` alongside `REQ-STP-010` / `TST-STP-010`.
  - Add explicit assertions: `assertFalse("Risk register RISK-003 must not cite unrelated Garmin FIT ticket ATT-8084 (REQ-STP-011.4, TST-STP-011.3)", riskContent.contains("ATT-8084"))` and `assertTrue("RISK-003 must reference Continuous Target SDK Maintenance (Rule 29 & ManifestSecurityContractTest) (REQ-STP-011.1, TST-STP-011.3)", riskContent.contains("Continuous Target SDK Maintenance (Rule 29 & ManifestSecurityContractTest)"))` (importing `org.junit.Assert.assertFalse` if needed).
* **Step 3 (`TST-STP-011.3`, `TST-STP-011.4` / Stage 4 Verification Execution)**:
  - Ensure Gradle SDK config symlink exists in worktree (`[ -e local.properties ] || ln -s /home/rainer/AndroidStudioProjects/aTrainingTracker/local.properties local.properties`) without touching `ANT-Android-SDKs`.
  - Execute targeted contract suite under lock: `python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --tests "*.ReleaseCiAndLintConfigContractTest"`.
  - Execute `python3 tools/verify_requirement_governance.py` and confirm exit `0`.

---

## 5. Verification & Rollback Plan
* **Verification**: Confirm 100% pass on `ReleaseCiAndLintConfigContractTest` (including `testProjectRiskRegister_existsAndHighExposureMitigated`) and clean exit `0` on `verify_requirement_governance.py`.
* **Rollback**: `git checkout -- docs/management/risk_register.md app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt` reverts Stage 4 edits cleanly.
